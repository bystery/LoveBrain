package com.lovebrain.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.AppDimens
import com.lovebrain.app.ui.theme.AppTypography
import com.lovebrain.app.ui.theme.Border
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.Neutral300
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.PrimaryDark
import com.lovebrain.app.ui.theme.PrimaryLight
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.SurfaceCard
import com.lovebrain.app.ui.theme.SurfaceInset
import com.lovebrain.app.ui.theme.TextHint
import com.lovebrain.app.ui.theme.TextPrimary
import com.lovebrain.app.ui.theme.TextSecondary

// ═════════════════════════════════════════════════════════════
// 首页统一组件集合
// ═════════════════════════════════════════════════════════════

/** 首页页面目的地——根级导航 */
sealed class HomeDestination {
data object Home : HomeDestination()
data object FeedbackCases : HomeDestination()
data object About : HomeDestination()
data object Providers : HomeDestination()
data object Usage : HomeDestination()
data object CaptureApps : HomeDestination()

companion object {
    /** Saver for rememberSaveable */
    val Saver = androidx.compose.runtime.saveable.Saver<HomeDestination, String>(
        save = { it::class.simpleName ?: "Home" },
        restore = { name ->
            when (name) {
                "FeedbackCases" -> FeedbackCases
                "About" -> About
                "Providers" -> Providers
                "Usage" -> Usage
                "CaptureApps" -> CaptureApps
                else -> Home
            }
        }
    )
}
}

/**
 * 首页顶部栏——标题 + 副标题 + 关于入口
 */
@Composable
fun HomeTopBar(
    onNavigateAbout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "LoveBrain",
                style = AppTypography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "帮你更自然地表达",
                style = AppTypography.bodySmall,
                color = TextHint
            )
        }
        // 关于/设置图标
        val (aboutInteraction, aboutScale) = rememberPressScale(0.94f, "aboutBtn")
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = aboutScale; scaleY = aboutScale }
                .clip(LoveBrainShape.full)
                .clickable(
                    interactionSource = aboutInteraction,
                    indication = null,
                    onClick = onNavigateAbout
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "关于",
                tint = TextHint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 悬浮军师状态卡——唯一主卡。
 *
 * 状态 Pill + 状态说明 + 唯一主按钮 + 右上次级隐藏图标。
 */
@Composable
fun AssistantStatusCard(
    statusText: String,
    statusColor: Color,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    onHideClick: (() -> Unit)? = null
) {
    Card(
        shape = LoveBrainShape.xl,
        colors = CardDefaults.cardColors(containerColor = PrimaryLight),
        modifier = Modifier
            .fillMaxWidth()
            .border(AppDimens.BORDER_WIDTH_DP.dp, com.lovebrain.app.ui.theme.PrimarySubtle, LoveBrainShape.xl)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 右上次级隐藏图标（不另起一行）
            if (onHideClick != null) {
                val (hideInteraction, hideScale) = rememberPressScale(0.92f, "hideBtn")
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md)
                        .size(48.dp)
                        .graphicsLayer { scaleX = hideScale; scaleY = hideScale }
                        .clip(LoveBrainShape.full)
                        .clickable(
                            interactionSource = hideInteraction,
                            indication = null,
                            onClick = onHideClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "暂时隐藏浮窗",
                        tint = PrimaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 状态行：标题 + Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "悬浮军师",
                        style = AppTypography.titleLarge,
                        color = PrimaryDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(Spacing.md))
                    // 状态 Pill
                    Box(
                        modifier = Modifier
                            .clip(LoveBrainShape.full)
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Text(
                            statusText,
                            style = AppTypography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                // 状态说明
                Text(
                    description,
                    style = AppTypography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.lg))
                // 唯一主按钮
                val (btnInteraction, btnScale) = rememberPressScale(0.96f, "heroBtn")
                Button(
                    onClick = onButtonClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = Color.White
                    ),
                    shape = LoveBrainShape.md,
                    interactionSource = btnInteraction,
                    modifier = Modifier
                        .height(48.dp)
                        .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                ) {
                    Text(
                        buttonText,
                        style = AppTypography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 分区标题 */
@Composable
fun HomeSectionHeader(title: String) {
    Text(
        title,
        style = AppTypography.titleMedium,
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.sm)
    )
}

/**
 * 快捷功能卡片——统一 HomeActionCard。
 * 标题、说明、图标容器、箭头、按压、禁用状态完全同源。
 */
@Composable
fun HomeActionCard(
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "actionCard_$title")
    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.lg)
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                .size(48.dp)
                .clip(LoveBrainShape.md)
                .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = title,
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextHint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                title,
                style = AppTypography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                subtitle,
                style = AppTypography.labelSmall,
                color = TextHint,
                maxLines = 1
            )
        }
    }
}

/**
 * 服务设置行——统一 HomeSettingRow。
 * 图标、标题、说明、状态、尾部动作。
 */
@Composable
fun HomeSettingRow(
    icon: ImageVector? = null,
    @androidx.annotation.DrawableRes iconRes: Int? = null,
    title: String,
    subtitle: String,
    statusText: String? = null,
    statusColor: Color = Neutral300,
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
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
        // 状态点
        if (statusText != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        // 尾部动作
        if (trailingText != null && onTrailingClick != null) {
            val (trailInteraction, trailScale) = rememberPressScale(0.94f, "trailing_$title")
            Box(
                modifier = Modifier
                    .heightIn(min = 32.dp)
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

/**
 * 使用概览——3 个指标横排。
 * 默认只展示：累计生成、累计花费、采用率。
 */
@Composable
fun UsageSummary(
    totalGenerate: String,
    totalCost: String,
    adoptRate: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (onClick != null) mod.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ) else mod }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            UsageMetric(label = "累计生成", value = totalGenerate, modifier = Modifier.weight(1f))
            UsageMetric(label = "累计花费", value = totalCost, modifier = Modifier.weight(1f))
            UsageMetric(label = "采用率", value = adoptRate, modifier = Modifier.weight(1f), highlight = true)
        }
    }
}

@Composable
private fun UsageMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = AppTypography.titleLarge,
            color = if (highlight) Primary else TextPrimary,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = AppTypography.labelSmall,
            color = TextHint,
            textAlign = TextAlign.Center
        )
    }
}
