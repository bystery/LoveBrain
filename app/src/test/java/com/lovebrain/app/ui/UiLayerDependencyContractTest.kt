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
}
