package com.juying.tv.data.source.quark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
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
        val json = getJson("https://uop.quark.cn/cas/ajax/getTokenForQrcodeLogin")
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
        val json = getJson("https://uop.quark.cn/cas/ajax/getServiceTicketByQrcodeToken?token=$token")
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
        val request = Request.Builder()
            .url("https://pan.quark.cn/account/info?st=$ticket&lw=scan")
            .headers(commonHeaders())
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("夸克登录确认失败：HTTP ${response.code}")
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (json != null && json.has("success") && !json.optBoolean("success")) {
                val message = json.optString("msg").ifBlank { "服务端未确认登录" }
                throw IOException("夸克登录确认失败：$message")
            }
        }
        val cookie = synchronized(cookieStore) {
            cookieStore.joinToString("; ") { "${it.name}=${it.value}" }
        }.trim()
        if (!cookie.contains("__puus=") || !cookie.contains("__pus=")) {
            throw IOException("登录成功但未获取到完整 Cookie，请刷新二维码重试")
        }
        cookie
    }

    private fun buildScanUrl(token: String): String =
        "https://su.quark.cn/4_eMHBJ".toHttpUrl().newBuilder()
            .addQueryParameter("token", token)
            .addQueryParameter("client_id", "532")
            .addQueryParameter("ssb", "weblogin")
            .addQueryParameter("uc_param_str", "")
            .addQueryParameter(
                "uc_biz_str",
                "S:custom|OPT:SAREA@0|OPT:IMMERSIVE@1|OPT:BACK_BTN_STYLE@0"
            )
            .build()
            .toString()

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .headers(commonHeaders())
            .get()
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
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }
}
