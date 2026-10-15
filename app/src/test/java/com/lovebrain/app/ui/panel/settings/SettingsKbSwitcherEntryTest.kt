package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.KnowledgeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 设置页那一格**当前知识库**的形状合同（2026-10-12 原型形状，原型换算表 `.kb` / `.arrow` /
 * `.kb-options` / `.kb-opt` 四行换算）。
 *
 * 这一族要钉的是五件"看界面一眼看不出、坏了却直接骗到用户"的事：
 * 1. **收起态只占一行**：行根 `settings-row-kb` 恰一颗、高不过一行版式；没点开之前，别的库
 *    根本不在树上——旧形状把所有知识库永久逐条铺在设置页上，一屏之下别的事情会被挤出视口；
 * 2. **展开触发挂在行根那一颗**（`settings-row-kb`，整行点得着），行标签归整页唯一那颗
 *    `LbRowLabel`；箭头是**文字符号**（收起 `›`、展开 `⌄`，原型 `.arrow` 17 字阶），
 *    换档必须跟着开合走——旧那枚 `Icon + rotate` 不是原型那张脸；
 * 3. **点开与收起是真开合**：收起之后那些节点必须从语义树上消失（不是"画着但看不见"），
 *    展开那一段用的又是一颗真正的纵向容器；
 * 4. **切换成功之前不涂成功**：点非当前那一颗只交出一句请求，屏上那个名字仍读宿主那一份；
 *    宿主没换（= 盘上没落地，切库失败正是这一条路径）名字就不许换；宿主换了名字才跟着换，
 *    而且同一时刻只许指着两块库里的一个；
 * 5. **当前那条在列表里高亮且按不动**（原型 `.kb-opt.current`：浅蓝底 + 深一档的字，颜色走
 *    源码尺那一半、禁用语义走语义树那一半），条目档取 `ROW_ACTION_COMPACT_MIN_HEIGHT_DP`(32)
 *    那颗**有意低于**全站下限的明写档，主人是 core 具名档，不是页面抄的第二份数。
 *
 * 文案锚点一律 `getString` 取：这台机器的环境解析出来是英文（前例写在
 * `ProviderFormSemanticsTest` 的文件头），写死中文会让判据在谁都没改代码的日子里假红。
 * 箭头两枚是**排印符号**（不随语言走），判据里按原型原文的字面写死，正是"换字形要当场红"的那颗牙。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsKbSwitcherEntryTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private val currentLabel: String get() = ctx.getString(R.string.settings_kb_current_label)
    private val expandName: String get() = ctx.getString(R.string.action_expand)
    private val collapseName: String get() = ctx.getString(R.string.action_collapse)

    /** 原型与原型换算表点名的两枚箭头字形（`.arrow`，收起 `›`、展开 `⌄`） */
    private val arrowClosed = "›"
    private val arrowOpen = "⌄"

    /** 展开条目的档：指回 core 那颗具名矮档（32），不是测试自己抄的第二份数 */
    private val optionFloor = AppDimens.ROW_ACTION_COMPACT_MIN_HEIGHT_DP.toFloat()

    /** 目录扫描交回的那一本库（显示名与文件夹名同值，与设置页真正拿到的形状一致） */
    private fun library(name: String, display: String = name) =
        KnowledgeBase(
            name = name,
            displayName = display,
            updatedAt = "2026-10-11T09:00:00+08:00",
            active = true
        )

    /**
     * 一次挂载。**每个用例只能 `setContent` 一次**（同一用例里第二次挂载会被框架当场拒掉，
     * 那一段所有断言从未执行过——这条坑写在 `SettingsPageSemanticsTest` 的文件头）。
     * [activeKb] 是宿主那一份真状态：用例用 `runOnIdle` 翻它，模拟"写盘回来之后才换人"。
     */
    private fun mount(
        knowledgeBases: List<KnowledgeBase>,
        activeKb: MutableState<String?>,
        switches: MutableList<String>,
        matrix: UiMatrix = UiMatrix(360, heightDp = 400)
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                SettingsKbSwitcherEntry(
                    knowledgeBases = knowledgeBases,
                    activeKbName = activeKb.value,
                    onSwitchKb = { name -> switches += name }
                )
            }
        }
        rule.waitForIdle()
    }

    /**
     * 行根读数。⚠ 按**未合并**那一侧取：行根只有 testTag、没有点击语义，
     * 拿合并树去数会永远数到 0（本仓既有先例，`SettingsIntentRowTest.bucket` 同一写法）。
     */
    private fun rowRoot(): SemanticsProbe.Target {
        val nodes = rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("行根该恰一颗（tag=$SETTINGS_ROW_KB_TAG，实到 ${nodes.size}）", 1, nodes.size)
        return probe.of(nodes.single())
    }

    private fun toggleNode(): SemanticsNode =
        rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG)).fetchSemanticsNodes().singleOrNull()
            ?: error("展开触发那一段不在树上（tag=$SETTINGS_ROW_KB_TAG）")

    /**
     * 那一行交出的文案。⚠ 读**合并之后**的那一颗：点击挂在行根上（整行点得着），行标签、库名与
     * 箭头都是它的子节点，语义树把子文案并进带点击语义的那一层——所以这一份读数就是
     * "手指与读屏实际信的那一颗"：它既钉住**库名**、也钉住**箭头的字形**（换字形、箭头不跟着换都红），
     * 还钉住这一行开口先说的是"当前知识库"这件事。
     */
    private fun toggleTexts(): List<String> =
        toggleNode().config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()

    private fun optionCount(): Int =
        rule.onAllNodes(hasTestTag(SETTINGS_KB_OPTION_TAG)).fetchSemanticsNodes().size

    /** 展开那一段外层容器的颗数（同样只有 tag、没有点击语义 ⇒ 按未合并那一侧读） */
    private fun optionsColumnCount(): Int =
        rule.onAllNodes(hasTestTag(SETTINGS_KB_OPTIONS_TAG), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun nodeCount(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /** 展开列表里那几颗的读数（按 tag 取，再读各自的禁用状态） */
    private fun optionNodes(): List<SemanticsProbe.Target> =
        rule.onAllNodes(hasTestTag(SETTINGS_KB_OPTION_TAG)).fetchSemanticsNodes().map { probe.of(it) }

    private fun openOptions() {
        rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG))[0].performClick()
        rule.waitForIdle()
    }

    private val SemanticsProbe.Target.rightEdge: Float get() = leftDp + widthDp

    @Test
    fun `collapsed state is one row with the name right-aligned and the closed arrow glyph`() {
        val switches = mutableListOf<String>()
        val activeKb = mutableStateOf<String?>("kbA")
        mount(knowledgeBases = listOf(library("kbA"), library("kbB")), activeKb = activeKb, switches = switches)

        assertEquals("行根恰一颗（多一颗就是又铺了一遍列表）", 1,
            rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG), useUnmergedTree = true)
                .fetchSemanticsNodes().size)
        assertEquals("默认不展开：可用项一颗都不在树上", 0, optionCount())
        assertEquals("别的库不许永久占着设置页", 0, nodeCount("kbB"))
        assertEquals("行标签是整页唯一那一颗、恰一句", 1, nodeCount(currentLabel))
        assertEquals(
            "可点那一段念两件事：宿主那份当前库名 + 收起档的箭头字形（原型 `.kb`+`.arrow`）",
            listOf(currentLabel, "kbA", arrowClosed), toggleTexts()
        )

        // 「只占一行」的两半：行骨架到下限（48 = 原型 `.item` 的 49）又不许被撑成一条卡片
        val row = rowRoot()
        assertFalse("收起态那一行不到全站下限 ${TouchTier.SITE_FLOOR.toInt()}dp：" + row.describe(),
            row.tooSmall(TouchTier.SITE_FLOOR))
        assertTrue(
            "收起态那一行高过一行版式（实到 ${row.heightDp.toInt()}dp）——紧凑靠少一层容器，" +
                "不是靠把行撑高：" + row.describe(),
            row.heightDp <= TouchTier.SITE_FLOOR + 8f
        )
        // 热区挂在**带点击语义的那一段**自己身上（外面套一层大盒子等于没改）
        val toggle = probe.of(toggleNode())
        assertFalse("展开触发那一段不到全站下限：" + toggle.describe(),
            toggle.tooSmall(TouchTier.SITE_FLOOR))
        // 名字+箭头贴右缘（原型 `.action-area` 靠右那一侧）：换成靠左或中间塞空盒都会分开
        assertEquals("库名+箭头没贴到行的右缘（差 ${row.rightEdge - toggle.rightEdge}dp）：" +
            toggle.describe() + " / " + row.describe(), row.rightEdge, toggle.rightEdge, 0.6f)

        assertEquals("挂载本身不投任何切库请求", 0, switches.size)
        assertTrue(
            "可点那一段要说得出「现在点开」：" + toggle.describe(),
            probe.of(toggleNode()).contentDescriptions.contains(expandName)
        )
    }

    @Test
    fun `tapping the toggle opens the list on one real column and tapping it again really closes it`() {
        val switches = mutableListOf<String>()
        val activeKb = mutableStateOf<String?>("kbA")
        mount(knowledgeBases = listOf(library("kbA"), library("kbB")), activeKb = activeKb, switches = switches)

        openOptions()
        assertEquals("点开之后可用项各一颗", 2, optionCount())
        assertEquals(
            "那一列的外层是一颗真正的纵向容器（读它要按未合并那一侧：只有 tag 的节点不进合并语义树）",
            1, optionsColumnCount()
        )
        assertEquals(
            "展开之后箭头字形必须跟着换成「收起」档——旧那枚 Icon+rotate 长回来的话这里红",
            listOf(currentLabel, "kbA", arrowOpen), toggleTexts()
        )
        val opened = probe.of(toggleNode())
        assertTrue(
            "展开之后读屏要说得出「现在收起」：" + opened.describe(),
            opened.contentDescriptions.contains(collapseName)
        )
        assertFalse(
            "同一颗不许同时念「展开」和「收起」（读屏就会念两遍）：" + opened.describe(),
            opened.contentDescriptions.contains(expandName)
        )
        assertEquals("当前那一本仍在列里（它就是答案）", 1,
            optionNodes().count { it.label.contains("kbA") })

        rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG))[0].performClick()
        rule.waitForIdle()
        // 这一句是那一族坑的格子：把可见性写死成"永远展开"，这里数到的仍是 2
        assertEquals("再点一次是真收起——那些节点必须从树上消失", 0, optionCount())
        assertEquals("收起之后连那一列的容器都不在树上", 0, optionsColumnCount())
        assertEquals("收起之后箭头换回收起档、名字仍读宿主那一份",
            listOf(currentLabel, "kbA", arrowClosed), toggleTexts())
        assertEquals("开合这一件事不投任何切库请求", 0, switches.size)
    }

    /**
     * 切换成功的判据只有一条：宿主那份真状态换了人。
     *
     * 两半都在这一格里——
     * · **失败那一半**：请求交出去之后宿主没换（盘上没落地时就是这一态），屏上那个名字一个字
     *   都不许动，旧名字不许被涂成新库；
     * · **成功那一半**：宿主换人之后名字才跟着换，并且当场只剩一颗新名字（不许两块库同时被
     *   指着说"当前"）。
     */
    @Test
    fun `the shown library name only changes after the host says the switch landed`() {
        val switches = mutableListOf<String>()
        val activeKb = mutableStateOf<String?>("kbA")
        mount(knowledgeBases = listOf(library("kbA"), library("kbB")), activeKb = activeKb, switches = switches)

        openOptions()
        rule.onAllNodes(hasText("kbB"))[0].performClick()
        rule.waitForIdle()

        assertEquals("点非当前那一颗只投一次切库请求", listOf("kbB"), switches.toList())
        assertEquals("请求交出去就把列表收起", 0, optionCount())
        assertEquals(
            "切换还没落地（宿主那份没换＝失败那一路）：屏上不许先涂成成功",
            listOf(currentLabel, "kbA", arrowClosed), toggleTexts()
        )

        rule.runOnIdle { activeKb.value = "kbB" }
        rule.waitForIdle()
        assertEquals("落地之后名字才跟着换", listOf(currentLabel, "kbB", arrowClosed), toggleTexts())
        assertEquals("旧名字当场消失，同一时刻只许指着一块库", 0, nodeCount("kbA"))
        assertEquals(
            "这一格不替切库写第二本账：除了那一句请求，别的什么都没投",
            listOf("kbB"), switches.toList()
        )
    }

    /**
     * 当前那一本在列表里**看得见但按不动**：它已经是答案，再点一次不该重复投请求；
     * 而"按不动"要读得出（`enabled = false` 交出禁用语义），不能靠把它藏起来实现。
     * 高亮那张脸（浅蓝底 + 深字）是颜色，语义树根本报不出色 ⇒ 由下面那格源码尺钉。
     */
    @Test
    fun `the library already in use is listed disabled at the compact floor and cannot be picked again`() {
        val switches = mutableListOf<String>()
        val activeKb = mutableStateOf<String?>("kbA")
        mount(knowledgeBases = listOf(library("kbA"), library("kbB")), activeKb = activeKb, switches = switches)
        openOptions()

        val current = optionNodes().firstOrNull { it.disabled }
        assertTrue("展开列表里当前那一颗必须报禁用语义（藏起来不算），实到：" +
            optionNodes().joinToString { it.describe() }, current != null)
        assertTrue(
            "当前那一颗自己也要够条目那一档（${optionFloor.toInt()}dp，core 具名矮档，不是页面抄的第二份数）：" +
                current!!.describe(),
            !current.tooSmall(optionFloor)
        )
        assertEquals("当前那一颗仍写在展开列表里（看得见自己是答案）", 1,
            optionNodes().count { it.label.contains("kbA") })
        assertEquals("另一颗是可点的（不许整列都被做成禁用的死列表）", 1,
            optionNodes().count { !it.disabled && it.label.contains("kbB") })

        // 用真指针点它：`performClick` 走语义动作、绕过命中测试，量不出"按不动"这件事；
        // 点完之后既没投请求、列表也还开着（投出去的话这一格会先把列表收起）
        val currentIdx = optionNodes().indexOfFirst { it.disabled }
        rule.onAllNodes(hasTestTag(SETTINGS_KB_OPTION_TAG))[currentIdx].performTouchInput { click() }
        rule.waitForIdle()
        assertEquals("点当前那一颗不许再投一句", emptyList<String>(), switches.toList())
        assertEquals("点不动的那一颗也没把这一列收掉", 2, optionCount())
    }

    /**
     * 只有一本库时这一行**照样点得开**：箭头照画、列表照出——用户要能亲眼看到
     * "目录里就这一本"，而不是一颗点不动的死标签。
     *
     * ⚠ 2026-10-15 真机报的形状教训：上一版在这里判 `canOpen = others.isNotEmpty()`，
     * 只有一本时藏箭头、禁用整行——真机目录一旦只剩一本（换签名卸载重装、库丢失、
     * 扫描丢库都会走到这一态），这一格就成了没有箭头、没有水波纹、按不动的"文字摆设"，
     * 而 1.4.0 的平铺卡在同一个目录读数下照样画出那一行蓝底。这格合同替那个形状守了三轮门，
     * 本轮把它翻过来：目录少库必须画成看得见的列表，不许画成失语。
     * 当前那一本在列表里仍**按不动**（它已经是答案），展开/收起照常翻面。
     */
    @Test
    fun `a single library still shows which one is in use and the row still opens`() {
        val switches = mutableListOf<String>()
        val activeKb = mutableStateOf<String?>("kbA")
        mount(knowledgeBases = listOf(library("kbA")), activeKb = activeKb, switches = switches)

        assertEquals("那一行还在（用户仍要看得见此刻用哪一本）", 1,
            rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG), useUnmergedTree = true)
                .fetchSemanticsNodes().size)
        assertEquals("收起档的箭头照画：行标签、库名、箭头三颗文案都在",
            listOf(currentLabel, "kbA", arrowClosed), toggleTexts())
        val toggle = probe.of(toggleNode())
        assertFalse("整行不许是禁用态——死标签就是本轮要废掉的形状：" + toggle.describe(),
            toggle.disabled)
        assertTrue("读屏说得出「现在点开」：" + toggle.describe(),
            toggle.contentDescriptions.contains(expandName))

        openOptions()
        assertEquals("点得开：列表恰一颗（当前那本自己，它就是答案）", 1, optionCount())
        assertEquals("展开之后箭头换到展开档", listOf(currentLabel, "kbA", arrowOpen), toggleTexts())
        val only = optionNodes().single()
        assertTrue("唯一那一颗就是当前那本（高亮且报禁用，再点不重复投）：", only.disabled &&
            only.label.contains("kbA"))

        rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG))[0].performClick()
        rule.waitForIdle()
        assertEquals("再点一次真收起——节点从树上消失", 0, optionCount())
        assertEquals("开合这一件事不投任何切库请求", 0, switches.size)
    }

    /**
     * 形状合同（源码级，补语义树量不到的那几件——颜色、字阶、来路与展开门）：
     * · 全量枚举只许出现在展开门后面——"永久逐条铺在设置页上"那一族旧规则到此为止；
     * · 展开那一段的第一颗布局节点必须是一颗真正的纵向 `Column`（`AnimatedVisibility` 不是
     *   纵向容器，多颗直接子节点会按 Box 叠放）；不许出现写死的 `visible = true`；
     * · 行骨架走族里那颗 `settingsEntryRow()`、行标签走整页唯一那颗 `LbRowLabel()`，
     *   **不借 `LbSettingRow`、不退回旧那枚灰字 `labelMedium` 行标签**；
     * · 箭头是 17 字阶的文字符号，旧 `Icon + rotate` 那枚形状不许长回来；
     * · 条目档那几颗数全部指回对照原型换算表 `.kb-opt` 那一行的落点：
     *   `ROW_ACTION_COMPACT_MIN_HEIGHT_DP` + `LoveBrainShape.sm` + 当前档
     *   `PrimaryLight` 底 / `PrimaryDark` 字、其余 `TextHint` 字、字号 `labelMedium`；
     * · 组壳不在这一颗里（外框归页面那一侧）。
     */
    @Test
    fun `the row takes the family shape and the whole-library enumeration stays behind the expand gate`() {
        val file = File("src/main/java/com/lovebrain/app/ui/panel/settings/SettingsKbSwitcherEntry.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/settings/SettingsKbSwitcherEntry.kt")
        assertTrue("找不到 $file——这把尺会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        assertTrue("掩码源码为空（扫了个寂寞的恒绿形状）", code.isNotBlank())

        val gate = Regex("""if \(expanded\) [{]""").findAll(code).toList()
        assertEquals("展开门恰好一处（多一处就是又长出第二份开合状态）", 1, gate.size)
        val afterGate = code.substring(gate.single().range.first)
        assertTrue(
            "展开门后第一段布局必须是一颗真正的 Column（多颗直接子节点在 AnimatedVisibility 里会叠放）",
            Regex("""\) [{]\s*Column\(""").containsMatchIn(afterGate.take(400))
        )
        val enumeration = Regex("""knowledgeBases\.forEach""").findAll(code).toList()
        assertEquals("全量枚举只许有一次，实到 ${enumeration.size}", 1, enumeration.size)
        assertTrue(
            "那一颗枚举必须在展开门后面——门外面永远逐条铺开就是这一格要废掉的旧规则",
            enumeration.single().range.first > gate.single().range.first
        )
        assertFalse("不许把可见性写死成 true（那样退场动画永远播不到）", code.contains("visible = true"))

        // 行族那一颗骨架与整页唯一那颗行标签（10-11 那一版的脸不对就从借字阶开始的）
        assertEquals("行骨架 settingsEntryRow() 恰一次（挂在行根）", 1,
            Regex("""settingsEntryRow\(\)""").findAll(code).count())
        assertEquals("行标签 LbRowLabel() 恰一次", 1,
            Regex("""LbRowLabel\(""").findAll(code).count())
        assertFalse("行壳不许借道 LbSettingRow（那是首页与捕获页的公共件）", code.contains("LbSettingRow"))
        assertFalse("组壳（外框/卡）归页面那一侧，这一颗只渲染组内那一行", code.contains("settingsEntryCard"))

        // 两档热区各自的主人：行根那一颗下限由 `settingsEntryRow()` 给（不在这颗文件里抄第二遍），
        // 展开列表里的每一条自己买一次（原型 `.kb-opt` 那 32 是**显示**那一档，32dp 在悬浮窗里点不中
        // ⇒ 有意不吃，理由写在那颗件的 KDoc 里）。所以这一格里 `TOUCH_TARGET_MIN_DP` 恰一处，
        // 多一处就是有人把行根那圈下限又抄了一遍。
        assertEquals("列表每一条自己垫全站那颗下限，恰一处（行根那一份归 settingsEntryRow() 给）", 1,
            Regex("""heightIn\(min = AppDimens\.TOUCH_TARGET_MIN_DP\.dp\)""").findAll(code).count())
        assertEquals("这一格不许拿明写的矮档当热区（32 那一档是版式档，不是命中档）", 0,
            Regex("""heightIn\(min = AppDimens\.ROW_ACTION_COMPACT_MIN_HEIGHT_DP\.dp\)""").findAll(code).count())

        // 箭头＝文字符号两档 + 17 字阶；旧 Icon 形状不许长回来
        assertTrue("收起档箭头字形（原型 `.arrow`）", code.contains("\"$arrowClosed\""))
        assertTrue("展开档箭头字形（原型 `.arrow`）", code.contains("\"$arrowOpen\""))
        assertEquals("箭头字阶那档数只写一次、按私有具名档立", 1,
            Regex("""ARROW_FONT_SIZE_SP = 17""").findAll(code).count())
        assertFalse("旧那枚 Icon 箭头不许回来（原型是 17px 文字符号，不是 18dp 图标）",
            code.contains("Icon(") || code.contains("KeyboardArrowRight") || code.contains("rotate("))

        // `.kb-opt` 换算表的落点：颜色语义树读不出，源码尺逐颗钉
        assertTrue("当前档浅蓝底按 `if (isCurrent) PrimaryLight else Color.Transparent` 涂一次",
            Regex("""\.background\(\s*if \(isCurrent\) PrimaryLight else Color\.Transparent,\s*LoveBrainShape\.sm""")
                .containsMatchIn(code))
        assertTrue("当前档的字深一档、其余 TextHint（`.kb-opt` / `.kb-opt.current` 的墨色）",
            code.contains("if (isCurrent) PrimaryDark else TextHint"))
        // 库名那一档墨色：原型 `.kb{color:#4c5870}` 是中灰（TextSecondary），不是近黑的正文色——
        // 这一格里"当前是哪本库"是被读的值。旧写法借了 TextPrimary，整行读起来像一句正文。
        assertTrue("库名没走原型那一档中灰 TextSecondary", code.contains("color = TextSecondary"))
        assertFalse("库名不许回退成正文黑 TextPrimary（原型 `.kb` 那档是中灰）", code.contains("color = TextPrimary"))
        assertEquals("行名与条目同走 labelMedium 那一档（原型 11px），恰两颗", 2,
            Regex("""AppTypography\.labelMedium""").findAll(code).count())
        assertFalse("旧那档 md 圆角不许留在这一格（`.kb-opt` 的 radius 落 sm）",
            code.contains("LoveBrainShape.md"))
        // 一颗可点出口：整格只有**行根**那一段接点开合，加上列表条目那颗，源码里 clickable 恰两颗。
        // 第二句钉的是"挂在哪一颗"：点击要是退回"名字+箭头"那一小块，正则就接不上——
        // 那一版真机上点整行的其它位置什么都不发生（用户报的"知识库无法更改了"就是这个形状）。
        assertEquals("clickable 恰两颗（行根那一段 + 列表条目）", 2,
            Regex("""\.clickable\(""").findAll(code).count())
        assertTrue(
            "展开触发没挂在行根上（整行必须点得着，见 SETTINGS_ROW_KB_TAG 那段说明）",
            Regex("""settingsEntryRow\(\)[\s\S]{0,200}?\.clickable\(""").containsMatchIn(code)
        )
    }
}
