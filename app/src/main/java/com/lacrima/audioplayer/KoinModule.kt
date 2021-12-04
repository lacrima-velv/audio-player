package com.lacrima.audioplayer

import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.lacrima.audioplayer.exoplayer.AudioDatabaseProvider
import com.lacrima.audioplayer.exoplayer.CacheDataSourceFactory
import com.lacrima.audioplayer.exoplayer.MusicServiceConnection
import com.lacrima.audioplayer.remote.MusicDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val koinModule = module {
    single { DefaultDataSource.Factory(androidContext()) }
    single { CacheDataSource.Factory() }
    single { MusicServiceConnection(androidContext()) }
    single { MusicDatabase() }
    single { AudioDatabaseProvider.getInstance(androidContext()) }
    single { CacheDataSourceFactory(androidContext()) }
}