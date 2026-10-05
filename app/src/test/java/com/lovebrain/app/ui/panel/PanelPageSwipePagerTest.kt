package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.ui.panel.counseling.CounselingTemplateChips
import com.lovebrain.app.ui.panel.reply.MESSAGE_ROW_TEST_TAG
import com.lovebrain.app.ui.panel.reply.MessageList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 第12节第2条「顶栏两个模式与滑动」的判据：回复 ↔ 谈心 两页左右滑互切、与点 tab 双向同步、
 * 页索引只有一个真 owner，切页保留各自草稿与谈心历史，以及那四条手势优先级仲裁
 * （第5节第3条 那一串顺序里与这一格有关的部分）。
 *
 * 每一格都写清了"哪种坏实现让它红"，而且**没有**"读不到对象就算过"的形状：
 * 该在树里的节点读不到，判据当场红，并把这几颗标签的实到计数一起交回读数。
 *
 * · **owner**：页只由外面传进来的那一颗决定（宿主页接的是 `composer.panelMode`）。
 *   反例：宿主自己 `remember` 一份内部页索引 ⇒ 外部改模式时画面不动；
 *   反例：滑动结束时既改内部又投回调 ⇒ 一记滑动投出两次写。
 * · **保留**：挂上就不再卸。反例：`if (page == 0) … else …`（切档即卸载另一页）或给页槽位
 *   套 `key(currentPage)` ⇒ 那一页 `remember` 里的草稿回不来。
 *   反例：两页一上来全挂 ⇒ "打开面板不许就去读谈心历史"红（那颗读的是盘，主线程）。
 * · **四条优先级**：①子层（气泡横滑删除 / 页内横向卡条）先消费就先赢；②没越过触控 slop
 *   或不是横向主导 ⇒ 不接管；③横向主导但目标页不存在 ⇒ 两端没有第三页；④剩下的区域才归切页；
 *   ⑤头部拖移是另一层的事。①各有一格用**生产子件**跑，并且每格同时判"子件真的动了"与
 *   "页没切"，再用"同一记行程落在页面其余区域必须切得动"当正控制——只判后半的话
 *   "父层根本没手势"也能绿，那是恒真。
 *
 * 真机边界（不假装测过）：手指跟手手感、IME 与悬浮窗 flags 的实际起落仍归真机验收；
 * 这里量的是几何、语义、回调落点与"那一页的状态还活不活着"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelPageSwipePagerTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density

    // 视口取 360dp：面板真实宽度里最窄的一档，也是"擦一下就换页"最容易出事的那一档
    private val cell = UiMatrix(PAGE_W_DP, PAGE_H_DP)
    private val pageWidthPx: Float get() = PAGE_W_DP * density

    /** 提交线在这一格的具体位置（px）：行程与阈值的关系全靠它算出来，不用仪器默认值 */
    private val commitLine: Float get() = PageSwipe.commitLineFor(pageWidthPx)

    // ───────────────────────────── 夹具 ─────────────────────────────

    private val markTags = listOf("lb_test_page_mark_0", "lb_test_page_mark_1")
    private val fillerTag = "lb_test_page_filler"
    private val headerSlotTag = "lb_test_header_slot"
    private val tapTargetTag = "lb_test_tap_target"
    private val scrollRowTag = "lb_test_scroll_row_0"

    /** 每一页被**挂载**过几次（自增放在 `remember` 里 ⇒ 只有新建组合才涨，重组成不算） */
    private val mountCounts = intArrayOf(0, 0)

    /** 各页 `remember` 里那份"本地草稿"被改了几次（切页不许把它冲掉） */
    private val draftEdits = intArrayOf(0, 0)

    private val pageRequests = mutableListOf<Int>()

    /**
     * 模式那一颗真 owner 的测试替身。写法照生产里 `PanelSurfaceHolder` 那一颗
     * （`LoveBrainPanelScreen.kt:113`，它是 main 里的 private 类，测试引用不到，只照写法不连符号）：
     * `var … by mutableIntStateOf(…)`，这样"改模式"与"读模式"在测试里只有这一颗可改。
     */
    private inner class PageMode(initial: Int = 0) {
        var value: Int by mutableIntStateOf(initial)
    }

    private fun resetCounters() {
        mountCounts.fill(0)
        draftEdits.fill(0)
        pageRequests.clear()
    }

    /** 失败消息里的实到读数：只列我认识的这几颗标签，不参与红绿 */
    private fun tree(): String =
        (markTags + listOf(fillerTag, MESSAGE_ROW_TEST_TAG, tapTargetTag, headerSlotTag))
            .joinToString("; ") { "$it=${nodesOf(it).fetchSemanticsNodes().size}" }

    /**
     * 一页最简正文：一颗可点标记（它 `remember` 里那份值就是这一页的本地草稿）+ 一行
     * "页面其余区域"（空白填充，切页手势的落点）。
     */
    @Composable
    private fun SimplePage(index: Int, extra: @Composable () -> Unit = {}) {
        remember(index) { mountCounts[index] += 1 }
        var draft by remember { mutableStateOf("draft-$index") }
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag(markTags[index])
                    .clickable {
                        draftEdits[index]++
                        draft = "edited-$index"
                    }
            ) {
                Text(text = draft)
            }
            extra()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(fillerTag)
            )
        }
    }

    private fun mountPager(
        mode: PageMode,
        onPageChange: (Int) -> Unit = { page -> pageRequests.add(page); mode.value = page },
        pageExtra: @Composable (Int) -> Unit = {}
    ) {
        resetCounters()
        rule.setContent {
            cell.RenderIn(LocalDensity.current.density) {
                PanelPagePager(
                    currentPage = panelModeToPage(mode.value),
                    onPageChange = onPageChange,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    SimplePage(page) { pageExtra(page) }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun nodesOf(tag: String) = rule.onAllNodes(hasTestTag(tag))
    private fun firstInteraction(tag: String) = nodesOf(tag).onFirst()
    private fun firstNode(tag: String) = firstInteraction(tag).fetchSemanticsNode()

    /** 当前画着的那一页的位置读数：页槽位的平移挂在 `graphicsLayer` 上，语义边界跟着图层走 */
    private fun markLeft(index: Int): Double {
        val nodes = nodesOf(markTags[index]).fetchSemanticsNodes()
        assertTrue(
            "第 $index 页的标记节点该在语义树里（切页不许把那一页卸掉）。实到：" + tree(),
            nodes.isNotEmpty()
        )
        return nodes.first().boundsInRoot.left.toDouble()
    }

    /** 填充行（页面其余区域）的横向落点：页没切时它必须还在 0 */
    private fun fillerLeft(): Double = firstNode(fillerTag).boundsInRoot.left.toDouble()

    private fun assertPresent(tag: String, why: String) {
        val size = nodesOf(tag).fetchSemanticsNodes().size
        assertTrue("$why。实到「$tag」=$size；整片读数：" + tree(), size > 0)
    }

    /** 走完一次手势的余波：过渡动画（220ms）与 owner 交接兜底（260ms）都要落地 */
    private fun settle() {
        rule.mainClock.advanceTimeBy(400L)
        rule.runOnIdle { }
    }

    /**
     * 从"页面其余区域"起按、按已知像素横走、抬指。
     * 起点先算好（不在注入过程中读树），行程与阈值的关系是**算出来的**。
     */
    private fun dragOnFiller(travelPx: Float, steps: Int = 6) {
        val anchor = firstNode(fillerTag).boundsInRoot
        val start = Offset(anchor.center.x, anchor.center.y)
        firstInteraction(PANEL_PAGE_PAGER_TEST_TAG).performTouchInput {
            down(start)
            for (i in 1..steps) moveTo(Offset(start.x + travelPx * i / steps, start.y))
            up()
        }
        settle()
    }

    /** 同一条注入路径，但起点画在**给定那一格**的像素上（子件自己的地盘：气泡行） */
    private fun dragFromNode(startTag: String, travelPx: Float, steps: Int = 4) {
        val anchor = firstNode(startTag).boundsInRoot
        val start = Offset(anchor.center.x, anchor.center.y)
        firstInteraction(PANEL_PAGE_PAGER_TEST_TAG).performTouchInput {
            down(start)
            for (i in 1..steps) moveTo(Offset(start.x + travelPx * i / steps, start.y))
            up()
        }
        settle()
    }

    // ─────────────────── A. 仲裁判据本身（纯函数） ───────────────────

    /**
     * ① **子层已消费位移 = 让位**，而且这一档排在所有判据**之前**：横向再足、目标页再存在，
     * 也不许换页。
     * 反例：把 `childConsumedPosition` 挪到"横向主导"之后判 ⇒ 三格全红
     *   （气泡横滑删除与页内卡条被父层抢走）；
     * 反例：只在手势第一帧读这颗 ⇒ 后面几帧又试着切页，同样红在这里。
     */
    @Test
    fun `子层消费过位移就绝不再试着切页`() {
        assertEquals(
            "子层认领的像素，横移再大也不许切",
            PageSwipeOutcome.DeferToChild,
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, -900f, 0f, 8f, true)
        )
        assertEquals(
            "谈心页右滑回回复这一档同样让位",
            PageSwipeOutcome.DeferToChild,
            PageSwipe.resolve(1, PANEL_PAGE_COUNT, 1000f, 900f, 0f, 8f, true)
        )
        assertEquals(
            "谈心页左滑（本来就没有第三页）也让位，而不是回绕",
            PageSwipeOutcome.DeferToChild,
            PageSwipe.resolve(1, PANEL_PAGE_COUNT, 1000f, -900f, 0f, 8f, true)
        )
    }

    /**
     * ② **没越过触控 slop / 不是横向主导** ⇒ 不接管也不消费：纵向浏览与点击原样归原来的主人。
     * 反例：slop 判成 `>=`（恰好等于就接管）⇒ `dx == slop` 那一格红；
     * 反例：只判 `abs(dx) > slop` 不判横向主导 ⇒ 对角线那一格红（往下滚被切成换页）；
     * 反例：slop 读成 0/负数（把 dp 当 px 用）⇒ 最后一格红（1px 也能换页）。
     */
    @Test
    fun `没有越过触控slop或者不是横向主导都不接管`() {
        fun r(dx: Float, dy: Float, slop: Float = 8f) =
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, dx, dy, slop, false)
        assertEquals(PageSwipeOutcome.NotAHorizontalDrag, r(0f, 0f))
        assertEquals("1px 不算越过 slop", PageSwipeOutcome.NotAHorizontalDrag, r(1f, 0f))
        assertEquals("恰好等于 slop 也不算越过", PageSwipeOutcome.NotAHorizontalDrag, r(8f, 0f))
        assertEquals("横向还没赢过纵向", PageSwipeOutcome.NotAHorizontalDrag, r(30f, 40f))
        assertEquals("横向主导才接管", PageSwipeOutcome.Switching(1, -20f), r(-20f, 5f))
        assertEquals(
            "slop 读成 0 是坏读数，这一档一律不接管",
            PageSwipeOutcome.NotAHorizontalDrag,
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, -500f, 0f, 0f, false)
        )
    }

    /**
     * ③④ **方向、页数与跟手行程**：回复左滑进谈心、谈心右滑回回复，**两端没有第三页**，也不回绕。
     * 反例：目标页不做界内检查 ⇒ 页 1 左滑那两格红（凭空多出第三页）；
     * 反例：`(currentPage + 1) % pageCount` 回绕 ⇒ 同一格红；
     * 反例：方向弄反（dx>0 当下一页）⇒ 前两格红；
     * 反例：夹行程用屏幕宽而不是页宽、或页宽没量到时不做门 ⇒ 后三格红。
     */
    @Test
    fun `方向对应目标页而两端都没有第三页`() {
        assertEquals(
            "回复左滑 = 谈心",
            PageSwipeOutcome.Switching(1, -100f),
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, -100f, 0f, 8f, false)
        )
        assertEquals(
            "谈心右滑 = 回回复",
            PageSwipeOutcome.Switching(0, 100f),
            PageSwipe.resolve(1, PANEL_PAGE_COUNT, 1000f, 100f, 0f, 8f, false)
        )
        assertEquals(
            "回复再右滑：左边没有页",
            PageSwipeOutcome.BlockedAtEdge,
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, 100f, 0f, 8f, false)
        )
        assertEquals(
            "谈心再左滑：右边没有页（不许出现第三页）",
            PageSwipeOutcome.BlockedAtEdge,
            PageSwipe.resolve(1, PANEL_PAGE_COUNT, 1000f, -100f, 0f, 8f, false)
        )
        assertEquals(
            "回复左滑（dx<0）→ 下一页 = 谈心（与上面 resolve 的 Switching(1, -100f) 同一条方向）",
            1, PageSwipe.targetPage(0, -50f, PANEL_PAGE_COUNT)
        )
        assertEquals(
            "谈心右滑（dx>0）→ 上一页 = 回复（与上面 resolve 的 Switching(0, 100f) 同一条方向）",
            0, PageSwipe.targetPage(1, 50f, PANEL_PAGE_COUNT)
        )
        assertEquals(null, PageSwipe.targetPage(1, -50f, PANEL_PAGE_COUNT))
        assertEquals(null, PageSwipe.targetPage(0, 50f, PANEL_PAGE_COUNT))
        assertEquals(null, PageSwipe.targetPage(0, 0f, PANEL_PAGE_COUNT))
        assertEquals(-1000.0, PageSwipe.clampDragOffset(-1500f, 1000f).toDouble(), 0.0)
        assertEquals(
            "页宽没量到（第一帧）不许画出去",
            0.0, PageSwipe.clampDragOffset(-500f, 0f).toDouble(), 0.0
        )
        assertEquals("负页宽是坏读数", 0.0, PageSwipe.clampDragOffset(-500f, -1f).toDouble(), 0.0)
    }

    /**
     * 松手换页**只看行程**过没过那条线，线的位置是页宽的一个比；线的两侧与压线各钉一格，
     * 两个方向同一条线；在两端即使过线也不许切。
     * 反例：比从 0.34 抬到 0.5 ⇒ `commitLineFor(1000)=340` 与"压线该换"两格红
     *   （窄面板上滑到底也换不了页）；
     * 反例：比调到 0.1 ⇒ "差一点点不许换"红（擦一下就换页）；
     * 反例：给甩速开后门 ⇒ 判据签名里压根没有速度这个入参，"短行程那一格"照样红在这里；
     * 反例：页宽 0 时把 0 行程当够线 ⇒ "页宽没量到"那一格红。
     */
    @Test
    fun `松手换页只看行程过没过那条线`() {
        assertEquals(340.0, PageSwipe.commitLineFor(1000f).toDouble(), 0.0)
        assertTrue("正好压线就该换（判据是 >=，不是 >）", PageSwipe.shouldCommit(-340f, 1000f))
        assertTrue("向左同样过线", PageSwipe.shouldCommit(340f, 1000f))
        assertFalse("差一点点不许换（339.99 < 340）", PageSwipe.shouldCommit(-339.99f, 1000f))
        assertFalse("左右两侧同一条线", PageSwipe.shouldCommit(339.99f, 1000f))
        assertFalse("页宽没量到不许凭空够线", PageSwipe.shouldCommit(-500f, 0f))
        assertEquals("过线：回复 → 谈心", 1, PageSwipe.commitTarget(0, -400f, 1000f, PANEL_PAGE_COUNT))
        assertEquals("过线：谈心 → 回复", 0, PageSwipe.commitTarget(1, 400f, 1000f, PANEL_PAGE_COUNT))
        assertEquals("没过线：留在原页", 0, PageSwipe.commitTarget(0, -100f, 1000f, PANEL_PAGE_COUNT))
        assertEquals(
            "过线但已经是最右一页：还是原页（不许切出不存在的第三页）",
            1, PageSwipe.commitTarget(1, -400f, 1000f, PANEL_PAGE_COUNT)
        )
        assertEquals(
            "过线但已经是最左一页：还是原页",
            0, PageSwipe.commitTarget(0, 400f, 1000f, PANEL_PAGE_COUNT)
        )
    }

    /**
     * `panelMode ↔ 页索引` 只有那**一对**函数：两个合法值上往返恒等、界外值一律落回回复。
     * 反例：页头与滑页宿主各写一份 `if (panelMode == 1)` ⇒ 换算漂移没人拦；
     * 反例：换算写成把页索引直当模式（不归一）⇒ 界外两格红（VM 收到 2 就成了第三页）。
     */
    @Test
    fun `模式与页索引的换算是同一对函数`() {
        assertEquals(0, panelModeToPage(0))
        assertEquals(1, panelModeToPage(1))
        assertEquals("界外模式落回回复", 0, panelModeToPage(2))
        assertEquals("界外模式落回回复", 0, panelModeToPage(-3))
        assertEquals(0, panelPageToMode(0))
        assertEquals(1, panelPageToMode(1))
        assertEquals("界外页落回回复", 0, panelPageToMode(7))
        assertEquals(listOf(0, 1), listOf(0, 1).map { panelPageToMode(panelModeToPage(it)) })
        assertEquals("页数就两页", 2, PANEL_PAGE_COUNT)
    }

    // ─────────────────── B. 宿主行为（真注入手势） ───────────────────

    /**
     * **只有一颗真 owner**：画面跟着外面那一颗走；滑动只投**一次**写，投完再由那一颗决定画面。
     * 点 tab 与左右滑是同一条路（双向同步），没有第二条。
     *
     * 反例：宿主内部 `remember` 一份页索引、只在自己的手势里改它 ⇒ 第一句红
     *   （外部把模式改成谈心，画面还停在回复）；
     * 反例：滑动结束时既改内部又投回调、或 `onDragEnd` 里投两次 ⇒ "一记滑动只许投一次写"红
     *   （两次写会打到 `setPanelMode` 那条模式通道）；
     * 反例：滑动只改画不回投 owner（单向同步）⇒ 最后一句红（一次写都没有）。
     */
    @Test
    fun `只有一颗真owner：改页只有一条路`() {
        val mode = PageMode()
        mountPager(mode)
        assertEquals("起始画的是回复那一页", 0.0, markLeft(0), 1.0)
        assertEquals("起始投过零次写", 0, pageRequests.size)

        // 点 tab 那条路：外部那一颗变 → 画面必须跟着换
        rule.runOnIdle { mode.value = 1 }
        settle()
        assertEquals("owner 换成谈心后，谈心那一页画在 0", 0.0, markLeft(1), 1.0)

        // 滑动那条路：只投一次写，落点是谈心回回复
        rule.runOnIdle { pageRequests.clear() }
        dragOnFiller(commitLine * 1.4f)
        assertEquals("一记滑动只许投一次写：" + pageRequests, listOf(0), pageRequests.toList())
        assertEquals("投完由 owner 决定画面：回复那一页回到 0", 0.0, markLeft(0), 1.0)

        // 再往左滑一次：进谈心；到边之后继续左滑——不许有第三页
        rule.runOnIdle { pageRequests.clear() }
        dragOnFiller(-commitLine * 1.4f)
        assertEquals(listOf(1), pageRequests.toList())
        assertEquals(0.0, markLeft(1), 1.0)
        rule.runOnIdle { pageRequests.clear() }
        dragOnFiller(-commitLine * 1.4f)
        assertTrue("最右一页继续左滑不许投任何写：" + pageRequests, pageRequests.isEmpty())
        assertEquals("到边那一页仍画在 0（不许留半张页）", 0.0, markLeft(1), 1.0)
    }

    /**
     * **切页保留各自草稿与谈心历史**：挂上就不再卸；同时"打开面板那一帧不许就挂谈心那页"
     * （谈心那页的历史在 `CounselingPanel.kt:240` 从 VM 现读，而那颗读的是盘）。
     *
     * 反例：`if (page == 0) … else …` 那种切档即卸载，或给页槽位套 `key(currentPage)`
     *   （每次换页重建子树）⇒ "切回来那份草稿还在"红；
     * 反例：一开始两页全挂 ⇒ "打开面板不许就挂上谈心那页"红；
     * 反例：藏起来那页没清语义 ⇒ "不许留在读屏树里"红（读屏会念到屏幕上根本看不见的另一页）。
     */
    @Test
    fun `切页保留各自草稿与谈心历史`() {
        val mode = PageMode()
        mountPager(mode)
        assertEquals("打开面板不许就挂上谈心那页", 0, mountCounts[1])
        assertTrue("回复那一页该挂着", mountCounts[0] > 0)

        // 在回复那一页写下草稿
        firstInteraction(markTags[0]).performClick()
        rule.waitForIdle()
        assertEquals(1, draftEdits[0])

        // 切到谈心（点 tab 那条路），在谈心里也写一份
        rule.runOnIdle { mode.value = 1 }
        settle()
        assertTrue("谈心那页第一次被挂上", mountCounts[1] > 0)
        firstInteraction(markTags[1]).performClick()
        rule.waitForIdle()
        assertEquals(1, draftEdits[1])
        assertEquals(
            "屏外那一页不许留在读屏树里",
            0,
            nodesOf(markTags[0]).fetchSemanticsNodes().size
        )

        // 切回回复、再切回谈心：两份本地状态都还在，而且两页都没被重挂
        rule.runOnIdle { mode.value = 0 }
        settle()
        assertEquals("切回来那份草稿还在（改动次数没被冲掉）", 1, draftEdits[0])
        rule.runOnIdle { mode.value = 1 }
        settle()
        assertEquals("谈心那页的本地历史/草稿同样还在", 1, draftEdits[1])
        assertEquals("切来切去不许重挂谈心那页（重挂 = 状态丢）", 1, mountCounts[1])
        assertEquals("回复那页同样只挂过一次", 1, mountCounts[0])
    }

    /**
     * ②④ **只有页面其余区域才切页**：够线才换、快擦不换，而点击与纵向滚动都不许被父层吃掉
     * （第5节第3条 明写"不能在整个面板最外层无条件 consume 全部移动事件"）。
     *
     * 反例：换成 `detectHorizontalDragGestures` 或从 `Initial` 起手势（父层先吃）⇒
     *   "点得动"与"纵向滚得动"两半都红；
     * 反例：一接管就换页（不看提交线）⇒ "短行程快擦不许换页"红；
     * 反例：不够线时忘了弹回（松手不回 0）⇒ "弹回之后还在 0"红（半张页留在屏上）；
     * 反例：提交线写成固定 px 而不是页宽比 ⇒ 换视口时最后两格红（一档过线一档不过线）。
     */
    @Test
    fun `页面其余区域才切页：点与纵向滚动都不该被吃掉`() {
        val mode = PageMode()
        val scrollValueRef = arrayOfNulls<Int>(1)
        var tapped = 0
        mountPager(
            mode,
            pageExtra = { index ->
                if (index == 0) {
                    val scroll = rememberScrollState()
                    // 在组合里读一次：子层滚起来会让这一格重组，读数跟着更新
                    scrollValueRef[0] = scroll.value
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .verticalScroll(scroll)
                    ) {
                        repeat(12) { row ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .testTag("lb_test_scroll_row_$row")
                            ) { Text(text = "row $row") }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(tapTargetTag)
                            .clickable { tapped++ }
                    )
                }
            }
        )

        // ① 点击原样归子件
        assertPresent(tapTargetTag, "页内那颗可点目标该画得出来")
        firstInteraction(tapTargetTag).performClick()
        rule.waitForIdle()
        assertEquals("父层不许把点击吃掉", 1, tapped)
        assertTrue("没滑动就不许投换页：" + pageRequests, pageRequests.isEmpty())

        // ② 纵向拖动原样归子层的滚动（父层不许无条件吃移动事件）
        val before = scrollValueRef[0] ?: 0
        val row = firstNode(scrollRowTag).boundsInRoot
        val rowStart = Offset(row.center.x, row.center.y)
        firstInteraction(PANEL_PAGE_PAGER_TEST_TAG).performTouchInput {
            down(rowStart)
            moveTo(Offset(rowStart.x, rowStart.y - 120f))
            moveTo(Offset(rowStart.x, rowStart.y - 260f))
            up()
        }
        settle()
        val after = scrollValueRef[0] ?: 0
        assertTrue("纵向拖动该让子层自己滚起来（before=$before after=$after）", after > before)
        assertTrue("纵向拖动不许换页：" + pageRequests, pageRequests.isEmpty())
        assertEquals("纵向拖动之后页面还在原位", 0.0, markLeft(0), 1.0)

        // ③ 快擦（越过 slop、没越过提交线）：不换页，页面自己弹回原位
        rule.runOnIdle { pageRequests.clear() }
        dragOnFiller(-commitLine * 0.4f)
        assertTrue("短行程快擦不许换页：" + pageRequests, pageRequests.isEmpty())
        assertEquals("弹回之后回复那一页还在 0", 0.0, markLeft(0), 1.0)

        // ④ 够线的横滑：换页，而且只写一次
        rule.runOnIdle { pageRequests.clear() }
        dragOnFiller(-commitLine * 1.4f)
        assertEquals("够线就该换页：" + pageRequests, listOf(1), pageRequests.toList())
        assertEquals("换完画的是谈心那一页", 0.0, markLeft(1), 1.0)
    }

    /**
     * ① **气泡行横滑删除优先**（生产 `MessageList` 挂在页里）：把一条气泡滑过删除线
     * （行宽四成）——删除要真发生，而且这一记**不许**同时把页切走。
     *
     * 这一格两半互相对账：后半把同一记行程落在"页面其余区域"，页**必须**切得动；
     * 少了后半，"父层根本没手势"也能绿，那是恒真。
     * 反例：父层从 `Initial` 起势或在 slop 处 `consume()` 整颗 change ⇒ 前半红
     *   （横滑删不动了，用户只剩读屏那一条路）；
     * 反例：父层不看 `positionChangeConsumed()` ⇒ "不许同时把页切走"那句红
     *   （一滑同时删消息又换页）。
     */
    @Test
    fun `气泡行横滑删除优先于切页`() {
        val mode = PageMode()
        val her = ChatMessage(role = ChatMessage.Role.HER, content = "在吗")
        val me = ChatMessage(role = ChatMessage.Role.ME, content = "在的")
        val deleted = mutableListOf<String>()
        resetCounters()
        rule.setContent {
            cell.RenderIn(LocalDensity.current.density) {
                PanelPagePager(
                    currentPage = panelModeToPage(mode.value),
                    onPageChange = { page -> pageRequests.add(page); mode.value = page }
                ) { page ->
                    if (page == 0) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            MessageList(
                                messages = listOf(her, me),
                                editingIndex = -1,
                                onReorder = { _, _ -> },
                                onEdit = { },
                                onDelete = { deleted.add(it) },
                                modifier = Modifier.fillMaxWidth().height(160.dp)
                            )
                            Box(Modifier.fillMaxWidth().weight(1f).testTag(fillerTag))
                        }
                    } else {
                        SimplePage(page)
                    }
                }
            }
        }
        rule.waitForIdle()
        pageRequests.clear()
        assertPresent(MESSAGE_ROW_TEST_TAG, "生产消息行该画在页里（读不到就不许空过）")

        // 过删除线（行宽四成）的一记横滑：删的是这一条，页一动不动
        dragFromNode(MESSAGE_ROW_TEST_TAG, -pageWidthPx * 0.5f)
        rule.mainClock.advanceTimeBy(320L)   // 退场那 200ms 收尾才投递删除
        rule.runOnIdle { }
        assertEquals("过阈值就该删这一条：" + deleted, listOf(her.id), deleted)
        assertTrue("气泡行上的横滑不许同时把页切走：" + pageRequests, pageRequests.isEmpty())
        assertEquals("页面仍画在回复那一页", 0.0, fillerLeft(), 1.0)

        // 后半（正控制）：同一记行程落在页面其余区域，页必须切得动
        dragOnFiller(-commitLine * 1.4f)
        assertEquals("同一次测量里，非气泡区域该能切页：" + pageRequests, listOf(1), pageRequests.toList())
    }

    /**
     * ① **页内横向卡条优先自己的滚动**（生产 `CounselingTemplateChips`：一行 `horizontalScroll`）。
     * 横滑那一行 ⇒ 这一行自己滚（首颗 chip 的落点左移），页面**不许**跟着切。
     *
     * 反例：父层抢在子层前面吃位移 ⇒ "首颗 chip 左移"红（这一行再也滑不动，尾部那几颗永远看不见）；
     * 反例：父层无视消费 ⇒ "不许同时切页"红。
     */
    @Test
    fun `页内横向卡条优先自己的滚动`() {
        val mode = PageMode(initial = 1)   // 直接停在谈心那一页
        resetCounters()
        rule.setContent {
            cell.RenderIn(LocalDensity.current.density) {
                PanelPagePager(
                    currentPage = panelModeToPage(mode.value),
                    onPageChange = { page -> pageRequests.add(page); mode.value = page }
                ) { page ->
                    if (page == 1) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            CounselingTemplateChips(onTemplateSelect = { })
                            Box(Modifier.fillMaxWidth().weight(1f).testTag(fillerTag))
                        }
                    } else {
                        SimplePage(page)
                    }
                }
            }
        }
        rule.waitForIdle()
        pageRequests.clear()

        val chipLabel = "她突然冷淡了怎么办"
        val before = rule.onAllNodes(hasText(chipLabel, substring = true)).fetchSemanticsNodes()
        assertTrue("谈心页里的模板 chip 行该画得出来（读不到就不许空过）。实到=${before.size}", before.isNotEmpty())
        val beforeBounds = before.first().boundsInRoot
        val beforeLeft = beforeBounds.left
        val beforeRight = beforeBounds.right
        val chipRowTop = beforeBounds.top

        // 起点画在 chip 那一行上，横滑一段**够父层提交线**的行程：够线都不许切，因为子层先消费
        val start = Offset(beforeLeft + 40f, chipRowTop + 10f)
        firstInteraction(PANEL_PAGE_PAGER_TEST_TAG).performTouchInput {
            down(start)
            moveTo(Offset(start.x - pageWidthPx * 0.22f, start.y))
            moveTo(Offset(start.x - pageWidthPx * 0.45f, start.y))
            up()
        }
        settle()

        val after = rule.onAllNodes(hasText(chipLabel, substring = true)).fetchSemanticsNodes()
        assertTrue("滑完这一行还在（不许被切页切掉）", after.isNotEmpty())
        val afterRight = after.first().boundsInRoot.right
        // 首颗 `boundsInRoot.left` 判不出滚动：它贴在视口左沿，被 scroll 容器裁掉之后 `left` 恒为 0
        // （见 CounselingTemplateChipTest 类头那句「滚出视口的那几颗在语义树里被压成 0x0 或半截」）。
        // 首颗右沿才跟着 `scrollState.value` 走：没滚=chipWidth，滚了 S=chipWidth-S。
        assertTrue(
            "chip 行自己该滚起来：首颗右缘向左收窄。beforeRight=$beforeRight afterRight=$afterRight",
            afterRight < beforeRight
        )
        assertTrue("页内横向卡条上的横滑不许同时切页：" + pageRequests, pageRequests.isEmpty())
        assertEquals("谈心那一页仍画在 0", 0.0, fillerLeft(), 1.0)
    }

    /**
     * ⑤ **头部拖移窗口仍可用**，而且头部那一行不是切页的手势道：横滑页头 ⇒
     * 窗口拖移回调在动、页面一次都不切。
     *
     * 反例：把滑页手势挂到面板最外层（一整窗一层）⇒ 后半红（拖头部把页切了，窗口挪不动）；
     * 反例：滑页宿主把手势伸出页槽位之外 ⇒ 前半红（头部再也拖不出位移）。
     */
    @Test
    fun `头部拖移仍是头部的事`() {
        val mode = PageMode()
        var headerDrags = 0
        resetCounters()
        rule.setContent {
            cell.RenderIn(LocalDensity.current.density) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().testTag(headerSlotTag)) {
                        PanelHeader(
                            panelMode = mode.value,
                            onModeChange = { mode.value = it },
                            onCollapse = {},
                            onHeaderDrag = { _, _ -> headerDrags++ }
                        )
                    }
                    PanelPagePager(
                        currentPage = panelModeToPage(mode.value),
                        onPageChange = { page -> pageRequests.add(page); mode.value = page },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) { page -> SimplePage(page) }
                }
            }
        }
        rule.waitForIdle()
        pageRequests.clear()

        val header = firstNode(headerSlotTag).boundsInRoot
        val pager = firstNode(PANEL_PAGE_PAGER_TEST_TAG).boundsInRoot
        assertTrue(
            "头部那一格该在滑页槽位**上面**（两者不重叠，否则手势域就串了）：" +
                "header.bottom=${header.bottom} pager.top=${pager.top}",
            header.bottom <= pager.top + 1f
        )
        // 从头部那一行起按、横走一段够线的行程（注入落在头部那颗粒上，不碰页槽位）
        val start = Offset(header.center.x, header.center.y)
        firstInteraction(headerSlotTag).performTouchInput {
            down(start)
            for (i in 1..6) moveTo(Offset(start.x - 25f * i, start.y))
            up()
        }
        settle()
        assertTrue("头部横滑该还是窗口拖移（回调要有动静）：$headerDrags", headerDrags > 0)
        assertTrue("头部不是切页道：" + pageRequests, pageRequests.isEmpty())
        assertEquals("头部拖完页面还在回复那一页", 0.0, markLeft(0), 1.0)
    }

    /**
     * 双向同步的**端到端**那一格：页头那两段与滑页宿主共用同一颗 `panelMode`。
     * 点 tab ⇒ 页面换；滑页面 ⇒ 头部的选中态跟着换。两条路读写的必须是同一颗数。
     *
     * 反例：头部自己记一份 selectedIndex、滑页宿主再记一份 ⇒ 后半红
     *   （滑动之后头部还亮着旧的那一段）；
     * 反例：宿主滑动只改画不回投模式 ⇒ 同样红；
     * 反例：只有头部能动（单向同步）⇒ 前半红。
     */
    @Test
    fun `点tab与左右滑是同一条路：双向同步`() {
        val mode = PageMode()
        resetCounters()
        rule.setContent {
            cell.RenderIn(LocalDensity.current.density) {
                Column(modifier = Modifier.fillMaxSize()) {
                    PanelHeader(
                        panelMode = mode.value,
                        onModeChange = { mode.value = it },
                        onCollapse = {}
                    )
                    PanelPagePager(
                        currentPage = panelModeToPage(mode.value),
                        onPageChange = { page -> mode.value = panelPageToMode(page) },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) { page -> SimplePage(page) }
                }
            }
        }
        rule.waitForIdle()

        val replyLabel = ctx.getString(R.string.panel_mode_reply)
        val counselingLabel = ctx.getString(R.string.panel_mode_counseling)

        fun selected(label: String): Boolean {
            val nodes = rule.onAllNodes(hasText(label)).fetchSemanticsNodes()
            assertTrue("页头该读得到那一段「$label」，实到=${nodes.size}；" + tree(), nodes.isNotEmpty())
            // 同名标签只许一颗：两颗就是"第二本账"的形状（另一处也在画同一段文字并自带选中态），
            // 那时 `first()` 读到哪一颗全凭树里的顺序，断言就不再钉住头部那一颗
            assertEquals("「$label」这一段在语义树里只许一颗，实到=${nodes.size}；" + tree(), 1, nodes.size)
            // `getOrNull` 是 `androidx.compose.ui.semantics` 的那颗扩展函数（不是 List/Result 那颗同名
            // 扩展），少这一句 import 这一格就编译不过：报在 receiver 类型不匹配上。
            // 选中态的写点在 `PanelHeader.kt` 的 `ModeSegmentLabel`（`semantics { this.selected = … }`），
            // 读的就是这一段自己那颗粒的 `SemanticsProperties.Selected`。
            return nodes.first().config.getOrNull(SemanticsProperties.Selected) == true
        }

        assertTrue("起点：回复那段是选中态", selected(replyLabel))
        assertFalse(selected(counselingLabel))

        // 点 tab → 页面换
        rule.onAllNodes(hasText(counselingLabel)).onFirst().performClick()
        settle()
        assertEquals("点 tab 之后画的是谈心那一页", 0.0, markLeft(1), 1.0)
        assertTrue(selected(counselingLabel))

        // 滑页面 → 头部的选中态跟着换（同一条路，另一个方向）
        dragOnFiller(commitLine * 1.4f)
        assertEquals("右滑之后画的是回复那一页", 0.0, markLeft(0), 1.0)
        assertTrue("滑完之后头部那段高亮也回来了", selected(replyLabel))
        assertFalse("另一段不许还挂着选中态（第二本账的形状）", selected(counselingLabel))
    }

    private companion object {
        const val PAGE_W_DP = 360
        const val PAGE_H_DP = 600
    }
}
