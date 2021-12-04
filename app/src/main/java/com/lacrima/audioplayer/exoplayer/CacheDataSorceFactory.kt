package com.lacrima.audioplayer.exoplayer

import android.content.Context
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Util
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

const val MAX_FILE_SIZE = 31457280L
const val MAX_CACHE_SIZE = 314572800L

class CacheDataSourceFactory(context: Context) : DataSource.Factory, KoinComponent {

    private val audioDatabaseProvider: DatabaseProvider by inject()

    private val defaultDataSourceFactory: DefaultDataSource.Factory
    private val fileDataSource: DataSource = FileDataSource()

    private val simpleCache: SimpleCache by lazy {
        val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
        SimpleCache(File(context.cacheDir, "media"), evictor, audioDatabaseProvider)
    }

    init {
        val userAgent = Util.getUserAgent(context, context.packageName)

        defaultDataSourceFactory = DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
        )
    }

    override fun createDataSource(): DataSource {
        return CacheDataSource(
            simpleCache,
            defaultDataSourceFactory.createDataSource(),
            fileDataSource,
            CacheDataSink(simpleCache, MAX_FILE_SIZE),
            CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            null
        )
    }
}