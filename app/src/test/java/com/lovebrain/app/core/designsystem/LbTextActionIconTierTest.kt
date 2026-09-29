package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
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
 * 图标档的读数账——**这一格之前它不存在**，而它接管的那些形状各自欠着什么，
 * 全都记在被搬走的那几处注释里：只垫一条轴（短文字量出 40x48dp、行内动作量出 32x48dp）、
 * `role=无`（首页那两颗入口、消息行那颗删除、逐条记忆那颗 `⋯`）、
 * 名字写在内层图标上（语义合并后念两遍）、名字写成内联中文（英文环境下仍念中文）。
 *
 * 判据形状抄 `RowActionSemanticsTest` 与 `LbTextActionTest`：**只读语义树**，
 * 不在源码里搜 `size(` 或 `heightIn`。
 *
 * ⚠ 这把尺**看不见颜色与字形尺寸**——语义树里没有这两栏。所以这里判的是
 * "换档不换热区"，不是"某一档真的是 22dp"；后者只有截图基线那一格能说，
 * 而那格现在还欠着（同 `LbTextActionTest` 第二格写的边界）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbTextActionIconTierTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun mountVector(description: String, onClick: () -> Unit, enabled: Boolean = true) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                LbTextAction(
                    icon = Icons.Filled.Close,
                    description = description,
                    onClick = onClick,
                    enabled = enabled
                )
            }
        }
        rule.waitForIdle()
    }

    /**
     * 名字与角色：读屏说得出这一句，而且只念一遍。
     *
     * 「只念一遍」不是凑数：这一族的旧写法是把标签写在内层 `Icon` 上，
     * 而外层那颗带点击的盒自己没有任何名字——树里读到的角色是 `Image`（知识库那颗
     * 18dp 删除图标就是这么量到的），合并出来的角色根本不是读屏要的那个。
     * 现在标签只挂在带 `clickable` 的那一颗上，内层图标是装饰。
     */
    @Test
    fun `the icon tier announces its description once and as a button`() {
        mountVector("CLOSE_LABEL", onClick = {})
        val targets = probe.actionableTargets(rule, "LbTextAction 图标档")
        assertEquals("这一屏只该有这一颗动作：" + targets.joinToString { it.describe() }, 1, targets.size)
        val target = targets.single()
        assertEquals(
            "图标没有文字，名字只能来自 contentDescription，而它必须挂在带点击的那一颗自己上：" +
                target.describe(),
            listOf("CLOSE_LABEL"), target.contentDescriptions
        )
        assertEquals(
            "自定义 Box + clickable 不会由 Material 补角色，图标档得自己报按钮：" + target.describe(),
            "Button", target.role
        )
        probe.assertNoDuplicatedAnnouncement(rule, "LbTextAction 图标档")
    }

    /**
     * 两轴都够下限。
     *
     * 20dp 的字形比 48dp 小得多，所以这一格量到的宽度**只能**来自那条 `widthIn`：
     * 把垫宽那一步删掉，这里立刻量到 20x48dp（`LbEmptyState` 第一版只垫高度量出
     * 40x48dp、`RowActionButton` 只垫高度量出 32x48dp，同一族）。
     */
    @Test
    fun `the icon tier fills the floor on both axes`() {
        mountVector("CLOSE_LABEL", onClick = {})
        val target = probe.actionableTargets(rule, "LbTextAction 图标档").single()
        assertEquals(
            "字形比下限小得多，宽度只许由那一档垫出来，实到 " + target.describe(),
            probe.floorDp, target.widthDp, 0.5f
        )
        assertEquals(
            "高度同理，实到 " + target.describe(),
            probe.floorDp, target.heightDp, 0.5f
        )
        assertTrue(
            "热区两轴都要 ≥${probe.floorDp.toInt()}dp：" + target.describe(),
            !target.tooSmall(probe.floorDp)
        )
    }

    /**
     * 换字形尺寸不许换热区。
     *
     * 页头返回那颗 22dp、卡片里的赞/踩 13dp，两者差 9dp 的字形，
     * 但**必须一样点得中**。这一格买的是"哪天有人给某一档偷偷补一条 `size(24.dp)`"
     * 那种改法——三档之所以是三档，就是因为尺寸这件事不再由调用方拿着。
     */
    @Test
    fun `switching the glyph tier must not switch the hot zone`() {
        val glyph = mutableStateOf(LbTextActionGlyph.Header)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                LbTextAction(
                    icon = Icons.Filled.Close,
                    description = "GLYPH_LABEL",
                    glyph = glyph.value,
                    onClick = {}
                )
            }
        }
        val sizes = mutableMapOf<String, Pair<Float, Float>>()
        for (tier in listOf(LbTextActionGlyph.Header, LbTextActionGlyph.Inline, LbTextActionGlyph.Compact)) {
            rule.runOnIdle { glyph.value = tier }
            rule.waitForIdle()
            val target = probe.assertAllActionableMeetTouchFloor(rule, "LbTextAction 图标档", "（${tier.name}）").single()
            sizes[tier.name] = target.widthDp to target.heightDp
            assertEquals("${tier.name} 那一档也得报按钮角色：" + target.describe(), "Button", target.role)
        }
        val base = sizes.getValue("Header")
        for ((name, size) in sizes) {
            assertEquals("$name 那一档的热区宽度与 Header 不一致：$base vs $size", base.first, size.first, 0.5f)
            assertEquals("$name 那一档的热区高度与 Header 不一致：$base vs $size", base.second, size.second, 0.5f)
        }
    }

    /**
     * 资源那一支与 `ImageVector` 那一支**同一个形状**。
     *
     * 两支入口只是取字形的方式不同（`R.drawable.ic_close` 与 `Icons.Filled.Close`）；
     * 要是其中一支自己再画一遍盒，热区就会分家——卡片里那三颗走的正是这一支。
     */
    @Test
    fun `the drawable overload is the same shape as the vector one`() {
        var fired = 0
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                LbTextAction(
                    iconRes = R.drawable.ic_copy,
                    description = "COPY_LABEL",
                    glyph = LbTextActionGlyph.Compact,
                    onClick = { fired++ }
                )
            }
        }
        rule.waitForIdle()
        val target = probe.actionableTargets(rule, "LbTextAction 图标档（资源那一支）").single()
        assertEquals("资源那一支也得报按钮角色：" + target.describe(), "Button", target.role)
        assertEquals(listOf("COPY_LABEL"), target.contentDescriptions)
        assertTrue("资源那一支的热区两轴都要够：" + target.describe(), !target.tooSmall(probe.floorDp))
        rule.onNode(hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals("按一次就该回调一次", 1, fired)
    }

    /**
     * 表态图标要报得出"现在表过态没有"。
     *
     * 卡片里的赞/踩被点过之后**只有图标换了颜色**，语义树里 `selected` 是 null——
     * 读屏能听到"赞、按钮"，听不到"这条方案已经表过态"（`ResultAreaTouchTargetsTest`
     * 那一屏量到的第二条）。`selected` 必须挂在带点击的这一颗上，不是内层图标上。
     */
    @Test
    fun `a marked icon announces that it is marked`() {
        val marked = mutableStateOf(true)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                LbTextAction(
                    icon = Icons.Filled.Close,
                    description = "LIKE_LABEL",
                    selected = marked.value,
                    onClick = {}
                )
            }
        }
        // 两档各读一次：名字与角色不能随状态漂移，而 selected 自己必须真的跟着翻。
        val readings = mutableMapOf<String, SemanticsProbe.Target>()
        for (state in listOf(true, false)) {
            rule.runOnIdle { marked.value = state }
            rule.waitForIdle()
            val target = probe.actionableTargets(rule, "LbTextAction 图标档（表态）").single()
            readings[state.toString()] = target
            assertEquals(
                "名字不能因为状态而换：" + target.describe(),
                listOf("LIKE_LABEL"), target.contentDescriptions
            )
            assertEquals("角色也不能：" + target.describe(), "Button", target.role)
        }
        val on = readings.getValue("true")
        assertEquals("表过态那颗必须报 true，实到 " + on.describe(), true, on.selected)
        val off = readings.getValue("false")
        assertEquals(
            "没表态那颗要报 false，不能报 null——null 等于读屏听不出差别，实到 " + off.describe(),
            false, off.selected
        )
    }

    /**
     * 禁用那一档：还是点得着"有这么一颗按钮"，只是按不动。
     *
     * 判的是**语义**，不是"点下去没反应"——`clickable(enabled = false)` 仍然是一个
     * 操作入口，只是当前不许按；把它从树里删掉（N=0 时不画）这一格就抓不到了。
     * 尺寸与名字都不能因为禁用而退化：禁用不等于可以小一点。
     */
    @Test
    fun `a disabled icon action keeps its name its role and its floor`() {
        var fired = 0
        mountVector("CLOSE_LABEL", onClick = { fired++ }, enabled = false)
        val target = probe.actionableTargets(rule, "LbTextAction 图标档（禁用）").single()
        assertTrue("禁用那一档得在树里报出 disabled：" + target.describe(), target.disabled)
        assertEquals("禁用不摘角色：" + target.describe(), "Button", target.role)
        assertEquals("禁用不摘名字：" + target.describe(), listOf("CLOSE_LABEL"), target.contentDescriptions)
        assertTrue("禁用不缩热区：" + target.describe(), !target.tooSmall(probe.floorDp))
        assertEquals("禁用那颗不该回调", 0, fired)
    }
}
