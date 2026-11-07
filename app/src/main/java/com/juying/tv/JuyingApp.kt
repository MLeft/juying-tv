package com.juying.tv

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.juying.tv.data.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JuyingApp : Application(), ImageLoaderFactory {
    lateinit var graph: AppGraph
        private set
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = AppGraph(this)
        ioScope.launch {
            graph.playbackResolver.pruneThumbCache()
            graph.playbackResolver.pruneCoverCache()
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.22)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(160L * 1024 * 1024)
                .build()
        }
        .allowRgb565(true)
        .crossfade(false)
        .build()

    companion object {
        lateinit var instance: JuyingApp
            private set

        fun graphOf(context: Context): AppGraph = (context.applicationContext as JuyingApp).graph
    }
}
