package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.ui.panel.LoveBrainPanelScreen
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
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
 * 「输入框直接解析」的整屏接线：轻点 ➕ → 面板生产 `onAdd` → 真 [LoveBrainViewModel] 的 A/B 分流。
 *
 * 这里**不用 VM 替身**：上一版把 `addDraftAuto` 的合同在测试里重写了一遍，等于量的是一份平行实现。
 * 现在挂的是真 VM + 真 [ComposerStore]，面板那一句 `addDraftAuto(text, composeRole)` 是唯一被测入口：
 * · A 路（空行分块，含单块）→ 每条正文一次原子入库，角色全部 = 输入框那颗；
 * · B 路（冒号两行/普通文本）→ 整段一条，角色 = 输入框那颗；
 * · 《补充》（composeRole = IDEA）→ 仍走 SubmitNote，不进消息列表、不触发拆条。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchAutoParseWiringTest {

    @get:Rule
    val rule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    // ═══════════ 装配：真 VM（依赖仍是 mockk，状态与行为全是真的） ═══════════

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

    private fun mountPanel(vm: LoveBrainViewModel) {
        rule.setContent {
            UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = vm,
                        onInputFocusChange = { _, _, _ -> },
                        onInputIntent = { },
                        onClearComposeFocus = { },
                        onResize = { _, _ -> },
                        onMove = { _, _ -> },
                        onCopy = { },
                        onOpenAppPage = { },
                        onCollapse = { }
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /** 输入行那颗 ➕——轻点是唯一入口 */
    private fun tapAdd() {
        rule.onNodeWithContentDescription("添加").performClick()
        rule.waitForIdle()
    }

    private fun messagePairs(vm: LoveBrainViewModel) =
        vm.composer.messagesNow.map { it.role to it.content }

    /** 空行分块的三块转录（块头/时间行/正文） */
    private val blockTranscript =
        "小美\n2024-01-01 12:00\n在吗\n\n" +
            "我\n2024-01-01 12:01\n在的\n\n" +
            "小美\n2024-01-01 12:02\n晚安"

    // ═══════════ A 路：轻点一次 → 全部条目 role = 输入框那颗 ═══════════

    @Test
    fun tappingAddLandsTheBlockTranscriptUnderTheComposeRole() {
        val vm = makeVm()
        mountPanel(vm)
        // 输入框角色选「我」：全部条目都必须是 ME
        vm.composer.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.ME))
        vm.setDraft(blockTranscript)
        rule.waitForIdle()

        tapAdd()

        assertEquals(
            "3 条按原文顺序入库，角色全部 = 输入框那颗（ME）",
            listOf(
                ChatMessage.Role.ME to "在吗",
                ChatMessage.Role.ME to "在的",
                ChatMessage.Role.ME to "晚安"
            ),
            messagePairs(vm)
        )
        assertEquals("整批被吞掉：草稿清空", "", vm.composer.draftTextNow)

        // 草稿已空 ⇒ 再点一次不许把同一份转录再入库一遍
        tapAdd()
        assertEquals("草稿清空后 ➕ 不再触发解析", 3, vm.composer.messagesNow.size)
    }

    @Test
    fun singleBlockTranscriptLandsAsOneEntryUnderTheComposeRole() {
        val vm = makeVm()
        mountPanel(vm)
        vm.composer.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.HER))
        vm.setDraft("小美\n2024-01-01 12:00\n今天好累")
        rule.waitForIdle()

        tapAdd()

        assertEquals(
            "单块结构成立 ⇒ A 路一条：只留正文，块头/时间行被结构吃掉，角色 = 输入框那颗",
            listOf(ChatMessage.Role.HER to "今天好累"),
            messagePairs(vm)
        )
        assertEquals("草稿清空", "", vm.composer.draftTextNow)
    }

    // ═══════════ B 路：冒号两行整段一条，角色 = 输入框那颗 ═══════════

    @Test
    fun colonTwoLineTextStaysOneMessageInTheComposerRole() {
        val vm = makeVm()
        mountPanel(vm)
        vm.composer.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.HER))
        val text = "小美：在吗\n我：在的"
        vm.setDraft(text)
        rule.waitForIdle()

        tapAdd()

        assertEquals(
            "没有空行分块 ⇒ 整段一条，角色就是输入框那颗（不按冒号拆）",
            listOf(ChatMessage.Role.HER to text),
            messagePairs(vm)
        )
        assertEquals("草稿清空", "", vm.composer.draftTextNow)
    }

    @Test
    fun plainTextLandsAsOneMessage() {
        val vm = makeVm()
        mountPanel(vm)
        vm.composer.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.ME))
        vm.setDraft("今晚一起吃饭")
        rule.waitForIdle()

        tapAdd()

        assertEquals(
            listOf(ChatMessage.Role.ME to "今晚一起吃饭"),
            messagePairs(vm)
        )
        assertEquals("", vm.composer.draftTextNow)
    }

    // ═══════════ 《补充》= IDEA：不触发拆条，仍走 SubmitNote ═══════════

    @Test
    fun supplementKindStillSubmitsANoteInsteadOfMessages() {
        val vm = makeVm()
        mountPanel(vm)
        // 先切《补充》再写草稿：切档那一下会把已有草稿归位，顺序反了量到的就不是本格
        vm.composer.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        // 逐行不重复：备注合并按行去重（mergeNote 的既有口径），这里量的是"整段一条、一行没少"
        val note = "小美\n2024-01-01 12:00\n在吗\n\n我\n2024-01-01 12:01\n在的"
        vm.setDraft(note)
        rule.waitForIdle()

        tapAdd()

        assertTrue("《补充》不是聊天流里的一条消息", vm.composer.messagesNow.isEmpty())
        assertEquals(
            "整段作为一条备注（未拆条）：原文逐行都在、顺序不变",
            note.lines().filter { it.isNotBlank() },
            vm.composer.noteTextNow.lines().filter { it.isNotBlank() }
        )
        assertEquals("投递后草稿清空", "", vm.composer.draftTextNow)
    }

    /** 整屏仍只有一个可编辑输入框：没有浮层/预览被这次改动新增出来 */
    @Test
    fun addFlowAddsNoSecondEditableFieldOrOverlay() {
        val vm = makeVm()
        mountPanel(vm)
        vm.setDraft(blockTranscript)
        rule.waitForIdle()

        val before = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        tapAdd()
        val after = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size

        assertEquals("可编辑输入框数目不变", before, after)
        assertEquals(1, after)
    }
}
