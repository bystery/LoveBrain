package com.lovebrain.app.ui.kb

import com.lovebrain.app.core.designsystem.AppDimens

/**
 * 知识库这一族页面（`ui/KnowledgeBaseActivity.kt` 的列表页 + 本包的建库向导）共用的尺寸常量
 * （令牌化那一轮的产物：数值不变，仅外放命名）。
 *
 * 它原本是 `KnowledgeBaseActivity.kt` 里的 `private object KbDimens`——一家在用，写在哪家都行。
 * 向导 [com.lovebrain.app.ui.kb.OnboardingScreen] 搬进本包之后读它变成了**两家**，于是这张表
 * 跟着搬到它自己的那一格：值一个都没动，只是所有者从「某一张页面」换成「这一族页面」。
 * 同形先例见 `ui/panel/reply/ResultArea.kt` 的 `internal object ResultDimens`。
 */
internal object KbDimens {
    const val PRIMARY_ACTION_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP   // 新建/导入/完成大按钮高度
    const val EDIT_ICON_SIZE_DP = 14          // 重命名小铅笔图标
    const val ONBOARDING_SPINNER_SIZE_DP = 18 // 问卷生成中按钮内转圈尺寸
    const val PROGRESS_BAR_HEIGHT_DP = 4     // 问卷答题进度条高度
}
