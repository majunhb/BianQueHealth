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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.bianque.health.face.domain.model.FaceDiagnosisResult
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
fun FaceScanScreen(
    onBack: () -> Unit,
    viewModel: FaceScanViewModel = hiltViewModel()
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
                title = { Text(stringResource(R.string.face_scan_title)) },
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

                    // 人脸轮廓遮罩层
                    FaceOutlineOverlay(detectionState = uiState.detectionState)

                    // 扫描光效动画（同心圆扩散）
                    if (uiState.isScanning) {
                        FaceScanAnimation()
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
                            text = uiState.statusMessage ?: "请将面部置于框内",
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
                    FaceDiagnosisResultCard(result = uiState.diagnosisResult!!)
                } else if (uiState.errorMessage != null) {
                    FaceErrorCard(message = uiState.errorMessage!!) { viewModel.clearError() }
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
                                "正在分析面色特征…",
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
                            else stringResource(R.string.start_face_scan),
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
 * 人脸轮廓遮罩 — 真实人脸形状轮廓线。
 * 使用上方圆角矩形 + 下方收窄的曲线模拟人脸轮廓：
 * 额头较宽 → 颧骨微收 → 下巴明显收窄。
 * 颜色根据检测状态变化：灰色(未检测到) → 黄色(定位中) → 绿色(定位成功)
 */
@Composable
private fun FaceOutlineOverlay(detectionState: DetectionState) {
    val outlineColor = when (detectionState) {
        DetectionState.NOT_DETECTED -> OutlineGray
        DetectionState.POOR_QUALITY -> OutlineYellow
        DetectionState.READY -> OutlineGreen
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 人脸轮廓参数
        val faceWidth = canvasWidth * 0.52f
        val faceHeight = canvasHeight * 0.62f
        val centerX = canvasWidth / 2f
        val topY = (canvasHeight - faceHeight) / 2f
        val chinWidth = faceWidth * 0.5f  // 下巴宽度约为额头的50%
        val chinY = topY + faceHeight

        // 构建人脸轮廓路径：上方圆角 → 两侧微收 → 下巴收窄
        val path = Path().apply {
            val cheekNarrowY = topY + faceHeight * 0.55f  // 颧骨下方开始收窄
            val jawStartY = topY + faceHeight * 0.72f  // 下颌线开始

            // 额头圆弧（顶部）
            val cornerRadius = faceWidth * 0.18f
            moveTo(centerX - faceWidth / 2f + cornerRadius, topY)
            // 顶部弧线
            lineTo(centerX + faceWidth / 2f - cornerRadius, topY)
            arcTo(
                rect = Rect(
                    centerX + faceWidth / 2f - cornerRadius * 2f,
                    topY,
                    centerX + faceWidth / 2f,
                    topY + cornerRadius * 2f
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            // 右侧：从颧骨下方开始收窄到下巴
            lineTo(centerX + faceWidth / 2f, cheekNarrowY)
            // 右侧下颌线 → 下巴
            lineTo(centerX + chinWidth / 2f, jawStartY)
            lineTo(centerX + chinWidth / 2f, chinY)
            // 下巴底部弧线
            val chinRadius = chinWidth * 0.3f
            arcTo(
                rect = Rect(
                    centerX - chinRadius,
                    chinY - chinRadius * 1.5f,
                    centerX + chinRadius,
                    chinY + chinRadius * 0.5f
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            // 左侧下颌线
            lineTo(centerX - chinWidth / 2f, jawStartY)
            lineTo(centerX - faceWidth / 2f, cheekNarrowY)
            // 左侧回到顶部
            lineTo(centerX - faceWidth / 2f, topY + cornerRadius)
            arcTo(
                rect = Rect(
                    centerX - faceWidth / 2f,
                    topY,
                    centerX - faceWidth / 2f + cornerRadius * 2f,
                    topY + cornerRadius * 2f
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }

        // 外轮廓
        drawPath(
            path = path,
            color = outlineColor.copy(alpha = 0.3f),
            style = Stroke(width = 4f)
        )

        // 内轮廓
        val innerPath = Path().apply {
            val inset = 12f
            val iw = faceWidth - inset * 2f
            val ih = faceHeight - inset * 2f
            val ix = centerX - iw / 2f
            val iy = topY + inset
            val icw = chinWidth * 0.7f

            val icr = iw * 0.18f
            moveTo(ix + icr, iy)
            lineTo(ix + iw - icr, iy)
            arcTo(
                rect = Rect(ix + iw - icr * 2f, iy, ix + iw, iy + icr * 2f),
                startAngleDegrees = -90f, sweepAngleDegrees = 90f, forceMoveTo = false
            )
            lineTo(ix + iw, iy + ih * 0.55f)
            lineTo(centerX + icw / 2f, iy + ih * 0.72f)
            lineTo(centerX + icw / 2f, iy + ih)
            arcTo(
                rect = Rect(centerX - icw * 0.3f, iy + ih - icw * 0.2f, centerX + icw * 0.3f, iy + ih + icw * 0.3f),
                startAngleDegrees = 0f, sweepAngleDegrees = 180f, forceMoveTo = false
            )
            lineTo(centerX - icw / 2f, iy + ih * 0.72f)
            lineTo(ix, iy + ih * 0.55f)
            lineTo(ix, iy + icr)
            arcTo(
                rect = Rect(ix, iy, ix + icr * 2f, iy + icr * 2f),
                startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false
            )
            close()
        }
        drawPath(
            path = innerPath,
            color = outlineColor.copy(alpha = 0.12f),
            style = Stroke(width = 2f)
        )
    }
}

/**
 * 面诊扫描光效 — 从中间向四周扩散的同心圆。
 * 动画持续 2 秒，从中心向外匀速扩散。
 */
@Composable
private fun FaceScanAnimation() {
    val scanProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scanProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
        )
    }

    val progress = scanProgress.value

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxRadius = size.width * 0.7f

        // 绘制多个同心圆，形成扩散效果
        for (i in 0..3) {
            val phase = (progress + i * 0.25f) % 1f
            val radius = maxRadius * phase
            val alpha = (1f - phase) * 0.5f

            drawCircle(
                color = OutlineBlue.copy(alpha = alpha),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 3f)
            )
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
        Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.face_result_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Green40,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            DiagnosisLabel(label = stringResource(R.string.label_complexion), value = result.overallComplexion)
            DiagnosisLabel(label = stringResource(R.string.label_gloss), value = "${(result.glossLevel * 100).toInt()}%")
            DiagnosisLabel(label = stringResource(R.string.label_confidence), value = "${(result.confidence * 100).toInt()}%")
            if (result.regions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.face_region_title),
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
                    stringResource(R.string.label_tips),
                    style = MaterialTheme.typography.titleLarge,
                    color = Danger40,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                result.abnormalities.forEach { issue ->
                    Text(
                        "• $issue",
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
private fun FaceErrorCard(message: String, onDismiss: () -> Unit) {
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