package com.lovebrain.app.ui.panel.counseling

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.AppConfig
import com.lovebrain.app.R
import com.lovebrain.app.ui.panel.DraggableDivider
import com.lovebrain.app.ui.panel.MarkdownText
import com.lovebrain.app.ui.panel.TriangleArrow
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.LoveBrainViewModel

/** 谈心面板内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object CounselingDimens {
    const val PLACEHOLDER_TOP_PAD_DP = 1    // 输入框占位文字顶部对齐内边距
}

@Composable
fun CounselingPanel(
    viewModel: LoveBrainViewModel,
    onFocusChange: (Boolean) -> Unit,
    onInputIntent: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // LB-LIFE-01：追问输入框独立焦点回调与输入意图
    onFollowUpFocusChange: ((Boolean) -> Unit)? = null,
    onFollowUpInputIntent: (() -> Unit)? = null,
    inputId: String = "counseling_main"
) {
    val draft by viewModel.composer.counselingDraft.collectAsStateWithLifecycle()
    val result by viewModel.counselingResult.collectAsStateWithLifecycle()
    val error by viewModel.counselingError.collectAsStateWithLifecycle()
    val isCounseling by viewModel.isCounseling.collectAsStateWithLifecycle()
    val streaming by viewModel.counselingStreaming.collectAsStateWithLifecycle()

    var inputHeight by remember { mutableFloatStateOf(48f) }
    val density = LocalDensity.current.density
    // 入口的占位文案与读屏名字共用一条资源（第6节第5条 第②栏的口径）
    val inputLabel = stringResource(R.string.counseling_input_hint)

    Column(modifier = modifier.fillMaxWidth()) {
        // 第6节第4条 位移修复：输入框高度从固定 100dp 改为 heightIn(min = ...)，
        // 与回复档的 PanelTextInput（heightIn(min = INPUT_ROW_HEIGHT_DP)）同一写法。
        // 之前 100dp 固定高度 vs 回复档 48dp 最小高度 = 高差 28dp（实测 76dp vs 48dp）。
        // 现在两档都走 heightIn，高度由内容决定，最小值统一。
        // DraggableDivider 仍调整 inputHeight（最小高度），用户可以往上拖让输入框更高。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = inputHeight.dp)
                .clip(LoveBrainShape.lg)
                .background(SurfaceCard)
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
                // 编辑意图：用户触碰输入框区域 → 通知 FloatingService 进入 EDITING
                // 使用 pointerInput 检测 Press 事件但不消费，让 BasicTextField 仍能收到点击获焦
                .then(if (onInputIntent != null) Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) {
                                onInputIntent()
                            }
                        }
                    }
                } else Modifier)
                .padding(horizontal = Spacing.lg)
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { viewModel.setCounselingDraft(it) },
                textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = SolidColor(Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .verticalScroll(rememberScrollState())
                    // 第6节第5条 :532 第②栏：这颗输入框此前**没有任何可读名字**（本机实量
                    // 336x76dp、文案与 contentDescription 两样都空，读屏只念「编辑框」）。
                    // 它比输入框自己的那行占位文案更糟：占位文案只在草稿为空时才画，
                    // 用户打了一个字之后连那句话都没了。
                    // 名字与占位文案**共用同一条资源**（不是再编一份只给读屏看的副本）。
                    // ⚠ `stringResource` 必须在 semantics 块**外面**先取好（那块不是 composable 上下文）。
                    .semantics { contentDescription = inputLabel }
                    .onFocusChanged { state ->
                        onFocusChange(state.isFocused)
                    }
            )

            if (draft.isEmpty()) {
                Text(
                    text = inputLabel,
                    color = TextHint,
                    style = AppTypography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(top = CounselingDimens.PLACEHOLDER_TOP_PAD_DP.dp)
                )
            }
        }

        DraggableDivider(
            onDragDelta = { delta ->
                inputHeight = (inputHeight + delta / density).coerceIn(48f, 200f)
            }
        )

        // 谈心快速模板：未在谈心中、无结果时，显示常见困惑模板 chip（全部 6 个，一行横向滚动）
        //（用户实测"示例没了"）：不再要求 draft.isEmpty()，也不折叠成 2 个——
        // 只要不在谈心中且无结果就全部展示；点击模板直接填入输入框。
        if (!isCounseling && result == null && error == null) {
            Spacer(Modifier.height(Spacing.xs))
            CounselingTemplateChips(onTemplateSelect = { viewModel.setCounselingDraft(it) })
        }

        // 字数提示：超过 100 字时显示，超过 500 字变橙色提醒
        // 调研依据：NN/G 10 Heuristics #5 Error Prevention——提前提示而非事后报错
        if (draft.length > 100) {
            Text(
                text = "${draft.length} 字",
                color = if (draft.length > 500) Warning else TextHint,
                style = AppTypography.labelSmall,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = Spacing.xs)
            )
        }

        val canStart = draft.isNotBlank() && !isCounseling
        // 谈心 CTA（脉冲条 / 开始按钮）已抽出到 CounselingLoadingSection.kt
        CounselingPulseCta(
            isCounseling = isCounseling,
            canStart = canStart,
            onStart = { viewModel.generateCounseling(draft.trim()) },
            onStop = { viewModel.stopCounseling() }
        )

        Spacer(Modifier.height(Spacing.md))

        when {
            // 流式中的正文与"这一轮没落定结果、但屏幕上已经出现过正文"（超时/失败留下的
            // 那一段）共用下面这张卡，所以这里把两档并成一条：轮次结束时正文不撤、不重排、
            // 也不闪回占位。`result == null` 是故意的前提——有完整结果时仍由下面那一档接管，
            // 这里不许留上一条的残影。
            // 失败提示只补在这段正文**下面一行**，用的还是原来那条错误状态条的画法与同一颗
            // 重试回调：不替换正文、不新增警告块、不改错误条的措辞（措辞由生成侧给）。
            // "还在等第一个字"那格占位只在生成中画，所以它单独留 `isCounseling` 这个条件。
            isCounseling || (result == null && streaming.isNotBlank()) -> {
                if (isCounseling && streaming.isBlank()) {
                    CounselingLoading(Modifier.fillMaxWidth())
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(LoveBrainShape.lg)
                            .background(SurfaceCard)
                            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
                            .padding(Spacing.xl)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MarkdownText(
                            text = streaming,
                            color = TextPrimary,
                            fontSize = MarkdownBodyFontSize,
                            lineHeight = MarkdownBodyLineHeight
                        )
                    }
                }
                val endedError = error
                if (!isCounseling && endedError != null) {
                    Spacer(Modifier.height(Spacing.md))
                    LbEmptyState(
                        message = endedError,
                        tone = LbStateTone.Error,
                        container = LbStateContainer.Strip,
                        action = ScreenAction(stringResource(R.string.panel_retry_tap)) {
                            viewModel.generateCounseling(draft.trim())
                        }
                    )
                }
            }

            error != null -> {
                val err = error ?: return
                // 这一档原来是自画的 `Box + .background(ErrorBg)`：说明 + 那颗重试已经归过
                // 一次 `LbTextAction`，整块版式现在一起交回 `LbEmptyState`（Strip + Error），
                // 与结果区那一档从此同一处画法。
                // ⚠ 两页各抄一遍的那条"热区 ≥24dp"注释也跟着没了——同一句话抄两遍时，
                // 连"多少算达标"都会被各自抄一次（本机两档当时都量到 72x26dp、role=无）。
                // 重试仍是原来那颗：拿当前草稿重新发起 `generateCounseling`，一字未改。
                LbEmptyState(
                    message = err,
                    tone = LbStateTone.Error,
                    container = LbStateContainer.Strip,
                    action = ScreenAction(stringResource(R.string.panel_retry_tap)) {
                        viewModel.generateCounseling(draft.trim())
                    }
                )
            }

            result != null -> {
                val res = result ?: return
                // 追问历史：保存之前的问答对，让用户看到完整对话脉络
                // 调研依据：NN/G 10 Heuristics #1 Visibility of System Status——用户应能看到之前的对话上下文
                var followUpText by remember { mutableStateOf("") }
                // 谈心多轮历史：从磁盘恢复，实现重启不丢失
                // 调研依据：NN/G 10 Heuristics #1 Visibility of System Status——用户应能看到之前的对话上下文
                var counselingHistory by remember {
                    mutableStateOf(viewModel.loadCounselingHistory())
                }
                // 操作行（继续追问/重新开始）移到卡片顶部文字前方
                var showFollowUp by remember { mutableStateOf(false) }
                // : Canvas 箭头替代 Unicode ▾/▴
                val followUpArrowRotation by animateFloatAsState(
                    targetValue = if (showFollowUp) 0f else -90f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "followUpArrow"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(LoveBrainShape.lg)
                        .background(SurfaceCard)
                        .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
                        .padding(Spacing.xl)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ═══ 操作行置顶：继续追问 toggle + 重新开始（移到文字前方）═══
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 继续追问胶囊归进设计系统那颗 [LbChip]：胶囊形状（clip/sm + PrimaryLight 底 +
                        // PrimarySubtle 描边 + labelMedium/PrimaryDark/SemiBold 字 + lg/sm 内边距 + 0.92 按压）
                        // 逐项抄进 [LbChipStyles.soft] 的 `.copy`，不在页面里另画一条 Modifier 链。
                        // ⚠ 这是**动作入口**（点下去开/收追问区），不是"在哪一格"——故选
                        // [LbChipInteraction.Action]：`Role.Button`，语义树不发 `selected`；
                        // 展开/收起那句话仍走 `stateDescription`，与改前完全同一槽位。
                        // ⚠ 三角箭头仍留在胶囊**外**（[LbChip] 的标签是纯文案，没有 trailing-icon 槽，
                        // 开一个就是发明 API——红线）：改前箭头在胶囊右内边距里，改后在胶囊右沿外
                        // 一个 `Spacing.xs`——这是归并这一颗的已知外观变化，登记在此，不另开槽。
                        val followUpAnnouncement = stringResource(
                            if (showFollowUp) R.string.state_expanded else R.string.state_collapsed
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LbChip(
                                label = "继续追问",
                                onClick = { showFollowUp = !showFollowUp },
                                interaction = LbChipInteraction.Action,
                                modifier = Modifier.semantics { stateDescription = followUpAnnouncement },
                                style = LbChipStyles.soft.copy(
                                    radius = LoveBrainShape.sm,
                                    textStyle = AppTypography.labelMedium,
                                    textColorSelected = PrimaryDark,
                                    textColor = PrimaryDark,
                                    fontWeightSelected = FontWeight.SemiBold,
                                    fontWeight = FontWeight.SemiBold,
                                    backgroundSelected = PrimaryLight,
                                    background = PrimaryLight,
                                    borderSelected = PrimarySubtle,
                                    border = PrimarySubtle,
                                    pressedScale = 0.92f,
                                    markSelectedWithCheck = false
                                )
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            // ：共享三角箭头（原 Canvas Path 块与锦囊处逐字相同）
                            TriangleArrow(color = Primary, rotation = followUpArrowRotation)
                        }
                        // 清空重聊胶囊归进设计系统那颗 [LbChip]（动作入口，点下去清空历史）。
                        // 档位用 [LbChipStyles.neutral] 再 `.copy`：字色 `TextHint`、字号 `labelMedium`、
                        // 标签居中、内边距 `lg/sm`——逐项抄改前那条链，不在页面里另画一条 Modifier。
                        LbChip(
                            label = "清空重聊",
                            onClick = {
                                counselingHistory = emptyList()
                                showFollowUp = false
                                viewModel.clearCounselingAll()
                            },
                            interaction = LbChipInteraction.Action,
                            style = LbChipStyles.neutral.copy(
                                textStyle = AppTypography.labelMedium,
                                textColor = TextHint,
                                textColorSelected = TextHint,
                                labelAlignment = LbChipLabelAlignment.Center,
                                paddingHorizontal = Spacing.lg,
                                paddingVertical = Spacing.sm
                            )
                        )
                    }
                    // 追问输入区（展开时显示，跟随操作行置顶）
                    AnimatedVisibility(
                        visible = showFollowUp,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = Spacing.md)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(LoveBrainShape.md)
                                        .background(SurfaceInset)
                                        .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
                                        // 编辑意图：追问输入框同样需要进入 EDITING
                                        // 使用 pointerInput 检测 Press 事件但不消费
                                        .then(if (onFollowUpInputIntent != null) Modifier.pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    if (event.changes.any { it.pressed }) {
                                                        onFollowUpInputIntent()
                                                    }
                                                }
                                            }
                                        } else Modifier)
                                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                                ) {
                                    if (followUpText.isEmpty()) {
                                        Text(
                                            text = "想继续追问…",
                                            color = TextHint,
                                            style = AppTypography.bodySmall
                                        )
                                    }
                                    BasicTextField(
                                        value = followUpText,
                                        onValueChange = { followUpText = it },
                                        singleLine = false,
                                        maxLines = 3,
                                        textStyle = AppTypography.bodySmall.copy(color = TextPrimary),
                                        cursorBrush = SolidColor(Primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (onFollowUpFocusChange != null) {
                                                    Modifier.onFocusChanged { state ->
                                                        onFollowUpFocusChange(state.isFocused)
                                                    }
                                                } else Modifier
                                            )
                                    )
                                }
                                Spacer(Modifier.width(Spacing.sm))
                                // 追问动作按钮补按压反馈（复用标准件 0.96 scale + 120ms）
                                val (askInteraction, askScale) = rememberPressScale(0.96f, "askScale")
                                Box(
                                    modifier = Modifier
                                        .clip(LoveBrainShape.md)
                                        .background(
                                            if (followUpText.isNotBlank()) Primary else SurfaceInset,
                                            LoveBrainShape.md
                                        )
                                        .graphicsLayer { scaleX = askScale; scaleY = askScale }
                                        // 禁用态三件套——空输入时不可点（对齐  先例）
                                        .then(if (followUpText.isNotBlank()) Modifier.clickable(interactionSource = askInteraction, indication = null, onClick = {
                                            if (followUpText.isNotBlank()) {
                                                // 保存当前问答对到历史
                                                counselingHistory = counselingHistory + (draft to res)
                                                // 持久化历史到磁盘（重启不丢失）
                                                viewModel.saveCounselingHistory(counselingHistory)
                                                // 组装带上下文的追问消息
                                                //  只保留最近 N 轮问答，防 context length 超限
                                                val recentHistory = counselingHistory.takeLast(AppConfig.COUNSELING_MAX_HISTORY_ROUNDS)
                                                val contextMsg = buildString {
                                                    append("【前情提要】\n")
                                                    recentHistory.forEach { (q, a) ->
                                                        append("我问：").append(q.trim()).append("\n")
                                                        append("军师回复：").append(a.trim()).append("\n\n")
                                                    }
                                                    append("我问：").append(draft.trim()).append("\n")
                                                    append("军师回复：").append(res.trim()).append("\n\n")
                                                    append("【追问】").append(followUpText.trim())
                                                }
                                                viewModel.setCounselingDraft(followUpText.trim())
                                                followUpText = ""
                                                showFollowUp = false
                                                viewModel.generateCounseling(contextMsg)
                                            }
                                        }) else Modifier)
                                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "追问",
                                        color = if (followUpText.isNotBlank()) Color.White else TextSecondary,
                                        style = AppTypography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))
                    HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border)
                    Spacer(Modifier.height(Spacing.md))

                    // 问答历史展示块已删（截断摘要无阅读价值；历史仍作为追问上下文发送给模型）

                    // 当前军师回复
                    MarkdownText(
                        text = res,
                        color = TextPrimary,
                        fontSize = MarkdownBodyFontSize,
                        lineHeight = MarkdownBodyLineHeight
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
            }

            // 空状态引导：初始进入谈心模式时，输入框下方为空白，用户不知道会发生什么。
            // 调研依据：NN/G 10 Heuristics「Visibility of System Status」——系统应在合理时间内给用户恰当反馈。
            // 空状态用占位+引导文案告诉用户下一步该做什么，避免「空白焦虑」。
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(LoveBrainShape.lg)
                        .background(SurfaceInset)
                        .padding(Spacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // ✦ 符号 → App 图标（去符号化装饰，统一图标语言）
                        Box(
                            modifier = Modifier
                                .size(AppDimens.EMPTY_ICON_CONTAINER_DP.dp)
                                .clip(LoveBrainShape.full)
                                .background(PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bubble_simple),
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(Spacing.xxxl)
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = "说说你的困惑，军师帮你分析",
                            color = TextSecondary,
                            style = AppTypography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "感情里遇到难题，军师用公正法官的视角帮你理清",
                            color = TextHint,
                            style = AppTypography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
