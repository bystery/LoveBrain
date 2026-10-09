package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.model.KnowledgeBase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 悬浮窗设置页切库的**成败反馈**（席6 交回点名的口子，主线程补的守卫）。
 *
 * 写侧那一次切库在被只读保护拒掉时是**静默返回**的（`KnowledgeRepository.setActive` 这员
 * 没有失败通道），所以"调用没抛"证明不了切换落地。守卫的判据：重读回来真的换人才算成功；
 * 成功不发通知（选中态与列表高亮就是反馈），失败在面板警告位说一句可重试的话（§11.1
 * 「点击成功才更新选中态」「失败不假成功」）。
 *
 * 红条件：把 `switchActiveKb` 里的"重读核实 + 失败警告"拿掉（退回旧形状），第一格当场红——
 * 旧实现对静默拒绝一声不吭，`currentNotice` 停在 null。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KbSwitchFeedbackTest {

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

    /** 与 `IntentKbIdentityRaceTest` 同一形状的最小夹具：VM + 假仓库 */
    private fun makeVm(knowledgeRepo: KnowledgeRepository): LoveBrainViewModel {
        val prefs = mockk<com.lovebrain.app.data.SecurePrefs>(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            PromptBuilder.ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk(relaxed = true),
            operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            )
        )
    }

    /** 一块"初始在 kb-a"的仓库，mockk(relaxed) 的 setActive 什么都不做 = 静默拒绝 */
    private fun repoStuckAtA(kbA: KnowledgeBase, kbB: KnowledgeBase): KnowledgeRepository =
        mockk<KnowledgeRepository>(relaxed = true).also { repo ->
            coEvery { repo.ensureInitialKnowledgeBase() } returns Unit
            coEvery { repo.migrateIfNeeded(any()) } returns Unit
            coEvery { repo.getActive() } returns kbA
            coEvery { repo.readVector(any()) } returns emptyMap()
            coEvery { repo.readIntent(any()) } returns com.lovebrain.app.model.IntentConfig()
            coEvery { repo.listAll() } returns listOf(kbA, kbB)
            coEvery { repo.readCorrectionsAndRevision(any()) } returns
                (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
            coEvery { repo.getCorrectionsRevision(any()) } returns 0
            coEvery { repo.getLessonCount(any()) } returns 0
        }

    @Test
    fun `a switch the repo silently rejects warns instead of faking success`() = runBlocking {
        val kbA = KnowledgeBase(name = "kb-a", stage = "stage-a")
        val kbB = KnowledgeBase(name = "kb-b", stage = "stage-b")
        val vm = makeVm(repoStuckAtA(kbA, kbB))
        vm.refreshKnowledgeBases()
        delay(200)
        assertNull(vm.currentNotice.value)

        vm.switchActiveKb("kb-b")
        delay(200)

        val notice = vm.currentNotice.value
        assertNotNull("静默拒绝后一声不吭 = 把没落地的切换说成成功（旧形状在这一格红）", notice)
        assertEquals(NoticeBoard.Channel.Warning, notice!!.channel)
        assertEquals("知识库切换没有成功，请重试", notice.message)
    }

    @Test
    fun `a switch that really lands stays quiet`() = runBlocking {
        val kbA = KnowledgeBase(name = "kb-a", stage = "stage-a")
        val kbB = KnowledgeBase(name = "kb-b", stage = "stage-b")
        val repo = repoStuckAtA(kbA, kbB)
        // 这次 setActive 真把活动库写过去了：重读回来是 kb-b
        coEvery { repo.setActive("kb-b") } answers {
            coEvery { repo.getActive() } returns kbB
            Unit
        }
        val vm = makeVm(repo)
        vm.refreshKnowledgeBases()
        delay(200)

        vm.switchActiveKb("kb-b")
        delay(200)

        assertEquals("切换落地后选中态要跟过去", "kb-b", vm.activeKb.value?.name)
        val notice = vm.currentNotice.value
        assertNull(
            "成功不发通知：列表高亮就是反馈（成功借警告位是 §4.4 明令禁止的形状）",
            notice?.takeIf { it.channel == NoticeBoard.Channel.Warning }
        )
    }
}
