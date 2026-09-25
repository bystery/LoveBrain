package com.lovebrain.app.ui.panel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * §6.4 :523 第五刀之前，先把"记录实际发送"这条**失败路径**量清楚。
 *
 * 起因是把 `showSentDialog` / `sentDialogSaving` 收进持有者时读到的那段接线：
 * 浮层的"保存中"是**靠观察 VM 状态的一次跳变**来解除的，于是——
 *
 * ① 枚举里有五种结果，那段接线只处理三种（`IDLE` / **`NO_KB`** 没有分支）。
 *    `NO_KB` 是**真会发生的**：`recordActualSentMessage` 在未激活知识库时就吐它
 *    （`LoveBrainViewModel` 里 `ctx.kbName ?: run { … NO_KB }`）。
 * ② `actualSentState` 是 `StateFlow`，**同一个值连续两次不再发射**。
 *    第一次失败（比如 IO_ERROR）之后浮层停在"保存中"，用户改两个字再确认一次、
 *    又失败——这一次没有任何跳变可观察，`saving` 永远解不开。
 *
 * 两个合起来的后果是同一个**死路**：`saving == true` 时浮层的取消与遮罩都是
 * `enabled = !saving`，用户既关不掉也退不出，只能杀掉悬浮窗。
 *
 * 这两格都是**先红后修**：判据不许写死枚举内容（②那格数的是真发射次数，
 * ①那格从 `ActualSentState.entries` 现算），否则加了新状态它照样绿。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordSentFailurePathTest {

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

    /** 一个"没有会话上下文"的 VM：`recordActualSentMessage` 必然走 IO_ERROR 那条早退分支 */
    private fun newViewModel(): LoveBrainViewModel {
        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-test", stage = "暧昧期")
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb", stage = ""))
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()

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

        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            PromptBuilder.ConfigValidationResult(0, 0, emptyList())
        every { promptBuilder.replyPromptAssetHash() } returns "reply-asset-hash"

        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk<TopicRecorder>(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk(relaxed = true),
            operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            )
        )
    }

    /**
     * 缺陷 ②：连续两次同样结果的失败，UI 那边**必须还能观察到第二次**。
     *
     * 判据不是"值等于 IO_ERROR"，而是"这一段接线赖以解除 saving 的那个东西
     * 真的又动了一次"——所以直接数收集到的发射序列里 IO_ERROR 出现几次。
     */
    @Test
    fun `a repeated identical failure is still observable to the panel`() {
        val vm = newViewModel()
        val seen = mutableListOf<LoveBrainViewModel.ActualSentState>()
        val job = kotlinx.coroutines.CoroutineScope(testDispatcher).launch {
            vm.actualSentState.collect { seen.add(it) }
        }

        vm.recordActualSentMessage("第一次尝试")
        vm.recordActualSentMessage("第二次尝试")

        job.cancel()
        val failures = seen.count { it == LoveBrainViewModel.ActualSentState.IO_ERROR }
        assertEquals(
            "两次尝试都失败，面板就该观察到两次失败信号；实到 $failures 次，" +
                "整段收集到的序列是 $seen —— 少于两次意味着第二次没有任何跳变，" +
                "浮层会永远停在「保存中」（取消与遮罩都挂在 enabled = !saving 上）",
            2, failures
        )
    }

    /**
     * 缺陷 ①：VM 可能吐出的**每一种**结果，面板那段接线都得有分支。
     *
     * 两处刻意的设计：
     * - 枚举内容**现算**（`ActualSentState.entries`），不在测试里抄一份名单——
     *   抄一份的话以后加第六种状态它照样绿，而那只脚正是为了拦这种情况。
     * - **先去注释再判**：这把尺读的是源码，留注释就等于允许
     *   "写一句 `// TODO: NO_KB 还没处理`"把闸糊过去——那正是复核报告点名的
     *   "注释式修复"。探针 Y1 就是拿一条注释去喂它，看它是否照样红。
     */
    @Test
    fun `the panel handles every outcome the view model can emit`() {
        val screen = File("src/main/java/com/lovebrain/app/ui/panel/LoveBrainPanelScreen.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/LoveBrainPanelScreen.kt")
        assertTrue("找不到 $screen——搬家了要同步改这条", screen.isFile)
        val code = withoutComments(screen.readText(Charsets.UTF_8))

        val unhandled = LoveBrainViewModel.ActualSentState.entries
            .map { it.name }
            .filterNot { code.contains("ActualSentState.$it") }
        assertTrue(
            "这些记录结果在面板里没有任何分支：$unhandled。" +
                "漏掉的那一种一旦真发生，「保存中」就解不开——" +
                "浮层的取消与遮罩都是 enabled = !saving，用户会被困在里面",
            unhandled.isEmpty()
        )
        // 反证：这把尺必须真看得见东西，而不是扫了个空集恒绿
        assertTrue(
            "枚举只数出 ${LoveBrainViewModel.ActualSentState.entries.size} 种结果，" +
                "少于 3 说明量具接错了类型",
            LoveBrainViewModel.ActualSentState.entries.size >= 3
        )
    }

    /** 去掉注释，只留代码——注释里写出那个枚举名不算处理了它 */
    private fun withoutComments(src: String): String = buildString {
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).let { if (it >= 0) it + 2 else src.length }
                    i = end
                }
                src.startsWith("//", i) -> {
                    i = src.indexOf('\n', i).let { if (it >= 0) it else src.length }
                }
                else -> { append(src[i]); i++ }
            }
        }
    }
}
