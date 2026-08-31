package com.example.aitranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aitranslator.data.preferences.PreferenceManager
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.util.Constants
import com.example.aitranslator.util.GeminiModelOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val segmentDuration: Int = 180,
    val autoPlayTts: Boolean = false,
    val saveAudio: Boolean = true,
    val deleteAudioAfterProcessing: Boolean = false,
    val backendUrl: String = "",
    val isDebugMode: Boolean = true,
    val geminiApiKey: String = "",
    val geminiModel: String = Constants.GEMINI_DEFAULT_MODEL
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val repository: TranslationRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            preferenceManager.segmentDurationSeconds,
            preferenceManager.autoPlayTts,
            preferenceManager.saveAudio,
            preferenceManager.deleteAudioAfterProcessing
        ) { duration, autoTts, saveAud, delAud ->
            SettingsPart1(duration, autoTts, saveAud, delAud)
        },
        combine(
            preferenceManager.backendUrl,
            preferenceManager.isDebugMode,
            preferenceManager.geminiApiKey,
            preferenceManager.geminiModel
        ) { url, debug, apiKey, model ->
            SettingsPart2(url, debug, apiKey, model)
        }
    ) { p1, p2 ->
        SettingsUiState(
            segmentDuration = p1.duration,
            autoPlayTts = p1.autoTts,
            saveAudio = p1.saveAud,
            deleteAudioAfterProcessing = p1.delAud,
            backendUrl = p2.url,
            isDebugMode = p2.debug,
            geminiApiKey = p2.apiKey,
            geminiModel = p2.model
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState()
    )

    fun setSegmentDuration(seconds: Int) {
        viewModelScope.launch { preferenceManager.setSegmentDuration(seconds) }
    }

    fun setAutoPlayTts(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.setAutoPlayTts(enabled) }
    }

    fun setSaveAudio(save: Boolean) {
        viewModelScope.launch { preferenceManager.setSaveAudio(save) }
    }

    fun setDeleteAudioAfterProcessing(delete: Boolean) {
        viewModelScope.launch { preferenceManager.setDeleteAudioAfterProcessing(delete) }
    }

    fun setBackendUrl(url: String) {
        viewModelScope.launch { preferenceManager.setBackendUrl(url) }
    }

    fun setDebugMode(debug: Boolean) {
        viewModelScope.launch { preferenceManager.setDebugMode(debug) }
    }

    fun setGeminiApiKey(apiKey: String) {
        viewModelScope.launch { preferenceManager.setGeminiApiKey(apiKey) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { preferenceManager.clearGeminiApiKey() }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch { preferenceManager.setGeminiModel(model) }
    }

    fun testGeminiApiKey(apiKey: String, model: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.testGeminiApiKey(apiKey, model)
            if (result.isSuccess) {
                onResult(true, "Successfully connected to Gemini API (${model})!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Connection test failed")
            }
        }
    }

    fun fetchEligibleModels(apiKey: String, onResult: (List<GeminiModelOption>?, String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.fetchEligibleModels(apiKey)
            if (result.isSuccess) {
                onResult(result.getOrNull(), null)
            } else {
                onResult(null, result.exceptionOrNull()?.message ?: "Failed to query account models")
            }
        }
    }

    fun testBackendConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.testBackendConnection()
            if (result.isSuccess) {
                onResult(true, "Backend is reachable and healthy (status: ${result.getOrNull()})!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Could not connect to backend")
            }
        }
    }
}

private data class SettingsPart1(
    val duration: Int,
    val autoTts: Boolean,
    val saveAud: Boolean,
    val delAud: Boolean
)

private data class SettingsPart2(
    val url: String,
    val debug: Boolean,
    val apiKey: String,
    val model: String
)

