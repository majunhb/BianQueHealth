package com.bianque.health.ui.screens

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.base.data.local.HealthDao
import com.bianque.health.base.data.local.HealthRecordEntity
import com.bianque.health.engine.data.DiagnosisCache
import com.bianque.health.pulse.domain.PulseDiagnosisRepository
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

data class PulseScanUiState(
    val isCapturing: Boolean = false,     // 正在采集信号
    val isAnalyzing: Boolean = false,     // 正在分析
    val frameCount: Int = 0,              // 已采集帧数
    val maxFrames: Int = 900,             // 最大帧数（30秒）
    val minFrames: Int = 90,              // 最小帧数（3秒）
    val signalQuality: Float = 0f,        // 信号质量 0-1
    val statusMessage: String = "请将面部置于镜头前，保持静止",
    val diagnosisResult: PulseDiagnosisResult? = null,
    val errorMessage: String? = null,
    val timeoutSeconds: Int = 30          // 倒计时
)

@HiltViewModel
class PulseScanViewModel @Inject constructor(
    private val repository: PulseDiagnosisRepository,
    private val diagnosisCache: DiagnosisCache,
    private val healthDao: HealthDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PulseScanUiState())
    val uiState: StateFlow<PulseScanUiState> = _uiState.asStateFlow()

    private var captureJob: Job? = null
    private var timeoutJob: Job? = null

    /**
     * 开始rPPG信号采集。
     * 每帧添加到processor，达到最小帧数后自动分析。
     */
    fun startCapture() {
        if (_uiState.value.isCapturing) return
        repository.reset()

        _uiState.value = PulseScanUiState(isCapturing = true)

        timeoutJob = viewModelScope.launch {
            for (sec in 30 downTo 0) {
                if (!_uiState.value.isCapturing) break
                _uiState.value = _uiState.value.copy(timeoutSeconds = sec)
                delay(1000)
            }
            if (_uiState.value.isCapturing) {
                stopCapture()
            }
        }
    }

    /**
     * 处理每一帧画面。
     */
    fun processFrame(bitmap: Bitmap) {
        if (!_uiState.value.isCapturing) return

        viewModelScope.launch {
            try {
                val rgb = withContext(Dispatchers.Default) {
                    repository.addFrame(bitmap)
                }
                val frameCount = repository.getFrameCount()

                _uiState.value = _uiState.value.copy(
                    frameCount = frameCount,
                    signalQuality = if (rgb != null) {
                        (frameCount.toFloat() / _uiState.value.minFrames).coerceAtMost(1f)
                    } else _uiState.value.signalQuality,
                    statusMessage = when {
                        rgb == null -> "未检测到面部皮肤，请保持面部正对镜头"
                        frameCount < _uiState.value.minFrames -> "正在采集信号… ${frameCount}/${_uiState.value.minFrames}"
                        else -> "信号采集完成，正在分析…"
                    }
                )

                // 达到最小帧数后自动分析
                if (frameCount >= _uiState.value.minFrames && !_uiState.value.isAnalyzing) {
                    analyzeAndFinish()
                }

                // 达到最大帧数强制停止
                if (frameCount >= _uiState.value.maxFrames) {
                    stopCapture()
                }
            } catch (e: Exception) {
                Timber.e(e, "PulseScanViewModel: processFrame failed")
            }
        }
    }

    private suspend fun analyzeAndFinish() {
        _uiState.value = _uiState.value.copy(isAnalyzing = true, statusMessage = "正在分析脉搏信号…")

        try {
            val result = withContext(Dispatchers.Default) {
                repository.analyze()
            }

            if (result != null) {
                diagnosisCache.pulseResult = result
                persistResult(result)
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    isAnalyzing = false,
                    statusMessage = "检测完成",
                    diagnosisResult = result
                )
            } else {
                // 信号不足，继续采集
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    statusMessage = "信号不足，请保持静止… ${repository.getFrameCount()}/${_uiState.value.maxFrames}"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "PulseScanViewModel: analyze failed")
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                isCapturing = false,
                errorMessage = "分析失败: ${e.message}"
            )
        }
    }

    private fun stopCapture() {
        _uiState.value = _uiState.value.copy(isCapturing = false)
        timeoutJob?.cancel()

        viewModelScope.launch {
            if (_uiState.value.diagnosisResult == null) {
                analyzeAndFinish()
            }
        }
    }

    fun reset() {
        captureJob?.cancel()
        timeoutJob?.cancel()
        repository.reset()
        _uiState.value = PulseScanUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private suspend fun persistResult(result: PulseDiagnosisResult) {
        try {
            val json = JSONObject().apply {
                put("pulseRate", result.pulseRate)
                put("pulseRhythm", result.pulseRhythm)
                put("pulseStrength", result.pulseStrength)
                put("pulseType", result.pulseType)
                put("systolic", result.systolic)
                put("diastolic", result.diastolic)
                put("pulseFeatures", JSONObject(result.pulseFeatures))
                put("confidence", result.confidence.toDouble())
                put("timestamp", result.timestamp)
            }.toString()

            healthDao.insertRecord(HealthRecordEntity(
                id = UUID.randomUUID().toString(),
                userId = "default_user",
                moduleType = "pulse",
                resultJson = json,
                schemaVersion = 1,
                timestamp = System.currentTimeMillis(),
                confidence = result.confidence,
                syncStatus = 0
            ))
        } catch (e: Exception) {
            Timber.w(e, "PulseScanViewModel: failed to persist")
        }
    }
}