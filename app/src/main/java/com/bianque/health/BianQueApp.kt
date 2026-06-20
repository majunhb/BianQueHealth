package com.bianque.health

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class BianQueApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (com.bianque.health.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}