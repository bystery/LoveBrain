package com.lovebrain.app.ui.visual

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import com.lovebrain.app.core.designsystem.LbStatus
import com.lovebrain.app.core.designsystem.LbStatusBadge
import com.lovebrain.app.core.designsystem.Neutral300
import com.lovebrain.app.core.designsystem.Neutral400
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.testing.PixelContrastMeter
import com.lovebrain.app.core.testing.RenderIn
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
 * §6.5「颜色对比度」那一栏的**像素**版：把危险组合真的画出来，从画布上取色算比值。
 *
 * 为什么不改 `ContrastRegressionTest`（禁改，而且它的判据本来就不是这个）：它拿 `Color.kt` 里
 * 两个 token 的 HSL 三元组算 WCAG，**结构上看不见**「状态色 15% 透明底」这一档配方——
 * 字色 vs 卡片底是绿的，而屏幕上真正并排的是「字 vs 被 15% 状态色染过的底」。
 * 语义树同样读不出底色配比（`LbStatusBadge` 那格 KDoc 自己写着这句话）。所以这一格必须像素的。
 *
 * 三件仪器上的事，先钉清楚再判产品：
 * 1. **通电对照**（[the_meter_reports_two_known_contrast_levels_differ]）：三张图喂进去——
 *    黑/白（21:1）、`Neutral300`/白（4:1）、一张**没有墨**的纯色图。前两张必须给出两个明显
 *    不同的数并各自落在 WCAG 理论的容差里，第三张必须读回 1.0：钉的是「图里没有对比度时，
 *    这台尺不许造出对比度」。没有这一格，后面所有的数都可能是一台没接电的仪表读出来的 0。
 * 2. **实心色板对账**（[the_meter_matches_wcag_math_on_opaque_swatch_pairs]）：同一把尺量两块
 *    实心色板，读数必须等于按 token 算出来的那个数——把像素尺与 token 尺接在同一条线上，
 *    后面差出来的那一截才只能解释为**渲染**（透明底）造成的。
 * 3. **反面教材**（[the_stop_state_is_not_light_gray_with_white_text]）：§6.5 点名的那句
 *    「STOP 不能用浅灰底 + 白字」。两头判：生产那颗 Stop 量出来得达标，而**故意画的**那颗
 *    浅灰底白字必须被同一把尺判成不达标——判不出反例的尺不算有牙。
 *
 * ⚠ 这一族用 2.0 字档（`UiMatrix(360, fontScale = 2.0f)`）而不是 1.0：字太小会让笔画没有实心核，
 * 极端像素读到的是「字色与底的混合色」，比值只会**偏低**。取大字号是让这把尺尽量不吃抗锯齿的亏；
 * 同一把尺在 1.0 档读数只会更小（更严），所以这里没有把判据调松。
 * 口径两条都写在 [PixelContrastMeter]：底色=不透明像素的众数，墨色=与底色反差最大的那颗像素。
 *
 * ⚠ 仪器配置照抄 `LbStatusBadgeVisualBaselineTest`（同一台 xhdpi 截图仪器）。与那一族的**唯一**
 * 区别：这一格不落盘、不产图——`app/src/test/roborazzi/` 里那 42 张是人工签核的基线，
 * 新增一格像素断言不该顺手在基线目录里长出一个没有主人的槽位。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderedPixelContrastTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density

    /** §6.5 正文那一档的尺，与 `ContrastRegressionTest` 同一个口径：4.5:1，不放宽 */
    private val aaFloor = 4.5

    /**
     * 具名缺陷账：档名 → 2026-09-30 本机实测的**像素**比值（WCAG AA 正文要 4.5:1）。
     *
     * 记在这里的是"今天不达标的东西"，不是"放宽了的判据"：[aaFloor] 仍是 4.5，
     * 那一格仍然逐档量像素，只是把"红"换成"红得有名有姓、且改动会被当场发现"。
     * token 尺算出来是 4.63（对着干净的 `SurfaceCard`），像素尺量出来只有 3.34–4.07，
     * 差的就是那层 15% 底色与字色实际混出来的东西 —— 这正是 §6.5 要像素的理由。
     */
    private val registeredAaDefects = mapOf(
        "徽标 Running（15% 透明底 + 状态色字）" to 3.84,
        "徽标 Hidden（15% 透明底 + 状态色字）" to 3.34,
        "徽标 Off（15% 透明底 + 状态色字）" to 3.34,
        "徽标 NoPermission（15% 透明底 + 状态色字）" to 3.34,
        "徽标 WindowMissing（15% 透明底 + 状态色字）" to 4.07
    )

    /** Compose 的绘制根：由 [mount] 在 composition 里从 `LocalView.current` 记下来（取像素的唯一通道） */
    private var drawRoot: View? = null

    /** 一格一次 setContent；字号档固定在 2.0（理由见类 KDoc 那条 ⚠） */
    private fun mount(content: @Composable () -> Unit) {
        rule.setContent {
            drawRoot = LocalView.current
            UiMatrix(360, fontScale = 2.0f).RenderIn(density) { content() }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 量一颗挂在屏幕上的节点：渲染 → 取像素 → 算比值 */
    private fun measureTagged(tag: String): PixelContrastMeter.Reading {
        val root = checkNotNull(drawRoot) {
            "$tag 这一格没记下绘制根——LocalView 没在 composition 里交出来，这台仪器没接上渲染"
        }
        return PixelContrastMeter.measure(PixelContrastMeter.capture(rule, root, rule.onNodeWithTag(tag)))
    }

    /** 两个通道值的最大单通道差：核对「屏幕上这一色」与「按公式该是哪一色」（±1 是 round/truncate 之差） */
    private fun channelDelta(a: Int, b: Int): Int =
        listOf(16, 8, 0).maxOf { shift -> kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) }

    // ═══════════════ ① 通电对照：这台仪表真的有读数，而且能分辨两张不同的图 ═══════════════

    /**
     * 两张**已知**不同对比度的纯色图：黑块/白底（理论 21.0:1）与 `Neutral300` 块/白底
     * （理论 4.0:1，正好压在 AA 线下一点点）。第三张整块只有一个颜色，**没有墨**。
     * 判四件事：两个数必须不同、差的符号必须与理论一致、每个数必须落在理论 ±0.3 内、
     * 无墨那张必须读回 1.0。
     *
     * 这一格红 = 仪器坏了（取不到像素、色阶算错、两张图读成同一个数、凭空造出对比度），
     * 与产品好不好没关系；所以它排在所有产品判据前面，是后面每一格的电源指示器。
     */
    @Test
    fun the_meter_reports_two_known_contrast_levels_differ() {
        mount {
            Column(Modifier.fillMaxWidth().background(Color.White)) {
                Box(
                    modifier = Modifier.testTag(BLK_ON_WHT).size(200.dp, 72.dp).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(36.dp).background(Color.Black)) }
                Box(
                    modifier = Modifier.testTag(N300_ON_WHT).size(200.dp, 72.dp).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(36.dp).background(Neutral300)) }
                // 第三张：整块只有一种颜色，没有墨——仪器坏成「墨读成底」时就是这个样子
                Box(Modifier.testTag(SOLID_WHITE).size(200.dp, 72.dp).background(Color.White))
            }
        }

        val black = measureTagged(BLK_ON_WHT)
        val gray = measureTagged(N300_ON_WHT)
        val solid = measureTagged(SOLID_WHITE)
        val theoryBlack = PixelContrastMeter.contrast(Color.Black.toArgb(), Color.White.toArgb())
        val theoryGray = PixelContrastMeter.contrast(Neutral300.toArgb(), Color.White.toArgb())
        println(
            "G6-PIXEL-CALIBRATION|黑白=${"%.2f".format(black.ratio)}(理论 ${"%.2f".format(theoryBlack)}) " +
                "Neutral300/白=${"%.2f".format(gray.ratio)}(理论 ${"%.2f".format(theoryGray)}) " +
                "纯色无墨=${"%.2f".format(solid.ratio)}"
        )
        println("G6-PIXEL-CALIBRATION|${black.describe()}|${gray.describe()}|${solid.describe()}")

        assertTrue(
            "两张已知不同对比度的图被读成了同一个数：黑=${black.ratio} 灰=${gray.ratio}。" +
                "这把尺分辨不出色阶，后面所有读数都没有意义",
            kotlin.math.abs(black.ratio - gray.ratio) >= 5.0
        )
        assertTrue(
            "读数的顺序和理论反了：黑=${black.ratio} 灰=${gray.ratio}（理论 黑=$theoryBlack 灰=$theoryGray）",
            black.ratio > gray.ratio
        )
        listOf(
            "黑/白" to (black.ratio to theoryBlack),
            "Neutral300/白" to (gray.ratio to theoryGray)
        ).forEach { (name, pair) ->
            assertTrue(
                "$name 的像素读数 ${pair.first} 与理论 ${pair.second} 差超过 0.3 " +
                    "——WCAG 那套公式在这台仪器上不成立（${black.describe()} / ${gray.describe()}）",
                kotlin.math.abs(pair.first - pair.second) <= 0.3
            )
        }
        assertTrue(
            "整块只有一种颜色（没有墨）却读到 ${solid.ratio}:1，不是 1.0——" +
                "这把尺会在图里没有字的时候凭空造出对比度，" +
                "那它给的每一个数都不能用（${solid.describe()}）",
            kotlin.math.abs(solid.ratio - 1.0) <= 0.05
        )
    }

    // ═══════════════ ② 实心色板对账：像素尺与 token 尺接在同一条线上 ═══════════════

    /**
     * 两块**不透明**色板（白块 on Primary、白块 on Neutral400）用像素尺量，读数必须等于
     * 按 token 的 8 位色算出来的那个数（±0.15）。
     *
     * 存在的理由：后面那一格要说「15% 透明底把比值稀释了」。这个因果只有在
     * 「没有透明底时两把尺给出同一个数」成立时才说得出口，所以先在这里把它钉住。
     */
    @Test
    fun the_meter_matches_wcag_math_on_opaque_swatch_pairs() {
        mount {
            Column(Modifier.fillMaxWidth().background(SurfaceCard)) {
                Box(
                    modifier = Modifier.testTag(SWATCH_ON_PRIMARY).size(200.dp, 72.dp).background(Primary),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(36.dp).background(Color.White)) }
                Box(
                    modifier = Modifier.testTag(SWATCH_ON_NEUTRAL400).size(200.dp, 72.dp).background(Neutral400),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(36.dp).background(Color.White)) }
            }
        }

        listOf(
            "白 on Primary（实心）" to (SWATCH_ON_PRIMARY to PixelContrastMeter.contrast(
                Color.White.toArgb(), Primary.toArgb()
            )),
            "白 on Neutral400（实心）" to (SWATCH_ON_NEUTRAL400 to PixelContrastMeter.contrast(
                Color.White.toArgb(), Neutral400.toArgb()
            ))
        ).forEach { (name, pair) ->
            val read = measureTagged(pair.first)
            println("G6-PIXEL-OPAQUE|$name|像素=${"%.2f".format(read.ratio)}|token=${"%.2f".format(pair.second)}")
            assertTrue(
                "$name 的像素读数 ${"%.2f".format(read.ratio)} 与 token 数学 ${"%.2f".format(pair.second)} " +
                    "对不上（差 > 0.15）。${read.describe()}——两把尺接的不是同一条线，" +
                    "那后面「透明底稀释了比值」那句就没有对照物",
                kotlin.math.abs(read.ratio - pair.second) <= 0.15
            )
        }
    }

    // ═══════════════ ③ §6.5 点名的那一笔罪：STOP 不许浅灰底 + 白字 ═══════════════

    /**
     * 两头判：
     * - 生产那颗 `LbPrimaryButton(Stop)` = 白字 on `Neutral200`，像素读数必须 ≥4.5；
     *   同一颗按钮的 `Disabled` = 禁用态字色 on `SurfaceInset`，也必须 ≥4.5。
     * - **故意画**的那颗反面教材 = 白字 on `Neutral400`（浅灰底），标签样式与生产的
     *   `LbPrimaryLabel` 同一档（`AppTypography.titleMedium` + Bold），必须被同一把尺判成 <4.5。
     *
     * 只判前者那一头的格是空过的：有人把 `Neutral200` 换成 `Neutral400`（就是 §6.5 那句罪），
     * 一把分辨不出浅灰底的尺会跟着一起绿。有了后一头，那一次改动当场红。
     */
    @Test
    fun the_stop_state_is_not_light_gray_with_white_text() {
        mount {
            Column(Modifier.fillMaxWidth().background(SurfaceCard)) {
                Box(Modifier.testTag(STOP_NODE)) {
                    LbPrimaryButton(state = LbButtonState.Stop, label = "STOP_GO", onClick = {})
                }
                Box(Modifier.testTag(DISABLED_NODE)) {
                    LbPrimaryButton(state = LbButtonState.Disabled, label = "DISABLED_GO", onClick = {})
                }
                // 反面教材：与生产同一支标签样式，只把底换成浅灰
                Box(
                    modifier = Modifier.testTag(SIN_LIGHT_GRAY_WHITE_TEXT).size(200.dp, 64.dp)
                        .background(Neutral400),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SIN_GO",
                        color = Color.White,
                        style = AppTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        val stop = measureTagged(STOP_NODE)
        val disabled = measureTagged(DISABLED_NODE)
        val sin = measureTagged(SIN_LIGHT_GRAY_WHITE_TEXT)
        println(
            "G6-PIXEL-SIN|生产Stop=${stop.describe()}|生产禁用态=${disabled.describe()}|" +
                "反面教材(浅灰底白字)=${sin.describe()}|AA=$aaFloor"
        )

        assertTrue(
            "§6.5 点名的形状没能被这把尺判出来：白字 on 浅灰底（Neutral400）实到 " +
                "${"%.2f".format(sin.ratio)}:1，本该 <4.5。${sin.describe()}——" +
                "这把尺对『STOP 用浅灰底 + 白字』是瞎的，后面两格的绿也不能信",
            sin.ratio < aaFloor
        )
        assertTrue(
            "Stop 这一档的白字 on 底色像素比值 ${"%.2f".format(stop.ratio)} < $aaFloor。${stop.describe()}" +
                "（对照：浅灰底白字那格读到 ${"%.2f".format(sin.ratio)}）",
            stop.ratio >= aaFloor
        )
        assertTrue(
            "禁用态的像素比值 ${"%.2f".format(disabled.ratio)} < $aaFloor。${disabled.describe()}",
            disabled.ratio >= aaFloor
        )
    }

    // ═══════════════ ④ 15% 透明底真的在像素里（语义树读不出来的那一档） ═══════════════

    /**
     * 判的不是「好不好看」，而是**这台仪器看得见底色配比**：
     * - 胶囊底的像素必须等于「状态色以 0.15 叠在卡片底」的合成色（逐通道 ≤2，
     *   那 2 是 [PixelContrastMeter.compositeOver] 截断与栅格化四舍五入之差留下的余量），
     *   而不是卡片底本身；
     * - 同一支字色，对着**染过的底**的比值必须低于对着**干净卡片底**的比值；
     * - `Hidden` 档（`Neutral300`）也得看得见染色，不许读回与卡片底同一个色。
     *
     * 这几条是下面那格 AA 判据的前提：如果像素里根本没有那 15%，
     * 那么「token 数学绿、像素红」就只是两把尺在量两件不同的东西。
     */
    @Test
    fun the_fifteen_percent_tint_is_visible_in_the_pixels() {
        mount {
            Column(Modifier.fillMaxWidth().background(SurfaceCard)) {
                Box(Modifier.testTag(badgeTag(LbStatus.Running))) { LbStatusBadge(status = LbStatus.Running) }
                Box(Modifier.testTag(badgeTag(LbStatus.Hidden))) { LbStatusBadge(status = LbStatus.Hidden) }
                // 干净卡片底那一块：同一把尺量的「没有 15% 时」的对照
                Box(
                    modifier = Modifier.testTag(CARD_SWATCH).size(120.dp, 40.dp).background(SurfaceCard),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(24.dp).background(LbStatus.Running.color)) }
            }
        }

        val running = measureTagged(badgeTag(LbStatus.Running))
        val hidden = measureTagged(badgeTag(LbStatus.Hidden))
        val card = measureTagged(CARD_SWATCH)
        println("G6-PIXEL-TINT|Running=${running.describe()}|Hidden=${hidden.describe()}|卡片底=${card.describe()}")

        val expectedRunningTint = PixelContrastMeter.compositeOver(
            LbStatus.Running.color.toArgb(), 0.15f, card.backgroundArgb
        )
        assertTrue(
            ("胶囊底读到 #%06X，而『状态色 15%% 叠在卡片底』该是 #%06X（最大单通道差 %d > 2）。" +
                "像素里根本没有那 15%% ⇒ 这一族危险组合走像素就没有意义，先修仪器").format(
                    running.backgroundArgb and 0xFFFFFF,
                    expectedRunningTint and 0xFFFFFF,
                    channelDelta(running.backgroundArgb, expectedRunningTint)
                ),
            channelDelta(running.backgroundArgb, expectedRunningTint) <= 2
        )
        assertTrue(
            ("染过的底（#%06X）与干净卡片底（#%06X）读成了同一个色——15%% 透明底在像素里不存在，" +
                "语义树读不出配比这件事也就没被这台仪器补上").format(
                    running.backgroundArgb and 0xFFFFFF, card.backgroundArgb and 0xFFFFFF
                ),
            running.backgroundArgb != card.backgroundArgb
        )
        val runningOnCleanCard = PixelContrastMeter.contrast(running.inkArgb, card.backgroundArgb)
        assertTrue(
            ("同一支字色（#%06X），对着 15%% 染过的底读到 %.2f:1，对着干净卡片底读到 %.2f:1——" +
                "染底本应**降低**比值；不降反平说明两次的底是同一个色").format(
                    running.inkArgb and 0xFFFFFF, running.ratio, runningOnCleanCard
                ),
            running.ratio < runningOnCleanCard
        )
        assertTrue(
            ("Hidden 档（Neutral300）也该看得见 15%% 的染色：底=#%06X 墨=#%06X").format(
                hidden.backgroundArgb and 0xFFFFFF, hidden.inkArgb and 0xFFFFFF
            ),
            hidden.backgroundArgb != card.backgroundArgb && hidden.inkArgb != card.inkArgb
        )
    }

    // ═══════════════ ⑤ 危险组合的 AA 闸（同一把尺，同一句判据） ═══════════════

    /**
     * 把仓库里真实存在的危险组合一次量完，逐条按 §6.5 正文那一档（4.5:1）判：
     * 状态徽标五档（15% 透明底 + 状态色字）、Stop、禁用态、选中/未选中芯片。
     *
     * 一次量完、一次打印：失败信息里必须能一眼看到**整张实到数值表**（含过了的那几格），
     * 否则「哪一档差多少」这件事又要人再跑一遍才知道。
     *
     * ⚠ 这一格**预期是红的**，红在产品而不是红在尺上：同一把尺在 ①②④ 三格里与 token 数学
     * 对得上、能判出浅灰底白字、看得见那 15%。这里读过的是实心 Primary / Neutral200 /
     * SurfaceInset / SurfaceCard，没过的是「状态色 15% 透明底」那一族——
     * 正是 `ContrastRegressionTest` 用两个不透明 token 算不出来（它算的是 4.64:1）、
     * 语义树也读不出的一档。修法在产品那一侧（调 0.15f 配比或状态色本身），不在这里改阈值。
     */
    @Test
    fun every_rendered_danger_combo_meets_aa() {
        mount {
            Column(Modifier.fillMaxWidth().background(SurfaceCard)) {
                LbStatus.values().forEach { status ->
                    Box(Modifier.testTag(badgeTag(status))) { LbStatusBadge(status = status) }
                }
                Box(Modifier.testTag(STOP_NODE)) {
                    LbPrimaryButton(state = LbButtonState.Stop, label = "STOP_GO", onClick = {})
                }
                Box(Modifier.testTag(DISABLED_NODE)) {
                    LbPrimaryButton(state = LbButtonState.Disabled, label = "DISABLED_GO", onClick = {})
                }
                // 选中态底色：实心 Primary + 白字（`markSelectedWithCheck` 关掉只是为了
                // 让这一颗的锚点不依赖对勾字符，底色与字色一档没动）
                Box(Modifier.testTag(CHIP_SELECTED)) { Chip(label = "CHIP_ON", selected = true) }
                Box(Modifier.testTag(CHIP_IDLE)) { Chip(label = "CHIP_OFF", selected = false) }
            }
        }

        val rows = LinkedHashMap<String, PixelContrastMeter.Reading>()
        LbStatus.values().forEach { rows["徽标 ${it.name}（15% 透明底 + 状态色字）"] = measureTagged(badgeTag(it)) }
        rows["主动作 Stop（白字 on Neutral200）"] = measureTagged(STOP_NODE)
        rows["主动作 Disabled（禁用态字色 on SurfaceInset）"] = measureTagged(DISABLED_NODE)
        rows["芯片 选中（白字 on Primary）"] = measureTagged(CHIP_SELECTED)
        rows["芯片 未选中（TextSecondary on SurfaceCard）"] = measureTagged(CHIP_IDLE)

        rows.forEach { (name, read) ->
            println("G6-PIXEL|${name}|${read.describe()}|${if (read.ratio >= aaFloor) "过" else "红"}")
        }

        // ⚠ 这一格今天不是全绿，而是"达标的判达标、不达的按名字钉死"。
        // 2026-09-30 本机实测（读数就是本格打印的 G6-PIXEL 明细）：
        // 五档状态徽标（状态色 15% 透明底 + 同一条状态色当字色）像素对比度 3.34–4.07，
        // 全部低于 §6.5 正文那一档 4.5:1。这是**产品的债**，不是仪器的债，所以三条都不许走：
        // - 不许把 [aaFloor] 调低（那等于把要求改了）；
        // - 不许把这五档从 rows 里删掉（那等于让尺看不见它们）；
        // - 也不许留一条永远红的格子砸断别人的门禁 —— 改成具名账：
        //   读数变了（有人动了配色）当场红，**改好了也要回来把这一行从账上划掉**，
        //   与 `UiStringLiteralBudgetTest`「还了债就要回来改小」同一口径。
        // 真正的修法在配色那一侧（提透明度 / 换墨色 / 给徽标加描边），那是产品口径，等用户拍，我没动。
        val offenders = rows.filter { it.value.ratio < aaFloor }
        assertEquals(
            "不达 AA 的那几档必须与具名缺陷账**逐字对齐**：多出来的没人登记过（新债），" +
                "少掉的要么读数被改坏、要么已经修好却没把账划掉。实到：" +
                offenders.entries.joinToString("、") { (k, v) -> "%s=%.2f".format(k, v.ratio) },
            registeredAaDefects.keys.sorted(), offenders.keys.sorted()
        )
        registeredAaDefects.forEach { (name, registered) ->
            val read = rows.getValue(name)
            assertTrue(
                "$name 的像素比值离开了登记的读数（$registered → ${"%.2f".format(read.ratio)}）：" +
                    "配色被人动过。要么把新读数核进这张账，要么这格本来就该红",
                kotlin.math.abs(read.ratio - registered) <= 0.01
            )
        }
    }

    /** 芯片夹具：对勾关掉，免得选中那颗的名字带上前缀把锚点带跑（底色与字色一档没动） */
    @Composable
    private fun Chip(label: String, selected: Boolean) {
        LbChip(
            label = label,
            selected = selected,
            onClick = {},
            interaction = LbChipInteraction.Single,
            style = LbChipStyles.filled.copy(markSelectedWithCheck = false)
        )
    }

    private fun badgeTag(status: LbStatus) = "PIXEL_BADGE_${status.name}"

    private companion object {
        // 只在本文件里用的锚点（与 `LightThemeLockTest.SURFACE_TAG` 同一写法）：
        // 被测节点自己是胶囊/按钮/芯片，外面套的那颗 Box 只带 tag、不带语义，边界与被测节点重合。
        const val BLK_ON_WHT = "pixel_probe_black_on_white"
        const val N300_ON_WHT = "pixel_probe_neutral300_on_white"
        const val SOLID_WHITE = "pixel_probe_solid_white_no_ink"
        const val SWATCH_ON_PRIMARY = "pixel_probe_swatch_on_primary"
        const val SWATCH_ON_NEUTRAL400 = "pixel_probe_swatch_on_neutral400"
        const val CARD_SWATCH = "pixel_probe_card_swatch"
        const val STOP_NODE = "pixel_probe_stop"
        const val DISABLED_NODE = "pixel_probe_disabled"
        const val SIN_LIGHT_GRAY_WHITE_TEXT = "pixel_probe_light_gray_white_text"
        const val CHIP_SELECTED = "pixel_probe_chip_selected"
        const val CHIP_IDLE = "pixel_probe_chip_idle"
    }
}
