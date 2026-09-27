package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.Success
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary

/**
 * 「进行中事项」那张折叠卡——方案卡下方的一块次级信息（ongoing 可视化）。
 *
 * 它为什么是一个独立的所有者：变化理由是**事项状态的词表与折叠形状**——
 * 「已完成 ✓ / 已取消 ✗ / 其余 …」这三档、展开与收起那两个字、`state` 那行补充说明画不画。
 * 方案卡改版、记忆条目换词表、菜单换项都不会动这里；反过来，它长成什么样
 * 也不该拖着整个结果区一起改。开合状态是卡片自己的，当前也不需要跨屏持有。
 */
@Composable
internal fun OngoingSection(items: List<com.lovebrain.app.model.OngoingItem>) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(SurfaceInset)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("进行中事项", color = TextSecondary, style = AppTypography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "收起" else "展开",
                color = TextHint,
                style = AppTypography.labelSmall
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
                items.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(Spacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, color = TextPrimary, style = AppTypography.bodySmall, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = when (item.status) {
                                "已完成" -> "✓"
                                "已取消" -> "✗"
                                else -> "…"
                            },
                            color = if (item.status == "已完成") Success else TextHint,
                            style = AppTypography.labelSmall
                        )
                    }
                    if (item.state.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(item.state, color = TextHint, style = AppTypography.labelSmall)
                    }
                }
            }
        }
    }
}
