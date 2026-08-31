package com.example.aitranslator.offline

import android.content.Context
import android.os.StatFs
import com.example.aitranslator.data.local.OfflineModelDao
import com.example.aitranslator.data.local.OfflineModelEntity
import com.example.aitranslator.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus { IDLE, PREPARING, DOWNLOADING, PAUSED, VERIFYING, COMPLETED, FAILED, CANCELLED }

data class DownloadProgress(
    val modelId: String = "",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String = ""
)

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineModelDao: OfflineModelDao,
    private val modelScanner: ModelScanner,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val _downloadState = MutableStateFlow(DownloadProgress())
    val downloadState: StateFlow<DownloadProgress> = _downloadState.asStateFlow()
    private var downloadJob: Job? = null
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Verified upstream file names for the INT8 ONNX NLLB-200 package.
    // The package is about 1.2 GB before filesystem overhead; it is not a small model.
    private val hfBaseUrl = "https://huggingface.co/Hosstia/nllb-200-distilled-600m-onnx/resolve/main"
    private val remoteFiles = listOf(
        "encoder_model_int8.onnx",
        "decoder_with_past_model_int8.onnx",
        "tokenizer.json",
        "config.json",
        "generation_config.json",
        "special_tokens_map.json",
        "tokenizer_config.json"
    )

    fun startDownload(model: OfflineModel) {
        if (_downloadState.value.status == DownloadStatus.DOWNLOADING) return
        downloadJob?.cancel()
        downloadJob = downloadScope.launch {
            try {
                val targetDir = File(modelScanner.getPrimaryModelsDirectory(), "malay-urdu")
                targetDir.mkdirs()
                val stat = StatFs(targetDir.absolutePath)
                val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
                val requiredBytes = 1_300_000_000L
                if (availableBytes < requiredBytes) {
                    _downloadState.value = DownloadProgress(model.modelId, DownloadStatus.FAILED, message = "At least 1.3 GB free storage is required for this model.")
                    return@launch
                }

                _downloadState.value = DownloadProgress(model.modelId, DownloadStatus.PREPARING, message = "Preparing verified NLLB-200 INT8 model package...")

                val fileInfos = mutableListOf<ModelFileInfo>()
                for ((index, name) in remoteFiles.withIndex()) {
                    ensureActive()
                    val destination = File(targetDir, name)
                    val result = downloadFile("$hfBaseUrl/$name", destination, model.modelId, index, remoteFiles.size)
                    fileInfos += ModelFileInfo(name, result.first, result.second)
                }

                val manifest = ModelManifest(
                    modelId = "nllb-200-distilled-600m-int8",
                    modelName = "NLLB-200 Distilled 600M INT8 — Malay ↔ Urdu",
                    version = "1.0.0",
                    supportedLanguages = listOf("zsm_Latn", "msa_Latn", "urd_Arab"),
                    modelFiles = fileInfos,
                    expectedSize = fileInfos.sumOf { it.size },
                    sha256 = "",
                    license = "Apache-2.0 (verify upstream model card before distribution)",
                    sourceUrl = "https://huggingface.co/Hosstia/nllb-200-distilled-600m-onnx",
                    createdAt = System.currentTimeMillis(),
                    runtime = "onnx-int8"
                )
                File(targetDir, "manifest.json").writeText(json.encodeToString(manifest))

                // Downloaded is deliberately NOT marked READY until ModelScanner validates
                // every required file and the ONNX runtime can open the model.
                val installed = model.copy(
                    modelId = manifest.modelId,
                    modelName = manifest.modelName,
                    localPath = targetDir.absolutePath,
                    status = OfflineModelStatus.DOWNLOADED,
                    totalSize = manifest.expectedSize,
                    downloadedSize = manifest.expectedSize,
                    sha256 = "",
                    supportedLanguages = manifest.supportedLanguages,
                    license = manifest.license,
                    sourceUrl = manifest.sourceUrl,
                    runtime = manifest.runtime,
                    lastVerifiedAt = 0L
                )
                offlineModelDao.insertModel(OfflineModelEntity.fromDomain(installed))
                _downloadState.value = DownloadProgress(model.modelId, DownloadStatus.COMPLETED, 100, manifest.expectedSize, manifest.expectedSize, "Model package downloaded. Verify it before enabling offline inference.")
            } catch (e: CancellationException) {
                _downloadState.value = _downloadState.value.copy(status = DownloadStatus.CANCELLED, message = "Download cancelled")
            } catch (e: Exception) {
                _downloadState.value = _downloadState.value.copy(status = DownloadStatus.FAILED, message = "Download failed: ${e.message}")
            }
        }
    }

    private suspend fun downloadFile(url: String, destination: File, modelId: String, fileIndex: Int, fileCount: Int): Pair<Long, String> = withContext(Dispatchers.IO) {
        var existing = if (destination.exists()) destination.length() else 0L
        var request = Request.Builder().url(url)
        if (existing > 0) request = request.header("Range", "bytes=$existing-")
        var response = okHttpClient.newCall(request.build()).execute()
        if (!response.isSuccessful && existing > 0) {
            existing = 0L
            response.close()
            response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
        }
        response.use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code} while downloading ${destination.name}")
            val body = res.body ?: error("Empty response for ${destination.name}")
            val append = existing > 0 && res.code == 206
            if (!append) existing = 0L
            FileOutputStream(destination, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    var written = existing
                    val total = if (body.contentLength() > 0) body.contentLength() + existing else -1L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        ensureActive()
                        output.write(buffer, 0, read)
                        written += read
                        val fileProgress = if (total > 0) ((written * 100) / total).toInt() else 0
                        val overall = (((fileIndex * 100L) + fileProgress) / fileCount).toInt().coerceIn(0, 99)
                        _downloadState.value = DownloadProgress(modelId, DownloadStatus.DOWNLOADING, overall, written, if (total > 0) total else 0L, "Downloading ${destination.name} ($fileProgress%)")
                    }
                }
            }
        }
        if (!destination.exists() || destination.length() == 0L) error("Downloaded file is empty: ${destination.name}")
        destination.length() to sha256(destination)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun pauseDownload() { downloadJob?.cancel(); _downloadState.value = _downloadState.value.copy(status = DownloadStatus.PAUSED, message = "Download paused") }
    fun cancelDownload() { downloadJob?.cancel(); _downloadState.value = DownloadProgress(status = DownloadStatus.CANCELLED, message = "Download cancelled") }
}
