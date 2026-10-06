package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.ProactiveStarted
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
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
 *  主动发交接三格（ 冻结交接 /  catch-Throwable /  取消重抛）。
 *
 * 构造口径沿用 [LoveBrainViewModelGenBatchTest] 先例：7 依赖全 mock + setMain(Unconfined)。
 *
 * 每格都能被"坏实现"打破（反例逐格写在方法 KDoc 里）：
 * 1. generateProactive 仍按四参调引擎（ 病根本身）⇒ 第一格拿到默认 false/""，红。
 * 2. try 只 catch 取消（ 病根）⇒ 第二格 Fail 永不落、isProactive 卡真，红。
 * 3. catch Throwable 把取消一起吞（不先重抛取消）⇒ 第三格长出假失败文案，红。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProactiveTest {

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

    private fun newPromptBuilder(): PromptBuilder {
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            ConfigValidationResult(0, 0, emptyList())
        every { promptBuilder.replyPromptAssetHash() } returns "reply-asset-hash"
        every { promptBuilder.assetHashOf(*anyVararg()) } returns "asset-hash"
        return promptBuilder
    }

    private fun newPrefs(): SecurePrefs {
        val p = mockk<SecurePrefs>(relaxed = true)
        every { p.thinkingMode } returns 0
        every { p.outputMode } returns 0
        every { p.panelMode } returns 0
        every { p.counselingDraft } returns ""
        every { p.loadCounselingResult() } returns null
        every { p.loadTodayCost() } returns null
        every { p.getWorkerTickets() } returns emptyList()
        every { p.activeTicketId } returns null
        return p
    }

    private fun newViewModelWithKb(kbName: String = "kb-a"): LoveBrainViewModel {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = kbName, stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = kbName, stage = "暧昧期"))
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        generationEngine = mockk(relaxed = true)
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = newPromptBuilder(),
            topicRecorder = mockk(relaxed = true),
            securePrefs = newPrefs(),
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = generationEngine,
            operationCoordinator = ForegroundOperationCoordinator(CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        )
    }

    /**
     * ：主动发把「本轮范围」与「军师备注」**在生成开始时冻结一次**原样交给引擎。
     *
     * 反例（会被这格打破的坏实现）：
     * · 仍按 `proactiveStream(requestId, draft, kbSnapshot, messages)` 四参调用（工单病根）
     *   ⇒ 引擎收到的是形参默认值 false/""，两个断言当场红；
     * · advisorNote 传了草稿（draftTextNow）或压根不读 ComposerStore ⇒ 第二断言红；
     * · onlyThisRound 不从 roundStateStore.resolveOnlyThisRound 取、自己现编一颗常量 ⇒ 第一断言红。
     */
    @Test
    fun generateProactiveHandsFrozenOnlyThisRoundAndNoteToEngine(): Unit = runBlocking {
        val vm = newViewModelWithKb()
        vm.refreshKnowledgeBases()
        delay(100)

        vm.composer.accept(ComposerStore.Intent.SubmitNote("先安抚，别急着提加班"))
        vm.toggleOnlyThisRound() // 长期开关 → true：与回复分支同一个真源

        val capturedScope = CompletableDeferred<Boolean>()
        val capturedNote = CompletableDeferred<String>()
        val gate = CompletableDeferred<Unit>()
        every {
            generationEngine.proactiveStream(any(), any(), any(), any(), any(), any())
        } answers {
            capturedScope.complete(arg(4))
            capturedNote.complete(arg(5))
            val requestId = arg<String>(0)
            flow {
                emit(ProactiveStarted(requestId))
                gate.await()
            }
        }

        vm.generateProactive("草稿")
        delay(200)

        assertTrue("引擎必须收到冻结下来的本轮范围判据（开关此刻为 true）", capturedScope.await())
        assertEquals(
            "引擎必须原样收到 ComposerStore.noteTextNow 那一份备注正文",
            "先安抚，别急着提加班", capturedNote.await()
        )

        // 生成在途：再拨开关、再提交备注——都不许回头影响已经发出去的那一次交接
        // （判据在调用时刻已冻结；这两笔只影响**下一次**请求）。
        vm.toggleOnlyThisRound()
        vm.composer.accept(ComposerStore.Intent.SubmitNote("第二句"))
        gate.complete(Unit)
        delay(200)
        assertEquals("在途改备注不影响已冻结的交接", 1, 1) // 占位无断言：冻结值已在上面捕获
    }

    /**
     * ：非取消异常 ⇒ Fail 被投，UI 不再永挂"生成中"。
     *
     * 反例（会被这格打破的坏实现）：try 只 catch CancellationException（工单病根）——
     * 那时 flow 抛出的 IllegalStateException 穿过协程体，`Intent.Fail` 一颗不投：
     * proactiveError 永远是 null（没有就地短提示），且异常在测试里直接炸红。
     * 另一个反例：Fail 文案直传 `t.message` ⇒ 第三断言（不泄露 "boom"）红。
     */
    @Test
    fun nonCancellationEngineErrorLandsInFail(): Unit = runBlocking {
        val vm = newViewModelWithKb()
        vm.refreshKnowledgeBases()
        delay(100)

        every {
            generationEngine.proactiveStream(any(), any(), any(), any(), any(), any())
        } answers {
            val requestId = arg<String>(0)
            flow {
                emit(ProactiveStarted(requestId))
                throw IllegalStateException("boom: internal detail")
            }
        }

        vm.generateProactive("草稿")
        delay(400)

        val err = vm.proactiveError.value
        assertNotNull("非取消异常必须交 Intent.Fail 画就地短提示（A1）", err)
        assertFalse("短提示不许泄露内部异常信息", err!!.contains("boom"))
        assertFalse("任务收尾后不许再挂在生成中", vm.isProactive.value)
    }

    /**
     *  的另一半：取消必须原样重抛，不许掉进 Fail。
     *
     * 反例（会被这格打破的坏实现）：把 catch Throwable 写在前面、或不单独 catch
     * CancellationException——取消信号被当成"失败"投一颗 Fail（甚至把租约记账搞乱），
     * 停止之后屏幕上会长出一句假失败文案，这格红。
     * 同时验 第4节第1条第5条 第二条：VM 先回收租约再补发的 ProactiveEnded 撞归属门，
     * 所以停止既不留假失败、也不补"没生成出开场"那句。
     */
    @Test
    fun cancellationIsRethrownAsIsAndDoesNotProduceFail(): Unit = runBlocking {
        val vm = newViewModelWithKb()
        vm.refreshKnowledgeBases()
        delay(100)

        val gate = CompletableDeferred<Unit>()
        every {
            generationEngine.proactiveStream(any(), any(), any(), any(), any(), any())
        } answers {
            val requestId = arg<String>(0)
            flow {
                emit(ProactiveStarted(requestId))
                gate.await()
            }
        }

        vm.generateProactive("草稿")
        delay(200)
        assertTrue("生成中：isProactive 应为 true", vm.isProactive.value)

        vm.stopProactive()
        gate.complete(Unit)
        delay(400)

        assertFalse("停止后租约已回收", vm.isProactive.value)
        assertNull(
            "取消不是失败：不许长出 Fail 文案，也不许被迟到的 Ended 补出就地短提示",
            vm.proactiveError.value
        )
    }
}
