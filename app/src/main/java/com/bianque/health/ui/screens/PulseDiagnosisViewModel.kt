package com.bianque.health.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.base.data.local.HealthDao
import com.bianque.health.base.data.local.HealthRecordEntity
import com.bianque.health.engine.data.DiagnosisCache
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class PulseRecord(
    val pulseRate: Int,
    val pulseType: String,
    val pulseStrength: String,
    val timestamp: String
)

data class PulseUiState(
    val isMeasuring: Boolean = false,
    val isConnected: Boolean = false,
    val currentPulseRate: Int = 72,
    val currentPulseType: String = "平脉",
    val currentPulseStrength: String = "适中",
    val history: List<PulseRecord> = listOf(
        PulseRecord(70, "平脉", "适中", "2026-06-19 09:00"),
        PulseRecord(74, "细脉", "偏弱", "2026-06-18 09:30"),
        PulseRecord(68, "平脉", "适中", "2026-06-17 21:00")
    )
)

@HiltViewModel
class PulseDiagnosisViewModel @Inject constructor(
    private val diagnosisCache: DiagnosisCache,
    private val healthDao: HealthDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PulseUiState())
    val uiState: StateFlow<PulseUiState> = _uiState.asStateFlow()

    fun startMeasurement() {
        _uiState.value = _uiState.value.copy(isMeasuring = true)

        viewModelScope.launch {
            delay(3000)
            val pulseRate = (68..76).random()
            val pulseTypes = listOf("平脉", "细脉", "弦脉", "滑脉")
            val strengths = listOf("适中", "偏弱", "有力")
            val pulseType = pulseTypes.random()
            val pulseStrength = strengths.random()
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            val result = PulseDiagnosisResult(
                pulseRate = pulseRate, pulseRhythm = "规律",
                pulseStrength = pulseStrength, pulseType = pulseType,
                pulseFeatures = emptyMap(), confidence = 0.8f
            )
            diagnosisCache.pulseResult = result
            persistResult(result)

            _uiState.value = _uiState.value.copy(
                isMeasuring = false, currentPulseRate = pulseRate,
                currentPulseType = pulseType, currentPulseStrength = pulseStrength,
                history = listOf(PulseRecord(pulseRate, pulseType, pulseStrength, now)) + _uiState.value.history
            )
        }
    }

    fun toggleConnection() {
        _uiState.value = _uiState.value.copy(isConnected = !_uiState.value.isConnected)
    }

    private suspend fun persistResult(result: PulseDiagnosisResult) {
        try {
            val json = JSONObject().apply {
                put("pulseRate", result.pulseRate)
                put("pulseRhythm", result.pulseRhythm)
                put("pulseStrength", result.pulseStrength)
                put("pulseType", result.pulseType)
                put("pulseFeatures", JSONObject(result.pulseFeatures))
                put("confidence", result.confidence.toDouble())
                put("timestamp", result.timestamp)
            }.toString()

            healthDao.insertRecord(HealthRecordEntity(
                id = UUID.randomUUID().toString(), userId = "default_user",
                moduleType = "pulse", resultJson = json, schemaVersion = 1,
                timestamp = System.currentTimeMillis(), confidence = result.confidence, syncStatus = 0
            ))
        } catch (e: Exception) {
            Timber.w(e, "PulseDiagnosisViewModel: failed to persist")
        }
    }
}