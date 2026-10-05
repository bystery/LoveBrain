package com.lovebrain.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `resolveGuideCursor` 的判据（基线 v1 §6.8 / 原始第 14 条）。
 *
 * 这条链的**病灶形状**是"点了去配置就把整段引导 complete 掉"——所以每一格都用
 * "派生事实"当输入，不许有任何一格是"用户按过某颗按钮"。
 *
 * 反例（把这版实现改回旧行为时谁会红，逐条写死在这里）：
 * - 把 `providerReady` 当"点过那颗按钮就算"（旧 `completeOnboarding()`）→ ②③④⑥ 全红；
 * - 加齐三步还继续画罩子 → ⑤ 红；
 * - "稍后"之后被追着弹罩子 → ⑥ 红；
 * - 权限被撤销还停在 DONE → ⑦ 红；
 * - 脏游标值抛异常 → ⑧ 红。
 */
class GuideCursorResolveTest {

    private fun facts(
        intro: Boolean = true,
        provider: Boolean = true,
        a11y: Boolean = true,
        capture: Boolean = true
    ) = SetupViewModel.GuideFacts(
        introSeen = intro,
        providerReady = provider,
        accessibilityGranted = a11y,
        captureOn = capture
    )

    @Test
    fun `intro not seen keeps the cursor off the overlay even when everything is configured`() {
        assertEquals(
            GuideCursor.NONE,
            resolveGuideCursor(GuideCursor.PROVIDER, facts(intro = false)),
            "介绍没看过就不该画罩子——游标指向哪一格都不算数"
        )
    }

    @Test
    fun `missing provider points at the provider step`() {
        assertEquals(
            GuideCursor.PROVIDER,
            resolveGuideCursor(GuideCursor.NONE, facts(provider = false)),
            "唯一缺供应商时只能指向供应商，不能被别的已完成步骤顶掉"
        )
    }

    @Test
    fun `provider ready but no accessibility points at the permission step`() {
        assertEquals(
            GuideCursor.ACCESSIBILITY,
            resolveGuideCursor(GuideCursor.PROVIDER, facts(a11y = false)),
            "配好供应商之后必须继续往下走一格，不能停在 PROVIDER 反复指同一格"
        )
    }

    @Test
    fun `permission granted but capture off points at the capture switch`() {
        assertEquals(
            GuideCursor.CAPTURE,
            resolveGuideCursor(GuideCursor.ACCESSIBILITY, facts(capture = false)),
            "只拨开关不算开启：captureOn 需要开关与披露同意同时为真（由 guideFacts 保证）"
        )
    }

    @Test
    fun `all three facts met closes the guide`() {
        assertEquals(
            GuideCursor.DONE,
            resolveGuideCursor(GuideCursor.CAPTURE, facts()),
            "三步派生事实都成立就该 DONE，不需要用户再点任何一颗按钮"
        )
    }

    @Test
    fun `deferred stays deferred while something is still missing`() {
        assertEquals(
            GuideCursor.DEFERRED_TO_HINT,
            resolveGuideCursor(GuideCursor.DEFERRED_TO_HINT, facts(provider = false)),
            "按过稍后不许被追着弹罩子"
        )
        assertEquals(
            GuideCursor.DONE,
            resolveGuideCursor(GuideCursor.DEFERRED_TO_HINT, facts()),
            "稍后之后自己配齐了也要 DONE，黄字行随之消失"
        )
    }

    @Test
    fun `revoked permission walks the cursor back to the unmet step`() {
        assertEquals(
            GuideCursor.ACCESSIBILITY,
            resolveGuideCursor(GuideCursor.DONE, facts(a11y = false)),
            "权限事后被撤销时，已存储的 DONE 不能把用户挡在外面"
        )
    }

    @Test
    fun `dirty stored cursor reads as none instead of throwing`() {
        assertEquals(GuideCursor.NONE, GuideCursor.from(null), "空串/没写过 = NONE")
        assertEquals(GuideCursor.NONE, GuideCursor.from(""), "没写过 = NONE")
        assertEquals(
            GuideCursor.NONE,
            GuideCursor.from("SOMETHING_OLD_VERSION_WROTE"),
            "脏值必须落回 NONE，读盘不能抛——抛了首页就起不来"
        )
        assertEquals(GuideCursor.CAPTURE, GuideCursor.from("CAPTURE"))
    }
}
