package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbRowLabel
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.model.KnowledgeBase

/**
 * 行根锚点（整页量具的锚点合同）：量具按这一颗找到"当前知识库"那一行。
 *
 * ⚠ 这一颗**同时就是那颗展开触发**：点击挂在行根上，整行 48dp 高、整行宽都点得着。
 * 以前只有右半边"名字+箭头"那一段接点击，真机回归里用户报的就是"知识库无法更改了"——
 * 这一族的读法是"这一行管的是这件事"，手指落在行里任何一处都是"我要改这一行"，
 * 只有贴着控件那一小块才响应，等于把入口藏起来（原型 `.kb` 那颗文字按钮管的是**长什么样**，
 * 不是"只有那块点得着"）。
 */
internal const val SETTINGS_ROW_KB_TAG = "settings-row-kb"

/** 展开后那一列的锚点（收起时整列不在树上，读不到这颗） */
internal const val SETTINGS_KB_OPTIONS_TAG = "settings-kb-options"

/** 展开列表里每一颗库的锚点：同 tag 多颗，按"恰有 N 颗"判列表长度 */
internal const val SETTINGS_KB_OPTION_TAG = "settings-kb-option"

/** 原型 `.arrow` 那一族的两个字形：收起 `›`、展开 `⌄`（这是排印符号，不随语言翻译） */
private const val KB_ARROW_COLLAPSED = "›"
private const val KB_ARROW_EXPANDED = "⌄"

/**
 * 箭头字阶（sp）：原型 `.arrow{font-size:17px;font-weight:300}`。
 * [AppTypography] 没有 17 那一档，而这一族的规矩是不许为单页字形去改全站 token ⇒
 * 按族里体例（`SettingsOpacityDimens` 同一写法）立成文件私有具名档，数只写这一次。
 */
private object KbSwitcherDimens {
    const val ARROW_FONT_SIZE_SP = 17
}

/**
 * 设置页里那一格**当前知识库**：默认只画一行——左边整页唯一那颗行标签"当前知识库"，
 * 右边**右对齐**的当前库名加一颗箭头（收起 `›`、展开 `⌄`，原型 `.kb`/`.arrow` 那一族）；
 * 点名字+箭头那一段才把可用的库列出来，再点收起。
 *
 * 为什么不再把所有知识库永久逐条铺在设置页上：一屏之下好几本库会把同一组里别的事情挤出视口，
 * 而"此刻在用哪一本"本来只需要看见一个名字。列表因此只在用户主动点开的时候存在。
 *
 * 三条真实的规矩，形状各自钉死：
 * · **展开态只是屏幕上这一行的开合**：本地一份 `remember`，不落盘、不进任何业务状态。
 *   切库唯一的真状态仍是宿主交下来的 [activeKbName]（那一侧要读盘才会换人）。
 *   这一颗也不自带卡壳：外框与横向内缩归这一组的外框那一层，这里只排行。
 * · **名字只在切换真的落地之后才跟着换**：这一格不预涂、不做乐观更新。点下去交出去的只是
 *   一句"请切到这本"，屏上那个名字仍读宿主那一份；盘上没换成那块库，屏幕上就不换成那块库。
 *   切库没落地时宿主本来就会在面板那一格说一句可重试的话，这一格不另发明第二套失败反馈，
 *   更不把失败涂成成功。
 * · **列出来的库来自仓库那一侧的目录扫描**：元数据读不出、坏 JSON、目录名与声明不符的那几本
 *   在数据层就被丢掉了（`data/KnowledgeCatalogStore.kt` 的 `readOne`），这一页不再自己筛第二遍。
 *   当前那本在列出来的一列里仍然显示（它自己就是答案），但按不动——再点一次不该重复投请求。
 *
 * 版式换算全部指回对照原型换算表那两行（`.kb` / `.kb-opt`）：
 * · 行壳 [Modifier.settingsEntryRow]（原型 `.item` 的 49 ≈ 48），行标签走整页唯一那颗
 *   [LbRowLabel]——10-11 那一版就是这里借了灰字 `labelMedium`，与别的行读出两种字阶；
 * · 库名 [AppTypography.labelMedium] + [TextSecondary]、靠右省略（原型 `.kb{color:#4c5870}` 是那一档中灰，
 *   不是近黑的正文色——这一格里"当前是哪本库"是被读的值，不是正文）；箭头 17 字阶 + [TextHint]
 *   （原型 `.arrow` 的数），它是那一段的一部分而不是第二颗可点控件；
 * · 展开条目每条自己垫到全站下限 [AppDimens.TOUCH_TARGET_MIN_DP]：原型 `.kb-opt` 那 32 是
 *   **显示**那一档，而这一列每一条都是独立点击目标（点一条=切那本库），32 在悬浮窗里点不中——
 *   要它的高亮与"只占一小段"，不要它的像素；圆角 [LoveBrainShape.sm]、
 *   当前那条 [PrimaryLight] 底 + `PrimaryDark` 字，其余透明底 + [TextHint] 字（`.kb-opt.current`）。
 * 热区：带点击语义的那一层（名字+箭头那一段）自己垫到全站下限
 * [AppDimens.TOUCH_TARGET_MIN_DP]，不另套空盒——外面再套一层大盒子等于没改。
 */
@Composable
internal fun SettingsKbSwitcherEntry(
    knowledgeBases: List<KnowledgeBase>,
    activeKbName: String?,
    onSwitchKb: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // 屏上那个名字只由宿主交下来的那一份决定：切库请求交出去、盘上还没换人时，它一个字都不动。
    val shownName = knowledgeBases.firstOrNull { it.name == activeKbName }
        ?.let { it.displayName.ifBlank { it.name } }
        ?: activeKbName
    val others = knowledgeBases.filterNot { it.name == activeKbName }
    if (shownName == null && others.isEmpty()) return
    // 展开永远可用——**哪怕目录里只有当前这一本**也要能点开，让用户亲眼看到"扫描就剩这一本"。
    // 上一版在这里挂过一道 `canOpen = others.isNotEmpty()` 的门：只有一本时藏箭头、禁用整行，
    // 结果真机目录一旦只剩一本，这一格就成了一颗点不动、无箭头的死标签（2026-10-15 真机报，
    // 1.4.0 的平铺卡从未这样过）——"目录少了库"必须画成一行看得见的列表，不许画成失语。
    val toggleDescription = stringResource(
        if (expanded) R.string.action_collapse else R.string.action_expand
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 行根 = 锚点 + 版式下限 + 整颗展开触发（三件事挂在同一颗节点上，见那颗常量的说明）。
            // 热区就在这一颗身上：`settingsEntryRow()` 已经把高度垫到全站下限，整行宽由
            // `fillMaxWidth` 给，所以这里不再另套一圈"买热区"的空盒。
            modifier = Modifier
                .settingsEntryRow()
                .clip(LoveBrainShape.sm)
                .clickable(role = Role.Button) { expanded = !expanded }
                // 读屏要说得出"现在点开还是收起"
                .semantics { contentDescription = toggleDescription }
                .testTag(SETTINGS_ROW_KB_TAG)
        ) {
            LbRowLabel(text = stringResource(R.string.settings_kb_current_label))
            // 名字 + 箭头那一段只管**摆**：吃掉标签之外的剩余宽度、内容贴右缘（原型 `.kb` 那一族的脸）。
            // 它自己不接点击——点击归上面那颗行根，否则同一格里会长出第二颗入口。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = shownName.orEmpty(),
                    style = AppTypography.labelMedium,
                    color = TextSecondary,
                    // 名字靠右（与同一组里别行的选择器同一侧），过长就省略号——想去看不属于这一格的
                    // 第二份全文，展开那一列里逐颗写的就是同一批名字。
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) KB_ARROW_EXPANDED else KB_ARROW_COLLAPSED,
                    fontSize = KbSwitcherDimens.ARROW_FONT_SIZE_SP.sp,
                    color = TextHint,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
        }

        if (expanded) {
            // ⚠ 展开那一段用的是一颗真正的纵向 `Column`，并且**今天不做展开动画**：
            // `AnimatedVisibility` 不是纵向容器，列表行那样的多颗直接子节点会被按 Box 叠放在一起；
            // 而把它的 `visible` 写死成 true 等于退场那一半永远播不到。两种坏形状都不如先不要动画。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 原型 `.kb-options{border-top + padding:4px 0 7px}`：那条线下面还要留上下两档，
                    // 列表才不像直接从上一行长出来。上 4 正好是 [Spacing.sm]，下 7 取 [Spacing.md]。
                    .padding(top = Spacing.sm, bottom = Spacing.md)
                    .testTag(SETTINGS_KB_OPTIONS_TAG)
            ) {
                // 展开那一列与上面那行之间：原型 `.kb-options{border-top:1px solid #e6e8ed}`
                // 与组内行分隔是**同一个色同一个宽**，所以走同一颗 `SettingsRowDivider()`，
                // 不在这一格里再画第二种线。
                SettingsRowDivider()
                knowledgeBases.forEach { kb ->
                    KbOptionLine(
                        kb = kb,
                        isCurrent = kb.name == activeKbName,
                        onPick = {
                            // 请求交出去就把这一列收起：屏幕上留着哪个名字仍由宿主那份真状态决定，
                            // 这一格不替切库记第二本账。
                            expanded = false
                            onSwitchKb(kb.name)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 展开列表里那一行库名（这一格内部重复的排版，不是一颗新组件；原型 `.kb-opt`）。
 *
 * 当前那本涂高亮（[PrimaryLight] 浅蓝底 + `PrimaryDark` 字——原型 `.kb-opt.current` 与 `:hover`
 * 同一张脸，不再另写"当前使用"那枚尾部标记）并**按不动**：`enabled = false` 仍交出禁用语义，
 * 读屏念得出"这一颗点不动"，而它不会因为看不见就让人以为这一列少了一本库。
 * 其余条目透明底 + [TextHint] 字。字号 [AppTypography.labelMedium]（原型 11px）。
 *
 * ⚠ 原型 `.kb-opt{min-height:32px}` 那一档**有意不吃**：这一列的每一条都是**独立的点击目标**
 * （点一条=切到那本库），而 32dp 在悬浮窗里是手指会点不中的那一类高度。
 * 全站那颗下限（[AppDimens.TOUCH_TARGET_MIN_DP]）由这一条**自己**买，不外包给行容器
 * ——展开列表没有"整行"可买。要的是原型那件语义（当前项高亮、其余可点、列表只占一小段），
 * 不是它那个像素；这是"只缩显示、不牺牲有效点击范围"在同一条纪律下的取舍。
 */
@Composable
private fun KbOptionLine(
    kb: KnowledgeBase,
    isCurrent: Boolean,
    onPick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .clip(LoveBrainShape.sm)
            .background(
                if (isCurrent) PrimaryLight else Color.Transparent,
                LoveBrainShape.sm
            )
            .clickable(enabled = !isCurrent, role = Role.Button, onClick = onPick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .testTag(SETTINGS_KB_OPTION_TAG)
    ) {
        Text(
            text = kb.displayName.ifBlank { kb.name },
            style = AppTypography.labelMedium,
            color = if (isCurrent) PrimaryDark else TextHint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
