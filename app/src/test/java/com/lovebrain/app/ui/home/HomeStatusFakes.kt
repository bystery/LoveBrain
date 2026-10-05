package com.lovebrain.app.ui.home

import com.lovebrain.app.viewmodel.HomeKnowledgePort
import com.lovebrain.app.viewmodel.HomeKnowledgePresence
import com.lovebrain.app.viewmodel.HomeKnowledgeSnapshot
import com.lovebrain.app.viewmodel.HomeProbeOutcome
import com.lovebrain.app.viewmodel.HomeProbePort
import com.lovebrain.app.viewmodel.HomeProviderPort
import com.lovebrain.app.viewmodel.HomeProviderRef
import com.lovebrain.app.viewmodel.HomeServicePort
import com.lovebrain.app.viewmodel.HomeStatusViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 首页状态机的共用夹具（viewmodel 那一头的行为用例与 ui 这一头的渲染用例都用这四颗）。
 *
 * 四颗端口全部是**哑**的：不读系统设置、不读盘、不发 HTTP。
 * ⚠ 这一族里**没有"消息捕获辅助权限"这一颗**——这不是漏了，而是合同写死的判据：
 * 可选辅助权限不该阻止"手工输入已可用"点绿，所以它根本不在绿灯的输入里。
 * 哪天有人把它加回判据，`HomeStatusViewModelTest` 里那一格"全都齐就绿"会因为
 * 夹具给不出这个输入而当场红（不是靠读源码读的）。
 */
internal class FakeHomeService(initialRunning: Boolean = false) : HomeServicePort {
    var running = initialRunning
    var stopCount = 0
    val pulse = MutableStateFlow(initialRunning)

    override fun isRunning(): Boolean = running
    override fun stop() {
        stopCount++
        running = false
        pulse.value = false
    }

    override fun runningChanges(): Flow<Boolean> = pulse

    /** 模拟服务起来（生产里这条脉冲来自 `FloatingService.windowStateFlow`） */
    fun markRunning() {
        running = true
        pulse.value = true
    }

    /**
     * 只推一次"状态变了"的脉冲，不改 running：生产里这来自窗口态换档
     * （例如悬浮窗被系统收回、或从通知栏点停止）。VM 收到之后**只重读事实、不发请求**，
     * 所以它是"事实自己漂了没有"那一类判据的触发器。
     */
    fun pulseNow() {
        pulse.value = !pulse.value
    }
}

internal class FakeHomeProvider(
    var ref: HomeProviderRef? = HomeProviderRef("t1", "DeepSeek", "deepseek-chat", hasKey = true)
) : HomeProviderPort {
    override fun currentProvider(): HomeProviderRef? = ref
}

internal class FakeHomeKnowledge(
    var presence: HomeKnowledgePresence = HomeKnowledgePresence.Present,
    var kbName: String? = "她"
) : HomeKnowledgePort {
    var reads = 0
    override suspend fun currentObjectKb(): HomeKnowledgeSnapshot {
        reads++
        return HomeKnowledgeSnapshot(presence, kbName)
    }
}

/**
 * [gate] 不为空时探针就停在半路——"检查中关掉、响应才回来"那一格靠它摆出来，
 * 不是靠 `delay(200)` 赌时序。
 */
internal class FakeHomeProbe(
    var outcome: HomeProbeOutcome = HomeProbeOutcome.Connected
) : HomeProbePort {
    var calls = 0
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun probe(ref: HomeProviderRef): HomeProbeOutcome {
        calls++
        gate?.await()
        return outcome
    }
}

/** 四颗端口 + 被它们装配起来的 VM：一个夹具就是一个可达状态 */
internal class HomeStatusHarness(
    val service: FakeHomeService = FakeHomeService(),
    val provider: FakeHomeProvider = FakeHomeProvider(),
    val knowledge: FakeHomeKnowledge = FakeHomeKnowledge(),
    val probe: FakeHomeProbe = FakeHomeProbe()
) {
    val vm: HomeStatusViewModel = HomeStatusViewModel(service, provider, knowledge, probe)

    /** 全齐那一格：服务在跑 + 点一次开始（探针立刻成功）→ 绿 */
    fun parkReady() {
        service.markRunning()
        vm.playClicked(overlayGranted = true)
    }
}
