package com.bianque.health.pulse.domain

import com.bianque.health.pulse.domain.model.PulseDiagnosisResult

interface PulseDiagnosisRepository {
    suspend fun analyze(rawSignal: FloatArray): PulseDiagnosisResult
}