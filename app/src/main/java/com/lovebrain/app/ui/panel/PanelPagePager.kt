package com.lovebrain.app.ui.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.pointer.pointerInput
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
 * 视图态（下面那根 `camera`），它不是页索引、不改页索引，也从不落盘。
 *
 * ── 画面只由一根"相机"决定（原始第 20 条：点《回复/谈心》会闪一下）──────
 * 每一页的横向落点是 `PanelPageMotion.translationFor(index, camera, pageWidthPx)`，
 * 里面**没有** `currentPage` 这一项（`currentPage` 只管挂载与读屏语义）。所以 owner 单独
 * 翻一次画不出任何差别：点 tab 那一帧旧页仍然在 0、新页仍然在它自己的那一侧，随后只有
 * `camera.animateTo(settledCamera(currentPage))` **一段**过渡。改前那一版把页号也写进落点算式、
 * 补位移却走在下一帧的 `LaunchedEffect` 里 ⇒ 新页先被画在 0（瞬移）、再被 `snapTo` 推回侧边
 * 重放入场 = 用户看到的"闪一下再滑进来"；半路再点一次时旧位移停在中途、补偿是**加法**，
 * 两页会一起被推出屏外（真空白）。现在写的是**绝对**目标，动画中途换页只是改目标。
 * 时长也只有 [PanelPageMotion.SLIDE_MS] 一颗主人（页头那两段读同一颗，原先 220/250 两条并行
 * = "抖一下"的另一半）。这里没有延时、没有遮罩、没有整页淡出（§6.5 禁止）。
 *
 * ── 为什么两页都留在组合里（而不是离屏就卸）──────────────────────────
 * 切页要保留**各自草稿、当前回复结果与谈心历史**（第12节第2条）。谈心那页的历史与追问输入
 * 住在它自己的 `remember` 里（`counseling/CounselingPanel.kt:237/240`），
 * `if (panelMode == 0) … else …` 那种写法一切档就把另一页**卸载**，
 * 于是那两份本地状态被丢掉——用户看到的就是"滑一下草稿没了"。
 * 本轮的做法是**首次显示才挂、挂上就不再卸**（[everShownPages]）：
 * · 第一帧只挂当前页 ⇒ 不会在面板一打开就去替用户读谈心历史（那是主线程盘读，
 *   `LoveBrainViewModel.loadCounselingHistory` 走 SecurePrefs）；
 * · 一旦挂上就永远在组合里 ⇒ 离屏那页的 `remember` 不丢、正在进行的流式结果继续收，
 *   本轮不发起、不取消、也不清任何数据（Service 的生命周期从来不在这一格手里）。
 *
 * ── 手势优先级仲裁（第12节第2条 末段 + 第5节第3条 那五条的顺序）──────────────────
 * 判据集中在 [PageSwipe.resolve] 一颗纯函数里，顺序就是优先级：
 * ① **这一记手势是横向主导、而子层已经消费了位移 ⇒ 让位**（气泡行横滑删除
 *   `reply/MessageList.kt` 的 `draggable`、页内横向卡条 `reply/ReplyInput.kt` 与
 *   `counseling/CounselingTemplateChips.kt` 的 `horizontalScroll`）。本轮只在 `PointerEventPass.Main`
 *   里读事件：Main 是**叶子→根**，所以子层先拿到、先消费，这里读到的 `positionChangeConsumed()`
 *   就是"这块像素已经有人认领了"。一次都不反抢：整记手势此后不再尝试切页。
 *   ⚠ 这一档**按轴筛**：纵向滚动容器（LazyColumn / verticalScroll）消费位置后同一颗读数也是
 *   true，若照字面"消费即让位"，用户"斜着起手再转正"的一记横滑会被判死 ⇒ 原始第 11 条
 *   "要滑好几次"的直接成因。所以先判横向主导，再谈让位。
 * ② 越不过触控 slop、或不是横向主导 ⇒ 不接管也不消费（纵向浏览、点击原样归原来的主人）。
 * ③ 横向主导但目标页不存在 ⇒ 两端没有第三页（页 0 右滑、页 1 左滑都不许动）。
 * ④ 剩下的（页面其余区域）才归切页。
 * 头部拖移窗口在另一层：`PanelHeader` 的 `detectDragGestures` 挂在页头那一行，
 * 本轮的手势只覆盖页槽位，碰不到它（⑤ 头部优先，顺序天然成立）。
 *
 * ⚠ 这一整套是"**子层先、父层补**"的形状，不是父层无条件吞掉所有子手势（第5节第3条 明确禁止后者）。
 * 反过来说也有一种坏实现会让上面四句全红：把本轮换成 `detectHorizontalDragGestures`
 * （它在 slop 处就 `consume()` 整颗 change）——那时气泡横滑删除与页内横向卡条都滑不动了。
 *
 * ── 松手判定（原始第 11 条：一次有意横滑就该换页）─────────────────────
 * [PageSwipe.shouldCommit] 是**或**关系：行程过 `0.25 × 页宽` 那条线，**或**末段甩速过
 * 400dp/s 那道门（基线 v1.1 §6.5）。两条各钉一格纯函数；速度那一轴在仪器侧注不出可信读数，
 * 所以注入手势那几格只按位移轴判（见 `PanelPageSwipePagerTest` 里的口径注释）。
 */
internal const val PANEL_PAGE_COUNT: Int = 2

/** 滑页宿主的测试锚点：手势注入只有落在槽位这一颗粒上才是稳定的（页槽位会随挂载增删） */
internal const val PANEL_PAGE_PAGER_TEST_TAG = "panel_page_pager"

/** `panelMode` → 页索引：**唯一**一处换算；头部与滑页宿主都读它，写不出第二本账 */
internal fun panelModeToPage(panelMode: Int): Int = if (panelMode == 1) 1 else 0

/** 页索引 → `panelMode`：[panelModeToPage] 的反向，同样只此一处（0/1 之外一律落回回复） */
internal fun panelPageToMode(page: Int): Int = if (page == 1) 1 else 0

/** 切页那一段过渡的动效规格：时长只有 [PanelPageMotion.SLIDE_MS] 一颗主人 */
private fun slideSpec() = tween<Float>(PanelPageMotion.SLIDE_MS, easing = FastOutSlowInEasing)

/** 一记横移的归属判定结果（[PageSwipe.resolve] 的返回值，四条优先级各一档） */
internal sealed class PageSwipeOutcome {
    /** ① 这一记**横向**手势已被子层消费 ⇒ 切页让位，而且这一整记手势都不再尝试 */
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
     * 提交一屏切换所需的**行程** = 页宽的这个比（基线 v1.1 §6.5：0.34 → 0.25）。
     * 它比气泡横滑删除那条线（0.4，见 `reply/MessageList.kt` 的 `shouldDeleteBySwipe`）低，
     * 但两者**互不相干**：谁拿到这一记横移由 [resolve] 里"先判横向主导、再读子层消费"那一档决定，
     * 不由数字比大小决定。
     */
    const val COMMIT_FRACTION: Float = 0.25f

    /**
     * 提交一屏切换所需的**甩速**门（dp/s，基线 v1.1 §6.5 新增）。
     * 单位是 dp/s 而不是 px/s：手势那一侧（`runPageSwipeGesture`）用它自己的 `Density` 换算完
     * 再交进来，所以这一颗判据与注入节奏、与屏幕密度都无关，能被纯函数格钉死。
     */
    const val FLING_VELOCITY_DP_PER_S: Float = 400f

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

    /** 行程这一轴：页宽没量到时不许凭空够线（0 行程会被当成"过了 0 这条线"） */
    fun committedByTravel(totalDxPx: Float, pageWidthPx: Float): Boolean =
        pageWidthPx > 0f && abs(totalDxPx) >= commitLineFor(pageWidthPx)

    /** 甩速这一轴：单凭它就够门（`>=`，压线即算）；读数 0（注不出/时间没走动）一律不算 */
    fun committedByFling(velocityDxPerDpPerS: Float): Boolean =
        abs(velocityDxPerDpPerS) >= FLING_VELOCITY_DP_PER_S

    /**
     * 松手要不要换页：**行程或甩速，任一够门就翻页**（基线 v1.1 §6.5）。
     *
     * 为什么必须是"或"：只判行程的话，一记又快又短的甩（用户已经决定要翻页，手指只走了半格）
     * 会被弹回去——那正是原始第 11 条"一点都不丝滑"的读数之一。
     * 为什么不会因此变成"擦一下就误切"：这一支只在手势**已经被认领**之后才被问到
     * （`resolve` 已经把 slop 与横向主导两档挡在前面），没打算横滑的那一记根本走不到这里。
     */
    fun shouldCommit(
        totalDxPx: Float,
        pageWidthPx: Float,
        velocityDxPerDpPerS: Float = 0f
    ): Boolean = committedByTravel(totalDxPx, pageWidthPx) || committedByFling(velocityDxPerDpPerS)

    /**
     * 该往哪一页去：行程过线时**行程说了算**（手指最终在哪一侧就跟谁），
     * 只有甩速单独够门时才用速度那一项的方向。
     */
    fun commitDirection(totalDxPx: Float, pageWidthPx: Float, velocityDxPerDpPerS: Float): Float =
        if (committedByTravel(totalDxPx, pageWidthPx)) totalDxPx else velocityDxPerDpPerS

    /** 提交落点：两轴都不够门 = 原页；够门但目标页不存在 = 原页（两端没有第三页） */
    fun commitTarget(
        currentPage: Int,
        totalDxPx: Float,
        pageWidthPx: Float,
        pageCount: Int,
        velocityDxPerDpPerS: Float = 0f
    ): Int {
        if (!shouldCommit(totalDxPx, pageWidthPx, velocityDxPerDpPerS)) return currentPage
        return targetPage(
            currentPage,
            commitDirection(totalDxPx, pageWidthPx, velocityDxPerDpPerS),
            pageCount
        ) ?: currentPage
    }

    /**
     * 一记横移归谁。参数是**累计**位移（从按下那一刻算起），不是单帧增量：
     * slop 判的是"从起点走了多远"，与注入节奏无关，这样测试与真机判的是同一条线。
     *
     * 顺序就是优先级：先确认"这一记确实是横向主导的一记横滑"，再谈让位——
     * [childConsumedPosition] 这一颗读数**不分轴**（纵向滚动容器消费后同样为 true），
     * 把它排在第一位就等于"这一记手势里出现过一次纵向消费 ⇒ 永久放弃切页"
     * （原始第 11 条"要滑好几次"的直接成因）。横向竞争者（行内删除 / 卡条自滚）
     * 走的仍然是同一档让位，且**粘滞**：一记手势不许同时删掉一条消息又把页切走。
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
        if (slopPx <= 0f) return PageSwipeOutcome.NotAHorizontalDrag
        val horizontal = abs(totalDxPx)
        if (horizontal <= slopPx || horizontal <= abs(totalDyPx)) return PageSwipeOutcome.NotAHorizontalDrag
        if (childConsumedPosition) return PageSwipeOutcome.DeferToChild
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
    // camera：这一帧整组页的"相机位"，单位是**页**而不是像素。0 = 第 0 页落满视口。
    // 它是视图态，不是第二颗页索引：从不参与"现在在哪一页"的判断，也不落盘。
    // 画面上每一页的落点只读这一颗（+ 页宽），所以 owner 翻号本身画不出差别 ⇒ 点 tab 不闪。
    val camera = remember { Animatable(currentPage.toFloat()) }
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    // 起势那一刻的相机位：动画没画完就被抓住时，拖动从**画面所在的位置**接手，而不是从
    // "理论上的那一页"接手（那种写法会先跳半格，正是"一点都不丝滑"里的"跳"）。
    var dragStartCamera by remember { mutableFloatStateOf(currentPage.toFloat()) }
    var dragActive by remember { mutableStateOf(false) }
    /**
     * 这一记滑动已经交回 owner 的目标页（-1 = 没有在飞的滑动提交）。
     * 存在只为了一件事：滑动那一支已经把相机动画排上了（见 [PageMotion.onDragSettle]），
     * 由 owner 变化重启的那格动画就不许再排第二段——排两次就是"滑完又卷回来半格"。
     */
    var pendingSwipeTarget by remember { mutableIntStateOf(-1) }
    // 挂过就永远在组合里（切页保留各自草稿/谈心历史/进行中的流式结果）
    val everShownPages = remember { mutableStateMapOf<Int, Boolean>() }
    val latestOnPageChange = rememberUpdatedState(onPageChange)
    val latestCurrentPage = rememberUpdatedState(currentPage)
    val latestPageCount = rememberUpdatedState(pageCount)

    // 手势侧唯一的落地端。它只碰"跨重组稳定"的那几颗粒（camera/scope/rememberUpdatedState
    // 交回的那颗 State / everShownPages），所以 remember 一次即可，不会把旧的页号钉在闭包里。
    val motion = remember(camera, scope) {
        object : PageMotion {
            override fun onDragClaim(targetPage: Int) {
                dragStartCamera = camera.value
                dragActive = true
                // 新的一记手势一接手，上一记那份"已经排好过渡"的账就必须作废：
                // 下面第一笔 `snapTo` 会把在飞的那段动画取消掉，`pendingSwipeTarget` 若还留着旧目标，
                // `LaunchedEffect` 就会把**这一记**的入场动画当成重复而整段吞掉（画面停在旧页、
                // 头部已经亮新页 = 两本账对不上）。P1d 复核查出来的这一根，判据见
                // `PanelPageMotionTest` 的「交接闸门」那一格。
                pendingSwipeTarget = -1
                // 认领这一记手势的那一刻就把目标页挂上：peek 出来的是真内容而不是空底，
                // 而"挂上"是一次状态写（一记手势只写一次，不是逐帧重组）。
                if (everShownPages[targetPage] != true) everShownPages[targetPage] = true
            }

            override fun onDragOffset(dragOffsetPx: Float) {
                // 跟手：把相机落到手指当前那一格。这颗**不许**是 suspend（接口那边写明理由：
                // `awaitEachGesture { … }` 是带限制的挂起作用域，调不动无关对象的挂起成员），
                // 而 `Animatable` 这一侧没有非挂起的写入口——1.6.8 的 javap 实测：
                // `getValue()` 有、`setValue` 没有（`camera.value = …` 报 Val cannot be reassigned），
                // `snapTo(T, Continuation)` 是挂起的。所以落笔走 `scope.launch { camera.snapTo(…) }`。
                // 这一支**不会**迟到一帧：`rememberCoroutineScope()` 的调度器是 Main.immediate，
                // 主线程上 launch 就地开跑；`snapTo` 里那颗 MutatorMutex 无争用时不挂起。
                // 上一记 animateTo 还在跑时 snapTo 会把它取消——那正是"手指抓住画面"要的语义，
                // 也是本仓库现成写法（`reply/MessageList.kt`、`reply/ReplyInput.kt` 的 draggable 跟手同一条）。
                scope.launch {
                    camera.snapTo(PanelPageMotion.cameraForDrag(dragStartCamera, dragOffsetPx, pageWidthPx))
                }
            }

            override fun onDragBail() {
                dragActive = false
                scope.launch { camera.animateTo(PanelPageMotion.settledCamera(latestCurrentPage.value), slideSpec()) }
            }

            override fun onDragSettle(totalDxPx: Float, velocityDxPerDpPerS: Float) {
                dragActive = false
                val page = latestCurrentPage.value
                val target = PageSwipe.commitTarget(
                    page, totalDxPx, pageWidthPx, latestPageCount.value, velocityDxPerDpPerS
                )
                if (target == page) {
                    // 两轴都不够门 / 在边上：相机自己走回当前页，什么都不投
                    scope.launch { camera.animateTo(PanelPageMotion.settledCamera(page), slideSpec()) }
                    return
                }
                // 先把那**一段**过渡排进队列（从手指松开时的画面接着走），**再**交回 owner——
                // 写只此一次，画完这一格就停。这个先后顺序就是"滑动那一支没有闪烁"的原因。
                pendingSwipeTarget = target
                scope.launch { camera.animateTo(PanelPageMotion.settledCamera(target), slideSpec()) }
                latestOnPageChange.value(target)
                scope.launch {
                    delay(PanelPageMotion.OWNER_HANDOFF_MS)
                    if (latestCurrentPage.value == page && !dragActive) {
                        // owner 没接手（VM 拒绝、被别的流盖回、宿主接线错）：弹回原位，
                        // 绝不留半张页在屏上。⚠ `!dragActive` 那一半是买给"260ms 内又起手"的：
                        // 兜底协程不被任何键取消，让位给在飞的那一记手势，别跟它抢相机。
                        pendingSwipeTarget = -1
                        camera.animateTo(PanelPageMotion.settledCamera(page), slideSpec())
                    }
                }
            }
        }
    }

    LaunchedEffect(currentPage) {
        if (everShownPages[currentPage] != true) everShownPages[currentPage] = true
        if (pendingSwipeTarget == currentPage) {
            // 滑动那一支已经接上手势的行程，这里只把账对齐，不排第二段动画
            pendingSwipeTarget = -1
        } else if (!dragActive && camera.value != currentPage.toFloat()) {
            // 点 tab / 别处改模式：相机此刻仍站着点下去那一刻的画面（旧页还在 0，新页还在
            // 它自己那一侧），所以这里只有**一段**进场过渡。写的是绝对目标，不是加法补偿 ⇒
            // 动画没画完又点一次，也只是改目标，不会把两页一起推出屏外。
            camera.animateTo(PanelPageMotion.settledCamera(currentPage), slideSpec())
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
                motion = motion
            )
    ) {
        for (index in 0 until pageCount) {
            if (index == currentPage || everShownPages[index] == true) {
                key(index) {
                    val announced = index == currentPage || dragActive
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX =
                                    PanelPageMotion.translationFor(index, camera.value, pageWidthPx)
                                // 页宽还没量到那一帧：非当前页压成看不见（当前页照旧），
                                // 画面上永远只有一页——既不是空白，也不是"闪"
                                alpha =
                                    PanelPageMotion.offMeasureAlpha(index, currentPage, pageWidthPx)
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
 * · [PageSwipe.resolve] 先判横向主导、再读 `positionChangeConsumed()`（按轴筛，见那颗的注释）。
 * · 认领之后只 `consumePositionChange()`，不 `consume()`：位移归切页，
 *   up/点击仍按原样走完，底下那颗按钮不会因为"这一记是滑"而多吃一次点。
 *
 * 手势本体在 `PanelPageMotion.kt` 的 [runPageSwipeGesture]（跟手门槛与提交判据分轴、末段甩速
 * 换算成 dp/s 都住在那儿），这里只负责"把手势读数落到相机与 owner 那两面账上"。
 */
private fun Modifier.pageSwitchSwipe(
    currentPage: Int,
    pageCount: Int,
    pageWidthPx: Float,
    motion: PageMotion
): Modifier = pointerInput(currentPage, pageCount, pageWidthPx) {
    runPageSwipeGesture(
        currentPage = currentPage,
        pageCount = pageCount,
        pageWidthPx = pageWidthPx,
        slopPx = viewConfiguration.touchSlop,
        motion = motion
    )
}
