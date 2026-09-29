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
 *
 * **§6.1 把"行"这一族归并进来时补的三个旋钮**（每个都是"旧签名表达不了"，不是给页面开风格口子）：
 * - `leading`：行首原来只有 `icon` / `iconRes` 两个入口，它们只收 `ImageVector` 与 drawable id。
 *   捕获行那一颗 `Checkbox`（"这一行授权了没有"唯一的视觉来源）与纠正行那颗跟着 enum 走的
 *   类型标签胶囊都塞不进去；把它们留在页面里、只把行壳交出来，出来的还是一颗异形。
 *   槽位是 `@Composable`，但**里面画什么仍由调用方负责**——组件不认识 Checkbox，也不认识纠正记录。
 * - `rowRole`：默认 [Role.Button]，与旧行为逐字相同。捕获行整行点下去是"切一个勾选框"，
 *   不是"打开一个按钮"；角色是读屏那笔账（§6.5 :532），拿不到这个旋钮就得为那一页再画一颗行。
 * - `rowEnabled`：默认 true = 旧行为。捕获页要把"被二次拒绝的类别"显示出来（让用户看得见
 *   它为什么不能选），旧签名只能靠"不传 onClick"表达，那会把整行变成哑行——
 *   连"现在按不动"都读不出来，因为 `Disabled` 语义根本不挂。
 *
 * **另外两处顺手对齐的是热区与空白，不是外观**：
 * - 说明槽改成与状态槽同一口径：`subtitle` 空白就不画。旧写法永远画一颗文本，
 *   于是纠正中心里"没有补正内容"的那一条要多出一行空白；而本组件的状态槽早就写着
 *   "空白不许留下一颗幽灵文本节点"（`LbSettingRowStateTest` 第二格），两槽同规矩。
 * - 尾部那颗动作除高度下限之外补**宽度**下限（见方）。旧实现只垫高度，
 *   两字标签会量出 38x48dp——同一个教训在 [LbTextAction] 上写过一遍
 *   （"只垫高度不够，短标签会量出 40x48dp"）。§6.5 :531 要的是两轴都不小于下限。
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
    onClick: (() -> Unit)? = null,
    rowRole: Role = Role.Button,
    rowEnabled: Boolean = true
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = rowRole,       // §6.5 :532；默认 Button = 旧行为，捕获行那一族交 Checkbox
            enabled = rowEnabled, // 被拒绝的类别：显示出来、按不动，并且读得出"按不动"
            onClick = onClick
        ) else it }

    Row(
        modifier = rowModifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .testTag(LbTags.SETTING_ROW),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 行首：三档共用同一个位置（leading 优先，其次两档图标）。
        // 图标那两条分支一个字没动——归并之前它们就是唯一入口，之后仍是绝大多数行的画法。
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
            // 说明空白就不画：与下面状态槽同一口径（幽灵节点那笔账写在 LbSettingRowStateTest）
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    maxLines = 1
                )
            }
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
                    // 高度垫够了还不算"见方"：两字标签（「管理」「撤销」）以前量得出 38x48dp。
                    // 同一句话写在 [LbTextAction] 上（"只垫高度不够"），这里补齐第二轴。
                    .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .graphicsLayer { scaleX = trailScale; scaleY = trailScale }
                    .clip(LoveBrainShape.md)
                    .clickable(
                        interactionSource = trailInteraction,
                        indication = null,
                        role = Role.Button,   // §6.5 :532
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
