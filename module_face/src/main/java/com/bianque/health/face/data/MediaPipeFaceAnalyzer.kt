package com.bianque.health.face.data

import android.graphics.Bitmap
import android.graphics.PointF
import com.bianque.health.face.data.MediaPipeFaceDetector.Companion.FACE_OVAL_INDICES
import com.bianque.health.face.data.MediaPipeFaceDetector.Companion.LEFT_EYE_INDICES
import com.bianque.health.face.data.MediaPipeFaceDetector.Companion.LEFT_EYEBROW_INDICES
import com.bianque.health.face.data.MediaPipeFaceDetector.Companion.LIPS_INDICES
import com.bianque.health.face.data.MediaPipeFaceDetector.Companion.NOSE_TIP_INDEX
import com.bianque.health.face.data.MediaPipeFaceDetector.Companion.RIGHT_EYE_INDICES
import com.bianque.health.face.data.MediaPipeFaceDetector.Companion.RIGHT_EYEBROW_INDICES
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

/**
 * 基于 MediaPipe 468 关键点的 TCM 面部分区分析器。
 *
 * 利用高密度面部网格，精确定义 7 个中医面诊分区：
 * - 额头（心）：眉骨以上区域
 * - 左脸颊（肝）：左侧颧骨区域
 * - 右脸颊（肺）：右侧颧骨区域
 * - 鼻部（脾）：鼻梁及鼻翼区域
 * - 下巴（肾）：下颌及颏部区域
 * - 眼周（肝）：眼睑及眼眶区域，用于肝血状态评估
 * - 唇周（脾胃）：唇部及周围区域，用于脾胃运化评估
 *
 * 相比基于 bounding box 的粗粒度分区，468 关键点方案可实现：
 * - 更精确的区域边界
 * - 排除头发、背景等干扰像素
 * - 更准确的中医面色分析
 */
@Singleton
class MediaPipeFaceAnalyzer @Inject constructor(
    private val colorAnalyzer: ColorAnalyzer
) {

    /**
     * 单个 TCM 区域的分析结果。
     */
    data class ZoneAnalysis(
        /** 区域名称（中文） */
        val name: String,
        /** 对应五脏 */
        val organ: String,
        /** 对应五行 */
        val element: String,
        /** 面色分类：正常/偏黄/偏红/偏白/晦暗 */
        val color: String,
        /** 亮度 (L* 归一化 0-1) */
        val brightness: Float,
        /** 红绿轴 (a* 归一化) */
        val redGreen: Float,
        /** 黄蓝轴 (b* 归一化) */
        val yellowBlue: Float,
        /** 光泽度 (0-1) */
        val gloss: Float,
        /** 异常提示（如果有） */
        val abnormalNote: String?
    )

    /**
     * 完整的面部分析结果。
     */
    data class FaceAnalysisResult(
        /** 各区域分析结果 */
        val zones: List<ZoneAnalysis>,
        /** 整体面色分类 */
        val overallComplexion: String,
        /** 整体光泽度 (0-1) */
        val glossLevel: Float,
        /** 左右对称性评分 (0-1, 1 为完全对称) */
        val symmetryScore: Float,
        /** 异常发现列表 */
        val abnormalities: List<String>,
        /** 诊断置信度 (0-1) */
        val confidence: Float
    )

    /** 五脏-五色-五区映射表 */
    data class TcmZoneMapping(
        val name: String,
        val organ: String,
        val element: String,
        val normalColor: String,
        val abnormalSigns: Map<String, String>
    )

    companion object {
        /** TCM 面诊七区映射 */
        val TCM_ZONES = listOf(
            TcmZoneMapping("额头", "心", "火", "红润",
                mapOf("偏红" to "心火亢盛", "偏白" to "心血不足", "晦暗" to "心脉瘀阻")),
            TcmZoneMapping("左脸颊", "肝", "木", "微青",
                mapOf("偏红" to "肝火上炎", "偏黄" to "肝郁脾虚", "晦暗" to "肝血瘀滞")),
            TcmZoneMapping("右脸颊", "肺", "金", "白润",
                mapOf("偏红" to "肺热壅盛", "偏白" to "肺气不足", "晦暗" to "肺络瘀阻")),
            TcmZoneMapping("鼻部", "脾", "土", "黄润",
                mapOf("偏红" to "脾胃积热", "偏白" to "脾胃虚寒", "偏黄" to "脾虚湿盛")),
            TcmZoneMapping("下巴", "肾", "水", "微黑",
                mapOf("偏红" to "肾阴不足", "偏白" to "肾阳亏虚", "晦暗" to "肾精亏虚")),
            TcmZoneMapping("眼周", "肝", "木", "润泽",
                mapOf("偏红" to "肝经风热", "偏黄" to "肝胆湿热", "晦暗" to "肝血亏虚")),
            TcmZoneMapping("唇周", "脾胃", "土", "红润",
                mapOf("偏红" to "脾胃积热", "偏白" to "脾胃虚寒", "偏黄" to "脾虚湿困", "晦暗" to "脾胃虚弱"))
        )

        /**
         * 额头（心）关键点索引 — 眉骨以上、前额区域的网格点。
         * 基于 468 点模型中额头区域的顶点。
         */
        val FOREHEAD_INDICES = intArrayOf(
            10, 151, 9, 8, 168, 6, 197, 195, 5, 4,
            337, 338, 299, 298, 297, 69, 67, 109, 107, 66,
            69, 108, 69, 104, 103, 54, 21, 162, 127, 234,
            93, 132, 58, 172, 136, 150, 149, 176, 148, 152
        )

        /**
         * 左脸颊（肝）关键点索引 — 左侧颧骨及面颊区域。
         */
        val LEFT_CHEEK_INDICES = intArrayOf(
            50, 101, 118, 119, 100, 117, 111, 36, 205, 203,
            206, 216, 212, 202, 210, 169, 132, 58, 172, 136,
            150, 149, 176, 123, 117, 50, 214, 207, 187, 137,
            123, 116, 143, 34, 227, 116
        )

        /**
         * 右脸颊（肺）关键点索引 — 右侧颧骨及面颊区域。
         */
        val RIGHT_CHEEK_INDICES = intArrayOf(
            280, 330, 347, 348, 329, 346, 340, 266, 425, 423,
            426, 436, 432, 422, 430, 394, 361, 288, 397, 365,
            379, 378, 400, 352, 345, 280, 434, 427, 411, 366,
            352, 345, 372, 264, 447, 345
        )

        /**
         * 鼻部（脾）关键点索引 — 鼻梁、鼻翼及鼻尖区域。
         */
        val NOSE_INDICES = intArrayOf(
            1, 2, 98, 327, 168, 197, 195, 5, 6, 8,
            45, 275, 4, 220, 440, 217, 437, 219, 439, 218,
            438, 44, 274, 1, 240, 460, 248, 456, 250, 462,
            290, 305, 294, 458, 49, 279
        )

        /**
         * 下巴（肾）关键点索引 — 下颌及颏部区域。
         */
        val CHIN_INDICES = intArrayOf(
            152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
            234, 127, 162, 21, 54, 103, 67, 109, 10, 199,
            175, 152, 200, 201, 207, 205, 187, 137, 170, 140,
            171, 175, 396, 369, 395, 378, 400, 377, 152
        )

        /**
         * 眼周（肝）关键点索引 — 双眼周围区域，评估肝血状态。
         */
        val EYE_AREA_INDICES = LEFT_EYE_INDICES + RIGHT_EYE_INDICES + intArrayOf(
            226, 228, 229, 230, 231, 232, 27, 28, 29, 30,
            56, 190, 243, 112, 446, 447, 448, 449, 450, 451,
            256, 257, 258, 259, 286, 414, 463, 341
        )

        /**
         * 唇周（脾胃）关键点索引 — 唇部及口周区域，评估脾胃运化功能。
         */
        val LIP_AREA_INDICES = LIPS_INDICES + intArrayOf(
            11, 12, 13, 14, 15, 16, 57, 186, 92, 165,
            167, 164, 393, 391, 322, 410, 287, 273, 335, 406,
            313, 18, 83, 182, 106, 43, 212, 77, 90, 180
        )
    }

    /**
     * 基于 468 关键点对整张面部进行 TCM 七区分析。
     *
     * @param bitmap 原始面部图像
     * @param faceResult MediaPipe 检测结果（含 468 关键点）
     * @return 面部分析结果
     */
    suspend fun analyzeRegions(
        bitmap: Bitmap,
        faceResult: MediaPipeFaceDetector.MediaPipeFaceResult
    ): FaceAnalysisResult = withContext(Dispatchers.Default) {
        Timber.d("MediaPipeFaceAnalyzer: analyzing regions with 468 landmarks")

        val zones = analyzeAllZones(bitmap, faceResult)

        val overallComplexion = determineOverallComplexion(zones)
        val glossLevel = computeOverallGloss(zones)
        val symmetryScore = computeSymmetry(faceResult, bitmap)
        val abnormalities = detectAbnormalities(zones, symmetryScore, faceResult)
        val confidence = computeConfidence(zones, faceResult)

        FaceAnalysisResult(
            zones = zones,
            overallComplexion = overallComplexion,
            glossLevel = glossLevel,
            symmetryScore = symmetryScore,
            abnormalities = abnormalities,
            confidence = confidence
        )
    }

    /**
     * 并行分析所有 7 个 TCM 区域。
     */
    private suspend fun analyzeAllZones(
        bitmap: Bitmap,
        faceResult: MediaPipeFaceDetector.MediaPipeFaceResult
    ): List<ZoneAnalysis> = withContext(Dispatchers.Default) {
        val zoneDefinitions = listOf(
            Triple("额头", "心", FOREHEAD_INDICES),
            Triple("左脸颊", "肝", LEFT_CHEEK_INDICES),
            Triple("右脸颊", "肺", RIGHT_CHEEK_INDICES),
            Triple("鼻部", "脾", NOSE_INDICES),
            Triple("下巴", "肾", CHIN_INDICES),
            Triple("眼周", "肝", EYE_AREA_INDICES),
            Triple("唇周", "脾胃", LIP_AREA_INDICES)
        )

        val deferred = zoneDefinitions.map { (name, organ, indices) ->
            async {
                analyzeSingleZone(bitmap, faceResult, name, organ, indices)
            }
        }

        deferred.awaitAll()
    }

    /**
     * 分析单个 TCM 区域。
     * 从关键点提取该区域的像素 bounding rect，然后委托 ColorAnalyzer 分析。
     */
    private suspend fun analyzeSingleZone(
        bitmap: Bitmap,
        faceResult: MediaPipeFaceDetector.MediaPipeFaceResult,
        zoneName: String,
        organ: String,
        landmarkIndices: IntArray
    ): ZoneAnalysis {
        val landmarks = faceResult.landmarks
        val imageWidth = faceResult.imageWidth
        val imageHeight = faceResult.imageHeight

        // 将关键点转换为屏幕坐标
        val points = landmarkIndices.mapNotNull { index ->
            if (index < landmarks.size) {
                val lm = landmarks[index]
                PointF(lm.x() * imageWidth, lm.y() * imageHeight)
            } else null
        }

        if (points.isEmpty()) {
            val mapping = TCM_ZONES.find { it.name == zoneName }
            return ZoneAnalysis(
                name = zoneName,
                organ = organ,
                element = mapping?.element ?: "未知",
                color = "未知",
                brightness = 0f,
                redGreen = 0f,
                yellowBlue = 0f,
                gloss = 0f,
                abnormalNote = null
            )
        }

        // 从关键点集合计算包围矩形
        val minX = points.minOf { it.x }.toInt().coerceIn(0, imageWidth - 1)
        val minY = points.minOf { it.y }.toInt().coerceIn(0, imageHeight - 1)
        val maxX = points.maxOf { it.x }.toInt().coerceIn(1, imageWidth)
        val maxY = points.maxOf { it.y }.toInt().coerceIn(1, imageHeight)

        val rect = Rect(minX, minY, maxX, maxY)

        // 委托 ColorAnalyzer 进行 CIELAB 分析
        val lab = colorAnalyzer.analyzeRegion(bitmap, rect)
        val complexion = colorAnalyzer.classifyComplexion(lab)
        val gloss = colorAnalyzer.computeGlossiness(bitmap, rect)

        // 查找 TCM 映射
        val mapping = TCM_ZONES.find { it.name == zoneName }
        val element = mapping?.element ?: "未知"
        val abnormalNote = mapping?.abnormalSigns?.get(complexion)

        Timber.d("MediaPipeFaceAnalyzer: zone=%s organ=%s color=%s L=%.1f a=%.1f b=%.1f gloss=%.2f",
            zoneName, organ, complexion, lab.l, lab.a, lab.b, gloss)

        ZoneAnalysis(
            name = zoneName,
            organ = organ,
            element = element,
            color = complexion,
            brightness = lab.l / 100f,
            redGreen = lab.a / 128f,
            yellowBlue = lab.b / 128f,
            gloss = gloss,
            abnormalNote = abnormalNote
        )
    }

    /**
     * 根据各区域分析结果确定整体面色。
     */
    private fun determineOverallComplexion(zones: List<ZoneAnalysis>): String {
        if (zones.isEmpty()) return "未知"
        val colorCounts = zones.groupBy { it.color }.mapValues { it.value.size }
        return colorCounts.maxByOrNull { it.value }?.key ?: "正常"
    }

    /**
     * 计算整体光泽度：各区域光泽度的加权平均。
     * 额头和鼻部权重更高，因为这两个区域是面诊中的核心观察区域。
     */
    private fun computeOverallGloss(zones: List<ZoneAnalysis>): Float {
        if (zones.isEmpty()) return 0f
        val weighted = zones.sumOf { zone ->
            val weight = when (zone.name) {
                "额头", "鼻部" -> 2.0
                "左脸颊", "右脸颊" -> 1.5
                else -> 1.0
            }
            zone.gloss.toDouble() * weight
        }
        val totalWeight = zones.sumOf { zone ->
            when (zone.name) {
                "额头", "鼻部" -> 2.0
                "左脸颊", "右脸颊" -> 1.5
                else -> 1.0
            }
        }
        return (weighted / totalWeight).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 计算面部左右对称性评分。
     * 基于左脸颊与右脸颊的亮度和色度差异。
     */
    private fun computeSymmetry(
        faceResult: MediaPipeFaceDetector.MediaPipeFaceResult,
        bitmap: Bitmap
    ): Float {
        return try {
            val leftCheekLab = extractZoneLab(
                bitmap, faceResult, LEFT_CHEEK_INDICES, faceResult.imageWidth, faceResult.imageHeight
            )
            val rightCheekLab = extractZoneLab(
                bitmap, faceResult, RIGHT_CHEEK_INDICES, faceResult.imageWidth, faceResult.imageHeight
            )

            if (leftCheekLab == null || rightCheekLab == null) return 0.5f

            // 计算色差
            val deltaL = abs(leftCheekLab.l - rightCheekLab.l)
            val deltaA = abs(leftCheekLab.a - rightCheekLab.a)
            val deltaB = abs(leftCheekLab.b - rightCheekLab.b)

            // 色差越小，对称性越好
            val maxDelta = 100f
            val lScore = (1f - (deltaL / maxDelta)).coerceIn(0f, 1f)
            val aScore = (1f - (deltaA / 256f)).coerceIn(0f, 1f)
            val bScore = (1f - (deltaB / 256f)).coerceIn(0f, 1f)

            val score = (lScore * 0.5f + aScore * 0.25f + bScore * 0.25f).coerceIn(0f, 1f)
            Timber.d("MediaPipeFaceAnalyzer: symmetry score=%.2f", score)
            score
        } catch (e: Exception) {
            Timber.w(e, "MediaPipeFaceAnalyzer: symmetry computation failed")
            0.5f
        }
    }

    /**
     * 从关键点区域提取平均 CIELAB 颜色。
     */
    private fun extractZoneLab(
        bitmap: Bitmap,
        faceResult: MediaPipeFaceDetector.MediaPipeFaceResult,
        indices: IntArray,
        imageWidth: Int,
        imageHeight: Int
    ): ColorAnalyzer.LabColor? {
        val landmarks = faceResult.landmarks
        val points = indices.mapNotNull { index ->
            if (index < landmarks.size) {
                val lm = landmarks[index]
                PointF(lm.x() * imageWidth, lm.y() * imageHeight)
            } else null
        }
        if (points.isEmpty()) return null

        val minX = points.minOf { it.x }.toInt().coerceIn(0, imageWidth - 1)
        val minY = points.minOf { it.y }.toInt().coerceIn(0, imageHeight - 1)
        val maxX = points.maxOf { it.x }.toInt().coerceIn(1, imageWidth)
        val maxY = points.maxOf { it.y }.toInt().coerceIn(1, imageHeight)

        // 使用步长为 2 的快速采样
        var sumL = 0f
        var sumA = 0f
        var sumB = 0f
        var count = 0
        val step = 2

        for (y in minY until maxY step step) {
            for (x in minX until maxX step step) {
                if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val lab = rgbToLabQuick(
                        android.graphics.Color.red(pixel),
                        android.graphics.Color.green(pixel),
                        android.graphics.Color.blue(pixel)
                    )
                    sumL += lab.l
                    sumA += lab.a
                    sumB += lab.b
                    count++
                }
            }
        }

        return if (count > 0) {
            ColorAnalyzer.LabColor(sumL / count, sumA / count, sumB / count)
        } else null
    }

    /**
     * 快速 sRGB → CIELAB 转换（用于对称性计算，精度略低于 ColorAnalyzer 版本）。
     */
    private fun rgbToLabQuick(r: Int, g: Int, b: Int): ColorAnalyzer.LabColor {
        var rr = r / 255.0
        var gg = g / 255.0
        var bb = b / 255.0

        rr = if (rr > 0.04045) Math.pow((rr + 0.055) / 1.055, 2.4) else rr / 12.92
        gg = if (gg > 0.04045) Math.pow((gg + 0.055) / 1.055, 2.4) else gg / 12.92
        bb = if (bb > 0.04045) Math.pow((bb + 0.055) / 1.055, 2.4) else bb / 12.92

        val x = (rr * 0.4124564 + gg * 0.3575761 + bb * 0.1804375) * 100.0
        val y = (rr * 0.2126729 + gg * 0.7151522 + bb * 0.0721750) * 100.0
        val z = (rr * 0.0193339 + gg * 0.1191920 + bb * 0.9503041) * 100.0

        val xn = 95.047
        val yn = 100.000
        val zn = 108.883

        fun f(t: Double): Double = if (t > 0.008856) Math.cbrt(t) else (7.787 * t + 16.0 / 116.0)

        val fy = f(y / yn)
        val l = (116.0 * fy - 16.0).toFloat()
        val a = (500.0 * (f(x / xn) - fy)).toFloat()
        val bVal = (200.0 * (fy - f(z / zn))).toFloat()

        return ColorAnalyzer.LabColor(l, a, bVal)
    }

    /**
     * 检测异常发现。
     */
    private fun detectAbnormalities(
        zones: List<ZoneAnalysis>,
        symmetryScore: Float,
        faceResult: MediaPipeFaceDetector.MediaPipeFaceResult
    ): List<String> {
        val issues = mutableListOf<String>()

        // 各区域异常面色
        for (zone in zones) {
            val mapping = TCM_ZONES.find { it.name == zone.name } ?: continue
            val organNote = mapping.abnormalSigns[zone.color]
            if (organNote != null) {
                issues.add("${zone.name}(${mapping.organ}): $organNote")
            }
        }

        // 左右对称性检查
        if (symmetryScore < 0.75f) {
            issues.add("左右面色不对称(对称性=${"%.2f".format(symmetryScore)}) — 提示经络气血失调")
        }

        // 整体光泽度检查
        val avgGloss = zones.map { it.gloss }.average().toFloat()
        if (avgGloss < 0.25f) {
            issues.add("整体面色晦暗 — 提示气血不足或肾虚")
        }

        // 眼周色暗检查（肝血亏虚的特征指标）
        val eyeZone = zones.find { it.name == "眼周" }
        if (eyeZone != null && eyeZone.color == "晦暗") {
            issues.add("眼周晦暗 — 提示肝血亏虚，建议注意作息")
        }

        // 唇周色淡检查（脾胃虚弱）
        val lipZone = zones.find { it.name == "唇周" }
        if (lipZone != null && lipZone.color == "偏白") {
            issues.add("唇周偏白 — 提示脾胃虚弱，气血生化不足")
        }

        if (issues.isEmpty()) {
            issues.add("面色未见明显异常，气血调和")
        }

        return issues
    }

    /**
     * 计算诊断置信度。
     */
    private fun computeConfidence(
        zones: List<ZoneAnalysis>,
        faceResult: MediaPipeFaceDetector.MediaPipeFaceResult
    ): Float {
        var conf = 0.85f

        // 关键点数量检查
        if (faceResult.landmarks.size < 468) {
            conf -= 0.1f
        }

        // 面部轮廓完整性
        if (faceResult.faceOval.size < 30) {
            conf -= 0.1f
        }

        // 各区域有效性检查
        val validZones = zones.count { it.color != "未知" }
        if (validZones < 5) {
            conf -= 0.15f
        }

        // Blendshapes 可用性
        if (faceResult.faceBlendshapes == null) {
            conf -= 0.05f
        }

        return conf.coerceIn(0.4f, 1f)
    }
}