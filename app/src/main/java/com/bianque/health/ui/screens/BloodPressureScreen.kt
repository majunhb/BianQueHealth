package com.bianque.health.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bianque.health.bp.data.BpAlertEngine
import com.bianque.health.bp.data.BpTrendAnalyzer
import com.bianque.health.ui.theme.Green40
import com.bianque.health.ui.theme.Danger40
import com.bianque.health.ui.theme.Warm40
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

private val RppgCyan = Color(0xFF00D4FF)
private val RppgBlue = Color(0xFF3B82F6)
private val RppgGreen = Color(0xFF22C55E)
private val RppgRed = Color(0xFFEF4444)
private val SurfaceDark = Color(0xFF1A1A2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodPressureScreen(
    onBack: () -> Unit,
    viewModel: BloodPressureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    // CameraX 预览
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    // 初始化 CameraX
    LaunchedEffect(uiState.isMeasuring) {
        if (uiState.isMeasuring) {
            val provider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider = provider
        } else {
            cameraProvider?.unbindAll()
            cameraProvider = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                cameraProvider?.unbindAll()
                executor.shutdown()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 绑定 CameraX ImageAnalysis
    LaunchedEffect(cameraProvider, previewView, uiState.isMeasuring) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val pv = previewView ?: return@LaunchedEffect

        if (uiState.isMeasuring) {
            try {
                provider.unbindAll()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                val preview = androidx.camera.core.Preview.Builder().build().apply {
                    setSurfaceProvider(pv.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val bitmap = imageProxyToBitmap(imageProxy)
                            imageProxy.close()
                            bitmap?.let { viewModel.addFrame(it) }
                        }
                    }
                provider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalysis
                )
            } catch (e: Exception) {
                // CameraX 绑定失败，回退无预览模式
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("光感测压", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        cameraProvider?.unbindAll()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // BLE 连接按钮
                    IconButton(onClick = { viewModel.toggleConnection() }) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = "蓝牙",
                            tint = if (uiState.isBleConnected) RppgBlue else Color.Gray
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isMeasuring) {
            // ====== 测量中视图 ======
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // CameraX 预览
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 圆形扫描叠加层
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height * 0.45f
                    val radius = minOf(size.width, size.height) * 0.35f

                    // 暗角遮罩
                    drawCircle(Color.Black.copy(alpha = 0.5f), radius = size.maxDimension,
                        center = Offset(cx, cy))
                    // 圆形透明区域
                    drawCircle(Color.Transparent, radius = radius, center = Offset(cx, cy),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear)

                    // 圆形扫描框
                    drawCircle(RppgCyan.copy(alpha = 0.7f), radius = radius,
                        center = Offset(cx, cy), style = Stroke(width = 3f))
                    drawCircle(RppgCyan.copy(alpha = 0.3f), radius = radius - 4f,
                        center = Offset(cx, cy), style = Stroke(width = 1.5f))
                }

                // 引导提示 + 进度
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 进度条
                    LinearProgressIndicator(
                        progress = { uiState.progress },
                        modifier = Modifier.fillMaxWidth(0.7f).height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = RppgCyan,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        uiState.guideMessage,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        uiState.progressMessage,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.analyzeAndFinish()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RppgRed
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "停止")
                        Spacer(Modifier.width(8.dp))
                        Text("停止测量")
                    }
                }
            }
        } else if (uiState.isAnalyzing) {
            // ====== 分析中视图 ======
            Box(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = RppgCyan,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        uiState.progressMessage,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "正在分析光感信号，请稍候…",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        } else if (uiState.showResults) {
            // ====== 结果展示视图 ======
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .background(SurfaceDark),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 免责声明
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Warm40.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "本功能基于光学 AI 估算，仅供日常健康趋势参考，不可替代传统袖带式血压计，不作为医疗诊断依据。",
                                color = Color(0xFFFFA726),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // 血压数值
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "光感测压结果",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${uiState.systolic}",
                                        fontSize = 56.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text("收缩压", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                                }
                                Text(
                                    " / ",
                                    fontSize = 40.sp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${uiState.diastolic}",
                                        fontSize = 56.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text("舒张压", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "mmHg",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "心率 ${uiState.heartRate} BPM",
                                color = RppgCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 预警结果
                uiState.alertResult?.let { alert ->
                    if (alert.alerts.isNotEmpty()) {
                        item {
                            AlertCard(alert)
                        }
                    }
                }

                // 趋势结果
                uiState.trendResult?.let { trend ->
                    item {
                        TrendCard(trend)
                    }
                }

                // 重新测量按钮
                item {
                    Button(
                        onClick = {
                            viewModel.reset()
                            viewModel.startMeasurement()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RppgCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("重新测量", fontWeight = FontWeight.Bold)
                    }
                }

                // 错误信息
                uiState.errorMessage?.let { msg ->
                    item {
                        Text(msg, color = RppgRed, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            // ====== 初始空闲视图 / 错误提示 ======
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .background(SurfaceDark),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 错误信息（分析失败时展示）
                uiState.errorMessage?.let { msg ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = RppgRed.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("⚠️ 测量失败", color = RppgRed, fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text(msg, color = RppgRed.copy(alpha = 0.8f),
                                    fontSize = 14.sp, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { viewModel.reset() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RppgCyan)
                                ) {
                                    Text("重新测量")
                                }
                            }
                        }
                    }
                }
                // 免责声明
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Warm40.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "本功能基于光学 AI 估算，仅供日常健康趋势参考，不可替代传统袖带式血压计，不作为医疗诊断依据。测量时请保持面部静止，避免强光直射。",
                                color = Color(0xFFFFA726),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // 测量引导
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 圆形引导图标
                            Box(
                                modifier = Modifier.size(80.dp)
                                    .border(2.dp, RppgCyan.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "rPPG",
                                    color = RppgCyan,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "光感测压",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "通过前置摄像头无创测量血压和心率",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                GuideItem("1", "正对摄像头", "保持面部完整")
                                GuideItem("2", "保持静止", "避免头部晃动")
                                GuideItem("3", "充足光线", "避免强光直射")
                            }
                        }
                    }
                }

                // 开始测量按钮
                item {
                    Button(
                        onClick = { viewModel.startMeasurement() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RppgCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("开始测量", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }

                // 历史记录
                if (uiState.history.isNotEmpty()) {
                    item {
                        Text(
                            "历史记录",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(uiState.history.take(10)) { record ->
                        HistoryItem(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideItem(number: String, title: String, desc: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(36.dp).border(1.dp, RppgCyan.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = RppgCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(desc, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
    }
}

@Composable
private fun AlertCard(alert: BpAlertEngine.BpAlertResult) {
    val bgColor = when (alert.overallLevel) {
        BpAlertEngine.AlertLevel.CRITICAL -> RppgRed.copy(alpha = 0.2f)
        BpAlertEngine.AlertLevel.HIGH -> Color(0xFFFF9800).copy(alpha = 0.2f)
        BpAlertEngine.AlertLevel.MEDIUM -> Warm40.copy(alpha = 0.15f)
        else -> RppgGreen.copy(alpha = 0.1f)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                alert.summary,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            if (alert.shouldCallEmergency) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "请立即拨打120急救电话！",
                    color = RppgRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            alert.alerts.take(3).forEach { a ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "${a.title}: ${a.recommendation}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun TrendCard(trend: BpTrendAnalyzer.BpTrendResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "趋势分析",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                trend.summary,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
            if (trend.recordCount > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "平均: ${trend.avgSystolic.toInt()}/${trend.avgDiastolic.toInt()} mmHg | " +
                    "波动性: ${String.format("%.2f", trend.variabilityIndex)}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(record: BPRecord) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val alertColor = when (record.alertLevel) {
        "CRITICAL", "HIGH" -> RppgRed
        "MEDIUM" -> Warm40
        else -> RppgGreen
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "${record.systolic}/${record.diastolic} mmHg",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    "心率 ${record.heartRate} BPM · ${record.method}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
                Text(
                    dateFormat.format(Date(record.timestamp)),
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier.background(alertColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    when (record.alertLevel) {
                        "CRITICAL" -> "危险"
                        "HIGH" -> "偏高"
                        "MEDIUM" -> "关注"
                        "LOW" -> "正常高值"
                        else -> "正常"
                    },
                    color = alertColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * RGBA_8888 ImageProxy → ARGB_8888 Bitmap（含通道转换）
 *
 * CameraX 输出 RGBA_8888（byte0=R, byte1=G, byte2=B, byte3=A），
 * Bitmap.Config.ARGB_8888 期望（byte0=A, byte1=R, byte2=G, byte3=B）。
 * copyPixelsFromBuffer 不做通道转换，需手动交换。
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // RGBA → ARGB 通道转换：每个像素旋转 4 字节
        for (i in bytes.indices step 4) {
            if (i + 3 >= bytes.size) break
            val r = bytes[i]
            val g = bytes[i + 1]
            val b = bytes[i + 2]
            val a = bytes[i + 3]
            bytes[i] = a
            bytes[i + 1] = r
            bytes[i + 2] = g
            bytes[i + 3] = b
        }

        val bitmap = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
        bitmap
    } catch (e: Exception) {
        null
    }
}