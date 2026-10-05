package com.lovebrain.app.domain

/**
 * Onboarding 迁移决策——纯函数，可测试。
 *
 * 从 SetupActivity 私有函数抽离为 domain 级 pure function。
 * 检测项：
 * - 已有工单
 * - 已有激活工单 ID
 * - 累计生成次数 > 0
 * - 已有知识库目录
 * - 已完成 onboarding 标记
 *
 * 任一命中即视为老用户，不显示 onboarding。
 */
object OnboardingDecision {

    /**
     * 判断是否为老用户（不显示 onboarding）。
     *
     * @param hasWorkerTickets 是否已有工单
     * @param hasActiveTicketId 是否已有激活工单 ID
     * @param totalGenerateCount 累计生成次数
     * @param hasKnowledgeBase 是否已有知识库目录
     * @param onboardingCompleted 是否已完成 onboarding 标记
     * @return true = 老用户，不显示 onboarding
     */
    fun isExistingUser(
        hasWorkerTickets: Boolean,
        hasActiveTicketId: Boolean,
        totalGenerateCount: Int,
        hasKnowledgeBase: Boolean,
        onboardingCompleted: Boolean = false
    ): Boolean {
        if (hasWorkerTickets) return true
        if (hasActiveTicketId) return true
        if (totalGenerateCount > 0) return true
        if (hasKnowledgeBase) return true
        if (onboardingCompleted) return true
        return false
    }
}
