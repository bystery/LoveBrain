package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.Primary
import kotlinx.coroutines.delay

/** 打字机自己的尺寸（跟着它唯一的使用者一起从 ResultDimens 搬过来） */
private object TypewriterDimens {
    const val CURSOR_START_PAD_DP = 1        // 打字机光标左间距
}

/**
 * 流式逐字显示组件：模仿打字机效果，逐字渐显。
 *
 * 它为什么是一个独立的所有者：变化理由是**逐字渲染的节奏**——追赶步长（长文本快进、
 * 短文本逐字）、帧间隔、光标 500ms 一跳。调"打字机看起来多快"只动这一处，
 * 跟这一屏排什么内容、方案卡长什么样无关；加载档（`CoreLoadingIndicator`）在用它，
 * 以后的流式文本也会用它，所以它不属于某一个版面。
 *
 * 调研依据：ChatGPT/Claude 流式输出体验，逐字显示提升阅读节奏感。
 * 实现：用 LaunchedEffect 跟踪上一次显示长度，每次新文本到达时逐步增长显示字符数。
 */
@Composable
internal fun TypewriterText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle,
    maxLines: Int = Int.MAX_VALUE
) {
    var displayedLength by remember { mutableStateOf(0) }

    // 当文本变长时，逐步追赶到最新长度
    LaunchedEffect(text) {
        if (text.length > displayedLength) {
            // 自适应步长：文本越长，每次追赶的字符越多，避免长文本打字太慢
            val remaining = text.length - displayedLength
            val step = when {
                remaining > 200 -> 8    // 长文本：快进
                remaining > 100 -> 5
                remaining > 50 -> 3
                else -> 2              // 短文本：逐字
            }
            val interval = when {
                remaining > 200 -> 8L  // 长文本：更快
                else -> 16L           // 正常 60fps
            }
            while (displayedLength < text.length) {
                val actualStep = minOf(step, text.length - displayedLength)
                displayedLength += actualStep
                delay(interval)
            }
        } else if (text.length < displayedLength) {
            // 文本重置（新的一轮生成）
            displayedLength = 0
        }
    }

    val displayText = if (displayedLength <= text.length) {
        text.take(displayedLength)
    } else {
        text
    }

    // 带闪烁光标的文本
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = displayText,
            color = color,
            style = style,
            maxLines = maxLines
        )
        // 光标闪烁动画：只在还在逐字显示时闪烁，全部显示后光标消失
        if (displayText.length < text.length) {
            var cursorVisible by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(500)
                    cursorVisible = !cursorVisible
                }
            }
            Text(
                text = "▎",
                color = if (cursorVisible) Primary else androidx.compose.ui.graphics.Color.Transparent,
                style = style,
                modifier = Modifier.padding(start = TypewriterDimens.CURSOR_START_PAD_DP.dp)
            )
        }
    }
}
