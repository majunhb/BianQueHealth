package com.bianque.health.engine.data

import com.bianque.health.bp.domain.model.BloodPressureResult
import com.bianque.health.engine.domain.model.BodyType
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyTypeClassifier @Inject constructor() {

    suspend fun classify(
        faceResult: FaceDiagnosisResult,
        tongueResult: TongueDiagnosisResult,
        pulseResult: PulseDiagnosisResult,
        bpResult: BloodPressureResult
    ): Pair<BodyType, Float> = withContext(Dispatchers.Default) {
        // TODO: Nine-constitution (九种体质) TCM body type classification
        Timber.d("BodyTypeClassifier: classifying body type from multi-modal diagnosis...")
        Pair(BodyType.BALANCED, 0.85f)
    }
}