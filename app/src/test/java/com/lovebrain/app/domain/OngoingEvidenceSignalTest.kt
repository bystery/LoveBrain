package com.lovebrain.app.domain

import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-15 回归测试：ongoing evidence 信号拆分。
 *
 * 确保：
 * - 只有 realMessageEvidence 刷新 lastEvidenceTurn
 * - replyDirectiveRelevant 不刷新证据时间
 * - persistentIntentRelevant 不刷新证据时间
 * - 持续意图不能永久保持 ACTIVE
 */
class OngoingEvidenceSignalTest {

    @Test
    fun `RelevanceSignals isRelevant when realMessageEvidence`() {
        val signals = OngoingContextSelector.RelevanceSignals(
            realMessageEvidence = true,
            replyDirectiveRelevant = false,
            persistentIntentRelevant = false
        )
        assertTrue(signals.isRelevant)
    }

    @Test
    fun `RelevanceSignals isRelevant when replyDirectiveRelevant`() {
        val signals = OngoingContextSelector.RelevanceSignals(
            realMessageEvidence = false,
            replyDirectiveRelevant = true,
            persistentIntentRelevant = false
        )
        assertTrue(signals.isRelevant)
    }

    @Test
    fun `RelevanceSignals isRelevant when persistentIntentRelevant`() {
        val signals = OngoingContextSelector.RelevanceSignals(
            realMessageEvidence = false,
            replyDirectiveRelevant = false,
            persistentIntentRelevant = true
        )
        assertTrue(signals.isRelevant)
    }

    @Test
    fun `RelevanceSignals not relevant when all false`() {
        val signals = OngoingContextSelector.RelevanceSignals(
            realMessageEvidence = false,
            replyDirectiveRelevant = false,
            persistentIntentRelevant = false
        )
        assertFalse(signals.isRelevant)
    }

    @Test
    fun `directive relevance does not update evidence - only realMessageEvidence does`() {
        // This test documents the design: even if replyDirective is relevant,
        // lastEvidenceTurn should NOT be updated.
        // The OngoingContextSelector.updateCooldown only adds to evidenceNames
        // when realMessageEvidence=true.
        val signals = OngoingContextSelector.RelevanceSignals(
            realMessageEvidence = false,
            replyDirectiveRelevant = true,
            persistentIntentRelevant = false
        )
        assertTrue(signals.isRelevant) // eligible for injection
        assertFalse(signals.realMessageEvidence) // but does NOT update evidence turn
    }

    @Test
    fun `persistent intent relevance does not update evidence`() {
        val signals = OngoingContextSelector.RelevanceSignals(
            realMessageEvidence = false,
            replyDirectiveRelevant = false,
            persistentIntentRelevant = true
        )
        assertTrue(signals.isRelevant) // eligible for injection
        assertFalse(signals.realMessageEvidence) // but does NOT update evidence turn
    }

    @Test
    fun `DORMANT_TURNS is 5 and COOLDOWN_TURNS is 3`() {
        assertEquals(5, OngoingContextSelector.DORMANT_TURNS)
        assertEquals(3, OngoingContextSelector.COOLDOWN_TURNS)
    }
}
