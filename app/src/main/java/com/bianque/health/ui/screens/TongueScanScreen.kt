package com.bianque.health.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
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
import com.bianque.health.base.analysis.ImageQualityAnalyzer.DetectionState
import com.bianque.health.base.camera.CameraHelper
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import com.bianque.health.ui.components.DiagnosisLabel
import com.bianque.health.ui.theme.Danger40
import com.bianque.health.ui.theme.Green40
import com.bianque.health.ui.theme.Warm40
import java.util.Locale

private val OutlineGray = Color(0xFFCCCCCC)
private val OutlineYellow = Color(0xFFFFCC00)
private val OutlineGreen = Color(0xFF00CC66)
private val ShutterOrange = Color(0xFFF4511E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TongueScanScreen(
    onBack: () -> Unit,
    viewModel: TongueScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val tts = remember {
        TextToSpeech(context, null).apply { language = Locale.CHINESE }
    }
    var lastSpokenMsg by remember { mutableStateOf("") }
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: ""
        if (msg.isNotEmpty() && msg != lastSpokenMsg && tts.isSpeaking.not()) {
            lastSpokenMsg = msg
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "tongue_tts")
        }
    }

    val shutterSound = remember { MediaActionSound() }

    DisposableEffect(Unit) {
        onDispose {
            CameraHelper.unbind()
            tts.stop()
            tts.shutdown()
        }
    }

    // 自动抓拍
    var captureTriggered by remember { mutableStateOf(false) }
    if (uiState.detectionState != DetectionState.READY) { captureTriggered = false }
    LaunchedEffect(uiState.detectionState, uiState.isScanning, uiState.isAnalyzing) {
        if (uiState.detectionState == DetectionState.READY
            && !uiState.isScanning && !uiState.isAnalyzing
            && uiState.diagnosisResult == null && !captureTriggered
        ) {
            captureTriggered = true
            val snapshot = capturedBitmap
            if (snapshot != null) {
                shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                viewModel.autoCapture(snapshot)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tongue_scan_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { CameraHelper.unbind(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E)),
                actions = {
                    IconButton(onClick = { /* 相册导入 */ }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "相册", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.diagnosisResult == null && uiState.errorMessage == null) {
                // ── 拍摄区域 ──
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // 深色背景底
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color(0xFF1A1A2E))
                    )

                    // 相机预览
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

                    // 暗色遮罩层（半透明覆层）
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val guideW = w * 0.55f
                        val guideH = h * 0.65f
                        val guideLeft = (w - guideW) / 2f
                        val guideTop = (h - guideH) / 2f - h * 0.03f
                        val guideRight = guideLeft + guideW
                        val guideBottom = guideTop + guideH
                        val radius = 40f

                        // 四周暗色遮罩（4个矩形覆盖引导框之外的区域）
                        val dimColor = Color.Black.copy(alpha = 0.6f)
                        // 上
                        drawRect(dimColor, Offset.Zero, Size(w, guideTop))
                        // 下
                        drawRect(dimColor, Offset(0f, guideBottom), Size(w, h - guideBottom))
                        // 左
                        drawRect(dimColor, Offset(0f, guideTop), Size(guideLeft, guideH))
                        // 右
                        drawRect(dimColor, Offset(guideRight, guideTop), Size(w - guideRight, guideH))

                        // 引导框（外框 + 内填充模拟轮廓效果）
                        val frameColor = when (uiState.detectionState) {
                            DetectionState.NOT_DETECTED -> Color.White.copy(alpha = 0.4f)
                            DetectionState.POOR_QUALITY -> OutlineYellow
                            DetectionState.READY -> OutlineGreen
                        }
                        drawRoundRect(
                            frameColor.copy(alpha = 0.25f),
                            guideLeft, guideTop, guideRight - guideLeft, guideBottom - guideTop,
                            radius
                        )
                        // 内框
                        drawRoundRect(
                            frameColor.copy(alpha = 0.12f),
                            guideLeft + 6f, guideTop + 6f,
                            guideRight - guideLeft - 12f, guideBottom - guideTop - 12f,
                            radius - 6f
                        )

                        // 四角标记
                        val cornerLen = 25f
                        val cornerStroke = 3f
                        fun drawCornerMark(cx: Float, cy: Float, dx: Float, dy: Float) {
                            drawLine(frameColor, Offset(cx, cy), Offset(cx + dx, cy), cornerStroke)
                            drawLine(frameColor, Offset(cx, cy), Offset(cx, cy + dy), cornerStroke)
                        }
                        drawCornerMark(guideLeft + 8f, guideTop + 8f, cornerLen, cornerLen)
                        drawCornerMark(guideRight - 8f, guideTop + 8f, -cornerLen, cornerLen)
                        drawCornerMark(guideLeft + 8f, guideBottom - 8f, cornerLen, -cornerLen)
                        drawCornerMark(guideRight - 8f, guideBottom - 8f, -cornerLen, -cornerLen)
                    }

                    // 顶部引导文字
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "请拍摄舌面",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            uiState.statusMessage ?: "请将舌头伸出，对齐引导框",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }

                    // 舌体轮廓剪影
                    TongueSilhouette()

                    // 扫描动画
                    if (uiState.isScanning) {
                        TongueScanAnimation()
                    }

                    // 隐私标识
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "🔒 数据本地处理，不上传云端",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }

                // ── 底部操作栏 ──
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF1A1A2E))
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 翻转摄像头
                    IconButton(
                        onClick = { /* 翻转摄像头 */ },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.Default.FlipCameraAndroid,
                            contentDescription = "翻转",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // 橙色快门按钮
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = ShutterOrange,
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                modifier = Modifier.size(62.dp),
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "拍照",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 占位（保持快门居中）
                    Spacer(Modifier.size(48.dp))
                }
            } else if (uiState.diagnosisResult != null) {
                TongueDiagnosisResultCard(result = uiState.diagnosisResult!!)
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Warm40)
                    ) {
                        Text(stringResource(R.string.retry), style = MaterialTheme.typography.titleLarge)
                    }
                }
            } else if (uiState.errorMessage != null) {
                TongueErrorCard(message = uiState.errorMessage!!) { viewModel.clearError() }
            }

            if (uiState.isAnalyzing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.analyzing_tongue), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

// ─── 舌体剪影 ──────────────────────────────────────────────────
@Composable
private fun TongueSilhouette() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.42f

        // 简易人脸轮廓剪影
        val facePath = Path().apply {
            // 头部轮廓
            addOval(androidx.compose.ui.geometry.Rect(
                cx - w * 0.18f, cy - h * 0.22f,
                cx + w * 0.18f, cy + h * 0.18f
            ))
        }
        drawPath(facePath, Color.White.copy(alpha = 0.06f))

        // 口腔区域（张嘴示意）
        val mouthPath = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(
                cx - w * 0.08f, cy + h * 0.06f,
                cx + w * 0.08f, cy + h * 0.14f
            ))
        }
        drawPath(mouthPath, Color.White.copy(alpha = 0.08f))

        // 舌体示意（从口腔伸出）
        val tonguePath = Path().apply {
            moveTo(cx - w * 0.05f, cy + h * 0.10f)
            lineTo(cx - w * 0.04f, cy + h * 0.28f)
            quadraticBezierTo(cx, cy + h * 0.32f, cx + w * 0.04f, cy + h * 0.28f)
            lineTo(cx + w * 0.05f, cy + h * 0.10f)
            close()
        }
        drawPath(tonguePath, Color.White.copy(alpha = 0.07f))
    }
}

// ─── 扫描动画 ──────────────────────────────────────────────────
@Composable
private fun TongueScanAnimation() {
    val scanProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scanProgress.animateTo(1f, tween(1500, easing = LinearEasing)) }
    val scanY = scanProgress.value

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val barH = h * 0.03f
        val barY = (h * 0.15f) + (h * 0.7f) * scanY

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF4FC3F7).copy(alpha = 0f),
                    Color(0xFF4FC3F7).copy(alpha = 0.5f),
                    Color(0xFF4FC3F7).copy(alpha = 0f)
                ),
                startY = barY - barH, endY = barY + barH
            ),
            topLeft = Offset(0f, barY - barH),
            size = Size(w, barH * 2f)
        )
    }
}

// ─── 诊断结果卡片 ──────────────────────────────────────────────
@Composable
private fun TongueDiagnosisResultCard(result: TongueDiagnosisResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.tongue_result_title), style = MaterialTheme.typography.headlineMedium, color = Green40, fontWeight = FontWeight.Bold)
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
            Text(stringResource(R.string.tcm_interpretation), style = MaterialTheme.typography.titleLarge, color = Warm40, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(interpretTongue(result), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
        "正常" -> sb.append("舌下络脉正常，气血运行通畅。")
    }
    when (result.tongueMobility) {
        "歪斜" -> sb.append("舌体歪斜，提示中风或中风先兆，建议及时就医排查。")
        "僵硬" -> sb.append("舌体僵硬，运动不灵活，多见于热入心包或风痰阻络。")
        "灵活" -> sb.append("舌体灵活，运动自如。")
    }
    if (sb.isEmpty()) sb.append("舌象基本正常，请保持良好生活习惯。")
    return sb.toString()
}