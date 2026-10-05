package com.lovebrain.app.ui.visual

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbRowState
import com.lovebrain.app.core.designsystem.LbSettingRow
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
 * `LbSettingRow` 的截图基线（首页「服务设置」那两行的版式，第6节第5条 :538 要求人工 review 才能改）。
 *
 * 参数口径全部对着 `HomeScreen:174` / `HomeScreen:187` 取：这两行是生产里唯一的调用点，
 * 它们交出的是 `dot` + `statusText` + `trailingText` + `onClick` 四颗旋钮——
 * 图标那两档（`icon` / `iconRes`）**今天没有任何调用点在用**，所以这里不替它们拍基线，
 * 免得把"组件签名里有"误记成"用户看得见"。
 *
 * 四格各自钉一条树上读不到的东西：
 * - `readyWithStatusAndTrailing`：点用 `LbRowState.Ready` 的颜色，旁边还跟着那两个字
 *   （这两个字曾被组件丢过一次——`statusText` 的值从没被画出来，KDoc 记着）。
 * - `notReadyWithoutStatusText`：无权限那一档——`statusText = null`、整行不可点
 *   （`onClick = null`），只剩一颗灰点 + 尾部那颗入口。
 * - `shortTrailingWord…`：尾部那颗只有下限宽。中文环境里它就是「管理」两字，
 *   实量过 38x48dp；ASCII 这边用两个同宽的字母顶上，钉的是"见方"那条下限的第二根轴。
 * - `longSubtitle…`：说明行 `maxLines = 1`，而生产交进来的长度没有上限（供应商名 + 模型名）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbSettingRowVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(
        title: String,
        subtitle: String,
        dot: LbRowState?,
        statusText: String?,
        trailingText: String?,
        withRowClick: Boolean
    ) {
        rule.setContent {
            UiMatrix(360, heightDp = 100).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    LbSettingRow(
                        title = title,
                        subtitle = subtitle,
                        dot = dot,
                        statusText = statusText,
                        trailingText = trailingText,
                        onTrailingClick = if (trailingText != null) ({}) else null,
                        onClick = if (withRowClick) ({}) else null
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun readyWithStatusAndTrailingHasACommittedVisualBaseline() =
        shot("ROW_PROVIDER", "DeepSeek · deepseek-chat", LbRowState.Ready, "ON", "MANAGE", true)

    @Test
    fun notReadyWithoutStatusTextHasACommittedVisualBaseline() =
        shot("ROW_CAPTURE_NO_PERMISSION", "no app is allowed yet", LbRowState.NotReady, null, "GRANT", false)

    @Test
    fun shortTrailingWordHasACommittedVisualBaseline() =
        shot("ROW_CAPTURE", "3 app(s) allowed", LbRowState.Ready, "ON", "OK", true)

    @Test
    fun longSubtitleHasACommittedVisualBaseline() =
        shot(
            "ROW_PROVIDER_WITH_A_VERY_LONG_NAME",
            "a model name long enough to be cut on a narrow phone row",
            LbRowState.Ready,
            "ON",
            null,
            true
        )
}
