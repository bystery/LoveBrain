package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * 使用统计的一格：**数值**（单位跟着数值一起给）+ **标签**。
 *
 * 单位为什么不是第三个字段：首页那三颗的值本来就是带单位的整串（`¥3.40`、`62%`），
 * 面板顶部那五颗也是（`¥0.000`、`0次`、`1.2s`、`—`）。拆成 value + unit 两栏，
 * "¥ 归数值还是归单位"就得两个调用方各判一次——那正是 §6.1 这一行要避免的事。
 * ⇒ 口径：整串给进来，组件统一的是**数值与标签怎么排**（字号、颜色、先后）。
 *
 * [label] 由调用方给：这一颗组件管排布，不认识某一页的词表。
 */
data class LbMetric(
    val label: String,
    val value: String,
    /** 强调档只在 [LbMetricDensity.Card] 有差别：小条那一档里数值本来就全是 Primary */
    val highlight: Boolean = false
)

/**
 * 同一份"数值 + 单位 + 标签"的**两种容器**（§6.1 表第 7 行的组件仍只有一颗）。
 *
 * 为什么要第二档，而不是让面板也画那张卡：
 * - [Card] 档 = 首页「使用概览」那一整块：`Spacing.xl` 内边距 + 三格等分，
 *   360dp 下实测 70dp 高（账本 §61 那一格量的就是它）；
 * - [Inline] 档 = 悬浮面板顶部那条 10sp 小字。它挂的锚点是 `DragHandle` 那一条
 *   **4dp** 高的 Box，外面还套着 `clip(LoveBrainShape.xl)`。把 70dp 的卡塞进 4dp 的锚点，
 *   上半截被面板圆角裁走、下半截压在页头那三档切换上——面板要的从来不是"另一颗组件"，
 *   是同一颗组件的**另一档容器**。
 *
 * 形状与 `LbTopBar` 的 `LbTopBarLevel` 一致：给两个**有名字的档**，
 * 不留一个 `style:` 参数让每页自选——那种参数等于把"每页另造样式"合法化。
 */
enum class LbMetricDensity {
    /** 带容器：一张 `Card`，数值在上、标签在下，格与格等分 */
    Card,

    /** 不带容器：一横条，标签在前、数值在后，挂在面板顶部那条锚点上 */
    Inline
}

/**
 * 使用统计——§6.1 表第 7 行的那一颗，全 App 数值的统一入口。
 *
 * **内容来源只有一个：[metrics]。** 归并这一格时曾经留过一条"三件套"旧口
 * （`totalGenerate / totalCost / adoptRate`，标签写死在组件里），本轮把它删了，理由是它自己就违反这一节：
 * 那三个词是**首页「使用概览」那一张卡的词表**，写死进设计系统之后，
 * 设计系统反过来认识某一页了——第二页想复用同一颗组件，要么被塞进同样的三个槽，
 * 要么把整颗组件另画一遍。这正是表末那句"禁止只在一个页面看起来不一样的组件"要挡的形状。
 * 标签由调用方给（见 [LbMetric] 的口径），组件只统一**数值与标签怎么排**。
 *
 * 归并这一格时渲染口径一格没改：三件套那条还是 3 格、还是那张卡；
 * 面板那条还是 5 格（首字那格的 `>0` 条件、`—` 占位都原样）。
 *
 * @param onClick 整块一处操作（首页那颗就跳「使用概览」）；不传就没有点击语义。
 *   注意 [LbMetricDensity.Inline] 那一档只有 10sp 高：真要让它可点，
 *   热区得自己垫到下限（见 `AppDimens.TOUCH_TARGET_MIN_DP`），别直接传。
 */
@Composable
fun LbMetricGrid(
    metrics: List<LbMetric>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    density: LbMetricDensity = LbMetricDensity.Card
) {
    require(metrics.isNotEmpty()) {
        "LbMetricGrid 没有任何内容可画：metrics 为空"
    }
    val cells = metrics

    val base = modifier.fillMaxWidth()
    val containerModifier = if (onClick != null) base.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Button,   // §6.5 :532；见 `DesignSystemRolesTest`
        onClick = onClick
    ) else base

    when (density) {
        LbMetricDensity.Card -> Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = containerModifier
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                cells.forEach { cell ->
                    LbMetricCard(cell, modifier = Modifier.weight(1f))
                }
            }
        }

        LbMetricDensity.Inline -> Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterHorizontally)
        ) {
            cells.forEach { cell ->
                LbMetricCard(cell, density = LbMetricDensity.Inline)
            }
        }
    }
}

@Composable
private fun LbMetricCard(
    metric: LbMetric,
    modifier: Modifier = Modifier,
    density: LbMetricDensity = LbMetricDensity.Card
) {
    when (density) {
        LbMetricDensity.Card -> Column(
            modifier = modifier.testTag(LbTags.METRIC_CELL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                metric.value,
                style = AppTypography.titleLarge,
                color = if (metric.highlight) Primary else TextPrimary,
                fontWeight = if (metric.highlight) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                metric.label,
                style = AppTypography.labelSmall,
                color = TextHint,
                textAlign = TextAlign.Center
            )
        }

        LbMetricDensity.Inline -> Row(modifier = modifier.testTag(LbTags.METRIC_CELL)) {
            Text(
                "${metric.label} ",
                style = AppTypography.labelSmall,
                color = TextHint
            )
            Text(
                metric.value,
                style = AppTypography.labelSmall,
                color = Primary,
                fontWeight = if (metric.highlight) FontWeight.SemiBold else null
            )
        }
    }
}
