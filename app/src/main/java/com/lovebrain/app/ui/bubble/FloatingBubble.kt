package com.lovebrain.app.ui.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lovebrain.app.AppConfig
import com.lovebrain.app.R
import com.lovebrain.app.ui.theme.Error
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.PrimaryLight
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

/** 悬浮球内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object BubbleDimens {
    const val BADGE_SIZE_DP = 10     // 未读红点直径
    const val BADGE_SHADOW_DP = 2    // 红点阴影高度
    const val ICON_SIZE_DP = 34      // 主球内图标尺寸
}

/** 悬浮球 UI 状态（Service 持有，Compose 只读） */
data class BubbleUiState(
    val dragging: Boolean = false,        // 是否处于拖拽中（抬起反馈）
    val badgeCount: Int = 0,              // 未读角标（无障碍服务新捕获消息数）
    val idleDimmed: Boolean = false,      // 闲置半透明（4s 无交互，AssistiveTouch 降遮挡思路）
    val edgeBreathing: Boolean = false,   // 半隐藏露边呼吸（让用户知道球还在）
    val snapLeft: Boolean = true          // 球吸在左侧（红点朝屏幕中心侧偏移用）
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

    val mainSize = AppConfig.BUBBLE_SIZE

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

    // ═══ 闲置半透明（AssistiveTouch 降遮挡；交互/拖拽时恢复不透明） ═══
    val dimAlpha by animateFloatAsState(
        targetValue = if (state.idleDimmed && !state.dragging && !pressed) {
            AppConfig.BUBBLE_IDLE_ALPHA
        } else 1f,
        animationSpec = midSpec,
        label = "bubbleDimAlpha"
    )

    // ═══ 半隐藏露边呼吸（只露 12dp 时靠呼吸感维持存在感，防误以为闪退） ═══
    val breathTransition = rememberInfiniteTransition(label = "bubbleBreath")
    val breathAlpha by breathTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubbleBreathAlpha"
    )
    Box(modifier = Modifier.size(mainSize.dp)) {
        // ─── 未读角标（主球右上角；纯红点无数字，z 轴高于主球——需求#14/调节9） ───
        val badgeScale = remember { Animatable(1f) }
        LaunchedEffect(state.badgeCount) {
            if (state.badgeCount > 0) {
                badgeScale.snapTo(1.35f)
                badgeScale.animateTo(1f, spring(dampingRatio = AppConfig.BUBBLE_BADGE_POP_DAMPING, stiffness = AppConfig.BUBBLE_BADGE_POP_STIFFNESS))
            }
        }
        AnimatedVisibility(
            visible = state.badgeCount > 0,
            enter = badgeEnterSpec,
            exit = badgeExitSpec
        ) {
            Box(
                modifier = Modifier
                    // 红点中心精确落在圆周上（红点半径5 + 球半径28 = 33dp），
                    // 方向 = 竖直向上向屏幕中心侧偏 20°：球在左→偏右，球在右→偏左
                    .zIndex(2f)
                    .align(Alignment.TopEnd)
                    .offset(
                        x = if (state.snapLeft) 4.6.dp else (-20.6).dp,
                        y = 1.7.dp
                    )
                    .size(BubbleDimens.BADGE_SIZE_DP.dp)
                    .graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                    }
                    // 需求#43：shadow 在 clip 之前，圆角阴影贴合圆角
                    .shadow(BubbleDimens.BADGE_SHADOW_DP.dp, CircleShape)
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
                // 无障碍：语义随状态变化（TalkBack 可读）
                .semantics {
                    contentDescription = "打开军师悬浮窗"
                }
                .graphicsLayer {
                    // 入场缩放 × 按压/拖拽缩放；闲置半透明 × 露边呼吸
                    val enterScale = 0.7f + 0.3f * enterAnim.value
                    scaleX = enterScale * pressScale
                    scaleY = enterScale * pressScale
                    alpha = enterAnim.value * dimAlpha * if (state.edgeBreathing && !reduceMotion) breathAlpha else 1f
                },
            contentAlignment = Alignment.Center
        ) {
            // 图标：需求#13 直接用 App 图标（自适应图标前景，圆形裁剪，保留原色）
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "军师助手",
                tint = Color.Unspecified,
                modifier = Modifier.size(BubbleDimens.ICON_SIZE_DP.dp)
            )
        }
    }
}
