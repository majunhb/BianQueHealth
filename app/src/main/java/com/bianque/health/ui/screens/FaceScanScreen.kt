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
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import com.bianque.health.ui.components.DiagnosisLabel
import com.bianque.health.ui.theme.Danger40
import com.bianque.health.ui.theme.Green40
import com.bianque.health.ui.theme.Warm40

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                                        onFrame = { bitmap -> capturedBitmap = bitmap }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (uiState.diagnosisResult != null) {
                    FaceDiagnosisResultCard(result = uiState.diagnosisResult!!)
                } else if (uiState.errorMessage != null) {
                    FaceErrorCard(message = uiState.errorMessage!!) { viewModel.clearError() }
                }

                if (uiState.isAnalyzing) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.analyzing_face), color = Color.White, style = MaterialTheme.typography.bodyLarge)
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
                        Text(stringResource(R.string.start_face_scan), style = MaterialTheme.typography.titleLarge)
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