package com.bianque.health.pulse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 脉象分类器 — 基于rPPG信号的脉象中医分类。
 */
@Singleton
class PulseClassifier @Inject constructor() {

    data class PulseType(
        val name: String,       // 浮/沉/迟/数/滑/涩/平
        val description: String // 中医描述
    )

    suspend fun classifyPulseType(rate: Int, signalVariance: Float): String = withContext(Dispatchers.Default) {
        Timber.d("PulseClassifier: classifying pulse rate=$rate, variance=$signalVariance")
        when {
            rate < 60 -> "迟脉"
            rate > 90 -> "数脉"
            signalVariance > 0.001f -> "滑脉"
            else -> "平脉"
        }
    }
}