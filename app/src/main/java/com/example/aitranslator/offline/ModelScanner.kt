package com.example.aitranslator.offline

import android.content.Context
import android.os.Environment
import com.example.aitranslator.data.local.OfflineModelDao
import com.example.aitranslator.data.local.OfflineModelEntity
import com.example.aitranslator.domain.model.ModelManifest
import com.example.aitranslator.domain.model.OfflineModel
import com.example.aitranslator.domain.model.OfflineModelStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineModelDao: OfflineModelDao,
    private val json: Json
) {
    fun getPrimaryModelsDirectory(): File {
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appFolder = File(publicDownloads, "AIConversationTranslator/models")
            if (appFolder.exists() || appFolder.mkdirs()) {
                val testFile = File(appFolder, ".test_write")
                if (testFile.createNewFile()) {
                    testFile.delete()
                    return appFolder
                }
            }
        } catch (_: Exception) {}

        val appPrivate = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        appPrivate.mkdirs()
        return appPrivate
    }

    suspend fun scanDirectory(modelsDir: File = getPrimaryModelsDirectory()): List<OfflineModel> = withContext(Dispatchers.IO) {
        val detected = mutableListOf<OfflineModel>()

        // Check both public downloads and app private models directories
        val candidateDirs = mutableListOf<File>()
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val pubDir = File(publicDownloads, "AIConversationTranslator/models")
            if (pubDir.exists()) candidateDirs.add(pubDir)
        } catch (_: Exception) {}

        val appPrivate = context.getExternalFilesDir("models")
        if (appPrivate != null && appPrivate.exists() && !candidateDirs.contains(appPrivate)) {
            candidateDirs.add(appPrivate)
        }
        if (!candidateDirs.contains(modelsDir)) {
            candidateDirs.add(modelsDir)
        }

        // 1. Evaluate default Malay <-> Urdu official model entry
        val defaultModel = getDefaultMalayUrduModel(modelsDir)
        offlineModelDao.insertModel(OfflineModelEntity.fromDomain(defaultModel))
        detected.add(defaultModel)

        for (baseDir in candidateDirs) {
            if (!baseDir.exists()) continue
            val subdirs = baseDir.listFiles { file -> file.isDirectory } ?: emptyArray()

            for (dir in subdirs) {
                val manifestFile = File(dir, "manifest.json")
                if (manifestFile.exists() && manifestFile.length() > 0) {
                    try {
                        val manifestText = manifestFile.readText()
                        val manifest = json.decodeFromString<ModelManifest>(manifestText)
                        val status = evaluatePackageStatus(dir, manifest)
                        val totalSize = calculateFolderSize(dir)

                        val offlineModel = OfflineModel(
                            modelId = manifest.modelId,
                            modelName = manifest.modelName,
                            version = manifest.version,
                            localPath = dir.absolutePath,
                            status = status,
                            totalSize = if (manifest.expectedSize > 0) manifest.expectedSize else totalSize,
                            downloadedSize = totalSize,
                            sha256 = manifest.sha256,
                            supportedLanguages = manifest.supportedLanguages,
                            license = manifest.license,
                            sourceUrl = manifest.sourceUrl,
                            runtime = manifest.runtime,
                            installedAt = if (manifest.createdAt > 0) manifest.createdAt else System.currentTimeMillis(),
                            lastVerifiedAt = System.currentTimeMillis()
                        )

                        offlineModelDao.insertModel(OfflineModelEntity.fromDomain(offlineModel))
                        if (detected.none { it.modelId == offlineModel.modelId }) {
                            detected.add(offlineModel)
                        }
                    } catch (_: Exception) {
                        // Invalid manifest JSON
                    }
                }
            }
        }
        detected
    }

    suspend fun verifyModelPackage(modelId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val entity = offlineModelDao.getModelById(modelId) ?: return@withContext Pair(false, "Model not found in database")
        val dir = File(entity.localPath)
        if (!dir.exists() || !dir.isDirectory) {
            offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.NOT_DOWNLOADED))
            return@withContext Pair(false, "Model directory does not exist: ${dir.absolutePath}")
        }

        val manifestFile = File(dir, "manifest.json")
        if (!manifestFile.exists()) {
            offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.INCOMPLETE))
            return@withContext Pair(false, "manifest.json is missing")
        }

        val manifest: ModelManifest
        try {
            manifest = json.decodeFromString(manifestFile.readText())
        } catch (e: Exception) {
            offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.CORRUPTED))
            return@withContext Pair(false, "manifest.json is malformed: ${e.message}")
        }

        for (fileInfo in manifest.modelFiles) {
            val file = File(dir, fileInfo.name)
            if (!file.exists()) {
                offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.INCOMPLETE))
                return@withContext Pair(false, "Missing required file: ${fileInfo.name}")
            }
            if (fileInfo.sha256.isNotBlank() && fileInfo.sha256.length == 64) {
                val actualSha256 = calculateSha256(file)
                if (!actualSha256.equals(fileInfo.sha256, ignoreCase = true)) {
                    offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.CORRUPTED))
                    return@withContext Pair(false, "SHA-256 checksum mismatch for ${fileInfo.name}")
                }
            }
        }

        val updated = entity.copy(
            status = OfflineModelStatus.READY,
            lastVerifiedAt = System.currentTimeMillis()
        )
        offlineModelDao.updateModel(updated)
        Pair(true, "Model verified successfully! Status: READY")
    }

    suspend fun deleteModelPackage(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val entity = offlineModelDao.getModelById(modelId) ?: return@withContext false
        val dir = File(entity.localPath)
        if (dir.exists() && dir.isDirectory) {
            dir.deleteRecursively()
        }
        offlineModelDao.updateModel(
            entity.copy(
                status = OfflineModelStatus.NOT_DOWNLOADED,
                downloadedSize = 0L
            )
        )
        true
    }

    private fun evaluatePackageStatus(dir: File, manifest: ModelManifest): OfflineModelStatus {
        if (manifest.modelFiles.isEmpty()) {
            return OfflineModelStatus.READY
        }
        var allPresent = true
        for (fileInfo in manifest.modelFiles) {
            val file = File(dir, fileInfo.name)
            if (!file.exists() || file.length() == 0L) {
                allPresent = false
                break
            }
        }
        return if (allPresent) OfflineModelStatus.READY else OfflineModelStatus.INCOMPLETE
    }

    private fun calculateFolderSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) size += file.length()
        }
        return size
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getDefaultMalayUrduModel(modelsDir: File = getPrimaryModelsDirectory()): OfflineModel {
        val modelFolder = File(modelsDir, "malay-urdu")
        val manifestFile = File(modelFolder, "manifest.json")
        val isReady = manifestFile.exists() && File(modelFolder, "model.onnx").exists()
        return OfflineModel(
            modelId = "nllb-200-distilled-600m-int8",
            modelName = "NLLB-200 Distilled (Malay ↔ Urdu INT8)",
            version = "1.0.0",
            localPath = modelFolder.absolutePath,
            status = if (isReady) OfflineModelStatus.READY else OfflineModelStatus.NOT_DOWNLOADED,
            totalSize = 548_000_000L, // ~548 MB
            downloadedSize = if (modelFolder.exists()) calculateFolderSize(modelFolder) else 0L,
            sha256 = "",
            supportedLanguages = listOf("msa_Latn", "zsm_Latn", "urd_Arab", "ms", "ur"),
            license = "CC-BY-NC 4.0 (Non-Commercial, Attribution)",
            sourceUrl = "https://huggingface.co/facebook/nllb-200-distilled-600M",
            runtime = "onnx-int8",
            installedAt = System.currentTimeMillis(),
            lastVerifiedAt = System.currentTimeMillis()
        )
    }
}
