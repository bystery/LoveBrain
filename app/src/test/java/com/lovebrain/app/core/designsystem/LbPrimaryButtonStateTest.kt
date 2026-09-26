package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 表里的 `LbPrimaryButton`：四态是不是**真的**一旋钮，以及四态占不占同一个盒子。
 *
 * 替代的旧实现把状态摊在 `mode` + `enabled` 两个参数上，那种形状有两个死角：
 * `STOP + enabled=false` 类型上合法却没人定义过；`Disabled` 又只是 NORMAL 的一个 if。
 * 这一组用例钉的是**换成的那一颗旋钮**在语义树上的表现，全走 `boundsInRoot` 与
 * 语义属性，不看源码里的数字（P1-02 点名的就是这个区别）。
 *
 * 第二件事是 §6.4 那句「模式切换只改变内容区，**不移动主要输入和主操作按钮**」里
 * 属于这颗按钮的半边：四态的 bounds 必须逐像素相同。
 * 原来这句话没人能证——生成一次要真在设备上跑一次。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbPrimaryButtonStateTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private val state: MutableState<LbButtonState> = mutableStateOf(LbButtonState.Idle)
    private var clicks = 0

    /**
     * 一次 setContent 挂四态：本仓库的仪器要求每个测试只挂一次，换态靠 hoisted 状态。
     *
     * `fillMaxWidth()` 是**故意跟着**的：这颗按钮自己不铺满，它按内容宽。
     * 实到证据（同一台仪器、不给宽度约束时量到的）：Idle=63dp、Loading=120dp、
     * Disabled=102dp、Stop=71dp——换态就把主操作横向撑来撑去。所以 §6.4 那句
     * "模式切换不移动主操作按钮"是**调用方 + 组件**的联合性质：槽位宽度由调用方钉死，
     * 四态才谈得上"同一个盒子"。生产那五个分支全都带 `fillMaxWidth()` 或 `weight(1f)`，
     * 这里按同一种形状测。
     */
    private fun mountPrimary() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbPrimaryButton(
                    state = state.value,
                    label = labelFor(state.value),
                    onClick = { clicks++ },
                    modifier = Modifier.fillMaxWidth().testTag("PRIMARY_SLOT")
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun labelFor(s: LbButtonState) = "LBL_" + s.name.uppercase()

    /**
     * 与 [mountPrimary] 同形，但**调用方不再往这颗按钮身上贴自己的 tag**。
     *
     * 实到证据（`ZzStopTagProbeTest` 探针 dump 出来的语义树）：同一个节点上出现两个
     * `Modifier.testTag` 时，**外面那个（调用方的）赢**——组件自己贴的
     * `LbTags.PRIMARY_STOP` 直接消失，合并树和未合并树都查不到。
     * 生产里浮层这条链（`LoveBrainPanelScreen.kt:428` → `ReplyPrimaryActions.kt:54+`）
     * 只往下传 `fillMaxWidth()`，没有撞名；首页 `HomeComponents.kt:238` 是撞的那一个，
     * 它把 `LbHomeTags.PRIMARY_BUTTON` 贴在了同一颗节点上。
     * 所以"停止锚点在不在树上"这一条必须按生产的形状测，不能被夹具自己的 tag 顶掉。
     */
    private fun mountBare() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbPrimaryButton(
                    state = state.value,
                    label = labelFor(state.value),
                    onClick = { clicks++ }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun show(next: LbButtonState) {
        rule.runOnIdle { state.value = next }
        // Loading 带无限脉冲动画：自动时钟下 waitForIdle 永远等不到空闲，手动推几帧
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
    }

    private fun targets() = probe.actionableTargets(rule, "主动作")

    @Test
    fun `the four states share one box so switching state cannot move the primary action`() {
        mountPrimary()
        val seen = LinkedHashMap<String, String>()
        LbButtonState.values().forEach { s ->
            show(s)
            val t = targets().single()
            seen[s.name] = "%.1f/%.1f/%.1f/%.1f".format(t.leftDp, t.topDp, t.widthDp, t.heightDp)
        }
        assertEquals(
            "四态占的盒子必须完全相同（换态不许把主操作挪走）：" + seen.entries.joinToString { "${it.key}=${it.value}" },
            1, seen.values.distinct().size
        )
        // 反空跑：盒子本身也得真量到东西，不是四个 0/0/0/0 相同
        val t = targets().single()
        assertTrue("宽度应当铺满 360dp 槽位，实到 ${t.widthDp}", t.widthDp > 300f)
    }

    /** §6.5 :531 ——四态每一态的可点击盒子都得过 48dp 下限 */
    @Test
    fun `every state meets the touch floor`() {
        mountPrimary()
        LbButtonState.values().forEach { s ->
            show(s)
            val got = probe.assertAllActionableMeetTouchFloor(rule, "主动作·${s.name}")
            assertEquals("${s.name} 应当只有一颗主动作", 1, got.size)
            assertEquals("${s.name} 的高度应当正好是下限", 48f, got.single().heightDp, 0.6f)
            // ⚠ 这一条是**搬首页那颗主按钮时量出来的缺陷**补进来的：
            // Material `Button` 自带 role=Button，而本组件是手画 Box + clickable，
            // 四态**全都没声明角色**（`role=无`）。搬过来那天语义树就从 Button 掉回无——
            // "换成设计系统的组件"这一步自己引入了 §6.5 :532 的回归，
            // 只有把两边的性质都量一遍才会发现（读代码读不出来，Material 那侧的角色不在源码里）。
            assertEquals(
                "${s.name} 必须在语义树里说得出自己是按钮：" + got.single().describe(),
                "Button", got.single().role
            )
        }
    }

    /**
     * 短标签那颗也得是**见方**的热区——不靠调用方记得加 `fillMaxWidth()`。
     *
     * 起因是一条实测红：`KbEditScreen` 编辑态那颗「保存」搬进本组件之后，
     * 语义树量到 **33x48dp**。组件原来只写 `.height(48)`，宽度按内容走，
     * 于是英文短标签（"Save"）自己就不够 48——:596 那句"无小于 48dp 的热区"
     * 判的是两条边，不是只判高度。
     * 这与 `LbTextAction` 的来历同一课（它第一版也只垫高度，被自家测试量出 40x48dp），
     * 差别在于这次把它写回**组件本身**：四态共用同一个下限，调用方拿不到"只设高度"的旋钮。
     */
    @Test
    fun `a short label still leaves a square hot zone in every state`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                // 故意**不给**宽度约束：这一格判的就是组件自己的下限
                LbPrimaryButton(
                    state = state.value,
                    label = "Save",
                    onClick = { clicks++ }
                )
            }
        }
        LbButtonState.values().forEach { st ->
            show(st)
            val t = targets().single()
            assertTrue(
                "${st.name} 那一态短标签的热区不到 48dp 见方：" + t.describe(),
                !t.tooSmall(probe.floorDp)
            )
        }
    }
    /**
     * **横向**内边距也是组件的性质，不是调用方的自觉。
     *
     * 上一条格子（`a short label…`）补的是"宽度有一条下限"，补完仍然留着一个洞：
     * 组件里只有 `padding(vertical = Spacing.xs)`，**横向一条都没有**。
     * 于是标签只要宽过 48dp，盒子的宽度就等于文字的宽度——字直接贴在品牌色底色的边上。
     * 首页那颗唯一主按钮（`HomeComponents`，不给宽度约束、按内容排）撞的就是这一档：
     * 归位之前那颗 Material `Button(containerColor = Primary)` 量到 **119x48dp**（`4ee1514` 记的），
     * 归位之后同一颗量到 **盒 87x48dp / 字 87x18dp** ⇒ 左右各 0dp。
     * ⚠ 那 32dp 宽度差的**成因没直接量过**（Material 那侧的内边距不在本仓库源码里，
     * 本机只量过总宽）；能量到的只有两件事：归位之后组件不留任何横向内边距，
     * 以及补上 16dp×2 之后总宽正好回到 119。**"改用统一组件这一步自己把主按钮缩了一圈"是量出来的，
     * 至于缩的正是 Material 原来给的那份，那是解释、不是读数**（与 `4ee1514` 那次角色掉档同一族）。
     *
     * 判据刻意不钉"盒子该多宽"（那由标签文案决定，换个语言就红），钉的是
     * **盒子比标签多留出的量**：左右合计不得少于 24dp（每侧 12dp = 本系统最小的按钮类内边距
     * `Spacing.lg`，见 `LbTextAction`）。组件以后要更宽松照样绿，要收回 0 当场红。
     */
    @Test
    fun `a content sized button keeps horizontal room around its label`() {
        val longLabel = "Generate my reply"
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                // 故意**不给**宽度约束：这一格判的就是"按内容排"那一档的组件自己
                LbPrimaryButton(state = state.value, label = longLabel, onClick = { clicks++ })
            }
        }
        show(LbButtonState.Idle)
        val box = targets().single()
        val inkNodes = rule.onAllNodes(hasText(longLabel), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("标签节点应当唯一（不唯一就说明这格没在判那颗按钮）：", 1, inkNodes.size)
        val ink = probe.of(inkNodes.single())
        val slack = box.widthDp - ink.widthDp
        assertTrue(
            "标签左右合共只留出 ${slack.toInt()}dp（盒 ${box.widthDp.toInt()} − 字 " +
                "${ink.widthDp.toInt()}）——字贴在底色边上。下限 24dp，" +
                "这条性质在组件里，不在调用方：" + box.describe(),
            slack >= 24f
        )
    }

    /** 合同第 1 行的那半个：禁用是"灰着不能点"，不是消失 */
    @Test
    fun `disabled stays in place and announces itself disabled`() {
        mountPrimary()
        show(LbButtonState.Disabled)
        val t = targets().single()
        assertEquals("禁用态还得画得出来", labelFor(LbButtonState.Disabled), t.label)
        assertTrue("必须带 Disabled 语义，读屏才知道念\"不可用\"：" + t.describe(), t.disabled)

        show(LbButtonState.Idle)
        assertTrue("同一条链上的 Idle 不该带 Disabled：" + targets().single().describe(),
            !targets().single().disabled)
    }

    /** 三态点下去都走那颗回调；Disabled 点下去什么都不该发生 */
    @Test
    fun `tapping works in the three live states and does nothing when disabled`() {
        rule.mainClock.autoAdvance = false
        mountPrimary()
        listOf(LbButtonState.Idle, LbButtonState.Stop, LbButtonState.Loading).forEach { s ->
            show(s)
            val before = clicks
            rule.onNodeWithTag("PRIMARY_SLOT").performClick()
            repeat(3) { rule.mainClock.advanceTimeByFrame() }
            assertEquals("$s 点一次应当恰好触发一次回调", before + 1, clicks)
        }
        show(LbButtonState.Disabled)
        rule.onNodeWithTag("PRIMARY_SLOT").performClick()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        assertEquals("Disabled 点下去不许触发回调，实到 $clicks 次", 3, clicks)
    }

    /** 停止锚点只属于"生成中"那一态：别的态冒出可停止的节点，自动化就会点错 */
    @Test
    fun `only the loading state carries the stop anchor`() {
        mountBare()
        LbButtonState.values().forEach { s ->
            show(s)
            val found = rule.onAllNodesWithTag(LbTags.PRIMARY_STOP, useUnmergedTree = true)
                .fetchSemanticsNodes().size
            val want = if (s == LbButtonState.Loading) 1 else 0
            assertEquals("$s 的停止锚点数", want, found)
        }
    }

    /**
     * 设备侧那三格红的形状，本机第一次把它钉住：`onNodeWithTag`/`onAllNodesWithTag`
     * **默认查的是合并后的语义树**，而 LOADING 那颗 `clickable` Box 会把后代合并进自己。
     * tag 原先只挂在被合并掉的子 Text 上 ⇒ 合并树里根本没有带这个 tag 的节点，
     * CI run 36214822274 上 `OverlayGenerateSmokeTest > successStream_…` /
     * `stopDuringGeneration_…` 与 `ReplyPrimaryActionsTest >
     * generating_showsProductionLoadingStopAffordance…` 三格都停在
     * 「断言「已显示」失败 / 节点：节点不存在（fetchSemanticsNode 失败）」。
     * 上一格用 `useUnmergedTree = true` 查，量的恰好是合并**之后**看不见的另一半，
     * 所以本机一直绿、设备一直红。
     */
    @Test
    fun `the stop anchor is reachable in the merged tree the device queries`() {
        mountBare()
        show(LbButtonState.Loading)

        val merged = rule.onAllNodesWithTag(LbTags.PRIMARY_STOP).fetchSemanticsNodes()
        assertEquals(
            "合并树里也必须恰有 1 颗停止条——设备侧默认就查合并树，" +
                "tag 挂在被合并掉的子节点上设备就找不到它：实到 ${merged.size} 颗",
            1, merged.size
        )

        // 设备侧不止"找得到"：它读这条语义文本校验 LOADING 文案，再对这个节点注入点击
        val texts = merged.single().config
            .getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
        assertTrue(
            "合并树里那颗停止条必须自己带 LOADING 文案（设备侧按 Text.first() 校验），实到：$texts",
            texts.contains(labelFor(LbButtonState.Loading))
        )

        val before = clicks
        rule.onNodeWithTag(LbTags.PRIMARY_STOP).performClick()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        assertEquals("按停止条的 tag 点下去应当恰好触发一次回调", before + 1, clicks)
    }
}
