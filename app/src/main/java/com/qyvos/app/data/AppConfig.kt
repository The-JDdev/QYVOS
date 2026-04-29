package com.qyvos.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "qyvos_config")

@Singleton
class AppConfig @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        val KEY_BASE_URL    = stringPreferencesKey("base_url")
        val KEY_API_KEY     = stringPreferencesKey("api_key")
        val KEY_MODEL_NAME  = stringPreferencesKey("model_name")
        val KEY_MAX_TOKENS  = stringPreferencesKey("max_tokens")
        val KEY_TEMPERATURE = stringPreferencesKey("temperature")
        val KEY_MAX_STEPS   = stringPreferencesKey("max_steps")
        val KEY_THEME       = stringPreferencesKey("theme")

        const val DEFAULT_BASE_URL    = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL       = "deepseek-r1-0528"
        const val DEFAULT_MAX_TOKENS  = "8192"
        const val DEFAULT_TEMPERATURE = "0.7"
        const val DEFAULT_MAX_STEPS   = "20"
    }

    val baseUrl: Flow<String> = context.dataStore.data.map {
        it[KEY_BASE_URL] ?: DEFAULT_BASE_URL
    }
    val apiKey: Flow<String> = context.dataStore.data.map {
        it[KEY_API_KEY] ?: ""
    }
    val modelName: Flow<String> = context.dataStore.data.map {
        it[KEY_MODEL_NAME] ?: DEFAULT_MODEL
    }
    val maxTokens: Flow<String> = context.dataStore.data.map {
        it[KEY_MAX_TOKENS] ?: DEFAULT_MAX_TOKENS
    }
    val temperature: Flow<String> = context.dataStore.data.map {
        it[KEY_TEMPERATURE] ?: DEFAULT_TEMPERATURE
    }
    val maxSteps: Flow<String> = context.dataStore.data.map {
        it[KEY_MAX_STEPS] ?: DEFAULT_MAX_STEPS
    }

    suspend fun save(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        maxTokens: String,
        temperature: String,
        maxSteps: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL]    = baseUrl
            prefs[KEY_API_KEY]     = apiKey
            prefs[KEY_MODEL_NAME]  = modelName
            prefs[KEY_MAX_TOKENS]  = maxTokens
            prefs[KEY_TEMPERATURE] = temperature
            prefs[KEY_MAX_STEPS]   = maxSteps
        }
    }

    suspend fun getSnapshot(): ConfigSnapshot {
        var snapshot = ConfigSnapshot()
        context.dataStore.edit { prefs ->
            snapshot = ConfigSnapshot(
                baseUrl    = prefs[KEY_BASE_URL]    ?: DEFAULT_BASE_URL,
                apiKey     = prefs[KEY_API_KEY]     ?: "",
                modelName  = prefs[KEY_MODEL_NAME]  ?: DEFAULT_MODEL,
                maxTokens  = prefs[KEY_MAX_TOKENS]  ?: DEFAULT_MAX_TOKENS,
                temperature = prefs[KEY_TEMPERATURE] ?: DEFAULT_TEMPERATURE,
                maxSteps   = prefs[KEY_MAX_STEPS]   ?: DEFAULT_MAX_STEPS
            )
        }
        return snapshot
    }
}

data class ConfigSnapshot(
    val baseUrl: String     = AppConfig.DEFAULT_BASE_URL,
    val apiKey: String      = "",
    val modelName: String   = AppConfig.DEFAULT_MODEL,
    val maxTokens: String   = AppConfig.DEFAULT_MAX_TOKENS,
    val temperature: String = AppConfig.DEFAULT_TEMPERATURE,
    val maxSteps: String    = AppConfig.DEFAULT_MAX_STEPS
)
