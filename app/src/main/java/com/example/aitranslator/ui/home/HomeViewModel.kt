package com.example.aitranslator.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aitranslator.data.preferences.PreferenceManager
import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.model.Language
import com.example.aitranslator.domain.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val sourceLanguage: Language = Language.defaultSource(),
    val targetLanguage: Language = Language.defaultTarget(),
    val recentConversations: List<Conversation> = emptyList(),
    val segmentDurationSeconds: Int = 180,
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TranslationRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        preferenceManager.sourceLanguageCode,
        preferenceManager.targetLanguageCode,
        repository.observeAllConversations(),
        preferenceManager.segmentDurationSeconds
    ) { srcCode, tgtCode, conversations, duration ->
        HomeUiState(
            sourceLanguage = Language.getByCode(srcCode),
            targetLanguage = Language.getByCode(tgtCode),
            recentConversations = conversations.take(5),
            segmentDurationSeconds = duration,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomeUiState()
    )

    fun selectSourceLanguage(language: Language) {
        viewModelScope.launch {
            preferenceManager.setSourceLanguage(language.code)
        }
    }

    fun selectTargetLanguage(language: Language) {
        viewModelScope.launch {
            preferenceManager.setTargetLanguage(language.code)
        }
    }

    fun swapLanguages() {
        viewModelScope.launch {
            preferenceManager.swapLanguages()
        }
    }

    fun createConversationAndStart(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val timeString = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
            val title = "${state.sourceLanguage.name} → ${state.targetLanguage.name} ($timeString)"
            val convId = repository.createConversation(
                title = title,
                sourceLanguage = state.sourceLanguage.code,
                targetLanguage = state.targetLanguage.code
            )
            onCreated(convId)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }
}
