package com.lovebrain.app.viewmodel

/**
 * 面板那九个"用了多少"的数——一份不可变快照 + 一个纯 `reduce`。
 *
 * 为什么要它（指导书 §2.2 那行的原话是"仍各自直接写多个 MutableStateFlow，
 * 没有统一 Reducer/UiState"）：这九个量原先是九个 `MutableStateFlow`，
 * 十二处语句直接改它们，其中五处还顺手把值抄回 `SecurePrefs`——
 * 于是"谁是这些计数的所有者"没有答案，跨天清零的判据还额外存在一个
 * `private var todayCostDate`（**状态的第二份**，不在任何 flow 里）。
 *
 * 现在：状态转移全在 [reduce]（纯函数，JVM 上直接测），
 * 落盘只在 `LoveBrainViewModel.applyUsage` 一处，日期的唯一来源是 [todayDate] 本身。
 */
data class UsageStats(
    /** 今日是哪一天（跨天清零的判据就靠它，原先另有一个 var 记同一件事） */
    val todayDate: String = "",
    val todayCostYuan: Double = 0.0,
    val lastCostYuan: Double? = null,
    val lastResponseMs: Long = 0L,
    val firstReplyMs: Long = 0L,
    val totalGenerateCount: Int = 0,
    val totalCostYuan: Double = 0.0,
    val totalCopyCount: Int = 0,
    val totalAdoptCount: Int = 0,
    val totalRewriteCount: Int = 0
) {

    /** 状态事件：只有这六种能改动上面的数 */
    sealed interface Event {
        /** 一次可计费请求的结果。`foreground` 才更新"本次费用"，今日/累计含全部请求 */
        data class Costed(val today: String, val yuan: Double, val foreground: Boolean) : Event

        /** 一次生成成功返回 */
        data object Generated : Event

        /** 复制了一条方案 */
        data object Copied : Event

        /** 采用了一条方案（口径：真的发出去那条） */
        data object Adopted : Event

        /** 改写了一条方案 */
        data object Rewritten : Event

        /** 回复链路的计时：首字耗时与首个 token 耗时分别来自同一次流式 */
        data class Timed(val firstReplyMs: Long? = null, val firstTokenMs: Long? = null) : Event
    }

    fun reduce(event: Event): UsageStats = when (event) {
        is Event.Costed -> {
            // 跨天：今日那一格从头算，而不是"清零再加"——两步合一步，中间没有可读到的 0
            val today = if (event.today == todayDate) todayCostYuan + event.yuan else event.yuan
            copy(
                todayDate = event.today,
                todayCostYuan = today,
                totalCostYuan = totalCostYuan + event.yuan,
                lastCostYuan = if (event.foreground) event.yuan else lastCostYuan
            )
        }
        Event.Generated -> copy(totalGenerateCount = totalGenerateCount + 1)
        Event.Copied -> copy(totalCopyCount = totalCopyCount + 1)
        Event.Adopted -> copy(totalAdoptCount = totalAdoptCount + 1)
        Event.Rewritten -> copy(totalRewriteCount = totalRewriteCount + 1)
        is Event.Timed -> copy(
            firstReplyMs = event.firstReplyMs ?: firstReplyMs,
            lastResponseMs = event.firstTokenMs ?: lastResponseMs
        )
    }

    companion object {
        /**
         * 从偏好里载入。跨天清零的判据只有这一处：存的不是今天就当 0 起步
         * （与 `LoveBrainViewModel.rollTodayCost` 同一条尺，那个函数因此被吸收进来）。
         */
        fun loaded(
            today: String,
            savedTodayCost: Pair<String, Double>?,
            totalGenerateCount: Int = 0,
            totalCostYuan: Double = 0.0,
            totalCopyCount: Int = 0,
            totalAdoptCount: Int = 0,
            totalRewriteCount: Int = 0
        ): UsageStats = UsageStats(
            todayDate = today,
            todayCostYuan = if (savedTodayCost?.first == today) (savedTodayCost?.second ?: 0.0) else 0.0,
            totalGenerateCount = totalGenerateCount,
            totalCostYuan = totalCostYuan,
            totalCopyCount = totalCopyCount,
            totalAdoptCount = totalAdoptCount,
            totalRewriteCount = totalRewriteCount
        )
    }
}
