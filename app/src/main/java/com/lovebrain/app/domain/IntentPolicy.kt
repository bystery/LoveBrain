package com.lovebrain.app.domain

import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 持续意图的到期判定与保存校验（从 `LoveBrainViewModel` 拆出）。
 *
 * 两条规则原先嵌在 ViewModel 里，只能连着协程、KB 切换和面板状态才碰得到；
 * 它们却都是"日期/时间比较"这种一眼可能写错的东西，所以搬出来单独可测。
 *
 * 有效期四档（[IntentExpiry]）：
 * - ONE_HOUR / ONE_DAY / ONE_WEEK：保存时由 [effectiveExpiryDate] 用"保存时刻"算出
 *   到期时刻（yyyy-MM-dd HH:mm），到期判定走 [shouldAutoExpire] 的字符串比较
 *   （ISO-like 格式字典序 = 时间序）。UI 不再填日期，所以保存路径不再校验 UI 传入的日期。
 * - COMPLETED：保存即标完成（[effectiveStatus] 把 status 改成 COMPLETED），
 *   不参与到期判定——它已经是终态。
 *
 * `now` 一律是 "yyyy-MM-dd HH:mm"（设备本地时区，由调用方交 [com.lovebrain.app.util.TimeFmt.now]）：
 * 同一次保存里所有时间判定共用这一个 now，跨午夜/跨小时那一下不会出现两个不同的"现在"。
 */
object IntentPolicy {

    private val DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** 是否需要把这条意图改写成 EXPIRED（自动到期检测） */
    fun shouldAutoExpire(config: IntentConfig, now: String): Boolean {
        if (!config.enabled) return false
        if (config.status != IntentStatus.ACTIVE) return false
        return when (config.expiry) {
            IntentExpiry.COMPLETED -> false
            IntentExpiry.ONE_HOUR, IntentExpiry.ONE_DAY, IntentExpiry.ONE_WEEK -> {
                if (config.expiryDate.isBlank()) false else config.expiryDate < now
            }
        }
    }

    /**
     * 时间档的到期时刻——已有就保留，没有就算。
     *
     * 保留是为了：文本编辑 / 开关切换这种"没换有效期档"的保存不该把计时重置——
     * 每按一键就把"一天"续到此刻+1天，意图就永远不过期了。换档时 UI 传空串触发重算，
     * 编辑浮层保存也传空串（那是一次"重新设一条意图"的操作）。
     * COMPLETED 不带日期。
     */
    fun effectiveExpiryDate(expiry: IntentExpiry, expiryDate: String, now: String): String {
        if (expiry == IntentExpiry.COMPLETED) return ""
        if (expiryDate.isNotBlank()) return expiryDate
        val base = runCatching { LocalDateTime.parse(now, DT_FMT) }.getOrNull()
            ?: return ""
        val shifted = when (expiry) {
            IntentExpiry.ONE_HOUR -> base.plusHours(1)
            IntentExpiry.ONE_DAY -> base.plusDays(1)
            IntentExpiry.ONE_WEEK -> base.plusDays(7)
            IntentExpiry.COMPLETED -> return ""
        }
        return shifted.format(DT_FMT)
    }

    /**
     * COMPLETED 档保存即标完成——status 跟着 expiry 走，UI 不必再单独操作状态。
     * 其余档保留调用方交来的 status。
     */
    fun effectiveStatus(expiry: IntentExpiry, status: IntentStatus): IntentStatus =
        if (expiry == IntentExpiry.COMPLETED) IntentStatus.COMPLETED else status

    /**
     * 保存前的拒绝理由；返回 null 表示可以保存。
     *
     * 新规格下日期由保存路径自己算，UI 不再填日期，所以这里不再做格式/过去日期校验。
     * 仅保留"时间档算出来的日期不能为空"这一条兜底——算失败说明 now 解析出了问题。
     */
    fun validateSave(
        expiry: IntentExpiry,
        effectiveExpiryDate: String,
        status: IntentStatus,
        now: String
    ): String? {
        if (expiry == IntentExpiry.COMPLETED) return null
        if (effectiveExpiryDate.isBlank()) {
            return "有效期不能为空，请重新选择"
        }
        return null
    }
}
