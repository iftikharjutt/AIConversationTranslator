package com.example.aitranslator.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.aitranslator.domain.model.SegmentStatus
import com.example.aitranslator.domain.model.TranslationSegment

@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversationId"),
        Index(value = ["conversationId", "segmentNumber"], unique = true)
    ]
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true)
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
) {
    fun toDomain(): TranslationSegment = TranslationSegment(
        id = id,
        conversationId = conversationId,
        segmentNumber = segmentNumber,
        audioPath = audioPath,
        startTime = startTime,
        endTime = endTime,
        originalText = originalText,
        translatedText = translatedText,
        status = status,
        errorMessage = errorMessage,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(seg: TranslationSegment): SegmentEntity = SegmentEntity(
            id = seg.id,
            conversationId = seg.conversationId,
            segmentNumber = seg.segmentNumber,
            audioPath = seg.audioPath,
            startTime = seg.startTime,
            endTime = seg.endTime,
            originalText = seg.originalText,
            translatedText = seg.translatedText,
            status = seg.status,
            errorMessage = seg.errorMessage,
            createdAt = seg.createdAt
        )
    }
}
