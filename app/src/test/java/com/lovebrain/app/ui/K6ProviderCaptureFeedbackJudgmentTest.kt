package com.lovebrain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * K6 判决表：**Provider / 捕获 / 反馈 同一套 row/card/state 语法 → 逐格判过，并且每一格都有下落**。
 *
 * 这条 ⚠ 的原文是："Provider/捕获/反馈同一套 row/card/state 语法 → 没做"。它要的是三个目的地
 * （供应商 [ProviderSection 那一族]、捕获范围、反馈案例）不再各自发明卡片、行、状态点与空/错误态，
 * 而是共用设计系统那一家（`LbActionCard` / `LbSettingRow` / `LbRowState` / `LbAsyncState`）。
 *
 * ## 这一格判的**不是**"某一棵文件里必须出现某个组件名"
 *
 * 上一版把判据写成"`home/HomeScreen.kt` 里必须有 2 处 `LbActionCard(`"。那不是判决，那是一句
 * **关于文件路径的字面量**：首页的快捷格一旦由页面自绘的那一颗服务（或在 core 里换了主人），
 * 屏幕的语义一个字没变，这一格却会红——反过来也成立：只要那个名字还留在指名的文件里，
 * 里面画的是什么它都看不见。仓库里已经明写着不许靠改测试指向的文件来糊绿，所以这一版把判据
 * 换成**归属**：每一格要么有设计系统的主人，要么在异形账本里登记过，要么明写"还没有主人"。
 *
 * 三种下落（[ServedBy]）：
 * - [ServedBy.CORE] —— 这一格由 `core/designsystem` 里那颗组件画。判据：那颗组件在全树**恰好一处声明**，
 *   且声明落在 `core/designsystem/`；并且指名的屏幕文件里确实由它服务（次数对得上）。
 * - [ServedBy.LEDGERED] —— 这一格由页面自己命名的那一颗画，而它**在异形账本里登记着**。判据：恰好一处声明、
 *   声明不在 `core/designsystem/`、`<声明文件名>#<名字>` 出现在 `architecture/OddShapeOwnershipTest`
 *   那本账里（这把尺**读**那本账，不另抄一份数，所以归并成功把账改小时这里当场红）。
 *   登记还得分清是哪一种：账上标"欠归并"的，core 那档**必须已经在盘上且尺寸逐数对得上**（[CoreTier]，
 *   由本文件实测，不许写成散文）；账上标"缺件"的，core 那档**必须还没有**（[Judgment.coreGap] 非空、
 *   [Judgment.coreTier] 为空），否则"缺件"就是一句挡箭牌。
 * - [ServedBy.NO_OWNER_YET] —— 这一格今天**没有主人**：屏上那一层形状由屏幕文件自己画。
 *   这一档不豁免，它把债钉成一个**确切数量**：屏幕文件里那个自画记号的出现次数必须逐数对上
 *   （多一处 = 又长了一层没主人的形状），并且它指名的那颗 core 主人**必须还不存在**
 *   （哪天补进设计系统，这一格当场红，回来把账翻成 [ServedBy.CORE]）。
 *
 * ⚠ 一把只会因为"搬家"而红的尺不是判决；一把**搬家不红、失去主人立刻红**的尺才是。
 * 三种下落之外都不许有第四种形状：没登记也没主人的自画，[ServedBy.LEDGERED] 与
 * [ServedBy.NO_OWNER_YET] 两档都会把它判红。
 *
 * 与 `architecture/OddShapeOwnershipTest` 的分工：那把尺按**声明形状**全树扫（凡是带家族词的顶层
 * `@Composable` 都要有下落），它管"全仓不许有没主人的形状"；这一格按**合同里的屏上那一格**下判
 * （这一格是屏上的哪一处、由谁画、判决是什么），它管"判决不是空话"。两把尺一横一竖，
 * 拿掉任何一把，另一把都留着一整片看不见的地方。
 */
class K6ProviderCaptureFeedbackJudgmentTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    /** 异形账本所在的那颗测试文件——这本账只有一份，这一格读它，不抄它 */
    private val oddShapeLedger: File
        get() = File("src/test/java/com/lovebrain/app/architecture/OddShapeOwnershipTest.kt")
            .takeIf { it.isFile }
            ?: File("app/src/test/java/com/lovebrain/app/architecture/OddShapeOwnershipTest.kt")

    private fun mainFile(path: String): File =
        File(appRoot, path).also {
            assertTrue("找不到 $it——这一格会恒绿（判决表指向了不存在的文件）", it.isFile)
        }

    /** 去注释与字符串字面量后的代码：注释里写 `LbActionCard(` 不算一处调用 */
    private fun codeOf(src: String): String = buildString {
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).takeIf { it >= 0 } ?: src.length
                    i = minOf(end + 2, src.length)
                }
                src.startsWith("//", i) -> {
                    val end = src.indexOf('\n', i).takeIf { it >= 0 } ?: src.length
                    i = end
                }
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3).takeIf { it >= 0 } ?: src.length
                    append("""" """"); i = minOf(end + 3, src.length)
                }
                src[i] == '"' -> {
                    val end = src.indexOf('"', i + 1).takeIf { it >= 0 } ?: src.length
                    append("\"\""); i = minOf(end + 1, src.length)
                }
                else -> { append(src[i]); i++ }
            }
        }
    }

    /**
     * 锚点计数按**词边界**判，不按裸子串判。裸子串那一版把 `AssistantStatusCard(` 与
     * `HomeFeatureCard(` 都算成"自画卡底"那一格的 `Card(` 一处——同缩进的两个数会互相顶掉，
     * 尺子把三处读成八处，然后有人来把 3 改成 8（那就等于把判据改松）。
     */
    private fun countAnchor(code: String, anchor: String): Int =
        Regex("(?<![A-Za-z0-9_.])" + Regex.escape(anchor)).findAll(code).count()

    private fun codeOfMain(path: String): String = codeOf(mainFile(path).readText())

    /** 全树的 `.kt`（剥过注释），用来按名字找声明落在哪颗文件 */
    private val treeCode: List<Pair<String, String>> by lazy {
        appRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.name to codeOf(it.readText()) }
            .toList()
    }

    /**
     * 一颗名字的**声明**处（`fun` / `class` / `object` / `interface`，允许 `fun <T>` 与可见性/修饰前缀）。
     * 返回的是文件名，所以"恰好一处"与"落在哪一层"两件事都能判。
     */
    private fun declarationsOf(name: String): List<String> {
        val decl = Regex(
            "^(?:@Composable[ \t]*)?(?:private |internal |public )?" +
                "(?:enum |data |sealed |annotation )*(?:fun|class|object|interface)" +
                "(?:[ \t]*<[^>]*>)?[ \t]+" + Regex.escape(name) + "(?![A-Za-z0-9_])"
        )
        return treeCode.filter { (_, code) ->
            code.lineSequence().any { decl.containsMatchIn(it.trim()) }
        }.map { it.first }.sorted()
    }

    /** 异形账本登记的两本清单合起来的样子：`<文件名>#<声明名>` 的集合 */
    private val ledgerEntries: Set<String> by lazy {
        assertTrue(
            "找不到异形账本：$oddShapeLedger —— 这一格的「要么登记过」那一条会恒绿",
            oddShapeLedger.isFile
        )
        Regex("\"([A-Za-z0-9_.]+\\.kt)#([A-Za-z0-9_]+)\"")
            .findAll(oddShapeLedger.readText())
            .map { "${it.groupValues[1]}#${it.groupValues[2]}" }
            .toSet()
    }

    /** core 里已经存在、且与这一格逐数对得上的一档（账上标「欠归并」的那几颗才准填） */
    private data class CoreTier(
        val coreFile: String,        // core/designsystem/ 下的相对路径
        val tierAnchor: String,      // 那一档在 core 源码里的名字
        val coreSizeAnchor: Regex,   // 从 core 里量出那一档的数
        val pageFile: String,        // 页面自己写那个数的文件
        val pageSizeAnchor: Regex    // 从页面里量出同一个数
    )

    private enum class ServedBy { CORE, LEDGERED, NO_OWNER_YET }

    private data class Judgment(
        val id: Int,
        val destination: String,     // 首页 / Provider / 捕获 / 反馈
        val screen: String,          // 这一格画在哪颗文件（main 下相对路径）
        val family: String,          // row / card / state
        val what: String,            // 屏上的哪一格
        val owner: String,           // 它由谁画（组件名；没有主人时写它欠的那颗主人）
        val serveAnchor: String,     // 在那颗屏幕文件里认出"仍由它服务"的字面锚点
        val expectCount: Int,        // 这一处锚点应当出现的次数（0 不许，"存在即可"也不算）
        val servedBy: ServedBy,
        val coreTier: CoreTier?,     // 非空 = 账上标着欠归并，core 那档必须已经在盘上且逐数相等
        val coreGap: String,         // 缺的是哪一颗旋钮（CORE 档留空）
        val verdict: String          // 判决本身：为什么不搬 / 欠在哪
    )

    /**
     * 判决表。每一行 = 屏上的一格 + 它现在的下落 + 为什么不搬。
     *
     * 首页那一族按实施书的首页合同逐格列（Hero、快捷 2×2、捕获行、供应商行、行尾箭头那一族、
     * 行组卡底那一层），Provider/捕获/反馈三个目的地各自的状态点与列表态也各自成行。
     */
    private val judgments: List<Judgment> = listOf(
        Judgment(
            1, "首页", "ui/home/HomeScreen.kt", "card",
            "Hero：悬浮军师状态主卡（这一屏唯一的主动作所在）",
            "AssistantStatusCard", "AssistantStatusCard(", 1, ServedBy.LEDGERED, null,
            "core 没有「一张主卡里两处独立出口 + 自画渐变卡底」那一档",
            "★ 判过、按设计不搬：这一格里有两处独立出口（主动作 + 右上角那颗临时隐藏），" +
                "`LbActionCard` 把整张卡并成一处操作，第二处要么被吞进卡的名字、要么在卡里再叠一层 " +
                "clickable（那就是一颗变两颗可点）；卡底那一档（圆角 24 + PrimaryLight + PrimarySubtle " +
                "描边）全仓只此一处。已在异形账本登记。"
        ),
        Judgment(
            2, "首页", "ui/home/HomeScreen.kt", "card",
            "快捷功能 2×2 四格（知识库 / 今日锦囊 / 谈心模式 / 感情五维）",
            "LbActionCard", "LbActionCard(", 4, ServedBy.CORE, null, "",
            "★ 已归并、下落从 LEDGERED 翻成 CORE：这一格原来由页面自画的 `HomeFeatureCard` 画、" +
                "账上标着欠归并——core 的 `LbActionCard` 早有 `GridCell` 那一档，方块/字形/箭头三数" +
                "与页面那三个常数逐数相等，并过去是零像素差。 已把四处调用换成 " +
                "`LbActionCard(iconBlock = GridCell)`、删掉页面那一颗、回异形账本把 HomeFeatureCard 销行" +
                "（`HomeComponents.kt#HomeFeatureCard` 已从这本账移除）。屏上这一格现由设计系统的主人画，" +
                "页面不再自画——所以判决 2 作为「异形/欠归并」那一行已销，下落改记 CORE。"
        ),
        // 判决 3（HomeScreen.kt「消息捕获那一行」）**整块销行**——账本 stale，不是生产漏接：
        //  首页瘦身，HomeScreen 只剩状态卡 + 黄字提示 + 2×2 四入口；「服务设置段（捕获开关与
        // 无障碍授权）」按合同点名撤下，那一行（状态说明 + 尾部出口 + 开关）连同安全线一起搬进
        // `ui/home/CaptureAppsScreen.kt` 子页（异形账本 `HomeComponents.kt#HomeCaptureRow` 已同步销行，
        // 见 OddShapeOwnershipTest 第 291-292 行的注释）。所以这一格既不是「翻 CORE」（HomeScreen 里
        // 没有任何 core 主人在服务这一行），也不是「欠件保留」（这一行在这里已经不存在）——判决的
        // 屏幕与那一格都离场，只能销行。id 保留不复用，与判决 2「改记 CORE 而不重编号」同法。
        // 判决 4/5/6/7（原锚 ui/home/HomeScreen.kt 的「Provider 行 / 状态点 / HomeArrowRow 那一族 / 卡底那一层」）
        // **整批销行**——与判决 3 同一根因： 首页瘦身后 HomeScreen.kt 只剩状态卡 + 2×2 四入口，
        // 服务设置区（供应商行 + 状态点 + 行尾箭头 + 三张卡底）按合同点名撤下，那一族连同安全线一起
        // 搬进 `CaptureAppsScreen.kt`（Provider/Capture 都在那颗子页），异形账本 `HomeComponents.kt#HomeArrowRow/HomeCaptureRow`
        // 已销行。留在 HomeScreen 的判决里就没有这一族的锚点可指——判**账本 stale 而非生产漏接**（同判决 3）。
        // 判决 8-13 分别锚在 ProviderSection.kt / CaptureAppsScreen.kt / FeedbackCasesScreen.kt 上，那一族
        // 的现在主人所在屏还在账，不动。id 4/5/6/7 保留历史不复用（同判决 3 追写风格）。
        Judgment(
            8, "Provider", "ui/home/ProviderSection.kt", "state",
            "供应商展开头那颗状态点（就绪↔颜色）",
            "LbRowState", "LbRowState.Ready", 1, ServedBy.CORE, null, "",
            "★ 按设计只搬一半：就绪↔颜色那一对已收进 `LbRowState.color`，点的**形状**留在调用方。" +
                "判过、不搬，不是漏搬。"
        ),
        Judgment(
            9, "Provider", "ui/home/ProviderSection.kt", "state",
            "供应商票据列表的空/有内容两态",
            "LbAsyncState", "LbAsyncState(", 1, ServedBy.CORE, null, "",
            "已用同一颗异步状态组件：空/有内容由同一个判定决定、版式一处，不再 if/else 两边各画一次"
        ),
        Judgment(
            10, "捕获", "ui/home/CaptureAppsScreen.kt", "state",
            "捕获 App 列表四态",
            "LbAsyncState", "LbAsyncState(", 1, ServedBy.CORE, null, "",
            "已用同一颗异步状态组件（与供应商票据列表、反馈案例列表同一颗），不再自己发明空态卡片"
        ),
        Judgment(
            11, "捕获", "ui/home/CaptureAppsScreen.kt", "row",
            "捕获 App 那一行（勾选框在 leading 槽）",
            "CaptureAppRow", "CaptureAppRow(", 2, ServedBy.LEDGERED, null,
            "设计系统不该认识「勾选框」这个具体控件：那一颗留在调用方的 leading 槽",
            "★ 按设计只搬一半：行本体已是设计系统那颗行的委托壳，勾选框故意留页面里。已登记（委托壳账）。"
        ),
        Judgment(
            12, "反馈", "ui/feedback/FeedbackCasesScreen.kt", "state",
            "反馈案例列表的空/有内容两态",
            "LbAsyncState", "LbAsyncState(", 1, ServedBy.CORE, null, "",
            "已用同一颗异步状态组件——三个目的地的列表四态现在共用一颗，这正是「同一套语法」落地的证人"
        ),
        Judgment(
            13, "反馈", "ui/feedback/FeedbackCasesScreen.kt", "card",
            "案例那张白卡（首句当标题 + 余文摘要 + 时间元信息，点整卡展开）",
            "LbListCard", "LbListCard(", 1, ServedBy.CORE, null, "",
            "★ 已归并、下落从 NO_OWNER_YET 翻成 CORE——**这一格当初就写着" +
                "「等 LbCaseCard（或等价的摘要卡那一档）真进了设计系统，这一格会先红一次，" +
                "那时把账翻成 CORE 而不是把数量改大」，2026-10-05 M3b 就是那一次**：" +
                "主人是 `core/designsystem/LbListCard.kt`（设计基线 v1.1 §3.6 那一张列表卡四槽：" +
                "标题 `titleMedium`15 SemiBold 单行 ellipsis / 状态 6dp 点 + `bodySmall`12 字同屏 / " +
                "摘要 `bodyMedium`13 最多两行 / 元信息 `labelSmall`10 用「｜」合成一行 / 动作行 ≤3 颗 RowCapsule），" +
                "卡底按 §3.4 走 `Border` 1dp + 无阴影。名字用基线给的 `LbListCard` 而不是当初预名的 `LbCaseCard`：" +
                "同一档摘要卡知识库与消息捕获将来都要用，不该按页面命名（预名的 `LbCaseCard` 因此不在盘上，" +
                "本文件第③颗反向证人指的 `LbSummaryRow` 也仍不在盘上）。" +
                "页面那颗 `CaseCard` 只剩「把内容交给公共件」，异形账本已同步把它移进委托壳清单。"
        )
    )

    /** 判决表本身：目的地与语法族都得在场，颗数逐数钉住（少一格是漏判，多一格是有人加了颗没判过的） */
    @Test
    fun `the judgment table covers every destination and every syntax family`() {
        // 判决 3/4/5/6/7 已随  首页瘦身整批销行（HomeScreen.kt 里那一族格已经不存在，都在 CaptureAppsScreen）
        // ⇒ 8 格，id 3-7 保留不复用。
        assertEquals("判决表实到 ${judgments.size} 格", 8, judgments.size)
        assertEquals(
            "判决 id 不许重号也不许缺号——判决 3/4/5/6/7 已销行、id 保留历史不复用（判决 2 的 LEDGERED→CORE 也是原 id 追写、不重编号）",
            (1..13).toList() - listOf(3, 4, 5, 6, 7), judgments.map { it.id }.sorted()
        )
        assertEquals(
            "四个目的地都得在判决里，否则「Provider/捕获/反馈」这条 ⚠ 的标题就名不副实",
            setOf("首页", "Provider", "捕获", "反馈"), judgments.map { it.destination }.toSet()
        )
        assertEquals("row/card/state 三族都得在判决里", setOf("row", "card", "state"),
            judgments.map { it.family }.toSet())
        // 2026-10-06：判决 13 按**当初写在判决里的约定**翻 CORE（旧文："等 LbCaseCard 真进了设计系统，
        // 这一格会先红一次，那时把账翻成 CORE 而不是把数量改大"；2026-10-05 M3b 就是预定的那次翻案）。
        // 于是表里今天零颗 NO_OWNER_YET——这是判决表该续行，不是删牙：
        //  ① 这一档的**判据**必须还上膛——由 the no-owner-yet branch stays armed... 那格拿合成件
        //     逐颗打给主判据的 when 走，摘牙当场红；
        //  ② 表里一出现 NO_OWNER_YET 的实例，主判据当场逐条验它（这条不许有人来放松）；
        //  ③ "找无主件"另有architecture/OddShapeOwnershipTest 全树扫兜底，这一族的两把尺一横一竖。
        // 想改下面这个集合只有两条正路：真判出一颗无主下落（回来把集合加一颗），或全仓再无自画形状
        // 且不登记的那一类（那要在交接件里给撤档理由——本格**没有**撤这一档）。
        val servedTypes = judgments.map { it.servedBy }.toSet()
        assertEquals(
            "表里实判的下落种类必须逐数对上现实这两档（CORE/LEDGERED 各至少一格真判过）：" +
                "少一格是漏判，多一格是有人加了颗没判过的下落",
            setOf(ServedBy.CORE, ServedBy.LEDGERED), servedTypes
        )
        assertEquals(
            "三种下落的枚举一颗都不许被删——从枚举里划掉 NO_OWNER_YET 等于把「找无主件」这只手砍掉",
            3, ServedBy.values().size
        )
        // 恒绿形状的第一道门：判据不许是"数到零就算对"
        judgments.forEach { j ->
            assertTrue("判决 ${j.id} 的 expectCount=${j.expectCount}——0 意味着这一格判成「存在即可」", j.expectCount > 0)
            assertTrue("判决 ${j.id} 的屏幕文件必须带路径（只写个文件名就等于靠文件名糊绿）", "/" in j.screen)
        }
        val kt = appRoot.walkTopDown().count { it.isFile && it.extension == "kt" }
        assertTrue("appRoot 只扫到 $kt 颗 .kt——路径接错了，下面每一格都会扫空集恒绿", kt >= 60)
    }

    /**
     * 主判据：逐格验"有 core 主人 / 在账本登记 / 明写还没有主人并且钉死数量"。
     * 搬家不许让它红；失去主人、账上销行、卡底长出第四层，都得让它红。
     */
    @Test
    fun `every judged cell has a core owner or a place in the odd-shape ledger`() {
        judgments.forEach { j ->
            val code = codeOfMain(j.screen)
            val got = countAnchor(code, j.serveAnchor)
            assertEquals(
                "判决 ${j.id}（${j.destination}/${j.family}）：${j.screen} 里「${j.serveAnchor}」应有 " +
                    "${j.expectCount} 处，实到 $got 处。\n  这一格：${j.what}\n  下落：${j.servedBy} " +
                    "（主人：${j.owner}）\n  判决：${j.verdict}",
                j.expectCount, got
            )

            verifyServedBy(j)

            j.coreTier?.let { tier -> requireTierNumbersMatch(j, tier) }
        }
    }

    /**
     * 三种下落各自的**归属判据**——从主判据里原样搬出来一颗，一条不松：
     * 主判据逐格调它；NO_OWNER_YET 那档今天表里零颗实例，由 armed 哨兵格拿合成件打它，
     * 谁把这一档的牙摘了或放松了，哨兵当场红。
     */
    private fun verifyServedBy(j: Judgment) {
        when (j.servedBy) {
            ServedBy.CORE -> {
                val decls = declarationsOf(j.owner)
                assertEquals(
                    "判决 ${j.id} 说这一格有 core 主人 ${j.owner}，但它在全树的声明处不是恰好一处：$decls",
                    1, decls.size
                )
                assertTrue(
                    "判决 ${j.id}：${j.owner} 的声明不在 core/designsystem 里（实到 ${decls.single()}）——" +
                        "那它就不是设计系统的主人，这一格得改判成登记或欠件",
                    coreFileNames().contains(decls.single())
                )
                assertEquals(
                    "判决 ${j.id}：${j.owner} 已经收口了，就不该再有「缺哪一颗旋钮」这句话：${j.coreGap}",
                    "", j.coreGap
                )
                assertTrue(
                    "判决 ${j.id}：已经有 core 主人的格子不许标「core 里那一档已经在盘上」" +
                        "（那句话是给欠归并那几格用的）",
                    j.coreTier == null
                )
            }
            ServedBy.LEDGERED -> {
                val decls = declarationsOf(j.owner)
                assertEquals(
                    "判决 ${j.id} 说这一格由 ${j.owner} 画，但它在全树的声明处不是恰好一处：$decls",
                    1, decls.size
                )
                val entry = "${decls.single()}#${j.owner}"
                assertTrue(
                    "判决 ${j.id}：${j.owner} 是页面自画的一颗，却没在异形账本里登记（找的是 $entry）。" +
                        "要么把它登记进去并写明缺哪颗旋钮，要么让它真的有主人——不许两边都不在。",
                    ledgerEntries.contains(entry)
                )
                assertTrue(
                    "判决 ${j.id}：${j.owner} 的声明落在 core/designsystem（${decls.single()}），" +
                    "却按异形登记——那本账管的是页面里的形状",
                    !coreFileNames().contains(decls.single())
                )
                // 登记要分得清是哪一种，两种都不许空着手：
                //  欠归并 ⇒ core 那一档必须已经在盘上（下面当场量），此时不该再写"缺哪颗旋钮"；
                //  缺件   ⇒ 必须写明缺的那一颗，此时 core 那一档必须还没有。
                assertTrue(
                    "判决 ${j.id}：${j.owner} 要么写明缺哪颗旋钮（缺件），要么给出 core 里已经存在的那一档" +
                        "并让这一格被当场量过（欠归并）——两个都不填就是把「页面自画」当默认选项；" +
                        "两个都填就是自相矛盾。coreGap=«${j.coreGap}» coreTier=${j.coreTier?.tierAnchor}",
                    (j.coreGap.isNotBlank()) xor (j.coreTier != null)
                )
            }
            ServedBy.NO_OWNER_YET -> {
                val decls = declarationsOf(j.owner)
                assertTrue(
                    "判决 ${j.id} 把这一格记成「还没有主人」，可 ${j.owner} 已经在盘上了：$decls。" +
                        "主人补齐了就要回来把这一格翻成 CORE，不许让它继续挂着欠件",
                    decls.isEmpty()
                )
                assertTrue(
                    "判决 ${j.id}：这一格还没有主人，就必须写明缺哪颗旋钮：«${j.coreGap}»",
                    j.coreGap.isNotBlank()
                )
                assertTrue(
                    "判决 ${j.id}：还没有主人的格子没有「core 里那一档已经在盘上」这回事",
                    j.coreTier == null
                )
            }
        }
    }

    /** core 那一档在不在盘上、与页面自己那个数等不等——量出来，不写进散文 */
    private fun requireTierNumbersMatch(j: Judgment, tier: CoreTier) {
        val core = codeOfMain(tier.coreFile)
        val page = codeOfMain(tier.pageFile)
        val anchor = Regex(Regex.escape(tier.tierAnchor))
        assertTrue(
            "判决 ${j.id} 说 core 已有 ${tier.tierAnchor} 那一档，${tier.coreFile} 里却找不到：账上" +
                "「欠归并」这句已经不成立（要么那一档被删了，要么这一格本来就不该标欠归并）",
            anchor.containsMatchIn(core)
        )
        val coreN = tier.coreSizeAnchor.find(core)?.groupValues?.get(2)
        val pageN = tier.pageSizeAnchor.find(page)?.groupValues?.get(2)
        assertTrue(
            "判决 ${j.id}：量不出两边的数（core=$coreN / 页面=$pageN）——" +
                "档位表或页面常量的写法变了，这把尺读不动，别把它当绿",
            coreN != null && pageN != null
        )
        assertEquals(
            "判决 ${j.id}：core 的 ${tier.tierAnchor} 那一档与页面自己写的那个数不相等了" +
                "（core=${coreN}dp / 页面=${pageN}dp）——「并入零像素差」这句判决当场作废",
            pageN, coreN
        )
    }

    /** `core/designsystem` 里那些文件名——用来分「设计系统的主人」与「页面自画」 */
    private fun coreFileNames(): Set<String> =
        File(appRoot, "core/designsystem").listFiles { f -> f.isFile && f.extension == "kt" }
            ?.map { it.name }?.toSet().orEmpty().also {
                assertTrue("core/designsystem 里一颗 .kt 都没有——路径接错了", it.isNotEmpty())
            }

    /**
     * 这把尺不许是瞎的：正向三件 + 反向一件，任何一头分错，上面那格的绿就不算数。
     */
    @Test
    fun `the ownership ruler distinguishes owner, registration and absence`() {
        // ① 已知有 core 主人的一颗：必须恰好一处声明、且声明在 core/designsystem
        val setting = declarationsOf("LbSettingRow")
        assertEquals("LbSettingRow 的声明处应当恰好一处：$setting", 1, setting.size)
        assertTrue("LbSettingRow 不在 core/designsystem：$setting", coreFileNames().contains(setting.single()))
        // ② 已知登记为异形的一颗：必须在账上。
        //    这颗原来用 `HomeComponents.kt#HomeFeatureCard`——判决 2 已把它并进 core 的 `LbActionCard`
        //    并回异形账本销行，那一页不再自画那颗，所以它已不在册；换成同页仍在册的
        //    `HomeComponents.kt#AssistantStatusCard`（判决 1 的 LEDGERED 主人）当这颗证人。
        assertTrue(
            "异形账本读不出 HomeComponents.kt#AssistantStatusCard——这本账的格式变了，" +
                "「要么在账本登记」那一条已经看不见东西了",
            ledgerEntries.contains("HomeComponents.kt#AssistantStatusCard")
        )
        // ③ 已知还没有主人的一颗：必须真的不在盘上（否则那条欠件判据是恒真的）
        assertTrue("LbSummaryRow 已经在盘上了，判决 7 的「还没有主人」就是假话", declarationsOf("LbSummaryRow").isEmpty())
        // ④ 反向证人：一个不存在的名字必须什么都读不出来。这一条挡住"resolver 恒真"的假绿
        assertEquals("读声明那把尺对一个不存在的名字必须交回空集", emptyList<String>(), declarationsOf("LbOwnerNobodyDrawsThis"))
        assertTrue("账本解析出来只有 ${ledgerEntries.size} 条——少于 30 条就是那本账换了写法", ledgerEntries.size >= 30)
    }

    /**
     * NO_OWNER_YET 那一档不许因为表里今天零颗就被悄悄摘牙——三枚合成件直接打给主判据的 when：
     *  坏件：欠的主人已经在盘上却还把格子记成「还没有主人」⇒ 分支必须抛 AssertionError（牙还在）；
     *  好件：欠的主人真的还不存在、缺件写明 ⇒ 必须安静通过（这一档不许是恒真的门）；
     *  懒件：好件把缺件那句擦掉 ⇒ 也必须红（否则「缺件」就成了挡箭牌）。
     * 回退成什么会红：有人删掉/放松 NO_OWNER_YET 分支（比如摘掉 decls.isEmpty() 那一句）
     *   ⇒ 坏件不再抛，这一格当场红；有人把这一档从枚举里划掉 ⇒ coverage 那格的哨兵红。
     * 这一格是判决 13 按预定翻 CORE 之后、替"以后还会有人找无主件"守着的哨兵——不是撤档。
     */
    @Test
    fun `the no-owner-yet branch stays armed even while the table has zero such rows`() {
        assertEquals(
            "哨兵本人也判现实：表里今天就该零颗 NO_OWNER_YET——真判出一颗无主下落时，" +
                "主判据会逐条验它，这一句跟着改（改它要先在交接件里写理由）",
            0, judgments.count { it.servedBy == ServedBy.NO_OWNER_YET }
        )
        val ghost = Judgment(
            90, "证人", "ui/feedback/FeedbackCasesScreen.kt", "card",
            "合成件：欠的主人已经在盘上，却还把格子记成「还没有主人」",
            "LbAsyncState", "LbAsyncState(", 1, ServedBy.NO_OWNER_YET, null,
            "缺摘要档（证人文案）", "反向证人——这一颗必须被打红"
        )
        var threw = false
        try {
            verifyServedBy(ghost)
        } catch (_: AssertionError) {
            threw = true
        }
        assertTrue(
            "NO_OWNER_YET 的「欠的主人已在盘上必须红」这一牙掉了——分支被删或被放松，" +
                "无主件从此没人找；判决 13 翻 CORE 不等于撤这一档",
            threw
        )
        val honest = Judgment(
            91, "证人", "ui/feedback/FeedbackCasesScreen.kt", "card",
            "合成件：欠的那颗主人真的还没进设计系统",
            "LbSummaryRow", "LbListCard(", 1, ServedBy.NO_OWNER_YET, null,
            "缺「摘要行」那一档：整行可点、摘要两行、状态点与字同行", "正向证人——这一颗必须安静通过"
        )
        verifyServedBy(honest)
        var threwAgain = false
        try {
            verifyServedBy(honest.copy(coreGap = ""))
        } catch (_: AssertionError) {
            threwAgain = true
        }
        assertTrue("NO_OWNER_YET 不填缺件也算过——这一档就成了挡箭牌", threwAgain)
    }

    /**
     * 「按设计只搬一半」那两处的反向证人：捕获行的勾选那一格仍留在调用方的 leading 槽。
     *
     * 三条正判据：`leading = {` 还在（勾选那一格由页面画，不是设计系统认识勾选框）、
     * `Role.Checkbox` 还在（整行仍把"这是个可勾选项"交给读屏）、
     * `Icons.Filled.Check` 还在（勾上了就画对勾，这一格是 leading 槽唯一的视觉来源）。
     * 一条反判据：**不许再画 Material 那颗 `Checkbox(`**——它自带 48dp 轨道，正是这一屏
     * 被用户点名"什么都变大"的那一族形状（第9节第4条 要的是紧凑白卡行）。
     * 反例：有人把勾选框塞进 `LbSettingRow`（leading 槽消失）⇒ 红；
     * 有人把这一格换回 Material `Checkbox` ⇒ 红。
     */
    @Test
    fun `the half-moved capture row keeps its checkbox in the caller leading slot`() {
        val code = codeOfMain("ui/home/CaptureAppsScreen.kt")
        listOf("leading = {", "Role.Checkbox", "Icons.Filled.Check").forEach { anchor ->
            assertTrue(
                "捕获行的勾选那一格应留在调用方 leading 槽并把角色交给读屏——找不到「$anchor」" +
                    "说明设计系统开始认识这个具体控件，或者这一行丢了角色",
                Regex(Regex.escape(anchor)).containsMatchIn(code)
            )
        }
        assertTrue(
            "勾选那一格又换回 Material 那颗 `Checkbox(` 了：它自带 48dp 轨道，" +
                "与这一屏的紧凑白卡行不是同一档（）",
            !Regex("""(?<![\w.])Checkbox\s*\(""").containsMatchIn(code)
        )
    }
}
