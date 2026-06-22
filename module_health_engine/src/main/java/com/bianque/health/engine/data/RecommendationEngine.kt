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
                Recommendation("diet", "【宜食】山药、红枣、黄芪、党参、小米、糯米、鸡肉、牛肉、茯苓、莲子。" +
                    "【忌食】生冷瓜果、冰镇饮料、萝卜（破气）、浓茶。" +
                    "【药膳推荐】黄芪炖鸡汤：黄芪30g + 党参15g + 老母鸡1只，文火炖2小时。", 1),
                Recommendation("diet", "【茶饮推荐】红枣枸杞茶：红枣5枚 + 枸杞10g + 黄芪10g，沸水冲泡代茶饮。" +
                    "【日常调养】早餐宜食小米山药粥，午餐可适量食用牛肉或鸡肉，晚餐宜清淡。", 2),
                Recommendation("exercise", "选择太极拳、八段锦、五禽戏等柔缓运动，避免剧烈运动耗气。最佳运动时间：早晨7-9点（胃经当令）。每周3-4次，每次20-30分钟，以微微出汗为度。", 2),
                Recommendation("lifestyle", "保证充足睡眠，建议午休15-30分钟（子午觉）。避免过度劳累和长时间说话。注意腹部保暖，可艾灸气海、关元、足三里等穴位。", 1),
                Recommendation("emotional", "保持心态平和，避免过度思虑伤脾。可通过冥想、听轻音乐、书法等放松。多与积极乐观的朋友交流。", 2)
            )
            BodyType.YANG_DEFICIENCY -> listOf(
                Recommendation("diet", "【宜食】羊肉、韭菜、生姜、桂圆、核桃、虾仁、肉桂、小茴香、栗子。" +
                    "【忌食】生冷食物、西瓜、苦瓜、绿豆、螃蟹、冰镇饮料。" +
                    "【药膳推荐】当归生姜羊肉汤：羊肉500g + 当归20g + 生姜30g，文火炖1.5小时。", 1),
                Recommendation("diet", "【茶饮推荐】桂圆姜枣茶：桂圆10g + 生姜3片 + 红枣5枚 + 红糖适量，沸水冲泡。" +
                    "【日常调养】冬季宜多食温热食物，夏季避免贪凉。三餐定时，细嚼慢咽。", 2),
                Recommendation("exercise", "选择快走、慢跑、太极拳等有氧运动，以微出汗为度。最佳运动时间：上午9-11点（脾经当令）或下午3-5点（膀胱经当令）。注意保暖，避免受凉。", 2),
                Recommendation("lifestyle", "注意保暖，尤其腰腹部和足部。可艾灸关元、命门、足三里等穴位，每周2-3次。睡前温水泡脚15-20分钟。冬季可适当使用热水袋暖腹。", 1),
                Recommendation("emotional", "多参加社交活动，保持积极心态。阳光充足时多晒太阳，尤其背部（督脉所在）。避免长期独处和消极情绪。", 2)
            )
            BodyType.YIN_DEFICIENCY -> listOf(
                Recommendation("diet", "【宜食】百合、银耳、枸杞、鸭肉、甲鱼、黑芝麻、桑葚、蜂蜜、梨、莲藕。" +
                    "【忌食】辛辣食物（辣椒、花椒）、煎炸烧烤、羊肉、烈酒、咖啡。" +
                    "【药膳推荐】百合银耳莲子羹：百合30g + 银耳20g + 莲子15g + 冰糖适量，慢炖至粘稠。", 1),
                Recommendation("diet", "【茶饮推荐】枸杞菊花茶：枸杞15g + 菊花5g + 麦冬10g，沸水冲泡代茶饮。" +
                    "【日常调养】多食滋阴润燥食物，少食多餐。晚餐宜清淡，避免夜宵。", 2),
                Recommendation("exercise", "选择游泳、瑜伽、太极等柔缓运动，避免大量出汗伤阴。最佳运动时间：傍晚5-7点（肾经当令）。运动后及时补充水分。", 2),
                Recommendation("lifestyle", "保证充足睡眠，避免熬夜。睡前可温水泡脚15分钟，避免热水澡过久。保持居室湿度适宜，可使用加湿器。节制房事。", 1),
                Recommendation("emotional", "保持心态平和，避免急躁发怒伤肝阴。可通过书法、茶道、园艺修身养性。练习腹式深呼吸，帮助降心火。", 2)
            )
            BodyType.PHLEGM_DAMPNESS -> listOf(
                Recommendation("diet", "【宜食】薏米、冬瓜、赤小豆、山药、茯苓、白扁豆、陈皮、荷叶、海带。" +
                    "【忌食】油腻肥甘、甜食糕点、奶油奶酪、啤酒、冷饮。" +
                    "【药膳推荐】薏米赤小豆粥：薏米50g + 赤小豆30g + 茯苓15g + 陈皮5g，煮粥食用。", 1),
                Recommendation("diet", "【茶饮推荐】荷叶山楂茶：荷叶10g + 山楂10g + 陈皮5g + 决明子10g，沸水冲泡。" +
                    "【日常调养】饮食清淡，七分饱为宜。少食多餐，晚餐宜早宜少。", 2),
                Recommendation("exercise", "加强有氧运动：快走、游泳、骑行、健身操，每周至少4次，每次30-45分钟。运动强度以出汗为宜，帮助湿气排出。", 2),
                Recommendation("lifestyle", "保持居住环境干燥通风，避免潮湿。避免久坐，每小时起身活动5分钟。可拔罐、刮痧帮助祛湿。衣着宽松透气。", 1),
                Recommendation("emotional", "保持乐观开朗，避免思虑过度伤脾生湿。多参加户外活动，接触自然。培养音乐、舞蹈等兴趣爱好。", 2)
            )
            BodyType.DAMPNESS_HEAT -> listOf(
                Recommendation("diet", "【宜食】绿豆、苦瓜、芹菜、莲藕、薏米、冬瓜、丝瓜、西瓜、草莓。" +
                    "【忌食】辛辣食物、油炸食品、羊肉、狗肉、榴莲、芒果、烈酒。" +
                    "【药膳推荐】绿豆薏米汤：绿豆50g + 薏米30g + 冰糖适量，煮至豆烂。", 1),
                Recommendation("diet", "【茶饮推荐】菊花金银花茶：菊花10g + 金银花10g + 薄荷5g，沸水冲泡。" +
                    "【日常调养】饮食偏凉性，多食清热利湿食物。避免暴饮暴食和夜宵。", 2),
                Recommendation("exercise", "选择中等强度运动：跑步、游泳、球类运动，帮助排汗祛湿。最佳运动时间：早晨或傍晚凉爽时。运动后及时擦干汗液，更换干爽衣物。", 2),
                Recommendation("lifestyle", "保持皮肤清洁干爽，穿着透气棉质衣物。避免潮湿闷热环境和烈日暴晒。可适当使用艾草、藿香等熏香祛湿。", 1),
                Recommendation("emotional", "保持心情舒畅，避免急躁易怒。可通过音乐、冥想调节情绪。学会释放压力，避免长期压抑。", 2)
            )
            BodyType.BLOOD_STASIS -> listOf(
                Recommendation("diet", "【宜食】山楂、黑木耳、醋、玫瑰花、桃仁、红花、洋葱、大蒜、茄子。" +
                    "【忌食】寒凉食物、肥甘厚腻、冷饮、冰淇淋。" +
                    "【药膳推荐】山楂红糖水：山楂30g + 红糖15g + 生姜3片，煮水饮用。", 1),
                Recommendation("diet", "【茶饮推荐】玫瑰花茶：玫瑰花10g + 月季花5g + 红糖适量，沸水冲泡。" +
                    "【日常调养】适量食用醋和辛温食物（葱、姜、蒜），促进气血运行。", 2),
                Recommendation("exercise", "坚持有氧运动促进血液循环：快走、慢跑、舞蹈、健身操。运动前充分热身，避免运动损伤。每天至少30分钟，每周5次。", 2),
                Recommendation("lifestyle", "避免久坐久站，定时活动身体。注意保暖，尤其冬季。可适当泡脚（水温40-45℃），促进循环。可按摩血海、三阴交等穴位。", 1),
                Recommendation("emotional", "保持心情舒畅，避免忧郁压抑。多与人交流，培养兴趣爱好。练习深呼吸和冥想，帮助气血运行。", 2)
            )
            BodyType.QI_STAGNATION -> listOf(
                Recommendation("diet", "【宜食】柑橘、玫瑰花、佛手、薄荷、小麦、大枣、百合、莲子、黄花菜。" +
                    "【忌食】咖啡、浓茶、辛辣刺激食物、酒精。" +
                    "【药膳推荐】甘麦大枣汤：小麦30g + 大枣10枚 + 甘草6g，煮水饮用。", 1),
                Recommendation("diet", "【茶饮推荐】佛手玫瑰茶：佛手10g + 玫瑰花10g + 合欢花5g，沸水冲泡。" +
                    "【日常调养】多食芳香理气食物，少食多餐。避免空腹饮用刺激性饮品。", 2),
                Recommendation("exercise", "选择团体运动或户外活动：羽毛球、登山、跳舞、健身操。运动能疏解郁气，释放内啡肽。建议与朋友一起运动，增加社交互动。", 1),
                Recommendation("lifestyle", "建立规律作息，培养兴趣爱好。可按摩太冲、合谷、膻中等穴位帮助疏肝理气。睡前可听轻音乐、泡脚放松。", 1),
                Recommendation("emotional", "这是最重要的调养方向。多与亲友交流，必要时寻求心理咨询。练习冥想、瑜伽和深呼吸。学会表达情感，不压抑情绪。", 1)
            )
            BodyType.INHERENT_SPECIAL -> listOf(
                Recommendation("diet", "【宜食】富含维生素C的食物：猕猴桃、草莓、西兰花、番茄。优质蛋白：鱼肉、鸡肉、豆制品。" +
                    "【忌食】已知过敏食物、海鲜发物、辛辣刺激食物。" +
                    "【药膳推荐】玉屏风散茶：黄芪15g + 白术10g + 防风6g，煮水代茶饮。", 1),
                Recommendation("diet", "【茶饮推荐】紫苏叶茶：紫苏叶10g + 生姜3片 + 红枣5枚，沸水冲泡。" +
                    "【日常调养】饮食清淡均衡，避免食用不常吃的食物。逐步建立个人食物日记。", 2),
                Recommendation("exercise", "选择温和运动增强体质：散步、太极拳、八段锦。避免在花粉季节或空气污染严重时户外运动。运动前做好热身，循序渐进。", 2),
                Recommendation("lifestyle", "保持居住环境清洁，定期除螨除尘。注意季节变化，适时增减衣物。外出佩戴口罩。了解自身过敏原，做好预防措施。", 1),
                Recommendation("emotional", "保持平和心态，避免过度焦虑。了解自身体质特点，从容应对。培养积极乐观的生活态度。", 2)
            )
            else -> listOf( // BALANCED
                Recommendation("diet", "【宜食】饮食均衡，五谷为养（大米、小米、小麦、玉米、豆类），五果为助（苹果、香蕉、橙子、葡萄、猕猴桃），五畜为益（鱼肉、鸡肉、猪肉、牛肉适量），五菜为充（各类蔬菜）。" +
                    "【忌食】偏食、暴饮暴食、过度节食。" +
                    "【药膳推荐】四神汤：山药30g + 莲子20g + 茯苓15g + 芡实15g，煮粥或炖汤。", 1),
                Recommendation("diet", "【茶饮推荐】陈皮红枣茶：陈皮5g + 红枣3枚 + 枸杞10g，沸水冲泡。" +
                    "【四季调养】春养肝（多食绿色蔬菜）、夏养心（多食红色食物）、秋养肺（多食白色食物）、冬养肾（多食黑色食物）。", 2),
                Recommendation("exercise", "维持适度运动习惯：散步、太极拳、游泳、骑行，每周3-5次，每次30分钟。根据季节调整运动强度，春夏多动，秋冬适度。", 2),
                Recommendation("lifestyle", "保持规律作息，早睡早起（建议23:00前入睡，7:00前起床）。避免熬夜，保证7-8小时睡眠。保持居室整洁通风。", 1),
                Recommendation("emotional", "保持平和心态，适度社交。培养兴趣爱好，丰富精神生活。定期自我反思，保持身心平衡。", 2)
            )
        }
    }
}