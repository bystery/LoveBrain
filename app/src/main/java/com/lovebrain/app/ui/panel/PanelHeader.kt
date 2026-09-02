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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.ui.theme.*

/** 面板头部内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object HeaderDimens {
    const val ROW_HEIGHT_DP = 30            // 头部整行高度（含拖拽热区）
    const val CONTROL_HEIGHT_DP = 20        // 三段切换/收起按钮视觉字形统一控件高（KDoc 规格"所有控件高 20dp"）
    const val SEGMENT_INNER_PADDING_DP = 1  // 三段切换器内边距（高亮块间隙）
    const val BORDER_WIDTH_DP = 1           // 细边框宽度
    const val COLLAPSE_HOTZONE_DP = 24      // 收起按钮热区外包盒（触控下限，/ 口径）
}

/**
 * 面板头部。
 *
 * 当前布局：[回复/锦囊/谈心 三段切换 weight] [收起按钮]
 * 主人 2026-08-31：进攻模式的齿轮入口与开关已从 UI 移除（进攻逻辑保留在 VM，
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
            Box(
                modifier = Modifier
                    .size(HeaderDimens.COLLAPSE_HOTZONE_DP.dp)
                    .semantics { contentDescription = "收起面板" }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCollapse
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(HeaderDimens.CONTROL_HEIGHT_DP.dp)
                )
            }
        }
    }
}

/** 三段胶囊切换器：回复/锦囊/谈心；weight 弹性宽度，三段均分（用户要求适应性大小） */
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
    Box(
        modifier = modifier
            .height(HeaderDimens.CONTROL_HEIGHT_DP.dp) // 与头部其他控件同高
            .clip(LoveBrainShape.full)
            .background(SurfaceInset, LoveBrainShape.full)
            .border(HeaderDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.full)
            .padding(HeaderDimens.SEGMENT_INNER_PADDING_DP.dp)
    ) {
        // 滑动高亮：精确用 graphicsLayer 按段宽平移（padding 后内容宽 = 容器宽 - 2dp）
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
        // 三段文字：等宽均分、Box 精确居中、统一 11sp（20dp 高内不被裁切）
        Row(modifier = Modifier.fillMaxSize()) {
            ModeSegmentLabel("回复", selected = selectedIndex == 0, modifier = Modifier.weight(1f)) {
                onSelect(0)
            }
            ModeSegmentLabel("锦囊", selected = selectedIndex == 1, modifier = Modifier.weight(1f)) {
                onSelect(1)
            }
            ModeSegmentLabel("谈心", selected = selectedIndex == 2, modifier = Modifier.weight(1f)) {
                onSelect(2)
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
            .clip(LoveBrainShape.full) // clip 在 clickable 前 → ripple 跟随圆角（用户方案）
            .semantics { this.selected = selected }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
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
