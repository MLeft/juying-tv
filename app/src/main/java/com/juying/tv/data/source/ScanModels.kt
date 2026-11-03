package com.juying.tv.data.source

enum class ScannedKind { VIDEO, SUBTITLE, IMAGE, OTHER }

data class ScannedFile(
    val remoteId: String,
    val path: String,
    val parentPath: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mime: String?,
    val kind: ScannedKind,
    val playRef: String
)

data class ScanResult(
    val files: List<ScannedFile>,
    val rootLabel: String
)

data class ParsedName(
    val title: String,
    val groupTitle: String = title,
    val season: Int = 1,
    val episode: Int = 1,
    val hasEpisodeMarker: Boolean = false,
    val explicitEpisode: Boolean = false,
    val year: Int? = null
)

data class GroupedFile(
    val file: ScannedFile,
    val parsed: ParsedName
)

data class LibraryGroup(
    val key: String,
    val title: String,
    val kind: String,
    val year: Int?,
    val coverRef: String?,
    val files: List<GroupedFile>
)

fun videoExtensions(): Set<String> = setOf(
    "mp4", "mkv", "avi", "ts", "m2ts", "m4v", "mov", "wmv", "flv", "webm",
    "rmvb", "rm", "mpg", "mpeg", "3gp", "vob", "iso", "mts", "divx", "f4v"
)

fun subtitleExtensions(): Set<String> = setOf("srt", "ass", "ssa", "vtt")

fun imageExtensions(): Set<String> = setOf("jpg", "jpeg", "png", "webp")

fun extensionOf(name: String): String =
    name.substringAfterLast('.', "").lowercase()

fun mimeForExtension(ext: String): String = when (ext) {
    "srt" -> "application/x-subrip"
    "ass", "ssa" -> "text/x-ssa"
    "vtt" -> "text/vtt"
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "avi" -> "video/x-msvideo"
    "ts", "m2ts", "mts" -> "video/mp2t"
    "mov" -> "video/quicktime"
    "webm" -> "video/webm"
    "flv" -> "video/x-flv"
    "wmv" -> "video/x-ms-wmv"
    "mpg", "mpeg" -> "video/mpeg"
    else -> "application/octet-stream"
}

fun classify(name: String): ScannedKind {
    val ext = extensionOf(name)
    return when {
        ext in videoExtensions() -> ScannedKind.VIDEO
        ext in subtitleExtensions() -> ScannedKind.SUBTITLE
        ext in imageExtensions() -> ScannedKind.IMAGE
        else -> ScannedKind.OTHER
    }
}

fun isGenericFolder(name: String): Boolean {
    val value = name.trim().lowercase()
    if (value.isBlank() || value == "/") return true
    return value in setOf(
        "downloads", "download", "videos", "video", "movies", "movie",
        "tv", "tv shows", "tvshows", "media", "anime", "animation",
        "动画", "动漫", "動畫", "电视剧", "電視劇", "电影", "電影", "视频", "視頻",
        "下载", "下載", "影视", "影視", "新建文件夹", "新建資料夾", "夸克网盘", "夸克網盤",
        "我的资源", "我的資源", "资源", "資源", "共享", "uploads", "upload", "root",
        "storage", "usb", "sda1", "sdb1", "外部存储", "u盘", "本地视频", "全部视频"
    )
}

fun isSeasonFolder(name: String): Boolean {
    val value = name.trim()
    if (value.isBlank()) return false
    return Regex("""(?i)^(season\s*\d{1,2}|s\d{1,2}|第\s*\d{1,2}\s*季|\d{1,2}季)$""").matches(value)
}
