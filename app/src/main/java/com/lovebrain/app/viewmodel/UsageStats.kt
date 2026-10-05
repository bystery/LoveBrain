package com.lovebrain.app.viewmodel

/**
 * 面板那九个"用了多少"的数——一份不可变快照 + 一个纯 `reduce`。
 *
 * 为什么要它（ 第2节第2条 那行的原话是"仍各自直接写多个 MutableStateFlow，
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

/** 一分 = 0.01 元。低于这一档，两位小数会被念成 `0.00` ⇒ 另走 [CostKnowledge.BelowCent] 那一句 */
const val COST_CENT_YUAN = 0.01

/**
 * 一笔记账金额的**可读性**：已知 / 已知但不足一分 / 根本不知道。
 *
 * 为什么 `Unknown` 不等于"免费"（这一格的全部理由）：
 * 钱进 [UsageStats.totalCostYuan] 只有两条路——[Event.Costed] 累加，
 * 或 [UsageStats.loaded] 从 `SecurePrefs` 原样载入（那份存档本身也只由前者写）。
 * 而 [Event.Costed] 的唯一发射点是 `ApiUsageTracker.logUsage` 里
 * `if (cost > 0.0)` 那一道（`data/ApiUsageTracker.kt`），
 * `UsagePricer.shouldBill` 不满足（自定义 Provider 不给 usage、地址不含 deepseek.com）
 * 或 `priceTier` 落在 `UNKNOWN` 档时压根不发射。
 * ⇒ 每一笔记进来的账都是**严格为正**的，`totalCostYuan == 0.0` 与"一条可计价记录都没有"
 *   是同一件事的两种说法；反过来它**推不出**"这家 Provider 免费"。
 * ⇒ 所以这里不需要再挂一个"已计价请求数"的计数器：那是同一件事的第二份账
 *   （而它还没法跟着 `SecurePrefs` 那份存档活过冷启——载入路径在禁区里）。
 */
enum class CostKnowledge {
    /** 一条可计价记录都没入过账：不知道，不是 0 元 */
    Unknown,

    /** 有可计价记录，但合计不足一分 */
    BelowCent,

    /** 有可计价记录且已足一分 */
    Known
}

/** 判据只有一个所有者：三处显示点（首页、使用概览详情、面板顶部那条）都调这一颗 */
fun costKnowledge(yuan: Double): CostKnowledge = when {
    yuan <= 0.0 -> CostKnowledge.Unknown
    yuan < COST_CENT_YUAN -> CostKnowledge.BelowCent
    else -> CostKnowledge.Known
}

/**
 * 把一笔记账金额念成用户看得见的那一串——**分岔只在这一个函数里**（三处显示点必须一致）：
 *  - [CostKnowledge.Unknown] → [unknownText]（各页给「—」）
 *  - [CostKnowledge.BelowCent] → [belowCentText]（"不足 ¥0.01"那种不撒谎的写法，**不写 0、不写免费**）
 *  - [CostKnowledge.Known] → [amountText]
 *
 * ⚠ [amountText] 由调用方给，**不是**忘了收口：首页/详情页是两位小数且不锁 Locale，
 * 面板走 `LoveBrainViewModel.formatYuan`（三位小数、锁 `Locale.US`）。
 * 这两条口径的差异是账本 第61节第4条 里记着的既有欠账，
 * 差异现由 `CostDisplayTest` 钉着（原来另一头的 `UsageExtremeValuesSemanticsTest` 已随首页重做删除），本轮不并（并了就是顺手改 第61节第4条）。
 */
fun costReadout(
    yuan: Double,
    unknownText: String,
    belowCentText: String,
    amountText: (Double) -> String
): String = when (costKnowledge(yuan)) {
    CostKnowledge.Unknown -> unknownText
    CostKnowledge.BelowCent -> belowCentText
    CostKnowledge.Known -> amountText(yuan)
}
