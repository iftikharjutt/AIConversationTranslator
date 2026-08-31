package com.example.aitranslator.offline

import com.example.aitranslator.data.preferences.PreferenceManager
import com.example.aitranslator.domain.model.TranslationEngineType
import com.example.aitranslator.domain.model.TranslationResult
import com.example.aitranslator.domain.repository.TranslationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranslationEngine @Inject constructor(
    private val repository: TranslationRepository,
    private val preferenceManager: PreferenceManager
) : TranslationEngine {

    override val engineName: String = "Gemini Direct Cloud AI"
    override val isOfflineEngine: Boolean = false

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        context: String?
    ): Result<TranslationResult> {
        val startTime = System.currentTimeMillis()
        val model = preferenceManager.geminiModel.first()

        val result = repository.translateText(
            text = text,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            context = context
        )

        return if (result.isSuccess) {
            val latency = System.currentTimeMillis() - startTime
            Result.success(
                TranslationResult(
                    originalText = text,
                    translatedText = result.getOrNull() ?: "",
                    engineType = TranslationEngineType.GEMINI,
                    engineDescription = "Powered by Gemini ($model)",
                    latencyMs = latency,
                    isOffline = false
                )
            )
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Gemini translation failed"))
        }
    }
}
