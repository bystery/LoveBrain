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

    /**
     * 自画容器 / 自挂交互：出现任何一条就不是"委托壳"。
     *
     * ⚠ 调用形状必须按**词边界**判，不能按子串判。第一版这里是裸子串，于是
     * `LbDialog(` 命中 tell `Dialog(`、`LbSettingRow(` 命中 tell `Row(`、`SchemeCard(` 命中 `Card(`
     * ——**任何一颗归并成功的设计系统委托都会被误判成异形**，尺会把"修好了"报成"还在欠"。
     * 这个误判是两个并行代理各自独立报上来的（不是我复跑发现的），所以判据改完还加了一格反向证人。
     * 点号开头的（`.background(` 等）保持子串：`Modifier.background(` 本来就该算。
     */
    private val callShapes = listOf(
        "Box", "Column", "Row", "Card", "Surface", "Dialog", "IconButton", "BasicTextField"
    )

    /** 一条 tell 的正则：词边界 + 左括号；预先编好，避免每行重复编译（原始字符串，反斜杠不 escapes） */
    private val callTells = callShapes.map {
        Regex("""(?<![\w.])""" + it + """\s*\(""")
    }

    private val dotTells = listOf(".background(", ".border(", ".clickable", ".selectable")

    /** 体里是否出现任何"自画"信号 */
    private fun hasHandDrawnTell(text: String): Boolean =
        callTells.any { it.containsMatchIn(text) } || dotTells.any { it in text }

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
        val shape = if (hasHandDrawnTell(text)) Shape.HAND_DRAWN else Shape.SHELL
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

    /**
     * 账本来源：**这一版是 1583 格那一次运行打印出来的实测清单，不是手数的。**
     * 25 颗异形 + 13 颗委托壳；上一轮记的是 39 颗，本轮 §6.1 两拍共归并掉 12 颗
     * （`ProviderEditDialog`/`IntentEditorDialog`/`UsageStatsRow`/`UsageStatCell`/
     * `RowActionButton` 本体/`CaptureAppRow` 本体/`CorrectionRecordRow` 本体/`LbSection` 折叠行）。
     */
    private val expectedHandDrawn = setOf(
        "AccessibilityDisclosureDialog.kt#AccessibilityDisclosureDialog",
        "AiLoadingRow.kt#AiLoadingRow",
        "HomeComponents.kt#AssistantStatusCard",
        "KnowledgeBaseActivity.kt#KbCard",
        "VectorPills.kt#VectorPill",
        "VectorPills.kt#VectorPillsRow",
        "PanelSuggestionCards.kt#ProfileSuggestionCard",
        "PanelSuggestionCards.kt#StageSuggestionCard",
        "MarkdownText.kt#ListRow",
        "MemoryRefsFeed.kt#MemoryRefsSection",
        "OnboardingOptionCard.kt#OnboardingOptionCard",
        "OngoingSection.kt#OngoingSection",
        "PanelHeader.kt#PanelHeader",
        "ProviderSection.kt#MiniSwitchRow",
        "ProviderSection.kt#ProviderSection",
        "ResultArea.kt#SchemeCardsRow",
        "SchemeCard.kt#SchemeCard",
        // 2026-09-30：锦囊结果态那一整块从 `SuggestPanel.kt` 搬进 `ui/panel/suggest/SuggestResultContent.kt`
        // （§7 第二步"巨石按行为块下降"那一格），四处只是换了文件，一处没少。
        "SuggestResultContent.kt#InviteSuggestionCard",
        "SuggestResultContent.kt#SuggestStageCard",
        "SuggestResultContent.kt#SuggestTipCard",
        "SuggestResultContent.kt#TipCategoryHeader",
        // ⚠ 这一处是**新看见的**，不是新长出来的：「要避开的说法」那两格的表头以前写在函数体里，
        // 搬家之后成为顶层 @Composable，才被这把按"顶层声明"认形状的尺扫到。
        // 语义：它是**内容块的表头**（多行 ✗ 列表的标题），不是状态条、不是按钮；
        // 公共件里 `LbSection` 管的是"一节"的外壳，对不上这一处 ⇒ 暂留账上，等 §6.1 那一批判决走完再定。
        "SuggestResultContent.kt#SuggestAvoidHeader",
        // 2026-10-01：谈心模板 chip 行从 CounselingPanel.kt 搬进 CounselingTemplateChips.kt，
        // 成为顶层 @Composable 才被这把按"顶层声明"认形状的尺扫到——
        // 它画 Box + Row + 渐隐遮罩，是内容容器不是委托壳。
        "CounselingTemplateChips.kt#CounselingTemplateChips"
    )

    /** 已经退化成"只转一次参数"的壳——不是异形，但也不该再长出新形状 */
    private val expectedShells = setOf(
        "CaptureAppsScreen.kt#CaptureAppRow",
        "CorrectionCenter.kt#CorrectionRecordRow",
        "CounselingTemplateChips.kt#TemplateChip",
        "DislikeReasonPanel.kt#CategoryChipRow",
        "DislikeReasonPanel.kt#ReasonChipGrid",
        "FeedbackCasesScreen.kt#FilterChip",
        "LoveBrainPanelScreen.kt#KbNoticeBanner",
        "OnboardingFlow.kt#OnboardingButton",
        "ProviderSection.kt#ProviderEditDialog",
        "ReplyInput.kt#RoleChip",
        "ResultArea.kt#InputChangedBanner",
        "RowAction.kt#RowActionButton",
        "ScreenHeader.kt#ScreenHeader",
        "SuggestPanel.kt#IntentChip",
        "SuggestPanel.kt#IntentEditorDialog",
        // 2026-09-30：有效期那三颗单选 chip 从自画那条 Modifier 链归进 `LbChip`（Single 互斥），
        // 留下"只转一次参数"的壳——`LbChipStyles.filled.copy(...)` 调数，不是在页面里再画一条链。
        "SuggestPanel.kt#IntentExpiryChip",
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

    /**
     * 反向证人（这一格专门钉上面那个误判）：
     * 三颗**真委托**必须判成 SHELL，一颗**真自画**必须判成 HAND_DRAWN。
     * 判据是拿分类器本体跑的，不是我手数的——把词边界退回子串，这三颗当场被误判成异形。
     */
    @Test
    fun `delegating calls to design-system widgets are not mistaken for hand-drawn ones`() {
        fun shapeOf(body: String) = hasHandDrawnTell(body)

        assertTrue(
            "LbDialog( 不该被当成自画 Dialog(，LbSettingRow( 不该被当成自画 Row(，SchemeCard( 不该被当成自画 Card(",
            !shapeOf("    LbDialog(title = t, onDismissRequest = d) { }") &&
                !shapeOf("    LbSettingRow(title = t, subtitle = s) { }") &&
                !shapeOf("    SchemeCard(data) { }")
        )
        assertTrue("真自画的 Dialog/Row/Card 必须仍然算异形",
            shapeOf("    Dialog(onDismissRequest = d) { }") &&
                shapeOf("    Row(horizontalArrangement = null) { }") &&
                shapeOf("    Card(colors = c) { }"))
        assertTrue(".background( 这种修饰符仍然算异形", shapeOf("    Modifier.background(Primary)"))
        // 分类器真跑一遍：这两颗是生产里的真委托，形状必须是 SHELL
        val measured = scan().filter { it.name == "ScreenHeader" || it.name == "OnboardingButton" }
            .associate { "${it.file}#${it.name}" to it.shape }
        assertEquals(
            "生产里这两颗必须是委托壳",
            mapOf("ScreenHeader.kt#ScreenHeader" to Shape.SHELL,
                "OnboardingFlow.kt#OnboardingButton" to Shape.SHELL),
            measured
        )
    }

    @Test
    fun `the scanner is not blind on a known hand-drawn file`() {
        // 反向证人：整份 PanelHeader.kt 里至少有一处异形；如果扫描器对这份文件返回空集，
        // 说明它的注释剥离或声明识别先坏了——那时"账本一致"是假的。
        val fileSites = scan().filter { it.file == "PanelHeader.kt" }
        assertTrue("PanelHeader.kt 一个都没扫到 ⇒ 尺瞎了", fileSites.isNotEmpty())
    }
}
