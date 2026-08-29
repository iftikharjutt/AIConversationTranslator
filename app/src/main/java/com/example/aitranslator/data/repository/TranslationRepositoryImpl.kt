package com.example.aitranslator.data.repository

import com.example.aitranslator.data.local.ConversationDao
import com.example.aitranslator.data.local.ConversationEntity
import com.example.aitranslator.data.local.SegmentDao
import com.example.aitranslator.data.local.SegmentEntity
import com.example.aitranslator.data.remote.SpeechApi
import com.example.aitranslator.data.remote.TranslateRequest
import com.example.aitranslator.data.remote.TranslationApi
import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.util.FileUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val segmentDao: SegmentDao,
    private val speechApi: SpeechApi,
    private val translationApi: TranslationApi
) : TranslationRepository {

    override suspend fun createConversation(title: String, sourceLanguage: String, targetLanguage: String): Long {
        val entity = ConversationEntity(
            title = title,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage
        )
        return conversationDao.insertConversation(entity)
    }

    override suspend fun getConversation(conversationId: Long): Conversation? {
        return conversationDao.getConversationById(conversationId)?.toDomain()
    }

    override fun observeConversation(conversationId: Long): Flow<Conversation?> {
        return conversationDao.observeConversationById(conversationId).map { it?.toDomain() }
    }

    override fun observeAllConversations(): Flow<List<Conversation>> {
        return conversationDao.observeAllConversations().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun deleteConversation(conversationId: Long) {
        // Cleanup local audio files for segments
        val segments = segmentDao.getSegmentsForConversation(conversationId)
        for (seg in segments) {
            FileUtils.deleteAudioFile(seg.audioPath)
        }
        conversationDao.deleteConversationById(conversationId)
    }

    override suspend fun updateConversationTitle(conversationId: Long, newTitle: String) {
        conversationDao.updateTitle(conversationId, newTitle)
    }

    override suspend fun addSegment(segment: TranslationSegment): Long {
        return segmentDao.insertSegment(SegmentEntity.fromDomain(segment))
    }

    override suspend fun getSegment(segmentId: Long): TranslationSegment? {
        return segmentDao.getSegmentById(segmentId)?.toDomain()
    }

    override suspend fun getSegmentsForConversation(conversationId: Long): List<TranslationSegment> {
        return segmentDao.getSegmentsForConversation(conversationId).map { it.toDomain() }
    }

    override fun observeSegments(conversationId: Long): Flow<List<TranslationSegment>> {
        return segmentDao.observeSegmentsForConversation(conversationId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun updateSegmentStatus(segmentId: Long, status: SegmentStatus, errorMessage: String?) {
        segmentDao.updateStatus(segmentId, status, errorMessage)
    }

    override suspend fun updateSegmentResult(segmentId: Long, originalText: String, translatedText: String, status: SegmentStatus) {
        segmentDao.updateResult(segmentId, originalText, translatedText, status)
    }

    override suspend fun deleteSegment(segmentId: Long) {
        val segment = segmentDao.getSegmentById(segmentId)
        segment?.let { FileUtils.deleteAudioFile(it.audioPath) }
        segmentDao.deleteSegmentById(segmentId)
    }

    override suspend fun retrySegment(segmentId: Long) {
        segmentDao.updateStatus(segmentId, SegmentStatus.RECORDED, null)
    }

    override suspend fun transcribeAudio(audioFile: File, languageCode: String): Result<String> {
        return try {
            if (!audioFile.exists()) {
                return Result.failure(IllegalStateException("Audio file not found: ${audioFile.absolutePath}"))
            }

            val requestFile = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, requestFile)
            val langBody = languageCode.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = speechApi.transcribeAudio(audioPart, langBody)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (!body.error.isNullOrBlank()) {
                    Result.failure(Exception(body.error))
                } else {
                    Result.success(body.text)
                }
            } else {
                val err = response.errorBody()?.string() ?: "Transcription failed with code ${response.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun translateText(text: String, sourceLanguage: String, targetLanguage: String, context: String?): Result<String> {
        return try {
            val req = TranslateRequest(
                text = text,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                context = context
            )
            val response = translationApi.translate(req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (!body.error.isNullOrBlank()) {
                    Result.failure(Exception(body.error))
                } else {
                    Result.success(body.translation ?: "")
                }
            } else {
                val err = response.errorBody()?.string() ?: "Translation failed with code ${response.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRecentContext(conversationId: Long, currentSegmentNumber: Int, windowSize: Int): String {
        val recentSegments = segmentDao.getRecentCompletedSegments(conversationId, currentSegmentNumber, windowSize)
        // Order ascending by segment number for chronological context flow
        return recentSegments.reversed().joinToString("\n") { seg ->
            "Original: ${seg.originalText}\nTranslated: ${seg.translatedText}"
        }
    }
}
