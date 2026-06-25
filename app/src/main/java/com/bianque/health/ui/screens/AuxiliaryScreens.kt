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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bianque.health.content.domain.model.*
import com.bianque.health.content.domain.repository.ContentRepository
import com.bianque.health.ui.components.MedicalDisclaimer
import com.bianque.health.ui.theme.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── 药膳食疗 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreen(onBack: () -> Unit) {
    val viewModel: DietViewModel = hiltViewModel()
    val state by viewModel.state
    val constitutions = listOf("平和质", "气虚质", "阳虚质", "阴虚质", "痰湿质", "湿热质", "血瘀质", "气郁质", "特禀质")
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selected) {
        viewModel.loadData(selected)
    }

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
                Text("选择您的体质，为您推荐适合的食疗方案", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    constitutions.take(5).forEach { c ->
                        FilterChip(selected = selected == c, onClick = {
                            selected = if (selected == c) null else c
                        }, label = { Text(c, fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    constitutions.drop(5).forEach { c ->
                        FilterChip(selected = selected == c, onClick = {
                            selected = if (selected == c) null else c
                        }, label = { Text(c, fontSize = 12.sp) })
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }

            when (state) {
                is DietState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Orange40)
                        }
                    }
                }
                is DietState.Success -> {
                    val recipes = (state as DietState.Success).recipes
                    if (recipes.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("暂无匹配的食疗方", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(recipes) { recipe ->
                        DietRecipeCard(recipe)
                    }
                }
                is DietState.Error -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("加载失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text((state as DietState.Error).message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { MedicalDisclaimer() }
        }
    }
}

@Composable
fun DietRecipeCard(recipe: DietRecipe) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("来源：${recipe.source}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("食材", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            recipe.ingredients.forEach { ing ->
                Text("· ${ing.name} ${ing.dosage}", fontSize = 14.sp)
            }

            Text("做法", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            recipe.steps.forEachIndexed { i, step ->
                Text("${i + 1}. $step", fontSize = 14.sp)
            }

            Text("功效：${recipe.efficacy}", fontSize = 14.sp)
            if (recipe.contraindications.isNotBlank()) {
                Text("禁忌：${recipe.contraindications}", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            }

            if (recipe.constitutionFit.isNotEmpty()) {
                Text("适用体质：${recipe.constitutionFit.joinToString("、")}", fontSize = 12.sp, color = Orange40)
            }
        }
    }
}

sealed class DietState {
    object Loading : DietState()
    data class Success(val recipes: List<DietRecipe>) : DietState()
    data class Error(val message: String) : DietState()
}

@HiltViewModel
class DietViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = mutableStateOf<DietState>(DietState.Loading)
    val state: State<DietState> = _state

    fun loadData(constitution: String?) {
        viewModelScope.launch {
            _state.value = DietState.Loading
            try {
                val recipes = contentRepository.getDietRecipes(constitution)
                _state.value = DietState.Success(recipes)
            } catch (e: Exception) {
                _state.value = DietState.Error(e.message ?: "未知错误")
            }
        }
    }
}

// ─── 经络穴位 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeridianScreen(onBack: () -> Unit) {
    val viewModel: MeridianViewModel = hiltViewModel()
    val state by viewModel.state
    var selectedMeridian by remember { mutableStateOf<String?>(null) }
    val meridians = viewModel.meridians

    LaunchedEffect(selectedMeridian) {
        viewModel.loadData(selectedMeridian)
    }

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
                Text("选择经络查看穴位（共 ${meridians.size} 条经络）", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Split 14 meridians into two rows of 7
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meridians.take(7).forEach { m ->
                        FilterChip(
                            selected = selectedMeridian == m,
                            onClick = { selectedMeridian = if (selectedMeridian == m) null else m },
                            label = { Text(m, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meridians.drop(7).forEach { m ->
                        FilterChip(
                            selected = selectedMeridian == m,
                            onClick = { selectedMeridian = if (selectedMeridian == m) null else m },
                            label = { Text(m, fontSize = 11.sp) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }

            when (state) {
                is MeridianState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Blue40)
                        }
                    }
                }
                is MeridianState.Success -> {
                    val acupoints = (state as MeridianState.Success).acupoints
                    if (acupoints.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("暂无穴位数据", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(acupoints) { acupoint ->
                        AcupointCard(acupoint)
                    }
                }
                is MeridianState.Error -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("加载失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text((state as MeridianState.Error).message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { MedicalDisclaimer() }
        }
    }
}

@Composable
fun AcupointCard(acupoint: Acupoint) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(acupoint.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("${acupoint.pinyin} · ${acupoint.meridian}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("定位：${acupoint.location}", fontSize = 14.sp)

            if (acupoint.indications.isNotEmpty()) {
                Text("主治：", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                acupoint.indications.forEach { ind ->
                    Text("· $ind", fontSize = 14.sp)
                }
            }

            Text("按摩方法：${acupoint.massageMethod}", fontSize = 14.sp)
            Text("建议时长：${acupoint.massageDuration}", fontSize = 12.sp, color = Blue40)

            if (!acupoint.caution.isNullOrBlank()) {
                Text("注意：${acupoint.caution}", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }

            Text("编码：GB/T 12346-2021 ${acupoint.gbCode}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

sealed class MeridianState {
    object Loading : MeridianState()
    data class Success(val acupoints: List<Acupoint>) : MeridianState()
    data class Error(val message: String) : MeridianState()
}

@HiltViewModel
class MeridianViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = mutableStateOf<MeridianState>(MeridianState.Loading)
    val state: State<MeridianState> = _state
    val meridians: List<String> = listOf(
        "手太阴肺经", "手阳明大肠经", "足阳明胃经", "足太阴脾经",
        "手少阴心经", "手太阳小肠经", "足太阳膀胱经", "足少阴肾经",
        "手厥阴心包经", "手少阳三焦经", "足少阳胆经", "足厥阴肝经",
        "任脉", "督脉"
    )

    fun loadData(meridian: String?) {
        viewModelScope.launch {
            _state.value = MeridianState.Loading
            try {
                val acupoints = contentRepository.getAcupoints(meridian)
                _state.value = MeridianState.Success(acupoints)
            } catch (e: Exception) {
                _state.value = MeridianState.Error(e.message ?: "未知错误")
            }
        }
    }
}

// ─── 健康自测 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthQuizScreen(onBack: () -> Unit) {
    val viewModel: HealthQuizViewModel = hiltViewModel()
    val state by viewModel.state

    LaunchedEffect(Unit) {
        viewModel.loadQuizzes()
    }

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
            item {
                Text("选择量表进行自测，结果仅供健康趋势参考", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when (state) {
                is QuizListState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Warm40)
                        }
                    }
                }
                is QuizListState.Success -> {
                    items((state as QuizListState.Success).quizzes) { quiz ->
                        HealthQuizCard(quiz)
                    }
                }
                is QuizListState.Error -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("加载失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text((state as QuizListState.Error).message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { MedicalDisclaimer() }
        }
    }
}

@Composable
fun HealthQuizCard(quiz: HealthQuiz) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(quiz.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("来源：${quiz.source}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(quiz.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("共 ${quiz.questions.size} 题", fontSize = 13.sp, color = Warm40)
        }
    }
}

sealed class QuizListState {
    object Loading : QuizListState()
    data class Success(val quizzes: List<HealthQuiz>) : QuizListState()
    data class Error(val message: String) : QuizListState()
}

@HiltViewModel
class HealthQuizViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = mutableStateOf<QuizListState>(QuizListState.Loading)
    val state: State<QuizListState> = _state

    fun loadQuizzes() {
        viewModelScope.launch {
            try {
                val quizzes = contentRepository.getQuizzes()
                _state.value = QuizListState.Success(quizzes)
            } catch (e: Exception) {
                _state.value = QuizListState.Error(e.message ?: "未知错误")
            }
        }
    }
}

// ─── 疾病图解 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseScreen(onBack: () -> Unit) {
    val viewModel: DiseaseViewModel = hiltViewModel()
    val state by viewModel.state

    LaunchedEffect(Unit) {
        viewModel.loadData(null)
    }

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
            when (state) {
                is DiseaseListState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Danger40)
                        }
                    }
                }
                is DiseaseListState.Success -> {
                    val diseases = (state as DiseaseListState.Success).diseases
                    items(diseases) { disease ->
                        DiseaseCard(disease)
                    }
                }
                is DiseaseListState.Error -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("加载失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text((state as DiseaseListState.Error).message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { MedicalDisclaimer() }
        }
    }
}

@Composable
fun DiseaseCard(disease: DiseaseEntry) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(disease.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                if (disease.tcmName.isNotBlank() && disease.tcmName != disease.name) {
                    Text("中医：${disease.tcmName}", fontSize = 12.sp, color = Danger40)
                }
            }
            Text("ICD-11：${disease.icdCode}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)

            if (disease.overview.isNotBlank()) {
                Text("概述", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(disease.overview, fontSize = 14.sp)
            }

            if (disease.symptoms.isNotEmpty()) {
                Text("常见症状", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                disease.symptoms.forEach { sym ->
                    Text("· $sym", fontSize = 14.sp)
                }
            }

            if (disease.tcmSyndrome.isNotEmpty()) {
                Text("中医证型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(disease.tcmSyndrome.joinToString("、"), fontSize = 14.sp)
            }

            if (disease.prevention.isNotBlank()) {
                Text("预防建议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(disease.prevention, fontSize = 14.sp)
            }

            if (disease.whenToSeeDoctor.isNotBlank()) {
                Text("就医提示", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Danger40)
                Text(disease.whenToSeeDoctor, fontSize = 14.sp)
            }

            Text(disease.disclaimer, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

sealed class DiseaseListState {
    object Loading : DiseaseListState()
    data class Success(val diseases: List<DiseaseEntry>) : DiseaseListState()
    data class Error(val message: String) : DiseaseListState()
}

@HiltViewModel
class DiseaseViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = mutableStateOf<DiseaseListState>(DiseaseListState.Loading)
    val state: State<DiseaseListState> = _state

    fun loadData(category: String?) {
        viewModelScope.launch {
            try {
                val diseases = contentRepository.getDiseases(category)
                _state.value = DiseaseListState.Success(diseases)
            } catch (e: Exception) {
                _state.value = DiseaseListState.Error(e.message ?: "未知错误")
            }
        }
    }
}

// ─── 中药图鉴 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerbScreen(onBack: () -> Unit) {
    val viewModel: HerbViewModel = hiltViewModel()
    val state by viewModel.state

    LaunchedEffect(Unit) {
        viewModel.loadData(null)
    }

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
            when (state) {
                is HerbListState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Green40)
                        }
                    }
                }
                is HerbListState.Success -> {
                    val herbs = (state as HerbListState.Success).herbs
                    items(herbs) { herb ->
                        HerbCard(herb)
                    }
                }
                is HerbListState.Error -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("加载失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text((state as HerbListState.Error).message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { MedicalDisclaimer() }
        }
    }
}

@Composable
fun HerbCard(herb: HerbEntry) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(herb.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(herb.family, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("拉丁学名：${herb.latinName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            Text("药用部位：${herb.medicinalPart}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("性味：${herb.nature}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("归经：${herb.meridian}", fontSize = 14.sp)
            Text("功效：${herb.efficacy}", fontSize = 14.sp)
            Text("常用剂量：${herb.dosage}", fontSize = 14.sp)

            if (herb.similarHerbs.isNotEmpty()) {
                Text("易混淆药材：${herb.similarHerbs.joinToString("、")}", fontSize = 13.sp, color = Green40)
            }
        }
    }
}

sealed class HerbListState {
    object Loading : HerbListState()
    data class Success(val herbs: List<HerbEntry>) : HerbListState()
    data class Error(val message: String) : HerbListState()
}

@HiltViewModel
class HerbViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = mutableStateOf<HerbListState>(HerbListState.Loading)
    val state: State<HerbListState> = _state

    fun loadData(family: String?) {
        viewModelScope.launch {
            try {
                val herbs = contentRepository.getHerbs(family)
                _state.value = HerbListState.Success(herbs)
            } catch (e: Exception) {
                _state.value = HerbListState.Error(e.message ?: "未知错误")
            }
        }
    }
}

// ─── 名方今用 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionScreen(onBack: () -> Unit) {
    val viewModel: PrescriptionViewModel = hiltViewModel()
    val state by viewModel.state

    LaunchedEffect(Unit) {
        viewModel.loadData(null)
    }

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
            when (state) {
                is FormulaListState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Purple40)
                        }
                    }
                }
                is FormulaListState.Success -> {
                    val formulas = (state as FormulaListState.Success).formulas
                    items(formulas) { formula ->
                        FormulaCard(formula)
                    }
                }
                is FormulaListState.Error -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("加载失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text((state as FormulaListState.Error).message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { MedicalDisclaimer() }
        }
    }
}

@Composable
fun FormulaCard(formula: ClassicFormula) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formula.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(formula.source, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("原文", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(formula.originalText, fontSize = 14.sp, fontStyle = FontStyle.Italic)

            Text("组成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            formula.composition.forEach { ing ->
                Text("· ${ing.name} ${ing.dosage}", fontSize = 14.sp)
            }

            Text("原方适应症：${formula.indications}", fontSize = 14.sp)

            if (formula.modernUse.isNotBlank()) {
                Text("现代应用：${formula.modernUse}", fontSize = 14.sp, color = Purple40)
            }
        }
    }
}

sealed class FormulaListState {
    object Loading : FormulaListState()
    data class Success(val formulas: List<ClassicFormula>) : FormulaListState()
    data class Error(val message: String) : FormulaListState()
}

@HiltViewModel
class PrescriptionViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = mutableStateOf<FormulaListState>(FormulaListState.Loading)
    val state: State<FormulaListState> = _state

    fun loadData(source: String?) {
        viewModelScope.launch {
            try {
                val formulas = contentRepository.getClassicFormulas(source)
                _state.value = FormulaListState.Success(formulas)
            } catch (e: Exception) {
                _state.value = FormulaListState.Error(e.message ?: "未知错误")
            }
        }
    }
}

// ─── 养生科普 ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTipsScreen(onBack: () -> Unit) {
    val viewModel: HealthTipsViewModel = hiltViewModel()
    val state by viewModel.state

    LaunchedEffect(Unit) {
        viewModel.loadArticles(null, null)
    }

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
            when (state) {
                is HealthTipsListState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF00838F))
                        }
                    }
                }
                is HealthTipsListState.Success -> {
                    val articles = (state as HealthTipsListState.Success).articles
                    items(articles) { article ->
                        HealthArticleCard(article)
                    }
                }
                is HealthTipsListState.Error -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("加载失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text((state as HealthTipsListState.Error).message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { MedicalDisclaimer() }
        }
    }
}

@Composable
fun HealthArticleCard(article: HealthArticle) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (article.subtitle.isNotBlank()) {
                Text(article.subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("来源：${article.source}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            article.solarTerm?.let { term ->
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(term, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE0F7FA))
                )
            }
        }
    }
}

sealed class HealthTipsListState {
    object Loading : HealthTipsListState()
    data class Success(val articles: List<HealthArticle>) : HealthTipsListState()
    data class Error(val message: String) : HealthTipsListState()
}

@HiltViewModel
class HealthTipsViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = mutableStateOf<HealthTipsListState>(HealthTipsListState.Loading)
    val state: State<HealthTipsListState> = _state

    fun loadArticles(solarTerm: String?, tags: List<String>?) {
        viewModelScope.launch {
            try {
                val articles = contentRepository.getArticles(solarTerm, tags)
                _state.value = HealthTipsListState.Success(articles)
            } catch (e: Exception) {
                _state.value = HealthTipsListState.Error(e.message ?: "未知错误")
            }
        }
    }
}