package com.bianque.health.ui.screens

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.base.data.local.HealthDao
import com.bianque.health.base.data.local.HealthRecordEntity
import com.bianque.health.bp.data.BpAlertEngine
import com.bianque.health.bp.data.BpTrendAnalyzer
import com.bianque.health.bp.data.QualityControlEngine
import com.bianque.health.bp.domain.BloodPressureRepository
import com.bianque.health.bp.domain.model.BloodPressureResult
import com.bianque.health.engine.data.DiagnosisCache
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

/**
 * 血压测量页面 UI 状态。
 */
data class BPUiState(
    val systolic: Int = 0,
    val diastolic: Int = 0,
    val heartRate: Int = 0,
    val isMeasuring: Boolean = false,
    val isAnalyzing: Boolean = false,
    val measurementMethod: String = "",
    val confidence: Float? = null,
    val errorMessage: String? = null,
    val progress: Float = 0f,
    val progressMessage: String = "",
    val qualityScore: Float? = null,
    val alertResult: BpAlertEngine.BpAlertResult? = null,
    val trendResult: BpTrendAnalyzer.BpTrendResult? = null,
    val history: List<BPRecord> = emptyList(),
    val isBleConnected: Boolean = false,
    val showResults: Boolean = false,
    val framesCollected: Int = 0,
    val guideMessage: String = "请将面部对准摄像头，保持静止"
)

data class BPRecord(
    val systolic: Int,
    val diastolic: Int,
    val heartRate: Int,
    val method: String,
    val timestamp: Long,
    val alertLevel: String = "正常"
)

@HiltViewModel
class BloodPressureViewModel @Inject constructor(
    private val bloodPressureRepository: BloodPressureRepository,
    private val diagnosisCache: DiagnosisCache,
    private val healthDao: HealthDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BPUiState())
    val uiState: StateFlow<BPUiState> = _uiState.asStateFlow()

    private var collectedFrames = mutableListOf<Bitmap>()

    /** 开始 rPPG 光感测压 */
    fun startMeasurement() {
        if (_uiState.value.isMeasuring) return

        _uiState.value = _uiState.value.copy(
            isMeasuring = true,
            progress = 0f,
            progressMessage = "正在准备采集…",
            errorMessage = null,
            showResults = false,
            guideMessage = "请将面部对准摄像头，保持静止",
            framesCollected = 0
        )
        collectedFrames.clear()
        QualityControlEngine.reset()
    }

    /** 添加一帧（由 CameraX ImageAnalysis 回调驱动） */
    fun addFrame(bitmap: Bitmap) {
        if (!_uiState.value.isMeasuring) return

        collectedFrames.add(bitmap)

        val frameCount = collectedFrames.size
        val targetFrames = 300  // 30fps × 10秒

        val progress = (frameCount.toFloat() / targetFrames).coerceIn(0f, 0.9f)
        _uiState.value = _uiState.value.copy(
            framesCollected = frameCount,
            progress = progress,
            progressMessage = "正在采集光感信号… ${frameCount}/${targetFrames}"
        )

        // 10 秒后自动触发分析
        if (frameCount >= targetFrames) {
            analyzeAndFinish()
        }
    }

    /** 手动触发分析 */
    fun analyzeAndFinish() {
        // 双重调用防护
        if (_uiState.value.isAnalyzing) return

        val frames = collectedFrames.toList()
        if (frames.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isMeasuring = false,
                errorMessage = "未采集到有效帧，请重试"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isMeasuring = false,
            isAnalyzing = true,
            progress = 0.9f,
            progressMessage = "正在分析信号…"
        )

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    bloodPressureRepository.measureViaPpg(frames)
                }

                if (result.systolic <= 0 || result.heartRate <= 0) {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        errorMessage = "信号提取失败，请确保面部光线充足且保持静止",
                        guideMessage = "请移至明亮处，面部正对摄像头，保持静止后重试"
                    )
                    return@launch
                }

                // 异常预警
                val alertResult = BpAlertEngine.analyze(result)

                // 趋势分析（加载历史数据）
                val historyRecords = loadHistoryRecords()
                val trendResult = if (historyRecords.isNotEmpty()) {
                    BpTrendAnalyzer.analyzeWeeklyTrend(historyRecords + result)
                } else null

                // 缓存 + 持久化
                diagnosisCache.bpResult = result
                persistResult(result)

                _uiState.value = _uiState.value.copy(
                    systolic = result.systolic,
                    diastolic = result.diastolic,
                    heartRate = result.heartRate,
                    measurementMethod = result.measurementMethod,
                    isAnalyzing = false,
                    progress = 1f,
                    progressMessage = "测量完成",
                    showResults = true,
                    alertResult = alertResult,
                    trendResult = trendResult,
                    guideMessage = when (alertResult.overallLevel) {
                        BpAlertEngine.AlertLevel.CRITICAL -> "⚠️ 请立即就医"
                        BpAlertEngine.AlertLevel.HIGH -> "建议尽快就医"
                        BpAlertEngine.AlertLevel.MEDIUM -> "建议关注血压变化"
                        else -> "测量完成，请查看结果"
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "BloodPressureViewModel: measurement failed")
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = "测量异常：${e.message}"
                )
            }
        }
    }

    fun reset() {
        // 回收 Bitmap 内存
        collectedFrames.forEach { it.recycle() }
        collectedFrames.clear()
        QualityControlEngine.reset()
        _uiState.value = BPUiState(history = _uiState.value.history)
    }

    fun toggleConnection() {
        _uiState.value = _uiState.value.copy(
            isBleConnected = !_uiState.value.isBleConnected
        )
    }

    /** 加载历史血压记录 */
    fun loadHistory() {
        viewModelScope.launch {
            try {
                val records = withContext(Dispatchers.IO) {
                    healthDao.getRecordsByModule("BLOOD_PRESSURE")
                }
                val bpRecords = records.map { entity ->
                    val json = org.json.JSONObject(entity.resultJson)
                    BPRecord(
                        systolic = json.optInt("systolic", 0),
                        diastolic = json.optInt("diastolic", 0),
                        heartRate = json.optInt("heartRate", 0),
                        method = json.optString("method", "rPPG"),
                        timestamp = entity.timestamp,
                        alertLevel = BpAlertEngine.analyze(
                            BloodPressureResult(
                                json.optInt("systolic", 0),
                                json.optInt("diastolic", 0),
                                json.optInt("heartRate", 0),
                                json.optString("method", "rPPG"),
                                null,
                                entity.timestamp
                            )
                        ).overallLevel.name
                    )
                }
                _uiState.value = _uiState.value.copy(history = bpRecords)
            } catch (e: Exception) {
                Timber.w(e, "BloodPressureViewModel: failed to load history")
            }
        }
    }

    private suspend fun loadHistoryRecords(): List<BloodPressureResult> {
        return try {
            val records = withContext(Dispatchers.IO) {
                healthDao.getRecordsByModule("BLOOD_PRESSURE")
            }
            records.map { entity ->
                val json = JSONObject(entity.resultJson)
                BloodPressureResult(
                    systolic = json.optInt("systolic", 0),
                    diastolic = json.optInt("diastolic", 0),
                    heartRate = json.optInt("heartRate", 0),
                    measurementMethod = json.optString("method", "rPPG"),
                    deviceName = null,
                    timestamp = entity.timestamp
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun persistResult(result: BloodPressureResult) {
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("systolic", result.systolic)
                    put("diastolic", result.diastolic)
                    put("heartRate", result.heartRate)
                    put("method", result.measurementMethod)
                    put("deviceName", result.deviceName ?: "")
                }
                healthDao.insertRecord(
                    HealthRecordEntity(
                        id = UUID.randomUUID().toString(),
                        userId = "local_user",
                        moduleType = "BLOOD_PRESSURE",
                        resultJson = json.toString(),
                        timestamp = result.timestamp,
                        confidence = 0f,
                        syncStatus = 0
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "BloodPressureViewModel: failed to persist result")
            }
        }
    }
}