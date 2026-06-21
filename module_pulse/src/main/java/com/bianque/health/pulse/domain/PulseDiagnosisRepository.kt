package com.bianque.health.pulse.domain

import android.graphics.Bitmap
import com.bianque.health.pulse.data.PulseSignalProcessor
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PulseDiagnosisRepository @Inject constructor(
    private val signalProcessor: PulseSignalProcessor
) {
    suspend fun addFrame(bitmap: Bitmap): FloatArray? = signalProcessor.addFrame(bitmap)

    suspend fun analyze(): PulseDiagnosisResult? = signalProcessor.processSignal()

    fun getFrameCount(): Int = signalProcessor.getFrameCount()

    fun reset() = signalProcessor.reset()
}