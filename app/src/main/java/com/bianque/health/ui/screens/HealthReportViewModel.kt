package com.bianque.health.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.engine.data.DiagnosisCache
import com.bianque.health.engine.domain.HealthEngineRepository
import com.bianque.health.engine.domain.model.HealthReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class HealthReportUiState {
    data object Loading : HealthReportUiState()
    data class Success(val report: HealthReport) : HealthReportUiState()
    data class Error(val message: String) : HealthReportUiState()
    /** 诊断数据不完整，提示用户先完成各模块检测 */
    data class Incomplete(val missingModules: List<String>) : HealthReportUiState()
}

@HiltViewModel
class HealthReportViewModel @Inject constructor(
    private val repository: HealthEngineRepository,
    private val diagnosisCache: DiagnosisCache
) : ViewModel() {

    private val _uiState = MutableStateFlow<HealthReportUiState>(HealthReportUiState.Loading)
    val uiState: StateFlow<HealthReportUiState> = _uiState.asStateFlow()

    fun generateReport() {
        val faceResult = diagnosisCache.faceResult
        val tongueResult = diagnosisCache.tongueResult
        val pulseResult = diagnosisCache.pulseResult
        val bpResult = diagnosisCache.bpResult

        // 检查数据完整性
        val missing = mutableListOf<String>()
        if (faceResult == null) missing.add("面诊")
        if (tongueResult == null) missing.add("舌诊")
        if (pulseResult == null) missing.add("脉诊")
        if (bpResult == null) missing.add("血压")

        if (missing.isNotEmpty()) {
            _uiState.value = HealthReportUiState.Incomplete(missing)
            return
        }

        _uiState.value = HealthReportUiState.Loading

        viewModelScope.launch {
            try {
                val report = repository.generateReport(
                    userId = "default_user",
                    faceResult = faceResult!!,
                    tongueResult = tongueResult!!,
                    pulseResult = pulseResult!!,
                    bpResult = bpResult!!
                )
                _uiState.value = HealthReportUiState.Success(report)
            } catch (e: Exception) {
                Timber.e(e, "HealthReportViewModel: failed to generate report")
                _uiState.value = HealthReportUiState.Error(
                    e.message ?: "报告生成失败，请重试"
                )
            }
        }
    }
}