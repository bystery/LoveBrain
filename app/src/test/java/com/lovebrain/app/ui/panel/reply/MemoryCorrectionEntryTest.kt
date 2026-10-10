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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「记忆纠正入口」这一族在屏上的**可发现性**判据（M20：结果区那条入口不易发现）。
 *
 * 用户原话的两条硬约束决定了这里只判什么：① 不重做记忆管理平台，所以这里没有新页面、
 * 新抽象层可测；② 先让**当前引用看得见、操作找得到、修正后的内容真影响后续回答**。
 * 前两条落在这些格子里，第三条是数据层的事（不在本文件射程，见交付说明）。
 *
 * 每一格都能被一种具体的坏实现打破：
 * - **① 本轮没有真引用 ⇒ 出口仍在树上且点得开**
 *   （`the exit survives a round with no real refs`）：坏实现=把出口也挂在"有真引用"之后
 *   （`if (memoryRefs.isNotEmpty())` 包住整条出口），那一格数到 0 颗可点节点。
 *   同一格里那颗入口的角色与热区仍按全站下限判（旧形状是内联 `Text().clickable{}`，
 *   点得中但读屏说不出干什么）。
 * - **② 开着「仅看本轮」的那一轮，仍然看得见"这一刻用的是什么上下文"并找得到出口**
 *   （`a round-only turn keeps the exit and still says which context it used`）：
 *   旧实现是"没有真引用就一块都不画"——清单那块**必须**继续不画（第10节第3条：那一轮交回来的
 *   是空清单，画得出引用就是假引用），但**出口与那一句上下文说明跟着一起消失**就是这一格要红的
 *   那一半。夹具故意传进一份**非空**清单 + `onlyThisRound = true`（那份"对不上"的手上货），
 *   于是断言两头都咬：正文一条读不到（假引用 ⇒ 红），出口与说明句各恰好一颗/一句（整块不画 ⇒ 红）。
 * - **③ 那份清单是整轮共享的**（`every card toggles the same single list`）：
 *   八张卡下方那八条入口翻的是同一块展开区。坏实现是给某一张卡一份分身清单
 *   （数到 2 份正文）、或者给某一条参考编一个"这张卡专属"的归属（点 A 卡只收起 A 下面那块）。
 *   这一格用**行为**判：八张卡下方那八条入口点的是同一颗 toggle，随便点一张，那一份正文整体消失
 *   （按卡分身的话只会收掉那一张下面那块）。
 *   附带一根禁令钉子（`no per-card copy of the list exists`）：清单本体只许住在
 *   `MemoryRefsFeed.kt`，`SchemeCard.kt` 里一次都不许出现——按卡铺清单就是那套假归属。
 * - **④ 点入口只投一次"打开纠正中心"，不自己改数据**
 *   （`one click hands over exactly one open intent`）：这颗组件**没有** `onCorrection` 形参，
 *   所以它结构上就改不了数据；这一格把三件事一起钉：点一次 ⇒ 回调计数 1（点两次 ⇒ 2，
 *   重复投递红）、那四种说法一颗都不弹（自己就地开菜单红）、纠正浮层的主人一个 target 都没被填上
 *   （顺手把 `flow.requestWrong` 之类偷偷调了红）。
 * - **⑤ 有真引用时清单照常，而且那一句说明不说两遍**
 *   （`the shared list still shows and the sentence is not spoken twice`）：反向证人。
 *   坏实现为了"看得见"给空态铺一张假清单（正文从 1 涨不到，但假条目会让那一格另红），
 *   或者把说明句常驻在出口旁边 ⇒ 同一句话在树里出现两次 ⇒ 红（第10节第4条）。
 *
 * ⚠ 锚点全部从资源取（`ctx.getString(...)`）：这台机器的环境解析出英文，写死中文会恒红。
 * 那颗结果区出口的标签走 `memory_fix_entry`（与逐条记忆行上那颗同一个 key），
 * 说明句走 `memory_refs_note`（清单头上那一句，两处共用同一份、只念一遍）。
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
    private val noteLabel get() = ctx.getString(R.string.memory_refs_note)
    private val wrongLabel get() = ctx.getString(R.string.memory_action_wrong)
    private val muteLabel get() = ctx.getString(R.string.memory_action_mute)

    /** 卡下方那条入口的名字（生产里是 `SchemeCardCopy` 那颗常量，测试只读不改） */
    private val cardRefsLabel = SchemeCardCopy.REFERENCE_ENTRY_LABEL

    private val refA = MemoryRef(
        id = "mem-a", kbId = "kb", kind = MemoryKind.PROFILE,
        text = "她说过周五要交毕业设计", sourcePath = "understand/her.md"
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

    /**
     * 把「清单本体 + 那条常驻出口」按**生产里 ResultArea 的接法**挂在一起：
     * 出口自己不看清单空不空，只看那块清单此刻在不在屏上（同一颗 [memoryRefsFeedVisible] 算出来的）。
     */
    private fun mountFeed(
        refs: List<MemoryRef>,
        showRefs: Boolean,
        onlyThisRound: Boolean
    ) {
        val feedOnScreen = memoryRefsFeedVisible(refs, showRefs, onlyThisRound)
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
                            onlyThisRound = onlyThisRound
                        )
                        MemoryCorrectionEntry(
                            feedOnScreen = feedOnScreen,
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
     * ① 本轮一份真引用都没有：清单不画（不许编假引用），**出口照旧在树上、照旧点得开**，
     * 并由那一句说明说出这一刻用的是哪一份上下文。
     */
    @Test
    fun `the exit survives a round with no real refs`() {
        reset()
        mountFeed(refs = emptyList(), showRefs = true, onlyThisRound = false)
        assertEquals("没有真引用时不许编一条出来", 0, plainTextCount(refA.text))
        assertEquals(
            "本轮没有参考记忆时，纠正出口恰恰是唯一能找到的一条路，不许跟着清单一起消失，实到：${spokenTexts()}",
            1, buttonCount(entryLabel)
        )
        assertEquals("那一刻的上下文由那一句说明交代：清单不在屏上时它必须念得出", 1, plainTextCount(noteLabel))
        assertTrue(
            "出口要报得出角色并够全站下限，不能是又一颗「点得中但读屏说不出干什么」的自画文字",
            probe.laid(probe.actionableTargets(rule, "空引用档的纠正出口"))
                .firstOrNull { it.label == entryLabel }
                ?.let { it.role == "Button" && !it.tooSmall(probe.floorDp) } == true
        )
    }

    /**
     * ② 开着「仅看本轮」的那一轮：清单那块**继续一个字不画**（第10节第3条），
     * 但用户仍然看得见"这一刻用的是什么上下文"、仍然找得到纠正出口。
     *
     * 夹具故意传进**非空**清单 + `onlyThisRound = true`：那是只可能来自假引用的一份对不上手。
     * 旧实现（"没有真引用就一块都不画"把整族连出口一起收掉）红在后两句。
     */
    @Test
    fun `a round-only turn keeps the exit and still says which context it used`() {
        reset()
        mountFeed(refs = listOf(refA), showRefs = true, onlyThisRound = true)
        assertEquals("开着「仅看本轮」时画得出的正文都是假引用", 0, plainTextCount(refA.text))
        assertEquals("那一轮的清单不许有多出来的可点条目", 0, buttonCount(ctx.getString(R.string.memory_action_undo)))
        assertEquals(
            "那一轮仍然要看得见出口，否则用户既不知道军师用了什么、也改不动它，实到：${spokenTexts()}",
            1, buttonCount(entryLabel)
        )
        assertEquals("并且那一句上下文说明必须念得出", 1, plainTextCount(noteLabel))
    }

    /**
     * ②ʼ 同一格在**整屏那一档**的读数：「仅看本轮」那一轮，卡片下方那条入口不许留下
     * （清单真给不出来的时刻，那颗入口就是一颗点下去什么都不会出现的死路），
     * 而结果区那条常驻出口仍然在。
     *
     * 坏实现：卡片入口只按"手上有没有清单"判（旧写法 `memoryRefs.isNotEmpty()`），
     * 于是八张卡各挂一颗翻不动的入口 ⇒ 第一句数到 8 红；
     * 或者把出口一起收掉 ⇒ 第二句红。
     */
    @Test
    fun `a round-only turn offers no dead end toggle but keeps the exit`() {
        reset()
        mountResultArea(refs = listOf(refA), expanded = false, frozenOnlyThisRound = true)
        assertEquals("那一轮不许给每张卡挂一颗翻不开的入口", 0, plainTextCount(cardRefsLabel))
        assertEquals("结果区那条出口仍在树上", 1, buttonCount(entryLabel))
        assertEquals("假引用一条都不许念出来", 0, plainTextCount(refA.text))
        assertEquals("那一句上下文说明念得出", 1, plainTextCount(noteLabel))
    }

    /**
     * ③ 那份清单是**整轮共享**的：八张卡下方那八条入口翻的是同一块展开区。
     *
     * 判据用行为而不是文案：八张卡下方那八条入口点的是**同一颗** toggle，随便点一张，
     * 那一份正文都整体消失——若实现给每张卡一份分身清单（或给某条参考编一个"这张卡专属"的归属），
     * 点一张只收得掉那一张下面那块，正文数不会归零，这一句当场红。
     * 清单本体在时，那句说明只在清单头上念一遍（不跟出口各念一遍）。
     */
    @Test
    fun `every card toggles the same single list`() {
        reset()
        mountResultArea(refs = listOf(refA), expanded = true, frozenOnlyThisRound = false)
        assertTrue(
            "有真引用时每张卡下方都该给那条入口（实到 $cardRefsLabel 共 ${plainTextCount(cardRefsLabel)} 颗）",
            plainTextCount(cardRefsLabel) >= 2
        )
        assertEquals("整轮只有**一份**清单：正文只许念得出一次", 1, plainTextCount(refA.text))
        assertEquals("共享的那一份展开区只有一份说明句", 1, plainTextCount(noteLabel))

        // 点**第一张**卡那条入口（八张卡共用同一个 toggle，点哪张都该收掉同一块；
        // 取第一颗是因为纵向档下面那几张可能已经划出视口，量不到落点）
        rule.onAllNodes(hasText(cardRefsLabel) and hasClickAction())[0].performClick()
        rule.mainClock.advanceTimeBy(400L)
        assertEquals(
            "点任意一张卡那条入口翻的都是同一个 toggle：这一颗下去整份清单就该收掉",
            0, plainTextCount(refA.text)
        )
        assertEquals("收掉之后出口照旧在", 1, buttonCount(entryLabel))
        assertEquals("清单收了，那一句说明改由出口念，仍然只念一遍", 1, plainTextCount(noteLabel))
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
     * ④ 点出口只投一次"打开纠正中心"，不自己改数据、不就地开那四种说法。
     *
     * 坏实现各红在一句上：把纠正动作直接接在这颗入口上（回调 0 次 / 纠正表被填）、
     * 重复投递（点一次数到 2）、或者顺手弹开本地那条纠正浮层（`flow` 的两个 target 之一非空）。
     */
    @Test
    fun `one click hands over exactly one open intent`() {
        reset()
        // 收起态挂载：此刻树里叫这个名字的只有结果区那条出口（清单行上那颗同名入口没画出来），
        // 点下去落在谁身上是可确定的，不用靠位置猜。
        mountFeed(refs = listOf(refA), showRefs = false, onlyThisRound = false)
        assertEquals("收起态下这条名字只该属于那颗出口", 1, buttonCount(entryLabel))
        assertEquals("清单没展开，那份正文读不到", 0, plainTextCount(refA.text))
        rule.onAllNodes(hasText(entryLabel) and hasClickAction())[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点一次只投一次「打开纠正中心」", 1, openCenterCalls)
        rule.onAllNodes(hasText(entryLabel) and hasClickAction())[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("再点一次就再投一次（这里不做去抖也不吞点击）", 2, openCenterCalls)
        assertEquals("这颗出口不许自己改任何记忆", 0, correctionCalls.size)
        assertEquals("也不许自己撤销", 0, undoCalls.size)
        assertTrue("标错的目标一颗都没被填：浮层归 MemoryCorrectionFlow 的主人", flow.wrongTargetId == null)
        assertTrue("静音的目标一颗都没被填", flow.muteTargetId == null)
        assertEquals("点入口不许把四种说法铺开", 0, plainTextCount(wrongLabel))
        assertEquals("点入口不许把时长浮层带出来", 0, plainTextCount(muteLabel))
    }

    /**
     * ⑤ 反向证人：**有真引用时清单照常**（这一条不许退化），说明句只在清单头上念一遍，
     * 出口旁边不重复念第二遍。
     */
    @Test
    fun `the shared list still shows and the sentence is not spoken twice`() {
        reset()
        mountFeed(refs = listOf(refA), showRefs = true, onlyThisRound = false)
        assertEquals("有引用时那份正文照旧读得到", 1, plainTextCount(refA.text))
        assertEquals("清单头上那一句说明只念一遍（出口不许再念第二遍）", 1, plainTextCount(noteLabel))
        assertNotEquals("清单在屏上时那一块不许被收空", emptySet<String>(), spokenTexts())
        assertEquals(
            "同名两颗是两回事：清单行上那颗改**这一条**，结果区那条出口开**纠正中心**——都在才算没退化",
            2, buttonCount(entryLabel)
        )
        assertTrue(
            "清单展开时逐条纠正与那条回得去的路都要点得着",
            clickableCount() >= 3
        )
    }
}
