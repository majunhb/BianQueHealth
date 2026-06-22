package com.bianque.health.pulse.data

import android.graphics.Bitmap
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * 脉搏信号处理器 -- rPPG非接触式脉搏检测。
 *
 * 管线：相机帧 -> ROI提取 -> RGB时间序列 -> POS算法 -> 带通滤波 -> FFT心率 -> HRV分析 -> 三部九候 -> 血压估算
 *
 * 集成HrvAnalyzer和SanJiaoSimulator，提供全面的脉诊分析能力。
 */
@Singleton
class PulseSignalProcessor @Inject constructor() {

    /** 累积的RGB帧序列 */
    private val rgbBuffer = mutableListOf<FloatArray>()

    /** HRV重采样率 [Hz] */
    companion object {
        private const val HRV_RESAMPLE_RATE = 4f
    }

    fun reset() {
        rgbBuffer.clear()
    }

    fun getFrameCount(): Int = rgbBuffer.size

    /**
     * 添加一帧，提取ROI的RGB均值。
     * @return 该帧的RGB均值，如果未检测到皮肤则返回null
     */
    suspend fun addFrame(bitmap: Bitmap): FloatArray? = withContext(Dispatchers.Default) {
        val rgb = RppGather.extractMultiRoiRgb(bitmap)
        if (rgb != null) {
            rgbBuffer.add(rgb)
        }
        rgb
    }

    /**
     * 处理累积的帧数据，输出心率+血压+基础脉象分类。
     */
    suspend fun processSignal(): PulseDiagnosisResult? = withContext(Dispatchers.Default) {
        if (rgbBuffer.size < RppGProcessor.MIN_FRAMES) {
            Timber.d("PulseSignalProcessor: frames=${rgbBuffer.size}, need>=${RppGProcessor.MIN_FRAMES}")
            return@withContext null
        }

        Timber.d("PulseSignalProcessor: processing ${rgbBuffer.size} frames...")

        val rppGResult = RppGProcessor.process(rgbBuffer.toList())
        if (rppGResult == null) {
            Timber.w("PulseSignalProcessor: rPPG processing failed")
            return@withContext null
        }

        val heartRate = rppGResult.heartRate
        Timber.d("PulseSignalProcessor: HR=$heartRate BPM, quality=${rppGResult.signalQuality}, conf=${rppGResult.confidence}")

        // 血压估算
        val bpResult = BloodPressureEstimator.estimate(
            rppGResult.rawSignal, heartRate, 30f
        )

        // 提取RR间期
        val rrIntervals = RppGProcessor.extractRRIntervals(rppGResult.rawSignal, 30f)

        // 脉搏类型分类（增强版）
        val pulseType = classifyPulseType(heartRate, rppGResult.rawSignal, rrIntervals)
        val pulseStrength = classifyStrength(rppGResult.rawSignal)
        val pulseRhythm = classifyRhythm(rrIntervals)

        val features = mapOf(
            "amplitude" to (rppGResult.signalQuality),
            "frequency" to (heartRate.toFloat() / 100f),
            "stiffness" to bpResult.confidence
        )

        PulseDiagnosisResult(
            pulseRate = heartRate,
            pulseRhythm = pulseRhythm,
            pulseStrength = pulseStrength,
            pulseType = pulseType,
            systolic = bpResult.systolic,
            diastolic = bpResult.diastolic,
            pulseFeatures = features,
            confidence = rppGResult.confidence,
            hrvSdnn = 0f,
            hrvRmssd = 0f,
            hrvLfHfRatio = 0f,
            sanJiaoResult = null,
            autonomicBalance = ""
        )
    }

    /**
     * 全面脉诊分析：包含HRV和三部九候的完整分析结果。
     *
     * 与processSignal()相比，此方法额外执行：
     * - FFT-based HRV时域和频域分析
     * - 三部九候脉诊模拟
     * - 增强型脉象分类
     *
     * @return 包含HRV和三部九候信息的完整诊断结果
     */
    suspend fun analyzeFull(): PulseDiagnosisResult? = withContext(Dispatchers.Default) {
        if (rgbBuffer.size < RppGProcessor.MIN_FRAMES) {
            Timber.d("PulseSignalProcessor: insufficient frames for full analysis, count=${rgbBuffer.size}")
            return@withContext null
        }

        Timber.d("PulseSignalProcessor: starting full analysis with ${rgbBuffer.size} frames")

        // 使用RppGProcessor的综合分析方法
        val fullResult = RppGProcessor.getFullAnalysis(rgbBuffer.toList())
        if (fullResult == null) {
            Timber.w("PulseSignalProcessor: full analysis failed")
            return@withContext null
        }

        val heartRate = fullResult.hr
        val hrvResult = fullResult.hrvResult
        val sanJiaoResult = fullResult.sanJiaoResult

        // 血压估算
        val bpResult = BloodPressureEstimator.estimate(
            fullResult.rawSignal, heartRate, 30f
        )

        // 提取RR间期用于节律分析
        val rrIntervals = RppGProcessor.extractRRIntervals(fullResult.rawSignal, 30f)

        // 脉搏类型分类
        val pulseType = fullResult.pulseType
        val pulseStrength = classifyStrength(fullResult.rawSignal)
        val pulseRhythm = classifyRhythm(rrIntervals)

        // 提取HRV指标
        val hrvSdnn = hrvResult?.sdnn ?: 0f
        val hrvRmssd = hrvResult?.rmssd ?: 0f
        val hrvLfHfRatio = hrvResult?.lfHfRatio ?: 0f
        val autonomicBalance = hrvResult?.autonomicBalance ?: ""

        val features = buildPulseFeatures(fullResult, bpResult, hrvResult)

        Timber.d("PulseSignalProcessor: full analysis complete - HR=$heartRate, type=$pulseType, " +
                "SDNN=%.1f, RMSSD=%.1f, LF/HF=%.2f, balance=$autonomicBalance",
            hrvSdnn, hrvRmssd, hrvLfHfRatio)

        PulseDiagnosisResult(
            pulseRate = heartRate,
            pulseRhythm = pulseRhythm,
            pulseStrength = pulseStrength,
            pulseType = pulseType,
            systolic = bpResult.systolic,
            diastolic = bpResult.diastolic,
            pulseFeatures = features,
            confidence = fullResult.confidence,
            hrvSdnn = hrvSdnn,
            hrvRmssd = hrvRmssd,
            hrvLfHfRatio = hrvLfHfRatio,
            sanJiaoResult = sanJiaoResult,
            autonomicBalance = autonomicBalance
        )
    }

    /**
     * 构建综合脉象特征映射。
     */
    private fun buildPulseFeatures(
        fullResult: RppGProcessor.PulseAnalysisResult,
        bpResult: BloodPressureEstimator.BpResult,
        hrvResult: HrvAnalyzer.HrvResult?
    ): Map<String, Float> {
        val features = mutableMapOf<String, Float>()

        features["amplitude"] = fullResult.signalQuality
        features["frequency"] = fullResult.hr.toFloat() / 100f
        features["stiffness"] = bpResult.confidence
        features["signalQuality"] = fullResult.signalQuality

        if (hrvResult != null) {
            features["sdnn"] = hrvResult.sdnn / 100f  // 归一化
            features["rmssd"] = hrvResult.rmssd / 100f
            features["pnn50"] = hrvResult.pnn50 / 100f
            features["lfPower"] = hrvResult.lfPower / 1000f
            features["hfPower"] = hrvResult.hfPower / 1000f
            features["lfHfRatio"] = (hrvResult.lfHfRatio / 10f).coerceIn(0f, 1f)
            features["totalPower"] = hrvResult.totalPower / 10000f
        }

        return features
    }

    /**
     * 增强版脉象类型分类。
     *
     * 支持类型：结代脉、涩脉、弦脉、细脉、迟脉、数脉、滑脉、平脉
     */
    private fun classifyPulseType(
        rate: Int,
        signal: FloatArray,
        rrIntervals: List<Float>
    ): String {
        // 计算信号特征
        val mean = signal.sum() / signal.size
        var variance = 0f
        for (v in signal) {
            val diff = v - mean
            variance += diff * diff
        }
        variance /= signal.size
        val signalStd = sqrt(variance)
        val amplitude = (signal.maxOrNull() ?: 0f) - (signal.minOrNull() ?: 0f)

        // 计算RR间期变异性
        var rrVariation = 0f
        if (rrIntervals.size >= 2) {
            val rrMean = rrIntervals.sum() / rrIntervals.size
            var rrSumSq = 0f
            for (rr in rrIntervals) {
                val diff = rr - rrMean
                rrSumSq += diff * diff
            }
            rrVariation = sqrt(rrSumSq / rrIntervals.size) / rrMean
        }

        // 结代脉：RR间期变异大，表示心律不齐
        if (rrVariation > 0.15f) {
            return "结代脉"
        }

        // 涩脉：信号幅值小且变异大，表示血流不畅
        if (amplitude < 0.01f && variance > 0.0005f) {
            return "涩脉"
        }

        // 弦脉：信号幅值大且标准差小，表示血管紧张
        if (amplitude > 0.04f && signalStd < 0.015f) {
            return "弦脉"
        }

        // 细脉：信号幅值小，表示气血不足
        if (amplitude < 0.008f) {
            return "细脉"
        }

        // 迟脉：心率 < 60
        if (rate < 60) {
            return "迟脉"
        }

        // 数脉：心率 > 90
        if (rate > 90) {
            return "数脉"
        }

        // 滑脉：信号光滑，幅值适中，变异小
        if (variance < 0.001f && amplitude in 0.01f..0.04f) {
            return "滑脉"
        }

        return "平脉"
    }

    /**
     * 脉象力度分类。
     */
    private fun classifyStrength(signal: FloatArray): String {
        val maxVal = signal.maxOrNull() ?: 0f
        val minVal = signal.minOrNull() ?: 0f
        val amplitude = maxVal - minVal
        return if (amplitude > 0.03f) "有力" else "无力"
    }

    /**
     * 脉象节律分类。
     *
     * 基于RR间期的变异系数判断节律是否整齐。
     */
    private fun classifyRhythm(rrIntervals: List<Float>): String {
        if (rrIntervals.size < 2) return "规律"

        val mean = rrIntervals.sum() / rrIntervals.size
        var sumSq = 0f
        for (rr in rrIntervals) {
            val diff = rr - mean
            sumSq += diff * diff
        }
        val std = sqrt(sumSq / rrIntervals.size)
        val cv = if (mean > 0) std / mean else 0f

        // 变异系数 > 10% 视为不齐
        return if (cv > 0.1f) "不齐" else "规律"
    }
}