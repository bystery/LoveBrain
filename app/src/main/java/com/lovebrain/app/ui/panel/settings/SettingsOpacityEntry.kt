package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextSecondary
import kotlin.math.roundToInt

/** 那颗滑杆的稳定锚点：文案会跟着数值变，tag 不会 */
internal const val SETTINGS_OPACITY_SLIDER_TAG = "settings-opacity-slider"

/**
 * 设置页里那一颗**透明度滑杆**：label + 滑杆 + 百分比读数，没有别的。
 *
 * 用户 2026-10-03 原话："设置里面暂时先弄一个调透明度的，别的都不要弄"，并且点名要删掉
 * "只调整背景的浓淡…"那一整段解释。所以这一格里**没有**说明段落、没有预览条、
 * 没有超时档位、没有供应商与捕获范围——那些能力各自住在首页那四个入口的页面里。
 *
 * 数值语义：**100% = 最不透明**，数值越小越通透；画出去走 [PanelBackdropOpacity.alphaOf]
 * （`alpha = percent / 100f`），**不在这里手算 `100 - x`**。区间、步长、默认值、非法值回落
 * 全都取自同一把尺 [PanelBackdropOpacity]，这一页一个数都不重抄。
 *
 * 生命周期：拖动期间每帧只交预览值（不落盘），松手那一次才交给 commit 写盘。
 * ⚠ commit 之后**不许**动窗口的 `ComposeView.alpha`——服务侧在 present/hide 路径上会把它
 * 复位成 `1f`，那条通道属于显示/隐藏动画，与用户这里的浓度是两件事。
 */
@Composable
internal fun SettingsOpacityEntry(
    opacityPercent: Int,
    onOpacityPreview: (Int) -> Unit,
    onOpacityCommit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 拖动过程中的临时读数：调用方那份状态要等 onOpacityCommit 才落定，
    // 若这里不接管这一段，松手前那半秒读数会停在旧值上——滑杆与数字对不上。
    // null = 现在没人按着，读数一律跟参数走（这一格自己**不**保存透明度）。
    var draggingPercent by remember { mutableStateOf<Int?>(null) }
    val shownPercent = (draggingPercent ?: opacityPercent).coerceIn(
        PanelBackdropOpacity.MIN_PERCENT,
        PanelBackdropOpacity.MAX_PERCENT
    )
    val percentReadout = stringResource(R.string.settings_opacity_percent, shownPercent)
    // 滑杆那颗可点节点自己没有文案（M3 给它的是进度语义），读屏就只剩"滑块"两个字。
    val sliderName = stringResource(R.string.settings_opacity_label) + " " + percentReadout

    // 卡容器走那一族唯一的主人 [settingsEntryCard]（依据基线 v1 §6.1：SurfaceCard 行 / Border 1dp /
    // Lg 圆角 / 内 12）——三格同一条链，以前各自抄一遍，抄出过描边色不一致那两处漂移。
    // ⚠ 卡里那一行的**版式高度**由滑杆自己那条链上的 48dp 触摸下限顶出来（§6.1 明写"滑杆那 48
    //    行高不是膨胀点，别缩"），那是热区轴给的，不是这里再叠一层 padding 撑的（三轴分离）。
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.settingsEntryCard()
    ) {
        Text(
            text = stringResource(R.string.settings_opacity_label),
            style = AppTypography.labelMedium,
            color = TextSecondary
        )
        Spacer(Modifier.width(Spacing.md))
        // 下限垫在滑杆**自己**那条链上（不是外面套一层大盒子）：
        // 带语义的那颗节点如果还是 20dp 高，手指信的就是 20dp。
        Slider(
            value = shownPercent.toFloat(),
            onValueChange = { next ->
                val percent = next.roundToInt()
                draggingPercent = percent
                onOpacityPreview(percent)
            },
            onValueChangeFinished = {
                draggingPercent?.let(onOpacityCommit)
                draggingPercent = null
            },
            valueRange = PanelBackdropOpacity.MIN_PERCENT.toFloat()..PanelBackdropOpacity.MAX_PERCENT.toFloat(),
            // 落点格数由那把尺数出来：steps 给的是"档数"，Slider 要的是**内部**落点数 ⇒ 档数减二。
            // 写死 11 就是在这里再发明一套刻度。
            steps = PanelBackdropOpacity.steps.size - 2,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                .semantics { contentDescription = sliderName }
                .testTag(SETTINGS_OPACITY_SLIDER_TAG)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = percentReadout,
            style = AppTypography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(start = Spacing.xs)
        )
    }
}
