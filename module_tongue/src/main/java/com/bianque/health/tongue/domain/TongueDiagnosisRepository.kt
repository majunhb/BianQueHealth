package com.bianque.health.tongue.domain

import android.graphics.Bitmap
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult

interface TongueDiagnosisRepository {
    suspend fun analyze(bitmap: Bitmap): TongueDiagnosisResult
}