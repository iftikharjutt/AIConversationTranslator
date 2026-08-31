package com.example.aitranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aitranslator.data.local.OfflineModelDao
import com.example.aitranslator.data.preferences.PreferenceManager
import com.example.aitranslator.domain.model.OfflineModel
import com.example.aitranslator.domain.model.TranslationMode
import com.example.aitranslator.domain.repository.TranslationRepository
import com.example.aitranslator.offline.DownloadProgress
import com.example.aitranslator.offline.ModelDownloadManager
import com.example.aitranslator.offline.ModelScanner
import com.example.aitranslator.offline.OfflineTranslationEngine
import com.example.aitranslator.util.Constants
import com.example.aitranslator.util.GeminiModelOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BenchmarkItem(
    val sourceText: String,
    val sourceLang: String,
    val targetLang: String,
    val translatedText: String,
    val latencyMs: Long,
    val isSuccess: Boolean
)

data class SettingsUiState(
    val segmentDuration: Int = 180,
    val autoPlayTts: Boolean = false,
    val saveAudio: Boolean = true,
    val deleteAudioAfterProcessing: Boolean = false,
    val backendUrl: String = "",
    val isDebugMode: Boolean = true,
    val geminiApiKey: String = "",
    val geminiModel: String = Constants.GEMINI_DEFAULT_MODEL,
    val translationMode: TranslationMode = TranslationMode.AUTO,
    val activeOfflineModelId: String = "nllb-200-distilled-600m-int8",
    val offlineModels: List<OfflineModel> = emptyList(),
    val downloadProgress: DownloadProgress = DownloadProgress()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val repository: TranslationRepository,
    private val offlineModelDao: OfflineModelDao,
    private val modelScanner: ModelScanner,
    private val downloadManager: ModelDownloadManager,
    private val offlineEngine: OfflineTranslationEngine
) : ViewModel() {

    init {
        viewModelScope.launch {
            modelScanner.scanDirectory()
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            preferenceManager.segmentDurationSeconds,
            preferenceManager.autoPlayTts,
            preferenceManager.saveAudio,
            preferenceManager.deleteAudioAfterProcessing
        ) { duration, autoTts, saveAud, delAud ->
            SettingsPart1(duration, autoTts, saveAud, delAud)
        },
        combine(
            preferenceManager.backendUrl,
            preferenceManager.isDebugMode,
            preferenceManager.geminiApiKey,
            preferenceManager.geminiModel,
            preferenceManager.translationMode
        ) { url, debug, apiKey, model, mode ->
            SettingsPart2(url, debug, apiKey, model, mode)
        },
        combine(
            preferenceManager.activeOfflineModelId,
            offlineModelDao.observeAllModels(),
            downloadManager.downloadState
        ) { activeId, models, dlProgress ->
            Triple(activeId, models.map { it.toDomain() }, dlProgress)
        }
    ) { p1, p2, p3 ->
        SettingsUiState(
            segmentDuration = p1.duration,
            autoPlayTts = p1.autoTts,
            saveAudio = p1.saveAud,
            deleteAudioAfterProcessing = p1.delAud,
            backendUrl = p2.url,
            isDebugMode = p2.debug,
            geminiApiKey = p2.apiKey,
            geminiModel = p2.model,
            translationMode = p2.mode,
            activeOfflineModelId = p3.first,
            offlineModels = if (p3.second.isNotEmpty()) p3.second else listOf(modelScanner.getDefaultMalayUrduModel()),
            downloadProgress = p3.third
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState()
    )

    fun setTranslationMode(mode: TranslationMode) {
        viewModelScope.launch { preferenceManager.setTranslationMode(mode) }
    }

    fun setActiveOfflineModel(modelId: String) {
        viewModelScope.launch { preferenceManager.setActiveOfflineModelId(modelId) }
    }

    fun scanOfflineModels() {
        viewModelScope.launch {
            modelScanner.scanDirectory()
        }
    }

    fun startModelDownload(model: OfflineModel) {
        downloadManager.startDownload(model)
    }

    fun pauseModelDownload() {
        downloadManager.pauseDownload()
    }

    fun cancelModelDownload() {
        downloadManager.cancelDownload()
    }

    fun verifyModel(modelId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = modelScanner.verifyModelPackage(modelId)
            onResult(result.first, result.second)
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelScanner.deleteModelPackage(modelId)
        }
    }

    fun runBenchmark(onResult: (List<BenchmarkItem>) -> Unit) {
        viewModelScope.launch {
            val benchmarkPhrases = listOf(
                // 20 Malay -> Urdu test sentences
                Pair("Apa khabar?", "ms" to "ur"),
                Pair("Selamat pagi semua.", "ms" to "ur"),
                Pair("Di manakah masjid terdekat?", "ms" to "ur"),
                Pair("Berapa harga tiket ini?", "ms" to "ur"),
                Pair("Saya dah makan tadi.", "ms" to "ur"),
                Pair("Jom kita pergi solat bersama-sama.", "ms" to "ur"),
                Pair("Terima kasih banyak atas bantuan awak.", "ms" to "ur"),
                Pair("Selamat Hari Raya Aidilfitri.", "ms" to "ur"),
                Pair("InshaAllah kita jumpa esok pagi.", "ms" to "ur"),
                Pair("Tolong hantar saya ke stesen kereta api.", "ms" to "ur"),
                Pair("Nama saya Tariq dan saya dari Kuala Lumpur.", "ms" to "ur"),
                Pair("Sepuluh ringgit sahaja harganya.", "ms" to "ur"),
                Pair("Bilik ini sangat bersih dan selesa.", "ms" to "ur"),
                Pair("Kereta saya rosak di tepi jalan.", "ms" to "ur"),
                Pair("Boleh saya minta segelas air minuman?", "ms" to "ur"),
                Pair("Pukul berapa mesyuarat akan bermula?", "ms" to "ur"),
                Pair("Alhamdulillah semua urusan berjalan lancar.", "ms" to "ur"),
                Pair("Bulan puasa akan bermula minggu hadapan.", "ms" to "ur"),
                Pair("Siti dan Encik Tan sedang berbincang.", "ms" to "ur"),
                Pair("Saya tidak faham apa yang dimaksudkan.", "ms" to "ur"),

                // 20 Urdu -> Malay test sentences
                Pair("آپ کیسے ہیں؟", "ur" to "ms"),
                Pair("صبح بخیر۔", "ur" to "ms"),
                Pair("مسجد کہاں ہے؟", "ur" to "ms"),
                Pair("اس کی قیمت کتنی ہے؟", "ur" to "ms"),
                Pair("میں نے کھانا کھا لیا ہے۔", "ur" to "ms"),
                Pair("آئیے نماز پڑھنے چلتے ہیں۔", "ur" to "ms"),
                Pair("بہت شکریہ آپ کی مدد کا۔", "ur" to "ms"),
                Pair("عید الفطر مبارک ہو۔", "ur" to "ms"),
                Pair("انشاء اللہ کل ملاقات ہوگی۔", "ur" to "ms"),
                Pair("مجھے ریلوے اسٹیشن پہنچا دیں۔", "ur" to "ms"),
                Pair("میرا نام محمد ہے اور میں لاہور سے ہوں۔", "ur" to "ms"),
                Pair("یہ صرف دس روپے کی بات نہیں ہے۔", "ur" to "ms"),
                Pair("یہ کمرہ بہت صاف اور آرام دہ ہے۔", "ur" to "ms"),
                Pair("گاڑی راستے میں خراب ہو گئی۔", "ur" to "ms"),
                Pair("کیا مجھے پینے کے لیے پانی مل سکتا ہے؟", "ur" to "ms"),
                Pair("میٹنگ کس وقت شروع ہوگی؟", "ur" to "ms"),
                Pair("الحمد للہ سب کام خیریت سے ہو گئے۔", "ur" to "ms"),
                Pair("رمضان کا روزہ اگلے ہفتے شروع ہو رہا ہے۔", "ur" to "ms"),
                Pair("جناب طارق صاحب گفتگو کر رہے ہیں۔", "ur" to "ms"),
                Pair("مجھے یہ بات سمجھ نہیں آئی۔", "ur" to "ms")
            )

            val results = mutableListOf<BenchmarkItem>()
            for ((text, pair) in benchmarkPhrases) {
                val start = System.currentTimeMillis()
                val res = offlineEngine.translate(text, pair.first, pair.second, null)
                val latency = System.currentTimeMillis() - start
                results.add(
                    BenchmarkItem(
                        sourceText = text,
                        sourceLang = pair.first,
                        targetLang = pair.second,
                        translatedText = res.getOrNull()?.translatedText ?: "[Error]",
                        latencyMs = latency,
                        isSuccess = res.isSuccess
                    )
                )
            }
            onResult(results)
        }
    }

    fun setSegmentDuration(seconds: Int) {
        viewModelScope.launch { preferenceManager.setSegmentDuration(seconds) }
    }

    fun setAutoPlayTts(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.setAutoPlayTts(enabled) }
    }

    fun setSaveAudio(save: Boolean) {
        viewModelScope.launch { preferenceManager.setSaveAudio(save) }
    }

    fun setDeleteAudioAfterProcessing(delete: Boolean) {
        viewModelScope.launch { preferenceManager.setDeleteAudioAfterProcessing(delete) }
    }

    fun setBackendUrl(url: String) {
        viewModelScope.launch { preferenceManager.setBackendUrl(url) }
    }

    fun setDebugMode(debug: Boolean) {
        viewModelScope.launch { preferenceManager.setDebugMode(debug) }
    }

    fun setGeminiApiKey(apiKey: String) {
        viewModelScope.launch { preferenceManager.setGeminiApiKey(apiKey) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { preferenceManager.clearGeminiApiKey() }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch { preferenceManager.setGeminiModel(model) }
    }

    fun testGeminiApiKey(apiKey: String, model: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.testGeminiApiKey(apiKey, model)
            if (result.isSuccess) {
                onResult(true, "Successfully connected to Gemini API (${model})!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Connection test failed")
            }
        }
    }

    fun fetchEligibleModels(apiKey: String, onResult: (List<GeminiModelOption>?, String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.fetchEligibleModels(apiKey)
            if (result.isSuccess) {
                onResult(result.getOrNull(), null)
            } else {
                onResult(null, result.exceptionOrNull()?.message ?: "Failed to query account models")
            }
        }
    }

    fun testBackendConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.testBackendConnection()
            if (result.isSuccess) {
                onResult(true, "Backend is reachable and healthy (status: ${result.getOrNull()})!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Could not connect to backend")
            }
        }
    }
}

private data class SettingsPart1(
    val duration: Int,
    val autoTts: Boolean,
    val saveAud: Boolean,
    val delAud: Boolean
)

private data class SettingsPart2(
    val url: String,
    val debug: Boolean,
    val apiKey: String,
    val model: String,
    val mode: TranslationMode
)

