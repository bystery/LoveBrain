package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 场景链注入转换（`SceneChainInjection`）。
 *
 * 这一步决定"屏上旧的状态条目有多少会被当成这一轮的事实喂给模型"。
 * 时钟与"今天"都固定传入，所以跨小时、跨日、超龄这三类边界能被确定地测，
 * 而不是等真跑到那个点才知道对不对。
 */
class SceneChainInjectionTest {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val zone = ZoneId.systemDefault()

    /** 以 base 为"现在"，构造一条 base-minus-hours 的条目 */
    private fun entryAt(base: LocalDateTime, hoursAgo: Double, label: String, facts: List<String>): String {
        val t = base.minusMinutes((hoursAgo * 60).toLong())
        return "- [${t.format(fmt)}] $label：${facts.joinToString("；")}"
    }

    private fun tsOf(t: LocalDateTime): Long = t.atZone(zone).toInstant().toEpochMilli()

    private val base = LocalDateTime.parse("2026-09-24 18:00", fmt)
    private val now = tsOf(base)
    private val today = "2026-09-24"

    private fun run(content: String, nowMs: Long = now, todayStr: String = today) =
        SceneChainInjection.transform(content, nowMs, todayStr)

    // ─── 解析与龄标注 ────────────────────────────────────────────

    @Test
    fun `a fresh same-day entry is labelled by hours only`() {
        val out = run(entryAt(base, 0.5, "她", listOf("在加班")))
        assertEquals("- [不到1小时前] 她：在加班", out)
    }

    @Test
    fun `an entry from the previous day keeps its date so age alone is not misread`() {
        // 刚过午夜：now=09-24 00:30，条目=09-23 23:00 → 1 小时前，且仍在注入窗口内
        val nowAt = base.withHour(0).withMinute(30)
        val previousDay = base.minusDays(1).withHour(23).withMinute(0)
        val out = run(
            entryAt(previousDay, 0.0, "她", listOf("已回城")),
            nowMs = tsOf(nowAt)
        )
        assertEquals("- [09-23 1小时前] 她：已回城", out)
    }

    @Test
    fun `an unparseable timestamp is reported as unknown time instead of being dropped`() {
        val out = run("- [0000-00-00 00:00] 她：说不清时间")
        assertEquals("- [时间未知] 她：说不清时间", out)
    }

    // ─── 过期与去重 ──────────────────────────────────────────────

    @Test
    fun `entries older than the window are never injected`() {
        val stale = entryAt(base, (AppConfig.SCENE_CHAIN_MAX_HOURS + 5).toDouble(), "她", listOf("很久以前的状态"))
        assertEquals("整条都过期时应该一段都不注入", "", run(stale))
    }

    @Test
    fun `the same fact text is injected once, from the newest entry`() {
        val content = listOf(
            entryAt(base, (AppConfig.SCENE_CHAIN_MAX_HOURS - 1).toDouble(), "早先", listOf("她今天在加班")),
            entryAt(base, 0.2, "刚才", listOf("她今天在加班", "她旁边有人"))
        ).joinToString("\n")

        val out = run(content)

        assertEquals("同一条事实只能出现一次", 1, Regex("她今天在加班").findAll(out).count())
        assertTrue("较新版本保留", out.contains("刚才"))
        assertTrue("新条目里的其他事实也要带上", out.contains("她旁边有人"))
    }

    // ─── 来源身份剥离 ────────────────────────────────────────────

    @Test
    fun `writer-side markers are stripped from the injected text`() {
        assertEquals("她喜欢猫", SceneChainInjection.cleanFact("她喜欢猫|src=abc|spk=HER|subj=她"))
        assertEquals("她喜欢猫", SceneChainInjection.cleanFact("她喜欢猫⟨m-1,m-2⟩"))
        assertEquals("她喜欢猫", SceneChainInjection.cleanFact("  她喜欢猫  "))
    }

    @Test
    fun `markers never survive into the injected line`() {
        val out = run(entryAt(base, 0.1, "她", listOf("在加班|src=m-9|spk=HER|subj=她")))
        assertEquals("- [不到1小时前] 她：在加班", out)
    }

    @Test
    fun `entries without any fact text are dropped`() {
        assertEquals("", run("- [2026-09-24 17:50] 只有一句标签没有内容"))
        assertEquals("", run("这段里没有条目行格式"))
    }
}
