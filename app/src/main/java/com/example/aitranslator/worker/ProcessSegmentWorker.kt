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
            repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSCRIBING)

            val geminiKey = preferenceManager.geminiApiKey.first()
            val geminiModel = preferenceManager.geminiModel.first()

            // 3. Fetch rolling conversational context
            val context = repository.getRecentContext(conversationId, segment.segmentNumber, windowSize = 3)

            if (geminiKey.isNotBlank()) {
                repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSLATING)

                val geminiResult = repository.processAudioWithGemini(
                    audioFile = audioFile,
                    sourceLanguage = conversation.sourceLanguage,
                    targetLanguage = conversation.targetLanguage,
                    context = context,
                    apiKey = geminiKey,
                    model = geminiModel
                )

                if (geminiResult.isFailure) {
                    val error = geminiResult.exceptionOrNull()
                    if (isTransientError(error)) {
                        return Result.retry()
                    } else {
                        repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, error?.message ?: "Gemini translation failed")
                        return Result.failure()
                    }
                }

                val (transcription, translation) = geminiResult.getOrThrow()
                repository.updateSegmentResult(segmentId, transcription, translation, SegmentStatus.COMPLETED)
                handleAudioRetention(audioFile)
                return Result.success()
            } else {
                // Fallback to legacy backend proxy if no Gemini key is set
                val transcribeResult = repository.transcribeAudio(audioFile, conversation.sourceLanguage)
                if (transcribeResult.isFailure) {
                    val error = transcribeResult.exceptionOrNull()
                    if (isTransientError(error)) {
                        return Result.retry()
                    } else {
                        repository.updateSegmentStatus(
                            segmentId,
                            SegmentStatus.FAILED,
                            "Gemini API key is required. Tap 'Add API Key' on the home screen to configure."
                        )
                        return Result.failure()
                    }
                }

                val transcribedText = transcribeResult.getOrNull().orEmpty()
                if (transcribedText.isBlank()) {
                    repository.updateSegmentResult(segmentId, "[No speech detected]", "[No speech detected]", SegmentStatus.COMPLETED)
                    handleAudioRetention(audioFile)
                    return Result.success()
                }

                repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSLATING)
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
                repository.updateSegmentResult(segmentId, transcribedText, translatedText, SegmentStatus.COMPLETED)
                handleAudioRetention(audioFile)
                return Result.success()
            }
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
