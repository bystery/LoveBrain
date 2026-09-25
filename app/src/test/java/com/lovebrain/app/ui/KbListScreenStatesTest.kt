package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbAsyncTags
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.KbListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.3 第三份：知识库页这一格**确实**走的是那一个四态出口（[com.lovebrain.app.core.designsystem.LbAsyncState]），
 * 而不是页面自己再画一套。
 *
 * 断言全部读语义树上的 `LbAsyncTags`，不看源码：标签只有共用组件会发，
 * 所以"这页换回自造卡片"这种回退一定会红。组件自身的版式（≥48dp、按钮角色）
 * 已有 [com.lovebrain.app.core.designsystem.LbAsyncStateTest] 钉住，这里只补目的地这一层的**接线**：
 * 空态那颗动作点了真的开向导、错误态那颗真的重新读、有内容时不再出现空态那套壳。
 *
 * 一个用例只能 `setContent` 一次，所以四格靠改提升出来的 [slot] 换（与 LbAsyncStateTest
 * 那 12 格矩阵同一办法）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KbListScreenStatesTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val slot: MutableState<ScreenState<KbListState>> = mutableStateOf(ScreenState.Loading)
    private var newKbFired = 0
    private var retryFired = 0

    private fun mount() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    KbListScreen(
                        screenState = slot.value,
                        onActivate = {},
                        onNewKb = { newKbFired++ },
                        onRename = { _, _ -> },
                        onDelete = {},
                        onEdit = {},
                        onConfirmExport = {},
                        onImport = {},
                        onBack = {}
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun show(state: KbListState) {
        rule.runOnIdle {
            slot.value = kbScreenState(
                state = state,
                emptyMessage = EMPTY_SENTINEL,
                errorMessage = ERROR_SENTINEL,
                newKb = ScreenAction(NEW_KB_SENTINEL) { newKbFired++ },
                retry = ScreenAction(RETRY_SENTINEL) { retryFired++ }
            )
        }
        rule.waitForIdle()
    }

    private fun messageCount() =
        rule.onAllNodesWithTag(LbAsyncTags.MESSAGE).fetchSemanticsNodes().size

    private fun spinnerCount() =
        rule.onAllNodesWithTag(LbAsyncTags.LOADING).fetchSemanticsNodes().size

    @Test
    fun `the four cells of the knowledge base destination all come from the one renderer`() {
        mount()

        // ① Loading：共用组件的转圈，页面自己那套壳不许同时在树里
        rule.runOnIdle { slot.value = ScreenState.Loading }
        rule.waitForIdle()
        assertEquals("Loading 那一格要画共用转圈", 1, spinnerCount())
        assertEquals("Loading 不许同时画空态", 0, messageCount())

        // ② Empty：一条说明 + 一颗真动作
        show(KbListState(loaded = true))
        assertEquals("空态说明必须来自共用组件（自造卡片一回来这里就红）", 1, messageCount())
        rule.onNodeWithText(EMPTY_SENTINEL).assertExists()
        assertEquals(0, spinnerCount())

        // ③ Error：与空态各自的句子，不能共用一条含糊过去
        show(KbListState(loaded = true, loadFailed = true))
        rule.onNodeWithText(ERROR_SENTINEL).assertExists()
        rule.onNodeWithText(EMPTY_SENTINEL).assertDoesNotExist()

        // ④ Content：画卡片，四态那套壳整个退场
        show(
            KbListState(
                knowledgeBases = listOf(KnowledgeBase(name = "kb_a", displayName = CARD_SENTINEL)),
                activeName = "kb_a",
                loaded = true
            )
        )
        rule.onNodeWithText(CARD_SENTINEL).assertExists()
        assertEquals("有内容时不该再出现空态/错误态版式", 0, messageCount())
        assertEquals(0, spinnerCount())
    }

    /** 空态那颗动作是**一处操作**：按钮角色、够大、点了真的开向导（替换前这里只是一行指路文字） */
    @Test
    fun `the empty cell offers a real button that opens the creation flow`() {
        mount()
        show(KbListState(loaded = true))

        val target = probe.of(
            rule.onNodeWithTag(LbAsyncTags.ACTION)
                .fetchSemanticsNode("空态没有动作入口")
        )
        assertEquals("空态动作必须带按钮角色：" + target.describe(), "Button", target.role)
        assertTrue("空态动作热区不足 ${probe.floorDp.toInt()}dp：" + target.describe(), !target.tooSmall(probe.floorDp))

        val before = newKbFired
        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        rule.waitForIdle()
        assertEquals("点了空态动作没把向导打开", before + 1, newKbFired)
    }

    /** 错误态那颗重试同理——点了要真的再读一次（这里以刷新回调为证） */
    @Test
    fun `the error cell offers a retry that is actually invoked`() {
        mount()
        show(KbListState(loaded = true, loadFailed = true))

        val target = probe.of(
            rule.onNodeWithTag(LbAsyncTags.ACTION)
                .fetchSemanticsNode("错误态没有重试入口")
        )
        assertTrue("重试热区不足：" + target.describe(), !target.tooSmall(probe.floorDp))

        val before = retryFired
        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        rule.waitForIdle()
        assertEquals(before + 1, retryFired)
    }

    /**
     * 空态那一屏上**每个**可交互节点都要过 §6.5 那把尺。
     *
     * 这一格是本轮顺手量出来的：页头返回箭头热区原来只有 32dp，四个二级页共用这一个页头，
     * 而仓库里此前没有任何一条用例量过它（`PanelHeader` 那颗有过，`ScreenHeader` 没有）。
     */
    @Test
    fun `every interactive node on the empty screen meets the touch and labeling floor`() {
        mount()
        show(KbListState(loaded = true))

        val targets = probe.assertAllActionableMeetTouchFloor(rule, "知识库页空态")
        probe.assertAllActionableLabeled(rule, "知识库页空态")
        probe.assertNoDuplicatedAnnouncement(rule, "知识库页空态")
        // 空态动作 + 底部两颗大按钮 + 页头返回：少于这个数说明有入口被画没了
        assertTrue("只量到 ${targets.size} 个可交互节点", targets.size >= 4)
    }
}

private const val EMPTY_SENTINEL = "KB_EMPTY_SENTINEL"
private const val ERROR_SENTINEL = "KB_ERROR_SENTINEL"
private const val NEW_KB_SENTINEL = "KB_NEW_KB_SENTINEL"
private const val RETRY_SENTINEL = "KB_RETRY_SENTINEL"
private const val CARD_SENTINEL = "KB_CARD_SENTINEL"
