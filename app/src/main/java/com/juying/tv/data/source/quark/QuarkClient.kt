package com.juying.tv.data.source.quark

import com.juying.tv.data.source.QuarkConfig
import com.juying.tv.data.source.QuarkRoot
import com.juying.tv.data.source.ScannedFile
import com.juying.tv.data.source.ScannedKind
import com.juying.tv.data.source.ScanResult
import com.juying.tv.data.source.classify
import com.juying.tv.data.source.extensionOf
import com.juying.tv.data.source.mimeForExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * 夸克网盘非官方接口。Cookie 失效时需要用户重新填写。
 * 仅用于个人媒体库访问，接口随时可能变化。
 */
class QuarkClient(private val client: OkHttpClient) {

    companion object {
        private const val BASE = "https://drive-pc.quark.cn/1/clouddrive"
        private const val REFERER = "https://pan.quark.cn/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        // 夸克会对大文件直链返回 23018；官方 PC 客户端 UA 可以绕过该限制。
        private const val PC_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) quark-cloud-drive/2.5.56 Chrome/100.0.4896.160 Electron/18.3.5.12-a038f7b798 Safari/537.36 Channel/pckk_other_ch"
    }

    private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    /**
     * 夸克接口每次响应都会通过 Set-Cookie 续期 __puus。只保留内存中的最新值，
     * 供同一次播放的直链请求使用；原始 Cookie 仍保存在媒体源配置里。
     */
    private val cookieOverrides = ConcurrentHashMap<String, String>()

    private fun currentCookie(config: QuarkConfig): String {
        val base = normalizeCookie(config.cookie)
        return cookieOverrides[base.hashCode().toString()] ?: base
    }

    private fun captureCookies(baseCookie: String, response: okhttp3.Response) {
        val setCookies = response.headers("Set-Cookie")
        if (setCookies.isEmpty()) return
        val key = baseCookie.hashCode().toString()
        val current = cookieOverrides[key] ?: baseCookie
        val merged = LinkedHashMap<String, String>()
        current.split(';').forEach { part ->
            val name = part.substringBefore('=').trim()
            if (name.isNotEmpty()) merged[name] = part.substringAfter('=', "").trim()
        }
        setCookies.forEach { raw ->
            val pair = raw.substringBefore(';')
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=', "").trim()
            if (name.isNotEmpty() && value.isNotEmpty()) merged[name] = value
        }
        val updated = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
        if (updated != current) {
            cookieOverrides[key] = updated
            android.util.Log.i("JuyingQuark", "Cookie 已自动续期（${setCookies.size} 项）")
        }
    }

    suspend fun test(config: QuarkConfig): String = withContext(Dispatchers.IO) {
        val fid = config.effectiveRoots().firstOrNull()?.fid ?: "0"
        val entries = listFolder(config, fid)
        "连接成功 · 当前目录 ${entries.size} 项"
    }

    /** 供目录选择器使用：只返回当前层级的文件夹。 */
    suspend fun listDirectories(config: QuarkConfig, fid: String): List<QuarkDir> =
        withContext(Dispatchers.IO) {
            listFolder(config, fid)
                .filter { it.isDir && it.name.isNotBlank() && !it.name.startsWith(".") }
                .map { QuarkDir(it.fid, it.name) }
                .sortedWith(compareBy { it.name.lowercase() })
        }

    suspend fun scan(config: QuarkConfig, onProgress: (Int, String) -> Unit): ScanResult =
        withContext(Dispatchers.IO) {
            val files = ArrayList<ScannedFile>()
            val seen = HashSet<String>()
            val explicitRoots = config.roots.isNotEmpty()
            val roots = config.effectiveRoots()
            roots.forEach { root ->
                coroutineContext.ensureActive()
                val basePath = if (explicitRoots) {
                    "/" + root.name.replace('/', ' ').replace('\\', ' ').trim().ifBlank { "目录" }
                } else {
                    "/"
                }
                walk(config, root.fid.ifBlank { "0" }, basePath, files, 1, onProgress, seen)
            }
            ScanResult(
                files,
                if (explicitRoots) "夸克网盘 · ${roots.size} 个目录" else config.rootName.ifBlank { "夸克网盘" }
            )
        }

    fun playbackHeaders(config: QuarkConfig): Map<String, String> = mapOf(
        "User-Agent" to PC_USER_AGENT,
        "Referer" to REFERER,
        "Origin" to "https://pan.quark.cn",
        "Cookie" to currentCookie(config)
    )

    suspend fun getDownloadUrl(
        config: QuarkConfig,
        fid: String,
        forceRefresh: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val baseCookie = normalizeCookie(config.cookie)
        val cacheKey = "${baseCookie.hashCode()}:$fid"
        if (!forceRefresh) {
            val cached = urlCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.second < 3 * 60 * 1000L) {
                return@withContext cached.first
            }
        }
        val url = "$BASE/file/download?pr=ucpro&fr=pc&sys=win32&ve=2.5.56&ut=&guid=".toHttpUrl()
        val body = JSONObject().put("fids", JSONArray().put(fid)).toString()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .headers(okhttp3.Headers.Builder().apply {
                add("User-Agent", PC_USER_AGENT)
                add("Referer", REFERER)
                add("Origin", "https://pan.quark.cn")
                add("Cookie", currentCookie(config))
                add("Accept", "application/json, text/plain, */*")
                add("Accept-Language", "zh-CN,zh;q=0.9")
            }.build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("夸克接口 HTTP ${response.code}")
            captureCookies(baseCookie, response)
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw IOException("夸克接口返回异常，请稍后重试")
            }
            ensureSuccess(json)
            val data = json.optJSONObject("data")
            val direct = data?.optString("download_url").orEmpty()
            if (direct.isNotBlank()) {
                urlCache[cacheKey] = direct to System.currentTimeMillis()
                return@withContext direct
            }
            val array = data?.optJSONArray("list") ?: json.optJSONArray("data")
            val item = array?.optJSONObject(0)
            val downloadUrl = item?.optString("download_url").orEmpty()
            if (downloadUrl.isBlank()) {
                throw IOException("获取夸克播放地址失败，可能是会员权益或文件限制")
            }
            urlCache[cacheKey] = downloadUrl to System.currentTimeMillis()
            downloadUrl
        }
    }

    suspend fun fetchBytes(
        url: String,
        config: QuarkConfig,
        maxBytes: Long = 8L * 1024 * 1024
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", PC_USER_AGENT)
            .header("Referer", REFERER)
            .header("Cookie", currentCookie(config))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("下载夸克文件失败：HTTP ${response.code}")
            captureCookies(normalizeCookie(config.cookie), response)
            val body = response.body ?: throw IOException("文件内容为空")
            if (body.contentLength() > maxBytes) throw IOException("文件过大")
            val bytes = body.bytes()
            if (bytes.size > maxBytes) throw IOException("文件过大")
            bytes
        }
    }

    private suspend fun walk(
        config: QuarkConfig,
        fid: String,
        logicalPath: String,
        out: MutableList<ScannedFile>,
        depth: Int,
        onProgress: (Int, String) -> Unit,
        seen: MutableSet<String>
    ) {
        coroutineContext.ensureActive()
        if (depth > 10) return
        val entries = listFolder(config, fid)
        for (entry in entries) {
            coroutineContext.ensureActive()
            if (out.size >= 20000) {
                throw IOException("所选夸克目录下文件超过 20000 个，请在编辑媒体源时少选几个目录，或选择更具体的文件夹")
            }
            if (entry.name.isBlank() || entry.name.startsWith(".")) continue
            if (!seen.add(entry.fid)) continue
            val path = if (logicalPath == "/") "/${entry.name}" else "$logicalPath/${entry.name}"
            if (entry.isDir) {
                walk(config, entry.fid, path, out, depth + 1, onProgress, seen)
            } else {
                val kind = classify(entry.name)
                if (kind == ScannedKind.VIDEO || kind == ScannedKind.SUBTITLE) {
                    val parent = path.substringBeforeLast('/', "/").ifBlank { "/" }
                    out += ScannedFile(
                        remoteId = entry.fid,
                        path = path,
                        parentPath = parent,
                        name = entry.name,
                        size = entry.size,
                        lastModified = entry.updatedAt,
                        mime = mimeForExtension(extensionOf(entry.name)),
                        kind = kind,
                        playRef = entry.fid
                    )
                    if (out.size % 20 == 0) onProgress(out.size, path)
                }
            }
        }
        onProgress(out.size, logicalPath)
    }

    private fun listFolder(config: QuarkConfig, fid: String): List<QuarkEntry> {
        val result = ArrayList<QuarkEntry>()
        var page = 1
        var total = Long.MAX_VALUE
        while (result.size < total && page <= 200) {
            val url = "$BASE/file/sort".toHttpUrl().newBuilder()
                .addQueryParameter("pr", "ucpro")
                .addQueryParameter("fr", "pc")
                .addQueryParameter("uc_param_str", "")
                .addQueryParameter("pdir_fid", fid)
                .addQueryParameter("_page", page.toString())
                .addQueryParameter("_size", "100")
                .addQueryParameter("_fetch_total", "1")
                .addQueryParameter("_fetch_sub_dirs", "0")
                .addQueryParameter("_sort", "file_type:asc,file_name:asc")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Origin", "https://pan.quark.cn")
                .header("Cookie", currentCookie(config))
                .header("Accept", "application/json, text/plain, */*")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("夸克接口 HTTP ${response.code}")
                captureCookies(normalizeCookie(config.cookie), response)
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse {
                    throw IOException("夸克返回内容无法解析，可能登录已失效")
                }
                ensureSuccess(json)
                val data = json.optJSONObject("data") ?: return emptyList()
                data.optJSONObject("metadata")?.let {
                    val value = it.optLong("_total", -1L)
                    if (value >= 0) total = value
                }
                val list = data.optJSONArray("list") ?: JSONArray()
                if (list.length() == 0) return result
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    val name = item.optString("file_name")
                    if (name.isBlank()) continue
                    val isDir = item.optBoolean("dir", item.optInt("file_type") == 1)
                    val updated = item.optLong("updated_at", 0L).let { if (it in 1..9_999_999_999L) it * 1000 else it }
                    result += QuarkEntry(
                        fid = item.optString("fid"),
                        name = name,
                        isDir = isDir,
                        size = item.optLong("size", 0L),
                        updatedAt = updated
                    )
                }
            }
            if (result.size >= total) break
            page++
        }
        return result
    }

    private fun ensureSuccess(json: JSONObject) {
        val code = json.optInt("code", 0)
        if (code != 0) {
            val message = json.optString("message").ifBlank { "错误码 $code" }
            throw IOException(
                when {
                    code == 31001 || message.contains("登录", ignoreCase = true) ->
                        "夸克登录已失效，请重新填写 Cookie"
                    code == 23018 ->
                        "夸克限制了大文件直链（23018），当前播放地址无法获取。可改用 AList/WebDAV 接入夸克，或选择较小的片源。"
                    else -> "夸克接口错误：$message（$code）"
                }
            )
        }
    }

    private fun normalizeCookie(raw: String): String {
        var value = raw.trim().replace("\n", "").replace("\r", "")
        if (value.startsWith("Cookie:", ignoreCase = true)) value = value.substringAfter(':').trim()
        return value
    }

    data class QuarkDir(
        val fid: String,
        val name: String
    )

    private data class QuarkEntry(
        val fid: String,
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val updatedAt: Long
    )
}
