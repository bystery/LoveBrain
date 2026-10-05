package com.lovebrain.app.ui.panel.counseling

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceBase

/** 谈心模板 chip 尾部渐隐遮罩尺寸（仅本文件使用）。 */
internal object CounselingTemplateDimens {
    const val FADE_MASK_WIDTH_DP = 24
    const val FADE_MASK_HEIGHT_DP = 28
}

/**
 * 谈心快速模板 chip 行：未在谈心中、无结果时，显示常见困惑模板（全部 6 个，一行横向滚动）。
 * 点击模板直接填入输入框。
 */
@Composable
internal fun CounselingTemplateChips(
    onTemplateSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val templates = listOf(
        "她突然冷淡了怎么办",
        "我们吵架了该谁先低头",
        "她说了这句话什么意思",
        "怎么判断她喜不喜欢我",
        "暧昧期怎么推进关系",
        "她嫌我不够浪漫"
    )
    // 尾部渐隐遮罩，提示后面还有可滑动的 chip
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            templates.forEach { template ->
                TemplateChip(text = template) { onTemplateSelect(template) }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(CounselingTemplateDimens.FADE_MASK_WIDTH_DP.dp)
                .height(CounselingTemplateDimens.FADE_MASK_HEIGHT_DP.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, SurfaceBase)
                    )
                )
        )
    }
}

/**
 * 谈心模板 chip（需求11：折叠后仍保持统一 chip 样式）——形状归设计系统那颗 [LbChip]。
 *
 * 点下去是把模板填进输入框，不是"在哪一格" ⇒ [LbChipInteraction.Action]：
 * `Role.Button`（与改之前那条链上写的同一个角色），语义树里不发 `selected`。
 * 档位是把改之前那条链逐项抄进来的：圆角 `LoveBrainShape.sm`、底 `SurfaceInset`、
 * 描边 `Border`、字 `labelSmall`（它自带 Medium，前后同值 = 不靠字重表达状态）、
 * 内边距左右 [Spacing.md] / 上下 [Spacing.sm]、按压 0.92（原 `#1` 那一档）、
 * 下限 48 见方垫在可点那颗自己身上（原来就垫在 `clickable` 之前，归并后仍是那颗）。
 */
@Composable
private fun TemplateChip(text: String, onClick: () -> Unit) {
    LbChip(
        label = text,
        onClick = onClick,
        interaction = LbChipInteraction.Action,
        style = LbChipStyles.neutral
    )
}
