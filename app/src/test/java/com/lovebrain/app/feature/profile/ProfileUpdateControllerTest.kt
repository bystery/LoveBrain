package com.lovebrain.app.feature.profile

import com.lovebrain.app.model.PreconditionReason
import com.lovebrain.app.model.ProfileSuggestion
import com.lovebrain.app.model.ProfileTransactionResult
import com.lovebrain.app.model.ProfileUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画像确认那一段行为的判据（复核 第5节第2条 第 6 步搬的第二块**行为**）。
 *
 * 搬之前这是 ViewModel 里最大的单个成员（98 行），而且**每一条分支都要 mock 仓库 + 协调器 + 触发器
 * 才走得到**，所以它一次也没被 JVM 量过。搬进来之后每条分支都是一个可钉的用户可见差别：
 * · **只读保护不许清卡**（`LIBRARY_READ_ONLY` 说的是"这个 App 比库旧"，建议本身仍然有效；
 *   跟着另外两种前置失败一起清掉，用户这次攒的审核内容就白丢一次）；
 * · 回滚成功与回滚失败是**两句不一样的话**（合并成"保存失败"就是撒谎）；
 * · 成功只清"我确认的这一份"（确认期间到了新建议时不许把新的清掉）；
 * · 任何一条路走到底，确认位都必须复位（否则卡片永远转圈，而浮层的取消与遮罩都是 `!saving`）。
 *
 * ⚠ 这里**不钉"确认位曾经为 true"**：那种瞬时值随调度器合并而消失，钉它会得到一条
 * 换个调度器就红的判据。要钉的是落定之后的两件事：卡片还在不在、确认位最后有没有复位。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileUpdateControllerTest {

    private val payload = ProfileUpdate(
        rawJson = "{}", valid = true, error = null,
        me = "我变了", her = null, warmth = null,
        stageChanged = false, newStage = null,
        observations = emptyList(), messageToUser = null, displaySummary = "摘要"
    )

    private fun suggestion(id: String = "s-1", revision: Int = 7, body: ProfileUpdate = payload) =
        ProfileSuggestion(
            suggestionId = id, kbName = "小芳", display = "摘要", rawJson = "{}",
            profileUpdate = body, correctionsRevision = revision
        )

    private class Harness(
        var exists: Boolean = true,
        var revisionNow: Int = 7,
        var result: ProfileTransactionResult = ProfileTransactionResult.Success,
        var throwOnApply: Throwable? = null
    ) {
        var appliedCalls = 0
        var stopCalls = 0
        /** 跑完一次 `run(...)` 之后读这里——`runTest` 只能返回 TestResult，控制器没法当返回值用 */
        lateinit var controller: ProfileUpdateController
        fun snapshot() = controller.review.value
        val warnings = mutableListOf<String>()
        val notices = mutableListOf<String>()
        var logged: Pair<String, Throwable?>? = null

        fun build(scope: CoroutineScope) = ProfileUpdateController(
            scope = scope,
            libraryExists = { exists },
            readCorrectionsRevision = { revisionNow },
            applyUpdate = { _, _, _ -> throwOnApply?.let { throw it }; result },
            onApplied = { appliedCalls++ },
            onWarning = { warnings += it },
            onNotice = { notices += it },
            onError = { msg, e -> logged = msg to e },
            stopRegeneration = { stopCalls++ }
        )
    }

    /**
     * 起一个控制器、把卡片摆成"有建议"（可选再摆成"确认中"）、执行动作、排干调度器。
     * 动作跑完之后判据一律读 `h.snapshot()`（控制器存在 harness 里）。
     */
    private fun run(
        h: Harness,
        seed: ProfileSuggestion? = null,
        confirming: Boolean = false,
        action: ProfileUpdateController.() -> Unit
    ) = runTest {
        // 控制器交给 harness：runTest 的返回值只能是 TestResult，没法把它当返回值带出去
        val controller = h.build(CoroutineScope(StandardTestDispatcher(testScheduler)))
        h.controller = controller
        if (seed != null) controller.accept(ProfileReview.Event.Arrived(seed))
        if (confirming) controller.accept(ProfileReview.Event.ConfirmStarted)
        controller.action()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `invalid payload clears the card and never touches the repository`() {
        val h = Harness()
        run(h, suggestion(body = payload.copy(valid = false))) { confirm() }
        assertEquals(listOf("建议格式无效，请重新生成"), h.warnings)
        assertNull("格式无效要把卡清掉", h.snapshot().suggestion)
        assertEquals(0, h.appliedCalls)
    }

    @Test
    fun `library gone clears the card`() {
        val h = Harness(exists = false)
        run(h, suggestion()) { confirm() }
        assertEquals(listOf("原知识库已删除，这条画像建议已失效"), h.warnings)
        assertNull(h.snapshot().suggestion)
        assertEquals(0, h.appliedCalls)
    }

    @Test
    fun `revision drift clears the card before writing`() {
        val h = Harness(revisionNow = 8)
        run(h, suggestion(revision = 7)) { confirm() }
        assertEquals(listOf("资料已变化，请重新生成"), h.warnings)
        assertNull(h.snapshot().suggestion)
        assertEquals("revision 不符就不该落盘", 0, h.appliedCalls)
    }

    @Test
    fun `success clears only the confirmed suggestion, notifies and refreshes`() {
        val h = Harness()
        run(h, suggestion(id = "s-1")) {
            confirm()
            // 确认期间到了新建议：成功回来时只许清"我确认的那一份"
            accept(ProfileReview.Event.Arrived(suggestion(id = "s-2")))
        }
        assertEquals(listOf("画像已更新"), h.notices)
        assertEquals(1, h.appliedCalls)
        assertEquals(
            "新到的那份 s-2 不许被上一次确认的成功清掉",
            "s-2", h.snapshot().suggestion?.suggestionId
        )
        assertFalse("确认位必须复位", h.snapshot().isConfirming)
    }

    @Test
    fun `read-only protection warns but keeps the suggestion`() {
        val h = Harness(
            result = ProfileTransactionResult.PreconditionFailed(PreconditionReason.LIBRARY_READ_ONLY)
        )
        run(h, suggestion()) { confirm() }

        assertEquals(
            "只读保护要讲清是'App 比库旧'，不是'建议作废'",
            listOf("这个知识库的结构版本比本 App 还新，已被设为只读，画像没有写入（可以先升级 App 再确认）"),
            h.warnings
        )
        assertEquals(
            "建议必须留着——升级之后同一份仍然有效，清掉就白丢用户这次攒的审核内容",
            "s-1", h.snapshot().suggestion?.suggestionId
        )
    }

    @Test
    fun `the two genuine precondition failures clear the card and read differently from read-only`() {
        for ((reason, expected) in listOf(
            PreconditionReason.KB_NOT_FOUND to "原知识库已删除，这条画像建议已失效",
            PreconditionReason.REVISION_CONFLICT to "资料已变化，请重新生成"
        )) {
            val h = Harness(result = ProfileTransactionResult.PreconditionFailed(reason))
            run(h, suggestion()) { confirm() }
            assertEquals(listOf(expected), h.warnings)
            assertNull("$reason 属于建议作废，要清卡", h.snapshot().suggestion)
        }
    }

    @Test
    fun `rolled back and rollback failed are two different sentences`() {
        val h1 = Harness(result = ProfileTransactionResult.RolledBack(java.io.IOException("disk full")))
        run(h1, suggestion()) { confirm() }
        val h2 = Harness(
            result = ProfileTransactionResult.RollbackFailed(
                java.io.IOException("restore failed"), listOf("profile.json")
            )
        )
        run(h2, suggestion()) { confirm() }

        assertEquals(listOf("画像写入失败，已恢复原数据，可重试"), h1.warnings)
        assertEquals(listOf("画像写入失败且恢复异常，数据可能已损坏，请检查知识库"), h2.warnings)
        assertTrue("两种严重程度不许合并成一句话", h1.warnings != h2.warnings)
        assertTrue("回滚失败要留下诊断线索：${h2.logged?.first}", (h2.logged?.first ?: "").contains("CRITICAL"))
    }

    @Test
    fun `rolled back keeps the suggestion so the user can retry`() {
        val h = Harness(result = ProfileTransactionResult.RolledBack(java.io.IOException("disk full")))
        run(h, suggestion()) { confirm() }
        assertEquals("写入失败不是建议作废：卡要留着让人重试", "s-1", h.snapshot().suggestion?.suggestionId)
        assertFalse(h.snapshot().isConfirming)
    }

    @Test
    fun `unexpected exception still resets the confirming flag`() {
        val h = Harness(throwOnApply = java.io.IOException("boom"))
        run(h, suggestion()) { confirm() }
        assertEquals(listOf("画像写入发生异常，请重试"), h.warnings)
        assertFalse("异常路径也必须复位确认位，否则卡片永远转圈", h.snapshot().isConfirming)
        assertEquals("异常不该被当成一次成功落盘", 0, h.appliedCalls)
    }

    @Test
    fun `a second confirm while one is in flight does not write twice`() {
        val h = Harness()
        run(h, suggestion(), confirming = true) { confirm() }
        assertEquals("重复点确认不得再走一次落盘", 0, h.appliedCalls)
        assertTrue("重复点也不该给一条警告（它什么都没做）：${h.warnings}", h.warnings.isEmpty())
    }

    @Test
    fun `confirm without a suggestion is a no-op`() {
        val h = Harness()
        run(h, seed = null) { confirm() }
        assertEquals(0, h.appliedCalls)
        assertTrue(h.warnings.isEmpty() && h.notices.isEmpty())
    }

    @Test
    fun `dismiss releases the running regeneration and clears the card`() {
        val h = Harness()
        run(h, suggestion()) { dismiss() }
        assertEquals("关卡片要先让在跑的重新生成让位，否则迟到回调会把卡翻回来", 1, h.stopCalls)
        assertNull(h.snapshot().suggestion)
    }
}
