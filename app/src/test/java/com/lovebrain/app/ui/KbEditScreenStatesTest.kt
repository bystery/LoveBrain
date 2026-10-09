package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
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
import com.lovebrain.app.core.designsystem.LbTags
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

    /**
     * ⑧–⑫ 那一组要**控制写盘什么时候回来、数它写了几次、接住退出那一句**，
     * 所以这一颗 mount 把 `saveFile` 与 `onBack` 也交出来（上面那七格一个字没动）。
     */
    private fun mountWith(
        read: suspend (String) -> Pair<String, String>,
        save: suspend (String, String, String?) -> String? = { _, _, _ -> "new-version" },
        onBack: () -> Unit = {}
    ) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                KbEditScreen(
                    files = files,
                    lastFile = "understand/me.md",
                    onLastFileChange = {},
                    readFile = read,
                    saveFile = save,
                    onBack = onBack
                )
            }
        }
        rule.waitForIdle()
    }

    /** 进编辑态（卡头那颗「编辑」是这一屏唯一那扇输入的门） */
    private fun enterEdit() {
        rule.onAllNodes(hasClickAction() and hasText("编辑")).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun backNode() = rule.onAllNodes(
        hasClickAction() and hasContentDescription(app.getString(R.string.common_back))
    ).onFirst()

    private fun savingAnchorCount() =
        rule.onAllNodes(hasTestTag(KB_EDIT_SAVING_TAG)).fetchSemanticsNodes().size

    private fun designSystemLoadingCount() =
        rule.onAllNodes(hasTestTag(LbTags.PRIMARY_STOP)).fetchSemanticsNodes().size

    /**
     * 编辑器那棵的真值。只取 `EditableText` 的**文本**这一栏：它是 AnnotatedString（不是 String），
     * 所以要 `.text`（坑表里那条"语义读出来是 AnnotatedString"）。
     *
     * 光标位置这一栏（`SemanticsProperties.TextSelectionRange`）**不在这里读**：本仓库没有任何一格
     * 证过这颗属性在这套 foundation 版本的语义树里报得出来，拿它当判据就是拿一条没量过的读数写判据。
     * 插入位置本身已经把同一件事咬住了（见 ⑫：光标被打回开头时第二笔会插到最前面，顺序当场颠倒）。
     */
    private fun editorText(): String =
        rule.onNode(hasSetTextAction()).fetchSemanticsNode("编辑态没有输入框")
            .config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

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

    // ═══════════ ⑧ 保存四态：写盘那一整趟要报"保存中"，而且不许提前报"已保存" ═══════════

    /**
     * §12.4「保存状态应可辨：正在编辑、保存中、已保存或失败」里，**保存中**这一档在这一屏原来
     * 根本没有出口：那颗「保存」的 `state` 是写死的 `LbButtonState.Idle`，
     * `saveFile` 那一整趟（版本校验 + IO 落盘）屏上一个变化都没有。
     *
     * 这一格把写盘钉在**没回来**的那一档上开火，量三件事：
     * ① 设计系统 Loading 那颗的锚点（`LbTags.PRIMARY_STOP`）在树上；
     * ② 卡头那一格"正在写盘"的转圈（[KB_EDIT_SAVING_TAG]）在树上；
     * ③ 此时"已保存"那一句**一个字都不许出现**（同一条也挡住"失败/进行中却报已保存"）。
     * 然后把闸门放开：句子换成"已保存"、转圈退场（2 秒后自己消失，那是本页既有契约）。
     *
     * 回退成什么会红：`state = LbEditButtonState.Idle` 那种写死回去 → ①② 当场 0 命中；
     * 把 `hint = savedHint` 提到写盘**之前**去设 → ③ 当场红。
     * 时钟为什么手动推：`waitForIdle()` 会把那颗 2 秒定时器一次跑到底，
     * "已保存刚出现"那一档就被自己清掉了——本机实测过的坑，这里只推一帧。
     */
    @Test
    fun `a manual save in flight reports saving and never claims saved before the write returns`() {
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        mountWith(
            read = { _ -> "原文" to "sha-v1" },
            save = { _, _, _ ->
                writes++
                gate.await()
                "sha-v2"
            }
        )
        enterEdit()
        rule.onNode(hasSetTextAction()).performTextInput("改动")
        rule.onNode(hasText(app.getString(R.string.kb_save))).performClick()
        rule.waitForIdle()

        assertEquals("写盘那一整趟没报出保存中（那颗 Loading 锚点不在树上）",
            1, designSystemLoadingCount())
        assertEquals("卡头那一格没报出保存中（转圈锚点不在树上）", 1, savingAnchorCount())
        assertEquals("一次点击写了几遍", 1, writes)
        rule.onNodeWithText(app.getString(R.string.hint_saved)).assertDoesNotExist()

        gate.complete(Unit)
        rule.mainClock.advanceTimeBy(64L) // 只推一帧：让写盘的结果落到树上，又不喂给那颗 2 秒定时器
        rule.onNodeWithText(app.getString(R.string.hint_saved)).assertExists()
        assertEquals("写完了转圈还亮着", 0, savingAnchorCount())
        assertEquals("写完了那颗还在 Loading 档", 0, designSystemLoadingCount())

        rule.mainClock.advanceTimeBy(2_100L) // 既有契约：成功提示 2s 自己消失，失败提示常驻
        rule.onNodeWithText(app.getString(R.string.hint_saved)).assertDoesNotExist()
    }

    // ═══════════ ⑨ 切分区那趟静默自动保存：进行中与失败都要看得见 ═══════════

    /**
     * §12.4 明写"按当前自动保存契约验切分区和返回"——所以自动保存那一趟也得可辨，
     * 而且**切过去的动作要等写盘回来**（既有契约：写不进去就不换格子，草稿留在原地）。
     *
     * 回退成什么会红：`autosave` 不再往 [KB_EDIT_SAVING_TAG] 那个真源挂号 → 第一组断言 0 命中；
     * 把 `switchToFile` 改成先切格子再写盘 → "还停在原文那一份"当场红。
     */
    @Test
    fun `switching partitions autosaves with a visible saving state and keeps the old document until it returns`() {
        val gate = CompletableDeferred<Unit>()
        mountWith(
            read = { path -> if (path == "understand/me.md") "A的正文" to "sha-a" else "B的正文" to "sha-b" },
            save = { _, _, _ ->
                gate.await()
                "sha-new"
            }
        )
        enterEdit()
        rule.onNode(hasSetTextAction()).performTextInput("敲了一半")
        rule.onAllNodes(hasClickAction() and hasText("预览")).onFirst().performClick()
        rule.waitForIdle()

        rule.onAllNodes(hasClickAction() and hasText("最近两句")).onFirst().performClick()
        rule.waitForIdle()

        assertEquals("切分区那趟自动保存没有报出保存中", 1, savingAnchorCount())
        // 用 substring 而不是整句相等：这一档屏上摆的是**带着未保存草稿的那一份**
        // （`drafts` 里是「敲了一半A的正文」），把整句写死就是把测试自己钉在一个拼接顺序上。
        rule.onNodeWithText("A的正文", substring = true).assertExists()
        rule.onNodeWithText("B的正文", substring = true).assertDoesNotExist()

        gate.complete(Unit)
        rule.mainClock.advanceTimeBy(64L)
        assertEquals("写盘回来了保存中却没退场", 0, savingAnchorCount())
        rule.onNodeWithText("B的正文", substring = true).assertExists()
    }

    /**
     * **这一格是本轮抓到的真缺陷**：提示行原来住在编辑那一支的里侧，
     * 而切分区/退出这两趟自动保存**只在预览态发生** ⇒ 写盘失败的那句红字根本没地方画。
     * 用户的实际形状是：按下返回或点另一格、屏上一个字都没变，只能反复按。
     *
     * 桩这里让 `saveFile` 返回 null（= 版本冲突，本页既有的失败档），要求：
     * 失败那句话在**预览态**看得见、没切成格子、草稿一个字没丢（§12.4 失败不显示已保存、不丢草稿）。
     *
     * 回退成什么会红：把提示行搬回编辑那一支 → 第一句断言当场不存在（这一格就是这么抓出来的）。
     */
    @Test
    fun `an autosave that fails while switching partitions says so out loud on the preview screen`() {
        mountWith(
            read = { path -> if (path == "understand/me.md") "A的正文" to "sha-a" else "B的正文" to "sha-b" },
            save = { _, _, _ -> null } // 版本冲突：这一趟没写进去
        )
        enterEdit()
        rule.onNode(hasSetTextAction()).performTextInput("没落盘的一句")
        rule.onAllNodes(hasClickAction() and hasText("预览")).onFirst().performClick()
        rule.waitForIdle()

        rule.onAllNodes(hasClickAction() and hasText("最近两句")).onFirst().performClick()
        rule.waitForIdle()

        rule.onNodeWithText(app.getString(R.string.hint_save_failed)).assertExists()
        rule.onNodeWithText(app.getString(R.string.hint_saved)).assertDoesNotExist()
        rule.onNodeWithText("B的正文", substring = true).assertDoesNotExist() // 没切过去
        // 草稿还在：再进编辑，那一笔字一个字不许少
        enterEdit()
        assertTrue(
            "自动保存失败不许吃掉草稿：" + editorText(),
            editorText().contains("没落盘的一句")
        )
    }

    // ═══════════ ⑩ 刚说保存了，返回就绝不再要求第二次写盘（§2.2 第 6 条）═══════════

    /**
     * 「保存方式必须让人能预期」落到判据上就是一条：屏幕上刚报过"已保存"，
     * 按返回就**不许**再写一遍、更不许把用户领到一个"要放弃吗"的出口上。
     *
     * 数的是 `saveFile` 的**调用次数**与 `onBack` 的调用次数，两边都是本次实到读数：
     * 手动保存写了 1 次 → 返回必须 0 次新写、并且真的退出去了。
     *
     * 回退成什么会红：手动保存成功后不再同步 `saved`（旧写法少这一笔时 `anyDirty` 仍是真）→
     * 返回那一趟把同一篇又写一遍 → `writes` 变 2 当场红。
     */
    @Test
    fun `returning right after a successful save exits without writing the same document twice`() {
        var writes = 0
        var backs = 0
        mountWith(
            read = { _ -> "原文" to "sha-v1" },
            save = { _, _, _ ->
                writes++
                "sha-v${writes + 1}"
            },
            onBack = { backs++ }
        )
        enterEdit()
        rule.onNode(hasSetTextAction()).performTextInput("改动")
        rule.onNode(hasText(app.getString(R.string.kb_save))).performClick()
        rule.waitForIdle()
        assertEquals("保存这一趟该写一次", 1, writes)

        backNode().performClick()
        rule.waitForIdle()

        assertEquals("刚报过已保存，返回却把同一篇又写了一遍", 1, writes)
        assertEquals("返回没真的退出：$backs", 1, backs)
    }

    // ═══════════ ⑪ 放弃只放弃**没落盘**的那部分：没有就不许再写一笔 ═══════════

    /**
     * §12.4 那句"放弃只放弃明确尚未持久化的修改，不能偷偷撤销已保存内容"判的是两件事：
     * - 放弃回的是基线（`saved` 那一档），不是脏草稿，也不是空串（⑦ 已钉）；
     * - **磁盘上已经是这一份时，放弃不许再写一笔**——旧写法不管有没有可放弃的东西都照写一次，
     *   于是一个纯撤消动作伪装成一次保存、把版本号白顶一格（真冲突时还会反过来失败）。
     *
     * 两段都在这一格里开火：① 刚保存完就放弃 → 磁盘上就是这一份 → 写次数不许涨；
     * ② 再敲一笔不保存就放弃 → 撤消的是内存里那一笔 → 写次数**仍然**不许涨，
     *   而屏上回到已保存的那一份。
     *
     * 回退成什么会红：把那颗的 `if (pending)` 守卫摘掉（旧行为）→ 第一段 `writes` 从 1 变 2 当场红。
     */
    @Test
    fun `discarding gives up only what was never persisted and does not write a redundant copy`() {
        var writes = 0
        mountWith(
            read = { _ -> "原文" to "sha-v1" },
            save = { _, _, _ ->
                writes++
                "sha-v${writes + 1}"
            }
        )
        enterEdit()
        rule.onNode(hasSetTextAction()).performTextInput("改动")
        rule.onNode(hasText(app.getString(R.string.kb_save))).performClick()
        rule.waitForIdle()
        assertEquals("手动保存写一次", 1, writes)

        // ① 内容已经落盘：这一趟"放弃"没有任何可放弃的东西
        enterEdit()
        rule.onAllNodes(hasClickAction() and hasText("放弃修改")).onFirst().performClick()
        rule.waitForIdle()
        assertEquals("没有未持久化的修改时放弃不许再写一遍磁盘", 1, writes)
        // 屏上仍是刚保存的那一份（不断言拼接顺序：`performTextInput` 落在光标处，
        // 而初始光标在 0——顺序归 ⑫ 那一格管，这里只判"内容一个字没被撤掉"）
        rule.onNodeWithText("改动", substring = true).assertExists()

        // ② 这一笔确实没落盘：放弃撤消的是内存里的那一笔，磁盘仍留着上一份已保存内容
        enterEdit()
        rule.onNode(hasSetTextAction()).performTextInput("又敲了一句")
        rule.onAllNodes(hasClickAction() and hasText("放弃修改")).onFirst().performClick()
        rule.waitForIdle()
        assertEquals("放弃是撤消，不是第二次保存", 1, writes)
        rule.onNodeWithText("改动", substring = true).assertExists()
        rule.onNodeWithText("又敲了一句", substring = true).assertDoesNotExist()
    }

    // ═══════════ ⑫ 输入位置不被 value 回写打断（§10：输入状态与业务状态分开）═══════════

    /**
     * §10 那句"组合中的中文候选不能被每次 value 回写打断"，在 Activity 这一侧能量到的形状是
     * **插入位置**：每敲一笔，本页把 `TextFieldValue` 存回 `editorStates` 再把 `drafts` 换成新串
     * （整屏跟着重组）。只要回写用的是**带光标的那一份**，下一笔就落在上一笔后面；
     * 谁把它降级成 `String`（`value = TextFieldValue(text)` 那种重建）再喂回去，光标就回到 0，
     * 第二笔插到最前面 → 顺序当场颠倒，这一格就是这么红的。
     *
     * 期望串由**本次输入的两段**拼出来，长度由本次读数算，不写死常数。
     * 软键盘与中文候选本身在 JVM 拿不到（Robolectric 没有 IME）⇒ 那一半归真机，不在这里拿字符串冒充。
     */
    @Test
    fun `each keystroke write-back keeps the insertion point so the next piece lands after the previous one`() {
        mountWith(read = { _ -> "" to "sha-empty" })
        enterEdit()

        val pieceA = "第一段"
        val pieceB = "第二段"
        rule.onNode(hasSetTextAction()).performTextInput(pieceA)
        rule.waitForIdle()
        assertEquals("第一笔之后编辑器里就该只有那三个字", pieceA, editorText())

        rule.onNode(hasSetTextAction()).performTextInput(pieceB)
        rule.waitForIdle()
        assertEquals(
            "第二笔没有接在第一笔后面 = 上一次的 value 回写把插入点打回了开头，实到「${editorText()}」",
            pieceA + pieceB, editorText()
        )

        // 另一条业务状态（字数那一句）跟着走了，却不许反过来动输入
        val len = editorText().length
        rule.onNode(hasText("｜ $len 字", substring = true)).assertExists()
    }
}
