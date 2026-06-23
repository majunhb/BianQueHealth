package com.bianque.health.bp.data

import android.graphics.Bitmap
import timber.log.Timber

/**
 * 光感测压质控引擎。
 *
 * 实时评估每帧的采集质量，确保入库数据的准确性：
 * - 运动伪影检测（帧间亮度变化）
 * - 光照条件检查（过暗/过亮/不均匀）
 * - 皮肤 ROI 覆盖率检查
 * - 综合质量评分
 *
 * 对标医疗级 rPPG 设备的质控标准。
 */
object QualityControlEngine {

    /** 质控级别 */
    enum class QualityLevel {
        GOOD,        // 优良 — 可以采集
        ACCEPTABLE,  // 可接受 — 继续采集但做标记
        POOR,        // 差 — 提示用户调整
        REJECTED     // 不合格 — 阻断采集
    }

    /** 质控结果 */
    data class QcResult(
        val level: QualityLevel,
        val score: Float,                    // 综合评分 0-1
        val motionScore: Float,              // 运动评分 0-1
        val lightScore: Float,               // 光照评分 0-1
        val skinCoverage: Float,             // 皮肤覆盖率 0-1
        val message: String?,                // 用户提示（中文）
        val shouldReject: Boolean            // 是否应阻断
    )

    /** 光照阈值 */
    private const val MIN_BRIGHTNESS = 40f    // 最低亮度（过暗）
    private const val MAX_BRIGHTNESS = 230f   // 最高亮度（过曝）
    private const val MIN_SKIN_COVERAGE = 0.15f // 最低皮肤覆盖率

    // 上一帧的亮度均值（用于运动检测）
    private var lastFrameBrightness: Float = -1f
    private var consecutivePoorFrames = 0
    private const val MAX_POOR_FRAMES = 5

    /**
     * 对单帧进行质量评估。
     *
     * @param bitmap 当前帧
     * @param isFirstFrame 是否为第一帧（重置状态）
     */
    fun evaluateFrame(bitmap: Bitmap, isFirstFrame: Boolean = false): QcResult {
        if (isFirstFrame) {
            reset()
        }

        val w = bitmap.width
        val h = bitmap.height
        if (w < 20 || h < 20) {
            return QcResult(QualityLevel.REJECTED, 0f, 0f, 0f, 0f,
                "图像分辨率过低", true)
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. 光照评估
        val lightResult = evaluateLighting(pixels, w, h)
        val lightScore = lightResult.first
        val avgBrightness = lightResult.second

        // 2. 运动检测
        val motionScore = evaluateMotion(avgBrightness, isFirstFrame)

        // 3. 皮肤覆盖率
        val skinCoverage = evaluateSkinCoverage(pixels, w, h)

        // 4. 综合评分
        val score = (lightScore * 0.4f + motionScore * 0.35f + skinCoverage * 0.25f).coerceIn(0f, 1f)

        // 5. 判定级别
        val (level, message, shouldReject) = when {
            lightScore < 0.2f -> {
                consecutivePoorFrames++
                val msg = if (avgBrightness < MIN_BRIGHTNESS) "光线过暗，请移至明亮处"
                else "光线过强，请避免强光直射"
                Triple(QualityLevel.POOR, msg, consecutivePoorFrames > MAX_POOR_FRAMES)
            }
            motionScore < 0.3f -> {
                consecutivePoorFrames++
                Triple(QualityLevel.POOR, "请保持静止，避免头部晃动",
                    consecutivePoorFrames > MAX_POOR_FRAMES)
            }
            skinCoverage < MIN_SKIN_COVERAGE -> {
                consecutivePoorFrames++
                Triple(QualityLevel.POOR, "请将面部对准摄像头",
                    consecutivePoorFrames > MAX_POOR_FRAMES)
            }
            score < 0.5f -> {
                Triple(QualityLevel.ACCEPTABLE, if (motionScore < 0.6f) "请尽量保持静止" else null, false)
            }
            score < 0.7f -> {
                consecutivePoorFrames = 0
                Triple(QualityLevel.ACCEPTABLE, null, false)
            }
            else -> {
                consecutivePoorFrames = 0
                Triple(QualityLevel.GOOD, null, false)
            }
        }

        lastFrameBrightness = avgBrightness

        return QcResult(level, score, motionScore, lightScore, skinCoverage, message, shouldReject)
    }

    /**
     * 对整个帧序列做综合质控。
     */
    fun evaluateBatch(qcResults: List<QcResult>): QcResult {
        if (qcResults.isEmpty()) {
            return QcResult(QualityLevel.REJECTED, 0f, 0f, 0f, 0f,
                "未采集到有效帧", true)
        }

        val avgScore = qcResults.map { it.score }.average().toFloat()
        val avgMotion = qcResults.map { it.motionScore }.average().toFloat()
        val avgLight = qcResults.map { it.lightScore }.average().toFloat()
        val avgCoverage = qcResults.map { it.skinCoverage }.average().toFloat()

        val poorRatio = qcResults.count { it.level == QualityLevel.POOR || it.level == QualityLevel.REJECTED }
            .toFloat() / qcResults.size

        val level = when {
            poorRatio > 0.5f -> QualityLevel.REJECTED
            poorRatio > 0.3f -> QualityLevel.POOR
            avgScore < 0.5f -> QualityLevel.POOR
            avgScore < 0.7f -> QualityLevel.ACCEPTABLE
            else -> QualityLevel.GOOD
        }

        val message = when (level) {
            QualityLevel.REJECTED -> "信号质量不达标，请重新测量"
            QualityLevel.POOR -> "信号质量较差，建议重新测量"
            QualityLevel.ACCEPTABLE -> "信号质量可接受，结果仅供参考"
            QualityLevel.GOOD -> null
        }

        return QcResult(level, avgScore, avgMotion, avgLight, avgCoverage, message,
            level == QualityLevel.REJECTED)
    }

    fun reset() {
        lastFrameBrightness = -1f
        consecutivePoorFrames = 0
    }

    // ==================== 私有方法 ====================

    /**
     * 评估光照条件。
     * 返回 Pair(score, avgBrightness)
     */
    private fun evaluateLighting(pixels: IntArray, w: Int, h: Int): Pair<Float, Float> {
        val sampleCount = (w * h * 0.1f).toInt().coerceAtLeast(100)
        val step = (pixels.size / sampleCount).coerceAtLeast(1)

        var sumBrightness = 0f
        var sumSqBrightness = 0f
        var count = 0

        for (i in pixels.indices step step) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val brightness = 0.299f * r + 0.587f * g + 0.114f * b
            sumBrightness += brightness
            sumSqBrightness += brightness * brightness
            count++
        }

        if (count == 0) return Pair(0f, 0f)

        val avgBrightness = sumBrightness / count
        val variance = (sumSqBrightness / count) - (avgBrightness * avgBrightness)

        // 亮度评分
        val brightnessScore = when {
            avgBrightness < MIN_BRIGHTNESS -> avgBrightness / MIN_BRIGHTNESS * 0.5f
            avgBrightness > MAX_BRIGHTNESS -> 1f - (avgBrightness - MAX_BRIGHTNESS) / (255f - MAX_BRIGHTNESS)
            avgBrightness in 100f..180f -> 1f  // 理想亮度范围
            else -> 0.8f
        }.coerceIn(0f, 1f)

        // 均匀度评分
        val uniformityScore = (1f - (variance / 10000f)).coerceIn(0f, 1f)

        return Pair((brightnessScore * 0.6f + uniformityScore * 0.4f).coerceIn(0f, 1f), avgBrightness)
    }

    /**
     * 运动检测：帧间亮度变化。
     */
    private fun evaluateMotion(currentBrightness: Float, isFirstFrame: Boolean): Float {
        if (isFirstFrame || lastFrameBrightness < 0f) return 1f

        val diff = kotlin.math.abs(currentBrightness - lastFrameBrightness)
        return (1f - diff / 50f).coerceIn(0f, 1f)
    }

    /**
     * 皮肤覆盖率。
     */
    private fun evaluateSkinCoverage(pixels: IntArray, w: Int, h: Int): Float {
        val sampleCount = (w * h * 0.05f).toInt().coerceAtLeast(200)
        val step = (pixels.size / sampleCount).coerceAtLeast(1)

        var skinCount = 0
        var totalCount = 0

        for (i in pixels.indices step step) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val cr = 0.5f * r - 0.4187f * g - 0.0813f * b + 128f
            val cb = -0.1687f * r - 0.3313f * g + 0.5f * b + 128f

            if (y > 80 && cr in 135f..180f && cb in 85f..135f) {
                skinCount++
            }
            totalCount++
        }

        return if (totalCount > 0) skinCount.toFloat() / totalCount else 0f
    }
}