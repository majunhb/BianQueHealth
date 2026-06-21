package com.bianque.health.ui.screens

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.base.analysis.ImageQualityAnalyzer
import com.bianque.health.base.analysis.ImageQualityAnalyzer.DetectionState
import com.bianque.health.base.data.local.HealthDao
import com.bianque.health.base.data.local.HealthRecordEntity
import com.bianque.health.engine.data.DiagnosisCache
import com.bianque.health.tongue.data.TongueFeatureExtractor
import com.bianque.health.tongue.data.TongueSegmenter
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class TongueScanUiState(
    val isAnalyzing: Boolean = false,
    val isScanning: Boolean = false,
    val detectionState: ImageQualityAnalyzer.DetectionState = ImageQualityAnalyzer.DetectionState.NOT_DETECTED,
    val qualityScore: Float = 0f,
    val statusMessage: String? = "请张嘴伸舌，对齐轮廓线",
    val diagnosisResult: TongueDiagnosisResult? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class TongueScanViewModel @Inject constructor(
    private val tongueSegmenter: TongueSegmenter,
    private val tongueFeatureExtractor: TongueFeatureExtractor,
    private val diagnosisCache: DiagnosisCache,
    private val healthDao: HealthDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TongueScanUiState())
    val uiState: StateFlow<TongueScanUiState> = _uiState.asStateFlow()

    private var lastFrameAnalysisTime = 0L
    private val frameAnalysisIntervalMs = 400L
    private var captureCooldownUntil = 0L

    fun analyzeFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastFrameAnalysisTime < frameAnalysisIntervalMs) return
        lastFrameAnalysisTime = now

        // 冷却期内跳过分析
        if (now < captureCooldownUntil) return

        viewModelScope.launch {
            try {
                val quality = withContext(Dispatchers.Default) {
                    ImageQualityAnalyzer.analyzeTongueFrame(bitmap)
                }
                _uiState.value = _uiState.value.copy(
                    detectionState = quality.detectionState,
                    qualityScore = quality.score,
                    statusMessage = when (quality.detectionState) {
                        DetectionState.NOT_DETECTED -> "请张嘴伸舌，对齐轮廓线"
                        DetectionState.POOR_QUALITY -> "正在对焦，请保持稳定"
                        DetectionState.READY -> "对焦成功，正在自动检测…"
                    }
                )
            } catch (e: Exception) {
                Timber.w(e, "TongueScanViewModel: frame analysis failed")
            }
        }
    }

    fun autoCapture(bitmap: Bitmap) {
        if (_uiState.value.isScanning || _uiState.value.isAnalyzing) return
        if (_uiState.value.diagnosisResult != null) return

        _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)

        viewModelScope.launch {
            // 扫描动画持续 1.5 秒
            delay(1500)
            _uiState.value = _uiState.value.copy(isScanning = false, isAnalyzing = true)

            try {
                val masked = withContext(Dispatchers.Default) { tongueSegmenter.segment(bitmap) }
                val result = withContext(Dispatchers.Default) { tongueFeatureExtractor.extract(masked) }
                diagnosisCache.tongueResult = result
                persistResult(result)
                _uiState.value = _uiState.value.copy(isAnalyzing = false, diagnosisResult = result)
            } catch (e: Exception) {
                Timber.e(e, "TongueScanViewModel: autoCapture failed")
                captureCooldownUntil = System.currentTimeMillis() + 3000
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    isAnalyzing = false,
                    detectionState = DetectionState.POOR_QUALITY,
                    statusMessage = "正在对焦，请保持稳定"
                )
            }
        }
    }

    fun reset() {
        captureCooldownUntil = 0L
        _uiState.value = TongueScanUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private suspend fun persistResult(result: TongueDiagnosisResult) {
        try {
            val json = JSONObject().apply {
                put("tongueColor", result.tongueColor)
                put("coatingColor", result.coatingColor)
                put("coatingThickness", result.coatingThickness)
                put("coatingMoisture", result.coatingMoisture)
                put("tongueShape", result.tongueShape)
                put("tongueBody", result.tongueBody)
                put("sublingualVein", result.sublingualVein)
                put("tongueMobility", result.tongueMobility)
                put("confidence", result.confidence.toDouble())
                put("timestamp", result.timestamp)
            }.toString()

            healthDao.insertRecord(HealthRecordEntity(
                id = UUID.randomUUID().toString(),
                userId = "default_user",
                moduleType = "tongue",
                resultJson = json,
                schemaVersion = 1,
                timestamp = System.currentTimeMillis(),
                confidence = result.confidence,
                syncStatus = 0
            ))
        } catch (e: Exception) {
            Timber.w(e, "TongueScanViewModel: failed to persist")
        }
    }
}