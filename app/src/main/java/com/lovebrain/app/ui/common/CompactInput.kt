package com.lovebrain.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary

/**
 * 紧凑圆角单行输入框（48dp 高、圆角灰底）：问卷页与供应商弹窗共用，
 * 取代问卷页 M3 OutlinedTextField——全 App 输入框长相统一。
 * 48 这一档的原因写在 AppDimens.INPUT_ROW_HEIGHT_DP 上（§6.5：可点节点自己≥48×48）。
 */
@Composable
fun CompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    passwordVisible: Boolean = true,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val textStyle = AppTypography.bodyMedium.copy(color = TextPrimary)
    Box(
        modifier = modifier
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
            cursorBrush = SolidColor(com.lovebrain.app.core.designsystem.Primary),
            modifier = Modifier
                .fillMaxWidth()
                // 可编辑节点自己也要垫够：外层 Box 有 48dp，点击/编辑语义却挂在里面这行 15dp 的字上，
                // 等于没改（这是捕获范围页整屏量出来的，见 AppDimens.INPUT_ROW_HEIGHT_DP 的注释）。
                .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                .align(Alignment.CenterStart)
                .padding(end = if (trailingAction != null) 56.dp else 0.dp)
                // 与 PanelTextInput 同一个问题、同一个修法：placeholder 那行 Text 是兄弟节点，
                // 读屏念不到输入框本身（问卷页与供应商弹窗共用这一颗）
                .semantics { contentDescription = placeholder }
        )
        trailingAction?.let {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) { it() }
        }
    }
}
