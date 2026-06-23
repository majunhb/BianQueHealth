package com.bianque.health.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.base.analysis.ImageQualityAnalyzer.DetectionState
import com.bianque.health.base.data.local.HealthDao
import com.bianque.health.base.data.local.HealthRecordEntity
import com.bianque.health.engine.data.DiagnosisCache
import com.bianque.health.face.data.FaceMeshDetector
import com.bianque.health.face.data.FacePreviewAnalyzer
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class FaceScanUiState(
    val isAnalyzing: Boolean = false,
    val isScanning: Boolean = false,
    val detectionState: DetectionState = DetectionState.NOT_DETECTED,
    val qualityScore: Float = 0f,
    val statusMessage: String? = "请将面部置于框内，保持正脸",
    val diagnosisResult: FaceDiagnosisResult? = null,
    val errorMessage: String? = null,
    // 实时面部预览数据（用于绘制真实面部轮廓线）
    val faceFound: Boolean = false,
    val faceRect: Rect? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val faceContourPoints: List<PointF> = emptyList(),
    // 面部中心坐标（图像坐标系，用于动态跟踪圆形叠加层）
    val faceCenterX: Float = 0f,
    val faceCenterY: Float = 0f,
    val faceRadius: Float = 0f
)

@HiltViewModel
class FaceScanViewModel @Inject constructor(
    private val faceMeshDetector: FaceMeshDetector,
    private val facePreviewAnalyzer: FacePreviewAnalyzer,
    private val diagnosisCache: DiagnosisCache,
    private val healthDao: HealthDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FaceScanUiState())
    val uiState: StateFlow<FaceScanUiState> = _uiState.asStateFlow()

    private var lastFrameAnalysisTime = 0L
    private val frameAnalysisIntervalMs = 300L
    private var captureCooldownUntil = 0L

    fun analyzeFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastFrameAnalysisTime < frameAnalysisIntervalMs) return
        lastFrameAnalysisTime = now

        if (now < captureCooldownUntil) return

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    facePreviewAnalyzer.analyze(bitmap)
                } ?: return@launch // 节流跳过

                _uiState.value = _uiState.value.copy(
                    detectionState = result.detectionState,
                    qualityScore = result.faceSizeRatio,
                    statusMessage = result.guidanceMessage,
                    faceFound = result.faceFound,
                    faceRect = result.boundingBox,
                    imageWidth = result.imageWidth,
                    imageHeight = result.imageHeight,
                    faceContourPoints = result.contourPoints,
                    faceCenterX = result.faceCenterX,
                    faceCenterY = result.faceCenterY,
                    faceRadius = result.faceRadius
                )
            } catch (e: Exception) {
                Timber.w(e, "FaceScanViewModel: frame analysis failed")
            }
        }
    }

    fun autoCapture(bitmap: Bitmap) {
        if (_uiState.value.isScanning || _uiState.value.isAnalyzing) return
        if (_uiState.value.diagnosisResult != null) return
        if (!_uiState.value.faceFound) return

        _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)

        viewModelScope.launch {
            try {
                // 扫描动画 0.5 秒（快速反馈）
                delay(500)
                _uiState.value = _uiState.value.copy(isScanning = false, isAnalyzing = true)

                // 调用 MediaPipe 468点七区高精度分析（ML Kit 回退）
                val result = withContext(Dispatchers.Default) {
                    faceMeshDetector.detectHybrid(appContext, bitmap)
                }

                if (result.overallComplexion == "未检测到面部") {
                    captureCooldownUntil = System.currentTimeMillis() + 2000
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        isAnalyzing = false,
                        detectionState = DetectionState.POOR_QUALITY,
                        statusMessage = "识别失败，请确保面部光线充足且保持静止"
                    )
                    return@launch
                }

                diagnosisCache.faceResult = result
                persistResult(result)
                _uiState.value = _uiState.value.copy(isAnalyzing = false, diagnosisResult = result)
            } catch (e: Exception) {
                Timber.e(e, "FaceScanViewModel: autoCapture failed")
                captureCooldownUntil = System.currentTimeMillis() + 2000
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    isAnalyzing = false,
                    detectionState = DetectionState.POOR_QUALITY,
                    statusMessage = "检测异常，请调整光线与姿势后重试"
                )
            }
        }
    }

    fun reset() {
        captureCooldownUntil = 0L
        _uiState.value = FaceScanUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private suspend fun persistResult(result: FaceDiagnosisResult) {
        try {
            val regionsArray = JSONArray()
            result.regions.forEach { region ->
                regionsArray.put(JSONObject().apply {
                    put("name", region.name)
                    put("color", region.color)
                    put("brightness", region.brightness.toDouble())
                    put("redGreen", region.redGreen.toDouble())
                    put("yellowBlue", region.yellowBlue.toDouble())
                })
            }
            val json = JSONObject().apply {
                put("overallComplexion", result.overallComplexion)
                put("glossLevel", result.glossLevel.toDouble())
                put("regions", regionsArray)
                put("abnormalities", JSONArray(result.abnormalities))
                put("confidence", result.confidence.toDouble())
                put("timestamp", result.timestamp)
            }.toString()

            healthDao.insertRecord(HealthRecordEntity(
                id = UUID.randomUUID().toString(),
                userId = "default_user",
                moduleType = "face",
                resultJson = json,
                schemaVersion = 1,
                timestamp = System.currentTimeMillis(),
                confidence = result.confidence,
                syncStatus = 0
            ))
        } catch (e: Exception) {
            Timber.w(e, "FaceScanViewModel: failed to persist")
        }
    }
}