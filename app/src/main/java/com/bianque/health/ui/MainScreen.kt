package com.bianque.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bianque.health.ui.components.HealthModuleCard
import com.bianque.health.ui.theme.Green40

data class HealthModule(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val backgroundColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToFace: () -> Unit,
    onNavigateToTongue: () -> Unit,
    onNavigateToBP: () -> Unit,
    onNavigateToPulse: () -> Unit,
    onNavigateToReport: () -> Unit
) {
    val modules = listOf(
        HealthModule(
            title = "面诊",
            description = "面部气色分析",
            icon = Icons.Default.Face,
            backgroundColor = Color(0xFFE64A19)
        ),
        HealthModule(
            title = "舌诊",
            description = "舌象特征辨识",
            icon = Icons.Default.Visibility,
            backgroundColor = Color(0xFFC2185B)
        ),
        HealthModule(
            title = "血压",
            description = "血压心率监测",
            icon = Icons.Default.Favorite,
            backgroundColor = Color(0xFF1976D2)
        ),
        HealthModule(
            title = "脉诊",
            description = "脉象采集分析",
            icon = Icons.Default.MonitorHeart,
            backgroundColor = Color(0xFF7B1FA2)
        ),
        HealthModule(
            title = "健康报告",
            description = "综合健康评估",
            icon = Icons.Default.Assessment,
            backgroundColor = Green40
        )
    )

    val onClickMap = listOf(
        onNavigateToFace,
        onNavigateToTongue,
        onNavigateToBP,
        onNavigateToPulse,
        onNavigateToReport
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "扁鹊健康",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(modules.size) { index ->
                    val module = modules[index]
                    HealthModuleCard(
                        title = module.title,
                        description = module.description,
                        icon = module.icon,
                        backgroundColor = module.backgroundColor,
                        onClick = onClickMap[index]
                    )
                }
            }
            // 副标题
            Text(
                text = "多模态中医健康检测",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            )
        }
    }
}