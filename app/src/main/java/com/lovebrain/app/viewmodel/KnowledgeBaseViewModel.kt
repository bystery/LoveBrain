package com.lovebrain.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KbArchiveTransfer
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.domain.AssetRegistry
import com.lovebrain.app.domain.OnboardingResultParser
import com.lovebrain.app.domain.OnboardingSchema
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.util.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * 知识库管理页的一次性事件（导入/导出/删除/建库结果）。
 *
 * 事件不带文案：提示语属于 UI 层，ViewModel 只回传事实，由 Activity 选词。
 */
sealed interface KbEvent {
    /** 建库事务结果 */
    data class Creation(val outcome: KbCreationOutcome) : KbEvent
    data object Exported : KbEvent
    data object ExportFailed : KbEvent
    data object Imported : KbEvent
    data object ImportFailed : KbEvent
    data object DeleteFailed : KbEvent
}

/** 建库事务结果 */
enum class KbCreationOutcome {
    /** 空模板库已创建（无需提示） */
    EmptyCreated,

    /** 知识库已创建且 AI 画像三段齐全 */
    ProfileCreated,

    /** 知识库已创建但画像不完整——降级为模板库，需告知用户可稍后补充 */
    TemplateOnlyCreated,

    /** 空模板建库失败（名称碰撞/IO 失败） */
    EmptyCreateFailed,

    /** AI 建库落盘失败 */
    OnboardingCreateFailed,

    /** 未配置供应商：UI 应弹二选一，确认后改走 [KnowledgeBaseViewModel.createEmptyKb] */
    ProviderNotConfigured,

    /** 生成中被取消：不提示、不建库 */
    Cancelled
}

/** 知识库列表状态（唯一真源，取代 Activity 内的 remember 局部列表状态） */
data class KbListState(
    val knowledgeBases: List<KnowledgeBase> = emptyList(),
    val activeName: String? = null,
    val loaded: Boolean = false
)

/**
 * 知识库管理页 ViewModel。
 *
 * 分层规则：KnowledgeBaseActivity 只负责窗口标记、Activity Result 启动与 Compose 承载，
 * Repository / Provider / 归档 IO 一律经此转发。
 */
class KnowledgeBaseViewModel(
    private val appContext: Context,
    private val repo: KnowledgeRepository,
    private val deepSeek: DeepSeekRepository,
    /** 归档 IO 的调度上下文；默认真实 IO，单测可注入虚拟时间调度器 */
    private val ioContext: CoroutineContext = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow(KbListState())
    val state: StateFlow<KbListState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<KbEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<KbEvent> = _events.asSharedFlow()

    /** 建库/AI 生成协程 = 事务唯一 owner；isActive 即互斥标记，不另设 boolean 台账 */
    private var creationJob: Job? = null

    val isCreating: Boolean get() = creationJob?.isActive == true

    // ═══════════ 列表读写 ═══════════

    fun refresh() {
        viewModelScope.launch { loadState() }
    }

    fun setActive(name: String) {
        viewModelScope.launch {
            try {
                repo.setActive(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e("setActive failed", e)
            }
            loadState()
        }
    }

    fun rename(name: String, newDisplayName: String) {
        viewModelScope.launch {
            try {
                repo.updateDisplayName(name, newDisplayName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e("updateDisplayName failed", e)
            }
            loadState()
        }
    }

    /** 删除失败不再静默（旧实现留了两个空 if 分支） */
    fun delete(name: String) {
        viewModelScope.launch {
            val ok = try {
                repo.delete(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e("delete failed", e)
                false
            }
            if (!ok) _events.tryEmit(KbEvent.DeleteFailed)
            loadState()
        }
    }

    private suspend fun loadState() {
        val kbs = repo.listAll()
        _state.value = KbListState(
            knowledgeBases = kbs,
            activeName = repo.getActive()?.name,
            loaded = true
        )
    }

    // ═══════════ 建库事务 ═══════════

    /** 供应商就绪判定：激活工单有模型 + 有 Key */
    private fun isProviderReady(): Boolean =
        deepSeek.getActiveTicket()?.model?.isNotBlank() == true &&
            !deepSeek.getActiveApiKey().isNullOrBlank()

    /** AI 建库入口：未配置供应商时只回传事实，由 UI 弹二选一 */
    fun createKbWithOnboarding(schema: OnboardingSchema) {
        if (isCreating) return
        if (!isProviderReady()) {
            _events.tryEmit(KbEvent.Creation(KbCreationOutcome.ProviderNotConfigured))
            return
        }
        creationJob = viewModelScope.launch {
            val outcome = runCatching { runOnboardingCreation(schema) }
            finishCreation(outcome)
        }
    }

    private suspend fun runOnboardingCreation(schema: OnboardingSchema): KbCreationOutcome {
        val name = autoKbName()
        val system = readEngineAsset(AssetRegistry.ONBOARDING)
        // 问卷输入以 JSON Schema 传给引擎（取代文本拼接块）
        val user = Json.encodeToString(OnboardingSchema.serializer(), schema)
        val raw = try {
            withContext(ioContext) { deepSeek.generateRaw(system, user) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e("onboarding generation failed", e)
            ""
        }
        // 取消竞态：IO 期间被 cancel 时不再落盘
        if (!coroutineContext.isActive) throw CancellationException("onboarding cancelled")

        val parsed = OnboardingResultParser.parse(raw)
        val display = parsed.display.ifBlank {
            schema.names.counterpart.ifBlank { "我的她" }
        }
        val stage = parsed.stage.ifBlank { "待确定" }

        if (!createKnowledgeBase(name, display)) return KbCreationOutcome.OnboardingCreateFailed
        // 只有对应段非空才覆盖模板，空段保留 schema 模板内容
        if (parsed.me.isNotBlank()) repo.writeFile(name, "understand/me.md", parsed.me)
        if (parsed.her.isNotBlank()) repo.writeFile(name, "understand/her.md", parsed.her)
        if (parsed.warmth.isNotBlank()) repo.writeFile(name, "understand/warmth.md", parsed.warmth)
        repo.updateStage(name, stage)
        return if (parsed.hasUsableProfile) KbCreationOutcome.ProfileCreated
        else KbCreationOutcome.TemplateOnlyCreated
    }

    /** 空模板建库 */
    fun createEmptyKb() {
        if (isCreating) return
        creationJob = viewModelScope.launch {
            val outcome = runCatching {
                if (createKnowledgeBase(autoKbName(), "新知识库")) KbCreationOutcome.EmptyCreated
                else KbCreationOutcome.EmptyCreateFailed
            }
            finishCreation(outcome)
        }
    }

    /** 生成中取消：cancel 协程，不落盘、不提示 */
    fun cancelOnboarding() {
        creationJob?.cancel()
        creationJob = null
    }

    private suspend fun createKnowledgeBase(name: String, displayName: String): Boolean =
        try {
            repo.create(name, displayName)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e("kb create failed", e)
            false
        }

    private fun finishCreation(outcome: Result<KbCreationOutcome>) {
        creationJob = null
        val event = when {
            outcome.isSuccess -> outcome.getOrThrow()
            outcome.exceptionOrNull() is CancellationException -> KbCreationOutcome.Cancelled
            else -> KbCreationOutcome.OnboardingCreateFailed
        }
        _events.tryEmit(KbEvent.Creation(event))
    }

    // ═══════════ 归档导出 / 导入 ═══════════

    /**
     * 导出到用户选定的 [target]。zip 打包在 IO 线程执行（大库不阻塞主线程），
     * 输出流由本类打开并关闭——调用方（Activity）只交出一个 Uri。
     */
    fun export(kbName: String, target: Uri) {
        viewModelScope.launch(ioContext) {
            val ok = runCatching {
                val folder = File(appContext.filesDir, "knowledge/$kbName")
                val output = appContext.contentResolver.openOutputStream(target)
                    ?: error("无法打开导出文件")
                output.use { KbArchiveTransfer.export(folder, kbName, it) }
            }.isSuccess
            if (!ok) L.e("knowledge export failed")
            _events.tryEmit(if (ok) KbEvent.Exported else KbEvent.ExportFailed)
        }
    }

    /**
     * 从 [source] 导入。解压+校验在 IO 线程；成功后强制修正 active，
     * 防止导入库自带 active=true 造成双激活。
     */
    fun import(source: Uri) {
        viewModelScope.launch(ioContext) {
            val ok = runCatching {
                val staging = File(appContext.cacheDir, "kb_import_${System.currentTimeMillis()}")
                val input = appContext.contentResolver.openInputStream(source)
                    ?: error("无法打开导入文件")
                input.use {
                    KbArchiveTransfer.import(
                        it,
                        staging,
                        File(appContext.filesDir, "knowledge")
                    )
                }
                val currentActive = repo.getActive()?.name ?: repo.listAll().firstOrNull()?.name
                if (currentActive != null) repo.setActive(currentActive)
            }.isSuccess
            if (!ok) L.e("knowledge import failed")
            if (ok) loadState()
            _events.tryEmit(if (ok) KbEvent.Imported else KbEvent.ImportFailed)
        }
    }

    // ═══════════ 内部工具 ═══════════

    /** 时间戳全量 + 随机后缀（不取模，避免每 16.7 分钟循环碰撞） */
    private fun autoKbName(): String =
        "kb_" + System.currentTimeMillis().toString(36) + "_" + (0..9999).random().toString(36)

    private fun readEngineAsset(path: String): String = runCatching {
        appContext.assets.open(path).bufferedReader().use { it.readText() }
    }.onFailure { L.e("readEngineAsset missing/failed: $path", it) }
        .getOrDefault("")
}
