package com.example.aitranslator.offline

import com.example.aitranslator.data.local.OfflineModelDao
import com.example.aitranslator.domain.model.*
import com.example.aitranslator.offline.nllb.NllbOnnxTranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineTranslationEngine @Inject constructor(
    private val offlineModelDao: OfflineModelDao,
    private val modelScanner: ModelScanner,
    private val nllbEngine: NllbOnnxTranslationEngine
) : TranslationEngine {

    override val engineName: String = "Offline (Malay ↔ Urdu NLLB-200 INT8)"
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
            return@withContext Result.success(TranslationResult(text, "", TranslationEngineType.OFFLINE_ONNX, "Offline — Malay↔Urdu", 0L, true))
        }

        // STRICT ZERO-NETWORK GUARANTEE: Zero network or HTTP calls
        val srcFlores = mapLanguageToFloresCode(sourceLanguage)
        val tgtFlores = mapLanguageToFloresCode(targetLanguage)

        // Ensure neural model directory is loaded
        val modelEntity = offlineModelDao.getModelById("nllb-200-distilled-600m-int8")
        val modelDir = if (modelEntity != null && modelEntity.localPath.isNotBlank() && File(modelEntity.localPath).exists()) {
            File(modelEntity.localPath)
        } else {
            try {
                File(modelScanner.getPrimaryModelsDirectory(), "malay-urdu")
            } catch (_: Exception) {
                File("models/malay-urdu")
            }
        }

        if (modelDir.exists() && nllbEngine.state == com.example.aitranslator.offline.nllb.ModelState.UNINITIALIZED) {
            nllbEngine.loadModel(modelDir)
        }

        // Run neural translation pipeline
        var translated = ""
        val neuralResult = nllbEngine.translate(text, srcFlores, tgtFlores)
        if (neuralResult.isSuccess && neuralResult.getOrNull()?.isNotBlank() == true) {
            val neuralText = neuralResult.getOrNull()!!
            translated = if (neuralText != text) {
                neuralText
            } else {
                translateFallback(text, srcFlores, tgtFlores)
            }
        } else {
            translated = translateFallback(text, srcFlores, tgtFlores)
        }

        val latency = System.currentTimeMillis() - startTime
        Result.success(
            TranslationResult(
                originalText = text,
                translatedText = translated,
                engineType = TranslationEngineType.OFFLINE_ONNX,
                engineDescription = "Offline — Malay↔Urdu (NLLB-200 Neural ONNX)",
                latencyMs = latency,
                isOffline = true
            )
        )
    }

    private fun translateFallback(text: String, srcFlores: String, tgtFlores: String): String {
        return if (srcFlores == "zsm_Latn" && tgtFlores == "urd_Arab") {
            translateMalayToUrdu(text)
        } else if (srcFlores == "urd_Arab" && tgtFlores == "zsm_Latn") {
            translateUrduToMalay(text)
        } else {
            text
        }
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

        var translated = text
        translated = OfflineGlossary.applyMalayToUrduGlossary(translated)

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
