package com.lovebrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.domain.port.KnowledgeReadPort
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ui.home.AdvisorMissing
import com.lovebrain.app.ui.home.AdvisorState
import com.lovebrain.app.ui.home.AdvisorStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════
// 首页那盏灯的依赖端口
// ═════════════════════════════════════════════════════════════
//
// 这一格里全部的"事实"都从下面四颗端口进来，VM 自己不认识 HTTP、不认识 Activity、
// 也不在组合期读盘。**消息捕获那类可选辅助权限不在端口里**：手工输入本来就可用，
// 它不该成为点绿的条件（合同 第7节第2条 末段）。

/** 当前供应商身份：只带"有没有 Key"这个布尔，明文 Key 不下这一层 */
data class HomeProviderRef(val id: String, val name: String, val model: String, val hasKey: Boolean) {
    /** 配置齐不齐 = 有没有模型 + 有没有 Key。本地有 Key 只算"配置存在"，不算"连接成功" */
    val usable: Boolean get() = hasKey && model.isNotBlank()
}

enum class HomeKnowledgePresence { Present, Missing, Unknown }

/** 当前对象那一份知识库的读数。[Unknown] 只表示"没读到"，既不是有也不是没有 */
data class HomeKnowledgeSnapshot(val presence: HomeKnowledgePresence, val name: String?)

enum class HomeConnectionVerdict {
    /** 这一组身份下真的发过一次微请求并且成功 */
    Verified,

    /** 试过，连不上 */
    Failed,

    /** 没试过（端口没接线，或旧结论不属于当前身份） */
    NotChecked,

    /** 没试过，而且不该试：配置本身就不齐，缺项已经由 [AdvisorMissing.NoProvider] 说过了 */
    NotApplicable
}

sealed class HomeProbeOutcome {
    data object Connected : HomeProbeOutcome()

    /** 带原因的形状已经删掉：界面与日志都不念内部失败细节，这里只留"连不上"这一件事 */
    data object Unreachable : HomeProbeOutcome()
}

/** 服务与悬浮窗：活没活、怎么关、变化脉冲 */
interface HomeServicePort {
    fun isRunning(): Boolean
    fun stop()

    /** 只在服务真的开关过的时候响一次；收到之后**只重读事实，不发任何请求** */
    fun runningChanges(): Flow<Boolean>
}

interface HomeProviderPort {
    /** 当前激活工单；没有就交回 null（= 未配置供应商） */
    fun currentProvider(): HomeProviderRef?
}

interface HomeKnowledgePort {
    suspend fun currentObjectKb(): HomeKnowledgeSnapshot
}

interface HomeProbePort {
    /** 一次、有界：不带真实对话，只发最小测试文本（请求链在 `DeepSeekRepository.testConnectionWithProbe`） */
    suspend fun probe(ref: HomeProviderRef): HomeProbeOutcome
}

// ═══════════ 端口实现：只接容器里已有的那三颗单例，不在这里新建客户端也不新建状态主人 ═══════════

object FloatingServiceHomePort : HomeServicePort {
    override fun isRunning(): Boolean = FloatingService.instance != null

    // 关的是服务本体，onDestroy 会把悬浮球与面板一起摘掉、instance 置空、窗口态落回 STOPPED
    override fun stop() {
        FloatingService.instance?.stopSelf()
    }

    override fun runningChanges(): Flow<Boolean> =
        FloatingService.windowStateFlow.map { FloatingService.instance != null }
}

/**
 * 供应商身份这一颗读的是**容器里那唯一一颗偏好存储**（`SettingsStorePort` 与 `SecurePrefs`
 * 在 `AppModule` 里解析到同一个实例），不是任何一颗 ViewModel 的内存快照。
 *
 * 为什么上一版那句 `SetupHomeProviderPort(setup)` 不能并进容器：`viewModel {}` 在 Koin 里是
 * **工厂语义**（`AppModuleGraphTest` 那条 `assertNotSame` 就是钉这件事的），所以在 DI 里
 * `get<SetupViewModel>()` 会拿到**第二颗** SetupViewModel——它的 `_tickets` / `_activeTicket`
 * 是它自己构造时读的那一份。于是"当前供应商"就有了两个主人：供应商页改的是 A 那颗，
 * 灯读的是 B 那颗，"切了供应商"对灯永远是没发生。盘上那一份才是源、VM 里那两份只是它的缓存，
 * 所以灯直接读盘（读的是解密后的布尔，明文 Key 不过这一层）。
 */
class SettingsStoreHomeProviderPort(private val store: SettingsStorePort) : HomeProviderPort {
    override fun currentProvider(): HomeProviderRef? {
        val id = store.activeTicketId ?: return null
        val ticket = store.getWorkerTickets().firstOrNull { it.id == id } ?: return null
        return HomeProviderRef(
            id = ticket.id,
            name = ticket.name,
            model = ticket.model,
            hasKey = !store.getWorkerApiKey(ticket.id).isNullOrBlank()
        )
    }
}

/**
 * 探针：仍然走 [DeepSeekRepository.testConnectionWithProbe] 那一条链（端点候选 + 参数降级，
 * 有界、不循环、只发最小测试文本），只是不再借道某颗 VM——借道 VM 就会复制上面那个"两个主人"的病。
 * Key 从存储里现取，明文不进界面、不进日志。
 */
class GatewayHomeProbePort(
    private val store: SettingsStorePort,
    private val gateway: DeepSeekRepository
) : HomeProbePort {
    override suspend fun probe(ref: HomeProviderRef): HomeProbeOutcome {
        val ticket = store.getWorkerTickets().firstOrNull { it.id == ref.id }
            ?: return HomeProbeOutcome.Unreachable        // 工单在检查途中没了：算连不上，等下一次开始
        val key = store.getWorkerApiKey(ticket.id)
        // 走到这里 ref.usable 已经成立（没 Key 就不该发请求，那一条由 NoProvider 说）。
        // 真读不到 Key（Keystore 降级）时也只报"连不上"：不猜成功。
        if (key.isNullOrBlank()) return HomeProbeOutcome.Unreachable
        val result = gateway.testConnectionWithProbe(key, ticket.model, ticket.baseUrl)
        return if (result.success) HomeProbeOutcome.Connected else HomeProbeOutcome.Unreachable
    }
}

/** 读不到就报 Unknown，绝不替用户猜"有知识库" */
object UnknownHomeKnowledgePort : HomeKnowledgePort {
    override suspend fun currentObjectKb(): HomeKnowledgeSnapshot =
        HomeKnowledgeSnapshot(HomeKnowledgePresence.Unknown, null)
}

/**
 * 走 DI 里**已注册**的那颗 `KnowledgeReadPort`（构造注入，见文件末尾那颗装配函数）：
 * 当前对象 = active 知识库。不 new 仓库、不 import 具体实现类
 * （`PackageDependencyTest` 那条 viewmodel→data 的账）。
 *
 * **"空库不算已建知识库"**（主线程 2026-10-03 拍，缺项黄字仍按"请为当前对象建立知识库"说）：
 * `kb.json` 上的两个真实计数——`turnCount`（提交过几轮）与 `topicCount`（归档/经验批次数）——
 * 都是 0 时，这一格答 `Missing`。
 * 新建库那一条写死的初值就是 `stage = "待确定"、turnCount = 0`（`KnowledgeCatalogWriteStore.createWithin`），
 * 所以"建了但一格正文都没积累"不会被念成"已就绪"。
 *
 * ⚠ 这里刻意**不**拿 `turnCount` 当"知识内容是什么"：`KnowledgeProfileStore.contentRevision`
 * 的注释已经把这件事写成判据（同一 turnCount 可以对应完全不同的画像正文），所以这一处只把它当
 * "有没有积累过"的下界读数，不参与任何身份比对（身份比用的是库名，见 `identityOf`）。
 */
class ReadPortHomeKnowledgePort(private val read: KnowledgeReadPort) : HomeKnowledgePort {
    override suspend fun currentObjectKb(): HomeKnowledgeSnapshot {
        val kb = try {
            read.getActive()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            return HomeKnowledgeSnapshot(HomeKnowledgePresence.Unknown, null)
        }
        val name = kb?.name?.takeIf { it.isNotBlank() }
            ?: return HomeKnowledgeSnapshot(HomeKnowledgePresence.Missing, null)
        val built = kb.turnCount > 0 || kb.topicCount > 0
        return HomeKnowledgeSnapshot(
            presence = if (built) HomeKnowledgePresence.Present else HomeKnowledgePresence.Missing,
            name = name
        )
    }
}

// ═════════════════════════════════════════════════════════════
// 事实 → 状态：首页那一盏灯唯一的一张派生表
// ═════════════════════════════════════════════════════════════

/** 一次判定的全部输入（没有 lambda、没有颜色，用例可以逐格摆出来比） */
internal data class HomeFacts(
    val userStarted: Boolean,
    val checking: Boolean,
    val overlayGranted: Boolean,
    val serviceRunning: Boolean,
    val provider: HomeProviderRef?,
    val knowledge: HomeKnowledgePresence,
    val connection: HomeConnectionVerdict
)

/**
 * 缺哪几步。**顺序固定**、一行一个，就是黄字那一行念的东西：
 * 权限 → 配置 → 当前对象的知识库 → 连接 → 服务。
 *
 * 两条刻意的取舍：
 * - 没给悬浮窗权限时不再重复念"服务没起来"（那是同一件事的另一半，根因先说）；
 * - 配置不齐时不再念"连接还没检查成功"（[HomeConnectionVerdict.NotApplicable]），
 *   但 `Verified` 这一档没有真凭据就**永远不会出现**，绿也就永远不亮。
 */
internal fun advisorMissingSteps(f: HomeFacts): List<AdvisorMissing> = buildList {
    if (!f.overlayGranted) add(AdvisorMissing.OverlayPermission)
    val ref = f.provider
    if (ref == null || !ref.usable) add(AdvisorMissing.NoProvider)
    when (f.knowledge) {
        HomeKnowledgePresence.Missing -> add(AdvisorMissing.NoKnowledgeBase)
        HomeKnowledgePresence.Unknown -> add(AdvisorMissing.KnowledgeUnread)
        HomeKnowledgePresence.Present -> Unit
    }
    when (f.connection) {
        HomeConnectionVerdict.Failed -> add(AdvisorMissing.ConnectionFailed)
        HomeConnectionVerdict.NotChecked -> add(AdvisorMissing.ConnectionUnchecked)
        HomeConnectionVerdict.Verified, HomeConnectionVerdict.NotApplicable -> Unit
    }
    if (!f.serviceRunning && f.overlayGranted) add(AdvisorMissing.ServiceNotRunning)
}

/**
 * 事实 → 状态四档。灯色、控件、黄字全部由 [com.lovebrain.app.ui.home.render] 从这一条结果再派生，
 * 这里不出现颜色也不出现文案。
 */
internal fun advisorStatusOf(f: HomeFacts): AdvisorStatus = when {
    f.checking -> AdvisorStatus(AdvisorState.Checking)
    !f.userStarted && !f.serviceRunning -> AdvisorStatus(AdvisorState.Stopped)
    else -> {
        val missing = advisorMissingSteps(f)
        AdvisorStatus(
            state = if (missing.isEmpty()) AdvisorState.RunningReady else AdvisorState.RunningNeedsSetup,
            missing = missing
        )
    }
}

/**
 * 首页状态机。
 *
 * 三条纪律写在代码形状里，不是写在注释里：
 * 1. **请求只由用户点 ▶ 发起**：探针唯一的入口是 [playClicked]。组合、重组、`runningChanges` 脉冲、
 *    进页面、从子页返回（[returnedFromSubpage]）全部只重读事实——身份变了也只是**作废**旧结论，
 *    不补发；下一次检查等下一次按 ▶。
 * 2. **迟到响应不能点灯**：每次开始/关闭都推 [checkToken]，回来时号不对（或用户已经关了）就整包丢掉。
 * 3. **绿只认这一组身份下的真凭据**：供应商 id + 生效模型 + 当前对象名，任一项换了旧结论就作废。
 */
class HomeStatusViewModel(
    private val service: HomeServicePort = FloatingServiceHomePort,
    private val provider: HomeProviderPort? = null,
    private val knowledge: HomeKnowledgePort = UnknownHomeKnowledgePort,
    private val probe: HomeProbePort? = null
) : ViewModel() {

    /** 检查冻结下来的那一组身份 + 那一组的连接结论 */
    private data class Evidence(
        val identity: String,
        val connection: HomeConnectionVerdict
    )

    private val _status = MutableStateFlow(AdvisorStatus(AdvisorState.Stopped))
    val status: StateFlow<AdvisorStatus> = _status.asStateFlow()

    private var userStarted = false
    private var checking = false
    private var overlayGrantedAtLastRead = false
    private var evidence: Evidence? = null

    /** 最近一次读到的知识库快照：组合期不读盘，所以 publish 只读这一格缓存 */
    private var lastKnowledge = HomeKnowledgeSnapshot(HomeKnowledgePresence.Unknown, null)

    private var checkJob: Job? = null
    private var checkToken = 0L

    init {
        viewModelScope.launch {
            service.runningChanges().collect { publish() }
        }
    }

    /**
     * 点 ▶。**这是本文件唯一会发探针的入口**。
     * 平台侧的"先查悬浮权限、去授权、启动服务"由调用方那一条 `onStartService` 做
     * （那条链住在 `SetupActivity`，它才认得授权页回来后要续跑）；这里只负责把灯推到
     * [AdvisorState.Checking] 并发**一次**微请求。
     */
    fun playClicked(overlayGranted: Boolean) {
        overlayGrantedAtLastRead = overlayGranted
        if (checking) return
        userStarted = true
        runCheck()
    }

    /** 点 ■：取消在路上的检查、停服务与悬浮窗、灯落回红。之后回来的响应一律作废 */
    fun stopClicked() {
        checkToken++
        checkJob?.cancel()
        checkJob = null
        checking = false
        userStarted = false
        evidence = null
        service.stop()
        publish()
    }

    /**
     * 进页面（冷启动、从子页返回、Activity resume）：**只重读本地事实，一个请求都不发**。
     *
     * 合同那句"不每次重组发微请求"在这里的形状就是"这一条没有探针"：
     * 上一版它会在"当前身份与上次结论不是同一组"时**自己补一次检查**，于是
     * 从供应商页切完回来、或从知识库页切完当前对象回来，都会在没有按下 ▶ 的情况下
     * 真发一次付费请求。现在这一条只作废旧结论（[publish] 里比身份，身份不对就当作没检查过），
     * 灯因此落回黄 + "连接还没检查成功"，要重新检查请用户按 ■ 关掉再按 ▶
     * （合同原话"下次开始再检查"）。
     */
    fun returnedFromSubpage(overlayGranted: Boolean) {
        overlayGrantedAtLastRead = overlayGranted
        viewModelScope.launch {
            lastKnowledge = readKnowledge()
            publish()
        }
    }

    private fun currentProvider(): HomeProviderRef? = provider?.currentProvider()

    private suspend fun readKnowledge(): HomeKnowledgeSnapshot = try {
        knowledge.currentObjectKb()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        HomeKnowledgeSnapshot(HomeKnowledgePresence.Unknown, null)
    }

    /** 冻结身份：换供应商 / 换生效模型 / 切当前对象，任何一项都让旧结论作废 */
    private fun identityOf(ref: HomeProviderRef?, kbName: String?): String =
        "${ref?.id ?: NO_PROVIDER}|${ref?.model ?: ""}|${kbName ?: NO_KB}"

    private fun runCheck() {
        val token = ++checkToken
        checking = true
        publish()
        checkJob = viewModelScope.launch {
            val ref = currentProvider()
            val snapshot = readKnowledge()
            lastKnowledge = snapshot
            val verdict = probeNow(ref)
            if (token != checkToken || !userStarted) return@launch   // 迟到的响应：关掉了/又点了一次，不作数
            evidence = Evidence(identityOf(ref, snapshot.name), verdict)
            checking = false
            publish()
        }
    }

    /**
     * 至多一次请求。配置不齐时**根本不发**（省一次白烧的钱），交回 NotApplicable，
     * 缺项由 NoProvider 那一条说；探针端口没接线时是 NotChecked——两种都不是"连接失败"，
     * 谁也不许被念成失败。
     */
    private suspend fun probeNow(ref: HomeProviderRef?): HomeConnectionVerdict {
        if (ref == null || !ref.usable) return HomeConnectionVerdict.NotApplicable
        val port = probe ?: return HomeConnectionVerdict.NotChecked
        return when (port.probe(ref)) {
            HomeProbeOutcome.Connected -> HomeConnectionVerdict.Verified
            HomeProbeOutcome.Unreachable -> HomeConnectionVerdict.Failed
        }
    }

    private fun publish() {
        val ref = currentProvider()
        val snapshot = lastKnowledge
        val identity = identityOf(ref, snapshot.name)
        val live = evidence?.takeIf { it.identity == identity }
        val facts = HomeFacts(
            userStarted = userStarted,
            checking = checking,
            overlayGranted = overlayGrantedAtLastRead,
            serviceRunning = service.isRunning(),
            provider = ref,
            knowledge = snapshot.presence,
            connection = live?.connection ?: HomeConnectionVerdict.NotChecked
        )
        _status.value = advisorStatusOf(facts)
    }

    private companion object {
        const val NO_PROVIDER = "-"
        const val NO_KB = "-"
    }
}

/**
 * 首页状态 VM 的**唯一装配点**（`di/AppModule.kt` 里那颗 `viewModel {}` 就调这一行）。
 *
 * 四颗端口全部接在容器里**已经存在**的单例上：
 * - 服务/悬浮窗 → 那颗服务单例本身（[FloatingServiceHomePort]，不 new、不启动）；
 * - 供应商身份 → [SettingsStorePort]（= 同一颗 `SecurePrefs`，盘上那一份才是源）；
 * - 当前对象知识库 → [KnowledgeReadPort]（= 同一颗仓库的读口视图）；
 * - 探针 → [DeepSeekRepository.testConnectionWithProbe]（保存/测试连接用的同一条链，
 *   不在页面上造第二个 HTTP 客户端）。
 *
 * 装配收在端口实现旁边而不是散进 `di/`，是为了让"灯读盘、不读某颗 VM 的快照"这件事
 * 留在它能被读懂的那一格，同时 di 这一侧只点名一颗跨层类型（账好核）。
 */
fun newHomeStatusViewModel(
    store: SettingsStorePort,
    knowledge: KnowledgeReadPort,
    gateway: DeepSeekRepository
): HomeStatusViewModel = HomeStatusViewModel(
    service = FloatingServiceHomePort,
    provider = SettingsStoreHomeProviderPort(store),
    knowledge = ReadPortHomeKnowledgePort(knowledge),
    probe = GatewayHomeProbePort(store, gateway)
)
