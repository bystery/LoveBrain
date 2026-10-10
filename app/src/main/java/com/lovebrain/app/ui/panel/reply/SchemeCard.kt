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
import com.lovebrain.app.ReplyCardLayout
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
 *
 * ⚠ 2026-10-10 §3 加了"纵向阅读"那一档之后，**尺寸语义只有下面 [widthFor] / [heightFor] /
 * [hasBoundedContentHeight] 三颗分叉口**：一张卡换方向摆不改颜色、边框、圆角、字号、按钮与
 * 按压缩放，只改"这一颗数当固定值还是当下限"。所以别的文件**不许**再抄一份
 * `CARD_WIDTH_DP`/`CARD_HEIGHT_DP` 的替代常量，也不许在调用点直接 `.width(...)`/`.height(...)`。
 */
object SchemeCardDimens {
    /**
     * 卡宽 = 1.3.1 基线 **158dp**（中途为塞下三颗 48dp 动作抬到 164，已撤回）。
     * 语义：**横向档**的固定宽（一行里并排滑着读，靠这颗数决定一屏露得出几张）。
     * 纵向档不读它——那一档宽度铺满整行（见 [widthFor]）。
     */
    const val CARD_WIDTH_DP = 158
    /**
     * 卡高 = 1.3.1 基线 **150dp**，固定值（旧版那条就是 `height(...)`）。
     * 之前那档 `max=200` 让"默认态"其实能长到 200，等于默认态判据不存在，已撤。
     * 各状态内容层自己有 `weight(1f)` + `verticalScroll`，靠内部滚动不靠卡片膨胀。
     *
     * 纵向档（§3）把**同一颗数**当**下限**读：卡片按内容长高、由外层那条纵向滚动接着读，
     * 但一张卡再矮也不许矮过这一档（见 [heightFor] 与 [hasBoundedContentHeight]）。
     * 这是一颗数换一种读法，不是第二份尺寸常量。
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

    // ═══ 两种阅读方向下的尺寸语义（§3：纵向/横向只有这三颗分叉口，别的都不许分叉）═══

    /**
     * 一张卡在两种方向下的**宽度**：横向档固定 [CARD_WIDTH_DP]（并排滑着读），
     * 纵向档铺满整行（一卡一行）。
     *
     * 纵向档没有"另一颗宽度常量"，因为那不是另一种卡、只是同一种卡的另一种摆法。
     */
    fun widthFor(layout: ReplyCardLayout, base: Modifier = Modifier): Modifier =
        if (layout == ReplyCardLayout.VERTICAL) base.fillMaxWidth() else base.width(CARD_WIDTH_DP.dp)

    /**
     * 一张卡在两种方向下的**高度**：同一颗 [CARD_HEIGHT_DP]，横向档当固定高、纵向档当下限。
     *
     * 纵向档为什么允许长高：那一档的溢出由外层那条纵向滚动接（`ResultArea` 唯一的 `readScroll`），
     * 不由卡片自己接——所以纵向档**不许**再往卡内塞一条滚动（见 [hasBoundedContentHeight]）。
     */
    fun heightFor(layout: ReplyCardLayout, base: Modifier = Modifier): Modifier =
        if (layout == ReplyCardLayout.VERTICAL) {
            base.heightIn(min = CARD_HEIGHT_DP.dp)
        } else {
            base.height(CARD_HEIGHT_DP.dp)
        }

    /**
     * 卡内内容是否坐在**有界高度**里——两档唯一的内容侧分叉判据。
     *
     * - 横向档（true）：卡片自己就是那一格的边界，所以正文/调整区靠 `weight(1f)` 撑开、
     *   超出部分**在卡内滚**（旧形状，一字不变）。
     * - 纵向档（false）：卡片按内容长高，此时 `weight(1f)` 与卡内 `verticalScroll` 拿到的
     *   是无限高度（外层 `verticalScroll` 给子节点的就是这一种约束）——那既量不出真尺寸，
     *   又会在外层滚动里再嵌一条滚动。所以这一档正文整条摊开、由外层读。
     *
     * 两档共用同一套卡内容（同一颗 [SchemeCard]、同一批展示块），分叉只有"溢出归谁管"这一条。
     */
    fun hasBoundedContentHeight(layout: ReplyCardLayout): Boolean =
        layout == ReplyCardLayout.HORIZONTAL
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
 *
 * [cardLayout] 是这一张卡**唯一的**方向开关（§3「回复卡片纵向排列」）：它只改尺寸语义
 * （见 [SchemeCardDimens.widthFor] / [SchemeCardDimens.heightFor] /
 * [SchemeCardDimens.hasBoundedContentHeight]），不改颜色、边框、圆角、字号、按钮与按压缩放，
 * 也不改身份、回调、展开态与自定义草稿那套逻辑——两档共用下面同一条分发，没有第二张卡。
 * 默认沿横向档那一组固定尺寸：直接把单卡挂起来量的测试与调用点因此一字不变，
 * 纵向由卡片行（`ResultArea` 的 `SchemeCardsRow`）显式传。
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
    cardLayout: ReplyCardLayout = ReplyCardLayout.HORIZONTAL,
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

    // 卡片本体 + 卡下方那条文字入口。入口画在卡**外**：卡自己那一格撑不回来。
    // 尺寸只从 SchemeCardDimens 那两颗分叉口取（横向档固定 158x150＝旧形状一字不变；
    // 纵向档宽铺满、高按同一颗 150 当**下限**按内容长高）。
    val boundedContentHeight = SchemeCardDimens.hasBoundedContentHeight(cardLayout)
    Column(horizontalAlignment = Alignment.Start) {
        Box(
            modifier = SchemeCardDimens.heightFor(
                cardLayout,
                SchemeCardDimens.widthFor(cardLayout, modifier)
            )
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
                // 横向档：卡片高度是固定的，内容层填满它才谈得上"剩余空间给正文"。
                // 纵向档：卡片按内容长高，`fillMaxSize()` 在这一档拿到的是无限高度（外层
                // verticalScroll 给的约束），填不出真尺寸——所以只铺宽、不撑高。
                modifier = (if (boundedContentHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .padding(Spacing.md)
            ) {
                // 内容层的占位方式：横向档 `weight(1f)` 撑满卡片剩余高度（溢出在卡内滚），
                // 纵向档整条摊开（溢出交外层那条唯一的纵向滚动）。
                // 这一颗是两档唯一的内容侧分叉——下面三档展示块本体两档共用同一份实现。
                val contentBlockModifier: Modifier = Modifier.fillMaxWidth().then(
                    if (boundedContentHeight) Modifier.weight(1f) else Modifier
                )

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
                // 分发的三档**两种阅读方向共用同一份**：横向档 `contentBlockModifier` 带 weight(1f)
                // 让子组件根节点在固定高度的卡里撑开剩余空间（与原内联实现完全一致），
                // 纵向档只带 fillMaxWidth()——卡按内容长高，溢出交外层滚动（判据见 contentHeightBounded）。
                when (cardState) {
                    SchemeCardPresentationState.Rewriting -> {
                        // 改写中：旧正文继续在卡里，底部才是那句进度与停止
                        SchemeRewritingBlock(
                            reply = scheme.reply,
                            bodyColor = bodyColor,
                            onCancelRewrite = { onCancelRewrite(identity) },
                            modifier = contentBlockModifier,
                            contentHeightBounded = boundedContentHeight
                        )
                    }
                    SchemeCardPresentationState.Adjusting -> {
                        SchemeAdjustingBlock(
                            onRewrite = { command -> onRewrite(identity, command) },
                            onCustomRewrite = { text -> onCustomRewrite(identity, text) },
                            customDraft = customDraft,
                            onCustomDraftChange = onCustomDraftChange,
                            isCustomInputOpen = isCustomInputOpen,
                            onCustomInputOpenChange = onCustomInputOpenChange,
                            onInputIntent = onInputIntent,
                            modifier = contentBlockModifier,
                            contentHeightBounded = boundedContentHeight
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
                            modifier = contentBlockModifier,
                            contentHeightBounded = boundedContentHeight
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
