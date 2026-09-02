package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.model.ChatMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 *  收敛轮 #1 F-1 回归（计 1 条用例，三幕）：
 * reorderMessages 拖拽重排必须同步修正 editingIndex——与  同源口径（修正下沉 VM）。
 *
 * UI 语义锚点（MessageList onDrag :171-174）：拖拽每次跨越一条即发一次
 * onReorder(from, target)（相邻交换，draggedIndex 随迁），故幕内按相邻交换序列复现手势。
 *
 * 幕①：编辑 B(k)，把 B 之前的消息拖到 B 之后——removeAt/add 真源推演：
 *       被拖项自 B 之前移除后插入 B 之后，B 的真实位置前移一位 → editingIndex = k-1。
 *       （修复前 editingIndex 纹丝不动仍为 k → 高亮与草稿指向错位行，保存写坏另一条 = F-1 数据损坏面）
 * 幕②：拖动 B 本身到 to → editingIndex 逐步跟随到 to（from == editing 分支）。
 * 幕③：反向跨越——B 之后的消息拖到 B 之前，编辑位被推后一位 → editingIndex = k+1。
 *
 * 纯 JVM 构造沿用 LoveBrainViewModelR2RegressionTest 先例：7 依赖全 mock + setMain 接管。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoveBrainViewModelR5RegressionTest {

    private lateinit var prefs: SecurePrefs

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    /** 构造 LoveBrainViewModel：init 读取面显式桩（同 R2RegressionTest 先例） */
    private fun newViewModel(): LoveBrainViewModel {
        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        // ：init 新增今日花费读取面（relaxed 默认返 Object 会 CCE，显式桩 null）
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = mockk(relaxed = true),
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk(relaxed = true)
        )
    }

    /** F-1：reorder 修正 editingIndex——三幕覆盖跨越 -1 / 自身跟随 / 反向跨越 +1 */
    @Test
    fun reorder_corrects_editing_index() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        // ── 幕①：编辑 B(k=1)，把 B 之前的 A 拖到 B 之后（两次相邻交换，首跳命中 to==editing 边界） ──
        vm.addMessage(ChatMessage.Role.HER, "A")
        vm.addMessage(ChatMessage.Role.ME, "B")
        vm.addMessage(ChatMessage.Role.HER, "C")
        vm.setEditingIndex(1)
        vm.setDraft("editing-B")

        vm.reorderMessages(0, 1) // A 跨过 B：[A,B,C] → [B,A,C]，B 落 0 → 修正 -1
        assertEquals(0, vm.editingIndex.value)
        vm.reorderMessages(1, 2) // A 继续后移：[B,A,C] → [B,C,A]，未再跨越 → 不动
        assertEquals(0, vm.editingIndex.value)
        assertEquals(listOf("B", "C", "A"), vm.messages.value.map { it.content })
        assertEquals("B", vm.messages.value[vm.editingIndex.value].content)

        // ── 幕②：拖动 B 本身到末尾 → editingIndex 逐步跟随（from == editing 分支） ──
        // 接幕①终态 [B,C,A]，B 在 0
        vm.setDraft("editing-B-2")
        vm.reorderMessages(0, 1) // B 与 C 交换：[B,C,A] → [C,B,A]
        assertEquals(1, vm.editingIndex.value)
        vm.reorderMessages(1, 2) // B 与 A 交换：[C,B,A] → [C,A,B]
        assertEquals(2, vm.editingIndex.value)
        assertEquals(listOf("C", "A", "B"), vm.messages.value.map { it.content })
        assertEquals("B", vm.messages.value[vm.editingIndex.value].content)

        // ── 幕③：反向跨越 +1——干净列表 [X,B,A]，编辑 B(k=1)，B 之后的 A 拖到最前 ──
        val vm3 = newViewModel()
        advanceUntilIdle()
        vm3.addMessage(ChatMessage.Role.HER, "X")
        vm3.addMessage(ChatMessage.Role.ME, "B")
        vm3.addMessage(ChatMessage.Role.HER, "A")
        vm3.setEditingIndex(1)
        vm3.setDraft("editing-B-3")

        vm3.reorderMessages(2, 1) // A 跨过 B：[X,B,A] → [X,A,B]，B 落 2 → 修正 +1（to==editing 边界）
        assertEquals(2, vm3.editingIndex.value)
        vm3.reorderMessages(1, 0) // A 继续前移：[X,A,B] → [A,X,B]，未再跨越 → 不动
        assertEquals(2, vm3.editingIndex.value)
        assertEquals(listOf("A", "X", "B"), vm3.messages.value.map { it.content })
        assertEquals("B", vm3.messages.value[vm3.editingIndex.value].content)
    }
}
