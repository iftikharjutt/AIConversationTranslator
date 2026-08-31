package com.example.aitranslator.offline.nllb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class NllbTokenizer {

    private val vocab = ConcurrentHashMap<String, Long>()
    private val idToToken = ConcurrentHashMap<Long, String>()
    private var isInitialized = false

    companion object {
        const val BOS_TOKEN_ID = 0L
        const val PAD_TOKEN_ID = 1L
        const val EOS_TOKEN_ID = 2L
        const val UNK_TOKEN_ID = 3L

        const val MALAY_TOKEN_ID = 256125L   // zsm_Latn
        const val URDU_TOKEN_ID = 256190L    // urd_Arab
        const val ENGLISH_TOKEN_ID = 256047L // eng_Latn

        private val LANG_CODE_MAP = mapOf(
            "ms" to "zsm_Latn",
            "zsm" to "zsm_Latn",
            "msa" to "zsm_Latn",
            "zsm_latn" to "zsm_Latn",
            "msa_latn" to "zsm_Latn",
            "ur" to "urd_Arab",
            "urd" to "urd_Arab",
            "urd_arab" to "urd_Arab",
            "en" to "eng_Latn",
            "eng" to "eng_Latn",
            "eng_latn" to "eng_Latn"
        )
    }

    fun loadFromDirectory(modelDir: File): Boolean {
        try {
            val tokenizerFile = File(modelDir, "tokenizer.json")
            if (!tokenizerFile.exists() || tokenizerFile.length() == 0L) {
                initFallbackVocab()
                isInitialized = true
                return true
            }

            val jsonContent = tokenizerFile.readText()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonContent).jsonObject

            val modelObj = root["model"]?.jsonObject
            val vocabObj = modelObj?.get("vocab")?.jsonObject

            if (vocabObj != null) {
                vocab.clear()
                idToToken.clear()
                for ((token, element) in vocabObj) {
                    val id = element.jsonPrimitive.content.toLongOrNull() ?: continue
                    vocab[token] = id
                    idToToken[id] = token
                }
            } else {
                initFallbackVocab()
            }

            isInitialized = true
            return true
        } catch (_: Exception) {
            initFallbackVocab()
            isInitialized = true
            return true
        }
    }

    private fun initFallbackVocab() {
        vocab.clear()
        idToToken.clear()

        vocab["<s>"] = BOS_TOKEN_ID
        vocab["<pad>"] = PAD_TOKEN_ID
        vocab["</s>"] = EOS_TOKEN_ID
        vocab["<unk>"] = UNK_TOKEN_ID
        vocab["zsm_Latn"] = MALAY_TOKEN_ID
        vocab["msa_Latn"] = MALAY_TOKEN_ID
        vocab["urd_Arab"] = URDU_TOKEN_ID
        vocab["eng_Latn"] = ENGLISH_TOKEN_ID

        idToToken[BOS_TOKEN_ID] = "<s>"
        idToToken[PAD_TOKEN_ID] = "<pad>"
        idToToken[EOS_TOKEN_ID] = "</s>"
        idToToken[UNK_TOKEN_ID] = "<unk>"
        idToToken[MALAY_TOKEN_ID] = "zsm_Latn"
        idToToken[URDU_TOKEN_ID] = "urd_Arab"
        idToToken[ENGLISH_TOKEN_ID] = "eng_Latn"
    }

    fun getLanguageTokenId(langCode: String): Long {
        val standard = LANG_CODE_MAP[langCode.lowercase().trim()] ?: "zsm_Latn"
        return when (standard) {
            "urd_Arab" -> URDU_TOKEN_ID
            "eng_Latn" -> ENGLISH_TOKEN_ID
            else -> MALAY_TOKEN_ID
        }
    }

    fun encode(text: String, sourceLang: String): LongArray {
        if (!isInitialized) initFallbackVocab()

        val tokens = mutableListOf<Long>()
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

        for (word in words) {
            val spToken = " $word"
            if (vocab.containsKey(spToken)) {
                tokens.add(vocab[spToken]!!)
            } else if (vocab.containsKey(word)) {
                tokens.add(vocab[word]!!)
            } else {
                var matched = false
                for (i in word.indices) {
                    val charToken = if (i == 0) " ${word[i]}" else "${word[i]}"
                    val id = vocab[charToken] ?: vocab["${word[i]}"] ?: UNK_TOKEN_ID
                    tokens.add(id)
                    matched = true
                }
                if (!matched) {
                    tokens.add(UNK_TOKEN_ID)
                }
            }
        }

        // NLLB sequence format: [Tokens...] + [EOS] + [Source Lang ID]
        tokens.add(EOS_TOKEN_ID)
        tokens.add(getLanguageTokenId(sourceLang))

        return tokens.toLongArray()
    }

    fun buildDecoderPrompt(targetLang: String): LongArray {
        return longArrayOf(EOS_TOKEN_ID, getLanguageTokenId(targetLang))
    }

    fun decode(tokenIds: List<Long>): String {
        val sb = StringBuilder()
        for (id in tokenIds) {
            if (id == EOS_TOKEN_ID) break
            if (id == BOS_TOKEN_ID || id == PAD_TOKEN_ID || id >= 256000L) continue

            val token = idToToken[id] ?: continue
            if (token.startsWith(" ")) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(token.removePrefix(" "))
            } else {
                sb.append(token)
            }
        }
        return sb.toString().trim()
    }
}
