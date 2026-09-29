package com.lovebrain.app.domain.port

import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.SuggestionCache

/**
 * 页面侧（ViewModel）需要的**偏好/凭据存储**这一族能力。
 *
 * **为什么单开这颗端口而不是让页面继续注入 `data/SecurePrefs`**：`SecurePrefs` 是加密存储的
 * 具体实现类（EncryptedSharedPreferences + Keystore 降级），页面按它的**类型**注入就等于
 * viewmodel 直接认了 data 层——这与 `PackageDependencyTest` 早已把 `KnowledgeRepository` 挡在
 * viewmodel 之外是同一个理由。这里只开页面**真正调用过**的那些成员（逐 VM 实扫，不是照抄仓库公开面），
 * 因此这格里没有 apiKey/model/baseUrl/panelWidth 那类页面根本不碰的旧字段。
 *
 * 实现方仍是同一个 `SecurePrefs`（它 implements 本端口）：容器里这颗端口视图与
 * `single { SecurePrefs(...) }` 解析到**同一个实例**（见 di/AppModule.kt），
 * 所以偏好落盘没有第二条路径，加密与降级判据也只有一处。
 *
 * 语义钉在合同里：[SettingsStorePortContract] 对生产实现（真 SecurePrefs）与内存 fake 跑同一批格子。
 */
interface SettingsStorePort {

    // ── 生成/面板开关（越界一律钳到合法域，读写两侧都钳）──
    /** 思考模式二态：0=直出、1=思考·轻；写越界钳回 [0,1] */
    var thinkingMode: Int

    /** 输出模式二态：0=普通、1=进攻 */
    var outputMode: Int

    /** 面板模式：0=回复、1=谈心 */
    var panelMode: Int

    // ── 谈心与草稿的持久化 ──
    var counselingDraft: String
    fun saveCounselingResult(json: String)
    fun loadCounselingResult(): String?
    fun clearCounselingResult()
    fun saveCounselingHistory(json: String)
    fun loadCounselingHistory(): String?
    fun clearCounselingHistory()

    // ── 今日锦囊缓存 ──
    fun saveSuggestion(json: String, dateStr: String, kbId: String, contextFingerprint: String, promptVersion: String)
    fun loadSuggestion(): SuggestionCache?

    // ── 今日花费（日期 + 金额，跨天清零由消费侧判定）──
    fun saveTodayCost(dateStr: String, yuan: Double)
    fun loadTodayCost(): Pair<String, Double>?

    // ── 工单与凭据（apiKey 分条加密；降级路径仅内存）──
    fun getWorkerTickets(): List<ProviderTicket>
    fun setWorkerTickets(tickets: List<ProviderTicket>)
    var activeTicketId: String?
    fun getWorkerApiKey(ticketId: String): String?
    fun saveWorkerApiKey(ticketId: String, apiKey: String)
    fun deleteWorkerApiKey(ticketId: String)

    // ── 累计用量统计 ──
    var totalGenerateCount: Int
    var totalCostYuan: Double
    var totalCopyCount: Int
    var totalAdoptCount: Int
    var totalRewriteCount: Int

    // ── 引导与抓取开关 ──
    var hasCompletedOnboarding: Boolean
    var captureEnabled: Boolean
    var accessibilityDisclosureVersion: Int
    var captureAllowedPackages: Set<String>

    /** 知识库编辑页记忆：上次打开的文件相对路径 */
    var lastKbEditFile: String?
}
