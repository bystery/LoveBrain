package com.lovebrain.app.architecture

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 原始第 6 条（"所有朝右三角形的三个角都要真圆角，不许是尖角"）的**全站同类形状闸**。
 *
 * 指导书 §12 明写"只改一处不算这条过"，所以这条要求的凭据不是某一份台账文档，而是**一把会红的尺**：
 * 台账会随重构腐烂，而这把尺把"同类形状审计清单"整个钉进源码扫描——
 * 下一颗在页面里自己拼尖角的多边形会当场报它。
 *
 * ## 这一族到底是什么（口径，不是命名）
 *
 * 用户指的是**实心字形**：朝右三角（开始）、停止方块、以及同一族的折叠/展开指示三角。
 * 判断按形状不看名字，按三条收口：
 * 1. 尖角的来源就是多边形顶点表 ⇒ 生产源码里出现 `moveTo(`/`lineTo(` 的文件必须逐颗点名
 *    （[polygonHitLines]）；圆角矩形/差集罩子（`addRect`/`addRoundRect`）没有"尖角"这一说，不落这一族；
 * 2. **描边**画出来的线性字形（`style = Stroke(...)`，端点本来就是 `StrokeCap.Round`）
 *    不是实心字形——§3.10 明写返回/展开 chevron 一类线性指示器不参与这一族，
 *    所以例外行必须能当场自证它真的走描边（[strokedIndicatorExceptions] 那一条判据）；
 * 3. 实心形状的主人只许是 `core/designsystem/LbTriangleGlyph.kt`：扫描根**整目录跳过 designsystem**
 *    （与 [OddShapeOwnershipTest] 同一口径），页面里只许出现"调用那颗件"，不许出现"再拼一条 Path"。
 *
 * ## 矢量图标为什么不在这一族
 *
 * `R.drawable.ic_*`（`ic_chevron_down.xml` 是 `strokeWidth=2` 的描边折线、
 * `ic_model_dropdown.xml` 是屏上零引用的孤儿资源）与 Material `Icons.*`（`KeyboardArrowRight`、
 * `Icons.Filled.Add`）都由资产/字形库持有圆角与端点口径，不是仓库自绘的 Path，**不许也不需要**
 * 换成 `LbTriangleGlyph`。这一格判的是"自绘"，不是"所有看起来是三角形的像素"。
 *
 * ## 每一格配的反例
 *
 * - 有人把旧的实心三角抄回页面（`Path().apply { moveTo/lineTo/close }` + 无描边 `drawPath`）
 *   ⇒ 第 1 格红，报的就是那颗文件；
 * - 有人给 `ResizeGrip` 那颗对角箭头去掉 `Stroke` 改成实心 ⇒ 例外行当场红
 *   （它的"能自证"那一句读不到了）；
 * - 有人给公共件加一个 `color:` 或 `cornerRadius:` 旋钮、或页面直接透传 ⇒ 第 3 格红；
 * - 有人把 0.18 那一份比例抄成第二颗常量（或上提进 `Dimens.kt`）⇒ 第 4 格红；
 * - 反向证人（尺不许是死的）：第 2 格拿两种坏法（真尖角、同行两条 tell）与两种好法
 *   （注释里的顶点表、圆角矩形罩子）各喂一遍**同一个判据**。
 */
class SolidGlyphShapeAuditTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private val designSystemDir get() = File(appRoot, "core/designsystem")

    /** 尖角的来源：多边形顶点表的两条 tell（`arcTo` 那一族只在公共件里出现，不在这一族里判） */
    private val polygonTells = listOf("moveTo(", "lineTo(")

    /**
     * 审计在册的**例外**：文件 → 为什么它那一处多边形不是实心字形。
     *
     * 这本账两头都要动：新长一颗没登记的红，登记的对象消失了也红（不留一条不成立的豁免）。
     * 每一行还必须**能自证**：体里得有 `Stroke(`，否则它就不是"描边线性指示器"而是实心形状，
     * 那就该换成公共件、把这一行销掉。
     */
    private val strokedIndicatorExceptions = mapOf(
        "ResizeGrip.kt" to "右下角缩放手柄：斜向三条线走 drawLine + StrokeCap.Round，" +
            "激活态那颗对角箭头是一条 `Path` 但整条以 `style = Stroke(width, cap = Round)` 描出——" +
            "它没有\"尖角填充\"这回事，是线性指示器（§3.10：线性字形不参与实心字形这一族）"
    )

    /** 掩平注释后的源码 → 那些 tell 所在行号（1 基）；KDoc 里"旧 Path 长什么样"不算一处 */
    private fun polygonHitLines(masked: String): List<Int> =
        masked.split("\n").mapIndexed { i, line ->
            if (polygonTells.any { it in line }) i + 1 else null
        }.filterNotNull()

    /** 全树实到：文件名 → 命中行号（跳过 core/designsystem，那里才是这一族的主人） */
    private fun scanPolygonSites(): Map<String, List<Int>> {
        assertTrue("找不到源码根：$appRoot——这把尺会恒绿", appRoot.isDirectory)
        val out = linkedMapOf<String, List<Int>>()
        appRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            if (f.absolutePath.startsWith(designSystemDir.absolutePath)) return@forEach
            val hits = polygonHitLines(SourceScan.maskComments(f.readText(Charsets.UTF_8)))
            if (hits.isNotEmpty()) out[f.name] = hits
        }
        return out
    }

    // ═══════════ 1. 清单本体：全站自绘多边形逐颗点名，未登记的就是尖角 ═══════════

    @Test
    fun `no page outside the design system draws a sharp polygon any more`() {
        val measured = scanPolygonSites()
        val unregistered = measured.filterKeys { !strokedIndicatorExceptions.containsKey(it) }
        assertTrue(
            "这些文件在页面里自己拼多边形（实心尖角就是这么长出来的），" +
                "而实心字形的唯一主人是 core/designsystem/LbTriangleGlyph.kt：" +
                unregistered.map { (file, lines) -> "$file:${lines.joinToString(",")}" } +
                "；实到全量 ${measured.mapValues { it.value }}",
            unregistered.isEmpty()
        )
        val stale = strokedIndicatorExceptions.keys - measured.keys
        assertTrue(
            "这些例外登记已经对不上现实（文件里已经没有多边形了）——销行，别留一条不成立的豁免：" +
                "$stale；实到全量 ${measured.mapValues { it.value }}",
            stale.isEmpty()
        )
    }

    /**
     * 例外不许只是一句说辞：每一行都要能当场自证它是**描边**，并且理由写清了归谁。
     * 反例：把 `ResizeGrip` 那颗箭头的 `style = Stroke(...)` 删掉改成实心填充 ⇒ 这一格先红。
     */
    @Test
    fun `each registered exception proves it is a stroked indicator`() {
        strokedIndicatorExceptions.forEach { (file, why) ->
            assertTrue("$file 的例外行必须写清它为什么不是实心字形", why.isNotBlank())
            val found = appRoot.walkTopDown().firstOrNull { it.name == file && it.isFile }
            assertTrue("例外登记的 $file 已经不在盘上——这一行要一起销掉", found != null)
            val code = SourceScan.maskComments(found!!.readText(Charsets.UTF_8))
            assertTrue(
                "$file 说是描边线性字形，可体里读不到 `Stroke(`：那它就是实心形状，该换公共件",
                code.contains("Stroke(") || code.contains("drawLine(")
            )
        }
    }

    // ═══════════ 2. 这把尺看得见东西（反例与前向证人跑同一个判据）═══════════

    @Test
    fun `the ruler catches a copied-back glyph and spares a comment about it`() {
        // 坏法 1：旧的实心三角被抄回页面（三颗顶点全是尖角）
        val sharp = "@Composable\nfun AdvisorPlayGlyph() {\n    Canvas {\n" +
            "        val path = Path().apply {\n            moveTo(0f, 0f)\n" +
            "            lineTo(w, h / 2f)\n            lineTo(0f, h)\n            close()\n        }\n" +
            "        drawPath(path, color)\n    }\n}\n"
        assertEquals("真尖角多边形必须被点到（moveTo 与两颗 lineTo 各一行）",
            listOf(5, 6, 7), polygonHitLines(SourceScan.maskComments(sharp)))

        // 坏法 2：同一个判据换个写法再走一遍——两条 tell 挤在同一行也只算一格行号，但必须读得到
        val square = "fun G() {\n    val p = Path().apply { moveTo(0f, 0f); lineTo(9f, 0f) }\n}\n"
        assertEquals(listOf(2), polygonHitLines(SourceScan.maskComments(square)))

        // 好法 1：KDoc / 行注释里写"旧的那段 Path 长这样"不算一处（否则每颗归并成功的键都会被冤）
        val docOnly = "/**\n * 旧体是 moveTo(0,0) → lineTo(w,0) → close()。\n" +
            " */\n@Composable\nfun LbTriangleGlyph() {\n    // 这里以前是一颗 lineTo( 拼的尖角\n    LbChip()\n}\n"
        assertEquals("注释里的顶点表被当成了自绘形状：" + polygonHitLines(SourceScan.maskComments(docOnly)),
            emptyList<Int>(), polygonHitLines(SourceScan.maskComments(docOnly)))

        // 好法 2：圆角矩形差集（罩子那一族）没有尖角，不该落进这一族
        val mask = "fun S() {\n    drawPath(Path().apply {\n        addRect(0f, 0f, w, h)\n" +
            "        addRoundRect(l, t, r, b, c, c)\n    }, color)\n}\n"
        assertEquals(emptyList<Int>(), polygonHitLines(SourceScan.maskComments(mask)))
    }

    // ═══════════ 3. 调用点只有三档旋钮：页面拿不到 color / cornerRadius ═══════════

    /**
     * §3.10 的接缝：圆化比例与墨色都不许从页面透进去。
     * 反例：`LbTriangleGlyph(sizeDp = x, tone = t, shape = s, cornerRadius = 6.dp)`
     * 或 `color = Primary` ⇒ 这一格红（公共件签名里没有这两颗旋钮，写了根本编译不过；
     * 这里是**先于编译**把它钉住，报得出文件名与行号，也拦住有人把签名改宽）。
     */
    @Test
    fun `glyph call sites cannot reach a colour or corner radius knob`() {
        val needle = "LbTriangleGlyph("
        val sites = mutableListOf<Triple<String, Int, String>>()
        appRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            if (f.absolutePath.startsWith(designSystemDir.absolutePath)) return@forEach
            val masked = SourceScan.maskComments(f.readText(Charsets.UTF_8))
            var from = 0
            while (true) {
                val at = masked.indexOf(needle, from)
                if (at < 0) break
                val open = at + needle.length - 1
                val close = SourceScan.closeIndexOf(masked, open)
                sites += Triple(f.name, masked.take(at).count { it == '\n' } + 1, masked.substring(open, close))
                from = close
            }
        }
        assertTrue(
            "一把读不出调用点的尺不算闸：实到 ${sites.size} 处（至少要有首页 hero 与面板那颗折叠指示）",
            sites.size >= 2
        )
        assertTrue(
            "至少这两处必须在册（少了就说明扫描根或名字换了而闸还在自称绿）：" +
                sites.map { "${it.first}:${it.second}" },
            sites.any { it.first == "HomeComponents.kt" } && sites.any { it.first == "DragHandle.kt" }
        )
        sites.forEach { (file, line, args) ->
            val where = "$file:$line"
            listOf("sizeDp", "tone", "shape").forEach { knob ->
                assertTrue("$where 的调用没交 $knob 这一档（公共件只有多大/哪档墨/什么形三颗）：$args",
                    knob in args)
            }
            listOf("color", "cornerRadius", "radius", "vertices").forEach { banned ->
                assertTrue(
                    "$where 想从页面透 `$banned` 进来——§3.10：墨色与比例都来自词表/那唯一一颗常量，" +
                        "页面拿不到旋钮",
                    !Regex("""\b""" + banned + """\s*=""").containsMatchIn(args)
                )
            }
        }
    }

    /**
     * 展开/折叠那一族不许被顺手换成播放键（§3.10 明写）。
     * 反例：把 `TriangleArrow` 的档写成 `TriangleRight` ⇒ "不是播放那一档" 那句红。
     */
    @Test
    fun `the collapse indicator stays a down triangle instead of becoming a play key`() {
        val file = File(appRoot, "ui/panel/DragHandle.kt")
        assertTrue("找不到 $file——这把尺会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        assertEquals("折叠指示器应整颗交给公共件（一处调用，零处自绘）", 1, Regex("\\bLbTriangleGlyph\\s*\\(").findAll(code).count())
        assertTrue("没走 TriangleDown 那一档", code.contains("LbTriangleGlyphShape.TriangleDown"))
        assertTrue("展开指示器被换成了播放键 TriangleRight", !code.contains("LbTriangleGlyphShape.TriangleRight"))
        assertTrue(
            "可见尺寸那一轴没走 core 已有的 10dp 档（不许为这一族新立一档尺寸）",
            code.contains("AppDimens.ARROW_SIZE_DP.dp")
        )
        assertEquals("页面里不许再留一条自绘 Path", 0, Regex("\\bPath\\(\\)").findAll(code).count())
        assertEquals("页面里不许再自己 lineTo 出尖角", 0, Regex("\\blineTo\\(").findAll(code).count())
        assertEquals("页面里不许再自己 drawPath", 0, Regex("\\bdrawPath\\(").findAll(code).count())
    }

    // ═══════════ 4. 比例只有一颗数，而且只住在那颗文件里 ═══════════

    /**
     * 反例：有人把 0.18 抄成第二份（同名或**换个名字**再声明一次）⇒ `declared` 长出第二颗文件名；
     * 反例：有人把它"归一"进间距 token 表（`Dimens.kt`）⇒ 后一句红——那一格谁都能借，
     * §3.10 的"这颗比例独享"当场失效。
     *
     * ⚠ 2026-10-06 修 B 档（仪器自己坏）：上一版把 `walkTopDown` 的 Sequence 直接跟 List 比，
     * 断言比的是对象身份、永远红（`expected:<[LbTriangleGlyph.kt]> but was:<…TransformingSequence@…>`）。
     * 修法不是只补 `.toList()`：补完之后把判据从"精确名"收紧成"名字里带 CORNER_RATIO 的 `const val`
     * 声明族"——只认精确名那一版，换个名字抄第二份的正好从牙缝里过去。
     * 注入反例（改坏实现必须先打破这条新断言，已验证形状）：
     *  - 在 `app/src/main/java/com/lovebrain/app/ui/home/HomeComponents.kt` 顶层加一行
     *    `const val LB_GLYPH_CORNER_RATIO = 0.18f` ⇒ `declared` 变两颗，第一句红；
     *  - 同一行改写成 `const val APP_CORNER_RATIO = 0.18f`（改名抄）⇒ 收紧后的这颗一样红
     *    （旧精确名版对这一条是瞎的，合成证人②就是钉这个的）。
     */
    @Test
    fun `the corner ratio is declared exactly once and not lifted into the spacing tokens`() {
        val decl = Regex("""const val \w*CORNER_RATIO\w*""")
        val declared = appRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { decl.containsMatchIn(SourceScan.maskComments(it.readText(Charsets.UTF_8))) }
            .map { it.name }
            .toList()
        assertEquals(
            "圆化比例只许有一颗声明（抄第二份就会长出第二种圆角；换了名字抄的第二份也要报出来）",
            listOf("LbTriangleGlyph.kt"), declared
        )

        // ── 反向证人：合成件走的是同一颗正则同一把 mask，坏形状必须读得到、好形状不许误伤 ──
        // ① 同名第二份的声明形状必须被读到（否则第一句是恒绿的空门）
        assertTrue(
            "注件没就位：合成一颗同名声明必须被这把尺读到",
            decl.containsMatchIn(SourceScan.maskComments("const val LB_GLYPH_CORNER_RATIO = 0.18f"))
        )
        // ② 换个名字的第二份也必须被读到——这正是把精确名那一版放宽成声明族的理由
        assertTrue(
            "注件没就位：改名抄的第二份（APP_CORNER_RATIO）必须被这把尺读到，否则「抄第二份」改个名就过关",
            decl.containsMatchIn(SourceScan.maskComments("private const val APP_CORNER_RATIO = 0.18f"))
        )
        // ③ 好法证人：KDoc/注释里引用这颗名字不算一处声明（否则每份解释它的注释都会被冤）
        assertTrue(
            "注件没就位：注释里的引用被当成了声明",
            !decl.containsMatchIn(
                SourceScan.maskComments("/** 半径 = 边长 × [LB_GLYPH_CORNER_RATIO] 独享 */\nfun A() {}\n")
            )
        )

        // Dimens.kt 管的是容器与热区那几档下限；字形圆化不是间距 token
        val dimens = File(designSystemDir, "Dimens.kt")
        assertTrue("找不到 $dimens", dimens.isFile)
        assertTrue(
            "比例被上提进间距 token 表了——那一格谁都能借，§3.10 的\"独享\"当场失效",
            !SourceScan.maskComments(dimens.readText(Charsets.UTF_8)).contains("CORNER_RATIO")
        )
    }

    // ═══════════ 5. 公共件自己的签名：三颗旋钮，没有第四颗、也没有 clickable ═══════════

    /**
     * 第 3 格管的是**调用点**，这一格管的是**声明**：有人把 `color:` 或 `cornerRadius:` 加回签名，
     * 全站每一页就又能各挑一种圆角/一种蓝——那正是 §3.10 立这颗件要断掉的那条路。
     * 两格都要在，因为"签名宽了但暂时没人用"这件事只有声明那一格看得见。
     *
     * 反例：`fun LbTriangleGlyph(sizeDp, tone, shape, cornerRadius: Dp = …)` ⇒ 那一档禁词红；
     * 反例：给这一颗补 `.clickable`（想让字形自己可点）⇒ 最后一句红（热区归调用方那一排）。
     */
    @Test
    fun `the shared glyph keeps three knobs and no interaction of its own`() {
        val file = File(designSystemDir, "LbTriangleGlyph.kt")
        assertTrue("找不到 $file——这一族的主人没了", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        val marker = "fun LbTriangleGlyph("
        val at = code.indexOf(marker)
        assertTrue("读不到公共件的声明（改名就等于把这两格一起摘掉）", at >= 0)
        val open = at + marker.length - 1
        val params = code.substring(open, SourceScan.closeIndexOf(code, open))
        listOf("sizeDp", "tone", "shape", "modifier").forEach { knob ->
            assertTrue("公共件的签名少了 $knob 这一档：$params", knob in params)
        }
        listOf("color", "cornerRadius", "radius", "vertices", "path").forEach { banned ->
            assertTrue(
                "公共件的签名里长出了 `$banned:` 这一颗旋钮——墨色与比例都不许由页面透进来",
                !Regex("""\b""" + banned + """\s*:""").containsMatchIn(params)
            )
        }
        assertTrue("字形自己不许挂交互：热区归调用方那颗可点盒子（三轴分离）", !code.contains(".clickable"))
        // 比例不许在这里被写死成第二个数：整个文件只许有一颗声明（第 4 格量全局，这一格量就地）
        assertEquals(
            "`LB_GLYPH_CORNER_RATIO` 的声明必须恰好一颗",
            1, Regex("""const val LB_GLYPH_CORNER_RATIO""").findAll(code).count()
        )
    }
}
