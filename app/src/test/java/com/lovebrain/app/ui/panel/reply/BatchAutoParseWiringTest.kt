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
import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
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
 * · **陈旧解析失效原样**——预填出的预览也绑原文，原文一改预览即收回（审计 R1-1 同一条）。
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
}