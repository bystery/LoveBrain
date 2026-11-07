package com.lovebrain.app.domain.champion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未定事项检测阈值边界与结构信号（规格书 §5.3 夹具口径）：
 * 9 条正则、clause 切分、bigram 阈值（关闭 0.34 / 场上 0.50）、
 * 三条结构信号 A/B/C、R3 相关性过滤、R4 引号转述防误注入、新→旧去重取 3、
 * 无未定项返回空串（prompt 与父本逐字节相同）。
 */
class UncertainBlockDetectorTest {

    private fun ev(uid: Long, text: String, turn: Int = uid.toInt()) =
        ChampionEvent(uid = uid, role = "HER", text = text, ts = "2026-11-06 10:00", turn = turn)

    // ── 计量与切分 ──

    @Test
    fun `splitClauses keeps question flags`() {
        val out = UncertainBlockDetector.splitClauses("你到底买没买？我说了不算，得问她。")
        // 「，」也是切分符：3 段
        assertEquals(3, out.size)
        assertTrue(out[0].second)      // 问号 clause
        assertFalse(out[1].second)
        assertFalse(out[2].second)
    }

    @Test
    fun `overlap thresholds are the ported bigram formulas`() {
        // abcd vs abce：bigram {ab,bc,cd} vs {ab,bc,ce}，交 2 / |A|=3 → 0.6667
        assertTrue(UncertainBlockDetector.overlap("abcd", "abce") > 0.34)
        // 完全不相关
        assertEquals(0.0, UncertainBlockDetector.overlap("聊天软件", "退货流程"), 1e-9)
        // containment：交集 / 较短一方
        assertEquals(1.0, UncertainBlockDetector.overlapCont("abcde", "abc"), 1e-9)
    }

    // ── 信号 A：hedge + 锚点 ──

    @Test
    fun `hedge with anchor becomes an uncertain item`() {
        val cur = listOf(ev(0, "她说如果周五到货就先发货"))
        val r = UncertainBlockDetector.buildBlock(emptyList(), cur, 1)
        assertTrue(r.block.isNotEmpty())
        assertEquals("hedge", r.items[0].kind)
        assertTrue(r.block.startsWith(UncertainBlockDetector.UNCERTAIN_TITLE))
        assertTrue(r.block.endsWith(UncertainBlockDetector.UNCERTAIN_FOOTER))
        assertTrue(r.cu <= UncertainBlockDetector.UNCERTAIN_BUDGET_CU)
    }

    @Test
    fun `hedge without anchor or third party is not a candidate (R3)`() {
        // "你要是去"——纯条件/客套，无锚点无第三方+世界动词
        val cur = listOf(ev(0, "你要是去就早点回来"))
        val r = UncertainBlockDetector.buildBlock(emptyList(), cur, 1)
        assertEquals("", r.block)
        assertEquals(0, r.cu)
    }

    // ── 信号 B：问句 + 世界动词 + 锚点 ──

    @Test
    fun `unconfirmed question with anchor becomes an item`() {
        val cur = listOf(ev(0, "那个200块到底转没转？"))
        val r = UncertainBlockDetector.buildBlock(emptyList(), cur, 1)
        assertTrue(r.block.isNotEmpty())
        assertEquals("question", r.items[0].kind)
    }

    @Test
    fun `later confirmation with the same anchor closes the item (threshold 0_34)`() {
        // 同锚点（200）+ 确认词"已经/转了" → 关闭
        val prior = listOf(ev(0, "那个200块到底转没转？", turn = 1))
        val later = listOf(ev(1, "已经转了，200块你查收", turn = 2))
        val r = UncertainBlockDetector.buildBlock(prior, later, 2)
        assertEquals("", r.block)
    }

    @Test
    fun `confirmation without entity continuity does not close (conservative)`() {
        // "已经"确认但与原句无锚点重合、bigram 重合 <0.34 → 保守：不关闭
        val prior = listOf(ev(0, "老板说300块的货还没发？", turn = 1))
        val cur = listOf(ev(1, "已经问过老板了，还没答复"))
        val r = UncertainBlockDetector.buildBlock(prior, cur, 2)
        // 关闭判定：确认词有（已经/答复），但无同锚点、重合 1/11 < 0.34 → 不关闭；
        // R3 场上判定：同第三方（老板）→ 相关，仍注入
        assertTrue(r.block.isNotEmpty())
        assertTrue(r.block.contains("300块的货还没发"))
    }

    // ── 信号 C：引号转述（R4 防误注入） ──

    @Test
    fun `quoted hearsay in an asking message becomes a quote item`() {
        val cur = listOf(ev(0, "我舅说“网上买的不保险”，你说退不退？"))
        val r = UncertainBlockDetector.buildBlock(emptyList(), cur, 1)
        assertTrue(r.block.isNotEmpty())
        assertEquals("quote", r.items[0].kind)
        assertTrue(r.block.contains("网上买的不保险"))
    }

    @Test
    fun `quoted praise or decided quotes are not injected (R4)`() {
        // 赞美（无世界动词/锚点）不注入
        val praise = listOf(ev(0, "她说“你人真好”，还让我帮你问。"))
        assertEquals("", UncertainBlockDetector.buildBlock(emptyList(), praise, 1).block)
        // 引号内是已定决定（不退）不注入
        val decided = listOf(ev(1, "老板说“不退了”，就这样吧。"))
        assertEquals("", UncertainBlockDetector.buildBlock(emptyList(), decided, 1).block)
        // 引号后有当场答复（客服说…）不注入
        val answered = listOf(ev(2, "客服说“三天内发货”，已经答复我了。"))
        assertEquals("", UncertainBlockDetector.buildBlock(emptyList(), answered, 1).block)
    }

    // ── R3 相关性过滤与上限 ──

    @Test
    fun `historical items off the current board are not injected (R3)`() {
        val prior = listOf(ev(0, "她说如果周六去修车就顺便问问价格", turn = 1))
        val cur = listOf(ev(1, "晚饭吃了吗？"))
        assertEquals("", UncertainBlockDetector.buildBlock(prior, cur, 2).block)
    }

    @Test
    fun `at most MAX_ITEMS and within the 120 CU budget`() {
        val cur = listOf(ev(0,
            "如果周五到货就先发货？听说那个300的还没下单，舅舅说库存货不确定，你看要不要退？"))
        val r = UncertainBlockDetector.buildBlock(emptyList(), cur, 1)
        assertTrue(r.items.size <= UncertainBlockDetector.MAX_ITEMS)
        assertTrue(r.cu <= UncertainBlockDetector.UNCERTAIN_BUDGET_CU)
        for (line in r.block.split("\n")) {
            if (line.startsWith("- 「")) assertTrue(line.endsWith("）"))
        }
    }

    @Test
    fun `duplicate clauses dedupe newest first`() {
        val cur = listOf(
            ev(0, "那500块到底转没转？"),
            ev(1, "我再说一遍，那500块到底转没转？")
        )
        val r = UncertainBlockDetector.buildBlock(emptyList(), cur, 1)
        val texts = r.items.map { it.text }
        assertEquals(texts.size, texts.distinct().size)
    }

    @Test
    fun `no uncertain items means empty block byte-equal to parent`() {
        val cur = listOf(ev(0, "好的，就这么定了。"))
        val r = UncertainBlockDetector.buildBlock(emptyList(), cur, 1)
        assertEquals("", r.block)
        assertEquals(emptyList<UncertainBlockDetector.UncertainItem>(), r.items)
        assertEquals(0, r.cu)
    }

    @Test
    fun `item line format is verbatim quote with where label`() {
        val cur = listOf(ev(0, "如果明天到货就给你寄过去"))
        val r = UncertainBlockDetector.buildBlock(emptyList(), cur, 1)
        val it0 = r.items[0]
        assertEquals("- 「${it0.text}」（${it0.where}）",
            r.block.split("\n").first { it.startsWith("- ") })
    }
}
