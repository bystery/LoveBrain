package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 芯片的两档**新旋钮**：标签对齐档与按压反馈档。
 *
 * 这两档是补账，不是新功能——归并那六处芯片时缺了这两个旋钮，于是"把形状收进公共件"
 * 顺手改掉了两页的脸（账本 §76.3 如实登记的两处外观变化）：谈心模板那颗的标签从药丸左上
 * 被挪到居中（约 10dp），回复输入行那三颗角色芯片的按压曲线从默认弹簧被换成全站那条 120ms。
 * 这一组用例判三件事：
 *
 * 1. **换档必须量出差别**：同一颗芯片、同一个文案、只换档位 ⇒ 语义树里标签真的动了位置。
 * 2. **默认档必须等于今天**：另外三档的标签还在中轴上、四档的按压曲线还是全站那一条——
 *    这条判的是"补旋钮的人没有顺手改别人的页面"。
 * 3. **改回原样不许靠调用点自己画**：档位只在 `LbChipStyles.neutral` 里被选了一次，
 *    摆放与曲线都从档位取；组件里既没有第二条分支，也没有一个自由的
 *    `Alignment` / `FiniteAnimationSpec` 旋钮。
 *
 * ⚠ 仪器边界（照 `LbTextActionIconTierTest` 的写法，量不到的就明写）：
 * 语义树量得到**标签摆在哪**，量不到**绘制缩放**——本栈的 `ui-test` 1.6.8 只有
 * `boundsInRoot`（布局坐标，不含 `graphicsLayer` 的缩放），没有按绘制边界读的那把尺。
 * 所以按压这一半判的是"档位取到的是不是两条不同的曲线"（曲线对象的实测读数）
 * 加上"组件真的把档位交进唯一那颗实现"（源码级路由 + 它的反向证人）。
 * 像素级的按压曲线归截图基线那一格，而这一族今天还没有基线（`app/src/test/roborazzi`
 * 里没有芯片那一张，所以本格的改动不漂任何基线）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbChipTierTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    /** 标签与胶囊盒子之间的关系：两个方向的空隙（dp）。居中 = 两者相等。 */
    private data class Gap(val label: String, val toTop: Float, val toBottom: Float, val pillHeight: Float) {
        val describe: String
            get() = "$label：上隙 ${toTop.f1()}dp / 下隙 ${toBottom.f1()}dp / 胶囊高 ${pillHeight.f1()}dp"
    }

    /**
     * 盒子与标签**各读各的**：盒子取合并树里那颗带点击的（下限与动作都在它身上），
     * 标签取未合并树里那个文字节点——与 `LbPrimaryButtonStateTest` 量"字贴底色边上"
     * 用的是同一对读数。两个锚点都点名，读不到就当场红，不许这格在空树上自证。
     */
    private fun gapOf(label: String): Gap {
        val pill = probe.actionableTargets(rule, "标签档·$label").single()
        val inkNodes: List<SemanticsNode> =
            rule.onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("$label 的标签节点应当唯一（不唯一就说明这格没在判那颗芯片）", 1, inkNodes.size)
        val ink = probe.of(inkNodes.single())
        return Gap(
            label = label,
            toTop = ink.topDp - pill.topDp,
            toBottom = (pill.topDp + pill.heightDp) - (ink.topDp + ink.heightDp),
            pillHeight = pill.heightDp
        )
    }

    /**
     * 换档开火格：同一颗芯片、同一个文案，只把 [LbChipStyle.labelAlignment] 换一档，
     * 标签在胶囊里的位置必须真的动。把档位写死成"永远居中"的实现红在这里
     * （它的对照组是 [the measuring rod is not blind when a chip ignores the anchor tier]）。
     */
    @Test
    fun `switching the label anchor tier moves the label inside the pill`() {
        val anchor: MutableState<LbChipLabelAlignment> = mutableStateOf(LbChipLabelAlignment.Center)
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbChip(
                    label = "ANCHOR_LABEL",
                    onClick = {},
                    interaction = LbChipInteraction.Single,
                    style = LbChipStyles.filled.copy(labelAlignment = anchor.value)
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val seen = LinkedHashMap<String, Gap>()
        listOf(LbChipLabelAlignment.Center, LbChipLabelAlignment.TopStart).forEach { tier ->
            rule.runOnIdle { anchor.value = tier }
            rule.waitForIdle()
            seen[tier.name] = gapOf("ANCHOR_LABEL")
        }
        val readings = seen.values.joinToString("；") { it.describe }
        val center = seen.getValue("Center")
        val top = seen.getValue("TopStart")

        assertEquals(
            "Center 那一档标签必须落在胶囊中轴上（上下隙相等）：" + readings,
            center.toTop, center.toBottom, 1f
        )
        assertTrue(
            "TopStart 那一档标签必须明显靠上（下隙比上隙大出一截）：" + readings,
            top.toBottom - top.toTop >= 6f
        )
        assertTrue(
            "同一颗芯片换档必须量出差别（上隙之差 ≥6dp）：" + readings,
            center.toTop - top.toTop >= 6f
        )
        seen.values.forEach {
            assertEquals(
                "换标签档不许顺手把热区改小（下限仍然要长在这颗自己身上）：" + it.describe,
                probe.floorDp, it.pillHeight, 0.6f
            )
        }
    }

    /**
     * 量尺自己的牙：一颗**忽略档位的仿制品**在同一把尺下必须量出"换档没差别"。
     *
     * 这颗盒子不在生产里，也不该在——它只为上一条格子服务：如果标签位置这件事根本量不到
     * （字体度量把两档压成同一个读数、或锚点找错人），仿制品会与真组件报出同样的 δ，
     * 那一格就成了恒真的绿。这里钉的是"仿制品 δ = 0、真组件 δ ≥ 6dp"这一对。
     */
    @Test
    fun `the measuring rod is not blind when a chip ignores the anchor tier`() {
        val anchor: MutableState<LbChipLabelAlignment> = mutableStateOf(LbChipLabelAlignment.Center)
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                // 与 LbChip 单层那颗同一条链、同两档内边距，只是 contentAlignment 写死居中：
                // 这就是"公共件少了一档"时归并留下的那颗东西的形状
                Box(
                    modifier = Modifier
                        .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                        .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                        .background(SurfaceCard, LoveBrainShape.sm)
                        .clickable(role = Role.Tab, onClick = {})
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "ROD_LABEL", style = AppTypography.labelSmall, color = TextSecondary)
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        val seen = mutableListOf<Gap>()
        listOf(LbChipLabelAlignment.Center, LbChipLabelAlignment.TopStart).forEach { tier ->
            rule.runOnIdle { anchor.value = tier }
            rule.waitForIdle()
            seen += gapOf("ROD_LABEL")
        }
        val drift = kotlin.math.abs(seen[0].toTop - seen[1].toTop)
        assertTrue(
            "仿制品里换档量不到任何东西（这正是它要证明的事）；实到 drift=${drift.f1()}dp，" +
                seen.joinToString("；") { it.describe },
            drift < 1f
        )
        // 同一把尺在真组件上必须量得到——那句写在上面那格，两格合起来才叫有牙
        // 判据形状与真组件那一格**同一条**（上下隙相等，容差 = 一整像素的取整误差）：
        // 实到 上隙 19.0dp / 下隙 18.0dp——labelSmall 的行长 11dp 是奇数，居中之后一边 19、
        // 一边 18 是 mdpi 上取整的正常结果，用严格小于 1 的写法会把这份取整误判成"没在判对齐"。
        assertEquals(
            "仿制品的中轴读数本身必须是居中的（否则这格没在判对齐）：" + seen[0].describe,
            seen[0].toTop, seen[0].toBottom, 1f
        )
    }

    /**
     * 改回原样的那一半被钉住：动作档 [LbChipStyles.neutral]（谈心模板那颗选的档）的标签
     * 仍然贴在胶囊左上，而不是被组件的居中挪走。
     *
     * 这一格红 = 归并又把那一族的标签挪回居中了，正是账本 §76.3 登记的那处外观变化。
     */
    @Test
    fun `the action chip preset keeps its label pinned to the pill top`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbChip(
                    label = "NEUTRAL_LABEL",
                    onClick = {},
                    interaction = LbChipInteraction.Action,
                    style = LbChipStyles.neutral
                )
            }
        }
        rule.waitForIdle()
        val gap = gapOf("NEUTRAL_LABEL")
        assertTrue(
            "neutral 这一档的形状就是'标签贴药丸左上'，被挪回居中就是换脸：" + gap.describe,
            gap.toBottom - gap.toTop >= 6f
        )
        assertEquals(
            "贴上沿也不许把下限改掉（归并前那颗就是 48 见方 + 标签贴左上）：" + gap.describe,
            probe.floorDp, gap.pillHeight, 0.6f
        )
    }

    /**
     * 默认档 = 今天：没换档的那三档还在中轴上（这条判的是"我没改别人的页面"）。
     *
     * 与开火格同一把尺——把默认改成 TopStart，第一格与这一格会同时红，
     * 两格分开才说得出到底是"档位坏了"还是"默认被换了"。
     */
    @Test
    fun `the untouched presets still sit their labels on the pill axis`() {
        listOf("filled" to LbChipStyles.filled, "soft" to LbChipStyles.soft, "pill" to LbChipStyles.pill)
            .forEach { (name, style) ->
                assertTrue(
                    "$name 这一档没理由换标签摆放，默认必须是居中：" + style.labelAlignment,
                    style.labelAlignment == LbChipLabelAlignment.Center
                )
            }
        assertEquals(
            "形状参数的默认值必须是今天的行为（新长一档也是居中）",
            LbChipLabelAlignment.Center, LbChipStyle().labelAlignment
        )
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbChip(
                    label = "DEFAULT_LABEL",
                    selected = true,
                    onClick = {},
                    interaction = LbChipInteraction.Single,
                    style = LbChipStyles.filled
                )
            }
        }
        rule.waitForIdle()
        val gap = gapOf("✓ DEFAULT_LABEL")
        assertEquals(
            "默认档（filled）今天就是居中，上下隙必须相等：" + gap.describe,
            gap.toTop, gap.toBottom, 1f
        )
        assertEquals(
            "居中的那一颗也得过下限：" + gap.describe, probe.floorDp, gap.pillHeight, 0.6f
        )
    }

    /**
     * 两档按压曲线**必须是两条**：`Standard` 交出的就是全站那一条（120ms + FastOutSlowIn），
     * `Spring` 交出的是 `animateFloatAsState` 不指定 animationSpec 时那条默认弹簧。
     *
     * 判据落在曲线对象的实测读数上：把两档并成同一条曲线的实现，会在
     * "同一个对象"或"Spring 不是弹簧"这两处之一红。
     */
    @Test
    fun `the two press feedback tiers are two different curves`() {
        val standardSpec = LbChipPressFeedback.Standard.pressCurve
        val springy = LbChipPressFeedback.Spring.pressCurve
        assertNotSame("两档取到同一个对象 = 这一档是假的", standardSpec, springy)
        assertTrue(
            "Standard 必须是全站那条 tween，实到 ${standardSpec::class.simpleName}",
            standardSpec is TweenSpec<*>
        )
        val standard = standardSpec as TweenSpec<*>
        assertEquals("Standard 的时长就是今天那个数", 120, standard.durationMillis)
        assertEquals("Standard 的缓动也是今天那颗", FastOutSlowInEasing, standard.easing)
        assertTrue(
            "Spring 必须是弹簧，不是被换算出来的时长，实到 ${springy::class.simpleName}",
            springy is SpringSpec<*>
        )
        assertTrue("弹簧档不许长成 tween 的样子", springy !is TweenSpec<*>)
    }

    /**
     * 默认曲线 = 今天：这一族今天全部走全站那一条，**没有任何一档自己换过曲线**。
     *
     * 角色那颗要回默认弹簧，是在调用点把档位选成 `Spring`（选档位，不是画形状）；
     * 在档位表里替某一档改掉曲线，等于替别的页面换脸——这一格拦的就是这件事。
     */
    @Test
    fun `no preset switched its press curve by itself`() {
        assertEquals(
            "形状参数的默认值必须是今天的行为",
            LbChipPressFeedback.Standard, LbChipStyle().pressFeedback
        )
        val presets = listOf(
            "filled" to LbChipStyles.filled,
            "soft" to LbChipStyles.soft,
            "neutral" to LbChipStyles.neutral,
            "pill" to LbChipStyles.pill
        )
        val switched = presets.filter { it.second.pressFeedback != LbChipPressFeedback.Standard }
        assertEquals(
            "四档的按压曲线都得是全站那一条，换过档的：" + switched.joinToString { it.first },
            0, switched.size
        )
    }

    /**
     * 源码级路由格（判的是"档位真的被组件取走了"，不是"档位存在但没人读"）：
     * - 取按压缩放时**必须**把档位交进全站那一颗实现（组件里不许另写一条曲线）；
     * - 标签摆放只从档位取：两处放标签的盒子都写 `style.labelAlignment.labelPlacement`，
     *   剩下那一处 `Alignment.Center` 只许是分层热区外层那颗触摸盒（它管的不是标签）；
     * - 旋钮只有档位：`LbChipStyle` 里不许出现自由的 `Alignment` / `FiniteAnimationSpec` 属性；
     * - 全站那条曲线只许写一次，且带曲线的入口只许设计系统内部用（internal）。
     */
    @Test
    fun `the component routes both tiers instead of drawing a second shape`() {
        val chip = maskedOf("LbChip.kt")
        assertTrue("真组件必须把按压档位交进实现：" + pressArgs(chip), routesPressTier(chip))
        assertEquals("两处放标签的盒子都从档位取摆放（分层那颗的内层 + 单层那颗）", 2, placementTierCount(chip))
        assertEquals(
            "剩下的 Alignment Center 只许是分层热区外层那颗触摸盒，实到 ${bareCenterCount(chip)} 处",
            1, bareCenterCount(chip)
        )
        assertEquals("组件里不许再抄一条曲线（tween 只许写在 PressScale 那一处）", 0, Regex("""\btween\(""").findAll(chip).count())
        assertEquals(
            "旋钮只许是枚举档位：LbChipStyle 里不许出现自由的 Alignment / FiniteAnimationSpec 属性，" +
                "实到 ${freeKnobProps(chip).joinToString { it.value }}",
            0, freeKnobProps(chip).size
        )

        val press = maskedOf("PressScale.kt")
        assertEquals("全站那条按压曲线只许写一次", 1, Regex("""\btween\(""").findAll(press).count())
        assertEquals(
            "带曲线的重载必须是 internal——页面拿得到它，等于每一页都能自选曲线",
            1, Regex("""internal fun rememberPressScale""").findAll(press).count()
        )
        assertEquals(
            "页面向芯片的入口仍然是那颗两参数的默认档", 1,
            Regex("""fun rememberPressScale\(\s*targetScale: Float,\s*label: String\s*\)""")
                .findAll(press).count()
        )
    }

    /**
     * 路由那把尺的反向证人：把"组件忽略档位"的写法喂给同一组判据，三条必须都不通过。
     *
     * 没有这一格，上面那格可能红在一件没发生的事上，也可能恒绿（判据写错方向的形状）。
     */
    @Test
    fun `the routing scan rejects a chip that hardwires its own shape`() {
        val broken = """
            @Composable
            fun LbChip(label: String, style: LbChipStyle) {
                val (pressSource, scale) = rememberPressScale(style.pressedScale, "lbChip_")
                Box(contentAlignment = Alignment.Center) { ChipLabel("A", style) }
                Box(contentAlignment = Alignment.Center) { ChipLabel("B", style) }
            }
        """.trimIndent()
        val masked = SourceScan.maskComments(broken)
        assertTrue("忽略档位的写法必须被拒（它没有把 pressFeedback 交进实现）", !routesPressTier(masked))
        assertEquals("自己写死的摆放一处也不许算数", 0, placementTierCount(masked))
        assertEquals("两颗盒子都写死居中 ⇒ 尺必须看得见这 2 处", 2, bareCenterCount(masked))
    }

    /** 取按压缩放那一次调用的实参片段（按括号配对取，不用 `[^)]*`——坑表 95） */
    private fun pressArgs(masked: String): String {
        val hits = Regex("""rememberPressScale\(""").findAll(masked).map { it.range.last + 1 }.toList()
        assertEquals("组件里只许有一处取按压缩放，实到 ${hits.size} 处", 1, hits.size)
        val open = masked.lastIndexOf('(', hits.single())
        return masked.substring(hits.single(), SourceScan.closeIndexOf(masked, open))
    }

    private fun routesPressTier(masked: String): Boolean {
        val args = runCatching { pressArgs(masked) }.getOrDefault("")
        return "pressFeedback" in args && "pressCurve" in args
    }

    private fun placementTierCount(masked: String): Int =
        Regex("""contentAlignment\s*=\s*style\.labelAlignment\.labelPlacement""").findAll(masked).count()

    private fun bareCenterCount(masked: String): Int =
        Regex("""contentAlignment\s*=\s*Alignment\.Center""").findAll(masked).count()

    private fun freeKnobProps(masked: String): List<MatchResult> =
        Regex("""val [A-Za-z]\w*:\s*(Alignment|FiniteAnimationSpec)\b""").findAll(masked).toList()

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    /**
     * 掩掉注释、并统一行尾之后的源码。
     *
     * ⚠ 行尾必须统一：仓库里的源文件是 CRLF，而 `OddShapeOwnershipTest` 的第一版正是
     * 因为没去掉 `\r` 才把四格全打红——它报的是"没扫到结尾"而不是"账本不一致"，
     * 那种坏法最难查。形状判据一律按掩过、统一过行尾的文本走。
     */
    private fun maskedOf(name: String): String {
        val file = File(appRoot, "core/designsystem/$name")
        assertTrue("找不到 $file——路径接错了这格会恒绿", file.isFile)
        return SourceScan.maskComments(file.readText(Charsets.UTF_8)).replace("\r\n", "\n")
    }
}

/**
 * 读数写成一位小数（失败信息要说得出实到，不许只说"不等"）。
 *
 * 写成**文件级**扩展而不是类的成员：`Gap` 是嵌套类，拿不到外层那个 dispatch receiver，
 * 成员扩展函数在里面是解析不到的——第一发就撞在这里（三处 `f1` 全部 unresolved）。
 */
private fun Float.f1(): String = "%.1f".format(this)
