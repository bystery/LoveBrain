package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovebrain.app.R
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SchemeIdentity
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 方案卡尺寸常量（骨架屏共用，值不变；公共对象供同包 ResultArea 引用）。
 */
object SchemeCardDimens {
    /**
     * 卡宽（骨架屏与实体卡共用）。
     *
     * 158 → **164** 不是改版式：卡片右下角是三颗图标动作，每颗的可点击盒要 ≥48dp
     * （§6.5 :531），而卡内左右各 `Spacing.md`=8dp 内边距 ⇒ 里面只剩 142dp，
     * 三颗 48 需要 144dp。原来 158 那一档放不下，实测最后一颗被压成 **46x48dp**。
     * 加 6dp 是为了留 4dp 余量，不是随手凑整。
     * 由 `SchemeCardDimens values are stable` 那格按"放得下三颗下限"判，不再钉这个数。
     */
    const val CARD_WIDTH_DP = 164
    const val CARD_HEIGHT_DP = 150    // 卡高（骨架屏 166->150 对齐实体，消除跳变）
    const val CARD_MAX_HEIGHT_DP = 200 // 最大高度上限，防止展开时无限增长
    const val TAG_HPAD_DP = 6         // 标签水平内边距
    const val TAG_VPAD_DP = 3         // 标签垂直内边距
    const val TAG_TO_BODY_GAP_DP = 6  // 标签到正文间距
}

/** 方案卡正文排版常量（internal：同包抽离的展示子组件共用） */
internal object SchemeTextDimens {
    val BODY_FONT_SIZE = 13.sp       // 话术正文字号
    val BODY_LINE_HEIGHT = 18.sp     // 话术正文行高
}

/**
 * SchemeCard 展示状态——纯展示态推导，不负责 transition。
 *
 * 状态决定卡片内容层显示什么：
 * - Collapsed: 标签 + 正文 + 操作行（默认态）
 * - Adjusting: 标签 + 改写选项 + 取消（调整态，替换内容不追加）
 * - Rewriting: 改写 API 调用中
 * - RewriteError/RewriteDone: 改写结果
 */
sealed class SchemeCardPresentationState {
    data object Collapsed : SchemeCardPresentationState()
    data object Adjusting : SchemeCardPresentationState()
    data object Rewriting : SchemeCardPresentationState()
    data class RewriteError(val message: String) : SchemeCardPresentationState()
    data object RewriteDone : SchemeCardPresentationState()
}

/**
 * 回复方案卡（单面卡）：v1.3.1 视觉重量回归。
 *
 * 默认态：标签 + 正文 + 右下操作（复制/赞/踩）。
 * 调整态：标签 + 改写选项 + 取消（替换内容，不追加）。
 *
 * 卡片展开 = 进入调整态，替换内容而非在正文下方追加
 * 方向 chips 移出卡片——方向属于 Result-level
 * 普通点击展开调整区（长按录音已随语音模式删除）
 */
@Composable
fun SchemeCard(
    scheme: Scheme,
    feedback: SchemeFeedback,
    onFeedback: (Scheme, SchemeFeedback) -> Unit,
    onCopy: (Scheme) -> Unit,
    rewriteState: RewriteState? = null,
    onRewrite: (SchemeIdentity, RewriteCommand) -> Unit = { _, _ -> },
    onClearRewriteState: (SchemeIdentity) -> Unit = {},
    onCancelRewrite: (SchemeIdentity) -> Unit = {},
    onUndoRewrite: (SchemeIdentity) -> Unit = {},
    onToggleRewriteExpand: (SchemeIdentity) -> Unit = {},
    isExpanded: Boolean = false,
    // 自定义改写回调
    onCustomRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val isEmpty = scheme.reply.isBlank()
    // 使用 identity 作为所有操作的稳定身份
    val identity = scheme.identity
    val identityKey = identity.key

    val borderColor by animateColorAsStateCompat(
        targetValue = when (feedback) {
            SchemeFeedback.LIKED -> Primary
            SchemeFeedback.DISLIKED -> Error
            SchemeFeedback.NONE -> Border
        },
        label = "cardBorder"
    )
    val borderWidth by animateFloatAsState(
        targetValue = if (feedback != SchemeFeedback.NONE) 2f else 1f,
        animationSpec = tween(200),
        label = "cardBorderWidth"
    )

    val isRecommended = scheme.title == "推荐" && !isEmpty
    val tagColor = if (isRecommended) Color.White else PrimaryDark
    val tagBg = if (isRecommended) Primary else PrimaryLight
    val cardBg = if (isEmpty) SurfaceInset else SurfaceCard
    val bodyColor = if (isEmpty) TextHint else TextPrimary

    // 改写状态
    val isRewriting = rewriteState is RewriteState.Loading
    val rewriteError = (rewriteState as? RewriteState.Error)?.message
    val rewriteDone = rewriteState is RewriteState.Done


    // 统一状态推导——使用抽离的纯函数
    val cardState: SchemeCardPresentationState = deriveCardPresentationState(
        isRewriting = isRewriting,
        rewriteError = rewriteError,
        rewriteDone = rewriteDone,
        isExpanded = isExpanded
    )

    // 按压缩放——录音长按手势随语音模式一起删除，这里只留 InteractionSource 的 pressed 态
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()

    // 改写中——卡片边框高亮
    val effectiveBorderWidth = if (isRewriting) 2f else borderWidth
    val effectiveBorderColor = when {
        isRewriting -> Primary
        feedback != SchemeFeedback.NONE -> borderColor
        else -> Border
    }

    // 按压缩放
    val scale by animateFloatAsState(
        targetValue = if (isPressed || isRewriting) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "cardScale"
    )

    // 外框高度保持稳定
    Box(
        modifier = modifier
            .width(SchemeCardDimens.CARD_WIDTH_DP.dp)
            .heightIn(min = SchemeCardDimens.CARD_HEIGHT_DP.dp, max = SchemeCardDimens.CARD_MAX_HEIGHT_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.lg)
            .clip(LoveBrainShape.lg)
            .background(cardBg)
            .border(effectiveBorderWidth.dp, effectiveBorderColor, LoveBrainShape.lg)
            .then(if (isEmpty) Modifier else Modifier.clickable(
                interactionSource = pressSource,
                indication = null
            ) { if (!isRewriting && rewriteError == null) onToggleRewriteExpand(identity) })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
                .animateContentSize()
        ) {
            // 标签行（所有状态都显示）
            Box(
                modifier = Modifier
                    .background(tagBg, LoveBrainShape.sm)
                    .padding(horizontal = SchemeCardDimens.TAG_HPAD_DP.dp, vertical = SchemeCardDimens.TAG_VPAD_DP.dp)
            ) {
                Text(
                    text = "${scheme.tag} · ${scheme.title}",
                    color = tagColor,
                    style = AppTypography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(SchemeCardDimens.TAG_TO_BODY_GAP_DP.dp))

            // 根据 cardState 渲染卡片内容——替换而非追加
            // 各状态内容层已抽离为同包纯展示子组件（Scheme*Block.kt），此处只做分发。
            // modifier 中的 weight(1f) 让子组件根节点在 Column 中撑开剩余空间，
            // 与原内联实现的布局权重完全一致。
            when (cardState) {
                SchemeCardPresentationState.Rewriting -> {
                    SchemeRewritingBlock(
                        onCancelRewrite = { onCancelRewrite(identity) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                is SchemeCardPresentationState.RewriteError -> {
                    SchemeRewriteErrorBlock(
                        message = cardState.message,
                        onClearRewriteState = { onClearRewriteState(identity) },
                        onToggleRewriteExpand = { onToggleRewriteExpand(identity) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                SchemeCardPresentationState.RewriteDone -> {
                    SchemeRewriteDoneBlock(
                        reply = scheme.reply,
                        bodyColor = bodyColor,
                        onUndoRewrite = { onUndoRewrite(identity) },
                        onClearRewriteState = { onClearRewriteState(identity) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                SchemeCardPresentationState.Adjusting -> {
                    SchemeAdjustingBlock(
                        onRewrite = { command -> onRewrite(identity, command) },
                        onCustomRewrite = { text -> onCustomRewrite(identity, text) },
                        onCancel = { onToggleRewriteExpand(identity) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                SchemeCardPresentationState.Collapsed -> {
                    SchemeCollapsedBlock(
                        isEmpty = isEmpty,
                        reply = scheme.reply,
                        bodyColor = bodyColor,
                        feedback = feedback,
                        onCopy = { onCopy(scheme) },
                        onFeedback = { fb -> onFeedback(scheme, fb) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}


/**
 * 兼容封装：颜色动画状态。
 */
@Composable
private fun animateColorAsStateCompat(
    targetValue: Color,
    label: String
): State<Color> {
    return animateColorAsState(
        targetValue = targetValue,
        animationSpec = tween(300),
        label = label
    )
}
