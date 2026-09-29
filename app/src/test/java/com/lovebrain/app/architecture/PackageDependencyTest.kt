package com.lovebrain.app.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 包依赖方向门禁（独立复核 §5.1「先做包边界，不急着一次多模块化」、
 * §7 第二步第 4 条「建 package dependency test，禁止 UI import data、domain import Android」，
 * 以及 §4 表里 DIP/迪米特那两行的 FAIL）。
 *
 * 为什么不是"现在就全绿"：今天的真实存量是 **5 条越界 import，分布在 5 个文件**
 * （本轮 `SecurePrefs` / `KbArchiveTransfer` 端口化后，viewmodel 少掉一处 `java.io.File`：
 * `2dd94c0` 15→10、`ab7457a` 10→6、本轮 6→5；下面那份 baseline 登记 5 条 / 5 个键；
 * `assertEquals(5, …)` 那格每次都在核它）。
 * ⚠ 这一段原来写的是"6 条 / 6 个文件"——还债时顺手把存量叙述也扫了，别再留"注释比实现旧"的假账。
 * 直接把规则写成硬门禁会让 CI 永久红，然后被人用 `|| true` 关掉——
 * 复核 §9 第 6 条禁止的正是这个，那比没有门禁更糟。
 *
 * 所以这里是棘轮：
 * 1. 新增任何一条越界 import 立即失败，失败信息点名文件、import 与它撞了哪条前缀；
 * 2. 基线登记的条目如果已经不在，也失败，红的内容是"债务降了，把清单改短"——
 *    没有这一半，基线会随着重构悄悄腐烂，最后没人知道还剩多少；
 * 3. 条目总数也比一次，防止"文件登记对了但漏了第二条"。
 *
 * 还债顺序（复核 §7 第二步第 1 条）：先给这些具体类建端口（AiGateway /
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
        // feature/* 是 §5.2 搬出来的状态持有者：它只能碰 model 与 domain.port，
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
        // §5.1 第一层：core 不知道数据层、容器与 Android 侧的具体东西，否则"设计系统"
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
            "出现了新的跨层依赖。要么改成走端口/意图（复核 §4 的 DIP 目标），" +
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
}
