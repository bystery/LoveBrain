package com.lovebrain.app.testing

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.di.appModule
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.ui.panel.LoveBrainPanelScreen
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * S1-03（审计 §8.1 第 4 条）：主链测试夹具。
 *
 * 设计约束（决定了这里为什么长这样）：
 * 1. 本任务禁止修改 app/src/main 下的生产代码，也不能新增 androidTest 依赖。生产
 *    LoveBrainViewModel / GenerationEngine / DeepSeekRepository / PromptBuilder 均为
 *    final 类 → 无法 fake 子类化，也没有 mockk-android。
 * 2. 因此依赖装配直接复用生产 Koin appModule（LoveBrainApp.onCreate 已启动），
 *    Provider 用 [FakeProviderServer]（loopback HTTP/SSE）注入 —— 测试跑真链路：
 *    真 ViewModel → 真 GenerationEngine → 真 DeepSeekRepository → fake Provider。
 * 3. 只使用生产 UI/ViewModel 现在就公开、且被生产面板自身调用的 API
 *    （messages / addMessage / generate / stopGeneration / composerMode / result /
 *    isGenerating / providerReady / refreshTicketState / draftText / setDraft），
 *    避免把测试绑到正在重构的 GenerationInput / DomainEvent / ReplyUiState 上。
 */
object MainChainHarness {

    /** fake 工单固定 ID，tearDown 精准清理，不污染用户数据 */
    const val FAKE_TICKET_ID = "instrumentation-fake-provider"

    fun app(): Application = ApplicationProvider.getApplicationContext()

    /** 生产 Koin 图。instrumentation 跑真实 Application，通常已由 LoveBrainApp 启动。 */
    fun koin(): Koin {
        GlobalContext.getOrNull()?.let { return it }
        return startKoin {
            androidContext(app())
            modules(appModule)
        }.koin
    }

    /** 每次取一个新的生产 ViewModel 实例（appModule 中 viewModel 定义为工厂） */
    fun newViewModel(): LoveBrainViewModel = koin().get()

    fun securePrefs(): SecurePrefs = koin().get()

    fun deepSeekRepository(): DeepSeekRepository = koin().get()

    /**
     * 用生产解析器构造一份「只有推荐那一格有内容」的成功结果模型。
     *
     * ⚠ 入参是一句**回复正文**，不是 JSON：它会被塞进 validReplyJson 的 recommended 槽，
     * 另外三格（清醒/俏皮/温柔）与 directions 都是空的。要四格齐全 + 方向列表的模型，
     * 用下面的 [parseProviderText] 直接喂一段完整 Provider 文本。
     * 这个名字以前叫 parsedSuccess，结果有人把整段 JSON 当正文传了进来——
     * 解析出来的方案卡只剩一条、文本是那一整段 JSON，断言于是找不着节点。
     */
    fun successWithOnlyRecommendedReply(recommended: String): LoveBrainResponse =
        parseProviderText(FakeProviderServer.validReplyJson(recommended))

    /** 用生产 parseReplyResponse 解析任意 Provider 文本（要四风格/方向样本时走这条） */
    fun parseProviderText(providerText: String): LoveBrainResponse =
        deepSeekRepository().parseReplyResponse(providerText)

    /** 清掉 provider 配置（无 Provider 场景 + 防用例间互相污染） */
    fun clearProviderConfig() {
        val prefs = securePrefs()
        prefs.deleteWorkerApiKey(FAKE_TICKET_ID)
        prefs.activeTicketId?.let { id -> prefs.deleteWorkerApiKey(id) }
        prefs.setWorkerTickets(emptyList())
        prefs.activeTicketId = null
    }

    /** 把激活工单指向 fake Provider：这是「不改生产代码即可注入 Provider」的唯一出口 */
    fun installFakeProvider(server: FakeProviderServer) {
        val prefs = securePrefs()
        val ticket = ProviderTicket(
            id = FAKE_TICKET_ID,
            name = "instrumentation fake",
            baseUrl = server.baseUrl,
            model = "instrumentation-fake-model"
        )
        prefs.setWorkerTickets(listOf(ticket))
        prefs.saveWorkerApiKey(ticket.id, "instrumentation-test-key")
        prefs.activeTicketId = ticket.id
    }

    /** 面板不再渲染首启引导卡片，保证节点树确定（键值与 LoveBrainPanelScreen 一致） */
    fun skipOnboarding() {
        app().getSharedPreferences("lovebrain_onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("done", true).apply()
    }

    /** 让 VM 重读工单配置，并等待 providerReady 落到期望值（refreshTicketState 异步） */
    fun awaitProviderReady(vm: LoveBrainViewModel, ready: Boolean) {
        vm.refreshTicketState()
        await("providerReady 应为 $ready（实际 ${vm.providerReady.value}）") {
            vm.providerReady.value == ready
        }
    }

    /**
     * R1 诊断：把"点过生成按钮之后请求到底走到哪一段"切成**可读的三段**，
     * 不改生产代码、不猜因果。CI run 36214822274 上 5 格红都停在
     * `PERF t2 request enqueued` 之后——而 `API onFailure` / `API error code=` /
     * `API stats` 在全部 45 份逐格 logcat 里**一条都没有**（`CONNECT_TIMEOUT_SEC=15`
     * 却没触发连接超时），所以必须先有一把尺能说清是下面哪一种：
     *
     * 1. fake 侧 accepted=0 requests=0 ⇒ OkHttp 连 TCP 都没开（请求被挂在别处、
     *    被取消，或 `ProxySelector` 把 127.0.0.1 交给了系统代理）；
     * 2. accepted>0 requests=0 ⇒ 连上了但没收到可解析的请求行；
     * 3. requests>0 ⇒ 请求真到了服务侧，红点在响应回传 / 语义树那半边。
     *
     * 用 instrumentation 线程直接读 `ProxySelector.getDefault()`：生产
     * `OkHttpClient.Builder()`（`DeepSeekRepository.kt:296`，没设 `proxy`）在设备上
     * 走的就是这条选择逻辑，量的是同一个东西。
     */
    fun providerDiagnosis(server: FakeProviderServer): String {
        val uri = java.net.URI(server.baseUrl)
        val proxies = runCatching { java.net.ProxySelector.getDefault().select(uri) }
            .fold(
                { list -> if (list.isEmpty()) "空列表" else list.joinToString(" ") },
                { e -> "查询失败(${e::class.java.simpleName}:${e.message})" }
            )
        return "fake[accepted=${server.acceptedCount} requests=${server.requestCount} " +
            "lastRequestLine='${server.lastRequestLine}'] systemProxy=$proxies " +
            "loopbackSelfTest=${loopbackSelfTest()}"
    }

    /**
     * 本进程能不能做 loopback TCP —— 用**自己的一次性监听口**测，绝不拨 fake 服务的端口：
     * 拨它就等于替被测链路制造一条连接，会把 `requestCount` 和应答队列污染掉。
     */
    private fun loopbackSelfTest(): String {
        return try {
            val loop = java.net.InetAddress.getByName("127.0.0.1")
            val probeServer = java.net.ServerSocket(0, 4, loop)
            val accepted = java.util.concurrent.CountDownLatch(1)
            Thread({
                runCatching { probeServer.accept()?.close() }
                accepted.countDown()
            }, "LoopbackProbe").apply { isDaemon = true; start() }
            val result = runCatching {
                java.net.Socket().use {
                    it.connect(java.net.InetSocketAddress(loop, probeServer.localPort), 2_000)
                }
            }.fold({ "拨号ok" }, { e -> "拨号失败(${e::class.java.simpleName}:${e.message})" })
            val heard = accepted.await(2, java.util.concurrent.TimeUnit.SECONDS)
            runCatching { probeServer.close() }
            "$result/监听端${if (heard) "收到" else "没收到"}"
        } catch (e: Exception) {
            "监听口都没开(${e::class.java.simpleName}:${e.message})"
        }
    }

    /**
     * 轮询等待条件成立。生产状态由 Main/IO 协程推进，instrumentation 线程只能等。
     * 超时抛 AssertionError（带 reason），绝不「等不到就当通过」。
     */
    fun await(reason: String, timeoutMs: Long = 15_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            try {
                Thread.sleep(40L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw AssertionError("await($reason) 被中断", e)
            }
        }
        throw AssertionError("等待超时（${timeoutMs}ms）：$reason")
    }
}

/**
 * 挂载真实生产 Panel —— 与 FloatingService.ensurePanelCreated 的 setContent 同一组件、
 * 同一 LoveBrainTheme 包裹，只把 Service 侧窗口回调替换为测试收集的 no-op。
 *
 * onCopy / onOpenSettings / onCollapse 由调用方收集，用于断言
 * 「点击确实走到生产回调」，而不是测试自造的假按钮。
 */
@Composable
fun ProductionPanel(
    viewModel: LoveBrainViewModel,
    onCopy: (String) -> Unit = {},
    onCollapse: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    LoveBrainTheme {
        LoveBrainPanelScreen(
            viewModel = viewModel,
            onInputFocusChange = { _, _ -> },
            onInputIntent = { },
            onClearComposeFocus = { },
            onResize = { _, _ -> },
            onMove = { _, _ -> },
            onCopy = onCopy,
            onOpenSettings = onOpenSettings,
            onCollapse = onCollapse
        )
    }
}
