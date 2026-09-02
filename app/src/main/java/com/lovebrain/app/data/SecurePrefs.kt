package com.lovebrain.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lovebrain.app.AppConfig
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.util.L
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * 加密存储：API key 等敏感配置。
 * 使用 AndroidX Security 的 EncryptedSharedPreferences（AES256）。
 */
class SecurePrefs(context: Context) {

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

        // ：消息/想法改纯内存（杀进程即清）——启动顺手移除旧残留键，键不再使用
        prefs.edit()
            .remove("saved_messages")
            .remove("saved_user_hint")
            .apply()
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
    var thinkingMode: Int
        get() = prefs.getInt(KEY_THINKING, 0).coerceIn(0, 1)  // ← 越界钳制为 0 或 1
        set(value) = prefs.edit().putInt(KEY_THINKING, value.coerceIn(0, 1)).apply()  // ← 写入时钳制

    /**
     * 输出模式二态：0=普通 1=进攻（进攻模式在 system prompt 追加 aggressive.md）
     * 默认 0
     */
    var outputMode: Int
        get() = prefs.getInt(KEY_OUTPUT_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_OUTPUT_MODE, value).apply()

    // ═══ 状态持久化（重启不丢失）═══

    /** 今日锦囊持久化（JSON + 日期，仅当天恢复；） */
    fun saveSuggestion(json: String, dateStr: String) {
        prefs.edit().putString(KEY_SUGGESTION, json).putString(KEY_SUGGESTION_DATE, dateStr).apply()
    }

    /** 读取今日锦囊（json to dateStr；无则 null） */
    fun loadSuggestion(): Pair<String, String>? {
        val json = prefs.getString(KEY_SUGGESTION, null) ?: return null
        val date = prefs.getString(KEY_SUGGESTION_DATE, null) ?: return null
        return json to date
    }

    /** 清除今日锦囊 */
    fun clearSuggestion() {
        prefs.edit().remove(KEY_SUGGESTION).remove(KEY_SUGGESTION_DATE).apply()
    }

    /** 面板模式 (0=reply, 1=counseling) */
    var panelMode: Int
        get() = prefs.getInt(KEY_PANEL_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_PANEL_MODE, value).apply()

    /** 谈心结果持久化（重启不丢失） */
    fun saveCounselingResult(json: String) {
        prefs.edit().putString(KEY_COUNSELING_RESULT, json).apply()
    }

    fun loadCounselingResult(): String? = prefs.getString(KEY_COUNSELING_RESULT, null)

    fun clearCounselingResult() {
        prefs.edit().remove(KEY_COUNSELING_RESULT).apply()
    }

    /** 谈心草稿持久化 */
    var counselingDraft: String
        get() = prefs.getString(KEY_COUNSELING_DRAFT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COUNSELING_DRAFT, value).apply()

    /** 谈心多轮历史持久化（JSON 格式，重启不丢失） */
    fun saveCounselingHistory(json: String) {
        prefs.edit().putString(KEY_COUNSELING_HISTORY, json).apply()
    }

    /** 读取谈心多轮历史 */
    fun loadCounselingHistory(): String? = prefs.getString(KEY_COUNSELING_HISTORY, null)

    /** 清除谈心多轮历史 */
    fun clearCounselingHistory() {
        prefs.edit().remove(KEY_COUNSELING_HISTORY).apply()
    }

    /** API 统计持久化 */
    fun saveApiStats(json: String) {
        prefs.edit().putString(KEY_API_STATS, json).apply()
    }

    fun loadApiStats(): String? = prefs.getString(KEY_API_STATS, null)

    // ═══ ：今日花费持久化（仿锦囊 date+value 双键，非敏感金额）═══

    /** 保存今日花费（日期 + 金额；跨天清零由消费侧 rollTodayCost 判定） */
    fun saveTodayCost(dateStr: String, yuan: Double) {
        prefs.edit()
            .putString(KEY_TODAY_COST_DATE, dateStr)
            .putString(KEY_TODAY_COST_YUAN, yuan.toString())
            .apply()
    }

    /** 读取今日花费（dateStr to yuan；无存档返回 null） */
    fun loadTodayCost(): Pair<String, Double>? {
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

    // ═══ 消息捕获开关（ 问题 4）═══

    /** 消息捕获总开关：关闭后 CopyCaptureService 在事件入口直接忽略一切捕获，默认开 */
    var captureEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CAPTURE_ENABLED, value).apply()

    // ═══════════ 工单系统字段（ - ）════════════

    /** 工单列表 JSON（非敏感元数据） */
    fun getWorkerTicketsJson(): String? = prefs.getString(KEY_TICKER_LIST_JSON, null)
    fun saveWorkerTicketsJson(json: String) {
        prefs.edit().putString(KEY_TICKER_LIST_JSON, json).apply()
    }

    /** 解析工单列表（老 JSON 的旧字段经 ignoreUnknownKeys 忽略；老数据只有 model 时迁移 models = [model]，多模型批） */
    fun getWorkerTickets(): List<ProviderTicket> {
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
    fun setWorkerTickets(tickets: List<ProviderTicket>) {
        val json = Json.encodeToString(serializer<List<ProviderTicket>>(), tickets)
        saveWorkerTicketsJson(json)
    }

    /** 激活工单 ID */
    var activeTicketId: String?
        get() = prefs.getString(KEY_ACTIVE_TICKET_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_TICKET_ID, value).apply()

    /** 知识库编辑页记忆：上次打开的文件路径（切页/重启后恢复，编辑页抽屉方案） */
    var lastKbEditFile: String?
        get() = prefs.getString("kb_edit_last_file", null)
        set(value) = prefs.edit().putString("kb_edit_last_file", value).apply()

    /**
     * 每工单的 selectedModel（分条存储）——仅供老数据迁移读取（：一工单 = 一模型后不再写入）
     */
    fun getSelectedModel(ticketId: String): String? = prefs.getString("selected_model_$ticketId", null)

    /** 
     * 获取工单的 API Key（加密分条存储 / 降级内存 Map）
     * 优先级：memoryMap → encrypted → fallback 空串
     */
    fun getWorkerApiKey(ticketId: String): String? {
        // 先查内存 Map（Keystore 降级路径）
        if (!isEncrypted) {
            memoryTicketKeyMap?.let { map ->
                map[ticketId]?.takeIf { it.isNotEmpty() }?.also { return it }
            }
        }
        
        // 再查加密存储
        val key = prefs.getString("provider_key_$ticketId", null)
        
        // 降级路径同步：如果从加密区读到且内存 Map 为空，回填内存 Map
        if (!isEncrypted && !key.isNullOrEmpty()) {
            memoryTicketKeyMap?.set(ticketId, key)
        }
        
        return key
    }

    /** 保存工单的 API Key（加密分条存储 / 降级内存 Map） */
    fun saveWorkerApiKey(ticketId: String, apiKey: String) {
        if (isEncrypted) {
            prefs.edit().putString("provider_key_$ticketId", apiKey).apply()
        } else {
            // 绝不明文落盘！仅存内存
            memoryTicketKeyMap?.set(ticketId, apiKey)
        }
    }

    /** 删除工单的 API Key（/：deleteTicket 时对称清理——加密分条与降级内存双通道皆清，防孤立密文残留） */
    fun deleteWorkerApiKey(ticketId: String) {
        if (isEncrypted) {
            prefs.edit().remove("provider_key_$ticketId").apply()
        }
        memoryTicketKeyMap?.remove(ticketId)
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
        private const val KEY_SUGGESTION = "saved_suggestion"
        private const val KEY_SUGGESTION_DATE = "saved_suggestion_date"
        private const val KEY_API_STATS = "saved_api_stats"
        // ：今日花费（日期 + 金额双键）
        private const val KEY_TODAY_COST_DATE = "today_cost_date"
        private const val KEY_TODAY_COST_YUAN = "today_cost_yuan"
        private const val KEY_COUNSELING_RESULT = "saved_counseling_result"
        private const val KEY_COUNSELING_DRAFT = "saved_counseling_draft"
        private const val KEY_COUNSELING_HISTORY = "saved_counseling_history"
        private const val KEY_PANEL_WIDTH = "panel_width"
        private const val KEY_PANEL_HEIGHT = "panel_height"
        // 消息捕获开关
        private const val KEY_CAPTURE_ENABLED = "capture_enabled"

        // 工单系统键
        private const val KEY_TICKER_LIST_JSON = "worker_tickets_json"
        private const val KEY_ACTIVE_TICKET_ID = "active_ticket_id"
    }
}