package com.bianque.health.face.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.bianque.health.base.analysis.ImageQualityAnalyzer.DetectionState
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
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
class FacePreviewAnalyzer @Inject constructor(
    private val mediaPipeDetector: MediaPipeFaceDetector
) {

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

    /**
     * 采集帧的 MediaPipe 高精度预览结果。
     *
     * 相比实时预览的 PreviewResult，此结果包含：
     * - MediaPipe 468 个关键点（更密集的面部网格）
     * - 36 个面部轮廓点（Face Oval）
     * - 面部 Blendshapes 分类结果
     * - 完整的 MediaPipeFaceResult（可直接用于面诊分析）
     *
     * 适用于用户点击拍照后的采集帧分析，而非实时预览流。
     */
    data class CapturePreviewResult(
        /** 是否检测到人脸 */
        val faceFound: Boolean,
        /** ML Kit 边界框（回退时使用） */
        val boundingBox: Rect?,
        /** 图像宽度 */
        val imageWidth: Int,
        /** 图像高度 */
        val imageHeight: Int,
        /** 面部在画面中的占比 */
        val faceSizeRatio: Float,
        /** 头部偏航角（左右转头） */
        val headEulerAngleY: Float,
        /** 头部翻滚角（歪头） */
        val headEulerAngleZ: Float,
        /** 左眼睁开概率 */
        val leftEyeOpen: Float,
        /** 右眼睁开概率 */
        val rightEyeOpen: Float,
        /** 检测状态 */
        val detectionState: DetectionState,
        /** 引导提示消息 */
        val guidanceMessage: String,
        /** ML Kit 面部轮廓点（图像坐标系） */
        val contourPoints: List<PointF> = emptyList(),
        /** MediaPipe 468 关键点结果（如果 MediaPipe 检测成功） */
        val mediaPipeResult: MediaPipeFaceDetector.MediaPipeFaceResult? = null,
        /** 使用的检测器类型 */
        val detectorType: String = "ML Kit"
    )

    /**
     * 采集帧分析：使用 MediaPipe 进行高精度面部检测，ML Kit 作为回退。
     *
     * 与实时预览的 `analyze()` 方法不同，此方法：
     * 1. 优先使用 MediaPipe Face Landmarker（468 点）进行高精度检测
     * 2. 如果 MediaPipe 不可用，回退到 ML Kit
     * 3. 返回完整的面部关键点数据，可用于后续面诊分析
     *
     * 使用场景：用户点击拍照按钮后，对采集到的帧进行精细分析。
     * 不适合实时预览流（MediaPipe 图像模式比 ML Kit 慢，会影响帧率）。
     *
     * @param context Android Context，用于加载 MediaPipe 模型
     * @param bitmap 采集到的面部图像（建议原始分辨率）
     * @return CapturePreviewResult 分析结果
     */
    suspend fun analyzeForCapture(context: Context, bitmap: Bitmap): CapturePreviewResult = withContext(Dispatchers.Default) {
        val maxDim = 1024
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

        // 第一步：尝试 MediaPipe 高精度检测
        try {
            val mpResult = mediaPipeDetector.detect(context, processedBitmap)
            if (mpResult != null) {
                Timber.d("FacePreviewAnalyzer: MediaPipe capture detection succeeded, landmarks=%d",
                    mpResult.landmarks.size)

                // 从 MediaPipe 关键点计算面部区域信息
                val faceSizeRatio = computeFaceSizeRatio(mpResult.faceOval, imageWidth, imageHeight)
                val boundingBox = computeBoundingBoxFromLandmarks(mpResult.faceOval, imageWidth, imageHeight)

                return@withContext CapturePreviewResult(
                    faceFound = true,
                    boundingBox = boundingBox,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    faceSizeRatio = faceSizeRatio,
                    headEulerAngleY = 0f, // MediaPipe IMAGE 模式不提供头部姿态角
                    headEulerAngleZ = 0f,
                    leftEyeOpen = computeBlendshapeScore(mpResult.faceBlendshapes, "eyeBlinkLeft"),
                    rightEyeOpen = computeBlendshapeScore(mpResult.faceBlendshapes, "eyeBlinkRight"),
                    detectionState = DetectionState.READY,
                    guidanceMessage = "定位成功（MediaPipe 468点）",
                    contourPoints = convertToPointF(mpResult.faceOval, imageWidth, imageHeight),
                    mediaPipeResult = mpResult,
                    detectorType = "MediaPipe 468"
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "FacePreviewAnalyzer: MediaPipe capture detection failed, falling back to ML Kit")
        }

        // 第二步：回退到 ML Kit
        Timber.d("FacePreviewAnalyzer: using ML Kit for capture analysis")
        val inputImage = InputImage.fromBitmap(processedBitmap, 0)

        try {
            val faces = Tasks.await(detector.process(inputImage))

            if (faces.isEmpty()) {
                return@withContext CapturePreviewResult(
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
                    guidanceMessage = "请将面部置于画面中央",
                    detectorType = "ML Kit"
                )
            }

            val face = faces[0]
            val bounds = face.boundingBox ?: Rect(0, 0, imageWidth, imageHeight)
            val faceArea = bounds.width() * bounds.height()
            val frameArea = imageWidth * imageHeight
            val faceSizeRatio = faceArea.toFloat() / frameArea

            val contour = face.getContour(FaceContour.FACE)
            val contourPoints = contour?.points ?: emptyList()

            return@withContext CapturePreviewResult(
                faceFound = true,
                boundingBox = bounds,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                faceSizeRatio = faceSizeRatio,
                headEulerAngleY = face.headEulerAngleY ?: 0f,
                headEulerAngleZ = face.headEulerAngleZ ?: 0f,
                leftEyeOpen = face.leftEyeOpenProbability ?: 1f,
                rightEyeOpen = face.rightEyeOpenProbability ?: 1f,
                detectionState = DetectionState.READY,
                guidanceMessage = "定位成功（ML Kit）",
                contourPoints = contourPoints,
                detectorType = "ML Kit"
            )
        } catch (e: Exception) {
            Timber.w(e, "FacePreviewAnalyzer: ML Kit capture detection also failed")
            CapturePreviewResult(
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
                guidanceMessage = "请将面部置于画面中央",
                detectorType = "None"
            )
        }
    }

    /**
     * 从 MediaPipe 面部轮廓关键点计算面部在画面中的占比。
     */
    private fun computeFaceSizeRatio(
        faceOval: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ): Float {
        if (faceOval.isEmpty()) return 0f

        val minX = faceOval.minOf { it.x() }
        val minY = faceOval.minOf { it.y() }
        val maxX = faceOval.maxOf { it.x() }
        val maxY = faceOval.maxOf { it.y() }

        val faceArea = (maxX - minX) * (maxY - minY)
        return faceArea.coerceIn(0f, 1f)
    }

    /**
     * 从面部轮廓关键点计算包围矩形。
     */
    private fun computeBoundingBoxFromLandmarks(
        faceOval: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ): Rect {
        if (faceOval.isEmpty()) return Rect(0, 0, imageWidth, imageHeight)

        val minX = (faceOval.minOf { it.x() } * imageWidth).toInt().coerceIn(0, imageWidth)
        val minY = (faceOval.minOf { it.y() } * imageHeight).toInt().coerceIn(0, imageHeight)
        val maxX = (faceOval.maxOf { it.x() } * imageWidth).toInt().coerceIn(0, imageWidth)
        val maxY = (faceOval.maxOf { it.y() } * imageHeight).toInt().coerceIn(0, imageHeight)

        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * 从 Blendshapes 分类中提取指定表情的得分。
     *
     * @param classifications Blendshapes 分类列表
     * @param categoryName 目标表情名称（如 "eyeBlinkLeft"）
     * @return 得分 0-1，如果未找到返回 1.0（默认睁眼）
     */
    private fun computeBlendshapeScore(
        categories: List<com.google.mediapipe.tasks.components.containers.Category>?,
        categoryName: String
    ): Float {
        if (categories == null) return 1f
        return try {
            for (category in categories) {
                if (category.categoryName() == categoryName) {
                    // eyeBlink 值越高表示眼睛越闭合，需要反转
                    return (1f - category.score()).coerceIn(0f, 1f)
                }
            }
            1f
        } catch (e: Exception) {
            Timber.w(e, "FacePreviewAnalyzer: failed to extract blendshape '%s'", categoryName)
            1f
        }
    }

    /**
     * 将 MediaPipe 归一化关键点转换为 Android PointF 列表。
     */
    private fun convertToPointF(
        landmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ): List<PointF> {
        return landmarks.map { lm ->
            PointF(lm.x() * imageWidth, lm.y() * imageHeight)
        }
    }
}