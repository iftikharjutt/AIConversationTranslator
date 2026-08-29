package com.example.aitranslator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.aitranslator.domain.model.Conversation

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(conv: Conversation): ConversationEntity = ConversationEntity(
            id = conv.id,
            title = conv.title,
            sourceLanguage = conv.sourceLanguage,
            targetLanguage = conv.targetLanguage,
            createdAt = conv.createdAt
        )
    }
}
