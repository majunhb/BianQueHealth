package com.bianque.health.base.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Base 模块 DI 声明。
 * HealthDatabase / HealthDao 的提供由 app 模块的 AppModule 负责（需要 Application Context）。
 */
@Module
@InstallIn(SingletonComponent::class)
object BaseModule {
    // 预留：base 模块内部需要的依赖提供
}