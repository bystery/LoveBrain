package com.lovebrain.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 外部复核 共用判据：累计/今日那格费用的三态「无法计价 / 不足一分 / 已知」。
 *
 * **为什么测函数不测界面**（本仓库踩过的坑：JVM 上跑 Compose 语义树有一堆假通过形态——
 * 被 `maxLines + Ellipsis` 裁掉的串照样报完整原串、文本判据可能根本没牙）：
 * 这一格的判据就是"选哪一句串"，纯字符串、零 Android 依赖，三处显示点
 * （`HomeScreen` / `UsageDetailScreen` / `LoveBrainPanelScreen`）都汇到 [costReadout] 这一颗。
 * 所以红绿在这里量到才是真的；UI 那两家守卫本轮只跟着改期望串，不当证人。
 *
 * 池子口径（报告要求每个数字说清出处）：本文件断言的是**累计 = 全部已计价请求（前台 + 后台）
 * 之和**那一个池子（见 `UsageStats.Event.Costed` 的注释与 [accumulated pool] 那一格），
 * 不是"本次"那一格（只有 `CostScope.FOREGROUND` 会更新它）。
 *
 * ⚠ 金额串那一半刻意**不**收进本文件断言：首页/详情页是两位小数且不锁 Locale、面板走
 * `LoveBrainViewModel.formatYuan`（三位小数、锁 `Locale.US`）。两边各有守卫钉着
 * （`UsageExtremeValuesSemanticsTest` / `CostDisplayTest`），差异记在账本 第61节第4条，本轮不并。
 * 下面的 [homeAmount] 只是"调用方给的那支格式化函数"的形状替身（那里锁了 Locale 好让断言稳定）。
 */
class CostReadoutTest {

    private val dash = "—"
    private val belowCent = "不足 ￥0.01"
    private fun homeAmount(yuan: Double): String = "￥" + String.format(Locale.US, "%.2f", yuan)
    private fun panelAmount(yuan: Double): String = "¥" + String.format(Locale.US, "%.3f", yuan)

    private fun readout(yuan: Double, amount: (Double) -> String = ::homeAmount): String =
        costReadout(yuan, dash, belowCent, amount)

    /** (a) 一条都计不了价：念「—」，绝不念成"￥0 / 0.00 / 免费" */
    @Test
    fun `a ledger with nothing priceable reads the dash and never a free zero`() {
        for (yuan in listOf(0.0, -0.0)) {
            assertEquals("0.0 那一档就是把\"不知道\"说成\"免费\"", dash, readout(yuan))
            assertEquals("面板同理", dash, readout(yuan, ::panelAmount))
        }
        val unknown = readout(0.0) + readout(0.0, ::panelAmount)
        listOf("￥0", "¥0", "0.00", "0.000", "免费").forEach { lie ->
            assertFalse("无法计价时念出了「$lie」：$unknown", unknown.contains(lie))
        }
        assertEquals(CostKnowledge.Unknown, costKnowledge(0.0))
    }

    /** (b) 有可计价记录、合计不足一分：说"不足 ￥0.01"，不写 0、不写免费 */
    @Test
    fun `priceable records under a cent say under a cent instead of zero`() {
        for (yuan in listOf(0.001, 0.005, 0.009999)) {
            assertEquals("$yuan 不该被念成 0", belowCent, readout(yuan))
            assertEquals(CostKnowledge.BelowCent, costKnowledge(yuan))
        }
        val said = readout(0.004)
        assertFalse("不足一分那一档念出了裸的零：$said", said == "￥0" || said == "￥0.00")
        assertFalse("不足一分那一档念成了免费：$said", said.contains("免费"))
    }

    /** (c) 正常金额照旧两位小数；一分那道边界算"已知"而不是"不足一分" */
    @Test
    fun `known amounts still read as money and the cent boundary is known`() {
        assertEquals("￥1.23", readout(1.234))
        assertEquals("￥0.01", readout(COST_CENT_YUAN))
        assertEquals(CostKnowledge.Known, costKnowledge(COST_CENT_YUAN))
        assertEquals("￥10000.00", readout(9999.999))
        // 三位小数那一档（面板）走调用方的函数，判据是同一颗
        assertEquals("¥1.234", readout(1.234, ::panelAmount))
        assertEquals("¥8.900", readout(8.9, ::panelAmount))
    }

    /**
     * 判据的**前提**必须自己会站着：为什么 `0.0` 就等于"一条都计不了价"。
     * 账只从 [UsageStats.Event.Costed] 进（发射口在 `ApiUsageTracker.logUsage` 的 `cost > 0.0`
     * 那一道，本文件用同一形状喂事件）⇒ 每笔都是严格正的 ⇒ 累计为 0 只可能是"没有可计价记录"。
     * 这一格把"两个 scope 都进同一个池子"也钉住：少了一发就念 dash 的话，面板冷启那一路就是假的。
     */
    @Test
    fun `accumulated pool is every priceable request of both scopes`() {
        val today = "2026-10-05"
        val start = UsageStats()
        assertEquals("空快照 = 一条都计不了价 = 未知", dash, readout(start.totalCostYuan))

        val withBackground = start.reduce(UsageStats.Event.Costed(today, 0.004, foreground = false))
        val both = withBackground.reduce(UsageStats.Event.Costed(today, 0.003, foreground = true))

        // 后台那一笔进的是同一个池子（"累计"含全部请求），所以两笔之后已经不再是"未知"
        assertEquals(0.007, both.totalCostYuan, 1e-9)
        assertTrue("后台计费被漏出累计池子了", costKnowledge(withBackground.totalCostYuan) != CostKnowledge.Unknown)
        assertEquals("合计不足一分 ⇒ 说\"不足\"，不写 0", belowCent, readout(both.totalCostYuan))
        // "本次"只看前台：后台那一笔不该污染它（这一句钉的是 lastCostYuan，不是钱池）
        assertEquals(0.003, both.lastCostYuan!!, 1e-9)

        val big = both.reduce(UsageStats.Event.Costed(today, 1.5, foreground = true))
        assertEquals("￥1.51", readout(big.totalCostYuan))
    }

    /**
     * 反向证人：判据必须把「无法计价」与「不足一分」**分成两句**。
     * 改前的写法 `if (totalCostYuan < 0.01) "￥0"` 把这两档并成同一句"免费"——
     * 谁把它们并回去（判据写成 `yuan < COST_CENT_YUAN -> BelowCent` 就是这一发），红在这里。
     */
    @Test
    fun `the old free-zero shape collapsed unknown and under-a-cent into one string`() {
        val buckets = listOf(
            0.0 to CostKnowledge.Unknown,
            0.0001 to CostKnowledge.BelowCent,
            0.01 to CostKnowledge.Known,
            100.0 to CostKnowledge.Known
        )
        buckets.forEach { (yuan, expected) ->
            assertEquals("$yuan 落错了档", expected, costKnowledge(yuan))
        }
        val unknown = readout(0.0)
        val underACent = readout(0.0001)
        assertTrue(
            "\"无法计价\"和\"有账但不足一分\"被念成了同一句（$unknown）——那就是把不知道当免费",
            unknown != underACent
        )
    }
}
