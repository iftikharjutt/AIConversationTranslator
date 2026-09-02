package com.example.aitranslator.worker

import com.example.aitranslator.data.preferences.PreferenceManager
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.offline.TranslationEngineDispatcher
import com.example.aitranslator.util.FileUtils
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SegmentProcessor @Inject constructor(
    private val repository: TranslationRepository,
    private val preferenceManager: PreferenceManager,
    private val dispatcher: TranslationEngineDispatcher
) {
    suspend fun process(segmentId: Long, conversationId: Long): ProcessingResult {
        val segment = repository.getSegment(segmentId) ?: return ProcessingResult.FAILED
        val conversation = repository.getConversation(conversationId) ?: return ProcessingResult.FAILED
        val audioFile = File(segment.audioPath)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, "Audio file is missing or empty")
            return ProcessingResult.FAILED
        }

        return try {
            val mode = preferenceManager.translationMode.first()
            val apiKey = preferenceManager.geminiApiKey.first()
            val model = preferenceManager.geminiModel.first()
            val context = repository.getRecentContext(conversationId, segment.segmentNumber, windowSize = 3)

            if (mode == com.example.aitranslator.domain.model.TranslationMode.OFFLINE) {
                repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSLATING)
                val textToTranslate = segment.originalText.ifBlank { "Audio Segment #${segment.segmentNumber}" }
                val result = dispatcher.translate(textToTranslate, conversation.sourceLanguage, conversation.targetLanguage, context)
                if (result.isSuccess) {
                    val res = result.getOrNull()!!
                    repository.updateSegmentResult(segmentId, segment.originalText.ifBlank { "[Offline Audio Segment #${segment.segmentNumber}]" }, res.translatedText, SegmentStatus.COMPLETED)
                    handleAudioRetention(audioFile)
                    ProcessingResult.SUCCESS
                } else {
                    repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, "Offline translation failed")
                    ProcessingResult.FAILED
                }
            } else if (apiKey.isNotBlank()) {
                repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSLATING)
                val result = repository.processAudioWithGemini(audioFile, conversation.sourceLanguage, conversation.targetLanguage, context, apiKey, model)
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    if (ProcessSegmentWorker.isTransientError(error)) ProcessingResult.RETRY
                    else {
                        repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, error?.message ?: "Gemini processing failed")
                        ProcessingResult.FAILED
                    }
                } else {
                    val pair = result.getOrNull() ?: Pair("", "")
                    repository.updateSegmentResult(segmentId, pair.first.ifBlank { "[No speech detected]" }, pair.second.ifBlank { "[No speech detected]" }, SegmentStatus.COMPLETED)
                    handleAudioRetention(audioFile)
                    ProcessingResult.SUCCESS
                }
            } else {
                repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSCRIBING)
                val transcribe = repository.transcribeAudio(audioFile, conversation.sourceLanguage)
                if (transcribe.isFailure) {
                    val error = transcribe.exceptionOrNull()
                    if (ProcessSegmentWorker.isTransientError(error)) ProcessingResult.RETRY
                    else {
                        repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, error?.message ?: "Audio transcription failed. Configure Gemini API key in Settings.")
                        ProcessingResult.FAILED
                    }
                } else {
                    val text = transcribe.getOrNull().orEmpty().trim()
                    if (text.isBlank()) {
                        repository.updateSegmentResult(segmentId, "[No speech detected]", "[No speech detected]", SegmentStatus.COMPLETED)
                        handleAudioRetention(audioFile)
                        ProcessingResult.SUCCESS
                    } else {
                        repository.updateSegmentStatus(segmentId, SegmentStatus.TRANSLATING)
                        val translate = repository.translateText(text, conversation.sourceLanguage, conversation.targetLanguage, context)
                        if (translate.isFailure) {
                            val error = translate.exceptionOrNull()
                            if (ProcessSegmentWorker.isTransientError(error)) ProcessingResult.RETRY
                            else {
                                repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, error?.message ?: "AI translation failed")
                                ProcessingResult.FAILED
                            }
                        } else {
                            repository.updateSegmentResult(segmentId, text, translate.getOrNull().orEmpty().trim(), SegmentStatus.COMPLETED)
                            handleAudioRetention(audioFile)
                            ProcessingResult.SUCCESS
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (ProcessSegmentWorker.isTransientError(e)) ProcessingResult.RETRY
            else {
                repository.updateSegmentStatus(segmentId, SegmentStatus.FAILED, e.message ?: "Processing error")
                ProcessingResult.FAILED
            }
        }
    }

    private suspend fun handleAudioRetention(audioFile: File) {
        if (preferenceManager.deleteAudioAfterProcessing.first() || !preferenceManager.saveAudio.first()) {
            FileUtils.deleteAudioFile(audioFile.absolutePath)
        }
    }

    enum class ProcessingResult { SUCCESS, RETRY, FAILED }
}
