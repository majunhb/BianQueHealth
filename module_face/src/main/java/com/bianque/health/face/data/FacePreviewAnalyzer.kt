package com.bianque.health.face.data

import android.graphics.Bitmap
import android.graphics.Rect
import com.bianque.health.base.analysis.ImageQualityAnalyzer.DetectionState
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实时面部预览分析器 — 简化版：人脸与引导框重叠即触发扫描。
 *
 * 核心逻辑：
 * 1. ML Kit 检测人脸边界框
 * 2. 计算人脸框与画面中央引导框的重叠率
 * 3. 重叠率 > 50% → READY（开始扫描）
 * 4. 重叠率 20%-50% → POOR_QUALITY（提示调整位置）
 * 5. 未检测到或重叠率 < 20% → NOT_DETECTED
 */
@Singleton
class FacePreviewAnalyzer @Inject constructor() {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)
            .enableTracking()
            .build()
    )

    private var lastAnalysisTime = 0L
    private val analysisIntervalMs = 300L

    data class PreviewResult(
        val faceFound: Boolean,
        val boundingBox: Rect?,
        val imageWidth: Int,
        val imageHeight: Int,
        val faceSizeRatio: Float,
        val headEulerAngleY: Float,
        val headEulerAngleZ: Float,
        val leftEyeOpen: Float,
        val rightEyeOpen: Float,
        val detectionState: DetectionState,
        val guidanceMessage: String,
        /** 人脸框与引导框的重叠率 0-1 */
        val overlapRatio: Float = 0f
    )

    suspend fun analyze(bitmap: Bitmap): PreviewResult? = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < analysisIntervalMs) {
            return@withContext null
        }
        lastAnalysisTime = now

        val maxDim = 640
        val processedBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = (maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap

        val imageWidth = processedBitmap.width
        val imageHeight = processedBitmap.height
        val inputImage = InputImage.fromBitmap(processedBitmap, 0)

        try {
            val faces = Tasks.await(detector.process(inputImage))

            if (faces.isEmpty()) {
                return@withContext PreviewResult(
                    faceFound = false,
                    boundingBox = null,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    faceSizeRatio = 0f,
                    headEulerAngleY = 0f,
                    headEulerAngleZ = 0f,
                    leftEyeOpen = 0f,
                    rightEyeOpen = 0f,
                    detectionState = DetectionState.NOT_DETECTED,
                    guidanceMessage = "请将面部放入轮廓框内"
                )
            }

            val face = faces[0]
            val bounds = face.boundingBox ?: return@withContext PreviewResult(
                faceFound = false,
                boundingBox = null,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                faceSizeRatio = 0f,
                headEulerAngleY = 0f,
                headEulerAngleZ = 0f,
                leftEyeOpen = 0f,
                rightEyeOpen = 0f,
                detectionState = DetectionState.NOT_DETECTED,
                guidanceMessage = "请将面部放入轮廓框内"
            )

            val faceArea = bounds.width() * bounds.height()
            val frameArea = imageWidth * imageHeight
            val faceSizeRatio = faceArea.toFloat() / frameArea

            val yaw = face.headEulerAngleY ?: 0f
            val roll = face.headEulerAngleZ ?: 0f
            val leftEye = face.leftEyeOpenProbability ?: 1f
            val rightEye = face.rightEyeOpenProbability ?: 1f

            // 计算人脸框与画面中央引导框的重叠率
            val guideRect = computeGuideRect(imageWidth, imageHeight)
            val overlapRatio = computeOverlapRatio(bounds, guideRect)

            Timber.d("FacePreviewAnalyzer: face found, size=%.1f%%, overlap=%.1f%%",
                faceSizeRatio * 100, overlapRatio * 100)

            val (state, message) = assessOverlap(overlapRatio)

            PreviewResult(
                faceFound = true,
                boundingBox = bounds,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                faceSizeRatio = faceSizeRatio,
                headEulerAngleY = yaw,
                headEulerAngleZ = roll,
                leftEyeOpen = leftEye,
                rightEyeOpen = rightEye,
                detectionState = state,
                guidanceMessage = message,
                overlapRatio = overlapRatio
            )
        } catch (e: Exception) {
            Timber.w(e, "FacePreviewAnalyzer: detection failed")
            PreviewResult(
                faceFound = false,
                boundingBox = null,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                faceSizeRatio = 0f,
                headEulerAngleY = 0f,
                headEulerAngleZ = 0f,
                leftEyeOpen = 0f,
                rightEyeOpen = 0f,
                detectionState = DetectionState.NOT_DETECTED,
                guidanceMessage = "请将面部放入轮廓框内"
            )
        }
    }

    /**
     * 计算画面中央引导框在图像坐标中的位置。
     * 与 UI 层 FaceOutlineOverlay 的默认椭圆范围一致：
     * 居中，宽 52% 画面宽，高 62% 画面高。
     */
    private fun computeGuideRect(imageWidth: Int, imageHeight: Int): Rect {
        val gw = (imageWidth * 0.52f).toInt()
        val gh = (imageHeight * 0.62f).toInt()
        val gx = (imageWidth - gw) / 2
        val gy = (imageHeight - gh) / 2
        return Rect(gx, gy, gx + gw, gy + gh)
    }

    /**
     * 计算人脸框与引导框的重叠率。
     * 重叠率 = 交集面积 / 人脸框面积
     */
    private fun computeOverlapRatio(faceRect: Rect, guideRect: Rect): Float {
        val intersectLeft = maxOf(faceRect.left, guideRect.left)
        val intersectTop = maxOf(faceRect.top, guideRect.top)
        val intersectRight = minOf(faceRect.right, guideRect.right)
        val intersectBottom = minOf(faceRect.bottom, guideRect.bottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }

        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val faceArea = faceRect.width() * faceRect.height()
        return if (faceArea > 0) intersectArea.toFloat() / faceArea else 0f
    }

    /**
     * 基于重叠率判定状态。
     * - 重叠 > 50%：人脸大部分在框内 → READY
     * - 重叠 20%-50%：部分在框内 → POOR_QUALITY，提示调整
     * - 重叠 < 20%：不在框内 → NOT_DETECTED
     */
    private fun assessOverlap(overlapRatio: Float): Pair<DetectionState, String> {
        return when {
            overlapRatio > 0.5f -> DetectionState.READY to "定位成功，正在自动检测…"
            overlapRatio > 0.2f -> DetectionState.POOR_QUALITY to "请将面部对准轮廓框"
            else -> DetectionState.NOT_DETECTED to "请将面部放入轮廓框内"
        }
    }
}