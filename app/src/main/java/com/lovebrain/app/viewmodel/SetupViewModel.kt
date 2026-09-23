package com.lovebrain.app.viewmodel

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.HttpsTrustGuard
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val securePrefs: SecurePrefs,
    private val deepSeekRepo: DeepSeekRepository,
    private val feedbackCaseRepository: com.lovebrain.app.data.FeedbackCaseRepository? = null
) : ViewModel() {

    // ═══════════ 反馈案例（通过 ViewModel/DI 提供，不在 Composable 中 new Repository） ═══════════

    private val _feedbackCases = MutableStateFlow<List<com.lovebrain.app.model.FeedbackCase>>(emptyList())
    val feedbackCases: StateFlow<List<com.lovebrain.app.model.FeedbackCase>> = _feedbackCases.asStateFlow()

    private val _feedbackLoading = MutableStateFlow(false)
    val feedbackLoading: StateFlow<Boolean> = _feedbackLoading.asStateFlow()

    private val _feedbackError = MutableStateFlow<String?>(null)
    val feedbackError: StateFlow<String?> = _feedbackError.asStateFlow()

/** 导出状态——typed，替代裸异常 */
sealed class ExportState {
    data object Idle : ExportState()
    data object Loading : ExportState()
    /** S1-05: Success 携带 exportId——绑定复制/保存状态，新导出自动重置 */
    data class Success(val text: String, val exportId: String = java.util.UUID.randomUUID().toString()) : ExportState()
    data class Error(val message: String) : ExportState()
}
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()
    fun resetExportState() { _exportState.value = ExportState.Idle }

    suspend fun loadFeedbackCases() {
        val repo = feedbackCaseRepository ?: run {
            _feedbackError.value = "反馈功能未配置"
            return
        }
        _feedbackLoading.value = true
        _feedbackError.value = null
        try {
            _feedbackCases.value = withContext(Dispatchers.IO) { repo.getAll() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _feedbackError.value = "加载失败，请重试"
        } finally {
            _feedbackLoading.value = false
        }
    }

    fun exportFeedback(cases: List<com.lovebrain.app.model.FeedbackCase>, format: String) {
        val repo = feedbackCaseRepository ?: run {
            _exportState.value = ExportState.Error("反馈功能未配置")
            return
        }
        _exportState.value = ExportState.Loading
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    if (format == "markdown") repo.exportMarkdown(cases)
                    else repo.exportJson(cases)
                }
                _exportState.value = ExportState.Success(text)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("导出失败，请重试")
            }
        }
    }

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

    /** URL-01：保存中状态（探测 endpoint 时 UI 显示 loading） */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** 保存前信任判定：委托 [HttpsTrustGuard] 纯判定，与单测共用；返回固定拦截文案，null=放行 */
    private fun checkBaseUrlTrust(baseUrl: String): String? =
        runCatching { HttpsTrustGuard.enforce(baseUrl) }.exceptionOrNull()?.message

    /**
     * UX-02：供应商真正就绪态（与 LoveBrainViewModel.providerReady 同一判定）。
     * 蓝点 = 工单存在 && 模型非空 && Key 非空；不完整 = 灰点。
     * 在 _activeTicket / _tickets 变更时同步刷新。
     */
    private val _providerReady = MutableStateFlow(computeReady())
    val providerReady: StateFlow<Boolean> = _providerReady.asStateFlow()

    private fun computeReady(): Boolean {
        val ticket = _activeTicket.value ?: return false
        if (ticket.model.isBlank()) return false
        return !securePrefs.getWorkerApiKey(ticket.id).isNullOrBlank()
    }

    /** 每次工单变更后同步刷新就绪态 */
    private fun refreshReadyState() {
        _providerReady.value = computeReady()
    }

    /**
     * UX-02：供 UI 查询指定工单是否就绪（列表行蓝/灰）。
     */
    fun isTicketReady(ticket: ProviderTicket): Boolean =
        ticket.model.isNotBlank() &&
            !securePrefs.getWorkerApiKey(ticket.id).isNullOrBlank()

    // ──────────────── 供应商 CRUD（多模型批：一供应商多模型 + 设为当前） ────────────────

    /**
     * URL-01 + UX-01：带 endpoint 自动探测的供应商保存（新建 + 编辑统一入口）。
     *
     * 保存前验证：
     * - 新建：名称 + URL + 至少一个模型 + API Key 四项齐全
     * - 编辑：名称 + URL + 至少一个模型；API Key 留空保留原 Key
     *
     * 验证通过后自动探测 endpoint：
     * - 成功 → 保存完整 endpoint 到 ProviderTicket.baseUrl，关闭弹窗
     * - 失败 → 弹窗不关闭，formError 显示具体原因
     */
    suspend fun saveTicketWithProbe(
        ticketId: String?,
        name: String,
        baseUrl: String,
        models: List<String>,
        apiKey: String,
        thinkingMode: Int
    ): Boolean = withContext(Dispatchers.IO) {
        // ── 基础验证 ──
        if (name.isBlank()) {
            _formError.value = "供应商名称不能为空"
            return@withContext false
        }
        if (baseUrl.isBlank()) {
            _formError.value = "接口地址不能为空"
            return@withContext false
        }
        val cleanModels = models.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanModels.isEmpty()) {
            _formError.value = "请至少添加一个模型"
            return@withContext false
        }
        // UX-01：新建时 Key 必填；编辑时 Key 留空保留原 Key
        val effectiveKey = if (ticketId != null && apiKey.isBlank()) {
            securePrefs.getWorkerApiKey(ticketId).orEmpty()
        } else {
            apiKey.trim()
        }
        if (effectiveKey.isBlank()) {
            _formError.value = "请填写 API Key"
            return@withContext false
        }

        // ── HttpsTrustGuard 拦截 ──
        val violation = checkBaseUrlTrust(baseUrl)
        if (violation != null) {
            _formError.value = violation
            return@withContext false
        }

        _formError.value = null
        _saving.value = true

        // ── URL-01：endpoint 自动探测 ──
        val testModel = cleanModels.first()
        val result = deepSeekRepo.testConnectionWithProbe(effectiveKey, testModel, baseUrl)

        _saving.value = false

        if (!result.success) {
            _formError.value = result.message ?: "连接失败，请检查配置"
            return@withContext false
        }

        // ── 探测成功，保存完整 endpoint ──
        val resolvedUrl = result.resolvedUrl!!
        val effectiveThinking = if (ticketId != null) {
            // 编辑：保留原 thinkingMode（UI 层可单独切换）
            _tickets.value.find { it.id == ticketId }?.thinkingMode ?: thinkingMode
        } else {
            thinkingMode
        }

        if (ticketId == null) {
            // 新建
            val ticket = ProviderTicket(
                name = name.trim(),
                baseUrl = resolvedUrl,
                model = cleanModels.first(),
                models = cleanModels,
                thinkingMode = effectiveThinking
            )
            val updated = _tickets.value + ticket
            securePrefs.setWorkerTickets(updated)
            securePrefs.saveWorkerApiKey(ticket.id, effectiveKey)
            _tickets.value = updated
            if (securePrefs.activeTicketId == null) {
                securePrefs.activeTicketId = ticket.id
            }
        _activeTicket.value = resolveActiveTicket()
        refreshReadyState()
        L.w("工单已添加（URL已探测）：${ticket.name} → $resolvedUrl")
        } else {
            // 编辑
            val updated = _tickets.value.map { t ->
                if (t.id == ticketId) {
                    t.copy(
                        name = name.trim(),
                        baseUrl = resolvedUrl,
                        models = cleanModels,
                        model = if (t.model in cleanModels) t.model else cleanModels.first()
                    )
                } else t
            }
            securePrefs.setWorkerTickets(updated)
            securePrefs.saveWorkerApiKey(ticketId, effectiveKey)
            _tickets.value = updated
        _activeTicket.value = resolveActiveTicket()
        refreshReadyState()
        L.w("工单已更新（URL已探测）：$name → $resolvedUrl")
        }

        _formError.value = null
        return@withContext true
    }

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
        refreshReadyState()
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
        refreshReadyState()
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
        refreshReadyState()
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
        refreshReadyState()
        L.w("工单已删除：$id")
    }

    /** 激活工单（多工单单激活，） */
    fun activateTicket(id: String) {
        securePrefs.activeTicketId = id
        _activeTicket.value = resolveActiveTicket()
        refreshReadyState()
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
        refreshReadyState()
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
     * 连接测试：走 testConnectionWithProbe，与「保存」走同一条探测链，
     * 保证「测试连接」和「保存」行为一致。
     * @param key 表单里用户新填的 Key；留空则用供应商已存 Key（UI 永不接触明文，）
     * @return ConnectionTestResult（含 success / resolvedUrl / message）
     */
    suspend fun testConnection(
        ticket: ProviderTicket,
        model: String,
        key: String
    ): com.lovebrain.app.data.ConnectionTestResult {
        if (model.isBlank()) {
            return com.lovebrain.app.data.ConnectionTestResult(
                success = false,
                message = "模型名称不能为空"
            )
        }
        val effectiveKey = key.ifBlank { securePrefs.getWorkerApiKey(ticket.id).orEmpty() }
        if (effectiveKey.isBlank()) {
            return com.lovebrain.app.data.ConnectionTestResult(
                success = false,
                message = "API Key 不能为空"
            )
        }
        return deepSeekRepo.testConnectionWithProbe(effectiveKey, model, ticket.baseUrl)
    }

    // ═══ 消息捕获开关（ 问题 4）═══

    private val _captureEnabled = MutableStateFlow(securePrefs.captureEnabled)
    val captureEnabled: StateFlow<Boolean> = _captureEnabled.asStateFlow()

    fun toggleCapture() {
        val newValue = !_captureEnabled.value
        securePrefs.captureEnabled = newValue
        _captureEnabled.value = newValue
        if (!newValue) {
            com.lovebrain.app.service.CopyCaptureService.discardPendingCapture()
        }
        L.w("capture switch toggled: $newValue")
    }

    /**
     * RA-02：用户明确点击「同意并继续」后写入当前披露版本号。
     * CopyCaptureService 在 consent 版本 < CURRENT_DISCLOSURE_VERSION 时不处理消息内容。
     */
    fun confirmAccessibilityDisclosure() {
        securePrefs.accessibilityDisclosureVersion =
            com.lovebrain.app.service.CopyCaptureService.CURRENT_DISCLOSURE_VERSION
        L.w("accessibility disclosure confirmed: version=${com.lovebrain.app.service.CopyCaptureService.CURRENT_DISCLOSURE_VERSION}")
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

    // ═══════════ F12: 性能统计 ═══════════

    /** F12: 累计生成次数 */
    val totalGenerateCount: Int get() = securePrefs.totalGenerateCount

    /** F12: 累计花费（元） */
    val totalCostYuan: Double get() = securePrefs.totalCostYuan

    /** F12: 累计复制次数 */
    val totalCopyCount: Int get() = securePrefs.totalCopyCount

    /** F12: 累计采用次数（记录实际发送） */
    val totalAdoptCount: Int get() = securePrefs.totalAdoptCount

    /** F12: 累计改写次数 */
    val totalRewriteCount: Int get() = securePrefs.totalRewriteCount

    /** F12: 采用率 = 采用次数 / 生成次数 */
    val adoptRate: Float
        get() {
            val gen = securePrefs.totalGenerateCount
            return if (gen > 0) securePrefs.totalAdoptCount.toFloat() / gen else 0f
        }
}
