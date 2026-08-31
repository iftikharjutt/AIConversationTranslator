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

// Direct Gemini REST Models
@Serializable
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = "user",
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String // Base64-encoded audio bytes
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float = 0.2f,
    val responseMimeType: String? = "application/json"
)

@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiApiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContentResponse? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiContentResponse(
    val parts: List<GeminiPartResponse>? = null,
    val role: String? = null
)

@Serializable
data class GeminiPartResponse(
    val text: String? = null
)

@Serializable
data class GeminiApiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@Serializable
data class GeminiModelListResponse(
    val models: List<GeminiModelItem>? = null
)

@Serializable
data class GeminiModelItem(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String>? = null
)

@Serializable
data class GeminiStructuredResult(
    @SerialName("transcript") val transcript: String? = null,
    @SerialName("translation") val translation: String? = null
)

