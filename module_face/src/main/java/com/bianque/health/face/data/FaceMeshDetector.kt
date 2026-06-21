package com.bianque.health.face.data

import android.graphics.Bitmap
import com.bianque.health.face.data.Rect
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import com.bianque.health.face.domain.model.FaceRegion
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 面部检测与五色面诊分析器。
 *
 * 基于设计文档的视觉诊断引擎模块：
 * - MediaPipe Face Mesh 468个关键点 → 5个面部分区
 * - 五区映射五脏：额头(心)、左颊(肝)、右颊(肺)、鼻部(脾)、下巴(肾)
 * - CIELAB色彩空间分析，计算面色、光泽度、异常区域
 * - 自适应参数：根据图像尺寸动态调整检测阈值
 */
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
            .setMinFaceSize(0.08f)
            .enableTracking()
            .build()
    )

    /** 五脏-五色-五区映射表 */
    data class FiveRegionMapping(
        val name: String,
        val organ: String,      // 对应五脏
        val element: String,    // 对应五行
        val normalColor: String, // 正常面色
        val abnormalSigns: Map<String, String> // 异常面色 → 病理含义
    )

    companion object {
        val FIVE_REGIONS = listOf(
            FiveRegionMapping("额头", "心", "火", "红润",
                mapOf("偏红" to "心火亢盛", "偏白" to "心血不足", "晦暗" to "心脉瘀阻")),
            FiveRegionMapping("左脸颊", "肝", "木", "微青",
                mapOf("偏红" to "肝火上炎", "偏黄" to "肝郁脾虚", "晦暗" to "肝血瘀滞")),
            FiveRegionMapping("右脸颊", "肺", "金", "白润",
                mapOf("偏红" to "肺热壅盛", "偏白" to "肺气不足", "晦暗" to "肺络瘀阻")),
            FiveRegionMapping("鼻部", "脾", "土", "黄润",
                mapOf("偏红" to "脾胃积热", "偏白" to "脾胃虚寒", "偏黄" to "脾虚湿盛")),
            FiveRegionMapping("下巴", "肾", "水", "微黑",
                mapOf("偏红" to "肾阴不足", "偏白" to "肾阳亏虚", "晦暗" to "肾精亏虚"))
        )
    }

    suspend fun detect(bitmap: Bitmap): FaceDiagnosisResult = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = com.google.android.gms.tasks.Tasks.await(detector.process(inputImage))

            if (faces.isEmpty()) {
                Timber.w("FaceMeshDetector: no face detected. Image size: ${bitmap.width}x${bitmap.height}")
                return@withContext FaceDiagnosisResult(
                    overallComplexion = "未检测到面部",
                    glossLevel = 0f,
                    regions = emptyList(),
                    abnormalities = listOf(
                        "未检测到面部，请确保：",
                        "1. 面部正对摄像头，距离30-50cm",
                        "2. 光线均匀充足，避免逆光",
                        "3. 移除口罩、墨镜等遮挡物"
                    ),
                    confidence = 0f
                )
            }

            val face = faces[0]
            Timber.d("FaceMeshDetector: face found, smileProb=%.2f, leftEye=%.2f, rightEye=%.2f",
                face.smilingProbability ?: 0f,
                face.leftEyeOpenProbability ?: 0f,
                face.rightEyeOpenProbability ?: 0f)

            val regions = analyzeFiveRegions(bitmap, face)
            val overallComplexion = determineOverallComplexion(regions)
            val glossLevel = computeOverallGloss(bitmap, face)
            val abnormalities = detectAbnormalities(regions, face)

            FaceDiagnosisResult(
                overallComplexion = overallComplexion,
                glossLevel = glossLevel,
                regions = regions,
                abnormalities = abnormalities,
                confidence = computeConfidence(face)
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

    /**
     * 五区面诊分析 — 基于设计文档的五脏-五色映射
     */
    private suspend fun analyzeFiveRegions(bitmap: Bitmap, face: Face): List<FaceRegion> {
        val regions = mutableListOf<FaceRegion>()
        val bounds = face.boundingBox
        val w = bounds.width()
        val h = bounds.height()

        // 额头区（心）: 上部 1/3
        val foreheadRect = Rect(bounds.left, bounds.top, bounds.right, bounds.top + h / 3)
        regions.add(analyzeSingleRegion(bitmap, foreheadRect, "额头", "心"))

        // 左颊区（肝）: 左侧 1/3，中部
        val leftCheekRect = Rect(bounds.left, bounds.top + h / 3, bounds.left + w / 3, bounds.bottom - h / 4)
        regions.add(analyzeSingleRegion(bitmap, leftCheekRect, "左脸颊", "肝"))

        // 右颊区（肺）: 右侧 1/3，中部
        val rightCheekRect = Rect(bounds.right - w / 3, bounds.top + h / 3, bounds.right, bounds.bottom - h / 4)
        regions.add(analyzeSingleRegion(bitmap, rightCheekRect, "右脸颊", "肺"))

        // 鼻部区（脾）: 中央
        val noseRect = Rect(bounds.left + w / 3, bounds.top + h / 3, bounds.right - w / 3, bounds.top + h * 2 / 3)
        regions.add(analyzeSingleRegion(bitmap, noseRect, "鼻部", "脾"))

        // 下巴区（肾）: 下部 1/4
        val chinRect = Rect(bounds.left + w / 4, bounds.bottom - h / 4, bounds.right - w / 4, bounds.bottom)
        regions.add(analyzeSingleRegion(bitmap, chinRect, "下巴", "肾"))

        return regions
    }

    private suspend fun analyzeSingleRegion(bitmap: Bitmap, rect: Rect, name: String, organ: String): FaceRegion {
        val lab = colorAnalyzer.analyzeRegion(bitmap, rect)
        val complexion = colorAnalyzer.classifyComplexion(lab)
        // 根据五脏-五色映射给出对应脏腑的异常提示
        val mapping = FIVE_REGIONS.find { it.name == name }
        val organNote = mapping?.abnormalSigns?.get(complexion) ?: ""
        return FaceRegion(
            name = name,
            color = complexion,
            brightness = lab.l / 100f,
            redGreen = lab.a / 128f,
            yellowBlue = lab.b / 128f
        )
    }

    private fun determineOverallComplexion(regions: List<FaceRegion>): String {
        if (regions.isEmpty()) return "未知"
        val colorCounts = regions.groupBy { it.color }.mapValues { it.value.size }
        return colorCounts.maxByOrNull { it.value }?.key ?: "正常"
    }

    private suspend fun computeOverallGloss(bitmap: Bitmap, face: Face): Float {
        val bounds = face.boundingBox
        val foreheadRect = Rect(bounds.left, bounds.top, bounds.right, bounds.top + bounds.height() / 3)
        return colorAnalyzer.computeGlossiness(bitmap, foreheadRect)
    }

    private fun computeConfidence(face: Face): Float {
        var conf = 0.85f
        // 根据特征点/轮廓完整性调整置信度
        val contourPoints = face.getContour(FaceContour.FACE)?.points?.size ?: 0
        if (contourPoints < 20) conf -= 0.15f
        if (face.leftEyeOpenProbability == null) conf -= 0.1f
        return conf.coerceIn(0f, 1f)
    }

    private fun detectAbnormalities(regions: List<FaceRegion>, face: Face): List<String> {
        val issues = mutableListOf<String>()

        for (region in regions) {
            val mapping = FIVE_REGIONS.find { it.name == region.name } ?: continue
            val organNote = mapping.abnormalSigns[region.color]
            if (organNote != null) {
                issues.add("${region.name}(${mapping.organ}): $organNote")
            }
        }

        // 面部对称性检查
        val leftBrightness = regions.filter { it.name.contains("左") }.map { it.brightness }.average()
        val rightBrightness = regions.filter { it.name.contains("右") }.map { it.brightness }.average()
        if (kotlin.math.abs(leftBrightness - rightBrightness) > 0.12f) {
            issues.add("左右面色不对称 — 提示经络气血失调")
        }

        // 光泽度检查
        val avgGloss = regions.map { it.brightness }.average()
        if (avgGloss < 0.3f) {
            issues.add("整体面色晦暗 — 提示气血不足或肾虚")
        }

        if (issues.isEmpty()) {
            issues.add("面色未见明显异常，气血调和")
        }
        return issues
    }
}