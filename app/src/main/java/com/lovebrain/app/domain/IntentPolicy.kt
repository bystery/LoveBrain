package com.lovebrain.app.domain

import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import java.time.LocalDate

/**
 * 持续意图的到期判定与保存校验（从 `LoveBrainViewModel` 拆出）。
 *
 * 两条规则原先嵌在 ViewModel 里，只能连着协程、KB 切换和面板状态才碰得到；
 * 它们却都是"日期比较"这种一眼可能写错的东西，所以搬出来单独可测。
 *
 * 一个值得写清楚的事实：[IntentExpiry.TODAY] 与 [IntentExpiry.DATE] 用的是**同一条**过期尺
 * （`expiryDate < today`）。这不是偷懒合并——保存时 TODAY 的 `expiryDate` 一律被写成当天，
 * 于是"仅今天"在第二天自然满足"日期已过"，与"指定日期"的语义重合。
 * 但反过来，**历史上写进去的 TODAY 意图如果没有日期**（自动填之前的数据、或外部导入），
 * 这条就永远不过期；保存路径堵不住这个口，所以在这里显式说明并按"不过期"处理，
 * 让用例把行为钉住而不是让它继续靠巧合。
 */
object IntentPolicy {

    /** 是否需要把这条意图改写成 EXPIRED（自动到期检测） */
    fun shouldAutoExpire(config: IntentConfig, today: String): Boolean {
        if (!config.enabled) return false
        if (config.status != IntentStatus.ACTIVE) return false
        if (config.expiry == IntentExpiry.UNTIL_DONE) return false
        if (config.expiryDate.isBlank()) return false
        return config.expiryDate < today
    }

    /** TODAY 类型一律把日期写成"今天"，不依赖 UI 传；其余原样 */
    fun effectiveExpiryDate(expiry: IntentExpiry, expiryDate: String, today: String): String =
        if (expiry == IntentExpiry.TODAY) today else expiryDate

    /**
     * 保存前的拒绝理由；返回 null 表示可以保存。
     *
     * 只严格管 [IntentExpiry.DATE]：空、格式不对、过去日期配 ACTIVE 都得当场拒绝——
     * 这三条都是"存进去之后每次生成都带上一条永远实现不了的意图"的那种错。
     *
     * "过去"与 [today] 比，不与系统时钟第二次取：同一函数里用两个时钟，
     * 跨午夜那一下就会出现"自动填的今天"和"校验用的今天"不是同一天。
     */
    fun validateSave(
        expiry: IntentExpiry,
        effectiveExpiryDate: String,
        status: IntentStatus,
        today: String
    ): String? {
        if (expiry != IntentExpiry.DATE) return null
        if (effectiveExpiryDate.isBlank()) {
            return "指定日期不能为空，请输入 yyyy-MM-dd 格式的日期"
        }
        val parsed = runCatching { LocalDate.parse(effectiveExpiryDate) }.getOrNull()
            ?: return "日期格式无效，请使用 yyyy-MM-dd 格式"
        if (status == IntentStatus.ACTIVE) {
            val todayDate = runCatching { LocalDate.parse(today) }.getOrNull()
            if (todayDate != null && parsed.isBefore(todayDate)) {
                return "过去日期不能以活跃状态保存"
            }
        }
        return null
    }
}
