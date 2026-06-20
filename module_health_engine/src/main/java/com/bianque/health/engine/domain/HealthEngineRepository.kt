package com.bianque.health.engine.domain

import com.bianque.health.bp.domain.model.BloodPressureResult
import com.bianque.health.engine.domain.model.HealthReport
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult

interface HealthEngineRepository {
    suspend fun generateReport(
        userId: String,
        faceResult: FaceDiagnosisResult,
        tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult,
        bpResult: BloodPressureResult
    ): HealthReport
}