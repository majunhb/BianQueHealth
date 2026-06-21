package com.bianque.health.face.data

import android.graphics.Bitmap
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import com.bianque.health.face.domain.model.FaceRegion
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceMeshDetector @Inject constructor(
    private val colorAnalyzer: ColorAnalyzer
) {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            // ★ 移除enableTracking() — 某些设备上tracking模式导致检测不到面部
            .build()
    )

    suspend fun detect(bitmap: Bitmap): FaceDiagnosisResult = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = com.google.android.gms.tasks.Tasks.await(detector.process(inputImage))

            if (faces.isEmpty()) {
                return@withContext FaceDiagnosisResult(
                    overallComplexion = "未检测到面部",
                    glossLevel = 0f,
                    regions = emptyList(),
                    abnormalities = listOf("未检测到面部，请确保光线充足且面部正对摄像头"),
                    confidence = 0f
                )
            }

            val face = faces[0]
            val regions = analyzeRegions(bitmap, face)
            val overallComplexion = determineOverallComplexion(regions)
            val glossLevel = computeOverallGloss(bitmap, face)
            val abnormalities = detectAbnormalities(regions, face)

            FaceDiagnosisResult(
                overallComplexion = overallComplexion,
                glossLevel = glossLevel,
                regions = regions,
                abnormalities = abnormalities,
                confidence = 0.85f
            )
        } catch (e: Exception) {
            Timber.e(e, "FaceMeshDetector: detection failed")
            FaceDiagnosisResult(
                overallComplexion = "分析失败",
                glossLevel = 0f,
                regions = emptyList(),
                abnormalities = listOf("面部分析失败: ${e.message}"),
                confidence = 0f
            )
        }
    }

    private suspend fun analyzeRegions(bitmap: Bitmap, face: Face): List<FaceRegion> {
        val regions = mutableListOf<FaceRegion>()
        val bounds = face.boundingBox

        // ★ 所有矩形坐标必须clamp到bitmap范围内，防止越界
        // 额头区域 (bounding box 上部 1/3)
        val foreheadRect = ColorAnalyzer.Rect(
            bounds.left.coerceIn(0, bitmap.width - 1),
            bounds.top.coerceIn(0, bitmap.height - 1),
            bounds.right.coerceIn(1, bitmap.width),
            (bounds.top + bounds.height() / 3).coerceIn(1, bitmap.height)
        )
        val foreheadLab = colorAnalyzer.analyzeRegion(bitmap, foreheadRect)
        regions.add(FaceRegion(
            name = "额头",
            color = colorAnalyzer.classifyComplexion(foreheadLab),
            brightness = foreheadLab.l / 100f,
            redGreen = foreheadLab.a / 128f,
            yellowBlue = foreheadLab.b / 128f
        ))

        // 左脸颊 (bounding box 左侧 1/3，中部)
        val leftCheekRect = ColorAnalyzer.Rect(
            bounds.left.coerceIn(0, bitmap.width - 1),
            (bounds.top + bounds.height() / 3).coerceIn(0, bitmap.height - 1),
            (bounds.left + bounds.width() / 3).coerceIn(1, bitmap.width),
            (bounds.bottom - bounds.height() / 4).coerceIn(1, bitmap.height)
        )
        val leftCheekLab = colorAnalyzer.analyzeRegion(bitmap, leftCheekRect)
        regions.add(FaceRegion(
            name = "左脸颊",
            color = colorAnalyzer.classifyComplexion(leftCheekLab),
            brightness = leftCheekLab.l / 100f,
            redGreen = leftCheekLab.a / 128f,
            yellowBlue = leftCheekLab.b / 128f
        ))

        // 右脸颊 (bounding box 右侧 1/3，中部)
        val rightCheekRect = ColorAnalyzer.Rect(
            (bounds.right - bounds.width() / 3).coerceIn(0, bitmap.width - 1),
            (bounds.top + bounds.height() / 3).coerceIn(0, bitmap.height - 1),
            bounds.right.coerceIn(1, bitmap.width),
            (bounds.bottom - bounds.height() / 4).coerceIn(1, bitmap.height)
        )
        val rightCheekLab = colorAnalyzer.analyzeRegion(bitmap, rightCheekRect)
        regions.add(FaceRegion(
            name = "右脸颊",
            color = colorAnalyzer.classifyComplexion(rightCheekLab),
            brightness = rightCheekLab.l / 100f,
            redGreen = rightCheekLab.a / 128f,
            yellowBlue = rightCheekLab.b / 128f
        ))

        // 鼻梁区域 (面部中心)
        val noseRect = ColorAnalyzer.Rect(
            (bounds.left + bounds.width() / 3).coerceIn(0, bitmap.width - 1),
            (bounds.top + bounds.height() / 3).coerceIn(0, bitmap.height - 1),
            (bounds.right - bounds.width() / 3).coerceIn(1, bitmap.width),
            (bounds.top + bounds.height() * 2 / 3).coerceIn(1, bitmap.height)
        )
        val noseLab = colorAnalyzer.analyzeRegion(bitmap, noseRect)
        regions.add(FaceRegion(
            name = "鼻梁",
            color = colorAnalyzer.classifyComplexion(noseLab),
            brightness = noseLab.l / 100f,
            redGreen = noseLab.a / 128f,
            yellowBlue = noseLab.b / 128f
        ))

        // 下巴区域
        val chinRect = ColorAnalyzer.Rect(
            (bounds.left + bounds.width() / 4).coerceIn(0, bitmap.width - 1),
            (bounds.bottom - bounds.height() / 4).coerceIn(0, bitmap.height - 1),
            (bounds.right - bounds.width() / 4).coerceIn(1, bitmap.width),
            bounds.bottom.coerceIn(1, bitmap.height)
        )
        val chinLab = colorAnalyzer.analyzeRegion(bitmap, chinRect)
        regions.add(FaceRegion(
            name = "下巴",
            color = colorAnalyzer.classifyComplexion(chinLab),
            brightness = chinLab.l / 100f,
            redGreen = chinLab.a / 128f,
            yellowBlue = chinLab.b / 128f
        ))

        return regions
    }

    private fun determineOverallComplexion(regions: List<FaceRegion>): String {
        if (regions.isEmpty()) return "未知"
        val colorCounts = regions.groupBy { it.color }.mapValues { it.value.size }
        return colorCounts.maxByOrNull { it.value }?.key ?: "正常"
    }

    private suspend fun computeOverallGloss(bitmap: Bitmap, face: Face): Float {
        val bounds = face.boundingBox
        val foreheadRect = ColorAnalyzer.Rect(
            bounds.left.coerceIn(0, bitmap.width - 1),
            bounds.top.coerceIn(0, bitmap.height - 1),
            bounds.right.coerceIn(1, bitmap.width),
            (bounds.top + bounds.height() / 3).coerceIn(1, bitmap.height)
        )
        return colorAnalyzer.computeGlossiness(bitmap, foreheadRect)
    }

    private fun detectAbnormalities(regions: List<FaceRegion>, face: Face): List<String> {
        val issues = mutableListOf<String>()

        for (region in regions) {
            when (region.color) {
                "偏黄" -> if (!issues.contains("面色偏黄")) issues.add("面色偏黄 — 可能存在脾虚或湿盛")
                "偏红" -> if (!issues.contains("面色偏红")) issues.add("面色偏红 — 可能存在热证")
                "偏白" -> if (!issues.contains("面色偏白")) issues.add("面色偏白 — 可能存在血虚或气虚")
                "晦暗" -> if (!issues.contains("面色晦暗")) issues.add("面色晦暗 — 可能存在肾虚或血瘀")
            }
        }

        // 检查面部对称性
        val leftRegions = regions.filter { it.name.contains("左") }
        val rightRegions = regions.filter { it.name.contains("右") }
        if (leftRegions.isNotEmpty() && rightRegions.isNotEmpty()) {
            val leftBrightness = leftRegions.map { it.brightness }.average()
            val rightBrightness = rightRegions.map { it.brightness }.average()
            if (kotlin.math.abs(leftBrightness - rightBrightness) > 0.15f) {
                issues.add("左右面色不对称 — 建议关注")
            }
        }

        if (issues.isEmpty()) {
            issues.add("面色未见明显异常")
        }
        return issues
    }
}
