package com.lovebrain.app.domain.port

import com.lovebrain.app.model.ProviderTicket

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

    /**
     * 引导进度新键（基线 v1 §6.8，2026-10-05）：**只加键，旧 [hasCompletedOnboarding] 的语义一字不动**。
     *
     * - [introSeen]：介绍层看过或明确跳过。它**只豁免介绍**，不豁免后续步骤；
     * - [guideCursor]：`GuideCursor` 枚举名（`NONE/PROVIDER/ACCESSIBILITY/CAPTURE/DONE/DEFERRED_TO_HINT`），
     *   空串=从没写过。游标持久化替代 `OnboardingFlow` 里那颗 `remember` 步号（重建即丢的那本旧账）。
     *
     * 完成与否**派生自真实状态**（供应商工单有效 / 无障碍已授权 / 捕获已开+披露已同意），
     * 这两个键不回答"配了没配"，只回答"介绍看过没、指引停在哪一格"。
     */
    var introSeen: Boolean
    var guideCursor: String
    /**
     * 介绍层走到第几格（0…3，默认 0）。与上面两颗分工清楚：`introSeen` 只管"介绍层豁免没豁免"、
     * `guideCursor` 只管"指引停在哪一步"，**这一步是介绍页内部的格号**——把它塞进上面任何一颗
     * 就是基线 §6.8 明令废掉的"第二本账"（G1b 接线单 §4）。
     */
    var introStep: Int
    /**
     * 消息捕获总开关。**默认关（fail-closed）**：没写过这颗键 = 用户从没要过捕获 ⇒ 读到关。
     * 隐私能力不许默认开——旧默认"开"被用户 2026-10-06 原话（"现在你的默认刚打开，
     * 你的开关就是拨开的"）钉成缺陷，本条即那次修复的端口侧合同。
     *
     * 老安装升级不断捕获由实现方在**存储构造期**一次性归一：四件事实齐（键从没写过 +
     * 无障碍已授予 + 当前版披露已同意 + 范围非空）才落 true，缺一落 false；
     * 判据是"键在不在盘上"（contains），**不是读出来的值**，写过之后永不再动。
     * 语义钉在 `SettingsStorePortContractTest`：空存储读到 false、显式写 true/false 原样读回、
     * 归一第二次跑不写盘也不翻转第一次的结论。
     */
    var captureEnabled: Boolean
    var accessibilityDisclosureVersion: Int
    var captureAllowedPackages: Set<String>

    /** 知识库编辑页记忆：上次打开的文件相对路径 */
    var lastKbEditFile: String?

    /**
     * 连接检查结论的**落盘副本**（默认 false = 未验证）。
     *
     * 与 [HomeConnectionLedger]（纯内存、随 VM 生灭）分工：ledger 按**身份**记当前 VM 会话内的结论，
     * 这颗布尔只记"上一次按 ▶ 有没有真的连通过"——Activity 销毁、VM 重建之后 ledger 空了，
     * 这颗布尔让 [connectionFor] 在全新 VM 的首次 publish 上仍能交回 [HomeConnectionVerdict.Verified]，
     * 不把已经绿过的灯落回黄。身份变化（换供应商/换模型/切当前对象）的作废纪律仍由 ledger 的
     * "按身份取不到 = NotChecked"管，这颗布尔只在 ledger **整本空**（= 全新 VM）时才兜底。
     *
     * 语义钉在 [SettingsStorePortContract]：空存储读到 false、显式写 true/false 原样读回。
     */
    var connectionVerified: Boolean
}
