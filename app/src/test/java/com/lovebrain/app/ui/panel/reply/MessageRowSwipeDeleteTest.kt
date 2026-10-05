package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.feature.composer.MessageListEditing
import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 消息行的删除：横滑**过阈值**才删那一条、不到阈值什么都不删、屏幕上不再放叉号之后
 * 删除仍要有**读屏**那一条出口；删掉"正在编辑那一条"时交出去的必须是它自己的稳定 id。
 *
 * 这里只判 JVM 上几何与语义能判定的事：
 * · 阈值那颗纯函数（`shouldDeleteBySwipe`）逐值钉死，**线的两侧各钉一格**，而且钉到"正好压线"
 *   那一格（`>=` 写成 `>` 会当场红）；行宽没量到 / 为 0 / 为负一律不删；左右两个方向同一条线；
 * · 滑过阈值的删除落点（左、右各一条，且删的是被滑那一条而不是邻居）；
 * · **快速小幅横滑**（越过 slop、没越过阈值）一条都不删——这一格走真实注入路径，
 *   并且同一格里再补一记过阈值的滑：先"擦不误删"、后"真删得掉"，两半合起来才证明那两记
 *   手势打的是同一个对象（只判前一半的话，"手势根本没落到行上"也能绿，那是恒真）；
 * · 屏幕上没有叉号、但自定义删除动作真实存在且**真能删**（动作自己必须回报 handled=true，
 *   删除必须只落这一条，且不许顺手把这条选进编辑位）；
 * · 纵向浏览（swipeUp）不落在删除的轴上，因此一条都不删；
 * · 尾部那行军师备注**不再是删除对象**，也不再是"擦一下就打开编辑器"的点击落点
 *   （ 之后它只有一个出口 = 真点它才去编辑）；
 * · 删除正在编辑那一条时，编辑指向由 `MessageListEditing.reindex` 这一份口径修正：
 *   收到的 id 不在列表里 ⇒ 编辑位 **-1**，绝不落到顶上来的那一条（草稿跟着清的那一半在
 *   `ComposerStore`，本文件只判"UI 交出去的那一颗键必须是 id"这前半）。
 *
 * ── 关于读屏那一条为什么带一份"诊断读数" ────────────────────────────────
 * 上一版这一格红在最后一句（动作存在、调用它、删除没发生），而红的那一句读不出红在哪一侧：
 * 可能是**判据读错了树**（行是 `semantics(mergeDescendants = true)` 的合并根，合并过程把
 * `customActions` 换成了别的一份 ⇒ 点到的是空壳），也可能是**生产没挂到位**。
 * 现在两件事分开判：① 动作回报的布尔值（生产那一句是 `true`；拿到 `false` 就说明调用没落到
 * 生产的动作体上）；② 删除落点。失败消息里额外带一份**未合并树**的实到标签集与"是不是同一颗"
 * 的读数（这一份只是读数，不参与红绿，读不到也不许影响判据）：红在① ⇒ 换树读；
 * 红在②而①已经 true ⇒ 生产的读屏出口真没接到删除，改 `MessageList.kt`。
 *
 * ── 关于阈值注入路径的旧记录 ────────────────────────────────────────────
 * 旧版这里写着"未达阈值那条注入路径在本机判不稳，所以只由纯函数逐值判"。这一轮把它补上了：
 * 不稳的是"短滑 + 靠默认 `swipeLeft()` 收行程"这种写法（默认那记会滑过整行宽，本来就过阈值）。
 * 现在这一格自己按 `center` 起按、按已知像素走、抬指，行程与阈值的关系是**算出来的**，
 * 不靠仪器默认值；同一格的后半（过阈值必须删掉）保证这两记手势确实打到了这一行。
 * 真正的手指跟手感、以及 360dp 窄面板上"擦一下会不会误删"的实际体感仍归真机验收。
 *
 * ⚠ 纯函数那两格刻意挑 **1000 / 250 这一类行宽**：`宽 × 0.4f` 在 float 上正好落在整百
 * （1000f×0.4f=400f、250f×0.4f=100f，舍入误差不见），于是"压线那一格"判的是 `>=` 本身，
 * 不是浮点噪声。换成 300 这种行宽，阈值会算成 120.000002f，压线格就退化成"差一点点"格。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageRowSwipeDeleteTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val her = ChatMessage(role = ChatMessage.Role.HER, content = "在吗")
    private val me = ChatMessage(role = ChatMessage.Role.ME, content = "在的")
    private val third = ChatMessage(role = ChatMessage.Role.HER, content = "那今天几点下班")
    private val idea = ChatMessage(role = ChatMessage.Role.IDEA, content = "先哄两句")

    private val deleted = mutableListOf<String>()
    private val edited = mutableListOf<Int>()
    private val noteEdits = mutableListOf<Int>()

    private fun mount(
        messages: List<ChatMessage>,
        noteText: String? = null,
        editingIndex: Int = -1
    ) {
        deleted.clear()
        edited.clear()
        noteEdits.clear()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                MessageList(
                    messages = messages,
                    editingIndex = editingIndex,
                    onReorder = { _, _ -> },
                    onEdit = { edited.add(it) },
                    onDelete = { deleted.add(it) },
                    noteText = noteText,
                    onEditNote = { noteEdits.add(1) }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val deleteActionLabel: String
        get() = app.getString(R.string.a11y_delete_message)

    /** 行节点（合并树：这一行的读屏身份与动作都挂在这一颗粒上） */
    private fun mergedRows() =
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG)).fetchSemanticsNodes()

    /**
     * 只读的诊断读数，**不参与红绿**：万一读屏那一格还红，这一段直接告诉下一个人红在哪一侧。
     * 读不到就交回一句"读不到"，不许因为它读不到就让判据空过。
     */
    private fun unmergedDiagnose(mergedActionLabelHit: Any?): String = runCatching {
        val own = rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG), useUnmergedTree = true)[0]
            .fetchSemanticsNode()
        val actions = if (own.config.contains(SemanticsActions.CustomActions)) {
            own.config[SemanticsActions.CustomActions]
        } else {
            emptyList()
        }
        val sameAction = actions.firstOrNull { it.label == deleteActionLabel } === mergedActionLabelHit
        "未合并树这一行实到动作标签=" + actions.map { it.label } + "；与合并树读到的是同一颗=" + sameAction
    }.getOrElse { "未合并树读不到这一行（${it::class.simpleName}）" }

    /**
     * 阈值线的两侧各钉一格，连压线那一格一起钉（行宽 1000 ⇒ 线在 400）。
     * 反例（每条各自会打破一格）：
     * · `>=` 写成 `>` ⇒ 400f 那一格红（用户要拖到"再过去一点点"才删，手感像失灵）；
     * · 阈值比从 0.4 调到 0.2/0.3 ⇒ 399.99f 那一格红（擦一下就删了，内容丢得没道理）；
     * · 阈值比调到 0.5 以上 ⇒ 400f/400.01f 两格红（小面板上根本拖不到线，删除只剩读屏一条路）；
     * · 判据写成 `dragPx >= 0`（把阈值比改成 0）⇒ 0f 那一格红；
     * · 忘了 `rowWidthPx > 0` 那道门 ⇒ 行宽 0/负数那三格红（第一帧凭空满足阈值）；
     * · 只算正方向（漏掉绝对值）⇒ 向左那两格红。
     */
    @Test
    fun `the swipe threshold sits on four tenths of the row width in both directions`() {
        assertFalse("差一点点不许删（399.99 < 400 = 0.4×1000）", shouldDeleteBySwipe(399.99f, 1000f))
        assertTrue("正好压线就该删（判据是 >=，不是 >）", shouldDeleteBySwipe(400f, 1000f))
        assertTrue(shouldDeleteBySwipe(400.01f, 1000f))
        assertFalse("向左同样要差一点点才不算", shouldDeleteBySwipe(-399.99f, 1000f))
        assertTrue("向左过线同样删（双向对称）", shouldDeleteBySwipe(-400f, 1000f))
        assertFalse("一个像素都没拖", shouldDeleteBySwipe(0f, 1000f))
        // 行宽没量到 / 非正数 ⇒ 恒不删
        assertFalse("行宽 0（还没布局）不许凭空满足阈值", shouldDeleteBySwipe(999f, 0f))
        assertFalse(shouldDeleteBySwipe(-999f, 0f))
        assertFalse("负行宽是坏读数，不许被 abs 之后当成够阈值", shouldDeleteBySwipe(999f, -1000f))
    }

    /**
     * 判据是**比例**不是定值 px：同一把拖量在窄行上过线、在宽行上还没过线。
     * 反例：阈值写成固定 120px（或任何一刀切的 dp 数）⇒ 150/1000 那一格红；
     * 反例：把两行的行宽读成同一个数（例如都取屏幕宽）⇒ 150/300 与 150/1000 读成同一个结果，红。
     */
    @Test
    fun `a longer row needs a longer swipe than a shorter one`() {
        assertTrue(shouldDeleteBySwipe(150f, 300f))    // 线在 120
        assertFalse(shouldDeleteBySwipe(150f, 1000f))  // 线在 400
        assertFalse(shouldDeleteBySwipe(399f, 1000f))
        // 另一根行宽上同样按 0.4 走（250 ⇒ 线正好是 100，float 上不漂）
        assertFalse(shouldDeleteBySwipe(99.99f, 250f))
        assertTrue(shouldDeleteBySwipe(100f, 250f))
    }

    /** 向左滑过阈值 → 走退场动画，退场收尾后恰好投递被滑那条的 id */
    @Test
    fun `swiping a row left past the threshold deletes that message`() {
        mount(listOf(her, me))
        assertEquals("两行都该在：" + mergedRows().size, 2, mergedRows().size)
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(320L)
        assertEquals("滑到阈值应只删这一条：" + deleted, listOf(her.id), deleted)
    }

    /** 向右滑同样有效（双向删除）；删的是被滑的那条，不是它的邻居 */
    @Test
    fun `swiping a row right past the threshold deletes exactly that row`() {
        mount(listOf(her, me))
        val rows = mergedRows()
        assertEquals("两行都该有滑动锚点：" + rows.size, 2, rows.size)
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[1].performTouchInput { swipeRight() }
        rule.mainClock.advanceTimeBy(320L)
        assertEquals("滑第二行就该删第二行：" + deleted, listOf(me.id), deleted)
    }

    /**
     * **快速小幅横滑不误删**（第5节第2条/第5节第3条 的合同）：一记 30ms 走完 80px 的快擦，
     * 越过了触控 slop（所以行的横向拖拽确实起势了）、没越过行宽四成的阈值 ⇒ 一条都不许丢。
     *
     * 同一格的后半再补一记整行宽的滑：那一条**必须**被删掉。
     * 为什么要这后半：只判"擦一下没删"的话，"手势根本没落到这一行"（标签变了、锚点没了、
     * draggable 被关掉）也能绿——那是恒真。补上"同一行随后仍能删掉这一条"，
     * 就证明这两记手势打的是同一个删除判据，前一半的"没删"是真的没过阈值。
     *
     * 反例：阈值比从 0.4 往下调（80px 够线了）⇒ 前一半红（擦一下就丢内容）；
     * 反例：给甩动/速度开后门（`onDragStopped` 里看 velocity 不看行程）⇒ 前一半红；
     * 反例：`draggable` 的 enabled 挂错（永远起不了势）或整行不再带删除锚点 ⇒ 后一半红。
     */
    @Test
    fun `a fast short swipe deletes nothing while a full swipe on the same row still does`() {
        mount(listOf(her, me))
        assertEquals("两行都该在：" + mergedRows().size, 2, mergedRows().size)
        // 360dp 面板上这一行的宽约 330–360px ⇒ 阈值在 ~132–144px；80px 远在阈值之内
        val shortTravel = 80f
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput {
            val start = center
            down(start)   // 1.6.8 的 TouchInjectionScope 只有 down/moveTo/up，`touchDown` 是 1.7 才有的名字
            moveTo(Offset(start.x - shortTravel / 2f, start.y))   // 15ms
            moveTo(Offset(start.x - shortTravel, start.y))        // 再 15ms：整段 ~30ms
            up()
        }
        rule.mainClock.advanceTimeBy(320L)
        assertTrue("快速小幅横滑（越过 slop、没越过阈值）不许删掉任何东西：" + deleted, deleted.isEmpty())
        assertEquals("擦一下之后两条都该还在：" + mergedRows().size, 2, mergedRows().size)

        // 后一半：同一行来一记真的过阈值的滑，必须删的就是这一行
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(320L)
        assertEquals("同一行随后过阈值仍必须删掉这一条（否则前一半是恒真）：" + deleted,
            listOf(her.id), deleted)
    }

    /**
     * 屏幕上默认不放叉号（第5节第2条），但删除不能跟着失去可达出口：
     * 行的语义节点上必须有那颗自定义"删除消息"动作，而且点它走的仍是同一条退场→onDelete。
     *
     * 判据分两层，各挡一种坏法：
     * · 动作**回报 false** ⇒ 调用没落到生产的动作体（空壳动作，或合并过程把 customActions
     *   换成了另一份）——失败消息里那份诊断读数会同时给出未合并树的实到标签集；
     * · 回报 true 而删除没发生 / 删的不是这一条 ⇒ 生产的读屏出口真没接到删除；
     * · 反例：把"叉号"换成一个画在屏上的删除按钮 ⇒ 叉号计数那句红（合同要求屏幕默认不放叉号）；
     * · 反例：撤掉叉号时把 customActions 一起删了 ⇒ "挂得出自定义动作"与"点名的那一句在"两句红
     *   （用户只剩横滑一条路）；
     * · 反例：动作体写成空壳 ⇒ 回报那句红；
     * · 反例：退场收尾按旧下标删 / 接成"删邻居那一条" ⇒ 落点那句红（消息里打印实到列表）；
     * · 反例：读屏动作错接成"把这条选进编辑位" ⇒ 最后一句红（edited 非空）。
     */
    @Test
    fun `the cross is gone from the screen but the accessibility delete still deletes`() {
        mount(listOf(her, me))
        val rowNodes = mergedRows()
        assertEquals("两行都该在：" + rowNodes.size, 2, rowNodes.size)
        // 正控制：这一棵语义树确实读得到东西（撤掉可见标签后，行的读屏身份还在），
        // 于是下面那句"叉号计数 = 0"不是一棵空树给的假绿。
        val announcementCount = rule
            .onAllNodes(hasContentDescription(app.getString(R.string.a11y_message_her)))
            .fetchSemanticsNodes().size
        assertEquals("正控制：语义树里该有 1 颗『她的消息』（读不到东西时 0 计数不作数）",
            1, announcementCount)
        // 叉号真的没了：语义树里没有任何节点把"删除消息"当 contentDescription 画出来
        assertEquals(
            "屏幕默认不该再有叉号（删除只该有横滑与读屏动作两条路）",
            0,
            rule.onAllNodes(hasContentDescription(deleteActionLabel)).fetchSemanticsNodes().size
        )
        val first = rowNodes.first()
        assertTrue(
            "行上必须挂得出自定义动作，否则撤掉叉号就是撤掉了删除：" + first.config,
            first.config.contains(SemanticsActions.CustomActions)
        )
        val deleteAction = first.config[SemanticsActions.CustomActions]
            .firstOrNull { it.label == deleteActionLabel }
        // 诊断读数只给失败消息用，不参与红绿（见类 KDoc 那一段）
        val diagnose = unmergedDiagnose(deleteAction)
        assertNotNull(
            "读屏动作里要点名的就是那一句删除。实到标签集=" +
                first.config[SemanticsActions.CustomActions].map { it.label } + "；" + diagnose,
            deleteAction
        )

        var handled = false
        rule.runOnIdle {
            handled = deleteAction!!.action()
            // 读屏动作是在 runOnIdle 块里被直接调用的（不走注入通道）：这次写落在全局快照里，
            // 不在任何 dispatcher 帧任务的尾部，Compose 不会自动把改动合并进组合。手势那几条绿
            // 是因为写发生在指针事件帧内、帧尾自动 sendApplyNotifications。这里补上这一笔，
            // 让 armDelete 的写被组合观察到——判的仍是生产那一句动作体，不改它的行为。
            Snapshot.sendApplyNotifications()
        }
        assertTrue(
            "读屏动作必须自己回报『我处理了』（生产那一句交回 true）。拿到 false 说明调用没落到" +
                "生产的动作体上——这一格红在**读的那棵树/那颗动作是空壳**，不红在删除落点。" + diagnose,
            handled
        )
        rule.mainClock.advanceTimeBy(320L)
        assertEquals(
            "读屏那条删除要真的删得掉，且只删这一条（handled=$handled）：" + deleted + "；" + diagnose,
            listOf(her.id), deleted
        )
        assertTrue("读屏删除不该顺手把这条选进编辑位：" + edited, edited.isEmpty())
    }

    /**
     * 删掉**正在编辑**的那一条：交出去的必须是被删那条的稳定 id（不是下标、不是邻居的 id），
     * 因为宿主只认这颗 id 才知道"编辑对象没了"⇒ 编辑位归 -1、对应草稿跟着清。
     *
     * 后半是对照（纯函数，同一份口径）：删掉编辑位前一条时编辑位要跟着挪，但**仍指同一条**，
     * 不许把草稿交给顶上来的那一条（ 要禁的就是"用上一个位置猜对象"）。
     *
     * 反例：退场收尾按旧的 `pair.first` 下标删 ⇒ 第一句红（交出去的 id 是邻居的，
     *   于是被删的正在编辑那条没被认出来，草稿转移到下一条）；
     * 反例：删除路径顺带投一次 `onEdit` ⇒ 第二句红（编辑位被 UI 抢改）；
     * 反例：`MessageListEditing.reindex` 被换成算术位移（删前一条就 -1、删自己就留给上一条）
     *   ⇒ 后两句红。
     */
    @Test
    fun `deleting the row being edited hands over its own id and the edit target does not slide`() {
        val before = listOf(her, me, third)
        mount(before, editingIndex = 1)          // 正在编辑的是 me 那一条
        assertEquals("三行都该在：" + mergedRows().size, 3, mergedRows().size)

        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[1].performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(320L)

        assertEquals("删正在编辑那一条，交出去的必须是它自己的 id：" + deleted,
            listOf(me.id), deleted)
        assertTrue("删除这条消息不该同时投一次『去编辑第 N 条』：" + edited, edited.isEmpty())

        // 宿主拿收到的 id 摘掉那一条之后，编辑指向的唯一口径是按身份重算
        val after = before.filterNot { it.id == me.id }
        assertEquals("被删的正是编辑对象 ⇒ 编辑位 -1（草稿跟着清，不许转移到下一条）",
            -1, MessageListEditing.reindex(before, after, 1))
        assertEquals("删掉编辑位前面那一条 ⇒ 位置跟着挪（1→0），但认的还是同一条",
            0, MessageListEditing.reindex(before, before.filterNot { it.id == her.id }, 1))
    }

    /**
     * 纵向浏览不是删除：滑动落在这条行的横滑轴之外 ⇒ 一条都不删。
     * 前面先钉"两行都在"、后面再钉"两条都还在"，这一句才不是"什么都没发生所以什么都没删"的恒真。
     *
     * 反例：删除判据改成"只要拖了就删"或按 `dragAmount.y` 起势 ⇒ 中间那句红；
     * 反例：把横滑的 draggable 换成任意方向 draggable ⇒ 同一句红。
     *（本轮刻意不判"有没有顺带触发点击"：两行装得下、没人消费纵向拖拽时那一格是点击合同的事，
     *  不在删除这族里造一条与删除无关的红。）
     */
    @Test
    fun `scrolling a row vertically deletes nothing`() {
        mount(listOf(her, me))
        assertEquals("两行都该在：" + mergedRows().size, 2, mergedRows().size)
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(320L)
        assertTrue("上下浏览列表不该被当成删除：" + deleted, deleted.isEmpty())
        assertEquals("浏览之后两条都该还在：" + mergedRows().size, 2, mergedRows().size)
    }

    /**
     *  之后尾部那行灰字**不是删除对象**，也不是"擦一下就打开编辑器"的点击落点：
     * 它唯一的出口是真点它 ⇒ 去编辑完整备注。
     *
     * 这一格红过一次，成因是形状换了两次手：`Modifier.clickable` 的判据是"抬指时还在本节点范围
     * 内"、**不看走了多远**，而备注行有整行宽（实测 344dp），一记横滑的起与止都在它自己身上 ⇒
     * 抬指被当成点击，把编辑入口点着了。生产最早那道闸门 `swipeIsNotATap` 是在 Initial pass
     * **无条件吃事件**（把备注变成父层切页的死区），随原话第 15 条一起撤；撤了之后
     * `AdvisorNoteLine` 里那颗 `draggable` 只在传了 `onSwipeClear` 时存在，宿主没接清除出口
     * （`onClearNote == null`）就没人挡这一路。
     * 现在补回来的闸门是 `MessageList.kt` 的 `noteSwipeIsNotATap`：**只观察不消费**，
     * 横向越过 slop 那一次手势的抬指不投编辑（挂在两个调用点上）。
     * 反例：给备注行留回横滑删除 ⇒ "删掉任何东西"那句红（deleted 非空）；
     * 反例：闸门撤掉（横滑仍被当成点击）⇒ "横滑也不该顺手触发编辑"那句红；
     * 反例：闸门吃得太宽（连原地起落的真点击也吃掉，或在 Initial pass 无条件消费）⇒
     *        "真点那一行只投一次"那句红（用户点灰字没反应，备注再也编辑不了）；
     * 反例：闸门只挂在有侧滑出口的那一支 ⇒ 本格的 mount（没接 `onClearNote`）红在擦那一句；
     * 反例：备注的点击错接成"编辑第 N 条聊天"⇒ 同上那句红 + 最后一句红（edited 非空）；
     * 反例：一记横滑把整行灰字擦没了 ⇒ "擦一下之后那行灰字必须还在"那句红。
     */
    @Test
    fun `the note line is an edit entry, not a swipe delete target`() {
        mount(listOf(her, idea), noteText = "先哄两句")
        val notes = rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG)).fetchSemanticsNodes()
        assertEquals("尾部该有那一行灰字：" + notes.size, 1, notes.size)

        rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG))[0].performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(320L)
        assertTrue("备注行不该被横滑删掉任何东西：" + deleted, deleted.isEmpty())
        assertTrue("横滑也不该顺手触发编辑（擦过去不是点击）：" + noteEdits, noteEdits.isEmpty())
        val afterSwipe = rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG)).fetchSemanticsNodes()
        assertEquals("擦一下之后那行灰字必须还在（不许被擦成没了）：" + afterSwipe.size, 1, afterSwipe.size)

        rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("真点那一行只投一次『去编辑备注』：" + noteEdits, listOf(1), noteEdits)
        assertTrue("编辑备注不是删除：" + deleted, deleted.isEmpty())
        assertTrue("点备注不许落到『编辑第 N 条聊天』：" + edited, edited.isEmpty())
    }

    /** 条目节奏（本轮新起点）：行间距就是那颗 4dp，单行条目也不再垫到 48dp 可见高 */
    @Test
    fun `entries keep the new compact rhythm`() {
        mount(listOf(her, me))
        val rows = probe.laid(
            rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG)).fetchSemanticsNodes().map { probe.of(it) }
        ).sortedBy { it.topDp }
        assertEquals("两行都该量得到：" + rows.joinToString { it.describe() }, 2, rows.size)
        val gap = rows[1].topDp - (rows[0].topDp + rows[0].heightDp)
        assertEquals("条目间距应当是这一轮起点那 4dp：" + rows.joinToString { it.describe() },
            4f, gap, 0.6f)
        // 旧的"整行垫到 48dp 可见高"这一档本次主动放弃：单行气泡应只有 20sp 行高 + 竖 6dp×2 ≈ 32dp。
        // 反例：把 heightIn(min = 48dp) 或旧的 12dp 竖内边距抄回来 ⇒ 这一格当场红。
        rows.forEach { row ->
            assertTrue(
                "单行条目不该再被垫高（合同要的是条目缩小，实测 " + row.describe() + "）",
                row.heightDp < 40f
            )
        }
    }
}
