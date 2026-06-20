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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bianque.health.R
import com.bianque.health.base.camera.CameraHelper
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import com.bianque.health.ui.components.DiagnosisLabel
import com.bianque.health.ui.theme.Danger40
import com.bianque.health.ui.theme.Green40
import com.bianque.health.ui.theme.Warm40

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
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
                                        cameraFacing = CameraSelector.LENS_FACING_BACK,
                                        onFrame = { bitmap -> capturedBitmap = bitmap }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (uiState.diagnosisResult != null) {
                    TongueDiagnosisResultCard(result = uiState.diagnosisResult!!)
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

            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (uiState.diagnosisResult == null) {
                    Button(
                        onClick = {
                            val bitmap = capturedBitmap
                            if (bitmap == null) {
                                viewModel.clearError()
                                return@Button
                            }
                            viewModel.analyze(bitmap)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isAnalyzing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.start_tongue_scan), style = MaterialTheme.typography.titleLarge)
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
        "黄白" -> sb.append("黄白相兼苔，表邪入里化热。")
    }
    when (result.coatingThickness) {
        "厚" -> sb.append("苔厚提示邪气较盛，病位较深。")
        "薄" -> sb.append("苔薄为正常或病邪轻浅。")
    }
    if (sb.isEmpty()) sb.append("舌象基本正常，请保持良好生活习惯。")
    return sb.toString()
}