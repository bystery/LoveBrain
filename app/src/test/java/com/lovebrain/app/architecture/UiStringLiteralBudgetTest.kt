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

        /**
         * `stateDescription =` ——读屏公告"现在收起/展开"那一句。
         *
         * 这是第二个盲区：它既不是 `Text(` 也不是 `contentDescription`，
         * 但同样是**用户听得见的话**（`SuggestPanel` 与 `CounselingPanel` 那两处折叠控件
         * 原本写着内联中文，英文环境下照样念中文，而两把尺都看不见）。
         * 起点就是 0——本轮把锦囊那处搬进资源了，谈心那处还欠着，见下方交接行。
         */
        STATE("stateDescription", Regex("""\bstateDescription\s*=\s*""")),

        /**
         * `Lb…(` ——设计系统组件的**具名实参**里写的中文。
         *
         * 这一栏是 §6.1 搬家逼出来的：`HomeTopBar` 通用化成 `LbTopBar(title =, subtitle =)` 之后，
         * 「帮你更自然地表达」从 `Text("…")` 里挪进了组件参数，而 TEXT 那把尺的锚点是 `Text(`，
         * 于是实扫从 247 掉到 246。**那不是我搬走了一处硬编码**，同一条字符串还在原处，
         * 只是尺看不见了。按本文件自己的规矩（"搬掉两处而 DESC 一个没动"那次），
         * 计数变了必须先证明变化是真的，才许动预算数字。
         *
         * 口径：整段实参里带中文的字面量，**减去已被前三栏区间覆盖的那些**——
         * 所以 `LbCard { Text("中文") }` 只在 TEXT 记一次，不会两栏重复入账。
         *
         * 边界（仍然欠着的那半）：只认 `Lb` 前缀的设计系统组件。页面自造的子组件
         * （`StatCell(label = "…")` 这种）依旧在盲区里，交接单 §4 有这一行。
         */
        COMPONENT("组件实参里的可见文案", Regex("""\bLb\w+\s*\(""")),
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
        val anchorIsCall = kind == Kind.TEXT || kind == Kind.COMPONENT
        var depth = if (anchorIsCall) 1 else 0
        var i = from
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    if (depth == 0) return text.substring(from, i)
                    depth--
                    // 只有锚点本身是"函数调用"（Text(）时，配对回到 0 才是这段实参的结束。
                    // 赋值型锚点（contentDescription = / stateDescription =）不能在这里收尾：
                    // `contentDescription = if (expanded) "收起" else "展开"` 的 if 条件括号会把切片
                    // 截断，于是那两处中文又数不到了——本轮第一版就栽在这行上（预算报 DESC 没变，
                    // 而我刚搬掉了两处，这个"没变"本身就是线索）。
                    if (anchorIsCall && depth == 0) return text.substring(from, i)
                }
                ',' -> if (depth == 0 && !anchorIsCall) return text.substring(from, i)
                '\n' -> if (!anchorIsCall && i + 1 < text.length &&
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

    private fun countHanLiterals(text: String, kind: Kind): Int = when (kind) {
        Kind.COMPONENT -> {
            // 前三栏已经覆盖的字符区间（半开区间 [start, end)）：
            // 落进去的字面量已经入过账，这里只记漏下来的
            val covered: List<Pair<Int, Int>> = Kind.values()
                .filter { it != Kind.COMPONENT }
                .flatMap { other ->
                    other.anchor.findAll(text).map { m ->
                        val from = m.range.last + 1
                        from to (from + expressionAt(text, from, other).length)
                    }
                }
            // **按字符区间去重**再计数：`LbDialog(title = …, confirm = LbDialogAction(…))`
            // 这种嵌套调用，外层实参切片与内层切片会各含同一条字符串一次。
            // 按锚点求和的话，"把一颗按钮换成两颗 Lb 组件嵌套"就会凭空涨几笔——
            // 涨的是量具的重复计数，不是债（§26.3 那次 247→246 的教训反过来同样成立）。
            val ranges = LinkedHashSet<Pair<Int, Int>>()
            Kind.COMPONENT.anchor.findAll(text).forEach { m ->
                val from = m.range.last + 1
                HAN_LITERAL.findAll(expressionAt(text, from, Kind.COMPONENT)).forEach { lit ->
                    val start = from + lit.range.first
                    val end = from + lit.range.last + 1
                    if (covered.none { (lo, hi) -> lo <= start && end <= hi }) ranges += start to end
                }
            }
            ranges.size
        }
        else -> kind.anchor.findAll(text).sumOf { match ->
            HAN_LITERAL.findAll(expressionAt(text, match.range.last + 1, kind)).count()
        }
    }

    /**
     * 实测基线。**数字来自这把尺对 app/src/main 的一次实扫**，不是照抄复核报告的 101。
     *
     * 三把尺的关系（都留档，不然下一次又有人拿最小的那个数当全量）：
     * - 复核报告的 **101**：只数 `Text("中文`，是下界；
     * - 上一轮的正则 **209**：多认 `Text(text = "中文…")` 与跨行写法，仍看不见
     *   `Text(text = if (…) "中文" else "中文")`，还是下界；
     * - 换成按括号配对取整段实参 → TEXT **254**；搬掉 4 处后 **250**。
     * - §6.1 把知识库页那张自造空态卡换成共用组件，又搬掉 3 处（标题、指路文案、底部那颗
     *   "新建知识库"）→ **247**。DESC / STATE 两栏这次没动。
     * - §6.1 把五颗首页组件搬进 core/designsystem 时，TEXT 掉到 **246**——
     *   **这一条不是还债**：掉的那处是「帮你更自然地表达」，它只是从 `Text("…")`
     *   变成了 `LbTopBar(subtitle = "…")`，字符串一个字没动，是锚点 `Text(` 看不见它了。
     *   所以同一次加了 COMPONENT 这一栏（起点 16，全仓实扫），246 + 1 对得上旧的 247。
     * - §6.1 收浮层（11 处 `AlertDialog` → `LbDialog`）之后 **TEXT 209 / COMPONENT 59**。
     *   这次两栏一起动，账要摊开说清：
     *   ① 37 处从 TEXT 挪进 COMPONENT（`Text("取消")` → `LbDialogAction("取消")`），
     *   246 − 37 = 209、16 + 37 = 53 —— 这 37 处**一条都没还**，只是换了形状；
     *   ② COMPONENT 另外 +6 是**以前两栏都看不见的**：写在 `TextButton(onClick = { … })` 里的
     *   提示语（"清空失败，请重试"、"文件已被后台修改，请重新打开"、"没有可用的分享应用"…），
     *   它们搬进 `LbDialogAction(onClick = { … })` 之后才落进实参切片。总数 262 → 268，
     *   **涨的 6 条是量具新看见的既有债，不是这次新塞的**（与 209 → 254 那次同一回事）；
     *   ③ 这一栏改成**按字符区间去重**再计数：嵌套调用（`LbDialog(…, confirm = LbDialogAction(…))`）
     *   外层与内层切片各含同一条字符串一次，按锚点求和会虚报（同一次实扫 85 vs 去重后 59）。
     *   不做这件事的话，"把一颗按钮拆成两颗 Lb 组件"都会让数字涨，那涨的是量具自己。
     * - §6.1 的 Sheet 半边（`PanelModalHost`/`Title`/`Actions` → `LbModalSheet*`）之后
     *   **COMPONENT 59 → 66（+7）、TEXT 不动**。这 7 条全是**既有的**用户可见文案：
     *   「暂停时长」「标记为错误」「取消」×3、「确认」「保存」——原先写在 `PanelModalTitle("…")`
     *   与 `PanelModalActions(confirmLabel = "…")` 这类**非 `Lb` 锚点**里（"取消"还是旧组件的默认实参），
     *   三把老尺一条都看不见；换名进设计系统之后才被量到 ⇒ 涨的是量具新看见的既有债。
     * - §6.4 第一刀（`RecordSentDialog` 与导出 Loading 的自画遮罩并进 `LbModalSheet`）之后
     *   **TEXT 209 → 205、COMPONENT 66 → 69、DESC 仍 12**。摊开说：
     *   ① 3 条从 TEXT 进 COMPONENT（「记录实际发送」「取消」「确认已发送并记录」从 `Text("…")`
     *   变成 `LbModalSheetTitle(…)` / `LbDialogAction(label = …)`）——换形状，没还债；
     *   ② 1 条**真的还掉了**：那颗输入框的提示语进了 `R.string.panel_record_sent_hint`（zh+en）。
     *   起因是守卫量到它"既没文案也没 contentDescription"，而修读屏名要让同一句话用两次——
     *   源码里写两遍是新的维护债，进资源才是收口。合计 275 → 274，减的就是这一条。
     * - §6.1 把五颗首页组件搬进 core/designsystem 时，TEXT 掉到 **246**——
     *   **这一条不是还债**：掉的那处是「帮你更自然地表达」，它只是从 `Text("…")`
     *   变成了 `LbTopBar(subtitle = "…")`，字符串一个字没动，是锚点 `Text(` 看不见它了。
     *   所以同一次加了 COMPONENT 那一栏（起点 **16**，全仓实扫），246 + 1 那条落进新栏 =
     *   原来的 247，总数一笔没少。以后再把文案搬进组件参数，涨的是 COMPONENT，照样红。
     *
     * 注意上面那串 254 / 250 / 247 是**历史**，不是现在值：现在值只有一处真源，
     * 就是下面 `budget` 里那几个数（`the budget still reflects reality` 那格保证两边不一致时报红）。
     * 所以别往这段说明里续抄数字——历史可以记，读数一律看常量。
     *
     * DESC 这一栏要单独记一笔：本轮第一次改尺时**赋值型锚点的切片被内层括号截断了**——
     * `contentDescription = if (expanded) "收起" else "展开"` 里那对 if 条件括号提前收尾，
     * 于是"搬掉两处中文之后 DESC 一个没变"。那个"没变"本身就是尺子还在漏的证据。
     * 修好之后 DESC = **12**（而修之前那次量到的 10 同样是下界）。
     *
     * 结论：换尺让数字变大不是"债涨了"，是量到了以前漏的。棘轮照旧只许往下走。
     */
    private val budget = mapOf(
        Kind.TEXT to 205,
        Kind.DESC to 12,
        Kind.STATE to 0,
        Kind.COMPONENT to 69
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

            // 赋值型锚点 + 内层条件括号：第一版的切片在这里提前收尾，把两处中文漏成了 0。
            // 没这一格的话，"刚搬掉两处而预算一个没动"这种矛盾根本不会被发现。
            File(tmp, "E.kt").writeText(
                """
                package x
                val mod = Modifier.semantics { contentDescription = if (open) "收起" else "展开" }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "隔着 if 条件括号的 contentDescription 也要数到（C 的 1 处 + E 的 2 处）",
                3, countIn(tmp, Kind.DESC)
            )

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

            // COMPONENT 这一栏是新加的，两头都要有反例：
            //  ① 具名实参里的中文必须看见（搬家那次就是从这里漏出去的）；
            //  ② 同一颗组件的 content 槽里套着 Text 时，那两处只能记一次（记 TEXT 名下）。
            File(tmp, "F.kt").writeText(
                """
                package x
                import androidx.compose.material3.Text
                @Composable fun F() {
                    LbTopBar(
                        title = "页面标题",
                        trailing = { Text("里面" + "那颗字") }
                    )
                }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "组件具名实参里的中文要入账，套在里面的 Text 不许重复记",
                1, countIn(tmp, Kind.COMPONENT)
            )
            assertEquals(
                "F 里 Text 的两处仍归 TEXT 栏（4 + 2）",
                6, countIn(tmp, Kind.TEXT)
            )

            // 恒真的另一种形状：如果锚点其实是 `Text(`，那 F 的 COMPONENT 会报 0——
            // 上面那格因此同时是"锚点写错成什么样"的探测器。
            File(tmp, "G.kt").writeText(
                """
                package x
                @Composable fun G() { OtherCell(label = "别人的组件不算") }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "非 Lb 前缀的组件仍在盲区里：这一格写明它「不数」，而不是假装数到了",
                1, countIn(tmp, Kind.COMPONENT)
            )

            // 去重这一支必须有牙：`LbDialog(title = "外层标题", confirm = LbDialogAction("内层按钮"))`
            // 里，外层实参切片同时含两条串、内层切片又含 "内层按钮" 一次。
            // 按锚点求和 ⇒ 这里会数到 3（合计 4）；按字符区间去重 ⇒ 数到 2（合计 3）。
            File(tmp, "H.kt").writeText(
                """
                package x
                @Composable fun H() {
                    LbDialog(
                        title = "外层标题",
                        confirm = LbDialogAction(label = "内层按钮", onClick = {})
                    )
                }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "嵌套的两层 Lb 实参不许把同一条字符串记两遍（F 1 条 + H 2 条）",
                3, countIn(tmp, Kind.COMPONENT)
            )
        } finally {
            tmp.deleteRecursively()
        }
    }
}
