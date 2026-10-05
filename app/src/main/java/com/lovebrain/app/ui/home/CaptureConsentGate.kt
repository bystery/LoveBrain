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
// 2. **同意与权限是两根独立的轴**（2026-10-06 改判，见 `captureSwitchGateStep` 上方）：
//    披露是"我要读你屏幕上的聊天内容"这句告知，**权限有没有都不会替它顶缺**。
//    旧形状写的"已授予就不骚扰"直接把这条法律闸废掉了——**在系统设置里手动开过无障碍的人
//    永远见不到披露**，而服务那边没有同意记录就一条都不抓（用户看到的是"开了却不捕获"）。
//    现在的"不骚扰"只有一种：这一版已经明确同意过、权限也给了 ⇒ 点开关就是开；
// 3. **没看见过这一版披露就不许开捕获**：只有用户明确按"同意并继续"才写同意记录；
//    按"取消"什么都不发生（不写记录、不跳设置、不改开关）；同意后没授予才跳系统设置；
// 4. **同一版披露只说一次**：披露文本是有版本号的（`CopyCaptureService.CURRENT_DISCLOSURE_VERSION`），
//    记在偏好里的那个号 >= 当前号 = 用户已经明确同意过这一版；此时未授予就直送系统设置，
//    不再把同一篇长文端第二遍——反复骚扰与静默开权限是两种同样的坏写法。
// 5. **开关"画的哪一档"与"用户想去哪一档"是两根轴**（2026-10-06 CAP3，见 [captureSwitchShowsOn]）：
//    画的是四件事实派生的**有效态**，落盘的是用户那一记**意图旗标**。
//    合成一根（拿有效态当意图回写）就会出现"用户拨一下开关，那一档自己弹回去"的新毛病。

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
 * 那扇披露弹窗是**谁**把它开出来的。
 *
 * 同一扇窗、同一篇长文，出去之后的那一步却有两件事，而且这两件事在"权限已经给了"这一格里
 * **长得一模一样、判起来完全相反**（2026-10-06 CAP3 的病灶）：
 * - [FromSwitch]：用户刚按过那颗开关，他要的是"开"。同意后必须把这一格**续跑**
 *   （权限已给 ⇒ 当场生效；权限没给 ⇒ 去系统设置，回来由 resume 那一拍接上）；
 * - [FromStatusRow]：状态卡那一行只是缺"同意记录"这一颗，其余三件本来就是真的。
 *   同意后**只补记录**——拨开关会当场把用户已经开着的捕获关掉，跳设置是骚扰。
 *
 * 所以这一格必须是**一记意图**（弹窗开着时才存在的那颗局部状态，随弹窗一起没了），
 * 不能拿 `accessibilityGranted` 当分流键：那颗旗标分不开"是谁开的"，
 * 把"开关那一路 + 权限已给"错分成"只补记录"，用户读到的就是
 * 「按长文开完无障碍回来，开关还是没开，再拨又跳去设置，卡好几次」。
 * **不落盘、不加偏好键**：偏好里那把同意尺仍是唯一的那一本账。
 */
internal enum class CaptureDisclosureEntry {
    /** 开关那一路：同意后要把"要开"这一格续跑 */
    FromSwitch,

    /** 状态卡那一行：同意后只补记录，不许拨开关、不许跳系统设置 */
    FromStatusRow
}

/**
 * 点那颗捕获开关时走哪一步。
 *
 * 反例（每一条都是一个能被用例钉住的坏实现）：
 * - 没授予就直接开捕获 + `startActivity`：披露整段被跳过 = 静默开权限；
 * - **已授予就把披露整段跳过**（2026-10-06 那条 P0 的原形）：同意记录永远写不进偏好，
 *   服务侧 CONSENT_PENDING 把每一颗事件丢掉 ⇒ 用户"开了却抓不到"，而且界面还说已开启；
 * - 同一版已经明确同意过还每次弹披露：法律长文反复端给用户，用户只会学会无脑点"同意"；
 * - 关掉捕获也被闸门挡住：想退出采集反而退不出去，那是比少说一句更坏的后果；
 * - 把"未授予"当成"已同意过"（默认 true）：那一格红得比什么都快。
 */
internal fun captureSwitchGateStep(
    targetEnabled: Boolean,
    accessibilityGranted: Boolean,
    disclosureConfirmed: Boolean
): CaptureGateStep = when {
    !targetEnabled -> CaptureGateStep.ApplySwitch
    // ⚠ 2026-10-06 顺序改判（用户 P0：「我选中了微信、手动开启了无障碍，还是无法捕获」）。
    // 旧形状把"已授予"排在"未同意"前面 ⇒ **自己在系统设置里手动开过无障碍的人，
    // 一辈子见不到这篇披露**：开关一拨就走 ApplySwitch，`accessibilityDisclosureVersion` 留在 0，
    // 而服务侧每一颗事件都被 `consentVersion < CURRENT_DISCLOSURE_VERSION` 判成
    // `disclosure_not_confirmed` 丢掉（`CopyCaptureService` 的 CONSENT_PENDING 那一格）。
    // 结果就是"界面说已开启、服务一条都不抓"——**授权状态不能替同意顶缺**：
    // 披露是采集面的法律闸，跟权限给没给是两根独立的轴。
    // 反例（回退成什么就红）：把这两行换回原顺序 ⇒ 下面那格的"开·已授予·未同意 = ShowDisclosure"当场红。
    !disclosureConfirmed -> CaptureGateStep.ShowDisclosure
    accessibilityGranted -> CaptureGateStep.ApplySwitch
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

// ═════════════════════════════════════════════════════════════
// CAP1（2026-10-06）：「捕获已开启」要说真话——四件判据唯一的一张派生表
// ═════════════════════════════════════════════════════════════
//
// 病灶（A1 取证 source-10 §3 候选①）：旧写法里"捕获已开启"只看开关偏好位 + 披露同意，
// **不看 allowlist 非空**，也不看悬浮窗在不在。而服务侧 `CapturePolicy.decide` 空集即全拒
// （`domain/CapturePolicy.kt` 的 `allowlist_empty`，那是对的隐私默认，保持不动）。
// 两本账一对不上，界面就绿、事件就全丢——用户看到的正是"显示已开启，但抓不到聊天内容"。
//
// 这一格把"运行中"钉成**四件都真**（权限 ∧ 开关 ∧ 披露 ∧ 范围），再加上候选③的投递条件
// （悬浮窗在）：任何一件缺了就念它自己的那一格，**绝不许借"运行中"的句子**。
// 状态语言只有 [LbRowState] 那一档（Ready / NotReady）+ 一行说明，不新增第四种状态件。

/** 「捕获现在到底算哪一格」——只有 [Running] 允许念"已开启" */
internal enum class CaptureTruthState {
    /** 系统位没给：无障碍未授予 */
    NoPermission,

    /** 偏好位没开：捕获总开关是关的 */
    CaptureSwitchOff,

    /** 开关与权限都在，但这一版披露还没明确同意——服务侧此刻每条事件都静默拦（CONSENT_PENDING） */
    DisclosurePending,

    /** 前三件都真，但 allowlist 是空的——服务侧此刻每条事件都 fail-closed 全拒（allowlist_empty） */
    ScopeNotSelected,

    /** 四件都真，但悬浮窗不在：长按复制到的内容会当场不记（候选③，不暂存、不补录、不自动拉起） */
    FloatingNotStarted,

    /** 权限 ∧ 开关 ∧ 披露 ∧ 范围 ∧ 悬浮窗全部为真——**唯一**允许念"已开启"的一格 */
    Running
}

/** 一次判定的全部输入：每颗各自只有一个真源，读取点见 `CaptureAppsScreen` 的那一批 IO 读数 */
internal data class CaptureTruthFacts(
    val accessibilityGranted: Boolean,
    val switchOn: Boolean,
    val disclosureConfirmed: Boolean,
    val scopeNonEmpty: Boolean,
    val floatingRunning: Boolean
)

/**
 * 四件判据 → 状态。**顺序是判据的一部分**：先报"地基缺的那一格"（权限），
 * 再报"意愿"（开关）、"合法闸"（披露）、"范围"（allowlist）、"投递闸"（悬浮窗）。
 *
 * 反例（每条都是一个能被用例钉住的坏实现）：
 * - 把 `scopeNonEmpty` 从判据里漏掉（＝CAP1 之前的旧账）→ 空 allowlist 那格红；
 * - `Running` 的门槛写成三件（漏披露）→ 服务在 CONSENT_PENDING 全拒时界面却念"已开启"；
 * - 拿 `switchOn` 单独当"已开启"（＝guideFacts 旧病的另一半）→ 未授予那格红。
 */
internal fun captureTruthOf(f: CaptureTruthFacts): CaptureTruthState = when {
    !f.accessibilityGranted -> CaptureTruthState.NoPermission
    !f.switchOn -> CaptureTruthState.CaptureSwitchOff
    !f.disclosureConfirmed -> CaptureTruthState.DisclosurePending
    !f.scopeNonEmpty -> CaptureTruthState.ScopeNotSelected
    !f.floatingRunning -> CaptureTruthState.FloatingNotStarted
    else -> CaptureTruthState.Running
}

/**
 * 那颗开关**画**哪一档：四件事实（权限 ∧ 意图 ∧ 披露 ∧ 范围）全真才画"拨开"。
 *
 * 病灶（2026-10-06 CAP3，用户原话「第一次进入消息捕获页面，开关是打开的」）：显示以前直读
 * `captureEnabled` 那颗偏好位，而它当时**默认就是 true**（默认值本身已由 CAP4 翻关，另一位的账）——
 * 第一次进来什么都没给，开关却摆成开的，用户只能"先关掉再拨开"才触发那篇长文。
 * 这一格把显示接回 [captureTruthOf] 同一张表，**不新写第二颗旗标**（第二本账是明令禁的）：
 * 它吃的就是页面刚读齐的那五颗读数，与那颗默认值是几号**再无关系**。
 *
 * 第五次读数（悬浮窗）**不参与这一格**：投递闸只改状态那一行的句子，不改开关的脸——
 * 那一格里用户自己的那一档（意图旗标）是开着的，把显示跟着悬浮窗掉下去，
 * 就等于让他"拨一下开"反而把意图写成关（下面 [captureIntentNeedsWrite] 那格钉住这件事）。
 */
internal fun captureSwitchShowsOn(f: CaptureTruthFacts): Boolean = when (captureTruthOf(f)) {
    CaptureTruthState.Running, CaptureTruthState.FloatingNotStarted -> true
    else -> false
}

/**
 * 闸门判出 `ApplySwitch` 之后，**到底要不要落那一笔**：只有"用户要去的那一档"与
 * 现在那颗意图旗标不一致时才写（写口只有 `toggleCapture()` 这一颗，它是翻、不是设）。
 *
 * 这一格是"显示 ≠ 意图"必然带出来的另一半。反例（无条件翻）：
 * 权限、披露、意图旗标三样都齐、只差没勾 App 时，开关**画的是关**，用户照直觉拨一下"开"
 * ⇒ 闸门判 `ApplySwitch` ⇒ 无条件翻就把他的意图从"开"写成"关"——他拨的这一下反而把捕获关了。
 * 反例（反过来把显示当意图回写）：那才是"拨一下自己弹回去"的新毛病。
 */
internal fun captureIntentNeedsWrite(currentIntent: Boolean, targetIntent: Boolean): Boolean =
    currentIntent != targetIntent
