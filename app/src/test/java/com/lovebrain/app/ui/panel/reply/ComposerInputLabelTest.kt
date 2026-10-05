package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.ui.common.CompactInput
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
 * 输入框在语义树上到底有没有名字（第6节第5条 无障碍第②栏：读屏念不出这是什么输入框）。
 *
 * 缺陷的形状很具体：placeholder 是**兄弟节点**的一行 `Text`，只在草稿为空时画出来。
 * TalkBack 因此只报「编辑框」，念不到那句提示；用户敲进第一个字之后连那行字都不在树上了，
 * 这一颗于是彻底没有名字。`ComposerAddButtonGatingTest` 里 describe() 的那条兜底分支
 * 「（输入框：有 EditableText，但没有文案也没有 contentDescription）」量的就是它。
 *
 * 修法是给可编辑节点本身挂 `contentDescription = placeholder`。
 * 这里刻意**不断言中文原文**：面板上那三句提示目前仍是硬编码在 `ReplyInput` 里的字面量
 * （那笔账属于资源驱动那条，本轮不顺手改用户可见文案），所以钉的是三件语言无关的事实：
 * 有名字、名字随角色变、override 传进来就是什么。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerInputLabelTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun editable(): SemanticsNode =
        rule.onNode(hasSetTextAction())
            .fetchSemanticsNode("输入框不在语义树上（hasSetTextAction 找不到节点）")

    /** 读屏会念的那句：从语义树读，不看源码 */
    private fun spokenLabel(): String? =
        editable().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("+")

    private fun SemanticsMatcher.clickable(): SemanticsMatcher = this and hasClickAction()

    private fun mountReply(draft: MutableState<String>, role: MutableState<ChatMessage.Role>) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = draft.value,
                    currentRole = role.value,
                    editingIndex = -1,
                    onDraftChange = { draft.value = it },
                    onRoleChange = { role.value = it },
                    onAdd = {},
                    onFocusChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun theComposerInputCarriesASpokenLabelFromTheFirstFrame() {
        val draft = mutableStateOf("")
        val role = mutableStateOf(ChatMessage.Role.ME)
        mountReply(draft, role)

        val label = spokenLabel()
        assertTrue("输入框在语义树上没有名字（读屏只会念「编辑框」）：实到 $label", !label.isNullOrBlank())
    }

    /** 切角色之后名字要跟着换——三句提示各说不同的事，共用一句等于没说 */
    @Test
    fun theLabelFollowsTheSelectedRole() {
        val draft = mutableStateOf("")
        val role = mutableStateOf(ChatMessage.Role.ME)
        mountReply(draft, role)

        val meLabel = spokenLabel()
        rule.onNode(hasText("她").clickable()).performClick()
        rule.mainClock.advanceTimeBy(16L)
        val herLabel = spokenLabel()

        assertNotEquals(
            "切到《她》之后输入框的名字没变，读屏仍会念上一角色的提示",
            meLabel, herLabel
        )
        assertTrue("名字仍不许是空：$herLabel", !herLabel.isNullOrBlank())
    }

    /** 输入之后 placeholder 那行 Text 不再画——那时 contentDescription 是唯一的名字来源 */
    @Test
    fun theLabelSurvivesTheFirstCharacter() {
        val draft = mutableStateOf("")
        val role = mutableStateOf(ChatMessage.Role.ME)
        mountReply(draft, role)

        rule.onNode(hasSetTextAction()).performTextInput("在吗")
        rule.mainClock.advanceTimeBy(16L)

        val label = spokenLabel()
        assertTrue(
            "敲了字之后输入框反而没名字了（placeholder 那行已经不画了）：实到 $label",
            !label.isNullOrBlank()
        )
        assertEquals("输入本身要落到草稿里", "在吗", draft.value)
    }

    /** 主动发复用同一颗时传 placeholderOverride：名字就是那句 override，不能是回复态的默认 */
    @Test
    fun anOverridePlaceholderBecomesTheSpokenLabel() {
        val draft = mutableStateOf("")
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = draft.value,
                    currentRole = ChatMessage.Role.ME,
                    editingIndex = -1,
                    onDraftChange = { draft.value = it },
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {},
                    showRoleChips = false,
                    showAddButton = false,
                    placeholderOverride = "PROACTIVE_LABEL_SENTINEL"
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        assertEquals(
            "override 没传到语义上——两颗输入场景共用一个组件时，名字必须跟着场景走",
            "PROACTIVE_LABEL_SENTINEL", spokenLabel()
        )
    }

    /** 第二个实例：问卷页与供应商弹窗用的 CompactInput 同样有过这颗无名输入框 */
    @Test
    fun compactInputAlsoCarriesASpokenLabel() {
        val draft = mutableStateOf("")
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                CompactInput(
                    value = draft.value,
                    onValueChange = { draft.value = it },
                    placeholder = "COMPACT_LABEL_SENTINEL"
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        assertEquals("COMPACT_LABEL_SENTINEL", spokenLabel())
        // 视觉上那行提示照旧画着（那一格只加语义名字，不改视觉）
        rule.onNodeWithText("COMPACT_LABEL_SENTINEL").assertExists()
    }

    /**
     * 同一颗节点的第二笔账：念得到之后，手指还得按得到。
     *
     * 上一格只解决了"名字"，没量过尺寸。这一格最早是把 `heightIn(min = TOUCH_TARGET_MIN_DP)`
     * 挂到可编辑节点自己身上——那把**可见输入行**一起撑到了下限那一档。本轮输入行外观
     * 回到旧版单行胶囊，≥48dp 的触摸下限改由包裹整行的透明热区承担（与角色 chip
     * `layeredTouch` 同一范式，旧版本来就量到 28dp 的行也能点得中）。判据不降，换的是层：
     * 热区过下限、可编辑节点完整落在热区里、可见那颗不许被重新撑成下限档
     * （撑回去 = 两层塌成一层，外观就回退了）。
     */
    @Test
    fun theComposerInputSitsInsideAFullHeightTouchLayer() {
        val draft = mutableStateOf("")
        val role = mutableStateOf(ChatMessage.Role.ME)
        mountReply(draft, role)

        val touch = probe.of(rule.onNodeWithTag(PANEL_INPUT_TOUCH_TAG).fetchSemanticsNode())
        assertTrue(
            "输入行热区不足 ${probe.floorDp.toInt()}dp：" + touch.describe(),
            !touch.tooSmall(probe.floorDp)
        )
        val field = probe.of(editable())
        assertTrue(
            "可编辑节点必须整个被热区包住（热区没包住输入=假分层）：字段 ${field.describe()} 热区 ${touch.describe()}",
            field.leftDp >= touch.leftDp - 0.5f && field.topDp >= touch.topDp - 0.5f &&
                field.leftDp + field.widthDp <= touch.leftDp + touch.widthDp + 0.5f &&
                field.topDp + field.heightDp <= touch.topDp + touch.heightDp + 0.5f
        )
        assertTrue(
            "可见输入行应回到旧版单行高度，不许被重新撑成热区那一档：" + field.describe(),
            field.heightDp + 0.5f < probe.floorDp
        )
    }

    /**
     * 问卷页 / 供应商弹窗 / 知识库改名框共用这一颗，所以它也必须自己够大。
     *
     * ⚠ 上一格（`theComposerInputSitsInsideAFullHeightTouchLayer`）能直接按 tag 取热区，
     * 是因为 `PanelTextInput` 外层那颗透明盒**在生产里就带** `PANEL_INPUT_TOUCH_TAG`。
     * `CompactInput` 那两层（只转焦点、不声明点击的透明盒 + 36dp 可见胶囊）都不带语义：
     * 生产侧这一轮**有意不补**——给这一排凭空加一颗"入口"会把"这屏有几个可点项"的守卫数错
     * （同一处取舍写在 `ReplyInput.kt` 里 `PanelTextInput` 那段注释与 `CompactInput.kt` 的
     * 热区层注释上）。
     * 于是树上只有那颗文字行（本机实量 336x15dp），拿它去比 36 或 48 都是许愿。
     *
     * 这一格因此换成与上一格**同一个读法**，量具从测试侧借：在组件外面套一颗只用来量的盒
     * （它不吃点击、不接语义，尺寸完全由被测那颗自己撑出来）——
     * 生产把外层 `heightIn(min = 48)` 撤掉，这盒就跟着缩回 36，下面第一条当场红；
     * 生产把可编辑节点自己再撑成 48（两层塌成一层、外观回退），第三条当场红。
     */
    @Test
    fun compactInputIsBigEnoughToBeItsOwnTouchTarget() {
        val draft = mutableStateOf("")
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Box(modifier = Modifier.testTag(COMPACT_HOTSPOT_PROBE_TAG)) {
                    CompactInput(
                        value = draft.value,
                        onValueChange = { draft.value = it },
                        placeholder = "COMPACT_SIZE_SENTINEL"
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val touch = probe.of(rule.onNodeWithTag(COMPACT_HOTSPOT_PROBE_TAG).fetchSemanticsNode())
        assertTrue(
            "CompactInput 外层那颗热区不足 ${probe.floorDp.toInt()}dp（整行 = 这颗量测盒，" +
                "它包的就是生产那一层）：" + touch.describe(),
            !touch.tooSmall(probe.floorDp)
        )
        val field = probe.of(editable())
        assertTrue(
            "可编辑节点必须整个被热区包住（热区没包住输入=假分层）：字段 ${field.describe()} 热区 ${touch.describe()}",
            field.leftDp >= touch.leftDp - 0.5f && field.topDp >= touch.topDp - 0.5f &&
                field.leftDp + field.widthDp <= touch.leftDp + touch.widthDp + 0.5f &&
                field.topDp + field.heightDp <= touch.topDp + touch.heightDp + 0.5f
        )
        assertTrue(
            "可见输入行应回到通用表单那一档，不许被重新撑成热区那一档：" + field.describe(),
            field.heightDp + 0.5f < probe.floorDp
        )
    }

    private companion object {
        /** 只服务这一格的量测盒标签：测试侧的东西，不进生产语义 */
        const val COMPACT_HOTSPOT_PROBE_TAG = "compact_input_hotspot_probe"
    }
}
