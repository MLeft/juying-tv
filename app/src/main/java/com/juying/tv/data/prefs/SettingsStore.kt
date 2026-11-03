package com.juying.tv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.juying.tv.util.sha256
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.settingsDataStore by preferencesDataStore(name = "juying_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val kidsMode = booleanPreferencesKey("kids_mode")
        val pinHash = stringPreferencesKey("pin_hash")
        val autoNext = booleanPreferencesKey("auto_next")
        val resumePlayback = booleanPreferencesKey("resume_playback")
        val showPhoneAssistOnLaunch = booleanPreferencesKey("phone_assist_hint")
    }

    val kidsMode: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.kidsMode] ?: false }
    val pinHash: Flow<String?> = context.settingsDataStore.data.map { it[Keys.pinHash] }
    val autoNext: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.autoNext] ?: true }
    val resumePlayback: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.resumePlayback] ?: true }

    suspend fun setKidsMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.kidsMode] = enabled }
    }

    suspend fun setAutoNext(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoNext] = enabled }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.resumePlayback] = enabled }
    }

    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val hash = sha256(salt + pin)
        context.settingsDataStore.edit { it[Keys.pinHash] = "$salt:$hash" }
    }

    suspend fun clearPin() {
        context.settingsDataStore.edit {
            it.remove(Keys.pinHash)
            it[Keys.kidsMode] = false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val value = context.settingsDataStore.data
            .map { it[Keys.pinHash] }
            .first() ?: return false
        val parts = value.split(":")
        if (parts.size != 2) return false
        return sha256(parts[0] + pin) == parts[1]
    }
}
