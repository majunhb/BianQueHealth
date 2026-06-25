package com.bianque.health.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bianque.health.R
import com.bianque.health.ui.components.MedicalDisclaimer
import com.bianque.health.ui.theme.*

// ─── 药膳食疗 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreen(onBack: () -> Unit) {
    val constitutions = listOf("平和质", "气虚质", "阳虚质", "阴虚质", "痰湿质", "湿热质", "血瘀质", "气郁质", "特禀质")
    var selected by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("药膳食疗", color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Orange40)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("选择您的体质，AI 将为您推荐适合的食疗方案", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    constitutions.take(5).forEach { c ->
                        FilterChip(selected = selected == c, onClick = { selected = if (selected == c) null else c }, label = { Text(c, fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    constitutions.drop(5).forEach { c ->
                        FilterChip(selected = selected == c, onClick = { selected = if (selected == c) null else c }, label = { Text(c, fontSize = 12.sp) })
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("内容建设中", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Orange40)
                        Spacer(Modifier.height(8.dp))
                        Text("药膳食疗模块正在搭建中，将采用「国家药典数据库 + 经典食疗方 + LLM 个性化推荐」混合模式。\n\n第一阶段：人工整理《药典》药材数据 + 经典食疗方导入 Excel\n第二阶段：接入 RAG 系统，根据体质诊断结果智能推荐\n第三阶段：社区 UGC 反馈，优化推荐效果", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { MedicalDisclaimer() }
        }
    }
}

// ─── 经络穴位 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeridianScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("经络穴位", color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Blue40)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入症状，如「头痛按哪里」") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("内容建设中", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Blue40)
                        Spacer(Modifier.height(8.dp))
                        Text("经络穴位模块正在搭建中，将采用「GB/T 12346-2021 标准穴位数据 + 3D人体模型 + LLM症状反查」混合模式。\n\n第一阶段：导入国家标准穴位数据（名称/定位/归经/主治）\n第二阶段：接入3D人体模型，可视化展示穴位位置\n第三阶段：LLM 支持症状→穴位智能反查", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { MedicalDisclaimer() }
        }
    }
}

// ─── 健康自测 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthQuizScreen(onBack: () -> Unit) {
    val quizzes = listOf(
        Triple("中医体质分类", "中华中医药学会标准", "9种体质分类自测"),
        Triple("PHQ-9 抑郁筛查", "国际通用量表", "9题快速筛查抑郁情绪"),
        Triple("GAD-7 焦虑筛查", "国际通用量表", "7题快速筛查焦虑情绪"),
        Triple("睡眠质量评估", "PSQI 匹兹堡量表", "全面评估睡眠质量")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("健康自测", color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Warm40)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("选择量表进行自测，结果仅供健康趋势参考", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(quizzes) { (title, source, desc) ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("来源：$source", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(desc, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {}, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Warm40)) { Text("开始测试") }
                    }
                }
            }
            item { MedicalDisclaimer() }
        }
    }
}

// ─── 疾病图解 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("疾病图解", color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Danger40)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("内容建设中", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Danger40)
                        Spacer(Modifier.height(8.dp))
                        Text("疾病图解模块正在搭建中，将采用「ICD-11 国际疾病分类 + 中医辨证分型 + LLM 通俗解读」混合模式。\n\n严禁自行诊断用药，AI 仅提供科普参考。\n\n第一阶段：导入 ICD-11 编码 + 中医对应病名\n第二阶段：构建疾病-症状-证型知识图谱\n第三阶段：LLM 预问诊模拟 + 就医指引", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { MedicalDisclaimer() }
        }
    }
}

// ─── 中药图鉴 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerbScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("中药图鉴", color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green40)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("内容建设中", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Green40)
                        Spacer(Modifier.height(8.dp))
                        Text("中药图鉴模块正在搭建中，将采用「《药典》中药材数据库 + 高清植物图库 + LLM 鉴别口诀」混合模式。\n\n第一阶段：人工整理《药典》药材数据，导入 Excel（名称/性味/归经/功效/用量）\n第二阶段：引入高清药材图库（避免版权问题）\n第三阶段：LLM 生成鉴别顺口溜 + 相似药材对比表", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { MedicalDisclaimer() }
        }
    }
}

// ─── 名方今用 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("名方今用", color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple40)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("内容建设中", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Purple40)
                        Spacer(Modifier.height(8.dp))
                        Text("名方今用模块正在搭建中，将采用「经典古籍数字化 + 古方今译 LLM + 现代应用推荐」混合模式。\n\n第一阶段：人工整理《伤寒杂病论》《本草纲目》等公版经典方剂\n第二阶段：LLM 古方今译 + 现代应用解读\n第三阶段：结合体质诊断结果，个性化推荐名方", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { MedicalDisclaimer() }
        }
    }
}

// ─── 养生科普 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTipsScreen(onBack: () -> Unit) {
    val solarTerms = listOf(
        "立春" to "春回大地，万物复苏",
        "雨水" to "春雨贵如油",
        "惊蛰" to "春雷乍动，万物生长",
        "春分" to "昼夜平分，阴阳平衡",
        "清明" to "天清地明，踏青时节",
        "谷雨" to "雨生百谷，春夏之交"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("养生科普", color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF00838F))
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("节气养生 · 每日推送 · 权威科普", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            item { Text("二十四节气养生", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(solarTerms) { (term, desc) ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(term, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF00838F))
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA))) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("内容来源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF00838F))
                        Spacer(Modifier.height(8.dp))
                        Text("养生科普采用「权威指南 + LLM 润色」混合模式：\n\n- 地基：《中国居民膳食指南》、三甲医院科普文章 RAG 检索\n- 应用层：LLM 将专业内容通俗化、趣味化\n- 节气推送：结合二十四节气，自动生成当日养生小贴士\n- 每日更新：关注热点健康话题，反向补充内容库", fontSize = 14.sp, color = Color(0xFF004D40))
                    }
                }
            }
            item { MedicalDisclaimer() }
        }
    }
}