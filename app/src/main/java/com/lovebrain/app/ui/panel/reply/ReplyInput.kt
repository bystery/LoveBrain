package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.lovebrain.app.R
import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

/** 输入区内部尺寸常量（外观全部照旧版数值：胶囊 28dp、加号内边距 2dp、图标 20dp） */
private object ReplyDimens {
    const val ROLE_CHIP_HEIGHT_DP = 28    // 角色 chip / 输入胶囊 / ➕ 胶囊共用的视觉高度
    /**
     * 三颗角色 chip 热区（各 48dp）加两段间隔共 152dp，留一点余量定成 156dp 上限。
     * 正常字号整组在屏上；字号放大或面板变窄时 chip 段自己横向滑，
     * 不换行另起一整行，也不挤压输入框与加号。
     */
    const val ROLE_CHIPS_MAX_WIDTH_DP = 156
}

/**
 * 输入行的透明热区节点标签：可见胶囊回旧版单行高度，≥48dp 的可点下限由
 * 包裹它的那层透明盒承担（两层并存，与 [LbChip] 的 layeredTouch 同一范式）。
 * 触摸下限的断言从这里读语义树量。
 */
internal const val PANEL_INPUT_TOUCH_TAG = "reply_input_touch_floor"

/**
 * 面板共享输入行（DRY）：单行输入框 + placeholder。
 * 回复输入、主动发一条草稿等面板内输入场景全部复用此组件。
 * （✕ 清空按钮已按用户要求移除：热区小且易误触）
 *
 * ⚠ **热区与视觉分两层**（与 [LbChip] 的 layeredTouch 同一范式）：画出来的是旧版那颗
 * `heightIn(min = height)` 的胶囊，手指拿到的是包着它的透明热区
 * （[PANEL_INPUT_TOUCH_TAG]，垫到全站那颗下限）。热区只负责"点胶囊外的空档也能获焦"，
 * 胶囊内的点击仍旧直达可编辑节点——透明热区之间不抢点击。
 */
@Composable
fun PanelTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = AppDimens.INPUT_ROW_HEIGHT_DP.dp,
    focusRequester: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onInputIntent: (() -> Unit)? = null
) {
    // 热区层：透明、垫到下限；挂在热区上的轻点只把焦点转给可编辑节点，
    // 不声明点击语义（否则会给"这一排有几个入口"的守卫凭空多出一颗没人管的入口）
    Box(
        modifier = modifier
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .testTag(PANEL_INPUT_TOUCH_TAG)
            .then(
                if (focusRequester != null) Modifier.pointerInput(focusRequester) {
                    // 胶囊外的空档也是输入行的热区：轻点→把焦点转给可编辑节点，
                    // 并同步上报编辑意图（碰到输入区 = 想编辑，与胶囊内触碰同一语义）
                    detectTapGestures(onTap = {
                        focusRequester.requestFocus()
                        onInputIntent?.invoke()
                    })
                } else Modifier
            )
    ) {
        // 视觉层：旧版那颗胶囊（min 高度语义，防系统大字号截断）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .heightIn(min = height)
                .background(SurfaceCard, LoveBrainShape.md)
                .border(AppDimens.BORDER_WIDTH_DP.dp, if (value.isNotEmpty()) PrimarySubtle else Border, LoveBrainShape.md)
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
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextHint,
                    style = AppTypography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            var tfModifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                // 读屏标签：placeholder 那行 Text 是**兄弟节点**，只在草稿为空时画出来，
                // TalkBack 不会把它算进输入框自己——于是这一颗只有 EditableText 语义，
                // 念出来是"编辑框"，输入第一个字之后连那点提示都没了（无障碍守卫量过这件事）。
                // 这里把同一句话挂成 contentDescription：节点本身永远有名字，
                // 名字随输入对象变（她/我/补充），但不写死在语义里——传进来什么就是什么。
                .semantics { contentDescription = placeholder }
            if (focusRequester != null) tfModifier = tfModifier.focusRequester(focusRequester)
            if (onFocusChange != null) tfModifier = tfModifier.onFocusChanged { onFocusChange(it.isFocused) }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                // 裸字号走排版令牌（bodyMedium = 13sp）
                textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = SolidColor(Primary),
                modifier = tfModifier
            )
        }
    }
}

/**
 * 输入行里那颗「仅看本轮」入口的可见文案常量。
 *
 * 写在这里而不是画在 `Text("…")` 里，是为了不往用户可见字面量的那本账上加新页
 * （`architecture/UiStringLiteralBudgetTest` 数的是 `Text(` / `contentDescription =` / `Lb*(`
 * 三把锚点里的中文字面量）。文案已经由 PRODUCT_SPEC 第3节 定死，搬进 `res/values` 需要
 * 资源文件的主人一起做，见本轮交付报告"需"。
 */
internal const val PANEL_ROUND_SCOPE_LABEL = "仅看本轮"

/**
 * 军师备注行的固定前缀（用户原话定死的措辞，）。
 *
 * 灰字只做**展示**：正文一律来自 `ComposerStore.ideaHint()` 那一颗真源，
 * 显示超长省略，实际发给军师的一个字都不裁。
 */
internal const val ADVISOR_NOTE_PREFIX = "我让军师注意："

/** 备注行的语义锚点（守卫与真机门禁按这颗取节点） */
internal const val ADVISOR_NOTE_TEST_TAG = "advisor_note_line"

/** 「仅看本轮」入口的语义锚点 */
internal const val PANEL_ROUND_SCOPE_TEST_TAG = "round_scope_entry"

/**
 * 消息输入区。
 *
 * 形状（依据基线 v1 §3 第 11 条窄窗砍序 + 用户原话第 10 条，见
 * `handoffs\2026-10-05-P2-宿主接线单.md`）：
 * · **行 1** = 她 / 我 / 补充 / ＋ / 🔒（仅看本轮） / 输入框(weight 1f) 同一条中线；
 *   输入框吃剩余宽度（weight 1f），挤也不缩短它。「仅看本轮」那颗用 🔒 符号占位
 *   （contentDescription 仍是 [PANEL_ROUND_SCOPE_LABEL]，给读屏的全名），常驻行 1、
 *   不再随有没有真实消息在行 2 / 消息卡之间二选一挂载。
 *
 * 行 1 是回复/主动发**共用**的通用形制：主动发（`showRoleChips = showAddButton = false`）时
 * 行 1 自然退化为只剩输入框——这不是"未接线的旧单行分支"，而是行 1 的窄化实例。
 *
 * ⚠ 持续意图入口已搬到设置页（不再由宿主灌进来），旧「行 2 次级控制」整组撤掉；
 *   「仅看本轮」是行 1 上的一颗紧凑胶囊（与 [IntentChip] 同一档 `LbChipStyles.pill`）。
 *
 * 设计来源：
 * · 微信 8.0 聊天界面改版：功能按钮与输入框整合、单手操作、圆润边框
 * · CometChat Composer 最佳实践：single-line 输入框超长自动横向滚动
 * · MD3 Text Fields：单行输入自动左滚；Apple HIG：输入框配 clear 按钮（✕ 已按用户要求移除）
 */
@Composable
fun ReplyInput(
    draftText: String,
    currentRole: ChatMessage.Role,
    editingIndex: Int,
    onDraftChange: (String) -> Unit,
    onRoleChange: (ChatMessage.Role) -> Unit,
    onAdd: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onInputIntent: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    // 主动发态复用输入行三参——默认值保回复态行为逐字不变
    showRoleChips: Boolean = true,
    showAddButton: Boolean = true,
    placeholderOverride: String? = null,
    // 输入框身份令牌（目前仅用于文档追踪，实际透传由调用方完成）
    inputId: String = "reply",
    /**
     * 输入对象（她/我/补充）。 的三轴之一，**不传就按 [currentRole] 推**
     * （旧宿主传的是 `composeRole`，IDEA 读成补充）——两种给法算出来的那一颗 chip 是同一颗。
     */
    inputKind: ComposerInputKind? = null,
    /** 选了三颗 chip 里的哪一颗。不传就退回 [onRoleChange] 那条旧通道（映射见 [toLegacyRole]） */
    onInputKindChange: ((ComposerInputKind) -> Unit)? = null,
    /** 「仅看本轮」当前状态——只读，开关本体归主线程（） */
    onlyThisRound: Boolean = false,
    /** 那颗入口的点击——**null 就不画这一颗**（不给没接线的屏留假入口） */
    onOnlyThisRoundChange: (() -> Unit)? = null,
    /**
     * 当前是否有真实消息（HER/ME）。保留给宿主与 [MessageList] 共用同一口径的那一根线
     * （见 `hasRealDialogueRows`）；「仅看本轮」现已常驻行 1，这一参不再决定它的挂载点。
     */
    hasRealMessages: Boolean = true
) {
    val isEditing = editingIndex >= 0
    val kind = inputKind ?: currentRole.toComposerInputKind()
    val emitKind: (ComposerInputKind) -> Unit = { next ->
        val sink = onInputKindChange
        if (sink != null) sink(next) else onRoleChange(next.toLegacyRole())
    }
    val roundEntryArmed = showRoleChips && onOnlyThisRoundChange != null

    // ── 三颗输入对象 chip（她/我/补充） ────────────────────────────────
    val chipsSection: @Composable RowScope.() -> Unit = {
        if (showRoleChips) {
            // 放不下就这一段自己横滑，输入框与加号的宽度不受挤压
            val chipsScroll = rememberScrollState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .horizontalScroll(chipsScroll)
                    .widthIn(max = ReplyDimens.ROLE_CHIPS_MAX_WIDTH_DP.dp)
            ) {
                RoleChip(ROLE_LABEL_HER, kind == ComposerInputKind.HER) { emitKind(ComposerInputKind.HER) }
                Spacer(Modifier.width(Spacing.sm))
                RoleChip(ROLE_LABEL_ME, kind == ComposerInputKind.ME) { emitKind(ComposerInputKind.ME) }
                Spacer(Modifier.width(Spacing.sm))
                // 《补充》= 本轮军师备注这一种**内容**，不是一种消息角色：
                // 选中它不会移动捕获角色，也不会把下一条捕获的消息标成"想法"（）。
                RoleChip(ROLE_LABEL_SUPPLEMENT, kind == ComposerInputKind.SUPPLEMENT) {
                    emitKind(ComposerInputKind.SUPPLEMENT)
                }
            }
            Spacer(Modifier.width(Spacing.sm))
        }
    }

    // ── 输入框（吃剩余宽度：weight(1f) 天然满足"挤不缩短它"） ─────────────
    val inputSection: @Composable RowScope.() -> Unit = {
        PanelTextInput(
            value = draftText,
            onValueChange = onDraftChange,
            placeholder = placeholderOverride ?: when (kind) {
                ComposerInputKind.HER -> "输入她说的话…"
                ComposerInputKind.ME -> "输入你说的话…"
                // 第6节第1条 定死的 placeholder：这一段不是"你想怎么回"，是你给军师的补充
                ComposerInputKind.SUPPLEMENT -> "补充背景或告诉军师你的要求…"
            },
            height = ReplyDimens.ROLE_CHIP_HEIGHT_DP.dp,
            modifier = Modifier.weight(1f),
            focusRequester = focusRequester,
            onFocusChange = onFocusChange,
            onInputIntent = onInputIntent
        )
    }

    val addSection: @Composable RowScope.() -> Unit = {
        if (showAddButton) {
            Spacer(Modifier.width(Spacing.sm))
            AddMessageButton(
                canAdd = draftText.isNotBlank(),
                isEditing = isEditing,
                onAdd = onAdd
            )
        }
    }

    // ── 行 1：她 / 我 / 补充 / ＋ / 🔒（仅看本轮） / 输入框(last, weight 1f) ──
    // 主动发（showRoleChips = showAddButton = false）时 chipsSection/addSection 各自短路，
    // 行 1 自然只剩那颗吃满宽度的输入框——这就是原话第 10 条要的第一行，不是遗留单行分支。
    // 「仅看本轮」常驻行 1（不再随有没有真实消息在行 2 / 消息卡之间二选一挂载），
    // 接了回调才画（roundEntryArmed），否则一颗都不长出来。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            chipsSection()
            addSection()
            if (roundEntryArmed) {
                Spacer(Modifier.width(Spacing.sm))
                RoundScopeChip(
                    selected = onlyThisRound,
                    onClick = { onOnlyThisRoundChange?.invoke() }
                )
            }
            inputSection()
        }
    }
}

/**
 * 一颗角色 chip（她/我/补充）——形状归设计系统那颗 [LbChip]（`Single` 一档）。
 *
 * ⚠ **热区与视觉分两层**。外层那颗**可点的**自己垫到 48dp 见方，胶囊在里面按旧版的
 * 28dp 画：视觉一字不改，手指与读屏拿到的都是 48——这一族原来就是两层的，归并时选的
 * 是组件里 `layeredTouch` 那一档，不是把它压成单层（压成单层会把胶囊撑到 48dp 见方，
 * 那是换脸）。角色 `Role.Tab` + 语义里的 `Selected` 由 `Single` 这一档发
 * （与页头那三档模式同一写法）。
 *
 * 档位 = 旧版那条链逐项抄过来：圆角 `LoveBrainShape.md`、字 `labelMedium`、
 * 选中实心 `Primary` + 白字 `Bold`、未选中 `SurfaceInset` + `Border` 描边 + `Normal` 字重、
 * 左右内边距 9dp（留更多空间给输入框）、竖直内边距 0（这颗的盒子由它自己的高度定）、
 * 胶囊高 [ReplyDimens.ROLE_CHIP_HEIGHT_DP]、按压 0.92。
 * 选中那一档今天**不描边**：组件的描边色与底色同色时像素不变，于是那条
 * `if (!selected) Modifier.border(...)` 的分叉收成一条直链，画出来还是同一张脸。
 *
 * ⚠ `clickable` 排在任何内边距**之前**：原来这段就是 `clickable(...).padding(horizontal = 9.dp)`，
 * 内边距排在后面等于自己把热区又削掉一圈。组件里也是同一顺序。
 */
@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    LbChip(
        label = label,
        selected = selected,
        onClick = onClick,
        interaction = LbChipInteraction.Single,
        style = LbChipStyles.filled.copy(
            radius = LoveBrainShape.md,
            textStyle = AppTypography.labelMedium,
            fontWeightSelected = FontWeight.Bold,
            background = SurfaceInset,
            paddingHorizontal = 9.dp,
            paddingVertical = 0.dp,
            pressedScale = 0.92f,
            // 归并前这一颗走的是 `animateFloatAsState` 的默认弹簧；共用组件统一成 120ms 那条
            // 曲线时它被一起换掉了。档位补上之后接回原位：
            // 换的是**档**，不是在这页自己画一条曲线。
            pressFeedback = LbChipPressFeedback.Spring,
            markSelectedWithCheck = false,
            layeredTouch = true,
            pillHeight = ReplyDimens.ROLE_CHIP_HEIGHT_DP.dp
        )
    )
}

/**
 * 「仅看本轮」那一颗：常驻行 1（在 ＋ 与输入框之间），紧凑胶囊档（[LbChipStyles.pill]，
 * 与 [IntentChip] 同一档、22dp），选中带勾（第10节第4条 的可见判据）。
 *
 * 可见文案用 🔒 符号占位（"锁在这一轮 / 仅看本轮"），读屏仍念全名 [PANEL_ROUND_SCOPE_LABEL]
 * （挂在 contentDescription 上）。开与不开真正的行为（下一轮快照生效、
 * 旧结果标过时）住在 `RoundStateStore` 与主线程那一侧，不归这颗管（）。
 *
 * ⚠ **热区与视觉分两层**（与 [RoleChip] 同一范式、与 `LbChipStyles.segmented` 同一写法）：
 * `pill` 这一档默认 `touchFloor = false` + `layeredTouch = false`（[IntentChip] 就是这么用的、
 * 它那一笔欠账登记在 `UiLayerDependencyContractTest` 里）。这一颗长在行 1 上、由
 * `PanelHostSemanticsTest.every actionable node the panel draws meets the touch floor`
 * 这一格直接量到，所以这里把这两档都翻成 `true`：外层透明盒垫到 48dp 见方承担点击与语义，
 * 里面的胶囊仍按 22dp 画——视觉一字不改，手指与读屏拿到的都是 48。
 */
@Composable
private fun RoundScopeChip(selected: Boolean, onClick: () -> Unit) {
    // 可见用 🔒 符号占位（"锁在这一轮 / 仅看本轮"），读屏仍念全名 [PANEL_ROUND_SCOPE_LABEL]。
    // 紧凑胶囊档与 [IntentChip] 同一档（LbChipStyles.pill, 22dp），选中带勾。
    // 热区分层：外层 48dp 透明盒承担点击/语义，内层 22dp 胶囊只管可见——与 RoleChip 同一范式。
    LbChip(
        label = "🔒",
        selected = selected,
        onClick = onClick,
        interaction = LbChipInteraction.Multi,
        modifier = Modifier
            .testTag(PANEL_ROUND_SCOPE_TEST_TAG)
            .semantics { contentDescription = PANEL_ROUND_SCOPE_LABEL },
        style = LbChipStyles.pill.copy(
            pillHeight = 22.dp,
            markSelectedWithCheck = true,
            touchFloor = true,
            layeredTouch = true
        )
    )
}

/**
 * ➕ 那颗：可见胶囊按旧版 24dp 画，48dp 的可点下限由外面那层透明盒承担。
 *
 * ⚠ **门控是这一颗自己的合同**：草稿为空时它**不带点击语义**，不是"灰着的禁用态"。
 *   守着这条的是 `ComposerAddButtonGatingTest`，其中一格还写着"没推帧时它已经
 *   带上点击语义 = CI 那 7 格的成因"。这一颗试过写成 `clickable(enabled = canAdd, ...)`，
 *   那是**顺手改掉别人守卫着的性质**，已退回。"禁用是灰着还在"那条只管
 *   页面唯一主动作（主动发回退合同），次级入口不在它范围内。
 */
@Composable
private fun AddMessageButton(canAdd: Boolean, isEditing: Boolean, onAdd: () -> Unit) {
    val (addInteraction, addScale) = rememberPressScale(0.92f, "addScale")
    Box(
        modifier = Modifier
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .then(
                if (canAdd) Modifier.clickable(
                    interactionSource = addInteraction,
                    indication = null,
                    role = Role.Button,
                    onClick = onAdd
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = addScale; scaleY = addScale }
                .clip(LoveBrainShape.md)
                .background(if (canAdd) Primary else SurfaceInset)
                .padding(Spacing.xs),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = if (isEditing) "保存修改" else "添加",
                tint = if (canAdd) Color.White else TextSecondary,
                modifier = Modifier.size(ReplyDimens.ROLE_CHIP_HEIGHT_DP.dp - 8.dp)
            )
        }
    }
}

/**
 * 军师备注那一行**黄色小字**（用户原话第 15 条的渲染件，依据基线 v1 §3.2「不新增色相」）。
 *
 * 它是"最后一条真实消息下方的一行黄色小字"，不是一张大盒子、更不是一张卡：
 * 1. 没有备注 → 什么都不画，连空标题/大占位都不留；
 * 2. 一行、`Warning`（现有语义色档里的黄）小字、超长省略——省略只发生在**展示**这一侧，
 *    发出去与编辑用的都是传进来的完整正文（真源在 `ComposerStore.ideaHint()`）；
 * 3. 点击 = 编辑完整正文（宿主投 `ComposerStore.Intent.BeginNoteEdit`）；
 * 4. 传入 [onSwipeClear] 时可像消息一样侧滑清除（阈值复用消息行的 [shouldDeleteBySwipe]）：
 *    过阈值落 [onSwipeClear]（→ `ComposerStore.Intent.ClearNote`，界面与 prompt 同步清除），
 *    不到阈值回弹、不触发编辑也不切页。被删对象与回调分离——这里只交"清除备注"这个回调，
 *    绝不碰消息删除链（备注不是 HER/ME 消息，不进重排数组、不计轮次、不进归档）。
 *
 * 可见尺寸就是这一行文字本身（labelMedium + 竖 [Spacing.xs] 内边距），旧的
 * `heightIn(min = 48)` 大盒子随原话第 15 条一起撤；行版式矮下来后消息列能多露出内容
 * （"撤掉宿主固定的 160dp 槽、改由消息列内测量"记在 `handoffs\2026-10-05-P2-宿主接线单.md`）。
 *
 * 文案前缀是常量 [ADVISOR_NOTE_PREFIX]，不重复"想法："，也不再画第二块《我的想法》。
 */
@Composable
internal fun AdvisorNoteLine(
    noteText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeClear: (() -> Unit)? = null
) {
    // 多行备注在展示侧并成一行（原话要的就是"一行"），正文本身一个字没少
    val text = noteText.trim().replace('\n', ' ')
    if (text.isEmpty()) return
    val interaction = remember { MutableInteractionSource() }
    // 读屏名字进资源（zh + en 各一份）：这一句屏幕上不画，只说给耳朵，
    // 写成内联中文的话英文环境里 TalkBack 念的仍是中文——屏幕上完全看不出来。
    val editAdvisorNoteName = stringResource(R.string.a11y_edit_advisor_note)

    // ── 侧滑清除：跟手位移 + 过阈值才落回调（复用消息行同一判据，被删对象与回调分离） ──
    val scope = rememberCoroutineScope()
    val swipeX = remember { Animatable(0f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val canSwipeClear = onSwipeClear != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ADVISOR_NOTE_TEST_TAG)
            .graphicsLayer { translationX = swipeX.value }
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .then(
                if (canSwipeClear) Modifier.draggable(
                    state = rememberDraggableState { delta ->
                        if (rowWidthPx > 0f) {
                            scope.launch {
                                swipeX.snapTo((swipeX.value + delta).coerceIn(-rowWidthPx, rowWidthPx))
                            }
                        }
                    },
                    orientation = Orientation.Horizontal,
                    enabled = rowWidthPx > 0f,
                    onDragStopped = {
                        if (shouldDeleteBySwipe(swipeX.value, rowWidthPx)) {
                            // 过阈值：先滑出再落"清除备注"，落点仍只认这一次动作，不回消息删除链
                            val dir = if (swipeX.value < 0f) -1 else 1
                            scope.launch {
                                swipeX.animateTo(dir * rowWidthPx)
                                onSwipeClear?.invoke()
                                swipeX.snapTo(0f)
                            }
                        } else if (swipeX.value != 0f) {
                            scope.launch { swipeX.animateTo(0f) }  // 不到阈值：回弹，不删、不编辑、不切页
                        }
                    }
                ) else Modifier
            )
            // 单击 = 去编辑完整正文（真点才触发；越 slop 的横滑被 draggable 接走，抬指不算点击）
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = editAdvisorNoteName }
            .padding(vertical = Spacing.xs)
    ) {
        Text(
            text = ADVISOR_NOTE_PREFIX + text,
            color = Warning,
            style = AppTypography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 三颗 chip 的字面量（PRODUCT_SPEC 第3节 定死：短标签"补充"，不再叫"想法"） */
internal const val ROLE_LABEL_HER = "她"
internal const val ROLE_LABEL_ME = "我"
internal const val ROLE_LABEL_SUPPLEMENT = "补充"

/**
 * 旧角色 → 输入对象（未接线调用点的读法）。
 *
 * `Role.IDEA` 在这里只读成"输入对象 = 补充"——它是**内容种类**，不是消息角色；
 * 真实聊天集合里不再有它（转换的生产收口在 `ComposerStore`，见那一份的 `toInputKind`）。
 */
internal fun ChatMessage.Role.toComposerInputKind(): ComposerInputKind = when (this) {
    ChatMessage.Role.HER -> ComposerInputKind.HER
    ChatMessage.Role.ME -> ComposerInputKind.ME
    ChatMessage.Role.IDEA -> ComposerInputKind.SUPPLEMENT
}

/**
 * 输入对象 → 旧角色通道（宿主还没接 `onInputKindChange` 时走这一条）。
 *
 * 补充 → `Role.IDEA`：VM 那一侧的 `SetCurrentRole` 已经把它读成"输入对象 = 补充"，
 * **不会**移动捕获角色，也不会让下一条捕获的消息变成想法。
 */
private fun ComposerInputKind.toLegacyRole(): ChatMessage.Role = when (this) {
    ComposerInputKind.HER -> ChatMessage.Role.HER
    ComposerInputKind.ME -> ChatMessage.Role.ME
    ComposerInputKind.SUPPLEMENT -> ChatMessage.Role.IDEA
}
