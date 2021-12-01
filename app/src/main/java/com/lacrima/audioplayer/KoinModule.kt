package com.lacrima.audioplayer

import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.lacrima.audioplayer.exoplayer.MusicService
import com.lacrima.audioplayer.exoplayer.MusicServiceConnection
import com.lacrima.audioplayer.ui.MainViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val koinModule = module {
    //single { MainViewModel(get()) }
    single { DefaultDataSource.Factory(androidContext()) }
    single { MusicServiceConnection(androidContext()) }
//    single { Glide.with(androidContext()).setDefaultRequestOptions(
//        RequestOptions()
//            .placeholder(R.drawable.ic_baseline_music_note_24)
//            .error(R.drawable.ic_baseline_music_note_24)
//            .diskCacheStrategy(DiskCacheStrategy.DATA)
//    ) }
}