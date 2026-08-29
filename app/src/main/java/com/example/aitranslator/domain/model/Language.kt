package com.example.aitranslator.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Language(
    val code: String,
    val name: String,
    val nativeName: String,
    val speechSupported: Boolean = true,
    val translationSupported: Boolean = true,
    val ttsSupported: Boolean = true,
    val requiresCapabilityVerification: Boolean = false
) {
    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            Language("en", "English", "English", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("ur", "Urdu", "اردو", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("ms", "Malay", "Bahasa Melayu", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("id", "Indonesian", "Bahasa Indonesia", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("ar", "Arabic", "العربية", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("hi", "Hindi", "हिन्दी", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("bn", "Bengali", "বাংলা", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("zh", "Chinese", "中文", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("ta", "Tamil", "தமிழ்", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("es", "Spanish", "Español", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("fr", "French", "Français", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("de", "German", "Deutsch", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("ja", "Japanese", "日本語", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("ko", "Korean", "한국어", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("tr", "Turkish", "Türkçe", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("vi", "Vietnamese", "Tiếng Việt", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("th", "Thai", "ไทย", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("fa", "Persian", "فارسی", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language("ps", "Pashto", "پښتو", speechSupported = true, translationSupported = true, ttsSupported = true),
            Language(
                code = "rhg",
                name = "Rohingya",
                nativeName = "Ruáingga",
                speechSupported = false,
                translationSupported = false,
                ttsSupported = false,
                requiresCapabilityVerification = true
            )
        )

        fun getByCode(code: String): Language {
            return SUPPORTED_LANGUAGES.find { it.code.equals(code, ignoreCase = true) }
                ?: Language(code, code, code)
        }
        
        fun defaultSource(): Language = getByCode("ms") // Default Malay
        fun defaultTarget(): Language = getByCode("ur") // Default Urdu
    }
}
