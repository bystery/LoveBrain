package com.lovebrain.app.domain

import com.lovebrain.app.domain.CapturePolicy.Decision
import com.lovebrain.app.domain.CapturePolicy.Observation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3-05 裁决矩阵。
 *
 * 复核报告 §7.2 的结论是"XML 移除了 flagIncludeNotImportantViews 只是收窄，
 * 代码里没有 allowlist，只有脆弱 blocklist，未命中的任意 App 仍可进入捕获逻辑"。
 * 这组用例就是把"默认 fail-closed"钉成可执行的合同。
 */
class CapturePolicyTest {

    private fun observe(
        pkg: String,
        allowed: Set<String>,
        systemWindow: Boolean = false,
        passwordNode: Boolean = false,
        otpNode: Boolean = false
    ) = Observation(
        sourcePackage = pkg,
        allowedPackages = allowed,
        ownPackage = "com.lovebrain.app",
        windowIsSystemLevel = systemWindow,
        hasPasswordNode = passwordNode,
        hasVerificationCodeNode = otpNode
    )

    @Test
    fun `empty allowlist captures nothing - fail closed by default`() {
        assertEquals(
            Decision.Deny("allowlist_empty"),
            CapturePolicy.decide(observe("com.tencent.mm", emptySet()))
        )
    }

    @Test
    fun `apps outside the allowlist are never captured`() {
        assertEquals(
            Decision.Deny("not_in_allowlist"),
            CapturePolicy.decide(observe("com.any.random.app", setOf("com.tencent.mm")))
        )
    }

    @Test
    fun `an allowlisted chat app is captured`() {
        val allowed = setOf("com.tencent.mm")
        assertEquals(Decision.Allow, CapturePolicy.decide(observe("com.tencent.mm", allowed)))
    }

    @Test
    fun `own process events are ignored`() {
        assertEquals(
            Decision.Deny("own_process"),
            CapturePolicy.decide(observe("com.lovebrain.app", setOf("com.lovebrain.app")))
        )
    }

    /**
     * 关键回归：旧实现只有关键词 blocklist，
     * 所以"地区性银行 / 企业内聊 / 医疗"这些没命中关键词的包会正常被采集。
     * 新实现靠 allowlist 拦，用户没选就一定不采。
     */
    @Test
    fun `long-tail sensitive apps that a keyword blocklist would miss are denied`() {
        val sneaky = listOf(
            "com.hbjy.csbank.mobile",     // 地区性农商行，不含 "bank" 整段之外的常见关键词
            "corp.internal-im.chat",
            "com.yiyuan.appointment",     // 挂号 App，包名里没有 health/medical
            "com.example.embeddedbrowser"
        )
        for (pkg in sneaky) {
            assertEquals(
                "must deny $pkg because the user never allowlisted it",
                Decision.Deny("not_in_allowlist"),
                CapturePolicy.decide(observe(pkg, setOf("com.tencent.mm")))
            )
        }
    }

    @Test
    fun `even an allowlisted sensitive app gets a second refusal`() {
        // 用户把支付宝加进 allowlist，也仍然不采
        assertTrue(CapturePolicy.isSecondRejectApp("com.eg.android.AlipayGphone"))
        assertEquals(
            Decision.Deny("sensitive_app_in_allowlist"),
            CapturePolicy.decide(observe("com.eg.android.AlipayGphone", setOf("com.eg.android.AlipayGphone")))
        )
    }

    @Test
    fun `system windows are refused even for an allowlisted app`() {
        assertEquals(
            Decision.Deny("system_window"),
            CapturePolicy.decide(
                observe("com.tencent.mm", setOf("com.tencent.mm"), systemWindow = true)
            )
        )
    }

    @Test
    fun `password and verification-code nodes are refused even inside an allowlisted app`() {
        val allowed = setOf("com.tencent.mm")
        assertEquals(
            Decision.Deny("password_field"),
            CapturePolicy.decide(observe("com.tencent.mm", allowed, passwordNode = true))
        )
        assertEquals(
            Decision.Deny("verification_code_field"),
            CapturePolicy.decide(observe("com.tencent.mm", allowed, otpNode = true))
        )
    }

    /**
     * 二次拒绝的关键词只按包名**段**做前缀匹配，且泛化的短词（pay/otp/abc）根本不入表。
     *
     * 旧实现用整串 `pkgLower.contains("pay")`：`com.payne.chatapp` 会被当成支付 App，
     * 用户点了授权却静默不采——"看不见的拒绝"比多一个可选项危险得多。
     * 真正的边界是 allowlist，不靠这里猜包名。
     */
    @Test
    fun `second reject matches per package segment and does not over-match surnames`() {
        assertFalse(
            "Payne is a surname, not a payment app",
            CapturePolicy.isSecondRejectApp("com.payne.chatapp")
        )
        assertFalse(
            "a surname segment must not contaminate a later app segment either",
            CapturePolicy.isSecondRejectApp("com.example.payne.chat")
        )
        // 这些是真正需要二次拒绝的类型
        assertTrue(CapturePolicy.isSecondRejectApp("com.example.paymentstore"))
        assertTrue(CapturePolicy.isSecondRejectApp("com.bankofchina.app"))
        assertTrue(CapturePolicy.isSecondRejectApp("com.eg.android.AlipayGphone"))
        assertTrue(CapturePolicy.isSecondRejectApp("com.android.settings"))
        assertTrue(CapturePolicy.isSecondRejectApp("com.android.settings.intelligence"))
    }

    @Test
    fun `blank source package cannot be captured`() {
        assertEquals(
            Decision.Deny("no_source_package"),
            CapturePolicy.decide(observe("", setOf("com.tencent.mm")))
        )
    }

    @Test
    fun `credential node detection keys off input type not body text`() {
        // 正文里出现 "password" 不应该把正常聊天判成凭据页
        assertFalse(
            CapturePolicy.looksLikeCredentialNode("android.widget.TextView", false, false)
        )
        assertTrue(
            CapturePolicy.looksLikeCredentialNode("android.widget.EditText", false, true)
        )
        assertTrue(
            CapturePolicy.looksLikeCredentialNode("android.widget.EditText", true, false)
        )
    }
}
