package com.lovebrain.app.ui.common

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 归并 `RowActionButton`（行内次级小按钮）之后的读数账。
 *
 * 这颗的归属是 **`LbTextAction`（文字动作）**，不是 `LbSettingRow`（行）：
 * 它自己就是"行的尾部那一颗"，没有标题/说明/状态可归，而 `LbSettingRow` 的尾部槽
 * 也只在"整行"存在时才有意义（供应商行、纠正行）。指导书 §6.1 表里"文字动作"这个词
 * 有主人之后（`LbEmptyState` 那行："可选文字动作；动作热区 ≥48dp"），
 * 页面再自己画一颗 `Box + Text + clickable` 就是第二次实现同一件事。
 *
 * ⚠ 归并之前这格的两条判据在旧实现上是**红的**（本机实测写在各格的失败信息里）：
 *  - 旧 `RowActionButton` 的 `clickable` 没声明角色 ⇒ 读屏只念那两个字，不念"按钮"；
 *  - 旧实现只垫了高度（`heightIn(min = 48)`）⇒ 短标签量出 **宽 32dp**，
 *    与 `LbEmptyState` 第一版"只垫高度量出 40x48dp"是同一件事（账本 §39.5）。
 *    现在两个数都由 `LbTextAction` 那一处保证。
 *
 * 只读语义树，不在源码里搜 `heightIn`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RowActionSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun mount(label: String, onClick: () -> Unit) {
        rule.setContent {
            UiMatrix(320).RenderIn(LocalDensity.current.density) {
                RowActionButton(text = label, onClick = onClick)
            }
        }
        rule.waitForIdle()
    }

    /** 名字读得到、点下去真的执行一次 */
    @Test
    fun `the row action reads its label and fires exactly once`() {
        var fired = 0
        mount("EDIT_LABEL") { fired++ }

        val targets = probe.actionableTargets(rule, "行内动作")
        assertEquals("这一屏只该有这一颗动作：" + targets.joinToString { it.describe() }, 1, targets.size)
        assertEquals("EDIT_LABEL", targets.single().label)
        rule.onNodeWithText("EDIT_LABEL").performClick()
        rule.waitForIdle()
        assertEquals("按一次就该回调一次", 1, fired)
    }

    /**
     * 两轴都够下限，并且报得出按钮角色（§6.5 :531 + :532 两件事一起判）。
     *
     * 标签刻意用**单个字符**：文字比下限宽的时候，宽度这把尺根本不会成为约束，
     * 那种断言是恒真的（`LbTextActionTest` 第二格就是这么从四字标签改成单字的）。
     *
     * ⚠ 归并之前这一格是红的，读数原样记在这里：
     * `「X」 role=无 selected=null state=null 尺寸 32x48dp @(0,0)`
     * ——旧实现只垫了高度，也没声明角色。
     */
    @Test
    fun `the row action fills the floor on both axes and announces itself as a button`() {
        mount("X") {}
        val target = probe.actionableTargets(rule, "行内动作").single()
        assertEquals(
            "行内次级动作也得报按钮角色：" + target.describe(),
            "Button", target.role
        )
        assertTrue(
            "热区两轴都要 ≥${probe.floorDp.toInt()}dp（只垫高度不算达标）：" + target.describe(),
            !target.tooSmall(probe.floorDp)
        )
    }

    /**
     * 换语气不许换热区（同 `LbTextActionTest` 第二格的规矩）。
     *
     * 「删除」那一支走错误色，但它必须和「编辑」那一支一样点得到——
     * "看着更危险所以更小"是这一族最容易长出来的样子。
     */
    @Test
    fun `switching the tone must not switch the hot zone`() {
        val tint = mutableStateOf<Color>(TextSecondary)
        rule.setContent {
            UiMatrix(320).RenderIn(LocalDensity.current.density) {
                RowActionButton(text = "X", tint = tint.value, onClick = {})
            }
        }
        val sizes = mutableMapOf<String, Pair<Float, Float>>()
        for ((name, color) in listOf("次要" to TextSecondary, "删除" to Error)) {
            rule.runOnIdle { tint.value = color }
            rule.waitForIdle()
            val target = probe.actionableTargets(rule, "行内动作（$name）").single()
            sizes[name] = target.widthDp to target.heightDp
            assertEquals("$name 那一档也得报按钮角色：" + target.describe(), "Button", target.role)
        }
        val (a, b) = sizes.getValue("次要")
        val (c, d) = sizes.getValue("删除")
        assertEquals("两档语气的热区宽度不一致：$a vs $c", a, c, 0.5f)
        assertEquals("两档语气的热区高度不一致：$b vs $d", b, d, 0.5f)
        assertTrue(
            "单字标签本该撑不满 ${probe.floorDp.toInt()}dp，量到 ${a.toInt()}x${b.toInt()}dp 说明垫宽那一步没生效",
            a >= probe.floorDp - 0.5f
        )
    }

    /**
     * 旧 `tint` 旋钮现在只选语气档，不再直接当颜色用。
     *
     * 这一格只能判纯函数：语义树里没有颜色与字号两栏（`LbTextActionTest` 自己就写着这件事），
     * 所以"删除那颗走错误色"这个说法除了这样钉住，没有别的证人。
     */
    @Test
    fun `the legacy tint knob resolves onto the tone table`() {
        assertEquals("删除那一支要有自己的档", LbTextActionTone.Destructive, rowActionTone(Error))
        assertEquals("默认那一支是行内次级", LbTextActionTone.RowSecondary, rowActionTone(TextSecondary))
        assertEquals(
            "词表里没有的颜色不许被当成一个新颜色，退回次要档",
            LbTextActionTone.RowSecondary, rowActionTone(Color.Red)
        )
    }
}
