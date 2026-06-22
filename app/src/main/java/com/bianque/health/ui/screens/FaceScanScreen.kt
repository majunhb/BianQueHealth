package com.bianque.health.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.media.MediaActionSound
import android.speech.tts.TextToSpeech
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max

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
    val context = LocalContext.current

    // 语音播报
    val tts = remember {
        TextToSpeech(context, null).apply {
            language = Locale.CHINESE
        }
    }
    var lastSpokenMsg by remember { mutableStateOf("") }
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: ""
        if (msg.isNotEmpty() && msg != lastSpokenMsg && tts.isSpeaking.not()) {
            lastSpokenMsg = msg
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "face_tts")
        }
    }

    // 快门音效
    val shutterSound = remember { MediaActionSound() }

    // 屏幕补光：进入面诊时自动调高亮度，退出时恢复
    val window = remember { context.findActivity()?.window }
    val originalBrightness = remember { window?.attributes?.screenBrightness ?: -1f }
    DisposableEffect(Unit) {
        window?.apply {
            // 如果系统支持自动亮度，强制调至最大亮度用于补光
            val attrs = attributes
            if (attrs.screenBrightness < 0.8f) {
                attrs.screenBrightness = 1.0f
                attributes = attrs
            }
        }
        onDispose {
            // 恢复原始亮度
            window?.let { w ->
                if (originalBrightness >= 0f) {
                    val attrs = w.attributes
                    attrs.screenBrightness = originalBrightness
                    w.attributes = attrs
                }
            }
            CameraHelper.unbind()
            tts.stop()
            tts.shutdown()
        }
    }

    // 自动抓拍：当连续处于READY状态1.5秒后自动触发（给中老年用户留足调整时间）
    LaunchedEffect(uiState.detectionState, uiState.isScanning, uiState.isAnalyzing) {
        if (uiState.detectionState == DetectionState.READY
            && !uiState.isScanning
            && !uiState.isAnalyzing
            && uiState.diagnosisResult == null
        ) {
            delay(1500)
            val bitmap = capturedBitmap
            if (bitmap != null) {
                shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                viewModel.autoCapture(bitmap)
            }
        }
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

                    // 人脸轮廓遮罩层（真实面部轮廓线，ML Kit 36点）
                    FaceOutlineOverlay(
                        detectionState = uiState.detectionState,
                        faceRect = uiState.faceRect,
                        imageWidth = uiState.imageWidth,
                        imageHeight = uiState.imageHeight,
                        contourPoints = uiState.faceContourPoints
                    )

                    // 扫描光效动画
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
                            text = uiState.statusMessage ?: "请将面部置于框内，保持正脸",
                            color = outlineColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // 底部引导提示条：光照、距离、姿势
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        GuideTipItem(text = "☀ 明亮光线")
                        GuideTipItem(text = "↔ 一臂之距")
                        GuideTipItem(text = "⟳ 正脸平视")
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

/**
 * 人脸轮廓遮罩 — 使用 ML Kit 36个面部轮廓点绘制真实面部轮廓线。
 *
 * 对标 dlib 68点方案：ML Kit 的 FaceContour.FACE 提供 36 个轮廓点，
 * 覆盖额头、太阳穴、颧骨、下颌线，形成贴合真实人脸形状的闭合曲线。
 * 检测到人脸 → 轮廓线变绿，自动扫描；未检测到 → 轮廓线灰白脉冲。
 */
@Composable
private fun FaceOutlineOverlay(
    detectionState: DetectionState,
    faceRect: Rect?,
    imageWidth: Int,
    imageHeight: Int,
    contourPoints: List<PointF>
) {
    val outlineColor = when (detectionState) {
        DetectionState.NOT_DETECTED -> OutlineGray
        DetectionState.POOR_QUALITY -> OutlineYellow
        DetectionState.READY -> OutlineGreen
    }

    val pulseAlpha = remember { Animatable(0.3f) }
    LaunchedEffect(detectionState) {
        if (detectionState == DetectionState.NOT_DETECTED) {
            pulseAlpha.animateTo(0.5f, tween(1200))
            pulseAlpha.animateTo(0.2f, tween(1200))
        }
    }
    val currentAlpha = if (detectionState == DetectionState.NOT_DETECTED) pulseAlpha.value else 0.3f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        if (contourPoints.isNotEmpty() && imageWidth > 0 && imageHeight > 0) {
            // === 绘制真实面部轮廓线（ML Kit 36点） ===
            // 图像坐标 → 屏幕坐标映射（FILL_CENTER + 前置摄像头镜像）
            val scaleX = canvasWidth / imageWidth.toFloat()
            val scaleY = canvasHeight / imageHeight.toFloat()
            val scale = max(scaleX, scaleY)
            val displayW = imageWidth * scale
            val displayH = imageHeight * scale
            val offsetX = (canvasWidth - displayW) / 2f
            val offsetY = (canvasHeight - displayH) / 2f

            val facePath = Path()
            contourPoints.forEachIndexed { index, point ->
                // 前置摄像头水平镜像：screenX = canvasWidth - (imageX * scale + offsetX)
                val sx = canvasWidth - (point.x * scale + offsetX)
                val sy = point.y * scale + offsetY
                if (index == 0) {
                    facePath.moveTo(sx, sy)
                } else {
                    facePath.lineTo(sx, sy)
                }
            }
            facePath.close()

            // 外层轮廓线 4px
            drawPath(
                path = facePath,
                color = outlineColor.copy(alpha = currentAlpha),
                style = Stroke(width = 4f)
            )
            // 内层轮廓线 2px，半透明
            drawPath(
                path = facePath,
                color = outlineColor.copy(alpha = currentAlpha * 0.4f),
                style = Stroke(width = 2f)
            )
        } else {
            // === 未检测到人脸：绘制默认引导椭圆 ===
            val ovalWidth = canvasWidth * 0.52f
            val ovalHeight = canvasHeight * 0.62f
            val ovalLeft = (canvasWidth - ovalWidth) / 2f
            val ovalTop = (canvasHeight - ovalHeight) / 2f

            drawOval(
                color = outlineColor.copy(alpha = currentAlpha),
                topLeft = Offset(ovalLeft, ovalTop),
                size = androidx.compose.ui.geometry.Size(ovalWidth, ovalHeight),
                style = Stroke(width = 4f)
            )
            val inset = 10f
            drawOval(
                color = outlineColor.copy(alpha = currentAlpha * 0.4f),
                topLeft = Offset(ovalLeft + inset, ovalTop + inset),
                size = androidx.compose.ui.geometry.Size(ovalWidth - inset * 2f, ovalHeight - inset * 2f),
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
private fun FaceScanAnimation() {
    val scanProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scanProgress.animateTo(1f, tween(2000, easing = LinearEasing))
    }
    val progress = scanProgress.value

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxRadius = size.width * 0.7f

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
            Text(stringResource(R.string.face_result_title), style = MaterialTheme.typography.headlineMedium, color = Green40, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            DiagnosisLabel(label = stringResource(R.string.label_complexion), value = result.overallComplexion)
            DiagnosisLabel(label = stringResource(R.string.label_gloss), value = "${(result.glossLevel * 100).toInt()}%")
            DiagnosisLabel(label = stringResource(R.string.label_confidence), value = "${(result.confidence * 100).toInt()}%")
            if (result.regions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.face_region_title), style = MaterialTheme.typography.titleLarge, color = Green40, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                result.regions.forEach { region ->
                    DiagnosisLabel(label = region.name, value = region.color)
                }
            }
            if (result.abnormalities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.label_tips), style = MaterialTheme.typography.titleLarge, color = Danger40, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                result.abnormalities.forEach { issue ->
                    Text("• $issue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 2.dp))
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

@Composable
private fun GuideTipItem(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 从 Context 中获取当前 Activity */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}