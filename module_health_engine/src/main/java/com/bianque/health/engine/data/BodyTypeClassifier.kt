package com.bianque.health.engine.data

import com.bianque.health.bp.domain.model.BloodPressureResult
import com.bianque.health.engine.domain.model.BodyType
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 中医体质辨证分类器 — 双智能体架构 (Dual-Agent)。
 *
 * 基于设计文档的辨证推理引擎：
 * - Agent A (初筛): 规则引擎 + 概率模型，快速初筛候选体质
 * - Agent B (精辨): LLM 深度推理，从候选中确定最终体质
 *
 * 可用的89条中医辨证规则 (rule_base.json) + 知识图谱 (Neo4j) 策略
 */
@Singleton
class BodyTypeClassifier @Inject constructor(
    private val llmClient: LlmClient,
    private val apiKeyProvider: ApiKeyProvider
) {

    // ==================== Agent A: 规则引擎初筛 ====================

    /**
     * 规则引擎 — 89条中医辨证规则。
     * 每条规则：条件匹配 → 体质类型 + 置信度
     */
    private data class Rule(val conditions: List<Condition>, val bodyType: BodyType, val confidence: Float)
    private data class Condition(val field: String, val operator: String, val value: String)

    private val rules: List<Rule> = buildRules()

    private fun buildRules(): List<Rule> = listOf(
        // ==================== 湿热质 (DAMPNESS_HEAT) ====================
        Rule(listOf(Condition("tongueColor", "contains", "红"), Condition("coatingColor", "contains", "黄")), BodyType.DAMPNESS_HEAT, 0.85f),
        Rule(listOf(Condition("tongueColor", "contains", "红"), Condition("coatingThickness", "contains", "腻")), BodyType.DAMPNESS_HEAT, 0.80f),
        Rule(listOf(Condition("complexion", "contains", "偏红"), Condition("coatingColor", "contains", "黄")), BodyType.DAMPNESS_HEAT, 0.75f),
        Rule(listOf(Condition("tongueColor", "contains", "红"), Condition("coatingColor", "contains", "黄"), Condition("coatingThickness", "contains", "腻")), BodyType.DAMPNESS_HEAT, 0.90f),
        Rule(listOf(Condition("coatingColor", "contains", "黄"), Condition("tongueBody", "contains", "老")), BodyType.DAMPNESS_HEAT, 0.75f),
        Rule(listOf(Condition("complexion", "contains", "偏红"), Condition("tongueColor", "contains", "红"), Condition("coatingThickness", "contains", "腻")), BodyType.DAMPNESS_HEAT, 0.80f),
        Rule(listOf(Condition("pulseType", "contains", "滑"), Condition("coatingColor", "contains", "黄")), BodyType.DAMPNESS_HEAT, 0.80f),

        // ==================== 痰湿质 (PHLEGM_DAMPNESS) ====================
        Rule(listOf(Condition("tongueShape", "contains", "胖大"), Condition("coatingThickness", "contains", "腻")), BodyType.PHLEGM_DAMPNESS, 0.85f),
        Rule(listOf(Condition("coatingThickness", "contains", "厚"), Condition("tongueShape", "contains", "胖大")), BodyType.PHLEGM_DAMPNESS, 0.80f),
        Rule(listOf(Condition("tongueShape", "contains", "胖大"), Condition("pulseType", "contains", "滑")), BodyType.PHLEGM_DAMPNESS, 0.80f),
        Rule(listOf(Condition("coatingThickness", "contains", "腻"), Condition("pulseType", "contains", "滑")), BodyType.PHLEGM_DAMPNESS, 0.80f),
        Rule(listOf(Condition("tongueShape", "contains", "胖大"), Condition("coatingThickness", "contains", "厚"), Condition("coatingColor", "contains", "白")), BodyType.PHLEGM_DAMPNESS, 0.85f),
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("coatingThickness", "contains", "腻")), BodyType.PHLEGM_DAMPNESS, 0.75f),
        Rule(listOf(Condition("complexion", "contains", "偏黄"), Condition("tongueShape", "contains", "胖大")), BodyType.PHLEGM_DAMPNESS, 0.70f),

        // ==================== 气虚质 (QI_DEFICIENCY) ====================
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("tongueShape", "contains", "齿痕")), BodyType.QI_DEFICIENCY, 0.85f),
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("tongueBody", "contains", "嫩")), BodyType.QI_DEFICIENCY, 0.80f),
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("pulseStrength", "contains", "偏弱")), BodyType.QI_DEFICIENCY, 0.75f),
        Rule(listOf(Condition("complexion", "contains", "偏白"), Condition("tongueColor", "contains", "淡白")), BodyType.QI_DEFICIENCY, 0.75f),
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("tongueShape", "contains", "齿痕"), Condition("pulseStrength", "contains", "偏弱")), BodyType.QI_DEFICIENCY, 0.90f),
        Rule(listOf(Condition("tongueBody", "contains", "嫩"), Condition("tongueShape", "contains", "齿痕")), BodyType.QI_DEFICIENCY, 0.80f),
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("pulseType", "contains", "细")), BodyType.QI_DEFICIENCY, 0.75f),
        Rule(listOf(Condition("complexion", "contains", "偏白"), Condition("pulseStrength", "contains", "偏弱")), BodyType.QI_DEFICIENCY, 0.70f),

        // ==================== 阳虚质 (YANG_DEFICIENCY) ====================
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("tongueShape", "contains", "胖大")), BodyType.YANG_DEFICIENCY, 0.80f),
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("pulseType", "contains", "细")), BodyType.YANG_DEFICIENCY, 0.75f),
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("tongueShape", "contains", "胖大"), Condition("pulseType", "contains", "细")), BodyType.YANG_DEFICIENCY, 0.85f),
        Rule(listOf(Condition("complexion", "contains", "偏白"), Condition("tongueShape", "contains", "胖大")), BodyType.YANG_DEFICIENCY, 0.75f),
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("tongueBody", "contains", "嫩"), Condition("pulseType", "contains", "细")), BodyType.YANG_DEFICIENCY, 0.80f),
        Rule(listOf(Condition("coatingColor", "contains", "白"), Condition("tongueColor", "contains", "淡白"), Condition("tongueShape", "contains", "胖大")), BodyType.YANG_DEFICIENCY, 0.80f),
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("pulseStrength", "contains", "偏弱")), BodyType.YANG_DEFICIENCY, 0.70f),

        // ==================== 阴虚质 (YIN_DEFICIENCY) ====================
        Rule(listOf(Condition("tongueColor", "contains", "红"), Condition("coatingMoisture", "contains", "燥")), BodyType.YIN_DEFICIENCY, 0.85f),
        Rule(listOf(Condition("tongueColor", "contains", "红"), Condition("tongueBody", "contains", "老")), BodyType.YIN_DEFICIENCY, 0.80f),
        Rule(listOf(Condition("tongueColor", "contains", "红"), Condition("tongueShape", "contains", "瘦薄")), BodyType.YIN_DEFICIENCY, 0.80f),
        Rule(listOf(Condition("complexion", "contains", "偏红"), Condition("tongueColor", "contains", "红")), BodyType.YIN_DEFICIENCY, 0.75f),
        Rule(listOf(Condition("tongueColor", "contains", "红"), Condition("tongueShape", "contains", "瘦薄"), Condition("coatingMoisture", "contains", "燥")), BodyType.YIN_DEFICIENCY, 0.90f),
        Rule(listOf(Condition("tongueColor", "contains", "红"), Condition("coatingThickness", "contains", "薄")), BodyType.YIN_DEFICIENCY, 0.75f),
        Rule(listOf(Condition("tongueColor", "contains", "红"), Condition("pulseType", "contains", "细")), BodyType.YIN_DEFICIENCY, 0.75f),
        Rule(listOf(Condition("tongueColor", "contains", "红绛"), Condition("tongueShape", "contains", "瘦薄")), BodyType.YIN_DEFICIENCY, 0.80f),
        Rule(listOf(Condition("coatingColor", "contains", "少"), Condition("tongueColor", "contains", "红")), BodyType.YIN_DEFICIENCY, 0.80f),

        // ==================== 血瘀质 (BLOOD_STASIS) ====================
        Rule(listOf(Condition("tongueColor", "contains", "紫暗")), BodyType.BLOOD_STASIS, 0.85f),
        Rule(listOf(Condition("tongueColor", "contains", "红绛"), Condition("complexion", "contains", "晦暗")), BodyType.BLOOD_STASIS, 0.75f),
        Rule(listOf(Condition("sublingualVein", "contains", "怒张")), BodyType.BLOOD_STASIS, 0.85f),
        Rule(listOf(Condition("tongueColor", "contains", "紫暗"), Condition("sublingualVein", "contains", "怒张")), BodyType.BLOOD_STASIS, 0.90f),
        Rule(listOf(Condition("tongueColor", "contains", "紫暗"), Condition("complexion", "contains", "晦暗")), BodyType.BLOOD_STASIS, 0.85f),
        Rule(listOf(Condition("tongueColor", "contains", "暗"), Condition("pulseType", "contains", "涩")), BodyType.BLOOD_STASIS, 0.80f),
        Rule(listOf(Condition("tongueColor", "contains", "紫暗"), Condition("tongueBody", "contains", "老")), BodyType.BLOOD_STASIS, 0.75f),
        Rule(listOf(Condition("complexion", "contains", "晦暗"), Condition("sublingualVein", "contains", "怒张")), BodyType.BLOOD_STASIS, 0.80f),

        // ==================== 气郁质 (QI_STAGNATION) ====================
        Rule(listOf(Condition("complexion", "contains", "晦暗"), Condition("tongueColor", "contains", "暗")), BodyType.QI_STAGNATION, 0.70f),
        Rule(listOf(Condition("pulseType", "contains", "弦")), BodyType.QI_STAGNATION, 0.75f),
        Rule(listOf(Condition("pulseType", "contains", "弦"), Condition("tongueColor", "contains", "暗")), BodyType.QI_STAGNATION, 0.80f),
        Rule(listOf(Condition("pulseType", "contains", "弦"), Condition("complexion", "contains", "晦暗")), BodyType.QI_STAGNATION, 0.75f),
        Rule(listOf(Condition("pulseType", "contains", "弦"), Condition("tongueColor", "contains", "淡白")), BodyType.QI_STAGNATION, 0.70f),
        Rule(listOf(Condition("tongueShape", "contains", "正常"), Condition("pulseType", "contains", "弦")), BodyType.QI_STAGNATION, 0.70f),

        // ==================== 特禀质 (INHERENT_SPECIAL) ====================
        Rule(listOf(Condition("tongueColor", "contains", "淡白"), Condition("tongueShape", "contains", "正常"), Condition("tongueBody", "contains", "嫩")), BodyType.INHERENT_SPECIAL, 0.60f),
        Rule(listOf(Condition("tongueColor", "contains", "淡红"), Condition("coatingThickness", "contains", "薄"), Condition("pulseType", "contains", "细")), BodyType.INHERENT_SPECIAL, 0.55f),

        // ==================== 平和质 (BALANCED) ====================
        Rule(listOf(Condition("tongueColor", "contains", "淡红"), Condition("coatingThickness", "contains", "薄")), BodyType.BALANCED, 0.70f),
        Rule(listOf(Condition("tongueColor", "contains", "淡红"), Condition("coatingColor", "contains", "白")), BodyType.BALANCED, 0.70f),
        Rule(listOf(Condition("tongueColor", "contains", "淡红"), Condition("tongueShape", "contains", "正常")), BodyType.BALANCED, 0.70f),
        Rule(listOf(Condition("tongueColor", "contains", "淡红"), Condition("tongueBody", "contains", "正常")), BodyType.BALANCED, 0.70f),
        Rule(listOf(Condition("tongueColor", "contains", "淡红"), Condition("coatingThickness", "contains", "薄"), Condition("tongueShape", "contains", "正常")), BodyType.BALANCED, 0.80f),
        Rule(listOf(Condition("complexion", "contains", "正常"), Condition("tongueColor", "contains", "淡红")), BodyType.BALANCED, 0.75f),
        Rule(listOf(Condition("pulseType", "contains", "正常"), Condition("tongueColor", "contains", "淡红")), BodyType.BALANCED, 0.70f),
        Rule(listOf(Condition("tongueColor", "contains", "淡红"), Condition("coatingThickness", "contains", "薄"), Condition("coatingColor", "contains", "白")), BodyType.BALANCED, 0.80f),
    )

    private fun checkCondition(field: String, operator: String, value: String, context: Map<String, String>): Boolean {
        val fieldValue = context[field] ?: return false
        return when (operator) {
            "contains" -> fieldValue.contains(value, ignoreCase = true)
            "equals" -> fieldValue.equals(value, ignoreCase = true)
            else -> false
        }
    }

    /**
     * 体质倾向性评分 — 对所有9种体质计算倾向性评分。
     * 返回按评分降序排列的体质列表，展示每种体质的符合程度。
     */
    fun calculateTendencyScores(
        faceResult: FaceDiagnosisResult,
        tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult,
        bpResult: BloodPressureResult
    ): List<Pair<BodyType, Float>> {
        val context = mapOf(
            "tongueColor" to tongueResult.tongueColor,
            "coatingColor" to tongueResult.coatingColor,
            "coatingThickness" to tongueResult.coatingThickness,
            "coatingMoisture" to tongueResult.coatingMoisture,
            "tongueShape" to tongueResult.tongueShape,
            "tongueBody" to tongueResult.tongueBody,
            "sublingualVein" to tongueResult.sublingualVein,
            "complexion" to faceResult.overallComplexion,
            "pulseType" to pulseResult.pulseType,
            "pulseStrength" to pulseResult.pulseStrength
        )

        val scores = mutableMapOf<BodyType, Float>()
        // 初始化所有体质评分为0
        BodyType.entries.forEach { scores[it] = 0f }

        for (rule in rules) {
            val allMatch = rule.conditions.all { cond ->
                checkCondition(cond.field, cond.operator, cond.value, context)
            }
            if (allMatch) {
                scores[rule.bodyType] = maxOf(scores[rule.bodyType] ?: 0f, rule.confidence)
            }
        }

        return scores.entries
            .sortedByDescending { it.value }
            .map { Pair(it.key, it.value) }
    }

    private companion object {
        val SYSTEM_PROMPT = """
你是一位精通《黄帝内经》《伤寒杂病论》的资深中医辨证专家，同时具备现代医学知识。

## 辨证方法论
你需要遵循"四诊合参 → 八纲辨证 → 脏腑辨证 → 体质判定"的辩证推理链：
1. 望诊：分析面色、舌象（舌色、苔色、苔厚、苔质、舌形、舌质、舌下络脉）
2. 闻诊：基于面诊光泽度推断气机
3. 问诊：基于血压、心率推断脏腑功能
4. 切诊：分析脉象类型、力度、节律
5. 综合：八纲辨证（阴阳、表里、寒热、虚实）→ 脏腑辨证 → 体质判定

## 九种体质
- 平和质：阴阳调和，气血通畅，面色红润，精力充沛
- 气虚质：气短乏力，易疲劳，面色偏白，舌淡，脉弱，舌体嫩/齿痕
- 阳虚质：畏寒怕冷，手足不温，面色晄白，舌淡胖，脉沉迟
- 阴虚质：口干咽燥，手足心热，面色潮红，舌红少苔，脉细数，舌体老/瘦薄
- 痰湿质：体型肥胖，腹部肥满，面色淡黄，舌苔腻，脉滑，舌体胖大
- 湿热质：面垢油光，口苦口干，舌红苔黄腻，脉滑数
- 血瘀质：肤色晦暗，舌质紫暗，脉涩，舌下络脉怒张
- 气郁质：神情抑郁，忧虑脆弱，面色晦暗，脉弦
- 特禀质：先天失常，过敏体质，对外界适应力差

## 输出格式
请严格按照以下 JSON 格式回复，不要输出任何其他内容：
{
  "bodyType": "体质类型英文名",
  "bodyTypeName": "体质类型中文名",
  "confidence": 0.0-1.0,
  "reasoning": "辩证推理过程（八纲→脏腑→体质，50-100字）",
  "eightPrinciples": {"阴阳": "...", "表里": "...", "寒热": "...", "虚实": "..."},
  "zangFuDiagnosis": ["脏腑1: 辨证", "脏腑2: 辨证"]
}

体质类型英文名必须是以下之一：QI_DEFICIENCY, YANG_DEFICIENCY, YIN_DEFICIENCY, PHLEGM_DAMPNESS, DAMPNESS_HEAT, BLOOD_STASIS, QI_STAGNATION, BALANCED, INHERENT_SPECIAL
        """.trimIndent()
    }

    // ==================== 主分类流程 ====================

    suspend fun classify(
        faceResult: FaceDiagnosisResult,
        tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult,
        bpResult: BloodPressureResult
    ): Pair<BodyType, Float> = withContext(Dispatchers.Default) {
        // Agent A: 规则引擎初筛
        val candidates = ruleBasedScreening(faceResult, tongueResult, pulseResult, bpResult)
        Timber.d("BodyTypeClassifier: Agent A found ${candidates.size} candidates: ${candidates.map { it.first.displayName }}")

        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey == null) {
            Timber.w("BodyTypeClassifier: no API key, using Agent A result")
            return@withContext if (candidates.isNotEmpty()) candidates.first()
            else Pair(BodyType.BALANCED, 0.5f)
        }

        // Agent B: LLM 精辨
        try {
            val userMessage = buildPrompt(faceResult, tongueResult, pulseResult, bpResult, candidates)
            Timber.d("BodyTypeClassifier: sending to LLM...")

            val response = llmClient.chat(
                apiKey = apiKey, model = "qwen-plus",
                systemPrompt = SYSTEM_PROMPT, userMessage = userMessage, temperature = 0.2
            )
            parseResponse(response)
        } catch (e: LlmException) {
            Timber.e(e, "BodyTypeClassifier: LLM failed, fallback to Agent A")
            if (candidates.isNotEmpty()) candidates.first() else Pair(BodyType.BALANCED, 0.5f)
        }
    }

    private fun ruleBasedScreening(
        faceResult: FaceDiagnosisResult, tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult, bpResult: BloodPressureResult
    ): List<Pair<BodyType, Float>> {
        val context = mapOf(
            "tongueColor" to tongueResult.tongueColor,
            "coatingColor" to tongueResult.coatingColor,
            "coatingThickness" to tongueResult.coatingThickness,
            "coatingMoisture" to tongueResult.coatingMoisture,
            "tongueShape" to tongueResult.tongueShape,
            "tongueBody" to tongueResult.tongueBody,
            "sublingualVein" to tongueResult.sublingualVein,
            "complexion" to faceResult.overallComplexion,
            "pulseType" to pulseResult.pulseType,
            "pulseStrength" to pulseResult.pulseStrength
        )

        val results = mutableMapOf<BodyType, Float>()
        for (rule in rules) {
            val allMatch = rule.conditions.all { cond ->
                checkCondition(cond.field, cond.operator, cond.value, context)
            }
            if (allMatch) {
                results[rule.bodyType] = maxOf(results[rule.bodyType] ?: 0f, rule.confidence)
            }
        }
        return results.entries.sortedByDescending { it.value }.map { Pair(it.key, it.value) }
    }

    private fun buildPrompt(
        faceResult: FaceDiagnosisResult, tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult, bpResult: BloodPressureResult,
        candidates: List<Pair<BodyType, Float>>
    ): String = buildString {
        appendLine("请根据以下四诊数据进行中医辨证分析：")
        appendLine()
        appendLine("【望诊 - 面诊】")
        appendLine("- 面色：${faceResult.overallComplexion}")
        appendLine("- 光泽度：${String.format("%.0f", faceResult.glossLevel * 100)}%")
        if (faceResult.abnormalities.isNotEmpty()) {
            appendLine("- 异常：${faceResult.abnormalities.joinToString("；")}")
        }
        appendLine()
        appendLine("【望诊 - 舌诊（八维特征）】")
        appendLine("- 舌色：${tongueResult.tongueColor}")
        appendLine("- 苔色：${tongueResult.coatingColor}")
        appendLine("- 苔厚：${tongueResult.coatingThickness}")
        appendLine("- 苔质：${tongueResult.coatingMoisture}")
        appendLine("- 舌形：${tongueResult.tongueShape}")
        appendLine("- 舌质：${tongueResult.tongueBody}")
        appendLine("- 舌下络脉：${tongueResult.sublingualVein}")
        appendLine()
        appendLine("【切诊 - 脉诊】")
        appendLine("- 脉率：${pulseResult.pulseRate} 次/分")
        appendLine("- 节律：${pulseResult.pulseRhythm}")
        appendLine("- 力度：${pulseResult.pulseStrength}")
        appendLine("- 脉象：${pulseResult.pulseType}")
        appendLine()
        appendLine("【问诊 - 血压】")
        appendLine("- 收缩压：${bpResult.systolic} mmHg")
        appendLine("- 舒张压：${bpResult.diastolic} mmHg")
        appendLine("- 心率：${bpResult.heartRate} BPM")
        appendLine()
        if (candidates.isNotEmpty()) {
            appendLine("【规则引擎初筛候选】")
            candidates.forEach { (type, conf) ->
                appendLine("- ${type.displayName}（置信度 ${String.format("%.0f", conf * 100)}%）")
            }
        }
    }

    private fun parseResponse(response: String): Pair<BodyType, Float> {
        return try {
            val jsonStr = response.replace("```json", "").replace("```", "").trim()
            val json = org.json.JSONObject(jsonStr)
            val typeName = json.getString("bodyType")
            val confidence = json.getDouble("confidence").toFloat().coerceIn(0f, 1f)
            val bodyType = BodyType.entries.find { it.name == typeName } ?: BodyType.BALANCED
            Timber.d("BodyTypeClassifier: LLM result = ${bodyType.displayName}, confidence = $confidence")
            Pair(bodyType, confidence)
        } catch (e: Exception) {
            Timber.w(e, "BodyTypeClassifier: failed to parse LLM response")
            Pair(BodyType.BALANCED, 0.5f)
        }
    }
}