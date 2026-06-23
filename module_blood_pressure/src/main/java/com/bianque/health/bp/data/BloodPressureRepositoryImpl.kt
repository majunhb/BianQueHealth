package com.bianque.health.bp.data

import android.graphics.Bitmap
import com.bianque.health.bp.domain.BloodPressureRepository
import com.bianque.health.bp.domain.model.BloodPressureResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 血压测量仓库实现。
 *
 * 光感测压（rPPG）路径：CameraX 实时帧 → PpgAnalyzer → BVP提取 → 血压估算
 * 蓝牙测压（BLE）路径：BleManager 连接传统血压计（待实现）
 */
@Singleton
class BloodPressureRepositoryImpl @Inject constructor(
    private val ppgAnalyzer: PpgAnalyzer
) : BloodPressureRepository {

    override suspend fun measureViaBle(): BloodPressureResult? {
        // BLE 蓝牙血压计测量尚未实现，预留接口
        Timber.w("BloodPressureRepositoryImpl: BLE measurement not yet implemented")
        return null
    }

    override suspend fun measureViaPpg(frames: List<Bitmap>): BloodPressureResult {
        Timber.d("BloodPressureRepositoryImpl: starting rPPG measurement with %d frames", frames.size)
        return ppgAnalyzer.analyzePpg(frames)
    }

    /**
     * 带进度回调的 rPPG 测量。
     */
    suspend fun measureViaPpgWithProgress(
        frames: List<Bitmap>,
        onProgress: (PpgAnalyzer.MeasurementProgress) -> Unit
    ): BloodPressureResult {
        return ppgAnalyzer.analyzePpg(frames, onProgress)
    }
}