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
import com.bianque.health.tongue.data.TongueFeatureExtractor
import com.bianque.health.tongue.data.TongueSegmenter
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
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
 * Hilt EntryPoint 用于从 Composable 中获取舌诊检测器
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TongueScanEntryPoint {
    fun tongueSegmenter(): TongueSegmenter
    fun tongueFeatureExtractor(): TongueFeatureExtractor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TongueScanScreen(onBack: () -> Unit) {
    var isAnalyzing by remember { mutableStateOf(false) }
    var diagnosisResult by remember { mutableStateOf<TongueDiagnosisResult?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val segmenter = remember {
        EntryPointAccessors.fromApplication(context, TongueScanEntryPoint::class.java).tongueSegmenter()
    }
    val extractor = remember {
        EntryPointAccessors.fromApplication(context, TongueScanEntryPoint::class.java).tongueFeatureExtractor()
    }

    // 清理 CameraX
    DisposableEffect(Unit) {
        onDispose { CameraHelper.unbind() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("舌诊") },
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
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                post {
                                    CameraHelper.bind(
                                        lifecycleOwner = lifecycleOwner,
                                        previewView = this,
                                        cameraFacing = CameraSelector.LENS_FACING_BACK,
                                        onFrame = { bitmap -> capturedBitmap = bitmap }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (diagnosisResult != null) {
                    // 诊断结果
                    TongueDiagnosisResultCard(result = diagnosisResult!!)
                } else if (errorMessage != null) {
                    // 错误提示
                    TongueErrorCard(message = errorMessage!!) {
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
                            Text("正在分析舌象特征...", color = Color.White, style = MaterialTheme.typography.bodyLarge)
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
                                errorMessage = "请对准舌头，确保光线充足"
                                return@Button
                            }
                            isAnalyzing = true
                            scope.launch {
                                try {
                                    val masked = withContext(Dispatchers.Default) {
                                        segmenter.segment(bitmap)
                                    }
                                    val result = withContext(Dispatchers.Default) {
                                        extractor.extract(masked)
                                    }
                                    diagnosisResult = result
                                } catch (e: Exception) {
                                    errorMessage = "舌象分析失败: ${e.message}"
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAnalyzing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("开始舌诊", style = MaterialTheme.typography.titleLarge)
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
private fun TongueDiagnosisResultCard(result: TongueDiagnosisResult) {
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
                text = "舌诊结果",
                style = MaterialTheme.typography.headlineMedium,
                color = Green40,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            DiagnosisLabel(label = "舌色", value = result.tongueColor)
            DiagnosisLabel(label = "苔色", value = result.coatingColor)
            DiagnosisLabel(label = "苔厚", value = result.coatingThickness)
            DiagnosisLabel(label = "苔质", value = result.coatingMoisture)
            DiagnosisLabel(label = "舌形", value = result.tongueShape)
            DiagnosisLabel(label = "置信度", value = "${(result.confidence * 100).toInt()}%")

            // 中医解读
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "中医解读",
                style = MaterialTheme.typography.titleLarge,
                color = Warm40,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = interpretTongue(result),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TongueErrorCard(message: String, onDismiss: () -> Unit) {
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

/**
 * 中医舌诊解读
 */
private fun interpretTongue(result: TongueDiagnosisResult): String {
    val sb = StringBuilder()

    when (result.tongueColor) {
        "淡白" -> sb.append("舌色淡白，多见于气血两虚或阳虚。建议注意补气养血。\n")
        "淡红" -> sb.append("舌色淡红，为正常舌色，气血调和。\n")
        "红" -> sb.append("舌色偏红，提示体内有热。建议清淡饮食，避免辛辣。\n")
        "红绛" -> sb.append("舌色红绛，为热入营血之象，建议及时就医。\n")
        "紫暗" -> sb.append("舌色紫暗，多为血瘀之象，建议活血化瘀。\n")
    }

    when (result.coatingColor) {
        "白" -> sb.append("白苔属表证、寒证。")
        "黄" -> sb.append("黄苔属里证、热证。")
        "灰" -> sb.append("灰苔多见于里热证或寒湿证。")
        "黄白" -> sb.append("黄白相兼苔，表邪入里化热。")
    }

    when (result.coatingThickness) {
        "厚" -> sb.append("苔厚提示邪气较盛，病位较深。")
        "薄" -> sb.append("苔薄为正常或病邪轻浅。")
    }

    if (sb.isEmpty()) sb.append("舌象基本正常，请保持良好生活习惯。")
    return sb.toString()
}

// ViewGroup 引用
private typealias ViewGroup = android.view.ViewGroup