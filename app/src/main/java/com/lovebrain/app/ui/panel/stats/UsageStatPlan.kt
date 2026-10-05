package com.lovebrain.app.ui.panel.stats

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.LbMetric
import com.lovebrain.app.core.designsystem.Spacing
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/*
 * 顶部那一行统计的**决策层**：分组、要不要轮播、每页摆在哪、什么时候换组。
 * 全部是纯函数，不碰 Compose 状态——坏实现在这一层最容易撞出来。
 *
 * 三条口径：
 * 1. 一格 = 完整的「标签＋值」**原子项**，由渲染层排成**一段** Text（不换行、不缩字），
 *    组件自己不新增统计维度、不认某一页的词表；
 * 2. 分组看**实测宽度**：按现有字段的先后顺序往这一行里装，装得下就只有一组（不轮播），
 *    装不下才另起一组；一整组也塞不进这一行时，拆的是**组**（退成一格一页），
 *    不是某个数字的后半截；
 * 3. 宽度还没量到的那一帧一律按「放得下」处理（平铺、不轮播、不渐隐），
 *    量到之后才可能翻成轮播——所以首帧与旧版那条平铺一模一样。
 *
 * 费用口径不在这里：[LbMetric.value] 是上游已经格式化好的整串（「—」「不足 ¥0.01」「¥0.123」），
 * 本文件不认识 Double，也就没有第二条「把不知道念成 0」的路径。
 */

/**
 * 自动换组的停顿：看得清的几秒级常量，**不是配置项**——
 * 一行放得下时它根本不参与渲染（见 [planUsageStatLayout] 的 rotates）。
 */
const val USAGE_STAT_GROUP_INTERVAL_MS = 4_000L

/** 换组时那一记淡入淡出的时长：只动 alpha，不动位置（位置在切换那一帧就落定） */
const val USAGE_STAT_CROSSFADE_MS = 150

/** 组内、组间同一档间距：与旧版那条小字一模一样（[Spacing.lg] = 12dp） */
val USAGE_STAT_GROUP_GAP: Dp = Spacing.lg

/** 两侧渐隐带的宽度：只有「确实还有内容在那边」且「这一侧留得下这条带」时才画 */
val USAGE_STAT_FADE_WIDTH: Dp = 8.dp

/** 还没量到宽度的那一帧用的保底切法：按现有字段顺序、每组最多三格 */
private const val USAGE_STAT_MAX_FIELDS_IN_ONE_GROUP = 3

/** 横向滑到其他组的手势阈值：位移超过视口的这一比例（或甩速过线）才算「要翻过去」 */
private const val USAGE_STAT_SWIPE_FRACTION = 0.34f

/** 甩动翻页的最低速度（px/s）；低于它就只认位移，轻碰不跳组 */
private const val USAGE_STAT_FLING_VELOCITY_PX = 900f

/** 极窄那一行也要能说出「翻一下」：位移阈值最低取这个数（px） */
private const val USAGE_STAT_SWIPE_MIN_PX = 24f

/** 「平移到底了没有」的容差（px）：不到一个像素不当成还能再滑 */
private const val USAGE_STAT_PAN_EPSILON_PX = 0.5f

/** 语义锚点。页那一档带序号后缀（`usage_stat_page_0`…）：判「整组换、没裁半个数字」要能说出是哪一组 */
object UsageStatTags {
    const val BAR = "usage_stat_bar"
    const val STRIP = "usage_stat_strip"
    const val PAGE_PREFIX = "usage_stat_page_"
    const val FADE_LEADING = "usage_stat_fade_leading"
    const val FADE_TRAILING = "usage_stat_fade_trailing"
}

// ══════════════════════ 分组 ══════════════════════

/**
 * 保底切法（还没量到宽度时的那一帧）：按**现有字段的先后**切，不看标签内容、不加维度。
 * 三格以内不切，超过三格切成两到三组、每组最多三格。
 */
fun groupUsageStatFields(fields: List<LbMetric>): List<List<LbMetric>> {
    if (fields.isEmpty()) return emptyList()
    if (fields.size <= USAGE_STAT_MAX_FIELDS_IN_ONE_GROUP) return listOf(fields)
    val perGroup = ceil(fields.size / 2.0).toInt().coerceAtMost(USAGE_STAT_MAX_FIELDS_IN_ONE_GROUP)
    return fields.chunked(perGroup)
}

/**
 * 一整组的自然宽 = 各格自然宽之和 + 组内间距；任一格还没量到就返回 null（= 维持平铺）。
 * 这一层只用加法，于是「组宽」与「格宽」永远是同一把尺，不存在两处各算一遍。
 */
fun usageStatGroupWidthPx(
    items: List<LbMetric>,
    widthOfItem: (LbMetric) -> Int?,
    gapPx: Int
): Int? {
    if (items.isEmpty()) return 0
    var total = 0
    items.forEachIndexed { index, item ->
        val width = widthOfItem(item) ?: return null
        total += width + if (index == 0) 0 else gapPx
    }
    return total
}

/**
 * 按**实测宽度**把原子项顺序装进这一行：装得下就只有一组（不轮播），
 * 装不下才另起一组。
 *
 * 每组的容量先扣掉两侧渐隐带（[fadeWidthPx]），于是「换组」与「渐隐有地方落」是同一件事：
 * 淡掉的永远是留白，不是数字本身。
 * 单格本身就比容量还宽时它独占一组——被拆开的是组，不是数字（那一格怎么横向看完由
 * [usageStatItemPanRangePx] 与渲染层负责）。
 */
fun groupUsageStatFieldsByWidth(
    fields: List<LbMetric>,
    widthOfItem: (LbMetric) -> Int?,
    viewportPx: Int,
    gapPx: Int,
    fadeWidthPx: Int
): List<List<LbMetric>> {
    if (fields.isEmpty()) return emptyList()
    val capacity = viewportPx - fadeWidthPx * 2
    val groups = ArrayList<List<LbMetric>>()
    var current = ArrayList<LbMetric>()
    var currentWidth = 0
    fields.forEach { item ->
        val width = widthOfItem(item) ?: return groupUsageStatFields(fields)
        if (current.isEmpty()) {
            current.add(item)
            currentWidth = width
        } else if (currentWidth + gapPx + width <= capacity) {
            current.add(item)
            currentWidth += gapPx + width
        } else {
            groups.add(current.toList())
            current = arrayListOf(item)
            currentWidth = width
        }
    }
    groups.add(current.toList())
    return groups
}

// ══════════════════════ 换组方案 ══════════════════════

/**
 * 一页 = 一次摆出来的**完整一组**。
 * @param width 这一页的自然宽；`null` = 还没量到（首帧按「放得下」处理，不轮播也不渐隐）
 * @param fitsViewport 整页摆进这一行要多宽；`false` = 连一整格都放不下这一行，
 *   组件仍然只画一行、仍然整页换，但**诚实**地把这个事实报出去（不靠缩字糊过去），
 *   由渲染层给这一页开横向平移
 */
data class UsageStatPage(
    val fields: List<LbMetric>,
    val width: Dp?,
    val fitsViewport: Boolean,
    val fadeLeading: Boolean,
    val fadeTrailing: Boolean
)

/** @param rotates 要不要整组轮播：`false` 时所有组同时摆在同一行里，等价旧版那条平铺 */
data class UsageStatLayout(
    val pages: List<UsageStatPage>,
    val rotates: Boolean,
    val gap: Dp
) {
    val pageCount: Int get() = pages.size
}

/**
 * 放得下就不轮播——这条判据只有这一个所有者。
 *
 * 分组已经是两级：一整组放不下这一行时把那一组按字段顺序拆成一格一页，
 * 换的还是「完整信息」。
 */
fun planUsageStatLayout(
    groups: List<List<LbMetric>>,
    measuredWidth: (List<LbMetric>) -> Dp?,
    availableWidth: Dp,
    gap: Dp = USAGE_STAT_GROUP_GAP,
    fadeWidth: Dp = USAGE_STAT_FADE_WIDTH
): UsageStatLayout {
    if (groups.isEmpty()) return UsageStatLayout(pages = emptyList(), rotates = false, gap = gap)

    val groupWidths = groups.map { measuredWidth(it) }
    if (groupWidths.any { it == null }) {
        // 还没量到：平铺、不轮播、不渐隐
        return UsageStatLayout(groups.map { UsageStatPage(it, null, true, false, false) }, false, gap)
    }

    val totalWidth = groupWidths.fold(0f) { acc, width -> acc + width!!.value } + gap.value * (groups.size - 1)
    if (totalWidth <= availableWidth.value) {
        // 一行放得下 ⇒ 全部同时摆出来，不做任何切换
        val pages = groups.map { UsageStatPage(it, measuredWidth(it), true, false, false) }
        return UsageStatLayout(pages, rotates = false, gap = gap)
    }

    val pageFields = groups.flatMap { group ->
        if ((measuredWidth(group)?.value ?: 0f) <= availableWidth.value) listOf(group) else group.map { listOf(it) }
    }
    val last = pageFields.lastIndex
    val pages = pageFields.mapIndexed { index, fields ->
        val width = measuredWidth(fields)
        // 渐隐要落在**留白**里：页宽吃掉整行时不画，否则淡掉的就是数字本身
        val margin = (availableWidth.value - (width?.value ?: availableWidth.value)) / 2f
        val roomForFade = width != null && margin >= fadeWidth.value
        UsageStatPage(
            fields = fields,
            width = width,
            fitsViewport = width == null || width.value <= availableWidth.value,
            fadeLeading = roomForFade && index > 0,
            fadeTrailing = roomForFade && index < last
        )
    }
    return UsageStatLayout(pages, rotates = pages.size > 1, gap = gap)
}

// ══════════════════════ 几何 ══════════════════════

/** 组在自然排布里各自的左缘（不翻译时组件就把它们挨个摆开） */
fun usageStatNaturalStartsPx(widthsPx: List<Int>, gapPx: Int): List<Float> {
    val starts = ArrayList<Float>(widthsPx.size)
    var cursor = 0f
    widthsPx.forEach { width ->
        starts.add(cursor)
        cursor += width + gapPx
    }
    return starts
}

/**
 * 每一页该往哪走（相对自然位置的位移，px）。
 *
 * 静止态（[dragPx] = 0、[panPx] = 0）的**不变式**：当前页整页落在视口里，其余页整页停在视口外。
 * 于是「半个数字」在这个组件的静止态里是不存在的形状，而不是「尽量别出现」。
 * 拖动时只把**要进来的那一页**接到边上，其余仍然停在外面——手指跟着走的是整页。
 *
 * [oversizedPage] = 当前页本身就比这一行还宽：它不再居中（居中会把标签和金额的头几位一起裁掉），
 * 改成行首对齐 + [panPx] 横向平移，让用户把这一格**单行**看完。
 */
fun usageStatPageTranslationsPx(
    widthsPx: List<Int>,
    gapPx: Int,
    viewportPx: Int,
    page: Int,
    dragPx: Float,
    rotates: Boolean,
    panPx: Float = 0f,
    oversizedPage: Boolean = false
): List<Float> {
    if (widthsPx.isEmpty()) return emptyList()
    if (!rotates && !oversizedPage) return List(widthsPx.size) { 0f }
    val starts = usageStatNaturalStartsPx(widthsPx, gapPx)
    val current = page.coerceIn(0, widthsPx.lastIndex)
    val clamped = dragPx.coerceIn(-viewportPx.toFloat(), viewportPx.toFloat())
    val outside = (viewportPx + gapPx).toFloat()
    val lefts = widthsPx.indices.map { index ->
        when {
            index == current -> {
                val rest = if (oversizedPage) 0f else (viewportPx - widthsPx[index]) / 2f
                rest + panPx + clamped
            }
            index == current + 1 && clamped < 0f -> viewportPx + clamped
            index == current - 1 && clamped > 0f -> -widthsPx[index] + clamped
            index > current -> outside + (index - current - 1) * outside
            else -> -(widthsPx[index] + outside + (current - index - 1) * outside)
        }
    }
    return lefts.indices.map { lefts[it] - starts[it] }
}

/** 这一格比这一行还宽时能横向平移的距离（px）；0 = 放得下，不需要平移 */
fun usageStatItemPanRangePx(pageWidthPx: Int, viewportPx: Int): Int =
    if (viewportPx <= 0) 0 else (pageWidthPx - viewportPx).coerceAtLeast(0)

/** 平移落点：先看完这一格（行首 ↔ 行尾），撞到底之后剩下的位移才归翻页 */
fun usageStatPanAfterDrag(panPx: Float, delta: Float, rangePx: Int): Float =
    (panPx + delta).coerceIn(-rangePx.toFloat(), 0f)

/**
 * 哪一侧要画渐隐：整组摆得下时看分组方案给的那两侧（留白里）；
 * 当前页超宽被平移时，看那一侧**还有没有没看完的内容**。
 */
fun usageStatFadeSides(
    fadeLeading: Boolean,
    fadeTrailing: Boolean,
    panPx: Float,
    panRangePx: Int
): Pair<Boolean, Boolean> {
    if (panRangePx <= 0) return fadeLeading to fadeTrailing
    val moreLeft = panPx < -USAGE_STAT_PAN_EPSILON_PX
    val moreRight = -panPx < panRangePx - USAGE_STAT_PAN_EPSILON_PX
    return (fadeLeading || moreLeft) to (fadeTrailing || moreRight)
}

// ══════════════════════ 换组 ══════════════════════

/** 到点就换下一组；**用户正在滑的时候一律不跳**（返回原页号） */
fun usageStatPageAfterTick(page: Int, pageCount: Int, dragging: Boolean): Int =
    if (dragging || pageCount < 2) page else (page + 1) % pageCount

/** 手动滑完之后的落点：位移或甩速过线才换组，轻一下不跳；两端不越过 */
fun usageStatPageAfterDrag(page: Int, pageCount: Int, dragPx: Float, velocityPx: Float, viewportPx: Int): Int {
    if (pageCount < 2) return page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val threshold = max(viewportPx * USAGE_STAT_SWIPE_FRACTION, USAGE_STAT_SWIPE_MIN_PX)
    val forward = dragPx < 0f || (dragPx == 0f && velocityPx < 0f)
    val moved = abs(dragPx) >= threshold || abs(velocityPx) >= USAGE_STAT_FLING_VELOCITY_PX
    val target = when {
        !moved -> page
        forward -> page + 1
        else -> page - 1
    }
    return target.coerceIn(0, pageCount - 1)
}

/**
 * 换组方案变了要不要校正页号：只在**分组结构**变了的时候动，数值后台更新不动
 * （「今日 ¥0.12」变「今日 ¥0.15」不该把用户正在看的那一组推回第一组）。
 */
fun usageStatPageAfterStructureChange(page: Int, pageCount: Int): Int =
    if (pageCount <= 0) 0 else page.coerceIn(0, pageCount - 1)
