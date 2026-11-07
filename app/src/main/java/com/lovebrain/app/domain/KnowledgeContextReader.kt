package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.KnowledgeReadPort
import com.lovebrain.app.domain.prompt.CorrectionExtractionFilter
import com.lovebrain.app.model.MemoryKind

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
        val sceneChain = correctionFiltered(kbName, MemoryKind.SCENE, "moment/scene.md",
            knowledgeRepo.readFile(kbName, "moment/scene.md"), corrections)
        val recent = knowledgeRepo.readFile(kbName, "moment/recent.md")
        val rawChat = knowledgeRepo.readFile(kbName, "memory/raw_chat.md")
        val rawScene = knowledgeRepo.readFile(kbName, "memory/raw_scene.md")
        val rawTopic = knowledgeRepo.readFile(kbName, "memory/raw_topic.md")
        return buildString {
            append("当前话题：").append(topic as CharSequence).append("\n\n")
            if (sceneChain.isNotBlank()) {
                append("【此刻状态】\n").append(sceneChain.trim()).append("\n\n")
            }
            if (rawScene.isNotBlank()) {
                append("【状态暂存】\n").append(rawScene.trim()).append("\n\n")
            }
            if (rawChat.isNotBlank()) {
                append("【对话暂存】\n").append(rawChat.trim()).append("\n\n")
            }
            if (recent.isNotBlank()) {
                append("【最近对话】\n").append(recent.trim()).append("\n\n")
            }
            if (rawTopic.isNotBlank()) {
                append("【已结束话题（近期）】\n")
                append(lastTopics(rawTopic, topicCount))
                append("\n\n")
            }
        }
    }

    /** 向量重估专用上下文：当前话题 + 场景 + 最近对话 + 最近 N 个话题档案 */
    internal suspend fun getVectorContext(kbName: String): String {
        //  F3.3：向量重估同样消费纠正表（与 getTopicFullContext 同一颗过滤）
        val corrections = knowledgeRepo.readCorrections(kbName)
        val topic = knowledgeRepo.getCurrentTopic(kbName)
        val sceneChain = correctionFiltered(kbName, MemoryKind.SCENE, "moment/scene.md",
            knowledgeRepo.readFile(kbName, "moment/scene.md"), corrections)
        val recent = knowledgeRepo.readFile(kbName, "moment/recent.md")
        val rawTopic = knowledgeRepo.readFile(kbName, "memory/raw_topic.md")
        return buildString {
            append("当前话题：").append(topic as CharSequence).append("\n")
            if (sceneChain.isNotBlank()) {
                append("【此刻状态】\n").append(sceneChain.trim()).append("\n\n")
            }
            if (recent.isNotBlank()) {
                append("【最近对话】\n").append(recent.trim()).append("\n\n")
            }
            if (rawTopic.isNotBlank()) {
                append("【最近话题档案】\n")
                append(lastTopics(rawTopic, AppConfig.VECTOR_CONTEXT_TOPICS))
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
     * 当前两类读取材料里只有 scene.md 有可纠正身份（recent / raw_* / 当前话题无 MemoryRef，
     * 纠正表不可能命中它们，原样读取）。
     */
    private fun correctionFiltered(
        kbName: String,
        kind: MemoryKind,
        sourcePath: String,
        content: String,
        corrections: Map<String, com.lovebrain.app.model.MemoryCorrection>
    ): String = CorrectionExtractionFilter.filterForExtraction(kbName, kind, sourcePath, content, corrections) ?: ""
}
