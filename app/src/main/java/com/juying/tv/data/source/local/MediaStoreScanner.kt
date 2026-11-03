package com.juying.tv.data.source.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.juying.tv.data.source.ScannedFile
import com.juying.tv.data.source.ScannedKind
import com.juying.tv.data.source.ScanResult
import com.juying.tv.data.source.classify
import com.juying.tv.data.source.extensionOf
import com.juying.tv.data.source.mimeForExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

class MediaStoreScanner(private val context: Context) {

    suspend fun scan(minDurationSec: Int, onProgress: (Int, String) -> Unit): ScanResult =
        withContext(Dispatchers.IO) {
            val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
            else Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                throw IOException("缺少存储读权限，请在系统设置中允许后重试")
            }

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATA
            )
            val minDurationMs = minDurationSec.coerceAtLeast(0) * 1000L
            val files = ArrayList<ScannedFile>()
            val parentDirs = HashSet<String>()

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    if (files.size >= 20000) throw IOException("视频数量超过 20000，请改用指定文件夹作为媒体源")
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: continue
                    val size = cursor.getLong(sizeIndex)
                    val duration = cursor.getLong(durationIndex)
                    if (duration in 1 until minDurationMs) continue
                    if (size in 1 until 1024 * 1024) continue
                    val data = cursor.getString(dataIndex) ?: continue
                    val parent = File(data).parent ?: continue
                    val mime = cursor.getString(mimeIndex)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    parentDirs += parent
                    files += ScannedFile(
                        remoteId = data,
                        path = data,
                        parentPath = parent,
                        name = name,
                        size = size,
                        lastModified = cursor.getLong(modifiedIndex) * 1000L,
                        mime = mime,
                        kind = ScannedKind.VIDEO,
                        playRef = uri.toString()
                    )
                    if (files.size % 30 == 0) onProgress(files.size, parent)
                }
            }

            parentDirs.forEach { dir ->
                coroutineContext.ensureActive()
                val directory = File(dir)
                val children = try {
                    directory.listFiles()
                } catch (e: Exception) {
                    null
                } ?: return@forEach
                children.forEach { file ->
                    val name = file.name
                    val kind = classify(name)
                    if (kind == ScannedKind.SUBTITLE || (kind == ScannedKind.IMAGE && looksLikeCover(name))) {
                        files += ScannedFile(
                            remoteId = file.absolutePath,
                            path = file.absolutePath,
                            parentPath = dir,
                            name = name,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            mime = mimeForExtension(extensionOf(name)),
                            kind = kind,
                            playRef = Uri.fromFile(file).toString()
                        )
                    }
                }
            }

            onProgress(files.size, "扫描完成")
            ScanResult(files, "本机与 U 盘")
        }

    private fun looksLikeCover(name: String): Boolean {
        val lower = name.lowercase()
        return listOf("poster", "folder", "cover", "fanart", "thumb", "banner").any { lower.contains(it) }
    }
}
