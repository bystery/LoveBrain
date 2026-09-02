package com.lovebrain.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.theme.AppDimens
import com.lovebrain.app.ui.theme.AppTypography
import com.lovebrain.app.ui.theme.Border
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.SurfaceInset
import com.lovebrain.app.ui.theme.TextHint
import com.lovebrain.app.ui.theme.TextPrimary

/**
 * 紧凑圆角单行输入框（36dp 高、圆角灰底）：问卷页与供应商弹窗共用，
 * 取代问卷页 M3 OutlinedTextField——全 App 输入框长相统一。
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
            cursorBrush = SolidColor(com.lovebrain.app.ui.theme.Primary),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(end = if (trailingAction != null) 56.dp else 0.dp)
        )
        trailingAction?.let {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) { it() }
        }
    }
}
