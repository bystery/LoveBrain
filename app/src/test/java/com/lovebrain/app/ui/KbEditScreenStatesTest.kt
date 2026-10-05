package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    // ═══════════ ⑤ 结构那一格：正文编辑器吃满有限视口，不塌成空白（§7.1 / 原始第 1 条）═══════════

    /**
     * **旧两格在结构上测不到这一条**：本类与 `KbEditScreenSemanticsTest` 全跑在 1000/1200dp 高视口，
     * 塌陷那两档（`H_page ≤ 322` 整卡 0 高、`322 < H_page ≤ 454` 保存被挤出）永远撞不到。
     * 这一格把同一棵 `KbEditScreen` 挂进**两档矮视口**去量编辑器那一棵的真实高度。
     *
     * JVM 怎么模拟"键盘压矮"：`UiMatrix.RenderIn`（`core/testing/UiMatrix.kt:68-76`）用
     * `Modifier.size(w, H)` 把整棵子树的**父约束**钉死；Robolectric 的 `ime` inset 恒 0，
     * 于是 §A1 算式里"H_page 被键盘吃掉一截"就落成"直接把 H_page 调小"（本条的键盘实拍仍归真机）。
     *
     * 判的是**相对关系**，不用绝对坐标把常数钉死（`F_page/F_card` 里的分隔线、M3 `TextButton` 在本仓
     * 版本下的真实高度、字体回退行高都要真机 Layout Inspector 校准，本机给不出可信常数）：
     * - 高视口：编辑器实测 ≥ 120dp——**高于 96 地板**，说明这一格是被外层 `weight` 撑开的真空间，
     *   不是 `heightIn(min)` 撑出来的溢出；
     * - 窗口压矮 66dp：编辑器高度**跟着变小**（两档差 ≥ 40dp）——只有"高度来自权重"才成立；
     * - 「保存」那颗 bottom 仍在 640 视口内：没被固定占位挤出屏幕。
     *
     * 尺寸为什么直接读 `boundsInRoot` 而不靠文案：`maxLines+Ellipsis` 那族在本项目语义树里永远报**完整原文**
     * （文本判据无牙），这一格判的是**盒子的几何**（编辑器有没有真的占到位），不是"树里有没有那串字"。
     *
     * 回退成什么会红：
     * - `LbAsyncState` 的 Content 支又不接传入 `modifier`、内部子项也不各自带权重 → 编辑器拿不到那一格 →
     *   高视口档就 < 120dp（塌回空白），红；
     * - 回来给编辑器再垫 `heightIn(min=…)` 当"修复"（把地板焊成高度）→ 两档编辑器高度差不再随视口变（<40），红；
     * - 给分区三排 / 卡头 toggle 加回 `heightIn(min=48)` 把固定占位涨回去 → 矮视口那档「保存」被顶出视口，红。
     */
    @Test
    fun `the editor takes a real finite viewport and shrinks with the window instead of collapsing`() {
        val heightDp = androidx.compose.runtime.mutableStateOf(640)
        rule.setContent {
            UiMatrix(360, heightDp.value).RenderIn(LocalDensity.current.density) {
                KbEditScreen(
                    files = files,
                    lastFile = "understand/me.md",
                    onLastFileChange = {},
                    readFile = { _ ->
                        "SENTINEL 一整段足够长的正文，用来证明编辑器是被权重撑开的、不是溢出" to "sha"
                    },
                    saveFile = { _, _, _ -> "v" },
                    onBack = {}
                )
            }
        }
        rule.waitForIdle()
        rule.onAllNodes(hasClickAction() and hasText("编辑")).onFirst().performClick()
        rule.waitForIdle()

        val editorTall = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val saveTall = rule.onNode(hasText(app.getString(R.string.kb_save)))
            .fetchSemanticsNode().boundsInRoot
        val tallHeight = editorTall.height / density
        val saveBottom = saveTall.bottom / density

        rule.runOnIdle { heightDp.value = 574 } // 把窗口压矮 66dp（键盘等效）
        rule.waitForIdle()
        val shortHeight =
            rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.height / density

        assertTrue(
            "高视口编辑器实测 ${tallHeight.toInt()}dp：拿不到 ≥120dp 就是塌成空白、或被 96 地板焊死" +
                "（回退成：Content 支不接 modifier 又没子项权重）",
            tallHeight >= 120f
        )
        assertTrue(
            "视口 640→574（压矮 66dp）编辑器只从 ${tallHeight.toInt()} 变到 ${shortHeight.toInt()}dp，" +
                "差 ${(tallHeight - shortHeight).toInt()}dp（<40）——这一格高度不是外层权重给的，" +
                "是 heightIn 地板焊出来的溢出（回退成：拿再垫 heightIn 当修复）",
            tallHeight - shortHeight >= 40f
        )
        assertTrue(
            "「保存」bottom=${saveBottom.toInt()}dp 掉到 640 视口之外：固定占位又涨回来了" +
                "（回退成：给分区排/卡头 toggle 加回 heightIn(min=48)）",
            saveBottom <= 640f + 0.5f
        )
    }

    // ═══════════ ⑥ 缺陷一：编辑态不依赖读成功，失败也能进编辑并保住已写的字 ═══════════

    /**
     * 旧状态机 `readFailed -> Error` 判在 `isPreview` 之前 ⇒ 读失败时那颗「编辑」画在状态件外面（点得到），
     * 但点下去编辑态还是被 `readFailed` 抢先画成 Error，屏幕上没有输入框——"连输入框都打不开"。
     * 现在编辑态永远落 Content：失败也打得开、打得开就写得进、写了切走再切回来字还在。
     *
     * 回退成什么会红：把 `kbEditFileScreenState` 改回 `readFailed -> Error`（不分预览/编辑）→
     * 点「编辑」后 `hasSetTextAction` 不存在 / `messageCount` 仍为 1，红。
     */
    @Test
    fun `a failed read still lets the user open the editor and keeps what they typed`() {
        mount { _ -> throw IOException("模拟磁盘读不动") }
        // 预览那一档：失败仍然报成共用错误态（既不谎报成空态，也不画成"还在读"）
        assertEquals("预览档读失败要走共用错误态", 1, messageCount())
        rule.onNodeWithText(app.getString(R.string.kb_edit_read_failed)).assertExists()

        // 点卡头那颗「编辑」：修完之后这一趟必须真的打开输入框
        rule.onAllNodes(hasClickAction() and hasText("编辑")).onFirst().performClick()
        rule.waitForIdle()
        assertEquals("读失败时进编辑态不许还挂着错误态说明", 0, messageCount())
        rule.onNode(hasSetTextAction()).assertExists()

        // 保住用户已写的字：输入 → 切预览 → 切回编辑，字一个字不许丢
        rule.onNode(hasSetTextAction()).performTextInput("手敲的一句话")
        rule.onAllNodes(hasClickAction() and hasText("预览")).onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasClickAction() and hasText("编辑")).onFirst().performClick()
        rule.waitForIdle()
        // 读数走语义树那一份 `EditableText`（本仓库没有 `assertTextContains` 那颗公共 API，
        // 且 `maxLines+Ellipsis` 时 Text 那栏会报完整原文、只有 EditableText 才是真输入值）。
        val kept = rule.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText) ?: ""
        assertTrue("切走再切回必须保住手敲的字，实读「$kept」", kept.contains("手敲的一句话"))
    }

    // ═══════════ ⑦ 缺陷二：放弃修改回到真正的原文，不是脏草稿 ═══════════

    /**
     * 旧记账 `editBaselines[path] = drafts[path]` 只在"从预览进编辑"那一刻写，且取的是 `drafts`。
     * 预览→编辑→（改几笔、不保存）→预览→再编辑 之后，`drafts` 已带着上一轮没保存的改动，
     * 于是基线被刷成**脏草稿**，那颗「放弃修改」回的是脏草稿而不是原文——与"放弃恢复原文"不符。
     * 现在基线每次进编辑都对齐 `saved`（最后一次落盘/读回的正文），"放弃"回的是真原文。
     *
     * 回退成什么会红：把进编辑那行改回 `editBaselines[selectedPath] = drafts[selectedPath] ?: ""` →
     * 这一串之后「放弃」落回"原文的改动"（5 字），断言"｜ 2 字"当场红。
     */
    @Test
    fun `discarding after a preview-edit round trip returns the committed text, not the dirty draft`() {
        mount { _ -> "原文" to "sha-v1" } // 已落盘的正文 = 原文（2 字）
        rule.waitForIdle()

        rule.onAllNodes(hasClickAction() and hasText("编辑")).onFirst().performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("的改动") // 草稿 = 原文的改动（5 字），未保存
        rule.waitForIdle()

        // 编辑 → 预览（不保存）→ 预览 → 编辑：这一趟就是旧记账被刷脏的那一步
        rule.onAllNodes(hasClickAction() and hasText("预览")).onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasClickAction() and hasText("编辑")).onFirst().performClick()
        rule.waitForIdle()

        rule.onAllNodes(hasClickAction() and hasText("放弃修改")).onFirst().performClick()
        rule.waitForIdle()

        // 回到预览，卡头字数行按当前草稿长度：真原文 = 2 字；脏草稿才是 5 字
        rule.onNode(hasText("｜ 2 字", substring = true)).assertExists()
        rule.onNode(hasText("｜ 5 字", substring = true)).assertDoesNotExist()
    }
}
