package com.bianque.health.pulse.data

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * rPPG信号处理器 -- 非接触式脉搏信号提取与心率计算。
 *
 * 实现POS算法（Plane-Orthogonal-to-Skin）：
 * 1. 空间平均 -> RGB时间序列
 * 2. 时间归一化 -> 消除直流分量
 * 3. 正交投影 -> 消除镜面反射
 * 4. 带通滤波 -> 0.75-3.0 Hz
 * 5. FFT频域分析 -> 峰值频率 -> BPM
 *
 * 使用Apache Commons Math3的FastFourierTransformer进行FFT运算。
 */
object RppGProcessor {

    /** 采样率（帧率） */
    private const val SAMPLE_RATE = 30f

    /** 心率频段 [Hz] */
    private const val HR_MIN_HZ = 0.75f  // 45 BPM
    private const val HR_MAX_HZ = 3.0f   // 180 BPM

    /** 最小有效帧数 */
    const val MIN_FRAMES = 90   // 30fps x 3秒

    /** 最大帧数（30秒） */
    const val MAX_FRAMES = 900  // 30fps x 30秒

    /** HRV重采样率 [Hz] */
    private const val HRV_RESAMPLE_RATE = 4f

    data class RppGResult(
        val heartRate: Int,           // 心率 BPM
        val signalQuality: Float,     // 信号质量 0-1
        val rawSignal: FloatArray,    // 滤波后信号
        val frequencySpectrum: FloatArray, // 频谱
        val confidence: Float         // 置信度
    )

    /**
     * 综合脉诊分析结果，包含HRV和三部九候信息。
     */
    data class PulseAnalysisResult(
        val hr: Int,                                  // 心率 BPM
        val hrvResult: HrvAnalyzer.HrvResult?,        // HRV分析结果
        val sanJiaoResult: SanJiaoSimulator.SanJiaoResult?, // 三部九候结果
        val pulseType: String,                        // 脉象类型
        val confidence: Float,                        // 综合置信度
        val signalQuality: Float,                     // 信号质量
        val rawSignal: FloatArray,                    // 滤波后信号
        val frequencySpectrum: FloatArray             // 频谱
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

        // 6. FFT频域分析 - 使用Apache Commons Math FFT
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
     * 综合脉诊分析：一次性完成心率、HRV和三部九候分析。
     *
     * @param rgbFrames RGB帧序列
     * @return 综合脉诊分析结果
     */
    fun getFullAnalysis(rgbFrames: List<FloatArray>): PulseAnalysisResult? {
        if (rgbFrames.size < MIN_FRAMES) {
            Timber.w("RppGProcessor: insufficient frames for full analysis, count=${rgbFrames.size}")
            return null
        }

        Timber.d("RppGProcessor: starting full analysis with ${rgbFrames.size} frames")

        // 1. 基本rPPG处理
        val rppGResult = process(rgbFrames)
        if (rppGResult == null) {
            Timber.w("RppGProcessor: basic rPPG processing failed")
            return null
        }

        val hr = rppGResult.heartRate
        val filteredSignal = rppGResult.rawSignal
        val spectrum = rppGResult.frequencySpectrum

        // 2. 提取RR间期
        val rrIntervals = extractRRIntervals(filteredSignal, SAMPLE_RATE)
        Timber.d("RppGProcessor: extracted ${rrIntervals.size} RR intervals")

        // 3. HRV分析
        val hrvResult = if (rrIntervals.size >= 10) {
            HrvAnalyzer.analyze(rrIntervals, HRV_RESAMPLE_RATE)
        } else {
            Timber.w("RppGProcessor: insufficient RR intervals for HRV analysis")
            null
        }

        // 4. 提取幅值序列用于三部九候模拟
        val amplitudes = extractAmplitudes(filteredSignal, rrIntervals, SAMPLE_RATE)

        // 5. 三部九候模拟
        val sanJiaoResult = if (rrIntervals.size >= 10 && amplitudes.size >= 10) {
            SanJiaoSimulator.simulate(rrIntervals, amplitudes, SAMPLE_RATE)
        } else {
            Timber.w("RppGProcessor: insufficient data for SanJiao simulation")
            null
        }

        // 6. 脉象类型分类
        val pulseType = classifyPulseType(hr, filteredSignal, rrIntervals)

        // 7. 综合置信度
        val combinedConfidence = computeCombinedConfidence(
            rppGResult.confidence,
            hrvResult,
            sanJiaoResult
        )

        return PulseAnalysisResult(
            hr = hr,
            hrvResult = hrvResult,
            sanJiaoResult = sanJiaoResult,
            pulseType = pulseType,
            confidence = combinedConfidence,
            signalQuality = rppGResult.signalQuality,
            rawSignal = filteredSignal,
            frequencySpectrum = spectrum
        )
    }

    /**
     * 从PPG信号中提取RR间期序列。
     *
     * 委托给HrvAnalyzer的峰值检测方法，然后从峰值位置计算RR间期。
     *
     * @param signal PPG波形信号（已滤波）
     * @param sampleRate 采样率 [Hz]
     * @return RR间期序列 [ms]
     */
    fun extractRRIntervals(signal: FloatArray, sampleRate: Float): List<Float> {
        return HrvAnalyzer.extractRRIntervals(signal, sampleRate)
    }

    /**
     * 从滤波后的PPG信号中提取幅值序列。
     *
     * 对每个检测到的峰值，计算其幅值（峰值-基线），用于三部九候分析。
     */
    private fun extractAmplitudes(
        signal: FloatArray,
        rrIntervals: List<Float>,
        sampleRate: Float
    ): List<Float> {
        if (rrIntervals.isEmpty()) return emptyList()

        val peakIndices = findPeakIndices(signal, sampleRate)
        val amplitudes = mutableListOf<Float>()

        // 计算基线（信号均值）
        val baseline = signal.sum() / signal.size

        for (peakIdx in peakIndices) {
            if (peakIdx in signal.indices) {
                val amplitude = abs(signal[peakIdx] - baseline)
                amplitudes.add(amplitude)
            }
        }

        return amplitudes
    }

    /**
     * 查找PPG信号中的峰值索引。
     */
    private fun findPeakIndices(signal: FloatArray, sampleRate: Float): List<Int> {
        val n = signal.size
        if (n < 3) return emptyList()

        val mean = signal.sum() / n
        var variance = 0f
        for (v in signal) {
            val diff = v - mean
            variance += diff * diff
        }
        variance /= n
        val std = sqrt(variance)
        val threshold = mean + 0.5f * std

        val minPeakDistance = (sampleRate / 3.0f).toInt().coerceAtLeast(2)

        val peaks = mutableListOf<Int>()
        var lastPeakIdx = -minPeakDistance

        for (i in 1 until n - 1) {
            if (signal[i] > threshold &&
                signal[i] > signal[i - 1] &&
                signal[i] > signal[i + 1] &&
                i - lastPeakIdx >= minPeakDistance
            ) {
                peaks.add(i)
                lastPeakIdx = i
            }
        }

        return peaks
    }

    /**
     * 脉象类型分类（增强版，支持更多类型）。
     */
    private fun classifyPulseType(
        hr: Int,
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
        if (hr < 60) {
            return "迟脉"
        }

        // 数脉：心率 > 90
        if (hr > 90) {
            return "数脉"
        }

        // 滑脉：信号光滑，幅值适中，变异小
        if (variance < 0.001f && amplitude in 0.01f..0.04f) {
            return "滑脉"
        }

        return "平脉"
    }

    /**
     * 计算综合置信度。
     */
    private fun computeCombinedConfidence(
        rppGConfidence: Float,
        hrvResult: HrvAnalyzer.HrvResult?,
        sanJiaoResult: SanJiaoSimulator.SanJiaoResult?
    ): Float {
        var confidence = rppGConfidence * 0.5f

        if (hrvResult != null) {
            confidence += 0.25f
        }
        if (sanJiaoResult != null) {
            confidence += 0.25f
        }

        return confidence.coerceIn(0.1f, 1f)
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

            // 自适应alpha：基于信号标准差
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
     * FFT频域分析 -- 使用Apache Commons Math3 FastFourierTransformer。
     *
     * 将信号补零到2的幂后进行FFT，返回幅度谱。
     */
    private fun computeFFT(signal: FloatArray, sampleRate: Float): FloatArray {
        val n = signal.size

        // 补零到2的幂以提高FFT效率
        val paddedSize = nextPowerOfTwo(n)
        val paddedSignal = DoubleArray(paddedSize)
        for (i in 0 until n) {
            paddedSignal[i] = signal[i].toDouble()
        }

        // Apache Commons Math FFT
        val transformer = FastFourierTransformer(DftNormalization.STANDARD)
        val complexResult = transformer.transform(paddedSignal, TransformType.FORWARD)

        // 提取幅度谱（仅正频率部分）
        val halfSize = paddedSize / 2
        val spectrum = FloatArray(halfSize)
        for (i in 0 until halfSize) {
            spectrum[i] = complexResult[i].abs().toFloat()
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
        // 补零后的实际长度
        val paddedSize = nextPowerOfTwo(signalLength)
        val freqResolution = sampleRate / paddedSize
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
        val paddedSize = nextPowerOfTwo(signalLength)
        val freqResolution = sampleRate / paddedSize
        val peakIdx = (peakFreq / freqResolution).toInt().coerceIn(0, spectrum.size - 1)
        if (peakIdx >= spectrum.size) return 0f

        val peakVal = spectrum[peakIdx]
        val meanVal = spectrum.sum() / spectrum.size

        return if (meanVal > 0) {
            (peakVal / meanVal / 3f).coerceIn(0f, 1f)
        } else 0f
    }

    /**
     * 计算大于等于n的最小2的幂。
     */
    private fun nextPowerOfTwo(n: Int): Int {
        var value = 1
        while (value < n) {
            value = value shl 1
        }
        return value
    }
}