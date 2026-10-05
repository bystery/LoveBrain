package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbSection
import com.lovebrain.app.core.designsystem.LbSectionFold
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.Success
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary

/**
 * 「进行中事项」那张折叠卡——方案卡下方的一块次级信息（ongoing 可视化）。
 *
 * 它为什么是一个独立的所有者：变化理由是**事项状态的词表与条目排布**——
 * 「已完成 ✓ / 已取消 ✗ / 其余 …」这三档、`state` 那行补充说明画不画。
 * 方案卡改版、记忆条目换词表、菜单换项都不会动这里。开合状态是卡片自己的，
 * 当前也不需要跨屏持有。
 *
 * **标题与折叠入口不归这一页**（第6节第1条 表第 3 行「不允许每页另造标题样式」）：
 * 归并之前这行标题写的是 `labelMedium` + `TextSecondary`（本机实量比设计系统那颗矮一档），
 * 折叠入口是页面自己画的 `Row + clickable + AnimatedVisibility`，实测 **360x41dp**、
 * 没有 `Role.Button`、也没有 `stateDescription`——读屏只念得出那两个**内联中文**「展开 / 收起」，
 * 英文环境下照样念中文。现在这三件事由 [LbSection] 的可折叠档一次给定
 * （热区下限、角色、状态公告走全站那两条 `state_*` 资源）。
 *
 * 这一页留三样：那两条状态词（交进 `fold.label` 槽里，**不进**组件的实参，
 * 于是文案预算那两栏一笔都不用换抽屉）、`SurfaceInset` 那层卡底（表里这一行没规定"分区必须长在卡里"，
 * 首页三段就没有）、以及上面那套事项排布。
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
        LbSection(
            title = "进行中事项",
            fold = LbSectionFold(
                expanded = expanded,
                onToggle = { expanded = !expanded },
                label = {
                    Text(
                        text = if (expanded) "收起" else "展开",
                        color = TextHint,
                        style = AppTypography.labelSmall
                    )
                }
            ),
            content = {
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
        )
    }
}
