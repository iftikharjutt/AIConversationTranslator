package com.example.aitranslator.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aitranslator.data.preferences.PreferenceManager
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.util.Constants
import com.example.aitranslator.util.FileUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException

@HiltWorker
class ProcessSegmentWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: TranslationRepository,
    private val preferenceManager: PreferenceManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val segmentId = inputData.getLong(Constants.WORKER_SEGMENT_ID_KEY, -1L)
        val conversationId = inputData.getLong(Constants.WORKER_CONVERSATION_ID_KEY, -1L)

        if (segmentId == -1L || conversationId == -1L) {
            return Result.failure()
        }

        val segment = repository.getSegment(segmentId) ?: return Result.failure()
        val conversation = repository.getConversation(conversationId) ?: return Result.failure()

        // 1. Verify Audio File exists
        val audioFile = File(segment.audioPath)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, "Audio file is missing or empty")
            return Result.failure()
        }

        try {
            // 2. Set Status: UPLOADING / TRANSCRIBING
            repository.updateSegmentStatus(segmentId, SegmentStatus.UPLOADING)
            repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSCRIBING)

            // 3. Perform Speech-To-Text
            val transcribeResult = repository.transcribeAudio(audioFile, conversation.sourceLanguage)
            if (transcribeResult.isFailure) {
                val error = transcribeResult.exceptionOrNull()
                if (isTransientError(error)) {
                    return Result.retry()
                } else {
                    repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, error?.message ?: "Transcription failed")
                    return Result.failure()
                }
            }

            val transcribedText = transcribeResult.getOrNull().orEmpty()
            if (transcribedText.isBlank()) {
                // Empty speech detected in this audio segment
                repository.updateSegmentResult(segmentId, "[No speech detected]", "[No speech detected]", SegmentStatus.COMPLETED)
                handleAudioRetention(audioFile)
                return Result.success()
            }

            // 4. Set Status: TRANSLATING
            repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSLATING)

            // 5. Fetch rolling conversational context (e.g. last 3 segments)
            val context = repository.getRecentContext(conversationId, segment.segmentNumber, windowSize = 3)

            // 6. Perform Context-Aware AI Translation
            val translateResult = repository.translateText(
                text = transcribedText,
                sourceLanguage = conversation.sourceLanguage,
                targetLanguage = conversation.targetLanguage,
                context = context
            )

            if (translateResult.isFailure) {
                val error = translateResult.exceptionOrNull()
                if (isTransientError(error)) {
                    return Result.retry()
                } else {
                    repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, error?.message ?: "Translation failed")
                    return Result.failure()
                }
            }

            val translatedText = translateResult.getOrNull().orEmpty()

            // 7. Save Final Result to Database and Mark COMPLETED
            repository.updateSegmentResult(segmentId, transcribedText, translatedText, SegmentStatus.COMPLETED)

            // 8. Handle Privacy / Audio Retention
            handleAudioRetention(audioFile)

            return Result.success()
        } catch (e: Exception) {
            if (isTransientError(e)) {
                return Result.retry()
            }
            repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, e.message ?: "Processing error")
            return Result.failure()
        }
    }

    private suspend fun handleAudioRetention(audioFile: File) {
        val deleteAfterProcessing = preferenceManager.deleteAudioAfterProcessing.first()
        val saveAudio = preferenceManager.saveAudio.first()
        if (deleteAfterProcessing || !saveAudio) {
            FileUtils.deleteAudioFile(audioFile.absolutePath)
        }
    }

    private fun isTransientError(throwable: Throwable?): Boolean {
        return throwable is IOException ||
                throwable?.message?.contains("timeout", ignoreCase = true) == true ||
                throwable?.message?.contains("connect", ignoreCase = true) == true
    }
}
