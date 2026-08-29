package com.example.aitranslator.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
        }
    }

    fun speak(text: String, languageCode: String) {
        if (!isInitialized || tts == null || text.isBlank()) return

        val locale = when (languageCode.lowercase()) {
            "ur" -> Locale("ur", "PK")
            "ms" -> Locale("ms", "MY")
            "id" -> Locale("id", "ID")
            "ar" -> Locale("ar", "SA")
            "hi" -> Locale("hi", "IN")
            "bn" -> Locale("bn", "BD")
            "zh" -> Locale.CHINESE
            "ta" -> Locale("ta", "IN")
            "es" -> Locale("es", "ES")
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "tr" -> Locale("tr", "TR")
            "vi" -> Locale("vi", "VN")
            "th" -> Locale("th", "TH")
            "fa" -> Locale("fa", "IR")
            else -> Locale(languageCode)
        }

        val available = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (available >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = locale
        } else {
            tts?.language = Locale.ENGLISH
        }

        val utteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
