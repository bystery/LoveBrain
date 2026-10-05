package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.MuteDuration

/**
 * 记忆引用与纠正过滤（自 `domain/PromptBuilder.kt` 的「记忆引用与纠正过滤」行为块搬出）。
 *
 * 这一块的职责只有两件事：在知识段注入时为每一段被注入的记忆生成稳定的 [MemoryRef]
 * （文件级 / 条目级 ID），并按 corrections 表决定该段是跳过、补正、还是静音。它不读知识库、
 * 不读资产、不读时钟，也不持有任何状态，只接收**已经读出来的文本**与**纠正表**，
 * 因此是纯函数——搬运前后对同一批输入必须给出逐字相同的 MemoryRef 清单与 StringBuilder
 * 追加结果（字节证据见 `domain/prompt/PromptByteFreezeBaselineTest` 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - ID 为 `kind:sourcePath` 稳定 ID；条目级再追 `:entryId`。旧实现用内容 hash 做 ID，
 *   画像添一句、经验多一块、事项更新都会改 ID、丢纠正——改为文件级稳定 ID，纠正绑文件而非内容快照。
 * - WRONG：跳过原始内容；有 replacementText 则补正。
 * - FINISHED：跳过（事项已结束）。
 * - MUTED：检查时长过期；未过期则跳过原文并标记可被动回应；过期则恢复注入。
 *   THIS_ROUND 在持久层视为 legacy expired（旧版本可能持久化 THIS_ROUND，升级后不应变成永久静音）。
 * - WRONG_PERSON：隔离跳过。
 * - 历史带 `:hash` 的旧 ID 通过去掉后缀回退匹配。
 * - [filterRefsByPrompt] 把预算裁剪后不再出现在最终 prompt 里的引用剔除——只保留首行完整
 *   出现且不在省略标记区域内的引用，不再靠整段子串猜测。
 */
object MemoryRefPolicy {

    /** 生成 MemoryRef — id 为 `kind:sourcePath` 的稳定 ID（条目级再追 `:entryId`） */
    fun makeRef(kbId: String, kind: MemoryKind, sourcePath: String, text: String, entryId: String = ""): MemoryRef {
        val stableId = if (entryId.isNotBlank()) "${kind.name}:$sourcePath:$entryId" else "${kind.name}:$sourcePath"
        return MemoryRef(
            id = stableId,
            kbId = kbId,
            kind = kind,
            text = text.take(500),  // 截断防过大
            sourcePath = sourcePath
        )
    }

    /**
     * 为 ongoing 事项生成条目级 MemoryRef。
     * 每条事项有自己的 entryId（事项名），纠正只影响该条。
     */
    fun makeOngoingEntryRef(kbId: String, itemName: String, text: String): MemoryRef {
        return makeRef(kbId, MemoryKind.ONGOING, "moment/plan.md", text, entryId = itemName)
    }

    /** 检查 memoryId 是否被纠正。如果被纠正，按 action 类型处理。 */
    fun isCorrected(
        memoryId: String,
        corrections: Map<String, MemoryCorrection>,
        sb: StringBuilder
    ): Boolean {
        // 1. 精确匹配
        val correction = corrections[memoryId]
            // 2. 兼容：回退到旧格式文件级 ID（去掉 :hash 后缀）
            ?: run {
                val lastColon = memoryId.lastIndexOf(':')
                if (lastColon > 0) {
                    val oldFormatId = memoryId.substring(0, lastColon)
                    corrections[oldFormatId]
                } else null
            } ?: return false
        return when (correction.action) {
            CorrectionAction.WRONG -> {
                // 停止可信注入，如果有 replacementText 则注入补正内容
                if (correction.replacementText.isNotBlank()) {
                    sb.append("（已纠正：").append(correction.replacementText.trim()).append("）\n")
                }
                true  // 跳过原始内容
            }
            CorrectionAction.FINISHED -> true  // 事项已结束，跳过
            CorrectionAction.MUTED -> {
                // 检查静音是否已过期
                if (isMuteExpired(correction)) {
                    // 静音已过期——恢复正常注入
                    return false
                }
                // MUTED 真正限制——不注入原始内容，
                // 但在末尾标记可被动回应（模型可回答相关提问但不主动提）
                sb.append("（此条记忆已暂停主动提及，但仍可被动回应相关提问）\n")
                true  // 跳过原始内容
            }
            CorrectionAction.WRONG_PERSON -> true  // 隔离，跳过
        }
    }

    /**
     * 检查 MUTED 纠正是否已过期。
     * - THIS_ROUND: 持久 corrections.json 中的 THIS_ROUND 记录视为 legacy expired。
     *   旧版本可能持久化了 THIS_ROUND，升级后不应变成永久静音。
     *   THIS_ROUND 的真实生命周期由 ViewModel roundCorrections transient map 管理。
     * - TODAY: 今天剩余时间。跨天后恢复。
     * - UNTIL_RESTORE: 永不过期，只能手动撤销。
     * 旧数据无 muteDuration 字段时默认为 UNTIL_RESTORE，保持原语义。
     */
    fun isMuteExpired(correction: MemoryCorrection): Boolean {
        if (correction.muteTimestamp.isBlank()) return false
        return when (correction.muteDuration) {
            MuteDuration.UNTIL_RESTORE -> false
            MuteDuration.THIS_ROUND -> {
                // 持久层不应存在 THIS_ROUND 记录；如果存在，视为 legacy expired。
                // 旧版本持久化的 THIS_ROUND 升级后不应变成永久静音。
                true
            }
            MuteDuration.TODAY -> {
                // 今天剩余——跨天后恢复
                val muteTime = runCatching {
                    java.time.OffsetDateTime.parse(correction.muteTimestamp)
                }.getOrNull() ?: return false
                val now = java.time.OffsetDateTime.now()
                muteTime.toLocalDate() != now.toLocalDate()
            }
        }
    }

    /** refs 裁剪后过滤 — 只保留实际出现在最终 prompt 中的引用。
     * 不再仅靠子串猜测，而是检查 ref 的首行（标志性内容）
     * 是否完整出现在 prompt 中且不在省略标记区域内。 */
    fun filterRefsByPrompt(refs: List<MemoryRef>, prompt: String): List<MemoryRef> {
        return refs.filter { ref ->
            val marker = ref.text.lineSequence()
                .firstOrNull { it.isNotBlank() }?.take(80) ?: return@filter false
            val idx = prompt.indexOf(marker)
            // 必须在 prompt 中找到，且上下文不是省略标记
            if (idx < 0) return@filter false
            val ctxStart = maxOf(0, idx - 30)
            val ctxEnd = minOf(prompt.length, idx + marker.length + 30)
            val ctx = prompt.substring(ctxStart, ctxEnd)
            !ctx.contains("…（")
        }
    }

    /**
     * 「仅看本轮」那一档**不存在**旧记忆引用：这一档的请求正文里没有任何一段来自知识库
     * （画像 / 阶段 / 历史 / 经验 / 旧事项 / 表达偏好 / 持续意图整块没进 prompt），
     * 所以开关开着时交回来的非空清单**按定义就是假引用**——整条丢掉，不发给界面。
     *
     * 这是 第10节第3条「开启后不能仍显示假引用」那颗判据的**唯一规则来源**：
     * 请求侧（[com.lovebrain.app.domain.GenerationEngine] 发 ReplyMemoryRefs 之前）过这一颗。
     * 界面侧（`ui/panel/reply/MemoryRefsFeed.kt`）不重写这条 when，它只读同一个布尔、
     * 负责"没得画就一块都不画"这一半。
     */
    fun refsForRoundScope(refs: List<MemoryRef>, onlyThisRound: Boolean): List<MemoryRef> =
        if (onlyThisRound) emptyList() else refs
}
