package com.bianque.health.ui.screens

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bianque.health.R
import com.bianque.health.base.analysis.ImageQualityAnalyzer.DetectionState
import com.bianque.health.base.camera.CameraHelper
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import com.bianque.health.ui.components.DiagnosisLabel
import com.bianque.health.ui.theme.Danger40
import com.bianque.health.ui.theme.Green40
import com.bianque.health.ui.theme.Warm40

// 设计文档定义的颜色
private val OutlineBlue = Color(0xFF007AFF)
private val OutlineGray = Color(0xFFCCCCCC)
private val OutlineYellow = Color(0xFFFFCC00)
private val OutlineGreen = Color(0xFF00CC66)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TongueScanScreen(
    onBack: () -> Unit,
    viewModel: TongueScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        onDispose { CameraHelper.unbind() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tongue_scan_title)) },
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 相机预览区域
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.diagnosisResult == null && uiState.errorMessage == null) {
                    // 相机预览层
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
                                            capturedBitmap = bitmap
                                            viewModel.analyzeFrame(bitmap)
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 舌形轮廓遮罩层
                    TongueOutlineOverlay(detectionState = uiState.detectionState)

                    // 扫描光效动画
                    if (uiState.isScanning) {
                        TongueScanAnimation()
                    }

                    // 引导文字
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val outlineColor = when (uiState.detectionState) {
                            DetectionState.NOT_DETECTED -> OutlineGray
                            DetectionState.POOR_QUALITY -> OutlineYellow
                            DetectionState.READY -> OutlineGreen
                        }
                        Text(
                            text = uiState.statusMessage ?: "请张嘴伸舌，对齐轮廓线",
                            color = outlineColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else if (uiState.diagnosisResult != null) {
                    TongueDiagnosisResultCard(result = uiState.diagnosisResult!!)
                } else if (uiState.errorMessage != null) {
                    TongueErrorCard(message = uiState.errorMessage!!) { viewModel.clearError() }
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
                            Text(
                                stringResource(R.string.analyzing_tongue),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // 底部按钮
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (uiState.diagnosisResult == null) {
                    Button(
                        onClick = {
                            val bitmap = capturedBitmap
                            if (bitmap == null) return@Button
                            viewModel.startScan(bitmap)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isAnalyzing && !uiState.isScanning,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (uiState.isScanning) stringResource(R.string.scanning)
                            else stringResource(R.string.start_tongue_scan),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                } else {
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

/**
 * 舌形轮廓遮罩 — 科技感半透明轮廓线。
 * 颜色根据检测状态变化：灰色(未检测到) → 黄色(质量不佳) → 绿色(对焦成功)
 */
@Composable
private fun TongueOutlineOverlay(detectionState: DetectionState) {
    val outlineColor = when (detectionState) {
        DetectionState.NOT_DETECTED -> OutlineGray
        DetectionState.POOR_QUALITY -> OutlineYellow
        DetectionState.READY -> OutlineGreen
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 绘制舌形椭圆轮廓（竖直方向，模拟张嘴伸舌）
        val ovalWidth = canvasWidth * 0.4f
        val ovalHeight = canvasHeight * 0.55f
        val topLeft = Offset(
            (canvasWidth - ovalWidth) / 2f,
            (canvasHeight - ovalHeight) / 2f
        )

        // 外轮廓
        drawOval(
            color = outlineColor.copy(alpha = 0.3f),
            topLeft = topLeft,
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 4f)
        )

        // 内轮廓（虚线效果，通过缩小+描边模拟）
        drawOval(
            color = outlineColor.copy(alpha = 0.15f),
            topLeft = Offset(topLeft.x + 8f, topLeft.y + 8f),
            size = Size(ovalWidth - 16f, ovalHeight - 16f),
            style = Stroke(width = 2f)
        )
    }
}

/**
 * 舌诊扫描光效 — 从上至下移动的发光条。
 * 动画持续 1.5 秒，线性渐变光带。
 */
@Composable
private fun TongueScanAnimation() {
    val scanProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scanProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
        )
    }

    val scanY = scanProgress.value

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 扫描光带位置
        val barHeight = canvasHeight * 0.04f
        val barY = (canvasHeight * 0.15f) + (canvasHeight * 0.7f) * scanY

        // 水平渐变光带
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    OutlineBlue.copy(alpha = 0f),
                    OutlineBlue.copy(alpha = 0.6f),
                    OutlineBlue.copy(alpha = 0f)
                ),
                startY = barY - barHeight,
                endY = barY + barHeight
            ),
            topLeft = Offset(0f, barY - barHeight),
            size = Size(canvasWidth, barHeight * 2f)
        )
    }
}

@Composable
private fun TongueDiagnosisResultCard(result: TongueDiagnosisResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.tongue_result_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Green40,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            DiagnosisLabel(label = stringResource(R.string.label_tongue_color), value = result.tongueColor)
            DiagnosisLabel(label = stringResource(R.string.label_coating_color), value = result.coatingColor)
            DiagnosisLabel(label = stringResource(R.string.label_coating_thickness), value = result.coatingThickness)
            DiagnosisLabel(label = stringResource(R.string.label_coating_moisture), value = result.coatingMoisture)
            DiagnosisLabel(label = stringResource(R.string.label_tongue_shape), value = result.tongueShape)
            DiagnosisLabel(label = stringResource(R.string.label_tongue_body), value = result.tongueBody)
            DiagnosisLabel(label = stringResource(R.string.label_sublingual_vein), value = result.sublingualVein)
            DiagnosisLabel(label = stringResource(R.string.label_tongue_mobility), value = result.tongueMobility)
            DiagnosisLabel(label = stringResource(R.string.label_confidence), value = "${(result.confidence * 100).toInt()}%")
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.tcm_interpretation),
                style = MaterialTheme.typography.titleLarge,
                color = Warm40,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                interpretTongue(result),
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
        "黑" -> sb.append("黑苔主里证，或为热极，或为寒极。")
        "黄白" -> sb.append("黄白相兼苔，表邪入里化热。")
    }
    when (result.coatingThickness) {
        "厚" -> sb.append("苔厚提示邪气较盛，病位较深。")
        "薄" -> sb.append("苔薄为正常或病邪轻浅。")
        "腻" -> sb.append("腻苔提示痰湿内蕴。")
    }
    when (result.tongueBody) {
        "老" -> sb.append("舌质苍老，属实证。")
        "嫩" -> sb.append("舌质娇嫩，属虚证。")
    }
    when (result.sublingualVein) {
        "怒张" -> sb.append("舌下络脉怒张，提示血瘀。")
    }
    if (sb.isEmpty()) sb.append("舌象基本正常，请保持良好生活习惯。")
    return sb.toString()
}