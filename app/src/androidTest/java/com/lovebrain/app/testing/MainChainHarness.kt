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
     * 用生产解析器构造一份「成功结果」模型 —— 测试不自己 new ReplySchemes
     * （生产同包内存在两个同名 ReplySchemes，测试端不依赖其构造签名）。
     */
    fun parsedSuccess(marker: String): LoveBrainResponse =
        parseProviderText(FakeProviderServer.validReplyJson(marker))

    /** 用生产 parseReplyResponse 解析任意 Provider 文本（构造 UI 测试要用的四风格/方向样本） */
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
