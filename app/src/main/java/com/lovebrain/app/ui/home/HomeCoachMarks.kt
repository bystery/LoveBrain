package com.lovebrain.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.LbTriangleGlyph
import com.lovebrain.app.core.designsystem.LbTriangleGlyphShape
import com.lovebrain.app.core.designsystem.LbTriangleGlyphTone
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.viewmodel.GuideCursor
import kotlin.math.roundToInt

/**
 * 首页**覆盖式**引导：一层罩子 + 一支指向"当前这一步该点的那颗"的箭头
 * （用户原话第 14 条「引导一翻页就没了，能不能做成直接主页面上覆盖几个箭头指向的那种引导」）。
 *
 * 它不是一次性向导。画不画、指哪儿，**只看宿主交进来的 [GuideCursor]**——那颗游标由
 * `SetupViewModel.currentGuideCursor(context)` 从真状态派生。这一层不判"哪一步没做"，
 * 只把游标翻译成锚点键（[coachAnchorKeyFor]）与那一格的字（[CoachStepCopy]）。
 *
 * 数值按设计基线 v1.1 §6.7：遮罩 0.55 黑、目标描边 2dp `Primary` 外扩 4dp、
 * 箭头 12×8 每角圆、提示板宽 ≤260dp、内边距 12、圆角 16、优先放目标下方。
 *
 * 指向那一格的形状**不自己画**：用 core 的公共件 `LbTriangleGlyph`（§3.10 那颗圆化三角，
 * 半径比例由它独享，页面拿不到），见 [CoachPointerSlot]。
 */

/**
 * 罩子这一族的自动化锚点。判据按 tag 认、不拿文案当锚（与 [LbHomeTags] 同一口径）：
 * 本仓库那条仪器事实是"截断时语义树仍报完整原文"，所以文本判据没有牙，几何才作数。
 */
object LbCoachTags {
    const val SCRIM = "lb_coach_scrim"
    const val POINTER = "lb_coach_pointer"
    const val PLATE = "lb_coach_plate"
    const val OPEN_TARGET = "lb_coach_open_target"
    const val DEFER = "lb_coach_defer"
    const val STOP = "lb_coach_stop"
}

/**
 * 一格的六句字，**全部由宿主从 `R.string` 取**：这一层不写一个中文字面量。
 * [plateDescription] 是提示板对读屏报的名字（`contentDescription` 走资源，英文环境才不念中文）。
 */
data class CoachStepCopy(
    val plateDescription: String,
    val title: String,
    val body: String,
    val actionLabel: String,
    val deferLabel: String,
    val stopLabel: String
)

/**
 * 锚点账：被指的入口把自己在根坐标里的矩形登记进来，罩子只读这一本账。
 *
 * 为什么是一本账而不是一路传参：入口住在 `HomeScreen`（`SetupRoot` 的孙子），
 * 宿主 `SetupActivity` 与它们之间没有参数通道，[LocalCoachAnchorRegistry] 就是那条通道。
 * 入口那一侧的挂法是 [coachAnchor]，与已有的 `testTag` 并排在同一条链上。
 */
class CoachAnchorRegistry {

    private val bounds = mutableStateOf<Map<String, Rect>>(emptyMap())

    fun rectFor(key: String): Rect? = bounds.value[key]

    fun register(key: String, rect: Rect) {
        if (bounds.value[key] != rect) bounds.value = bounds.value + (key to rect)
    }

    fun release(key: String) {
        if (bounds.value.containsKey(key)) bounds.value = bounds.value - key
    }

    /** 换页/离场清账：留着上一屏的矩形会把箭头指到根本没摆出来的地方 */
    fun clear() {
        if (bounds.value.isNotEmpty()) bounds.value = emptyMap()
    }

    companion object {
        /**
         * 生产共用的那一本账（`SetupRoot` 没有参数通道把实例递进 `HomeScreen`）。
         * 测试自己造实例并经 [LocalCoachAnchorRegistry] 提供，不碰这一颗。
         */
        val Shared = CoachAnchorRegistry()
    }
}

/** 覆盖层读哪本账、入口往哪本账写，由这一颗 Local 决定（默认 = 生产共用那本） */
val LocalCoachAnchorRegistry = compositionLocalOf { CoachAnchorRegistry.Shared }

/**
 * 把一颗入口挂进遮罩引导的锚点账。**这是给 `HomeScreen` 四入口留的接线位**：
 * 现有 `.testTag(LbHomeTags.ENTRY_*)` 后面并一句 `.coachAnchor(LbHomeTags.ENTRY_*)` 就通
 * （`HomeScreen.kt` 本轮不归本席，改动落交接单 §1）。
 */
@Composable
fun Modifier.coachAnchor(key: String): Modifier {
    val registry = LocalCoachAnchorRegistry.current
    return this.then(
        Modifier.onGloballyPositioned { coordinates ->
            // 节点摘下时 Compose 仍会以 isAttached=false 回调一次，正好用来销账
            if (coordinates.isAttached) {
                registry.register(key, coordinates.boundsInRoot())
            } else {
                registry.release(key)
            }
        }
    )
}

/**
 * 游标 → 该指的入口锚点。**这张表只认 [GuideCursor] 的返回值**，不在这里判事实。
 *
 * 无障碍与捕获两格都指首页「消息捕获」那颗：授权与开关同住 `CaptureAppsScreen`
 * （见 `HomeScreen.kt` 顶部"被删掉的那些去哪了"那一段），首页没有第二颗无障碍入口可指。
 */
internal fun coachAnchorKeyFor(cursor: GuideCursor): String? = when (cursor) {
    GuideCursor.PROVIDER -> LbHomeTags.ENTRY_PROVIDER
    GuideCursor.ACCESSIBILITY -> LbHomeTags.ENTRY_CAPTURE
    GuideCursor.CAPTURE -> LbHomeTags.ENTRY_CAPTURE
    GuideCursor.NONE, GuideCursor.DONE, GuideCursor.DEFERRED_TO_HINT -> null
}

/**
 * 罩子这一族的**可见尺寸**（三轴分离：热区不在这里——三颗动作全借设计系统的件，
 * 因此指回 `AppDimens.TOUCH_TARGET_MIN_DP` 那一颗）。每一颗注明基线出处。
 */
private object CoachMarksDimens {
    /** 遮罩黑度（§6.7 定版 0.55） */
    const val SCRIM_ALPHA = 0.55f

    /** 目标描边宽（§6.7 定版 2dp；全站细描边那颗 1dp 是 `AppDimens.BORDER_WIDTH_DP`，用途不同） */
    const val HIGHLIGHT_STROKE_DP = 2

    /** 箭头字形的两条可见边（§6.7 定版 12×8dp：外盒这一档，字形取等边的 8dp 那一档） */
    const val ARROW_WIDTH_DP = 12
    const val ARROW_HEIGHT_DP = 8

    /** 提示板可见宽上限（§6.7 定版 ≤260dp） */
    const val PLATE_MAX_WIDTH_DP = 260

    /**
     * "往下放还是往上翻"那一格的估算高（§6.7 只说优先放目标下方，没给预留值）。
     * 它**不是**提示板的排版高——板的落点公式里板高自己抵消（见 [CoachHintPlate] 那条偏移）。
     */
    val PLATE_RESERVE = 120.dp
}

/**
 * 首页遮罩引导本体。**画不画、指哪儿只由 [cursor] 与 [copy] 决定**：
 * `NONE / DONE / DEFERRED_TO_HINT` 三档一格都不画（缺项交回首页黄字行，那一行不归本席）。
 *
 * 锚点没接上（`HomeScreen` 还没挂 [coachAnchor]）时退化成**不遮不挡**的提示板：引导仍在场、
 * 字仍指同一格，只是没有罩子与箭头——这是本轮的可见边界，补一行接线整块回来（交接单 §1）。
 *
 * @param onOpenTarget 提示板主动作。本席改不了 `SetupRoot` 的导航参数，这颗今天的语义是
 *   "让路"（罩子不再吃掉目标格之外的点击）；直达导航待主线程接线（交接单 §3）。
 */
@Composable
fun HomeCoachMarks(
    cursor: GuideCursor,
    copy: CoachStepCopy?,
    onOpenTarget: () -> Unit,
    onDefer: () -> Unit,
    onStopGuiding: () -> Unit,
    modifier: Modifier = Modifier,
    registry: CoachAnchorRegistry = LocalCoachAnchorRegistry.current
) {
    val anchorKey = coachAnchorKeyFor(cursor)
    if (anchorKey == null || copy == null) return

    // "去设置"按过一次就让路：罩子此后不再吃掉目标格之外的点击，用户的手指直接落到被亮的那一格。
    // 游标一换（从子页回来重算）这面就复位，引导本身不消失。
    var pathCleared by remember(cursor) { mutableStateOf(false) }
    var overlayBounds by remember { mutableStateOf<Rect?>(null) }
    val anchorRoot = registry.rectFor(anchorKey)
    // 罩子铺满宿主那一层，根原点就是它自己的原点：量到之前按 (0,0) 摆，量到之后同一把尺换算，
    // 两头都不会跳一次位置，也不会出现"锚点有、罩子还没量到自己"因而画不出来的死角
    val origin = overlayBounds?.topLeft ?: Offset.Zero
    // 锚点记的是根坐标；减去罩子自己的根原点就换成罩板内部的坐标
    val target = if (anchorRoot == null) {
        null
    } else {
        Rect(
            left = anchorRoot.left - origin.x,
            top = anchorRoot.top - origin.y,
            right = anchorRoot.right - origin.x,
            bottom = anchorRoot.bottom - origin.y
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayBounds = it.boundsInRoot() },
        contentAlignment = Alignment.TopStart
    ) {
        val density = LocalDensity.current
        val plateWidth = minOf(maxWidth, CoachMarksDimens.PLATE_MAX_WIDTH_DP.dp)
        val plateWidthPx = with(density) { plateWidth.toPx() }
        val arrowWidthPx = with(density) { CoachMarksDimens.ARROW_WIDTH_DP.dp.toPx() }
        val arrowHeightPx = with(density) { CoachMarksDimens.ARROW_HEIGHT_DP.dp.toPx() }
        val outsetPx = with(density) { Spacing.sm.toPx() }
        val gapPx = with(density) { Spacing.md.toPx() }
        val reservePx = with(density) { CoachMarksDimens.PLATE_RESERVE.toPx() }
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }
        // 目标与提示板之间那一摞：外扩 4 + 箭头高 + 8dp 间距
        val stackPx = outsetPx + arrowHeightPx + gapPx

        // 往下放不下就整摞翻到目标上方；判定只量目标位置与可用高，不量提示板自己
        val placeBelow = target == null ||
            target.bottom + stackPx + reservePx <= boxHeightPx

        // 200ms alpha 渐入：游标落到这一步时遮罩从透明渐入到 0.55，不再硬切。
        // （退出时游标走到 NONE/DONE → anchorKey 为 null → 早退，组件从树里摘掉 = 无退出动画。
        // 要做退出动画得改成"组件留在树里 + visible=false"那一套，但那会破"NONE 不画"的合同。）
        val scrimAlpha by animateFloatAsState(
            targetValue = CoachMarksDimens.SCRIM_ALPHA,
            animationSpec = tween(200),
            label = "scrim_alpha"
        )

        if (target != null) {
            CoachScrimLayer(
                target = target,
                swallowOutsideTarget = !pathCleared,
                scrimAlpha = scrimAlpha,
                modifier = Modifier.matchParentSize()
            )
            CoachPointerSlot(
                target = target,
                pointsUp = placeBelow,
                leftPx = (target.center.x - arrowWidthPx / 2f)
                    .coerceAtMost(boxWidthPx - arrowWidthPx)
                    .coerceAtLeast(0f),
                boxHeightPx = boxHeightPx,
                outsetPx = outsetPx,
                alpha = scrimAlpha,
                modifier = Modifier.align(if (placeBelow) Alignment.TopStart else Alignment.BottomStart)
            )
        }

        CoachHintPlate(
            copy = copy,
            placeBelow = placeBelow,
            target = target,
            leftPx = if (target == null) 0f else (target.center.x - plateWidthPx / 2f)
                .coerceAtMost(boxWidthPx - plateWidthPx)
                .coerceAtLeast(0f),
            plateWidth = plateWidth,
            stackPx = stackPx,
            boxHeightPx = boxHeightPx,
            alpha = scrimAlpha,
            onOpenTarget = {
                pathCleared = true
                onOpenTarget()
            },
            onDefer = onDefer,
            onStopGuiding = onStopGuiding,
            // 无锚点退路：贴底居中，不遮不挡不画箭头，字与三条出口都还在场
            modifier = if (target == null) {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.xxl)
            } else {
                Modifier.align(if (placeBelow) Alignment.TopStart else Alignment.BottomStart)
            }
        )
    }
}

/**
 * 遮罩：一张 `Path` 以 EvenOdd 差集挖出目标那一格（§6.7"单张 Canvas/Path 差集挖洞"）。
 * 洞 = 目标外扩 4dp，圆角与提示板同取 `LoveBrainShape.lg`（描边圆角等于填充圆角，§3.4）。
 *
 * 点击口径：洞**里**那一下一个字都不消费，让被指的那颗入口自己收到；洞外归罩子。
 * 提示板里三颗动作各自先消费（[awaitFirstDown] 默认只等未被消费的那一下）。
 */
@Composable
private fun CoachScrimLayer(
    target: Rect,
    swallowOutsideTarget: Boolean,
    scrimAlpha: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val outset = with(density) { Spacing.sm.toPx() }
    val hole = rectOutside(target, outset)
    val corner = cornerPxOf(LoveBrainShape.lg, hole.size, density)
    val stroke = with(density) { CoachMarksDimens.HIGHLIGHT_STROKE_DP.dp.toPx() }

    Canvas(
        modifier = modifier
            .testTag(LbCoachTags.SCRIM)
            .pointerInput(target to swallowOutsideTarget) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (swallowOutsideTarget && !target.contains(down.position)) {
                        down.consume()
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { if (it.pressed) it.consume() }
                            pressed = event.changes.any { it.pressed }
                        }
                    }
                    // 洞里的这一下：原样往下传，罩子不替用户点
                }
            }
    ) {
        drawPath(
            path = Path().apply {
                fillType = PathFillType.EvenOdd
                // 这一族 API 只收对象形式（`addRect(Rect)` / `addRoundRect(RoundRect)`），
                // 没有四个 float 的重载——写 float 列表就是这一片编译红的原形。
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(
                    RoundRect(hole.left, hole.top, hole.right, hole.bottom, corner, corner)
                )
            },
            color = Color.Black.copy(alpha = scrimAlpha)
        )
        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(hole.left, hole.top, hole.right, hole.bottom, corner, corner)
                )
            },
            color = Primary,
            style = Stroke(width = stroke)
        )
    }
}

/** 目标矩形外扩一圈（自己算四条边，不引第二把 inflate 尺） */
private fun rectOutside(rect: Rect, by: Float): Rect = Rect(
    left = rect.left - by,
    top = rect.top - by,
    right = rect.right + by,
    bottom = rect.bottom + by
)

/** `LoveBrainShape.lg` 的圆角换成 px：半径从形状那一处来，罩子不另抄一份数 */
private fun cornerPxOf(shape: RoundedCornerShape, size: Size, density: Density): Float =
    // 这一档 `toPx` 收的是 **px 的 Size**（不是 `DpSize`）：形状自己会按传进去的盒子算半径，
    // 洞多大、角就跟着多大，比例留在 `LoveBrainShape` 那一处，这里不再换算第二次。
    shape.topStart.toPx(size, density)

/**
 * 指向被指物的那一格：形状用 core 的公共件 [LbTriangleGlyph]（基线 v1.1 §3.10 的圆化三角，
 * 半径 = 边长 × `LB_GLYPH_CORNER_RATIO`，页面拿不到那颗比例、也长不出第二种圆角）。
 *
 * 方向照 §6.7"优先放目标下方"那一支：提示板在目标下面 → 三角朝上（`TriangleDown` 转 180°）；
 * 屏幕放不下整摞翻到上方 → 直接 `TriangleDown` 朝下。旋转挂在字形自己的 `graphicsLayer` 上
 * （与 `DragHandle` 那一族同一口径：形状语义不动，方向由调用方拿着），盒子与偏移一寸不因旋转漂。
 *
 * ⚠ §6.7 给的是 12×8 那种**长短分离**的箭头，公共件只有等边档：这里外盒留 12×8 的位、
 * 字形取 8dp 那一档（更接近"小指头"而不是"色块"）。要真 12×8 得 core 那侧加档——已写进交接单 §2。
 */
@Composable
private fun CoachPointerSlot(
    target: Rect,
    pointsUp: Boolean,
    leftPx: Float,
    boxHeightPx: Float,
    outsetPx: Float,
    alpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    // 箭头紧贴 highlight 那一圈外侧；提示板再往下走一整摞（外扩 + 箭头高 + 间距），两者不叠。
    // 往上翻那一档从底边对齐：unaligned 位置是 boxHeight-箭头高，偏移量里箭头高自己抵消
    val offsetY = if (pointsUp) {
        (target.bottom + outsetPx).roundToInt()
    } else {
        (target.top - outsetPx - boxHeightPx).roundToInt()
    }

    Box(
        modifier = modifier
            .offset { IntOffset(leftPx.roundToInt(), offsetY) }
            .size(CoachMarksDimens.ARROW_WIDTH_DP.dp, CoachMarksDimens.ARROW_HEIGHT_DP.dp)
            .graphicsLayer { this.alpha = alpha }
            .testTag(LbCoachTags.POINTER)
    ) {
        LbTriangleGlyph(
            sizeDp = CoachMarksDimens.ARROW_HEIGHT_DP.dp,
            tone = LbTriangleGlyphTone.Accent,
            shape = LbTriangleGlyphShape.TriangleDown,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { rotationZ = if (pointsUp) 180f else 0f }
        )
    }
}

/**
 * 提示板：这一格缺什么 + 三条出口。宽 ≤260dp、内边距 12、圆角 16（§6.7）。
 *
 * 三颗动作全借设计系统的件（`LbPrimaryButton` / `LbTextAction`）：热区因此指回全站那一颗下限，
 * 页面不给它们垫盒子（§3.1、§3.5）。
 *
 * 落点公式：往下放从顶边对齐，偏移就是目标底边往下一摞；往上翻从底边对齐，偏移写成
 * "目标顶边 − 那一摞 − 整屏高"，提示板自己的排版高在两项相减里抵消 ⇒ 不需要估它的高度。
 */
@Composable
private fun CoachHintPlate(
    copy: CoachStepCopy,
    placeBelow: Boolean,
    target: Rect?,
    leftPx: Float,
    plateWidth: Dp,
    stackPx: Float,
    boxHeightPx: Float,
    alpha: Float = 1f,
    onOpenTarget: () -> Unit,
    onDefer: () -> Unit,
    onStopGuiding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetY = when {
        target == null -> 0
        placeBelow -> (target.bottom + stackPx).roundToInt()
        else -> (target.top - stackPx - boxHeightPx).roundToInt()
    }

    Column(
        modifier = modifier
            .then(
                if (target == null) {
                    Modifier
                } else {
                    Modifier.offset { IntOffset(leftPx.roundToInt(), offsetY) }
                }
            )
            .width(plateWidth)
            .graphicsLayer { this.alpha = alpha }
            .clip(LoveBrainShape.lg)
            .background(SurfaceCard)
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
            .padding(Spacing.lg)
            .semantics { contentDescription = copy.plateDescription }
            .testTag(LbCoachTags.PLATE),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = copy.title,
            style = AppTypography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(text = copy.body, style = AppTypography.bodyMedium, color = TextSecondary)
        LbPrimaryButton(
            state = LbButtonState.Idle,
            label = copy.actionLabel,
            onClick = onOpenTarget,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LbCoachTags.OPEN_TARGET)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            LbTextAction(
                label = copy.deferLabel,
                onClick = onDefer,
                tone = LbTextActionTone.Muted,
                modifier = Modifier.testTag(LbCoachTags.DEFER)
            )
            LbTextAction(
                label = copy.stopLabel,
                onClick = onStopGuiding,
                tone = LbTextActionTone.Muted,
                modifier = Modifier.testTag(LbCoachTags.STOP)
            )
        }
    }
}
