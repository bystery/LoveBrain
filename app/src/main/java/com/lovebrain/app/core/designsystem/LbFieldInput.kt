package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 输入框的五态名册：静息 / 聚焦 / 已填 / 错误 / 禁用。
 *
 * 优先级写死在 [lbFieldVisualState]：**禁用 > 错误 > 聚焦 > 已填 > 静息**——
 * 禁用不响应所以不配显示聚焦；错误在场时描边归错误，错误文案归字段（[LbFormField]），
 * 不靠描边单独报错误。
 */
enum class LbFieldVisualState { Resting, Focused, Filled, Error, Disabled }

/**
 * 一个状态该长什么样：底、描边（颜色 + 宽度，null = 无描边）、正文色。
 *
 * 五组值是基线视觉表的逐格转录，不许在这里就地漂移（要改回基线件升版本）：
 * 聚焦 `Primary` 的 [AppDimens.INPUT_FOCUS_BORDER_WIDTH_DP]（1.5dp）与静息 `Border` 的
 * [AppDimens.BORDER_WIDTH_DP]（1dp）之间的差，就是"聚焦可见"这件事本身。
 */
internal data class LbFieldVisual(
    val background: Color,
    val borderColor: Color?,
    val borderWidth: Dp,
    val textColor: Color
)

/** 状态 → 形状。只有这一处映射；组件体里没有第二条 when。 */
internal fun lbFieldVisual(state: LbFieldVisualState): LbFieldVisual = when (state) {
    LbFieldVisualState.Resting ->
        LbFieldVisual(SurfaceInset, Border, AppDimens.BORDER_WIDTH_DP.dp, TextPrimary)
    LbFieldVisualState.Focused ->
        LbFieldVisual(SurfaceCard, Primary, AppDimens.INPUT_FOCUS_BORDER_WIDTH_DP.dp, TextPrimary)
    LbFieldVisualState.Filled ->
        LbFieldVisual(SurfaceCard, BorderLight, AppDimens.BORDER_WIDTH_DP.dp, TextPrimary)
    LbFieldVisualState.Error ->
        LbFieldVisual(SurfaceCard, Error, AppDimens.BORDER_WIDTH_DP.dp, TextPrimary)
    LbFieldVisualState.Disabled ->
        LbFieldVisual(Neutral800, null, 0.dp, TextHint)
}

/** 四颗布尔开关 → 状态。优先级只在这里排一次（见 [LbFieldVisualState]）。 */
internal fun lbFieldVisualState(
    enabled: Boolean,
    hasError: Boolean,
    focused: Boolean,
    filled: Boolean
): LbFieldVisualState = when {
    !enabled -> LbFieldVisualState.Disabled
    hasError -> LbFieldVisualState.Error
    focused -> LbFieldVisualState.Focused
    filled -> LbFieldVisualState.Filled
    else -> LbFieldVisualState.Resting
}

/**
 * 通用表单输入框：五态可见框的唯一主人。三条轴各管各的，互不串线：
 *
 *  - **内容保真**：可编辑节点恒 `singleLine = false`——IME 会话因此带 MULTI_LINE，
 *    粘贴的换行不被拍平。编辑态（光标/选区/IME 组合）住在内部那颗 [TextFieldValue] 里，
 *    外部 String 真变了才同步，回写往返不打断输入；越界的选区/组合由 TextFieldValue 自己收拢。
 *  - **视觉档**：由 [maxLines] 派生，只分岔一次。`maxLines == 1` = Row：固定胶囊高
 *    [AppDimens.INPUT_ROW_HEIGHT_DP]、一行可视、**框内横向滚动**、动作键 Done（回车不注入换行）；
 *    `maxLines > 1` = Block：同一 token 做最小高、随内容长到 maxLines 行、纵向自滚。
 *    Row 的横向滚动为什么不能靠 `singleLine = true`：那会把 IME 会话变成单行，真机粘贴的换行
 *    被 IME 拍平成空格（内容轴的事，不许拿它换视觉）。可行的做法只有"可编辑节点按内容宽度
 *    铺开、不折行，滚动交给外层"，见组件体里 decorationBox 那一段注释。
 *  - **焦点/意图**：[onInputIntent] 经 [overlayInputIntent] 挂载：只在每次手势**按下那一跳**
 *    上报一次（边沿触发、不消费事件；WHY 见那颗扩展）。[onFocusChange] 把获焦/失焦边沿
 *    交给宿主，宿主不必再拿"哪些输入框不上报"的名单去猜失焦。
 *
 * 热区与视觉分两层：外层透明 Box 垫到 [AppDimens.TOUCH_TARGET_MIN_DP]，只转焦点、
 * **不声明点击语义**（免得给"这一行有几个可点项"的守卫凭空多一颗入口）；五态只动里面
 * 那颗胶囊的底/描边/文字色，不动版式高度。**不许把视觉层抬到 48。**
 *
 * 尾部槽：带 [trailingAction] 时可编辑节点右让 [AppDimens.INPUT_TRAILING_SLOT_DP]。
 * 错误位由 [LbFormField] 经 [LocalLbFieldHasError] 传入；禁用不响应（获焦手势撤下、
 * 文本节点 enabled=false），但仍在语义树里多报一个 Disabled。
 * 组件内没有任何词表：label/placeholder/错误文案全由调用方交进来。
 * 文字工具条走环境的 LocalTextToolbar（悬浮窗宿主挂载），这颗不做逐字段接线，
 * 也不在按下时收菜单——选区菜单的寿命归框架与宿主，不归输入框。
 *
 * @param focusRequester 外部焦点入口（键盘引导、进页自动聚焦）。传了就挂这一颗，
 *   空档获焦也复用它，不另起第二个焦点拥有者。
 * @param onInputIntent 悬浮窗宿主的编辑意图信号：碰到输入区 = 想打字，宿主据此把窗口切到
 *   可聚焦让 IME 弹出。null（默认）= 普通 Activity，IME 原生可用，不需要这条信号。
 * @param textStyle 这一格的字阶档，默认 [AppTypography.bodyMedium]；**同时管**可编辑节点
 *   与 placeholder 两条链——只接一处会画出半张脸（同一行里两种字号打架）。
 * @param onFocusChange 获焦/失焦边沿回调（挂载时的初始焦点事件不算一次失焦）。
 *   null（默认）= 宿主不需要焦点信号，既有调用点一寸不动。
 */
@Composable
fun LbFieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    passwordVisible: Boolean = true,
    enabled: Boolean = true,
    maxLines: Int = 1,
    trailingAction: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onInputIntent: (() -> Unit)? = null,
    textStyle: TextStyle = AppTypography.bodyMedium,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    // 只保留一个焦点入口：外部给了就用外部的。两颗 FocusRequester 挂同一节点会互相覆盖。
    val editFocusRequester = focusRequester ?: remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val hasError = LocalLbFieldHasError.current
    val state = lbFieldVisualState(
        enabled = enabled,
        hasError = hasError,
        focused = focused,
        filled = value.isNotEmpty()
    )
    val visual = lbFieldVisual(state)
    val borderChain = visual.borderColor?.let { color ->
        Modifier.border(visual.borderWidth, color, LoveBrainShape.md)
    } ?: Modifier

    // String 只是投影：外部文本真变了才同步，copy 保留选区与组合（回写往返不重置光标）。
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(text = value)
    }
    // 交出去的 String 去重：文本没变就不打扰宿主（与框架 String 重载同一口径）。
    var lastDelivered by remember(value) { mutableStateOf(value) }

    // 视觉档只在这里分岔一次：Row 固定胶囊高，Block 拿同一 token 当最小高。
    val isRow = maxLines == 1

    // ── Row 档的横向滚动（多行输入没有横向滚动，这里补给它） ─────────────────
    // 病根：可编辑节点拿到的是有界宽 ⇒ 长文本折行，而 maxLines = 1 只画第一行，
    // 于是右半边"折到第二行、却看不见也滚不到"= 用户报的"折行裁切、横向滚动消失"。
    // 退回 singleLine = true 能换回横向滚动，但 IME 会话变成单行、真机粘贴的换行被拍平成空格
    // （内容轴那条红线，不能换）。所以只动视觉轴：把可编辑节点交给一颗横向滚动视口，
    // 它 measure 子节点时给的是**无界宽** ⇒ TextLayout 不折行、按内容宽度铺开，
    // 值里的 \n 仍是唯一断行；未滚动的部分靠外层视口横向拖出来。
    val rowScrollState = rememberScrollState()
    var rowLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val rowDensity = LocalDensity.current
    // 光标跟手：横向滚动收不到"光标动了"（输入框内部的横向跟随只服务 singleLine 档），
    // 按最新布局把光标矩形贴回视口——否则打到行尾之外就看不见正在打的字。
    // 视口宽与可滚范围进键：它们由布局写回来，首帧读到 0 时这一趟只会空转，得再跑一次。
    LaunchedEffect(
        isRow, focused, fieldValue.selection, rowLayoutResult,
        rowScrollState.viewportSize, rowScrollState.maxValue
    ) {
        if (!isRow || !focused) return@LaunchedEffect
        val layout = rowLayoutResult ?: return@LaunchedEffect
        val viewport = rowScrollState.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val offset = fieldValue.selection.end.coerceIn(0, layout.layoutInput.text.length)
        val cursorX = layout.getCursorRect(offset).left
        val margin = with(rowDensity) { Spacing.sm.toPx() }
        val target = when {
            cursorX - margin < rowScrollState.value -> cursorX - margin
            cursorX + margin > rowScrollState.value + viewport -> cursorX + margin - viewport
            else -> return@LaunchedEffect
        }.roundToInt().coerceIn(0, rowScrollState.maxValue)
        rowScrollState.scrollTo(target)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .then(
                if (enabled) {
                    // 只转焦点、不声明点击语义；禁用时整条撤下——"不响应"包含点空档也不获焦。
                    Modifier.pointerInput(editFocusRequester) {
                        detectTapGestures(onTap = { editFocusRequester.requestFocus() })
                    }
                } else {
                    Modifier
                }
            )
            // 编辑意图唯一拥有者：整块热区（含胶囊外空档）按下那一跳报一次；禁用不接。
            .overlayInputIntent(if (enabled) onInputIntent else null)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .then(
                    if (isRow) Modifier.height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                    else Modifier.heightIn(min = AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                )
                .clip(LoveBrainShape.md)
                .background(visual.background)
                .then(borderChain)
                .padding(horizontal = Spacing.lg)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextHint,
                    // 与可编辑节点同一档：换字阶时提示字与打的字一起换。
                    style = textStyle,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { next ->
                    fieldValue = next
                    if (next.text != lastDelivered) {
                        lastDelivered = next.text
                        onValueChange(next.text)
                    }
                },
                // 恒 false：行数与回车行为归 maxLines 与动作键管，内容轴不替视觉轴拍扁换行。
                singleLine = false,
                maxLines = maxLines,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (isRow) ImeAction.Done else ImeAction.Default
                ),
                enabled = enabled,
                // 字阶来自参数、颜色仍由状态表给：换档换的是大小，不是五态文字色。
                textStyle = textStyle.copy(color = visual.textColor),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                cursorBrush = SolidColor(Primary),
                // 光标跟随的唯一读数口：布局一变就把结果交给上面那颗 LaunchedEffect。
                onTextLayout = { result -> if (isRow) rowLayoutResult = result },
                decorationBox = { innerTextField ->
                    if (isRow) {
                        // Row 档：视口 fillMaxWidth，子节点按无界宽 measure ⇒ 不折行，
                        // 横向滚动把超宽那一段拖出来（Block 档原样：折行 + 纵向长高）。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rowScrollState)
                        ) {
                            innerTextField()
                        }
                    } else {
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    // Block 档这颗可编辑节点自带 onClick 语义，是热区探针量到的那一颗：垫到全站
                    // 下限，别让它缩成一行字高（Row 档仍是一行可视，不垫；紧凑调用方的 max 会收拢它）。
                    .then(if (isRow) Modifier else Modifier.heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp))
                    // Row 档只画一行：硬件回车不许往值里注入看不见的换行（IME 动作键仍走 Done；
                    // 粘贴文本里的 \n 不经键盘事件，原样留给自动拆分）。
                    .then(
                        if (isRow) Modifier.onPreviewKeyEvent { e ->
                            e.type == KeyEventType.KeyDown &&
                                (e.key == Key.Enter || e.key == Key.NumPadEnter)
                        } else Modifier
                    )
                    .onFocusEvent { event ->
                        // 边沿交给宿主：获焦/失焦各报一次，挂载时的初始事件不算失焦。
                        val now = event.hasFocus
                        if (now != focused) {
                            focused = now
                            onFocusChange?.invoke(now)
                        }
                    }
                    .focusRequester(editFocusRequester)
                    // 尾部槽恰恰到位：再多就变成右侧一条空白带。档位只在这里读一次。
                    .padding(end = if (trailingAction != null) AppDimens.INPUT_TRAILING_SLOT_DP.dp else 0.dp)
                    // placeholder 那行 Text 是兄弟节点，读屏念不到输入框本身——挂成节点自己的名字。
                    .semantics { contentDescription = placeholder }
                    .then(
                        if (enabled) {
                            Modifier
                        } else {
                            // 禁用还在树上，只是多报一个 Disabled；不声明=读屏以为它可用。
                            Modifier.semantics { this[SemanticsProperties.Disabled] = Unit }
                        }
                    )
            )
            trailingAction?.let {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) { it() }
            }
        }
    }
}