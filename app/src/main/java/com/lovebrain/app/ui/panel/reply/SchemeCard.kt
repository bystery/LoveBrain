package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovebrain.app.R
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*

/**
 * 方案卡尺寸常量（：骨架屏共用，值不变；公共对象供同包 ResultArea 引用）。
 */
object SchemeCardDimens {
    const val CARD_WIDTH_DP = 158     // 卡宽（骨架屏与实体卡共用）
    const val CARD_HEIGHT_DP = 150    // 卡高（：骨架屏 166→150 对齐实体，消除跳变）
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
 * 回复方案卡（单面卡）：tag 标签 + 话术全文（内部滚动）+ 右下角操作（复制/赞/踩）。
 * "推荐"卡用实心底反白突出；赞/踩用边框变色反馈。按压缩放 0.96，有入场动画。
 * F09-7: reply 为空时显示"本轮不适合"，不可复制/赞/踩，灰色样式。
 */
/** 卡片内部改写操作选项 — DRY: 统一使用 RewriteCommand.ALL_LABELS */

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
    modifier: Modifier = Modifier
) {
    val isEmpty = scheme.reply.isBlank()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // 选中态缩放动画：按下时缩小到 0.96，松开回弹
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "cardScale"
    )

    // 反馈后高亮：被赞/踩时卡片描边变色
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

    // 四色标签体系已删，统一 Primary 色系；"推荐"用实心底反白突出唯一层级
    val isRecommended = scheme.title == "推荐" && !isEmpty
    val tagColor = if (isRecommended) Color.White else PrimaryDark
    val tagBg = if (isRecommended) Primary else PrimaryLight
    // F09-7: 空回复用灰色样式
    val cardBg = if (isEmpty) SurfaceInset else SurfaceCard
    val bodyColor = if (isEmpty) TextHint else TextPrimary

    // 改写状态展示
    val isRewriting = rewriteState is RewriteState.Loading
    val rewriteError = (rewriteState as? RewriteState.Error)?.message
    val rewriteDone = rewriteState is RewriteState.Done
    // 阻断C修复：hasHistory 独立于 Done——只有实际有撤销历史才显示撤销
    val hasHistory = rewriteDone // Done 意味着有上一版本可撤销

    Box(
        modifier = modifier
            .width(SchemeCardDimens.CARD_WIDTH_DP.dp)
            .then(if (isExpanded || isRewriting || rewriteError != null || hasHistory)
                Modifier.wrapContentHeight() else Modifier.height(SchemeCardDimens.CARD_HEIGHT_DP.dp))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.lg)
            .clip(LoveBrainShape.lg)
            .background(cardBg)
            .border(borderWidth.dp, borderColor, LoveBrainShape.lg)
    // b2-6: 点击卡片时——如果在错误/成功状态，先清状态再展开选项；
    // 如果已展开，收起
    // b2-6: 错误状态下的"重试"按钮应该直接展开选项区让用户重选
    .then(if (isEmpty) Modifier else Modifier.combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {
            onToggleRewriteExpand(scheme.tag)
        },
        onLongClick = {
            // UI 冻结：长按触发语音改写（统一交互模式）
            if (!isRewriting && rewriteError == null) {
                onToggleRewriteExpand(scheme.tag)
            }
        }
    ))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
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

            // F09-7: 空回复显示"本轮不适合"，不可滚动/复制
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // 话术全文：内部垂直滚动（过长可滑动看完整）
                Box(
                    modifier = Modifier
                        .weight(1f, fill = !isExpanded && !isRewriting && rewriteError == null && !hasHistory)
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

            // 改写操作区（正文下方展开）
            if (isRewriting) {
                // 改写中状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
            } else if (rewriteError != null && !isExpanded) {
                // b2-6: 错误状态未展开时显示短提示+重试
                // 重试直接展开选项区，让用户重新选择改写方向
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
                            // b2-6: 重试 = 清错误状态 + 展开选项区
                            onClick = {
                                onClearRewriteState(scheme.tag)
                                onToggleRewriteExpand(scheme.tag)
                            }
                        ).padding(Spacing.xs)
                    )
                }
            } else if (isExpanded && !isEmpty) {
                // 2×2 操作区（小窗容不下时自动变单列）
                // 尝试 2 列排列
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

            Spacer(Modifier.height(Spacing.sm))

            // 操作行：固定右下角（F09-7: 空回复不显示操作按钮）
            if (!isEmpty) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // b2-6: 改写成功后撤销并入现有操作行
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
    // #5：小图标补按压反馈（标准件 0.92 scale + 120ms；一处覆盖复制/赞/踩三图标）
    val (iconInteraction, iconScale) = rememberPressScale(0.92f, "cardActionIconScale")
    Box(
        modifier = Modifier
            .size(Spacing.xxl)
            .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
            .clip(LoveBrainShape.sm)
            .clickable(interactionSource = iconInteraction, indication = null, onClick = onClick)
            .padding(Spacing.sm), // ：padding 移入 clickable 内层，热区外扩至 28dp（与 KDoc 一致）
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
 * 兼容封装：颜色动画状态。
 * 调研依据：Android Compose 官方动画 API。
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
