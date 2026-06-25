package com.bianque.health.content.di

import com.bianque.health.content.data.QwenContentRepository
import com.bianque.health.content.domain.repository.ContentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 内容模块 Hilt DI 模块。
 *
 * 统一注入通义千问驱动的内容仓库，全APP所有辅助功能模块
 * 都通过 [ContentRepository] 获取内容，底层统一走 [QwenService]。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ContentModule {

    @Binds
    @Singleton
    abstract fun bindContentRepository(
        impl: QwenContentRepository
    ): ContentRepository
}