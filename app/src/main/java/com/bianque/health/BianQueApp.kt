package com.bianque.health

import android.app.Application
import com.bianque.health.BuildConfig
import com.bianque.health.base.security.PrivacyManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class BianQueApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 仅在 Debug 构建中种植 DebugTree，Release 构建不输出日志
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 初始化隐私管理器
        PrivacyManager.init(this)
    }
}