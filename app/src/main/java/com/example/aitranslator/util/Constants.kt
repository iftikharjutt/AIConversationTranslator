package com.example.aitranslator.util

object Constants {
    const val DATABASE_NAME = "ai_translator.db"
    const val PREFERENCES_NAME = "ai_translator_prefs"
    
    // Audio constants
    const val SAMPLE_RATE_HZ = 16000
    const val CHANNEL_CONFIG_IN = android.media.AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM = 2 bytes per sample
    
    // Translation chunks: keep recording continuous, but send completed audio
    // to Gemini frequently so the UI does not appear to only record for minutes.
    const val DEFAULT_SEGMENT_DURATION_SECONDS = 10
    const val DEBUG_SEGMENT_DURATION_SECONDS = 5
    
    // Notification constants
    const val NOTIFICATION_CHANNEL_ID = "recording_service_channel"
    const val NOTIFICATION_ID = 1001
    
    // WorkManager
    const val WORKER_SEGMENT_ID_KEY = "key_segment_id"
    const val WORKER_CONVERSATION_ID_KEY = "key_conversation_id"

    // Gemini AI Models (Direct REST generateContent with Multimodal Audio)
    const val GEMINI_DEFAULT_MODEL = "gemini-2.5-flash"
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    val GEMINI_MODELS = listOf(
        GeminiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Latest high-speed multimodal audio & translation (Recommended)", true),
        GeminiModelOption("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", "Ultra low latency & high throughput", false),
        GeminiModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "High reasoning & nuanced accuracy", false),
        GeminiModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", "Next-gen fast multimodal model", false),
        GeminiModelOption("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite", "Lightweight next-gen model", false),
        GeminiModelOption("gemini-1.5-flash", "Gemini 1.5 Flash", "Standard fast audio translation", false),
        GeminiModelOption("gemini-1.5-pro", "Gemini 1.5 Pro", "High-capacity legacy reasoning model", false)
    )
}

data class GeminiModelOption(
    val id: String,
    val name: String,
    val description: String,
    val isRecommended: Boolean = false
)
