package com.bianque.health.face.domain.model

data class FaceRegion(
    val name: String,
    val color: String,
    val brightness: Float,
    val redGreen: Float,
    val yellowBlue: Float
)

data class FaceDiagnosisResult(
    val overallComplexion: String, // 偏黄/偏白/偏红/正常
    val glossLevel: Float, // 0-1 光泽度
    val regions: List<FaceRegion>,
    val abnormalities: List<String>,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)