package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.PromptBuilder
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 普通 ➕ tap 的自动拆分（角色策略 B：绝不静默错标）——钉在 VM 门面 [LoveBrainViewModel.addDraftAuto]
 * 与归属表读写那几条真实路径上。每格的红条件写在断言消息里：
 *
 * · 普通文本照旧走单条 AddMessage——**被误拆成多条 = 红**；
 * · 认全角色的转录整批走同一条原子 AddMessages：顺序逐位保持、**列表只更新一次**
 *   （逐条投 N 次 AddMessage = 红，那是外部审计 F3 修过的形状）；
 * · 拆出 ≥2 条完备记录但夹 warning（NeedsReview：转发标题被跳过 / 续行并回正文等）→
 *   只交回落信号走**既有浮层预览**，**一条都不许入库**，也不许直吞成一条（认全角色也一样）；
 * · 有昵称没认过角色 → 只交回落信号，**一条都不许落列表**，更不许默认成当前 composeRole
 *   （把对方的话标成我 = 红）；
 * · 浮层提交记住的归属表要落盘、要并入旧账（丢旧昵称 = 红），下一次 tap 零摩擦；
 * · 没接落盘口子时读空表：转录一律回落显式指定，绝不猜角色。
 *
 * 解析三态判据（NotTranscript / NeedsReview / Ready）归 `BatchTranscriptParser.autoSplit`
 * 的合同，这里只消费它的产物，不复制第二份规则。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchAutoParseTest {

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

    // ═══════════ 装配（与 BatchImportFirstHintTest 同一副骨架，只按本格需要收窄） ═══════════

    private fun makeVm(
        readRoles: (() -> Map<String, ChatMessage.Role>)? = null,
        writeRoles: ((Map<String, ChatMessage.Role>) -> Unit)? = null
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
            readBatchImportNameRoles = readRoles,
            writeBatchImportNameRoles = writeRoles
        )
    }

    /** 冒号行格式转录：两条以上、全部有昵称、无警告——autoSplit 该交 Ready 的那一种 */
    private val transcript = "小美：在吗\n我：在的\n小美：今晚一起吃饭？"

    private val fullRoles = mapOf(
        "小美" to ChatMessage.Role.HER,
        "我" to ChatMessage.Role.ME
    )

    /** 列表更新计数：StateFlow 每次真更新一帧；原子整批 = 1，逐条投 = N（仪器先证阳性） */
    private suspend fun CoroutineScope.countListUpdatesDuring(vm: LoveBrainViewModel, block: () -> Unit): Int {
        var updates = 0
        val collector = launch(testDispatcher) { vm.composer.messages.collect { updates++ } }
        val before = updates
        block()
        val delta = updates - before
        collector.cancel()
        return delta
    }

    // ═══════════ 判据格 ═══════════

    @Test
    fun `plain text keeps the single add path unchanged`() = runBlocking {
        val vm = makeVm(readRoles = { fullRoles }, writeRoles = {})
        delay(120)

        var consumed = false
        val updates = countListUpdatesDuring(vm) {
            consumed = vm.addDraftAuto("今晚一起吃饭", ChatMessage.Role.ME)
        }

        assertTrue("普通文本该被消费（走既有单条路径）", consumed)
        assertEquals(
            "普通文本只落一条，角色就是输入行选的那颗",
            listOf(ChatMessage.Role.ME to "今晚一起吃饭"),
            vm.composer.messagesNow.map { it.role to it.content }
        )
        assertEquals("单条路径一次列表更新", 1, updates)
    }

    @Test
    fun `a lone colon line is not a transcript and is never split`() = runBlocking {
        val vm = makeVm(readRoles = { fullRoles }, writeRoles = {})
        delay(120)

        val consumed = vm.addDraftAuto("小美：在吗", ChatMessage.Role.ME)

        assertTrue("只有一条的文本不够成转录，照旧单条消费", consumed)
        assertEquals(
            "单条原文一字不动（不剥昵称、不改角色）——拆成多条或标成 HER 都是红",
            listOf(ChatMessage.Role.ME to "小美：在吗"),
            vm.composer.messagesNow.map { it.role to it.content }
        )
    }

    @Test
    fun `a transcript that needs review opens the sheet and never becomes one blob`() = runBlocking {
        // 合并转发首行必产 warning（标题跳过），后面两条记录完备：NeedsReview 该走浮层预览，
        // 既不静默丢弃，也不直吞成一条——即便归属表已认全也不许绕开预览。
        val vm = makeVm(readRoles = { fullRoles }, writeRoles = { })
        delay(120)

        var consumed = true
        val updates = countListUpdatesDuring(vm) {
            consumed = vm.addDraftAuto("小美和小红的聊天记录\n小美：在吗\n我：在的", ChatMessage.Role.ME)
        }

        assertFalse("≥2 条完备记录但有 warning → 交既有浮层预览，不直吞", consumed)
        assertEquals(
            "NeedsReview 一条都不许入库（变成一大坨 = 红）",
            emptyList<ChatMessage>(), vm.composer.messagesNow
        )
        assertEquals("零追加零通知", 0, updates)
        assertFalse("回浮层 = 即将看见批量入口，首次轻提示同拍收掉", vm.batchImportHintVisible.value)
    }

    @Test
    fun `fully mapped transcript lands as one atomic batch in preview order`() = runBlocking {
        val vm = makeVm(readRoles = { fullRoles }, writeRoles = {})
        delay(120)

        var consumed = false
        val updates = countListUpdatesDuring(vm) {
            consumed = vm.addDraftAuto(transcript, ChatMessage.Role.ME)
        }

        assertTrue("认全角色的转录该被消费（整批入库，不开浮层）", consumed)
        assertEquals(
            "顺序即转录顺序，角色按归属表逐位对齐——composeRole 是 ME 也不许把小美标成我",
            listOf(
                ChatMessage.Role.HER to "在吗",
                ChatMessage.Role.ME to "在的",
                ChatMessage.Role.HER to "今晚一起吃饭？"
            ),
            vm.composer.messagesNow.map { it.role to it.content }
        )
        assertEquals(
            "整批恰好一次列表更新（同一条原子 AddMessages）；逐条投三次 = 红",
            1, updates
        )
        assertFalse("完成一次导入 = 已发现入口，首次轻提示同拍收掉", vm.batchImportHintVisible.value)
    }

    @Test
    fun `an unmapped speaker falls back without adding anything`() = runBlocking {
        val vm = makeVm(readRoles = { emptyMap() }, writeRoles = {})
        delay(120)

        var consumed = true
        val updates = countListUpdatesDuring(vm) {
            consumed = vm.addDraftAuto(transcript, ChatMessage.Role.ME)
        }

        assertFalse("有昵称没认过角色 → 只交回落信号，让宿主开既有浮层显式指定", consumed)
        assertEquals(
            "回落时一条都不许落列表——默认成 composeRole 会把对方整段话标成我",
            emptyList<ChatMessage>(), vm.composer.messagesNow
        )
        assertEquals("零追加零通知", 0, updates)
        assertFalse("回浮层 = 即将看见批量入口，首次轻提示同拍收掉", vm.batchImportHintVisible.value)
    }

    @Test
    fun `a partially mapped transcript still falls back`() = runBlocking {
        val vm = makeVm(readRoles = { mapOf("小美" to ChatMessage.Role.HER) }, writeRoles = {})
        delay(120)

        val consumed = vm.addDraftAuto(transcript, ChatMessage.Role.ME)

        assertFalse("只认出一半昵称也不许入库：剩下的必须显式指定，不许猜", consumed)
        assertEquals(emptyList<ChatMessage>(), vm.composer.messagesNow)
    }

    @Test
    fun `remembered roles persist and the next tap is zero friction`() = runBlocking {
        val disk = mutableMapOf<String, ChatMessage.Role>()
        val vm = makeVm(readRoles = { disk.toMap() }, writeRoles = { disk.clear(); disk.putAll(it) })
        delay(120)

        assertFalse("归属表还没认过：第一次 tap 回落", vm.addDraftAuto(transcript, ChatMessage.Role.ME))

        // 浮层提交那一拍：宿主把整份归属表交回来
        vm.rememberBatchImportNameRoles(fullRoles)
        assertEquals("归属表必须落盘（写丢弃 = 下次 tap 又问一遍 = 红）", fullRoles, disk)

        assertTrue("认全之后同一转录零摩擦消费", vm.addDraftAuto(transcript, ChatMessage.Role.ME))
        assertEquals(3, vm.composer.messagesNow.size)

        // 重启后的新 VM 读同一份盘：不用再问
        val reborn = makeVm(readRoles = { disk.toMap() }, writeRoles = { disk.clear(); disk.putAll(it) })
        delay(120)
        assertTrue("落盘的归属表要在构造后读得回来：新 VM 第一次 tap 就零摩擦", reborn.addDraftAuto(transcript, ChatMessage.Role.ME))
        assertEquals(3, reborn.composer.messagesNow.size)
    }

    @Test
    fun `remember merges into the old ledger instead of replacing it`() = runBlocking {
        val disk = mutableMapOf("旧友" to ChatMessage.Role.HER)
        val vm = makeVm(readRoles = { disk.toMap() }, writeRoles = { disk.clear(); disk.putAll(it) })
        delay(120)

        vm.rememberBatchImportNameRoles(mapOf("小美" to ChatMessage.Role.HER))

        assertEquals(
            "本次转录没出现的旧昵称不许丢（整表覆盖 = 红）",
            mapOf("旧友" to ChatMessage.Role.HER, "小美" to ChatMessage.Role.HER),
            disk
        )
        assertEquals("只读出口与盘上同一份", disk, vm.batchImportKnownRoles)
    }

    @Test
    fun `an empty remember never writes the store`() = runBlocking {
        var writes = 0
        val vm = makeVm(readRoles = { emptyMap() }, writeRoles = { writes++ })
        delay(120)

        vm.rememberBatchImportNameRoles(emptyMap())

        assertEquals("空表不写盘（全是未识别条目按序号指定的那次提交没有昵称账可记）", 0, writes)
    }

    @Test
    fun `unwired role store never guesses roles`() = runBlocking {
        val vm = makeVm()   // 不接读写对：读空表、写丢弃
        delay(120)

        assertFalse("没接落盘口子时转录一律回落显式指定——宁可多问一次，绝不猜角色", vm.addDraftAuto(transcript, ChatMessage.Role.ME))
        assertEquals(emptyList<ChatMessage>(), vm.composer.messagesNow)
        assertTrue("普通文本不受影响，照旧单条消费", vm.addDraftAuto("今晚一起吃饭", ChatMessage.Role.ME))
        assertNull("回落与单条都不该投任何通知", vm.currentNotice.value)
    }
}