package com.lovebrain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S2-05 职责分层的源码级合同（防"注释式修复"回归）。
 *
 * 复核报告 §6 S2-05 的原话是：Activity 只是新增了"这里违反 SRP/DIP、以后应改 ViewModel"的注释，
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
                    "$name 不应再直接引用 $type（S2-05：改走 ViewModel）",
                    code.contains(type)
                )
            }
            assertTrue("$name must obtain its ViewModel", code.contains("by viewModel()"))
        }
    }

    @Test
    fun `production sources carry no debt-note comments without a fix`() {
        // 报告点名的写法："S2-05 审计技术债：此处直接 inject Repository 违反 SRP/DIP"
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
     * §6.1：设计系统的组件必须**住在** core/designsystem，且旧名字不许回来。
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
            // §6.1 的 B 类第一行：两颗平行旋钮（mode + enabled）收成一颗状态
            "GenerationActionButton" to "LbPrimaryButton",
            "ButtonMode" to "LbButtonState",
            // §6.1 的 Sheet 半边：面板那套自画浮层归进设计系统。形状**必须**是自画的
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
     * §6.1 `LbModalSheet/Dialog`：浮层只有**一个所有者**。
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
        val exempt = mapOf(
            // 文件 -> 允许的裸 Dialog( 处数。供应商编辑器是一张完整表单（十几个字段 + 模型列表），
            // 属于 Sheet 那一类；§6.1 这行的 Sheet 半边还没做（见交接单 §4）——是欠账，不是漏网
            "ui/home/ProviderSection.kt" to 1
        )
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
     * §6.4 / §6.1：**整屏遮罩只有一个所有者**。
     *
     * 这条是被量出来的：生产里原先有三处各自画 `Color.Black.copy(alpha = …)` 的全屏遮罩
     * （`LbModalSheet` 自己、`RecordSentDialog`、`FeedbackCasesScreen` 的导出 Loading），
     * 后两处还各带一个 `clickable` 挂在整屏上——于是语义树里多出一颗"360x1000 的按钮"，
     * 其中一处把标题合并成了自己的名字（`SheetProbeTest` 留了改之前的实测原文）。
     *
     * "用同一颗组件却自造样式"这种坏法，§26 那把按声明处判的尺抓不到（它看的是谁**声明**了组件），
     * 所以要有一把盯着**形状本身**的闸：谁再想自己画一层遮罩，就在这里红。
     *
     * 判据取"画半透明黑底"这个具体写法而不是"有没有 fillMaxSize"：后者到处合法。
     */
    @Test
    fun `only the sheet owner draws a full window scrim`() {
        val owner = "core/designsystem/LbModalSheet.kt"
        val scrim = Regex("Color\\.Black\\.copy\\(\\s*alpha")
        val drawers = kotlinFiles(appRoot).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to codeOf(it.readText())
        }.filter { (path, code) ->
            scrim.containsMatchIn(code) && path != owner
        }.map { it.first }
        assertTrue(
            "整屏遮罩只许由 $owner 画；这些文件自己画了一层（改走 LbModalSheet，" +
                "它顺手修掉了遮罩冒充可点击节点的问题）：$drawers",
            drawers.isEmpty()
        )
        // 反向确认这把尺看得见东西：所有者自己那一份必须还在，否则就是正则坏了
        val ownerSource = File(appRoot, "core/designsystem/LbModalSheet.kt")
        assertTrue("找不到 $owner——被搬走或改名了，这把尺就成了一把扫空集的闸", ownerSource.isFile)
        assertTrue(
            "$owner 应当还在画遮罩（实到 0 处说明正则匹配不到任何写法，恒绿）",
            scrim.containsMatchIn(codeOf(ownerSource.readText()))
        )
    }

    /**
     * §6.4 :523：面板上的浮层必须各归**一个 state holder**，不许在屏幕函数里
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
                "新的浮层请开一个 holder（§6.4 :523），别在屏幕函数里加 showXxx",
            adHoc <= adHocBudget
        )
        assertTrue(
            "面板至少要认得出 $holderFloor 颗 holder，实到 $holders——" +
                "变小说明持有者被拆回内联状态，或者这条正则已经扫不到东西了",
            holders >= holderFloor
        )
    }

    /**
     * §6.1 表最后一行：`LbScreenScaffold` —— **页面外框只有一个所有者**。
     *
     * 判据取"谁画整屏底色"这一个具体写法（`background(SurfaceBase`），
     * 不取"有没有 fillMaxSize"——后者到处合法。理由与 §29/§30 那两把同源：
     * "用了同一个 token 却各页自己拼一层外框"这种坏法，按声明处判的尺抓不到。
     *
     * 名单是**本机实扫**出来的（去注释之后），分两类，别混：
     * - 所有者：`core/designsystem/LbScreenScaffold.kt`，1 处。
     * - **不是页面的那几个**（登记在这里是因为它们本就不该走页面外框）：
     *   `ui/home/SetupRoot.kt` 是路由宿主（它套着 600dp 限宽与 insets，
     *   目标页在它里面再走一次脚手架）、
     *   `ui/panel/LoveBrainPanelScreen.kt` 与 `ui/panel/SuggestPanel.kt`
     *   跑在 `TYPE_APPLICATION_OVERLAY` 窗口里，没有系统栏也不受页面边距档管。
     * - **欠账**：`ui/feedback/FeedbackCasesScreen.kt` 是一整页，今天还在自己画外框。
     *   它没一起搬走不是因为不重要，是因为它内部一堆区块自己带 `Spacing.lg` 的边距，
     *   直接套脚手架会变成"外面 24 里面又 12"叠两层——那一页要连着内部边距一起改，
     *   是独立的一格。**这里的计数只许往下**：搬走之后必须把这一行删掉，
     *   不许留着一条早已不成立的豁免当"管住了"。
     */
    @Test
    fun `the page frame has exactly one owner`() {
        val owner = "core/designsystem/LbScreenScaffold.kt"
        val notAPage = setOf(
            "ui/home/SetupRoot.kt",
            "ui/panel/LoveBrainPanelScreen.kt",
            "ui/panel/SuggestPanel.kt"
        )
        val registeredDebt = mapOf("ui/feedback/FeedbackCasesScreen.kt" to 1)

        val sources = kotlinFiles(appRoot).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to codeOf(it.readText())
        }
        val drawers = sources.filter { (_, code) -> code.contains("background(SurfaceBase") }
            .map { it.first }

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
            val got = Regex("background\\(SurfaceBase").findAll(code).count()
            // 判 ==，不判 <=：登记着 1 处而实到 0 处，意思是**这笔债已经还了**，
            // 表里那一行必须当场删掉。留一条早已不成立的豁免，比没有豁免更坏——
            // 它会让下一个人以为这一页已经处理过了（§29 那把"点名豁免"的尺同一课）。
            assertTrue(
                "$path 实到 $got 处，登记的是 $want。多了是新债；" +
                    "零了是已经还完——那就把这一行从表里删掉，别留着当已有闸",
                got == want
            )
        }
    }

    /**
     * §6.1 :479：页头只有一个所有者。
     *
     * 判据取"谁自己画返回那颗"这个具体形状：箭头字形 `"←"` 或 `KeyboardArrowLeft`。
     * 为什么不是数"有几个 Row"：`Row` 到处合法；而"自己拼一颗返回"必然要画那个字形，
     * 抓得住。与 §36 那把"整屏底色只有一个所有者"同族，也是被同一条理由逼出来的——
     * `PageHeaderConsistencyTest` 量到四式并存时，读屏名字有两派是**空串**。
     *
     * 本机实扫（去注释后）：所有者 1 处；**登记着的欠账 1 处**
     * （`ui/feedback/FeedbackCasesScreen.kt`）。那一页没顺手一起搬的三个理由写在这儿：
     * 它的栏还带一段"(N条)"计数与一条 `SurfaceCard` 底带，形状比"标题 + 一个尾部动作"多；
     * 而且这一页要 `rememberLauncherForActivityResult`，**JVM 上挂不起来**——
     * 搬一页却量不到搬的效果，等于自签。所以留在表里，判 `==`：
     * 还完之后这一行必须删，不许留着一条已不成立的豁免当"管住了"（同 §36 那条规矩）。
     */
    @Test
    fun `the page header has exactly one owner`() {
        val owner = "core/designsystem/LbTopBar.kt"
        val registeredDebt = mapOf("ui/feedback/FeedbackCasesScreen.kt" to 1)
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
     * §6.1 :490「禁止创建只在一个页面看起来不一样的按钮/卡片」——**只数趋势，不判对错**。
     *
     * 判据：`ui/` 下"在同一个 `.background(...)` 里出现品牌色"的出现次数，按文件记，
     * 每个文件**只许往下**。
     *
     * ⚠ 这把尺**证明不了任何一处"该搬"**，这一点必须写在代码里而不只在注释里：
     * 同样一串 `.background(Primary…)` 里，有页面主动作（该走 `LbPrimaryButton`）、
     * 有选中态 chip（就该是那个样子）、有"生成中/停止"这种带状态的切换、
     * 也有面板自身的表面色。谁算哪种是**逐处读语义**读出来的，那份判读在账本 §38
     * （连同本机量到的两处真缺陷）。
     * 所以这一格只买一样东西：**别再长新的**。热区/角色这类真性质
     * 由 `SemanticsProbe` 那些格子判（`OnboardingPrimaryActionTest` 就是这么量到
     * 38x25dp 与 312x34dp 的），不由这一格判。
     *
     * 本机实扫（`_temp/ratchet_counts.json`）：**25 处 / 12 个文件**。
     *
     * ⚠ 这把尺与"自造按钮清单"是**两把不同的尺，别混成一个数**：
     * - 本格这把数的是"品牌色涂在某个 `.background(...)` 里"，**不管它可不可点**——
     *   所以面板表面、加载条、行底色都算，实扫 **25 处 / 12 个文件**；
     * - `_temp/scan_primary_buttons.py` 那把要求**同一条 Modifier 链上有 `.clickable`**，
     *     才是"能按下去的自造按钮"，实扫 **19 处 / 8 个文件**（本格登记前是 20 处 / 9 个文件，
     *   首次引导那颗主按钮搬进 `LbPrimaryButton` 后各减一处）。
     * 两个数都对，只是量的不是同一件事——写成一个数就是假账。
     *
     * 上一格收首次引导那颗主按钮，就是从这两堆里各销掉的一处。
     */
    @Test
    fun `hand-drawn brand-toned surfaces do not grow`() {
        // ⚠ 这张表是**换过口径**重登记的（`471a512`）：旧口径 25 处 / 12 文件读不到
        // "条件涂色"那一档（见 `brandTonedArgs` 那段），实扫是 **45 处 / 17 文件**。
        // 这不是"债涨了 20 处"，是**以前漏了 20 处**——别拿 25 与 45 比涨跌。
        // 表里每家的额度 = 本次实扫，`<=` 只挡长新的，下面那条等号证人挡"表比现实宽"。
        val perFileBudget = mapOf(
            "bubble/FloatingBubble.kt" to 1,
            "KnowledgeBaseActivity.kt" to 2,
            "feedback/FeedbackCasesScreen.kt" to 2,
            "home/ProviderSection.kt" to 4,
            "onboarding/OnboardingOptionCard.kt" to 2,
            "panel/AiLoadingRow.kt" to 1,
            "panel/LoveBrainPanelScreen.kt" to 3,
            "panel/OnboardingFlow.kt" to 1,
            "panel/PanelHeader.kt" to 1,
            "panel/SuggestPanel.kt" to 8,
            "panel/counseling/CounselingPanel.kt" to 6,
            "panel/reply/CorrectionCenter.kt" to 1,
            "panel/reply/DislikeReasonPanel.kt" to 2,
            "panel/reply/MessageList.kt" to 2,
            "panel/reply/ReplyInput.kt" to 2,
            "panel/reply/ResultArea.kt" to 6,
            "panel/reply/SchemeCard.kt" to 1
        )
        val total = perFileBudget.values.sum()
        assertTrue("登记的就是本机实扫的 45 处，表本身错了要先修表", total == 45)

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

        val grew = found.filter { (path, n) -> (perFileBudget[path] ?: 0) < n }
        assertTrue(
            "这些文件里自己画品牌色底的数量涨了（§6.1 :490 要的是别再长新的）：$grew；" +
                "登记的是 ${perFileBudget.filterKeys { k -> (found[k] ?: 0) > 0 }}",
            grew.isEmpty()
        )
        // 反证之一：表里登记的每一项都要真在盘上，别留一条指向不存在文件的额度当"管住了"
        perFileBudget.keys.forEach { path ->
            assertTrue("$path 已不在 ui/ 下——表里这一行要一起删掉，别留着当已有闸",
                File(uiRoot, path).isFile)
        }
        // 反证之二：等号——还了债就得回来把表改小，否则"预算填松"就是另一种恒绿
        assertEquals(
            "登记总数与实扫不一致（表比现实宽 = 恒绿的另一种写法）：实扫 $found",
            total, found.values.sum()
        )
        found.keys.forEach { path ->
            assertTrue("$path 实扫到 ${found[path]} 处、表里却没有这一行——要么加行、要么把写法收进组件",
                perFileBudget.containsKey(path))
        }
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
     * 报告 S2-05 要的是"数据逻辑归 ViewModel"，如果 VM 活不过旋转，这句话就落不了地。
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
     * §6.5 :531 / 验收线 :596 那颗 48dp 下限——**这个数在仓库里只许写一次**。
     *
     * 为什么要买这一格：下限原先以 `const val … = 48` 的形式写了 17 遍
     * （本机对 HEAD 实扫，尺见 `_temp/measure_touch_floor_owners.py`：
     * DECL 17、内联 `48.dp` 8 处 / 4 个文件），另有 0 处写成"指回全局那颗"的别名。
     * 数写 17 遍等于**没有下限**：抬它要改 17 处，漏一处就只有那一屏的热区偷偷不达标，
     * 而 :596 那句"无小于 48dp 热区"是全站口径、不是逐组件口径。
     * 现在 15 处改成 `= AppDimens.TOUCH_TARGET_MIN_DP`（名字都留着，读调用方仍看得出
     * "这是哪一颗的下限"），2 处删掉，内联 8 处收进 token。
     *
     * ⚠ 这把尺**判不了任何一处尺寸对不对**，它只保证"改一次数就改全站"这件事成立。
     * 真热区由 `SemanticsProbe` 那些格子量（`LbTextActionTest`、`LbAsyncStateTest`、
     * `OnboardingPrimaryActionTest`），这一格只买"不许再长出第二个数"。
     *
     * 白名单里剩的都不是"下限"而是**恰好同数的版式尺寸**：
     * [AppDimens.TOUCH_TARGET_MIN_DP] 是唯一那颗下限；`EMPTY_ICON_CONTAINER_DP` 是
     * 空态图标方块，`LbActionCard` 那 1 处是卡片图标方块，`HomeComponents` 那 1 处是
     * Material `Button` 的行高，`ProviderSection` 那 1 处是 `MiniSwitch` 的宽
     * （**那一处是已知缺陷**：48x32 的开关高度不够下限，还写着"满足 48dp 下限"的注释——
     * 单独一格处理，还完把表里那行删掉）。
     */
    @Test
    fun `the touch-target floor is written as a number in exactly one place`() {
        val ownerPath = "core/designsystem/Dimens.kt"
        val allowedDeclarations = setOf(
            ownerPath to "TOUCH_TARGET_MIN_DP",      // 唯一那颗下限
            ownerPath to "EMPTY_ICON_CONTAINER_DP"   // 版式尺寸，恰好同数，不是下限
        )
        val allowedInlineLiterals = mapOf(
            "core/designsystem/LbActionCard.kt" to 1,
            // `ui/home/HomeComponents.kt` 那一行原来是 1（首页那颗 Material Button 写死
            // `.height(48.dp)`）。`91babe7` 之后它搬进了 `LbPrimaryButton`，数没了 ⇒
            // **这一行按下面那条 `==` 的规矩必须删掉**。留着不算错，但留着就等于
            // 承认"表可以比现实宽"——那正是坑表 71 那一族（预算填松 ⇒ 恒绿）。
            "ui/home/ProviderSection.kt" to 1        // ⚠ 已知缺陷，不是"允许这样"
        )
        val declaration = Regex("""\bval\s+(\w+)\s*=\s*48\b""")
        val inlineLiteral = Regex("""48\.dp""")
        val alias = Regex("""\bval\s+\w+\s*=\s*AppDimens\.TOUCH_TARGET_MIN_DP\b""")

        val sources = kotlinFiles(appRoot).map {
            it.relativeTo(appRoot).invariantSeparatorsPath to codeOf(it.readText())
        }
        val declarations = mutableListOf<Pair<String, String>>()
        val inlineCounts = mutableMapOf<String, Int>()
        var aliasCount = 0
        sources.forEach { (rel, code) ->
            declaration.findAll(code).forEach { declarations.add(rel to it.groupValues[1]) }
            val hits = inlineLiteral.findAll(code).count()
            if (hits > 0) inlineCounts[rel] = hits
            aliasCount += alias.findAll(code).count()
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
        // 反证之一：owner 必须在。扫不到就说明锚点或路径错了，整格会恒绿
        assertTrue(
            "$ownerPath 里必须还看得到 TOUCH_TARGET_MIN_DP = 48，扫不到说明这把尺已经瞎了",
            declarations.contains(ownerPath to "TOUCH_TARGET_MIN_DP")
        )
        // 反证之二：别名机制得真的有人走，否则白名单会退化成"只有一处 48"的假象。
        // 判 `==` 而不是 `>=`：14 是本机实扫（`_temp/measure_touch_floor_owners.py`）的数，
        // 少了就是有人又开始各自抄数（或把 token 改了名而尺没跟着改），多了是加了新的一颗
        // 下限——两种都该回来把这一行改掉，而不是让它默默地"还过得去"。
        assertTrue(
            "指回全局下限的别名登记 14 处，实到 $aliasCount 处",
            aliasCount == 14
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
     * "文字动作"在设计系统里只有一个所有者。
     *
     * §6.1 那张表在 `LbEmptyState` 那一行就写了"可选文字动作；动作热区 ≥48dp"，
     * 但在这一格之前**这个词只在 `LbAsyncState.kt` 内部存在过**：页面想要一颗别的
     * 文字动作没地方放，于是首次引导自己画了一颗，本机量到 38x25dp。
     * 现在两处共用 `LbTextAction`，这一格看着别再分开长。
     *
     * ⚠ 这一格判不了"动作该不该在"：如果有人把 `LbEmptyState` 的动作整个删掉，
     * 这里会绿——那是 `LbAsyncStateTest` 那两格（"必须正好一个动作、点了必须真的执行一次"）
     * 的职责。两把尺各看一件事。
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

        // 所有者这一侧：三件事各只声明一次（多了就是同一颗上叠了两层语义）
        listOf(
            Triple("clickable 挂点", Regex("""\.clickable\("""), 1),
            Triple("按钮角色", Regex("""\bRole\.Button\b"""), 1),
            Triple("热区下限", Regex("""heightIn\(min ="""), 1)
        ).forEach { (what, regex, budget) ->
            val got = regex.findAll(owner).count()
            assertTrue(
                "LbTextAction 里的「$what」应为 $budget 处，实到 $got 处" +
                    "（0 = 那颗盒子不再自己保证热区；>1 = 同一颗上叠了语义）",
                got == budget
            )
        }
        // 借用方这一侧：不许再自己画一颗
        Regex("""\.clickable\(""").findAll(host).count().let { got ->
            assertTrue(
                "LbEmptyState 又自己画了一颗动作（实到 $got 处）——它该 call LbTextAction，" +
                    "否则页级那颗和空态那颗会再次长成两样",
                got == 0
            )
        }
        Regex("""\bLbTextAction\(""").findAll(host).count().let { got ->
            assertTrue("LbEmptyState 必须经 LbTextAction 画动作，实到 $got 处", got == 1)
        }
    }

    /**
     * §6.1 :490 的**第三个锚点**：从 `containerColor` 那扇门涂进来的品牌色。
     *
     * 为什么单独一把尺：:490 那份清单一直锚在 Modifier 链的 `.background(品牌色)` 上，
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
            "home/HomeComponents.kt" to 1,            // 状态卡那张 Card 的品牌浅底
            "KbEditActivity.kt" to 1                  // 只剩版本选中态那颗（条件涂色）——
                                                     // 「保存」已由 `f5d199d`/本格归 `LbPrimaryButton`
        )
        // 登记总数由本次实扫定（`_temp/scan_container_color2.py`，剥注释 + 括号配对）。
        // ⚠ **口径在 `471a512` 换过**（旧尺读不到"条件涂色"那一档，见 `brandTonedArgs`），
        //   换口径之后一路：5 → 3（表单「保存」）→ 2（问卷「下一步」「完成」）→ **2**
        //   （`KbEditScreen` 那颗「保存」也归位；`KnowledgeBaseActivity` 整档不在表里）。
        //   别把 3 与历史的 5/4 放在一起比涨跌——那三个数都是**旧口径**的下界。
        assertTrue("登记的就是本机实扫的 2 处，表本身错了要先修表", perFileBudget.values.sum() == 2)

        val uiRoot = dir("ui")
        val scanned = kotlinFiles(uiRoot).map {
            it.relativeTo(uiRoot).invariantSeparatorsPath to
                containerColorBrandHits(codeOf(it.readText()))
        }.filter { it.second.isNotEmpty() }
        val found = scanned.associate { it.first to it.second.size }

        // 正向对照：这把尺**必须认得两种写法**。哪天其中一档扫不到，要么代码真没了
        // （那要回来改这张表和这条证人），要么尺又漏了——两种都不能闷着绿。
        val allArgs = scanned.flatMap { it.second }
        val conditional = allArgs.filter { it.trimStart().startsWith("if") || it.trimStart().startsWith("when") }
        val direct = allArgs.filter { it.trimStart().startsWith("Primary") }
        assertTrue(
            "条件涂色那一档扫到 ${conditional.size} 处、直涂 ${direct.size} 处。" +
                "这把尺上一次就是瞎在条件涂色上（旧正则到 `if (…)` 的右括号就断），" +
                "任何一档变成 0 都要停下来分辨是代码没了还是尺又漏了：$allArgs",
            conditional.isNotEmpty() && direct.isNotEmpty()
        )

        val grew = found.filter { (path, n) -> (perFileBudget[path] ?: 0) < n }
        assertTrue(
            "这些文件从 containerColor 那扇门涂品牌色的写法涨了（§6.1 :490 要的是别再长新的）：$grew；" +
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
     * 少判 20 处，其中 `ProviderSection`(4)、`DislikeReasonPanel`(2)、`ReplyInput`(2)、
     * `FeedbackCasesScreen`(2)、`OnboardingOptionCard`(2) 五个文件以前**整档不在表里**。
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
     * 供应商表单：**测量走本体，外壳只留一颗 Dialog**。
     *
     * 这条不是 UI 事实的尺（那种一律读语义树），是一条**结构合同**：
     * `ProviderFormBody` 被 `ProviderEditDialog` 那颗 `Dialog` 包着，用户看到的仍然是一扇浮层；
     * 而本体自己**不许**再含 `Dialog(`——它一旦重新长出浮层，
     * `ProviderFormSemanticsTest` 就会撞上"Dialog 窗口 + 文本框永不空闲"那堵墙（账本 §45.1），
     * 整屏又会退回"一颗都量不到"的状态。这台机器量不了浮层窗口，所以这一半只能读结构，
     * 并且要说清楚：读结构证明的是"没被搬坏"，不是"长得对"。
     */
    @Test
    fun `the provider form body stays measurable and the dialog stays its only host`() {
        val file = File(dir("ui"), "home/ProviderSection.kt")
        assertTrue("ProviderSection.kt 不在了——这一格会恒绿", file.isFile)
        val code = codeOf(file.readText())

        val bodyDecl = Regex("""internal fun ProviderFormBody\(""").findAll(code).count()
        assertEquals("表单本体必须是 internal 且只声明一次（测试要能直接挂它）", 1, bodyDecl)

        val hostCalls = Regex("""ProviderFormBody\(""").findAll(code).count()
        assertEquals("除声明外只许有一处调用（那一处必须在 Dialog 里面）", 2, hostCalls)

        val dialogOpens = Regex("""Dialog\(\s*onDismissRequest""").findAll(code).count()
        assertEquals("这一屏只许剩一扇浮层（表单本体里不该再嵌 Dialog）", 1, dialogOpens)

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
