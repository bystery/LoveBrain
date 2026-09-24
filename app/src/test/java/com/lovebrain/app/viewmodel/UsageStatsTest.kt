package com.lovebrain.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 使用统计的**纯**状态转移（没有 Android、没有协程、没有 prefs）。
 *
 * 为什么单独测这个：指导书 §2.2 那行说的是"各自直接写多个 MutableStateFlow，
 * 没有统一 Reducer/UiState"。合并成一份快照之后，判据全落在 `reduce` 里，
 * 于是它能像普通函数一样被逐个钉死——原先这九个数是十二处语句各改各的，
 * 想测"复制不会动采用"只能起整个 ViewModel。
 *
 * 落盘那半边（`applyUsage` 写 `SecurePrefs`）由既有 VM 级用例覆盖：
 * `MechanismClosureTest`（IO 失败时 adopt 不许涨）与 `RewriteEffectWiringTest`
 * （改写计数恰好写 prefs 一次）——本轮把它们的读法迁到 `usageStats` 上，断言原样未动。
 */
/**
 * 浮点一律带 delta：JUnit4 的 `assertEquals(double, double)` 在运行期直接拒判
 * （"Use assertEquals(expected, actual, delta) to compare floating-point numbers"），
 * 这一格本轮就是这么红的七次。
 */
class UsageStatsTest {

    private val today = "2026-09-25"
    private val yesterday = "2026-09-24"

    private fun assertYuan(expected: Double, actual: Double, label: String = "") =
        assertEquals(if (label.isEmpty()) null else label, expected, actual, 1e-9)

    @Test
    fun loadedKeepsTodayCostOnlyForTheSameDay() {
        assertYuan(3.2, UsageStats.loaded(today, today to 3.2).todayCostYuan)
        assertYuan(0.0, UsageStats.loaded(today, yesterday to 5.5).todayCostYuan, "跨天清零")
        assertYuan(0.0, UsageStats.loaded(today, null).todayCostYuan, "首启无存档")
        assertEquals(today, UsageStats.loaded(today, null).todayDate)
    }

    @Test
    fun loadedPassesTheFivePersistedCountersThrough() {
        val s = UsageStats.loaded(
            today, null,
            totalGenerateCount = 7, totalCostYuan = 1.25,
            totalCopyCount = 3, totalAdoptCount = 2, totalRewriteCount = 4
        )
        assertEquals(7, s.totalGenerateCount)
        assertYuan(1.25, s.totalCostYuan)
        assertEquals(3, s.totalCopyCount)
        assertEquals(2, s.totalAdoptCount)
        assertEquals(4, s.totalRewriteCount)
    }

    @Test
    fun aForegroundCostUpdatesTodayTotalAndThisRun() {
        val s = UsageStats(todayDate = today).reduce(
            UsageStats.Event.Costed(today, 0.4, foreground = true)
        )
        assertYuan(0.4, s.todayCostYuan)
        assertYuan(0.4, s.totalCostYuan)
        assertYuan(0.4, s.lastCostYuan ?: -1.0, "本次费用只认前台流式请求")
    }

    @Test
    fun aBackgroundCostStillCountsIntoTodayAndTotalButNotThisRun() {
        val start = UsageStats(todayDate = today, todayCostYuan = 0.9, lastCostYuan = 0.9)
        val s = start.reduce(UsageStats.Event.Costed(today, 0.3, foreground = false))
        assertYuan(1.2, s.todayCostYuan)
        assertYuan(0.3, s.totalCostYuan)
        assertYuan(0.9, s.lastCostYuan ?: -1.0, "后台 raw 不许污染\"本次\"")
    }

    @Test
    fun crossingMidnightRestartsTodayWithoutEatingTheNewCost() {
        val s = UsageStats(todayDate = yesterday, todayCostYuan = 8.0, totalCostYuan = 8.0)
            .reduce(UsageStats.Event.Costed(today, 0.5, foreground = true))
        assertYuan(0.5, s.todayCostYuan, "跨天不是\"清零再加\"，今日那格只有新的一笔")
        assertYuan(8.5, s.totalCostYuan)
        assertEquals(today, s.todayDate)
    }

    /** 四个计数器各改各的：任何一处串了，面板上就会出现"复制一次、采用数也 +1"这种鬼 */
    @Test
    fun eachCounterEventTouchesOnlyItsOwnField() {
        val events = listOf<UsageStats.Event>(
            UsageStats.Event.Generated, UsageStats.Event.Copied,
            UsageStats.Event.Adopted, UsageStats.Event.Rewritten
        )
        for (evt in events) {
            val before = UsageStats(
                todayDate = today, totalGenerateCount = 1, totalCopyCount = 2,
                totalAdoptCount = 3, totalRewriteCount = 4, totalCostYuan = 1.0
            )
            val after = before.reduce(evt)
            val changed = listOf(
                "gen" to (before.totalGenerateCount to after.totalGenerateCount),
                "copy" to (before.totalCopyCount to after.totalCopyCount),
                "adopt" to (before.totalAdoptCount to after.totalAdoptCount),
                "rewrite" to (before.totalRewriteCount to after.totalRewriteCount)
            ).filter { it.second.first != it.second.second }
            assertEquals("$evt 应该只动一个计数器，实动 $changed", 1, changed.size)
            val (name, pair) = changed.first()
            assertEquals("$name 应恰好 +1", pair.first + 1, pair.second)
            assertYuan(before.totalCostYuan, after.totalCostYuan, "事件不该动钱")
        }
    }

    @Test
    fun timingEventsKeepPreviousValuesWhenAbsent() {
        val start = UsageStats(todayDate = today, firstReplyMs = 1100, lastResponseMs = 700)

        val onlyReply = start.reduce(UsageStats.Event.Timed(firstReplyMs = 1500))
        assertEquals(1500, onlyReply.firstReplyMs)
        assertEquals("没有 firstToken 就该留着上一个", 700, onlyReply.lastResponseMs)

        val onlyToken = start.reduce(UsageStats.Event.Timed(firstTokenMs = 300))
        assertEquals(1100, onlyToken.firstReplyMs)
        assertEquals(300, onlyToken.lastResponseMs)

        val empty = start.reduce(UsageStats.Event.Timed())
        assertEquals(start, empty)
    }

    /** reduce 不许原地改：VM 里 `before`/`after` 要比对才知道该往 prefs 写哪一格 */
    @Test
    fun reduceNeverMutatesThePreviousSnapshot() {
        val before = UsageStats(todayDate = today, todayCostYuan = 1.0, totalCostYuan = 1.0)
        val after = before.reduce(UsageStats.Event.Costed(today, 2.0, foreground = true))
        assertYuan(1.0, before.todayCostYuan, "reduce 不许改旧快照")
        assertYuan(1.0, before.totalCostYuan)
        assertNotEquals(before, after)
    }

    @Test
    fun freshStatsHavePlaceholderFriendlyValues() {
        val s = UsageStats()
        assertNull("未计费时\"本次\"是 null，UI 显示占位\"—\"", s.lastCostYuan)
        assertEquals(0L, s.lastResponseMs)
        assertEquals(0L, s.firstReplyMs)
        assertEquals("", s.todayDate)
    }
}
