package com.lovebrain.app.architecture

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 包依赖方向门禁（ 第5节第1条「先做包边界，不急着一次多模块化」、
 * 第7节 第二步第 4 条「建 package dependency test，禁止 UI import data、domain import Android」，
 * 以及 第4节 表里 DIP/迪米特那两行的 FAIL）。
 *
 * 为什么不是"现在就全绿"：今天的真实存量是 **5 条越界 import，分布在 5 个文件**
 * （本轮 `SecurePrefs` / `KbArchiveTransfer` 端口化后，viewmodel 少掉一处 `java.io.File`：
 * `2dd94c0` 15→10、`ab7457a` 10→6、本轮 6→5；下面那份 baseline 登记 5 条 / 5 个键；
 * `assertEquals(5, …)` 那格每次都在核它）。
 * ⚠ 这一段原来写的是"6 条 / 6 个文件"——还债时顺手把存量叙述也扫了，别再留"注释比实现旧"的假账。
 * 直接把规则写成硬门禁会让 CI 永久红，然后被人用 `|| true` 关掉——
 * 复核 第9节 第 6 条禁止的正是这个，那比没有门禁更糟。
 *
 * 所以这里是棘轮：
 * 1. 新增任何一条越界 import 立即失败，失败信息点名文件、import 与它撞了哪条前缀；
 * 2. 基线登记的条目如果已经不在，也失败，红的内容是"债务降了，把清单改短"——
 *    没有这一半，基线会随着重构悄悄腐烂，最后没人知道还剩多少；
 * 3. 条目总数也比一次，防止"文件登记对了但漏了第二条"。
 *
 * 还债顺序（复核 第7节 第二步第 1 条）：先给这些具体类建端口（AiGateway /
 * KnowledgeReadPort / KnowledgeWritePort）。**原计划第一站"把 domain 的 6 处 `data.*` 换掉"已经做完了**
 * （现在 domain 只剩 `PromptBuilder` 一处 `android.content.Context`），
 * 剩下的 5 条按上面那份登记走：model 撞 domain 两处、ui 撞 data 一处、viewmodel 撞 `java.io.File` 一处
 * （KnowledgeBaseViewModel 那处 `java.io.File` 本轮随 `SecurePrefs` / `KbArchiveTransfer` 端口化还掉了）。
 * 每换掉一处，就重跑 report 脚本、把基线改小。
 */
class PackageDependencyTest {

    /** 包 -> 不允许出现在 import 里的前缀 */
    private val forbidden: Map<String, List<String>> = mapOf(
        "model" to listOf("android.", "androidx.", "com.lovebrain.app.data.", "com.lovebrain.app.domain."),
        "domain" to listOf(
            "android.", "androidx.", "com.lovebrain.app.ui.", "com.lovebrain.app.viewmodel.",
            "com.lovebrain.app.data."
        ),
        "ui" to listOf("com.lovebrain.app.data.", "java.io.File"),
        // feature/* 是 第5节第2条 搬出来的状态持有者：它只能碰 model 与 domain.port，
        // 反过来依赖 VM、UI 或 data 的话，"store 是纯状态所有者"这条就又是口号。
        // 现在 0 违规，所以这条规则没有基线可躲。
        "feature" to listOf(
            "android.", "androidx.", "com.lovebrain.app.data.",
            "com.lovebrain.app.ui.", "com.lovebrain.app.viewmodel."
        ),
        "viewmodel" to listOf(
            "java.io.File",
            // 知识库仓库是**具体实现类**：ViewModel 按它的类型注入，就等于页面层直接认了数据层，
            // 端口那一层（domain/port）在页面上完全不生效。这条以前不在尺的视野里——
            // viewmodel 只禁 java.io.File，所以三个 VM 一直按 `KnowledgeRepository` 注入也没人红。
            // 现在三个 VM 都改成了端口视图，这条就是 0 违例的硬门禁（不需要基线，见上面 feature 那格的规矩）。
            "com.lovebrain.app.data.KnowledgeRepository",
            // 加密偏好与归档传输也各自端口化后（[com.lovebrain.app.domain.port.SettingsStorePort] /
            // [com.lovebrain.app.domain.port.KbArchivePort]），这两颗具体类同样不该再被页面 import。
            // 与仓库那条同口径：端口化完成后就是 0 违例的硬门禁，任何一处回潮当场变红。
            "com.lovebrain.app.data.SecurePrefs",
            "com.lovebrain.app.data.KbArchiveTransfer"
        ),
        // 第5节第1条 第一层：core 不知道数据层、容器与 Android 侧的具体东西，否则"设计系统"
        // 就变成另一坨业务代码的附属品。
        // 这条以前只禁到 data/viewmodel/feature，因为 token 还住在 ui.theme 下面——
        // `core.designsystem.LbAsyncState` 当时必须 import `ui.theme.*` 才拿得到 Spacing/颜色，
        // 那条欠账是登记着过的（不是漏的）。第三步-1 把 token 整体迁进 core/designsystem 之后，
        // "core 反过来依赖 ui"就没有借口了，所以这里把整条 `com.lovebrain.app.ui.` 前缀禁掉。
        "core" to listOf(
            "android.", "java.io.File", "org.koin.",
            "com.lovebrain.app.data.", "com.lovebrain.app.viewmodel.", "com.lovebrain.app.feature.",
            "com.lovebrain.app.ui."
        )
    )

    /**
     * 存量债务的逐条登记。来源：bash scripts/package_deps_report.sh（2026-09-24 实扫）。
     * 只想缩，不想长；要新增必须先在这里写清"为什么这次不得不过界"。
     */
    private val baseline: Map<String, List<String>> = mapOf(
        "domain/PromptBuilder.kt" to listOf("android.content.Context"),
        "model/ProfileUpdate.kt" to listOf("com.lovebrain.app.domain.StageCatalog"),
        "model/ProfileUpdateSchema.kt" to listOf("com.lovebrain.app.domain.StageCatalog"),
        "ui/SetupActivity.kt" to listOf("com.lovebrain.app.data.EventBus"),
        // viewmodel/KnowledgeBaseViewModel.kt 的 `java.io.File` 已随归档端口化还掉：
        // 归档 IO 挪进 data/FileKbArchiveTransfer，页面只交出流，不再自己拼路径。
        "viewmodel/SetupViewModel.kt" to listOf("java.io.File")
    )

    private fun mainRoot(): File {
        val root = File("src/main/java/com/lovebrain/app")
        assertTrue("必须在 :app 模块下跑，找不到 $root", root.isDirectory)
        return root
    }

    /** 实扫：文件相对路径 -> 它撞了规则的 import（带撞的前缀，方便看错误） */
    private fun violations(root: File = mainRoot()): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        for ((pkg, prefixes) in forbidden) {
            val dir = File(root, pkg)
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val rel = "$pkg/" + f.relativeTo(dir).path.replace(File.separatorChar, '/')
                val hit = f.readLines().mapNotNull { line ->
                    val name = line.trim().takeIf { it.startsWith("import ") }
                        ?.removePrefix("import ")?.trim()?.substringBefore(' ') ?: return@mapNotNull null
                    val prefix = prefixes.firstOrNull { name.startsWith(it) } ?: return@mapNotNull null
                    name to prefix
                }
                if (hit.isNotEmpty()) out.getOrPut(rel) { mutableListOf() }.addAll(
                    hit.map { (name, prefix) -> "$name  (撞前缀 $prefix)" }
                )
            }
        }
        return out
    }

    /** 扫描器自己也要能红：接错目录时会"零违规通过"，那是最坏的一种绿 */
    @Test
    fun `the scanner actually sees imports`() {
        val filesWithImports = mainRoot().walkTopDown().count {
            it.isFile && it.extension == "kt" && it.readLines().any { l -> l.trim().startsWith("import ") }
        }
        assertTrue("src/main 下扫到 0 个带 import 的文件——路径接错了", filesWithImports > 50)
    }

    @Test
    fun `no new package dependency violation appears`() {
        val grew = StringBuilder()
        for ((file, imports) in violations()) {
            val allowed = baseline[file].orEmpty()
            for (i in imports) {
                val bare = i.substringBefore("  (")
                if (bare !in allowed) grew.append("\n  $file -> $i")
            }
        }
        assertTrue(
            "出现了新的跨层依赖。要么改成走端口/意图（复核  的 DIP 目标），" +
                "要么在这里登记并写清为什么这次不得不过界：$grew",
            grew.isEmpty()
        )
    }

    /**
     * 反向半：基线里登记的条目如果已经不存在，也要红——
     * 红的内容是"债务降了，把清单改短"。
     */
    @Test
    fun `the recorded debt baseline still matches reality`() {
        val now = violations()
        val stale = StringBuilder()
        for ((file, imports) in baseline) {
            val present = now[file].orEmpty().map { it.substringBefore("  (") }
            for (i in imports) if (i !in present) stale.append("\n  $file -> $i 已经不存在了")
        }
        val unlisted = now.keys.sorted().filter { it !in baseline }
        assertTrue(
            "基线必须如实反映存量。" +
                (if (stale.isEmpty()) "" else "\n这些越界已被修掉，请把它们从基线删掉（债务在降，是好事）：$stale") +
                (if (unlisted.isEmpty()) "" else "\n这些文件有越界但没登记：$unlisted"),
            stale.isEmpty() && unlisted.isEmpty()
        )
    }

    /** 总数也比一次：防"文件登记对了但漏了其中一条" */
    @Test
    fun `the total count of violations matches the recorded baseline`() {
        val nowCount = violations().values.sumOf { it.size }
        val baseCount = baseline.values.sumOf { it.size }
        assertEquals(
            "实扫越界条数与登记不一致（实扫 $nowCount，登记 $baseCount）。" +
                "重跑 bash scripts/package_deps_report.sh 取真值",
            baseCount, nowCount
        )
    }

    /** 规则本身不许被清空来"通过" */
    @Test
    fun `the rules are not silently emptied`() {
        assertTrue("model 层必须禁止 android", forbidden.getValue("model").contains("android."))
        assertTrue("domain 层必须禁止 android", forbidden.getValue("domain").contains("android."))
        assertTrue("domain 层必须禁止具体 data 仓库", forbidden.getValue("domain").contains("com.lovebrain.app.data."))
        assertTrue("ui 层必须禁止直接 import data 仓库", forbidden.getValue("ui").contains("com.lovebrain.app.data."))
        assertTrue("viewmodel 不许自己拼文件路径", forbidden.getValue("viewmodel").contains("java.io.File"))
        assertEquals("基线条目数必须与 report 脚本同一次统计一致", 5, baseline.values.sumOf { it.size })
    }

    /**
     * 上一条只扫 `import` 行，于是有一条便宜的绕法：把类型写成全限定名
     * `private val repo: com.lovebrain.app.data.KnowledgeRepository` 就不用 import 了。
     * 这条按"代码里出没出现这个类型名"判，注释先剥掉——
     * KDoc 里指名道姓解释"为什么这里不用具体仓库"是好事，不该被当成依赖。
     *
     * 与棘轮的分工：上面那条走 baseline（可以登记存量、只许缩），
     * 这条**没有基线可登记**，因为它今天就是 0；出现任何一处都是新账。
     */
    @Test
    fun `view model layer never names the concrete knowledge repository type`() {
        // 规则本身不许被删掉来"通过"（与 `the rules are not silently emptied` 同一口径，
        // 只是那条是既有断言、不该被后来的改动续写，所以这条新违例的自证留在这里）
        assertTrue(
            "viewmodel 的禁 import 前缀里必须有具体仓库类型",
            forbidden.getValue("viewmodel").contains("com.lovebrain.app.data.KnowledgeRepository")
        )
        val dir = File(mainRoot(), "viewmodel")
        assertTrue("找不到 $dir —— 尺接错了目录会'零违例通过'", dir.isDirectory)
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("viewmodel 下扫到 0 个文件，这条等于没跑", files.isNotEmpty())

        fun offendingCode(src: String) = stripComments(src).contains("KnowledgeRepository")
        val offenders = files.filter { offendingCode(it.readText(Charsets.UTF_8)) }.map { it.name }
        assertTrue(
            "viewmodel 层不许出现具体仓库类型 KnowledgeRepository（改注入 domain.port 的端口；" +
                "端口视图在 di/AppModule.kt 里指回同一个仓库实例）。违规：\n$offenders",
            offenders.isEmpty()
        )

        // 尺自证：同一段判据必须咬得住"全限定名注入"，也不能咬注释与端口注入
        assertTrue("尺没抓到全限定名注入", offendingCode("class V { val r: com.lovebrain.app.data.KnowledgeRepository = x }"))
        assertTrue(
            "抓到注释里的类名了——KDoc 允许解释为什么不用它",
            !offendingCode("/** 不用 KnowledgeRepository 因为要收窄 */\nclass V { val r: KnowledgeRuntimePort = x }")
        )
        assertTrue("端口注入被误报", !offendingCode("import com.lovebrain.app.domain.port.KnowledgeRuntimePort\nclass V"))
    }

    /** 剥掉块注释与行注释（块注释允许跨行；未闭合的块注释吃到文件尾） */
    private fun stripComments(src: String): String = buildString {
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2)
                    i = if (end >= 0) end + 2 else src.length
                }

                src.startsWith("//", i) -> {
                    val end = src.indexOf('\n', i)
                    i = if (end >= 0) end else src.length
                }

                else -> {
                    append(src[i]); i++
                }
            }
        }
    }

    /**
     * 反向证明：规则不是摆设。
     *
     * 在**临时目录**里造一棵和 src/main 同布局的小树，塞一条越界 import 进去，
     * 要求扫描器抓到；再造一条合规的，要求它不误报。
     * 不去改真实源文件——测试进程万一被中途 kill，改过的生产文件就留在树里了。
     */
    @Test
    fun `a freshly introduced violation is caught and a legal import is not`() {
        val tmp = java.nio.file.Files.createTempDirectory("pkgdep").toFile()
        try {
            val domainDir = File(tmp, "domain").apply { mkdirs() }
            File(domainDir, "Bad.kt").writeText(
                "package com.lovebrain.app.domain\n" +
                    "import com.lovebrain.app.data.KnowledgeRepository\n" +
                    "class Bad\n",
                Charsets.UTF_8
            )
            File(domainDir, "Good.kt").writeText(
                "package com.lovebrain.app.domain\n" +
                    "import com.lovebrain.app.model.ChatMessage\n" +
                    "class Good\n",
                Charsets.UTF_8
            )
            val found = violations(tmp)
            val bad = found["domain/Bad.kt"].orEmpty()
            assertTrue(
                "注入了越界 import 却没抓到，规则是摆设：$found",
                bad.any { it.substringBefore("  (") == "com.lovebrain.app.data.KnowledgeRepository" }
            )
            assertTrue("合规 import 被误报：${found["domain/Good.kt"]}", !found.containsKey("domain/Good.kt"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ═════════════ 第4节 DIP 的装配侧与入口侧：`di/` 与 `service/`（工单 W-E）═════════════

    /**
     * 这两个包以前不在任何尺的射程里：上面那份 [forbidden] 只管 model/domain/ui/feature/
     * viewmodel/core，`scripts/package_deps_report.sh`（它自己那份 FORBIDDEN 与本文件逐条对齐，
     * 所以 `--count` 印的就是同一批）也照样看不见它们。
     *
     * **为什么不并进 [forbidden]**：`--count` 那个数是台账（现登 5，
     * `the rules are not silently emptied` 里那条 `assertEquals(5, …)` 钉着它）。口径一换、
     * 新旧数就不可比——`UiLayerDependencyContractTest` 那把「表面色」尺换口径时留的就是这块告示。
     * 所以这一格自带两本登记加自己的等号证人，`--count` 一个字节都不动；
     * 要把这两包并进台账那把尺，是「改脚本 + 重登数」那条线的事，不在本档顺手做。
     *
     * **口径（写成能判的样子，不是「DI 例外」）**：一条跨层引用算必需还是越界，
     * 按 `(谁引用, 引用了哪一层)` 查表判（[entryCategory]），不按「我是 DI / 我是 Service」
     * 这种身份判：
     *
     * - `di -> data./viewmodel.` ＝ **装配必需**：容器绑定的天生形状就是同时点名两侧
     *   （`single<KnowledgeWritePort> { get<KnowledgeRepository>() }` 不点名具体类就绑不上，
     *   `viewModel { … }` 不点名 VM 就注册不了）。但这份必需是**逐颗点名**换来的：
     *   没点名的新具体类当场红——「因为我是 DI」这句空话换不来通行证。
     * - `di -> ui.` ＝ **一律不许、不可登记**：接线不需要知道任何屏幕或 Activity。本次实扫 0 条。
     * - `service -> ui./viewmodel.` ＝ **宿主必需**：Service 是 overlay 窗口的宿主，它必须把
     *   Composable 挂进 ComposeView、把面板 VM 从容器取出来——Activity 干的是同一件事。
     *   ⚠ 这一族与 `ui -> service`（`ui/home/HomeScreen.kt`、`ui/home/AdvisorStatus.kt`、
     *   `ui/SetupActivity.kt` 各有一处 `import …service.FloatingService`）**互指成环**；
     *   登记只让每条边看得见，不等于宣布它干净，判环不在本轮。
     * - `service -> data./java.io.File` ＝ **真越界**：入口拿数据必须经过 `domain/port/`。
     *   证据是本仓自己已经写下的两处判法——`ui` 那格把 `com.lovebrain.app.data.` 全禁，
     *   而 `ui/SetupActivity.kt -> data.EventBus` 是**登记着的债**、不是许可；
     *   Activity 与 Service 同为入口，不该有差别。这 5 条逐条写理由，只许缩。
     *
     * 形状之外还有两条硬要求（都被现存的东西逼出来）：
     * 1. **全限定名也算引用**：`di/AppModule.kt` 有 4 颗跨层类型根本不走 import 行
     *    （`com.lovebrain.app.data.FileKbArchiveTransfer(`、`…viewmodel.KnowledgeBaseViewModel(`、
     *    `…viewmodel.KbEditViewModel(`、`…viewmodel.newHomeStatusViewModel(`），只扫 import 行的尺对它们整个失明。
     * 2. **剥注释按源码语法**：用仓库里那唯一一份 [SourceScan.maskComments]（字符串里的 `//`
     *    不算注释、块注释按深度配对、掩成空格所以行号可信）——KDoc 里写「以后把
     *    com.lovebrain.app.data.EventBus 换成端口」不该被读成一条依赖。
     *    字符串字面量里的类名**算**依赖（这一层没有先例可抄，宁可偏严；本次实扫无这种命中）。
     */
    @Test
    fun `di and service name only the cross-layer edges registered with a reason`() {
        val root = mainRoot()
        entryPackages.forEach { pkg ->
            assertTrue("找不到 $pkg/ ——这一格会扫了个空集恒绿", File(root, pkg).isDirectory)
        }
        val found = entryPointEdges(root)
        // 实到明细每次跑都打一遍：登记值要能对着这份打印核（Gradle 收进 test-results 的 system-out）
        val foundTotal = found.values.sumOf { it.size }
        println("W-E entry-point scan: total=" + foundTotal + " files=" + found.size)
        found.forEach { (file, names) -> println("  " + file + " -> " + names.joinToString(", ")) }

        val problems = entryEdgeProblems(found)
        assertTrue(
            "di/service 的跨层引用与两本登记对不上。新账要么改走 domain/port 上的端口，" +
                "要么在下面点名并写清为什么非得这样：\n${problems.joinToString("\n")}",
            problems.isEmpty()
        )
        assertTrue("实扫 0 条说明这把尺接错了目录或前缀表（恒绿假闸）", found.isNotEmpty())

        // 表自己的等号证人：登记数写死在这里，「表比现实宽」就是恒绿的另一种写法
        assertEquals(
            "装配必需那一族现登 19 颗（本次实扫），表自己错了要先修表",
            19, entryAssembly.values.sumOf { it.size }
        )
        assertEquals("service 侧的越界债现登 5 条，只许缩不许长", 5, entryDebt.values.sumOf { it.size })

        // 口径表自证：同为认 data，di 算装配必需、service 算真越界；di 认 ui 一律禁
        assertEquals(EntryEdge.Assembly, entryCategory("di", "com.lovebrain.app.data.KnowledgeRepository"))
        assertEquals(EntryEdge.Debt, entryCategory("service", "com.lovebrain.app.data.SecurePrefs"))
        assertEquals(EntryEdge.Forbidden, entryCategory("di", "com.lovebrain.app.ui.panel.LoveBrainPanelScreen"))
        assertEquals(EntryEdge.Assembly, entryCategory("service", "com.lovebrain.app.ui.panel.LoveBrainPanelScreen"))

        // 扫描形状自证：全限定名抓得到、FileInputStream 不误判成 File、注释里的名字不算依赖
        val byFqn = entryEdgesOf("service", "class F { val p = com.lovebrain.app.data.SecurePrefs(app) }")
        assertTrue(
            "尺读不到全限定名引用（只认 import 行的话 di 那三颗就是漏的）：$byFqn",
            byFqn.contains("com.lovebrain.app.data.SecurePrefs")
        )
        val stream = entryEdgesOf("service", "import java.io.FileInputStream")
        assertTrue("java.io.FileInputStream 被当成了 java.io.File：$stream", stream.isEmpty())
        val commented = entryEdgesOf("service", "/** 以后把 com.lovebrain.app.data.EventBus 换成端口 */\nclass S")
        assertTrue("注释里的类名被当成了依赖：$commented", commented.isEmpty())
    }

    /**
     * 反向证明：新格子不是摆设。在**临时目录**里造一棵同布局的小树，不动生产文件
     * （与上面 `a freshly introduced violation is caught and a legal import is not` 同一手法）。
     */
    @Test
    fun `the entry-point ruler catches a freshly introduced edge and ignores a registered one`() {
        val tmp = java.nio.file.Files.createTempDirectory("entrydep").toFile()
        try {
            File(tmp, "di").mkdirs()
            File(tmp, "service").mkdirs()
            File(tmp, "di/Wired.kt").writeText(
                "package com.lovebrain.app.di\n" +
                    "import com.lovebrain.app.ui.panel.LoveBrainPanelScreen\nval x = 1\n",
                Charsets.UTF_8
            )
            File(tmp, "service/Bad.kt").writeText(
                "package com.lovebrain.app.service\n" +
                    "import com.lovebrain.app.data.KnowledgeRepository\nclass Bad\n",
                Charsets.UTF_8
            )
            File(tmp, "service/Fq.kt").writeText(
                "package com.lovebrain.app.service\n" +
                    "class Fq { val p = com.lovebrain.app.data.SecurePrefs(app) }\n",
                Charsets.UTF_8
            )
            File(tmp, "service/Commented.kt").writeText(
                "package com.lovebrain.app.service\n" +
                    "// import com.lovebrain.app.data.SecurePrefs 这条已经还掉了\nclass Commented\n",
                Charsets.UTF_8
            )
            File(tmp, "service/Stream.kt").writeText(
                "package com.lovebrain.app.service\nimport java.io.FileInputStream\nclass Stream\n",
                Charsets.UTF_8
            )
            // 已登记的宿主边（同一个键 + 同一个名字）不该被当成新账
            File(tmp, "service/FloatingService.kt").writeText(
                "package com.lovebrain.app.service\n" +
                    "import com.lovebrain.app.ui.panel.LoveBrainPanelScreen\nclass FloatingService\n",
                Charsets.UTF_8
            )
            val problems = entryEdgeProblems(entryPointEdges(tmp))
            assertTrue("di 里出现屏幕类型却没被判禁：$problems",
                problems.any { it.startsWith("禁：di/Wired.kt -> com.lovebrain.app.ui.panel") })
            assertTrue("service 新认一颗 data 具体类却没报新债：$problems",
                problems.any { it.startsWith("新债：service/Bad.kt -> com.lovebrain.app.data.KnowledgeRepository") })
            assertTrue("全限定名注入没被抓到：$problems",
                problems.any { it.startsWith("新债：service/Fq.kt -> com.lovebrain.app.data.SecurePrefs") })
            assertTrue("注释里的 import 被当成了依赖：$problems", problems.none { it.contains("Commented.kt") })
            assertTrue("FileInputStream 被当成了 File：$problems", problems.none { it.contains("Stream.kt") })
            assertTrue("已登记的宿主边被当成新账：$problems",
                problems.none { it.startsWith("新账：service/FloatingService.kt") })
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** 本轮的扫描范围（不进 [forbidden]，所以也不进 `--count`，理由见上面那段 KDoc） */
    private val entryPackages: List<String> = listOf("di", "service")

    /** 三个跨层前缀两包都扫；`java.io.File` 只给 service——与 [forbidden] 里 viewmodel 那格同口径 */
    private val entryLayerPrefixes: List<String> = listOf(
        "com.lovebrain.app.data.", "com.lovebrain.app.ui.", "com.lovebrain.app.viewmodel."
    )

    private fun entryPrefixesFor(pkg: String): List<String> =
        if (pkg == "service") entryLayerPrefixes + "java.io.File" else entryLayerPrefixes

    /** 一条跨层引用的归类——口径的代码形状：判据是 (包, 被引层)，不是身份 */
    private enum class EntryEdge { Assembly, Debt, Forbidden }

    private fun entryCategory(pkg: String, name: String): EntryEdge = when {
        pkg == "di" && name.startsWith("com.lovebrain.app.ui.") -> EntryEdge.Forbidden
        pkg == "service" &&
            (name.startsWith("com.lovebrain.app.data.") || name == "java.io.File") -> EntryEdge.Debt

        else -> EntryEdge.Assembly
    }

    /**
     * 只认 ASCII 标识符字符。Kotlin 的 `isLetterOrDigit()` 对汉字也返回 true，
     * 于是"前缀后紧跟中文"那种写法（注释以外的字面量里）会被拼进名字，
     * 实扫就多出一条测量探针读不到的边——登记值与打印值就对不上。
     */
    private fun isIdentChar(c: Char) =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

    /** 剥注释后按前缀抓名字：import 行与全限定名都算，同一文件内去重、按字典序 */
    private fun entryEdgesOf(pkg: String, src: String): List<String> {
        val code = SourceScan.maskComments(src)
        val out = LinkedHashSet<String>()
        for (prefix in entryPrefixesFor(pkg)) {
            var at = code.indexOf(prefix)
            while (at >= 0) {
                val after = at + prefix.length
                var end = after
                while (end < code.length && (isIdentChar(code[end]) || code[end] == '.')) {
                    end++
                }
                val tail = code.substring(after, end).trimEnd('.')
                val name = if (prefix.endsWith('.')) {
                    if (tail.isEmpty()) null else prefix + tail
                } else {
                    // 整名前缀：紧跟标识符字符说明那是更长的名字（java.io.FileInputStream）
                    if (after < code.length && isIdentChar(code[after])) null else prefix
                }
                if (name != null) out.add(name)
                at = code.indexOf(prefix, after)
            }
        }
        return out.sorted()
    }

    /** 实扫：文件相对路径 -> 它点名的跨层类型（剥注释，import 与全限定名一起算） */
    private fun entryPointEdges(root: File = mainRoot()): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        for (pkg in entryPackages) {
            val dir = File(root, pkg)
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.path }.forEach { f ->
                    val rel = "$pkg/" + f.relativeTo(dir).path.replace(File.separatorChar, '/')
                    val names = entryEdgesOf(pkg, f.readText(Charsets.UTF_8))
                    if (names.isNotEmpty()) out.getOrPut(rel) { mutableListOf() }.addAll(names)
                }
        }
        return out
    }

    /** 登记值去掉尾部理由之后的裸名字集合（与 [baseline] 那本册子同一种写法） */
    private fun registeredIn(register: Map<String, List<String>>, file: String): Set<String> =
        register[file].orEmpty().map { it.substringBefore("  (") }.toSet()

    /** 实扫与两本登记对一遍，返回所有问题（空 = 口径成立且表如实）；双向都判 */
    private fun entryEdgeProblems(found: Map<String, List<String>>): List<String> {
        val problems = mutableListOf<String>()
        found.forEach { (file, names) ->
            val pkg = file.substringBefore('/')
            names.forEach { name ->
                when (entryCategory(pkg, name)) {
                    EntryEdge.Forbidden -> problems += "禁：$file -> ${name}（装配根不许命名屏幕，这条不可登记）"
                    EntryEdge.Debt -> if (name !in registeredIn(entryDebt, file)) {
                        problems += "新债：$file -> ${name}（service 认数据层，改走 domain/port 的端口）"
                    }

                    EntryEdge.Assembly -> if (name !in registeredIn(entryAssembly, file)) {
                        problems += "新账：$file -> ${name}（未点名的跨层引用；确实必需也要先登记并写理由）"
                    }
                }
            }
        }
        entryAssembly.forEach { (file, entries) ->
            entries.map { it.substringBefore("  (") }.forEach { name ->
                if (name !in found[file].orEmpty()) problems += "装配登记已不存在：$file -> ${name}（那就把这一行删掉）"
            }
        }
        entryDebt.forEach { (file, entries) ->
            entries.map { it.substringBefore("  (") }.forEach { name ->
                if (name !in found[file].orEmpty()) problems += "债登记已不存在（还完了就把这一行删掉）：$file -> $name"
            }
        }
        val foundTotal = found.values.sumOf { it.size }
        val registeredTotal = entryAssembly.values.sumOf { it.size } + entryDebt.values.sumOf { it.size }
        if (foundTotal != registeredTotal) {
            problems += "总数不一致：实扫 ${foundTotal}，登记 ${registeredTotal}（防「文件登记对了但漏了其中一条」）"
        }
        return problems
    }

    /**
     * 装配必需的正向点名，19 颗（di 10 + service 宿主那一侧 9）。
     * 来源：本轮 2026-09-30 首跑实扫的打印值，不是凭印象写；
     * 2026-10-04 首页灯那颗装配工厂 `newHomeStatusViewModel` 进 di/AppModule.kt，di 侧 9→10，
     * 计数哨兵同步 18→19（值是那次实扫读出来的，不是猜的）。
     * 每颗都写清它是哪一格绑定 / 哪一个挂载点——「DI 天生要看实现」这句话不作数。
     */
    private val entryAssembly: Map<String, List<String>> = mapOf(
        "di/AppModule.kt" to listOf(
            "com.lovebrain.app.data.DeepSeekRepository  (装配必需：single { DeepSeekRepository(get()) } 的构造目标，也是 AiGateway 端口的实现侧——绑定要同时点名两侧)",
            "com.lovebrain.app.data.FeedbackCaseRepository  (装配必需：single { FeedbackCaseRepository(androidContext()) } 的构造目标)",
            "com.lovebrain.app.data.KnowledgeRepository  (装配必需：single { KnowledgeRepository(File(…), get(), …) } 的构造目标，且那几颗 `single<Knowledge*Port> { get<KnowledgeRepository>() }` 靠点名它来保证端口视图与仓库解析到同一个实例——「只有一个事务 owner」要能从图上读出来)",
            "com.lovebrain.app.data.SecurePrefs  (装配必需：single { SecurePrefs(androidContext()) } 与 single<SettingsStorePort> { get<SecurePrefs>() } 两端——页面拿端口、仓库拿具体类，必须是同一个实例)",
            "com.lovebrain.app.data.FileKbArchiveTransfer  (装配必需：single<KbArchivePort> { … } 的实现侧。**全限定名写法、没有 import 行**，只扫 import 的那把尺对它整个失明，本轮抓得到)",
            "com.lovebrain.app.viewmodel.LoveBrainViewModel  (装配必需：viewModel { LoveBrainViewModel(…) } 的注册目标)",
            "com.lovebrain.app.viewmodel.SetupViewModel  (装配必需：viewModel { SetupViewModel(…) } 的注册目标)",
            "com.lovebrain.app.viewmodel.KnowledgeBaseViewModel  (装配必需：viewModel {} 的注册目标，全限定名写法)",
            "com.lovebrain.app.viewmodel.KbEditViewModel  (装配必需：同上，全限定名写法)",
            "com.lovebrain.app.viewmodel.newHomeStatusViewModel  (装配必需：viewModel { com.lovebrain.app.viewmodel.newHomeStatusViewModel(get(), get(), get()) } 的注册目标。尺命中的是**工厂名**这一颗——装配收在 viewmodel 侧的 newHomeStatusViewModel() 里，HomeStatusViewModel 类名不出现在 di 这一格，di 对它只点名这一颗；全限定名写法、没有 import 行，与上一格同一种失明形状)"
        ),
        "service/FloatingService.kt" to listOf(
            "com.lovebrain.app.ui.SetupActivity  (宿主必需：Intent(this, SetupActivity::class.java) 拉起设置页——入口起 Activity 与 ui 层同形状，不是拿数据层)",
            "com.lovebrain.app.ui.common.OverlayTextToolbarHost  (宿主必需：悬浮窗里的文本工具条由窗口挂载，Compose 内容归 ui 所有)",
            "com.lovebrain.app.ui.common.rememberOverlayTextToolbar  (宿主必需：与上一行成对的那颗状态钩子，同一个挂载点)",
            "com.lovebrain.app.ui.panel.LoveBrainPanelScreen  (宿主必需：面板正文就是这颗 Composable，Service 是它唯一的 overlay 窗口宿主。⚠ 它与 ui/home 那几处 import service.FloatingService 互指成环，登记≠判它干净)",
            "com.lovebrain.app.viewmodel.LoveBrainViewModel  (宿主必需：by inject() 取面板 VM；本文件自搭 ViewModelStore/LifecycleRegistry/SavedStateRegistry 就是为它)"
        ),
        "service/OverlayBubbleWindow.kt" to listOf(
            "com.lovebrain.app.ui.bubble.BubbleUiState  (宿主必需：气泡窗口的状态类型，窗口只渲染它、不自己决定内容)",
            "com.lovebrain.app.ui.bubble.FloatingBubble  (宿主必需：气泡那颗 Composable 本身)",
            "com.lovebrain.app.ui.theme.LoveBrainTheme  (宿主必需：ComposeView.setContent 必须套主题)"
        ),
        "service/OverlayPanelWindow.kt" to listOf(
            "com.lovebrain.app.ui.theme.LoveBrainTheme  (宿主必需：面板窗口那一份主题套壳)"
        )
    )

    /**
     * 真越界的存量债，5 条（全在 service 侧），逐条写理由。只许缩：还掉一条就要回来删掉这一行，
     * 上面那条双向棘轮会替你数着。di 侧没有 debt 类别——那是口径表 [entryCategory] 判的，不是巧合。
     */
    private val entryDebt: Map<String, List<String>> = mapOf(
        "service/CopyCaptureService.kt" to listOf(
            "com.lovebrain.app.data.EventBus  (真越界·存量债：按静态成员用 EventBus.emitCapturedMessage(…)。domain/port 上还没有进程内总线这颗端口，端口化要动 data/ 那一族（本档禁改），所以先点名登记、只许缩)",
            "com.lovebrain.app.data.SecurePrefs  (真越界·存量债，这一族最重的一条：securePrefs = SecurePrefs(this) 自己 new 了一颗，绕开容器 ⇒ 第二个加密/降级判据持有者，与 AppModule 那格 KDoc「加密与降级判据仍只有 SecurePrefs 一处」直接冲突。它读的三员 captureEnabled / captureAllowedPackages / accessibilityDisclosureVersion 都已经在 SettingsStorePort 上 ⇒ 还法是让它从容器取端口，但那改的是服务的运行形状，不在本档顺手做)",
            "java.io.File  (真越界·存量债：诊断日志自己拼 File(filesDir, DIAG_FILE) 落盘。与 [forbidden] 里 viewmodel 那格 java.io.File 同口径——入口不许自己摸路径；还法要把诊断落盘挪进 data 侧唯一的 IO 主人，本档禁改)"
        ),
        "service/FloatingService.kt" to listOf(
            "com.lovebrain.app.data.EventBus  (真越界·存量债：订阅 capturedMessages / panelRequest 并调 consumePanelRequest()，用的都是 data 里那颗 SharedFlow 的静态成员；缺端口，同 CopyCaptureService 那条)",
            "com.lovebrain.app.data.SecurePrefs  (真越界·存量债：by inject() 拿的是容器那一颗——实例唯一，比 CopyCaptureService 自己 new 那条轻——但按**具体类型**注入 ⇒ 端口那层在入口上不生效。它用的 panelWidth/panelHeight 两员不在 SettingsStorePort 上，补那两员要连着动端口与它的内存 fake 契约，不在本档文件集)"
        )
    )
}
