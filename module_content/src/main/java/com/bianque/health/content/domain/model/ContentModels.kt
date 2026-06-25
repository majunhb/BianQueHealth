package com.bianque.health.content.domain.model

// ─── 药膳食疗 ──────────────────────────────────────────────────
data class DietRecipe(
    val id: String,
    val name: String,               // 方名
    val source: String,             // 出处（伤寒论/本草纲目/药典）
    val sourceVerified: Boolean,    // 是否经人工核对
    val constitutionFit: List<String>, // 适用体质
    val ingredients: List<Ingredient>, // 食材
    val steps: List<String>,        // 制作步骤
    val efficacy: String,           // 功效
    val contraindications: String,  // 禁忌
    val llmGenerated: Boolean = false,
    val llmPrompt: String? = null
)

data class Ingredient(
    val name: String,
    val dosage: String,             // 剂量
    val nature: String,             // 性味
    val meridian: String,           // 归经
    val note: String? = null        // 备注
)

// ─── 经络穴位 ──────────────────────────────────────────────────
data class Acupoint(
    val id: String,
    val name: String,               // 穴位名
    val pinyin: String,             // 拼音
    val gbCode: String,             // GB/T 12346-2021 标准编码
    val meridian: String,           // 所属经络
    val location: String,           // 定位描述
    val location3D: FloatArray,     // 3D模型坐标 [x, y, z]
    val indications: List<String>,  // 主治
    val massageMethod: String,      // 按摩手法
    val massageDuration: String,    // 建议时长
    val caution: String? = null     // 注意事项
)

// ─── 中药图鉴 ──────────────────────────────────────────────────
data class HerbEntry(
    val id: String,
    val name: String,               // 通用名
    val latinName: String,          // 拉丁学名
    val family: String,             // 科属
    val medicinalPart: String,      // 药用部位
    val nature: String,             // 性味
    val meridian: String,           // 归经
    val efficacy: String,           // 功效
    val dosage: String,             // 用量
    val imageUrl: String? = null,   // 高清图片URL
    val similarHerbs: List<String>, // 易混淆药材
    val mnemonic: String? = null    // 鉴别口诀（LLM生成）
)

// ─── 名方今用 ──────────────────────────────────────────────────
data class ClassicFormula(
    val id: String,
    val name: String,               // 方名
    val source: String,             // 出处
    val originalText: String,       // 原文
    val modernTranslation: String,  // 今译（LLM生成）
    val composition: List<Ingredient>, // 组成
    val indications: String,        // 原方适应症
    val modernUse: String,          // 现代应用（LLM生成）
    val modernExplanation: String,  // 现代解读（LLM生成）
    val suitability: String,        // 适用人群（LLM生成）
    val llmGenerated: Boolean = false
)

// ─── 疾病 ──────────────────────────────────────────────────────
data class DiseaseEntry(
    val id: String,
    val icdCode: String,            // ICD-11 编码
    val name: String,               // 病名
    val tcmName: String,            // 中医对应病名
    val overview: String,           // 概述
    val symptoms: List<String>,     // 症状
    val tcmSyndrome: List<String>,  // 中医证型
    val prevention: String,         // 预防
    val whenToSeeDoctor: String,    // 就医指征
    val disclaimer: String = "本内容仅供健康参考，不作为医疗诊断依据"
)

// ─── 健康自测 ──────────────────────────────────────────────────
data class HealthQuiz(
    val id: String,
    val title: String,
    val description: String,
    val source: String,             // 量表来源（PHQ-9/GAD-7/中医体质分类）
    val questions: List<QuizQuestion>,
    val scoring: QuizScoring
)

data class QuizQuestion(
    val id: Int,
    val text: String,
    val options: List<String>,      // 选项
    val scores: List<Int>           // 对应分值
)

data class QuizScoring(
    val ranges: List<ScoreRange>,
    val generatedAdvice: Boolean = true // 是否由LLM生成建议
)

data class ScoreRange(
    val minScore: Int,
    val maxScore: Int,
    val label: String,
    val advice: String              // LLM生成建议
)

// ─── 养生科普 ──────────────────────────────────────────────────
data class HealthArticle(
    val id: String,
    val title: String,              // 爆款标题（LLM生成）
    val subtitle: String,
    val source: String,             // 权威来源
    val solarTerm: String?,         // 相关节气
    val content: String,            // 正文（LLM润色）
    val tags: List<String>,
    val isSeasonal: Boolean,        // 是否节气相关
    val llmGenerated: Boolean = false
)

// ─── RAG 上下文 ────────────────────────────────────────────────
data class RagContext(
    val query: String,
    val retrievedDocs: List<RagDocument>,
    val systemPrompt: String,
    val constraints: List<String>   // 约束条件
)

data class RagDocument(
    val source: String,             // 知识库来源
    val content: String,            // 检索到的内容
    val relevance: Float,           // 相关性分数
    val verified: Boolean           // 是否人工核对
)