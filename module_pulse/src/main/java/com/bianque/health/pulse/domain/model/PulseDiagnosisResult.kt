package com.bianque.health.pulse.domain.model

data class PulseDiagnosisResult(
    val pulseRate: Int,              // 脉率
    val pulseRhythm: String,         // 整齐/不齐
    val pulseStrength: String,       // 有力/无力
    val pulseType: String,           // 浮/沉/迟/数/滑/涩...
    val pulseFeatures: Map<String, Float>,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)