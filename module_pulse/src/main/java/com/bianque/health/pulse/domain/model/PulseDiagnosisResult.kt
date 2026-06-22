package com.bianque.health.pulse.domain.model

import com.bianque.health.pulse.data.SanJiaoSimulator

/**
 * rPPG非接触式脉搏+血压+HRV+三部九候综合诊断结果。
 */
data class PulseDiagnosisResult(
    val pulseRate: Int,              // 脉率 BPM
    val pulseRhythm: String,         // 整齐/不齐
    val pulseStrength: String,       // 有力/无力
    val pulseType: String,           // 浮/沉/迟/数/滑/涩/弦/细/结代...
    val systolic: Int = 0,           // 收缩压 mmHg（rPPG估算）
    val diastolic: Int = 0,          // 舒张压 mmHg（rPPG估算）
    val pulseFeatures: Map<String, Float>,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),

    // === HRV 时域指标 ===
    /** SDNN：正常窦性心搏间期标准差 [ms] */
    val hrvSdnn: Float = 0f,
    /** RMSSD：相邻NN间期差值的均方根 [ms] */
    val hrvRmssd: Float = 0f,
    /** LF/HF比值：自主神经平衡指标 */
    val hrvLfHfRatio: Float = 0f,

    // === 三部九候模拟结果 ===
    /** 三部九候脉诊模拟结果 */
    val sanJiaoResult: SanJiaoSimulator.SanJiaoResult? = null,

    // === 自主神经平衡评估 ===
    /** 自主神经平衡状态描述 */
    val autonomicBalance: String = ""
)