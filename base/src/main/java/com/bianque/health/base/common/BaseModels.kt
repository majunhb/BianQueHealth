package com.bianque.health.base.common

data class UserProfile(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val heightCm: Float,
    val weightKg: Float
)

data class HealthRecord(
    val id: String,
    val userId: String,
    val type: String,
    val result: String,
    val timestamp: Long,
    val confidence: Float
)

enum class HealthModule(
    val displayName: String,
    val description: String,
    val icon: String
) {
    FACE_SCAN("面诊", "面部扫描健康检测", "face_scan"),
    TONGUE_SCAN("舌诊", "舌苔扫描健康检测", "tongue_scan"),
    BLOOD_PRESSURE("血压", "血压测量与记录", "blood_pressure"),
    PULSE_DIAGNOSIS("脉诊", "脉搏诊断分析", "pulse_diagnosis"),
    HEALTH_REPORT("健康报告", "综合健康报告", "health_report")
}