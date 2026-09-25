package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 服务设置行——统一 `LbSettingRow`（§6.1 表里的 `LbSettingRow` 那一行）。
 * 图标、标题、说明、状态、尾部动作。
 *
 * **这一版的"状态"槽才是通的。**旧签名是
 * `(statusText: String?, statusColor: Color = Neutral300)`，而组件里只写了
 * `if (statusText != null) { 画一颗 6dp 的点 }`——**statusText 的值从来没被画出来过**。
 * 于是 `HomeScreen` 那两行认真算出来的 `R.string.home_on` / `home_off`
 * （"开"/"关"）解析完就被丢掉；供应商行更离谱，传的是 `statusText = ""`，
 * 意思其实是"我只要一颗点"。两颗行的真实意图挤在一个参数上，其中一个还没接。
 *
 * 现在拆成两个旋钮：`dot` 决定画不画点、什么颜色（颜色住在 [LbRowState]，不由调用方交），
 * `statusText` 决定要不要在点旁边写那两个字。
 */
@Composable
fun LbSettingRow(
    icon: ImageVector? = null,
    @androidx.annotation.DrawableRes iconRes: Int? = null,
    title: String,
    subtitle: String,
    dot: LbRowState? = null,
    statusText: String? = null,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ) else it }

    Row(
        modifier = rowModifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .testTag(LbTags.SETTING_ROW),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(LoveBrainShape.md)
                    .background(SurfaceInset),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Spacing.md))
        } else if (iconRes != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(LoveBrainShape.md)
                    .background(SurfaceInset),
                contentAlignment = Alignment.Center
            ) {
                Icon(painter = painterResource(iconRes), contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Spacing.md))
        }
        // 标题 + 说明
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = AppTypography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                subtitle,
                style = AppTypography.labelSmall,
                color = TextHint,
                maxLines = 1
            )
        }
        // 状态槽：一颗点（颜色来自 LbRowState）+ 可选两个字的词。
        // 词以前根本不在这里画——见函数 KDoc 那段。
        if (dot != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dot.color)
                    .testTag(LbRowTags.DOT)
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        if (!statusText.isNullOrBlank()) {
            Text(
                statusText,
                style = AppTypography.labelSmall,
                color = TextHint
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        // 尾部动作
        if (trailingText != null && onTrailingClick != null) {
            val (trailInteraction, trailScale) = rememberPressScale(0.94f, "trailing_$title")
            Box(
                modifier = Modifier
                    // 32 → 48：§6.5 :531 要所有 clickable ≥48×48。这颗"管理"是整行之外
                    // 唯一另一个入口，32dp 是 `LbSettingRowStateTest` 那把尺量出来的。
                    .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .graphicsLayer { scaleX = trailScale; scaleY = trailScale }
                    .clip(LoveBrainShape.md)
                    .clickable(
                        interactionSource = trailInteraction,
                        indication = null,
                        onClick = onTrailingClick
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    trailingText,
                    style = AppTypography.labelMedium,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
