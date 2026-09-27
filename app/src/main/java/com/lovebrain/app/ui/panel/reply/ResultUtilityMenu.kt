package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.model.MemoryRef

/**
 * 结果区的**低频操作菜单**——右上角那颗 `⋯` 和它的 DropdownMenu。
 *
 * 它为什么是一个独立的所有者：变化理由是"菜单里放哪几项低频动作"（记录实际发送 /
 * 仅看本轮 / 记忆纠正中心 / 本轮参考 toggle），不是"结果内容怎么排"。指导书 §6.4 给
 * 这一屏的原话是「ResultArea 只负责结果内容，不再同时承载菜单…」「"⋯"菜单只放低频次操作」；
 * 上一格「记入知识库」从菜单里退场、改由主操作区承担，动的就是这一处。
 * 开合仍挂在这一份里（`menuOpen`）：这一支只有**项目列表**在变，没有草稿、没有回调编排，
 * 还不需要 state holder——真需要那两件事的（暂停时长、标记为错误的草稿）住在
 * [MemoryCorrectionFlow] 那边。
 * 热区数值仍读 [ResultDimens.UTILITY_HITBOX_DP]：那颗 48dp 的静态合同
 * （`ProductionUiContractTest`）把它钉在 ResultArea.kt 里，同一个数再抄一份就成了两个真值。
 *
 * 形状（搬家前就这么写的，原样留着）：右上角轻量 ⋯ trigger + DropdownMenu，
 * 不增加结果区纵向高度；菜单里 memoryRefs 非空时才有"本轮参考"展开/收起 toggle。
 * 只负责：⋯ trigger、DropdownMenu、toggle 事件回调——
 * MemoryRefsSection 由 ResultArea 在主 Column 中渲染（现在在 [MemoryRefsFeed.kt]）。
 * 目标：功能一直存在，但没有"一个功能一整行"。
 * 通过 modifier = Modifier.align(TopEnd) 定位为 overlay，不参与 Column measurement。
 */
@Composable
internal fun ResultUtilityTrigger(
    memoryRefs: List<MemoryRef>,
    showRefs: Boolean,
    onToggleRefs: () -> Unit,
    // 仅看本轮开关
    onlyThisRound: Boolean = false,
    onToggleOnlyThisRound: () -> Unit = {},
    // 记录实际发送
    onRecordSent: () -> Unit = {},
    hasResult: Boolean = false,
    // 记忆纠正中心
    onShowCorrectionCenter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    // 视觉仍是 28dp 图标，但可点击盒子必须 ≥48dp——这是自定义 Box.clickable，
    // Material 不会帮忙补足触摸热区。
    Box(
        modifier = modifier
    ) {
        // ⋯ trigger
        val (triggerInteraction, triggerScale) = rememberPressScale(0.92f, "resultUtilityTriggerScale")
        val menuDescription = stringResource(com.lovebrain.app.R.string.panel_result_menu)
        Box(
            modifier = Modifier
                .size(ResultDimens.UTILITY_HITBOX_DP.dp)
                .semantics { contentDescription = menuDescription }
                // :532 那一栏：点开下拉菜单的那颗要报 `DropdownList`，
                // 不是"没有角色"。实量 48x48dp **尺寸早就够**，缺的只是角色——
                // 上一格量这一屏时只判了热区与名字，没判角色，所以它一直漏着（坑表 96）。
                .clickable(
                    interactionSource = triggerInteraction,
                    indication = null,
                    role = Role.DropdownList,
                    onClick = { menuOpen = !menuOpen }
                ),
            contentAlignment = Alignment.Center
        ) {
            // 字形保持 28dp，热区 48dp
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { scaleX = triggerScale; scaleY = triggerScale }
                    .clip(LoveBrainShape.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⋯",
                    style = AppTypography.labelLarge,
                    color = TextHint
                )
            }
        }

        // DropdownMenu 浮层——不改变结果区 layout height
        androidx.compose.material3.DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier
                .clip(LoveBrainShape.md)
                .background(SurfaceCard)
        ) {
            // "记入知识库"已作为有结果时的主操作按钮展示（ReplyPrimaryActions），
            // ⋯ 菜单不再重复提供——消除双入口和旧设计残留。

            // 记录实际发送
            if (hasResult) {
                UtilityMenuItem(
                    label = "记录实际发送",
                    desc = "确认你已发送的版本"
                ) {
                    menuOpen = false
                    onRecordSent()
                }
            }

            // 仅看本轮
            UtilityMenuItem(
                label = if (onlyThisRound) "✓ 仅看本轮" else "仅看本轮",
                desc = "排除旧记忆，只看本轮输入"
            ) {
                menuOpen = false
                onToggleOnlyThisRound()
            }

            // 记忆纠正中心
            UtilityMenuItem(
                label = "记忆纠正中心",
                desc = "查看和撤销已纠正的记忆"
            ) {
                menuOpen = false
                onShowCorrectionCenter()
            }

            // memoryRefs 非空时包含"本轮参考" toggle
            if (memoryRefs.isNotEmpty()) {
                UtilityMenuItem(
                    label = if (showRefs) "收起本轮参考" else "本轮参考 ${memoryRefs.size}",
                    desc = "查看本轮注入的记忆"
                ) {
                    menuOpen = false
                    onToggleRefs()
                }
            }
        }
    }
}

/** DropdownMenu utility 菜单项 */
@Composable
private fun UtilityMenuItem(
    label: String,
    desc: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "utilityMenuScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AppTypography.labelSmall,
            color = PrimaryDark,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            desc,
            style = AppTypography.labelSmall,
            color = TextHint
        )
    }
}
