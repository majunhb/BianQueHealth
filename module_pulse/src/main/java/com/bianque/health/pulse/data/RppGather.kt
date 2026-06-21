package com.bianque.health.pulse.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * rPPG面部ROI提取器。
 * 基于YCrCb色彩空间进行皮肤分割，提取额头和脸颊区域作为感兴趣区域。
 */
object RppGather {

    /**
     * 计算ROI区域的平均RGB值。
     * 使用YCrCb皮肤分割排除非皮肤像素，降低噪声干扰。
     */
    fun extractRoiRgb(bitmap: Bitmap, regionRatio: Float = 0.25f): FloatArray? {
        val width = bitmap.width
        val height = bitmap.height

        // 额头ROI：画面顶部25%区域
        val roiX = (width * (1f - regionRatio) / 2f).toInt()
        val roiY = (height * 0.05f).toInt()
        val roiW = (width * regionRatio).toInt()
        val roiH = (height * regionRatio * 0.7f).toInt()

        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        var count = 0
        val step = 4

        for (y in roiY until roiY + roiH step step) {
            for (x in roiX until roiX + roiW step step) {
                if (x < 0 || x >= width || y < 0 || y >= height) continue
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // YCrCb皮肤分割
                if (isSkinPixel(r, g, b)) {
                    rSum += r
                    gSum += g
                    bSum += b
                    count++
                }
            }
        }

        if (count < 100) return null
        return floatArrayOf(rSum / count, gSum / count, bSum / count)
    }

    /**
     * 提取面部多个ROI区域的平均RGB。
     * 覆盖额头、左脸颊、右脸颊三个区域，提高信号稳定性。
     */
    fun extractMultiRoiRgb(bitmap: Bitmap): FloatArray? {
        val width = bitmap.width
        val height = bitmap.height

        // 额头区域
        val foreheadX = (width * 0.3f).toInt()
        val foreheadY = (height * 0.05f).toInt()
        val foreheadW = (width * 0.4f).toInt()
        val foreheadH = (height * 0.15f).toInt()

        // 左脸颊
        val leftCheekX = (width * 0.15f).toInt()
        val leftCheekY = (height * 0.3f).toInt()
        val cheekW = (width * 0.2f).toInt()
        val cheekH = (height * 0.2f).toInt()

        // 右脸颊
        val rightCheekX = (width * 0.65f).toInt()
        val rightCheekY = leftCheekY

        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        var count = 0
        val step = 4

        val regions = listOf(
            intArrayOf(foreheadX, foreheadY, foreheadW, foreheadH),
            intArrayOf(leftCheekX, leftCheekY, cheekW, cheekH),
            intArrayOf(rightCheekX, rightCheekY, cheekW, cheekH)
        )

        for (region in regions) {
            val rx = region[0]; val ry = region[1]; val rw = region[2]; val rh = region[3]
            for (y in ry until ry + rh step step) {
                for (x in rx until rx + rw step step) {
                    if (x < 0 || x >= width || y < 0 || y >= height) continue
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    if (isSkinPixel(r, g, b)) {
                        rSum += r
                        gSum += g
                        bSum += b
                        count++
                    }
                }
            }
        }

        if (count < 200) return null
        return floatArrayOf(rSum / count, gSum / count, bSum / count)
    }

    /**
     * YCrCb皮肤分割。
     * 条件：Y>80, 135<Cr<180, 85<Cb<135, 基于经验阈值。
     */
    private fun isSkinPixel(r: Int, g: Int, b: Int): Boolean {
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val cb = 128 - 0.168736f * r - 0.331264f * g + 0.5f * b
        val cr = 128 + 0.5f * r - 0.418688f * g - 0.081312f * b
        return y > 80 && cr in 135f..180f && cb in 85f..135f
    }
}