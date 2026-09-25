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
     * 实测口径：②③ 各 6 个名字，① 各 6 个旧名，改前全为 0/1/命中。
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
            "UsageMetric" to "LbMetricCard"
        )
        assertTrue("旧名字一个都不该有，新名字 6 颗，所以不能扫了个空目录", retired.size == 6)
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

    /** `typealias` 后面跟 `=`，`fun` 后面跟 `(`，两个分支都得在尺子里 */
    private fun declaration(name: String): Regex =
        Regex("\\b(?:fun|typealias)\\s+`?$name`?\\s*[<(=]")

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
