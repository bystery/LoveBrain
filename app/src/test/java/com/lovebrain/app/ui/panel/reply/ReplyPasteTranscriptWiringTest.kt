package com.lovebrain.app.ui.panel.reply

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
 * 真实面板输入行 → 真实 VM/ComposerStore 的粘贴-解析接线（**只有一条输入链**）。
 *
 * 用户口径：不要浮层、不要长按——多行文本粘进输入框，轻点 ➕ 就由面板生产 `onAdd`
 * 调 `addDraftAuto` 直接解析入库。这一格量的是"换行进了输入框之后下游真会按块拆条、
 * 角色统一用输入框那颗"，并且**走 [LoveBrainPanelScreen] 的 onAdd 生产接线**（不是在测试里手抄一段
 * 同名逻辑）。换行保真由 [ReplyInputImeContractTest] 在 IME 会话参数那一层钉（真机粘贴不被拍平）；
 * `performTextInput` 绕过 IME 直接写值，所以这里能逐字喂进多行样本。
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

    /** 空行分块的三块转录：块头/时间行只是结构，正文三句是条目 */
    private val transcript =
        "小美\n2024-01-01 12:00\n在吗\n\n" +
            "我\n2024-01-01 12:01\n在的\n\n" +
            "小美\n2024-01-01 12:02\n晚安"

    /** A 路全部条目 role = 输入框当前角色（这几格不设角色，默认 HER） */
    private val expectedComposeRoleEntries = listOf(
        ChatMessage.Role.HER to "在吗",
        ChatMessage.Role.HER to "在的",
        ChatMessage.Role.HER to "晚安"
    )

    // ═══════════ 装配（与 BatchAutoParseTest.makeVm 同一副 mockk 骨架） ═══════════

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

    /** 生产接线：整屏面板，草稿/角色全读真 composer，➕ 走面板自己的 onAdd */
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

    private fun fieldText(): String =
        rule.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

    private fun addActionable(): Boolean =
        rule.onNodeWithContentDescription("添加").fetchSemanticsNode()
            .config.contains(SemanticsActions.OnClick)

    private fun tapAdd() {
        rule.onNodeWithContentDescription("添加").performClick()
        rule.waitForIdle()
    }

    // ═══════════ 判据格 ═══════════

    /** (a) 多行转录：换行逐字进框 → 轻点一次 → 多条按序、角色全部 = 输入框当前角色、草稿清空 */
    @Test
    fun pastedBlockTranscriptLandsAsOrderedMessagesUnderTheComposeRole() {
        val vm = makeVm()
        mountPanel(vm)

        rule.onNode(hasSetTextAction()).performTextInput(transcript)
        rule.waitForIdle()
        assertEquals(
            "原始换行逐字留在输入框里（maxLines=1 只收视觉，不裁值）",
            transcript, fieldText()
        )
        assertTrue("非空草稿推到 ➕ 带上点击语义再点", addActionable())

        tapAdd()

        assertEquals(
            "三条按原文顺序入库，角色全部 = 输入框当前角色（默认 HER）",
            expectedComposeRoleEntries, vm.composer.messagesNow.map { it.role to it.content }
        )
        assertEquals("消费完草稿清空", "", vm.composer.draftTextNow)
        assertEquals("输入框同步清空", "", fieldText())
    }

    /** (b) 普通文本照旧单条：不被误拆，角色就是输入行那颗 */
    @Test
    fun plainTextStillTakesTheSingleAddPath() {
        val vm = makeVm()
        vm.setCurrentRole(ChatMessage.Role.ME)
        mountPanel(vm)

        rule.onNode(hasSetTextAction()).performTextInput("今晚一起吃饭")
        rule.waitForIdle()
        assertTrue(addActionable())

        tapAdd()

        assertEquals(
            "普通文本只落一条，原文一字不动、角色 = 输入框那颗",
            listOf(ChatMessage.Role.ME to "今晚一起吃饭"),
            vm.composer.messagesNow.map { it.role to it.content }
        )
        assertEquals("", vm.composer.draftTextNow)
    }

    /** (c) CRLF 变体：换行形态不同仍拆出同样三条，正文逐字不变、角色同样 = 输入框当前角色 */
    @Test
    fun crlfTranscriptVariantStillSplitsIntoTheSameMessages() {
        val crlf = transcript.replace("\n", "\r\n")
        val vm = makeVm()
        mountPanel(vm)

        rule.onNode(hasSetTextAction()).performTextInput(crlf)
        rule.waitForIdle()
        val raw = fieldText()
        assertTrue("CRLF 粘贴的换行也留在框里", raw.contains('\n') || raw.contains('\r'))
        assertEquals("换行形态归一后正文逐字不变", transcript, raw.replace("\r\n", "\n"))
        assertTrue(addActionable())

        tapAdd()

        assertEquals(expectedComposeRoleEntries, vm.composer.messagesNow.map { it.role to it.content })
        assertEquals("", vm.composer.draftTextNow)
    }

    /** (d) 单块转录：结构成立也拆出正文一条，块头/时间行被结构吃掉，角色 = 输入框当前角色 */
    @Test
    fun singleBlockTranscriptLandsAsOneEntryUnderTheComposeRole() {
        val vm = makeVm()
        vm.setCurrentRole(ChatMessage.Role.ME)
        mountPanel(vm)

        rule.onNode(hasSetTextAction()).performTextInput("小美\n2024-01-01 12:00\n今天好累")
        rule.waitForIdle()
        assertTrue(addActionable())

        tapAdd()

        assertEquals(
            listOf(ChatMessage.Role.ME to "今天好累"),
            vm.composer.messagesNow.map { it.role to it.content }
        )
        assertEquals("", vm.composer.draftTextNow)
    }
}
