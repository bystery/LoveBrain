package com.lovebrain.app.ui.common

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary

/**
 * 通用表单的单行紧凑输入框：36dp 可见外框、圆角灰底、12dp 水平内边距。
 *
 * 两层分开，各管一条轴（与回复面板那颗 `PanelTextInput` 同一个写法，不是第二套设计）：
 *  - **热区层**（外层透明 Box）垫到 [AppDimens.TOUCH_TARGET_MIN_DP]，负责"点得到"，
 *    并且点框内任何空档都把焦点转给可编辑节点（只让那行字吃点击的话，整框高度是假的）。
 *  - **视觉层**（里面那颗胶囊）按 [AppDimens.INPUT_ROW_HEIGHT_DP] 画，负责"看着对"。
 *    placeholder 与输入文字共用同一条垂直中线，所以两者都在框内居中。
 *
 * 两条轴不许拧成一条：**不许把视觉层也抬到 48**。热区本来就不靠抬高可见控件来凑，
 * 抬可见层会顺带把供应商弹窗、知识库编辑页、问卷页的输入框全部撑高。
 *
 * @param focusRequester 外部（键盘引导、进入页面自动聚焦）要用的焦点入口。
 *   传了就挂在这一颗输入框上，空档获焦也复用它，不另起第二个焦点拥有者。
 */
@Composable
fun CompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    passwordVisible: Boolean = true,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    val textStyle = AppTypography.bodyMedium.copy(color = TextPrimary)
    // 只保留一个焦点入口：外部给了就用外部的，否则用这颗自己创建的。
    // 两个 FocusRequester 同时挂在同一节点上会互相覆盖，所以不做"都挂"这种写法。
    val editFocusRequester = focusRequester ?: remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .pointerInput(editFocusRequester) {
                // 只转焦点、不声明点击语义：给这排凭空多出一颗"入口"会让
                // "这一行有几个可点项"的守卫数出错（与 PanelTextInput 同一个取舍）。
                detectTapGestures(onTap = { editFocusRequester.requestFocus() })
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                .clip(LoveBrainShape.md)
                .background(SurfaceInset)
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
                .padding(horizontal = 12.dp)
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
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                cursorBrush = SolidColor(Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .focusRequester(editFocusRequester)
                    // 显隐 Key 那颗是轻量文字按钮，不是大图标：留 40dp 就够它落进 36dp 的框，
                    // 再多就变成右侧一条空白带。
                    .padding(end = if (trailingAction != null) 40.dp else 0.dp)
                    // placeholder 那行 Text 是兄弟节点，读屏念不到输入框本身（问卷页与供应商弹窗共用）
                    .semantics { contentDescription = placeholder }
            )
            trailingAction?.let {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) { it() }
            }
        }
    }
}
