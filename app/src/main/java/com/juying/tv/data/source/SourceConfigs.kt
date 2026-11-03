package com.juying.tv.data.source

import com.juying.tv.data.db.SourceType
import org.json.JSONArray
import org.json.JSONObject

data class LocalConfig(val treeUri: String = "")

data class MediaStoreConfig(val minDurationSec: Int = 60)

data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val rootPath: String = ""
)

data class QuarkConfig(
    val cookie: String = "",
    val rootFid: String = "0",
    val rootName: String = "夸克网盘"
)

object SourceConfigs {
    fun encodeLocal(config: LocalConfig): String = JSONObject().put("treeUri", config.treeUri).toString()

    fun decodeLocal(raw: String): LocalConfig {
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        return LocalConfig(json.optString("treeUri"))
    }

    fun encodeMediaStore(config: MediaStoreConfig): String =
        JSONObject().put("minDurationSec", config.minDurationSec).toString()

    fun decodeMediaStore(raw: String): MediaStoreConfig {
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        return MediaStoreConfig(json.optInt("minDurationSec", 60))
    }

    fun encodeWebDav(config: WebDavConfig): String = JSONObject()
        .put("url", config.url)
        .put("username", config.username)
        .put("password", config.password)
        .put("rootPath", config.rootPath)
        .toString()

    fun decodeWebDav(raw: String): WebDavConfig {
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        return WebDavConfig(
            url = json.optString("url"),
            username = json.optString("username"),
            password = json.optString("password"),
            rootPath = json.optString("rootPath")
        )
    }

    fun encodeQuark(config: QuarkConfig): String = JSONObject()
        .put("cookie", config.cookie)
        .put("rootFid", config.rootFid)
        .put("rootName", config.rootName)
        .toString()

    fun decodeQuark(raw: String): QuarkConfig {
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        return QuarkConfig(
            cookie = json.optString("cookie"),
            rootFid = json.optString("rootFid", "0").ifBlank { "0" },
            rootName = json.optString("rootName", "夸克网盘").ifBlank { "夸克网盘" }
        )
    }

    fun decode(type: String, raw: String): Any = when (type) {
        SourceType.LOCAL -> decodeLocal(raw)
        SourceType.MEDIASTORE -> decodeMediaStore(raw)
        SourceType.WEBDAV -> decodeWebDav(raw)
        SourceType.QUARK -> decodeQuark(raw)
        else -> Unit
    }
}

data class SubtitleRef(
    val ref: String,
    val label: String,
    val mime: String
)

object SubtitleRefs {
    fun encode(list: List<SubtitleRef>): String {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("ref", it.ref)
                    .put("label", it.label)
                    .put("mime", it.mime)
            )
        }
        return array.toString()
    }

    fun decode(raw: String): List<SubtitleRef> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                SubtitleRef(
                    ref = item.optString("ref"),
                    label = item.optString("label", "字幕"),
                    mime = item.optString("mime")
                )
            }.filter { it.ref.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}
