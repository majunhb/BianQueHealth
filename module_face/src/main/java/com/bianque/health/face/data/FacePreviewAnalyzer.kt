package com.bianque.health.face.data

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.bianque.health.base.analysis.ImageQualityAnalyzer.DetectionState
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实时面部预览分析器 — 使用 ML Kit 人脸轮廓点绘制真实面部轮廓线。
 *
 * 对标 dlib 68点方案：ML Kit 的 FaceContour.FACE 提供 36 个面部轮廓点，
 * 可绘制出贴合真实人脸形状的轮廓线。
 *
 * 判定逻辑极简：
 * - 检测到人脸 → READY（开始扫描）
 * - 未检测到 → NOT_DETECTED
 */
@Singleton
class FacePreviewAnalyzer @Inject constructor() {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
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
        /** ML Kit 面部轮廓点 (FaceContour.FACE)，图像坐标系 */
        val contourPoints: List<PointF> = emptyList()
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
                    guidanceMessage = "请将面部置于画面中央"
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
                guidanceMessage = "请将面部置于画面中央"
            )

            val faceArea = bounds.width() * bounds.height()
            val frameArea = imageWidth * imageHeight
            val faceSizeRatio = faceArea.toFloat() / frameArea

            val yaw = face.headEulerAngleY ?: 0f
            val roll = face.headEulerAngleZ ?: 0f
            val leftEye = face.leftEyeOpenProbability ?: 1f
            val rightEye = face.rightEyeOpenProbability ?: 1f

            // 提取面部轮廓点 (对标 dlib 68点中的 jaw_line[0:17])
            val contour = face.getContour(FaceContour.FACE)
            val contourPoints = contour?.points ?: emptyList()

            Timber.d("FacePreviewAnalyzer: face found, contour points=%d", contourPoints.size)

            // 简化为：检测到人脸即为 READY
            val state = DetectionState.READY
            val message = "定位成功，正在自动检测…"

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
                contourPoints = contourPoints
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
                guidanceMessage = "请将面部置于画面中央"
            )
        }
    }
}