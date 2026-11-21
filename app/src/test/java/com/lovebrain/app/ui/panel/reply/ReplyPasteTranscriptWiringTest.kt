package com.lovebrain.app.ui.panel.reply

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 真实回复输入行 → 真实 VM/ComposerStore 的粘贴-拆条接线（不新增第二条输入链）。
 *
 * 钉的是用户旅程那一段：整段微信转录进输入框 → 轻点 ➕ → addDraftAuto 按归属表整批原子入库。
 * 换行保真由 [ReplyInputImeContractTest] 在 IME 会话参数那一层钉（真机粘贴不被拍平）；
 * 这一格量的是"换行一旦进了输入框，下游拆条/角色/原子性/草稿清除"整条接线对不对。
 * performTextInput 绕过 IME 直接写值，所以这里能逐字喂进多行样本。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyPasteTranscriptWiringTest {

    @get:Rule
    val rule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 用户真实块样本（逐字，条目间两空行，末条「行啵」）：6 条、中文日期、零警告 */
    private val userBlockSample =
        "W-2f\n2026年11月20日 15:14\n那我要晚一点粗发嘛\n\n\n" +
            "W-2f\n2026年11月20日 15:14\n四点出发？\n\n\n" +
            "陆晓\n2026年11月20日 15:15\n哈哈哈不急\n\n\n" +
            "陆晓\n2026年11月20日 15:15\n等我出发的时候和你是哦\n\n\n" +
            "陆晓\n2026年11月20日 15:15\n咱俩应该差不多同时到\n\n\n" +
            "W-2f\n2026年11月20日 15:16\n行啵"

    private val sampleRoles = mapOf(
        "W-2f" to ChatMessage.Role.ME,
        "陆晓" to ChatMessage.Role.HER
    )

    private val expectedSix = listOf(
        ChatMessage.Role.ME to "那我要晚一点粗发嘛",
        ChatMessage.Role.ME to "四点出发？",
        ChatMessage.Role.HER to "哈哈哈不急",
        ChatMessage.Role.HER to "等我出发的时候和你是哦",
        ChatMessage.Role.HER to "咱俩应该差不多同时到",
        ChatMessage.Role.ME to "行啵"
    )

    // ═══════════ 装配（与 BatchAutoParseTest.makeVm 同一副 mockk 骨架） ═══════════

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

    /** 生产接线的输入行半边：草稿读 composer.draftText，➕ 走 addDraftAuto，回落交宿主信号 */
    private fun mountReply(vm: LoveBrainViewModel, onFallback: (String) -> Unit = {}) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                val draft by vm.composer.draftText.collectAsState()
                ReplyInput(
                    draftText = draft,
                    currentRole = ChatMessage.Role.ME,
                    editingIndex = -1,
                    onDraftChange = { vm.setDraft(it) },
                    onRoleChange = { vm.setCurrentRole(it) },
                    onAdd = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            if (vm.addDraftAuto(text, ChatMessage.Role.ME)) {
                                vm.setDraft("")
                            } else {
                                onFallback(text)
                            }
                        }
                    },
                    onFocusChange = {},
                    onBatchImport = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun fieldText(): String =
        rule.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

    private fun addActionable(): Boolean =
        rule.onNodeWithContentDescription("添加").fetchSemanticsNode()
            .config.contains(SemanticsActions.OnClick)

    /** 列表更新计数：StateFlow 每真更新一帧；原子整批 = 1，逐条投 = N（unconfined 收集器同步收） */
    private fun listUpdatesDuring(vm: LoveBrainViewModel, block: () -> Unit): Int {
        val scope = CoroutineScope(testDispatcher)
        var updates = 0
        val job = scope.launch { vm.composer.messages.collect { updates++ } }
        val before = updates
        block()
        val delta = updates - before
        job.cancel()
        scope.cancel()
        return delta
    }

    private fun tapAdd() {
        rule.onNodeWithContentDescription("添加").performClick()
        rule.waitForIdle()
    }

    // ═══════════ 判据格 ═══════════

    /** (a) 整段转录：换行逐字进框 → 轻点一次 → 6 条按序按映射角色、草稿清空、恰好一次原子更新 */
    @Test
    fun pastedTranscriptLandsAsSixOrderedMessagesInOneAtomicUpdate() {
        val vm = makeVm(readRoles = { sampleRoles }, writeRoles = {})
        mountReply(vm)

        rule.onNode(hasSetTextAction()).performTextInput(userBlockSample)
        rule.waitForIdle()
        assertEquals(
            "原始换行逐字留在输入框里（maxLines=1 只收视觉，不裁值）",
            userBlockSample, fieldText()
        )
        assertTrue("非空草稿推到 ➕ 带上点击语义再点", addActionable())

        val updates = listUpdatesDuring(vm) { tapAdd() }

        assertEquals("整批恰好一次列表更新（同一条原子 AddMessages）；逐条投六次 = 红", 1, updates)
        assertEquals(
            "顺序即转录顺序，角色按归属表逐位对齐（composeRole 是 ME 也不许把陆晓标成我）",
            expectedSix, vm.composer.messagesNow.map { it.role to it.content }
        )
        assertEquals("消费完整批草稿清空", "", vm.composer.draftTextNow)
        assertEquals("输入框同步清空", "", fieldText())
    }

    /** (b) 普通文本照旧单条：不被误拆，角色就是输入行选的那颗 */
    @Test
    fun plainTextStillTakesTheSingleAddPath() {
        val vm = makeVm(readRoles = { sampleRoles }, writeRoles = {})
        mountReply(vm)

        rule.onNode(hasSetTextAction()).performTextInput("今晚一起吃饭")
        rule.waitForIdle()
        assertTrue(addActionable())

        val updates = listUpdatesDuring(vm) { tapAdd() }

        assertEquals("单条路径一次列表更新", 1, updates)
        assertEquals(
            "普通文本只落一条，原文一字不动",
            listOf(ChatMessage.Role.ME to "今晚一起吃饭"),
            vm.composer.messagesNow.map { it.role to it.content }
        )
        assertEquals("", vm.composer.draftTextNow)
    }

    /** (c) 有昵称没认过角色：只交回落信号（宿主开预填浮层），一条都不许落列表，草稿不丢 */
    @Test
    fun unmappedSpeakerFallsBackToThePrefilledSheetSignalWithoutAdding() {
        var fallbackRaw: String? = null
        val vm = makeVm(readRoles = { emptyMap() }, writeRoles = {})
        mountReply(vm, onFallback = { fallbackRaw = it })

        rule.onNode(hasSetTextAction()).performTextInput(userBlockSample)
        rule.waitForIdle()
        assertTrue(addActionable())

        val updates = listUpdatesDuring(vm) { tapAdd() }

        assertEquals("回落时零追加零通知", 0, updates)
        assertEquals("一条都不许落列表（默认成 composeRole 会把对方整段话标成我）",
            emptyList<ChatMessage>(), vm.composer.messagesNow)
        assertEquals("回落信号把原文交给宿主开预填浮层", userBlockSample, fallbackRaw)
        assertEquals("回落不清草稿：用户关掉浮层这句话还在输入框里", userBlockSample, fieldText())
    }

    /** (d) CRLF 变体：换行形态不同仍拆出同样 6 条（解析器统一换行），原子一次 */
    @Test
    fun crlfTranscriptVariantStillSplitsIntoTheSameSixMessages() {
        val crlf = userBlockSample.replace("\n", "\r\n")
        val vm = makeVm(readRoles = { sampleRoles }, writeRoles = {})
        mountReply(vm)

        rule.onNode(hasSetTextAction()).performTextInput(crlf)
        rule.waitForIdle()
        val raw = fieldText()
        assertTrue("CRLF 粘贴的换行也留在框里", raw.contains('\n') || raw.contains('\r'))
        assertEquals("换行形态归一后正文逐字不变", userBlockSample, raw.replace("\r\n", "\n"))
        assertTrue(addActionable())

        val updates = listUpdatesDuring(vm) { tapAdd() }

        assertEquals("CRLF 整批仍恰好一次原子更新", 1, updates)
        assertEquals(expectedSix, vm.composer.messagesNow.map { it.role to it.content })
        assertEquals("", vm.composer.draftTextNow)
    }
}