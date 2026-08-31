package com.example.aitranslator.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.aitranslator.data.preferences.PreferenceManager
import com.example.aitranslator.domain.model.TranslationMode
import com.example.aitranslator.domain.model.TranslationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationEngineDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val geminiEngine: GeminiTranslationEngine,
    private val offlineEngine: OfflineTranslationEngine
) {

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        contextText: String?
    ): Result<TranslationResult> {
        val mode = preferenceManager.translationMode.first()
        val apiKey = preferenceManager.geminiApiKey.first()

        return when (mode) {
            TranslationMode.OFFLINE -> {
                // STRICT ZERO-NETWORK GUARANTEE
                offlineEngine.translate(text, sourceLanguage, targetLanguage, contextText)
            }
            TranslationMode.ONLINE -> {
                geminiEngine.translate(text, sourceLanguage, targetLanguage, contextText)
            }
            TranslationMode.AUTO -> {
                // AUTO: Prefer Gemini when online & configured, otherwise seamlessly use Offline Engine
                val online = isOnline() && apiKey.isNotBlank()
                if (online) {
                    val geminiResult = geminiEngine.translate(text, sourceLanguage, targetLanguage, contextText)
                    if (geminiResult.isSuccess) {
                        geminiResult
                    } else {
                        // Fallback to offline engine upon network error
                        offlineEngine.translate(text, sourceLanguage, targetLanguage, contextText)
                    }
                } else {
                    // Directly use offline engine without attempting network
                    offlineEngine.translate(text, sourceLanguage, targetLanguage, contextText)
                }
            }
        }
    }
}
