package com.example.aitranslator.audio

import android.content.Context
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.worker.SegmentProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SegmentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TranslationRepository,
    private val processor: SegmentProcessor
) : AudioSegmentListener {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: AudioRecorder? = null

    private val _currentSegmentNumber = MutableStateFlow(1)
    val currentSegmentNumber: StateFlow<Int> = _currentSegmentNumber.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var activeConversationId: Long = 0

    fun startContinuousRecording(conversationId: Long, segmentDurationSeconds: Int) {
        activeConversationId = conversationId
        _currentSegmentNumber.value = 1
        _errorMessage.value = null
        recorder = AudioRecorder(context, segmentDurationSeconds).apply {
            setSegmentListener(this@SegmentManager)
            startRecording(conversationId)
        }
        _isRecording.value = true
    }

    fun stopContinuousRecording() {
        recorder?.stopRecording()
        recorder = null
        _isRecording.value = false
    }

    fun getAudioAmplitudeFlow() = recorder?.audioAmplitude

    override fun onSegmentCompleted(segmentNumber: Int, wavFile: File, startTime: Long, endTime: Long) {
        _currentSegmentNumber.value = segmentNumber + 1
        // Process immediately in a dedicated coroutine. Recording never waits for translation,
        // and completed segments no longer enter a WorkManager queue.
        val conversationId = activeConversationId
        coroutineScope.launch {
            val segment = TranslationSegment(
                conversationId = conversationId,
                segmentNumber = segmentNumber,
                audioPath = wavFile.absolutePath,
                startTime = startTime,
                endTime = endTime,
                status = SegmentStatus.RECORDED
            )
            val segmentId = repository.addSegment(segment)
            when (processor.process(segmentId, conversationId)) {
                SegmentProcessor.ProcessingResult.RETRY -> {
                    // Network/rate-limit failures are recorded instead of silently queuing
                    // the live recording pipeline. The next segment can still process immediately.
                    repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, "Temporary processing failure; please retry")
                }
                SegmentProcessor.ProcessingResult.FAILED,
                SegmentProcessor.ProcessingResult.SUCCESS -> Unit
            }
        }
    }

    override fun onRecordingError(error: String) {
        _errorMessage.value = error
    }
}
