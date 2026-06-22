package com.bianque.health.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bianque.health.base.data.local.HealthDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class PrivacySettingsUiState(
    val faceRecordCount: Int = 0,
    val tongueRecordCount: Int = 0,
    val pulseRecordCount: Int = 0,
    val isDeleted: Boolean = false
)

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val healthDao: HealthDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacySettingsUiState())
    val uiState: StateFlow<PrivacySettingsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            try {
                val faceRecords = withContext(Dispatchers.IO) {
                    healthDao.getRecordsByModule("face")
                }
                val tongueRecords = withContext(Dispatchers.IO) {
                    healthDao.getRecordsByModule("tongue")
                }
                val pulseRecords = withContext(Dispatchers.IO) {
                    healthDao.getRecordsByModule("pulse")
                }
                _uiState.value = _uiState.value.copy(
                    faceRecordCount = faceRecords.size,
                    tongueRecordCount = tongueRecords.size,
                    pulseRecordCount = pulseRecords.size
                )
            } catch (e: Exception) {
                Timber.w(e, "PrivacySettingsViewModel: failed to load stats")
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    healthDao.deleteAllRecords()
                }
                _uiState.value = _uiState.value.copy(
                    faceRecordCount = 0,
                    tongueRecordCount = 0,
                    pulseRecordCount = 0,
                    isDeleted = true
                )
            } catch (e: Exception) {
                Timber.e(e, "PrivacySettingsViewModel: failed to delete all data")
            }
        }
    }
}