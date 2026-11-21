package com.lovebrain.app.viewmodel

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 建库这一趟**不许把取消咽下去**（审计核实的 `runCatching` 吞取消站点）。
 *
 * 为什么这一格按源码形状判、而不是跑一遍看行为：取消是外部触发的
 * （`cancelOnboarding()` / viewModelScope 销毁），从调用方看 job 本来就已经是 cancelled，
 * "有没有重抛"在外部量不出差别；而这一笔差别恰恰是
 * `scripts/audit_cancellation.py` 判 CANCEL-SWALLOWED 的那一条——
 * `runCatching { … }` 会把 CancellationException 变成一次"正常完成"的 Result，
 * 协程以成功收场，等它的人以为真跑完了。
 *
 * 形状判据只钉两件事（注释已由 [SourceScan.maskComments] 掩成空格，注释里提这个词不算）：
 * 1. 两个建库入口（问卷 / 跳过）里不再出现 `runCatching`；
 * 2. 文件里每一处 `catch (: CancellationException)` 的块体内都有 `throw`（不许咽掉）。
 */
class KnowledgeBaseCreationCancellationTest {

    private fun maskedSource(): String {
        val f = File("src/main/java/com/lovebrain/app/viewmodel/KnowledgeBaseViewModel.kt")
        assertTrue("尺接错了文件，找不到 $f —— 宁可红也不许空转", f.isFile)
        return SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    /** 从 [head] 起第一个 `{` 的配对块体（返回左花括号与配对右花括号之间的文本） */
    private fun blockAfter(src: String, head: String): String {
        val at = src.indexOf(head)
        assertTrue("判据读不到「$head」", at >= 0)
        val open = src.indexOf('{', at)
        assertTrue("「$head」后面没有块体", open > at)
        var depth = 0
        var i = open
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(open + 1, i)
                }
            }
            i++
        }
        throw AssertionError("「$head」的块体走到文件尾都没闭合")
    }

    @Test
    fun `the two creation entries never wrap suspend work in runCatching`() {
        val src = maskedSource()
        listOf("fun createKbWithOnboarding(", "fun createEmptyKb(").forEach { head ->
            val body = blockAfter(src, head)
            assertFalse(
                "「$head」里 runCatching 会把取消变成一次正常完成的 Result（job.isCancelled = false）：" +
                    "这一趟必须 try/catch，并在 catch 里把 CancellationException 原样抛回",
                body.contains("runCatching")
            )
            assertTrue("「$head」里必须自己接住取消并重抛（找不到 catch 就说明形状换了）",
                body.contains("catch (e: CancellationException)"))
            assertTrue("「$head」接住取消后必须 throw e 把取消抛回去", body.contains("throw e"))
        }
    }

    @Test
    fun `every CancellationException catch in the view model rethrows`() {
        val src = maskedSource()
        val needle = "catch (e: CancellationException)"
        var from = 0
        var found = 0
        while (true) {
            val at = src.indexOf(needle, from)
            if (at < 0) break
            found++
            val open = src.indexOf('{', at)
            assertTrue("第 $found 处取消分支没有块体", open > at)
            var depth = 0
            var i = open
            while (i < src.length) {
                when (src[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                i++
            }
            assertTrue("第 $found 处取消分支走到文件尾都没闭合", i < src.length)
            val body = src.substring(open + 1, i)
            assertTrue(
                "第 $found 处 `catch (CancellationException)` 没有重抛——取消会被吞成正常完成：$body",
                body.contains("throw")
            )
            from = i
        }
        assertTrue("一处 catch 都没扫到：判据坏了，不许空转", found >= 2)
    }
}
