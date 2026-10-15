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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbStateTone
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionGlyph
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.ui.panel.KbNoticeBanner
import com.lovebrain.app.ui.panel.PanelCollapseAction

/**
 * 这一页紧凑页头那一族的三个数。
 *
 * 为什么写成文件私有的具名档而不是抄 `LbTopBarLevel.Page`：`LbTopBarLevel` 现在只有
 * `Identity`/`Page` 两档（`Page` 走行高 = [AppDimens.TOUCH_TARGET_MIN_DP] 的 48 + `titleLarge`18 + 带分割线），
 * 那是 F1/F2 App 全屏页的规格；用户报的"点齿轮进去像另一个 App（字大、控件大）"
 * 就是这一页错拿了那一档。齿轮设置页属 **F3 悬浮窗紧凑族**，要与本体 `PanelHeader` 同线：
 * 行高 30、标题 `titleMedium`15、无分割线、返回字形降档。
 *
 * ⚠ "要不要把这档上提成 `LbTopBarLevel.Panel`"是**跨页决策**：`core/designsystem/LbTopBar.kt`
 * 是全站公共件，动它会牵到其它页面，因此在那一档落地之前，字阶/行高降档只发生在**这一页**，
 * 不动公共件。下面那颗 30 是**版式轴**（相邻布局那一列），不是热区下限——
 * 返回那颗的热区由 [LbTextAction] 的图标档自己给，这一行只管页头本身的厚度。
 *
 * ⚠ **原型的页头是 40px（`.hd{height:40px;padding:5px 10px 0}`），这一页有意不照它画**：
 * 40 是原型那张 300×510 手机壳里的读数，而这一页真正的邻居是悬浮窗本体那一排
 * （`ui/panel/PanelHeader.kt#HeaderDimens.ROW_HEIGHT_DP` = 30）。10-11 那一版用户明确要的是
 * "齿轮那一页与本体同线"，把这一行抬到 40 就会在同一次进窗里让头部厚度跳一档，
 * 本体页头也跟着显胖；量具那侧也按 30 划的线
 * （`SettingsPageSemanticsTest` 用 `TouchTier.PANEL_HEADER_ROW` 把页头节点卡在 ≤30）。
 * **这一版只补脸（字阶、密度、分隔线色），页头那一格维持 30。**
 */
private object SettingsHeaderDimens {
    /** F3 装饰带行高：与本体 `PanelHeader.ROW_HEIGHT_DP` 同一数（维持 30，回复页头部也不抬高） */
    const val ROW_HEIGHT_DP = 30
    /** 返回字形与标题之间那一小段（与本体页头 `LbTopBar` 的 2dp 留白同档） */
    const val TITLE_GAP_DP = 2
}

/**
 * 悬浮窗齿轮那一扇**整窗设置页**的正文：一行页头「[返回] 设置 [收起]」，下面是三组内容——
 * 「显示」（透明度、图标大小、回复卡片）、「内容」（当前知识库、持续意图）与「悬浮助手」。
 *
 * 版面只有三张外框，且外框由**这一页**持有：每组一张 [settingsEntryGroupCard]，组名画在框外、
 * 框内只排行与分隔。各 Entry 只渲染组内那一行（或可展开的那一段），不再自带卡壳——
 * "每项一张大卡、卡里再套行"会把页面拉得又长又散，标题与控件的左右边缘也各自漂移。
 * 横向内缩的唯一主人是组外框（横 [Spacing.lg]），行只留纵向呼吸；于是同组里
 * 标签的左边缘、选择器的右边缘各自对齐在同一条线上。相邻同组行用一条 1dp 分隔线紧凑相接，
 * 组与组之间用组名那一行留呼吸，不再用逐项 `Spacer` 堆叠。
 *
 * **这一页现在按原型（`_…设置页紧凑交互原型.html`）对得上的那几件事**（数值判据全在
 * `SettingsPageStructureTest`，换算表指回对照原型换算表，一个都不新造 token）：
 * · 三组 = 三张框：`.group{border-radius:16;border:1px solid var(--border);padding:3px 10px}`
 *   → [settingsEntryGroupCard]（`LoveBrainShape.lg` + `Border` + 横 [Spacing.lg] 竖 [Spacing.xs]）；
 *   「显示」三行、「内容」两行、「悬浮助手」一行，全页不画第四张框、也不给单项套旧卡壳；
 * · 组名画在框外、且只比行标签小一档：`.section-title{font-size:10.5px;font-weight:650;
 *   color:var(--muted);padding:10px 5px 7px}` → [SettingsGroupTitle]
 *   （[AppTypography.labelSmall] + [FontWeight.SemiBold] + [TextSecondary]，上下各 [Spacing.md]，
 *   横向不缩⇒ 组名的左缘与它那张框的左缘同一条线，正是原型那一格的"横内缩与卡片左缘对齐"）；
 * · 行只渲染一行：**页面不往组里灌 modifier、也不给任何一行套壳或垫间距**——行的版式下限
 *   （[settingsEntryRow]，48 ≈ 原型 `.item` 的 49）由各 Entry 自己在行根上取，那是各自行那一格的活；
 *   行标签的字阶归 `LbRowLabel`（整页唯一一种，原型 `.lbl` 那一档）。
 *   页面这一侧能保证的是：同组里标签的左缘、尾部选择器的右缘各自只有一条线可落，
 *   因为横向内缩只有组外框那一个主人；
 * · 组内分隔线比框描边浅一档：`.item + .item{border-top:1px solid #e6e8ed}` → [SettingsRowDivider]
 *   （宽度仍取 [AppDimens.BORDER_WIDTH_DP]，色取 [BorderLight]；以前与框同色，框内那条线读起来像框自己）；
 * · 滚动柱首尾：`.content{padding:5px 10px 17px}` → 顶 [Spacing.sm]、底 [Spacing.xl]
 *   （横向那 10px 归宿主外框一个主人，本页不动）。
 *
 * **两处有意不照原型**：① 页头不采纳 `.hd` 那 40px，维持与本体页头同线的 30dp
 *   （理由与量具落点写在 [SettingsHeaderDimens]）；② `.section-title` 的 `letter-spacing:.5px`
 *   不采纳——Compose 侧要新造一颗 `TextStyle` 才拿得到字距，而这一族的规矩是不许新造 token。
 *
 * 页头钉在滚动柱之外：键盘再高也不会把"返回"滚出可达范围。
 * 供应商 / 超时档位 / 捕获范围不在这一页：供应商与模型在首页"模型供应商"那一格里
 * 增删改与测试连接，超时四档同一张表单里就有（`ui/home/ProviderSection.kt`），
 * 捕获范围在首页"消息捕获"那一格里。这一页不碰 PackageManager、不碰供应商状态源。
 *
 * 无状态：这一格不持有配置。透明度读数由宿主给，拖动预览与松手写盘各一条回调，
 * 作用在**面板背景层**上；不许走窗口 `ComposeView.alpha` 那条通路（服务侧会复位成 1f）。
 * 意图与知识库切换的配置也由宿主交下来，写口各一条。这一页自己不持有任何浮层与盘上记录。
 *
 * @param onBack 回到打开设置之前的那一面（回复/谈心由宿主决定恢复哪一面，输入与卡片状态由宿主保留）
 * @param opacityPercent 当前背景层不透明度（100 = 最不透明；刻度与区间归 `PanelBackdropOpacity`）
 * @param onOpacityPreview 拖动每帧：只用来实时预览背景层，不落盘
 * @param onCollapse 右上那颗「收起」：宿主把它接到 `dismissPanelToBubble`，这一页不自己判断该不该收
 * @param onOpacityCommit 松手一次：落盘由宿主处理
 * @param assistantOn 悬浮助手此刻开着没有——**只能来自真实窗口状态**（`WindowState != STOPPED`），
 *   本页不许为它存一份、也不给默认值：默认值＝宿主漏接线时屏幕自己猜一个状态
 * @param onAssistantEnable 拨开：交宿主走**既有那条启动链**（先查悬浮窗权限，本页不 `startActivity`）
 * @param onAssistantClose 拨关：交宿主走**与主页 ■ 同一颗**停止入口，最终 `stopSelf()` 经
 *   `onDestroy` 清理；本页不自己摘窗、也不接到「暂时隐藏」那一支
 * @param bubbleSizeDp 悬浮图标当前档位（dp）；三档刻度与回落在 `AppConfig.BubbleSizeTier`，
 *   这一页只跟着读数画选中态
 * @param onBubbleSizeChange 选一档：写盘与"窗口宽高/球径/角标/贴边同步"都由宿主与服务那一侧完成，
 *   本页不持有尺寸
 * @param replyCardVertical 回复卡片是否纵向排列；真值是 `ReplyCardLayout` 枚举（默认纵向），
 *   这里只摊成用户能看懂的一颗开关
 * @param onReplyCardVerticalChange 换方向：宿主只写那一格，**不重新请求、不清空当前回复**
 * @param onIntentChange 意图唯一的写口：`(正文, 启用)`——期限与状态那一族读数已经没有消费者
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
    intentSaving: Boolean = false,
    intentSaveReceipts: Int = 0,
    onIntentChange: (String, Boolean) -> Unit = { _, _ -> },
    knowledgeBases: List<KnowledgeBase> = emptyList(),
    activeKbName: String? = null,
    onSwitchKb: (String) -> Unit = {},
    /** 面板级通知的**同一条**（`NoticeBoard.current`）：设置页盖住主面的通知位，
     *  切库失败那一句「知识库切换没有成功，请重试」以前只画在主面那一格——
     *  用户在设置页里根本看不见，点了像没点。这里把同一条消息、同一个语气件画在页首，
     *  不是第二套失败反馈。`null` = 现在没有通知。 */
    notice: NoticeBoard.Notice? = null,
    onDismissNotice: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    onInputIntent: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 紧凑页头：一颗返回 + 页名 + 一颗收起。返回那颗的名字仍走 `R.string.common_back`、
        // 角色与热区由 [LbTextAction] 的图标档保证（不给 `LbTopBarLevel` 加新档，见 [SettingsHeaderDimens]）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SettingsHeaderDimens.ROW_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Spacer(Modifier.width(SettingsHeaderDimens.TITLE_GAP_DP.dp))
            // 返回字形降档：F3 档走 [LbTextActionGlyph.RowIcon]（16dp 字形 / 28dp 见方热区），
            // 与本体那颗齿轮同档；28dp 热区 < 行高 30，不被父约束裁掉。
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
            // 「收不起窗」那一半的落点：主面的收起长在 `PanelHeader` 那颗，齿轮一开整窗换成
            // 这一页就没了出口，所以这一页也要有一颗。**控件就是同一颗**（[PanelCollapseAction]，
            // 面板一族共用），不是第二颗手写的——以前这里走 `LbTextAction` 的 16dp 字形档，
            // 与本体那颗 20dp 的长得不一样，透明开高时两颗还在同一角叠影（用户 2026-10-12 点名：
            // "复用 复用 复用"）。名字仍走 `R.string.panel_collapse`；
            // **点击不在这页里存任何状态**——`onCollapse` 由宿主传下来（宿主那侧走
            // `dismissPanelToBubble`），收起只是收起到球，不等于关闭服务。
            Spacer(Modifier.weight(1f))
            PanelCollapseAction(onCollapse = onCollapse)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                // 滚动柱的首尾两段照原型 `.content{padding:5px 10px 17px}` 那两条数取档：
                // 顶 5px → [Spacing.sm]（4，页头到第一组组名那一小段，页头本身已降到 30 高、无分割线）、
                // 底 17px → [Spacing.xl]（16，最后一组到窗口下缘）。横向那 10px 归宿主外框一个主人
                // （`LoveBrainPanelScreen` 给的那圈），本页不重复缩。
                .padding(top = Spacing.sm, bottom = Spacing.xl)
        ) {
            // 面板级通知画在滚动柱的最上面：与主面通知位**同一条消息、同一个语气件**
            // （[KbNoticeBanner]），带建议卡的那几类仍归主面（确认动作在那边的一族里），
            // 这里只接普通通知——切库失败、保存回执这类一次性的话。
            notice?.let { current ->
                if (current.suggestion == null) {
                    // 通知与第一组组名之间那一小段呼吸垫在**通知自己的容器**上：
                    // 不另立高度 Spacer——页面正文那一层 `Spacer(Modifier.height(…)` 归零的尺
                    // 看守的是"逐项 Spacer 堆叠"那种旧形状，别让这一条把它数回去。
                    Box(Modifier.padding(bottom = Spacing.sm)) {
                        KbNoticeBanner(
                            text = current.message,
                            tone = if (current.channel == NoticeBoard.Channel.Warning) {
                                LbStateTone.Warning
                            } else {
                                LbStateTone.Success
                            },
                            onDismiss = { onDismissNotice(current.id) }
                        )
                    }
                }
            }
            // 「显示」组：一张外框装三行——透明度滑杆 / 悬浮图标大小 / 回复卡片。
            // 连续值=滑杆；三选一、二选一=分段。组名在框外，行间一条 1dp 分隔线。
            SettingsGroupTitle(stringResource(R.string.settings_group_display))
            Column(modifier = Modifier.settingsEntryGroupCard()) {
                SettingsOpacityEntry(
                    opacityPercent = opacityPercent,
                    onOpacityPreview = onOpacityPreview,
                    onOpacityCommit = onOpacityCommit
                )
                SettingsRowDivider()
                SettingsBubbleSizeEntry(
                    sizeDp = bubbleSizeDp,
                    onSizeChange = onBubbleSizeChange
                )
                SettingsRowDivider()
                SettingsReplyLayoutEntry(
                    vertical = replyCardVertical,
                    onVerticalChange = onReplyCardVerticalChange
                )
            }

            // 「内容」组：知识库排在意图前面不是排版口味——意图是按库隔离的那一份数据，
            // 先认对象、再改这一块的那件事，读序与"切库必须取对应数据"同一头。
            SettingsGroupTitle(stringResource(R.string.settings_group_content))
            Column(modifier = Modifier.settingsEntryGroupCard()) {
                SettingsKbSwitcherEntry(
                    knowledgeBases = knowledgeBases,
                    activeKbName = activeKbName,
                    onSwitchKb = onSwitchKb
                )
                SettingsRowDivider()
                SettingsIntentEntry(
                    intentEnabled = intentEnabled,
                    intentText = intentText,
                    // 写侧那两个真读数原样往下传：按钮"按不动"与"亮一下"读的都是它们，
                    // 这一族不留第二本"哪一笔还在飞"的账
                    saving = intentSaving,
                    saveReceipts = intentSaveReceipts,
                    onIntentChange = onIntentChange,
                    ownerKbName = activeKbName,
                    onInputIntent = onInputIntent
                )
            }

            // 「悬浮助手」组：只剩这一处开/关；页头那颗「收起」收的是面板成球，不是停服务。
            SettingsGroupTitle(stringResource(R.string.settings_group_assistant))
            Column(modifier = Modifier.settingsEntryGroupCard()) {
                SettingsAssistantEntry(
                    assistantOn = assistantOn,
                    onRequestEnable = onAssistantEnable,
                    onRequestClose = onAssistantClose
                )
            }
        }
    }
}

/**
 * 组名：画在组外框**外面**的那一行字，原型 `.section-title` 那一格的脸。
 * 它的上边距就是组与组之间的那一段呼吸——不再靠逐项 `Spacer` 堆。
 *
 * 四个数全部指回原型 + 既有 token，一个新档都不造（换算见对照原型换算表 `.section-title` 那一行）：
 * · 字阶 [AppTypography.labelSmall]（10sp）——原型 10.5px，仓库里没有 10.5 那一档，
 *   而 `labelSmall` 是唯一与它同数量级且**低于**行标签 `labelLarge`（12sp ≈ 原型 `.lbl` 的 12px）
 *   的那一颗。以前这里写的是 [AppTypography.labelMedium]（11sp Medium），比行标签只小一丝、
 *   又比它细，"组名是索引、行内容才是正文"那层主次就读不出来；
 * · 字重 [FontWeight.SemiBold]——原型 650 落在现成的 600 与 700 之间，取矮的那一档，
 *   不为一颗小标题新造字重（[AppTypography.labelSmall] 自带的 Medium 500 太轻）；
 * · 墨色 [TextSecondary]——原型 `--muted`，换算表里同值那一颗；
 * · 上下各 [Spacing.md]（8dp）——原型 `padding:10px 5px 7px` 那两个数与 8 各差 2 与 1，
 *   收到同一档。整行厚度 = 8 + 14（`labelSmall.lineHeight`）+ 8 = 30，与原型
 *   10 + ≈13 + 7 ≈ 30 同数；组与组之间看到的就只有这一行，不另加 `Spacer`。
 *
 * 横向**不给** padding：原型那 5px 是写在 `.content` 已给的 10px 之上的，读出来就是"组名贴着
 * 它那张框的左缘"，原型换算表的落点也写的"横内缩与卡片左缘对齐"。这一页横向内缩只有两个主人
 * （宿主外框 + 组外框），组名跟着框的左缘走，不再自己缩第三层。
 * 原型那条 `letter-spacing:.5px` **不采纳**：Compose 侧要新造 `TextStyle` 才拿得到字距。
 */
@Composable
private fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        style = AppTypography.labelSmall,
        color = TextSecondary,
        fontWeight = FontWeight.SemiBold,
        // 原型 `.section-title{padding:10px 5px 7px}`：上下那两个数各差 2 与 1，收到同一档 `Spacing.md`(8)；
        // 那 5px 横向内缩**这一版吃下了**——组名不贴着框的左缘，而是往里让一档，
        // 于是"索引行"与"它领的那张框"之间留出原型那层错位。仓库没有 5 那一档间距，
        // 取最近的那一颗 `Spacing.sm`(4)，不为一个 1dp 的差去新造 token。
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.md, start = Spacing.sm)
    )
}

