package com.bianque.health.face.data

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaPipe 468点面部关键点检测器。
 *
 * 基于 MediaPipe Face Landmarker 实现高精度面部关键点检测，
 * 提供 468 个面部网格点、轮廓、五官区域及表情融合形状（Blendshapes）。
 *
 * 与 ML Kit 的差异：
 * - ML Kit 提供约 130 个轮廓点 + 分类（微笑/睁眼概率）
 * - MediaPipe 提供 468 个密集网格点 + 52 种 Blendshapes 分类
 * - MediaPipe 精度更高，适合面诊分区分析
 *
 * 注意：此检测器需要 "face_landmarker.task" 模型文件存在于 assets 目录中。
 * 如果模型不存在，将优雅降级返回 null。
 */
@Singleton
class MediaPipeFaceDetector @Inject constructor() {

    /**
     * MediaPipe 面部关键点检测结果。
     *
     * @property landmarks 468 个面部网格点（归一化坐标，范围 0-1）
     * @property faceOval 36 个面部轮廓点（用于绘制面部边界）
     * @property leftEye 左眼区域关键点（含眼睑、虹膜）
     * @property rightEye 右眼区域关键点（含眼睑、虹膜）
     * @property lips 唇部区域关键点（外唇轮廓）
     * @property leftEyebrow 左眉关键点
     * @property rightEyebrow 右眉关键点
     * @property noseTip 鼻尖关键点
     * @property faceBlendshapes 表情融合形状分类结果（52 种表情权重）
         * @property imageWidth 原始图像宽度
         * @property imageHeight 原始图像高度
         */
    data class MediaPipeFaceResult(
        val landmarks: List<NormalizedLandmark>,
        val faceOval: List<NormalizedLandmark>,
        val leftEye: List<NormalizedLandmark>,
        val rightEye: List<NormalizedLandmark>,
        val lips: List<NormalizedLandmark>,
        val leftEyebrow: List<NormalizedLandmark>,
        val rightEyebrow: List<NormalizedLandmark>,
        val noseTip: NormalizedLandmark?,
        val faceBlendshapes: List<Category>?,
        val imageWidth: Int,
        val imageHeight: Int
    )

    companion object {
        private const val MODEL_ASSET_PATH = "face_landmarker.task"
        private const val MIN_FACE_DETECTION_CONFIDENCE = 0.3f
        private const val MIN_FACE_PRESENCE_CONFIDENCE = 0.3f
        private const val MIN_TRACKING_CONFIDENCE = 0.3f

        /**
         * 面部轮廓 (Face Oval) 索引 — 36 个点，按顺时针方向排列。
         * 对应 MediaPipe 468 点模型中面部外轮廓的关键点。
         */
        val FACE_OVAL_INDICES = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
            397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
            172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
        )

        /** 左眼关键点索引（含眼睑和虹膜） */
        val LEFT_EYE_INDICES = intArrayOf(
            33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7
        )

        /** 右眼关键点索引（含眼睑和虹膜） */
        val RIGHT_EYE_INDICES = intArrayOf(
            362, 398, 384, 385, 386, 387, 388, 466, 263, 249, 390, 373, 374, 380, 381, 382
        )

        /** 唇部关键点索引（外唇轮廓） */
        val LIPS_INDICES = intArrayOf(
            61, 146, 91, 181, 84, 17, 314, 405, 321, 375,
            291, 409, 270, 269, 267, 0, 37, 39, 40, 185
        )

        /** 左眉关键点索引 */
        val LEFT_EYEBROW_INDICES = intArrayOf(46, 53, 52, 65, 55)

        /** 右眉关键点索引 */
        val RIGHT_EYEBROW_INDICES = intArrayOf(276, 283, 282, 295, 285)

        /** 鼻尖关键点索引 */
        const val NOSE_TIP_INDEX = 1
    }

    private var faceLandmarker: FaceLandmarker? = null
    private var modelAvailable: Boolean? = null

    /**
     * 检查模型文件是否存在于 assets 目录中。
     */
    private fun isModelAvailable(context: Context): Boolean {
        if (modelAvailable != null) return modelAvailable!!
        return try {
            context.assets.open(MODEL_ASSET_PATH).use { it.close() }
            Timber.d("MediaPipeFaceDetector: model '$MODEL_ASSET_PATH' found in assets")
            modelAvailable = true
            true
        } catch (e: Exception) {
            Timber.w("MediaPipeFaceDetector: model '$MODEL_ASSET_PATH' not found in assets, falling back to ML Kit")
            modelAvailable = false
            false
        }
    }

    /**
     * 初始化 FaceLandmarker（延迟创建）。
     * 如果模型文件不存在，landmarker 保持为 null。
     */
    private fun getOrCreateLandmarker(context: Context): FaceLandmarker? {
        if (faceLandmarker != null) return faceLandmarker
        if (!isModelAvailable(context)) return null

        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(MIN_FACE_DETECTION_CONFIDENCE)
                .setMinFacePresenceConfidence(MIN_FACE_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setOutputFaceBlendshapes(true)
                .build()

            val landmarker = FaceLandmarker.createFromOptions(context, options)
            faceLandmarker = landmarker
            Timber.d("MediaPipeFaceDetector: FaceLandmarker initialized successfully")
            landmarker
        } catch (e: Exception) {
            Timber.e(e, "MediaPipeFaceDetector: failed to initialize FaceLandmarker")
            null
        }
    }

    /**
     * 检测单张图像中的面部关键点。
     *
     * @param context Android Context，用于访问 assets 中的模型文件
     * @param bitmap 待检测的输入图像
     * @return MediaPipeFaceResult 如果检测成功，否则返回 null（模型不可用或未检测到人脸）
     */
    fun detect(context: Context, bitmap: Bitmap): MediaPipeFaceResult? {
        val landmarker = getOrCreateLandmarker(context) ?: run {
            Timber.d("MediaPipeFaceDetector: FaceLandmarker not available, skipping detection")
            return null
        }

        return try {
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
            val result: FaceLandmarkerResult = landmarker.detect(mpImage)

            val faceLandmarksList = result.faceLandmarks()
            if (faceLandmarksList.isEmpty()) {
                Timber.d("MediaPipeFaceDetector: no face detected in image")
                return null
            }

            val landmarks = faceLandmarksList[0]
            Timber.d("MediaPipeFaceDetector: detected face with %d landmarks", landmarks.size)

            if (landmarks.size < 468) {
                Timber.w("MediaPipeFaceDetector: expected 468 landmarks, got %d", landmarks.size)
            }

            // 提取各区域关键点
            val faceOval = extractLandmarks(landmarks, FACE_OVAL_INDICES)
            val leftEye = extractLandmarks(landmarks, LEFT_EYE_INDICES)
            val rightEye = extractLandmarks(landmarks, RIGHT_EYE_INDICES)
            val lips = extractLandmarks(landmarks, LIPS_INDICES)
            val leftEyebrow = extractLandmarks(landmarks, LEFT_EYEBROW_INDICES)
            val rightEyebrow = extractLandmarks(landmarks, RIGHT_EYEBROW_INDICES)
            val noseTip = if (NOSE_TIP_INDEX < landmarks.size) landmarks[NOSE_TIP_INDEX] else null

            val blendshapes = result.faceBlendshapes().orElse(null)?.getOrNull(0)

            MediaPipeFaceResult(
                landmarks = landmarks,
                faceOval = faceOval,
                leftEye = leftEye,
                rightEye = rightEye,
                lips = lips,
                leftEyebrow = leftEyebrow,
                rightEyebrow = rightEyebrow,
                noseTip = noseTip,
                faceBlendshapes = blendshapes,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        } catch (e: Exception) {
            Timber.e(e, "MediaPipeFaceDetector: detection failed")
            null
        }
    }

    /**
     * 将归一化坐标（0-1）转换为屏幕像素坐标。
     *
     * @param landmark 归一化关键点
     * @param imageWidth 图像宽度
     * @param imageHeight 图像高度
     * @return Pair<Float, Float> (pixelX, pixelY)
     */
    fun toScreenCoordinates(
        landmark: NormalizedLandmark,
        imageWidth: Int,
        imageHeight: Int
    ): Pair<Float, Float> {
        return Pair(
            landmark.x() * imageWidth,
            landmark.y() * imageHeight
        )
    }

    /**
     * 批量将归一化关键点列表转换为屏幕像素坐标列表。
     */
    fun toScreenCoordinates(
        landmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Pair<Float, Float>> {
        return landmarks.map { toScreenCoordinates(it, imageWidth, imageHeight) }
    }

    /**
     * 从完整关键点列表中按索引提取子集。
     */
    private fun extractLandmarks(
        landmarks: List<NormalizedLandmark>,
        indices: IntArray
    ): List<NormalizedLandmark> {
        return indices.filter { it < landmarks.size }.map { landmarks[it] }
    }

    /**
     * 释放 MediaPipe 资源。
     * 在不再需要检测器时调用，或通过 onCleared() 生命周期回调。
     */
    fun close() {
        try {
            faceLandmarker?.close()
            faceLandmarker = null
            modelAvailable = null
            Timber.d("MediaPipeFaceDetector: resources released")
        } catch (e: Exception) {
            Timber.e(e, "MediaPipeFaceDetector: error closing FaceLandmarker")
        }
    }
}