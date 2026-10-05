package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LB_GLYPH_CORNER_RATIO
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SourceScan
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
import kotlin.math.abs

/**
 * 面板那一族折叠指示三角（`ui/panel/DragHandle.kt#TriangleArrow`）换成公共件之后的读数。
 *
 * 这是"所有朝右三角形真圆角"那一族里，全站最后一颗仍留在页面里自绘的实心尖角
 * （同类形状审计的落点；清单与闸见 `architecture/SolidGlyphShapeAuditTest`）。
 * 旧体是一条 `Path().apply { moveTo → lineTo → lineTo → close }`，三颗顶点全是尖角。
 *
 * ⚠ **观感那一半没有在这里被验证，也不可能在这里被验证**：本机没有设备、也没有可用的
 * system-image，`drawPath` 落下的像素在 JVM 侧读不出来。这一格交的是**形状与参数判据**
 * （走哪一颗件、哪一档形、可见那一轴到底多宽、比例从哪一颗数来）；
 * 圆弧本身的数学由 `core/designsystem/LbTriangleGlyphGeometryTest` 逐颗钉
 * （半径 / 切点 / 垂直 / 夹取，10dp 那一档就是它的 `indicatorSide = 20px` 那两遍）。
 * "放大截图上三个角是不是真圆"仍然挂在真机那一栏，不写成已验证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DragHandleGlyphTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density

    private fun mount(matrix: UiMatrix = UiMatrix(360)) {
        rule.setContent {
            matrix.RenderIn(LocalDensity.current.density) {
                TriangleArrow(color = Primary, rotation = 0f, modifier = Modifier.testTag(TAG_PROBE))
            }
        }
        rule.waitForIdle()
    }

    /** 可见轴读数（`boundsInRoot` 是 px，这里翻成 dp） */
    private fun visibleSizeDp(): Pair<Float, Float> {
        val rect = rule.onNodeWithTag(TAG_PROBE).fetchSemanticsNode().boundsInRoot
        return (rect.width / density) to (rect.height / density)
    }

    // ═══════════ 1. 可见轴：换形状没把这颗键撑大 ═══════════

    /**
     * 公共件的边长就是交进去的 `sizeDp`（切角只往里收），所以可见盒必须**正好**是 core 已有那一颗
     * `AppDimens.ARROW_SIZE_DP`（10dp）。
     *
     * ⚠ 同一颗控件要在两个矩阵档下量 ⇒ **两颗用例**，不是一格里 `forEach` 重挂两次：
     * `createComposeRule` 每个用例只许调一次 `setContent`（第二次抛
     * `Cannot call setContent twice per test!`，这一格原本报的就是它）。两档都不许丢，判据逐字相同。
     *
     * 反例：有人为了"看着更圆"把描边加粗（形状整体外扩 width/2）或顺手把 `sizeDp` 抬到 12/16
     * ⇒ 两格都红；反例：把 `size()` 丢了让 Canvas 吃满父约束 ⇒ 宽度直接红到几百 dp。
     */
    @Test
    fun `the visible box is exactly the ten dp tier at the standard matrix`() {
        assertVisibleBoxIsTheTenDpTier(UiMatrix(360))
    }

    /** 最窄 + 最大字那一档：同一颗 10dp 可见轴（这一颗件不跟字号长） */
    @Test
    fun `the visible box is exactly the ten dp tier at the worst-case matrix`() {
        assertVisibleBoxIsTheTenDpTier(UiMatrix(320, fontScale = 2.0f))
    }

    /** 两档共用的那一把尺：可见宽高都读 `AppDimens.ARROW_SIZE_DP`，容差 0.6dp */
    private fun assertVisibleBoxIsTheTenDpTier(matrix: UiMatrix) {
        mount(matrix)
        val (w, h) = visibleSizeDp()
        val want = AppDimens.ARROW_SIZE_DP.toFloat()
        assertTrue(
            "${matrix.id} 下可见宽应为 ${want}dp，实到 ${w}dp×${h}dp（外扩或撑满父约束都不是切角）",
            abs(w - want) <= 0.6f && abs(h - want) <= 0.6f
        )
    }

    // ═══════════ 2. 三轴分离：这一颗只占可见那一轴 ═══════════

    /**
     * 热区不在字形上：体里没有 `clickable`，可点那一轴仍归调用方那一排（谈心页那颗 `LbChip` 动作）。
     * 所以这一屏里**一颗可点节点都不许出现**，而可见盒也不该被垫到 48dp 那一档——
     * 真垫上去就是把"可见"与"热区"两根轴重新绑回一件事（§3.1 明令禁止的那种叠法）。
     *
     * 反例：给 `TriangleArrow` 自己加 `clickable` 或 `heightIn(min = TOUCH_TARGET_MIN_DP)` ⇒ 两句都红。
     */
    @Test
    fun `the glyph carries no hit zone and is not padded up to the touch floor`() {
        mount()
        assertEquals(
            "折叠指示三角自己不许可点：热区归外面那颗可点盒子",
            0, rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        )
        val (w, h) = visibleSizeDp()
        assertTrue(
            "可见轴读数不该等于热区档（$w×$h）：48 那一档由外面那颗盒子负责，不靠这里垫高",
            w < AppDimens.TOUCH_TARGET_MIN_DP.toFloat() && h < AppDimens.TOUCH_TARGET_MIN_DP.toFloat()
        )
    }

    // ═══════════ 3. 旋转仍由调用方拿着：布局那一格感觉不到它 ═══════════

    /**
     * 角度语义一个字没改（0 / −90 两值仍出自 `CounselingPanel.kt:246-250`），旋转挂在字形自己的
     * `graphicsLayer` 上——那是**绘制期**的一层，布局那一格一寸都不该动。
     *
     * 用控制组摆读数：同一个居中盒子里叠三颗（转 0° 的、转 −90° 的、以及一颗**不画形的 10dp 空盒**），
     * 三者的槽位必须逐字相同。空盒那颗是**死尺证人**：一把量不到位置的尺也会让"相等"恒真，
     * 而它必须同时落进同一格，这条读数才算数。
     *
     * 反例：把 `graphicsLayer` 换成"另起一层带尺寸/带 padding 的容器" ⇒ 那颗的槽位与另两颗对不上，红。
     * ⚠ 箭头到底画成朝上还是朝下（像素）仍归真机那一栏：`graphicsLayer` 的旋转角在 JVM 侧读不到。
     */
    @Test
    fun `rotation is a draw time layer the layout slot does not feel`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Box(contentAlignment = Alignment.Center) {
                    TriangleArrow(color = Primary, rotation = 0f, modifier = Modifier.testTag(TAG_STRAIGHT))
                    TriangleArrow(color = Primary, rotation = -90f, modifier = Modifier.testTag(TAG_ROTATED))
                    Box(modifier = Modifier.size(AppDimens.ARROW_SIZE_DP.dp).testTag(TAG_CONTROL))
                }
            }
        }
        rule.waitForIdle()
        val straight = rule.onNodeWithTag(TAG_STRAIGHT).fetchSemanticsNode().boundsInRoot
        val rotated = rule.onNodeWithTag(TAG_ROTATED).fetchSemanticsNode().boundsInRoot
        val control = rule.onNodeWithTag(TAG_CONTROL).fetchSemanticsNode().boundsInRoot
        val side = AppDimens.ARROW_SIZE_DP.toFloat()

        // 死尺证人：控制组必须量到 10dp 那一档；量不到的话，下面六句"相等"全是恒真
        assertEquals(
            "同档的一颗空盒量出来不是 ${AppDimens.ARROW_SIZE_DP}dp——这把尺是死的",
            side, control.width / density, 0.6f
        )
        assertEquals(side, control.height / density, 0.6f)

        assertEquals("转 −90° 之后宽度漂了", straight.width, rotated.width, 0.5f)
        assertEquals("转 −90° 之后高度漂了", straight.height, rotated.height, 0.5f)
        assertEquals("转 −90° 之后左沿漂了", straight.left, rotated.left, 0.5f)
        assertEquals("转 −90° 之后顶沿漂了", straight.top, rotated.top, 0.5f)
        assertEquals("字形那颗与同档空盒不同位（可见那一格被这次改造挪过）",
            control.left, straight.left, 0.5f)
        assertEquals("字形那颗与同档空盒不同位（同上，纵轴）",
            control.top, straight.top, 0.5f)
    }

    // ═══════════ 4. 比例只从那一颗常量来，页面拿不到旋钮 ═══════════

    /**
     * 10dp 这一档的名义半径 = 10 × [LB_GLYPH_CORNER_RATIO] = **1.8dp**。
     * 这里判的是来路，不是像素：旧的容器档 `LoveBrainShape.sm` 是固定 6dp，1.8 ≠ 6 ⇒
     * 说明这一档读的是比例而不是那颗固定半径；同一条算式在 20dp 那一档给出 3.6dp
     * （首页 hero 那颗），两档共用一颗常量 ⇒ 全站只有一种"实心字形有多圆"。
     *
     * 反例：把比例抄成页面里的固定数（`0.18f`）或给这一颗补一颗 `cornerRadius` 旋钮 ⇒ 后三句红；
     * 反例：有人把形状重新画回页面（`Canvas(` / `RoundedCornerShape`）⇒ 最后一句红。
     */
    @Test
    fun `the ten dp tier reads one ratio instead of a copied fixed radius`() {
        val nominalDp = AppDimens.ARROW_SIZE_DP.toFloat() * LB_GLYPH_CORNER_RATIO
        assertEquals("10dp 档的名义半径", 1.8f, nominalDp, 1e-5f)
        assertFalse(
            "圆角被抄成容器那颗固定 6dp（同一族在不同尺寸上就会硬软不一）",
            abs(nominalDp - 6f) < 1e-3f
        )
        assertEquals("20dp 档共用同一颗常量", 3.6f, 20f * LB_GLYPH_CORNER_RATIO, 1e-5f)

        val code = SourceScan.maskComments(productionSource())
        assertTrue("比例数被抄进页面了", !code.contains("0.18f"))
        assertTrue("页面自己带圆角旋钮", !code.contains("cornerRadius"))
        assertTrue(
            "页面又想自己画形状（Canvas 与 RoundedCornerShape 都不该出现在这一颗里）",
            !code.contains("RoundedCornerShape") && !code.contains("Canvas(")
        )
    }

    /** 生产源文件本体（判据读的是来路，不是编译产物） */
    private fun productionSource(): String {
        val file = java.io.File("src/main/java/com/lovebrain/app/ui/panel/DragHandle.kt").takeIf { it.isFile }
            ?: java.io.File("app/src/main/java/com/lovebrain/app/ui/panel/DragHandle.kt")
        assertTrue("找不到 $file——这把尺会恒绿", file.isFile)
        return file.readText(Charsets.UTF_8)
    }

    private companion object {
        const val TAG_PROBE = "lb_panel_triangle_arrow_probe"
        const val TAG_STRAIGHT = "lb_panel_triangle_arrow_straight"
        const val TAG_ROTATED = "lb_panel_triangle_arrow_rotated"
        const val TAG_CONTROL = "lb_panel_triangle_arrow_control"
    }
}
