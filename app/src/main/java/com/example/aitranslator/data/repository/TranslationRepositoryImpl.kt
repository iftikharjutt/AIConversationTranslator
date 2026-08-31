package com.example.aitranslator.data.repository

import com.example.aitranslator.data.local.ConversationDao
import com.example.aitranslator.data.local.ConversationEntity
import com.example.aitranslator.data.local.SegmentDao
import com.example.aitranslator.data.local.SegmentEntity
import com.example.aitranslator.data.remote.*
import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.model.Language
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.util.Constants
import com.example.aitranslator.util.FileUtils
import com.example.aitranslator.util.GeminiModelOption
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

    override suspend fun testBackendConnection(): Result<String> {
        return try {
            val response = translationApi.healthCheck()
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()?.get("status") ?: "ok"
                Result.success(status)
            } else {
                Result.failure(Exception("Backend health check returned ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    override suspend fun processAudioWithGemini(
        audioFile: File,
        sourceLanguage: String,
        targetLanguage: String,
        context: String?,
        apiKey: String,
        model: String
    ): Result<Pair<String, String>> {
        return try {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalArgumentException("Gemini API key is not configured."))
            }
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return Result.failure(IllegalStateException("Audio file does not exist or is empty: ${audioFile.absolutePath}"))
            }

            val sourceLangObj = Language.getByCode(sourceLanguage)
            val targetLangObj = Language.getByCode(targetLanguage)
            val sourceLangName = "${sourceLangObj.name} (${sourceLangObj.code})"
            val targetLangName = "${targetLangObj.name} (${targetLangObj.code})"

            val prompt = buildString {
                appendLine("You are a professional real-time conversational translator.")
                appendLine("Transcribe the spoken audio accurately in the source language ($sourceLangName).")
                appendLine("Then translate the meaning naturally and faithfully into the target language ($targetLangName).")
                appendLine()
                appendLine("Requirements:")
                appendLine("- Preserve meaning accurately and naturally for everyday spoken conversation.")
                appendLine("- Preserve names, places, numbers, dates, and important terminology.")
                appendLine("- Do not invent information, hallucinate, add explanations, or summarize.")
                appendLine("- Correct obvious acoustic speech-recognition slips when context makes intended meaning clear.")
                appendLine("- Handle incomplete spoken sentences naturally.")
                appendLine("- When conversation context is provided below, use it to resolve ambiguous words, pronouns, and references.")
                if (!context.isNullOrBlank()) {
                    appendLine()
                    appendLine("Recent conversation context:")
                    appendLine(context)
                }
                appendLine()
                appendLine("Return the output STRICTLY as a JSON object with this exact schema:")
                appendLine("{")
                appendLine("  \"transcript\": \"<exact transcribed speech in source language>\",")
                appendLine("  \"translation\": \"<fluent natural translation in target language>\"")
                appendLine("}")
            }

            val audioBytes = audioFile.readBytes()
            val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)

            val request = GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(inlineData = GeminiInlineData(mimeType = "audio/wav", data = base64Audio))
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.2f,
                    responseMimeType = "application/json"
                )
            )

            val effectiveModel = if (model.isNotBlank()) model else Constants.GEMINI_DEFAULT_MODEL
            val response = geminiApi.generateContent(
                model = effectiveModel,
                apiKey = apiKey,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val candidate = response.body()?.candidates?.firstOrNull()
                val rawText = candidate?.content?.parts?.firstOrNull()?.text ?: ""
                
                if (rawText.isBlank()) {
                    return Result.failure(Exception("Gemini returned an empty response. Spoken audio might be silent."))
                }

                val (transcript, translation) = parseGeminiResponse(rawText)
                if (transcript.isBlank() && translation.isBlank()) {
                    Result.failure(Exception("Could not parse transcript or translation from Gemini response: $rawText"))
                } else {
                    Result.success(Pair(transcript, translation))
                }
            } else {
                val errorCode = response.code()
                val errorBody = response.errorBody()?.string() ?: ""
                val userFriendlyMessage = when (errorCode) {
                    400 -> "Invalid request to Gemini (400): Unsupported audio or prompt parameters. $errorBody"
                    401, 403 -> "Gemini API key is invalid or unauthorized (HTTP $errorCode). Please check your key in Settings."
                    404 -> "Gemini model '$effectiveModel' was not found or is not supported for generateContent (404)."
                    429 -> "Gemini rate limit reached (HTTP 429). The system will retry shortly."
                    500, 503 -> "Gemini service temporarily unavailable (HTTP $errorCode). Will retry automatically."
                    else -> "Gemini API request failed (HTTP $errorCode): $errorBody"
                }
                Result.failure(Exception(userFriendlyMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testGeminiApiKey(apiKey: String, model: String): Result<String> {
        return try {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalArgumentException("Gemini API key cannot be blank."))
            }
            val effectiveModel = if (model.isNotBlank()) model else Constants.GEMINI_DEFAULT_MODEL
            val testRequest = GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = "Respond with: {\"status\":\"ok\"}"))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1f,
                    responseMimeType = "application/json"
                )
            )
            val response = geminiApi.generateContent(
                model = effectiveModel,
                apiKey = apiKey,
                request = testRequest
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success("Successfully connected to Gemini API ($effectiveModel)!")
            } else {
                val code = response.code()
                val err = response.errorBody()?.string() ?: "HTTP $code"
                val msg = when (code) {
                    401, 403 -> "API Key test failed: Invalid or expired API Key ($code)."
                    404 -> "Model '$effectiveModel' not found on API ($code). Try selecting another model like gemini-2.5-flash."
                    429 -> "Rate limit reached for this API key ($code)."
                    else -> "API test failed ($code): $err"
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchEligibleModels(apiKey: String): Result<List<GeminiModelOption>> {
        return try {
            if (apiKey.isBlank()) {
                return Result.failure(IllegalArgumentException("Gemini API key is required to query models."))
            }
            val response = geminiApi.listModels(apiKey = apiKey)
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()?.models ?: emptyList()
                val matching = list.filter { item ->
                    item.supportedGenerationMethods?.contains("generateContent") == true
                }.map { item ->
                    val cleanId = item.name.removePrefix("models/")
                    GeminiModelOption(
                        id = cleanId,
                        name = item.displayName ?: cleanId,
                        description = item.description ?: "Eligible multimodal generateContent model",
                        isRecommended = cleanId == "gemini-2.5-flash"
                    )
                }
                if (matching.isNotEmpty()) {
                    Result.success(matching)
                } else {
                    Result.success(Constants.GEMINI_MODELS)
                }
            } else {
                Result.failure(Exception("Failed to fetch models (HTTP ${response.code()}): ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGeminiResponse(rawText: String): Pair<String, String> {
        val cleaned = cleanJsonString(rawText)
        try {
            val structured = json.decodeFromString<GeminiStructuredResult>(cleaned)
            val transcript = structured.transcript?.trim() ?: ""
            val translation = structured.translation?.trim() ?: ""
            if (transcript.isNotBlank() || translation.isNotBlank()) {
                return Pair(transcript, translation)
            }
        } catch (_: Exception) {
            // Fallback to regex pattern extraction
        }

        // Regex fallback
        val transcriptRegex = Regex(""""transcript"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        val translationRegex = Regex(""""translation"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")

        val transcriptMatch = transcriptRegex.find(cleaned)?.groupValues?.getOrNull(1)
        val translationMatch = translationRegex.find(cleaned)?.groupValues?.getOrNull(1)

        val unescapedTranscript = transcriptMatch?.replace("\\\"", "\"")?.replace("\\n", "\n")?.trim() ?: ""
        val unescapedTranslation = translationMatch?.replace("\\\"", "\"")?.replace("\\n", "\n")?.trim() ?: ""

        if (unescapedTranscript.isNotBlank() || unescapedTranslation.isNotBlank()) {
            return Pair(unescapedTranscript, unescapedTranslation)
        }

        // Final fallback: If Gemini returned a plain text sentence without JSON
        return Pair(cleaned, cleaned)
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
        return text.trim()
    }
}

