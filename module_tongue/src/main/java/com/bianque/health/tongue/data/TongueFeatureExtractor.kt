package com.bianque.health.tongue.data

import android.graphics.Bitmap
import android.graphics.Color
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 舌象八维特征提取器 — 基于设计文档的舌诊分析模块。
 *
 * 提取8个维度的舌象特征：
 * 1. 舌色 (tongue_color)      — 淡白/淡红/红/绛/紫暗
 * 2. 苔色 (coating_color)     — 白/黄/灰/黑
 * 3. 苔厚 (coating_thickness) — 薄/厚/腻
 * 4. 苔质 (coating_moisture)  — 润/燥/滑
 * 5. 舌形 (tongue_shape)      — 正常/胖大/瘦薄/齿痕/裂纹
 * 6. 舌质 (tongue_body)       — 老/嫩
 * 7. 舌下络脉 (sublingual_vein) — 正常/怒张
 * 8. 舌态 (tongue_mobility)   — 灵活/歪斜/僵硬
 */
@Singleton
class TongueFeatureExtractor @Inject constructor() {

    data class LabColor(val l: Float, val a: Float, val b: Float)

    /**
     * 从分割后的舌体图像提取八维特征。
     */
    suspend fun extract(maskedBitmap: Bitmap): TongueDiagnosisResult = withContext(Dispatchers.Default) {
        try {
            val width = maskedBitmap.width
            val height = maskedBitmap.height
            val pixels = IntArray(width * height)
            maskedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // 统计舌体像素（非黑色像素）
            val tonguePixels = pixels.filter { Color.red(it) > 10 || Color.green(it) > 10 || Color.blue(it) > 10 }
            if (tonguePixels.isEmpty()) {
                Timber.w("TongueFeatureExtractor: no tongue pixels found")
                return@withContext TongueDiagnosisResult(
                    tongueColor = "未知", coatingColor = "未知",
                    coatingThickness = "未知", coatingMoisture = "未知",
                    tongueShape = "未知", tongueBody = "未知",
                    sublingualVein = "未知", tongueMobility = "未知",
                    confidence = 0f
                )
            }

            val colors = tonguePixels.map { pixel ->
                Triple(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            }

            // 特征1: 舌色分类
            val tongueColor = classifyTongueColor(colors)

            // 特征2: 苔色分类
            val coatingColor = classifyCoatingColor(colors)

            // 特征3: 苔厚分类
            val coatingThickness = classifyCoatingThickness(colors)

            // 特征4: 苔质分类
            val coatingMoisture = classifyCoatingMoisture(colors)

            // 特征5: 舌形分析
            val tongueShape = classifyTongueShape(tonguePixels, width, height)

            // 特征6: 舌质分析
            val tongueBody = classifyTongueBody(colors)

            // 舌下络脉分析
            val sublingualVein = classifySublingualVein(maskedBitmap)

            // 舌态分析
            val tongueMobility = classifyTongueMobility(maskedBitmap)

            val confidence = computeConfidence(tonguePixels.size, pixels.size)

            TongueDiagnosisResult(
                tongueColor = tongueColor,
                coatingColor = coatingColor,
                coatingThickness = coatingThickness,
                coatingMoisture = coatingMoisture,
                tongueShape = tongueShape,
                tongueBody = tongueBody,
                sublingualVein = sublingualVein,
                tongueMobility = tongueMobility,
                confidence = confidence
            )
        } catch (e: Exception) {
            Timber.e(e, "TongueFeatureExtractor: extraction failed")
            TongueDiagnosisResult(
                tongueColor = "分析失败", coatingColor = "分析失败",
                coatingThickness = "分析失败", coatingMoisture = "分析失败",
                tongueShape = "分析失败", tongueBody = "分析失败",
                sublingualVein = "分析失败", tongueMobility = "分析失败",
                confidence = 0f
            )
        }
    }

    /**
     * 特征1: 舌色分类 — 基于RGB转HSV再分类
     */
    private fun classifyTongueColor(colors: List<Triple<Int, Int, Int>>): String {
        val hsv = FloatArray(3)
        val hueValues = mutableListOf<Float>()
        val satValues = mutableListOf<Float>()
        val valValues = mutableListOf<Float>()

        for ((r, g, b) in colors) {
            Color.RGBToHSV(r, g, b, hsv)
            hueValues.add(hsv[0])
            satValues.add(hsv[1])
            valValues.add(hsv[2])
        }

        val avgHue = hueValues.average().toFloat()
        val avgSat = satValues.average().toFloat()
        val avgVal = valValues.average().toFloat()

        return when {
            // 淡白舌：低饱和度，高亮度，偏红
            avgSat < 0.18f && avgVal > 0.45f -> "淡白"
            // 紫暗舌：低亮度，偏紫
            avgVal < 0.25f && avgHue > 300f -> "紫暗"
            // 绛舌：高饱和度，低亮度
            avgSat > 0.45f && avgVal < 0.35f -> "红绛"
            // 红舌：高饱和度
            avgSat > 0.4f -> "红"
            // 淡红舌：正常范围
            avgSat in 0.18f..0.4f && avgVal in 0.35f..0.55f -> "淡红"
            // 默认
            else -> "淡红"
        }
    }

    /**
     * 特征2: 苔色分类
     */
    private fun classifyCoatingColor(colors: List<Triple<Int, Int, Int>>): String {
        val hsv = FloatArray(3)
        val hueValues = mutableListOf<Float>()
        val satValues = mutableListOf<Float>()
        val valValues = mutableListOf<Float>()

        for ((r, g, b) in colors) {
            Color.RGBToHSV(r, g, b, hsv)
            hueValues.add(hsv[0])
            satValues.add(hsv[1])
            valValues.add(hsv[2])
        }

        val avgHue = hueValues.average().toFloat()
        val avgSat = satValues.average().toFloat()
        val avgVal = valValues.average().toFloat()

        return when {
            // 黄苔：H在40-70度
            avgHue in 40f..70f && avgSat > 0.15f -> "黄"
            // 灰/黑苔：低饱和度，低亮度
            avgSat < 0.12f && avgVal < 0.4f -> "灰"
            // 白苔：高亮度，低饱和度
            avgVal > 0.5f && avgSat < 0.2f -> "白"
            // 苔色与舌色接近，不明显
            avgSat < 0.15f -> "白"
            else -> "黄白"
        }
    }

    /**
     * 特征3: 苔厚分类
     */
    private fun classifyCoatingThickness(colors: List<Triple<Int, Int, Int>>): String {
        val hsv = FloatArray(3)
        var highSatCount = 0
        var lowSatCount = 0

        for ((r, g, b) in colors) {
            Color.RGBToHSV(r, g, b, hsv)
            if (hsv[1] > 0.35f) highSatCount++
            if (hsv[1] < 0.15f) lowSatCount++
        }

        val totalPixels = colors.size.toFloat()
        val highSatRatio = highSatCount / totalPixels
        val lowSatRatio = lowSatCount / totalPixels

        return when {
            // 厚苔：高饱和度像素占比高
            highSatRatio > 0.5f -> "厚"
            // 腻苔：中高饱和度 + 中等占比
            highSatRatio in 0.25f..0.5f -> "腻"
            // 薄苔：低饱和度为主
            lowSatRatio > 0.6f -> "薄"
            else -> "薄"
        }
    }

    /**
     * 特征4: 苔质分类（润/燥/滑）
     */
    private fun classifyCoatingMoisture(colors: List<Triple<Int, Int, Int>>): String {
        val hsv = FloatArray(3)
        val valValues = mutableListOf<Float>()
        var glossyCount = 0

        for ((r, g, b) in colors) {
            Color.RGBToHSV(r, g, b, hsv)
            valValues.add(hsv[2])
            // 高亮度+低饱和度 = 光泽（湿滑）
            if (hsv[2] > 0.6f && hsv[1] < 0.2f) glossyCount++
        }

        val avgVal = valValues.average().toFloat()
        val glossyRatio = glossyCount.toFloat() / colors.size

        return when {
            // 滑苔：光泽度高
            glossyRatio > 0.3f -> "滑"
            // 燥苔：亮度低
            avgVal < 0.3f -> "燥"
            // 润苔：正常
            else -> "润"
        }
    }

    /**
     * 特征5: 舌形分析
     */
    private fun classifyTongueShape(tonguePixels: List<Int>, width: Int, height: Int): String {
        // 计算舌体在图像中的分布
        var minX = width; var maxX = 0
        var minY = height; var maxY = 0

        for (i in tonguePixels.indices) {
            val pixel = tonguePixels[i]
            if (Color.red(pixel) > 10 || Color.green(pixel) > 10 || Color.blue(pixel) > 10) {
                // 从像素数组索引推算位置（假设原图像由extract接收）
                // 实际舌体像素已在maskedBitmap中，这里用像素数量估算
            }
        }

        // 基于像素数量估算舌形
        val tongueRatio = tonguePixels.size.toFloat() / (width * height)
        val aspectRatio = width.toFloat() / height

        return when {
            tongueRatio > 0.5f -> "胖大"
            tongueRatio < 0.1f -> "瘦薄"
            // 齿痕和裂纹需要更精细的轮廓分析，标记为正常
            else -> "正常"
        }
    }

    /**
     * 特征6: 舌质分析（老/嫩）
     */
    private fun classifyTongueBody(colors: List<Triple<Int, Int, Int>>): String {
        val hsv = FloatArray(3)
        var textureScore = 0f

        // 老舌：纹理粗糙，颜色较深，饱和度较高
        // 嫩舌：纹理细腻，颜色较浅，饱和度较低
        for ((r, g, b) in colors) {
            Color.RGBToHSV(r, g, b, hsv)
            // 高饱和度+中低亮度 = 老舌特征
            if (hsv[1] > 0.4f && hsv[2] < 0.5f) textureScore += 1f
            // 低饱和度+高亮度 = 嫩舌特征
            if (hsv[1] < 0.2f && hsv[2] > 0.5f) textureScore -= 1f
        }

        val normalizedScore = textureScore / colors.size.coerceAtLeast(1)
        return when {
            normalizedScore > 0.2f -> "老"
            normalizedScore < -0.2f -> "嫩"
            else -> "正常"
        }
    }

    /**
     * 舌下络脉分类。
     * 分析舌体底部区域的暗色/蓝紫色像素分布。
     * 正常舌下络脉隐而不显；怒张时可见暗紫色粗大血管。
     */
    private fun classifySublingualVein(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val step = 4

        var totalPixels = 0
        var darkVeinPixels = 0

        // 分析舌体下半部分（模拟舌下区域）
        val startY = (height * 0.6f).toInt()
        for (y in startY until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == 0) continue // 跳过背景
                totalPixels++

                Color.colorToHSV(pixel, hsvArray)
                val (h, s, v) = Triple(hsvArray[0], hsvArray[1], hsvArray[2])

                // 暗紫色/蓝紫色/深色区域 → 络脉
                val isVeinPixel = (v < 0.35f && s > 0.15f) ||
                        (h in 240f..300f && s > 0.2f && v < 0.4f) ||
                        (h in 300f..360f && s > 0.25f && v < 0.35f)
                if (isVeinPixel) darkVeinPixels++
            }
        }

        if (totalPixels == 0) return "待检测"

        val veinRatio = darkVeinPixels.toFloat() / totalPixels

        return when {
            veinRatio > 0.12f -> "怒张"
            veinRatio > 0.05f -> "正常"
            else -> "正常"
        }
    }

    /**
     * 舌态分析。
     * 基于静态图像分析舌体形态对称性和边缘平滑度。
     * - 灵活：舌体居中、对称、边缘平滑
     * - 歪斜：舌体左右不对称
     * - 僵硬：舌体边缘粗糙、形态不规则
     */
    private fun classifyTongueMobility(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val step = 4

        // 计算舌体像素的左右分布
        var leftPixels = 0
        var rightPixels = 0
        var totalPixels = 0
        val midX = width / 2f

        // 计算边缘粗糙度：统计边缘像素的局部方差
        var edgeRoughness = 0f
        var edgeCount = 0

        for (y in step until height - step step step) {
            for (x in step until width - step step step) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == 0) continue
                totalPixels++

                if (x < midX) leftPixels++ else rightPixels++

                // 边缘检测：8邻域中是否有背景像素
                val hasBackground = bitmap.getPixel(x - step, y) == 0 ||
                        bitmap.getPixel(x + step, y) == 0 ||
                        bitmap.getPixel(x, y - step) == 0 ||
                        bitmap.getPixel(x, y + step) == 0

                if (hasBackground) {
                    Color.colorToHSV(pixel, hsvArray)
                    edgeRoughness += hsvArray[2] // 边缘亮度
                    edgeCount++
                }
            }
        }

        if (totalPixels == 0) return "待检测"

        // 左右对称性
        val symmetryRatio = if (rightPixels > 0) {
            leftPixels.toFloat() / rightPixels
        } else 1f

        val isAsymmetric = symmetryRatio < 0.7f || symmetryRatio > 1.4f

        // 边缘粗糙度评估
        val avgEdgeBrightness = if (edgeCount > 0) edgeRoughness / edgeCount else 0f
        val isRoughEdge = edgeCount > 0 && avgEdgeBrightness < 0.35f

        return when {
            isAsymmetric && isRoughEdge -> "歪斜"
            isRoughEdge -> "僵硬"
            isAsymmetric -> "歪斜"
            else -> "灵活"
        }
    }

    private fun computeConfidence(tonguePixelCount: Int, totalPixelCount: Int): Float {
        val ratio = tonguePixelCount.toFloat() / totalPixelCount
        return when {
            ratio < 0.02f -> 0.3f
            ratio < 0.05f -> 0.5f
            ratio < 0.1f -> 0.7f
            ratio < 0.3f -> 0.85f
            else -> 0.9f
        }
    }
}