package com.bianque.health.face.data

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Singleton
class ColorAnalyzer @Inject constructor() {

    data class LabColor(val l: Float, val a: Float, val b: Float)

    fun analyzeRegion(bitmap: Bitmap, region: Rect): LabColor {
        // TODO: CIE Lab color space conversion
        return LabColor(50f, 0f, 0f)
    }

    fun classifyComplexion(lab: LabColor): String = when {
        lab.b > 10 -> "偏黄"
        lab.a > 8 -> "偏红"
        lab.l > 70 -> "偏白"
        else -> "正常"
    }
}