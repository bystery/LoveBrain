package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.5 :532「可交互控件在语义树里说得出角色」——**设计系统自己欠的那笔**。
 *
 * 起因不是这一格想到了要查，是搬首页那颗主按钮时撞出来的：
 * `LbPrimaryButton`（手画 `Box + clickable`）**四态都没声明 `Role.Button`**，
 * 而它替掉的那颗 Material `Button` 是自带的——于是"改用统一组件"那一步
 * 自己引入了一次无障碍回归（`4ee1514`，坑表 87）。
 *
 * ⚠ 为什么这一格必须挂起来量，不许读源码数 `role =`：
 *   - **反方向也漏**：Material 组件的角色根本不出现在源码里，读源码会把它们判成"都缺"；
 *   - 声明了 `role =` 不等于树里读得到（挂在被合并掉的子节点上就换了一个所有者）；
 *     没声明也不一定就念成按钮——两件事都只有语义树说了算。
 *   所以 `_temp/scan_roles.py` 那份 6 处清单在这里只当**待测名单**用，不当结论。
 *
 * 一个用例只能 `setContent` 一次 ⇒ 一颗组件一格。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignSystemRolesTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /** 读语义树里所有可交互节点；失败信息一律带 describe()，好说出"哪一颗、多大、念什么"。 */
    private fun targets(what: String) = probe.actionableTargets(rule, what)

    @Test
    fun `the action card announces itself as a button`() {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360).RenderIn(d) {
                LbActionCard(
                    iconRes = R.drawable.ic_copy,
                    title = "打开军师",
                    subtitle = "让军师看着这段对话",
                    onClick = {}
                )
            }
        }
        val got = targets("LbActionCard")
        assertEquals("快捷卡应当只有一颗可交互节点：" + got.joinToString { it.describe() }, 1, got.size)
        assertEquals(
            "整张卡就是一处操作，读屏得说出它是按钮：" + got.single().describe(),
            "Button", got.single().role
        )
    }

    @Test
    fun `the metric grid announces itself as a button when it is tappable`() {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360).RenderIn(d) {
                LbMetricGrid(
                    totalGenerate = "128",
                    totalCost = "¥3.40",
                    adoptRate = "62%",
                    onClick = {}
                )
            }
        }
        val got = targets("LbMetricGrid")
        assertEquals("统计卡应当只有一颗可交互节点：" + got.joinToString { it.describe() }, 1, got.size)
        assertEquals(
            "点它就去使用概览，那它就是按钮：" + got.single().describe(),
            "Button", got.single().role
        )
    }

    @Test
    fun `a setting row and its trailing action each announce themselves`() {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360).RenderIn(d) {
                LbSettingRow(
                    iconRes = R.drawable.ic_copy,
                    title = "供应商",
                    subtitle = "配置模型与密钥",
                    trailingText = "管理",
                    onTrailingClick = {},
                    onClick = {}
                )
            }
        }
        val got = targets("LbSettingRow")
        assertEquals(
            "整行 + 尾部那颗动作，两处都该在树里：" + got.joinToString { it.describe() },
            2, got.size
        )
        got.forEach { t ->
            assertEquals("两处操作都得报得出角色：" + t.describe(), "Button", t.role)
        }
    }

    @Test
    fun `sheet actions announce themselves`() {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360).RenderIn(d) {
                LbModalSheetActions(
                    listOf(
                        LbDialogAction(label = "取消", onClick = {}, tone = LbDialogActionTone.Muted),
                        LbDialogAction(label = "保存", onClick = {}, tone = LbDialogActionTone.Accent)
                    )
                )
            }
        }
        val got = targets("LbModalSheetActions")
        assertEquals("两档动作都该在树里：" + got.joinToString { it.describe() }, 2, got.size)
        got.forEach { t ->
            assertEquals("浮层动作也得报角色：" + t.describe(), "Button", t.role)
        }
    }

    @Test
    fun `the page header back control announces itself as a button`() {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360).RenderIn(d) {
                LbTopBar(
                    title = "使用概览",
                    level = LbTopBarLevel.Page,
                    onBack = {}
                )
            }
        }
        val got = targets("LbTopBar")
        assertEquals("页头只该有返回这一颗可点：" + got.joinToString { it.describe() }, 1, got.size)
        assertEquals(
            "返回那颗必须报成按钮——它同时是这一页唯一的退出入口：" + got.single().describe(),
            "Button", got.single().role
        )
        // 名字仍得来自资源（§6.5 第②栏那笔的判据形状，这里顺手钉住不回退）
        val expected = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.common_back)
        assertEquals("返回那颗的名字来自资源", expected, got.single().contentDescriptions.joinToString())
    }
}
