package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
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
 * §9（K09 窗口侧）：面板/谈心顶部那条**透明拖动区**到底打不打得到。
 *
 * 这一族把三轴分开量（§4.3 明写三者不许混为一谈）：
 * - 可见那一格 = 父布局占位 = 页面顶部那颗 4dp 槽位（[SLOT_TAG]，由测试自己包，生产不为此改）；
 * - 真实触控区域 = `DragHandle` 里面那一颗粒（[DRAG_HANDLE_HIT_TAG]，带子自己的边界）。
 *
 * 结构照搬宿主 `LoveBrainPanelScreen` 顶部那三段（`Column(padding vertical = Spacing.lg)` →
 * `Box(height = Spacing.sm){ DragHandle }` → `Spacer(Spacing.sm)` → `PanelHeader`），
 * 但**不引整屏 ViewModel**：这一格要的是带子的几何与手势归属，不是整屏装配。
 *
 * ⚠ 这台仪器证到的与证不到的，划清：
 * - 证得到：带子的实际边界（多高、上下各溢到哪儿、下沿与页头上沿的关系）、
 *   注入落在带子上半段是否真的交回窗口位移、短点击是否不交位移、页头 clickable 是否照旧赢。
 * - 证不到：真实手指宽下"这一条好不好命中"、悬浮窗在 WindowManager 里的真实移动、
 *   面板圆角外/窗外那一圈是否凭空拦下宿主的触摸、键盘与输入焦点是否被抢——
 *   全部挂在真机那一栏，不写成已验证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DragHandleHitBandTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density

    private fun toDp(px: Float): Float = px / density
    private fun toPx(dp: Dp): Float = dp.value * density

    private fun mount(
        moves: MutableList<Pair<Float, Float>> = mutableListOf(),
        headerDrags: (Int) -> Unit = {},
        modeChanges: (Int) -> Unit = {}
    ) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(Spacing.sm).testTag(SLOT_TAG)) {
                        DragHandle(onMove = { dx, dy -> moves.add(dx to dy) })
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Box(modifier = Modifier.fillMaxWidth().testTag(HEADER_SLOT_TAG)) {
                        PanelHeader(
                            panelMode = 0,
                            onModeChange = { modeChanges(it) },
                            onCollapse = {},
                            onHeaderDrag = { _, _ -> headerDrags(1) }
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun boundsOf(tag: String) =
        rule.onAllNodes(hasTestTag(tag)).onFirst().fetchSemanticsNode().boundsInRoot

    /**
     * ① 三轴各归各的：带子比可见那一格高出一大截，向上把面板顶部内边距那段真空白吃进来，
     * 下沿正好停在页头上沿——**不压**页头那两段、收起。
     *
     * 怎么坏会红：
     * - 带子退回等于槽位（本轮之前的样子：热区 = 可见 = 4dp）⇒ 第一句红；
     * - 只用 `requiredHeight` 朝下长（上侧空白没吃到）⇒ 第二句红（上沿没挪到内边距那一格）；
     * - 改成居中溢出（把一半浪费到下侧）或直接盖过页头 ⇒ 第三句红：透明热区压住模式按钮。
     */
    @Test
    fun `命中带吃的是邻居的真空白，一寸不压页头`() {
        mount()

        val slot = boundsOf(SLOT_TAG)
        val band = boundsOf(DRAG_HANDLE_HIT_TAG)
        val header = boundsOf(HEADER_SLOT_TAG)

        val slotDp = toDp(slot.height)
        val bandDp = toDp(band.height)
        assertTrue(
            "热区必须明显高于可见那一格：可见 ${slotDp.toInt()}dp，热区 ${bandDp.toInt()}dp",
            bandDp > slotDp * 2f
        )
        assertEquals(
            "热区高度就是 DragBand 里那颗数（不是注释里的一句话）",
            DragBand.hitHeight.value,
            bandDp,
            1.5f
        )
        assertEquals(
            "上侧溢出 = 面板根 Column 的顶部内边距那段真空白",
            toPx(DragBand.overhangAbove),
            slot.top - band.top,
            1.5f
        )
        assertTrue(
            "带子下沿不许越过页头上沿（透明热区不能覆盖模式按钮/收起）：" +
                "band.bottom=${band.bottom} header.top=${header.top}",
            band.bottom <= header.top + 1.5f
        )
        assertTrue(
            "带子与槽位之间那段 Spacer 也归热区（连续一条，不是两段）：" +
                "band.bottom=${band.bottom} slot.bottom=${slot.bottom}",
            band.bottom >= slot.bottom
        )
    }

    /**
     * ② 手指按在**可见那一格之外**（带子上半段 = 顶部内边距那段空白）横拖 ⇒ 窗口就该动，
     * 位移逐帧交回且与手指同向（§9"按下之后立即跟手"，不追赶不反向）。
     *
     * 怎么坏会红：
     * - 溢出那一侧打不到（带子只是画得大，命中仍留在 4dp 槽位里）⇒ `moves` 为空 ⇒ 红；
     * - 中间加了节流/缓动把位移吞掉或反号 ⇒ 同向那句红；
     * - 给整扇窗加了抢焦点的父手势 ⇒ 最后两句红（带子上半段被当成选模式/页头拖动）。
     */
    @Test
    fun `打在带子上半段（可见标记之外）就真的移动窗口`() {
        val moves = mutableListOf<Pair<Float, Float>>()
        var headerDrags = 0
        var modeChanges = 0
        mount(moves, { headerDrags += it }, { modeChanges++ })

        val band = boundsOf(DRAG_HANDLE_HIT_TAG)
        val slot = boundsOf(SLOT_TAG)
        // 落点：带子上半段的中点（节点内坐标）——换算到根坐标必须落在槽位之上
        val insideOverhang = Offset(band.width / 2f, (slot.top - band.top) / 2f)
        assertTrue(
            "落点该在可见那一格之外，否则这一格什么都没测：根 y=${band.top + insideOverhang.y} slot.top=${slot.top}",
            band.top + insideOverhang.y < slot.top
        )

        rule.onAllNodes(hasTestTag(DRAG_HANDLE_HIT_TAG)).onFirst().performTouchInput {
            down(insideOverhang)
            var p = insideOverhang
            repeat(6) {
                p = Offset(p.x + 12f * density, p.y)
                moveTo(p)
            }
            up()
        }
        rule.waitForIdle()

        assertTrue("带子上半段的横拖要产生窗口位移，实到 ${moves.size} 帧", moves.isNotEmpty())
        val totalDx = moves.sumOf { it.first.toDouble() }.toFloat()
        assertTrue("位移要与手指同向（跟手）：总 dx=$totalDx", totalDx > 0f)
        assertEquals("带子上半段不是页头拖动道", 0, headerDrags)
        assertEquals("带子上半段不吃模式两段的点击", 0, modeChanges)
    }

    /**
     * ③ 短点击不跳窗（§9 原话）：在带子上按下即抬、不走行程 ⇒ 一次位移都不交。
     *
     * 怎么坏会红：把手势改成"按下即动"（丢掉 touch slop）或在 `onDragStart` 里补一段位移 ⇒
     * 这里红（用户报的正是"短点击导致窗口突然跳走"）。
     */
    @Test
    fun `短点击不把窗口带跑`() {
        val moves = mutableListOf<Pair<Float, Float>>()
        var headerDrags = 0
        mount(moves, { headerDrags += it })

        val band = boundsOf(DRAG_HANDLE_HIT_TAG)
        val tap = Offset(band.width / 2f, band.height / 2f)

        rule.onAllNodes(hasTestTag(DRAG_HANDLE_HIT_TAG)).onFirst().performTouchInput {
            down(tap)
            up()
        }
        rule.waitForIdle()

        assertTrue("短点击不该产生任何窗口位移，实到 ${moves.size} 帧", moves.isEmpty())
        assertEquals("短点击也不该触发页头那一条拖动", 0, headerDrags)
    }

    /**
     * ④ 带子把空白吃走之后，页头那两段的点击仍归它们自己（§9"避开模式按钮、齿轮、关闭和输入区域"）：
     * 在页头第一段那一格点一下 ⇒ 切模式回调一次、窗口位移零帧。
     *
     * 怎么坏会红：带子拉长到盖住页头，或给整扇窗加了抢点击的父手势 ⇒
     * `modeChanges` 数到 0、`moves` 不为空，两句一起红。
     */
    @Test
    fun `带子不吃页头那两段的点击`() {
        val moves = mutableListOf<Pair<Float, Float>>()
        var modeChanges = 0
        mount(moves, modeChanges = { modeChanges++ })

        val header = boundsOf(HEADER_SLOT_TAG)
        val onFirstSegment = Offset(header.width * 0.3f, header.height / 2f)
        rule.onAllNodes(hasTestTag(HEADER_SLOT_TAG)).onFirst().performTouchInput {
            click(onFirstSegment)
        }
        rule.waitForIdle()

        assertEquals("页头那一段该照旧被点到（切模式回调一次），实到 $modeChanges", 1, modeChanges)
        assertTrue("点模式按钮不该顺带移动窗口，实到 ${moves.size} 帧", moves.isEmpty())

        // 页头那一行自己的可点节点：两段 + 收起 = 三颗，透明带没把它们并掉也没吃掉（这里没交齿轮，故三颗）
        val headerClickables = rule.onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .count { it.boundsInRoot.top >= header.top - 1.5f && it.boundsInRoot.bottom <= header.bottom + 1.5f }
        assertTrue(
            "页头行内的可点节点该数得到（两段 + 收起），实到 $headerClickables",
            headerClickables >= 3
        )
    }

    private companion object {
        const val SLOT_TAG = "drag_handle_slot_probe"
        const val HEADER_SLOT_TAG = "drag_handle_header_probe"
    }
}
