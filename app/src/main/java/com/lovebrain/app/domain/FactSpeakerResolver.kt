package com.lovebrain.app.domain

import com.lovebrain.app.model.DialogueMessage
import com.lovebrain.app.model.DialogueSpeaker
import com.lovebrain.app.model.EntityRef

/**
 * 事实说话人推导器——从 source_ids 确定性推导 speaker。
 *
 * 核心原则：speaker 绝不交给 AI 判断。
 * source_id → DialogueMessage → speaker 是 100% 确定的。
 *
 * 规则：
 * - 所有有效 source IDs 都是 PARTNER → speaker = HER
 * - 所有有效 source IDs 都是 USER → speaker = ME
 * - 来源混合 → speaker = MULTIPLE
 * - 无可靠来源 → speaker = UNKNOWN
 */
object FactSpeakerResolver {

    /**
     * 从 source_ids 和冻结消息快照推导 speaker。
     *
     * @param sourceIds 事实引用的来源消息 ID（已解析为实际消息 ID）
     * @param dialogue 冻结的对话消息快照
     * @return 确定性的 EntityRef（HER / ME / MULTIPLE / UNKNOWN）
     */
    fun resolve(sourceIds: List<String>, dialogue: List<DialogueMessage>): EntityRef {
        if (sourceIds.isEmpty()) return EntityRef.UNKNOWN

        val speakers = sourceIds
            .mapNotNull { id -> dialogue.find { it.id == id }?.speaker }
            .toSet()

        if (speakers.isEmpty()) return EntityRef.UNKNOWN
        if (speakers.size == 1) {
            return when (speakers.first()) {
                DialogueSpeaker.PARTNER -> EntityRef.HER
                DialogueSpeaker.USER -> EntityRef.ME
            }
        }
        return EntityRef.MULTIPLE
    }

    /**
     * 兼容旧接口：从 ChatMessage 列表推导 speaker。
     * 使用 validSourceMap (id → role) 进行推导。
     */
    fun resolveFromRoles(sourceIds: List<String>, validSourceMap: Map<String, com.lovebrain.app.model.ChatMessage.Role>): EntityRef {
        if (sourceIds.isEmpty()) return EntityRef.UNKNOWN

        val roles = sourceIds
            .mapNotNull { id -> validSourceMap[id] }
            .toSet()

        if (roles.isEmpty()) return EntityRef.UNKNOWN
        if (roles.size == 1) {
            return when (roles.first()) {
                com.lovebrain.app.model.ChatMessage.Role.HER -> EntityRef.HER
                com.lovebrain.app.model.ChatMessage.Role.ME -> EntityRef.ME
                else -> EntityRef.UNKNOWN
            }
        }
        return EntityRef.MULTIPLE
    }
}
