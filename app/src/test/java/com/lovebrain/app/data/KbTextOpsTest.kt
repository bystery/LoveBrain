package com.lovebrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识库文本算法合同（从 `KnowledgeRepository` 拆出来的那批纯函数）。
 *
 * 这些规则原先是仓库类的 private 方法，只能在临时目录 + 互斥锁后面被间接触到；
 * 拆出来后本文件直接对它们下断言。逐条对应原实现的语义，不趁机改行为。
 */
class KbTextOpsTest {

    // ═══════════ 哈希：移动前后必须逐字节同值 ═══════════

    @Test
    fun `sha256 matches the known vectors`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            KbTextOps.sha256("")
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            KbTextOps.sha256("abc")
        )
    }

    @Test
    fun `contentHash is the concatenation hash truncated to 16`() {
        val joined = KbTextOps.sha256("abcdef").take(16)
        assertEquals(joined, KbTextOps.contentHash("abc", "def"))
        // 分段拼接不能与"带分隔符的拼接"混淆——原实现就是逐段 update，无分隔符
        assertFalse(KbTextOps.contentHash("abc", "def") == KbTextOps.contentHash("abc|def"))
    }

    // ═══════════ 当下状态条目 ═══════════

    @Test
    fun `scene entries sort newest first`() {
        val older = "- [2026-01-02 09:00] 她答应了周末见面"
        val newer = "- [2026-03-05 21:30] 开始在筹备搬家"
        val merged = KbTextOps.mergeSceneEntries(older, newer)
        assertTrue(
            "最新条目必须在最前：$merged",
            merged.indexOf(newer) in 0 until merged.indexOf(older)
        )
    }

    @Test
    fun `lines without a valid timestamp are kept as legacy not dropped`() {
        val valid = "- [2026-03-05 21:30] 有效条目"
        val junk = "随手写的一行，没有时间戳"
        val merged = KbTextOps.mergeSceneEntries(valid, junk)
        assertTrue("用户内容不得被静默丢弃", merged.contains(junk))
        assertTrue(merged.contains("# [legacy] 以下为无法解析时间戳的历史内容"))
        assertTrue(
            "legacy 段必须在尾部",
            merged.indexOf(junk) > merged.indexOf(valid)
        )
    }

    @Test
    fun `schema template example lines are not treated as valid entries`() {
        // 模板示例行是 "- [yyyy-MM-dd HH:mm] 说明"，日期位置不是数字 → 不算合法条目
        val template = "- [yyyy-MM-dd HH:mm] 形如"
        val merged = KbTextOps.mergeSceneEntries(template)
        assertTrue(merged.contains("[legacy]"))
    }

    @Test
    fun `merging nothing yields empty text`() {
        assertEquals("", KbTextOps.mergeSceneEntries())
        assertEquals("", KbTextOps.mergeSceneEntries("", "   "))
    }

    @Test
    fun `entry timestamp falls back to epoch zero when unparsable`() {
        assertEquals(0L, KbTextOps.entryTimestamp("- 没有时间戳的行"))
        assertTrue(KbTextOps.entryTimestamp("- [2026-03-05 21:30] x") > 0L)
    }

    // ═══════════ 进行中事项的状态链 ═══════════

    @Test
    fun `consecutive identical states collapse keeping the newest wording`() {
        val chain = "冷战中 → 冷战中（当前） → 已缓和"
        val cleaned = KbTextOps.cleanStateChain(chain)
        val parts = cleaned.split("→").map { it.trim() }
        assertEquals(2, parts.size)
        assertEquals("已缓和", parts.last())
        assertTrue("连续重复被合并后应保留最新写法：$cleaned", parts.first().contains("（当前）"))
    }

    @Test
    fun `state chain truncates to the last ten entries`() {
        val chain = (1..15).joinToString("→") { "状态$it" }
        val cleaned = KbTextOps.cleanStateChain(chain)
        val parts = cleaned.split("→")
        assertEquals(10, parts.size)
        assertEquals("状态15", parts.last().trim())
        assertEquals("状态6", parts.first().trim())
    }

    @Test
    fun `empty chain is returned unchanged`() {
        assertEquals("   ", KbTextOps.cleanStateChain("   "))
    }

    @Test
    fun `normalization strips timestamp prefix and current marker`() {
        assertEquals("冷战中", KbTextOps.normalizeStateForCompare("- [2026-03-05 21:30] 冷战中（当前）"))
        assertEquals("冷战中", KbTextOps.normalizeStateForCompare("冷战中"))
    }

    // ═══════════ plan.md 的说明行 ═══════════

    @Test
    fun `bare format and example lines get wrapped into html comments`() {
        val out = KbTextOps.wrapPlanMetaLines("格式：每行一条\n示例：搬家｜进行中\n真正的事项行")
        assertEquals("<!-- 格式：每行一条 -->\n<!-- 示例：搬家｜进行中 -->\n真正的事项行\n", out)
    }

    @Test
    fun `already commented lines are not double wrapped`() {
        val out = KbTextOps.wrapPlanMetaLines("<" + "!-- 格式：每行一条 -->\n事项")
        assertEquals("<" + "!-- 格式：每行一条 -->\n事项\n", out)
        assertEquals("已注释的行不得再包一层", 1, Regex("<" + "!--").findAll(out).count())
    }

    @Test
    fun `a comment spanning lines stays a single comment`() {
        val src = "<" + "!-- 开头\n格式：还在注释里\n结束 --" + ">\n事项"
        // 注释跨行时，中间的"格式：…"行不得再被包一层
        val out = KbTextOps.wrapPlanMetaLines(src)
        assertEquals(src + "\n", out)
        assertEquals(1, Regex("<" + "!--").findAll(out).count())
    }

    @Test
    fun `output always ends with exactly one newline`() {
        assertEquals("事项\n", KbTextOps.wrapPlanMetaLines("事项\n\n\n"))
    }

    @Test
    fun `blank input is returned untouched and non-blank ends with one newline`() {
        assertEquals("", KbTextOps.wrapPlanMetaLines(""))
        assertEquals("  \n", KbTextOps.wrapPlanMetaLines("  \n"))
        assertEquals("事项\n", KbTextOps.wrapPlanMetaLines("事项"))
        assertEquals("事项\n", KbTextOps.wrapPlanMetaLines("事项\n\n\n"))
    }
}
