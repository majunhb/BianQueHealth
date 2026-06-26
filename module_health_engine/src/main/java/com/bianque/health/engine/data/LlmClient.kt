package com.bianque.health.engine.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通义千问 (DashScope) REST API 客户端。
 *
 * 使用 OkHttp 直接调用阿里云百炼 API，无需额外 SDK。
 * 内置指数退避重试机制，处理网络抖动和服务端限流。
 *
 * 文档: https://help.aliyun.com/zh/model-studio/qwen-api-via-dashscope
 */
@Singleton
class LlmClient @Inject constructor() {

    private val endpoint =
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY_MS = 1000L
    }

    /**
     * 发送聊天请求到通义千问。
     *
     * @param apiKey       DashScope API Key
     * @param model        模型名称，默认 qwen-plus
     * @param systemPrompt 系统提示词（角色设定）
     * @param userMessage  用户消息（诊断数据）
     * @param temperature  温度参数，默认 0.3（医疗场景需要低温度以保证一致性）
     * @return 模型回复文本
     * @throws LlmException 当 API 调用失败且重试已耗尽时
     */
    suspend fun chat(
        apiKey: String,
        model: String = "qwen-plus",
        systemPrompt: String,
        userMessage: String,
        temperature: Double = 0.3
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            put("input", JSONObject().apply {
                put("messages", messages)
            })
            put("parameters", JSONObject().apply {
                put("result_format", "message")
                put("temperature", temperature)
                put("max_tokens", 2000)
            })
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        executeWithRetry(request)
    }

    /**
     * 带指数退避重试的请求执行。
     * 可重试的错误：网络超时、DNS 解析失败、服务端 5xx/429。
     */
    private suspend fun executeWithRetry(request: Request): String {
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw LlmException("Empty response body")

                if (response.isSuccessful) {
                    return parseResponse(body)
                }

                val errorMsg = parseErrorBody(body, response.code)

                // 502/503/504/429：服务端临时故障或限流，可重试
                if (isRetryableHttpError(response.code)) {
                    Timber.w("LlmClient: retryable error (attempt $attempt/$MAX_RETRIES): HTTP ${response.code} $errorMsg")
                    if (attempt < MAX_RETRIES) {
                        delay(computeDelay(attempt))
                        continue
                    }
                }

                throw LlmException("HTTP ${response.code}: $errorMsg")
            } catch (e: LlmException) {
                throw e
            } catch (e: SocketTimeoutException) {
                lastException = e
                Timber.w(e, "LlmClient: timeout (attempt $attempt/$MAX_RETRIES)")
                if (attempt < MAX_RETRIES) {
                    delay(computeDelay(attempt))
                    continue
                }
            } catch (e: UnknownHostException) {
                lastException = e
                Timber.w(e, "LlmClient: DNS resolution failed (attempt $attempt/$MAX_RETRIES)")
                if (attempt < MAX_RETRIES) {
                    delay(computeDelay(attempt))
                    continue
                }
            } catch (e: IOException) {
                lastException = e
                Timber.w(e, "LlmClient: IO error (attempt $attempt/$MAX_RETRIES)")
                if (attempt < MAX_RETRIES) {
                    delay(computeDelay(attempt))
                    continue
                }
            }
        }

        throw LlmException(
            "网络请求失败（已重试 $MAX_RETRIES 次）: ${lastException?.message ?: "未知错误"}"
        )
    }

    private fun parseResponse(body: String): String {
        val json = JSONObject(body)
        val output = json.getJSONObject("output")
        val choices = output.getJSONArray("choices")
        if (choices.length() == 0) {
            throw LlmException("No choices in response")
        }
        val message = choices.getJSONObject(0).getJSONObject("message")
        val content = message.getString("content")

        if (json.has("usage")) {
            val usage = json.getJSONObject("usage")
            Timber.d("LlmClient: tokens - input=${usage.optInt("input_tokens")}, output=${usage.optInt("output_tokens")}")
        }

        return content
    }

    private fun parseErrorBody(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            json.optString("message",
                json.optJSONObject("error")?.optString("message") ?: "HTTP $code")
        } catch (_: Exception) {
            "HTTP $code"
        }
    }

    private fun isRetryableHttpError(code: Int): Boolean {
        return code in listOf(429, 502, 503, 504)
    }

    /**
     * 指数退避延迟：1s → 2s → 4s，加上随机抖动避免惊群效应。
     */
    private fun computeDelay(attempt: Int): Long {
        val exponential = BASE_DELAY_MS * (1L shl (attempt - 1))
        val jitter = (Math.random() * 500).toLong()
        return exponential + jitter
    }
}

/**
 * LLM 调用异常。
 */
class LlmException(message: String) : Exception(message)