package com.bianque.health.content.data.rag

import com.bianque.health.content.domain.model.RagContext
import com.bianque.health.engine.data.ApiKeyProvider
import com.bianque.health.engine.data.LlmClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一通义千问服务 — 全APP所有LLM调用的唯一入口。
 *
 * 封装 [LlmClient] + [ApiKeyProvider] + [RagPromptTemplates]，
 * 所有模块（面诊/舌诊/脉诊/血压/内容/推荐）都通过此服务调用通义千问。
 *
 * 使用方式：
 * ```
 * val context = RagPromptTemplates.dietForConstitution("湿热体质", docs)
 * val result = qwenService.ask(context)
 * ```
 */
@Singleton
class QwenService @Inject constructor(
    private val llmClient: LlmClient,
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val DEFAULT_MODEL = "qwen-plus"
        private const val DEFAULT_TEMPERATURE = 0.3  // 医疗场景低温度
        private const val CREATIVE_TEMPERATURE = 0.7  // 科普/润色场景较高温度
    }

    /**
     * 核心方法：基于 RAG 上下文向通义千问提问。
     *
     * 自动从 [ApiKeyProvider] 获取 API Key，若 Key 为空则降级返回本地知识库内容。
     */
    suspend fun ask(context: RagContext, model: String = DEFAULT_MODEL): String {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey == null) {
            Timber.w("QwenService: API Key not available, returning local knowledge base fallback")
            return buildFallbackResponse(context)
        }

        // 拼接检索到的知识库文档作为上下文
        val knowledgeContent = if (context.retrievedDocs.isNotEmpty()) {
            context.retrievedDocs.joinToString("\n\n---\n\n") { doc ->
                "[来源：${doc.source}] [已验证：${if (doc.verified) "是" else "否"}]\n${doc.content}"
            }
        } else {
            "（暂无匹配的知识库内容，请诚实告知用户并建议咨询专业医师）"
        }

        val userMessage = """
## 知识库检索结果
$knowledgeContent

## 用户问题
${context.query}
        """.trimIndent()

        return try {
            llmClient.chat(
                apiKey = apiKey,
                model = model,
                systemPrompt = context.systemPrompt,
                userMessage = userMessage,
                temperature = DEFAULT_TEMPERATURE
            )
        } catch (e: Exception) {
            Timber.e(e, "QwenService: LLM call failed, falling back to cached response")
            buildFallbackResponse(context)
        }
    }

    /**
     * 通用问答（不走 RAG，适用于简单翻译/润色场景）。
     */
    suspend fun askSimple(
        systemPrompt: String,
        userMessage: String,
        model: String = DEFAULT_MODEL,
        temperature: Double = DEFAULT_TEMPERATURE
    ): String {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey == null) {
            return "大模型服务暂不可用，请检查 API Key 配置。"
        }
        return llmClient.chat(apiKey, model, systemPrompt, userMessage, temperature)
    }

    /**
     * 科普润色（较高温度，增加创意性）。
     */
    suspend fun polishArticle(
        context: RagContext,
        model: String = DEFAULT_MODEL
    ): String {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey == null) {
            return buildFallbackResponse(context)
        }

        val knowledgeContent = context.retrievedDocs.joinToString("\n\n") { it.content }
        val userMessage = """
## 源材料
$knowledgeContent

## 任务
${context.query}
        """.trimIndent()

        return try {
            llmClient.chat(
                apiKey = apiKey,
                model = model,
                systemPrompt = context.systemPrompt,
                userMessage = userMessage,
                temperature = CREATIVE_TEMPERATURE
            )
        } catch (e: Exception) {
            Timber.e(e, "QwenService: polish failed")
            buildFallbackResponse(context)
        }
    }

    /**
     * 检查大模型服务是否可用。
     */
    fun isAvailable(): Boolean = apiKeyProvider.getApiKey() != null

    // ─── 降级处理 ──────────────────────────────────────────────

    private fun buildFallbackResponse(context: RagContext): String {
        if (context.retrievedDocs.isEmpty()) {
            return "暂无相关数据，建议咨询专业中医师获取个性化建议。"
        }
        return context.retrievedDocs.joinToString("\n\n") { doc ->
            "【${doc.source}】\n${doc.content}"
        } + "\n\n---\n⚠️ 以上内容来自本地知识库，大模型服务暂不可用。"
    }
}