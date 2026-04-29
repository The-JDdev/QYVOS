package com.qyvos.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenVault @Inject constructor(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "qyvos_secure_tokens"
        const val KEY_GITHUB_TOKEN   = "github_access_token"
        const val KEY_GITHUB_REFRESH = "github_refresh_token"
        const val KEY_GOOGLE_TOKEN   = "google_access_token"
    }

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun storeToken(key: String, token: String) {
        encryptedPrefs.edit().putString(key, token).apply()
    }

    fun getToken(key: String): String? =
        encryptedPrefs.getString(key, null)

    fun removeToken(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    fun hasToken(key: String): Boolean =
        encryptedPrefs.contains(key)

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    fun getAllTokenKeys(): Set<String> =
        encryptedPrefs.all.keys
}
