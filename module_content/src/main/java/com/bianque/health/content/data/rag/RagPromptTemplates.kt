package com.bianque.health.content.data.rag

import com.bianque.health.content.domain.model.RagContext
import com.bianque.health.content.domain.model.RagDocument

/**
 * RAG 系统提示模板 — 权威数据库打底 + 大模型润色
 *
 * 混合策略：
 * - 地基：国家标准/公版古籍/药典结构化数据
 * - 应用层：大模型只做翻译/个性化推荐/通俗解读，绝不编造不存在的内容
 * - 强制约束：必须注明出处，添加免责声明，不知道就直说不知道
 */
object RagPromptTemplates {

    /** 系统提示词 — 通用模板 */
    fun buildSystemPrompt(
        knowledgeBase: String = "国家中医药管理局标准，GB/T 12346-2021《经穴名称与定位》，《中华人民共和国药典》，经典古籍公版数字化",
        constraints: List<String> = defaultConstraints
    ): String {
        return """
你是【扁鹊健康】APP 的中医健康助理，你的回答必须严格遵守以下规则：

# 核心规则
1. **只基于提供的知识库回答**，知识库中没有的内容，你必须回答「暂无权威数据支撑，建议咨询专业中医师」，禁止编造，禁止发散。
2. **必须注明数据来源**，例如「数据来源：GB/T 12346-2021《经穴名称与定位》」。
3. **必须添加免责声明**，结尾必须标注：「本内容仅供健康参考，不作为医疗诊断依据，身体不适请及时就医。」
4. **不做诊断**：你只提供知识科普和健康参考，不进行疾病诊断，不推荐处方药。

# 当前知识库来源
$knowledgeBase

# 约束条件
${constraints.joinToString("\n")}
        """.trimIndent()
    }

    /** 通用约束列表 */
    val defaultConstraints = listOf(
        "语言通俗易懂，适合普通用户阅读",
        "避免晦涩古文，必要时解释专业术语",
        "重点突出，条理清晰，分段论述",
        "不编造不存在的方剂、穴位、疾病",
        "涉及处方药和剂量时提醒「遵医嘱使用」"
    )

    /** 药膳食疗问答 — 结合体质诊断结果 */
    fun dietForConstitution(
        constitution: String,
        docs: List<RagDocument>
    ): RagContext {
        val content = docs.joinToString("\n---\n") { "[${it.source}]\n${it.content}" }
        val prompt = """
用户体质：$constitution
请从提供的知识库中筛选出适合该体质的食疗方子：
1. 每个方子说明适用理由
2. 列出食材和做法（严格依据知识库内容，不编造）
3. 说明禁忌人群
        """.trimIndent()

        return RagContext(
            query = prompt,
            retrievedDocs = docs,
            systemPrompt = buildSystemPrompt(
                knowledgeBase = "国家药典 + 经典食疗知识库",
                constraints = defaultConstraints + listOf(
                    "严格只推荐知识库中存在的方子，不要增加不存在的方子",
                    "说明为什么适合该体质，结合中医理论解释"
                )
            ),
            constraints = defaultConstraints
        )
    }

    /** 名方今译 — 古文→白话解读 */
    fun classicFormulaTranslation(formulaContent: String): RagContext {
        val doc = RagDocument(
            source = "经典古籍公版",
            content = formulaContent,
            relevance = 1f,
            verified = true
        )
        return RagContext(
            query = """
请将以下中医古方原文翻译成现代白话文：
1. 解释原文每一句话的意思
2. 说明该方剂在现代临床上的主要应用场景
3. 说明现代人使用的注意事项
4. 原文保留在开头
            """.trimIndent(),
            retrievedDocs = listOf(doc),
            systemPrompt = buildSystemPrompt(
                knowledgeBase = "经典古籍公版数字化",
                constraints = defaultConstraints + listOf(
                    "忠实原文，不添加原文没有的内容",
                    "通俗化翻译，不改变原意"
                )
            ),
            constraints = defaultConstraints
        )
    }

    /** 穴位查询 — 症状反查 */
    fun acupointQuery(symptom: String): RagContext {
        return RagContext(
            query = "用户有症状「$symptom」，请从提供的知识库中推荐适用的穴位，给出定位和按摩方法",
            retrievedDocs = emptyList(), // 由检索器填充
            systemPrompt = buildSystemPrompt(
                knowledgeBase = "GB/T 12346-2021《经穴名称与定位》",
                constraints = defaultConstraints + listOf(
                    "按穴位相关性排序，相关性低的不推荐",
                    "说明按摩手法和时长，明确禁忌"
                )
            ),
            constraints = defaultConstraints
        )
    }

    /** 中药鉴别 — 相似药材对比 */
    fun herbComparison(herbA: String, herbB: String, docs: List<RagDocument>): RagContext {
        val content = docs.joinToString("\n---\n") { "[${it.source}]\n${it.content}" }
        return RagContext(
            query = "请对比 $herbA 和 $herbB 的区别，包括来源、性味、功效、用法",
            retrievedDocs = docs,
            systemPrompt = buildSystemPrompt(
                knowledgeBase = "《中华人民共和国药典》中药材数据库",
                constraints = defaultConstraints + listOf(
                    "用表格对比差异，一目了然",
                    "提醒用户切勿自行混淆用药"
                )
            ),
            constraints = defaultConstraints
        )
    }

    /** 健康科普 — 通俗化加工 */
    fun healthArticle(sourceContent: String): RagContext {
        val doc = RagDocument(
            source = "权威健康指南",
            content = sourceContent,
            relevance = 1f,
            verified = true
        )
        return RagContext(
            query = """
请将以下专业内容加工为通俗易懂的大众科普文章：
1. 标题要吸引人，但不要标题党
2. 保留核心信息，去掉过于专业的术语，必要时解释
3. 分段清晰，重点加粗（用 markdown 格式）
4. 保留来源声明
            """.trimIndent(),
            retrievedDocs = listOf(doc),
            systemPrompt = buildSystemPrompt(
                knowledgeBase = "权威健康指南（《中国居民膳食指南》等）",
                constraints = defaultConstraints + listOf(
                    "保留核心数据和结论，不歪曲原意",
                    "生动有趣，适合手机阅读"
                )
            ),
            constraints = defaultConstraints
        )
    }

    /** 节气养生 */
    fun solarTerm养生(solarTerm: String): RagContext {
        return RagContext(
            query = "请生成一篇 $solarTerm 节气养生小贴士，包含：饮食建议、生活起居、运动建议",
            retrievedDocs = emptyList(),
            systemPrompt = buildSystemPrompt(
                knowledgeBase = "传统二十四节气养生知识库",
                constraints = defaultConstraints + listOf(
                    "贴近现代生活习惯，不推荐封建迷信",
                    "简洁实用，每条建议控制在20字以内"
                )
            ),
            constraints = defaultConstraints
        )
    }
}
