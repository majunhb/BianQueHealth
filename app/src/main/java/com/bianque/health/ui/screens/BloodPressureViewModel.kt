package com.bianque.health.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.base.data.local.HealthDao
import com.bianque.health.base.data.local.HealthRecordEntity
import com.bianque.health.bp.domain.model.BloodPressureResult
import com.bianque.health.engine.data.DiagnosisCache
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

data class BPRecord(
    val systolic: Int,
    val diastolic: Int,
    val heartRate: Int,
    val timestamp: String
)

data class BPUiState(
    val isMeasuring: Boolean = false,
    val isConnected: Boolean = false,
    val currentSystolic: Int = 120,
    val currentDiastolic: Int = 80,
    val currentHeartRate: Int = 72,
    val history: List<BPRecord> = listOf(
        BPRecord(118, 78, 70, "2026-06-19 08:30"),
        BPRecord(122, 82, 74, "2026-06-18 08:15"),
        BPRecord(119, 79, 71, "2026-06-17 20:00")
    )
)

@HiltViewModel
class BloodPressureViewModel @Inject constructor(
    private val diagnosisCache: DiagnosisCache,
    private val healthDao: HealthDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BPUiState())
    val uiState: StateFlow<BPUiState> = _uiState.asStateFlow()

    fun startMeasurement() {
        _uiState.value = _uiState.value.copy(isMeasuring = true)

        viewModelScope.launch {
            delay(3000)
            val systolic = (115..125).random()
            val diastolic = (75..85).random()
            val heartRate = (68..76).random()
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            val result = BloodPressureResult(
                systolic = systolic, diastolic = diastolic, heartRate = heartRate,
                measurementMethod = "模拟", deviceName = null
            )
            diagnosisCache.bpResult = result
            persistResult(result)

            _uiState.value = _uiState.value.copy(
                isMeasuring = false, currentSystolic = systolic,
                currentDiastolic = diastolic, currentHeartRate = heartRate,
                history = listOf(BPRecord(systolic, diastolic, heartRate, now)) + _uiState.value.history
            )
        }
    }

    fun toggleConnection() {
        _uiState.value = _uiState.value.copy(isConnected = !_uiState.value.isConnected)
    }

    private suspend fun persistResult(result: BloodPressureResult) {
        try {
            val json = JSONObject().apply {
                put("systolic", result.systolic)
                put("diastolic", result.diastolic)
                put("heartRate", result.heartRate)
                put("measurementMethod", result.measurementMethod)
                put("deviceName", result.deviceName ?: JSONObject.NULL)
                put("timestamp", result.timestamp)
            }.toString()

            healthDao.insertRecord(HealthRecordEntity(
                id = UUID.randomUUID().toString(), userId = "default_user",
                moduleType = "blood_pressure", resultJson = json, schemaVersion = 1,
                timestamp = System.currentTimeMillis(), confidence = 0.9f, syncStatus = 0
            ))
        } catch (e: Exception) {
            Timber.w(e, "BloodPressureViewModel: failed to persist")
        }
    }
}