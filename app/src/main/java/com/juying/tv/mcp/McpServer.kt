package com.juying.tv.mcp

import com.juying.tv.data.AppGraph
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

object McpRuntime {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _port = MutableStateFlow(8787)
    val port: StateFlow<Int> = _port.asStateFlow()

    fun onStarted(port: Int) {
        _port.value = port
        _error.value = null
        _running.value = true
    }

    fun onError(message: String?) {
        _error.value = message
        _running.value = false
    }

    fun onStopped() {
        _running.value = false
    }
}

/**
 * 最小可用的 MCP Streamable HTTP 服务：
 * POST /mcp，Authorization: Bearer <token>，JSON-RPC 2.0。
 */
class McpServer(
    private val port: Int,
    private val token: String,
    graph: AppGraph,
    private val tasks: McpTaskRegistry
) : NanoHTTPD(port) {

    private val tools = McpTools(context = graph.appContext, graph = graph, tasks = tasks)

    override fun serve(session: IHTTPSession): Response {
        if (!isPrivateClient(session)) {
            return text(Response.Status.FORBIDDEN, "仅允许局域网访问")
        }
        return when (session.uri) {
            "/health" -> json(Response.Status.OK, JSONObject().put("ok", true).put("name", "juying-tv"))
            "/mcp" -> {
                if (session.method == Method.OPTIONS) {
                    return newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")
                }
                if (session.method != Method.POST) {
                    return text(Response.Status.METHOD_NOT_ALLOWED, "请使用 POST /mcp")
                }
                if (!authorized(session)) {
                    return text(Response.Status.UNAUTHORIZED, "缺少或错误的 Bearer Token")
                }
                handleMcp(session)
            }
            else -> text(Response.Status.NOT_FOUND, "Not found")
        }
    }

    private fun handleMcp(session: IHTTPSession): Response {
        val body = HashMap<String, String>()
        return try {
            // 多数 MCP 客户端不写 charset；NanoHTTPD 默认可能按单字节解码，先强制成 UTF-8。
            val contentType = session.headers["content-type"].orEmpty()
            if (!contentType.contains("charset=", true)) {
                session.headers["content-type"] = (contentType.ifBlank { "application/json" }) + "; charset=utf-8"
            }
            session.parseBody(body)
            val raw = body["postData"].orEmpty()
            if (raw.length > 1_000_000) {
                return text(Response.Status.BAD_REQUEST, "请求体过大")
            }
            // NanoHTTPD 对无 charset 的 application/json 可能按 Latin-1 解码，这里做一次 UTF-8 纠正。
            val textBody = decodeUtf8IfNeeded(raw)
            val trimmed = textBody.trim()
            if (trimmed.startsWith("[")) {
                val input = JSONArray(trimmed)
                val output = JSONArray()
                for (i in 0 until input.length()) {
                    val item = input.optJSONObject(i) ?: continue
                    handleRpc(item)?.let { output.put(it) }
                }
                if (output.length() == 0) {
                    newFixedLengthResponse(Response.Status.ACCEPTED, MIME_PLAINTEXT, "")
                } else {
                    json(Response.Status.OK, output)
                }
            } else {
                val input = JSONObject(trimmed)
                val output = handleRpc(input)
                if (output == null) {
                    newFixedLengthResponse(Response.Status.ACCEPTED, MIME_PLAINTEXT, "")
                } else {
                    json(Response.Status.OK, output)
                }
            }
        } catch (e: Exception) {
            json(
                Response.Status.OK,
                errorResponse(null, -32603, e.message ?: "服务器内部错误")
            )
        }
    }

    private fun decodeUtf8IfNeeded(raw: String): String {
        if (raw.isEmpty()) return raw
        if (raw.any { it.code > 255 }) return raw
        if (raw.none { it.code in 128..255 }) return raw
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        return runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault(raw)
    }

    private fun handleRpc(request: JSONObject): JSONObject? {
        val method = request.optString("method")
        val id = if (request.has("id")) request.opt("id") else null
        if (method.isBlank()) {
            return if (id == null) null else errorResponse(id, -32600, "缺少 method")
        }
        return when (method) {
            "initialize" -> {
                val requested = request.optJSONObject("params")?.optString("protocolVersion").orEmpty()
                val version = if (requested in setOf("2024-11-05", "2025-03-26", "2025-06-18")) {
                    requested
                } else {
                    "2025-03-26"
                }
                resultResponse(
                    id,
                    JSONObject()
                        .put("protocolVersion", version)
                        .put(
                            "capabilities",
                            JSONObject().put("tools", JSONObject().put("listChanged", false))
                        )
                        .put(
                            "serverInfo",
                            JSONObject().put("name", "juying-tv").put("version", "1.4.5")
                        )
                )
            }
            "notifications/initialized", "notifications/cancelled" -> null
            "ping", "logging/setLevel" -> resultResponse(id, JSONObject())
            "tools/list" -> resultResponse(id, JSONObject().put("tools", tools.toolsJson()))
            "tools/call" -> {
                val params = request.optJSONObject("params")
                val name = params?.optString("name").orEmpty()
                val args = params?.optJSONObject("arguments") ?: JSONObject()
                if (name.isBlank()) {
                    resultResponse(
                        id,
                        toolResult("缺少工具名", isError = true)
                    )
                } else {
                    try {
                        val result = runBlocking { tools.call(name, args) }
                        resultResponse(id, toolResult(result.toString(2)))
                    } catch (e: Exception) {
                        resultResponse(id, toolResult(e.message ?: "工具执行失败", isError = true))
                    }
                }
            }
            "resources/list" -> resultResponse(id, JSONObject().put("resources", JSONArray()))
            "prompts/list" -> resultResponse(id, JSONObject().put("prompts", JSONArray()))
            else -> errorResponse(id, -32601, "未知方法：$method")
        }
    }

    private fun toolResult(text: String, isError: Boolean = false): JSONObject =
        JSONObject()
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
            .put("isError", isError)

    private fun resultResponse(id: Any?, result: JSONObject): JSONObject =
        JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL).put("result", result)

    private fun errorResponse(id: Any?, code: Int, message: String): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id ?: JSONObject.NULL)
            .put("error", JSONObject().put("code", code).put("message", message))

    private fun authorized(session: IHTTPSession): Boolean {
        if (token.isBlank()) return false
        val header = session.headers["authorization"].orEmpty()
        val expected = "Bearer $token"
        return MessageDigest.isEqual(header.toByteArray(), expected.toByteArray())
    }

    private fun isPrivateClient(session: IHTTPSession): Boolean {
        val raw = session.remoteIpAddress.orEmpty().removePrefix("::ffff:")
        if (raw == "::1" || raw == "127.0.0.1" || raw.startsWith("127.")) return true
        if (raw.startsWith("fe80:") || raw.startsWith("fc") || raw.startsWith("fd")) return true
        val parts = raw.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val a = parts[0]
        val b = parts[1]
        return when {
            a == 10 -> true
            a == 192 && b == 168 -> true
            a == 172 && b in 16..31 -> true
            a == 100 && b in 64..127 -> true
            else -> false
        }
    }

    private fun json(status: Response.Status, value: Any): Response {
        val response = newFixedLengthResponse(status, "application/json; charset=utf-8", value.toString())
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Mcp-Session-Id")
        response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        if (value is JSONObject && value.has("result")) {
            response.addHeader("Mcp-Session-Id", UUID.randomUUID().toString())
        }
        return response
    }

    private fun text(status: Response.Status, message: String): Response {
        val response = newFixedLengthResponse(status, "text/plain; charset=utf-8", message)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }
}
