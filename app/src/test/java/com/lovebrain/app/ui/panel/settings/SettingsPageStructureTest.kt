package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.BorderLight
import com.lovebrain.app.core.designsystem.LbSettingRow
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 整窗设置页的两件事：透明度那一颗换算（挂在唯一的换算口 [PanelBackdropOpacity.alphaOf] 上），
 * 以及"这一页不许长成别的东西"。
 *
 * 为什么把几条放在同一格 JVM 测试里（结构格不要设备；对齐那一格走 Robolectric 语义树）：
 * · 浓度换算是一条**纯判据**——区间内线性、越界夹紧、非刻度就近对齐；最淡那一档停在合同
 *   划的那条线上（不是"背景完全退掉"，理由写在那一格的 KDoc 里）。
 *   它是"先处理背景层、正文保持可读"这句话唯一能被机器判的那一半，
 *   另一半（真的作用在哪一层）在服务侧，本机量不到。
 * · 「整页只剩三张外框」「行不再自带卡壳」「标签左边缘与选择器右边缘对齐」这三条钉的是
 *   **版面归属**：外框只有一个主人（页面，每组一张），横向内缩也只有一个主人（组外框），
 *   行与 Entry 只渲染组内的内容。这类"别再长成六张卡 / 别再卡套卡"的否定式合同，
 *   读结构比读一次语义树可靠：语义树只能证明"现在没画"，证明不了"哪天有人又给一行加壳"。
 *   对齐那一条反过来必须**量树**：它是数值判据（左边缘、右边缘各自落在同一条线上），
 *   源码里搜不出"对齐"两个字。
 * · 2026-10-12 这一版把**原型那一格的脸**也钉成结构判据（`_…设置页紧凑交互原型.html` 的 CSS 是判据来源，
 *   换算表在对照原型换算表）：组名那一行读 `labelSmall`+`SemiBold`+`TextSecondary`+上下 `Spacing.md`
 *   （`.section-title` 10.5px/650/`--muted`/10·5·7）、组内分隔线读 `BorderLight`
 *   （`.item + .item` 的 `#e6e8ed`，比框那颗 `Border` 浅一档）、滚动柱首尾读顶 `Spacing.sm` 底 `Spacing.xl`
 *   （`.content{padding:5px 10px 17px}`）、组名必须落在三张框的**子树之外**、分隔线必须落在**之内**、
 *   页头以下不许再出现任何 `Spacer`。这几条每一条都配一则**反例证人**：拿真实源码改一处坏形状，
 *   让同一把尺自己报违例——10-11 那一版"结构对了但没照原型画脸"就是只钉结构、没钉字阶与色造成的。
 * · 页头那一格维持 30（用户10-11 那一版明确要"与本体 `PanelHeader` 同线"，**原型的 40 不采纳**）：
 *   这里按源码钉那颗常量与它的 `height` 落点，并拦住回退成 `LbTopBarLevel`；
 *   「收起不等于停止」那一族在源码侧的凭据（页只画一颗「收起」、不碰服务侧任何一颗口、不存第二本账）
 *   也钉在这一格，语义树那一半归 `SettingsPageSemanticsTest`。
 * · 「这一页不是弹窗/半屏 Sheet、不跳外部 Activity、不新造偏好模块」这类否定式合同保持原样；
 *   介绍浮层已随"开→输入→保存"的简化从这一页整体退场，所以那一族的登记豁免一起删了——
 *   设置目录里再出现 `LbModalSheet` 就应当是这一格先红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPageStructureTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density

    private val settingsDir: File
        get() = File("src/main/java/com/lovebrain/app/ui/panel/settings")
            .takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/settings")

    private val sources: List<Pair<String, String>> by lazy {
        assertTrue("找不到 $settingsDir——这一格会扫了个空集恒绿", settingsDir.isDirectory)
        settingsDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.name to SourceScan.maskComments(it.readText(Charsets.UTF_8)) }
            .toList()
            .also { assertTrue("settings/ 下没有 .kt：路径接错了", it.isNotEmpty()) }
    }

    /** 按文件名取"剥掉注释之后"的那一份源码（这一族的尺都先 mask，免得数到 KDoc 里的举例） */
    private fun maskedOf(name: String): String =
        sources.firstOrNull { it.first == name }?.second
            ?: error("$name 不在 settings/ 的扫描范围内——这一格会恒绿：${sources.map { it.first }}")

    /** 掩码源码里某根针的出现次数（计数而不是"有没有"，把"多画一张框"也判红） */
    private fun String.countOf(needle: String): Int = Regex(Regex.escape(needle)).findAll(this).count()

    // ─── 版面归属的三把尺共用的读法（都作用在**掩码后**的源码上，KDoc 里的举例不算数）───────

    /**
     * 某颗调用点的下标清单（词边界认，且把 `fun Name(` 那一行自己的声明剔出去）。
     *
     * 为什么不是裸 `indexOf`/`countOf`：这几把尺判的是**位置**（在框里还是框外、在滚动柱以上还是以下），
     * 光数次数会把"多一颗"和"长错地方"混成同一种红，也会让声明行自己混进调用清单。
     */
    private fun callOffsets(code: String, name: String): List<Int> =
        Regex("""(?<![\w.])""" + Regex.escape(name) + """\s*\(""")
            .findAll(code)
            .map { it.range.first }
            .filter { open -> !code.substring(0, open).trimEnd().endsWith("fun") }
            .toList()

    /** 三张组外框各自的源码跨度：左括号下标 → 含尾随 lambda 的子树终点（配对由 [SourceScan.callEnd] 算） */
    private fun groupFrameSpans(code: String): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var from = 0
        while (true) {
            val at = code.indexOf("settingsEntryGroupCard()", from)
            if (at < 0) break
            val head = code.lastIndexOf("Column(", at)
            assertTrue(
                "$PAGE_FILE 里那颗组外框不再包在一棵 Column 里（外框的主人换了形状，这几把尺要重写）",
                head >= 0
            )
            val open = head + "Column".length
            out += open to SourceScan.callEnd(code, open)
            from = at + 1
        }
        return out
    }

    private fun inside(span: Pair<Int, Int>, at: Int): Boolean = at > span.first && at < span.second

    /** ② 组名画在框外：返回被塞进任何一张框的子树里的那些组名调用点 */
    private fun titlesInsideFrames(code: String): List<Int> {
        val spans = groupFrameSpans(code)
        return callOffsets(code, "SettingsGroupTitle").filter { at -> spans.any { inside(it, at) } }
    }

    /** ③ 分隔线只许出现在组内：返回落在所有框子树**之外**的那些调用点 */
    private fun dividersOutsideFrames(code: String): List<Int> {
        val spans = groupFrameSpans(code)
        return callOffsets(code, "SettingsRowDivider").filter { at -> spans.none { inside(it, at) } }
    }

    /** ④ 滚动柱以下不许再出现任何 `Spacer`（组与组之间的呼吸只由组名那一行给） */
    private fun spacersBelowTheScrollColumn(code: String): List<Int> {
        val scroll = code.indexOf(".verticalScroll(")
        assertTrue("$PAGE_FILE 读不到 .verticalScroll( ——正文不在竖列里滚了，这把尺要重写", scroll >= 0)
        return callOffsets(code, "Spacer").filter { it > scroll }
    }

    /**
     * 「组名那一格有没有照原型画脸」的判据本体：只读 `SettingsGroupTitle` 那颗 helper 的函数体。
     * 返回违例清单，空 = 合档。数值指回原型 `.section-title{font-size:10.5px;font-weight:650;
     * color:var(--muted);padding:10px 5px 7px}` 与对照原型换算表那一行的落点。
     */
    private fun groupTitleFaceViolations(body: String): List<String> {
        val out = mutableListOf<String>()
        if ("AppTypography.labelSmall" !in body) {
            out += "字阶不是 labelSmall（原型 10.5px 唯一同数量级、又低于行标签 labelLarge 的那一档）"
        }
        listOf("labelMedium", "labelLarge", "titleMedium", "bodyMedium").forEach { stolen ->
            if ("AppTypography.$stolen" in body) {
                out += "组名借了行标签/正文那一档 $stolen（组名与行内容必须差一档才读得出主次）"
            }
        }
        if ("FontWeight.SemiBold" !in body) {
            out += "字重不是 SemiBold（原型 650 落在现成 600/700 之间，取矮的一档，不新造字重）"
        }
        if ("color = TextSecondary" !in body) out += "墨色不是 TextSecondary（原型 --muted，换算表同值那颗）"
        if ("padding(top = Spacing.md, bottom = Spacing.md, start = Spacing.sm)" !in body) {
            out += "留白不是原型那三档（上下各 Spacing.md、往里让 Spacing.sm；原型 10/7 各差 2 与 1 收到同一档，" +
                "5px 那档横向内缩取最近的 Spacing.sm，整行厚度 8+14+8=30 与原型同数）"
        }
        if ("horizontal" in body) {
            out += "组名自己又缩了一层横向（横内缩的主人只有宿主外框与组外框，组名跟着框的左缘走）"
        }
        if (Regex("""\.padding\(""").findAll(body).count() > 1) {
            out += "组名那一行多套了一层 padding（组与组之间只由这一行的上下留白给呼吸，一次就够）"
        }
        Regex("""\d+\s*\.(dp|sp)""").findAll(body).forEach {
            out += "体里出现自写的数「${it.value}」——这一版不许新造 dp/sp 字面量，只许指回 Spacing 那一族"
        }
        return out
    }

    /** 取某颗顶层函数「从声明名到与之配对的那颗 `}`」的掩码源码片段 */
    private fun maskedBody(code: String, funName: String): String {
        val at = code.indexOf("fun $funName(")
        assertTrue("$PAGE_FILE 里读不到 $funName——这一格会恒绿", at >= 0)
        val braceAt = code.indexOf('{', at)
        var depth = 0
        var i = braceAt
        while (i < code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return code.substring(at, i + 1)
                }
            }
            i++
        }
        error("$funName 的函数体没闭合")
    }

    /**
     * ③ 的另一半 + ⑤ 的页头：这两把尺读的是"某一行里写的是哪颗 token"，
     * 违例返回清单（空 = 合档）。
     */
    private fun dividerInkViolations(body: String): List<String> {
        val out = mutableListOf<String>()
        if ("color = BorderLight" !in body) {
            out += "组内分隔线不是 BorderLight（原型 #e6e8ed ≈ hsl(250 5% 88%)，比框那颗 Border 浅一档）"
        }
        if (Regex("""color\s*=\s*Border\b""").containsMatchIn(body)) {
            out += "组内分隔线又回成框那颗 Border（框与框内分界同色，每一行都像被框再切一刀）"
        }
        if ("AppDimens.BORDER_WIDTH_DP" !in body) out += "分隔线宽度不再指回具名档（体里只许交 token，不许写数）"
        Regex("""\d+\s*\.(dp|sp)""").findAll(body).forEach { out += "体里出现自写的数「${it.value}」" }
        return out
    }

    /**
     * ⑤ 页头那一格：常量必须是 30 那一档、`height` 落点只有一颗、不许回退去拿 `LbTopBarLevel`。
     * 原型 `.hd{height:40px}` **有意不采纳**（用户10-11 那一版明确要"与本体 `PanelHeader` 同线"），
     * 所以这一把尺判的是"别偷偷抬回 40/48"，不是"照 40 画"。
     */
    private fun headerTierViolations(code: String): List<String> {
        val out = mutableListOf<String>()
        if (!Regex("""ROW_HEIGHT_DP\s*=\s*30\b""").containsMatchIn(code)) {
            out += "页头那一档不再是 30（原型那 40 不采纳，理由写在 SettingsHeaderDimens 的 KDoc 里）"
        }
        val heightAt = code.countOf(".height(SettingsHeaderDimens.ROW_HEIGHT_DP.dp)")
        if (heightAt != 1) out += "页头的行高落点应当恰有一颗 .height(SettingsHeaderDimens.ROW_HEIGHT_DP.dp)，实到 $heightAt"
        if ("LbTopBarLevel" in code) out += "这一页又去借 LbTopBarLevel 那一档（F1/F2 全屏页的 48 厚顶栏，正是这一版要拆的旧形制）"
        return out
    }

    /**
     * ⑤ 的另一半「收起不等于停止」在**源码侧**的凭据：这一页只画一颗「收起」、只投宿主那一次点击，
     * 服务侧那几颗口一颗都不许碰，也不自己记第二本账。语义树那一半（点一次只投一次）归
     * `SettingsPageSemanticsTest`，这里拦的是"页面长出自己的停止口/自己的状态"那种回退。
     *
     * 2026-10-12 起「收起」的字形与名字搬进了面板一族**共用**的那颗
     * （`ui/panel/PanelHeader.kt#PanelCollapseAction`，用户点名"复用 复用 复用"——
     * 以前这一页走 `LbTextAction` 自己又写了一颗 16dp 档，与本体 20dp 那颗长得不一样，
     * 透明开高时右上角两颗叠影）。所以名字（`R.string.panel_collapse`）在这页必须是**零颗**
     * ——它在共用控件那一处；这一页对共用控件的引用必须**恰一颗**——再手写第二颗就是回退。
     */
    private fun collapseIsNotStopViolations(code: String): List<String> {
        val out = mutableListOf<String>()
        val named = code.countOf("R.string.panel_collapse")
        if (named != 0) out += "「收起」的名字已归共用控件（PanelHeader.kt#PanelCollapseAction），这一页不许再抄第二份，实到 $named"
        val shared = code.countOf("PanelCollapseAction(")
        if (shared != 1) out += "这一页的收起必须是共用那颗 PanelCollapseAction 且恰一颗，实到 $shared"
        listOf(
            "stopSelf", "stopForeground", "removeView", "addView", "WindowManager", "WindowState",
            "FloatingService", "tempHide", "TEMP_HIDE", "startActivity"
        ).forEach { needle ->
            if (needle in code) out += "这一页自己碰了「$needle」——收起只是收起到球，停服务与摘窗归服务侧那一颗入口"
        }
        if (Regex("""\bremember\b""").containsMatchIn(code)) {
            out += "这一页自己 remember 了一份状态（收起/开关的第二本账），状态所有者只能是宿主"
        }
        if (Regex("""\bmutableStateOf\b""").containsMatchIn(code)) {
            out += "这一页自己存了一个可变状态（同上：无状态那一半的合同）"
        }
        return out
    }

    /**
     * ④ 的另一半「滚动柱首尾」：原型 `.content{padding:5px 10px 17px}` → 顶 [Spacing] 的 `sm`(4)、
     * 底 `xl`(16)，两个数都指回现成档位，横向那 10px 归宿主外框一个主人（本页不重复缩）。
     * 读的是**滚动柱自己那条 modifier 链**那一段（从 `.verticalScroll(` 到第一颗组名之间），
     * 不扫全页——别处合法的 padding 不归这一把尺管。
     */
    private fun scrollInsetViolations(code: String): List<String> {
        val out = mutableListOf<String>()
        val scroll = code.indexOf(".verticalScroll(")
        if (scroll < 0) return listOf("读不到 .verticalScroll( ——正文不在竖列里滚了，这把尺要重写")
        val firstTitle = callOffsets(code, "SettingsGroupTitle").firstOrNull { it > scroll }
        if (firstTitle == null) return listOf("滚动柱之后读不到组名——这一页的正文形状换了，这把尺要重写")
        val chain = code.substring(scroll, firstTitle)
        if (chain.countOf(".padding(top = Spacing.sm, bottom = Spacing.xl)") != 1) {
            out += "滚动柱首尾不是顶 Spacing.sm / 底 Spacing.xl（原型 .content 那 5px 与 17px 的取档）"
        }
        Regex("""\d+\s*\.(dp|sp)""").findAll(chain).forEach {
            out += "首尾那一段里出现自写的数「${it.value}」——只许指回 Spacing 那一族"
        }
        if (".padding(horizontal" in chain || "padding(start" in chain) {
            out += "滚动柱自己又缩了一层横向（横向内缩的主人只有宿主外框与组外框）"
        }
        return out
    }

    /**
     * 浓度换算这条纯判据：区间内线性、越界夹紧、非刻度就近对齐。
     *
     * ⚠ **主体重新挂在哪**：这一格测的是**唯一那一个换算口** [PanelBackdropOpacity.alphaOf]——
     * 也就是面板真的画出来那两处读的那一颗：面板外框与浮层面板各一层的
     * `SurfaceBase.copy(alpha = PanelBackdropOpacity.effectiveAlpha(...))`
     * （`ui/panel/LoveBrainPanelScreen.kt` 里那两层，按这一句找、不按行号找）。
     * **测的仍是同一件事**："界面上交出的每一个数与画出来的 alpha 必须同一来源"。
     * 盘上那个整数是**不透明度**（100 = 最不透明），界面显示的是**透明度读数**——
     * 两者的双向换算走 `PanelBackdropOpacity.transparencyOf()` / `fromTransparencyPercent()`
     * 那一对镜像口；本格钉的是落盘与渲染那一头：**换算口的端点与刻度一个字不许动**。
     *
     * ⚠ **区间下限为什么是 0.2，而不是 0，也不再是旧的 0.6**：这一档被划过两次，方向相反，
     * 两次都是真机报回来的——
     * · 曾经有一版滑到底把整窗洗到看不见（下限 40%），于是按"正文保持可读"那条线收回 0.6；
     * · 收回之后用户在真机上把滑杆推到最底，报的是另一头：那块大面积底还是实底、推不到头
     *   （"最大透明度 80%"就是这一句），所以 0.6 那一条是**过头的保守**，现在按 0.2 划。
     * 0 仍然不给：整窗背景退到 0 就没有任何一层能承接正文，那一档不是"更透"而是"这一屏没了"。
     * ⚠ 面板可以到 0.2，**悬浮球不行**——球是全屏唯一的手势入口，淡到点不中等于功能消失，
     *   它那一侧的可读下限长在 `PanelBackdropOpacity.effectiveAlpha(readabilityFloor = true)`
     *   那颗参数上（0.5 为止）。这一格钉的是换算口自己的端点，不带球那侧的下限。
     *
     * 各条要挡住的坏实现：
     * · 端点（0/负数 → 0.6、100 与越上界 → 1f）：把越界原样抄出去的实现会交出 0f 或 1.8f，
     *   前者面板看不见、后者颜色溢出；
     * · 区间内线性（65 → 0.65、85 → 0.85）：只把下限钳住却把中段拍平（一律 0.6）的实现，
     *   滑杆就成了装饰；
     * · 就近对齐刻度（62 落回 60，绝不交出一个盘上存不下的数）：界面上的数字与盘上存的数字
     *   必须一模一样，否则重开设置会看到另一个数；
     * · 逐档往返（下面那个循环）：中途偷偷加一层"为了好看"的偏移（例如一律再减 0.05），
     *   端点与中段那几条可能还是绿的，而滑杆上写的 80% 画出来是 75%；
     * · null（这台机器从没滑过）：必须落到合法档，不能是 0f。
     */
    @Test
    fun `opacity maps the background layer linearly and clamps out-of-range values`() {
        // 合同区间的两端：滑到最底就是最底那一档（0.2 = 屏上"透明度 80%"），滑到顶是不透明
        assertEquals(0.2f, PanelBackdropOpacity.alphaOf(0), 0f)
        assertEquals(1f, PanelBackdropOpacity.alphaOf(100), 0f)
        // 区间内逐档线性（刻度 5%，两端都取到）
        assertEquals(0.65f, PanelBackdropOpacity.alphaOf(65), 0f)
        assertEquals(0.85f, PanelBackdropOpacity.alphaOf(85), 0f)
        // 非刻度的脏值就近对齐到合法刻度（62 落回 60，绝不交出一个盘上存不下的数）
        assertEquals(0.6f, PanelBackdropOpacity.alphaOf(62), 0f)
        // 越界夹紧：滑杆交不出 1f 以上的数，也交不出比合同下限更淡的数
        assertEquals(0.2f, PanelBackdropOpacity.alphaOf(-40), 0f)
        assertEquals(1f, PanelBackdropOpacity.alphaOf(180), 0f)
        // 从没写过这一项（老数据 / 升级）：落到默认那一档 = 完全不透明，不是"面板看不见"
        assertEquals(1f, PanelBackdropOpacity.alphaOf(null), 0f)

        // 逐档往返：这一族交出的每一个数，画出来的 alpha 必须还能原样读回那个数。
        // 刻度表由被测对象自己数（不许在这一格里抄第二份 60..100 的字面量清单）。
        val ticks = PanelBackdropOpacity.steps
        val broken = ticks.filter { (PanelBackdropOpacity.alphaOf(it) * 100).roundToInt() != it }
        assertTrue(
            "画出来的 alpha 与盘上那个整数不是同一个来源，坏掉的档位：$broken（${ticks.size} 档全跑）",
            broken.isEmpty()
        )
        // 循环哨兵：档数为 0 时上面那句会空跑成"一条都没坏 = 绿"，那是假绿
        assertEquals("这一格真的逐档量过（刻度表空了 = 量具瞎）", 17, ticks.size)
        assertEquals(
            "刻度就是 5% 一档、从区间下限到 100（下限由 60 挪到 20，档数 9 → 17 是自然结果）",
            (20..100 step 5).toList(),
            ticks
        )
    }

    /**
     * 整窗设置页**不是一扇浮层**，也不在任何地方偷偷挂一扇。
     *
     * 判的是形状而不是意图：自画的 `Dialog(`、设计系统的 `LbDialog(`、`LbModalSheet`、
     * `BottomSheet`、`Popup(` 任何一颗一出现，这一页就不再是"整个悬浮窗切过去"的那一页了
     * （面板那扇 overlay 窗口也起不了系统对话框——那里缺的是 Activity 的 window token）。
     * 词边界那一支是必要的：`LbDialog(` 里含 `Dialog(` 这个子串，裸 contains 会把
     * "只走共用浮层所有者"误报成"自己开了一扇窗"（同一族坑：按子串认的锚点会误吃前缀）。
     *
     * 以前这里登记过一条豁免：`SettingsIntentEntry.kt` 画一扇首次介绍浮层（叠加层、不是页容器）。
     * 那扇浮层连同"已经看过介绍"的盘上记录一起随新合同退场（开启意图就是开启，
     * 输入与保存同行直达），豁免与它的"幽灵证人"检查一并删除——
     * 谁再往设置目录里画 `LbModalSheet`，这一格当场红，直到有人重新登记并说清理由。
     */
    @Test
    fun `the settings page opens no floating surface and no external window`() {
        val selfDrawnDialog = Regex("(?<![0-9A-Za-z_])Dialog\\s*\\(")
        val needles = listOf(
            "LbDialog(" to "共用浮层的所有者（这一页要画在正文里）",
            "LbModalSheet" to "半屏 Sheet（首次介绍浮层已随新合同退场，这里不再有豁免）",
            "BottomSheet" to "半屏 Sheet",
            "Popup(" to "弹层",
            "startActivity" to "跳外部 Activity"
        )
        val hits = needles.flatMap { (needle, why) ->
            sources.filter { (_, code) -> code.contains(needle) }
                .map { "${it.first} 里的「$needle」——$why" }
        } + sources.filter { (_, code) -> selfDrawnDialog.containsMatchIn(code) }
            .map { "${it.first} 里自己开了一扇 Dialog(" }
        assertTrue("设置页长出了不该有的容器：\n$hits", hits.isEmpty())
    }

    /**
     * 反向证人：上面那把尺看得见东西。
     *
     * 否定式判据坏掉的时候最安静——正则写歪、路径接错、mask 把整份文件抹成空格，
     * 三种都表现为"一条都没扫到 = 通过"。所以拿生产里**真有**浮层的那一屏同判据跑一遍：
     * `LbDialog(` 必须被指认到，`BottomSheet` 必须指认不到（它确实不存在，
     * 这条同时证明我没有把名单写成"什么都命中"）。
     */
    @Test
    fun `the floating-surface ruler is not blind on a known dialog host`() {
        val provider = File(
            "src/main/java/com/lovebrain/app/ui/home/ProviderSection.kt"
        ).takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/home/ProviderSection.kt")
        assertTrue("找不到 $provider——这把尺会恒绿", provider.isFile)
        val code = SourceScan.maskComments(provider.readText(Charsets.UTF_8))
        assertTrue("首页供应商那一屏本来就走 LbDialog 那扇浮层，扫不到说明判据坏了", code.contains("LbDialog("))
        assertTrue("名单里那条不存在的形状不该被误报", !code.contains("BottomSheet"))
    }

    /**
     * 这一页只集中四件事，**不新造模块**。
     *
     * "偏好""工具中心""低频工具"都不是真实功能名，是历史上按使用频率做过的归类；
     * 这一页的分组口径也不授权新增一个叫"偏好"的模块。名字先钉住，免得日后又被"顺手加一格"。
     */
    @Test
    fun `the settings page does not introduce a preference or tool-hub module`() {
        val bannedWords = listOf("偏好", "工具中心", "低频工具")
        val hits = bannedWords.flatMap { word ->
            sources.filter { (_, code) -> code.contains(word) }.map { "${it.first} 里出现了「$word」" }
        }
        assertTrue("这一页新造了没有的结构：\n$hits", hits.isEmpty())
    }

    /**
     * 外框的**唯一主人是页面，且恰有三张**：「显示」「内容」「悬浮助手」各一张；
     * 页面这一侧不再往组与组之间塞任何东西（④）。
     *
     * 牙在这些计数上（每一颗各挡一种回退形状）：
     * · 页面里 `settingsEntryGroupCard()` 恰 3 次：画 6 张（每项一张卡的那种旧形制，正是这一页
     *   要拆掉的形状）、只画 2 张（把助手塞回某组里面）都红；
     * · 页面里 `settingsEntryCard()` 恰 0 次：整页只剩组外框，页面不许再给单项套旧卡壳；
     *   （"settingsEntryGroupCard" 不含 "settingsEntryCard" 子串，计数不会互相污染）
     * · 组名三根针各恰 1 次，且组名调用点恰三颗（少一颗=某一组没有名字，多一颗=框里长出了小标题）；
     * · ④ 两项：`Spacer(Modifier.height(` 全页归零，且**滚动柱以下一颗 `Spacer` 都不许有**
     *   （页头那一行里的横撑与 `weight` 不在这把尺射程内，它们本来就在滚动柱以上）。
     *   反例证人拿真实源码改一处（两组建之间插一颗 `Spacer(Modifier.height(Spacing.md))`），
     *   同一把尺必须报一颗——证明它不是恒零的摆设。
     */
    @Test
    fun `the page owns exactly three group frames and no per-item stacking`() {
        val page = maskedOf(PAGE_FILE)
        assertEquals(
            "整页只剩三张外框：「显示」「内容」「悬浮助手」各一张",
            3, page.countOf("settingsEntryGroupCard()")
        )
        assertEquals(
            "页面自己一张旧单项卡壳都不画（卡套卡就是这一版要拆的形状）",
            0, page.countOf("settingsEntryCard()")
        )
        listOf(
            "R.string.settings_group_display" to "显示",
            "R.string.settings_group_content" to "内容",
            "R.string.settings_group_assistant" to "悬浮助手"
        ).forEach { (needle, label) ->
            assertEquals("组名「$label」($needle) 在组外恰画一次", 1, page.countOf(needle))
        }
        assertEquals("组名调用点恰三颗（每组一颗，不多不少）", 3, callOffsets(page, "SettingsGroupTitle").size)
        assertEquals(
            "逐项 Spacer 堆叠已拆掉：组与组之间的呼吸归组名那一行，不再用高度垫",
            0, Regex("""Spacer\(Modifier\.height\(""").findAll(page).count()
        )
        assertTrue(
            "滚动柱以下又长出了 Spacer（组与组之间只由组名那一行留呼吸）：${spacersBelowTheScrollColumn(page)}",
            spacersBelowTheScrollColumn(page).isEmpty()
        )
        // ④ 的反例证人：往两组建之间塞一颗高度 Spacer，同一把尺必须报两颗（高度那把 + 位置那把）。
        val spans = groupFrameSpans(page)
        assertEquals("外框跨度应当数得出三棵 Column 子树", 3, spans.size)
        val stuffed = StringBuilder(page).insert(spans.first().second, "\nSpacer(Modifier.height(Spacing.md))\n").toString()
        assertTrue("反例没被打破：插入的 Spacer 未被 spacersBelowTheScrollColumn 认出", spacersBelowTheScrollColumn(stuffed).isNotEmpty())
        assertEquals("反例没被打破：插入的高度 Spacer 未被那句计数认出", 1,
            Regex("""Spacer\(Modifier\.height\(""").findAll(stuffed).count())
    }

    /**
     * ② 组名画在**框外**：三颗组名都不落在任何一张组外框的子树里。
     *
     * 这条判的是位置而不是"读得到字"：语义树里数得出三句组名，但数不出它在框里还是框外；
     * 而"组名被塞回框里当第一行"正是10-11 那一版那种"卡里再套一层标题"的形状，也是原型没有的脸
     * （原型 `.section-title` 与 `.group` 是**并列**的两个块，不是嵌套）。
     *
     * 牙：把第一颗组名搬进它那一张框的子树里（反例证人，同一把尺跑改过的源码），必须报一颗。
     * 顺带钉"组名排在它那组内容之前"那句旧判据——它抓的是读序，这一格抓的是父子关系，两把不重复。
     */
    @Test
    fun `group titles are drawn outside the frames they name`() {
        val page = maskedOf(PAGE_FILE)
        val strays = titlesInsideFrames(page)
        assertTrue("有组名被画进了框里面（原型 .section-title 与 .group 是并列块）：$strays", strays.isEmpty())

        val frameAt = listOf(
            "R.string.settings_group_display" to "SettingsOpacityEntry(",
            "R.string.settings_group_content" to "SettingsKbSwitcherEntry(",
            "R.string.settings_group_assistant" to "SettingsAssistantEntry("
        )
        frameAt.forEach { (title, entry) ->
            assertTrue(
                "组名 $title 必须排在它那组的内容 $entry 之前（组名在组外）",
                page.indexOf(title) in 0 until page.indexOf(entry)
            )
        }

        // 反例证人：把第一颗组名搬到它那张框的子树里（框的收尾之前），同一把尺必须当场报。
        val first = groupFrameSpans(page).first()
        val pushed = StringBuilder(page)
            .insert(first.second - 1, "SettingsGroupTitle(stringResource(R.string.settings_group_display))\n")
            .toString()
        assertTrue("反例没被打破：塞进框里的组名没被 titlesInsideFrames 认出", titlesInsideFrames(pushed).isNotEmpty())
    }

    /**
     * ③ 组内分隔线：**只有一个主人、只出现在组内、色取 `BorderLight`**。
     *
     * 原型 `.item + .item{border-top:1px solid #e6e8ed}`：换算表里 `#e6e8ed` ≈ hsl(250 5% 88%)
     * 就是仓库那颗 `BorderLight`，比外框那颗 `Border`（hsl(250 6% 82%)）浅一档。
     * 10-11 那一版这里写的是框那颗同色，于是"框"和"框里的分界"读起来是同一层线。
     *
     * 四把尺各挡一种回退：
     * · 画线处只许一个主人（`HorizontalDivider(` 全页恰一颗，而且落在 helper 自己的体里）——
     *   谁在页面里再手画一条线就红；
     * · 调用位恰三处（显示组两条、内容组一条，助手组只有一行、没有行可分）——多一条=组之间画了线；
     * · 每条调用点都落在某一张框的子树**之内**（分隔线是"这几行属于一张框"的凭据，组之间不用它）；
     * · helper 体里那颗色必须是 `BorderLight`，宽度仍指回 `AppDimens.BORDER_WIDTH_DP`，
     *   体里不许出现任何数字（这颗由 `OddShapeOwnershipTest` 按**委托壳**记账，写了自己的数就没人认领）。
     *
     * 反例证人三则，全部拿真源码改一处再跑同一把尺：线搬到框外、色改回 `Border`、宽度写成 `2.dp`。
     */
    @Test
    fun `row dividers sit inside a group and wear the lighter border token`() {
        val page = maskedOf(PAGE_FILE)
        // 画线那颗**主人搬到家族文件了**（10-13）：知识库那一格展开列表的上沿要用同一条线
        // （原型 `.kb-options{border-top}` 与 `.item + .item{border-top}` 同色同宽），
        // 一条视觉关系不该有两个落点。所以这一格量两份源码：家族里那一颗定义、页面里那三处调用。
        val family = maskedOf(FAMILY_FILE)
        val body = maskedBody(family, "SettingsRowDivider")

        assertEquals("整族里画线处只许有一颗，主人是家族文件那颗 SettingsRowDivider", 1,
            callOffsets(family, "HorizontalDivider").size)
        assertEquals("页面自己不许再画第二种线（搬回来就是两个落点）", 0,
            callOffsets(page, "HorizontalDivider").size)
        val bodyAt = family.indexOf("fun SettingsRowDivider(")
        val dividerAt = callOffsets(family, "HorizontalDivider").single()
        assertTrue("那条线不是由 SettingsRowDivider 画的（画线处换了主人）", dividerAt > bodyAt)

        val calls = callOffsets(page, "SettingsRowDivider")
        assertEquals("组内行分隔恰三条：显示组两条、内容组一条，助手组没有行可分", 3, calls.size)
        val outside = dividersOutsideFrames(page)
        assertTrue("有分隔线画在组外（组之间的呼吸归组名那一行，不归线）：$outside", outside.isEmpty())
        // 展开那一列的上沿也走同一颗线：那一格里画线处恰一处，且不是自己新造的第二种线
        val kb = maskedOf(KB_FILE)
        assertEquals("知识库那一格只借这一颗线画展开块的上沿，恰一处", 1,
            callOffsets(kb, "SettingsRowDivider").size)
        assertEquals("那一格里不许另起一种 HorizontalDivider", 0,
            callOffsets(kb, "HorizontalDivider").size)

        val ink = dividerInkViolations(body)
        assertTrue("组内分隔线这一格不合档：$ink", ink.isEmpty())

        // 反例证人 ①：最后一条线搬到所有框之外（组之间画线）。
        val lastSpan = groupFrameSpans(page).last()
        val movedOut = StringBuilder(page).insert(lastSpan.second, "\nSettingsRowDivider()\n").toString()
        assertTrue("反例没被打破：搬到组外的分隔线没被认出", dividersOutsideFrames(movedOut).isNotEmpty())

        // 反例证人 ②③：同一条线换成框那颗色 / 宽度自己写数。
        val backToBorder = body.replace("color = BorderLight", "color = Border")
        assertTrue("反例没被打破：回退成 Border 没被认出", dividerInkViolations(backToBorder).isNotEmpty())
        val selfMeasured = body.replace("AppDimens.BORDER_WIDTH_DP.dp", "2.dp")
        assertTrue("反例没被打破：自己写宽度没被认出", dividerInkViolations(selfMeasured).isNotEmpty())
    }

    /**
     * ① 组名那一格**照原型画脸**：`labelSmall` + `SemiBold` + `TextSecondary` + 上下各 `Spacing.md`，
     * 横向不缩、体里一个自写的数都没有。
     *
     * 判据来源：原型 `.section-title{font-size:10.5px;font-weight:650;color:var(--muted);
     * padding:10px 5px 7px;letter-spacing:.5px}`，落点由对照原型换算表那一行冻结。
     * 10-11 那一版这里写的是 `labelMedium` + 上 12 / 下 4——字阶与行标签只差一丝还更细，
     * 上下留白也比原型歪（12/4 对 10/7），页面上"组名"读起来既不像索引也不够紧凑。
     *
     * 取档那一笔的算术写在这里，免得后来人以为是随手挑的：10 与 7 各差 2 与 1 收到 `Spacing.md`(8)，
     * 整行厚度 = 8 + 14(`labelSmall.lineHeight`) + 8 = **30**，原型 = 10 + ≈13 + 7 ≈ **30**。
     * `letter-spacing:.5px` 不采纳（要新造 `TextStyle` 才拿得到，这一版禁止新造 token）。
     *
     * 反例证人：把生产那一行改回旧字阶（`labelMedium` + `padding(top = 10.dp, bottom = 7.dp)`），
     * 同一把尺必须同时报出"字阶不对"和"体里出现自写的数"两颗。
     */
    @Test
    fun `the group title wears the prototype face`() {
        val page = maskedOf(PAGE_FILE)
        val body = maskedBody(page, "SettingsGroupTitle")
        val violations = groupTitleFaceViolations(body)
        assertTrue("组名那一格与原型 .section-title 对不上：$violations", violations.isEmpty())

        val oldFace = body
            .replace("AppTypography.labelSmall", "AppTypography.labelMedium")
            .replace("padding(top = Spacing.md, bottom = Spacing.md, start = Spacing.sm)", "padding(top = 10.dp, bottom = 7.dp, start = 5.dp)")
            .replace("fontWeight = FontWeight.SemiBold", "fontWeight = FontWeight.Normal")
        val broken = groupTitleFaceViolations(oldFace)
        assertTrue("反例没被打破：旧字阶那一版没被认出（实到 $broken）", broken.size >= 3)
        assertTrue("反例里「字阶不对」那颗必须在：$broken", broken.any { it.contains("labelSmall") })
        assertTrue("反例里「字重不对」那颗必须在：$broken", broken.any { it.contains("SemiBold") })
        assertTrue("反例里「不许写新 dp」那颗必须在：$broken", broken.any { it.contains(".dp") })
    }

    /**
     * ⑤ 页头维持 F3 那一档 30（**原型的 40 不采纳**）+ ④「收起不等于停止」在源码侧的凭据。
     *
     * 页头那一格：常量读数 30、`height` 落点恰一颗、不许回退去借 `LbTopBarLevel`
     * （那是 F1/F2 全屏页的 48 厚顶栏，正是"点齿轮进去像另一个 App"那句投诉的来源）。
     * 原型 `.hd{height:40px}` 有意不照画，理由写在 `SettingsHeaderDimens` 的 KDoc 里；
     * 语义树那一侧由 `SettingsPageSemanticsTest` 把页头节点卡在 ≤30（`TouchTier.PANEL_HEADER_ROW`），
     * 这一格钉的是源码——哪天有人把常量抬到 40，那边量得到、这边也读得到。
     *
     * 「收起」那一格：字形与名字归面板一族**共用**的那颗 `PanelCollapseAction`（本体页头同款），
     * 这一页只许引用它恰一颗、不许再抄第二份名字；服务侧那几颗口（`stopSelf` / `stopForeground` /
     * `removeView` / `WindowState` / `tempHide` / `startActivity` …）这一页一颗都不许碰；
     * 也不自己 `remember` / `mutableStateOf` 一份"收起了没有"。
     * 反例证人：给页头那一行加一颗 `remember { mutableStateOf(false) }` 并去调 `stopSelf()`，
     * 同一把尺必须报出三颗（状态两颗 + 停止口一颗）。
     */
    @Test
    fun `the page header keeps the F3 row and collapse is not stop`() {
        val page = maskedOf(PAGE_FILE)

        val header = headerTierViolations(page)
        assertTrue("页头那一档不成立了：$header", header.isEmpty())

        val collapse = collapseIsNotStopViolations(page)
        assertTrue("「收起」那一颗越界了：$collapse", collapse.isEmpty())

        // 反例证人 ①：把页头抬回原型那 40（这一版明令不采纳），这一把尺必须报。
        val raised = page.replace("ROW_HEIGHT_DP = 30", "ROW_HEIGHT_DP = 40")
        assertTrue("反例没被打破：抬回 40 没被认出", headerTierViolations(raised).isNotEmpty())
        // 反例证人 ①b：这一页改去借 `LbTopBarLevel` 那一档（48 厚顶栏回退），也必须报。
        val borrowed = page.replace("SettingsHeaderDimens", "LbTopBarLevel")
        val borrowedTold = headerTierViolations(borrowed)
        assertTrue("反例没被打破：借 LbTopBarLevel 没被认出（实到 $borrowedTold）",
            borrowedTold.any { it.contains("LbTopBarLevel") })
        // 反例证人 ②：这一页自己存状态并去停服务——「收起」写成「停止」那种回退。
        val drifting = page.replace(
            "SettingsGroupTitle(stringResource(R.string.settings_group_display))",
            "val collapsed = remember { mutableStateOf(false) }\nFloatingService.stopSelf()\n" +
                "SettingsGroupTitle(stringResource(R.string.settings_group_display))"
        )
        val told = collapseIsNotStopViolations(drifting)
        assertTrue("反例没被打破：自己存状态 + 自己停服务没被认出（实到 $told）", told.size >= 3)
        assertTrue("反例里 FloatingService 那颗必须在：$told", told.any { it.contains("FloatingService") })
        assertTrue("反例里 remember 那本第二账必须在：$told", told.any { it.contains("remember") })
    }

    /**
     * ④b 滚动柱首尾那一格：顶 `Spacing.sm` / 底 `Spacing.xl`（原型 `.content{padding:5px 10px 17px}`），
     * 横向不许再缩一层，那一段里不许出现自写的 dp。
     *
     * 10-11 那一版这里写的理由是"页头已降到 30 高，这里留一小段与本体同档"——那是一句没有来处的口味。
     * 数值本来就在档上（这一版没改数，改的是**理由**与**这一格的牙**）：反例三则——
     * 换松一档（`lg`/`xxl`）、直接写 `5.dp`/`17.dp`（新造数）、滚动柱自己再缩一层横向。
     */
    @Test
    fun `the scroll column wears the prototype's two insets`() {
        val page = maskedOf(PAGE_FILE)
        val violations = scrollInsetViolations(page)
        assertTrue("滚动柱首尾与原型 .content 对不上：$violations", violations.isEmpty())

        val looser = page.replace(
            ".padding(top = Spacing.sm, bottom = Spacing.xl)",
            ".padding(top = Spacing.lg, bottom = Spacing.xxl)"
        )
        assertTrue("反例没被打破：整段松一档没被认出", scrollInsetViolations(looser).isNotEmpty())

        val handMeasured = page.replace(
            ".padding(top = Spacing.sm, bottom = Spacing.xl)",
            ".padding(top = 5.dp, bottom = 17.dp)"
        )
        val told = scrollInsetViolations(handMeasured)
        assertTrue("反例没被打破：自写的 5/17 没被认出（实到 $told）",
            told.isNotEmpty() && told.any { it.contains(".dp") })

        val indented = page.replace(
            ".padding(top = Spacing.sm, bottom = Spacing.xl)",
            ".padding(horizontal = Spacing.md, top = Spacing.sm, bottom = Spacing.xl)"
        )
        assertTrue("反例没被打破：滚动柱自己再缩横向没被认出", scrollInsetViolations(indented).isNotEmpty())
    }

    /**
     * 各 Entry **只渲染组内的那一行，不再自带卡壳**。
     *
     * 逐文件计数而不是"整目录找一遍"：哪个 Entry 哪天又把 `settingsEntryCard()` 加回自己身上，
     * 失败信息直接点名是哪一格长回了卡。`settingsEntryCard()` 这颗 helper 本身仍留在
     * `SettingsEntryFamily.kt`（别的调用位还在读它），这里不判它，只判"行不许拿它当壳"。
     */
    @Test
    fun `entries render rows inside the group instead of carrying their own frames`() {
        val entryFiles = listOf(
            "SettingsOpacityEntry.kt",
            "SettingsBubbleSizeEntry.kt",
            "SettingsReplyLayoutEntry.kt",
            "SettingsKbSwitcherEntry.kt",
            "SettingsIntentEntry.kt",
            "SettingsAssistantEntry.kt"
        )
        val offenders = entryFiles.map { name ->
            name to maskedOf(name).countOf("settingsEntryCard()")
        }.filter { (_, count) -> count != 0 }
        assertTrue(
            "这些 Entry 还给自己画外框（卡套卡的另一半来源）：\n" +
                offenders.joinToString("\n") { "  ${it.first} 里 settingsEntryCard() 出现 ${it.second} 次" },
            offenders.isEmpty()
        )
        // 反向证人（尺不是瞎的）：页面这一侧此刻确实还数得出三张组外框（上一格判过计数，
        // 这里补一句"扫描真的读到了这些文件"——任何一个 Entry 文件消失或被改名，
        // maskedOf 会先 error，而不是"数到 0 次 = 绿"地空过）。
        entryFiles.forEach { assertTrue("$it 的掩码源码为空", maskedOf(it).isNotBlank()) }
    }

    /**
     * 读序合同：**透明度 → 当前知识库 → 意图**，且助手那一组排在最后。
     *
     * 切库排在意图前面不是排版口味：意图是按库隔离的那一份数据，先认对象、再改那一块的那件事，
     * 读序与"切库必须取对应数据"同一头。回退成旧的 opacity → intent → kb 会红在最后那一句；
     * 助手组被塞回正文中间（旧"每项一张卡"的第一格）会红在补的那一句。
     */
    @Test
    fun `the settings family is organized in the order the spec names`() {
        val page = maskedOf(PAGE_FILE)
        val opacity = page.indexOf("SettingsOpacityEntry(")
        val switcher = page.indexOf("SettingsKbSwitcherEntry(")
        val intent = page.indexOf("SettingsIntentEntry(")
        assertTrue(
            "三格都得在这一页里（opacity=$opacity kb=$switcher intent=$intent）",
            opacity >= 0 && switcher >= 0 && intent >= 0
        )
        assertTrue(
            "读序要的是透明度、当前知识库、意图，实到 opacity=$opacity kb=$switcher intent=$intent",
            opacity < switcher && switcher < intent
        )
        val assistant = page.indexOf("SettingsAssistantEntry(")
        assertTrue(
            "悬浮助手那一组排在正文三格之后（独立一组，不再当页头第一格）",
            assistant > intent
        )
    }

    /**
     * 「切库不许把 A 的意图展示或写给 B」那条判据的纯函数那一半。
     *
     * 任一侧认不出来（宿主没交库名、或这一稿根本没被编辑过）都放行——拦下没接线那一侧
     * 会让"从没切过库"的正常保存变成静默失败，那是另一种假象。真正的牙在最后一行。
     */
    @Test
    fun `a draft is only written to the library it was typed on`() {
        assertTrue(intentWriteOwnedBy(draftOwnerKb = null, currentKb = "kbB"))
        assertTrue(intentWriteOwnedBy(draftOwnerKb = "kbA", currentKb = null))
        assertTrue(intentWriteOwnedBy(draftOwnerKb = "kbA", currentKb = "kbA"))
        assertFalse(
            "这一稿打在 A 上、屏幕已经是 B：这一记必须被挡下",
            intentWriteOwnedBy(draftOwnerKb = "kbA", currentKb = "kbB")
        )
    }

    // ═══════════ 对齐的数值判据：量语义树，不读源码 ═══════════

    /**
     * 横向内缩只有一个主人：**组外框**。行自己不再垫横向 padding。
     *
     * 判的是数值边缘而不是"看起来齐了"：
     * · 裸行（外框不给 padding）里，标签左边缘 = 外包盒左边缘、选择器右边缘 = 外包盒右边缘
     *   ⇒ 证明行自己没有再垫横向（回退成旧的"行自带横 12"当场红：边缘会各差 12dp）；
     * · 组外框（横 [Spacing.lg]）里，两行标签的左边缘同一条线、尾部选择器的**右边缘**同一条线
     *   ——两颗选择器故意画成 28dp 与 44dp 两种宽度：只右对齐才可能边缘重合，
     *   "按左边缘排"或"各自再缩一层"的实现都会在这一句上分开；
     * · 标签左、选择器右各自离外框内缘的距离恰是 [Spacing.lg]：外框与行**都不许多垫一层、
     *   也不许谁都不垫**（双重内缩与贴边塌陷两种坏形状各红一头）。
     *
     * 夹具只量这两个主人本身（组外框 helper + 行组件），不挂真实 Entry：那些 Entry 的内部
     * 控件版式也在同步调整，把它们的具体控件长进这一格只会制造与判据无关的红。
     * 真实页面里"每行一个标签 + 一个右对齐选择器"的形状与这里逐字同构。
     */
    @Test
    fun `the group frame owns the horizontal inset and rows share their label and selector edges`() {
        val inset = Spacing.lg.value
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(260, heightDp = 420).RenderIn(deviceDensity) {
                Column {
                    // 裸行：外包盒不给横向 padding——行若还自带一层，边缘就会离开外包盒。
                    Box(modifier = Modifier.testTag(RAW_FRAME_TAG)) {
                        LbSettingRow(
                            title = ROW_TITLE_OUTSIDE,
                            subtitle = "",
                            trailing = { Box(Modifier.size(28.dp).testTag(SELECTOR_ONE)) }
                        )
                    }
                    // 组外框：横向内缩的唯一主人；两行之间一条分隔线，选择器画两种宽度。
                    Column(modifier = Modifier.testTag(GROUP_FRAME_TAG).settingsEntryGroupCard()) {
                        LbSettingRow(
                            title = ROW_TITLE_FIRST,
                            subtitle = "",
                            trailing = { Box(Modifier.size(28.dp).testTag(SELECTOR_TWO)) }
                        )
                        HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = BorderLight)
                        LbSettingRow(
                            title = ROW_TITLE_SECOND,
                            subtitle = "",
                            trailing = { Box(Modifier.size(44.dp).testTag(SELECTOR_THREE)) }
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        val probe = SemanticsProbe(density)
        fun targetOf(matcher: androidx.compose.ui.test.SemanticsMatcher): SemanticsProbe.Target {
            // 只有 testTag 的节点不进合并语义树，必须按未合并那一侧读（本仓既有先例）。
            val nodes = rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("量具只认一颗，实到 ${nodes.size}", 1, nodes.size)
            return probe.of(nodes.single())
        }
        fun tagTarget(tag: String) = targetOf(hasTestTag(tag))
        fun labelLeft(title: String) = targetOf(hasText(title)).leftDp
        fun selectorRight(tag: String): Float = tagTarget(tag).let { it.leftDp + it.widthDp }

        // ① 裸行：行自己交出零横向内缩（左标签、右选择器都贴在外包盒边缘）。
        val rawFrame = tagTarget(RAW_FRAME_TAG)
        assertTrue(
            "行组件又自带横向 padding 了：标签左边缘离外包盒 ${labelLeft(ROW_TITLE_OUTSIDE) - rawFrame.leftDp}dp",
            abs(labelLeft(ROW_TITLE_OUTSIDE) - rawFrame.leftDp) < EDGE_TOLERANCE_DP
        )
        assertTrue(
            "行组件又自带横向 padding 了：选择器右边缘离外包盒 ${rawFrame.leftDp + rawFrame.widthDp - selectorRight(SELECTOR_ONE)}dp",
            abs(rawFrame.leftDp + rawFrame.widthDp - selectorRight(SELECTOR_ONE)) < EDGE_TOLERANCE_DP
        )

        // ② 组外框：横向 [Spacing.lg] 由框一处给，行贴到框内缘。
        val frame = tagTarget(GROUP_FRAME_TAG)
        val frameRight = frame.leftDp + frame.widthDp
        assertEquals("组外框内左侧的标签要恰离框边 ${inset}dp", inset, labelLeft(ROW_TITLE_FIRST) - frame.leftDp, EDGE_TOLERANCE_DP)
        assertEquals("组外框内右侧的选择器要恰离框边 ${inset}dp", inset, frameRight - selectorRight(SELECTOR_TWO), EDGE_TOLERANCE_DP)

        // ③ 同组跨行对齐：标签左边缘一条线，选择器右边缘一条线（宽度 28 与 44 不同，靠右缘重合）。
        assertEquals(
            "同组两行的标签左边缘必须同一条线",
            labelLeft(ROW_TITLE_FIRST), labelLeft(ROW_TITLE_SECOND), EDGE_TOLERANCE_DP
        )
        assertEquals(
            "同组两行的选择器右边缘必须同一条线（两颗宽度不同，左对齐冒充不了右对齐）",
            selectorRight(SELECTOR_TWO), selectorRight(SELECTOR_THREE), EDGE_TOLERANCE_DP
        )
    }

    companion object {
        /** 这几把结构尺读的那一份正文（名字写一处，六把尺不许各自抄一遍文件名） */
        private const val PAGE_FILE = "LoveBrainSettingsContent.kt"

        private const val FAMILY_FILE = "SettingsEntryFamily.kt"

        private const val KB_FILE = "SettingsKbSwitcherEntry.kt"

        private const val RAW_FRAME_TAG = "structure-raw-frame"
        private const val GROUP_FRAME_TAG = "structure-group-frame"
        private const val SELECTOR_ONE = "structure-selector-one"
        private const val SELECTOR_TWO = "structure-selector-two"
        private const val SELECTOR_THREE = "structure-selector-three"

        /** 夹具标签用 ASCII：语义树里按整串文字认节点，中文串会被 locale/断词干扰（本仓既有教训） */
        private const val ROW_TITLE_OUTSIDE = "STRUCTURE-ROW-OUTSIDE"
        private const val ROW_TITLE_FIRST = "STRUCTURE-ROW-FIRST"
        private const val ROW_TITLE_SECOND = "STRUCTURE-ROW-SECOND"

        /** 边缘比对的容差：亚像素舍入（mdpi 下基本是整数，留半像素不制造假红） */
        private const val EDGE_TOLERANCE_DP = 0.6f
    }
}
