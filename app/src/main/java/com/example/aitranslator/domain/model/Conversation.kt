package com.example.aitranslator.domain.model

data class Conversation(
    val id: Long = 0,
    val title: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val createdAt: Long = System.currentTimeMillis()
)
