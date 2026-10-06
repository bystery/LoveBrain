package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionGlyph
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.KnowledgeBase

/**
 * 这一页紧凑页头那一族的三个数（依据基线 v1 §6.1 / §2-Q13）。
 *
 * 为什么写成文件私有的具名档而不是抄 `LbTopBarLevel.Page`：`LbTopBarLevel` 现在只有
 * `Identity`/`Page` 两档（`Page` 走行高 = [AppDimens.TOUCH_TARGET_MIN_DP] 的 48 + `titleLarge`18 + 带分割线），
 * 那是 F1/F2 App 全屏页的规格；用户第 17 条点的"点齿轮进去像另一个 App（字大、控件大）"
 * 就是这一页错拿了那一档。齿轮设置页属 **F3 悬浮窗紧凑族**，要与本体 `PanelHeader` 同线：
 * 行高 30、标题 `titleMedium`15、无分割线、返回字形降档。
 *
 * ⚠ "要不要把这档上提成 `LbTopBarLevel.Panel`"是**跨页决策**（`core/designsystem/LbTopBar.kt` 归在飞席，
 * 不是本席地盘），已作为**决策件**写进接线单交主线程；在它落地之前，字阶/行高降档只发生在**这一页**，
 * 不动公共件。下面那颗 30 是**版式轴**（相邻布局那一列），不是热区下限——
 * 返回那颗的热区由 [LbTextAction] 的图标档自己给，这一行只管页头本身的厚度。
 */
private object SettingsHeaderDimens {
    /** F3 装饰带行高：与本体 `PanelHeader.ROW_HEIGHT_DP` 同一数（Q13：维持 30，回复页头部也不抬） */
    const val ROW_HEIGHT_DP = 30
    /** 返回字形与标题之间那一小段（与本体页头 `LbTopBar` 的 2dp 留白同档） */
    const val TITLE_GAP_DP = 2
}

/**
 * 悬浮窗齿轮那一扇**整窗设置页**的正文：一行页头「[返回] 设置」，下面只有一颗透明度滑杆。
 *
 * 依据是用户 2026-10-03 的原话："设置里面暂时先弄一个调透明度的，别的都不要弄"。
 * 被撤出去的三段（供应商 / 超时档位 / 捕获范围）都不是失去入口：
 * 供应商与模型在首页"模型供应商"那一格里增删改与测试连接，超时四档同一张表单里就有
 * （`ui/home/ProviderSection.kt:576-590`），捕获范围在首页"消息捕获"那一格里。
 *
 * 顺带改掉的是**第一次点设置会卡一下**那件事：这一页以前在组合阶段就同步枚举整机安装包
 * （`selectableCaptureTargets(context)`），并为了拿供应商那一行而 `koinViewModel()` 依赖容器。
 * 现在这一页不碰 PackageManager、不碰供应商状态源，只接三个透明度回调。
 *
 * 无状态：这一格不持有配置。透明度读数由宿主给，拖动预览与松手写盘各一条回调，
 * 作用在**面板背景层**上；不许走窗口 `ComposeView.alpha` 那条通路（服务侧会复位成 1f）。
 *
 * @param onBack 回到打开设置之前的那一面（回复/谈心由宿主决定恢复哪一面，输入与卡片状态由宿主保留）
 * @param opacityPercent 当前背景层不透明度（100 = 最不透明；刻度与区间归 `PanelBackdropOpacity`）
 * @param onOpacityPreview 拖动每帧：只用来实时预览背景层，不落盘
 * @param onCollapse 右上那颗「收起」：宿主把它接到 `dismissPanelToBubble`，这一页不自己判断该不该收
 * @param onOpacityCommit 松手一次：落盘由宿主处理
 */
@Composable
fun LoveBrainSettingsContent(
    onBack: () -> Unit,
    onCollapse: () -> Unit,
    opacityPercent: Int,
    onOpacityPreview: (Int) -> Unit,
    onOpacityCommit: (Int) -> Unit,
    intentEnabled: Boolean = false,
    intentText: String = "",
    intentExpiry: IntentExpiry = IntentExpiry.ONE_DAY,
    onIntentChange: (String, Boolean, IntentExpiry, Boolean) -> Unit = { _, _, _, _ -> },
    knowledgeBases: List<KnowledgeBase> = emptyList(),
    activeKbName: String? = null,
    onSwitchKb: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onInputIntent: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 紧凑页头：一颗返回 + 一行页名。返回那颗的名字仍走 `R.string.common_back`、角色与热区
        // 由 [LbTextAction] 的图标档保证（本席不给 `LbTopBarLevel` 加 Panel 档，见 [SettingsHeaderDimens] 上方的决策件说明）。
        // 这一行钉在滚动柱之外：键盘再高也不会把"返回"滚出可达范围（判据在 `SettingsPageSemanticsTest`）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SettingsHeaderDimens.ROW_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Spacer(Modifier.width(SettingsHeaderDimens.TITLE_GAP_DP.dp))
            // 返回字形降档：F3 档走 [LbTextActionGlyph.RowIcon]（16dp 字形 / 28dp 见方热区），
            // 与本体那颗齿轮（16dp 字形）同档；不再拿 `Page` 那一条 22dp 字形 / 48 见方的热区，
            // 那是"控件大"的另一处来源。28dp 热区 < 行高 30，不被父约束裁掉。
            LbTextAction(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                description = stringResource(R.string.common_back),
                onClick = onBack,
                tone = LbTextActionTone.RowSecondary,
                glyph = LbTextActionGlyph.RowIcon
            )
            Spacer(Modifier.width(SettingsHeaderDimens.TITLE_GAP_DP.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = AppTypography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            // 「收不起窗」那一半的落点（原话第 17 条，主线程 2026-10-06 补）：主面的收起长在
            // `PanelHeader` 那颗，齿轮一开整窗换成这一页就没了出口。名字仍走 `R.string.panel_collapse`
            // （零新增字面量）、字形/热区两轴走 `LbTextAction` 的图标档，与左边那颗返回同档；
            // **点击不在这页里存任何状态**——`onCollapse` 由宿主传下来（宿主那侧走
            // `dismissPanelToBubble`），这一页不许自己判断"该不该收起"。
            Spacer(Modifier.weight(1f))
            LbTextAction(
                // 走 `iconRes` 那一支：仓库只依赖 `material-icons-core`，`Icons.Filled.ExpandMore`
                // 属 extended 集，写上去就是编译不过——这里改用主面那颗收起在用的同一张 drawable。
                iconRes = R.drawable.ic_chevron_down,
                description = stringResource(R.string.panel_collapse),
                onClick = onCollapse,
                tone = LbTextActionTone.RowSecondary,
                glyph = LbTextActionGlyph.RowIcon
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                // 页头与卡片之间那一段：以前顶着 `Page` 那 48 行 + 分割线 + 本处 12，三段叠成
                // "顶累计 73dp 才见控件"（§6.1）。页头降到 30、去掉分割线后，这里再收到 8，
                // 与本体那一族头部到内容的节奏同档。水平那一段仍归宿主外框那一个主人（本席不动）。
                .padding(top = Spacing.md, bottom = Spacing.xl)
        ) {
            SettingsOpacityEntry(
                opacityPercent = opacityPercent,
                onOpacityPreview = onOpacityPreview,
                onOpacityCommit = onOpacityCommit
            )
            // 持续意图入口（原话第 10 条：从面板输入行搬进设置页，收掉屏上那两排次级控件）。
            // 默认关；开着时展开有效期 + 正文录入。能力与意图编辑浮层同源，落盘仍走 IntentController。
            Spacer(Modifier.height(Spacing.md))
            SettingsIntentEntry(
                intentEnabled = intentEnabled,
                intentText = intentText,
                intentExpiry = intentExpiry,
                onIntentChange = onIntentChange,
                onInputIntent = onInputIntent
            )
            Spacer(Modifier.height(Spacing.md))
            SettingsKbSwitcherEntry(
                knowledgeBases = knowledgeBases,
                activeKbName = activeKbName,
                onSwitchKb = onSwitchKb
            )
        }
    }
}
