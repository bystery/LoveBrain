package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 表单分组卡的唯一主人（基线 v1 §3.1/§3.7；D1 §③-6 对供应商表单"基础/凭据/模型/请求"
 * 四组的建议就落在这颗上，组内放什么归页面，组的**版式**归这里）。
 *
 * 内部版式只有一份：
 * 上间距 [Spacing.xl] 16（组→组，靠这颗自带，页面排 Column 时**不许**再垫一次 16——
 * 同一段空白扣两遍是 D1 §② C6 那一族）；卡体 `SurfaceCard` 底 + `Border`
 * [AppDimens.BORDER_WIDTH_DP] 1dp + `Lg` 16 圆角（描边圆角=填充圆角，R2）+ 内边距
 * [Spacing.lg] 12；组题 `titleMedium` 15sp `TextPrimary`；组题→首字段 [Spacing.md] 8、
 * 字段→字段（同组）同为 [Spacing.md] 8（一条 spacedBy 排出来的，组内节奏只有一个数）。
 *
 * 节奏顺序仍是判据（NN/g 邻近律，D1 参考池 #6）：标签→字段 4（[LbFormField]）
 * < 组内字段→字段 8 < 组→组 16。三段各有一个主人，谁也不抄谁的数。
 *
 * 阴影不许进这颗（基线 v1 §3.4：描边管静息、阴影管浮起；表单组卡是静息层）。
 *
 * @param title 组题，由调用方交（设计系统不持词表）。
 * @param content 组内字段；直接当 ColumnScope 排，字段一颗接一颗交给 [LbFormField]。
 */
@Composable
fun LbFormGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxWidth().padding(top = Spacing.xl)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LoveBrainShape.lg)
                .background(SurfaceCard)
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = title,
                style = AppTypography.titleMedium,
                color = TextPrimary
            )
            content()
        }
    }
}
