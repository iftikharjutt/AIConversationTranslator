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
}
