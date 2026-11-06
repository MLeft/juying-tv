package com.juying.tv.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.juying.tv.data.db.AppDatabase
import com.juying.tv.data.db.SeriesEntity
import com.juying.tv.data.repo.LibraryRepository
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
    private val bailian: BailianClient
) {

    /**
     * 整理一部剧集：文件名/目录 → 季集/标题，必要时用联网搜索补封面。
     */
    suspend fun organizeSeries(series: SeriesEntity, findCover: Boolean = true): AiApplyResult {
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
        val plan = bailian.normalize(series.displayTitle, series.year, files)
            ?: return AiApplyResult("AI 没有返回可解析的整理结果", 0, false)
        if (media.size >= 5 && plan.episodes.size < media.size / 3) {
            return AiApplyResult("AI 返回的集数过少（${plan.episodes.size}/${media.size}），已放弃应用", 0, false)
        }

        var coverUri: String? = null
        if (findCover && series.coverUrl.isNullOrBlank()) {
            coverUri = downloadCover(series)
        }
        val applied = repository.applyAiPlan(series, plan, coverUri)
        if (applied == 0) return AiApplyResult("AI 结果与文件不匹配，未修改", 0, false)

        val name = plan.seriesTitle ?: series.displayTitle
        val coverText = if (coverUri != null) "，已更新封面" else ""
        return AiApplyResult(
            "AI 已整理「$name」：$applied 集$coverText",
            applied,
            coverUri != null
        )
    }

    suspend fun fillCover(series: SeriesEntity): String? = downloadCover(series)

    private suspend fun downloadCover(series: SeriesEntity): String? = withContext(Dispatchers.IO) {
        val poster = bailian.findPoster(series.displayTitle, series.year) ?: return@withContext null
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
