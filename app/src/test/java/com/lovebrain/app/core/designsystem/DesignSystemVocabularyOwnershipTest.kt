package com.lovebrain.app.core.designsystem

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 公共件不许带**某一个页面的词表**（坑表 157 / 账本 §75.1 那一笔）。
 *
 * 成因不是想象：上一轮的归并代理把首页那三件套的标签 `累计生成 / 累计花费 / 采用率`
 * 写死进了 `core/designsystem/LbMetricGrid.kt`，还在 KDoc 里给自己写了一句辩护
 * （"这三颗是设计系统自己的词，不是首页的词"）——同一份文件里另一颗组件的注释当场反驳了它。
 * 那条旧口已经被删掉，但**没有任何一格在判它会不会再长回来**：异形那把尺只判形状，
 * 一颗持有单页词表的"正常组件"它照样报成正常。这一格补的就是那一栏。
 *
 * 判据按**位置**认，不按词表黑名单认（黑名单要靠猜下一个页面用什么词，那种尺会恒绿）：
 * `core/designsystem` 里每一条含中文的字符串字面量，都必须落在**给开发者看的断言消息**里
 * （`require` / `check` / `error` / 那几颗异常构造），也就是账本 §75.1 复算时用的同一口径
 * ——"用户可见 0 条，剩下的在 `require(…)` 里"。落在别处（文案位、默认实参、`Text(` 里）
 * 就是设计系统开始认识某一页。
 *
 * ⚠ 这一格的绿是廉价的（今天本来就是 0 条用户可见），所以另外两格专门判尺本身：
 * 分类器必须认得出合成出来的坏形状、也必须看得见真文件里那几条开发者消息——
 * 两头都不失败才叫"这条断言在判东西"。
 *
 * ⚠ 射程只有 `core/designsystem` 这一个目录（整目录的 .kt 都扫）。
 * 同一批公共件 `ui/common` 今天另有 4 条界面词
 * （`OverlayTextToolbar.kt` 的复制/剪切/粘贴/全选），那是**实到欠账**、不是本格的漏判：
 * 本格一开张就把别人地盘的债算成自己的红，只会让人把判据改软。那一半归字面量预算那把尺。
 */
class DesignSystemVocabularyOwnershipTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private val designSystemDir get() = File(appRoot, "core/designsystem")

    /** 一行以内、含中文的字符串字面量（与 UiStringLiteralBudgetTest 的 HAN_LITERAL 同一把尺） */
    private val hanLiteral = Regex(""""[^"\n]*[\p{IsHan}][^"\n]*"""")

    /** 给开发者看的那些调用：它们的实参不是界面文案，是"实现被违反了"的话 */
    private val developerCall = Regex(
        """(?<![\w.])(require|requireNotNull|check|checkNotNull|error|TODO|""" +
            """IllegalArgumentException|IllegalStateException|AssertionError)\s*\("""
    )

    /**
     * 掩注释 → 把字符串内容抹平（配对才安全）→ 找出**不在**开发者断言消息里的中文字面量。
     *
     * 返回的是"文件名:行号: 原文"，失败信息要能让人直接对着文件看，不许只报一个数。
     */
    private fun unownedVocabulary(name: String, text: String): List<String> {
        val masked = SourceScan.maskComments(text).replace("\r\n", "\n")
        val spans = developerSpans(blankOutStrings(masked))
        return hanLiteral.findAll(masked)
            .filter { lit -> spans.none { it.first <= lit.range.first && lit.range.last < it.second } }
            .map { lit ->
                val line = masked.take(lit.range.first).count { it == '\n' } + 1
                "$name:$line ${lit.value}"
            }
            .toList()
    }

    /** 每个开发者断言调用的**整段跨度**（含尾随 lambda：`require(x) { "中文" }` 的文案在括号之后） */
    private fun developerSpans(view: String): List<Pair<Int, Int>> =
        developerCall.findAll(view).map { m ->
            val open = view.indexOf('(', m.range.first)
            open to SourceScan.callEnd(view, open)
        }.toList()

    /**
     * 字符串内容整段抹成空格（保留引号与换行，偏移一字不变）。
     *
     * 为什么需要这一步：`require(size <= 3) { "最多 3 个) 再多请改别的容器" }` 里那对**串内**
     * 括号会把配对计数带跑——`callEnd` 在串内那个右括号就收工，于是这条真·开发者消息
     * 反而被判成词表，而后面每一条坏形状都可能被算进"某个调用里"而漏掉。
     * 坑表 156 那一课的形状：按形状复算必须**先认字符串、再认注释**，
     * 而且只把内容换成空格、不删文本（否则行号就飘了）。
     */
    private fun blankOutStrings(masked: String): String {
        val out = StringBuilder(masked)
        var i = 0
        while (i < masked.length) {
            if (masked[i] == '"') {
                val triple = masked.startsWith("\"\"\"", i)
                val body = if (triple) i + 3 else i + 1
                var j = body
                if (triple) {
                    while (j < masked.length && !masked.startsWith("\"\"\"", j)) j++
                    blank(out, body, minOf(j, masked.length))
                    i = minOf(j + 3, masked.length)
                } else {
                    while (j < masked.length && masked[j] != '"') {
                        if (masked[j] == '\\') j++
                        j++
                    }
                    blank(out, body, minOf(j, masked.length))
                    i = minOf(j + 1, masked.length)
                }
            } else if (masked[i] == '\'') {
                var j = i + 1
                while (j < masked.length && masked[j] != '\'') {
                    if (masked[j] == '\\') j++
                    j++
                }
                blank(out, i + 1, minOf(j, masked.length))
                i = minOf(j + 1, masked.length)
            } else {
                i++
            }
        }
        return out.toString()
    }

    private fun blank(out: StringBuilder, from: Int, to: Int) {
        for (k in from until to) if (out[k] != '\n') out.setCharAt(k, ' ')
    }

    private fun designSystemSources(): Map<String, String> {
        assertTrue("找不到设计系统目录：$designSystemDir", designSystemDir.isDirectory)
        return designSystemDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.name to it.readText(Charsets.UTF_8) }
    }

    /** 主判据：设计系统里一条用户看得见的中文都不许有，中文只许出现在给开发者看的断言消息里 */
    @Test
    fun `the design system holds no page vocabulary`() {
        val offenders = designSystemSources().flatMap { (name, text) ->
            unownedVocabulary(name, text)
        }
        assertTrue(
            "core/designsystem 里有 ${offenders.size} 条中文不在开发者断言消息里——" +
                "设计系统一旦认识某一页的词，下一颗组件就会再来一套：\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty()
        )
    }

    /**
     * 尺自己看得见东西：整目录的中文字面量读数（连同实到清单一起报出来）。
     *
     * 扫到 0 条就说明掩码或配对先坏了——那种"账本一致"是假的（`OddShapeOwnershipTest`
     * 同一格判据的写法：看不见东西时宁可失败也别猜）。
     */
    @Test
    fun `the scanner is not blind on the real design system`() {
        val readings = designSystemSources().mapValues { (_, text) ->
            hanLiteral.findAll(SourceScan.maskComments(text).replace("\r\n", "\n")).count()
        }
        val seen = readings.values.sum()
        assertTrue(
            "整目录扫到 0 条中文字面量 ⇒ 这把尺是瞎的（账本 §75.1 记的是 3 条 require 消息），" +
                "逐文件读数：$readings",
            seen > 0
        )
        assertTrue(
            "看得见的那几处要逐文件点名，换文件就红在这里看读数：" +
                readings.filter { it.value > 0 }.toSortedMap(),
            readings.values.count { it > 0 } >= 1
        )
    }

    /**
     * 反向证人：合成出来的坏形状必须被抓，好形状必须不被抓。
     *
     * 这一格才是"判据有牙"的那一半——把跨度换成"只到配对右括号"（即不用
     * [SourceScan.callEnd]，它含尾随 lambda），`require(x) { "中文" }` 就会被判成词表；
     * 把 [blankOutStrings] 删掉，串内那对括号把配对带跑，文案那一条反而逃掉了。
     */
    @Test
    fun `the classifier tells a developer message from a rendered label`() {
        val kept = """
            fun LbThing(metrics: List<String>, size: Int) {
                require(metrics.isNotEmpty()) {
                    "LbThing 没有任何内容可画：metrics 为空"
                }
                require(size <= 3) { "最多 3 个) 再多请改用浮层 Sheet" }
            }
        """.trimIndent()
        assertEquals(
            "开发者断言消息不算词表（含串内括号那一发）",
            emptyList<String>(), unownedVocabulary("Kept.kt", kept)
        )

        val bad = """
            object LbThingStyles {
                val defaults = listOf("累计生成", "累计花费", "采用率")
            }

            @Composable
            fun LbThing(label: String = "今日对话") {
                Text(text = "帮你更自然地表达")
            }
        """.trimIndent()
        val caught = unownedVocabulary("Bad.kt", bad)
        assertEquals("写死在设计系统里的页面词表必须逐条抓到，实到 $caught", 5, caught.size)
        assertTrue("四条里必须有那三个标签与那条默认实参，实到 $caught",
            caught.any { "累计生成" in it } && caught.any { "今日对话" in it })
    }
}
