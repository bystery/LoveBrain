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
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipLabelAlignment
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceBase

/**
 * 模板 chip 尾部渐隐遮罩尺寸（仅本文件使用）。
 *
 * 高度 28 与那颗胶囊的**可见**高度同档（[AppDimens.CHIP_PANEL_HEIGHT_DP]）：
 * 归并进 `neutral` 那一档时可见胶囊被 `touchFloor` 撑到 48，遮罩只有 28 反而比胶囊短；
 * 这一格把可见收回到面板胶囊族那一档 28 之后，遮罩与可见胶囊重新等高（依据基线 v1 §6.4）。
 */
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
 * 谈心模板那颗的**形状档**：面板紧凑胶囊族（依据基线 v1 §6.4、用户原话第 12 条"模板胶囊卡片太高"）。
 *
 * 为什么写成文件私有的具名档而不是直接用 [LbChipStyles.neutral]：`neutral` 是"无选中态的动作胶囊·模板形"
 * 一族（带 `paddingVertical = Spacing.sm`、`labelAlignment = TopStart`、`touchFloor`），
 * 归并时把这条链逐项抄了过来——于是那颗可见胶囊被 48 见方下限撑高、标签钉在左上沿，
 * 内容只需 22dp 却占了 48dp，那 26dp 空白 + 顶部贴字就是"卡片过高"的可定位来源（§6.4 算清楚了这条）。
 *
 * ⚠ 本席不给 `LbChipStyles` 新增 `panelChip`/`compactAction` 具名档（`core/designsystem` 目录归在飞席，
 * 不是本席地盘），所以这一档先落**在本文件**；"要不要把它上提成 `LbChipStyles.panelChip`、
 * 把回复输入行那颗私有 28（`ReplyDimens.ROLE_CHIP_HEIGHT_DP`）一起收编"是跨页决策，已写进接线单交主线程。
 *
 * 三轴分离后的三个数（§3.1）：
 * · **可见**：胶囊 [AppDimens.CHIP_PANEL_HEIGHT_DP]=28 高、标签居中（`Center`）、竖内边距 0；
 * · **热区**：外层那颗透明盒仍垫到全站下限 [AppDimens.TOUCH_TARGET_MIN_DP]=48（`touchFloor` 由
 *   `neutral` 继承、开着），[LbChipStyles.neutral] 的 `layeredTouch=false→true` 后热区与视觉分两层，
 *   点得到的面积一寸没少；
 * · **相邻布局**：所在横滚行的版式高度由那颗 48 外层盒决定，与改之前**同数**（撤掉的是可见厚度，
 *   不是行高），不会挤到谈心正文。
 * 颜色/字重/描边/横内边距全部照 `neutral` 原样（`SurfaceInset` 底 + `Border` 描边 + `labelSmall` +
 * Medium + 左右 [Spacing.md] + 按压 0.92），只把"过高"这一处收掉。
 */
private val PanelTemplateChipStyle = LbChipStyles.neutral.copy(
    labelAlignment = LbChipLabelAlignment.Center,
    paddingVertical = 0.dp,
    pillHeight = AppDimens.CHIP_PANEL_HEIGHT_DP.dp,
    layeredTouch = true,
    labelMaxLines = 1
)

/**
 * 谈心模板 chip（需求11：折叠后仍保持统一 chip 样式）——形状归设计系统那颗 [LbChip]。
 *
 * 点下去是把模板填进输入框，不是"在哪一格" ⇒ [LbChipInteraction.Action]：
 * `Role.Button`（与改之前那条链上写的同一个角色），语义树里不发 `selected`。
 * 形状档见 [PanelTemplateChipStyle]（面板紧凑胶囊族，可见 28 + 外层 48 透明热区两层）。
 */
@Composable
private fun TemplateChip(text: String, onClick: () -> Unit) {
    LbChip(
        label = text,
        onClick = onClick,
        interaction = LbChipInteraction.Action,
        style = PanelTemplateChipStyle
    )
}
