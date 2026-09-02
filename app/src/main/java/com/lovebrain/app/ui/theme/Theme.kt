package com.lovebrain.app.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.dp

// ═══ 间距标尺（section/touch 无调用方已删）═══
object Spacing {
    val xs = 2.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 20.dp
    val xxxl = 24.dp
}

// ═══ 圆角标尺（柔和化：大圆角更显专业温和）═══
object LoveBrainShape {
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(10.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(24.dp)
    val full = RoundedCornerShape(999.dp)
}

// ═══ 阴影/兼容别名（已删：LoveBrainShadow/AppShape/AppElevation 无调用方）═══

// ═══ ColorScheme（暗色已删，固定亮色方案）═══
@Composable
private fun loveBrainColorScheme(): androidx.compose.material3.ColorScheme {
    return lightColorScheme(
        primary = Primary,
        onPrimary = Color.White,
        primaryContainer = PrimaryLight,
        onPrimaryContainer = PrimaryDark,
        secondary = PrimarySubtle,
        onSecondary = TextPrimary,
        surface = SurfaceCard,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        surfaceVariant = SurfaceInset,
        outline = Border,
        outlineVariant = BorderLight,
        error = Error,
        onError = Color.White,
        background = SurfaceBase,
        onBackground = TextPrimary,
        surfaceContainerHighest = Neutral300,
        surfaceContainerHigh = Neutral200,
        surfaceContainer = Neutral200,
        surfaceContainerLow = Neutral100,
        surfaceContainerLowest = Neutral50
    )
}

// ═══ 全局去 ripple（需求#21 终极修复）═══
// 用户反复反馈"圆角组件点击出现矩形灰色阴影"——根因是 Compose 默认 Material ripple
// 未按圆角裁剪（clip 顺序/组件自身 bounds）。在主题层把 LocalIndication 替换为空绘制：
// 所有 clickable/Button 等不再绘制水波纹，按压反馈统一走各组件自定义 scale。
private object NoRippleIndication : Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        return object : IndicationInstance {
            override fun ContentDrawScope.drawIndication() {
                // 什么都不画，只绘制内容本身 → 无矩形水波纹
                drawContent()
            }
        }
    }
}

@Composable
fun LoveBrainTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides NoRippleIndication
    ) {
        MaterialTheme(
            colorScheme = loveBrainColorScheme(),
            typography = AppTypography,
            content = content
        )
    }
}
