package com.bianque.health.pulse.domain.model

/**
 * rPPG非接触式脉搏+血压诊断结果。
 */
data class PulseDiagnosisResult(
    val pulseRate: Int,              // 脉率 BPM
    val pulseRhythm: String,         // 整齐/不齐
    val pulseStrength: String,       // 有力/无力
    val pulseType: String,           // 浮/沉/迟/数/滑/涩...
    val systolic: Int = 0,               // 收缩压 mmHg（rPPG估算）
    val diastolic: Int = 0,              // 舒张压 mmHg（rPPG估算）
    val pulseFeatures: Map<String, Float>,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)