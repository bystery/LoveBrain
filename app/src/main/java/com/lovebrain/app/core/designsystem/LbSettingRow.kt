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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 服务设置行：图标 / 标题 / 说明 / 状态 / 尾部动作。
 *
 * 状态是两个独立旋钮，别把它们当同一个开关：
 * - [dot] 决定画不画那颗点、什么颜色（颜色住在 [LbRowState]，不由调用方交）；
 * - [statusText] 决定要不要在点旁边写那两个字。
 * 只要一颗点就传 `statusText = null`；空白文字不画，不留幽灵文本节点。
 *
 * [leading] 与 [trailing] 是 `@Composable` **槽位**：设计系统不认识 Checkbox、开关这类具体控件，
 * 所以槽里画什么、`contentDescription` 挂什么、热区两轴各多大**都由调用方负责**，
 * 组件不替它垫、也不给它改名。页面把行壳交出来就不再自拼一行；
 * 但把同一枚箭头/开关在三个页面各抄一遍，也仍然是一颗异形。
 *
 * [rowRole] 默认 [Role.Button]（= 旧行为）：整行点下去是"切一个勾选框"的行要交 `Role.Checkbox`。
 * [rowEnabled] 默认 true：false 时行仍然显示（让用户看得见它为什么不能选），但按不动，
 * 并且 `Disabled` 语义挂得上——读屏读得出"按不动"。
 *
 * [subtitle] 空白就不画，与状态槽同一口径。
 * 尾部那颗动作除高度下限之外还有**宽度**下限（见方）：只垫高度时两字标签会在宽度轴不达标。
 *
 * 内缩的分工：**横向内缩归外框**（组卡、浮层、列表容器给），行自己只垫纵向——
 * 行再垫一遍横 pad 就会与外框叠成双重内缩，同组各行的左右边缘对不齐。
 * 只靠行自带横向 padding 与外框留距的调用方，要在自己的外框上把这一层补回去。
 */
@Composable
fun LbSettingRow(
    icon: ImageVector? = null,
    @androidx.annotation.DrawableRes iconRes: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String,
    dot: LbRowState? = null,
    statusText: String? = null,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    rowRole: Role = Role.Button,
    rowEnabled: Boolean = true
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = rowRole,       // 默认 Button = 旧行为；整行点下去是勾选框的那一族交 Role.Checkbox
            enabled = rowEnabled, // 被拒绝的类别：显示出来、按不动，并且读得出"按不动"
            onClick = onClick
        ) else it }

    Row(
        modifier = rowModifier
            // 横向内缩**不在这一行**：它由外框（设置页的组卡、浮层与页面的容器）一处给齐，
            // 行只保留纵向呼吸与分隔。以前行自己再垫一遍横 12，与外框叠成"双重内缩"，
            // 标题就比同一组里别的行多缩一截、左右边缘对不齐。
            // 原先只靠行自带这一层横向 padding 的调用方（捕获页的白卡与名单）已把
            // 内缩改交到各自外框上补回，语义没有塌陷。
            .padding(vertical = Spacing.md)
            .testTag(LbTags.SETTING_ROW),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 行首：三档共用同一个位置（leading 优先，其次两档图标）。
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.md))
        } else if (icon != null) {
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
            // 说明空白就不画：与状态槽同口径，不留幽灵文本节点
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    maxLines = 1
                )
            }
        }
        // 状态槽：一颗点（颜色来自 LbRowState）+ 可选两个字的词，两档各自独立。
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
                    // 这颗"管理"是整行之外的第二个入口：高度与宽度两轴都要垫到下限。
                    .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    // 只垫高度不算"见方"：两字标签（「管理」「撤销」）会在宽度轴不达标。
                    .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .graphicsLayer { scaleX = trailScale; scaleY = trailScale }
                    .clip(LoveBrainShape.md)
                    .clickable(
                        interactionSource = trailInteraction,
                        indication = null,
                        role = Role.Button,   // 尾部动作是按钮，不是整行那个角色
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
        // 尾部槽位。⚠ 这一行是补一条**漏了的调用**，不是新形状：
        // `trailing` 在本文件的签名里（见那颗参数）与本组件自己的 KDoc（"[leading] 与 [trailing] 是
        // @Composable 槽位"）都登记过，`leading` 在本函数行首那一支被调用（`if (leading != null)`），
        // 而 `trailing` 从声明那天起
        // **从来没被调用过一次** —— 传进来的 composable 被静默丢弃。
        // 实到后果（不是推断，是 2026-10-04 全量单测读数）：`CaptureAppsScreen` 那张状态卡的
        // 尾部交的就是这一槽，于是"消息捕获"页画不出那颗总开关，整条
        // "未授予 → 披露 → 明确同意 → 才进系统设置"的授权链在界面上不可达
        // （`HomeStatusCaptureDisclosureTest` 8 条红、`CaptureAppsScreenStatesTest` 1 条红，
        // 两把尺读到的都是 `实到 0 颗`）。
        // 摆放位置 = Row 的最后一颗（行尾），与 trailingText 那一档同一侧；两者互不替换：
        // trailingText 是"设计系统认识的文字动作"，trailing 是"设计系统不认识的那颗控件"。
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * **一行的标签**：整页/全站"这一行在管什么"那几个字的唯一一张脸（`labelLarge` + [TextPrimary]，
 * 一行、超出省略）。
 *
 * 为什么要单独有这一颗：[LbSettingRow] 的标题走 `titleMedium` 粗体，那是**服务设置列表**那一族
 * （首页、捕获页）的脸；悬浮窗那一族紧凑设置行借它，就会在同一页里出现两种行标题粗细
 * ——「悬浮助手」比「持续意图」大一档、重一级。行的层次本来由**组名**与**分隔线**表达，
 * 不由行标题的字阶表达，所以这一族所有行共用这一张脸。
 *
 * 字阶取现成的 [AppTypography.labelLarge]（12sp Medium）+ [TextPrimary]，不新造 TextStyle、
 * 不改全站字阶表。宽度不够时省略号吃掉的是标签自己，不是同一行尾部那颗选择器
 * （调用方给 `weight(1f)`，尾部控件不给权重先量到自己的下限宽）。
 */
@Composable
fun LbRowLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = AppTypography.labelLarge,
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
