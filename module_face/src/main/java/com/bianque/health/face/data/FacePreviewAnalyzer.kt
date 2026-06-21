package com.bianque.health.face.data

import android.graphics.Bitmap
import android.graphics.Rect
import com.bianque.health.base.analysis.ImageQualityAnalyzer.DetectionState
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 实时面部预览分析器 — 对标 YouCam / SkinVision / VIDA 的实时人脸检测方案。
 *
 * 在预览阶段使用 ML Kit FaceDetection 进行实时人脸检测，
 * 替代原来不可靠的像素级 HSV 肤色检测，提供：
 * - 实时人脸边界框（用于动态引导框）
 * - 头部姿态评估（偏航/翻滚角度）
 * - 眼睛开合状态
 * - 面部大小与位置质量评估
 * - 精确的、可执行的用户引导提示
 */
@Singleton
class FacePreviewAnalyzer @Inject constructor() {

    /** 预览专用轻量检测器：跳过轮廓点，只保留分类和特征点 */
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

    /**
     * 实时预览分析结果。
     */
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
        val guidanceMessage: String
    )

    /**
     * 对一帧图像执行实时人脸检测与质量评估。
     * 自动节流：300ms 内只处理一帧。
     */
    suspend fun analyze(bitmap: Bitmap): PreviewResult? = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < analysisIntervalMs) {
            return@withContext null // 节流跳过
        }
        lastAnalysisTime = now

        // 缩放到 640px 以内，提升检测速度
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
                    guidanceMessage = "请将面部置于框内，保持正脸"
                )
            }

            val face = faces[0]
            val bounds = face.boundingBox
            if (bounds == null) {
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
                    guidanceMessage = "请将面部置于框内，保持正脸"
                )
            }

            val faceArea = bounds.width() * bounds.height()
            val frameArea = imageWidth * imageHeight
            val faceSizeRatio = faceArea.toFloat() / frameArea

            val yaw = face.headEulerAngleY ?: 0f
            val roll = face.headEulerAngleZ ?: 0f
            val leftEye = face.leftEyeOpenProbability ?: 1f
            val rightEye = face.rightEyeOpenProbability ?: 1f

            Timber.d("FacePreviewAnalyzer: face found, size=%.2f%%, yaw=%.1f, roll=%.1f, eyes=%.1f/%.1f",
                faceSizeRatio * 100, yaw, roll, leftEye, rightEye)

            val (state, message) = assessQuality(faceSizeRatio, yaw, roll, leftEye, rightEye)

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
                guidanceMessage = message
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
                guidanceMessage = "请将面部置于框内，保持正脸"
            )
        }
    }

    /**
     * 多维度面部质量评估 — 对标 VIDA Liveness SDK 和 SkinVision 的采集标准。
     *
     * 判断优先级（从高到低）：
     * 1. 面部大小（太远/太近 → NOT_DETECTED）
     * 2. 头部角度（偏航/翻滚 → POOR_QUALITY）
     * 3. 眼睛闭合（→ POOR_QUALITY）
     * 4. 面部稍小（→ POOR_QUALITY，提示靠近）
     * 5. 全部通过 → READY
     */
    private fun assessQuality(
        faceSizeRatio: Float,
        yaw: Float,
        roll: Float,
        leftEye: Float,
        rightEye: Float
    ): Pair<DetectionState, String> {
        // 面部太远：占画面 < 8%
        if (faceSizeRatio < 0.08f) {
            return DetectionState.NOT_DETECTED to "面部太远，请靠近摄像头"
        }
        // 面部太近：占画面 > 35%
        if (faceSizeRatio > 0.35f) {
            return DetectionState.NOT_DETECTED to "面部太近，请远离摄像头"
        }
        // 侧脸角度过大：偏航 > ±15°
        if (abs(yaw) > 15f) {
            return DetectionState.POOR_QUALITY to "请正对摄像头，不要侧脸"
        }
        // 头部倾斜：翻滚 > ±12°
        if (abs(roll) > 12f) {
            return DetectionState.POOR_QUALITY to "请保持头部正直，不要歪头"
        }
        // 眼睛闭合
        if (leftEye < 0.5f || rightEye < 0.5f) {
            return DetectionState.POOR_QUALITY to "请保持双眼睁开"
        }
        // 面部偏小（但已检测到）：提示靠近
        if (faceSizeRatio < 0.12f) {
            return DetectionState.POOR_QUALITY to "请再靠近一些"
        }
        // 全部通过
        return DetectionState.READY to "定位成功，正在自动检测…"
    }
}