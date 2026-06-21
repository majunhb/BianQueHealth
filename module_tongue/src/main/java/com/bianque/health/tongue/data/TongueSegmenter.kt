package com.bianque.health.tongue.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Singleton
class TongueSegmenter @Inject constructor() {

    data class SegmentationResult(
        val maskedBitmap: Bitmap,
        val tongueAreaRatio: Float,
        val contourPoints: List<Pair<Int, Int>>
    )

    /**
     * 对输入图像进行舌体分割。
     * 改进策略：放宽HSV阈值 + 连通域最大面积筛选 + 几何验证
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

            // ★ 放宽的HSV阈值 — 覆盖更广的舌体颜色范围
            for (i in pixels.indices) {
                val pixel = pixels[i]
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0]  // 0-360
                val s = hsv[1]  // 0-1
                val v = hsv[2]  // 0-1

                val isTongueTissue = when {
                    // 核心红色区：淡红舌、红舌
                    h in 0f..25f && s in 0.10f..0.70f && v in 0.15f..0.80f -> true
                    // 暗红/绛红区
                    h in 340f..360f && s in 0.10f..0.65f && v in 0.10f..0.60f -> true
                    // 淡白舌（极淡红，偏粉白）
                    h in 0f..20f && s in 0.05f..0.30f && v in 0.25f..0.85f -> true
                    // 偏紫暗舌
                    h in 300f..340f && s in 0.10f..0.50f && v in 0.10f..0.50f -> true
                    else -> false
                }

                if (isTongueTissue) {
                    mask[i] = true
                }
            }

            // ★ 形态学操作：闭运算填充小孔洞
            // 膨胀 (3x3 → 5x5 以更好填充)
            val dilated = BooleanArray(mask.size)
            for (y in 2 until scaledHeight - 2) {
                for (x in 2 until scaledWidth - 2) {
                    val idx = y * scaledWidth + x
                    if (mask[idx]) {
                        for (dy in -2..2) {
                            for (dx in -2..2) {
                                val ny = y + dy
                                val nx = x + dx
                                if (ny in 0 until scaledHeight && nx in 0 until scaledWidth) {
                                    dilated[ny * scaledWidth + nx] = true
                                }
                            }
                        }
                    }
                }
            }

            // 腐蚀恢复边界
            val eroded = BooleanArray(mask.size)
            for (y in 2 until scaledHeight - 2) {
                for (x in 2 until scaledWidth - 2) {
                    var allSet = true
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            val ny = y + dy
                            val nx = x + dx
                            if (ny in 0 until scaledHeight && nx in 0 until scaledWidth) {
                                if (!dilated[ny * scaledWidth + nx]) {
                                    allSet = false
                                }
                            }
                        }
                    }
                    eroded[y * scaledWidth + x] = allSet
                }
            }

            // ★ 连通域分析：只保留最大连通域（消除嘴唇、皮肤等干扰）
            val labels = IntArray(scaledWidth * scaledHeight) { -1 }
            val componentSizes = mutableListOf<Int>()
            var currentLabel = 0

            for (y in 0 until scaledHeight) {
                for (x in 0 until scaledWidth) {
                    val idx = y * scaledWidth + x
                    if (eroded[idx] && labels[idx] == -1) {
                        // BFS flood fill
                        val queue = ArrayDeque<Int>()
                        queue.add(idx)
                        labels[idx] = currentLabel
                        var size = 0
                        while (queue.isNotEmpty()) {
                            val cur = queue.removeFirst()
                            size++
                            val cx = cur % scaledWidth
                            val cy = cur / scaledWidth
                            for (dy in -1..1) {
                                for (dx in -1..1) {
                                    val nx = cx + dx
                                    val ny = cy + dy
                                    if (nx in 0 until scaledWidth && ny in 0 until scaledHeight) {
                                        val nIdx = ny * scaledWidth + nx
                                        if (eroded[nIdx] && labels[nIdx] == -1) {
                                            labels[nIdx] = currentLabel
                                            queue.add(nIdx)
                                        }
                                    }
                                }
                            }
                        }
                        componentSizes.add(size)
                        currentLabel++
                    }
                }
            }

            // 只保留最大连通域
            val finalMask = BooleanArray(scaledWidth * scaledHeight)
            if (componentSizes.isNotEmpty()) {
                val maxLabel = componentSizes.indices.maxByOrNull { componentSizes[it] } ?: 0
                val maxSize = componentSizes[maxLabel]

                for (i in finalMask.indices) {
                    finalMask[i] = labels[i] == maxLabel
                }

                // 验证最大连通域面积是否合理
                val totalPixels = scaledWidth * scaledHeight
                val areaRatio = maxSize.toFloat() / totalPixels
                if (areaRatio < 0.01f || areaRatio > 0.80f) {
                    Timber.w("TongueSegmenter: largest component area ratio %.2f out of range", areaRatio)
                    // 太小或太大，可能不是舌体
                    if (areaRatio < 0.01f) {
                        // 几乎没检测到，返回空掩码
                        return@withContext createEmptyMask(scaledWidth, scaledHeight, width, height, scale)
                    }
                }
            } else {
                Timber.w("TongueSegmenter: no connected components found")
                return@withContext createEmptyMask(scaledWidth, scaledHeight, width, height, scale)
            }

            // ★ 几何验证：最大连通域应在画面中心区域
            var sumX = 0L
            var sumY = 0L
            var count = 0
            for (y in 0 until scaledHeight) {
                for (x in 0 until scaledWidth) {
                    if (finalMask[y * scaledWidth + x]) {
                        sumX += x
                        sumY += y
                        count++
                    }
                }
            }

            if (count > 0) {
                val centroidX = sumX.toFloat() / count
                val centroidY = sumY.toFloat() / count
                val centerX = scaledWidth / 2f
                val centerY = scaledHeight / 2f
                val maxOffset = maxOf(scaledWidth, scaledHeight) * 0.35f

                if (abs(centroidX - centerX) > maxOffset || abs(centroidY - centerY) > maxOffset) {
                    Timber.w("TongueSegmenter: centroid too far from center (%.1f, %.1f), rejecting",
                        centroidX, centroidY)
                    return@withContext createEmptyMask(scaledWidth, scaledHeight, width, height, scale)
                }
            }

            // 生成掩码图像
            val resultPixels = IntArray(scaledWidth * scaledHeight)
            for (i in pixels.indices) {
                resultPixels[i] = if (finalMask[i]) pixels[i] else Color.BLACK
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

    /**
     * 创建全黑掩码 — 当分割失败时返回，让 FeatureExtractor 知道没有舌体
     */
    private fun createEmptyMask(
        scaledWidth: Int, scaledHeight: Int,
        originalWidth: Int, originalHeight: Int, scale: Float
    ): Bitmap {
        val result = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val emptyPixels = IntArray(scaledWidth * scaledHeight) { Color.BLACK }
        result.setPixels(emptyPixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(result, originalWidth, originalHeight, true)
        } else {
            result
        }
    }
}
