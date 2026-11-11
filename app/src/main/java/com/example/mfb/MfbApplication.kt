package com.example.mfb

import android.app.Application
import com.example.mfb.core.di.AppContainer
import com.example.mfb.core.di.DefaultAppContainer

class MfbApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
