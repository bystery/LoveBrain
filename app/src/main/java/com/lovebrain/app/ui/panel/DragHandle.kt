package com.lovebrain.app.ui.panel

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LbTriangleGlyph
import com.lovebrain.app.core.designsystem.LbTriangleGlyphShape
import com.lovebrain.app.core.designsystem.LbTriangleGlyphTone
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint

/**
 * 顶部拖动那一格的**三轴**（§4.3：可见尺寸、父布局占位、真实触控区域，不允许再混为一谈）。
 *
 * 这一族数值一颗没变大（用户明说不许把 `hitHeight` 从 20 一路改成 30/40/50 了事），
 * 换的是**谁来占这一格**：从前是外面那只 4dp 的盒子占、带子靠自定义布局向上下溢出画到 20dp；
 * 现在带子自己就是那 20dp 的容器，溢出那一套整颗删掉。这么改的理由写在这一格文件头上。
 */
internal object DragBand {

    /**
     * 可见那一格 = 顶部那条细槽的占位（[Spacing.sm] = 4dp）。
     *
     * 它现在住在带子**里面**（上面隔 [overhangAbove]，下面隔 [overhangBelow]），
     * 尺寸与绝对像素位置与从前逐字相同：用户一路把顶部从 22dp → 14dp → 8dp → 4dp 收下来，
     * 这一轮同样不把它画粗。
     */
    val slot: Dp = Spacing.sm

    /**
     * 槽位之上真实存在的空白 = 面板根 `Column` 从前那段顶部内边距（`vertical = Spacing.lg`）。
     * 这一档现在由带子**自己占进布局**（宿主那一格的内边距交回给容器），不是画出去的。
     */
    val overhangAbove: Dp = Spacing.lg

    /**
     * 槽位之下真实存在的空白 = 从前页头之前那颗 `Spacer(Spacing.sm)`。
     * 带子的下沿因此正好停在页头上沿：**不压**模式两段、齿轮、收起，也不压输入区。
     */
    val overhangBelow: Dp = Spacing.sm

    /**
     * 主面顶部：容器真实占位 = 命中高度 = 12 + 4 + 4 = **20dp**（数值与从前一样）。
     *
     * ⚠ 为什么不是 §9 里"可先以 24–32dp 高的空白带调试"的那一档：再往上只有两条路，
     * 一条是凭空给页面顶部加一条空白（书里明写不要求），
     * 一条是让透明带盖住页头那两段的 clickable（书里明写"透明热区不能覆盖它们"）。
     * 两条都越线。这一轮的活不是把这一颗数抬高，而是让**这一颗数真的被人摸得到**。
     */
    val hitHeight: Dp = overhangAbove + slot + overhangBelow

    /**
     * 设置页顶部：那一格上面同样有 12dp 内边距，但**页头之前没有那颗 Spacer**，
     * 所以下沿那一档 [overhangBelow] 拿不到 ⇒ 容器只许占 16dp。
     * 拿不到 4dp 就少 4dp：不许为了两处共用一颗数而把设置页整页往下顶 4dp。
     */
    val settingsHitHeight: Dp = overhangAbove + slot
}

/**
 * 命中带的测试锚点。
 *
 * 挂在**容器自己**身上：这只盒子占多高、就被摸多大，两个读数必须长在同一颗粒上。
 * 从前它挂在里面那颗溢出的粒子上——那颗量到 20dp 也只证明"画得出 20dp"，
 * 不证明父链给了那一格位置、更不证明那一格在兄弟节点之上还收得到触摸。
 */
internal const val DRAG_HANDLE_HIT_TAG = "panel_top_drag_hit_band"

/**
 * 可见那一格（4dp 细槽）的锚点：判据拿它钉"容器长高了，可见那一寸没跟着长"。
 *
 * 它是带子的**孩子**（不是带子的父亲）——这一条父子关系本身就是本轮的验收面：
 * 统计条那一样东西必须挂在带子里面，手势才由父子层级分家，而不是靠兄弟排序抢命。
 */
internal const val DRAG_HANDLE_SLOT_TAG = "panel_top_drag_visible_slot"

/**
 * 顶部拖拽条：按住即可移动悬浮窗（§8.4 表末行"顶部明确空白拖动区域 = 移动悬浮窗"）。
 *
 * 三轴各归各的（数值都在 [DragBand]）：
 * - 可见：仍是 [DragBand.slot] 那一寸细槽，体里不画箭头/横线装饰，也没为好拖把它画粗；
 * - 命中：**容器自己**就是 [hitHeight] 高的一格，占进布局、由父链真给位置；
 * - 布局占位：与命中同一颗数 ⇒ 这一轮的三轴里"占位"与"命中"并成一条，
 *   这正是修的东西：从前它俩差着 16dp，那 16dp 只是画出来的，不是打得到的。
 *
 * ⚠ 为什么把上一版的"自定义布局溢出"整颗删掉（用户 2026-10 报"顶部透明拖动区难拖"的落点）：
 * 溢出那一套在 Compose 里靠的是"父节点没开 clip 时命中会往界外的孩子里继续走"，
 * 而同一只 4dp 盒子里还叠着统计条那一行（`stats/UsageStatBar`，`fillMaxWidth` + 一行高）。
 * 兄弟节点的命中是**按 z 序倒着走、第一个有命中的那一支就走完整条路**：统计条排在带子之后，
 * 于是它那一行的矩形之内（整条带宽、一行高）手指事件根本不会进到带子那一支，
 * 带子只剩下最上面那几 dp；再碰上统计条"放不下"而装上横向拖拽时，横着拖窗口的那一下
 * 还被它整片接走去做换组。用户摸到的就是"这一条几乎拖不动"。
 * ⇒ 这一格改两件事：①容器自己真占 20dp（不再靠画）；②统计条挂进带子**里面**，
 * 父子各持一手势：子层只认领横向（换组），父层认领拖动 ⇒ 竖向拖动在统计那一行上也照样移动窗口。
 * 两颗 owner 谁也不吞谁，但**认领的范围**要说准（2026-10-10 实测，证人
 * `DragHandleHitBandTest`）：Compose 1.6 的指针分发不是按子层自己那格矩形算的，
 * 而是只要父层那颗 LayoutNode 被命中，子树的 pointer-input 节点一起进命中链
 * （`LayoutNode.hitTest` 收集子树那颗集合）⇒ 统计那一格**装了换组手势**时，
 * 整条带子的横向都先归它，带子拿剩下那一轴（竖向）；带子那一段的横向只有在统计那一格
 * 不装手势（一行放得下、`gestureEnabled` 为假）时才真的归窗口。
 * 从前那句"带子左右那几 dp 的横向拖动仍然归窗口"是不成立的形状假设，已由实测改回。
 *
 * 时序（§9"按下之后立即跟手，短点击不导致窗口突然跳走"）：
 * 用 [detectDragGestures]，起手有系统 touch slop ⇒ 短点击不产生位移，窗口不会跳；
 * 过线之后每一帧的 `dragAmount` 直接交回 [onMove]，中间没有动画也没有节流（宿主的
 * `OverlayPanelWindow.move` 逐次 updateLayout），所以是跟手而不是追赶。
 * 这里**没有**给整扇窗加父手势（§8.4 正文）：横滑切页、长按重排、输入焦点仍归各自那一层。
 *
 * 页头那一行自己也是拖动宿主（`PanelHeader.onHeaderDrag`），管的是页头**内部**那些没被
 * clickable 占走的空白（齿轮/收起外包盒上下那几 dp、两段左右那 8dp）；与这条带子上下相接、不重叠。
 *
 * @param content 挂在带子**里面**的那一样东西（面板顶部只有统计条那一行）。用它而不在宿主那边
 *        另起兄弟盒子，是为了让手势归属由父子层级决定：里面的节点先收事件、只认领它自己的轴，
 *        剩下的（含同一矩形内的竖向拖动）才归带子。
 */
@Composable
fun DragHandle(
    onMove: (dxPx: Float, dyPx: Float) -> Unit,
    modifier: Modifier = Modifier,
    hitHeight: Dp = DragBand.hitHeight,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hitHeight)
            .testTag(DRAG_HANDLE_HIT_TAG)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount.x, dragAmount.y)
                    }
                )
            },
        content = content
    )
}

/**
 * 展开/收起那一族用的**实心三角**：形状交回设计系统那颗 [LbTriangleGlyph]（基线 v1 §3.10）。
 *
 * 旧体是这一颗文件自己拼的尖 Path（`moveTo(w/2,h) → lineTo(w,0) → lineTo(0,0) → close()`），
 * 三个顶点全是尖角。用户第 6 条（"所有朝右三角形的三个角都要真圆角，不许是尖角"）点名的
 * 是**实心字形**这一族，而全站页面里只剩这一颗还在自己画尖角——所以它换。
 * 公共件的顶点表正是从这段旧 Path 逐字抄过去的（`LbTriangleGlyphGeometryTest` 第 2 格钉着），
 * 于是外形与重心一寸没挪，只是三个角各往里退掉一段切角长（10dp 那一档：半径 1.8dp，不被夹取）。
 *
 * 三轴分离（§3.1）：这里只有**可见尺寸**那一轴（`AppDimens.ARROW_SIZE_DP` = 10dp），
 * 字形自己不垫热区、体里没有 clickable——热区归调用方那一排（谈心页那颗 `LbChip` 动作）。
 * 方向语义一个字不改：旋转角仍由调用方拿着，挂在字形自己的 `graphicsLayer` 上；
 * 用的是 `TriangleDown` 那一档，**不许**顺手换成播放键 `TriangleRight`（§3.10 明写：
 * 展开/折叠指示器不是播放形状）。
 *
 * ⚠ 旧的 `color: Color` 参数**本轮保留**：唯一调用方 `counseling/CounselingPanel.kt:303`
 * 交进来的是 `Primary`，而那颗文件的写入权在 S1b 席手上，本席不跨区动它。
 * 这里只做一次"收敛"——把页面自由挑的墨色折回公共件词表的两档之一，
 * **不是**把 `color` 透给字形（公共件体里没有 `color:` 旋钮，也不该有）。
 * 把这颗参数整族收掉是下一拍的账（已写进 impl-H1c 台账）。
 */
@Composable
fun TriangleArrow(
    color: Color,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    LbTriangleGlyph(
        sizeDp = AppDimens.ARROW_SIZE_DP.dp,
        tone = lbTriangleArrowTone(color),
        shape = LbTriangleGlyphShape.TriangleDown,
        modifier = modifier.graphicsLayer { rotationZ = rotation }
    )
}

/** 旧的颜色口子折回墨色词表：认得出 `TextHint` 走 Muted，其余按今天那一档 Accent（= `Primary`） */
private fun lbTriangleArrowTone(color: Color): LbTriangleGlyphTone =
    if (color == TextHint) LbTriangleGlyphTone.Muted else LbTriangleGlyphTone.Accent
