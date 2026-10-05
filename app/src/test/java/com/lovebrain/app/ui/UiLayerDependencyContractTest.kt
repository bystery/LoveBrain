package com.lovebrain.app.ui

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 职责分层的源码级合同（防"注释式修复"回归）。
 *
 *  第6节 原话是：Activity 只是新增了"这里违反 SRP/DIP、以后应改 ViewModel"的注释，
 * 随后仍直接 inject Repository/SecurePrefs——**记录技术债不等于修复技术债**。
 * 因此这条门禁要锁的不是"有没有注释"，而是可静态核对的依赖方向：
 * ui 层不得直接持有 data 层对象，必须经 viewmodel 层转发。
 *
 * 用源码扫描而不是行为断言的原因：依赖方向是编译期事实，静态检查能在 CI 里跑，
 * 而把它写成 Compose 测试需要设备，本机没有 system image。
 */
class UiLayerDependencyContractTest {

    private val appRoot = File("src/main/java/com/lovebrain/app")
        .takeIf { it.isDirectory }
        ?: File("app/src/main/java/com/lovebrain/app")

    private fun dir(vararg parts: String): File =
        File(appRoot, parts.joinToString(File.separator)).also {
            assertTrue("missing source dir: $it", it.isDirectory)
        }

    private fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().also {
            assertTrue("no kotlin sources under $root", it.isNotEmpty())
        }

    /** 去掉注释，只留代码：注释里出现 "Repository" 三个字不算违规 */
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
                else -> {
                    append(src[i]); i++
                }
            }
        }
    }

    /**
     * 注释与字符串**一次扫过**都抹成空格：长度一字不变 ⇒ 报出来的行号还能对着文件看。
     *
     * ⚠ 为什么不用 [codeOf] 那一条：它会删掉注释 ⇒ 行号漂移，而且不分字符串
     * （URL 里的 `//` 会被当注释，把后面整片代码一起吃掉）。分步剥（先剥注释再剥字符串）
     * 也有同一个毛病：第一步读不到的边界会让第二步跨行吞掉一大段。这里只做一趟字符扫描。
     * 这一份仪器只服务热区那三格；同文件其余格子仍按 [codeOf] 的口径读，两套口径别混。
     */
    private fun blanked(src: String): String {
        val text = src.replace("\r\n", "\n").replace("\r", "\n")
        val out = StringBuilder(text)
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("//", i) -> {
                    while (i < text.length && text[i] != '\n') { out[i] = ' '; i++ }
                }
                text.startsWith("/*", i) -> {
                    var depth = 0
                    while (i < text.length) {
                        if (text.startsWith("/*", i)) { depth += 2; i += 2 }
                        else if (text.startsWith("*/", i)) { depth -= 2; i += 2 }
                        else {
                            if (text[i] != '\n') out[i] = ' '
                            i++
                        }
                        if (depth == 0) break
                    }
                }
                text.startsWith("\"\"\"", i) -> i = blankQuoted(out, text, i, true)
                text[i] == '"' || text[i] == '\'' -> i = blankQuoted(out, text, i, false)
                else -> i++
            }
        }
        return out.toString()
    }

    /** 引号留着、内容抹成空格；串里的 `Box(`、`clickable` 都不算一处 */
    private fun blankQuoted(out: StringBuilder, text: String, start: Int, triple: Boolean): Int {
        val close = if (triple) "\"\"\"" else text[start].toString()
        var i = start + close.length
        val singleLineLimit = if (triple) -1 else text.indexOf('\n', start)
        while (i < text.length) {
            if (text[i] == '\\') {
                out[i] = ' '
                if (i + 1 < text.length && text[i + 1] != '\n') out[i + 1] = ' '
                i += 2
                continue
            }
            if (text.startsWith(close, i)) return i + close.length
            if (!triple && singleLineLimit >= 0 && i > singleLineLimit) return i
            if (text[i] != '\n') out[i] = ' '
            i++
        }
        return i
    }

    private val dataTypes = listOf(
        "KnowledgeRepository",
        "DeepSeekRepository",
        "FeedbackCaseRepository",
        "SecurePrefs"
    )

    @Test
    fun `ui layer never injects a repository or prefs directly`() {
        val offenders = mutableListOf<String>()
        kotlinFiles(dir("ui")).forEach { file ->
            val code = codeOf(file.readText())
            val hits = dataTypes.filter { type ->
                Regex("(:\\s*\\b$type\\b|\\b$type\\()").containsMatchIn(code) ||
                    code.contains("by inject()") && code.contains(type)
            }
            if (hits.isNotEmpty()) offenders += "${file.name} -> $hits"
        }
        assertTrue(
            "ui 层必须经 ViewModel 访问数据层，违规：\n$offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun `knowledge base screens delegate data access to their view models`() {
        listOf("KnowledgeBaseActivity.kt", "KbEditActivity.kt").forEach { name ->
            val code = codeOf(File(dir("ui"), name).readText())
            dataTypes.forEach { type ->
                assertFalse(
                    "$name 不应再直接引用 $type（：改走 ViewModel）",
                    code.contains(type)
                )
            }
            assertTrue("$name must obtain its ViewModel", code.contains("by viewModel()"))
        }
    }

    @Test
    fun `production sources carry no debt-note comments without a fix`() {
        // 报告点名的写法："此处直接 inject Repository 违反 SRP/DIP"
        val pattern = Regex("(审计技术债|修复方向[:：])")
        val offenders = kotlinFiles(appRoot)
            .map { it.name to codeOf(it.readText()) }
            .filter { (_, code) -> pattern.containsMatchIn(code) }
            .map { it.first }
        assertTrue("存在只记录不修复的注释：$offenders", offenders.isEmpty())
    }

    @Test
    fun `view models are the only ui-facing owner of data access`() {
        val registered = File(dir("di"), "AppModule.kt").readText()
        listOf("KnowledgeBaseViewModel", "KbEditViewModel", "SetupViewModel", "LoveBrainViewModel")
            .forEach { name ->
                assertTrue("$name must be registered in Koin", registered.contains(name))
            }
    }

    /**
     * 第6节第1条：设计系统的组件必须**住在** core/designsystem，且旧名字不许回来。
     *
     * 为什么要有这条：搬家的失败模式不是"搬不过去"（编译会红），而是**搬完之后**
     * 有人在 ui/home 里再声明一颗同名的 `HomeActionCard` 顶掉它——core 那份还在、
     * 编译还绿，设计系统却重新退化成"目录里有个文件"。这正是本文件其它规则对付过的
     * 同一类漂移：要看住的东西得真被扫到，而不是靠人记得。
     *
     * 三条判据（都是全仓扫，不限目录）：
     * ① 旧名字（含 typealias）全仓声明数 = 0；
     * ② 新名字全仓声明数 = 1（多了就是有人复制了一份分叉）；
     * ③ 那唯一一处声明落在 core/designsystem 子树里。
     *
     * ②③ 必须分开：只写"core 里恰好一颗"的话，把整颗组件搬回 ui/home 会两半都绿
     * （core 0 颗、ui 1 颗都不报错）；只写"全仓一颗"的话，搬出 core 又抓不到。
     * 实测口径：① 11 个旧名全为 0；②③ 11 个新名各恰 1 处且在 core 子树里。
     *
     * 已知边界：按 `fun 名字(`/`typealias 名字 =` 的形状判；写成
     * `val X: (@Composable () -> Unit)` 这种函数值能躲过——那种写法本仓库没有先例。
     */
    @Test
    fun `design system components live in core and their home-prefixed names stay retired`() {
        val retired = mapOf(
            // 旧名字 -> 现在的名字
            "HomeTopBar" to "LbTopBar",
            "HomeSectionHeader" to "LbSection",
            "HomeActionCard" to "LbActionCard",
            "HomeSettingRow" to "LbSettingRow",
            "UsageSummary" to "LbMetricGrid",
            "UsageMetric" to "LbMetricCard",
            // 第6节第1条 的 B 类第一行：两颗平行旋钮（mode + enabled）收成一颗状态
            "GenerationActionButton" to "LbPrimaryButton",
            "ButtonMode" to "LbButtonState",
            // 第6节第1条 的 Sheet 半边：面板那套自画浮层归进设计系统。形状**必须**是自画的
            // （overlay 窗口起不了 Dialog，会抛 BadTokenException），但"谁拥有这个形状"仍只许一处
            "PanelModalHost" to "LbModalSheet",
            "PanelModalTitle" to "LbModalSheetTitle",
            "PanelModalActions" to "LbModalSheetActions"
        )
        assertTrue("旧名字一个都不该有，新名字 ${retired.size} 颗，所以不能扫了个空目录",
            retired.size == 11)
        val sources = kotlinFiles(appRoot).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to codeOf(it.readText())
        }

        fun declarationsOf(name: String): List<String> = sources.flatMap { (path, code) ->
            declaration(name).findAll(code).map { "$path" }
        }

        retired.keys.forEach { old ->
            val back = declarationsOf(old)
            assertTrue("这些名字已随组件搬进 core/designsystem，不许重新声明：$back", back.isEmpty())
        }

        retired.values.forEach { now ->
            val at = declarationsOf(now)
            assertTrue("$now 应当全仓只声明一次，实到 ${at.size}: $at", at.size == 1)
            assertTrue(
                "$now 的唯一声明应当在 core/designsystem 子树里，实到：$at",
                at.single().startsWith("core/designsystem/")
            )
        }
    }

    /**
     * 第6节第1条 `LbModalSheet/Dialog`：浮层只有**一个所有者**。
     *
     * 为什么不是"数一下少了几处"就完事：`AlertDialog` 是 Material 的东西，谁都能直接 call，
     * 编译永远不会红。上一格搬家时立的规矩在这里同样适用——收口之后真正要防的是
     * "第二天有人在别的页面又直接 call 了一颗"，所以这把尺盯的是**调用点**。
     *
     * 判两条：
     * ① `AlertDialog(` 在 main 里只许出现在 `core/designsystem/LbDialog.kt`，且恰 1 处；
     * ② 裸 `Dialog(`（不是 AlertDialog）只许出现在下面这个**点名豁免表**里，且计数要逐处对上——
     *    豁免必须看得见，而且它消失时这把尺也要红（不许留一条早已不成立的豁免当"管住了"）。
     */
    @Test
    fun `floating decision surfaces have exactly one owner`() {
        val owner = "core/designsystem/LbDialog.kt"
        // 这里原本是 `mapOf("ui/home/ProviderSection.kt" to 1)`——那颗供应商编辑器已在 第6节第1条
        // 归并进 `LbDialog`（标题走 title= 槽、表单走 body= 槽），自画的浮层外壳没有了 ⇒
        // **这一行按下面那条"豁免消失也要红"的规矩必须删掉**。
        // 留着不算错，但留着就等于承认"表可以比现实宽"（坑表 71 那一族）。
        val exempt: Map<String, Int> = emptyMap()
        val sources = kotlinFiles(appRoot).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to codeOf(it.readText())
        }
        val alert = Regex("\\bAlertDialog\\s*\\(")
        val bareDialog = Regex("(?<!Alert)\\bDialog\\s*\\(")

        val strayAlert = sources.filter { (path, code) ->
            alert.containsMatchIn(code) && path != owner
        }.map { it.first }
        assertTrue("生产里不许再绕过 LbDialog 直接 call AlertDialog，违规：$strayAlert",
            strayAlert.isEmpty())
        val owned = alert.findAll(sources.first { it.first == owner }.second).count()
        assertTrue(
            "$owner 应当恰好包一颗 AlertDialog（实到 $owned）；多一颗就说明有两种浮层形状" +
                "被混进了同一个所有者", owned == 1
        )

        val strayDialog = sources.filter { (path, code) ->
            bareDialog.containsMatchIn(code) && !exempt.containsKey(path) && path != owner
        }.map { it.first }
        assertTrue("新的裸 Dialog( 要么并进 LbDialog/LbModalSheet，要么在这里显式登记豁免：$strayDialog",
            strayDialog.isEmpty())
        exempt.forEach { (path, want) ->
            val code = sources.firstOrNull { it.first == path }?.second
                ?: error("豁免登记的 $path 已不在扫描范围内——豁免表要一起删，别留着当已有闸")
            val got = bareDialog.findAll(code).count()
            assertTrue(
                "$path 的裸 Dialog( 实到 $got，豁免登记的是 $want（改了就把这里一起改对）",
                got == want
            )
        }
    }

    /**
     * 第6节第4条 / 第6节第1条：**整屏遮罩只有一个所有者**。
     *
     * 这条是被量出来的：生产里原先有三处各自画 `Color.Black.copy(alpha = …)` 的全屏遮罩
     * （`LbModalSheet` 自己、`RecordSentDialog`、`FeedbackCasesScreen` 的导出 Loading），
     * 后两处还各带一个 `clickable` 挂在整屏上——于是语义树里多出一颗"360x1000 的按钮"，
     * 其中一处把标题合并成了自己的名字（`SheetProbeTest` 留了改之前的实测原文）。
     *
     * "用同一颗组件却自造样式"这种坏法，第26节 那把按声明处判的尺抓不到（它看的是谁**声明**了组件），
     * 所以要有一把盯着**形状本身**的闸：谁再想自己画一层遮罩，就在这里红。
     *
     * 判据取"画半透明黑底"这个具体写法而不是"有没有 fillMaxSize"：后者到处合法。
     */
    /**
     * 遮罩的**登记式豁免**（不是把扫描范围调小）：路径 → 为什么这一层不是"LbModalSheet 那一族浮层"。
     *
     * 每一颗豁免都要同时过第二把尺：那一层**不许挂 `clickable`**。这条闸当初的起因就是
     * "遮罩冒充一颗 360x1000 的按钮"，豁免只豁免"自己画一层半透明黑"这一件事，
     * 不豁免语义树那半——所以以后谁在豁免文件里给全屏层加可点，这里照样红。
     */
    private val scrimExemptions: Map<String, String> = mapOf(
        "ui/home/HomeCoachMarks.kt" to
            "覆盖式引导的那一层不是浮层，是**挖洞的聚光罩**：它要的是" +
                "\"整屏压暗 + 目标格那块透明\"（Path 挖洞），而 LbModalSheet 给的是" +
                "\"整屏压暗 + 一块居中卡\"，把罩子塞进那颗组件就得先给设计系统加一档带洞的遮罩——" +
                "那是 core 的地盘，缺件已登记（见 2026-10-06-G1b 接线单）。" +
                "这一层零 `clickable`：目标格之外那点被有意让给手指，罩子自己不当按钮"
    )

    @Test
    fun `only the sheet owner draws a full window scrim`() {
        val owner = "core/designsystem/LbModalSheet.kt"
        val scrim = Regex("Color\\.Black\\.copy\\(\\s*alpha")
        val drawers = kotlinFiles(appRoot).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to codeOf(it.readText())
        }.filter { (path, code) ->
            scrim.containsMatchIn(code) && path != owner && !scrimExemptions.containsKey(path)
        }.map { it.first }
        assertTrue(
            "整屏遮罩只许由 $owner 画（豁免要写理由，见 scrimExemptions）；这些文件自己画了一层" +
                "（改走 LbModalSheet，它顺手修掉了遮罩冒充可点击节点的问题）：$drawers",
            drawers.isEmpty()
        )
        // 豁免不是空白支票：被豁免的那一层必须还在"不可点"这条线上，且文件确实还画着遮罩
        // （文件被搬走/改名 ⇒ 这条豁免就成了幽灵，同样红）。
        scrimExemptions.forEach { (path, why) ->
            val exempt = File(appRoot, path)
            assertTrue("豁免登记指向的文件不在了：$path（理由：$why）", exempt.isFile)
            val code = codeOf(exempt.readText())
            assertTrue(
                "$path 画了遮罩就许它画遮罩；正则看不见 `Color.Black.copy(alpha` 说明这颗豁免已经没主人认领了：$why",
                scrim.containsMatchIn(code)
            )
            assertTrue(
                "豁免只豁免\u201c自己画一层半透明黑\u201d，不豁免语义树：$path 的可点遮罩回来就是当初那个\u201c360x1000 的按钮\u201d",
                !Regex("\\.clickable\\b").containsMatchIn(code)
            )
        }
        // 反向确认这把尺看得见东西：所有者自己那一份必须还在，否则就是正则坏了
        val ownerSource = File(appRoot, "core/designsystem/LbModalSheet.kt")
        assertTrue("找不到 $owner——被搬走或改名了，这把尺就成了一把扫空集的闸", ownerSource.isFile)
        assertTrue(
            "$owner 应当还在画遮罩（实到 0 处说明正则匹配不到任何写法，恒绿）",
            scrim.containsMatchIn(codeOf(ownerSource.readText()))
        )
    }

    /**
     * 第6节第4条 :523：面板上的浮层必须各归**一个 state holder**，不许在屏幕函数里
     * 随手 `var showXxx by remember { mutableStateOf(false) }`。
     *
     * 为什么不是"数到 0 就完事"：单看"杂散布尔还有几颗"这一把尺，归零之后它就变成
     * 扫空集恒绿——下次有人新加一颗，它从 0 涨到 1 才红一次，而那已经是债；更糟的是
     * 归零那天如果顺手把正则改坏，它永远绿着也没人知道。所以配**第二把反证的尺**：
     * holder 的声明数只许往上，它同时是"这把尺看得见东西"的证人——
     * 正则一旦失效，holders 数掉到 0 当场红。
     *
     * 本机实扫（`LoveBrainPanelScreen.kt`，去注释后按形状数）：
     * - holder 声明 **3** 颗：`rememberMemoryCorrectionFlow(`、`rememberCorrectionCenterHolder(`、
     *   `rememberRecordSentFlow(`
     * - 杂散可见性布尔 **1** 颗：`showOnboard`（引导卡片，不是浮层，但按形状判就会被数进来；
     *   记在这儿是为了"只许往下"，不是给它单独开后门）
     *
     * 这两个数是**成对改的**。`5d6b71a` 立这把尺时量到 3 / 2；上一格把
     * `showSentDialog` + `sentDialogSaving` 收进 `RecordSentFlow` 之后才是 1 / 3。
     * 只降杂散布尔那一侧、不抬 holder 下限的话，下限就停在"几颗都算过"上，
     * 那把反证的尺当场失效——这正是坑表 71 说的那个坑的续集。
     */
    @Test
    fun `panel decision surfaces are held by state holders, not ad-hoc booleans`() {
        val screen = File(dir("ui", "panel"), "LoveBrainPanelScreen.kt")
        assertTrue("找不到 $screen——搬家了就要同步改这条", screen.isFile)
        val code = codeOf(screen.readText())

        val adHoc = Regex("var\\s+show[A-Z]\\w*\\s+by\\s+remember\\s*\\{\\s*mutableStateOf\\(")
            .findAll(code).count() +
            Regex("var\\s+\\w*(Saving|Expanded|Open)\\s+by\\s+remember\\s*\\{\\s*mutableStateOf\\(")
                .findAll(code).count()
        val holders = Regex("remember[A-Z]\\w*(Flow|Holder)\\(").findAll(code).count()

        val adHocBudget = 1
        val holderFloor = 3
        assertTrue(
            "面板里杂散的可见性布尔 $adHoc 颗，棘轮 $adHocBudget——" +
                "新的浮层请开一个 holder（ :523），别在屏幕函数里加 showXxx",
            adHoc <= adHocBudget
        )
        assertTrue(
            "面板至少要认得出 $holderFloor 颗 holder，实到 $holders——" +
                "变小说明持有者被拆回内联状态，或者这条正则已经扫不到东西了",
            holders >= holderFloor
        )
    }

    /**
     * "谁画整屏底色"那把尺的口径。
     *
     * 三种写法都要认得：`.background(SurfaceBase)`、`.background(color = SurfaceBase)`、
     * `.background(com.lovebrain...SurfaceBase)`。P5 那发变异证明只认第一种会漏后两种
     * （见 `the page frame has exactly one owner` 里那段正向对照）。
     */
    private val pageFrameDraw =
        Regex("""background\(\s*(?:color\s*=\s*)?(?:[\w.]+\.)?SurfaceBase\b""")

    /**
     * 第6节第1条 表最后一行：`LbScreenScaffold` —— **页面外框只有一个所有者**。
     *
     * 判据取"谁画整屏底色"这一个具体写法（`background(SurfaceBase`），
     * 不取"有没有 fillMaxSize"——后者到处合法。理由与 第29节/第30节 那两把同源：
     * "用了同一个 token 却各页自己拼一层外框"这种坏法，按声明处判的尺抓不到。
     *
     * 名单是**本机实扫**出来的（去注释之后），分两类，别混：
     * - 所有者：`core/designsystem/LbScreenScaffold.kt`，1 处。
     * - **不是页面的那几个**（登记在这里是因为它们本就不该走页面外框）：
     *   `ui/home/SetupRoot.kt` 是路由宿主（它套着 600dp 限宽与 insets，
     *   目标页在它里面再走一次脚手架）、
     *   `ui/panel/LoveBrainPanelScreen.kt` 跑在 `TYPE_APPLICATION_OVERLAY` 窗口里，
     *   没有系统栏也不受页面边距档管。
     *   （`ui/panel/SuggestPanel.kt` 曾与 LoveBrainPanelScreen 同列这里，因为那一页在 overlay
     *   里自己画 SurfaceBase；锦囊页 第12节第1条 删除、文件 mv 成 `IntentEditorSheet.kt` 后只剩
     *   编辑浮层的开关/卡片，全不涂 SurfaceBase ⇒ 这一行随现实改小、从表里删掉。）
     *
     * **欠账清零**（`04259ef` 之后那一格，账本 第58节）：`ui/feedback/FeedbackCasesScreen.kt`
     * 曾是表里唯一一条"这一页还在自己画外框"的豁免，现已搬进脚手架、那一行从表里删了。
     * 删掉的理由写在这儿，别让下一个人以为豁免还在：它内部一堆区块原本自带 `Spacing.lg`，
     * 搬的时候必须连着把芯片行与列表的 `contentPadding` 一起归零，
     * 否则就是"外面 24 里面又 12"叠两层——那件事做完了，量到的水平边距从 12dp 变 24dp
     * （证人：`ScreenScaffoldFrameTest` 第四格）。
     */
    @Test
    fun `the page frame has exactly one owner`() {
        val owner = "core/designsystem/LbScreenScaffold.kt"
        val notAPage = setOf(
            "ui/home/SetupRoot.kt",
            "ui/panel/LoveBrainPanelScreen.kt"
        )
        // 曾经这里是 `mapOf("ui/feedback/FeedbackCasesScreen.kt" to 1)`——那一页已搬进
        // 脚手架，**债还了就得把表改小**（留着一条不成立的豁免比没有豁免更坏）。
        val registeredDebt: Map<String, Int> = emptyMap()

        val sources = kotlinFiles(appRoot).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to codeOf(it.readText())
        }
        val drawers = sources.filter { (_, code) -> pageFrameDraw.containsMatchIn(code) }
            .map { it.first }

        // ⚠ 这把尺**换过口径**，是被 P5 那发变异打出来的（账本 第58节第5条）：原来写成
        //   `code.contains("background(SurfaceBase")`，于是两种合法写法它都看不见——
        //   `background(color = SurfaceBase)`（具名实参）与
        //   `background(com.lovebrain.app.core.designsystem.SurfaceBase)`（全限定）。
        //   注入后者时这一格**照样绿**：删掉豁免之后闸是空的，比留着豁免更危险。
        assertTrue(
            "整屏底色那把尺必须同时认得直涂与具名/全限定两种写法，任一档归零就是尺又瞎了",
            pageFrameDraw.containsMatchIn("Modifier.fillMaxSize().background(SurfaceBase)") &&
                pageFrameDraw.containsMatchIn("Modifier.background(color = SurfaceBase)") &&
                pageFrameDraw.containsMatchIn(
                    "Modifier.background(com.lovebrain.app.core.designsystem.SurfaceBase)"
                ) &&
                !pageFrameDraw.containsMatchIn("Modifier.background(SurfaceCard)")
        )

        val strays = drawers.filter {
            it != owner && !notAPage.contains(it) && !registeredDebt.containsKey(it)
        }
        assertTrue("新的整屏底色别在页面里自己画，走 LbScreenScaffold；违规：$strays", strays.isEmpty())

        assertTrue(
            "$owner 应当恰好画一次整屏底色（实到 ${drawers.count { it == owner }}）——" +
                "多一次就是又长出第二种页面外框",
            drawers.count { it == owner } == 1
        )
        // 反向确认这把尺看得见东西：所有者那份必须还在，否则是正则坏了
        assertTrue(
            "找不到 $owner——被搬走或改名了，这把尺就成了扫空集的闸",
            File(appRoot, owner).isFile
        )
        registeredDebt.forEach { (path, want) ->
            val code = sources.firstOrNull { it.first == path }?.second
                ?: error("$path 的欠账登记已不成立——搬完了就把这一行从表里删掉，别留着当已有闸")
            val got = pageFrameDraw.findAll(code).count()
            // 判 ==，不判 <=：登记着 1 处而实到 0 处，意思是**这笔债已经还了**，
            // 表里那一行必须当场删掉。留一条早已不成立的豁免，比没有豁免更坏——
            // 它会让下一个人以为这一页已经处理过了（第29节 那把"点名豁免"的尺同一课）。
            assertTrue(
                "$path 实到 $got 处，登记的是 $want。多了是新债；" +
                    "零了是已经还完——那就把这一行从表里删掉，别留着当已有闸",
                got == want
            )
        }
    }

    /**
     * 第6节第1条 :479：页头只有一个所有者。
     *
     * 判据取"谁自己画返回那颗"这个具体形状：箭头字形 `"←"` 或 `KeyboardArrowLeft`。
     * 为什么不是数"有几个 Row"：`Row` 到处合法；而"自己拼一颗返回"必然要画那个字形，
     * 抓得住。与 第36节 那把"整屏底色只有一个所有者"同族，也是被同一条理由逼出来的——
     * `PageHeaderConsistencyTest` 量到四式并存时，读屏名字有两派是**空串**。
     *
     * 本机实扫（去注释后）：所有者 1 处；**登记的欠账 1 个文件 2 处**（面板设置那一格紧凑页头，
     * 见下面 `registeredDebt` 那段——收口条件写在那儿，不许把它读成"已处理"）。
     *
     * ⚠ **勘误（账本 第58节）**：这一格的注释以前写着"反馈案例页没顺手一起搬的理由之一：
     * 这一页要 `rememberLauncherForActivityResult`，**JVM 上挂不起来**——搬一页却量不到搬的效果，
     * 等于自签"。**那条理由是错的**：`createComposeRule` 下注册 launcher 不报错，
     * 第57节 的一次性探针就是挂着这一页量到 9 颗节点的；挂不起来的是**触发**那一步
     * （`saveLauncher.launch()` 要起真 Activity 选择器）。用一条没验过的"测不到"
     * 给一笔欠账签字，欠账就会一直活着——这一格本身就是坑表那条"「要 VM」不是测不到的理由"的
     * 又一个形状。
     *
     * 搬完之后：`(N条)` 那句与页头那颗导出的标签进了资源（英文环境不再念中文），
     * 量到的读屏名字由 `FeedbackCasesSemanticsTest` 那几格钉住
     * （返回那颗现在念 `R.string.common_back`，不再念「←」这个字形）。
     * 还完之后这一行必须删，不许留着一条已不成立的豁免当"管住了"（同 第36节 那条规矩）。
     */
    @Test
    fun `the page header has exactly one owner`() {
        val owner = "core/designsystem/LbTopBar.kt"
        // 2026-10-06 S1b 登记一笔欠账（**不是**放宽判据，这把尺照旧按"字形出现几处"数）：
        // 面板设置那一格的页头走的是基线 v1.2 的紧凑档（整行 30dp），它确实没有自己拼箭头——
        // 它把 `Icons.AutoMirrored.Filled.KeyboardArrowLeft` 交给设计系统唯一主人 `LbTextAction`
        // 的 `RowIcon` 档画（16dp 字形 / 28 见方热区）。但这把尺按**字形读数**判，看不见"交给了谁"，
        // 于是 import 一行 + 用法一行 = 2 处，按实到登记。
        // 收口条件（写死在这里，别让它当"已处理"）：要么这一格改读 `LbTopBar` 的紧凑档
        // （那需要 core 给 LbTopBar 加一档，属 core 席），要么这把尺改成"只认自画箭头"
        // （那就得先有反例证人，证明改窄之后仍然抓得住自己拼返回的那一族）。
        val registeredDebt: Map<String, Int> = mapOf(
            "ui/panel/settings/LoveBrainSettingsContent.kt" to 2
        )
        val glyph = Regex(""""←"""")
        val icon = Regex("\\bKeyboardArrowLeft\\b")

        val sources = kotlinFiles(appRoot).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to codeOf(it.readText())
        }
        val drawers = sources.filter { (_, code) -> glyph.containsMatchIn(code) || icon.containsMatchIn(code) }
            .map { it.first }

        val strays = drawers.filter { it != owner && !registeredDebt.containsKey(it) }
        assertTrue(
            "新的页头别自己拼返回那颗，走 LbTopBar（它把读屏名字挂在资源上）；违规：$strays",
            strays.isEmpty()
        )
        assertTrue(
            "$owner 必须自己用那个字形——实到 ${drawers.count { it == owner }}，" +
                "为 0 说明这把尺已经扫不到任何东西了（恒绿假闸）",
            drawers.count { it == owner } == 1
        )
        registeredDebt.forEach { (path, want) ->
            val code = sources.firstOrNull { it.first == path }?.second
                ?: error("$path 的欠账登记已不成立——搬完了把这一行从表里删掉")
            val got = glyph.findAll(code).count() + icon.findAll(code).count()
            assertTrue(
                "$path 实到 $got 处，登记的是 $want。多了是新债；零了是还完了——" +
                    "那就把这一行从表里删掉",
                got == want
            )
        }
    }

    /**
     * 第一把品牌底尺：`.background(品牌色)` 的表面色，**不管可不可点**。
     *
     * 它买的东西按关系写（见 [assertBrandLedger]）：新长一处 = 红、还完债不删行 = 红、
     * 每一行指不出主人 = 红。它**证明不了任何一处"该搬"**：同样一串 `.background(Primary…)` 里，
     * 有页面主动作（该走 `LbPrimaryButton`）、有选中态 chip（就该是那个样子）、有带状态的切换、
     * 也有面板自身的表面色——谁算哪种要逐处读语义，那份判读不在这把尺里。
     *
     * ⚠ 与"自画可点控件"那把是**两把不同的尺，别把两个数合成一个**：那把要求同一条 Modifier 链上有
     * `clickable`，这把不要求 ⇒ 面板表面、加载条、行底色都只进这一把。
     * 热区/角色这类真性质由 `SemanticsProbe` 那些语义树格子判，不由这一格判。
     */
    @Test
    fun `hand-drawn brand-toned surfaces do not grow`() {
        // 口径：`.background(品牌色)`（含条件涂色那一档），按文件记，**只许往下**。
        // 与"自画可点控件"那把是两把不同的尺：这一把不管可不可点，所以面板表面、加载条、
        // 行底色都算；两把的数不许合成一个数。
        // 每家的额度 = 本次本机实扫；表里每一行还必须指得出一颗**主人**（设计系统里真有那一颗，
        // 或明写"无同类"）——额度只挡"长新的"，主人那一列挡的是"这处自画到底该归谁"。
        val ledger = surfacesLedger
        val uiRoot = dir("ui")
        val scanned = kotlinFiles(uiRoot).map {
            it.relativeTo(uiRoot).invariantSeparatorsPath to brandTonedArgs(
                codeOf(it.readText()),
                Regex("""\.background\(\s*(?:color\s*=\s*)?"""),
                brandToneNoSubtle
            )
        }.filter { it.second.isNotEmpty() }
        val found = scanned.associate { it.first to it.second.size }

        // 正向对照：两档写法都要认得。这把尺上一次就是瞎在"条件涂色"上，
        // 任何一档变成 0 都得停下来分辨——是代码真没了，还是尺又漏了。
        val conditional = scanned.flatMap { it.second }
            .count { it.trimStart().startsWith("if") || it.trimStart().startsWith("when") }
        val direct = scanned.flatMap { it.second }.count { it.trimStart().startsWith("Primary") }
        assertTrue(
            "条件涂色扫到 $conditional 处、直涂 $direct 处；任何一档归零都要先怀疑尺（旧口径正是断在 `if (…)` 的右括号）",
            conditional > 0 && direct > 0
        )
        assertBrandLedger("表面色（.background）", uiRoot, ledger, found, scanned)
    }

    /**
     * 声明的形状：`fun X(`、`typealias X =`、`class|enum class|interface X {|<|(:`。
     *
     * 三支都得在：`typealias` 后面跟的是 `=` 不是 `(`（第一版就漏在这一支上，
     * 探针"没咬"才发现它一直是死的）；`ButtonMode` 这类**状态表**是 `enum class`，
     * 名字后面直接跟 `{`，只认 `fun` 或只认括号的话它整个逃出尺外。
     */
    private fun declaration(name: String): Regex = Regex(
        "\\b(?:fun|typealias|class|interface)\\s+`?$name`?\\s*[<(=:{]"
    )

    /**
     * 「伸手进 VM 拿仓库」等价于直接 inject 仓库。
     *
     * SetupActivity 原先写的是 `viewModel.securePrefs.hasCompletedOnboarding`——
     * 类型上确实没在 ui 包出现 SecurePrefs，前面那条规则抓不到它，但分层已经被穿透了。
     * 所以这条规则按**属性穿透**判，而不是按类型名判。
     */
    @Test
    fun `ui layer must not reach through a view model into the data layer`() {
        val laundered = Regex("\\.(securePrefs|knowledgeRepo|deepSeekRepo|repo|feedbackCaseRepository)\\b")
        val offenders = kotlinFiles(dir("ui"))
            .map { it.name to codeOf(it.readText()) }
            .filter { (_, code) -> laundered.containsMatchIn(code) }
            .map { it.first }
        assertTrue(
            "ui 层不得通过 viewModel.xxxRepo 间接访问数据层（该走 VM 上语义化方法）：$offenders",
            offenders.isEmpty()
        )
    }

    /**
     * Activity 取 ViewModel 必须经 ViewModelStore。
     *
     * `by inject()` 每次解析一个新实例，配置变更后 VM 内存态（反馈列表、导出状态）直接丢失；
     * 报告 要的是"数据逻辑归 ViewModel"，如果 VM 活不过旋转，这句话就落不了地。
     */
    @Test
    fun `activities obtain view models through the ViewModelStore not raw injection`() {
        val vmProperty = Regex(":\\s*(\\w+ViewModel)\\s+by\\s+(inject|lazy)")
        // 按**类名**而不是文件名筛 Activity：注入点写在哪个文件里不重要，
        // 第一版按 "xxxActivity.kt" 过滤，负向用例把 Activity 放进 ZzLayerProbe.kt 就躲过去了。
        val offenders = kotlinFiles(dir("ui"))
            .map { it.name to codeOf(it.readText()) }
            .filter { (_, code) ->
                vmProperty.containsMatchIn(code) ||
                    Regex("class\\s+\\w*Activity\\b").containsMatchIn(code) &&
                        Regex("by\\s+(inject|lazy)\\s*\\(\\s*\\)").containsMatchIn(code)
            }
            .map { it.first }
        assertTrue(
            "ui 层的 ViewModel 必须用 by viewModel() 取，使实例挂在 ViewModelStore 上：$offenders",
            offenders.isEmpty()
        )
        // 反向确认这条规则真的在看着东西，不是扫了个空目录
        val activitiesUsingViewModelStore = kotlinFiles(dir("ui"))
            .map { codeOf(it.readText()) }
            .count { it.contains("by viewModel()") }
        assertTrue(
            "至少 3 处应经 viewModel() 取得 VM，实测 $activitiesUsingViewModelStore",
            activitiesUsingViewModelStore >= 3
        )
    }

    /**
     * 第二把品牌底尺：自画的、涂着品牌底的**可点**控件（唯一实现在 `SourceScan.actionableBranded`）。
     *
     * 为什么收进仓库而不是留在仓库外的脚本：脚本不进 CI、没有证人、也没有正向对照，而且它
     * **只看 `clickable` 自己那条链**——把角色 chip 与「添加」改成"热区与视觉分两层"
     * （外层可点、里层涂底）之后，它从 17 处掉到 15 处：**一处债都没还，数字自己降了**。
     * 那一族要防的正是"某一页看起来不一样"，尺瞎了就会把它登记成进展。
     *
     * 判据的形状（每档都有正向对照，见 `SourceScanTest`）：
     * ①`clickable` 所在的那次组合调用，**子树里**涂着品牌底——含尾随 lambda 里那层
     * （Compose 的 `Box( … ) { … }`，内容在配对右括号**之外**，只按右括号取范围就认不出两层形状）；
     * ②品牌底允许条件涂色（`if (selected) Primary else SurfaceInset`）；
     * ③注释一律按字符掩成空格（长度不变 ⇒ 行号可信），而**字符串里的 `//` 不算注释**。
     *
     * ⚠ 这把尺比它替代的那把**严**：多出来的那些处不是新债，是以前看不见的形状（热区分层、
     * "clickable 在外层品牌盒子的尾随 lambda 里"那种嵌套）。所以它只买"别再长新的"，
     * **不许在本轮顺手宣布"每一处都判完了"**——每一处该不该并、归哪一颗，是逐处读语义的判读。
     */
    @Test
    fun `hand-drawn brand-toned actionable widgets do not grow`() {
        val uiRoot = dir("ui")
        val scanned = kotlinFiles(uiRoot).map {
            val masked = SourceScan.maskComments(it.readText())
            it.relativeTo(uiRoot).invariantSeparatorsPath to
                SourceScan.actionableBranded(masked).map { (click, _) -> SourceScan.lineOf(masked, click).toString() }
        }.filter { it.second.isNotEmpty() }
        val found = scanned.associate { it.first to it.second.size }
        assertBrandLedger("可点控件（链上有 clickable）", uiRoot, actionableLedger, found, scanned)
        // 行号只当读数印出来（改名/搬家会让行号漂，判据不押在行号上）
        assertTrue(
            "实扫明细（一家一处都看得见）：" + scanned.joinToString { "${it.first}=${it.second}" },
            found.isNotEmpty()
        )
    }

    /**
     * 那把 48dp 下限尺——**这个数在仓库里只许写一次**，并且数是从主人那里现读的。
     *
     * 为什么要买这一格：下限原先以 `const val … = 48` 的形式写了 17 遍，
     * 数写 17 遍等于**没有下限**：抬它要改 17 处，漏一处就只有那一屏的热区偷偷不达标，
     * 而"无小于 48dp 热区"是全站口径、不是逐组件口径。
     *
     * ⚠ **这一格判不了任何一处尺寸对不对**，它只保证"改一次数就改全站"这件事成立。
     * 真热区由 `SemanticsProbe` 那些格子量（`LbTextActionTest`、`LbAsyncStateTest`、
     * `OnboardingPrimaryActionTest`、`ProviderFormSemanticsTest`），这一格只买"不许再长出第二个数"。
     *
     * 本轮合同把若干**明写的矮档**写进了尺寸表（28 卡片/行内紧凑盒、36 输入框与结果双出口、
     * 40 面板主动作），它们**不是**第二颗下限：不进这一格的白名单，
     * 而由 `the compact tiers this round names are declared once and read somewhere`
     * 与 `every number on a clickable chain traces back to a design system owner` 两格分别盯
     * （前者管"有名字且真被人读"，后者管"挂在可点链上的那颗数解得出主人"）。
     */
    @Test
    fun `the touch-target floor is written as a number in exactly one place`() {
        val ownerPath = "core/designsystem/Dimens.kt"
        val sources = pageAndCoreSources()
        // 数从主人那里读，不抄在判据里：抬那颗下限时这一格跟着走，不需要有人记得来改这里
        val (_, floorValue) = requireFloorConstant(sources)
        val allowedDeclarations = setOf(
            ownerPath to "TOUCH_TARGET_MIN_DP",      // 唯一那颗下限
            ownerPath to "EMPTY_ICON_CONTAINER_DP"   // 版式尺寸，恰好同数，不是下限
        )
        // 内联字面量的存量账：登记的都是"某处确实还写着一颗与下限同数的**版式**尺寸"。
        // 多出的是新债（要改成引用）；清零了的就是还完了（那一行要删，不许留着当已有闸）。
        val allowedInlineLiterals = mapOf(
            "core/designsystem/LbActionCard.kt" to 1
        )
        val declaration = Regex("""\bval\s+(\w+)\s*=\s*$floorValue\b""")
        val inlineLiteral = Regex("""$floorValue\.dp""")

        val declarations = mutableListOf<Pair<String, String>>()
        val inlineCounts = mutableMapOf<String, Int>()
        sources.forEach { (rel, code) ->
            val plain = codeOf(File(appRoot, rel).readText())
            declaration.findAll(plain).forEach { declarations.add(rel to it.groupValues[1]) }
            val hits = inlineLiteral.findAll(plain).count()
            if (hits > 0) inlineCounts[rel] = hits
        }

        assertTrue(
            "把 48 当数值写进 decl 的只许白名单那两处；多出的一律改成引用 AppDimens.TOUCH_TARGET_MIN_DP" +
                "（版式尺寸也要自己说明它为什么不是下限）。实到：$declarations",
            declarations.toSet() == allowedDeclarations
        )
        assertTrue(
            "同一条声明被写了不止一次（尺按名字去重会看不见），实到 ${declarations.size} 条",
            declarations.size == allowedDeclarations.size
        )
        // 反证：owner 必须在。扫不到就说明锚点或路径错了，整格会恒绿
        assertTrue(
            "$ownerPath 里必须还看得到 TOUCH_TARGET_MIN_DP = 48，扫不到说明这把尺已经瞎了",
            declarations.contains(ownerPath to "TOUCH_TARGET_MIN_DP")
        )
        assertTrue(
            "内联 48.dp 的分布变了：登记 $allowedInlineLiterals，实到 $inlineCounts。" +
                "多出的是新债；少掉的是还完了——那就把表里那一行删掉",
            inlineCounts.toMap() == allowedInlineLiterals
        )
        allowedInlineLiterals.keys.forEach { path ->
            assertTrue("$path 已不在原位——表里这一行要一起删掉，别留着当已有闸",
                File(appRoot, path).isFile)
        }
    }

    /**
     * 热区这一族共用的仪器：先把 main 里的常量表读成「名字 → 数 + 住在哪颗文件」，
     * 再把每一条**挂着点击语义的 Modifier 链**上写下的数顺藤摸到它的最终主人。
     *
     * ⚠ 三格测的都是**来路与命名**，不是尺寸本身（这句话也写进了每格的失败信息里）。
     * 真实热区由 `SemanticsProbe` 那些语义树格子量；这一族买的是另外两件事：
     *  - 下限那颗数是从 `Dimens.kt` 现读的，不抄在判据里 ⇒ 抬下限时这三格跟着一起动；
     *  - 低于下限的每一颗数都必须"在这条链上被真引用"并且登记了主人 + 预期数
     *    ⇒ 页面把 28 偷偷改成 20、或新长一颗没人认领的矮档，当场红。
     *
     * 旧的写法只按 `NAME\s*\.` 认引用，于是 `PanelDimens.TOUCH_TARGET_MIN_DP.dp`、
     * `AppDimens.CARD_ACTION_HIT_DP.dp` 这种**具名限定**的读法一律被判成"没人读"——
     * 七条假红全断在这上面。现在按词边界认引用，并且数是从声明链上解出来的。
     */
    private data class Declaration(val file: String, val initializer: String)

    private data class ConstantIndex(
        val objects: Map<String, Map<String, Declaration>>,
        val globals: Map<String, List<Declaration>>,
        val coreTierProperties: Set<String>
    )

    /** 摸出来的那颗数：住在哪、叫什么、值多少；`viaOwnerTier` = 由 core 的档型属性给 */
    private data class Resolved(
        val value: Int?,
        val terminalFile: String?,
        val terminalName: String,
        val viaOwnerTier: Boolean = false,
        val ambiguous: Boolean = false
    )

    private fun indexConstants(sources: List<Pair<String, String>>): ConstantIndex {
        val objects = mutableMapOf<String, MutableMap<String, Declaration>>()
        val globals = mutableMapOf<String, MutableList<Declaration>>()
        val coreProps = mutableSetOf<String>()
        val objectHead = Regex("""\b(?:object|enum class|class)\s+(\w+)""")
        val member = Regex("""\b(?:const )?val\s+(\w+)\s*(?::\s*\w+)?\s*=\s*([^\n,]+)""")
        val global = Regex("""(?m)^[ \t]*(?:private |internal |public )?(?:const )?val\s+(\w+)\s*=\s*([^\n,]+)""")
        val declaredProp = Regex("""\bval\s+(\w+)\s*(?::\s*Int)?\s*(?:=|[,)])""")
        sources.forEach { (path, code) ->
            global.findAll(code).forEach {
                globals.getOrPut(it.groupValues[1]) { mutableListOf() } += Declaration(path, it.groupValues[2].trim())
            }
            objectHead.findAll(code).forEach { head ->
                val open = code.indexOf('{', head.range.last)
                if (open < 0) return@forEach
                val body = code.substring(open + 1, blockEnd(code, open))
                val members = objects.getOrPut(head.groupValues[1]) { mutableMapOf() }
                member.findAll(body).forEach { members[it.groupValues[1]] = Declaration(path, it.groupValues[2].trim()) }
                if (path.startsWith("core/designsystem/")) {
                    declaredProp.findAll(code).forEach { coreProps += it.groupValues[1] }
                }
            }
        }
        return ConstantIndex(objects, globals, coreProps)
    }

    /** 从 [open]（左括号下标）按配对走到右括号之后 */
    private fun blockEnd(code: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        return code.length
    }

    /**
     * 顺着一颗名字走到最终那个数：`A.B = AppDimens.X` → `X = 48` 一路解到底。
     * 解到底的那颗住在哪 = 这一处数的主人；[knownName] 把最后那一级**名字**带到底
     * （末级是裸字面量时，"主人叫什么"就靠它，否则别名链会解出数却认不出指回的是哪颗）。
     */
    private fun resolveExpr(
        raw: String,
        file: String,
        depth: Int,
        index: ConstantIndex,
        knownName: String = ""
    ): Resolved {
        val expr = raw.trim()
        expr.toIntOrNull()?.let {
            val name = if (knownName.toIntOrNull() == null && knownName.isNotEmpty()) knownName else expr
            return Resolved(it, file, name)
        }
        if (depth > 6) return Resolved(null, null, expr)
        val parts = expr.split('.')
        val head = parts.first()
        val tail = parts.drop(1)
        if (tail.isNotEmpty() && index.objects.containsKey(head)) {
            val members = index.objects.getValue(head)
            val asWritten = members[tail.joinToString(".")]
            if (asWritten != null) {
                // `LbButtonHeightTier.PanelPrimaryAction.minHeightDp` 这种"档型属性"：数由主人的档表给
                if (index.coreTierProperties.contains(tail.last()) && tail.size > 1) {
                    return Resolved(null, asWritten.file, tail.joinToString("."), viaOwnerTier = true)
                }
                return resolveExpr(
                    asWritten.initializer, asWritten.file, depth + 1, index,
                    knownName = tail.joinToString(".")
                )
            }
            val oneLevel = members[tail.first()]
            if (oneLevel != null) {
                if (index.coreTierProperties.contains(tail.last())) {
                    return Resolved(null, oneLevel.file, tail.last(), viaOwnerTier = true)
                }
                return resolveExpr(oneLevel.initializer, oneLevel.file, depth + 1, index, knownName = tail.first())
            }
            if (index.coreTierProperties.contains(tail.last())) {
                return Resolved(null, null, tail.last(), viaOwnerTier = true)
            }
            return Resolved(null, null, expr)
        }
        if (tail.isEmpty()) {
            val all = index.globals[head] ?: emptyList()
            val cands = all.filter { it.file == file }.ifEmpty { all }
            if (cands.size == 1) {
                return resolveExpr(cands[0].initializer, cands[0].file, depth + 1, index, knownName = head)
            }
            return Resolved(null, null, expr, ambiguous = cands.size > 1)
        }
        return Resolved(null, null, expr)
    }

    /**
     * 一条链上写下的那个数：`min = X.dp` / `.size(X.dp ± Y.dp)`，含 `X.dp - 8.dp` 那种算式。
     * 只认写在同一行的形状（跨行的 min 垫读不到 ⇒ 靠"扫到的条数太少=尺瞎了"那条证人兜）。
     */
    private data class HitSite(
        val path: String,
        val line: Int,
        val modifier: String,
        val expr: String,
        val value: Int?,
        val terminalFile: String?,
        val terminalName: String,
        val viaOwnerTier: Boolean,
        val unresolved: Boolean
    )

    private val hitWrite = Regex(
        """(?:(heightIn|widthIn)\(\s*min\s*=\s*|\.(size|height|width)\()([\w.]+\.dp(?:\s*[-+]\s*[\w.]+\.dp)?)"""
    )

    private val interactionOnChain = Regex("""\.\s*(clickable|combinedClickable|selectable|toggleable)\b""")

    /** 括号/大括号配平地往前走，走到这条 Modifier 链的终点（尾随 lambda 的 `}` 也算断点） */
    private fun chainEnd(masked: String, from: Int): Int {
        var i = from
        var depth = 0
        while (i < masked.length) {
            when (masked[i]) {
                '(', '[', '{' -> depth++
                ')', ']' -> {
                    if (depth == 0) return i
                    depth--
                }
                '}' -> {
                    if (depth == 0) return i
                    depth--
                }
                ',', ';' -> if (depth == 0) return i
            }
            i++
        }
        return masked.length
    }

    /** 从 [off] 往回走到"包住它的那条链的头部"（能穿过 `then(if (…) Modifier.clickable …)` 那层） */
    private fun chainStart(masked: String, off: Int): Int {
        val lo = maxOf(0, off - 6000)
        var k = masked.indexOf("Modifier", lo)
        while (k in 0 until off) {
            val atHead = k == 0 || !(masked[k - 1].isLetterOrDigit() || masked[k - 1] == '_')
            if (atHead && chainHeadReaches(masked, k, off)) return k
            k = masked.indexOf("Modifier", k + 1)
        }
        val lineStart = masked.lastIndexOf('\n', off)
        return if (lineStart < lo) lo else lineStart + 1
    }

    private fun chainHeadReaches(masked: String, from: Int, to: Int): Boolean {
        var depth = 0
        var i = from
        while (i < to) {
            when (masked[i]) {
                '(', '[', '{' -> depth++
                ')', ']' -> {
                    if (depth == 0) return false
                    depth--
                }
                '}', ';', ',' -> if (depth == 0) return false
            }
            i++
        }
        return true
    }

    /** 把 `A.dp - 8.dp` 这种算式拆成带符号的项，逐项解数；有一项解不出就整颗解不出 */
    private fun evalExpression(text: String, index: ConstantIndex, file: String): Resolved {
        val terms = Regex("""([-+]?)\s*([\w.]+?)\.dp""")
            .findAll(text.trim()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        if (terms.isEmpty()) return Resolved(null, null, text)
        var sum = 0
        var name = ""
        var terminalFile: String? = null
        var viaTier = false
        for ((sign, term) in terms) {
            val one = resolveExpr(term, file, 0, index, knownName = term)
            if (one.value == null) {
                return Resolved(one.value, one.terminalFile, term, one.viaOwnerTier, one.ambiguous)
            }
            sum += if (sign == "-") -one.value else one.value
            if (term.toIntOrNull() == null) name = term
            terminalFile = one.terminalFile ?: terminalFile
            viaTier = viaTier || one.viaOwnerTier
        }
        return Resolved(sum, terminalFile, name.ifEmpty { terms.joinToString(" ").trim() }, viaTier)
    }

    /** 每一处"挂在可点链上的数"：算出它解到哪颗、那颗住在哪 */
    private fun hitSites(sources: List<Pair<String, String>>, index: ConstantIndex): List<HitSite> {
        val out = LinkedHashMap<String, HitSite>()
        sources.forEach { (path, code) ->
            interactionOnChain.findAll(code).forEach { hit ->
                val off = hit.range.first
                if (code.substring(maxOf(0, code.lastIndexOf('\n', off) + 1), off)
                        .trimStart().startsWith("import")
                ) return@forEach
                val start = chainStart(code, off)
                val chain = code.substring(start, minOf(chainEnd(code, off), code.length))
                hitWrite.findAll(chain).forEach { w ->
                    val written = w.groupValues[3].replace(" ", "")
                    val resolved = evalExpression(w.groupValues[3], index, path)
                    val key = "$path#$written"
                    if (!out.containsKey(key)) {
                        out[key] = HitSite(
                            path = path,
                            line = code.substring(0, start + w.range.first).count { it == '\n' } + 1,
                            modifier = w.groupValues[1].ifEmpty { w.groupValues[2] },
                            expr = written,
                            value = resolved.value,
                            terminalFile = resolved.terminalFile,
                            terminalName = resolved.terminalName,
                            viaOwnerTier = resolved.viaOwnerTier,
                            unresolved = resolved.value == null && !resolved.viaOwnerTier
                        )
                    }
                }
            }
        }
        return out.values.toList()
    }

    /**
     * 每格都要有的正证：下限那颗必须还在，读数从它现取（不抄在判据里）。
     * 读不到就抛——"扫不到东西"绝不能被读成"很干净"。
     */
    private fun requireFloorConstant(sources: List<Pair<String, String>>): Pair<String, Int> {
        val ownerPath = "core/designsystem/Dimens.kt"
        val owner = sources.firstOrNull { it.first == ownerPath }?.second
            ?: error("$ownerPath 不在了——下限那颗数没了主人，热区那三格全部失去意义")
        val floor = Regex("""\bTOUCH_TARGET_MIN_DP\s*=\s*(\d+)""").find(owner)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("$ownerPath 里读不到 TOUCH_TARGET_MIN_DP = <数>——这把尺已经看不见锚点了")
        return ownerPath to floor
    }

    /**
     * 热区那三格共用的读法：整棵 main 源码树，注释与字符串抹平、长度不变（行号可信）。
     * 与同文件其余格子用的 [codeOf] 是两套口径，别混：那一把会删注释（行号漂移、且不认字符串）。
     */
    private fun pageAndCoreSources(): List<Pair<String, String>> =
        kotlinFiles(appRoot).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to blanked(it.readText())
        }

    /**
     * 每一处**挂在可点链上的数**都要有名字、有主人；低于下限的那些还要逐颗登记。
     *
     * 这一格是来路判据，不是尺寸判据 —— 真实热区由 `SemanticsProbe` 那些语义树格子量。
     * 但它比旧写法多了三样牙，都是旧写法明说自己办不到的：
     *  - 数是从 `Dimens.kt` 现读的（抬下限时这一格跟着一起动，不靠人记得）；
     *  - 只看**真被写进 Modifier 链**的那个数（`val X = 48` 声明着没人用＝不算达标；
     *    链上写 `20.dp`＝当场红，旧写法对这两向都没牙）；
     *  - 每一颗低于下限的数都登记了**实到值 + 主人 + 为什么这一处还没并过去**，
     *    把 28 偷偷改成 20 会因为"登记值 ≠ 实到值"红，而不是靠数条目。
     *
     * 判据本身不看条数：登记表多一行少一行都只在"对不上现实"时红（新长一颗没人认领 ⇒ 红；
     * 并掉了没删行 ⇒ 红），数量只印在信息里当辅助读数。
     */
    @Test
    fun `every number on a clickable chain traces back to a design system owner`() {
        val sources = pageAndCoreSources()
        val (_, floor) = requireFloorConstant(sources)
        val index = indexConstants(sources)
        val sites = hitSites(sources, index).filterNot { it.path.startsWith("core/designsystem/") }

        // 仪器证人：这条链真的读得到来路（全仓至少一屏把热区指回全局那颗）
        assertTrue(
            "扫到 ${sites.size} 处挂在可点链上的数，其中指回 core 那颗的有 " +
                "${sites.count { it.terminalFile?.startsWith("core/designsystem/") == true }} 处" +
                "—— 两个数都是 0 说明这条链的读法坏了，不是代码干净",
            sites.size >= 20 && sites.any { it.terminalFile?.startsWith("core/designsystem/") == true }
        )

        val needsOwner = sites.filter { (it.value != null && it.value!! < floor) || it.unresolved }
        val registered = subFloorNumbers.associateBy { it.site }
        val unregistered = needsOwner.filter { registered[it.path + "#" + it.expr] == null }
        assertTrue(
            "这些写在可点链上的数低于全局下限 ${floor}dp（或解不出主人），却没登记主人：" +
                unregistered.joinToString { "${it.path}:${it.line} ${it.modifier}(${it.expr}) = ${it.value}" } +
                "。要么指回 core/designsystem 里那档具名主人，要么在 subFloorNumbers 里按实到登记" +
                "（写清主人是谁、为什么这一处还留在页面里）",
            unregistered.isEmpty()
        )
        // 数变了：登记值 ≠ 实到值（旧写法对这一条完全没牙——改数不影响"声明存在"）
        val drifted = needsOwner.mapNotNull { site ->
            val row = registered[site.path + "#" + site.expr] ?: return@mapNotNull null
            if (site.value != null && site.value!! != row.dp) site to row else null
        }
        assertTrue(
            "这些登记过的数与实到不符（改了数就要回来把登记改成实到，或把这一档指回主人）：" +
                drifted.joinToString { (site, row) -> "${site.path}:${site.line} ${site.expr} 实到 ${site.value}、登记 ${row.dp}" },
            drifted.isEmpty()
        )
        // 登记表不许比现实宽：行还在、数却已经不在链上了 ⇒ 债已还，删行
        val stale = subFloorNumbers.filter { row -> sites.none { it.path + "#" + it.expr == row.site } }
        assertTrue(
            "这些登记行已经对不上任何一处实到——并掉了就把这一行删掉，别留着当已有闸：" +
                stale.map { it.site },
            stale.isEmpty()
        )
        subFloorNumbers.forEach { row ->
            assertTrue(
                "${row.site} 的登记必须写清为什么这一处还留在页面里（why 不能空）",
                row.why.isNotBlank()
            )
            row.ownerTier?.let { tier ->
                assertTrue(
                    "${row.site} 指的主人 $tier 必须真是 core/designsystem 里的常量",
                    index.objects["AppDimens"]?.containsKey(tier) == true
                )
            }
            if (row.ownerTier == null) {
                assertTrue(
                    "${row.site} 没有可指的主人时，ownerNote 要说清缺的是哪一档（不许留空混过去）",
                    row.ownerNote.isNotBlank()
                )
            }
        }
        // 反向对照：仪器必须能看见一颗没人认领的矮档。对照件是现造的（不借生产文件的形状，
        // 免得哪天那一屏改了写法，这格从"有牙"悄悄退化成"恒真"）。
        val control = """
            @Composable
            fun ProbeHitBox(onClick: () -> Unit) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 20.dp)
                        .clickable(role = Role.Button, onClick = onClick)
                ) { Text("probe") }
            }
        """.trimIndent()
        val probeSites = hitSites(listOf("ui/probe/ProbeControl.kt" to control), index)
        assertTrue(
            "把一颗 20.dp 的热区注进仪器，这格必须看见它、并且它不在登记表里（看不见就等于整格恒绿）。" +
                "实到：${probeSites.joinToString { "${it.expr}=${it.value}" }}",
            probeSites.size == 1 && probeSites[0].value == 20 &&
                registered["ui/probe/ProbeControl.kt#20.dp"] == null
        )
    }

    /**
     * 每一屏的**热区档位来源**：要么指回全局那颗下限，要么自己说出"矮在哪一档"。
     *
     * 这一格替代的是原来那句 `aliasCount == 13`：数"有几处写成别名"这件事，任何改名、
     * 删组件、多一屏都会误红，而全屏改成 20dp 它照样绿。现在按**关系**判：
     * ① 一颗别名要能顺初始化链解到 `AppDimens.TOUCH_TARGET_MIN_DP`（"指回唯一那颗"）；
     * ② 这颗别名必须**真被人读**——含 `PanelDimens.X.dp` / `AppDimens.X.dp` 这种具名读法。
     *    旧写法按 `NAME\.` 认引用，把七条具名读法全判成"没人读"，那是尺瞎了不是债。
     *
     * ⚠ 这一格仍然测不到任何一处的真实尺寸：来路对了但数值写歪，由上一格（按链上实到值判）
     * 与语义树那些格子分别管。这一格只买"别长出第二颗下限、也别留一颗没人用的别名"。
     */
    @Test
    fun `every screen takes its touch tier from the one global floor and actually reads it`() {
        val sources = pageAndCoreSources()
        val (ownerPath, _) = requireFloorConstant(sources)
        val index = indexConstants(sources)
        val decl = Regex("""\b(?:const )?val\s+(\w+)\s*=\s*([^\n,]+)""")

        val aliases = mutableListOf<Triple<String, String, Int>>()
        sources.forEach { (path, code) ->
            if (path == ownerPath) return@forEach
            decl.findAll(code).forEach { m ->
                val resolved = resolveExpr(m.groupValues[2], path, 0, index, knownName = m.groupValues[2])
                if (resolved.terminalName == "TOUCH_TARGET_MIN_DP" &&
                    resolved.terminalFile == ownerPath &&
                    resolved.value != null
                ) {
                    val name = m.groupValues[1]
                    // "真被读"只认带 `.dp` 的读法：本文件的裸读、别处的 `某个对象.NAME.dp`、
                    // 以及跨文件 import 后的裸读，三种形状都在这一个模式里。
                    // 旧写法按 `NAME\.` 数，把具名读法全判成"没人读"（七条假红就是它造的）。
                    // ⚠ 已知松的一头：两颗**同名**别名会互相顶掉计数（数得出有人读，但分不清读的是哪一颗）；
                    //    按链上实到值判的那一格不看名字，补的正是这一头。
                    val reads = sources.sumOf { (_, c) ->
                        Regex("(?<![\\w])" + name + "\\.dp\\b").findAll(c).count()
                    }
                    aliases += Triple(path, name, reads)
                }
            }
        }
        val dead = aliases.filter { it.third == 0 }
        assertTrue(
            "这些别名声明了却没人读（那一屏真正用的数写在别处，改全局下限动不到它）：" +
                dead.joinToString { "${it.first}#${it.second}" },
            dead.isEmpty()
        )
        assertTrue(
            "只扫到 ${aliases.size} 处指回下限的别名 ⇒ 锚点或路径大概变了，这一格已经看不见东西：" +
                aliases.joinToString { "${it.first}#${it.second}" },
            aliases.size >= 8
        )
        // 反向对照：只声明不引用的那颗必须被这格看见（否则"没人读"这条判据就是恒绿的）
        val declarationOnly = "private const val ProbeFloorAlias = AppDimens.TOUCH_TARGET_MIN_DP"
        val probeReads = Regex("(?<![\\w])ProbeFloorAlias\\.dp\\b").findAll(declarationOnly).count()
        assertTrue(
            "对照物没就位：造一颗只声明、不引用的别名应当数到 0 处读法，实到 $probeReads" +
                "（数不到 0 就说明这一格的计数坏了，整格会恒绿）",
            probeReads == 0
        )
    }

    /**
     * 本轮合同明写的**矮档**：数值只许写在 core 那一颗，而且必须真被人读。
     *
     * 三条各管一件坏事：
     * ① 合同点名的四档，数值钉在 `Dimens.kt`（36 / 28 / 40 / 36）——数值写在别处或写歪都红；
     * ② `Dimens.kt` 里**每一颗**低于下限的常量都必须被 core 之外至少一处读到（新增一颗死档 ⇒ 红，
     *    这条按"有没有人读"判，不数颗数，也不列名单 ⇒ 上收新档不必改这里）；
     * ③ 页面直接读这些矮档而不走组件的，逐颗点名登记：说明它是谁的替身、该搬到哪。
     *    新增一屏自己抄矮档 ⇒ 这一条红（旧写法只问"有没有人读"，对这一向没牙）。
     */
    @Test
    fun `the compact tiers this round names are declared once and read somewhere`() {
        val sources = pageAndCoreSources()
        val (ownerPath, floor) = requireFloorConstant(sources)
        val owner = sources.first { it.first == ownerPath }.second
        val declared = Regex("""\b(?:const )?val\s+(\w+)\s*=\s*(\d+)""")
            .findAll(owner).associate { it.groupValues[1] to it.groupValues[2].toInt() }

        val contractTiers = mapOf(
            "INPUT_ROW_HEIGHT_DP" to 36,                    // 通用表单输入框外框
            "CARD_ACTION_HIT_DP" to 28,                     // 卡片/行内紧凑盒（合同：28 盒）
            "PANEL_PRIMARY_ACTION_HEIGHT_DP" to 40,         // 面板那一排主动作
            "PANEL_RESULT_ACTION_HEIGHT_DP" to 36           // 结果那一排并列双出口
        )
        contractTiers.forEach { (name, value) ->
            assertTrue(
                "$ownerPath 里必须把矮档 $name 写成字面量 $value，实到 ${declared[name]}" +
                    "（数只在主人这里写一次；页面不许抄第二份）",
                declared[name] == value
            )
            assertTrue("$name 有意低于全局下限 $floor，却不再低了就不该还叫矮档", declared.getValue(name) < floor)
        }

        // ② 每一颗低于下限的 core 常量都得有人读；这条不看名单也不看颗数
        val deadTiers = declared.filterValues { it < floor }.keys.filter { name ->
            sources.none { (path, code) ->
                path != ownerPath && Regex("(?<![\\w])$name\\b").containsMatchIn(code)
            }
        }
        assertTrue(
            "core/designsystem 里这些档没人读（$deadTiers）：要么真被某一颗组件用上，要么删掉——" +
                "留着一颗没人读的矮档就是给下一屏准备的抄数入口",
            deadTiers.isEmpty()
        )
        // 辅助读数：谁在读这几档（不据此判对错，只印出来给人看）
        val readers = contractTiers.keys.associateWith { name ->
            sources.filter { (path, code) ->
                path != ownerPath && Regex("(?<![\\w])$name\\b").containsMatchIn(code)
            }.map { it.first }
        }

        // ③ 页面直读矮档（不走组件的档）必须逐颗登记主人
        val pageReaders = readers.flatMap { (tier, files) ->
            files.filter { !it.startsWith("core/designsystem/") }.map { "$it#$tier" }
        }.toSet()
        val unregistered = pageReaders - compactTierPageReaders.keys
        assertTrue(
            "这些页面直接读了 core 的矮档却没登记它该由谁拥有（$unregistered）。" +
                "本轮合同允许紧凑档，但页面该读组件的具名档；实到读数：$readers",
            unregistered.isEmpty()
        )
        val stale = compactTierPageReaders.keys - pageReaders
        assertTrue(
            "这些登记已经对不上现实（页面不再直读那一档了）——搬完了就把行删掉，别留着当已有闸：$stale" +
                "；实到读数：$readers",
            stale.isEmpty()
        )
        compactTierPageReaders.forEach { (site, why) ->
            assertTrue("$site 的登记要说清它该搬去哪一个具名档（不能留空）", why.isNotBlank())
        }
    }

    /**
     * 挂在可点链上、又低于全局下限的每一颗数：逐颗登记实到值 + 主人 + 为什么还留在页面里。
     *
     * 这些就是本轮合同说的"可以紧凑，但语义与合理点击要留住"的具体落点：
     * 24dp 的齿轮/收起盒、30dp 的模式行、卡内 28dp 紧凑盒都**有意**低于 48dp，
     * 而它们现在仍住在页面自己的私有 object 里、没指回 core 那颗同名档 ⇒ 记在这里等收口，
     * 不是给它们发"合格"证。数值一改这表就对不上（红），新长一颗没登记的也红。
     */
    private data class SubFloorNumber(
        val site: String,      // "相对路径#链上原样写下的表达式"
        val dp: Int,           // 实到的数（登记时打印的就是它）
        val ownerTier: String?, // core 里已有主人的那颗名（没有就填 null）
        val ownerNote: String,  // 没主人时缺的是哪一档
        val why: String         // 为什么这一处本轮还留在页面里
    )

    private val subFloorNumbers = listOf(
        SubFloorNumber(
            "ui/home/ProviderSection.kt#ProviderDimens.FEATURE_ARROW_SIZE_DP.dp", 20, null,
            "缺档：行尾箭头字形（合同给的就是 20dp 箭头）",
            "装饰字形，不是热区：那一整行自己指回下限那颗"
        ),
        // 首页 Hero 那一排（HERO_ICON_SIZE_DP 16 / HERO_BUTTON_HEIGHT_DP 40）已从可点链上消失：
        // Agent F 把四入口卡并进 LbActionCard、Hero 主动作归 LbPrimaryButton 之后，HomeComponents
        // 里没有这两颗数写在链上了（实扫 0 处）。按本文件"并掉了就把这一行删掉"的规矩删行。
        // `ui/KnowledgeBaseActivity.kt#KbDimens.EDIT_ICON_SIZE_DP.dp`（14）这一行于 2026-10-06 L1b **删掉**：
        // 母版页那张卡的动作行改由 `core/designsystem/LbListCard.kt` 那一族动作件画，字形档由公共件读
        // `LbTextAction` 的具名档，页面里那颗 14dp 字形不在任何可点链上了（实扫 0 处）。
        // 按本文件"并掉了就把这一行删掉，别留着当已有闸"的规矩删行。
        SubFloorNumber(
            "ui/home/ProviderSection.kt#ProviderDimens.STATUS_DOT_SIZE_DP.dp", 6, null,
            "缺档：状态点直径",
            "点不是热区；那一行本身垫到下限"
        ),
        SubFloorNumber(
            "ui/panel/PanelHeader.kt#HeaderDimens.ROW_HEIGHT_DP.dp", 30, null,
            "缺档：悬浮窗模式栏整行高（合同 30dp）",
            "合同明写这一排 30dp；可点面积是每段 1/3 宽 × 整行高，比单独一颗胶囊大得多"
        ),
        SubFloorNumber(
            "ui/panel/PanelHeader.kt#HeaderDimens.CONTROL_HEIGHT_DP.dp", 20, null,
            "缺档：模式胶囊内高（合同 20dp）",
            "画在行里的胶囊，不是热区；上一颗整行高才是"
        ),
        SubFloorNumber(
            "ui/panel/PanelHeader.kt#HeaderDimens.SETTINGS_HOTZONE_DP.dp", 24, null,
            "缺档：悬浮窗工具条的外包盒（合同 24 容器 / 16 字形）",
            "这一颗确实是 24 见方的热区、低于下限：本轮按合同保留旧版摆法；它该由谁拥有还没定，给悬浮窗工具条立一档后并过去"
        ),
        SubFloorNumber(
            "ui/panel/PanelHeader.kt#HeaderDimens.COLLAPSE_HOTZONE_DP.dp", 24, null,
            "同上：收起那颗与齿轮同一档外包盒",
            "与齿轮同一课：24 见方低于下限，本轮按合同保留，登记等收口"
        ),
        SubFloorNumber(
            "ui/panel/PanelHeader.kt#HeaderDimens.GEAR_GLYPH_DP.dp", 16, null,
            "缺档：齿轮字形（合同 16dp）",
            "装饰字形；热区是上面那颗 24 的盒"
        ),
        SubFloorNumber(
            "ui/panel/counseling/CounselingLoadingSection.kt#CounselingCtaDimens.CTA_HEIGHT_DP.dp", 40, "PANEL_PRIMARY_ACTION_HEIGHT_DP",
            "",
            "谈心那一排的开始按钮与面板主动作同数同用途，主人已有；本轮拆块时先落在页面私有 object，改成引用那颗"
        ),
        // 下面三颗是持续意图编辑器的**启用开关**（44×24 轨道 + 20 圆钮）：这一族随文件
        // `SuggestPanel.kt`→`IntentEditorSheet.kt` 改名（见该文件头 KDoc，mv 移非删），
        // 数与用途一字未改，只是路径跟着换——不是新长一颗矮档。core 里没有开关这一档（缺 LbSwitch），
        // 与 FeedbackCasesScreen 那排自画开关同一处理：按实到登记、给开关立档。
        SubFloorNumber(
            "ui/panel/IntentEditorSheet.kt#44.dp", 44, null,
            "缺档：意图编辑器启用开关的宽（设计系统无 LbSwitch 这一颗）",
            "内联字面量本身就是第二颗数：本轮不动生产码，按实到登记，给它立档并改写成引用"
        ),
        SubFloorNumber(
            "ui/panel/IntentEditorSheet.kt#24.dp", 24, null,
            "缺档：同一颗启用开关轨道的高",
            "44×24 的可点盒低于下限，与上面那颗成一对，一并等立档"
        ),
        SubFloorNumber(
            "ui/panel/IntentEditorSheet.kt#20.dp", 20, null,
            "缺档：开关轨道里那枚圆钮的字形",
            "装饰字形，热区是那颗 44×24 的轨道盒"
        ),
        // `ui/home/HomeComponents.kt#HomeDimens.LAMP_VISIBLE_DP.dp`（12）这一行于 2026-10-06 **删掉**：
        // 那颗指示灯今天住在 `AdvisorLampDot`（`HomeComponents.kt:174-181`）——一条
        // `Modifier.size().clip().background().testTag()` 的**纯装饰链**，体里没有 clickable、没有角色，
        // 也不再带 contentDescription（同一个名字写在旁边那颗可见文字上）。
        // 这本账钉的是"可点链上的低于下限的数"，它不在这条链上了 ⇒ 按"对不上实就删行"处理；
        // 12dp 那颗数本身仍然由 `HomeDimens` 拥有，没有换成第二把尺，也没有换成内联字面量。
        SubFloorNumber(
            "ui/panel/reply/ReplyInput.kt#ReplyDimens.ROLE_CHIP_HEIGHT_DP.dp-8.dp", 20, null,
            "缺档：➕ 添加胶囊里的加号字形（角色 chip 28 视觉高减 8 得到的字形档）",
            "装饰字形，热区是那颗 28dp 的添加胶囊盒；这一颗只是把 chip 档减出一个图标尺寸"
        ),
        SubFloorNumber(
            "ui/panel/host/PanelSuggestionCards.kt#14.dp", 14, null,
            "缺档：建议卡里的指示字形",
            "装饰字形，不单独承担点击"
        ),
        SubFloorNumber(
            "ui/panel/reply/SchemeRewritingBlock.kt#12.dp", 12, null,
            "缺档：改写进行中的指示字形",
            "装饰字形；同一排的可点出口另有那颗 28 的盒"
        ),
        SubFloorNumber(
            "ui/panel/reply/SchemeAdjustingBlock.kt#SchemeCardDimens.ADJUST_PILL_HEIGHT_DP.dp", 28, "CARD_ACTION_HIT_DP",
            "",
            "卡内紧凑档在 core 已有主人（28），这一颗是页面私有 object 里的同名第二数"
        ),
        SubFloorNumber(
            "ui/panel/reply/SchemeCollapsedBlock.kt#SchemeCardDimens.ACTION_BOX_DP.dp", 28, "CARD_ACTION_HIT_DP",
            "",
            "主人已在 core 立住（LbTextAction 的 Compact 字形档就照它垫两轴）；页面这颗是第二数"
        ),
        SubFloorNumber(
            "ui/panel/reply/SchemeRewritingBlock.kt#SchemeCardDimens.ACTION_BOX_DP.dp", 28, "CARD_ACTION_HIT_DP",
            "",
            "与折叠态那一排共用同一颗页面私有 28，同样该指回 CARD_ACTION_HIT_DP"
        )
        // 锦囊进度条那颗 PROGRESS_HEIGHT_DP（6，线粗）已不在可点链上：`ui/panel/suggest/` 整个目录
        // 随锦囊结果页离场（main 源码树里 `SuggestDimens` 0 处引用），实扫 0 处。同样按"还了债就删行"
        // 处理，不留虚闸；它在 surfacesLedger 里那一行也一并删掉了。
    )

    /** 页面直读 core 矮档的落点：这一屏该改读哪一颗具名档（登记着等收口，不是给它发合格证） */
    private val compactTierPageReaders = mapOf(
        // `ui/common/CompactInput.kt#INPUT_ROW_HEIGHT_DP` 于 2026-10-06 **删掉这一行**：那一层已经
        // 整体搬进设计系统的新主人 `core/designsystem/LbFieldInput.kt`（五态可见框，36dp 档由它自己读），
        // 页面侧那半只剩调用；这正是这条登记当初写的"搬完它就成了主人、这条登记要跟着删"那一步。
        // `ui/home/ProviderSection.kt#INPUT_ROW_HEIGHT_DP` 同批删行：Key 显隐那颗尾部按钮今天住在
        // `LbFieldInput` 的尾部槽里（`ProviderSection.kt` 全文不再出现这颗档名，实扫 0 处），
        // "整行 48 / 中层 36"那两层写法的主人换成了 core，页面不再有直读点。
        // ⚠ 这两笔都是**换桶之外的事**：数还是那颗 36，只是不再由页面直读；
        //   两档的其余落点（KbEditActivity、ReplyInput 的默认形参）仍在下面这条账上。
        "ui/KbEditActivity.kt#INPUT_ROW_HEIGHT_DP" to
            "知识库编辑页那几格表单输入直读了档；该改读 CompactInput（或搬进 core 之后的那一颗）",
        "ui/panel/reply/ReplyInput.kt#INPUT_ROW_HEIGHT_DP" to
            "回复那一行的默认形参；合同给这一条的是 28 档、不是通用 36，两档别混：\u21d2 该由回复自己的具名档接手"
    )

    /**
     * "文字动作"在设计系统里只有一个所有者。
     *
     * 第6节第1条 那张表在 `LbEmptyState` 那一行就写了"可选文字动作；动作热区 ≥48dp"，
     * 但在这一格之前**这个词只在 `LbAsyncState.kt` 内部存在过**：页面想要一颗别的
     * 文字动作没地方放，于是首次引导自己画了一颗，本机量到 38x25dp。
     * 现在两处共用 `LbTextAction`，这一格看着别再分开长。
     *
     * ⚠ 这一格判不了"动作该不该在"：如果有人把 `LbEmptyState` 的动作整个删掉，
     * 这里会绿——那是 `LbAsyncStateTest` 那两格（"必须正好一个动作、点了必须真的执行一次"）
     * 的职责。两把尺各看一件事。
     *
     * ⚠ **尺寸下限那把读法换过一次（按层读，不按条数读）**：这一族现在写成**两层**——外层透明盒
     * 保证热区（`minHit`，两轴 ≥ 全局那颗），内层才是可见胶囊（`capsuleVisualMinHeight`，
     * ROW_ACTION_COMPACT 那一档版式高）。旧口径"`heightIn(min =` 恰好 1 处"把这两层读成了
     * "同一颗上叠了语义"⇒ 现在按层认锚点：外层与内层各 1 处、总数 2 处，第三条同形状才算叠语义；
     * "≥48"这一半由 `minHit` 的两个来路（size.hitSize / glyph.hitSize）与那颗别名常量钉住，
     * 不再靠数条目。可点挂点与角色仍是各 1 处（那两件事分不得第二层）。
     */
    @Test
    fun `the design-system text action has exactly one owner`() {
        val ownerPath = "core/designsystem/LbTextAction.kt"
        val hostPath = "core/designsystem/LbAsyncState.kt"
        val sources = kotlinFiles(dir("core", "designsystem")).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to codeOf(it.readText())
        }
        val owner = sources.firstOrNull { it.first == ownerPath }?.second
            ?: error("$ownerPath 不在了——文字动作又回到没人负责的状态")
        val host = sources.firstOrNull { it.first == hostPath }?.second
            ?: error("$hostPath 不在了——四态那格还在测它，先修这一格的说法")

        // 所有者这一侧：可点挂点与角色各只声明一次（多了就是同一颗上叠了两层语义）。
        listOf(
            Triple("clickable 挂点", Regex("""\.clickable\("""), 1),
            Triple("按钮角色", Regex("""\bRole\.Button\b"""), 1)
        ).forEach { (what, regex, budget) ->
            val got = regex.findAll(owner).count()
            assertTrue(
                "LbTextAction 里的「$what」应为 $budget 处，实到 $got 处" +
                    "（0 = 那颗盒子不再自己保证热区；>1 = 同一颗上叠了语义）",
                got == budget
            )
        }

        // 「热区下限」这一把按**两层**读（Agent I 的写法是对的，尺跟着改口径，生产码不动）：
        // 外层透明盒保证**热区**（minHit，两轴）、内层保证**可见形状**（capsuleVisualMinHeight，
        // 32 那一档版式高）。两层各写一次 `In(min =` 是设计，不是叠语义 ⇒ 判据不再是"数 1 处"，
        // 而是"恰好这两层，且每层指得出自己的锚点"：
        //   ① 层数：`heightIn(min =` 全文件恰好 2 处，外层那处绑 minHit、内层那处绑
        //      capsuleVisualMinHeight，各 1 处（0 = 那一层不再自己保证尺寸；>1 = 又长一层）；
        //   ② 外层 ≥48：minHit 只能由 size.hitSize / glyph.hitSize 交出，而那颗下限常量是
        //      AppDimens.TOUCH_TARGET_MIN_DP 的别名；胶囊档（唯一走两层的那一档）两条分支
        //      都交 LB_TEXT_ACTION_MIN_DP.dp——所以"两层里的外层"必然是全站下限；
        //   ③ 内层是 ROW_ACTION_COMPACT 那一档：capsuleVisualMinHeight 只许指
        //      AppDimens.ROW_ACTION_COMPACT_MIN_HEIGHT_DP.dp（Standard 交 null 即单层）。
        // 宽度轴与高度轴共用同一对参数（minHit / capsuleVisualMinHeight），所以 ①②③ 同样买住两轴。
        val (allMin, outerFloor, innerFloor) = hotZoneLayerCounts(owner)
        assertTrue(
            "LbTextAction 的尺寸下限应为「外层热区 + 内层视觉」两层各 1 处，" +
                "实到 `heightIn(min =` 共 $allMin 处（外层 minHit $outerFloor、内层 capsuleVisualMinHeight $innerFloor）" +
                "（某层 0 = 那一层不再自己保证尺寸；>1 = 同一颗上叠了语义）",
            allMin == 2 && outerFloor == 1 && innerFloor == 1
        )
        listOf(
            "外层热区绑的是热区参数 minHit" to (Regex("""\.heightIn\(min = minHit\)""") to 1),
            "内层视觉绑的是版式参数 capsuleVisualMinHeight" to
                (Regex("""\.heightIn\(min = capsuleVisualMinHeight\b""") to 1),
            "下限那颗是全局常量的别名（不另抄数）" to
                (Regex("""const val LB_TEXT_ACTION_MIN_DP\s*=\s*AppDimens\.TOUCH_TARGET_MIN_DP""") to 1),
            "外层两轴来自 size.hitSize" to (Regex("""minHit = size\.hitSize""") to 1),
            // 图标档有两支入口（ImageVector / painterResource），两支都只能把 minHit 交给
            // glyph.hitSize——所以这一条的额度是 2，不是 1：少了就是某一支不再交设计系统的档。
            "外层两轴来自 glyph.hitSize" to (Regex("""minHit = glyph\.hitSize""") to 2),
            "胶囊档的热区仍是全局下限" to
                (Regex("""LbTextActionSize\.RowCapsule\s*->\s*LB_TEXT_ACTION_MIN_DP\.dp""") to 1),
            "胶囊档的可见高度只占 ROW_ACTION_COMPACT 那一档" to
                (Regex("""LbTextActionSize\.RowCapsule\s*->\s*AppDimens\.ROW_ACTION_COMPACT_MIN_HEIGHT_DP\.dp""") to 1)
        ).forEach { (what, anchor) ->
            val (regex, budget) = anchor
            val got = regex.findAll(owner).count()
            assertTrue(
                "LbTextAction 的两层热区读不到锚点「$what」（实到 $got 处，应为 $budget 处）——" +
                    "外层不再 ≥48、或内层不再指 ROW_ACTION_COMPACT 那一档，都得先看清是哪一层改了主人",
                got == budget
            )
        }
        // 正向对照：这把两层尺必须**分得清单层与两层**（无底档就该只看见外层）。
        // 对照件现造，不借生产形状，免得哪天盒子改了写法这格悄悄退化成恒真。
        val singleLayer = """
            val hitZone = modifier
                .heightIn(min = minHit)
                .clickable(role = Role.Button, onClick = onClick)
            Box(modifier = hitZone) { content() }
        """.trimIndent()
        val twoLayer = singleLayer + "\n" +
            "Box(modifier = Modifier.background(capsuleColor).heightIn(min = capsuleVisualMinHeight ?: 0.dp)) { }"
        val singleLayerCounts = hotZoneLayerCounts(singleLayer)
        val twoLayerCounts = hotZoneLayerCounts(twoLayer)
        assertTrue(
            "两层尺自己得能分辨形状：合成单层应 (1,1,0)、实到 $singleLayerCounts；" +
                "合成两层应 (2,1,1)、实到 $twoLayerCounts（读成一样说明这把尺瞎了）",
            singleLayerCounts == Triple(1, 1, 0) && twoLayerCounts == Triple(2, 1, 1)
        )
        // 借用方这一侧：不许再自己画一颗
        Regex("""\.clickable\(""").findAll(host).count().let { got ->
            assertTrue(
                "LbEmptyState 又自己画了一颗动作（实到 $got 处）——它该 call LbTextAction，" +
                    "否则页级那颗和空态那颗会再次长成两样",
                got == 0
            )
        }
        // 「LbTextAction( 恰好一处」原来是防止有人把动作再抄一遍进容器分支。第 16 条给 LbEmptyState
        // 加了轻通知档（LbStateContainer.Notice），它的关闭那颗走 LbTextAction 的**图标档**、
        // Block/Strip 那颗仍走**文字档**——两支入口都住在 `StateAction` 这一个函数里、
        // 都指回设计系统那唯一一处文字/图标动作，不是第二个所有者。真正拦"自画"的是上面
        // 那条 `clickable == 0`；这里因此从 1 放宽到 2（文字 + 图标各一支），不是把闸拆了。
        Regex("""\bLbTextAction\(""").findAll(host).count().let { got ->
            assertTrue("LbEmptyState 的动作只许经 LbTextAction 画（文字 + 图标两支入口），实到 $got 处", got == 2)
        }
    }

    /**
     * 文字动作那一颗盒子的**尺寸下限层数**读数：(`In(min =` 总数, 外层热区那处, 内层视觉那处)。
     *
     * 为什么按层认而不是数总条数：这一族刻意写成两层（外层透明盒垫热区、内层才是可见胶囊），
     * 旧口径"恰好 1 处"把 Agent I 那两层合法写法读成了"同一颗上叠了语义"。现在外层与内层各有各的
     * 参数锚点（minHit / capsuleVisualMinHeight），第三条同形状的 `In(min =` 才会红——
     * 那正是"同一颗上又叠一层"的形状。宽度轴与高度轴共用同一对参数，所以这一读覆盖两轴。
     */
    private fun hotZoneLayerCounts(code: String): Triple<Int, Int, Int> = Triple(
        Regex("""heightIn\(min =""").findAll(code).count(),
        Regex("""\.heightIn\(min = minHit\)""").findAll(code).count(),
        Regex("""\.heightIn\(min = capsuleVisualMinHeight\b""").findAll(code).count()
    )

    /**
     * 一把账本共用的四条判据（表面色那把与可点控件那把只差在扫描口径）。
     *
     * 买的东西按**关系**排，条数只当读数：
     * ① 实扫到债的文件必须在表里（新长一处 = 有人绕开设计系统）；
     * ② 每家只许往下（`<=` 额度）——还了债不会误红；
     * ③ 表里每一行必须还在盘上、且**实扫至少还剩一处**（整档清零了就要删行，不留虚闸）；
     * ④ 每一行必须指得出主人：要么是真存在的设计系统组件（按声明扫，不许写一颗不存在的名字），
     *    要么明写缺件（owner 空着时 note 不许空）。
     */
    private fun assertBrandLedger(
        label: String,
        uiRoot: File,
        ledger: Map<String, BrandLedger>,
        found: Map<String, Int>,
        scanned: List<Pair<String, List<String>>>
    ) {
        val detail = scanned.joinToString { "${it.first}=${it.second}" }
        val grew = found.filter { (path, n) -> (ledger[path]?.ceiling ?: 0) < n }
        assertTrue(
            "$label：这些文件自画品牌底的数量涨了（要的是别再长新的）：$grew；实扫明细 $detail",
            grew.isEmpty()
        )
        val unregistered = found.keys.filter { !ledger.containsKey(it) }
        assertTrue(
            "$label：这些文件实扫到自画品牌底、表里却没有这一行——要么加行（并写出主人是谁），" +
                "要么把写法收进组件：$unregistered；实扫明细 $detail",
            unregistered.isEmpty()
        )
        ledger.forEach { (path, row) ->
            assertTrue("$label：$path 已不在 ui/ 下——这一行要一起删掉，别留着当已有闸",
                File(uiRoot, path).isFile)
            assertTrue("$label：$path 的额度不许填成 0（清零就是这一行该删了）", row.ceiling > 0)
            assertTrue(
                "$label：$path 已经一处都不自画了——债还完了就把这一行删掉，" +
                    "留一条不成立的豁免比没有豁免更坏；实扫明细 $detail",
                (found[path] ?: 0) > 0
            )
            assertTrue("$label：$path 必须写清为什么这一处还留在页面里（note 不许空）", row.note.isNotBlank())
            if (row.owner.isNotEmpty()) {
                assertTrue(
                    "$label：$path 指的主人 ${row.owner} 必须真在 core/designsystem 里声明" +
                        "（写一颗不存在的名字等于没主人）",
                    declarationsInDesignSystem().contains(row.owner)
                )
            }
        }
    }

    private fun declarationsInDesignSystem(): Set<String> =
        kotlinFiles(dir("core", "designsystem")).flatMap { file ->
            val code = codeOf(file.readText())
            listOf(
                Regex("""@Composable\s+(?:internal |private )?fun (\w+)\s*\("""),
                Regex("""\b(?:fun|class|object|interface|enum class|typealias)\s+(\w+)""")
            ).flatMap { it.findAll(code).map { m -> m.groupValues[1] } }
        }.toSet()

    /** 一笔自画品牌底的账：额度（本次实扫，只许往下）+ 主人 + 为什么这一处还留在页面里 */
    private data class BrandLedger(val ceiling: Int, val owner: String, val note: String)

    private val surfacesLedger: Map<String, BrandLedger> = mapOf(
        // "KnowledgeBaseActivity.kt" 那一行（原额度 1）于 2026-10-06 L1b **删掉**：库卡卡内那块品牌浅底
        // 随卡本体一起交回 `core/designsystem/LbListCard.kt`，本页 `.background(` 实扫 0 处。
        // 账本自己的规矩：债还完了就删行，留一条不成立的豁免比没有豁免更坏。
        "bubble/FloatingBubble.kt" to BrandLedger(1, "", "悬浮球球体底：设计系统里没有 overlay 悬浮球这一颗（缺件）"),
        "feedback/FeedbackCasesScreen.kt" to BrandLedger(1, "", "导出那排开关的轨道底色：没有开关这一颗（缺 LbSwitch）"),
        // "home/HomeComponents.kt" 那一行（原额度 1，"只剩军师控制条「停止」那颗实心方块的直涂 Primary 底
        // （AdvisorStopGlyph，热区盒自己无底）"）于 2026-10-06 **删掉**：那颗 ▶/■ 交回设计系统的
        // `LbTriangleGlyph`（H1c 全类三角审计那一格），实心方块的直涂 Primary 底随私有 Canvas 一起离场，
        // 本页 `.background(` 里已经没有品牌色（实到：`lamp.color` 那颗灯与一处非品牌底，都不在本把尺射程）。
        // 灯与播放/停止字形仍各有主人：`HomeDimens.LAMP_VISIBLE_DP` 与 `AppDimens.ARROW_SIZE_DP`。
        "home/ProviderSection.kt" to BrandLedger(3, "LbRowState", "模型行的选中态该走 LbRowState；第三处是 MiniSwitch 的轨道底（缺件）"),
        "kb/KbOnboardingWizard.kt" to BrandLedger(1, "", "建库向导的进度填充：LbAsyncState 没有进度条档（缺件）"),
        "onboarding/OnboardingOptionCard.kt" to BrandLedger(2, "LbChip", "单选选中态与 LbChip 的 Single 档同一语义；勾选点那一处也在这一行里"),
        "panel/AiLoadingRow.kt" to BrandLedger(1, "", "加载行的脉冲点底色，不是可点控件（缺件）"),
        "panel/LoveBrainPanelScreen.kt" to BrandLedger(1, "", "面板建议槽的表面浅底，整块形状尚无主人（缺件）"),
        "panel/OnboardingFlow.kt" to BrandLedger(1, "", "首次引导的浅底提示块：内容块，不冒充状态条（缺件）"),
        "panel/PanelHeader.kt" to BrandLedger(1, "LbChip", "模式栏选中那颗实心胶囊与 LbChip 的 filled 档同族；悬浮窗行高另有一格"),
        "panel/IntentEditorSheet.kt" to BrandLedger(1, "", "开关轨道的条件涂色（if 启用 Primary else SurfaceInset）：随 SuggestPanel→IntentEditorSheet 改名一路从旧账本平移过来，锦囊那颗实心主动作与空态浅底已随锦囊删除离场（各自另有落点），此处只剩这一颗开关底；设计系统没有 LbSwitch 这一颗（缺件）"),
        "panel/counseling/CounselingLoadingSection.kt" to BrandLedger(3, "LbPrimaryButton", "开始谈心那颗归 LbPrimaryButton；脉冲条与 PrimaryDark 叠层是动画表面（缺件）"),
        "panel/counseling/CounselingPanel.kt" to BrandLedger(2, "LbPrimaryButton", "发送那颗条件涂色归 LbPrimaryButton；另一处是阶段浅底"),
        "panel/host/PanelSuggestionCards.kt" to BrandLedger(2, "", "画像/阶段两张建议卡的浅底：带表态的卡尚无主人（缺件）"),
        "panel/reply/CorrectionCenter.kt" to BrandLedger(1, "", "纠正中心标签小块浅底，内容块装饰"),
        "panel/reply/MessageList.kt" to BrandLedger(2, "", "她/我左右气泡是本轮合同指定的语义色，气泡尚无主人（缺 LbBubbleRow）"),
        "panel/reply/ReplyInput.kt" to BrandLedger(1, "LbChip", "添加那颗胶囊底与三颗角色 chip 同族，角色 chip 已转 LbChip"),
        // ReplyPrimaryActions 那一行（原额度 1，`ReplyGenerateSlot` 自涂 `.background(Primary, …)`）
        // 已删：A18 把长按"本轮一次生成"那颗自画槽整个摘掉后，本页只剩 LbPrimaryButton 调用、
        // `.background(` 实扫 0 处。债还完了就删行，不留一条不成立的豁免。
        "panel/reply/ResultArea.kt" to BrandLedger(3, "LbChip", "筛选 Tab 选中态该走 LbChip；另两处是结果区浅底装饰"),
        "panel/reply/SchemeAdjustingBlock.kt" to BrandLedger(1, "LbChip", "卡内调整胶囊的选中底与 LbChip 同族")
        // panel/suggest/SuggestResultContent.kt 那一行（原额度 1）已删：锦囊结果页整个离场了
        // （`ui/panel/suggest/` 目录已不存在，`SuggestDimens` 在 main 源码树里 0 处引用；IntentEditorSheet
        // 那两行的登记里早就写着"锦囊那颗实心主动作已随页面删除离开"）。账本自己的规矩：路径不在
        // ui/ 下就删行——这一条原先被 ReplyPrimaryActions 那条红挡在后面（一格只报第一处失败），
        // 所以从没露出来过。
    )

    private val actionableLedger: Map<String, BrandLedger> = mapOf(
        "bubble/FloatingBubble.kt" to BrandLedger(1, "", "悬浮球本体可点：设计系统没有 overlay 悬浮球这一颗（缺件）"),
        "feedback/FeedbackCasesScreen.kt" to BrandLedger(1, "", "导出预览那一颗可点窄盒：尚无同类主人（缺件）"),
        // home/HomeComponents.kt 那一行（原额度 2）已删：Agent F 把首页四入口卡并进 LbActionCard、
        // Hero 主动作归 LbPrimaryButton 之后，本页 `clickable` 子树里涂品牌底的形状实扫 0 处——
        // 只剩军师控制条那一颗：热区盒自己无底，播放/停止字形是私有 composable 调用（Canvas 画三角 /
        // 一颗实心方块），这把按"clickable 那次调用的子树"读的尺看不见它。表面色那把尺仍然看得见
        // 方块那一处，所以它在 surfacesLedger 里留着 1 的额度。
        // 这条账本原来还押着一处未解矛盾（"页面 0 次调用 LbActionCard"），随归并完成一并作废。
        "home/ProviderSection.kt" to BrandLedger(2, "LbSettingRow", "模型行与添加行都是带点击的行，主人是 LbSettingRow"),
        // "KnowledgeBaseActivity.kt" 那一行（原额度 1，"可点的库卡归 LbActionCard，本轮还没转过去"）
        // 于 2026-10-06 L1b **删掉**：可点那张卡今天整颗走 `LbListCard`（点击由公共件挂角色与热区），
        // 页面 `clickable` 子树里涂品牌底的形状实扫 0 处——那句"本轮还没转过去"已经成立了，
        // 留着就是这条账目明文禁止的"不成立的豁免"。
        "panel/IntentEditorSheet.kt" to BrandLedger(1, "", "启用开关那颗可点盒（chain 上有 clickable、子树涂条件品牌底）：随 SuggestPanel→IntentEditorSheet 改名平移过来，锦囊那颗实心主动作已随页面删除离开。设计系统没有开关这一颗（缺 LbSwitch），与 FeedbackCasesScreen 那排自画开关同一处理"),
        "panel/counseling/CounselingLoadingSection.kt" to BrandLedger(2, "LbPrimaryButton", "脉冲条与开始按钮：开始那颗归 LbPrimaryButton"),
        "panel/counseling/CounselingPanel.kt" to BrandLedger(1, "LbPrimaryButton", "发送那颗条件涂色的可点控件归 LbPrimaryButton"),
        "panel/reply/MessageList.kt" to BrandLedger(1, "", "气泡行内的可点装饰块：气泡形状尚无主人（缺 LbBubbleRow）"),
        "panel/reply/ReplyInput.kt" to BrandLedger(1, "LbChip", "添加那颗可点胶囊与角色 chip 同族，角色 chip 已转 LbChip"),
        "panel/reply/ResultArea.kt" to BrandLedger(1, "LbChip", "筛选 Tab 那颗可胶囊化；本轮只剩这一处"),
        "panel/reply/SchemeAdjustingBlock.kt" to BrandLedger(1, "LbChip", "卡内调整胶囊的可点底与 LbChip 同族")
    )

    /**
     * 品牌色的**第三个锚点**：从 `containerColor` 那扇门涂进来的品牌色。
     *
     * 为什么单独一把尺：那份「禁止只在一个页面看起来不一样的按钮/卡片」的清单一直锚在
     * Modifier 链的 `.background(品牌色)` 上，
     * 而 Material 组件涂同层底走的是**具名实参**——
     * `ButtonDefaults.buttonColors(containerColor = Primary)`、
     * `CardDefaults.cardColors(containerColor = PrimaryLight)`。
     * 前一格把首页那颗唯一主按钮搬进 `LbPrimaryButton` 时才发现它从没进过清单：
     * 本机实扫 `Button(` 7 处、涂品牌色 5 处，**全在 `.background(` 那把尺的射程外**。
     * 搬掉首页那颗之后是 4 处 Button + 1 处 Card。
     *
     * ⚠ 与另两把尺一样，**这一格只买"别再长新的"**，它判不了任何一处该不该搬：
     * `containerColor = PrimaryLight` 里既有该走组件的主动作，也有状态卡那种
     * 本来就该是品牌浅底的表面。三把尺各扫各的（表面色 25 / 链上有 clickable 的 18 /
     * 这扇门的 5），**三个数别合成一个**——这正是坑表 ⑫ 那条的第三次复发。
     *
     * **口径在 `471a512` 换过一次（新旧数不可比）**：旧尺写的是
     * `containerColor\s*=\s*[^,)]*…`，那个字符类**在 `if (canProceed)` 的右括号处就断了**，
     * 于是 `containerColor = if (canProceed) Primary else SurfaceInset` 这种"条件涂色"
     * 从来没进过计数（本机两处：`KnowledgeBaseActivity:846`、`KbEditActivity:311`）。
     * ⇒ 历史的 5 / 4 / 3 全是**下界**；现在改成按括号配对取那一段实参（`brandArgumentAt`），
     * 并且下面加了一条**正向对照**证人：两档写法（直涂 / 条件）都得各扫得到——
     * 漏写法这种毛病不会自己喊人（坑表 88 那一族：按形状认的尺要能把形状的变体认全）。
     *
     * 判据按文件记、只许往下（`<=`）；表里的行必须还在盘上（不留"指向不存在文件"的额度）；
     * 扫到 0 说明锚点坏了。
     */
    @Test
    fun `brand tones painted through containerColor do not grow`() {
        val perFileBudget = mapOf(
            "KbEditActivity.kt" to 1                  // 只剩版本选中态那颗（条件涂色）——
                                                     // 「保存」已由 `f5d199d`/本轮归 `LbPrimaryButton`
        )
        // ⚠ **red 3（代码归零，不是尺瞎）**：登记原先是 2 处，另一处是 `home/HomeComponents.kt`
        //   状态卡那张 Card 的品牌浅底 `containerColor = PrimaryLight`。Agent F 把状态卡搬进
        //   `LbActionCard` 后它改念 `containerColor = SurfaceCard`（见 HomeComponents:99），
        //   直涂品牌底这一档在生产里**真的归零了**。按本文件反复立的规矩——"还了债就把表改小、
        //   不留虚闸"——这一行从表里删掉，登记总数 2 → 1。
        assertTrue("登记的就是本机实扫的 1 处，表本身错了要先修表", perFileBudget.values.sum() == 1)

        val uiRoot = dir("ui")
        val scanned = kotlinFiles(uiRoot).map {
            it.relativeTo(uiRoot).invariantSeparatorsPath to
                containerColorBrandHits(codeOf(it.readText()))
        }.filter { it.second.isNotEmpty() }
        val found = scanned.associate { it.first to it.second.size }

        // 正向对照：这把尺**必须认得两种写法**（直涂 / 条件）。
        // ⚠ 这条证人原先押在**生产实扫**上（"任一档变 0 都要停下来分辨"）。red 3 逼我们分辨完了：
        //   直涂那一档是**代码真归零**（首页状态卡搬进 LbActionCard 后改念 SurfaceCard），不是尺又瞎。
        //   那就不能再让证人依赖"生产恰好还留一颗直涂"——否则哪天连条件也搬完、整档清零，
        //   这把尺反而会误红（恒绿的反面：被代码变化误伤）。改成**现造合成对照**喂进同一把尺：
        //   只判"尺认不认得两种形状"，既挡得住"右括号断掉"那类漏读，也不会因为生产搬完而假红。
        //   生产那一档的实到涨跌，由上面的 perFileBudget 与下面的等号证人看着。
        val directControl = "Button(colors = ButtonDefaults.buttonColors(containerColor = Primary))"
        val conditionalControl =
            "Button(colors = ButtonDefaults.buttonColors(containerColor = if (canProceed) Primary else SurfaceInset))"
        val controlArgs = containerColorBrandHits(directControl) + containerColorBrandHits(conditionalControl)
        val controlConditional = controlArgs.count { it.trimStart().startsWith("if") || it.trimStart().startsWith("when") }
        val controlDirectCount = controlArgs.count { it.trimStart().startsWith("Primary") }
        assertTrue(
            "两档写法都得被这把尺各认到 1 处：合成直涂 $controlDirectCount、合成条件 $controlConditional。" +
                "任一档为 0 说明尺又漏读（旧口径正是断在 `if (…)` 的右括号），代码搬没搬都拦不住它读数",
            controlDirectCount == 1 && controlConditional == 1
        )

        val grew = found.filter { (path, n) -> (perFileBudget[path] ?: 0) < n }
        assertTrue(
            "这些文件从 containerColor 那扇门涂品牌色的写法涨了（ :490 要的是别再长新的）：$grew；" +
                "登记的是 ${perFileBudget.filterKeys { k -> (found[k] ?: 0) > 0 }}",
            grew.isEmpty()
        )
        perFileBudget.keys.forEach { path ->
            assertTrue("$path 已不在 ui/ 下——表里这一行要一起删掉，别留着当已有闸",
                File(uiRoot, path).isFile)
        }
        // ⚠ 上面那条 `<=` 只挡"长新的"，**挡不住表比现实宽**：搬掉一处之后实扫 4、
        // 表还写 5，`grew` 是空的、格子照绿。这条等号证人补上另一半——
        // 还了债就得回来把表改小（与字面量预算那句"还掉了就来把数字改小"同一个规矩）。
        // 它同时是"尺又被改瞎"的报警器：尺一漏数，等号当场不成立（探针 G5 就是钉这个的）。
        assertEquals(
            "登记总数与实扫不一致（表比现实宽 = 恒绿的另一种写法）：实扫 " + found,
            perFileBudget.values.sum(), found.values.sum()
        )
        found.keys.forEach { path ->
            assertTrue("$path 实扫到 ${found[path]} 处、表里却没有这一行——要么加行、要么把写法收进组件",
                perFileBudget.containsKey(path))
        }
        assertTrue("实扫到 ${found.values.sum()} 处，为 0 说明锚点失效（恒绿假闸）",
            found.values.sum() > 0)
    }

    /**
     * 从锚点往后取**那一段实参**，返回其中出现品牌色的实参——两把"按形状认"的尺共用这一把。
     *
     * 为什么不是一条正则搞定（这一条是 `471a512` 查出来的**真漏**）：
     * 旧尺写成 `[^)]*` / `[^,)]*`，那种"在 `if (…)` 的右括号处断掉"的写法它们**全都看不见**——
     * `.background(if (canProceed) Primary else SurfaceInset)` 里 `[^)]*` 走到 `if (canProceed)`
     * 那个右括号就停了，后面那句 `Primary` 永远接不上锚点。
     * 本机实扫（`_temp/scan_surface_ruler_check.py`）：
     * **表面色那把尺旧口径 25 处 / 12 文件，括号配对口径 45 处 / 17 文件**——
     * 少判 20 处（那是**当时那一盘**的实扫读数），其中 `ProviderSection`(4)、`ReplyInput`(2)、
     * `FeedbackCasesScreen`(2)、`OnboardingOptionCard`(2) 四个文件以前**整档不在表里**；
     * 余下 2 处挂在一颗已整删（）的面板上，宿主已不在盘上，故不再点名，20 这个历史总数保持不动。
     * ⇒ 历史那些"25 / 19 / 5 / 4"全是**下界**，新口径的数字与它们**不可比**。
     *
     * 口径：从锚点走到"顶层逗号"或"本段实参结束"为止（括号配对），中间的内层括号不拦。
     * 与字面量预算那把尺的 `expressionAt` 同一个思路，注释也一并剥掉（坑表 88）。
     */
    private fun brandTonedArgs(code: String, anchor: Regex, tones: Regex): List<String> {
        return anchor.findAll(code).map { m ->
            val from = m.range.last + 1
            var depth = 0
            var i = from
            while (i < code.length) {
                when (code[i]) {
                    '(' -> depth++
                    ')' -> if (depth == 0) break else depth--
                    ',' -> if (depth == 0) break
                }
                i++
            }
            code.substring(from, i)
        }.toList().filter { tones.containsMatchIn(it) }
    }

    private val brandTone = Regex("""\b(?:Primary|PrimaryDark|PrimaryLight|PrimarySubtle)\b""")
    private val brandToneNoSubtle = Regex("""\b(?:Primary|PrimaryDark|PrimaryLight)\b""")

    /**
     * 从 `containerColor =` 锚点往后取**那一段实参**，返回其中出现品牌色的实参。
     */
    private fun containerColorBrandHits(code: String): List<String> =
        brandTonedArgs(code, Regex("""containerColor\s*=\s*"""), brandTone)

    /**
     * 供应商表单：**测量走本体，外壳只留一颗浮层**。
     *
     * 这条不是 UI 事实的尺（那种一律读语义树），是一条**结构合同**：
     * `ProviderFormBody` 挂在 `ProviderEditDialog` 交给 `LbDialog` 的 `body=` 槽里，
     * 用户看到的仍然是一扇浮层；而本体自己**不许**再含 `Dialog(`——它一旦重新长出浮层，
     * `ProviderFormSemanticsTest` 就会撞上"Dialog 窗口 + 文本框永不空闲"那堵墙（账本 第45节第1条），
     * 整屏又会退回"一颗都量不到"的状态。这台机器量不了浮层窗口，所以这一半只能读结构，
     * 并且要说清楚：读结构证明的是"没被搬坏"，不是"长得对"。
     *
     * ⚠ 归并到 `LbDialog` 之后本轮的两个数都**换了口径**（1 → 0 与 0 → 2），
     * 那是还债不是放宽：原来"这一屏一扇浮层"量的是**自画**的 `Dialog(`，
     * 现在自画的归零了，尺改成"自画的必须为 0 **并且** 两扇浮层都走同一个所有者"——
     * 两头都判，比原来只数一头的旧口径更紧。
     */
    @Test
    fun `the provider form body stays measurable and the dialog stays its only host`() {
        val file = File(dir("ui"), "home/ProviderSection.kt")
        assertTrue("ProviderSection.kt 不在了——这一格会恒绿", file.isFile)
        val code = codeOf(file.readText())

        val bodyDecl = Regex("""internal fun ProviderFormBody\(""").findAll(code).count()
        assertEquals("表单本体必须是 internal 且只声明一次（测试要能直接挂它）", 1, bodyDecl)

        val hostCalls = Regex("""ProviderFormBody\(""").findAll(code).count()
        assertEquals("除声明外只许有一处调用（那一处必须在浮层的 body 槽里）", 2, hostCalls)

        val selfDrawnDialogs = Regex("""\bDialog\(\s*onDismissRequest""").findAll(code).count()
        assertEquals("归并后这一屏不再自己开窗（浮层归 LbDialog 所有）", 0, selfDrawnDialogs)
        val ownedDialogs = Regex("""LbDialog\(""").findAll(code).count()
        assertEquals(
            "这一屏两扇浮层：删除确认 + 表单，两扇都必须走同一个所有者（实到 $ownedDialogs）",
            2, ownedDialogs
        )

        // 本体自己那一段里不能再出现浮层开口：从声明起，到下一个顶层 @Composable 前
        val body = code.substringAfter("internal fun ProviderFormBody(")
            .substringBefore("\n@Composable")
        assertTrue(
            "表单本体里又长出了浮层，测量路径会被「不空闲」那堵墙重新封死：" +
                body.lines().filter { it.contains("Dialog(") },
            !body.contains("Dialog(")
        )
    }
}
