package com.lovebrain.app.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §6.1 末句"禁止创建只在一个页面看起来不一样的按钮/卡片"的结构门禁。
 *
 * 为什么要有这把尺：那 11 行组件表管的是"该有谁"，管不到"页面自己又画了一颗"。
 * 上一轮量到 39 颗异形（`hand/…`、`panel/…` 各处），全靠文档里一段临时脚本数——
 * **脚本不进门禁，数字就会随重构腐烂**：下一颗自画胶囊不会让任何东西变红。
 *
 * ⚠ 这把尺数的是**形状**，不是命名。判定规则（[classify]）：
 * 函数体里出现任何自画容器或自挂交互（`Box(`/`Column(`/`Row(`/`Card(`/`Surface(`/`Dialog(`/
 * `IconButton(`/`.background(`/`.border(`/`clickable`/`selectable`/`BasicTextField`）
 * ⇒ 它是异形；否则它必须只调用一颗 `core/designsystem` 的 `Lb*` 组件才算**委托壳**。
 * 所以"把 `ScreenHeader` 改名叫 `LbWhatever`"骗不过这把尺，"壳里偷偷加一个 Box"当场就红。
 *
 * 三格各判一件事，缺一不可：
 * 1. **委托壳清单**逐颗点名：多一颗红、少一颗也红（壳变少通常意味着有人把壳拆了或里面长了东西）；
 * 2. **异形清单**逐颗点名：多一颗红（新异形要显式登记并写清为什么它表达了不同语义），
 *    少一颗也红（归并成功要回来把账改小，否则账本慢慢虚高）；
 * 3. **这把尺看得见东西**：拿一个已知异形（`PanelHeader`）与一个已知委托壳（`ScreenHeader`）
 *    各判一次；两头里任何一头分错，就说明分类器是死的，那种绿不算数。
 */
class OddShapeOwnershipTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private val designSystemDir get() = File(appRoot, "core/designsystem")

    /** 家族词表——与 §6.1 那张表里"按钮/卡片/行/胶囊"这一类同名族 */
    private val familyName = Regex(
        "^[ \\t]*(?:private |internal |public )?fun ([A-Za-z0-9_]*" +
            "(?:Button|Card|Row|Chip|Pill|Section|Header|Dialog|Sheet|Metric|Badge|Banner)" +
            "[A-Za-z0-9_]*)[ \\t]*\\("
    )

    /** 自画容器 / 自挂交互：出现任何一条就不是"委托壳" */
    private val handDrawnTells = listOf(
        "Box(", "Column(", "Row(", "Card(", "Surface(", "Dialog(", "IconButton(",
        ".background(", ".border(", ".clickable", ".selectable", "BasicTextField"
    )

    private enum class Shape { SHELL, HAND_DRAWN }

    /**
     * 剥掉块注释与行注释，再把字符串字面量整段抹成空引号——注释与文案里写 `Box(` 都不算一处。
     *
     * ⚠ 先统一行尾：这份仓库的源文件是 CRLF。不先去掉 `\r`，
     * "找一栏顶格的 `}` 当函数体结尾"就永远匹配不上——第一发正是这样把四格全打成红的。
     * （它报的是"函数体没扫到结尾"而不是"账本一致"：静默的绿才是最难查的那种坏法。）
     */
    private fun codeLines(text: String): List<String> = text
        .replace("\r\n", "\n").replace("\r", "\n")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
        .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "\"\"")
        .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "\"\"")
        .split("\n")

    /** 顶层声明的开头——用来给"没有大括号的表达式体"画终点 */
    private val topLevelDecl =
        Regex("^(private |internal |public )?(fun |class |object |interface |sealed |enum |data class |val |var |const )")

    private fun classify(lines: List<String>, declIndex: Int): Pair<Shape, String> {
        val indent = lines[declIndex].takeWhile { it == ' ' }
        var i = declIndex + 1
        val body = StringBuilder()
        var closed = false
        while (i < lines.size) {
            val l = lines[i]
            if (l == "$indent}") { closed = true; break }
            if (l.startsWith("@") || topLevelDecl.matches(l)) break
            body.append(l).append('\n'); i++
        }
        assertTrue(
            "函数体既没扫到大括号收尾、也没扫到内容（${lines[declIndex].trim()}）" +
                "——分类器读不动这种形状，宁可失败也别猜",
            closed || body.isNotEmpty()
        )
        val text = body.toString()
        val shape = if (handDrawnTells.any { it in text }) Shape.HAND_DRAWN else Shape.SHELL
        if (shape == Shape.SHELL) {
            assertTrue(
                "被判成委托壳，但体里没调用任何 Lb* 组件：${lines[declIndex].trim()}",
                Regex("(?<![\\w.])Lb[A-Za-z0-9_]*\\s*\\(").containsMatchIn(text)
            )
        }
        return shape to text
    }

    private data class Site(val file: String, val name: String, val shape: Shape)

    private fun scan(): List<Site> {
        assertTrue("找不到 designsystem 目录：$designSystemDir", designSystemDir.isDirectory)
        val out = mutableListOf<Site>()
        appRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            if (f.absolutePath.startsWith(designSystemDir.absolutePath)) return@forEach
            val lines = codeLines(f.readText(Charsets.UTF_8))
            for (i in lines.indices) {
                val m = familyName.find(lines[i]) ?: continue
                var j = i - 1
                while (j >= 0 && lines[j].isBlank()) j--
                if (j < 0 || "@Composable" !in lines[j]) continue
                val (shape, _) = classify(lines, i)
                out += Site(f.name, m.groupValues[1], shape)
            }
        }
        return out
    }

    /** 键 = `文件名#组件名`。还掉一颗就删一项并写清归到哪儿了；新长一颗必须先登记并写明它表达了什么不同语义。 */
    private val expectedHandDrawn = setOf(
        "AccessibilityDisclosureDialog.kt#AccessibilityDisclosureDialog",
        "AiLoadingRow.kt#AiLoadingRow",
        "CaptureAppsScreen.kt#CaptureAppRow",
        "CorrectionCenter.kt#CorrectionRecordRow",
        "CounselingPanel.kt#TemplateChip",
        "DislikeReasonPanel.kt#CategoryChipRow",
        "DislikeReasonPanel.kt#ReasonChipGrid",
        "FeedbackCasesScreen.kt#FilterChip",
        "HomeComponents.kt#AssistantStatusCard",
        "KnowledgeBaseActivity.kt#KbCard",
        "LoveBrainPanelScreen.kt#KbNoticeBanner",
        "LoveBrainPanelScreen.kt#ProfileSuggestionCard",
        "LoveBrainPanelScreen.kt#StageSuggestionCard",
        "LoveBrainPanelScreen.kt#UsageStatsRow",
        "LoveBrainPanelScreen.kt#VectorPill",
        "LoveBrainPanelScreen.kt#VectorPillsRow",
        "MarkdownText.kt#ListRow",
        "MemoryRefsFeed.kt#MemoryRefsSection",
        "OnboardingOptionCard.kt#OnboardingOptionCard",
        "OngoingSection.kt#OngoingSection",
        "PanelHeader.kt#PanelHeader",
        "ProviderSection.kt#MiniSwitchRow",
        "ProviderSection.kt#ProviderEditDialog",
        "ProviderSection.kt#ProviderSection",
        "ReplyInput.kt#RoleChip",
        "ResultArea.kt#InputChangedBanner",
        "ResultArea.kt#SchemeCardsRow",
        "RowAction.kt#RowActionButton",
        "SchemeCard.kt#CardActionIcon",
        "SchemeCard.kt#SchemeCard",
        "SuggestPanel.kt#IntentChip",
        "SuggestPanel.kt#IntentEditorDialog",
        "SuggestPanel.kt#IntentExpiryChip",
        "SuggestPanel.kt#InviteSuggestionCard",
        "SuggestPanel.kt#SuggestStageCard",
        "SuggestPanel.kt#SuggestTipCard",
        "SuggestPanel.kt#TipCategoryHeader"
    )

    /** 已经退化成"只转一次参数"的壳——不是异形，但也不该再长出新形状 */
    private val expectedShells = setOf(
        "OnboardingFlow.kt#OnboardingButton",
        "ScreenHeader.kt#ScreenHeader"
    )


    @Test
    fun `the odd-shape ledger names every hand-drawn site`() {
        val sites = scan()
        assertTrue("扫到 0 处——路径接错了，这把尺会恒绿", sites.isNotEmpty())
        val measured = sites.filter { it.shape == Shape.HAND_DRAWN }
            .map { "${it.file}#${it.name}" }.toSet()
        val grew = (measured - expectedHandDrawn).toSortedSet()
        val repaid = (expectedHandDrawn - measured).toSortedSet()
        assertTrue(
            "异形账本与实到不一致：新增 $grew；已归并/已消失 $repaid。" +
                "新增要写明它表达了什么不同语义，归并要回来把这一项删掉。",
            grew.isEmpty() && repaid.isEmpty()
        )
    }

    @Test
    fun `delegating shells stay exactly the registered ones`() {
        val measured = scan().filter { it.shape == Shape.SHELL }.map { "${it.file}#${it.name}" }.toSet()
        assertEquals(
            "委托壳清单变了（壳里长出容器、或壳被拆掉都要显式认账）",
            expectedShells, measured
        )
    }

    /**
     * 尺自己要有牙：一个已知异形必须被判成 HAND_DRAWN，一个已知委托壳必须被判成 SHELL。
     * 两头里任何一头分错 ⇒ 分类器是死的，上面两格的绿都不算数。
     */
    @Test
    fun `the classifier actually tells the two shapes apart`() {
        val byName = scan().groupBy { "${it.file}#${it.name}" }
        val header = byName["PanelHeader.kt#PanelHeader"]?.firstOrNull()
        val shell = byName["ScreenHeader.kt#ScreenHeader"]?.firstOrNull()
        assertTrue("扫不到 PanelHeader——这把尺看不见东西，判据失效", header != null)
        assertTrue("扫不到 ScreenHeader 壳——同上", shell != null)
        assertEquals("PanelHeader 自己画 Box/Row，必须是异形", Shape.HAND_DRAWN, header!!.shape)
        assertEquals("ScreenHeader 只转参数给 LbTopBar，必须是委托壳", Shape.SHELL, shell!!.shape)
    }

    @Test
    fun `the scanner is not blind on a known hand-drawn file`() {
        // 反向证人：整份 PanelHeader.kt 里至少有一处异形；如果扫描器对这份文件返回空集，
        // 说明它的注释剥离或声明识别先坏了——那时"账本一致"是假的。
        val fileSites = scan().filter { it.file == "PanelHeader.kt" }
        assertTrue("PanelHeader.kt 一个都没扫到 ⇒ 尺瞎了", fileSites.isNotEmpty())
    }
}
