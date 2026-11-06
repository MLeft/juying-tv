package com.juying.tv.data.ai

data class AiConfig(
    val provider: String,
    val providerName: String,
    val baseUrl: String,
    val nativeBaseUrl: String?,
    val apiKey: String,
    val model: String,
    val supportsSearch: Boolean
)

data class AiProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val nativeBaseUrl: String? = null,
    val models: List<String> = emptyList(),
    val supportsSearch: Boolean = false,
    val customBaseUrl: Boolean = false
)

/**
 * OpenAI 兼容供应商预设。LiteLLM / New API / One API 等自建网关
 * 可直接选“自定义 OpenAI 兼容”，填网关地址后动态读取 /models。
 */
object AiProviders {
    val presets = listOf(
        AiProviderPreset(
            id = "bailian",
            name = "阿里云百炼",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            nativeBaseUrl = "https://dashscope.aliyuncs.com",
            models = listOf(
                "qwen-plus", "qwen-max", "qwen-turbo", "qwen-long",
                "qwen3-max", "qwen3.5-plus", "qwen3.7-plus", "qwen3.8-max"
            ),
            supportsSearch = true
        ),
        AiProviderPreset(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            models = listOf("gpt-5", "gpt-5-mini", "gpt-4.1", "gpt-4o")
        ),
        AiProviderPreset(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            models = listOf("deepseek-chat", "deepseek-reasoner")
        ),
        AiProviderPreset(
            id = "siliconflow",
            name = "硅基流动 SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1"
        ),
        AiProviderPreset(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1"
        ),
        AiProviderPreset(
            id = "moonshot",
            name = "月之暗面 Moonshot",
            baseUrl = "https://api.moonshot.cn/v1",
            models = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
        ),
        AiProviderPreset(
            id = "zhipu",
            name = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            models = listOf("glm-4-plus", "glm-4-air", "glm-4-flash")
        ),
        AiProviderPreset(
            id = "custom",
            name = "OpenAI 兼容 / 自建网关",
            baseUrl = "",
            customBaseUrl = true
        )
    )

    fun byId(id: String): AiProviderPreset =
        presets.firstOrNull { it.id == id } ?: presets.first()

    fun defaultModel(id: String): String = byId(id).models.firstOrNull() ?: ""
}
