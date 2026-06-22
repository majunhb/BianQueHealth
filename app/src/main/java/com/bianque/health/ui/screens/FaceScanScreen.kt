package com.bianque.health.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import android.media.MediaActionSound
import android.speech.tts.TextToSpeech
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.*

private val OutlineGray = Color(0xFFCCCCCC)
private val OutlineYellow = Color(0xFFFFCC00)
private val OutlineGreen = Color(0xFF00CC66)
private val HaloCyan = Color(0xFF00D4FF)
private val DotGlow = Color(0xFF60EFFF)

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

                    // 圆形扫描叠加层（圆形框 + 光晕 + 关键穴位闪烁点）
                    CircularScanOverlay(
                        detectionState = uiState.detectionState,
                        isScanning = uiState.isScanning,
                        imageWidth = uiState.imageWidth,
                        imageHeight = uiState.imageHeight,
                        contourPoints = uiState.faceContourPoints
                    )

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

                    // 隐私保护标识
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 36.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Green40.copy(alpha = 0.85f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔒", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.privacy_badge_local),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
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

// ===================================================================
// 圆形扫描叠加层 — 圆形框 + 旋转光晕 + 关键穴位闪烁点
// ===================================================================

/**
 * TCM 面诊关键穴位定义。
 * 每个穴位在圆形区域内的相对位置（以圆心为中心，半径为单位）。
 */
private data class KeyPoint(
    val name: String,       // 穴位名称
    val organ: String,      // 对应脏腑
    val relX: Float,        // 相对圆心 X（-1..1）
    val relY: Float,        // 相对圆心 Y（-1..1）
    val dotSize: Float = 7f // 点大小
)

private val KEY_POINTS = listOf(
    KeyPoint("额头", "心", 0f, -0.72f, 8f),
    KeyPoint("印堂", "肺", 0f, -0.42f, 7f),
    KeyPoint("左太阳穴", "肝", -0.52f, -0.38f, 6f),
    KeyPoint("右太阳穴", "肺", 0.52f, -0.38f, 6f),
    KeyPoint("鼻梁", "脾", 0f, -0.06f, 7f),
    KeyPoint("左颧骨", "肝", -0.40f, 0.10f, 8f),
    KeyPoint("右颧骨", "肺", 0.40f, 0.10f, 8f),
    KeyPoint("左嘴角", "脾胃", -0.28f, 0.42f, 6f),
    KeyPoint("右嘴角", "脾胃", 0.28f, 0.42f, 6f),
    KeyPoint("下巴", "肾", 0f, 0.62f, 7f)
)

@Composable
private fun CircularScanOverlay(
    detectionState: DetectionState,
    isScanning: Boolean,
    imageWidth: Int,
    imageHeight: Int,
    contourPoints: List<PointF>
) {
    val accentColor = when (detectionState) {
        DetectionState.NOT_DETECTED -> OutlineGray
        DetectionState.POOR_QUALITY -> OutlineYellow
        DetectionState.READY -> OutlineGreen
    }

    // 旋转角度动画
    val rotationAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        rotationAnim.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    // 扫描进度动画（点位依次亮起）
    val scanProgress = remember { Animatable(0f) }
    LaunchedEffect(isScanning) {
        if (isScanning) {
            scanProgress.snapTo(0f)
            scanProgress.animateTo(1f, tween(2000, easing = LinearEasing))
        } else {
            scanProgress.snapTo(0f)
        }
    }

    // 呼吸脉冲动画
    val breatheAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        breatheAnim.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    val hasFace = contourPoints.isNotEmpty() && imageWidth > 0 && imageHeight > 0

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height
        val cx = canvasW / 2f
        val cy = canvasH * 0.46f
        val radius = min(canvasW, canvasH) * 0.38f
        val rotation = rotationAnim.value
        val breathe = breatheAnim.value
        val progress = scanProgress.value

        // 计算人脸关键点实际屏幕坐标（如有检测到人脸）
        val faceDots: Map<String, Offset> = if (hasFace) {
            computeFaceKeyPoints(contourPoints, imageWidth, imageHeight, canvasW, canvasH)
        } else emptyMap()

        // === 第1层：暗角遮罩（圆形外部变暗） ===
        drawCircleHoleMask(cx, cy, radius * 1.08f, Color.Black.copy(alpha = 0.45f))

        // === 第2层：旋转光晕轨道 ===
        drawRotatingHalo(cx, cy, radius, rotation, accentColor, isScanning)

        // === 第3层：圆形扫描框 ===
        drawCircularFrame(cx, cy, radius, accentColor, isScanning, breathe)

        // === 第4层：关键穴位闪烁点 ===
        KEY_POINTS.forEachIndexed { index, kp ->
            val dotCenter = if (faceDots.containsKey(kp.name)) {
                faceDots[kp.name]!!
            } else {
                Offset(cx + kp.relX * radius, cy + kp.relY * radius)
            }

            val dotProgress = if (isScanning) {
                // 扫描时依次亮起
                val threshold = index.toFloat() / KEY_POINTS.size
                (progress - threshold).coerceIn(0f, 0.15f) / 0.15f
            } else if (detectionState == DetectionState.READY) {
                1f
            } else {
                breathe * 0.6f + 0.4f
            }

            drawKeyPointDot(dotCenter, kp, dotProgress, accentColor, isScanning)
        }

        // === 第5层：面部轮廓线（检测到人脸时） ===
        if (hasFace) {
            drawFaceContour(contourPoints, imageWidth, imageHeight, canvasW, canvasH, accentColor)
        }
    }
}

/**
 * 绘制暗角遮罩（圆形外部区域变暗，突出扫描区）。
 */
private fun DrawScope.drawCircleHoleMask(
    cx: Float, cy: Float, radius: Float, darkColor: Color
) {
    // 绘制四个矩形覆盖圆外区域
    val w = size.width
    val h = size.height
    // 顶部
    drawRect(darkColor, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, max(0f, cy - radius)))
    // 底部
    drawRect(darkColor, topLeft = Offset(0f, cy + radius), size = androidx.compose.ui.geometry.Size(w, max(0f, h - cy - radius)))
    // 左侧
    drawRect(darkColor, topLeft = Offset(0f, max(0f, cy - radius)),
        size = androidx.compose.ui.geometry.Size(max(0f, cx - radius), (cy + radius).coerceAtMost(h) - max(0f, cy - radius)))
    // 右侧
    drawRect(darkColor, topLeft = Offset(cx + radius, max(0f, cy - radius)),
        size = androidx.compose.ui.geometry.Size(max(0f, w - cx - radius), (cy + radius).coerceAtMost(h) - max(0f, cy - radius)))
}

/**
 * 绘制旋转光晕轨道。
 */
private fun DrawScope.drawRotatingHalo(
    cx: Float, cy: Float, radius: Float,
    rotation: Float, accentColor: Color, isScanning: Boolean
) {
    if (!isScanning) return

    // 主光环 — 渐变旋转弧
    val arcCount = 3
    for (i in 0 until arcCount) {
        val arcRotation = rotation + i * 120f
        val arcAlpha = 0.25f - i * 0.06f
        val arcRadius = radius + 2f + i * 3f

        drawArc(
            color = HaloCyan.copy(alpha = arcAlpha),
            startAngle = arcRotation,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - arcRadius, cy - arcRadius),
            size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
            style = Stroke(width = 2.5f - i * 0.5f)
        )
    }

    // 粒子点沿轨道旋转
    val particleCount = 8
    for (i in 0 until particleCount) {
        val angle = Math.toRadians((rotation + i * 45f).toDouble())
        val px = cx + (radius + 8f) * cos(angle).toFloat()
        val py = cy + (radius + 8f) * sin(angle).toFloat()
        val particleAlpha = 0.5f + 0.3f * sin(Math.toRadians((rotation * 2 + i * 72f).toDouble())).toFloat()
        drawCircle(
            color = HaloCyan.copy(alpha = particleAlpha.coerceIn(0f, 1f)),
            radius = 2.5f,
            center = Offset(px, py)
        )
    }
}

/**
 * 绘制圆形扫描框 — 多层光环。
 */
private fun DrawScope.drawCircularFrame(
    cx: Float, cy: Float, radius: Float,
    accentColor: Color, isScanning: Boolean, breathe: Float
) {
    val frameAlpha = if (isScanning) 0.9f else 0.65f + breathe * 0.15f

    // 外圈 — 主扫描框
    drawCircle(
        color = accentColor.copy(alpha = frameAlpha),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 3f)
    )

    // 内圈 — 辅助线
    drawCircle(
        color = accentColor.copy(alpha = frameAlpha * 0.4f),
        radius = radius - 6f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f)
    )

    // 四角刻度标记
    val tickLen = radius * 0.06f
    val tickColor = accentColor.copy(alpha = frameAlpha * 0.7f)
    val angles = listOf(0f, 90f, 180f, 270f)
    for (angleDeg in angles) {
        val a = Math.toRadians(angleDeg.toDouble())
        val innerR = radius - tickLen
        val outerR = radius + tickLen
        drawLine(
            color = tickColor,
            start = Offset(cx + innerR * cos(a).toFloat(), cy + innerR * sin(a).toFloat()),
            end = Offset(cx + outerR * cos(a).toFloat(), cy + outerR * sin(a).toFloat()),
            strokeWidth = 2f
        )
    }
}

/**
 * 绘制单个关键穴位闪烁点。
 */
private fun DrawScope.drawKeyPointDot(
    center: Offset, kp: KeyPoint, progress: Float,
    accentColor: Color, isScanning: Boolean
) {
    if (progress <= 0f) return

    val dotAlpha = progress.coerceIn(0f, 1f)
    val dotColor = when {
        isScanning && progress >= 0.8f -> HaloCyan
        progress >= 0.5f -> accentColor
        else -> accentColor.copy(alpha = 0.3f)
    }

    // 外光晕
    val glowRadius = kp.dotSize * 2.2f * dotAlpha
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                DotGlow.copy(alpha = dotAlpha * 0.6f),
                DotGlow.copy(alpha = 0f)
            ),
            center = center,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = center
    )

    // 主点
    drawCircle(
        color = dotColor.copy(alpha = dotAlpha),
        radius = kp.dotSize * dotAlpha,
        center = center
    )

    // 内亮点
    drawCircle(
        color = Color.White.copy(alpha = dotAlpha * 0.8f),
        radius = kp.dotSize * 0.35f * dotAlpha,
        center = center
    )
}

/**
 * 基于 ML Kit 轮廓点计算关键穴位在屏幕上的坐标。
 */
private fun computeFaceKeyPoints(
    contourPoints: List<PointF>,
    imageWidth: Int, imageHeight: Int,
    canvasW: Float, canvasH: Float
): Map<String, Offset> {
    if (contourPoints.size < 20) return emptyMap()

    val scaleX = canvasW / imageWidth.toFloat()
    val scaleY = canvasH / imageHeight.toFloat()
    val scale = max(scaleX, scaleY)
    val displayW = imageWidth * scale
    val displayH = imageHeight * scale
    val offsetX = (canvasW - displayW) / 2f
    val offsetY = (canvasH - displayH) / 2f

    fun toScreen(p: PointF): Offset {
        val sx = canvasW - (p.x * scale + offsetX)
        val sy = p.y * scale + offsetY
        return Offset(sx, sy)
    }

    // ML Kit 36点轮廓：索引0-9为额头/太阳穴，10-16为下颌线，17-25为右侧，26-35为左侧
    // 简化映射：取轮廓点中的关键位置
    val result = mutableMapOf<String, Offset>()

    try {
        // 额头：取顶部几个点的中心
        val topPoints = contourPoints.take(5)
        if (topPoints.isNotEmpty()) {
            val avgX = topPoints.map { it.x }.average().toFloat()
            val avgY = topPoints.map { it.y }.average().toFloat()
            result["额头"] = toScreen(PointF(avgX, avgY - 10f))
        }

        // 下巴：取最底部点
        val bottomPoint = contourPoints.maxByOrNull { it.y }
        if (bottomPoint != null) result["下巴"] = toScreen(bottomPoint)

        // 左/右太阳穴：侧面点
        if (contourPoints.size > 30) {
            result["左太阳穴"] = toScreen(contourPoints[28])
            result["右太阳穴"] = toScreen(contourPoints[18])
        }

        // 左/右颧骨：取中部偏下点
        if (contourPoints.size > 24) {
            result["左颧骨"] = toScreen(contourPoints[26])
            result["右颧骨"] = toScreen(contourPoints[20])
        }

        // 其他点：基于轮廓估算
        val faceCenter = PointF(
            contourPoints.map { it.x }.average().toFloat(),
            contourPoints.map { it.y }.average().toFloat()
        )
        val faceHeight = (bottomPoint?.y ?: contourPoints.last().y) - contourPoints.first().y
        val faceWidth = contourPoints.maxOf { it.x } - contourPoints.minOf { it.x }

        result["印堂"] = toScreen(PointF(faceCenter.x, contourPoints.first().y + faceHeight * 0.22f))
        result["鼻梁"] = toScreen(PointF(faceCenter.x, contourPoints.first().y + faceHeight * 0.48f))
        result["左嘴角"] = toScreen(PointF(faceCenter.x - faceWidth * 0.15f, contourPoints.first().y + faceHeight * 0.70f))
        result["右嘴角"] = toScreen(PointF(faceCenter.x + faceWidth * 0.15f, contourPoints.first().y + faceHeight * 0.70f))
    } catch (_: Exception) { }

    return result
}

/**
 * 绘制检测到人脸时的面部轮廓线。
 */
private fun DrawScope.drawFaceContour(
    contourPoints: List<PointF>,
    imageWidth: Int, imageHeight: Int,
    canvasW: Float, canvasH: Float,
    accentColor: Color
) {
    val scaleX = canvasW / imageWidth.toFloat()
    val scaleY = canvasH / imageHeight.toFloat()
    val scale = max(scaleX, scaleY)
    val displayW = imageWidth * scale
    val displayH = imageHeight * scale
    val offsetX = (canvasW - displayW) / 2f
    val offsetY = (canvasH - displayH) / 2f

    val facePath = Path()
    contourPoints.forEachIndexed { index, point ->
        val sx = canvasW - (point.x * scale + offsetX)
        val sy = point.y * scale + offsetY
        if (index == 0) facePath.moveTo(sx, sy) else facePath.lineTo(sx, sy)
    }
    facePath.close()

    drawPath(facePath, accentColor.copy(alpha = 0.35f), style = Stroke(width = 2.5f))
    drawPath(facePath, accentColor.copy(alpha = 0.15f), style = Stroke(width = 1.2f))
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