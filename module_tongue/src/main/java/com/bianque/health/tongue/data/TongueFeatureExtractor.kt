package com.bianque.health.tongue.data

import android.graphics.Bitmap
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TongueFeatureExtractor @Inject constructor() {

    suspend fun extract(maskedBitmap: Bitmap): TongueDiagnosisResult = withContext(Dispatchers.Default) {
        // TODO: Extract tongue features from masked image
        Timber.d("TongueFeatureExtractor: extracting features from masked tongue image...")
        TongueDiagnosisResult(
            tongueColor = "淡白",
            coatingColor = "白",
            coatingThickness = "薄",
            coatingMoisture = "润",
            tongueShape = "正常",
            confidence = 0.82f
        )
    }
}