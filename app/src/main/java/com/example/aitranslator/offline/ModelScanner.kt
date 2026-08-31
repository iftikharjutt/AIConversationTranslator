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
    private val json: Json,
    private val onnxModelValidator: OnnxModelValidator
) {
    fun getPrimaryModelsDirectory(): File {
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDownloads != null) {
                val appFolder = File(publicDownloads, "AIConversationTranslator/models")
                if (appFolder.exists() || appFolder.mkdirs()) {
                    val testFile = File(appFolder, ".test_write")
                    if (testFile.createNewFile()) {
                        testFile.delete()
                        return appFolder
                    }
                }
            }
        } catch (_: Exception) {}
        val appPrivate = try {
            context.getExternalFilesDir("models") ?: (context.filesDir?.let { File(it, "models") }) ?: File("models")
        } catch (_: Exception) {
            File("models")
        }
        appPrivate.mkdirs()
        return appPrivate
    }

    suspend fun scanDirectory(modelsDir: File = getPrimaryModelsDirectory()): List<OfflineModel> = withContext(Dispatchers.IO) {
        val detected = mutableListOf<OfflineModel>()
        val candidateDirs = mutableListOf<File>()
        try {
            val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AIConversationTranslator/models")
            if (pubDir.exists()) candidateDirs.add(pubDir)
        } catch (_: Exception) {}
        val appPrivate = context.getExternalFilesDir("models")
        if (appPrivate != null && appPrivate.exists() && !candidateDirs.contains(appPrivate)) candidateDirs.add(appPrivate)
        if (!candidateDirs.contains(modelsDir)) candidateDirs.add(modelsDir)

        val defaultModel = getDefaultMalayUrduModel(modelsDir)
        offlineModelDao.insertModel(OfflineModelEntity.fromDomain(defaultModel))
        detected.add(defaultModel)

        for (baseDir in candidateDirs) {
            if (!baseDir.exists()) continue
            val subdirs = baseDir.listFiles { file -> file.isDirectory } ?: emptyArray()
            for (dir in subdirs) {
                val manifestFile = File(dir, "manifest.json")
                if (!manifestFile.exists() || manifestFile.length() == 0L) continue
                try {
                    val manifest = json.decodeFromString<ModelManifest>(manifestFile.readText())
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
                        lastVerifiedAt = 0L
                    )
                    offlineModelDao.insertModel(OfflineModelEntity.fromDomain(offlineModel))
                    if (detected.none { it.modelId == offlineModel.modelId }) detected.add(offlineModel)
                } catch (_: Exception) {
                    // Ignore invalid third-party packages.
                }
            }
        }
        detected
    }

    suspend fun verifyModelPackage(modelId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val entity = offlineModelDao.getModelById(modelId) ?: return@withContext Pair(false, "Model not found in database")
        val dir = File(entity.localPath)
        if (!dir.isDirectory) {
            offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.NOT_DOWNLOADED))
            return@withContext Pair(false, "Model directory does not exist")
        }
        val manifestFile = File(dir, "manifest.json")
        if (!manifestFile.isFile) {
            offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.INCOMPLETE))
            return@withContext Pair(false, "manifest.json is missing")
        }
        val manifest = try {
            json.decodeFromString<ModelManifest>(manifestFile.readText())
        } catch (e: Exception) {
            offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.CORRUPTED))
            return@withContext Pair(false, "manifest.json is malformed: ${e.message}")
        }
        for (fileInfo in manifest.modelFiles) {
            val file = File(dir, fileInfo.name)
            if (!file.isFile || file.length() == 0L) {
                offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.INCOMPLETE))
                return@withContext Pair(false, "Missing required file: ${fileInfo.name}")
            }
            if (fileInfo.sha256.length == 64) {
                val actual = calculateSha256(file)
                if (!actual.equals(fileInfo.sha256, ignoreCase = true)) {
                    offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.CORRUPTED))
                    return@withContext Pair(false, "SHA-256 mismatch for ${fileInfo.name}")
                }
            }
        }
        val runtimeCheck = onnxModelValidator.validate(dir)
        if (runtimeCheck.isFailure) {
            val message = runtimeCheck.exceptionOrNull()?.message ?: "ONNX Runtime could not load the model"
            offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.UNSUPPORTED))
            return@withContext Pair(false, message)
        }
        offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.READY, lastVerifiedAt = System.currentTimeMillis()))
        Pair(true, "Model verified and loadable by ONNX Runtime. Translation inference is enabled only when the engine implementation supports this package.")
    }

    suspend fun deleteModelPackage(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val entity = offlineModelDao.getModelById(modelId) ?: return@withContext false
        val dir = File(entity.localPath)
        if (dir.exists() && dir.isDirectory) dir.deleteRecursively()
        offlineModelDao.updateModel(entity.copy(status = OfflineModelStatus.NOT_DOWNLOADED, downloadedSize = 0L))
        true
    }

    private fun evaluatePackageStatus(dir: File, manifest: ModelManifest): OfflineModelStatus {
        if (manifest.modelFiles.isEmpty()) return OfflineModelStatus.INCOMPLETE
        for (fileInfo in manifest.modelFiles) {
            val file = File(dir, fileInfo.name)
            if (!file.isFile || file.length() == 0L) return OfflineModelStatus.INCOMPLETE
        }
        return OfflineModelStatus.DOWNLOADED
    }

    private fun calculateFolderSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getDefaultMalayUrduModel(modelsDir: File = getPrimaryModelsDirectory()): OfflineModel {
        val modelFolder = File(modelsDir, "malay-urdu")
        val required = File(modelFolder, "encoder_model_int8.onnx")
        val downloaded = if (modelFolder.exists()) calculateFolderSize(modelFolder) else 0L
        return OfflineModel(
            modelId = "nllb-200-distilled-600m-int8",
            modelName = "NLLB-200 Distilled 600M INT8 — Malay ↔ Urdu",
            version = "1.0.0",
            localPath = modelFolder.absolutePath,
            status = if (required.isFile) OfflineModelStatus.DOWNLOADED else OfflineModelStatus.NOT_DOWNLOADED,
            totalSize = 1_200_000_000L,
            downloadedSize = downloaded,
            sha256 = "",
            supportedLanguages = listOf("zsm_Latn", "msa_Latn", "urd_Arab"),
            license = "Apache-2.0 (upstream conversion; verify upstream model card before distribution)",
            sourceUrl = "https://huggingface.co/Hosstia/nllb-200-distilled-600m-onnx",
            runtime = "onnx-int8",
            installedAt = 0L,
            lastVerifiedAt = 0L
        )
    }
}
