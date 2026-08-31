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

    private val hfBaseUrl = "https://huggingface.co/Hosstia/nllb-200-distilled-600m-onnx/resolve/main"

    private data class RemoteFile(val name: String, val url: String)

    private val remoteFiles = listOf(
        RemoteFile("encoder_model_int8.onnx", "$hfBaseUrl/encoder_model_int8.onnx"),
        RemoteFile("decoder_with_past_model_int8.onnx", "$hfBaseUrl/decoder_with_past_model_int8.onnx"),
        RemoteFile("tokenizer.json", "$hfBaseUrl/tokenizer.json")
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
                val requiredBytes = 1_200_000_000L
                if (availableBytes < requiredBytes) {
                    _downloadState.value = DownloadProgress(model.modelId, DownloadStatus.FAILED, message = "At least 1.2 GB free storage is required for this model.")
                    return@launch
                }

                _downloadState.value = DownloadProgress(model.modelId, DownloadStatus.PREPARING, message = "Preparing NLLB-200 INT8 model download...")

                val fileInfos = mutableListOf<ModelFileInfo>()
                for ((index, remote) in remoteFiles.withIndex()) {
                    ensureActive()
                    val destination = File(targetDir, remote.name)
                    val result = downloadFile(remote.url, destination, model.modelId, index, remoteFiles.size)
                    fileInfos += ModelFileInfo(remote.name, result.first, result.second)
                }

                val manifest = ModelManifest(
                    modelId = "nllb-200-distilled-600m-int8",
                    modelName = "NLLB-200 Distilled 600M INT8 — Malay ↔ Urdu",
                    version = "1.0.0",
                    supportedLanguages = listOf("zsm_Latn", "msa_Latn", "urd_Arab"),
                    modelFiles = fileInfos,
                    expectedSize = fileInfos.sumOf { it.size },
                    sha256 = "",
                    license = "See upstream model license before commercial distribution",
                    sourceUrl = hfBaseUrl,
                    createdAt = System.currentTimeMillis(),
                    runtime = "onnx-int8"
                )
                File(targetDir, "manifest.json").writeText(json.encodeToString(manifest))

                val installed = model.copy(
                    modelId = "nllb-200-distilled-600m-int8",
                    modelName = manifest.modelName,
                    localPath = targetDir.absolutePath,
                    status = OfflineModelStatus.READY,
                    totalSize = manifest.expectedSize,
                    downloadedSize = manifest.expectedSize,
                    sha256 = "",
                    supportedLanguages = manifest.supportedLanguages,
                    license = manifest.license,
                    sourceUrl = manifest.sourceUrl,
                    runtime = manifest.runtime,
                    lastVerifiedAt = System.currentTimeMillis()
                )
                offlineModelDao.insertModel(OfflineModelEntity.fromDomain(installed))
                _downloadState.value = DownloadProgress(model.modelId, DownloadStatus.COMPLETED, 100, manifest.expectedSize, manifest.expectedSize, "Real model files downloaded. Runtime validation is required before translation.")
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
                        val overallTotal = if (total > 0) total else 0L
                        val fileProgress = if (overallTotal > 0) ((written * 100) / overallTotal).toInt() else 0
                        val overall = (((fileIndex * 100L) + fileProgress) / fileCount).toInt().coerceIn(0, 99)
                        _downloadState.value = DownloadProgress(modelId, DownloadStatus.DOWNLOADING, overall, written, overallTotal, "Downloading ${destination.name} ($fileProgress%)")
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
