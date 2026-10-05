package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.feature.roundstate.RoundScope
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 面板侧要接的三件接线，钉在 ViewModel 的真实生产路径上：
 *
 * 1. **通知队列出口**：`currentNotice` 给出通知位上正在显示的那一条，`endCurrentNotice()` 让它真正结束。
 *    同时钉住三条通道的同名只读出口**没有被这次接线换掉**——它们说的是"这条通道最近一次的文案"，
 *    与"正在显示哪一条"是两件事，且已有用例在读它们。
 * 2. **长按 = 仅看本轮**：`generate(RoundScope.CurrentRoundOnly)` 把只读本轮用在**这一次请求**上
 *    （冻结进生成输入与上下文），而长期开关逐字不动；普通点击（不传参）跟着开关走。
 *    再加一条最容易漏的：长按产出的结果不该被随后的 `checkInputChanged()` 判成 stale——
 *    那条判据读的是长期开关，而长按不拨开关，两边不同一条判据就会让刚生成的卡片立刻变灰。
 * 3. **设置页两个薄口子**：写活动工单的超时档位是**纯配置写**（不许顺手重新探测连接）；
 *    面板背景浓度对外交的是整数百分比（100 = 原本那一档），越界值先落回合法刻度再落盘。
 *
 * 队列本身的排队/不提前倒计时判据归 `NoticeBoardTest`，本轮开关本身的语义归 `RoundStateStore` 的测试；
 * 这里只判"VM 这一格口子是不是真的接到了那两边"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelWiringViewModelHooksTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var prefs: SecurePrefs
    private lateinit var deepSeek: DeepSeekRepository
    private lateinit var storedTickets: MutableList<ProviderTicket>

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
        storedTickets = mutableListOf()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    // ═══════════ 装配 ═══════════

    private fun makeVm(
        engine: GenerationEngine = mockk(relaxed = true),
        readBackdropOpacityPercent: (() -> Int)? = null,
        writeBackdropOpacityPercent: ((Int) -> Unit)? = null
    ): LoveBrainViewModel {
        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } answers { storedTickets.toList() }
        every { prefs.setWorkerTickets(any()) } answers {
            storedTickets = firstArg<List<ProviderTicket>>().toMutableList()
        }
        every { prefs.activeTicketId } answers { storedTickets.firstOrNull()?.id }
        every { prefs.totalAdoptCount } returns 0

        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kb = KnowledgeBase(name = "test-kb", displayName = "她", stage = "暧昧期", active = true)
        coEvery { knowledgeRepo.getActive() } returns kb
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.listAll() } returns listOf(kb)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0

        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            PromptBuilder.ConfigValidationResult(0, 0, emptyList())
        every { promptBuilder.replyPromptAssetHash() } returns "reply-asset-hash"

        deepSeek = mockk(relaxed = true)
        return LoveBrainViewModel(
            deepSeekRepo = deepSeek,
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = engine,
            operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(
                CoroutineScope(SupervisorJob())
            ),
            readBackdropOpacityPercent = readBackdropOpacityPercent,
            writeBackdropOpacityPercent = writeBackdropOpacityPercent
        )
    }

    /** 记录传进 Engine 的那份冻结输入，并流一条成功结果 */
    private fun capturingEngine(): Pair<GenerationEngine, MutableList<GenerationInput>> {
        val captured = mutableListOf<GenerationInput>()
        val engine = mockk<GenerationEngine>(relaxed = true)
        every { engine.replyStream(any()) } answers {
            val input = firstArg<GenerationInput>()
            captured += input
            EventRecorder().apply { requestId = input.requestId }.record {
                onReplyStart()
                onReplyResult(GenerateResult.Success(response(input.requestId)))
            }
        }
        return engine to captured
    }

    private fun response(tag: String): LoveBrainResponse = LoveBrainResponse(
        response = ReplySchemes(recommended = "reply-$tag"),
        analysis = ReplyAnalysis(topic_status = "same", topic_label = "test")
    )

    // ═══════════ 1. 通知队列出口 ═══════════

    @Test
    fun `notice slot exposes the one being shown and the three channel exits stay readable`() = runBlocking {
        val vm = makeVm()
        delay(120)

        vm.showPanelWarning("还没有配置模型")

        val shown = vm.currentNotice.value
        assertNotNull("投递时通知位空着，就该立即显示而不是空转到下一次投递", shown)
        assertEquals("面板读的那一格必须就是刚投递的文案", "还没有配置模型", shown?.message)
        assertEquals(NoticeBoard.Channel.Warning, shown?.channel)
        assertNotNull("面板级警告仍带原有的自动过期时限", shown?.autoDismissMillis)
        // 三条通道的同名只读出口没有被队列出口换掉
        assertEquals("通道内容仍从各通道读得到（有用例在读这三员）", "还没有配置模型", vm.panelWarning.value)
        assertNull(vm.kbNotice.value)
        assertNull(vm.vectorUpdate.value)

        vm.endCurrentNotice()
        assertNull("结束后通知位空着", vm.currentNotice.value)
        assertNull("结束后这条通道的内容也一并抹掉", vm.panelWarning.value)
    }

    @Test
    fun `same channel written twice refreshes the shown notice instead of queueing a second one`() = runBlocking {
        val vm = makeVm()
        delay(120)

        vm.showPanelWarning("输入已变化")
        val firstId = vm.currentNotice.value?.id
        vm.showPanelWarning("输入已变化，可重试")

        assertEquals("同通道再写是刷新那一条，不另起一条、不排队", "输入已变化，可重试", vm.currentNotice.value?.message)
        assertTrue("刷新要换号，面板那张表才重起", (vm.currentNotice.value?.id ?: -1) != firstId)
        assertEquals("通道里也只剩最新那一条", "输入已变化，可重试", vm.panelWarning.value)
    }

    // ═══════════ 2. 长按 = 仅看本轮 ═══════════

    @Test
    fun `long press uses current round only for that request and never flips the durable switch`() = runBlocking {
        val (engine, captured) = capturingEngine()
        val vm = makeVm(engine)
        vm.refreshKnowledgeBases()
        delay(200)
        vm.addMessage(ChatMessage.Role.HER, "在吗")

        vm.generate(RoundScope.CurrentRoundOnly)
        delay(300)

        assertEquals("这一次请求真的只读本轮", 1, captured.size)
        assertTrue(captured[0].onlyThisRound)
        assertFalse("长按不拨长期开关——否则下一次普通点击就悄悄变成永久忽略历史",
            vm.onlyThisRound.value)
        assertNotNull(vm.result.value)
    }

    @Test
    fun `plain click keeps following the durable switch`() = runBlocking {
        val (engine, captured) = capturingEngine()
        val vm = makeVm(engine)
        vm.refreshKnowledgeBases()
        delay(200)
        vm.addMessage(ChatMessage.Role.HER, "在吗")

        vm.generate()
        delay(300)
        assertFalse("不传参 = 跟着关闭的长期开关走，与从前逐字一致", captured[0].onlyThisRound)

        vm.toggleOnlyThisRound()
        vm.generate()
        delay(300)
        assertTrue("开关拨上之后普通点击才只读本轮", captured[1].onlyThisRound)
        assertTrue(vm.onlyThisRound.value)
    }

    @Test
    fun `long press result is not judged stale by the switch based fingerprint check`() = runBlocking {
        val (engine, _) = capturingEngine()
        val vm = makeVm(engine)
        vm.refreshKnowledgeBases()
        delay(200)
        vm.addMessage(ChatMessage.Role.HER, "在吗")

        vm.generate(RoundScope.CurrentRoundOnly)
        delay(300)
        assertTrue(vm.result.value is GenerateResult.Success)
        assertFalse(vm.inputChanged.value)

        // 输入一个字没改，只是那一次请求多带了"只读本轮"——判据不许因此把新结果判旧
        vm.checkInputChanged()
        assertFalse("长按产出的结果不能因为长期开关仍是关闭就被判成输入已变化", vm.inputChanged.value)
    }

    @Test
    fun `switching the durable switch still marks a plain click result stale`() = runBlocking {
        val (engine, captured) = capturingEngine()
        val vm = makeVm(engine)
        vm.refreshKnowledgeBases()
        delay(200)
        vm.addMessage(ChatMessage.Role.HER, "在吗")

        vm.generate()
        delay(300)
        assertFalse("普通点击跟着关闭的开关", captured[0].onlyThisRound)
        assertFalse(vm.inputChanged.value)

        // 上面那一笔放宽不能顺手把这条判据一起放掉：开关拨动 = 有效范围变了 = 旧结果作废
        vm.toggleOnlyThisRound()
        assertTrue("切换长期开关仍旧把普通点击的旧结果标 stale", vm.inputChanged.value)
    }

    // ═══════════ 3a. 超时档位：纯配置写 ═══════════

    @Test
    fun `writing the active ticket timeout tier saves config without probing the connection`() = runBlocking {
        val ticket = ProviderTicket(
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            model = "deepseek-chat",
            models = listOf("deepseek-chat", "deepseek-reasoner"),
            generateTimeoutSec = 60
        )
        storedTickets = mutableListOf(ticket)

        val vm = makeVm()
        delay(120)
        assertEquals("活动工单快照先落到 60 档", 60, vm.activeTicket.value!!.generateTimeoutSec!!)

        vm.setActiveTicketGenerationTimeout(GenerationTimeoutTier.SEC_180)
        delay(200)

        assertEquals("盘上写的是档位秒数", 180, storedTickets.single().generateTimeoutSec)
        assertEquals("其余字段一个字没动", ticket.copy(generateTimeoutSec = 180), storedTickets.single())
        assertEquals("设置页的选中态读的就是这颗快照", 180, vm.activeTicket.value!!.generateTimeoutSec)
        coVerify(exactly = 0) { deepSeek.testConnectionWithProbe(any(), any(), any()) }
    }

    @Test
    fun `timeout tier write is a no-op when there is no active ticket`() = runBlocking {
        storedTickets = mutableListOf()
        val vm = makeVm()
        delay(120)

        vm.setActiveTicketGenerationTimeout(GenerationTimeoutTier.SEC_300)
        delay(200)

        assertTrue("没有活动工单就不该凭空造出一张", storedTickets.isEmpty())
        coVerify(exactly = 0) { deepSeek.testConnectionWithProbe(any(), any(), any()) }
    }

    // ═══════════ 3b. 面板背景浓度：整数百分比 ═══════════

    @Test
    fun `backdrop opacity is exposed as an integer percent that round trips through the store`() = runBlocking {
        var persisted = PanelBackdropOpacity.DEFAULT_PERCENT
        val vm = makeVm(
            readBackdropOpacityPercent = { persisted },
            writeBackdropOpacityPercent = { persisted = it }
        )
        delay(120)

        assertEquals("没滑过滑杆 = 100%，面板与从前逐字同形",
            100, vm.panelBackdropOpacityPercent)

        vm.setPanelBackdropOpacityPercent(70)
        assertEquals(70, persisted)
        assertEquals("读回来的就是刚写进去的那个数", 70, vm.panelBackdropOpacityPercent)
        assertEquals("画的那一步就取这一格的换算口", 0.7, PanelBackdropOpacity.alphaOf(persisted).toDouble(), 0.0001)

        vm.setPanelBackdropOpacityPercent(62)
        assertEquals("刻度外的值先落回合法刻度再落盘，显示与盘上不分家", 60, persisted)
        assertEquals(60, vm.panelBackdropOpacityPercent)

        vm.setPanelBackdropOpacityPercent(0)
        assertEquals("0 不可能变成\u201c面板看不见\u201d，钳到可读性下限", PanelBackdropOpacity.MIN_PERCENT, persisted)

        vm.setPanelBackdropOpacityPercent(140)
        assertEquals("越上界钳回完全不透明", PanelBackdropOpacity.MAX_PERCENT, persisted)
    }

    @Test
    fun `backdrop opacity falls back to the untouched default when the store seam is not wired`() = runBlocking {
        val vm = makeVm()
        delay(120)

        assertEquals(PanelBackdropOpacity.DEFAULT_PERCENT, vm.panelBackdropOpacityPercent)
        // 装配侧少接一行时：写不炸、读仍是那一档合法值，设置页照常能画
        vm.setPanelBackdropOpacityPercent(62)
        assertEquals(PanelBackdropOpacity.DEFAULT_PERCENT, vm.panelBackdropOpacityPercent)
    }
}
