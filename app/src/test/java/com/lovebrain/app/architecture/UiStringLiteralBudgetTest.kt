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

    private enum class Kind(val label: String, val anchor: Regex) {
        /** `Text(` ——整段实参里任何带中文的字符串字面量 */
        TEXT("Text 可见文案", Regex("""\bText\s*\(""")),

        /** `contentDescription =` ——赋值右边那段表达式里带中文的字面量 */
        DESC("contentDescription", Regex("""\bcontentDescription\s*=\s*""")),
    }

    /** 一行以内、含中日韩字符的字符串字面量 */
    private val HAN_LITERAL = Regex(""""[^"\n]*[\p{IsHan}][^"\n]*"""")

    /**
     * 从锚点往后截"这一个表达式"的范围。
     *
     * 为什么不是正则一行搞定：`Text(text = if (proactiveActive) "中文A" else "中文B", …)`
     * 这种写法里，锚点和字面量之间隔着一层带括号的判断条件——一条只看"锚点后面紧跟引号"的
     * 正则永远数不到它。本轮从 `MessageList` 里搬走两处中文时就是这么发现的：
     * **它们当时根本不在这把尺的计数里**，搬完了预算数字一个字都不用改，那笔账就成了假账。
     * 所以这里按括号配对取范围，而不是按"紧跟不紧跟"。
     */
    private fun expressionAt(text: String, from: Int, kind: Kind): String {
        var depth = if (kind == Kind.TEXT) 1 else 0
        var i = from
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    if (depth == 0) return text.substring(from, i)
                    depth--
                    if (depth == 0) return text.substring(from, i)
                }
                ',' -> if (depth == 0 && kind == Kind.DESC) return text.substring(from, i)
                '\n' -> if (kind == Kind.DESC && i + 1 < text.length &&
                    text[i + 1] != '+' && text[i + 1] != '"'
                ) {
                    // 赋值换行且下一行不是续着写的字符串/拼接 → 表达式到此为止
                    if (depth == 0) return text.substring(from, i)
                }
            }
            i++
        }
        return text.substring(from)
    }

    private fun countHanLiterals(text: String, kind: Kind): Int =
        kind.anchor.findAll(text).sumOf { match ->
            HAN_LITERAL.findAll(expressionAt(text, match.range.last + 1, kind)).count()
        }

    /**
     * 实测基线。**数字来自本文件这把尺对 app/src/main 的一次实扫**，不是照抄复核报告的 101。
     *
     * 三把尺的关系（都留档，不然下一次又有人拿最小的那个数当全量）：
     * - 复核报告的 **101**：只数 `Text("中文`，是下界；
     * - 上一轮的正则 **209**：多认 `Text(text = "中文…")` 与跨行写法，
     *   仍看不见 `Text(text = if (…) "中文" else "中文")`，还是下界；
     * - 本轮换成按括号配对取整段实参（见 [expressionAt]）→ **实测 254**。
     *
     * 209 → 254 这 45 处**不是有人新塞了中文**，是原来量不到的那批。
     * 换尺会让数字变大，这一条写在预算旁边，免得下一个窗口把它误读成"债涨了"、
     * 或者干脆把正则改窄回去拿个好看的数。棘轮照旧：只许往下走。
     */
    private val budget = mapOf(
        Kind.TEXT to 254,
        Kind.DESC to 10
    )

    private fun countIn(root: File, kind: Kind): Int {
        if (!root.isDirectory) return -1
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { f -> countHanLiterals(f.readText(Charsets.UTF_8), kind) }
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

            // 这一格是给"尺子换过"这件事兜底的：锚点与字面量之间隔着一层带括号的判断条件，
            // 旧那条只看紧跟引号的正则在这里是瞎的——真出过事（见 expressionAt 的注释）。
            File(tmp, "D.kt").writeText(
                """
                package x
                import androidx.compose.material3.Text
                @Composable fun D() {
                    Text(
                        text = if (expanded) "已经展开" else "点击展开",
                        color = Color.Blue
                    )
                }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "隔着一层 if 的两处中文也必须数到（A 里 2 处 + D 里 2 处）",
                4, countIn(tmp, Kind.TEXT)
            )
        } finally {
            tmp.deleteRecursively()
        }
    }
}
