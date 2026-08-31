package com.example.aitranslator

import com.example.aitranslator.data.remote.TranscribeResponse
import com.example.aitranslator.data.remote.TranslateRequest
import com.example.aitranslator.data.remote.TranslateResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ApiModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun testTranscribeResponseSerialization() {
        val jsonStr = """{"text":"Selamat pagi","detectedLanguage":"ms"}"""
        val response = json.decodeFromString<TranscribeResponse>(jsonStr)
        assertEquals("Selamat pagi", response.text)
        assertEquals("ms", response.detectedLanguage)
        assertNull(response.error)
    }

    @Test
    fun testTranslateRequestSerialization() {
        val req = TranslateRequest(
            text = "Selamat pagi",
            sourceLanguage = "ms",
            targetLanguage = "ur",
            context = "Previous context"
        )
        val jsonStr = json.encodeToString(TranslateRequest.serializer(), req)
        assertTrue(jsonStr.contains("Selamat pagi"))
        assertTrue(jsonStr.contains("ms"))
        assertTrue(jsonStr.contains("ur"))
        assertTrue(jsonStr.contains("Previous context"))
    }

    @Test
    fun testTranslateResponseSerialization() {
        val jsonStr = """{"translation":"صبح بخیر"}"""
        val response = json.decodeFromString<TranslateResponse>(jsonStr)
        assertEquals("صبح بخیر", response.translation)
        assertNull(response.error)
    }

    @Test
    fun testGeminiStructuredResultSerialization() {
        val jsonStr = """{"transcript":"Apa khabar?","translation":"آپ کیسے ہیں؟"}"""
        val response = json.decodeFromString<com.example.aitranslator.data.remote.GeminiStructuredResult>(jsonStr)
        assertEquals("Apa khabar?", response.transcript)
        assertEquals("آپ کیسے ہیں؟", response.translation)
    }

    @Test
    fun testGeminiGenerateContentRequestSerialization() {
        val request = com.example.aitranslator.data.remote.GeminiGenerateContentRequest(
            contents = listOf(
                com.example.aitranslator.data.remote.GeminiContent(
                    parts = listOf(
                        com.example.aitranslator.data.remote.GeminiPart(text = "Hello world"),
                        com.example.aitranslator.data.remote.GeminiPart(
                            inlineData = com.example.aitranslator.data.remote.GeminiInlineData(
                                mimeType = "audio/wav",
                                data = "UklGRi4AAABXQVZFZg=="
                            )
                        )
                    )
                )
            )
        )
        val jsonStr = json.encodeToString(com.example.aitranslator.data.remote.GeminiGenerateContentRequest.serializer(), request)
        assertTrue(jsonStr.contains("Hello world"))
        assertTrue(jsonStr.contains("audio/wav"))
        assertTrue(jsonStr.contains("UklGRi4AAABXQVZFZg=="))
    }
}
