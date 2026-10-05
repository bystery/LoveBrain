package com.lovebrain.app.ui.panel.stats

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.LbMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 顶部一行统计条的**决策层**：分组、要不要轮播、每页摆在哪、什么时候换组。
 *
 * 这一层全部是纯函数，所以坏实现在这儿撞得最干净：
 * - 把"永远轮播"当需求（一行明明放得下）→ 第 2、3 栏红；
 * - 靠**裁掉半个数字**或缩字来塞进一行 → 第 4、5、6 栏红（静止态每页要么整页在里面、
 *   要么整页在外面，这条不变式对"截一半"没有豁免）；
 * - 定时器与手指抢同一颗页号 → 第 7、8 栏红。
 *
 * 一行放得下/放不下的判据用**宽度**说话，不用字符串长度：语义树在被裁切时照样报完整原串，
 * 文本判据看不见裁切（这条教训在本仓库已经踩过一次，见 `UsageExtremeValuesSemanticsTest`
 * 与 `EmptyStateOwnershipSemanticsTest` 里那份自然宽控制组）。
 */
class UsageStatBarPlanTest {

    // ═══════════ 造数据 ═══════════

    private fun field(name: String, value: String) = LbMetric(name, value)

    /** 现有那五格，顺序与页面传进来的顺序一致 */
    private fun fiveFields() = listOf(
        field("今日", "¥0.123"),
        field("本次", "¥0.045"),
        field("首字", "1.2s"),
        field("累计", "37次"),
        field("已统计", "¥4.560")
    )

    /** 一格占多宽的假尺：数值 + 12dp 组内间距，完全确定性，不依赖字体 */
    private fun fakeWidth(items: List<LbMetric>, perFieldDp: Float = 60f, gapDp: Float = 12f): Dp {
        val inner = if (items.isEmpty()) 0f else perFieldDp * items.size + gapDp * (items.size - 1)
        return inner.dp
    }

    // ═══════════ 1. 分组只用现有字段，不加维度 ═══════════

    @Test
    fun `five existing fields split into whole groups without inventing a dimension`() {
        val groups = groupUsageStatFields(fiveFields())

        // 现有五格 ⇒ 三格 + 两格（今日+本次+首字 / 累计+已统计），与那张例子同形
        assertEquals(listOf(3, 2), groups.map { it.size })
        // 不新增也不丢：逐格逐序
        assertEquals(fiveFields(), groups.flatten())
    }

    @Test
    fun `the three-field legacy row and the empty row are not split at all`() {
        val legacy = listOf(field("今日", "¥0.123"), field("本次", "—"), field("首字", "1.2s"))
        assertEquals(listOf(legacy), groupUsageStatFields(legacy))
        assertEquals(listOf(2, 2), groupUsageStatFields(fiveFields().take(4)).map { it.size })
        assertEquals(1, groupUsageStatFields(listOf(field("累计", "37次"))).size)
        assertTrue(groupUsageStatFields(emptyList()).isEmpty())
    }

    @Test
    fun `grouping never reorders and never duplicates a field`() {
        val fields = (1..9).map { field("格$it", "$it.0") }
        val flattened = groupUsageStatFields(fields).flatten()
        assertEquals(fields, flattened)
        assertEquals(fields.size, flattened.distinct().size)
    }

    // ═══════════ 2. 一行放得下就不轮播 ═══════════

    @Test
    fun `a line wide enough for every group keeps them all on screen and does not rotate`() {
        val groups = groupUsageStatFields(fiveFields())
        val needed = fakeWidth(groups[0]) + 12.dp + fakeWidth(groups[1])
        val layout = planUsageStatLayout(groups, { fakeWidth(it) }, needed + 1.dp)

        assertFalse("一行明明放得下，却还是开了轮播", layout.rotates)
        assertEquals(groups.size, layout.pages.size)
        layout.pages.forEach { page ->
            assertFalse("一行放得下时不该有渐隐（左）", page.fadeLeading)
            assertFalse("一行放得下时不该有渐隐（右）", page.fadeTrailing)
        }
        // 每一格都还在"会显示"的那份内容里：平铺时所有组同时可见
        assertEquals(fiveFields(), layout.pages.flatMap { it.fields })
    }

    @Test
    fun `one dp short of the same line is what turns rotation on`() {
        val groups = groupUsageStatFields(fiveFields())
        val needed = fakeWidth(groups[0]) + 12.dp + fakeWidth(groups[1])
        val tight = planUsageStatLayout(groups, { fakeWidth(it) }, needed - 1.dp)

        assertTrue("差 1dp 放不下，必须整组轮播", tight.rotates)
        assertEquals(2, tight.pages.size)
        assertEquals(listOf(3, 2), tight.pages.map { it.fields.size })
    }

    @Test
    fun `widths that are not measured yet keep the old flat behaviour`() {
        val groups = groupUsageStatFields(fiveFields())
        val layout = planUsageStatLayout(groups, { null }, 100.dp)
        assertFalse(layout.rotates)
        assertEquals(2, layout.pages.size)
        layout.pages.forEach {
            assertFalse(it.fadeLeading)
            assertFalse(it.fadeTrailing)
        }
    }

    // ═══════════ 3. 一整组也放不下时，拆的是组、不是数字 ═══════════

    @Test
    fun `an over-wide group degrades to whole-field pages instead of a half number`() {
        val groups = groupUsageStatFields(fiveFields())
        // 视口只够一格（60dp + 组内 12dp）：三格那一组与两格那一组整组都塞不进去
        val layout = planUsageStatLayout(groups, { fakeWidth(it) }, 100.dp)

        assertTrue(layout.rotates)
        // 每页都是"完整的字段"，没有任何一页被切成半格
        assertEquals(List(layout.pages.size) { 1 }, layout.pages.map { it.fields.size })
        // 现有字段一个不多一个不少，顺序没变
        assertEquals(fiveFields(), layout.pages.flatMap { it.fields })
    }

    @Test
    fun `a single field wider than the line is reported honestly instead of shrinking text`() {
        val groups = listOf(listOf(field("已统计", "不足 ¥0.000")))
        val layout = planUsageStatLayout(groups, { fakeWidth(it, perFieldDp = 400f) }, 100.dp)

        assertFalse("只有一格时没有第二组可换，不许假装能轮播", layout.rotates)
        assertFalse("这一页塞不进这一行：这个事实必须报出来，而不是靠缩字盖掉",
            layout.pages.single().fitsViewport)
    }

    // ═══════════ 4. 渐隐只在"还能翻 + 有留白"时出现 ═══════════

    @Test
    fun `fades mark only the directions that still have a page and only over blank space`() {
        val groups = groupUsageStatFields(fiveFields())
        val layout = planUsageStatLayout(groups, { fakeWidth(it) }, 210.dp)
        assertTrue(layout.rotates)
        assertEquals(listOf(3, 2), layout.pages.map { it.fields.size })

        // 第一组 204dp 摆进 210dp：两边各剩 3dp < 16dp ⇒ 不许画渐隐（淡掉的就是数字本身）
        val first = layout.pages[0]
        assertFalse(first.fadeLeading)
        assertFalse("行边只剩 3dp 留白，渐隐会淡到数字上", first.fadeTrailing)
        // 第二组 132dp：留白 39dp ≥ 16dp ⇒ 往后没内容了，只该有左边那一条
        val second = layout.pages[1]
        assertTrue("还能往回翻却没给渐隐", second.fadeLeading)
        assertFalse("最后一页右边没有内容了还画渐隐", second.fadeTrailing)
    }

    @Test
    fun `the first page never promises a page to its left`() {
        val groups = groupUsageStatFields(fiveFields())
        val layout = planUsageStatLayout(groups, { fakeWidth(it) }, 100.dp)
        assertTrue(layout.pages.size > 1)
        assertFalse("第一页左边没有内容了还画渐隐", layout.pages.first().fadeLeading)
    }

    // ═══════════ 5. 静止态的几何：整页在里面或整页在外面 ═══════════

    @Test
    fun `at rest no page is left half inside the line`() {
        val groups = groupUsageStatFields(fiveFields())
        val viewport = 210.dp
        val layout = planUsageStatLayout(groups, { fakeWidth(it) }, viewport)
        val widthsPx = layout.pages.map { (it.width!!.value * 4f).toInt() }   // 假 density = 4
        val gapPx = (12.dp.value * 4f).toInt()
        val viewportPx = (viewport.value * 4f).toInt()

        repeat(layout.pages.size) { restingPage ->
            val translations = usageStatPageTranslationsPx(
                widthsPx, gapPx, viewportPx, restingPage, dragPx = 0f, rotates = true
            )
            val starts = usageStatNaturalStartsPx(widthsPx, gapPx)
            val lefts = translations.indices.map { translations[it] + starts[it] }

            lefts.forEachIndexed { index, left ->
                val right = left + widthsPx[index]
                if (index == restingPage) {
                    assertTrue(
                        "静止那一页没整页落进这一行：left=$left right=$right viewport=$viewportPx",
                        left >= -0.5f && right <= viewportPx + 0.5f
                    )
                } else {
                    assertTrue(
                        "第 $index 页压在行边上——用户看到的就是半个数字：left=$left right=$right",
                        left >= viewportPx - 0.5f || right <= 0.5f
                    )
                }
            }
        }
    }

    @Test
    fun `a line that holds everything puts every page exactly where the old flat row did`() {
        val groups = groupUsageStatFields(fiveFields())
        val layout = planUsageStatLayout(groups, { fakeWidth(it) }, 900.dp)
        val widthsPx = layout.pages.map { (it.width!!.value * 4f).toInt() }
        val gapPx = (12.dp.value * 4f).toInt()

        val translations = usageStatPageTranslationsPx(widthsPx, gapPx, 3600, 0, 0f, rotates = false)
        assertEquals(listOf(0f, 0f), translations)
        assertEquals(listOf(0f, (204f * 4f) + gapPx), usageStatNaturalStartsPx(widthsPx, gapPx))
    }

    @Test
    fun `dragging follows the finger on the current page and only pulls in its neighbour`() {
        val widthsPx = listOf(400, 300)
        val translations = usageStatPageTranslationsPx(widthsPx, 48, 800, page = 0, dragPx = -120f, rotates = true)
        val starts = usageStatNaturalStartsPx(widthsPx, 48)
        val lefts = translations.indices.map { translations[it] + starts[it] }

        assertEquals("当前页跟着手指走", 200.0 - 120.0, lefts[0].toDouble(), 0.5)
        assertEquals("下一页从行的右缘接进来", 800.0 - 120.0, lefts[1].toDouble(), 0.5)

        val backwards = usageStatPageTranslationsPx(widthsPx, 48, 800, page = 1, dragPx = 90f, rotates = true)
        val backLefts = backwards.indices.map { backwards[it] + starts[it] }
        assertEquals("往回翻时上一行从左边接进来", -400.0 + 90.0, backLefts[0].toDouble(), 0.5)
    }

    // ═══════════ 6. 定时与手动互不抢 ═══════════

    @Test
    fun `the rotation tick never jumps the group while the user is swiping`() {
        assertEquals("手指按住时不许往前跳组", 0, usageStatPageAfterTick(page = 0, pageCount = 2, dragging = true))
        assertEquals("手指按住时也不许绕回第一组", 1, usageStatPageAfterTick(page = 1, pageCount = 2, dragging = true))
        // 只有没被按住时才换
        assertEquals(1, usageStatPageAfterTick(page = 0, pageCount = 2, dragging = false))
        assertEquals("到末尾回到第一组", 0, usageStatPageAfterTick(page = 1, pageCount = 2, dragging = false))
        assertEquals(0, usageStatPageAfterTick(page = 0, pageCount = 1, dragging = false))
    }

    @Test
    fun `a light touch keeps the current group and a real swipe moves exactly one whole group`() {
        val viewport = 800
        assertEquals(0, usageStatPageAfterDrag(0, 2, dragPx = -40f, velocityPx = 0f, viewportPx = viewport))
        assertEquals(1, usageStatPageAfterDrag(0, 2, dragPx = -400f, velocityPx = 0f, viewportPx = viewport))
        assertEquals(0, usageStatPageAfterDrag(1, 2, dragPx = 400f, velocityPx = 0f, viewportPx = viewport))
        // 甩一下也算翻页，但一次只翻一组
        assertEquals(1, usageStatPageAfterDrag(0, 2, dragPx = -10f, velocityPx = -1200f, viewportPx = viewport))
        // 两端不越过
        assertEquals(0, usageStatPageAfterDrag(0, 2, dragPx = 900f, velocityPx = 2000f, viewportPx = viewport))
        assertEquals(1, usageStatPageAfterDrag(1, 2, dragPx = -900f, velocityPx = -2000f, viewportPx = viewport))
    }

    // ═══════════ 7. 间隔是"可读的几秒"，不是配置项也不是毫秒级闪烁 ═══════════

    @Test
    fun `the rotation interval is a readable few seconds and there is no second owner of it`() {
        assertTrue(
            "轮播间隔必须是看得清的几秒级常量，实测=$USAGE_STAT_GROUP_INTERVAL_MS",
            USAGE_STAT_GROUP_INTERVAL_MS in 2_000L..10_000L
        )
        // 间距与字号档沿用旧版那条：12dp 这一档来自设计系统的 Spacing.lg，不是本文件另发明的数
        assertEquals("间距沿用旧版那一档 12dp", 12.0, USAGE_STAT_GROUP_GAP.value.toDouble(), 0.01)
    }

    @Test
    fun `the tag anchors are namespaced so the bar can be told apart from the mode row`() {
        assertTrue(UsageStatTags.BAR.startsWith("usage_stat_"))
        assertTrue(UsageStatTags.STRIP.startsWith("usage_stat_"))
        assertTrue(UsageStatTags.PAGE_PREFIX.startsWith("usage_stat_"))
        assertFalse(UsageStatTags.BAR == UsageStatTags.STRIP)
    }
}
