package com.bianque.health.engine.domain.model

data class DiagnosisSummary(
    val face: String,
    val tongue: String,
    val pulse: String,
    val bloodPressure: String
)

data class Recommendation(
    val category: String,   // diet/exercise/lifestyle/emotional
    val content: String,
    val priority: Int
)

data class HealthReport(
    val id: String,
    val userId: String,
    val diagnosisSummary: DiagnosisSummary,
    val bodyType: BodyType,
    val bodyTypeConfidence: Float,
    val recommendations: List<Recommendation>,
    val riskAssessment: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)