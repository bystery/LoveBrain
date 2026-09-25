package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SemanticsProbe.Target
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 知识库编辑屏（`KbEditScreen`）——§6.5 :531/:532 在这一屏的第一次逐颗量。
 *
 * 旧账说这一屏"要 VM 和真实磁盘所以测不了"——**那是一条抄来的事实**（坑表 84 第五次复发）：
 * 它的签名是 `KbEditScreen(files, lastFile, onLastFileChange, readFile, saveFile, onBack)`，
 * **一个 ViewModel 都不收**，读盘走两个 suspend lambda ⇒ 交桩就行。
 *
 * 两个状态都要扫：**预览态**与**点过「编辑」之后的编辑态**——
 * 那颗 `Button(containerColor = Primary)` 的「保存」只在编辑态出现（本机实扫确认），
 * 只测首屏会当场漏掉它（坑表 97）。
 *
 * ⚠ 文件标签排在一个 `horizontalScroll` 里，被横排裁过的宽度会进语义树（坑表 85/90）：
 * 热区判据只判**整颗在视口内**的，排除项连同尺寸打进失败信息，并要求样本数下限——
 * 筛到只剩一两颗就等于这格没跑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KbEditScreenSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val matrix = UiMatrix(360, 1000)

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /**
     * 「保存」那颗**走资源**（本格把它搬进 `LbPrimaryButton` 时顺手还的债），
     * 锚点必须 `getString` 取——本机解析出英文，写死"保存"会 0 命中。
     * 「清空」「编辑」「预览」「放弃修改」与三个文件标签**仍是内联中文**（债还在）。
     */
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val saveLabel: String get() = ctx.getString(com.lovebrain.app.R.string.kb_save)
    private val scan by lazy { ScrollScan(rule, probe) }

    private val files = listOf(
        KbFile("我是谁", "understand/me.md", layer = "画像"),
        KbFile("她是谁", "understand/her.md", layer = "画像"),
        // ⚠ 这一屏按 `layers = listOf("画像", "当下", "积累")` 分组渲染：
        //   layer 写错的文件**根本不上屏**（第一版就报"没量到 最近两句"，其实是我造了个不存在的档位）。
        KbFile("最近两句", "moment/recent.md", layer = "积累")
    )

    private fun mount() {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                KbEditScreen(
                    files = files,
                    lastFile = "understand/me.md",
                    onLastFileChange = {},
                    readFile = { path -> "«$path» 的内容" to "sha-of-$path" },
                    saveFile = { _, _, _ -> "new-version" },
                    onBack = {}
                )
            }
        }
        rule.waitForIdle()
    }

    /** 预览态 + 编辑态两档都扫一遍，累计"每颗控件自己的尺寸"（跨档取最大面积） */
    private fun scanBothStates(): Map<String, Target> {
        mount()
        val all = LinkedHashMap<String, Target>()
        fun absorb(from: Map<String, Target>) {
            from.forEach { (k, t) ->
                val cur = all[k]
                if (cur == null || cur.widthDp * cur.heightDp < t.widthDp * t.heightDp) all[k] = t
            }
        }
        absorb(scan.toBottom("KbEditScreen 预览态"))
        val edit = all["编辑"] ?: error("预览态找不到「编辑」那颗，编辑态进不去：" + all.keys)
        assertEquals("那颗「编辑」得先报成按钮：" + edit.describe(), "Button", edit.role)
        rule.onAllNodes(hasClickAction() and hasText("编辑")).onFirst().performClick()
        rule.waitForIdle()
        absorb(scan.toBottom("KbEditScreen 编辑态"))
        assertTrue(
            "两档走完只累计到 ${all.size} 类节点，样本这么少说明挂载或切换没生效：" +
                all.values.joinToString { it.describe() },
            all.size >= 7
        )
        return all
    }

    /**
     * 横排裁切筛掉"露出视口右边"的那些，并**把排除项说清楚**。
     * 左边贴边的那些留下：页头那颗正常控件在 x=0 也在这儿，一刀切会把真缺陷筛掉。
     */
    private fun assertReachableMeetFloor(all: Map<String, Target>, context: String) {
        val (reachable, excluded) = all.values.partition { t ->
            t.widthDp > 0f && t.heightDp > 0f && t.leftDp >= 0f &&
                t.leftDp + t.widthDp < matrix.widthDp - 0.5f
        }
        assertTrue(
            "$context 视口内只量到 ${reachable.size} 颗（整表 ${all.size} 颗）——" +
                "筛到这么少，热区这格就退化成空转：" + excluded.joinToString { it.describe() },
            reachable.size >= 6
        )
        val offenders = reachable.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "知识库编辑屏$context 有 ${offenders.size}/${reachable.size} 个可交互节点小于 " +
                "${probe.floorDp.toInt()}dp：\n" + offenders.joinToString("\n") { "  " + it.describe() } +
                "\n  （另排除 ${excluded.size} 颗被横排/纵向滚动裁掉的：" +
                excluded.joinToString { it.describe() } + "）",
            offenders.isEmpty()
        )
    }

    @Test
    fun `the editor screen reports its tabs and both state actions`() {
        val all = scanBothStates()
        val absent = listOf("Back", "清空", "我是谁", "她是谁", "编辑", "预览", "放弃修改", saveLabel)
            .filter { !all.containsKey(it) }
        assertTrue("这一屏该量到这 8 类，没量到：" + absent + "；实际：" + all.keys.sorted(),
            absent.isEmpty())
    }

    @Test
    fun `every control on the editor screen meets the 48dp floor`() {
        assertReachableMeetFloor(scanBothStates(), "")
    }

    @Test
    fun `every non-editable control on the editor screen announces a role`() {
        val all = scanBothStates()
        val missing = all.values.filter { !it.editable && it.role == "无" }
        assertTrue(
            "编辑屏里有 ${missing.size} 类可交互节点没声明角色：\n" +
                missing.joinToString("\n") { "  " + it.describe() },
            missing.isEmpty()
        )
    }

    @Test
    fun `every control on the editor screen can be named`() {
        val all = scanBothStates()
        val unlabeled = all.values.filter { !it.labeled }
        assertTrue(
            "有 ${unlabeled.size} 类节点没名字：\n" + unlabeled.joinToString("\n") { "  " + it.describe() },
            unlabeled.isEmpty()
        )
    }

    /**
     * 文件标签排得让读屏听不出"现在开的是哪一份"——这一格钉住它。
     *
     * 选中态原来**只画在底色上**（`containerColor = if (isSel) PrimaryLight else SurfaceCard`），
     * 语义树里那两颗报的是 `role=Button selected=null`：眼睛看得见哪份是当前的，
     * 读屏看不见（§6.5 :532 那一栏；与点踩面板、方案卡同一族，量之前谁都不知道）。
     */
    @Test
    fun `the file tabs announce which one is open`() {
        val all = scanBothStates()
        val tabs = all.values.filter { it.label in files.map { f -> f.label } }
        assertEquals("三个文件标签都该在树上，实到：" + tabs.map { it.label }, 3, tabs.size)
        probe.assertSelectableAnnounceState(tabs, "知识库文件标签")
        tabs.forEach { t ->
            assertEquals(
                "这一排是互斥的「当前开的是哪一份」，得报成 Tab 而不是三颗各不相干的按钮：" +
                    t.describe(),
                "Tab", t.role
            )
        }
        val open = tabs.first { it.label == "我是谁" }
        assertEquals("当前那份必须报 selected=true：" + open.describe(), true, open.selected)
        val other = tabs.first { it.label == "她是谁" }
        assertEquals("没打开的那份要报 selected=false，不能不报：" + other.describe(),
            false, other.selected)
    }

    /**
     * 编辑态那颗「保存」：这一态的主动作，够大、报得出角色、能按。
     *
     * 搬进 `LbPrimaryButton` 之前它实量 **78x44dp**（Material `Button` 被
     * `KbEditDimens.ACTION_BUTTON_HEIGHT_DP` 钉在 44）——:596 那条"无小于 48dp 热区"
     * 在这一屏是**没过的**，而且这是全站唯一一颗高度由页面常量决定的主动作。
     */
    @Test
    fun `the save action in edit mode is a pressable button big enough`() {
        val all = scanBothStates()
        val saves = all.values.filter { it.label == saveLabel }
        assertEquals(
            "编辑态应当恰好一颗「保存」（本机解析成 $saveLabel）：" +
                all.values.joinToString { it.describe() },
            1, saves.size)
        val t = saves.single()
        assertEquals("主动作得报成按钮：" + t.describe(), "Button", t.role)
        assertTrue("热区不到 ${probe.floorDp.toInt()}dp：" + t.describe(), !t.tooSmall(probe.floorDp))
        assertTrue("编辑态里「保存」不该是灰的：" + t.describe(), !t.disabled)
    }
}
