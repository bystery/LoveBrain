package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.unit.dp
import com.lovebrain.app.AppConfig
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 行 1 的**宽度预算**本体（request2 §四「按钮间距 — 热区占位不同，宽度预算还有重复预留」）。
 *
 * 本轮修的是**重复预留**那一半：旧 `ReplyDimens` 把「范围符号与前一颗粒之间那段 `Spacing.sm`」
 * 烧进了那颗占位常量（`ROUND_SCOPE_RESERVE = ROUND_SCOPE_HIT + Spacing.sm`，改动前第 121 行），
 * 而 `tailWidth` 又自己加了一遍同一段间隔（`Spacing.sm + ROUND_SCOPE_RESERVE`，改动前第 145 行）
 * ⇒ 同一格 4dp 被扣两遍。三轴就此混成一团：可见轴（22dp 胶囊）、占位轴（本该＝热区那颗 28）、
 * 命中轴（core 的紧凑档 28）里，只有占位轴被虚报了 4dp，而且虚报的那半还乘了 `fontScale`
 * ——`Spacing.sm` 是间隔令牌，不是字，字号放大时它不跟着长。
 *
 * 下面每一格都能被一个**具体的坏实现**打破（本机不跑构建，标在类 KDoc 末）：
 * · `the fixed tail charges each gap exactly once` ← 把 `Spacing.sm` 再烧回符号那颗占位数
 *   （本轮拆掉的那一版）⇒ 88 变 92，这一格与 `the all-visible tier flips…`（254 那条边界）各红一次；
 * · `the necessary gaps do not grow with the system font` ← 恢复 `* fontScale` 乘整颗
 *   （连固定间隔一起放大）⇒ 字号差值那一格红；
 * · `the scope symbol keeps its own three axes apart` ← 给紧凑件再包一层 48dp 见方
 *   （占位轴被抬到 48）⇒ 那一格红；`➕ 的热区被动过了` 那一句钉的是反向的坏事（拿"拆重复预留"
 *   当口子去缩别人的热区）；
 * · `every production window width keeps the supplement chip out of the horizontal scroll`
 *   ← 把档位改回按**行宽**比（旧 360 门槛）或把 116 改回 156（大方盒那一代的账）⇒ 默认窗那一格红；
 *   同一格里另有反向证人（恒 116 那种死尺会被 `the all-visible tier flips…` 的 253 那句打红）。
 *
 * ⚠ 本轮**未跑构建/未跑测试**（这一轮的规矩是禁止 gradle）：这些都是静态自证格，
 * 断言的数全部从主人处现取（`Spacing` 令牌、`AppDimens` 那颗下限与那颗紧凑档、
 * `AppConfig` 那三档窗宽），测试里不抄第二份 116/66/48/28/48。
 * 真实手指观感（会不会误触、输入法在场时这一排的排布）不在这里，只能真机。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
class ReplyInputWidthBudgetTest {

    /** 面板自己左右各扣 `Spacing.xl`，才是行 1 真正拿到的宽（与 `RoleChipSemanticsTest` 同一口径） */
    private fun rowWidthOf(windowWidthDp: Int): Float =
        windowWidthDp - 2f * Spacing.xl.value

    // ═══════════ ① 宽度预算里没有同一格重复扣 ═══════════

    /**
     * 固定件预算的**本体**：三段间隔各一份、➕ 一份、范围符号一份，没有第四样。
     *
     * 期望值不叫 `tailWidth` 自己算（那会自证），而是照行 1 真实画出来的东西拼：
     * `Spacer(chip→输入框) + Spacer(输入框→➕) + ➕ 的热区 + Spacer(➕→符号) + 符号的热区`。
     * 坏实现：把 `Spacing.sm` 再烧回符号那颗占位数（本轮拆掉的那一版）⇒ 88 变 92，红在这里。
     */
    @Test
    fun `the fixed tail charges each gap exactly once`() {
        val expected = 3f * Spacing.sm.value +
            AppDimens.TOUCH_TARGET_MIN_DP + AppDimens.CARD_ACTION_HIT_DP
        assertEquals(
            "行 1 固定件应当只有『三段间隔 + ➕ 热区 + 符号热区』这一份账：" +
                "预算 ${ReplyDimens.tailWidth(1f, withAdd = true, withScope = true)}，拼出来的 $expected",
            expected,
            ReplyDimens.tailWidth(1f, withAdd = true, withScope = true).value,
            0.01f
        )
        // 逐颗加回去的那一段：每颗只贡献「自己那一段间隔 + 自己那颗占位」
        val base = ReplyDimens.tailWidth(1f, withAdd = false, withScope = false).value
        assertEquals("没有 ➕ 与符号时只剩 chip 段那一段间隔", Spacing.sm.value, base, 0.01f)
        assertEquals(
            "➕ 上树只多一段间隔加它自己的热区",
            Spacing.sm.value + AppDimens.TOUCH_TARGET_MIN_DP,
            ReplyDimens.tailWidth(1f, withAdd = true, withScope = false).value - base,
            0.01f
        )
        assertEquals(
            "符号上树只多一段间隔加它自己的占位（占位＝热区那颗，间隔不许再烧进占位里）",
            Spacing.sm.value + ReplyDimens.ROUND_SCOPE_HIT.value,
            ReplyDimens.tailWidth(1f, withAdd = true, withScope = true)
                .value - ReplyDimens.tailWidth(1f, withAdd = true, withScope = false).value,
            0.01f
        )
        // 占位轴自己也不夹带间隔：它必须正好等于命中轴那颗数（字号 1.0 那一档）
        assertEquals(
            "占位轴把间隔夹带回来了（本轮拆掉的那颗重复预留）：" +
                ReplyDimens.roundScopeFootprint(1f),
            ReplyDimens.ROUND_SCOPE_HIT,
            ReplyDimens.roundScopeFootprint(1f)
        )
    }

    /**
     * **间隔不随字号放大**：`Spacing.sm` 是令牌不是字，字号那一乘只许乘在符号自己那颗粒上。
     *
     * 旧写法是 `Spacing.sm + (ROUND_SCOPE_HIT + Spacing.sm) * fontScale`：字号 1.3 时它连那段
     * 固定间隔一起放大（多虚占 5.2dp）。这一格把字号换成 2.0 让差值翻倍到 28dp，坏实现一眼可见。
     * ➕ 那颗 48 的盒也不该随字号长（它是下限盒，不是字）。
     */
    @Test
    fun `the necessary gaps do not grow with the system font`() {
        val withScope1 = ReplyDimens.tailWidth(1f, withAdd = true, withScope = true).value
        val withScope2 = ReplyDimens.tailWidth(2f, withAdd = true, withScope = true).value
        assertEquals(
            "字号从 1.0 到 2.0 只该让符号自己那颗粒长，间隔一寸不动：" +
                "tail(1.0)=$withScope1 tail(2.0)=$withScope2",
            ReplyDimens.ROUND_SCOPE_HIT.value,   // 一颗热区边长 ×（2.0−1.0）
            withScope2 - withScope1,
            0.01f
        )
        assertEquals(
            "➕ 那颗下限盒跟着字号长了：它是热区盒，不是字",
            ReplyDimens.tailWidth(1f, withAdd = true, withScope = false).value,
            ReplyDimens.tailWidth(2f, withAdd = true, withScope = false).value,
            0.01f
        )
        // 系统字号调小（<1）也不许把这一颗的占位收到热区之下：占位轴不得低于命中轴
        assertTrue(
            "小字号把符号占位收到热区之下了：" + ReplyDimens.roundScopeFootprint(0.8f),
            ReplyDimens.roundScopeFootprint(0.8f) >= ReplyDimens.ROUND_SCOPE_HIT
        )
    }

    // ═══════════ ③ 三轴各归各位（数值这一半；形状那一半在 ReplyInputSymbolOwnerTest） ═══════════

    /**
     * 那颗范围符号的**三轴**（§4.3「三种尺寸分开测」）：
     * 命中轴 = core 那颗具名紧凑档（28，**有意低于**全站 48，指回主人不是页面自造）；
     * 占位轴 = 同一颗数（本轮把重复预留并掉之后它不再虚高）；
     * 而且占位轴必须**仍然低于**全站那颗下限——给紧凑件再包一层 48dp 见方容器（本轮明令禁的形状）
     * 会把这一句当场打红。可见轴（那颗 22dp 胶囊）不在预算里，由源码扫描那一格钉住它没被动过。
     */
    @Test
    fun `the scope symbol keeps its own three axes apart`() {
        val hit = ReplyDimens.ROUND_SCOPE_HIT
        assertEquals(
            "命中轴必须指回 core 那颗紧凑档，不许是页面抄的第二份数",
            AppDimens.CARD_ACTION_HIT_DP.toFloat(),
            hit.value,
            0.01f
        )
        assertEquals(
            "占位轴与命中轴分家了（旧写法这里是 32：热区 + 又一遍间隔）：" +
                ReplyDimens.roundScopeFootprint(1f),
            hit,
            ReplyDimens.roundScopeFootprint(1f)
        )
        assertTrue(
            "占位轴占回了全站下限——那就是 §4.3 禁的『给紧凑件包一层 48dp 见方容器』：" +
                ReplyDimens.roundScopeFootprint(1f),
            ReplyDimens.roundScopeFootprint(1f) < AppDimens.TOUCH_TARGET_MIN_DP.dp
        )
        // ➕ 那一档仍买满全站下限：本轮拆的是重复预留，不是别人的热区
        assertEquals(
            "➕ 的热区被动过了",
            AppDimens.TOUCH_TARGET_MIN_DP.toFloat(),
            ReplyDimens.ADD_HIT.value,
            0.01f
        )
    }

    // ═══════════ ② 默认窗与最低窗：补充不被悄悄藏进横滑 ═══════════

    /**
     * 档位真值表的生产读数（窗口宽从 `AppConfig` 那三档现取，行宽 = 窗口 − 面板左右内边距）。
     *
     * 判的是上一轮实测过的那个缺陷形状**不许退回去**：三颗胶囊＋输入框＋➕＋符号这一排在最低窗
     * 本来就装不下（根因在公共件那颗宽度轴下限，本轮不动它），所以"补充"在这一档**由本来就有的
     * 横滑接住、仍在树上**——但默认窗与最高窗必须三颗全可见，谁都不许掉进"只留一颗"那档兜底。
     *
     * ⚠ 说实话：这一格抓不住"重复预留回来了"（那一版 268 的余量 126 仍够 116、228 的余量 76
     * 仍够 66，档位一模一样）。把那一版打红的是另外两把：`每一格只扣一次`（直接量 88 这一颗数）
     * 与 `the all-visible tier flips…`（254 那条边界一多扣 4dp 就掉档）。
     * 这一格钉的是另一件坏事：有人把 `chipsCap` 按**行宽**比回旧 360 门槛、或把 116 改回 156
     * 那种大方盒的账——那才是把"补充"重新藏进横滑的写法。
     */
    @Test
    fun `every production window width keeps the supplement chip out of the horizontal scroll`() {
        val full = ReplyDimens.ROLE_CHIPS_MAX_WIDTH_DP.toFloat()
        val tight = ReplyDimens.ROLE_CHIPS_TIGHT_MAX_WIDTH_DP.toFloat()

        // 默认窗与最高窗：三颗**全可见**（chip 段拿到的上限就是全可见那一档，不是让位档）
        listOf(AppConfig.PANEL_DEFAULT_W, AppConfig.PANEL_MAX_W).forEach { window ->
            val cap = ReplyDimens.chipsCap(
                maxWidth = rowWidthOf(window).dp, fontScale = 1f, withAdd = true, withScope = true
            )
            assertEquals(
                "窗口 $window（行宽 ${rowWidthOf(window)}）该把三颗角色 chip 全摆在屏上，" +
                    "不是把补充藏进横滑",
                full, cap.value, 0.01f
            )
        }
        // 最低窗：让到两颗档，但**不许**掉到只留一颗的兜底档（那是"藏控件"那一族）
        val minCap = ReplyDimens.chipsCap(
            maxWidth = rowWidthOf(AppConfig.PANEL_MIN_W).dp, fontScale = 1f,
            withAdd = true, withScope = true
        )
        assertEquals(
            "最低窗（行宽 ${rowWidthOf(AppConfig.PANEL_MIN_W)}）该停在两颗档：" +
                "第三颗仍在树上、由这一段本来就有的横滑接住",
            tight, minCap.value, 0.01f
        )
        // 单调性：更宽的窗永远不该拿到更窄的一档（重复预留一回来会在边界上把它打回两颗档）
        var previous = 0f
        (AppConfig.PANEL_MIN_W..AppConfig.PANEL_MAX_W step 4).forEach { window ->
            val cap = ReplyDimens.chipsCap(
                maxWidth = rowWidthOf(window).dp, fontScale = 1f, withAdd = true, withScope = true
            ).value
            assertTrue(
                "窗口 $window 比上一档更宽却拿到更窄的 chip 段（预算里多扣了一格）：$cap < $previous",
                cap >= previous - 0.01f
            )
            previous = cap
        }
    }

    /**
     * 全可见那一档的**边界**与其反向证人（尺不许是死的）。
     *
     * ⚠ 门槛的两个组成（固定件那份、输入框下限那份）**一律从令牌自己拼**，
     * 不叫 `tailWidth` / `inputFloor` 算（拿被测函数推出的边界去问被测函数＝恒真那句老账）：
     * · 固定件 = 三段 `Spacing.sm` + 全站下限那颗（➕）+ 紧凑档那颗（符号）= 88；
     * · 输入框下限 = 它自己左右内边距 `Spacing.lg`×2 + 两颗 `bodyMedium` = 50；
     * · ⇒ 全可见门槛 = `116 + 88 + 50` = **254**，两颗档门槛 = `66 + 88 + 50` = **204**。
     * 坏实现一：重复预留回来（固定件那份变 92）⇒ 行宽 254 的余量只剩 112，第一句从 116 掉成 66，红；
     * 坏实现二：把 `chipsCap` 写成恒返回 116（那种"看着达标"的死尺）⇒ 253 那一句红。
     */
    @Test
    fun `the all-visible tier flips exactly where the deduped budget says it flips`() {
        val fixed = 3f * Spacing.sm.value +
            AppDimens.TOUCH_TARGET_MIN_DP + AppDimens.CARD_ACTION_HIT_DP
        val input = 2f * Spacing.lg.value + 2f * AppTypography.bodyMedium.fontSize.value
        // 令牌拼出来的这两份，必须与生产函数算出的完全一致（漂了就是两处各说各话）
        assertEquals(
            "生产那份固定件预算与令牌拼出来的对不上了（有人又开始多扣一格）",
            fixed, ReplyDimens.tailWidth(1f, withAdd = true, withScope = true).value, 0.01f
        )
        assertEquals(
            "生产那份输入框下限与令牌拼出来的对不上了",
            input, ReplyDimens.inputFloor(1f).value, 0.01f
        )

        val threshold = ReplyDimens.ROLE_CHIPS_MAX_WIDTH_DP + fixed + input
        assertEquals("门槛本体就是本轮那份账：116 + 88 + 50 = 254", 254f, threshold, 0.01f)
        assertEquals(
            "行宽 $threshold（= 三颗档 + 固定件 + 输入框下限）该刚好进全可见档",
            ReplyDimens.ROLE_CHIPS_MAX_WIDTH_DP.toFloat(),
            ReplyDimens.chipsCap(threshold.dp, 1f, withAdd = true, withScope = true).value,
            0.01f
        )
        assertEquals(
            "比门槛还窄 1dp 却仍给全可见档 ⇒ 这把尺是死的（或有人把整颗 48 包回符号上）",
            ReplyDimens.ROLE_CHIPS_TIGHT_MAX_WIDTH_DP.toFloat(),
            ReplyDimens.chipsCap((threshold - 1f).dp, 1f, withAdd = true, withScope = true).value,
            0.01f
        )
        // 两颗档与兜底档之间同一条判据：门槛 = 66 + 固定件 + 输入框下限，低 1dp 掉兜底
        val tightThreshold = ReplyDimens.ROLE_CHIPS_TIGHT_MAX_WIDTH_DP + fixed + input
        assertEquals("两颗档门槛本体：66 + 88 + 50 = 204", 204f, tightThreshold, 0.01f)
        assertEquals(
            "行宽 $tightThreshold 该停在两颗档",
            ReplyDimens.ROLE_CHIPS_TIGHT_MAX_WIDTH_DP.toFloat(),
            ReplyDimens.chipsCap(tightThreshold.dp, 1f, withAdd = true, withScope = true).value,
            0.01f
        )
        assertEquals(
            "低过两颗档门槛就该落回兜底那一颗（安全网还在）",
            AppDimens.TOUCH_TARGET_MIN_DP.toFloat(),
            ReplyDimens.chipsCap((tightThreshold - 1f).dp, 1f, withAdd = true, withScope = true).value,
            0.01f
        )
    }

    /**
     * 主动发那一档（＋ 与符号都不上树）不许按最满的那一排扣宽——旧写法在这里也白扣一段间隔。
     *
     * 这一格管的是"预算照着这一排**真实有什么**算"那半句：短路的件一份都不许留在账上，
     * 否则 chip 段会在根本没有 ＋ 的屏上白白多让一档（request2 §四 那句"重复预留"的同族形状）。
     */
    @Test
    fun `a row without the add button and the symbol does not pay for them`() {
        assertEquals(
            "主动发那一排只剩 chip 段与输入框之间那一段间隔",
            Spacing.sm.value,
            ReplyDimens.tailWidth(1f, withAdd = false, withScope = false).value,
            0.01f
        )
        assertEquals(
            "只有符号、没有 ➕ 时，账上不该出现那颗 48 的盒（份数从令牌拼，不叫被测函数自证）",
            Spacing.sm.value * 2f + AppDimens.CARD_ACTION_HIT_DP,
            ReplyDimens.tailWidth(1f, withAdd = false, withScope = true).value,
            0.01f
        )
    }
}
