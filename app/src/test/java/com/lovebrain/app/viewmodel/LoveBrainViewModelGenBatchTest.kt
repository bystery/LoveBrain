package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.toChatMessages
import com.lovebrain.app.model.IntentConfig
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
// advanceUntilIdle only works inside runTest; using delay() in runBlocking tests
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
 * 第六批修改定向测试（GEN-01 ~ GEN-04）。
 *
 * 纯 JVM 构造沿用 LoveBrainViewModelR5RegressionTest 先例：7 依赖全 mock + setMain 接管。
 * 使用 UnconfinedTestDispatcher 让所有协程立即执行，避免 Dispatchers.IO 在测试中不被推进的问题。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoveBrainViewModelGenBatchTest {

    private lateinit var prefs: SecurePrefs
    private lateinit var topicRecorder: TopicRecorder
    private lateinit var generationEngine: com.lovebrain.app.domain.GenerationEngine
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

    /** 构造 LoveBrainViewModel：init 读取面显式桩（同 R5RegressionTest 先例） */
    private fun newViewModel(): LoveBrainViewModel {
        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        topicRecorder = mockk(relaxed = true)
        generationEngine = mockk(relaxed = true)
        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns null
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = topicRecorder,
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = generationEngine,
operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        )
    }

    /**
     * 构造带 KB 的 ViewModel。
     * @param kbName 知识库名称
     * @param recorder 自定义 TopicRecorder（默认 relaxed mock）
     */
    private fun newViewModelWithKb(
        kbName: String = "kb-a",
        recorder: TopicRecorder = mockk<TopicRecorder>(relaxed = true).also { r ->
            coEvery { r.record(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns false
        },
        knowledgeRepoOverride: com.lovebrain.app.data.KnowledgeRepository? = null
    ): LoveBrainViewModel {
        val knowledgeRepo = knowledgeRepoOverride ?: mockk(relaxed = true)
        if (knowledgeRepoOverride == null) {
            coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = kbName, stage = "暧昧期")
            coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
            coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
            coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = kbName, stage = "暧昧期"))
            // 显式 stub 泛型返回方法，防 relaxed mock 返回 null 导致 NPE
            coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
            coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
            coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
            coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        }

        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        topicRecorder = recorder
        generationEngine = mockk(relaxed = true)
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = recorder,
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = generationEngine,
operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        )
    }

    /** 模拟 Engine 成功生成回复 */
    private fun stubEngineGenerateSuccess(
        engine: com.lovebrain.app.domain.GenerationEngine,
        response: LoveBrainResponse = LoveBrainResponse(
            response = ReplySchemes(recommended = "reply"),
            analysis = ReplyAnalysis(topic_status = "same", topic_label = "test")
        )
    ) {
        every { engine.generateReply(any(), any(), any()) } answers {
            val scope = arg<CoroutineScope>(1)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                callbacks.onReplyResult(GenerateResult.Success(response))
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyPanelState(com.lovebrain.app.model.PanelState.AI_RESULT)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Test 1: 重复 generate 不覆盖正在运行 Job
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t1_duplicate_generate_does_not_replace_active_job() = runBlocking {
        val vm = newViewModel()
        vm.messages.value

        var engineCallCount = 0
        val genGate = CompletableDeferred<Unit>()
        every { generationEngine.generateReply(any(), any(), any()) } answers {
            engineCallCount++
            val scope = arg<CoroutineScope>(1)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                try { genGate.await() } catch (_: Exception) {}
            }
        }

        vm.generate()
        delay(200)
        assertEquals("Engine 应被调用 1 次", 1, engineCallCount)
        assertTrue("isGenerating 应为 true", vm.isGenerating.value)

        // 第二次调用 — 应被 ViewModel guard 拒绝
        vm.generate()
        delay(100)
        assertEquals("Engine 不应被第二次调用", 1, engineCallCount)

        // 停止生成
        vm.stopGeneration()
        genGate.complete(Unit)
        delay(200)
        assertFalse("isGenerating 应为 false", vm.isGenerating.value)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 2: 谈心/锦囊/主动发保持相同 Job ownership
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t2_counseling_duplicate_does_not_replace_active_job() = runBlocking {
        val vm = newViewModel()
        delay(100)

        var callCount = 0
        every { generationEngine.generateCounseling(any(), any(), any(), any()) } answers {
            callCount++
            val scope = arg<CoroutineScope>(2)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(3)
            scope.launch {
                callbacks.onCounselingStart()
                try { delay(999_999) } catch (_: Exception) {}
            }
        }

        vm.generateCounseling("我好累")
        delay(100)
        assertEquals(1, callCount)
        assertTrue("isCounseling 应为 true", vm.isCounseling.value)

        vm.generateCounseling("再试一次")
        delay(100)
        assertEquals("不应重复调用", 1, callCount)

        vm.stopCounseling()
        delay(100)
        assertFalse("isCounseling 应为 false", vm.isCounseling.value)
    }

    @Test
    fun t2_suggest_duplicate_does_not_replace_active_job() = runBlocking {
        val vm = newViewModel()
        delay(100)

        var callCount = 0
        every { generationEngine.generateSuggest(any(), any()) } answers {
            callCount++
            val scope = arg<CoroutineScope>(0)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(1)
            scope.launch {
                callbacks.onSuggestStart()
                try { delay(999_999) } catch (_: Exception) {}
            }
        }

        vm.generateSuggest()
        delay(100)
        assertEquals(1, callCount)
        assertTrue("isSuggesting 应为 true", vm.isSuggesting.value)

        vm.generateSuggest()
        delay(100)
        assertEquals("不应重复调用", 1, callCount)

        vm.stopSuggest()
        delay(100)
        assertFalse("isSuggesting 应为 false", vm.isSuggesting.value)
    }

    @Test
    fun t2_proactive_duplicate_does_not_replace_active_job() = runBlocking {
        val vm = newViewModel()
        vm.messages.value

        var callCount = 0
        val gate = CompletableDeferred<Unit>()
        every { generationEngine.generateProactive(any(), any(), any(), any()) } answers {
            callCount++
            val scope = arg<CoroutineScope>(2)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(3)
            scope.launch {
                callbacks.onProactiveStart()
                try { gate.await() } catch (_: Exception) {}
            }
        }

        vm.generateProactive("草稿", "场景")
        delay(200)
        assertEquals(1, callCount)
        assertTrue("isProactive 应为 true", vm.isProactive.value)

        vm.generateProactive("再试", "")
        delay(100)
        assertEquals("不应重复调用", 1, callCount)

        vm.stopProactive()
        gate.complete(Unit)
        delay(200)
        assertFalse("isProactive 应为 false", vm.isProactive.value)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 3: 生成 snapshot 不受后来新增消息影响
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t3_snapshot_not_affected_by_messages_added_during_generation() = runBlocking {
        val vm = newViewModel()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "A")
        vm.addMessage(ChatMessage.Role.ME, "B")

        val capturedMessages = slot<com.lovebrain.app.model.GenerationInput>()
        val gate = CompletableDeferred<Unit>()
every { generationEngine.generateReply(capture(capturedMessages), any(), any()) } answers {
val input = capturedMessages.captured
val scope = arg<CoroutineScope>(1)
val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                try { gate.await() } catch (_: Exception) {}
            }
        }

        vm.generate()
        delay(100)

        // 生成期间新增 C
        vm.addMessage(ChatMessage.Role.HER, "C")

        assertEquals(
            "Engine 收到的快照应只有 A+B",
            listOf("A", "B"),
            capturedMessages.captured.toChatMessages().map { it.content }
        )
        assertEquals(
            "实时消息列表应有 A+B+C",
            listOf("A", "B", "C"),
            vm.messages.value.map { it.content }
        )

        vm.stopGeneration()
        gate.complete(Unit)
        delay(100)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 4: 保存成功只消费 snapshot IDs
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t4_commit_success_only_consumes_snapshot_ids() = runBlocking {
        val vm = newViewModelWithKb()
        vm.refreshKnowledgeBases()
        // 等待 init 完成
        vm.messages.value
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "A")
        vm.addMessage(ChatMessage.Role.ME, "B")

        stubEngineGenerateSuccess(generationEngine)

        vm.generate()
        // 等待 generate 完成
        kotlinx.coroutines.delay(200)

        // 生成期间新增 C
        vm.addMessage(ChatMessage.Role.HER, "C")

        assertTrue("result 应为 Success", vm.result.value is GenerateResult.Success)

        vm.nextRound()
        // 等待 nextRound 异步写盘完成
        kotlinx.coroutines.delay(500)

        assertEquals(
            "保存成功后应只保留 C",
            listOf("C"),
            vm.messages.value.map { it.content }
        )
    }

    // ════════════════════════════════════════════════════════════════
    // Test 5: 生成期间修改 A 不改变写盘 snapshot
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t5_edit_during_generation_does_not_change_snapshot() = runBlocking {
        val vm = newViewModel()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "A")

        val capturedMessages = slot<com.lovebrain.app.model.GenerationInput>()
        val gate = CompletableDeferred<Unit>()
every { generationEngine.generateReply(capture(capturedMessages), any(), any()) } answers {
val input = capturedMessages.captured
val scope = arg<CoroutineScope>(1)
val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                try { gate.await() } catch (_: Exception) {}
            }
        }

        vm.generate()
        delay(100)

        // 生成期间编辑 A → A'
        vm.updateMessage(0, ChatMessage.Role.HER, "A'")

        assertEquals(
            "Engine 收到的快照应仍是 A（原始内容）",
            listOf("A"),
            capturedMessages.captured.toChatMessages().map { it.content }
        )

        vm.stopGeneration()
        gate.complete(Unit)
        delay(100)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 6: 生成期间切换 KB 不串库
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t6_kb_switch_during_generation_does_not_cross_save() = runBlocking {
        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-a", stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb-a", stage = "暧昧期"))
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0

        val vm = newViewModelWithKb(
            kbName = "kb-a",
            knowledgeRepoOverride = knowledgeRepo
        )
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("kb-a", vm.activeKb.value?.name)

        vm.addMessage(ChatMessage.Role.HER, "msg-A")

        stubEngineGenerateSuccess(generationEngine)

        vm.generate()
        delay(200)

        // 生成后切换 KB（模拟用户切换）
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-b", stage = "热恋期")
        coEvery { knowledgeRepo.listAll() } returns listOf(
            KnowledgeBase(name = "kb-a", stage = "暧昧期"),
            KnowledgeBase(name = "kb-b", stage = "热恋期")
        )

        vm.nextRound()
        delay(500)

        val kbSlot = slot<KnowledgeBase>()
        coVerify { topicRecorder.record(capture(kbSlot), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(
            "record 应使用生成时的 KB (kb-a)，不是切换后的 kb-b",
            "kb-a",
            kbSlot.captured.name
        )
    }

    // ════════════════════════════════════════════════════════════════
    // Test 7: record 失败时不清 UI
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t7_record_failure_preserves_ui_state() = runBlocking {
        val failRecorder = mockk<TopicRecorder>()
        coEvery { failRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers { throw RuntimeException("disk full") }

        val vm = newViewModelWithKb(recorder = failRecorder)
        vm.refreshKnowledgeBases()
        delay(100)
        vm.messages.value

        vm.addMessage(ChatMessage.Role.HER, "A")
        vm.addMessage(ChatMessage.Role.ME, "B")

        stubEngineGenerateSuccess(generationEngine)

        vm.generate()
        kotlinx.coroutines.delay(200)

        vm.setFeedback("A", SchemeFeedback.LIKED)

        vm.nextRound()
        kotlinx.coroutines.delay(500)

        assertEquals(
            "record 失败后消息应保留",
            listOf("A", "B"),
            vm.messages.value.map { it.content }
        )
        assertTrue("result 应仍为 Success", vm.result.value is GenerateResult.Success)
        assertEquals("feedbacks 应保留", SchemeFeedback.LIKED, vm.feedbacks.value["A"])
        assertNotNull("应出现保存失败警告", vm.panelWarning.value)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 8: record 成功后才清本轮（异步期间数据仍在）
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t8_record_success_commits_ui_only_after_completion() = runBlocking {
        val recordDeferred = CompletableDeferred<Boolean>()
        val slowRecorder = mockk<TopicRecorder>()
        coEvery { slowRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            recordDeferred.await()
        }

        val vm = newViewModelWithKb(recorder = slowRecorder)
        vm.refreshKnowledgeBases()
        delay(100)
        vm.messages.value

        vm.addMessage(ChatMessage.Role.HER, "A")

        stubEngineGenerateSuccess(generationEngine)

        vm.generate()
        kotlinx.coroutines.delay(200)

        // 执行 nextRound — record 会挂起
        vm.nextRound()
        kotlinx.coroutines.delay(200)

        // record 挂起期间：消息和结果应仍在
        assertEquals(
            "record 挂起期间消息应保留",
            listOf("A"),
            vm.messages.value.map { it.content }
        )
        assertTrue("record 挂起期间 result 应仍在", vm.result.value is GenerateResult.Success)

        // 完成 record
        recordDeferred.complete(false)
        kotlinx.coroutines.delay(500)

        assertEquals(
            "record 成功后消息应被消费",
            emptyList<String>(),
            vm.messages.value.map { it.content }
        )
        assertNull("record 成功后 result 应清空", vm.result.value)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 9: 失败 attempt 不污染 retry — streamingSchemes reset
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t9_failed_attempt_does_not_pollute_retry_schemes() = runBlocking {
        val vm = newViewModel()
        delay(100)

        every { generationEngine.generateReply(any(), any(), any()) } answers {
            val scope = arg<CoroutineScope>(1)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                callbacks.onReplyStreamingSchemes(listOf(Scheme(tag = "A", title = "方案A-第一次", reply = "r1")))
                callbacks.onReplyStreamingSchemesReset()
                callbacks.onReplyStreamingSchemes(listOf(Scheme(tag = "C", title = "方案C-第二次", reply = "r2")))
                callbacks.onReplyResult(GenerateResult.Success(
                    LoveBrainResponse(response = ReplySchemes(recommended = "r2"))
                ))
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyPanelState(com.lovebrain.app.model.PanelState.AI_RESULT)
            }
        }

        vm.generate()
        delay(200)

        assertEquals(
            "最终流式方案应只有第二次的 C",
            1,
            vm.streamingSchemes.value.size
        )
        assertEquals("C", vm.streamingSchemes.value[0].tag)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 10: 手动停止后 context 被清，但消息不被清
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t10_stop_clears_context_but_not_messages() = runBlocking {
        val vm = newViewModel()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "A")
        vm.addMessage(ChatMessage.Role.ME, "B")

        val gate = CompletableDeferred<Unit>()
        every { generationEngine.generateReply(any(), any(), any()) } answers {
            val scope = arg<CoroutineScope>(1)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                try { gate.await() } catch (_: Exception) {}
            }
        }

        vm.generate()
        delay(100)
        assertTrue("isGenerating 应为 true", vm.isGenerating.value)

        vm.stopGeneration()
        gate.complete(Unit)
        delay(100)

        assertEquals(
            "停止后消息应保留",
            listOf("A", "B"),
            vm.messages.value.map { it.content }
        )
        assertFalse("isGenerating 应为 false", vm.isGenerating.value)
        assertTrue("result 应为 Error", vm.result.value is GenerateResult.Error)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 11: Engine reject 时 context 不保存、旧 Job 不覆盖
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t11_engine_reject_does_not_save_context_or_replace_job() = runBlocking {
        val vm = newViewModel()
        vm.messages.value

        var firstJob: Job? = null
        val gate = CompletableDeferred<Unit>()
        every { generationEngine.generateReply(any(), any(), any()) } answers {
            val scope = arg<CoroutineScope>(1)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                try { gate.await() } catch (_: Exception) {}
            }.also { firstJob = it }
        }

        vm.addMessage(ChatMessage.Role.HER, "msg")
        vm.generate()
        delay(200)
        assertNotNull("第一次应有 Job", firstJob)
        assertTrue("isGenerating 应为 true", vm.isGenerating.value)

        // 第二次调用：Engine 返回 null（reject）
        every { generationEngine.generateReply(any(), any(), any()) } returns null
        vm.generate()
        delay(100)

        assertTrue("旧 Job 应仍活跃", firstJob?.isActive == true)
        assertTrue("isGenerating 应仍为 true", vm.isGenerating.value)

        vm.stopGeneration()
        gate.complete(Unit)
        delay(200)
        assertFalse("isGenerating 应为 false", vm.isGenerating.value)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 12: 无 KB 时 nextRound 保持"未记入"产品语义
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t12_no_kb_nextRound_shows_warning_and_consumes_messages() = runBlocking {
        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns null
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0

        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        topicRecorder = mockk(relaxed = true)
        generationEngine = mockk(relaxed = true)
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns ConfigValidationResult(0, 0, emptyList())

        val vm = LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = topicRecorder,
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = generationEngine,
operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        )
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "A")

        stubEngineGenerateSuccess(generationEngine)

        vm.generate()
        delay(200)

        vm.nextRound()
        delay(200)

        assertEquals("无 KB 时消息仍应被消费", emptyList<String>(), vm.messages.value.map { it.content })
        assertEquals("未激活知识库，本轮对话未记入", vm.panelWarning.value)
        coVerify(exactly = 0) { topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // ════════════════════════════════════════════════════════════════
    // Test 13: stopGeneration 清 streamingSchemes
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t13_stop_clears_streaming_schemes() = runBlocking {
        val vm = newViewModel()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "A")

        val gate = CompletableDeferred<Unit>()
        every { generationEngine.generateReply(any(), any(), any()) } answers {
            val scope = arg<CoroutineScope>(1)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                callbacks.onReplyStreamingSchemes(listOf(Scheme(tag = "A", title = "test", reply = "r")))
                try { gate.await() } catch (_: Exception) {}
            }
        }

        vm.generate()
        delay(100)

        assertEquals(1, vm.streamingSchemes.value.size)

        vm.stopGeneration()
        gate.complete(Unit)
        delay(100)

        assertEquals("停止后 streamingSchemes 应清空", emptyList<Scheme>(), vm.streamingSchemes.value)
    }

    // ════════════════════════════════════════════════════════════════
    // COUN-01 Test 1: 谈心冻结 KB — prompt 使用发起时的 KB
    // ════════════════════════════════════════════════════════════════

    @Test
    fun coun01_counseling_freezes_kb_snapshot_for_prompt() = runBlocking {
        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-a", stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb-a", stage = "暧昧期"))
        coEvery { knowledgeRepo.appendCounselingEntries(any(), any(), any()) } returns Unit

        val vm = newViewModelWithKb(kbName = "kb-a", knowledgeRepoOverride = knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("kb-a", vm.activeKb.value?.name)

        // 捕获传给 Engine 的 KnowledgeBase 参数
        val capturedKb = slot<KnowledgeBase>()
        val gate = CompletableDeferred<Unit>()
        every { generationEngine.generateCounseling(any(), capture(capturedKb), any(), any()) } answers {
            val scope = arg<CoroutineScope>(2)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(3)
            scope.launch {
                callbacks.onCounselingStart()
                try { gate.await() } catch (_: Exception) {}
            }
        }

        vm.generateCounseling("我好累")
        delay(200)

        // 谈心进行中切换 KB
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-b", stage = "热恋期")
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("kb-b", vm.activeKb.value?.name)
        assertEquals(
            "Engine 收到的 KB 应为发起时的 kb-a",
            "kb-a",
            capturedKb.captured.name
        )

        vm.stopCounseling()
        gate.complete(Unit)
        delay(100)
    }

    // ════════════════════════════════════════════════════════════════
    // COUN-01 Test 2: 谈心日志写原 KB — 不写切换后的 KB
    // ════════════════════════════════════════════════════════════════

    @Test
    fun coun01_counseling_save_log_uses_originating_kb() = runBlocking {
        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-a", stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb-a", stage = "暧昧期"))
        coEvery { knowledgeRepo.appendCounselingEntries(any(), any(), any()) } returns Unit

        val vm = newViewModelWithKb(kbName = "kb-a", knowledgeRepoOverride = knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("kb-a", vm.activeKb.value?.name)

        // Engine 成功完成谈心，回调 onCounselingSaveLog
        every { generationEngine.generateCounseling(any(), any(), any(), any()) } answers {
            val scope = arg<CoroutineScope>(2)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(3)
            val kb = arg<KnowledgeBase?>(1)
            scope.launch {
                callbacks.onCounselingStart()
                callbacks.onCounselingStreaming("回复内容")
                callbacks.onCounselingResult("回复内容")
                callbacks.onCounselingSaveLog(kb?.name, "我好累", "回复内容", "分析内容")
                callbacks.onCounselingEnd()
            }
        }

        vm.generateCounseling("我好累")
        delay(300)

        // 谈心完成后切换 KB
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-b", stage = "热恋期")
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("kb-b", vm.activeKb.value?.name)

        // 日志应写入 kb-a（发起时 KB），不写 kb-b
        coVerify { knowledgeRepo.appendCounselingEntries("kb-a", any(), any()) }
        coVerify(exactly = 0) { knowledgeRepo.appendCounselingEntries("kb-b", any(), any()) }
    }

    // ════════════════════════════════════════════════════════════════
    // KBUI-01 Test: 切 KB 清 transient vector state
    // ════════════════════════════════════════════════════════════════

    @Test
    fun kbui01_switch_kb_clears_transient_vector_state() = runBlocking {
        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        val kbA = KnowledgeBase(name = "kb-a", stage = "暧昧期")
        val kbB = KnowledgeBase(name = "kb-b", stage = "热恋期")
        coEvery { knowledgeRepo.getActive() } returns kbA
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector("kb-a") } returns mapOf("intimacy" to 5)
        coEvery { knowledgeRepo.readVector("kb-b") } returns mapOf("intimacy" to 8)

        val vm = newViewModelWithKb(kbName = "kb-a", knowledgeRepoOverride = knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        // 模拟 A 有 delta / update / notice
        vm.onVectorUpdated("kb-a", mapOf("intimacy" to 5), mapOf("intimacy" to 2))
        vm.onVectorUpdateNotice("kb-a", "五维更新")
        vm.onKbNotice("A 的通知")
        delay(100)

        assertEquals("delta 应存在", 2, vm.vectorDelta.value["intimacy"])
        assertNotNull("vectorUpdate 应存在", vm.vectorUpdate.value)
        assertNotNull("kbNotice 应存在", vm.kbNotice.value)

        // 切换到 B
        coEvery { knowledgeRepo.getActive() } returns kbB
        vm.refreshKnowledgeBases()
        delay(300)

        // 断言 transient state 被清
        assertEquals(
            "切库后 vectorDelta 应为空",
            emptyMap<String, Int>(),
            vm.vectorDelta.value
        )
        assertNull("切库后 vectorUpdate 应为 null", vm.vectorUpdate.value)
        assertNull("切库后 kbNotice 应为 null", vm.kbNotice.value)
        // 新 vector 应为 B 的
        assertEquals(8, vm.currentVector.value["intimacy"])
    }
}
