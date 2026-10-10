package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbMetric
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.panel.stats.UsageStatBar
import com.lovebrain.app.ui.panel.stats.UsageStatTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §9（K09 窗口侧）：面板/谈心顶部那条**透明拖动区**到底打不打得到。
 *
 * 2026-10 本轮把这一族换了判据，因为旧那一版测的是**画出来的读数**：
 * 命中带当时靠自定义布局从一只 4dp 的盒子里向上下溢出到 20dp，语义树当然量得到 20dp——
 * 可"量得到 20dp"与"打得到 20dp"是两件事。Compose 的命中遍历（1.6.8，
 * `NodeCoordinator.hitTest` / `InnerNodeCoordinator.hitTestChild`）只看两样：
 * ①沿途有没有**开 clip 的层**把这一点挡在外面（`withinLayerBounds` = graphicsLayer 的 clip，
 *   不是父节点的宽高），②兄弟节点按 z 序**倒着**走，第一个有命中的那一支一旦记到命中，
 *   就不再回到别的兄弟（`siblingHits` + `shouldSharePointerInputWithSiblings`）。
 * 于是旧结构里那一只 4dp 盒子里并排躺着两样东西：带子（先摆）与统计那一行（后摆、`fillMaxWidth`、
 * 按一行高溢出到 4dp 之外）。统计条在"放不下"那一档装了自己的横向 `draggable` ⇒
 * 它那一行的矩形之内（整条带宽 × 一行高）手指事件**根本进不到带子那一支**，
 * 带子只剩下最上面那几 dp——这就是用户报的"顶部透明拖动区难拖"。
 *
 * 现在的结构：带子**自己就是那只容器**（`DragBand.hitHeight` 占进布局，体里没有溢出），
 * 统计那一行挂在带子里面那只 4dp 细槽上 ⇒ 手势归属由父子层级定：
 * 子层（统计）先收事件、只认领横向；父层（带子）认领拖动 ⇒ 同一块矩形内竖向照样移动窗口。
 *
 * ⚠ 2026-10-10 实测把上面那句"子层先收事件"的**范围**钉准了（本轮三格红因就落在这一句）：
 * Compose 1.6 里 pointer-input 节点不按自己那格矩形收事件——只要**它所属的父 LayoutNode** 被命中，
 * 子树的 pointer-input 节点一起进命中链（`LayoutNode.hitTest` 收集子树那颗集合）。
 * 于是带子里装着统计那一格（换组手势挂在它自己身上）时，**整条带子的横向**都先被它认领，
 * 带子拿到剩下那一轴（竖向）。带子自己那一段的横向只在统计那一格**不装手势**时才归窗口
 * （诊断件实到：把那一格整颗摘掉 ⇒ 顶段 y=2px 的横拖 `moves` 5 帧；装回去 ⇒ `moves` 0/换组 5 帧，
 * 且把探针缩进 4dp 槽里、落点从 y=2px 换到 y=38px，归属都不变）。
 * ⇒ 这一族按这个形状判：**竖向在带子任意一段 ⇒ 窗口位移；横向 ⇒ 统计那一格换组、窗口一寸不动**。
 *   旧结构（统计行与带子并排做兄弟）确实能还带子一段横向，代价就是用户报的"难拖"本身；
 *   第①格（容器占位 + 槽位是孩子）与第③格第一句（行内竖拖 ⇒ 窗口）会把那种回退钉住，不会悄悄退回去。
 *
 * 这一族仍然把三轴分开量（§4.3）：可见 = [DRAG_HANDLE_SLOT_TAG] 那一档 4dp 细槽；
 * 命中 = [DRAG_HANDLE_HIT_TAG] 那颗容器自己；父布局占位 = 同一个容器（本轮这两颗并成一颗，
 * 正是修的东西）。结构照搬宿主 `LoveBrainPanelScreen` 顶部那一段
 * （`Column(padding 左右 + 底)` → `DragHandle{ 4dp 槽 → 统计 }` → `PanelHeader`），
 * 但**不引整屏 ViewModel**：这一格要的是带子的几何与手势归属，不是整屏装配。
 *
 * ⚠ 这台仪器证到的与证不到的，划清：
 * - 证得到：容器自己的高度就是命中高度（不是溢出读数）、槽位是带子的**孩子**而不是它的父亲、
 *   可见那一格仍是 4dp 且位置没动、注入落在带子任意一段是否真的交回窗口位移、
 *   两颗手势 owner 是否各归各的轴、短点击是否不交位移、页头 clickable 是否照旧赢。
 * - 证不到（全部挂真机那一栏，不写成已验证）：真实手指宽下这一条好不好命中、
 *   悬浮窗在 WindowManager 里的真实移动、面板 24dp 圆角那一圈 `clipToOutline`
 *   把带子上沿左右两个角真的挡在外面挡掉多少、键盘与输入焦点是否被抢、
 *   空消息/回复/谈心三种状态下分别拖不拖得起来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DragHandleHitBandTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density

    private fun toDp(px: Float): Float = px / density
    private fun toPx(dp: Dp): Float = dp.value * density

    /** 统计条那条定时器是 `while(true) delay(...)`：自动走时会让 waitForIdle 一路快进 */
    @Before
    fun freezeTheClock() {
        rule.mainClock.autoAdvance = false
    }

    private fun settle(frames: Int = 6) {
        repeat(frames) { rule.mainClock.advanceTimeBy(16L) }
        rule.waitForIdle()
    }

    /**
     * 顶部那一段。[statFields] 给非空就挂**真的** `UsageStatBar`（页面真实那一颗），
     * 不给就挂 [StatRowProbe] 那一只只借"子层 + 横向"这一形状的探针（判归属时要一个
     * 一定装手势、也一定读得出计数的那一颗）。
     */
    private fun mount(
        moves: MutableList<Pair<Float, Float>> = mutableListOf(),
        statDrags: MutableState<Int> = mutableStateOf(0),
        headerDrags: MutableState<Int> = mutableStateOf(0),
        modeChanges: MutableState<Int> = mutableStateOf(0),
        statFields: List<LbMetric>? = null
    ) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // 与宿主同一把尺：顶部内边距交回带子自己占（12 + 4 + 4），这里只留左右与底
                        .padding(horizontal = Spacing.xl)
                        .padding(bottom = Spacing.lg)
                ) {
                    DragHandle(
                        onMove = { dx, dy -> moves.add(dx to dy) },
                        content = {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(y = DragBand.overhangAbove)
                                    .fillMaxWidth()
                                    .height(DragBand.slot)
                                    .testTag(DRAG_HANDLE_SLOT_TAG),
                                contentAlignment = Alignment.Center
                            ) {
                                if (statFields != null) {
                                    UsageStatBar(
                                        fields = statFields,
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true)
                                    )
                                } else {
                                    StatRowProbe(
                                        onHorizontalDrag = { statDrags.value += 1 },
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true)
                                    )
                                }
                            }
                        }
                    )
                    Box(modifier = Modifier.fillMaxWidth().testTag(HEADER_SLOT_TAG)) {
                        PanelHeader(
                            panelMode = 0,
                            onModeChange = { modeChanges.value += 1 },
                            onCollapse = {},
                            onHeaderDrag = { _, _ -> headerDrags.value += 1 }
                        )
                    }
                }
            }
        }
        settle()
    }

    private fun rectOf(tag: String, unmerged: Boolean = true): Rect =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged)
            .onFirst()
            .fetchSemanticsNode()
            .boundsInRoot

    private fun nodeOf(tag: String, unmerged: Boolean = true): SemanticsNode =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged).onFirst().fetchSemanticsNode()

    /**
     * 一棵子树里出现过的 testTag（判"谁挂在谁里面"用，不靠像素猜）。
     *
     * ⚠ 读法必须是 `config.getOrNull(TestTag)`（与 `ScrollScan` 的 `config.contains(...)`、
     * `SemanticsProbe.of` 的 `getOrNull` 同一族），**不是** `config[TestTag]`：
     * `SemanticsConfiguration.get` 对没有这个键的节点直接抛
     * `IllegalStateException: Key not present: AccessibilityKey: TestTag`。
     * 一棵真实子树里绝大多数节点（Text、Box、间距）都不写 tag，所以按 `config[...]` 走第一颗
     * 无名后代就炸——原来这两格红在"Key not present"上，红的是**这台仪器自己**，
     * 不是带子的形状。别让下一个人在同样的地方再错一次。
     */
    private fun tagsUnder(node: SemanticsNode): Set<String> {
        val mine = node.config.getOrNull(SemanticsProperties.TestTag)
        return (if (mine == null) emptySet() else setOf(mine)) +
            node.children.flatMap { tagsUnder(it) }
    }

    /** 从带子那一段往左右/上下注入：坐标是**那颗节点自己的**局部坐标 */
    private fun dragInside(tag: String, start: Offset, dxPx: Float, dyPx: Float, steps: Int = 6) {
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).onFirst().performTouchInput {
            down(start)
            var p = start
            repeat(steps) {
                p = Offset(p.x + dxPx, p.y + dyPx)
                moveTo(p)
            }
            up()
        }
        settle(2)
    }

    /**
     * 带子上半段那一段真空里的一点（在统计那一行的矩形**之外**，且仍在容器之内）。
     * 取"那一段空白自己的中点"而不是写死一个数：行高由字号长，写死的落点会哪天掉进行里
     * 变成"这一格什么都没测"，所以量不到空白时这一句直接红（夹具自己要有牙）。
     */
    private fun pointInBandAboveRow(band: Rect, row: Rect): Offset {
        val gapPx = row.top - band.top
        assertTrue(
            "带子里必须有统计那一行之外的一段空白，否则这一格测不到任何东西：" +
                "band.top=${band.top} row.top=${row.top} gap=$gapPx",
            gapPx > 2f
        )
        return Offset(band.width / 2f, gapPx / 2f)
    }

    // ═══════════ ① 容器那一格自己就是命中带 ═══════════

    /**
     * 命中高度由**容器占进布局**那一格给出来，不是靠子节点溢出画出来的读数。
     *
     * 四句一起判：
     * - 容器那颗（= 挂手势那颗）自己的高度就是 `DragBand.hitHeight`；
     * - 可见那一格 4dp 的槽位是它的**孩子**（`tagsUnder(band)` 里读得到），不是它的父亲——
     *   旧写法正好反过来：4dp 盒子是父亲、带子是往外画的儿子，这一句当场红；
     *   同时槽位住在带子里第 [DragBand.overhangAbove] dp 那一档、下沿再留 [DragBand.overhangBelow]，
     *   三段都是真占进布局的位置（没有负偏移）；
     * - 带子的下沿正好停在页头上沿（连续一条、不压页头），页头紧跟其后（不留第二颗 Spacer）；
     * - 源码侧：带子里再没有自定义布局往外画（`.layout {` / `placeRelative`），
     *   容器高度确实写的就是那颗命中数。
     *
     * 怎么坏会红：把溢出那套 `dragHitBand` 装回来（父盒子又变 4dp）⇒ 第二句与最后一句红；
     * 把 `hitHeight` 抬成 30/40/50 或把带子压回 4dp ⇒ 第一句红；
     * 带子往下长到盖住页头 ⇒ 第三句红。
     */
    @Test
    fun `命中那一格由容器自己占进布局，可见那一格仍是里面那寸细槽`() {
        mount()

        val band = rectOf(DRAG_HANDLE_HIT_TAG)
        val slot = rectOf(DRAG_HANDLE_SLOT_TAG)
        val header = rectOf(HEADER_SLOT_TAG)

        assertEquals(
            "容器高度就是命中高度（${DragBand.hitHeight}dp），实到 ${toDp(band.height)}dp",
            DragBand.hitHeight.value,
            toDp(band.height),
            1.5f
        )
        assertTrue(
            "可见那一格必须是带子的孩子（旧写法里它是带子的父亲，带子只是往外画）：" +
                tagsUnder(nodeOf(DRAG_HANDLE_HIT_TAG)).joinToString(),
            tagsUnder(nodeOf(DRAG_HANDLE_HIT_TAG)).contains(DRAG_HANDLE_SLOT_TAG)
        )
        assertEquals(
            "槽位住在带子里第 ${DragBand.overhangAbove}dp 那一档（上沿没有往外画）",
            toPx(DragBand.overhangAbove),
            slot.top - band.top,
            1.5f
        )
        assertEquals(
            "槽位下沿到带子下沿仍是那一段真空白（页头之前的 Spacer 现在归容器）",
            toPx(DragBand.overhangBelow),
            band.bottom - slot.bottom,
            1.5f
        )
        assertEquals("带子下沿必须正好停在页头上沿", header.top, band.bottom, 1.5f)

        val code = SourceScan.maskComments(bandSource())
        assertTrue(
            "带子又改用自定义布局往外画（`.layout {` / `placeRelative`）：那种读数只是画出来的，" +
                "同一格里叠着别的东西时兄弟命中根本走不到它",
            !code.contains(".layout {") && !code.contains("placeRelative")
        )
        assertTrue(
            "容器高度不再是那颗命中数（`.height(hitHeight)` 丢了就是又回到往外画）",
            Regex("""\.height\(\s*hitHeight\s*\)""").containsMatchIn(code)
        )
        assertEquals(
            "可见那一格的档位不许因为好拖而变粗：DragBand.slot 仍然是 Spacing.sm",
            Spacing.sm,
            DragBand.slot
        )
    }

    /**
     * 可见那一格的尺寸读数一寸没动，带子也没有为了好拖多画一条粗东西。
     *
     * 怎么坏会红：给带子补一条横线/加高槽位（`background(` / `Canvas(` / `drawBehind`）⇒ 后两句红；
     * 把槽位撑高 ⇒ 第一句红。
     */
    @Test
    fun `可见细标记的尺寸读数不变，带子自己不多画东西`() {
        mount()

        val slot = rectOf(DRAG_HANDLE_SLOT_TAG)
        assertEquals(
            "可见那一格仍是 ${Spacing.sm}dp，实到 ${toDp(slot.height)}dp",
            Spacing.sm.value,
            toDp(slot.height),
            0.6f
        )
        // 槽位的**垂直中心**落在带子里第 overhangAbove + slot/2 那一档 ⇒ 统计那一行的像素位置与本轮之前逐字相同
        assertEquals(
            "可见那一格在窗口里的垂直位置漂了（统计那一行会跟着漂）",
            toPx(DragBand.overhangAbove) + toPx(DragBand.slot) / 2f,
            rectOf(DRAG_HANDLE_HIT_TAG).top + (slot.top + slot.bottom) / 2f,
            1.5f
        )

        val code = SourceScan.maskComments(bandSource())
        val bandBody = code.substring(code.indexOf("fun DragHandle("), code.indexOf("fun TriangleArrow("))
        assertTrue(
            "带子自己长出涂装（background/drawBehind/Canvas）就是把透明带画粗了：$bandBody",
            listOf("background(", "drawBehind", "Canvas(").none { bandBody.contains(it) }
        )
    }

    // ═══════════ ② 带子任意一段都真的打得到 ═══════════

    /**
     * 手指按在**可见那一格之外**（带子上半段那一段真空白）拖 ⇒ 窗口就该动，
     * 位移逐帧交回且与手指同向（§9"按下之后立即跟手"，不追赶不反向）。
     *
     * ⚠ 这一格的**轴**在 2026-10-10 按实测重写（旧写法是"同一点横拖 ⇒ 窗口动"，红在 `实到 0 帧`）。
     * 分开判之后结论是：**注入没坏、带子也没坏**，坏的是那句判据对形状的假设。实到读数（同一颗粒、
     * 同一落点 y=7px，density=2）：
     * - 轴换成**竖向** ⇒ `moves` 5 帧且同向 ⇒ 容器自己占的那一段真打得到；
     * - 把带子里统计那一格**整颗摘掉** ⇒ 同一点横拖 `moves` 5 帧 ⇒ 带子自己的横向也活着；
     * - 统计那一格**装回**换组手势 ⇒ 同一点横拖变 `moves=0 / 换组 5 帧`，
     *   且这一记归属**与落点无关**（诊断件把探针缩进 4dp 槽里、只占 24..32px，
     *   带子顶段 y=2px 的横拖照样被它认领）。
     *   ⇒ 这是 Compose 1.6 的指针分发规矩：pointerInput 节点在它**所属 LayoutNode 被命中**时
     *   就进命中链（`LayoutNode.hitTest` 会把子树的 pointer-input 节点一起收进来），
     *   不必自己那格矩形被点到。于是
     *   **"竖向在统计那一行之内归窗口"与"横向在带子自己那一段归窗口"结构上不可同时成立**：
     *   前者要求统计那一格是带子的**后代**（正是本轮修的东西，也是本文件第①格验的父子关系），
     *   后者就必然让那颗 `draggable` 在整条带子的矩形内先认领横向。
     *   旧结构（带子与统计行**并排**做兄弟）确实能让带子独占顶段横向，代价就是用户报的那一条：
     *   统计那一行之内连竖向都进不到带子那一支 ⇒ "顶部透明拖动区难拖"。旧结构仍然被第③格
     *   第一句（行内竖拖 ⇒ 窗口动）与第①格（容器占位 + 槽位是孩子）钉住，不会悄悄回退。
     * ⇒ 于是这一格判**新形状真实的那两条**，都还是数值判据、没有松成"存在即可"：
     *   带子自己那一段的**竖向**拖动交回窗口位移且同向；同一点的**横向**由统计那一格的换组先认领、
     *   窗口一寸都不许跟着走。
     *
     * 怎么坏会红：容器高度缩回 4dp（回到"往外画"）⇒ 落点出界，夹具自己那颗
     * `assertTrue(gapPx > 2f)` 先红（夹具带牙，不放行空测）；带子的 `pointerInput` 掉了或改成
     * 只认领横向 ⇒ 竖向 `moves` 0 帧红；中间加节流/缓动 ⇒ 同向那句红；
     * 统计那一格的换组被摘掉 ⇒ 横向那两句红（换组 0 帧）。
     */
    @Test
    fun `打在带子上半段（可见那一格之外）就真的移动窗口`() {
        val moves = mutableListOf<Pair<Float, Float>>()
        val statDrags = mutableStateOf(0)
        val headerDrags = mutableStateOf(0)
        val modeChanges = mutableStateOf(0)
        mount(moves, statDrags = statDrags, headerDrags = headerDrags, modeChanges = modeChanges)

        val band = rectOf(DRAG_HANDLE_HIT_TAG)
        val row = rectOf(STAT_ROW_TAG)
        val insideOverhang = pointInBandAboveRow(band, row)

        // 竖向：带子自己占的那一段（可见那一格之外）必须把位移交回窗口
        dragInside(DRAG_HANDLE_HIT_TAG, insideOverhang, 0f, 12f * density)
        assertTrue("带子上半段的竖向拖动要产生窗口位移，实到 ${moves.size} 帧", moves.isNotEmpty())
        val totalDy = moves.sumOf { it.second.toDouble() }.toFloat()
        assertTrue("位移要与手指同向（跟手）：总 dy=$totalDy", totalDy > 0f)
        assertEquals("带子的竖向拖动不许被统计那一格的换组认领", 0, statDrags.value)
        assertEquals("带子上半段不是页头拖动道", 0, headerDrags.value)
        assertEquals("带子上半段不吃模式两段的点击", 0, modeChanges.value)

        // 横向：同一点归统计那一格的换组，窗口一寸都不许走
        moves.clear(); statDrags.value = 0
        dragInside(DRAG_HANDLE_HIT_TAG, insideOverhang, 12f * density, 0f)
        assertTrue(
            "统计那一格装了换组手势时，带子这一段的横拖由它认领（子层先收事件），实到 ${statDrags.value} 帧",
            statDrags.value > 0
        )
        assertTrue("带子这一段的横拖不许把窗口拖走：实到 ${moves.size} 帧", moves.isEmpty())
    }

    /**
     * 短点击不跳窗（§9 原话）：在带子上按下即抬、不走行程 ⇒ 一次位移都不交。
     *
     * 怎么坏会红：把 `detectDragGestures` 改成"按下即动"（丢掉 touch slop）或在 `onDragStart`
     * 里补一段位移 ⇒ 这里红（用户报的正是"短点击导致窗口突然跳走"）。
     */
    @Test
    fun `短点击不把窗口带跑`() {
        val moves = mutableListOf<Pair<Float, Float>>()
        val headerDrags = mutableStateOf(0)
        mount(moves, headerDrags = headerDrags)

        val band = rectOf(DRAG_HANDLE_HIT_TAG)
        rule.onAllNodes(hasTestTag(DRAG_HANDLE_HIT_TAG), useUnmergedTree = true).onFirst()
            .performTouchInput { click(Offset(band.width / 2f, band.height / 2f)) }
        settle(2)

        assertTrue("短点击不该产生任何窗口位移，实到 ${moves.size} 帧", moves.isEmpty())
        assertEquals("短点击也不该触发页头那一条拖动", 0, headerDrags.value)
    }

    /**
     * 带子把空白吃进自己那一格之后，页头那两段的点击仍归它们自己
     * （§9"避开模式按钮、齿轮、关闭和输入区域"）：在页头第一段那一格点一下 ⇒ 切模式回调一次、窗口位移零帧。
     *
     * 怎么坏会红：带子往下长到盖住页头，或给整扇窗加了抢点击的父手势 ⇒ `modeChanges` 数到 0、
     * `moves` 不为空，两句一起红。
     */
    @Test
    fun `带子不吃页头那两段的点击`() {
        val moves = mutableListOf<Pair<Float, Float>>()
        val modeChanges = mutableStateOf(0)
        mount(moves, modeChanges = modeChanges)

        val header = rectOf(HEADER_SLOT_TAG)
        rule.onAllNodes(hasTestTag(HEADER_SLOT_TAG), useUnmergedTree = true).onFirst()
            .performTouchInput { click(Offset(header.width * 0.3f, header.height / 2f)) }
        settle(2)

        assertEquals("页头那一段该照旧被点到（切模式回调一次），实到 ${modeChanges.value}", 1, modeChanges.value)
        assertTrue("点模式按钮不该顺带移动窗口，实到 ${moves.size} 帧", moves.isEmpty())

        val headerClickables = rule.onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .count { it.boundsInRoot.top >= header.top - 1.5f && it.boundsInRoot.bottom <= header.bottom + 1.5f }
        assertTrue(
            "页头行内的可点节点该数得到（两段 + 收起），实到 $headerClickables",
            headerClickables >= 3
        )
    }

    // ═══════════ ③ 两颗手势 owner 各归各的轴 ═══════════

    /**
     * 统计那一格与拖动带**不共享同一颗 owner**：同一段像素上，竖向归带子、横向归统计条，
     * 谁也不许把谁吞掉。三句必须一起判才咬得住：
     * 1. 竖向拖**打在统计那一行之内** ⇒ 窗口动、统计那一格一次都不认领
     *    （旧并排结构在这一句红：统计条那一行之内事件走不到带子那一支，这就是"难拖"本身）；
     * 2. 横向拖打在统计那一行之内 ⇒ 统计那一格认领到、窗口**不**动（带子不吞它）；
     * 3. 竖向拖打在带子自己那一段（统计那一行之外）⇒ 窗口动、统计那一格一次都不认领。
     *    ⚠ 这第三句在 2026-10-10 由"横拖"改成"竖拖"，改的是**轴**不是松紧：带子里装着换组手势的
     *    那一格是带子的**后代**，而 Compose 1.6 把子树的 pointer-input 节点一起收进父层的命中链，
     *    所以带子那一段的横向**必然**先被那颗 `draggable` 认领（实测：落点从 y=2px 换到 y=38px、
     *    把探针缩进 4dp 槽里，结论都不变）。带子那一段的横向归属因此由下一格（"打在带子上半段"）
     *    按真实形状钉住：横拖 ⇒ 换组认领、窗口不动。
     *    "把统计条的横向手势搬到带子那一层（两颗 owner 并一颗）"这种坏实现仍然红，红在那两条闸上：
     *    本格末尾的源码来路闸（带子那一段不许出现 `draggable`/`Orientation.Horizontal`）与
     *    下一格"同一点横拖要由统计那一格认领、窗口不许动"（并成一颗后窗口会动 ⇒ 那句当场红）。
     *
     * 再加一句来路闸：横向那颗 `draggable` 必须还长在 `UsageStatBar` 自己的文件里，
     * 带子那一段里既没有 `draggable` 也没有 `Orientation.Horizontal`。
     */
    @Test
    fun `统计条那一格只认领横向，竖向与带子自己那一段归拖动`() {
        val moves = mutableListOf<Pair<Float, Float>>()
        val statDrags = mutableStateOf(0)
        mount(moves, statDrags = statDrags)

        val band = rectOf(DRAG_HANDLE_HIT_TAG)
        val row = rectOf(STAT_ROW_TAG)

        // 1. 竖向，起点就在统计那一行的正中间
        moves.clear(); statDrags.value = 0
        dragInside(STAT_ROW_TAG, Offset(row.width / 2f, row.height / 2f), 0f, 10f * density)
        assertTrue("统计那一行里的竖向拖动要移动窗口，实到 ${moves.size} 帧", moves.isNotEmpty())
        assertEquals("统计那一行里的竖向拖动不该被它当成横滑认领", 0, statDrags.value)

        // 2. 横向，起点同样在统计那一行的正中间
        moves.clear(); statDrags.value = 0
        dragInside(STAT_ROW_TAG, Offset(row.width / 2f, row.height / 2f), -10f * density, 0f)
        assertTrue("横滑统计那一行该由它自己认领（换组），实到 ${statDrags.value} 帧", statDrags.value > 0)
        assertTrue("统计那一格的横滑不许顺带把窗口拖走，实到 ${moves.size} 帧", moves.isEmpty())

        // 3. 同样打在带子自己那一段（统计那一行之上），要移动窗口的那一手是竖向
        moves.clear(); statDrags.value = 0
        dragInside(DRAG_HANDLE_HIT_TAG, pointInBandAboveRow(band, row), 0f, 10f * density)
        assertTrue("带子自己那一段的竖向拖动要移动窗口，实到 ${moves.size} 帧", moves.isNotEmpty())
        assertEquals("带子自己那一段不是统计条的换组道（两颗 owner 一旦并一颗就会从这里漏）", 0, statDrags.value)

        val bandCode = SourceScan.maskComments(bandSource())
        val bandBody = bandCode.substring(bandCode.indexOf("fun DragHandle("), bandCode.indexOf("fun TriangleArrow("))
        assertTrue(
            "带子那一层又长出统计条的横向手势（两颗 owner 并成一颗）：$bandBody",
            !bandBody.contains("draggable") && !bandBody.contains("Orientation.Horizontal")
        )
        assertTrue(
            "横向换组那颗必须还挂在统计条自己的节点上（搬到别处就是本页的手势归属又漂了）",
            SourceScan.maskComments(statBarSource()).contains("Modifier.draggable(") ||
                SourceScan.maskComments(statBarSource()).contains(".draggable(")
        )
    }

    /**
     * 页面真实那一颗统计条挂进带子里面，且**竖向一拖就动窗口**。
     *
     * 这一格是用户报的那一条的形状：五个字段摆不宽时（轮播档）它装着自己的横向拖拽，
     * 旧并排结构下带子在那一行之内整片失效。判据两样：
     * 真实 BAR 那颗是带子的**后代**（不是带子的父亲、也不是带子的兄弟）+ 在它正中间往上下拖 ⇒ 窗口位移非空。
     *
     * ⚠ 只判**竖向**：横向那一档归上一格那颗探针（真实组件装不装手势取决于量宽结果，
     * 拿它当"横滑一定换组"的判据会把时钟与字号都拖进来）。
     */
    @Test
    fun `真的统计条挂在带子里面，它那一行里的竖向拖动照样移动窗口`() {
        val moves = mutableListOf<Pair<Float, Float>>()
        mount(
            moves,
            statFields = listOf(
                LbMetric("今日", "¥12345.678"),
                LbMetric("本次", "¥9876.543"),
                LbMetric("首字", "123.4s"),
                LbMetric("累计", "123456次"),
                LbMetric("已统计", "¥8765.432")
            )
        )

        val bandNode = nodeOf(DRAG_HANDLE_HIT_TAG)
        assertTrue(
            "统计条必须是命中带的后代（并排躺着就会被兄弟命中挡在带子之外）：" +
                tagsUnder(bandNode).joinToString(),
            tagsUnder(bandNode).contains(UsageStatTags.BAR)
        )
        val bar = rectOf(UsageStatTags.BAR)
        val slot = rectOf(DRAG_HANDLE_SLOT_TAG)
        assertTrue(
            "统计那一行仍住在可见细槽的垂直中心上（位置漂了就是把带子画粗了另一种写法）：" +
                "bar center=${(bar.top + bar.bottom) / 2f} slot center=${(slot.top + slot.bottom) / 2f}",
            kotlin.math.abs((bar.top + bar.bottom) / 2f - (slot.top + slot.bottom) / 2f) <= 1.5f
        )

        dragInside(UsageStatTags.BAR, Offset(bar.width / 2f, bar.height / 2f), 0f, 10f * density)
        assertTrue("真实统计那一行里的竖向拖动要移动窗口，实到 ${moves.size} 帧", moves.isNotEmpty())
    }

    // ═══════════ 仪器 ═══════════

    /**
     * 只借生产那一格的**手势形状**的一枚探针：子层、`fillMaxWidth`、按一行高溢出那只 4dp 细槽，
     * 而且**只装横向**拖拽（与 `UsageStatBar` 那一颗同一档归属）。
     * 用探针而不用真组件判归属三句，是因为这一格要的是"一定装着手势、计数一定读得出"，
     * 真组件装不装手势由量宽结果决定（真组件那一面归下面那一格与 `stats/` 那一族）。
     */
    @Composable
    private fun StatRowProbe(onHorizontalDrag: () -> Unit, modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .testTag(STAT_ROW_TAG)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { onHorizontalDrag() }
                )
        ) {
            // 字号档与生产那一颗一致（labelSmall）：探针的行高要是别的档，上面那三句注入的
            // "行内 / 行外"落点就量的不是页面真实那一段重叠
            Text(text = "今日 ¥1.230 本次 ¥0.500 累计 42次", style = AppTypography.labelSmall)
        }
    }

    /** 生产源文件本体（判据读的是来路与形状，不是编译产物） */
    private fun bandSource(): String = readProduction("ui/panel/DragHandle.kt")

    private fun statBarSource(): String = readProduction("ui/panel/stats/UsageStatBar.kt")

    private fun readProduction(relative: String): String {
        val file = java.io.File("src/main/java/com/lovebrain/app/$relative").takeIf { it.isFile }
            ?: java.io.File("app/src/main/java/com/lovebrain/app/$relative")
        assertTrue("找不到 $file——这把尺会恒绿", file.isFile)
        return file.readText(Charsets.UTF_8)
    }

    private companion object {
        const val HEADER_SLOT_TAG = "drag_handle_header_probe"
        const val STAT_ROW_TAG = "drag_handle_stat_row_probe"
    }
}
