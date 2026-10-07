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
     * 每按一键就把"一天"续到此刻+1天，意图就永远不过期了。哪些保存该重算由
     * [saveDecision] 判（重新启用、重新选有效时间两类），UI 按那颗 `recomputeExpiry`
     * 决定传空串还是传已有时刻——设置页与意图编辑浮层两条路共用同一份判据。
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
     * 一次「保存意图改动」的决议：状态落到哪一格 + 到期时刻要不要按此刻重算。
     *
     * 变化理由只有一条：**用户这一刻动的是哪一根**。三条判据（原话 §10.2）：
     * · **重新启用**（关 → 开，到期/完成后的重开就走这一条）⇒ **必须重算**。
     *   旧 `expiryDate` 在被标 EXPIRED 那一刻就已经是过去的时刻，带着它落盘，下一次
     *   [shouldAutoExpire] 立刻把它判回 EXPIRED——"重新开启后立即失效"就是这一条形状。
     * · **重新选有效时间**（换了有效期档，或 UI 点名要重算）⇒ 同样重算；
     *   旧状态是终态（COMPLETED / EXPIRED）时一并恢复 ACTIVE，否则重开之后仍然不注入。
     * · **只改正文 / 只拨关**⇒ 期限逐字不动（[recomputeExpiry] = false，调用方原样把
     *   已有 `expiryDate` 交回）。每按一键就按"此刻 + 一天"续期，意图就永远不过期了。
     *
     * 两条不许越界的地方：
     * - 恢复 ACTIVE 只在**这一条真的被启用**时发生：开关还拨着关就不改状态，
     *   也不许把终态洗成活动态；
     * - 用户**这一次明确点成「已完成」**（[nextStatus] 从别档改成 COMPLETED，或有效期
     *   从时间档换到 COMPLETED 档）永远压过"恢复 ACTIVE"——完成是状态动作，不是重新启用。
     *   ⚠ 认的是**这次改动**，不是那份原样传回来的旧值：已完成过的意图被重新拨开时，
     *   调用方传回的 status 仍是 COMPLETED（编辑浮层就带着旧值回来），那种"带着"不算新的
     *   完成动作，必须恢复 ACTIVE——否则 §10.2 那句「COMPLETED / EXPIRED 重开仍不注入：
     *   必须在用户明确重新启用时恢复 ACTIVE」就永远做不到。
     *
     * 纯函数、不碰时间：重算与"保留原样"这一步只决定**传不传空串**，
     * 真正的到期时刻仍由 [effectiveExpiryDate] 用保存那一刻算（同一个时钟只取一次）。
     */
    fun saveDecision(
        current: IntentConfig,
        nextEnabled: Boolean,
        nextExpiry: IntentExpiry,
        nextStatus: IntentStatus = current.status,
        callerRequestedRecompute: Boolean = false
    ): IntentSaveDecision {
        val reactivating = nextEnabled && !current.enabled
        val expiryRepicked = callerRequestedRecompute || nextExpiry != current.expiry
        val wasTerminal = current.status == IntentStatus.COMPLETED ||
            current.status == IntentStatus.EXPIRED
        // 「这一次被点成完成」：新值与旧值不同才算，旧值原样带回来不算
        val completedNow = (nextStatus == IntentStatus.COMPLETED && current.status != nextStatus) ||
            (nextExpiry == IntentExpiry.COMPLETED && current.expiry != nextExpiry)
        val backToActive = nextEnabled && !completedNow &&
            (reactivating || (wasTerminal && expiryRepicked))
        return IntentSaveDecision(
            status = if (backToActive) IntentStatus.ACTIVE else nextStatus,
            recomputeExpiry = reactivating || expiryRepicked
        )
    }

    /** [saveDecision] 的返回：**状态** + **是否按此刻重算到期时刻**（true = 调用方传空串） */
    data class IntentSaveDecision(
        val status: IntentStatus,
        val recomputeExpiry: Boolean
    )

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
