package com.juying.tv.ui.util

import com.juying.tv.util.localIpv4Address
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PhoneField(
    val key: String,
    val label: String,
    val placeholder: String = "",
    val value: String = "",
    val multiline: Boolean = false,
    val password: Boolean = false,
    val hint: String = ""
)

data class PhoneResult(
    val values: Map<String, String>
)

class PhoneInputServer(
    private val title: String,
    private val fields: List<PhoneField>
) : NanoHTTPD(0) {

    private val token = UUID.randomUUID().toString().replace("-", "").take(10)

    private val _url = MutableStateFlow<String?>(null)
    val url = _url.asStateFlow()

    private val _result = MutableStateFlow<PhoneResult?>(null)
    val result = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun startServer(): Boolean {
        return runCatching {
            start(SOCKET_READ_TIMEOUT, false)
            val ip = localIpv4Address() ?: throw IllegalStateException("电视未连接到局域网")
            _url.value = "http://$ip:$listeningPort/$token"
            true
        }.getOrElse {
            _error.value = it.message ?: "手机填写服务启动失败"
            false
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/$token") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "页面不存在")
        }
        return try {
            when (session.method) {
                Method.POST -> handlePost(session)
                else -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", formHtml())
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/html; charset=utf-8",
                pageShell("提交失败", "<p class='err'>${escape(e.message ?: "未知错误")}</p>")
            )
        }
    }

    private fun handlePost(session: IHTTPSession): Response {
        val body = HashMap<String, String>()
        session.parseBody(body)
        val params = session.parameters
        val values = fields.associate { field ->
            field.key to (params[field.key]?.firstOrNull()?.trim().orEmpty())
        }
        _result.value = PhoneResult(values)
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=utf-8",
            pageShell(
                "已发送到电视",
                "<div class='ok'>✓</div><p class='lead'>信息已发送，可以关闭此页面回到电视继续操作。</p>"
            )
        )
    }

    private fun formHtml(): String {
        val inputs = fields.joinToString("\n") { field ->
            val label = "<label>${escape(field.label)}</label>"
            val hint = if (field.hint.isNotBlank()) "<div class='hint'>${escape(field.hint)}</div>" else ""
            val input = if (field.multiline) {
                "<textarea name='${field.key}' rows='5' placeholder='${escape(field.placeholder)}'>${escape(field.value)}</textarea>"
            } else {
                "<input name='${field.key}' type='${if (field.password) "password" else "text"}' " +
                    "value='${escape(field.value)}' placeholder='${escape(field.placeholder)}' autocomplete='off' />"
            }
            "<div class='field'>$label$input$hint</div>"
        }
        return pageShell(
            title,
            """
            <p class='lead'>在手机上填写，电视会即时收到。请确保手机连接同一个 Wi-Fi。</p>
            <form method='post' action='/${token}'>
            $inputs
            <button type='submit'>发送到电视</button>
            </form>
            """.trimIndent()
        )
    }

    private fun pageShell(heading: String, body: String): String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1" />
<meta name="theme-color" content="#070B12" />
<title>$heading · 聚影 TV</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 22px; background: #070B12; color: #F4F7FB;
         font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif; }
  h1 { font-size: 22px; margin: 6px 0 4px; color: #E7B563; }
  .brand { font-size: 12px; letter-spacing: 2px; color: #6E8099; text-transform: uppercase; }
  .lead { color: #9FB0C7; font-size: 14px; line-height: 1.6; margin: 10px 0 20px; }
  .field { margin: 0 0 16px; }
  label { display: block; font-size: 13px; color: #9FB0C7; margin-bottom: 7px; }
  input, textarea { width: 100%; background: #111C2E; border: 1px solid #263B55; border-radius: 12px;
                    color: #F4F7FB; padding: 13px 14px; font-size: 16px; outline: none; }
  input:focus, textarea:focus { border-color: #E7B563; }
  textarea { resize: vertical; font-family: ui-monospace, Menlo, monospace; font-size: 13px; line-height: 1.5; }
  .hint { color: #6E8099; font-size: 12px; line-height: 1.5; margin-top: 6px; }
  button { width: 100%; margin-top: 6px; padding: 15px; border: 0; border-radius: 14px;
           background: #E7B563; color: #201503; font-size: 17px; font-weight: 700; }
  button:active { opacity: .8; }
  .ok { width: 64px; height: 64px; border-radius: 50%; background: #63D6A3; color: #04120C;
        font-size: 36px; display: flex; align-items: center; justify-content: center; margin: 26px 0; }
  .err { color: #FF6B6B; }
  .footer { color: #4E5F78; font-size: 11px; margin-top: 26px; line-height: 1.6; }
</style>
</head>
<body>
  <div class="brand">JUYING TV</div>
  <h1>$heading</h1>
  $body
  <div class="footer">此页面只在你的局域网内可访问，关闭电视端对话框后立即失效。</div>
</body>
</html>
""".trimIndent()

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
