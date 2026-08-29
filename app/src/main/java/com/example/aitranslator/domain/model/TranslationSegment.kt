package com.example.aitranslator.domain.model

data class TranslationSegment(
    val id: Long = 0,
    val conversationId: Long,
    val segmentNumber: Int,
    val audioPath: String,
    val startTime: Long,
    val endTime: Long,
    val originalText: String = "",
    val translatedText: String = "",
    val status: SegmentStatus = SegmentStatus.RECORDED,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
