package com.example.aitranslator.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: TranslationRepository
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = repository.observeAllConversations()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }
}
