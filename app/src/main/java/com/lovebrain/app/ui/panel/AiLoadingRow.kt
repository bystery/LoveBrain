package com.lovebrain.app.ui.panel

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.PrimaryLight
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.TextSecondary
import com.lovebrain.app.ui.theme.AppTypography
import kotlinx.coroutines.delay
/** 加载文案轮换节奏 */
private const val PHRASE_ROTATE_TICK_MS = 1000L
/** 超时计时步进（与 delay 耦合，必须同值） */
private const val ELAPSED_TICK_MS = 1000L


/** 加载行内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object AiLoadingDimens {
    const val DOT_SIZE_DP = 6    // 跳动圆点直径
}

/**
 * AI 加载行（ 新建）。
 *
 * 统一锦囊/谈心/回复区的加载样式，替代各处 CircularProgressIndicator 转圈：
 * 三点跳动（错开 140ms，1.4s 循环）+ 轮换文案（"军师正在 xxx"）+ 可选超时提示。
 * 调研：AI 加载范式——语义化文案替代无意义"加载中"；超时 15s 追加提示降低放弃率。
 */
@Composable
fun AiLoadingRow(
    phrases: List<String>,
    modifier: Modifier = Modifier,
    // 超过该毫秒数后追加"（仍在处理，请稍候）"；0 = 不显示超时提示
    timeoutHintMs: Long = 15000L,  // 默认 15s 超时提示
    background: Color = PrimaryLight
) {
    var phraseIndex by remember { mutableIntStateOf(0) }
    // 加快文案轮换节奏至 1s（原 1.5s），避免"最后一组文案出现即完成"
    LaunchedEffect(Unit) {
        while (true) {
            delay(PHRASE_ROTATE_TICK_MS)
            phraseIndex = (phraseIndex + 1) % phrases.size
        }
    }
    // 超时提示（仅当 timeoutHintMs > 0 时计时）
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(timeoutHintMs) {
        while (timeoutHintMs > 0) {
            delay(ELAPSED_TICK_MS)
            elapsedMs += ELAPSED_TICK_MS
        }
    }
    val showTimeout = timeoutHintMs > 0 && elapsedMs >= timeoutHintMs

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(background)
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
    ) {
        // 三点跳动（错开 140ms）
        val transition = rememberInfiniteTransition(label = "aiDots")
        for (i in 0..2) {
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(280, delayMillis = i * 140),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(AiLoadingDimens.DOT_SIZE_DP.dp)
                    .graphicsLayer { translationY = offsetY }
                    .clip(LoveBrainShape.full)
                    .background(Primary)
            )
            if (i < 2) Spacer(Modifier.width(Spacing.sm))
        }
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = phrases[phraseIndex] + if (showTimeout) "（仍在处理，请稍候）" else "",
            color = TextSecondary,
            style = AppTypography.bodySmall
        )
    }
}
