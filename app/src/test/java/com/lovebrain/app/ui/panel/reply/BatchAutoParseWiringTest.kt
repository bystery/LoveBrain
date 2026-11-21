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
import com.lovebrain.app.ReplyCardLayout
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 自动拆分回落时这一扇浮层的预填接线（角色策略 B 的 UI 半边）：
 *
 * · **预填种得进去**——原文进粘贴框、已知归属直接选中、预览立刻在场（不必再点一次解析）；
 * · **逐条指定与提交闸门原样**——只认出一半昵称时提交键仍禁用，补指定后才放行；
 *   提交把整份归属表经 onRememberRoles 交回宿主（落盘后下一次 tap 零摩擦）；
 * · **退场动画不空窗**——visible 翻 false 后的 200ms 淡出里，刚提交的预览必须还在
 *   （旧 remember(visible) 写法会在淡出中途把状态整份重置 = 红）；重开才是干净一版；
 * · **陈旧解析失效原样**——预填出的预览也绑原文，原文一改预览即收回（审计 R1-1 同一条）；
 * · **草稿消费绑浮层来源**——回落浮层由草稿打开时，浮层里编辑过原文再提交，草稿照样同拍
 *   清掉（按"原文与草稿逐字相等"判会漏清，下一次 ➕ 把归属已落盘的同一份转录静默重复入库）；
 *   取消则草稿原样保留、不入库。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchAutoParseWiringTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** 冒号行格式样本：两条、两个昵称，预填解析该立刻出预览 */
    private val sample = "小美：在吗\n我：在的"

    private val fullRoles = mapOf(
        "小美" to ChatMessage.Role.HER,
        "我" to ChatMessage.Role.ME
    )

    private fun mountSheet(
        initialRaw: String? = null,
        initialNameRoles: Map<String, ChatMessage.Role> = emptyMap(),
        onSubmit: (List<BatchImportDraft>) -> Unit = {},
        onRememberRoles: ((Map<String, ChatMessage.Role>) -> Unit)? = null
    ) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                BatchImportSheet(
                    visible = true,
                    onDismiss = {},
                    onSubmit = onSubmit,
                    initialRaw = initialRaw,
                    initialNameRoles = initialNameRoles,
                    onRememberRoles = onRememberRoles
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun pickSpeakerCount(): Int =
        rule.onAllNodes(hasText(ctx.getString(R.string.batch_import_pick_speaker)))
            .fetchSemanticsNodes().size

    private fun submitLabel(count: Int) = ctx.getString(R.string.batch_import_submit, count)

    @Test
    fun prefillSeedsRawKnownRolesAndAnImmediatePreview() {
        var submitted: List<BatchImportDraft>? = null
        var remembered: Map<String, ChatMessage.Role>? = null
        mountSheet(
            initialRaw = sample,
            initialNameRoles = fullRoles,
            onSubmit = { submitted = it },
            onRememberRoles = { remembered = it }
        )

        assertEquals(
            "预填原文整份进粘贴框",
            sample,
            rule.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        assertEquals("预填该直接出预览，不必用户再点一次解析", 1, pickSpeakerCount())
        assertEquals(
            "已知归属随预填直接选中（两颗 chip 各就各位）",
            2, rule.onAllNodes(isSelected()).fetchSemanticsNodes().size
        )
        rule.onNode(hasText(ROLE_LABEL_HER) and isSelected()).assertExists()
        rule.onNode(hasText(ROLE_LABEL_ME) and isSelected()).assertExists()

        rule.onNodeWithText(submitLabel(2)).assertIsEnabled()
        rule.onNodeWithText(submitLabel(2)).performClick()
        rule.mainClock.advanceTimeBy(16L)

        assertEquals(
            "提交的草稿角色按归属表、顺序按转录",
            listOf(BatchImportDraft(ChatMessage.Role.HER, "在吗"), BatchImportDraft(ChatMessage.Role.ME, "在的")),
            submitted
        )
        assertEquals("提交同拍把整份归属表交回宿主落盘", fullRoles, remembered)
    }

    @Test
    fun partialPrefillKeepsSubmitGatedUntilEverySpeakerIsAssigned() {
        var submitted: List<BatchImportDraft>? = null
        var remembered: Map<String, ChatMessage.Role>? = null
        mountSheet(
            initialRaw = sample,
            initialNameRoles = mapOf("小美" to ChatMessage.Role.HER),
            onSubmit = { submitted = it },
            onRememberRoles = { remembered = it }
        )

        assertEquals("只种进认过的那一颗", 1, rule.onAllNodes(isSelected()).fetchSemanticsNodes().size)
        rule.onNodeWithText(submitLabel(2)).assertIsNotEnabled()

        // 「我」那一行的 ME chip：两颗 ME chip 里排第二的那颗（第一颗属于小美那行）
        rule.onAllNodes(hasText(ROLE_LABEL_ME) and hasClickAction())[1].performClick()
        rule.mainClock.advanceTimeBy(16L)

        rule.onNodeWithText(submitLabel(2)).assertIsEnabled()
        rule.onNodeWithText(submitLabel(2)).performClick()
        rule.mainClock.advanceTimeBy(16L)

        assertEquals(
            listOf(BatchImportDraft(ChatMessage.Role.HER, "在吗"), BatchImportDraft(ChatMessage.Role.ME, "在的")),
            submitted
        )
        assertEquals("补指定的昵称与预填的已知归属一起交回（下一次 tap 零摩擦）", fullRoles, remembered)
    }

    @Test
    fun exitAnimationKeepsThePreviewAndOnlyReopenResetsIt() {
        rule.mainClock.autoAdvance = false
        var visible by mutableStateOf(true)
        var prefill: String? by mutableStateOf(sample)
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                BatchImportSheet(
                    visible = visible,
                    onDismiss = {},
                    onSubmit = {},
                    initialRaw = prefill,
                    initialNameRoles = fullRoles
                )
            }
        }
        rule.mainClock.advanceTimeBy(300L)
        assertEquals("入场后预览在场", 1, pickSpeakerCount())

        visible = false
        rule.mainClock.advanceTimeBy(100L)   // 200ms 退场走到一半
        assertEquals(
            "退场动画中途预览不许凭空清空（remember(visible) 旧写法在这一帧重置 = 红）",
            1, pickSpeakerCount()
        )
        rule.onNodeWithText("在吗").assertExists()

        rule.mainClock.advanceTimeBy(250L)   // 退场播完，树摘除
        assertEquals(0, pickSpeakerCount())

        rule.mainClock.autoAdvance = true
        prefill = null
        visible = true
        rule.waitForIdle()
        assertEquals("重开（无预填）是干净一版：不带上一次的预览", 0, pickSpeakerCount())
        assertEquals(
            "重开粘贴框清空",
            "",
            rule.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        rule.onNodeWithText(ctx.getString(R.string.batch_import_parse)).assertExists()
    }

    @Test
    fun editingThePrefilledRawStillInvalidatesTheStaleParse() {
        mountSheet(initialRaw = sample, initialNameRoles = fullRoles)
        assertEquals(1, pickSpeakerCount())

        rule.onNode(hasSetTextAction()).performTextInput("（补一句后来才想起的）")
        rule.mainClock.advanceTimeBy(16L)

        assertEquals("预填出的预览也绑原文：原文一改即收回（审计 R1-1 同一条）", 0, pickSpeakerCount())
        rule.onNodeWithText(ctx.getString(R.string.batch_import_parse)).assertExists()
        assertEquals(
            0, rule.onAllNodes(hasText(ctx.getString(R.string.batch_import_reparse))).fetchSemanticsNodes().size
        )

        rule.onNodeWithText(ctx.getString(R.string.batch_import_parse)).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("重新解析后预览恢复", 1, pickSpeakerCount())
    }

    @Test
    fun openingWithoutPrefillStaysTheCleanManualParseSheet() {
        // 不传 initialRaw / initialNameRoles / onRememberRoles：旧调用点（长按入口）行为一字不变
        mountSheet()

        rule.onNodeWithText(ctx.getString(R.string.batch_import_paste_placeholder)).assertExists()
        assertEquals("长按开的干净一版没有预览", 0, pickSpeakerCount())
        rule.onNodeWithText(ctx.getString(R.string.batch_import_parse)).assertIsNotEnabled()
    }

    private fun pasteText(): String =
        rule.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /**
     * 预填原文在 visible 不动时换了：整扇浮层必须重新播种（新原文、新预览、新归属）。
     * 旧写法 LaunchedEffect(visible) 的 key 没变就不重跑——粘贴框与预览停在上一次的转录 = 红。
     */
    @Test
    fun changingThePrefillWhileVisibleReseedsRawRolesAndPreview() {
        var prefill: String? by mutableStateOf(sample)
        var roles by mutableStateOf(fullRoles)
        var submitted: List<BatchImportDraft>? = null
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                BatchImportSheet(
                    visible = true,
                    onDismiss = {},
                    onSubmit = { submitted = it },
                    initialRaw = prefill,
                    initialNameRoles = roles
                )
            }
        }
        rule.mainClock.advanceTimeBy(300L)
        assertEquals("第一份预填的预览在场", 1, pickSpeakerCount())
        rule.onNodeWithText(submitLabel(2)).assertIsEnabled()

        // visible 保持 true，只换预填原文与归属表（宿主在浮层开着时递来新一份转录）
        prefill = "小美：在忙\n我：稍等"
        roles = mapOf("小美" to ChatMessage.Role.HER)
        rule.mainClock.advanceTimeBy(16L)

        assertEquals("粘贴框必须换成新预填原文", "小美：在忙\n我：稍等", pasteText())
        rule.onNodeWithText("在忙").assertExists()
        rule.onNodeWithText("稍等").assertExists()
        assertEquals("旧转录的条目不许残留", 0, rule.onAllNodes(hasText("在吗")).fetchSemanticsNodes().size)
        assertEquals("归属随新预填重新种：只认得小美这一颗", 1, rule.onAllNodes(isSelected()).fetchSemanticsNodes().size)
        rule.onNodeWithText(submitLabel(2)).assertIsNotEnabled()

        // 补指定「我」后放行，提交的是新转录（不是上一场的残稿）
        rule.onAllNodes(hasText(ROLE_LABEL_ME) and hasClickAction())[1].performClick()
        rule.mainClock.advanceTimeBy(16L)
        rule.onNodeWithText(submitLabel(2)).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            listOf(BatchImportDraft(ChatMessage.Role.HER, "在忙"), BatchImportDraft(ChatMessage.Role.ME, "稍等")),
            submitted
        )
    }

    /**
     * 反向守卫：预填原文没变时，宿主刷新归属表（内容相同的新实例）触发的重组不许把用户的编辑冲掉。
     * 重置只认上升沿与预填原文变化两条触发，不是"每次重组都重播"。
     */
    @Test
    fun recompositionWithoutASeedChangeKeepsUserEdits() {
        var roles by mutableStateOf(fullRoles)
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                BatchImportSheet(
                    visible = true,
                    onDismiss = {},
                    onSubmit = {},
                    initialRaw = sample,
                    initialNameRoles = roles
                )
            }
        }
        rule.mainClock.advanceTimeBy(300L)

        val note = "（补一句后来才想起的）"
        rule.onNode(hasSetTextAction()).performTextInput(note)
        rule.mainClock.advanceTimeBy(16L)
        // 预填后光标停在 0，performTextInput 从光标处插入：插入位置由输入框定，
        // 这里只钉原文与补句都落进去了，不钉位置；重组后逐字相等才是判别式
        val edited = pasteText()
        assertTrue("用户编辑必须真的落进粘贴框", edited.contains(sample) && edited.contains(note))

        roles = HashMap(fullRoles)
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("预填未变时重组不许重置用户编辑", edited, pasteText())
    }

    // ── 草稿消费判据：整屏挂载，走生产真实的 open/close/onSubmit 接线 ──────────────
    // 草稿清不清是宿主（面板 onSubmit）的决策，浮层单挂量不到；这一格照
    // BatchSheetLifecycleTest 那台整屏夹具挂 VM 的 flow 桩，面板只当接线测。

    private val draft = MutableStateFlow("")
    private val panelMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val knownRoles = mutableMapOf<String, ChatMessage.Role>()
    private var autoCalls = 0
    private var importCalls = 0

    private fun appendDrafts(drafts: List<BatchImportDraft>) {
        importCalls++
        panelMessages.value = panelMessages.value + drafts.map { ChatMessage(role = it.role, content = it.content) }
    }

    private fun fakeIntents(): IntentController = mockk<IntentController>(relaxed = true) {
        every { config } returns MutableStateFlow(IntentConfig())
        every { showEditor } returns MutableStateFlow(false)
        every { isSaving } returns MutableStateFlow(false)
    }

    /**
     * VM 的 flow 逐条桩住（照 BatchSheetLifecycleTest / PanelHostSemanticsTest 那台夹具），
     * 批量那几颗给**合同级替身**：addDraftAuto 按 `autoSplit` 三态（NotTranscript 单条 /
     * NeedsReview 回落 / Ready 按归属表整批）模拟生产决策，importBatchMessages 的入库
     * 照生产语义走；这一格量的是面板真实的 open/close/onSubmit 接线，不是测试里另拼一份。
     */
    private fun fakeVm(): LoveBrainViewModel {
        val vm = mockk<LoveBrainViewModel>(relaxed = true)
        val composer = mockk<ComposerStore>(relaxed = true)
        every { vm.composer } returns composer
        every { composer.panelMode } returns MutableStateFlow(0)
        // 面板挂载即读这两颗盘上读数：显式桩住，免得 relaxed 的缺省值让底色/摆法吃意外
        every { vm.panelBackdropOpacityPercent } returns 100
        every { vm.replyCardLayout } returns ReplyCardLayout.DEFAULT
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
        every { composer.messages } returns panelMessages
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
                    panelMessages.value = panelMessages.value + ChatMessage(role = secondArg(), content = text)
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
        panelMessages.value = emptyList()
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

    /** 输入行那颗 ➕：轻点 = 提交草稿（认不全角色的转录回落浮层），长按 = 干净一版 */
    private fun addButton() = rule.onNodeWithContentDescription("添加")

    /** 浮层粘贴框（整屏挂载时输入行那颗编辑框也在树上，靠测试标签单独认它） */
    private fun sheetPasteText(): String =
        rule.onNode(hasTestTag(BATCH_PASTE_TEST_TAG)).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

    /**
     * 落回浮层后用户编辑了原文（与原草稿不再逐字相等）：提交仍要清草稿。
     *
     * 旧判据按"浮层原文 == 输入草稿"清草稿，编辑过就漏清——草稿留着，归属已落盘，
     * 下一次 ➕ 自动拆分认全角色，把同一份转录第二次入库。这里连同"再点 ➕ 不再入库"
     * 一起钉住：草稿消费绑的是"这扇浮层由草稿打开"，不是文本相等。
     */
    @Test
    fun submittingPrefilledSheetAfterEditingRawStillConsumesTheDraft() {
        mountPanel()
        draft.value = sample
        rule.waitForIdle()

        // 轻点 ➕：昵称没认过角色 → 回落浮层，带着草稿原文开
        addButton().performClick()
        rule.waitForIdle()
        assertEquals("回落浮层带着草稿原文开", sample, sheetPasteText())
        assertEquals(1, pickSpeakerCount())

        // 用户在浮层里编辑原文（末尾补一行）：与草稿不再逐字相等，但仍可解析为 ≥2 条
        val edited = "小美：在吗\n我：在的\n小美：晚安"
        rule.onNode(hasTestTag(BATCH_PASTE_TEST_TAG)).performTextReplacement(edited)
        rule.waitForIdle()
        assertEquals("编辑真的落进粘贴框", edited, sheetPasteText())
        assertEquals("原文一改旧预览即收回", 0, pickSpeakerCount())

        // 重新解析 → 逐条指定昵称归属 → 提交
        rule.onNodeWithText(ctx.getString(R.string.batch_import_parse)).performClick()
        rule.waitForIdle()
        assertEquals(1, pickSpeakerCount())
        // 只认浮层预览行里的 chip（输入行的她/我角色 chip 同文，靠测试标签圈开）
        rule.onAllNodes(hasText(ROLE_LABEL_HER) and hasClickAction() and hasAnyAncestor(hasTestTag(BATCH_ENTRY_CHIPS_TAG)))[0].performClick()
        rule.onAllNodes(hasText(ROLE_LABEL_ME) and hasClickAction() and hasAnyAncestor(hasTestTag(BATCH_ENTRY_CHIPS_TAG)))[1].performClick()
        rule.waitForIdle()
        // 三条预览把卡片撑进滚动区：先滚到提交键再点，否则坐标落在遮罩上（=点外关闭）
        rule.onNodeWithText(submitLabel(3)).performScrollTo().assertIsEnabled()
        rule.onNodeWithText(submitLabel(3)).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(
            "入库的是编辑后的转录，角色按指定、顺序按原文",
            listOf(
                ChatMessage.Role.HER to "在吗",
                ChatMessage.Role.ME to "在的",
                ChatMessage.Role.HER to "晚安"
            ),
            panelMessages.value.map { it.role to it.content }
        )
        assertEquals("提交一次入库一批", 1, importCalls)
        assertEquals("浮层由草稿打开：编辑过原文，提交也视为消费掉草稿", "", draft.value)
        rule.mainClock.advanceTimeBy(300L)   // 退场播完、遮罩摘除，➕ 才点得到

        // 再点 ➕：草稿已清，不许把同一份转录再处理一遍
        addButton().performClick()
        rule.waitForIdle()
        assertEquals("再点 ➕ 消息数不变", 3, panelMessages.value.size)
        assertEquals("再点 ➕ 不再触发自动拆分", 1, autoCalls)
    }

    /** 取消那一扇回落浮层：它没消费草稿，草稿原样保留；什么都没入库。 */
    @Test
    fun cancellingPrefilledSheetKeepsTheDraft() {
        mountPanel()
        draft.value = sample
        rule.waitForIdle()

        addButton().performClick()
        rule.waitForIdle()
        assertEquals("开场是同一扇回落浮层", sample, sheetPasteText())
        assertEquals(1, pickSpeakerCount())

        rule.onNodeWithText(ctx.getString(R.string.batch_import_cancel)).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300L)   // 退场播完、遮罩摘除

        assertEquals("取消不清草稿", sample, draft.value)
        assertEquals("取消不入库", 0, panelMessages.value.size)
        assertEquals("取消不触发导入", 0, importCalls)
    }
}