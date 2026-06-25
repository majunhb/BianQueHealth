package com.bianque.health.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bianque.health.R
import com.bianque.health.ui.theme.*

// ─── 辅助模块卡片数据 ────────────────────────────────────────
private data class SupportModule(
    val titleResId: Int,
    val descriptionResId: Int,
    val icon: ImageVector,
    val color: Color
)

// ─── 首页 ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToAIDiagnosis: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    // 8 个辅助功能模块
    val supportModules = listOf(
        SupportModule(R.string.module_diet, R.string.module_diet_desc, Icons.Default.Restaurant, Orange40),
        SupportModule(R.string.module_meridian, R.string.module_meridian_desc, Icons.Default.AccountTree, Blue40),
        SupportModule(R.string.module_health_quiz, R.string.module_health_quiz_desc, Icons.Default.Quiz, Warm40),
        SupportModule(R.string.module_disease, R.string.module_disease_desc, Icons.Default.Coronavirus, Danger40),
        SupportModule(R.string.module_herb, R.string.module_herb_desc, Icons.Default.LocalFlorist, Green40),
        SupportModule(R.string.module_prescription, R.string.module_prescription_desc, Icons.Default.MenuBook, Purple40),
        SupportModule(R.string.module_health_tips, R.string.module_health_tips_desc, Icons.Default.Lightbulb, Color(0xFF00838F)),
        SupportModule(R.string.module_report, R.string.module_report_desc, Icons.Default.Assessment, Color(0xFF4A148C))
    )

    // 呼吸动画
    val breatheAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        breatheAnim.animateTo(1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Security, contentDescription = stringResource(R.string.privacy_settings_menu), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Hero 卡片: AI诊断（占满一行两列） ──
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                AIHeroCard(
                    onClick = onNavigateToAIDiagnosis,
                    breatheValue = breatheAnim.value
                )
            }

            // ── 8 个辅助功能模块 ──
            itemsIndexed(supportModules) { _, module ->
                ElevatedCard(
                    onClick = { /* 后续迭代接入 */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = module.color.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(module.icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = module.color)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(module.titleResId),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(module.descriptionResId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─── AI诊断 Hero 卡片 ────────────────────────────────────────
@Composable
private fun AIHeroCard(
    onClick: () -> Unit,
    breatheValue: Float
) {
    val heroGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C))
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroGradient, RoundedCornerShape(20.dp))
        ) {
            // 背景装饰圆环
            val breatheRadius = 140f + breatheValue * 20f
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * 0.82f
                val cy = size.height * 0.35f
                // 外圈
                drawCircle(Color.White.copy(alpha = 0.06f), radius = breatheRadius, center = Offset(cx, cy))
                drawCircle(Color.White.copy(alpha = 0.04f), radius = breatheRadius * 1.3f, center = Offset(cx, cy))
                // 十字星芒
                val lineLen = 60f
                val alpha = 0.08f + breatheValue * 0.04f
                drawLine(Color.White.copy(alpha = alpha), Offset(cx - lineLen, cy), Offset(cx + lineLen, cy), 1.5f)
                drawLine(Color.White.copy(alpha = alpha), Offset(cx, cy - lineLen), Offset(cx, cy + lineLen), 1.5f)
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                // 标题行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 四诊图标
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(60.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                // 十字 + 眼 组合图标
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.ai_diagnosis_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        )
                        Text(
                            stringResource(R.string.ai_diagnosis_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 四诊子模块标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroTag("👅 舌诊", Color.White.copy(alpha = 0.15f))
                    HeroTag("😊 面诊", Color.White.copy(alpha = 0.15f))
                    HeroTag("💓 脉诊", Color.White.copy(alpha = 0.15f))
                    HeroTag("🩺 血压", Color.White.copy(alpha = 0.15f))
                }

                Spacer(Modifier.height(16.dp))

                // 描述
                Text(
                    stringResource(R.string.module_ai_diagnosis_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─── Hero 子标签 ──────────────────────────────────────────────
@Composable
private fun HeroTag(text: String, bgColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}