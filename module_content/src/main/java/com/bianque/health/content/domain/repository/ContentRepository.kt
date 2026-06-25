package com.bianque.health.content.domain.repository

import com.bianque.health.content.data.rag.RagPromptTemplates
import com.bianque.health.content.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 内容知识库仓库接口
 *
 * 第一阶段（冷启动）：人工整理 Excel 数据导入
 * 第二阶段（LLM接入）：RAG 检索 + LLM 润色
 * 第三阶段（UGC）：用户反馈 + 反向补充
 */
interface ContentRepository {
    // ── 药膳食疗 ──
    suspend fun getDietRecipes(constitution: String? = null): List<DietRecipe>
    suspend fun getDietRecipeById(id: String): DietRecipe?
    suspend fun generateDietRecommendation(constitution: String): RagContext

    // ── 经络穴位 ──
    suspend fun getAcupoints(meridian: String? = null): List<Acupoint>
    suspend fun getAcupointById(id: String): Acupoint?
    suspend fun searchAcupointsBySymptom(symptom: String): RagContext
    suspend fun getMeridians(): List<String>

    // ── 中药图鉴 ──
    suspend fun getHerbs(family: String? = null): List<HerbEntry>
    suspend fun getHerbById(id: String): HerbEntry?
    suspend fun compareHerbs(herbA: String, herbB: String): RagContext

    // ── 名方今用 ──
    suspend fun getClassicFormulas(source: String? = null): List<ClassicFormula>
    suspend fun getClassicFormulaById(id: String): ClassicFormula?
    suspend fun translateFormula(formulaId: String): RagContext

    // ── 疾病 ──
    suspend fun getDiseases(category: String? = null): List<DiseaseEntry>
    suspend fun getDiseaseByIcdCode(icdCode: String): DiseaseEntry?
    suspend fun getDiseaseById(id: String): DiseaseEntry?

    // ── 健康自测 ──
    suspend fun getQuizzes(): List<HealthQuiz>
    suspend fun getQuizById(id: String): HealthQuiz?
    suspend fun scoreQuiz(quizId: String, answers: List<Int>): ScoreRange

    // ── 养生科普 ──
    suspend fun getArticles(solarTerm: String? = null, tags: List<String>? = null): List<HealthArticle>
    suspend fun getArticleById(id: String): HealthArticle?
    suspend fun generateSolarTermTip(solarTerm: String): RagContext
    suspend fun generateArticle(sourceContent: String): RagContext
}