package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard

/**
 * 悬浮窗那一族设置行的**共同容器**：`fillMaxWidth` + 圆角 + 卡底 + 描边 + 内边距，只写这一次。
 *
 * 为什么要有这一颗（§11.1「各项用共同标签、辅助文字、分隔和展开规则」）：三格以前各自抄一遍
 * 同一条链，抄出两处真实漂移——
 * · 知识库那一格的描边写的是硬编码的 `0xFFE0E0E6`，不是族里那颗 [Border]，同一页上两张卡边色不同；
 * · 内边距那一档要靠"记得抄对"才一致，改一族就得同时改三个文件。
 * 现在容器只有一个主人，页面只交外面那条 `Modifier`（调用方的 `modifier` 排在最前，
 * 宿主给的对齐与权重仍然生效）。
 *
 * 数值一个都不新造：描边宽度取 [AppDimens.BORDER_WIDTH_DP]、色取设计系统那颗 [Border]、
 * 圆角 [LoveBrainShape.lg]、内边距横 [Spacing.lg] 竖 [Spacing.md]（基线 v1 §6.1 那一档）。
 * 涂的都是表面色（[SurfaceCard] / [Border]），不含品牌色，因此不落在
 * `UiLayerDependencyContractTest` 那两把"页面自画品牌底"的尺的射程里。
 */
internal fun Modifier.settingsEntryCard(): Modifier = this
    .fillMaxWidth()
    .clip(LoveBrainShape.lg)
    .background(SurfaceCard, LoveBrainShape.lg)
    .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
