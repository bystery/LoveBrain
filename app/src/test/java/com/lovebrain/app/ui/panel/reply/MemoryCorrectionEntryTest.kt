package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「本轮参考」这一族在屏上的**可发现性与诚实性**判据（2026-11-21 信息减法重构）。
 *
 * 旧形状是四层壳：问句标题「这轮参考了哪些信息？」→「N 项正文重复」提示 →
 * 说明句「调整军师本轮参考的记忆」→ 独立的「修正记忆」文字入口——用户原话：
 * "像写说明书不像软件，反人类"。新形状把三层壳并成一个紧凑容器
 * （本体在 `MemoryRefsFeed.kt`，这里判的是它在屏上的行为）：
 *
 * - **① 无引用（或那一轮是「仅看本轮」）⇒ 整区一个节点都不画**
 *   （`a round without real refs draws the whole family as nothing` /
 *   `a round-only turn draws no refs even when a non-empty list is handed in`）：
 *   不许铺空壳、不许编假引用、也不许留一句"本轮没有引用历史记忆"的说明常驻——
 *   诚实由"什么都不说"保证。这一格就是旧 M20"出口常驻"与 F06"三档说明"的替身：
 *   出口与说明都退场之后，"没有"是唯一念不错的那一句话。
 *   反向证人在 ③——同一颗挂载路径给真引用就得念得出，"整块删空"红在那边。
 * - **② 整屏那一档的两头**（`a round-only turn offers no container at the result area either` /
 *   `the result area leaves no memory shell when the round has no refs`）：
 *   `ResultArea`（冻结的 `frozenOnlyThisRound`）与清单同判——不许给"点下去什么都不出现"的
 *   死路容器留位置，也不许在空轮铺壳。
 * - **③ 有引用 ⇒ 首行念真条数，且它就是那份整轮共享清单的唯一 toggle**
 *   （`the header names the true count and toggles the single shared list`）：
 *   「本轮参考 N 条」的 N 来自 `memoryRefs.size`，两条就念 2（念错条数就是编）；
 *   点首行整份清单收得掉、再点展得开（按卡分身只会收掉一张下面那块）；
 *   收掉之后首行与行尾「纠正中心」仍在——容器不跟着清单消失。
 *   热区两轴仍按全站下限判，不许靠削热区省空间。
 * - **③ʼ 一根禁令钉子**（`no per-card copy of the list exists`）：清单本体只许住在
 *   `MemoryRefsFeed.kt`，`SchemeCard.kt` 里一次都不许出现——按卡铺清单就是假归属。
 * - **④ 点行尾「纠正中心」只投一次"打开"意图，不自己改数据**
 *   （`the center entry hands over exactly one open intent`）：点一次 ⇒ 回调计数 1
 *   （点两次 ⇒ 2），四种说法一颗都不弹（自己就地开菜单红），纠正浮层的主人一个 target
 *   都没被填上（顺手把 `flow.requestWrong` 之类偷偷调了红）。
 *
 * ⚠ 锚点全部从资源取（`ctx.getString(...)`）：这台机器的环境解析出英文，写死中文会恒红。
 * 逐条行上那颗入口走 `memory_fix_entry`，容器行尾那颗走 `memory_center_entry`，
 * 首行计数走 `memory_refs_summary`（带 %1$d，测试按同一颗格式拼同一份读数）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoryCorrectionEntryTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    // 与生产取同一份资源，不抄第二份中文
    private val entryLabel get() = ctx.getString(R.string.memory_fix_entry)
    private val centerLabel get() = ctx.getString(R.string.memory_center_entry)
    private fun summaryLabel(count: Int): String =
        ctx.getString(R.string.memory_refs_summary, count)
    private val wrongLabel get() = ctx.getString(R.string.memory_action_wrong)
    private val muteLabel get() = ctx.getString(R.string.memory_action_mute)

    private val refA = MemoryRef(
        id = "mem-a", kbId = "kb", kind = MemoryKind.PROFILE,
        text = "她说过周五要交毕业设计", sourcePath = "understand/her.md"
    )
    private val refB = MemoryRef(
        id = "mem-b", kbId = "kb", kind = MemoryKind.SCENE,
        text = "她这周在赶毕业设计", sourcePath = "scene/now.md"
    )

    private var correctionCalls = mutableListOf<Triple<String, CorrectionAction, String>>()
    private var undoCalls = mutableListOf<String>()
    private var openCenterCalls = 0

    /** `flow` 是 JUnit 每格新建的那一颗（本类字段），不需要在这里手动收浮层 */
    private val flow = MemoryCorrectionFlow()

    private fun reset() {
        correctionCalls.clear()
        undoCalls.clear()
        openCenterCalls = 0
    }

    /** 把「紧凑容器」按生产里 MemoryRefsFeed.kt 的形状直接挂起来量 */
    private fun mountFeed(
        refs: List<MemoryRef>,
        showRefs: Boolean,
        onlyThisRound: Boolean
    ) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Box(modifier = Modifier.width(360.dp).height(900.dp)) {
                    Column {
                        MemoryRefsSection(
                            memoryRefs = refs,
                            showRefs = showRefs,
                            onCorrection = { id, action, text, _ ->
                                correctionCalls.add(Triple(id, action, text))
                            },
                            onUndoCorrection = { id -> undoCalls.add(id) },
                            correctionFlow = flow,
                            onlyThisRound = onlyThisRound,
                            onShowCorrectionCenter = { openCenterCalls++ }
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    // ── 整屏那一档（纵向默认摆法：八张卡一次全组合，不用滚过去才看得见）──────
    private val expandedState: MutableState<Boolean> = mutableStateOf(false)

    private fun successFixture(): GenerateResult.Success = GenerateResult.Success(
        LoveBrainResponse(
            response = ReplySchemes(
                recommended = "我理解你的意思，不过今天先把话说清楚再决定。",
                badBoy = "你要是还想拖，我就直说了：这事拖不下去。",
                playful = "哟，又来，这次我站你这边五秒钟。",
                warm = "我知道你不容易，我们慢慢来。"
            ),
            directions = listOf("先问清楚她想要什么", null, "把你的底线说一次", null),
            analysis = ReplyAnalysis()
        )
    )

    private fun mountResultArea(
        refs: List<MemoryRef>,
        expanded: Boolean,
        frozenOnlyThisRound: Boolean
    ) {
        expandedState.value = expanded
        rule.setContent {
            UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                ResultArea(
                    result = successFixture(),
                    streamingCoreText = "",
                    isGeneratingCore = false,
                    streamingSchemes = emptyList(),
                    feedbacks = emptyMap(),
                    onFeedback = { _, _ -> },
                    onCopyScheme = {},
                    onRetry = {},
                    providerReady = true,
                    onOpenSettings = {},
                    memoryRefs = refs,
                    memoryRefsExpanded = expandedState.value,
                    onToggleMemoryRefs = { expandedState.value = !expandedState.value },
                    frozenOnlyThisRound = frozenOnlyThisRound,
                    onShowCorrectionCenter = { openCenterCalls++ },
                    onCorrection = { id, action, text, _ ->
                        correctionCalls.add(Triple(id, action, text))
                    },
                    onUndoCorrection = { id -> undoCalls.add(id) }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun spokenTexts(): Set<String> = rule.onAllNodes(
        SemanticsMatcher("节点带可见文字") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
        }
    ).fetchSemanticsNodes()
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        .toSet()

    private fun plainTextCount(text: String): Int =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /** 数"一颗入口"要文案与点击动作一起取：`clickable` 会把子节点文字并进自己那一份语义 */
    private fun buttonCount(label: String): Int =
        rule.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().size

    private fun clickableCount(): Int =
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size

    /**
     * ① 本轮一份真引用都没有：整区一个节点都不画——空壳、说明句、占位条目、
     * 那句"本轮没有引用历史记忆"的常驻说明都不许有。
     */
    @Test
    fun `a round without real refs draws the whole family as nothing`() {
        reset()
        mountFeed(refs = emptyList(), showRefs = true, onlyThisRound = false)
        assertEquals(
            "没有真引用时整区一个字都不许念（空壳或占位条目都会让它涨过 0），实到：${spokenTexts()}",
            emptySet<String>(), spokenTexts()
        )
        assertEquals("没有真引用时不许有任何可点入口", 0, clickableCount())
    }

    /**
     * ①ʼ 开着「仅看本轮」的那一轮，手里就算攥着一条引用也不许画
     * （第10节第3条「仅看本轮开启后没有旧记忆引用，不能仍显示假引用」的显示侧判据）。
     * 传进去的是**非空**清单——那一轮的请求正文里根本没有这段记忆，
     * 屏上画得出的任何一条都是假引用。
     */
    @Test
    fun `a round-only turn draws no refs even when a non-empty list is handed in`() {
        reset()
        mountFeed(refs = listOf(refA), showRefs = true, onlyThisRound = true)
        assertEquals(
            "开着「仅看本轮」时这一族一个字都不该念出来（手里的清单是假引用），实到：${spokenTexts()}",
            emptySet<String>(), spokenTexts()
        )
        assertEquals("开着「仅看本轮」时不该有可点的入口或撤销", 0, clickableCount())
        assertEquals("那条真引用的正文必须读不到", 0, plainTextCount(refA.text))
        assertEquals("首行计数小字也不许出现（容器整块不画）", 0, plainTextCount(summaryLabel(1)))
    }

    /**
     * ② 整屏那一档：`frozenOnlyThisRound` 为真时容器整块不画——
     * 不许留一颗"点下去什么都不会出现"的死路 toggle，也不许念假引用。
     */
    @Test
    fun `a round-only turn offers no container at the result area either`() {
        reset()
        mountResultArea(refs = listOf(refA), expanded = false, frozenOnlyThisRound = true)
        assertEquals("假引用一条都不许念出来", 0, plainTextCount(refA.text))
        assertEquals("容器首行不许出现", 0, plainTextCount(summaryLabel(1)))
        assertEquals("行尾「纠正中心」不许出现", 0, buttonCount(centerLabel))
        assertEquals("逐条入口不许出现", 0, buttonCount(entryLabel))
    }

    /** ②ʼ 整屏那一档、本轮没有真引用：同样什么都不画，不留壳。 */
    @Test
    fun `the result area leaves no memory shell when the round has no refs`() {
        reset()
        mountResultArea(refs = emptyList(), expanded = false, frozenOnlyThisRound = false)
        assertEquals("没有真引用时不许有首行计数", 0, plainTextCount(summaryLabel(0)))
        assertEquals("没有真引用时不许有纠正中心动作", 0, buttonCount(centerLabel))
        assertEquals("没有真引用时不许有逐条入口", 0, buttonCount(entryLabel))
    }

    /**
     * ③ 首行念的是**真条数**，且它就是那份整轮共享清单的唯一 toggle。
     *
     * 坏实现各红在一句上：首行数错条数（两条念 1 或念 3 ⇒ 第一句红）、
     * 首行点不动或热区被削到下限之下（中间两句红）、
     * 点首行只收掉某一张卡下面那块（正文数不归零）、或者只收不展（最后一句红——死路量不出来）。
     */
    @Test
    fun `the header names the true count and toggles the single shared list`() {
        reset()
        mountResultArea(refs = listOf(refA, refB), expanded = true, frozenOnlyThisRound = false)
        assertEquals("首行念的是真条数（两条就念 2）", 1, plainTextCount(summaryLabel(2)))
        assertEquals("那份共享清单的正文只念得出一次一份", 1, plainTextCount(refA.text))
        assertEquals("首行点得开（文案与点击动作挂在同一颗节点上）", 1, buttonCount(summaryLabel(2)))

        // toggle 的热区不许靠削来省空间：两轴仍按全站下限判（语义树实量，不是源码里写着）
        val headerBox = rule.onAllNodes(hasText(summaryLabel(2)) and hasClickAction())
            .fetchSemanticsNodes().first().boundsInRoot
        val floor = AppDimens.TOUCH_TARGET_MIN_DP.toFloat()
        assertTrue(
            "首行的可点高度实到 ${headerBox.height / density}dp，低于全站下限 ${floor}dp 就是靠削热区省的空间",
            headerBox.height / density >= floor
        )
        assertTrue(
            "同一条首行的可点宽度实到 ${headerBox.width / density}dp",
            headerBox.width / density >= floor
        )

        // 点首行：整份清单收掉，容器本体不跟着消失
        rule.onAllNodes(hasText(summaryLabel(2)) and hasClickAction())[0].performClick()
        rule.mainClock.advanceTimeBy(500L)
        assertEquals("点首行收掉的就是整份共享清单", 0, plainTextCount(refA.text))
        assertEquals("收掉之后首行仍在（容器不跟着清单消失）", 1, plainTextCount(summaryLabel(2)))
        assertEquals("行尾「纠正中心」仍常驻", 1, buttonCount(centerLabel))

        // 再点一次：展得开。只证"收"不证"展"的话，那颗点下去什么都不会出现的死路量不出来
        rule.onAllNodes(hasText(summaryLabel(2)) and hasClickAction())[0].performClick()
        rule.mainClock.advanceTimeBy(500L)
        assertEquals("再点一次那份共享清单该重新展开", 1, plainTextCount(refA.text))
    }

    /**
     * ③ʼ 一根禁令钉子：清单本体只许有一个主人。
     * 按卡各铺一份清单（"这张回复专门引用了某条"那种假归属）就会在 `SchemeCard.kt` 里出现调用。
     */
    @Test
    fun `no per-card copy of the list exists`() {
        val dir = java.io.File("src/main/java/com/lovebrain/app/ui/panel/reply")
            .takeIf { it.isDirectory }
            ?: java.io.File("app/src/main/java/com/lovebrain/app/ui/panel/reply")
        val schemeCard = java.io.File(dir, "SchemeCard.kt").readText(Charsets.UTF_8)
        val feed = java.io.File(dir, "MemoryRefsFeed.kt").readText(Charsets.UTF_8)
        assertEquals(
            "卡片里不许渲染那份清单（展开的是本轮共享清单，不是这张卡专属的证据）",
            0, Regex("""MemoryRefsSection\(""").findAll(schemeCard).count()
        )
        assertEquals(
            "那份清单在整棵树里只许有一个本体，才谈得上「整轮共享」",
            1, Regex("""internal fun MemoryRefsSection\(""").findAll(feed).count()
        )
    }

    /**
     * ④ 点行尾「纠正中心」只投一次"打开纠正中心"，不自己改数据、不就地开那四种说法。
     *
     * 坏实现各红在一句上：把纠正动作直接接在这颗动作上（回调 0 次 / 纠正表被填）、
     * 重复投递（点一次数到 2）、或者顺手弹开本地那条纠正浮层（`flow` 的两个 target 之一非空）。
     */
    @Test
    fun `the center entry hands over exactly one open intent`() {
        reset()
        // 收起态挂载：清单没展开，树上叫「纠正中心」的只有行尾那颗动作，不用靠位置猜
        mountFeed(refs = listOf(refA), showRefs = false, onlyThisRound = false)
        assertEquals("收起态下行尾那颗动作该在树上且唯一", 1, buttonCount(centerLabel))
        assertEquals("清单没展开，那份正文读不到", 0, plainTextCount(refA.text))
        rule.onAllNodes(hasText(centerLabel) and hasClickAction())[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点一次只投一次「打开纠正中心」", 1, openCenterCalls)
        rule.onAllNodes(hasText(centerLabel) and hasClickAction())[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("再点一次就再投一次（这里不做去抖也不吞点击）", 2, openCenterCalls)
        assertEquals("这颗动作不许自己改任何记忆", 0, correctionCalls.size)
        assertEquals("也不许自己撤销", 0, undoCalls.size)
        assertTrue("标错的目标一颗都没被填：浮层归 MemoryCorrectionFlow 的主人", flow.wrongTargetId == null)
        assertTrue("静音的目标一颗都没被填", flow.muteTargetId == null)
        assertEquals("点它不许把四种说法铺开", 0, plainTextCount(wrongLabel))
        assertEquals("也不许把时长浮层带出来", 0, plainTextCount(muteLabel))
    }
}
