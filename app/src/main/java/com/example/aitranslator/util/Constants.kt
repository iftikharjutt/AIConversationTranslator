package com.example.aitranslator.util

object Constants {
    const val DATABASE_NAME = "ai_translator.db"
    const val PREFERENCES_NAME = "ai_translator_prefs"
    
    // Audio constants
    const val SAMPLE_RATE_HZ = 16000
    const val CHANNEL_CONFIG_IN = android.media.AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM = 2 bytes per sample
    
    // Default Durations in seconds
    const val DEFAULT_SEGMENT_DURATION_SECONDS = 180 // 3 minutes
    const val DEBUG_SEGMENT_DURATION_SECONDS = 10 // 10 seconds for debug/dev
    
    // Notification constants
    const val NOTIFICATION_CHANNEL_ID = "recording_service_channel"
    const val NOTIFICATION_ID = 1001
    
    // WorkManager
    const val WORKER_SEGMENT_ID_KEY = "key_segment_id"
    const val WORKER_CONVERSATION_ID_KEY = "key_conversation_id"

    // Gemini AI Models
    const val GEMINI_DEFAULT_MODEL = "gemini-3.7-flash"

    val GEMINI_MODELS = listOf(
        GeminiModelOption("gemini-3.7-flash", "Gemini 3.7 Flash", "Latest frontier intelligence & rapid translation (Recommended)", true),
        GeminiModelOption("gemini-3.6-flash", "Gemini 3.6 Flash", "High performance multimodal translation", false),
        GeminiModelOption("gemini-3.5-flash", "Gemini 3.5 Flash", "Fast, high quality conversational audio processing", false),
        GeminiModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra low latency & high throughput", false),
        GeminiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Stable high speed multimodal model", false),
        GeminiModelOption("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", "Lightweight and efficient", false),
        GeminiModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "High reasoning & nuanced accuracy", false),
        GeminiModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", "Multimodal audio translation", false),
        GeminiModelOption("gemini-1.5-flash", "Gemini 1.5 Flash", "Legacy fast model", false)
    )
}

data class GeminiModelOption(
    val id: String,
    val name: String,
    val description: String,
    val isRecommended: Boolean = false
)
