package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 输入框的**五态**名册（基线 v1 §3.7）：静息 / 聚焦 / 已填 / 错误 / 禁用。
 *
 * 这一族以前只有一态（静态 `SurfaceInset + Border` 1dp，聚焦一寸不变，D1 §② C3
 * 的"表单像死物"就是它），所以状态得先有名字，才有判据能钉"改回单态必须红"。
 * 优先级写死在 [lbFieldVisualState] 里：**禁用 > 错误 > 聚焦 > 已填 > 静息**——
 * 禁用不响应所以不配显示聚焦；错误在场时聚焦会抢走红描边，但错误行文字一直在
 * （归属字段，见 [LbFormField]，D1 §② C7），不靠描边单独报错误。
 */
enum class LbFieldVisualState { Resting, Focused, Filled, Error, Disabled }

/**
 * 一个状态该长什么样：底、描边（颜色 + 宽度，null = 无描边）、正文色。
 *
 * ⚠ 这五组值是基线 v1 §3.7 那张表的逐格转录（D1 §③-7），改动要回基线件升版本，
 * 不许在这里就地漂移：聚焦 `Primary` 的 [AppDimens.INPUT_FOCUS_BORDER_WIDTH_DP]（1.5dp）
 * 与静息 `Border` 的 [AppDimens.BORDER_WIDTH_DP]（1dp）之间的差就是"聚焦可见"这件事本身。
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

/** 四颗布尔开关 → 状态。优先级只在这里排一次（见 [LbFieldVisualState] 那段）。 */
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
 * 通用表单的单行输入框：五态可见框的唯一主人（基线 v1 §3.7；D1 §⑥ 改动点 #8）。
 *
 * 前身是 `ui/common/CompactInput`：那一层现在只是把参数转给这颗，行为清单
 * （36 可见 / 48 外层透明热区 / placeholder 转可编辑节点的读屏名 / 空档获焦 /
 * 密码遮蔽 / 尾部槽）逐条原样保留——本轮改的是**外观状态表**（五态），不是行为。
 *
 * 两层分开，各管一条轴（三轴分离，TEAM_RULES §3）：
 *  - **热区层**（外层透明 Box）垫到 [AppDimens.TOUCH_TARGET_MIN_DP]，只转焦点、
 *    **故意不声明点击语义**——给这一排凭空多出一颗"入口"会让"这一行有几个可点项"
 *    的守卫数错（同 `PanelTextInput` 的取舍，写在原 CompactInput 注释里，一字没丢）。
 *  - **视觉层**（里面那颗 36dp 胶囊）按 [AppDimens.INPUT_ROW_HEIGHT_DP] 画，
 *    五态只动它的底 / 描边 / 文字色，不动它的版式高度。**不许把视觉层抬到 48。**
 *
 * 尾部槽：带 [trailingAction] 时可编辑节点右让 [AppDimens.INPUT_TRAILING_SLOT_DP]
 * （那颗 40 字面量的原籍是 `CompactInput.kt` 体里，本轮上提为具名 token，解 D1 §② C4）。
 *
 * 错误：描边转 `Error` 的位由所在 [LbFormField] 经 [LocalLbFieldHasError] 传入——
 * 错误行的文字归字段所有（C7"错误不归属字段"的正解），这颗只管框。
 * 禁用：不响应（外层获焦手势撤下、文本节点 `enabled = false`），但**仍在语义树里**
 * 并多报一个 Disabled——不是从树上消失（与 `LbChip` 禁用那颗同一判据）。
 *
 * 里面没有任何词表：label、placeholder、错误文案一律由调用方交进来。
 *
 * @param focusRequester 外部（键盘引导、进入页面自动聚焦）要用的焦点入口。
 *   传了就挂在这一颗输入框上，空档获焦也复用它，不另起第二个焦点拥有者。
 */
@Composable
fun LbFieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    passwordVisible: Boolean = true,
    enabled: Boolean = true,
    trailingAction: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    val textStyleBase = AppTypography.bodyMedium
    // 只保留一个焦点入口：外部给了就用外部的，否则用这颗自己创建的。
    // 两个 FocusRequester 同时挂在同一节点上会互相覆盖，所以不做"都挂"这种写法。
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .then(
                if (enabled) {
                    // 只转焦点、不声明点击语义；禁用时整条撤下——"不响应"包含点空档也不获焦
                    Modifier.pointerInput(editFocusRequester) {
                        detectTapGestures(onTap = { editFocusRequester.requestFocus() })
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                .clip(LoveBrainShape.md)
                .background(visual.background)
                .then(borderChain)
                .padding(horizontal = Spacing.lg)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextHint,
                    style = textStyleBase,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                textStyle = textStyleBase.copy(color = visual.textColor),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                cursorBrush = SolidColor(Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .onFocusEvent { focused = it.hasFocus }
                    .focusRequester(editFocusRequester)
                    // 尾部件（Key 显隐那颗是轻量文字按钮，不是大图标）：留恰恰到位的槽，
                    // 再多就变成右侧一条空白带——档位只在这里读一次（INPUT_TRAILING_SLOT_DP）。
                    .padding(end = if (trailingAction != null) AppDimens.INPUT_TRAILING_SLOT_DP.dp else 0.dp)
                    // placeholder 那行 Text 是兄弟节点，读屏念不到输入框本身（问卷页与供应商弹窗共用）
                    .semantics { contentDescription = placeholder }
                    .then(
                        if (enabled) {
                            Modifier
                        } else {
                            // 禁用还在树上，只是多报一个 Disabled；不声明禁用=读屏以为它可用
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
