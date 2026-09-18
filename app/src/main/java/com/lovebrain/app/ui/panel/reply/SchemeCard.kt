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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.res.painterResource
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
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 方案卡尺寸常量（骨架屏共用，值不变；公共对象供同包 ResultArea 引用）。
 */
object SchemeCardDimens {
    const val CARD_WIDTH_DP = 158     // 卡宽（骨架屏与实体卡共用）
    const val CARD_HEIGHT_DP = 150    // 卡高（骨架屏 166->150 对齐实体，消除跳变）
    const val CARD_MAX_HEIGHT_DP = 200 // P1-3: 最大高度上限，防止展开时无限增长
    const val TAG_HPAD_DP = 6         // 标签水平内边距
    const val TAG_VPAD_DP = 3         // 标签垂直内边距
    const val TAG_TO_BODY_GAP_DP = 6  // 标签到正文间距
    const val ACTION_ICON_SIZE_DP = 13 // 操作图标视觉尺寸
}

/** 方案卡正文排版常量 */
private object SchemeTextDimens {
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
 * P0-4: 卡片展开 = 进入调整态，替换内容而非在正文下方追加
 * P0-5: 方向 chips 移出卡片——方向属于 Result-level
 * P0-6: 使用 pointerInput 实现真实手势生命周期
 * P0-7: 权限反馈移出卡片——通过 onPermissionEvent 回调通知 Panel
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
    // P0-7: 权限事件回调——Panel/ViewModel 复用 panelWarning/banner
    onPermissionEvent: (PermissionEvent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isEmpty = scheme.reply.isBlank()
    // P0-1: 使用 identity 作为所有操作的稳定身份
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

    // P0-6: 语音改写控制器——只负责 STT
    // P0-7: 权限结果通过回调上抛，不在卡片内展示
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

    // P1-12: 统一状态推导——使用抽离的纯函数
    val cardState: SchemeCardPresentationState = deriveCardPresentationState(
        isRecording = isRecording,
        voiceState = voiceState,
        isRewriting = isRewriting,
        rewriteError = rewriteError,
        rewriteDone = rewriteDone,
        isExpanded = isExpanded
    )

    // P0-6: pointerInput 手势生命周期——真实 PRESSING 状态 + 移出取消
    // DOWN -> PRESSING（未达阈值）
    // 达到长按阈值 -> RECORDING
    // RECORDING 中正常 UP -> RELEASED -> stopListening
    // RECORDING 中 pointer 离开有效区域 -> CANCELLED -> cancel，不发 API
    // PRESSING 中 UP（未达阈值）-> 普通 click
    val longPressThresholdMs = 300L
    var longPressTriggered by remember { mutableStateOf(false) }
    var gesturePhase by remember { mutableStateOf(GesturePhase.IDLE) }

    // P0-5: touch slop——拖动超过此距离时取消长按等待，避免横滑/纵滚误触录音
    val touchSlopPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }

    // 录音中或改写中——卡片边框高亮
    val effectiveBorderWidth = if (isRecording || isRewriting) 2f else borderWidth
    val effectiveBorderColor = when {
        isRecording || isRewriting -> Primary
        feedback != SchemeFeedback.NONE -> borderColor
        else -> Border
    }

    // P0-4: 按压缩放
    val scale by animateFloatAsState(
        targetValue = if (longPressTriggered || isRecording || gesturePhase == GesturePhase.PRESSING) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "cardScale"
    )

    // P0-1: 外框高度保持稳定
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
                // P0-5: 真实手势状态机——单一 owner，避免 SchemeCard 和 VoiceRewriteController 双状态机漂移
                // touch slop 检测：拖动超过阈值时取消 PRESSING，避免横滑/纵滚误触录音
                kotlinx.coroutines.coroutineScope {
                    awaitEachGesture {
                        // 等待手指按下——requireUnconsumed=true 保证只收到未被子控件消费的事件
                        val down = awaitFirstDown(requireUnconsumed = true)
                        // 如果 awaitFirstDown 返回了，说明事件未被消费——直接进入手势
                        gesturePhase = GesturePhase.PRESSING

                        var longPressReached = false
                        var pointerLeftBounds = false
                        var dragCancelled = false
                        val downPos = down.position

                        val longPressJob = launch {
                            kotlinx.coroutines.delay(longPressThresholdMs)
                            // 达到长按阈值 -> 开始录音（拖动取消后不触发）
                            if (gesturePhase == GesturePhase.PRESSING && !isRewriting && rewriteError == null && !isRecording && !dragCancelled) {
                                // P0-1: startListening 返回 typed result——只有 STARTED 才进入 RECORDING
                                val result = voiceController.startListening()
                                if (result == StartListeningResult.STARTED) {
                                    longPressReached = true
                                    longPressTriggered = true
                                    gesturePhase = GesturePhase.RECORDING
                                }
                                // PERMISSION_REQUESTED / FAILED → 不进入 RECORDING
                                // 外层松手时 stopListening 安全处理 null recognizer
                            }
                        }

                        // 持续追踪手指位置——检测拖动取消和移出边界
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (!change.pressed) {
                                    // 手指抬起
                                    longPressJob.cancel()
                                    if (longPressReached && !pointerLeftBounds) {
                                        // 正常松手 -> 停止录音，提交暂存的 transcript
                                        // P0-1: stopListening 停止录音，commitTranscript 提交缓存的 final transcript
                                        // onResults 可能已暂存 transcript，commitTranscript 会读取并提交
                                        gesturePhase = GesturePhase.RELEASED
                                        voiceController.stopListening()
                                        voiceController.commitTranscript()
                                    } else if (!longPressReached && !dragCancelled) {
                                        // 未达到长按阈值且未拖动取消 -> 普通 click
                                        gesturePhase = GesturePhase.IDLE
                                        if (!isRecording && !isRewriting) {
                                            onToggleRewriteExpand(identity)
                                        }
                                    }
                                    break
                                }

                                // P0-5: touch slop 检测——拖动距离超过阈值时取消 PRESSING
                                // 避免横滑 LazyRow 或纵滚长文误触发长按录音
                                if (!dragCancelled && !longPressReached && gesturePhase == GesturePhase.PRESSING) {
                                    val dx = change.position.x - downPos.x
                                    val dy = change.position.y - downPos.y
                                    val dragDist = kotlin.math.sqrt(dx * dx + dy * dy)
                                    if (dragDist > touchSlopPx) {
                                        dragCancelled = true
                                        gesturePhase = GesturePhase.IDLE
                                        longPressJob.cancel()
                                    }
                                }

                                // 检查手指是否仍在卡片边界内
                                val stillInside = change.position.x >= 0f &&
                                    change.position.x <= size.width &&
                                    change.position.y >= 0f &&
                                    change.position.y <= size.height

                                if (!stillInside && longPressReached && !pointerLeftBounds) {
                                    // 手指移出卡片有效区域 -> CANCELLED
                                    pointerLeftBounds = true
                                    gesturePhase = GesturePhase.CANCELLED
                                    voiceController.cancel()
                                }
                            }
                        } finally {
                            longPressJob.cancel()
                            if (gesturePhase != GesturePhase.CANCELLED) {
                                gesturePhase = GesturePhase.IDLE
                            }
                            longPressTriggered = false
                            // P0-1: 如果手势结束时仍在 RECORDING/PROCESSING 但未正常 RELEASED
                            // （如 dragCancelled 后松手），确保 cancel 清理
                            if (gesturePhase == GesturePhase.CANCELLED || dragCancelled) {
                                voiceController.cancel()
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

            // P0-4: 根据 cardState 渲染卡片内容——替换而非追加
            when (cardState) {
                is SchemeCardPresentationState.Recording, SchemeCardPresentationState.Recognizing -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Primary
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = if (cardState is SchemeCardPresentationState.Recognizing) "识别中..." else "正在录音...",
                                style = AppTypography.labelSmall,
                                color = PrimaryDark
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = "松手后用语音修改",
                                style = AppTypography.labelSmall,
                                color = TextHint
                            )
                        }
                    }
                }
                SchemeCardPresentationState.Rewriting -> {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "正在改写...",
                            style = AppTypography.labelSmall,
                            color = PrimaryDark
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "取消",
                            style = AppTypography.labelSmall,
                            color = TextHint,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onCancelRewrite(identity) }
                            ).padding(Spacing.xs)
                        )
                    }
                }
                is SchemeCardPresentationState.RewriteError -> {
                    // P0-4: 改写错误——替换内容显示错误+重试
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = cardState.message,
                                style = AppTypography.labelSmall,
                                color = Error,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                "重试",
                                style = AppTypography.labelSmall,
                                color = PrimaryDark,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        onClearRewriteState(identity)
                                        onToggleRewriteExpand(identity)
                                    }
                                ).padding(Spacing.xs)
                            )
                        }
                    }
                }
                SchemeCardPresentationState.RewriteDone -> {
                    // P0-4: 改写成功——显示新正文+撤销入口
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = scheme.reply,
                            color = bodyColor,
                            style = AppTypography.bodyMedium,
                            fontSize = SchemeTextDimens.BODY_FONT_SIZE,
                            lineHeight = SchemeTextDimens.BODY_LINE_HEIGHT
                        )
                    }
                    // 撤销入口
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "撤销",
                            style = AppTypography.labelSmall,
                            color = PrimaryDark,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onUndoRewrite(identity) }
                            ).padding(horizontal = Spacing.xs, vertical = Spacing.xs)
                        )
                    }
                }
                SchemeCardPresentationState.Adjusting -> {
                    // P0-4: 调整态——替换内容：显示改写选项 + 取消
                    // 不显示方向 chips（P0-5: 方向属于 Result-level）
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        val chunkedRows = RewriteCommand.entries.chunked(2)
                        chunkedRows.forEachIndexed { rowIndex, rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                rowOptions.forEach { command ->
                                    val (interaction, optScale) = rememberPressScale(0.94f, "optScale${command.label}")
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .graphicsLayer { scaleX = optScale; scaleY = optScale }
                                            .clip(LoveBrainShape.sm)
                                            .background(PrimaryLight, LoveBrainShape.sm)
                                            .clickable(
                                                interactionSource = interaction,
                                                indication = null,
                                                onClick = { onRewrite(identity, command) }
                                            )
                                            .padding(vertical = Spacing.xs, horizontal = Spacing.sm),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            command.label,
                                            style = AppTypography.labelSmall,
                                            color = PrimaryDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (rowIndex < chunkedRows.lastIndex) {
                                Spacer(Modifier.height(Spacing.xs))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // 取消/返回
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "取消",
                                style = AppTypography.labelSmall,
                                color = TextHint,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onToggleRewriteExpand(identity) }
                                ).padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                            )
                        }
                    }
                }
                SchemeCardPresentationState.Collapsed -> {
                    // P0-4: 默认态——v1.3.1 简洁：标签 + 正文 + 操作行
                    if (isEmpty) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "本轮不适合",
                                color = TextHint,
                                style = AppTypography.labelMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = scheme.reply,
                                color = bodyColor,
                                style = AppTypography.bodyMedium,
                                fontSize = SchemeTextDimens.BODY_FONT_SIZE,
                                lineHeight = SchemeTextDimens.BODY_LINE_HEIGHT
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.sm))

                    // 操作行：右下角（空回复不显示操作按钮）
                    if (!isEmpty) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CardActionIcon(
                                icon = R.drawable.ic_copy,
                                desc = "复制",
                                tint = TextSecondary,
                                onClick = { onCopy(scheme) }
                            )
                            CardActionIcon(
                                icon = R.drawable.ic_thumb_up,
                                desc = "赞",
                                tint = if (feedback == SchemeFeedback.LIKED) Primary else TextHint,
                                onClick = { onFeedback(scheme, SchemeFeedback.LIKED) }
                            )
                            CardActionIcon(
                                icon = R.drawable.ic_thumb_down,
                                desc = "踩",
                                tint = if (feedback == SchemeFeedback.DISLIKED) Error else TextHint,
                                onClick = { onFeedback(scheme, SchemeFeedback.DISLIKED) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 卡片操作小图标：视觉 20dp，点击热区外扩至 28dp（触控下限友好） */
@Composable
internal fun CardActionIcon(
    icon: Int,
    desc: String,
    tint: Color,
    onClick: () -> Unit
) {
    val (iconInteraction, iconScale) = rememberPressScale(0.92f, "cardActionIconScale")
    Box(
        modifier = Modifier
            .size(Spacing.xxl)
            .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
            .clip(LoveBrainShape.sm)
            .clickable(interactionSource = iconInteraction, indication = null, onClick = onClick)
            .padding(Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(SchemeCardDimens.ACTION_ICON_SIZE_DP.dp)
        )
    }
}

/**
 * P0-7: 权限事件——SchemeCard 上抛给 Panel/ViewModel。
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
