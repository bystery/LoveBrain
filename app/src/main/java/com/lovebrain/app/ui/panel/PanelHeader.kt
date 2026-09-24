package com.lovebrain.app.ui.panel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.ui.theme.*

/** 面板头部内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object HeaderDimens {
    const val ROW_HEIGHT_DP = 48            // 头部整行高度——等于无障碍触摸区下限，见 MIN_TOUCH_TARGET_DP
    const val CONTROL_HEIGHT_DP = 20        // 三段切换/收起按钮的**视觉字形**高度
    const val BORDER_WIDTH_DP = 1           // 细边框宽度

    /**
     * 无障碍触摸区下限。
     *
     * 图标/文字仍然按 CONTROL_HEIGHT_DP 画小，但可点击盒子必须 ≥48dp。
     * 旧实现把 20dp 的胶囊和 24dp 的收起盒子直接当热区，
     * 而这是自定义 Box.clickable，不会由 Material 自动补齐触摸区。
     */
    const val MIN_TOUCH_TARGET_DP = 48
    /** 收起按钮热区外包盒——满足 MIN_TOUCH_TARGET_DP */
    const val COLLAPSE_HOTZONE_DP = MIN_TOUCH_TARGET_DP
}

/**
 * 面板头部。
 *
 * 当前布局：[回复/锦囊/谈心 三段切换 weight] [收起按钮]
 * 进攻模式的齿轮入口与开关已从 UI 移除（进攻逻辑保留在 VM，
 * 无 UI 开关，静默留档）；头部仅剩三段切换与收起。
 * 整行支持拖拽移动面板。
 */
@Composable
fun PanelHeader(
    panelMode: Int,
    onModeChange: (Int) -> Unit,
    showPlanPanel: Boolean,
    onPlanVisibility: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onHeaderDrag: ((Float, Float) -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderDimens.ROW_HEIGHT_DP.dp)
            .then(
                if (onHeaderDrag != null) Modifier.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onHeaderDrag(dragAmount.x, dragAmount.y)
                    }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── 左：《回复/锦囊/谈心》弹性占满剩余 ──
            ModeSegmentThree(
                selectedIndex = when {
                    panelMode == 1 -> 2       // 谈心
                    showPlanPanel -> 1        // 锦囊
                    else -> 0                 // 回复
                },
                onSelect = { idx ->
                    when (idx) {
                        0 -> { onModeChange(0); onPlanVisibility(false) }
                        1 -> { onModeChange(0); onPlanVisibility(true) }
                        2 -> { onModeChange(1) }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md)
            )

            // ── 右：收起按钮 ──
            val collapseDescription = stringResource(R.string.panel_collapse)
            Box(
                modifier = Modifier
                    .size(HeaderDimens.COLLAPSE_HOTZONE_DP.dp)
                    .semantics { contentDescription = collapseDescription }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCollapse
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = collapseDescription,
                    tint = TextSecondary,
                    modifier = Modifier.size(HeaderDimens.CONTROL_HEIGHT_DP.dp)
                )
            }
        }
    }
}

/**
 * 三段胶囊切换器：回复/锦囊/谈心；weight 弹性宽度，三段均分（用户要求适应性大小）。
 *
 * 这里曾经的问题是"外层套了 48dp 的盒子、可点击却挂在 20dp 胶囊**里面**的标签上"——
 * 语义树实测可点击节点只有 84x18dp，手指点不到你以为点得到的那块。
 * 现在两层各管一件事：
 * - 视觉层：20dp 高的胶囊底 + 滑动高亮，不接收点击；
 * - 交互层：三段各占 1/3 宽 × 整行 48dp 高，clickable 与 selected/role 全挂在自己身上。
 * 文字随之从视觉层搬到交互层——它必须跟着"被点的那个节点"走，否则读屏念到的和手指点的是两回事。
 */
@Composable
private fun ModeSegmentThree(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "modeIndicator"
    )
    val labels = listOf(
        stringResource(R.string.panel_mode_reply),
        stringResource(R.string.panel_mode_suggest),
        stringResource(R.string.panel_mode_counseling)
    )
    Box(
        modifier = modifier
            .height(HeaderDimens.MIN_TOUCH_TARGET_DP.dp)
    ) {
        // ── 视觉层：胶囊底 + 高亮，垂直居中，不吃点击 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .height(HeaderDimens.CONTROL_HEIGHT_DP.dp) // 视觉字形高度不变
                .clip(LoveBrainShape.full)
                .background(SurfaceInset, LoveBrainShape.full)
                .border(HeaderDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.full)
        ) {
            // 滑动高亮：按段宽平移（与文字层分开后不再需要减 padding）
            // 去掉高亮块 shadow——20dp 高内 2dp 阴影造成文字视觉偏移/重影
            val segWidth = 1f / 3f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(segWidth)
                    .graphicsLayer {
                        translationX = indicatorOffset * size.width
                    }
                    .background(Primary, LoveBrainShape.full)
            )
        }
        // ── 交互层：每段一整列 48dp，点击与语义都挂在这列自己身上 ──
        Row(modifier = Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                ModeSegmentLabel(
                    label,
                    selected = selectedIndex == index,
                    modifier = Modifier.weight(1f)
                ) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun ModeSegmentLabel(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    //  钉死：三段字号恒定（AppTypography.labelMedium）+ Box 居中（Alignment.Center），
    // 仅颜色随选中态变（防回归，账本-实况差异留档 PART15 -③）
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(LoveBrainShape.full)
            .semantics { this.selected = selected }
            // role 交给 clickable 自己声明：TalkBack 念成「标签」而不是一堆普通按钮
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            // 头部文字规格统一：11sp、深黑 TextPrimary、Medium
            // includeFontPadding=false：去掉中文字体自带上下留白 → 文字垂直居中不再偏下（实测问题）
            color = if (selected) Color.White else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = AppTypography.labelMedium.copy(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}

/**
 * 双态胶囊开关已随 C3-A 统一删除（进攻改文字 chip 后无调用方，2026-08-30）。
 * 保留占位注释防误引用；如需双态开关请复用 SetupActivity.MiniSwitch 或文字 chip 语言。
 */
