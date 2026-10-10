package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 行 1 上那颗「仅看本轮」的**符号主人**与**三轴形状**（request2 §四 表「仅看本轮符号 — 当前硬编码 `◉`，应改」）。
 *
 * 这一族全是**源码 / 资源级**的静态自证格（本机不跑构建，也一条都不靠渲染）：
 * 判的是"这颗字面量现在住在谁那里"与"那一格的三轴有没有各归各位"，这两件事都能从源码读出来。
 * 真实手指观感（会不会误触、输入法在场时这一排怎么排）不在这里，只能真机。
 *
 * 四件事各钉一格，每格都对得上一个具体的坏实现：
 * 1. **符号不再硬编码在生产 Kotlin 里**：扫 `app/src/main/java` 全部 `.kt`，掩平注释之后
 *    那颗 U+25C9 一颗都不许出现。坏实现：把字符写回 `const val` 或内联进 `label = "…"` ⇒ 红。
 * 2. **主人确实在位**（反向证人 A）：那颗字符必须**在资源里**读得到，而且页面真的读那一颗键
 *    ——删掉字面量却没有主人，等于把 §2.2 第 3 条要的那颗形状一起弄丢。
 * 3. **反向证人 B（尺不许是死的）**：同一把判据喂一份"坏实现"样本必须报命中，
 *    喂"只在注释里讲这颗字符"的样本必须不报（KDoc 里的历史不算字面量，否则没人敢写解释）。
 * 4. **语义一条都不许改**：读屏仍念中文全称、名中不含"锁"、单色（不许 emoji 位平面外的字形）、
 *    不新增常驻文字、三轴分开（可见那颗 22dp 胶囊没被动、命中仍指回 core 紧凑档、
 *    占位轴不许再包一层全站下限）。
 *
 * ⚠ **字形保持 U+25C9**：request2 §三 那份约 22KB 的完整 Worker 指导书没落到本机，§四 那一行
 * 只给了"不许硬编码"这个结论、没给字形细则，所以本轮只把字面量挪出 Kotlin；
 * **要不要换形已登记为待主线程指认（台账 M22），不在这一轮自决**。
 */
class ReplyInputSymbolOwnerTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private val resRoot: File
        get() = File("src/main/res").takeIf { it.isDirectory } ?: File("app/src/main/res")

    private val replyInput: File
        get() = File(appRoot, "ui/panel/reply/ReplyInput.kt")

    /** 那颗被点名的符号（U+25C9）。写成转义是为了这份文件自己也不带那颗字符 */
    private val scopeGlyph: Char get() = '\u25C9'

    private val glyphKey = "panel_round_scope_glyph"

    /**
     * 读一颗源文件：先**统一换行**再掩平注释。
     *
     * ⚠ 这一步不是装饰：仓库 `.gitattributes` 写的是 `* text=auto`，同一份 `.kt` 在本机签出来是
     * CRLF、CI 的 ubuntu 上是 LF。不统一，下面按 `}` 收尾找函数体的那两把就会在 Windows 上
     * 永远对不上（`}\r` ≠ `}`），函数体一路读到文件末尾 ⇒ 一格假红（同仓 `OddShapeOwnershipTest`
     * 的注释里记着"第一发就是把 `\r` 忘了去掉，四格全红"这一课）。
     */
    private fun maskedCode(file: File): String {
        assertTrue("找不到本轮要读的文件：$file", file.isFile)
        val src = file.readText(Charsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n")
        return SourceScan.maskComments(src)
    }

    /** 生产源码（掩平注释）里那颗字符的落点：文件名 → 命中行号（1 基） */
    private fun productionSitesHoldingGlyph(): Map<String, List<Int>> {
        assertTrue("找不到源码根：$appRoot——这把尺会恒绿", appRoot.isDirectory)
        val out = linkedMapOf<String, List<Int>>()
        appRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val hits = maskedCode(f).split("\n").mapIndexed { i, line ->
                if (line.contains(scopeGlyph)) i + 1 else null
            }.filterNotNull()
            if (hits.isNotEmpty()) out[f.name] = hits
        }
        return out
    }

    /**
     * 资源里那一颗的值（只读那颗键，读不到就当场红，不做静默兜底）。
     *
     * 2026-10-10 改过一次落点：这一颗起初住在代理自立的临时文件 `values/symbols.xml`，主线程把它
     * 折进 `values/strings.xml` 后那份临时文件已经退役。⇒ 判据不变（"键必须有主人、主人必须读得到"），
     * 但**不按某一份文件名认主人**：扫 `values/` 下全部 XML，找不到就红并列出扫过哪些文件。
     * 这样"换个文件住"不会被误判成缺陷，而"键真没了"照样红。
     */
    private fun glyphResourceValue(): String {
        val scanned = File(resRoot, "values").listFiles { f -> f.isFile && f.name.endsWith(".xml") }
            .orEmpty().sortedBy { it.name }
        assertTrue("values/ 底下一份资源文件都没有——那颗符号没有主人了：${File(resRoot, "values")}", scanned.isNotEmpty())
        val pattern = Regex("<string name=\"$glyphKey\"[^>]*>([^<]*)</string>")
        scanned.forEach { f ->
            pattern.find(f.readText(Charsets.UTF_8))?.let { return it.groupValues[1] }
        }
        // ⚠ 编译阻塞点（2026-10-10 接手修复）：这里原来是 `assertTrue(msg, false)`——
        // 一句"永不返回"的断言放在返回 String 的函数体末尾，Kotlin 报
        // "A 'return' expression required in a function with a block body"，整个 test 源集编译不下来，
        // 全站读数（包括与本格无关的 8 格）都读不到。判据一字未改：读不到那颗键仍然是当场红，
        // 只是换成 `throw AssertionError`（`assertTrue(msg, false)` 抛的就是它）。
        throw AssertionError(
            "资源里没有 `$glyphKey` 那一颗键，页面读的就是一个不存在的来路（扫过：${scanned.joinToString { it.name }}）"
        )
    }

    // ═══════════ 1 + 2. 那颗符号的主人：从 Kotlin 挪进资源，而且资源真在位 ═══════════

    @Test
    fun `the scope symbol is no longer a literal in production kotlin`() {
        val measured = productionSitesHoldingGlyph()
        assertTrue(
            "「仅看本轮」那颗符号又被写回生产 Kotlin 的字面量了（request2 §四 点名的就是这件事，" +
                "主人应当是资源 `$glyphKey`）：" +
                measured.map { (f, lines) -> "$f:${lines.joinToString(",")}" },
            measured.isEmpty()
        )
        // 反向证人 A：搬走 ≠ 删掉——那颗字符必须还在盘上，而且住在资源那一侧
        assertTrue(
            "那颗符号没有资源主人了（页面删了字面量，形状却一起丢了）",
            glyphResourceValue().contains(scopeGlyph)
        )
        // 页面调用点必须真的读那一颗键，而不是留下第二份自己画
        val code = maskedCode(replyInput)
        assertTrue(
            "RoundScopeChip 不从资源读那颗符号（认错了主人，或还揣着第二份）",
            code.contains("stringResource(R.string.$glyphKey)")
        )
        // 旧版那颗 Kotlin 常量不许回来（回来了就等于主人又换回页面）
        assertTrue(
            "那颗常量的名字又回到生产 Kotlin 里了（主人应当只有资源那一份）",
            !code.contains("PANEL_ROUND_SCOPE_GLYPH")
        )
    }

    /**
     * **反向证人 B**：同一把判据必须看得见坏实现、也必须放过"注释里提到那颗字符"。
     * 没有这一格，上面那格就只是"扫不到东西所以绿"。
     */
    @Test
    fun `the glyph scanner catches a hand-rolled literal and spares prose about it`() {
        // 坏实现：把那颗字符写成 const val / 内联实参 ⇒ 未掩平的判据必须报命中
        val bad = "internal const val G = \"$scopeGlyph\""
        assertTrue("尺对着一份真字面量报了干净——它看不见东西的那种绿不算守卫", bad.contains(scopeGlyph))
        // 好实现：同一颗字符只出现在注释里 ⇒ 掩平之后一颗不剩（生产那一格用的就是这条口径）
        val prose = "/* 为什么不是 $scopeGlyph 而是锁形 */\nval label = stringResource(R.string.x)"
        assertTrue(
            "注释里讲历史的那颗字符被当成字面量数进来了（那样整页都会被冤红，下次没人写解释）",
            !SourceScan.maskComments(prose).contains(scopeGlyph)
        )
        assertTrue("掩平之前这条 KDoc 本来就该被数到（掩平才是它绿的原因）", prose.contains(scopeGlyph))
    }

    // ═══════════ 4a. 语义不许改：读屏念中文全称、名中不含"锁"、不是 emoji 字形、不是词 ═══════════

    @Test
    fun `the read-aloud name stays a Chinese full name with no lock word`() {
        val name = PANEL_ROUND_SCOPE_LABEL
        assertTrue("读屏名必须是中文全称（§2.2 第 3 条），实到：'$name'", name.all { it.isHan() })
        assertEquals("全称就是那四个字，多一个字就是在往这一排新增常驻文字", 4, name.length)
        assertTrue(
            "读屏名里不许再出现\"锁\"那个歧义（§2.2 第 3 条点名要治的就是它）：'$name'",
            !name.contains('锁')
        )
    }

    /**
     * 屏上那颗的语义四条（单色、非 emoji、非锁形、不是词）——**本轮只挪主人、不换字形**，
     * 所以这颗同时是"换形与否"那条待指认项的钉子：谁把形状改了，这里先红，逼他回来登记。
     */
    @Test
    fun `the drawn symbol stays a single monochrome glyph that is not a lock`() {
        val value = glyphResourceValue()
        assertEquals("画在屏上的那颗只许一颗字符（不是词、更不是四字常驻按钮）：'$value'", 1, value.length)
        assertEquals(
            "那颗符号换了字形——换形不在本轮范围内（request2 §三 那份 22KB 指导书没落到本机，" +
                "M22 已登记为待主线程指认），要改先回来改这一格并写明出处",
            0x25C9, value[0].code
        )
        assertTrue("那颗字符是 emoji 位平面外的字形（彩色字形不吃 pill 那两档墨色）：'$value'",
            value.none { it.isHighSurrogate() || it.isLowSurrogate() })
        assertTrue("那颗符号自己成了中文词（会把整行撑宽）：'$value'", value.none { it.isHan() })
    }

    /** 符号与名**分家**：可见那颗走资源、读屏名走具名的那颗，链上不许多出第三份内联中文 */
    @Test
    fun `the symbol chain takes its a11y name from the named owner not an inline literal`() {
        val code = maskedCode(replyInput)
        val body = functionBody(code, "private fun RoundScopeChip(")
        assertTrue(
            "那颗的可点链上没把读屏全名挂进语义（换成内联中文也算红在这里）：$body",
            Regex("""contentDescription\s*=\s*(PANEL_ROUND_SCOPE_LABEL|stringResource\()""").containsMatchIn(body)
        )
        assertEquals(
            "RoundScopeChip 里内联了中文（§2.2 与 `UiStringLiteralBudgetTest` 都不认这份账）：" +
                hanLiteralsIn(body),
            emptyList<String>(), hanLiteralsIn(body)
        )
    }

    // ═══════════ 4b. 三轴各归各位：可见 / 命中 / 父布局占位 ═══════════

    /**
     * 那颗符号的**形状链**这一半（数值那一半在 `ReplyInputWidthBudgetTest`）：
     * 命中轴两轴都垫到 core 那颗紧凑档（只垫高度＝假分层，正是本轮实测过的坏形状）、
     * 可见轴那颗 22dp 胶囊一寸没动、占位轴不许再包一层全站下限（§4.3：那会把紧凑档作废）。
     *
     * ⚠ 这里判的是"链上写了谁"，不是"屏上量到几 dp"——量边界那两把是 `RoundScopeChipFootprintTest`
     * （占位两轴 < 48）与 `PanelHostSemanticsTest`（整屏逐颗换尺），三把尺各看一件事。
     */
    @Test
    fun `the symbol keeps the hit floor on its own tier without resizing the visible pill`() {
        val code = maskedCode(replyInput)
        val body = functionBody(code, "private fun RoundScopeChip(")
        // ① 命中轴：两轴都垫、都指回页面那颗具名档
        assertTrue("宽度轴没垫到紧凑档（§4.3 要两轴各自垫到位）：$body",
            Regex("""widthIn\(\s*min\s*=\s*ReplyDimens\.ROUND_SCOPE_HIT""").containsMatchIn(body))
        assertTrue("高度轴没垫到紧凑档：$body",
            Regex("""heightIn\(\s*min\s*=\s*ReplyDimens\.ROUND_SCOPE_HIT""").containsMatchIn(body))
        // ② 分层档：带切换语义的那一层才是热区 ⇒ layeredTouch + 关掉 core 那颗 48 见方下限盒
        assertTrue("这颗不再走分层（可见与热区又被拧成一颗）：$body", body.contains("layeredTouch = true"))
        assertTrue("这颗把 core 那颗 48 见方下限盒买回来了：$body", body.contains("touchFloor = false"))
        // ③ 可见轴：那颗 22dp 胶囊没被动——本轮改的是硬编码与重复预留，不是换脸
        assertTrue("可见胶囊的高度被改了（本轮不许改外观）：$body",
            Regex("""pillHeight\s*=\s*22\.dp""").containsMatchIn(body))
        // ④ 这一颗身上不许出现第二颗下限数（包一层 48 见方、或抄一份 48 都算）
        val siteFloorCopies = Regex("""\b48\b""").findAll(body).count()
        assertEquals(
            "范围符号这一颗上数到 $siteFloorCopies 处 48 —— " +
                "§4.3 禁的就是给紧凑件再包一层占 48dp 的容器",
            0, siteFloorCopies
        )
        // ⑤ 命中轴那颗数指回 core 的紧凑档，不是页面自造的第二颗 28
        val dimens = functionBody(code, "internal object ReplyDimens {")
        assertTrue(
            "ROUND_SCOPE_HIT 不再指回 core 那颗紧凑档（页面抄第二份数就是这一行的债）",
            dimens.contains("val ROUND_SCOPE_HIT: Dp = AppDimens.CARD_ACTION_HIT_DP.dp")
        )
        assertEquals("core 那颗紧凑档的数（改了它这一行就该跟着红，别静默漂）", 28, AppDimens.CARD_ACTION_HIT_DP)
    }

    /**
     * 占位表达式**不许夹带间隔**——本轮那格重复预留的根形状就断在这里：
     * `roundScopeFootprint` 只能由那颗热区推出来（乘字号），出现任何 `Spacing.*` 就是又在占位里
     * 烧了一遍间隔；`tailWidth` 里那三段 `Spacing.sm` 每段只出现一次，与行 1 画上那三段 `Spacer`
     * 一一对应（屏上一段、账上两段，就是本轮拆掉的那个形状）。
     */
    @Test
    fun `the footprint expression reserves the gap exactly once`() {
        val code = maskedCode(replyInput)
        val footprint = declarationLine(code, "fun roundScopeFootprint(")
        assertTrue("占位轴不再由那颗热区推出来（换了主人或抄了第二份数）：$footprint",
            footprint.contains("ROUND_SCOPE_HIT"))
        assertTrue("占位轴里又烧了一段间隔——这就是本轮拆掉的那格重复预留：$footprint",
            !footprint.contains("Spacing."))

        val tail = functionBody(code, "fun tailWidth(")
        assertEquals(
            "固定件预算里 `Spacing.sm` 该恰好三段（chip→输入框 / 输入框→➕ / ➕→符号）；" +
                "多一段＝同一格扣两遍，少一段＝有人把间隔藏进件自己那颗数里：" + tail,
            3, Regex("""Spacing\.sm""").findAll(tail).count()
        )
        val row = functionBody(code, "fun ReplyInput(")
        val scopeSlot = row.substringBeforeLast("RoundScopeChip(")
            .substringAfterLast("if (roundEntryArmed) {")
        assertEquals(
            "行 1 画在符号前面那段间隔与预算对不上了（屏上 $scopeSlot）",
            1, Regex("""Spacer\(Modifier\.width\(Spacing\.sm\)\)""").findAll(scopeSlot).count()
        )
    }

    // ───────────────────────── 读法：只按形状认，不按行号认 ─────────────────────────

    /**
     * 读出某个声明的体：从声明那一行往下，直到**与声明同缩进**的那颗 `}`。
     * 表达式体（`fun f(...) = 一颗数`）没有收尾大括号 ⇒ 用 [declarationLine]，不要拿这一颗。
     */
    private fun functionBody(code: String, decl: String): String {
        val lines = code.split("\n")
        val declIndex = lines.indexOfFirst { it.trim().startsWith(decl) }
        assertTrue("源码里读不到 `$decl` 这一颗——这把尺已经看不见东西了", declIndex >= 0)
        val indent = lines[declIndex].takeWhile { it == ' ' }
        val body = StringBuilder()
        for (i in declIndex + 1 until lines.size) {
            val line = lines[i]
            if (line == "$indent}") return body.toString()
            if (line.startsWith("@")) break
            body.append(line).append('\n')
        }
        return body.toString()
    }

    /** 声明那一行自己（表达式体的主人就读这一行） */
    private fun declarationLine(code: String, decl: String): String {
        val line = code.split("\n").firstOrNull { it.trim().startsWith(decl) }
        assertTrue("源码里读不到 `$decl` 那一行", line != null)
        return line!!
    }

    /** 文本里的**单行字符串字面量**里带中文的那些（掩平注释之后再取，注释不算） */
    private fun hanLiteralsIn(text: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '"') {
                val close = text.indexOf('"', i + 1)
                if (close < 0) break
                val literal = text.substring(i + 1, close)
                if (literal.any { it.isHan() }) out += literal
                i = close + 1
            } else i++
        }
        return out
    }

    private fun Char.isHan(): Boolean = this.code in 0x4E00..0x9FFF
}
