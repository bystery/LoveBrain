package com.lovebrain.app.domain.port

import android.content.Context
import android.content.SharedPreferences
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.SuggestionCache
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SettingsStorePort] 的合同：谁实现偏好/凭据存储，谁就必须给出同样的可观察行为。
 *
 * 沿用仓库既有合同打法（[AiGatewayContract] / [KbArchivePortContract]）——同一批格子对
 * **生产侧**（[ProductionSettingsStoreContractTest]：真 [SecurePrefs]，只是把底层的
 * EncryptedSharedPreferences 换成一份 JVM 可跑的 [FakeSharedPreferences]，其余判据一字不改）
 * 与 **fake 侧**（[InMemorySettingsStoreContractTest]：纯内存 [InMemorySettingsStore]）各跑一遍。
 * 于是"内存 fake 有没有把越界钳制、空态默认、同名清理这些做对"第一次变成可被同一份合同咬住的事。
 */
abstract class SettingsStorePortContract {

    /** 每次给一个全新的空存储（生产侧＝新 FakePrefs，fake 侧＝新 map） */
    protected abstract fun newStore(): SettingsStorePort

    @Test
    fun `an empty store reports the documented defaults, not invented values`() {
        val s = newStore()
        assertEquals(0, s.thinkingMode)
        assertEquals(0, s.outputMode)
        assertEquals(0, s.panelMode)
        assertEquals("", s.counselingDraft)
        assertNull(s.activeTicketId)
        assertNull(s.lastKbEditFile)
        assertEquals(0, s.totalGenerateCount)
        assertEquals(0.0, s.totalCostYuan, 0.0)
        assertEquals(0, s.totalCopyCount)
        assertEquals(0, s.totalAdoptCount)
        assertEquals(0, s.totalRewriteCount)
        assertEquals(0, s.accessibilityDisclosureVersion)
        assertFalse("未确认过引导就不能算完成", s.hasCompletedOnboarding)
        assertTrue("抓取开关默认开", s.captureEnabled)
        assertEquals(emptyList<ProviderTicket>(), s.getWorkerTickets())
        assertEquals(emptySet<String>(), s.captureAllowedPackages)
        assertNull(s.loadCounselingResult())
        assertNull(s.loadCounselingHistory())
        assertNull(s.loadSuggestion())
        assertNull(s.loadTodayCost())
        assertNull("没存过 Key 的工单读回 null，而不是空串冒充已配置", s.getWorkerApiKey("nope"))
    }

    @Test
    fun `thinking mode is clamped into zero and one on both write and read`() {
        val s = newStore()
        s.thinkingMode = 5
        assertEquals("写越界钳到 1", 1, s.thinkingMode)
        s.thinkingMode = -2
        assertEquals("写越界钳到 0", 0, s.thinkingMode)
        s.thinkingMode = 1
        assertEquals(1, s.thinkingMode)
    }

    @Test
    fun `plain settings round-trip`() {
        val s = newStore()
        s.outputMode = 1
        s.panelMode = 1
        s.counselingDraft = "她：下周考雅思"
        s.lastKbEditFile = "moment/plan.md"
        s.totalGenerateCount = 12
        s.totalCostYuan = 3.25
        s.hasCompletedOnboarding = true
        s.captureEnabled = false
        s.accessibilityDisclosureVersion = 2

        assertEquals(1, s.outputMode)
        assertEquals(1, s.panelMode)
        assertEquals("她：下周考雅思", s.counselingDraft)
        assertEquals("moment/plan.md", s.lastKbEditFile)
        assertEquals(12, s.totalGenerateCount)
        assertEquals(3.25, s.totalCostYuan, 0.0)
        assertTrue(s.hasCompletedOnboarding)
        assertFalse(s.captureEnabled)
        assertEquals(2, s.accessibilityDisclosureVersion)
    }

    @Test
    fun `worker tickets round-trip without a migration rewrite`() {
        val s = newStore()
        val t = ProviderTicket(
            id = "t1", name = "工单", baseUrl = "https://example.test/v1",
            model = "m1", models = listOf("m1"), thinkingMode = 0
        )
        s.setWorkerTickets(listOf(t))
        assertEquals(listOf(t), s.getWorkerTickets())

        s.setWorkerTickets(emptyList())
        assertEquals(emptyList<ProviderTicket>(), s.getWorkerTickets())
    }

    @Test
    fun `active ticket id round-trips and can be cleared`() {
        val s = newStore()
        s.activeTicketId = "t1"
        assertEquals("t1", s.activeTicketId)
        s.activeTicketId = null
        assertNull(s.activeTicketId)
    }

    @Test
    fun `api key save read and delete stay symmetric`() {
        val s = newStore()
        s.saveWorkerApiKey("t1", "sk-secret")
        assertEquals("sk-secret", s.getWorkerApiKey("t1"))
        s.deleteWorkerApiKey("t1")
        assertNull("删除后必须读回 null（防孤立残留冒充已配置）", s.getWorkerApiKey("t1"))
    }

    @Test
    fun `capture allowlist round-trips as a set`() {
        val s = newStore()
        s.captureAllowedPackages = setOf("com.a", "com.b")
        assertEquals(setOf("com.a", "com.b"), s.captureAllowedPackages)
    }

    @Test
    fun `today cost round-trips date and amount`() {
        val s = newStore()
        s.saveTodayCost("2026-09-24", 1.5)
        assertEquals("2026-09-24" to 1.5, s.loadTodayCost())
    }

    @Test
    fun `suggestion cache round-trips all five fields`() {
        val s = newStore()
        s.saveSuggestion("J", "2026-09-24", "kb1", "fp", "v2")
        assertEquals(SuggestionCache("J", "2026-09-24", "kb1", "fp", "v2"), s.loadSuggestion())
    }

    @Test
    fun `counseling result and history clear independently`() {
        val s = newStore()
        s.saveCounselingResult("R")
        s.saveCounselingHistory("H")
        assertEquals("R", s.loadCounselingResult())
        assertEquals("H", s.loadCounselingHistory())
        s.clearCounselingResult()
        assertNull(s.loadCounselingResult())
        assertEquals("H", s.loadCounselingHistory())
        s.clearCounselingHistory()
        assertNull(s.loadCounselingHistory())
    }
}

/**
 * 一份 JVM 可跑的 [SharedPreferences]：底层就是 map，读时尊重传入的默认值。
 * 只为把真 [SecurePrefs] 的落盘/降级判据搬到无 Android 运行时的环境里验，不改任何业务逻辑。
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = LinkedHashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)
    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        // 一份快照：apply/commit 时用 pending 整体替换 data，这样 remove()/clear() 才会真的生效
        private val pending = LinkedHashMap<String, Any?>(data)
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putStringSet(key: String, value: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { pending.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { pending.clear() }
        override fun commit(): Boolean = flush()
        override fun apply() { flush() }
        private fun flush(): Boolean {
            data.clear()
            data.putAll(pending)
            return true
        }
    }
}

/** 生产侧：真 SecurePrefs，底层换成 FakeSharedPreferences（走 Keystore 不可用的降级分支） */
class ProductionSettingsStoreContractTest : SettingsStorePortContract() {
    override fun newStore(): SettingsStorePort {
        val ctx = mockk<Context>()
        every { ctx.getSharedPreferences(any(), any()) } returns FakeSharedPreferences()
        return SecurePrefs(ctx)
    }
}

/**
 * fake 侧：纯内存实现，语义与生产逐条对齐——越界钳制、空态默认、同名清理都得自己做对。
 */
class InMemorySettingsStore : SettingsStorePort {
    override var thinkingMode: Int = 0
        set(value) { field = value.coerceIn(0, 1) }
    override var outputMode: Int = 0
    override var panelMode: Int = 0
    override var counselingDraft: String = ""

    private var counselingResult: String? = null
    private var counselingHistory: String? = null
    override fun saveCounselingResult(json: String) { counselingResult = json }
    override fun loadCounselingResult(): String? = counselingResult
    override fun clearCounselingResult() { counselingResult = null }
    override fun saveCounselingHistory(json: String) { counselingHistory = json }
    override fun loadCounselingHistory(): String? = counselingHistory
    override fun clearCounselingHistory() { counselingHistory = null }

    private var suggestion: SuggestionCache? = null
    override fun saveSuggestion(json: String, dateStr: String, kbId: String, contextFingerprint: String, promptVersion: String) {
        suggestion = SuggestionCache(json, dateStr, kbId, contextFingerprint, promptVersion)
    }
    override fun loadSuggestion(): SuggestionCache? = suggestion

    private var todayCost: Pair<String, Double>? = null
    override fun saveTodayCost(dateStr: String, yuan: Double) { todayCost = dateStr to yuan }
    override fun loadTodayCost(): Pair<String, Double>? = todayCost

    private var tickets: List<ProviderTicket> = emptyList()
    override fun getWorkerTickets(): List<ProviderTicket> = tickets
    override fun setWorkerTickets(tickets: List<ProviderTicket>) { this.tickets = tickets }
    override var activeTicketId: String? = null
    private val apiKeys = mutableMapOf<String, String>()
    override fun getWorkerApiKey(ticketId: String): String? = apiKeys[ticketId]
    override fun saveWorkerApiKey(ticketId: String, apiKey: String) { apiKeys[ticketId] = apiKey }
    override fun deleteWorkerApiKey(ticketId: String) { apiKeys.remove(ticketId) }

    override var totalGenerateCount: Int = 0
    override var totalCostYuan: Double = 0.0
    override var totalCopyCount: Int = 0
    override var totalAdoptCount: Int = 0
    override var totalRewriteCount: Int = 0

    override var hasCompletedOnboarding: Boolean = false
    override var captureEnabled: Boolean = true
    override var accessibilityDisclosureVersion: Int = 0
    override var captureAllowedPackages: Set<String> = emptySet()
    override var lastKbEditFile: String? = null
}

/** fake 必须通过同一套合同 */
class InMemorySettingsStoreContractTest : SettingsStorePortContract() {
    override fun newStore(): SettingsStorePort = InMemorySettingsStore()
}
