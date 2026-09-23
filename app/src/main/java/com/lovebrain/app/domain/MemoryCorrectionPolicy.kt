package com.lovebrain.app.domain

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration

/**
 * 记忆纠正的判定与文案单一真源（从 `LoveBrainViewModel` 拆出）。
 *
 * 拆出来不是为了少几行，而是这几条规则原先在两个撤销入口里各写一遍，
 * 其中"卡片里的撤销"失败时连提示都没有——同一次失败在纠正中心看得见、
 * 在卡片上看不见。文案归口后这种不对称没法再回来。
 */
object MemoryCorrectionPolicy {

    /**
     * 本轮瞬时暂停不写 corrections.json，只存活到本轮结束。
     *
     * 只有 MUTED + THIS_ROUND 这一种组合走瞬时路径；其余纠正都必须持久化，
     * 否则重启后"我以为已经纠正过的记忆"又回来了。
     */
    fun isTransientRoundMute(action: CorrectionAction, duration: MuteDuration): Boolean =
        action == CorrectionAction.MUTED && duration == MuteDuration.THIS_ROUND

    /** 记瞬时 mute 时要带的时间戳字段名在这，值由调用方取——这里不碰时钟 */
    fun transientMute(memoryId: String, replacementText: String, targetKbId: String, timestamp: String): MemoryCorrection =
        MemoryCorrection(
            memoryId = memoryId,
            action = CorrectionAction.MUTED,
            replacementText = replacementText,
            targetKbId = targetKbId,
            muteDuration = MuteDuration.THIS_ROUND,
            muteTimestamp = timestamp
        )

    fun roundMuteApplied(): String = "已暂停本轮提及，下次生成将过滤此条记忆"

    fun roundMuteUndone(): String = "已撤销本轮暂停，该记忆恢复注入"

    /** 持久化纠正成功的文案——每种动作说清它到底改了什么 */
    fun applied(action: CorrectionAction): String = when (action) {
        CorrectionAction.WRONG -> "已标记为错误，下次生成将过滤此条记忆"
        CorrectionAction.FINISHED -> "已标记为结束，不再作为活跃事项"
        CorrectionAction.MUTED -> "已暂停提及，下次生成将过滤此条记忆"
        CorrectionAction.WRONG_PERSON -> "已隔离，不再注入此条记忆"
    }

    fun applyFailed(): String = "纠正保存失败，请重试"

    fun undone(): String = "已撤销纠正，该记忆恢复可信注入"

    /** 撤销失败必须可见：两条撤销入口共用，不再一条有提示一条没有 */
    fun undoFailed(): String = "撤销失败，请重试"
}

/**
 * 本轮瞬时 mute 的存储。
 *
 * 语义与原来那个裸 `mutableMapOf` 相同，只是把"给外面发的快照必须是副本"钉死：
 * 生成时 PromptBuilder 拿到的是合并后的地图，若拿到内部实例就能改到 VM 的状态里。
 */
class RoundCorrectionStore {

    private val entries = mutableMapOf<String, MemoryCorrection>()

    /** 只读副本——调用方改不动存储本体 */
    fun snapshot(): Map<String, MemoryCorrection> = entries.toMap()

    fun put(correction: MemoryCorrection) {
        entries[correction.memoryId] = correction
    }

    /** 撤销瞬时 mute；没有这条时返回 false，调用方据此去撤持久化纠正 */
    fun remove(memoryId: String): Boolean = entries.remove(memoryId) != null

    fun clear() {
        entries.clear()
    }

    fun isEmpty(): Boolean = entries.isEmpty()
}
