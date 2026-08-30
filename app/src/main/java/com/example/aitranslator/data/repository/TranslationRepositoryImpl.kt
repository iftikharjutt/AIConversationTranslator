package com.example.aitranslator.data.repository

import com.example.aitranslator.data.local.ConversationDao
import com.example.aitranslator.data.local.ConversationEntity
import com.example.aitranslator.data.local.SegmentDao
import com.example.aitranslator.data.local.SegmentEntity
import com.example.aitranslator.data.remote.GeminiApi
import com.example.aitranslator.data.remote.GeminiContent
import com.example.aitranslator.data.remote.GeminiGenerateContentRequest
import com.example.aitranslator.data.remote.GeminiGenerationConfig
import com.example.aitranslator.data.remote.GeminiInlineData
import com.example.aitranslator.data.remote.GeminiPart
import com.example.aitranslator.data.remote.GeminiTranslationResult
import com.example.aitranslator.data.remote.SpeechApi
import com.example.aitranslator.data.remote.TranslateRequest
import com.example.aitranslator.data.remote.TranslationApi
import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.model.Language
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.util.Constants
import com.example.aitranslator.util.FileUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
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
    private val translationApi: TranslationApi,
    private val geminiApi: GeminiApi,
    private val json: Json
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

    override fun observeLatestSegments(limit: Int): Flow<List<TranslationSegment>> {
        return segmentDao.observeLatestSegments(limit).map { list ->
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

    override suspend fun processAudioWithGemini(
        audioFile: File,
        sourceLanguage: String,
        targetLanguage: String,
        context: String?,
        apiKey: String,
        model: String
    ): Result<Pair<String, String>> {
        return try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return Result.failure(IllegalStateException("Audio file is missing or empty"))
            }

            val audioBytes = audioFile.readBytes()
            val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)

            val srcLangObj = Language.getByCode(sourceLanguage)
            val tgtLangObj = Language.getByCode(targetLanguage)

            val promptText = buildString {
                appendLine("You are an expert real-time audio interpreter and translator.")
                appendLine("Analyze this audio recording of a live conversation segment.")
                appendLine("Source language: ${srcLangObj.name} (${srcLangObj.code})")
                appendLine("Target language: ${tgtLangObj.name} (${tgtLangObj.code})")
                if (!context.isNullOrBlank()) {
                    appendLine("\nPrior conversation context for accuracy:")
                    appendLine(context)
                }
                appendLine("\nInstructions:")
                appendLine("1. Accurately transcribe what is spoken in the source language (${srcLangObj.name}).")
                appendLine("2. Translate that speech into natural, fluent ${tgtLangObj.name}.")
                appendLine("3. If the audio is silent, background noise, or unintelligible, set both 'transcription' and 'translation' to '[No speech detected]'.")
                appendLine("4. Return strictly a JSON object conforming to:")
                appendLine("{\"transcription\": \"<transcribed source text>\", \"translation\": \"<translated target text>\"}")
            }

            val request = GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = "audio/wav",
                                    data = base64Audio
                                )
                            ),
                            GeminiPart(text = promptText)
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.2f
                )
            )

            val modelName = if (model.isNotBlank()) model else Constants.GEMINI_DEFAULT_MODEL
            val response = geminiApi.generateContent(
                model = modelName,
                apiKey = apiKey,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    return Result.failure(Exception(body.error.message ?: "Gemini API error ${body.error.code}"))
                }
                val rawText = body.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (rawText.isNullOrBlank()) {
                    return Result.failure(Exception("Gemini returned empty response"))
                }

                val cleanedJson = cleanJsonString(rawText)
                val parsed = try {
                    json.decodeFromString<GeminiTranslationResult>(cleanedJson)
                } catch (_: Exception) {
                    extractJsonFields(cleanedJson)
                }

                Result.success(Pair(parsed.transcription.trim(), parsed.translation.trim()))
            } else {
                val errorBody = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Gemini API request failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testGeminiApiKey(apiKey: String, model: String): Result<String> {
        return try {
            val request = GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = "Respond with 'OK' if you can read this message.")
                        )
                    )
                )
            )
            val modelName = if (model.isNotBlank()) model else Constants.GEMINI_DEFAULT_MODEL
            val response = geminiApi.generateContent(
                model = modelName,
                apiKey = apiKey,
                request = request
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    Result.failure(Exception(body.error.message ?: "API error: ${body.error.code}"))
                } else {
                    val text = body.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    Result.success(text?.trim() ?: "Connected")
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("API Key test failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchEligibleModels(apiKey: String): Result<List<com.example.aitranslator.util.GeminiModelOption>> {
        return try {
            val response = geminiApi.listModels(apiKey = apiKey)
            if (response.isSuccessful && response.body() != null) {
                val rawModels = response.body()?.models.orEmpty()
                // Filter only models that support generateContent
                val eligible = rawModels.filter { model ->
                    val methods = model.supportedGenerationMethods ?: emptyList()
                    methods.contains("generateContent")
                }.map { model ->
                    val cleanId = model.name.removePrefix("models/")
                    val isRec = cleanId.contains("2.5-flash") || cleanId.contains("flash") && !cleanId.contains("lite") && !cleanId.contains("8b")
                    com.example.aitranslator.util.GeminiModelOption(
                        id = cleanId,
                        name = model.displayName ?: cleanId,
                        description = model.description ?: "Supports audio & text translation",
                        isRecommended = isRec
                    )
                }.sortedWith(compareByDescending<com.example.aitranslator.util.GeminiModelOption> { it.isRecommended }
                    .thenByDescending { it.id })
                
                if (eligible.isNotEmpty()) {
                    Result.success(eligible)
                } else {
                    Result.success(Constants.GEMINI_MODELS)
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to fetch models (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanJsonString(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json").trim()
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```").trim()
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }
        return text
    }

    private fun extractJsonFields(raw: String): GeminiTranslationResult {
        val transRegex = "\"transcription\"\\s*:\\s*\"(.*?)\"".toRegex()
        val translRegex = "\"translation\"\\s*:\\s*\"(.*?)\"".toRegex()
        val transcription = transRegex.find(raw)?.groupValues?.get(1) ?: raw
        val translation = translRegex.find(raw)?.groupValues?.get(1) ?: raw
        return GeminiTranslationResult(transcription = transcription, translation = translation)
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

