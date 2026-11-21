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
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 轻点 ➕ 的唯一路径（[LoveBrainViewModel.addDraftAuto]）的 A/B 分流：
 *
 * · **A 路**：空行分块的转录 → 每条正文一次原子入库，所有条目角色统一 = `composeRole`
 *   （传 ME 就全 ME，与条目序号无关）；
 * · **B 路**：结构不合格（冒号两行、普通文本、坏块、时间行还带半句话）→ 整段一条，角色 = `composeRole`；
 * · **IDEA 保护**：`composeRole == IDEA` 不进批量通道（`BatchImportDraft` 会硬挡），
 *   整段一条走备注通道，绝不拆；
 * · **空文本** no-op：不落消息、不写备注。
 *
 * 拆条判据归 [com.lovebrain.app.feature.composer.BatchTranscriptParser.autoSplitEntries]
 * 的合同（结构规则另有单测），这里只量分流后果。
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
        unmockkStatic(Log::class)
    }

    // ═══════════ 装配（与其它 VM 行为测试同一副骨架） ═══════════

    private fun makeVm(): LoveBrainViewModel {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.thinkingMode } returns 0
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
        every { promptBuilder.validateConfig(any()) } returns
            PromptBuilder.ConfigValidationResult(0, emptyList())
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
            )
        )
    }

    /** 空行分块的三块转录：块头/时间行只是结构，正文三句是条目 */
    private val blockTranscript =
        "小美\n2024-01-01 12:00\n在吗\n\n" +
            "我\n2024-01-01 12:01\n在的\n\n" +
            "小美\n2024-01-01 12:02\n晚安"

    /** 列表更新计数：StateFlow 每次真更新一帧；原子整批 = 1，逐条投 = N */
    private suspend fun CoroutineScope.countListUpdatesDuring(vm: LoveBrainViewModel, block: () -> Unit): Int {
        var updates = 0
        val collector = launch(testDispatcher) { vm.composer.messages.collect { updates++ } }
        val before = updates
        block()
        val delta = updates - before
        collector.cancel()
        return delta
    }

    // ═══════════ A 路：全部条目 role = composeRole ═══════════

    @Test
    fun `A path lands every entry under the compose role`() = runBlocking {
        val vm = makeVm()
        delay(120)

        val updates = countListUpdatesDuring(vm) {
            // composeRole 给 ME：A 路全部条目都该是 ME
            vm.addDraftAuto(blockTranscript, ChatMessage.Role.ME)
        }

        assertEquals(
            "全部条目 role = composeRole（传 ME 就全 ME）；顺序即原文顺序",
            listOf(
                ChatMessage.Role.ME to "在吗",
                ChatMessage.Role.ME to "在的",
                ChatMessage.Role.ME to "晚安"
            ),
            vm.composer.messagesNow.map { it.role to it.content }
        )
        assertEquals("整批恰好一次列表更新（同一条原子 AddMessages）", 1, updates)
        assertNull("整批入库不该弹通知", vm.currentNotice.value)
    }

    @Test
    fun `A path keeps the compose role for a fourth entry`() = runBlocking {
        val vm = makeVm()
        delay(120)

        vm.addDraftAuto(
            "$blockTranscript\n\n我\n2024-01-01 12:03\n明天见",
            ChatMessage.Role.HER
        )

        assertEquals(
            listOf(
                ChatMessage.Role.HER,
                ChatMessage.Role.HER,
                ChatMessage.Role.HER,
                ChatMessage.Role.HER
            ),
            vm.composer.messagesNow.map { it.role }
        )
        assertEquals("明天见", vm.composer.messagesNow[3].content)
    }

    /**
     * A 路正文保真（端到端）：正文行首缩进/行尾空格是粘贴文本的一部分，解析器保留它，
     * 写入口径（`ComposerStore.addMessages`）也必须保留——任何一处 trim 都会把它削掉，
     * 且中间没有任何一层能补回来。
     */
    @Test
    fun `A path preserves indented body lines end to end`() = runBlocking {
        val vm = makeVm()
        delay(120)

        val text = "小美\n2024-01-01 12:00\n  第一行\n    第二行\n\n" +
            "我\n2024-01-01 12:01\n  收到  "
        vm.addDraftAuto(text, ChatMessage.Role.HER)

        assertEquals(
            "正文行首缩进与行尾空格按原样入库（只去块级首尾空行）",
            listOf(
                ChatMessage.Role.HER to "  第一行\n    第二行",
                ChatMessage.Role.HER to "  收到  "
            ),
            vm.composer.messagesNow.map { it.role to it.content }
        )
    }

    // ═══════════ B 路：整段一条，角色 = composeRole ═══════════

    /**
     * B 路不受 A 路保真改动影响：整段一条仍按既有输入习惯归一（逐条落点保留 trim），
     * 不为 A 路把两条路的清洗口径混成一份。
     */
    @Test
    fun `B path still trims the whole single message`() = runBlocking {
        val vm = makeVm()
        delay(120)

        vm.addDraftAuto("  今晚一起吃饭  \n", ChatMessage.Role.ME)

        assertEquals(
            listOf(ChatMessage.Role.ME to "今晚一起吃饭"),
            vm.composer.messagesNow.map { it.role to it.content }
        )
    }

    @Test
    fun `B path keeps a colon two line text as one message under compose role`() = runBlocking {
        val vm = makeVm()
        delay(120)

        val text = "小美：在吗\n我：在的"

        val updates = countListUpdatesDuring(vm) {
            vm.addDraftAuto(text, ChatMessage.Role.ME)
        }

        assertEquals(
            "没有空行分块 ⇒ 整段一条，角色就是输入框那颗",
            listOf(ChatMessage.Role.ME to text),
            vm.composer.messagesNow.map { it.role to it.content }
        )
        assertEquals("单条路径一次列表更新", 1, updates)
    }

    @Test
    fun `B path uses the compose role for plain text`() = runBlocking {
        val vm = makeVm()
        delay(120)

        vm.addDraftAuto("今晚一起吃饭", ChatMessage.Role.HER)

        assertEquals(
            listOf(ChatMessage.Role.HER to "今晚一起吃饭"),
            vm.composer.messagesNow.map { it.role to it.content }
        )
    }

    @Test
    fun `single block with valid structure takes the A path as one entry`() = runBlocking {
        val vm = makeVm()
        delay(120)

        val text = "小美\n2024-01-01 12:00\n今天好累"
        vm.addDraftAuto(text, ChatMessage.Role.ME)

        assertEquals(
            "单块结构成立 ⇒ A 路一条：只留正文，块头/时间行被结构吃掉，角色 = composeRole",
            listOf(ChatMessage.Role.ME to "今天好累"),
            vm.composer.messagesNow.map { it.role to it.content }
        )
    }

    /**
     * 时间行后面还跟着半句话 ⇒ 第 2 行不是时间行 ⇒ 坏块 ⇒ 整段 B 路一条。
     *
     * 核验给的真反例：旧写法在 "21:30 见" 上认了块结构，于是切成 2 条，还把
     * "见" 与 "带伞" 一起丢掉。
     */
    @Test
    fun `timestamp plus trailing words falls back to one verbatim message`() = runBlocking {
        val vm = makeVm()
        delay(120)

        val text = "甲\n2024-01-01\n加班\n\n乙\n21:30 见\n带伞"
        vm.addDraftAuto(text, ChatMessage.Role.ME)

        assertEquals(
            "整段一条、原文一字不动：既不拆条，也不丢 '见' 与 '带伞'",
            listOf(ChatMessage.Role.ME to text),
            vm.composer.messagesNow.map { it.role to it.content }
        )
    }

    // ═══════════ IDEA 保护：不拆、不进批量通道 ═══════════

    @Test
    fun `IDEA compose role never splits and never reaches the message list`() = runBlocking {
        val vm = makeVm()
        delay(120)

        // 逐行不重复：备注合并按行去重（mergeNote 的既有口径），这里要量的是
        // "整段一条、一行没少、顺序不变"，不是去重行为
        val text = "小美\n2024-01-01 12:00\n在吗\n\n我\n2024-01-01 12:01\n在的"
        vm.addDraftAuto(text, ChatMessage.Role.IDEA)

        assertTrue("IDEA 不进真实对话通道（BatchImportDraft 也不接受）", vm.composer.messagesNow.isEmpty())
        assertEquals(
            "整段作为一条备注（未拆条）：原文逐行都在、顺序不变",
            text.lines().filter { it.isNotBlank() },
            vm.composer.noteTextNow.lines().filter { it.isNotBlank() }
        )
    }

    // ═══════════ 空文本 no-op ═══════════

    @Test
    fun `blank text is a no-op`() = runBlocking {
        val vm = makeVm()
        delay(120)

        val updates = countListUpdatesDuring(vm) {
            vm.addDraftAuto("   \n \t ", ChatMessage.Role.HER)
            vm.addDraftAuto("", ChatMessage.Role.ME)
        }

        assertTrue(vm.composer.messagesNow.isEmpty())
        assertEquals("", vm.composer.noteTextNow)
        assertEquals("空文本零列表更新", 0, updates)
        assertNull(vm.currentNotice.value)
    }

    @Test
    fun `addDraftAuto is a unit returning fire-and-forget entry`() = runBlocking {
        val vm = makeVm()
        delay(120)

        val unit: Unit = vm.addDraftAuto("今晚一起吃饭", ChatMessage.Role.ME)
        assertEquals(Unit, unit)

        vm.addDraftAuto(blockTranscript, ChatMessage.Role.ME)
        assertEquals("单条 + 整批都在同一条路径上落库", 4, vm.composer.messagesNow.size)
        assertTrue(vm.currentNotice.value == null)
    }
}
