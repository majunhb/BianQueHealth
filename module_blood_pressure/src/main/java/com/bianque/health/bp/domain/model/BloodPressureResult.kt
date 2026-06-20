package com.bianque.health.bp.domain.model

data class BloodPressureResult(
    val systolic: Int,            // 收缩压 mmHg
    val diastolic: Int,           // 舒张压 mmHg
    val heartRate: Int,           // 心率 bpm
    val measurementMethod: String, // BLE / PPG
    val deviceName: String?,
    val timestamp: Long = System.currentTimeMillis()
)