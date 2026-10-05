package com.lovebrain.app.ui.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 切页动效与"一次有意横滑就切换"判据的**纯函数格 + 接线证人**（基线 v1.1 §6.5 / 需求映射 原始 11、20）。
 *
 * 这一颗文件为什么单独存在（三件事，每件都只在这台仪器能测的范围内判）：
 *
 * · **松手判据**：位移那条线与甩速那道门是**或**关系。注入手势那一侧量不出可信的甩速
 *   （注入节奏与真机 120/240Hz 采样不是一回事，`HorizontalVelocityEstimator` 又明写"时间没
 *   走动一律 0"），所以"快扫一小段也要翻页"这一档**只在纯函数这里钉**——
 *   `PanelPageSwipePagerTest` 里那几格只按位移轴判，两边各买各的，不互相冒充。
 *   这是本仓库记过的坑：手势判据要按生产算式写成或关系，只写位移会把"快扫一小段"判成不翻页。
 *
 * · **相机算式**（原始第 20 条那一半）：画面落点只由一根相机（单位=页）决定，
 *   页号那一路画不出差别。这里量的是**代数**：跟手恒等式、两页首尾相接（永不出空档）、
 *   页宽没量到时不挪相机。逐帧那一半归 `PanelPageSwipePagerTest` 里按帧读树的两格；
 *   真机上到底还闪不闪**不许在这里签**（未验证-需真机录像）。
 *
 * · **接线证人**：`PanelPageMotion.kt` 上一席落的时候是"只有测试引用"的半成品（仓库记过的同一颗坑：
 *   新实现文件若只有测试引用＝功能没接线）。所以这里显式判生产那一侧真的在读它，
 *   并且反向判旧形制不许被抄回来（`PanelPagerDimens`、第二套 `awaitEachGesture`、裸写的 `tween(数字)`）。
 *
 * 反例总表（哪一处写坏就会打到哪一格）都写在每格自己的注释里。
 */
class PanelPageMotionTest {

    // ───────────────────── 夹具：源码位置与剥注释 ─────────────────────

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private fun codeOf(vararg path: String): String {
        val file = File(appRoot, path.joinToString(File.separator))
        assertTrue("找不到 $file——搬家了就要同步改这条", file.isFile)
        return stripComments(file.readText())
    }

    /**
     * 剥掉注释与字符串，只留可执行代码（一趟字符扫描，不分先后两种剥法——
     * `OddShapeOwnershipTest` 记过那两条自伤：先剥块注释会被行注释里的起头一路吃到很后面，
     * 先剥行注释会被 KDoc 里的 `https://` 吃掉行尾的收口）。
     * 这里不需要保留行结构（判据都是"这一段里有没有那颗符号"），所以命中就吐空格。
     */
    private fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        var blockDepth = 0
        var quote: Char? = null
        var inLine = false
        while (i < src.length) {
            val c = src[i]
            when {
                inLine -> {
                    if (c == '\n') { inLine = false; out.append('\n') } else out.append(' ')
                    i++
                }
                blockDepth > 0 -> {
                    when {
                        src.startsWith("/*", i) -> { blockDepth++; i += 2 }
                        src.startsWith("*/", i) -> { blockDepth--; i += 2 }
                        else -> { out.append(if (c == '\n') '\n' else ' '); i++ }
                    }
                }
                quote != null -> {
                    when {
                        c == '\\' -> { out.append("  "); i += 2 }
                        c == quote -> { quote = null; out.append("  "); i++ }
                        else -> { out.append(if (c == '\n') '\n' else ' '); i++ }
                    }
                }
                src.startsWith("//", i) -> { inLine = true; i += 2 }
                src.startsWith("/*", i) -> { blockDepth = 1; i += 2 }
                c == '"' || c == '\'' -> { quote = c; out.append(' '); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private val pagerCode: String get() = codeOf("ui", "panel", "PanelPagePager.kt")
    private val headerCode: String get() = codeOf("ui", "panel", "PanelHeader.kt")
    private val motionCode: String get() = codeOf("ui", "panel", "PanelPageMotion.kt")

    // ─────────────── A. 时长只有一颗主人（原始 20 的第一半） ───────────────

    /**
     * 切页平移与页头高亮平移**同一颗时长**，数值是基线定的 200ms（改前 220/250 两条并行）。
     * 反例：任一边把数字抄回自己文件里 ⇒ ①②③ 三句里至少一句红（`SLIDE_MS` 读不到，或裸数字回来了）；
     * 反例：把兜底那颗调成比过渡还短 ⇒ ④红（过渡没画完就被打断，正是"闪"的同族）。
     */
    @Test
    fun `切页与页头读同一颗时长，没有第二条时序`() {
        assertEquals("基线 v1.1 §6.5 定的切页时长", 200, PanelPageMotion.SLIDE_MS)
        assertTrue(
            "页面平移必须读 PanelPageMotion.SLIDE_MS（实到 " +
                Regex("SLIDE_MS").findAll(pagerCode).count() + " 处引用）",
            pagerCode.contains("PanelPageMotion.SLIDE_MS")
        )
        assertTrue(
            "页头高亮平移必须读同一颗（原来那颗是裸写的 tween(250)）",
            headerCode.contains("PanelPageMotion.SLIDE_MS")
        )
        // 反向证人：时长不许在页面/页头文件里各写一份数字。
        // 这一档本轮**加宽**过：旧写法只抓 `tween(250`，把 `tween(durationMillis = 250 …)` 或者
        // 干脆换一条动效族（`spring` / `keyframes` / `snap`）抄回第二时序都放过去了。
        val numericSpec = Regex("tween\\s*(<[^>]*>)?\\s*\\(\\s*(\\w+\\s*=\\s*)?-?\\d")
        val otherSpecFamily = Regex("\\b(spring|keyframes|snap)\\s*(<[^>]*>)?\\s*\\(")
        listOf("页面平移" to pagerCode, "页头高亮" to headerCode).forEach { (who, code) ->
            assertFalse(
                "$who 不许自带第二条时长（tween 的实参只能是那颗主人；抓到数值 tween=" +
                    numericSpec.findAll(code).joinToString(" | ") { it.value } +
                    "，抓到别的动效族=" + otherSpecFamily.findAll(code).joinToString(" | ") { it.value} + "）",
                numericSpec.containsMatchIn(code) || otherSpecFamily.containsMatchIn(code)
            )
        }
        assertTrue(
            "owner 交接兜底不许短于过渡本身（否则动画没画完就被它打断）：" +
                PanelPageMotion.OWNER_HANDOFF_MS,
            PanelPageMotion.OWNER_HANDOFF_MS >= PanelPageMotion.SLIDE_MS
        )
    }

    /**
     * **接线证人**：`PanelPageMotion.kt` 里那三件（时长主人 / 相机算式 / 手势本体）必须被生产读走。
     *
     * 上一席落这一格时它谁都没接（全仓 grep 只命中它自己），那种"半成品"在本仓库有过记录：
     * 只有测试引用＝功能没接线。所以这一格判的是**生产侧**的引用，判不到测试自己。
     * 反例：把 `runPageSwipeGesture` 换回 pager 里的第二套 `awaitEachGesture` ⇒ ①②红；
     * 反例：画面落点重新写成 `(index - currentPage) * W + slot`（闪烁那一形的原身）⇒ ③④红；
     * 反例：`PanelPagerDimens`（220/260 那一族私有数）被抄回来 ⇒ ⑤红。
     */
    @Test
    fun `切页动效件真的接在生产路径上，不是只有测试在引用`() {
        assertTrue(
            "滑页宿主必须调用手势本体 runPageSwipeGesture",
            pagerCode.contains("runPageSwipeGesture(")
        )
        assertTrue(
            "滑页宿主必须以 PageMotion 落地（手势与动作之间只许这一个接口）",
            pagerCode.contains("object : PageMotion") && pagerCode.contains("motion = motion")
        )
        assertTrue(
            "落点算式必须出自相机件（不许把页号也写进平移算式——那是闪烁那一半的原身）",
            pagerCode.contains("PanelPageMotion.translationFor(")
        )
        assertTrue(
            "跟手必须走相机件那条算式（不许再拿页宽做减法补偿）",
            pagerCode.contains("PanelPageMotion.cameraForDrag(")
        )
        assertFalse(
            "私有那族旧时长（PanelPagerDimens）已并入主人，不许被抄回来",
            pagerCode.contains("PanelPagerDimens")
        )
        // 手势实现只许有一份
        assertEquals(
            "切页手势的循环不许回到滑页宿主里（那里只许调 runPageSwipeGesture），实到 " +
                Regex("awaitEachGesture\\s*\\{").findAll(pagerCode).count(),
            0,
            Regex("awaitEachGesture\\s*\\{").findAll(pagerCode).count()
        )
        assertEquals(
            "手势本体在动效件里恰一处（import 那一句不算），实到 " +
                Regex("awaitEachGesture\\s*\\{").findAll(motionCode).count(),
            1,
            Regex("awaitEachGesture\\s*\\{").findAll(motionCode).count()
        )
    }

    // ─────────────── B. 松手判定：位移线 OR 甩速门（原始 11） ───────────────

    /**
     * 提交线是页宽的比（0.25，基线 v1.1 §6.5 从 0.34 降下来），**不是**定值像素；
     * 页宽没量到时两条轴都不许凭空够门。
     * 反例：比写成固定 px ⇒ 换页宽那两句红（窄面板滑到底也换不了页）；
     * 反例：压线判成 `>` ⇒ "正好够线"两句红；
     * 反例：页宽 0 时把 0 行程当够线 ⇒ 页宽未量到那句红。
     */
    @Test
    fun `位移那条线是页宽的比，压线即过`() {
        assertEquals("360dp 面板上的提交线在 90px", 90.0, PageSwipe.commitLineFor(360f).toDouble(), 0.0)
        assertEquals(250.0, PageSwipe.commitLineFor(1000f).toDouble(), 0.0)
        assertTrue("正好压线就算够（>=，不是 >）", PageSwipe.committedByTravel(-250f, 1000f))
        assertTrue("两个方向同一条线", PageSwipe.committedByTravel(250f, 1000f))
        assertFalse("差一点点不算", PageSwipe.committedByTravel(-249.99f, 1000f))
        assertFalse("页宽没量到时不许凭空够线", PageSwipe.committedByTravel(-500f, 0f))
        assertFalse("负页宽是坏读数", PageSwipe.committedByTravel(-500f, -1f))
    }

    /**
     * 甩速那道门是 **400dp/s**（单位就写在判据上，像素换算归手势那一侧的 `Density`）：
     * 压线即过、差一点点不过、读数 0（注不出速度 / 时间没走动）一律不算够门。
     * 反例：把门做成 px/s ⇒ 换密度时这一档在真机与测试里判的是两条线；
     * 反例：`velocity == 0f` 当成"无穷快"或"够门" ⇒ 中间那句红（仪器侧本来就读不到速度）。
     */
    @Test
    fun `甩速门是 400dp 每秒，读数缺失就当没够`() {
        assertEquals(400f, PageSwipe.FLING_VELOCITY_DP_PER_S, 0f)
        assertTrue("压线即过", PageSwipe.committedByFling(-400f))
        assertTrue("两个方向同一道门", PageSwipe.committedByFling(400f))
        assertFalse("差一点点不算甩", PageSwipe.committedByFling(-399.99f))
        assertFalse("0 读数不算够门", PageSwipe.committedByFling(0f))
    }

    /**
     * **或关系**整张真值表：两轴各自单独够、两轴都不够、以及"位移不够但甩速够"那一格
     * ——最后一格就是本仓库记过的那个坑（只写位移会把快扫一小段判成不翻页，用户读到的
     * 是"我明明甩了一下它又弹回去"）。
     * 反例：把速度项做成 `&&`（既要过线又要够门）⇒ 第 4 句红（快扫又换不了页）；
     * 反例：速度项整条删掉 ⇒ 第 4 句同样红；
     * 反例：位移项删掉 ⇒ 第 2 句红（慢滑到底也不换了）。
     */
    @Test
    fun `位移或甩速任一够门就翻页`() {
        // 1) 两轴都不够：慢擦一小段 ⇒ 不翻
        assertFalse("都不够门不许翻", PageSwipe.shouldCommit(-50f, 1000f, -100f))
        // 2) 只有位移够：慢滑到底（末段几乎没速度）⇒ 翻
        assertTrue("慢滑够行程就该翻", PageSwipe.shouldCommit(-300f, 1000f, 0f))
        // 3) 只有位移够（反向那一侧同样）
        assertTrue("反向同一条线", PageSwipe.shouldCommit(300f, 1000f, 0f))
        // 4) 只有甩速够：**这一格就是"快扫一小段"**
        assertTrue(
            "行程 80px（页宽 1080 ⇒ 提交线 270px）不够线，但甩速过了 400dp/s 就该翻",
            PageSwipe.shouldCommit(-80f, 1080f, -1666f)
        )
        // 5) 页宽没量到 + 没有速度读数：两轴都关着
        assertFalse("页宽未量到且不甩不许翻", PageSwipe.shouldCommit(-500f, 0f, 0f))
        // 6) 页宽没量到但真甩了一下：速度那一轴独立成立（它不认页宽）
        assertTrue("甩速那一轴不依赖页宽读数", PageSwipe.shouldCommit(-20f, 0f, -900f))
    }

    /**
     * 该往哪一页：**位移过线时位移说了算**（手指最终停在哪儿就跟哪儿），只有甩速单独够门时
     * 才用速度那一项的方向。两端没有第三页——两轴都不许虚拟出第三页。
     * 反例：方向一律取速度 ⇒ 第 1 句红（拖到左边、末尾手指抖一下就跑反方向）；
     * 反例：方向一律取位移 ⇒ 第 2 句红（快扫那一记行程≈0，谁都指不出来）；
     * 反例：界外做回绕 ⇒ 后两句红。
     */
    @Test
    fun `翻页方向与落点：位移优先指方向，两端没有第三页`() {
        assertEquals(
            "行程已过线、末段速度反向：仍按行程那一侧走",
            1, PageSwipe.commitTarget(0, -300f, 1000f, PANEL_PAGE_COUNT, 500f)
        )
        assertEquals(
            "纯甩速够门：按速度方向指页",
            1, PageSwipe.commitTarget(0, -20f, 1000f, PANEL_PAGE_COUNT, -1666f)
        )
        assertEquals(
            "纯甩速反向：谈心回回复",
            0, PageSwipe.commitTarget(1, 20f, 1000f, PANEL_PAGE_COUNT, 1666f)
        )
        assertEquals(
            "两轴都不够：留在原页",
            0, PageSwipe.commitTarget(0, -50f, 1000f, PANEL_PAGE_COUNT, -100f)
        )
        assertEquals(
            "最左一页被甩向右：不许回绕出第三页",
            0, PageSwipe.commitTarget(0, 20f, 1000f, PANEL_PAGE_COUNT, 1666f)
        )
        assertEquals(
            "最右一页被甩向左：不许回绕出第三页",
            1, PageSwipe.commitTarget(1, -20f, 1000f, PANEL_PAGE_COUNT, -1666f)
        )
    }

    /**
     * **让位那一档按轴筛**（原始 11 的另一半成因）：`positionChangeConsumed()` 不分轴，
     * 纵向滚动容器消费后同样为 true；照字面"消费即让位"，一记斜着起手再转正的横滑会被
     * 永久判死——用户读到的就是"要滑好几次"。
     * 现在：横向主导 + 子层消费 ⇒ 让位（气泡删除 / 卡条自滚的合同一字不动）；
     * 纵向主导 + 子层消费 ⇒ 只是这一记不是横滑（不接管、也不粘滞放弃整记手势）。
     * 反例：把 `childConsumedPosition` 排回第一位 ⇒ 第 2、3 句红（纵向消费又被当成死刑）；
     * 反例：让位判没了 ⇒ 第 1 句红（一记手势同时删消息又换页）。
     */
    @Test
    fun `只有横向竞争者才让位，纵向消费不判死刑`() {
        assertEquals(
            "横向主导 + 子层消费 = 让位（气泡行横滑删除优先）",
            PageSwipeOutcome.DeferToChild,
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, -300f, 0f, 8f, true)
        )
        assertEquals(
            "斜着起手再转正：这一帧纵向还在赢，消费不算让位、也不算切页",
            PageSwipeOutcome.NotAHorizontalDrag,
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, 30f, 200f, 8f, true)
        )
        assertEquals(
            "纯纵滚 + 子层消费 = 本来就不是横滑",
            PageSwipeOutcome.NotAHorizontalDrag,
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, 1f, 400f, 8f, true)
        )
        assertEquals(
            "横竖一样多：不算横向主导（对角线不许被切成换页）",
            PageSwipeOutcome.NotAHorizontalDrag,
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, -100f, 100f, 8f, false)
        )
        assertEquals(
            "横向主导但目标页不存在：先让位于子层，再谈边界",
            PageSwipeOutcome.DeferToChild,
            PageSwipe.resolve(1, PANEL_PAGE_COUNT, 1000f, -300f, 0f, 8f, true)
        )
        assertEquals(
            "横向主导、没人消费、目标页存在：才归切页，且带的是夹好的一页内位移",
            PageSwipeOutcome.Switching(1, -300f),
            PageSwipe.resolve(0, PANEL_PAGE_COUNT, 1000f, -300f, 0f, 8f, false)
        )
    }

    // ───────────── C. 相机算式（原始 20 的那一半：页号画不出差别） ─────────────

    /**
     * **跟手恒等式**：拖了 d 像素之后，当前页的落点必须正好是 d（这是"拖动要跟手"的代数定义，
     * 与注不注入无关）。反例：算式里少一个负号 ⇒ 第一句红（手指向左、页面往右跑）；
     * 反例：把 px 当 dp 用（少除页宽）⇒ 第二句红（拖 90px 与拖 180px 画得一样）。
     */
    @Test
    fun `跟手恒等式：拖多少像素，当前页就走多少像素`() {
        val w = 360f
        listOf(-360f, -160f, -1f, 0f, 1f, 120f, 360f).forEach { drag ->
            val camera = PanelPageMotion.cameraForDrag(PanelPageMotion.settledCamera(0), drag, w)
            assertEquals(
                "拖 $drag px 时第 0 页的落点该等于 $drag",
                drag.toDouble(),
                PanelPageMotion.translationFor(0, camera, w).toDouble(),
                0.01
            )
        }
        assertEquals(
            "向左拖 160px：相机往 +页方向走 160/360 页",
            0.4444, PanelPageMotion.cameraForDrag(0f, -160f, 360f).toDouble(), 0.001
        )
        assertEquals(
            "页宽还没量到：相机一分都不挪（不许凭空把两页拉开）",
            0.0, PanelPageMotion.cameraForDrag(0f, -160f, 0f).toDouble(), 0.0
        )
    }

    /**
     * **两页永远首尾相接**（= 画面上永远不会出现"谁都不盖视口"的那一帧，也就是候选 B 那种真空白）：
     * 前一页的右沿恒等于后一页的左沿；稳态（相机停在整数页）时该页正好落满 0。
     * 反例：落点算式里再把 `currentPage` 加进去（闪烁那一形）⇒ 稳态那两句红
     *   （owner 一翻，落点就跟着跳，而位移那一路还在下一帧）；
     * 反例：用"位移 += 页宽减法"的加法补偿 ⇒ 中途取样的相接关系红。
     */
    @Test
    fun `相机在路上时两页首尾相接，到了以后正好落满`() {
        val w = 1000f
        listOf(0f, 0.001f, 0.25f, 0.5f, 0.9f, 1f).forEach { camera ->
            val left0 = PanelPageMotion.translationFor(0, camera, w)
            val right0 = left0 + w
            val left1 = PanelPageMotion.translationFor(1, camera, w)
            assertEquals(
                "相机 $camera 时两页之间不许有空档",
                left1.toDouble(), right0.toDouble(), 0.01
            )
        }
        assertEquals("稳态在第 1 页：那一页正好落满 0", 0.0,
            PanelPageMotion.translationFor(1, PanelPageMotion.settledCamera(1), w).toDouble(), 0.0)
        assertEquals("稳态在第 1 页：第 0 页在左外一整页", -w.toDouble(),
            PanelPageMotion.translationFor(0, PanelPageMotion.settledCamera(1), w).toDouble(), 0.0)
        assertEquals(
            "换页号这件事本身画不出差别：相机仍是 0 时，第 1 页还在右外一整页",
            w.toDouble(),
            PanelPageMotion.translationFor(1, PanelPageMotion.settledCamera(0), w).toDouble(),
            0.0
        )
    }

    /**
     * 页宽还没量到（冷启动首帧）那一帧的可见度：非当前页 0、当前页 1；量到之后一律 1。
     * 反例：把这一档写成"两页都 0" ⇒ 空白那一格红；反例：写成"两页都 1" ⇒ 叠图那一格红
     * （两页都乘 0 落在同一格，读起来就是"闪一下"）。
     */
    @Test
    fun `页宽没量到的那一帧只画当前页`() {
        assertEquals("量不到页宽时另一页不许叠在同一格", 0f, PanelPageMotion.offMeasureAlpha(1, 0, 0f), 0f)
        assertEquals("当前页永远看得见", 1f, PanelPageMotion.offMeasureAlpha(0, 0, 0f), 0f)
        assertEquals("量到页宽后两页都正常", 1f, PanelPageMotion.offMeasureAlpha(1, 0, 360f), 0f)
    }

    // ─────────────── D. 甩速估计器（速度那一轴的证人） ───────────────

    /** 样本不足、时间没走动、时间倒退三种坏读数一律回 0——**不凭空够速度门**。 */
    @Test
    fun `速度读数拿不到时一律为 0`() {
        val onlyOne = HorizontalVelocityEstimator()
        onlyOne.addSample(100L, -40f)
        assertEquals("单样本算不出速度", 0f, onlyOne.velocityPxPerS(), 0f)
        assertEquals("确实收了 1 个样本", 1, onlyOne.sampleCount())

        val sameTime = HorizontalVelocityEstimator()
        sameTime.addSample(100L, 0f)
        sameTime.addSample(100L, -50f)
        assertEquals("时间没走动是坏读数，不许当无穷快", 0f, sameTime.velocityPxPerS(), 0f)

        val backwards = HorizontalVelocityEstimator()
        backwards.addSample(200L, 0f)
        backwards.addSample(190L, -50f)   // 时间倒退：整段清空重开
        assertEquals("倒退之后旧样本全废", 1, backwards.sampleCount())
        assertEquals("只剩一个新样本：回 0", 0f, backwards.velocityPxPerS(), 0f)

        val empty = HorizontalVelocityEstimator()
        assertEquals("一个样本都没有", 0f, empty.velocityPxPerS(), 0f)
        empty.addSample(10L, 5f)
        empty.clear()
        assertEquals("clear 之后归零", 0, empty.sampleCount())
    }

    /**
     * 窗内平均（80ms）：越窗的样本不参与计算；这就是"单帧差被一次抖动放大成假甩"的解药。
     * 数值全是手算的：t=0 → x=0；t=200 → x=-50；t=240 → x=-60 ⇒ 窗内只剩后两格 ⇒ -10px/40ms = -250px/s。
     */
    @Test
    fun `速度只取最后一个采样往前八十毫秒那一窗`() {
        val tracker = HorizontalVelocityEstimator(windowMillis = 80L)
        tracker.addSample(0L, 0f)
        tracker.addSample(200L, -50f)
        tracker.addSample(240L, -60f)
        assertEquals(-250f, tracker.velocityPxPerS(), 0.01f)

        val whole = HorizontalVelocityEstimator(windowMillis = 80L)
        whole.addSample(0L, 0f)
        whole.addSample(60L, -120f)
        assertEquals("60ms 走 -120px = -2000px/s", -2000f, whole.velocityPxPerS(), 0.01f)

        // 窗口**边界本身**（`> windowMillis` 才丢 ⇒ 恰好 80ms 那一格还在窗内）：
        // 反例：把判据写成 `>=` ⇒ 恰好那一格红（一次抖动被当成整窗速度的那族坏法）；
        // 反例：越窗的那格留在窗内 ⇒ 下面第二句红（旧样本污染末段读数，慢拖也能凑出"甩"）。
        val edge = HorizontalVelocityEstimator(windowMillis = 80L)
        edge.addSample(0L, 0f)
        edge.addSample(80L, -80f)
        assertEquals("恰好 80ms 不算越窗：整段在窗内 = -1000px/s", -1000f, edge.velocityPxPerS(), 0.01f)

        val outside = HorizontalVelocityEstimator(windowMillis = 80L)
        outside.addSample(0L, 0f)
        outside.addSample(81L, -81f)
        assertEquals(
            "越窗的那格被丢之后只剩它自己：时间没走动 ⇒ 回 0（不凭空够速度门）",
            0f, outside.velocityPxPerS(), 0f
        )
    }

    /**
     * 环形缓冲：满 16 格丢最老的一格（每帧成本是常数），所以"一路拖很久"不会被早期的样本污染。
     * 反例：写成"满了就丢最新"⇒ 末段那一格红（越拖越慢的读数丢掉最后一步，正主就是它）。
     */
    @Test
    fun `样本缓冲满十六格就丢最老那一格`() {
        val tracker = HorizontalVelocityEstimator(windowMillis = 10_000L)
        for (i in 0 until 20) tracker.addSample(i.toLong(), -i.toFloat())
        assertEquals("只留 16 格", 16, tracker.sampleCount())
        // 留下的是 t=4..19、x=-4..-19 ⇒ dx=-15 / dt=15ms = -1000px/s
        assertEquals(-1000f, tracker.velocityPxPerS(), 0.01f)
    }

    /**
     * **两格合起来**才算买到的那件事：估计器吐出的 px/s 经密度换算后打进 400dp/s 那道门，
     * 落点判据跟着翻页。这就是"手指只走了一小段（150px，页宽 1080 ⇒ 提交线 270px）但甩了一下，
     * 也该换页"的算式；注入手势那一侧读不到可信速度，所以这一格在纯函数侧钉。
     * 反例：换算写成乘密度（而不是除）⇒ 高密度机上速度读数被放大数倍，"慢慢推"那一句红
     *   （轻碰一下就算甩，误切）；
     * 反例：门停在 px/s ⇒ mdpi 与 xxhdpi 判的不是同一条线；
     * 反例：估计算法换成"最后一帧减前一帧"⇒ 上面那两格的红（一次抖动就被放大成假甩）。
     */
    @Test
    fun `估计器读数换算成 dp 每秒后打进速度门`() {
        val tracker = HorizontalVelocityEstimator(windowMillis = 80L)
        // 一记 40ms 走完 -150px 的快扫（xxhdpi：density = 3）
        tracker.addSample(1_000L, 0f)
        tracker.addSample(1_040L, -150f)
        assertEquals("窗内平均：-150px / 40ms", -3750f, tracker.velocityPxPerS(), 1f)
        val density = 3f
        val dpPerS = tracker.velocityPxPerS() / density      // -3750px/s ⇒ -1250dp/s
        assertTrue("过门（这一轴单独就够翻页）", PageSwipe.committedByFling(dpPerS))
        assertEquals(
            "行程只有 150px（这条线在 270px），但甩速过了门 ⇒ 该翻",
            1, PageSwipe.commitTarget(0, -150f, 1080f, PANEL_PAGE_COUNT, dpPerS)
        )
        assertEquals(
            "同样一记行程、末段几乎没速度（慢慢推）：两轴都不够 ⇒ 不翻",
            0, PageSwipe.commitTarget(0, -150f, 1080f, PANEL_PAGE_COUNT, -100f)
        )
        // 同一记手势换到 mdpi：px/s 不变但 dp/s 更大 ⇒ 仍然过门（判据认的是 dp/s）
        assertTrue(PageSwipe.committedByFling(tracker.velocityPxPerS() / 1f))
    }

    /**
     * 基线定的那几颗数（0.25 / 400dp/s / 200ms / 260ms）钉一次，顺便钉**单位的归属**：
     * 密度换算只许住在手势那一侧（`runPageSwipeGesture` 自己就是 `Density`），
     * 判据那一侧（`PageSwipe`）一个字都不许认密度——否则 400dp/s 在 mdpi/xxhdpi 上变成三条线。
     * 反例：把换算挪进 `PageSwipe` ⇒ 最后一句红；反例：换算整个删掉（拿 px/s 直接比 dp/s 的门）
     * ⇒ 上一格的红（高密度机上永远过不了门，"快扫那一记"又被弹回去）。
     */
    @Test
    fun `基线定的那几个数与单位的归属`() {
        assertEquals(0.25f, PageSwipe.COMMIT_FRACTION, 0f)
        assertEquals(400f, PageSwipe.FLING_VELOCITY_DP_PER_S, 0f)
        assertEquals(200, PanelPageMotion.SLIDE_MS)
        assertEquals(260L, PanelPageMotion.OWNER_HANDOFF_MS)
        assertTrue(
            "runPageSwipeGesture 必须自己用 Density 把 px/s 换成 dp/s（实到 density 引用 " +
                Regex("density").findAll(motionCode).count() + " 处）",
            Regex("velocityPxPerS\\(\\)\\s*\\*\\s*velocityToDpPerS").containsMatchIn(motionCode) &&
                Regex("1f\\s*/\\s*density").containsMatchIn(motionCode)
        )
        // 取"判据那一段"的窗口：`substringAfter` 找不到分隔符时**整篇原样吐回来**，
        // 于是"窗口里不许有 density"会绿得毫无意义。所以先把窗口的身份钉住（旧写法只量长度>200，
        // 那连"窗口=整颗文件"都判不出来），再谈窗口里不许有什么。
        // 右边界用 `@Composable`：`PageSwipe` 那颗对象之后第一个出现的 @Composable 就是宿主
        // （`PanelPagePager`），再往后才是 `object : PageMotion` 与 `LaunchedEffect`——
        // 这两颗出现在窗口里就说明右边界失效了（别拿 `private fun Modifier.pageSwitchSwipe` 当右界，
        // 它在宿主之后，窗口语义就变成"判据 + 整颗宿主"了）。
        assertTrue("判据那颗对象必须在滑页宿主里（`internal object PageSwipe`）",
            pagerCode.contains("internal object PageSwipe"))
        val pageSwipeBody = pagerCode.substringAfter("internal object PageSwipe")
            .substringBefore("@Composable")
        assertTrue(
            "这一格必须真的只看见判据那一段（尺别是瞎的）：窗口长度 ${pageSwipeBody.length}、" +
                "窗口末尾是 " + pageSwipeBody.takeLast(60).replace(Regex("\\s+"), " "),
            pageSwipeBody.length > 200 &&
                pageSwipeBody.contains("shouldCommit") &&
                pageSwipeBody.contains("FLING_VELOCITY_DP_PER_S") &&
                pageSwipeBody.contains("fun resolve") &&
                !pageSwipeBody.contains("object : PageMotion") &&
                !pageSwipeBody.contains("LaunchedEffect")
        )
        assertFalse(
            "PageSwipe 的判据里不许出现 density（速度门认的是 dp/s，换算归手势侧）",
            Regex("\\bdensity\\b").containsMatchIn(pageSwipeBody)
        )
    }

    // ─────────── E. 落地那一侧的接线（P1d 复核补的三格） ───────────

    /**
     * **松手那一段必须把两轴都交给判据**（原始第 11 条在生产路径上的那一半）。
     *
     * 上面 `位移或甩速任一够门就翻页` 钉的是纯函数；但纯函数对了、**落地只喂行程**同样会
     * 把"快扫一小段"弹回去，而且那种坏法纯函数一格判不到（本仓库记过的正是这一坑：
     * 判据住在生产算式里才算数）。所以这一格数的是提交那一句的**实参**。
     * 反例：把速度实参从 `commitTarget(...)` 里删掉（回到"只看行程"）⇒ 第 3 句红；
     * 反例：把 `PageSwipe.commitTarget` 换成宿主自己 `if (abs(dx) > 一半页宽)` ⇒ 第 1 句红；
     * 反例：行程实参换成"单帧增量"（不是累计）⇒ 第 2 句红。
     */
    @Test
    fun `落地把行程与甩速两轴一起交给判据`() {
        val call = Regex("PageSwipe\\.commitTarget\\(([^)]*)\\)").find(pagerCode)
        assertTrue("滑动的落地必须走 PageSwipe.commitTarget（不许另起一颗判据）", call != null)
        val args = call!!.groupValues[1].replace(Regex("\\s+"), " ")
        assertTrue("必须把**累计行程**交给判据：实到参数（$args）", args.contains("totalDxPx"))
        assertTrue("必须把**页宽**交给判据（提交线是它的比）：实到参数（$args）", args.contains("pageWidthPx"))
        assertTrue(
            "必须把**甩速**交给判据——删掉这一半就是「只写位移」那颗坑回到生产路径：实到参数（$args）",
            args.contains("velocityDxPerDpPerS")
        )
        assertTrue("必须把页数交给判据（两端没有第三页）：实到参数（$args）", args.contains("PageCount"))
        // 手势那一侧交出来的也必须成对：抬指时行程与甩速一起交（只交行程 ⇒ 速度门永远读不到）
        assertTrue(
            "runPageSwipeGesture 抬指时必须把甩速读数交给 onDragSettle",
            Regex("onDragSettle\\([\\s\\S]{0,200}velocityPxPerS\\(\\)").containsMatchIn(motionCode)
        )
    }

    /**
     * **交接闸门：新手势一接手就把上一记的去重账作废**（P1d 复查查出的那一根）。
     *
     * `pendingSwipeTarget` 只为一件事存在：滑动那一支已经把相机动画排上了，owner 回来时
     * `LaunchedEffect` 不许再排第二段（排两次＝"滑完又卷回来半格"）。但那一记排的动画会被
     * **下一记手势的第一笔 `snapTo` 取消**，而 260ms 那颗兜底在"正在拖动"时故意让路
     * （`latestCurrentPage.value == page && !dragActive`）——于是闸门可能带着旧目标活下来，
     * 之后 owner 真把页号投过去时，那一段入场动画会被整段吞掉：画面停在旧页、
     * 页头已经亮新页（本文件头明说写不出来的那本对不上账的形状）。
     * 修法只有一条：**认领**那一刻把闸门归 -1。这一格钉的是这条结构不变量；
     * 触发它需要卡进 260ms 那个竞态窗口，注入手势那一侧给不出可信时序 ⇒ 观感归真机。
     * 反例：删掉 `onDragClaim` 里那句 `pendingSwipeTarget = -1` ⇒ 第 1 句红；
     * 反例：把兜底那颗 `!dragActive` 让路删了（兜底与在飞的手势抢同一颗相机）⇒ 第 2 句红。
     */
    @Test
    fun `交接闸门在认领新手势时作废`() {
        val claimBody = pagerCode.substringAfter("override fun onDragClaim")
            .substringBefore("override fun onDragOffset")
        assertTrue(
            "这一格必须真的只看见认领那一段（尺别是瞎的）：窗口长度 ${claimBody.length}",
            claimBody.length > 60 && claimBody.contains("everShownPages") &&
                !claimBody.contains("override fun onDragSettle")
        )
        assertTrue(
            "认领新一记手势必须把上一记的交接闸门作废（`pendingSwipeTarget = -1`），实到：$claimBody",
            Regex("pendingSwipeTarget\\s*=\\s*-1").containsMatchIn(claimBody)
        )
        assertTrue(
            "owner 交接兜底必须让路给在飞的那一记手势（`!dragActive` 那一半判据不许被删）",
            Regex("latestCurrentPage\\.value\\s*==\\s*page\\s*&&\\s*!dragActive").containsMatchIn(pagerCode)
        )
        // 反向证人：闸门只许在"认领 / 排过渡 / 交接成功 / 兜底弹回"这四笔写里出现，
        // 别处再写一笔就说明它被当成开关在用了（`(?!=)` 是把 `== currentPage` 那一笔**读**排除掉）
        val writeCount = Regex("pendingSwipeTarget\\s*=(?!=)").findAll(pagerCode).count()
        assertTrue(
            "pendingSwipeTarget 的写点只许四笔（认领作废 / 排过渡时置目标 / 交接成功清 / 兜底弹回清），" +
                "实到 $writeCount 笔：" +
                Regex("pendingSwipeTarget\\s*=(?!=)[^\\n]*").findAll(pagerCode).joinToString(" | ") { it.value },
            writeCount == 4
        )
    }

    /**
     * **`Animatable` 那一面账的写入口只有一条**（P1d 用 javap 量过 animation-core 1.6.8：
     * 只有 `getValue()`、没有 `setValue`，`snapTo(T, Continuation)` 是挂起的）。
     *
     * 这一格买的是两次真撞过的编译错，两个方向各钉一半：
     * · 接口那颗被标回 `suspend` ⇒ 在 `awaitEachGesture { … }`（`PointerInputScope` 带
     *   `@RestrictsSuspension`）里调它就报 `Restricted suspending functions can only invoke
     *   member or extension suspending functions`；
     * · 实现那颗改写成 `camera.value = …` ⇒ `Val cannot be reassigned`。
     * 反例：把 `onDragOffset` 标回 suspend ⇒ 第 1 句红；
     * 反例：跟手那一笔不走 `scope.launch { camera.snapTo(…) }`（改成直接赋值 / 改成空实现）⇒ 第 2、3 句红。
     */
    @Test
    fun `跟手那一笔的写法只有非挂起接口加协程里的 snapTo 这一条`() {
        val decl = Regex("(suspend\\s+)?fun\\s+onDragOffset").find(motionCode)
        assertTrue("接口必须声明 onDragOffset（尺别是瞎的）", decl != null)
        val declared = decl!!
        assertEquals(
            "接口那颗不许是 suspend（`awaitEachGesture` 是带限制的挂起作用域），实到：" + declared.value,
            "",
            declared.groups[1]?.value ?: ""
        )
        assertFalse(
            "Animatable 在 1.6.8 只有 getValue（javap 实测），`camera.value = …` 编译不过",
            Regex("camera\\.value\\s*=(?!=)").containsMatchIn(pagerCode)
        )
        assertTrue(
            "跟手那一笔必须写成 `scope.launch { camera.snapTo(…) }`" +
                "（rememberCoroutineScope 是 Main.immediate，就地开跑、不迟到一帧），实到：" +
                Regex("scope\\.launch").findAll(pagerCode).count() + " 处 launch",
            Regex("scope\\.launch\\s*\\{\\s*camera\\.snapTo\\(").containsMatchIn(pagerCode)
        )
    }
}
