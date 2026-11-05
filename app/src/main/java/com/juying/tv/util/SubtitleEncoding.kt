package com.juying.tv.util

import org.mozilla.universalchardet.UniversalDetector
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 外挂字幕常见的 GBK/Big5 编码在 UTF-8 解析下会乱码，这里统一转换为 UTF-8。 */
object SubtitleEncoding {

    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun toUtf8(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes
        return try {
            // UTF-8 BOM 直接保留，Media3 可识别。
            if (bytes.size >= 3 && bytes[0] == utf8Bom[0] && bytes[1] == utf8Bom[1] && bytes[2] == utf8Bom[2]) {
                return bytes
            }
            // UTF-16 BOM 需要转成 UTF-8。
            if (bytes.size >= 2) {
                val le = bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
                val be = bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
                if (le || be) {
                    val cs = Charset.forName(if (le) "UTF-16LE" else "UTF-16BE")
                    return String(bytes, 2, bytes.size - 2, cs).toByteArray(Charsets.UTF_8)
                }
            }
            // 已经是合法 UTF-8（含纯 ASCII）就不动。
            if (isValidUtf8(bytes)) return bytes

            val detector = UniversalDetector()
            detector.handleData(bytes, 0, bytes.size)
            detector.dataEnd()
            val detected = detector.detectedCharset
            detector.reset()
            if (!detected.isNullOrBlank()) {
                val upper = detected.uppercase()
                if (!upper.startsWith("UTF-8") && !upper.startsWith("UTF-16") && upper != "US-ASCII") {
                    return String(bytes, Charset.forName(detected)).toByteArray(Charsets.UTF_8)
                }
            }
            // 短字幕样本不足以让检测器给出结果：在 GB18030 与 Big5 中选常用字更多的一种。
            val gbText = String(bytes, Charset.forName("GB18030"))
            val big5Text = runCatching { String(bytes, Charset.forName("Big5")) }.getOrNull()
            val chosen = if (big5Text != null && commonChineseScore(big5Text) > commonChineseScore(gbText)) {
                big5Text
            } else {
                gbText
            }
            if (containsCjk(chosen)) chosen.toByteArray(Charsets.UTF_8) else bytes
        } catch (e: Exception) {
            bytes
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun containsCjk(text: String): Boolean = text.any { c ->
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF
    }

    private const val COMMON_CHINESE =
        "的一是不了在人有我他她它這这個个們们來来去去到說说時时会會對对為为國国学學好你您嗎吗呢吧啊很都就也還还和與与上中大下小多少年月日"

    private fun commonChineseScore(text: String): Int {
        var score = 0
        for (c in text) if (c in COMMON_CHINESE) score++
        return score
    }
}
