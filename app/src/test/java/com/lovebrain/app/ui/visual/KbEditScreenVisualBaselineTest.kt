package com.lovebrain.app.ui.visual

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.KbFile
import com.lovebrain.app.ui.KbEditScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 知识库编辑页（`KbEditScreen`）的屏幕级截图基线——这一页 §6.3 四态收进 `LbAsyncState` 之后
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
 * 同模块可见；`readFile` 给同步 stub 返回固定正文 + 版本，`LaunchedEffect` 走完即落 Content。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KbEditScreenVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private val files = listOf(
        KbFile("最近两句", "moment/recent.md", layer = "当下"),
        KbFile("在聊什么", "moment/topic.md", layer = "当下"),
        KbFile("我是谁", "understand/me.md", layer = "画像")
    )

    private fun mount() {
        rule.setContent {
            UiMatrix(360, heightDp = 1200).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    KbEditScreen(
                        files = files,
                        lastFile = files.first().path,
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
        }
        rule.mainClock.advanceTimeBy(16L)
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
}
