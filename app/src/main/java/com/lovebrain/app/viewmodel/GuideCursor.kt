package com.lovebrain.app.viewmodel

/**
 * 首页遮罩引导的游标（基线 v1 §6.8；原始第 14 条 / 优化第 17 条）。
 *
 * 这一格**不回答"配了没配"**——那三件事从真状态读（`providerReady` / 无障碍已授权 /
 * 捕获已开且披露已同意，见 [SetupViewModel.guideFacts]）。游标只回答两件事：
 * **介绍层看过没有**、**指引现在停在哪一格**。
 *
 * 它替代 `OnboardingFlow` 里那颗 `remember` 步号（重建即丢的那本旧账），
 * 持久化键是 `SettingsStorePort.guideCursor`（存枚举名，空串=从没写过）。
 */
enum class GuideCursor {
    /** 介绍层还没看：这一格不画罩子，走介绍页 */
    NONE,

    /** 指引指向首页「模型供应商」入口 */
    PROVIDER,

    /** 指引指向首页「消息捕获」入口 + 去系统设置那一步 */
    ACCESSIBILITY,

    /** 指向真实捕获开关（范围与披露按现有流程走） */
    CAPTURE,

    /** 三步派生事实都成立，或被用户明确"不再指引"钉死 */
    DONE,

    /** 用户按了"稍后"：收罩，缺项交回首页黄字行，可经 `resumeGuide` 恢复 */
    DEFERRED_TO_HINT;

    companion object {
        /** 读盘：空串、脏值、旧版本写进来的未知名都算 `NONE`，**不许抛** */
        fun from(raw: String?): GuideCursor =
            raw?.let { name -> entries.firstOrNull { it.name == name } } ?: NONE
    }
}

/**
 * 游标 = 派生事实算出来的，**不是按钮点出来的**（原始第 14 条的病灶正是"点去配置即 complete"）。
 *
 * 规则（逐条都有对应的回归格，见 `GuideCursorResolveTest`）：
 * 1. 介绍没看过 → `NONE`（罩子在介绍页之后才登场）；
 * 2. 用户按过"稍后"且**仍有未完成项** → 停在 `DEFERRED_TO_HINT`，不追着他画罩子；
 * 3. 其余情况取**最早那一格还没满足的**步骤：供应商 → 无障碍 → 捕获；
 * 4. 三步都满足 → `DONE`（包括"稍后"之后又自己配齐的情形，提示行随之消失）；
 * 5. 权限事后被撤销 → 按规则 3 自己退回未完成那一格，不靠人工重置。
 *
 * `stored` 只用来分辨"是否按过稍后"；**它不能把已完成的那一步再指回去**，
 * 因为指向一格已经完成的目标等于骗用户再操作一遍。
 */
internal fun resolveGuideCursor(
    stored: GuideCursor,
    facts: SetupViewModel.GuideFacts
): GuideCursor {
    if (!facts.introSeen) return GuideCursor.NONE
    if (facts.providerReady && facts.accessibilityGranted && facts.captureOn) return GuideCursor.DONE
    if (stored == GuideCursor.DEFERRED_TO_HINT) return GuideCursor.DEFERRED_TO_HINT
    return when {
        !facts.providerReady -> GuideCursor.PROVIDER
        !facts.accessibilityGranted -> GuideCursor.ACCESSIBILITY
        !facts.captureOn -> GuideCursor.CAPTURE
        else -> GuideCursor.DONE
    }
}
