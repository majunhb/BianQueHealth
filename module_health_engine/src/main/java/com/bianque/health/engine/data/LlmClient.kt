package com.bianque.health.engine.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通义千问 (DashScope) REST API 客户端。
 *
 * 使用 OkHttp 直接调用阿里云百炼 API，无需额外 SDK。
 * API Key 通过构造函数注入，建议通过后端代理获取，避免客户端硬编码。
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
        .build()

    /**
     * 发送聊天请求到通义千问。
     *
     * @param apiKey       DashScope API Key
     * @param model        模型名称，默认 qwen-plus
     * @param systemPrompt 系统提示词（角色设定）
     * @param userMessage  用户消息（诊断数据）
     * @param temperature  温度参数，默认 0.3（医疗场景需要低温度以保证一致性）
     * @return 模型回复文本
     * @throws LlmException 当 API 调用失败时
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

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw LlmException("Empty response body")

            if (!response.isSuccessful) {
                val errorJson = try {
                    JSONObject(body)
                } catch (_: Exception) {
                    JSONObject()
                }
                val errorMsg = errorJson.optString("message",
                    errorJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}")
                Timber.e("LlmClient: API error (${response.code}): $errorMsg")
                throw LlmException(errorMsg)
            }

            val json = JSONObject(body)
            val output = json.getJSONObject("output")
            val choices = output.getJSONArray("choices")
            if (choices.length() == 0) {
                throw LlmException("No choices in response")
            }
            val message = choices.getJSONObject(0).getJSONObject("message")
            val content = message.getString("content")

            // 记录 Token 用量
            if (json.has("usage")) {
                val usage = json.getJSONObject("usage")
                Timber.d("LlmClient: tokens - input=${usage.optInt("input_tokens")}, output=${usage.optInt("output_tokens")}")
            }

            content
        } catch (e: LlmException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "LlmClient: network error")
            throw LlmException("网络请求失败: ${e.message}")
        }
    }
}

/**
 * LLM 调用异常。
 */
class LlmException(message: String) : Exception(message)