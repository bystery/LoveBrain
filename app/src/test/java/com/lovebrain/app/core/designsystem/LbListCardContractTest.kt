package com.lovebrain.app.core.designsystem

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 列表卡那一族的**形状合同**（基线 v1.1 §3.4「描边管静息、阴影管浮起」+ §3.6「卡高由内容给」+ §3.5「动作只有一个所有者」）。
 *
 * ## 为什么这一族只能按源码形状判
 *
 * 语义树里没有 elevation、没有描边宽度、也没有字号：`shadow(4)` 换成 `Border 1` 这件事
 * 在树上**一个读数都不动**（本仓库把这条坑记在 `LongProviderNameSemanticsTest` 的文件头：
 * 文本判据无牙，几何判据也看不见"底是描边还是投影"）。所以这里用仓库里那把共用的尺
 * [SourceScan] 按形状判，失败信息点名文件与行号；真实观感仍由截图那一格负责（本机无设备，已登记未验证）。
 *
 * ## 每一句"回退成什么会红"
 *
 * | 判据 | 回退成什么就红 |
 * | --- | --- |
 * | 卡底无 `.shadow(` | 把母版旧档 `shadow(ELEVATION_MAX)` 抄回来 |
 * | 卡底有且只有 1 条 `Border` 1dp 描边 | 描边删了（卡与底同色）、或描边又叠回阴影（§3.4 禁"shadow + border 双叠"） |
 * | 卡体无 `heightIn(` | 给卡写 `heightIn(min = …)`（§3.6 明令不许） |
 * | 圆角只有一档 `Lg` | 换成 `md`/`xl`，或描边圆角与填充圆角不一致（§8 规则 R2） |
 * | 动作行只 call `LbTextAction`、且是 `RowCapsule` 档 | 页面/组件里再画一颗 `Box + clickable`（第四种动作写法） |
 * | 案例页不再自绘卡底 | `FeedbackCasesScreen.kt` 里又出现裸 `Card(` 或 `shadow(` |
 * | 母版页（知识库一级）也不再自绘卡底 | `KnowledgeBaseActivity.kt` 里复活旧母版的 `Card(` / `shadow(` / 三种自绘动作写法（改名透明盒、`Box+clickable`、`RowActionButton`）、或页面自己抄 `｜` |
 * | 全站那颗 48 不抄第二份 | 这一族里写 `48.dp` 字面量（`TouchFloorSingleOwnerTest`（已删）同样会红，这里先在本族门口挡住） |
 *
 * ⚠ 每一格都带**反向证人**：判据自己必须看得见坏形状，否则"数到 0"就是恒绿的假闸
 * （`SourceScanTest` 那几格是这把尺本人的证人，这一格再加一条本族专用的注件）。
 */
class LbListCardContractTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    /** 剥过注释的代码（注释里写 `shadow(` 不算一处——那些话恰恰是这条判据的说明） */
    private fun codeOf(path: String): String {
        val f = File(appRoot, path)
        assertTrue("找不到 $path——这一格会扫了个空集恒绿", f.isFile)
        return SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    private val listCardCode: String get() = codeOf("core/designsystem/LbListCard.kt")
    private val casesCode: String get() = codeOf("ui/feedback/FeedbackCasesScreen.kt")
    private val kbPageCode: String get() = codeOf("ui/KnowledgeBaseActivity.kt")

    /**
     * 只留**正文**：`import androidx.compose.foundation.clickable` 那一行里也含 `.clickable`，
     * 拿整份文件数"自绘可点控件有几颗"会把那条 import 数成一颗（本机第一次跑就红在这里：
     * 实到 2、期望 1，多出来的那半颗根本不是控件）。判"页面自己画了几处交互"一律先摘 import。
     */
    private fun String.withoutImports(): String =
        lines().filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")

    private fun count(code: String, regex: String) = Regex(regex).findAll(code).count()

    /** §3.4：列表卡的静息结构由描边管，阴影那一档不在这一族里 */
    @Test
    fun `the list card is stroked, not elevated`() {
        val code = listCardCode
        assertEquals(
            "F1 列表卡不许有阴影（基线 §3.4：`ELEVATION_MAX` 只留给浮层投影，卡内禁止 shadow + border 双叠）。" +
                "实到 ${count(code, """\.shadow\(""")} 处",
            0, count(code, """\.shadow\(""")
        )
        assertEquals(
            "卡底应当恰好一条描边，实到 ${count(code, """\.border\(""")} 处",
            1, count(code, """\.border\(""")
        )
        assertEquals(
            "那条描边的宽度必须读 `AppDimens.BORDER_WIDTH_DP`（1dp 那一档的主人），不许在这一族里写数",
            1, count(code, """\.border\(AppDimens\.BORDER_WIDTH_DP\.dp""")
        )
        assertEquals(
            "描边的颜色必须是词表里那颗 `Border`（开 `borderColor` 旋钮就等于每页自选一对颜色）",
            1, count(code, """\.border\(AppDimens\.BORDER_WIDTH_DP\.dp, Border,""")
        )
        // 反向证人：这把尺必须看得见坏形状
        assertEquals(
            "注件没就位：把 shadow 抄回来时这一句应当数到 1，实到",
            1, count("Modifier.shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)", """\.shadow\(""")
        )
    }

    /** §3.6：卡高由内容给——这一族里一处 `heightIn` 都不许有（胶囊那两层在 LbTextAction 自己家里） */
    @Test
    fun `the list card never gives itself a height floor`() {
        val code = listCardCode
        assertEquals(
            "列表卡里出现 `heightIn(` = 卡高不再由内容给（基线 §3.6 明令）。实到 ${count(code, """heightIn\(""")} 处",
            0, count(code, """heightIn\(""")
        )
        assertEquals(
            "同样不许用 `requiredHeight` 绕过那一句", 0, count(code, """requiredHeight\(""")
        )
        assertEquals(
            "注件没就位：合成一颗 `heightIn(min = 96.dp)` 的卡体应当数到 1",
            1, count("Modifier.heightIn(min = 96.dp)", """heightIn\(""")
        )
    }

    /** §8 规则 R2：描边圆角必须等于填充圆角，而这一族只许有一档卡片圆角 */
    @Test
    fun `the list card carries exactly one corner tier`() {
        val code = listCardCode
        // 填充那一档与描边那一档各写一次，且**必须是同一个名字**（这两句合起来就是 R2 的形状判据）
        assertEquals("Card 的填充圆角应当恰好一处 `shape = LoveBrainShape.lg`",
            1, count(code, """shape = LoveBrainShape\.lg"""))
        assertEquals("描边的圆角必须是同一档 lg（§8 R2：描边圆角 == 填充圆角）",
            1, count(code, """\.border\(AppDimens\.BORDER_WIDTH_DP\.dp, Border, LoveBrainShape\.lg\)"""))
        assertEquals("这一族不许出现第二档卡片圆角（`md`/`xl`/`sm`）——同屏三档圆角的上限从这里开始算",
            0, count(code, """LoveBrainShape\.(md|xl|sm)"""))
        // 反向证人：合成一条 `LoveBrainShape.md` 必须被这一句数到，否则"数到 0"可能就是尺瞎了
        assertTrue(
            "注件没就位：合成一句 `clip(LoveBrainShape.md)` 应当数到 1",
            count("Box(modifier = Modifier.clip(LoveBrainShape.md))", """LoveBrainShape\.(md|xl|sm)""") == 1
        )
    }

    /**
     * §3.5 动作矩阵：这一族的行内动作**只有一个所有者**——`LbTextAction` 的 `RowCapsule` 档。
     *
     * 与 `UiLayerDependencyContractTest > the design-system text action has exactly one owner` 的分工：
     * 那一格管 `LbTextAction.kt`/`LbAsyncState.kt` 自己家里那两层热区，这一格管**新主人不许又画一颗**。
     */
    @Test
    fun `the list card action row delegates to the one text action owner`() {
        val code = listCardCode
        assertEquals("动作行应当恰好 call 一次 `LbTextAction(`", 1, count(code, """\bLbTextAction\("""))
        assertEquals("而那一颗必须是 `RowCapsule` 档（可见 32 / 热区 48），不是 `Standard` 那种 48 见方",
            1, count(code, """size = LbTextActionSize\.RowCapsule"""))
        assertEquals(
            "整卡只许一处 `.clickable(`（卡自己那一处）；多出来的一颗就是「又长一种动作写法」：" +
                "实到 ${count(code, """\.clickable\(""")} 处",
            1, count(code, """\.clickable\(""")
        )
        assertEquals("这一族里不许写 `Box(size(48))` 那种自绘热区盒", 0, count(code, """size\(48"""))
        // 反向证人：注一件「卡里又自己画了一颗动作」的形状，必须被这一句数到
        assertEquals(
            "注件没就位：合成两处 clickable 时这一句应当数到 2",
            2, count(
                "Modifier.clickable(role = Role.Button, onClick = onClick)\n" +
                    "Box(modifier = Modifier.clickable(onClick = { }))",
                """\.clickable\("""
            )
        )
    }

    /** 48 那颗全站下限在本族里只许是被读，不被抄（`TouchFloorSingleOwnerTest`（已删）在本族门口先挡一层） */
    @Test
    fun `the list card family writes no second copy of the site floor`() {
        listOf("core/designsystem/LbListCard.kt" to listCardCode, "ui/feedback/FeedbackCasesScreen.kt" to casesCode)
            .forEach { (path, code) ->
                assertEquals(
                    "$path 里出现了内联的 `48.dp`：全站那颗下限只许写在 Dimens.kt 一次，" +
                        "要热区就读 `AppDimens.TOUCH_TARGET_MIN_DP`",
                    0, count(code, """(?<![\d.])48\.dp""")
                )
            }
        assertEquals("注件没就位：合成一句 `48.dp` 应当数到 1", 1, count("Modifier.widthIn(min = 48.dp)", """(?<![\d.])48\.dp"""))
    }

    /**
     * 案例页**不再自绘卡底**：整页只经那一颗公共件，`shadow` 与裸 `Card(` 一起退场。
     *
     * 这一格是"复用母版 vs 只换了个壳"最硬的一条：只把旧代码改名字、壳仍自己画，`Card(` 那一处就在；
     * 反过来把卡片搬进公共件，页面只剩一次 `LbListCard(` 调用。
     */
    @Test
    fun `the cases page no longer draws its own card`() {
        val code = casesCode
        assertEquals("案例页不许再有阴影（母版旧档已被 §3.4 作废）", 0, count(code, """\.shadow\("""))
        assertEquals("案例页不许再自绘 Material `Card(`（卡底的主人现在是 `LbListCard`）",
            0, count(code, """(?<![\w.])Card\("""))
        // 裸标识符的计数点先摘 import：`import androidx.compose.material3.CardDefaults` 那条
        // 光剩的名字不是"这一页在读它"（带左括号的针脚 import 里出不来，无须套）。
        assertEquals("案例页不许再读 `CardDefaults`", 0, count(code.withoutImports(), "CardDefaults"))
        assertEquals("列表每一张卡都走那一颗公共件：恰好一处调用（页面自己不许再抄第二份）",
            1, count(code, """\bLbListCard\("""))
        assertEquals("整页不再有自绘可点控件：导出那颗已并进设计系统 `LbPrimaryButton`（旧缺件已销行）",
            0, SourceScan.clickableOffsets(casesCode.withoutImports()).size)
        // 反向证人两半：① 摘 import 这条尺本身要有牙——合成一句真控件必须数得到；
        // ② 那句 import 本身不许被算成控件（否则"实到 1"永远查不到多出来的那半颗从哪来）。
        assertEquals(
            "注件没就位：正文里一句 `Modifier.clickable(onClick = { })` 应当数到 1",
            1, SourceScan.clickableOffsets("Box(modifier = Modifier.clickable(onClick = { }))").size
        )
        assertEquals(
            "尺不许把 `import androidx.compose.foundation.clickable` 数成一处自绘控件" +
                "（流水线和上面「整页零自绘」那句、母版页那一格一致：先 withoutImports 再数——" +
                "上一版这半句证人忘了套，量的其实是没摘 import 的整份文本，B 档仪器坏）",
            0, SourceScan.clickableOffsets(
                ("import androidx.compose.foundation.clickable\n" + "import androidx.compose.foundation.layout.Box")
                    .withoutImports()
            ).size
        )
        assertEquals("注件没就位：合成一次 `LbListCard(` 调用应当数到 1",
            1, count("LbListCard(title = x)", """\bLbListCard\("""))
    }

    /**
     * 案例页不许把"待分析"那一类诊断字段又写回屏上。
     *
     * 语义树那边由 `FeedbackCasesSemanticsTest` 的哨兵钉着，这一格钉的是**代码那扇门**：
     * 资源名 `feedback_status_pending` / `feedback_context_mode` / `feedback_app_version` /
     * `feedback_token_usage` / `feedback_memory_refs` / `feedback_card_header`（空「【】」那一排的主人）
     * 一旦回到这一页，屏上就一定长出对应那一行——回退成什么会红：任何一条被重新引用。
     */
    @Test
    fun `the cases page keeps the deleted diagnostic fields out`() {
        val code = casesCode
        listOf(
            "feedback_status_pending",   // 《待分析》
            "feedback_context_mode",     // 上下文模式：full
            "feedback_app_version",      // 版本：1.40-re1（release）
            "feedback_token_usage",      // tokens
            "feedback_memory_refs",      // 内部记忆引用
            "feedback_card_header",      // 空的「【】」标题排
            "feedback_filter_all",       // 分类 chips 那一排
            "feedback_format_markdown",  // Markdown/JSON 切换
            "feedback_export_markdown"
        ).forEach { res ->
            assertEquals(
                "这一页又引用了被删掉的展示位资源 R.string.$res——那一行就会回到屏上",
                0, count(code, Regex.escape("R.string.$res"))
            )
        }
        assertEquals("feedback_status_pending 不应再被引用",
            0, count(code, Regex.escape("R.string.feedback_status_pending")))
    }

    /**
     * 母版页（知识库一级）自己也接上公共件——这是"母版"两个字最后一次还欠着的那笔。
     *
     * 用户原话第 2/3 条是"几个管理页各自一套格式 / 列表卡片这一族没有统一"。案例页接上
     * `LbListCard` 只还了一半：母版当时还自己画卡，而且**同一张卡里长着三种动作写法**
     * （改名的自绘透明盒、删除的 `Box + clickable`、元信息行里的 `RowActionButton`）。
     * 这一格把案例页那格的判据**原样扩**到母版页，方向一字未松；每一句都是形状判据，
     * 真实观感仍由截图那一格负责（本机无设备，已登记未验证）。
     *
     * 2026-10-XX：删除从动作行挪到顶右角的尾图标（`trailingIcon` 槽），动作行让位给
     * rename/edit/export 三颗次级胶囊。这一改把「重命名」从 `detail` 槽接回 `actions`，
     * 「⋯ 溢出档」那条债一并还清——四件事都在屏上、且都走动作行/尾图标这两条有主人的路。
     *
     * 每一句"回退成什么会红"：
     * | 判据 | 回退成什么就红 |
     * | --- | --- |
     * | `.shadow(` = 0 | 旧母版 `:399` 那颗 `shadow(ELEVATION_MAX)` 抄回来（§3.4 已把列表卡的阴影档作废） |
     * | 裸 `Card(` / `CardDefaults` = 0 | 卡底从公共件退回页面自绘（`KbCard(` 有前导词母，不被 `(?<![\w.])` 误计） |
     * | `LbListCard(` = 恰好 1 | 页面又抄第二份壳，或公共件调用被摘掉退回自绘 |
     * | 自绘 clickable = 0 | 三种旧动作写法里任何种回来（透明改名盒 / 删除盒 / 行内 `RowActionButton` 的底层 clickable）——接好之后这一页**一颗**自绘可点控件都不该有，唯一那处 clickable 在 `LbListCard.kt` 自己家里 |
     * | `size(48` / `48.dp` = 0 | "把盒子垫到 48"的旧热区写法回来（三轴分离 §3.1：热区归 `LbTextAction`，不归页面） |
     * | `RowActionButton(` = 0 | 第三种动作写法重新进这一页 |
     * | `｜` = 0 | 页面又开始自己拼元信息行——分隔符唯一主人是 `lbMetaLine`（本文件那颗组件的 :105），长第二个主人处，下一次统一就只有一处生效 |
     * | secondary 恰好 3、destructive 恰好 0 | rename/edit/export 三颗次级胶囊；删除挪去尾图标，动作行不再有 destructive 档 |
     * | `LbTextAction(` 恰好 1 且 `LbTextActionTone.Destructive` 1 / `RowSecondary` 0 / `RowCapsule` 0 | 尾图标那颗垃圾桶走 `LbTextAction` 图标档（destructive 语气），`detail` 槽已退场——页面不再自报 RowSecondary/RowCapsule |
     */
    @Test
    fun `the master page no longer draws its own card`() {
        val code = kbPageCode
        assertTrue("找不到知识库一级页——这一格会扫了个空集恒绿", File(appRoot, "ui/KnowledgeBaseActivity.kt").isFile)
        assertEquals("母版页不许有阴影（旧档 `shadow(ELEVATION_MAX)` 已被 §3.4 作废）",
            0, count(code, """\.shadow\("""))
        assertEquals("母版页不许再自绘 Material `Card(`（卡底的主人现在是 `LbListCard`）",
            0, count(code, """(?<![\w.])Card\("""))
        assertEquals("母版页不许再读 `CardDefaults`", 0, count(code.withoutImports(), "CardDefaults"))
        assertEquals("列表每一张卡都走那一颗公共件：恰好一处调用",
            1, count(code, """\bLbListCard\("""))
        val selfDrawnClick = SourceScan.clickableOffsets(code.withoutImports()).size
        assertEquals("母版页里不许再出现任何一种自绘可点控件（三种动作写法并成一种后应为 0），实到 $selfDrawnClick 处",
            0, selfDrawnClick)
        assertEquals("旧写法「把盒子垫到 48」不许回来", 0, count(code, """size\(48"""))
        assertEquals("全站那颗 48 不许在母版页抄第二份", 0, count(code, """(?<![\d.])48\.dp"""))
        assertEquals("第三种动作写法 `RowActionButton` 已退出这一页", 0, count(code, """RowActionButton\("""))
        assertEquals("元信息分隔符的主人是 `lbMetaLine`：母版页不许自己抄「｜」", 0, count(code, "｜"))
        assertEquals("动作行恰好三颗次级（重命名/编辑/导出）", 3, count(code, """LbListCardAction\.secondary\("""))
        assertEquals("删除已挪去尾图标，动作行不再有 destructive 档", 0, count(code, """LbListCardAction\.destructive\("""))
        assertEquals("尾图标那颗垃圾桶走 `LbTextAction` 图标档（detail 槽已退场，全页只剩这一处 LbTextAction 调用）",
            1, count(code, """\bLbTextAction\("""))
        assertEquals("detail 槽退场：页面不再自报 RowCapsule 档（动作行三颗由公共件自己 call LbTextAction）",
            0, count(code, """size = LbTextActionSize\.RowCapsule"""))
        // 语气这两针脚也是裸标识符（`import …LbTextActionTone.Destructive` 这种枚举项 import
        // 是合法写法）：同族规矩，先摘 import 再数，判据本身一寸不松。
        assertEquals("detail 槽退场：页面不再自报 RowSecondary 语气", 0, count(code.withoutImports(), """LbTextActionTone\.RowSecondary"""))
        assertEquals("删除那颗尾图标自报 Destructive 语气（恒红，页面改不了）",
            1, count(code.withoutImports(), """LbTextActionTone\.Destructive"""))

        // ── 反向证人：这把尺每一句都得看得见对应的坏形状，否则"数到 0"是恒绿的假闸 ──
        assertEquals("注件没就位：合成一句 `shadow(` 应当数到 1",
            1, count("Modifier.shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)", """\.shadow\("""))
        assertEquals("注件没就位：合成一句裸 `Card(` 应当数到 1，而 `KbCard(` 不该被误计",
            1, count("Card(shape = LoveBrainShape.lg) { }\nKbCard(kb = kb)", """(?<![\w.])Card\("""))
        assertEquals("注件没就位：合成两处 LbListCard 调用应当数到 2",
            2, count("LbListCard(title = a)\nLbListCard(title = b)", """\bLbListCard\("""))
        assertEquals("注件没就位：合成一颗 `Box + clickable` 应当被那把尺数到 1",
            1, SourceScan.clickableOffsets("Box(modifier = Modifier.clickable(onClick = { }))").size)
        assertEquals("注件没就位：合成一句 `size(48.dp)` 应当数到 1",
            1, count("Box(modifier = Modifier.size(48.dp))", """size\(48"""))
        assertEquals("注件没就位：合成一句 `RowActionButton(` 应当数到 1",
            1, count("RowActionButton(\"编辑\") { onEdit() }", """RowActionButton\("""))
        assertEquals("注件没就位：页面合成一句带「｜」的元信息应当数到 1",
            1, count("Text(\"阶段：热恋期 ｜ 已对话 12 轮\")", "｜"))
        assertEquals("注件没就位：把删除那颗换热区前缀（页面自报 Destructive）应当数到 1",
            1, count("LbTextAction(label = \"删除\", tone = LbTextActionTone.Destructive)", """LbTextActionTone\.Destructive"""))
    }
}
