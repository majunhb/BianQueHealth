package com.bianque.health.tongue.data

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TongueSegmenter @Inject constructor() {

    suspend fun segment(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        // TODO: U-Net tongue segmentation model integration
        Timber.d("TongueSegmenter: segmenting tongue region...")
        // Return a mask bitmap — placeholder
        bitmap
    }
}