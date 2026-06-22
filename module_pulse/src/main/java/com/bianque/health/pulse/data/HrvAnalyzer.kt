package com.bianque.health.pulse.data

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import timber.log.Timber
import kotlin.math.sqrt

/**
 * 心率变异性（HRV）分析器。
 *
 * 基于FFT进行时域和频域HRV分析：
 * - 时域指标：SDNN、RMSSD、pNN50、Mean HR
 * - 频域指标：LF Power、HF Power、LF/HF Ratio、Total Power
 *
 * 使用Apache Commons Math3的FastFourierTransformer进行频域变换。
 */
object HrvAnalyzer {

    /** LF频段下限 [Hz] */
    private const val LF_MIN = 0.04f

    /** LF频段上限 [Hz] */
    private const val LF_MAX = 0.15f

    /** HF频段下限 [Hz] */
    private const val HF_MIN = 0.15f

    /** HF频段上限 [Hz] */
    private const val HF_MAX = 0.40f

    /** NN50差值阈值 [ms] */
    private const val NN50_THRESHOLD_MS = 50f

    /** 最小RR间期数 */
    private const val MIN_RR_COUNT = 10

    /**
     * HRV分析结果数据类。
     */
    data class HrvResult(
        /** 正常窦性心搏间期标准差 [ms] */
        val sdnn: Float,
        /** 相邻NN间期差值的均方根 [ms] */
        val rmssd: Float,
        /** 相邻NN间期差值>50ms的百分比 [%] */
        val pnn50: Float,
        /** 平均心率 [BPM] */
        val meanHr: Float,
        /** LF频段功率 [ms^2] */
        val lfPower: Float,
        /** HF频段功率 [ms^2] */
        val hfPower: Float,
        /** LF/HF比值 */
        val lfHfRatio: Float,
        /** 总功率 [ms^2] */
        val totalPower: Float,
        /** 自主神经平衡描述 */
        val autonomicBalance: String
    )

    /**
     * 对RR间期序列进行完整的HRV分析。
     *
     * @param rrIntervals RR间期序列 [ms]
     * @param sampleRate RR间期重采样率 [Hz]，通常为4Hz
     * @return HRV分析结果，数据不足则返回null
     */
    fun analyze(rrIntervals: List<Float>, sampleRate: Float): HrvResult? {
        if (rrIntervals.size < MIN_RR_COUNT) {
            Timber.w("HrvAnalyzer: insufficient RR intervals, count=${rrIntervals.size}")
            return null
        }

        Timber.d("HrvAnalyzer: analyzing ${rrIntervals.size} RR intervals at ${sampleRate}Hz")

        // ===== 时域分析 =====
        val sdnn = computeSdnn(rrIntervals)
        val rmssd = computeRmssd(rrIntervals)
        val pnn50 = computePnn50(rrIntervals)
        val meanHr = computeMeanHr(rrIntervals)

        // ===== 频域分析 =====
        // 对不均匀采样的RR间期进行线性插值重采样，得到均匀采样序列
        val resampledSignal = resampleRrIntervals(rrIntervals, sampleRate)
        val (lfPower, hfPower, lfHfRatio, totalPower) = computeFrequencyDomain(resampledSignal, sampleRate)

        // ===== 自主神经平衡评估 =====
        val autonomicBalance = assessAutonomicBalance(lfHfRatio, sdnn, rmssd)

        val result = HrvResult(
            sdnn = sdnn,
            rmssd = rmssd,
            pnn50 = pnn50,
            meanHr = meanHr,
            lfPower = lfPower,
            hfPower = hfPower,
            lfHfRatio = lfHfRatio,
            totalPower = totalPower,
            autonomicBalance = autonomicBalance
        )

        Timber.d("HrvAnalyzer: result - SDNN=%.1f, RMSSD=%.1f, pNN50=%.1f, " +
                "HR=%.1f, LF=%.1f, HF=%.1f, LF/HF=%.2f, TP=%.1f, balance=%s",
            sdnn, rmssd, pnn50, meanHr, lfPower, hfPower, lfHfRatio, totalPower, autonomicBalance)

        return result
    }

    /**
     * 从PPG信号中提取RR间期序列。
     *
     * 流程：
     * 1. 对信号进行带通滤波
     * 2. 检测峰值位置
     * 3. 计算相邻峰值之间的时间间隔
     *
     * @param signal PPG波形信号（已滤波）
     * @param sampleRate 采样率 [Hz]
     * @return RR间期序列 [ms]
     */
    fun extractRRIntervals(signal: FloatArray, sampleRate: Float): List<Float> {
        if (signal.size < 2) return emptyList()

        // 检测峰值
        val peakIndices = findPeaks(signal)
        if (peakIndices.size < 2) {
            Timber.w("HrvAnalyzer: insufficient peaks found, count=${peakIndices.size}")
            return emptyList()
        }

        // 计算RR间期（转换为毫秒）
        val rrIntervals = mutableListOf<Float>()
        for (i in 1 until peakIndices.size) {
            val intervalSamples = (peakIndices[i] - peakIndices[i - 1]).toFloat()
            val intervalMs = intervalSamples / sampleRate * 1000f
            // 过滤异常值：生理范围 300ms ~ 2000ms (30-200 BPM)
            if (intervalMs in 300f..2000f) {
                rrIntervals.add(intervalMs)
            }
        }

        Timber.d("HrvAnalyzer: extracted ${rrIntervals.size} RR intervals from ${peakIndices.size} peaks")
        return rrIntervals
    }

    /**
     * 峰值检测：找到信号中所有局部最大值。
     *
     * 使用自适应阈值法：
     * 1. 计算信号均值作为基线
     * 2. 找到高于基线的局部最大值
     * 3. 应用最小峰间距约束
     */
    private fun findPeaks(signal: FloatArray): List<Int> {
        val n = signal.size
        if (n < 3) return emptyList()

        // 计算均值和标准差
        val mean = signal.sum() / n
        var variance = 0f
        for (v in signal) {
            val diff = v - mean
            variance += diff * diff
        }
        variance /= n
        val std = sqrt(variance)

        // 阈值：均值 + 0.5*标准差
        val threshold = mean + 0.5f * std

        // 最小峰间距：对应最大心率 200 BPM 的采样点数
        val minPeakDistance = (n / (200f / 60f * n / signal.size)).toInt().coerceAtLeast(2)

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

    // ==================== 时域指标计算 ====================

    /**
     * 计算SDNN：正常窦性心搏间期标准差。
     * SDNN反映总体的HRV，正常范围 50-100ms。
     */
    private fun computeSdnn(rrIntervals: List<Float>): Float {
        val mean = rrIntervals.sum() / rrIntervals.size
        var sumSq = 0f
        for (rr in rrIntervals) {
            val diff = rr - mean
            sumSq += diff * diff
        }
        return sqrt(sumSq / rrIntervals.size)
    }

    /**
     * 计算RMSSD：相邻NN间期差值的均方根。
     * RMSSD主要反映副交感神经活性，正常范围 20-50ms。
     */
    private fun computeRmssd(rrIntervals: List<Float>): Float {
        if (rrIntervals.size < 2) return 0f
        var sumSq = 0f
        for (i in 1 until rrIntervals.size) {
            val diff = rrIntervals[i] - rrIntervals[i - 1]
            sumSq += diff * diff
        }
        return sqrt(sumSq / (rrIntervals.size - 1))
    }

    /**
     * 计算pNN50：相邻NN间期差值>50ms的百分比。
     * pNN50反映副交感神经活性，正常范围 5-30%。
     */
    private fun computePnn50(rrIntervals: List<Float>): Float {
        if (rrIntervals.size < 2) return 0f
        var count = 0
        for (i in 1 until rrIntervals.size) {
            val diff = kotlin.math.abs(rrIntervals[i] - rrIntervals[i - 1])
            if (diff > NN50_THRESHOLD_MS) count++
        }
        return count.toFloat() / (rrIntervals.size - 1) * 100f
    }

    /**
     * 计算平均心率。
     */
    private fun computeMeanHr(rrIntervals: List<Float>): Float {
        val meanRR = rrIntervals.sum() / rrIntervals.size
        return if (meanRR > 0) 60000f / meanRR else 0f
    }

    // ==================== 频域分析 ====================

    /**
     * 对不均匀采样的RR间期进行线性插值重采样。
     *
     * 将RR间期序列转换为等间隔的时间序列，便于FFT分析。
     */
    private fun resampleRrIntervals(rrIntervals: List<Float>, sampleRate: Float): FloatArray {
        // 构建累积时间序列
        val n = rrIntervals.size
        val times = FloatArray(n)
        var cumulativeTime = 0f
        for (i in 0 until n) {
            times[i] = cumulativeTime
            cumulativeTime += rrIntervals[i] / 1000f // 转换为秒
        }

        // 目标采样点数
        val totalDuration = cumulativeTime
        val resampledCount = (totalDuration * sampleRate).toInt().coerceAtLeast(64)
        val resampled = FloatArray(resampledCount)

        val dt = 1f / sampleRate
        var rrIdx = 0

        for (i in 0 until resampledCount) {
            val targetTime = i * dt

            // 找到目标时间所在的区间
            while (rrIdx < n - 1 && times[rrIdx + 1] < targetTime) {
                rrIdx++
            }

            if (rrIdx >= n - 1) {
                // 超出范围，使用最后一个值
                resampled[i] = rrIntervals.last()
            } else {
                // 线性插值
                val t0 = times[rrIdx]
                val t1 = times[rrIdx + 1]
                val v0 = rrIntervals[rrIdx]
                val v1 = rrIntervals[rrIdx + 1]

                if (t1 - t0 > 0) {
                    val ratio = (targetTime - t0) / (t1 - t0)
                    resampled[i] = v0 + (v1 - v0) * ratio.coerceIn(0f, 1f)
                } else {
                    resampled[i] = v0
                }
            }
        }

        // 去均值（去直流分量）
        val mean = resampled.sum() / resampledCount
        for (i in 0 until resampledCount) {
            resampled[i] -= mean
        }

        // 应用汉宁窗减少频谱泄漏
        for (i in 0 until resampledCount) {
            val window = 0.5f * (1f - kotlin.math.cos(2f * kotlin.math.PI.toFloat() * i / (resampledCount - 1)).toFloat())
            resampled[i] *= window
        }

        return resampled
    }

    /**
     * 使用Apache Commons Math FFT进行频域分析。
     *
     * 返回 (lfPower, hfPower, lfHfRatio, totalPower)
     */
    private fun computeFrequencyDomain(
        signal: FloatArray,
        sampleRate: Float
    ): Quadruple {
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

        // 计算功率谱密度
        val freqResolution = sampleRate / paddedSize
        val halfSize = paddedSize / 2

        var lfPower = 0f
        var hfPower = 0f
        var totalPower = 0f

        for (i in 0 until halfSize) {
            val freq = i * freqResolution
            val power = (complexResult[i].abs() * complexResult[i].abs()).toFloat()

            // 只计算到0.5Hz（Nyquist限制实际不需要，但HRV分析通常关注<0.5Hz）
            if (freq <= 0.5f) {
                totalPower += power

                when {
                    freq >= LF_MIN && freq < LF_MAX -> lfPower += power
                    freq >= HF_MIN && freq <= HF_MAX -> hfPower += power
                }
            }
        }

        // 归一化功率（乘以频率分辨率）
        lfPower *= freqResolution
        hfPower *= freqResolution
        totalPower *= freqResolution

        // LF/HF比值
        val lfHfRatio = if (hfPower > 0) lfPower / hfPower else Float.MAX_VALUE

        return Quadruple(lfPower, hfPower, lfHfRatio, totalPower)
    }

    /**
     * 评估自主神经平衡状态。
     */
    private fun assessAutonomicBalance(lfHfRatio: Float, sdnn: Float, rmssd: Float): String {
        return when {
            sdnn < 20f && rmssd < 15f -> "自主神经功能显著降低，HRV严重不足"
            lfHfRatio > 3.0f -> "交感神经占优势，可能存在压力或紧张状态"
            lfHfRatio < 0.5f -> "副交感神经占优势，处于放松状态"
            lfHfRatio in 1.5f..3.0f -> "交感-副交感轻度失衡，交感神经略占优势"
            lfHfRatio in 0.5f..1.5f -> "自主神经平衡良好"
            sdnn > 100f -> "HRV偏高，自主神经活跃"
            else -> "自主神经功能正常"
        }
    }

    // ==================== 工具函数 ====================

    /**
     * 四元组数据类，用于频域分析结果返回。
     */
    private data class Quadruple(
        val lfPower: Float,
        val hfPower: Float,
        val lfHfRatio: Float,
        val totalPower: Float
    )

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