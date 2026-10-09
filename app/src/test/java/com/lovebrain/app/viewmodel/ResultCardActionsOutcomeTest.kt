package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 结果卡片三颗行内动作的**结果语义**（指导书 §13.2 / R28，接在
 * `DislikeCaseSaveTest`（纯函数格）与 `FeedbackCaseControllerTest`（控制器格）之后，
 * 把最后一段没有证人的 VM 接线钉住）：
 *
 * 1. 点踩成功 → 短通知走 Knowledge 回执格（不是黄色警告格），带自动消失时限；
 * 2. 点踩写盘失败 → 只出一句警告，**不出**任何成功回执；用户刚点的踩不被回滚、
 *    结果与回复原样保留（原话："不许清掉用户刚点的赞踩、不许清回复"）；
 * 3. 没有任何盘可写（仓库缺席）也**不算成功**——`persistCase` 交回 false 必须走到失败那一句；
 * 4. 点踩路径不唤醒 AI：整条链上对 Engine 的调用只有发起生成的那一次；
 * 5. 复制只交回可发送正文：不带风格名（title）、不带分析、不带任何前后缀。
 *
 * 每格的红条件写在各自测试里——这一族以前坏在两处：`persistCase` 把 IO 异常吞掉后
 * 界面照报「已记录」（假成功），以及原因表单还没删干净时点踩要先弹窗。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultCardActionsOutcomeTest {

    private lateinit var prefs: SecurePrefs
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

    /** 与 `MechanismClosureTest.makeVm` 同一形状：engine 走事件流替身，案例仓库可缺席可注入 */
    private fun makeVm(
        knowledgeRepo: KnowledgeRepository,
        generationEngine: com.lovebrain.app.domain.GenerationEngine = mockk(relaxed = true),
        caseRepo: com.lovebrain.app.data.FeedbackCaseRepository? = null
    ): LoveBrainViewModel {
        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        every { prefs.totalAdoptCount } returns 0
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            PromptBuilder.ConfigValidationResult(0, 0, emptyList())
        every { promptBuilder.replyPromptAssetHash() } returns "reply-asset-hash"
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = generationEngine,
            operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            ),
            feedbackCaseRepository = caseRepo
        )
    }

    private fun defaultRepoStubs(knowledgeRepo: KnowledgeRepository) {
        val kb = KnowledgeBase(name = "test-kb", stage = "暧昧期")
        val intent = IntentConfig()
        coEvery { knowledgeRepo.getActive() } returns kb
        coEvery { knowledgeRepo.ensureInitialKnowledgeBase() } returns Unit
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readIntent(any()) } returns intent
        coEvery { knowledgeRepo.listAll() } returns listOf(kb)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        coEvery { knowledgeRepo.readFile(any(), any()) } returns ""
        coEvery { knowledgeRepo.writeFile(any(), any(), any()) } returns Unit
    }

    /** 走到「有一条 Success 结果在架」这一步，返回第一条方案的 identity.key */
    private suspend fun generateAndGetFirstKey(vm: LoveBrainViewModel): String {
        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)
        val result = vm.result.value as? GenerateResult.Success
        assertTrue("前置：应有一条成功结果在架", result != null)
        val schemes = result!!.response.schemes
        assertTrue("前置：应有至少一张方案卡", schemes.isNotEmpty())
        return schemes.first().identity.key
    }

    /**
     * 写盘成功 = 才可以说「已记录」，而且说的是回执格（Knowledge 通道 + 自动消失），
     * 不是用户点名骂过的那种黄色警告条。
     *
     * 红条件：① 成功回执被投到 Warning 通道（或没有通知）；② 通知不带自动消失时限
     *（短通知变成了要用户点掉的常驻条）。
     */
    @Test
    fun `a dislike that reached the disk reports the short success receipt`() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)
        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)
        val caseRepo = mockk<com.lovebrain.app.data.FeedbackCaseRepository>(relaxed = true)
        val vm = makeVm(knowledgeRepo, engine, caseRepo)
        delay(200)

        val key = generateAndGetFirstKey(vm)
        vm.setFeedback(key, SchemeFeedback.DISLIKED)
        delay(200)

        val notice = vm.currentNotice.value
        assertNotNull("点踩成功后通知位上要有一句回执", notice)
        assertEquals(
            "成功回执走 Knowledge 格，落 Warning 格就是把『成功了』画成警告（用户原话点名骂过的那种）",
            NoticeBoard.Channel.Knowledge, notice!!.channel
        )
        assertEquals("已记录", notice.message)
        assertNotNull(
            "短通知必须带自动消失时限，要用户点掉的就不是短通知",
            notice.autoDismissMillis
        )
        assertNull(
            "失败那一句不该同时出现",
            vm.panelWarning.value
        )
    }

    /**
     * 写盘失败 = 只出一句「记入失败，请重试」的警告；成功回执一个字都不许出现；
     * 用户刚点的那一下踩**留在屏上**（不回滚），结果与回复原样保留。
     *
     * 红条件：① `persist` 抛了异常仍然报「已记录」（旧病：launch 完不看落盘结果）；
     * ② 失败后把 DISLIKED 回滚成 NONE；③ 失败顺手清了结果。
     */
    @Test
    fun `a dislike whose write fails warns once and keeps the vote`() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)
        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)
        val caseRepo = mockk<com.lovebrain.app.data.FeedbackCaseRepository>(relaxed = true)
        coEvery { caseRepo.save(any()) } throws java.io.IOException("disk full")
        val vm = makeVm(knowledgeRepo, engine, caseRepo)
        delay(200)

        val key = generateAndGetFirstKey(vm)
        vm.setFeedback(key, SchemeFeedback.DISLIKED)
        delay(200)

        assertEquals(
            "写坏了只能说失败——这一句要从真实的落盘结果来，不是界面猜的",
            "记入失败，请重试", vm.panelWarning.value
        )
        assertNull(
            "失败时不许报成功：Knowledge 通道必须还是空的",
            vm.kbNotice.value
        )
        assertEquals(
            "写盘失败不许把用户刚点的踩收走",
            SchemeFeedback.DISLIKED, vm.feedbacks.value[key]
        )
        assertTrue(
            "失败也不许清掉结果与回复",
            vm.result.value is GenerateResult.Success
        )
    }

    /**
     * 仓库缺席 = 没有任何一格盘可写：`persistCase` 交回 false，`setFeedback` 的接线必须把
     * 这个 false 变成失败，而不是把「没写」当成「写完了」。
     *
     * 红条件：接线不再读 `persistCase` 的返回值（比如 `if (!persistCase(case)) throw` 被删掉）
     * ——那时这一格落到成功回执上，当场红。
     */
    @Test
    fun `a dislike with nowhere to write is not reported as success`() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)
        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)
        val vm = makeVm(knowledgeRepo, engine, caseRepo = null)
        delay(200)

        val key = generateAndGetFirstKey(vm)
        vm.setFeedback(key, SchemeFeedback.DISLIKED)
        delay(200)

        assertEquals(
            "没有仓库就没有写盘成功这件事",
            "记入失败，请重试", vm.panelWarning.value
        )
        assertNull("同上：不许假报成功", vm.kbNotice.value)
    }

    /**
     * 点踩整条链上不唤醒 AI（R28 明文）：Engine 在这个场景里只被叫过一次——发起生成那次。
     *
     * 红条件：任何把「点踩」接到 AI 上的改法（点踩后追加一次分析/重生成/补齐请求）
     * 都会让 `replyStream` 的调用次数超过 1，或者点亮另外两个流入口，当场红。
     */
    @Test
    fun `disliking never wakes the ai`() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)
        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)
        val caseRepo = mockk<com.lovebrain.app.data.FeedbackCaseRepository>(relaxed = true)
        val vm = makeVm(knowledgeRepo, engine, caseRepo)
        delay(200)

        val key = generateAndGetFirstKey(vm)
        vm.setFeedback(key, SchemeFeedback.DISLIKED)
        delay(200)

        coVerify(exactly = 1) { engine.replyStream(any()) }
        coVerify(exactly = 0) { engine.counselingStream(any(), any(), any()) }
        coVerify(exactly = 0) { engine.proactiveStream(any(), any(), any(), any(), any(), any()) }
    }

    /**
     * 复制只交回可发送正文（§13.2 明文「不带分析、风格名和理由」）。
     *
     * 红条件：任何在 `copyScheme` 里拼前缀/后缀的改法——带上风格名（title）、
     * 带上分析、带上点赞理由——返回值不再是 `scheme.reply` 原文，当场红。
     */
    @Test
    fun `copy hands out exactly the sendable body`() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)
        val vm = makeVm(knowledgeRepo)
        delay(200)

        val scheme = Scheme(tag = "C", title = "俏皮", reply = "在想你，别多想")
        val copied = vm.copyScheme(scheme)
        assertEquals(
            "复制的内容必须是正文原文，一个字不添",
            "在想你，别多想", copied
        )
        assertFalse(
            "风格名不许混进剪贴板",
            copied.contains(scheme.title)
        )
        assertFalse(
            "标签字母不许混进剪贴板",
            copied.contains(scheme.tag)
        )
    }
}
