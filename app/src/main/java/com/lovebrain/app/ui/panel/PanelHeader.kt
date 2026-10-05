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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

/** 面板头部内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object HeaderDimens {
    /**
     * 头部整行高度——旧版 30dp，本轮按用户的界面合同把它从 48 恢复到 30。
     *
     * 之前这一格是"整行 = 全站触摸下限"，于是模式栏被顶成一条厚顶栏，用户给的评语是
     * "UI 1.5/10"。这一行并没有丢掉可点面积：两段切换器仍然各占 1/2 宽 × **整行高**，
     * 齿轮与收起各占一格 24dp 见方——只是不再为了凑 48 把可见的条撑高一倍。
     * 全站那颗下限仍写在 [AppDimens.TOUCH_TARGET_MIN_DP]，页面主体、列表行、弹窗按钮
     * 一寸不动；这里是**明写的例外**，例外只在这一条 30dp 的窗口装饰带上。
     */
    const val ROW_HEIGHT_DP = 30
    const val CONTROL_HEIGHT_DP = 20        // 两段切换/收起按钮的**视觉字形**高度
    const val SEGMENT_INNER_PADDING_DP = 1  // 两段切换器内边距（高亮块间隙）
    const val BORDER_WIDTH_DP = 1           // 细边框宽度

    /** 齿轮与收起的外包盒：旧版 24dp 见方，字形分别 16dp / 20dp */
    const val COLLAPSE_HOTZONE_DP = 24
    /** 齿轮热区外包盒：与收起同一档，字形更小吃 16dp */
    const val SETTINGS_HOTZONE_DP = 24
    const val GEAR_GLYPH_DP = 16
}

/**
 * 面板头部。
 *
 * 当前布局：[齿轮] [回复/谈心 两段切换 weight] [收起按钮]
 * 进攻模式的开关已从 UI 移除（进攻逻辑保留在 VM，无 UI 开关，静默留档）。
 * 齿轮是这一族**唯一**新增的入口：点它把整扇悬浮窗切到设置页（宿主换内容，不是弹窗、不是半屏
 * Sheet、也不跳外部 Activity），所以这里只交一句意图 `onOpenSettings`，页面归宿主画。
 * 传 null 就不画那一格——这颗控件要能在没有依赖容器的情况下被组合（同 `onHeaderDrag` 的接法）。
 * 整行支持拖拽移动面板。
 *
 * 头部**只有两段**（PRODUCT_SPEC 第4节 那句"只『回复/谈心』两段 + 已有收起；设置是小图标工具操作，
 * 不是第三模式"）。第三段原来是「今日锦囊」， 已按用户授权整删，所以这里连 `showPlanPanel`
 * 与 `onPlanVisibility` 两颗参数一起退场——"哪一页在上面"从此只有 `panelMode` 一本账。
 */
@Composable
fun PanelHeader(
    panelMode: Int,
    onModeChange: (Int) -> Unit,
    onCollapse: () -> Unit,
    onHeaderDrag: ((Float, Float) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
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
            // ── 左：齿轮 = 整窗切设置页 ──
            // 版式照旧版这一行：容器 24dp 见方、字形 16dp（用户合同里齿轮就是这颗小尺寸），
            // 与右边收起同一档外包盒。整行的可点面积仍由那两段各占 1/2 宽 × 整行高承担。
            if (onOpenSettings != null) {
                val settingsDescription = stringResource(R.string.settings_title)
                Box(
                    modifier = Modifier
                        .size(HeaderDimens.SETTINGS_HOTZONE_DP.dp)
                        .semantics { contentDescription = settingsDescription }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            // 与收起同一课：自定义 Box.clickable，Material 不替我补角色
                            role = Role.Button,
                            onClick = onOpenSettings
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        // 名字只在上层热区盒声明一次（图标当装饰），否则语义合并后读屏念两遍
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(HeaderDimens.GEAR_GLYPH_DP.dp)
                    )
                }
            }

            // ── 中：《回复/谈心》弹性占满剩余 ──
            // 这一段与下面滑页那两页读写的**是同一颗数**：`panelMode`。
            // 页索引的换算不在这里重写第二遍，用 `PanelPagePager.kt` 里那一对
            // `panelModeToPage` / `panelPageToMode`——点 tab 与左右滑从此同一条路（第12节第2条）。
            // 连时序都是同一颗：那一段高亮的平移与页面那一段过渡都读 `PanelPageMotion.SLIDE_MS`，
            // 所以点一次 tab 只有一条过渡，不会再读成"抖一下"（原始第 20 条的两半之一）。
            ModeSegmentTwo(
                selectedIndex = panelModeToPage(panelMode),
                onSelect = { idx -> onModeChange(panelPageToMode(idx)) },
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
                        // 角色要自己声明：这是自定义 Box.clickable，Material 不会替我补
                        // （面板整屏第一次进仪器量到：热区 48x48 达标、`role=无`）
                        role = Role.Button,
                        onClick = onCollapse
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    // 标签只在上面那个热区 Box 上声明一次。这里再声明一遍，
                    // 语义合并后会拼成「Collapse panel+Collapse panel」，读屏念两遍
                    // （改之前实测到的就是这串）。图标在这颗按钮里是装饰。
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(HeaderDimens.CONTROL_HEIGHT_DP.dp)
                )
            }
        }
    }
}

/**
 * 两段胶囊切换器：回复/谈心；weight 弹性宽度，两段均分（用户要求适应性大小）。
 *
 * 这里曾经的问题是"外层套了 48dp 的盒子、可点击却挂在 20dp 胶囊**里面**的标签上"——
 * 语义树实测可点击节点只有 84x18dp，手指点不到你以为点得到的那块。
 * 现在两层各管一件事：
 * - 视觉层：20dp 高的胶囊底 + 滑动高亮，不接收点击；
 * - 交互层：两段各占 1/2 宽 × 整行高，clickable 与 selected/role 全挂在自己身上。
 * 文字随之从视觉层搬到交互层——它必须跟着"被点的那个节点"走，否则读屏念到的和手指点的是两回事。
 *
 * 第三段（锦囊）随  整删，`segWidth` 从 1/3 换成 1/2；高亮平移仍按段宽算，
 * 所以两段之间不会出现"高亮块走到半格就停住"的那种漂移。
 */
@Composable
private fun ModeSegmentTwo(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        // 时长与页面平移**同一颗主人**（`PanelPageMotion.SLIDE_MS`，基线 v1.1 §6.5）：
        // 这里原来是裸写的 `tween(250)`，页面那一段是 220ms ⇒ 点一次 tab 有两条不同时长的
        // 并行过渡，视觉上读成"抖一下"。两段合一之后一次切换只有一条时序。
        animationSpec = tween(PanelPageMotion.SLIDE_MS, easing = FastOutSlowInEasing),
        label = "modeIndicator"
    )
    val labels = listOf(
        stringResource(R.string.panel_mode_reply),
        stringResource(R.string.panel_mode_counseling)
    )
    Box(
        modifier = modifier
            .height(HeaderDimens.ROW_HEIGHT_DP.dp)
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
                // 旧版这一格的内边距：高亮块与胶囊底之间留 1dp 间隙，20dp 内不重影
                .padding(HeaderDimens.SEGMENT_INNER_PADDING_DP.dp)
        ) {
            // 滑动高亮：按段宽平移（与文字层分开后不再需要减 padding）
            // 去掉高亮块 shadow——20dp 高内 2dp 阴影造成文字视觉偏移/重影
            val segWidth = 1f / 2f
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
        // ── 交互层：每段一整列行高，点击与语义都挂在这列自己身上 ──
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
    //  钉死：两段字号恒定（AppTypography.labelMedium）+ Box 居中（Alignment.Center），
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
 * 双态胶囊开关已随  统一删除（进攻改文字 chip 后无调用方，2026-08-30）。
 * 保留占位注释防误引用；如需双态开关请复用 SetupActivity.MiniSwitch 或文字 chip 语言。
 */
