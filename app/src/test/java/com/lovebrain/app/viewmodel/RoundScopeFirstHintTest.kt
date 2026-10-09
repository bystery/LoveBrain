package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.MemoryCorrection
import io.mockk.coEvery
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「仅看本轮」首次轻提示（§2.2 第 3 条"仅看本轮开启时有明显选中态＋中文语义描述与
 * 一次轻提示"）——钉在 VM 这条真实切换路径上（`toggleOnlyThisRound` →
 * `maybeShowRoundScopeFirstHint`）。判据与先例（`intent_intro_seen` 落设置存储：
 * 每次回来不再重弹）同款，每格的红条件写在断言消息里：
 *
 * · 只在 OFF→ON 那一跳投——**取消/关闭也弹 = 红**（弹是开启的反馈，不是关闭的）；
 * · 投一次就落旗标——**旗标不落盘（写丢弃）→ 第二次开启又弹 = 红**；
 * · 落盘的旗标构造时要读回来——**落了盘但构造不读 → 重启后第一次开启又弹 = 红**；
 * · 通道走 Knowledge（回执形态、到点自散）——**投进 Warning 通道 = 屏上画成大黄警告
 *   （§4.4"普通成功不许画成大黄警告"）= 红**；
 * · 没接落盘口子时的回落（读默认、写丢弃）：**同一 VM 会话内重复弹 = 红**。
 *
 * 开关本身的状态机语义归 `RoundStateStore` 那一族测试；通知位的排队/刷新归 `NoticeBoardTest`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoundScopeFirstHintTest {

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

    // ═══════════ 装配（与 PanelWiringViewModelHooksTest 同一副骨架，只按本格需要收窄） ═══════════

    private fun makeVm(
        readRoundScopeHintShown: (() -> Boolean)? = null,
        writeRoundScopeHintShown: ((Boolean) -> Unit)? = null
    ): LoveBrainViewModel {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.totalAdoptCount } returns 0

        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kb = KnowledgeBase(name = "test-kb", displayName = "她", stage = "暧昧期", active = true)
        coEvery { knowledgeRepo.getActive() } returns kb
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.listAll() } returns listOf(kb)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0

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
            generationEngine = mockk(relaxed = true),
            operationCoordinator = ForegroundOperationCoordinator(
                CoroutineScope(SupervisorJob())
            ),
            readRoundScopeHintShown = readRoundScopeHintShown,
            writeRoundScopeHintShown = writeRoundScopeHintShown
        )
    }

    // ═══════════ 判据格 ═══════════

    @Test
    fun `first enable shows the hint once on the receipt channel and persists the flag`() = runBlocking {
        var persisted = false
        val vm = makeVm({ persisted }, { persisted = it })
        delay(120)

        vm.toggleOnlyThisRound()

        val notice = vm.currentNotice.value
        assertNotNull("第一次把开关打开就该给那条轻提示（§2.2 第 3 条）", notice)
        assertEquals(
            "文案就是层内那一条常量（语义与真实范围合同一致）",
            ROUND_SCOPE_FIRST_HINT, notice!!.message
        )
        assertEquals(
            "走回执那格，不走 Warning——投进 Warning 会画成大黄警告（§4.4），这一格就是红",
            NoticeBoard.Channel.Knowledge, notice.channel
        )
        assertNotNull("回执形态自带时限（到点自散），不是要用户确认的那一种", notice.autoDismissMillis)
        assertTrue("投递当拍落旗标；不落盘 = 第二次开启又弹 = 红", persisted)
        assertTrue("开关真的被拨开了", vm.onlyThisRound.value)
    }

    @Test
    fun `closing or cancelling never shows the hint`() = runBlocking {
        var persisted = false
        val vm = makeVm({ persisted }, { persisted = it })
        delay(120)

        vm.toggleOnlyThisRound()          // ON：提示来了
        assertNotNull(vm.currentNotice.value)
        vm.endCurrentNotice()             // 读完关掉（用户点关闭 / 到点回报的同一形状）
        assertNull(vm.currentNotice.value)

        vm.toggleOnlyThisRound()          // OFF：取消/关闭方向
        assertNull(
            "取消/关闭不弹：关闭方向没有反馈义务，也弹就是红（弹是开启的反馈）",
            vm.currentNotice.value
        )
        assertTrue(
            "取消/关闭那一跳不许动旗标（它记的是'提示过没有'，不跟开关回 false）",
            persisted
        )
    }

    @Test
    fun `second enable never re-shows because the flag persisted`() = runBlocking {
        var persisted = false
        val vm = makeVm({ persisted }, { persisted = it })
        delay(120)

        vm.toggleOnlyThisRound()          // ON：提示来了、旗标落盘
        vm.endCurrentNotice()
        vm.toggleOnlyThisRound()          // OFF（新轮次/切库的复位在生产里走的是 SetOnlyThisRound(false)，同形状）
        vm.toggleOnlyThisRound()          // ON 第二次

        assertNull(
            "旗标已落盘：第二次开启又弹 = 红（先例：每次回来不再重弹）",
            vm.currentNotice.value
        )
    }

    @Test
    fun `flag read back from storage suppresses even the first enable in this session`() = runBlocking {
        var persisted = true              // 盘上已有旗标（早前会话给过）
        val vm = makeVm({ persisted }, { persisted = it })
        delay(120)

        vm.toggleOnlyThisRound()

        assertNull(
            "落盘旗标要在构造时读回来：不读盘 = 重启后第一次开启又弹 = 红",
            vm.currentNotice.value
        )
    }

    @Test
    fun `unwired persistence pair still shows at most once per vm session`() = runBlocking {
        val vm = makeVm()                 // 不接读写对：读默认 false、写丢弃
        delay(120)

        vm.toggleOnlyThisRound()
        assertNotNull(vm.currentNotice.value)
        vm.endCurrentNotice()
        vm.toggleOnlyThisRound()          // OFF
        vm.toggleOnlyThisRound()          // ON（写丢弃 ⇒ 旗标只剩会话内那一份）

        assertNull(
            "没接落盘口子时的回落：同一 VM 会话内也只弹一次（重复弹 = 红）",
            vm.currentNotice.value
        )
    }
}
