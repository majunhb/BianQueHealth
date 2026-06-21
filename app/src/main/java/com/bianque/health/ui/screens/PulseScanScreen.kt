package com.bianque.health.ui.screens

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bianque.health.R
import com.bianque.health.base.camera.CameraHelper
import com.bianque.health.ui.theme.Blue40
import com.bianque.health.ui.theme.Danger40
import com.bianque.health.ui.theme.Warm40

private val OutlineBlue = Color(0xFF007AFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseScanScreen(
    onBack: () -> Unit,
    viewModel: PulseScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        onDispose { CameraHelper.unbind() }
    }

    // 进入页面自动开始采集
    LaunchedEffect(Unit) {
        viewModel.startCapture()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pulse_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        CameraHelper.unbind()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 相机预览 + 状态提示
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.diagnosisResult == null && uiState.errorMessage == null) {
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
                                        onFrame = { bitmap ->
                                            viewModel.processFrame(bitmap)
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 人脸椭圆轮廓
                    FaceGuideOverlay()

                    // 状态信息卡片
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .fillMaxWidth(0.9f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                uiState.statusMessage,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    stringResource(R.string.pulse_frame_count, uiState.frameCount),
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    stringResource(R.string.pulse_time_remaining, uiState.timeoutSeconds),
                                    color = if (uiState.timeoutSeconds <= 10) Danger40 else Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (uiState.timeoutSeconds <= 10) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            // 进度条
                            if (uiState.isCapturing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (uiState.frameCount.toFloat() / uiState.maxFrames).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = OutlineBlue,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                } else if (uiState.diagnosisResult != null) {
                    PulseResultCard(result = uiState.diagnosisResult!!)
                } else if (uiState.errorMessage != null) {
                    PulseErrorCard(
                        message = uiState.errorMessage!!,
                        onDismiss = { viewModel.clearError() }
                    )
                }

                // 分析中遮罩
                if (uiState.isAnalyzing) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.pulse_analyzing), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // 底部免责声明
            if (uiState.isCapturing || uiState.diagnosisResult != null) {
                Text(
                    stringResource(R.string.pulse_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 底部按钮
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (uiState.diagnosisResult != null) {
                    Button(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Warm40)
                    ) {
                        Text(stringResource(R.string.retry), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceGuideOverlay() {
    val pulseAlpha = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        pulseAlpha.animateTo(0.35f, tween(1500))
        pulseAlpha.animateTo(0.2f, tween(1500))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val ovalWidth = canvasWidth * 0.52f
        val ovalHeight = canvasHeight * 0.62f
        val topLeft = Offset(
            (canvasWidth - ovalWidth) / 2f,
            (canvasHeight - ovalHeight) / 2f
        )

        drawOval(
            color = OutlineBlue.copy(alpha = pulseAlpha.value),
            topLeft = topLeft,
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 3f)
        )
    }
}

@Composable
private fun PulseResultCard(result: com.bianque.health.pulse.domain.model.PulseDiagnosisResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.pulse_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Blue40,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 心率
            Text(
                "${result.pulseRate}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                fontWeight = FontWeight.Bold,
                color = Blue40
            )
            Text(
                stringResource(R.string.bpm_unit),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 血压
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.pulse_sbp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${result.systolic}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Danger40
                    )
                    Text(stringResource(R.string.mmhg_unit), style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.pulse_dbp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${result.diastolic}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Blue40
                    )
                    Text(stringResource(R.string.mmhg_unit), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 脉象
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.label_pulse_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(result.pulseType, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Blue40)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.label_pulse_strength), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(result.pulseStrength, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Blue40)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.pulse_confidence, (result.confidence * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PulseErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Danger40.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(stringResource(R.string.label_tips), style = MaterialTheme.typography.headlineMedium, color = Danger40)
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}