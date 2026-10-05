package com.lovebrain.app.feature.feedback

import com.lovebrain.app.model.FeedbackCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点踩落盘这一格的四件事：无弹窗也照样落盘、失败不虚报成功、重复踩不涨记录数、
 * 取消/再踩不靠"文案一样"跨轮去重。
 *
 * 每格都写了**什么反例会让它红**——这一族以前是"面板自己弹一张原因表单，用户写完才存"，
 * 表单删掉之后如果没人补这条判据，落盘就退化成"点完就当成功了"。
 */
class DislikeCaseSaveTest {

    /**
     * 夹具走**生产形状**：`FeedbackCaseController.toggle()` 在建案例时给的是
     * `categories = emptyList(), reasons = emptyList()`（表单没了，没人再填），
     * `promptVersion` 由 `freezeDislikeDraft` 从生成上下文带来。
     * 所以"没有原因"不是这格的假设，而是这格要保住的行为。
     */
    private fun case(
        caseId: String,
        identity: String,
        promptVersion: String,
        reply: String = "候选那句",
        reasons: List<String> = emptyList()
    ) = FeedbackCase(
        caseId = caseId,
        schemeIdentityKey = identity,
        candidateReply = reply,
        categories = emptyList(),
        reasons = reasons,
        promptVersion = promptVersion
    )

    /** 空原因合法：点踩这一步不需要任何用户输入就能落盘（反例：要求 reasons 非空才写 → 红） */
    @Test
    fun `a dislike with no reason at all still reaches the disk`() = runTest {
        var written: FeedbackCase? = null
        val outcome = recordDislikeCase(
            incoming = case("id-1", "STYLE:B", "pv-9"),
            existing = emptyList(),
            persist = { written = it }
        )
        assertEquals(CaseSaveOutcome.Recorded, outcome)
        assertEquals("空原因也要真的把这一条交下去（不是只改内存状态）", "id-1", written?.caseId)
        assertTrue(written!!.reasons.isEmpty() && written!!.categories.isEmpty())
    }

    /**
     * 失败不虚报成功（反例：`persist` 抛异常、这一格仍旧返回 Recorded——
     * 这正是当前 `FeedbackCaseController.persistCase` 只记日志的写法在界面上的后果）。
     */
    @Test
    fun `a write that throws is reported as failure, not as success`() = runTest {
        val outcome = recordDislikeCase(
            incoming = case("id-2", "STYLE:B", "pv-9"),
            existing = emptyList(),
            persist = { throw java.io.IOException("disk full") }
        )
        assertEquals("写坏了只能说失败：界面据此才不能报「已记录」", CaseSaveOutcome.Failed, outcome)
    }

    /** 失败之后可以再点一次：第二次写成功就报成功（反例：把第一次的失败状态粘住 → 红） */
    @Test
    fun `a retry after a failure can still record`() = runTest {
        var shouldFail = true
        val outcome = recordDislikeCase(
            incoming = case("id-3", "DIRECTION:F", "pv-9"),
            existing = emptyList(),
            persist = { if (shouldFail) { shouldFail = false; throw IllegalStateException() } }
        )
        assertEquals(CaseSaveOutcome.Failed, outcome)
        val retry = recordDislikeCase(
            incoming = case("id-3", "DIRECTION:F", "pv-9"),
            existing = emptyList(),
            persist = { }
        )
        assertEquals(CaseSaveOutcome.Recorded, retry)
    }

    /**
     * 重复踩不产生重复记录：同一 identity + 同一生成版本，第二次点踩不写库。
     *
     * 反例一：判据只看 `caseId`——每次点踩都是新 UUID，库里就会长出两条。
     * 反例二：`toggle()` 在"取消再踩"之后交回同一 identity 的新案例，这一格若直接 persist 也涨。
     */
    @Test
    fun `the same scheme and generation version does not add a second record`() = runTest {
        val stored = listOf(case("first", "STYLE:B", "pv-9"))
        var persisted = 0
        val outcome = recordDislikeCase(
            incoming = case("second", "STYLE:B", "pv-9"),
            existing = stored,
            persist = { persisted++ }
        )
        assertEquals(CaseSaveOutcome.AlreadyRecorded("first"), outcome)
        assertEquals("重复踩一条都不许多写", 0, persisted)
    }

    /**
     * 生成版本换了就是新的一条：同一条方案在新一轮生成里被再踩，必须落库。
     *
     * 反例：identity 相同就拦掉——那会把"用户踩了两次不同版本的回复"合并成一条，
     * 而 第9节第3条 要的是 identity + 生成版本绑定，不是"这条方案只能踩一次"。
     */
    @Test
    fun `a new generation version of the same scheme is a new record`() = runTest {
        val stored = listOf(case("first", "STYLE:B", "pv-9"))
        var persisted = 0
        val outcome = recordDislikeCase(
            incoming = case("second", "STYLE:B", "pv-10"),
            existing = stored,
            persist = { persisted++ }
        )
        assertEquals(CaseSaveOutcome.Recorded, outcome)
        assertEquals(1, persisted)
    }

    /**
     * 不许凭文案相同跨轮去重（第9节第3条 明文）。
     *
     * 反例：把判据写成 `candidateReply` 相等——两条不同轮、不同 identity 的回复
     * 只要字面一样（"我今天有点累"这种短句很容易再出现）就会被吞掉一条。
     */
    @Test
    fun `the same reply text in a different scheme is still recorded`() = runTest {
        val stored = listOf(case("first", "STYLE:B", "pv-9", reply = "同一句文案"))
        var persisted = 0
        val outcome = recordDislikeCase(
            incoming = case("second", "DIRECTION:F", "pv-9", reply = "同一句文案"),
            existing = stored,
            persist = { persisted++ }
        )
        assertEquals(CaseSaveOutcome.Recorded, outcome)
        assertEquals(1, persisted)
    }

    /** identity 为空（夹具/脏数据）不许把所有案例吞成"重复"——反例：只比 promptVersion */
    @Test
    fun `a blank identity never matches as duplicate`() = runTest {
        val stored = listOf(case("first", "", "pv-9"))
        var persisted = 0
        val outcome = recordDislikeCase(
            incoming = case("second", "", "pv-9"),
            existing = stored,
            persist = { persisted++ }
        )
        assertEquals(CaseSaveOutcome.Recorded, outcome)
        assertEquals(1, persisted)
    }

    /**
     * 协程取消原样上抛：一次作用域拆除不是"保存失败"
     * （这条判据从 `FeedbackCaseController.persistCase` 搬过来，仍然要有证人）。
     *
     * 反例：把 `catch (e: Exception)` 写成也接 CancellationException —— 面板销毁时
     * 用户会看到"记入失败"，而真相是没有任何失败发生过。
     */
    @Test
    fun `cancellation is not swallowed as a save failure`() = kotlinx.coroutines.runBlocking {
        val thrown = runCatching {
            recordDislikeCase(
                incoming = case("id-4", "STYLE:B", "pv-9"),
                existing = emptyList(),
                persist = { throw CancellationException("scope torn down") }
            )
        }.exceptionOrNull()
        assertTrue(
            "取消必须原样上抛，实到：$thrown",
            thrown is CancellationException
        )
    }

    /**
     * 提示语只跟着结果走：失败那一句不许拿到"已记录"。
     * 反例：`caseSaveNoticeText` 里把 Failed 也映射成 recorded——
     * 界面上就是用户嫌的那种"说记了其实没记"。
     */
    @Test
    fun `the notice sentence follows the outcome`() = runTest {
        val recorded = "已记录"
        val failed = "记入失败，请重试"
        assertEquals(recorded, caseSaveNoticeText(CaseSaveOutcome.Recorded, recorded, failed))
        assertEquals(
            "库里已有同一条也算「已记录」（这一句说的是状态，不是新增条数）",
            recorded, caseSaveNoticeText(CaseSaveOutcome.AlreadyRecorded("x"), recorded, failed)
        )
        assertEquals(failed, caseSaveNoticeText(CaseSaveOutcome.Failed, recorded, failed))
    }
}
