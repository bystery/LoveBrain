package com.lovebrain.app.ui.visual

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.5 的**截图基线**第一格（指导书 :527–538 那张清单的最后一行 + :610 的
 * "roborazzi / paparazzi 二选一"——这里选 roborazzi，paparazzi 不引入）。
 *
 * 为什么不走设备截屏：三个 Activity 全都设了 `FLAG_SECURE`
 * （`SetupActivity.kt:89`、`KnowledgeBaseActivity.kt:113`、`KbEditActivity.kt:125`，
 * 注释写的理由是"防止 API Key / 关系数据在最近任务截图里泄露"），而
 * CI run 36236822959 实测：包重装回去、`dumpsys` 确认前台就是 app 之后，
 * `adb exec-out screencap -p` 交回 **0 字节** ⇒ 设备侧那一路结构上拿不到证据
 * （此前那两张"114996 字节的屏幕截图"拍的是安卓桌面，前台记录写着 `launcher3/.Launcher`）。
 * JVM 里用 Robolectric + NATIVE 图形渲染 composable，不经过那条安全开关。
 *
 * 判据形状按 :538 那句立："baseline 变更必须人工 review，不允许自动覆盖 baseline 后直接绿"：
 * - **CI 只跑 `:app:verifyRoborazziDebug`**（比对 `app/src/test/roborazzi/` 里已提交的基线，不一致就红）；
 * - 重新生成是显式人工动作 `./gradlew :app:recordRoborazziDebug` + 人看过 PNG + 单独一笔提交；
 * - 普通 `testDebugUnitTest` 不参与比对（roborazzi 只在带 `roborazzi.test.verify=true` 时比），
 *   所以"跑过单测"绝不等于"看过视觉回归"——这道步必须单独存在。
 *
 * ⚠ 覆盖面老实说：这一格立的是**机制 + 一颗组件的三态**；
 * 4 宽 × 3 字 × 中英 × 各屏的矩阵仍未铺开（:532–535 那几行照旧未达）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbPrimaryButtonVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun labelFor(s: LbButtonState) = "LBL_" + s.name.uppercase()

    /**
     * 一次 setContent 挂一颗按钮并当场截图：`captureRoboImage` 是**包住内容**的 composable
     * （1.30.0 的 compose API 就是这个形状；它不是 `SemanticsNodeInteraction` 的扩展）。
     * **不传文件路径**：第一次实测传了相对名，结果三张 PNG 落在 `app/` 模块根目录
     * （`app/lbprimarybutton_idle_360.png` 等，已挪进 `_temp/roborazzi-stray-first-run/` 留档）——
     * 那种位置既不进基线比对，还会被人当成仓库里的垃圾删掉。用 roborazzi 的默认命名，
     * 记录落 `app/build/outputs/roborazzi/`，比对读 `app/src/test/roborazzi/`。
     */
    private fun shot(s: LbButtonState) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    LbPrimaryButton(
                        state = s,
                        label = labelFor(s),
                        onClick = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        // LOADING 里有 rememberInfiniteTransition：自动时钟下永远不空闲，手动推两帧定住画面
        rule.mainClock.advanceTimeBy(16L)
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun idleHasACommittedVisualBaseline() = shot(LbButtonState.Idle)

    @Test
    fun loadingHasACommittedVisualBaseline() = shot(LbButtonState.Loading)

    @Test
    fun stopHasACommittedVisualBaseline() = shot(LbButtonState.Stop)
}
