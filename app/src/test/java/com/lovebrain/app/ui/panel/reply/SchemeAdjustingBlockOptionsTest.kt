package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.RewriteCommand
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 卡片调整区那四项的读数：只有四颗、都点得中、自定义才展开输入、没有常驻取消。
 *
 * 卡片的外框尺寸不归这里改（`SchemeCardDimens` 那一份由方案卡与骨架屏共用），
 * 所以这里判的是两件"实际可达"的事：
 * - 横向：四项排成**两行两列**（一行两颗、各占一半可用宽），四颗挤同一行时每颗只剩 34dp 宽，
 *   再让它们在 142dp 内容宽里各要 48dp 就把正文挤没了。这一格钉"四颗都摆得出来 + 每颗两轴
 *   都到**卡内那一档** + 一行两颗的合计宽放得进卡内容宽"。
 * - 纵向：粒子本身就是热区（视觉与热区同一颗数，见 [SchemeCardDimens.ADJUST_PILL_HEIGHT_DP]），
 *   两行 + 展开的输入行必须落在卡片固定外框给出的可用高度里；放不下就让读数说话，
 *   而不是把卡片撑大。
 *
 * ⚠ 这一族的档**低于全站触控下限**（`AppDimens.TOUCH_TARGET_MIN_DP` 仍写着 48，全站别处照旧）。
 * 这里不空口认这个例外：每次判紧凑档都同时判那条几何理由（见 [assertCompactTierIsTheReasonThisRowFits]），
 * 理由一旦不成立（比如哪天卡内可用高度松出来了）这一格就红，要求重钉，而不是让例外一直留着。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SchemeAdjustingBlockOptionsTest {

    private companion object {
        /** `labelSmall` 现在那一档的行高（14sp）；读数拿不到正值时用它兜底，免得可用高度算成负数 */
        const val DEFAULT_TAG_LINE_HEIGHT_DP = 14f
    }

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val tapped = mutableListOf<RewriteCommand>()
    private val customSent = mutableListOf<String>()

    /** 卡内可用宽度：外框宽 − 左右各一份 `Spacing.md` 内边距（与 SchemeCard 同一份） */
    private val innerWidthDp: Float
        get() = SchemeCardDimens.CARD_WIDTH_DP - 2f * Spacing.md.value

    /**
     * 卡内可用高度：外框高 − 上下内边距 − 标签行（`labelSmall` 行高 + 标签上下内边距）
     * − 标签到正文的间距。调整态的内容层就住在这段里。
     */
    private val innerHeightDp: Float
        get() {
            val tagLineHeight = AppTypography.labelSmall.lineHeight.value
                .takeIf { it > 0f } ?: DEFAULT_TAG_LINE_HEIGHT_DP
            return SchemeCardDimens.CARD_HEIGHT_DP - 2f * Spacing.md.value -
                tagLineHeight - 2f * SchemeCardDimens.TAG_VPAD_DP -
                SchemeCardDimens.TAG_TO_BODY_GAP_DP
        }

    // ─────────────────────────────────────────────────────────────────────────────
    // 这一族的尺：卡内紧凑档（不是全站触控下限那一把）
    // ─────────────────────────────────────────────────────────────────────────────

    /** 档位表里给卡内这一族的那一把尺（28dp 见方一档） */
    private val compactTierDp: Float get() = TouchTier.COMPACT_CHIP

    /** 全站那颗下限（本轮一个字没动；这一族只是明写的例外，不是把它整体调松） */
    private val siteFloorDp: Float get() = TouchTier.SITE_FLOOR

    /**
     * 调整区那一块占掉的纵向高度：两行胶囊 + 两道具间间距 + 展开后的输入行下限。
     * **算式里全是生产常量**，不在这里抄字面量。
     */
    private fun adjustingBlockHeightDp(pillHeightDp: Float): Float =
        2f * pillHeightDp + SchemeCardDimens.CUSTOM_FIELD_MIN_HEIGHT_DP + 2f * Spacing.xs.value

    /**
     * 三颗数先对齐，再谈读数：档位表 = 生产那颗卡内热区常量 = 卡片自己的粒子高，
     * 而全站下限仍是全站下限（紧凑档**确实低于**它，否则这一格判的是一个不存在的例外）。
     *
     * 反例：把 `ADJUST_PILL_HEIGHT_DP` 与档位表写成两个各说各话的数；
     * 或者把全站下限本身改成 28（那时这一条红，而不是全站每一格静默一起松）。
     */
    private fun assertCompactTierEntryAgreesWithTheTierTable() {
        assertEquals(
            "档位表里卡内那一档与生产那颗「卡片操作热区」常量不一致",
            compactTierDp, AppDimens.CARD_ACTION_HIT_DP.toFloat(), 0f
        )
        assertEquals(
            "调整区粒子的视觉高就是它的热区（两层同数）；这两颗数分家等于又长出第三把尺",
            compactTierDp, SchemeCardDimens.ADJUST_PILL_HEIGHT_DP.toFloat(), 0f
        )
        assertEquals(
            "全站下限那颗生产常量与档位表不一致（本轮只放过卡内这一族，下限自己不许跟着缩）",
            siteFloorDp, AppDimens.TOUCH_TARGET_MIN_DP.toFloat(), 0f
        )
        assertTrue(
            "卡内这一档（${compactTierDp.toInt()}dp）现在不低于全站下限（${siteFloorDp.toInt()}dp）" +
                "——这一格判的是一个不存在的例外，请把尺改回全站下限",
            compactTierDp < siteFloorDp
        )
    }

    /**
     * 「为什么这一族允许低于全站下限」那条几何理由本身，每次判紧凑档都顺便判一遍。
     *
     * 卡片本体固定 158x150、内边距各一份 `Spacing.md`，调整态的内容层住在
     * [innerHeightDp] 那段可用高度里。两行胶囊 + 展开的输入行：
     * - 按紧凑档算：放得下（这才是允许低于下限的理由）；
     * - 同一算式换成全站下限：放不下（所以不是"页面上随便一颗小图标"，也不是无理由放宽）。
     *
     * 两个方向都有牙：**哪天卡内可用高度松出来、全站下限也放得下了**，第二条当场红，
     * 要求把这一族钉回全站下限，而不是让那个例外一直留着——缺了这条证人，
     * "把 48 改成 28"就只是照着实到读数认输。
     */
    private fun assertCompactTierIsTheReasonThisRowFits(context: String) {
        val atCompact = adjustingBlockHeightDp(compactTierDp)
        val atSiteFloor = adjustingBlockHeightDp(siteFloorDp)
        assertTrue(
            "$context 紧凑档这一族的算式已经不成立：两行胶囊 + 输入行按 ${compactTierDp.toInt()}dp 算要 " +
                "${atCompact.toInt()}dp，卡内可用高只有 ${innerHeightDp.toInt()}dp —— " +
                "那就不是「放得下才允许低于下限」了，请重钉这一档",
            atCompact <= innerHeightDp + 0.5f
        )
        assertTrue(
            "$context 这一族低于全站下限的理由已经不成立：同一块按全站下限 ${siteFloorDp.toInt()}dp 算要 " +
                "${atSiteFloor.toInt()}dp，现在也放得进卡内可用高 ${innerHeightDp.toInt()}dp —— " +
                "请把这三处判据钉回全站下限，而不是继续放过 ${compactTierDp.toInt()}dp",
            atSiteFloor > innerHeightDp + 0.5f
        )
    }

    /**
     * 四颗胶囊逐颗过**卡内那一档**（两轴数值判据，不是存在性），并钉住"换档不许掉语义"：
     * `Role.Button` 要说得出、名字要说得出、粒子高就是这一档（涨回 48 与缩成 20 一样红）。
     */
    private fun assertOptionsMeetCompactTier(options: List<SemanticsProbe.Target>, screen: String) {
        probe.at(compactTierDp).assertTargetsMeetFloor(
            options, compactTierDp,
            "$screen（卡内紧凑档 ${compactTierDp.toInt()}dp 这一把尺，不是全站下限那一把）"
        )
        options.forEach { option ->
            assertEquals(
                "换到紧凑档不许把角色弄丢，这一颗必须报 Button：" + option.describe(),
                "Button", option.role
            )
            assertTrue(
                "换到紧凑档不许把名字弄丢（读屏得说得出这一颗是什么）：" + option.describe(),
                option.labeled
            )
            assertEquals(
                "粒子热区的高度就是卡内这一档 ${SchemeCardDimens.ADJUST_PILL_HEIGHT_DP}dp" +
                    "（视觉与热区同数；涨回全站下限会顶破卡内可用高，缩成别的数就不是这一档了）：实到 " +
                    option.describe(),
                SchemeCardDimens.ADJUST_PILL_HEIGHT_DP.toFloat(), option.heightDp, 0.6f
            )
        }
    }

    private fun mount(widthDp: Float, heightDp: Float = 1000f) {
        rule.setContent {
            UiMatrix(widthDp = widthDp.toInt(), heightDp = heightDp.toInt())
                .RenderIn(LocalDensity.current.density) {
                    SchemeAdjustingBlock(
                        onRewrite = { tapped += it },
                        onCustomRewrite = { customSent += it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 同一宽度、可换高度的那一格：先给足、再收到卡内可用高，两次读数必须相同才叫"装得下" */
    private fun mountIn(heightDp: MutableState<Float>, widthDp: Float) {
        rule.setContent {
            UiMatrix(widthDp = widthDp.toInt(), heightDp = heightDp.value.toInt())
                .RenderIn(LocalDensity.current.density) {
                    SchemeAdjustingBlock(
                        onRewrite = {},
                        onCustomRewrite = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun openCustom() {
        rule.onAllNodes(hasText(RewriteCommand.CUSTOM.displayLabel)).onFirst().performClick()
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun editorTarget() =
        probe.of(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().single())

    /** 带点击语义的那几颗（输入框走 SetText，不在这堆里） */
    private fun optionTargets(what: String) =
        probe.laid(probe.actionableTargets(rule, what)).filter { it.role == "Button" }

    /** 调整区列出的就是那四项，顺序就是屏幕上那一行从左到右 */
    @Test
    fun `the adjusting row lists exactly the four options`() {
        mount(widthDp = 260f)
        val options = optionTargets("调整区·四项")
        assertEquals(
            "调整区该恰好四颗可点选项：" + options.joinToString { it.describe() },
            4, options.size
        )
        assertEquals(
            "四项的文案与阅读顺序（先读第一行、再读第二行）：" + options.joinToString { it.describe() },
            RewriteCommand.UI_OPTION_LABELS,
            options.sortedWith(compareBy({ it.topDp }, { it.leftDp })).map { it.label }
        )
    }

    /** 撤下的那四颗预设不再出现（枚举项本身仍留在 model 里，见 RewriteCommandTest） */
    @Test
    fun `the dropped presets are not listed any more`() {
        mount(widthDp = 260f)
        listOf("更短", "更像我", "别反问", "更直接").forEach { label ->
            assertTrue(
                "「$label」不该再出现在调整区：" + optionTargets("调整区·撤下的预设").joinToString { it.describe() },
                rule.onAllNodes(hasText(label)).fetchSemanticsNodes().isEmpty()
            )
        }
    }

    /** 没有常驻的"取消/收起"那颗：卡片收起归"点卡片外侧" */
    @Test
    fun `there is no persistent cancel or collapse control`() {
        mount(widthDp = 260f)
        listOf("取消", "收起").forEach { label ->
            assertTrue(
                "调整区默认态不该有「$label」",
                rule.onAllNodes(hasText(label)).fetchSemanticsNodes().isEmpty()
            )
        }
        openCustom()
        listOf("取消", "收起").forEach { label ->
            assertTrue(
                "展开自定义之后也不该有「$label」（那一格的收回是再点一次自定义）",
                rule.onAllNodes(hasText(label)).fetchSemanticsNodes().isEmpty()
            )
        }
        assertEquals(
            "自定义那一格该有输入框 + 提交",
            1, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        )
        assertTrue(
            "提交那颗要在",
            rule.onAllNodes(hasText("确认改写")).fetchSemanticsNodes().isNotEmpty()
        )
    }

    /** 预设点下去交回各自的命令，不碰自定义那条 */
    @Test
    fun `tapping a preset hands that command to the caller`() {
        mount(widthDp = 260f)
        rule.onAllNodes(hasText("换种说话")).onFirst().performClick()
        rule.onAllNodes(hasText("更自然")).onFirst().performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(listOf(RewriteCommand.REPHRASE, RewriteCommand.NATURAL), tapped)
        assertTrue("预设不该走自定义那条道", customSent.isEmpty())
    }

    /** 自定义才有的输入框：空文字不发，写了字发的是去掉首尾空格的那句 */
    @Test
    fun `the custom requirement is sent trimmed and blank text is not sent`() {
        mount(widthDp = 260f)
        openCustom()
        rule.onAllNodes(hasText("确认改写")).onFirst().performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertTrue("空要求不该发出：" + customSent.joinToString(), customSent.isEmpty())

        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("  保留第一句  ")
        rule.mainClock.advanceTimeBy(16L)
        rule.onAllNodes(hasText("确认改写")).onFirst().performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(listOf("保留第一句"), customSent)
    }

    /** 再点一次自定义收回那一格，不靠多一颗按钮 */
    @Test
    fun `tapping the custom option again folds the editor away`() {
        mount(widthDp = 260f)
        openCustom()
        assertEquals(1, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        openCustom()
        assertEquals(0, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    /**
     * 卡内这一档：两轴都是**数值**判据，外加那条"为什么允许低于全站下限"的几何证人。
     * 给足宽度那一格量的才是控件自己的尺寸，不是容器裁完的残值。
     */
    @Test
    fun `every option keeps the card compact tier when there is room`() {
        assertCompactTierEntryAgreesWithTheTierTable()
        mount(widthDp = 260f)
        val options = optionTargets("调整区·热区")
        assertEquals(
            "四颗选项热区都得在样本里，实到 " + options.joinToString { it.describe() },
            4, options.size
        )
        assertOptionsMeetCompactTier(options, "调整区·热区")
        assertCompactTierIsTheReasonThisRowFits("调整区·热区")
    }

    /**
     * 卡内可用宽度那一格：四项都要摆得出来、两轴都到**卡内那一档**，而且不许排成三行以上。
     *
     * 这一格是"不能只改常量后被裁切"的证人：四项排成两行两列，每行两颗各占一半可用宽
     * （实到约 70x28dp）；把四项塞成同一行会让每颗只剩约 34dp 宽、标签被 ellipsis 裁掉，
     * 而四颗各要全站下限的话，横向与纵向都放不下这张 158x150 的卡。
     * 允许这一族低于全站下限的理由不在档位表里，在几何里：一行两颗的合计宽必须放得进卡内容宽
     * （下面那条 `rowWidthDp` 判据），两行 + 展开的输入行必须放得进卡内可用高
     * （由 [assertCompactTierIsTheReasonThisRowFits] 那条算式，与更下面那格
     * 「four options plus the custom editor fit the card's fixed height」的实测读数一起判）。
     */
    @Test
    fun `all four options are laid out and hittable at the card width`() {
        assertCompactTierEntryAgreesWithTheTierTable()
        mount(widthDp = innerWidthDp)
        val options = optionTargets("调整区·卡宽")
        assertEquals(
            "卡内可用宽 ${innerWidthDp.toInt()}dp 要把四项都摆出来：" +
                options.joinToString { it.describe() },
            4, options.size
        )
        assertOptionsMeetCompactTier(options, "调整区·卡宽")
        assertCompactTierIsTheReasonThisRowFits("调整区·卡宽")
        // 一行两颗的合计宽：超出卡内容宽的就是被卡片裁掉的那一截，不是控件自己的尺寸
        val rowsOfTwo = options.groupBy { it.topDp.roundToInt() }.values.filter { it.size == 2 }
        assertTrue(
            "卡宽这一档量不到「一行两颗」的读数——四项没排成两行两列：" +
                options.joinToString { it.describe() },
            rowsOfTwo.isNotEmpty()
        )
        rowsOfTwo.forEach { row ->
            val rowWidthDp = row.fold(0f) { acc, t -> acc + t.widthDp }
            assertTrue(
                "一行两颗合计 ${rowWidthDp.toInt()}dp，超过卡内可用宽 ${innerWidthDp.toInt()}dp" +
                    "（超出的那部分是卡片在裁，不是热区）：" + row.joinToString { it.describe() },
                rowWidthDp <= innerWidthDp + 0.5f
            )
            val spreadDp = row.maxOf { it.widthDp } - row.minOf { it.widthDp }
            assertTrue(
                "同一行两颗各占一半位子；现在两颗差了 ${spreadDp.toInt()}dp——" +
                    "被挤扁的那一颗已经不在这一档上了：" + row.joinToString { it.describe() },
                spreadDp <= 1f
            )
        }
        val rows = options.map { it.topDp }.distinct().size
        assertTrue(
            "四项最多两行，不许再排成数行标签：" + options.joinToString { it.describe() },
            rows in 1..2
        )
        assertTrue(
            "四项的底边落在 ${options.maxOf { it.topDp + it.heightDp }.toInt()}dp，" +
                "超过卡内可用 ${innerHeightDp.toInt()}dp",
            options.maxOf { it.topDp + it.heightDp } <= innerHeightDp + 0.5f
        )
    }

    /**
     * 纵向实测：自定义展开后那一格（收成"自定义"一颗 + 输入行）在卡片固定外框给出的
     * 可用高度里放得下吗？
     * 量法是同一宽度先给足高度、再把盒子收到卡内可用高度——两次的输入行读数必须逐像素相同，
     * 一旦被裁，受限那一格会矮一截，这格就红并说出是哪一截放不下（而不是把卡片撑大躲过去）。
     */
    @Test
    fun `the four options plus the custom editor fit the card's fixed height`() {
        val boxHeight = mutableStateOf(1000f)
        mountIn(boxHeight, widthDp = innerWidthDp)
        openCustom()
        val roomyEditor = editorTarget()
        val roomyOption = optionTargets("调整区·不受限").minByOrNull { it.topDp }!!

        rule.runOnIdle { boxHeight.value = innerHeightDp }
        rule.mainClock.advanceTimeBy(16L)
        val boxedEditor = editorTarget()
        val boxedOptions = optionTargets("调整区·卡内高度")

        assertEquals(
            "卡内可用高度 ${innerHeightDp.toInt()}dp 装不下「自定义那一颗 + 输入行」：" +
                "输入行不受限 ${roomyEditor.describe()} → 受限 ${boxedEditor.describe()}",
            roomyEditor.heightDp, boxedEditor.heightDp, 0.5f
        )
        assertEquals(
            "压进卡内高度时上面那一排不许被压矮：不受限 ${roomyOption.describe()} → " +
                "受限 ${boxedOptions.minByOrNull { it.topDp }!!.describe()}",
            roomyOption.heightDp, boxedOptions.minByOrNull { it.topDp }!!.heightDp, 0.5f
        )
        assertTrue(
            "输入行底边落在 ${boxedEditor.topDp.toInt()}+${boxedEditor.heightDp.toInt()} = " +
                "${(boxedEditor.topDp + boxedEditor.heightDp).toInt()}dp，超过卡内可用 ${innerHeightDp.toInt()}dp",
            boxedEditor.topDp + boxedEditor.heightDp <= innerHeightDp + 0.5f
        )
    }
}
