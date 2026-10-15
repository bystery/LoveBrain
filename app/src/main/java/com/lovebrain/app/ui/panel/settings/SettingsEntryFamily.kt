package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.BorderLight
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset

/**
 * 设置页那一族**行**自己的三个数。
 *
 * 为什么写成文件私有的具名档（与 `SettingsHeaderDimens` 同一体例）：这三个数只在"这一族的行"里成立，
 * 上提到 [AppDimens] 就是宣布全站都该长这样——而全站别的行（首页、捕获页）走的是另一档密度。
 * 数值全部指回既有 token 或原型那一族，不新造第四套尺：
 * · 行版式下限取全站那颗热区下限，不另立一档；
 * · 轨道那三颗（内缩 / 间距 / 最小格宽）是"一条浅底轨道里几格选项"这一族的形状参数。
 */
internal object SettingsRowDimens {
    /** 一行的版式下限（dp）：与 [AppDimens.TOUCH_TARGET_MIN_DP] 同数——行本身就是那颗热区的主人 */
    const val ROW_MIN_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP

    /** 分段轨道容器自己那圈内缩（dp）：轨道底要包住格子一圈，格子才读成"在同一件事里" */
    const val PILL_TRACK_PADDING_DP = 3

    /** 分段轨道里相邻两格之间那一段（dp） */
    const val PILL_TRACK_GAP_DP = 2

    /** 一格的可见最小宽（dp）：单字档（"小"）不垫这一档就会缩成一枚图标 */
    const val PILL_CELL_MIN_WIDTH_DP = 37

    /**
     * **两字档那一族**一格的可见最小宽（dp）：原型给"回复卡片"那一排单独一档
     * （`.segments.compact button{min-width:49px}`），比三档那一排的 37 宽一截。
     * 两格一排时整条轨道本来就窄，格子跟着松一档，读起来才是"两个并列选项"而不是"两个小按钮"；
     * 原型这两档是分开写的，这里也分开写，不合并成一颗数。
     */
    const val PILL_CELL_MIN_WIDTH_COMPACT_DP = 49

    /**
     * 轨道那一条底的**可见厚度**（dp）= 格子可见高 [AppDimens.CHIP_PANEL_HEIGHT_DP]（28）+ 上下各
     * [PILL_TRACK_PADDING_DP]（3）。
     *
     * 为什么不"由格子自己撑出来"：格子那颗芯片按分层写法买 48dp 热区（可见 28），
     * 让子节点决定容器厚度的话，这条底会被撑到 48+3+3 = 54dp——真机回归里"右边的按钮太大了"
     * 量到的就是这一圈透明外盒被涂成了实底。这一档把底钉回可见那一档，热区一寸不缩。
     */
    const val PILL_TRACK_VISIBLE_DP =
        AppDimens.CHIP_PANEL_HEIGHT_DP + PILL_TRACK_PADDING_DP * 2
}

/**
 * 悬浮窗那一族设置行的**共同容器**：`fillMaxWidth` + 圆角 + 卡底 + 描边 + 内边距，只写这一次。
 *
 * 为什么要有这一颗：同一族设置行要用共同的标签、辅助文字、分隔和展开规则，否则"共同"只是口头约定。
 * 三格以前各自抄一遍同一条链，抄出两处真实漂移——
 * · 知识库那一格的描边写的是硬编码的 `0xFFE0E0E6`，不是族里那颗 [Border]，同一页上两张卡边色不同；
 * · 内边距那一档要靠"记得抄对"才一致，改一族就得同时改三个文件。
 * 现在容器只有一个主人，页面只交外面那条 `Modifier`（调用方的 `modifier` 排在最前，
 * 宿主给的对齐与权重仍然生效）。
 *
 * 数值一个都不新造：描边宽度取 [AppDimens.BORDER_WIDTH_DP]、色取设计系统那颗 [Border]、
 * 圆角 [LoveBrainShape.lg]、内边距横 [Spacing.lg] 竖 [Spacing.md]（与全站那一档紧凑内边距同数）。
 * 涂的都是表面色（[SurfaceCard] / [Border]），不含品牌色，因此不落在
 * `UiLayerDependencyContractTest` 那两把"页面自画品牌底"的尺的射程里。
 */
internal fun Modifier.settingsEntryCard(): Modifier = this
    .fillMaxWidth()
    .clip(LoveBrainShape.lg)
    .background(SurfaceCard, LoveBrainShape.lg)
    .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
    .padding(horizontal = Spacing.lg, vertical = Spacing.md)

/**
 * 设置页**一组一张**的外框：页面为「显示」「内容」「悬浮助手」三组各画一次，组名写在框外。
 *
 * 与 [settingsEntryCard] 的分工有两处，而这两处正是"卡套卡、双重内缩、一组比一屏还高"的解药：
 * · 横向内缩 [Spacing.lg] **只由外框给**——组内的行只管自己的版式下限，不再各自垫一层横向 padding；
 * · 纵向只给 [Spacing.xs] 那一档**轨道缝**（首末行与卡边之间那一段），行的高度与呼吸归 [settingsEntryRow]。
 * 于是同一组里标签的左边缘、尾部选择器的右边缘各自落在同一条线上，行与行之间只剩一条分隔线，
 * 而不是"每格自带一张卡、卡里再套行"那种逐格漂移（数值判据见 `SettingsPageStructureTest` 的对齐格）。
 *
 * 数值仍全部复用族里现成的那一套：[AppDimens.BORDER_WIDTH_DP] / [Border] / [LoveBrainShape.lg] /
 * [Spacing.lg] / [Spacing.xs]，一个都不新造。
 */
internal fun Modifier.settingsEntryGroupCard(): Modifier = this
    .fillMaxWidth()
    .clip(LoveBrainShape.lg)
    .background(SurfaceCard, LoveBrainShape.lg)
    .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
    .padding(horizontal = Spacing.lg, vertical = Spacing.xs)

/**
 * 组内**一行**的骨架：铺满宽度 + 版式下限 [SettingsRowDimens.ROW_MIN_HEIGHT_DP]。
 *
 * 三轴分离（三轴分离那条纪律）：这一颗买的是**相邻布局**那一轴（行与行之间只剩一条分隔线），
 * 行里控件自己的可见尺寸归各档芯片（[com.lovebrain.app.core.designsystem.LbChipStyles.segmentedPill]
 * 与 [com.lovebrain.app.core.designsystem.LbChipStyles.actionSolid] 的 `pillHeight`），
 * 热区归它们那一条分层外盒。行自己**不**再垫横向 padding，也不给控件外面套大方盒。
 */
internal fun Modifier.settingsEntryRow(): Modifier = this
    .fillMaxWidth()
    .heightIn(min = SettingsRowDimens.ROW_MIN_HEIGHT_DP.dp)

/**
 * 分段选择器那一条**浅底轨道**：一格一格透明，选中那一格才涂强调色——
 * 底与圆角归轨道，不归格子（格子自己带描边会读成三个独立按钮，见
 * [com.lovebrain.app.core.designsystem.LbChipStyles.segmentedPill] 为什么第二档不画底）。
 *
 * 只画表面色（[SurfaceInset]，与输入框静息底同一颗），不含品牌色；圆角取 [LoveBrainShape.md]，
 * 比外框那颗 `lg` 小一档——轨道在卡里，卡是外、轨道是内，两档圆角不能反过来。
 * 宽度由内容定（`width = fit-content` 那一族），不铺满整行：铺满会把两格撑到行尾之外，
 * 读成"这一行有两个互不相干的按钮"。
 */
@Composable
internal fun SettingsSegmentedPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            // 外层这一圈只买**热区**那一档（格子的分层外盒要 48 才站得下），它自己不涂底；
            // 底画在下面那一步里，厚度归可见那一档，于是"看着小、点得着"两件事各归各的主人。
            .heightIn(min = SettingsRowDimens.ROW_MIN_HEIGHT_DP.dp)
            .drawBehind {
                val strip = SettingsRowDimens.PILL_TRACK_VISIBLE_DP.dp.toPx()
                drawRoundRect(
                    color = SurfaceInset,
                    topLeft = Offset(0f, (size.height - strip) / 2f),
                    size = Size(size.width, strip),
                    // 圆角不抄第二份数：从 LoveBrainShape.md 那同一颗形状里读出它自己的角半径
                    cornerRadius = CornerRadius(LoveBrainShape.md.topStart.toPx(Size.Zero, this))
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        content = {
            Row(
                modifier = Modifier.padding(horizontal = SettingsRowDimens.PILL_TRACK_PADDING_DP.dp),
                horizontalArrangement = Arrangement.spacedBy(SettingsRowDimens.PILL_TRACK_GAP_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    )
}

/**
 * 组内相邻两行之间那一条线：**整页只有这一颗画线**（页面排组内行用它，
 * 知识库那一格展开列表的上沿也用它——同一层视觉关系，不该有第二个落点）。
 *
 * 粗细取 [AppDimens.BORDER_WIDTH_DP]、色取 [BorderLight]：原型 `.item + .item{border-top:1px solid
 * #e6e8ed}` 与 `.kb-options{border-top:1px solid #e6e8ed}` 是**同一个色同一个宽**，
 * 而外框描边是更深一档的 `--border`——"外框重、内部分界轻"那层关系就靠这一档色差画出来。
 * 体里一个自己的数都不写，全部指回具名 token。
 */
@Composable
internal fun SettingsRowDivider() {
    HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = BorderLight)
}
