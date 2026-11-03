package com.juying.tv

import android.app.Application
import android.content.Context
import com.juying.tv.data.AppGraph

class JuyingApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = AppGraph(this)
    }

    companion object {
        lateinit var instance: JuyingApp
            private set

        fun graphOf(context: Context): AppGraph = (context.applicationContext as JuyingApp).graph
    }
}
