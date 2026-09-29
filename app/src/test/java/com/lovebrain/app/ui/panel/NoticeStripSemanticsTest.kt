package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LbAsyncTags
import com.lovebrain.app.core.designsystem.LbStateTone
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.ui.panel.reply.ResultArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 面板里那一条"带语气浅底的状态条"归进 `LbEmptyState(container = Strip)` 之后的读数账。
 *
 * 归并之前这两族的形状是各页自己画的：四处错误条写作 `Box + .background(ErrorBg) + 居中 Column`，
 * 两处通知条写作 `Row + .background(SuccessBg/WarningBg)`。语义树读不出底色（那是截图那一格的职责），
 * 但读得出**这一条上那颗操作点不点得到、报不报得出自己是什么**——而这一族当时恰好都缺：
 *  - `InputChangedBanner` 尾部那颗「按新输入生成」是裸 `Text.clickable`，**role=无**；
 *  - `KbNoticeBanner` 那颗关闭的 `Box.clickable` 同样**没声明 role**，而且它的
 *    `contentDescription` 是一串**内联中文**（英文环境下读屏照念中文，资源里那条
 *    `a11y_close_notice` 一直存在却从没被引用过）。
 * 这两处现在都走设计系统里唯一的文字动作，所以这一格判的是补齐之后的读数，不是"还在欠"。
 *
 * 四件事各自有反面（把生产改回自画、把动作删掉、给空态长出入口、换语气换热区，都会红）：
 * 1. 通知条那颗关闭：**恰好一颗**可点、报 `Button`、两轴都到下限、名字来自资源、点一次回调一次；
 * 2. 输入已变化那条：动作同样报 `Button` 且热区达标，说明文字由组件画（带 `contentDescription` 锚点）；
 * 3. 错误条那一档：错因整句进语义树，且画它的是组件（页面自画的那一颗没有 MESSAGE 锚点）；
 * 4. 主动发失败那一档**不给第二颗出口**：这一屏的主操作是下面那颗「生成开场」。
 *
 * ⚠ 一个诚实的边界：这三条话在本机**都是资源/VM 给的**，测试里用的是中文原文——
 * 英文环境下 `a11y_close_notice` 解析成 "Dismiss notice"，所以锚点一律 `getString` 取，
 * 不写死（CI run 36019334520 红掉的那 12 格就是这个形状）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoticeStripSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private val density: Float get() = ctx.resources.displayMetrics.density

    /** 下限不自己抄一个数：指回全站唯一那一颗（同 `EmptyStateOwnershipSemanticsTest`） */
    private val probe by lazy {
        SemanticsProbe(density, AppDimens.TOUCH_TARGET_MIN_DP.toFloat())
    }

    /** 三条真实存在的通知文本（前两条就是 VM 现在会推出去的那两句） */
    private val noticeText = "已暂停本轮提及，下次生成将过滤此条记忆"
    private val errorText = "网络断了，这轮没生成成"

    private val closeLabel: String get() = ctx.getString(R.string.a11y_close_notice)
    private val retryLabel: String get() = ctx.getString(R.string.panel_retry_tap)
    private val changedText: String get() = ctx.getString(R.string.panel_input_changed)
    private val regenLabel: String get() = ctx.getString(R.string.panel_regenerate_with_new_input)

    // ═══════════ 挂载 ═══════════

    private fun mountNotice(fired: () -> Unit) {
        rule.setContent {
            UiMatrix(360, 400).RenderIn(LocalDensity.current.density) {
                KbNoticeBanner(text = noticeText, tone = LbStateTone.Success, onDismiss = fired)
            }
        }
        rule.waitForIdle()
    }

    private fun mountResult(result: GenerateResult?, onRegenerate: () -> Unit = {}) {
        rule.setContent {
            UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                ResultArea(
                    result = result,
                    isGenerating = false,
                    streamingCoreText = "",
                    isGeneratingCore = false,
                    streamingSchemes = emptyList(),
                    feedbacks = emptyMap(),
                    onFeedback = { _, _ -> },
                    onCopyScheme = {},
                    onRetry = {},
                    providerReady = true,
                    onOpenSettings = {},
                    inputChanged = true,
                    onRegenerateWithNewInput = onRegenerate
                )
            }
        }
        rule.waitForIdle()
    }

    private fun mountProactive(error: String?) {
        rule.setContent {
            UiMatrix(360, 600).RenderIn(LocalDensity.current.density) {
                ProactiveResultArea(
                    isProactive = false,
                    options = emptyList(),
                    error = error,
                    onCopy = {}
                )
            }
        }
        rule.waitForIdle()
    }

    /** 组件那颗说明节点（页面自画的那一颗没有这个 tag，也没有 contentDescription） */
    private fun messageNodes() = rule.onAllNodesWithTag(LbAsyncTags.MESSAGE, useUnmergedTree = true)
        .fetchSemanticsNodes().map { probe.of(it) }

    // ═══════════ ① 面板顶部那条通知 ═══════════

    /**
     * 那颗关闭：一个名字、一个角色、两轴热区、点得到。
     *
     * 归并之前它**只有热区**（48x48 的盒），`clickable` 没写 role ⇒ 读屏念得出"关闭通知"、
     * 说不出它是按钮；而那句名字当时是内联中文。两条都在这格里钉住。
     */
    @Test
    fun `the notice strip offers exactly one named dismiss that reads as a button`() {
        var fired = 0
        mountNotice { fired++ }
        val targets = probe.actionableTargets(rule, "面板·通知条")
        assertEquals(
            "通知条上只该有那一颗关闭：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        val way = targets.single()
        assertEquals(
            "关闭那颗要报 Button（裸 Box.clickable 读屏不会念成按钮）：" + way.describe(),
            "Button", way.role
        )
        assertEquals(
            "名字必须来自资源，不许是内联中文（英文环境下要念成 Dismiss notice）：" + way.describe(),
            closeLabel, way.label
        )
        assertTrue(
            "热区两轴都要 ≥${probe.floorDp.toInt()}dp，只垫高度不算达标：" + way.describe(),
            !way.tooSmall(probe.floorDp)
        )
        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        rule.waitForIdle()
        assertEquals("按一次就该回调一次", 1, fired)
    }

    /**
     * 换所有者不许把话换丢：说明整句进语义树，而且现在画它的是组件。
     *
     * 证人两样：`LbAsyncTags.MESSAGE` 这个 tag 与那条 `contentDescription`——
     * 页面自画的那一颗两样都没有（同一判据见 `EmptyStateOwnershipSemanticsTest` 第②栏）。
     * 旧的 `maxLines = 1 + Ellipsis` 会在这两句上裁掉后半句，改回自画就得再欠一次。
     */
    @Test
    fun `the notice strip says the whole sentence and is drawn by the design system`() {
        mountNotice {}
        val nodes = messageNodes()
        assertEquals(
            "通知条该恰好一颗说明节点，多一颗就是有人在同一条上画了两行字：" +
                nodes.joinToString { it.describe() },
            1, nodes.size
        )
        val message = nodes.single()
        assertEquals("说明节点念的是整句原文", noticeText, message.label)
        assertEquals(
            "组件那颗说明文字自己打 contentDescription 锚点（页面自画的那一颗没有这一条）",
            listOf(noticeText), message.contentDescriptions
        )
        rule.onNodeWithText(noticeText).assertExists()
    }

    /**
     * 三条通知共用一条形状：**换语气不许换热区**（同 `RowActionSemanticsTest` 那一格的规矩）。
     *
     * 反面：有人为了让"警告"更显眼，把 Warning 那一档的动作改小或改成一行的字——
     * 语义树里热区或角色就会不一样，这一格当场红。语气只该改颜色，不该改点得中与否。
     */
    @Test
    fun `switching the notice tone must not switch the hot zone`() {
        val tone = androidx.compose.runtime.mutableStateOf(LbStateTone.Success)
        rule.setContent {
            UiMatrix(360, 400).RenderIn(LocalDensity.current.density) {
                KbNoticeBanner(text = noticeText, tone = tone.value, onDismiss = {})
            }
        }
        val sizes = LinkedHashMap<String, Pair<Float, Float>>()
        for ((name, value) in listOf("已成事实" to LbStateTone.Success, "警告" to LbStateTone.Warning)) {
            rule.runOnIdle { tone.value = value }
            rule.waitForIdle()
            val target = probe.actionableTargets(rule, "面板·通知条（$name）").single()
            sizes[name] = target.widthDp to target.heightDp
            assertEquals("$name 那一档也得报按钮角色：" + target.describe(), "Button", target.role)
        }
        val (a, b) = sizes.getValue("已成事实")
        val (c, d) = sizes.getValue("警告")
        assertEquals("两档语气的热区宽度不一致：$a vs $c", a, c, 0.5f)
        assertEquals("两档语气的热区高度不一致：$b vs $d", b, d, 0.5f)
        assertTrue(
            "两轴都得 ≥${probe.floorDp.toInt()}dp（这一颗的名字是资源给的，宽度有多少是文字撑出来的、" +
                "多少是垫出来的，这一格分不出来——那种判法见 `LbAsyncStateTest` 用单字标签那一格）：" +
                "实到 ${a.toInt()}x${b.toInt()}dp",
            a >= probe.floorDp - 0.5f && b >= probe.floorDp - 0.5f
        )
    }

    // ═══════════ ② 输入已变化那一条 ═══════════

    /**
     * 「按新输入生成」现在是一颗真的按钮。
     *
     * 这一格是本次唯一一处**从"点不到"改成"点得到"**的读数：归并之前它是裸
     * `Text + .background(Primary) + clickable`，语义树里 `role=无`，热区两轴都没到下限
     * （它外面那条 `Row` 只有 `vertical = Spacing.xs` 的内边距）。
     * 说法与回调不许动：文案仍是资源里那两句，点击仍走 `onRegenerateWithNewInput`。
     */
    @Test
    fun `the input changed strip offers a regenerate that reads as a button`() {
        var fired = 0
        mountResult(
            GenerateResult.Success(
                LoveBrainResponse(
                    response = ReplySchemes(recommended = "一", badBoy = "二", playful = "三", warm = "四"),
                    directions = listOf("先问清楚"),
                    analysis = ReplyAnalysis()
                )
            ),
            onRegenerate = { fired++ }
        )
        val all = probe.actionableTargets(rule, "结果区·输入已变化条")
        val hits = all.filter { it.label == regenLabel }
        assertEquals(
            "这一屏该恰好一颗「$regenLabel」：" + all.joinToString { it.describe() },
            1, hits.size
        )
        val way = hits.single()
        assertEquals(
            "那颗动作要报 Button，否则读屏念得出字、说不出它能点（归并之前实到 role=无）：" +
                way.describe(),
            "Button", way.role
        )
        assertTrue(
            "热区两轴都要 ≥${probe.floorDp.toInt()}dp：" + way.describe(),
            !way.tooSmall(probe.floorDp)
        )
        // 点法走语义树上的 `OnClick`，而不是注入坐标：这一档容器里 `performClick()` 两次都
        // 静默打空（先按文字命中合并块、再按组件自己的 ACTION 锚点，各一次，`fired` 仍是 0），
        // 而读屏真正触发的就是这个语义动作 —— 判"点它到底会不会回调"，用它会走的那条路才算数。
        // （这一族坑本仓库已记过：`performClick` 只投触摸、不查语义。）
        rule.runOnIdle {
            val node = rule.onNodeWithTag(LbAsyncTags.ACTION).fetchSemanticsNode()
            val click = node.config.getOrNull(SemanticsActions.OnClick)
            assertTrue("那颗动作在语义树上没有 OnClick 动作，读屏也点不动它：" + node.config,
                click?.action?.invoke() == true)
        }
        rule.waitForIdle()
        assertEquals("按一次就该回调一次", 1, fired)
    }

    /** 说明那句仍然是原话，而且带组件的锚点（原来的 `Text("输入已修改…")` 两样都没有） */
    @Test
    fun `the input changed strip keeps its words and its announcement anchor`() {
        mountResult(
            GenerateResult.Success(
                LoveBrainResponse(
                    response = ReplySchemes(recommended = "一", badBoy = "二", playful = "三", warm = "四"),
                    directions = listOf("先问清楚"),
                    analysis = ReplyAnalysis()
                )
            )
        )
        val messages = messageNodes().filter { it.label == changedText }
        assertEquals(
            "输入已变化那条该给出一颗说明节点（读资源，不读内联中文）：" +
                messageNodes().joinToString { it.describe() },
            1, messages.size
        )
        assertEquals(
            "说明节点自己带 contentDescription ⇒ 画的确实是组件那一颗",
            listOf(changedText), messages.single().contentDescriptions
        )
    }

    // ═══════════ ③④ 两处错误条 ═══════════

    /**
     * 结果区那一档：错因整句进语义树，并且现在由组件画。
     *
     * 那颗重试的尺寸与角色由 `PanelErrorStatesSemanticsTest` 量着（那一格已经并进了
     * 三档错误态的下限与角色判据），这一格补的是**说明那一行**：
     * 页面自己画的那一颗 `Text` 没有 MESSAGE 锚点、也没有 contentDescription，
     * 读屏与截图两侧都定位不到"这一屏出事的那句话"。
     */
    @Test
    fun `the result error strip states the whole reason through the component`() {
        mountResult(GenerateResult.Error(errorText))
        rule.onNodeWithText(errorText).assertExists()
        val nodes = messageNodes()
        assertEquals(
            "错误条该恰好一颗说明节点：" + nodes.joinToString { it.describe() },
            1, nodes.size
        )
        val message = nodes.single()
        assertEquals("错因原话一个字都不许多也不许少", errorText, message.label)
        assertEquals(
            "说明节点自己带 contentDescription（页面自画的那一颗没有这一条）",
            listOf(errorText), message.contentDescriptions
        )
        // 这一档唯一能自救的东西就是那颗重试：只许一颗，而且仍走资源里那个标签
        val retries = probe.actionableTargets(rule, "结果区·错误档").filter { it.label == retryLabel }
        assertEquals(
            "错误档就地该有且只该有那一颗重试：" + retries.joinToString { it.describe() },
            1, retries.size
        )
    }

    /**
     * 主动发那一档**不给动作**：这一屏的主操作是下面那颗「生成开场」。
     *
     * 判"没有可点节点"必须显式读未合并树：`probe.actionableTargets` 对空集合是**抛**的
     * （它防的是"入口被删了"），拿它判 0 颗就会把"本来就该 0 颗"判成事故。
     */
    @Test
    fun `the proactive error strip does not grow a second way out`() {
        mountProactive(errorText)
        val clickable = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(
            "主动发失败那一档该是 0 颗可点击节点，实到 ${clickable.size} 颗" +
                "（要给出口就让持有者传 action，不许在这一档里再画一颗）",
            clickable.isEmpty()
        )
        val nodes = messageNodes()
        assertEquals("失败说明该恰好一颗：" + nodes.joinToString { it.describe() }, 1, nodes.size)
        assertEquals("错因整句进语义树", errorText, nodes.single().label)
        rule.onNodeWithText(errorText).assertExists()
    }

    /** 挂的是哪一档的证人：主动发失败时不该同时冒出空态那句话 */
    @Test
    fun `the proactive error strip is not the empty tier`() {
        mountProactive(errorText)
        val texts = messageNodes().map { it.label }
        assertEquals(
            "失败那一档只该念错因，不该把空态提示一起画出来：" + texts,
            listOf(errorText), texts
        )
    }
}
