package com.bianque.health.content.data

import android.content.Context
import com.bianque.health.content.data.rag.QwenService
import com.bianque.health.content.data.rag.RagPromptTemplates
import com.bianque.health.content.domain.model.*
import com.bianque.health.content.domain.repository.ContentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通义千问驱动的统一内容仓库实现。
 *
 * 冷启动策略：
 * 1. 优先从本地种子数据（assets/knowledge_base/）加载，确保打开即有数据
 * 2. 当通义千问 API Key 可用时，LLM 可用于润色/翻译/生成个性化内容
 * 3. 种子数据由人工整理核对，来源权威可追溯
 *
 * 所有模块都通过此仓库 + [QwenService] 调用通义千问：
 * - 药膳食疗 → 体质匹配推荐
 * - 经络穴位 → 症状反查
 * - 中药图鉴 → 药材对比
 * - 名方今用 → 古方今译
 * - 养生科普 → 节气推送 + 文章润色
 * - 健康自测 → 评分解释
 * - 疾病 → 科普解读
 */
@Singleton
class QwenContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seedDataLoader: SeedDataLoader,
    private val qwenService: QwenService
) : ContentRepository {

    // ─── 本地缓存 + 线程安全 ────────────────────────────────────

    private val cacheMutex = Mutex()

    private var herbsCache: List<HerbEntry>? = null
    private var acupointsCache: List<Acupoint>? = null
    private var dietRecipesCache: List<DietRecipe>? = null
    private var formulasCache: List<ClassicFormula>? = null
    private var quizzesCache: List<HealthQuiz>? = null
    private var diseasesCache: List<DiseaseEntry>? = null
    private var articlesCache: List<HealthArticle>? = null

    // ─── 药膳食疗 ──────────────────────────────────────────────

    override suspend fun getDietRecipes(constitution: String?): List<DietRecipe> {
        val all = getDietCache()
        if (constitution == null) return all
        return all.filter { recipe ->
            recipe.constitutionFit.any { it.contains(constitution) || constitution.contains(it) }
        }
    }

    override suspend fun getDietRecipeById(id: String): DietRecipe? {
        return getDietCache().find { it.id == id }
    }

    override suspend fun generateDietRecommendation(constitution: String): RagContext {
        return RagPromptTemplates.dietForConstitution(
            constitution = constitution,
            docs = emptyList() // 由上层注入检索结果
        )
    }

    // ─── 经络穴位 ──────────────────────────────────────────────

    override suspend fun getAcupoints(meridian: String?): List<Acupoint> {
        val all = getAcupointCache()
        if (meridian == null) return all
        return all.filter { it.meridian == meridian }
    }

    override suspend fun getAcupointById(id: String): Acupoint? {
        return getAcupointCache().find { it.id == id }
    }

    override suspend fun searchAcupointsBySymptom(symptom: String): RagContext {
        return RagPromptTemplates.acupointQuery(symptom)
    }

    override suspend fun getMeridians(): List<String> = listOf(
        "手太阴肺经", "手阳明大肠经", "足阳明胃经", "足太阴脾经",
        "手少阴心经", "手太阳小肠经", "足太阳膀胱经", "足少阴肾经",
        "手厥阴心包经", "手少阳三焦经", "足少阳胆经", "足厥阴肝经",
        "任脉", "督脉"
    )

    // ─── 中药图鉴 ──────────────────────────────────────────────

    override suspend fun getHerbs(family: String?): List<HerbEntry> {
        val all = getHerbCache()
        if (family == null) return all
        return all.filter { it.family.contains(family) }
    }

    override suspend fun getHerbById(id: String): HerbEntry? {
        return getHerbCache().find { it.id == id }
    }

    override suspend fun compareHerbs(herbA: String, herbB: String): RagContext {
        return RagPromptTemplates.herbComparison(herbA, herbB, emptyList())
    }

    // ─── 名方今用 ──────────────────────────────────────────────

    override suspend fun getClassicFormulas(source: String?): List<ClassicFormula> {
        val all = getFormulaCache()
        if (source == null) return all
        return all.filter { it.source.contains(source) }
    }

    override suspend fun getClassicFormulaById(id: String): ClassicFormula? {
        return getFormulaCache().find { it.id == id }
    }

    override suspend fun translateFormula(formulaId: String): RagContext {
        val formula = getClassicFormulaById(formulaId)
        val content = formula?.originalText ?: "（暂无数据）"
        return RagPromptTemplates.classicFormulaTranslation(content)
    }

    // ─── 疾病 ──────────────────────────────────────────────────

    override suspend fun getDiseases(category: String?): List<DiseaseEntry> {
        val all = getDiseaseCache()
        if (category == null) return all
        return all.filter { it.tcmSyndrome.any { s -> s.contains(category) } }
    }

    override suspend fun getDiseaseByIcdCode(icdCode: String): DiseaseEntry? {
        return getDiseaseCache().find { it.icdCode == icdCode }
    }

    override suspend fun getDiseaseById(id: String): DiseaseEntry? {
        return getDiseaseCache().find { it.id == id }
    }

    // ─── 健康自测 ──────────────────────────────────────────────

    override suspend fun getQuizzes(): List<HealthQuiz> {
        return getQuizCache()
    }

    override suspend fun getQuizById(id: String): HealthQuiz? {
        return getQuizCache().find { it.id == id }
    }

    override suspend fun scoreQuiz(quizId: String, answers: List<Int>): ScoreRange {
        val quiz = getQuizById(quizId) ?: run {
            Timber.w("QwenContentRepository: quiz not found: $quizId")
            return ScoreRange(0, 100, "评估完成", "建议咨询专业医师获取详细解读")
        }

        // 计算总分
        val totalScore = answers.sum()

        // 根据量表评分区间匹配结果
        val matchedRange = quiz.scoring.ranges.find { range ->
            totalScore in range.minScore..range.maxScore
        }

        return matchedRange ?: ScoreRange(
            minScore = 0,
            maxScore = 100,
            label = "评估完成",
            advice = "您的总分为 $totalScore 分，建议咨询专业医师获取详细解读"
        )
    }

    // ─── 养生科普 ──────────────────────────────────────────────

    override suspend fun getArticles(solarTerm: String?, tags: List<String>?): List<HealthArticle> {
        var results = getArticleCache()

        if (solarTerm != null) {
            results = results.filter { it.solarTerm == solarTerm }
        }

        if (tags != null && tags.isNotEmpty()) {
            results = results.filter { article ->
                tags.any { tag -> article.tags.any { it.contains(tag) || tag.contains(it) } }
            }
        }

        return results
    }

    override suspend fun getArticleById(id: String): HealthArticle? {
        return getArticleCache().find { it.id == id }
    }

    override suspend fun generateSolarTermTip(solarTerm: String): RagContext {
        return RagPromptTemplates.solarTerm养生(solarTerm)
    }

    override suspend fun generateArticle(sourceContent: String): RagContext {
        return RagPromptTemplates.healthArticle(sourceContent)
    }

    // ─── 缓存加载（延迟加载 + 线程安全）────────────────────────

    private suspend fun getHerbCache(): List<HerbEntry> {
        cacheMutex.withLock {
            herbsCache?.let { return it }
            herbsCache = seedDataLoader.loadHerbs(context)
            Timber.d("QwenContentRepository: loaded ${herbsCache?.size ?: 0} herbs from seed data")
            return herbsCache ?: emptyList()
        }
    }

    private suspend fun getAcupointCache(): List<Acupoint> {
        cacheMutex.withLock {
            acupointsCache?.let { return it }
            acupointsCache = seedDataLoader.loadAcupoints(context)
            Timber.d("QwenContentRepository: loaded ${acupointsCache?.size ?: 0} acupoints from seed data")
            return acupointsCache ?: emptyList()
        }
    }

    private suspend fun getDietCache(): List<DietRecipe> {
        cacheMutex.withLock {
            dietRecipesCache?.let { return it }
            dietRecipesCache = seedDataLoader.loadDietRecipes(context)
            Timber.d("QwenContentRepository: loaded ${dietRecipesCache?.size ?: 0} diet recipes from seed data")
            return dietRecipesCache ?: emptyList()
        }
    }

    private suspend fun getFormulaCache(): List<ClassicFormula> {
        cacheMutex.withLock {
            formulasCache?.let { return it }
            formulasCache = seedDataLoader.loadFormulas(context)
            Timber.d("QwenContentRepository: loaded ${formulasCache?.size ?: 0} formulas from seed data")
            return formulasCache ?: emptyList()
        }
    }

    private suspend fun getQuizCache(): List<HealthQuiz> {
        cacheMutex.withLock {
            quizzesCache?.let { return it }
            quizzesCache = seedDataLoader.loadQuizzes(context)
            Timber.d("QwenContentRepository: loaded ${quizzesCache?.size ?: 0} quizzes from seed data")
            return quizzesCache ?: emptyList()
        }
    }

    private suspend fun getDiseaseCache(): List<DiseaseEntry> {
        cacheMutex.withLock {
            diseasesCache?.let { return it }
            diseasesCache = seedDataLoader.loadDiseases(context)
            Timber.d("QwenContentRepository: loaded ${diseasesCache?.size ?: 0} diseases from seed data")
            return diseasesCache ?: emptyList()
        }
    }

    private suspend fun getArticleCache(): List<HealthArticle> {
        cacheMutex.withLock {
            articlesCache?.let { return it }
            articlesCache = seedDataLoader.loadSolarTerms(context)
            Timber.d("QwenContentRepository: loaded ${articlesCache?.size ?: 0} articles from seed data")
            return articlesCache ?: emptyList()
        }
    }
}