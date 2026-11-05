package com.juying.tv

import com.juying.tv.util.SubtitleEncoding
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SubtitleEncodingTest {

    @Test
    fun convertsGbkSubtitleToUtf8() {
        val text = "1\n00:00:01,000 --> 00:00:03,000\n你好，世界！\n"
        val gbk = text.toByteArray(Charset.forName("GBK"))
        val utf8 = SubtitleEncoding.toUtf8(gbk)
        val decoded = String(utf8, Charsets.UTF_8)
        assertTrue(decoded.contains("你好，世界！"))
    }

    @Test
    fun convertsBig5SubtitleToUtf8() {
        val text = "1\n00:00:01,000 --> 00:00:03,000\n這是一個繁體中文字幕測試，我們今天要學習一首新的歌曲。\n" +
            "2\n00:00:04,000 --> 00:00:06,000\n你喜歡這首歌嗎？我們一起來唱吧。\n"
        val big5 = text.toByteArray(Charset.forName("Big5"))
        val decoded = String(SubtitleEncoding.toUtf8(big5), Charsets.UTF_8)
        assertTrue(decoded.contains("這是一個繁體中文字幕測試"))
    }

    @Test
    fun keepsUtf8SubtitleUnchanged() {
        val bytes = "1\n00:00:01,000 --> 00:00:03,000\nhello\n".toByteArray(Charsets.UTF_8)
        assertArrayEquals(bytes, SubtitleEncoding.toUtf8(bytes))
    }
}
