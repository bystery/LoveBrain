package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.feature.composer.BatchImportDraft
import com.lovebrain.app.feature.composer.BatchTranscriptParser
import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.feature.intent.IntentController
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.feature.profile.ProfileReview
import com.lovebrain.app.feature.roundcommit.ActualSentState
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.ui.panel.LoveBrainPanelScreen
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.UsageStats
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 批量导入浮层的**会话生命周期**与**宿主接线**守卫：
 *
 * · 重开的第一帧就是干净一版——上一场的 raw/预览/可提交键不许闪现在重开那一帧
 *   （重置住在组合期而不是 LaunchedEffect，正是为了消掉真机这一帧残稿）；
 * · 提交后退场那 200ms 里，刚提交的预览必须还在（下降沿不重置）；
 * · 回落预填 → 取消 → 长按开干净一版 → 粘贴同一份转录 → 提交 → 再点 ➕ **不许重复入库**：
 *   提交消费掉的就是草稿那份原文，同文即清草稿，与预填来源无关；
 * · 取消/关闭清掉预填，下一扇干净浮层不继承上一场的原文。
 *
 * 宿主那几格整屏挂载（VM 的 flow 桩住、批量决策给合同级替身），走的是生产真实的
 * open/close/onSubmit 接线，不是测试里另拼一份。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchSheetLifecycleTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private val sample = "小美：在吗\n我：在的"
    private val fullRoles = mapOf(
        "小美" to ChatMessage.Role.HER,
        "我" to ChatMessage.Role.ME
    )

    private fun pickSpeakerCount(): Int =
        rule.onAllNodes(hasText(ctx.getString(R.string.batch_import_pick_speaker)))
            .fetchSemanticsNodes().size

    private fun submitLabel(count: Int) = ctx.getString(R.string.batch_import_submit, count)

    /** 浮层粘贴框（整屏挂载时输入行那颗编辑框也在树上，靠测试标签单独认它） */
    private fun pasteText(): String =
        rule.onNode(hasTestTag(BATCH_PASTE_TEST_TAG)).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

    /**
     * autoAdvance=false 下整段跳时间不逐帧产帧：状态变更与动画都要在逐帧里走，
     * 退场尤其要在起播那一帧之后的帧里才播完摘树（同 ModalSheetExitAnimationTest 的 pump 口径）。
     */
    private fun pump(ms: Long) {
        repeat(((ms + 15L) / 16L).toInt()) { rule.mainClock.advanceTimeByFrame() }
    }

    // ── 浮层本体：重开第一帧 / 退场保留 ──────────────────────────────────────

    /**
     * 重开只推一帧就断言干净：重置住在组合期（而非 effect），上升沿那一帧就是新会话；
     * 旧 LaunchedEffect 写法在这一帧还画着上一场残稿 = 红。退场段必须逐帧推——
     * 整段跳时间只落一帧，退场刚起播就读，读到的必然是还在树上的旧预览。
     */
    @Test
    fun reopenShowsCleanStateOnTheFirstComposedFrame() {
        rule.mainClock.autoAdvance = false
        var visible by mutableStateOf(true)
        var prefill: String? by mutableStateOf(sample)
        val rawReports = mutableListOf<String>()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                BatchImportSheet(
                    visible = visible,
                    onDismiss = {},
                    onSubmit = {},
                    initialRaw = prefill,
                    initialNameRoles = fullRoles,
                    onRawChange = { rawReports += it }
                )
            }
        }
        pump(300L)
        assertEquals("第一场预填的预览在场", 1, pickSpeakerCount())
        rule.onNodeWithText(submitLabel(2)).assertIsEnabled()

        visible = false
        pump(48L)    // 退场中段（200ms 淡出半路）：预览仍须在树里
        assertEquals("退场中途预览不许凭空清空", 1, pickSpeakerCount())
        pump(320L)   // 逐帧播完摘树（照 ModalSheetExitAnimationTest 的 48+320 口径，中段读驱动收尾重组）
        assertEquals(0, pickSpeakerCount())

        prefill = null
        visible = true
        // 重开：逐帧推进到浮层内容真正上树的第一帧（AnimatedVisibility 的内容在起播后一两帧才组合），
        // 一上树立刻断言——旧 LaunchedEffect 重置写法在这一帧仍画着上一场残稿 = 红。
        var composed = false
        repeat(3) {
            if (!composed) {
                rule.mainClock.advanceTimeByFrame()
                composed = rule.onAllNodes(hasTestTag(BATCH_PASTE_TEST_TAG))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        assertTrue("重开三帧内浮层内容必须上树（否则后面几条断言量不到东西）", composed)

        assertEquals("重开第一帧粘贴框为空", "", pasteText())
        assertEquals("重开第一帧无预览", 0, pickSpeakerCount())
        assertEquals("重开第一帧无残留条目", 0, rule.onAllNodes(hasText("在吗")).fetchSemanticsNodes().size)
        rule.onNodeWithText(ctx.getString(R.string.batch_import_parse)).assertIsNotEnabled()
        rule.onNodeWithText(submitLabel(0)).assertIsNotEnabled()
        assertEquals("播种当帧即把干净原文回报宿主", "", rawReports.last())
    }

    /** 提交成功宿主即关浮层：退场走到一半，刚提交的那份预览不许凭空清空。 */
    @Test
    fun exitFadeAfterSubmitKeepsTheJustSubmittedPreview() {
        rule.mainClock.autoAdvance = false
        var visible by mutableStateOf(true)
        var submitted: List<BatchImportDraft>? = null
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                BatchImportSheet(
                    visible = visible,
                    onDismiss = {},
                    onSubmit = { submitted = it; visible = false },
                    initialRaw = sample,
                    initialNameRoles = fullRoles
                )
            }
        }
        rule.mainClock.advanceTimeBy(300L)
        rule.onNodeWithText(submitLabel(2)).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertNotNull("提交真的发生了", submitted)

        rule.mainClock.advanceTimeBy(100L)   // 200ms 退场走到一半
        assertEquals("退场中途预览不许清空", 1, pickSpeakerCount())
        rule.onNodeWithText("在吗").assertExists()
        assertEquals("退场中途原文也不许清空", sample, pasteText())

        rule.mainClock.advanceTimeBy(250L)   // 退场播完
        assertEquals(0, pickSpeakerCount())
    }

    // ── 宿主接线：整屏挂载 ──────────────────────────────────────────────────

    private val draft = MutableStateFlow("")
    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val knownRoles = mutableMapOf<String, ChatMessage.Role>()
    private var autoCalls = 0
    private var importCalls = 0

    private fun appendDrafts(drafts: List<BatchImportDraft>) {
        importCalls++
        messages.value = messages.value + drafts.map { ChatMessage(role = it.role, content = it.content) }
    }

    private fun fakeIntents(): IntentController = mockk<IntentController>(relaxed = true) {
        every { config } returns MutableStateFlow(IntentConfig())
        every { showEditor } returns MutableStateFlow(false)
        every { isSaving } returns MutableStateFlow(false)
    }

    /**
     * VM 的 flow 逐条桩住（照 PanelHostSemanticsTest 那台夹具），批量那几颗给**合同级替身**：
     * addDraftAuto 按 `autoSplit` 三态（NotTranscript 单条 / NeedsReview 回落 / Ready 按归属表
     * 整批）模拟生产决策，importBatchMessages 的入库照生产语义走，
     * 真逻辑另有 VM 侧守卫量；这一格量的是面板真实的 open/close/onSubmit 接线。
     */
    private fun fakeVm(): LoveBrainViewModel {
        val vm = mockk<LoveBrainViewModel>(relaxed = true)
        val composer = mockk<ComposerStore>(relaxed = true)
        every { vm.composer } returns composer
        every { composer.panelMode } returns MutableStateFlow(0)
        every { vm.activeKb } returns MutableStateFlow(null)
        every { vm.actualSentState } returns MutableStateFlow(ActualSentState.IDLE)
        every { vm.composerMode } returns MutableStateFlow(ComposerMode.REPLY)
        every { composer.counselingDraft } returns MutableStateFlow("")
        every { vm.counselingError } returns MutableStateFlow(null)
        every { vm.counselingResult } returns MutableStateFlow(null)
        every { vm.counselingStreaming } returns MutableStateFlow("")
        every { vm.currentNotice } returns MutableStateFlow<NoticeBoard.Notice?>(null)
        every { composer.currentRole } returns MutableStateFlow(ChatMessage.Role.HER)
        every { composer.inputKind } returns MutableStateFlow(ComposerInputKind.HER)
        every { vm.currentVector } returns MutableStateFlow(emptyMap())
        every { composer.draftText } returns draft
        every { composer.editingIndex } returns MutableStateFlow(-1)
        every { vm.feedbacks } returns MutableStateFlow(emptyMap())
        every { vm.generationRoundId } returns MutableStateFlow(1)
        every { composer.ideaComposeMode } returns MutableStateFlow(false)
        every { vm.inputChanged } returns MutableStateFlow(false)
        every { vm.intents } returns fakeIntents()
        every { vm.isCounseling } returns MutableStateFlow(false)
        every { vm.isGenerating } returns MutableStateFlow(false)
        every { vm.isGeneratingCore } returns MutableStateFlow(false)
        every { vm.isProactive } returns MutableStateFlow(false)
        every { vm.kbNotice } returns MutableStateFlow(null)
        every { composer.messages } returns messages
        every { vm.onlyThisRound } returns MutableStateFlow(false)
        every { vm.panelWarning } returns MutableStateFlow(null)
        every { vm.proactiveError } returns MutableStateFlow(null)
        every { vm.proactiveOptions } returns MutableStateFlow(emptyList())
        every { vm.profileRegenerating } returns MutableStateFlow(false)
        every { vm.profileReview } returns MutableStateFlow(ProfileReview())
        every { vm.providerReady } returns MutableStateFlow(true)
        every { vm.result } returns MutableStateFlow(null)
        every { vm.resultMode } returns MutableStateFlow(ResultMode.REPLY)
        every { vm.rewriteStates } returns MutableStateFlow(emptyMap())
        every { vm.stageSuggestion } returns MutableStateFlow(null)
        every { vm.streamingCoreText } returns MutableStateFlow("")
        every { vm.streamingSchemes } returns MutableStateFlow(emptyList())
        every { vm.usageStats } returns MutableStateFlow(UsageStats())
        every { vm.vectorDelta } returns MutableStateFlow(emptyMap())
        every { vm.vectorUpdate } returns MutableStateFlow(null)

        every { vm.setDraft(any()) } answers { draft.value = firstArg() }
        every { vm.batchImportKnownRoles } answers { knownRoles.toMap() }
        every { vm.rememberBatchImportNameRoles(any()) } answers { knownRoles.putAll(firstArg()) }
        every { vm.importBatchMessages(any()) } answers { appendDrafts(firstArg()) }
        every { vm.addDraftAuto(any(), any()) } answers {
            autoCalls++
            val text = firstArg<String>()
            when (val split = BatchTranscriptParser.autoSplit(text)) {
                BatchTranscriptParser.AutoSplit.NotTranscript -> {
                    messages.value = messages.value + ChatMessage(role = secondArg(), content = text)
                    true
                }
                is BatchTranscriptParser.AutoSplit.NeedsReview -> false
                is BatchTranscriptParser.AutoSplit.Ready -> {
                    val drafts = split.entries.mapNotNull { e ->
                        val name = e.speakerName ?: return@mapNotNull null
                        knownRoles[name]?.let { BatchImportDraft(it, e.content) }
                    }
                    if (drafts.size == split.entries.size) {
                        appendDrafts(drafts)
                        true
                    } else {
                        false
                    }
                }
            }
        }
        return vm
    }

    private fun mountPanel() {
        draft.value = ""
        messages.value = emptyList()
        knownRoles.clear()
        autoCalls = 0
        importCalls = 0
        rule.setContent {
            UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = fakeVm(),
                        onInputFocusChange = { _, _ -> },
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

    private fun addButton() = rule.onNodeWithContentDescription("添加")

    /**
     * 重复入库的原场景：回落预填 T → 取消（草稿留着 T）→ 长按开干净一版 → 粘贴 T → 提交
     * （归属落盘）→ 再点 ➕。提交消费掉的就是草稿那份原文，必须同拍清草稿；不清的话
     * 再点 ➕ 时自动拆分已认全角色，会把 T 第二次入库 = 红。
     */
    @Test
    fun cancelFallbackThenLongPressPasteSubmitDoesNotDuplicateOnNextTap() {
        mountPanel()
        draft.value = sample
        rule.waitForIdle()

        // 轻点 ➕：昵称没认过角色 → 回落预填浮层
        addButton().performClick()
        rule.waitForIdle()
        assertEquals("回落浮层带着原文开", sample, pasteText())
        assertEquals(1, pickSpeakerCount())

        // 取消：草稿不丢，预填随关闭清掉
        rule.onNodeWithText(ctx.getString(R.string.batch_import_cancel)).performClick()
        rule.waitForIdle()
        assertEquals("取消不清草稿", sample, draft.value)
        rule.mainClock.advanceTimeBy(300L)

        // 长按 ➕：干净一版，不继承上一场预填
        addButton().performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals("干净一版粘贴框为空", "", pasteText())
        assertEquals("干净一版无预览", 0, pickSpeakerCount())

        // 粘贴同一份转录 → 解析 → 逐条指定 → 提交
        rule.onNode(hasTestTag(BATCH_PASTE_TEST_TAG)).performTextInput(sample)
        rule.waitForIdle()
        rule.onNodeWithText(ctx.getString(R.string.batch_import_parse)).performClick()
        rule.waitForIdle()
        // 只认浮层预览行里的 chip（输入行的她/我角色 chip 同文，靠测试标签圈开）
        rule.onAllNodes(hasText(ROLE_LABEL_HER) and hasClickAction() and hasAnyAncestor(hasTestTag(BATCH_ENTRY_CHIPS_TAG)))[0].performClick()
        rule.onAllNodes(hasText(ROLE_LABEL_ME) and hasClickAction() and hasAnyAncestor(hasTestTag(BATCH_ENTRY_CHIPS_TAG)))[1].performClick()
        rule.waitForIdle()
        rule.onNodeWithText(submitLabel(2)).performClick()
        rule.waitForIdle()

        assertEquals("提交一次入库两条", 2, messages.value.size)
        assertEquals("提交一次", 1, importCalls)
        assertEquals("提交消费掉草稿：同文即清，与预填来源无关", "", draft.value)
        assertEquals("归属落盘", fullRoles, knownRoles)
        rule.mainClock.advanceTimeBy(300L)

        // 再点 ➕：草稿已空，不许把同一份转录第二次入库
        addButton().performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals("再点 ➕ 消息数不变", 2, messages.value.size)
        assertEquals("再点 ➕ 不再触发自动拆分", 1, autoCalls)
    }

    /** 关闭/取消清掉预填：下一扇长按开的干净浮层不继承上一场的原文与预览。 */
    @Test
    fun afterDismissTheNextCleanOpenInheritsNoStalePrefill() {
        mountPanel()
        draft.value = sample
        rule.waitForIdle()

        addButton().performClick()
        rule.waitForIdle()
        assertEquals(sample, pasteText())

        rule.onNodeWithText(ctx.getString(R.string.batch_import_cancel)).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300L)   // 退场播完、遮罩摘除，➕ 才点得到
        // 长按重开：上升沿重置 + 预填已随关闭清掉，看到的仍是干净一版
        addButton().performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals("重开不继承旧预填原文", "", pasteText())
        assertEquals("重开不继承旧预览", 0, pickSpeakerCount())
        assertEquals("取消/重开都不动草稿", sample, draft.value)
    }
}