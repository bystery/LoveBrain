package com.lovebrain.app.domain.port

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Clock] 端口的合同：谁实现时间端口，谁就必须给出同样的可观察行为。
 *
 * 沿用仓库既有合同打法（[AiGatewayContract] / [KbArchivePortContract] / [KnowledgePortContract] /
 * [SettingsStorePortContract]）——同一批格子对 **生产侧**（[SystemClock]）与 **fake 侧**
 * （[FixedClock]）各跑一遍。于是"fake 有没有把三个时间形态的格式与单调性做对"第一次变成
 * 可被同一份合同咬住的事，而不是每个测试自己现编一份断言。
 *
 * 复核 §4 的 LSP 验收标准写的是"同一 contract test suite 对 production adapter 和 fake 都通过"——
 * `Clock` 与 AiGateway / 知识端口并列列为 domain 依赖的端口，但本轮之前它只有一份 [ClockWiringTest]
 * （行为接在 `TopicRecorder` 上，不是端口合同），fake 与生产各自长什么样没有任何格子盯。
 *
 * 为什么合同里只钉这几条、不钉"墙钟的字面串"：
 * - [SystemClock.wallClock] / [FixedClock.wallClock] 的字面串是"现在几点"——它在两台机器、
 *   两个时区、两秒之间都不一样。把它写进合同等于把测试钉死在某台机器的当下，与 [ClockWiringTest]
 *   头注那条"同一份输入在 09:59 与 10:01 会得到不同 prompt"恰恰相反。所以这里只钉**格式**
 *   （`yyyy-MM-dd HH:mm`，14 个字符、一个空格、一个冒号）与 **三个方法之间的自洽**
 *   （today 是 wallClock 的日期前缀、epochMs 是毫秒数）。
 * - [FixedClock] 还另钉一条"拨快之后墙钟真的跟着变"——光有"固定"测不到依赖时间的逻辑，
 *   [ClockWiringTest] 头注同款理由：冷却、话题老化、当日锦囊换天都要时间真的往前走一步才触发被测分支。
 *
 * 时区：[SystemClock] 走设备默认时区，[FixedClock] 走 `Locale.getDefault()` 的 SimpleDateFormat。
 * 合同不钉时区，只钉"三种形态自洽"——这样在 UTC 的 CI 与 +08:00 的本机都过。
 */
abstract class ClockContract {

    /** 每次给一个全新的 Clock（生产侧=SystemClock，fake 侧=新 FixedClock） */
    protected abstract fun newClock(): Clock

    @Test
    fun `wallClock is a 16-char datetime string`() {
        val c = newClock()
        val w = c.wallClock()
        assertEquals("wallClock 必须是 16 个字符（yyyy-MM-dd HH:mm）", 16, w.length)
        val re = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")
        assertTrue("wallClock 必须匹配 yyyy-MM-dd HH:mm：$w", re.matches(w))
    }

    @Test
    fun `today is a 10-char yyyy-MM-dd date`() {
        val c = newClock()
        val t = c.today()
        assertEquals("today 必须是 10 个字符（yyyy-MM-dd）", 10, t.length)
        assertTrue("today 必须匹配 yyyy-MM-dd：$t", Regex("\\d{4}-\\d{2}-\\d{2}").matches(t))
    }

    @Test
    fun `today is the date prefix of wallClock`() {
        val c = newClock()
        val w = c.wallClock()
        val t = c.today()
        assertEquals("today 必须是 wallClock 的前 10 个字符", w.substring(0, 10), t)
    }

    @Test
    fun `epochMs is a positive millisecond count`() {
        val c = newClock()
        val ms = c.epochMs()
        assertTrue("epochMs 必须是正的毫秒数（1970 之后）", ms > 0L)
    }

    @Test
    fun `two reads of the same clock agree on today and epochMs window`() {
        val c = newClock()
        val ms1 = c.epochMs()
        val t1 = c.today()
        // 同一只时钟在两次读之间，today 不应变（除非恰好跨午夜，这里只断 epochMs 不倒退）
        val ms2 = c.epochMs()
        val t2 = c.today()
        assertTrue("epochMs 不许倒退", ms2 >= ms1)
        // today 在同一毫秒窗口内必须稳定（跨午夜由 advance 那条单独测）
        if (ms2 - ms1 < 86_400_000L) {
            assertEquals("同一时钟两次读 today 必须一致（除非跨午夜）", t1, t2)
        }
    }

    /**
     * 拨快之后墙钟真的跟着变——这条只对可推进的 fake 有意义；生产 [SystemClock] 不进。
     * 生产侧覆写为 no-op 即可（默认实现就足够）。
     */
    @Test
    open fun `advancing the clock moves wallClock forward`() {
        // 生产侧默认不推进：SystemClock 自己读墙钟，无可控 advance；子类按需覆写。
    }
}

/** 生产侧：[SystemClock]——直接读系统时间。只跑那几条格式与自洽。 */
class SystemClockContractTest : ClockContract() {
    override fun newClock(): Clock = SystemClock
}

/**
 * fake 侧：[FixedClock]——可控、可推进。在公共合同之上另钉一条"拨快墙钟真的往前走"，
 * 这是 [ClockWiringTest] 里"第二轮块头跟着变"那条能成立的根。
 */
class FixedClockContractTest : ClockContract() {
    override fun newClock(): Clock = FixedClock()

    override fun `advancing the clock moves wallClock forward`() {
        val c = FixedClock()
        val before = c.wallClock()
        val msBefore = c.epochMs()
        c.advanceMinutes(60)
        val after = c.wallClock()
        val msAfter = c.epochMs()
        assertNotEquals("拨快 60 分钟后 wallClock 必须变", before, after)
        assertEquals("拨快 60 分钟后 epochMs 必须正好多 3_600_000 毫秒", msBefore + 3_600_000L, msAfter)
        assertTrue("拨快 60 分钟后的 wallClock 字面串仍要匹配 yyyy-MM-dd HH:mm",
            Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}").matches(after))
    }

    @Test
    fun `advancing across midnight rolls the date portion`() {
        val c = FixedClock()
        val today0 = c.today()
        // 推进到第二天同一时刻（24 小时），日期段必须 +1 天而格式不变
        c.advanceHours(24)
        val today1 = c.today()
        assertNotEquals("跨天后 today 必须变", today0, today1)
        assertEquals("跨天后 today 仍要 10 字符", 10, today1.length)
        assertTrue("跨天后 today 仍要匹配 yyyy-MM-dd：$today1",
            Regex("\\d{4}-\\d{2}-\\d{2}").matches(today1))
    }
}
