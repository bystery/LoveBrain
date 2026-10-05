package com.lovebrain.app.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings

// ═════════════════════════════════════════════════════════════
// 消息捕获页的**授权披露闸门**：把"披露 → 明确同意 → 系统设置"这条链收成一处纯判据
// ═════════════════════════════════════════════════════════════
//
// 为什么这件事要能被单独判：首页重做删掉"服务设置段"时，`AccessibilityDisclosureDialog`
// 整个调用点被一起删掉了（全仓 grep 只剩它自己的声明与异形账本里那一行登记），
// 而"未授予时先弹披露、明确同意才进系统设置"这条法定/平台必需的告知就此断链——
// 断链的下一种写法就是"点开关直接 startActivity 去系统设置"，也就是**静默开权限**。
// 所以这里把格子写成能摆出来的样子，页面只负责把它们接起来：
//
// 1. **关掉永远立刻生效**：退出采集不需要任何前置告知；
// 2. **已授予不骚扰**：无障碍权限已经在系统里给了的，点开关就是开，不弹披露也不跳设置；
// 3. **未授予必须先看见披露**：只有用户明确按"同意并继续"才写同意记录、才跳系统设置；
//    按"取消"什么都不发生（不写记录、不跳设置、不改开关）；
// 4. **同一版披露只说一次**：披露文本是有版本号的（`CopyCaptureService.CURRENT_DISCLOSURE_VERSION`），
//    记在偏好里的那个号 >= 当前号 = 用户已经明确同意过这一版；此时未授予就直送系统设置，
//    不再把同一篇长文端第二遍——反复骚扰与静默开权限是两种同样的坏写法。

/** 闸门判完给出的一步动作。只说这一格该做什么，不替页面写结论 */
internal enum class CaptureGateStep {
    /** 直接生效：开（披露与权限都已就位）或关（退出采集不设门槛） */
    ApplySwitch,

    /** 先弹披露：这一步**不许**同时跳系统设置、也不许同时改开关 */
    ShowDisclosure,

    /** 用户已明确同意过这一版披露，但系统权限还没给 → 送去系统设置 */
    OpenAccessibilitySettings
}

/**
 * 点那颗捕获开关时走哪一步。
 *
 * 反例（每一条都是一个能被用例钉住的坏实现）：
 * - 没授予就直接开捕获 + `startActivity`：披露整段被跳过 = 静默开权限；
 * - 已授予还每次弹披露：同一篇法律长文反复端给用户，用户只会学会无脑点"同意"；
 * - 关掉捕获也被闸门挡住：想退出采集反而退不出去，那是比少说一句更坏的后果；
 * - 把"未授予"当成"已同意过"（默认 true）：那一格红得比什么都快。
 */
internal fun captureSwitchGateStep(
    targetEnabled: Boolean,
    accessibilityGranted: Boolean,
    disclosureConfirmed: Boolean
): CaptureGateStep = when {
    !targetEnabled -> CaptureGateStep.ApplySwitch
    accessibilityGranted -> CaptureGateStep.ApplySwitch
    !disclosureConfirmed -> CaptureGateStep.ShowDisclosure
    else -> CaptureGateStep.OpenAccessibilitySettings
}

/**
 * 用户在披露弹窗上按了"同意并继续"之后走哪一步。
 *
 * **调用顺序是承重的**：同意记录必须先写下来，这一步才允许发生——否则跳到系统设置里给完权限，
 * 服务那边 `consentVersion < CURRENT_DISCLOSURE_VERSION` 仍然把正文挡在门外
 * （`CopyCaptureService` 的 consPending 那一格），用户拿到的是"授权了却不工作"。
 *
 * 反例：同意后不管授予没有都开捕获 ⇒ 未授予时开关亮着、服务却根本没跑；
 * 反例：同意后仍然只弹窗不落地 ⇒ 用户点了同意却什么都没发生，第二次还是同一篇长文。
 */
internal fun disclosureAgreedGateStep(accessibilityGranted: Boolean): CaptureGateStep =
    if (accessibilityGranted) CaptureGateStep.ApplySwitch else CaptureGateStep.OpenAccessibilitySettings

/**
 * 打开系统的无障碍设置页——这条链的**唯一**出口，页面不许再各自 `startActivity` 一遍。
 *
 * 只交一个 `ACTION_ACCESSIBILITY_SETTINGS` 的 Intent：不"顺手把服务打开"（系统也不允许应用
 * 自己写 `Settings.Secure` 的无障碍名单，能做的只有把人送到那一页）。
 * 读不出来 / 起不动时不吞异常也不假装成功：这一格的失败由调用方那颗按钮自己承担，
 * 但**绝不**因为"起不动"就退回"直接改开关"那条路——那正是静默开权限的另一种写法。
 */
internal fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

/**
 * 这一屏的自动化锚点：披露链的判据是**动作顺序**（谁先谁后、有没有发生），
 * 拿中文当锚点的话改一句文案就把这条法律链的守卫弄红，而链一个字没动。
 * 与 [LbHomeTags] 同一口径（首页那一族另写一处）。
 */
internal object LbCaptureTags {
    /** 披露弹窗本体 */
    const val DISCLOSURE = "lb_capture_disclosure"

    /** 捕获总开关那一颗卡 */
    const val STATUS_CARD = "lb_capture_status_card"
}
