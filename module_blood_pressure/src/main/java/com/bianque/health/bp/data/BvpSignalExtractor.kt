package com.bianque.health.bp.data

import android.graphics.Bitmap
import timber.log.Timber
import kotlin.math.sqrt

/**
 * 血容量脉搏（BVP）波形提取器。
 *
 * 采用 POS（Plane-Orthogonal-to-Skin）算法从 RGB 视频帧中提取 rPPG 信号。
 * 核心流程：
 * 1. 逐帧提取 ROI 区域（额头 + 双脸颊）RGB 均值
 * 2. 时间归一化（除以滑动窗口均值）
 * 3. POS 正交投影（消除镜面反射和运动伪影）
 * 4. 带通滤波 (0.75–3.0 Hz，对应 45–180 BPM)
 *
 * 参考: Wang et al., "Algorithmic Principles of Remote PPG", IEEE TBME 2017
 */
object BvpSignalExtractor {

    /** 有效心率范围对应的带通频率 */
    private const val BP_LOW_CUTOFF = 0.75f   // 45 BPM
    private const val BP_HIGH_CUTOFF = 3.0f   // 180 BPM

    /** 信号提取结果 */
    data class BvpResult(
        val bvpSignal: FloatArray,          // 提取的 BVP 波形
        val heartRate: Int,                  // 心率 BPM
        val signalQuality: Float,            // 信号质量 0-1
        val snr: Float,                      // 信噪比 dB
        val rawRgbSignals: List<FloatArray>  // 原始 RGB 三通道信号 [R, G, B]
    )

    /**
     * 从帧序列中提取 ROI 区域的 RGB 均值。
     * 优先使用多 ROI（额头 + 双脸颊），皮肤像素过滤。
     */
    fun extractRoiRgb(bitmap: Bitmap): FloatArray? {
        return extractMultiRoi(bitmap)
    }

    /**
     * 多 ROI 提取：额头区域（顶部 25%）+ 左脸颊 + 右脸颊。
     */
    private fun extractMultiRoi(bitmap: Bitmap): FloatArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 20 || h < 20) return null

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var count = 0

        // 额头区域：y 在 0 ~ h*0.25，x 在 w*0.2 ~ w*0.8
        val foreheadTop = 0
        val foreheadBottom = (h * 0.25f).toInt()
        val foreheadLeft = (w * 0.2f).toInt()
        val foreheadRight = (w * 0.8f).toInt()

        for (y in foreheadTop until foreheadBottom step 2) {
            for (x in foreheadLeft until foreheadRight step 2) {
                val pixel = pixels[y * w + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (isSkinPixel(r, g, b)) {
                    sumR += r
                    sumG += g
                    sumB += b
                    count++
                }
            }
        }

        // 左脸颊：y 在 h*0.25 ~ h*0.55，x 在 w*0.05 ~ w*0.35
        val cheekTop = (h * 0.25f).toInt()
        val cheekBottom = (h * 0.55f).toInt()
        for (y in cheekTop until cheekBottom step 3) {
            for (x in (w * 0.05f).toInt() until (w * 0.35f).toInt() step 3) {
                val pixel = pixels[y * w + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (isSkinPixel(r, g, b)) {
                    sumR += r
                    sumG += g
                    sumB += b
                    count++
                }
            }
        }

        // 右脸颊
        for (y in cheekTop until cheekBottom step 3) {
            for (x in (w * 0.65f).toInt() until (w * 0.95f).toInt() step 3) {
                val pixel = pixels[y * w + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (isSkinPixel(r, g, b)) {
                    sumR += r
                    sumG += g
                    sumB += b
                    count++
                }
            }
        }

        if (count < 100) return null

        return floatArrayOf(sumR / count, sumG / count, sumB / count)
    }

    /**
     * 皮肤像素过滤（YCrCb 阈值）。
     */
    private fun isSkinPixel(r: Int, g: Int, b: Int): Boolean {
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val cr = 0.5f * r - 0.4187f * g - 0.0813f * b + 128f
        val cb = -0.1687f * r - 0.3313f * g + 0.5f * b + 128f
        return y > 80 && cr in 135f..180f && cb in 85f..135f
    }

    /**
     * 从 RGB 帧序列提取 BVP 波形。
     *
     * @param frames 连续视频帧列表
     * @param sampleRate 采样率（fps），默认 30
     * @return BvpResult 或 null（帧数不足）
     */
    fun extract(frames: List<Bitmap>, sampleRate: Float = 30f): BvpResult? {
        val minFrames = (sampleRate * 3).toInt()  // 至少 3 秒
        if (frames.size < minFrames) {
            Timber.d("BvpSignalExtractor: insufficient frames: %d < %d", frames.size, minFrames)
            return null
        }

        // 1. 逐帧提取 RGB 均值
        val rawR = mutableListOf<Float>()
        val rawG = mutableListOf<Float>()
        val rawB = mutableListOf<Float>()

        for (frame in frames) {
            val rgb = extractRoiRgb(frame)
            if (rgb != null) {
                rawR.add(rgb[0])
                rawG.add(rgb[1])
                rawB.add(rgb[2])
            }
        }

        if (rawR.size < minFrames) {
            Timber.d("BvpSignalExtractor: insufficient valid frames: %d", rawR.size)
            return null
        }

        // 2. 时间归一化
        val r = normalize(rawR.toFloatArray())
        val g = normalize(rawG.toFloatArray())
        val b = normalize(rawB.toFloatArray())

        val n = r.size

        // 3. POS 算法正交投影
        // 投影矩阵 P = [0, 1, -1; -2, 1, 1] / sqrt(6)
        val posSignal = FloatArray(n)
        for (i in 0 until n) {
            // Xs = G - B
            val xs = g[i] - b[i]
            // Ys = -2*R + G + B
            val ys = -2f * r[i] + g[i] + b[i]
            // α = std(Xs) / std(Ys)
            // 简化: h = Xs + α * Ys
            posSignal[i] = xs + ys
        }

        // 4. 带通滤波
        val filtered = butterworthBandpass(posSignal, sampleRate, BP_LOW_CUTOFF, BP_HIGH_CUTOFF)

        // 5. 信号质量评估
        val quality = evaluateSignalQuality(filtered)

        // 6. 频域分析提取心率
        val heartRate = estimateHeartRate(filtered, sampleRate) ?: 0
        val snr = computeSnr(filtered, sampleRate, heartRate)

        // 7. 构建 BVP 信号（归一化到 [-1, 1]）
        val maxAbs = filtered.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
        val bvpSignal = FloatArray(filtered.size) { filtered[it] / maxAbs }

        return BvpResult(
            bvpSignal = bvpSignal,
            heartRate = heartRate,
            signalQuality = quality,
            snr = snr,
            rawRgbSignals = listOf(r, g, b)
        )
    }

    // ==================== 私有方法 ====================

    private fun normalize(signal: FloatArray): FloatArray {
        val mean = signal.average().toFloat()
        return FloatArray(signal.size) { signal[it] / mean - 1f }
    }

    /**
     * 二阶 Butterworth 带通滤波器（双线性变换）。
     * 简化实现：级联低通 + 高通。
     */
    private fun butterworthBandpass(
        signal: FloatArray,
        sampleRate: Float,
        lowCutoff: Float,
        highCutoff: Float
    ): FloatArray {
        val n = signal.size
        if (n < 4) return signal

        val result = signal.copyOf()

        // 低通滤波（截止频率 highCutoff）
        val dt = 1f / sampleRate
        val rc = 1f / (2f * Math.PI.toFloat() * highCutoff)
        val alpha = dt / (rc + dt)

        // 前向低通
        for (i in 1 until n) {
            result[i] = result[i - 1] + alpha * (result[i] - result[i - 1])
        }
        // 反向低通（零相位）
        for (i in n - 2 downTo 0) {
            result[i] = result[i + 1] + alpha * (result[i] - result[i + 1])
        }

        // 高通滤波（截止频率 lowCutoff）
        val rcHigh = 1f / (2f * Math.PI.toFloat() * lowCutoff)
        val alphaHigh = rcHigh / (rcHigh + dt)

        // 前向高通
        var prev = result[0]
        for (i in 1 until n) {
            val filtered = alphaHigh * (result[i - 1] + result[i] - prev)
            prev = result[i]
            result[i] = filtered
        }
        // 反向高通
        prev = result[n - 1]
        for (i in n - 2 downTo 0) {
            val filtered = alphaHigh * (result[i + 1] + result[i] - prev)
            prev = result[i]
            result[i] = filtered
        }

        return result
    }

    /**
     * 使用 FFT 频域分析估计心率。
     */
    private fun estimateHeartRate(signal: FloatArray, sampleRate: Float): Int? {
        val n = signal.size
        if (n < 8) return null

        // 零填充到 2 的幂次
        val paddedSize = nextPowerOfTwo(n)
        val padded = FloatArray(paddedSize)
        System.arraycopy(signal, 0, padded, 0, n)

        // 汉宁窗
        for (i in 0 until n) {
            padded[i] = padded[i] * (0.5f - 0.5f * Math.cos(2.0 * Math.PI * i / (n - 1)).toFloat())
        }

        // DFT（简化 FFT）
        val spectrum = computePowerSpectrum(padded, paddedSize, sampleRate)
        val freqResolution = sampleRate / paddedSize

        // 在 0.75–3.0 Hz 范围内找峰值
        val lowBin = (BP_LOW_CUTOFF / freqResolution).toInt().coerceAtLeast(0)
        val highBin = (BP_HIGH_CUTOFF / freqResolution).toInt().coerceAtMost(paddedSize / 2)

        var maxPower = 0f
        var peakBin = lowBin
        for (i in lowBin..highBin) {
            if (spectrum[i] > maxPower) {
                maxPower = spectrum[i]
                peakBin = i
            }
        }

        if (maxPower < 1e-4f) return null

        val freq = peakBin * freqResolution
        return (freq * 60f).toInt().coerceIn(40, 180)
    }

    private fun computePowerSpectrum(data: FloatArray, size: Int, sampleRate: Float): FloatArray {
        val halfSize = size / 2
        val spectrum = FloatArray(halfSize + 1)

        for (k in 0..halfSize) {
            var real = 0f
            var imag = 0f
            for (n in 0 until size) {
                val angle = -2.0 * Math.PI * k * n / size
                real += data[n] * Math.cos(angle).toFloat()
                imag += data[n] * Math.sin(angle).toFloat()
            }
            spectrum[k] = (real * real + imag * imag) / (size * size)
        }
        return spectrum
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var power = 1
        while (power < n) power = power shl 1
        return power
    }

    /**
     * 信号质量评估：基于周期性、幅值稳定性。
     */
    private fun evaluateSignalQuality(signal: FloatArray): Float {
        val n = signal.size
        if (n < 10) return 0f

        val mean = signal.average().toFloat()
        val variance = signal.map { (it - mean) * (it - mean) }.average().toFloat()

        if (variance < 1e-6f) return 0f

        // 自相关峰值检测
        val maxLag = (n / 2).coerceAtMost(60)
        var maxCorrelation = 0f
        for (lag in 10 until maxLag) {
            var corr = 0f
            var count = 0
            for (i in 0 until n - lag) {
                corr += (signal[i] - mean) * (signal[i + lag] - mean)
                count++
            }
            if (count > 0) {
                corr = corr / (count * variance)
                if (corr > maxCorrelation) maxCorrelation = corr
            }
        }

        return (maxCorrelation * 0.7f + 0.3f).coerceIn(0f, 1f)
    }

    private fun computeSnr(signal: FloatArray, sampleRate: Float, heartRate: Int): Float {
        if (heartRate <= 0) return 0f
        val n = signal.size
        if (n < 8) return 0f

        val paddedSize = nextPowerOfTwo(n)
        val padded = FloatArray(paddedSize)
        System.arraycopy(signal, 0, padded, 0, n)
        val spectrum = computePowerSpectrum(padded, paddedSize, sampleRate)
        val freqResolution = sampleRate / paddedSize

        val signalBin = ((heartRate / 60f) / freqResolution).toInt().coerceIn(0, spectrum.size - 1)
        val signalPower = (0..2).sumOf { spectrum[(signalBin + it).coerceIn(0, spectrum.size - 1)].toDouble() }.toFloat()

        val totalPower = spectrum.sum()
        val noisePower = (totalPower - signalPower).coerceAtLeast(1e-6f)

        return (10f * kotlin.math.log10((signalPower / noisePower).toDouble()).toFloat()).coerceIn(0f, 30f)
    }
}