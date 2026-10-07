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
import com.lovebrain.app.util.CredentialFingerprint
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

/**
 * 当前供应商身份：带"有没有 Key"这个布尔，再带**生效地址与 Key 各自的不可逆摘要片段**
 * （[CredentialFingerprint.of]，SHA-256 前 12 位十六进制）。
 *
 * ⚠ 明文 Key 与明文地址**都不下这一层**：这一颗会被摆进 [HomeFacts]、进 UI 状态、
 * 会随 `toString` 进日志与反馈报告，只有摘要可以说出口。§11.1 要的只是"地址/Key 真变了要能判出来"，
 * 判等用摘要就够，不需要把凭据本身搬来搬去。
 *
 * 无 Key / 无地址时那一段是**空串**（与"有值"天然可区分：有值永远是 12 个十六进制字符）。
 */
data class HomeProviderRef(
    val id: String,
    val name: String,
    val model: String,
    val hasKey: Boolean,
    /** 生效地址的摘要片段：明文 baseUrl 不进身份、不落盘、不进日志（默认空 = 没读到地址） */
    val baseUrlFingerprint: String = "",
    /** API Key 的摘要片段：明文 Key 绝不离开 `data/`（默认空 = 没配 Key） */
    val keyFingerprint: String = ""
) {
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
 * 所以灯直接读盘（读的是解密后的那一条 Key，但它当场只折成"有没有"的布尔与一段不可逆摘要；
 * 明文 Key 与明文地址都不过这一层，见 [HomeProviderRef]）。
 */
class SettingsStoreHomeProviderPort(private val store: SettingsStorePort) : HomeProviderPort {
    override fun currentProvider(): HomeProviderRef? {
        val id = store.activeTicketId ?: return null
        val ticket = store.getWorkerTickets().firstOrNull { it.id == id } ?: return null
        // Key 只在这一个局部里活着：读一次，立刻折成"有没有"这颗布尔与一段不可逆摘要，
        // 明文既不进 ref 也不进身份串（ref 会随 HomeFacts 摆上 UI，还会被 toString 带进日志）。
        val key = store.getWorkerApiKey(ticket.id)
        return HomeProviderRef(
            id = ticket.id,
            name = ticket.name,
            model = ticket.model,
            hasKey = !key.isNullOrBlank(),
            // §11.1：地址与 Key 真变了才让旧成功作废——所以这两位要进身份，
            // 但进的是摘要片段（无 Key ⇒ 空串），不是凭据本身。
            baseUrlFingerprint = CredentialFingerprint.of(ticket.baseUrl),
            keyFingerprint = CredentialFingerprint.of(key)
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
 * **有效已选的库就算已建立**（指导书 §7.4）：`getActive()` 成功读回一个有真名的库，这一格就答 `Present`。
 * `turnCount`、`topicCount`、阶段、正文有没有内容都**不参与存在性**——新建库的初值就是
 * `stage = "待确定"、turnCount = 0`（`KnowledgeCatalogWriteStore.createWithin`），手工编辑画像也不动计数
 * （`KnowledgeProfileStore.contentRevision`），所以"选了库但还没积累"是常态，不该被念成"请为当前对象建立知识库"。
 * 库内容完整度是另一格独立信息，要展示就另立一格，不许回到存在性里来。
 *
 * 另一半判据同样要守住：**没有活动库 / 库名空白（无效引用）→ `Missing`，读取失败 → `Unknown`**——
 * 缺项黄字只对这两格分别念"请建立"/"还没读到"，读取中（冷启动首帧，`lastKnowledge` 默认 `Unknown`）
 * 两者都不是。
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
        // 读到了带真名的活动库就算"已建立"：轮数与归档计数不参与这一判（§7.4）
        return HomeKnowledgeSnapshot(presence = HomeKnowledgePresence.Present, name = name)
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
 * - 配置不齐时不再念"连接还没检查过"（[HomeConnectionVerdict.NotApplicable]，publish 一侧由
 *   [HomeStatusViewModel.connectionFor] 兑现：没配置 = 这一格根本没有"检查"这件事可言），
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
 * **连接结论的账**：身份 → 那一次检查的结论，一条身份记一次，之后一直有效。
 *
 * 为什么必须是"一张按身份的账"而不是"一格上次结论"：上一版 `evidence` 只有一个可空槽，
 * 于是任何一次**事实漂移**（当前对象一时读不到、供应商位读到空）都会算出一个新身份，
 * 而那一次唯一的槽就被新身份占掉或清空——旧身份那点真凭据**被扔掉**了。用户看到的症状就是
 * 「配好了 → 绿 → 切出去再切回来 → 黄，还说'连接还没检查成功'」，可他明明能正常生成。
 * 记成账之后：漂走只是这一格暂时取不到，漂回来（同身份）当场取回，**一次钱都不用再花**。
 *
 * 这一张账只在内存里（跟着 VM 活），落盘要写 `data/` 与 `domain/port/`，本轮没有那一格的写入权。
 */
internal class HomeConnectionLedger {
    private val verdicts = LinkedHashMap<String, HomeConnectionVerdict>()

    fun remember(identity: String, verdict: HomeConnectionVerdict) {
        verdicts[identity] = verdict
    }

    /** 这一组身份检查过没有；没检查过交回 null，**调用方**决定那一句实话怎么说 */
    fun recall(identity: String): HomeConnectionVerdict? = verdicts[identity]

    /** 只清账，不改别的：这是用户按 ■ 的语义（"我主动关掉，下次开始重新看"） */
    fun clear() {
        verdicts.clear()
    }

    /** 判据用的读数：账上记了几组身份 */
    val remembered: Int get() = verdicts.size
}

/**
 * 首页状态机。
 *
 * **检查一次，结果持续有效**（用户原话 2026-10-05：「我们只检查一次可以吗——就是每次首次点击右朝向
 * 的三角形，之后一直是正方形就不用检测了，耗费 token 还谎报」）。三条纪律写在代码形状里：
 * 1. **请求只由用户点 ▶ 发起**：探针唯一的入口是 [playClicked]。组合、重组、`runningChanges` 脉冲、
 *    进页面、从子页返回（[returnedFromSubpage]）全部只重读事实，**一个都不补发**；■（运行中）
 *    期间同样不发。
 * 2. **迟到响应不能点灯**：每次开始/关闭都推 [checkToken]，回来时号不对（或用户已经关了）就整包丢掉。
 * 3. **绿只认这一组身份下的真凭据，而凭据按身份记账、不按身份淘汰**：[HomeConnectionLedger] 里
 *    换供应商 / 换生效模型都会算出一个取不到的新身份（于是当场不绿，见纪律 1 的反面），
 *    切回旧身份则当场取回旧结论；只有 [stopClicked]（用户主动关）才清账。
 *    连接身份只属于供应商配置（§11.1），切知识库不改连接灯。
 */
class HomeStatusViewModel(
    private val service: HomeServicePort = FloatingServiceHomePort,
    private val provider: HomeProviderPort? = null,
    private val knowledge: HomeKnowledgePort = UnknownHomeKnowledgePort,
    private val probe: HomeProbePort? = null,
    private val store: SettingsStorePort? = null
) : ViewModel() {

    private val _status = MutableStateFlow(AdvisorStatus(AdvisorState.Stopped))
    val status: StateFlow<AdvisorStatus> = _status.asStateFlow()

    private var userStarted = false
    private var checking = false
    private var overlayGrantedAtLastRead = false

    /** 最近一次读到的知识库快照：组合期不读盘，所以 publish 只读这一格缓存 */
    private var lastKnowledge = HomeKnowledgeSnapshot(HomeKnowledgePresence.Unknown, null)

    /** 身份 → 连接结论：见 [HomeConnectionLedger]，"检查一次、之后一直有效"就住在这颗里 */
    private val ledger = HomeConnectionLedger()

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

    /**
     * 点 ■：取消在路上的检查、停服务与悬浮窗、灯落回红。之后回来的响应一律作废。
     * **这是全文件唯一清 [ledger] 的地方**——只有用户主动关掉，才算"这次不算了，下次开始重新检查"；
     * 切后台、回前台、返回子页都走不到这里，所以它们作废不了已验证结论。
     */
    fun stopClicked() {
        checkToken++
        checkJob?.cancel()
        checkJob = null
        checking = false
        userStarted = false
        ledger.clear()
        service.stop()
        publish()
    }

    /**
     * 进页面（冷启动、从子页返回、Activity resume）：**只重读本地事实，一个请求都不发**。
     *
     * 合同那句"不每次重组发微请求"在这里的形状就是"这一条没有探针"：
     * 上一版它会在"当前身份与上次结论不是同一组"时**自己补一次检查**，于是从供应商页切完回来、
     * 或从知识库页切完当前对象回来，都会在没有按下 ▶ 的情况下真发一次付费请求。
     *
     * 而**这一轮修掉的是另一半谎报**：上一版只作废、不记账，身份位一漂那点真凭据就没了，
     * 灯因此落回黄 + "连接还没检查过"，可用户的生成一直是好的。现在重读只更新事实
     * （[recordKnowledge]），结论留在 [ledger] 里按身份取得回：同身份漂走又漂回来 = 当场取回，
     * 不作废也不补发；真的换了供应商/模型/对象 = 取不到旧账（当场不绿），同样不补发。
     * 要重新检查，请用户按 ■ 关掉再按 ▶（合同原话"下次开始再检查"）。
     */
    fun returnedFromSubpage(overlayGranted: Boolean) {
        overlayGrantedAtLastRead = overlayGranted
        viewModelScope.launch {
            recordKnowledge(readKnowledge())
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

    /** 唯一写 [lastKnowledge] 的入口 */
    private fun recordKnowledge(snapshot: HomeKnowledgeSnapshot) {
        lastKnowledge = snapshot
    }

    /**
     * 冻结身份：换供应商 / 换生效模型 / **换地址 / 换 Key** 让旧结论取不回来（新身份没账）。
     *
     * 连接身份只属于供应商配置（指导书 §11.1）：切知识库不改连接灯。四位串的形状 =
     * `工单 id | 生效模型 | 地址摘要 | Key 摘要`，逐字对应 §11.1 末句
     * 「**地址/Key/模型真正变化才使旧成功无效**」——少任何一位，绿灯就会带着旧凭据继续；
     * 后两位是 [CredentialFingerprint.of] 出来的 12 位十六进制片段，**明文 Key 与明文地址
     * 永远不在这串里**（这一串要落 `SharedPreferences`、要进日志可达的 `toString`）。
     * 没 Key 那一段是空串；没供应商（[NO_PROVIDER]）整串落在 `-|||` 那一档，语义与旧版一致
     * （配置不齐时连接那一格本来就是 NotApplicable，谁也走不到绿）。
     *
     * **纯函数**：只读 ref 的字段，不读盘、不取时钟、不加盐——同一份配置每次算出同一个串，
     * 这一条算式同时喂 [HomeConnectionLedger.remember]、`store.connectionVerifiedIdentity`
     * 落盘与 [connectionFor] 的兜底比对（三处一个算式，不许有第二份）。
     *
     * ⚠ 这一位**不能**混进任何会自己漂的读数（轮数、归档计数、阶段都不算身份，见 `ReadPortHomeKnowledgePort`）：
     * 漂一次就是一次"谎报没检查过"，还要再烧一次钱才恢复。地址与 Key 不属于会自己漂的读数——
     * 它们只在用户真的改配置时漂。
     */
    private fun identityOf(ref: HomeProviderRef?): String =
        "${ref?.id ?: NO_PROVIDER}|${ref?.model ?: ""}|${ref?.baseUrlFingerprint ?: ""}|${ref?.keyFingerprint ?: ""}"

    private fun runCheck() {
        val token = ++checkToken
        checking = true
        publish()
        checkJob = viewModelScope.launch {
            val ref = currentProvider()
            val snapshot = readKnowledge()
            recordKnowledge(snapshot)
            val verdict = probeNow(ref)
            if (token != checkToken || !userStarted) return@launch   // 迟到的响应：关掉了/又点了一次，不作数
            // 记在身份上，而不是记在"最后一次"上：这一组的凭据之后一直有效（取回不再花钱）
            ledger.remember(identityOf(ref), verdict)
            // 落盘副本：VM 被 Activity 销毁后 ledger 会丢，这颗布尔让重新进页面时
            // 仍能交回 Verified（见 connectionFor 的 ledger 空兜底），不把绿过的灯落回黄。
            when (verdict) {
                HomeConnectionVerdict.Verified -> {
                    store?.connectionVerified = true
                    store?.connectionVerifiedIdentity = identityOf(ref)
                }
                HomeConnectionVerdict.Failed -> {
                    store?.connectionVerified = false
                    store?.connectionVerifiedIdentity = ""
                }
                else -> Unit
            }
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
        val facts = HomeFacts(
            userStarted = userStarted,
            checking = checking,
            overlayGranted = overlayGrantedAtLastRead,
            serviceRunning = service.isRunning(),
            provider = ref,
            knowledge = snapshot.presence,
            connection = connectionFor(ref, identityOf(ref))
        )
        _status.value = advisorStatusOf(facts)
    }

    /**
     * 当前这一组身份的连接结论。**三档出口，每一档都是实话**：
     * - 配置不齐（没供应商 / 没 Key / 没模型）→ [HomeConnectionVerdict.NotApplicable]：
     *   这一格根本没有"检查"这件事，缺项由 `NoProvider` 自己说。派生表注释（本文件 第 201 行那一格）
     *   早就写了"配置不齐时不再念连接那一句"，上一版只在**检查时**兑现、publish 漏了，
     *   于是切回来还会多念一句"连接还没检查过"——那是重复报账，也是把没意义的话摆成缺项；
     * - 账上取得到 → 原样取回：不作废、不补发（"检查一次、结果持续有效"就落在这两行）；
     * - 账上取不到（这组身份真的从没按过 ▶）→ [HomeConnectionVerdict.NotChecked]，
     *   黄字念的是中性事实"连接还没检查过"，**不是**"连接失败"。
     *
     * **落盘兜底**（修"重进屏幕灯落回黄"）：账上取不到 **且账本是空的**（= 全新 VM，Activity 销毁后
     * 重建的那一次）时，读 [SettingsStorePort.connectionVerified]——上一次按 ▶ 真的连通过就交回
     * [HomeConnectionVerdict.Verified]，不把已经绿过的灯落回黄。只在账本空时兜底，不在身份变化时兜底：
     * 换供应商/换模型/切当前对象时 ledger 里已有旧身份的记录（`remembered > 0`），走的是 NotChecked
     * 那一档——旧身份的结论不许替没验过的新身份说话。
     */
    private fun connectionFor(ref: HomeProviderRef?, identity: String): HomeConnectionVerdict {
        if (ref == null || !ref.usable) return HomeConnectionVerdict.NotApplicable
        ledger.recall(identity)?.let { return it }
        // 全新 VM（ledger 整本空）：落盘副本兜底，重进屏幕不落回黄
        // §11.1：只在身份匹配时才兜底——换了供应商/模型不沿用旧结论
        if (ledger.remembered == 0 && store?.connectionVerified == true &&
            store?.connectionVerifiedIdentity == identity) {
            return HomeConnectionVerdict.Verified
        }
        return HomeConnectionVerdict.NotChecked
    }

    private companion object {
        const val NO_PROVIDER = "-"
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
    probe = GatewayHomeProbePort(store, gateway),
    store = store
)
