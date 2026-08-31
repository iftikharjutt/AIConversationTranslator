package com.example.aitranslator.offline

import com.example.aitranslator.data.local.OfflineModelDao
import com.example.aitranslator.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineTranslationEngine @Inject constructor(
    private val offlineModelDao: OfflineModelDao,
    private val modelScanner: ModelScanner
) : TranslationEngine {
    override val engineName: String = "Offline Malay ↔ Urdu"
    override val isOfflineEngine: Boolean = true

    fun mapLanguageToFloresCode(langCode: String): String = when (langCode.lowercase().trim()) {
        "ms", "zsm", "msa", "may", "malay" -> "zsm_Latn"
        "ur", "urd", "urdu" -> "urd_Arab"
        "en", "eng", "english" -> "eng_Latn"
        "ar", "ara", "arabic" -> "arb_Arab"
        "hi", "hin", "hindi" -> "hin_Deva"
        "id", "ind", "indonesian" -> "ind_Latn"
        else -> langCode
    }

    override suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String, context: String?): Result<TranslationResult> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        if (text.isBlank()) {
            return@withContext Result.success(TranslationResult(text, "", TranslationEngineType.OFFLINE_GLOSSARY_FALLBACK, "Offline — Local Dictionary", 0L, true))
        }

        // This engine is deliberately conservative: downloaded NLLB ONNX files are
        // validated separately, but this class does not pretend that a glossary is
        // neural inference. Real NLLB encoder/decoder + tokenizer generation will be
        // enabled only after the Android inference adapter is implemented and tested.
        val src = mapLanguageToFloresCode(sourceLanguage)
        val tgt = mapLanguageToFloresCode(targetLanguage)
        val translated = when {
            src == "zsm_Latn" && tgt == "urd_Arab" -> translateMalayToUrdu(text)
            src == "urd_Arab" && tgt == "zsm_Latn" -> translateUrduToMalay(text)
            else -> text
        }
        val latency = System.currentTimeMillis() - startTime
        Result.success(TranslationResult(text, translated, TranslationEngineType.OFFLINE_GLOSSARY_FALLBACK, "Offline — Malay↔Urdu (Local Dictionary)", latency, true))
    }

    private fun translateMalayToUrdu(text: String): String {
        val lower = text.trim().lowercase()
        val directMatch = when (lower) {
            "apa khabar?", "apa khabar" -> "آپ کیسے ہیں؟"
            "khabar baik", "khabar baik." -> "میں ٹھیک ہوں۔"
            "selamat pagi", "selamat pagi." -> "صبح بخیر۔"
            "selamat petang", "selamat petang." -> "شام بخیر۔"
            "selamat malam", "selamat malam." -> "شب بخیر۔"
            "terima kasih", "terima kasih banyak" -> "بہت شکریہ۔"
            "sama-sama" -> "خوش آمدید۔"
            "di mana masjid?", "kat mana masjid?" -> "مسجد کہاں ہے؟"
            "dah makan ke?", "sudah makan?" -> "کیا آپ نے کھانا کھا لیا؟"
            "berapa harga ini?", "bape harga ni?" -> "اس کی قیمت کتنی ہے؟"
            "jom solat", "jom pergi solat" -> "آئیے نماز پڑھنے چلتے ہیں۔"
            "selamat hari raya aidilfitri" -> "عید الفطر مبارک ہو۔"
            "inshaallah jumpa esok" -> "انشاء اللہ کل ملیں گے۔"
            else -> null
        }
        if (directMatch != null) return directMatch
        var translated = OfflineGlossary.applyMalayToUrduGlossary(text)
        return translated
            .replace(Regex("(?i)\\bsaya\\b"), "میں")
            .replace(Regex("(?i)\\banda\\b|\\bawak\\b"), "آپ")
            .replace(Regex("(?i)\\bmakan\\b"), "کھانا")
            .replace(Regex("(?i)\\bminum\\b"), "پینا")
            .replace(Regex("(?i)\\bpergi\\b|\\bgi\\b"), "جانا")
            .replace(Regex("(?i)\\brumah\\b"), "گھر")
            .replace(Regex("(?i)\\bduit\\b|\\bwang\\b"), "پیسے")
            .replace(Regex("(?i)\\bharga\\b"), "قیمت")
            .replace(Regex("(?i)\\bbilik\\b"), "کمرہ")
            .replace(Regex("(?i)\\bkereta\\b"), "گاڑی")
            .replace(Regex("(?i)\\bjalan\\b"), "راستہ / سڑک")
    }

    private fun translateUrduToMalay(text: String): String {
        val directMatch = when (text.trim()) {
            "آپ کیسے ہیں؟", "آپ کیسے ہیں" -> "Apa khabar?"
            "میں ٹھیک ہوں۔", "میں ٹھیک ہوں" -> "Khabar baik."
            "صبح بخیر۔", "صبح بخیر" -> "Selamat pagi."
            "شام بخیر۔", "شام بخیر" -> "Selamat petang."
            "شب بخیر۔", "شب بخیر" -> "Selamat malam."
            "بہت شکریہ۔", "شکریہ" -> "Terima kasih."
            "مسجد کہاں ہے؟" -> "Di manakah masjid?"
            "اس کی قیمت کتنی ہے؟" -> "Berapa harganya ini?"
            "عید مبارک۔", "عید الفطر مبارک" -> "Selamat Hari Raya Aidilfitri."
            "انشاء اللہ" -> "InshaAllah."
            else -> null
        }
        if (directMatch != null) return directMatch
        return OfflineGlossary.applyUrduToMalayGlossary(text)
            .replace("میں", "saya")
            .replace("آپ", "awak")
            .replace("کھانا", "makan")
            .replace("پانی", "air")
            .replace("گھر", "rumah")
            .replace("گاڑی", "kereta")
            .replace("کہاں", "di mana")
            .replace("کب", "bila")
    }
}
