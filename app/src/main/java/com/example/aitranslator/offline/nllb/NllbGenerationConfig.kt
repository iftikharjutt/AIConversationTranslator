package com.example.aitranslator.offline.nllb

data class NllbGenerationConfig(
    val maxOutputTokens: Int = 128,
    val eosTokenId: Long = 2L,
    val padTokenId: Long = 1L,
    val bosTokenId: Long = 0L,
    val unkTokenId: Long = 3L,
    val sourceLangTokenId: Long = 256125L, // default zsm_Latn (Malay)
    val targetLangTokenId: Long = 256190L, // default urd_Arab (Urdu)
    val temperature: Float = 0.0f          // Greedy decoding
)
