package com.example.aitranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aitranslator.data.preferences.PreferenceManager
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
    val isDebugMode: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferenceManager.segmentDurationSeconds,
        preferenceManager.autoPlayTts,
        preferenceManager.saveAudio,
        preferenceManager.deleteAudioAfterProcessing,
        preferenceManager.backendUrl,
        preferenceManager.isDebugMode
    ) { args: Array<Any> ->
        SettingsUiState(
            segmentDuration = args[0] as Int,
            autoPlayTts = args[1] as Boolean,
            saveAudio = args[2] as Boolean,
            deleteAudioAfterProcessing = args[3] as Boolean,
            backendUrl = args[4] as String,
            isDebugMode = args[5] as Boolean
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
}
