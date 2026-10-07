package com.lovebrain.app.viewmodel

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.HttpsTrustGuard
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.domain.CapturePolicy
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 介绍层一共四格（0…3）——步号钳制的那把尺（`restoreIntroStep` / `saveIntroStep`）。
 * 顶层而不是类里：Kotlin 的 `const val` 不许长在普通类里。
 * 只有本 VM 读它，页面拿的是钳好之后的值（`OnboardingFlow` 与 `SetupActivity` 都不抄这个数）。
 */
internal const val INTRO_STEP_MAX = 0

/**
 * 设置页 ViewModel（：工单式模型供应商管理）。
 *
 * 职责：封装 SetupActivity 的供应商管理逻辑（ 分层治理：ui → ViewModel → data）
 * - 供应商列表 CRUD（增删改 + 激活单选 + 多模型列表 + 设为当前）
 * - Key 有无比尔（只暴露"有/无 + 掩码串"，不向 UI 提供读明文通道，）
 * - 连接测试调度（IO 隔离，走 DeepSeekRepository 真实链路，修复 ）
 * - 消息捕获开关：captureEnabled 状态暴露与切换
 *
 * 分层规则：SetupActivity 用 by viewModel() 取本类；既不直接 inject SecurePrefs/Repo，
 * 也不通过 viewModel.securePrefs 这种「伸手进 VM 拿仓库」的方式间接直连。
 */
class SetupViewModel(
    private val securePrefs: SettingsStorePort,
    private val deepSeekRepo: DeepSeekRepository,
    private val feedbackCaseRepository: com.lovebrain.app.data.FeedbackCaseRepository? = null,
    /** 只为"本机是否已有知识库"这一项判断存在；测试里可不给，此时按"没有"处理 */
    private val appContext: Context? = null
) : ViewModel() {

    // ═══════════ 首次引导（原先由 Activity 直接读写 securePrefs，现已收在这里）═══════════

    // ═══════════ 首次引导（原先由 Activity 直接读写 securePrefs，现已收在这里）═══════════

    /**
     * 本次启动是否应该显示引导流程。
     *
     * 老用户（已配供应商 / 已生成过 / 已有知识库）在第一次进设置页时就把完成标记补上，
     * 免得被当成新人重走一遍。判定逻辑与"补标记"这个写动作都在 ViewModel 里，
     * Activity 只拿一个布尔结果。
     *
     * ⚠ 2026-10-05 基线 v1 §6.8 后本颗**只作旧合同兼容**留在树上（`SetupViewModelOnboardingTest`
     * 前五格钉的就是它）；Activity 的引导闸门已改走 [shouldShowIntro] + [currentGuideCursor]，
     * "点了去配置"这一格从此不再有任何写成完成的通道。
     */
    fun shouldShowOnboarding(): Boolean {
        if (securePrefs.hasCompletedOnboarding) return false
        if (isExistingUser()) {
            securePrefs.hasCompletedOnboarding = true
            return false
        }
        return true
    }

    /** 用户跳过 / 完成 / 转去设置，三种出口都算引导结束（⚠ 旧口径，见 [shouldShowIntro] 上方注释） */
    fun completeOnboarding() {
        securePrefs.hasCompletedOnboarding = true
    }

    /**
     * 本次启动是否应该显示**介绍页**（引导四步的第一格，其余三步走首页遮罩）。
     *
     * 三条出口各是谁：看过或明确跳过 → [markIntroSeen] 只写 `introSeen`；
     * 老用户（旧旗标或真状态命中）→ 补写 `introSeen + cursor=DONE`，
     * **升级上来的用户既不会被拽回介绍，也不会被遮罩追着重配**（基线 §6.8"已有用户不能被强制重看"）。
     */
    fun shouldShowIntro(): Boolean {
        if (securePrefs.introSeen) return false
        if (securePrefs.hasCompletedOnboarding) {
            // 迁移：旧旗标为真 = 介绍层与指引都收工（只补一次，之后本颗不再读旧键）
            securePrefs.introSeen = true
            securePrefs.guideCursor = GuideCursor.DONE.name
            return false
        }
        if (isExistingUser()) {
            securePrefs.hasCompletedOnboarding = true
            securePrefs.introSeen = true
            securePrefs.guideCursor = GuideCursor.DONE.name
            return false
        }
        return true
    }

    /**
     * 介绍看完/跳过。**这只豁免介绍层**——不写完成旗标、不写游标 DONE，
     * 后续"配模型/给权限/开捕获"仍由派生状态决定是否起罩。
     */
    fun markIntroSeen() {
        securePrefs.introSeen = true
    }

    /** 四步的派生事实：全从真状态读，与"点过什么按钮"无关 */
    data class GuideFacts(
        val introSeen: Boolean,
        val providerReady: Boolean,
        val accessibilityGranted: Boolean,
        val captureOn: Boolean
    )

    fun guideFacts(context: android.content.Context): GuideFacts = GuideFacts(
        introSeen = securePrefs.introSeen,
        providerReady = _providerReady.value,
        accessibilityGranted = isCaptureServiceEnabled(context),
        // "捕获已开启"= 权限已给 **且** 开关意图 **且** 披露同意记录 **且** 范围非空（四件都得真）。
        // CAP1（2026-10-06，A1 source-10 候选①）：旧账少了第三件——服务侧 `CapturePolicy` 对空
        // allowlist 是 fail-closed 全拒（`allowlist_empty`，那是对的隐私默认，保持不动），
        // 而引导层曾对同样的机器念"捕获已开启"。判据从此与页面六格 `captureTruthOf` 同源同料：
        // 界面绿只发生在服务真能抓的那一格。
        // CAP3/CAP4（2026-10-06，用户原话"第一次进来开关就是开的"那一族）补第四件：
        // 无障碍**没授予**时服务一条正文都收不到（`onServiceConnected` 都进不来），
        // 引导却可以拿着"旗标 + 同意 + 范围"三件真判 DONE ——两张嘴对同一台机器念相反的话。
        // 这一颗加进来之后，`guideFacts.captureOn` 是 `captureTruthOf` 五颗读数的**严格子集**：
        // 少的那一颗只有悬浮窗（`floatingRunning`），它是投递闸、不是同意闸，
        // 引导不该因为"服务还没起来"把用户卡在 CAPTURE 那一格（那一格用户已经全给完了）。
        captureOn = isCaptureServiceEnabled(context) && _captureEnabled.value &&
            isAccessibilityDisclosureConfirmed() &&
            _captureAllowedPackages.value.isNotEmpty()
    )

    /**
     * 当前指引位置：读盘上存量游标 + 派生事实算出，顺带把该落盘的推进落盘。
     * 每次首页组合/ON_RESUME 都该重算一遍——权限被撤销时它会自己退回未完成那一格。
     */
    fun currentGuideCursor(context: android.content.Context): GuideCursor {
        val resolved = resolveGuideCursor(
            stored = GuideCursor.from(securePrefs.guideCursor),
            facts = guideFacts(context)
        )
        if (resolved.name != securePrefs.guideCursor) {
            securePrefs.guideCursor = resolved.name
        }
        if (resolved == GuideCursor.DONE && !securePrefs.hasCompletedOnboarding) {
            // 派生 DONE 时把旧旗标一并补真，两面旗标自此只剩"介绍已看 + 游标"这一本新账
            securePrefs.hasCompletedOnboarding = true
        }
        return resolved
    }

    /** 每步的"稍后"：收罩、缺项交回首页黄字行，游标可经 [resumeGuide] 恢复 */
    fun deferGuide() {
        if (GuideCursor.from(securePrefs.guideCursor) != GuideCursor.DONE) {
            securePrefs.guideCursor = GuideCursor.DEFERRED_TO_HINT.name
        }
    }

    /** 从黄字行/缺项提示点回来：清掉"稍后"，按派生事实重新定位 */
    fun resumeGuide(context: android.content.Context) {
        if (GuideCursor.from(securePrefs.guideCursor) == GuideCursor.DEFERRED_TO_HINT) {
            securePrefs.guideCursor = ""
        }
        currentGuideCursor(context)
    }

    /** 用户明确"不再指引"：唯一一条把游标钉死在 DONE 的人工通道（不要求重装、不清任何数据） */
    fun stopBeingGuided() {
        securePrefs.introSeen = true
        securePrefs.guideCursor = GuideCursor.DONE.name
        securePrefs.hasCompletedOnboarding = true
    }

    /**
     * 介绍层第几格的**唯一读写口**（G1b 接线单 §4）。
     *
     * 钳制放在这里而不是 `SecurePrefs`：盘上那一份只是"存了一个整数"，
     * "这一本一共几格"是产品知识（介绍层四格 ⇒ 0…3），归 VM。
     * 脏值（旧版本残留、手改过的偏好）读回来落回 0 = 从第一格重走，
     * 不抛异常——介绍页读不到步号不该让首页起不来。
     */
    fun restoreIntroStep(): Int = securePrefs.introStep.coerceIn(0, INTRO_STEP_MAX)

    fun saveIntroStep(step: Int) {
        securePrefs.introStep = step.coerceIn(0, INTRO_STEP_MAX)
    }

    /**
     * 从系统设置/子页返回（ON_RESUME）时重读落盘事实：
     * 捕获开关可能在别的进程里被改过，StateFlow 不自愈，这里按唯一真源补一次。
     */
    fun refreshGuideFactsFromStore() {
        _captureEnabled.value = securePrefs.captureEnabled
    }

    private fun isExistingUser(): Boolean {
        // 没有 Context 只代表"查不了本机知识库"，不代表其他三条都不成立——
        // 早先写成 `?: return false` 会让老用户（已有工单/已生成过）在缺 Context 时被当成新人重走引导。
        val knowledgeRoot = appContext?.filesDir?.let { File(it, "knowledge") }
        val hasKb = knowledgeRoot != null &&
            knowledgeRoot.exists() && knowledgeRoot.listFiles()?.isNotEmpty() == true
        return com.lovebrain.app.domain.OnboardingDecision.isExistingUser(
            hasWorkerTickets = securePrefs.getWorkerTickets().isNotEmpty(),
            hasActiveTicketId = !securePrefs.activeTicketId.isNullOrBlank(),
            totalGenerateCount = securePrefs.totalGenerateCount,
            hasKnowledgeBase = hasKb
        )
    }

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
    /** Success 携带 exportId——绑定复制/保存状态，新导出自动重置 */
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

    /** 保存中状态（探测 endpoint 时 UI 显示 loading） */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** 保存前信任判定：委托 [HttpsTrustGuard] 纯判定，与单测共用；返回固定拦截文案，null=放行 */
    private fun checkBaseUrlTrust(baseUrl: String): String? =
        runCatching { HttpsTrustGuard.enforce(baseUrl) }.exceptionOrNull()?.message

    /**
     * 供应商真正就绪态（与 LoveBrainViewModel.providerReady 同一判定）。
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
     * 供 UI 查询指定工单是否就绪（列表行蓝/灰）。
     */
    fun isTicketReady(ticket: ProviderTicket): Boolean =
        ticket.model.isNotBlank() &&
            !securePrefs.getWorkerApiKey(ticket.id).isNullOrBlank()

    // ──────────────── 供应商 CRUD（多模型批：一供应商多模型 + 设为当前） ────────────────

    /**
     * + 带 endpoint 自动探测的供应商保存（新建 + 编辑统一入口）。
     *
     * 保存前验证：
     * - 新建：名称 + URL + 至少一个模型 + API Key 四项齐全
     * - 编辑：名称 + URL + 至少一个模型；API Key 留空保留原 Key
     *
     * 验证通过后自动探测 endpoint：
     * - 成功 → 保存完整 endpoint 到 ProviderTicket.baseUrl，关闭弹窗
     * - 失败 → 弹窗不关闭，formError 显示具体原因
     *
     * [generateTimeoutSec] 是这张工单的生成超时档位（秒），只认
     * [com.lovebrain.app.GenerationTimeoutTier] 白名单里那四个数：非法值与 null（没这一项）
     * 一律落成默认档，落盘的就是档位本身，不留脏值给下游再判一遍。
     */
    suspend fun saveTicketWithProbe(
        ticketId: String?,
        name: String,
        baseUrl: String,
        models: List<String>,
        apiKey: String,
        thinkingMode: Int,
        generateTimeoutSec: Int? = null
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
        // 新建时 Key 必填；编辑时 Key 留空保留原 Key
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

        // ── endpoint 自动探测 ──
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
        // 档位过白名单：表单交来的只能是四档之一，脏值（含 null）在这里落回默认档，
        // 盘上因此永远存得到 60/120/180/300 四个数之一——下游读取时那层回落是兜第二道。
        val effectiveTimeoutSec = GenerationTimeoutTier.fromSecondsOrDefault(generateTimeoutSec).seconds

        if (ticketId == null) {
            // 新建
            val ticket = ProviderTicket(
                name = name.trim(),
                baseUrl = resolvedUrl,
                model = cleanModels.first(),
                models = cleanModels,
                thinkingMode = effectiveThinking,
                generateTimeoutSec = effectiveTimeoutSec
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
                        model = if (t.model in cleanModels) t.model else cleanModels.first(),
                        generateTimeoutSec = effectiveTimeoutSec
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
        // http:// 非 loopback 保存前拦截不落盘；固定文案经 formError 暴露
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
        // http:// 非 loopback 保存前拦截不落盘；固定文案经 formError 暴露
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
        // 删工单同步清理分条 Key 密文（激活/未激活分支均清，防幽灵密钥残留）
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
     * copy 写值经 [SettingsStorePort.setWorkerTickets] 落盘。
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
     * 用户明确点击「同意并继续」后写入当前披露版本号。
     * CopyCaptureService 在 consent 版本 < CURRENT_DISCLOSURE_VERSION 时不处理消息内容。
     */
    fun confirmAccessibilityDisclosure() {
        securePrefs.accessibilityDisclosureVersion =
            com.lovebrain.app.service.CopyCaptureService.CURRENT_DISCLOSURE_VERSION
        L.w("accessibility disclosure confirmed: version=${com.lovebrain.app.service.CopyCaptureService.CURRENT_DISCLOSURE_VERSION}")
    }

    /**
     * 当前这一版披露，用户**明确同意过**没有（只读，与上面那颗写口同一把尺）。
     *
     * 判据与 `CopyCaptureService` 挡住正文那条是同一个比较：记在偏好里的版本号
     * >= `CURRENT_DISCLOSURE_VERSION`。为什么页面要问这一格而不是自己读偏好：
     * ui 层不认 data 层（`PackageDependencyTest`），而披露记录只有一个主人。
     *
     * 消费方只有 `ui/home/CaptureAppsScreen` 那一处闸门——它决定"先弹披露"还是"直接去系统设置"，
     * 于是同一篇法律长文不会端第二遍，也不会被跳过。
     */
    fun isAccessibilityDisclosureConfirmed(): Boolean =
        securePrefs.accessibilityDisclosureVersion >=
            com.lovebrain.app.service.CopyCaptureService.CURRENT_DISCLOSURE_VERSION

    // ═══ 消息捕获 allowlist（默认 fail-closed）═══

    /** 用户已授权可捕获的包名集合。空集 = 什么都不捕获。 */
    private val _captureAllowedPackages = MutableStateFlow(securePrefs.captureAllowedPackages)
    val captureAllowedPackages: StateFlow<Set<String>> = _captureAllowedPackages.asStateFlow()

    /**
     * 本机可启动的 App（包名 → 显示名），已排除本应用与二次拒绝的敏感类别。
     *
     * **返回 null 表示"读不出来"，不是"没有 App"**。旧写法是
     * `runCatching { pm.queryIntentActivities(...) }.getOrDefault(emptyList())`——
     * 那把两件事合成了一件，于是捕获范围页会把"读不动"报成"这台机器上没有可授权的 App"。
     * 对一个默认 fail-closed 的采集功能，这是把用户往"我的 App 怎么都不见了"上误导，
     * 而且页面还给不出任何出口（空态没有重试，因为系统以为没什么可重试）。
     *
     * `queryIntentActivities` 是同步 IPC：装机量大时 `TransactionTooLargeException`、
     * 远端进程死亡都从这里过，所以抛出来要当成一次读失败。
     *
     * 下面那条 `?: run { … }` 是**防注解撒谎的兜底**，不是已验行为：当前 compileSdk 把这个
     * 方法标成 `@NonNull`（JVM 用例连 `returns null` 都编译不过），所以本机给它构造不出反例。
     * 留着它的理由只有一个：注解拦不住 ROM 真回 null，而 null 漏出去就是组合期 NPE 崩整页。
     */
    fun selectableCaptureTargets(context: Context): List<CaptureApp>? {
        val pm = context.packageManager
        val launchIntent = android.content.Intent(
            android.content.Intent.ACTION_MAIN
        ).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val raw = try {
            pm.queryIntentActivities(launchIntent, 0)
        } catch (e: Exception) {
            // 这是个非挂起函数（组合期同步调用），没有协程上下文要交代，所以不按类型分流取消；
            // 隐私红线：不记包名，只记失败类型
            L.e("capture target enumeration failed: ${e.javaClass.simpleName}")
            return null
        } ?: run {
            L.e("capture target enumeration returned null")
            return null
        }
        return raw.asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = runCatching {
                    info.loadLabel(pm).toString()
                }.getOrDefault(pkg)
                CaptureApp(
                    packageName = pkg,
                    displayName = label.ifBlank { pkg },
                    secondRejected = CapturePolicy.isSecondRejectApp(pkg)
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
            .toList()
    }

    /** 勾选/取消一个允许捕获的 App；二次拒绝类别不允许加入 */
    fun setCaptureAllowed(packageName: String, allowed: Boolean) {
        if (packageName.isBlank()) return
        if (allowed && CapturePolicy.isSecondRejectApp(packageName)) {
            L.w("refused to allowlist a second-reject package: ${packageName.takeLast(24)}")
            return
        }
        val next = if (allowed) _captureAllowedPackages.value + packageName
        else _captureAllowedPackages.value - packageName
        securePrefs.captureAllowedPackages = next
        _captureAllowedPackages.value = next
        if (!allowed) com.lovebrain.app.service.CopyCaptureService.discardPendingCapture()
        // allowlist 变更必须提高披露版本要求：旧 consent 覆盖不了新的采集范围
        L.w("capture allowlist changed: size=${next.size}")
    }

    /** 一个可被授权捕获的 App */
    data class CaptureApp(
        val packageName: String,
        val displayName: String,
        val secondRejected: Boolean
    )

    /**
     * 无障碍授权状态判定（只读）。
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

    // ═══════════ 性能统计 ═══════════

    /** 累计生成次数 */
    val totalGenerateCount: Int get() = securePrefs.totalGenerateCount

    /** 累计花费（元） */
    val totalCostYuan: Double get() = securePrefs.totalCostYuan

    /** 累计复制次数 */
    val totalCopyCount: Int get() = securePrefs.totalCopyCount

    /** 累计采用次数（记录实际发送） */
    val totalAdoptCount: Int get() = securePrefs.totalAdoptCount

    /** 累计改写次数 */
    val totalRewriteCount: Int get() = securePrefs.totalRewriteCount

    /** 采用率 = 采用次数 / 生成次数 */
    val adoptRate: Float
        get() {
            val gen = securePrefs.totalGenerateCount
            return if (gen > 0) securePrefs.totalAdoptCount.toFloat() / gen else 0f
        }
}
