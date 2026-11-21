package com.lovebrain.app.viewmodel

import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.service.CopyCaptureService
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 采集范围变更 → 披露重新置为待确认（审计项 5）。
 *
 * 披露/同意状态本来就有（`isAccessibilityDisclosureConfirmed` 读的那颗版本号，服务侧同一条判据），
 * 但 allowlist 变更此前只写一行日志：用户加了新 App 之后，旧同意被当成覆盖了新范围。
 * 现在变更（且真的变了）就落回 0，判定交给既有的 `consent_pending` 那条判据与既有披露界面。
 *
 * 反例：把 reset 只写成日志 → 第一格红；无变更也 reset（每次拨开关都弹披露）→ `no-op` 格红。
 */
class SetupCaptureScopeDisclosureTest {

    private fun vmWith(store: InMemorySettingsStore) = SetupViewModel(
        store,
        mockk<DeepSeekRepository>(relaxed = true),
        null,
        null
    )

    private fun storeConfirmed(): InMemorySettingsStore = InMemorySettingsStore().apply {
        accessibilityDisclosureVersion = CopyCaptureService.CURRENT_DISCLOSURE_VERSION
    }

    @Test
    fun `adding a capture target returns the disclosure to pending`() {
        val store = storeConfirmed()
        val vm = vmWith(store)
        assertTrue("前置：已同意当前披露", vm.isAccessibilityDisclosureConfirmed())

        vm.setCaptureAllowed("com.example.chat", true)

        assertFalse("范围变了，旧同意不覆盖新范围", vm.isAccessibilityDisclosureConfirmed())
        assertEquals(0, store.accessibilityDisclosureVersion)
        assertEquals(setOf("com.example.chat"), store.captureAllowedPackages)
    }

    @Test
    fun `removing a capture target also returns to pending`() {
        val store = storeConfirmed().apply {
            captureAllowedPackages = setOf("com.example.chat", "com.example.other")
        }
        val vm = vmWith(store)

        vm.setCaptureAllowed("com.example.chat", false)

        assertFalse(vm.isAccessibilityDisclosureConfirmed())
        assertEquals(setOf("com.example.other"), store.captureAllowedPackages)
    }

    @Test
    fun `re-selecting the same target does not nag again`() {
        val store = storeConfirmed().apply { captureAllowedPackages = setOf("com.example.chat") }
        val vm = vmWith(store)

        vm.setCaptureAllowed("com.example.chat", true)

        assertTrue("范围没变，不该把已同意的人打回待披露", vm.isAccessibilityDisclosureConfirmed())
        assertEquals(
            CopyCaptureService.CURRENT_DISCLOSURE_VERSION,
            store.accessibilityDisclosureVersion
        )
    }

    @Test
    fun `confirming after the change restores consent`() {
        val store = storeConfirmed()
        val vm = vmWith(store)
        vm.setCaptureAllowed("com.example.chat", true)

        vm.confirmAccessibilityDisclosure()

        assertTrue(vm.isAccessibilityDisclosureConfirmed())
    }
}
