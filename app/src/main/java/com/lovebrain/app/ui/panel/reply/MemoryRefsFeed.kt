package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryRef

/**
 * 「本轮参考」那几条记忆的**清单本体与逐条纠正入口**。
 *
 * 它为什么是一个独立的所有者：变化理由是记忆条目的**展示与纠正词表**——kind 标签
 * （画像/场景/事项/经验）、初始折叠条数与那行「更多 N 条」、行尾 `⋯` 里的五项
 * （不对 / 结束 / 暂时别提 / 不是她 / 撤销纠正）。这些跟方案卡的内容、跟风格/方向切换
 * 都不是同一件会一起变的事。
 * 指导书 §6.4 要的是「内容与 modal host 分离」，所以这一份**只发意图**：
 * 浮层的开合与草稿归 [MemoryCorrectionFlow]（它自己渲染 [MemoryCorrectionFlowHost]），
 * 落盘动作归调用方传进来的回调；本文件只回答"这一条记忆怎么念出来、从哪里能改它"。
 * 列表的展开状态不搬进来——`showRefs` 由 [ResultArea] 的成功档持有（`remember(generationRoundId)`），
 * 因为 toggle 来自 ⋯ 菜单、渲染来自这一份，状态得住在两者之上才换得动。
 *
 * 渲染形状（搬家前就这么写的）：MemoryRefsSection 在主 Column 中按正常文档流渲染，
 * 默认 showRefs=false 时 AnimatedVisibility 不占高度，用户主动展开后才正常增加信息高度，
 * 不覆盖方案卡或切换器。
 */
@Composable
internal fun MemoryRefsSection(
    memoryRefs: List<MemoryRef>,
    showRefs: Boolean,
    onCorrection: (String, CorrectionAction, String, com.lovebrain.app.model.MuteDuration) -> Unit,
    onUndoCorrection: (String) -> Unit,
    correctionFlow: MemoryCorrectionFlow
) {
    AnimatedVisibility(
        visible = showRefs && memoryRefs.isNotEmpty(),
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .clip(LoveBrainShape.md)
                .background(SurfaceInset, LoveBrainShape.md)
                .padding(Spacing.md)
        ) {
            val maxInitialRefs = 5
            var showAllRefs by remember { mutableStateOf(false) }
            val displayRefs = if (showAllRefs) memoryRefs else memoryRefs.take(maxInitialRefs)
            displayRefs.forEachIndexed { index, ref ->
                if (index > 0) Spacer(Modifier.height(Spacing.sm))
                MemoryRefItem(
                    ref = ref,
                    onCorrection = onCorrection,
                    onUndoCorrection = onUndoCorrection,
                    correctionFlow = correctionFlow
                )
            }
            if (memoryRefs.size > maxInitialRefs && !showAllRefs) {
                Spacer(Modifier.height(Spacing.sm))
                val (moreInteraction, moreScale) = rememberPressScale(0.96f, "moreRefsScale")
                Text(
                    "更多 ${memoryRefs.size - maxInitialRefs} 条",
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    modifier = Modifier
                        .graphicsLayer { scaleX = moreScale; scaleY = moreScale }
                        .clickable(interactionSource = moreInteraction, indication = null) {
                            showAllRefs = true
                        }
                        .padding(Spacing.xs)
                )
            }
        }
    }
}

/**
 * 单条记忆引用 — 只显示正文 + ⋯ 菜单。
 * ⋯ 菜单内提供：不对 / 结束 / 暂时别提 / 不是她 / 撤销。
 */
@Composable
internal fun MemoryRefItem(
    ref: MemoryRef,
    onCorrection: (String, CorrectionAction, String, com.lovebrain.app.model.MuteDuration) -> Unit,
    onUndoCorrection: (String) -> Unit,
    /** §6.4：浮层的开关与草稿都归持有者，这一行只发意图 */
    correctionFlow: MemoryCorrectionFlow
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // kind 标签（紧凑）
            val kindLabel = when (ref.kind) {
                com.lovebrain.app.model.MemoryKind.PROFILE -> "画像"
                com.lovebrain.app.model.MemoryKind.SCENE -> "场景"
                com.lovebrain.app.model.MemoryKind.ONGOING -> "事项"
                com.lovebrain.app.model.MemoryKind.LESSON -> "经验"
            }
            Text(
                text = "[$kindLabel]",
                style = AppTypography.labelSmall,
                color = TextHint,
                modifier = Modifier.padding(end = Spacing.xs)
            )
            // 记忆正文
            Text(
                text = ref.text,
                style = AppTypography.labelSmall,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // ⋯ 菜单入口
            // §6.5 :531：可点击盒子必须 ≥48dp。这里以前是 `Box(size = 28.dp).clickable{}`——
            // 字形 28dp 没问题，但点击挂在 28dp 的盒子上就是"点不到"（新守卫
            // `MemoryCorrectionFlowTest` 实量到 28x28dp）。
            // 形状与结果级那颗 utility trigger 一致：外层 48dp 承担点击，内层 28dp 只管字形。
            val (menuInteraction, menuScale) = rememberPressScale(0.92f, "refMenuScale")
            Box(
                modifier = Modifier
                    .size(AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .clickable(interactionSource = menuInteraction, indication = null) {
                        menuOpen = !menuOpen
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { scaleX = menuScale; scaleY = menuScale }
                        .clip(LoveBrainShape.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⋯",
                        style = AppTypography.labelLarge,
                        color = TextHint
                    )
                }
            }
        }

        // 纠正菜单——DropdownMenu 浮层，不改变结果区 layout height
        androidx.compose.material3.DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier
                .clip(LoveBrainShape.md)
                .background(SurfaceCard)
        ) {
            // "不对"——弹出输入框让用户输入正确内容
            CorrectionDropdownItem("不对", "标记为错误内容") {
                menuOpen = false
                correctionFlow.requestWrong(ref.id)
            }
            CorrectionDropdownItem("结束", "这件事已结束") {
                onCorrection(ref.id, CorrectionAction.FINISHED, "", com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE)
                menuOpen = false
            }
            // "暂时别提"——展开时长选择子菜单
            CorrectionDropdownItem("暂时别提", "暂停作为续聊素材") {
                menuOpen = false
                correctionFlow.requestMute(ref.id)
            }
            CorrectionDropdownItem("不是她", "归属错误，暂时隔离") {
                onCorrection(ref.id, CorrectionAction.WRONG_PERSON, "", com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE)
                menuOpen = false
            }
            CorrectionDropdownItem("撤销纠正", "恢复可信注入") {
                onUndoCorrection(ref.id)
                menuOpen = false
            }
        }

    }
}

/** DropdownMenu 纠正菜单项——浮层内文字行 */
@Composable
private fun CorrectionDropdownItem(
    label: String,
    desc: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "correctionDropdownScale")
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
