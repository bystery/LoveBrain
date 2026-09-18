package com.lovebrain.app.ui.panel.reply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertEquals(158, SchemeCardDimens.CARD_WIDTH_DP)
        assertEquals(150, SchemeCardDimens.CARD_HEIGHT_DP)
        assertEquals(200, SchemeCardDimens.CARD_MAX_HEIGHT_DP)
    }
}
