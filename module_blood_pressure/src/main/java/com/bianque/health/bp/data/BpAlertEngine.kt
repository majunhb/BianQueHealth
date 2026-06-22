package com.bianque.health.bp.data

import com.bianque.health.bp.domain.model.BloodPressureResult
import com.bianque.health.bp.data.BpTrendAnalyzer.TrendDirection
import timber.log.Timber

/**
 * 血压异常预警引擎。
 *
 * 基于实时血压和历史趋势进行多级异常预警：
 * - 实时异常检测：高血压危象、低血压、心律失常
 * - 趋势异常检测：持续上升趋势、晨峰高血压、夜间高血压、波动过大
 * - 风险等级评估：低/中/高/极高
 * - 就医建议
 *
 * 对标竞品血压异常预警功能（如 Omron Complete, QardioArm）。
 */
object BpAlertEngine {

    /** 预警等级 */
    enum class AlertLevel {
        NORMAL,    // 正常
        LOW,       // 低风险（关注）
        MEDIUM,    // 中风险（需要干预）
        HIGH,      // 高风险（建议就医）
        CRITICAL   // 极高风险（立即就医）
    }

    /** 预警类别 */
    enum class AlertCategory {
        HYPERTENSION_CRISIS,    // 高血压危象
        HYPERTENSION_HIGH,      // 高血压偏高
        HYPOTENSION,            // 低血压
        MORNING_SURGE,          // 晨峰高血压
        HIGH_VARIABILITY,       // 血压波动过大
        RISING_TREND,           // 持续上升趋势
        IRREGULAR_HEARTBEAT,    // 心律不齐
        ISOLATED_SYSTOLIC,      // 单纯收缩期高血压
        ISOLATED_DIASTOLIC,     // 单纯舒张期高血压
        PULSE_PRESSURE_ABNORMAL // 脉压异常
    }

    /** 单条预警 */
    data class BpAlert(
        val category: AlertCategory,
        val level: AlertLevel,
        val title: String,
        val description: String,
        val recommendation: String,
        val systolic: Int? = null,
        val diastolic: Int? = null,
        val heartRate: Int? = null
    )

    /** 综合预警结果 */
    data class BpAlertResult(
        val overallLevel: AlertLevel,
        val alerts: List<BpAlert>,
        val summary: String,
        val shouldSeeDoctor: Boolean,
        val shouldCallEmergency: Boolean
    )

    // ==================== 实时异常检测 ====================

    /**
     * 对单次血压测量进行实时异常检测。
     */
    fun analyze(bp: BloodPressureResult): BpAlertResult {
        val alerts = mutableListOf<BpAlert>()

        // 高血压危象检测
        checkHypertensionCrisis(bp)?.let { alerts.add(it) }

        // 高血压检测
        checkHypertension(bp)?.let { alerts.add(it) }

        // 低血压检测
        checkHypotension(bp)?.let { alerts.add(it) }

        // 单纯收缩期/舒张期高血压
        checkIsolatedHypertension(bp)?.let { alerts.add(it) }

        // 脉压异常
        checkPulsePressure(bp)?.let { alerts.add(it) }

        // 心律不齐
        checkIrregularHeartbeat(bp)?.let { alerts.add(it) }

        val overallLevel = determineOverallLevel(alerts, bp)
        val shouldSeeDoctor = overallLevel.ordinal >= AlertLevel.MEDIUM.ordinal
        val shouldCallEmergency = overallLevel == AlertLevel.CRITICAL

        val summary = buildSummary(alerts, overallLevel, bp)

        return BpAlertResult(
            overallLevel = overallLevel,
            alerts = alerts,
            summary = summary,
            shouldSeeDoctor = shouldSeeDoctor,
            shouldCallEmergency = shouldCallEmergency
        )
    }

    /**
     * 结合趋势数据进行综合预警。
     */
    fun analyzeWithTrend(
        bp: BloodPressureResult,
        trendResult: BpTrendAnalyzer.BpTrendResult
    ): BpAlertResult {
        val baseResult = analyze(bp)
        val alerts = baseResult.alerts.toMutableList()

        // 晨峰高血压检测
        checkMorningSurge(bp, trendResult)?.let { alerts.add(it) }

        // 血压波动过大
        checkHighVariability(trendResult)?.let { alerts.add(it) }

        // 持续上升趋势
        checkRisingTrend(trendResult)?.let { alerts.add(it) }

        val overallLevel = determineOverallLevel(alerts, bp)
        val shouldSeeDoctor = overallLevel.ordinal >= AlertLevel.MEDIUM.ordinal
        val shouldCallEmergency = overallLevel == AlertLevel.CRITICAL

        return BpAlertResult(
            overallLevel = overallLevel,
            alerts = alerts,
            summary = buildSummary(alerts, overallLevel, bp),
            shouldSeeDoctor = shouldSeeDoctor,
            shouldCallEmergency = shouldCallEmergency
        )
    }

    // ==================== 具体检测逻辑 ====================

    private fun checkHypertensionCrisis(bp: BloodPressureResult): BpAlert? {
        if (bp.systolic >= 180 || bp.diastolic >= 110) {
            return BpAlert(
                category = AlertCategory.HYPERTENSION_CRISIS,
                level = AlertLevel.CRITICAL,
                title = "高血压危象",
                description = "血压 ${bp.systolic}/${bp.diastolic} mmHg 达到高血压危象水平。",
                recommendation = "请立即就医！如伴有头痛、胸痛、呼吸困难、视力模糊等症状，请立即拨打120。",
                systolic = bp.systolic, diastolic = bp.diastolic
            )
        }
        return null
    }

    private fun checkHypertension(bp: BloodPressureResult): BpAlert? {
        val sys = bp.systolic
        val dia = bp.diastolic

        return when {
            sys >= 160 || dia >= 100 -> BpAlert(
                category = AlertCategory.HYPERTENSION_HIGH,
                level = AlertLevel.HIGH,
                title = "2级高血压",
                description = "血压 ${sys}/${dia} mmHg 属于中度高血压。",
                recommendation = "建议尽快就医，遵医嘱调整用药方案。注意低盐饮食、规律作息。",
                systolic = sys, diastolic = dia
            )
            sys >= 140 || dia >= 90 -> BpAlert(
                category = AlertCategory.HYPERTENSION_HIGH,
                level = AlertLevel.MEDIUM,
                title = "1级高血压",
                description = "血压 ${sys}/${dia} mmHg 属于轻度高血压。",
                recommendation = "建议就医评估，同时注意饮食控制（低盐低脂）、适量运动、戒烟限酒。",
                systolic = sys, diastolic = dia
            )
            sys >= 130 || dia >= 85 -> BpAlert(
                category = AlertCategory.HYPERTENSION_HIGH,
                level = AlertLevel.LOW,
                title = "正常高值",
                description = "血压 ${sys}/${dia} mmHg 处于正常高值范围。",
                recommendation = "建议加强生活方式干预：减少钠盐摄入、增加运动、控制体重。",
                systolic = sys, diastolic = dia
            )
            else -> null
        }
    }

    private fun checkHypotension(bp: BloodPressureResult): BpAlert? {
        if (bp.systolic < 90 || bp.diastolic < 60) {
            return BpAlert(
                category = AlertCategory.HYPOTENSION,
                level = if (bp.systolic < 80) AlertLevel.HIGH else AlertLevel.MEDIUM,
                title = "低血压",
                description = "血压 ${bp.systolic}/${bp.diastolic} mmHg 低于正常范围。",
                recommendation = "注意补充水分和盐分摄入。如伴有头晕、乏力、眼前发黑等症状，请及时就医。避免突然站立。",
                systolic = bp.systolic, diastolic = bp.diastolic
            )
        }
        return null
    }

    private fun checkIsolatedHypertension(bp: BloodPressureResult): BpAlert? {
        val sys = bp.systolic
        val dia = bp.diastolic

        return when {
            sys >= 140 && dia < 90 -> BpAlert(
                category = AlertCategory.ISOLATED_SYSTOLIC,
                level = AlertLevel.MEDIUM,
                title = "单纯收缩期高血压",
                description = "收缩压 ${sys} mmHg 偏高而舒张压 ${dia} mmHg 正常。常见于老年人，提示大动脉硬化。",
                recommendation = "建议就医进行动脉硬化评估。注意控制盐摄入，可适当补充钙和维生素D。",
                systolic = sys, diastolic = dia
            )
            sys < 140 && dia >= 90 -> BpAlert(
                category = AlertCategory.ISOLATED_DIASTOLIC,
                level = AlertLevel.MEDIUM,
                title = "单纯舒张期高血压",
                description = "舒张压 ${dia} mmHg 偏高而收缩压 ${sys} mmHg 正常。常见于中青年，与外周血管阻力增高有关。",
                recommendation = "建议控制体重、减少精神压力、增加有氧运动。必要时就医评估。",
                systolic = sys, diastolic = dia
            )
            else -> null
        }
    }

    private fun checkPulsePressure(bp: BloodPressureResult): BpAlert? {
        val pulsePressure = bp.systolic - bp.diastolic
        return when {
            pulsePressure > 60 -> BpAlert(
                category = AlertCategory.PULSE_PRESSURE_ABNORMAL,
                level = AlertLevel.LOW,
                title = "脉压增大",
                description = "脉压差 ${pulsePressure} mmHg 偏大，提示可能存在动脉硬化。",
                recommendation = "建议定期监测血压，如持续脉压增大请就医进行心血管评估。",
                systolic = bp.systolic, diastolic = bp.diastolic
            )
            pulsePressure < 30 -> BpAlert(
                category = AlertCategory.PULSE_PRESSURE_ABNORMAL,
                level = AlertLevel.LOW,
                title = "脉压减小",
                description = "脉压差 ${pulsePressure} mmHg 偏小，可能提示心输出量减少。",
                recommendation = "如伴有乏力、头晕等症状，建议就医检查心功能。",
                systolic = bp.systolic, diastolic = bp.diastolic
            )
            else -> null
        }
    }

    private fun checkIrregularHeartbeat(bp: BloodPressureResult): BpAlert? {
        if (bp.heartRate > 100) {
            return BpAlert(
                category = AlertCategory.IRREGULAR_HEARTBEAT,
                level = AlertLevel.MEDIUM,
                title = "心动过速",
                description = "心率 ${bp.heartRate} BPM，超过正常范围。",
                recommendation = "如持续快速心率且伴有心悸、胸闷等症状，建议就医检查。避免咖啡因和酒精摄入。",
                heartRate = bp.heartRate
            )
        }
        if (bp.heartRate < 50) {
            return BpAlert(
                category = AlertCategory.IRREGULAR_HEARTBEAT,
                level = AlertLevel.MEDIUM,
                title = "心动过缓",
                description = "心率 ${bp.heartRate} BPM，低于正常范围。",
                recommendation = "如持续慢心率且伴有头晕、乏力等症状，建议就医检查。",
                heartRate = bp.heartRate
            )
        }
        return null
    }

    // ==================== 趋势异常检测 ====================

    private fun checkMorningSurge(
        bp: BloodPressureResult,
        trend: BpTrendAnalyzer.BpTrendResult
    ): BpAlert? {
        val morningSbp = trend.morningAvgSystolic ?: return null
        if (morningSbp >= 135) {
            return BpAlert(
                category = AlertCategory.MORNING_SURGE,
                level = if (morningSbp >= 150) AlertLevel.HIGH else AlertLevel.MEDIUM,
                title = "晨峰高血压",
                description = "晨间（6-10点）平均收缩压 ${morningSbp.toInt()} mmHg，存在晨峰高血压现象。",
                recommendation = "晨峰高血压是心血管事件的独立危险因素。建议早晨起床动作缓慢，避免剧烈运动，按时服药。",
                systolic = morningSbp.toInt()
            )
        }
        return null
    }

    private fun checkHighVariability(trend: BpTrendAnalyzer.BpTrendResult): BpAlert? {
        if (trend.variabilityIndex > 0.3f) {
            return BpAlert(
                category = AlertCategory.HIGH_VARIABILITY,
                level = AlertLevel.MEDIUM,
                title = "血压波动过大",
                description = "血压波动性指数 ${String.format("%.2f", trend.variabilityIndex)}，超过正常范围。" +
                    "标准差：收缩压 ±${String.format("%.1f", trend.stdSystolic)}，舒张压 ±${String.format("%.1f", trend.stdDiastolic)}。",
                recommendation = "血压波动过大可能增加心血管风险。建议增加测量频率，记录血压日记，并咨询医生调整用药方案。",
                systolic = trend.avgSystolic.toInt(), diastolic = trend.avgDiastolic.toInt()
            )
        }
        return null
    }

    private fun checkRisingTrend(trend: BpTrendAnalyzer.BpTrendResult): BpAlert? {
        if (trend.sbpTrend == TrendDirection.RISING && trend.sbpTrendSlope > 0.5f) {
            return BpAlert(
                category = AlertCategory.RISING_TREND,
                level = if (trend.sbpTrendSlope > 1.0f) AlertLevel.HIGH else AlertLevel.MEDIUM,
                title = "血压持续上升",
                description = "${trend.period}收缩压呈上升趋势，日变化率 ${String.format("%.1f", trend.sbpTrendSlope)} mmHg/天。",
                recommendation = "血压持续上升需引起重视。建议回顾近期生活方式变化（饮食、压力、睡眠），必要时就医调整治疗方案。",
                systolic = trend.avgSystolic.toInt(), diastolic = trend.avgDiastolic.toInt()
            )
        }
        return null
    }

    // ==================== 综合评估 ====================

    private fun determineOverallLevel(alerts: List<BpAlert>, bp: BloodPressureResult): AlertLevel {
        if (alerts.isEmpty()) return AlertLevel.NORMAL
        return alerts.maxByOrNull { it.level.ordinal }?.level ?: AlertLevel.NORMAL
    }

    private fun buildSummary(alerts: List<BpAlert>, level: AlertLevel, bp: BloodPressureResult): String {
        if (alerts.isEmpty()) {
            return "血压 ${bp.systolic}/${bp.diastolic} mmHg，心率 ${bp.heartRate} BPM，一切正常，请继续保持。"
        }

        return when (level) {
            AlertLevel.NORMAL -> "血压正常，请继续保持健康生活方式。"
            AlertLevel.LOW -> "血压处于正常高值，建议关注生活方式调整。"
            AlertLevel.MEDIUM -> "检测到 ${alerts.size} 项异常，建议关注血压变化并考虑就医咨询。"
            AlertLevel.HIGH -> "检测到 ${alerts.size} 项异常（高风险），强烈建议尽快就医。"
            AlertLevel.CRITICAL -> "检测到高血压危象！请立即就医或拨打120！"
        }
    }
}