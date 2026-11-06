package com.juying.tv.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.juying.tv.data.db.AppDatabase
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.repo.LibraryRepository
import com.juying.tv.data.repo.PlaybackResolver
import com.juying.tv.util.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class AiApplyResult(
    val message: String,
    val episodes: Int,
    val coverApplied: Boolean
)

class AiAgent(
    private val context: Context,
    private val database: AppDatabase,
    private val repository: LibraryRepository,
    private val ai: AiClient,
    private val playbackResolver: PlaybackResolver
) {

    /**
     * 整理一部剧集：文件名/目录 → 季集/标题，必要时用联网搜索补封面。
     */
    suspend fun organizeSeries(
        series: SeriesEntity,
        findCover: Boolean = true,
        onStage: (String) -> Unit = {}
    ): AiApplyResult {
        val media = database.mediaDao().allBySeries(series.id)
        if (media.isEmpty()) return AiApplyResult("没有可整理的文件", 0, false)
        val files = media.map { item ->
            val folder = item.parentPath
                .split('/')
                .filter { it.isNotBlank() }
                .takeLast(2)
                .joinToString("/")
            AiFile(item.fileName, folder)
        }
        // 长剧集分块请求，避免单次 JSON 过长导致超时/截断。
        val chunkSize = 40
        val chunks = files.chunked(chunkSize)
        val mergedEpisodes = ArrayList<AiEpisodePlan>()
        var mergedTitle: String? = null
        chunks.forEachIndexed { chunkIndex, chunk ->
            onStage("正在整理第 ${chunkIndex + 1}/${chunks.size} 批（${chunk.size} 集）…")
            Log.i("JuyingAi", "原始目录：${series.remotePath}")
            val chunkPlan = ai.normalize(series.displayTitle, series.year, chunk, series.remotePath)
                ?: return AiApplyResult(
                    "AI 第 ${chunkIndex + 1}/${chunks.size} 批没有返回可解析结果",
                    0,
                    false
                )
            if (!chunkPlan.seriesTitle.isNullOrBlank() && mergedTitle.isNullOrBlank()) {
                mergedTitle = chunkPlan.seriesTitle
            }
            chunkPlan.episodes.forEach { episode ->
                mergedEpisodes += episode.copy(index = chunkIndex * chunkSize + episode.index)
            }
            if (chunkIndex < chunks.lastIndex) kotlinx.coroutines.delay(300)
        }
        val improvedTitle = improveSeriesTitle(
            mergedTitle,
            series.displayTitle,
            series.remotePath,
            files
        )
        val plan = AiSeriesPlan(improvedTitle, mergedEpisodes)
        Log.i("JuyingAi", "分块整理：${chunks.size} 批，合计 ${plan.episodes.size} 集，标题=$improvedTitle")
        if (media.size >= 5 && plan.episodes.size < media.size / 3) {
            return AiApplyResult("AI 返回的集数过少（${plan.episodes.size}/${media.size}），已放弃应用", 0, false)
        }

        var coverUri: String? = null
        if (findCover && series.coverUrl.isNullOrBlank()) {
            onStage("正在联网搜索封面…")
            coverUri = downloadCover(series)
        }
        val originalSeasons = media.map { it.season }.distinct()
        val aiSeasons = plan.episodes.map { it.season }.distinct()
        val collapsedSeasons = originalSeasons.size > 1 && aiSeasons.size == 1
        if (collapsedSeasons) {
            Log.w("JuyingAi", "AI 把多季折叠成单季，保留原始季/集，仅应用标题")
        }
        val usedPairs = HashSet<Pair<Int, Int>>()
        val sanitized = plan.copy(
            episodes = plan.episodes.map { episode ->
                val target = media.getOrNull(episode.index - 1)
                val fallback = cleanFileStem(target?.fileName.orEmpty())
                val title = episode.title.takeIf {
                    !isGenericTitle(it, series.displayTitle, fallback)
                } ?: fallback
                var season = episode.season
                var number = episode.episode
                if (collapsedSeasons || !usedPairs.add(season to number)) {
                    target?.let {
                        season = it.season
                        number = it.episode
                    }
                }
                usedPairs.add(season to number)
                episode.copy(season = season, episode = number, title = title)
            }
        )
        android.util.Log.i(
            "JuyingAi",
            "计划：${sanitized.seriesTitle}，${sanitized.episodes.size} 集，示例=" +
                sanitized.episodes.take(3).joinToString { "${it.season}-${it.episode}:${it.title}" }
        )
        onStage("正在写入媒体库…")
        val applied = repository.applyAiPlan(series, sanitized, coverUri)
        if (applied == 0) return AiApplyResult("AI 结果与文件不匹配，未修改", 0, false)

        val name = sanitized.seriesTitle ?: series.displayTitle
        val coverText = if (coverUri != null) "，已更新封面" else ""
        return AiApplyResult(
            "AI 已整理「$name」：$applied 集$coverText",
            applied,
            coverUri != null
        )
    }

    suspend fun fillCover(series: SeriesEntity): String? = downloadCover(series)

    private fun improveSeriesTitle(
        aiTitle: String?,
        currentTitle: String,
        remotePath: String,
        files: List<AiFile>
    ): String? {
        val title = aiTitle?.trim().orEmpty()
        if (title.isBlank()) return null
        val genericMarkers = listOf(
            "引进版", "完整版", "中英", "英文", "中文", "双语",
            "视频", "音频", "课程", "合集", "全集"
        )
        val looksGeneric = genericMarkers.any { title.contains(it) } ||
            Regex("第\\s*\\d+\\s*[-~至到]?\\s*\\d*\\s*级").containsMatchIn(title)
        if (!looksGeneric) return title
        val brand = findBrand(remotePath, files) ?: return title
        if (title.contains(brand, ignoreCase = true)) return title
        return "$brand · $title".take(80)
    }

    /** 从分组目录/文件目录里找作品品牌名，避免标题只剩“引进版/第x级/中英”。 */
    private fun findBrand(remotePath: String, files: List<AiFile>): String? {
        val genericWords = setOf(
            "english", "video", "audio", "unit", "units", "level", "levels",
            "course", "lesson", "lessons", "mp4", "mkv", "avi", "mov",
            "引进版", "完整版", "中英", "英文", "中文", "双语", "视频", "音频"
        )
        fun latinWords(value: String): List<String> =
            value.split(Regex("[^A-Za-z]+"))
                .map { it.trim() }
                .filter { it.length >= 2 && it.lowercase() !in genericWords }

        val pathSegments = (remotePath.split('/') + files.flatMap { it.folder.split('/') })
            .map { it.trim() }
            .filter { it.isNotBlank() }
        for (segment in pathSegments) {
            val words = latinWords(segment)
            if (words.size >= 2) {
                // 目录名通常是完整品牌，如 Wow English / Super Simple Songs。
                return words.take(4).joinToString(" ")
            }
        }
        // 目录没有品牌时，尝试文件名共同前缀。
        val samples = files.take(30).map { latinWords(it.name.substringBeforeLast('.')) }
            .filter { it.isNotEmpty() }
        if (samples.size < 2) return null
        val common = ArrayList<String>()
        val limit = samples.minOf { it.size }
        for (i in 0 until limit) {
            val word = samples.first()[i]
            if (samples.all { it.getOrNull(i)?.equals(word, ignoreCase = true) == true }) {
                common += word
            } else break
        }
        return common.takeIf { it.size >= 2 }?.take(4)?.joinToString(" ")
    }

    private fun cleanFileStem(fileName: String): String {
        if (fileName.isBlank()) return ""
        var value = fileName.substringBeforeLast('.')
        value = value.replace(Regex("^\\d{1,3}[.\\s_-]+"), "")
        value = value.replace(Regex("(?i)(1080p|720p|2160p|4k|bluray|web[- ]?dl|hdtv|x264|x265|hevc|aac|h\\.?264|h\\.?265)"), " ")
        value = value.replace(Regex("[_]+"), " ")
        return value.replace(Regex("\\s+"), " ").trim().take(60)
    }

    private fun isGenericTitle(title: String, seriesTitle: String, fallback: String): Boolean {
        val value = title.trim()
        if (value.length < 2) return true
        if (value.all { it.isDigit() }) return true
        if (value == seriesTitle || value == seriesTitle.substringBefore(" 第一季")) return true
        if (value == fallback) return false
        val generic = setOf(
            "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
            "又一个", "下一个", "未知", "未命名", "暂无", "无标题"
        )
        if (value in generic) return true
        if (value.matches(Regex("第\\s*\\d+\\s*集"))) return true
        return false
    }

    private suspend fun downloadCover(series: SeriesEntity): String? = withContext(Dispatchers.IO) {
        val poster = ai.findPoster(series.displayTitle, series.year)
        android.util.Log.i(
            "JuyingAi",
            "封面搜索「${series.displayTitle}」：${if (poster == null) "无结果" else poster.sourceUrl.substringBefore('?')}"
        )
        if (poster == null) {
            // 联网搜索没有拿到可用海报时，用第一集真实缩略图兜底，至少保证有封面。
            val first = database.mediaDao().allBySeries(series.id).firstOrNull()
            val thumb = first?.let { runCatching { playbackResolver.episodeThumbnail(it.id) }.getOrNull() }
            if (thumb != null) {
                val uri = android.net.Uri.fromFile(thumb).toString()
                repository.applyAiCover(series, uri)
                Log.i("JuyingAi", "AI 封面改用首集缩略图")
                return@withContext uri
            }
            return@withContext null
        }
        val bitmap = BitmapFactory.decodeByteArray(poster.bytes, 0, poster.bytes.size)
            ?: return@withContext null
        val width = 800
        val scaled = if (bitmap.width > width) {
            val height = (bitmap.height * (width.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "ai-${sha256(series.id).take(24)}.jpg")
        runCatching {
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
        }.onFailure { Log.w("JuyingAi", "AI 封面保存失败: ${it.message}") }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        if (!file.exists() || file.length() < 1024) return@withContext null
        val uri = android.net.Uri.fromFile(file).toString()
        repository.applyAiCover(series, uri)
        uri
    }
}
