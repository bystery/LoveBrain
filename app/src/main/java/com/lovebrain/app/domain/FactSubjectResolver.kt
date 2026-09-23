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
 * P1-12: AI subject_candidate 已从 format.md 移除，不再请求模型生成。
 * 客户端通过 Level1/2 自行推导，无法确定时返回 UNKNOWN。
 */
object FactSubjectResolver {

    /**
     * 解析事实的 subject。
     *
     * @param factText 事实文本
     * @param speaker 已确定的说话人（由 FactSpeakerResolver 推导）
     * @param sourceIds 来源消息 ID
     * @param dialogue 冻结对话快照
     * @return EntityRef（HER / ME / UNKNOWN）
     */
    fun resolve(
        factText: String,
        speaker: EntityRef,
        sourceIds: List<String>,
        dialogue: List<DialogueMessage>
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
     * 不盲目用 startsWith("我") 判定为说话人自指。
     *
     * 核心问题：事实文本是模型按用户视角写的摘要（如"我昨天加班"），
     * 但模型可能是从对方的话推导出来的（如对方说"你昨天是不是加班了"）。
     * 如果 speaker=HER 且文本以"我"开头，旧代码会把 subject 判成 HER，
     * 但这里的"我"是用户视角的"我"，subject 应该是 ME。
     *
     * 新规则：
     * - speaker=HER + "我..." → subject=ME（模型按用户视角写）
     * - speaker=HER + "你..." → subject=HER（模型按对方视角写对方自己）
     * - speaker=ME + "我..." → subject=ME
     * - speaker=ME + "你..." → subject=HER
     * - 其他情况返回 null（不确定）
     *
     * 删掉 startsWith("我今天") 等冗余分支——startsWith("我") 已覆盖。
     */
    private fun resolveByPronoun(text: String, speaker: EntityRef): EntityRef? {
        val trimmed = text.trim()

        // 检查句首"我"——用户视角的"我"始终指向用户本人
        if (trimmed.startsWith("我")) {
            return when (speaker) {
                EntityRef.HER -> EntityRef.ME  // 模型按用户视角写"我"→subject=ME
                EntityRef.ME -> EntityRef.ME
                else -> null
            }
        }

        // 检查句首"你"——用户视角的"你"始终指向对方
        if (trimmed.startsWith("你")) {
            return when (speaker) {
                EntityRef.HER -> EntityRef.HER  // F11: 模型按用户视角写"你"→subject=HER
                EntityRef.ME -> EntityRef.HER
                else -> null
            }
        }

        return null
    }

    /**
     * Level 2: 简单实体规则。
     *
     * F11 修复：事实文本以"她"开头 → 描述对方，subject=HER。
     * 删掉旧代码中"事实文本以'我'开头且 speaker==HER → subject=ME"的逻辑——
     * 这已在 Level 1 的 resolveByPronoun 中正确处理。
     *
     * F11 新增：保守处理亲属、第三人、引语——不确定的返回 null，不进任何一方确定画像。
     */
    private fun resolveByEntityRule(
        factText: String,
        speaker: EntityRef,
        @Suppress("UNUSED_PARAMETER") sourceIds: List<String>,
        @Suppress("UNUSED_PARAMETER") dialogue: List<DialogueMessage>
    ): EntityRef? {
        val trimmed = factText.trim()

        // 事实文本以"她"开头 → 描述对方
        if (trimmed.startsWith("她")) return EntityRef.HER

        // F11: 亲属关系、第三人——不确定，不强行判定
        // "我妈住院了" → speaker=HER, 模型写"我妈住院了" → subject 不是 HER 也不是 ME
        // "你朋友考试" → subject 不是 HER 也不是 ME
        // 保守返回 null，由 Level 3 返回 UNKNOWN

        return null
    }
}
