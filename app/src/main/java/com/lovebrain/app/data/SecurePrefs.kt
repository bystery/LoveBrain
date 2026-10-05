package com.lovebrain.app.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lovebrain.app.AppConfig
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.service.CopyCaptureService
import com.lovebrain.app.util.L
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * 加密存储：API key 等敏感配置。
 * 使用 AndroidX Security 的 EncryptedSharedPreferences（AES256）。
 *
 * 它同时是 [SettingsStorePort] 的实现：页面侧按端口注入、拿到的是同一实例（见 di/AppModule.kt），
 * 加密与 Keystore 降级的判据只有这里一处，端口视图不额外开第二条落盘口。
 */
class SecurePrefs(context: Context) : SettingsStorePort {

    /** 加密是否可用。不可用时 apiKey 仅存内存，绝不落明文（ 安全加固）。 */
    private val isEncrypted: Boolean
    private val prefs: SharedPreferences
    private var memoryKey: String = ""

    // 工单系统降级路径：Keystore 不可用时，内存 Map（ticketId → apiKey）
    private var memoryTicketKeyMap: MutableMap<String, String>? = null

    init {
        var enc = true
        prefs = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "lovebrain_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            enc = false
            context.getSharedPreferences("lovebrain_prefs_fallback", Context.MODE_PRIVATE)
        }
        isEncrypted = enc

        // 初始化内存 Map（降级路径用）
        if (!isEncrypted) {
            memoryTicketKeyMap = mutableMapOf()
        }

        // 一次性内存迁移——如果旧明文 prefs 中有 provider_key_*，
        // 迁移到加密存储后立即删除明文残留。
        // 只在加密可用时执行——降级路径中不存在明文 Key（已不写入）。
        if (isEncrypted) {
            migrateAndDeleteOldPlaintextKeys(context)
        }

        // 消息/想法改纯内存（杀进程即清）——启动顺手移除旧残留键，键不再使用
        prefs.edit()
            .remove("saved_messages")
            .remove("saved_user_hint")
            .apply()

        // CAP4（2026-10-06）：captureEnabled 默认翻成 fail-closed 之后，老安装里
        // "从没写过这颗键、但四件事实则在抓"的那一档在这里一次性落成 true——升级后捕获不断。
        // 为什么这一跳、为什么幂等，见函数 KDoc。
        reconcileLegacyCaptureEnabledOnce(context)
    }

    // ═══════════ 旧单 Key 兼容字段（过渡期保留）═══════════

    var apiKey: String
        get() = if (isEncrypted) prefs.getString(KEY_API_KEY, "") ?: "" else memoryKey
        set(value) {
            if (isEncrypted) prefs.edit().putString(KEY_API_KEY, value).apply()
            else memoryKey = value  // 加密不可用：仅内存，不落明文
        }

    var model: String
        get() = prefs.getString(KEY_MODEL, AppConfig.DEFAULT_MODEL) ?: AppConfig.DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, AppConfig.API_BASE_URL) ?: AppConfig.API_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var activeKbName: String
        get() = prefs.getString(KEY_ACTIVE_KB, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_KB, value).apply()

    /**
     * 思考模式两态：0=直出 (thinking disabled) 1=思考·轻 (effort low)
     * 旧值 2→降级为 0（工单系统通用化，不同供应商支持程度统一）
     * 默认 0（话术生成任务实测直出更快更省更稳）
     */
    override var thinkingMode: Int
        get() = prefs.getInt(KEY_THINKING, 0).coerceIn(0, 1)  // ← 越界钳制为 0 或 1
        set(value) = prefs.edit().putInt(KEY_THINKING, value.coerceIn(0, 1)).apply()  // ← 写入时钳制

    /**
     * 输出模式二态：0=普通 1=进攻（进攻模式在 system prompt 追加 aggressive.md）
     * 默认 0
     */
    override var outputMode: Int
        get() = prefs.getInt(KEY_OUTPUT_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_OUTPUT_MODE, value).apply()

    // ═══ 状态持久化（重启不丢失）═══

    /** 面板模式 (0=reply, 1=counseling) */
    override var panelMode: Int
        get() = prefs.getInt(KEY_PANEL_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_PANEL_MODE, value).apply()

    /** 谈心结果持久化（重启不丢失） */
    override fun saveCounselingResult(json: String) {
        prefs.edit().putString(KEY_COUNSELING_RESULT, json).apply()
    }

    override fun loadCounselingResult(): String? = prefs.getString(KEY_COUNSELING_RESULT, null)

    override fun clearCounselingResult() {
        prefs.edit().remove(KEY_COUNSELING_RESULT).apply()
    }

    /** 谈心草稿持久化 */
    override var counselingDraft: String
        get() = prefs.getString(KEY_COUNSELING_DRAFT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COUNSELING_DRAFT, value).apply()

    /** 谈心多轮历史持久化（JSON 格式，重启不丢失） */
    override fun saveCounselingHistory(json: String) {
        prefs.edit().putString(KEY_COUNSELING_HISTORY, json).apply()
    }

    /** 读取谈心多轮历史 */
    override fun loadCounselingHistory(): String? = prefs.getString(KEY_COUNSELING_HISTORY, null)

    /** 清除谈心多轮历史 */
    override fun clearCounselingHistory() {
        prefs.edit().remove(KEY_COUNSELING_HISTORY).apply()
    }

    /** API 统计持久化 */
    fun saveApiStats(json: String) {
        prefs.edit().putString(KEY_API_STATS, json).apply()
    }

    fun loadApiStats(): String? = prefs.getString(KEY_API_STATS, null)

    // ═══ 今日花费持久化（date+value 双键，非敏感金额）═══

    /** 保存今日花费（日期 + 金额；跨天清零由消费侧 rollTodayCost 判定） */
    override fun saveTodayCost(dateStr: String, yuan: Double) {
        prefs.edit()
            .putString(KEY_TODAY_COST_DATE, dateStr)
            .putString(KEY_TODAY_COST_YUAN, yuan.toString())
            .apply()
    }

    /** 读取今日花费（dateStr to yuan；无存档返回 null） */
    override fun loadTodayCost(): Pair<String, Double>? {
        val date = prefs.getString(KEY_TODAY_COST_DATE, null) ?: return null
        val yuan = prefs.getString(KEY_TODAY_COST_YUAN, null)?.toDoubleOrNull() ?: return null
        return date to yuan
    }

    // 暗色模式已删，darkMode/followSystemDarkMode 键不再使用（旧数据自然残留不读）

    /** 悬浮窗面板宽度（dp，默认 0 表示使用 AppConfig 默认值） */
    var panelWidth: Int
        get() = prefs.getInt(KEY_PANEL_WIDTH, 0)
        set(value) = prefs.edit().putInt(KEY_PANEL_WIDTH, value).apply()

    /** 悬浮窗面板高度（dp，默认 0 表示使用 AppConfig 默认值） */
    var panelHeight: Int
        get() = prefs.getInt(KEY_PANEL_HEIGHT, 0)
        set(value) = prefs.edit().putInt(KEY_PANEL_HEIGHT, value).apply()

    /**
     * 面板**背景层**浓度：整数百分比，100 = 完全不透明（滑杆没动过的那一档，外观与从前逐字相同）。
     *
     * 这一格是 [PanelBackdropOpacity] 读写通路的那一对，键名、区间与刻度全归那颗 object，
     * 这里不复制字面量、也不另发明一套回落：读写两侧共用同一个 [PanelBackdropOpacity.snapPercent]，
     * 盘上因此只可能出现合法刻度值，越界脏值与升级前"根本没写过这一项"都落回同一格。
     * 它作用的是背景层颜色的 alpha，不是窗口的 `ComposeView.alpha`（那条通道归淡入淡出动画）。
     */
    var panelBackdropOpacityPercent: Int
        get() = PanelBackdropOpacity.snapPercent(
            prefs.getInt(PanelBackdropOpacity.PREF_KEY, PanelBackdropOpacity.DEFAULT_PERCENT))
        set(value) = prefs.edit()
            .putInt(PanelBackdropOpacity.PREF_KEY, PanelBackdropOpacity.snapPercent(value)).apply()

    // ═══ 消息捕获开关（ 问题 4）═══

    /**
     * 消息捕获总开关：关闭后 CopyCaptureService 在事件入口直接忽略一切捕获。
     *
     * **默认关（fail-closed，2026-10-06 CAP4 改）**：没写过这颗键 = 用户从没要过捕获 ⇒ 读出来必须是关。
     * 旧默认是"开"——全新安装一进消息捕获页开关就是拨开的，而披露没同意、无障碍没授予、范围没选，
     * 页面显示的那一格就是一句谎。用户原话（"现在你的默认刚打开，你的开关就是拨开的"）把这条钉成
     * 缺陷，团队据此立的规矩：**隐私能力不许默认开**。
     *
     * 老安装升级不断捕获不归这颗默认值管——由 [reconcileLegacyCaptureEnabledOnce] 在构造期
     * 一次性归一落盘；这颗 getter 永远只回答"没写过就是关"。
     * 语义钉在 `SettingsStorePortContractTest`（空存储读到 false、显式写 true/false 原样读回）。
     */
    override var captureEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CAPTURE_ENABLED, value).apply()

    /**
     * 一次性归一 [captureEnabled] 的持久值（CAP4，2026-10-06）。
     *
     * **只跑一次的凭据是键本身**：`prefs.contains(KEY_CAPTURE_ENABLED)`。写过——不管写的是 true
     * 还是 false，不管是用户拨的还是上一趟归一落的——直接返回：不写盘，更不许拿后来的事实
     * 翻转第一次的结论。所以不需要"是否已归一"的第二本账；也**不许**用"读出来是 true"
     * 当"写过"的证据：默认翻成关之后，"读到 false"既可能是"从没写过"也可能是"归一落成的 false"，
     * 读值装不下这两件事——那正是这颗 bug 自己的形状。
     *
     * **发生在哪一跳**：SecurePrefs 的构造期（init），先于任何人读这颗键。这是全工程唯一同时
     * 覆盖两个入口的跳：页面侧走 DI 单例（AppModule），服务侧 `CopyCaptureService.onServiceConnected`
     * 自己 `SecurePrefs(this)` 也走这里。放在 VM 里会留洞：老用户升级后没打开任何页面时 VM
     * 永不被构造，服务读到的就是没归一的关——静默关掉正在捕获的老用户，恰是本工单禁的事。
     * 对 A 席（CaptureAppsScreen 显示改读有效态）无抢颗粒：归一只在构造期写这一颗键一次，
     * UI 侧只读；此后唯一的写者是 toggleCapture（用户显式动作），同主线程串行，无竞态窗口。
     *
     * **落 true 的条件 = 四件事实齐**（缺一落 false，就是用户看到的实话）：
     * ①本键从没写过（上面那条 contains）；②本应用捕获服务已在系统无障碍启用列表里；
     * ③当前这一版披露明确同意过（记的版本 >= [CopyCaptureService.CURRENT_DISCLOSURE_VERSION]，
     * 与 `SetupViewModel.isAccessibilityDisclosureConfirmed` 同一把尺）；④抓取范围非空。
     * ②③④ 齐是"旧版本真的在抓"的充分证据——旧默认"开"从没被用户显式拨过，而服务侧每一条
     * 正文都必须穿过这三道闸才会落进捕获。
     *
     * 任何一步抛（Keystore 降级、JVM 桩、系统设置读不动）都按"证据不齐"处理 = 落 false：
     * fail-closed 不因归一自身失败而变宽。
     */
    private fun reconcileLegacyCaptureEnabledOnce(context: Context) {
        if (prefs.contains(KEY_CAPTURE_ENABLED)) return
        val legacyWasActuallyCapturing = runCatching {
            isCaptureServiceEnabledForReconciliation(context) &&
                accessibilityDisclosureVersion >= CopyCaptureService.CURRENT_DISCLOSURE_VERSION &&
                captureAllowedPackages.isNotEmpty()
        }.getOrDefault(false)
        prefs.edit().putBoolean(KEY_CAPTURE_ENABLED, legacyWasActuallyCapturing).apply()
        L.w("capture_enabled fail-closed reconciliation wrote=$legacyWasActuallyCapturing")
    }

    /**
     * 归一用的无障碍启用判据：与 `SetupViewModel.isCaptureServiceEnabled` 逐字同一把尺
     * （组件全名逐段比对，不 `contains(packageName)` 松判）。为什么不端口化/不共用：
     * 那是给 data 开一条 system-settings 读通道的新账，而这一格只在构造期读一次——
     * 两处字面一致由本函数注释与那侧各钉一个反例格看住。
     */
    private fun isCaptureServiceEnabledForReconciliation(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val self = "${context.packageName}/com.lovebrain.app.service.CopyCaptureService"
        return enabled.split(':').any { it.trim() == self }
    }

    // ═══ 无障碍隐私披露 consent 版本号 ═══

    /**
     * 无障碍隐私披露 consent 版本号。用户明确点击「同意并继续」后写入当前版本。
     * 披露内容发生重要变化时递增 CURRENT_DISCLOSURE_VERSION 即可重新要求确认。
     */
    override var accessibilityDisclosureVersion: Int
        get() = prefs.getInt(KEY_ACCESSIBILITY_DISCLOSURE_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_ACCESSIBILITY_DISCLOSURE_VERSION, value).apply()

    // ═══ 无障碍抓取 allowlist（默认 fail-closed）═══

    /**
     * 用户明确允许抓取的聊天 App 包名集合。
     *
     * 空集 = 什么都不抓。这是有意的默认值：上一版只有关键词 blocklist，
     * 没命中关键词的任意 App（地区银行、企业内聊、医疗、WebView 登录页）都会进入捕获逻辑。
     * allowlist 才能把边界收敛到用户真正授权的那几个聊天 App。
     */
    override var captureAllowedPackages: Set<String>
        get() = prefs.getStringSet(KEY_CAPTURE_ALLOWED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        set(value) =
            prefs.edit().putStringSet(KEY_CAPTURE_ALLOWED_PACKAGES, value.toSet()).apply()

    /** 追加一个允许抓取的包名 */
    fun addCaptureAllowedPackage(pkg: String) {
        if (pkg.isBlank()) return
        captureAllowedPackages = captureAllowedPackages + pkg
    }

    /** 移除一个允许抓取的包名 */
    fun removeCaptureAllowedPackage(pkg: String) {
        captureAllowedPackages = captureAllowedPackages - pkg
    }

    /**
     * 偏好变更的**可订阅口**：交回一颗取消订阅的闭包（调用方必须在 `onDestroy` 里调它，
     * 否则 `SharedPreferences` 持着监听器 = 持着整个服务实例，那是漏）。
     *
     * 为什么不做成"只报某个键"：本类默认走 `EncryptedSharedPreferences`，
     * 而它回给监听器的 **key 是加密后的那串**，与 `"capture_allowed_packages"` 永远对不上
     * （AndroidX 的老坑）。按键名过滤在这里会写成一颗**永远不触发**的监听器——
     * 比没监听更糟，因为它看起来是接上了的。所以这里**不筛键名**，
     * 由调用方自己重读那一份真源（幂等：值没变就是空操作，见 `CopyCaptureService.syncDeclaredPackageScope`
     * 里那句 `allowed == lastAppliedPackageNames` 早退）。
     *
     * 存在的理由：无障碍框架按 `ServiceInfo.packageNames` **在框架层就滤掉**不匹配的事件，
     * 于是"用户刚勾完第二个 App"这件事**不会有任何事件流进服务**来提醒它——
     * 只靠事件里的复检，新选的那一个永远抓不到，直到服务下次重建。
     */
    fun onAnyPreferenceChanged(listener: () -> Unit): () -> Unit {
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> listener() }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        return { runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) } }
    }

    // ═══════════ 工单系统字段（ - ）════════════

    /** 工单列表 JSON（非敏感元数据） */
    fun getWorkerTicketsJson(): String? = prefs.getString(KEY_TICKER_LIST_JSON, null)
    fun saveWorkerTicketsJson(json: String) {
        prefs.edit().putString(KEY_TICKER_LIST_JSON, json).apply()
    }

    /** 解析工单列表（老 JSON 的旧字段经 ignoreUnknownKeys 忽略；老数据只有 model 时迁移 models = [model]，多模型批） */
    override fun getWorkerTickets(): List<ProviderTicket> {
        val raw = getWorkerTicketsJson() ?: return emptyList()
        return runCatching {
            val migrationJson = Json { ignoreUnknownKeys = true }
            migrationJson.decodeFromString<List<ProviderTicket>>(raw)
                .map { t ->
                    when {
                        // 老数据：models 空 + model 非空 → models = [model]
                        t.models.isEmpty() && t.model.isNotBlank() -> t.copy(models = listOf(t.model))
                        // 老迁移兜底：model 空时用原 selectedModel 分条数据（ 遗留通道）
                        t.model.isBlank() && t.models.isEmpty() -> {
                            val legacy = getSelectedModel(t.id).orEmpty()
                            if (legacy.isNotBlank()) t.copy(model = legacy, models = listOf(legacy)) else t
                        }
                        // 当前模型不在列表内（编辑被删光/删掉当前项）→ 回退列表首个
                        t.model !in t.models && t.models.isNotEmpty() -> t.copy(model = t.models.first())
                        else -> t
                    }
                }
        }.getOrElse { e ->
            L.w("解析工单列表失败：${e.javaClass.simpleName}")
            emptyList()
        }
    }

    /** 保存工单列表 */
    override fun setWorkerTickets(tickets: List<ProviderTicket>) {
        val json = Json.encodeToString(serializer<List<ProviderTicket>>(), tickets)
        saveWorkerTicketsJson(json)
    }

    /** 激活工单 ID */
    override var activeTicketId: String?
        get() = prefs.getString(KEY_ACTIVE_TICKET_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_TICKET_ID, value).apply()

    /** 知识库编辑页记忆：上次打开的文件路径（切页/重启后恢复，编辑页抽屉方案） */
    override var lastKbEditFile: String?
        get() = prefs.getString("kb_edit_last_file", null)
        set(value) = prefs.edit().putString("kb_edit_last_file", value).apply()

    /**
     * 每工单的 selectedModel（分条存储）——仅供老数据迁移读取（：一工单 = 一模型后不再写入）
     */
    fun getSelectedModel(ticketId: String): String? = prefs.getString("selected_model_$ticketId", null)

    /**
     * 获取工单的 API Key（加密分条存储 / 降级内存 Map）
     * 优先级：memoryMap → encrypted → fallback null
     * 降级路径不再从明文 prefs 读 provider_key_*——旧明文 Key 已在迁移后删除
     */
    override fun getWorkerApiKey(ticketId: String): String? {
        // 先查内存 Map（Keystore 降级路径）
        if (!isEncrypted) {
            memoryTicketKeyMap?.let { map ->
                map[ticketId]?.takeIf { it.isNotEmpty() }?.also { return it }
            }
            // 降级路径不再从明文 prefs 读旧 Key——返回 null，用户需重新输入
            return null
        }

        // 加密存储路径
        val key = prefs.getString("provider_key_$ticketId", null)

        return key
    }

    /** 保存工单的 API Key（加密分条存储 / 降级内存 Map） */
    override fun saveWorkerApiKey(ticketId: String, apiKey: String) {
        if (isEncrypted) {
            prefs.edit().putString("provider_key_$ticketId", apiKey).apply()
        } else {
            // 绝不明文落盘！仅存内存
            memoryTicketKeyMap?.set(ticketId, apiKey)
        }
    }

    /** 删除工单的 API Key（/：deleteTicket 时对称清理——加密分条与降级内存双通道皆清，防孤立密文残留） */
    override fun deleteWorkerApiKey(ticketId: String) {
        if (isEncrypted) {
            prefs.edit().remove("provider_key_$ticketId").apply()
        }
        memoryTicketKeyMap?.remove(ticketId)
    }

    // ═══ 性能统计持久化 ═══

    /** 累计生成次数 */
    override var totalGenerateCount: Int
        get() = prefs.getInt(KEY_TOTAL_GEN_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_GEN_COUNT, value).apply()

    /** 累计花费（元） */
    override var totalCostYuan: Double
        get() = prefs.getString(KEY_TOTAL_COST_YUAN, "0")?.toDoubleOrNull() ?: 0.0
        set(value) = prefs.edit().putString(KEY_TOTAL_COST_YUAN, value.toString()).apply()

    /** 累计复制次数 */
    override var totalCopyCount: Int
        get() = prefs.getInt(KEY_TOTAL_COPY_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_COPY_COUNT, value).apply()

    /** 累计采用次数（记录实际发送） */
    override var totalAdoptCount: Int
        get() = prefs.getInt(KEY_TOTAL_ADOPT_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_ADOPT_COUNT, value).apply()

    /** 累计改写次数 */
    override var totalRewriteCount: Int
        get() = prefs.getInt(KEY_TOTAL_REWRITE_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_REWRITE_COUNT, value).apply()

    /** 是否已完成引导（已有用户不强制重走） */
    override var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /**
     * 介绍层看过/明确跳过（基线 v1 §6.8 新增键；与 [hasCompletedOnboarding] 各管各的层：
     * 这颗只豁免介绍，不代表引导整体完成）。语义钉在 `SettingsStorePortContractTest` 同一份合同里。
     */
    override var introSeen: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_INTRO_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_INTRO_SEEN, value).apply()

    /**
     * 引导游标：存 `GuideCursor` 枚举名，空串 = 从没写过（读写方只有 `SetupViewModel`，
     * 解析与非法值回落也在那一处——这里只是本子上的一行字）。
     */
    override var guideCursor: String
        get() = prefs.getString(KEY_ONBOARDING_GUIDE_CURSOR, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ONBOARDING_GUIDE_CURSOR, value).apply()

    /**
     * 介绍层第几格（0…3）。越界**不在这里钳**——钳制归 `SetupViewModel` 那两颗读写口
     * （`restoreIntroStep` / `saveIntroStep`），这里只做"存一个整数、读不回脏值（缺省 0）"。
     * 为什么值得单独一键：见 `SettingsStorePort.introStep` 上方——把格号塞进 `guideCursor`
     * 就是基线 §6.8 废掉的第二本账。
     */
    override var introStep: Int
        get() = prefs.getInt(KEY_ONBOARDING_INTRO_STEP, 0)
        set(value) = prefs.edit().putInt(KEY_ONBOARDING_INTRO_STEP, value).apply()

    /**
     * 一次性内存迁移——将旧明文 prefs 中的 provider_key_* 迁移到加密存储后删除。
     * 在 init 中调用，只执行一次（迁移后明文 key 已删除，后续不再命中）。
     */
    private fun migrateAndDeleteOldPlaintextKeys(context: Context) {
        // 读取可能的旧明文 prefs（降级路径使用的 fallback prefs）
        val fallbackPrefs = context.getSharedPreferences("lovebrain_prefs_fallback", Context.MODE_PRIVATE)
        val allEntries = fallbackPrefs.all
        val keysToDelete = mutableListOf<String>()
        for ((key, value) in allEntries) {
            if (key.startsWith("provider_key_") && value is String && value.isNotEmpty()) {
                // 迁移到加密存储
                val ticketId = key.removePrefix("provider_key_")
                prefs.edit().putString(key, value).apply()
                keysToDelete.add(key)
                L.w("migrated plaintext key for ticket=$ticketId to encrypted store")
            }
        }
        if (keysToDelete.isNotEmpty()) {
            val editor = fallbackPrefs.edit()
            keysToDelete.forEach { editor.remove(it) }
            editor.apply()
            L.w("deleted ${keysToDelete.size} old plaintext keys from fallback prefs")
        }
    }

    companion object {
        private const val KEY_API_KEY = "deepseek_api_key"
        private const val KEY_MODEL = "deepseek_model"
        private const val KEY_BASE_URL = "api_base_url"
        private const val KEY_ACTIVE_KB = "active_kb_name"
        private const val KEY_THINKING = "thinking_enabled"
        private const val KEY_OUTPUT_MODE = "output_mode"
        // 状态持久化
        private const val KEY_PANEL_MODE = "saved_panel_mode"
        private const val KEY_API_STATS = "saved_api_stats"
        // 今日花费（日期 + 金额双键）
        private const val KEY_TODAY_COST_DATE = "today_cost_date"
        private const val KEY_TODAY_COST_YUAN = "today_cost_yuan"
        private const val KEY_COUNSELING_RESULT = "saved_counseling_result"
        private const val KEY_COUNSELING_DRAFT = "saved_counseling_draft"
        private const val KEY_COUNSELING_HISTORY = "saved_counseling_history"
        private const val KEY_PANEL_WIDTH = "panel_width"
        private const val KEY_PANEL_HEIGHT = "panel_height"
        // 消息捕获开关
        private const val KEY_CAPTURE_ENABLED = "capture_enabled"
        // 性能统计
        private const val KEY_TOTAL_GEN_COUNT = "total_gen_count"
        private const val KEY_TOTAL_COST_YUAN = "total_cost_yuan"
        private const val KEY_TOTAL_COPY_COUNT = "total_copy_count"
        private const val KEY_TOTAL_ADOPT_COUNT = "total_adopt_count"
        private const val KEY_TOTAL_REWRITE_COUNT = "total_rewrite_count"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        // 引导进度新键（基线 v1 §6.8；旧 onboarding_done 语义不动，迁移规则见 SetupViewModel 那一族）
        private const val KEY_ONBOARDING_INTRO_SEEN = "onboarding_intro_seen"
        private const val KEY_ONBOARDING_GUIDE_CURSOR = "onboarding_guide_cursor"
        private const val KEY_ONBOARDING_INTRO_STEP = "onboarding_intro_step"
        // 无障碍隐私披露 consent 版本号
        private const val KEY_ACCESSIBILITY_DISCLOSURE_VERSION = "accessibility_disclosure_version"
        // 无障碍抓取 allowlist（默认空集 = 不抓任何 App）
        private const val KEY_CAPTURE_ALLOWED_PACKAGES = "capture_allowed_packages"

        // 工单系统键
        private const val KEY_TICKER_LIST_JSON = "worker_tickets_json"
        private const val KEY_ACTIVE_TICKET_ID = "active_ticket_id"
    }
}