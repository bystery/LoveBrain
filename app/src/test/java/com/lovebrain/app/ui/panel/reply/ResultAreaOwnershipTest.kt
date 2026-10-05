package com.lovebrain.app.ui.panel.reply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 结果区"一个变化理由一个所有者"的分家合同：ResultArea 只负责结果内容，
 * 不同时承载菜单、纠正浮层、逐条记忆的纠正词表。
 *
 * ⚠ 这一格读的是**结构**，不是行为：它证明的是"这次搬家没被搬坏、也没有搬回原处"，
 * 证明不了"这一屏长得对"。后者一律在语义树那边判
 * （`ResultAreaTouchTargetsTest`、`MemoryCorrectionFlowTest`、`PanelHostSemanticsTest`）。
 * 还值得写这一格的原因很具体：搬家最常见的坏法不是编译不过（那当场就红），
 * 而是几周后有人把菜单又内联回 ResultArea.kt，于是同一个变化理由重新住进两个文件，
 * 而语义树那几格照样全绿——它们判的是屏幕，不是文件归位。
 *
 * 每条判据都配一条反向证人（"这把尺真的看得见东西"）：只断言"ResultArea.kt 里没有
 * `DropdownMenu(`"的话，正则写坏了也能永远绿，那正是本仓库坑表里"扫空集恒绿"那一族。
 *
 * 结果区那颗右上角 `⋯` 总工具入口整块退场之后，这一格多了三条**反向**判据：
 * 那颗菜单不许换个名字长回来（`ResultUtilityMenu.kt` 不在、`ResultUtilityTrigger` 不在）、
 * "同一时间只展开一张卡"那一档行级状态不许回来、
 * 「仅看本轮」那颗可见开关不许在这屏重新出现。三条都是"删掉之后容易被写回去"的形状。
 */
class ResultAreaOwnershipTest {

    private val replyDir: File by lazy {
        File("src/main/java/com/lovebrain/app/ui/panel/reply").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/reply")
    }

    private fun kotlinFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().also {
            assertTrue("扫到 0 个 .kt——路径接错了，这一格会恒绿：$dir", it.isNotEmpty())
        }

    private fun codeOf(name: String): String {
        val f = File(replyDir, name)
        assertTrue("少了我预期里的这一位所有者文件：$f", f.isFile)
        return f.readText(Charsets.UTF_8)
    }

    /** 去掉注释再数：这些文件的 KDoc 里写"以前这里是……"是常态，不能当成又长了一份 */
    private fun stripComments(src: String): String = buildString {
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).takeIf { e -> e >= 0 } ?: src.length
                    i = minOf(end + 2, src.length)
                }
                src.startsWith("//", i) -> {
                    val end = src.indexOf('\n', i).takeIf { e -> e >= 0 } ?: src.length
                    i = end
                }
                else -> { append(src[i]); i++ }
            }
        }
    }

    private fun declCount(code: String, name: String): Int =
        Regex("\\bfun\\s+$name\\s*\\(").findAll(code).count()

    /** 谁搬家谁住哪儿：声明处唯一，且就在它自己那份文件里 */
    private val homes = mapOf(
        // 「本轮参考」清单与逐条纠正入口
        "MemoryRefsSection" to "MemoryRefsFeed.kt",
        "MemoryRefItem" to "MemoryRefsFeed.kt",
        "CorrectionDropdownItem" to "MemoryRefsFeed.kt",
        // 暂停时长那颗浮层的选项行——跟着它唯一的宿主住
        "CorrectionSubmenuItem" to "MemoryCorrectionFlow.kt",
        // 纠正浮层唯一的渲染处（ResultArea 只发意图、只持有 host 的位置）
        "MemoryCorrectionFlowHost" to "MemoryCorrectionFlow.kt",
        // 方案卡下方那块次级信息
        "OngoingSection" to "OngoingSection.kt",
        // 流式逐字的渲染节奏
        "TypewriterText" to "StreamingTypewriter.kt"
    )

    @Test
    fun `the result area entry point still has exactly one declaration`() {
        val found = kotlinFiles(replyDir)
            .map { it.name to declCount(stripComments(it.readText(Charsets.UTF_8)), "ResultArea") }
            .filter { it.second > 0 }
        assertEquals(
            "公共入口 ResultArea 应当只声明一处、且在 ResultArea.kt，实到 $found",
            listOf("ResultArea.kt" to 1), found
        )
    }

    @Test
    fun `each moved owner lives in exactly one home`() {
        val files = kotlinFiles(replyDir)
        homes.forEach { (name, expectedFile) ->
            val found = files
                .map { it.name to declCount(stripComments(it.readText(Charsets.UTF_8)), name) }
                .filter { it.second > 0 }
            assertEquals(
                "$name 的声明处应当恰好一处、就在 $expectedFile，实到 $found",
                listOf(expectedFile to 1), found
            )
        }
    }

    @Test
    fun `the result area itself draws no popup and holds no menu state`() {
        val code = stripComments(codeOf("ResultArea.kt"))
        // 结果区不许再画浮层、再持有菜单开合，也不许把已经退场的展示分组写回来。
        // 「仅看本轮」在这里连字符串都不该出现：那颗可见开关整块删掉了，
        // 组件还收着的同名形参已经被 @Suppress 标成"不读"，读代码的人一眼能看见。
        val stray = listOf(
            "DropdownMenu(", "menuOpen", "showAllRefs",
            "ResultUtilityTrigger", "UtilityMenuItem",
            "SchemeViewMode", "SchemeViewSwitcher",
            "仅看本轮", "expandedRewriteTag"
        ).filter { code.contains(it) }
        // 方案卡的展开状态是**内容侧**的状态，它跟着 SchemeCardsRow 留在 ResultArea.kt 是刻意的，
        // 不是漏搬——所以这里禁的是那个"只存一颗"的旧名字，不是禁展开状态本身。
        assertTrue("ResultArea.kt 里又长出浮层、菜单状态或已经退场的分组了：$stray", stray.isEmpty())
        // 反向证人之一：`DropdownMenu(` 这把尺不是瞎的——逐条记忆那颗纠正菜单照样量得到
        assertTrue(
            "MemoryRefsFeed.kt 必须还看得见一颗 DropdownMenu（否则上面那条'没有'是尺失效造成的假绿）",
            Regex("DropdownMenu\\(").containsMatchIn(stripComments(codeOf("MemoryRefsFeed.kt")))
        )
        // 反向证人之二：整份菜单文件已经搬走，不是"改了名字还留在源码树里"
        assertTrue(
            "ResultUtilityMenu.kt 已经退场，不许留在 app/src/main 的 reply 目录里（要留档就搬进 _archive/）",
            !File(replyDir, "ResultUtilityMenu.kt").exists()
        )
        // 反向证人之三：结果区还在正常调用搬出去的邻居（不是整块被删空造成的假绿）
        val stillCalled = homes.keys.filter { codeOf("ResultArea.kt").contains(it) }.toSet()
        assertEquals(
            "ResultArea.kt 叫得出的邻居应当恰好是这一批（少了是渲染点被删空，多了是有人把菜单又内联回来）",
            setOf("MemoryRefsSection", "MemoryCorrectionFlowHost", "OngoingSection", "TypewriterText"),
            stillCalled
        )
    }

    @Test
    fun `the utility hit box is still declared once and still points at the global floor`() {
        val mainRoot = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")
        val declarations = kotlinFiles(mainRoot).flatMap { f ->
            Regex("\\bUTILITY_HITBOX_DP\\s*=[^=\\n]*")
                .findAll(stripComments(f.readText(Charsets.UTF_8)))
                .map { f.name to it.value.trim() }
        }
        assertEquals(
            "结果区文字入口那颗热区只许有一处定义，且必须指回全局下限而不是自己抄一个 48，实到 $declarations",
            listOf("ResultArea.kt" to "UTILITY_HITBOX_DP = AppDimens.TOUCH_TARGET_MIN_DP"),
            declarations.toList()
        )
        // 反向证人：读它的那一份还在读——现在读它的是结果区下方那条文字入口自己。
        // 入口被删掉的话这颗数就成了没人读的定义，这一条会红，而不是留下一颗"没人用但照样绿"的下限。
        assertTrue(
            "ResultArea.kt 必须还在读 ResultDimens.UTILITY_HITBOX_DP（不读就说明那颗文字入口没了）",
            stripComments(codeOf("ResultArea.kt")).contains("ResultDimens.UTILITY_HITBOX_DP")
        )
    }
}
