package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.feature.composer.BatchImportDraft
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.model.ChatMessage
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 批量导入入口的首次轻提示（外部审计 F3-2：入口只有 ➕ 的长按、太深）——钉在 VM 这几条
 * 真实路径上（`maybeShowBatchImportFirstHint` / `markBatchImportDiscovered` /
 * `importBatchMessages`）。判据与先例（`RoundScopeFirstHintTest`、落盘先例
 * `intent_intro_seen`/`round_scope_hint_seen`：每次回来不再重弹）同款，每格的红条件写在
 * 断言消息里：
 *
 * · 首次进回复输入面板投一次就落旗标——**旗标不落盘（写丢弃）→ 下次进面板又弹 = 红**；
 * · 落盘的旗标构造时要读回来——**落了盘但构造不读 → 重启后第一次进面板又弹 = 红**；
 * · **长按过 ➕ 之后不再提示**（`markBatchImportDiscovered` 只收旗标、不投通知——
 *   收了旗标还再弹 = 红）；
 * · **完成过一次导入之后不再提示**（`importBatchMessages` 同拍收旗标——导入完了还弹 = 红）；
 * · 通道走 Knowledge（回执形态 + 到点自散）——**投进 Warning 通道 = 屏上画成大黄警告
 *   （§4.4"普通提示不许画成大黄警告"）= 红**；
 * · 没接落盘口子时的回落（读默认、写丢弃）：**同一 VM 会话内重复弹 = 红**。
 *
 * 文案由屏上经 `stringResource` 交进来（`strings.xml` 持有，英文环境不再念中文），
 * 这里用测试串代表屏上会交进来的那一份；通知位的排队/刷新归 `NoticeBoardTest`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchImportFirstHintTest {

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

    // ═══════════ 装配（与 RoundScopeFirstHintTest 同一副骨架，只按本格需要收窄） ═══════════

    private fun makeVm(
        readBatchImportHintShown: (() -> Boolean)? = null,
        writeBatchImportHintShown: ((Boolean) -> Unit)? = null
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
            readBatchImportHintShown = readBatchImportHintShown,
            writeBatchImportHintShown = writeBatchImportHintShown
        )
    }

    private fun drafts() = listOf(
        BatchImportDraft(ChatMessage.Role.HER, "在吗"),
        BatchImportDraft(ChatMessage.Role.ME, "在的")
    )

    // ═══════════ 判据格 ═══════════

    @Test
    fun `first panel entry shows the hint once on the receipt channel and persists the flag`() = runBlocking {
        var persisted = false
        val vm = makeVm({ persisted }, { persisted = it })
        delay(120)
        assertTrue("用户还没发现入口，提示该给（构造读盘：盘上没旗标）", vm.batchImportHintVisible.value)

        vm.maybeShowBatchImportFirstHint("长按「＋」可批量粘贴导入聊天记录")

        val notice = vm.currentNotice.value
        assertNotNull("首次进回复输入面板就该给那条轻提示（外部审计 F3-2）", notice)
        assertEquals("文案就是屏上交进来的那一份（strings.xml 持有）",
            "长按「＋」可批量粘贴导入聊天记录", notice!!.message)
        assertEquals(
            "走回执那格，不走 Warning——投进 Warning 会画成大黄警告（§4.4），这一格就是红",
            NoticeBoard.Channel.Knowledge, notice.channel
        )
        assertNotNull("回执形态自带时限（到点自散），不是要用户确认的那一种", notice.autoDismissMillis)
        assertTrue("投递当拍落旗标；不落盘 = 下次进面板又弹 = 红", persisted)
        assertFalse("旗标落盘的同帧收起只读出口：界面即刻不再显示提示", vm.batchImportHintVisible.value)
    }

    @Test
    fun `second panel entry never re-shows because the flag persisted`() = runBlocking {
        var persisted = false
        val vm = makeVm({ persisted }, { persisted = it })
        delay(120)

        vm.maybeShowBatchImportFirstHint("第一次")
        assertNotNull(vm.currentNotice.value)
        vm.endCurrentNotice()

        vm.maybeShowBatchImportFirstHint("第二次")
        assertNull(
            "旗标已落盘：再次进面板又弹 = 红（先例：每次回来不再重弹）",
            vm.currentNotice.value
        )
    }

    @Test
    fun `discovering the entry by long-press suppresses the hint without posting a notice`() = runBlocking {
        var persisted = false
        val vm = makeVm({ persisted }, { persisted = it })
        delay(120)

        vm.markBatchImportDiscovered()   // 长按过 ➕（面板 onBatchImport 里 mark）

        vm.maybeShowBatchImportFirstHint("不该再弹")
        assertNull(
            "长按过 ➕ = 用户已发现入口：收了旗标还再弹 = 红",
            vm.currentNotice.value
        )
        assertTrue("发现入口也要落旗标（跨会话不再提示）", persisted)
        assertFalse(vm.batchImportHintVisible.value)
    }

    @Test
    fun `completing an import marks the discovery and suppresses the hint`() = runBlocking {
        var persisted = false
        val vm = makeVm({ persisted }, { persisted = it })
        delay(120)

        vm.importBatchMessages(drafts())   // 完成一次导入（同拍收旗标）

        vm.maybeShowBatchImportFirstHint("不该再弹")
        assertNull(
            "完成过一次导入 = 用户已发现入口：之后还弹 = 红",
            vm.currentNotice.value
        )
        assertTrue("导入完成的同拍旗标落盘", persisted)
        assertFalse(vm.batchImportHintVisible.value)
    }

    @Test
    fun `an import still lands in the real dialogue channel while marking discovery`() = runBlocking {
        val vm = makeVm()
        delay(120)

        vm.importBatchMessages(drafts())

        assertTrue(
            "导入本体照常走真实对话通道（改走 AddMessages 不改变导入的行为）",
            vm.composer.messagesNow.size == 2
        )
    }

    @Test
    fun `flag read back from storage suppresses even the first entry in this session`() = runBlocking {
        var persisted = true              // 盘上已有旗标（早前会话给过/已发现）
        val vm = makeVm({ persisted }, { persisted = it })
        delay(120)

        assertFalse("构造读盘：盘上有旗标 → 提示不该给", vm.batchImportHintVisible.value)
        vm.maybeShowBatchImportFirstHint("不该弹")
        assertNull(
            "落盘旗标要在构造时读回来：不读盘 = 重启后第一次进面板又弹 = 红",
            vm.currentNotice.value
        )
    }

    @Test
    fun `unwired persistence pair still shows at most once per vm session`() = runBlocking {
        val vm = makeVm()                 // 不接读写对：读默认 false、写丢弃
        delay(120)

        vm.maybeShowBatchImportFirstHint("第一次")
        assertNotNull(vm.currentNotice.value)
        vm.endCurrentNotice()
        vm.maybeShowBatchImportFirstHint("第二次")

        assertNull(
            "没接落盘口子时的回落：同一 VM 会话内也只弹一次（重复弹 = 红）",
            vm.currentNotice.value
        )
    }
}
