package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbAsyncTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.viewmodel.SetupViewModel
import com.lovebrain.app.viewmodel.SetupViewModel.CaptureApp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 第6节第3条 收尾：捕获范围这一页**整页**接在共用四态出口上（此前这页一格用例都没有）。
 *
 * 挂的是 `CaptureAppsScreen` 本身而不是子组件，因为这格要证的三件事都在页面上：
 * 1. 四格真的走 [com.lovebrain.app.core.designsystem.LbAsyncState]（自造那张 inset 卡片已删）；
 * 2. 错误态那颗重试**确实又枚举了一次**（数 `selectableCaptureTargets` 的调用次数，
 *    不是"画了个按钮"就算通过——这是本仓库反复栽过的"钉磁盘不钉返回值"同族）；
 * 3. 判据搬进 mapper 之后，勾选这条路仍然落到 `setCaptureAllowed(包名, true)`。
 *
 * 状态源用一份 mockk 的 SetupViewModel：null / 空表 / 两份列表都给得出，
 * 而页面拿到的仍是真 `StateFlow`，`collectAsStateWithLifecycle()` 走的是它平时的路。
 * 断言里要比对文案时一律 `getString(资源 id)`，不抄中文字面量（中英任一边改版式都不会假红）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureAppsScreenStatesTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val density: Float
        get() = app.resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val alfred = CaptureApp(packageName = "PACKET_ALFRED", displayName = "LABEL_ALFRED", secondRejected = false)
    private val bear = CaptureApp(
        packageName = "PACKET_BEAR", displayName = "LABEL_BEAR", secondRejected = true
    )

    private fun vm(
        candidates: List<CaptureApp>?,
        allowed: Set<String> = emptySet(),
        disclosure: Boolean = false
    ): SetupViewModel = mockk<SetupViewModel>(relaxed = true).also {
        every { it.captureAllowedPackages } returns MutableStateFlow(allowed)
        every { it.captureEnabled } returns MutableStateFlow(false)
        every { it.isCaptureServiceEnabled(any()) } returns true
        every { it.selectableCaptureTargets(any()) } returns candidates
        // 这一颗必须显式桩：披露闸门（captureSwitchGateStep）把它当输入，"没桩=relaxed 返回 false"
        // 与"这台机器真的没同意过"在判据上两回事。
        every { it.isAccessibilityDisclosureConfirmed() } returns disclosure
    }

    private fun mount(model: SetupViewModel) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                CaptureAppsScreen(viewModel = model, onBack = {})
            }
        }
        rule.waitForIdle()
        awaitCaptureBatch()
    }

    /**
     * 等装配那一批 IO 读数**真的落位**，再把控制权交回用例。
     *
     * ⚠ 这条等待是必需的，不是保险：`CaptureAppsScreen` 把四颗读数放进 `Dispatchers.IO`
     * （:215），而 `waitForIdle()` **不跟踪后台线程上的协程**——同族的两颗姊妹用例
     * （`CaptureAppsScreenTruthTest:124`、`HomeStatusCaptureDisclosureTest:146`）头部把这件事
     * 写得明明白白："同一条用例两次跑出两种颜色，那样的读数不能拿来定案"。
     * 本文件此前只靠 `waitForIdle`，CI 机器一忙，`no candidates at all…` 就在
     * `assertEquals(1, messageCount())` 上数到 0（转圈还在）——2026-10-09 run 37926106535 的红就是它。
     *
     * 定点选"转圈消失"而不是"某一行的文字出现"：生产把 `scanning = false` 排在整批赋值的
     * **最后一颗**（:213 的注释同一条理由），所以 `loading = scanning` 落下来就意味着
     * 四颗读数都已就位；而空态/错误态根本没有那一行，文字锚点在这些格子里不成立。
     */
    private fun awaitCaptureBatch() {
        // 本仓这一版 Compose 的 `waitUntil` 不交回布尔（同族两颗姊妹用例也只调用、不取值），
        // 所以等完再独立判一次：没落位就红在这里，而不是带着转圈往下走。
        rule.waitUntil(WAIT_FOR_BATCH_MS) { spinnerCount() == 0 }
        val left = spinnerCount()
        assertTrue(
            "装配批次在 ${WAIT_FOR_BATCH_MS}ms 内没落位（转圈仍 $left 颗）：" +
                "这一屏每一格的状态断言都建立在「这一批已落」之上",
            left == 0
        )
    }

    /**
     * 第6节第5条 矩阵：把这一屏从固定 360dp 推到 4 宽 × 3 字号（`UiMatrix.FULL`）。
     * 量具，不是闸：热区 < 48dp、越槽只登记不改码（工单明文），打 stdout 进交付表。
     * 硬断言只有哨兵（十二格各量到一次）+ 样本下限（防空转）。
     * 换格子靠 hoisted 状态（一个用例只 `setContent` 一次，同 `UiMatrixFullSweepTest`）。
     */
    private val matrixCell: MutableState<UiMatrix> = mutableStateOf(UiMatrix.FULL.first())

    private fun mountSweep(model: SetupViewModel) {
        rule.setContent {
            matrixCell.value.RenderIn(LocalDensity.current.density) {
                CaptureAppsScreen(viewModel = model, onBack = {})
            }
        }
        rule.waitForIdle()
        awaitCaptureBatch()
    }

    private fun useCell(next: UiMatrix) {
        rule.runOnIdle { matrixCell.value = next }
        rule.waitForIdle()
    }

    @Test
    fun `the touch floor is recorded across four widths and three font scales`() {
        val model = vm(candidates = listOf(alfred, bear), allowed = setOf("PACKET_ALFRED"))
        mountSweep(model)
        val table = StringBuilder()
        val visited = LinkedHashMap<String, String>()
        var judged = 0
        for (m in UiMatrix.FULL) {
            useCell(m)
            val targets = probe.actionableTargets(rule, "捕获范围页·${m.id}")
            val inside = targets.filter {
                it.widthDp > 0f && it.heightDp > 0f &&
                    it.leftDp >= 0f && it.topDp >= 0f &&
                    it.leftDp + it.widthDp <= m.widthDp + 0.5f &&
                    it.topDp + it.heightDp <= m.heightDp + 0.5f
            }
            judged += inside.size
            val small = inside.filter { it.tooSmall(probe.floorDp) }
            val over = targets.filter { it.leftDp + it.widthDp > m.widthDp + 0.5f }
            table.append("PROBE64 捕获范围页·${m.id} 判 ${inside.size}/${targets.size} 颗；")
                .append("热区<${probe.floorDp.toInt()}dp ${small.size} 颗：")
                .append(small.joinToString(" | ") { it.describe() }).append("；")
                .append("越槽 ${over.size} 颗：")
                .append(over.joinToString(" | ") { it.describe() }).append('\n')
            visited[m.id] = "${inside.size}/${targets.size}"
        }
        println(" 矩阵·捕获范围页·实到读数（4 宽 × 3 字号）")
        println(table.toString())
        assertEquals(
            "矩阵只量到 ${visited.size} 格（应为 ${UiMatrix.FULL.size}）：${visited.keys}" +
                " —— 覆盖面不能靠循环写法自称",
            UiMatrix.FULL.map { it.id }, visited.keys.toList()
        )
        assertTrue(
            "十二格一共只判到 $judged 颗，低于样本下限 $MIN_SWEEP_JUDGED —— 这格在空转，" +
                "别把它读成「全达标」",
            judged >= MIN_SWEEP_JUDGED
        )
    }

    companion object {
        /** 十二格至少该判到这么多颗；低于它就是这格没扫到东西，不是「全达标」 */
        private const val MIN_SWEEP_JUDGED = 12

        /** 等装配那批 IO 落位的上限：体例照同族两颗姊妹用例（10 秒，见 [awaitCaptureBatch]） */
        private const val WAIT_FOR_BATCH_MS = 10_000L
    }

    private fun messageCount() =
        rule.onAllNodesWithTag(LbAsyncTags.MESSAGE).fetchSemanticsNodes().size

    private fun spinnerCount() =
        rule.onAllNodesWithTag(LbAsyncTags.LOADING).fetchSemanticsNodes().size

    @Test
    fun `an enumeration failure shows an error with a retry that really enumerates again`() {
        val model = vm(candidates = null)
        mount(model)
        assertEquals("读失败这一格要走共用错误态", 1, messageCount())
        // 新形状（本轮给这一页加了 resume 重读之后）：mount = 初始装配 1 次 + 挂观察者时宿主已 RESUMED
        // 即时派发的那次 ON_RESUME 再 1 次 = 2 次。这两次都在**装配期**、与重试无关；
        // 重试"确实又枚举一次"由下面那次 2→3 的增量单独钉，不动这条原语义。
        verify(exactly = 2) { model.selectableCaptureTargets(any()) }

        val target = probe.of(
            rule.onNodeWithTag(LbAsyncTags.ACTION).fetchSemanticsNode("错误态没有重试入口")
        )
        assertEquals("重试必须带按钮角色：" + target.describe(), "Button", target.role)
        assertTrue(
            "重试热区不足 ${probe.floorDp.toInt()}dp：${target.describe()}",
            !target.tooSmall(probe.floorDp)
        )

        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        rule.waitForIdle()
        // 重试那一拍同样走 IO：不等落位就去数 `exactly = 3`，快机上碰对、慢机上停在 2（同一族的红）
        awaitCaptureBatch()
        // 重试真的再枚举一次：在装配期那 2 次之上再涨 1（→ 3）。重试坏掉不枚举就停在 2，红。
        verify(exactly = 3) { model.selectableCaptureTargets(any()) }
    }

    /** 同一份枚举结果换成"空表"，语气必须从错误变成空——这两格分不开就是本轮修掉的那个谎 */
    @Test
    fun `no candidates at all is an empty state, not an error`() {
        mount(vm(candidates = emptyList()))
        assertEquals(1, messageCount())
        rule.onNodeWithText(app.getString(R.string.capture_apps_none_to_authorize)).assertExists()
        rule.onNodeWithTag(LbAsyncTags.ACTION).assertDoesNotExist()
        assertEquals("空态不许画转圈", 0, spinnerCount())
    }

    @Test
    fun `a query that matches nothing says so without reusing the no-apps sentence`() {
        mount(vm(candidates = listOf(alfred)))
        rule.onNodeWithText("LABEL_ALFRED").assertExists()

        rule.onNode(hasSetTextAction()).performTextInput("zzz")
        rule.waitForIdle()

        assertEquals(1, messageCount())
        rule.onNodeWithText(app.getString(R.string.capture_apps_no_results)).assertExists()
        assertNotEquals(
            "「没搜索到」与「整机没有可授权的 App」共用一句话，就等于对用户少说一件事",
            app.getString(R.string.capture_apps_none_to_authorize),
            app.getString(R.string.capture_apps_no_results)
        )
        rule.onNodeWithText("LABEL_ALFRED").assertDoesNotExist()
    }

    @Test
    fun `rows still hand the package name and the new state to the view model`() {
        val model = vm(candidates = listOf(alfred, bear), allowed = emptySet())
        mount(model)

        rule.onNodeWithText("LABEL_ALFRED").performClick()
        rule.waitForIdle()
        verify(exactly = 1) { model.setCaptureAllowed("PACKET_ALFRED", true) }
    }

    /**
     * 这一页顶上那颗捕获总开关：点一次只切一次，**并且不重扫安装包**。
     *
     * 两个反例都要它红：
     * ① 界面回退成"这一页没有开关，去首页开" → 语义树里找不到那颗带名字的开关，红；
     * ② 每次点击都顺手把 `queryIntentActivities` 再跑一遍（本轮要修的就是这条）
     *   → 在装配期那 2 次（初始 1 + resume 即时 1）之上再涨到 3 次，红。
     *
     * 本轮加了 resume 重读，装配期读数从 1 变 2——那是**新形状**（观察者对已 RESUMED 的宿主
     * 同步补发 ON_RESUME，与 `HomeScreen.kt` 同一族写法），不是重复订阅：`DisposableEffect`
     * 只 addObserver 一次、onDispose 只 removeObserver 一次。原语义"拨开关不重扫已装应用"照旧钉死
     * （点击之后不许从 2 涨到 3），一颗 verify 都不丢。
     */
    /**
     * 这台机器**没有这一版的同意记录**时，拨总开关不许直接写开关。
     *
     * 钉的是本轮 P0（"无障碍捕获完全失效"那一串）修好之后的闸门顺序：
     * `captureSwitchGateStep` 里"未同意"排在"权限已授予"之前——权限给了不能替用户同意顶缺，
     * 所以这一格的正确下落是**先看披露长文**，三样写操作一颗都不许动。
     * 反例（改坏就红）：把 `!disclosureConfirmed` 那格并回 `accessibilityGranted` 之后
     * → 这里 `toggleCapture` 变 1、弹窗变 0；弹窗一出现就顺手 `confirmAccessibilityDisclosure()`
     * → 那是替用户同意，confirm 变 1。
     */
    @Test
    fun `switching on with no agreement record for this version opens the disclosure and writes nothing`() {
        val model = vm(candidates = listOf(alfred), disclosure = false)
        mount(model)

        val switch = rule.onAllNodes(
            hasClickAction() and hasContentDescription(app.getString(R.string.capture_apps_allow))
        )
        assertEquals(
            "这一屏该有恰好一颗说得出「允许捕获」（走资源，不抄中文）的开关",
            1, switch.fetchSemanticsNodes().size
        )
        switch[0].performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(LbCaptureTags.DISCLOSURE).assertExists()
        verify(exactly = 0) { model.toggleCapture() }
        verify(exactly = 0) { model.confirmAccessibilityDisclosure() }
        verify(exactly = 0) { model.setCaptureAllowed(any(), any()) }
    }

    /**
     * 这一版**已经明确同意过**（且权限真授予了）时，拨开关才落到 `toggleCapture()` 恰好一次，
     * 并且**不重扫已装应用**——原语义留在这一格。
     *
     * 装配期读数 = 初始 1 次 + 挂观察者时宿主已 RESUMED 即时派发的 ON_RESUME 再 1 次 = 2 次，
     * 两次都在拨开关之前；拨完仍停在 2（涨到 3 就是开关那一路多开了一条读取通道）。
     */
    @Test
    fun `the master switch toggles once and does not rescan installed apps`() {
        val model = vm(candidates = listOf(alfred), disclosure = true)
        mount(model)
        // 装配期 = 初始 1 + resume 即时 1 = 2 次；这 2 次都发生在拨开关之前。
        verify(exactly = 2) { model.selectableCaptureTargets(any()) }

        val switch = rule.onAllNodes(
            hasClickAction() and hasContentDescription(app.getString(R.string.capture_apps_allow))
        )
        assertEquals(
            "这一屏该有恰好一颗说得出「允许捕获」（走资源，不抄中文）的开关",
            1, switch.fetchSemanticsNodes().size
        )
        switch[0].performClick()
        rule.waitForIdle()

        verify(exactly = 1) { model.toggleCapture() }
        rule.onNodeWithTag(LbCaptureTags.DISCLOSURE).assertDoesNotExist()
        // 原语义仍钉：拨开关**不新增读取**——停在装配期那 2 次，不涨到 3。
        verify(exactly = 2) { model.selectableCaptureTargets(any()) }
    }

    /**
     * 这一屏每个可交互节点都过 第6节第5条 那把尺（页头那颗 32dp 就是上一格这么量出来的，账本 第20节第4条）。
     *
     * 勾选行"现在有没有被勾上"念不念得出来是 第6节第5条 第②栏的账，**这格只挂不判**：
     * 判据写在别的账里，别在这里顺手扩范围。
     *
     * ⚠ 顶上那颗**搜索输入框**不参加这一把 48 尺，理由是量不到而不是放宽：语义树里它是
     * 那一行字（本机实量「Search apps」288x15dp @(36,178)），36dp 的可见框与外层那颗 48dp 的
     * 热区盒都没有语义节点（热区盒只转焦点、不声明点击）。这里改成：输入框那一族单独数得出
     * 恰好一颗且说得出资源里那一句，其余（行、页头出口、错误态那颗重试）一颗不落按全站 48 判。
     */
    @Test
    fun `every interactive node on the list screen meets the touch and labeling floor`() {
        mount(vm(candidates = listOf(alfred, bear), allowed = setOf("PACKET_ALFRED")))

        val targets = probe.actionableTargets(rule, "捕获范围页")
        val fields = targets.filter { it.editable }
        assertEquals(
            "这一页顶上应当恰好一颗搜索输入框，实到 " + fields.joinToString { it.describe() },
            1, fields.size
        )
        assertTrue(
            "搜索框要说得出资源里那一句（这台 JVM 解析成英文，判据不许抄中文）：" +
                fields.single().announced,
            fields.single().contentDescriptions.contains(app.getString(R.string.capture_apps_search))
        )
        assertTrue(
            "搜索框宽到挤成一条就没法打字了：" + fields.single().describe(),
            fields.single().widthDp + 0.5f >= TouchTier.SITE_FLOOR
        )
        probe.assertTargetsMeetFloor(
            targets - fields.toSet(), TouchTier.SITE_FLOOR,
            "捕获范围页·行与出口（输入框那一族另判）"
        )
        probe.assertAllActionableLabeled(rule, "捕获范围页")
        probe.assertNoDuplicatedAnnouncement(rule, "捕获范围页")
        assertTrue(
            "只量到 ${targets.size} 个可交互节点，入口大概被画没了",
            targets.size >= 3
        )
    }
}
