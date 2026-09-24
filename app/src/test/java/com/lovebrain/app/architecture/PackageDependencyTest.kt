package com.lovebrain.app.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 包依赖方向门禁（独立复核 §5.1「先做包边界，不急着一次多模块化」、
 * §7 第二步第 4 条「建 package dependency test，禁止 UI import data、domain import Android」，
 * 以及 §4 表里 DIP/迪米特那两行的 FAIL）。
 *
 * 为什么不是"现在就全绿"：今天的真实存量是 **15 条越界 import，分布在 11 个文件**
 * （数字来自 `bash scripts/package_deps_report.sh` 的一次实扫，不是凭印象写的）。
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
 * KnowledgeReadPort / KnowledgeWritePort），把 domain 的 6 处 data.* 换掉，
 * 再动 viewmodel。每换掉一处，就重跑 report 脚本、把基线改小。
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
        "viewmodel" to listOf("java.io.File")
    )

    /**
     * 存量债务的逐条登记。来源：bash scripts/package_deps_report.sh（2026-09-24 实扫）。
     * 只想缩，不想长；要新增必须先在这里写清"为什么这次不得不过界"。
     */
    private val baseline: Map<String, List<String>> = mapOf(
        "domain/GenerationEngine.kt" to listOf(
            "com.lovebrain.app.data.DeepSeekRepository",
            "com.lovebrain.app.data.ProviderRequestConfig"
        ),
        "domain/KnowledgeTriggerCoordinator.kt" to listOf(
            "com.lovebrain.app.data.DeepSeekRepository",
            "com.lovebrain.app.data.RawGenerationResult"
        ),
        "domain/PromptBuilder.kt" to listOf("android.content.Context"),
        "model/ProfileUpdate.kt" to listOf("com.lovebrain.app.domain.StageCatalog"),
        "model/ProfileUpdateSchema.kt" to listOf("com.lovebrain.app.domain.StageCatalog"),
        "ui/SetupActivity.kt" to listOf("com.lovebrain.app.data.EventBus"),
        "viewmodel/KnowledgeBaseViewModel.kt" to listOf("java.io.File"),
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
        assertEquals("基线条目数必须与 report 脚本同一次统计一致", 10, baseline.values.sumOf { it.size })
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
