package com.lovebrain.app.ui.visual

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbAsyncTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.KbFile
import com.lovebrain.app.ui.KbEditScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 知识库编辑页（`KbEditScreen`）的屏幕级截图基线——这一页 第6节第3条 四态收进 `LbAsyncState` 之后
 * 在像素上的第一份证人。
 *
 * 这一页的回归风险正是它自己 KDoc 里写的那两件「树上读不出来」的事：
 * - 左边那一排文件标签的选中态涂在 `containerColor` 上，读屏听不出「哪一份正开着」
 *   （`selected` 那条由 `KbEditScreenSemanticsTest` 钉，像素由这一格钉）；
 * - `LbAsyncState` 那四格里 Content 这一档下面还有「预览 / 编辑」两支，预览那支走
 *   `MarkdownText` 分块懒渲染——块切多大、`prettyForPreview` 把 plan.md 那张表抹成什么样，
 *   只有像素知道。
 *
 * 只摆「预览态·有正文」那一档：这是产品天天走的那一格（`isPreview = true` 且正文非空），
 * Loading / Error / Empty 三档都收在 `LbAsyncState` 那张组件级基线里，不另开屏幕级基线
 * 去重复它。
 *
 * 挂载方式照 `KbEditScreenStatesTest`：`KbFile` 与 `KbEditScreen` 都是 `internal`，
 * 同模块可见；`readFile` 给同步 stub 返回固定正文 + 版本。
 *
 * ⚠ **两处与上一版不同，都是判据逼出来的**（上一版把 Loading 档钉成了 `previewWithContent`）：
 * 1. **等法**：这一屏的读文件在 `LaunchedEffect` 里（`KbEditActivity.kt:231-246` 才把
 *    `loaded` 置真），`setContent` 之后只 `advanceTimeBy(16L)` 就停在 Loading 档——CI run
 *    36730225255 的实到图正是那张（卡体全白、`0 字`、居中一个 spinner 残弧）。
 *    同页的 `KbEditScreenStatesTest:87` 用的是 `rule.waitForIdle()`，这里**照同一口径**补上，
 *    不发明第二套等待。
 * 2. **拍法**：`captureRoboImage { content }` 那个 composable 形态会把 content 装进
 *    **另一颗 `RoborazziTransparentActivity` 的 composition**（本机实量：这样拍时
 *    `rule.onRoot()` 的语义树 `children=0`，屏幕上却照样有字）。那条通道下，任何
 *    "录制前的状态断言"读的都是**没被拍的那一棵**，判据就是假的。所以这里改成
 *    先 `setContent` → `waitForIdle` → 判状态 → `rule.onRoot().captureRoboImage()`，
 *    断言与像素出自**同一棵已落定的树**；落盘的文件名仍走 roborazzi 默认命名
 *    （`<类名>.<方法名>.png`，与其余 42 张同一规则）。
 *
 * 窗口高度与下面 `UiMatrix` 的 heightDp **对齐**（都 1000dp）：上一版 `@Config` 写 h640dp
 * 而矩阵要 1200dp，`Modifier.size` 被父约束夹回 640dp，画到一半还当画全了。
 * `zh-rCN` 固定 locale（1.4 只面向中文，用户已定）：没有那一段时 Robolectric 落 en-US，
 * 屏上就是「资源英文 + 内联中文」的混排，换机器换 locale 就换图。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KbEditScreenVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val files = listOf(
        KbFile("最近两句", "moment/recent.md", layer = "当下"),
        KbFile("在聊什么", "moment/topic.md", layer = "当下"),
        KbFile("我是谁", "understand/me.md", layer = "画像")
    )

    /** 这一格开着的那一份，与它该显示的正文。 */
    private val selected: KbFile = files.first()
    private val body: String get() = sampleOf(selected.path)

    private fun mount() {
        rule.setContent {
            UiMatrix(360, heightDp = 1000).RenderIn(LocalDensity.current.density) {
                KbEditScreen(
                    files = files,
                    lastFile = selected.path,
                    onLastFileChange = {},
                    readFile = { path ->
                        // 同步返回固定正文 + 版本：LaunchedEffect 走完即落 Content/预览
                        sampleOf(path) to "v1"
                    },
                    saveFile = { _, _, _ -> "v2" },
                    onBack = {}
                )
            }
        }
        // 与 `KbEditScreenStatesTest:87` 同一口径的唯一一次等待
        rule.waitForIdle()
        assertPreviewShowsCommittedContent()
        rule.onRoot().captureRoboImage()
    }

    /**
     * 录制前的状态断言：这一格必须到了它名字承诺的那一档——**预览 · 有正文**。
     *
     * 五条各咬一种坏实现：
     * - spinner 还在 = 那一次读没走完（Loading 档，就是上一版实到的那张）；
     * - 状态件说明锚点还在 = Empty / Error 档；
     * - 卡头那句「标签 ｜ N 字」按**夹具正文的长度**认（`KbEditActivity.kt:431,441`
     *   `"${selected.label} ｜ $liveLen 字"`，`liveLen = drafts[selectedPath].length`）——
     *   正文没进树时它是 `0 字`，换了正文它跟着变；
     * - 预览正文里那句原话必须在树里（`MarkdownText` 逐行成节点，这里认其中一句）；
     * - 树上不许有编辑输入框 = 这一格拍的是预览那一支，不是编辑那一支。
     */
    private fun assertPreviewShowsCommittedContent() {
        assertRecordedLocaleIsZhCn()
        assertEquals(
            "还在转圈：那一屏是 Loading 档，不是预览·有正文",
            0,
            rule.onAllNodesWithTag(LbAsyncTags.LOADING).fetchSemanticsNodes().size
        )
        assertEquals(
            "状态件还挂着一句说明：那一屏是 Empty / Error 档",
            0,
            rule.onAllNodesWithTag(LbAsyncTags.MESSAGE).fetchSemanticsNodes().size
        )
        rule.onNodeWithText("${selected.label} ｜ ${body.length} 字").assertExists()
        rule.onNodeWithText(PREVIEW_SENTENCE).assertExists()
        assertEquals(
            "预览档不该有编辑输入框（`isPreview` 那一支走错了）",
            0,
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        )
    }

    /** 基线固定 zh-CN：locale 一漂，屏上的字整批变，这张就不是同一格。 */
    private fun assertRecordedLocaleIsZhCn() {
        val locales = app.resources.configuration.locales
        assertEquals("截图基线要求 zh-CN", "zh-CN", locales.toLanguageTags())
    }

    private fun sampleOf(path: String): String = when (path) {
        "moment/recent.md" ->
            "# 最近两句\n\n她说：今天好累，不想说话。\n\n我说：辛苦了，早点休息。"
        "moment/topic.md" ->
            "# 在聊什么\n\n- 工作\n- 周末计划"
        else ->
            "# 我是谁\n\n- 名字：我\n- 风格：直接"
    }

    /** 预览态·有正文：产品天天走的那一格。 */
    @Test
    fun previewWithContentHasACommittedVisualBaseline() = mount()

    private companion object {
        /** [sampleOf] 正文里的第一句原话，`MarkdownText` 会把它单独成一个文本节点。 */
        const val PREVIEW_SENTENCE = "她说：今天好累，不想说话。"
    }
}
