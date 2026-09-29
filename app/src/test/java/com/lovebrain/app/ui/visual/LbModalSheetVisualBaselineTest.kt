package com.lovebrain.app.ui.visual

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbModalSheetTitle
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `LbModalSheet` 的截图基线——浮窗世界里那一套遮罩 + 居中卡片。
 *
 * 为什么这一族特别需要像素：它**不是** Material 的 Dialog（`TYPE_APPLICATION_OVERLAY`
 * 窗口里没有合适的 activity token，`AlertDialog` 会抛 `BadTokenException`），
 * 遮罩、卡片底、描边、圆角、动作行全是这一处自画的。自画的东西没有 Material 兜底，
 * 一次 `padding` 顺序改动就能让卡片贴到屏边，而语义树上节点一个不少。
 *
 * 五格对着真实调用点取参数：
 * - `singleAccentAction` / `allActionTones`：`DislikeReasonPanel:72`（两颗 Muted）、
 *   `MemoryCorrectionFlow:107`、`RecordSentFlow:153`（带 `enabled = !flow.saving` 的禁用档）。
 *   四档语气（Accent 实心 / Muted 退一步 / Destructive 错误色 / 禁用灰底）都在同一张图里，
 *   因为它们共用那一颗 `LbModalSheetActionCell`——着色表被改一行就该看得见。
 * - `blankLabelActionDropped`：标签为空的动**根本不入树**（修的是旧形状那颗
 *   24x22dp、没有任何可读名字的可点节点）。这一格拍的是"只剩一颗"那半张图。
 * - `loadingContent`：`FeedbackCasesScreen:331` 导出中那一格（不可点空白关 + 进度环）。
 *   `dismissable` 换的是手势、不是像素，所以这格钉的是那一格的**内容形状**。
 * - `tallerThanCardMaxHeight`：卡片有 560dp 上限，超了就滚，不把面板顶出屏。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbModalSheetVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(dismissable: Boolean = true, content: @Composable () -> Unit) {
        rule.setContent {
            UiMatrix(360, heightDp = 640).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    LbModalSheet(
                        onDismissRequest = {},
                        dismissable = dismissable,
                        content = content
                    )
                }
            }
        }
        // 进度环那一格是不定动画：手动推到定帧，四格都按同一个节拍拍
        rule.mainClock.advanceTimeBy(16L)
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 卡片里那一列：标题 + 一句说明 + 动作行——生产三处调用点都是这个形状 */
    @Composable
    private fun Titled(body: String, actions: List<LbDialogAction>) {
        LbModalSheetTitle(text = "SHEET_TITLE")
        Spacer(Modifier.height(Spacing.md))
        Text(text = body, style = AppTypography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(Spacing.md))
        LbModalSheetActions(actions = actions)
    }

    @Test
    fun singleAccentActionHasACommittedVisualBaseline() = shot {
        Titled("one decision to make", listOf(LbDialogAction("SAVE", {})))
    }

    @Test
    fun allActionTonesHasACommittedVisualBaseline() = shot {
        Titled(
            "four tones share one cell",
            listOf(
                LbDialogAction("SAVE", {}),
                LbDialogAction("CANCEL", {}, tone = LbDialogActionTone.Muted),
                LbDialogAction("DELETE", {}, tone = LbDialogActionTone.Destructive),
                LbDialogAction("DONE", {}, enabled = false)
            )
        )
    }

    @Test
    fun blankLabelActionDroppedHasACommittedVisualBaseline() = shot {
        Titled(
            "the blank one never enters the tree",
            listOf(
                LbDialogAction("SAVE", {}),
                LbDialogAction("   ", {}, tone = LbDialogActionTone.Muted)
            )
        )
    }

    @Test
    fun loadingContentHasACommittedVisualBaseline() = shot(dismissable = false) {
        CircularProgressIndicator(color = Primary)
    }

    @Test
    fun tallerThanCardMaxHeightHasACommittedVisualBaseline() = shot {
        Column {
            repeat(24) { index ->
                Text(
                    text = "SHEET_LONG_ROW_$index",
                    style = AppTypography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
