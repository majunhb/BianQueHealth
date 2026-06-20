package com.bianque.health.bp.domain

import android.graphics.Bitmap
import com.bianque.health.bp.domain.model.BloodPressureResult

interface BloodPressureRepository {
    suspend fun measureViaBle(): BloodPressureResult?
    suspend fun measureViaPpg(frames: List<Bitmap>): BloodPressureResult
}