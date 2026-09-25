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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LbStatusBadge
import com.lovebrain.app.core.designsystem.LbRowState
import com.lovebrain.app.core.designsystem.LbRowTags
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Neutral300
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.core.designsystem.PrimarySubtle
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary

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
 * 首页自动化锚点（§6.2 四段结构 + §6.5 视觉基线都靠它定位）。
 *
 * 为什么不按文案查：那四段的判据是**结构**（谁在第几段、一颗还是两颗主按钮、
 * 三等分是不是真的三等分），拿中文当锚点的话，改一句文案就把结构守卫弄红，
 * 而结构其实没动——那正是本仓库反复写过的"文字会变，tag 不会"。
 */
object LbHomeTags {
    const val SECTION = "lb_home_section"
    const val ABOUT = "lb_home_about"
    const val STATUS_CARD = "lb_home_status_card"
    const val PRIMARY_BUTTON = "lb_home_primary_button"
    const val HIDE_BUTTON = "lb_home_hide_button"
    const val ACTION_CARD = "lb_home_action_card"
    const val SETTING_ROW = "lb_home_setting_row"
    const val METRIC_CELL = "lb_home_metric_cell"
}

/**
 * 首页顶部那颗 About 入口。
 *
 * 它从 `core/designsystem/LbTopBar` 里搬回来：尾部动作是**页面**的决定
 * （首页是 About，二级页是返回），而"关于"这个锚点也只对这一页有意义。
 */
@Composable
fun HomeAboutEntry(onNavigateAbout: () -> Unit) {
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
            )
            .testTag(LbHomeTags.ABOUT),
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

/**
 * 悬浮军师状态卡——唯一主卡（§6.2 首页四段里的第 2 段）。
 *
 * 交进来的是**一份** `AdvisorStatus`，不是 (statusText, statusColor) 两个参数：
 * 旧签名允许调用方把"运行中"配成 Neutral300，编译器不拦、判据也不在一处。
 * 现在文案与颜色都由 `LbStatus` 那一张表决定，这一颗组件只负责摆。
 */
@Composable
internal fun AssistantStatusCard(
    status: AdvisorStatus,
    onButtonClick: () -> Unit,
    onHideClick: (() -> Unit)? = null
) {
    val description = stringResource(status.descriptionRes)
    val buttonText = stringResource(status.buttonRes)
    Card(
        shape = LoveBrainShape.xl,
        colors = CardDefaults.cardColors(containerColor = PrimaryLight),
        modifier = Modifier
            .fillMaxWidth()
            .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.xl)
            .testTag(LbHomeTags.STATUS_CARD)
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
                        )
                        .testTag(LbHomeTags.HIDE_BUTTON),
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
                    // 状态胶囊：配方（状态色 15% 底 + 状态色字 + 读屏语义）在 LbStatusBadge 里，
                    // 这里不再自己 `statusColor.copy(alpha = 0.15f)` 画一遍。
                    LbStatusBadge(status = status.badge)
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
                        .testTag(LbHomeTags.PRIMARY_BUTTON)
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
