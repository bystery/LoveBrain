package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovebrain.app.R
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SchemeIdentity
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 方案卡尺寸常量（骨架屏共用，值不变；公共对象供同包 ResultArea 引用）。
 */
object SchemeCardDimens {
    /**
     * 卡宽（骨架屏与实体卡共用）。
     *
     * 158 → **164** 不是改版式：卡片右下角是三颗图标动作，每颗的可点击盒要 ≥48dp
     * （§6.5 :531），而卡内左右各 `Spacing.md`=8dp 内边距 ⇒ 里面只剩 142dp，
     * 三颗 48 需要 144dp。原来 158 那一档放不下，实测最后一颗被压成 **46x48dp**。
     * 加 6dp 是为了留 4dp 余量，不是随手凑整。
     * 由 `SchemeCardDimens values are stable` 那格按"放得下三颗下限"判，不再钉这个数。
     */
    const val CARD_WIDTH_DP = 164
    const val CARD_HEIGHT_DP = 150    // 卡高（骨架屏 166->150 对齐实体，消除跳变）
    const val CARD_MAX_HEIGHT_DP = 200 // 最大高度上限，防止展开时无限增长
    const val TAG_HPAD_DP = 6         // 标签水平内边距
    const val TAG_VPAD_DP = 3         // 标签垂直内边距
    const val TAG_TO_BODY_GAP_DP = 6  // 标签到正文间距
}

/** 方案卡正文排版常量（internal：同包抽离的展示子组件共用） */
internal object SchemeTextDimens {
    val BODY_FONT_SIZE = 13.sp       // 话术正文字号
    val BODY_LINE_HEIGHT = 18.sp     // 话术正文行高
}

/**
 * SchemeCard 展示状态——纯展示态推导，不负责 transition。
 *
 * 状态决定卡片内容层显示什么：
 * - Collapsed: 标签 + 正文 + 操作行（默认态）
 * - Adjusting: 标签 + 改写选项 + 取消（调整态，替换内容不追加）
 * - Recording/Recognizing: 录音/识别中
 * - Rewriting: 改写 API 调用中
 * - RewriteError/RewriteDone: 改写结果
 */
sealed class SchemeCardPresentationState {
    data object Collapsed : SchemeCardPresentationState()
    data object Adjusting : SchemeCardPresentationState()
    data object Recording : SchemeCardPresentationState()
    data object Recognizing : SchemeCardPresentationState()
    data object Rewriting : SchemeCardPresentationState()
    data class RewriteError(val message: String) : SchemeCardPresentationState()
    data object RewriteDone : SchemeCardPresentationState()
}

/**
 * 回复方案卡（单面卡）：v1.3.1 视觉重量回归。
 *
 * 默认态：标签 + 正文 + 右下操作（复制/赞/踩）。
 * 调整态：标签 + 改写选项 + 取消（替换内容，不追加）。
 *
 * 卡片展开 = 进入调整态，替换内容而非在正文下方追加
 * 方向 chips 移出卡片——方向属于 Result-level
 * 使用 pointerInput 实现真实手势生命周期
 * 权限反馈移出卡片——通过 onPermissionEvent 回调通知 Panel
 */
@Composable
fun SchemeCard(
    scheme: Scheme,
    feedback: SchemeFeedback,
    onFeedback: (Scheme, SchemeFeedback) -> Unit,
    onCopy: (Scheme) -> Unit,
    rewriteState: RewriteState? = null,
    onRewrite: (SchemeIdentity, RewriteCommand) -> Unit = { _, _ -> },
    onClearRewriteState: (SchemeIdentity) -> Unit = {},
    onCancelRewrite: (SchemeIdentity) -> Unit = {},
    onUndoRewrite: (SchemeIdentity) -> Unit = {},
    onToggleRewriteExpand: (SchemeIdentity) -> Unit = {},
    isExpanded: Boolean = false,
    onVoiceRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    // 权限事件回调——Panel/ViewModel 复用 panelWarning/banner
    onPermissionEvent: (PermissionEvent) -> Unit = {},
    // 自定义改写回调
    onCustomRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val isEmpty = scheme.reply.isBlank()
    // 使用 identity 作为所有操作的稳定身份
    val identity = scheme.identity
    val identityKey = identity.key

    val borderColor by animateColorAsStateCompat(
        targetValue = when (feedback) {
            SchemeFeedback.LIKED -> Primary
            SchemeFeedback.DISLIKED -> Error
            SchemeFeedback.NONE -> Border
        },
        label = "cardBorder"
    )
    val borderWidth by animateFloatAsState(
        targetValue = if (feedback != SchemeFeedback.NONE) 2f else 1f,
        animationSpec = tween(200),
        label = "cardBorderWidth"
    )

    val isRecommended = scheme.title == "推荐" && !isEmpty
    val tagColor = if (isRecommended) Color.White else PrimaryDark
    val tagBg = if (isRecommended) Primary else PrimaryLight
    val cardBg = if (isEmpty) SurfaceInset else SurfaceCard
    val bodyColor = if (isEmpty) TextHint else TextPrimary

    // 改写状态
    val isRewriting = rewriteState is RewriteState.Loading
    val rewriteError = (rewriteState as? RewriteState.Error)?.message
    val rewriteDone = rewriteState is RewriteState.Done

    // 语音改写控制器——只负责 STT
    // 权限结果通过回调上抛，不在卡片内展示
    val voiceController = rememberVoiceRewriteController(
        schemeTag = identityKey,
        onVoiceRewrite = { tag, transcript -> onVoiceRewrite(identity, transcript) },
        onPermissionGranted = {
            onPermissionEvent(PermissionEvent.Granted)
        },
        onPermissionDenied = { permanently ->
            onPermissionEvent(PermissionEvent.Denied(permanently))
        }
    )
    val voiceState = voiceController.state
    val isRecording = voiceState == VoiceRewriteState.RECORDING || voiceState == VoiceRewriteState.PROCESSING

    // 统一状态推导——使用抽离的纯函数
    val cardState: SchemeCardPresentationState = deriveCardPresentationState(
        isRecording = isRecording,
        voiceState = voiceState,
        isRewriting = isRewriting,
        rewriteError = rewriteError,
        rewriteDone = rewriteDone,
        isExpanded = isExpanded
    )

    // pointerInput 手势生命周期——真实 PRESSING 状态 + 移出取消
    // DOWN -> PRESSING（未达阈值）
    // 达到长按阈值 -> RECORDING
    // RECORDING 中正常 UP -> RELEASED -> stopListening
    // RECORDING 中 pointer 离开有效区域 -> CANCELLED -> cancel，不发 API
    // PRESSING 中 UP（未达阈值）-> 普通 click
    val longPressThresholdMs = 300L
    var longPressTriggered by remember { mutableStateOf(false) }
    var gesturePhase by remember { mutableStateOf(GesturePhase.IDLE) }

    // touch slop——拖动超过此距离时取消长按等待，避免横滑/纵滚误触录音
    val touchSlopPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }

    // 录音中或改写中——卡片边框高亮
    val effectiveBorderWidth = if (isRecording || isRewriting) 2f else borderWidth
    val effectiveBorderColor = when {
        isRecording || isRewriting -> Primary
        feedback != SchemeFeedback.NONE -> borderColor
        else -> Border
    }

    // 按压缩放
    val scale by animateFloatAsState(
        targetValue = if (longPressTriggered || isRecording || gesturePhase == GesturePhase.PRESSING) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "cardScale"
    )

    // 外框高度保持稳定
    Box(
        modifier = modifier
            .width(SchemeCardDimens.CARD_WIDTH_DP.dp)
            .heightIn(min = SchemeCardDimens.CARD_HEIGHT_DP.dp, max = SchemeCardDimens.CARD_MAX_HEIGHT_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.lg)
            .clip(LoveBrainShape.lg)
            .background(cardBg)
            .border(effectiveBorderWidth.dp, effectiveBorderColor, LoveBrainShape.lg)
            .then(if (isEmpty) Modifier else Modifier.pointerInput(identityKey) {
                // 手势状态机由 reduceGesturePhase 纯函数驱动——
                // pointerInput 事件喂给 reducer，所有状态转换通过 reducer 完成。
                // longPressReached 从 reducer 状态推导（RECORDING/RELEASED/CANCELLED 意味着已达到长按阈值）。
                // JVM reducer test 真正保护生产逻辑。
                kotlinx.coroutines.coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        // DOWN 事件 → reducer 驱动到 PRESSING
                        gesturePhase = reduceGesturePhase(gesturePhase, GestureEvent.DOWN)

                        // longPressReached 从 reducer 状态推导——不再维护并行变量
                        fun hasReachedLongPress(): Boolean =
                            gesturePhase == GesturePhase.RECORDING ||
                            gesturePhase == GesturePhase.RELEASED ||
                            gesturePhase == GesturePhase.CANCELLED

                        var pointerLeftBounds = false
                        var dragCancelled = false
                        val downPos = down.position

                        val longPressJob = launch {
                            kotlinx.coroutines.delay(longPressThresholdMs)
                            // 达到长按阈值 → LONG_PRESS_REACHED 事件喂给 reducer
                            if (gesturePhase == GesturePhase.PRESSING && !isRewriting && rewriteError == null && !isRecording && !dragCancelled) {
                                val result = voiceController.startListening()
                                val canStart = result == StartListeningResult.STARTED
                                gesturePhase = reduceGesturePhase(
                                    gesturePhase, GestureEvent.LONG_PRESS_REACHED, canStart
                                )
                                if (canStart) {
                                    longPressTriggered = true
                                }
                            }
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (!change.pressed) {
                                    // 手指抬起
                                    longPressJob.cancel()
                                    val stillInside = change.position.x >= 0f &&
                                        change.position.x <= size.width &&
                                        change.position.y >= 0f &&
                                        change.position.y <= size.height

                                    // upEvent 从 reducer 状态推导——hasReachedLongPress() 替代局部变量
                                    val upEvent = if (hasReachedLongPress() && !pointerLeftBounds) {
                                        if (stillInside) GestureEvent.UP_IN_BOUNDS
                                        else GestureEvent.UP_OUT_OF_BOUNDS
                                    } else if (hasReachedLongPress() && pointerLeftBounds) {
                                        GestureEvent.UP_OUT_OF_BOUNDS
                                    } else {
                                        GestureEvent.UP_IN_BOUNDS
                                    }
                                    gesturePhase = reduceGesturePhase(gesturePhase, upEvent)

                                    if (gesturePhase == GesturePhase.RELEASED) {
                                        // 正常松手 → release() 内部完成 stop + rendezvous 提交
                                        voiceController.release()
                                        gesturePhase = reduceGesturePhase(gesturePhase, GestureEvent.DOWN) // RELEASED → IDLE
                                    } else if (!hasReachedLongPress() && !dragCancelled) {
                                        // 未达到长按阈值 → 普通 click
                                        if (!isRecording && !isRewriting) {
                                            onToggleRewriteExpand(identity)
                                        }
                                    }
                                    break
                                }

                                // touch slop 检测——拖动超过阈值时取消 PRESSING
                                if (!dragCancelled && !hasReachedLongPress() && gesturePhase == GesturePhase.PRESSING) {
                                    val dx = change.position.x - downPos.x
                                    val dy = change.position.y - downPos.y
                                    val dragDist = kotlin.math.sqrt(dx * dx + dy * dy)
                                    if (dragDist > touchSlopPx) {
                                        dragCancelled = true
                                        gesturePhase = reduceGesturePhase(gesturePhase, GestureEvent.DRAG_CANCELLED)
                                        longPressJob.cancel()
                                    }
                                }

                                // 检查手指是否仍在卡片边界内
                                val stillInsideBounds = change.position.x >= 0f &&
                                    change.position.x <= size.width &&
                                    change.position.y >= 0f &&
                                    change.position.y <= size.height

                                if (!stillInsideBounds && hasReachedLongPress() && !pointerLeftBounds) {
                                    // 手指移出卡片有效区域 → UP_OUT_OF_BOUNDS 或 SYSTEM_CANCEL
                                    pointerLeftBounds = true
                                    gesturePhase = reduceGesturePhase(gesturePhase, GestureEvent.UP_OUT_OF_BOUNDS)
                                    voiceController.cancel()
                                }
                            }
                        } finally {
                            longPressJob.cancel()
                            // 确保状态归位——RELEASED/CANCELLED → IDLE
                            if (gesturePhase == GesturePhase.RELEASED || gesturePhase == GesturePhase.CANCELLED) {
                                gesturePhase = reduceGesturePhase(gesturePhase, GestureEvent.DOWN)
                            }
                            longPressTriggered = false
                            // 如果手势结束时仍在 RECORDING（未正常 RELEASED），确保 cancel 清理
                            if (gesturePhase == GesturePhase.RECORDING || dragCancelled) {
                                voiceController.cancel()
                                gesturePhase = GesturePhase.IDLE
                            }
                        }
                    }
                }
            })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
                .animateContentSize()
        ) {
            // 标签行（所有状态都显示）
            Box(
                modifier = Modifier
                    .background(tagBg, LoveBrainShape.sm)
                    .padding(horizontal = SchemeCardDimens.TAG_HPAD_DP.dp, vertical = SchemeCardDimens.TAG_VPAD_DP.dp)
            ) {
                Text(
                    text = "${scheme.tag} · ${scheme.title}",
                    color = tagColor,
                    style = AppTypography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(SchemeCardDimens.TAG_TO_BODY_GAP_DP.dp))

            // 根据 cardState 渲染卡片内容——替换而非追加
            // 各状态内容层已抽离为同包纯展示子组件（Scheme*Block.kt），此处只做分发。
            // modifier 中的 weight(1f) 让子组件根节点在 Column 中撑开剩余空间，
            // 与原内联实现的布局权重完全一致。
            when (cardState) {
                is SchemeCardPresentationState.Recording, SchemeCardPresentationState.Recognizing -> {
                    SchemeRecordingBlock(
                        isRecognizing = cardState is SchemeCardPresentationState.Recognizing,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                SchemeCardPresentationState.Rewriting -> {
                    SchemeRewritingBlock(
                        onCancelRewrite = { onCancelRewrite(identity) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                is SchemeCardPresentationState.RewriteError -> {
                    SchemeRewriteErrorBlock(
                        message = cardState.message,
                        onClearRewriteState = { onClearRewriteState(identity) },
                        onToggleRewriteExpand = { onToggleRewriteExpand(identity) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                SchemeCardPresentationState.RewriteDone -> {
                    SchemeRewriteDoneBlock(
                        reply = scheme.reply,
                        bodyColor = bodyColor,
                        onUndoRewrite = { onUndoRewrite(identity) },
                        onClearRewriteState = { onClearRewriteState(identity) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                SchemeCardPresentationState.Adjusting -> {
                    SchemeAdjustingBlock(
                        onRewrite = { command -> onRewrite(identity, command) },
                        onCustomRewrite = { text -> onCustomRewrite(identity, text) },
                        onCancel = { onToggleRewriteExpand(identity) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                SchemeCardPresentationState.Collapsed -> {
                    SchemeCollapsedBlock(
                        isEmpty = isEmpty,
                        reply = scheme.reply,
                        bodyColor = bodyColor,
                        feedback = feedback,
                        onCopy = { onCopy(scheme) },
                        onFeedback = { fb -> onFeedback(scheme, fb) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 权限事件——SchemeCard 上抛给 Panel/ViewModel。
 * Panel 复用 panelWarning/KbNoticeBanner 展示，不在卡片内造通知。
 */
sealed class PermissionEvent {
    data object Granted : PermissionEvent()
    data class Denied(val permanently: Boolean) : PermissionEvent()
}

/**
 * 兼容封装：颜色动画状态。
 */
@Composable
private fun animateColorAsStateCompat(
    targetValue: Color,
    label: String
): State<Color> {
    return animateColorAsState(
        targetValue = targetValue,
        animationSpec = tween(300),
        label = label
    )
}
