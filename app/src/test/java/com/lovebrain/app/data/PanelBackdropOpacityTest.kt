package com.lovebrain.app.data

import com.lovebrain.app.AppConfig
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/**
 * 面板**背景层**不透明度这一项配置（[PanelBackdropOpacity]）的判据，全是纯 JVM。
 *
 * 这一族钉的是**六件**互相独立的事（与下面六段小标题一一对应，3b 算第 3 件的另一侧），
 * 缺一条就会长出对应的那一种坏实现：
 *
 * 1. **默认没被改动**：从没滑过滑杆的机器上，这一项换算出来就是完全不透明，
 *    面板画出来与"背景不透明度不可调"时逐字同形。放开"可调"最坏的失败方式不是调不动，
 *    而是所有人一上线就发现自己的面板变淡了。
 * 2. **有界且能回落**：量纲是整数百分比，区间外的脏值不能原样画出去，
 *    更不能因为读不出而变成 0（= 面板直接看不见）。
 * 3. **整数存盘这件事本身成立**：滑杆给用户看的每一个合法值都必须能
 *    `整数 → 浮点 alpha → 整数` 原样回来，否则"盘上是整数"这句只是注释。
 *    这一件的另一侧（下面 3b）是**屏上念的是显示轴、盘上存的仍是落盘轴**：两者是一对镜像，
 *    换算只许走那两个口。把落盘轴的数字直接标成"透明度"印出去，屏上"100% 透明"其实是完全不透明。
 * 4. **盘上只许有一颗透明度设置**：再落一颗 opacity 键就是第二个开关，而且两颗各画各的
 *    必然分家——悬浮球跟着这一颗、面板背景跟着那一颗，同一位用户的设置长出两个读数。
 * 5. **悬浮球、面板两层底色与消息区那两块大面积底共用同一颗尺**：面板侧静止就等于用户设的那一档
 *    （能一路到 20%），球那一侧多一颗可读下限（到 50% 为止）——这条不对称是有意的，
 *    球是全屏唯一手势入口，淡到点不中等于功能消失。闲置降遮挡不许再乘第二层，
 *    两次相乘会把图标压到可读下限之下；大面积底只许读面板根部供出来的那颗读数，不许自己再算一层。
 * 6. **它作用在背景层、不作用在整扇窗**：`ComposeView.alpha` 那一条通道归淡入淡出动画，
 *    窗口在三个地方把它复位回基准；用户设的值一旦被画在那条通道上，就会在第一次收起面板时消失。
 *    这一条是窗口文件里的形状判据，钉的是"复位只能复位动画那一条"。
 */
class PanelBackdropOpacityTest {

    /** 单测的工作目录可能是模块目录也可能是仓库根，两种都接住（与本仓其它源码级判据同一写法）。 */
    private fun mainSource(relative: String): File =
        File("src/main/java/com/lovebrain/app/$relative").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/$relative")

    // ═══════════ 1. 默认 = 今天的样子 ═══════════

    @Test
    fun `never touched means fully opaque so nobody's panel changes on its own`() {
        assertEquals("默认必须是 100%（实色），这一笔上线时滑杆没动过的用户不该看到任何变化",
            100, PanelBackdropOpacity.DEFAULT_PERCENT)
        assertEquals("老数据 / 从没写过这一项 ⇒ 换算出来正好是 1f，不多不少",
            1f, PanelBackdropOpacity.alphaOf(null))
        assertEquals("默认档换算回来的整数也得是 100，读盘→画→回写要能逐字往返",
            100, PanelBackdropOpacity.snapPercent(null))
    }

    // ═══════════ 2. 有界 + 回落 ═══════════

    @Test
    fun `the legal range is bounded and every junk value lands back inside it`() {
        assertEquals("区间下限就是屏上念到的最大透明度 80%（不透明度 20）",
            20, PanelBackdropOpacity.MIN_PERCENT)
        assertEquals("区间上限就是完全不透明", 100, PanelBackdropOpacity.MAX_PERCENT)
        val junk = listOf(-100, -1, 0, 1, 5, 19, 101, 999, Int.MAX_VALUE, Int.MIN_VALUE)
        val escaped = junk.map { it to PanelBackdropOpacity.snapPercent(it) }
            .filter { (_, snapped) -> snapped !in PanelBackdropOpacity.steps }
        assertTrue(
            "盘上可能出现的脏值，换算完必须仍落在合法刻度里（0 与负数绝不能变成\u201c整扇窗看不见\u201d）：$escaped",
            escaped.isEmpty()
        )
        assertEquals("越界向下钳到最淡那一档，而不是照抄 0（0 等于把面板洗没）",
            20, PanelBackdropOpacity.snapPercent(0))
        assertEquals("越界向上钳到完全不透明，999 不等于\u201c更实\u201d这一回事",
            100, PanelBackdropOpacity.snapPercent(999))
    }

    // ═══════════ 3. 整数量纲这件事本身成立 ═══════════

    @Test
    fun `the persisted dimension is an integer percent that round-trips through the float`() {
        val steps = PanelBackdropOpacity.steps
        assertEquals("刻度是 5% 一档、从下限 20 到 100 的整串（放开下限之后 17 档）",
            listOf(20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100), steps)
        // 选整数存盘的那条理由要能被证伪：滑杆上的每个合法值都必须活着经过一次浮点换算再回来
        val broken = steps.filter { (PanelBackdropOpacity.alphaOf(it) * 100).roundToInt() != it }
        assertTrue("整数 → alpha → 整数这一趟必须有损不到任何一个刻度，坏掉的：$broken", broken.isEmpty())
        // 就近对齐：非刻度值进不来，界面显示的数字与盘上的数字才会一模一样
        assertEquals("42 已在区间内、离 40 更近 ⇒ 对齐到 40，不照抄 42", 40, PanelBackdropOpacity.snapPercent(42))
        assertEquals("63 离 65 更近 ⇒ 对齐到 65", 65, PanelBackdropOpacity.snapPercent(63))
        assertEquals("10 在区间之外 ⇒ 先钳回最淡那一档，再谈对齐（绝不照抄出一个盘上存不下的数）",
            20, PanelBackdropOpacity.snapPercent(10))
        assertTrue("对齐必须幂等（写盘后再读回来不能再动一次）",
            steps.all { PanelBackdropOpacity.snapPercent(PanelBackdropOpacity.snapPercent(it)) == it })
    }

    @Test
    fun `the transparency mirror is exactly the opacity's complement both ways`() {
        // 界面上那一行标的是"透明度"，盘上存的是"不透明度"；这层镜像只允许写在这里
        assertEquals("完全不透明就是零透明", 0, PanelBackdropOpacity.transparencyOf(100))
        assertEquals("不透明度 60% ⇒ 透明 40%（放开之后这已经只是中段，不是最透那一头）",
            40, PanelBackdropOpacity.transparencyOf(60))
        val bad = PanelBackdropOpacity.steps.filter {
            PanelBackdropOpacity.fromTransparencyPercent(PanelBackdropOpacity.transparencyOf(it)) != it
        }
        assertTrue("来回必须逐字相等，否则滑杆会把用户设的值改口：" + bad, bad.isEmpty())
        assertEquals("界面上\u201c最透明\u201d那一头换算回来是区间下限，不是 0",
            20, PanelBackdropOpacity.fromTransparencyPercent(80))
    }

    @Test
    fun `the wire key is a stable non empty string`() {
        // 键名一旦上线就是盘上格式，改它等于把用户设过的值全丢掉，所以要钉住拼写
        assertEquals("panel_backdrop_opacity_percent", PanelBackdropOpacity.PREF_KEY)
    }

    // ═══════════ 3b. 显示那一侧的轴：屏上写的是透明度，落盘的仍是不透明度 ═══════════

    /**
     * 设置页那颗滑杆现在画的是**真透明度**，而这屏的量程就是这一格里数出来的三个读数。
     *
     * 为什么值得单独钉（而不是只看上面那格"镜像关系"）：镜像关系本身一直成立，
     * 出问题的是**控件用错了那一侧**——把"100 = 完全不透明"那个数直接标成"透明度"印在屏上，
     * 于是屏上"100% 透明度"其实是完全不透明。这一格钉的正是"屏上那三个数住在哪一把轴上"：
     * 0% = 完全不透明（盘上 100）、40% = 正中（盘上 60）、80% = 这一族允许的最透（盘上 20）。
     * 80% 这条上界不是口味：它就是"不透明度下限 20"的镜像，也就是用户真机回归点名的那一句
     * "最大透明度是 80%"。钉住它，等于钉住"滑杆推到底画出去的就是 0.20 而不是别的什么数"。
     *
     * 反例各挡一种：
     * · 显示轴直接印落盘轴 ⇒ 那三个读数变成 100 / 60 / 20，第一组断言当场红；
     * · 量程手写成 0..100 或 0..40（旧那把轴）⇒ 与换算口现算的两端不符，红在端点那两句；
     * · 反向换算写成裸减法而不过刻度 ⇒ 刻度外的值不再被对齐，红在最后那一句。
     */
    @Test
    fun `the readout the slider shows is the transparency scale from zero to eighty`() {
        assertEquals("完全不透明就是零透明", 0, PanelBackdropOpacity.transparencyOf(100))
        assertEquals("正中：不透明度 60 = 透明 40", 40, PanelBackdropOpacity.transparencyOf(60))
        assertEquals("这一族允许的最透：不透明度 20 = 透明 80", 80, PanelBackdropOpacity.transparencyOf(20))
        // 显示轴整串：把落盘那把尺镜像过去，仍是同一档数、同一 5% 步长，一颗不多一颗不少
        val displayTicks = PanelBackdropOpacity.steps.map { PanelBackdropOpacity.transparencyOf(it) }.reversed()
        assertEquals((0..80 step 5).toList(), displayTicks)
        assertEquals("显示轴的左端必须是完全不透明那一头",
            0, PanelBackdropOpacity.transparencyOf(PanelBackdropOpacity.MAX_PERCENT))
        assertEquals("显示轴的右端必须是这一族允许的最透那一头",
            80, PanelBackdropOpacity.transparencyOf(PanelBackdropOpacity.MIN_PERCENT))
        // 反向口：界面交来的是透明度时，落盘必须回到那一把尺的刻度上
        assertEquals(100, PanelBackdropOpacity.fromTransparencyPercent(0))
        assertEquals(60, PanelBackdropOpacity.fromTransparencyPercent(40))
        assertEquals(20, PanelBackdropOpacity.fromTransparencyPercent(80))
        assertEquals("界面交出刻度外的透明度（17）也要落回合法刻度，不能裸减法交出 83",
            85, PanelBackdropOpacity.fromTransparencyPercent(17))
        assertEquals("界面交出比这一族允许的最透还透的数（95）也只能落到区间下限，不是 5",
            20, PanelBackdropOpacity.fromTransparencyPercent(95))
    }

    /**
     * **持久化与画侧一个字没动**：换的是"屏上写哪个数"，不是"盘上存哪个数"。
     *
     * 三条读数合起来才是这一句：① 逐档往返（不透明度 → 透明度 → 落盘值）原样回来；
     * ② 绕显示轴一趟再取 `alphaOf` / `effectiveAlpha`，与直接按落盘轴取的是同一个数——
     * 也就是面板那三层作用点（两层底色 + 供出去的读数）与悬浮球画的还是同一颗尺；
     * ③ 界面万一交出越界的透明度，落盘值也出不去合法刻度（最透只到区间下限那一档的 alpha，
     *   不是"整扇窗看不见"）。
     */
    @Test
    fun `showing the mirror never moves the stored value or the painted alpha`() {
        val drift = PanelBackdropOpacity.steps.filter {
            PanelBackdropOpacity.fromTransparencyPercent(PanelBackdropOpacity.transparencyOf(it)) != it
        }
        assertTrue("来回必须逐字相等，否则滑杆会把用户设的值改口：$drift", drift.isEmpty())
        val painted = PanelBackdropOpacity.steps.filter {
            val viaDisplay = PanelBackdropOpacity.fromTransparencyPercent(PanelBackdropOpacity.transparencyOf(it))
            PanelBackdropOpacity.alphaOf(viaDisplay) != PanelBackdropOpacity.alphaOf(it) ||
                PanelBackdropOpacity.effectiveAlpha(viaDisplay, idleDimmed = false) !=
                    PanelBackdropOpacity.effectiveAlpha(it, idleDimmed = false)
        }
        assertTrue(
            "显示轴换算完画出来的 alpha 必须与直接按落盘轴画的同一个数，坏掉的档位：$painted",
            painted.isEmpty()
        )
        assertEquals("越界的显示值落回区间下限，而不是照抄出去",
            20, PanelBackdropOpacity.fromTransparencyPercent(95))
        assertEquals("显示轴最透那一头画出来就是区间下限的 alpha（0.20），不是看不见",
            0.20f, PanelBackdropOpacity.alphaOf(PanelBackdropOpacity.fromTransparencyPercent(80)), 0f)
        // 哨兵：刻度表空了上面全是空转
        assertEquals(17, PanelBackdropOpacity.steps.size)
    }

    /**
     * 那颗控件必须真的走这两个换算口，而不是把宿主交来的数直接印在屏上。
     *
     * 判的是形状（屏上那个数与滑杆那把轴的**来路**），不是像素：
     * · 屏上那一个百分比只许有一处出处，而且那一处必须是换算口算出来的；
     * · 滑杆 `valueRange` 的两端不许再直接拿不透明度那把轴的两个端点（旧形状就是这么写的）；
     * · 页面上不许出现手算的镜像减法；
     * · 宿主交来的那颗原始不透明度只许被**换算口**读一次（签名那一行除外）——
     *   旧形状正是把它直接交给滑杆与读数。
     * 反向证人：`assertEquals(1, readouts.size)` 让"把读数撤掉"与"另画第二份读数"都红，
     * 找不到出处那一行时 `assertTrue` 直接红——这把尺扫空集不会恒绿。
     */
    @Test
    fun `the opacity control converts through the mirror instead of printing the stored percent`() {
        val file = mainSource("ui/panel/settings/SettingsOpacityEntry.kt")
        assertTrue("$file 读不到——这一格会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        assertTrue("显示那颗必须走 transparencyOf（不走去哪儿换算？）",
            code.contains("PanelBackdropOpacity.transparencyOf("))
        assertTrue("交回宿主的必须走 fromTransparencyPercent，落盘那把轴才不变",
            code.contains("PanelBackdropOpacity.fromTransparencyPercent("))
        assertFalse("页面上不许再手算一遍镜像减法", Regex("""100\s*-\s*""").containsMatchIn(code))
        assertFalse("滑杆的两端不许再直接拿不透明度那把轴的端点（旧形状）",
            Regex("""valueRange\s*=[^\n]*(MIN_PERCENT|MAX_PERCENT)""").containsMatchIn(code))
        // 宿主交来的那颗不透明度只许被换算口读一次：直接画出去 = 把落盘轴当成显示轴印在屏上。
        // （签名那一行 `opacityPercent: Int` 是声明不是读取，按那半句排除。）
        val rawReads = code.lines()
            .filter { it.contains("opacityPercent") }
            .filterNot { it.contains("transparencyOf") || it.contains("opacityPercent: Int") }
        assertTrue(
            "读原始不透明度的地方除签名外只许是换算口那一句，实到：$rawReads",
            rawReads.isEmpty()
        )
        val readouts = Regex(
            """stringResource\(\s*R\.string\.settings_opacity_percent\s*,\s*([A-Za-z_][A-Za-z0-9_]*)"""
        ).findAll(code).map { it.groupValues[1] }.toList()
        assertEquals("屏上那一个百分比只许有一处出处，实到 $readouts", 1, readouts.size)
        val origin = code.lines().firstOrNull { it.contains("val ${readouts.single()} ") }
        assertTrue(
            "读数「${readouts.single()}」必须是 transparencyOf 换算出来的那颗——把落盘轴直接印在屏上" +
                "就是这一版修掉的那个反了。实到：$origin",
            origin != null && origin.contains("transparencyOf")
        )
    }

    // ═══════════ 4. 只有一颗透明度控制：新增的必须是同一个百分比的另一侧，不是第二个开关 ═══════════

    @Test
    fun `there is exactly one opacity preference in the whole source tree`() {
        // 产品口径是"沿用原来那颗透明度滑杆，不新增第二个透明度开关或滑杆"。
        // 这一格钉的是"盘上只有一个 opacity 键"：新加一颗就是第二个开关，
        // 而且两颗各画各的必然分家（球跟着 A、面板跟着 B）。
        val code = SourceScan.maskComments(mainSource("AppConfig.kt").readText(Charsets.UTF_8))
        val opacityKeys = Regex("""PREF_KEY\s*(?::\s*String\s*)?=\s*"([^"]*opacity[^"]*)"""").findAll(code)
            .map { it.groupValues[1] }.toList()
        assertEquals("透明度相关的落盘键必须只有一颗，实到：$opacityKeys",
            listOf("panel_backdrop_opacity_percent"), opacityKeys)
    }

    // ═══════════ 5. 有效 alpha：面板底 / 大面积底 / 悬浮球共用的一把尺（球那侧多一颗可读下限）═══════════

    @Test
    fun `the panel backdrop paints exactly the tier the user set all the way down to twenty percent`() {
        // 面板那一侧（不带可读下限的一侧）静止态**严格等于用户设置**，一路到区间下限：
        // 滑杆推到最透那一格画出去就得是 0.20，不能被谁悄悄托回 0.50——
        // 那等于把屏上念的"透明度 80%"变成实际的 50%，而这一笔用户要的正是那 80%。
        assertEquals("最透那一档画出去就是 0.20，不是可读下限 0.50",
            0.20f, PanelBackdropOpacity.effectiveAlpha(20, idleDimmed = false), 0f)
        assertEquals(0.60f, PanelBackdropOpacity.effectiveAlpha(60, idleDimmed = false), 0f)
        assertEquals("静止值就是既有那颗 alphaOf 的读数，画侧从此只有一把尺",
            PanelBackdropOpacity.alphaOf(60), PanelBackdropOpacity.effectiveAlpha(60, idleDimmed = false), 0f)
        // 默认档（从没滑过滑杆的人）：静止 = 完全不透明 = 与从前逐字同形
        assertEquals(1f, PanelBackdropOpacity.effectiveAlpha(null, idleDimmed = false), 0f)
    }

    @Test
    fun `the bubble stops at the readability floor while the panel keeps going`() {
        val floor = PanelBackdropOpacity.IDLE_ALPHA_FLOOR
        // 球带可读下限：低档位上球**比用户设的更不透明**，这一条不对称是有意的
        //（球是全屏唯一手势入口，淡到找不着就点不中；面板淡下去用户看得见也滑得回来）。
        assertEquals("20% 档的球坐在下限上，不是 0.20（摘掉那颗下限这一句就红）",
            floor, PanelBackdropOpacity.effectiveAlpha(20, idleDimmed = false, readabilityFloor = true), 0f)
        assertEquals("45% 档同理：静止值也被托住（摘掉下限交出 0.45，红）",
            floor, PanelBackdropOpacity.effectiveAlpha(45, idleDimmed = false, readabilityFloor = true), 0f)
        // 下限不许反过来把高档位也压住：档位本身比下限浓时，静止值还是用户那一格
        assertEquals(0.60f,
            PanelBackdropOpacity.effectiveAlpha(60, idleDimmed = false, readabilityFloor = true), 0f)
        assertEquals(1f,
            PanelBackdropOpacity.effectiveAlpha(100, idleDimmed = false, readabilityFloor = true), 0f)
        // 默认值必须是"不带下限"：面板那三层作用点全靠这一条才透得到 20%
        assertTrue("不传 readabilityFloor 时必须能画到区间下限（默认值被人翻过来这一句就红）",
            PanelBackdropOpacity.effectiveAlpha(PanelBackdropOpacity.MIN_PERCENT, idleDimmed = false) < floor)
    }

    @Test
    fun `idle is dimmer than the setting but never a second multiplication of it`() {
        val rest = PanelBackdropOpacity.effectiveAlpha(60, idleDimmed = false)
        val idle = PanelBackdropOpacity.effectiveAlpha(60, idleDimmed = true)
        assertTrue("60% 档闲置必须更低才有降遮挡（实到 $idle）", idle < rest)
        // 两次相乘（0.60 × 0.78 = 0.468）正是要防的那件事：比可读下限还淡，图标淡到找不着
        val product = 0.60f * AppConfig.BUBBLE_IDLE_ALPHA
        assertTrue("闲置读数不许就是那两次相乘的积（$product）：实到 $idle",
            kotlin.math.abs(idle - product) > 0.02f)
        assertTrue("闲置不得低于可读下限：实到 $idle", idle >= PanelBackdropOpacity.IDLE_ALPHA_FLOOR)
        // 默认档：闲置读数与从前那颗 BUBBLE_IDLE_ALPHA 逐字相同（观感回归的替身）
        assertEquals(AppConfig.BUBBLE_IDLE_ALPHA,
            PanelBackdropOpacity.effectiveAlpha(100, idleDimmed = true), 0.0001f)
        // 球那一侧在低档位上没有余地可降：闲置与静止同浓，绝不倒挂成"闲置比静止更不透明"
        val lowRest = PanelBackdropOpacity.effectiveAlpha(20, idleDimmed = false, readabilityFloor = true)
        val lowIdle = PanelBackdropOpacity.effectiveAlpha(20, idleDimmed = true, readabilityFloor = true)
        assertTrue("低档位的闲置不许比静止更不透明（实到 idle=$lowIdle rest=$lowRest）", lowIdle <= lowRest)
    }

    @Test
    fun `every tier keeps its rest value and idle only ever goes down`() {
        // 面板侧（不带下限）：① 静止 = 用户设置 ② 闲置不越过静止 ③ 带下限的一侧静止就在下限上
        val bad = PanelBackdropOpacity.steps.filter { percent ->
            val atRest = PanelBackdropOpacity.effectiveAlpha(percent, idleDimmed = false)
            val idle = PanelBackdropOpacity.effectiveAlpha(percent, idleDimmed = true)
            atRest != PanelBackdropOpacity.alphaOf(percent) || idle > atRest
        }
        assertTrue("逐档量（面板侧）：$bad 违反了静止等于设置/闲置不越过静止中的某一条", bad.isEmpty())
        val bubbleBad = PanelBackdropOpacity.steps.filter { percent ->
            val atRest = PanelBackdropOpacity.effectiveAlpha(percent, idleDimmed = false, readabilityFloor = true)
            val idle = PanelBackdropOpacity.effectiveAlpha(percent, idleDimmed = true, readabilityFloor = true)
            val floor = PanelBackdropOpacity.IDLE_ALPHA_FLOOR
            // 球侧三条：静止 = max(档位, 下限)；闲置 ≤ 静止；两态都不低于下限
            atRest != maxOf(PanelBackdropOpacity.alphaOf(percent), floor) ||
                idle > atRest ||
                idle < floor ||
                atRest < floor
        }
        assertTrue("逐档量（球侧）：$bubbleBad 违反了静止/闲置/可读下限中的某一条", bubbleBad.isEmpty())
        // 55 是最低那一格"还有余地可降"的档（20..50 已经坐在下限上）：这一句防的是
        // "闲置那一层整个被摘掉"——摘掉之后 idle 与 rest 在 55 档相等，当场红。
        assertTrue("55% 档的球闲置必须真的更低（实到 " +
                "${PanelBackdropOpacity.effectiveAlpha(55, idleDimmed = true, readabilityFloor = true)}）",
            PanelBackdropOpacity.effectiveAlpha(55, idleDimmed = true, readabilityFloor = true) <
                PanelBackdropOpacity.effectiveAlpha(55, idleDimmed = false, readabilityFloor = true))
        // 进出对称：闲置结束画回来的必须还是这一侧的静止值
        val drift = PanelBackdropOpacity.steps.filter {
            PanelBackdropOpacity.effectiveAlpha(it, idleDimmed = false) !=
                PanelBackdropOpacity.effectiveAlpha(PanelBackdropOpacity.snapPercent(it), idleDimmed = false)
        }
        assertTrue("闲置结束后画回来的必须还是用户那一位：$drift", drift.isEmpty())
        // 单调：用户调浓，静止与闲置两侧都不许变淡（带下限与不带下限各量一遍）
        val pairs = PanelBackdropOpacity.steps.zipWithNext()
        val flipped = pairs.filter { (lo, hi) ->
            PanelBackdropOpacity.effectiveAlpha(lo, true) > PanelBackdropOpacity.effectiveAlpha(hi, true) ||
                PanelBackdropOpacity.effectiveAlpha(lo, false) > PanelBackdropOpacity.effectiveAlpha(hi, false) ||
                PanelBackdropOpacity.effectiveAlpha(lo, true, readabilityFloor = true) >
                    PanelBackdropOpacity.effectiveAlpha(hi, true, readabilityFloor = true) ||
                PanelBackdropOpacity.effectiveAlpha(lo, false, readabilityFloor = true) >
                    PanelBackdropOpacity.effectiveAlpha(hi, false, readabilityFloor = true)
        }
        assertTrue("档位调浓不许反而更淡，坏样本：$flipped", flipped.isEmpty())
        // 哨兵：刻度表空了上面全是假绿
        assertEquals(17, PanelBackdropOpacity.steps.size)
    }

    @Test
    fun `the bubble reads the one effective alpha instead of multiplying its own dim layer`() {
        val code = SourceScan.maskComments(mainSource("ui/bubble/FloatingBubble.kt").readText(Charsets.UTF_8))
        assertTrue("球侧必须从 effectiveAlpha 这一颗取值（与面板同一把尺）",
            code.contains("PanelBackdropOpacity.effectiveAlpha("))
        assertTrue("球侧必须带上可读下限这一颗（区间放开到 20% 之后，不带它的球就点不中了）",
            code.contains("readabilityFloor = true"))
        assertFalse("球侧不许再自己乘 BUBBLE_IDLE_ALPHA：那是第二次相乘",
            code.contains("BUBBLE_IDLE_ALPHA"))
        assertFalse("球侧不许再用 alphaOf 另算一层", code.contains("alphaOf("))
        assertTrue("alpha 只由入场动画 × 有效 alpha 两项组成",
            code.contains("alpha = enterAnim.value * dimAlpha"))
        // 面板那三层作用点也必须走同一颗，而且**一次都不带**那颗下限
        val panel = SourceScan.maskComments(mainSource("ui/panel/LoveBrainPanelScreen.kt").readText(Charsets.UTF_8))
        assertEquals("面板背景的三个作用点（两层底色 + 供出去的读数）都从 effectiveAlpha 取值",
            3, Regex("PanelBackdropOpacity\\.effectiveAlpha\\(").findAll(panel).count())
        assertFalse("面板一侧不许带可读下限：带了它最大透明度就成了 50%，而屏上念的还是 80%",
            panel.contains("readabilityFloor"))
    }

    /**
     * 消息区那两块大面积底：**只读**面板根部供出来的那颗浓度读数。
     *
     * 这一格挡的是这一族最容易长出来的两种坏形状：
     * · 大面积底留着自己那颗满不透明的底 ⇒ 滑杆推到底屏幕中间仍是一整块实底（就是用户报的那件事）；
     * · 大面积底自己再拿百分比算一层、或再乘一次 ⇒ 同一屏长出第二本浓度账，
     *   面板与卡片会在同一档读数上画出两种浓淡。
     * 缺省值那条断言挡第三种：把缺省改成任何一个小于 1 的数，脱离面板根的挂载
     * （全部既有 JVM/Robolectric 夹具）集体变脸，按像素量底色的那台仪器红在量具上。
     */
    @Test
    fun `the message area reads the density the panel root provides and never recomputes it`() {
        val reply = SourceScan.maskComments(mainSource("ui/panel/reply/MessageList.kt").readText(Charsets.UTF_8))
        assertTrue("那颗读数的缺省必须是 1f（满不透明）——脱离面板根的挂载要继续画成今天的样子",
            Regex("LocalPanelBackdropAlpha\\s*=\\s*compositionLocalOf\\s*\\{\\s*1f\\s*}")
                .containsMatchIn(reply))
        assertEquals("消息区那两块大面积底都按那颗读数取 alpha，一处不许留实底、一处不许多",
            2, Regex("background\\(\\s*SurfaceInset\\.copy\\(alpha = backdropAlpha\\)").findAll(reply).count())
        assertEquals("这一族里读那颗粒子的地方只许有一处（别的大面积底与小件不动）",
            1, reply.lines().count { it.contains("LocalPanelBackdropAlpha.current") })
        assertFalse("消息区不许自己调有效 alpha 那颗尺（根部算完才是它读的那一颗）",
            reply.contains("effectiveAlpha("))
        assertFalse("消息区不许另走 alphaOf 换算", reply.contains("alphaOf("))
        assertFalse("消息区不许把浓度再乘一层",
            Regex("copy\\(alpha = [^)]*\\*").containsMatchIn(reply))
        // 根部必须真的供出去，而且供的是那一颗尺的读数，不是百分比
        val panel = SourceScan.maskComments(mainSource("ui/panel/LoveBrainPanelScreen.kt").readText(Charsets.UTF_8))
        assertTrue("面板根部必须 provide 这一颗",
            panel.contains("LocalPanelBackdropAlpha provides"))
        assertTrue("provide 的必须是 effectiveAlpha 的成品读数",
            panel.contains("val backdropAlpha = PanelBackdropOpacity.effectiveAlpha("))
    }

    // ═══════════ 6. 窗口那一侧的形状：复位只复位动画通道 ═══════════

    @Test
    fun `the panel window resets only its fade channel and never borrows it for the backdrop`() {
        val file = mainSource("service/OverlayPanelWindow.kt")
        assertTrue("$file 读不到——这一格会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        assertFalse(
            "`ComposeView.alpha` 上不许再出现裸写的复位字面量，三处必须走同一个基准，",
            code.contains("cv.alpha = 1f")
        )
        assertEquals("淡出结束 / 再次打开 / 立即隐藏三处复位都走同一个出口",
            3, code.lines().count { it.trim() == "restoreFadeBaseline(cv)" })
        assertTrue("淡出动画必须从那枚命名基准起淡，而不是自己再写一个 1f",
            code.contains("ValueAnimator.ofFloat(WINDOW_SHOWN_ALPHA, 0f)"))
        assertFalse(
            "用户设的背景透明度不许被画在整扇窗的 alpha 上（那会连正文一起洗淡，也会被复位抹掉）",
            code.contains("PanelBackdropOpacity")
        )
    }
}
