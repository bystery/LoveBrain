package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbAsyncTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

/**
 * 第6节第3条 收尾：知识库编辑页这一格**确实**走的是那一个四态出口，而不是页面自己再画一套。
 *
 * 挂的是 `KbEditScreen` 本身，四格各由**一次真的读**打开——用例不直接塞一个 `ScreenState`
 * 进去（那是 `KbListScreenStatesTest` 那一格干的事，它挂的是已经收好状态的 `KbListScreen`）：
 * 这一屏的状态由页面里那一次 `LaunchedEffect` 读盘算出来，所以这格要证的是
 * **整条接线**：读没回来 → 共用转圈；读抛了 → 共用错误态 + 点了真的又读一遍；
 * 读回来是空的 → 共用空态（且不再有第二颗同义按钮）；读回来有正文 → 交回那一格画。
 *
 * 四格都靠 `readFile` 那颗桩的开火形状区分（hang / throw / 空串 / 正文），
 * 断言只读语义树上的 `LbAsyncTags`，不看源码 ⇒ 谁把这页换回自造卡片，这里当场红。
 *
 * 前一格留下的教训照用：`KbEditScreen` 一个 ViewModel 都不收，读盘走两个 suspend lambda，
 * "要 VM 和真实磁盘所以测不了"那条是抄来的事实，不是判据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KbEditScreenStatesTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val density: Float get() = app.resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /**
     * 两个文件不是随手写的：`files` 里必须有 `moment/recent.md` **或者** `lastFile` 命中，
     * 否则页面那句 `files.first { it.path == "moment/recent.md" }` 会当场抛
     * （上一格那条"要 VM 所以测不了"的假话，第一版就是被这行戳穿的）。
     */
    private val files = listOf(
        KbFile("我是谁", "understand/me.md", layer = "画像"),
        KbFile("最近两句", "moment/recent.md", layer = "当下")
    )

    private fun mount(read: suspend (String) -> Pair<String, String>) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                KbEditScreen(
                    files = files,
                    lastFile = "understand/me.md",
                    onLastFileChange = {},
                    readFile = read,
                    saveFile = { _, _, _ -> "new-version" },
                    onBack = {}
                )
            }
        }
        rule.waitForIdle()
    }

    private fun messageCount() =
        rule.onAllNodesWithTag(LbAsyncTags.MESSAGE).fetchSemanticsNodes().size

    private fun spinnerCount() =
        rule.onAllNodesWithTag(LbAsyncTags.LOADING).fetchSemanticsNodes().size

    // ═══════════ ① Loading：那一次读真的还没回来 ═══════════

    @Test
    fun `a read that has not come back draws the shared spinner, and it is not decoration`() {
        val gate = CompletableDeferred<Unit>()
        mount { _ ->
            gate.await()
            "SENTINEL 正文一句" to "sha"
        }
        assertEquals("读还没回来要画共用转圈", 1, spinnerCount())
        assertEquals("转圈那一格不许同时画空态/错误态", 0, messageCount())

        // 这一半才证明那一格不是装饰分支：放它过去，转圈必须退场
        gate.complete(Unit)
        rule.waitForIdle()
        assertEquals("读回来之后共用转圈没有退场", 0, spinnerCount())
        assertEquals(0, messageCount())
        rule.onNodeWithText("SENTINEL 正文一句").assertExists()
    }

    // ═══════════ ② Error：读抛了 ≠ 还在读，也 ≠ 这篇是空的 ═══════════

    @Test
    fun `a failed read shows the shared error cell and the retry really reads again`() {
        var attempts = 0
        mount { _ ->
            attempts++
            throw IOException("模拟磁盘读不动")
        }

        assertEquals("读一次就该记一次尝试（associate 在第一颗就中断）", 1, attempts)
        assertEquals("失败那一格要走共用错误态", 1, messageCount())
        rule.onNodeWithText(app.getString(R.string.kb_edit_read_failed)).assertExists()
        assertEquals("读不出来不许画成'还在读'", 0, spinnerCount())

        val action = probe.of(
            rule.onNodeWithTag(LbAsyncTags.ACTION).fetchSemanticsNode("错误态没有重试入口")
        )
        assertEquals("重试必须带按钮角色：" + action.describe(), "Button", action.role)
        assertTrue(
            "重试热区不足 ${probe.floorDp.toInt()}dp：" + action.describe(),
            !action.tooSmall(probe.floorDp)
        )

        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        rule.waitForIdle()
        assertEquals("点了重试没有真的再读一遍", 2, attempts)
        assertEquals("再读一次仍然失败要回到错误态，不许停在转圈", 1, messageCount())
    }

    // ═══════════ ③ Empty：读成功而这篇是空的 ═══════════

    @Test
    fun `a document that reads back empty is the shared empty cell, not an error`() {
        mount { _ -> "" to "sha-of-empty" }

        assertEquals(1, messageCount())
        rule.onNodeWithText(app.getString(R.string.kb_edit_empty_hint)).assertExists()
        assertEquals("空态不许画转圈", 0, spinnerCount())
        // 这一格**不带**第二颗动作：「编辑」那颗画在状态件外面、四格都在。
        // 同一条债在这里被钉住——有人加同义按钮时这里会红，逼他先读判据那段的理由。
        rule.onNodeWithTag(LbAsyncTags.ACTION).assertDoesNotExist()
    }

    /**
     * 空正文在预览那一档是 Empty，在编辑那一档必须**不是**——
     * 用户点「编辑」就是要往空框里敲第一个字，把输入框换成一张空态图等于把人关死。
     * 这一格是这屏判据里唯一与同族两页不同的那条条件（`isPreview`）的开火证人。
     */
    @Test
    fun `entering edit mode on an empty document keeps the editor`() {
        mount { _ -> "" to "sha-of-empty" }
        rule.onNodeWithText(app.getString(R.string.kb_edit_empty_hint)).assertExists()

        // 「编辑」那颗是内联中文（它就在页上，不是这格要动的账），照 `KbEditScreenSemanticsTest`
        // 同一写法取：先证明它可点，再点它——`performClick()` 只注坐标不查语义。
        rule.onAllNodes(hasClickAction() and hasText("编辑")).onFirst().performClick()
        rule.waitForIdle()

        rule.onNodeWithText(app.getString(R.string.kb_edit_empty_hint)).assertDoesNotExist()
        assertEquals("编辑态不许再挂状态件的说明", 0, messageCount())
        rule.onNode(hasSetTextAction()).assertExists()
    }

    // ═══════════ ④ Content：有正文，四态那套壳整个退场 ═══════════

    @Test
    fun `a document with content paints it and takes the state shell away`() {
        mount { _ -> "SENTINEL 正文一句" to "sha" }

        assertEquals("有内容时不该出现空态/错误态版式", 0, messageCount())
        assertEquals(0, spinnerCount())
        // 交回的必须是判过的那一份正文，不是页面再去 drafts 里另拿一次
        rule.onNodeWithText("SENTINEL 正文一句").assertExists()
    }

    /**
     * 同一颗说明锚点（`LbAsyncTags.MESSAGE`）在**换句子之后还在**——这正是那组 tag 存在的理由
     * （"状态换了 tag 不换，文字换了 tag 也不换"）。所以把错误态与空态在一条用例里连着开火：
     * 先让读抛，再让重试成功读到一份空正文，两句各自要认得出，tag 两处都要有。
     */
    @Test
    fun `the message anchor survives from error to empty`() {
        var shouldFail = true
        mount { path ->
            if (shouldFail && path == "understand/me.md") throw IOException("模拟读不动")
            "" to "sha-of-$path"
        }
        assertEquals("错误态那一格要有且只有一句说明", 1, messageCount())
        rule.onNodeWithText(app.getString(R.string.kb_edit_read_failed)).assertExists()

        shouldFail = false
        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        rule.waitForIdle()

        assertEquals("换成空态之后那颗说明锚点必须还在（tag 不随文字换）", 1, messageCount())
        rule.onNodeWithText(app.getString(R.string.kb_edit_empty_hint)).assertExists()
        rule.onNodeWithText(app.getString(R.string.kb_edit_read_failed)).assertDoesNotExist()
    }
}
