package com.bianque.health.engine.data

import com.bianque.health.engine.domain.model.BodyType
import com.bianque.health.engine.domain.model.Recommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 个性化健康建议生成器 — 基于通义千问 LLM。
 *
 * 根据用户体质类型和诊断数据，生成饮食、运动、作息、情绪
 * 四个维度的个性化建议。
 */
@Singleton
class RecommendationEngine @Inject constructor(
    private val llmClient: LlmClient,
    private val apiKeyProvider: ApiKeyProvider
) {

    private companion object {
        val SYSTEM_PROMPT = """
你是一位资深中医养生顾问，精通《黄帝内经》养生理论和现代健康管理。

你的任务是根据用户的体质类型和诊断数据，生成个性化的健康建议。

建议需覆盖以下四个维度：
1. diet（饮食调理）：具体食材推荐和禁忌
2. exercise（运动建议）：适合的运动类型、频率和强度
3. lifestyle（作息调整）：睡眠、作息、生活习惯建议
4. emotional（情绪管理）：心理调适和情志养生建议

要求：
- 每条建议 50-100 字，具体可操作
- 基于中医理论，融入现代医学知识
- 根据体质类型给出针对性建议，而非泛泛而谈

请严格按照以下 JSON 格式回复，不要输出任何其他内容：
{
  "recommendations": [
    {
      "category": "diet",
      "content": "饮食建议内容",
      "priority": 1
    },
    {
      "category": "exercise",
      "content": "运动建议内容",
      "priority": 2
    },
    {
      "category": "lifestyle",
      "content": "作息建议内容",
      "priority": 1
    },
    {
      "category": "emotional",
      "content": "情绪建议内容",
      "priority": 2
    }
  ],
  "riskAssessment": ["风险提示1", "风险提示2"]
}

priority 取值：1=重要，2=建议，3=参考
        """.trimIndent()
    }

    /**
     * 根据体质类型和诊断数据生成个性化建议。
     *
     * @param bodyType      体质类型
     * @param diagnosisData 诊断摘要数据（面诊、舌诊、脉诊、血压摘要）
     * @return 建议列表
     */
    suspend fun generate(
        bodyType: BodyType,
        diagnosisData: Map<String, Any>
    ): Pair<List<Recommendation>, List<String>> = withContext(Dispatchers.Default) {
        val apiKey = apiKeyProvider.getApiKey()

        if (apiKey == null) {
            Timber.w("RecommendationEngine: API Key not available, falling back to template")
            return@withContext Pair(fallbackRecommendations(bodyType), emptyList())
        }

        try {
            val userMessage = buildPrompt(bodyType, diagnosisData)
            Timber.d("RecommendationEngine: sending to LLM for ${bodyType.displayName}...")

            val response = llmClient.chat(
                apiKey = apiKey,
                model = "qwen-plus",
                systemPrompt = SYSTEM_PROMPT,
                userMessage = userMessage,
                temperature = 0.5
            )

            parseResponse(response)
        } catch (e: LlmException) {
            Timber.e(e, "RecommendationEngine: LLM call failed, falling back to template")
            Pair(fallbackRecommendations(bodyType), emptyList())
        }
    }

    private fun buildPrompt(bodyType: BodyType, diagnosisData: Map<String, Any>): String {
        return buildString {
            appendLine("请根据以下信息生成个性化健康建议：")
            appendLine()
            appendLine("体质类型：${bodyType.displayName}（${bodyType.description}）")
            appendLine()
            appendLine("诊断摘要：")
            diagnosisData.forEach { (key, value) ->
                appendLine("- $key：$value")
            }
        }
    }

    private fun parseResponse(response: String): Pair<List<Recommendation>, List<String>> {
        return try {
            val jsonStr = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = org.json.JSONObject(jsonStr)
            val recommendationsArray = json.getJSONArray("recommendations")
            val recommendations = (0 until recommendationsArray.length()).map { i ->
                val item = recommendationsArray.getJSONObject(i)
                Recommendation(
                    category = item.getString("category"),
                    content = item.getString("content"),
                    priority = item.getInt("priority")
                )
            }

            val risks = if (json.has("riskAssessment")) {
                val riskArray = json.getJSONArray("riskAssessment")
                (0 until riskArray.length()).map { riskArray.getString(it) }
            } else {
                emptyList()
            }

            Timber.d("RecommendationEngine: generated ${recommendations.size} recommendations, ${risks.size} risks")
            Pair(recommendations, risks)
        } catch (e: Exception) {
            Timber.w(e, "RecommendationEngine: failed to parse LLM response")
            Pair(fallbackRecommendations(BodyType.BALANCED), emptyList())
        }
    }

    /**
     * 降级方案：基于体质类型的模板化建议。
     */
    private fun fallbackRecommendations(bodyType: BodyType): List<Recommendation> {
        return when (bodyType) {
            BodyType.QI_DEFICIENCY -> listOf(
                Recommendation("diet", "多食补气食物：山药、红枣、黄芪炖鸡、小米粥。避免生冷寒凉食物。", 1),
                Recommendation("exercise", "选择太极拳、八段锦等柔缓运动，避免剧烈运动耗气。每周3-4次，每次20-30分钟。", 2),
                Recommendation("lifestyle", "保证充足睡眠，建议午休15-30分钟。避免过度劳累和长时间说话。", 1),
                Recommendation("emotional", "保持心态平和，避免过度思虑。可通过冥想、听音乐放松。", 2)
            )
            BodyType.YANG_DEFICIENCY -> listOf(
                Recommendation("diet", "多食温阳食物：羊肉、韭菜、生姜、桂圆。避免生冷和寒性食物。", 1),
                Recommendation("exercise", "选择快走、慢跑等有氧运动，以微出汗为度。注意保暖，避免受凉。", 2),
                Recommendation("lifestyle", "注意保暖，尤其腰腹部和足部。可艾灸关元、足三里等穴位。", 1),
                Recommendation("emotional", "多参加社交活动，保持积极心态。阳光充足时多晒太阳。", 2)
            )
            BodyType.YIN_DEFICIENCY -> listOf(
                Recommendation("diet", "多食滋阴食物：百合、银耳、枸杞、鸭肉。避免辛辣、煎炸食物。", 1),
                Recommendation("exercise", "选择游泳、瑜伽等柔缓运动，避免大量出汗。建议傍晚运动。", 2),
                Recommendation("lifestyle", "保证充足睡眠，避免熬夜。睡前可温水泡脚，避免热水澡过久。", 1),
                Recommendation("emotional", "保持心态平和，避免急躁发怒。可通过书法、茶道修身养性。", 2)
            )
            BodyType.PHLEGM_DAMPNESS -> listOf(
                Recommendation("diet", "饮食清淡，多食薏米、冬瓜、赤小豆、山药。少食油腻、甜食。", 1),
                Recommendation("exercise", "加强有氧运动：快走、游泳、骑行，每周至少4次，每次30分钟以上。", 2),
                Recommendation("lifestyle", "保持居住环境干燥通风。避免久坐，每小时起身活动。", 1),
                Recommendation("emotional", "保持乐观开朗，避免思虑过度。多参加户外活动。", 2)
            )
            BodyType.DAMPNESS_HEAT -> listOf(
                Recommendation("diet", "清淡饮食，多食绿豆、苦瓜、芹菜、莲藕。避免辛辣、油腻、甜食。", 1),
                Recommendation("exercise", "选择中等强度运动：跑步、游泳、球类，帮助排汗祛湿。", 2),
                Recommendation("lifestyle", "保持皮肤清洁，穿着透气衣物。避免潮湿环境和烈日暴晒。", 1),
                Recommendation("emotional", "保持心情舒畅，避免急躁易怒。可通过音乐、冥想调节情绪。", 2)
            )
            BodyType.BLOOD_STASIS -> listOf(
                Recommendation("diet", "多食活血化瘀食物：山楂、黑木耳、醋、玫瑰花茶。避免寒凉食物。", 1),
                Recommendation("exercise", "坚持有氧运动促进血液循环：快走、慢跑、舞蹈。运动前充分热身。", 2),
                Recommendation("lifestyle", "避免久坐久站，定时活动。注意保暖，可适当泡脚促进循环。", 1),
                Recommendation("emotional", "保持心情舒畅，避免忧郁压抑。多与人交流，培养兴趣爱好。", 2)
            )
            BodyType.QI_STAGNATION -> listOf(
                Recommendation("diet", "多食理气解郁食物：柑橘、玫瑰花茶、佛手、薄荷。避免咖啡和浓茶。", 1),
                Recommendation("exercise", "选择团体运动或户外活动：羽毛球、登山、跳舞。运动能疏解郁气。", 2),
                Recommendation("lifestyle", "建立规律作息，培养兴趣爱好。可尝试按摩太冲、合谷等穴位。", 1),
                Recommendation("emotional", "这是最重要的调养方向。多与亲友交流，必要时寻求心理咨询。练习冥想和深呼吸。", 1)
            )
            BodyType.INHERENT_SPECIAL -> listOf(
                Recommendation("diet", "饮食清淡均衡，避免已知过敏食物。多食富含维生素C的食物增强免疫力。", 1),
                Recommendation("exercise", "选择温和运动增强体质：散步、太极拳。避免在花粉季节户外运动。", 2),
                Recommendation("lifestyle", "保持居住环境清洁，定期除螨。注意季节变化，适时增减衣物。", 1),
                Recommendation("emotional", "保持平和心态，避免过度焦虑。了解自身过敏原，做好预防。", 2)
            )
            else -> listOf( // BALANCED
                Recommendation("diet", "饮食均衡，荤素搭配，五谷为养，五果为助。保持三餐规律，食不过饱。", 1),
                Recommendation("exercise", "维持适度运动习惯：散步、太极拳、游泳，每周3-5次，每次30分钟。", 2),
                Recommendation("lifestyle", "保持规律作息，早睡早起。避免熬夜，保证7-8小时睡眠。", 1),
                Recommendation("emotional", "保持平和心态，适度社交。培养兴趣爱好，丰富精神生活。", 2)
            )
        }
    }
}