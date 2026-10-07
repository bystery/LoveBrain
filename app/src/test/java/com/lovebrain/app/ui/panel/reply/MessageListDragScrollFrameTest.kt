package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * 指导书 §8 在**算式**上的那一半：自动滚动、交换、视口夹持三者读的是同一份（最新）布局，
 * 而且被拖那一条与刚发出的交换每一帧都还看得见。
 *
 * 判的对象是 [dragFramePlan]（生产里手指帧与自动滚动帧**唯一**共用的一颗）；UI 侧只接线，
 * 所以接线那一档由最后的源码尺钉（`both drag branches read the newest layout through one plan`）。
 *
 * 两类缺陷态各有一格"缺陷态真喂进去"的反例（这是本轮要修的那两档，不是又写一遍大视口直接重排）：
 * ① **被拖那一条不在最新那份 `visibleItemsInfo` 里**（被自动滚出去的下一帧就是这个读数）：
 *    旧那颗任务在这种帧里还继续 `scrollBy` + 加偏移，条目被 LazyColumn 虚拟化掉 ⇒
 *    "手指底下那条凭空不见"。这里判两件事：量不到人就**一分都不滚、一像素都不补、一次都不夹**
 *    （`a frame that cannot measure…`），而且**根本滚不出这个态**——每帧的滚动量先过
 *    [budgetedAutoScrollPx] 的"这一格至少还留在视口里"预算（`the auto scroll never pushes…` 与
 *    `every scroll frame leaves…` 两格：三档逐值 + 八档槽位 × 双向六速扫不变式）。
 * ② **交换发出到布局追上之间的那几帧**（`pendingSwapFromIndex` 还压着）：旧写法在这种帧里
 *    继续拿**交换前**的槽位夹持、继续补滚动量 ⇒ "交换尚未落到实际布局时跳动"。这里判：
 *    那种帧一律不滚（`holds the scroll…`）、位移只按发出交换那帧**预测出的落点**夹
 *    （`the clamp follows the landing slot…` 拿 200px 视口把两种算法的读数差逐值量出来：
 *    按落点夹 = 中心 200（在视口里）、按旧槽位夹 = 中心 244（出界））；
 * 以及交换本身在**没有手指事件的滚动帧**里也要照发（`a scroll frame commits…`），
 * 相邻格是尾部备注那一行时不许发假交换、更不许扣补偿（`a note footer neighbour…`）。
 *
 * ⚠ 这一族**证明不了手感**：`scrollBy` 之后布局到底几帧才追上、条目被回收的真实边界
 *   （`beyondBoundsItemPlacements` 那一档）、跟不跟手、震得对不对，JVM 上量不到 ⇒ 归真机录屏。
 *   语义树那一半（屏上顺序 == 落库 == prompt）在 `MessageListDragContainmentTest`。
 */
class MessageListDragScrollFrameTest {

    // ═══════════ 夹具：只造槽位几何，判据一条都不在夹具里写 ═══════════

    /** 等行高一档：行 40px + 间距 4px ⇒ 槽位 `index * 44`，key 就是那条消息的 id */
    private fun slot(index: Int, offsetPx: Int, sizePx: Int = ROW, key: String? = "row$index") =
        DragSlot(index, offsetPx, sizePx, key)

    /** 屏上那几行（展示位 = 槽位 index、原始下标 = 同一个数）：`count` 行 */
    private fun displayedRows(count: Int) = List(count) { DragRow(it, it, "row$it") }

    private fun input(
        rows: List<DragSlot>,
        draggedId: String = "row2",
        offsetPx: Float = 0f,
        pending: Int = -1,
        landing: DragSlot? = null,
        start: Int = 0,
        end: Int = VIEWPORT_END,
        fingerY: Float? = null,
        requestedScrollPx: Float = 0f,
        keepVisiblePx: Int = KEEP,
        displayed: List<DragRow> = displayedRows(8)
    ) = DragFrameInput(
        rows = rows,
        displayed = displayed,
        draggedId = draggedId,
        offsetPx = offsetPx,
        pendingSwapFromIndex = pending,
        landingSlot = landing,
        viewportStartPx = start,
        viewportEndPx = end,
        fingerY = fingerY,
        edgeZonePx = 80f,
        requestedScrollPx = requestedScrollPx,
        keepVisiblePx = keepVisiblePx
    )

    /** 三行都在场、被拖的是中间那一格（156 / 200 / 244 那一族，起点由 `topOfDragged` 平移） */
    private fun threeRows(topOfDragged: Int = 200) = listOf(
        slot(1, topOfDragged - STEP),
        slot(2, topOfDragged),
        slot(3, topOfDragged + STEP)
    )

    // ═══════════ ① 缺陷态：被拖那一格量不到 ═══════════

    /**
     * 缺陷态①：`visibleItemsInfo` 里**没有**被拖那一条（被自动滚出去的下一帧就是这个读数）。
     * 判这一帧什么算式都不许跑：不滚、不补、不夹、不交换、速度归零，闸门与落点原样留着
     * （下一次手指事件还得靠它挡重复那一发）。
     * 反例：拿着上一帧的槽位继续夹 ⇒ 第二句红（位移被改掉）；
     * 反例：旧那颗任务那种"不看布局照样 scrollBy + 加偏移" ⇒ 第一、第四句一起红。
     */
    @Test
    fun `a frame that cannot measure the dragged row scrolls nothing and pays nothing`() {
        val staleLanding = slot(6, 300)
        val plan = dragFramePlan(
            input(
                rows = listOf(slot(1, 0), slot(2, 44), slot(3, 88)),   // 里面没有 row9
                draggedId = "row9",
                offsetPx = 320f,
                pending = 5,
                landing = staleLanding,
                requestedScrollPx = 24f
            )
        )
        assertEquals("量不到被拖那一格就不许再滚（旧那颗就是在这里继续 scrollBy 把条目推丢）", 0, plan.scrollPx)
        assertEquals("也不许拿旧槽位继续夹/继续补：位移一分都不动", 320f, plan.offsetPx, 0.001f)
        assertEquals("量不到人就不许发交换：" + plan.reorder, null, plan.reorder)
        assertEquals("速度交回 0 ⇒ 那颗自动滚动任务自己退出", 0f, plan.speedPx, 0f)
        assertEquals("闸门键不许被抹掉（布局追上的那一帧还得挡着重复那一发）", 5, plan.pendingSwapFromIndex)
        assertEquals("落点槽位也原样留着", staleLanding, plan.landingSlot)
        assertFalse("交回 false = UI 该把条目请回视口，而不是继续算账", plan.draggedVisible)
    }

    /** 视口还没量到（start == end，列表没布局完）时同样一分不滚、不造新位置 */
    @Test
    fun `an unmeasured viewport scrolls nothing and leaves the offset alone`() {
        val plan = dragFramePlan(
            input(rows = threeRows(), offsetPx = 77f, start = 0, end = 0, requestedScrollPx = 24f)
        )
        assertEquals(0, plan.scrollPx)
        assertEquals(77f, plan.offsetPx, 0.001f)
        assertEquals(0f, plan.speedPx, 0f)
        assertTrue("被拖那一条量得到（只是视口没量到）⇒ 不该触发『请回视口』那一支", plan.draggedVisible)
    }

    /**
     * 缺陷态①的**根因**那一档：每帧的滚动量都夹在"被拖那一格至少还留 [KEEP] 个像素在视口里"内。
     * 逐值三档：预算够（照原速滚）／预算不够（只滚剩下一截）／已经贴到线（不滚，速度交回 0）。
     * 反例：不夹预算 ⇒ 第二句读到 60（旧那颗就是把槽位一路推到视口外再被回收），红；
     * 反例：夹到干脆不滚 ⇒ 第一句红（正常跟手没了）。
     */
    @Test
    fun `the auto scroll never pushes the dragged slot out of the viewport`() {
        val roomy = dragFramePlan(input(rows = threeRows(200), requestedScrollPx = 24f))
        assertEquals("离视口上沿还远 ⇒ 照原速滚", 24, roomy.scrollPx)
        assertEquals(24f, roomy.speedPx, 0f)

        // 槽位 30、行 40、keep 20 ⇒ 下沿最多退到 20：还能滚 50，不许滚 60
        val tight = dragFramePlan(input(rows = threeRows(30), requestedScrollPx = 60f))
        assertEquals("滚过这一帧被拖那一条就出视口了 ⇒ 只许滚剩下的 50", 50, tight.scrollPx)

        // 槽位 -20（下沿正好 20 = keep）⇒ 一分都不能再滚，速度交回 0 = 那颗任务退出
        val spent = dragFramePlan(input(rows = threeRows(-20), requestedScrollPx = 24f))
        assertEquals("预算用尽还不许滚 ⇒ 条目被回收就是这么来的", 0, spent.scrollPx)
        assertEquals("不滚了就把速度交回 0，不许空转", 0f, spent.speedPx, 0f)
    }

    /**
     * 不变式扫一遍（八档合法槽位 × 双向六种速度）：**滚完这一帧，被拖那一格仍按 keep 线留在视口里**，
     * 而且滚动量永不超过这一帧要的那一点（多滚 = 拿不存在的像素做补偿）。
     * 末尾两句哨兵是给"干脆都不滚"这种假绿兜底的：必须真的出现过滚满的帧与被截短的帧。
     */
    @Test
    fun `every scroll frame leaves the dragged slot inside the viewport in both directions`() {
        var fullSpeedFrames = 0
        var truncatedFrames = 0
        // 合法带：顶边在 [KEEP - ROW, VIEWPORT_END - KEEP] = [-20, 580]（这一族帧帧由预算维持）
        for (topOfDragged in listOf(-20, 0, 44, 150, 300, 520, 579, 580)) {
            for (requested in listOf(24f, 6f, -24f, -6f, 100f, -100f)) {
                val plan = dragFramePlan(
                    input(rows = threeRows(topOfDragged), requestedScrollPx = requested)
                )
                assertTrue("被拖那一格明明在场：" + plan.draggedVisible, plan.draggedVisible)
                assertTrue(
                    "滚动量不许超过这一帧要的那一点（$topOfDragged、要 $requested、实到 ${plan.scrollPx}）",
                    abs(plan.scrollPx) <= abs(requested)
                )
                val shiftedTop = topOfDragged - plan.scrollPx
                assertTrue(
                    "滚完这一帧被拖那一条就不在视口里了（槽位 $topOfDragged、滚 ${plan.scrollPx} ⇒ " +
                        "新槽位 $shiftedTop）",
                    shiftedTop + ROW >= KEEP && shiftedTop <= VIEWPORT_END - KEEP
                )
                if (plan.scrollPx == requested.toInt()) fullSpeedFrames++
                if (plan.scrollPx != 0 && abs(plan.scrollPx) < abs(requested)) truncatedFrames++
            }
        }
        assertTrue("必须真的滚满过（一次都没有 = 这一族压根不滚了）：$fullSpeedFrames", fullSpeedFrames > 4)
        assertTrue("也必须真的截短过（一次都没有 = 预算那一档没生效）：$truncatedFrames", truncatedFrames > 0)
    }

    /**
     * 补偿只等于**真滚掉的那一截**：没有交换、夹持也没咬的那一帧，
     * `新槽位 + 新位移` 必须与 `旧槽位 + 旧位移` 逐字相等（画在手指下的那颗一个像素都不漂）。
     * 反例：按请求量补而实际滚得少 ⇒ 第二句红，用户看到"它自己飘一下"。
     */
    @Test
    fun `a plain scroll frame pays exactly what it scrolled`() {
        val paintedBefore = 200 + 10f
        val plan = dragFramePlan(input(rows = threeRows(200), offsetPx = 10f, requestedScrollPx = 24f))
        assertEquals("位移 = 旧值 + 这一帧真滚掉的量", 34f, plan.offsetPx, 0.001f)
        assertEquals(
            "画出来的顶边不许因为滚动而移动（这就是『补偿 = 滚动量』那一条）",
            paintedBefore, (200 - plan.scrollPx) + plan.offsetPx, 0.001f
        )
        assertEquals("没越过中心线就不许发交换：" + plan.reorder, null, plan.reorder)
        assertEquals("滚动帧没有手指位置可读 ⇒ 维持原速，等下一帧", 24f, plan.speedPx, 0f)
    }

    // ═══════════ ② 缺陷态：交换还在路上，布局没追上 ═══════════

    /**
     * 缺陷态②：上一次交换已经发出、布局还停在**发出那一格**（`dragged.index == pending`）。
     * 这一帧读到的槽位是交换前的 ⇒ 一律不滚（滚了就是拿旧槽位算补偿、再拿旧槽位夹），
     * 位移只按发出那帧预测的**落点**夹；速度维持原速 = 等一帧再继续，不许就此停住。
     * 反例：照旧 scrollBy ⇒ 第一句红（旧布局 + 新补偿混用就是那一档跳动）；
     * 反例：把任务判死（速度归零） ⇒ 第三句红（贴边滚动一次交换之后再也不走）。
     */
    @Test
    fun `holds the scroll until the committed swap lands in the layout`() {
        val landing = slot(3, 244)
        val plan = dragFramePlan(
            input(rows = threeRows(200), offsetPx = 6f, pending = 2, landing = landing,
                requestedScrollPx = 24f)
        )
        assertEquals("布局没追上那一帧一格都不滚", 0, plan.scrollPx)
        assertEquals("不许发第二次（同一对下标发两次会把那一步整个抵消）：" + plan.reorder, null, plan.reorder)
        assertEquals("速度留住：只等一帧，不是把自动滚动关掉", 24f, plan.speedPx, 0f)
        assertEquals("闸门键留着", 2, plan.pendingSwapFromIndex)
        assertEquals("落点留着", landing, plan.landingSlot)
        assertEquals("位移仍按**落点**那一格夹（6 在范围内，原样交回）", 6f, plan.offsetPx, 0.001f)
    }

    /**
     * 同一态的另一半：那种帧里夹持**必须**按落点格算，不许退回旧槽位。
     * 数字钉死（视口 0..200、落点格 150、旧槽位 106、行 40、位移 200）：
     * · 按落点夹：上限 = 200 - 20 - 150 = 30 ⇒ 画出来的中心 150+30+20 = 200（正好在视口里）；
     * · 按旧槽位夹（上一版的形状）：上限 = 200 - 20 - 106 = 74 ⇒ 按落点算的中心 = 244（出界）。
     * 反例：`landingSlot` 那一支被摘掉/改回读旧槽位 ⇒ 第一句读到 74，红；第二句当场红。
     */
    @Test
    fun `the clamp follows the landing slot while the layout is still behind`() {
        val landing = slot(3, 150)
        val plan = dragFramePlan(
            input(rows = threeRows(106), offsetPx = 200f, pending = 2, landing = landing,
                start = 0, end = 200)
        )
        assertEquals("夹的是落点那一格（旧槽位那一夹会读到 74）", 30f, plan.offsetPx, 0.001f)
        val centre = landing.offsetPx + plan.offsetPx + ROW / 2f
        assertTrue("按落点画出来的中心必须留在视口里（实到 $centre）", centre <= 200f + 0.001f)
        assertEquals("这一帧不许滚（旧布局不配拿补偿）", 0, plan.scrollPx)
    }

    /**
     * 落点也没量到（列表被别处改花、闸门还压着）时：**原样交回，不夹**。
     * 这一句买的是"宁可不夹也不拿旧槽位夹"——旧写法正是拿旧槽位夹出了一次瞬移。
     */
    @Test
    fun `no landing prediction means the offset is handed back untouched`() {
        val plan = dragFramePlan(
            input(rows = threeRows(106), offsetPx = 200f, pending = 2, landing = null,
                start = 0, end = 200)
        )
        assertEquals(0, plan.scrollPx)
        assertEquals("读不到落点就不许凭空造一个位置", 200f, plan.offsetPx, 0.001f)
    }

    /**
     * 交换在**没有手指事件的滚动帧**里也照发（§8：滚动帧也维护被拖身份与交换）。
     * 旧写法只在 `onDrag` 里判交换：手指按住不动、列表还在滚，邻格要滚进来了才看得见，
     * 于是那一发永远补不上 ⇒ 滚出去的只有位移、顺序没动。
     * 两帧对照：邻格还不在场 ⇒ 不发；邻格滚进来了 ⇒ 同一份偏移发出来，并交出落点。
     */
    @Test
    fun `a scroll frame commits the swap the last finger event could not see`() {
        val withoutNeighbour = dragFramePlan(
            input(rows = listOf(slot(1, 146), slot(2, 190)), offsetPx = 60f, requestedScrollPx = 10f)
        )
        assertEquals("下面那一格还没滚进来 ⇒ 这一帧发不出交换：" + withoutNeighbour.reorder,
            null, withoutNeighbour.reorder)
        assertEquals("发不出交换就不许扣补偿（扣了就是凭空瞬移）", 70f, withoutNeighbour.offsetPx, 0.001f)
        assertEquals("闸门也不许被没发生的交换压上", -1, withoutNeighbour.pendingSwapFromIndex)

        val withNeighbour = dragFramePlan(
            input(rows = listOf(slot(1, 146), slot(2, 190), slot(3, 234)), offsetPx = 60f,
                requestedScrollPx = 10f)
        )
        assertEquals("邻格进来了就在这一帧发出去（展示位 2 → 3）：" + withNeighbour.reorder,
            2 to 3, withNeighbour.reorder)
        assertEquals("闸门键 = 发出那一格", 2, withNeighbour.pendingSwapFromIndex)
        assertEquals("落点 = 滚完之后相邻格之后那一槽（key 仍是被拖那一条）",
            slot(3, 224, ROW, "row2"), withNeighbour.landingSlot)
        assertEquals("补偿已扣在位移里", 26f, withNeighbour.offsetPx, 0.001f)
        assertEquals(
            "发完交换的画位必须与发之前同一处（跳的就是『拖到一半弹回去』）",
            190f + 60f, withNeighbour.landingSlot!!.offsetPx + withNeighbour.offsetPx, 0.001f
        )
    }

    /**
     * 发交换那一帧的夹持**跟着落点走**（不是交换前的那一格）。200px 短视口上两种算法差得看得见：
     * · 按落点（194）夹：上限 = 200-20-194 = -14 ⇒ 中心 194-14+20 = 200（在视口里）；
     * · 按旧槽位（150）夹（上一版）：上限 = 200-20-150 = 30 ⇒ 那帧不咬人、位移留在 2 ⇒ 中心 216（出界）。
     * 这一格就是表行里"交换后仍混用旧布局夹持"那句的反例读数。
     */
    @Test
    fun `the frame that commits a swap clamps against the slot the offset now lives in`() {
        val plan = dragFramePlan(input(rows = threeRows(150), offsetPx = 46f, start = 0, end = 200))
        assertEquals("视觉中心 216 已过邻格中心 214 ⇒ 发交换：" + plan.reorder, 2 to 3, plan.reorder)
        assertEquals("夹持按落点格算：-14（按旧槽位夹会留在 2 ⇒ 中心出界 216）", -14f, plan.offsetPx, 0.001f)
        val centre = plan.landingSlot!!.offsetPx + plan.offsetPx + ROW / 2f
        assertTrue("刚发出的那一格此刻画出来的中心不许下到视口外（实到 $centre）", centre <= 200f + 0.001f)
        assertTrue("也不许高到视口外（实到 $centre）", centre >= 0f - 0.001f)
    }

    /**
     * 相邻格是**尾部备注**那一行（`displayed` 里读不到）⇒ 不发假交换、更不许扣补偿。
     * 反例：只按 `swap != null` 就扣补偿 ⇒ 位移凭空少 44，第二句红（用户看到"没拖它自己跳一下"）。
     */
    @Test
    fun `a note footer neighbour commits no reorder and pays no compensation`() {
        val rows = listOf(slot(1, 106), slot(2, 150), slot(3, 194, ROW, "advisor_note_footer"))
        val plan = dragFramePlan(input(rows = rows, offsetPx = 46f, displayed = displayedRows(3)))
        assertEquals("备注行不是聊天行，换不得：" + plan.reorder, null, plan.reorder)
        assertEquals("没发出去的交换一分补偿都不许扣", 46f, plan.offsetPx, 0.001f)
        assertEquals("闸门也不许被这次没发生的交换压上", -1, plan.pendingSwapFromIndex)
        assertEquals("落点同理留空", null, plan.landingSlot)
    }

    // ═══════════ ③ 手指帧：速度仍按手指位置重算，位移不许多改 ═══════════

    /**
     * 手指帧（`fingerY != null`）：速度由 [edgeAutoScrollSpeedPx] 现算——滑回中央就是 0（那颗任务立刻退出），
     * 压在底边缘就是正数；这一帧自己不滚（滚动只归那颗任务）。
     * 反例：手指帧沿用上一帧速度 ⇒ 第一句红（"我离开了边缘它还在自己跑"）；
     * 反例：手指帧也去 scrollBy ⇒ 第二句红（一帧两笔滚动，速度直接翻倍）。
     */
    @Test
    fun `the finger frame recomputes the speed and never scrolls itself`() {
        val middle = dragFramePlan(input(rows = threeRows(200), offsetPx = 10f, fingerY = 300f))
        assertEquals("手指滑回中央 ⇒ 立刻归零", 0f, middle.speedPx, 0f)
        assertEquals("手指帧不许自己滚", 0, middle.scrollPx)
        assertEquals("不许夹坏跟手：中心 230 在视口里 ⇒ 位移原样", 10f, middle.offsetPx, 0.001f)

        val bottomEdge = dragFramePlan(input(rows = threeRows(200), offsetPx = 10f, fingerY = 595f))
        assertTrue("压在底边缘 ⇒ 交回正速度交给那颗任务：" + bottomEdge.speedPx, bottomEdge.speedPx > 0f)
        assertEquals("除了速度，这一帧同样不滚", 0, bottomEdge.scrollPx)
    }

    /**
     * 视口之内不许有半点改动（夹持只咬越界那一段）：整段位移扫一遍，中心已在视口内、
     * 且这一帧没发交换的那些格都必须逐字等于"旧值 + 真滚掉的量"。
     * 这是"自动滚动顺带把位移夹歪"那一族的反例哨兵。
     */
    @Test
    fun `a scroll frame never touches an offset whose centre is already inside`() {
        var checked = 0
        for (topOfDragged in listOf(44, 150, 300, 500)) {
            for (raw in -60..120 step 6) {
                val centreBefore = topOfDragged + raw + ROW / 2f
                if (centreBefore < 0f || centreBefore > VIEWPORT_END) continue
                val plan = dragFramePlan(
                    input(rows = threeRows(topOfDragged), offsetPx = raw.toFloat(), requestedScrollPx = 12f)
                )
                if (plan.reorder != null) continue
                assertEquals(
                    "中心还在视口里 ⇒ 只许加滚动补偿（槽位 $topOfDragged、位移 $raw → ${plan.offsetPx}）",
                    raw + 12f, plan.offsetPx, 0.001f
                )
                checked++
            }
        }
        assertTrue("这一趟必须真的扫到「视口之内」那一段（一次都没判 = 在空过）：$checked", checked > 50)
    }

    // ═══════════ ④ 时间线：短视口贴边一路按住 ═══════════

    /**
     * 短视口贴边滚动的**时间线**（§8 验收点名的"两条短消息的小视口 / 贴边滚动"那一档，
     * 在 JVM 上只能按生产那颗任务的循环建模，不是手感）：
     * 读最新布局 → 问 [dragFramePlan] → 滚它交回的量 → 记账（闸门键 / 落点 / 顺序）。
     * 夹具只补两件事：`scrolled` 累计滚动量，和"边界与视口不相交的那几格不在 `visibleItemsInfo` 里"
     * —— 那正是本轮缺陷的落点：旧那颗任务不看这一条，照样 scrollBy + 加偏移。
     * 判四件事（每一句都是旧形状会红的那一档）：
     * · 被拖那一条**每一帧都在** `visibleItemsInfo` 里（不许被回收）；
     * · 手指按住不动 ⇒ 画出来的中心逐帧同一个数（滚动补偿不许漂）；
     * · 一路走到列表末尾恰好让位三次（滚动帧也在维护交换，不是只滚不换）；
     * · 走到底之后预算用尽 ⇒ 这一颗**自己停**，而不是把条目推出视口继续滚。
     */
    @Test
    fun `a held edge drag keeps the dragged row visible while it walks to the end of the list`() {
        var scrolled = 0
        var offset = 0f
        var pending = -1
        var landing: DragSlot? = null
        val order = (0 until TIMELINE_ROWS).map { "row$it" }.toMutableList()
        val commits = mutableListOf<Pair<Int, Int>>()
        val centres = mutableListOf<Float>()
        var stopped = false
        var frame = 0
        while (frame < 20 && !stopped) {
            val visible = (0 until TIMELINE_ROWS).mapNotNull { k ->
                val top = k * STEP - scrolled
                if (top + ROW > 0 && top < TIMELINE_VIEWPORT_END) DragSlot(k, top, ROW, order[k]) else null
            }
            assertTrue("第 $frame 帧被拖那一条已经被回收（这就是缺陷态①）：" + visible.map { it.key },
                visible.any { it.key == DRAGGED })
            val plan = dragFramePlan(
                input(rows = visible, offsetPx = offset, pending = pending, landing = landing,
                    end = TIMELINE_VIEWPORT_END, requestedScrollPx = 24f,
                    displayed = List(TIMELINE_ROWS) { j -> DragRow(j, j, order[j]) })
            )
            offset = plan.offsetPx
            pending = plan.pendingSwapFromIndex
            landing = plan.landingSlot
            plan.reorder?.let { (from, to) ->
                commits += from to to
                order.add(to, order.removeAt(from))
            }
            scrolled += plan.scrollPx
            val draggedTop = order.indexOf(DRAGGED) * STEP - scrolled
            centres += draggedTop + offset + ROW / 2f
            assertTrue("第 $frame 帧画出来的中心出了视口（${centres.last()}）",
                centres.last() >= 0f && centres.last() <= TIMELINE_VIEWPORT_END)
            if (plan.scrollPx == 0) stopped = true
            frame++
        }
        assertTrue("这一趟必须因为滚不动而停（不停 = 还在把条目往回收边界上推）", stopped)
        assertEquals("走到末尾恰好三次相邻让位（滚动帧自己就把交换维护住了）：" + commits,
            listOf(2 to 3, 3 to 4, 4 to 5), commits)
        assertEquals("落点必须是最后一格：" + order, TIMELINE_ROWS - 1, order.indexOf(DRAGGED))
        assertEquals("手指没动 ⇒ 画出来的中心一分都不许多走：" + centres,
            centres.first(), centres.last(), 0.001f)
        assertTrue("必须真的走了好几帧（一帧就停 = 算式没被跑起来）：$frame", frame > 3)
    }

    // ═══════════ ⑤ 源码尺：两处都只经同一颗帧判据接线 ═══════════

    private val source: String by lazy {
        val f = File("src/main/java/com/lovebrain/app/ui/panel/reply/MessageList.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/reply/MessageList.kt")
        assertTrue("找不到生产源码：$f（这把尺没有对象，不许静默空过）", f.isFile)
        SourceScan.maskComments(f.readText())
    }

    private fun linesWith(needle: String): List<String> = source.lines().filter { needle in it }

    /**
     * §8 的"同一份布局"在接线那一层的读数：
     * · 帧判据只有**一颗**纯函数，UI 两处（`onDrag` 与那颗滚动任务）各调一次；
     * · 视口夹持算式只在纯函数里面（旧写法在 composable 里另读一份 `layoutInfo` 夹一次，
     *   读的就是上一帧的布局——那正是表行点名的"混用旧布局夹持"）；
     * · 滚动任务只按算式交回的量 `scrollBy`，滚不动的那一截按真滚掉的量退回来；
     * · 量不到被拖那一格时唯一的动作是把条目请回视口（`draggedVisible` 那一支）。
     * 反例：夹持调用抄回 composable ⇒ `at in planStart..planEnd` 那句红；
     * 反例：滚动任务改回 `scrollBy(speed)` ⇒ 第三组红（旧那颗任务的形状）。
     */
    @Test
    fun `both drag branches read the newest layout through one plan`() {
        val frameLines = linesWith("runDragFrame(")
        assertEquals("两处调用（手指帧 + 滚动帧），一处都不许多：" + frameLines, 2, frameLines.size)
        assertTrue("手指帧那一处必须交进手指位置、且自己不许滚：" + frameLines,
            frameLines.any { "runDragFrame(change.position.y, 0f)" in it })
        assertTrue("滚动帧那一处必须带着速度去问算式：" + frameLines,
            frameLines.any { "runDragFrame(null, edgeSpeedPx)" in it })

        val clampCalls = linesWith("clampDragOffsetInsideViewport(").filter { "internal fun" !in it }
        val planStart = source.indexOf("internal fun dragFramePlan(")
        val planEnd = source.indexOf("private fun LazyListItemInfo.asDragSlot()")
        assertTrue("读不到帧判据本体（$planStart→$planEnd），这把尺没咬住东西", planEnd > planStart + 100)
        assertEquals("夹持算式在帧判据的两条分支里各一次，实到：" + clampCalls, 2, clampCalls.size)
        clampCalls.forEachIndexed { k, line ->
            val at = source.indexOf(line)
            assertTrue("第 $k 处夹持调用必须落在帧判据里面（旧写法挂在 composable 里另读一份布局）",
                at in planStart..planEnd)
        }
        assertTrue("composable 里一次都不许直接夹：" + linesWith("clampDraggedRowIntoViewport"),
            linesWith("clampDraggedRowIntoViewport").isEmpty())

        val taskAt = source.indexOf("LaunchedEffect(dragSession")
        val task = source.substring(taskAt, (taskAt + 700).coerceAtMost(source.length))
        assertTrue("那颗任务必须按算式交回的量滚：" + task.take(320),
            "listState.scrollBy(planned.toFloat())" in task)
        assertFalse("不许再拿速度直接 scrollBy（旧那颗就是这个形状把条目推丢的）：" + task.take(320),
            "listState.scrollBy(speed)" in task || "listState.scrollBy(edgeSpeedPx)" in task)
        assertTrue("滚不动的那一截必须退回来（补偿只等于真滚掉的量）：" + task.take(320),
            "dragOffsetY += scrolled - planned" in task)

        val reshowLines = linesWith("reshowDraggedRow(id)")
        assertEquals("条目不丢的出口只有一处：" + reshowLines, 1, reshowLines.size)
        assertTrue("那一处必须挂在『量不到最新槽位』那一支上：" + reshowLines.single(),
            "if (!plan.draggedVisible" in reshowLines.single())
        assertTrue("请回视口只搬条目，不夹持不补偿：" + linesWith("scrollToItem(target)"),
            linesWith("scrollToItem(target)").single().contains("listState.scrollToItem(target)"))

        val gateWrites = linesWith("pendingSwapFromIndex = plan.pendingSwapFromIndex")
        val landingWrites = linesWith("pendingSwapLanding = plan.landingSlot")
        assertEquals("闸门键只由算式交回（实到 ${gateWrites.size} 处）", 1, gateWrites.size)
        assertEquals("落点与它成对写（同一处、同一帧的读数）", gateWrites.size, landingWrites.size)
        assertEquals("位移也只吃算式那一个数：" + linesWith("dragOffsetY = plan.offsetPx"),
            1, linesWith("dragOffsetY = plan.offsetPx").size)
        assertEquals("发重排的出口只有一处（两处分支共用那一颗）：" + linesWith("onReorder("),
            1, linesWith("onReorder(").size)
    }

    /**
     * §8 的"只修这条现有链，不另造第二套拖动架构"：滚动出口、会话任务、手势入口、身份各仍只一颗。
     * 反例：再长出一颗 LaunchedEffect / 第二处 scrollBy / 第二处手势 ⇒ 逐条红。
     */
    @Test
    fun `the fix stays on the existing drag chain`() {
        assertEquals("滚动出口全文件仍只一处：" + linesWith("scrollBy("), 1, linesWith("scrollBy(").size)
        assertEquals("自动滚动仍只那颗带会话键的任务：" + linesWith("LaunchedEffect(dragSession"),
            1, linesWith("LaunchedEffect(dragSession").size)
        assertEquals("拖拽手势入口仍只一处：" + linesWith("detectDragGesturesAfterLongPress("),
            1, linesWith("detectDragGesturesAfterLongPress(").size)
        assertTrue("被拖身份仍只认稳定 id：" + linesWith("draggedId = hitId"),
            source.contains("draggedId = hitId"))
        assertTrue("边缘档仍按视口限流（短视口重叠那一档不许退回去）",
            source.contains("minOf(edgeZonePx, viewportHeight / 3f)"))
    }
}

// ═══════════ 夹具档位（纯函数入参，不是生产常量）═══════════

/** 行高与步长（40 + 4 间距），与 `MessageListDragOrderTest` 同一档 */
private const val ROW = 40
private const val STEP = 44

/** 夹具视口 0..600 与"被拖那一格至少还留在视口里"的那条线 */
private const val VIEWPORT_END = 600
private const val KEEP = 20

/** 时间线那一格的档位：六行、视口 600、被拖的是第三行（下标 2） */
private const val TIMELINE_ROWS = 6
private const val TIMELINE_VIEWPORT_END = 600
private const val DRAGGED = "row2"
