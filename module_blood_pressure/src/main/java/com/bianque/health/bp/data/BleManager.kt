package com.bianque.health.bp.data

import com.bianque.health.bp.domain.model.BloodPressureResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor() {

    suspend fun scanDevices(): List<String> = withContext(Dispatchers.Default) {
        // TODO: BLE device scanning
        Timber.d("BleManager: scanning for BLE devices...")
        emptyList()
    }

    suspend fun connect(deviceAddress: String): Boolean = withContext(Dispatchers.Default) {
        // TODO: BLE GATT connection
        Timber.d("BleManager: connecting to device $deviceAddress...")
        false
    }

    suspend fun readBloodPressure(): BloodPressureResult? = withContext(Dispatchers.Default) {
        // TODO: Read blood pressure measurement from BLE device
        Timber.d("BleManager: reading blood pressure...")
        null
    }

    suspend fun disconnect() = withContext(Dispatchers.Default) {
        // TODO: Disconnect BLE device
        Timber.d("BleManager: disconnecting...")
    }
}