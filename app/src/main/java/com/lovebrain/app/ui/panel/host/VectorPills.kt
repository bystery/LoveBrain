package com.lovebrain.app.ui.panel.host

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

/** 向量药丸行（亲密 / 信任 / 承诺 / 激情 / 安全）的内部尺寸常量。 */
private object VectorPillDimens {
    const val PILL_HEIGHT_DP = 14
    const val PILL_LABEL_GAP_DP = 3
}

/** Vector pills row */
@Composable
internal fun VectorPillsRow(vector: Map<String, Int>, delta: Map<String, Int>) {
    // light theme
    // colors from Color.kt
    val dims = listOf(
        Triple("intimacy", "亲密", VectorIntimacy),
        Triple("trust", "信任", VectorTrust),
        Triple("commitment", "承诺", VectorCommitment),
        Triple("passion", "激情", VectorPassion),
        Triple("security", "安全", VectorSecurity)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dims.forEach { (key, label, color) ->
            VectorPill(
                label = label,
                value = (vector[key] ?: 50).coerceIn(0, 100),
                delta = delta[key] ?: 0,
                color = color,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Single vector pill */
@Composable
private fun VectorPill(
    label: String,
    value: Int,
    delta: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = nonlinearFraction(value),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "vectorPill_$label"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(label).append(' ').append(value).append("分")
                    if (delta > 0) append("，上升 ").append(delta).append(" 分")
                    else if (delta < 0) append("，下降 ").append(-delta).append(" 分")
                }
            }
    ) {
        // label left
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextHint,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
            )
        )
        Spacer(Modifier.width(VectorPillDimens.PILL_LABEL_GAP_DP.dp))
        // pill body
        Box(
            modifier = Modifier
                .weight(1f)
                .height(VectorPillDimens.PILL_HEIGHT_DP.dp)
                .clip(LoveBrainShape.full)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(animatedFraction)
                    .height(VectorPillDimens.PILL_HEIGHT_DP.dp)
                    .background(color, LoveBrainShape.full)
            )
            // value inside
            Text(
                text = "$value",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (animatedFraction > 0.4f) Color.White else color,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
        if (delta != 0) {
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = if (delta > 0) "+$delta" else "$delta",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (delta > 0) Success else Error,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

/** nonlinear mapping */
private fun nonlinearFraction(value: Int): Float {
    return when {
        value <= 40 -> (value / 40f) * 0.20f
        value <= 80 -> 0.20f + ((value - 40) / 40f) * 0.70f
        else -> 0.90f + ((value - 80) / 20f) * 0.10f
    }
}
