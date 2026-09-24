package com.lovebrain.app.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 用户可见字面量的预算（独立复核 P1-05）。
 *
 * 报告原话：静态计数至少还有 `Text("中文...")` 101 处、`contentDescription = "中文..."` 10 处，
 * 并规定"新 UI 架构必须禁止 production composable 新增直接用户可见字面量"。
 *
 * 本轮没有能力把 101 处全搬进 strings.xml（那是阶段三与 `Lb*` 组件一起做的工作），
 * 但**先把闸装上**是有意义的：不做闸，阶段三每写一个新组件都会顺手再塞几条中文，
 * 到时候还是 101 → 150，"文案收口"永远在下一轮。
 *
 * 与 [PackageDependencyTest] 同一套棘轮语义：超出预算 = 新增债；
 * 实测低于预算 = 债还掉了但没落账，也红。
 */
class UiStringLiteralBudgetTest {

    private enum class Kind(val label: String, val regex: Regex) {
        /** Text("...") / Text(text = "...") 里带中日韩字符的字面量 */
        TEXT("Text 可见文案", Regex("""\bText\s*\(\s*(?:text\s*=\s*)?"[^"]*[\p{IsHan}][^"]*"""")),

        /** contentDescription = "..." 里带中文的字面量 */
        DESC("contentDescription", Regex("""\bcontentDescription\s*=\s*"[^"]*[\p{IsHan}][^"]*""""))
    }

    /**
     * 实测基线。**用本文件这两条正则扫 app/src/main 得到**，不是照抄复核报告的 101。
     *
     * 为什么和报告的数不一样：复核那条是 `grep 'Text\("[^"]*[一-龥]'`，
     * 只能数到 `Text("中文` 这种紧挨着写法的；本文件的正则连
     * `Text(text = "中文…")`、`Text(\n  "中文…", color = …)` 一起数。
     * 两把尺的差 = 209 − 101 = 108 处，说明报告那句"至少还有 101 处"确实是下界，
     * 真实面更大。棘轮只能用其中一把尺量，这里选宽的那把：
     * 窄尺会漏掉新写法，等于给"换个写法继续塞中文"留门。
     */
    private val budget = mapOf(
        Kind.TEXT to 209,
        Kind.DESC to 10
    )

    private fun countIn(root: File, kind: Kind): Int {
        if (!root.isDirectory) return -1
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { f -> kind.regex.findAll(f.readText(Charsets.UTF_8)).count() }
    }

    private fun mainRoot(): File {
        val root = File("src/main")
        assertTrue("必须在 :app 模块下跑，找不到 $root", root.isDirectory)
        return root
    }

    /** 扫描器接错目录时会"0 条 = 通过"，所以先证明它看得见东西 */
    @Test
    fun `the scanner actually finds the known literals`() {
        val found = countIn(mainRoot(), Kind.TEXT)
        assertTrue("扫到 $found 条 Text 中文字面量——路径或正则坏了", found > 50)
    }

    @Test
    fun `user visible string literals do not grow`() {
        val grew = Kind.values().mapNotNull { kind ->
            val now = countIn(mainRoot(), kind)
            val allowed = budget.getValue(kind)
            if (now > allowed) "${kind.label}: 实测 $now > 预算 $allowed" else null
        }
        assertTrue(
            "新增了用户可见的字面量。请放进 res/values/strings.xml 与 values-en，" +
                "让中英文一起覆盖：\n  " + grew.joinToString("\n  "),
            grew.isEmpty()
        )
    }

    @Test
    fun `the budget still reflects reality`() {
        val stale = Kind.values().mapNotNull { kind ->
            val now = countIn(mainRoot(), kind)
            val allowed = budget.getValue(kind)
            if (now < allowed) "${kind.label}: 实测 $now < 预算 $allowed（还掉了就来把数字改小）" else null
        }
        assertTrue(
            "预算允许虚高的话，它会慢慢烂回去。" +
                "本轮把 101 处搬掉了一些，就必须把这里的数字一起改小：\n  " + stale.joinToString("\n  "),
            stale.isEmpty()
        )
    }

    /**
     * 反向证明：临时目录里造两个文件，一个带中文 Text、一个只用字符串资源，
     * 要求前者被数到、后者为 0。没有这一格，正则是不是恒真没人知道。
     */
    @Test
    fun `the counters count what they claim and nothing else`() {
        val tmp = java.nio.file.Files.createTempDirectory("literal").toFile()
        try {
            File(tmp, "A.kt").writeText(
                """
                package x
                import androidx.compose.material3.Text
                @Composable fun A() { Text("你好"); Text(text = "再说一次") }
                """.trimIndent(), Charsets.UTF_8
            )
            File(tmp, "B.kt").writeText(
                """
                package x
                import androidx.compose.material3.Text
                @Composable fun B() {
                    Text(stringResource(R.string.panel_retry))
                    Image(contentDescription = stringResource(R.string.cd_close))
                }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals("两个中文字面量都应被数到", 2, countIn(tmp, Kind.TEXT))
            assertEquals("走 stringResource 的不许被数进来", 0, countIn(tmp, Kind.DESC))

            File(tmp, "C.kt").writeText(
                """
                package x
                val mod = Modifier.semantics { contentDescription = "关闭按钮" }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals("contentDescription 里的中文也要被数到", 1, countIn(tmp, Kind.DESC))
        } finally {
            tmp.deleteRecursively()
        }
    }
}
