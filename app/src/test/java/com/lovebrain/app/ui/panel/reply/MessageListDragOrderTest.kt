package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.domain.prompt.ChatTranscriptBlock
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 原话第 19 条（优化版第 10 条）的后半：**拖完的最终顺序 = 喂给模型的 prompt 顺序**；
 * 外加"指下那条不许飞出屏幕"的两颗算式（视口夹持 + 同一帧不许重复发重排）。
 *
 * 全 JVM、不碰 Robolectric：这里判的是**读数同源**与**算式**，不是手感。
 *
 * ① **顺序这一条本轮没发现分叉，这一族钉的是"以后不许分叉"**（台账同一条）：
 *    屏上那份列表 = `messages.mapIndexed{}.filter { role != IDEA }`（[MessageList] 里的
 *    `dialogueDisplayed`，不排序），prompt 那份 = `ComposerStore.messageSnapshot()` →
 *    `realChatOf` 同样的 filter + `mapIndexed`，再由 [ChatTranscriptBlock.render] 在同一次遍历里
 *    按出现顺序编号 m0…；两边读的都是 `_messages` 这**一颗** StateFlow。所以两件事一起做：
 *    · 真把 [ComposerStore] 与 [ChatTranscriptBlock] 拉进来跑（不是照抄一份算式自证）——
 *      3 条与 20 条各一格：**发出去的重排对**打进 store 之后，`<chat>` 里 JSON 行的先后
 *      必须与那份列表的先后逐位相等；
 *    · 扫源码钉住"链条上谁都不许再排一次"（`sortedBy / sortBy / sorted() / reversed() / shuffled`
 *      在 MessageList、ChatTranscriptBlock、ComposerStore 三颗文件里出现 0 次；0 次是本轮实测读数）。
 *      以后任何一方加一句"按 timestamp 再排一次"，这一格先红——那正是分叉的形状。
 * ② 同一帧里对**同一对下标**发两次重排 = 把那一步移动整个抵消（落库那一步是"摘下再插回"）：
 *    [dragSwapIsSettled] 这道闸门存在的就是这一件事。危险本身用一格量出来（算式，不是主张），
 *    闸门用一格时间线钉住（**故意让布局慢一帧**，判发出的重排对序列与"布局不慢"时逐字相同）。
 * ③ [clampDragOffsetInsideViewport] 是"不许飞出"那颗算式本体：逐值钉 + 一整段位移扫不变式 +
 *    一条"夹了仍然进得去首格与末格"（夹持与交换判据共用同一颗中心线，夹错的写法会把首格夹死）。
 *
 * ⚠ 这一族**证明不了手感**：跟不跟手、指下到底看不看得见，JVM 判不了；
 *    语义树那一半在 `MessageListDragContainmentTest`，真机录屏归台账"未验证-需真机"。
 */
class MessageListDragOrderTest {

    // ═══════════ 夹具：读生产源码与生产链条上的真件 ═══════════

    private fun maskedSourceOf(relativePath: String): String {
        val f = File("src/main/java/com/lovebrain/app/$relativePath")
            .takeIf { it.isFile } ?: File("app/src/main/java/com/lovebrain/app/$relativePath")
        assertTrue("找不到生产源码：$f（这把尺没有对象，不许静默空过）", f.isFile)
        return SourceScan.maskComments(f.readText())
    }

    private val messageListSource: String by lazy { maskedSourceOf("ui/panel/reply/MessageList.kt") }

    /** 只判先后，不判文案：`<chat>` 里每行一个 `"id":"mK"`，按出现顺序换回真实消息 id */
    private fun promptOrderOf(rendered: ChatTranscriptBlock.Rendered): List<String> {
        val aliasesInBody = Regex("\"id\":\"(m\\d+)\"")
            .findAll(rendered.body)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("一行都没 parse 出来，这把尺没咬住 prompt：" + rendered.body, aliasesInBody.isNotEmpty())
        assertEquals(
            "围栏里的行数必须和别名表一样多（少一行就是有一条根本没发给模型）：" + aliasesInBody,
            rendered.sourceAliasMap.size, aliasesInBody.size
        )
        return aliasesInBody.map { rendered.sourceAliasMap.getValue(it) }
    }

    /** 新 store：只给 scope，其余持久化回调一律留默认（重排链不碰盘） */
    private fun newStore(): ComposerStore = ComposerStore(scope = CoroutineScope(Job()))

    /** 往 store 里塞 n 条真实对话（HER/ME 交替），交回**落库顺序**的 id 表 */
    private fun fill(store: ComposerStore, n: Int): List<String> {
        repeat(n) { k ->
            store.accept(
                ComposerStore.Intent.AddMessage(
                    role = if (k % 2 == 0) ChatMessage.Role.HER else ChatMessage.Role.ME,
                    content = "row$k"
                )
            )
        }
        return store.messagesNow.map { it.id }
    }

    /** 落库那一步（`ComposerStore.reorderMessages` 的唯一公开入口 = 投意图，不抄它的实现） */
    private fun moveIn(store: ComposerStore, from: Int, to: Int) {
        store.accept(ComposerStore.Intent.ReorderMessages(from, to))
    }

    private fun moveIn(list: List<Int>, from: Int, to: Int): List<Int> {
        val out = list.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }

    // ═══════════ ① 落库顺序 = prompt 顺序（3 条与 20 条） ═══════════

    /**
     * 3 条：把第一条一路拖到底，两次交换发出 (0,1)、(1,2)，落库应是 [B,C,A]，
     * `<chat>` 里的先后必须逐位相等。
     * 反例：屏上发的是展示位而落库读的是原列表下标却算错一档 ⇒ 落库顺序红在第一句；
     * 反例：prompt 侧自己倒序/按时间排 ⇒ 第二句红。
     */
    @Test
    fun `a three row drag lands the store order and the prompt order in the same sequence`() {
        val store = newStore()
        val ids = fill(store, 3)
        assertEquals("三条都该落库：" + ids, 3, ids.size)

        val run = simulateDrag(rows = 3, dragged = 0, deltas = List(20) { 5f }, eventsPerFrame = 1)
        assertEquals("从第 0 格拖到底只该发两次重排：" + run.commits, listOf(0 to 1, 1 to 2), run.commits)
        run.commits.forEach { (from, to) -> moveIn(store, from, to) }

        val stored = store.messagesNow.map { it.id }
        assertEquals("落库顺序该是被拖那条垫底：" + stored, listOf(ids[1], ids[2], ids[0]), stored)
        assertEquals(
            "时间线判的落库结果必须与真 store 落出来的逐字同一个数（否则夹具是自造的）",
            run.finalOrder.map { ids[it] }, stored
        )
        assertEquals(
            "喂给模型的对话记录先后必须与落库顺序逐位相等（原话第 19 条后半）",
            stored,
            promptOrderOf(ChatTranscriptBlock.render(store.messageSnapshot()))
        )
    }

    /**
     * 20 条：中间那条（第 10 句）拖到最上面，落库应是 [10,0,1,…,9,11,…,19]，prompt 读回同一个数。
     * 这一格同时把"预算掐尾"排除在外：20 条远低于 `AppConfig.REPLY_MAX_MESSAGES`，
     * 于是"两边同序"不可能靠"都被截成同一小截"蒙出来（真被截断时 prompt 只剩尾段、
     * 与屏上顺序不等长，这一格当场红——那正是要的红）。
     */
    @Test
    fun `a twenty row drag keeps store order and prompt order identical item by item`() {
        val store = newStore()
        val ids = fill(store, 20)
        val run = simulateDrag(rows = 20, dragged = 10, deltas = List(140) { -5f }, eventsPerFrame = 1)
        val expectedCommits = (10 downTo 1).map { it to it - 1 }
        assertEquals("第 10 格拖到顶恰好十次交换，且一次都不许多：" + run.commits, expectedCommits, run.commits)
        run.commits.forEach { (from, to) -> moveIn(store, from, to) }

        val stored = store.messagesNow.map { it.id }
        val expectedPositions = listOf(10) + (0..9).toList() + (11..19).toList()
        assertEquals(
            "落库顺序应是『第 10 句到顶、其余顺次后移』：" + stored.map { ids.indexOf(it) },
            expectedPositions, stored.map { ids.indexOf(it) }
        )
        assertEquals(
            "20 条这一档，时间线判的落库结果必须与真 store 逐字同一个数",
            run.finalOrder.map { ids[it] }, stored
        )
        assertEquals(
            "20 条这一档，prompt 里的先后必须与落库顺序逐位相等",
            stored,
            promptOrderOf(ChatTranscriptBlock.render(store.messageSnapshot()))
        )
    }

    /**
     * 同一份列表派生的 prompt 里不许长出"第三种人"：旧数据残留的 `Role.IDEA` 行
     * 既不进屏上的聊天顺序（`dialogueDisplayed` 那一步 filter），也不进 `<chat>` 围栏
     * （[ChatTranscriptBlock.render] 那一步 filter），正文由尾部那行灰字交回。
     * 反例：任何一方不 filter ⇒ IDEA 进 prompt ⇒ 别名表与屏上行数对不上，红；
     * 反例：任何一方排序 ⇒ 上面 `no station…` 那一格先红。
     */
    @Test
    fun `a leftover idea row is out of both the chat order and the prompt transcript`() {
        val legacy = listOf(
            ChatMessage(id = "a", role = ChatMessage.Role.HER, content = "row a"),
            ChatMessage(id = "i", role = ChatMessage.Role.IDEA, content = "note i"),
            ChatMessage(id = "b", role = ChatMessage.Role.ME, content = "row b")
        )
        val rendered = ChatTranscriptBlock.render(legacy)
        assertEquals("IDEA 不许进对话记录：" + rendered.sourceAliasMap.values,
            listOf("a", "b"), promptOrderOf(rendered))
        // 与屏上那一步派生同序（mapIndexed + filter 的等价写法，只判先后）
        val displayedOrder = legacy.mapIndexed { index, msg -> index to msg }
            .filter { it.second.role != ChatMessage.Role.IDEA }
            .map { it.second.id }
        assertEquals("屏上顺序与 prompt 顺序必须同一个数：", displayedOrder, promptOrderOf(rendered))
    }

    // ═══════════ ② 同一帧不许发第二次：那一发会把移动整个抵消 ═══════════

    /**
     * **危险本身的量读**（算出来的，不是主张）：对同一对下标发两次"摘下再插回"，列表回到原点。
     * 手指已经把那条拖下去、落库却回原样 ⇒ 屏上顺序 ≠ 落库顺序 ≠ prompt 顺序。
     * 这一格是 [dragSwapIsSettled] 存在的理由；闸门撤掉之后，下面那格时间线会红。
     */
    @Test
    fun `committing the same reorder pair twice cancels the move out`() {
        val start = listOf(0, 1, 2)
        val once = moveIn(start, 0, 1)
        assertEquals("一次交换就该把那条挪下去：" + once, listOf(1, 0, 2), once)
        assertEquals(
            "同一对下标发两次 = 整个移动被抵消（这就是必须挡住重复那一发的原因）",
            start, moveIn(once, 0, 1)
        )
    }

    /** 闸门真值表：没有待落地的 = 能发；布局还停在发出时那一格 = 不能发；挪过 = 已落地。 */
    @Test
    fun `the settle gate only blocks the frame where the layout has not caught up`() {
        assertTrue("没有待落地的交换时当然能发",
            dragSwapIsSettled(draggedLayoutIndex = 3, pendingSwapFromIndex = -1))
        assertFalse("布局还停在发出那一格 = 没追上，这一帧不许再发",
            dragSwapIsSettled(draggedLayoutIndex = 2, pendingSwapFromIndex = 2))
        assertTrue("行已经站到目标格 = 列表追上了",
            dragSwapIsSettled(draggedLayoutIndex = 3, pendingSwapFromIndex = 2))
        assertTrue("行被别处的增删挪去别处也算追上（闸门不许把拖拽锁死）",
            dragSwapIsSettled(draggedLayoutIndex = 0, pendingSwapFromIndex = 2))
    }

    /**
     * 时间线：按生产 `onDrag` 的记账顺序跑（累计手指位移 → 问闸门 → 判交换 → 发重排 + 扣补偿 → 夹视口）。
     * 与 `MessageRowDragFollowTest.simulate` 的**唯一**区别是这里故意让布局慢 [eventsPerFrame] 记事件，
     * 于是同一帧里会来第二、第三记事件。
     *
     * 判据：布局慢不慢，**发出的重排对序列必须逐字一样**（挡住重复那一发），
     * 而且每帧画出来的位置仍等于"起点 + 手指走过的总量"（跟手不许被闸门弄坏）。
     * 反例：闸门撤掉 ⇒ 出现 `(0,1),(0,1)` 这种重复对，第一句红，且落库顺序跟着红；
     * 反例：闸门把补偿也算错（不发也扣） ⇒ 位置那一句当场红。
     */
    @Test
    fun `a stale layout frame must not change which reorders get committed`() {
        val deltas = List(30) { 5f }
        val steady = simulateDrag(rows = 4, dragged = 0, deltas = deltas, eventsPerFrame = 1)
        val stale = simulateDrag(rows = 4, dragged = 0, deltas = deltas, eventsPerFrame = 2)
        assertEquals(
            "布局慢一帧不许多出一发重排：" + steady.commits + " vs " + stale.commits,
            steady.commits, stale.commits
        )
        assertEquals("四行拖到底恰好三次交换：" + stale.commits, 3, stale.commits.size)
        assertEquals("两趟落出来的顺序必须同一个数：", steady.finalOrder, stale.finalOrder)
        stale.commits.forEachIndexed { k, (from, to) ->
            assertEquals("第 $k 次必须是相邻让位（不是隔格跳）：" + stale.commits, to - from, 1)
        }
        // 跟手那一半：每一帧画出来的顶边 = 手指走过的总量（视口够高，夹持不参与）
        stale.paintedAtFrameEnd.forEachIndexed { k, painted ->
            val walked = deltas.take((k + 1) * 2).sum()
            assertEquals("第 $k 帧的画位该等于手指走过的总量（实到 $painted，应为 $walked）",
                walked, painted, 0.001f)
        }
    }

    // ═══════════ ③ 视口夹持：画出来的那条不许离开视口，也仍要够得到首末格 ═══════════

    /**
     * 逐值钉 [clampDragOffsetInsideViewport]（视口 0..600、行高 40 ⇒ 中心上下限各留半条）。
     * 反例（各挡一种坏法）：
     * · 忘了夹 ⇒ 越界那两格红（手指划出列表，行跟着飞出屏幕 = 原话前半）；
     * · 夹成"整条不许出视口" ⇒ 顶边最多到视口上沿，中心永远上不到首行中心线以上，
     *   首格再也进不去（下面 `a clamped drag can still reach…` 那一格红）；这里读数是 -108 不是 -88；
     * · 端点算反 ⇒ `coerceIn` 拿 min>max 当场抛，整格红；
     * · 没量到就凭空造位置 ⇒ 最后三格红（第一帧自己跳）。
     */
    @Test
    fun `the dragged row is clamped by its centre but follows the finger inside the viewport`() {
        // 视口之内：原样交回（跟手不许被夹坏）
        assertEquals("槽位 88、位移 0：画在 88，中心 108 在视口内 ⇒ 不许动",
            0f, clampDragOffsetInsideViewport(0f, 88, 40, 0, 600), 0.001f)
        assertEquals(37f, clampDragOffsetInsideViewport(37f, 88, 40, 0, 600), 0.001f)
        // 中心顶到视口上沿 = 下限（0 - 20 - 88 = -108）；再往上的手指量一律吃掉
        assertEquals(-108f, clampDragOffsetInsideViewport(-500f, 88, 40, 0, 600), 0.001f)
        // 中心贴到视口下沿 = 上限（600 - 20 - 88 = 492）
        assertEquals(492f, clampDragOffsetInsideViewport(900f, 88, 40, 0, 600), 0.001f)
        // 行比视口高（大字体 + 展开的长消息）：中心照样留在视口里，仍能上下拖
        assertEquals(-345f, clampDragOffsetInsideViewport(-900f, 0, 690, 0, 600), 0.001f)
        assertEquals(255f, clampDragOffsetInsideViewport(300f, 0, 690, 0, 600), 0.001f)
        // 没量到：原样交回，不许凭空造一个位置
        assertEquals(900f, clampDragOffsetInsideViewport(900f, 88, 0, 0, 600), 0.001f)
        assertEquals(900f, clampDragOffsetInsideViewport(900f, 88, 40, 600, 600), 0.001f)
        assertEquals(900f, clampDragOffsetInsideViewport(900f, 88, 40, 600, 0), 0.001f)
    }

    /**
     * 不变式扫一遍（-4000..4000 位移 × 四档槽位）：**画出来的中心必须留在视口里**，
     * 且中心本来就落在视口内的那一段必须逐字不动（跟手）。
     * 反例：只夹一侧 ⇒ 另一侧那一半红；
     * 反例：夹持顺带改了视口内的值 ⇒ "视口之内不许动"那一段红，`untouched` 那句哨兵也跟着红。
     */
    @Test
    fun `every offset in a full sweep paints the row centre inside the viewport`() {
        val viewportStart = 0
        val viewportEnd = 600
        val rowHeight = 40
        var untouched = 0
        var clamped = 0
        for (layoutOffset in listOf(0, 44, 88, 300)) {
            for (rawMillis in -4000..4000 step 7) {
                val raw = rawMillis.toFloat()
                val clampedValue = clampDragOffsetInsideViewport(raw, layoutOffset, rowHeight, viewportStart, viewportEnd)
                val centre = layoutOffset + clampedValue + rowHeight / 2f
                assertTrue("画出来的中心不许上到视口外（槽位 $layoutOffset、位移 $raw → 中心 $centre）",
                    centre >= viewportStart.toFloat() - 0.001f)
                assertTrue("画出来的中心不许下到视口外（槽位 $layoutOffset、位移 $raw → 中心 $centre）",
                    centre <= viewportEnd.toFloat() + 0.001f)
                val rawCentre = layoutOffset + raw + rowHeight / 2f
                if (rawCentre in viewportStart.toFloat()..viewportEnd.toFloat()) {
                    assertEquals("中心在视口之内原样跟手，不许有半点改动", raw, clampedValue, 0.001f)
                    untouched++
                } else {
                    assertTrue(
                        "中心出视口那一段必须被夹回来（原样交回就是根本没夹住）：位移 $raw → $clampedValue",
                        clampedValue != raw
                    )
                    clamped++
                }
            }
        }
        assertTrue("这一趟必须真的扫到视口内那一段（一次都没原样交回 = 夹持在乱改）：$untouched",
            untouched > 100)
        assertTrue("也必须真的扫到出界那一段（一次都没夹 = 这一格在空过）：$clamped", clamped > 100)
    }

    /**
     * 夹持与交换判据共用同一颗中心线，所以"夹"与"够得到两端"必须同时成立：
     * 八行、视口正好装得下八行，从中间一路夹到顶、又一路夹到底，
     * 交换次数必须等于行数差（**不许因为夹持而卡在中间**）。
     * 反例：夹成整条不许出视口 ⇒ 上行只走到第 1 格就再也进不了首格，第一句红；
     * 反例：夹持上限太松 ⇒ 上面那两格红，这一格反而红在"中心出界"没有证人。
     */
    @Test
    fun `a clamped drag can still reach the first and the last slot`() {
        val rows = 8
        val viewport = rows * 44
        val up = simulateDrag(rows = rows, dragged = 4, deltas = List(200) { -5f },
            eventsPerFrame = 1, viewportEnd = viewport)
        assertEquals("从第 4 格往上只该换四次：" + up.commits,
            listOf(4 to 3, 3 to 2, 2 to 1, 1 to 0), up.commits)
        assertEquals("落点必须是第一格：" + up.finalOrder, 0, up.finalOrder.indexOf(4))

        val down = simulateDrag(rows = rows, dragged = 3, deltas = List(200) { 5f },
            eventsPerFrame = 1, viewportEnd = viewport)
        assertEquals("从第 3 格往下只该换四次：" + down.commits,
            listOf(3 to 4, 4 to 5, 5 to 6, 6 to 7), down.commits)
        assertEquals("落点必须是最后一格：" + down.finalOrder, rows - 1, down.finalOrder.indexOf(3))
    }

    // ═══════════ ④ 源码尺：链条上谁都不许再排一次、夹持与闸门各在其位 ═══════════

    /**
     * 顺序只由"列表自己的先后"决定：重排链、快照、prompt 渲染三颗文件里
     * 一次排序/倒序都不许出现（本轮实测 0 次）。这一格买的是"以后不许长出第一次"。
     * 反例：屏侧加 `sortedBy { it.timestamp }` ⇒ 屏幕顺序与落库顺序分叉，红；
     * 反例：prompt 侧加 `reversed()` ⇒ 喂模型的顺序不是用户拖出来的，红。
     */
    @Test
    fun `no station on the order chain is allowed to re sort the list`() {
        val needles = listOf("sortedBy", "sortBy", "sorted()", "reversed()", "shuffled")
        val files = listOf(
            "ui/panel/reply/MessageList.kt" to messageListSource,
            "domain/prompt/ChatTranscriptBlock.kt" to maskedSourceOf("domain/prompt/ChatTranscriptBlock.kt"),
            "feature/composer/ComposerStore.kt" to maskedSourceOf("feature/composer/ComposerStore.kt")
        )
        files.forEach { (name, src) ->
            needles.forEach { needle ->
                assertEquals(
                    "$name 里出现了重新排序的写法「$needle」（实到 ${src.split(needle).size - 1} 次）：" +
                        "聊天顺序的唯一真源是列表先后，谁都不许再排一次",
                    0, src.split(needle).size - 1
                )
            }
        }
    }

    /**
     * 两处写 `dragOffsetY` 的分支里，**只有手指那一半夹视口**：
     * 边缘滚动那一半靠"补偿 = 滚动量"把画位钉在原地，而 `layoutInfo` 要到下一次布局才反映
     * 刚滚掉的那一截，在它里面夹会拿旧槽位算新边界、每帧漂一格（这是另一种"不跟手"）。
     * 反例：夹持从长按那一支摘掉 ⇒ 第二句红（手指划出列表就是飞出屏幕）；
     * 反例：在自动滚动那颗里也夹 ⇒ 第三句红；
     * 反例：夹持的调用写了两处 ⇒ 第一句红（同一件事两本账）。
     */
    @Test
    fun `the viewport clamp sits on the finger branch and nowhere else`() {
        val clampCalls = messageListSource.lines()
            .filter { "clampDragOffsetInsideViewport(" in it && "internal fun" !in it }
        assertEquals("夹持算式的调用只该有一处，实到：" + clampCalls, 1, clampCalls.size)

        val helperAt = messageListSource.indexOf("clampDraggedRowIntoViewport()")
        assertTrue("读不到夹持那一颗 helper 的调用（这一族已经改形状，这把尺要重写）", helperAt >= 0)
        val helperCalls = messageListSource.split("clampDraggedRowIntoViewport()").size - 1
        assertEquals("夹持那颗 helper 的调用只许一处（实到 $helperCalls 处）", 1, helperCalls)
        assertTrue(
            "那一处必须落在长按起势之后的手指事件里（onDrag 与 onDragEnd 之间）：" +
                "onDrag 在 ${messageListSource.lastIndexOf("onDrag =")}、" +
                "onDragEnd 在 ${messageListSource.indexOf("onDragEnd =")}",
            helperAt > messageListSource.lastIndexOf("onDrag =") &&
                helperAt < messageListSource.indexOf("onDragEnd =")
        )

        val taskAt = messageListSource.indexOf("LaunchedEffect(dragSession")
        val task = messageListSource.substring(taskAt, (taskAt + 600).coerceAtMost(messageListSource.length))
        assertTrue("边缘滚动那一颗必须补滚动量（不补就被拖行脱离手指）：" + task.take(240),
            "dragOffsetY += scrolled" in task)
        assertFalse("但那一颗不许自己夹视口（拿旧槽位算新边界 = 每帧漂一格）：" + task.take(240),
            "clampDraggedRowIntoViewport()" in task)
    }

    /**
     * 发重排那一支必须带着"落地闸门"，而且闸门键与发出的那一发**同时**写：
     * 反例：闸门摘掉 ⇒ 上面时间线那一格先红，这一格再把"闸门只在纯函数那一侧"钉住；
     * 反例：闸门写成 UI 里的手抄算式 ⇒ 第二句红（判据没法逐值钉）；
     * 反例：发了重排却不置闸门键（或置成别的下标）⇒ 第三句红（重复那一发又没人挡）。
     */
    @Test
    fun `the reorder commit runs through the settle predicate`() {
        val gateLines = messageListSource.lines()
            .filter { "dragSwapIsSettled(" in it && "internal fun" !in it }
        assertEquals("闸门调用只该有一处（长按那一支），实到：" + gateLines, 1, gateLines.size)
        assertTrue("闸门本体必须是纯函数（这样它才能被逐值钉，而不是只能对着手势猜）",
            messageListSource.contains("internal fun dragSwapIsSettled("))

        val onReorderAt = messageListSource.indexOf("onReorder(fromPair.first, toPair.first)")
        assertTrue("读不到发重排那一句（这一族已经改形状，这把尺要重写）", onReorderAt >= 0)
        val after = messageListSource.substring(onReorderAt,
            (onReorderAt + 400).coerceAtMost(messageListSource.length))
        assertTrue("发出交换的同一段里必须同时把闸门键置上（不置就是没人挡重复那一发）：" + after.take(200),
            "pendingSwapFromIndex = slot.index" in after)
        assertTrue("同一段也要扣交换产生的布局偏移（扣了才不瞬移）：" + after.take(200),
            "dragOffsetY += swap.offsetCompensationPx" in after)
        assertTrue("闸门参与发送条件：" + messageListSource.lines().filter { "pendingSwapFromIndex < 0" in it },
            messageListSource.contains("pendingSwapFromIndex < 0"))
    }

    /**
     * 浮起那一档（`zIndex`）必须挂在**条目根节点**上才管得住"压住哪一条"：挂在行内那颗子节点上
     * 只在它自己内部排序，兄弟条目各画各的，被拖那一条会被后面那些不透明气泡整个盖住
     * ——那正是"手指底下那条看不见"的约束链成因（本仓库记过同类坑：子节点的判据赢不过父那一级）。
     * 反例：把 zIndex 抄回 BoxWithConstraints 那一串 ⇒ "条目根节点吃到 modifier"红、
     *      且"行内那颗不再夹一份"红；
     * 反例：两处都挂 ⇒ 计数红（同一件事两本账）。
     * ⚠ 读 AnimatedVisibility 那一颗**按括号配对取实参**，不按"往后 N 个字符"取：
     *   那颗调用的实参里压着好几行长注释（掩成空格后长度不变），定长窗口会在注释里就断掉，
     *   于是"modifier 到底吃没吃到"读的是注释而不是代码（W6 那一格红的就是这一档）。
     */
    @Test
    fun `the dragged row lifts at the item root not inside the row`() {
        val liftLines = messageListSource.lines().filter { "zIndex(" in it }
        assertEquals("浮起只该有一处出口，实到：" + liftLines, 1, liftLines.size)
        assertTrue("那一处必须与『谁被手指控制』同档：" + liftLines.single(), "isDragged" in liftLines.single())

        val open = messageListSource.indexOf("itemsIndexed(") + "itemsIndexed".length - 1
        val end = SourceScan.callEnd(messageListSource, open)
        assertTrue("读不到 items 的子树（下标 $open→$end），这把尺没咬住东西", end > open + 20)
        val insideItems = messageListSource.substring(open, end)
        assertTrue("条目级那一颗必须交进 MessageRow 的 modifier：" + insideItems.take(240),
            "modifier = if (isDragged) Modifier.zIndex" in insideItems)

        val visibilityAt = messageListSource.indexOf("AnimatedVisibility(")
        assertTrue("读不到 MessageRow 那处 AnimatedVisibility", visibilityAt >= 0)
        val visibilityOpen = visibilityAt + "AnimatedVisibility".length
        val visibilityClose = SourceScan.closeIndexOf(messageListSource, visibilityOpen)
        assertTrue(
            "AnimatedVisibility 的实参括号没配上（开 $visibilityOpen → 闭 $visibilityClose），这把尺没咬住东西",
            visibilityClose > visibilityOpen + 10 && messageListSource[visibilityClose - 1] == ')'
        )
        val visibilityArgs = messageListSource.substring(visibilityOpen + 1, visibilityClose - 1)
        assertTrue("条目根节点（AnimatedVisibility）必须吃到那颗 modifier：" + visibilityArgs.take(120),
            "modifier = modifier" in visibilityArgs)
        val rowBoxAt = messageListSource.indexOf("BoxWithConstraints(")
        val rowBox = messageListSource.substring(rowBoxAt, (rowBoxAt + 400).coerceAtMost(messageListSource.length))
        assertTrue("行内那颗不许再挂一份条目级修饰符：" + rowBox.take(200), ".then(modifier)" !in rowBox)
        assertTrue("行内那颗也不许再挂一份 zIndex：" + rowBox.take(200), "zIndex(" !in rowBox)
    }

    // ═══════════ 时间线夹具（只按生产 onDrag 那几步记账） ═══════════

    private data class DragRun(
        val commits: List<Pair<Int, Int>>,
        val paintedAtFrameEnd: List<Float>,
        val finalOrder: List<Int>
    )

    /**
     * 按生产 `onDrag` 的顺序跑一遍：累计手指位移 → 问闸门 → 判交换 → 发重排 + 扣补偿 → 夹视口。
     *
     * 与生产唯一的**简化**：行高与步长取等档（40 + 4 间距），槽位偏移按 `index * step` 给
     * （视口顶 = 第 0 格），于是"列表里滚动的槽位"这一维不参与，判的只有交换/闸门/夹持三件事；
     * 交换与补偿**全部**由 [dragSwapStep] 交回，夹持全部由 [clampDragOffsetInsideViewport] 交回，
     * 夹具一处判断都不写。
     * [eventsPerFrame] 是这里多出来的那一件事：布局（= `visibleItemsInfo` 与 `currentDialogue`
     * 读到的那一份）每这么多记事件才追上落库一次，用来造"同一帧里来好几记移动"。
     */
    private fun simulateDrag(
        rows: Int,
        dragged: Int,
        deltas: List<Float>,
        eventsPerFrame: Int,
        step: Int = 44,
        rowHeight: Int = 40,
        viewportEnd: Int = 1000
    ): DragRun {
        val store = (0 until rows).toMutableList()   // 落库顺序（意图投进去就立即生效）
        var layout = store.toList()                  // 布局/组合读到的那一份（会慢一帧）
        var accumulated = 0f
        var pending = -1
        val commits = mutableListOf<Pair<Int, Int>>()
        val painted = mutableListOf<Float>()
        deltas.forEachIndexed { k, delta ->
            accumulated += delta
            val layoutIndex = layout.indexOf(dragged)
            if (dragSwapIsSettled(layoutIndex, pending)) pending = -1
            val swap = dragSwapStep(
                dragged = DragSlot(layoutIndex, layoutIndex * step, rowHeight),
                offsetPx = accumulated,
                above = if (layoutIndex > 0) DragSlot(layoutIndex - 1, (layoutIndex - 1) * step, rowHeight) else null,
                below = if (layoutIndex < rows - 1) {
                    DragSlot(layoutIndex + 1, (layoutIndex + 1) * step, rowHeight)
                } else {
                    null
                }
            )
            if (swap != null && pending < 0) {
                commits += layoutIndex to swap.newIndex
                store.add(swap.newIndex, store.removeAt(layoutIndex))
                pending = layoutIndex
                accumulated += swap.offsetCompensationPx
            }
            accumulated = clampDragOffsetInsideViewport(
                accumulated, layoutIndex * step, rowHeight, 0, viewportEnd
            )
            if (k % eventsPerFrame == eventsPerFrame - 1) {
                layout = store.toList()
                painted += layout.indexOf(dragged) * step + accumulated
            }
        }
        return DragRun(commits, painted, store.toList())
    }
}
