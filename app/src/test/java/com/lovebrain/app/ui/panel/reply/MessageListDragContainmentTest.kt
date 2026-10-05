package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.domain.prompt.ChatTranscriptBlock
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 原话第 19 条（优化版第 10 条）在**语义树**上的那一半：屏上顺序、落库顺序、喂模型的顺序
 * 必须是同一个读数；并且"不许给短的点击绑定移动"。
 *
 * 挂载走宿主那条形状：`store.messages`（同一颗 StateFlow）`collectAsState()` 进组合，
 * `onReorder` 直接投 `ComposerStore.Intent.ReorderMessages`——于是"重排落库的那份列表"与
 * "拼进 prompt 的那份列表"在这一格里**本来就是同一颗读数**，这里判的就是它有没有分叉：
 * · **顺序**：条目在树上的先后（按 top 排）== `store.messagesNow` == `ChatTranscriptBlock`
 *   里 JSON 行的先后（3 条与 20 条各一格；20 条那格先数够 20 颗再说同序）；
 * · **落库之后屏幕跟着翻**：把拖拽会交出的重排对投进 store，树上的先后必须换成新顺序，
 *   且与 prompt 再度同序（反例：屏侧留第二本账 / 自己再排一次 ⇒ 这一格红，那正是分叉的形状）；
 * · **短点击只编辑、不移动**：一记 `performClick` ⇒ `onEdit` 恰好一次（交的是那条自己的
 *   原列表下标）、`onReorder` 零次、顺序一字不变；
 * · **纵向擦动也不移动**：一记 `swipeUp` ⇒ `onReorder` 零次、`onDelete` 零次、条目一条不少，
 *   同一格后半再点一次仍然投编辑（正控制：手势真的落在行上，不是"什么都没发生"的恒真）。
 *
 * 两处读树的老规矩照本仓库既有写法走：
 * · 正文的 top 从**未合并树**那颗 `Text` 上读——行节点写了 `semantics(mergeDescendants = true)`，
 *   合并会把子树摘出去（`ChatBubbleAppearanceTest` 为此踩过一整轮红）；
 * · 每颗都要过 `0x0` 那道筛：滚动容器会把没摆出来的那颗报成 `0x0@(0,0)`，
 *   它既不算"看得见"，也会把"最上/最下"这类锚点判据顶掉（`SemanticsProbe.laid` 那一族坑）。
 *
 * ⚠ **"是哪两格在换"不由这里判**：长按 + 连续拖动在本机注入不出稳定时序（同一记录见
 *   `MessageRowDragFollowTest`、`MessageRowSwipeDeleteTest` 开头），注入不出来的那一轴交给
 *   `MessageListDragOrderTest` 的纯函数时间线去钉。
 * ⚠ **跟不跟手、指下到底看不看得见**语义树也量不到：位移是手势逐帧写进 graphicsLayer 的，
 *   JVM 上没有手指。那两样归真机录屏（台账标"未验证-需真机"），这里不许自称"手感已修好"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageListDragContainmentTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val reorders = mutableListOf<Pair<Int, Int>>()
    private val edited = mutableListOf<Int>()
    private val deleted = mutableListOf<String>()

    private fun newStore(n: Int): ComposerStore {
        val store = ComposerStore(scope = CoroutineScope(Job()))
        repeat(n) { k ->
            store.accept(
                ComposerStore.Intent.AddMessage(
                    role = if (k % 2 == 0) ChatMessage.Role.HER else ChatMessage.Role.ME,
                    content = "row$k"
                )
            )
        }
        return store
    }

    /** 与宿主同形的挂载：同一颗 StateFlow 进组合，重排直接投 store 那颗意图 */
    private fun mount(store: ComposerStore, listHeightDp: Int) {
        reorders.clear()
        edited.clear()
        deleted.clear()
        rule.setContent {
            val shown by store.messages.collectAsState()
            UiMatrix(360, heightDp = 1200).RenderIn(LocalDensity.current.density) {
                MessageList(
                    messages = shown,
                    editingIndex = -1,
                    onReorder = { from, to ->
                        reorders += from to to
                        store.accept(ComposerStore.Intent.ReorderMessages(from, to))
                    },
                    onEdit = { edited.add(it) },
                    onDelete = { deleted.add(it) },
                    modifier = Modifier.height(listHeightDp.dp)
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 某一行的 top（dp）：从未合并树里正文那颗 `Text` 读，并顺手判"这一行只有一颗" */
    private fun rowTopDp(content: String): Float {
        val nodes = rule.onAllNodes(hasText(content), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("屏上该有恰好一行写着「$content」，实到 ${nodes.size} 颗", 1, nodes.size)
        val target = probe.of(nodes.single())
        assertTrue("「$content」根本没摆出来（0x0 不算 laid）：" + target.describe(),
            target.widthDp > 0f && target.heightDp > 0f)
        return target.topDp
    }

    /** 屏上自上而下读出来的先后（按 top 排） */
    private fun screenOrder(store: ComposerStore): List<String> =
        store.messagesNow.sortedBy { rowTopDp(it.content) }.map { it.content }

    private fun storedOrder(store: ComposerStore): List<String> = store.messagesNow.map { it.content }

    /** 条目颗数（合并树上的行锚点）：先数够，"两份表同序"才不是两份空表在比 */
    private fun rowCount(): Int =
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG)).fetchSemanticsNodes().size

    /** prompt 里消息行的先后：`<chat>` 内 JSON 行的出现顺序，换回正文再比 */
    private fun promptOrder(store: ComposerStore): List<String> {
        val rendered = ChatTranscriptBlock.render(store.messageSnapshot())
        val ids = Regex("\"id\":\"(m\\d+)\"").findAll(rendered.body).map { it.groupValues[1] }.toList()
        assertTrue("prompt 里一行都没读到，这把尺没咬住东西：" + rendered.body, ids.isNotEmpty())
        return ids.map { rendered.sourceAliasMap.getValue(it) }.map { id ->
            store.messagesNow.first { it.id == id }.content
        }
    }

    /**
     * 3 条：屏上先后 == 落库先后 == prompt 先后，一次判完。
     * 反例：屏侧多算一次排序 ⇒ 第二句红；prompt 侧多算一次 ⇒ 第三句红。
     */
    @Test
    fun `three rows read in the same order on screen in the store and in the prompt`() {
        val store = newStore(3)
        mount(store, listHeightDp = 400)
        assertEquals("三条都该在屏上：" + rowCount(), 3, rowCount())
        assertEquals("屏上顺序必须就是落库顺序：" + screenOrder(store), storedOrder(store), screenOrder(store))
        assertEquals("prompt 里消息行的先后必须就是落库顺序：" + promptOrder(store),
            storedOrder(store), promptOrder(store))
    }

    /**
     * 拖完落库之后，屏幕必须换成新顺序、prompt 也跟着同一个数。
     * 投的是拖拽那条时间线会交出的重排对（(0,1) 再 (1,2)：第一条一路挪到底；
     * "是哪两格"由 `MessageListDragOrderTest` 的纯函数格钉，这里只判落库之后两边跟不跟）。
     * 反例：屏幕自己留一份顺序（副本 / 再排一次）⇒ 第二句红 = 分叉；
     * 反例：条目在重排中被复制或被丢 ⇒ 最后那句红。
     */
    @Test
    fun `after the drag commits its reorders the screen shows the new order and the prompt agrees`() {
        val store = newStore(3)
        mount(store, listHeightDp = 400)
        listOf(0 to 1, 1 to 2).forEach { (from, to) ->
            rule.runOnIdle { store.accept(ComposerStore.Intent.ReorderMessages(from, to)) }
            // 状态换到组合里要过一帧（collectAsState 那一颗收集协程在测试的 Main 上被唤起）
            rule.waitForIdle()
        }
        assertEquals("落库顺序该是被拖那条垫底：" + storedOrder(store),
            listOf("row1", "row2", "row0"), storedOrder(store))
        assertEquals("屏上必须跟着换成新顺序（不许留第二本账）：" + screenOrder(store),
            storedOrder(store), screenOrder(store))
        assertEquals("prompt 也必须跟着换成同一个数：" + promptOrder(store),
            storedOrder(store), promptOrder(store))
        assertEquals("一条不许多、一条不许少：" + rowCount(), 3, rowCount())
    }

    /**
     * 20 条：整列表都在视口里，屏上必须**数得到 20 颗**并按落库先后排好；
     * 20 条远低于 prompt 的掐尾预算，所以"同序"不是"两边都被截成同一段"蒙出来的。
     * 反例：有条目被压成 0x0（没摆出来）⇒ 计数或 `rowTopDp` 那句红（看得见的那半截不算摆出来）；
     * 反例：屏侧排序 ⇒ 第二句红。
     */
    @Test
    fun `twenty rows all lay out on screen in the stored order and the prompt order`() {
        val store = newStore(20)
        mount(store, listHeightDp = 900)
        assertEquals("二十行都该摆出来：" + rowCount(), 20, rowCount())
        assertEquals("屏上先后必须逐位等于落库先后：" + screenOrder(store),
            storedOrder(store), screenOrder(store))
        assertEquals("prompt 先后必须逐位等于落库先后：" + promptOrder(store),
            storedOrder(store), promptOrder(store))
        assertEquals("两份表必须逐字同一个数：", screenOrder(store), promptOrder(store))
    }

    /**
     * 短的点击**只**编辑，不许把消息挪走（原话第 19 条尾句）。三样一起判：
     * `onEdit` 恰好一次且交的是那条自己的原列表下标、`onReorder` 零次、屏幕先后不变。
     * 反例：把移动挂在点击上 ⇒ 第二、三句红；
     * 反例：点击错投成删除 ⇒ `onDelete` 那句红。
     */
    @Test
    fun `a short click edits that row and moves nothing`() {
        val store = newStore(3)
        mount(store, listHeightDp = 400)
        val before = screenOrder(store)
        assertEquals("三条都该在：" + before.size, 3, before.size)

        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[1].performClick()
        rule.mainClock.advanceTimeBy(16L)

        assertEquals("点第二行只该投一次『编辑这一条』，交的是它在持有者列表里的下标：" + edited,
            listOf(1), edited)
        assertEquals("一次点击不许把任何一条挪走：" + reorders, 0, reorders.size)
        assertTrue("点击也不是删除：" + deleted, deleted.isEmpty())
        assertEquals("先后必须一字不变：" + screenOrder(store), before, screenOrder(store))
    }

    /**
     * 纵向擦动（浏览列表）也不是移动：`swipeUp` 之后重排回调零次、删除零次、条目一条不少。
     * 后半"同一行随后仍点得着"是正控制——只判"没移动"的话，手势根本没落到行上也能绿（恒真）。
     * 反例：把纵向拖量吃成重排位移 ⇒ 第二句红；
     * 反例：手势锚点丢了 ⇒ 最后一句红（编辑投不进去）。
     */
    @Test
    fun `scrolling the list vertically moves no message`() {
        val store = newStore(3)
        mount(store, listHeightDp = 400)
        val before = screenOrder(store)
        assertEquals("三条都该在：" + before.size, 3, before.size)

        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(320L)
        assertEquals("上下浏览不许投任何一次重排：" + reorders, 0, reorders.size)
        assertTrue("上下浏览也不许删东西：" + deleted, deleted.isEmpty())
        assertEquals("三条一条不许丢：" + rowCount(), 3, rowCount())
        assertEquals("先后不许被擦乱：" + screenOrder(store), before, screenOrder(store))

        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("擦一下之后这一行仍然点得着（正控制：手势真的落在行上）：" + edited,
            listOf(0), edited)
    }
}
