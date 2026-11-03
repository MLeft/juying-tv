package com.juying.tv.data.source.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.juying.tv.data.source.ScannedFile
import com.juying.tv.data.source.ScannedKind
import com.juying.tv.data.source.ScanResult
import com.juying.tv.data.source.classify
import com.juying.tv.data.source.extensionOf
import com.juying.tv.data.source.mimeForExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext

class LocalScanner(private val context: Context) {

    suspend fun scan(treeUri: Uri, onProgress: (Int, String) -> Unit): ScanResult =
        withContext(Dispatchers.IO) {
            val root = try {
                DocumentFile.fromTreeUri(context, treeUri)
            } catch (e: Exception) {
                null
            } ?: throw IOException("无法打开目录，请重新授权该文件夹")

            if (!root.exists() || !root.canRead()) {
                throw IOException("目录授权已失效，请删除该媒体源后重新添加并授权")
            }

            val files = ArrayList<ScannedFile>()
            walk(root, "", files, 1, onProgress)
            ScanResult(files, root.name ?: "本地目录")
        }

    private suspend fun walk(
        dir: DocumentFile,
        parentPath: String,
        out: MutableList<ScannedFile>,
        depth: Int,
        onProgress: (Int, String) -> Unit
    ) {
        coroutineContext.ensureActive()
        if (depth > 9) return
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return
        }
        children.sortBy { it.name?.lowercase().orEmpty() }
        for (child in children) {
            coroutineContext.ensureActive()
            if (out.size >= 20000) throw IOException("文件数量超过 20000，请选择更具体的目录作为媒体源")
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val path = "$parentPath/$name"
            if (child.isDirectory) {
                if (name.equals("Android", ignoreCase = true) ||
                    name.equals("LOST.DIR", ignoreCase = true) ||
                    name.equals(".thumbnails", ignoreCase = true)
                ) continue
                walk(child, path, out, depth + 1, onProgress)
            } else {
                val kind = classify(name)
                if (kind == ScannedKind.VIDEO) {
                    out += ScannedFile(
                        remoteId = child.uri.toString(),
                        path = path,
                        parentPath = parentPath.ifBlank { "/" },
                        name = name,
                        size = child.length(),
                        lastModified = child.lastModified(),
                        mime = child.type ?: mimeForExtension(extensionOf(name)),
                        kind = kind,
                        playRef = child.uri.toString()
                    )
                    if (out.size % 20 == 0) onProgress(out.size, path)
                } else if (kind == ScannedKind.SUBTITLE) {
                    out += ScannedFile(
                        remoteId = child.uri.toString(),
                        path = path,
                        parentPath = parentPath.ifBlank { "/" },
                        name = name,
                        size = child.length(),
                        lastModified = child.lastModified(),
                        mime = child.type ?: mimeForExtension(extensionOf(name)),
                        kind = kind,
                        playRef = child.uri.toString()
                    )
                } else if (kind == ScannedKind.IMAGE && looksLikeCover(name)) {
                    out += ScannedFile(
                        remoteId = child.uri.toString(),
                        path = path,
                        parentPath = parentPath.ifBlank { "/" },
                        name = name,
                        size = child.length(),
                        lastModified = child.lastModified(),
                        mime = child.type ?: mimeForExtension(extensionOf(name)),
                        kind = kind,
                        playRef = child.uri.toString()
                    )
                }
            }
        }
        onProgress(out.size, parentPath.ifBlank { "/" })
    }

    private fun looksLikeCover(name: String): Boolean {
        val lower = name.lowercase()
        return listOf("poster", "folder", "cover", "fanart", "thumb", "banner").any { lower.contains(it) }
    }
}
