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
        val scrapeMergeDuplicates = booleanPreferencesKey("scrape_merge_duplicates")
        val scrapeYearCollections = booleanPreferencesKey("scrape_year_collections")
        val aiApiKey = stringPreferencesKey("ai_api_key")
        val aiModel = stringPreferencesKey("ai_model")
        val aiProvider = stringPreferencesKey("ai_provider")
        val aiBaseUrl = stringPreferencesKey("ai_base_url")
        val aiKeys = stringPreferencesKey("ai_keys")
        val mcpEnabled = booleanPreferencesKey("mcp_enabled")
        val mcpToken = stringPreferencesKey("mcp_token")
        val mcpPort = androidx.datastore.preferences.core.intPreferencesKey("mcp_port")
        val subtitleSize = androidx.datastore.preferences.core.intPreferencesKey("subtitle_size")
        val subtitleBottom = androidx.datastore.preferences.core.intPreferencesKey("subtitle_bottom")
        val subtitleFont = stringPreferencesKey("subtitle_font")
    }

    val kidsMode: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.kidsMode] ?: false }
    val pinHash: Flow<String?> = context.settingsDataStore.data.map { it[Keys.pinHash] }
    val autoNext: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.autoNext] ?: true }
    val resumePlayback: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.resumePlayback] ?: true }
    val scrapeMergeDuplicates: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.scrapeMergeDuplicates] ?: true }
    val scrapeYearCollections: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.scrapeYearCollections] ?: false }
    val aiLegacyKey: Flow<String> = context.settingsDataStore.data.map { it[Keys.aiApiKey].orEmpty() }
    val aiModel: Flow<String> = context.settingsDataStore.data.map { it[Keys.aiModel] ?: "qwen-plus" }
    val aiProvider: Flow<String> = context.settingsDataStore.data.map { it[Keys.aiProvider] ?: "bailian" }
    val aiBaseUrl: Flow<String> = context.settingsDataStore.data.map { it[Keys.aiBaseUrl].orEmpty() }
    val mcpEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.mcpEnabled] ?: false }
    val mcpToken: Flow<String> = context.settingsDataStore.data.map { it[Keys.mcpToken].orEmpty() }
    val mcpPort: Flow<Int> = context.settingsDataStore.data.map { it[Keys.mcpPort] ?: 8787 }
    val subtitleSize: Flow<Int> = context.settingsDataStore.data.map { it[Keys.subtitleSize] ?: 28 }
    val subtitleBottom: Flow<Int> = context.settingsDataStore.data.map { it[Keys.subtitleBottom] ?: 20 }
    val subtitleFont: Flow<String> = context.settingsDataStore.data.map { it[Keys.subtitleFont] ?: "default" }
    val aiApiKeys: Flow<Map<String, String>> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[Keys.aiKeys].orEmpty()
        val map = runCatching {
            val json = org.json.JSONObject(raw)
            buildMap {
                json.keys().forEach { key -> put(key, json.optString(key)) }
            }
        }.getOrDefault(emptyMap()).toMutableMap()
        val legacy = prefs[Keys.aiApiKey].orEmpty()
        if (legacy.isNotBlank() && map["bailian"].isNullOrBlank()) {
            map["bailian"] = legacy
        }
        map
    }

    suspend fun setKidsMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.kidsMode] = enabled }
    }

    suspend fun setAutoNext(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoNext] = enabled }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.resumePlayback] = enabled }
    }

    suspend fun setScrapeMergeDuplicates(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.scrapeMergeDuplicates] = enabled }
    }

    suspend fun setScrapeYearCollections(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.scrapeYearCollections] = enabled }
    }

    suspend fun setAiKey(provider: String, key: String) {
        context.settingsDataStore.edit { prefs ->
            val raw = prefs[Keys.aiKeys].orEmpty()
            val map = runCatching {
                val json = org.json.JSONObject(raw)
                buildMap {
                    json.keys().forEach { name -> put(name, json.optString(name)) }
                }
            }.getOrDefault(emptyMap()).toMutableMap()
            if (key.isBlank()) map.remove(provider) else map[provider] = key.trim()
            prefs[Keys.aiKeys] = org.json.JSONObject(map as Map<*, *>).toString()
            if (provider == "bailian") prefs[Keys.aiApiKey] = key.trim()
        }
    }

    suspend fun setAiProvider(provider: String) {
        context.settingsDataStore.edit { it[Keys.aiProvider] = provider }
    }

    suspend fun setAiBaseUrl(url: String) {
        context.settingsDataStore.edit { it[Keys.aiBaseUrl] = url.trim() }
    }

    suspend fun setAiModel(model: String) {
        context.settingsDataStore.edit { it[Keys.aiModel] = model.trim() }
    }

    suspend fun setMcpEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.mcpEnabled] = enabled }
    }

    suspend fun setMcpToken(token: String) {
        context.settingsDataStore.edit { it[Keys.mcpToken] = token.trim() }
    }

    suspend fun setMcpPort(port: Int) {
        context.settingsDataStore.edit { it[Keys.mcpPort] = port }
    }

    suspend fun setSubtitleSize(sp: Int) {
        context.settingsDataStore.edit { it[Keys.subtitleSize] = sp.coerceIn(16, 44) }
    }

    suspend fun setSubtitleBottom(percent: Int) {
        context.settingsDataStore.edit { it[Keys.subtitleBottom] = percent.coerceIn(4, 40) }
    }

    suspend fun setSubtitleFont(font: String) {
        context.settingsDataStore.edit { it[Keys.subtitleFont] = font }
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
