package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.model.SchemeFeedback
import kotlinx.coroutines.delay

/**
 * 复制成功的对钩显示时长（约 1 秒）。
 *
 * ⚠ **这一条不是复刻**：1.3.1 卡片上没有任何对钩，旧版复制只弹一句 Toast
 * （旧版 `FloatingService` 里 `Toast.makeText(this, "已复制", ...)`）。
 * 这是按用户选择**新增**的外观反馈，判据只有"够看清、又不用用户点掉"，
 * 所以配套那条测的是区间不是这个数。复制**不等于已发送**：这里不碰记录发送那一族。
 */
object SchemeCopyFeedback {
    const val TICK_MS = 1000L
}

/**
 * 方案卡默认态展示块——从 SchemeCard 抽离的纯展示子组件。
 *
 * 默认态（Collapsed）：正文 + 操作行（复制/赞/踩）。
 * - 空回复：显示"本轮不适合"，不带操作行——**不伪造一句正文把卡片填满**
 * - 非空：正文（可滚） + 右下角三颗紧凑动作
 * - [notice]：改写失败/停止后那一句短反馈（旧正文照旧在，这里不替换它、不画错误大块）
 *
 * 操作行两层同数：字形 13dp、点击盒 28dp 见方，三颗共 84dp，落在卡片
 * 158-2x8=142dp 的内容宽里（旧版右下角那一簇就是这个尺寸）。
 * 卡内这一族小动作因此自己画一件紧凑件（本文件私有、只在这里用三次），
 * 设计系统那颗文字动作的 48dp 档继续管全站别处——两边都不动。
 *
 * 复制对钩按 [identityKey] + 正文记：只有对应那张卡变勾，正文换版后旧勾自己清。
 */
@Composable
internal fun SchemeCollapsedBlock(
    isEmpty: Boolean,
    reply: String,
    bodyColor: Color,
    feedback: SchemeFeedback,
    onCopy: () -> Unit,
    onFeedback: (SchemeFeedback) -> Unit,
    identityKey: String = "",
    notice: String? = null,
    /** true = 模型主动说"不适合"（显示"本轮不适合"）；false = 模型没生成（显示"未生成"） */
    notSuitable: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 点过复制：图标立即换成变色对钩，TICK_MS 后自己收回。原 onCopy 动作一个字没改。
    // 键到"哪张卡 + 现在的正文"上：换卡不带着上一张的勾，正文更新后旧勾也一并作废。
    var copyAck by remember(identityKey, reply) { mutableStateOf(false) }
    LaunchedEffect(copyAck) {
        if (copyAck) {
            delay(SchemeCopyFeedback.TICK_MS)
            copyAck = false
        }
    }

    if (isEmpty) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (notSuitable) "本轮不适合" else "未生成",
                color = TextHint,
                style = AppTypography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(modifier = modifier) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = reply,
                    color = bodyColor,
                    style = AppTypography.bodyMedium,
                    fontSize = SchemeTextDimens.BODY_FONT_SIZE,
                    lineHeight = SchemeTextDimens.BODY_LINE_HEIGHT
                )
            }
            // 失败/停止：一句短反馈，旧正文一个字没动
            if (!notice.isNullOrBlank()) {
                SchemeRewriteNotice(notice)
            }
            Spacer(Modifier.height(Spacing.sm))
            // 操作行：固定右下角。三颗 28dp 见方 = 84dp，卡片内容宽 142dp 装得下，
            // 所以这里既不用 requiredWidth 顶出内容沿、也不用把卡放大。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardCompactAction(
                    iconRes = if (copyAck) null else R.drawable.ic_copy,
                    iconVector = if (copyAck) Icons.Filled.Check else null,
                    description = stringResource(R.string.panel_copy),
                    tint = if (copyAck) Primary else TextSecondary,
                    onClick = {
                        onCopy()
                        copyAck = true
                    }
                )
                CardCompactAction(
                    iconRes = R.drawable.ic_thumb_up,
                    iconVector = null,
                    description = stringResource(R.string.a11y_scheme_like),
                    tint = if (feedback == SchemeFeedback.LIKED) Primary else TextHint,
                    selected = feedback == SchemeFeedback.LIKED,
                    onClick = { onFeedback(SchemeFeedback.LIKED) }
                )
                CardCompactAction(
                    iconRes = R.drawable.ic_thumb_down,
                    iconVector = null,
                    description = stringResource(R.string.a11y_scheme_dislike),
                    tint = if (feedback == SchemeFeedback.DISLIKED) Error else TextHint,
                    selected = feedback == SchemeFeedback.DISLIKED,
                    onClick = { onFeedback(SchemeFeedback.DISLIKED) }
                )
            }
        }
    }
}

/**
 * 卡内右下角那一颗紧凑动作：28dp 见方的点击盒 + 13dp 字形。
 *
 * 三件事跟着卡片自己那族走，不借用全站文字动作那颗 48dp 档：
 * 名字与 `selected` 挂在**带 clickable 的同一节点**上（挂内层会被语义合并念两遍）、
 * 角色声明为 [Role.Button]、按压缩放仍用全站那一处 [rememberPressScale]。
 */
@Composable
private fun CardCompactAction(
    iconRes: Int?,
    iconVector: ImageVector?,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    selected: Boolean? = null
) {
    val (interaction, scale) = rememberPressScale(0.92f, "cardCompactActionScale")
    Box(
        modifier = Modifier
            .size(SchemeCardDimens.ACTION_BOX_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.sm)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description }
            .then(if (selected == null) Modifier else Modifier.semantics { this.selected = selected }),
        contentAlignment = Alignment.Center
    ) {
        // 两支行数一样、只是取字形的方式不同（资源图 vs 矢量图标），字形尺寸同一颗数。
        val glyphModifier = Modifier.size(SchemeCardDimens.ACTION_GLYPH_SIZE_DP.dp)
        if (iconVector != null) {
            // 名字只在外面那颗热区上声明一次，这里再写一遍就是念两遍
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = tint,
                modifier = glyphModifier
            )
        } else if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = glyphModifier
            )
        }
    }
}
