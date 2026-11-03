package com.juying.tv.data.source.webdav

import android.util.Xml
import com.juying.tv.data.source.ScannedFile
import com.juying.tv.data.source.ScannedKind
import com.juying.tv.data.source.ScanResult
import com.juying.tv.data.source.WebDavConfig
import com.juying.tv.data.source.classify
import com.juying.tv.data.source.extensionOf
import com.juying.tv.data.source.mimeForExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.coroutineContext

class WebDavClient(private val client: OkHttpClient) {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:getcontenttype/>
  </d:prop>
</d:propfind>"""
    }

    suspend fun test(config: WebDavConfig): String = withContext(Dispatchers.IO) {
        val root = resolveRoot(config)
        val response = executePropfind(root, config, depth = 1)
        if (!response.isSuccessful) {
            val hint = when (response.code) {
                401 -> "用户名或密码不正确"
                403 -> "服务器拒绝访问"
                404 -> "路径不存在，请检查地址与目录"
                405 -> "服务器不支持 WebDAV 请求"
                else -> "HTTP ${response.code}"
            }
            response.close()
            throw IOException("WebDAV 连接失败：$hint")
        }
        response.close()
        "连接成功 · ${root.host}"
    }

    suspend fun scan(config: WebDavConfig, onProgress: (Int, String) -> Unit): ScanResult =
        withContext(Dispatchers.IO) {
            val root = resolveRoot(config)
            val files = ArrayList<ScannedFile>()
            walk(root, "/", files, 1, config, onProgress)
            ScanResult(files, "${root.host}${root.encodedPath.trimEnd('/')}")
        }

    fun playbackHeaders(config: WebDavConfig): Map<String, String> {
        val headers = mutableMapOf("User-Agent" to USER_AGENT)
        if (config.username.isNotBlank()) {
            headers["Authorization"] = Credentials.basic(config.username, config.password)
        }
        return headers
    }

    suspend fun fetchBytes(url: String, config: WebDavConfig, maxBytes: Long = 8L * 1024 * 1024): ByteArray =
        withContext(Dispatchers.IO) {
            val httpUrl = url.toHttpUrlOrNull() ?: throw IOException("字幕地址无效")
            val builder = Request.Builder()
                .url(httpUrl)
                .get()
                .header("User-Agent", USER_AGENT)
            if (config.username.isNotBlank()) {
                builder.header("Authorization", Credentials.basic(config.username, config.password))
            }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载字幕失败：HTTP ${response.code}")
                val body = response.body ?: throw IOException("字幕内容为空")
                if (body.contentLength() > maxBytes) throw IOException("字幕文件过大")
                val bytes = body.bytes()
                if (bytes.size > maxBytes) throw IOException("字幕文件过大")
                bytes
            }
        }

    fun resolveRoot(config: WebDavConfig): HttpUrl {
        val base = config.url.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("WebDAV 地址无效，请以 http:// 或 https:// 开头")
        val builder = base.newBuilder()
        config.rootPath.trim().trim('/').split('/')
            .filter { it.isNotBlank() }
            .forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private suspend fun walk(
        dirUrl: HttpUrl,
        logicalPath: String,
        out: MutableList<ScannedFile>,
        depth: Int,
        config: WebDavConfig,
        onProgress: (Int, String) -> Unit
    ) {
        coroutineContext.ensureActive()
        if (depth > 8) return
        val response = executePropfind(dirUrl, config, depth = 1)
        if (!response.isSuccessful) {
            response.close()
            val hint = when (response.code) {
                401 -> "认证失败，请检查用户名和密码"
                404 -> "服务器上没有该目录"
                else -> "HTTP ${response.code}"
            }
            throw IOException("读取 WebDAV 目录失败：$hint")
        }
        val xml = response.use { it.body?.string().orEmpty() }
        if (xml.isBlank()) throw IOException("WebDAV 返回了空响应")
        val entries = parseMultiStatus(xml, dirUrl)

        for (entry in entries) {
            coroutineContext.ensureActive()
            if (out.size >= 10000) throw IOException("WebDAV 文件过多，请缩小目录范围")
            if (entry.name.isBlank() || entry.name.startsWith(".")) continue
            val path = if (logicalPath == "/") "/${entry.name}" else "$logicalPath/${entry.name}"
            if (entry.isDir) {
                walk(entry.url, path, out, depth + 1, config, onProgress)
            } else {
                val kind = classify(entry.name)
                val include = kind == ScannedKind.VIDEO ||
                    kind == ScannedKind.SUBTITLE ||
                    (kind == ScannedKind.IMAGE && looksLikeCover(entry.name))
                if (!include) continue
                val parent = path.substringBeforeLast('/', "/").ifBlank { "/" }
                out += ScannedFile(
                    remoteId = path,
                    path = path,
                    parentPath = parent,
                    name = entry.name,
                    size = entry.size,
                    lastModified = entry.lastModified,
                    mime = entry.mime ?: mimeForExtension(extensionOf(entry.name)),
                    kind = kind,
                    playRef = entry.url.toString()
                )
                if (out.size % 20 == 0) onProgress(out.size, path)
            }
        }
        onProgress(out.size, logicalPath)
    }

    private fun executePropfind(url: HttpUrl, config: WebDavConfig, depth: Int): okhttp3.Response {
        val builder = Request.Builder()
            .url(url)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", depth.toString())
            .header("Connection", "close")
            .header("User-Agent", USER_AGENT)
        if (config.username.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password))
        }
        return client.newCall(builder.build()).execute()
    }

    private fun parseMultiStatus(xml: String, base: HttpUrl): List<DavEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val entries = ArrayList<DavEntry>()
        var event = parser.eventType
        var inResponse = false
        var href = ""
        var isDir = false
        var size = 0L
        var lastModified = 0L
        var mime: String? = null
        var currentTag = ""

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.substringAfter(':')
                    when (currentTag) {
                        "response" -> {
                            inResponse = true
                            href = ""
                            isDir = false
                            size = 0L
                            lastModified = 0L
                            mime = null
                        }
                        "collection" -> if (inResponse) isDir = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inResponse) {
                        val text = parser.text?.trim().orEmpty()
                        when (currentTag) {
                            "href" -> href = text
                            "getcontentlength" -> size = text.toLongOrNull() ?: 0L
                            "getlastmodified" -> lastModified = parseHttpDate(text)
                            "getcontenttype" -> mime = text.ifBlank { null }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    if (tag == "response" && inResponse) {
                        inResponse = false
                        if (href.isNotBlank()) {
                            val resolved = runCatching {
                                if (href.startsWith("http://", true) || href.startsWith("https://", true)) {
                                    href.toHttpUrlOrNull()
                                } else {
                                    base.resolve(href)
                                }
                            }.getOrNull()
                            if (resolved != null) {
                                val selfPath = resolved.encodedPath.trimEnd('/')
                                val rootPath = base.encodedPath.trimEnd('/')
                                if (selfPath != rootPath) {
                                    val name = resolved.pathSegments.lastOrNull { it.isNotBlank() }.orEmpty()
                                    val dir = isDir || href.endsWith("/")
                                    entries += DavEntry(
                                        url = resolved,
                                        name = name,
                                        isDir = dir,
                                        size = size,
                                        lastModified = lastModified,
                                        mime = mime
                                    )
                                }
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }
        return entries
    }

    private fun parseHttpDate(value: String): Long {
        if (value.isBlank()) return 0L
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        formats.forEach { pattern ->
            runCatching {
                return SimpleDateFormat(pattern, Locale.US).parse(value)?.time ?: 0L
            }
        }
        return 0L
    }

    private fun looksLikeCover(name: String): Boolean {
        val lower = name.lowercase()
        return listOf("poster", "folder", "cover", "fanart", "thumb", "banner").any { lower.contains(it) }
    }

    private data class DavEntry(
        val url: HttpUrl,
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val lastModified: Long,
        val mime: String?
    )
}
