package com.bianque.health.engine.data

import com.bianque.health.bp.domain.model.BloodPressureResult
import com.bianque.health.engine.domain.HealthEngineRepository
import com.bianque.health.engine.domain.model.DiagnosisSummary
import com.bianque.health.engine.domain.model.HealthReport
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 健康引擎仓库实现。
 *
 * 串联体质分类 → 建议生成 → 组装完整 HealthReport。
 */
@Singleton
class HealthEngineRepositoryImpl @Inject constructor(
    private val bodyTypeClassifier: BodyTypeClassifier,
    private val recommendationEngine: RecommendationEngine
) : HealthEngineRepository {

    override suspend fun generateReport(
        userId: String,
        faceResult: FaceDiagnosisResult,
        tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult,
        bpResult: BloodPressureResult
    ): HealthReport {
        Timber.d("HealthEngineRepository: generating health report for user $userId")

        // 步骤1：体质分类
        val (bodyType, confidence) = bodyTypeClassifier.classify(
            faceResult, tongueResult, pulseResult, bpResult
        )

        // 步骤2：构建诊断摘要
        val diagnosisSummary = DiagnosisSummary(
            face = "面色${faceResult.overallComplexion}，光泽度${String.format("%.0f", faceResult.glossLevel * 100)}%",
            tongue = "${tongueResult.tongueColor}舌，${tongueResult.coatingColor}苔，${tongueResult.coatingThickness}苔，${tongueResult.tongueBody}质，${tongueResult.tongueShape}形",
            pulse = "脉率${pulseResult.pulseRate}次/分，${pulseResult.pulseType}",
            bloodPressure = "${bpResult.systolic}/${bpResult.diastolic} mmHg，心率${bpResult.heartRate} BPM"
        )

        // 步骤3：构建诊断数据 Map
        val diagnosisData = mapOf<String, Any>(
            "面诊" to diagnosisSummary.face,
            "舌诊" to diagnosisSummary.tongue,
            "脉诊" to diagnosisSummary.pulse,
            "血压" to diagnosisSummary.bloodPressure
        )

        // 步骤4：生成个性化建议
        val (recommendations, riskAssessment) = recommendationEngine.generate(
            bodyType, diagnosisData
        )

        Timber.d("HealthEngineRepository: report generated - ${bodyType.displayName}, ${recommendations.size} recommendations")

        return HealthReport(
            id = UUID.randomUUID().toString(),
            userId = userId,
            diagnosisSummary = diagnosisSummary,
            bodyType = bodyType,
            bodyTypeConfidence = confidence,
            recommendations = recommendations,
            riskAssessment = riskAssessment
        )
    }
}