package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
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
import com.lovebrain.app.model.IntentStatus
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
 * 悬浮窗齿轮那一扇**整窗设置页**的正文：一行页头「[返回] 设置」，下面按 §11.1 那一族的顺序
 * 依次是三格——透明度、当前知识库、意图。
 *
 * 这一页是**一棵 Box**：里面那一列是页头 + 可滚的表单，最外面那一层留给浮层。
 * 为什么页根必须是 Box（§11.2 点名要查的那件事）：意图那扇介绍浮层的遮罩走
 * `fillMaxSize()`，而滚动柱交给子节点的最大高度是无限的——浮层画在滚动列里时，
 * 遮罩只剩自己那一块、白占一个表单槽位、还能被滚走。它因此挂在这一页的最外一层，
 * 由 [SettingsIntentIntroSheet] 画那一颗设计系统共用的壳（[LbModalSheet]），
 * 树常驻、开合只经 `visible` 说话，外面不再包第二份动画。
 *
 * 供应商 / 超时档位 / 捕获范围不在这一页：供应商与模型在首页"模型供应商"那一格里
 * 增删改与测试连接，超时四档同一张表单里就有（`ui/home/ProviderSection.kt:576-590`），
 * 捕获范围在首页"消息捕获"那一格里。
 *
 * 这一页不碰 PackageManager、不碰供应商状态源。
 *
 * 无状态：这一格不持有配置。透明度读数由宿主给，拖动预览与松手写盘各一条回调，
 * 作用在**面板背景层**上；不许走窗口 `ComposeView.alpha` 那条通路（服务侧会复位成 1f）。
 * 意图与知识库切换的配置也由宿主交下来，写口各一条。这一页唯一自己持有的东西是
 * 那扇介绍浮层的开合（[SettingsIntentIntroHolder]）与"已经看过介绍"那一条盘上的记录。
 *
 * @param onBack 回到打开设置之前的那一面（回复/谈心由宿主决定恢复哪一面，输入与卡片状态由宿主保留）
 * @param opacityPercent 当前背景层不透明度（100 = 最不透明；刻度与区间归 `PanelBackdropOpacity`）
 * @param onOpacityPreview 拖动每帧：只用来实时预览背景层，不落盘
 * @param onCollapse 右上那颗「收起」：宿主把它接到 `dismissPanelToBubble`，这一页不自己判断该不该收
 * @param onOpacityCommit 松手一次：落盘由宿主处理
 * @param assistantOn 悬浮助手此刻开着没有——**只能来自真实窗口状态**（`WindowState != STOPPED`），
 *   本页不许为它存一份、也不给默认值：默认值＝宿主漏接线时屏幕自己猜一个状态（§2）
 * @param onAssistantEnable 拨开：交宿主走**既有那条启动链**（先查悬浮窗权限，本页不 `startActivity`）
 * @param onAssistantClose 拨关／「关闭悬浮助手」那颗：交宿主走**与主页 ■ 同一颗**停止入口，
 *   最终 `stopSelf()` 经 `onDestroy` 清理；本页不自己摘窗、也不接到「暂时隐藏」那一支（§2）
 * @param bubbleSizeDp 悬浮图标当前档位（dp）；三档刻度与回落在 `AppConfig.BubbleSizeTier`，
 *   这一页只跟着读数画选中态（§1）
 * @param onBubbleSizeChange 选一档：写盘与"窗口宽高/球径/角标/贴边同步"都由宿主与服务那一侧完成，
 *   本页不持有尺寸（§1 明写不许只对 Compose 缩放而留着旧的窗口点击区）
 * @param replyCardVertical 回复卡片是否纵向排列；真值是 `ReplyCardLayout` 枚举（默认纵向），
 *   这里只摊成用户能看懂的一颗开关（§3）
 * @param onReplyCardVerticalChange 换方向：宿主只写那一格，**不重新请求、不清空当前回复**（§3）
 * @param onIntentChange 意图唯一的写口：`(正文, 启用, 有效期, 是否按此刻重算期限)`
 * @param intentStatus 那条意图现在的状态（活动／已到期／已完成）——§11.3 那六档判据要读它；
 *   宿主没接这一颗时按活动态画，与接上之前一模一样，不会多画一格也不会少画一格
 * @param intentExpiryDate 时间档那条到期时刻（`yyyy-MM-dd HH:mm`，盘上那份），用于"展示期限"
 */
@Composable
fun LoveBrainSettingsContent(
    onBack: () -> Unit,
    onCollapse: () -> Unit,
    assistantOn: Boolean,
    onAssistantEnable: () -> Unit,
    onAssistantClose: () -> Unit,
    opacityPercent: Int,
    onOpacityPreview: (Int) -> Unit,
    onOpacityCommit: (Int) -> Unit,
    bubbleSizeDp: Int,
    onBubbleSizeChange: (Int) -> Unit,
    replyCardVertical: Boolean,
    onReplyCardVerticalChange: (Boolean) -> Unit,
    intentEnabled: Boolean = false,
    intentText: String = "",
    intentExpiry: IntentExpiry = IntentExpiry.ONE_DAY,
    onIntentChange: (String, Boolean, IntentExpiry, Boolean) -> Unit = { _, _, _, _ -> },
    knowledgeBases: List<KnowledgeBase> = emptyList(),
    activeKbName: String? = null,
    onSwitchKb: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    intentStatus: IntentStatus = IntentStatus.ACTIVE,
    intentExpiryDate: String = "",
    onInputIntent: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // 介绍浮层：可见性与「已经看过」只有这一颗持有者（页面关掉再开 = 回到盘上那份记录）。
    val intentIntro = rememberSettingsIntentIntroHolder()

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
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
            // 指导书 2026-10-10 §7 的分组：悬浮助手（开关 → 透明度 → 图标大小）→ 回复显示 →
            // 当前知识库 → 持续意图。分组只用**现有那张卡 + 顺序 + 组名那一行字阶**表达，
            // 不新绘第二套容器、分割线或标题样式（§7 原话：「不得据此新绘制按钮、卡片和分割线样式」
            // 「使用现有容器的真实布局能力，先比对当前设置页」）。
            SettingsAssistantEntry(
                assistantOn = assistantOn,
                onRequestEnable = onAssistantEnable,
                onRequestClose = onAssistantClose
            )
            Spacer(Modifier.height(Spacing.md))
            SettingsOpacityEntry(
                opacityPercent = opacityPercent,
                onOpacityPreview = onOpacityPreview,
                onOpacityCommit = onOpacityCommit
            )
            Spacer(Modifier.height(Spacing.md))
            SettingsBubbleSizeEntry(
                sizeDp = bubbleSizeDp,
                onSizeChange = onBubbleSizeChange
            )
            Spacer(Modifier.height(Spacing.md))
            SettingsReplyLayoutEntry(
                vertical = replyCardVertical,
                onVerticalChange = onReplyCardVerticalChange
            )
            // §11.1 那一族的顺序：透明度 → **当前知识库** → 意图。
            // 切库排在意图前面不是排版口味：意图是按库隔离的那一份数据，先认对象、再改这一块的那件事，
            // 读序与"切库必须取对应数据"同一头。
            Spacer(Modifier.height(Spacing.md))
            SettingsKbSwitcherEntry(
                knowledgeBases = knowledgeBases,
                activeKbName = activeKbName,
                onSwitchKb = onSwitchKb
            )
            // 持续意图入口（原话第 10 条：从面板输入行搬进设置页，收掉屏上那两排次级控件）。
            // 默认关；首次拨开只起下面那一层介绍浮层，确认后才启用并展开有效期 + 正文录入。
            Spacer(Modifier.height(Spacing.md))
            SettingsIntentEntry(
                intentEnabled = intentEnabled,
                intentText = intentText,
                intentExpiry = intentExpiry,
                onIntentChange = onIntentChange,
                intentStatus = intentStatus,
                intentExpiryDate = intentExpiryDate,
                ownerKbName = activeKbName,
                introSeen = intentIntro.seen,
                introPending = intentIntro.pending,
                onRequestIntro = { intentIntro.request() },
                onInputIntent = onInputIntent
            )
        }
    }

    // 介绍浮层挂在这一页的最外一层（判据见本页文件头那句 §11.2 的挂载理由）：
    // 树常驻、开合只经 `visible` 说话，那 200ms 入退场仍由壳里的 `AnimatedVisibility` 持有，
    // 这里不再包第二层动画（§4.4「先修挂载层级，不重复包动画」）。
    SettingsIntentIntroSheet(
        visible = intentIntro.pending,
        onAcknowledge = {
            // 确认之后才正式启用：正文交屏幕上这一份（此时还没进编辑，通常是空串），
            // 换没换档说 `false`——重算期限那一句由启用那件事自己触发（判据在宿主那颗 saveDecision）。
            intentIntro.acknowledge()
            IntentIntroRecord.markSeen(context)
            onIntentChange(intentText, true, intentExpiry, false)
        },
        onDismiss = {
            // 取消／点遮罩 = 保持关闭，一个字都不写
            intentIntro.cancel()
        }
    )
    }
}
