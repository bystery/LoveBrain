package com.lovebrain.app.ui.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 第12节第2条「顶栏两个模式与滑动」的页宿主：回复(0) ↔ 谈心(1) 两页左右滑互切。
 *
 * ── 页索引的唯一真 owner ──────────────────────────────────────────────
 * 这一格里**没有**第二颗"现在在哪一页"。写只有一个口：滑动结束时投一次
 * [onPageChange]（宿主接 `viewModel.setPanelMode`），点 tab 走的也是同一个口；
 * 画哪一页永远由传进来的 [currentPage]（= `composer.panelMode`）决定。
 * `panelMode ↔ 页索引` 的换算也只有 [panelModeToPage] / [panelPageToMode] 这一对，
 * 头部那两段与这里读的是同一对函数——所以"头部显示谈心、身体画回复"这种两本账
 * 对不上的形状在这棵树上写不出来。
 *
 * 刻意**不**用 `HorizontalPager` + `rememberPagerState`：那颗 state 自带一份页索引与
 * 一段"滑动中"的内部偏移，等于凭空多出第二本账（要双向同步就得在两边各写一次、
 * 再拿 `LaunchedEffect` 互相对表），而 第12节第2条 要的是"只有一个实际 owner"。
 * 同一句给的另一条路（"或明确水平手势"）在这里更便宜：跟手位移是本轮的
 * 视图态（[PanelPagePager] 里那颗 `slot`），它不是页索引、不改页索引，也从不落盘。
 *
 * ── 为什么两页都留在组合里（而不是离屏就卸）──────────────────────────
 * 切页要保留**各自草稿、当前回复结果与谈心历史**（第12节第2条）。谈心那页的历史与追问输入
 * 住在它自己的 `remember` 里（`counseling/CounselingPanel.kt:237/240`），
 * `if (panelMode == 0) … else …` 那种写法一切档就把另一页**卸载**，
 * 于是那两份本地状态被丢掉——用户看到的就是"滑一下草稿没了"。
 * 本轮的做法是**首次显示才挂、挂上就不再卸**（[everShownPages]）：
 * · 第一帧只挂当前页 ⇒ 不会在面板一打开就去替用户读谈心历史（那是主线程盘读，
 *   `LoveBrainViewModel.loadCounselingHistory` 走 SecurePrefs，见）；
 * · 一旦挂上就永远在组合里 ⇒ 离屏那页的 `remember` 不丢、正在进行的流式结果继续收，
 *   本轮不发起、不取消、也不清任何数据（Service 的生命周期从来不在这一格手里）。
 *
 * ── 手势优先级仲裁（第12节第2条 末段 + 第5节第3条 那五条的顺序）──────────────────
 * 判据集中在 [PageSwipe.resolve] 一颗纯函数里，顺序就是优先级：
 * ① **子层已消费位移 ⇒ 让位**（气泡行横滑删除 `reply/MessageList.kt:592` 的 `draggable`、
 *   页内横向卡条 `reply/ReplyInput.kt:243` 与 `counseling/CounselingTemplateChips.kt:50` 的
 *   `horizontalScroll`、结果区的 `verticalScroll`）。本轮只在 `PointerEventPass.Main` 里读事件：
 *   Main 是**叶子→根**，所以子层先拿到、先消费，这里读到的 `positionChangeConsumed()`
 *   就是"这块像素已经有人认领了"。一次都不反抢：整记手势此后不再尝试切页。
 * ② 越不过触控 slop、或不是横向主导 ⇒ 不接管也不消费（纵向浏览、点击原样归原来的主人）。
 * ③ 横向主导但目标页不存在 ⇒ 两端没有第三页（页 0 右滑、页 1 左滑都不许动）。
 * ④ 剩下的（页面其余区域）才归切页。
 * 头部拖移窗口在另一层：`PanelHeader` 的 `detectDragGestures` 挂在页头那一行，
 * 本轮的手势只覆盖页槽位，碰不到它（⑤ 头部优先，顺序天然成立）。
 *
 * ⚠ 这一整套是"**子层先、父层补**"的形状，不是父层无条件吞掉所有子手势（第5节第3条 明确禁止后者）。
 * 反过来说也有一种坏实现会让上面四句全红：把本轮换成 `detectHorizontalDragGestures`
 * （它在 slop 处就 `consume()` 整颗 change）——那时气泡横滑删除与页内横向卡条都滑不动了。
 */
internal const val PANEL_PAGE_COUNT: Int = 2

/** 滑页宿主的测试锚点：手势注入只有落在槽位这一颗粒上才是稳定的（页槽位会随挂载增删） */
internal const val PANEL_PAGE_PAGER_TEST_TAG = "panel_page_pager"

/** `panelMode` → 页索引：**唯一**一处换算；头部与滑页宿主都读它，写不出第二本账 */
internal fun panelModeToPage(panelMode: Int): Int = if (panelMode == 1) 1 else 0

/** 页索引 → `panelMode`：[panelModeToPage] 的反向，同样只此一处（0/1 之外一律落回回复） */
internal fun panelPageToMode(page: Int): Int = if (page == 1) 1 else 0

private object PanelPagerDimens {
    /** 过渡时长：与页头那两段高亮平移同一档（`PanelHeader` 的 250ms 手感） */
    const val SLIDE_MS: Int = 220

    /**
     * owner 交接的兜底时限：投完 `onPageChange` 若索引根本没接过去
     * （VM 拒绝、被别的流盖回、宿主接线错），不许把半张页留在屏上——到点自己弹回原位。
     */
    const val OWNER_HANDOFF_MS: Long = 260L
}

/** 一记横移的归属判定结果（[PageSwipe.resolve] 的返回值，四条优先级各一档） */
internal sealed class PageSwipeOutcome {
    /** ① 子层已经消费了这一记位移 ⇒ 切页让位，而且**这一整记手势**都不再尝试 */
    object DeferToChild : PageSwipeOutcome()

    /** ② 还没越过触控 slop，或这一记不是横向主导 ⇒ 不接管、不消费 */
    object NotAHorizontalDrag : PageSwipeOutcome()

    /** ③ 横向主导但目标页不存在 ⇒ 两端没有第三页 */
    object BlockedAtEdge : PageSwipeOutcome()

    /** ④ 归切页：目标页 + 这一帧该跟手画到哪（已夹在一页之内） */
    data class Switching(val targetPage: Int, val offsetPx: Float) : PageSwipeOutcome()
}

internal object PageSwipe {

    /**
     * 提交一屏切换所需的行程 = 页宽的这个比。它比气泡横滑删除那条线（0.4，见
     * `reply/MessageList.kt` 的 `shouldDeleteBySwipe`）低，但两者**互不相干**：
     * 谁拿到这一记横移由"哪一层先消费"决定（[resolve] 的第一判据），不由数字比大小决定。
     */
    const val COMMIT_FRACTION: Float = 0.34f

    /** 提交线在具体页宽上的位置：判据与测试都读这一颗，不在两处各写一份数字 */
    fun commitLineFor(pageWidthPx: Float): Float = COMMIT_FRACTION * pageWidthPx

    /**
     * 横移方向对应的目标页：`dx < 0` = 手指向左 = 下一页（回复→谈心），
     * `dx > 0` = 向右 = 上一页（谈心→回复）。
     * 越界交回 null——**不回绕、也不虚拟出第三页**。
     */
    fun targetPage(currentPage: Int, dxPx: Float, pageCount: Int): Int? {
        if (dxPx == 0f) return null
        val target = if (dxPx < 0f) currentPage + 1 else currentPage - 1
        return if (target in 0 until pageCount) target else null
    }

    /** 跟手位移夹在一页之内；页宽还没量到（第一帧）一律 0，不许画出"整页飞出去" */
    fun clampDragOffset(dxPx: Float, pageWidthPx: Float): Float =
        if (pageWidthPx <= 0f) 0f else dxPx.coerceIn(-pageWidthPx, pageWidthPx)

    /**
     * 松手要不要换页：只看行程过没过那条线，**不看甩速**。
     * 理由与消息行那条删除线同一套：面板只有 360–412dp 宽，一记没走够行程的快擦
     * 就改掉用户当前的工作模式，代价是"我明明还在回复那一面"，而且这一格没有撤销出口。
     * 反例：给 velocity 开后门 ⇒ `PanelPageSwipeTest` 里"短促快擦不许换页"那一格红。
     */
    fun shouldCommit(totalDxPx: Float, pageWidthPx: Float): Boolean {
        if (pageWidthPx <= 0f) return false
        return abs(totalDxPx) >= commitLineFor(pageWidthPx)
    }

    /** 提交落点：不过线 = 原页；过线但目标页不存在 = 原页（两端没有第三页） */
    fun commitTarget(currentPage: Int, totalDxPx: Float, pageWidthPx: Float, pageCount: Int): Int {
        if (!shouldCommit(totalDxPx, pageWidthPx)) return currentPage
        return targetPage(currentPage, totalDxPx, pageCount) ?: currentPage
    }

    /**
     * 一记横移归谁。参数是**累计**位移（从按下那一刻算起），不是单帧增量：
     * slop 判的是"从起点走了多远"，与注入节奏无关，这样测试与真机判的是同一条线。
     *
     * 读 `childConsumedPosition` 的顺序在第一位——这就是"气泡横滑删除优先、
     * 页内横向卡条/分区滚动优先自己的滚动"的实现处，而不是靠比数字大小。
     */
    fun resolve(
        currentPage: Int,
        pageCount: Int,
        pageWidthPx: Float,
        totalDxPx: Float,
        totalDyPx: Float,
        slopPx: Float,
        childConsumedPosition: Boolean
    ): PageSwipeOutcome {
        if (childConsumedPosition) return PageSwipeOutcome.DeferToChild
        val horizontal = abs(totalDxPx)
        if (slopPx <= 0f) return PageSwipeOutcome.NotAHorizontalDrag
        if (horizontal <= slopPx || horizontal <= abs(totalDyPx)) return PageSwipeOutcome.NotAHorizontalDrag
        val target = targetPage(currentPage, totalDxPx, pageCount) ?: return PageSwipeOutcome.BlockedAtEdge
        return PageSwipeOutcome.Switching(target, clampDragOffset(totalDxPx, pageWidthPx))
    }
}

/**
 * 两页左右滑互切的槽位宿主（见文件头那张优先级与所有权合同）。
 *
 * @param currentPage 当前页索引——**只读**，它来自 `composer.panelMode` 这一颗真 owner。
 * @param onPageChange 换页的唯一出口：宿主把它接到 `viewModel.setPanelMode`。
 *        滑动结束、点 tab、别处改模式，全都经过同一个 [currentPage] 回来。
 * @param pageContent 按页索引画内容；本轮只在 `0 until pageCount` 上取页。
 */
@Composable
fun PanelPagePager(
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pageCount: Int = PANEL_PAGE_COUNT,
    pageContent: @Composable (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    // slot：这一帧整组页横向偏了多少 px。**它是视图态，不是第二颗页索引**：
    // 它从不参与"现在在哪一页"的判断，页永远由 currentPage 定位。
    val slot = remember { Animatable(0f) }
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    var previousPage by remember { mutableIntStateOf(currentPage) }
    var peekPage by remember { mutableIntStateOf(-1) }
    var dragActive by remember { mutableStateOf(false) }
    /**
     * 这一记滑动已经交回 owner 的目标页（-1 = 没有在飞的滑动提交）。
     * 存在只为了一件事：滑动那一支已经把行程接上了（见下面 `onDragEnd`），
     * 由 owner 变化重启的那格动画就不许再补一次位移——补两次就是"滑完又卷回来半格"。
     */
    var pendingSwipeTarget by remember { mutableIntStateOf(-1) }
    // 挂过就永远在组合里（切页保留各自草稿/谈心历史/进行中的流式结果）
    val everShownPages = remember { mutableStateMapOf<Int, Boolean>() }
    val latestOnPageChange by rememberUpdatedState(onPageChange)
    val latestCurrentPage by rememberUpdatedState(currentPage)

    LaunchedEffect(currentPage) {
        val from = previousPage
        previousPage = currentPage
        if (everShownPages[currentPage] != true) everShownPages[currentPage] = true
        if (from == currentPage) return@LaunchedEffect
        if (pendingSwipeTarget == currentPage) {
            // 滑动那一支已经接上手势的行程，这里只把账对齐，不重启动画
            pendingSwipeTarget = -1
        } else if (pageWidthPx > 0f) {
            // 外部换页（点 tab）：owner 一翻，"该页本来在的位置"就少了一格，先把 slot 补上
            // 那一格（新页正好从它自己的那一侧进场），再走完成余下的行程。
            slot.snapTo(slot.value + (currentPage - from) * pageWidthPx)
            slot.animateTo(0f, tween(PanelPagerDimens.SLIDE_MS, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size -> pageWidthPx = size.width.toFloat() }
            .testTag(PANEL_PAGE_PAGER_TEST_TAG)
            .pageSwitchSwipe(
                currentPage = currentPage,
                pageCount = pageCount,
                pageWidthPx = pageWidthPx,
                onDragClaim = { target ->
                    // 认领这一记手势的那一刻就把目标页挂上：peek 出来的是真内容而不是空底，
                    // 而"挂上"是一次状态写（一记手势只写一次，不是逐帧重组）。
                    dragActive = true
                    peekPage = target
                    if (everShownPages[target] != true) everShownPages[target] = true
                },
                onDragOffset = { offset -> scope.launch { slot.snapTo(offset) } },
                onDragEnd = { totalDx ->
                    dragActive = false
                    peekPage = -1
                    val target = PageSwipe.commitTarget(currentPage, totalDx, pageWidthPx, pageCount)
                    if (target == currentPage) {
                        // 不够线 / 在边上：弹回原位，什么都不投
                        scope.launch {
                            slot.animateTo(0f, tween(PanelPagerDimens.SLIDE_MS, easing = FastOutSlowInEasing))
                        }
                    } else {
                        // 先把"接上手势的起点了动画"排进同一个队列（排在最后一次跟手位移之后），
                        // 再交回 owner——写只此一次，画完这一格就停。
                        pendingSwipeTarget = target
                        scope.launch {
                            slot.snapTo(totalDx + (target - currentPage) * pageWidthPx)
                            slot.animateTo(0f, tween(PanelPagerDimens.SLIDE_MS, easing = FastOutSlowInEasing))
                        }
                        latestOnPageChange(target)
                        scope.launch {
                            delay(PanelPagerDimens.OWNER_HANDOFF_MS)
                            if (latestCurrentPage == currentPage) {
                                // owner 没接手（VM 拒绝、被别的流盖回、宿主接线错）：
                                // 弹回原位，绝不留半张页在屏上
                                pendingSwipeTarget = -1
                                slot.animateTo(0f, tween(PanelPagerDimens.SLIDE_MS, easing = FastOutSlowInEasing))
                            }
                        }
                    }
                }
            )
    ) {
        for (index in 0 until pageCount) {
            val mounted = index == currentPage || peekPage == index || everShownPages[index] == true
            if (mounted) {
                key(index) {
                    val announced = index == currentPage || dragActive
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = (index - currentPage) * pageWidthPx + slot.value
                            }
                            .then(if (announced) Modifier else Modifier.clearAndSetSemantics { })
                    ) {
                        pageContent(index)
                    }
                }
            }
        }
    }
}

/**
 * 切页手势本身：**只用 Main pass、只吃自己那份位移**。
 *
 * 三条写法决定这四条优先级成不成立，动这一块之前先读完：
 * · `awaitPointerEvent(PointerEventPass.Main)`——Main 是叶子→根，所以每次轮到本轮时，
 *   子层要么已经消费（→ [PageSwipeOutcome.DeferToChild]），要么明确没要这块像素；
 *   换成 `Initial` 或换成 `detectHorizontalDragGestures` 就是父层抢在子层前面吞掉移动事件。
 * · `change.positionChangeConsumed()` 排在判据第一位（见 [PageSwipe.resolve]）。
 * · 认领之后只 `consumePositionChange()`，不 `consume()`：位移归切页，
 *   up/点击仍按原样走完，底下那颗按钮不会因为"这一记是滑"而多吃一次点。
 */
private fun Modifier.pageSwitchSwipe(
    currentPage: Int,
    pageCount: Int,
    pageWidthPx: Float,
    onDragClaim: (Int) -> Unit,
    onDragOffset: (Float) -> Unit,
    onDragEnd: (Float) -> Unit
): Modifier = pointerInput(currentPage, pageCount, pageWidthPx) {
    if (pageWidthPx > 0f && pageCount > 1) {
        val slopPx = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            var totalDx = 0f
            var totalDy = 0f
            var owned = false
            var claimed = false
            var deferredToChild = false
            var lifted = false
            var tracking = true
            while (tracking) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    tracking = false
                } else {
                    if (!deferredToChild) {
                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y
                        when (
                            val outcome = PageSwipe.resolve(
                                currentPage = currentPage,
                                pageCount = pageCount,
                                pageWidthPx = pageWidthPx,
                                totalDxPx = totalDx,
                                totalDyPx = totalDy,
                                slopPx = slopPx,
                                childConsumedPosition = change.positionChangeConsumed()
                            )
                        ) {
                            PageSwipeOutcome.DeferToChild -> {
                                deferredToChild = true
                                if (owned) {
                                    owned = false
                                    onDragOffset(0f)
                                }
                            }
                            is PageSwipeOutcome.Switching -> {
                                if (!owned) {
                                    owned = true
                                    if (!claimed) {
                                        claimed = true
                                        onDragClaim(outcome.targetPage)
                                    }
                                }
                                onDragOffset(outcome.offsetPx)
                                change.consumePositionChange()
                            }
                            else -> Unit
                        }
                    }
                    if (!change.pressed) {
                        lifted = true
                        tracking = false
                    }
                }
            }
            // 没认领过就什么都不做（点击、纵向浏览、被子层认领的横滑都从这一句原样走出去）。
            // 认领过但中途让位给子层的那一记也必须收尾——否则 `dragActive`/`peekPage`
            // 会一直挂在 true 上，屏外那页的读屏语义就再也不被清掉了。
            if (claimed) onDragEnd(if (owned && lifted) totalDx else 0f)
        }
    }
}
