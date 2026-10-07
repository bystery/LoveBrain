package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 长按拖拽那一族的判据（）。**能证什么、不能证什么都写在每一格上**：
 *
 * ① 纯函数那一半（[dragSwapStep] / [edgeAutoScrollSpeedPx]）就是生产算法本体：UI 只把
 *    `layoutInfo` 读出来的槽位与手指 y 喂进来，逐值钉死它们，等于钉死手势背后的算式。
 *    [simulate] 那一格把这两颗接成一条时间线：它**只**按生产 `onDrag` 里那三行做记账
 *    （先累手指位移 → 问一次交换 → 把交换交回的补偿加回去），布局槽按夹具自己的等行高规则给，
 *    算式全部由被测对象交回。判的是那条不变式：**被拖那一条画出来的顶边 == 起点 + 手指累计量**，
 *    逐帧成立。上一版"拖到一半弹回去"缺的就是这笔补偿，那一格正是它的反例。
 * ② 结构那一半只能证明"那一族坏写法没有回来"，它**证明不了手指真的跟上了**：
 *    本机这台仪器注入长按 + 连续拖动的时序判不稳（同一件事见 `MessageRowSwipeDeleteTest`
 *    与 `ReplyGenerateLongPressLayerTest`（已收档） 开头各自的记录），所以这里刻意不写一条
 *    永远绿不起来的时序格子冒充证据。跟手感本身归真机录屏验收。
 */
class MessageRowDragFollowTest {

    /** 生产源码（注释按字符掩成空格，长度与行号不变）：尺读的是代码，不是谁写的那句话 */
    private val source: String by lazy {
        val f = File("src/main/java/com/lovebrain/app/ui/panel/reply/MessageList.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/reply/MessageList.kt")
        assertTrue("找不到生产源码：$f（这把尺没有对象，不许静默空过）", f.isFile)
        SourceScan.maskComments(f.readText())
    }

    private fun linesWith(needle: String): List<String> = source.lines().filter { needle in it }

    /**
     * 按生产 `onDrag` 的记账顺序跑一遍拖拽：返回每一帧的（展示位，画出来的顶边）。
     *
     * 这里刻意不含任何"该怎么交换"的判断——交换与补偿全部由 [dragSwapStep] 交回；
     * 夹具只补两件事：等行高 40 + 间距 4 的布局偏移，以及"交换后被拖行占的是新那一格"。
     */
    private fun simulate(rows: Int, startIndex: Int, deltas: List<Float>, step: Int = 44): List<Pair<Int, Float>> {
        var index = startIndex
        var accumulated = 0f
        val frames = mutableListOf<Pair<Int, Float>>()
        for (delta in deltas) {
            accumulated += delta
            val slot = DragSlot(index, index * step, 40)
            val swap = dragSwapStep(
                dragged = slot,
                offsetPx = accumulated,
                above = if (index > 0) DragSlot(index - 1, (index - 1) * step, 40) else null,
                below = if (index < rows - 1) DragSlot(index + 1, (index + 1) * step, 40) else null
            )
            if (swap != null) {
                accumulated += swap.offsetCompensationPx
                index = swap.newIndex
            }
            frames += index to (index * step + accumulated)
        }
        return frames
    }

    // ═══════════ ① 交换判据：越过相邻行的中心线才交换，交换要扣掉布局偏移 ═══════════

    /**
     * 视觉中心还没越过硬相邻行的中心 ⇒ 不交换。
     * 反例：把判据换成"手指进了那一格的范围就换"（`offset` 与 `offset+size` 那种区间比较）——
     * 一格之内来回抖会一路交换下去，第一帧就满足条件，这一格当场红。
     */
    @Test
    fun `no swap while the dragged centre has not crossed the neighbour centre`() {
        val dragged = DragSlot(index = 1, offsetPx = 0, sizePx = 40)
        val below = DragSlot(index = 2, offsetPx = 44, sizePx = 40)
        // 视觉中心 = 0 + 30 + 20 = 50，相邻中心 = 44 + 20 = 64：还没越过
        assertEquals(null, dragSwapStep(dragged, offsetPx = 30f, above = null, below = below))
        // 正好压在中心线上也不交换（判据是严格越过；压线反复交换就是抖动的来源）
        assertEquals(null, dragSwapStep(dragged, offsetPx = 44f, above = null, below = below))
    }

    /**
     * 向下交换：站进相邻那一格，并交出**该从累计位移里扣掉的量**。
     * 不变式：`新布局偏移 + 新累计位移 == 原布局偏移 + 原累计位移`（逐字相等，不是"差不多"）。
     * 反例：`offsetCompensationPx` 写成 0（交换完不扣）⇒ 被拖行瞬移一格，这一格红；
     * 反例：扣成一格行高而不算 gap ⇒ 数值对不上，同样红。
     */
    @Test
    fun `swapping down keeps the dragged row exactly where the finger put it`() {
        val dragged = DragSlot(index = 1, offsetPx = 0, sizePx = 40)
        val below = DragSlot(index = 2, offsetPx = 44, sizePx = 40)
        val offsetPx = 45f
        val swap = dragSwapStep(dragged, offsetPx, above = null, below = below)
            ?: error("视觉中心 65 已越过相邻中心 64，必须交换——判据没落地就是拖不动")
        assertEquals("交换后该站到相邻那一格：" + swap.newIndex, 2, swap.newIndex)
        val newLayoutOffset = dragged.offsetPx - swap.offsetCompensationPx
        assertEquals("交换后被拖行的新布局偏移该是 44（相邻格之后那一槽）", 44f, newLayoutOffset, 0.001f)
        assertEquals(
            "扣减之后画在手指下的位置必须一步都不跳（跳的就是「拖到一半弹回去」那个手感）",
            dragged.offsetPx + offsetPx,
            newLayoutOffset + (offsetPx + swap.offsetCompensationPx),
            0.001f
        )
    }

    /**
     * 上下两行**高度不同**时也要对得上——只减"一格行高"的那种推法在这里必然算错。
     * 反例：补偿量写成 `dragged.sizePx + gap`（用了自己的高度而不是邻居的）⇒ 这里差 20px，红。
     */
    @Test
    fun `the compensation accounts for neighbours of a different height`() {
        val dragged = DragSlot(index = 1, offsetPx = 0, sizePx = 30)
        val below = DragSlot(index = 2, offsetPx = 34, sizePx = 50)
        val offsetPx = 50f
        val swap = dragSwapStep(dragged, offsetPx, above = null, below = below)
            ?: error("视觉中心 65 已越过相邻中心 59，必须交换")
        val newLayoutOffset = dragged.offsetPx - swap.offsetCompensationPx
        assertEquals("不等高时新槽位 = 34 + 50 - 30 = 54", 54f, newLayoutOffset, 0.001f)
        assertEquals(
            "不等高也得让手指下那颗不动",
            50f, newLayoutOffset + (offsetPx + swap.offsetCompensationPx), 0.001f
        )
    }

    /**
     * 向上交换：走的是"占相邻格那一槽"的另一支，符号与向下相反。
     * 反例：只写了一支（向下）⇒ 向上拖永远不交换，这一格红。
     */
    @Test
    fun `swapping up moves into the neighbour slot and compensates the other way`() {
        val dragged = DragSlot(index = 1, offsetPx = 44, sizePx = 40)
        val above = DragSlot(index = 0, offsetPx = 0, sizePx = 40)
        val offsetPx = -50f
        val swap = dragSwapStep(dragged, offsetPx, above = above, below = null)
            ?: error("视觉中心 14 已越过上方行的中心 20，必须交换")
        assertEquals(0, swap.newIndex)
        assertEquals("向上交换后被拖行占的就是上方那一槽（offset 0）",
            0f, dragged.offsetPx - swap.offsetCompensationPx, 0.001f)
        assertEquals(
            "向上同样不许瞬移",
            dragged.offsetPx + offsetPx,
            0f + (offsetPx + swap.offsetCompensationPx),
            0.001f
        )
    }

    /**
     * 首行往上、末行往下没有邻居 ⇒ 不交换。
     * 反例：从列表两端越界取邻居（`getOrNull` 写成 `first()`/索引直取）⇒ 要么崩要么自交换，红。
     */
    @Test
    fun `the first and the last row have nothing to swap with in the direction they are pushed`() {
        val first = DragSlot(index = 0, offsetPx = 0, sizePx = 40)
        assertEquals(null, dragSwapStep(first, offsetPx = -60f, above = null, below = null))
        val last = DragSlot(index = 3, offsetPx = 132, sizePx = 40)
        assertEquals(null, dragSwapStep(last, offsetPx = 60f, above = null, below = null))
    }

    /**
     * 一路拖到底：**每一帧**画出来的顶边都等于"起点 + 手指走过的总量"，一次都不跳。
     * 四行、每 5px 一帧、共走 150px ⇒ 恰好换三次（44、88、132 各越线一次），最后停在第 3 格。
     *
     * 反例（就是"拖到一半弹回去"那两个成因）：
     * · 交换后不把 `offsetCompensationPx` 加回累计位移 ⇒ 第一次交换那帧读数从 45 跳到 89，红；
     * · 补偿量算错一档（漏掉 gap、或用自己行高代替邻居槽差）⇒ 每换一格漂 4px，红；
     * · 判据退化成"进了邻居范围就换"⇒ 同一帧里来回换，`展示位单调` 那句与"只换三次"同时红；
     * · 越界取邻居（末行下面还去要一条）⇒ 要么崩要么自交换，位置那句红。
     */
    @Test
    fun `dragging down paints the row under the finger on every frame`() {
        val deltas = List(30) { 5f }
        val frames = simulate(rows = 4, startIndex = 0, deltas = deltas)
        var walked = 0f
        frames.forEachIndexed { k, (index, visualTop) ->
            walked += deltas[k]
            assertEquals(
                "第 $k 帧被拖那一条不在手指下（跑到第 $index 格，画在 $visualTop，该在 $walked）",
                walked, visualTop, 0.001f
            )
        }
        val indices = frames.map { it.first }
        assertEquals("展示位只许朝一个方向走（来回抖就是压线反复交换）：" + indices,
            indices.sorted(), indices)
        assertEquals("四行拖到底只该换三次：" + indices,
            3, indices.zip(indices.drop(1)).count { (a, b) -> b > a })
        assertEquals("走完 150px 该停在最后一格", 3, indices.last())
    }

    /**
     * 反方向同样逐帧跟手（补偿符号那一支单独钉一次：只写对向下那一支的算式在这里必错）。
     * 反例：向上那一支忘了加补偿 ⇒ 第一次越线那帧从 -45 的相对位置跳到 +？；
     * 反例：向上那支扣成了向下的符号 ⇒ 每换一格漂 88px，红。
     */
    @Test
    fun `dragging up paints the row under the finger on every frame`() {
        val deltas = List(30) { -5f }
        val frames = simulate(rows = 4, startIndex = 3, deltas = deltas)
        var walked = 0f
        frames.forEachIndexed { k, (index, visualTop) ->
            walked += deltas[k]
            assertEquals(
                "第 $k 帧向上拖也不许瞬移（第 $index 格画在 $visualTop，该在 ${132f + walked}）",
                132f + walked, visualTop, 0.001f
            )
        }
        val indices = frames.map { it.first }
        assertEquals("向上也只许单调让位：" + indices, indices.sortedDescending(), indices)
        assertEquals("拖到顶该停在第一格", 0, indices.last())
    }

    // ═══════════ ② 边缘自动滚动：只有一颗任务，速度归零就是立刻停 ═══════════

    /**
     * 视口中段与**带外**速度必须是 0——那颗任务读到 0 就退出，这就是"离开边缘立刻停止"。
     * 反例：判据写成"手指在视口内就一直滚"⇒ 中间那三格红（用户看到的是列表自己跑）；
     * 反例：出带的那一格写成 `<=` 边界算反 ⇒ 刚出带还剩一个速度，红。
     */
    @Test
    fun `no edge scrolling in the middle of the viewport or outside the zone`() {
        assertEquals(0f, edgeAutoScrollSpeedPx(300f, 0, 600, 80f), 0f)
        assertEquals(0f, edgeAutoScrollSpeedPx(500f, 0, 600, 80f), 0f)  // 离底还有 100 > 带宽
        assertEquals(0f, edgeAutoScrollSpeedPx(100f, 0, 600, 80f), 0f)  // 离顶还有 100 > 带宽
        // 刚出带那一格（离底 81 > 80）与刚进带那一格（离底 79）之间必须换档
        assertEquals("出了带宽就是立刻停", 0f, edgeAutoScrollSpeedPx(519f, 0, 600, 80f), 0f)
        assertTrue("带内同距离还该给到最低档", edgeAutoScrollSpeedPx(521f, 0, 600, 80f) > 0f)
        assertEquals("带外再往外也不许有速度", 0f, edgeAutoScrollSpeedPx(300f, 0, 600, 80f), 0f)
    }

    /**
     * 带内**单调**：离边越远越慢，而且这几个距离必须读出**互不相同**的数。
     * 后面那句哨兵是给"循环没真的跑"兜底的——把 `depthSpeed` 写成常量（或与距离无关的表达式）
     * 时，五个距离会读回五个一样的数，只判 `>=` 关系的断言会全部假绿。
     *
     * 反例：`1f - distance/zone` 写成 `distance/zone`（越靠边越慢）⇒ 递减那句反了，红；
     * 反例：忘了 `coerceAtLeast(6f)` ⇒ 刚进带那一格读到 0.3（用户看到"擦到边几乎不动"），红；
     * 反例：忘了 `coerceAtMost` 的上档由压边那一格管（见下一格）。
     */
    @Test
    fun `edge speed falls off monotonically with the distance from the border`() {
        val distances = listOf(1f, 20f, 40f, 55f)
        val speeds = distances.map { edgeAutoScrollSpeedPx(600f - it, 0, 600, 80f) }
        for (k in 1 until speeds.size) {
            assertTrue("离边越远必须越慢：$distances → $speeds", speeds[k] < speeds[k - 1])
        }
        assertEquals("四个距离必须读出四个不同的速度（五个相同 = 这一族循环没跑）：$speeds",
            speeds.size, speeds.distinct().size)
        assertEquals("顶边镜像：符号相反、数值相同",
            speeds.map { -it }, distances.map { edgeAutoScrollSpeedPx(it, 0, 600, 80f) })
        assertEquals("刚进带宽那一边仍要够到最低档，不许几乎不动",
            6f, edgeAutoScrollSpeedPx(600f - 79f, 0, 600, 80f), 0.001f)
    }

    /**
     * 底部为正、顶部为负、两根端点夹住。
     * 反例：忘了取负号（顶部也向下滚）⇒ 顶边那一格红；
     * 反例：没有上限（手指越出去速度爆掉）⇒ 压边/越出那两格读到 >24，红。
     */
    @Test
    fun `edge speed is signed and stays inside its two ends`() {
        assertTrue(edgeAutoScrollSpeedPx(560f, 0, 600, 80f) > 0f)
        assertEquals("压在下边缘就是那一档上限", 24f, edgeAutoScrollSpeedPx(600f, 0, 600, 80f), 0.001f)
        assertEquals("越出下边缘也不许失控", 24f, edgeAutoScrollSpeedPx(999f, 0, 600, 80f), 0.001f)
        assertEquals("压在顶边缘符号相反", -24f, edgeAutoScrollSpeedPx(0f, 0, 600, 80f), 0.001f)
        assertEquals("越出上边缘同样夹住", -24f, edgeAutoScrollSpeedPx(-999f, 0, 600, 80f), 0.001f)
    }

    /** 带宽为 0（这一档没开）时恒不滚——不许拿"默认值"蒙出一个速度 */
    @Test
    fun `a zero width edge zone never scrolls`() {
        assertEquals(0f, edgeAutoScrollSpeedPx(599f, 0, 600, edgeZonePx = 0f), 0f)
        assertEquals(0f, edgeAutoScrollSpeedPx(1f, 0, 600, edgeZonePx = -80f), 0f)
    }

    /** 视口还没量到（start==end）时不许给速度：第一帧凭空滚动是最难查的那种抖 */
    @Test
    fun `an unmeasured viewport gives no speed`() {
        assertEquals(0f, edgeAutoScrollSpeedPx(0f, 0, 0, 80f), 0f)
        // 视口读数倒挂（坏布局）同样不许造速度
        assertEquals(0f, edgeAutoScrollSpeedPx(0f, 600, 0, 80f), 0f)
    }

    // ═══════════ ③ 结构：那一族坏写法不许回来（只证没退回去，不证跟手） ═══════════

    /**
     * 被手指控制的那一行不许吃 `animateItemPlacement`——它会对着手指正占的那一格反向插值。
     * 反例：把 `modifier = Modifier.animateItemPlacement()` 无条件挂回每一行 ⇒ 出现次数还是 1，
     * 但那一行里没有"被拖那一条"这一档 ⇒ 红（这一格要的就是那一档真的在场）。
     */
    @Test
    fun `the placement animation is switched off for exactly the row the finger controls`() {
        val hits = linesWith("animateItemPlacement()")
        assertEquals("全文件只该有一处条目位移动画，实到：$hits", 1, hits.size)
        assertTrue(
            "那一处必须写成「被拖的那一条不给动画」这一档，实到：" + hits.single(),
            "isDragged" in hits.single()
        )
    }

    /**
     * 边缘滚动只留一颗可取消的任务：`scrollBy` 全文件一处，挂在带拖拽会话键的 `LaunchedEffect` 上，
     * 且它前面 200 字符内不许出现 `launch`（那正是"每个手势事件起一颗新任务"的老写法），
     * 后面必须按帧 `delay`。
     * §8 之后这一颗**不再自己拿速度滚**：每一帧先问 [runDragFrame]（同一份最新布局），
     * 只滚它交回的那个量，并把"没滚满"的那一截从补偿里退回来。
     * 反例：`scope.launch { listState.scrollBy(...) }` 回到 onDrag 里 ⇒ 三条判据一起红；
     * 反例：把取消键 `dragSession` 摘掉（任务停不下来）⇒ 第二条红；
     * 反例：滚不动还照样补满（画位凭空飘）⇒ 最后那句红。
     */
    @Test
    fun `the drag auto scroll stays one cancellable task instead of one launch per frame`() {
        val hits = linesWith("scrollBy(")
        assertEquals("自动滚动只该有一处出口，实到：$hits", 1, hits.size)
        val at = source.indexOf("scrollBy(")
        val head = source.substring(0, at)
        assertTrue(
            "那颗任务必须挂在带拖拽会话键的 LaunchedEffect 上（没有取消键就停不下来）",
            source.contains("LaunchedEffect(dragSession")
        )
        assertTrue(
            "scrollBy 必须在 LaunchedEffect 里、不在 pointerInput 的手势回调里（LaunchedEffect 在 " +
                "${head.lastIndexOf("LaunchedEffect(")}，pointerInput 在 ${head.lastIndexOf("pointerInput(")}）",
            head.lastIndexOf("LaunchedEffect(") > head.lastIndexOf("pointerInput(")
        )
        val before = source.substring((at - 200).coerceAtLeast(0), at)
        assertTrue("不许逐帧 launch 一颗新的滚动任务，实到片段：$before", "launch" !in before)
        assertTrue("那一帧的量必须由帧判据交回（自己拿速度滚就是旧那颗任务的形状）：$before",
            "runDragFrame(null, edgeSpeedPx)" in before)
        val after = source.substring(at, (at + 200).coerceAtMost(source.length))
        assertTrue("那颗任务必须按帧走（没有 delay 就是空转或一帧滚到底）：$after", "delay(" in after)
        assertTrue(
            "只按真滚掉的那一截回填累计位移（多补 = 被拖行脱离手指）：$after",
            "scrollBy(planned.toFloat())" in after && "dragOffsetY += scrolled - planned" in after
        )
    }

    /**
     * 震动只有两处出口：长按起手一次、真换了一格一次（备注行不再是删除对象，它那一档随 NoteEntry 撤了）。
     * 反例：把震动写进逐帧累计位移那一行（`dragOffsetY += dragAmount.y`）⇒ 出口数涨到 3，
     * 或者那一行自己出现 performHapticFeedback ⇒ 两条都红，正是"每像素震一次"那个投诉的形态。
     */
    @Test
    fun `haptics fire on grab and on a real swap, never per pixel`() {
        assertEquals("震动出口只该有两处：" + linesWith("performHapticFeedback"),
            2, linesWith("performHapticFeedback").size)
        assertEquals("长按起手那一震只该写一次：" + linesWith("HapticFeedbackType.LongPress"),
            1, linesWith("HapticFeedbackType.LongPress").size)
        assertEquals("换格那一震只该写一次：" + linesWith("HapticFeedbackType.TextHandleMove"),
            1, linesWith("HapticFeedbackType.TextHandleMove").size)
        val accumulators = linesWith("dragOffsetY +=")
        assertTrue("有累计位移的行：" + accumulators, accumulators.isNotEmpty())
        accumulators.forEach {
            assertTrue("逐帧累计位移那一行不许顺手震：" + it, "performHapticFeedback" !in it)
        }
    }

    /**
     * 位移与层级：被拖那一条按**那颗自己交回来的累计位移**画 translationY，并浮在其他消息之上。
     * 反例：画成"按 index 换算出来的位置"（`index * rowHeight` 那种）⇒ 读的不再是 draggedOffsetY()，红；
     * 反例：把声明与调用改得对不上（只留一边）⇒ 签名那句红。
     */
    @Test
    fun `the dragged row is painted at the accumulated offset and lifted above the others`() {
        val translation = linesWith("translationY =")
        assertEquals("聊天行只该有一处纵向位移出口，实到：$translation", 1, translation.size)
        assertTrue("那一处画的必须是外面交回来的那颗累计位移读取器：" + translation.single(),
            "draggedOffsetY()" in translation.single())
        assertTrue("MessageRow 的签名里就得带着这一颗（声明与调用不许各写一份）：" +
            linesWith("draggedOffsetY: () -> Float"),
            linesWith("draggedOffsetY: () -> Float").size == 1)
        val layer = linesWith("zIndex(")
        assertEquals("层级只该有一处：" + layer, 1, layer.size)
        assertTrue("层级必须跟着「谁被手指控制」走：" + layer.single(), "isDragged" in layer.single())
    }

    /**
     * 拖拽不许把尾部那行备注搬进真实聊天顺序：备注挂在 LazyColumn **之外**，
     * 既不在 items 的子树里，聊天 items 也只喂两列对话。
     * 尺的对象是 `itemsIndexed(...)` 这一次调用的**子树**（Compose 的尾随 lambda 不在圆括号里，
     * 只按配对右括号取终点就会看不见 items 的内容——`SourceScan.callEnd` 管的就是这一件事）。
     * 反例：把备注塞进 itemsIndexed ⇒ 后两句红；
     * 反例：给 items 喂整个 `messages`（含旧 IDEA 行）⇒ "喂进去的必须是那两列对话"红，
     *        于是旧想法又被当成可拖的聊天气泡；
     * 反例：聊天行整个不再走 itemsIndexed（换成手写 items）⇒ "items 入口只该有一处"先红，
     *        不许让它因为"读不到东西"而空过。
     */
    @Test
    fun `the note line stays out of the lazy list the drag reorders`() {
        val itemEntries = linesWith("itemsIndexed(")
        assertEquals("聊天行只该有一处 items 入口，实到：$itemEntries", 1, itemEntries.size)
        val open = source.indexOf("itemsIndexed(") + "itemsIndexed".length - 1
        val end = SourceScan.callEnd(source, open)
        assertTrue("读不到 items 的子树（下标 $open→$end），这把尺没咬住东西", end > open + 20)
        val insideItems = source.substring(open, end)
        assertTrue("items 喂进去的必须是那两列对话：" + insideItems.take(160),
            "dialogueDisplayed" in insideItems)
        assertTrue("旧想法行不许被喂进聊天列表：" + insideItems.take(160),
            "messages" !in insideItems)
        assertTrue("备注那一行不许长在聊天列表里：" + insideItems,
            "AdvisorNoteLine(" !in insideItems)
        assertTrue("列表里画的必须就是聊天行", "MessageRow(" in insideItems)
    }

    /**
     *  那行灰字在**两个分支**都画：有聊天时钉在最后一个气泡下面，只有备注没聊天时（空态分支）
     * 同样要画——原话要的就是"只有备注、也能看到这行灰字"。
     * 这一格只买"两个分支都别漏"，画得对不对由 `ChatBubbleAppearanceTest` 那几格在树上判。
     * 反例：只在有聊天的那一支挂 ⇒ 数到 1 颗，红（用户首轮只有备注时看不见自己写的东西）；
     * 反例：两支各画两遍（同一分支里长出新的一份）⇒ 数到 3 颗，红（同一句灰字出现两次）；
     * 反例：某一支改读宿主原文 `noteText` 或自己再拼一遍旧想法行 ⇒ 那句"实参里必须是折叠结果"红。
     * ⚠ 实参按**括号配对**取，不按"同一行"取：调用点写成多行形制后，`AdvisorNoteLine(` 那一行
     *   里没有 `advisorNoteText` 这颗字（W6 那一格红的就是这一档），而行内注释掩成空格后
     *   长度不变 ⇒ 取到的仍是那次调用的实参，一字不多一字不少。
     */
    @Test
    fun `the note line is drawn in both the chat branch and the empty branch`() {
        val hits = linesWith("AdvisorNoteLine(")
        assertEquals("备注灰字只该在两个分支各画一次，实到：" + hits, 2, hits.size)
        val noteArgumentSlices = Regex("AdvisorNoteLine\\(").findAll(source).map { match ->
            val open = match.range.first + "AdvisorNoteLine".length
            val close = SourceScan.closeIndexOf(source, open)
            assertTrue(
                "第 ${source.take(open).count { it == '\n' } + 1} 行那颗 AdvisorNoteLine 的括号没配上" +
                    "（开 $open → 闭 $close），这把尺没咬住实参",
                close > open + 10 && source[close - 1] == ')'
            )
            source.substring(open + 1, close - 1)
        }.toList()
        assertEquals("读到的实参数必须与调用点数一致：" + noteArgumentSlices.size, 2, noteArgumentSlices.size)
        // 两处都挂在同一个折叠结果上（不许一支读宿主原文、另一支自己再拼一遍旧想法行）
        noteArgumentSlices.forEachIndexed { k, args ->
            assertTrue("第 $k 处必须读折叠结果 advisorNoteText（实参里读不到这一颗）：" + args.take(120),
                "noteText = advisorNoteText" in args)
        }
        assertTrue("折叠只该有一颗真源：" + linesWith("foldAdvisorNote("),
            linesWith("foldAdvisorNote(").size == 2)   // 声明一处 + 列表里调用一处
    }

    /**
     * 删除的两条出口都只认 id；阈值那颗纯函数必须仍按**行宽的比例**算（不是拍脑袋的定值 px）。
     * 反例：退场收尾改用记住的旧下标删 ⇒ 第一句红；
     * 反例：阈值写成固定 100dp ⇒ 第二句红（窄面板与宽面板的判定会完全不同）。
     */
    @Test
    fun `the delete paths stay id based and the threshold stays a fraction of the row`() {
        assertTrue("横滑阈值必须仍按行宽的比例算：" + linesWith("SWIPE_DELETE_THRESHOLD_FRACTION"),
            source.contains("rowWidthPx * MessageDimens.SWIPE_DELETE_THRESHOLD_FRACTION"))
        val exits = linesWith("onDelete(")
        assertEquals("交给持有者的删除出口只该有一处：" + exits, 1, exits.size)
        assertTrue("那一处交出去的必须是 id：" + exits.single(), "id" in exits.single())
        assertTrue("重排交的必须是原始下标那一对（展示位与它是两件事；§8 之后这一对由帧判据交回）",
            source.contains("onReorder(from, to)"))
        assertTrue("拖拽认人只认稳定 id，不许按格号认：" + linesWith("draggedId ="),
            source.contains("draggedId = hitId") && source.contains("hitItem?.key as? String"))
    }

    /**
     * 手势不许在整层无条件吃掉所有移动事件：全文件只有那一颗 `change.consume()`，
     * 而且它必须待在长按拖拽的 onDrag 里（长按已经起势之后才接管）。
     * 反例：把 consume 提到 onDragStart 或面板外层 ⇒ 横滑删除与父页切页都拿不到事件，红。
     */
    @Test
    fun `only the long press drag consumes pointer changes`() {
        val consumes = linesWith("change.consume()")
        assertEquals("消费移动事件只该有一处（长按起势之后），实到：$consumes", 1, consumes.size)
        val at = source.indexOf("change.consume()")
        val before = source.substring(0, at)
        assertTrue("那一处必须在 detectDragGesturesAfterLongPress 之内",
            before.lastIndexOf("detectDragGesturesAfterLongPress(") > before.lastIndexOf("pointerInput("))
        val after = source.substring(at, (at + 400).coerceAtMost(source.length))
        assertTrue("它后面紧跟的就是按手指累计位移那一行：" + after.take(200),
            "dragOffsetY += dragAmount.y" in after)
    }
}
