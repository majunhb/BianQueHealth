package com.bianque.health.face.domain

import android.graphics.Bitmap
import com.bianque.health.face.domain.model.FaceDiagnosisResult

interface FaceDiagnosisRepository {
    suspend fun analyze(bitmap: Bitmap): FaceDiagnosisResult
}