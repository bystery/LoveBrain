package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SchemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 第6节第5条 第②栏：**输入框自己有没有名字**（TalkBack 的 contentDescription 那一栏）。
 *
 * 上一格（`5245788`）新写的那格守卫一上来红在输入框身上，顺着量到全仓 7 处
 * `OutlinedTextField` 有 6 处既没文案也没 contentDescription。
 * 根因是同一件事：`placeholder` 是**兄弟节点**的一行 `Text`，
 * 它不进可编辑节点的语义；用户敲进第一个字之后连那行字都不在树上了。
 * 所以读屏只念"编辑框"——用户听不出自己在填哪一格。
 *
 * 这里刻意**不断言中文原文**（那是资源驱动那条账，也不该让改文案弄红无障碍守卫），
 * 钉的是语言无关的三件事：那颗节点有名字、名字不是空串、名字不来自 placeholder 的巧合。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputFieldLabelsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private fun editableNodes() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()

    private fun after(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        node.config.getOrNull(SemanticsProperties.EditableText)?.text

    /**
     * 名字**只认 contentDescription**。
     *
     * 第一版这里写成"contentDescription 或 Text 或 EditableText 三者取第一个非空"，
     * 于是 L2 探针（只摘掉第二颗输入框的 contentDescription）照样绿——那颗节点还能从别处蹭到字。
     * 第6节第5条 第②栏点名的就是 contentDescription 这一栏；放宽成"有字就行"等于把判据退回"看起来念得到"。
     * （`after()` 留着是给第三格当反证用的：它量的是"节点自己有没有这句话"。）
     */
    private fun nameOf(node: androidx.compose.ui.semantics.SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString("+")?.trim().orEmpty()

    private fun assertAllEditablesNamed(where: String) {
        val nodes = editableNodes()
        assertTrue("$where 一个输入框都没量到 ⇒ 这格会空过，先确认挂载真的画出了输入框", nodes.isNotEmpty())
        val unnamed = nodes.filter { nameOf(it).isBlank() }
        assertTrue(
            "$where 有 ${unnamed.size}/${nodes.size} 颗输入框读屏念不出名字（" +
                "修法是给可编辑节点挂 contentDescription，placeholder 不进语义树）：" +
                unnamed.joinToString { "尺寸 ${it.boundsInRoot.width / density}x" +
                    "${it.boundsInRoot.height / density}dp" },
            unnamed.isEmpty()
        )
    }

    private val density: Float get() = app.resources.displayMetrics.density

    private fun dislikeCase() = FeedbackCase(
        caseId = "case-1",
        schemeIdentityKey = "STYLE:A",
        candidateReply = "候选那句",
        categories = emptyList(),
        reasons = emptyList(),
        kbName = "default"
    )

    // （用户 2026-10-03："就点踩就不要弹窗全部删除！！记入就行了"）：点踩原因面板整块删除，
    // 这一格原来的被测主体（`DislikeReasonHost` 那两颗可选输入框）随之不存在了，故移除。
    // ⚠ 这不是"判据作废"：**"每一屏的可编辑节点都得有自己的名字"这条要重新找主体**
    //（回复输入行、知识库正文编辑器、供应商表单那几颗都还活着）——已登记进
    // ，别把它当已覆盖。

    /**
     * 卡片上的自定义改写输入：点下调整区那四项里的"自定义"之后才存在。
     *
     * 这一格的挂载形状跟着卡片走：卡片**自己不持有**"自定义开没开"与那句草稿——
     * 横向列表会把滑出视口的 item 连同它的 `remember` 一起丢掉，状态留在卡里就是
     * 滑出去再滑回来一句草稿没了，或更糟：上一张卡的草稿落到下一张卡上。
     * 所以这两样由调用方（卡片行按 `identity.key`）持有，这里就按调用方的身份把它们交进去。
     */
    @Test
    fun `the custom rewrite input on a scheme card is named once it appears`() {
        val scheme = Scheme(tag = "A", title = "推荐", reply = "text", source = SchemeSource.STYLE)
        val customOpen = mutableStateOf(false)
        val customDraft = mutableStateOf("")
        var submittedCustom: String? = null
        var submittedCommand: RewriteCommand? = null
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                SchemeCard(
                    scheme = scheme,
                    feedback = SchemeFeedback.NONE,
                    onFeedback = { _, _ -> },
                    onCopy = {},
                    onRewrite = { _, command -> submittedCommand = command },
                    onCustomRewrite = { _, text -> submittedCustom = text },
                    isExpanded = true,
                    customDraft = customDraft.value,
                    onCustomDraftChange = { customDraft.value = it },
                    isCustomInputOpen = customOpen.value,
                    onCustomInputOpenChange = { customOpen.value = it }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点开「自定义」之前不该有那颗输入框", 0, editableNodes().size)
        assertTrue("开合状态必须由调用方拿着，一上来却是开着的", !customOpen.value)

        // 入口那颗在屏幕上（不是 placeholder），可以直接按文案定位；
        // 用 substring 是因为那颗的读屏名与它同族（自定义 / 自定义改写要求），别把整句写死
        val trigger = rule.onAllNodes(SemanticsMatcher("文案含「自定义」") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { "自定义" in it.text }
                ?: false
        }).fetchSemanticsNodes()
        assertEquals("自定义改写入口应当恰好一个，实到 " + trigger.size, 1, trigger.size)
        rule.onAllNodes(SemanticsMatcher("文案含「自定义」") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { "自定义" in it.text }
                ?: false
        }).onFirst().performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点开之后该有那颗输入框", 1, editableNodes().size)
        // 点开这件事必须真的落在调用方那份状态上：卡片偷偷另记一份的话，
        // 这颗输入框在下一张卡上也会"开着"（同一屏两颗卡串味），而这里照样量得到 1 颗
        assertTrue("点了「自定义」，调用方持有的开合状态却没翻 ⇒ 卡片在私存第二份状态", customOpen.value)
        assertAllEditablesNamed("自定义改写输入")

        // 提交走自定义这一支：填进去的句子原样交出去，不退回枚举命令那一支
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("保留第一句，第二句不要")
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            "敲进去的草稿没交回给调用方持有（卡片自己存了一份，滑出视口就没）",
            "保留第一句，第二句不要", customDraft.value
        )
        val submit = rule.onAllNodes(SemanticsMatcher("文案含「确认改写」") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { "确认改写" in it.text }
                ?: false
        }).fetchSemanticsNodes()
        assertEquals("展开之后该出现自定义那一条的提交出口，实到 " + submit.size, 1, submit.size)
        rule.onAllNodes(SemanticsMatcher("文案含「确认改写」") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { "确认改写" in it.text }
                ?: false
        }).onFirst().performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            "提交的那一句必须原样走自定义这一支", "保留第一句，第二句不要", submittedCustom
        )
        assertTrue(
            "点自定义这一支不该顺手触发枚举命令（更自然/换种说话/更温柔）：" + submittedCommand,
            submittedCommand == null
        )
    }

    // （用户 2026-10-03 原话："就点踩就不要弹窗全部删除！！记入就行了"）：这一格与上面那一格
    // 的主体都是点踩原因面板（`DislikeReasonHost`），面板已整块删除 ⇒ 两颗测试随主体一起摘除。
    //
    // ⚠ **判据本身没有作废**，这里记清楚它要重新挂到哪里：
    //   1) "可编辑节点的名字必须是它自己的属性，不能蹭 placeholder"——敲字之后名字必须还在；
    //   2) "同一屏里每颗可编辑节点都要有名字，不许有 unnamed 的漏网"。
    // 还活着且有可编辑节点的主体：回复输入行（`ReplyInput`）、知识库正文编辑器（`KbEditActivity`）、
    // 供应商表单（`ProviderSection`）、卡片自定义改写输入（本文件 :130 那格仍在测它）。
    // 这条欠账登记在 ，**不许当已覆盖**。
}
