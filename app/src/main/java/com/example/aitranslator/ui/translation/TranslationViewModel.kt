package com.example.aitranslator.ui.translation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.model.TranslationSegment
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.tts.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranslationUiState(
    val conversation: Conversation? = null,
    val segments: List<TranslationSegment> = emptyList(),
    val isSpeaking: Boolean = false
)

@HiltViewModel
class TranslationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TranslationRepository,
    private val ttsManager: TextToSpeechManager
) : ViewModel() {

    private val conversationId: Long = checkNotNull(savedStateHandle["conversationId"])

    val uiState: StateFlow<TranslationUiState> = combine(
        repository.observeConversation(conversationId),
        repository.observeSegments(conversationId),
        ttsManager.isSpeaking
    ) { conv, segs, speaking ->
        TranslationUiState(
            conversation = conv,
            segments = segs,
            isSpeaking = speaking
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TranslationUiState()
    )

    fun speakTranslation(text: String, targetLanguage: String) {
        ttsManager.speak(text, targetLanguage)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun retrySegment(segmentId: Long) {
        viewModelScope.launch {
            repository.retrySegment(segmentId)
        }
    }

    fun deleteSegment(segmentId: Long) {
        viewModelScope.launch {
            repository.deleteSegment(segmentId)
        }
    }

    fun updateTitle(newTitle: String) {
        viewModelScope.launch {
            repository.updateConversationTitle(conversationId, newTitle)
        }
    }
}
