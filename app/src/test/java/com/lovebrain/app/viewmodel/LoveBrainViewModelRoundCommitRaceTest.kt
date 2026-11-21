package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.KnowledgeTriggerCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyMemoryRefs
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.ReplySourceAliasMap
import com.lovebrain.app.model.ReplyStarted
import com.lovebrain.app.model.SchemeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 回复轮次收口那一族的回归（纯 JVM，构造沿用 `GenerationRollbackTest` 先例）。
 *
 * 每条判据都对应一个**用户看得见**的后果：
 * 1. 成功收口要把 `memoryRefs` / `sourceAliasMap` 写回 live context——
 *    否则最新一次成功永远"没有引用"，下一轮的场景事实来源也解析不出来；
 * 2. `nextRound` 的写盘是异步的：写完时用户可能已经点了新的生成，旧收尾不许清掉新请求；
 * 3. `stopGeneration` 只在**真的停掉一个任务**时才清 context——已落定那轮不能被它抹掉；
 * 4. 成功收口复位"输入已变化"前必须重算指纹（生成期间用户可能又改了输入）；
 * 5. 失败轮不产出快照，但必须让版本栈知道——否则下一次回退会删掉最近一条成功版；
 * 6. 协调器拒绝要有可见反馈（生成与改写两条入口，不再只有一行日志）；
 * 7. 后台三引擎收集单飞：连续两轮话题轮换不许并发跑两遍；
 * 8. `refreshKnowledgeBases` 的慢读不许把旧激活库写回。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoveBrainViewModelRoundCommitRaceTest {

    private val kb = KnowledgeBase(name = "kb-a", stage = "暧昧期")

    private fun ref(id: String, text: String) = MemoryRef(
        id = id,
        kbId = kb.name,
        kind = MemoryKind.PROFILE,
        text = text,
        sourcePath = "understand/me.md"
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun stubRepo(repo: KnowledgeRepository) {
        coEvery { repo.getActive() } returns kb
        coEvery { repo.ensureInitialKnowledgeBase() } returns Unit
        coEvery { repo.migrateIfNeeded(any()) } returns Unit
        coEvery { repo.readVector(any()) } returns emptyMap()
        coEvery { repo.readIntent(any()) } returns IntentConfig()
        coEvery { repo.readProfile(any()) } returns ""
        coEvery { repo.contentRevision(any()) } returns ""
        coEvery { repo.listAll() } returns listOf(kb)
        coEvery { repo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, MemoryCorrection>() to 0)
        coEvery { repo.getCorrectionsRevision(any()) } returns 0
        coEvery { repo.getLessonCount(any()) } returns 0
        coEvery { repo.getTurnCount(any()) } returns 1
        coEvery { repo.readFile(any(), any()) } returns ""
        coEvery { repo.writeFile(any(), any(), any()) } returns true
    }

    private fun newVm(
        repo: KnowledgeRepository,
        engine: GenerationEngine,
        recorder: TopicRecorder = mockk(relaxed = true),
        triggerCoordinator: KnowledgeTriggerCoordinator = mockk(relaxed = true)
    ): LoveBrainViewModel {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        every { prefs.totalAdoptCount } returns 0
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any()) } returns
            PromptBuilder.ConfigValidationResult(0, emptyList())
        every { promptBuilder.replyPromptAssetHash() } returns "reply-asset-hash"
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = repo,
            promptBuilder = promptBuilder,
            topicRecorder = recorder,
            securePrefs = prefs,
            triggerCoordinator = triggerCoordinator,
            generationEngine = engine,
            operationCoordinator = ForegroundOperationCoordinator(
                CoroutineScope(SupervisorJob())
            )
        )
    }

    private fun response(text: String) = LoveBrainResponse(
        response = ReplySchemes(recommended = text),
        analysis = ReplyAnalysis(topic_status = "same", topic_label = "test")
    )

    private fun replyText(result: GenerateResult?): String? =
        (result as? GenerateResult.Success)?.response?.schemes?.first()?.reply

    // ════════════════════════════════════════════════════════════════
    // 1. 成功收口写回 memoryRefs / sourceAliasMap
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `successful round writes refs and alias map back into the live context`() = runBlocking {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        val refA = ref("mem-1", "她喜欢周五见面")
        val alias = mapOf("her-0" to "real-msg-id-1")
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            flow {
                emit(ReplyStarted(requestId))
                emit(ReplyMemoryRefs(requestId, listOf(refA)))
                emit(ReplySourceAliasMap(requestId, alias))
                emit(ReplyCompleted(requestId, GenerateResult.Success(response("reply-1"))))
            }
        }
        val aliasSlot = slot<Map<String, String>>()
        val recorder = mockk<TopicRecorder>(relaxed = true)
        coEvery {
            recorder.record(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), capture(aliasSlot)
            )
        } returns false

        val vm = newVm(repo, engine, recorder)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "周五见？")
        vm.generate()
        delay(300)

        // ① 最新一次成功必须能读到引用（写回 live context，不只是记进版本栈）
        assertEquals(listOf(refA), vm.getCurrentMemoryRefs())

        // ② 下一轮保存时带的别名表就是真实消息 ID（空表会让场景事实的来源校验落空）
        vm.nextRound()
        delay(400)
        assertEquals(alias, aliasSlot.captured)
        coVerify {
            recorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 2. nextRound 异步收尾 vs 新请求
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `a save finishing late does not clear the newer request owner or context`() = runBlocking {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val saveGate = CompletableDeferred<Unit>()
        var gateSaves = false
        coEvery { repo.listAll() } coAnswers {
            if (gateSaves) saveGate.await()
            listOf(kb)
        }

        val engine = mockk<GenerationEngine>(relaxed = true)
        val secondGate = CompletableDeferred<Unit>()
        val refB = ref("mem-2", "她在准备考试")
        var calls = 0
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            val index = calls++
            flow {
                emit(ReplyStarted(requestId))
                if (index == 0) {
                    emit(ReplyMemoryRefs(requestId, listOf(ref("mem-1", "旧一轮引用"))))
                    emit(ReplyCompleted(requestId, GenerateResult.Success(response("reply-old"))))
                } else {
                    secondGate.await()
                    emit(ReplyMemoryRefs(requestId, listOf(refB)))
                    emit(ReplyCompleted(requestId, GenerateResult.Success(response("reply-new"))))
                }
            }
        }

        val vm = newVm(repo, engine)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "在忙吗")
        vm.generate()
        delay(400)
        assertEquals("reply-old", replyText(vm.result.value))

        // 写盘卡住时发起新请求：owner 与 context 都换成新一轮
        gateSaves = true
        vm.nextRound()
        delay(200)
        vm.generate()
        delay(400)
        assertTrue("新请求应已进入在飞状态", vm.isGenerating.value)

        // 旧收尾此刻完成：不许把新请求清掉
        gateSaves = false
        saveGate.complete(Unit)
        delay(400)
        assertTrue("旧收尾不许清掉新请求的 owner", vm.isGenerating.value)

        // 新请求照常收口：结果与引用都属于新一轮
        secondGate.complete(Unit)
        delay(400)
        assertEquals("reply-new", replyText(vm.result.value))
        assertEquals(listOf(refB), vm.getCurrentMemoryRefs())
    }

    // ════════════════════════════════════════════════════════════════
    // 3. stopGeneration 只在真的停掉任务时才清 context
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `stop after a settled success keeps the round context usable`() = runBlocking {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            flow {
                emit(ReplyStarted(requestId))
                emit(ReplyMemoryRefs(requestId, listOf(ref("mem-1", "已落定的引用"))))
                emit(ReplyCompleted(requestId, GenerateResult.Success(response("reply-1"))))
            }
        }
        val recorder = mockk<TopicRecorder>(relaxed = true)
        coEvery {
            recorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns false

        val vm = newVm(repo, engine, recorder)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "周五见？")
        vm.generate()
        delay(400)
        assertEquals(1, vm.getCurrentMemoryRefs().size)

        // 已经落定：这次"停止"什么都没停，不许顺手把这一轮的 context 清掉
        vm.stopGeneration()
        delay(100)
        assertTrue("结果还在", vm.result.value is GenerateResult.Success)
        assertEquals("context 不许被清空", 1, vm.getCurrentMemoryRefs().size)

        // 下一轮仍然能保存（清掉 context 时这里会静默 return）
        vm.nextRound()
        delay(400)
        coVerify {
            recorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `stop of an in flight request still clears the round`() = runBlocking {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            flow {
                emit(ReplyStarted(requestId))
                gate.await()
            }
        }
        val vm = newVm(repo, engine)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "在吗")
        vm.generate()
        delay(200)
        assertTrue(vm.isGenerating.value)

        vm.stopGeneration()
        delay(150)
        assertFalse("真的停掉一个在飞请求时照旧落 Idle", vm.isGenerating.value)
    }

    // ════════════════════════════════════════════════════════════════
    // 4. 成功收口不无条件复位"输入已变化"
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `input edited during generation stays marked as changed after success`() = runBlocking {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            flow {
                emit(ReplyStarted(requestId))
                gate.await()
                emit(ReplyCompleted(requestId, GenerateResult.Success(response("reply-1"))))
            }
        }
        val vm = newVm(repo, engine)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(200)

        // 生成期间改了输入：这一条结果已经不代表当前输入
        vm.updateMessage(0, ChatMessage.Role.HER, "hello changed")
        gate.complete(Unit)
        delay(300)

        assertNotNull(vm.result.value)
        assertTrue("生成期间改过输入，成功收口不许把 stale 擦掉", vm.inputChanged.value)
    }

    @Test
    fun `unchanged input after success clears the stale flag`() = runBlocking {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            flow {
                emit(ReplyStarted(requestId))
                emit(ReplyCompleted(requestId, GenerateResult.Success(response("reply-1"))))
            }
        }
        val vm = newVm(repo, engine)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)

        assertFalse("输入没变：这一次成功对应的就是屏上这条结果", vm.inputChanged.value)
    }

    // ════════════════════════════════════════════════════════════════
    // 5. 失败轮不是回退目标（生产接线）
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `a failed round blocks rollback and v2 survives until the next success`() = runBlocking {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        var calls = 0
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            val index = calls++
            flow {
                emit(ReplyStarted(requestId))
                when (index) {
                    0 -> emit(ReplyCompleted(requestId, GenerateResult.Success(response("v1"))))
                    1 -> emit(ReplyCompleted(requestId, GenerateResult.Success(response("v2"))))
                    2 -> emit(ReplyCompleted(requestId, GenerateResult.Error("provider down")))
                    else -> emit(ReplyCompleted(requestId, GenerateResult.Success(response("v4"))))
                }
            }
        }
        val vm = newVm(repo, engine)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)
        vm.generate()
        delay(300)
        assertEquals("v2", replyText(vm.result.value))
        assertEquals(2, vm.generationHistorySize)

        // v3 失败：没有快照，此刻回退没有"当前版本"可丢
        vm.generate()
        delay(300)
        assertTrue(vm.result.value is GenerateResult.Error)
        assertEquals("失败轮不多记一条", 2, vm.generationHistorySize)
        assertFalse("失败轮不许成为回退目标", vm.canRollbackGeneration)
        vm.rollbackToPreviousGeneration()
        delay(100)
        assertEquals("拒绝回退时栈一字未动", 2, vm.generationHistorySize)

        // 再成功一轮：回退丢新那条、恢复的仍是 v2
        vm.generate()
        delay(300)
        assertEquals("v4", replyText(vm.result.value))
        assertEquals(3, vm.generationHistorySize)
        assertTrue(vm.canRollbackGeneration)
        vm.rollbackToPreviousGeneration()
        delay(150)
        assertEquals("回退必须恢复到 v2（失败轮不许把 v2 顶掉）", "v2", replyText(vm.result.value))
        assertEquals(2, vm.generationHistorySize)
    }

    // ════════════════════════════════════════════════════════════════
    // 6. 协调器拒绝要有可见反馈
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `a request rejected by the coordinator surfaces a panel warning`() = runBlocking<Unit> {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            flow {
                emit(ReplyStarted(requestId))
                gate.await()
            }
        }
        val vm = newVm(repo, engine)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "在吗")
        vm.generate()
        delay(200)
        assertTrue(vm.isGenerating.value)

        // 第二次点击被协调器拒掉：以前只有一行日志，屏上一个字都没有
        vm.generate()
        delay(100)
        assertEquals(FOREGROUND_SLOT_BUSY_HINT, vm.panelWarning.value)
        gate.complete(Unit)
    }

    /**
     * 反方向的那一格：生成占着槽时点"谈心"，同样要有一句话。
     *
     * 两侧都走同一条 `lease == null` 分支，但用户看到的是两个不同入口。
     */
    @Test
    fun `counseling rejected while a reply is running surfaces a panel warning`() = runBlocking<Unit> {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            flow {
                emit(ReplyStarted(requestId))
                gate.await()
            }
        }
        val vm = newVm(repo, engine)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "在吗")
        vm.generate()
        delay(200)
        assertTrue(vm.isGenerating.value)

        vm.generateCounseling("我有点累")
        delay(100)
        assertEquals(FOREGROUND_SLOT_BUSY_HINT, vm.panelWarning.value)
        assertFalse("被拒的谈心不许留下空壳状态", vm.isCounseling.value)
        gate.complete(Unit)
    }

    /**
     * 改写被拒的那一格：用户是在**有结果**的卡片上点"改写"，此时占槽的是谈心/主动发。
     *
     * 这条路径比 generate 更容易被当成死键——结果在屏上、卡片在屏上，点了转都不转。
     */
    @Test
    fun `a rewrite rejected by the coordinator surfaces a panel warning`() = runBlocking<Unit> {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            flow {
                emit(ReplyStarted(requestId))
                emit(ReplyCompleted(requestId, GenerateResult.Success(response("reply-1"))))
            }
        }
        val counselingGate = CompletableDeferred<Unit>()
        every { engine.counselingStream(any(), any(), any()) } returns flow {
            counselingGate.await()
        }

        val vm = newVm(repo, engine)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "在吗")
        vm.generate()
        delay(300)
        assertTrue(vm.result.value is GenerateResult.Success)

        // 谈心占住前台槽：改写请求过不了协调器那一关
        vm.generateCounseling("我有点累")
        delay(150)
        assertTrue("谈心应先占住前台槽", vm.isCounseling.value)
        assertTrue(
            "占槽提示还没出现过（下面这句必须是这次改写带出来的）",
            vm.panelWarning.value != FOREGROUND_SLOT_BUSY_HINT
        )
        vm.rewriteScheme(SchemeSource.STYLE, "A", "换个说法")
        delay(100)
        assertEquals(FOREGROUND_SLOT_BUSY_HINT, vm.panelWarning.value)
        counselingGate.complete(Unit)
    }

    // ════════════════════════════════════════════════════════════════
    // 7. 后台三引擎收集单飞
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `a second topic rotation does not start a concurrent trigger run`() = runBlocking<Unit> {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        stubRepo(repo)
        val engine = mockk<GenerationEngine>(relaxed = true)
        every { engine.replyStream(any()) } answers {
            val requestId = arg<GenerationInput>(0).requestId
            flow {
                emit(ReplyStarted(requestId))
                emit(ReplyCompleted(requestId, GenerateResult.Success(response("reply"))))
            }
        }
        val recorder = mockk<TopicRecorder>(relaxed = true)
        // 每一轮都"话题轮换"：连续两轮会各开一条后台收集
        coEvery {
            recorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns true
        val triggerGate = CompletableDeferred<Unit>()
        val coordinator = mockk<KnowledgeTriggerCoordinator>(relaxed = true)
        var triggerRuns = 0
        every { coordinator.triggerEvents(any()) } answers {
            triggerRuns++
            kotlinx.coroutines.flow.flow<com.lovebrain.app.domain.KnowledgeTriggerEvent> {
                triggerGate.await()
            }
        }

        val vm = newVm(repo, engine, recorder, coordinator)
        vm.refreshKnowledgeBases()
        delay(150)
        vm.addMessage(ChatMessage.Role.HER, "第一条")
        vm.generate()
        delay(300)
        vm.nextRound()
        delay(400)
        assertEquals("第一轮应开一条后台收集", 1, triggerRuns)

        // 第一轮的后台引擎还挂着：再记一轮不许并发再起一条（会重复计费、重复写经验）
        vm.addMessage(ChatMessage.Role.HER, "第二条")
        vm.generate()
        delay(300)
        vm.nextRound()
        delay(400)
        assertEquals("在飞时第二条收集必须被跳过", 1, triggerRuns)

        triggerGate.complete(Unit)
    }

    // ════════════════════════════════════════════════════════════════
    // 8. refreshKnowledgeBases 的慢读不许把旧激活库写回
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `a slow stale refresh does not write the old active kb back`() = runBlocking {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        val kbB = KnowledgeBase(name = "kb-b", stage = "初识期")
        stubRepo(repo)
        val slowGate = CompletableDeferred<Unit>()
        var calls = 0
        coEvery { repo.getActive() } coAnswers {
            when (calls++) {
                // 0：init 那次自动刷新（见下）注定落空，但 getActive 的调用要归它；
                // 1：显式基线刷新，读回 kb-a。
                0, 1 -> kb
                2 -> {
                    slowGate.await()
                    kb
                }
                else -> kbB
            }
        }
        coEvery { repo.listAll() } returns listOf(kb, kbB)

        val vm = newVm(repo, mockk(relaxed = true))
        // 基线激活库不能靠 init 那次自动刷新，也不能靠泵 TestDispatcher 救回来——
        // 实测根因（临时探针逐行取证）：UnconfinedTestDispatcher 会把 init 里那颗
        // `viewModelScope.launch { …; refreshKnowledgeBases() }` 在**构造器中途就地执行**
        // （mock 仓库没有真实挂起点，一路内联跑到底），此时 `intents` 还停在
        // 声明位置（line ~2190）没初始化——首读走"切库"分支，
        // 在 `intents.resetForKbSwitch()` 上抛 NPE，被 refreshKnowledgeBases 的
        // catch 吞掉，activeKb 永远停在 null。协程已经死了，runCurrent/advanceUntilIdle
        // 泵不出一个死掉的续体，所以这里按本文件其余各格的同一约定**显式发起基线刷新**。
        // 生产不受影响：真调度器/真 Room 挂起点会把这次刷新推迟到构造器跑完之后。
        // 慢读与最新读的竞速、以及"慢的旧读醒来必须被 seq 守卫拦下"这一判据原样保留。
        vm.refreshKnowledgeBases()
        assertEquals("kb-a", vm.activeKb.value?.name)

        vm.refreshKnowledgeBases()      // 慢的旧读（卡在 getActive）
        delay(100)
        vm.refreshKnowledgeBases()      // 最新一次：切到 kb-b
        delay(200)
        assertEquals("kb-b", vm.activeKb.value?.name)

        slowGate.complete(Unit)
        delay(200)
        assertEquals("更晚发起的那次已接管，旧读不许写回", "kb-b", vm.activeKb.value?.name)
    }
}
