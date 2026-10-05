package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.GenerateResult
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
 * 「输入已变化」那一条这一轮换了**位置与出口**，判据跟着换（换的是怎么看，不是看什么松了）：
 * 它不再由结果区在正文下面另起一条，而是作为一条警告进**通知位那一格**（同一时间只画一条），
 * 画它的就是生产那一颗 `KbNoticeBanner`。它尾部那颗「按新输入生成」也一并撤了——
 * 无结果时插入槽上画的正是那颗「生成回复」，用新输入再生成走的就是它，同屏再留第二个入口
 * 就是同一件事两处出口。所以那一族现在判的是：**整句说明到位、就地唯一可点的是那颗关闭，
 * 而且那颗重复的再生成出口不许长回来**。
 *
 * 四件事，每件事都有能对撞的坏实现：
 * 1. 通知条那颗关闭：**恰好一颗**可点、报 `Button`、两轴都到下限、名字来自资源、点一次回调一次；
 * 2. 输入已变化那一条：同样恰好一颗关闭（名字/角色/两轴/回调），说明整句进语义树、
 *    由组件画（带 `contentDescription` 锚点），**且这一条上没有那颗「按新输入生成」**；
 *    把读回的数据换成"只有一颗裸文字"或"说明被挪到动作下面另起一行"都会红；
 * 3. 错误档那一档：错因整句进语义树，而且画它的是组件（页面自画的那一颗没有 MESSAGE 锚点）；
 *    就地**不许有任何可点出口**——回复档错误态的出口是插入槽上那颗「生成回复」（由回复主按钮
 *    那一族判），在这一条上再放一颗重试就是本仓库刚撤掉的那个形状；
 * 4. 主动发失败那一档**不给第二颗出口**：这一屏的主操作是下面那颗「生成开场」。
 *
 * ⚠ 一个诚实的边界：这几条话在本机**都是资源给的**，测试里用的是中文原文——
 * 英文环境下 `a11y_close_notice` 解析成 "Dismiss notice"，所以锚点一律 `getString` 取，
 * 不写死（CI 上红掉的那一批就是这个形状）。
 *
 * ⚠ 那一档外观**本轮已经落地**（原话第 16 条 / 基线 v1 §6.3、§3.9）：轻通知走设计系统新立的
 * `LbStateContainer.Notice`——合同外观就是 11sp/16sp 的字、圆角 10、关闭那颗 14dp 字形坐在
 * **24dp** 见方盒里（[LbTextActionGlyph.Notice]，复用建议卡那一套 labelMedium 紧凑语言）。
 * 所以下面三处量热区的判据**从全站 48dp 换成 `TouchTier.PANEL_HEADER_HOTZONE`（24dp）那一档**：
 * 不是松成"存在即可"（仍然逐颗两轴 ≥24），而是量到已经做完的那一档。生产那颗关闭若退回
 * 48×48 的 `Standard` 文字盒，24dp 一横条会被顶成 56dp，`the notice strip stays within the
 * compact notice height tier` 那一格的 `boundsInRoot` 几何判据当场红。
 * ⚠ 前提：宿主 `KbNoticeBanner`（`LoveBrainPanelScreen.kt`，主线程持有）须把
 * `container = LbStateContainer.Strip` 翻成 `LbStateContainer.Notice`；本测试挂的是生产那一颗，
 * 未翻则这几格仍读到旧 Strip 的 48dp——那是宿主没接线，不是这一族的问题（见台账 impl-N1b）。
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

    /**
     * 轻通知档那颗关闭走的是 24dp 见方那一档（[TouchTier.PANEL_HEADER_HOTZONE]，与面板工具条那颗
     * 齿轮/收起外包盒同档），不是全站 48 下限——原话第 16 条落地后的档位入口（见文件头 ⚠ 段）。
     */
    private val noticeFloorDp: Float get() = TouchTier.PANEL_HEADER_HOTZONE

    /** 三条真实存在的通知文本（前两条就是 VM 现在会推出去的那两句） */
    private val noticeText = "已暂停本轮提及，下次生成将过滤此条记忆"
    private val errorText = "网络断了，这轮没生成成"

    /** 只给整条高度那一格量边界用的宿主 tag（把 banner 套进一颗 wrap 的 Box，读它自己的 boundsInRoot） */
    private val BANNER_HOST_TAG = "lb_notice_banner_host"

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

    /**
     * 「输入已变化」那一条现在走的就是通知位那一格（生产把它作为一条警告推进队列），
     * 挂的也是生产那一颗 [KbNoticeBanner]，语气换成 Warning。
     */
    private fun mountChangedNotice(fired: () -> Unit) {
        rule.setContent {
            UiMatrix(360, 400).RenderIn(LocalDensity.current.density) {
                KbNoticeBanner(text = changedText, tone = LbStateTone.Warning, onDismiss = fired)
            }
        }
        rule.waitForIdle()
    }

    private fun mountResultError(message: String) {
        rule.setContent {
            UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                ResultArea(
                    result = GenerateResult.Error(message),
                    isGenerating = false,
                    streamingCoreText = "",
                    isGeneratingCore = false,
                    streamingSchemes = emptyList(),
                    feedbacks = emptyMap(),
                    onFeedback = { _, _ -> },
                    onCopyScheme = {},
                    onRetry = {},
                    providerReady = true,
                    onOpenSettings = {}
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
            "轻通知关闭两轴都要 ≥${noticeFloorDp.toInt()}dp（24dp 那一档，原话第 16 条，不再是 48）：" + way.describe(),
            !way.tooSmall(noticeFloorDp)
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
            "两轴都得 ≥${noticeFloorDp.toInt()}dp（轻通知关闭那一颗走 24dp 见方那一档，" +
                "Success/Warning 两语气同尺寸、都由同一颗图标档垫出来，不再按 48 量）：" +
                "实到 ${a.toInt()}x${b.toInt()}dp",
            a >= noticeFloorDp - 0.5f && b >= noticeFloorDp - 0.5f
        )
    }

    /**
     * 横条**总高**的几何判据（原话第 16 条 / 基线 v1 §6.3）：轻通知档单行必须留在紧凑那一档里。
     *
     * 这一格量的是 `boundsInRoot` 的整条高度，不是热区——语义树读得出"这条横条被撑到多高"。
     * 反面（也是**必须红**的那一个反例）：把关闭那颗从 24dp 图标盒换回 `ScreenAction` 默认那颗
     * `LbTextActionSize.Standard` 的 48×48 无底文字盒，24dp 一横条会被顶成 56dp（这正是用户第 16 条
     * 骂"太大"的物理成因）⇒ `≤ 40` 这一句当场红。上限取 40 是给两行那一档留余量、又远低于 56；
     * 下限 16 挡的是"关闭整个没了、条子塌成一行零高"这种把它改没的坏实现。
     * ⚠ 挂的是生产那颗 [KbNoticeBanner]，前提是宿主已把它接到 `LbStateContainer.Notice`（见文件头 ⚠ 段）。
     */
    @Test
    fun `the notice strip stays within the compact notice height tier`() {
        val singleLine = "已记入"
        rule.setContent {
            UiMatrix(360, 400).RenderIn(LocalDensity.current.density) {
                Box(Modifier.testTag(BANNER_HOST_TAG)) {
                    KbNoticeBanner(text = singleLine, tone = LbStateTone.Success, onDismiss = {})
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag(BANNER_HOST_TAG).fetchSemanticsNode().boundsInRoot
        val heightDp = bounds.height / density
        assertTrue(
            "单行轻通知整条高该 ≤40dp（紧凑那一档）；把关闭换回 48×48 Standard 文字盒会顶到 56dp，" +
                "本格必须红。实到 ${heightDp.toInt()}dp",
            heightDp <= 40f
        )
        assertTrue(
            "整条高不该塌没（关闭被整个删掉会读到 ~0）：实到 ${heightDp.toInt()}dp",
            heightDp >= 16f
        )
    }

    // ═══════════ ② 输入已变化那一条（现在住在通知位那一格）═══════════

    /**
     * 这一条现在就地**只有一颗关闭**，而且那颗是真的按钮。
     *
     * 换掉的是"这一条上该有一颗「按新输入生成」"：无结果时插入槽上画的就是那颗「生成回复」，
     * 用新输入再生成走的就是它，结果底部/通知条上再留第二颗同一件事的出口就是本轮撤掉的形状。
     * 所以这一格两头都有牙：
     * · 那颗关闭的名字仍来自资源、仍报 `Button`、两轴仍到下限、点一次回调一次（改回裸
     *   `Text.clickable` 或把热区只垫高度都会红）；
     * · 这一条上**不许**出现资源里那句「按新输入生成」（把它加回来就红）。
     */
    @Test
    fun `the input changed notice keeps one named dismiss and grows no second way out`() {
        var fired = 0
        mountChangedNotice { fired++ }
        val all = probe.actionableTargets(rule, "面板·通知位·输入已变化条")
        assertEquals(
            "这一条上只该有那颗关闭：" + all.joinToString { it.describe() },
            1, all.size
        )
        val way = all.single()
        assertEquals(
            "关闭那颗要报 Button（裸 Box.clickable 读屏不会念成按钮）：" + way.describe(),
            "Button", way.role
        )
        assertEquals(
            "名字必须来自资源，不许是内联中文（英文环境下要念成 Dismiss notice）：" + way.describe(),
            closeLabel, way.label
        )
        assertTrue(
            "轻通知关闭两轴都要 ≥${noticeFloorDp.toInt()}dp（24dp 那一档，原话第 16 条，不再是 48）：" + way.describe(),
            !way.tooSmall(noticeFloorDp)
        )
        // 被撤掉的那颗重复出口不许长回来：整棵树里都读不到那个资源名才算（画成按钮、
        // 画成一行裸文字、挂在说明里都算），"少一颗"不是这一句的意思。
        val doubled = rule.onAllNodes(hasText(regenLabel, substring = true)).fetchSemanticsNodes()
            .map { probe.of(it) }
        assertTrue(
            "「$regenLabel」与插入槽上那颗「生成回复」是同一件事的两个出口，这一条上不许出现，" +
                "实到 ${doubled.size} 颗：" + doubled.joinToString { it.describe() },
            doubled.isEmpty()
        )
        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        rule.waitForIdle()
        assertEquals("按一次就该回调一次", 1, fired)
    }

    /**
     * 说法与摆位：整句逐字进语义树，并且**说明在前、动作在同一行尾巴上**。
     *
     * 三条都是值判据：
     * · `MESSAGE` 那颗逐字等于资源里那一句（换成另一句话、或页面自己再画一条都会红）；
     * · 恰好一颗（一屏两条横幅回来了就红）；
     * · 说明那颗的右缘不越过关闭那颗的左缘、两者垂直中心一致（把动作挪到说明下面另起一行、
     *   或把整条改成动作在前的形状都会红）。
     * ⚠ 这一格**不**声称证明了"没有被省略号裁尾"：语义树对 `maxLines + Ellipsis` 永远报完整
     * 原串，那种判据没牙，裁切归量高度/槽位的那几格（同 `EmptyStateOwnershipSemanticsTest`）。
     */
    @Test
    fun `the input changed notice states its whole sentence ahead of its one action`() {
        mountChangedNotice {}
        val messages = messageNodes()
        assertEquals(
            "输入已变化那条该给出恰好一颗说明节点：" + messages.joinToString { it.describe() },
            1, messages.size
        )
        val message = messages.single()
        assertEquals("说明逐字是资源里那一句（读资源，不读内联中文）", changedText, message.label)
        assertEquals(
            "说明节点自己带 contentDescription ⇒ 画的确实是组件那一颗",
            listOf(changedText), message.contentDescriptions
        )
        val way = probe.actionableTargets(rule, "面板·通知位·输入已变化条").single()
        assertTrue(
            "说明要在动作前面（整条改成动作在前的形状就红）：" +
                "${message.describe()} | ${way.describe()}",
            message.leftDp <= way.leftDp + 0.5f
        )
        assertTrue(
            "说明与动作要在同一行里，不是另起一档：" +
                "${message.describe()} | ${way.describe()}",
            kotlin.math.abs((message.topDp + message.heightDp / 2f) - (way.topDp + way.heightDp / 2f)) <= 1f
        )
        assertTrue(
            "说明那一节自己得有宽度（被 weight 挤成 0 宽 = 这句话根本没画出来）：" + message.describe(),
            message.widthDp > 0f && message.heightDp > 0f
        )
    }

    // ═══════════ ③④ 两处错误条 ═══════════

    /**
     * 结果档错误态：错因整句进语义树、由组件画，并且就地**一颗可点的都没有**。
     *
     * 判据从"就地必须有一颗重试"翻成"就地必须没有"：本轮合同要回复档的主动作只有一个插入槽，
     * 错误态属于"还没有结果"，槽上此刻画的正是那颗「生成回复」，错误条上再放一颗重试
     * 等于同一屏两个入口做同一件事（撤掉那颗的是结果区那一档；锦囊与谈心两档没有别的出口，
     * 那两格仍判"必须有重试"，见同一个仓库里 `PanelErrorStatesSemanticsTest`）。
     * 把那颗按钮加回来这一格就红。
     *
     * ⚠ 判"没有"必须显式读未合并树：`probe.actionableTargets` 对空集合是**抛**的
     * （它防的是"入口被删了"），拿它判 0 颗会把"本来就该 0 颗"读成事故。
     * 这一档的另一半仍然有牙：说明节点恰好一颗、逐字是那句错因、并带组件自己的锚点。
     */
    @Test
    fun `the result error strip states the whole reason and carries no second way out`() {
        mountResultError(errorText)
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
        val clickable = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { probe.of(it) }
        assertTrue(
            "回复档错误条上不该有任何可点出口（出口是插入槽上那颗「生成回复」），实到 " +
                "${clickable.size} 颗：" + clickable.joinToString { it.describe() },
            clickable.isEmpty()
        )
        // 上面那条只拦"带点击语义的"那颗；画成一行裸文字、或挂成灰着的，都得这一条拦。
        // 这一句判的是"这一屏读不到那个资源名"，不是"少一颗就行"。
        val namedRetry = rule.onAllNodes(hasText(retryLabel, substring = true)).fetchSemanticsNodes()
            .map { probe.of(it) }
        assertTrue(
            "资源里那句「$retryLabel」不许在这一档以任何形状出现（重复出口），实到 " +
                "${namedRetry.size} 颗：" + namedRetry.joinToString { it.describe() },
            namedRetry.isEmpty()
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
