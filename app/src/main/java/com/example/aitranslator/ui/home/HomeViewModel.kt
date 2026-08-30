package com.example.aitranslator.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aitranslator.audio.RecordingService
import com.example.aitranslator.audio.SegmentManager
import com.example.aitranslator.data.preferences.PreferenceManager
import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.model.Language
import com.example.aitranslator.domain.model.TranslationSegment
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.tts.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val sourceLanguage: Language = Language.defaultSource(),
    val targetLanguage: Language = Language.defaultTarget(),
    val recentConversations: List<Conversation> = emptyList(),
    val segmentDurationSeconds: Int = 10,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-1.5-flash",
    val isRecording: Boolean = false,
    val activeConversationId: Long? = null,
    val activeConversation: Conversation? = null,
    val liveSegments: List<TranslationSegment> = emptyList(),
    val amplitude: Float = 0f,
    val currentSegmentNumber: Int = 1,
    val elapsedRecordingSeconds: Long = 0L,
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val app: Application,
    private val repository: TranslationRepository,
    private val preferenceManager: PreferenceManager,
    private val segmentManager: SegmentManager,
    private val ttsManager: TextToSpeechManager
) : AndroidViewModel(app) {

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    private val _elapsedSeconds = MutableStateFlow(0L)
    private var timerJob: Job? = null

    // Observe active conversation's segments or latest segments in general
    private val activeSegmentsFlow = _activeConversationId.flatMapLatest { convId ->
        if (convId != null && convId > 0) {
            repository.observeSegments(convId)
        } else {
            repository.observeLatestSegments(20)
        }
    }

    private val activeConvFlow = _activeConversationId.flatMapLatest { convId ->
        if (convId != null && convId > 0) {
            repository.observeConversation(convId)
        } else {
            flowOf(null)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            preferenceManager.sourceLanguageCode,
            preferenceManager.targetLanguageCode,
            repository.observeAllConversations(),
            preferenceManager.segmentDurationSeconds,
            preferenceManager.geminiApiKey
        ) { srcCode, tgtCode, conversations, duration, apiKey ->
            Tuple5(srcCode, tgtCode, conversations, duration, apiKey)
        },
        combine(
            preferenceManager.geminiModel,
            segmentManager.isRecording,
            segmentManager.currentSegmentNumber,
            _activeConversationId,
            activeConvFlow
        ) { model, isRec, segNum, activeId, activeConv ->
            Tuple5(model, isRec, segNum, activeId, activeConv)
        },
        combine(
            activeSegmentsFlow,
            _elapsedSeconds
        ) { segments, elapsed ->
            Pair(segments, elapsed)
        }
    ) { base1, base2, base3 ->
        HomeUiState(
            sourceLanguage = Language.getByCode(base1.v1),
            targetLanguage = Language.getByCode(base1.v2),
            recentConversations = base1.v3.take(5),
            segmentDurationSeconds = base1.v4,
            geminiApiKey = base1.v5,
            geminiModel = base2.v1,
            isRecording = base2.v2,
            currentSegmentNumber = base2.v3,
            activeConversationId = base2.v4,
            activeConversation = base2.v5,
            liveSegments = base3.first,
            elapsedRecordingSeconds = base3.second,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomeUiState()
    )

    init {
        viewModelScope.launch {
            segmentManager.getAudioAmplitudeFlow()?.collect { _ ->
                // Keep amplitude flow active
            }
        }
    }

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

    fun saveGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            preferenceManager.setGeminiApiKey(apiKey)
        }
    }

    fun saveGeminiModel(model: String) {
        viewModelScope.launch {
            preferenceManager.setGeminiModel(model)
        }
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
            _activeConversationId.value = convId
            val duration = preferenceManager.segmentDurationSeconds.first()
            RecordingService.startService(app, convId, duration)
            startTimer()
            onCreated(convId)
        }
    }

    fun toggleLiveRecording() {
        viewModelScope.launch {
            if (uiState.value.isRecording) {
                stopLiveRecording()
            } else {
                createConversationAndStart { }
            }
        }
    }

    fun stopLiveRecording() {
        RecordingService.stopService(app)
        timerJob?.cancel()
        _elapsedSeconds.value = 0L
    }

    private fun startTimer() {
        timerJob?.cancel()
        _elapsedSeconds.value = 0L
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _elapsedSeconds.value += 1
            }
        }
    }

    fun speakTranslation(text: String, languageCode: String) {
        ttsManager.speak(text, languageCode)
    }

    fun retrySegment(segmentId: Long) {
        viewModelScope.launch {
            repository.retrySegment(segmentId)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
            }
            repository.deleteConversation(id)
        }
    }

    fun fetchEligibleModels(apiKey: String, onResult: (List<com.example.aitranslator.util.GeminiModelOption>?, String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.fetchEligibleModels(apiKey)
            if (result.isSuccess) {
                onResult(result.getOrNull(), null)
            } else {
                onResult(null, result.exceptionOrNull()?.message ?: "Failed to query account models")
            }
        }
    }

    fun getAudioAmplitudeFlow() = segmentManager.getAudioAmplitudeFlow()
}

private data class Tuple5<A, B, C, D, E>(
    val v1: A,
    val v2: B,
    val v3: C,
    val v4: D,
    val v5: E
)

