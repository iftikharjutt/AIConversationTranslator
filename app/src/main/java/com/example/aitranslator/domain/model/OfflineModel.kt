package com.example.aitranslator.domain.model

import kotlinx.serialization.Serializable

enum class OfflineModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    READY,
    CORRUPTED,
    INCOMPLETE,
    UNSUPPORTED
}

enum class TranslationMode(val displayName: String, val description: String) {
    AUTO("AUTO (Recommended)", "Use Gemini Cloud AI when online; seamlessly use Offline Model when offline"),
    ONLINE("ONLINE (Gemini API)", "Always use direct Google Gemini Cloud AI"),
    OFFLINE("OFFLINE (On-Device)", "Always use local on-device translation model (Zero internet)")
}

enum class TranslationEngineType {
    GEMINI,
    OFFLINE_ONNX,
    OFFLINE_GLOSSARY_FALLBACK
}

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val engineType: TranslationEngineType,
    val engineDescription: String,
    val latencyMs: Long = 0L,
    val isOffline: Boolean = false
)

data class OfflineModel(
    val modelId: String,
    val modelName: String,
    val version: String,
    val localPath: String,
    val status: OfflineModelStatus,
    val totalSize: Long,
    val downloadedSize: Long,
    val sha256: String,
    val supportedLanguages: List<String>,
    val license: String,
    val sourceUrl: String,
    val runtime: String,
    val installedAt: Long,
    val lastVerifiedAt: Long
)

@Serializable
data class ModelManifest(
    val modelId: String,
    val modelName: String,
    val version: String,
    val supportedLanguages: List<String> = emptyList(),
    val modelFiles: List<ModelFileInfo> = emptyList(),
    val expectedSize: Long = 0L,
    val sha256: String = "",
    val license: String = "",
    val sourceUrl: String = "",
    val createdAt: Long = 0L,
    val runtime: String = "onnx-int8"
)

@Serializable
data class ModelFileInfo(
    val name: String,
    val size: Long,
    val sha256: String = ""
)
