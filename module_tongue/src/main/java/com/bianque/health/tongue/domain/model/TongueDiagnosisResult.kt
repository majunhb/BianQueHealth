package com.bianque.health.tongue.domain.model

/**
 * 舌诊结果 — 八维特征模型。
 *
 * 基于设计文档的舌象分析模块：
 * 1. 舌色、2. 苔色、3. 苔厚、4. 苔质、5. 舌形、6. 舌质、7. 舌下络脉、8. 舌态
 */
data class TongueDiagnosisResult(
    /** 舌色: 淡白/淡红/红/红绛/紫暗 */
    val tongueColor: String,
    /** 苔色: 白/黄/灰/黑/黄白 */
    val coatingColor: String,
    /** 苔厚: 薄/厚/腻 */
    val coatingThickness: String,
    /** 苔质: 润/燥/滑 */
    val coatingMoisture: String,
    /** 舌形: 正常/胖大/瘦薄/齿痕/裂纹 */
    val tongueShape: String,
    /** 舌质: 正常/老/嫩 */
    val tongueBody: String,
    /** 舌下络脉: 正常/怒张/待检测 */
    val sublingualVein: String,
    /** 舌态: 灵活/歪斜/僵硬/待检测 */
    val tongueMobility: String,
    /** 置信度: 0.0-1.0 */
    val confidence: Float,
    /** 时间戳 */
    val timestamp: Long = System.currentTimeMillis()
)