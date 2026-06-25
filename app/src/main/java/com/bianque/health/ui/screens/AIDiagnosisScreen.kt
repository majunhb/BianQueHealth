package com.bianque.health.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bianque.health.R
import com.bianque.health.ui.theme.*

// ─── 诊断子模块数据 ────────────────────────────────────────────
private data class DiagModule(
    val titleResId: Int,
    val descriptionResId: Int,
    val icon: ImageVector,
    val color: Color,
    val gradient: List<Color>
)

// ─── AI诊断中心 ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIDiagnosisScreen(
    onBack: () -> Unit,
    onNavigateToFace: () -> Unit,
    onNavigateToTongue: () -> Unit,
    onNavigateToBP: () -> Unit,
    onNavigateToPulse: () -> Unit
) {
    val modules = listOf(
        DiagModule(
            R.string.module_tongue, R.string.module_tongue_desc,
            Icons.Default.RemoveRedEye, Orange40,
            listOf(Color(0xFFE65100), Color(0xFFEF6C00), Color(0xFFFF9800))
        ),
        DiagModule(
            R.string.module_face, R.string.module_face_desc,
            Icons.Default.Face, Green40,
            listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047))
        ),
        DiagModule(
            R.string.module_pulse, R.string.module_pulse_desc,
            Icons.Default.Favorite, Blue40,
            listOf(Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF1976D2))
        ),
        DiagModule(
            R.string.module_bp, R.string.module_bp_desc,
            Icons.Default.MonitorHeart, Danger40,
            listOf(Color(0xFFB71C1C), Color(0xFFC62828), Color(0xFFE53935))
        )
    )

    val onClickMap = listOf(onNavigateToTongue, onNavigateToFace, onNavigateToPulse, onNavigateToBP)

    // 中心脉动动画
    val pulseAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pulseAnim.animateTo(1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.ai_diagnosis_title), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.ai_diagnosis_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // 中心引导图标
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // 呼吸脉冲环
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val baseR = 50f
                    val pulseR = baseR + pulseAnim.value * 12f
                    drawCircle(Color(0xFF2E7D32).copy(alpha = 0.08f), radius = pulseR, center = Offset(cx, cy))
                    drawCircle(Color(0xFF2E7D32).copy(alpha = 0.05f), radius = pulseR * 1.2f, center = Offset(cx, cy))
                }
                // 四诊合一图标
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = Green40.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Green40
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ai_diagnosis_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(24.dp))

            // 四诊模块卡片（2x2 网格）
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 第一行：舌诊 + 面诊
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DiagModuleCard(
                        module = modules[0],
                        onClick = onClickMap[0],
                        modifier = Modifier.weight(1f)
                    )
                    DiagModuleCard(
                        module = modules[1],
                        onClick = onClickMap[1],
                        modifier = Modifier.weight(1f)
                    )
                }
                // 第二行：脉诊 + 血压
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DiagModuleCard(
                        module = modules[2],
                        onClick = onClickMap[2],
                        modifier = Modifier.weight(1f)
                    )
                    DiagModuleCard(
                        module = modules[3],
                        onClick = onClickMap[3],
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 底部免责声明
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Warm40.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Warm40,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI诊断结果仅供日常健康趋势参考，不可替代专业医疗诊断。如有不适，请及时就医。",
                        color = Warm40,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── 诊断模块卡片 ──────────────────────────────────────────────
@Composable
private fun DiagModuleCard(
    module: DiagModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标 + 渐变背景
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(module.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    module.icon,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = Color.White
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(module.titleResId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(module.descriptionResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}