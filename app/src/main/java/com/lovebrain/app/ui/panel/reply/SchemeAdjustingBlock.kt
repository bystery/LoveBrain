package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.model.RewriteCommand

/** 一排放两颗：卡内净宽 142dp 里两颗粒子各约 69dp，四颗塞同一行就点不准了 */
private const val ADJUST_OPTIONS_PER_ROW = 2

/**
 * 方案卡调整态展示块——从 SchemeCard 抽离的纯展示子组件。
 *
 * 卡片进入调整态后只列四项（[RewriteCommand.UI_OPTIONS] 那份**可见名单**：
 * 更自然 / 换种说话 / 更温柔 / 自定义），在 158dp 卡内排成 2×2：
 * - 前三项走现成的 `onRewrite(command)`——传的是 enum 自己，不是屏幕上那行字，
 *   所以"换种说话"这个显示名和枚举里那条稳定名字不一致也不会把请求弄丢；
 * - 点"自定义"才出现输入框与提交，再点一次收回；
 * - 这里**不画**常驻的"取消/收起"那颗：收起判定归卡片那一侧（点卡片由
 *   `onToggleRewriteExpand` 直接收展），本块不留第二颗收起口。
 * - 不显示方向 chips（方向属于 Result-level）、不往正文下方追加内容。
 *
 * 尺寸全按卡内那一档：粒子视觉高 [SchemeCardDimens.ADJUST_PILL_HEIGHT_DP]、热区同数，
 * 输入框 28–36dp、一至两行、超出内部滚动，提交是小字。卡片本体横向档是固定 158x150
 * （纵向档宽铺满、高按同一颗 150 当下限按内容长高，见 `SchemeCardDimens.widthFor/heightFor`），
 * 横向档内容顶不下就整块纵向滚动兜底。
 *
 * ⚠ 那句"内容顶不下就整块纵向滚动兜底"只属于**横向档**（[contentHeightBounded]＝true，§3）：
 * 纵向档卡片自己按内容长高，外层那一条唯一的纵向滚动已经接住了溢出，这里再嵌一条就是
 * 在可滚的柱里再套一条无限高的滚——所以纵向档这一块的根 Column 不挂 verticalScroll。
 * 两档共用这一份实现，分叉只有"溢出归谁管"这一条。
 *
 * **草稿与"自定义开没开"这两样在方案卡那条链路上不住在本块里**（[customDraft] /
 * [isCustomInputOpen] 由卡片行按 identity.key 持有）：横向列表会把滑出视口的 item 连同其
 * `remember` 一起丢掉，状态留在本块就是——滑出去再滑回来一句草稿都没了，或者更糟：
 * 上一张卡的草稿落到同名标签的下一张卡上。调用方没传（null）时本块自己临时记一份，
 * 免得"点了没反应"。
 *
 * 收起判定归卡片那一侧——点在卡内的胶囊、输入框或滚动区域都不算收起，
 * 本块也不往卡片外涂任何东西；本块不持有闲置的收起形参（旧的那颗从未被
 * 本块调用过，已删——有槽必有线，没有就删槽）。
 * modifier（含 weight）由调用方传入。
 *
 * **焦点这一头本块是所有者**（§12.2「输入焦点生命周期」，与 `SettingsIntentEntry` 同一形状）：
 * `focusRequester` + 容器 `onFocusChanged` + 展开那一次点名 `onInputIntent`。
 * 这颗块以前一个焦点观测点都没有——"进入编辑"只剩 `LbFieldInput` 内部那颗自建入口，
 * 悬浮窗那一刻还挂着 NOT_FOCUSABLE，于是"点了自定义，光标在闪而键盘起不来"。
 * 失焦那一半本块只能观测、报不到宿主（这条链上没有失焦信号，接线单在账上）。
 */
@Composable
internal fun SchemeAdjustingBlock(
    onRewrite: (RewriteCommand) -> Unit,
    onCustomRewrite: (String) -> Unit,
    // null = 本块自己临时记一份（调用方没接线时的兜底，不让"点了没反应"发生）；
    // 非 null = 这份草稿/开合归卡片行按 identity.key 持有，本块不私存。
    modifier: Modifier = Modifier,
    customDraft: String? = null,
    onCustomDraftChange: (String) -> Unit = {},
    isCustomInputOpen: Boolean? = null,
    onCustomInputOpenChange: (Boolean) -> Unit = {},
    onInputIntent: (() -> Unit)? = null,
    /** true＝横向档（卡高固定，本块整块在卡内滚）；false＝纵向档（卡按内容长高，本块摊开不滚） */
    contentHeightBounded: Boolean = true
) {
    // 读屏名字外面取好再闭包进去：semantics 的 lambda 不是 @Composable
    val customHint = stringResource(R.string.scheme_custom_hint)
    var localDraft by remember { mutableStateOf("") }
    var localOpen by remember { mutableStateOf(false) }
    val draft = customDraft ?: localDraft
    val open = isCustomInputOpen ?: localOpen
    val writeDraft: (String) -> Unit = { text ->
        if (customDraft == null) localDraft = text
        onCustomDraftChange(text)
    }
    val writeOpen: (Boolean) -> Unit = { value ->
        if (isCustomInputOpen == null) localOpen = value
        onCustomInputOpenChange(value)
    }
    // ── 「进入编辑」信号（§12.2 输入焦点生命周期，自定义改写这一条路）──────────
    // 这一族以前只有 `LbFieldInput` 内部那颗自建的焦点入口，本块**一个焦点观测点都没有**：
    // · 点「自定义」只是把框画出来，焦点没进去、也没点名宿主进 EDITING ⇒
    //   悬浮窗这一刻还挂着 NOT_FOCUSABLE，光标在闪而输入法起不来；
    // · 焦点什么时候走的 likewise 无人知道。
    // 形状照 `SettingsIntentEntry.kt` 那两颗（`focusRequester` + 容器 `onFocusChanged`），
    // 不另发明一套。
    val customFocusRequester = remember { FocusRequester() }
    var fieldHadFocus by remember { mutableStateOf(false) }
    // 「刚展开」才抢焦点。`isCustomInputOpen` 归卡片行按 identity 持有，横向列表把滑出视口的
    // item 连同它的 remember 一起丢掉 ⇒ 滑回来时这一格可能**本来就开着**：那一下不是用户刚点开的，
    // 抢焦点就变成"滑一下键盘自己弹出来"（用户没要求的第二件事）。首帧把现值当成已处理过。
    var openHandled by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        val justOpened = open && !openHandled
        openHandled = open
        if (!justOpened) return@LaunchedEffect
        // 1. 先递"要输入"这一句：窗口还不可聚焦时，焦点请求到了也起不了输入法（幂等：
        //    宿主 `PanelInputFocusOwner.setMode` 同态直接 return）
        onInputIntent?.invoke()
        // 2. 框这一刻还没进树（`open` 刚翻，下面那个 `if` 要下一次组合才画）——过一帧再要。
        //    拿不到就算了：用户自己点那一下仍进编辑（`LbFieldInput` 的空档获焦是原有路径）。
        withFrameNanos { }
        runCatching { customFocusRequester.requestFocus() }
    }
    Column(
        // 两档唯一的内容侧分叉：横向档（卡高固定）整块在卡内滚；纵向档（卡按内容长高）不挂
        // 第二条滚动——外层那唯一一颗 `readScroll` 已经接住溢出。判据见文件头 [contentHeightBounded]。
        // 写成无花括号的 if/else：这一颗文件的括号形状会被 `UiLayerDependencyContractTest`
        // 那把"沿可点链往回读"的尺碰到，别在这里多出成对花括号。
        modifier = if (contentHeightBounded) modifier.verticalScroll(rememberScrollState()) else modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        RewriteCommand.UI_OPTIONS.chunked(ADJUST_OPTIONS_PER_ROW).forEach { rowCommands ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                rowCommands.forEach { command ->
                    val isCustom = command == RewriteCommand.CUSTOM
                    AdjustOption(
                        command = command,
                        isSelected = isCustom && open,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isCustom) writeOpen(!open)
                            else onRewrite(command)
                        }
                    )
                }
                // 单数那一行补齐占位，保证两颗与两行那格的宽度同一把尺
                repeat(ADJUST_OPTIONS_PER_ROW - rowCommands.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (open) {
            CustomRequirementEditor(
                value = draft,
                onValueChange = writeDraft,
                name = customHint,
                onInputIntent = onInputIntent,
                focusRequester = customFocusRequester,
                onFocusChanged = { focused ->
                    if (focused) {
                        // 焦点真的落进来了：再确认一次编辑态（上面那次是"请求"，这一句是"已成"）。
                        fieldHadFocus = true
                        onInputIntent?.invoke()
                    } else if (fieldHadFocus) {
                        // 失焦：正文已经由 `onCustomDraftChange` 实时交回卡片行（这一格不私存草稿），
                        // 所以这里没有要落的东西，只把"焦点已经不在了"这一件事实记下来。
                        // ⚠ 宿主那侧 `activeInputId` / EDITING **收不掉**：这条链路只有"进入编辑"
                        //   信号、没有失焦信号，接法记在接线单（`ResultArea` → `SchemeCard` 需把
                        //   `onInputFocusChange("scheme_adjust", focused)` 接上）。在那之前，
                        //   面板那一侧的交接（返回键 / 切页 / 关层）由
                        //   `LoveBrainPanelScreen` 那颗唯一出口兜住。
                        fieldHadFocus = false
                    }
                },
                onSubmit = { text ->
                    if (text.isNotBlank()) onCustomRewrite(text.trim())
                }
            )
        }
    }
}

/**
 * 调整区的一枚选项：粒子本身就是热区（[SchemeCardDimens.ADJUST_PILL_HEIGHT_DP] 见方一档），
 * 卡内这一族不借全站那颗 48dp 下限——四项各约 69dp 宽、28dp 高，2×2 摆在 158dp 卡内。
 */
@Composable
private fun AdjustOption(
    command: RewriteCommand,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.94f, "adjustOption${command.name}")
    Box(
        modifier = modifier
            .height(SchemeCardDimens.ADJUST_PILL_HEIGHT_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.sm)
            .background(
                if (isSelected) PrimarySubtle else PrimaryLight,
                LoveBrainShape.sm
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            // 名字就用粒子上那行字：读屏从子节点读得到，再挂一条 contentDescription 就是念两遍
            .padding(
                horizontal = SchemeCardDimens.TAG_HPAD_DP.dp,
                vertical = SchemeCardDimens.TAG_VPAD_DP.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            command.displayLabel,
            style = AppTypography.labelSmall,
            color = PrimaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 自定义那一格展开后的输入与提交：卡内紧凑档。
 *
 * 输入复用公共 [LbFieldInput]——五态视觉、焦点生命周期、编辑意图信号全走同一颗公共件，
 * 不再自己 Box + BasicTextField + pointerInput（§7.2「直接复用，不再自己做输入行为」）。
 *
 * 焦点这一头两颗都由调用方（[SchemeAdjustingBlock]）持有：
 * · [focusRequester] 交进 [LbFieldInput]——那颗组件的规则是"外部给了就用外部的，
 *   不另起第二个焦点拥有者"，所以本块不自建第二份；
 * · [onFocusChanged] 挂在**整行**容器上（与 `SettingsIntentEntry` 同一形状）：
 *   观测点是"这一格有没有焦点"，不是"那根光标有没有焦点"，点在行内的「确认改写」
 *   那颗上时容器仍算持焦，不会被误判成失焦。
 *
 * 卡内紧凑外形通过 [SchemeCardDimens.CUSTOM_FIELD_MIN_HEIGHT_DP]–[SchemeCardDimens.CUSTOM_FIELD_MAX_HEIGHT_DP]
 * 约束可见高度；提交是小字，不铺成整行大按钮。可编辑节点自己带读屏名字（placeholder 只是举例）。
 */
@Composable
private fun CustomRequirementEditor(
    value: String,
    onValueChange: (String) -> Unit,
    name: String,
    onSubmit: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onInputIntent: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state -> onFocusChanged(state.hasFocus) },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LbFieldInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = name,
            modifier = Modifier
                .weight(1f)
                .heightIn(
                    min = SchemeCardDimens.CUSTOM_FIELD_MIN_HEIGHT_DP.dp,
                    max = SchemeCardDimens.CUSTOM_FIELD_MAX_HEIGHT_DP.dp
                ),
            maxLines = 2,
            focusRequester = focusRequester,
            onInputIntent = onInputIntent
        )
        Text(
            "确认改写",
            style = AppTypography.labelSmall,
            color = if (value.isNotBlank()) PrimaryDark else TextHint,
            maxLines = 1,
            modifier = Modifier
                .clip(LoveBrainShape.sm)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { onSubmit(value) }
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}
