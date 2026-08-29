package com.example.aitranslator.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranscribeResponse(
    val text: String,
    val detectedLanguage: String? = null,
    val error: String? = null
)

@Serializable
data class TranslateRequest(
    val text: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val context: String? = null
)

@Serializable
data class TranslateResponse(
    val translation: String? = null,
    val error: String? = null
)

// --- Google Gemini API Models ---

@Serializable
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    @SerialName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data")
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    @SerialName("mime_type")
    val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    @SerialName("response_mime_type")
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: GeminiPromptFeedback? = null,
    val error: GeminiApiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContentResponse? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiContentResponse(
    val parts: List<GeminiPartResponse>? = null
)

@Serializable
data class GeminiPartResponse(
    val text: String? = null
)

@Serializable
data class GeminiPromptFeedback(
    val blockReason: String? = null
)

@Serializable
data class GeminiApiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@Serializable
data class GeminiTranslationResult(
    val transcription: String = "",
    val translation: String = ""
)

