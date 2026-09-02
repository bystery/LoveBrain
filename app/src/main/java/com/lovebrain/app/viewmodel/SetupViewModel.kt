package com.lovebrain.app.viewmodel

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.HttpsTrustGuard
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.util.L
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置页 ViewModel（：工单式模型供应商管理）。
 *
 * 职责：封装 SetupActivity 的供应商管理逻辑（ 分层治理：ui → ViewModel → data）
 * - 供应商列表 CRUD（增删改 + 激活单选 + 多模型列表 + 设为当前）
 * - Key 有无比尔（只暴露"有/无 + 掩码串"，不向 UI 提供读明文通道，）
 * - 连接测试调度（IO 隔离，走 DeepSeekRepository 真实链路，修复 ）
 * - 消息捕获开关：captureEnabled 状态暴露与切换
 *
 * 分层规则：SetupActivity → inject SetupViewModel（不直连 SecurePrefs/Repo）
 */
class SetupViewModel(
    private val securePrefs: SecurePrefs,
    private val deepSeekRepo: DeepSeekRepository
) : ViewModel() {

    // ═══════════ 工单列表状态 ────────────────

    private val _tickets = MutableStateFlow(securePrefs.getWorkerTickets())
    val tickets: StateFlow<List<ProviderTicket>> = _tickets.asStateFlow()

    /** 当前激活工单（无激活返回 null） */
    private val _activeTicket = MutableStateFlow(resolveActiveTicket())
    val activeTicket: StateFlow<ProviderTicket?> = _activeTicket.asStateFlow()

    private fun resolveActiveTicket(): ProviderTicket? {
        val id = securePrefs.activeTicketId ?: return null
        return _tickets.value.find { it.id == id }
    }

    // ═══════════ 表单错误态（：http 网络信任拦截，） ═══════════

    private val _formError = MutableStateFlow<String?>(null)
    /** 表单保存错误文案；保存成功时清空。文案为固定字符串，不拼用户输入 */
    val formError: StateFlow<String?> = _formError.asStateFlow()

    /** 保存前信任判定：委托 [HttpsTrustGuard] 纯判定，与单测共用；返回固定拦截文案，null=放行 */
    private fun checkBaseUrlTrust(baseUrl: String): String? =
        runCatching { HttpsTrustGuard.enforce(baseUrl) }.exceptionOrNull()?.message

    // ──────────────── 供应商 CRUD（多模型批：一供应商多模型 + 设为当前） ────────────────

    /** 新增供应商；首个自动激活。models 首个 = 当前生效模型 */
    fun addTicket(name: String, baseUrl: String, models: List<String>, apiKey: String) {
        if (name.isBlank() || baseUrl.isBlank()) return
        // ：http:// 非 loopback 保存前拦截不落盘；固定文案经 formError 暴露
        val violation = checkBaseUrlTrust(baseUrl)
        if (violation != null) {
            _formError.value = violation
            return
        }
        _formError.value = null
        val cleanModels = models.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val ticket = ProviderTicket(
            name = name.trim(),
            baseUrl = baseUrl.trim().trimEnd('/'),
            model = cleanModels.firstOrNull().orEmpty(),
            models = cleanModels,
            // 新建默认关（老数据无此字段走消费兜底，）
            thinkingMode = 0
        )
        val updated = _tickets.value + ticket
        securePrefs.setWorkerTickets(updated)
        if (apiKey.isNotBlank()) {
            securePrefs.saveWorkerApiKey(ticket.id, apiKey.trim())
        }
        _tickets.value = updated
        if (securePrefs.activeTicketId == null) {
            securePrefs.activeTicketId = ticket.id
        }
        _activeTicket.value = resolveActiveTicket()
        L.w("工单已添加：${ticket.name}")
    }

    /** 更新供应商；apiKey 留空保留原 Key（：永不回显、重填才能改）。
     *  当前生效模型若仍在列表内则保持，否则回退列表首个 */
    fun updateTicket(id: String, name: String, baseUrl: String, models: List<String>, apiKey: String) {
        if (name.isBlank() || baseUrl.isBlank()) return
        // ：http:// 非 loopback 保存前拦截不落盘；固定文案经 formError 暴露
        val violation = checkBaseUrlTrust(baseUrl)
        if (violation != null) {
            _formError.value = violation
            return
        }
        _formError.value = null
        val cleanModels = models.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val updated = _tickets.value.map { t ->
            if (t.id == id) {
                t.copy(
                    name = name.trim(),
                    baseUrl = baseUrl.trim().trimEnd('/'),
                    models = cleanModels,
                    model = if (t.model in cleanModels) t.model else cleanModels.firstOrNull().orEmpty()
                )
            } else t
        }
        securePrefs.setWorkerTickets(updated)
        if (apiKey.isNotBlank()) {
            securePrefs.saveWorkerApiKey(id, apiKey.trim())
        }
        _tickets.value = updated
        _activeTicket.value = resolveActiveTicket()
        L.w("工单已更新：$name")
    }

    /** 设为当前：把列表内某模型设为该供应商的生效模型（多模型批） */
    fun setTicketModel(id: String, model: String) {
        val updated = _tickets.value.map { t ->
            if (t.id == id && model in t.models) t.copy(model = model.trim()) else t
        }
        securePrefs.setWorkerTickets(updated)
        _tickets.value = updated
        _activeTicket.value = resolveActiveTicket()
    }

    /** 删除工单；若删除的是激活工单，激活态一并清空 */
    fun deleteTicket(id: String) {
        val updated = _tickets.value.filterNot { it.id == id }
        securePrefs.setWorkerTickets(updated)
        if (securePrefs.activeTicketId == id) {
            securePrefs.activeTicketId = null
        }
        // ：删工单同步清理分条 Key 密文（激活/未激活分支均清，防幽灵密钥残留）
        securePrefs.deleteWorkerApiKey(id)
        _tickets.value = updated
        _activeTicket.value = resolveActiveTicket()
        L.w("工单已删除：$id")
    }

    /** 激活工单（多工单单激活，） */
    fun activateTicket(id: String) {
        securePrefs.activeTicketId = id
        _activeTicket.value = resolveActiveTicket()
    }

    /**
     * toggleTicketThinking——直出/思考两态切换：0=直出 1=思考，
     * copy 写值经 [SecurePrefs.setWorkerTickets] 落盘。
     * 老工单（null）先读全局设置作当前生效态再翻转（继承契约，）。
     */
    fun toggleTicketThinking(id: String) {
        val updated = _tickets.value.map { t ->
            if (t.id == id) {
                val current = (t.thinkingMode ?: securePrefs.thinkingMode).coerceIn(0, 1)
                t.copy(thinkingMode = if (current == 1) 0 else 1)
            } else t
        }
        securePrefs.setWorkerTickets(updated)
        _tickets.value = updated
        _activeTicket.value = resolveActiveTicket()
    }

    /** 全局直出/思考兜底值（老工单 null 时的生效态回退读源， 继承契约； UI 接线） */
    val globalThinking: Int get() = securePrefs.thinkingMode

    // ──────────────── Key 掩码 ────────────────

    /** 只暴露掩码串：有 Key 返回固定 "sk-***"，无 Key 返回空串；不泄露前缀与长度 */
    fun getKeyMask(ticketId: String): String {
        return if (!securePrefs.getWorkerApiKey(ticketId).isNullOrBlank()) "sk-***" else ""
    }

    // ──────────────── 连接测试（ 修复） ────────────────

    /**
     * 连接测试：走 DeepSeekRepository 真实链路，按模型逐个测（多模型批）。
     * @param key 表单里用户新填的 Key；留空则用供应商已存 Key（UI 永不接触明文，）
     * @return 连接是否成功
     */
    suspend fun testConnection(ticket: ProviderTicket, model: String, key: String): Boolean {
        if (model.isBlank()) return false
        val effectiveKey = key.ifBlank { securePrefs.getWorkerApiKey(ticket.id).orEmpty() }
        return runCatching {
            deepSeekRepo.testConnection(effectiveKey, model, ticket.baseUrl)
        }.getOrElse { false }
    }

    // ═══ 消息捕获开关（ 问题 4）═══

    private val _captureEnabled = MutableStateFlow(securePrefs.captureEnabled)
    val captureEnabled: StateFlow<Boolean> = _captureEnabled.asStateFlow()

    fun toggleCapture() {
        val newValue = !_captureEnabled.value
        securePrefs.captureEnabled = newValue
        _captureEnabled.value = newValue
        L.w("capture switch toggled: $newValue")
    }

    /**
     *  ：无障碍授权状态判定（只读）。
     * ui 不直读系统设置（分层保持）：本应用捕获服务组件在已启用无障碍服务列表内 = 已授权。
     * 组件全限定名与 Manifest 声明同步维护（本项目唯一无障碍服务）。
     */
    fun isCaptureServiceEnabled(context: Context): Boolean {
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull() ?: return false
        val self = "${context.packageName}/com.lovebrain.app.service.CopyCaptureService"
        return enabled.split(':').any { it.trim() == self }
    }
}
