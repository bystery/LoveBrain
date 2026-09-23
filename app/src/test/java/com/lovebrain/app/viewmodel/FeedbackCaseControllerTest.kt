package com.lovebrain.app.viewmodel

import com.lovebrain.app.data.FeedbackCaseRepository
import com.lovebrain.app.model.DialogueSnapshotEntry
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.model.SchemeFeedback
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `FeedbackCaseController` 的行为基线（从 `LoveBrainViewModel` 拆出的那一块）。
 *
 * 钉住四件报告点过的事：
 * 1. 案例在点击当刻**同步**构造并暴露——面板不需要异步查全库猜最后一条；
 * 2. 取消踩（第二次点踩）不建案例，也不能把上一次的案例留在屏上；
 * 3. 落盘 IO 失败只记日志，但协程取消必须原样上抛，不能被吞成"保存失败"；
 * 4. 案例里的 promptVersion 是调用方冻进来的真实资产指纹，不是写死的版本字符串。
 *
 * 控制器自己不 launch 协程（异步唯一 owner 见 SingleOwnerContractTest），
 * 所以每条落盘路径都能直接断言，不需要靠"等一会儿再看子任务状态"。
 */
class FeedbackCaseControllerTest {

    private fun controller(
        repo: FeedbackCaseRepository? = null,
        caseId: String = "case-1",
        clockValue: String = "2026-09-24 10:00"
    ) = FeedbackCaseController(
        repository = repo,
        ioContext = Dispatchers.Unconfined,
        caseIdProvider = { caseId },
        clock = { clockValue }
    )

    private fun draft(
        reply: String = "候选回复",
        kb: String = "default",
        promptVersion: String = "abc123def4567890"
    ) = FeedbackCaseDraft(
        schemeReply = reply,
        kbName = kb,
        ideaHint = "想约她周末",
        intentText = "语气软一点",
        dialogue = listOf(
            DialogueSnapshotEntry(speaker = "PARTNER", text = "在忙"),
            DialogueSnapshotEntry(speaker = "USER", text = "好")
        ),
        contextMode = "only-this-round",
        promptVersion = promptVersion,
        modelId = "deepseek-chat",
        costYuan = 0.0123
    )

    private fun case(caseId: String, reply: String) = FeedbackCase(
        caseId = caseId,
        schemeIdentityKey = "STYLE:A",
        candidateReply = reply,
        categories = emptyList(),
        reasons = emptyList(),
        kbName = "default"
    )

    // ─── 1. 同步构造、返回待落盘实例 ───────────────────────────────

    @Test
    fun `a dislike yields the case synchronously and writes nothing until asked`() = runBlocking {
        val repo = mockk<FeedbackCaseRepository>(relaxed = true)
        val c = controller(repo)

        val created = c.toggle("STYLE:A", SchemeFeedback.DISLIKED, draft())

        assertNotNull("点踩后必须立刻有案例可展示", created)
        assertSame("返回的实例就必须是屏上那份，不能构造两次", created, c.currentCase.value)
        coVerify(exactly = 0) { repo.save(any()) }

        c.persistCase(created!!)
        coVerify(exactly = 1) { repo.save(created) }
    }

    @Test
    fun `the case fields come from the frozen draft and nothing is invented`() = runBlocking {
        val c = controller()
        val created = c.toggle("STYLE:A", SchemeFeedback.DISLIKED, draft())!!

        assertEquals("case-1", created.caseId)
        assertEquals("STYLE:A", created.schemeIdentityKey)
        assertEquals("候选回复", created.candidateReply)
        assertEquals("default", created.kbName)
        assertEquals("想约她周末", created.ideaHint)
        assertEquals("语气软一点", created.intentText)
        assertEquals("only-this-round", created.contextMode)
        assertEquals("deepseek-chat", created.modelId)
        assertEquals(0.0123, created.costYuan, 1e-9)
        assertEquals("2026-09-24 10:00", created.timestamp)
        assertEquals(listOf("PARTNER", "USER"), created.dialogueSnapshot.map { it.speaker })
        assertEquals("未分类案例的初始状态", emptyList<FeedbackCategory>(), created.categories)
    }

    @Test
    fun `the case carries the frozen prompt asset hash not a hard-coded version string`() {
        val c = controller()
        val created = c.toggle("STYLE:B", SchemeFeedback.DISLIKED, draft(promptVersion = "ff00ff0011223344"))
        assertEquals("ff00ff0011223344", created?.promptVersion)
    }

    // ─── 2. toggle 语义 ──────────────────────────────────────────

    @Test
    fun `liking twice cancels the vote and never creates a case`() {
        val c = controller()
        c.toggle("STYLE:A", SchemeFeedback.LIKED, draft())
        assertEquals(SchemeFeedback.LIKED, c.feedbackFor("STYLE:A"))

        assertNull(c.toggle("STYLE:A", SchemeFeedback.LIKED, draft()))
        assertEquals(SchemeFeedback.NONE, c.feedbackFor("STYLE:A"))
        assertNull(c.currentCase.value)
    }

    @Test
    fun `cancelling a dislike clears the case that is on screen`() {
        val c = controller()
        assertNotNull(c.toggle("STYLE:A", SchemeFeedback.DISLIKED, draft()))

        assertNull(c.toggle("STYLE:A", SchemeFeedback.DISLIKED, draft()))
        assertEquals(SchemeFeedback.NONE, c.feedbackFor("STYLE:A"))
        assertNull("取消踩后不能再展示旧案例", c.currentCase.value)
    }

    @Test
    fun `a like never yields a feedback case`() {
        val c = controller()
        assertNull(c.toggle("STYLE:A", SchemeFeedback.LIKED, draft()))
        assertNull("点赞不建案例", c.currentCase.value)
        assertEquals(SchemeFeedback.LIKED, c.feedbackFor("STYLE:A"))
    }

    @Test
    fun `an unresolvable draft still records the vote but yields no case`() {
        val c = controller()
        assertNull(c.toggle("DIRECTION:F", SchemeFeedback.DISLIKED, null))
        assertEquals(SchemeFeedback.DISLIKED, c.feedbackFor("DIRECTION:F"))
        assertNull("采不到素材就不能凭空造案例", c.currentCase.value)
    }

    @Test
    fun `style and direction identities keep separate votes`() {
        val c = controller()
        c.toggle("STYLE:A", SchemeFeedback.LIKED, null)
        c.toggle("DIRECTION:A", SchemeFeedback.DISLIKED, null)
        assertEquals(SchemeFeedback.LIKED, c.feedbackFor("STYLE:A"))
        assertEquals(SchemeFeedback.DISLIKED, c.feedbackFor("DIRECTION:A"))
    }

    // ─── 3. 取消信号 / 失败兜底 ───────────────────────────────────

    @Test
    fun `persistCase contains an io failure but rethrows cancellation`() = runBlocking {
        val ioFailing = mockk<FeedbackCaseRepository>(relaxed = true)
        coEvery { ioFailing.save(any()) } throws java.io.IOException("disk full")
        val c1 = controller(ioFailing)
        val shown = c1.toggle("STYLE:A", SchemeFeedback.DISLIKED, draft())
        c1.persistCase(shown!!) // 不抛——IO 失败只进日志
        assertNotNull("IO 失败不能把已展示的案例一起吞掉", c1.currentCase.value)

        val cancelling = mockk<FeedbackCaseRepository>(relaxed = true)
        coEvery { cancelling.save(any()) } throws CancellationException("scope torn down")
        val thrown = runCatching { controller(cancelling).persistCase(case("case-1", "x")) }.exceptionOrNull()
        assertTrue(
            "取消信号必须原样上抛，否则作用域拆除会被记成\"保存失败\"；实测：$thrown",
            thrown is CancellationException
        )
    }

    @Test
    fun `updateCase merges fields, ignores unknown ids and keeps the same failure policy`() = runBlocking {
        val repo = mockk<FeedbackCaseRepository>(relaxed = true)
        coEvery { repo.getAll() } returns listOf(case("case-1", "旧正文"))
        val captured = slot<FeedbackCase>()
        coEvery { repo.save(capture(captured)) } returns Unit
        val c = controller(repo)

        c.updateCase(
            "case-1",
            listOf(FeedbackCategory.EXPRESSION_DISLIKE),
            listOf("不像我"),
            userNote = "别用感叹号",
            betterVersion = "改后的正文"
        )

        assertEquals(listOf(FeedbackCategory.EXPRESSION_DISLIKE), captured.captured.categories)
        assertEquals(listOf("不像我"), captured.captured.reasons)
        assertEquals("别用感叹号", captured.captured.userNote)
        assertEquals("改后的正文", captured.captured.betterVersion)
        assertEquals("旧正文", captured.captured.candidateReply)

        val unknown = mockk<FeedbackCaseRepository>(relaxed = true)
        coEvery { unknown.getAll() } returns listOf(case("other", "x"))
        controller(unknown).updateCase("case-1", emptyList(), emptyList())
        coVerify(exactly = 0) { unknown.save(any()) }

        val cancelling = mockk<FeedbackCaseRepository>(relaxed = true)
        coEvery { cancelling.getAll() } throws CancellationException("torn down")
        val thrown = runCatching {
            controller(cancelling).updateCase("case-1", emptyList(), emptyList())
        }.exceptionOrNull()
        assertTrue("实测：$thrown", thrown is CancellationException)

        val ioFailing = mockk<FeedbackCaseRepository>(relaxed = true)
        coEvery { ioFailing.getAll() } throws java.io.IOException("unreadable")
        controller(ioFailing).updateCase("case-1", emptyList(), emptyList()) // 不抛
    }

    @Test
    fun `without a repository the controller still serves panel state`() {
        val c = controller(repo = null)
        assertNotNull(c.toggle("STYLE:A", SchemeFeedback.DISLIKED, draft()))
        c.dismissCase()
        assertNull(c.currentCase.value)
        assertEquals(SchemeFeedback.DISLIKED, c.feedbacks.value["STYLE:A"])
    }

    @Test
    fun `clearFeedbacks resets every vote at once`() {
        val c = controller()
        c.putFeedback("STYLE:A", SchemeFeedback.LIKED)
        c.putFeedback("DIRECTION:A", SchemeFeedback.DISLIKED)
        assertEquals(2, c.feedbacks.value.size)

        c.clearFeedbacks()

        assertEquals(emptyMap<String, SchemeFeedback>(), c.feedbacks.value)
        assertEquals(SchemeFeedback.NONE, c.feedbackFor("STYLE:A"))
    }

    // ─── 4. 防回流：状态真源只能在控制器里 ─────────────────────────

    @Test
    fun `the view model no longer owns feedback state itself`() {
        val candidates = listOf(
            "src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt",
            "app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt"
        )
        val file = candidates.map(::File).firstOrNull { it.isFile }
        assertNotNull("找不到 LoveBrainViewModel.kt，静态合同失去对象", file)
        val src = file!!.readText(Charsets.UTF_8)
        assertFalse(
            "赞/踩状态又回到 ViewModel 自己持有了",
            src.contains("_feedbacks") || src.contains("_currentFeedbackCase")
        )
        assertFalse(
            "案例里不能再出现写死的 prompt 版本字符串",
            Regex("""promptVersion\s*=\s*"v[\d.]+"""").containsMatchIn(src)
        )
        assertTrue(
            "ViewModel 必须自己发起落盘（唯一异步 owner）",
            src.contains("feedbackCases.persistCase(")
        )
    }

    @Test
    fun `the controller owns no coroutine scope`() {
        val candidates = listOf(
            "src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt",
            "app/src/main/java/com/lovebrain/app/viewmodel/FeedbackCaseController.kt"
        )
        val file = candidates.map(::File).firstOrNull { it.isFile }
        assertNotNull("找不到 FeedbackCaseController.kt", file)
        val src = file!!.readText(Charsets.UTF_8)
        assertFalse("控制器不得自己 launch——否则异步工作出现第二个 owner", src.contains(".launch"))
        assertFalse(src.contains("CoroutineScope"))
    }
}
