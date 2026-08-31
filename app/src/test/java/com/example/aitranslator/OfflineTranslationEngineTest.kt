package com.example.aitranslator

import com.example.aitranslator.data.local.OfflineModelDao
import com.example.aitranslator.data.local.OfflineModelEntity
import com.example.aitranslator.domain.model.OfflineModelStatus
import com.example.aitranslator.domain.model.TranslationEngineType
import com.example.aitranslator.domain.model.TranslationMode
import com.example.aitranslator.offline.ModelScanner
import com.example.aitranslator.offline.OfflineGlossary
import com.example.aitranslator.offline.OfflineTranslationEngine
import com.example.aitranslator.offline.nllb.NllbGenerationConfig
import com.example.aitranslator.offline.nllb.NllbOnnxTranslationEngine
import com.example.aitranslator.offline.nllb.NllbTokenizer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineTranslationEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val offlineModelDao = mockk<OfflineModelDao>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private lateinit var modelScanner: ModelScanner
    private lateinit var nllbEngine: NllbOnnxTranslationEngine
    private lateinit var offlineEngine: OfflineTranslationEngine

    @Before
    fun setUp() {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        modelScanner = ModelScanner(mockContext, offlineModelDao, json)
        nllbEngine = NllbOnnxTranslationEngine()
        offlineEngine = OfflineTranslationEngine(offlineModelDao, modelScanner, nllbEngine)
    }

    @Test
    fun testNllbTokenizerLanguageTokenIds() {
        val tokenizer = NllbTokenizer()
        assertEquals(NllbTokenizer.MALAY_TOKEN_ID, tokenizer.getLanguageTokenId("ms"))
        assertEquals(NllbTokenizer.MALAY_TOKEN_ID, tokenizer.getLanguageTokenId("zsm_Latn"))
        assertEquals(NllbTokenizer.URDU_TOKEN_ID, tokenizer.getLanguageTokenId("ur"))
        assertEquals(NllbTokenizer.URDU_TOKEN_ID, tokenizer.getLanguageTokenId("urd_Arab"))
        assertEquals(NllbTokenizer.ENGLISH_TOKEN_ID, tokenizer.getLanguageTokenId("en"))
    }

    @Test
    fun testNllbTokenizerEncodeAndDecode() {
        val tokenizer = NllbTokenizer()
        val tokens = tokenizer.encode("Apa khabar?", "ms")
        assertTrue(tokens.isNotEmpty())
        assertEquals(NllbTokenizer.EOS_TOKEN_ID, tokens[tokens.size - 2])
        assertEquals(NllbTokenizer.MALAY_TOKEN_ID, tokens.last())

        val prompt = tokenizer.buildDecoderPrompt("ur")
        assertEquals(2, prompt.size)
        assertEquals(NllbTokenizer.EOS_TOKEN_ID, prompt[0])
        assertEquals(NllbTokenizer.URDU_TOKEN_ID, prompt[1])
    }

    @Test
    fun testNllbGenerationConfigDefaults() {
        val config = NllbGenerationConfig()
        assertEquals(128, config.maxOutputTokens)
        assertEquals(2L, config.eosTokenId)
        assertEquals(0.0f, config.temperature, 0.001f)
        assertEquals(256125L, config.sourceLangTokenId)
        assertEquals(256190L, config.targetLangTokenId)
    }

    @Test
    fun testOfflineTranslationMalayToUrdu() = runBlocking {
        val result = offlineEngine.translate(
            text = "Apa khabar?",
            sourceLanguage = "ms",
            targetLanguage = "ur",
            context = null
        )

        assertTrue(result.isSuccess)
        val translationResult = result.getOrNull()!!
        assertTrue(translationResult.isOffline)
        assertEquals("آپ کیسے ہیں؟", translationResult.translatedText)
        assertTrue(translationResult.engineDescription.contains("Offline"))
    }

    @Test
    fun testOfflineTranslationUrduToMalay() = runBlocking {
        val result = offlineEngine.translate(
            text = "آپ کیسے ہیں؟",
            sourceLanguage = "ur",
            targetLanguage = "ms",
            context = null
        )

        assertTrue(result.isSuccess)
        val translationResult = result.getOrNull()!!
        assertTrue(translationResult.isOffline)
        assertEquals("Apa khabar?", translationResult.translatedText)
    }

    @Test
    fun testOfflineIslamicGlossaryAlignment() {
        val malayInput = "Selamat Hari Raya Aidilfitri, jom pergi solat dan puasa."
        val urduOutput = OfflineGlossary.applyMalayToUrduGlossary(malayInput)

        assertTrue(urduOutput.contains("عید الفطر"))
        assertTrue(urduOutput.contains("نماز"))
        assertTrue(urduOutput.contains("روزہ"))

        val urduInput = "عید الفطر مبارک ہو اور مسجد میں نماز پڑھیں۔"
        val malayOutput = OfflineGlossary.applyUrduToMalayGlossary(urduInput)

        assertTrue(malayOutput.contains("Hari Raya Aidilfitri"))
        assertTrue(malayOutput.contains("solat"))
        assertTrue(malayOutput.contains("masjid"))
    }

    @Test
    fun testManifestParsingAndVerification() = runBlocking {
        val modelDir = tempFolder.newFolder("malay-urdu-test")
        val manifestContent = """
            {
              "modelId": "nllb-200-distilled-600m-int8",
              "modelName": "NLLB-200 Distilled INT8",
              "version": "1.0.0",
              "supportedLanguages": ["msa_Latn", "urd_Arab"],
              "modelFiles": [
                {"name": "model.onnx", "size": 100, "sha256": ""},
                {"name": "tokenizer.json", "size": 50, "sha256": ""}
              ],
              "expectedSize": 150,
              "license": "CC-BY-NC 4.0",
              "runtime": "onnx-int8"
            }
        """.trimIndent()

        val manifestFile = File(modelDir, "manifest.json")
        manifestFile.writeText(manifestContent)

        val modelFile = File(modelDir, "model.onnx")
        modelFile.writeBytes(ByteArray(100) { 1 })

        val tokenizerFile = File(modelDir, "tokenizer.json")
        tokenizerFile.writeBytes(ByteArray(50) { 2 })

        coEvery { offlineModelDao.getModelById("nllb-200-distilled-600m-int8") } returns OfflineModelEntity(
            modelId = "nllb-200-distilled-600m-int8",
            modelName = "NLLB-200 Distilled INT8",
            version = "1.0.0",
            localPath = modelDir.absolutePath,
            status = OfflineModelStatus.NOT_DOWNLOADED,
            totalSize = 150L,
            downloadedSize = 150L,
            sha256 = "",
            supportedLanguages = "msa_Latn,urd_Arab",
            license = "CC-BY-NC 4.0",
            sourceUrl = "",
            runtime = "onnx-int8",
            installedAt = 0L,
            lastVerifiedAt = 0L
        )

        val (isValid, message) = modelScanner.verifyModelPackage("nllb-200-distilled-600m-int8")
        assertTrue(isValid)
        assertTrue(message.contains("READY"))
    }

    @Test
    fun testCorruptedModelDetectionOnMissingFiles() = runBlocking {
        val modelDir = tempFolder.newFolder("corrupted-model")
        val manifestContent = """
            {
              "modelId": "test-corrupted",
              "modelName": "Corrupted Model",
              "version": "1.0.0",
              "supportedLanguages": ["ms", "ur"],
              "modelFiles": [
                {"name": "missing_file.onnx", "size": 500, "sha256": ""}
              ],
              "expectedSize": 500,
              "license": "MIT",
              "runtime": "onnx-int8"
            }
        """.trimIndent()

        val manifestFile = File(modelDir, "manifest.json")
        manifestFile.writeText(manifestContent)

        coEvery { offlineModelDao.getModelById("test-corrupted") } returns OfflineModelEntity(
            modelId = "test-corrupted",
            modelName = "Corrupted Model",
            version = "1.0.0",
            localPath = modelDir.absolutePath,
            status = OfflineModelStatus.NOT_DOWNLOADED,
            totalSize = 500L,
            downloadedSize = 0L,
            sha256 = "",
            supportedLanguages = "ms,ur",
            license = "MIT",
            sourceUrl = "",
            runtime = "onnx-int8",
            installedAt = 0L,
            lastVerifiedAt = 0L
        )

        val (isValid, message) = modelScanner.verifyModelPackage("test-corrupted")
        assertFalse(isValid)
        assertTrue(message.contains("Missing required file"))
    }

    @Test
    fun testChecksumCalculation() {
        val sampleFile = tempFolder.newFile("sample.txt")
        sampleFile.writeText("AI Conversation Translator Offline Engine")

        val sha256 = modelScanner.calculateSha256(sampleFile)
        assertNotNull(sha256)
        assertEquals(64, sha256.length)
    }

    @Test
    fun testTranslationModeOptions() {
        val modes = TranslationMode.values()
        assertTrue(modes.contains(TranslationMode.AUTO))
        assertTrue(modes.contains(TranslationMode.ONLINE))
        assertTrue(modes.contains(TranslationMode.OFFLINE))
    }
}
