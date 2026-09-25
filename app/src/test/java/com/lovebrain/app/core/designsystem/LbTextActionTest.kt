package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiProbeApplication
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 :529 那行里的"可选文字动作；动作热区 ≥48dp"——这一格它才有了页级的主人。
 *
 * 为什么单独测：`LbEmptyState` 里那颗文字动作由 `LbAsyncStateTest` 量着（同一处实现），
 * 但**弱化那一档语气（Muted）从没被量过**——而"看着更不重要所以热区更小"正是这一档
 * 最容易长出来的样子。首次引导那颗「跳过」本机实量 **38x25dp** 就是这么来的：
 * 它是那屏唯一能退出流程的出口，却比一个手指点得中的范围小一半。
 *
 * 只读语义树，不在源码里搜 `heightIn`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbTextActionTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    @Test
    fun `a muted text action still fills the floor and announces itself as a button`() {
        var fired = 0
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                LbTextAction(
                    label = "跳过",
                    onClick = { fired++ },
                    tone = LbTextActionTone.Muted
                )
            }
        }
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "LbTextAction(Muted)", "（320dp）")
        assertEquals("这一屏只该有这一颗动作：" + targets.joinToString { it.describe() }, 1, targets.size)
        assertEquals("文字动作没有 contentDescription，读屏念的就是这两个字", "跳过", targets.single().label)
        assertEquals(
            "裸 Text + clickable 时读屏不认它是按钮，必须组件自己补 Role.Button：" +
                targets.single().describe(),
            "Button",
            targets.single().role
        )
        probe.assertAllActionableLabeled(rule, "LbTextAction(Muted)")
    }

    /**
     * 换语气不许换热区。
     *
     * 标签必须是**一个字**。第一版写了「以后再说」，量出来 Accent 120dp 宽、Muted 112dp 宽，
     * 我以为抓到了"语气越长越瘦"——其实是两档字号本来就差 8dp，**文字比下限宽，
     * 下限根本没成为约束**，于是拿一个由文案决定的数去比另一个由文案决定的数。
     * 换成单字后宽度由 `widthIn(min = 48)` 决定，这一格才真的在量下限
     * （`LbEmptyState` 那颗第一版只垫高度、短标签量出 40x48dp，就是同一件事）。
     *
     * ⚠ 这把尺**看不见颜色与字号**——语义树里没有这两个属性，所以它证明的是
     * "两档语气的可点范围一样"，不是"两档语气长得符合词表"。后者只能等截图基线
     * （§6.5 :538，仍记在未做里）。
     */
    @Test
    fun `weakening the tone must not weaken the target`() {
        val tone = mutableStateOf(LbTextActionTone.Accent)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                LbTextAction(label = "好", onClick = {}, tone = tone.value)
            }
        }
        val sizes = mutableMapOf<String, Pair<Float, Float>>()
        for (t in listOf(LbTextActionTone.Accent, LbTextActionTone.Muted)) {
            rule.runOnIdle { tone.value = t }
            rule.waitForIdle()
            val target = probe.assertAllActionableMeetTouchFloor(rule, "LbTextAction", "（$t）").single()
            sizes[t.name] = target.widthDp to target.heightDp
            assertEquals("两档语气都必须自己声明按钮角色，$t 那档也不例外", "Button", target.role)
        }
        val (accentW, accentH) = sizes.getValue("Accent")
        val (mutedW, mutedH) = sizes.getValue("Muted")
        assertEquals("换了语气热区就变了宽：说明下限不是写在组件里而是被文案带出来的", accentW, mutedW, 0.5f)
        assertEquals("换了语气热区就变了高：说明下限不是写在组件里而是被文案带出来的", accentH, mutedH, 0.5f)
        assertTrue(
            "单字标签本该撑不满 48dp，量到 ${accentW.toInt()}x${accentH.toInt()}dp 说明垫宽那一步没生效",
            accentW >= probe.floorDp - 0.5f
        )
    }
}
