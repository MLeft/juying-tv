package com.juying.tv.data

import android.content.Context
import com.juying.tv.data.db.AppDatabase
import com.juying.tv.data.prefs.SettingsStore
import com.juying.tv.data.repo.LibraryRepository
import com.juying.tv.data.repo.PlaybackResolver
import com.juying.tv.data.ai.AiAgent
import com.juying.tv.data.ai.BailianClient
import com.juying.tv.data.scrape.DoubanClient
import com.juying.tv.data.scrape.ScrapeService
import com.juying.tv.data.scrape.TmdbClient
import com.juying.tv.data.scrape.TvmazeClient
import com.juying.tv.data.source.SourceScanner
import com.juying.tv.data.source.quark.QuarkClient
import com.juying.tv.data.source.webdav.WebDavClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.get(appContext)
    val settings = SettingsStore(appContext)

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val webDav = WebDavClient(okHttp)
    val quark = QuarkClient(okHttp)
    val douban = DoubanClient(okHttp)
    val tvmaze = TvmazeClient(okHttp)
    val tmdb = TmdbClient(okHttp)
    val scrape = ScrapeService(appContext, douban, tvmaze, tmdb)
    val bailian = BailianClient(okHttp, settings)
    val scanner = SourceScanner(appContext, webDav, quark)
    val repository = LibraryRepository(appContext, database, scanner)
    val aiAgent = AiAgent(appContext, database, repository, bailian)
    val playbackResolver = PlaybackResolver(appContext, database, webDav, quark)
}
