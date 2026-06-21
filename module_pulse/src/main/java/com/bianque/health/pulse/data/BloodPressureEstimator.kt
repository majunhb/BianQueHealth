package com.bianque.health.pulse.data

import kotlin.math.sqrt

/**
 * 非接触式血压估算器。
 *
 * 基于rPPG信号波形特征进行血压回归估算：
 * 1. 从脉搏波信号中提取形态学特征
 * 2. 使用线性回归模型估算收缩压/舒张压
 *
 * 注意：此为光学估算，仅供日常健康参考。
 */
object BloodPressureEstimator {

    data class BpFeatures(
        val pulseRate: Float,          // 心率
        val amplitude: Float,          // 信号幅值
        val risetime: Float,           // 上升时间（ms）
        val systolicTime: Float,       // 收缩期占比
        val pulseWidth: Float,         // 脉宽
        val stiffness: Float,          // 动脉僵硬指数
        val reflectionIndex: Float     // 反射波指数
    )

    data class BpResult(
        val systolic: Int,    // 收缩压 mmHg
        val diastolic: Int,   // 舒张压 mmHg
        val confidence: Float // 置信度
    )

    /**
     * 从rPPG信号估算血压。
     * @param signal 滤波后的rPPG信号
     * @param heartRate 心率 BPM
     * @param sampleRate 采样率 Hz
     */
    fun estimate(signal: FloatArray, heartRate: Int, sampleRate: Float): BpResult {
        val features = extractFeatures(signal, heartRate.toFloat(), sampleRate)
        return regressBp(features)
    }

    /**
     * 提取脉搏波形态学特征。
     */
    private fun extractFeatures(signal: FloatArray, pulseRate: Float, sampleRate: Float): BpFeatures {
        val n = signal.size
        val mean = signal.sum() / n

        // 幅值
        var maxVal = signal[0]
        var minVal = signal[0]
        for (v in signal) {
            if (v > maxVal) maxVal = v
            if (v < minVal) minVal = v
        }
        val amplitude = maxVal - minVal

        // 上升时间估算：基于心率周期
        val periodMs = 60000f / pulseRate.coerceAtLeast(1f)
        val risetime = periodMs * 0.3f  // 典型收缩期占30%

        // 收缩期占比
        val systolicTime = 0.33f + (pulseRate - 60f) * 0.001f

        // 脉宽
        val pulseWidth = periodMs * 0.35f

        // 动脉僵硬指数：基于最大斜率
        var maxSlope = 0f
        for (i in 1 until n) {
            val slope = abs(signal[i] - signal[i - 1])
            if (slope > maxSlope) maxSlope = slope
        }
        val stiffness = (maxSlope / amplitude).coerceIn(0.5f, 3f)

        // 反射波指数：基于信号后半段的能量占比
        var firstHalfEnergy = 0f
        var secondHalfEnergy = 0f
        for (i in 0 until n) {
            if (i < n / 2) firstHalfEnergy += signal[i] * signal[i]
            else secondHalfEnergy += signal[i] * signal[i]
        }
        val reflectionIndex = if (firstHalfEnergy > 0) {
            (secondHalfEnergy / firstHalfEnergy).coerceIn(0.3f, 1.5f)
        } else 0.8f

        return BpFeatures(
            pulseRate = pulseRate,
            amplitude = amplitude,
            risetime = risetime,
            systolicTime = systolicTime,
            pulseWidth = pulseWidth,
            stiffness = stiffness,
            reflectionIndex = reflectionIndex
        )
    }

    /**
     * 线性回归模型：将特征映射为血压值。
     * 基于MIMIC等公开数据集的经验公式。
     *
     * SBP ≈ 90 + 0.5*HR + 800*amplitude + 120*stiffness - 30*reflectionIndex
     * DBP ≈ 55 + 0.3*HR + 400*amplitude + 60*stiffness - 15*reflectionIndex
     */
    private fun regressBp(features: BpFeatures): BpResult {
        val systolic = (90f
                + 0.5f * features.pulseRate
                + 800f * features.amplitude
                + 120f * features.stiffness
                - 30f * features.reflectionIndex
                ).toInt().coerceIn(80, 200)

        val diastolic = (55f
                + 0.3f * features.pulseRate
                + 400f * features.amplitude
                + 60f * features.stiffness
                - 15f * features.reflectionIndex
                ).toInt().coerceIn(40, 130)

        // 确保收缩压 > 舒张压
        val validSBP = if (systolic > diastolic) systolic else diastolic + 20
        val validDBP = if (diastolic < systolic) diastolic else systolic - 20

        val confidence = when {
            features.amplitude > 0.02f && features.stiffness in 1f..2.5f -> 0.8f
            features.amplitude > 0.005f -> 0.55f
            else -> 0.3f
        }

        return BpResult(
            systolic = validSBP.coerceIn(80, 200),
            diastolic = validDBP.coerceIn(40, 130),
            confidence = confidence
        )
    }
}