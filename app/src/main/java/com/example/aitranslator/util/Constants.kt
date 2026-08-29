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
}
