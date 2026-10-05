package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 状态卡那一格里**唯一**的可点出口：▶ / ■ 这颗控件（旧名"Hero 主动作"，形状已被换成用户指定的灯+三角/方块）。
 *
 * 这一格单独存在，是因为首页的合同很窄：一条状态卡里只许有一处操作，
 * 而"点下去到底开始还是停止"必须能被读屏说出来——形状是指定的（三角/方块），
 * 但无障碍不能因此只剩一个几何图形。
 *
 * 判的是组件自己（不挂整屏），所以矩阵、角色、禁用态这些读数都出自这一颗。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeHeroActionTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private fun mount(status: AdvisorStatus, matrix: UiMatrix = UiMatrix(360)) {
        rule.setContent {
            matrix.RenderIn(LocalDensity.current.density) {
                AssistantStatusCard(
                    render = status.render(),
                    onPlay = {},
                    onStop = {}
                )
            }
        }
        rule.waitForIdle()
    }

    /**
     * 一颗卡里只有一颗可点：灯是读数。
     * 反例：给灯补一处 clickable（"一处变两处"那个形状）⇒ `single()` 直接抛；
     * 反例：控件没挂 clickable ⇒ 这里连一颗都测不到，也红。
     */
    @Test
    fun `the card has exactly one actionable node`() {
        mount(AdvisorStatus(AdvisorState.Stopped))
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(
            "状态卡里只许有 ▶/■ 这一颗可点，实到 ${nodes.size} 颗：" +
                nodes.joinToString(" | ") { probe.of(it).describe() },
            1, nodes.size
        )
    }

    /**
     * 名字由合同写死："开始/停止军师服务"。这里**照用户那句话写字面量**，
     * 不引用生产那个常量——引用生产常量等于让被测对象替测试提供期望值（改名字一起改，判据就空了）。
     * 反例：红档那颗念"军师未启动"（灯的话被搬到按钮上）、或黄档仍是"开始"（形状与动作脱钩）。
     */
    @Test
    fun `the control says start when red and stop when yellow or green`() {
        val stopped = AdvisorStatus(AdvisorState.Stopped).render()
        val checking = AdvisorStatus(AdvisorState.Checking).render()
        val ready = AdvisorStatus(AdvisorState.RunningReady).render()
        val needs = AdvisorStatus(AdvisorState.RunningNeedsSetup, listOf(AdvisorMissing.NoProvider)).render()

        mount(AdvisorStatus(AdvisorState.Stopped))
        val hero = probe.assertAllActionableMeetTouchFloor(rule, "状态卡·红档").single()
        assertEquals("那颗必须自己说出开始", "开始军师服务", hero.label)
        assertEquals("那颗必须是按钮角色：" + hero.describe(), "Button", hero.role)
        assertFalse("唯一出口不许是禁用态：" + hero.describe(), hero.disabled)

        assertEquals("停止军师服务", checking.controlDescription)
        assertEquals("停止军师服务", ready.controlDescription)
        assertEquals("停止军师服务", needs.controlDescription)
        assertTrue("黄与绿的动作名字相同、灯色不同", checking.lamp != ready.lamp)
    }

    /**
     * 热区两轴都要够全站那一档，而且**不许把可见字形一起垫大**（三轴分离那条）。
     * 反例：给三角本体写 `size(48)` 而不是外层盒垫热区 ⇒ 卡片高度超过这一档；
     * 反例：外层盒被改成 wrap content ⇒ 下面那句 tooSmall 红。
     */
    @Test
    fun `the control keeps a forty-eight hit box without inflating the card`() {
        mount(AdvisorStatus(AdvisorState.Stopped), UiMatrix(320, fontScale = 2.0f))
        val hero = probe.assertAllActionableMeetTouchFloor(rule, "状态卡（320dp + 2 倍字）").single()
        assertTrue(
            "2 倍字下 ▶ 的热区两轴仍要够 48dp，实到 " + hero.describe(),
            !hero.tooSmall(TouchTier.SITE_FLOOR)
        )
        // 可见那一格仍然只是"一条"：卡片高度不超过 80dp（旧介绍卡是 200+dp 那一族）
        val controlRect = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().first().boundsInRoot
        assertTrue(
            "热区垫到 48 就够了，不许把可见那一格撑成一块大按钮（实到高 ${(controlRect.height / density).toInt()}dp）",
            controlRect.height / density <= 56f
        )
    }

    /**
     * §3.10 的闭合判据（源码级来路）：▶/■ 这两颗形状**必须由设计系统那颗圆角字形件画**，
     * 页面里不许再留一条自己拼的实心尖 Path。
     *
     * 为什么这一格按来路判而不是按像素判：本机没有设备、也没有可用的 AVD/system-image，
     * "三个角到底圆不圆"这件事在渲染侧永远量不到；而几何那一半已经由
     * `core/designsystem/LbTriangleGlyphGeometryTest` 按圆弧/切点/半径逐颗证过（纯 JVM、有反例）。
     * 这里补的是**接缝**：页面是不是真的走了那颗件。
     *
     * 反例：
     * - 有人把 `AdvisorPlayGlyph` 那种 `Path().apply { moveTo/lineTo/lineTo/close }` 抄回来
     *   ⇒ 前两句红（尖角重新出现在页面上，而且是在没有圆弧概念的地方）；
     * - 只把三角搬走、停止块仍旧自己 `background(Primary, LoveBrainShape.sm)` 涂一颗固定 6dp
     *   ⇒ "两颗形状同一个主人" 那句红（两形就变两种语言）；
     * - 把那颗件改成接受 `cornerRadius:` 或 `color:` 自由参数 ⇒ 本文件测不到，
     *   但 `LbTriangleGlyphGeometryTest` 第 1 格（比例只有一颗数）会先红。
     *
     * ⚠ 注释一律先掩平再判（`SourceScan.maskComments`）：本文件自己的说明里就写着
     * `Path` / `lineTo` 这两个词，不掩注释的尺会判自己红（坑表那一族"量具自己吃自己"）。
     */
    @Test
    fun `the play and stop shapes come from the shared rounded glyph, not a local sharp path`() {
        val root = java.io.File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: java.io.File("app/src/main/java/com/lovebrain/app")
        val file = java.io.File(root, "ui/home/HomeComponents.kt")
        assertTrue("找不到 $file——这把尺会恒绿", file.isFile)
        val code = com.lovebrain.app.core.testing.SourceScan.maskComments(file.readText(Charsets.UTF_8))

        assertEquals("页面里不许再自己拼 Path（§3.10 那颗件才是主人）", 0, Regex("\\bPath\\(\\)").findAll(code).count())
        assertEquals("页面里不许再自己 lineTo 出尖角", 0, Regex("\\blineTo\\(").findAll(code).count())
        assertEquals("页面里不许再自己 drawPath", 0, Regex("\\bdrawPath\\(").findAll(code).count())
        // 一颗调用点，两颗形状（三角 / 停止块）都从它走
        assertEquals("▶/■ 应各自交出形状档、共用一颗 LbTriangleGlyph(", 1, Regex("\\bLbTriangleGlyph\\s*\\(").findAll(code).count())
        assertTrue("朝右那颗没走 TriangleRight 档", code.contains("LbTriangleGlyphShape.TriangleRight"))
        assertTrue("停止那颗没走 StopSquare 档（同件同比例这条就断了）", code.contains("LbTriangleGlyphShape.StopSquare"))
        assertTrue("字形可见尺寸仍是交进去的那一档", code.contains("HomeDimens.GLYPH_VISIBLE_DP.dp"))
    }
}
