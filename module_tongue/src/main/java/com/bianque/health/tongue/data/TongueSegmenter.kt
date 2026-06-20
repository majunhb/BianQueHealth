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

    /**
     * 舌头分割结果
     */
    data class SegmentationResult(
        val maskedBitmap: Bitmap,       // 仅保留舌体区域的掩码图
        val tongueAreaRatio: Float,     // 舌体占图像面积比例
        val contourPoints: List<Pair<Int, Int>> // 舌体轮廓点
    )

    /**
     * 对输入图像进行舌体分割
     * @return 掩码后的舌体图像
     */
    suspend fun segment(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        try {
            val width = bitmap.width
            val height = bitmap.height

            // 缩小图像以提升性能 (最大 320px)
            val scale = (320f / maxOf(width, height)).coerceAtMost(1f)
            val scaledWidth = (width * scale).toInt()
            val scaledHeight = (height * scale).toInt()
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

            val pixels = IntArray(scaledWidth * scaledHeight)
            scaledBitmap.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)

            val mask = BooleanArray(scaledWidth * scaledHeight)

            // HSV 阈值分割
            val hsv = FloatArray(3)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0]  // 0-360
                val s = hsv[1]  // 0-1
                val v = hsv[2]  // 0-1

                // 舌体颜色范围: 红色/品红色区域
                val isRed = (h < 10 || h > 340) && s > 0.15f && v > 0.2f
                val isPink = h in 300f..340f && s > 0.1f && v > 0.25f
                val isLightRed = h in 10f..30f && s > 0.08f && v > 0.3f

                if (isRed || isPink || isLightRed) {
                    mask[i] = true
                }
            }

            // 形态学操作: 膨胀 (3x3 核)
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

            // 形态学操作: 腐蚀 (3x3 核) — 去噪
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

            // 生成掩码图像
            val resultPixels = IntArray(scaledWidth * scaledHeight)
            for (i in pixels.indices) {
                resultPixels[i] = if (mask[i]) pixels[i] else Color.BLACK
            }

            val result = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            result.setPixels(resultPixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)

            // 如果原始图像缩小了，还原到原始尺寸
            if (scale < 1f) {
                Bitmap.createScaledBitmap(result, width, height, true)
            } else {
                result
            }
        } catch (e: Exception) {
            Timber.e(e, "TongueSegmenter: segmentation failed")
            bitmap // 返回原图作为降级
        }
    }
}