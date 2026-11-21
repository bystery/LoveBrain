package com.lovebrain.app.feature.composer

import com.lovebrain.app.model.ChatMessage

/**
 * 批量导入的一次提交项（Issue #7 / 外部审计 F3）：解析后交给
 * `LoveBrainViewModel.importBatchMessages`，整批走 `ComposerStore.Intent.AddMessages`
 * （一次校验、一次追加、一次通知）——与手工输入/自动捕获**同一条真实对话通道**（HER/ME 消息列表 →
 * `GenerationInput.dialogue`），绝不碰军师备注/IDEA 通道（KNOWN_FAILURES F1/F3 边界）。
 *
 * 没有预览、没有二次确认：轻点 ➕ 时 A 路（空行分块的转录）全部条目用输入框当前角色，
 * 这里只承接已解析好的单条正文，不参与任何角色推断。
 *
 * [role] 在构造期硬挡 `Role.IDEA`：备注不是一种消息角色，"批量导入"更没有资格
 * 从旁路把内容灌进 replyDirective。传 IDEA 会在构造期抛 [IllegalArgumentException]
 * （`require` 是运行期检查），而不是运行时悄悄改道。
 */
data class BatchImportDraft(
    val role: ChatMessage.Role,
    val content: String
) {
    init {
        require(role != ChatMessage.Role.IDEA) {
            "BatchImportDraft 只允许 HER/ME（真实对话通道）；IDEA 是军师备注通道，禁止混入"
        }
        require(content.isNotBlank()) { "BatchImportDraft 不接受空正文" }
    }
}
