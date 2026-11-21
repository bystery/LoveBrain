package com.lovebrain.app.domain.prompt

import com.lovebrain.app.domain.SceneChainInjection
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef

/**
 * 回复系知识段装配（2026-11-21 提示词结构收敛后的目标形状）。
 *
 * 目标结构：
 * ```
 * # 【懂得】关系画像
 *   ## 我
 *   ## 她
 * ## 当前阶段策略（stage.md 截取）
 * # 【此刻】场景上下文（当前话题 / 场景状态链）
 * [冠军历史块：逐字整块，独占原 recent 槽]
 * ```
 *
 * 语义约束：
 * - 画像只留「我 / 她」；「我们（warmth）」与「我的表达偏好（style）」已按用户拍板删除。
 * - 经验教训、进行中事项、「# 最近对话」块与「距上次对话」行全部删除——
 *   这些内容不再由本段渲染，`PromptBuilder` 也不再从知识库读取对应文件。
 * - 阶段节选由调用方用 `extractStageSection` 取好再传入；空则整段省略。
 * - 此刻段只保留「当前话题」与「场景状态链」；sceneChain 经 `SceneChainInjection.transform`
 *   后注入 SCENE MemoryRef。龄标注的时间由调用方从 `Clock` 端口取（[Input.nowMs]），
 *   本段不读系统钟——否则 FixedClock 冻不住"跨 2h 过期"这类分支。
 * - **空段不出标题**：画像/此刻整段没有可注入内容时，连段头一起省略；留一个空段头
 *   会让模型去找那段并不存在的内容。
 * - [Input.historicalBlock] 是冠军链路的真实历史块（3600 字逐字），渲染在「此刻」之后，
 *   即原来 recent.md 的槽位。它**只整块保留或整块丢弃**：本段原样渲染，不做任何裁剪；
 *   预算层（[com.lovebrain.app.domain.PromptBudget.byBlocks]）通过 [Output.historicalBlock]
 *   认出这段尾部字节并禁止对它按字符/按行截断。
 */
object PromptKnowledgeSection {

    /** 画像段头（`PromptBudget` 不依赖它，但阈值/顺序真源在这里） */
    const val PROFILE_HEADER = "# 【懂得】关系画像"

    /** 此刻段头；`PromptBudget.shrinkKnowledge` 用它在裁剪时优先保住「此刻」 */
    const val MOMENT_HEADER = "# 【此刻】场景上下文（仅供参考，以本次对话为准）"

    /** 调用方已读出的全部输入；任一字符串字段为空表示对应文件不存在/不注入 */
    data class Input(
        val kbName: String,
        val me: String,
        val her: String,
        val stageSection: String,
        val topic: String,
        val sceneChain: String,
        val corrections: Map<String, MemoryCorrection>,
        /** 场景链条目龄标注的"现在"（毫秒）——来自调用方的 Clock 端口，不在此处读系统钟 */
        val nowMs: Long,
        /** 场景链条目的"今天"（yyyy-MM-dd）——同样来自 Clock 端口 */
        val today: String,
        /** 冠军真实历史块（逐字；空 = 不注入，不出空标题） */
        val historicalBlock: String = ""
    )

    /**
     * 装配结果：段文本 + 注入时收集的 MemoryRef 清单 + 历史块在 [text] 里的逐字尾部副本。
     *
     * [historicalBlock] 为空串表示没有历史块；非空时 `text.endsWith(historicalBlock)` 恒成立，
     * 预算层据此把这部分当受保护段（整块留/整块丢），其余知识段照原规则裁剪。
     */
    data class Output(
        val text: String,
        val refs: List<MemoryRef>,
        val historicalBlock: String = ""
    )

    /** 装配回复系知识段并收集 MemoryRef；纠正记录在注入前过滤 */
    fun build(input: Input): Output {
        val sb = StringBuilder()
        val refs = mutableListOf<MemoryRef>()
        val corrections = input.corrections

        // 画像段：只留我 / 她（warmth / style 已随结构收敛删除）。
        // 先装配正文再决定要不要段头——me/her 全空或全被纠正时不留空标题。
        val profile = StringBuilder()
        appendProfileFile(profile, refs, input.kbName, MemoryKind.PROFILE, "understand/me.md", "## 我", input.me, corrections)
        appendProfileFile(profile, refs, input.kbName, MemoryKind.PROFILE, "understand/her.md", "## 她", input.her, corrections)
        if (profile.isNotEmpty()) {
            sb.append(PROFILE_HEADER).append("\n")
            sb.append(profile)
            sb.append("\n")
        }

        // 阶段节选
        if (input.stageSection.isNotBlank()) {
            sb.append("## 当前阶段策略（仅提取当前阶段，严格遵守；不是当前阶段的内容一律忽略）\n")
            sb.append(input.stageSection)
            sb.append("\n\n")
        }

        // 此刻段：当前话题 / 场景状态链（距上次对话已删除）
        val moment = StringBuilder()
        val topic = input.topic
        if (topic.isNotBlank() && topic != TopicRecorder.TOPIC_INITIAL_LABEL) {
            moment.append("当前话题：").append(topic).append("\n")
        }
        if (input.sceneChain.isNotBlank()) {
            val transformed = SceneChainInjection.transform(input.sceneChain, input.nowMs, input.today)
            if (transformed.isNotBlank()) {
                val ref = MemoryRefPolicy.makeRef(input.kbName, MemoryKind.SCENE, "moment/scene.md", transformed)
                if (!MemoryRefPolicy.isCorrected(ref.id, corrections, moment)) {
                    moment.append("## 场景状态链（条目后括号内为距今时间；同一事实只在最新条目保留一次）\n")
                        .append(transformed).append("\n")
                    refs.add(ref)
                }
            }
        }
        if (moment.isNotEmpty()) {
            sb.append(MOMENT_HEADER).append("\n")
            sb.append(moment)
            sb.append("\n")
        }

        // 原 recent 槽：冠军历史块独立整块（逐字，不做内部裁剪）
        var historicalBlock = ""
        if (input.historicalBlock.isNotBlank()) {
            val start = sb.length
            sb.append(input.historicalBlock)
            if (!input.historicalBlock.endsWith("\n")) sb.append("\n")
            historicalBlock = sb.substring(start)
        }

        return Output(sb.toString(), refs, historicalBlock)
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
