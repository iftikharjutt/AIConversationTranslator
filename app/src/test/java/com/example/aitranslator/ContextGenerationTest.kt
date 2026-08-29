package com.example.aitranslator

import org.junit.Assert.*
import org.junit.Test

class ContextGenerationTest {

    @Test
    fun testContextFormatting() {
        val mockSegments = listOf(
            Pair("Hello, how are you?", "ہیلو، آپ کیسے ہیں؟"),
            Pair("I am doing well, thank you.", "میں ٹھیک ہوں، شکریہ۔")
        )

        val formattedContext = mockSegments.joinToString("\n") { (orig, trans) ->
            "Original: $orig\nTranslated: $trans"
        }

        assertTrue(formattedContext.contains("Original: Hello, how are you?"))
        assertTrue(formattedContext.contains("Translated: ہیلو، آپ کیسے ہیں؟"))
        assertTrue(formattedContext.contains("Original: I am doing well, thank you."))
        assertTrue(formattedContext.contains("Translated: میں ٹھیک ہوں، شکریہ۔"))
    }
}
