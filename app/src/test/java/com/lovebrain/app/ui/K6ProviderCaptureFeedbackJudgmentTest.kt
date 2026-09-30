package com.lovebrain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * K6 判决表：**Provider / 捕获 / 反馈 同一套 row/card/state 语法 → 全判不搬**。
 *
 * 这条 ⚠ 来自第三步执行计划（`_temp/ledger_backup_before39.md` 第 189 行原文：
 * "Provider/捕获/反馈同一套 row/card/state 语法 → 没做"）。它的意思是：三个目的地
 * （供应商 [ProviderSection]、捕获范围 [CaptureAppsScreen]、反馈案例
 * [FeedbackCasesScreen]）原先各自发明卡片、行、状态点与空/错误态；指导书 §6.1/§6.3
 * 要的是它们共用同一套设计系统语法（`LbActionCard` / `LbSettingRow` / `LbRowState` /
 * `LbAsyncState`）。
 *
 * 这一格**不是**"搬过没搬过"那把尺（那种由 `OddShapeOwnershipTest` 与
 * `UiLayerDependencyContractTest` 各自持有），而是把"判完了、九处都不用再搬"这个
 * 判决本身写进代码：**判决就是完成**。⚠ 之所以一直在账上，是因为判决只在文档里，
 * 没有用一条会绿的测试钉住——这一格补上那一半。
 *
 * 为什么"全判不搬"：
 * - 七处**已经在用**同一套设计系统组件（`Lb*`），搬这件事在它们身上是已完成态、
 *   不是待办；登记它们是为了让"判过"看得见，而不是为了改它们。
 * - 两处**故意只搬一半**：`ProviderSection` 的展开头状态点把"就绪↔颜色"那一对收进了
 *   `LbRowState`，但点的**形状**留在调用方——`LbSettingRow` 的行布局（点在尾、无
 *   chevron）对不上这一行，硬搬会换形状；`CaptureAppsScreen` 的 `Checkbox` 同理留
 *   在 `leading` 槽，设计系统不该认识"勾选框"这个具体控件。这两处是"判过、按设计不搬"，
 *   不是漏搬。
 *
 * 判据取**源码里看得见的设计系统调用**（`Lb*(` 出现且计数对得上），而不是语义树断言：
 * 那半由 `FeedbackCasesSemanticsTest` / `CaptureAppRowSemanticsTest` /
 * `ProviderSectionSemanticsTest` 各自持有，两把尺各看一件事，别合成一个数。
 */
class K6ProviderCaptureFeedbackJudgmentTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private fun uiFile(path: String): File =
        File(appRoot, "ui/$path").also {
            assertTrue("找不到 $it——这一格会恒绿（判决表指向了不存在的文件）", it.isFile)
        }

    /** 去注释与字符串字面量后的代码：注释里写 `LbActionCard(` 不算一处调用 */
    private fun codeOf(src: String): String = buildString {
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).takeIf { it >= 0 } ?: src.length
                    i = minOf(end + 2, src.length)
                }
                src.startsWith("//", i) -> {
                    val end = src.indexOf('\n', i).takeIf { it >= 0 } ?: src.length
                    i = end
                }
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3).takeIf { it >= 0 } ?: src.length
                    append("""" """"); i = minOf(end + 3, src.length)
                }
                src[i] == '"' -> {
                    val end = src.indexOf('"', i + 1).takeIf { it >= 0 } ?: src.length
                    append("\"\""); i = minOf(end + 1, src.length)
                }
                else -> { append(src[i]); i++ }
            }
        }
    }

    private data class Judgment(
        val id: Int,
        val area: String,           // Provider / 捕获 / 反馈
        val file: String,            // ui/ 下的相对路径
        val family: String,          // row / card / state
        val what: String,            // 它表达什么
        val anchor: String,          // 在源码里要认出的设计系统调用
        val expectCount: Int,        // 这一处锚点应当出现的次数
        val notMovedBecause: String  // 判决：为什么不搬
    )

    /**
     * 九处判决。每行 = 一处"候选搬动点" + 它为什么不搬。
     *
     * 读法：①～⑦是"已经在用同一套语法"（搬这件事已完成，登记它们让"判过"看得见）；
     * ⑧⑨是"按设计只搬一半"——颜色/语义收进设计系统，形状/具体控件留在调用方，
     * 硬搬会换形状或让设计系统认识一个具体页面词汇。
     */
    private val judgments: List<Judgment> = listOf(
        Judgment(
            1, "Provider", "home/HomeScreen.kt", "card",
            "首页「快捷功能」区知识库入口卡片",
            "LbActionCard(", 2,
            "已用同一颗 LbActionCard（知识库与反馈案例两处入口共用一颗，§6.2 第 3 段" +
                "「相同组件」）；卡片自己的图标方块/箭头/按压/圆角底色全在组件里，这一页只交图标与出口"
        ),
        Judgment(
            2, "反馈", "home/HomeScreen.kt", "card",
            "首页「快捷功能」区反馈案例入口卡片",
            "LbActionCard(", 2,
            "与知识库入口同一颗 LbActionCard——反馈这一族在这一格的代表就是这颗入口卡，" +
                "不是 FeedbackCasesScreen 里的某处自画（那一页的空/错态另在判决 9）"
        ),
        Judgment(
            3, "Provider", "home/HomeScreen.kt", "row",
            "服务设置区「模型供应商」行",
            "LbSettingRow(", 2,
            "已用 LbSettingRow：标题/副标题/状态点/尾部动作统一交给行组件，" +
                "就绪↔颜色那对走 LbRowState（见判决 5），页面不再自己画行"
        ),
        Judgment(
            4, "捕获", "home/HomeScreen.kt", "row",
            "服务设置区「捕获范围」行",
            "LbSettingRow(", 2,
            "已用 LbSettingRow：与供应商行同一颗组件，捕获开/关/无权限三态都收在副标题" +
                "与 statusText 槽里，没有第二种「行」的说法"
        ),
        Judgment(
            5, "Provider", "home/HomeScreen.kt", "state",
            "服务设置区供应商 + 捕获两颗就绪状态点",
            "LbRowState.Ready", 2,
            "已用 LbRowState.Ready/NotReady 两档枚举表达就绪↔未就绪，" +
                "颜色来自词表不来自参数（§6.1 末句要的正是这一对收口）"
        ),
        Judgment(
            6, "Provider", "home/ProviderSection.kt", "state",
            "供应商展开头那颗状态点（就绪↔颜色）",
            "LbRowState.Ready", 1,
            "★ 按设计只搬一半：就绪↔颜色那一对已收进 LbRowState.color，但点的形状" +
                "留在调用方——LbSettingRow 的行布局（点在尾、无 chevron）对不上这一行" +
                "（展开头那颗点在首），硬搬会换形状。判过、不搬"
        ),
        Judgment(
            7, "Provider", "home/ProviderSection.kt", "state",
            "供应商票据列表空/有内容两态",
            "LbAsyncState(", 1,
            "已用 LbAsyncState：空/有内容两格由同一个 ScreenState 判定、一处版式" +
                "（§6.3 要的正是判定一处、版式一处），不再 if/else 两边各画一次"
        ),
        Judgment(
            8, "捕获", "home/CaptureAppsScreen.kt", "state",
            "捕获 App 列表四态",
            "LbAsyncState(", 1,
            "已用 LbAsyncState：这一页「现在是哪一格」只判一次，版式交给组件" +
                "（与供应商票据列表、反馈案例列表同一颗），不再自己发明空态卡片"
        ),
        Judgment(
            9, "反馈", "feedback/FeedbackCasesScreen.kt", "state",
            "反馈案例列表空/有内容两态",
            "LbAsyncState(", 1,
            "已用 LbAsyncState：三个目的地（供应商/捕获/反馈）的列表四态现在共用同一颗" +
                "组件——这正是「同一套 row/card/state 语法」那条 ⚠ 落地的证人"
        )
    )

    /** 判决表本身：九处，少一处就是漏判，多一处就是有人加了一颗没判过 */
    @Test
    fun `the K6 judgment names exactly nine provider-capture-feedback sites`() {
        assertEquals(
            "K6 判决表登记九处，实到 ${judgments.size}——" +
                "少一处是漏判，多一处是有人加了一颗没判过",
            9, judgments.size
        )
        // 三个目的地都得在场：否则「Provider/捕获/反馈」这条标题就名不副实
        val areas = judgments.map { it.area }.toSet()
        assertTrue("三个目的地都得有判决：$areas", areas == setOf("Provider", "捕获", "反馈"))
        // 三族语法都得被用到：否则「row/card/state」这一标题就有一族没人用
        val families = judgments.map { it.family }.toSet()
        assertTrue("row/card/state 三族都得在判决里：$families",
            families == setOf("row", "card", "state"))
    }

    /**
     * 逐处证人：每一处判决指向的文件都得真在盘上、且源码里看得见那颗设计系统调用，
     * 计数对得上。这一格是「判决不是空话」的那一半——
     * 某一处搬回去了（Lb* 调用消失）、或文件被改名了，这里当场红。
     */
    @Test
    fun `every judged site still uses the shared design-system syntax at its documented count`() {
        judgments.forEach { j ->
            val file = uiFile(j.file)
            val code = codeOf(file.readText())
            val got = Regex(Regex.escape(j.anchor)).findAll(code).count()
            assertTrue(
                "判决 ${j.id}（${j.area}/${j.family}）说 ${j.file} 里有 ${j.expectCount} 处 " +
                    "「${j.anchor}」，实到 $got 处。\n" +
                    "  表达：${j.what}\n" +
                    "  不搬的理由：${j.notMovedBecause}\n" +
                    "  搬回去了（Lb* 调用消失）= 判决已不成立，要回来改这一行",
                got == j.expectCount
            )
        }
    }

    /**
     * 反向证人之一：这把尺看得见东西。如果哪天设计系统组件被改名（`LbAsyncState` →
     * 别的东西）、或 ui/ 目录被搬空，上面那一格会扫空集恒绿——这一格挡住那种假绿。
     */
    @Test
    fun `the judgment ruler is not blind on a known shared-syntax site`() {
        // 供应商票据列表那颗是已知在用 LbAsyncState 的处：扫不到说明这把尺瞎了
        val providerList = uiFile("home/ProviderSection.kt")
        val code = codeOf(providerList.readText())
        assertTrue(
            "ProviderSection.kt 应当还在用 LbAsyncState（判决 7）——扫不到说明" +
                "codeOf 或锚点坏了，上面那一格的绿不算数",
            Regex(Regex.escape("LbAsyncState(")).containsMatchIn(code)
        )
    }

    /**
     * 反向证人之二：「按设计只搬一半」那两处（判决 6 与 ⑧之外的捕获行）必须真的
     * 把设计系统调用留在调用方，而不是被整颗搬进去之后留了一条指向已改文件的旧判决。
     * 这里钉的是捕获行那颗 Checkbox 留在 `leading` 槽这条契约——
     * 它消失说明有人把勾选框塞进了设计系统（那就是「行」长出第二种状态说法）。
     */
    @Test
    fun `the half-moved capture row keeps its checkbox in the caller leading slot`() {
        val code = codeOf(uiFile("home/CaptureAppsScreen.kt").readText())
        assertTrue(
            "CaptureAppRow 的勾选框应留在调用方 leading 槽（判决 8 同族契约）——" +
                "设计系统不该认识 Checkbox 这个具体控件。Checkbox( 消失说明有人" +
                "把勾选框塞进了 LbSettingRow，那就是「行」长出第二种状态说法",
            Regex(Regex.escape("leading = {")).containsMatchIn(code) &&
                Regex(Regex.escape("Checkbox(")).containsMatchIn(code)
        )
    }
}
