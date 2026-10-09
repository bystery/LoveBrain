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
import androidx.compose.ui.platform.LocalDensity
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

/**
 * 输入区内部尺寸常量（外观全部照旧版数值：胶囊 28dp、加号内边距 2dp、图标 20dp）。
 *
 * ⚠ **§7.1「先计算固定控件与必要间隔，剩余宽度全部给输入框」落在这一个 object 里**：
 * 行 1 的宽度预算只有这一份口径（[chipsCap]），不藏在某一颗粒子的 `widthIn` 里。
 * 三颗固定件各自的数都是从令牌与 core 那颗下限现取的，页面不抄第二份：
 *
 * · 一颗角色 chip 的**布局盒 = 高 48、宽随可见胶囊**——可见胶囊 28dp 高、"她/我"横向 29dp、
 *   "补充" 46dp（本机语义树实测）；过去外盒 `widthIn(min = 48)` 带来的 18dp 纯占位
 *   已由 core 那颗宽度轴旋钮收掉（[LbChipStyle.widthFloor]，本页认领 false），
 *   §2.1 第 1 行点名的那颗"额外触控盒"在宽度轴上不复存在；
 * · ➕ 那颗同理 48×48（[ADD_HIT]，可见胶囊 24dp）；
 * · 范围符号自己占的是**紧凑档那一颗热区**（[ReplyDimens.ROUND_SCOPE_HIT] = 28dp 见方，
 *   行宽按 [ROUND_SCOPE_RESERVE] 扣；上限由 `RoundScopeChipFootprintTest` 钉在 48 以下，
 *   再包一层 48 就是 §4.3 禁的"把紧凑档作废"）。
 *
 * ⇒ 所以行 1 能用的手段**只有让位**：chip 段按档收缩、输入框吃剩下的宽度、多出来的那颗
 * 由这一段本来就有的 `horizontalScroll` 接住。谁都不缩小可见尺寸，也不缩热区下限。
 */
internal object ReplyDimens {
    const val ROLE_CHIP_HEIGHT_DP = 28    // 角色 chip / 输入胶囊 / ➕ 胶囊共用的视觉高度

    /**
     * 三颗角色 chip **全可见那一档**的段宽：29 + 4 + 29 + 4 + 46 = 112（本机语义树逐颗实测，
     * 宽度轴幻影占位拆掉之后的内容定宽），留 4dp 余量 = 116。
     * 旧值 156 是三颗 48dp 大方盒那一代的账（3×48+2×4+4），F03 把宽度轴拆掉后它多留了
     * 40dp 空转——L1 复核挑中的正是这个：默认窗宽（行 268）余量 116 被 156 挡住，
     * "补充"在生产任何窗宽下都只能藏在横滑里，违反 §7.1"不能悄悄把必需控件藏掉"。
     */
    const val ROLE_CHIPS_MAX_WIDTH_DP = 116

    /**
     * 窄档时 chip 段改用的上限：两颗内容宽（29 + 4 + 29 = 62）留 4dp 余量 = 66，
     * 第三颗由既有的 `horizontalScroll` 滑出去接住（那是它本来设计的退路，不是藏控件：
     * 仍在树上、滑得到），把宽度还给输入框。可见尺寸与热区下限都不动——只让位，不缩小谁。
     * 旧值 96 同样是 48dp 大方盒那一代的账（两颗 48 加间隔正好放不下），拆掉占位后
     * 它多让了 30dp：默认窗宽下第三颗本可以全见（见 [ROLE_CHIPS_MAX_WIDTH_DP]）。
     */
    const val ROLE_CHIPS_TIGHT_MAX_WIDTH_DP = 66

    /**
     * 极窄兜底档的段宽：连两颗都放不下时只留一颗的位（48 = core 那颗下限同数，
     * 一颗完整胶囊 29 加余量装得下）。宽度轴拆掉后生产最低窗（行 228，余量 76）已经
     * 到不了这一档——留着是**安全网**（更窄的将来窗口/更大字号），不是现役档位。
     */
    val CHIP_BOX: Dp = AppDimens.TOUCH_TARGET_MIN_DP.dp

    /** ➕ 那颗的热区盒（同上，可见胶囊只有 24dp 画在里面） */
    val ADD_HIT: Dp = AppDimens.TOUCH_TARGET_MIN_DP.dp

    /**
     * 那颗范围符号的**热区边长**（两轴同一颗）：core 给紧凑族立的那颗具名矮档（28dp），
     * 不是全站那颗 48。为什么不能拿 48：§4.3 与 `RoleChipSemanticsTest`、
     * `RoundScopeChipFootprintTest` 三处钉着同一件事——再给这颗包一层 48dp 见方容器
     * 就是把悬浮窗的紧凑档作废。
     * ⚠ 这一颗的**可见**胶囊仍按 [RoundScopeChip] 里那一档的 22dp 画：这里垫的是外层
     *   那颗带切换语义的盒子，不是画出来的胶囊（热区与视觉两轴分开，与 [RoleChip] 同一范式）。
     * 行宽预算与热区**共用这一颗数**（[ROUND_SCOPE_RESERVE] 由它推出来），页面不抄第二份。
     */
    val ROUND_SCOPE_HIT: Dp = AppDimens.CARD_ACTION_HIT_DP.dp

    /**
     * 那颗范围符号自己占的横向档：热区那颗（[ROUND_SCOPE_HIT]）加一段间隔。
     * 本机读数是热区 28×28（外层垫的这颗），里面可见胶囊 14×22（开着时多出来的那个勾
     * 由这 4dp 余量接住）；它的**上限**不由这里说，由 `RoundScopeChipFootprintTest` 钉在 48 以下。
     */
    val ROUND_SCOPE_RESERVE: Dp = ROUND_SCOPE_HIT + Spacing.sm

    /**
     * 输入框的可见下限 = 它自己的左右内边距（`Spacing.lg`×2）+ **两颗正文**。
     *
     * §7.1 给输入框的那句是"正常宽度可见光标和可编辑内容"，§7.1 极窄那一行禁的是"遮住输入"，
     * 所以这一档取的是"看得见自己在打什么字"的最小份，不是舒适份（舒适份要行宽更松的档才谈得起）。
     * 字号放大时两颗字也要跟着变宽，所以乘 `fontScale`——
     * 这就是 §7.1 要的"较大系统字号至少做一次检查"在预算里的那一半落点。
     */
    fun inputFloor(fontScale: Float): Dp =
        Spacing.lg * 2 + (2f * AppTypography.bodyMedium.fontSize.value * fontScale).dp

    /**
     * 行 1 除 chip 段以外的固定件预算：必要间隔 + ➕ 的热区 + 那颗范围符号。
     *
     * 哪一件没上树（主动发那一档把 ＋ 与范围符号都短路了）就不扣它的宽——
     * 预算照着**这一排真实有什么**来算，不是照最满的那一档算（照最满算会在没有 ＋ 的屏上
     * 白白让 chip 段多让一档）。
     */
    fun tailWidth(fontScale: Float, withAdd: Boolean, withScope: Boolean): Dp {
        // chip 段与输入框之间那一段永远在
        var tail = Spacing.sm
        if (withAdd) tail += Spacing.sm + ADD_HIT
        if (withScope) tail += Spacing.sm + ROUND_SCOPE_RESERVE * fontScale
        return tail
    }

    /**
     * 行 1 的唯一一份宽度分配：`maxWidth` 先扣固定件与输入框那份可见下限，剩下的**余量**
     * 决定 chip 段拿哪一档。三档都是**让位**，不是缩小：
     *
     * · 余量 ≥ 116 → 三颗全可见（[ROLE_CHIPS_MAX_WIDTH_DP]）；
     * · 余量 ≥ 66 → 两颗在、第三颗横滑接住（[ROLE_CHIPS_TIGHT_MAX_WIDTH_DP]）；
     * · 再窄 → [CHIP_BOX] 兜底留一颗（生产现役窗宽到不了，见其注释）。
     *
     * 三档全部只看"扣完固定件与输入框下限之后还剩多少"——**不再有一行 360dp 的旧门槛**：
     * 那个数是 48dp 大方盒那一代推出来的，宽度轴拆掉后它在生产最高窗宽（行 318）下都进不来，
     * 等于永远强制"第三颗藏进横滑"，违反 §7.1"不能悄悄把必需控件藏掉"（L1 复核挑中，本行修正）。
     * 按 §7.1 末句把账说明白：**三颗全可见需要行宽 ≥ 116+92+60 = 268dp，即窗口 ≥ 300dp**
     * （PANEL_DEFAULT/MAX 达标；PANEL_MIN=260 那一档两颗全见、第三颗滑得到，输入框仍留住
     * 两颗正文的下限）。档位判定只吃常量与令牌，不吃字体测量——字号放大时 inputFloor 与
     * scope 预留跟着长，档位自然下沉，这是 §7.1"较大系统字号至少做一次检查"的几何那一半。
     */
    fun chipsCap(maxWidth: Dp, fontScale: Float, withAdd: Boolean, withScope: Boolean): Dp {
        val room = maxWidth - tailWidth(fontScale, withAdd, withScope) - inputFloor(fontScale)
        return when {
            room >= ROLE_CHIPS_MAX_WIDTH_DP.dp -> ROLE_CHIPS_MAX_WIDTH_DP.dp
            room >= ROLE_CHIPS_TIGHT_MAX_WIDTH_DP.dp -> ROLE_CHIPS_TIGHT_MAX_WIDTH_DP.dp
            else -> CHIP_BOX
        }
    }
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
 * 形状（§7.1 固定顺序与空间分配；窄窗那一档的依据另见基线 v1 §3 第 11 条与
 * `handoffs\2026-10-05-P2-宿主接线单.md`）：
 * · **行 1 的视觉顺序** = 她 / 我 / 补充 / 输入框(weight 1f) / ＋ / 范围符号（仅看本轮） 同一条中线。
 *   §7.1 明写这只是**视觉顺序，不是按它算宽度**：宽度先扣固定件（➕ 那颗 48dp 见方的热区、
 *   那颗范围符号、三段必要间隔）与输入框自己那份可见下限，剩下的才给 chip 段——
 *   这一份预算只写在 `ReplyDimens.chipsCap` 一处。窄档按原话**先缩短输入框**（chip 段同时让位，
 *   见 [ReplyDimens.chipsCap]），仍是一行、不换行。「仅看本轮」那颗用 [PANEL_ROUND_SCOPE_GLYPH]
 *   单色符号占位（contentDescription 仍是 [PANEL_ROUND_SCOPE_LABEL]，给读屏的全名），常驻行 1、
 *   仍然排在 ＋ 之后，不再随有没有真实消息在行 2 / 消息卡之间二选一挂载。
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
    // [chipsCap] 由行 1 那一级按当前可用宽度选定（三档：全可见 156 / 让到两颗 96 /
    // 极窄让到一颗，见 ReplyDimens.chipsCap），这一族自己不知道整行有多宽，也不该知道——
    // 宽度分配归行 1，§7.1 那句"先计算固定控件与必要间隔，剩余宽度全部给输入框"只在那里落一次。
    val chipsSection: @Composable RowScope.(chipsCap: Dp) -> Unit = { chipsCap ->
        if (showRoleChips) {
            // 放不下就这一段自己横滑（那颗 `horizontalScroll` 本来就在，是它的退路不是补丁）：
            // 让位的是这一段的上限，输入框与 ＋ 的**热区**一寸不动——被挤短的只有输入框的宽，
            // 而它被挤到哪一档由 ReplyDimens.chipsCap 那一处算，不留第二本账。
            val chipsScroll = rememberScrollState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    // 顺序即语义：**先**把这一段的上限定成视口（`widthIn` 在外），**再**在内层横滚——
                    // 反过来（scroll 在外、widthIn 在内）会把 max 压到内容行自己头上：内容行自报
                    // 恰好 48 ⇒ 滚动范围恒 0，"第三颗由横滑接住"是空话（本机探针实测：改序前
                    // scrollMax=0，改序后 153.6−48=105.6）。胶囊宽度轴下限拆掉后（`widthFloor`），
                    // 旧顺序下多出的颗还会被硬压到文字裁切。让位的是视口，不是胶囊。
                    .widthIn(max = chipsCap)
                    .horizontalScroll(chipsScroll)
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

    // ── 输入框（吃剩余宽度；让位那一档按 §7.1 "剩余宽度全部给输入框"先缩短它，但仍是一行） ──
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

    // ── 行 1：她 / 我 / 补充 / [自适应输入框] / ＋ / 范围符号（仅看本轮） ──
    // §7.1 的视觉顺序：她 → 我 → 补充 → 输入框 → ＋ → 仅看本轮符号。
    // 顺序是**视觉**的，宽度不按它算：先扣固定件（➕ 的热区、那颗范围符号、必要间隔）与输入框
    // 那份可见下限，剩下的才轮到 chip 段（ReplyDimens.chipsCap 一处算完），输入框吃其余（weight 1f）。
    // 主动发（showRoleChips = showAddButton = false）时 chipsSection/addSection 各自短路，
    // 行 1 自然只剩那颗吃满宽度的输入框——这就是 §7.1 要的第一行，不是遗留单行分支。
    // 「仅看本轮」常驻行 1（不再随有没有真实消息在行 2 / 消息卡之间二选一挂载），
    // 接了回调才画（roundEntryArmed），否则一颗都不长出来。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md)
    ) {
        // 宽度分配在这一级决定：先量这一行到底多宽，再决定 chip 段让不让位。
        // 现成先例：`ui/panel/reply/MessageList.kt:984` 用同一颗 BoxWithConstraints 量行宽。
        // ⚠ 量的是**这一行的可用宽**，不是屏幕宽：悬浮窗自己只有 260–350dp
        //   （`AppConfig.PANEL_MIN/DEFAULT/MAX_W`），面板再扣掉左右各 `Spacing.xl`，
        //   行可用宽只有 228–318dp——把屏幕那一头的 320/360 当成这里的档位是三种尺寸混为一谈
        //   （§4.3），所以这里只读 maxWidth，档位一律由它推出来。
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 字号放大时两颗正文与那颗符号都要跟着变宽，预算跟着走（§7.1「较大系统字号至少做一次检查」）
            val fontScale = LocalDensity.current.fontScale
            val chipsCap = ReplyDimens.chipsCap(
                maxWidth = maxWidth,
                fontScale = fontScale,
                withAdd = showAddButton,
                withScope = roundEntryArmed
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                chipsSection(chipsCap)
                inputSection()
                addSection()
                if (roundEntryArmed) {
                    Spacer(Modifier.width(Spacing.sm))
                    RoundScopeChip(
                        selected = onlyThisRound,
                        onClick = { onOnlyThisRoundChange?.invoke() }
                    )
                }
            }
        }
    }
}

/**
 * 一颗角色 chip（她/我/补充）——形状归设计系统那颗 [LbChip]（`Single` 一档）。
 *
 * ⚠ **热区与视觉分两层，两轴分开垫**。外层那颗**可点的**高度垫到 48dp，胶囊在里面按
 * 旧版的 28dp 画；宽度轴认领了 [LbChipStyle.widthFloor]=false——"她/我"可见横向只有
 * 30dp 上下，过去外盒 `widthIn(min = 48)` 把每颗白撑出 18dp 纯占位（§2.1 第 1 行点名的
 * "额外触控盒"、§4.3 的"小胶囊包大方盒"），横向命中区回落到可见胶囊本体，纵向仍是 48
 * 满档。角色 `Role.Tab` + 语义里的 `Selected` 由 `Single` 这一档发（与页头那三档模式同一写法）。
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
            widthFloor = false,
            pillHeight = ReplyDimens.ROLE_CHIP_HEIGHT_DP.dp
        )
    )
}

/**
 * 「仅看本轮」那一颗**画在屏上的那颗符号**（§2.2 第 3 条换掉的就是它）。
 *
 * 为什么是 `◉`（U+25C9，几何形状那一族）而不是从前那颗 🔒：
 * · 🔒 被读成"把这个悬浮窗锁住"——那是窗口操作，不是这一轮的上下文范围（§2.2 第 3 条原话）；
 * · emoji 位平面外的字符在 Android 上走**彩色 emoji 字形**，`Text` 的墨色对它无效——
 *   于是 `pill` 那一档为"开/关"准备的两套字色（`TextHint` ↔ `PrimaryDark`）在它身上一分都不显，
 *   只剩底色与那个勾在说状态。换成同一颗墨色画的单色符号，**开与关连字形自己的颜色一起分开**，
 *   这才叫"关/开状态可辨"（§7.1 范围符号那一行）；
 * · 它仍是一颗符号，不是一句话：不往这一排新增常驻文字，行宽预算一寸没多占
 *   （§2.2 第 3 条"不要新增常驻文字撑宽整行"、§7.1"不恢复四字常驻按钮"）。
 *
 * ⚠ **它还不是"既有图标族"的那一颗线条图标**：这一排另一颗符号是 `Icons.Filled.Add`，
 * 要拿同一族的 ImageVector 摆在这里，得让 `LbChip` 交出一个图标/前缀槽（现在只收 `label: String`），
 * 或者由 core 给悬浮窗那颗紧凑胶囊立一档图标 toggle。在这一页自己画一颗 Box + Icon 会撞上两处闸：
 * `OddShapeOwnershipTest` 把 `ReplyInput.kt#RoundScopeChip` 记在**委托壳**那一本（壳里长出 Box 当场红），
 * `UiLayerDependencyContractTest` 的 `BrandLedger` 只给这一页留了一处品牌底。
 * ⇒ 这一条已按缺口报回主线程（要的是 core 那一颗槽，不是这里破两处账）。
 */
internal const val PANEL_ROUND_SCOPE_GLYPH = "◉"

/**
 * 「仅看本轮」那一颗：常驻行 1，位置在 **＋ 之后**（§7.1 的视觉顺序：她 → 我 → 补充 → 输入框 → ＋ → 范围符号），
 * 画在 `LbChipStyles.pill` 那一档上、可见高度 22dp（与 [IntentChip] 同一档、同一个 22dp），
 * 选中带勾（第10节第4条 的可见判据）+ 那一档自己的选中底/描边/墨色一起变。
 *
 * 可见符号是 [PANEL_ROUND_SCOPE_GLYPH]（为什么不是 🔒 见那颗常量的 KDoc），读屏念的仍是全名
 * [PANEL_ROUND_SCOPE_LABEL]（挂在 contentDescription 上，这就是 §2.2 第 3 条与 §7.1 要的
 * "中文语义描述"——名字里没有任何"锁"字）。开与不开真正的行为（下一轮快照生效、
 * 旧结果标过时）住在 `RoundStateStore` 与主线程那一侧，不归这颗管（）。
 *
 * ⚠ **首次轻提示不在这一颗里画**（§2.2 第 3 条要的那一句）：这一屏只有一条通知位，
 * 归 `feature/notice/NoticeBoard`（§4.2 通知那一行"共用一个紧凑位置"），这里再画一条就地横条
 * 就是第二条通知位。现成的同一写法是持续意图那一族——`IntentController.kt:152`
 * `onNotice(if (enabled) "持续意图已开启" else "持续意图已关闭")`，由宿主投进通知位。
 * 这一颗的事件只有一条（[onClick] → 宿主的 `toggleOnlyThisRound()`），**首次那一次**该由
 * 那一条事件的上游（VM/`RoundStateStore` + 一份"提示过没有"的旗标）投，不该由屏上这一格自己记
 * （自己 `remember` 一份 = 第二本账，`AdvisorNoteLineSemanticsTest` 盯着的就是这件事）。
 * ⇒ **已接线**：旗标与投递都落在上游——`LoveBrainViewModel.toggleOnlyThisRound()` 在 OFF→ON
 * 那一跳往 `NoticeBoard.Channel.Knowledge` 投一条紧凑短通知（文案 `ROUND_SCOPE_FIRST_HINT`），
 * "提示过没有"落盘在 `SecurePrefs.roundScopeHintShown`（`di/AppModule` 那对函数接进 VM）。
 * 这一颗仍只消费 [onlyThisRound]，旗标不在这颗记、也不在这颗读。
 *
 * ⚠ **热区与视觉分两层，但外层不买 48**：下面 `pill.copy` 里 `layeredTouch = true` 把带切换语义
 * 的那一层拆出来，`touchFloor = false` 关掉的是 core 那颗**全站 48 见方**的下限盒，
 * 外层自己按紧凑档垫到 [ReplyDimens.ROUND_SCOPE_HIT]（28dp，两轴都垫）——
 * 那是用户那句"缩小按钮占用空间"换来的取舍，也是 §4.3 明写的那一条：
 * 悬浮窗沿用项目已有的紧凑档，**不给每颗紧凑控件再包一个占位 48dp 的容器**（那会把紧凑档作废）。
 * 画出来的胶囊仍是 22dp，外面那层透明盒才可点、才进语义树（与 [RoleChip]、`AddMessageButton`
 * 同一范式，只是各自垫到各自那一档：角色 chip 与 ➕ 买 48，这颗买 28）。
 * ⚠ 与谈心模板芯片（`TouchTier.COMPACT_CHIP` 那一族）同档不同颗：那颗本来就是 28dp 高。
 * 验收读数是三轴分开量的：可见 14×22 / 命中 28×28 / 布局占位 28 宽——
 * 由 `PanelHostSemanticsTest`（整屏逐颗换尺）、`RoundScopeChipFootprintTest`（不许占回 48）
 * 与 `RoleChipSemanticsTest`（§4.3 三轴）三处各钉一件，别拿其中一把去说另一把。
 */
@Composable
private fun RoundScopeChip(selected: Boolean, onClick: () -> Unit) {
    // 可见用 [PANEL_ROUND_SCOPE_GLYPH]（单色几何符号，跟着 pill 那一档的墨色走），读屏仍念全名
    // [PANEL_ROUND_SCOPE_LABEL]。紧凑胶囊档与 [IntentChip] 同走 LbChipStyles.pill 的默认形状
    //（可见 22dp），选中带勾；只有热区这一层按紧凑档自己垫出去。
    // 热区不垫 48dp——用户要求"缩小按钮占用空间"，§4.3 禁的就是给紧凑控件再包一层 48 占位。
    LbChip(
        label = PANEL_ROUND_SCOPE_GLYPH,
        selected = selected,
        onClick = onClick,
        interaction = LbChipInteraction.Multi,
        modifier = Modifier
            // 垫的是**带切换语义的那一层**（LbChip 分层档里外层盒子自己就是那颗可点节点），
            // 只放大外层容器而动作仍挂在子里面等于没改——两轴都垫，闸门两轴都判。
            .widthIn(min = ReplyDimens.ROUND_SCOPE_HIT)
            .heightIn(min = ReplyDimens.ROUND_SCOPE_HIT)
            .testTag(PANEL_ROUND_SCOPE_TEST_TAG)
            .semantics { contentDescription = PANEL_ROUND_SCOPE_LABEL },
        style = LbChipStyles.pill.copy(
            pillHeight = 22.dp,
            markSelectedWithCheck = true,
            touchFloor = false,
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
    // 热区与视觉分两层（与 RoleChip / RoundScopeChip 同一范式）：
    // 外层透明盒垫到 48dp 见方承担点击/热区下限，内层可见胶囊只有 24dp——
    // 视觉紧凑（用户要求的"缩小按钮占用空间"），手指与读屏拿到的都是 48。
    // 旧版把 widthIn(min=48) 撑在外层但内层只有 24dp 可见、且高度只有 36dp，
    // 两轴都没到 48 → PanelHostSemanticsTest 量的就是这一颗。
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
