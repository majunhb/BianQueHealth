package com.bianque.health.face.data

import android.graphics.Bitmap
import android.graphics.PointF
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
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.3f)
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

        // 额头区域 (bounding box 上部 1/3)
        val foreheadRect = Rect(
            bounds.left, bounds.top,
            bounds.right, bounds.top + bounds.height() / 3
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
        val leftCheekRect = Rect(
            bounds.left, bounds.top + bounds.height() / 3,
            bounds.left + bounds.width() / 3, bounds.bottom - bounds.height() / 4
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
        val rightCheekRect = Rect(
            bounds.right - bounds.width() / 3, bounds.top + bounds.height() / 3,
            bounds.right, bounds.bottom - bounds.height() / 4
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
        val noseRect = Rect(
            bounds.left + bounds.width() / 3, bounds.top + bounds.height() / 3,
            bounds.right - bounds.width() / 3, bounds.top + bounds.height() * 2 / 3
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
        val chinRect = Rect(
            bounds.left + bounds.width() / 4, bounds.bottom - bounds.height() / 4,
            bounds.right - bounds.width() / 4, bounds.bottom
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
        val foreheadRect = Rect(
            bounds.left, bounds.top,
            bounds.right, bounds.top + bounds.height() / 3
        )
        return colorAnalyzer.computeGlossiness(bitmap, foreheadRect)
    }

    private fun detectAbnormalities(regions: List<FaceRegion>, face: Face): List<String> {
        val issues = mutableListOf<String>()

        // 检查面色异常
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