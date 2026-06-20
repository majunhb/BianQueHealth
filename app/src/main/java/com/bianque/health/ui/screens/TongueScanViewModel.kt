package com.bianque.health.ui.screens

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.base.data.local.HealthDao
import com.bianque.health.base.data.local.HealthRecordEntity
import com.bianque.health.engine.data.DiagnosisCache
import com.bianque.health.tongue.data.TongueFeatureExtractor
import com.bianque.health.tongue.data.TongueSegmenter
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

    fun analyze(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(isAnalyzing = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val masked = withContext(Dispatchers.Default) { tongueSegmenter.segment(bitmap) }
                val result = withContext(Dispatchers.Default) { tongueFeatureExtractor.extract(masked) }
                diagnosisCache.tongueResult = result
                persistResult(result)
                _uiState.value = _uiState.value.copy(isAnalyzing = false, diagnosisResult = result)
            } catch (e: Exception) {
                Timber.e(e, "TongueScanViewModel: analysis failed")
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = "舌象分析失败: ${e.message}"
                )
            }
        }
    }

    fun reset() {
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