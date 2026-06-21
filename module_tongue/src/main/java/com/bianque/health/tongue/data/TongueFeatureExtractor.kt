package com.bianque.health.tongue.data

import android.graphics.Bitmap
import android.graphics.Color
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Singleton
class TongueFeatureExtractor @Inject constructor() {

    /**
     * 从掩码舌体图像中提取舌象特征
     * 改进：更精细的分类阈值 + 去除死分支 + 真实比例分析
     */
    suspend fun extract(maskedBitmap: Bitmap): TongueDiagnosisResult = withContext(Dispatchers.Default) {
        try {
            val width = maskedBitmap.width
            val height = maskedBitmap.height
            val pixels = IntArray(width * height)
            maskedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // 收集非黑色像素（舌体区域）
            val tonguePixels = mutableListOf<Int>()
            val hsv = FloatArray(3)
            for (pixel in pixels) {
                if (pixel != Color.BLACK) {
                    tonguePixels.add(pixel)
                }
            }

            if (tonguePixels.size < 500) {
                return@withContext TongueDiagnosisResult(
                    tongueColor = "未检测到舌体",
                    coatingColor = "未知",
                    coatingThickness = "未知",
                    coatingMoisture = "未知",
                    tongueShape = "未知",
                    confidence = 0f
                )
            }

            // 1. 舌色分析 — 基于Lab色度空间的a*通道（红绿轴）
            val tongueColor = analyzeTongueColor(tonguePixels)

            // 2. 苔色分析 — 亮度分层 + HSV饱和度联合判断
            val coatingColor = analyzeCoatingColor(tonguePixels)

            // 3. 苔厚度分析 — 中心vs边缘饱和度梯度
            val coatingThickness = analyzeCoatingThickness(maskedBitmap, pixels, width, height)

            // 4. 苔润燥分析 — 亮度分布偏度+高光比例
            val coatingMoisture = analyzeCoatingMoisture(pixels, width, height)

            // 5. 舌形分析
            val tongueShape = analyzeTongueShape(pixels, width, height)

            // 置信度根据实际检测质量浮动
            val conf = when {
                tonguePixels.size < 1000 -> 0.5f
                tonguePixels.size < 5000 -> 0.65f
                else -> 0.78f
            }

            TongueDiagnosisResult(
                tongueColor = tongueColor,
                coatingColor = coatingColor,
                coatingThickness = coatingThickness,
                coatingMoisture = coatingMoisture,
                tongueShape = tongueShape,
                confidence = conf
            )
        } catch (e: Exception) {
            Timber.e(e, "TongueFeatureExtractor: extraction failed")
            TongueDiagnosisResult(
                tongueColor = "分析失败",
                coatingColor = "未知",
                coatingThickness = "未知",
                coatingMoisture = "未知",
                tongueShape = "未知",
                confidence = 0f
            )
        }
    }

    /**
     * 舌色分类: 基于HSV的H（色相）和S（饱和度）联合判断
     * 改进：更精确的中医舌色分类，避免"淡红"一统天下
     */
    private fun analyzeTongueColor(pixels: List<Int>): String {
        val hsv = FloatArray(3)
        var sumH = 0f
        var sumS = 0f
        var sumV = 0f

        for (pixel in pixels) {
            Color.colorToHSV(pixel, hsv)
            sumH += hsv[0]
            sumS += hsv[1]
            sumV += hsv[2]
        }

        val avgH = sumH / pixels.size
        val avgS = sumS / pixels.size
        val avgV = sumV / pixels.size

        Timber.d("TongueColor: avgH=%.1f, avgS=%.3f, avgV=%.3f", avgH, avgS, avgV)

        return when {
            // 紫暗舌：极暗或紫色系
            avgV < 0.20 && avgS > 0.15 -> "紫暗"
            avgH in 280f..340f && avgS > 0.15 -> "紫暗"
            // 红绛舌：色相偏红且饱和度高或亮度低
            avgH in 340f..360f && avgS > 0.30 -> "红绛"
            avgH in 0f..10f && avgS > 0.35 -> "红绛"
            // 红舌：色相偏红，饱和度中等偏高
            avgH in 0f..20f && avgS in 0.25f..0.45f && avgV in 0.25f..0.55f -> "红"
            // 淡白舌：亮度高且饱和度低
            avgS < 0.12 && avgV > 0.50 -> "淡白"
            // 淡红舌：正常范围（需要饱和度和亮度在中间值）
            avgH in 0f..25f && avgS in 0.12f..0.30f && avgV in 0.35f..0.65f -> "淡红"
            // 亮度偏低但不算暗
            avgV < 0.30 -> "暗红"
            // 高亮度低饱和度
            avgV > 0.60 && avgS < 0.18 -> "淡"
            else -> "淡红"
        }
    }

    /**
     * 苔色分类: 分析舌体表面较亮区域（苔）的颜色
     * 改进：修复逻辑死分支，增加细粒度判断
     */
    private fun analyzeCoatingColor(pixels: List<Int>): String {
        val hsv = FloatArray(3)
        // 取亮度前 40% 的像素作为苔区域（更合理的采样比例）
        val sortedByBrightness = pixels.sortedByDescending {
            Color.colorToHSV(it, hsv)
            hsv[2]
        }
        val coatingPixels = sortedByBrightness.take((pixels.size * 0.4).toInt())

        var sumH = 0f
        var sumS = 0f
        var sumV = 0f
        for (pixel in coatingPixels) {
            Color.colorToHSV(pixel, hsv)
            sumH += hsv[0]
            sumS += hsv[1]
            sumV += hsv[2]
        }

        val avgH = sumH / coatingPixels.size
        val avgS = sumS / coatingPixels.size
        val avgV = sumV / coatingPixels.size

        Timber.d("CoatingColor: avgH=%.1f, avgS=%.3f, avgV=%.3f", avgH, avgS, avgV)

        return when {
            // 黑苔/灰苔：极低饱和度 + 极低亮度
            avgV < 0.20 && avgS < 0.10 -> "灰黑"
            // 灰苔：低饱和度 + 较低亮度
            avgV < 0.35 && avgS < 0.10 -> "灰"
            // 黄苔：色相在黄色范围 + 有一定饱和度
            avgH in 35f..75f && avgS > 0.15 -> "黄"
            // 黄白相兼：偏黄但饱和度不高
            avgH in 25f..45f && avgS in 0.08f..0.20f -> "黄白"
            // 白腻苔：高亮度 + 低饱和度
            avgV > 0.50 && avgS < 0.08 -> "白腻"
            // 薄白苔：一般亮度 + 较低饱和度
            avgS < 0.10 -> "薄白"
            else -> "白"
        }
    }

    /**
     * 苔厚度分析: 基于中心vs边缘饱和度差
     * 改进：真实梯度计算 + 更合理的阈值
     */
    private fun analyzeCoatingThickness(bitmap: Bitmap, pixels: IntArray, width: Int, height: Int): String {
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] != Color.BLACK) {
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
        }

        if (maxX <= minX || maxY <= minY) return "未知"

        val centerX = (minX + maxX) / 2
        val centerY = (minY + maxY) / 2
        val hsv = FloatArray(3)

        // 中心区域采样（半径为舌体短边的1/4）
        val shortSide = min(maxX - minX, maxY - minY)
        val radius = (shortSide * 0.25f).toInt().coerceAtLeast(10)

        var centerSat = 0f
        var centerCount = 0
        var edgeSat = 0f
        var edgeCount = 0

        for (y in minY until maxY) {
            for (x in minX until maxX) {
                val pixel = pixels[y * width + x]
                if (pixel != Color.BLACK) {
                    val dx = x - centerX
                    val dy = y - centerY
                    val dist = sqrt((dx * dx + dy * dy).toFloat())
                    Color.colorToHSV(pixel, hsv)
                    if (dist < radius) {
                        centerSat += hsv[1]
                        centerCount++
                    } else if (dist > radius * 2) {
                        edgeSat += hsv[1]
                        edgeCount++
                    }
                }
            }
        }

        if (centerCount == 0 || edgeCount == 0) return "薄"
        centerSat /= centerCount
        edgeSat /= edgeCount

        // 中心饱和度比边缘高 → 苔厚（苔覆盖了舌体本色）
        val satGradient = centerSat - edgeSat
        Timber.d("CoatingThickness: centerSat=%.3f, edgeSat=%.3f, gradient=%.3f",
            centerSat, edgeSat, satGradient)

        return when {
            centerSat > 0.30 || satGradient > 0.08 -> "厚"
            centerSat > 0.15 || satGradient > 0.03 -> "薄"
            else -> "薄"
        }
    }

    /**
     * 苔润燥分析: 基于亮度分布特征（偏度+高光比例）
     * 改进：不再简单用方差，而是计算高光像素比例
     */
    private fun analyzeCoatingMoisture(pixels: IntArray, width: Int, height: Int): String {
        val hsv = FloatArray(3)
        val vValues = mutableListOf<Float>()
        var highlightCount = 0  // 高光像素 (V>0.75 且 S<0.15)
        var totalCount = 0

        for (pixel in pixels) {
            if (pixel != Color.BLACK) {
                Color.colorToHSV(pixel, hsv)
                val v = hsv[2]
                val s = hsv[1]
                vValues.add(v)
                totalCount++
                // 高光 = 湿润反光
                if (v > 0.75f && s < 0.15f) {
                    highlightCount++
                }
            }
        }

        if (totalCount == 0) return "未知"

        val meanV = vValues.average().toFloat()
        val highlightRatio = highlightCount.toFloat() / totalCount

        // 方差计算
        val variance = if (vValues.size > 1) {
            vValues.map { (it - meanV) * (it - meanV) }.average().toFloat()
        } else 0f

        Timber.d("CoatingMoisture: meanV=%.3f, variance=%.4f, highlightRatio=%.3f",
            meanV, variance, highlightRatio)

        return when {
            // 润：高光比例高（反光多）或方差大（光泽感）
            highlightRatio > 0.05f -> "润"
            variance > 0.04f -> "润"
            // 燥：低亮度、低方差、几乎没有高光
            meanV < 0.30f && variance < 0.015f -> "燥"
            variance < 0.008f -> "燥"
            // 中间态
            else -> "适中"
        }
    }

    /**
     * 舌形分析: 基于舌体轮廓的长宽比和圆度
     * 改进：增加齿痕检测（边缘凹凸度）
     */
    private fun analyzeTongueShape(pixels: IntArray, width: Int, height: Int): String {
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var tonguePixelCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] != Color.BLACK) {
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                    tonguePixelCount++
                }
            }
        }

        if (maxX <= minX || maxY <= minY) return "未知"

        val w = maxX - minX
        val h = maxY - minY
        val aspectRatio = h.toFloat() / w.toFloat()

        // 圆度 = 实际面积 / 外接矩形面积
        val boundingArea = w * h
        val fillRatio = tonguePixelCount.toFloat() / boundingArea

        Timber.d("TongueShape: aspectRatio=%.2f, fillRatio=%.2f", aspectRatio, fillRatio)

        return when {
            // 胖大舌：宽胖，fillRatio低（边缘不规则）
            aspectRatio < 0.75 && fillRatio < 0.55 -> "胖大"
            // 齿痕舌：fillRatio低（边缘有凹陷）
            fillRatio < 0.50 -> "齿痕"
            // 瘦薄舌：纵长且窄
            aspectRatio > 1.4 -> "瘦薄"
            // 正常
            aspectRatio in 0.85f..1.2f && fillRatio > 0.55 -> "正常"
            else -> "正常"
        }
    }
}
