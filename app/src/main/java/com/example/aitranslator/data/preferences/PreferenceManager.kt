package com.example.aitranslator.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aitranslator.BuildConfig
import com.example.aitranslator.domain.model.Language
import com.example.aitranslator.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFERENCES_NAME)

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val KEY_SOURCE_LANG = stringPreferencesKey("source_language_code")
        val KEY_TARGET_LANG = stringPreferencesKey("target_language_code")
        val KEY_SEGMENT_DURATION = intPreferencesKey("segment_duration_seconds")
        val KEY_AUTO_PLAY_TTS = booleanPreferencesKey("auto_play_tts")
        val KEY_SAVE_AUDIO = booleanPreferencesKey("save_audio")
        val KEY_DELETE_AUDIO_AFTER_PROCESSING = booleanPreferencesKey("delete_audio_after_processing")
        val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
    }

    val geminiApiKey: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY] ?: ""
    }

    val geminiModel: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_MODEL] ?: Constants.GEMINI_DEFAULT_MODEL
    }

    val sourceLanguageCode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SOURCE_LANG] ?: Language.defaultSource().code
    }

    val targetLanguageCode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_TARGET_LANG] ?: Language.defaultTarget().code
    }

    val segmentDurationSeconds: Flow<Int> = dataStore.data.map { prefs ->
        val debug = prefs[KEY_DEBUG_MODE] ?: BuildConfig.DEBUG
        if (debug) {
            prefs[KEY_SEGMENT_DURATION] ?: Constants.DEBUG_SEGMENT_DURATION_SECONDS
        } else {
            prefs[KEY_SEGMENT_DURATION] ?: Constants.DEFAULT_SEGMENT_DURATION_SECONDS
        }
    }

    val autoPlayTts: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_PLAY_TTS] ?: false
    }

    val saveAudio: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SAVE_AUDIO] ?: true
    }

    val deleteAudioAfterProcessing: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DELETE_AUDIO_AFTER_PROCESSING] ?: false
    }

    val backendUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_BACKEND_URL] ?: BuildConfig.DEFAULT_BACKEND_URL
    }

    val isDebugMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_MODE] ?: BuildConfig.DEBUG
    }

    suspend fun setSourceLanguage(code: String) {
        dataStore.edit { it[KEY_SOURCE_LANG] = code }
    }

    suspend fun setTargetLanguage(code: String) {
        dataStore.edit { it[KEY_TARGET_LANG] = code }
    }

    suspend fun swapLanguages() {
        dataStore.edit { prefs ->
            val src = prefs[KEY_SOURCE_LANG] ?: Language.defaultSource().code
            val tgt = prefs[KEY_TARGET_LANG] ?: Language.defaultTarget().code
            prefs[KEY_SOURCE_LANG] = tgt
            prefs[KEY_TARGET_LANG] = src
        }
    }

    suspend fun setSegmentDuration(seconds: Int) {
        dataStore.edit { it[KEY_SEGMENT_DURATION] = seconds }
    }

    suspend fun setAutoPlayTts(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_PLAY_TTS] = enabled }
    }

    suspend fun setSaveAudio(save: Boolean) {
        dataStore.edit { it[KEY_SAVE_AUDIO] = save }
    }

    suspend fun setDeleteAudioAfterProcessing(delete: Boolean) {
        dataStore.edit { it[KEY_DELETE_AUDIO_AFTER_PROCESSING] = delete }
    }

    suspend fun setBackendUrl(url: String) {
        dataStore.edit { it[KEY_BACKEND_URL] = url }
    }

    suspend fun setDebugMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DEBUG_MODE] = enabled }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        dataStore.edit { it[KEY_GEMINI_API_KEY] = apiKey.trim() }
    }

    suspend fun clearGeminiApiKey() {
        dataStore.edit { it[KEY_GEMINI_API_KEY] = "" }
    }

    suspend fun setGeminiModel(model: String) {
        dataStore.edit { it[KEY_GEMINI_MODEL] = model.trim() }
    }
}
