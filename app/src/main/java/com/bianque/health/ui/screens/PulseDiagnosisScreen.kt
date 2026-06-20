package com.bianque.health.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bianque.health.R
import com.bianque.health.ui.theme.Blue40
import com.bianque.health.ui.theme.Danger40
import com.bianque.health.ui.theme.Green40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseDiagnosisScreen(
    onBack: () -> Unit,
    viewModel: PulseDiagnosisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pulse_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleConnection() }) {
                        Icon(
                            if (uiState.isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                            contentDescription = "蓝牙",
                            tint = if (uiState.isConnected) Green40 else Danger40
                        )
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.current_pulse), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "${uiState.currentPulseRate}",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                        fontWeight = FontWeight.Bold,
                        color = Blue40
                    )
                    Text(stringResource(R.string.bpm_unit), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.label_pulse_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(uiState.currentPulseType, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Blue40)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.label_pulse_strength), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(uiState.currentPulseStrength, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Blue40)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.startMeasurement() },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                enabled = !uiState.isMeasuring,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isMeasuring) MaterialTheme.colorScheme.surfaceVariant else Blue40
                )
            ) {
                if (uiState.isMeasuring) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.measuring), style = MaterialTheme.typography.titleLarge)
                } else {
                    Icon(Icons.Default.Favorite, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.start_pulse_measure), style = MaterialTheme.typography.titleLarge)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.history.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                uiState.history.forEach { record ->
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${record.pulseRate} ${stringResource(R.string.bpm_unit)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${record.pulseType} · ${record.pulseStrength}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(record.timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}