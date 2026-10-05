package com.lovebrain.app.domain.port

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.service.CopyCaptureService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
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
 *
 * CAP4（2026-10-06）一处合同公开重写：`抓取开关默认开` 那格改钉 fail-closed——旧默认被用户原话
 * 否决（隐私能力不许默认开），按 TEAM_RULES §1 记在这里而不是静默改。开关的**一次性归一**是
 * 生产侧构造期行为，fake 没有"盘上键存不存在"这一层，因此单独由
 * [SecurePrefsCaptureEnabledReconciliationTest] 钉在生产侧，不塞进合同抽象类逼 fake 演第二本账。
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
        // CAP4（2026-10-06）：这一格旧版钉的是"抓取开关默认开"，那条已被用户原话
        // （"现在你的默认刚打开，你的开关就是拨开的"）否决——隐私能力不许默认开。
        // 按 TEAM_RULES §1 依新形状重写，不删格；老安装升级不断捕获归构造期一次性归一管
        // （`SecurePrefsCaptureEnabledReconciliationTest` 那一族），不靠这颗默认值兜。
        assertFalse("没写过这颗键 = 用户从没要过捕获 ⇒ 读出来必须是关（fail-closed）", s.captureEnabled)
        assertEquals(emptyList<ProviderTicket>(), s.getWorkerTickets())
        assertEquals(emptySet<String>(), s.captureAllowedPackages)
        assertNull(s.loadCounselingResult())
        assertNull(s.loadCounselingHistory())
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
        s.captureEnabled = true
        s.accessibilityDisclosureVersion = 2

        assertEquals(1, s.outputMode)
        assertEquals(1, s.panelMode)
        assertEquals("她：下周考雅思", s.counselingDraft)
        assertEquals("moment/plan.md", s.lastKbEditFile)
        assertEquals(12, s.totalGenerateCount)
        assertEquals(3.25, s.totalCostYuan, 0.0)
        assertTrue(s.hasCompletedOnboarding)
        // CAP4：默认翻成关之后，往返格必须两侧都过——先写开读回开（防"getter 恒 false"把
        // fail-closed 做坏成"永远关"），再写关读回关。
        assertTrue("显式写开必须原样读回：fail-closed 改的是默认，不是写通路", s.captureEnabled)
        s.captureEnabled = false
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
 *
 * CAP4（2026-10-06）把监听器从空桩升级成 **flush 时按 diff 派发**：归一合同里"第二次跑不许再写盘"
 * 那一格要数**写盘次数**，空桩会让它恒真（仪器坏了，不是判据松了）。派发只看真实变化的键，
 * remove 不存在的键不算变化——与系统 SharedPreferences 的可观察形状一致。
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = LinkedHashMap<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

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
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {
        l?.let { listeners.add(it) }
    }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {
        l?.let { listeners.remove(it) }
    }

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
            val changedKeys = buildList {
                for (key in data.keys + pending.keys) {
                    if (!java.util.Objects.equals(data[key], pending[key])) add(key)
                }
            }.distinct()
            data.clear()
            data.putAll(pending)
            changedKeys.forEach { key ->
                listeners.toList().forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, key) }
            }
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
    // 引导闸门两颗（`SecurePrefs.kt:366-376`）：默认值与生产一致——没看过介绍、游标是空串。
    // 空串不是脏值：把它落成 NONE 是 `GuideCursor.from` 的责任，fake 不做第二套钳制。
    override var introSeen: Boolean = false
    override var guideCursor: String = ""
    // 介绍层第几格：默认 0；钳制归 `SetupViewModel`，fake 这里同样不做第二套（合同测的口径 = 存什么读什么）
    override var introStep: Int = 0
    // CAP4（2026-10-06）：抓取开关默认与生产同形——fail-closed，没写过 = 关。
    // 一次性归一是生产侧构造期行为（`SecurePrefsCaptureEnabledReconciliationTest` 钉），
    // fake 没有"盘上键存不存在"这一层，合同两侧同形的部分只有：默认关 + 显式写入原样读回。
    override var captureEnabled: Boolean = false
    override var accessibilityDisclosureVersion: Int = 0
    override var captureAllowedPackages: Set<String> = emptySet()
    override var lastKbEditFile: String? = null
}

/** fake 必须通过同一套合同 */
class InMemorySettingsStoreContractTest : SettingsStorePortContract() {
    override fun newStore(): SettingsStorePort = InMemorySettingsStore()
}

/**
 * CAP4（2026-10-06）：`SecurePrefs.captureEnabled` 默认翻成 fail-closed 之后，
 * **构造期一次性归一**的生产侧合同——老安装"从没写过这颗键、但四件事实齐"必须落 true
 * （升级后捕获不断），缺一落 false（用户看到的那句实话）；跑第二次不写盘、不翻转第一次的结论。
 *
 * 打法沿用本文件生产侧那一族：真 [SecurePrefs] + JVM 假盘（走 Keystore 不可用的降级分支），
 * 无障碍启用列表用 `mockkStatic(Settings.Secure)` 喂（与全仓库喂 `Log` 同一手）。
 * "写过"的唯一证据是 `contains`：**读到 false ≠ 没写过**——用读值当证据正是这颗 bug 的形状，
 * 下面 `a reconciled false is never flipped…` 一格专拆这个反例。
 */
class SecurePrefsCaptureEnabledReconciliationTest {

    private companion object {
        const val OUR_PACKAGE = "com.lovebrain.app"
        const val OUR_SERVICE = "$OUR_PACKAGE/com.lovebrain.app.service.CopyCaptureService"
        const val KEY_CAPTURE = "capture_enabled"
        const val KEY_DISCLOSURE = "accessibility_disclosure_version"
        const val KEY_PACKAGES = "capture_allowed_packages"
        val CURRENT = CopyCaptureService.CURRENT_DISCLOSURE_VERSION
    }

    @After
    fun releaseAndroidStatics() {
        unmockkStatic(Settings.Secure::class)
    }

    /** 摆好 JVM 假盘 + 无障碍启用列表，再构造一颗真 SecurePrefs（构造期即归一） */
    private fun build(fake: FakeSharedPreferences, enabledServices: String?): SecurePrefs {
        val ctx = mockk<Context>()
        every { ctx.getSharedPreferences(any(), any()) } returns fake
        every { ctx.packageName } returns OUR_PACKAGE
        every { ctx.contentResolver } returns mockk<ContentResolver>(relaxed = true)
        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), any()) } returns enabledServices
        return SecurePrefs(ctx)
    }

    /** 披露已同意 + 范围非空的盘；开关键默认**不写**（"从没写过"正是判据要吃的那件事） */
    private fun fullFactsFake(withSwitch: Boolean? = null): FakeSharedPreferences =
        FakeSharedPreferences().apply {
            edit().putInt(KEY_DISCLOSURE, CURRENT).apply()
            edit().putStringSet(KEY_PACKAGES, mutableSetOf("com.tencent.mm")).apply()
            withSwitch?.let { edit().putBoolean(KEY_CAPTURE, it).apply() }
        }

    @Test
    fun `never written with all four facts lands true on disk so the upgrade keeps capturing`() {
        val fake = fullFactsFake()
        // 列表里混着别家一段：判据必须按冒号分段逐组件比对
        val prefs = build(fake, "com.other.app/com.other.A11y:$OUR_SERVICE")
        assertTrue("四件齐的老安装归一必须落 true——不许静默把正在捕获的老用户关掉", prefs.captureEnabled)
        assertTrue("落成要写进盘，不是靠默认值兜", fake.contains(KEY_CAPTURE))
        assertTrue(fake.getBoolean(KEY_CAPTURE, false))
    }

    @Test
    fun `any missing fact lands false and still writes the key`() {
        // ① 无障碍没授予：列表为 null 与只有别家服务，两种形状都算缺
        for (enabled in listOf(null, "com.other.app/com.other.A11y")) {
            val fake = fullFactsFake()
            assertFalse("无障碍没授予 ⇒ 落 false", build(fake, enabled).captureEnabled)
            assertTrue("落 false 也是写过一次（归一完成），不是留白", fake.contains(KEY_CAPTURE))
        }
        // ② 披露差一版：旧同意覆盖不了新披露，与 isAccessibilityDisclosureConfirmed 同尺（>=）
        val stale = FakeSharedPreferences().apply {
            edit().putInt(KEY_DISCLOSURE, CURRENT - 1).apply()
            edit().putStringSet(KEY_PACKAGES, mutableSetOf("com.tencent.mm")).apply()
        }
        assertFalse("本版披露没同意过就不算真在抓", build(stale, OUR_SERVICE).captureEnabled)
        assertTrue(stale.contains(KEY_CAPTURE))
        // ③ 范围空：服务侧对空 allowlist 是 allowlist_empty 全拒，归一不许落 true
        val noScope = FakeSharedPreferences().apply {
            edit().putInt(KEY_DISCLOSURE, CURRENT).apply()
        }
        assertFalse("范围没选，服务一条都抓不到——不许落 true", build(noScope, OUR_SERVICE).captureEnabled)
        assertTrue(noScope.contains(KEY_CAPTURE))
    }

    @Test
    fun `a different service component of our own package does not count as granted`() {
        val fake = fullFactsFake()
        assertFalse(
            "判据是组件全名逐段比对；松成 contains(packageName) 就红（浮窗那类同名前缀服务不是捕获服务）",
            build(fake, "$OUR_PACKAGE/com.lovebrain.app.service.FloatingService").captureEnabled
        )
    }

    @Test
    fun `a reconciled false is never flipped by a later run with complete facts`() {
        // 第一趟无障碍缺 ⇒ 键落成 false；第二趟事实齐了 ⇒ 键已存在，必须一字不动。
        // 反例：拿"读出来是 false"当"从没写过"的证据——第二趟会重算成 true 把结论翻过去，
        // 这一格当场红；那正是这颗 bug 自己的形状。
        val fake = fullFactsFake()
        assertFalse(build(fake, null).captureEnabled)
        val second = build(fake, OUR_SERVICE)
        assertFalse("第一次的结论写盘作数；第二次既不重算也不翻转", second.captureEnabled)
    }

    @Test
    fun `a user-written true survives a run whose facts are incomplete`() {
        // 已写过 = 归一永不动，跟读出来的值与当时事实齐不齐都无关。
        // 反例：把判据松成"事实不齐就重写一遍"——用户显式拨的开会被无声关掉，这一格红。
        val fake = fullFactsFake(withSwitch = true)
        assertTrue(build(fake, null).captureEnabled)
    }

    @Test
    fun `a second construction writes nothing at all`() {
        val fake = fullFactsFake()
        val first = build(fake, OUR_SERVICE) // 第一趟：落 true，恰好写盘一次
        var fired = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> fired++ }
        fake.registerOnSharedPreferenceChangeListener(listener)
        // 仪器阳性对照（防恒真）：先证明显式写盘监听器**听得到**，下面那句"第二次构造零次写盘"
        // 才不是 FakeSharedPreferences 空桩给的假绿。
        // ⚠ 对照必须写**别的键**（这里用 `introStep`）：第一版拿 `captureEnabled` 自己当对照，
        // 于是把被测那颗值写成了 false，最后一句"读数也不变"必然红——
        // 对照件把被测对象改了，是本仓库坑表里"fixture 没进分支"的近亲（仪器自己污染样本）。
        first.introStep = 2
        assertEquals("写盘必须听得到，否则下一句的听不到是假绿", 1, fired)
        fired = 0
        val second = build(fake, OUR_SERVICE)
        assertEquals("归一只跑一次：第二次构造一个字都不许再写盘", 0, fired)
        assertTrue("第二次构造的读数也不变", second.captureEnabled)
    }
}
