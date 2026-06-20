package com.bianque.health.base.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX 辅助类 — 封装摄像头预览 + 帧回调
 *
 * 用法:
 *   CameraHelper.bind(lifecycleOwner, previewView) { bitmap -> ... }
 *   CameraHelper.unbind()
 */
object CameraHelper {

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var onFrameCallback: ((Bitmap) -> Unit)? = null
    private var isBound = false

    /**
     * 绑定摄像头预览 + 帧分析
     * @param cameraFacing 默认后置摄像头，面诊用前置
     */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraFacing: Int = CameraSelector.LENS_FACING_BACK,
        onFrame: (Bitmap) -> Unit
    ) {
        onFrameCallback = onFrame
        if (isBound) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera(lifecycleOwner, previewView, cameraFacing)
                isBound = true
            } catch (e: Exception) {
                Timber.e(e, "CameraHelper: bind failed")
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    fun unbind() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        cameraProvider = null
        onFrameCallback = null
        isBound = false
    }

    fun isActive(): Boolean = isBound

    private fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraFacing: Int
    ) {
        val provider = cameraProvider ?: return

        // 预览
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // 帧分析
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    bitmap?.let { bmp ->
                        onFrameCallback?.invoke(bmp)
                    }
                }
            }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
        } catch (e: Exception) {
            Timber.e(e, "CameraHelper: bindToLifecycle failed")
        }
    }

    /**
     * YUV_420_888 → JPEG → Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val planes = imageProxy.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val width = imageProxy.width
            val height = imageProxy.height

            val ySize = width * height
            val uvSize = (width / 2) * (height / 2)
            val nv21 = ByteArray(ySize + uvSize * 2)

            // Y 平面
            val yBuffer = yPlane.buffer
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            if (yPixelStride == 1) {
                for (row in 0 until height) {
                    yBuffer.position(row * yRowStride)
                    yBuffer.get(nv21, row * width, width)
                }
            } else {
                for (row in 0 until height) {
                    for (col in 0 until width) {
                        nv21[row * width + col] = yBuffer.get(row * yRowStride + col * yPixelStride)
                    }
                }
            }

            // UV 交错
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            val uRowStride = uPlane.rowStride
            val vRowStride = vPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vPixelStride = vPlane.pixelStride
            val uvHeight = height / 2
            val uvWidth = width / 2

            var uvPos = ySize
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    nv21[uvPos++] = vBuffer.get(row * vRowStride + col * vPixelStride)
                    nv21[uvPos++] = uBuffer.get(row * uRowStride + col * uPixelStride)
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) {
            Timber.w(e, "CameraHelper: imageProxyToBitmap failed")
            null
        }
    }
}