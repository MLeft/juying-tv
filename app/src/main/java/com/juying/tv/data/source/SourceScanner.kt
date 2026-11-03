package com.juying.tv.data.source

import android.content.Context
import android.net.Uri
import com.juying.tv.data.db.SourceEntity
import com.juying.tv.data.db.SourceType
import com.juying.tv.data.source.local.LocalScanner
import com.juying.tv.data.source.local.MediaStoreScanner
import com.juying.tv.data.source.quark.QuarkClient
import com.juying.tv.data.source.webdav.WebDavClient
import java.io.IOException

class SourceScanner(
    context: Context,
    private val webDav: WebDavClient,
    private val quark: QuarkClient
) {
    private val localScanner = LocalScanner(context)
    private val mediaStoreScanner = MediaStoreScanner(context)

    suspend fun scan(source: SourceEntity, onProgress: (Int, String) -> Unit): ScanResult {
        return when (source.type) {
            SourceType.LOCAL -> {
                val config = SourceConfigs.decodeLocal(source.config)
                val uri = runCatching { Uri.parse(config.treeUri) }.getOrNull()
                    ?: throw IOException("本地目录配置无效，请重新添加")
                localScanner.scan(uri, onProgress)
            }
            SourceType.MEDIASTORE -> {
                val config = SourceConfigs.decodeMediaStore(source.config)
                mediaStoreScanner.scan(config.minDurationSec, onProgress)
            }
            SourceType.WEBDAV -> {
                val config = SourceConfigs.decodeWebDav(source.config)
                webDav.scan(config, onProgress)
            }
            SourceType.QUARK -> {
                val config = SourceConfigs.decodeQuark(source.config)
                quark.scan(config, onProgress)
            }
            else -> throw IOException("不支持的媒体源类型：${source.type}")
        }
    }
}
