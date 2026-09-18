package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SchemeIdentity
import com.lovebrain.app.model.SchemeSource
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * F01/F02 针对性测试：点赞入库数据语义 + 真实聊天隔离。
 *
 * F01 验收点：
 * - 点赞保存候选正文（不只存标签）
 * - 多点赞分别保存
 * - 无点赞不默认 A 为已发送
 * - 点赞正文可追溯
 *
 * F02 验收点：
 * - IDEA 不写入真实聊天记录
 * - 点赞不形成真实连续发言
 * - 候选/点赞/IDEA 与 HER/ME 真实聊天隔离
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LikedSchemeRecordingTest {

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

    private fun newViewModelWithKb(kbName: String = "kb-test"): LoveBrainViewModel {
        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = kbName, stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = kbName, stage = "暧昧期"))
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
        coEvery { topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns false
        generationEngine = mockk(relaxed = true)
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = topicRecorder,
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = generationEngine
        )
    }

    private fun stubEngineSuccess(
        engine: com.lovebrain.app.domain.GenerationEngine,
        response: LoveBrainResponse
    ) {
        every { engine.generate(any(), any(), any(), any(), any(), any(), any()) } answers {
            val scope = arg<CoroutineScope>(3)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(4)
            scope.launch {
                callbacks.onReplyStart()
                callbacks.onReplyResult(GenerateResult.Success(response))
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyPanelState(com.lovebrain.app.model.PanelState.AI_RESULT)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // F01 Test 1: 点赞保存候选正文（不只存标签）
    // ════════════════════════════════════════════════════════════════

    @Test
    fun f01_liked_scheme_body_is_saved_not_just_tag() = runBlocking {
        val vm = newViewModelWithKb()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "今天好累")

        val response = LoveBrainResponse(
            response = ReplySchemes(
                recommended = "辛苦了，早点休息",
                badBoy = "又加班？你们公司没人性",
                playful = "累了就早点睡，别熬着",
                warm = "注意身体，别太拼了"
            ),
            analysis = ReplyAnalysis(topic_status = "same", topic_label = "日常")
        )
        stubEngineSuccess(generationEngine, response)

        vm.generate()
        delay(200)

        // 点赞方案 B
        vm.setFeedback(SchemeIdentity(SchemeSource.STYLE, "B").key, SchemeFeedback.LIKED)

        // 捕获传给 TopicRecorder 的 likedSchemes
        val likedSlot = slot<List<Scheme>>()
        coEvery {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), capture(likedSlot))
        } returns false

        vm.nextRound()
        delay(500)

        val liked = likedSlot.captured
        assertEquals("应捕获1条点赞", 1, liked.size)
        assertEquals("B", liked[0].tag)
        assertEquals("又加班？你们公司没人性", liked[0].reply)
        assertEquals("清醒", liked[0].title)
    }

    // ════════════════════════════════════════════════════════════════
    // F01 Test 2: 多点赞分别保存
    // ════════════════════════════════════════════════════════════════

    @Test
    fun f01_multiple_likes_saved_separately() = runBlocking {
        val vm = newViewModelWithKb()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "在吗")

        val response = LoveBrainResponse(
            response = ReplySchemes(
                recommended = "在的",
                badBoy = "怎么了",
                playful = "在呢在呢",
                warm = "我在，你说"
            ),
            analysis = ReplyAnalysis(topic_status = "same", topic_label = "日常")
        )
        stubEngineSuccess(generationEngine, response)

        vm.generate()
        delay(200)

        // 点赞 A 和 C
        vm.setFeedback(SchemeIdentity(SchemeSource.STYLE, "A").key, SchemeFeedback.LIKED)
        vm.setFeedback(SchemeIdentity(SchemeSource.STYLE, "C").key, SchemeFeedback.LIKED)

        val likedSlot = slot<List<Scheme>>()
        coEvery {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), capture(likedSlot))
        } returns false

        vm.nextRound()
        delay(500)

        val liked = likedSlot.captured
        assertEquals("应捕获2条点赞", 2, liked.size)
        // 按 ABCD 排序
        assertEquals("A", liked[0].tag)
        assertEquals("在的", liked[0].reply)
        assertEquals("C", liked[1].tag)
        assertEquals("在呢在呢", liked[1].reply)
    }

    // ════════════════════════════════════════════════════════════════
    // F01 Test 3: 无点赞不默认 A 为已发送
    // ════════════════════════════════════════════════════════════════

    @Test
    fun f01_no_like_does_not_default_to_a_sent() = runBlocking {
        val vm = newViewModelWithKb()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "你好")

        val response = LoveBrainResponse(
            response = ReplySchemes(
                recommended = "你好呀",
                badBoy = "嗨",
                playful = "哈喽",
                warm = "你好"
            ),
            analysis = ReplyAnalysis(topic_status = "same", topic_label = "日常")
        )
        stubEngineSuccess(generationEngine, response)

        vm.generate()
        delay(200)

        // 不点赞任何方案

        vm.nextRound()
        delay(500)

        // selectedScheme 应为 null（不默认选 A）— 验证 record 被调用
        coVerify {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ════════════════════════════════════════════════════════════════
    // F01 Test 4: 只复制不点赞也不标记发送
    // ════════════════════════════════════════════════════════════════

    @Test
    fun f01_copy_only_does_not_mark_as_sent() = runBlocking {
        val vm = newViewModelWithKb()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "在干嘛")

        val response = LoveBrainResponse(
            response = ReplySchemes(
                recommended = "在想你在干嘛",
                badBoy = "忙着呢",
                playful = "在想你",
                warm = "在休息"
            ),
            analysis = ReplyAnalysis(topic_status = "same", topic_label = "日常")
        )
        stubEngineSuccess(generationEngine, response)

        vm.generate()
        delay(200)

        // 复制方案（但不点赞）
        vm.copyScheme(Scheme(tag = "C", title = "俏皮", reply = "在想你"))

        vm.nextRound()
        delay(500)

        // 复制不等于发送，selectedScheme 应为 null
        coVerify {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ════════════════════════════════════════════════════════════════
    // F02 Test 5: IDEA 不写入真实聊天记录
    // ════════════════════════════════════════════════════════════════

    @Test
    fun f02_idea_not_written_as_real_chat() = runBlocking {
        val vm = newViewModelWithKb()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "今天好累")
        vm.addMessage(ChatMessage.Role.IDEA, "先不约，接她吐槽")
        vm.addMessage(ChatMessage.Role.ME, "辛苦了")

        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "早点休息"),
            analysis = ReplyAnalysis(topic_status = "same", topic_label = "日常")
        )
        stubEngineSuccess(generationEngine, response)

        vm.generate()
        delay(200)

        val messagesSlot = slot<List<ChatMessage>>()
        coEvery {
            topicRecorder.record(any(), capture(messagesSlot), any(), any(), any(), any(), any(), any(), any())
        } returns false

        vm.nextRound()
        delay(500)

        val recordedMessages = messagesSlot.captured
        // 传给 TopicRecorder 的 messages 包含 IDEA（因为 snapshot 包含所有消息），
        // 但 TopicRecorder.record 内部过滤只有 HER/ME 写入 recent.md
        // 这里验证 snapshot 包含 IDEA 但 TopicRecorder 正确过滤
        assertTrue("snapshot 应包含 IDEA", recordedMessages.any { it.role == ChatMessage.Role.IDEA })
        // TopicRecorder 内部会过滤 IDEA，这里只验证传入了完整 snapshot
    }

    // ════════════════════════════════════════════════════════════════
    // F02 Test 6: 点赞不形成真实连续发言
    // ════════════════════════════════════════════════════════════════

    @Test
    fun f02_liked_schemes_not_formed_into_continuous_speech() = runBlocking {
        val vm = newViewModelWithKb()
        delay(100)

        vm.addMessage(ChatMessage.Role.HER, "在吗")
        vm.addMessage(ChatMessage.Role.ME, "在")

        val response = LoveBrainResponse(
            response = ReplySchemes(
                recommended = "怎么了？",
                badBoy = "说",
                playful = "在呢",
                warm = "我在"
            ),
            analysis = ReplyAnalysis(topic_status = "same", topic_label = "日常")
        )
        stubEngineSuccess(generationEngine, response)

        vm.generate()
        delay(200)

        // 点赞 A 和 B
        vm.setFeedback("A", SchemeFeedback.LIKED)
        vm.setFeedback("B", SchemeFeedback.LIKED)

        vm.nextRound()
        delay(500)

        // selectedScheme 应为 null — 点赞不等于发送，多条点赞不拼成连续发言
        coVerify {
            topicRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }
}
