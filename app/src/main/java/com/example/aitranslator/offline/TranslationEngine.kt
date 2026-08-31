package com.example.aitranslator.offline

import com.example.aitranslator.domain.model.TranslationResult

interface TranslationEngine {
    val engineName: String
    val isOfflineEngine: Boolean

    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        context: String?
    ): Result<TranslationResult>
}
