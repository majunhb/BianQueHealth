package com.bianque.health.ui.screens

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bianque.health.base.camera.CameraHelper
import com.bianque.health.engine.data.DiagnosisCache
import com.bianque.health.face.data.FaceMeshDetector
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import com.bianque.health.ui.components.DiagnosisLabel
import com.bianque.health.ui.theme.Danger40
import com.bianque.health.ui.theme.Green40
import com.bianque.health.ui.theme.Warm40
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hilt EntryPoint 用于从 Composable 中获取面诊检测器
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FaceScanEntryPoint {
    fun faceMeshDetector(): FaceMeshDetector
    fun diagnosisCache(): DiagnosisCache
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceScanScreen(onBack: () -> Unit) {
    var isAnalyzing by remember { mutableStateOf(false) }
    var diagnosisResult by remember { mutableStateOf<FaceDiagnosisResult?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val detector = remember {
        EntryPointAccessors.fromApplication(context, FaceScanEntryPoint::class.java).faceMeshDetector()
    }
    val diagnosisCache = remember {
        EntryPointAccessors.fromApplication(context, FaceScanEntryPoint::class.java).diagnosisCache()
    }

    // 清理 CameraX
    DisposableEffect(Unit) {
        onDispose { CameraHelper.unbind() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("面诊") },
                navigationIcon = {
                    IconButton(onClick = {
                        CameraHelper.unbind()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            // 摄像头预览 / 结果展示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (diagnosisResult == null && errorMessage == null) {
                    // 摄像头预览
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                post {
                                    CameraHelper.bind(
                                        lifecycleOwner = lifecycleOwner,
                                        previewView = this,
                                        cameraFacing = CameraSelector.LENS_FACING_FRONT,
                                        onFrame = { bitmap -> capturedBitmap = bitmap }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (diagnosisResult != null) {
                    // 诊断结果
                    FaceDiagnosisResultCard(result = diagnosisResult!!)
                } else if (errorMessage != null) {
                    // 错误提示
                    ErrorCard(message = errorMessage!!) {
                        errorMessage = null
                    }
                }

                // 分析中遮罩
                if (isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在分析面部特征...", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // 底部按钮
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (diagnosisResult == null) {
                    Button(
                        onClick = {
                            val bitmap = capturedBitmap
                            if (bitmap == null) {
                                errorMessage = "请对准面部，确保光线充足"
                                return@Button
                            }
                            isAnalyzing = true
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.Default) {
                                        detector.detect(bitmap)
                                    }
                                    diagnosisResult = result
                                    diagnosisCache.faceResult = result
                                } catch (e: Exception) {
                                    errorMessage = "面部分析失败: ${e.message}"
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAnalyzing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("开始面诊", style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    Button(
                        onClick = {
                            diagnosisResult = null
                            capturedBitmap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Warm40)
                    ) {
                        Text("重新检测", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceDiagnosisResultCard(result: FaceDiagnosisResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "面诊结果",
                style = MaterialTheme.typography.headlineMedium,
                color = Green40,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            DiagnosisLabel(label = "整体面色", value = result.overallComplexion)
            DiagnosisLabel(label = "光泽度", value = "${(result.glossLevel * 100).toInt()}%")
            DiagnosisLabel(label = "置信度", value = "${(result.confidence * 100).toInt()}%")

            if (result.regions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "面部分区分析",
                    style = MaterialTheme.typography.titleLarge,
                    color = Green40,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                result.regions.forEach { region ->
                    DiagnosisLabel(label = region.name, value = region.color)
                }
            }

            if (result.abnormalities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "提示",
                    style = MaterialTheme.typography.titleLarge,
                    color = Danger40,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                result.abnormalities.forEach { issue ->
                    Text(
                        text = "• $issue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Danger40.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("提示", style = MaterialTheme.typography.headlineMedium, color = Danger40)
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("重试")
            }
        }
    }
}

