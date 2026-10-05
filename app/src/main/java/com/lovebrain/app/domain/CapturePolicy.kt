package com.lovebrain.app.domain

/**
 * 无障碍抓取裁决策略——默认 fail-closed 的 allowlist。
 *
 * ## 为什么重写
 * 上一版只在 XML 里移除了 `flagIncludeNotImportantViews`，注释声称"allowlist 应在代码层实现"，
 * 但生产代码里实际只有一小撮固定包名前缀 + `bank/pay/wallet/password/...` 关键词 blocklist，
 * **没命中 blocklist 的任意 App 仍然会被抓取**。blocklist 永远盖不住长尾：
 * 地区性银行、企业内聊、医疗、浏览器 WebView、密码输入页都不在那些关键词里。
 * README 写"最小化采集"，代码边界却不是那样，这就是文档不实。
 *
 * ## 现在的规则（顺序即优先级）
 * 1. 自己的进程 → 忽略。
 * 2. allowlist 为空 → 什么都不抓（默认关闭，用户必须显式选 App）。
 * 3. 包名不在 allowlist → 不抓。
 * 4. 即使命中 allowlist，仍做二次拒绝：
 *    - 系统级窗口类型（通知栏/锁屏/系统弹窗）
 *    - 密码/验证码输入节点
 *    - 命中敏感窗口的 App（浏览器 WebView、支付、密码管理器等）
 *
 * 本对象是纯函数，不依赖 Android framework，便于把整张裁决矩阵写成单元测试。
 */
object CapturePolicy {

    /** 单条裁决结果，携带可记录的原因（不记录任何正文） */
    sealed interface Decision {
        data object Allow : Decision
        data class Deny(val reason: String) : Decision
    }

    /** 一次事件里代码能观察到的最小事实 */
    data class Observation(
        val sourcePackage: String,
        val allowedPackages: Set<String>,
        val ownPackage: String,
        val windowIsSystemLevel: Boolean = false,
        val hasPasswordNode: Boolean = false,
        val hasVerificationCodeNode: Boolean = false
    )

    /**
     * 即使用户把某个 App 加进 allowlist，仍然二次拒绝的 App 类别。
     *
     * 覆盖"聊天 App 里内嵌浏览器打开的支付/登录页"这类越界场景：
     * 包名是用户选的微信，但窗口其实是银行 H5。
     */
    /**
     * 二次拒绝关键词。
     *
     * 只收"整段前缀撞上真实人名/普通词的概率足够低"的词。
     * 3~4 字母的泛化词（pay / otp / abc / cmb / boc …）刻意不放进来：
     * `com.payne.chatapp` 会被 "pay" 误杀成支付 App，用户点了授权却静默不采，
     * 这种"看不见的拒绝"比多一个可选项危险得多。
     * 真正的边界是 allowlist —— 没点选的 App 一律不采，不依赖这里猜。
     */
    private val SECOND_REJECT_KEYWORDS = listOf(
        // 支付 / 银行 / 证券
        "bank", "banking", "wallet", "finance", "securities", "securities", "trading",
        "alipay", "wechatpay", "unionpay", "paypal", "stripe", "payment", "payments",
        // 凭据
        "password", "passbook", "1password", "lastpass", "bitwarden", "keepass", "keeper",
        "authenticator",
        // 浏览器与 WebView 宿主（会话里可能是任何站点）
        "chrome", "firefox", "browser", "webview",
        // 医疗与企业身份
        "medical", "hospital", "okta", "adfs"
    )

    /** 系统组件包——精确前缀，不参与关键词猜测 */
    private val SECOND_REJECT_EXACT_PREFIXES = listOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.keyguard",
        "com.android.intentresolver"
    )

    fun decide(obs: Observation): Decision {
        val pkg = obs.sourcePackage
        if (pkg.isBlank()) return Decision.Deny("no_source_package")
        if (pkg == obs.ownPackage) return Decision.Deny("own_process")

        // 默认 fail-closed：没选 App 就一个都不抓
        if (obs.allowedPackages.isEmpty()) return Decision.Deny("allowlist_empty")
        if (pkg !in obs.allowedPackages) return Decision.Deny("not_in_allowlist")

        if (obs.windowIsSystemLevel) return Decision.Deny("system_window")
        if (obs.hasPasswordNode) return Decision.Deny("password_field")
        if (obs.hasVerificationCodeNode) return Decision.Deny("verification_code_field")
        if (isSecondRejectApp(pkg)) return Decision.Deny("sensitive_app_in_allowlist")

        return Decision.Allow
    }

    /** allowlist 里允许出现的候选——用于设置页展示，不做产品级枚举限制 */
    fun isSecondRejectApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (SECOND_REJECT_EXACT_PREFIXES.any { lower == it || lower.startsWith("$it.") }) return true
        // 分段匹配：只有当整个包名段命中关键词才算，
        // 否则 "com.xxx.payne.chat" 这种姓氏会被 "pay" 误杀。
        val segments = lower.split('.')
        return segments.any { seg -> SECOND_REJECT_KEYWORDS.any { seg == it || seg.startsWith("$it") } }
    }

    /**
     * 是否应当作为"疑似凭据输入节点"二次拒绝。
     *
     * 判定完全基于 AccessibilityNodeInfo 的布尔位与 className，不看正文，
     * 因此不会因为正文里出现"password"就误杀正常聊天。
     */
    fun looksLikeCredentialNode(className: String?, isPassword: Boolean, textHintsPasswordInputType: Boolean): Boolean {
        if (isPassword) return true
        if (!textHintsPasswordInputType) return false
        val cn = className?.lowercase().orEmpty()
        return cn.contains("edittext") || cn.contains("textfield")
    }

    /**
     * 事件送达面：把用户勾选的 allowlist 翻成 `ServiceInfo.packageNames` 需要的数组。
     *
     * 这是 CAP2（档位 D）里"代码层声明范围"那颗唯一真源——只吃 [allowedPackages]
     * （= `SecurePrefs.captureAllowedPackages`），不读 Android、不猜样例包名、更不把微信/QQ
     * 之类写死进来。XML 里没有 `packageNames` 声明，收不收、收谁全看这一颗算出来的数组。
     *
     * 纯函数，方便把形状钉成单测（给一份包名集合 ⇒ 断言数组恰是那一份、排序确定；
     * 空集合 ⇒ 空数组而不是 null）。
     *
     * 空集合返回空数组（非 null）的语义：空数组交给系统＝"没声明具体包"＝事件全投，
     * 但**采集边界不因此放开**——`decide()` 的 fail-closed（`allowlist_empty` → 全拒）仍然兜住每一条，
     * 所以"设了包名"绝不等于"收全部然后开抓"。
     *
     * 排序：`Set` 的迭代序不定，回写给系统的数组要可比对、可断言，故按字典序固定。
     */
    fun packageNamesFor(allowedPackages: Set<String>): Array<String> =
        allowedPackages.filter { it.isNotBlank() }.sorted().toTypedArray()
}
