package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.util.TimeFmt

/**
 * 持续意图与回复想法区块（自 `domain/PromptBuilder.kt` 的「持续意图区块」「想法区块」行为块搬出）。
 *
 * 这一块是 prompt 的**表示层**：给定一份意图配置 / 一段用户想法文本，写出 prompt 里那两段固定格式的
 * 字符串。它不读知识库、不读资产、不读时钟（意图有效期的设备本地日期走 [TimeFmt.today] 纯函数），
 * 也不持有任何状态，因此是纯函数——搬运前后对同一批输入必须给出逐字相同的字符串
 * （字节证据见 `domain/prompt/PromptByteFreezeBaselineTest` 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - 持续意图区块应用有效期与完成状态——到期或完成的意图不注入；PAUSED 保留文本但不注入。
 * - 有效期三类：TODAY 依赖 status 字段（已过期时 status=EXPIRED）；DATE 用设备本地日期判断过期
 *   （不硬编码 UTC，今天创建的今天有效，明天自动到期）；UNTIL_DONE 依赖 status 字段。
 * - 想法区块仅当 userHint 非空时出现，固定四方案措辞。
 */
object IntentIdeaBlock {

    /** 持续意图区块
     *  应用有效期与完成状态——到期或完成的意图不注入。
     *  使用设备本地时区判断 TODAY 和 DATE 过期。 */
    fun buildIntentBlock(intentConfig: IntentConfig): String {
        if (!intentConfig.enabled || intentConfig.text.isBlank()) return ""
        // 检查意图状态——COMPLETED/EXPIRED 不注入
        if (intentConfig.status == IntentStatus.COMPLETED ||
            intentConfig.status == IntentStatus.EXPIRED) return ""
        // PAUSED 保留文本但不注入
        if (intentConfig.status == IntentStatus.PAUSED) return ""
        // 检查有效期
        val today = TimeFmt.today()
        when (intentConfig.expiry) {
            IntentExpiry.TODAY -> {
                // 仅今天——使用设备本地日期，不硬编码 UTC
                // 今天创建的意图今天有效，明天自动到期
                // 由于我们不知道创建日期，依赖 status 字段——已过期时 status=EXPIRED
            }
            IntentExpiry.DATE -> {
                // 指定日期过期
                if (intentConfig.expiryDate.isNotBlank() && intentConfig.expiryDate < today) {
                    return ""  // 已过期，不注入
                }
            }
            IntentExpiry.UNTIL_DONE -> {
                // 直到手动完成——依赖 status 字段
            }
        }
        return "【持续意图】\n${intentConfig.text.trim()}\n\n"
    }

    /** IDEA 区块 */
    fun buildIdeaBlock(userHint: String): String {
        return if (userHint.isNotBlank()) {
            "# 用户的回复想法\n用户想这样回：「${userHint.trim()}」\n请基于这个方向润色出4种方案。\n\n"
        } else ""
    }
}
