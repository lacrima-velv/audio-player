@file:Suppress("unused")

package com.lacrima.audioplayer

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyApp: Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            //inject Android context
            androidContext(this@MyApp)
            modules(koinModule)
        }
    }
}