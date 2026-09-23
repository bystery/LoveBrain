package com.lovebrain.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "单 Job owner" 的静态防回归合同。
 *
 * 复核报告 §6 S2-02 / §8 第二步 1-4 条反复出现同一类病灶：
 * 领域类**接收外部 CoroutineScope、自己 launch 并返回 Job、再通过 Callbacks 接口反向写 ViewModel 状态**。
 * 那样"谁拥有这段异步工作"就有两个答案，取消、停止、迟到结果都得各写一套。
 *
 * GenerationEngine 与 KnowledgeTriggerCoordinator 都已改成冷流；本用例把这三条形状钉住，
 * 使同类写法不能再悄悄进来：
 * 1. 生产代码里不得再有 `interface *Callbacks`；
 * 2. 不得有函数返回 `Job`（返回 Job 就是要调用方去管第二个 owner）；
 * 3. `scope: CoroutineScope` 参数与领域层 `launch` 只允许出现在 ForegroundOperationCoordinator——
 *    它按设计就是那个唯一 owner。
 *
 * 扫描本身也要自证不是空跑，否则规则会因为路径写错而"永远通过"。
 */
class SingleOwnerContractTest {

    private val appRoot = File("src/main/java/com/lovebrain/app")
        .takeIf { it.isDirectory }
        ?: File("app/src/main/java/com/lovebrain/app")

    private fun sources(vararg dirs: String): List<File> {
        val files = dirs.flatMap { dir ->
            File(appRoot, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue("扫描到 0 个文件：$dirs 路径写错了，这条规则会永远通过", files.size >= 10)
        return files
    }

    /** 去掉注释，避免把"以前这里有个 Callbacks 接口"这种说明文字判成违规 */
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

    private val allowedOwner = "ForegroundOperationCoordinator.kt"

    @Test
    fun `no callback interface writes back into the view model`() {
        val offenders = sources("domain", "data", "service", "viewmodel", "ui", "model")
            .map { it.name to codeOf(it.readText()) }
            .filter { (_, code) -> Regex("interface\\s+\\w*Callbacks").containsMatchIn(code) }
            .map { it.first }
        assertTrue("不得再引入 Callbacks 回写接口：$offenders", offenders.isEmpty())
    }

    @Test
    fun `no function hands a Job back to its caller`() {
        val offenders = sources("domain", "data", "service", "viewmodel", "ui", "model")
            .map { it.name to codeOf(it.readText()) }
            .filter { (_, code) -> Regex("\\)\\s*:\\s*Job\\??\\s*[{=]").containsMatchIn(code) }
            .map { it.first }
        assertTrue("返回 Job 等于把第二个 owner 交给调用方：$offenders", offenders.isEmpty())
    }

    @Test
    fun `only the coordinator may own a scope and launch jobs`() {
        val scopeParams = sources("domain", "data", "service", "viewmodel")
            .map { it.name to codeOf(it.readText()) }
            .filter { (name, code) ->
                name != allowedOwner && Regex("scope\\s*:\\s*CoroutineScope").containsMatchIn(code)
            }
            .map { it.first }
        assertTrue(
            "领域/数据/VM 类不得接收外部 CoroutineScope（唯一例外是协调器本身）：$scopeParams",
            scopeParams.isEmpty()
        )

        val launches = sources("domain")
            .map { it.name to codeOf(it.readText()) }
            .filter { (name, code) ->
                name != allowedOwner && Regex("(?:\\w+Scope|scope)\\.launch\\b").containsMatchIn(code)
            }
            .map { it.first }
        assertTrue("domain 层只有协调器可以 launch：$launches", launches.isEmpty())
    }

    @Test
    fun `the engine and the trigger coordinator expose cold event streams`() {
        val engine = codeOf(File(appRoot, "domain/GenerationEngine.kt").readText())
        val trigger = codeOf(File(appRoot, "domain/KnowledgeTriggerCoordinator.kt").readText())
        for ((label, code) in listOf("GenerationEngine" to engine, "KnowledgeTriggerCoordinator" to trigger)) {
            assertTrue("$label 必须以 Flow 对外产出结果", code.contains("Flow<"))
            assertTrue("$label 不得再 import CoroutineScope", !code.contains("import kotlinx.coroutines.CoroutineScope"))
        }
    }
}
