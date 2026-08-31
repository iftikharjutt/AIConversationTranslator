package com.example.aiconversationtranslator.translation.offline

/**
 * Boundary for a local translation implementation.
 * The actual NLLB ONNX session is intentionally injected later so the
 * working Gemini engine is never coupled to model-specific code.
 */
interface OfflineTranslationEngine {
    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String
}

class NllbOnnxTranslationEngine : OfflineTranslationEngine {
    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String {
        require(text.isNotBlank()) { "Text must not be blank" }
        require(
            (sourceLanguage == NllbLanguageCodes.MALAY && targetLanguage == NllbLanguageCodes.URDU) ||
                (sourceLanguage == NllbLanguageCodes.URDU && targetLanguage == NllbLanguageCodes.MALAY)
        ) { "Offline NLLB currently supports Malay ↔ Urdu only" }

        throw OfflineModelNotReadyException(
            "NLLB ONNX model is not loaded. Install and verify the model before enabling offline inference."
        )
    }
}

class OfflineModelNotReadyException(message: String) : IllegalStateException(message)
