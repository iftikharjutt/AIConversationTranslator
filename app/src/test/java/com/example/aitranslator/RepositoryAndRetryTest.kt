package com.example.aitranslator

import com.example.aitranslator.data.repository.TranslationRepositoryImpl
import com.example.aitranslator.domain.model.Language
import com.example.aitranslator.worker.ProcessSegmentWorker
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RepositoryAndRetryTest {

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

    @Test
    fun testTransientErrorDetectionAndHttp429() {
        // Transient errors must trigger retry
        assertTrue(ProcessSegmentWorker.isTransientError(IOException("Connection reset by peer")))
        assertTrue(ProcessSegmentWorker.isTransientError(RuntimeException("Socket timeout waiting for response")))
        assertTrue(ProcessSegmentWorker.isTransientError(RuntimeException("Failed to connect to host:3000")))
        assertTrue(ProcessSegmentWorker.isTransientError(Exception("Gemini rate limit reached (HTTP 429). The system will retry shortly.")))
        assertTrue(ProcessSegmentWorker.isTransientError(Exception("HTTP 429 Too Many Requests: quota exceeded")))
        assertTrue(ProcessSegmentWorker.isTransientError(Exception("Gemini service temporarily unavailable (HTTP 503). Will retry automatically.")))

        // Non-transient / Permanent errors must NOT retry
        assertFalse(ProcessSegmentWorker.isTransientError(Exception("Gemini API key is invalid or unauthorized (HTTP 401). Please check your key in Settings.")))
        assertFalse(ProcessSegmentWorker.isTransientError(Exception("Gemini API key is invalid or unauthorized (HTTP 403). Please check your key in Settings.")))
        assertFalse(ProcessSegmentWorker.isTransientError(IllegalArgumentException("Gemini API key cannot be blank.")))
        assertFalse(ProcessSegmentWorker.isTransientError(Exception("Gemini model 'unknown-model' was not found (404).")))
    }

    @Test
    fun testMalayColloquialPromptGeneration() {
        val prompt = TranslationRepositoryImpl.buildGeminiPrompt(
            sourceLanguage = "ms",
            targetLanguage = "ur",
            context = null
        )

        // Verify colloquial Malay contractions and particles are present in prompt instructions
        assertTrue(prompt.contains("dah"))
        assertTrue(prompt.contains("tak"))
        assertTrue(prompt.contains("nak"))
        assertTrue(prompt.contains("kat"))
        assertTrue(prompt.contains("ni"))
        assertTrue(prompt.contains("tu"))
        assertTrue(prompt.contains("camne"))
        assertTrue(prompt.contains("bape"))
        assertTrue(prompt.contains("jap"))
        assertTrue(prompt.contains("lah"))
        assertTrue(prompt.contains("kan"))
        assertTrue(prompt.contains("jom"))
    }

    @Test
    fun testMalayToUrduAndUrduToMalayPromptRules() {
        val prompt = TranslationRepositoryImpl.buildGeminiPrompt(
            sourceLanguage = "ms",
            targetLanguage = "ur",
            context = null
        )

        // Malay -> Urdu guidelines
        assertTrue(prompt.contains("MALAY → URDU TRANSLATION"))
        assertTrue(prompt.contains("آپ"))
        assertTrue(prompt.contains("natural, idiomatic spoken Urdu"))

        // Urdu -> Malay guidelines
        assertTrue(prompt.contains("URDU → MALAY TRANSLATION"))
        assertTrue(prompt.contains("awak"))
        assertTrue(prompt.contains("encik"))
        assertTrue(prompt.contains("abang"))
        assertTrue(prompt.contains("kakak"))
    }

    @Test
    fun testIslamicTerminologyPromptInstructions() {
        val prompt = TranslationRepositoryImpl.buildGeminiPrompt(
            sourceLanguage = "ms",
            targetLanguage = "ur",
            context = null
        )

        // Islamic terms mapping
        assertTrue(prompt.contains("solat") && prompt.contains("نماز"))
        assertTrue(prompt.contains("puasa") && prompt.contains("روزہ"))
        assertTrue(prompt.contains("surau") && prompt.contains("مسجد"))
        assertTrue(prompt.contains("Hari Raya Aidilfitri") && prompt.contains("عید الفطر"))
        assertTrue(prompt.contains("InshaAllah"))
        assertTrue(prompt.contains("Alhamdulillah"))
        assertTrue(prompt.contains("SubhanAllah"))
        assertTrue(prompt.contains("JazakAllah"))
    }

    @Test
    fun testNameAndCurrencyPreservationInstructions() {
        val prompt = TranslationRepositoryImpl.buildGeminiPrompt(
            sourceLanguage = "ms",
            targetLanguage = "ur",
            context = null
        )

        // Names & proper nouns
        assertTrue(prompt.contains("NEVER translate personal names or surnames as dictionary words"))
        assertTrue(prompt.contains("Transliterate people's names, places, organizations, and important entities phonetically"))

        // Currency preservation
        assertTrue(prompt.contains("NEVER perform currency conversions"))
        assertTrue(prompt.contains("Sepuluh ringgit") && prompt.contains("10 ringgit"))
        assertTrue(prompt.contains("never convert to Pakistani Rupees"))
    }

    @Test
    fun testChronologicalContextPromptInstructions() {
        val testContext = "Original: Apa khabar?\nTranslated: آپ کیسے ہیں؟"
        val prompt = TranslationRepositoryImpl.buildGeminiPrompt(
            sourceLanguage = "ms",
            targetLanguage = "ur",
            context = testContext
        )

        assertTrue(prompt.contains("CHRONOLOGICAL CONVERSATION CONTEXT"))
        assertTrue(prompt.contains("preceding chronological conversation history from prior turns"))
        assertTrue(prompt.contains(testContext))
        assertTrue(prompt.contains("Do NOT invent missing context"))
    }

    @Test
    fun testMarkdownJsonCleaning() {
        val markdownJson = "```json\n{\"transcript\": \"Hello\", \"translation\": \"ہیلو\"}\n```"
        val cleaned = cleanJsonString(markdownJson)
        assertEquals("{\"transcript\": \"Hello\", \"translation\": \"ہیلو\"}", cleaned)

        val rawClean = "{\"transcript\": \"Test\", \"translation\": \"ٹیسٹ\"}"
        assertEquals(rawClean, cleanJsonString(rawClean))
    }

    @Test
    fun testGeminiResponseParsingWithMarkdownAndWhitespace() {
        val geminiResponse = """
            ```json
            {
              "transcript": "Di manakah anda sekarang?",
              "translation": "آپ اب کہاں ہیں؟"
            }
            ```
        """.trimIndent()

        val cleaned = cleanJsonString(geminiResponse)
        val transcriptRegex = Regex(""""transcript"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        val translationRegex = Regex(""""translation"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")

        val transcript = transcriptRegex.find(cleaned)?.groupValues?.getOrNull(1)
        val translation = translationRegex.find(cleaned)?.groupValues?.getOrNull(1)

        assertEquals("Di manakah anda sekarang?", transcript)
        assertEquals("آپ اب کہاں ہیں؟", translation)
    }

    @Test
    fun testRohingyaLanguageVerificationFlag() {
        val rohingya = Language.getByCode("rhg")
        assertEquals("Rohingya", rohingya.name)
        assertTrue(rohingya.requiresCapabilityVerification)
    }
}
