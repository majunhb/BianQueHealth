package com.bianque.health.tongue.domain.model

data class TongueDiagnosisResult(
    val tongueColor: String,      // 淡白/红/绛/紫
    val coatingColor: String,     // 白/黄/灰/黑
    val coatingThickness: String, // 薄/厚
    val coatingMoisture: String,  // 润/燥
    val tongueShape: String,      // 正常/胖大/齿痕/裂纹
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)