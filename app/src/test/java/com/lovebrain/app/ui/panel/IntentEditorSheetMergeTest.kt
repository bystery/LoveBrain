package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 持续意图那扇编辑浮层的两份证据：**归并没把浮层壳改坏**，以及**期限/已完成那一批控件
 * 已经随判据一起退役**（浮层与设置页必须是同一条语义，否则这页干净了那页还是旧行为）。
 *
 * 归并之前它**已经**站在对的所有者上（面板跑在 overlay 窗口里，Material 的 `AlertDialog`
 * 会抛 `WindowManager.BadTokenException`，那正是 `LbModalSheet` 存在的理由），
 * 但壳里还自己排了一遍版面，而且**这一扇浮层没有标题**——
 * 树的第一格是一段说明文字，别的浮层第一格都是 `LbModalSheetTitle`。
 * 现在：壳只起浮层，标题抬进 `LbModalSheetTitle`、两颗出口走同一份 `LbDialogAction` 词表。
 *
 * ⚠ 挂载口径：`LbModalSheet` **不是另一扇窗口**（同一棵 ComposeView 里自画的遮罩 + 卡片），
 * 所以这一整扇浮层在本机挂得上（`UiMatrix(360).RenderIn` + 推进一帧）。
 * 浮层卡片自带 `verticalScroll`，底部的两颗出口按 `ScrollScan` 的口径滚过一遍取最大面积
 * （滚动容器会把没露面的那颗裁成小读数）。
 *
 * 各格判的事：① 标题读得到且只有一份；② 「取消 / 保存」两颗出口还在、有名字、过 48dp；
 * ③ 结构上证壳真的只起一扇 `LbModalSheet`；④ 期限三档与「已完成」不在场（源码与语义树两边都数）；
 * ⑤ 正在保存时「保存」禁用、点它不落第二次写盘；
 * ⑥ F05A（2026-10-15 书 §6）那一族长度规则：超限那一稿原因可见、按钮灰着还在、正文不丢、
 * 不交盘，正好在上限那一稿整份交出去，而**尺只有一条**（浮层与设置页共用那颗数，两处都不许多抄、
 * 不许留静默截断那一刀）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntentEditorSheetMergeTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val probe by lazy { SemanticsProbe(ctx.resources.displayMetrics.density) }

    /** 标题全文——已还债进 R.string.intent_editor_title；本机无 values-en 源文件，两语言下都解析成中文 */
    private val titleText get() = ctx.getString(R.string.intent_editor_title)

    private fun mount(
        text: String = "周末约她看电影",
        enabled: Boolean = true,
        saving: Boolean = false,
        onSave: (String, Boolean) -> Unit = { _, _ -> }
    ) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                IntentEditorDialog(
                    text = text,
                    enabled = enabled,
                    saving = saving,
                    onSave = onSave,
                    onDismiss = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun `the merged sheet still shows its title and shows it exactly once`() {
        mount()
        val nodes = rule.onAllNodes(hasText(titleText, substring = true)).fetchSemanticsNodes()
        assertEquals(
            "浮层顶上那句话归并后仍要读得到，而且只有一份（两份就是各写各的）：" +
                "实到 ${nodes.size} 个节点",
            1, nodes.size
        )
        // 证人：这棵树确实是那扇浮层，不是空挂载——正文那一栏的标签也在树里
        assertTrue(
            "挂载证人：量不到正文标签说明浮层压根没起来",
            rule.onAllNodesWithText(ctx.getString(R.string.intent_content_label)).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun `cancel and save in the merged sheet are labeled and meet the touch floor`() {
        mount()
        val seen = ScrollScan(rule, probe).toBottom("持续意图浮层（归并后）")
        val missing = listOf("取消", "保存").filter { !seen.containsKey(it) }
        assertTrue(
            "两颗出口都得还在，没量到：$missing；实到：" + seen.keys.sorted(),
            missing.isEmpty()
        )
        val exits = listOf("取消", "保存").map { seen.getValue(it) }
        val unlabeled = exits.filter { !it.labeled }
        assertTrue("出口得有可读名字：" + unlabeled.joinToString { it.describe() }, unlabeled.isEmpty())
        val offenders = exits.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "归并用的就是 `LbModalSheetActions` 那颗动作行（它把 26dp / 22dp 的旧缺陷垫到了下限），" +
                "这里不达标说明出口没走共用形状：" + offenders.joinToString { it.describe() },
            offenders.isEmpty()
        )
        val noRole = exits.filter { it.role != "Button" }
        assertTrue("两颗出口都得报成按钮：" + noRole.joinToString { it.describe() }, noRole.isEmpty())
        assertTrue(
            "没在保存时「保存」点得动：读数 " + seen.getValue("保存").disabled,
            !seen.getValue("保存").disabled
        )
    }

    @Test
    fun `the shell only raises a LbModalSheet and draws nothing of its own`() {
        val code = maskedSheetSource()

        val shell = code.substringAfter("internal fun IntentEditorDialog(")
            .substringBefore("\n@Composable")
        assertEquals(
            "壳里只该起一扇设计系统的浮层：" + shell,
            1, Regex("""LbModalSheet\(""").findAll(shell).count()
        )
        val tells = listOf("Box(", "Column(", "Row(", "Dialog(", ".background(", ".clickable", "BasicTextField")
            .filter { it in shell }
        assertTrue("壳里又自己画了东西：" + tells, tells.isEmpty())

        // 标题槽：整份文件只画一次，两颗出口也只在同一处
        assertEquals("标题槽全文件只该有一处", 1, Regex("LbModalSheetTitle\\(").findAll(code).count())
        assertEquals("动作行全文件只该有一处", 1, Regex("LbModalSheetActions\\(").findAll(code).count())
        assertEquals(
            "标题资源引用只该存在一份（两份就是各写各的）",
            1, Regex("R\\.string\\.intent_editor_title").findAll(code).count()
        )
    }

    /**
     * 期限三档与「已完成」从这一扇浮层退役——语义树与源码两边都数一遍。
     *
     * 为什么两把尺都要：语义树那把看得见"屏上确实没有"，源码那把看得见"接线也没留着"
     * （只删渲染、留着 `IntentExpiry` 参数与那颗 chip，后来人接上去就能让它重新长回来）。
     */
    @Test
    fun `the retired expiry and completed controls are gone from this sheet`() {
        // 按**屏上字面量**数，不按资源键数：那些键随时可能被资源表的主人摘掉，
        // 而这一格判的是"这一排控件不能再画出来"——字形本身就是判据。
        val retiredLabels = listOf("有效期", "一小时", "一天", "一个星期", "已完成")
        mount()
        val present = retiredLabels.filter {
            rule.onAllNodes(hasText(it, substring = false)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("浮层里不该再有期限/已完成那一排：" + present + "；退役的是判据，不是按钮", present.isEmpty())
        // 证人：这把尺对"还在场"的控件确实数得到（启用那一行的标签量得到）
        assertEquals(
            "证人：同一把尺要能数到仍在场的那一行",
            1, rule.onAllNodes(hasText(ctx.getString(R.string.intent_enable_label), substring = false))
                .fetchSemanticsNodes().size
        )

        val code = maskedSheetSource()
        for (retired in listOf("IntentExpiryChip", "IntentExpiry", "IntentStatus", "intent_expiry", "editExpiry", "editStatus")) {
            assertEquals(
                "源码里不该再留『$retired』（旧控件的复活点），实到 ${code.countOccurrencesOf(retired)} 处",
                0, code.countOccurrencesOf(retired)
            )
        }
        // 证人：这几把尺都能数到"复活写法"
        val witness = "IntentExpiryChip(editExpiry == IntentExpiry.ONE_DAY) {} // intent_expiry_one_day" +
            "var editStatus = IntentStatus.ACTIVE"
        assertEquals(1, Regex("IntentExpiryChip").findAll(witness).count())
        assertEquals(2, Regex("editStatus|editExpiry").findAll(witness).count())
        assertEquals(1, Regex("intent_expiry").findAll(witness).count())
    }

    /**
     * 正在保存时那颗「保存」是**禁用**，不是"再点一次就多开一条写盘"。
     * 反例形状（旧的）：按钮恒可用、每次点击都 `scope.launch` 一条新协程——两连写会同时落盘。
     */
    @Test
    fun `save is disabled while a write is in flight and a click there writes nothing`() {
        val saved = mutableListOf<Pair<String, Boolean>>()
        mount(saving = true, onSave = { text, enabled -> saved += text to enabled })

        val seen = ScrollScan(rule, probe).toBottom("持续意图浮层（正在保存）")
        assertTrue(
            "正在保存时「保存」要报成禁用：" + seen.getValue("保存").describe(),
            seen.getValue("保存").disabled
        )
        assertTrue(
            "「取消」不受这一道门影响，用户随时退得出去：" + seen.getValue("取消").describe(),
            !seen.getValue("取消").disabled
        )
        rule.onNodeWithText("保存").performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("禁用那一拍点下去不许交第二次盘", emptyList<Pair<String, Boolean>>(), saved)
    }

    /** 正向对照：没在保存时同一颗按钮把**正文 + 开关**交出去（这就是新的启用/保存合同） */
    @Test
    fun `save hands the body and the switch to the host and nothing else`() {
        val saved = mutableListOf<Pair<String, Boolean>>()
        mount(text = "周末约她看电影", enabled = true, onSave = { text, enabled -> saved += text to enabled })

        rule.onNodeWithText("保存").performClick()
        rule.mainClock.advanceTimeBy(16L)

        assertEquals(1, saved.size)
        assertEquals("周末约她看电影", saved.single().first)
        assertTrue("开关读数跟着走", saved.single().second)
    }

    // ═══════════ F05A（2026-10-15 书 §6）：长度那一把尺 ═══════════

    /**
     * 超限那一稿：原因**看得见**（框下那一行 Error）、按钮**灰着还在**（不是消失），
     * 点它**一个字都不交**，而正文**一个字都不掉**。
     *
     * 反例形状三条：
     * · 只把提示写上、按钮仍可点 ⇒ 下面那句 `assertEquals(emptyList, saved)` 红（超长稿落进盘里）；
     * · 超限把那颗按钮**摘掉** ⇒ `seen.getValue("保存")` 取不到、当场红（用户看不出少了什么）；
     * · 交出去之前先 `take(200)` 削尾 ⇒ "正文一个字都不掉"那句红（书 §6 明令不许静默截断）。
     */
    @Test
    fun `an over-long draft says why stays whole and writes nothing`() {
        val draft = "意".repeat(INTENT_MAX_LENGTH + 1)
        val saved = mutableListOf<Pair<String, Boolean>>()
        mount(text = draft, onSave = { text, enabled -> saved += text to enabled })

        assertEquals(
            "超限那一句原因要在屏上，且只有一份（两处各写一句就是两张脸）",
            1,
            rule.onAllNodes(hasText(ctx.getString(R.string.intent_too_long_error, INTENT_MAX_LENGTH)))
                .fetchSemanticsNodes().size
        )
        val seen = ScrollScan(rule, probe).toBottom("持续意图浮层（超限）")
        assertTrue(
            "超限时「保存」是按不动的，但仍在屏上：" + seen.getValue("保存").describe(),
            seen.getValue("保存").disabled
        )
        assertTrue(
            "「取消」不受这一道门影响，用户随时退得出去：" + seen.getValue("取消").describe(),
            !seen.getValue("取消").disabled
        )
        assertEquals(
            "超限那一稿正文原样留在输入框里（一个字都不许被吃掉）",
            INTENT_MAX_LENGTH + 1,
            editableLength()
        )

        rule.onNodeWithText("保存").performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("超限那一按不交盘", emptyList<Pair<String, Boolean>>(), saved)
    }

    /**
     * 正好**在上限**那一稿是正向对照：门是 `>` 而不是 `>=`，而且交出去的是整份正文。
     *
     * 这一格与上面那一格各挂一次 `setContent`（框架会拒掉一格里第二次挂载，那一段断言从不执行）。
     * 反例形状：把上限写成"达到即拦"⇒ 这里 `saved` 空、当场红；
     * 保存前再削一刀 ⇒ 交出去的那串短于上限，`assertEquals(INTENT_MAX_LENGTH, …)` 红。
     */
    @Test
    fun `a draft at exactly the limit is handed over whole`() {
        val draft = "意".repeat(INTENT_MAX_LENGTH)
        val saved = mutableListOf<Pair<String, Boolean>>()
        mount(text = draft, onSave = { text, enabled -> saved += text to enabled })

        assertEquals("正好在上限那一稿不该挂超限那句", 0,
            rule.onAllNodes(hasText(ctx.getString(R.string.intent_too_long_error, INTENT_MAX_LENGTH)))
                .fetchSemanticsNodes().size)
        rule.onNodeWithText("保存").performClick()
        rule.mainClock.advanceTimeBy(16L)

        assertEquals("上限那一稿正常交一笔", 1, saved.size)
        assertEquals("交出去的是整份正文，不是削过的那一截", INTENT_MAX_LENGTH, saved.single().first.length)
    }

    /**
     * 长度那一把尺**只有一条**（F05A 的源码那一半）：数住在浮层这一颗，设置页那一格**引用**它，
     * 两处都不许多抄一份，也不许留"静默截断"那一刀。
     *
     * 为什么这一句用源码尺：屏上的一切在"抄了第二份数"之后**一模一样**，只有哪天那颗尺动了、
     * 两处入口又各走各的才看得出来——那正是书 §6 说的"两处入口规则不一致"的来路。
     * 语义树那一半（提示可见、按钮置灰、不丢字）由上面两格钉，两边不是同一件事。
     */
    @Test
    fun `the length ruler has exactly one owner and no silent truncation`() {
        val sheet = maskedSheetSource()
        val entry = maskedEntrySource()

        // 那颗尺的唯一一份字面数住在定义那里（跨两个文件数，多一处就是第二把尺）
        assertEquals(
            "整个长度规则只许有一份字面数 200（定义那一颗）：浮层与设置页各抄一份就是两处入口不一致",
            1, listOf(sheet, entry).sumOf { Regex("""\b200\b""").findAll(it).count() }
        )
        for ((name, code) in listOf("浮层" to sheet, "设置页那一格" to entry)) {
            assertTrue("$name 没引用那颗公共的尺 INTENT_MAX_LENGTH", code.contains("INTENT_MAX_LENGTH"))
            assertTrue("$name 引用了同一条超限提示资源（两处念两句话）",
                code.contains("R.string.intent_too_long_error"))
            // 静默截断那一刀：超限已经被"按钮按不动"拦住了，这一刀永远轮不到落下去，
            // 留着它就是留下第二个规则的形状（书 §6 不许静默 take）
            assertEquals("$name 又出现了 take(INTENT_MAX_LENGTH) 那一刀",
                0, Regex("""take\(\s*INTENT_MAX_LENGTH""").findAll(code).count())
        }
        // 反向证人：这几把尺都数得出"坏写法"本身（不然三句是恒绿的空转）
        val witness = "const val INTENT_MAX_LENGTH = 200 // 另一处：if (n > 200) onSave(t.take(INTENT_MAX_LENGTH))" +
            "stringResource(R.string.intent_too_long_error, INTENT_MAX_LENGTH)"
        assertEquals(2, Regex("""\b200\b""").findAll(witness).count())
        assertEquals(1, Regex("""take\(\s*INTENT_MAX_LENGTH""").findAll(witness).count())
        assertTrue(witness.contains("R.string.intent_too_long_error"))
    }

    /** 输入框此刻的正文长度（`EditableText` 那一槽，不是合并后的 label） */
    private fun editableLength(): Int {
        val nodes = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals("这一扇浮层该恰有一颗可编辑节点，实到 ${nodes.size}", 1, nodes.size)
        return nodes.single().config.getOrNull(SemanticsProperties.EditableText)?.text?.length ?: -1
    }

    private fun maskedEntrySource(): String {
        val rel = "ui/panel/settings/SettingsIntentEntry.kt"
        val file = File("src/main/java/com/lovebrain/app/$rel").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/$rel")
        assertTrue("$file 不在了——这一格会恒绿", file.isFile)
        return SourceScan.maskComments(file.readText(Charsets.UTF_8))
    }

    private fun maskedSheetSource(): String {
        val file = File("src/main/java/com/lovebrain/app/ui/panel/IntentEditorSheet.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/IntentEditorSheet.kt")
        assertTrue("$file 不在了——这一格会恒绿", file.isFile)
        return SourceScan.maskComments(file.readText(Charsets.UTF_8))
    }

    private fun String.countOccurrencesOf(needle: String): Int {
        var i = 0
        var n = 0
        while (true) {
            val at = indexOf(needle, i)
            if (at < 0) return n
            n++
            i = at + needle.length
        }
    }
}
