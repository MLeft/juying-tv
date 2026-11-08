package com.juying.tv.data.source.quark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * 夸克 App 扫码登录。
 *
 * 流程与网页登录一致：
 * 1. 获取二维码 token；
 * 2. 手机扫码访问 su.quark.cn 确认页；
 * 3. 轮询 service_ticket；
 * 4. 用 service_ticket 请求 account/info，换取登录 Cookie。
 */
class QuarkQrAuth(baseClient: OkHttpClient) {

    data class Session(val token: String, val scanUrl: String)

    sealed class PollResult {
        data object Pending : PollResult()
        data object Expired : PollResult()
        data class Confirmed(val ticket: String) : PollResult()
    }

    private val cookieStore = mutableListOf<Cookie>()
    private val client = baseClient.newBuilder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(cookieStore) {
                    cookies.forEach { cookie ->
                        cookieStore.removeAll {
                            it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                        }
                        cookieStore.add(cookie)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                synchronized(cookieStore) { cookieStore.filter { it.matches(url) } }
        })
        .build()

    suspend fun createSession(): Session = withContext(Dispatchers.IO) {
        val json = postForm(
            "https://uop.quark.cn/cas/ajax/getTokenForQrcodeLogin",
            linkedMapOf(
                "client_id" to CLIENT_ID.toString(),
                "v" to "1.2",
                "request_id" to System.currentTimeMillis().toString()
            )
        )
        val token = json.optJSONObject("data")
            ?.optJSONObject("members")
            ?.optString("token")
            .orEmpty()
        if (token.isBlank()) {
            throw IOException(json.optString("message").ifBlank { "获取扫码令牌失败" })
        }
        Session(token, buildScanUrl(token))
    }

    suspend fun poll(token: String): PollResult = withContext(Dispatchers.IO) {
        val json = postForm(
            "https://uop.quark.cn/cas/ajax/getServiceTicketByQrcodeToken",
            linkedMapOf(
                "client_id" to CLIENT_ID.toString(),
                "v" to "1.2",
                "request_id" to System.currentTimeMillis().toString(),
                "token" to token
            )
        )
        when (json.optInt("status")) {
            2000000 -> {
                val ticket = json.optJSONObject("data")
                    ?.optJSONObject("members")
                    ?.optString("service_ticket")
                    .orEmpty()
                if (ticket.isBlank()) throw IOException("登录确认失败，请重试")
                PollResult.Confirmed(ticket)
            }
            50004001 -> PollResult.Pending
            50004002 -> PollResult.Expired
            else -> throw IOException(json.optString("message").ifBlank { "扫码登录失败，请刷新重试" })
        }
    }

    /** 用 service_ticket 换取登录 Cookie。 */
    suspend fun exchange(ticket: String): String = withContext(Dispatchers.IO) {
        // 先建立 pan 域会话（ctoken），再兑换 ticket；之后补一次 info 与列表页，
        // 部分登录 Cookie 是在后续请求的 Set-Cookie 中下发的。
        runCatching { getText("https://pan.quark.cn/") }
        val infoUrl = "https://pan.quark.cn/account/info".toHttpUrl().newBuilder()
            .addQueryParameter("st", ticket)
            .addQueryParameter("lw", "scan")
            .build()
            .toString()
        val first = getJson(infoUrl)
        if (first.has("success") && !first.optBoolean("success")) {
            val message = first.optString("msg").ifBlank { "服务端未确认登录" }
            throw IOException("夸克登录确认失败：$message")
        }
        runCatching { getJson("https://pan.quark.cn/account/info") }
        runCatching { getText("https://pan.quark.cn/list") }
        // 夸克 API 通过 Set-Cookie 续期 __puus；用一次最小目录请求补齐它。
        runCatching {
            getJson(
                "https://drive-pc.quark.cn/1/clouddrive/file/sort" +
                    "?pr=ucpro&fr=pc&uc_param_str=&pdir_fid=0&_page=1&_size=1" +
                    "&_fetch_total=0&_fetch_sub_dirs=0&_sort=file_type:asc,file_name:asc"
            )
        }
        val cookie = synchronized(cookieStore) {
            cookieStore.joinToString("; ") { "${it.name}=${it.value}" }
        }.trim()
        val names = synchronized(cookieStore) { cookieStore.map { it.name }.distinct() }
        android.util.Log.i("JuyingQuarkQr", "login cookies: ${names.joinToString(",")}")
        if (!cookie.contains("__pus=")) {
            throw IOException("登录成功但未获取到登录 Cookie，请刷新二维码重试")
        }
        if (!cookie.contains("__puus=")) {
            // __puus 会由 drive-pc 接口响应续期；首次扫描/播放时自动补齐。
            android.util.Log.w("JuyingQuarkQr", "login cookie missing __puus, will renew via API")
        }
        cookie
    }

    private fun buildScanUrl(token: String): String =
        "https://su.quark.cn/4_eMHBJ".toHttpUrl().newBuilder()
            .addQueryParameter("uc_param_str", "")
            .addQueryParameter("token", token)
            .addQueryParameter("client_id", CLIENT_ID.toString())
            .addQueryParameter(
                "uc_biz_str",
                "S:custom|OPT:SAREA@0|OPT:IMMERSIVE@1|OPT:BACK_BTN_STYLE@0"
            )
            .build()
            .toString()

    private fun getJson(url: String): JSONObject {
        val text = getText(url)
        return runCatching { JSONObject(text) }
            .getOrElse { throw IOException("夸克登录接口返回异常，请稍后重试") }
    }

    private fun getText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .headers(commonHeaders())
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("夸克登录接口 HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .headers(commonHeaders())
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("夸克登录接口 HTTP ${response.code}")
            }
            return runCatching { JSONObject(text) }
                .getOrElse { throw IOException("夸克登录接口返回异常，请稍后重试") }
        }
    }

    private fun commonHeaders(): okhttp3.Headers = okhttp3.Headers.Builder().apply {
        add("User-Agent", DESKTOP_UA)
        add("Referer", "https://pan.quark.cn/")
        add("Origin", "https://pan.quark.cn")
        add("Accept", "application/json, text/plain, */*")
        add("Accept-Language", "zh-CN,zh;q=0.9")
    }.build()

    private companion object {
        const val CLIENT_ID = 386
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }
}
