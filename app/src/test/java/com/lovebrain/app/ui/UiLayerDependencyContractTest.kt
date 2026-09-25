package com.lovebrain.app.ui

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
}
