package com.lovebrain.app.ui.visual

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbDialog
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.LbDialogMessageTone
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `LbDialog` 的截图基线（第6节第5条 :538「baseline 变更必须人工 review」）。
 *
 * ⚠ 这一族用的入口与其余几格**不一样**：`captureRoboImage { … }` 那一发是另起一棵
 * 透明 activity、拍它那棵 ComposeView，而 `AlertDialog` 起的是**另一扇窗**——
 * 用前者拍对话框只会得到一张空白，那是一道会永远绿的假闸。这里改用屏幕级那一发
 * （roborazzi 自己的注释就是 "Capture the screen image including dialogs"），
 * 遮罩与对话框窗口一起收进同一张图。
 *
 * 五档语气/形状都取自真实调用点，没有一档是发明的：
 * - `plainMessageWithTwoActions`：`KnowledgeBaseActivity:237`（未配置供应商二选一，
 *   Accent 的「继续」+ Muted 的「取消」）。
 * - `destructiveConfirm`：`ProviderSection:284` / `KbEditActivity:560` /
 *   `KnowledgeBaseActivity:359` 那三处删除确认（Destructive + Muted）。
 * - `errorToneMessage`：`FeedbackCasesScreen:412` 导出失败那一句（`LbDialogMessageTone.Error`）。
 * - `disabledConfirm`：`KnowledgeBaseActivity:512` 的 `enabled = renameText.isNotBlank()`——
 *   表单没填时那颗 affirmative 就是这一档灰。
 * - `secondaryActionsWithBody`：`FeedbackCasesScreen:339` 导出预览（一个主动作 +
 *   两颗次级 + 关闭 + 自定义正文），这一格同时钉住"一个主动作 + 最多三个次级"那排按钮的行距。
 *
 * 为什么对话框需要像素而语义树不够：`LbDialogTest` 那把尺量的是每颗按钮的
 * `boundsInRoot`（≥48dp 那条下限），它量不出正文用了哪一档颜色、标题与正文之间的留白、
 * 三颗次级按钮是不是挤在同一行。那些正是"一功能一种格式"最容易漂回去的地方。
 *
 * 文字一律 ASCII（测试里写死的字面量，不走资源）：本项目已收成单一中文资源
 * （删掉了 `values-en`），这里用硬编码 ASCII 是为了让基线只钉版式、不随 locale 漂。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalRoborazziApi::class)
class LbDialogVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(content: @Composable () -> Unit) {
        rule.setContent { content() }
        // AlertDialog 有进出场动画，不定帧就每跑一次差几帧
        rule.mainClock.advanceTimeBy(400L)
        captureScreenRoboImage()
    }

    @Test
    fun plainMessageWithTwoActionsHasACommittedVisualBaseline() = shot {
        LbDialog(
            title = "DIALOG_NO_PROVIDER",
            onDismissRequest = {},
            message = "without a provider only an empty template can be created",
            confirm = LbDialogAction("CONTINUE", {}),
            dismiss = LbDialogAction("CANCEL", {}, tone = LbDialogActionTone.Muted)
        )
    }

    @Test
    fun destructiveConfirmHasACommittedVisualBaseline() = shot {
        LbDialog(
            title = "DIALOG_DELETE_PROVIDER",
            onDismissRequest = {},
            message = "deleting cannot be undone, all fields must be filled again",
            confirm = LbDialogAction("DELETE", {}, tone = LbDialogActionTone.Destructive),
            dismiss = LbDialogAction("CANCEL", {}, tone = LbDialogActionTone.Muted)
        )
    }

    @Test
    fun errorMessageToneHasACommittedVisualBaseline() = shot {
        LbDialog(
            title = "DIALOG_EXPORT_FAILED",
            onDismissRequest = {},
            message = "the export step reported a failure",
            messageTone = LbDialogMessageTone.Error,
            confirm = LbDialogAction("CLOSE", {})
        )
    }

    @Test
    fun disabledConfirmHasACommittedVisualBaseline() = shot {
        LbDialog(
            title = "DIALOG_RENAME",
            onDismissRequest = {},
            message = "the name must not be blank",
            confirm = LbDialogAction("SAVE", {}, enabled = false),
            dismiss = LbDialogAction("CANCEL", {}, tone = LbDialogActionTone.Muted)
        )
    }

    @Test
    fun secondaryActionsWithBodyHasACommittedVisualBaseline() = shot {
        LbDialog(
            title = "DIALOG_EXPORT_PREVIEW",
            onDismissRequest = {},
            confirm = LbDialogAction("SAVE", {}),
            secondary = listOf(
                LbDialogAction("SHARE", {}),
                LbDialogAction("COPY", {})
            ),
            dismiss = LbDialogAction("CLOSE", {}, tone = LbDialogActionTone.Muted),
            body = {
                Column {
                    Text(
                        text = "PREVIEW_BODY_FIRST_LINE",
                        style = AppTypography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "COPY SHARES OR SAVES THE FILE",
                        style = AppTypography.labelSmall,
                        color = TextHint
                    )
                }
            }
        )
    }
}
