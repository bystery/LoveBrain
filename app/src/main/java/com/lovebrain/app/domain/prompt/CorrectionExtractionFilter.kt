package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MemoryKind

/**
 * 提取链统一纠正过滤（ F3.3：纠错后旧值在摘要中回流）。
 *
 * 背景：纠正（corrections 表）此前只在**注入侧**生效（[MemoryRefPolicy] +
 * `PromptKnowledgeSection` 注入前过滤）；后台提取链——经验提取 / 画像 reflect /
 * 向量重估——读源材料时完全不消费纠正表，被用户纠正过的旧内容仍按原文回流进
 * 经验与画像，静默覆盖用户纠正。
 *
 * 这一格的职责只有一件事：提取链在读取源材料时，按 [MemoryRefPolicy] **既有的**
 * 纠正语义（WRONG / MUTED / FINISHED / WRONG_PERSON，含 MUTED 过期恢复与旧 ID 回退
 * 匹配）决定该段内容是原样放行、标注纠正值、还是整段排除。不发明新状态、不复述
 * 判据——跳不跳过这一颗判定只问 [MemoryRefPolicy.isCorrected]。
 *
 * 语义约束：
 * - 未被纠正 → 原文逐字放行（空串也原样返回）。
 * - WRONG 且带 replacementText → 返回 `（已纠正：…）` 标注，让纠正值传导进提取结果
 *   （与注入侧同款文案）；原始内容不再出现。
 * - WRONG 无 replacement / FINISHED / WRONG_PERSON / MUTED 未过期 → 返回 null，
 *   该内容不进提取材料（提取侧没有"可被动回应"的语义，MUTED 的提示文案在这里是噪声，
 *   故整段排除而不落标注）。
 * - MUTED 已过期 → 与注入侧同判（[MemoryRefPolicy.isMuteExpired]），原文恢复放行。
 *
 * 纯函数：不读库、不读时钟（时钟在 [MemoryRefPolicy.isMuteExpired] 内取系统时间，
 * 与注入侧同一来源）、不持有状态。
 */
object CorrectionExtractionFilter {

    /**
     * 提取链读源材料时的统一过滤口。
     *
     * @param kind 记忆类型（与注入侧 MemoryRef 同源：PROFILE / SCENE / LESSON / ONGOING）
     * @param sourcePath 源文件相对路径（与注入侧 makeRef 的 sourcePath 逐字同源）
     * @param content 已读出的源材料文本
     * @param corrections 纠正表（`memory/corrections.json` 读出的键值）
     * @param entryId 条目级 ID（仅 ONGOING 事项用，与注入侧 makeOngoingEntryRef 同源）
     * @return null = 该内容不进提取材料；非空 = 原文（未被纠正）或纠正标注（WRONG 带补正）
     */
    fun filterForExtraction(
        kbName: String,
        kind: MemoryKind,
        sourcePath: String,
        content: String,
        corrections: Map<String, MemoryCorrection>,
        entryId: String = ""
    ): String? {
        if (content.isBlank()) return content
        val ref = MemoryRefPolicy.makeRef(kbName, kind, sourcePath, content, entryId)
        val sb = StringBuilder()
        if (!MemoryRefPolicy.isCorrected(ref.id, corrections, sb)) return content
        // 命中纠正：只有「WRONG 且带补正」把纠正值传下去，其余整段排除
        val correction = findCorrection(ref.id, corrections)
        val replacement = correction?.replacementText?.trim().orEmpty()
        return if (correction?.action == CorrectionAction.WRONG && replacement.isNotEmpty()) {
            "（已纠正：$replacement）\n"
        } else {
            null
        }
    }

    /**
     * 与 [MemoryRefPolicy.isCorrected] 内部同款的 ID 回退查找（精确 → 去掉 `:hash` 后缀的
     * 旧格式文件级 ID）。isCorrected 负责判"是否跳过"，这里只负责取补正文案；两处使用
     * 同一条回退规则，避免"判定命中了、补正却取不到"的分叉。
     */
    private fun findCorrection(
        memoryId: String,
        corrections: Map<String, MemoryCorrection>
    ): MemoryCorrection? {
        corrections[memoryId]?.let { return it }
        val lastColon = memoryId.lastIndexOf(':')
        if (lastColon > 0) return corrections[memoryId.substring(0, lastColon)]
        return null
    }
}
