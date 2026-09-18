package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import com.lovebrain.app.model.DirectionCatalog
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*

/**
 * 方案卡尺寸常量（：骨架屏共用，值不变；公共对象供同包 ResultArea 引用）。
 */
object SchemeCardDimens {
    const val CARD_WIDTH_DP = 158     // 卡宽（骨架屏与实体卡共用）
    const val CARD_HEIGHT_DP = 150    // 卡高（：骨架屏 166→150 对齐实体，消除跳变）
    const val CARD_MAX_HEIGHT_DP = 240 // P1-3: 最大高度上限，防止展开时无限增长
    const val TAG_HPAD_DP = 6         // 标签水平内边距
    const val TAG_VPAD_DP = 3         // 标签垂直内边距
    const val TAG_TO_BODY_GAP_DP = 6  // 标签到正文间距
    const val ACTION_ICON_SIZE_DP = 13 // 操作图标视觉尺寸
}

/** 方案卡正文排版常量（ 外放：值不变，仅外放命名） */
private object SchemeTextDimens {
    val BODY_FONT_SIZE = 13.sp       // 话术正文字号
    val BODY_LINE_HEIGHT = 18.sp     // 话术正文行高
}

/**
 * P1-12: SchemeCard 统一 UI 状态 reducer。
 * 不再由多个互不相关 Boolean 拼接，消除非法组合。
 */
sealed class SchemeCardState {
    /** 正常收起态 */
    data object Collapsed : SchemeCardState()
    /** 展开文字改写选项 */
    data object Expanded : SchemeCardState()
    /** 正在录音 */
    data object Recording : SchemeCardState()
    /** STT 识别中 */
    data object Recognizing : SchemeCardState()
    /** 正在改写（文字或语音，统一 RewriteState.Loading） */
    data object Rewriting : SchemeCardState()
    /** 改写失败 */
    data class RewriteError(val message: String) : SchemeCardState()
    /** 改写成功（可撤销） */
    data object RewriteDone : SchemeCardState()
}

/**
 * 回复方案卡（单面卡）：tag 标签 + 话术全文（内部滚动）+ 右下角操作（复制/赞/踩）。
 * "推荐"卡用实心底反白突出；赞/踩用边框变色反馈。按压缩放 0.96，有入场动画。
 * F09-7: reply 为空时显示"本轮不适合"，不可复制/赞/踩，灰色样式。
 *
 * P0-1: 语音改写只负责 STT，API 改写统一用 RewriteState
 * P0-2: 长按手势生命周期：PRESSING → RECORDING → 松手 PROCESSING → IDLE
 * P0-3: 语音权限 UX——允许后提示、拒绝后提示
 * P1-3: 外框高度稳定，禁止 normal ↔ wrapContentHeight 结构级切换
 * P1-5: 更新过期提示文案
 * P1-6: 不永远显示"长按说话修改"
 */

/**
 * P0-4: 卡片内部 variant selector 的内容层标识。
 *
 * - [ORIGINAL]：原始回复（默认）
 * - [DIRECTION]：四方向变体——tag 对应 DirectionCatalog（F/E/X/S）
 */
sealed class CardVariant {
    data object Original : CardVariant()
    data class Direction(val directionTag: String, val directionTitle: String) : CardVariant()
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SchemeCard(
    scheme: Scheme,
    feedback: SchemeFeedback,
    onFeedback: (String, SchemeFeedback) -> Unit,
    onCopy: (Scheme) -> Unit,
    rewriteState: RewriteState? = null,
    onRewrite: (String, String) -> Unit = { _, _ -> },
    onClearRewriteState: (String) -> Unit = {},
    onCancelRewrite: (String) -> Unit = {},
    onUndoRewrite: (String) -> Unit = {},
    onToggleRewriteExpand: (String) -> Unit = {},
    isExpanded: Boolean = false,
    onVoiceRewrite: (String, String) -> Unit = { _, _ -> },
    // P0-4: 四方向变体方案——卡片内部换面
    directionSchemes: List<Scheme> = emptyList(),
    modifier: Modifier = Modifier
) {
    val isEmpty = scheme.reply.isBlank()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "cardScale"
    )

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

    // P0-1: 改写状态——统一使用 RewriteState，不再依赖 VoiceRewriteState.REWRITING
    val isRewriting = rewriteState is RewriteState.Loading
    val rewriteError = (rewriteState as? RewriteState.Error)?.message
    val rewriteDone = rewriteState is RewriteState.Done
    val hasHistory = rewriteDone

    // P0-1/P0-2: 语音改写控制器——只负责 STT，不维护 REWRITING
    // P0-3: 权限结果回调
    var permissionToast by remember { mutableStateOf<String?>(null) }
    val voiceController = rememberVoiceRewriteController(
        schemeTag = scheme.tag,
        onVoiceRewrite = onVoiceRewrite,
        onPermissionGranted = {
            permissionToast = "麦克风权限已开启，请再次长按说话"
        },
        onPermissionDenied = { permanently ->
            permissionToast = if (permanently) {
                "需要麦克风权限才能语音修改，请到设置中开启。仍可点击卡片使用文字调整。"
            } else {
                "需要麦克风权限才能语音修改，仍可点击卡片使用文字调整。"
            }
        }
    )
    val voiceState = voiceController.state
    val isRecording = voiceState == VoiceRewriteState.RECORDING || voiceState == VoiceRewriteState.PROCESSING

    // P0-4: 卡片内部 variant selector——当前选中的变体
    var currentVariant by remember(scheme.tag) { mutableStateOf<CardVariant>(CardVariant.Original) }

    // P0-4: 根据选中变体决定展示内容
    val variant = currentVariant
    val displayScheme = when (variant) {
        CardVariant.Original -> scheme
        is CardVariant.Direction -> {
            directionSchemes.find { it.tag == variant.directionTag }
                ?.takeIf { it.reply.isNotBlank() }
                ?: scheme // fallback to original if direction not available
        }
    }
    val displayReply = displayScheme.reply
    val displayIsEmpty = displayReply.isBlank()

    // P0-4: 有可用的方向变体时才显示 selector
    val hasDirectionVariants = directionSchemes.any { it.reply.isNotBlank() }

    // P1-12: 统一状态推导
    val cardState: SchemeCardState = when {
        isRecording && voiceState == VoiceRewriteState.PROCESSING -> SchemeCardState.Recognizing
        isRecording -> SchemeCardState.Recording
        isRewriting -> SchemeCardState.Rewriting
        rewriteError != null -> SchemeCardState.RewriteError(rewriteError)
        rewriteDone -> SchemeCardState.RewriteDone
        isExpanded -> SchemeCardState.Expanded
        else -> SchemeCardState.Collapsed
    }

    // P0-2: 松手时自动停止录音（PROCESSING 状态等待 final transcript）
    LaunchedEffect(isPressed, voiceState) {
        if (!isPressed && voiceState == VoiceRewriteState.RECORDING) {
            voiceController.stopListening()
        }
    }

    // P0-2: 权限提示自动消失
    LaunchedEffect(permissionToast) {
        if (permissionToast != null) {
            kotlinx.coroutines.delay(2500)
            permissionToast = null
        }
    }

    // P0-1: 录音中或改写中——卡片边框高亮
    val effectiveBorderWidth = if (isRecording || isRewriting) 2f else borderWidth
    val effectiveBorderColor = when {
        isRecording || isRewriting -> Primary
        feedback != SchemeFeedback.NONE -> borderColor
        else -> Border
    }

    // P1-3: 外框高度保持稳定——使用 animateContentSize + 上限，禁止 normal ↔ wrapContentHeight 结构级切换
    Box(
        modifier = modifier
            .width(SchemeCardDimens.CARD_WIDTH_DP.dp)
            .heightIn(min = SchemeCardDimens.CARD_HEIGHT_DP.dp, max = SchemeCardDimens.CARD_MAX_HEIGHT_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.lg)
            .clip(LoveBrainShape.lg)
            .background(cardBg)
            .border(effectiveBorderWidth.dp, effectiveBorderColor, LoveBrainShape.lg)
            .then(if (isEmpty) Modifier else Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    // P0-2: 如果正在录音，点击取消录音
                    if (isRecording) {
                        voiceController.cancel()
                    } else if (isRewriting) {
                        // 改写中点击不做任何事，防止误触
                    } else if (isExpanded) {
                        // P0-4: 已展开时点击切换回原回复变体
                        currentVariant = CardVariant.Original
                        onToggleRewriteExpand(scheme.tag)
                    } else {
                        onToggleRewriteExpand(scheme.tag)
                    }
                },
                onLongClick = {
                    // P0-2: 长按触发语音录音——真实手势生命周期
                    if (!isRewriting && rewriteError == null && !isRecording) {
                        voiceController.startListening()
                    }
                }
            ))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
                .animateContentSize()
        ) {
            // 标签行
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

            // P0-4: variant selector 条——展开时显示在标签下方、正文上方
            // 原回复 | 跟进 | 展开 | 表达 | 转向（只有可用方向才显示）
            if (isExpanded && hasDirectionVariants && !isRecording && !isRewriting && rewriteError == null) {
                // P0-4: 变体选择条——横向滚动，不独占新行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    VariantChip(
                        label = "原回复",
                        isSelected = currentVariant is CardVariant.Original,
                        onClick = { currentVariant = CardVariant.Original }
                    )
                    directionSchemes.forEach { dirScheme ->
                        if (dirScheme.reply.isNotBlank()) {
                            VariantChip(
                                label = dirScheme.title,
                                isSelected = (currentVariant as? CardVariant.Direction)?.directionTag == dirScheme.tag,
                                onClick = {
                                    currentVariant = CardVariant.Direction(dirScheme.tag, dirScheme.title)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(SchemeCardDimens.TAG_TO_BODY_GAP_DP.dp))
            }

            // P1-12: 根据 cardState 渲染正文区域
            when (cardState) {
                is SchemeCardState.Recording, SchemeCardState.Recognizing -> {
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
                                text = if (cardState is SchemeCardState.Recognizing) "识别中…" else "正在录音…",
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
                SchemeCardState.Rewriting -> {
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
                            "正在改写…",
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
                                onClick = { onCancelRewrite(scheme.tag) }
                            ).padding(Spacing.xs)
                        )
                    }
                }
                else -> {
                    // 正常态：话术全文（内部垂直滚动）
                    // P0-4: 使用 displayReply（根据变体选择显示对应内容）
                    if (displayIsEmpty) {
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
                                text = displayReply,
                                color = bodyColor,
                                style = AppTypography.bodyMedium,
                                fontSize = SchemeTextDimens.BODY_FONT_SIZE,
                                lineHeight = SchemeTextDimens.BODY_LINE_HEIGHT
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            // 改写操作区（正文下方展开）——P1-3: 使用 AnimatedVisibility 而非高度模式切换
            // P0-4: 改写选项只在原回复变体下显示（方向变体不支持改写）
            if (rewriteError != null && !isRecording && !isRewriting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        rewriteError,
                        style = AppTypography.labelSmall,
                        color = Error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "重试",
                        style = AppTypography.labelSmall,
                        color = PrimaryDark,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onClearRewriteState(scheme.tag)
                                onToggleRewriteExpand(scheme.tag)
                            }
                        ).padding(Spacing.xs)
                    )
                }
            } else if (isExpanded && !displayIsEmpty && !isRecording && !isRewriting && currentVariant is CardVariant.Original) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    RewriteCommand.ALL_LABELS.take(2).forEach { option ->
                        val (interaction, optScale) = rememberPressScale(0.94f, "optScale$option")
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { scaleX = optScale; scaleY = optScale }
                                .clip(LoveBrainShape.sm)
                                .background(PrimaryLight, LoveBrainShape.sm)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = { onRewrite(scheme.tag, option) }
                                )
                                .padding(vertical = Spacing.xs, horizontal = Spacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                option,
                                style = AppTypography.labelSmall,
                                color = PrimaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    RewriteCommand.ALL_LABELS.drop(2).forEach { option ->
                        val (interaction, optScale) = rememberPressScale(0.94f, "optScale$option")
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { scaleX = optScale; scaleY = optScale }
                                .clip(LoveBrainShape.sm)
                                .background(PrimaryLight, LoveBrainShape.sm)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = { onRewrite(scheme.tag, option) }
                                )
                                .padding(vertical = Spacing.xs, horizontal = Spacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                option,
                                style = AppTypography.labelSmall,
                                color = PrimaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // P0-3: 权限提示（临时显示）
            AnimatedVisibility(
                visible = permissionToast != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Text(
                    text = permissionToast ?: "",
                    style = AppTypography.labelSmall,
                    color = if (voiceController.permissionResult == VoicePermissionResult.GRANTED) PrimaryDark else Error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs)
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            // 操作行：固定右下角（F09-7: 空回复不显示操作按钮）
            if (!isEmpty) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rewriteDone) {
                        Text(
                            "撤销",
                            style = AppTypography.labelSmall,
                            color = PrimaryDark,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onUndoRewrite(scheme.tag) }
                            ).padding(horizontal = Spacing.xs, vertical = Spacing.xs)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    // P1-6: 不永远显示"长按说话修改"——只在未改写/录音时短暂显示
                    // 不再永久占用卡片底部空间
                    // P0-4: 复制当前变体内容（方向变体或原回复）
                    CardActionIcon(
                        icon = R.drawable.ic_copy,
                        desc = "复制",
                        tint = TextSecondary,
                        onClick = { onCopy(displayScheme) }
                    )
                    CardActionIcon(
                        icon = R.drawable.ic_thumb_up,
                        desc = "赞",
                        tint = if (feedback == SchemeFeedback.LIKED) Primary else TextHint,
                        onClick = { onFeedback(scheme.tag, SchemeFeedback.LIKED) }
                    )
                    CardActionIcon(
                        icon = R.drawable.ic_thumb_down,
                        desc = "踩",
                        tint = if (feedback == SchemeFeedback.DISLIKED) Error else TextHint,
                        onClick = { onFeedback(scheme.tag, SchemeFeedback.DISLIKED) }
                    )
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
 * P0-4: 变体选择 Chip——用于卡片内部 variant selector。
 * 紧凑、横向可滚动，选中态高亮。
 */
@Composable
private fun VariantChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.94f, "variantChip_$label")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.sm)
            .background(if (isSelected) Primary else PrimaryLight, LoveBrainShape.sm)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = AppTypography.labelSmall,
            color = if (isSelected) Color.White else PrimaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * 兼容封装：颜色动画状态。
 */
@Composable
private fun animateColorAsStateCompat(
    targetValue: Color,
    label: String
): State<Color> {
    return androidx.compose.animation.animateColorAsState(
        targetValue = targetValue,
        animationSpec = tween(300),
        label = label
    )
}
