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
 * 中医九种体质分类器 — 基于通义千问 LLM。
 *
 * 将面诊、舌诊、脉诊、血压四诊数据序列化为结构化 Prompt，
 * 交由 LLM 根据中医体质学说进行综合推理分类。
 */
@Singleton
class BodyTypeClassifier @Inject constructor(
    private val llmClient: LlmClient,
    private val apiKeyProvider: ApiKeyProvider
) {

    private companion object {
        val SYSTEM_PROMPT = """
你是一位资深中医体质辨识专家，精通《中医体质分类与判定》标准（九种体质）。

你的任务是根据用户提供的四诊数据（面诊、舌诊、脉诊、血压），分析用户的体质类型。

九种体质及其特征：
- 平和质：阴阳调和，气血通畅，面色红润，精力充沛
- 气虚质：气短乏力，易疲劳，面色偏白，舌淡，脉弱
- 阳虚质：畏寒怕冷，手足不温，面色晄白，舌淡胖，脉沉迟
- 阴虚质：口干咽燥，手足心热，面色潮红，舌红少苔，脉细数
- 痰湿质：体型肥胖，腹部肥满，面色淡黄，舌苔腻，脉滑
- 湿热质：面垢油光，口苦口干，舌红苔黄腻，脉滑数
- 血瘀质：肤色晦暗，舌质紫暗，脉涩
- 气郁质：神情抑郁，忧虑脆弱，面色晦暗，脉弦
- 特禀质：先天失常，过敏体质，对外界适应力差

请严格按照以下 JSON 格式回复，不要输出任何其他内容：
{
  "bodyType": "体质类型英文名",
  "bodyTypeName": "体质类型中文名",
  "confidence": 0.0-1.0,
  "reasoning": "分析依据（简要说明判断依据）"
}

体质类型英文名必须是以下之一：QI_DEFICIENCY, YANG_DEFICIENCY, YIN_DEFICIENCY, PHLEGM_DAMPNESS, DAMPNESS_HEAT, BLOOD_STASIS, QI_STAGNATION, BALANCED, INHERENT_SPECIAL
        """.trimIndent()
    }

    /**
     * 根据四诊数据分类体质。
     *
     * @return 体质类型 + 置信度 (0.0-1.0)
     */
    suspend fun classify(
        faceResult: FaceDiagnosisResult,
        tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult,
        bpResult: BloodPressureResult
    ): Pair<BodyType, Float> = withContext(Dispatchers.Default) {
        val apiKey = apiKeyProvider.getApiKey()

        if (apiKey == null) {
            Timber.w("BodyTypeClassifier: API Key not available, falling back to rule-based")
            return@withContext fallbackClassify(faceResult, tongueResult, pulseResult, bpResult)
        }

        try {
            val userMessage = buildPrompt(faceResult, tongueResult, pulseResult, bpResult)
            Timber.d("BodyTypeClassifier: sending to LLM...")

            val response = llmClient.chat(
                apiKey = apiKey,
                model = "qwen-plus",
                systemPrompt = SYSTEM_PROMPT,
                userMessage = userMessage,
                temperature = 0.2
            )

            parseResponse(response)
        } catch (e: LlmException) {
            Timber.e(e, "BodyTypeClassifier: LLM call failed, falling back to rule-based")
            fallbackClassify(faceResult, tongueResult, pulseResult, bpResult)
        }
    }

    /**
     * 构建四诊数据 Prompt。
     */
    private fun buildPrompt(
        faceResult: FaceDiagnosisResult,
        tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult,
        bpResult: BloodPressureResult
    ): String {
        return buildString {
            appendLine("请根据以下四诊数据分析体质：")
            appendLine()
            appendLine("【面诊】")
            appendLine("- 面色：${faceResult.overallComplexion}")
            appendLine("- 光泽度：${String.format("%.1f", faceResult.glossLevel * 100)}%")
            if (faceResult.abnormalities.isNotEmpty()) {
                appendLine("- 异常：${faceResult.abnormalities.joinToString("、")}")
            }
            appendLine()
            appendLine("【舌诊】")
            appendLine("- 舌色：${tongueResult.tongueColor}")
            appendLine("- 苔色：${tongueResult.coatingColor}")
            appendLine("- 苔厚：${tongueResult.coatingThickness}")
            appendLine("- 苔质：${tongueResult.coatingMoisture}")
            appendLine("- 舌形：${tongueResult.tongueShape}")
            appendLine()
            appendLine("【脉诊】")
            appendLine("- 脉率：${pulseResult.pulseRate} 次/分")
            appendLine("- 节律：${pulseResult.pulseRhythm}")
            appendLine("- 力度：${pulseResult.pulseStrength}")
            appendLine("- 脉象：${pulseResult.pulseType}")
            appendLine()
            appendLine("【血压】")
            appendLine("- 收缩压：${bpResult.systolic} mmHg")
            appendLine("- 舒张压：${bpResult.diastolic} mmHg")
            appendLine("- 心率：${bpResult.heartRate} BPM")
        }
    }

    /**
     * 解析 LLM 返回的 JSON，提取体质类型和置信度。
     */
    private fun parseResponse(response: String): Pair<BodyType, Float> {
        return try {
            // 提取 JSON（可能包裹在 markdown 代码块中）
            val jsonStr = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = org.json.JSONObject(jsonStr)
            val typeName = json.getString("bodyType")
            val confidence = json.getDouble("confidence").toFloat().coerceIn(0f, 1f)

            val bodyType = BodyType.entries.find { it.name == typeName } ?: BodyType.BALANCED
            Timber.d("BodyTypeClassifier: LLM result = ${bodyType.displayName}, confidence = $confidence")
            Pair(bodyType, confidence)
        } catch (e: Exception) {
            Timber.w(e, "BodyTypeClassifier: failed to parse LLM response, falling back to BALANCED")
            Pair(BodyType.BALANCED, 0.5f)
        }
    }

    /**
     * 降级方案：基于简单规则进行体质分类。
     * 当 LLM 不可用时使用。
     */
    private fun fallbackClassify(
        faceResult: FaceDiagnosisResult,
        tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult,
        bpResult: BloodPressureResult
    ): Pair<BodyType, Float> {
        Timber.d("BodyTypeClassifier: using rule-based fallback classification")

        // 基于舌诊和面色的简单规则匹配
        val tongueColor = tongueResult.tongueColor
        val coatingColor = tongueResult.coatingColor
        val complexion = faceResult.overallComplexion

        return when {
            // 舌红 + 苔黄 → 湿热
            tongueColor.contains("红") && coatingColor.contains("黄") ->
                Pair(BodyType.DAMPNESS_HEAT, 0.7f)
            // 舌淡 + 面色白 → 气虚/阳虚
            tongueColor.contains("淡") && complexion.contains("偏白") ->
                Pair(BodyType.QI_DEFICIENCY, 0.65f)
            // 舌紫 → 血瘀
            tongueColor.contains("紫") ->
                Pair(BodyType.BLOOD_STASIS, 0.7f)
            // 舌红 + 苔少 → 阴虚
            tongueColor.contains("红") && coatingColor.contains("灰") ->
                Pair(BodyType.YIN_DEFICIENCY, 0.65f)
            // 苔腻 → 痰湿
            tongueResult.coatingThickness.contains("厚") ->
                Pair(BodyType.PHLEGM_DAMPNESS, 0.6f)
            // 脉数 → 阴虚/湿热
            pulseResult.pulseRate > 90 ->
                Pair(BodyType.YIN_DEFICIENCY, 0.55f)
            // 血压偏高 → 阴虚
            bpResult.systolic > 140 ->
                Pair(BodyType.YIN_DEFICIENCY, 0.55f)
            // 默认：平和质
            else -> Pair(BodyType.BALANCED, 0.6f)
        }
    }
}