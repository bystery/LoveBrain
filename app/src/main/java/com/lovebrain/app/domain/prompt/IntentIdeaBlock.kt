package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.util.TimeFmt

/**
 * 持续意图与军师备注区块（自 `domain/PromptBuilder.kt` 的「持续意图区块」「想法区块」行为块搬出）。
 *
 * 这一块是 prompt 的**表示层**：给定一份意图配置 / 一段本轮备注文本，写出 prompt 里那两段固定格式的
 * 字符串。它不读知识库、不读资产、不读时钟（意图有效期的设备本地日期走 [TimeFmt.today] 纯函数），
 * 也不持有任何状态，因此是纯函数。
 *
 * 语义约束：
 * - 持续意图区块应用有效期与完成状态——到期或完成的意图不注入；PAUSED 保留文本但不注入。
 * - 有效期三类：TODAY 依赖 status 字段（已过期时 status=EXPIRED）；DATE 用设备本地日期判断过期
 *   （不硬编码 UTC，今天创建的今天有效，明天自动到期）；UNTIL_DONE 依赖 status 字段。
 * - 军师备注区块仅当正文非空时出现，正文**逐字全量**注入（展示端省略过，发给模型不许裁）。
 *
 * ⚠  改名与改语义：这一段以前写着"用户想这样回……请基于这个方向润色出4种方案"，
 * 那是把**背景/限制/提醒**读成了**待发送话术**，还顺手把输出数量钉死成 4 种。
 * 现在措辞只说明"这是用户给军师的补充"，方案数量与格式仍由回复任务原有格式控制。
 * （`buildAdvisorNoteBlock` 的字节变化会打到 `PromptByteFreezeBaselineTest` 的 4 行冻结读数，
 * 那 4 行需要按新字节重钉——见本轮交付报告。）
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

    /** 军师备注区块的标题行（UI 不参与拼装，这一颗是 prompt 侧唯一的措辞真源） */
    const val NOTE_BLOCK_HEADER = "# 军师备注（用户补充，不是要发出去的话术）"

    /**
     * 本轮军师备注区块。
     *
     * @param note 备注正文（`GenerationInput.replyDirective.text` 那一份，原样，不裁切）
     * @return 空正文给空串——没有备注就没有这一段，prompt 里不留空标题
     */
    fun buildAdvisorNoteBlock(note: String): String {
        val text = note.trim()
        if (text.isEmpty()) return ""
        return buildString {
            append(NOTE_BLOCK_HEADER).append("\n")
            append("「").append(text).append("」\n")
            append("这是用户给你的背景、限制、目的或提醒，只用来约束你这一轮怎么想、怎么回；")
            append("它不是要原样发给对方的消息，也不要当成用户已经说过的话来复述。\n")
            append("方案数量与输出格式按本任务既有的格式要求，不因为这段补充而改变。\n\n")
        }
    }
}
