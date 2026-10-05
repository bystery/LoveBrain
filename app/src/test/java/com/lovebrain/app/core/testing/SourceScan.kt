package com.lovebrain.app.core.testing

/**
 * 源码级的"尺"只许有这一份实现（此前是 python 一份、测试里两份，各自漂移）。
 *
 * 提供三件事，都是**按形状认**的闸必须做对的三件：
 * 1. [maskComments]：把 `//`、`/* */`、KDoc 的字符换成**空格**，长度不变 ⇒
 *    偏移还能映射回原文件，行号可信（删注释会挪行号，见坑表 94 那一族）；
 *    字符串里的 `//`（比如 URL）不算注释。
 * 2. [argumentSlice]：从某个 `.foo(` 的左括号开始**按括号配对**取实参，
 *    而不是 `[^)]*` —— 后者碰到 `if (x) A else B` 会在 `if` 的右括号处断掉（坑表 95）。
 * 3. [enclosingCall]：往回找**包住这个偏移的最内层组合调用**，
 *    用来认"热区与视觉分两层"那种形状（外层可点、里层才涂底色）——
 *    只看 `clickable` 自己那条链的尺会看不见它（`_temp/scan_primary_buttons.py` 就是这么
 *    从 17 掉到 15 的：一处债没还，数字自己降了）。
 */
object SourceScan {

    /** 本仓库的品牌色词（`core/designsystem/Color.kt`）。要加新词就在这里加，别散在判据里。 */
    val BRAND_TOKENS = listOf("PrimaryDark", "PrimaryLight", "Primary")

    /** 注释与 KDoc 掩成空格，长度与换行位置一字不变。 */
    fun maskComments(src: String): String {
        val out = StringBuilder(src)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            when {
                c == '"' -> i = skipString(src, i)
                c == '\'' -> i = skipChar(src, i)
                c == '/' && i + 1 < n && src[i + 1] == '/' -> {
                    while (i < n && src[i] != '\n') {
                        out[i] = ' '
                        i++
                    }
                }
                c == '/' && i + 1 < n && src[i + 1] == '*' -> {
                    i = maskBlock(src, out, i)
                }
                else -> i++
            }
        }
        return out.toString()
    }

    private fun skipString(src: String, start: Int): Int {
        // 三引号串与转义都要认，否则 `"""` 里的 // 会被当注释
        if (src.startsWith("\"\"\"", start)) {
            var i = start + 3
            while (i < src.length && !src.startsWith("\"\"\"", i)) i++
            return minOf(i + 3, src.length)
        }
        var i = start + 1
        while (i < src.length) {
            when (src[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return i
    }

    private fun skipChar(src: String, start: Int): Int {
        var i = start + 1
        while (i < src.length) {
            when (src[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                else -> i++
            }
        }
        return i
    }

    /** Kotlin 的块注释可以嵌套（KDoc 里常写 `/* … */`），所以要按深度走 */
    private fun maskBlock(src: String, out: StringBuilder, start: Int): Int {
        var i = start
        var depth = 0
        while (i < src.length) {
            if (src.startsWith("/*", i)) {
                depth++
                i += 2
            } else if (src.startsWith("*/", i)) {
                depth--
                i += 2
            } else {
                if (src[i] != '\n') out[i] = ' '
                i++
            }
            if (depth == 0) return i
        }
        return i
    }

    /** 从 `open`（左括号下标）按配对取实参片段，返回右括号**之后**的下标 */
    fun closeIndexOf(masked: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < masked.length) {
            when (masked[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        return masked.length
    }

    /** `.background(` 的实参里出现品牌色词 ⇒ 这一处是"手绘的品牌底" */
    fun brandedBackgroundOffsets(masked: String): List<Int> {
        val hits = mutableListOf<Int>()
        var i = 0
        val needle = ".background("
        while (true) {
            val at = masked.indexOf(needle, i)
            if (at < 0) break
            val open = at + needle.length - 1
            val close = closeIndexOf(masked, open)
            val arg = masked.substring(open + 1, close - 1)
            if (BRAND_TOKENS.any { Regex("\\b" + it + "\\b").containsMatchIn(arg) }) hits.add(at)
            i = close
        }
        return hits
    }

    /** 所有 `.clickable` / `.clickable(` 的起始下标 */
    fun clickableOffsets(masked: String): List<Int> =
        Regex("""\.\s*clickable\b""").findAll(masked).map { it.range.first }.toList()

    /**
     * 沿括号栈往回走，找**包住** [offset] 的那些左括号（栈里从内到外的顺序）。
     *
     * 不用正则再扫一遍"名字+左括号"：那会被实参里的 `Foo(` 之类骗到。
     * 栈扫描一次就能给出所有候选，最内层就是栈里最后一个仍然包住 offset 的。
     */
    private fun enclosingParens(masked: String, offset: Int): List<Int> {
        val stack = ArrayDeque<Int>()
        for (i in 0 until minOf(offset, masked.length)) {
            when (masked[i]) {
                '(' -> stack.addLast(i)
                ')' -> if (stack.isNotEmpty()) stack.removeLast()
            }
        }
        return stack.toList()
    }

    /**
     * 一次调用的**子树终点**：Compose 的尾随 lambda 不在圆括号里，
     * `Box( … ) { …涂着底色… }` 的内容在配对右括号**之后**——
     * 只把配对右括号当终点，就看不见"热区与视觉分两层"那种形状
     * （这正是本机第一次跑两层对照时量到 0 的原因）。
     */
    fun callEnd(masked: String, open: Int): Int {
        val close = closeIndexOf(masked, open)
        var i = close
        while (i < masked.length && masked[i].isWhitespace()) i++
        if (i >= masked.length || masked[i] != '{') return close
        var depth = 0
        while (i < masked.length) {
            when (masked[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        return masked.length
    }

    /**
     * 包住 [offset] 的**最内层大写开头调用**（`Box(...)` / `Column(...)` / `Row(...)`…），
     * 返回"左括号下标 → 该调用子树的终点（含尾随 lambda）"；找不到就返回 null。
     *
     * ⚠ 第一发这里写成"正则扫所有 `Name(` 再挑最小跨度"，结果**两层形状仍然认不出**：
     * 里层 `Box(` 的跨度在 `modifier = Modifier` 那里就断了，它根本不是包住 clickable 的那一个，
     * 而按"最小跨度"挑就会把这种半截的当候选（正则版的 `[^)]*` 老坑，坑表 95 的又一变体）。
     * 现在用括号栈：栈里剩下的左括号天然就是"真正包住 offset 的那些"，从内往外找大写调用名。
     */
    fun enclosingCall(masked: String, offset: Int): Pair<Int, Int>? {
        for (open in enclosingParens(masked, offset)) {
            val head = masked.substring(0, open).trimEnd()
            var i = head.length
            while (i > 0 && (head[i - 1].isLetterOrDigit() || head[i - 1] == '_')) i--
            val name = head.substring(i)
            if (name.isNotEmpty() && name[0].isUpperCase() &&
                Regex("""^[A-Z]\w*$""").matches(name)
            ) {
                return open to callEnd(masked, open)
            }
        }
        return null
    }

    /**
     * "自画的品牌底可点控件"：`clickable` 所在的那次组合调用，子树里涂着品牌底。
     *
     * 返回的是**下标对**（clickable 的位置 + 包住它的那次调用的左括号），交给调用方换算行号，
     * 这样判据报出来的站点能直接对着文件看。
     */
    fun actionableBranded(masked: String): List<Pair<Int, Int>> {
        val backgrounds = brandedBackgroundOffsets(masked)
        return clickableOffsets(masked).mapNotNull { click ->
            val call = enclosingCall(masked, click) ?: return@mapNotNull null
            val inside = backgrounds.any { it > call.first && it < call.second }
            if (inside) click to call.first else null
        }
    }

    /** 偏移 → 行号（1 基），供失败信息点名用 */
    fun lineOf(masked: String, offset: Int): Int =
        masked.take(offset).count { it == '\n' } + 1
}
