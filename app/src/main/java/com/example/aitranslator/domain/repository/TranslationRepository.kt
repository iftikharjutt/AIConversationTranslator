package com.example.aitranslator.domain.repository

import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment
import kotlinx.coroutines.flow.Flow
import java.io.File

interface TranslationRepository {
    suspend fun createConversation(title: String, sourceLanguage: String, targetLanguage: String): Long
    suspend fun getConversation(conversationId: Long): Conversation?
    fun observeConversation(conversationId: Long): Flow<Conversation?>
    fun observeAllConversations(): Flow<List<Conversation>>
    suspend fun deleteConversation(conversationId: Long)
    suspend fun updateConversationTitle(conversationId: Long, newTitle: String)

    suspend fun addSegment(segment: TranslationSegment): Long
    suspend fun getSegment(segmentId: Long): TranslationSegment?
    suspend fun getSegmentsForConversation(conversationId: Long): List<TranslationSegment>
    fun observeSegments(conversationId: Long): Flow<List<TranslationSegment>>
    fun observeLatestSegments(limit: Int): Flow<List<TranslationSegment>>
    suspend fun updateSegmentStatus(segmentId: Long, status: SegmentStatus, errorMessage: String? = null)
    suspend fun updateSegmentResult(segmentId: Long, originalText: String, translatedText: String, status: SegmentStatus)
    suspend fun deleteSegment(segmentId: Long)
    suspend fun retrySegment(segmentId: Long)

    suspend fun testBackendConnection(): Result<String>
    suspend fun transcribeAudio(audioFile: File, languageCode: String): Result<String>
    suspend fun translateText(text: String, sourceLanguage: String, targetLanguage: String, context: String?): Result<String>
    suspend fun getRecentContext(conversationId: Long, currentSegmentNumber: Int, windowSize: Int = 3): String

    // Direct Gemini Cloud AI Pipeline
    suspend fun processAudioWithGemini(
        audioFile: File,
        sourceLanguage: String,
        targetLanguage: String,
        context: String?,
        apiKey: String,
        model: String
    ): Result<Pair<String, String>>

    suspend fun testGeminiApiKey(apiKey: String, model: String): Result<String>
    suspend fun fetchEligibleModels(apiKey: String): Result<List<com.example.aitranslator.util.GeminiModelOption>>
}
