package com.lovebrain.app.ui.panel.reply

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.MuteDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「正在被引用的具体内容」这一族在屏幕上的三条判据，每条都能被一种具体的坏实现打破：
 *
 * ① **没有真引用就一块都不画**（`an empty ref list draws nothing`）——
 *    空清单时铺一个带说明句的空壳、或者替这一轮编一条占位条目，红。
 *    这一条盯的就是用户原话里那件没底的事：开着「仅看本轮」的那一轮，旧记忆整块没进
 *    prompt，请求侧交回来的清单**本来就是空的**（`buildReplyUserPromptOnlyThisRound`
 *    返回空 refs，`GenerationEngine` 把那份空清单原样发成事件）。这时屏幕上还画得出
 *    引用，画的就是假引用。所以这一格传进去的夹具就是**空清单**，与那一轮真实交回来的
 *    东西逐字同形，不是随手 `emptyList()` 凑个数。
 *    光这一条还不算钉住：整块被删空同样永远数到 0。反向证人在
 *    `a real ref still renders`——同一个挂载路径传一条真引用，正文、入口、短标签、
 *    首行计数与行尾「纠正中心」都必须读得到。两格一起才说明红绿是判据在起作用。
 * ①ʼ **开关开着的那一轮不许画任何引用，哪怕手里攥着一条**
 *    （`a round-only turn draws no refs even when a non-empty list is handed in`）——
 *    第10节第3条 点名的就是这一格："「仅看本轮」开启后没有旧记忆引用，不能仍显示假引用。"
 *    这一格传的是**非空**清单 + `onlyThisRound = true`（那是一份对不上的 handing，
 *    只可能来自假引用），判据仍然是零句可见文字与零颗可点节点。
 *    它的反向证人是 `the same list renders again the moment the round was not round-only`：
 *    同一份清单、同一处挂载，布尔一关就得四样全在——否则"什么都不画"可能只是把这一族整块删空。
 *    第三条尺是不依赖组合栈的那一颗：`the visibility gate is one truth table over its three
 *    inputs` 把 `memoryRefsFeedVisible` 的整个输入空间钉成"有且只有一种输入画得出"，
 *    两侧同时夹住（漏布尔 ⇒ 那四行红；整块删空 ⇒ "恰好一种"红）。它存在是因为同一族那台
 *    Robolectric 仪器这一轮出过 8 格锚点漂移的红，禁令得有一条不靠那台仪器也读得数的判据。
 * ② **壳只有首行计数与行尾动作，日常页面不常驻技术解释**
 *    （`the feed speaks exactly one sentence of explanation` /
 *    `the menu offers the four wordings plus undo`）——
 *    展开区里说得出字的句子必须**正好是那张表里的那些**：首行「本轮参考 N 条」、短标签、
 *    正文、入口、行尾「纠正中心」。多叠一行使用说明（问句标题、「调整军师本轮参考的记忆」
 *    那类句子已在 2026-11-21 信息减法里退场）就红。行尾菜单里以前每项跟一行技术解释
 *    （「标记为错误内容」「暂停作为续聊素材」…），那五行现在一颗都不许在树里。
 * ③ **入口点开的就是那四种说法 + 撤销，各交回原有的那一种纠正**
 *    （`every wording hands over the correction it names`）——
 *    名字走资源（中英各一份），交出去的 `CorrectionAction` 与改词之前一字不差；
 *    少一项、多一项、或者把「已结束」交成「暂时别提」都红。
 *
 * ⚠ 锚点一律从资源取（`app.getString(...)`）：这一族改走 `stringResource` 之后，
 * 写死中文的钉子在这台 JVM 上恒红（本机默认解析成英文），写死英文又把中文那份锁死。
 * 同一批 key 中英两边是否各有说法，由 `every label in this family has its own words in both languages` 钉。
 *
 * ⚠ 数「一颗入口」时判据是**文案 + 带点击动作**两条一起：`clickable` 会把子节点的文字并进
 * 自己那一份语义，只按文案取会同时命中那颗纯文字节点与外层可点节点（同一件事见
 * `MemoryCorrectionFlowTest` 的 `openRowCorrectionMenu`）。数句子用的又是另一把尺
 * （把所有节点的文案去重成一张集合），两套各有各的用处，别混用。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoryRefsFeedTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    // ── 这一族的可见名字：与生产取的是同一份资源 ─────────────────────────
    private val entryLabel get() = app.getString(R.string.memory_fix_entry)
    private val centerLabel get() = app.getString(R.string.memory_center_entry)

    /** 容器首行的计数句：N 必须是真条数，测试也从同一份资源按同一颗格式拼 */
    private fun summaryOf(count: Int): String = app.getString(R.string.memory_refs_summary, count)
    private val wrongLabel get() = app.getString(R.string.memory_action_wrong)
    private val muteLabel get() = app.getString(R.string.memory_action_mute)
    private val finishedLabel get() = app.getString(R.string.memory_action_finished)
    private val wrongPersonLabel get() = app.getString(R.string.memory_action_wrong_person)
    private val undoLabel get() = app.getString(R.string.memory_action_undo)

    /** 菜单里被撤掉的那几行技术解释：它们以前是内联中文，哪一语言下都不该再出现 */
    private val retiredGlosses = listOf(
        "标记为错误内容", "这件事已结束", "暂停作为续聊素材", "归属错误，暂时隔离", "恢复可信注入"
    )

    private val refA = MemoryRef(
        id = "mem-a", kbId = "kb", kind = MemoryKind.PROFILE,
        text = "她说过周五要交毕业设计", sourcePath = "understand/her.md"
    )

    /** 调用方交回来的动作，这一格只记录、不落盘 */
    private var correctionCalls = mutableListOf<Triple<String, CorrectionAction, MuteDuration>>()
    private var undoCalls = mutableListOf<String>()

    /**
     * 「这一行刚交出去过纠正」的登记账：四种说法都必须经 [com.lovebrain.app.ui.panel.reply.MemoryRefsSection]
     * 的 `onRefCorrected` 交回一次（新契约——登记住在调用方 ResultArea 的
     * `remember(generationRoundId)`，清单只消费入参）。
     */
    private var registeredIds = mutableListOf<String>()
    private val flow = MemoryCorrectionFlow()

    private fun mount(refs: List<MemoryRef>, showRefs: Boolean, onlyThisRound: Boolean = false) {
        correctionCalls.clear()
        undoCalls.clear()
        registeredIds.clear()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                // 调用方那一侧的同一份契约（ResultArea）：登记账住在调用方，
                // 纠正经 onRefCorrected 加一条、撤销把那一条摘出去，清单只读入参
                var correctedIds by remember { mutableStateOf(emptySet<String>()) }
                val onRefCorrected: (String) -> Unit = {
                    registeredIds.add(it)
                    correctedIds = correctedIds + it
                }
                val onRefUndo: (String) -> Unit = {
                    correctedIds = correctedIds - it
                    undoCalls.add(it)
                }
                Box(modifier = Modifier.width(360.dp).height(900.dp)) {
                    Column {
                        MemoryRefsSection(
                            memoryRefs = refs,
                            showRefs = showRefs,
                            onCorrection = { id, action, _, duration ->
                                correctionCalls.add(Triple(id, action, duration))
                            },
                            onUndoCorrection = onRefUndo,
                            correctionFlow = flow,
                            onlyThisRound = onlyThisRound,
                            correctedIds = correctedIds,
                            onRefCorrected = onRefCorrected
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 树里说得出字的每一句（去重：外层可点节点会把子节点的文字并进自己那一份语义） */
    private fun spokenTexts(): Set<String> = rule.onAllNodes(
        SemanticsMatcher("节点带可见文字") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
        }
    ).fetchSemanticsNodes()
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        .toSet()

    private fun clickableCount(): Int =
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size

    /** 数"一颗入口"要文案与点击动作一起取，理由写在类头那条 ⚠ 上 */
    private fun buttonCount(label: String): Int =
        rule.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().size

    private fun plainTextCount(text: String): Int =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /** 判据那一格的夹具：只要**条数**是真的，正文内容对这一块没影响 */
    private fun refsOf(count: Int): List<MemoryRef> =
        List(count) { index -> refA.copy(id = "mem-$index") }

    private fun resFor(tag: String): Resources {
        val cfg = Configuration(app.resources.configuration)
        cfg.setLocales(LocaleList.forLanguageTags(tag))
        return app.createConfigurationContext(cfg).resources
    }

    private fun text(tag: String, id: Int, vararg args: Any): String =
        if (args.isEmpty()) resFor(tag).getString(id) else resFor(tag).getString(id, *args)

    /** 每条前面那个短标签：走资源，测试也从同一份资源取，不抄第二份中文 */
    private fun kindLabel(ref: MemoryRef): String {
        val id = when (ref.kind) {
            MemoryKind.PROFILE -> R.string.memory_kind_profile
            MemoryKind.SCENE -> R.string.memory_kind_scene
            MemoryKind.ONGOING -> R.string.memory_kind_ongoing
            MemoryKind.LESSON -> R.string.memory_kind_lesson
        }
        return "[${app.getString(id)}]"
    }

    /**
     * ① 空清单 ⇒ 整棵树零句可见文字、零颗可点节点。
     *
     * 三种坏实现都被这一条盖住：空壳（画一张带说明句和「收起」的框——多出两句字、一颗可点）、
     * 假引用（替这一轮编一条"本轮没有旧记忆"的占位条目，同样从 0 涨上去）、
     * 以及整块被删空（那一半由 `a real ref still renders` 顶住）。
     */
    @Test
    fun `an empty ref list draws nothing`() {
        // 「仅看本轮」那一轮请求侧交回来的就是这种空清单
        mount(refs = emptyList(), showRefs = true)
        assertEquals(
            "空清单不该念出任何一句（空壳或占位条目都会让它涨过 0），实到：${spokenTexts()}",
            emptySet<String>(), spokenTexts()
        )
        assertEquals("空清单不该有可点入口", 0, clickableCount())
    }

    /** ① 的反向证人：真引用进来必须念得出正文、入口、短标签、首行计数与行尾动作 */
    @Test
    fun `a real ref still renders`() {
        mount(refs = listOf(refA), showRefs = true)
        assertEquals("真引用的正文必须读得到", 1, plainTextCount(refA.text))
        assertEquals("真引用旁边必须恰好有一颗入口", 1, buttonCount(entryLabel))
        assertEquals("首行计数小字念的是真条数", 1, plainTextCount(summaryOf(1)))
        assertEquals("行尾那颗「纠正中心」常驻动作必须在", 1, buttonCount(centerLabel))
        assertEquals("正文旁边那颗短标签走的是资源，不是 Kotlin 常量",
            1, plainTextCount(kindLabel(refA)))
    }

    /**
     * ①ʼ **开关开着的那一轮，手里就算攥着一条引用也不许画**（第10节第3条「仅看本轮开启后
     * 没有旧记忆引用，不能仍显示假引用」那一格的显示侧判据）。
     *
     * 传进去的是**非空**清单——这正是坏实现会露馅的形状：这一轮的请求正文里根本没有
     * 画像（见 `ProactiveRoundScopeTest`：开着开关时知识库读取次数为 0、旧记忆标记一条都不进），
     * 于是屏幕上任何一条"[画像] 她说过周五要交毕业设计"都是**假引用**——用户点得着、
     * 能改能撤销，改的却是这一轮压根没用过的记忆。
     *
     * 三种坏实现都被这一格打破：
     * - **没接这颗布尔**（`visible` 只看 `memoryRefs.isNotEmpty()`，也就是这一格改之前的写法）
     *   → 正文/入口/标签/首行计数四样全部涨过 0，红；
     * - **接了但顺手补一句解释**（"本轮只看当前对话，没有引用旧记忆"之类）
     *   → 可见文字从 0 变成 1，红；
     * - **画一颗灰掉的开关/占位条目** → 可点数或文字数涨过 0，红。
     */
    @Test
    fun `a round-only turn draws no refs even when a non-empty list is handed in`() {
        mount(refs = listOf(refA), showRefs = true, onlyThisRound = true)
        assertEquals(
            "开着「仅看本轮」时这一族一个字都不该念出来（手里的清单是假引用），实到：${spokenTexts()}",
            emptySet<String>(), spokenTexts()
        )
        assertEquals("开着「仅看本轮」时不该有可点的入口或撤销", 0, clickableCount())
        assertEquals("那条真引用的正文必须读不到", 0, plainTextCount(refA.text))
        assertEquals("修正入口必须一颗都不许画", 0, buttonCount(entryLabel))
    }

    /**
     * 上面那一格的反向证人：**同一份清单、同一处挂载**，把那颗布尔关掉就得全都在。
     * 没有这一格，"开着开关什么都不画"可能只是把整块删空了——那既不许算作 第10节第3条 达标，
     * 也会把"修正记忆"这一族整条删掉（用户原话要求保留这个机制）。
     */
    @Test
    fun `the same list renders again the moment the round was not round-only`() {
        mount(refs = listOf(refA), showRefs = true, onlyThisRound = false)
        assertEquals("关闭开关的那一轮正文必须读得到", 1, plainTextCount(refA.text))
        assertEquals("关闭开关的那一轮恰好一颗入口", 1, buttonCount(entryLabel))
        assertEquals("关闭开关的那一轮首行计数在", 1, plainTextCount(summaryOf(1)))
    }

    /**
     * 展开态翻不动它：开着「仅看本轮」时**展开这一颗入口本身**也不该留下任何可点痕迹。
     * 打破的坏实现：把那颗布尔只挂在清单上、入口仍在卡片行下方常驻，用户点下去得一块空白。
     */
    @Test
    fun `opening the feed on a round-only turn still shows nothing`() {
        mount(refs = listOf(refA), showRefs = false, onlyThisRound = true)
        assertEquals(emptySet<String>(), spokenTexts())
        assertEquals(0, clickableCount())
    }

    /**
     * 上面那三格量的是**屏幕上画得出什么**；这一格量的是**判据本身**
     * （`memoryRefsFeedVisible`，生产与探针共用的那一颗纯函数）。
     *
     * 为什么两把尺都要：Robolectric 那台仪器这一轮恰恰是红源（同一族里
     * `MemoryCorrectionFlowTest` 的锚点漂了 8 格），"开着「仅看本轮」时节点集为空"这条禁令
     * 如果不留一条不依赖组合栈的读数，就只能等那台仪器绿了才可证。判据这一层还能被离线探针
     * 直接编真生产码跑一遍这张真值表（）。
     *
     * 判据取的是**整个输入空间**而不是抽两行：九种输入里画得出的只有
     * 「展开态开着 + 手上有真引用 + 这一份结果当时不是仅看本轮」那**一种形状**
     * （表里那两行只差条数，1 条与 3 条都必须画得出）。
     * - 只钉"开着 ⇒ 不画"这一侧：把整块删空的实现同样恒绿（那一侧由 `a real ref still
     *   renders` 与这一格"必须恰好画得出一种"两头夹）；
     * - 只钉"关着 ⇒ 画"这一侧：漏掉那颗布尔的实现恒绿（`onlyThisRound` 那四行立刻红）；
     * - 把 `showRefs` 也当成"能不画"的挡箭牌（即只按 `!onlyThisRound` 判）：
     *   `a collapsed feed leaves only the one-line summary` 那一侧同样会红，因为收起态
     *   清单本体本来就不画。
     */
    @Test
    fun `the visibility gate is one truth table over its three inputs`() {
        val space = listOf(
            // refCount / showRefs / onlyThisRound ⇒ 该不该画这一块
            VisibilityRow(1, showRefs = true, onlyThisRound = false, visible = true),
            VisibilityRow(3, showRefs = true, onlyThisRound = false, visible = true),
            VisibilityRow(1, showRefs = true, onlyThisRound = true, visible = false),
            VisibilityRow(3, showRefs = true, onlyThisRound = true, visible = false),
            VisibilityRow(1, showRefs = false, onlyThisRound = false, visible = false),
            VisibilityRow(1, showRefs = false, onlyThisRound = true, visible = false),
            VisibilityRow(0, showRefs = true, onlyThisRound = false, visible = false),
            VisibilityRow(0, showRefs = true, onlyThisRound = true, visible = false),
            VisibilityRow(0, showRefs = false, onlyThisRound = false, visible = false),
        )
        space.forEach { row ->
            assertEquals(
                "输入「${row.describe()}」的读数（判据只有一颗，改哪一条都要红在对应那一行）",
                row.visible,
                memoryRefsFeedVisible(refsOf(row.refCount), row.showRefs, row.onlyThisRound)
            )
        }
        val drawn = space.filter {
            memoryRefsFeedVisible(refsOf(it.refCount), it.showRefs, it.onlyThisRound)
        }
        // 汇总两条，各自只咬一侧——缺任何一条，另一边都能被"整块删空/整块常画"蒙过去
        assertEquals(
            "能画得出的输入必须恰好是「展开态开着 + 手上有真引用 + 这一轮不是仅看本轮」那几种",
            space.filter { !it.onlyThisRound && it.showRefs && it.refCount > 0 }, drawn
        )
        assertEquals(
            "禁令那一侧：开着「仅看本轮」的输入里，画得出这一块的一个都不许有",
            0, space.count {
                it.onlyThisRound &&
                    memoryRefsFeedVisible(refsOf(it.refCount), it.showRefs, it.onlyThisRound)
            }
        )
        assertEquals(
            "反向证人那一侧：关着开关 + 展开态开着 + 手上有真引用，必须全部画得出（少一种就是整块被删空）",
            space.count { !it.onlyThisRound && it.showRefs && it.refCount > 0 },
            drawn.size
        )
    }

    /**
     * ② 展开区里说得出字的句子**正好是那五句**。
     *
     * 判据取集合而不是取条数：外层可点节点会把子节点文字并进去，数条数会把「入口」数成两颗，
     * 那种尺既不说明问题也拦不住多写一行说明。多出来的一句（第二行说明、重复念的入口）
     * 会以"集合里多一项"的形式红在这里。
     */
    @Test
    fun `the feed speaks exactly one sentence of explanation`() {
        mount(refs = listOf(refA), showRefs = true)
        assertEquals(
            "展开区里的每一句可见文字都该在这张表里；壳只剩首行计数与行尾动作，不再有说明句",
            setOf(summaryOf(1), kindLabel(refA), refA.text, entryLabel, centerLabel),
            spokenTexts()
        )
        assertEquals("首行计数只念得出一次", 1, plainTextCount(summaryOf(1)))
        assertTrue(
            "首行必须短到一行内说完（信息减法：壳不许长成说明书），实到「${summaryOf(1)}」",
            !summaryOf(1).contains('\n') && summaryOf(1).count { it == '.' || it == '。' } <= 1
        )
    }

    /** 收起态只留下首行那一行紧凑占位：清单正文与逐条入口都不占地方，说明句不许回来 */
    @Test
    fun `a collapsed feed leaves only the one-line summary`() {
        mount(refs = listOf(refA), showRefs = false)
        assertEquals("收起时清单正文一个字都不念", 0, plainTextCount(refA.text))
        assertEquals("收起时逐条入口也不在", 0, buttonCount(entryLabel))
        assertEquals("留下的只有首行计数小字", 1, plainTextCount(summaryOf(1)))
        assertEquals("和行尾那颗「纠正中心」常驻动作", 1, buttonCount(centerLabel))
        assertEquals("除这两颗外不该有多出来的可点节点", 2, clickableCount())
    }

    /**
     * ③ 行尾入口点开是**四项短词**，每项一行；以前那几行技术解释一颗都不许在。
     *
     * 「撤销」**不在这一档**：留在清单里的引用按定义都还没被纠正（纠正过的会被注入侧过滤掉），
     * 对它点撤销只会得到一次注定的假失败。撤销只在"这一行真的交出去过纠正"之后出现，
     * 那一格在下面 `undo appears only after this row was corrected`。
     */
    @Test
    fun `the menu offers the four wordings and no undo before any correction`() {
        mount(refs = listOf(refA), showRefs = true)
        assertEquals("菜单没展开时行上只该有入口这一颗可点的说法",
            1, buttonCount(entryLabel))
        rule.onAllNodes(hasText(entryLabel) and hasClickAction())[0].performClick()
        rule.mainClock.advanceTimeBy(16L)

        val menuLabels = listOf(wrongLabel, muteLabel, finishedLabel, wrongPersonLabel)
        menuLabels.forEach { label ->
            assertEquals("菜单里「$label」应当恰好一颗按钮，实到 ${buttonCount(label)} 颗",
                1, buttonCount(label))
        }
        assertEquals(
            "没纠正过这一条时不该出现撤销（点下去只会是一次假失败）",
            0, buttonCount(undoLabel)
        )
        retiredGlosses.forEach { gloss ->
            assertEquals("菜单里不该再常驻技术解释「$gloss」", 0, plainTextCount(gloss))
        }

        val targets = probe.actionableTargets(rule, "修正记忆菜单")
        val menuTargets = targets.filter { it.label in menuLabels }
        assertEquals(
            "菜单里应当恰好四颗可点的说法：" + menuTargets.joinToString { it.describe() },
            4, menuTargets.size
        )
        menuTargets.forEach { t ->
            assertTrue("每一项都得够全站下限：" + t.describe(), !t.tooSmall(probe.floorDp))
        }
    }

    /**
     * ③ʼ 「撤销」的出场条件：这一行**刚刚真的交出去过纠正**。
     *
     * 防的是旧形状：每条引用恒带一颗「撤销」，而未纠正的 id 在
     * `KnowledgeMemoryStore.undo` 那里返回 false——界面把一次本来就该是无操作的点按
     * 报成"撤销失败"。撤销完这一颗必须退场（本地记账跟着清），不留一颗恒假的出口。
     */
    @Test
    fun `undo appears only after this row was corrected`() {
        mount(refs = listOf(refA), showRefs = true)

        fun openMenu() {
            rule.onAllNodes(hasText(entryLabel) and hasClickAction())[0].performClick()
            rule.mainClock.advanceTimeBy(16L)
        }

        openMenu()
        rule.onAllNodes(hasText(finishedLabel))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            "「$finishedLabel」交回的仍是 FINISHED 那一笔",
            listOf(Triple("mem-a", CorrectionAction.FINISHED, MuteDuration.UNTIL_RESTORE)),
            correctionCalls.toList()
        )

        openMenu()
        assertEquals("纠正过之后撤销必须出现", 1, buttonCount(undoLabel))
        rule.onAllNodes(hasText(undoLabel))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("撤销那颗交回的是这条记忆的 id", listOf("mem-a"), undoCalls.toList())

        openMenu()
        assertEquals("撤销完之后这一颗要退场（不留恒假的出口）", 0, buttonCount(undoLabel))
    }

    /**
     * 四种说法 + 撤销交出去的仍是**原有的那几种纠正**，一个都没换：
     * 这条不对 → 原来那颗"标错"浮层（草稿流程不变）；暂时别提 → 原来那颗时长浮层；
     * 已结束 / 不是她的 → 直接落 `FINISHED` / `WRONG_PERSON`；撤销 → 撤销。
     */
    @Test
    fun `every wording hands over the correction it names`() {
        mount(refs = listOf(refA), showRefs = true)

        fun pick(label: String) {
            rule.onAllNodes(hasText(entryLabel) and hasClickAction())[0].performClick()
            rule.mainClock.advanceTimeBy(16L)
            rule.onAllNodes(hasText(label))[0].performClick()
            rule.mainClock.advanceTimeBy(16L)
        }

        pick(wrongLabel)
        assertEquals("「$wrongLabel」应打开原来那颗标错浮层", "mem-a", flow.wrongTargetId)

        rule.runOnIdle { flow.dismiss() }
        pick(muteLabel)
        assertEquals("「$muteLabel」应打开原来那颗时长浮层", "mem-a", flow.muteTargetId)

        rule.runOnIdle { flow.dismiss() }
        pick(finishedLabel)
        pick(wrongPersonLabel)
        assertEquals(
            "「$finishedLabel」/「$wrongPersonLabel」交回的 action 与时长必须与改词之前逐字相同",
            listOf(
                Triple("mem-a", CorrectionAction.FINISHED, MuteDuration.UNTIL_RESTORE),
                Triple("mem-a", CorrectionAction.WRONG_PERSON, MuteDuration.UNTIL_RESTORE)
            ),
            correctionCalls.toList()
        )
        // 新契约：登记账住在调用方（ResultArea 的 remember(generationRoundId)），四种说法
        // 每一种都必须经 onRefCorrected 交回这一条，否则「撤销」永远不出场
        assertEquals(
            "四种说法都得把这一条经 onRefCorrected 登记成「刚纠正过」",
            listOf("mem-a", "mem-a", "mem-a", "mem-a"),
            registeredIds.toList()
        )

        pick(undoLabel)
        assertEquals("撤销那颗交回的是这条记忆的 id", listOf("mem-a"), undoCalls.toList())
    }

    /**
     * 每条标签在中文下都能解析出非空说法：少一条 key，Android **不报错**，直接回落到默认那份
     * （本项目里是中文）。本项目已改为中文-only（删掉了 `values-en`），不再做中英逐键比；
     * 这里只钉"每条 key 在中文下都解析出非空、且「更多 N 条」真的把条数拼进句子"。
     */
    @Test
    fun `every label in this family resolves to its own words`() {
        val watched = listOf(
            R.string.memory_fix_entry,
            R.string.memory_refs_summary,
            R.string.memory_center_entry,
            R.string.memory_action_wrong,
            R.string.memory_action_mute,
            R.string.memory_action_finished,
            R.string.memory_action_wrong_person,
            R.string.memory_action_undo,
            R.string.memory_kind_profile,
            R.string.memory_kind_scene,
            R.string.memory_kind_ongoing,
            R.string.memory_kind_lesson
        )
        val blank = mutableListOf<String>()
        for (id in watched) {
            val zh = text("zh", id)
            if (zh.isBlank()) blank += "id=$id 解析成空白"
        }
        val moreZh = text("zh", R.string.memory_refs_more, 3)
        assertTrue("「更多 N 条」要把条数拼进句子：zh=$moreZh", moreZh.contains('3'))
        val summaryZh = text("zh", R.string.memory_refs_summary, 2)
        assertTrue("「本轮参考 N 条」要把真条数拼进句子：zh=$summaryZh", summaryZh.contains('2'))
        assertTrue(
            "这些标签在中文下解析成空白：\n" + blank.joinToString("\n"),
            blank.isEmpty()
        )
    }
}

/**
 * [MemoryRefsFeedTest] 那格真值表的一行：**输入**（清单条数 / 展开态 / 这一份结果当时是不是
 * 「仅看本轮」）与**该不该画**一起写死，改判据的人得同时改这张表，而不是只在屏幕上试一种。
 */
private data class VisibilityRow(
    val refCount: Int,
    val showRefs: Boolean,
    val onlyThisRound: Boolean,
    val visible: Boolean
) {
    fun describe(): String =
        "refs=$refCount showRefs=$showRefs onlyThisRound=$onlyThisRound"
}
