package com.bianque.health.engine.data

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API Key 管理器。
 *
 * 生产环境应将 API Key 存储在后端服务，通过代理转发请求。
 * 开发阶段可使用 BuildConfig 或 EncryptedSharedPreferences 存储。
 */
@Singleton
class ApiKeyProvider @Inject constructor() {

    /**
     * 获取 DashScope API Key。
     * 开发阶段从 BuildConfig 或环境变量获取；生产环境由后端代理提供。
     * 调用方需处理 key 为空的降级逻辑。
     */
    fun getApiKey(): String? {
        // 开发阶段：通过系统属性注入（CI 环境变量 DASHSCOPE_API_KEY）
        return System.getProperty("DASHSCOPE_API_KEY")
            ?: System.getenv("DASHSCOPE_API_KEY")
            ?: run {
                Timber.w("ApiKeyProvider: DASHSCOPE_API_KEY not set, LLM features disabled")
                null
            }
    }
}