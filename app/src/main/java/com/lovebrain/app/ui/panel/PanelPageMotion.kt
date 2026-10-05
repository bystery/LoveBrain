package com.lovebrain.app.ui.panel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeConsumed
import androidx.compose.ui.input.pointer.PointerInputScope

/**
 * 切页动效与"一次有意横滑就切换"判据的**私有辅助件**（基线 v1.1 §6.5 / 需求映射 原始 11、20）。
 *
 * 这一格里只有三类东西：被两个文件同时读的**动效时长**、切页那一段位移的**位置模型**、
 * 以及横滑那记手势本身。三者都住在生产路径上：时长被 `PanelPagePager` 与 `PanelHeader`
 * 直接读，位置模型被 `PanelPagePager` 的 `graphicsLayer` 读，手势被那颗 `pointerInput` 读。
 *
 * ① **动效时长只有一颗主人**：[SLIDE_MS]。页面平移（`PanelPagePager`）与页头高亮的平移
 *   （`PanelHeader` 的 `ModeSegmentTwo`）读同一颗数 ⇒ 点一次 tab 只有**一条**过渡。
 *   改前是 220ms（页，原 `PanelPagePager.kt` 的 `PanelPagerDimens.SLIDE_MS`）与 250ms
 *   （头部，原 `PanelHeader.kt` 里那颗裸 `tween(250, …)`）两条不同源的动画并行
 *   ⇒ 用户读到的"抖一下"。按压那 120ms 属另一族（`core/designsystem/PressScale.kt`），
 *   不在这一颗名下，本轮一个字不动。
 *
 * ② **位置模型是一根"相机"**：`PanelPageMotion` 里那组相机算式的单位是**页**而不是像素——
 *   `translationFor(index) =
 *   (index - camera) * pageWidthPx`。相机停在整数那一页时该页正好落满视口（稳态）；
 *   跟手时它被手指连续地拖着走（[cameraForDrag]，负方向 = 向左 = 下一页）。
 *
 * ── 为什么这一条能结掉原始第 20 条（点《回复/谈心》会闪一下）──────────────────
 * 改前：画面上每一页的落点是 `(index - currentPage) * pageWidthPx + slot.value`——
 * 页号与位移**两个入参**，而页号那一路是 composition/draw 阶段、补位移那一路是 effect/协程阶段。
 * 点 tab 那一帧 owner（`composer.panelMode`）已经写完、`currentPage` 是新页，而 `slot` 还是 0
 * ⇒ 新页被画在 0（瞬移）、旧页被推到 ±页宽；随后 `LaunchedEffect` 才 `snapTo(±W)` 把它推回侧边
 * 再 `animateTo(0)` 重放一次入场 ⇒ "闪一下再滑进来"。半路再点一次时 `slot.value` 停在中途
 * 且没人清零，而补偿是**加法**（`slot.value + (currentPage - from) * W`）⇒ 起点变成 1.4W 之类，
 * 第一帧两页都在屏外 ⇒ 真空白。
 * 改后：落点只由**一根相机**决定，页号那一路**画不出任何差别**——点 tab 只是让 `currentPage`
 * 换了值，相机仍站着点下去那一刻的画面（旧页还在 0、新页还在它自己的那一侧）。过渡只有
 * `animateTo(settledCamera(currentPage))` 这一段，而且它写的是**绝对**目标而不是加法补偿，
 * 所以"动画没画完又切一次"也不会把两页一起推出屏外。
 * 滑动侧的次序照原样保留：先让相机跟着手指走（跟手）、抬指时先排那**一条**动画、**才**投 owner
 * （[PageMotion.onDragSettle] 的实现处），owner 回来后 `LaunchedEffect` 认 `pendingSwipeTarget`
 * 那道去重闸门，不再补第二次位移。
 *
 * 这里**没有**延时、没有临时遮罩、没有整页淡出（§6.5 明令禁止用这三样掩盖闪烁）；
 * [OWNER_HANDOFF_MS] 只在"owner 根本没接手"那条错误分支上兜底，不是"等一等再画"。
 *
 * ── 页宽还没量到（冷启动首帧）────────────────────────────────────────────
 * 平移算式每一项都乘页宽，页宽为 0 时所有页会叠在同一格。[offMeasureAlpha] 把"还没量到"
 * 那一帧里非当前页的**绘制可见度**（alpha）压成 0：当前页照旧 1 ⇒ 那一帧画面上永远只有一页，
 * 既不是空白，也不是"闪"。（这是绘制属性，不是遮罩，也不是延时。）
 */
internal object PanelPageMotion {

    /** 切页过渡与页头高亮平移的**唯一**时长（原 220/250 ⇒ 两档合一，基线 v1.1 §6.5） */
    const val SLIDE_MS: Int = 200

    /**
     * owner 交接兜底时限：投完 `onPageChange` 若页号根本没接过去（VM 拒绝、被别的流盖回、
     * 宿主接线错），不许把半张页留在屏上——到点自己弹回原位。
     * ⚠ 这一颗**不是**"等一等再画"的延时：它只在"owner 没接手"这条错误分支上生效，
     *   数值仍须 >= [SLIDE_MS]，否则过渡还没画完就被它打断。
     */
    const val OWNER_HANDOFF_MS: Long = 260L

    /** 稳态：相机停在第 [page] 页（该页正好落满视口，其余页在 ±页宽外） */
    fun settledCamera(page: Int): Float = page.toFloat()

    /**
     * 跟手：相机从 [startCamera] 被手指拖了 [dragOffsetPx]（px，负 = 向左 = 下一页方向）之后停在哪。
     * 页宽还没量到（<=0，冷启动首帧）时一分都不挪——不许凭空把两页拉开。
     */
    fun cameraForDrag(startCamera: Float, dragOffsetPx: Float, pageWidthPx: Float): Float =
        if (pageWidthPx <= 0f) startCamera else startCamera - dragOffsetPx / pageWidthPx

    /** 第 [index] 页在这一帧的横向落点（px）：画面**只**由相机读数与页宽决定，与页号无关 */
    fun translationFor(index: Int, camera: Float, pageWidthPx: Float): Float =
        (index - camera) * pageWidthPx

    /** 没量到页宽那一帧的绘制可见度：非当前页 0（看不见但仍在组合里），当前页 1 */
    fun offMeasureAlpha(index: Int, currentPage: Int, pageWidthPx: Float): Float =
        if (pageWidthPx > 0f || index == currentPage) 1f else 0f
}

/**
 * 一记横滑末段的**水平甩速**估计器（px/s，带符号：负 = 手指向左 = 下一页方向）。
 *
 * 只用**按下到抬指**的真实时间差，取"最后一个采样往前 [windowMillis] 毫秒"的平均速度
 * （Android `VelocityTracker` 的同一族口径）。两条理由：
 * · 单帧差在 120/240Hz 触控采样下会被一次抖动放大成假"甩"；
 * · 只看时间窗而不是帧数 ⇒ 注入节奏（测试）与真机采样率不同也判的是同一条线。
 * 越窗的样本不参与计算；时间倒退是坏读数，整段清空重开，不拿它算速度。
 * 样本不足或时间没走动一律回 0——**不凭空够速度门**（仪器注不出速度时，判据退回只看位移）。
 */
internal class HorizontalVelocityEstimator(private val windowMillis: Long = 80L) {

    private val times = LongArray(CAPACITY)
    private val xs = FloatArray(CAPACITY)
    private var size = 0

    fun addSample(uptimeMillis: Long, x: Float) {
        if (size > 0 && uptimeMillis < times[size - 1]) clear()
        if (size == CAPACITY) {
            // 已满：丢最老的一格（整段左移一格，`copyInto` 允许重叠拷贝）
            times.copyInto(times, 0, 1, size)
            xs.copyInto(xs, 0, 1, size)
            size--
        }
        times[size] = uptimeMillis
        xs[size] = x
        size++
    }

    /** 窗内最老样本到最新样本的平均速度；样本不足或时间没走动一律 0（不许凭空够速度门） */
    fun velocityPxPerS(): Float {
        if (size < 2) return 0f
        val lastTime = times[size - 1]
        var i = 0
        while (i < size - 1 && lastTime - times[i] > windowMillis) i++
        val dt = lastTime - times[i]
        if (dt <= 0L) return 0f
        return (xs[size - 1] - xs[i]) * 1000f / dt.toFloat()
    }

    fun clear() {
        size = 0
    }

    /** 当前记住了几个样本（只给判据买"环形缓冲确实在收样本"这一件事，不参与算速度） */
    fun sampleCount(): Int = size

    private companion object {
        /** 80ms 窗在 240Hz 触控下约 19 个样本；16 格足够覆盖，且每帧成本是常数 */
        const val CAPACITY = 16
    }
}

/**
 * 切页手势与切页动作之间唯一的接口：`pointerInput` 那一侧只报告"发生了什么"，
 * **不**决定画到哪、**不**写 owner（页号那一面账只有 `ComposerStore.panelMode` 一颗）。
 *
 * 实现处是 `PanelPagePager` 里那颗粒（相机那一路状态的主人）。
 */
internal interface PageMotion {
    /** 这一记横移被判定为切页道：把目标页挂进组合（视图集合写，不是页号写） */
    fun onDragClaim(targetPage: Int)

    /**
     * 跟手：相机被手指拖到 [dragOffsetPx]（相对起势那一刻，px）。
     *
     * ⚠ 这颗**不许**是 `suspend`（主线程 2026-10-06 收的一根编译错）：调用它的那一段在
     * `awaitEachGesture { … }` 里面，而那颗是带限制的挂起函数——限制之下只能调
     * "成员/扩展挂起函数"，调一颗接口方法的挂起实现会直接报
     * `Restricted suspending functions can only invoke member or extension suspending functions`。
     * ⚠ 实现侧的落笔方式**只有一条**可走（P1d 用 javap 量过 animation-core 1.6.8）：
     * `Animatable` 只有 `getValue()`、**没有 `setValue`**（`camera.value = …` 报
     * `Val cannot be reassigned`），而 `snapTo(T, Continuation)` 是挂起的——所以实现必须
     * 在本颗（非挂起）里 `scope.launch { camera.snapTo(…) }`。这一支不迟到：
     * `rememberCoroutineScope()` 是 `Main.immediate`，主线程上就地开跑，`MutatorMutex` 无争用时不挂起；
     * 与 `reply/MessageList.kt`、`reply/ReplyInput.kt` 的 draggable 跟手是同一条写法。
     */
    fun onDragOffset(dragOffsetPx: Float)

    /** 横向竞争者（行内删除 / 模板横滚）接管这一记手势：相机自己走回当前页，不投任何写 */
    fun onDragBail()

    /**
     * 抬指：够线就走**那一条**提交序列（先排那一段过渡 → 再投 owner → 一段动画），不够线就弹回。
     *
     * @param totalDxPx 按下到抬指的累计位移（px）
     * @param velocityDxPerDpPerS 末段水平甩速，**dp/s**（手势侧已经用完自己的 density 换算，
     *        这样 [PageSwipe] 的判据与 400dp/s 那道门是同一个单位，测试与真机判同一条线）
     */
    fun onDragSettle(totalDxPx: Float, velocityDxPerDpPerS: Float)
}

/**
 * 切页手势本体：只用 **Main pass**（叶子→根，子层先拿到事件），每帧只吃**自己那份位移**
 * （`consumePositionChange()`，不 `consume()` ⇒ 抬起与点击仍按原样走完）。
 *
 * 它替换掉原来的 `Modifier.pageSwitchSwipe`（那颗只剩"把读数落到 motion 上"这一层，手势循环
 * 只留这一份）。改动理由记在 `evidence/2026-10-05-feedback/impl-P1c-pager-motion.md`：
 * 那颗把**任何**一次位置消费（包括纵向滚动容器那一次）当成整记手势的死刑，
 * 于是"斜着起手再转正"的一记横滑被第一帧的纵滚消费判死 ⇒ 用户"要滑好几次"（原始第 11 条）。
 * 本轮改的三件事：
 * · **让位只认横向竞争者**（见 [PageSwipe.resolve] 的按轴筛）：纵向那一记消费不再粘滞放弃整记手势，
 *   横滑行内删除 / 卡条自滚那一档一字不改（仍然粘滞：一记手势不许同时删消息又切页）；
 * · **跟手一帧只交一份读数**：`onDragOffset` 是非挂起的（见那颗自己的说明——`awaitEachGesture`
 *   那一带是带限制的挂起作用域），实现侧在 `Main.immediate` 上就地 `snapTo`，既不逐帧排队、
 *   也不许挂起（挂起就得撞回那条限制）；
 * · **松手交一份完整读数**：抬指时把累计行程与末段甩速一起交给 [PageMotion.onDragSettle]，
 *   翻页判定归 [PageSwipe]（位移线 **或** 速度门）。
 *
 * 位移那两档（slop 与横向主导）吃的都是**累计**位移（从按下起算），与注入节奏无关 ⇒
 * 测试与真机判同一条线；单帧那份只用来累加（`positionChange()` 交回的是**没被子层吃掉**的那一段）。
 *
 * @param currentPage 只读页号（= `composer.panelMode` 那一面）；这里不改它，只在抬指时投一次写。
 * @param slopPx 触控 slop（`viewConfiguration.touchSlop`），由调用方读好传入，判据本身仍可纯测。
 * @param motion 落地端：`PanelPagePager` 里那颗粒（相机 + 视图集合 + 一次 owner 写）。
 *
 * 甩速在这里换算成 dp/s：`PointerInputScope` 自己就是 `Density`，密度只有这一处知道，
 * 判据（[PageSwipe]）因此可以完全不认像素密度、被纯函数格钉死。
 */
internal suspend fun PointerInputScope.runPageSwipeGesture(
    currentPage: Int,
    pageCount: Int,
    pageWidthPx: Float,
    slopPx: Float,
    motion: PageMotion
) {
    if (pageWidthPx <= 0f || pageCount <= 1 || slopPx <= 0f) return
    val tracker = HorizontalVelocityEstimator()
    val velocityToDpPerS: Float = if (density > 0f) 1f / density else 0f
    // 这一记手势是不是正被本轮牵着走：手势协程被取消（pointerInput 因页号/页宽重启、节点下树）
    // 时靠它收尾，否则拖动态会永远挂在 true 上——屏外那页的读屏语义再也清不掉，画面停在一半。
    var owningDrag = false
    try {
        awaitEachGesture {
            // 每一记手势从零开始：上一记的样本不许渗进这一记的甩速读数
            tracker.clear()
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            tracker.addSample(down.uptimeMillis, down.position.x)
            var totalDx = 0f
            var totalDy = 0f
            var claimed = false
            var owned = false
            var deferredToChild = false
            var lifted = false
            var tracking = true
            while (tracking) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change: PointerInputChange? = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    tracking = false
                } else {
                    val delta = change.positionChange()
                    if (change.pressed) tracker.addSample(change.uptimeMillis, change.position.x)
                    if (!deferredToChild) {
                        totalDx += delta.x
                        totalDy += delta.y
                        when (
                            val outcome = PageSwipe.resolve(
                                currentPage = currentPage,
                                pageCount = pageCount,
                                pageWidthPx = pageWidthPx,
                                totalDxPx = totalDx,
                                totalDyPx = totalDy,
                                // 累计位移：slop 与"横向主导"两档都吃它 ⇒ 注入节奏与真机判同一条线
                                slopPx = slopPx,
                                childConsumedPosition = change.positionChangeConsumed()
                            )
                        ) {
                            PageSwipeOutcome.DeferToChild -> {
                                // 只有**横向**竞争者会走到这一档（resolve 按轴筛过）：粘滞让位是对的
                                // ——一记手势不许同时删掉一条消息又把页切走。让位时把已经跟手的位移
                                // 用动画走回当前页，不许 snapTo 硬跳一帧（那正是"闪"的同族形状）。
                                deferredToChild = true
                                if (owned) {
                                    owned = false
                                    owningDrag = false
                                    motion.onDragBail()
                                }
                            }
                            is PageSwipeOutcome.Switching -> {
                                if (!owned) {
                                    owned = true
                                    owningDrag = true
                                    if (!claimed) {
                                        claimed = true
                                        motion.onDragClaim(outcome.targetPage)
                                    }
                                }
                                motion.onDragOffset(outcome.offsetPx)
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
            // 认领过但中途让位的那一记已经由 onDragBail 收尾，不再补一次 settle（两段动画抢
            // 同一颗粒就是"弹回时抖一下"的同族形状）。
            if (claimed && !deferredToChild) {
                owningDrag = false
                motion.onDragSettle(
                    if (owned && lifted) totalDx else 0f,
                    if (owned && lifted) tracker.velocityPxPerS() * velocityToDpPerS else 0f
                )
            }
        }
    } finally {
        // 手势协程被取消（pointerInput 因页号/页宽重启、节点下树）而这一记还牵着相机：
        // 必须让落地端收尾，否则拖动态挂在 true 上 ⇒ 屏外那页的读屏语义清不掉、画面停在一半。
        // 正常走完的那一记（settle 或 bail）已经把这颗置回 false，这里不会再补第二段动画。
        if (owningDrag) {
            owningDrag = false
            motion.onDragBail()
        }
    }
}
