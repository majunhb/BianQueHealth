package com.bianque.health.ui.screens

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.base.analysis.ImageQualityAnalyzer
import com.bianque.health.base.analysis.ImageQualityAnalyzer.DetectionState
import com.bianque.health.base.data.local.HealthDao
import com.bianque.health.base.data.local.HealthRecordEntity
import com.bianque.health.engine.data.DiagnosisCache
import com.bianque.health.face.data.FaceMeshDetector
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val detectionState: ImageQualityAnalyzer.DetectionState = ImageQualityAnalyzer.DetectionState.NOT_DETECTED,
    val qualityScore: Float = 0f,
    val statusMessage: String? = "请将面部置于框内，保持正脸",
    val diagnosisResult: FaceDiagnosisResult? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class FaceScanViewModel @Inject constructor(
    private val faceMeshDetector: FaceMeshDetector,
    private val diagnosisCache: DiagnosisCache,
    private val healthDao: HealthDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(FaceScanUiState())
    val uiState: StateFlow<FaceScanUiState> = _uiState.asStateFlow()

    private var lastFrameAnalysisTime = 0L
    private val frameAnalysisIntervalMs = 400L
    private var captureCooldownUntil = 0L  // 冷却期：失败后3秒内不报告READY

    fun analyzeFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastFrameAnalysisTime < frameAnalysisIntervalMs) return
        lastFrameAnalysisTime = now

        // 冷却期内跳过分析，避免连拍循环
        if (now < captureCooldownUntil) return

        viewModelScope.launch {
            try {
                val quality = withContext(Dispatchers.Default) {
                    ImageQualityAnalyzer.analyzeFacePresence(bitmap)
                }

                _uiState.value = _uiState.value.copy(
                    detectionState = quality.detectionState,
                    qualityScore = quality.score,
                    statusMessage = when (quality.detectionState) {
                        DetectionState.READY -> "定位成功，正在自动检测…"
                        DetectionState.POOR_QUALITY -> "正在定位，请保持面部正对镜头"
                        else -> "请将面部置于框内，保持正脸"
                    }
                )
            } catch (e: Exception) {
                Timber.w(e, "FaceScanViewModel: frame analysis failed")
            }
        }
    }

    fun autoCapture(bitmap: Bitmap) {
        if (_uiState.value.isScanning || _uiState.value.isAnalyzing) return
        if (_uiState.value.diagnosisResult != null) return

        _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val preCheck = withContext(Dispatchers.Default) {
                    faceMeshDetector.detect(bitmap)
                }
                if (preCheck.overallComplexion == "未检测到面部") {
                    // ML Kit未检测到人脸 → 进入3秒冷却期，防止连拍
                    captureCooldownUntil = System.currentTimeMillis() + 3000
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        detectionState = DetectionState.POOR_QUALITY,
                        statusMessage = "正在定位，请保持面部正对镜头"
                    )
                    return@launch
                }

                // 扫描动画持续 2 秒
                delay(2000)
                _uiState.value = _uiState.value.copy(isScanning = false, isAnalyzing = true)

                diagnosisCache.faceResult = preCheck
                persistResult(preCheck)
                _uiState.value = _uiState.value.copy(isAnalyzing = false, diagnosisResult = preCheck)
            } catch (e: Exception) {
                Timber.e(e, "FaceScanViewModel: autoCapture failed")
                captureCooldownUntil = System.currentTimeMillis() + 3000
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    isAnalyzing = false,
                    detectionState = DetectionState.POOR_QUALITY,
                    statusMessage = "正在定位，请保持面部正对镜头"
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