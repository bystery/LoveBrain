package com.lovebrain.app.domain

import com.lovebrain.app.model.DialogueMessage
import com.lovebrain.app.model.DialogueSpeaker
import com.lovebrain.app.model.EntityRef

/**
 * P0-4: 事实主体解析器——subject 与 speaker 分离。
 *
 * subject 表示事实描述的对象（谁的事），不同于 speaker（谁说的）。
 *
 * 三层解析：
 * - Level 1: 代码可确定——当前消息 speaker + 明确代词"我/你"
 * - Level 2: 实体规则——显式名字、人称引用目标
 * - Level 3: 语义不确定——UNKNOWN（宁可 UNKNOWN，不猜）
 *
 * subject 不无条件信 AI。
 * 封板期：AI subject_candidate 暂不参与解析——Level1/2 无法确定时直接返回 UNKNOWN。
 */
object FactSubjectResolver {

    /**
     * 解析事实的 subject。
     *
     * @param factText 事实文本
     * @param speaker 已确定的说话人（由 FactSpeakerResolver 推导）
     * @param sourceIds 来源消息 ID
     * @param dialogue 冻结对话快照
     * @param subjectCandidate AI 提供的主体候选（PARTNER/USER/UNKNOWN），作为 Level 3 低优先级证据
     * @return EntityRef（HER / ME / UNKNOWN）
     */
    fun resolve(
        factText: String,
        speaker: EntityRef,
        sourceIds: List<String>,
        dialogue: List<DialogueMessage>,
        subjectCandidate: String = ""
    ): EntityRef {
        // Level 1: 代码可确定——基于 speaker + 代词
        val level1 = resolveByPronoun(factText, speaker)
        if (level1 != null) return level1

        // Level 2: 简单实体规则（未来可扩展）
        val level2 = resolveByEntityRule(factText, speaker, sourceIds, dialogue)
        if (level2 != null) return level2

        // Level 3: 封板期保守策略——不信任 AI subject_candidate。
        // 旧代码将 PARTNER→HER / USER→ME 直接映射，但 sourceIds 和 dialogue 未被用于验证，
        // 等于"枚举值合法就自动相信"，可能制造事实污染。
        // 封板期：Level1/2 无法确定 → UNKNOWN（宁可少记，不要错记）。
        // 未来可通过 sourceIds + dialogue 验证 candidate 后恢复 Level 3。
        return EntityRef.UNKNOWN
    }

    /**
     * Level 1: 基于 speaker + 代词推导 subject。
     *
     * PARTNER 说"我..." → subject = HER（说话人在说自己）
     * PARTNER 说"你..." → subject = ME（说话人在说用户）
     * USER 说"我..." → subject = ME
     * USER 说"你..." → subject = HER
     *
     * 只在句首或明确主语位置匹配，避免误命中。
     */
    private fun resolveByPronoun(text: String, speaker: EntityRef): EntityRef? {
        val trimmed = text.trim()

        // 检查句首"我"——说话人在说自己
        if (trimmed.startsWith("我") || trimmed.startsWith("我今天") ||
            trimmed.startsWith("我昨") || trimmed.startsWith("我刚") ||
            trimmed.startsWith("我准备") || trimmed.startsWith("我在") ||
            trimmed.startsWith("我要") || trimmed.startsWith("我不") ||
            trimmed.startsWith("我没") || trimmed.startsWith("我去")) {
            return when (speaker) {
                EntityRef.HER -> EntityRef.HER
                EntityRef.ME -> EntityRef.ME
                else -> null
            }
        }

        // 检查句首"你"——说话人在说对方
        if (trimmed.startsWith("你") || trimmed.startsWith("你今天") ||
            trimmed.startsWith("你昨") || trimmed.startsWith("你刚") ||
            trimmed.startsWith("你准备") || trimmed.startsWith("你在") ||
            trimmed.startsWith("你要") || trimmed.startsWith("你不") ||
            trimmed.startsWith("你没") || trimmed.startsWith("你去") ||
            trimmed.startsWith("你感冒") || trimmed.startsWith("你好")) {
            return when (speaker) {
                EntityRef.HER -> EntityRef.ME
                EntityRef.ME -> EntityRef.HER
                else -> null
            }
        }

        return null
    }

    /**
     * Level 2: 简单实体规则。
     *
     * 事实文本中明确提到"她"或"我"作为主语（非句首代词场景）。
     * 例如："她喜欢猫" → subject = HER
     */
    private fun resolveByEntityRule(
        factText: String,
        speaker: EntityRef,
        sourceIds: List<String>,
        dialogue: List<DialogueMessage>
    ): EntityRef? {
        val trimmed = factText.trim()

        // 事实文本以"她"开头且 speaker 不是 HER → 描述对方
        if (trimmed.startsWith("她")) return EntityRef.HER

        // 事实文本以"我"开头且 speaker 是 HER → 描述用户
        // （Level 1 已处理 speaker 自指场景，这里处理第三人称叙述）
        if (trimmed.startsWith("我") && speaker == EntityRef.HER) return EntityRef.ME

        return null
    }
}
