package com.lovebrain.app.ui.panel.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbMetric
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.SurfaceBase
import com.lovebrain.app.core.designsystem.TextHint
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

/*
 * 面板顶部的使用统计：**永远只占一行**。（本文件的门牌，不是某一颗声明的 KDoc）
 *
 * 决策全在 `UsageStatPlan.kt`（纯函数），这一份只管三件事：
 * 1. **一格 = 完整的「标签＋值」原子项，排成一段 Text**（`maxLines = 1`、`softWrap = false`）：
 *    标签与数值之间不再有能各自换行的缝隙，也就挤不出第二行、缩不了字；
 * 2. **量宽**：每一格按无限宽约束量出自己的自然宽（外层那条 4dp 的锚点与这一行的行宽
 *    都传不进来），分组与换组方案全部由这些实测值决定；
 * 3. **摆**：一组 = 一页，页与页按 12dp 挨个摆开，只把当前页平移进行里，
 *    其余页整页停在行外——静止态里「半个数字」这个形状不存在。
 *
 * 一行放得下就只有一组：不轮播、不渐隐、一个指针输入都不接（顶部原有的拖动不受影响）。
 * 放得下的短组（含首次零值/占位）**相对实际可用视口居中**，不再左贴边（§7.2）：已知宽由
 * [usageStatPageTranslationsPx] 居中、未测量的首帧由 Box `contentAlignment` 保底居中。
 * 放不下才整组换（[USAGE_STAT_GROUP_INTERVAL_MS]）、淡入淡出（[USAGE_STAT_CROSSFADE_MS]）、
 * 允许横向滑到别的组，两侧各留一条 [USAGE_STAT_FADE_WIDTH] 的渐隐表示「那边还有内容」。
 * 单格本身比这一行还宽时那一格行首对齐、可以横向平移着看完——绝不把金额的后几位裁掉当完整值。
 */

/** 渐隐带底色（面板那条 [SurfaceBase]）的浓度：淡的是留白，不是数字 */
private const val USAGE_STAT_FADE_EDGE_ALPHA = 0.92f

/** 渐隐带最多吃掉这一行的这一比例：行本身很窄时宁可不淡，也不把字盖住 */
private const val USAGE_STAT_FADE_MAX_FRACTION = 0.5f

/**
 * 顶部那一行统计。
 *
 * 挂法与旧版那条一致（调用方管，槽位与模式行的高度都不因这一行改变）：
 * `Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).align(Alignment.Center)`
 * ——外层那条 4dp 的透明拖拽锚点高度不变，本组件按自己**一行**的高度居中溢出绘制。
 * 组件内部同样自带 `wrapContentHeight(unbounded = true)`：就算调用方忘了给，
 * 文字也不会被那个 4dp 的框裁掉（4dp 是拖动锚点的热区，不是文字的高度）。
 *
 * 竖向位移原样留给顶部拖拽：只有**横向**越过 slop 时本组件才消费，一个手势只会有一个赢家。
 *
 * @param fields 已经格式化好的格子（标签 + 数值整串），顺序就是摆出来的顺序；
 *   数值怎么念（未知费用、「不足一分」、长金额）由调用方决定，本文件不认识 Double。
 */
@Composable
fun UsageStatBar(
    fields: List<LbMetric>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val gap = USAGE_STAT_GROUP_GAP
    val gapPx = with(density) { gap.roundToPx() }
    val fadePx = with(density) { USAGE_STAT_FADE_WIDTH.roundToPx() }

    /** 这一行的行宽（= 视口）：由外层给，组件不自己挑 */
    var viewportPx by remember { mutableIntStateOf(0) }
    /** 格子**所在位置**的实测自然宽（px）。位置是稳定的键：数值改了只重量这一格，
     *  不换键、不清表——于是后台把费用涨一次、次数加一次都不会把这一行打回平铺那一帧 */
    val slotWidthPx = remember { mutableStateMapOf<Int, Int>() }
    var page by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    /** 单格超宽时这一格自己的横向位移（≤ 0）：先把它看完，剩下的位移才用于翻页 */
    var panPx by remember { mutableFloatStateOf(0f) }

    val slotOf = HashMap<LbMetric, Int>(fields.size)
    fields.forEachIndexed { index, item -> slotOf[item] = index }
    val widthOf: (LbMetric) -> Int? = { item -> slotOf[item]?.let { slotWidthPx[it] } }

    // 还没量到的那一帧：按保底切法 + 空宽度开出不轮播/不渐隐的方案（§1.1 口径不变），
    // 摆位由下面 Box 的 contentAlignment 走「先按居中布局」保底——不再默认左贴边露一帧（§7.2）。
    val measuredEverything = viewportPx > 0 && fields.isNotEmpty() && fields.all { widthOf(it) != null }
    val availableWidth = with(density) { viewportPx.toDp() }

    val groups = if (measuredEverything) {
        groupUsageStatFieldsByWidth(fields, widthOf, viewportPx, gapPx, fadePx)
    } else {
        groupUsageStatFields(fields)
    }
    val layout = planUsageStatLayout(
        groups = groups,
        measuredWidth = { items ->
            if (!measuredEverything) null
            else usageStatGroupWidthPx(items, widthOf, gapPx)?.let { with(density) { it.toDp() } }
        },
        availableWidth = availableWidth,
        gap = gap
    )
    val pages = layout.pages
    val pageCount = layout.pageCount
    val current = usageStatPageAfterStructureChange(page, pageCount)
    val widthsPx = pages.map { usageStatGroupWidthPx(it.fields, widthOf, gapPx) ?: 0 }
    val panRangePx = usageStatItemPanRangePx(widthsPx.getOrElse(current) { 0 }, viewportPx)
    val oversizedPage = panRangePx > 0
    val translations = usageStatPageTranslationsPx(
        widthsPx = widthsPx,
        gapPx = gapPx,
        viewportPx = viewportPx,
        page = current,
        dragPx = dragPx,
        rotates = layout.rotates,
        panPx = if (oversizedPage) panPx else 0f,
        oversizedPage = oversizedPage
    )

    /** 分组结构（有哪几组、每组是哪些标签）：数值后台更新不动它，所以定时器与页号都不重置 */
    val structureKey = pages.joinToString("/") { statPage -> statPage.fields.joinToString(",") { it.label } }
    val gestureEnabled = layout.rotates || oversizedPage

    val latest by rememberUpdatedState(
        SwipeSnapshot(gestureEnabled, pageCount, current, viewportPx, panRangePx)
    )

    // 定时与手势写的是**同一个** page，而且定时器在拖动/平移期间整个被拆掉：
    // key 里有 dragging ⇒ 手指一落这条 LaunchedEffect 就重启并立刻返回，
    // 松手之后重新计满一整段间隔（手动看别的组期间不会有第二处代码去跳组）。
    LaunchedEffect(structureKey, layout.rotates, dragging, pageCount) {
        if (!layout.rotates || dragging || pageCount < 2) return@LaunchedEffect
        while (true) {
            delay(USAGE_STAT_GROUP_INTERVAL_MS)
            page = usageStatPageAfterTick(page, pageCount, dragging)
        }
    }
    // 只有分组结构变了才校正页号（越界就退回还剩的那一组）；只剩一组时上面的定时器自己就不跑了
    LaunchedEffect(structureKey, pageCount) {
        page = usageStatPageAfterStructureChange(page, pageCount)
    }
    // 换组之后上一格的平移归零：新的一组从头看起
    LaunchedEffect(structureKey, current) {
        panPx = 0f
    }
    // 格子少了就把落在后面的旧读数清掉（不牵连页号与定时器）
    LaunchedEffect(fields.size) {
        slotWidthPx.keys.filter { it >= fields.size }.forEach { slotWidthPx.remove(it) }
    }

    val dragState = rememberDraggableState { delta ->
        if (latest.gestureEnabled) {
            dragging = true
            val limit = latest.viewportPx.toFloat()
            if (latest.panRangePx > 0) {
                // 先把这一格横向看完：撞到行尾之后剩下的位移才是「翻页意图」；
                // 往回拉时这一格吃掉了位移，那份「翻页意图」当场作废（不留着松手时误跳一组）
                val before = panPx
                val after = usageStatPanAfterDrag(before, delta, latest.panRangePx)
                panPx = after
                val leftover = delta - (after - before)
                dragPx = if (leftover == 0f) 0f else (dragPx + leftover).coerceIn(-limit, limit)
            } else {
                dragPx = (dragPx + delta).coerceIn(-limit, limit)
            }
        }
    }
    // 只在「真的放不下」时装横向手势：一行放得下时这个组件不接任何指针输入，
    // 顶部原有的拖动行为不受影响。
    val swipe = if (gestureEnabled) {
        Modifier.draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStarted = { dragging = true },
            onDragStopped = { velocity ->
                val target = usageStatPageAfterDrag(latest.page, latest.pageCount, dragPx, velocity, latest.viewportPx)
                dragPx = 0f
                dragging = false
                if (latest.pageCount > 0) page = target
            }
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .testTag(UsageStatTags.BAR)
            .fillMaxWidth()
            // 高度按自己一行量：外层那条 4dp 的锚点既挤不出第二行，也裁不掉这一行
            .wrapContentHeight(unbounded = true)
            .onSizeChanged { viewportPx = it.width }
            .clipToBounds()
            .then(swipe),
        // §7.2 首帧「先按居中布局」保底：未测量那一帧（viewportPx / 自然宽尚未回灌）不默认左贴边。
        // Box 的水平位置由 fillMaxWidth 的**量算约束**决定，早于 onSizeChanged 把 viewportPx 灌进
        // 换组方案，所以这一帧 strip 已按真实视口水平居中；测量完成后回到 TopStart，改由
        // usageStatPageTranslationsPx 用实测宽相对视口居中（两处不会同时生效 ⇒ 不双移）。
        // 轮播/超宽那两档 measuredEverything 恒真，走 TopStart，几何不变。
        contentAlignment = if (!measuredEverything) Alignment.TopCenter else Alignment.TopStart
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                .testTag(UsageStatTags.STRIP),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start)
        ) {
            pages.forEachIndexed { index, statPage ->
                // 页与页都按「这一组排成一行要多宽」来量与摆（外层 unbounded ⇒ 无限宽约束）：
                // 既不接这一行的宽度约束（挤不出第二行、也裁不掉半个数字），
                // 又让每一格量到自己的自然宽——分组用的就是这把尺。
                val isCurrent = index == current
                val pullingIn = dragging && layout.rotates &&
                    ((index == current + 1 && dragPx < 0f) || (index == current - 1 && dragPx > 0f))
                val onScreen = !layout.rotates || isCurrent || pullingIn
                // 淡入淡出只改 alpha；位置在换组那一帧就落定（不飘几何）
                val pageAlpha = usageStatPageAlpha(if (onScreen) 1f else 0f)
                Row(
                    modifier = Modifier
                        .wrapContentWidth(unbounded = true, align = Alignment.Start)
                        .testTag(UsageStatTags.PAGE_PREFIX + index)
                        .graphicsLayer {
                            alpha = pageAlpha
                            translationX = translations.getOrElse(index) { 0f }
                        },
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start)
                ) {
                    statPage.fields.forEach { item ->
                        UsageStatField(
                            item = item,
                            modifier = Modifier.measureNaturalWidth { width ->
                                val slot = slotOf[item] ?: return@measureNaturalWidth
                                if (slotWidthPx[slot] != width) slotWidthPx[slot] = width
                            }
                        )
                    }
                }
            }
        }

        val shownPage = pages.getOrNull(current)
        if (shownPage != null && viewportPx > 0) {
            val (fadeLeft, fadeRight) = usageStatFadeSides(
                fadeLeading = shownPage.fadeLeading,
                fadeTrailing = shownPage.fadeTrailing,
                panPx = if (oversizedPage) panPx else 0f,
                panRangePx = panRangePx
            )
            val fraction = min(
                USAGE_STAT_FADE_MAX_FRACTION,
                USAGE_STAT_FADE_WIDTH.value / max(1f, availableWidth.value)
            )
            if (fadeLeft) {
                Box(
                    Modifier
                        .matchParentSize()
                        .testTag(UsageStatTags.FADE_LEADING)
                        .background(
                            Brush.horizontalGradient(
                                0f to SurfaceBase.copy(alpha = USAGE_STAT_FADE_EDGE_ALPHA),
                                fraction to Color.Transparent
                            )
                        )
                )
            }
            if (fadeRight) {
                Box(
                    Modifier
                        .matchParentSize()
                        .testTag(UsageStatTags.FADE_TRAILING)
                        .background(
                            Brush.horizontalGradient(
                                (1f - fraction) to Color.Transparent,
                                1f to SurfaceBase.copy(alpha = USAGE_STAT_FADE_EDGE_ALPHA)
                            )
                        )
                )
            }
        }
    }
}

/**
 * 让这一格**按自己的自然宽**去量：外层那条 4dp 的锚点、这一行的行宽都不许被当成这一格该有的宽度
 * ——被约束挤瘦过的金额末几位就是用户看到的「半个数字」，分组再拿这个假宽度去算会连着错下去。
 * 量到的自然宽一边回灌给 [onMeasured]（分组与摆位用的是同一把尺），一边就是这一格摆出来的宽度。
 */
private fun Modifier.measureNaturalWidth(onMeasured: (Int) -> Unit): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        )
        onMeasured(placeable.width)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * 一格的原子项：标签在前、数值在后，**一段 Text**、一行、不换行、不缩字。
 * 字号与颜色沿用旧版那条（labelSmall 10sp，标签 [TextHint] / 数值 [Primary]），
 * 但标签与值之间不再有可以各自换行的缝隙——用户说的「第二行」就是从那条缝隙里长出来的。
 */
@Composable
private fun UsageStatField(item: LbMetric, modifier: Modifier = Modifier) {
    val text = remember(item.label, item.value, item.highlight) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = TextHint)) {
                append(item.label)
                append(' ')
            }
            // 数值那一档不设字重：跟着 [AppTypography.labelSmall] 本来的字重走（与旧版那条一致），
            // 只有强调格抬到 SemiBold。
            withStyle(
                if (item.highlight) {
                    SpanStyle(color = Primary, fontWeight = FontWeight.SemiBold)
                } else {
                    SpanStyle(color = Primary)
                }
            ) {
                append(item.value)
            }
        }
    }
    Text(
        text = text,
        modifier = modifier,
        style = AppTypography.labelSmall,
        color = TextHint,
        maxLines = 1,
        softWrap = false
    )
}

/** 换组那一记淡入淡出：只动 alpha，位置在切换那一帧就落定（不动几何，读几何的判据不受影响） */
@Composable
private fun usageStatPageAlpha(target: Float): Float =
    animateFloatAsState(
        targetValue = target,
        animationSpec = tween(USAGE_STAT_CROSSFADE_MS),
        label = "usageStatGroupFade"
    ).value

/** 手势回调要读的只有这几样：打包进 [rememberUpdatedState]，拖动期间不会读到旧值 */
private data class SwipeSnapshot(
    val gestureEnabled: Boolean,
    val pageCount: Int,
    val page: Int,
    val viewportPx: Int,
    val panRangePx: Int
)
