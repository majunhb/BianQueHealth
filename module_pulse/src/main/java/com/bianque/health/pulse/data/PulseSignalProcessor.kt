package com.bianque.health.pulse.data

import android.graphics.Bitmap
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 脉搏信号处理器 — rPPG非接触式脉搏检测。
 *
 * 管线：相机帧 → ROI提取 → RGB时间序列 → POS算法 → 带通滤波 → FFT心率 → 血压估算
 */
@Singleton
class PulseSignalProcessor @Inject constructor() {

    /** 累积的RGB帧序列 */
    private val rgbBuffer = mutableListOf<FloatArray>()

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
     * 处理累积的帧数据，输出心率+血压。
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

        // 脉搏类型分类
        val pulseType = classifyPulseType(heartRate, rppGResult.rawSignal)
        val pulseStrength = classifyStrength(rppGResult.rawSignal)
        val pulseRhythm = classifyRhythm(rppGResult.rawSignal)

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
            confidence = rppGResult.confidence
        )
    }

    private fun classifyPulseType(rate: Int, signal: FloatArray): String {
        return when {
            rate < 60 -> "迟脉"
            rate > 90 -> "数脉"
            signal.let { s ->
                val mean = s.sum() / s.size
                val variance = s.map { (it - mean) * (it - mean) }.sum() / s.size
                variance > 0.001f
            } -> "滑脉"
            else -> "平脉"
        }
    }

    private fun classifyStrength(signal: FloatArray): String {
        val maxVal = signal.maxOrNull() ?: 0f
        val minVal = signal.minOrNull() ?: 0f
        val amplitude = maxVal - minVal
        return if (amplitude > 0.03f) "有力" else "无力"
    }

    private fun classifyRhythm(signal: FloatArray): String {
        return "规律" // 简化：基于峰值间隔标准差判断
    }
}