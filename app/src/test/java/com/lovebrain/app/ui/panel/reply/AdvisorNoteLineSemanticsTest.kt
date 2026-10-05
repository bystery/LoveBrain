package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
// `ToggleableState` 住在 `androidx.compose.ui.state` 那一档，不在 `ui.semantics` 里
// （对着构建真正用的那颗 ui-android 1.6.8 的 classes.jar 点名：里面有
// `androidx/compose/ui/state/ToggleableState.class`，`ui/semantics/` 下没有这一颗；
// `SemanticsProperties.ToggleableState` 那颗键的值类型也是它——不是猜的路径）。
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 等 在**语义树上**的读数：军师备注那一行灰字、三颗输入对象 chip、
 * 以及"没接线就不许长出来的那颗入口"。
 *
 * 判据全从树上读，不看源码——本项目已知的恒真形态之一就是"grep 到某个符号就算实现了"。
 * "只画一行、超长省略成省略号"这一半是像素级的事，要截图/真机（见交付报告的诚实清单）；
 * 这里钉得住的是"文本节点身上带的就是**完整正文**"：省略只发生在绘制阶段，
 * 于是"屏上看着少了"与"发给军师的少了"这两件事被分开守住——前者可以省略，后者不可以。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdvisorNoteLineSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun noteTextNodes() =
        rule.onAllNodesWithText(ADVISOR_NOTE_PREFIX, substring = true).fetchSemanticsNodes()

    private fun shownTextOf(node: androidx.compose.ui.semantics.SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") ?: ""

    // ═══════════ ：备注行 ═══════════

    /**
     * 没有备注 = 这一行根本不存在。
     *
     * 反例：空正文仍画"我让军师注意：" ⇒ 用户被要求去填一条空标题
     *        （原话骂的"没有备注也占一大块"就是这个形状）。
     */
    @Test
    fun anEmptyNoteDrawsNoLineAtAll() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                AdvisorNoteLine(noteText = "   ", onClick = {})
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        rule.onNodeWithText(ADVISOR_NOTE_PREFIX, substring = true).assertDoesNotExist()
        assertEquals("空备注却画出了节点：" + noteTextNodes().size, 0, noteTextNodes().size)
    }

    /**
     * 有备注：一个节点、固定前缀、整段正文都在节点身上、点得动。
     *
     * 反例①：前缀写成"想法："（ 明令不许重复"想法："）；
     * 反例②：渲染前先 `take(20)` ——树上读到的就是被裁过的正文，
     *        那样"展示省略"和"发给军师的内容"再没人保证是同一份。
     */
    @Test
    fun theNoteLineShowsThePrefixedFullBodyAndIsClickable() {
        val note = "我其实知道她今天加班，别再问她忙不忙"
        val clicked = mutableStateOf(false)
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                AdvisorNoteLine(noteText = note, onClick = { clicked.value = true })
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val nodes = noteTextNodes()
        assertEquals("备注行应恰好一个节点：" + nodes.size, 1, nodes.size)
        val shown = shownTextOf(nodes.single())
        assertEquals("$ADVISOR_NOTE_PREFIX$note", shown)
        assertTrue("正文被裁过——屏上的与发给军师的不是同一份：$shown", shown.contains(note))

        rule.onNodeWithTag(ADVISOR_NOTE_TEST_TAG).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertTrue("点灰字必须把编辑动作投出去（：编辑完整正文）", clicked.value)
    }

    /**
     * 多行备注（提交过两条补充）在展示侧并成一行，两半都在。
     *
     * 反例：换行原样画 ⇒ 这一行变成两行，占掉气泡的位置（原话要的就是"一行灰色小字"）。
     */
    @Test
    fun aMultiLineNoteIsFlattenedIntoOneDisplayLineWithoutLosingTheSecondHalf() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                AdvisorNoteLine(noteText = "别再问她忙不忙\n也别提她前任", onClick = {})
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            ADVISOR_NOTE_PREFIX + "别再问她忙不忙 也别提她前任",
            shownTextOf(noteTextNodes().single())
        )
    }

    /**
     * 备注行自己是**一个**可点节点，不是一张带标题的卡。
     *
     * 反例：给它套一层"我的想法"卡（旧形制）⇒ 树上多出第二个节点/一段标题，用户看到的
     *        就是原话里那句"触发了之后下面的展示很难看"。
     */
    @Test
    fun theNoteIsOneLineNotASecondCard() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                AdvisorNoteLine(noteText = "先听我说完", onClick = {})
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("只许一个节点：" + noteTextNodes().size, 1, noteTextNodes().size)
        rule.onNodeWithTag(ADVISOR_NOTE_TEST_TAG).assertExists()
        rule.onNodeWithText("我的想法").assertDoesNotExist()
        rule.onNodeWithText("想法：").assertDoesNotExist()
    }

    // ═══════════ ：三颗输入对象 chip ═══════════

    /**
     * 第三颗是「补充」，点它投出的是**输入对象**。
     *
     * 反例：只有 `onRoleChange(Role.IDEA)` 这一条旧通道、没有 inputKind 通道 ⇒
     *        接线侧拿不到"这是内容种类"这件事，只能靠上一个角色猜（原话第 6 条的成因）。
     */
    @Test
    fun theThirdChipReportsSupplementAsInputKind() {
        val seen = mutableListOf<ComposerInputKind>()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "",
                    currentRole = ChatMessage.Role.HER,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {},
                    inputKind = ComposerInputKind.HER,
                    onInputKindChange = { seen += it }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        assertEquals(
            "「补充」这一颗必须恰好一个文案节点",
            1, rule.onAllNodesWithText(ROLE_LABEL_SUPPLEMENT).fetchSemanticsNodes().size
        )
        rule.onNodeWithText(ROLE_LABEL_SUPPLEMENT).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(listOf(ComposerInputKind.SUPPLEMENT), seen)
    }

    /**
     * 退回旧通道（宿主还没接 `onInputKindChange`）时，点「补充」仍投 `Role.IDEA`。
     *
     * 反例：新参数一上来就把旧参数删掉 ⇒ 未接线的宿主编不过，三颗 chip 全哑。
     */
    @Test
    fun theLegacyRoleChannelStillCarriesTheSupplementChoice() {
        val roles = mutableListOf<ChatMessage.Role>()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "",
                    currentRole = ChatMessage.Role.HER,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = { roles += it },
                    onAdd = {},
                    onFocusChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        rule.onNodeWithText(ROLE_LABEL_SUPPLEMENT).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(listOf(ChatMessage.Role.IDEA), roles)
    }

    /**
     * `inputKind` 没传、只传旧 `currentRole = IDEA` 时，亮着的仍必须是「补充」那一颗。
     *
     * 反例：这颗靠 `currentRole == IDEA` 判断而 chip 已改名 ⇒ 三颗全不亮，
     *        用户看不出自己在写哪一种内容。
     */
    @Test
    fun theSupplementChipLightsUpFromTheLegacyRoleAlone() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "",
                    currentRole = ChatMessage.Role.IDEA,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        val chips = rule.onAllNodesWithText(ROLE_LABEL_SUPPLEMENT).fetchSemanticsNodes()
        assertTrue(
            "「补充」没亮（旧宿主的 composeRole 没被读成输入对象）",
            chips.singleOrNull { it.config.getOrNull(SemanticsProperties.Selected) == true } != null
        )
    }

    // ═══════════ ：仅看本轮入口的位置 ═══════════

    /** 没接线就不画这一颗——反例：画一颗点了没反应的 chip，用户以为开了其实没开 */
    @Test
    fun theRoundScopeEntryIsAbsentUntilItIsWired() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "",
                    currentRole = ChatMessage.Role.HER,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        rule.onNodeWithText(PANEL_ROUND_SCOPE_LABEL, substring = true).assertDoesNotExist()
    }

    /**
     * 接线后它在角色行右侧，而且状态跟着传进来的那颗读。
     *
     * 反例：这颗自己 `remember { mutableStateOf(false) }` 存一份开关 ⇒ 第二本账，
     *        屏上显示开着而生成用的是另一份值（第10节第4条 说的"画个勾不算完成"）。
     */
    @Test
    fun theRoundScopeEntrySitsAtTheRightEndAndReportsItsState() {
        var clicks = 0
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "",
                    currentRole = ChatMessage.Role.HER,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {},
                    onlyThisRound = true,
                    onOnlyThisRoundChange = { clicks++ }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val entry = rule.onAllNodesWithText(PANEL_ROUND_SCOPE_LABEL, substring = true)
            .fetchSemanticsNodes().single()
        val chip = rule.onAllNodesWithText(ROLE_LABEL_HER).fetchSemanticsNodes().single()
        val entryBox = probe.of(entry)
        val chipBox = probe.of(chip)
        assertTrue(
            "「仅看本轮」应在角色行右侧（入口 left ${entryBox.leftDp} 必须比「她」${chipBox.leftDp} 靠右）",
            entryBox.leftDp > chipBox.leftDp
        )
        assertTrue("入口自己得够热区下限：" + entryBox.describe(), !entryBox.tooSmall(probe.floorDp))
        // 状态由外面给：树上的勾选态必须来自传进来的那颗参数，而不是这颗自己的记忆
        // （第10节第4条 那句"只画一个 checked 图标不算完成"判的就是这里——状态得真在语义上）
        assertEquals(
            "开关状态没跟着传进来的值",
            ToggleableState.On, entry.config.getOrNull(SemanticsProperties.ToggleableState)
        )

        rule.onNodeWithTag(PANEL_ROUND_SCOPE_TEST_TAG).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点击只投一次回调，状态由外面给", 1, clicks)
    }

    // ═══════════ ：意图/主动发入口与 ➕ 同区相邻 ═══════════

    /**
     * 次级入口槽位排在 ➕ 之后、同一条输入行上（"意图"与"主动发一句"相邻的那块位置）。
     *
     * 反例：这一行没有槽位，或槽位被放到输入行**上面**那一行 ⇒ 主线程接进来时
     *        只能自己再造一排，两个入口又分家（原话第 4 条要的"相邻"就没了）。
     */
    @Test
    fun secondaryEntriesSitNextToTheAddButtonOnTheInputRow() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "",
                    currentRole = ChatMessage.Role.HER,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {},
                    onlyThisRound = false,
                    onOnlyThisRoundChange = {},
                    secondaryEntries = {
                        androidx.compose.material3.Text("SENTINEL_PROACTIVE")
                    }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val entry = rule.onAllNodesWithText("SENTINEL_PROACTIVE").fetchSemanticsNodes().single()
        val add = rule.onAllNodesWithContentDescription("添加").fetchSemanticsNodes().single()
        val entryBox = probe.of(entry)
        val addBox = probe.of(add)
        val entryMid = entryBox.topDp + entryBox.heightDp / 2f
        val addMid = addBox.topDp + addBox.heightDp / 2f
        assertTrue(
            "次级入口与 ➕ 不在同一条输入行上：➕ ${addBox.describe()} 入口 ${entryBox.describe()}",
            kotlin.math.abs(entryMid - addMid) < 2f
        )
        assertTrue("次级入口应在 ➕ 右边：" + entryBox.describe(), entryBox.leftDp >= addBox.leftDp)
    }
}
