package com.lovebrain.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 可写状态的五种形状——记账单位是 `(形状, 名字)` 这颗二元组，不是名字、更不是数量 */
enum class VmMutableShape { FLOW, VAR, COLL, LATEINIT, LEAKED }

/**
 * 一颗登记在账本上的可写状态。
 *
 * 枚举不能嵌套在别的类里（Kotlin 的限制），所以这两个类型放在文件顶层，
 * 名字带上前缀免得和同包别的尺撞名。
 */
data class VmStateSite(val shape: VmMutableShape, val name: String) : Comparable<VmStateSite> {
    override fun compareTo(other: VmStateSite): Int =
        if (shape != other.shape) shape.ordinal - other.shape.ordinal else name.compareTo(other.name)

    override fun toString(): String = "$shape($name)"
}

/**
 * "状态不许住在 ViewModel 里"这条的结构门禁（ 第5节第2条 第 6 步、第7节 第二步完成定义
 * 第 1 条"LoveBrainViewModel 不再持有 feature 的内部状态"）。
 *
 * 为什么要有这把尺：这几轮把 VM 里各自可写的状态分别搬进了
 * [com.lovebrain.app.feature.composer.ComposerStore]（输入区与消息列表）、
 * [com.lovebrain.app.feature.notice.NoticeBoard]（三条临时提示）、
 * [com.lovebrain.app.feature.provider.ProviderTicketStore]（工单与就绪位）、
 * [com.lovebrain.app.feature.roundcommit.ActualSentRecorder]（已发送回执）、
 * [com.lovebrain.app.feature.profile.ProfileUpdateController]（画像建议卡）、
 * [com.lovebrain.app.feature.reply.ReplyVersionStack]（版本栈）、
 * [com.lovebrain.app.feature.intent.IntentController]（持续意图）。
 * **搬家是可逆的**——下一个人图省事在 VM 里再写一颗 `private val _foo = MutableStateFlow(...)`
 * 就悄悄回到原状，而且不会有任何测试变红（既有行为测试读的是出口，不关心里面谁持有）。
 *
 * ⚠ 这一版把尺换成**五形状 × 具名登记 × 剥注释 × 双向红**，起因是量出来的账比登记的多：
 * 旧那一版只认 `private val _x = MutableStateFlow(`，于是——
 * · 四颗裸 `private var`（`replyGenerationContext`、`recordingRound`、`intentEditorKbName`，
 *   以及已死的 `generateStartTimeMs`）**一整支全盲**。其中 `intentEditorKbName` 装的是
 *   "编辑器绑哪块库"这条**判据**，它和 `_showIntentEditor` 是一对：只搬前者的话卡关了绑定还在、
 *   下一次保存写进一块已经不显示的库，**没有任何测试会红**；
 * · `MutableSharedFlow` / 可变集合 / `lateinit var` / 名字不带 `_` 那几支一支都没有；
 *   这些形状在本仓库真实存在（`data/ApiUsageTracker` 里就有 `MutableSharedFlow`），不是假想敌；
 * · **不剥注释、也不认字符串**：今天 raw 与剥完恰好都等于登记数，那是**碰巧对**不是**按口径对**——
 *   本仓库已经因同类翻过车（说明写在 `UiStringLiteralBudgetTest` 的 `maskComments` 那一层：
 *   按形状认的尺不剥注释，就会被自己写的说明书抵消掉）。本文件的 KDoc 里就满是
 *   "原先这里有几颗 MutableStateFlow"这种话，写一段**行首不带 `*`** 的块注释样例，
 *   或者在日志串里抄一句旧声明，账本立刻虚高。
 *
 * 六格各判一件事，缺一不可：
 * 1. 搬走的那批不许回来（点名的那批，加上"VM 现在确实在通过 store 写"的反向判据）；
 * 2. 剩下的账**逐颗点名登记**，多一颗红、少一颗也红——与 lint 预算同一口径：
 *    还了债不改账本，账本就会随着重构腐烂，最后没人知道还剩多少；
 * 3. 不许有**可写状态以非 private 的形式漏出去**（这一格预算恒为 0，没有基线可躲）；
 * 4. **这把尺看得见东西**（正向证人）：拿生产文件 `SetupViewModel` 证 FLOW 那一支活着，
 *    再拿一份夹具证 VAR / COLL / LATEINIT / 名字不带 `_` 那几支活着——
 *    生产里这几支今天全是 0，不造夹具就是恒真；
 * 5. **注释与字符串不许伪造读数**（反向证人）：写进 `//`、写进行首不带 `*` 的块注释、
 *    写进 KDoc、写进字符串字面量的那些形状都不算；而字符串里含 `//` 时，
 *    **它之后那一行真声明仍要数得到**（不剥注释会假红；不认字符串的剥法会把真声明吃掉——两头钉）；
 * 6. 函数里的局部变量不算状态：只有类级成员（0~4 空格缩进）才进账。
 *
 * ⚠ 账本里的名字**只能来自这一格打印出来的实到清单**，不许照叙述填
 * （同一族坑：数形状的尺必须先扫现状、把数打印出来，照心象填就是假账）。
 * 本次登记 12 颗 = 9 FLOW + 3 VAR；COLL / LATEINIT / LEAKED 三支在生产里实测各 0，
 * 所以那三支**只能靠夹具证明它们不是死正则**——"这一格绿了"本身不构成证据。
 */
class ViewModelStateOwnershipTest {

    private class Frame(val kind: Char, var depth: Int)

    private fun appRoot(): File {
        val root = File("src/main/java/com/lovebrain/app")
        assertTrue("必须在 :app 模块下跑，找不到 $root", root.isDirectory)
        return root
    }

    private fun sourceOf(rel: String): String = File(appRoot(), rel).readText(Charsets.UTF_8)

    /**
     * 已经搬出 ViewModel 的状态，**按新家分组点名**。
     *
     * 名字用的是各 store 从前的私有字段名（带 `_` 前缀那版）——因为"倒退"会长成那个样子：
     * 下一个人图省事，直接在 VM 里再写一颗同名的可写流，读的人完全看不出状态被分成了两份。
     */
    private val movedOut = mapOf(
        "ComposerStore" to listOf(
            "_panelState", "_messages", "_currentRole", "_editingIndex", "_ideaComposeMode",
            "_draftText", "_counselingDraft", "_panelMode", "_outputMode"
        ),
        "NoticeBoard" to listOf("_kbNotice", "_panelWarning", "_vectorUpdate"),
        "ProviderTicketStore" to listOf("_activeTicket", "_providerReady"),
        "ActualSentRecorder" to listOf("_actualSentState"),
        "ProfileUpdateController" to listOf("_profileReview"),
        "ReplyVersionStack" to listOf("_generationHistory", "_currentVersionId"),
        // 持续意图那一族：两颗流 + 一颗**旧尺看不见的裸 var** 一起走
        "IntentController" to listOf("_intentConfig", "_showIntentEditor", "intentEditorKbName"),
        // 第 20 窗口搬出：轮次上下文 + 模式 + 向量 + 阶段建议 + 用量
        "RoundStateStore" to listOf("_generationRoundId", "_onlyThisRound"),
        "ModeController" to listOf("_inputChanged", "_resultMode"),
        "VectorStore" to listOf("_currentVector", "_vectorDelta"),
        "StageSuggestionStore" to listOf("_stageSuggestion"),
        "UsageStatsStore" to listOf("_usageStats")
    )

    /** VM 通过哪个前缀访问那一家子（反向判据：不许绕开 store 另写一条路） */
    private val accessors = mapOf(
        "ComposerStore" to "composer.accept(",
        "NoticeBoard" to "notices.",
        "ProviderTicketStore" to "ticketStore.",
        "ActualSentRecorder" to "actualSent.",
        "ProfileUpdateController" to "profileUpdates.",
        "ReplyVersionStack" to "versions.",
        "IntentController" to "intents.",
        "RoundStateStore" to "roundStateStore.",
        "ModeController" to "modeController.",
        "VectorStore" to "vectorStore.",
        "StageSuggestionStore" to "stageSuggestionStore.",
        "UsageStatsStore" to "usageStatsStore."
    )

    /**
     * 还留在 ViewModel 里的可写状态——**逐颗点名登记**。
     *
     * 第 20 窗口搬走了八颗（轮次上下文、模式、向量、阶段建议、用量），
     * 剩下四颗是台账标 ★不搬 / 排在最后搬的：
     * · `_activeKb`——激活库是中心读数，六个块都读它，不属于任何单个 feature；
     * · `replyGenerationContext`——一轮生成的不可变快照，是所有块的公共上游；
     * · `recordingRound`——本轮提交的重入闩；
     * · `roundScopeHintShown`——仅看本轮首次轻提示的"提示过没有"（落盘格在 SecurePrefs）。
     */
    private val ledger: Map<VmStateSite, String> = mapOf(
        VmStateSite(VmMutableShape.FLOW, "_activeKb") to
            "★不搬：激活库是中心读数（生成 / 锦囊 / 主动发 / 谈心 / 纠正 / 改写 / 意图六个块都读它），" +
                "不属于任何单个 feature；单独搬它就是『搬字段不减行』那条教训的复现",
        VmStateSite(VmMutableShape.VAR, "replyGenerationContext") to
            "→ 轮次上下文那一块：一轮生成的不可变快照，十几个成员读它、是所有块的公共上游，所以排在最后搬",
        VmStateSite(VmMutableShape.VAR, "recordingRound") to
            "→ 本轮提交那一块：下一轮的重入闩，只有那条链读写",
        VmStateSite(VmMutableShape.VAR, "roundScopeHintShown") to
            "→ 仅看本轮首次轻提示那一块：跨会话一次性记录（'提示过没有'，§2.2 第 3 条），" +
                "不参与任何响应式状态图；盘上主人在 SecurePrefs.roundScopeHintShown（AppModule 那对函数接进来），" +
                "VM 只是会话内副本——放 RoundStateStore 得给它接落盘口子，把纯状态持有者拖成第二个持久化主人",
        VmStateSite(VmMutableShape.FLOW, "_knowledgeBasesState") to
            "★不搬：知识库列表是面板设置页与首页共用的中心读数，与 _activeKb 同族——" +
                "单独搬它就是『搬字段不减行』那条教训的复现"
    )

    /**
     * 把**注释、字符串字面量、字符字面量与字符串模板**都按字符换成空格（保住偏移量）。
     *
     * 注释那一半与 `UiStringLiteralBudgetTest.maskComments` 同一口径（字符串感知、块注释可嵌套、
     * 按字符替换为空格）；这里还多剥一层**字面量内容**，因为这把尺认的是**声明形状**，
     * 而形状可以整条藏在串里：`L.w("以前这里写的是 private val _x = MutableStateFlow(0)")`
     * 不剥就是假红。两处没有共用同一份实现，是因为那一格的账按字符区间去重、需要字面量
     * 留在原处，剥了它自己的读数就没了——同一族判据、两种射程，只能各自持有（改任一处都要记得
     * 看另一处，别把"两处口径不同"当成漂移）。
     *
     * 三个都必须处理的坑：
     * · 串里出现的 `//` 不是注释（否则它之后那一行的真声明会被当成注释吃掉）；
     * · **三引号原始串**与串里 `${…}` 的嵌套引号：只数 `"` 的朴素剥法在这里一定错位，
     *   一旦错位就可能把后面几十行当成串、或把真注释当代码——所以这里带一个帧栈
     *   （普通串 / 原始串 / 字符面量 / 模板表达式），模板 `${}` 里面按代码解析、但照样抹成空格；
     * · 块注释可以嵌套，未闭合的块注释吃到文件尾。
     */
    private fun maskComments(src: String): String {
        val out = src.toCharArray()
        val n = out.size
        val frames = ArrayDeque<Frame>()
        var i = 0
        while (i < n) {
            val top = frames.lastOrNull()
            val c = out[i]
            if (top != null && top.kind != 't') {
                // 字符串 / 字符面量：只有转义、终止符与 `${` 有意义；里面的 // 与 /* 都是内容
                if (c == '\\') {
                    out[i] = ' '
                    if (i + 1 < n && out[i + 1] != '\n') out[i + 1] = ' '
                    i += 2
                    continue
                }
                val tripleClose = top.kind == 'r' && c == '"' && i + 2 < n && out[i + 1] == '"' && out[i + 2] == '"'
                val close = (top.kind == 's' && c == '"') || (top.kind == 'c' && c == '\'') || tripleClose
                if (close) {
                    val width = if (tripleClose) 3 else 1
                    for (j in i until i + width) if (j < n) out[j] = ' '
                    i += width
                    frames.removeLast()
                    continue
                }
                if (c == '$' && (top.kind == 's' || top.kind == 'r') && i + 1 < n && out[i + 1] == '{') {
                    out[i] = ' '; out[i + 1] = ' '
                    frames.addLast(Frame('t', 1))
                    i += 2
                    continue
                }
                out[i] = ' '
                i++
                continue
            }
            // 到这里是**代码**：文件级，或字符串模板的表达式里（照样抹成空格）
            val blank = top != null // 模板里的代码也抹掉：类级声明不可能长在 ${} 里
            fun blankTo(end: Int) {
                var j = i
                while (j < end && j < n) {
                    if (out[j] != '\n') out[j] = ' '
                    j++
                }
                i = end
            }
            if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                val end = src.indexOf('\n', i).let { if (it < 0) n else it }
                blankTo(end)
                continue
            }
            if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                var depth = 1
                out[i] = ' '; out[i + 1] = ' '
                i += 2
                while (i < n && depth > 0) {
                    if (out[i] == '/' && i + 1 < n && out[i + 1] == '*') {
                        depth++; out[i] = ' '; out[i + 1] = ' '; i += 2; continue
                    }
                    if (out[i] == '*' && i + 1 < n && out[i + 1] == '/') {
                        depth--; out[i] = ' '; out[i + 1] = ' '; i += 2; continue
                    }
                    if (out[i] != '\n') out[i] = ' '
                    i++
                }
                continue
            }
            if (c == '"') {
                if (i + 2 < n && out[i + 1] == '"' && out[i + 2] == '"') {
                    for (j in i until i + 3) out[j] = ' '
                    frames.addLast(Frame('r', 0)); i += 3; continue
                }
                // 本行内没有收尾的引号就不是合法字面量：别把后面几十行当成串吃掉
                val close = simpleEnd(out, i + 1, n, '"')
                if (close < 0) { i++; continue }
                out[i] = ' '
                frames.addLast(Frame('s', 0)); i++; continue
            }
            if (c == '\'') {
                val close = simpleEnd(out, i + 1, n, '\'')
                if (close < 0) { i++; continue }
                if (blank) out[i] = ' '
                frames.addLast(Frame('c', 0)); i++; continue
            }
            if (top != null && top.kind == 't') {
                if (c == '{') { top.depth++; if (blank) out[i] = ' '; i++; continue }
                if (c == '}') {
                    top.depth--
                    if (top.depth == 0) frames.removeLast()
                    if (blank) out[i] = ' '
                    i++
                    continue
                }
            }
            if (blank) out[i] = ' '
            i++
        }
        return String(out)
    }

    /** 单行字面量的收尾下标（考虑转义、不许跨行）；找不到返回 -1 */
    private fun simpleEnd(out: CharArray, from: Int, n: Int, quote: Char): Int {
        var j = from
        while (j < n) {
            when (out[j]) {
                '\\' -> j += 2
                quote -> return j
                '\n' -> return -1
                else -> j++
            }
        }
        return -1
    }

    /** 0~4 个空格/制表符 = 类级成员；函数里的局部变量缩进更深，不进账 */
    private val indent = "[ \\t]{0,4}"
    private val mutableFlow = """[\w.]*Mutable(?:State|Shared)Flow\b"""

    /** 等号与初值之间允许换行：旧那一版要求构造括号同行，正是漏数那一族记录的成因 */
    private val assign = "[ \\t]*(?:\\r?\\n[ \\t]*)?"

    private fun branch(shape: VmMutableShape, pattern: String): Pair<VmMutableShape, Regex> =
        shape to Regex(pattern, RegexOption.MULTILINE)

    /**
     * 五支尺：全部只认行首 0~4 空格的类级成员，全部要求**先剥注释与字面量**。
     * `private val _x =` 换行再写 `MutableStateFlow(0)` 也数得到
     * （旧那一版要求构造括号同行，正是"尺瞎了"那一族记录的成因）。
     */
    private val branches: List<Pair<VmMutableShape, Regex>> = listOf(
        branch(
            VmMutableShape.FLOW,
            "^${indent}private[ \\t]+(?:val|var)[ \\t]+(\\w+)[ \\t]*(?::[^=\\n]*)?=$assign$mutableFlow"
        ),
        // 类型侧的私有可写流：`private val x: MutableStateFlow<Int>`（没有等号也进账）
        branch(VmMutableShape.FLOW, "^${indent}private[ \\t]+(?:val|var)[ \\t]+(\\w+)[ \\t]*:[ \\t]*$mutableFlow"),
        // 裸 var：本文件那几颗全靠这一支才看得见
        branch(VmMutableShape.VAR, "^${indent}private[ \\t]+var[ \\t]+(\\w+)\\b"),
        branch(VmMutableShape.LATEINIT, "^${indent}private[ \\t]+lateinit[ \\t]+var[ \\t]+(\\w+)\\b"),
        branch(
            VmMutableShape.COLL,
            "^${indent}private[ \\t]+(?:val|var)[ \\t]+(\\w+)\\b[^\\n]*?=[ \\t]*(?:mutableStateOf|mutableStateListOf|" +
                "mutableMapOf|mutableListOf|mutableSetOf|java\\.util\\.ConcurrentHashMap|ConcurrentHashMap|" +
                "Atomic(?:Reference|Integer|Long)|ArrayDeque|LinkedList)\\b"
        ),
        // 可见性不是 private 却交出可写流（等号左右任一侧都算；初值换行也算）
        branch(
            VmMutableShape.LEAKED,
            "^${indent}(?:(?:internal|public|protected|open|override)[ \\t]+)?(?:val|var)[ \\t]+(\\w+)\\b" +
                "[ \\t]*(?::[^=\\n]*)?(?:=$assign$mutableFlow|:[ \\t]*$mutableFlow)"
        )
    )

    /**
     * 文本里的全部可写状态位。`private var _x = MutableStateFlow(0)` 会被 FLOW 与 VAR 各数一遍——
     * 那是同一颗状态，所以别的形状命中过的名字不再重复记成 VAR：**每颗状态只有一个条目**。
     */
    private fun sites(text: String): List<VmStateSite> {
        val masked = maskComments(text)
        val hits = branches.flatMap { (shape, regex) ->
            regex.findAll(masked).map { m -> VmStateSite(shape, m.groupValues[1]) }.toList()
        }.toMutableList()
        val namedElsewhere = hits.filter { it.shape != VmMutableShape.VAR }.map { it.name }.toSet()
        return hits.filterNot { it.shape == VmMutableShape.VAR && it.name in namedElsewhere }
    }

    @Test
    fun `composer state does not move back into the viewmodel`() {
        val text = sourceOf("viewmodel/LoveBrainViewModel.kt")
        val present = sites(text).map { it.name }.toSet()
        val back = movedOut.toList().flatMap { (home, names) ->
            names.filter { it in present }.map { n -> "$n（本该住在 $home）" }
        }
        assertTrue(
            "这 ${back.size} 颗状态又回到 ViewModel 里了：$back —— 它们的主人已经搬出去了，" +
                "要加状态请加到那个 store 文件里（VM 只留只读出口）",
            back.isEmpty()
        )
        // 反向也要成立：VM 确实还在通过各 store 读写，而不是自己另开一条路
        for ((home, needle) in accessors) {
            assertTrue(
                "ViewModel 里已经不出现 `$needle` 了——那一家子的写入是不是绕开 $home 了？" +
                    "（真搬走了就同步改这个清单，别留一条恒真的判据）",
                text.contains(needle)
            )
        }
    }

    @Test
    fun `viewmodel mutable state is exactly the registered ledger`() {
        val measured = sites(sourceOf("viewmodel/LoveBrainViewModel.kt")).toSet()
        val grew = (measured - ledger.keys).sorted().joinToString()
        val repaid = (ledger.keys - measured).sorted().joinToString()
        assertTrue(
            "ViewModel 里长出没登记过的可写状态：[$grew] —— 新的可写状态请放进对应的 store" +
                "（输入区那族的主人是 ComposerStore，持续意图那族是 IntentController）。" +
                "确实只能放 VM，就在 ledger 里给它写清它为什么无处可去。" +
                "实到清单（登记就照它抄，别照叙述填）：[${measured.sorted().joinToString()}]",
            grew.isEmpty()
        )
        assertTrue(
            "这 ${(ledger.keys - measured).size} 颗已经不归 ViewModel 了：[$repaid] —— 搬家成功就把清单改短，" +
                "并把实到清单同步进 KDoc。留着旧名字，下一个人就不知道还剩多少没搬。",
            repaid.isEmpty()
        )
    }

    /** 这一格没有基线可躲：它今天就是 0，出现任何一处都是新账（同口径见 `PackageDependencyTest` 的 feature 那格） */
    @Test
    fun `no mutable state leaks past private in the viewmodel`() {
        val leaked = sites(sourceOf("viewmodel/LoveBrainViewModel.kt"))
            .filter { it.shape == VmMutableShape.LEAKED }
            .sorted()
        assertTrue(
            "这 ${leaked.size} 颗可写状态不是 private：$leaked —— 把可写的 `MutableStateFlow` 交给外面" +
                "（哪怕是 internal），就等于给 VM 开了第二条写入口，" +
                "『状态只有一个主人』那本账立刻作废；只许交出 `asStateFlow()` 的只读视图。",
            leaked.isEmpty()
        )
    }

    /** 正向证人（生产文件）：FLOW 那一支必须真的数得到一堆——"0 命中"不等于"通过" */
    @Test
    fun `the scanner actually sees private state flows`() {
        val seen = sites(sourceOf("viewmodel/SetupViewModel.kt")).filter { it.shape == VmMutableShape.FLOW }
        assertTrue(
            "扫描器在 SetupViewModel 里只看到 ${seen.size} 颗私有状态流——" +
                "那条正则大概是死的，上面那格通过不代表有牙",
            seen.size >= 5
        )
    }

    /**
     * 正向证人（夹具）：VAR / COLL / LATEINIT 与**名字不带 `_`** 那几支。
     *
     * 生产文件里这几支今天**全是 0**，所以拿生产文件当证人只能证到 FLOW 一支；
     * 那几支不钉夹具就是恒真（旧版正是对四颗裸 `private var` 全盲，量出来的账比登记的多三颗）。
     * 同一个夹具顺手钉两条反向的：局部变量不进账、`private var _x = MutableStateFlow(0)`
     * 这种一颗状态只登记一个条目。
     */
    @Test
    fun `every branch fires on its own fixture`() {
        val fixture = buildString {
            appendLine("package fixture")
            appendLine("class Fix {")
            // FLOW：名字不带 `_`（旧那一版只认 `_` 开头，这一颗当场躲得过）
            appendLine("    private val cache = MutableStateFlow(0)")
            // FLOW：`MutableSharedFlow` 那一支（旧版一支都没有），且换行写初值也要数得到
            appendLine("    private var shared =")
            appendLine("        MutableSharedFlow<Int>()")
            appendLine("    private var flag = false") // VAR：旧尺完全看不见这一支
            appendLine("    private val bucket = mutableListOf<String>()") // COLL
            appendLine("    private lateinit var holder: Any") // LATEINIT
            appendLine("    private var _both = MutableStateFlow(0)") // 一颗状态：只许登记一个条目
            appendLine("    internal val leaked = MutableStateFlow(0)") // LEAKED：internal 也算漏
            appendLine("    val rawLeak = MutableStateFlow(0)") // LEAKED：不写修饰符就是 public
            appendLine("    val typedLeak: MutableStateFlow<Int> = rawLeak") // LEAKED：类型侧漏出去
            appendLine("    fun f() {")
            appendLine("        val local = MutableStateFlow(0)") // 局部变量：不进账
            appendLine("    }")
            appendLine("}")
        }
        val seen = sites(fixture).toSet()
        val missing = listOf(
            VmStateSite(VmMutableShape.FLOW, "cache"),
            VmStateSite(VmMutableShape.FLOW, "shared"),
            VmStateSite(VmMutableShape.VAR, "flag"),
            VmStateSite(VmMutableShape.COLL, "bucket"),
            VmStateSite(VmMutableShape.LATEINIT, "holder"),
            VmStateSite(VmMutableShape.LEAKED, "leaked"),
            VmStateSite(VmMutableShape.LEAKED, "rawLeak"),
            VmStateSite(VmMutableShape.LEAKED, "typedLeak")
        ).filterNot { it in seen }
        assertTrue(
            "夹具里这几支没命中：$missing —— 那支尺是死的（实到：${seen.sorted()}）。" +
                "上面 ledger 那一格通过不等于有牙，这几支在生产里今天全是 0",
            missing.isEmpty()
        )
        assertTrue(
            "局部变量被当成类状态了：$seen —— 缩进 8 的那些不许进账，否则这把尺会把每个函数都记成债",
            seen.none { it.name == "local" }
        )
        assertTrue(
            "`private var _both = MutableStateFlow(0)` 被记成两颗了：$seen —— 记账单位是一颗状态一个条目",
            VmStateSite(VmMutableShape.FLOW, "_both") in seen &&
                seen.count { it.name == "_both" } == 1
        )
        assertTrue(
            "换行写初值的那颗没数到：$seen —— 旧尺正是要求构造括号在同一行才数得到",
            VmStateSite(VmMutableShape.FLOW, "shared") in seen &&
                VmStateSite(VmMutableShape.VAR, "shared") !in seen
        )
    }

    /**
     * 反向证人：注释与字符串里的形状**不许**伪造读数。
     *
     * (a) 同一批形状写进 `//`、写进行首不带 `*` 的块注释、写进 KDoc ⇒ 只许数到那一颗真声明；
     * (b) 字符串里整条抄了一句旧声明 ⇒ 不许进账；而串里含 `//` 时，
     *     **它之后那一行真声明仍要命中**（不认字符串的剥法会把它当注释吃掉）；
     * (c) 三引号原始串里嵌引号 + `${}` 模板里再嵌字符串 ⇒ 剥完错位就不许把串里的东西当成声明、
     *     也不许把串后面那颗真声明弄丢。
     */
    @Test
    fun `comments and string literals do not fake a finding`() {
        val fixture = buildString {
            appendLine("package fixture")
            appendLine("class Fix {")
            appendLine("    // private val _fakeLine = MutableStateFlow(0)")
            appendLine("    /* private var fakeBlockVar = 0 */")
            appendLine("/*")
            appendLine("private val _fakeAcrossLines = MutableSharedFlow<Int>()")
            appendLine("private lateinit var fakeLate: Any")
            appendLine("*/")
            appendLine("    /**")
            appendLine("     * 原先这里有一颗 `private val _fakeDoc = MutableStateFlow(0)`，")
            appendLine("     * 说明书里出现形状不算数。")
            appendLine("     */")
            appendLine("    private val note = \"路径 a//b 之后才是真声明\"")
            appendLine("    private val _afterSlashInsideString = MutableStateFlow(0)")
            appendLine("    private val raw = \"\"\"")
            appendLine("        private var fakeInRaw = 0")
            appendLine("        嵌套的 \${\"引号\"}与\"xx\" 都不算")
            appendLine("    \"\"\"")
            appendLine("    private val _real = MutableStateFlow(0)")
            appendLine("}")
        }
        val seen = sites(fixture)
        val names = seen.map { it.name }.toSet()
        assertTrue(
            "注释或字符串里的形状被当成状态数到了：${seen.filterNot { it.name in setOf("_real", "_afterSlashInsideString") }}",
            seen.all { it.name == "_real" || it.name == "_afterSlashInsideString" }
        )
        assertTrue("串里含 // 之后那颗真声明被剥法吃掉了：$names", "_afterSlashInsideString" in names)
        assertTrue("真声明丢了：$names", seen.any { it.shape == VmMutableShape.FLOW && it.name == "_real" })
    }

    /** 账本本身也不许被清空来"通过"（与 `PackageDependencyTest` 那条 `the rules are not silently emptied` 同口径） */
    @Test
    fun `the ledger is not silently emptied`() {
        val measured = sites(sourceOf("viewmodel/LoveBrainViewModel.kt")).toSet()
        assertTrue(
            "账本登记了 ${ledger.size} 颗，VM 实到 ${measured.size} 颗：[${measured.sorted().joinToString()}] —— " +
                "把 ledger 删空不算还债，登记数与实到数必须逐颗相等",
            ledger.isNotEmpty() && ledger.keys == measured
        )
        assertTrue(
            "账本里每一颗都要写去处，不许留空：${ledger.filterValues { it.isBlank() }.keys}",
            ledger.values.none { it.isBlank() }
        )
    }
}
