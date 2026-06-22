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

    /** 基础分析：心率+血压+基础脉象分类 */
    suspend fun analyze(): PulseDiagnosisResult? = signalProcessor.processSignal()

    /** 全面分析：心率+血压+HRV+三部九候+增强脉象分类 */
    suspend fun analyzeFull(): PulseDiagnosisResult? = signalProcessor.analyzeFull()

    fun getFrameCount(): Int = signalProcessor.getFrameCount()

    fun reset() = signalProcessor.reset()
}