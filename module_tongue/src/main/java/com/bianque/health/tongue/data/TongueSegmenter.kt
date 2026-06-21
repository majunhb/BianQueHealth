package com.bianque.health.tongue.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * 舌体分割器 — 基于设计文档的舌象处理流程。
 *
 * 流程：质量检测 → 色彩校正 → HSV分割 → 形态学后处理 → 中心区域校验
 */
@Singleton
class TongueSegmenter @Inject constructor() {

    data class SegmentationResult(
        val maskedBitmap: Bitmap,
        val tongueAreaRatio: Float,
        val qualityScore: Float,
        val qcMessage: String?
    )

    /**
     * 对输入图像进行舌体分割。
     *
     * 设计文档要求：
     * 1. 质量检测：检查光照、模糊度、舌体占比
     * 2. 色彩校正：白平衡调整（灰度世界法）
     * 3. HSV分割：收紧的舌体组织颜色阈值
     * 4. 形态学处理：闭运算填充孔洞
     * 5. 中心区域校验：排除非舌体误检
     */
    suspend fun segment(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        try {
            val width = bitmap.width
            val height = bitmap.height

            // 缩小图像以提升性能 (最大 480px)
            val scale = (480f / maxOf(width, height)).coerceAtMost(1f)
            val scaledWidth = (width * scale).toInt()
            val scaledHeight = (height * scale).toInt()
            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            } else bitmap

            val pixels = IntArray(scaledWidth * scaledHeight)
            scaledBitmap.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)

            // 步骤1: 质量检测
            val qc = performQualityCheck(pixels, scaledWidth, scaledHeight)
            if (qc.qualityScore < 0.3f) {
                Timber.w("TongueSegmenter: QC failed - ${qc.message}")
            }

            // 步骤2: 色彩校正
            val correctedPixels = applyColorCorrection(pixels)

            // 步骤3: HSV分割
            val mask = hsvSegmentation(correctedPixels, scaledWidth, scaledHeight)

            // 步骤4: 形态学处理
            val refinedMask = morphologicalProcess(mask, scaledWidth, scaledHeight)

            // 步骤5: 中心区域校验
            val validatedMask = validateCenterRegion(refinedMask, scaledWidth, scaledHeight)

            // 生成掩码图像
            val resultPixels = IntArray(scaledWidth * scaledHeight)
            for (i in correctedPixels.indices) {
                resultPixels[i] = if (validatedMask[i]) correctedPixels[i] else Color.BLACK
            }

            val result = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            result.setPixels(resultPixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)

            if (scale < 1f) Bitmap.createScaledBitmap(result, width, height, true) else result
        } catch (e: Exception) {
            Timber.e(e, "TongueSegmenter: segmentation failed")
            bitmap
        }
    }

    /**
     * 质量检测 — 检查图像是否适合舌象分析
     */
    private fun performQualityCheck(pixels: IntArray, width: Int, height: Int): QualityCheck {
        val hsv = FloatArray(3)
        var sumV = 0f
        var sumVSq = 0f
        var redCount = 0
        val totalPixels = width * height

        for (pixel in pixels) {
            Color.colorToHSV(pixel, hsv)
            val v = hsv[2]
            sumV += v
            sumVSq += v * v
            if (hsv[0] in 0f..20f && hsv[1] in 0.15f..0.55f && v in 0.2f..0.65f) {
                redCount++
            }
        }

        val meanV = sumV / totalPixels
        val varianceV = (sumVSq / totalPixels) - (meanV * meanV)
        val redRatio = redCount.toFloat() / totalPixels

        val issues = mutableListOf<String>()
        var score = 1.0f

        // 光照检查
        if (meanV < 0.15f) { issues.add("光线过暗"); score -= 0.4f }
        else if (meanV > 0.85f) { issues.add("光线过亮"); score -= 0.3f }

        // 模糊度检查（低方差 = 模糊）
        if (varianceV < 0.005f) { issues.add("图像模糊"); score -= 0.3f }

        // 舌体占比检查
        if (redRatio < 0.02f) { issues.add("未检测到舌体组织"); score -= 0.3f }
        else if (redRatio > 0.7f) { issues.add("舌体占比过大"); score -= 0.1f }

        return QualityCheck(score.coerceIn(0f, 1f), if (issues.isEmpty()) null else issues.joinToString("; "))
    }

    data class QualityCheck(val qualityScore: Float, val message: String?)

    /**
     * 色彩校正 — 灰度世界法白平衡
     */
    private fun applyColorCorrection(pixels: IntArray): IntArray {
        val corrected = IntArray(pixels.size)
        var sumR = 0L; var sumG = 0L; var sumB = 0L
        var count = 0

        for (pixel in pixels) {
            sumR += Color.red(pixel)
            sumG += Color.green(pixel)
            sumB += Color.blue(pixel)
            count++
        }

        if (count == 0) return pixels

        val avgR = sumR.toFloat() / count
        val avgG = sumG.toFloat() / count
        val avgB = sumB.toFloat() / count
        val avgGray = (avgR + avgG + avgB) / 3f

        // 避免极端校正
        if (avgGray < 5f) return pixels

        val scaleR = (avgGray / avgR).coerceIn(0.8f, 1.2f)
        val scaleG = (avgGray / avgG).coerceIn(0.8f, 1.2f)
        val scaleB = (avgGray / avgB).coerceIn(0.8f, 1.2f)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (Color.red(pixel) * scaleR).toInt().coerceIn(0, 255)
            val g = (Color.green(pixel) * scaleG).toInt().coerceIn(0, 255)
            val b = (Color.blue(pixel) * scaleB).toInt().coerceIn(0, 255)
            corrected[i] = Color.rgb(r, g, b)
        }

        return corrected
    }

    /**
     * HSV 舌体分割 — 收紧的舌体组织颜色阈值
     */
    private fun hsvSegmentation(pixels: IntArray, width: Int, height: Int): BooleanArray {
        val mask = BooleanArray(pixels.size)
        val hsv = FloatArray(3)

        for (i in pixels.indices) {
            Color.colorToHSV(pixels[i], hsv)
            val h = hsv[0]; val s = hsv[1]; val v = hsv[2]

            mask[i] = when {
                // 淡红舌: 正常舌色
                h in 0f..20f && s in 0.18f..0.55f && v in 0.2f..0.65f -> true
                // 红舌: 热证
                h in 0f..15f && s in 0.3f..0.6f && v in 0.2f..0.55f -> true
                // 暗红/绛舌: 热入营血
                h in 350f..360f && s in 0.2f..0.55f && v in 0.15f..0.5f -> true
                // 淡白舌: 血虚
                h in 0f..15f && s in 0.10f..0.25f && v in 0.3f..0.7f -> true
                // 紫暗舌: 血瘀
                h in 340f..360f && s in 0.15f..0.5f && v in 0.1f..0.35f -> true
                else -> false
            }
        }

        return mask
    }

    /**
     * 形态学处理：闭运算（先膨胀再腐蚀）填充孔洞，去除孤立噪声
     */
    private fun morphologicalProcess(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        // 膨胀
        val dilated = BooleanArray(mask.size)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                if (mask[idx]) {
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            dilated[(y + dy) * width + (x + dx)] = true
                        }
                    }
                }
            }
        }

        // 腐蚀
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var allSet = true
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (!dilated[(y + dy) * width + (x + dx)]) { allSet = false; break }
                    }
                    if (!allSet) break
                }
                mask[y * width + x] = allSet
            }
        }

        return mask
    }

    /**
     * 中心区域校验：检测区域应在画面中央，远离边缘的被清除
     */
    private fun validateCenterRegion(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val centerX = width / 2
        val centerY = height / 2
        val centerRadius = (width * 0.35).toInt()

        var totalMasked = 0
        var nearCenter = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) {
                    totalMasked++
                    val dx = x - centerX
                    val dy = y - centerY
                    if (dx * dx + dy * dy <= centerRadius * centerRadius) nearCenter++
                }
            }
        }

        val areaRatio = totalMasked.toFloat() / (width * height)
        if (areaRatio < 0.02f || areaRatio > 0.7f) {
            // 无效区域，只保留中心
            for (i in mask.indices) {
                val x = i % width; val y = i / width
                val dx = x - centerX; val dy = y - centerY
                if (dx * dx + dy * dy > centerRadius * centerRadius) mask[i] = false
            }
        }

        if (totalMasked > 0 && nearCenter < totalMasked * 0.3f) {
            for (i in mask.indices) {
                val x = i % width; val y = i / width
                val dx = x - centerX; val dy = y - centerY
                if (dx * dx + dy * dy > centerRadius * centerRadius) mask[i] = false
            }
        }

        return mask
    }
}