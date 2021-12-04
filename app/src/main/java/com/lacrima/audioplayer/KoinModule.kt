package com.lacrima.audioplayer

import com.lacrima.audioplayer.exoplayer.AudioDatabaseProvider
import com.lacrima.audioplayer.exoplayer.CacheDataSourceFactory
import com.lacrima.audioplayer.exoplayer.MusicServiceConnection
import com.lacrima.audioplayer.remote.MusicDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val koinModule = module {
    single { MusicServiceConnection(androidContext()) }
    single { MusicDatabase() }
    single { AudioDatabaseProvider.getInstance(androidContext()) }
    single { CacheDataSourceFactory(androidContext()) }
}