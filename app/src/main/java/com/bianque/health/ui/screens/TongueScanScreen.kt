package com.bianque.health.ui.screens

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bianque.health.ui.theme.Green40
import com.bianque.health.ui.theme.Warm40
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TongueScanScreen(onBack: () -> Unit) {
    var isAnalyzing by remember { mutableStateOf(false) }
    var diagnosisResult by remember { mutableStateOf<TongueDiagnosisResult?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("舌诊") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Camera preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (diagnosisResult == null) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                post {
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = surfaceProvider
                                        }
                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview
                                            )
                                        } catch (_: Exception) {
                                            // Camera binding failed
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Show diagnosis result
                    TongueDiagnosisResultCard(result = diagnosisResult!!)
                }

                // Analyzing overlay
                if (isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "正在分析舌象特征...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Bottom action button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (diagnosisResult == null) {
                    Button(
                        onClick = {
                            isAnalyzing = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAnalyzing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "开始舌诊",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            diagnosisResult = null
                            isAnalyzing = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Warm40
                        )
                    ) {
                        Text(
                            text = "重新检测",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }

    // Simulate analysis delay
    if (isAnalyzing) {
        LaunchedEffect(Unit) {
            delay(2000)
            isAnalyzing = false
            diagnosisResult = TongueDiagnosisResult(
                tongueColor = "淡红舌",
                tongueCoating = "薄白苔",
                tongueShape = "舌体适中",
                abnormalities = "无明显异常",
                constitutionHint = "平和质倾向"
            )
        }
    }
}

data class TongueDiagnosisResult(
    val tongueColor: String,
    val tongueCoating: String,
    val tongueShape: String,
    val abnormalities: String,
    val constitutionHint: String
)

@Composable
private fun TongueDiagnosisResultCard(result: TongueDiagnosisResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "舌诊结果",
                style = MaterialTheme.typography.headlineMedium,
                color = Green40,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            DiagnosisRow(label = "舌色", value = result.tongueColor)
            DiagnosisRow(label = "舌苔", value = result.tongueCoating)
            DiagnosisRow(label = "舌形", value = result.tongueShape)
            DiagnosisRow(label = "异常表现", value = result.abnormalities)
            DiagnosisRow(label = "体质倾向", value = result.constitutionHint)
        }
    }
}