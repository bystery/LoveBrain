package com.lovebrain.app.ui.panel

import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
// AlertDialog 已替换为 PanelModalHost，避免 Service 宿主 BadTokenException
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.ui.panel.suggest.InviteSuggestionCard
import com.lovebrain.app.ui.panel.suggest.SuggestAvoidHeader
import com.lovebrain.app.ui.panel.suggest.SuggestAvoidList
import com.lovebrain.app.ui.panel.suggest.SuggestStageCard
import com.lovebrain.app.ui.panel.suggest.SuggestTipCard
import com.lovebrain.app.ui.panel.suggest.SuggestUsageBar
import com.lovebrain.app.ui.panel.suggest.TipCategoryHeader
import com.lovebrain.app.ui.panel.suggest.groupTipsByCategory
import com.lovebrain.app.ui.panel.suggest.vectorMean
import com.lovebrain.app.viewmodel.LoveBrainViewModel

/**
 * 今日锦囊面板（v2）。
 *
 * 定位：参考性做法建议（类似日报/锦囊），不写知识库、不影响知识库。
 * 交互：空页面 → 点击"生成锦囊" → AI 生成 → 不满意可"重新生成"。
 * 已删除：刷新按钮、右上角 ×、"当前推荐"卡片、"进度追踪"卡片、时间戳、
 *         本阶段目标独立卡、做法卡全部按钮（/9）。
 * 阶段进度改用五维向量均值（方案 B）。
 */
@Composable
fun SuggestPanel(
    viewModel: LoveBrainViewModel,
    modifier: Modifier = Modifier
) {
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val isSuggesting by viewModel.isSuggesting.collectAsStateWithLifecycle()
    val currentVector by viewModel.currentVector.collectAsStateWithLifecycle()
    val streamingTips by viewModel.streamingTips.collectAsStateWithLifecycle()
    val suggestError by viewModel.suggestError.collectAsStateWithLifecycle()
    val intentConfig by viewModel.intents.config.collectAsStateWithLifecycle()
    val showIntentEditor by viewModel.intents.showEditor.collectAsStateWithLifecycle()
    val activeKb by viewModel.activeKb.collectAsStateWithLifecycle()

    // 锦囊加载文案改为「军师正在 xxx」轮换（AI 应用加载话术风格，参考"深度睡眠舱"AI 生成加载）
    val suggestPhrases = remember {
        listOf(
            "军师正在分析你们的关系…",
            "军师正在寻找今天该聊的话题…",
            "军师正在为你准备今日锦囊…"
        )
    }

    // 弹层放面板根部，不作为 LazyColumn 的某一 item，以免滚动使编辑器离开 composition
    Box(modifier = modifier.fillMaxWidth()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.lg)
            .background(SurfaceBase)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 标题栏：标题 + 持续意图 chip + 重新生成（三者共占一行）
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今日锦囊",
                        style = AppTypography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    // 持续意图 chip — 紧凑入口，复用标题行空档
                    if (activeKb != null) {
                        Spacer(Modifier.width(Spacing.sm))
                        IntentChip(
                            enabled = intentConfig.enabled,
                            text = intentConfig.text,
                            onClick = { viewModel.intents.openEditor() }
                        )
                    }
                }
                // #13 修复：重新生成按钮放大——从 labelMedium 文字 chip 升级为 labelLarge 按钮
                // 结果态升级为 Primary 实心底（与空态 CTA 同级，换一批是结果态唯一主动作）
                // 生成中点击 = 强行停止（替代原"禁用无反馈"）
                // 重新生成补按压反馈（复用标准件 0.96 scale + 120ms）
                val (regenInteraction, regenScale) = rememberPressScale(0.96f, "regenScale")
                Text(
                    if (isSuggesting) "生成中·点击停止" else "重新生成",
                    style = AppTypography.labelLarge,
                    color = if (isSuggesting) TextSecondary else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier

                        .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                        .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                        .clip(LoveBrainShape.md)
                        .background(if (isSuggesting) SurfaceInset else Primary)
                        .border(
                            AppDimens.BORDER_WIDTH_DP.dp,
                            if (isSuggesting) Border else Primary,
                            LoveBrainShape.md
                        )
                        .graphicsLayer { scaleX = regenScale; scaleY = regenScale }
                        .clickable(
                            interactionSource = regenInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                            if (isSuggesting) viewModel.stopSuggest()
                            else viewModel.regenerateSuggestion()
                        })
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
            }
        }

        when {
            // 生成中：已有流式 tips 则逐条渲染，否则转圈
            isSuggesting -> {
                if (streamingTips.isNotEmpty()) {
                    items(streamingTips, key = { it.id }) { tip ->
                        SuggestTipCard(tip = tip)
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PrimaryLight, LoveBrainShape.md)
                                .padding(Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = Primary,
                                    modifier = Modifier.size(AppDimens.LOADING_SPINNER_SIZE_DP.dp),
                                    strokeWidth = Spacing.xs
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text("军师正在整理其余做法…", color = PrimaryDark, style = AppTypography.bodySmall)
                            }
                        }
                    }
                } else {
                    item {
                        // 统一 AiLoadingRow（三点跳动 + 轮换文案 + 15s 超时提示）
                        AiLoadingRow(
                            phrases = suggestPhrases,
                            timeoutHintMs = 15_000L
                        )
                    }
                }
            }

            // 锦囊错误态（无 KB 引导/弱网超时/解析失败）——显示错因 + 重试
            suggestError != null -> {
                item {
                    // 这一档原来整块都是自己画的：`Box + .background(ErrorBg)` 里一列
                    // 居中 `Text`，尾部那颗重试还是本页唯一一处实心 `Text + background(Primary)`。
                    // 说法、标签（资源）、回调三样照旧，交回设计系统的是容器与那颗动作。
                    // ⚠ 行为差异要认账：原先那颗重试走的是 `generateSuggest()`
                    // （第一次生成那条路径），这里**保持不变**，没有顺手换成 regenerate。
                    LbEmptyState(
                        message = suggestError.orEmpty(),
                        tone = LbStateTone.Error,
                        container = LbStateContainer.Strip,
                        action = ScreenAction(stringResource(R.string.panel_retry_tap)) {
                            viewModel.generateSuggest()
                        }
                    )
                }
            }

            // 空页面：引导点击生成
            suggestion == null -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 空态图标：锦囊版（素材已预处理：贴边白底转透明+裁边，直接呈现不再裁圆）；
                        // tint=Unspecified 必须：M3 Icon 默认按内容色染色，位图彩图会被整体染黑（全黑根因）
                        Icon(
                            painter = painterResource(R.drawable.ic_empty_jinnang),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(AppDimens.EMPTY_ICON_CONTAINER_DP.dp)
                        )
                        Spacer(Modifier.height(Spacing.md))
                        // 空态文案
                        Text(
                            "生成今日专属做法，找话题、推进关系不卡壳",
                            color = TextHint,
                            style = AppTypography.bodySmall
                        )
                        Spacer(Modifier.height(Spacing.lg))
                        // 生成锦囊：空态唯一主动作，归 LbPrimaryButton（与 ResultArea「去设置」同族——
                        // 空态那颗单主动作由设计系统那颗四态按钮拥有，热区/角色由它一处保证）。
                        // 改之前是自画 `Text + .background(Primary)`，那一处品牌底自此由唯一主人画。
                        LbPrimaryButton(
                            state = LbButtonState.Idle,
                            label = "生成锦囊",
                            onClick = { viewModel.generateSuggest() }
                        )
                    }
                }
            }

            // 展示锦囊——轻量日常行动建议
            else -> {
                val plan = suggestion ?: return@LazyColumn
                // 顶部显示 usage 与 partial 标识
                item {
                    SuggestUsageBar(usage = plan.usage, isPartial = plan.partial)
                }
                // 阶段卡保留（阶段名 + 关系温度），但不再强制 goal
                item { SuggestStageCard(plan, vectorMean = vectorMean(currentVector)) }

                // 按 timingCategory 分组
                val groupedTips = groupTipsByCategory(plan.tips)
                for ((category, tips) in groupedTips) {
                    if (tips.isEmpty()) continue
                    item(key = "category-$category") {
                        TipCategoryHeader(category = category)
                    }
                    items(tips, key = { it.id }) { tip ->
                        SuggestTipCard(tip = tip)
                    }
                }

                // invite 保留但为可选（suggest.md 不再强制输出）
                plan.invite?.let { invite ->
                    if (invite.suggestion.isNotBlank()) {
                        item {
                            InviteSuggestionCard(
                                signal = invite.signal,
                                suggestion = invite.suggestion
                            )
                        }
                    }
                }

                // avoid 保留但为可选（suggest.md 不再默认长篇避雷）
                if (plan.avoid.isNotEmpty()) {
                    // 内容本体在 `ui/panel/suggest/SuggestResultContent.kt`；这里**仍按两格 item**摆：
                    // 标题一行、清单一张卡。合成一格就少算一档 `spacedBy(Spacing.md)`——那是改版式。
                    item { SuggestAvoidHeader() }
                    item { SuggestAvoidList(plan.avoid) }
                }
            }
        }
    }

    // 意图编辑弹窗——面板根部渲染，不在 LazyColumn 内部
    if (showIntentEditor) {
        IntentEditorDialog(
            text = intentConfig.text,
            enabled = intentConfig.enabled,
            expiry = intentConfig.expiry,
            expiryDate = intentConfig.expiryDate,
            status = intentConfig.status,
            onSave = { text, enabled, expiry, expiryDate, status ->
                viewModel.intents.save(text, enabled, expiry, expiryDate, status)
            },
            onDismiss = { viewModel.intents.dismissEditor() }
        )
    }
    } // close Box
}

// ═══════════ 持续意图 UI 组件（锦囊面板内紧凑入口） ═══════════

/**
 * 持续意图 chip — 紧凑入口，复用标题行空档。归进设计系统那颗 [LbChip]。
 * enabled=true 时高亮显示，点击打开编辑弹窗。
 *
 * 它是**入口**不是**选项**：点下去开编辑器，这一颗自己从不"在哪一格" ⇒
 * [LbChipInteraction.Action]。角色这一颗原来**一个都没声明**（`clickable` 没写 `role`），
 * 归并后由组件发 `Role.Button`——那是 §6.5 :532 要的那一栏，不是顺手升级；
 * 语义树里仍然**不发** `selected`：「开/关」这件事本来就写在自己的名字里
 * （`意图·xxx` / `意图·关`），那里才是它的规范位。
 *
 * ⚠ 名字里的 `enabled` 走的是组件的 `selected` **颜色分支**（选中档 = 开着那一套底/描边/字色），
 * 不能交成组件的 `enabled`：意图关掉时这颗仍然点得动、仍然要开得编辑器——那是今天的交互。
 *
 * 档位 = [LbChipStyles.pill]（标题行里那颗紧凑胶囊）再钉上它自己的高度：
 * 圆角 `LoveBrainShape.full`、胶囊高 22dp、字 `labelSmall`、文案钉一行、
 * 开着 `PrimaryLight` 底 + `PrimarySubtle` 描边 + `PrimaryDark` 字、
 * 关着 `SurfaceInset` 底 + `Border` 描边 + `TextHint` 字、左右内边距 [Spacing.sm]、
 * 竖直内边距 0、按压 0.92、不加对勾。
 *
 * ⚠ 这一颗**没接**全局那颗 48dp 下限（`touchFloor = false` 就是 pill 那一档的形状，
 * 也是它改之前的形状——那条链上从来只有一个钉死的 `height(22.dp)`，没有 `heightIn`）。
 * 热区仍是 22dp 高，归并按原样交出去：垫上去会把这张标题行的版式换掉，
 * 那是换脸不是归并。这一笔是**既有欠账**，登记着，与有效期那一排同一处理。
 */
@Composable
private fun IntentChip(
    enabled: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val label = if (enabled) {
        if (text.isNotBlank()) "意图·${text.take(8)}${if (text.length > 8) "…" else ""}"
        else "意图·未设"
    } else {
        "意图·关"
    }
    LbChip(
        label = label,
        selected = enabled,
        onClick = onClick,
        interaction = LbChipInteraction.Action,
        style = LbChipStyles.pill.copy(pillHeight = 22.dp)
    )
}

/**
 * 意图编辑浮层的长度上限——超限要**看得见地**提示，不许静默吞掉。
 */
private const val INTENT_MAX_LENGTH = 200

/**
 * 持续意图编辑弹窗（§6.1 归并）——浮层那一半的形状归设计系统的 `LbModalSheet`，这一层只转参数。
 *
 * 为什么这一族不能用 Material 的 `AlertDialog`：面板跑在 `TYPE_APPLICATION_OVERLAY` 窗口里，
 * 那里没有合适的 activity token，起 Dialog 窗口会直接抛 `WindowManager.BadTokenException`——
 * 那正是 `LbModalSheet` 存在的理由（见它文件头的 KDoc 与 `SheetProbeTest`）。
 * 归并之前这里已经站对了所有者，但**壳里还自己排了一遍版面**：标题、表单、两颗出口
 * 全摊在这颗浮层里，于是它既画浮层又管排版。现在壳只负责"起一扇浮层"，
 * 标题与表单交给 [IntentEditorBody]——与 `ProviderEditDialog` + `ProviderFormBody` 同一分工。
 *
 * `internal` 是为了让 `IntentEditorSheetMergeTest` 挂**生产那一颗**（连同它的浮层壳），
 * 而不是给测试另开一只旁门（同一形见 `ProviderFormBody`）。
 */
@Composable
internal fun IntentEditorDialog(
    text: String,
    enabled: Boolean,
    expiry: com.lovebrain.app.model.IntentExpiry = com.lovebrain.app.model.IntentExpiry.UNTIL_DONE,
    expiryDate: String = "",
    status: com.lovebrain.app.model.IntentStatus = com.lovebrain.app.model.IntentStatus.ACTIVE,
    onSave: (String, Boolean, com.lovebrain.app.model.IntentExpiry, String, com.lovebrain.app.model.IntentStatus) -> Unit,
    onDismiss: () -> Unit
) {
    LbModalSheet(onDismissRequest = onDismiss) {
        IntentEditorBody(
            text = text,
            enabled = enabled,
            expiry = expiry,
            expiryDate = expiryDate,
            status = status,
            onSave = onSave,
            onDismiss = onDismiss
        )
    }
}

/**
 * 意图编辑器本体——浮层里除了遮罩与卡片那两层的**全部**内容：标题、启用开关、有效期、
 * 日期与正文输入框、两颗出口。
 *
 * 标题原来没有槽位（这一格的第一句就是那段说明文字），归并时把它抬进 `LbModalSheetTitle`，
 * 于是这一扇与 `RecordSentFlowHost`、纠正中心、点踩原因面板长成同一个形状：
 * 标题 + 正文 + `LbModalSheetActions`，动作仍是同一份 `LbDialogAction` 词表。
 * ⚠ **没有新增任何文案**：那句话只是换了槽位（账记在 `UiStringLiteralBudgetTest` 的两栏）。
 */
@Composable
internal fun IntentEditorBody(
    text: String,
    enabled: Boolean,
    expiry: com.lovebrain.app.model.IntentExpiry,
    expiryDate: String,
    status: com.lovebrain.app.model.IntentStatus,
    onSave: (String, Boolean, com.lovebrain.app.model.IntentExpiry, String, com.lovebrain.app.model.IntentStatus) -> Unit,
    onDismiss: () -> Unit
) {
    var editText by remember { mutableStateOf(text) }
    var editEnabled by remember { mutableStateOf(enabled) }
    var editExpiry by remember { mutableStateOf(expiry) }
    var editExpiryDate by remember { mutableStateOf(expiryDate) }
    var editStatus by remember { mutableStateOf(status) }
    val overLimit = editText.length > INTENT_MAX_LENGTH

    Column(modifier = Modifier.fillMaxWidth()) {
        LbModalSheetTitle("设置一个持续的对话目标（如\"约她周末看电影\"），军师每轮生成时都会参考。")
        Spacer(Modifier.height(Spacing.md))
        // 开关行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用", style = AppTypography.labelLarge, color = TextPrimary)
            val (toggleInteraction, toggleScale) = rememberPressScale(0.92f, "intentToggleScale")
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(24.dp)
                    .graphicsLayer { scaleX = toggleScale; scaleY = toggleScale }
                    .clip(LoveBrainShape.full)
                    .background(if (editEnabled) Primary else SurfaceInset, LoveBrainShape.full)
                    .border(AppDimens.BORDER_WIDTH_DP.dp, if (editEnabled) Primary else Border, LoveBrainShape.full)
                    .clickable(interactionSource = toggleInteraction, indication = null) { editEnabled = !editEnabled },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    Modifier
                        .offset(x = if (editEnabled) 20.dp else 2.dp)
                        .size(20.dp)
                        .clip(LoveBrainShape.full)
                        .background(Color.White)
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        // 有效期选择
        Text("有效期", style = AppTypography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            IntentExpiryChip("直到完成", editExpiry == com.lovebrain.app.model.IntentExpiry.UNTIL_DONE) {
                editExpiry = com.lovebrain.app.model.IntentExpiry.UNTIL_DONE
            }
            IntentExpiryChip("仅今天", editExpiry == com.lovebrain.app.model.IntentExpiry.TODAY) {
                editExpiry = com.lovebrain.app.model.IntentExpiry.TODAY
            }
            IntentExpiryChip("指定日期", editExpiry == com.lovebrain.app.model.IntentExpiry.DATE) {
                editExpiry = com.lovebrain.app.model.IntentExpiry.DATE
            }
        }
        // 指定日期时显示日期输入框
        if (editExpiry == com.lovebrain.app.model.IntentExpiry.DATE) {
            Spacer(Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, LoveBrainShape.md)
                    .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.md)
                    .padding(Spacing.md)
            ) {
                if (editExpiryDate.isEmpty()) {
                    Text("输入日期（如 2026-12-31）", color = TextHint, style = AppTypography.bodyMedium)
                }
                BasicTextField(
                    value = editExpiryDate,
                    onValueChange = { editExpiryDate = it },
                    textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // 状态操作——已完成时可标记完成
        if (editEnabled && editStatus == com.lovebrain.app.model.IntentStatus.ACTIVE) {
            Spacer(Modifier.height(Spacing.sm))
            val (completeInteraction, completeScale) = rememberPressScale(0.96f, "intentCompleteScale")
            Text(
                "标记为已完成",
                style = AppTypography.labelSmall,
                color = TextHint,
                modifier = Modifier
                    .graphicsLayer { scaleX = completeScale; scaleY = completeScale }
                    .clickable(interactionSource = completeInteraction, indication = null) {
                        editStatus = com.lovebrain.app.model.IntentStatus.COMPLETED
                        editEnabled = false
                    }
                    .padding(vertical = Spacing.xs)
            )
        }
        Spacer(Modifier.height(Spacing.md))
        // 文本输入框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .background(SurfaceCard, LoveBrainShape.md)
                .border(
                    AppDimens.BORDER_WIDTH_DP.dp,
                    if (overLimit) Error else PrimarySubtle,
                    LoveBrainShape.md
                )
                .padding(Spacing.md)
        ) {
            if (editText.isEmpty()) {
                Text(
                    "输入你的持续意图…",
                    color = TextHint,
                    style = AppTypography.bodyMedium
                )
            }
            BasicTextField(
                value = editText,
                onValueChange = { editText = it },
                textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        // 字数 + 超限提示
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${editText.length}/$INTENT_MAX_LENGTH",
                style = AppTypography.labelSmall,
                color = if (overLimit) Error else TextHint
            )
        }
        if (overLimit) {
            Text(
                "意图过长，请精简到 $INTENT_MAX_LENGTH 字以内",
                style = AppTypography.labelSmall,
                color = Error,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
        // 统一操作行：超限时「保存」是**灰着还在**，不是消失——用户得知道少填了什么
        Spacer(Modifier.height(Spacing.md))
        LbModalSheetActions(
            listOf(
                LbDialogAction("取消", onDismiss, tone = LbDialogActionTone.Muted),
                LbDialogAction(
                    label = "保存",
                    enabled = !overLimit,
                    onClick = {
                        onSave(editText.trim().take(INTENT_MAX_LENGTH), editEnabled, editExpiry, editExpiryDate.trim(), editStatus)
                    }
                )
            )
        )
    }
}

/**
 * 有效期选择 chip——归进设计系统那颗 [LbChip]。
 *
 * 语义是**一组里互斥的单选**（"直到完成 / 仅今天 / 指定日期"三选一）：原来自己画的
 * 那条链上把"现在选哪一档"写进字面前缀（`✓ $label`）与底色（Primary / SurfaceCard），
 * 归并后这两件事分别由 [LbChipInteraction.Single] 在语义树里发 `Selected`、由
 * [LbChipStyles.filled] 画实心选中档——同一件事只剩一份。
 *
 * 档位用 [LbChipStyles.filled] 再 `.copy` 两处：字色未选那一档原来是 `TextHint`（比
 * `filled` 默认的 `TextSecondary` 还浅一档），左右内边距原来是 `Spacing.sm`、上下 `Spacing.xs`
 * （比 `filled` 默认的 `lg / sm` 更紧凑——这组三颗挤在一行）。两处都是调用方用 `.copy`
 * 调数，不是在页面里再画一条 Modifier 链（同 `IntentChip` 用 `pill.copy(pillHeight = ...)`）。
 */
@Composable
private fun IntentExpiryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    LbChip(
        label = label,
        selected = isSelected,
        onClick = onClick,
        interaction = LbChipInteraction.Single,
        style = LbChipStyles.filled.copy(
            textColor = TextHint,
            paddingHorizontal = Spacing.sm,
            paddingVertical = Spacing.xs
        )
    )
}
