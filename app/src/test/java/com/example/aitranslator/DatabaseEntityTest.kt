package com.example.aitranslator

import com.example.aitranslator.data.local.ConversationEntity
import com.example.aitranslator.data.local.SegmentEntity
import com.example.aitranslator.domain.model.Conversation
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment
import org.junit.Assert.*
import org.junit.Test

class DatabaseEntityTest {

    @Test
    fun testConversationEntityDomainMapping() {
        val domainConv = Conversation(
            id = 42L,
            title = "Malay to Urdu Translation Session",
            sourceLanguage = "ms",
            targetLanguage = "ur",
            createdAt = 1700000000000L
        )

        val entity = ConversationEntity.fromDomain(domainConv)
        assertEquals(42L, entity.id)
        assertEquals("Malay to Urdu Translation Session", entity.title)
        assertEquals("ms", entity.sourceLanguage)
        assertEquals("ur", entity.targetLanguage)

        val mappedBack = entity.toDomain()
        assertEquals(domainConv, mappedBack)
    }

    @Test
    fun testSegmentEntityDomainMappingAndStatuses() {
        val segment = TranslationSegment(
            id = 101L,
            conversationId = 42L,
            segmentNumber = 1,
            audioPath = "/data/data/com.example/audio_1.wav",
            startTime = 1000L,
            endTime = 11000L,
            originalText = "Selamat petang",
            translatedText = "شب بخیر",
            status = SegmentStatus.COMPLETED,
            errorMessage = null,
            createdAt = 1700000005000L
        )

        val entity = SegmentEntity.fromDomain(segment)
        assertEquals(101L, entity.id)
        assertEquals(42L, entity.conversationId)
        assertEquals(1, entity.segmentNumber)
        assertEquals(SegmentStatus.COMPLETED, entity.status)
        assertEquals("Selamat petang", entity.originalText)
        assertEquals("شب بخیر", entity.translatedText)

        val mappedBack = entity.toDomain()
        assertEquals(segment, mappedBack)
    }

    @Test
    fun testAllRequiredSegmentStatusesExist() {
        val requiredStatuses = setOf(
            "RECORDED",
            "UPLOADING",
            "TRANSCRIBING",
            "TRANSLATING",
            "COMPLETED",
            "FAILED"
        )
        val actualStatuses = SegmentStatus.entries.map { it.name }.toSet()
        assertEquals(requiredStatuses, actualStatuses)
    }
}
