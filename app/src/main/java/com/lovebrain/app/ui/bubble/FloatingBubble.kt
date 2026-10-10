package com.lovebrain.app.ui.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lovebrain.app.AppConfig
import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.util.L
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

// ══════════════════════════════════════════════════════════════════
// 悬浮球：纯主球形态。
// 单击主球 → 直接展示完整悬浮窗；长按拖拽 → 边缘吸附。
//
// 设计参考：
// · ChatGPT Android Quick Settings Overlay：极简白色圆形 + 状态动画，不过度装饰
// · 微信浮窗：动效服务于功能、不打扰原则
// · M3 Expressive：spring 物理、克制动效
// · 小米悬浮球：单击/拖拽分级手势
// ══════════════════════════════════════════════════════════════════

/**
 * 悬浮球内部几何：全部从**当前档位直径**这一颗数推出（指导书 2026-10-10 §1「大小调节」）。
 *
 * 标准档（[com.lovebrain.app.BubbleSizeTier.STANDARD_DP]）的读数与这一笔之前**逐字相同**，
 * 因为那几个数就是原来的手调常量，只是从此按 `sizeDp / 标准档` 等比走；
 * 也就是说 48 与 64 两档读出来必然与 56 不同，不存在"球变了、角标还钉在 28dp 半径上"。
 */
internal object BubbleGeometry {

    /** 未读红点直径：**不**随档位缩放——再小就不是"一眼看见有新消息"那一颗了 */
    const val BADGE_SIZE_DP: Int = 10

    /** 红点阴影高度：同上，固定 */
    const val BADGE_SHADOW_DP: Int = 2

    /** 标准档（56dp 球）里主图标占的直径，等比推到其它档 */
    private const val STANDARD_ICON_SIZE_DP: Float = 34f

    /** 标准档（56dp 球）的手调角标偏移，等比推到其它档 */
    private const val STANDARD_BADGE_OFFSET_X_LEFT_DP = 4.6f     // 球吸左：红点朝屏幕中心侧
    private const val STANDARD_BADGE_OFFSET_X_RIGHT_DP = -20.6f  // 球吸右：镜像
    private const val STANDARD_BADGE_OFFSET_Y_DP = 1.7f

    /** 档位缩放因子：标准档 = 1f，48 档 < 1f，64 档 > 1f */
    fun scaleOf(sizeDp: Int): Float = sizeDp.toFloat() / BubbleSizeTier.STANDARD_DP

    /** 主图标直径：跟着球径等比，小档不挤、大档不空 */
    fun iconSizeDp(sizeDp: Int): Float = STANDARD_ICON_SIZE_DP * scaleOf(sizeDp)

    /** 角标相对 `Alignment.TopEnd` 基准位的偏移：横向符号由吸附方向决定，纵向固定比例 */
    fun badgeOffsetDp(sizeDp: Int, snapLeft: Boolean): Pair<Float, Float> =
        (if (snapLeft) STANDARD_BADGE_OFFSET_X_LEFT_DP else STANDARD_BADGE_OFFSET_X_RIGHT_DP) * scaleOf(sizeDp) to
            STANDARD_BADGE_OFFSET_Y_DP * scaleOf(sizeDp)
}

/** 悬浮球 UI 状态（Service 持有，Compose 只读） */
data class BubbleUiState(
    val dragging: Boolean = false,        // 是否处于拖拽中（抬起反馈）
    val badgeCount: Int = 0,              // 未读角标（无障碍服务新捕获消息数）
    val idleDimmed: Boolean = false,      // 闲置半透明（4s 无交互，AssistiveTouch 降遮挡思路）
    val snapLeft: Boolean = true,         // 球吸在左侧（红点朝屏幕中心侧偏移用）
    /**
     * 当前档位直径（dp）：**窗口宽高、球体、图标、角标偏移全从这一颗推**，
     * 与系统窗口的点击区同源（不许只对 Compose 用 scale()）。默认标准档 = 与从前逐字同形。
     */
    val sizeDp: Int = BubbleSizeTier.DEFAULT_DP,
    /** 用户设的背景层不透明度百分比：与面板背景**同一把尺**（[PanelBackdropOpacity]） */
    val backdropOpacityPercent: Int = PanelBackdropOpacity.DEFAULT_PERCENT
)

@Composable
fun FloatingBubble(
    state: BubbleUiState,
    onBubbleClick: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val appContext = LocalContext.current

    // ═══ 无障碍：多源检测"减少动画"（兼容主流 ROM） ═══
    val reduceMotion = remember {
        // 方案 1：Android 标准 API（API 16+）
        val animatorScale = try {
            Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) { 1f }

        // 方案 2：某些 ROM 自定义 API（MIUI、ColorOS 等）
        val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val isReducedMotion = accessibilityManager?.isEnabled == true &&
                             accessibilityManager?.isTouchExplorationEnabled == true // 作为辅助判断

        // 只要任一检测到用户偏好减少动画，就降级
        animatorScale < 0.5f || isReducedMotion
    }

    // 动画规格（减少动画时仍保留基本过渡，避免突变）
    val fastSpec: FiniteAnimationSpec<Float> = if (reduceMotion) tween(100) else tween(150)
    val midSpec: FiniteAnimationSpec<Float> = if (reduceMotion) tween(200) else tween(400)
    val badgeEnterSpec: EnterTransition = if (reduceMotion) fadeIn(tween(100))
        else scaleIn(spring(dampingRatio = AppConfig.BUBBLE_BADGE_ENTER_DAMPING, stiffness = AppConfig.BUBBLE_BADGE_ENTER_STIFFNESS)) + fadeIn(tween(150))
    val badgeExitSpec: ExitTransition = if (reduceMotion) fadeOut(tween(100))
        else scaleOut(spring(dampingRatio = AppConfig.BUBBLE_BADGE_EXIT_DAMPING, stiffness = AppConfig.BUBBLE_BADGE_EXIT_STIFFNESS)) + fadeOut(tween(120))

    // 球径：档位读数（Service 侧喂进来），与系统窗口的宽高同一颗数
    val mainSize = state.sizeDp

    // ═══ 主球按压/拖拽反馈 ═══
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = when {
            state.dragging -> 1.08f   // 拖拽中："抓起"放大
            pressed -> 0.92f          // 按压中：下沉反馈
            else -> 1f
        },
        animationSpec = fastSpec,
        label = "bubbleScale"
    )

    // ═══ 入场动画（M3 Expressive spring 物理浮入） ═══
    val enterAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            enterAnim.snapTo(1f)
        } else {
            enterAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = AppConfig.BUBBLE_ENTRANCE_DAMPING,
                    stiffness = AppConfig.BUBBLE_ENTRANCE_STIFFNESS
                )
            )
        }
    }

    // ═══ 闲置半透明（AssistiveTouch 降遮挡；交互/拖拽时回到用户设置值）═══
    // 有效 alpha 只在 `PanelBackdropOpacity.effectiveAlpha` 那一处算：静止 = 用户设的那一档，
    // 闲置 = 按原有降遮挡意图挪一格但不低于可读下限。
    // ⚠ 这里**不许再乘一次** BUBBLE_IDLE_ALPHA 或 alphaOf()——60% 档两次相乘会掉到 0.468，
    // 那就是"为了让图标变透明而把整颗球洗到看不见"，正是用户点名不要的那件事。
    val dimAlpha by animateFloatAsState(
        targetValue = PanelBackdropOpacity.effectiveAlpha(
            opacityPercentRaw = state.backdropOpacityPercent,
            idleDimmed = state.idleDimmed && !state.dragging && !pressed
        ),
        animationSpec = midSpec,
        label = "bubbleDimAlpha"
    )

    Box(modifier = Modifier.size(mainSize.dp)) {
        // ─── 未读角标（主球右上角；纯红点无数字，z 轴高于主球——/调节9） ───
        val badgeScale = remember { Animatable(1f) }
        LaunchedEffect(state.badgeCount) {
            if (state.badgeCount > 0) {
                badgeScale.snapTo(1.35f)
                badgeScale.animateTo(1f, spring(dampingRatio = AppConfig.BUBBLE_BADGE_POP_DAMPING, stiffness = AppConfig.BUBBLE_BADGE_POP_STIFFNESS))
            }
        }
        // 角标偏移按当前球径算（标准档的读数与从前逐字相同，48 / 64 档跟着半径走）
        val (badgeOffsetX, badgeOffsetY) = BubbleGeometry.badgeOffsetDp(mainSize, state.snapLeft)
        AnimatedVisibility(
            visible = state.badgeCount > 0,
            enter = badgeEnterSpec,
            exit = badgeExitSpec
        ) {
            Box(
                modifier = Modifier
                    // 相对 `Alignment.TopEnd` 基准位外推：方向 = 竖直向上向屏幕中心侧偏，
                    // 球在左→偏右，球在右→偏左；偏移量随档位等比（见 BubbleGeometry）
                    .zIndex(2f)
                    .align(Alignment.TopEnd)
                    .offset(x = badgeOffsetX.dp, y = badgeOffsetY.dp)
                    .size(BubbleGeometry.BADGE_SIZE_DP.dp)
                    .graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                        // 未读角标与主球共用**同一颗**有效 alpha（§1 原话：作用到"悬浮图标整体，
                        // 包括主图标和角标"）。这一层不另乘第二档：`dimAlpha` 已经是
                        // `PanelBackdropOpacity.effectiveAlpha` 的读数，再乘一次就是把红点洗到看不见。
                        alpha = enterAnim.value * dimAlpha
                    }
                    // shadow 在 clip 之前，圆角阴影贴合圆角
                    .shadow(BubbleGeometry.BADGE_SHADOW_DP.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Error)
            )
        }

        // ─── 主球（渐变跟随主题色 + 高光描边 + 动态阴影；图标=App图标简化版） ───
        Box(
            modifier = Modifier
                .size(mainSize.dp)
                .pointerInput(Unit) {
                    // 自定义 点击 vs 拖拽：累计位移 ≥ 20dp 才算拖拽，否则抬起视为点击
                    val slopPx = AppConfig.BUBBLE_DRAG_THRESHOLD_DP.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var accumulated = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                L.w("bubble: up/cancel dragging=$dragging -> ${if (dragging) "snap" else "openPanel"}")
                                if (dragging) onDragEnd() else onBubbleClick()
                                break
                            }
                            if (!dragging) {
                                accumulated += change.position - change.previousPosition
                                if (accumulated.getDistance() >= slopPx) {
                                    dragging = true
                                    change.consume()
                                }
                            } else {
                                change.consume()
                                val d = change.position - change.previousPosition
                                onDragDelta(d.x, d.y)
                            }
                        }
                    }
                }
                // 矩形阴影修复：去掉主球外层 shadow（Compose shadow 会延伸到矩形 bounds 外，看着像矩形阴影）
                .clip(CircleShape)
                .background(
                    // 降饱和：浅蓝 → 主蓝（DeepSeek 浅蓝风格，柔和不抢眼）
                    brush = Brush.linearGradient(
                        colors = listOf(PrimaryLight, Primary)
                    )
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { /* 点击已由父级 drag 判定，此处仅收集按压态 */ }
                )
                // RB-01：用 clearAndSetSemantics 提供真正的 accessibility onClick action
                // TalkBack 双击走 semantics onClick，普通手指走 pointerInput，各走各的不冲突
                .clearAndSetSemantics {
                    contentDescription =
                        if (state.badgeCount > 0) {
                            "打开军师悬浮窗，有${state.badgeCount}条新消息"
                        } else {
                            "打开军师悬浮窗"
                        }
                    role = Role.Button
                    onClick(label = "打开军师悬浮窗") {
                        onBubbleClick()
                        true
                    }
                }
                .graphicsLayer {
                    // 入场缩放 × 按压/拖拽缩放；alpha 只吃"有效 alpha"那一颗（含用户档位 + 闲置降档）
                    val enterScale = 0.7f + 0.3f * enterAnim.value
                    scaleX = enterScale * pressScale
                    scaleY = enterScale * pressScale
                    alpha = enterAnim.value * dimAlpha
                },
            contentAlignment = Alignment.Center
        ) {
            // 图标：直接用 App 图标（自适应图标前景，圆形裁剪，保留原色）；直径跟着档位走
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "军师助手",
                tint = Color.Unspecified,
                modifier = Modifier.size(BubbleGeometry.iconSizeDp(mainSize).dp)
            )
        }
    }
}
