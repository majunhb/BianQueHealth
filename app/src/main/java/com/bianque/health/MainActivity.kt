package com.bianque.health

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.bianque.health.ui.BianQueNavHost
import com.bianque.health.ui.theme.BianQueHealthTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Timber.d("MainActivity: CAMERA permission %s", if (granted) "granted" else "denied")
    }

    private val bluetoothConnectLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Timber.d("MainActivity: BLUETOOTH_CONNECT permission %s", if (granted) "granted" else "denied")
    }

    private val bluetoothScanLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Timber.d("MainActivity: BLUETOOTH_SCAN permission %s", if (granted) "granted" else "denied")
    }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Timber.d("MainActivity: ACCESS_FINE_LOCATION permission %s", if (granted) "granted" else "denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 应用启动时请求相机权限
        requestCameraPermission()
        // 蓝牙和位置权限（按需延迟请求，避免启动时弹窗过多）
        requestBluetoothPermissions()

        setContent {
            BianQueHealthTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BianQueNavHost()
                }
            }
        }
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * 请求蓝牙和位置权限。
     * Android 12+：BLUETOOTH_SCAN/ADVERTISE/CONNECT 替代 BLUETOOTH/BLUETOOTH_ADMIN
     * Android 12+：BLUETOOTH_SCAN 需要配合 ACCESS_FINE_LOCATION（设备发现场景）
     */
    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+：需要 BLUETOOTH_CONNECT 和 BLUETOOTH_SCAN（运行时权限）
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothConnectLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothScanLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        // 位置权限：蓝牙扫描需要（Android 12+ 配置了 neverForLocation 可豁免）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}