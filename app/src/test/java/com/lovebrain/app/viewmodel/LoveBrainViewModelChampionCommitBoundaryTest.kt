package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.domain.champion.ChampionEngine
import com.lovebrain.app.domain.champion.ChampionEventStore
import com.lovebrain.app.domain.champion.ChampionPromptAssembler
import com.lovebrain.app.domain.champion.ContractGuard
import com.lovebrain.app.domain.champion.FileChampionEventStorage
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 生产接线回归（审计 S1-P0-1：Champion OFF 零副作用边界 + guide-6 Step 5 提交边界）：
 *
 * - **OFF（显式回退档）零副作用**：用户显式关掉 Champion 时走完整生产提交链
 *   （generate → nextRound → topicRecorder.record → 提交点），champion 事件库
 *   **一个文件都不写、连目录都不创建**。
 * - **SHADOW 正向对照**：同一提交链在 SHADOW 档（scope 空集=全体）正常入库——
 *   证明 OFF 下的空库是模式门禁在起作用，不是提交链本身坏了。提交门禁与
 *   commitConfirmedTurn 都走**冻结快照**（ReplyGenerationContext.championMode →
 *   modeSnapshot），不现场回读设置。
 * - **scope 未命中零副作用**：SHADOW 开着但 contact|story 不在白名单里，同样零 IO。
 * - **冻结一次（guide-6 §15.7）**：模式/轮数在生成开始各只读一次（GenerationInput
 *   快照可断言）；生成中把设置翻成 OFF 不影响本轮提交判定。
 * - **提交边界**：topicRecorder.record 成功才 commitConfirmedTurn；record 失败不提交
 *   （事件库零痕迹），同一轮修复后重试才入库。
 *
 * VM 构造沿用 LikedSchemeRecordingTest 先例：依赖 mock + setMain 接管；
 * ChampionEngine 是**真实实现**（真实 FileChampionEventStorage 落在临时目录），
 * 设置口接被 mock 的 SecurePrefs（与 AppModule 生产图同一端口形状）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoveBrainViewModelChampionCommitBoundaryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var prefs: SecurePrefs
    private lateinit var topicRecorder: TopicRecorder
    private lateinit var generationEngine: GenerationEngine
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    /** champion 事件库根目录（与 AppModule 生产图同形状：filesDir/champion/events） */
    private fun eventsDir(): File = File(tmp.root, "champion/events")

    private fun newEngine(): ChampionEngine {
        // prefs 必须先就位（champion 档位/作用域由测试字段 championMode/championScope 决定）
        check(::prefs.isInitialized) { "prefs 必须在 newEngine 之前初始化" }
        return ChampionEngine(
            gateway = mockk(relaxed = true),   // 提交链不触网关
            eventStore = ChampionEventStore(FileChampionEventStorage(eventsDir())),
            assembler = ChampionPromptAssembler(timestampBlock = { "" }, promptSource = { "" }),
            guard = ContractGuard(),
            settings = prefs,                  // SecurePrefs 即生产 SettingsStorePort 实现
            clock = object : com.lovebrain.app.domain.port.Clock {
                override fun wallClock() = "2026-11-06 10:00"
                override fun today() = "2026-11-06"
                override fun epochMs() = 1_790_000_000_000L
            },
            shadowExecutor = { body -> runBlocking { body() } },   // 本测试不触 shadow
            rootDir = File(tmp.root, "champion")
        )
    }

    private fun newPrefs() {
        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        every { prefs.championEngineMode } returns championMode
        every { prefs.championEngineScope } returns championScope
    }

    /** 组装完整 VM：真实 ChampionEngine（事件库落临时目录）+ 生产形状的依赖替身 */
    private fun newViewModel(): LoveBrainViewModel {
        val kbName = "kb-test"
        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = kbName, stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = kbName, stage = "暧昧期"))
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        // guide-6 Step 5：冻结点读已提交轮数（首轮 record 后口径 = 1）
        coEvery { knowledgeRepo.getTurnCount(any()) } returns 1

        newPrefs()
        topicRecorder = mockk(relaxed = true)
        coEvery {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns false
        generationEngine = mockk(relaxed = true)
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns ConfigValidationResult(0, 0, emptyList())
        every { promptBuilder.replyPromptAssetHash() } returns "reply-asset-hash"

        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = topicRecorder,
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = generationEngine,
            operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            ),
            championEngine = newEngine()
        )
    }

    private var championMode: String = "OFF"
    private var championScope: Set<String> = emptySet()

    /** 冻结断言用：replyStream 收到的 GenerationInput（生成开始时的快照） */
    private var capturedInput: com.lovebrain.app.model.GenerationInput? = null

    private fun stubEngineSuccess() {
        every { generationEngine.replyStream(any()) } answers {
            capturedInput = arg<com.lovebrain.app.model.GenerationInput>(0)
            val callbacks = EventRecorder().apply { requestId = arg<com.lovebrain.app.model.GenerationInput>(0).requestId }
            callbacks.record {
                onReplyStart()
                onReplyResult(
                    GenerateResult.Success(
                        LoveBrainResponse(
                            response = ReplySchemes(recommended = "辛苦了，早点休息"),
                            analysis = ReplyAnalysis(topic_status = "same", topic_label = "日常")
                        )
                    )
                )
                onReplyGenerating(false, false)
                onReplyPanelState(com.lovebrain.app.model.PanelState.AI_RESULT)
            }
        }
    }

    /** 完整生产提交链：输入 → 生成 → 下一轮（正式提交点） */
    private suspend fun runOneCommittedRound(vm: LoveBrainViewModel) {
        vm.refreshKnowledgeBases()
        delay(100)
        vm.addMessage(ChatMessage.Role.HER, "今天好累")
        stubEngineSuccess()
        vm.generate()
        delay(200)
        vm.nextRound()
        delay(500)
        coVerifyTopicRecorded()
    }

    private fun coVerifyTopicRecorded() {
        io.mockk.coVerify {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `off mode leaves the champion event library empty after a committed round`() = runBlocking {
        championMode = "OFF"
        val vm = newViewModel()
        runOneCommittedRound(vm)
        // 产品链路本身正常走完（topicRecorder 已 record），但 champion 事件库零副作用：
        assertFalse("OFF 时不创建 champion/events 目录", eventsDir().exists())
        val store = ChampionEventStore(FileChampionEventStorage(eventsDir()))
        assertTrue("OFF 时事件库仍为空", store.events("kb-test__kb-test").isEmpty())
    }

    @Test
    fun `shadow mode commits the confirmed turn through the production commit chain`() = runBlocking {
        championMode = "SHADOW"
        championScope = emptySet()   // 空集 = 全体
        val vm = newViewModel()
        runOneCommittedRound(vm)
        // 正向对照：同一提交链在 SHADOW 档真实入库（HER 真实消息、turn=1）
        val store = ChampionEventStore(FileChampionEventStorage(eventsDir()))
        val events = store.events("kb-test__kb-test")
        assertEquals(1, events.size)
        assertEquals("HER", events.single().role)
        assertEquals("今天好累", events.single().text)
        assertEquals(1, events.single().turn)
        // 回复候选永不入库
        assertTrue(events.none { it.text.contains("辛苦了") })
    }

    @Test
    fun `scope miss keeps the event library empty even in shadow mode`() = runBlocking {
        championMode = "SHADOW"
        championScope = setOf("other-contact|other-story")   // kb-test 不在白名单
        val vm = newViewModel()
        runOneCommittedRound(vm)
        assertFalse("scope 未命中不创建 champion/events 目录", eventsDir().exists())
        val store = ChampionEventStore(FileChampionEventStorage(eventsDir()))
        assertTrue(store.events("kb-test__kb-test").isEmpty())
    }

    @Test
    fun `mode snapshot frozen at generation start survives a mid generation settings change`() = runBlocking {
        championMode = "SHADOW"
        championScope = emptySet()
        val vm = newViewModel()
        var recordedMessages: List<ChatMessage> = emptyList()
        coEvery {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            recordedMessages = arg<List<ChatMessage>>(1)
            false
        }
        vm.refreshKnowledgeBases()
        delay(100)
        vm.addMessage(ChatMessage.Role.HER, "今天好累")
        stubEngineSuccess()
        vm.generate()
        delay(200)
        // 冻结断言（§15.7「冻结一次」）：GenerationInput 携带生成开始时的模式与轮数快照，
        // ReplyGenerationContext 冻结同一份模式（提交门禁的唯一判据）
        assertEquals("SHADOW", capturedInput?.championMode)
        assertEquals(1, capturedInput?.kbContext?.committedTurnCount)
        // 生成中把设置翻成 OFF——本轮提交判定只认冻结快照，不受影响
        every { prefs.championEngineMode } returns "OFF"
        vm.nextRound()
        delay(500)
        val store = ChampionEventStore(FileChampionEventStorage(eventsDir()))
        val events = store.events("kb-test__kb-test")
        assertEquals("生成中改设置不影响本轮：冻结快照 SHADOW 照常提交", 1, events.size)
        assertEquals("今天好累", events.single().text)
        assertEquals("turn = record 成功后的 getTurnCount", 1, events.single().turn)
        // msgUid = ChatMessage.id（审计 F2-1 去重身份）
        assertEquals(recordedMessages.first().id, events.single().msgUid)
    }

    @Test
    fun `champion commit only happens after topicRecorder record succeeds`() = runBlocking {
        championMode = "SHADOW"
        championScope = emptySet()
        val vm = newViewModel()
        vm.refreshKnowledgeBases()
        delay(100)
        vm.addMessage(ChatMessage.Role.HER, "今天好累")
        stubEngineSuccess()
        var recordCalls = 0
        coEvery {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            recordCalls++
            throw RuntimeException("disk full")
        }
        vm.generate()
        delay(200)
        vm.nextRound()
        delay(500)
        assertEquals("record 失败（本轮保存失败路径）", 1, recordCalls)
        assertFalse("record 失败不提交冠军事件（提交点在 record 成功之后）", eventsDir().exists())
        // 修复后重试同一轮（失败不清 context，用户可重试）：record 成功 → 提交点才走到
        coEvery {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns false
        vm.nextRound()
        delay(500)
        val store = ChampionEventStore(FileChampionEventStorage(eventsDir()))
        assertEquals("record 成功后才 commitConfirmedTurn", 1, store.events("kb-test__kb-test").size)
    }
}
