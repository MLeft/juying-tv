package com.juying.tv.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "--"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[index])
}

fun formatDate(epochMs: Long): String {
    if (epochMs <= 0) return "尚未扫描"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(epochMs))
}

fun formatEpisode(season: Int, episode: Int, isMovie: Boolean): String {
    if (isMovie) return "电影"
    return if (season > 1) "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
    else "第 ${episode.toString().padStart(2, '0')} 集"
}

fun formatSpeed(speed: Float): String {
    val value = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return "${value}x"
}
