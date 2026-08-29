package com.example.aitranslator.ui.recording

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aitranslator.audio.RecordingService
import com.example.aitranslator.audio.SegmentManager
import com.example.aitranslator.data.preferences.PreferenceManager
import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.model.Language
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.tts.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordingUiState(
    val conversation: Conversation? = null,
    val segments: List<TranslationSegment> = emptyList(),
    val isRecording: Boolean = false,
    val currentSegmentNumber: Int = 1,
    val totalElapsedSeconds: Long = 0L,
    val segmentElapsedSeconds: Long = 0L,
    val segmentTargetSeconds: Int = 180,
    val amplitude: Float = 0f,
    val errorMessage: String? = null
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: TranslationRepository,
    private val segmentManager: SegmentManager,
    private val preferenceManager: PreferenceManager,
    private val ttsManager: TextToSpeechManager
) : ViewModel() {

    private val conversationId: Long = checkNotNull(savedStateHandle["conversationId"])

    private val _totalSeconds = MutableStateFlow(0L)
    private val _segmentSeconds = MutableStateFlow(0L)
    private val _amplitude = MutableStateFlow(0f)
    private var timerJob: Job? = null

    private var previousCompletedCount = 0

    val uiState: StateFlow<RecordingUiState> = combine(
        repository.observeConversation(conversationId),
        repository.observeSegments(conversationId),
        segmentManager.isRecording,
        segmentManager.currentSegmentNumber,
        _totalSeconds,
        _segmentSeconds,
        preferenceManager.segmentDurationSeconds,
        _amplitude,
        segmentManager.errorMessage
    ) { args ->
        val conv = args[0] as? Conversation
        val segs = (args[1] as? List<*>)?.filterIsInstance<TranslationSegment>() ?: emptyList()
        val recording = args[2] as Boolean
        val segNum = args[3] as Int
        val totalSec = args[4] as Long
        val segSec = args[5] as Long
        val targetSec = args[6] as Int
        val amp = args[7] as Float
        val err = args[8] as? String

        // Check for auto-play TTS if newly completed segment arrives
        checkAutoPlay(segs, conv)

        RecordingUiState(
            conversation = conv,
            segments = segs,
            isRecording = recording,
            currentSegmentNumber = segNum,
            totalElapsedSeconds = totalSec,
            segmentElapsedSeconds = segSec,
            segmentTargetSeconds = targetSec,
            amplitude = amp,
            errorMessage = err
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RecordingUiState()
    )

    init {
        startRecordingSession()
        listenToAmplitude()
    }

    private fun startRecordingSession() {
        viewModelScope.launch {
            val duration = preferenceManager.segmentDurationSeconds.first()
            RecordingService.startService(context, conversationId, duration)
            startTimer(duration)
        }
    }

    private fun startTimer(segmentDuration: Int) {
        timerJob?.cancel()
        _totalSeconds.value = 0L
        _segmentSeconds.value = 0L

        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _totalSeconds.value++
                _segmentSeconds.value = (_totalSeconds.value % segmentDuration)
            }
        }
    }

    private fun listenToAmplitude() {
        viewModelScope.launch {
            segmentManager.getAudioAmplitudeFlow()?.collect { amp ->
                _amplitude.value = amp
            }
        }
    }

    private fun checkAutoPlay(segments: List<TranslationSegment>, conversation: Conversation?) {
        if (conversation == null) return
        val completed = segments.filter { it.status == SegmentStatus.COMPLETED && it.translatedText.isNotBlank() }
        if (completed.size > previousCompletedCount) {
            val latest = completed.last()
            previousCompletedCount = completed.size
            viewModelScope.launch {
                val autoPlay = preferenceManager.autoPlayTts.first()
                if (autoPlay) {
                    ttsManager.speak(latest.translatedText, conversation.targetLanguage)
                }
            }
        }
    }

    fun stopRecording(onStopped: () -> Unit) {
        timerJob?.cancel()
        RecordingService.stopService(context)
        onStopped()
    }

    fun speakTranslation(text: String, languageCode: String) {
        ttsManager.speak(text, languageCode)
    }

    fun retrySegment(segmentId: Long) {
        viewModelScope.launch {
            repository.retrySegment(segmentId)
            // Re-enqueue Worker
            segmentManager.onSegmentCompleted(1, java.io.File(""), 0, 0)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
