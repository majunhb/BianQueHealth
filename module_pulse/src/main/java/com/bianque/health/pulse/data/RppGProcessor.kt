package com.bianque.health.pulse.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * rPPG信号处理器 — 非接触式脉搏信号提取与心率计算。
 *
 * 实现POS算法（Plane-Orthogonal-to-Skin）：
 * 1. 空间平均 → RGB时间序列
 * 2. 时间归一化 → 消除直流分量
 * 3. 正交投影 → 消除镜面反射
 * 4. 带通滤波 → 0.75-3.0 Hz
 * 5. FFT频域分析 → 峰值频率 → BPM
 */
object RppGProcessor {

    /** 采样率（帧率） */
    private const val SAMPLE_RATE = 30f

    /** 心率频段 [Hz] */
    private const val HR_MIN_HZ = 0.75f  // 45 BPM
    private const val HR_MAX_HZ = 3.0f   // 180 BPM

    /** 最小有效帧数 */
    const val MIN_FRAMES = 90   // 30fps × 3秒

    /** 最大帧数（30秒） */
    const val MAX_FRAMES = 900  // 30fps × 30秒

    data class RppGResult(
        val heartRate: Int,           // 心率 BPM
        val signalQuality: Float,     // 信号质量 0-1
        val rawSignal: FloatArray,    // 滤波后信号
        val frequencySpectrum: FloatArray, // 频谱
        val confidence: Float         // 置信度
    )

    /**
     * 处理rPPG信号管线。
     * @param rgbFrames RGB帧序列，每个元素为 [R, G, B]
     * @return 心率结果
     */
    fun process(rgbFrames: List<FloatArray>): RppGResult? {
        if (rgbFrames.size < MIN_FRAMES) return null

        // 1. 提取RGB三通道时间序列
        val rSignal = FloatArray(rgbFrames.size)
        val gSignal = FloatArray(rgbFrames.size)
        val bSignal = FloatArray(rgbFrames.size)

        for (i in rgbFrames.indices) {
            rSignal[i] = rgbFrames[i][0]
            gSignal[i] = rgbFrames[i][1]
            bSignal[i] = rgbFrames[i][2]
        }

        // 2. 时间归一化：除以均值，消除直流分量
        val rMean = rSignal.sum() / rSignal.size
        val gMean = gSignal.sum() / gSignal.size
        val bMean = bSignal.sum() / bSignal.size

        val rNorm = FloatArray(rSignal.size) { rSignal[it] / rMean }
        val gNorm = FloatArray(gSignal.size) { gSignal[it] / gMean }
        val bNorm = FloatArray(bSignal.size) { bSignal[it] / bMean }

        // 3. POS算法：正交投影
        val posSignal = posAlgorithm(rNorm, gNorm, bNorm)

        // 4. 带通滤波
        val filtered = bandpassFilter(posSignal, HR_MIN_HZ, HR_MAX_HZ, SAMPLE_RATE)

        // 5. 信号质量评估
        val quality = evaluateSignalQuality(filtered)

        // 6. FFT频域分析
        val spectrum = computeFFT(filtered, SAMPLE_RATE)
        val peakFreq = findPeakFrequency(spectrum, HR_MIN_HZ, HR_MAX_HZ, SAMPLE_RATE, filtered.size)

        if (peakFreq <= 0) return null

        val heartRate = (peakFreq * 60f).toInt().coerceIn(40, 200)

        // 置信度
        val confidence = (quality * 0.6f + peakConfidence(spectrum, peakFreq, SAMPLE_RATE, filtered.size) * 0.4f)

        return RppGResult(
            heartRate = heartRate,
            signalQuality = quality,
            rawSignal = filtered,
            frequencySpectrum = spectrum,
            confidence = confidence.coerceIn(0.1f, 1f)
        )
    }

    /**
     * POS算法（Plane-Orthogonal-to-Skin）。
     * 将RGB信号投影到与皮肤平面正交的方向，消除镜面反射。
     */
    private fun posAlgorithm(rNorm: FloatArray, gNorm: FloatArray, bNorm: FloatArray): FloatArray {
        val n = rNorm.size
        val h = FloatArray(n)

        for (i in 0 until n) {
            // 投影矩阵
            // X = G - B
            val x = gNorm[i] - bNorm[i]
            // Y = G + B - 2*R
            val y = gNorm[i] + bNorm[i] - 2f * rNorm[i]

            // 自适应α：基于信号标准差
            val alpha = if (i > 0) {
                val stdX = computeStd(x, y, i)
                val stdY = computeStd(y, x, i)
                stdX / (stdY + 1e-6f)
            } else 0.5f

            h[i] = x - alpha * y
        }

        return h
    }

    private fun computeStd(x: Float, y: Float, n: Int): Float {
        // 简化：返回当前值的绝对值比例
        return abs(x) / (abs(y) + 1e-6f)
    }

    /**
     * 巴特沃斯带通滤波器（二阶IIR实现）。
     */
    private fun bandpassFilter(signal: FloatArray, lowFreq: Float, highFreq: Float, sampleRate: Float): FloatArray {
        val n = signal.size
        val filtered = FloatArray(n)

        // 归一化截止频率
        val wLow = 2f * PI.toFloat() * lowFreq / sampleRate
        val wHigh = 2f * PI.toFloat() * highFreq / sampleRate

        // 二阶Butterworth带通滤波器系数
        val bw = wHigh - wLow
        val w0 = sqrt(wLow * wHigh)
        val q = w0 / bw

        val alpha = sin(w0) / (2f * q)
        val cosW0 = cos(w0)

        val b0 = alpha
        val b1 = 0f
        val b2 = -alpha
        val a0 = 1f + alpha
        val a1 = -2f * cosW0
        val a2 = 1f - alpha

        // 归一化系数
        val b0n = b0 / a0
        val b1n = b1 / a0
        val b2n = b2 / a0
        val a1n = a1 / a0
        val a2n = a2 / a0

        // 应用滤波器
        var x1 = 0f; var x2 = 0f
        var y1 = 0f; var y2 = 0f

        for (i in 0 until n) {
            val x0 = signal[i]
            val y0 = b0n * x0 + b1n * x1 + b2n * x2 - a1n * y1 - a2n * y2
            filtered[i] = y0
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
        }

        return filtered
    }

    private fun sin(x: Float): Float = kotlin.math.sin(x.toDouble()).toFloat()

    /**
     * 信号质量评估。
     * 基于信号标准差和峰度。
     */
    private fun evaluateSignalQuality(signal: FloatArray): Float {
        val n = signal.size
        val mean = signal.sum() / n
        var variance = 0f
        for (v in signal) {
            val diff = v - mean
            variance += diff * diff
        }
        variance /= n
        val std = sqrt(variance)

        // 理想信号标准差应在合理范围
        return if (std in 0.005f..0.05f) {
            1f - abs(std - 0.015f) / 0.035f
        } else if (std < 0.005f) {
            std / 0.005f
        } else {
            0.5f
        }.coerceIn(0f, 1f)
    }

    /**
     * 简化FFT（使用DFT实现，适合小数据量）。
     */
    private fun computeFFT(signal: FloatArray, sampleRate: Float): FloatArray {
        val n = signal.size
        val spectrum = FloatArray(n / 2)
        for (k in 0 until n / 2) {
            var real = 0f
            var imag = 0f
            for (i in 0 until n) {
                val angle = 2f * PI.toFloat() * k * i / n
                real += signal[i] * cos(angle)
                imag -= signal[i] * sin(angle)
            }
            spectrum[k] = sqrt(real * real + imag * imag) / n
        }
        return spectrum
    }

    /**
     * 在心率频段内查找频谱峰值频率。
     */
    private fun findPeakFrequency(
        spectrum: FloatArray,
        minFreq: Float,
        maxFreq: Float,
        sampleRate: Float,
        signalLength: Int
    ): Float {
        val freqResolution = sampleRate / signalLength
        val minIdx = (minFreq / freqResolution).toInt().coerceAtLeast(1)
        val maxIdx = (maxFreq / freqResolution).toInt().coerceAtMost(spectrum.size - 1)

        if (maxIdx <= minIdx) return 0f

        var maxVal = 0f
        var maxIdxFound = minIdx
        for (i in minIdx..maxIdx) {
            if (spectrum[i] > maxVal) {
                maxVal = spectrum[i]
                maxIdxFound = i
            }
        }

        return maxIdxFound * freqResolution
    }

    /**
     * 峰值置信度：峰值相对于平均值的倍数。
     */
    private fun peakConfidence(
        spectrum: FloatArray,
        peakFreq: Float,
        sampleRate: Float,
        signalLength: Int
    ): Float {
        val freqResolution = sampleRate / signalLength
        val peakIdx = (peakFreq / freqResolution).toInt().coerceIn(0, spectrum.size - 1)
        if (peakIdx >= spectrum.size) return 0f

        val peakVal = spectrum[peakIdx]
        val meanVal = spectrum.sum() / spectrum.size

        return if (meanVal > 0) {
            (peakVal / meanVal / 3f).coerceIn(0f, 1f)
        } else 0f
    }
}