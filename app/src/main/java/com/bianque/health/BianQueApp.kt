package com.bianque.health

import android.app.Application
import android.content.pm.ApplicationInfo
import com.bianque.health.base.security.PrivacyManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class BianQueApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 仅在 Debug 构建中种植 DebugTree，Release 构建不输出日志
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Timber.plant(Timber.DebugTree())
        }

        // 初始化隐私管理器
        PrivacyManager.init(this)
    }
}