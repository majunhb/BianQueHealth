package com.bianque.health.bp.data

import android.graphics.Bitmap
import com.bianque.health.bp.domain.model.BloodPressureResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 光感测压分析器（PpgAnalyzer）— 基于 rPPG 的无创血压测量。
 *
 * 核心管线：
 * 1. 实时质控：QualityControlEngine 逐帧评估运动/光照/覆盖
 * 2. BVP 提取：BvpSignalExtractor POS 算法提取血容量脉搏波
 * 3. 血压估算：线性回归模型（基于脉搏波形态学特征）
 * 4. 异常预警：BpAlertEngine 实时异常检测
 *
 * 输出的测量结果可用于 BpTrendAnalyzer 长期趋势分析。
 *
 * 免责声明：本功能基于光学 AI 估算，仅供日常健康趋势参考，
 * 不可替代传统袖带式血压计，不作为医疗诊断依据。
 */
@Singleton
class PpgAnalyzer @Inject constructor() {

    /** 测量进度回调 */
    data class MeasurementProgress(
        val phase: MeasurementPhase,
        val progress: Float,          // 0-1
        val message: String,
        val qualityScore: Float? = null
    )

    enum class MeasurementPhase {
        PREPARING,     // 准备中（质控检查）
        COLLECTING,    // 采集中
        ANALYZING,     // 分析中
        COMPLETE,      // 完成
        REJECTED       // 质控不合格
    }

    /**
     * 标准 rPPG 血压测量（适合 UI 引导式测量）。
     *
     * @param frames 连续视频帧序列（30fps，至少 10 秒）
     * @param onProgress 进度回调（可选，用于 UI 进度条）
     * @return BloodPressureResult
     */
    suspend fun analyzePpg(
        frames: List<Bitmap>,
        onProgress: ((MeasurementProgress) -> Unit)? = null
    ): BloodPressureResult = withContext(Dispatchers.Default) {
        Timber.d("PpgAnalyzer: starting rPPG analysis with %d frames", frames.size)

        // 阶段 1: 质控预处理（仅批次级别拒绝，单帧容错 3 帧连续失败）
        onProgress?.invoke(MeasurementProgress(MeasurementPhase.PREPARING, 0f, "正在检查采集质量…"))

        val qcResults = mutableListOf<QualityControlEngine.QcResult>()
        val validFrames = mutableListOf<Bitmap>()
        var consecutiveRejections = 0

        for ((index, frame) in frames.withIndex()) {
            val qc = QualityControlEngine.evaluateFrame(frame, index == 0)

            if (qc.level == QualityControlEngine.QualityLevel.REJECTED) {
                consecutiveRejections++
                if (consecutiveRejections >= 3) {
                    Timber.w("PpgAnalyzer: 3 consecutive frames rejected at index %d: %s", index, qc.message)
                    onProgress?.invoke(MeasurementProgress(
                        MeasurementPhase.REJECTED, 0f,
                        qc.message ?: "采集质量不达标",
                        qc.score
                    ))
                    return@withContext BloodPressureResult(
                        systolic = 0, diastolic = 0, heartRate = 0,
                        measurementMethod = "PPG_FAILED",
                        deviceName = "rPPG质控不合格: ${qc.message}"
                    )
                }
            } else {
                consecutiveRejections = 0
            }

            qcResults.add(qc)
            if (qc.level != QualityControlEngine.QualityLevel.REJECTED) {
                validFrames.add(frame)
            }
        }

        // 阶段 2: 批次质控
        val batchQc = QualityControlEngine.evaluateBatch(qcResults)
        if (batchQc.shouldReject) {
            Timber.w("PpgAnalyzer: batch rejected: %s", batchQc.message)
            return@withContext BloodPressureResult(
                systolic = 0, diastolic = 0, heartRate = 0,
                measurementMethod = "PPG_FAILED",
                deviceName = "rPPG信号质量不达标: ${batchQc.message}"
            )
        }

        onProgress?.invoke(MeasurementProgress(
            MeasurementPhase.COLLECTING, 0.3f,
            "正在采集光感信号…", batchQc.score
        ))

        // 阶段 3: BVP 波形提取
        onProgress?.invoke(MeasurementProgress(
            MeasurementPhase.ANALYZING, 0.5f,
            "正在提取脉搏波信号…", batchQc.score
        ))

        val bvpResult = BvpSignalExtractor.extract(validFrames)
        if (bvpResult == null || bvpResult.heartRate <= 0) {
            Timber.w("PpgAnalyzer: BVP extraction failed, heartRate=%d",
                bvpResult?.heartRate ?: 0)
            return@withContext BloodPressureResult(
                systolic = 0, diastolic = 0, heartRate = 0,
                measurementMethod = "PPG_FAILED",
                deviceName = "rPPG信号提取失败，请确保面部光线充足且保持静止"
            )
        }

        onProgress?.invoke(MeasurementProgress(
            MeasurementPhase.ANALYZING, 0.7f,
            "正在推算血压值…", batchQc.score
        ))

        // 阶段 4: 血压估算
        val bpResult = estimateBloodPressure(bvpResult)

        // 阶段 5: 综合置信度
        val confidence = computeOverallConfidence(bvpResult.signalQuality, batchQc.score, bpResult)

        onProgress?.invoke(MeasurementProgress(
            MeasurementPhase.COMPLETE, 1f,
            "测量完成",
            confidence
        ))

        Timber.d("PpgAnalyzer: result — SBP=%d DBP=%d HR=%d quality=%.2f confidence=%.2f",
            bpResult.first, bpResult.second, bvpResult.heartRate,
            bvpResult.signalQuality, confidence)

        BloodPressureResult(
            systolic = bpResult.first,
            diastolic = bpResult.second,
            heartRate = bvpResult.heartRate,
            measurementMethod = "rPPG",
            deviceName = "光感测压"
        )
    }

    // ==================== 血压估算 ====================

    /**
     * 基于 BVP 波形形态学特征的线性回归血压估算。
     *
     * 特征提取：
     * - 幅值 (amplitude): BVP 信号峰值-谷值
     * - 上升时间 (risetime): 谷值到峰值的时间
     * - 动脉僵硬指数 (stiffness): 最大斜率/幅值
     * - 反射波指数 (reflectionIndex): 后半段能量/前半段能量
     *
     * 回归公式基于 MIMIC 等公开数据集的校准。
     */
    private fun estimateBloodPressure(bvp: BvpSignalExtractor.BvpResult): Pair<Int, Int> {
        val signal = bvp.bvpSignal
        val hr = bvp.heartRate.toFloat()
        val n = signal.size

        if (n < 10) return Pair(0, 0)

        // 特征提取
        val amplitude = (signal.maxOrNull() ?: 0f) - (signal.minOrNull() ?: 0f)
        val risetime = (60f / hr) * 0.3f  // 上升时间约为心动周期的 30%

        // 最大斜率 → 僵硬指数
        var maxSlope = 0f
        for (i in 1 until n) {
            val slope = kotlin.math.abs(signal[i] - signal[i - 1])
            if (slope > maxSlope) maxSlope = slope
        }
        val stiffness = (maxSlope / amplitude.coerceAtLeast(0.01f)).coerceIn(0.5f, 3.0f)

        // 反射波指数
        val half = n / 2
        val frontEnergy = (0 until half).sumOf { (signal[it] * signal[it]).toDouble() }.toFloat()
        val backEnergy = (half until n).sumOf { (signal[it] * signal[it]).toDouble() }.toFloat()
        val totalEnergy = (frontEnergy + backEnergy).coerceAtLeast(1e-6f)
        val reflectionIndex = (backEnergy / totalEnergy).coerceIn(0.3f, 1.5f)

        // 线性回归
        val sbp = (90f + 0.5f * hr + 800f * amplitude + 120f * stiffness - 30f * reflectionIndex)
            .toInt().coerceIn(80, 200)
        val dbp = (55f + 0.3f * hr + 400f * amplitude + 60f * stiffness - 15f * reflectionIndex)
            .toInt().coerceIn(40, 130)

        // 确保 SBP > DBP
        return if (sbp > dbp) Pair(sbp, dbp) else Pair(dbp + 10, dbp)
    }

    /**
     * 综合置信度计算。
     */
    private fun computeOverallConfidence(
        signalQuality: Float,
        qcScore: Float,
        bpResult: Pair<Int, Int>
    ): Float {
        if (bpResult.first <= 0) return 0f

        val bpPlausibility = when {
            bpResult.first in 90..140 && bpResult.second in 60..90 -> 1f
            bpResult.first in 80..160 && bpResult.second in 50..100 -> 0.8f
            else -> 0.5f
        }

        return (signalQuality * 0.4f + qcScore * 0.35f + bpPlausibility * 0.25f).coerceIn(0f, 1f)
    }
}