package com.lovebrain.app.viewmodel

import com.lovebrain.app.domain.OnboardingDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F12: OnboardingDecision 纯函数测试。
 *
 * 验证老用户检测逻辑：
 * - fresh install → onboarding
 * - 有 worker ticket → 不 onboarding
 * - 有 active ticket → 不 onboarding
 * - 有 KB → 不 onboarding
 * - totalGenerateCount > 0 → 不 onboarding
 * - completed flag → 不 onboarding
 */
class OnboardingDecisionTest {

    @Test
    fun freshInstall_showsOnboarding() {
        assertFalse(
            OnboardingDecision.isExistingUser(
                hasWorkerTickets = false,
                hasActiveTicketId = false,
                totalGenerateCount = 0,
                hasKnowledgeBase = false
            )
        )
    }

    @Test
    fun hasWorkerTickets_doesNotShowOnboarding() {
        assertTrue(
            OnboardingDecision.isExistingUser(
                hasWorkerTickets = true,
                hasActiveTicketId = false,
                totalGenerateCount = 0,
                hasKnowledgeBase = false
            )
        )
    }

    @Test
    fun hasActiveTicketId_doesNotShowOnboarding() {
        assertTrue(
            OnboardingDecision.isExistingUser(
                hasWorkerTickets = false,
                hasActiveTicketId = true,
                totalGenerateCount = 0,
                hasKnowledgeBase = false
            )
        )
    }

    @Test
    fun hasKnowledgeBase_doesNotShowOnboarding() {
        assertTrue(
            OnboardingDecision.isExistingUser(
                hasWorkerTickets = false,
                hasActiveTicketId = false,
                totalGenerateCount = 0,
                hasKnowledgeBase = true
            )
        )
    }

    @Test
    fun totalGenerateCountGreaterThanZero_doesNotShowOnboarding() {
        assertTrue(
            OnboardingDecision.isExistingUser(
                hasWorkerTickets = false,
                hasActiveTicketId = false,
                totalGenerateCount = 1,
                hasKnowledgeBase = false
            )
        )
    }

    @Test
    fun onboardingCompleted_doesNotShowOnboarding() {
        assertTrue(
            OnboardingDecision.isExistingUser(
                hasWorkerTickets = false,
                hasActiveTicketId = false,
                totalGenerateCount = 0,
                hasKnowledgeBase = false,
                onboardingCompleted = true
            )
        )
    }

    @Test
    fun allConditionsFalse_showsOnboarding() {
        assertFalse(
            OnboardingDecision.isExistingUser(
                hasWorkerTickets = false,
                hasActiveTicketId = false,
                totalGenerateCount = 0,
                hasKnowledgeBase = false,
                onboardingCompleted = false
            )
        )
    }
}
