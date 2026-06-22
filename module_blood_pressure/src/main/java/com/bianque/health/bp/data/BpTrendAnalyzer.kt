package com.bianque.health.bp.data

import com.bianque.health.bp.domain.model.BloodPressureResult
import timber.log.Timber
import kotlin.math.sqrt

/**
 * 血压趋势分析器。
 *
 * 基于历史血压数据提供多维度的趋势分析：
 * - 周趋势（最近7天）
 * - 月趋势（最近30天）
 * - 平均值、标准差、变化率
 * - 血压波动性评估
 * - 趋势方向判断（上升/下降/稳定）
 *
 * 对标竞品血压趋势分析功能（如 Withings Health Mate, Omron Connect）。
 */
object BpTrendAnalyzer {

    /** 血压分级阈值（中国高血压防治指南 2024） */
    object BpGrade {
        // 收缩压
        const val SBP_OPTIMAL_MAX = 120       // 理想血压上限
        const val SBP_NORMAL_MAX = 129        // 正常血压上限
        const val SBP_HIGH_NORMAL_MAX = 139   // 正常高值上限
        const val SBP_HYPERTENSION_1_MAX = 159 // 1级高血压上限
        const val SBP_HYPERTENSION_2_MAX = 179 // 2级高血压上限
        // 舒张压
        const val DBP_OPTIMAL_MAX = 80
        const val DBP_NORMAL_MAX = 84
        const val DBP_HIGH_NORMAL_MAX = 89
        const val DBP_HYPERTENSION_1_MAX = 99
        const val DBP_HYPERTENSION_2_MAX = 109
    }

    /**
     * 血压趋势分析结果。
     */
    data class BpTrendResult(
        val period: String,                    // 分析周期描述（如"最近7天"）
        val recordCount: Int,                  // 记录数
        val avgSystolic: Float,                // 平均收缩压
        val avgDiastolic: Float,               // 平均舒张压
        val avgHeartRate: Float,               // 平均心率
        val stdSystolic: Float,                // 收缩压标准差
        val stdDiastolic: Float,               // 舒张压标准差
        val maxSystolic: Int,                  // 最高收缩压
        val maxDiastolic: Int,                 // 最高舒张压
        val minSystolic: Int,                  // 最低收缩压
        val minDiastolic: Int,                 // 最低舒张压
        val sbpTrend: TrendDirection,          // 收缩压趋势方向
        val dbpTrend: TrendDirection,          // 舒张压趋势方向
        val sbpTrendSlope: Float,              // 收缩压趋势斜率（mmHg/天）
        val dbpTrendSlope: Float,              // 舒张压趋势斜率（mmHg/天）
        val variabilityIndex: Float,           // 血压波动性指数（0-1，越高越不稳定）
        val morningAvgSystolic: Float?,        // 晨峰血压（6-10点）
        val morningAvgDiastolic: Float?,
        val eveningAvgSystolic: Float?,        // 晚间血压（18-22点）
        val eveningAvgDiastolic: Float?,
        val hypertensionGrade: String,         // 基于平均值的血压分级
        val summary: String                    // 趋势总结文本
    )

    enum class TrendDirection {
        RISING,       // 上升趋势
        FALLING,      // 下降趋势
        STABLE,       // 稳定
        INSUFFICIENT  // 数据不足
    }

    // ==================== 趋势分析 ====================

    /**
     * 分析最近7天血压趋势。
     */
    fun analyzeWeeklyTrend(records: List<BloodPressureResult>): BpTrendResult {
        val now = System.currentTimeMillis()
        val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
        return analyzeTrend(records.filter { it.timestamp >= weekAgo }, "最近7天")
    }

    /**
     * 分析最近30天血压趋势。
     */
    fun analyzeMonthlyTrend(records: List<BloodPressureResult>): BpTrendResult {
        val now = System.currentTimeMillis()
        val monthAgo = now - 30L * 24 * 60 * 60 * 1000L
        return analyzeTrend(records.filter { it.timestamp >= monthAgo }, "最近30天")
    }

    /**
     * 通用趋势分析。
     */
    fun analyzeTrend(records: List<BloodPressureResult>, period: String): BpTrendResult {
        if (records.size < 2) {
            return BpTrendResult(
                period = period,
                recordCount = records.size,
                avgSystolic = records.firstOrNull()?.systolic?.toFloat() ?: 0f,
                avgDiastolic = records.firstOrNull()?.diastolic?.toFloat() ?: 0f,
                avgHeartRate = records.firstOrNull()?.heartRate?.toFloat() ?: 0f,
                stdSystolic = 0f, stdDiastolic = 0f,
                maxSystolic = records.firstOrNull()?.systolic ?: 0,
                maxDiastolic = records.firstOrNull()?.diastolic ?: 0,
                minSystolic = records.firstOrNull()?.systolic ?: 0,
                minDiastolic = records.firstOrNull()?.diastolic ?: 0,
                sbpTrend = TrendDirection.INSUFFICIENT,
                dbpTrend = TrendDirection.INSUFFICIENT,
                sbpTrendSlope = 0f, dbpTrendSlope = 0f,
                variabilityIndex = 0f,
                morningAvgSystolic = null, morningAvgDiastolic = null,
                eveningAvgSystolic = null, eveningAvgDiastolic = null,
                hypertensionGrade = classifyHypertension(
                    records.firstOrNull()?.systolic?.toFloat() ?: 0f,
                    records.firstOrNull()?.diastolic?.toFloat() ?: 0f
                ),
                summary = "数据不足，至少需要2条记录。"
            )
        }

        val sorted = records.sortedBy { it.timestamp }

        // 基础统计
        val sbpValues = sorted.map { it.systolic.toFloat() }
        val dbpValues = sorted.map { it.diastolic.toFloat() }
        val hrValues = sorted.map { it.heartRate.toFloat() }

        val avgSbp = sbpValues.average().toFloat()
        val avgDbp = dbpValues.average().toFloat()
        val avgHr = hrValues.average().toFloat()

        val stdSbp = calculateStd(sbpValues, avgSbp)
        val stdDbp = calculateStd(dbpValues, avgDbp)

        val maxSbp = sorted.maxBy { it.systolic }.systolic
        val maxDbp = sorted.maxBy { it.diastolic }.diastolic
        val minSbp = sorted.minBy { it.systolic }.systolic
        val minDbp = sorted.minBy { it.diastolic }.diastolic

        // 趋势斜率（线性回归）
        val sbpSlope = calculateTrendSlope(sorted, { it.systolic.toFloat() })
        val dbpSlope = calculateTrendSlope(sorted, { it.diastolic.toFloat() })

        val sbpTrend = when {
            sbpSlope > 0.5f -> TrendDirection.RISING
            sbpSlope < -0.5f -> TrendDirection.FALLING
            else -> TrendDirection.STABLE
        }
        val dbpTrend = when {
            dbpSlope > 0.3f -> TrendDirection.RISING
            dbpSlope < -0.3f -> TrendDirection.FALLING
            else -> TrendDirection.STABLE
        }

        // 波动性指数
        val variabilityIndex = ((stdSbp / avgSbp) + (maxSbp - minSbp) / avgSbp * 0.3f).coerceIn(0f, 1f)

        // 晨峰和晚间血压
        val morningRecords = sorted.filter {
            val hour = getHourOfDay(it.timestamp)
            hour in 6..10
        }
        val eveningRecords = sorted.filter {
            val hour = getHourOfDay(it.timestamp)
            hour in 18..22
        }

        val morningAvgSbp = if (morningRecords.isNotEmpty())
            morningRecords.map { it.systolic.toFloat() }.average().toFloat() else null
        val morningAvgDbp = if (morningRecords.isNotEmpty())
            morningRecords.map { it.diastolic.toFloat() }.average().toFloat() else null
        val eveningAvgSbp = if (eveningRecords.isNotEmpty())
            eveningRecords.map { it.systolic.toFloat() }.average().toFloat() else null
        val eveningAvgDbp = if (eveningRecords.isNotEmpty())
            eveningRecords.map { it.diastolic.toFloat() }.average().toFloat() else null

        // 分级
        val grade = classifyHypertension(avgSbp, avgDbp)

        // 趋势总结
        val summary = buildTrendSummary(avgSbp, avgDbp, sbpTrend, dbpTrend, grade, variabilityIndex, period)

        return BpTrendResult(
            period = period,
            recordCount = records.size,
            avgSystolic = avgSbp, avgDiastolic = avgDbp, avgHeartRate = avgHr,
            stdSystolic = stdSbp, stdDiastolic = stdDbp,
            maxSystolic = maxSbp, maxDiastolic = maxDbp,
            minSystolic = minSbp, minDiastolic = minDbp,
            sbpTrend = sbpTrend, dbpTrend = dbpTrend,
            sbpTrendSlope = sbpSlope, dbpTrendSlope = dbpSlope,
            variabilityIndex = variabilityIndex,
            morningAvgSystolic = morningAvgSbp, morningAvgDiastolic = morningAvgDbp,
            eveningAvgSystolic = eveningAvgSbp, eveningAvgDiastolic = eveningAvgDbp,
            hypertensionGrade = grade,
            summary = summary
        )
    }

    // ==================== 血压分级 ====================

    /**
     * 根据中国高血压防治指南分级。
     */
    fun classifyHypertension(sys: Float, dia: Float): String {
        return when {
            sys >= 180 || dia >= 110 -> "3级高血压（重度）"
            sys >= 160 || dia >= 100 -> "2级高血压（中度）"
            sys >= 140 || dia >= 90 -> "1级高血压（轻度）"
            sys >= 130 || dia >= 85 -> "正常高值"
            sys >= 120 || dia >= 80 -> "正常血压"
            sys >= 90 && dia >= 60 -> "理想血压"
            sys < 90 || dia < 60 -> "低血压"
            else -> "理想血压"
        }
    }

    /**
     * 获取血压颜色等级（用于UI展示）。
     */
    fun getBpColorLevel(sys: Float, dia: Float): Int {
        return when {
            sys >= 180 || dia >= 110 -> 4 // 红色 - 危险
            sys >= 160 || dia >= 100 -> 3 // 橙色 - 严重
            sys >= 140 || dia >= 90 -> 2  // 黄色 - 警告
            sys >= 130 || dia >= 85 -> 1  // 浅绿 - 偏高
            sys >= 90 && dia >= 60 -> 0   // 绿色 - 正常
            else -> 1                      // 低血压也需关注
        }
    }

    // ==================== 辅助方法 ====================

    private fun calculateStd(values: List<Float>, mean: Float): Float {
        if (values.size <= 1) return 0f
        val variance = values.map { (it - mean) * (it - mean) }.sum() / (values.size - 1)
        return sqrt(variance.toDouble()).toFloat()
    }

    /**
     * 线性回归计算趋势斜率。
     */
    private fun calculateTrendSlope(
        records: List<BloodPressureResult>,
        valueExtractor: (BloodPressureResult) -> Float
    ): Float {
        if (records.size < 2) return 0f
        val n = records.size.toFloat()
        val xValues = records.indices.map { it.toFloat() }
        val yValues = records.map(valueExtractor)

        val sumX = xValues.sum()
        val sumY = yValues.sum()
        val sumXY = xValues.zip(yValues).sumOf { (x, y) -> (x * y).toDouble() }
        val sumX2 = xValues.sumOf { (it * it).toDouble() }

        val denominator = n * sumX2 - sumX * sumX
        if (denominator == 0.0) return 0f

        return ((n * sumXY - sumX * sumY) / denominator).toFloat()
    }

    private fun getHourOfDay(timestamp: Long): Int {
        return ((timestamp / 3600000) % 24).toInt()
    }

    private fun buildTrendSummary(
        avgSbp: Float, avgDbp: Float,
        sbpTrend: TrendDirection, dbpTrend: TrendDirection,
        grade: String, variability: Float, period: String
    ): String {
        val sb = StringBuilder()
        sb.append("${period}平均血压：${avgSbp.toInt()}/${avgDbp.toInt()} mmHg，分级：${grade}。")

        when {
            sbpTrend == TrendDirection.RISING || dbpTrend == TrendDirection.RISING -> {
                sb.append("血压呈上升趋势，建议密切关注。")
            }
            sbpTrend == TrendDirection.FALLING && dbpTrend == TrendDirection.STABLE -> {
                sb.append("血压保持稳定。")
            }
            sbpTrend == TrendDirection.STABLE && dbpTrend == TrendDirection.STABLE -> {
                sb.append("血压控制良好，继续保持。")
            }
        }

        if (variability > 0.3f) {
            sb.append("血压波动较大，建议增加测量频率。")
        }

        return sb.toString()
    }
}