package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SchemeIdentity
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

/**
 * 方案卡尺寸常量（骨架屏共用；公共对象供同包 ResultArea 引用）。
 *
 * 这里刻意把**两层**分开写，谁也不替谁买单：
 * - 视觉层：卡宽 / 卡高 / 内边距 / 标签间距 / 操作字形，一律照 1.3.1 那份数；
 * - 热区层：右下角三颗动作各自 28dp 见方，整行 [ACTION_ROW_WIDTH_DP] = 84dp，
 *   卡在 158-2x8=142dp 的内容宽里放得下，不需要把卡片放大、也不需要让热区互抢点击。
 * 卡内这一族小动作因此由卡片自己画（[SchemeCollapsedBlock] 里那一个私有紧凑件），
 * 设计系统那颗文字动作的 48dp 档位继续管全站别处，两边都不改。
 */
object SchemeCardDimens {
    /** 卡宽 = 1.3.1 基线 **158dp**（中途为塞下三颗 48dp 动作抬到 164，已撤回） */
    const val CARD_WIDTH_DP = 158
    /**
     * 卡高 = 1.3.1 基线 **150dp**，固定值（旧版那条就是 `height(...)`）。
     * 之前那档 `max=200` 让"默认态"其实能长到 200，等于默认态判据不存在，已撤。
     * 各状态内容层自己有 `weight(1f)` + `verticalScroll`，靠内部滚动不靠卡片膨胀。
     */
    const val CARD_HEIGHT_DP = 150
    /**
     * 卡内右下角一颗动作的点击盒：**28dp 见方**（触控下限里"卡内紧凑"那一档，
     * 与旧版那颗外盒同数）。三颗并排 = [ACTION_ROW_WIDTH_DP]，放得进 142dp 净宽。
     */
    const val ACTION_BOX_DP = 28
    /** 操作行总宽：三颗 x [ACTION_BOX_DP]，卡片内容宽 142dp 装得下 */
    const val ACTION_ROW_WIDTH_DP = 3 * ACTION_BOX_DP
    /** 操作字形视觉尺寸（1.3.1 源码声明那颗数） */
    const val ACTION_GLYPH_SIZE_DP = 13
    const val TAG_HPAD_DP = 6
    const val TAG_VPAD_DP = 3
    const val TAG_TO_BODY_GAP_DP = 6
    /** 卡内调整区四项胶囊的视觉高（热区同数：卡内这一族按紧凑档走） */
    const val ADJUST_PILL_HEIGHT_DP = 28
    /** 自定义那条输入：可编辑盒的下限/上限（一至两行，超出内部滚动，不撑卡片） */
    const val CUSTOM_FIELD_MIN_HEIGHT_DP = 28
    const val CUSTOM_FIELD_MAX_HEIGHT_DP = 36
}

/**
 * 卡片层的文案。
 *
 * ⚠ 这条中文留在 Kotlin 里是**明知故犯**：本工位改不到 `res/values`。走 `const val` 是照
 * `LoveBrainPanelScreen` 里 `PanelStrings` 那条既有写法；**接线时应搬进 `values` + `values-en`
 * 两份资源**（英文环境现在念中文），别在别处抄第二份。
 */
object SchemeCardCopy {
    /** 卡片下方那条轻量文字入口（点开的是本轮共享的参考信息，不是这张卡专属的证据） */
    const val REFERENCE_ENTRY_LABEL = "这轮参考了哪些信息？"
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
 * 调整态：标签 + 2x2 四项入口（自定义展开时才多一行输入与提交）。
 * 改写中：旧正文继续在，底部一句"正在调整" + 停止。
 * 改写完成：就是一张默认卡，只有正文换了。
 * 改写失败/停止：默认卡恢复旧正文，底部一句短反馈——不画对比、不画撤销、不拿错误块替正文。
 *
 * 卡片展开 = 进入调整态，替换内容而非在正文下方追加。
 * 方向 chips 移出卡片——方向属于 Result-level。
 * 普通点击展开调整区（长按录音已随语音模式删除）。
 *
 * [displayTag] 只影响标签上那一个字母（A–H 连续编号是排列要求，不是内部身份）：
 * 内部 `scheme.tag` / `scheme.identity` 一个字没动，正文与回调仍携带原 scheme。
 * [onCardBoundsChanged] 把卡片自己在窗口里的落点报给行容器做"点卡外收起"的判据——
 * 那份 bounds 只服务点击判断，这里不铺任何遮罩、不接跨窗口手势系统。
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
    onToggleRewriteExpand: (SchemeIdentity) -> Unit = {},
    isExpanded: Boolean = false,
    // 自定义改写回调
    onCustomRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    // 卡下方"本轮参考"文字入口：null = 不渲染（渲染与否由调用方决定）。
    // 展开的清单内容由调用方持有，这里只给入口与文案。
    onReferenceClick: (() -> Unit)? = null,
    displayTag: String = scheme.tag,
    // 自定义那一格的草稿与开合态：住在卡片行（item 之上），所以滑出视口再回来还在，
    // 也不会串到别的卡上——卡片自己不留这份状态。
    customDraft: String = "",
    onCustomDraftChange: (String) -> Unit = {},
    isCustomInputOpen: Boolean = false,
    onCustomInputOpenChange: (Boolean) -> Unit = {},
    onCardBoundsChanged: ((Rect?) -> Unit)? = null,
    onInputIntent: (() -> Unit)? = null,
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

    // 完成态与失败态在卡片这一层落回"默认卡"：正文该是什么就是什么，
    // 失败只在正文下面多一句短反馈。两者都不再画对比条/撤销工具条/大块错误。
    val rewriteNotice = (cardState as? SchemeCardPresentationState.RewriteError)?.message

    // 按压缩放——录音长按手势随语音模式一起删除，这里只留 InteractionSource 的 pressed 态
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()

    // 改写中——卡片边框高亮（描边：无反馈 1dp，赞/踩/改写中 2dp）
    val effectiveBorderWidth = if (isRewriting) 2f else borderWidth
    val effectiveBorderColor = when {
        isRewriting -> Primary
        feedback != SchemeFeedback.NONE -> borderColor
        else -> Border
    }

    // 按压缩放
    val scale by animateFloatAsState(
        targetValue = if (isPressed || isRewriting) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "cardScale"
    )

    // 落点上报：latest-value 语义，别把过期回调闭包进去
    val boundsSink = rememberUpdatedState(onCardBoundsChanged)
    DisposableEffect(identityKey) {
        onDispose { boundsSink.value?.invoke(null) }
    }

    // 卡片本体 + 卡下方那条文字入口。入口画在卡**外**：卡自己仍是固定 158x150，撑不回来。
    Column(horizontalAlignment = Alignment.Start) {
        Box(
            modifier = modifier
                .width(SchemeCardDimens.CARD_WIDTH_DP.dp)
                .height(SchemeCardDimens.CARD_HEIGHT_DP.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.lg)
                .clip(LoveBrainShape.lg)
                .background(cardBg)
                .border(effectiveBorderWidth.dp, effectiveBorderColor, LoveBrainShape.lg)
                .onGloballyPositioned { coordinates ->
                    boundsSink.value?.invoke(coordinates.boundsInWindow())
                }
                .then(if (isEmpty) Modifier else Modifier.clickable(
                    interactionSource = pressSource,
                    indication = null
                ) { if (!isRewriting) onToggleRewriteExpand(identity) })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.md)
            ) {
                // 标签行（所有状态都显示）——字母是展示编号，来源身份仍跟着 scheme
                Box(
                    modifier = Modifier
                        .background(tagBg, LoveBrainShape.sm)
                        .padding(horizontal = SchemeCardDimens.TAG_HPAD_DP.dp, vertical = SchemeCardDimens.TAG_VPAD_DP.dp)
                ) {
                    Text(
                        text = "$displayTag · ${scheme.title}",
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
                        // 改写中：旧正文继续在卡里，底部才是那句进度与停止
                        SchemeRewritingBlock(
                            reply = scheme.reply,
                            bodyColor = bodyColor,
                            onCancelRewrite = { onCancelRewrite(identity) },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                    SchemeCardPresentationState.Adjusting -> {
                        SchemeAdjustingBlock(
                            onRewrite = { command -> onRewrite(identity, command) },
                            onCustomRewrite = { text -> onCustomRewrite(identity, text) },
                            onCancel = { onToggleRewriteExpand(identity) },
                            customDraft = customDraft,
                            onCustomDraftChange = onCustomDraftChange,
                            isCustomInputOpen = isCustomInputOpen,
                            onCustomInputOpenChange = onCustomInputOpenChange,
                            onInputIntent = onInputIntent,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                    SchemeCardPresentationState.Collapsed,
                    SchemeCardPresentationState.RewriteDone,
                    is SchemeCardPresentationState.RewriteError -> {
                        SchemeCollapsedBlock(
                            isEmpty = isEmpty,
                            reply = scheme.reply,
                            bodyColor = bodyColor,
                            feedback = feedback,
                            identityKey = identityKey,
                            notice = rewriteNotice,
                            notSuitable = scheme.notSuitable,
                            onCopy = { onCopy(scheme) },
                            onFeedback = { fb -> onFeedback(scheme, fb) },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                }
            }
        }
        if (onReferenceClick != null) {
            // 轻量文字入口：不加粗、无底色、无自画盒——走设计系统那颗文字动作。
            // 不另加 Spacer：见方热区本身已在 13sp 那行上下各留了空档。
            LbTextAction(
                label = SchemeCardCopy.REFERENCE_ENTRY_LABEL,
                tone = LbTextActionTone.Muted,
                onClick = onReferenceClick
            )
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
