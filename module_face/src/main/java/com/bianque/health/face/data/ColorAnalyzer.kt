package com.bianque.health.face.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

@Singleton
class ColorAnalyzer @Inject constructor() {

    data class LabColor(val l: Float, val a: Float, val b: Float)

    /**
     * 分析指定区域的平均 CIELAB 颜色
     */
    suspend fun analyzeRegion(bitmap: Bitmap, region: Rect): LabColor = withContext(Dispatchers.Default) {
        val clampedLeft = region.left.coerceIn(0, bitmap.width - 1)
        val clampedTop = region.top.coerceIn(0, bitmap.height - 1)
        val clampedRight = region.right.coerceIn(1, bitmap.width)
        val clampedBottom = region.bottom.coerceIn(1, bitmap.height)

        var sumL = 0f
        var sumA = 0f
        var sumB = 0f
        var count = 0

        // 采样步长 4 以提升性能
        val step = 4
        for (y in clampedTop until clampedBottom step step) {
            for (x in clampedLeft until clampedRight step step) {
                val pixel = bitmap.getPixel(x, y)
                val lab = rgbToLab(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                sumL += lab.l
                sumA += lab.a
                sumB += lab.b
                count++
            }
        }

        if (count == 0) return@withContext LabColor(50f, 0f, 0f)
        LabColor(sumL / count, sumA / count, sumB / count)
    }

    /**
     * 根据 CIELAB 值分类面色
     * 中医面诊参考:
     *   - b* > 8: 偏黄（脾虚/湿盛）
     *   - a* > 6: 偏红（热证）
     *   - L* > 75: 偏白（血虚/气虚）
     *   - L* < 25: 偏黑/晦暗（肾虚/血瘀）
     */
    fun classifyComplexion(lab: LabColor): String = when {
        lab.b > 8 -> "偏黄"
        lab.a > 6 -> "偏红"
        lab.l > 75 -> "偏白"
        lab.l < 25 -> "晦暗"
        else -> "正常"
    }

    /**
     * 计算光泽度 (基于 L* 通道的局部方差)
     */
    suspend fun computeGlossiness(bitmap: Bitmap, region: Rect): Float = withContext(Dispatchers.Default) {
        val clampedLeft = region.left.coerceIn(0, bitmap.width - 1)
        val clampedTop = region.top.coerceIn(0, bitmap.height - 1)
        val clampedRight = region.right.coerceIn(1, bitmap.width)
        val clampedBottom = region.bottom.coerceIn(1, bitmap.height)

        val lValues = mutableListOf<Float>()
        val step = 4
        for (y in clampedTop until clampedBottom step step) {
            for (x in clampedLeft until clampedRight step step) {
                val pixel = bitmap.getPixel(x, y)
                lValues.add(rgbToLab(Color.red(pixel), Color.green(pixel), Color.blue(pixel)).l)
            }
        }
        if (lValues.size < 2) return@withContext 0.5f
        val mean = lValues.average().toFloat()
        val variance = lValues.map { (it - mean) * (it - mean) }.average().toFloat()
        // 归一化光泽度: 方差越大光泽越好
        (variance / 100f).coerceIn(0f, 1f)
    }

    /**
     * sRGB → CIELAB (D65 光源)
     */
    private fun rgbToLab(r: Int, g: Int, b: Int): LabColor {
        // Step 1: sRGB → linear RGB
        var rr = r / 255.0
        var gg = g / 255.0
        var bb = b / 255.0

        rr = if (rr > 0.04045) Math.pow((rr + 0.055) / 1.055, 2.4) else rr / 12.92
        gg = if (gg > 0.04045) Math.pow((gg + 0.055) / 1.055, 2.4) else gg / 12.92
        bb = if (bb > 0.04045) Math.pow((bb + 0.055) / 1.055, 2.4) else bb / 12.92

        // Step 2: linear RGB → XYZ (D65)
        val x = (rr * 0.4124564 + gg * 0.3575761 + bb * 0.1804375) * 100.0
        val y = (rr * 0.2126729 + gg * 0.7151522 + bb * 0.0721750) * 100.0
        val z = (rr * 0.0193339 + gg * 0.1191920 + bb * 0.9503041) * 100.0

        // Step 3: XYZ → CIELAB
        val xn = 95.047
        val yn = 100.000
        val zn = 108.883

        fun f(t: Double): Double = if (t > 0.008856) Math.cbrt(t) else (7.787 * t + 16.0 / 116.0)

        val fy = f(y / yn)
        val l = (116.0 * fy - 16.0).toFloat()
        val a = (500.0 * (f(x / xn) - fy)).toFloat()
        val bVal = (200.0 * (fy - f(z / zn))).toFloat()

        return LabColor(l, a, bVal)
    }
}