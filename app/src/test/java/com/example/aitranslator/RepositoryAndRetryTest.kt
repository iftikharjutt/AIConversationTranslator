package com.example.aitranslator

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RepositoryAndRetryTest {

    private fun isTransientError(throwable: Throwable?): Boolean {
        return throwable is IOException ||
                throwable?.message?.contains("timeout", ignoreCase = true) == true ||
                throwable?.message?.contains("connect", ignoreCase = true) == true
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

    @Test
    fun testTransientErrorDetection() {
        assertTrue(isTransientError(IOException("Connection reset by peer")))
        assertTrue(isTransientError(RuntimeException("Socket timeout waiting for response")))
        assertTrue(isTransientError(RuntimeException("Failed to connect to host:3000")))
        assertFalse(isTransientError(IllegalArgumentException("Invalid model specified")))
    }

    @Test
    fun testMarkdownJsonCleaning() {
        val markdownJson = "```json\n{\"transcription\": \"Hello\", \"translation\": \"ہیلو\"}\n```"
        val cleaned = cleanJsonString(markdownJson)
        assertEquals("{\"transcription\": \"Hello\", \"translation\": \"ہیلو\"}", cleaned)

        val rawClean = "{\"transcription\": \"Test\", \"translation\": \"ٹیسٹ\"}"
        assertEquals(rawClean, cleanJsonString(rawClean))
    }
}
