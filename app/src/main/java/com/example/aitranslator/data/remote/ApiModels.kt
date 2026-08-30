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

