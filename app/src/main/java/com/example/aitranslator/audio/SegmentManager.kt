package com.example.aitranslator.audio

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.util.Constants
import com.example.aitranslator.worker.ProcessSegmentWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SegmentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TranslationRepository
) : AudioSegmentListener {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
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
        coroutineScope.launch {
            // 1. Insert segment into Database with status RECORDED
            val segment = TranslationSegment(
                conversationId = activeConversationId,
                segmentNumber = segmentNumber,
                audioPath = wavFile.absolutePath,
                startTime = startTime,
                endTime = endTime,
                status = SegmentStatus.RECORDED
            )
            val segmentId = repository.addSegment(segment)

            // 2. Enqueue WorkManager job asynchronously (RECORDING NEVER WAITS)
            enqueueProcessingWorker(segmentId, activeConversationId)
        }
    }

    override fun onRecordingError(error: String) {
        _errorMessage.value = error
    }

    private fun enqueueProcessingWorker(segmentId: Long, conversationId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ProcessSegmentWorker>()
            .setInputData(
                workDataOf(
                    Constants.WORKER_SEGMENT_ID_KEY to segmentId,
                    Constants.WORKER_CONVERSATION_ID_KEY to conversationId
                )
            )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "process_segment_${segmentId}",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
}
