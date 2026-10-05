package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
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
 * 图标档的读数账——**这一格之前它不存在**，而它接管的那些形状各自欠着什么，
 * 全都记在被搬走的那几处注释里：只垫一条轴（短文字量出 40x48dp、行内动作量出 32x48dp）、
 * `role=无`（首页那两颗入口、消息行那颗删除、逐条记忆那颗 `⋯`）、
 * 名字写在内层图标上（语义合并后念两遍）、名字写成内联中文（英文环境下仍念中文）。
 *
 * 判据形状抄 `RowActionSemanticsTest` 与 `LbTextActionTest`：**只读语义树**，
 * 不在源码里搜 `size(` 或 `heightIn`。
 *
 * ⚠ 这把尺**看不见颜色与字形尺寸**——语义树里没有这两栏。所以这里判的是
 * "换档不换热区"，不是"某一档真的是 22dp"；后者只有截图基线那一格能说，
 * 而那格现在还欠着（同 `LbTextActionTest` 第二格写的边界）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbTextActionIconTierTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /**
     * 档位表里那两把尺：**全站下限**与**卡内/行内紧凑档**。
     *
     * 本轮合同拆掉的只有"把所有小件的**可见尺寸**钉成 48"这一条过度约束；
     * 全站那颗下限本身一个字没动。所以这一族里"谁走哪一把"必须逐档写清楚，
     * 不许拿"两档相等"当判据（那种比法下全站一起缩是绿的）。
     */
    private val siteFloorDp: Float get() = TouchTier.SITE_FLOOR
    private val compactFloorDp: Float get() = TouchTier.CARD_ACTION

    /**
     * 轻通知条那颗关闭那一档（原话第 16 条 / 基线 v1 §6.3）：24dp 见方，比卡内 28 还矮一档。
     * 读的是测试侧现成的那一颗 [TouchTier.PANEL_HEADER_HOTZONE]（同值 24），不自己抄第二个数。
     */
    private val noticeFloorDp: Float get() = TouchTier.PANEL_HEADER_HOTZONE

    /** 这一档住在哪，就用哪一把尺：页头/行尾 = 全站下限；卡内一排三颗/列表行内 = 紧凑档；轻通知关闭 = 24 档 */
    private fun expectedFloorOf(tier: LbTextActionGlyph): Float = when (tier) {
        LbTextActionGlyph.Header, LbTextActionGlyph.Inline -> siteFloorDp
        LbTextActionGlyph.Compact, LbTextActionGlyph.RowIcon -> compactFloorDp
        LbTextActionGlyph.Notice -> noticeFloorDp
    }

    /**
     * 三颗数必须先对齐，再谈语义树读数：**档位入口自己声明的热区** = **生产常量** = **档位表**。
     *
     * 反例（都会在这里红，而不是悄悄流到下面那一格）：
     * - 把 `hitSize` 里 Header/Inline 那两支也指向卡内那颗常量（"全站一起缩"）；
     * - 把 `AppDimens.TOUCH_TARGET_MIN_DP` 自己改小（红的是"下限本身不许动"那一条，
     *   因为另外两颗数是档位表，不是由被测对象现算的）；
     * - 新增一档忘了登记（`when` 逼着编译期写全，运行期这条逼着它走对的一把尺）。
     */
    private fun assertTierEntriesAgreeWithTheTierTable() {
        assertEquals(
            "全站下限那颗生产常量与档位表不一致（本轮只放过卡内那一族，下限自己不许跟着缩）",
            siteFloorDp, AppDimens.TOUCH_TARGET_MIN_DP.toFloat(), 0f
        )
        assertEquals(
            "卡内/行内那一档的生产常量与档位表不一致",
            compactFloorDp, AppDimens.CARD_ACTION_HIT_DP.toFloat(), 0f
        )
        LbTextActionGlyph.values().forEach { tier ->
            assertEquals(
                "${tier.name} 那一档声明的热区没有走它该走的那把尺" +
                    "（期望 ${expectedFloorOf(tier).toInt()}dp，实到 ${tier.hitSize.value.toInt()}dp）",
                expectedFloorOf(tier), tier.hitSize.value, 0.01f
            )
        }
    }

    private companion object {
        /** 两支入口 × 两档热区，四颗各挂一个不重复的名字，读回来才知道哪颗是哪颗 */
        const val VECTOR_INLINE_LABEL = "VECTOR_INLINE_LABEL"
        const val DRAWABLE_INLINE_LABEL = "DRAWABLE_INLINE_LABEL"
        const val VECTOR_COMPACT_LABEL = "VECTOR_COMPACT_LABEL"
        const val DRAWABLE_COMPACT_LABEL = "DRAWABLE_COMPACT_LABEL"
    }

    private fun mountVector(description: String, onClick: () -> Unit, enabled: Boolean = true) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                LbTextAction(
                    icon = Icons.Filled.Close,
                    description = description,
                    onClick = onClick,
                    enabled = enabled
                )
            }
        }
        rule.waitForIdle()
    }

    /**
     * 名字与角色：读屏说得出这一句，而且只念一遍。
     *
     * 「只念一遍」不是凑数：这一族的旧写法是把标签写在内层 `Icon` 上，
     * 而外层那颗带点击的盒自己没有任何名字——树里读到的角色是 `Image`（知识库那颗
     * 18dp 删除图标就是这么量到的），合并出来的角色根本不是读屏要的那个。
     * 现在标签只挂在带 `clickable` 的那一颗上，内层图标是装饰。
     */
    @Test
    fun `the icon tier announces its description once and as a button`() {
        mountVector("CLOSE_LABEL", onClick = {})
        val targets = probe.actionableTargets(rule, "LbTextAction 图标档")
        assertEquals("这一屏只该有这一颗动作：" + targets.joinToString { it.describe() }, 1, targets.size)
        val target = targets.single()
        assertEquals(
            "图标没有文字，名字只能来自 contentDescription，而它必须挂在带点击的那一颗自己上：" +
                target.describe(),
            listOf("CLOSE_LABEL"), target.contentDescriptions
        )
        assertEquals(
            "自定义 Box + clickable 不会由 Material 补角色，图标档得自己报按钮：" + target.describe(),
            "Button", target.role
        )
        probe.assertNoDuplicatedAnnouncement(rule, "LbTextAction 图标档")
    }

    /**
     * 两轴都够下限。
     *
     * 20dp 的字形比 48dp 小得多，所以这一格量到的宽度**只能**来自那条 `widthIn`：
     * 把垫宽那一步删掉，这里立刻量到 20x48dp（`LbEmptyState` 第一版只垫高度量出
     * 40x48dp、`RowActionButton` 只垫高度量出 32x48dp，同一族）。
     */
    @Test
    fun `the icon tier fills the floor on both axes`() {
        mountVector("CLOSE_LABEL", onClick = {})
        val target = probe.actionableTargets(rule, "LbTextAction 图标档").single()
        assertEquals(
            "字形比下限小得多，宽度只许由那一档垫出来，实到 " + target.describe(),
            probe.floorDp, target.widthDp, 0.5f
        )
        assertEquals(
            "高度同理，实到 " + target.describe(),
            probe.floorDp, target.heightDp, 0.5f
        )
        assertTrue(
            "热区两轴都要 ≥${probe.floorDp.toInt()}dp：" + target.describe(),
            !target.tooSmall(probe.floorDp)
        )
    }

    /**
     * 换字形尺寸不许换热区档——**逐档各自钉一把尺**，不是"每一档都等于 Header"那种比法。
     *
     * 旧写法比的是"四档读数彼此相等"：那种比法抓不到「全站一起缩到 20dp」
     * （四档一起缩、仍然相等，照旧绿），而本轮合同拆掉的**只是**"把所有小件的可见尺寸钉成 48"
     * 那一条过度约束，全站那颗下限本身一个字没动。所以按档点名：
     * - [LbTextActionGlyph.Header] / [LbTextActionGlyph.Inline]（页头、行尾）：
     *   **仍然 ≥** 全站下限那一档，两轴各自判（缩进紧凑档立刻红）；
     * - [LbTextActionGlyph.Compact] / [LbTextActionGlyph.RowIcon]（卡内一排三颗、列表行内图标）：
     *   **恰好**紧凑档两轴——缩成 20 红，涨回 48 也红（涨回去等于把 3×48 塞回 142dp 的卡内容宽，
     *   "为什么这一档允许低于下限"那条几何证人在 `ResultAreaTouchTargetsTest` 里）。
     *
     * 顺序也是判据：先钉"档位入口自己声明的那一颗数"（`hitSize` ↔ 两颗生产常量 ↔ 档位表），
     * 再钉语义树实到读数——声明改了而布局没跟上、或布局改了而声明没改，都会在这里分家。
     *
     * ⚠ 这一格量不到**字形**尺寸（语义树没有尺寸以外的外观栏），买的是热区；
     * 22dp / 20dp / 16dp / 13dp 那四档字形的差别只有截图基线那一格能说。
     */
    @Test
    fun `switching the glyph tier must not switch the hot zone`() {
        assertTierEntriesAgreeWithTheTierTable()
        val glyph = mutableStateOf(LbTextActionGlyph.Header)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                LbTextAction(
                    icon = Icons.Filled.Close,
                    description = "GLYPH_LABEL",
                    glyph = glyph.value,
                    onClick = {}
                )
            }
        }
        val readings = mutableMapOf<String, Pair<Float, Float>>()
        for (tier in LbTextActionGlyph.values()) {
            rule.runOnIdle { glyph.value = tier }
            rule.waitForIdle()
            val floor = expectedFloorOf(tier)
            val target = probe.at(floor).assertAllActionableMeetTouchFloor(
                rule, "LbTextAction 图标档", "（${tier.name}·这一档走 ${floor.toInt()}dp）"
            ).single()
            readings[tier.name] = target.widthDp to target.heightDp
            assertEquals("${tier.name} 那一档也得报按钮角色：" + target.describe(), "Button", target.role)
            assertEquals(
                "换档不许把名字一起换掉：" + target.describe(),
                listOf("GLYPH_LABEL"), target.contentDescriptions
            )
            if (floor == compactFloorDp) {
                // 紧凑档两侧都钉：少一分（缩成 20）与多一分（涨回 48）都是违约
                assertEquals(
                    "${tier.name} 走的是 ${floor.toInt()}dp 见方那一档，宽度必须恰好落在这一档：" + target.describe(),
                    floor, target.widthDp, 0.6f
                )
                assertEquals(
                    "${tier.name} 走的是 ${floor.toInt()}dp 见方那一档，高度必须恰好落在这一档：" + target.describe(),
                    floor, target.heightDp, 0.6f
                )
            } else {
                // 全站下限那一族只判不低于：行尾/页头没有"必须正好 48"这一说，宽一点不算违约
                assertTrue(
                    "${tier.name} 仍走全站下限，两轴都不许低于 ${floor.toInt()}dp：" + target.describe(),
                    !target.tooSmall(floor)
                )
            }
        }
        // 两把尺之间的大小关系也得是读出来的，不是口号：紧凑档确实比页头那一档矮，
        // 矮到能排进卡内那一排。两把尺合回一颗（"全站一起缩/一起涨"）时这一条同样红。
        val compact = readings.getValue("Compact")
        val header = readings.getValue("Header")
        assertTrue(
            "紧凑档读数 $compact 并不比页头那一档 $header 矮，说明这两把尺又合回一颗了：$readings",
            compact.first < header.first && compact.second < header.second
        )
    }

    /**
     * 资源那一支与 `ImageVector` 那一支**同一个形状**。
     *
     * 两支入口只是取字形的方式不同（`R.drawable.ic_copy` 与 `Icons.Filled.Close`）；
     * 要是其中一支自己再画一遍盒，热区就会分家——卡片里那三颗走的正是这一支。
     * 这里一次挂齐"两支 × 两档"，比的是**同一档里两支逐位相同**，再把紧凑档那一对
     * 钉在见方那一档上：旧版 `CardActionIcon` 口头写着"热区 28dp"、实际是
     * `.size(20.dp).padding(4.dp)`（并不等于 28 见方），所以这里只认语义树实到读数，
     * 不认任何写在注释或名字里的口头数。
     */
    @Test
    fun `the drawable overload is the same shape as the vector one`() {
        var fired = 0
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                Column {
                    LbTextAction(
                        icon = Icons.Filled.Close,
                        description = VECTOR_INLINE_LABEL,
                        glyph = LbTextActionGlyph.Inline,
                        onClick = {}
                    )
                    LbTextAction(
                        iconRes = R.drawable.ic_copy,
                        description = DRAWABLE_INLINE_LABEL,
                        glyph = LbTextActionGlyph.Inline,
                        onClick = {}
                    )
                    LbTextAction(
                        icon = Icons.Filled.Close,
                        description = VECTOR_COMPACT_LABEL,
                        glyph = LbTextActionGlyph.Compact,
                        onClick = {}
                    )
                    LbTextAction(
                        iconRes = R.drawable.ic_copy,
                        description = DRAWABLE_COMPACT_LABEL,
                        glyph = LbTextActionGlyph.Compact,
                        onClick = { fired++ }
                    )
                }
            }
        }
        rule.waitForIdle()
        assertTierEntriesAgreeWithTheTierTable()
        val all = probe.actionableTargets(rule, "LbTextAction 两支入口")
        val byLabel = all.associateBy { it.label }
        assertEquals(
            "两支入口 × 两档热区应当各量到一颗（整树 ${all.size} 颗，实到名字 " + all.map { it.label } +
                "）——少一颗就是某一支根本没进树，" +
                "下面那场比较就成了空转：\n" + all.joinToString("\n") { "  " + it.describe() },
            4, byLabel.size
        )
        listOf(
            VECTOR_INLINE_LABEL to DRAWABLE_INLINE_LABEL,
            VECTOR_COMPACT_LABEL to DRAWABLE_COMPACT_LABEL
        ).forEach { (vectorLabel, drawableLabel) ->
            val vector = byLabel[vectorLabel]
                ?: throw AssertionError("矢量那一支读不到数：$vectorLabel，实到 " + all.joinToString { it.describe() })
            val drawable = byLabel[drawableLabel]
                ?: throw AssertionError("资源那一支读不到数：$drawableLabel，实到 " + all.joinToString { it.describe() })
            val tier = if (vectorLabel == VECTOR_COMPACT_LABEL) LbTextActionGlyph.Compact else LbTextActionGlyph.Inline
            val floor = expectedFloorOf(tier)
            // 语义不许因为换入口而变薄：两支都得报按钮、都说得出自己那一句
            assertEquals("矢量那一支要报按钮：" + vector.describe(), "Button", vector.role)
            assertEquals("资源那一支也要报按钮：" + drawable.describe(), "Button", drawable.role)
            assertEquals(
                "名字要挂在带点击的那一颗自己上：" + vector.describe(),
                listOf(vectorLabel), vector.contentDescriptions
            )
            assertEquals(
                "两支一样都得有名字，不能一支有名字一支没有：" + drawable.describe(),
                listOf(drawableLabel), drawable.contentDescriptions
            )
            // 两轴都不低于自己这一档：档位写在调用点上，不是整屏一把尺
            probe.at(floor).assertTargetsMeetFloor(
                listOf(vector, drawable), floor,
                "两支入口（${tier.name}·这一档 ${floor.toInt()}dp）"
            )
            // 「同一个形状」= 同一档里两支逐位相同
            assertEquals(
                "${tier.name} 那一档两支的热区宽度分家了（矢量 ${vector.describe()} vs 资源 ${drawable.describe()}）",
                vector.widthDp, drawable.widthDp, 0.6f
            )
            assertEquals(
                "${tier.name} 那一档两支的热区高度分家了（矢量 ${vector.describe()} vs 资源 ${drawable.describe()}）",
                vector.heightDp, drawable.heightDp, 0.6f
            )
            if (floor == compactFloorDp) {
                assertEquals(
                    "卡内那一排必须是 ${floor.toInt()}dp 见方（口头宣称的数不作数，只看实到）：" + drawable.describe(),
                    floor, drawable.widthDp, 0.6f
                )
                assertEquals("高度同理，实到 " + drawable.describe(), floor, drawable.heightDp, 0.6f)
            }
        }
        rule.onNode(hasClickAction() and hasContentDescription(DRAWABLE_COMPACT_LABEL)).performClick()
        rule.waitForIdle()
        assertEquals("按一次就该回调一次", 1, fired)
    }

    /**
     * 表态图标要报得出"现在表过态没有"。
     *
     * 卡片里的赞/踩被点过之后**只有图标换了颜色**，语义树里 `selected` 是 null——
     * 读屏能听到"赞、按钮"，听不到"这条方案已经表过态"（`ResultAreaTouchTargetsTest`
     * 那一屏量到的第二条）。`selected` 必须挂在带点击的这一颗上，不是内层图标上。
     */
    @Test
    fun `a marked icon announces that it is marked`() {
        val marked = mutableStateOf(true)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(320).RenderIn(deviceDensity) {
                LbTextAction(
                    icon = Icons.Filled.Close,
                    description = "LIKE_LABEL",
                    selected = marked.value,
                    onClick = {}
                )
            }
        }
        // 两档各读一次：名字与角色不能随状态漂移，而 selected 自己必须真的跟着翻。
        val readings = mutableMapOf<String, SemanticsProbe.Target>()
        for (state in listOf(true, false)) {
            rule.runOnIdle { marked.value = state }
            rule.waitForIdle()
            val target = probe.actionableTargets(rule, "LbTextAction 图标档（表态）").single()
            readings[state.toString()] = target
            assertEquals(
                "名字不能因为状态而换：" + target.describe(),
                listOf("LIKE_LABEL"), target.contentDescriptions
            )
            assertEquals("角色也不能：" + target.describe(), "Button", target.role)
        }
        val on = readings.getValue("true")
        assertEquals("表过态那颗必须报 true，实到 " + on.describe(), true, on.selected)
        val off = readings.getValue("false")
        assertEquals(
            "没表态那颗要报 false，不能报 null——null 等于读屏听不出差别，实到 " + off.describe(),
            false, off.selected
        )
    }

    /**
     * 禁用那一档：还是点得着"有这么一颗按钮"，只是按不动。
     *
     * 判的是**语义**，不是"点下去没反应"——`clickable(enabled = false)` 仍然是一个
     * 操作入口，只是当前不许按；把它从树里删掉（N=0 时不画）这一格就抓不到了。
     * 尺寸与名字都不能因为禁用而退化：禁用不等于可以小一点。
     */
    @Test
    fun `a disabled icon action keeps its name its role and its floor`() {
        var fired = 0
        mountVector("CLOSE_LABEL", onClick = { fired++ }, enabled = false)
        val target = probe.actionableTargets(rule, "LbTextAction 图标档（禁用）").single()
        assertTrue("禁用那一档得在树里报出 disabled：" + target.describe(), target.disabled)
        assertEquals("禁用不摘角色：" + target.describe(), "Button", target.role)
        assertEquals("禁用不摘名字：" + target.describe(), listOf("CLOSE_LABEL"), target.contentDescriptions)
        assertTrue("禁用不缩热区：" + target.describe(), !target.tooSmall(probe.floorDp))
        assertEquals("禁用那颗不该回调", 0, fired)
    }
}
