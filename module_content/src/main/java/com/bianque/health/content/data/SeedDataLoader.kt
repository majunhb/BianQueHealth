package com.bianque.health.content.data

import android.content.Context
import com.bianque.health.content.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 种子数据加载器 — 冷启动阶段从 assets/knowledge_base/ 加载预置数据。
 *
 * 所有种子数据均为人工整理核对的权威来源（国家药典/GB标准/公版古籍），
 * 确保APP一上线就有充实的内容，无需等待网络请求。
 */
@Singleton
class SeedDataLoader @Inject constructor() {

    // ─── 中药图鉴 ──────────────────────────────────────────────

    suspend fun loadHerbs(context: Context): List<HerbEntry> = withContext(Dispatchers.IO) {
        try {
            val json = readAsset(context, "knowledge_base/herbs_seed.json")
            parseHerbs(json)
        } catch (e: Exception) {
            Timber.e(e, "SeedDataLoader: failed to load herbs")
            emptyList()
        }
    }

    // ─── 经络穴位 ──────────────────────────────────────────────

    suspend fun loadAcupoints(context: Context): List<Acupoint> = withContext(Dispatchers.IO) {
        try {
            val json = readAsset(context, "knowledge_base/acupoints_seed.json")
            parseAcupoints(json)
        } catch (e: Exception) {
            Timber.e(e, "SeedDataLoader: failed to load acupoints")
            emptyList()
        }
    }

    // ─── 药膳食疗 ──────────────────────────────────────────────

    suspend fun loadDietRecipes(context: Context): List<DietRecipe> = withContext(Dispatchers.IO) {
        try {
            val json = readAsset(context, "knowledge_base/diet_recipes_seed.json")
            parseDietRecipes(json)
        } catch (e: Exception) {
            Timber.e(e, "SeedDataLoader: failed to load diet recipes")
            emptyList()
        }
    }

    // ─── 名方今用 ──────────────────────────────────────────────

    suspend fun loadFormulas(context: Context): List<ClassicFormula> = withContext(Dispatchers.IO) {
        try {
            val json = readAsset(context, "knowledge_base/formulas_seed.json")
            parseFormulas(json)
        } catch (e: Exception) {
            Timber.e(e, "SeedDataLoader: failed to load formulas")
            emptyList()
        }
    }

    // ─── 健康自测 ──────────────────────────────────────────────

    suspend fun loadQuizzes(context: Context): List<HealthQuiz> = withContext(Dispatchers.IO) {
        try {
            val json = readAsset(context, "knowledge_base/quizzes_seed.json")
            parseQuizzes(json)
        } catch (e: Exception) {
            Timber.e(e, "SeedDataLoader: failed to load quizzes")
            emptyList()
        }
    }

    // ─── 疾病图解 ──────────────────────────────────────────────

    suspend fun loadDiseases(context: Context): List<DiseaseEntry> = withContext(Dispatchers.IO) {
        try {
            val json = readAsset(context, "knowledge_base/diseases_seed.json")
            parseDiseases(json)
        } catch (e: Exception) {
            Timber.e(e, "SeedDataLoader: failed to load diseases")
            emptyList()
        }
    }

    // ─── 养生科普 ──────────────────────────────────────────────

    suspend fun loadSolarTerms(context: Context): List<HealthArticle> = withContext(Dispatchers.IO) {
        try {
            val json = readAsset(context, "knowledge_base/solar_terms_seed.json")
            parseSolarTerms(json)
        } catch (e: Exception) {
            Timber.e(e, "SeedDataLoader: failed to load solar terms")
            emptyList()
        }
    }

    // ─── 工具方法 ──────────────────────────────────────────────

    private fun readAsset(context: Context, path: String): String {
        val input = context.assets.open(path)
        return BufferedReader(InputStreamReader(input, "UTF-8")).use { it.readText() }
    }

    // ─── JSON 解析 (使用 org.json，避免额外依赖) ────────────────

    private fun parseHerbs(json: String): List<HerbEntry> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HerbEntry(
                id = o.optString("name"),
                name = o.optString("name"),
                latinName = o.optString("latin"),
                family = o.optString("family"),
                medicinalPart = o.optString("part"),
                nature = o.optString("nature"),
                meridian = o.optString("meridian"),
                efficacy = o.optString("efficacy"),
                dosage = o.optString("dosage"),
                similarHerbs = jsonArrayToList(o.optJSONArray("similar"))
            )
        }
    }

    private fun parseAcupoints(json: String): List<Acupoint> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Acupoint(
                id = o.optString("gbCode"),
                name = o.optString("name"),
                pinyin = o.optString("pinyin"),
                gbCode = o.optString("gbCode"),
                meridian = o.optString("meridian"),
                location = o.optString("location"),
                location3D = floatArrayOf(0f, 0f, 0f),
                indications = jsonArrayToList(o.optJSONArray("indications")),
                massageMethod = o.optString("massage"),
                massageDuration = o.optString("duration"),
                caution = o.optString("caution", null)
            )
        }
    }

    private fun parseDietRecipes(json: String): List<DietRecipe> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ingredients = (0 until o.getJSONArray("ingredients").length()).map { j ->
                val ing = o.getJSONArray("ingredients").getJSONObject(j)
                Ingredient(
                    name = ing.optString("name"),
                    dosage = ing.optString("dosage"),
                    nature = "",
                    meridian = ""
                )
            }
            DietRecipe(
                id = o.optString("id"),
                name = o.optString("name"),
                source = o.optString("source", "经典食疗方"),
                sourceVerified = true,
                constitutionFit = listOf(o.optString("constitution")),
                ingredients = ingredients,
                steps = jsonArrayToList(o.optJSONArray("steps")),
                efficacy = o.optString("efficacy"),
                contraindications = o.optString("contraindications", ""),
                llmGenerated = false
            )
        }
    }

    private fun parseFormulas(json: String): List<ClassicFormula> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val composition = (0 until o.getJSONArray("composition").length()).map { j ->
                val ing = o.getJSONArray("composition").getJSONObject(j)
                Ingredient(
                    name = ing.optString("name"),
                    dosage = ing.optString("dosage"),
                    nature = "",
                    meridian = ""
                )
            }
            ClassicFormula(
                id = o.optString("id"),
                name = o.optString("name"),
                source = o.optString("source"),
                originalText = o.optString("originalText"),
                modernTranslation = "",
                composition = composition,
                indications = o.optString("indications"),
                modernUse = o.optString("modernUse"),
                modernExplanation = "",
                suitability = "",
                llmGenerated = false
            )
        }
    }

    private fun parseQuizzes(json: String): List<HealthQuiz> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val questions = (0 until o.getJSONArray("questions").length()).map { j ->
                val q = o.getJSONArray("questions").getJSONObject(j)
                QuizQuestion(
                    id = q.optInt("id"),
                    text = q.optString("text"),
                    options = jsonArrayToList(q.optJSONArray("options")),
                    scores = jsonArrayToIntList(q.optJSONArray("scores"))
                )
            }
            val scoringObj = o.getJSONObject("scoring")
            val ranges = (0 until scoringObj.getJSONArray("ranges").length()).map { j ->
                val r = scoringObj.getJSONArray("ranges").getJSONObject(j)
                ScoreRange(
                    minScore = r.optInt("minScore"),
                    maxScore = r.optInt("maxScore"),
                    label = r.optString("label"),
                    advice = r.optString("advice")
                )
            }
            HealthQuiz(
                id = o.optString("id"),
                title = o.optString("title"),
                description = o.optString("description"),
                source = o.optString("source"),
                questions = questions,
                scoring = QuizScoring(ranges = ranges)
            )
        }
    }

    private fun parseDiseases(json: String): List<DiseaseEntry> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DiseaseEntry(
                id = o.optString("id"),
                icdCode = o.optString("icdCode"),
                name = o.optString("name"),
                tcmName = o.optString("tcmName"),
                overview = o.optString("overview"),
                symptoms = jsonArrayToList(o.optJSONArray("symptoms")),
                tcmSyndrome = jsonArrayToList(o.optJSONArray("tcmSyndrome")),
                prevention = o.optString("prevention"),
                whenToSeeDoctor = o.optString("whenToSeeDoctor")
            )
        }
    }

    private fun parseSolarTerms(json: String): List<HealthArticle> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val diet = jsonArrayToList(o.optJSONArray("diet"))
            val lifestyle = jsonArrayToList(o.optJSONArray("lifestyle"))
            val content = buildString {
                append("【饮食建议】\n")
                diet.forEach { append("· $it\n") }
                append("\n【生活起居】\n")
                lifestyle.forEach { append("· $it\n") }
                append("\n【运动建议】\n")
                append("· ${o.optString("exercise")}\n")
                append("\n【核心要点】\n")
                append(o.optString("healthTip"))
            }
            HealthArticle(
                id = o.optString("id"),
                title = "${o.optString("solarTerm")}养生指南",
                subtitle = o.optString("healthTip"),
                source = "传统二十四节气养生知识",
                solarTerm = o.optString("solarTerm"),
                content = content,
                tags = listOf(o.optString("season"), o.optString("solarTerm"), "节气养生"),
                isSeasonal = true,
                llmGenerated = false
            )
        }
    }

    // ─── 辅助方法 ──────────────────────────────────────────────

    private fun jsonArrayToList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    private fun jsonArrayToIntList(arr: org.json.JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optInt(it) }
    }
}