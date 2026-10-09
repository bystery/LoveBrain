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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 消息行横滑的**方向映射与门槛**（§8.1 / §8.2 / §8.4 消息侧）：
 * 普通消息向内换角色、向外删除；补充两个方向都是删除；门槛 = `max(24dp, min(48dp, 行宽×20%))`
 * 再加一档"有意快滑"（同方向 ≥16dp 且末段速度 ≥600dp/s），反向回拉不作数；
 * 一次手势只落一个动作；纵向浏览与点按编辑都不被吞。
 *
 * 这一族**取代**旧的"双向都是删除 + abs(offset) 达 0.4 行宽就删"那一档（台账 R03 已写明
 * "双向删除被最新方向转换替代"）。旧形状里仍然成立的判据一格没撤：删除仍只认稳定 id、
 * 读屏仍要有那条出口、擦过去仍不许丢内容——只是"擦过去"与"拖得够远"的那两根线换了。
 *
 * 这里只判 JVM 上几何与语义能判定的事：
 * · 「方向 → 动作」那颗纯函数逐值钉（两种角色 × 两个方向 + 补充 + 零位移），
 *   **向左与向右必须交回不同的值**（旧的 abs 判据喂它必红）；
 * · 门槛那颗纯函数按 §8.2 的公式逐值钉，两根 dp 端点**各喂两种 density**：
 *   把 dp 当 px 用的写法当场红（§8.2 明写"指针数据通常是像素，必须经密度转换"）；
 * · 松手判据的三条路：慢拖过线、有意快滑（同向）、反向回拉与不足 16dp 的短擦（都不落）；
 * · 真实注入路径上的四种落点：向外删得掉、向内换角色且**一条都不删**、
 *   没接线的那一侧**不许退化成删除**、过新门槛的短行程删得掉（旧 0.4 那一档喂它必红）；
 * · 一次手势只交一个动作（换角色那一记只递一次 id），且删除/换角色都不顺手投"去编辑"；
 * · 纵向浏览（swipeUp）既不删也不换角色（§8.4 那一行的消息侧）；
 * · 屏幕上没有叉号、但自定义删除动作真实存在且**真能删**（动作自己必须回报 handled=true，
 *   删除必须只落这一条，且不许顺手把这条选进编辑位）；
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
 * ── 关于快滑那一档为什么只在纯函数那一面判 ──────────────────────────────
 * §8.2 的快滑档读的是**末段速度**（px/s，带符号）。本机的指针注入不给可依赖的末段速度读数
 * （`moveTo` 那几步的间隔由仪器自己推帧决定），于是"注入一记快手"这种格子红绿都不说明问题：
 * 红可能是产品没接速度，也可能只是那记注入太慢。所以速度那一档逐值钉在
 * [swipeCommitAction] 上（喂数字，同向/反向/不足 16dp/刚好 600dp/s 各钉一格），
 * 注入路径只判**位移**那一档。真实手指的速度手感、揭示标签与长气泡的重叠档归真机。
 *
 * ── 关于阈值注入路径的旧记录 ────────────────────────────────────────────
 * 旧版这里写着"未达阈值那条注入路径在本机判不稳，所以只由纯函数逐值判"。后来那一格补上了：
 * 不稳的是"短滑 + 靠默认 `swipeLeft()` 收行程"这种写法（默认那记会滑过整行宽，本来就过阈值）。
 * 现在注入那几格自己按 `center` 起按、按**本次量出来的行宽算出行程**再走、抬指，
 * 行程与门槛的关系是算出来的，不靠仪器默认值；同一格的后半（过门槛必须落动作）
 * 保证这两记手势打的是同一个对象。
 *
 * ⚠ 纯函数那几格刻意挑 **1000 / 500 / 200 这一类行宽**：`宽 × 0.2f` 与那两根 dp 端点在 float 上
 * 都落在整百/整十（1000×0.2=200、500×0.2=100、200×0.2=40），于是"压线那一格"判的是 `>=` 本身，
 * 不是浮点噪声。换成 340 这种行宽，门槛会算成 68.000001f，压线格就退化成"差一点点"格。
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
    private val switched = mutableListOf<String>()
    private val edited = mutableListOf<Int>()
    private val noteEdits = mutableListOf<Int>()

    private fun mount(
        messages: List<ChatMessage>,
        noteText: String? = null,
        editingIndex: Int = -1,
        // null = 宿主没接换角色出口（§8.1 向内那一侧这时**既不揭示也不落**，更不许退化成删除）
        switchOwner: ((String) -> Unit)? = { id -> switched.add(id) }
    ) {
        deleted.clear()
        switched.clear()
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
                    onEditNote = { noteEdits.add(1) },
                    onSwitchRole = switchOwner
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
     * §8.1 那张表的唯一映射（纯函数）：**向左与向右交回不同的动作**，而且按**起始角色**判。
     * 反例（各自打破一格）：
     * · 留着旧的"abs(offset) 达阈值就删"那一颗（两个方向都交回 Delete）⇒ 我向左、她向右那两格红；
     * · 方向映射整个写反（向内删、向外换角色）⇒ 四格全红；
     * · 按"输入框当前角色"而不是这一条自己的角色判 ⇒ 同一记向左在两种角色上得到同一个值，
     *   那两格里必有一格红；
     * · 把"补充"（`Role.IDEA`）也按两列那一族判 ⇒ 它那两格红（补充没有另一边，两向都是删除）；
     * · 没有位移也落动作（`dragPx == 0` 走进 else 分支）⇒ 0f 那一格红；
     * · 行程变大就换动作 ⇒ 最后一格红（§8.2："拖动过程中不提前改角色再把第二次阈值当作另一动作"）。
     */
    @Test
    fun `left and right are two different actions picked by the row's starting role`() {
        // 我（右侧）：向左 = 改成她的消息，向右 = 删除
        assertEquals(SwipeAction.SwitchRole, swipeActionForDirection(ChatMessage.Role.ME, -1f))
        assertEquals(SwipeAction.Delete, swipeActionForDirection(ChatMessage.Role.ME, 1f))
        // 她（左侧）：向左 = 删除，向右 = 改成我的消息
        assertEquals(SwipeAction.Delete, swipeActionForDirection(ChatMessage.Role.HER, -1f))
        assertEquals(SwipeAction.SwitchRole, swipeActionForDirection(ChatMessage.Role.HER, 1f))
        // 补充：两个方向都是删除（"按既有方向体验"= 往哪滑就往哪退场，动作只有一个）
        assertEquals(SwipeAction.Delete, swipeActionForDirection(ChatMessage.Role.IDEA, -1f))
        assertEquals(SwipeAction.Delete, swipeActionForDirection(ChatMessage.Role.IDEA, 1f))
        assertEquals("一个像素都没拖 ⇒ 没有方向就没有意图",
            SwipeAction.None, swipeActionForDirection(ChatMessage.Role.ME, 0f))
        // 拖得远只改"到没到线"，不改动作本身
        assertEquals(SwipeAction.SwitchRole, swipeActionForDirection(ChatMessage.Role.ME, -400f))
    }

    /**
     * 位移门槛（纯函数）：`max(24dp, min(48dp, 行宽×20%))`，两根端点**必须经 density 换成 px**。
     * 反例（各自打破一格）：
     * · 门槛比还留着旧的 0.4（行宽 200 ⇒ 线在 80 而不是 40）⇒ 40f 那一格红
     *   （用户报的"手指滑很远仍不触发"就是这一档）；
     * · 只改比例、不加那两根 dp 端点（宽行上还是一百多 px）⇒ 1000 那一格红（该被 48dp 封顶）；
     * · **把 dp 当 px 用**（端点不乘 density）⇒ density=2 那三格红（48dp 该是 96f、24dp 该是 48f）；
     * · min/max 写反（夹成 `min(24dp, max(48dp, ·))`）⇒ 200/1000 任一格红；
     * · 行宽或 density 没量到就凭 24dp 给一个门槛 ⇒ 那三格红（第一帧不许凭空满足）。
     */
    @Test
    fun `the travel threshold is the clamped twenty percent band converted through density`() {
        // density 1：那两根端点就是 dp 数本身
        assertEquals("行宽 200 ⇒ 20% = 40，落在 24~48 之间 ⇒ 线在 40",
            40f, swipeTravelThresholdPx(200f, 1f), 0.001f)
        assertEquals("行宽 100 ⇒ 20 被下限 24dp 抬住", 24f, swipeTravelThresholdPx(100f, 1f), 0.001f)
        assertEquals("行宽 1000 ⇒ 200 被上限 48dp 封顶", 48f, swipeTravelThresholdPx(1000f, 1f), 0.001f)
        // density 2：同一档 dp 换成两倍的 px（这一族就是"dp 当 px"那颗雷的探测器）
        assertEquals("density=2 ⇒ 上限 48dp = 96px", 96f, swipeTravelThresholdPx(1000f, 2f), 0.001f)
        assertEquals("density=2 ⇒ 下限 24dp = 48px", 48f, swipeTravelThresholdPx(100f, 2f), 0.001f)
        // 原话的算式是 `max(24dp, min(48dp, 行宽×20%))`——**上限 48dp 对每一档都成立**，
        // 所以 density=2 时行宽 500（=250dp）那一格虽然 20% 算出 100px，仍被 48dp=96px 封顶。
        // （上一版这里写"不封顶"要 100，是把上限只当 dp 值读、漏了它也要过 density 转换。）
        assertEquals("density=2 ⇒ 中间档 500×0.2=100px 仍被上限 48dp=96px 封顶",
            96f, swipeTravelThresholdPx(500f, 2f), 0.001f)
        // 没量到 ⇒ 恒不许满足
        assertTrue("行宽 0（还没布局）不给门槛", swipeTravelThresholdPx(0f, 1f).isInfinite())
        assertTrue("负行宽是坏读数，也不给门槛", swipeTravelThresholdPx(-300f, 1f).isInfinite())
        assertTrue("density 没量到同样不给门槛", swipeTravelThresholdPx(300f, 0f).isInfinite())
    }

    /**
     * 松手那一帧的唯一判据（纯函数）：慢拖过线落方向那一侧的动作、有意快滑落**同一个**动作、
     * 反向回拉与短擦一律不落。一记手势只可能有一个非 None 的结果。
     *
     * 反例（各自打破一格）：
     * · 还按旧的"abs 达阈值就删"（不看方向）⇒ 我向左 -48f 那一格红（该换角色，却报删除）；
     * · 快滑档整个没接（只认位移）⇒ -30f 配同向 -1300f 那一格红（有意的一甩不算数）；
     * · 快滑档不看符号（写成 abs(velocity)）⇒ 反向回拉那一格红（旧峰值误触发，§8.2 点名的形状）；
     * · 快滑档不设最短位移 ⇒ -15.99f 那一格红（擦一下就算了数）；
     * · 速度端点写成 `> 600`、位移端点写成 `> 16` ⇒ 那两格"刚好压线"红；
     * · 端点没乘 density ⇒ density=2 那两格红（32px 在 2x 上才是 16dp）；
     * · 行宽没量到时放行快滑 ⇒ 最后一格红（第一帧凭空落动作）。
     */
    @Test
    fun `a slow drag past the band and a deliberate same direction flick each commit one action`() {
        // ① 慢拖过线（行宽 1000、density 1 ⇒ 线在 48）
        assertEquals("我向左过线 = 换角色", SwipeAction.SwitchRole,
            swipeCommitAction(ChatMessage.Role.ME, -48f, 1000f, 0f, 1f))
        assertEquals("她向右过线 = 换角色", SwipeAction.SwitchRole,
            swipeCommitAction(ChatMessage.Role.HER, 48f, 1000f, 0f, 1f))
        assertEquals("她向左过线 = 删除", SwipeAction.Delete,
            swipeCommitAction(ChatMessage.Role.HER, -48f, 1000f, 0f, 1f))
        assertEquals("我向右过线 = 删除", SwipeAction.Delete,
            swipeCommitAction(ChatMessage.Role.ME, 48f, 1000f, 0f, 1f))
        assertEquals("补充两向都删（向右）", SwipeAction.Delete,
            swipeCommitAction(ChatMessage.Role.IDEA, 48f, 1000f, 0f, 1f))
        assertEquals("补充两向都删（向左）", SwipeAction.Delete,
            swipeCommitAction(ChatMessage.Role.IDEA, -48f, 1000f, 0f, 1f))
        assertEquals("差一点点不算", SwipeAction.None,
            swipeCommitAction(ChatMessage.Role.ME, -47.99f, 1000f, 0f, 1f))

        // ② 有意快滑：位移 30（不到 48 那条线、过了 16dp 那道下限）+ 同向 1300dp/s
        assertEquals(SwipeAction.SwitchRole,
            swipeCommitAction(ChatMessage.Role.ME, -30f, 1000f, -1300f, 1f))
        assertEquals(SwipeAction.Delete,
            swipeCommitAction(ChatMessage.Role.HER, -30f, 1000f, -1300f, 1f))
        assertEquals("刚好 600dp/s 也算（判据是 >=，不是 >）", SwipeAction.SwitchRole,
            swipeCommitAction(ChatMessage.Role.ME, -30f, 1000f, -600f, 1f))
        assertEquals("刚好 16dp 位移也算", SwipeAction.SwitchRole,
            swipeCommitAction(ChatMessage.Role.ME, -16f, 1000f, -1300f, 1f))
        assertEquals("599dp/s 不到档", SwipeAction.None,
            swipeCommitAction(ChatMessage.Role.ME, -30f, 1000f, -599f, 1f))
        assertEquals("15.99dp 太短：擦一下不算", SwipeAction.None,
            swipeCommitAction(ChatMessage.Role.ME, -15.99f, 1000f, -3000f, 1f))
        // 反向回拉：位移与末端速度符号相反 ⇒ 旧峰值不许说话
        assertEquals("向右拖出去又拉回来抬手（末段速度向左）不算", SwipeAction.None,
            swipeCommitAction(ChatMessage.Role.HER, 30f, 1000f, -1300f, 1f))
        assertEquals("速度读数为 0 时只剩位移那一档", SwipeAction.None,
            swipeCommitAction(ChatMessage.Role.HER, 30f, 1000f, 0f, 1f))
        // 快滑那两端同样经 density：2x 机器上 16dp = 32px、600dp/s = 1200px/s
        assertEquals("24px 在 density=2 上还不够 16dp", SwipeAction.None,
            swipeCommitAction(ChatMessage.Role.ME, -24f, 2000f, -3000f, 2f))
        assertEquals("过了换算后的两端才算", SwipeAction.SwitchRole,
            swipeCommitAction(ChatMessage.Role.ME, -32f, 2000f, -1200f, 2f))
        // 行宽没量到 ⇒ 连快滑也不放行
        assertEquals(SwipeAction.None, swipeCommitAction(ChatMessage.Role.ME, -300f, 0f, -3000f, 1f))
    }

    /**
     * 向外那一侧的删除（她向左）：过线 → 走退场动画，退场收尾后恰好投递被滑那一条的 id，
     * 而且**换角色那一侧一次都没被投**（旧形状里根本没有这一档：那时向左向右都是删除，
     * 两种意图共用一条 abs 判据）。
     *
     * 反例：向内/向外写反（她向左成了换角色）⇒ 落点那句红；
     * 反例：删除与换角色都投了一次（一次手势落了两个动作）⇒ 最后那句红；
     * 反例：退场收尾按旧下标删/删了邻居 ⇒ 落点那句红。
     */
    @Test
    fun `a her row swiped left past the band deletes it and switches nothing`() {
        mount(listOf(her, me))
        assertEquals("两行都该在：" + mergedRows().size, 2, mergedRows().size)
        // 她（左列）向左 = 向外 = 删除
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(320L)
        assertEquals("她向左滑到线应只删这一条：" + deleted, listOf(her.id), deleted)
        assertTrue("这一记不许同时投换角色：" + switched, switched.isEmpty())
    }

    /**
     * 向外那一侧的删除（我向右）：删的是被滑那一行、不是它的邻居，也不换角色。
     * 反例：把"我向右"接成换角色（右列的方向写反）⇒ 落点那句红 + 换角色那句非空；
     * 反例：退场收尾按位置猜对象 ⇒ 第一句红（交出去的是邻居的 id）。
     */
    @Test
    fun `a me row swiped right past the band deletes exactly that row`() {
        mount(listOf(her, me))
        val rows = mergedRows()
        assertEquals("两行都该有滑动锚点：" + rows.size, 2, rows.size)
        // 我（右列）向右 = 向外 = 删除
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[1].performTouchInput { swipeRight() }
        rule.mainClock.advanceTimeBy(320L)
        assertEquals("我向右滑到线删的是第二行：" + deleted, listOf(me.id), deleted)
        assertTrue("这一记不许同时投换角色：" + switched, switched.isEmpty())
    }

    /**
     * 向内那一侧（她向右、我向左）过线 = **换角色**：只递这一条的稳定 id，
     * 一条都不删、两条都还在（同一条气泡移到另一侧，不是"先消失再新建"），也不顺手投"去编辑"。
     *
     * 反例：还按 abs 判据把向内也当删除 ⇒ "一条都不许删"与"两条都该还在"两句红；
     * 反例：向内接成了"编辑那一条"或"改输入框当前角色"那条旧通道 ⇒ 最后一句红
     *   （这一格只判 UI 交出去的是哪一颗键、交了几次；"输入框角色/捕获默认角色没被连带改"
     *    由 `feature/composer/ComposerSwitchRoleTest` 在持有列表的那一侧判）。
     */
    @Test
    fun `swiping a row inward switches that row's role and deletes nothing`() {
        mount(listOf(her, me))
        assertEquals("两行都该在：" + mergedRows().size, 2, mergedRows().size)
        // 她（左列）向右 = 向内 = 改成我的消息
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput { swipeRight() }
        rule.mainClock.advanceTimeBy(320L)
        // 我（右列）向左 = 向内 = 改成她的消息
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[1].performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(320L)

        assertTrue("向内那一侧一条都不许删：" + deleted, deleted.isEmpty())
        assertEquals("换角色只递被滑那一条的 id，一次手势一次：" + switched,
            listOf(her.id, me.id), switched)
        assertEquals("两条都该还在（同一条换边，不是删一条建一条）：" + mergedRows().size,
            2, mergedRows().size)
        assertTrue("换角色不该顺手把这条选进编辑位：" + edited, edited.isEmpty())
    }

    /**
     * 宿主**没接**换角色出口时，向内那一侧什么都不落：既不删（不许退回旧的"横滑一律删"那一档，
     * 留两套入口正是本轮要拆的东西）、也不递 id。
     *
     * 反例：向内没接线就 fallback 成删除 ⇒ 第一句红（用户只是想把那句话改成她说的，内容却没了）；
     * 反例：向外那一侧被一起关掉 ⇒ 后一半红（删除那条路不能因为换角色没接线就跟着没）。
     */
    @Test
    fun `the unwired inward side never falls back to delete while outward still deletes`() {
        mount(listOf(her, me), switchOwner = null)
        assertEquals("两行都该在：" + mergedRows().size, 2, mergedRows().size)
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput { swipeRight() }
        rule.mainClock.advanceTimeBy(320L)
        assertTrue("向内那一侧没主人就什么都不落：" + deleted, deleted.isEmpty())
        assertEquals("两条都该还在：" + mergedRows().size, 2, mergedRows().size)

        // 向外那一侧照旧删得掉（同一条行、同一个锚点）
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(320L)
        assertEquals("向外那一条不许被换角色那一支带没：" + deleted, listOf(her.id), deleted)
    }

    /**
     * 两半各钉一根线：**擦过去不算**、**过了新门槛的短行程就该落**。
     *
     * 前半：一记 12px 的横擦（越过触控 slop ⇒ 行的横向拖拽确实起势了），位移既不到 16dp
     * 那道快滑下限、也远不到位移门槛 ⇒ 一条都不丢、一次都不换。行程刻意压在 16dp **以下**，
     * 这一格才与"注入的末段速度是多少"无关（本机那一份读数不可依赖，见类 KDoc）。
     *
     * 后半：按**本次量出来的行宽**算出 §8.2 那一档门槛（`max(24dp, min(48dp, 行宽×20%))`），
     * 拖到"门槛 + 20px"就必须落删除。这一格是本轮那根"手指滑很远仍不触发"的线：
     * 反例：门槛还留着旧的 0.4 行宽 ⇒ 这一记（约半成行程）不够线，红；
     * 反例：门槛被写成另一个过高的百分比/定值 dp ⇒ 同一句红；
     * 反例：`draggable` 的 enabled 挂错（永远起不了势）或整行不再带锚点 ⇒ 后半红（什么都不落）；
     * 反例：把 dp 当 px 用 ⇒ density≠1 的机器上门槛算错，前半那句"擦过去不算"可能红。
     */
    @Test
    fun `a graze inside the band does nothing while a swipe just past the new band deletes`() {
        mount(listOf(her, me))
        assertEquals("两行都该在：" + mergedRows().size, 2, mergedRows().size)
        // 前半：12px 的擦（slop 是 8dp，这一记越过了 slop 但两档都不到）
        val graze = 12f
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput {
            val start = center
            down(start)   // 1.6.8 的 TouchInjectionScope 只有 down/moveTo/up，`touchDown` 是 1.7 才有的名字
            moveTo(Offset(start.x - graze / 2f, start.y))
            moveTo(Offset(start.x - graze, start.y))
            up()
        }
        rule.mainClock.advanceTimeBy(320L)
        assertTrue("没到两档里任何一档的横擦不许丢内容：" + deleted, deleted.isEmpty())
        assertTrue("也不许换角色：" + switched, switched.isEmpty())
        assertEquals("擦一下之后两条都该还在：" + mergedRows().size, 2, mergedRows().size)

        // 后半：门槛按这一轮那三条数**现场算**（行宽是本格量到的，density 是这台机器的）
        val rowWidthPx = probe.of(
            rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG)).fetchSemanticsNodes()[0]
        ).widthDp * density
        val bandPx = (rowWidthPx * 0.2f).coerceIn(24f * density, 48f * density)
        // 留 20px 余量：触控 slop 那一段（8dp）可能被拖拽自己吃掉，行内实际累积的是
        // "越过 slop 之后"的那一截；这一格判的是"过线就落"，不是那 8px 归谁
        val travel = bandPx + 20f
        assertTrue(
            "这一格的行程必须仍**低于旧的那条 0.4 线**，否则它测不出门槛降下来这件事" +
                "（行宽 " + rowWidthPx + "px ⇒ 旧线 " + rowWidthPx * 0.4f + "px、新线 " + bandPx + "px）",
            travel < rowWidthPx * 0.4f
        )
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[0].performTouchInput {
            val start = center
            down(start)
            moveTo(Offset(start.x - travel / 2f, start.y))
            moveTo(Offset(start.x - travel, start.y))
            up()
        }
        rule.mainClock.advanceTimeBy(320L)
        assertEquals("过了新门槛的短行程必须落删除（否则门槛等于没降）：" + deleted,
            listOf(her.id), deleted)
        assertTrue("删除那一侧不许顺带换角色：" + switched, switched.isEmpty())
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
     * ⚠ 这一格滑的是**向右**：被滑那一条是 `me`（右列），§8.1 之后它的**向外**才是删除，
     * 向左已经变成"改成她"（旧版这里写的是 swipeLeft，那个方向现在归换角色那一支）。
     *
     * 后半是对照（纯函数，同一份口径）：删掉编辑位前一条时编辑位要跟着挪，但**仍指同一条**，
     * 不许把草稿交给顶上来的那一条（ 要禁的就是"用上一个位置猜对象"）。
     *
     * 反例：退场收尾按旧的 `pair.first` 下标删 ⇒ 第一句红（交出去的 id 是邻居的，
     *   于是被删的正在编辑那条没被认出来，草稿转移到下一条）；
     * 反例：删除路径顺带投一次 `onEdit` ⇒ 第二句红（编辑位被 UI 抢改）；
     * 反例：向内/向外映射写反（向右成了换角色）⇒ 第一句红 + 换角色那句红；
     * 反例：`MessageListEditing.reindex` 被换成算术位移（删前一条就 -1、删自己就留给上一条）
     *   ⇒ 后两句红。
     */
    @Test
    fun `deleting the row being edited hands over its own id and the edit target does not slide`() {
        val before = listOf(her, me, third)
        mount(before, editingIndex = 1)          // 正在编辑的是 me 那一条
        assertEquals("三行都该在：" + mergedRows().size, 3, mergedRows().size)

        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[1].performTouchInput { swipeRight() }
        rule.mainClock.advanceTimeBy(320L)

        assertEquals("删正在编辑那一条，交出去的必须是它自己的 id：" + deleted,
            listOf(me.id), deleted)
        assertTrue("删除这条消息不该同时投一次『去编辑第 N 条』：" + edited, edited.isEmpty())
        assertTrue("向外那一侧不许顺带换角色：" + switched, switched.isEmpty())

        // 宿主拿收到的 id 摘掉那一条之后，编辑指向的唯一口径是按身份重算
        val after = before.filterNot { it.id == me.id }
        assertEquals("被删的正是编辑对象 ⇒ 编辑位 -1（草稿跟着清，不许转移到下一条）",
            -1, MessageListEditing.reindex(before, after, 1))
        assertEquals("删掉编辑位前面那一条 ⇒ 位置跟着挪（1→0），但认的还是同一条",
            0, MessageListEditing.reindex(before, before.filterNot { it.id == her.id }, 1))
    }

    /**
     * 纵向浏览不属于横滑那一条轴（§8.4："普通列表纵向滑动 = 滚动"）：既不删也不换角色。
     * 前面先钉"两行都在"、后面再钉"两条都还在"，这一句才不是"什么都没发生所以什么都没删"的恒真。
     *
     * 反例：删除判据改成"只要拖了就删"或按 `dragAmount.y` 起势 ⇒ 中间那句红；
     * 反例：把横滑的 draggable 换成任意方向 draggable ⇒ 同一句红（纵向被吞进横滑轴）；
     * 反例：换角色那一支也吃纵向位移 ⇒ 最后一句红。
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
        assertTrue("纵滑也不该被当成换角色：" + switched, switched.isEmpty())
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
