package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.FeedbackCase
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
 * §6.5 第②栏：**输入框自己有没有名字**（TalkBack 的 contentDescription 那一栏）。
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
     * §6.5 第②栏点名的就是 contentDescription 这一栏；放宽成"有字就行"等于把判据退回"看起来念得到"。
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

    /** 点踩原因面板：两颗可选输入框（补充说明 / 期望怎么回）以前都没有名字 */
    @Test
    fun `the dislike reason panel's two optional inputs are named`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                DislikeReasonHost(
                    case = dislikeCase(),
                    onUpdateCase = { _, _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("这一屏该有那两颗输入框", 2, editableNodes().size)
        assertAllEditablesNamed("点踩原因面板")
    }

    /** 卡片上的自定义改写输入：只有展开"自定义要求…"入口之后才存在 */
    @Test
    fun `the custom rewrite input on a scheme card is named once it appears`() {
        val scheme = Scheme(tag = "A", title = "推荐", reply = "text", source = SchemeSource.STYLE)
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                SchemeCard(
                    scheme = scheme,
                    feedback = SchemeFeedback.NONE,
                    onFeedback = { _, _ -> },
                    onCopy = {},
                    isExpanded = true
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("展开之前不该有那颗输入框", 0, editableNodes().size)

        // 入口那行字在屏幕上（不是 placeholder），可以直接按文案定位；
        // 用 substring 是因为它带着省略号，别把省略号写死进测试
        val trigger = rule.onAllNodes(SemanticsMatcher("文案含「自定义要求」") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { "自定义要求" in it.text }
                ?: false
        }).fetchSemanticsNodes()
        assertEquals("自定义改写入口应当恰好一个，实到 " + trigger.size, 1, trigger.size)
        rule.onAllNodes(SemanticsMatcher("文案含「自定义要求」") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { "自定义要求" in it.text }
                ?: false
        }).onFirst().performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点开之后该有那颗输入框", 1, editableNodes().size)
        assertAllEditablesNamed("自定义改写输入")
    }

    /**
     * 反空跑的第三格：名字**不能只是把 placeholder 蹭上**。
     * 蹭法（不挂 contentDescription）在用户敲字之后就读不到了，所以这里填进字再量一次：
     * 名字必须还在。
     */
    @Test
    fun `the name survives after the user starts typing`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                DislikeReasonHost(
                    case = dislikeCase(),
                    onUpdateCase = { _, _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        val before = editableNodes().first()
        val id = before.id
        // 真敲字（不是往语义里塞值）：placeholder 那行兄弟节点会随之消失
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("敲进去的第一个字")
        rule.mainClock.advanceTimeBy(16L)
        val after = editableNodes().first { it.id == id }
        assertTrue(
            "敲字之后名字必须还在（蹭 placeholder 的写法在这一步会念不出东西），实到「${nameOf(after)}」",
            nameOf(after).isNotBlank()
        )
        assertTrue(
            "名字得是可编辑节点自己的属性，不是蹭兄弟节点的文案：实到「${nameOf(after)}」",
            after.config.getOrNull(SemanticsProperties.ContentDescription)?.isNotEmpty() == true
        )
    }
}
