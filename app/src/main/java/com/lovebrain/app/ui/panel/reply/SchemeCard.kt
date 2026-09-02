package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 */
@Composable
fun SchemeCard(
    scheme: Scheme,
    feedback: SchemeFeedback,
    onFeedback: (String, SchemeFeedback) -> Unit,
    onCopy: (Scheme) -> Unit,
    modifier: Modifier = Modifier
) {
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
    val isRecommended = scheme.title == "推荐"
    val tagColor = if (isRecommended) Color.White else PrimaryDark
    val tagBg = if (isRecommended) Primary else PrimaryLight

    // 单面卡：标签 + 话术全文（内部滚动）+ 右下角操作
    Box(
        modifier = modifier
            .width(SchemeCardDimens.CARD_WIDTH_DP.dp)
            .height(SchemeCardDimens.CARD_HEIGHT_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.lg)
            .clip(LoveBrainShape.lg)
            .background(SurfaceCard)
            .border(borderWidth.dp, borderColor, LoveBrainShape.lg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* 单面卡：操作走右下角按钮，卡片仅按压反馈 */ }
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
            // 标签行（调节2：无 ★ 推荐徽标）
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

            // 话术全文：内部垂直滚动（过长可滑动看完整）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = scheme.reply,
                    color = TextPrimary,
                    style = AppTypography.bodyMedium,
                    fontSize = SchemeTextDimens.BODY_FONT_SIZE,
                    lineHeight = SchemeTextDimens.BODY_LINE_HEIGHT
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            // 操作行：固定右下角（视觉 20dp + 外圈 padding 扩热区至 28dp）
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

/** 卡片操作小图标：视觉 20dp，点击热区外扩至 28dp（触控下限友好） */
@Composable
private fun CardActionIcon(
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
