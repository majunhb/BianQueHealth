package com.bianque.health.pulse.data

import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 三部九候（Three Positions, Nine Indicators）脉诊模拟器。
 *
 * 基于中医经典《黄帝内经》三部九候脉诊理论，通过对PPG信号进行频域分解和
 * 幅值分析，模拟寸、关、尺三个部位的浮、中、沉三候脉象特征。
 *
 * 三部：
 * - 寸(CUN)：对应于远端/头部，通过高频成分分析
 * - 关(GUAN)：对应于中段/胸腹，通过中频成分分析
 * - 尺(CHI)：对应于近端/下肢，通过低频成分分析
 *
 * 每部三候：
 * - 浮(FU)：  高频成分（>2Hz），对应于浅层脉象
 * - 中(ZHONG)：中频成分（1-2Hz），对应于中层脉象
 * - 沉(CHEN)： 低频成分（<1Hz），对应于深层脉象
 */
object SanJiaoSimulator {

    /** 三部位置 */
    enum class Position {
        CUN,   // 寸
        GUAN,  // 关
        CHI    // 尺
    }

    /** 三候层次 */
    enum class Level {
        FU,    // 浮
        ZHONG, // 中
        CHEN   // 沉
    }

    /** 频段划分 [Hz] */
    private const val HIGH_FREQ_THRESHOLD = 2.0f   // >2Hz 为高频（浮）
    private const val MID_FREQ_MIN = 1.0f          // 1-2Hz 为中频（中）
    private const val LOW_FREQ_THRESHOLD = 1.0f    // <1Hz 为低频（沉）

    /** 寸关尺频率权重 - 模拟不同部位的频率响应特征 */
    private val CUN_WEIGHTS = floatArrayOf(0.6f, 0.3f, 0.1f)   // 寸偏高频
    private val GUAN_WEIGHTS = floatArrayOf(0.3f, 0.5f, 0.2f)  // 关偏中频
    private val CHI_WEIGHTS = floatArrayOf(0.1f, 0.3f, 0.6f)   // 尺偏低频

    /** 脉象描述词库 */
    private val QUALITY_STRENGTH = listOf("有力", "无力")
    private val QUALITY_DEPTH = listOf("浮", "沉", "不浮不沉")
    private val QUALITY_SPEED = listOf("迟", "数", "缓")
    private val QUALITY_SMOOTH = listOf("滑", "涩")
    private val QUALITY_SHAPE = listOf("弦", "细", "正常")

    /**
     * 三部九候模拟结果。
     */
    data class SanJiaoResult(
        /** 脉象结果映射：位置 → 层次 → 脉象质量描述 */
        val pulseMap: Map<Position, Map<Level, String>>,
        /** 总体脉象评估 */
        val overallAssessment: String,
        /** 每部每候的定量评分 */
        val quantitativeScores: Map<Position, Map<Level, Float>>
    )

    /**
     * 执行三部九候脉诊模拟。
     *
     * @param rrIntervals RR间期序列 [ms]
     * @param amplitudes 信号幅值序列（对应每个RR间期）
     * @param sampleRate 采样率 [Hz]
     * @return 三部九候模拟结果，数据不足则返回null
     */
    fun simulate(
        rrIntervals: List<Float>,
        amplitudes: List<Float>,
        sampleRate: Float
    ): SanJiaoResult? {
        if (rrIntervals.size < 10 || amplitudes.size < 10) {
            Timber.w("SanJiaoSimulator: insufficient data, rr=${rrIntervals.size}, amp=${amplitudes.size}")
            return null
        }

        Timber.d("SanJiaoSimulator: simulating with ${rrIntervals.size} intervals at ${sampleRate}Hz")

        // 信号预处理：构建时间序列并重采样
        val signal = buildTimeSeries(rrIntervals, amplitudes, sampleRate)

        // 频域分解：将信号分解为高频、中频、低频三个分量
        val (highFreq, midFreq, lowFreq) = decomposeFrequency(signal, sampleRate)

        // 幅值分析
        val overallAmplitude = computeMeanAmplitude(amplitudes)
        val amplitudeVariation = computeAmplitudeVariation(amplitudes)

        // 心率分析
        val meanHr = if (rrIntervals.isNotEmpty()) {
            60000f / (rrIntervals.sum() / rrIntervals.size)
        } else 75f

        // 心率变异性
        val hrVariation = computeHrVariation(rrIntervals)

        // 对每个部位进行三候分析
        val pulseMap = mutableMapOf<Position, Map<Level, String>>()
        val quantitativeScores = mutableMapOf<Position, Map<Level, Float>>()

        for (position in Position.values()) {
            val weights = getWeightsForPosition(position)

            val fuLevel = analyzeLevel(
                highFreq, midFreq, lowFreq, weights,
                Level.FU, overallAmplitude, amplitudeVariation, meanHr, hrVariation
            )
            val zhongLevel = analyzeLevel(
                highFreq, midFreq, lowFreq, weights,
                Level.ZHONG, overallAmplitude, amplitudeVariation, meanHr, hrVariation
            )
            val chenLevel = analyzeLevel(
                highFreq, midFreq, lowFreq, weights,
                Level.CHEN, overallAmplitude, amplitudeVariation, meanHr, hrVariation
            )

            pulseMap[position] = mapOf(
                Level.FU to fuLevel.first,
                Level.ZHONG to zhongLevel.first,
                Level.CHEN to chenLevel.first
            )

            quantitativeScores[position] = mapOf(
                Level.FU to fuLevel.second,
                Level.ZHONG to zhongLevel.second,
                Level.CHEN to chenLevel.second
            )
        }

        // 总体评估
        val overallAssessment = generateOverallAssessment(pulseMap, meanHr, overallAmplitude)

        val result = SanJiaoResult(
            pulseMap = pulseMap,
            overallAssessment = overallAssessment,
            quantitativeScores = quantitativeScores
        )

        Timber.d("SanJiaoSimulator: completed - assessment=${overallAssessment}")
        return result
    }

    /**
     * 构建时间序列：将RR间期和幅值合并为均匀采样的时间序列。
     */
    private fun buildTimeSeries(
        rrIntervals: List<Float>,
        amplitudes: List<Float>,
        sampleRate: Float
    ): FloatArray {
        val n = rrIntervals.size.coerceAtMost(amplitudes.size)

        // 构建累积时间
        val times = FloatArray(n)
        var cumulativeTime = 0f
        for (i in 0 until n) {
            times[i] = cumulativeTime
            cumulativeTime += rrIntervals[i] / 1000f
        }

        val totalDuration = cumulativeTime
        val resampledCount = (totalDuration * sampleRate).toInt().coerceAtLeast(64)
        val resampled = FloatArray(resampledCount)

        val dt = 1f / sampleRate
        var idx = 0

        for (i in 0 until resampledCount) {
            val targetTime = i * dt
            while (idx < n - 1 && times[idx + 1] < targetTime) idx++

            resampled[i] = if (idx >= n - 1) {
                amplitudes.lastOrNull() ?: 0f
            } else {
                val t0 = times[idx]
                val t1 = times[idx + 1]
                val v0 = amplitudes[idx]
                val v1 = amplitudes[idx + 1]
                if (t1 - t0 > 0) {
                    val ratio = ((targetTime - t0) / (t1 - t0)).coerceIn(0f, 1f)
                    v0 + (v1 - v0) * ratio
                } else v0
            }
        }

        return resampled
    }

    /**
     * 频域分解：使用FFT将信号分解为高频、中频、低频三个分量。
     */
    private fun decomposeFrequency(
        signal: FloatArray,
        sampleRate: Float
    ): Triple<Float, Float, Float> {
        val n = signal.size

        // 补零到2的幂
        val paddedSize = nextPowerOfTwo(n)
        val paddedSignal = DoubleArray(paddedSize)
        for (i in 0 until n) {
            paddedSignal[i] = signal[i].toDouble()
        }

        // Apache Commons Math FFT
        val transformer = FastFourierTransformer(DftNormalization.STANDARD)
        val complexResult = transformer.transform(paddedSignal, TransformType.FORWARD)

        val freqResolution = sampleRate / paddedSize
        val halfSize = paddedSize / 2

        var lowFreqEnergy = 0f
        var midFreqEnergy = 0f
        var highFreqEnergy = 0f

        for (i in 0 until halfSize) {
            val freq = i * freqResolution
            val magnitude = complexResult[i].abs().toFloat()

            when {
                freq < LOW_FREQ_THRESHOLD -> lowFreqEnergy += magnitude
                freq in MID_FREQ_MIN..HIGH_FREQ_THRESHOLD -> midFreqEnergy += magnitude
                freq > HIGH_FREQ_THRESHOLD && freq <= sampleRate / 2f -> highFreqEnergy += magnitude
            }
        }

        // 归一化
        val totalEnergy = lowFreqEnergy + midFreqEnergy + highFreqEnergy
        if (totalEnergy > 0) {
            lowFreqEnergy /= totalEnergy
            midFreqEnergy /= totalEnergy
            highFreqEnergy /= totalEnergy
        }

        return Triple(highFreqEnergy, midFreqEnergy, lowFreqEnergy)
    }

    /**
     * 获取指定部位的频率权重。
     */
    private fun getWeightsForPosition(position: Position): FloatArray {
        return when (position) {
            Position.CUN -> CUN_WEIGHTS
            Position.GUAN -> GUAN_WEIGHTS
            Position.CHI -> CHI_WEIGHTS
        }
    }

    /**
     * 分析某个部位某个层次的脉象。
     *
     * @return (脉象描述, 定量评分)
     */
    private fun analyzeLevel(
        highFreq: Float,
        midFreq: Float,
        lowFreq: Float,
        weights: FloatArray,
        level: Level,
        overallAmplitude: Float,
        amplitudeVariation: Float,
        meanHr: Float,
        hrVariation: Float
    ): Pair<String, Float> {
        // 根据层次选择对应的频率分量
        val freqComponent = when (level) {
            Level.FU -> highFreq
            Level.ZHONG -> midFreq
            Level.CHEN -> lowFreq
        }

        // 综合评分：频率分量加权
        val weightedFreq = highFreq * weights[0] + midFreq * weights[1] + lowFreq * weights[2]
        val score = (weightedFreq * 0.5f + freqComponent * 0.5f).coerceIn(0f, 1f)

        // 生成脉象描述
        val description = buildDescription(score, overallAmplitude, amplitudeVariation, meanHr, hrVariation)

        return Pair(description, score)
    }

    /**
     * 根据定量指标生成脉象质量描述。
     */
    private fun buildDescription(
        score: Float,
        overallAmplitude: Float,
        amplitudeVariation: Float,
        meanHr: Float,
        hrVariation: Float
    ): String {
        val parts = mutableListOf<String>()

        // 有力/无力
        when {
            overallAmplitude > 0.04f -> parts.add("有力")
            overallAmplitude > 0.015f -> parts.add("力度适中")
            else -> parts.add("无力")
        }

        // 浮/沉
        when {
            score > 0.6f -> parts.add("浮")
            score < 0.25f -> parts.add("沉")
            else -> parts.add("不浮不沉")
        }

        // 迟/数/缓
        when {
            meanHr < 60 -> parts.add("迟")
            meanHr > 90 -> parts.add("数")
            else -> parts.add("缓")
        }

        // 滑/涩
        when {
            amplitudeVariation < 0.3f -> parts.add("滑")
            amplitudeVariation > 0.7f -> parts.add("涩")
            // 中间情况不特别标注
        }

        // 弦/细
        when {
            hrVariation < 0.05f && overallAmplitude > 0.03f -> parts.add("弦")
            overallAmplitude < 0.01f -> parts.add("细")
        }

        // 如果只有力度描述，添加"正常"
        if (parts.size <= 1) {
            parts.add("脉象平和")
        }

        return parts.joinToString("，")
    }

    /**
     * 计算平均幅值。
     */
    private fun computeMeanAmplitude(amplitudes: List<Float>): Float {
        if (amplitudes.isEmpty()) return 0f
        return amplitudes.sum() / amplitudes.size
    }

    /**
     * 计算幅值变异系数（标准差/均值）。
     */
    private fun computeAmplitudeVariation(amplitudes: List<Float>): Float {
        if (amplitudes.size < 2) return 0f
        val mean = computeMeanAmplitude(amplitudes)
        if (mean == 0f) return 0f
        var sumSq = 0f
        for (a in amplitudes) {
            val diff = a - mean
            sumSq += diff * diff
        }
        val std = sqrt(sumSq / amplitudes.size)
        return (std / mean).coerceIn(0f, 1f)
    }

    /**
     * 计算心率变异性（变异系数）。
     */
    private fun computeHrVariation(rrIntervals: List<Float>): Float {
        if (rrIntervals.size < 2) return 0f
        val mean = rrIntervals.sum() / rrIntervals.size
        if (mean == 0f) return 0f
        var sumSq = 0f
        for (rr in rrIntervals) {
            val diff = rr - mean
            sumSq += diff * diff
        }
        val std = sqrt(sumSq / rrIntervals.size)
        return (std / mean).coerceIn(0f, 1f)
    }

    /**
     * 生成总体脉象评估。
     */
    private fun generateOverallAssessment(
        pulseMap: Map<Position, Map<Level, String>>,
        meanHr: Float,
        overallAmplitude: Float
    ): String {
        val sb = StringBuilder()

        // 总结三部脉象
        for (position in Position.values()) {
            val levels = pulseMap[position] ?: continue
            val positionName = when (position) {
                Position.CUN -> "寸"
                Position.GUAN -> "关"
                Position.CHI -> "尺"
            }
            sb.append("$positionName：")
            val descs = mutableListOf<String>()
            for (level in Level.values()) {
                val levelName = when (level) {
                    Level.FU -> "浮"
                    Level.ZHONG -> "中"
                    Level.CHEN -> "沉"
                }
                descs.add("${levelName}取${levels[level]}")
            }
            sb.append(descs.joinToString("；"))
            sb.append("。")
        }

        // 综合判断
        sb.append("综合：")
        when {
            meanHr < 60 -> sb.append("脉率偏慢，")
            meanHr > 90 -> sb.append("脉率偏快，")
            else -> sb.append("脉率正常，")
        }
        when {
            overallAmplitude > 0.04f -> sb.append("脉象有力，气血充盈。")
            overallAmplitude > 0.015f -> sb.append("脉象力度适中。")
            else -> sb.append("脉象无力，气血不足之象。")
        }

        return sb.toString()
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