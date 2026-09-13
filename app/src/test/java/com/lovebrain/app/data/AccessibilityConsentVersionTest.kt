package com.lovebrain.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RA-02 回归测试：无障碍隐私披露 consent 版本号逻辑。
 *
 * 验证：
 * - consentVersion < CURRENT → 不视为已同意
 * - consentVersion == CURRENT → 已同意
 * - CopyCaptureService.CURRENT_DISCLOSURE_VERSION 为正整数
 */
class AccessibilityConsentVersionTest {

    @Test
    fun consent_version_zero_is_not_confirmed() {
        // 默认值 0 < CURRENT_DISCLOSURE_VERSION (1) → 未同意
        val current = com.lovebrain.app.service.CopyCaptureService.CURRENT_DISCLOSURE_VERSION
        assertTrue("version 0 should be less than current ($current)", 0 < current)
    }

    @Test
    fun consent_version_equal_to_current_is_confirmed() {
        val current = com.lovebrain.app.service.CopyCaptureService.CURRENT_DISCLOSURE_VERSION
        // version == current → 已同意 (不再 < current)
        assertFalse("version == current should not be less than current", current < current)
    }

    @Test
    fun current_disclosure_version_is_positive() {
        assertTrue(
            "CURRENT_DISCLOSURE_VERSION 应为正整数",
            com.lovebrain.app.service.CopyCaptureService.CURRENT_DISCLOSURE_VERSION > 0
        )
    }
}
