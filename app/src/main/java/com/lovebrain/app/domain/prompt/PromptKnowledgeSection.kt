package com.lovebrain.app.domain.prompt

import com.lovebrain.app.domain.PromptBudget
import com.lovebrain.app.domain.SceneChainInjection
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef

/**
 * 回复系知识段装配（自 `domain/PromptBuilder.kt` 的 `buildKnowledgeInsertionWithRefs` 行为块搬出）。
 *
 * 这一块的职责是把**已经读出来的**画像 / 阶段 / 经验 / 场景 / 最近对话 / 进行中事项文本
 * 拼成回复 user 侧的知识段，并在注入时为每段生成稳定 [MemoryRef]、按 corrections 表过滤。
 * 它不读知识库、不读资产、不读时钟、不调 selector，也不持有任何状态——所有读取由
 * `PromptBuilder` 在调用前完成并以 [Input] 传入，因此是纯函数。搬运前后对同一批输入必须
 * 给出逐字相同的段文本与 MemoryRef 清单（字节证据见 `domain/prompt/PromptByteFreezeBaselineTest`
 * 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - 画像段（me/her/warmth/style）逐字同源，每段一条 PROFILE MemoryRef，纠正按文件级 ID 命中。
 * - 阶段节选由调用方用 `extractStageSection` 取好再传入；空则整段省略。
 * - 经验段过 `PromptBudget.lastH1Blocks(lessons, 3)` 后注入一条 LESSON MemoryRef。
 * - 进攻模式：`aggressiveText` 非空时以 `---` 围栏注入到记忆之后、此刻之前（字节级与旧实现一致）。
 * - 此刻段：topicAge/topic/sceneChain 三件，sceneChain 经 `SceneChainInjection.transform` 后注入 SCENE MemoryRef。
 * - 最近对话：原样 trim 追加。
 * - 进行中事项：按 `|` 分行，每条一条 ONGOING 条目级 MemoryRef；FINISHED 跳过活跃列表。
 */
object PromptKnowledgeSection {

    /** 调用方已读出的全部输入；任一字符串字段为空表示对应文件不存在/不注入 */
    data class Input(
        val kbName: String,
        val me: String,
        val her: String,
        val warmth: String,
        val style: String,
        val stageSection: String,
        val lessons: String,
        /** 非空表示进攻模式开启，内容为 aggressive.md 全文；空表示普通模式 */
        val aggressiveText: String,
        val topicAge: Int,
        val topic: String,
        val sceneChain: String,
        val recent: String,
        /** selector 已选出的进行中事项文本（`name | status | chain` 行）；空表示不注入 */
        val ongoingPlan: String,
        val corrections: Map<String, MemoryCorrection>
    )

    /** 装配结果：段文本 + 注入时收集的 MemoryRef 清单 */
    data class Output(val text: String, val refs: List<MemoryRef>)

    /** 装配回复系知识段并收集 MemoryRef；纠正记录在注入前过滤 */
    fun build(input: Input): Output {
        val sb = StringBuilder()
        val refs = mutableListOf<MemoryRef>()
        val corrections = input.corrections

        // 画像段（me/her/warmth/style 各一条 MemoryRef）
        sb.append("# 【懂得】关系画像\n")
        appendProfileFile(sb, refs, input.kbName, MemoryKind.PROFILE, "understand/me.md", "## 我", input.me, corrections)
        appendProfileFile(sb, refs, input.kbName, MemoryKind.PROFILE, "understand/her.md", "## 她", input.her, corrections)
        appendProfileFile(sb, refs, input.kbName, MemoryKind.PROFILE, "understand/warmth.md", "## 我们", input.warmth, corrections)
        appendProfileFile(sb, refs, input.kbName, MemoryKind.PROFILE, "understand/style.md", "## 我的表达偏好", input.style, corrections)
        sb.append("\n")

        // 阶段节选
        if (input.stageSection.isNotBlank()) {
            sb.append("## 当前阶段策略（仅提取当前阶段，严格遵守；不是当前阶段的内容一律忽略）\n")
            sb.append(input.stageSection)
            sb.append("\n\n")
        }

        // 经验段
        if (input.lessons.isNotBlank()) {
            val lessonText = PromptBudget.lastH1Blocks(input.lessons, 3)
            val ref = MemoryRefPolicy.makeRef(input.kbName, MemoryKind.LESSON, "memory/lessons.md", lessonText)
            if (!MemoryRefPolicy.isCorrected(ref.id, corrections, sb)) {
                sb.append("# 【记忆】经验教训（仅供参考）\n")
                sb.append(lessonText).append("\n\n")
                refs.add(ref)
            }
        }

        // 进攻模式
        if (input.aggressiveText.isNotBlank()) {
            sb.append("\n\n---\n\n")
            sb.append(input.aggressiveText)
            sb.append("\n\n---\n\n")
        }

        // 场景段
        sb.append("# 【此刻】场景上下文（仅供参考，以本次对话为准）\n")
        val topicAge = input.topicAge
        if (topicAge < 99) {
            if (topicAge < 1) sb.append("距上次对话：不到1小时前\n")
            else {
                sb.append("距上次对话：约").append(topicAge).append("小时前")
                if (topicAge > 4) sb.append("（间隔较久，话题可能已切换）")
                sb.append("\n")
            }
        }
        val topic = input.topic
        if (topic.isNotBlank() && topic != "（等待第一次对话）") {
            sb.append("当前话题：").append(topic)
            if (topicAge > 6) sb.append("（⚠️ 此信息来自").append(topicAge).append("小时前，可能已过时）")
            sb.append("\n")
        }
        if (input.sceneChain.isNotBlank()) {
            val transformed = SceneChainInjection.transform(input.sceneChain)
            if (transformed.isNotBlank()) {
                val ref = MemoryRefPolicy.makeRef(input.kbName, MemoryKind.SCENE, "moment/scene.md", transformed)
                if (!MemoryRefPolicy.isCorrected(ref.id, corrections, sb)) {
                    sb.append("## 场景状态链（条目后括号内为距今时间；同一事实只在最新条目保留一次）\n")
                        .append(transformed).append("\n")
                    refs.add(ref)
                }
            }
        }
        sb.append("\n")

        // 最近对话
        if (input.recent.isNotBlank()) sb.append("# 最近对话\n").append(input.recent.trim()).append("\n\n")

        // 进行中事项段
        if (input.ongoingPlan.isNotBlank()) {
            val planLines = input.ongoingPlan.lines().filter { it.contains("|") }
            for (line in planLines) {
                val parts = line.split("|").map { it.trim() }
                if (parts.size >= 2 && parts[0].isNotBlank()) {
                    val tildeIdx = parts[0].indexOf('~')
                    val entryName = if (tildeIdx > 0) parts[0].substring(tildeIdx + 1) else parts[0]
                    val ref = MemoryRefPolicy.makeOngoingEntryRef(input.kbName, entryName, line)
                    val correction = corrections[ref.id]
                    if (correction?.action == CorrectionAction.FINISHED) {
                        // 事项已结束，不注入活跃列表
                    } else if (!MemoryRefPolicy.isCorrected(ref.id, corrections, sb)) {
                        if (!sb.contains("# 【进行中事项】")) {
                            sb.append("# 【进行中事项】（长期追踪，仅在与当前对话相关时提及，不必每条都提）\n")
                        }
                        sb.append(line).append("\n")
                        refs.add(ref)
                    }
                }
            }
        }

        return Output(sb.toString(), refs)
    }

    /** 画像单文件注入：非空且未被纠正则追加 `## 标题\n正文` 并登记 PROFILE MemoryRef */
    private fun appendProfileFile(
        sb: StringBuilder,
        refs: MutableList<MemoryRef>,
        kbName: String,
        kind: MemoryKind,
        sourcePath: String,
        title: String,
        content: String,
        corrections: Map<String, MemoryCorrection>
    ) {
        if (content.isBlank()) return
        val ref = MemoryRefPolicy.makeRef(kbName, kind, sourcePath, content)
        if (!MemoryRefPolicy.isCorrected(ref.id, corrections, sb)) {
            sb.append(title).append("\n").append(content.trim()).append("\n")
            refs.add(ref)
        }
    }
}
