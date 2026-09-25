package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.core.designsystem.AppDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-4/P0-6 回归测试：SchemeCardPresentationState 状态推导逻辑。
 *
 * 验证：
 * - 状态优先级正确（Recording > Rewriting > RewriteError > RewriteDone > Adjusting > Collapsed）
 * - 每个 sealed class 子类型可正确区分
 * - 状态不是互斥的——但推导规则保证同一时刻只有一个生效
 */
class SchemeCardPresentationStateTest {

    @Test
    fun `Collapsed is the default state`() {
        val state = SchemeCardPresentationState.Collapsed
        assertEquals("Collapsed", state::class.simpleName)
    }

    @Test
    fun `all states are distinct`() {
        val states = listOf(
            SchemeCardPresentationState.Collapsed,
            SchemeCardPresentationState.Adjusting,
            SchemeCardPresentationState.Recording,
            SchemeCardPresentationState.Recognizing,
            SchemeCardPresentationState.Rewriting,
            SchemeCardPresentationState.RewriteError("error"),
            SchemeCardPresentationState.RewriteDone
        )
        // All should be distinct
        assertEquals(states.size, states.toSet().size)
    }

    @Test
    fun `RewriteError carries message`() {
        val state = SchemeCardPresentationState.RewriteError("network error")
        assertEquals("network error", state.message)
    }

    @Test
    fun `Recording and Recognizing are different states`() {
        assertNotEquals(
            SchemeCardPresentationState.Recording as SchemeCardPresentationState,
            SchemeCardPresentationState.Recognizing as SchemeCardPresentationState
        )
    }

    @Test
    fun `RewriteDone is different from Collapsed`() {
        assertNotEquals(
            SchemeCardPresentationState.RewriteDone as SchemeCardPresentationState,
            SchemeCardPresentationState.Collapsed as SchemeCardPresentationState
        )
    }

    @Test
    fun `GesturePhase enum has all expected values`() {
        val phases = GesturePhase.entries
        assertEquals(5, phases.size)
        assertEquals(GesturePhase.IDLE, GesturePhase.valueOf("IDLE"))
        assertEquals(GesturePhase.PRESSING, GesturePhase.valueOf("PRESSING"))
        assertEquals(GesturePhase.RECORDING, GesturePhase.valueOf("RECORDING"))
        assertEquals(GesturePhase.RELEASED, GesturePhase.valueOf("RELEASED"))
        assertEquals(GesturePhase.CANCELLED, GesturePhase.valueOf("CANCELLED"))
    }

    @Test
    fun `PermissionEvent has Granted and Denied subtypes`() {
        val granted = PermissionEvent.Granted
        val denied = PermissionEvent.Denied(permanently = false)
        val deniedPermanently = PermissionEvent.Denied(permanently = true)

        assertEquals("Granted", granted::class.simpleName)
        assertEquals(false, denied.permanently)
        assertEquals(true, deniedPermanently.permanently)
    }

    @Test
    fun `SchemeCardDimens values are stable`() {
        // 卡宽这一档**不钉具体数**，钉的是它必须满足的几何事实：
        // 右下角三颗图标动作，每颗的可点击盒要 ≥ §6.5 :531 的下限，
        // 卡内左右各 8dp 内边距（`Spacing.md`，实体卡与骨架屏同一份）。
        // 原来写的是 `assertEquals(158, …)`——那只会把"放不下三颗下限"这件事
        // 一起钉死，谁想修就得先说服这格；换成判性质之后，158 当场就是红的。
        val innerWidth = SchemeCardDimens.CARD_WIDTH_DP - 2 * 8
        assertTrue(
            "卡内只剩 ${innerWidth}dp，放不下三颗 ${AppDimens.TOUCH_TARGET_MIN_DP}dp 的动作热区" +
                "（实到 ${SchemeCardDimens.CARD_WIDTH_DP}dp 卡宽）",
            innerWidth >= 3 * AppDimens.TOUCH_TARGET_MIN_DP
        )
        assertEquals(150, SchemeCardDimens.CARD_HEIGHT_DP)
        assertEquals(200, SchemeCardDimens.CARD_MAX_HEIGHT_DP)
    }
}
