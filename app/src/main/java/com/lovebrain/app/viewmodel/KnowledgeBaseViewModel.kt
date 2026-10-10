package com.lovebrain.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.EventBus
import com.lovebrain.app.domain.AssetRegistry
import com.lovebrain.app.domain.OnboardingResultParser
import com.lovebrain.app.domain.OnboardingSchema
import com.lovebrain.app.domain.port.KbArchivePort
import com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.StageCatalog
import com.lovebrain.app.util.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * 知识库管理页的一次性事件（导入/导出/删除/建库/切库/改名结果）。
 *
 * 事件不带文案：提示语属于 UI 层，ViewModel 只回传事实，由 Activity 选词。
 * [KbEvent.ActiveSwitched] 带的是**那个库的显示名**——它是事实的对象名，不是句子的一半。
 */
sealed interface KbEvent {
    /** 建库事务结果 */
    data class Creation(val outcome: KbCreationOutcome) : KbEvent
    data object Exported : KbEvent
    data object ExportFailed : KbEvent
    data object Imported : KbEvent
    data object ImportFailed : KbEvent
    data object DeleteFailed : KbEvent
    data object RenameFailed : KbEvent

    /**
     * 切库成功（§12.2「点击非活动卡用于切换当前知识库时，应明确反馈『已切换到……』」）。
     *
     * 带的必须是**显示名**：卡片那一行念的就是它，内部库名（目录身份）不该出现在给用户的
     * 那句话里（§12.2「卡片信息不展示内部文件名与路径」同一口径）。
     */
    data class ActiveSwitched(val displayName: String) : KbEvent

    /** 切换没能落到那个库上（写失败，或写成了但重读不认这条引用） */
    data object SwitchFailed : KbEvent
}

/** 建库事务结果 */
enum class KbCreationOutcome {
    /** 空模板库已创建；首次流程里也可能是复用了那座还没被用过的初始库（§4 场景②：结果同样是 1 座可用空库） */
    EmptyCreated,

    /** 知识库已创建且 AI 画像三段齐全（创建或复用同一套判据，见 [KnowledgeBaseViewModel.runOnboardingCreation]） */
    ProfileCreated,

    /** 引擎答了话但画像一段都没解析出来——降级为模板库，需告知用户可稍后补充。
     *  ⚠ 引擎**报错或答了空**不再是这一支：那种情况下一次目录都不许多造（§4 场景⑤） */
    TemplateOnlyCreated,

    /** 空模板建库失败（名称碰撞/IO 失败） */
    EmptyCreateFailed,

    /** AI 建库落盘失败，或引擎没答出可写的内容：这一次**没有**留下任何新库 */
    OnboardingCreateFailed,

    /** 未配置供应商：UI 应弹二选一，确认后改走 [KnowledgeBaseViewModel.createEmptyKb] */
    ProviderNotConfigured,

    /** 生成中被取消：不提示、不建库；已经自造的那一座会在取消时回收 */
    Cancelled
}

/**
 * 知识库列表状态（唯一真源，取代 Activity 内的 remember 局部列表状态）。
 *
 * `loaded` / `loadFailed` 只由 [loadState] 写（[refresh] 与建库/导入/改名/删除成功后的那次
 * 重读走的是同一个出口），别处不改——第6节第3条 那四格要的正是
 * "这一次读完了没有、读成什么样"这两个事实，判据因此只有一处（见 ui 层 kbScreenState）。
 */
data class KbListState(
    val knowledgeBases: List<KnowledgeBase> = emptyList(),
    val activeName: String? = null,
    val loaded: Boolean = false,
    /**
     * 上一次读取抛了。为真时 [knowledgeBases] 是**上一次成功的残留值**，不能当成"刚读到的"，
     * 所以四格判定把 Error 排在 Content 之前。
     */
    val loadFailed: Boolean = false
)

/**
 * 知识库管理页 ViewModel。
 *
 * 分层规则：KnowledgeBaseActivity 只负责窗口标记、Activity Result 启动与 Compose 承载，
 * Repository / Provider / 归档 IO 一律经此转发。
 *
 * 仓库这一格注入的是 [KnowledgeBaseCatalogPort]（库的清单与元信息 + 建库要写的画像三段），
 * 不是具体仓库类：这一页不需要版本化保存那条链，也不该拿到它。
 */
class KnowledgeBaseViewModel(
    private val appContext: Context,
    private val repo: KnowledgeBaseCatalogPort,
    private val deepSeek: DeepSeekRepository,
    /** 归档导出/导入走端口：库目录、暂存区、zip 解包的路径归属都在实现侧，本类不再拼 File */
    private val archive: KbArchivePort,
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
            val written = try {
                repo.setActive(name)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e("setActive failed", e)
                false
            }
            loadState()
            reportSwitch(name, written)
        }
    }

    /**
     * 切库的结果只认**重读之后的真实数据流**，不认「调用有没有抛」。
     *
     * 为什么不能只信调用：目录写侧那一次切库是先写加密偏好、再逐库改 kb.json，
     * 后者被只读保护拒掉时它是静默返回的（端口上这一员是 `Unit`，压根没有失败通道），
     * 于是"没抛 = 成功"会把一次没落地的切换说成成功——而 §12.3 要的正是
     * 「活动库选择…使用同一身份」这条引用真的落在那一个库上。
     *
     * 重读本身失败时**一个事件都不发**：那一格由页面顶上去的 Error 说话
     * （`kbScreenState` 把 Error 排在 Content 之前）。这里再补一句「已切换到」或「切换失败」，
     * 都是替一次没读到的数据编结论。
     */
    private fun reportSwitch(name: String, written: Boolean) {
        val snapshot = _state.value
        if (snapshot.loadFailed) return
        val settled = snapshot.knowledgeBases.firstOrNull { it.name == name }
        if (written && settled != null && snapshot.activeName == name) {
            _events.tryEmit(KbEvent.ActiveSwitched(settled.displayName))
        } else {
            _events.tryEmit(KbEvent.SwitchFailed)
        }
    }

    fun rename(name: String, newDisplayName: String) {
        viewModelScope.launch {
            // 送出去与验收用同一个已归一化的值：写侧还会再 trim 一次，
            // 不先 trim 就比出"没改成"的假失败。
            val wanted = newDisplayName.trim()
            val written = try {
                repo.updateDisplayName(name, wanted)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e("updateDisplayName failed", e)
                false
            }
            loadState()
            reportRename(name, wanted, written)
        }
    }

    /**
     * 重命名的判据同 [reportSwitch]：`updateDisplayName` 在端口上是 `Unit`，
     * 只读库、目录已不在、kb.json 坏掉这三种情况它都是**静默不写**（写侧 KDoc 明写的降级），
     * 不核对真实数据流就会"卡片上名字没变、却一声不响"（§12.3「重命名只影响显示名及必要元数据」
     * 的另一半是：改没改成得让人知道）。
     */
    private fun reportRename(name: String, wanted: String, written: Boolean) {
        if (!written) {
            _events.tryEmit(KbEvent.RenameFailed)
            return
        }
        val snapshot = _state.value
        if (snapshot.loadFailed) return
        if (snapshot.knowledgeBases.firstOrNull { it.name == name }?.displayName != wanted) {
            _events.tryEmit(KbEvent.RenameFailed)
        }
    }

    /** 删除失败不再静默（旧实现留了两个空 if 分支）；写侧说删成了，还得看清单里是不是真没了 */
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
            loadState()
            val snapshot = _state.value
            when {
                !ok -> _events.tryEmit(KbEvent.DeleteFailed)
                !snapshot.loadFailed && snapshot.knowledgeBases.any { it.name == name } ->
                    // 目录还在清单里 = 这一删根本没落到真实数据流上（§12.1 三分法里的「列表没刷新」那一支）
                    _events.tryEmit(KbEvent.DeleteFailed)
            }
        }
    }

    private suspend fun loadState() {
        try {
            val kbs = repo.listAll()
            _state.value = KbListState(
                knowledgeBases = kbs,
                activeName = repo.getActive()?.name,
                loaded = true
            )
            // 通知面板侧的 LoveBrainViewModel 也刷新：用户在 KnowledgeBaseActivity 改名/删除/导入后，
            // 面板如果一直开着，它的 kbList 不会自动更新——发这条事件让 FloatingService 调 refreshKnowledgeBases()。
            EventBus.emitKbChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 这一格不是防御性摆设：抛出前一行是这个协程里**唯一**的读取路径，
            // 而 viewModelScope 里没人接这个异常——页面就停在"第一次数据还没到"那一格
            // （转圈转到用户退出），比"读到了 0 个库"更糟的是把失败说成空。
            // 真实触发点：getActive() 读 EncryptedSharedPreferences.activeKbName，
            // Keystore 里的值解不开时 getString 会抛（SecurePrefs 只在**构造**时兜了降级，逐次读没兜）。
            L.e("knowledge base list load failed", e)
            _state.value = _state.value.copy(loaded = true, loadFailed = true)
        }
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
            // cancel-safe: 取消不吞——finishCreation 按 exceptionOrNull() 的**类型**把
            // CancellationException 分流成 KbCreationOutcome.Cancelled（不建库、不报失败），
            // 由 KnowledgeBaseViewModelTest 的 cancelling generation 用例钉住
            val outcome = runCatching { runOnboardingCreation(schema) }
            finishCreation(outcome)
        }
    }

    private suspend fun runOnboardingCreation(schema: OnboardingSchema): KbCreationOutcome {
        // §12.3「生成或写入在开始时绑定目标库」：这一次事务**发起时**就绑好"这一次要落进哪一座"，
        // 全程不回来问"现在在用哪一个库"。注意绑的是"这一次流程的目标库"而不是"这一次要的库名"：
        // §4（M01/M02）把首次初始化与首次正式建库定为同一个用户流程的两步，盘上要是有那顶
        // 还没被用过的初始库，写侧会把它当目标库（内部身份不动，只改显示名），
        // 回来的 `created.name` 就是这一次真正该写进去的那一座。
        val name = autoKbName()
        val system = readEngineAsset(AssetRegistry.ONBOARDING)
        // 问卷输入以 JSON Schema 传给引擎（取代文本拼接块）
        val user = Json.encodeToString(OnboardingSchema.serializer(), schema)
        // §4 场景⑤「首次问卷失败 ⇒ 不留下额外半成品库」的顺序修法：
        // 引擎报错时 raw 只能是空串，解析出来的四段也全是空——**还没拿到可写内容就不建目录**。
        // 旧顺序（raw="" 照样往下走）会在 default 之外再造一座 13 格全模板的空库，
        // 那正是"首次建库出现两个库"里最难解释的那一支。
        val raw = try {
            withContext(ioContext) { deepSeek.generateRaw(system, user) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e("onboarding generation failed", e)
            return KbCreationOutcome.OnboardingCreateFailed
        }
        if (raw.isBlank()) {
            L.w("onboarding produced nothing; no library is created")
            return KbCreationOutcome.OnboardingCreateFailed
        }
        // 取消竞态：IO 期间被 cancel 时不再落盘
        if (!coroutineContext.isActive) throw CancellationException("onboarding cancelled")

        val parsed = OnboardingResultParser.parse(raw)
        val display = parsed.display.ifBlank {
            schema.names.counterpart.ifBlank { "我的她" }
        }
        // 回落值读阶段词表那颗主人（`StageCatalog.UNKNOWN`），与写侧 seed 用的
        // `KnowledgeCatalogWriteStore.INITIAL_STAGE` 同一颗——两处各抄一遍字面量时，
        // 任何一处改字都会让"这还是不是那顶没用过的空壳"那条判据悄悄恒假（§4）。
        val stage = parsed.stage.ifBlank { StageCatalog.UNKNOWN }

        val created = createKnowledgeBase(name, display) ?: return KbCreationOutcome.OnboardingCreateFailed
        // 目标库自此定死：写侧没复用时 created.name == name（这一次自己造的），
        // 复用了那顶初始库时 created.name != name（**不是**这一次造的，取消时一个字节都不许回收）。
        val target = created.name
        val selfCreated = target == name
        try {
            // 只有对应段非空才覆盖模板，空段保留 seed 留下的形状
            if (parsed.me.isNotBlank()) repo.writeFile(target, "understand/me.md", parsed.me)
            if (parsed.her.isNotBlank()) repo.writeFile(target, "understand/her.md", parsed.her)
            if (parsed.warmth.isNotBlank()) repo.writeFile(target, "understand/warmth.md", parsed.warmth)
            repo.updateStage(target, stage)
        } catch (e: CancellationException) {
            // §4 场景⑤另一半：取消落在"目录已落盘、画像还在挂起"这一支时，
            // 只回收**本次调用自造**的那一座（沿用写侧 seed 失败回收自己目录的同一先例），
            // 绝不动用户那座——把 selfCreated 判丢一次，就会以"修复重复"为名删掉真实数据。
            if (selfCreated) discardSelfCreatedKb(target)
            throw e
        }
        return if (parsed.hasUsableProfile) KbCreationOutcome.ProfileCreated
        else KbCreationOutcome.TemplateOnlyCreated
    }

    /**
     * 回收**这一次**调用自造的那座库（取消/半途失败时用）。
     *
     * 三道护栏：① 调用方必须先证明 `kbName` 等于它自己提出的那个库名——写侧复用盘上那座
     * 初始库时回来的名字不是它，永远走不到这里；② 走 [NonCancellable]：这一次调用已经被
     * 取消了，用取消中的协程去删等于什么都不会删（那正是要修的形状）；
     * ③ 回收失败不改口说成功，只留一条错误级日志——下次启动清单里多出一座半成品时查得到。
     */
    private suspend fun discardSelfCreatedKb(kbName: String) {
        withContext(NonCancellable) {
            val reclaimed = try {
                repo.delete(kbName)
            } catch (e: CancellationException) {
                // 取消信号显式放行（本仓 `audit_cancellation.py` 认这一种形状）：
                // 这一段虽然套在 NonCancellable 里，一旦被删的那一跳仍然可能把取消抛回来；
                // 把它记成"回收失败"等于把取消当业务失败上报——那正是这道审计要拦的事。
                throw e
            } catch (e: Exception) {
                L.e("self-created kb $kbName could not be reclaimed after cancellation", e)
                false
            }
            if (!reclaimed) {
                // 半成品留在盘上 = 下一次启动清单里多一座库。不改口说成功，但要把话说准。
                L.e("self-created kb $kbName is still on disk after the cancelled creation", null)
            }
        }
    }

    /** 空模板建库（问卷"跳过"那一支） */
    fun createEmptyKb() {
        if (isCreating) return
        creationJob = viewModelScope.launch {
            // cancel-safe: 同上——取消经 finishCreation 转成 Cancelled，不伪装成 CreateFailed
            val outcome = runCatching {
                // §4 场景②「全新安装跳过问卷 ⇒ 1 个可使用的空库」：
                // 首次初始化那一步留下的空壳若还没被用过，它就是这一次要的结果（写侧复用它），
                // 于是"跳过"得到的是一座能直接用的空库而不是第二座；没有可复用的才真的加一座库。
                if (createKnowledgeBase(autoKbName(), "新知识库") != null) KbCreationOutcome.EmptyCreated
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

    /**
     * 建库这一趟交回来的**真实的那座库**（null = 没建成）。
     *
     * 返回值不能省：写侧在首次流程里可能把盘上那顶还没用过的初始库当目标库回来，
     * 那时库名不是调用方提出的那一个（`kb_xxx`），后续每一格都必须写进它拿回来的那一座。
     */
    private suspend fun createKnowledgeBase(name: String, displayName: String): KnowledgeBase? =
        try {
            repo.create(name, displayName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e("kb create failed", e)
            null
        }

    private fun finishCreation(outcome: Result<KbCreationOutcome>) {
        creationJob = null
        val event = when {
            outcome.isSuccess -> outcome.getOrThrow()
            outcome.exceptionOrNull() is CancellationException -> KbCreationOutcome.Cancelled
            else -> KbCreationOutcome.OnboardingCreateFailed
        }
        // P0 修复：创建是唯一一个没在 VM 里直接 loadState() 的变更操作——
        // 重命名/删除/切换/导入都在各自方法里调 loadState()，唯独创建走 Activity 事件总线，
        // 事件丢了列表就不刷新，新建的库不显示。这里补上，与其它变更操作同路。
        if (event == KbCreationOutcome.EmptyCreated ||
            event == KbCreationOutcome.ProfileCreated ||
            event == KbCreationOutcome.TemplateOnlyCreated) {
            viewModelScope.launch { loadState() }
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
            val ok = try {
                val output = appContext.contentResolver.openOutputStream(target)
                    ?: error("无法打开导出文件")
                output.use { archive.exportTo(kbName, it) }
                true
            } catch (e: CancellationException) {
                // 页面已销毁不是"导出失败"：不回事件，也不许把这次收场写成"正常完成"——
                // 咽掉取消会让 job.isCancelled=false，等它的人以为真导出完了。
                L.w("knowledge export cancelled")
                throw e
            } catch (e: Exception) {
                L.e("knowledge export failed", e)
                false
            }
            _events.tryEmit(if (ok) KbEvent.Exported else KbEvent.ExportFailed)
        }
    }

    /**
     * 从 [source] 导入。解压+校验在 IO 线程；成功后强制修正 active，
     * 防止导入库自带 active=true 造成双激活。
     */
    fun import(source: Uri) {
        viewModelScope.launch(ioContext) {
            val ok = try {
                val input = appContext.contentResolver.openInputStream(source)
                    ?: error("无法打开导入文件")
                input.use { archive.importFrom(it) }
                val currentActive = repo.getActive()?.name ?: repo.listAll().firstOrNull()?.name
                if (currentActive != null) repo.setActive(currentActive)
                true
            } catch (e: CancellationException) {
                // active 修正是挂起调用：取消必须原样上抛，不能被算成"导入失败"再提示用户
                // （原来这里 return@launch 让协程以"正常完成"收场，与注释的说法相反）
                L.w("knowledge import cancelled")
                throw e
            } catch (e: Exception) {
                L.e("knowledge import failed", e)
                false
            }
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
