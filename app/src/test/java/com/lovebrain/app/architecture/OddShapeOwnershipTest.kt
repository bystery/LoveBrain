package com.lovebrain.app.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 结构门禁：拦的是"只在某一个页面看起来不一样的按钮/卡片"——同一类控件各页各画一颗，
 * 设计系统从此管不到它们，改一次样式要翻遍所有页面，而这正是各页长相开始分叉的起点。
 *
 * 为什么要有这把尺：组件清单管的是"该有谁"，管不到"页面自己又画了一颗"。
 * 接这把尺之前全树量到 39 颗异形（`hand/…`、`panel/…` 各处），那个数只存在一段一次性脚本里——
 * **脚本不进门禁，数字就会随重构腐烂**：下一颗自画胶囊不会让任何东西变红。
 *
 * ⚠ 这把尺数的是**形状**，不是命名。判定规则（[classify]）：
 * 函数体里出现任何自画容器或自挂交互（`Box(`/`Column(`/`Row(`/`Card(`/`Surface(`/`Dialog(`/
 * `IconButton(`/`.background(`/`.border(`/`clickable`/`selectable`/`BasicTextField`）
 * ⇒ 它是异形；否则它必须把形状交给一颗看得见的主人才算**委托壳**，主人两档（[hasDesignSystemOwner]）：
 * 调用一颗 `core/designsystem` 的 `Lb*` 组件，或者只调一颗**设计系统自己已经代言的 Material 公开件**
 * 并且体里一颗自己的数都不写（宽度与颜色全指回具名 token）。
 * 所以"把 `ScreenHeader` 改名叫 `LbWhatever`"骗不过这把尺，"壳里偷偷加一个 Box"当场就红。
 *
 * ⚠ 但"名字里有家族词"不等于"它是一颗形状"。**画形状的东西不返回值**：
 * `@Composable private fun rememberSchemeRowState(...): SchemeRowState` 交回的是一个状态对象
 * （屏上没有一格像素出自它），硬按 `Row` 把它当成形状，就会要求它"必须调用一颗 `Lb*` 才算壳"，
 * 分类器当场抛出去 —— 而 `scan()` 是这几格共用的入口，一颗误判能把整串判据一起拖红
 * （这套判据真红过 5 格，就是这么来的）。
 * 所以显式返回类型（非 `Unit`）走第三本账（[expectedStateFactories]），见 [explicitReturnType]。
 * 那不是豁免：交值的函数体里真画了容器，照样落回异形账。
 *
 * 每本账各判一件事，缺一不可：
 * 1. **委托壳清单**逐颗点名：多一颗红、少一颗也红（壳变少通常意味着有人把壳拆了或里面长了东西）；
 * 2. **异形清单**逐颗点名：多一颗红（新异形要显式登记并写清为什么它表达了不同语义），
 *    少一颗也红（归并成功要回来把账改小，否则账本慢慢虚高）；
 * 3. **状态工厂清单**逐颗点名：这条排除是可见的，不是把扫描范围调小；
 * 4. **这把尺看得见东西**：拿一个已知异形（`PanelHeader`）与一个已知委托壳（`ScreenHeader`）
 *    各判一次；两头里任何一头分错，就说明分类器是死的，那种绿不算数。
 * 另有三格专钉判据自己：词边界那一条（真委托不许被冤、真自画不许漏）、剥注释那一条
 * （注释里的字形不算、真代码一行都不许被吞），以及"主人第二档"那一条——它把
 * 只调公开件却不给自己写数的那一颗承认下来，同时用四条反例钉住它**不是**"调用即合格"
 * （自己写宽度、自己调颜色、名单外的件、自己拼 Modifier 链，四种都仍要红）。
 */
class OddShapeOwnershipTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private val designSystemDir get() = File(appRoot, "core/designsystem")

    /** 家族词表："按钮/卡片/行/胶囊"这一类同名族——名字归这一族的声明都要被这把尺扫到 */
    private val familyName = Regex(
        "^[ \\t]*(?:private |internal |public )?fun ([A-Za-z0-9_]*" +
            "(?:Button|Card|Row|Chip|Pill|Section|Header|Dialog|Sheet|Metric|Badge|Banner)" +
            "[A-Za-z0-9_]*)[ \\t]*\\("
    )

    /**
     * 自画容器 / 自挂交互：出现任何一条就不是"委托壳"。
     *
     * ⚠ 调用形状必须按**词边界**判，不能按子串判。第一版这里是裸子串，于是
     * `LbDialog(` 命中 tell `Dialog(`、`LbSettingRow(` 命中 tell `Row(`、`SchemeCard(` 命中 `Card(`
     * ——**任何一颗归并成功的设计系统委托都会被误判成异形**，尺会把"修好了"报成"还在欠"。
     * 这个误判光读判据本身看不出来（两处独立复现才被发现），所以判据改完还加了一格反向证人，
     * 见 `delegating calls to design-system widgets are not mistaken for hand-drawn ones`。
     * 点号开头的（`.background(` 等）保持子串：`Modifier.background(` 本来就该算。
     */
    private val callShapes = listOf(
        "Box", "Column", "Row", "Card", "Surface", "Dialog", "IconButton", "BasicTextField"
    )

    /** 一条 tell 的正则：词边界 + 左括号；预先编好，避免每行重复编译（原始字符串，反斜杠不 escapes） */
    private val callTells = callShapes.map {
        Regex("""(?<![\w.])""" + it + """\s*\(""")
    }

    private val dotTells = listOf(".background(", ".border(", ".clickable", ".selectable")

    /** 体里是否出现任何"自画"信号 */
    private fun hasHandDrawnTell(text: String): Boolean =
        callTells.any { it.containsMatchIn(text) } || dotTells.any { it in text }

    /** 第一档主人：设计系统里那颗 `Lb*` 组件（这把尺最早就只认这一档） */
    private val lbCall = Regex("""(?<![\w.])Lb[A-Za-z0-9_]*\s*\(""")

    /**
     * 第二档主人——只收**设计系统自己已经代言、且没有自有版式身份的装饰件**：
     * 一条细线、一颗转圈。它们不给任何页面换面貌，所以"只在一个页面看起来不一样"那句
     * 判据在它们身上没有可拦的东西。
     *
     * 为什么要有这一档：仓库到今天没有第二颗分隔件（`LbDivider` 全树零命中），
     * Material 的 `HorizontalDivider` 就是组内那条 1dp 线唯一的主人；`core/designsystem` 自己
     * 也照这一句用它（页头那条分割线）。按原来的口径，一颗"只把 `AppDimens.BORDER_WIDTH_DP` 与
     * `Border` 交给公开件"的 helper 会因为体里没有 `Lb*` 而被判成需要登记的异形——
     * 那不是异形，那是把形状交出去之后剩下的最后一句话。
     *
     * ⚠ 这一档**不是**"调用即合格"，三道条件少一条都不算主人：
     *  ① 调的是名单里那颗（名单每一颗都还要真被 `core/designsystem` 进口，当场核，
     *     由 `a token-only delegation to an adopted primitive is a shell, a self-measured one is not`
     *     那一格钉着；公共件哪天不再代言它，页面这一处立刻重新变红）；
     *  ② 体里不许出现任何数字——宽度只能来自 `AppDimens.*` 这种具名 token，
     *     于是 `HorizontalDivider(thickness = 3.dp, …)` 这种"页面自己定粗细"照样没人认领；
     *  ③ 不许 `Color(...)` 现调颜色、不许自己拼 `Modifier` 链——那两样都是"这一页和别页长得不一样"
     *     的起点。
     * 文字与图标**不在**名单里：页面想画一行标题仍然要指回一颗 `Lb*`，这一档不给散写文字开后门。
     */
    private val delegablePrimitives = setOf("HorizontalDivider", "CircularProgressIndicator")

    /** 体里出现的任何数字（`3.dp`、`0.5f`、`0xFF…`）都说明这一处给自己写了数，不算交给主人 */
    private val ownNumber = Regex("""\d""")

    /** 现调颜色 = 这一页自己的颜色，不在具名 token 账上 */
    private val ownColor = Regex("""(?<![\w.])Color\s*\(""")

    /** 自己拼 Modifier 链 = 形状又回到页面手里 */
    private val ownModifier = Regex("""(?<![\w.])Modifier\b""")

    private fun primitiveCall(name: String) = Regex("""(?<![\w.])""" + name + """\s*\(""")

    /** 设计系统今天自己进口的 Material 公开件——第二档主人的资格由它发，不许测试里许愿 */
    private fun designSystemAdoptedPrimitives(): Set<String> {
        val imported = Regex("""import androidx\.compose\.material3\.(\w+)""")
        val files = designSystemDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("读不到 $designSystemDir 里的源文件——这一档主人的资格发不出来了，别当干净", files.isNotEmpty())
        val names = files.flatMap { f ->
            imported.findAll(f.readText(Charsets.UTF_8)).map { it.groupValues[1] }
        }.toSet()
        assertTrue(
            "core/designsystem 里一颗 Material 公开件都没读到（$designSystemDir）：" +
                "这把尺已经看不见公共件代言了谁，那种绿不算数",
            names.isNotEmpty()
        )
        return names
    }

    /** 第二档读法：把具名 token 交给一颗设计系统代言的公开件，也算把形状交给了主人 */
    private fun hasDelegatedPrimitiveOwner(text: String): Boolean =
        delegablePrimitives.any { primitiveCall(it).containsMatchIn(text) } &&
            !ownNumber.containsMatchIn(text) &&
            !ownColor.containsMatchIn(text) &&
            !ownModifier.containsMatchIn(text)

    /** 一颗委托壳的主人：两档之一，都要在体里看得见，不许"存在即可" */
    private fun hasDesignSystemOwner(text: String): Boolean =
        lbCall.containsMatchIn(text) || hasDelegatedPrimitiveOwner(text)

    private enum class Shape { SHELL, HAND_DRAWN }

    /**
     * 剥掉注释与字符串字面量，只留可执行代码——**一趟字符扫描**，不是"先剥一种再剥另一种"的正则串。
     *
     * ⚠ 顺序剥在这仓库里咬过两次，两条都由 `comment stripping keeps every real tell and swallows no code`
     * 那一格当场验（这里刻意不写出那两对注释符号本身——写进块注释里就会让这段说明提前闭合）：
     * - **先剥块注释**（旧写法）：一条行注释里出现块注释的起头，"一路找到下一个结束符为止"就会吃到
     *   很后面那颗真结束符，**中间的真代码整片消失**——尺从"多判一颗"翻成"看不见一颗"，那种绿最难查。
     * - **先剥行注释**：KDoc 里一个 `https://` 会吃掉同一行行尾的块注释结束符，块注释永不闭合，同样吞代码。
     * 一趟扫描没有顺序可言，所以两条都不成立。顺带补上旧写法漏的一处：**行注释里的 `Box(` 以前算一处**
     * （这一格的 KDoc 一直写着"剥块注释与行注释"，实际只剥了块注释），于是有人写一句
     * "这里以前是一颗 Box(" 就能把一颗干净的委托壳冤成异形。
     *
     * 另外三条口径：Kotlin 的块注释可嵌套 ⇒ 带深度计数（旧写法找到第一个结束符就停，会把注释尾巴上的
     * 字形漏进代码）；KDoc 里的代码样本随块注释一起没了 ⇒ 不会凭空多出一处形状声明
     * （旧写法这一条本来就是对的，保留）；换行**保留**，因为 [classify] 靠"与声明同缩进的那颗 `}`"
     * 找函数体终点，行结构塌了就再也对不上。
     * 这份仓库的源文件是 CRLF：剥注释前先归一行尾，否则 `\r` 会留在每行行尾，四格一起红。
     */
    private fun codeLines(text: String): List<String> {
        val src = text.replace("\r\n", "\n").replace("\r", "\n")
        val out = StringBuilder(src.length)
        fun at(pos: Int, needle: String) = src.regionMatches(pos, needle, 0, needle.length)
        var i = 0
        var blockDepth = 0
        var inLineComment = false
        var inRawString = false
        var quote: Char? = null
        while (i < src.length) {
            val c = src[i]
            val q = quote
            when {
                inLineComment -> {
                    out.append(if (c == '\n') '\n' else ' ')
                    if (c == '\n') inLineComment = false
                    i++
                }
                inRawString ->
                    if (at(i, "\"\"\"")) { inRawString = false; out.append("\"\""); i += 3 }
                    else { out.append(if (c == '\n') '\n' else ' '); i++ }
                q != null -> when {
                    c == '\\' -> { out.append("  "); i += 2 }
                    c == q -> { out.append(q).append(q); quote = null; i++ }
                    // 引号没闭合就地收摊：宁可这一行留原样，也不许它把后面整份文件吞成字符串
                    c == '\n' -> { quote = null; out.append('\n'); i++ }
                    else -> { out.append(' '); i++ }
                }
                blockDepth > 0 -> when {
                    at(i, "/*") -> { blockDepth++; out.append("  "); i += 2 }
                    at(i, "*/") -> { blockDepth--; out.append("  "); i += 2 }
                    else -> { out.append(if (c == '\n') '\n' else ' '); i++ }
                }
                at(i, "//") -> { inLineComment = true; out.append("  "); i += 2 }
                at(i, "/*") -> { blockDepth = 1; out.append("  "); i += 2 }
                at(i, "\"\"\"") -> { inRawString = true; out.append("\"\""); i += 3 }
                c == '"' -> { quote = '"'; out.append("\"\""); i++ }
                c == '\'' -> { quote = '\''; out.append("''"); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString().split("\n")
    }

    /** 一段源码按这把尺的读法剥平后的样子（只给判据自证那几格用） */
    private fun stripped(src: String): String = codeLines(src).joinToString("\n")

    /** 声明头：从声明那一行起，往下拼到出现 `{` 或 `=` 为止（最多再看八行） */
    private fun declHead(lines: List<String>, declIndex: Int): String {
        var head = lines[declIndex]
        var k = declIndex
        while (k < lines.size && !head.contains('{') && !head.contains('=')) {
            k++
            if (k - declIndex > 8) break
            head = head + " " + lines[k].trim()
        }
        return head.trim()
    }

    /**
     * 从声明头里取**显式返回类型**；没有、或就是 `Unit` ⇒ null（那就按一颗形状处理）。
     *
     * 括号按配对找，不按"最后一个 `)`"找：参数里 `content: @Composable () -> Unit` 那种内层括号
     * 不能当成参数表的闭合。字符串在 [codeLines] 里已经抹平，所以字面量里的半个括号也不来捣乱。
     * 读不出（跨行到八行以外、表达式体拉太长）⇒ 一律回 null，**保守地继续当形状盯**，
     * 这条判据只会多问一颗，不会放过一颗。
     */
    private fun explicitReturnType(head: String): String? {
        val open = head.indexOf('(')
        if (open < 0) return null
        var depth = 0
        var close = -1
        var i = open
        while (i < head.length) {
            val ch = head[i]
            if (ch == '(') {
                depth++
            } else if (ch == ')') {
                depth--
                if (depth == 0) { close = i; break }
            }
            i++
        }
        if (close < 0) return null
        val brace = head.indexOf('{', close)
        val eq = head.indexOf('=', close)
        var end = head.length
        if (brace >= 0) end = minOf(end, brace)
        if (eq >= 0) end = minOf(end, eq)
        if (end <= close + 1) return null
        val tail = head.substring(close + 1, end).trim()
        if (!tail.startsWith(":")) return null
        return tail.substring(1).trim().takeIf { it.isNotEmpty() && it != "Unit" }
    }

    /** 顶层声明的开头——用来给"没有大括号的表达式体"画终点 */
    private val topLevelDecl =
        Regex("^(private |internal |public )?(fun |class |object |interface |sealed |enum |data class |val |var |const )")

    /**
     * 读出一颗声明的体并判形。
     *
     * `isShape == false`（交值的函数）时**只有自画那一条还管它**：它既然不在屏上画东西，
     * 就谈不上"必须指得出一颗主人"——那半格断言是给形状准备的，拿它去问一个状态工厂，
     * 只能问出一句假话（这套判据真红过 5 格，就是这么来的）。自画信号一律照判，不许豁免。
     */
    private fun classify(lines: List<String>, declIndex: Int, isShape: Boolean): Pair<Shape, String> {
        val indent = lines[declIndex].takeWhile { it == ' ' }
        var i = declIndex + 1
        val body = StringBuilder()
        var closed = false
        while (i < lines.size) {
            val l = lines[i]
            if (l == "$indent}") { closed = true; break }
            if (l.startsWith("@") || topLevelDecl.matches(l)) break
            body.append(l).append('\n'); i++
        }
        assertTrue(
            "函数体既没扫到大括号收尾、也没扫到内容（${lines[declIndex].trim()}）" +
                "——分类器读不动这种形状，宁可失败也别猜",
            closed || body.isNotEmpty()
        )
        val text = body.toString()
        val shape = if (hasHandDrawnTell(text)) Shape.HAND_DRAWN else Shape.SHELL
        if (shape == Shape.SHELL && isShape) {
            assertTrue(
                "被判成委托壳，但体里既没调用任何 Lb* 组件，也没只把具名 token 交给一颗设计系统" +
                    "已经代言的公开件（见 hasDesignSystemOwner 那两档）：${lines[declIndex].trim()}",
                hasDesignSystemOwner(text)
            )
        }
        return shape to text
    }

    /** [factory] = 它交回一个值（状态工厂/计算器），不是屏上一颗形状；仍受"不许自画"那一条管 */
    private data class Site(
        val file: String,
        val name: String,
        val shape: Shape,
        val factory: Boolean
    )

    private fun scan(): List<Site> {
        assertTrue("找不到 designsystem 目录：$designSystemDir", designSystemDir.isDirectory)
        val out = mutableListOf<Site>()
        appRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            if (f.absolutePath.startsWith(designSystemDir.absolutePath)) return@forEach
            val lines = codeLines(f.readText(Charsets.UTF_8))
            for (i in lines.indices) {
                val m = familyName.find(lines[i]) ?: continue
                var j = i - 1
                while (j >= 0 && lines[j].isBlank()) j--
                if (j < 0 || "@Composable" !in lines[j]) continue
                val returnType = explicitReturnType(declHead(lines, i))
                val (shape, _) = classify(lines, i, returnType == null)
                out += Site(f.name, m.groupValues[1], shape, returnType != null)
            }
        }
        return out
    }

    /**
     * 账本来源：**三本账逐条都是拿这把尺自己的判据（[scan] 那套剥注释 + 声明识别 + [classify]）把
     * `app/src/main` 全树重扫一遍抄下来的，不是手数的**。改这三本账唯一合法的姿势是重扫一次再照抄；
     * 任何人复跑这颗套件都应当一字不差对上（对不上就是有人又画了新的一颗）。
     *
     * 实到：**24 颗异形 + 14 颗委托壳 + 1 个交值的状态工厂**（三本账，见下面三颗清单）。
     *
     * 下面只记每一条的进出理由——说了些什么形状，并按日期留一次账面移动前后的颗数。
     *
     * - 2026-10-11（设置页收成三张组外框）：壳这一本 **15→14**，动的三行都是按同一判据重扫出来的——
     *   - `IntentEditorSheet.kt#IntentExpiryChip` **销行**：有效期那三颗单选 chip 连同这颗壳一起被撤下
     *     （编辑器只剩"正文 + 启用"两件事）。这一族有没有复活由 `IntentEditorSheetMergeTest` 逐名钉着，
     *     这把尺这边只负责"账上不许留一颗已经不在屏上的壳"；
     *   - `SettingsIntentEntry.kt#SettingsIntentIntroSheet` **销行**：开启意图不再先弹那一层介绍，
     *     那扇浮层的主体已从生产删掉（离场动画那一族改由 `ModalSheetExitAnimationTest` 与
     *     `SettingsIntentRowTest` 钉），声明本身扫不到了；
     *   - `LoveBrainSettingsContent.kt#SettingsRowDivider` **进账**：组内那条 1dp 分隔线，
     *     体里只把 `AppDimens.BORDER_WIDTH_DP` 与 `Border` 交给 Material 的 `HorizontalDivider`
     *     ——主人走的是上面说的第二档，它不是异形，也不该再长出自己的形状。
     * - 2026-10-08（悬浮设置那一批改动）：壳 **14→15**、异形被加到第 24 颗。为修"意图介绍浮层的挂载
     *   层级"从 `SettingsIntentEntry.kt` 里拆出 `SettingsIntentIntroSheet`，拆出来时壳里多裹了一层自画
     *   `Column` ⇒ 被这把尺认成异形；那层容器在 `LbModalSheet` 里本来就有（卡片就是一棵竖列），
     *   撤掉后这一颗退化成"只把内容交给公共件"的委托壳 ⇒ 进第二本账（见该条注释）。
     *   ⚠ 那一次账面写过"23 颗异形"，对不上它自己那份清单（清单 24 条）——这种对不上的句子
     *   就是"数字靠手抄"的代价。现在这句是实扫读数，异形这一本从头到尾没有增删，24 条逐条与实扫同名。
     * - 2026-10-06：把知识库母版页那张自画卡底（`KnowledgeBaseActivity.kt#KbCard`）也交给同一个主人
     *   `core/designsystem/LbListCard.kt`（列表卡四槽那套 + "描边管静息"；母版页从此只交内容）⇒
     *   异形销行一颗、壳加一颗，那一次账面是 **24 异形 + 13 壳**。
     * - 2026-10-05：把反馈案例那张自绘卡底（`FeedbackCasesScreen.kt#CaseCard`）上提进同一颗公共件 ⇒
     *   页面那一颗退化成委托壳，异形 **25→24**、壳 **12→13**。
     * - 最早记的是 **39 颗异形**，此后陆续归并进公共件、或随功能一起删除（锦囊五颗、首页重做撤下三颗、
     *   砍掉的设置页一颗、点踩面板一颗壳），并新登记页面自画的卡底。2026-10-03 按这把尺对
     *   `app/src/main` 全树重扫过一遍：三本账逐条与实扫对齐，摘掉账上已经扫不到的幽灵、补上自画卡底，
     *   一条都没有靠"把扫描范围缩小"抵掉。
     */
    private val expectedHandDrawn = setOf(
        "AccessibilityDisclosureDialog.kt#AccessibilityDisclosureDialog",
        "AiLoadingRow.kt#AiLoadingRow",
        "HomeComponents.kt#AssistantStatusCard",
        // KnowledgeBaseActivity.kt#KbCard 于 2026-10-06 **移进下面的 expectedShells**：
        // 卡底形状、字阶、行数上限、动作写法全部交回 `LbListCard`，母版页体里没有 `Card(`、
        // 没有 `shadow(`、没有自画 `clickable`，只剩一次 `LbListCard(` 调用（与 CaseCard 同一档）。
        "VectorPills.kt#VectorPill",
        "VectorPills.kt#VectorPillsRow",
        "PanelSuggestionCards.kt#ProfileSuggestionCard",
        "PanelSuggestionCards.kt#StageSuggestionCard",
        "MarkdownText.kt#ListRow",
        "MemoryRefsFeed.kt#MemoryRefsSection",
        "OnboardingOptionCard.kt#OnboardingOptionCard",
        "OngoingSection.kt#OngoingSection",
        "PanelHeader.kt#PanelHeader",
        "ProviderSection.kt#MiniSwitchRow",
        "ProviderSection.kt#ProviderSection",
        "ResultArea.kt#SchemeCardsRow",
        "SchemeCard.kt#SchemeCard",
        // 锦囊整块（含 SuggestResultContent.kt 的五颗结果态形状）已随功能删除 ⇒ 这五条摘掉。
        // 2026-10-01：谈心模板 chip 行从 CounselingPanel.kt 搬进 CounselingTemplateChips.kt，
        // 成为顶层 @Composable 才被这把按"顶层声明"认形状的尺扫到——
        // 它画 Box + Row + 渐隐遮罩，是内容容器不是委托壳。
        "CounselingTemplateChips.kt#CounselingTemplateChips",
        // ── 2026-10-03 首页重做后的形状账 ──────────────────────────────────────
        // HomeFeatureCard / HomeArrowRow / HomeCaptureRow 已销行：首页四格改成一律委托
        // `LbActionCard(iconBlock = GridCell)`，那三颗自画卡底/行随重做整块撤下（形状本身没了，不是并给别人）。
        // 首页状态卡右侧那颗播放/停止控件：48dp 热区点击盒 + 内层 Box 画灯与字形（Canvas 三角/方块）。
        // 主人：`HomeComponents.kt`（`AssistantStatusCard` 内一处调用）。
        // 【真需要自画 / 缺件】core 没有"灯 + 随状态切形的图标 + 48dp 热区"合一的控件，
        // `LbActionCard` 只交图标方块 + 文字，给不出这一格的双态控件。⇒ 留账，缺件补齐再并。
        "HomeComponents.kt#AdvisorControlButton",
        // 「我的想法」那一块（`MessageList.kt#IdeaSection`）已于 2026-10-03 **销行**：
        // 备注换成 `ReplyInput.kt#AdvisorNoteLine` 那一行灰字之后，标题 + 限高内滚列那个形状
        // 整块没了（不是归并给别人，是那个形状本身被撤）。名字里带 Line，不进这把尺的词表。
        // 一条消息的整行：她/我左右两列气泡 + 横向滑动删除 + 长按重排 + 退场动画，全宽那层是手势落点。
        // 主人：`ui/panel/reply/MessageList.kt`（聊天列表那一处调用）。
        // 【真需要自画 / 缺件】全仓没有第二处这种形状：底色挂在**行内那颗**而不是行上，才分得出左右两列；
        // `LbActionCard`/`LbSettingRow` 既没有左右档也不带滑动手势。缺的那一颗叫 `LbBubbleRow`。
        "MessageList.kt#MessageRow",
        // 卡内右下角那颗紧凑动作：28dp 见方的点击盒 + 13dp 字形，名字与 selected 挂在带 clickable
        // 的同一节点上，按压缩放仍用全站那一处 `rememberPressScale`。
        // 主人：`ui/panel/reply/SchemeCollapsedBlock.kt`（收起态三颗动作都走它）。
        // 【欠归并】主人就是 `LbTextAction(icon/iconRes, description, …, glyph = LbTextActionGlyph.Compact)`
        // ——那一档照 `AppDimens.CARD_ACTION_HIT_DP`（28）垫两轴、字形 13dp、`selected`/`enabled`/角色全齐，
        // 与本颗逐条同数；它现在仍在页面里，账上挂着的就是这笔还没做完的归并——别读成"已经并过了"。
        // ⇒ 下一步：三处调用直接换成那颗，销行。
        "SchemeCollapsedBlock.kt#CardCompactAction",
        // 供应商设置页（SettingsProviderEntry.kt）整页已砍 ⇒ SettingsValueRow 随之消失，从账上摘掉。
        // ── 2026-10-03 新登记的自画卡底 ────────────────────────────────────────
        // 反馈案例那张展开卡（`FeedbackCasesScreen.kt#CaseCard`）已于 2026-10-05 **销行**：
        // 那张卡底（自绘 Card + shadow(4) + 整卡 clickable）整块上提进设计系统新主人
        // `core/designsystem/LbListCard.kt`（列表卡那四槽 + "描边管静息"那条口径），
        // 页面那一颗只剩"把内容交给公共件"——形状本身在这一页没了，不是并给别人后仍留在册。
        // ⇒ 这一行移进下面的 expectedShells（异形 25→24、委托壳 12→13）：它的形状主人从此算
        //    core 给得出，不再是"这一页还没有主人"。
        // 捕获页授权状态卡：Card 底 + 1dp 描边，里面包 `LbSettingRow`（标题/说明 + 尾部开关 + 状态点）。
        // 主人：`CaptureAppsScreen.kt`（页首一处）。【真需要自画 / 缺件】带底那层没有主人能一并给出。
        "CaptureAppsScreen.kt#CaptureStatusCard",
        // 捕获应用清单的一行：`LbSettingRow` 委托，但 leading 槽里自画了一颗勾选方框（Box + Icon）。
        // 主人：`CaptureAppsScreen.kt`（清单每行）。【真需要自画 / 缺件】`LbSettingRow.leading` 只收一个可组合位，
        // 勾选框只能在页面自画。⇒ 这颗从委托壳账上**移进异形账**（那颗 Box 让它不再是纯壳）。
        "CaptureAppsScreen.kt#CaptureAppRow",
        // 「加一条消息」那颗：48dp 热区点击盒 + 内层 Box 画圆形实心底与 + 号（随 canAdd 换底色）。
        // 主人：`ReplyInput.kt`（composer 底部一处）。【真需要自画 / 缺件】`LbIconButton` 交不出随 canAdd 切底色的实心圆钮。
        "ReplyInput.kt#AddMessageButton",
        // ── 2026-10-09 主动发展示登记 ──────────────────────────────────────────
        // 主动发候选卡：可复制正文在前 + 常驻角度行 + 时机/先别发/需要准备"默认收起、按需展开"
        // 三段次要行（主动发结果的展示结构），整卡点击=复制正文，策略行是卡内独立点击目标。
        // 主人：`LoveBrainPanelScreen.kt`（`ProactiveResultArea` 的候选列表）。
        // 【真需要自画 / 缺件】`LbListCard` 的四槽（title/status/meta/actions）装不下
        // "正文即主角 + 可展开策略层"这一形状；与 `SchemeCard` 同为结果卡但内容结构不同，不硬套。
        // ⇒ 留账，缺件补齐再并。
        "LoveBrainPanelScreen.kt#ProactiveOptionCard",
        // 悬浮窗设置页分段选择器那条**浅底轨道**：`Row` + `background(SurfaceInset, LoveBrainShape.md)`
        // + 内缩与格间距各一档（数住在 `SettingsRowDimens`，体里不写裸数）。
        // 主人：`ui/panel/settings/SettingsEntryFamily.kt`（这一族两行分段共用这一颗，全站只有这一页有轨道）。
        // 【真需要自画 / 缺件】`LbChip` 只管**一格**的脸（它新落的 `segmentedPill` 档连底都不画），
        // 而"一整条轨道把几格圈成一件事"这个容器在仓库里没有主人：`LbFormGroup` 是表单分组、
        // `settingsEntryGroupCard` 是一组一张的外框，两者都不是行里那一条。
        // ⇒ 留账：哪天第二页也要一条轨道，就上提成设计系统件并回来销这一行。
        "SettingsEntryFamily.kt#SettingsSegmentedPill"
    )

    /**
     * 交回一个值的 `@Composable`（状态工厂/计算器）——**不在屏上画形状，所以两头账都不记它**，
     * 但记在这里：这条排除是可见的、可点的，不是把扫描范围调小。
     *
     * 判据是"有没有显式返回类型"（见 [explicitReturnType]），不是"名字里有没有 `remember`"：
     * 名字会改，返回值改不了它的身份。多一颗 ⇒ 红，要写清它确实不画形状；少一颗 ⇒ 红，回来改小。
     * 它体里真画了容器照样落回异形账（`a value-returning composable that draws is still odd` 那一格盯着）。
     */
    private val expectedStateFactories = setOf(
        // 结果区卡片行的**行级状态工厂**：@Composable 是因为里面用 remember/LaunchedEffect，
        // 交回的是一个状态对象；名字里那颗 `Row` 说的是"哪一行的状态"，不是它自己长什么样子。
        // 它不画容器、不挂交互，只把同一轮的那份状态捞回来。
        "ResultArea.kt#rememberSchemeRowState"
    )

    /**
     * 已经退化成"只转一次参数"的壳——不是异形，但也不该再长出新形状。
     *
     * ⚠ 这一本记的是**壳**，两种主人都算（见 [hasDesignSystemOwner]）：转参数给一颗 `Lb*`，
     * 或只把具名 token 交给一颗设计系统代言的公开件。多一颗、少一颗都红——
     * 所以"承认第二档主人"这件事没有把任何一颗异形变成免登记：异形那一本先看自画信号，
     * 只有本来就不含容器的体才会走到主人这一步。
     */
    private val expectedShells = setOf(
        "CorrectionCenter.kt#CorrectionRecordRow",
        "CounselingTemplateChips.kt#TemplateChip",
        // CaptureAppsScreen.kt#CaptureAppRow **移进异形账**：它的 leading 槽里自画了一颗 Box 勾选框，
        // 不再是"只转一次参数"的纯壳（见 expectedHandDrawn 同一条）。
        // FeedbackCasesScreen.kt#FilterChip 已随点踩原因面板整块删除 ⇒ 摘掉。
        // FeedbackCasesScreen.kt#CaseCard 2026-10-05 **从异形账移进这一本**：卡底（形状、描边、
        // 字阶、行数上限、动作写法）整块上提进 `core/designsystem/LbListCard.kt`，页面那一颗现在
        // 只把内容（首句 / 余文 / 时间 / 展开层）交给公共件——体里没有 `Card(`、没有 `shadow(`、
        // 没有 `clickable`，只剩一次 `LbListCard(` 调用。
        "FeedbackCasesScreen.kt#CaseCard",
        // KnowledgeBaseActivity.kt#KbCard 2026-10-06 **从异形账移进这一本**（与上面那颗同一个主人、
        // 同一条理由）：知识库母版页的卡今天只把 title / status / meta / actions / detail 五槽的内容
        // 交给 `core/designsystem/LbListCard.kt`，形状归公共件——它确实还是"那一颗卡"，但壳里没长出新形状。
        "KnowledgeBaseActivity.kt#KbCard",
        "LoveBrainPanelScreen.kt#KbNoticeBanner",
        // 2026-10-11 登记：设置页收成三张组外框时加的组内行分隔 `SettingsRowDivider()`——
        // 体里只有一句 `HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border)`，
        // 粗细与颜色都不自己写、全部指回具名 token，而仓库到今天没有第二颗分隔件（`LbDivider` 零命中），
        // `core/designsystem` 自己也是这么用这颗 Material 公开件的（页头那条分割线）。
        // ⇒ 它是"把形状交给公开件"的委托壳，走的是 [hasDesignSystemOwner] 的第二档；
        //   它只在这组里被 call 三处，页面没有因此多出第二种线。
        // 反向证人（同一格里钉着）：把这一颗换成页面自画的 `Box(Modifier.height(1.dp).background(Color(0xFF…)))`
        // 立刻落回异形账，换成 `thickness = 3.dp` 这种自己写数的也当场没人认领。
        // 2026-10-13 **换了住址，没换形状**：同一颗 `SettingsRowDivider()` 从页面文件搬进
        //   `SettingsEntryFamily.kt`，因为知识库那一格展开列表的上沿要用**同一条线**
        //   （原型 `.kb-options{border-top}` 与 `.item + .item{border-top}` 同色同宽）。
        //   一条视觉关系不该有两个落点，否则哪天只会改到其中一处。判据与反例跟着搬。
        "SettingsEntryFamily.kt#SettingsRowDivider",
        "OnboardingFlow.kt#OnboardingButton",
        "ProviderSection.kt#ProviderEditDialog",
        "ReplyInput.kt#RoleChip",
        // ReplyInput.kt#RoundScopeChip：只把参数转给 `LbChip`（filled 档），不在页面自画 ⇒ 委托壳。
        "ReplyInput.kt#RoundScopeChip",
        // 2026-10-02：`ResultArea.kt#InputChangedBanner` 从这本账上**销掉**（不是为了让计数对上）：
        // 那颗就地横条整颗删了，"输入已变化 + 按新输入重新生成"那一步交回面板的通知队列
        // （`feature/notice/NoticeBoard` 一条，判据同一条、位置同一处），成功档不再自己画第二条。
        // 生产里已无这颗声明，留着就是虚高——这本账两头都要动。
        "RowAction.kt#RowActionButton",
        "ScreenHeader.kt#ScreenHeader",
        // 2026-10-03：SuggestPanel.kt 改名成 IntentEditorSheet.kt（持续意图那一族随锦囊删除留下、
        // 只是换了宿主与文件名）⇒ 这三颗壳的文件前缀跟着改，声明本身没动。
        "IntentEditorSheet.kt#IntentChip",
        "IntentEditorSheet.kt#IntentEditorDialog",
        // `IntentEditorSheet.kt#IntentExpiryChip` **销行**（按这把尺的实扫，不是为了让计数对上）：
        // 有效期那一族连同这颗"只转一次参数"的壳一起从编辑器里撤了，`app/src/main` 全树现在
        // 扫不到这颗声明。这颗壳欠的债没有跟着消失——"不许再把旧控件接回来"由
        // `IntentEditorSheetMergeTest` 逐名钉着（那颗把 `IntentExpiryChip` 列在退役名单里，
        // 并自带一条"复活写法仍数得到"的证人），这一本只负责不留幽灵。
        // `SettingsIntentEntry.kt#SettingsIntentIntroSheet` 同日（2026-10-11）**销行**：
        // 开启意图不再先弹那一层介绍，那扇浮层的主体已从生产删掉（这颗声明扫不到了）。
        // 它当初登记的判断本身没变——壳里不许长出新形状；形状没了，账就跟着没了。
        // 它原本钉的"退场要播得到"那一半由 `ModalSheetExitAnimationTest` 继续钉另外两扇浮层，
        // "开一扇意图到底要说什么"那一半由 `SettingsIntentRowTest` 与 `SettingsPageSemanticsTest` 钉。
    )

    @Test
    fun `the odd-shape ledger names every hand-drawn site`() {
        val sites = scan()
        assertTrue("扫到 0 处——路径接错了，这把尺会恒绿", sites.isNotEmpty())
        val measured = sites.filter { it.shape == Shape.HAND_DRAWN }
            .map { "${it.file}#${it.name}" }.toSet()
        val grew = (measured - expectedHandDrawn).toSortedSet()
        val repaid = (expectedHandDrawn - measured).toSortedSet()
        assertTrue(
            "异形账本与实到不一致：新增 $grew；已归并/已消失 $repaid。" +
                "新增要写明它表达了什么不同语义，归并要回来把这一项删掉。",
            grew.isEmpty() && repaid.isEmpty()
        )
    }

    @Test
    fun `delegating shells stay exactly the registered ones`() {
        val measured = scan()
            .filter { it.shape == Shape.SHELL && !it.factory }
            .map { "${it.file}#${it.name}" }.toSet()
        assertEquals(
            "委托壳清单变了（壳里长出容器、或壳被拆掉都要显式认账）",
            expectedShells, measured
        )
    }

    /**
     * 第三本账：交值的 `@Composable` 被排除在"形状"之外，这件事本身要点名，不许静默跳过。
     * 有人新写一颗 `remember*State()` / `xxxMetric()` 的状态工厂 ⇒ 这一格红，去看它确实不画东西再登记；
     * 工厂消失了要回来销账。这条就是"分诊不是把尺子砍短"的凭据。
     */
    @Test
    fun `the state factory ledger names every value-returning composable`() {
        val measured = scan().filter { it.factory }.map { "${it.file}#${it.name}" }.toSet()
        assertEquals(
            "交值的 @Composable 清单变了：新登记的要写明它不在屏上画形状，撤掉的要回来把账改小",
            expectedStateFactories, measured
        )
    }

    /**
     * 分诊的判据本身要跑一遍：什么算"交值"、什么不算，都必须看得见。
     * 反向那半句尤其重要——**排除只免掉"必须委托给 Lb*"那一条，不免"不许自画"**。
     */
    @Test
    fun `a value-returning composable that draws is still on the odd-shape hook`() {
        assertEquals("状态工厂交的是值，判得出来",
            "SchemeRowState",
            explicitReturnType("private fun rememberSchemeRowState(generationRoundId: Int): SchemeRowState {"))
        assertEquals("形状不返回值：没有返回类型 ⇒ 照形状办",
            null, explicitReturnType("private fun SchemeCardsRow(state: SchemeRowState) {"))
        assertEquals("显式写 Unit 也仍是形状",
            null, explicitReturnType("private fun TipCategoryHeader(title: String): Unit {"))
        assertEquals("参数里那对括号不许当成参数表收口（`() -> Unit` 是形状参数的类型）",
            null, explicitReturnType(
                "private fun MetricCard(m: Metric, content: @Composable () -> Unit = { Box(m) }) {"))
        assertEquals("表达式体带返回类型也认得出来",
            "Metric", explicitReturnType("private fun statBadge(v: Int): Metric = LbMetric(v)"))
        // 反向证人：交值的函数体里真画了容器 ⇒ 自画那一条照样管它，落回异形账
        val drawing = "    val s = remember { SchemeRowState() }\n    Box(modifier = Modifier.background(c))\n"
        assertTrue("状态工厂里画 Box + 背景，必须仍被判成自画", hasHandDrawnTell(drawing))
        val clean = "    val s = remember(id) { SchemeRowState() }\n    LaunchedEffect(s) { stash(s) }\n"
        assertTrue("干净的状态工厂不该有自画信号", !hasHandDrawnTell(clean))
        // 而且它不许被"必须是委托壳"那一条冤枉：这一格钉的就是"交值的工厂被当成立形状"那个坑
        val lines = listOf(
            "private fun rememberSchemeRowState(generationRoundId: Int): SchemeRowState {",
            clean.trimEnd(), "}"
        )
        assertEquals(Shape.SHELL, classify(lines, 0, isShape = false).first)
    }

    /**
     * 剥注释这条读法要有牙：四种坏法各钉一条，其中两条正是这仓库反复咬人的"分步剥注释"。
     * 每条都成对写——**该看不见的看不见、该看见的必须看见**，只写前一半等于把尺子调瞎。
     */
    @Test
    fun `comment stripping keeps every real tell and swallows no code`() {
        // 1) 行注释里的字形不算一处（旧写法只剥块注释，这一处会被冤成异形）
        assertTrue(
            "行注释里写 Box( 被当成了自画",
            !hasHandDrawnTell(stripped("fun A() {\n    // 这里以前是一颗 Box(\n    LbChip(x)\n}\n"))
        )
        // 2) 块注释里的交互不算一处
        assertTrue(
            "块注释里写 .clickable 被当成了自画",
            !hasHandDrawnTell(stripped("/* 以后再加 .clickable 上去 */\nfun A() { LbChip() }\n"))
        )
        // 3) 行注释里出现块注释起头，不许把后面的真代码整片吞掉
        val swallowed = stripped(
            "fun A() {\n    // 见 /* 说明\n" +
                "    Box(modifier = Modifier.background(c))\n}\n/** 收尾 */\n"
        )
        assertTrue("吞代码了：真代码里的 .background( 读不到 ⇒ 尺瞎\n$swallowed",
            swallowed.contains(".background("))
        // 4) 反向那条顺序坑：KDoc 里的 https:// 不许把块注释截断
        val truncated = stripped(
            "/** 参考 https://developer.android.com/xyz */\nfun A() { Row(m = Modifier.border(w)) }\n"
        )
        assertTrue("KDoc 里的 URL 截断了块注释：后面的 .border( 读不到 ⇒ 尺瞎\n$truncated",
            truncated.contains(".border("))
        // 5) Kotlin 块注释可嵌套：外层没闭合之前，里面那颗 Box( 不算一处
        assertTrue(
            "嵌套注释尾巴上的 Box( 被当成了自画",
            !hasHandDrawnTell(stripped(
                "/* 说明 /* 更深 */ 这里写 Box( 也不算 */\nfun A() { LbChip() }\n"))
        )
        // 6) 字符串与原始字符串里的字形不算，真代码里的必须算
        assertTrue("字符串里的 Box( 被当成了自画",
            !hasHandDrawnTell(stripped("fun A() { val s = \"Press Box( now\"; LbChip() }")))
        assertTrue("原始字符串里的 .clickable 被当成了自画",
            !hasHandDrawnTell(stripped("fun A() { val s = \"\"\".clickable\"\"\"; LbChip() }")))
        assertTrue("真代码里的 Box + 背景必须看得见",
            hasHandDrawnTell(stripped("fun A() {\n    Box(modifier = Modifier.background(c))\n}")))
        // 7) KDoc 里的代码样本不许凭空多出一处形状声明（那会虚涨账本，也可能替一颗不存在的异形背书）
        val docLines = codeLines(
            "/**\n * 例：\n * fun FooCard() {\n *     Box(\n * }\n */\n" +
                "@Composable\nprivate fun BarRow() {\n    LbChip()\n}\n"
        )
        assertEquals(
            "KDoc 样本被当成形状声明了：" + docLines.filter { familyName.containsMatchIn(it) },
            1, docLines.count { familyName.containsMatchIn(it) }
        )
    }

    /**
     * 尺自己要有牙：一个已知异形必须被判成 HAND_DRAWN，一个已知委托壳必须被判成 SHELL。
     * 两头里任何一头分错 ⇒ 分类器是死的，上面两格的绿都不算数。
     */
    @Test
    fun `the classifier actually tells the two shapes apart`() {
        val byName = scan().groupBy { "${it.file}#${it.name}" }
        val header = byName["PanelHeader.kt#PanelHeader"]?.firstOrNull()
        val shell = byName["ScreenHeader.kt#ScreenHeader"]?.firstOrNull()
        assertTrue("扫不到 PanelHeader——这把尺看不见东西，判据失效", header != null)
        assertTrue("扫不到 ScreenHeader 壳——同上", shell != null)
        assertEquals("PanelHeader 自己画 Box/Row，必须是异形", Shape.HAND_DRAWN, header!!.shape)
        assertEquals("ScreenHeader 只转参数给 LbTopBar，必须是委托壳", Shape.SHELL, shell!!.shape)
    }

    /**
     * 主人第二档（"体里只调一颗设计系统已经代言的公开件、而且不给自己写数"）要有牙。
     *
     * 这一格存在的理由：不加它，"承认 `HorizontalDivider`"就只是把一句报错话换掉，
     * 谁都能在页面里现调一颗颜色、自己定一条粗细，然后拿这句免登记。所以四则反例 + 一则落回异形账
     * 全部拿分类器本体跑，正向那则还额外要求**生产里那一颗今天确实是壳**（尺对着真代码跑，不只是夹具）：
     * - 体里出现任何数字（`thickness = 3.dp`）⇒ 没人认领 ⇒ 要么改回具名 token，要么登记成异形；
     * - 现调颜色（`Color(0xFF…)`）⇒ 没人认领；
     * - 名单外的公开件（`Slider(`）⇒ 没人认领（名单只收无自有版式身份的装饰件，而且每一颗都要真被
     *   `core/designsystem` 进口——公共件哪天不再代言它，这里立刻重新红）；
     * - 自己拼 `Modifier` 链 ⇒ 没人认领；
     * - 把那条线换成页面自画的 `Box(Modifier.height(1.dp).background(Color(0xFF…)))`
     *   ⇒ 判成**手绘异形**，落进第一本账，不登记就过不了 `the odd-shape ledger names every hand-drawn site`。
     */
    @Test
    fun `a token-only delegation to an adopted primitive is a shell, a self-measured one is not`() {
        val adopted = designSystemAdoptedPrimitives()
        assertTrue(
            "名单里每一颗都必须真是 core/designsystem 进口过的公开件，实到进口集 $adopted",
            delegablePrimitives.isNotEmpty() && delegablePrimitives.all { it in adopted }
        )
        assertTrue(
            "页头那条分割线自己就走 Material：读不到 HorizontalDivider 说明这一格在自说自话",
            "HorizontalDivider" in adopted
        )
        assertTrue(
            "名单不许与自画词表重合（重合就是拿第二档给第一档开后门）",
            delegablePrimitives.none { it in callShapes }
        )

        // 正向：今天生产里真实存在的那一颗（组内行分隔），体里一个自己的数都没有
        val hairline = "    HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border)\n"
        assertTrue("具名 token 的分隔线要认得出主人", hasDesignSystemOwner(hairline))
        assertEquals("同一句交给公开件的分隔线必须是委托壳", Shape.SHELL,
            classify(listOf("private fun SettingsRowDivider() {", hairline, "}"), 0, true).first)

        // 反向那几则：每一则都不许被认成有主人
        assertFalse("自己写宽度的不算主人",
            hasDesignSystemOwner("    HorizontalDivider(thickness = 3.dp, color = Border)\n"))
        assertFalse("自己调颜色的不算主人",
            hasDesignSystemOwner(
                "    HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Color(0xFFE5E7EB))\n"
            ))
        assertFalse("名单外的公开件不算主人", hasDesignSystemOwner("    Slider(value = v, onValueChange = { })\n"))
        assertFalse("自己拼 Modifier 链不算主人",
            hasDesignSystemOwner("    HorizontalDivider(modifier = Modifier.fillMaxWidth())\n"))
        assertFalse(
            "没有公开件、也没有 Lb* 的空壳不许被这一档救活",
            hasDesignSystemOwner("    val w = measureSomething()\n")
        )

        // 反例证人：同一条线改成页面自画 ⇒ 必须落回异形账（要显式登记才过得了第一本账）
        val selfDrawn = "    Box(modifier = Modifier.height(1.dp).background(Color(0xFFE5E7EB)))\n"
        assertEquals("自己画的 1dp 盒子必须是手绘异形，不是委托壳", Shape.HAND_DRAWN,
            classify(listOf("private fun SettingsRowDivider() {", selfDrawn, "}"), 0, true).first)

        // 这一档确实在真代码上跑过：生产里那颗分隔线今天必须是壳，且不是交值的工厂
        val site = scan().firstOrNull { "${it.file}#${it.name}" == "SettingsEntryFamily.kt#SettingsRowDivider" }
        assertTrue(
            "扫不到组内行分隔那一颗——那颗声明没了就回来把 expectedShells 那一行删掉，别删这一格",
            site != null
        )
        assertEquals("组内行分隔走的是设计系统代言的公开件，必须是委托壳", Shape.SHELL, site!!.shape)
        assertFalse("它不交值，所以进的是壳账、不是状态工厂账", site!!.factory)
    }

    /**
     * 反向证人（这一格专门钉上面那个误判）：
     * 三颗**真委托**必须判成 SHELL，一颗**真自画**必须判成 HAND_DRAWN。
     * 判据是拿分类器本体跑的，不是手数出来的——把词边界退回裸子串，这三颗当场被误判成异形。
     */
    @Test
    fun `delegating calls to design-system widgets are not mistaken for hand-drawn ones`() {
        fun shapeOf(body: String) = hasHandDrawnTell(body)

        assertTrue(
            "LbDialog( 不该被当成自画 Dialog(，LbSettingRow( 不该被当成自画 Row(，SchemeCard( 不该被当成自画 Card(",
            !shapeOf("    LbDialog(title = t, onDismissRequest = d) { }") &&
                !shapeOf("    LbSettingRow(title = t, subtitle = s) { }") &&
                !shapeOf("    SchemeCard(data) { }")
        )
        assertTrue("真自画的 Dialog/Row/Card 必须仍然算异形",
            shapeOf("    Dialog(onDismissRequest = d) { }") &&
                shapeOf("    Row(horizontalArrangement = null) { }") &&
                shapeOf("    Card(colors = c) { }"))
        assertTrue(".background( 这种修饰符仍然算异形", shapeOf("    Modifier.background(Primary)"))
        // 分类器真跑一遍：这两颗是生产里的真委托，形状必须是 SHELL
        val measured = scan().filter { it.name == "ScreenHeader" || it.name == "OnboardingButton" }
            .associate { "${it.file}#${it.name}" to it.shape }
        assertEquals(
            "生产里这两颗必须是委托壳",
            mapOf("ScreenHeader.kt#ScreenHeader" to Shape.SHELL,
                "OnboardingFlow.kt#OnboardingButton" to Shape.SHELL),
            measured
        )
    }

    @Test
    fun `the scanner is not blind on a known hand-drawn file`() {        // 反向证人：整份 PanelHeader.kt 里至少有一处异形；如果扫描器对这份文件返回空集，
        // 说明它的注释剥离或声明识别先坏了——那时"账本一致"是假的。
        val fileSites = scan().filter { it.file == "PanelHeader.kt" }
        assertTrue("PanelHeader.kt 一个都没扫到 ⇒ 尺瞎了", fileSites.isNotEmpty())
    }
}
