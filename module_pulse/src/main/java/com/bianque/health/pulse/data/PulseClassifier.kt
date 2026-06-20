package com.bianque.health.pulse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PulseClassifier @Inject constructor() {

    suspend fun classify(features: Map<String, Float>): String = withContext(Dispatchers.Default) {
        // TODO: LSTM-based pulse type classification
        Timber.d("PulseClassifier: classifying pulse from features: $features")
        "平"
    }
}