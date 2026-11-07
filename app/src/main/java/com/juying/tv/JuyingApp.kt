package com.juying.tv

import android.app.Application
import android.content.Context
import com.juying.tv.data.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JuyingApp : Application() {
    lateinit var graph: AppGraph
        private set
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = AppGraph(this)
        ioScope.launch { graph.playbackResolver.pruneThumbCache() }
    }

    companion object {
        lateinit var instance: JuyingApp
            private set

        fun graphOf(context: Context): AppGraph = (context.applicationContext as JuyingApp).graph
    }
}
