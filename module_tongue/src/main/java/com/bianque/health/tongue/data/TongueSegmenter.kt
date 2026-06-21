package com.bianque.health.tongue.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TongueSegmenter @Inject constructor() {

    data class SegmentationResult(
        val maskedBitmap: Bitmap,
        val tongueAreaRatio: Float,
        val contourPoints: List<Pair<Int, Int>>
    )

    /**
     * 对输入图像进行舌体分割。
     * 使用收紧的 HSV 阈值精准提取舌体区域，并验证检测区域是否合理。
     *
     * @return 掩码后的舌体图像（仅保留舌体，其余为黑色）
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

            val mask = BooleanArray(scaledWidth * scaledHeight)
            val hsv = FloatArray(3)

            // 收紧的 HSV 阈值 — 只匹配舌体组织特有的颜色范围
            for (i in pixels.indices) {
                val pixel = pixels[i]
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0]  // 0-360
                val s = hsv[1]  // 0-1
                val v = hsv[2]  // 0-1

                // 舌体颜色特征：
                // - 淡红舌: H 0-20, S 0.2-0.55, V 0.25-0.65
                // - 红舌:   H 0-15, S 0.3-0.6, V 0.2-0.55
                // - 暗红舌: H 0-10 / 350-360, S 0.25-0.55, V 0.15-0.45
                // 排除：地面/墙面（通常 S 很低）、皮肤（S 通常 < 0.15）、衣物（颜色各异但饱和度和亮度组合不同）
                val isTongueTissue = when {
                    // 核心红色区：淡红舌和红舌
                    h in 0f..20f && s in 0.18f..0.55f && v in 0.2f..0.65f -> true
                    // 暗红/绛红区
                    h in 350f..360f && s in 0.2f..0.55f && v in 0.15f..0.5f -> true
                    // 淡白舌（极淡红）
                    h in 0f..15f && s in 0.12f..0.25f && v in 0.3f..0.7f -> true
                    else -> false
                }

                if (isTongueTissue) {
                    mask[i] = true
                }
            }

            // 形态学闭运算：先膨胀再腐蚀，填充小孔洞
            val dilated = BooleanArray(mask.size)
            for (y in 1 until scaledHeight - 1) {
                for (x in 1 until scaledWidth - 1) {
                    val idx = y * scaledWidth + x
                    if (mask[idx]) {
                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                dilated[(y + dy) * scaledWidth + (x + dx)] = true
                            }
                        }
                    }
                }
            }

            // 腐蚀：恢复边界，去除孤立噪声
            for (y in 1 until scaledHeight - 1) {
                for (x in 1 until scaledWidth - 1) {
                    var allSet = true
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (!dilated[(y + dy) * scaledWidth + (x + dx)]) {
                                allSet = false
                            }
                        }
                    }
                    mask[y * scaledWidth + x] = allSet
                }
            }

            // 验证：检测区域是否大致位于图像中心（舌体应在画面中央）
            val centerX = scaledWidth / 2
            val centerY = scaledHeight / 2
            var maskedCount = 0
            var nearCenterCount = 0
            val centerRadius = (scaledWidth * 0.3).toInt()

            for (y in 0 until scaledHeight) {
                for (x in 0 until scaledWidth) {
                    if (mask[y * scaledWidth + x]) {
                        maskedCount++
                        val dx = x - centerX
                        val dy = y - centerY
                        if (dx * dx + dy * dy <= centerRadius * centerRadius) {
                            nearCenterCount++
                        }
                    }
                }
            }

            val totalPixels = scaledWidth * scaledHeight
            val areaRatio = maskedCount.toFloat() / totalPixels

            // 舌体区域应该占画面 5%-60%，且至少有 40% 的检测像素靠近中心
            if (areaRatio < 0.02f || areaRatio > 0.7f) {
                Timber.w("TongueSegmenter: area ratio out of range (%.2f), likely not a tongue", areaRatio)
                // 对于无效检测，返回仅保留接近中心区域的掩码
                for (i in mask.indices) {
                    val x = i % scaledWidth
                    val y = i / scaledWidth
                    val dx = x - centerX
                    val dy = y - centerY
                    if (dx * dx + dy * dy > centerRadius * centerRadius) {
                        mask[i] = false
                    }
                }
            }

            if (nearCenterCount < maskedCount * 0.3f) {
                Timber.w("TongueSegmenter: only %.0f%% of detected pixels near center, clearing edge pixels",
                    (nearCenterCount.toFloat() / maskedCount.coerceAtLeast(1) * 100))
                // 清除远离中心的像素
                for (i in mask.indices) {
                    val x = i % scaledWidth
                    val y = i / scaledWidth
                    val dx = x - centerX
                    val dy = y - centerY
                    if (dx * dx + dy * dy > centerRadius * centerRadius) {
                        mask[i] = false
                    }
                }
            }

            // 生成掩码图像
            val resultPixels = IntArray(scaledWidth * scaledHeight)
            for (i in pixels.indices) {
                resultPixels[i] = if (mask[i]) pixels[i] else Color.BLACK
            }

            val result = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            result.setPixels(resultPixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)

            if (scale < 1f) {
                Bitmap.createScaledBitmap(result, width, height, true)
            } else {
                result
            }
        } catch (e: Exception) {
            Timber.e(e, "TongueSegmenter: segmentation failed")
            bitmap
        }
    }
}