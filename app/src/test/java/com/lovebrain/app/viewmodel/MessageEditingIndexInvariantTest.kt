package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 编辑位与消息列表那份不变式的**穷举**检查（§2.2 "多个 MutableStateFlow 各写各的"在这一族的真代价）。
 *
 * `_messages` 与 `_editingIndex` 是两个 flow：`editingIndex` 是"列表里第几条正在编辑"，
 * 它对列表的索引，因此任何改动列表的操作都必须同帧修正它——否则会出现在编辑第 2 条、
 * 点一下却改到第 3 条这种用户看得见、机器不报错的事故。
 * 仓库里这条规则被写在三处（`removeMessageById`、`reorderMessages`、`commitReplyRound`），
 * 前两处是算术位移（`reorderMessages` 那段还带着边界反例注释），第三处是按身份重算。
 *
 * 这里不复制那份算术来"自证一致"（比对一个副本证明不了生产代码），
 * 而是把真实的 VM 方法在每个 (长度, 编辑位, from, to) 组合上跑一遍，
 * 只断言不变式：**操作之后 `messages[editingIndex]` 还是操作前那条消息**
 * （被删掉的情况必须是 `-1` 且草稿清空）。
 * 组合数同时断言不为 0——穷举型用例最怕循环条件写错后"零次执行全绿"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageEditingIndexInvariantTest {

    private var cases = 0

    private fun newVm(): LoveBrainViewModel {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { repo.getActive() } returns KnowledgeBase(name = "kb1", displayName = "她", active = true)
        coEvery { repo.listAll() } returns listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        coEvery { repo.readVector(any()) } returns emptyMap()
        coEvery { repo.getCorrectionsRevision(any()) } returns 0
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = repo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk(relaxed = true),
            operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            )
        )
    }

    @Before
    fun setUp() {
        cases = 0
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    /** 建一个 n 条消息、编辑位指到 editing 的 VM；返回操作前的 id 序列 */
    private fun seed(vm: LoveBrainViewModel, n: Int, editing: Int): List<String> {
        repeat(n) { vm.addMessage(ChatMessage.Role.HER, "第${it}条") }
        if (editing >= 0) vm.setEditingIndex(editing)
        return vm.messages.value.map { it.id }
    }

    private fun editedId(vm: LoveBrainViewModel, before: List<String>, editing: Int): String? =
        if (editing < 0) null else before[editing]

    /** 不变式：编辑位必须还指着那条消息；越界直接判失败 */
    private fun assertStillPointingAt(vm: LoveBrainViewModel, label: String, wantId: String?) {
        val now = vm.editingIndex.value
        val list = vm.messages.value
        if (wantId == null) {
            assertTrue("$label：没有编辑位时不该冒出索引，实到 $now", now < 0)
            return
        }
        assertTrue(
            "$label：编辑位越界（列表 ${list.size} 条，editingIndex=$now）",
            now in list.indices
        )
        assertEquals("$label：编辑位换了一条消息（用户以为在改这条，实际改了那条）", wantId, list[now].id)
    }

    @Test
    fun reorderNeverStrandsTheEditingIndex() = runTest {
        for (n in 1..4) {
            for (editing in -1 until n) {
                for (from in 0 until n) for (to in 0 until n) {
                    if (from == to) continue
                    val vm = newVm()
                    val before = seed(vm, n, editing)
                    advanceUntilIdle()
                    cases += 1

                    vm.reorderMessages(from, to)

                    assertStillPointingAt(vm, "n=$n editing=$editing 拖 $from→$to", editedId(vm, before, editing))
                }
            }
        }
        assertTrue("穷举只跑了 $cases 组，循环条件多半写错了", cases >= 40)
    }

    @Test
    fun removingMessagesKeepsTheEditingIndexOnTheSameMessage() = runTest {
        for (n in 1..4) {
            for (editing in -1 until n) {
                for (victim in 0 until n) {
                    val vm = newVm()
                    val before = seed(vm, n, editing)
                    advanceUntilIdle()
                    cases += 1

                    vm.removeMessageById(before[victim])

                    if (victim == editing) {
                        assertEquals("删掉正在编辑的那条应清编辑位", -1, vm.editingIndex.value)
                        assertEquals("清编辑位要连草稿一起清，否则下一次编辑继承残留",
                            "", vm.draftText.value)
                        assertEquals("删一条少一条", n - 1, vm.messages.value.size)
                    } else {
                        assertStillPointingAt(
                            vm, "n=$n editing=$editing 删第 $victim 条", editedId(vm, before, editing)
                        )
                    }
                }
            }
        }
        assertTrue("穷举只跑了 $cases 组，循环条件多半写错了", cases >= 20)
    }

    /** 没有编辑位时，两个操作都不该擅自造出一个编辑位 */
    @Test
    fun operationsWithoutAnEditingIndexDoNotInventOne() = runTest {
        val vm = newVm()
        seed(vm, 3, -1)
        advanceUntilIdle()
        cases += 1
        vm.reorderMessages(0, 2)
        assertTrue("无编辑位时重排不该冒出编辑位，实到 ${vm.editingIndex.value}", vm.editingIndex.value < 0)

        val vm2 = newVm()
        seed(vm2, 3, -1)
        advanceUntilIdle()
        cases += 1
        vm2.removeMessageById(vm2.messages.value.first().id)
        assertTrue("无编辑位时删除不该冒出编辑位，实到 ${vm2.editingIndex.value}", vm2.editingIndex.value < 0)
    }

    /**
     * 悬空编辑位（指着不存在的下标）在真实方法上要被清掉。
     *
     * 本轮换判据时顺带拧紧的一条：旧写法遇到悬空索引原样留着，而 `ReplyInput` 只看
     * `editingIndex >= 0` 就进编辑态——等于允许界面显示"正在编辑一条不存在的消息"。
     */
    @Test
    fun aDanglingEditingIndexIsClearedByRealOperations() = runTest {
        val vm = newVm()
        seed(vm, 3, 2)
        vm.setEditingIndex(7)
        advanceUntilIdle()
        cases += 1

        vm.reorderMessages(0, 1)

        assertEquals(
            "悬空索引该判成\"没有编辑位\"",
            -1, vm.editingIndex.value
        )
        assertTrue("列表本身不受影响", vm.messages.value.size == 3)
    }

    @Test
    fun theMatrixActuallyRan() {
        // 哨兵：上面三个用例各自也断言了，但 JUnit 会在本类里先跑这个字段为 0 的实例——
        // 真正防"零次执行"的是各用例末尾那句 cases >= N；这里只把计数留在报告里便于核对。
        assertEquals("本类每个用例用新实例，计数不跨用例累计", 0, cases)
    }
}
