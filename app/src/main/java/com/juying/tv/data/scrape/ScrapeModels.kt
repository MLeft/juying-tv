package com.juying.tv.data.scrape

import java.io.IOException

/** 接口限流/风控，通知上层暂停后重试。 */
class ScrapeRateLimitedException(
    val provider: String,
    val retryAfterMs: Long,
    message: String
) : IOException(message)

data class ScrapeSourceTest(
    val provider: String,
    val ok: Boolean,
    val count: Int,
    val detail: String
)
