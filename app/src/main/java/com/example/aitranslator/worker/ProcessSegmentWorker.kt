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
            val apiKey = preferenceManager.geminiApiKey.first()
            val model = preferenceManager.geminiModel.first()
            val context = repository.getRecentContext(conversationId, segment.segmentNumber, windowSize = 3)

            if (apiKey.isNotBlank()) {
                // Direct Gemini Multimodal Pipeline (Audio -> Transcription + Context-Aware Translation)
                repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSLATING)

                val geminiResult = repository.processAudioWithGemini(
                    audioFile = audioFile,
                    sourceLanguage = conversation.sourceLanguage,
                    targetLanguage = conversation.targetLanguage,
                    context = context,
                    apiKey = apiKey,
                    model = model
                )

                if (geminiResult.isFailure) {
                    val error = geminiResult.exceptionOrNull()
                    if (isTransientError(error)) {
                        return Result.retry()
                    } else {
                        repository.updateSegmentStatus(
                            segmentId,
                            SegmentStatus.FAILED,
                            error?.message ?: "Gemini processing failed"
                        )
                        return Result.failure()
                    }
                }

                val (transcribedText, translatedText) = geminiResult.getOrNull() ?: Pair("", "")
                val finalOrig = if (transcribedText.isBlank()) "[No speech detected]" else transcribedText
                val finalTrans = if (translatedText.isBlank()) "[No speech detected]" else translatedText

                repository.updateSegmentResult(segmentId, finalOrig, finalTrans, SegmentStatus.COMPLETED)
                handleAudioRetention(audioFile)
                return Result.success()
            } else {
                // Fallback to Backend Proxy Service if configured
                repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSCRIBING)

                val transcribeResult = repository.transcribeAudio(audioFile, conversation.sourceLanguage)
                if (transcribeResult.isFailure) {
                    val error = transcribeResult.exceptionOrNull()
                    if (isTransientError(error)) {
                        return Result.retry()
                    } else {
                        repository.updateSegmentStatus(
                            segmentId,
                            SegmentStatus.FAILED,
                            error?.message ?: "Audio transcription failed. Configure Gemini API key in Settings."
                        )
                        return Result.failure()
                    }
                }

                val transcribedText = transcribeResult.getOrNull().orEmpty().trim()
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
                        repository.updateSegmentStatus(
                            segmentId,
                            SegmentStatus.FAILED,
                            error?.message ?: "AI translation failed"
                        )
                        return Result.failure()
                    }
                }

                val translatedText = translateResult.getOrNull().orEmpty().trim()
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
