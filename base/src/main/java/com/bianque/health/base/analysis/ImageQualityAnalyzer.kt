package com.bianque.health.base.analysis

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

/**
 * 图像质量评估器 — 基于设计文档的质量评估算法。
 *
 * 用于舌诊/面诊的实时帧质量检测，实现：
 * - 光照均匀度评估（HSV V通道标准差归一化）
 * - 清晰度评估（Laplacian方差归一化）
 * - 面积占比评估（舌体/面部像素占比）
 * - 综合质量评分：0.4 × light_uniformity + 0.3 × sharpness + 0.3 × area_ratio
 */
object ImageQualityAnalyzer {

    /** 检测状态 */
    enum class DetectionState {
        NOT_DETECTED,   // 未检测到目标
        POOR_QUALITY,   // 检测到但质量不佳
        READY           // 对焦成功，可扫描
    }

    data class QualityResult(
        val score: Float,
        val lightUniformity: Float,
        val sharpness: Float,
        val areaRatio: Float,
        val detectionState: DetectionState,
        val message: String?
    )

    /**
     * 舌象帧质量评估。
     * 采样步长=8，在保持性能的同时评估整体质量。
     */
    fun analyzeTongueFrame(bitmap: Bitmap): QualityResult {
        val width = bitmap.width
        val height = bitmap.height
        val step = 8

        val hsv = FloatArray(3)
        val vValues = mutableListOf<Float>()
        var tonguePixelCount = 0
        var totalSamples = 0

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                vValues.add(hsv[2])
                totalSamples++

                // 舌体颜色范围：大幅放宽，覆盖更多实际场景
                val isTongueColor = when {
                    hsv[0] in 0f..25f && hsv[1] in 0.08f..0.6f && hsv[2] in 0.15f..0.75f -> true
                    hsv[0] in 340f..360f && hsv[1] in 0.08f..0.55f && hsv[2] in 0.08f..0.6f -> true
                    hsv[0] in 0f..20f && hsv[1] in 0.05f..0.3f && hsv[2] in 0.2f..0.8f -> true
                    else -> false
                }
                if (isTongueColor) tonguePixelCount++
            }
        }

        if (totalSamples == 0) {
            return QualityResult(0f, 0f, 0f, 0f, DetectionState.NOT_DETECTED, "未检测到舌象")
        }

        // 1. 光照均匀度：HSV V通道标准差归一化
        val meanV = vValues.sum() / totalSamples
        val varianceV = vValues.map { (it - meanV) * (it - meanV) }.sum() / totalSamples
        val stdV = sqrt(varianceV)
        val lightUniformity = (1f - (stdV / 0.45f)).coerceIn(0f, 1f)

        // 2. 清晰度：Laplacian方差归一化
        val sharpness = computeSharpness(bitmap, width, height)

        // 3. 面积占比
        val areaRatio = tonguePixelCount.toFloat() / totalSamples

        // 4. 综合质量评分
        val qualityScore = (0.4f * lightUniformity + 0.3f * sharpness + 0.3f * areaRatio).coerceIn(0f, 1f)

        // 5. 判定检测状态与异常消息
        val detectionState: DetectionState
        val message: String?

        when {
            // 光线不足
            meanV < 0.15f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "光线太暗，请前往明亮处重新扫描"
            }
            // 光线过亮
            meanV > 0.9f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "光线过亮，请避免强光直射"
            }
            // 模糊
            sharpness < 0.08f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "画面抖动，请保持手机稳定"
            }
            // 未检测到舌体
            areaRatio < 0.02f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "未检测到有效舌象，请重新拍摄"
            }
            // 舌体遮挡
            areaRatio > 0.7f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "舌体被遮挡，请重新拍摄"
            }
            // 舌体置信度低
            qualityScore < 0.15f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "未检测到舌象"
            }
            // 质量不佳，正在对焦
            qualityScore < 0.35f -> {
                detectionState = DetectionState.POOR_QUALITY
                message = "正在对焦"
            }
            // 对焦成功
            else -> {
                detectionState = DetectionState.READY
                message = "对焦成功"
            }
        }

        return QualityResult(qualityScore, lightUniformity, sharpness, areaRatio, detectionState, message)
    }

    /**
     * 人脸帧检测 — 基于ML Kit FaceDetection结果评判。
     * 由于人脸检测已在 FaceMeshDetector 中实现，此处提供简易的像素级评估。
     */
    fun analyzeFacePresence(bitmap: Bitmap): QualityResult {
        val width = bitmap.width
        val height = bitmap.height
        val step = 8

        val hsv = FloatArray(3)
        val vValues = mutableListOf<Float>()
        var skinPixelCount = 0
        var totalSamples = 0

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                vValues.add(hsv[2])
                totalSamples++

                // 肤色范围检测：大幅放宽
                if (hsv[0] in 0f..40f && hsv[1] in 0.05f..0.6f && hsv[2] in 0.15f..0.9f) {
                    skinPixelCount++
                }
            }
        }

        if (totalSamples == 0) {
            return QualityResult(0f, 0f, 0f, 0f, DetectionState.NOT_DETECTED, "未检测到人脸")
        }

        val meanV = vValues.sum() / totalSamples
        val varianceV = vValues.map { (it - meanV) * (it - meanV) }.sum() / totalSamples
        val stdV = sqrt(varianceV)
        val lightUniformity = (1f - (stdV / 0.45f)).coerceIn(0f, 1f)

        val sharpness = computeSharpness(bitmap, width, height)
        val areaRatio = skinPixelCount.toFloat() / totalSamples

        val qualityScore = (0.4f * lightUniformity + 0.3f * sharpness + 0.3f * areaRatio).coerceIn(0f, 1f)

        val detectionState: DetectionState
        val message: String?

        when {
            meanV < 0.12f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "光线太暗，请前往明亮处重新扫描"
            }
            meanV > 0.92f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "光线过亮，请避免强光直射"
            }
            sharpness < 0.08f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "画面抖动，请保持手机稳定"
            }
            areaRatio < 0.05f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "未检测到人脸"
            }
            qualityScore < 0.15f -> {
                detectionState = DetectionState.NOT_DETECTED
                message = "未检测到人脸"
            }
            qualityScore < 0.35f -> {
                detectionState = DetectionState.POOR_QUALITY
                message = "正在定位"
            }
            else -> {
                detectionState = DetectionState.READY
                message = "定位成功"
            }
        }

        return QualityResult(qualityScore, lightUniformity, sharpness, areaRatio, detectionState, message)
    }

    /**
     * 基于 Laplacian 算子的清晰度评估。
     * 先将图像缩小到 200px 宽以提升性能，然后计算 Laplacian 方差。
     */
    private fun computeSharpness(bitmap: Bitmap, width: Int, height: Int): Float {
        val maxDim = 200
        val scale = (maxDim.toFloat() / maxOf(width, height)).coerceAtMost(1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else bitmap

        val sw = scaled.width
        val sh = scaled.height
        val step = 2
        var sum = 0f
        var sumSq = 0f
        var count = 0

        for (y in 1 until sh - 1 step step) {
            for (x in 1 until sw - 1 step step) {
                val g0 = Color.red(scaled.getPixel(x, y)).toFloat()
                val g1 = Color.red(scaled.getPixel(x - 1, y)).toFloat()
                val g2 = Color.red(scaled.getPixel(x + 1, y)).toFloat()
                val g3 = Color.red(scaled.getPixel(x, y - 1)).toFloat()
                val g4 = Color.red(scaled.getPixel(x, y + 1)).toFloat()
                val laplacian = g1 + g2 + g3 + g4 - 4f * g0
                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return (variance / 400f).coerceIn(0f, 1f)
    }
}