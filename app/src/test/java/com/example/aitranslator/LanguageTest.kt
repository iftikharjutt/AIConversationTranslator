package com.example.aitranslator

import com.example.aitranslator.domain.model.Language
import org.junit.Assert.*
import org.junit.Test

class LanguageTest {

    @Test
    fun testDefaultLanguages() {
        val defaultSrc = Language.defaultSource()
        val defaultTgt = Language.defaultTarget()

        assertEquals("ms", defaultSrc.code)
        assertEquals("ur", defaultTgt.code)
    }

    @Test
    fun testRequiredLanguagesSupported() {
        val requiredCodes = listOf("en", "ur", "ms", "id", "ar", "hi", "bn", "zh", "ta")
        for (code in requiredCodes) {
            val lang = Language.getByCode(code)
            assertNotNull("Language $code should exist", lang)
            assertEquals(code, lang.code)
            assertTrue("Language $code should have speech supported", lang.speechSupported)
            assertTrue("Language $code should have translation supported", lang.translationSupported)
        }
    }

    @Test
    fun testRohingyaRequiresVerification() {
        val rohingya = Language.getByCode("rhg")
        assertNotNull(rohingya)
        assertEquals("rhg", rohingya.code)
        assertTrue("Rohingya must require capability verification", rohingya.requiresCapabilityVerification)
        assertFalse("Rohingya speech support must not be falsely claimed as true", rohingya.speechSupported)
        assertFalse("Rohingya translation support must not be falsely claimed as true", rohingya.translationSupported)
    }

    @Test
    fun testGetByCodeFallback() {
        val custom = Language.getByCode("xyz")
        assertEquals("xyz", custom.code)
    }
}
