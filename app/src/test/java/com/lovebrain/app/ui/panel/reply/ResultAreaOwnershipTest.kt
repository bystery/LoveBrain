package com.lovebrain.app.ui.panel.reply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 结果区"一个变化理由一个所有者"的分家合同（复核指导书 §6.4 第一句：
 * ResultArea 只负责结果内容，不再同时承载菜单、纠正浮层…）。
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
        // 结果区的低频 ⋯ 菜单，连它自己的菜单项行
        "ResultUtilityTrigger" to "ResultUtilityMenu.kt",
        "UtilityMenuItem" to "ResultUtilityMenu.kt",
        // 「本轮参考」清单与逐条纠正入口
        "MemoryRefsSection" to "MemoryRefsFeed.kt",
        "MemoryRefItem" to "MemoryRefsFeed.kt",
        "CorrectionDropdownItem" to "MemoryRefsFeed.kt",
        // 暂停时长那颗浮层的选项行——跟着它唯一的宿主住
        "CorrectionSubmenuItem" to "MemoryCorrectionFlow.kt",
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
        val stray = listOf("DropdownMenu(", "menuOpen", "showAllRefs").filter { code.contains(it) }
        // 只盯着浮层与菜单那几颗：方案卡行里"同一时间只展开一张改写区"是**内容侧**的状态，
        // 它跟着 SchemeCardsRow 留在 ResultArea.kt 是刻意的，不是漏搬
        assertTrue("ResultArea.kt 里又长出浮层或菜单状态了：$stray", stray.isEmpty())
        // 反向证人之一：⋯ 菜单确实还画得出浮层，不是被删掉了才"没有"
        assertTrue(
            "ResultUtilityMenu.kt 必须还看得见一颗 DropdownMenu",
            Regex("DropdownMenu\\(").containsMatchIn(stripComments(codeOf("ResultUtilityMenu.kt")))
        )
        // 反向证人之二：结果区还在正常调用搬出去的邻居（不是整块被删空造成的假绿）
        val stillCalled = homes.keys.filter { codeOf("ResultArea.kt").contains(it) }
        assertTrue(
            "ResultArea.kt 至少还得叫得出 4 位搬出去的邻居（实到 ${stillCalled.size}：$stillCalled）",
            stillCalled.size >= 4
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
            "⋯ 菜单那颗热区只许有一处定义，且必须指回全局下限而不是自己抄一个 48，实到 $declarations",
            listOf("ResultArea.kt" to "UTILITY_HITBOX_DP = AppDimens.TOUCH_TARGET_MIN_DP"),
            declarations.toList()
        )
        // 反向证人：读它的那一份还在读
        assertTrue(
            "⋯ 菜单必须还在读 ResultDimens.UTILITY_HITBOX_DP（不然上面那条等号是删引用删出来的）",
            codeOf("ResultUtilityMenu.kt").contains("ResultDimens.UTILITY_HITBOX_DP")
        )
    }
}
