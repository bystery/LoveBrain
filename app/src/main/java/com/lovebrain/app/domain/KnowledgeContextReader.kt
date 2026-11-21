package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.KnowledgeReadPort
import com.lovebrain.app.domain.prompt.CorrectionExtractionFilter
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.util.L

/**
 * 知识库读侧上下文装配的所有者——"要把哪些文件、按什么顺序拼给下游"。
 *
 * 变化理由：这两段文本是给经验提取（[KnowledgeTriggerCoordinator] 的 lesson 一路）
 * 和向量重估（vector 一路）用的输入，改的是**读取与排版**：取哪几份文件、
 * 分节标题怎么写、话题档案倒取几个。它不写任何文件，因此也不该和
 * 那些管落盘/事务的类住在一起——想调 prompt 上下文时不该翻 WAL 的判据。
 *
 * 从 [TopicRecorder] 拆出，分节文案与 `lastTopics` 的截断口径逐字保留。
 *
 *  F3.3：读源材料时统一消费纠正表——被纠正（WRONG/MUTED/FINISHED/WRONG_PERSON）的
 * 内容不再按原文回流进经验提取与向量重估，见 [correctionFiltered]。
 *
 *  F3.3 扩展（R4 覆盖面）：scene 纠正生效时，`moment/recent.md`、`memory/raw_chat.md`、
 * `memory/raw_scene.md`、`memory/raw_topic.md` 不再原样放行——按"已被纠正的条目原文"
 * 做条目级/行级精确匹配剔除（raw_scene 条目级、对话材料行级），剔除量只记数进日志，
 * 见 [CorrectionExtractionFilter.filterSceneArchive] / [filterChatArchive]。未被纠正时
 * 四份文件逐字放行。
 *
 *  S3 遗漏 B：对撞基准优先取持久依据（`memory/corrected_scene_facts.txt`，记忆格在
 * scene 纠正保存/撤销时写/清），归档后 scene.md 清空时依据不再消失；[persistedSceneFacts]。
 *
 * 端口收窄：本类只读不写，所以只依赖 [KnowledgeReadPort]——拿不到 deleteFile /
 * writeFile 那类它根本不该碰的写能力（与 [OngoingPlanStore] 把读、写两条门分别
 * 注入同一思路：端口只按调用方真正用到的开）。
 */
class KnowledgeContextReader(
    private val knowledgeRepo: KnowledgeReadPort
) {

    /** 获取经验提取的完整上下文：当前话题 + 场景链 + 最近对话 + 暂存 + 话题档案（最近N个） */
    internal suspend fun getTopicFullContext(kbName: String, topicCount: Int = AppConfig.VECTOR_CONTEXT_TOPICS): String {
        //  F3.3：提取链读源材料前先取纠正表——被纠正的内容不按原文回流进经验
        val corrections = knowledgeRepo.readCorrections(kbName)
        val topic = knowledgeRepo.getCurrentTopic(kbName)
        val sceneRaw = knowledgeRepo.readFile(kbName, "moment/scene.md")
        val sceneChain = correctionFiltered(kbName, MemoryKind.SCENE, "moment/scene.md", sceneRaw, corrections)
        //  F3.3 扩展：生效中的 scene 纠正 → "已被纠正的条目原文"对撞基准（recent / raw_* 的剔除基准）
        //  S3 遗漏 B：优先用持久依据（纠正产生时落盘的原文，归档后 scene.md 清空依据不再消失），
        //  scene.md 现算降级为旧数据兜底
        val correctedFacts = CorrectionExtractionFilter.correctedSceneFactTexts(
            kbName, sceneRaw, corrections, persistedSceneFacts(kbName)
        )
        val recent = chatFiltered(knowledgeRepo.readFile(kbName, "moment/recent.md"), correctedFacts)
        val rawChat = chatFiltered(knowledgeRepo.readFile(kbName, "memory/raw_chat.md"), correctedFacts)
        val rawScene = sceneFiltered(knowledgeRepo.readFile(kbName, "memory/raw_scene.md"), correctedFacts)
        val rawTopic = topicFiltered(knowledgeRepo.readFile(kbName, "memory/raw_topic.md"), correctedFacts)
        logRemovals("getTopicFullContext", listOf(
            "moment/recent.md" to recent.removed,
            "memory/raw_chat.md" to rawChat.removed,
            "memory/raw_scene.md" to rawScene.removed,
            "memory/raw_topic.md" to rawTopic.removed
        ))
        return buildString {
            append("当前话题：").append(topic as CharSequence).append("\n\n")
            if (sceneChain.isNotBlank()) {
                append("【此刻状态】\n").append(sceneChain.trim()).append("\n\n")
            }
            if (rawScene.text.isNotBlank()) {
                append("【状态暂存】\n").append(rawScene.text.trim()).append("\n\n")
            }
            if (rawChat.text.isNotBlank()) {
                append("【对话暂存】\n").append(rawChat.text.trim()).append("\n\n")
            }
            if (recent.text.isNotBlank()) {
                append("【最近对话】\n").append(recent.text.trim()).append("\n\n")
            }
            if (rawTopic.text.isNotBlank()) {
                append("【已结束话题（近期）】\n")
                append(lastTopics(rawTopic.text, topicCount))
                append("\n\n")
            }
        }
    }

    /** 向量重估专用上下文：当前话题 + 场景 + 最近对话 + 最近 N 个话题档案 */
    internal suspend fun getVectorContext(kbName: String): String {
        //  F3.3：向量重估同样消费纠正表（与 getTopicFullContext 同一颗过滤）
        val corrections = knowledgeRepo.readCorrections(kbName)
        val topic = knowledgeRepo.getCurrentTopic(kbName)
        val sceneRaw = knowledgeRepo.readFile(kbName, "moment/scene.md")
        val sceneChain = correctionFiltered(kbName, MemoryKind.SCENE, "moment/scene.md", sceneRaw, corrections)
        //  F3.3 扩展：与 getTopicFullContext 同一份对撞基准（含 S3 遗漏 B 的持久依据）
        val correctedFacts = CorrectionExtractionFilter.correctedSceneFactTexts(
            kbName, sceneRaw, corrections, persistedSceneFacts(kbName)
        )
        val recent = chatFiltered(knowledgeRepo.readFile(kbName, "moment/recent.md"), correctedFacts)
        val rawTopic = topicFiltered(knowledgeRepo.readFile(kbName, "memory/raw_topic.md"), correctedFacts)
        logRemovals("getVectorContext", listOf(
            "moment/recent.md" to recent.removed,
            "memory/raw_topic.md" to rawTopic.removed
        ))
        return buildString {
            append("当前话题：").append(topic as CharSequence).append("\n")
            if (sceneChain.isNotBlank()) {
                append("【此刻状态】\n").append(sceneChain.trim()).append("\n\n")
            }
            if (recent.text.isNotBlank()) {
                append("【最近对话】\n").append(recent.text.trim()).append("\n\n")
            }
            if (rawTopic.text.isNotBlank()) {
                append("【最近话题档案】\n")
                append(lastTopics(rawTopic.text, AppConfig.VECTOR_CONTEXT_TOPICS))
            }
        }
    }

    /** 从话题档案中倒取最近 N 个话题（按 H1 "# " 分割） */
    private fun lastTopics(text: String, count: Int): String {
        val valid = text.split(Regex("(?<=\\n)(?=# )")).map { it.trim() }.filter { it.startsWith("# ") }
        return if (valid.size <= count) {
            valid.joinToString("\n\n")
        } else {
            "…（更早的话题已省略）\n\n" + valid.takeLast(count).joinToString("\n\n")
        }
    }

    /**
     * 提取链源材料统一纠正过滤（ F3.3）。
     *
     * 可被纠正的源文件（kind:sourcePath 与注入侧 [com.lovebrain.app.domain.prompt.MemoryRefPolicy.makeRef]
     * 的 MemoryRef ID 逐字同源）在这里过 [CorrectionExtractionFilter]：
     * WRONG 带补正 → 只落 `（已纠正：…）` 标注；WRONG 无补正 / FINISHED / WRONG_PERSON /
     * MUTED 未过期 → 返回 null，调用侧按空白处理（本类原有的 isNotBlank 分支自然整段省略）；
     * MUTED 已过期 → 原文恢复。未被纠正的文件内容逐字不变。
     */
    private fun correctionFiltered(
        kbName: String,
        kind: MemoryKind,
        sourcePath: String,
        content: String,
        corrections: Map<String, com.lovebrain.app.model.MemoryCorrection>
    ): String = CorrectionExtractionFilter.filterForExtraction(kbName, kind, sourcePath, content, corrections) ?: ""

    /**
     * 对话归档 / 轮次块行级过滤（ F3.3 扩展）：只剔除整行等于
     * 「她：/我： + 已纠正事实原文」的对话行；无生效纠正时逐字放行。
     */
    private fun chatFiltered(
        content: String,
        correctedFacts: Set<String>
    ): CorrectionExtractionFilter.FilteredArchive =
        CorrectionExtractionFilter.filterChatArchive(content, correctedFacts)

    /** 场景条目归档条目级过滤（ F3.3 扩展）：只剔除事实原文精确命中的那一条事实。 */
    private fun sceneFiltered(
        content: String,
        correctedFacts: Set<String>
    ): CorrectionExtractionFilter.FilteredArchive =
        CorrectionExtractionFilter.filterSceneArchive(content, correctedFacts)

    /**
     * raw_topic 混合归档（状态段条目行 + 对话段自由行）过滤（ F3.3 扩展）：
     * 两颗过滤依次过——条目行只有 [CorrectionExtractionFilter.filterSceneArchive] 会动它，
     * 对话行只有 [CorrectionExtractionFilter.filterChatArchive] 会动它，互不重叠。
     * S3 遗漏 A：串联逻辑上收为 [CorrectionExtractionFilter.filterTopicArchive] 单一真源，
     * 这里只剩薄转发。
     */
    private fun topicFiltered(
        content: String,
        correctedFacts: Set<String>
    ): CorrectionExtractionFilter.FilteredArchive =
        CorrectionExtractionFilter.filterTopicArchive(content, correctedFacts)

    /**
     * 「已被纠正的条目原文」持久依据（ S3 遗漏 B）：scene 文件级纠正保存时由记忆格
     * 落进 `memory/corrected_scene_facts.txt`，归档后 scene.md 清空时过滤依据仍在这里。
     * 读不到（旧版本没写过）→ 空集，[CorrectionExtractionFilter.correctedSceneFactTexts]
     * 自动降级为 scene.md 现算兜底。只读不写——写入职责在记忆格的保存/撤销两格。
     */
    private suspend fun persistedSceneFacts(kbName: String): Set<String> =
        CorrectionExtractionFilter.decodePersistedFacts(
            knowledgeRepo.readFile(kbName, CorrectionExtractionFilter.PERSISTED_FACTS_PATH)
        )

    /** 剔除量记账（隐私红线：只记路径与条数，不记任何被剔除内容） */
    private fun logRemovals(context: String, perPath: List<Pair<String, Int>>) {
        val total = perPath.sumOf { it.second }
        if (total > 0) {
            L.w("F3.3 extraction filter [$context]: removed $total corrected-fact copies " +
                perPath.filter { it.second > 0 }.joinToString(", ") { "${it.first}=${it.second}" })
        }
    }
}
