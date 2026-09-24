package com.lovebrain.app.viewmodel

import com.lovebrain.app.model.ProfileSuggestion
import com.lovebrain.app.model.ProfileUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画像卡片那份快照的**纯**状态转移（没有 Android、没有协程、没有仓库）。
 *
 * 为什么单独测：卡片上那两件事（建议内容、"正在确认"）原先是两个 flow、14 处各改各的，
 * 于是"确认中重复点""确认期间来了新建议"这两种竞态只能靠调用点各自记得判一下。
 * 现在判据住在这个类型里，就能像普通函数一样逐条钉死。
 *
 * 与 `ProfileTransactionResultTest` / `ProfileConfirmTest` 的分工：那两处跑整个 ViewModel，
 * 钉的是"点下去之后卡片的可见结果"（含仓库 typed result 的分歧）；这里钉的是转移规则本身。
 */
class ProfileReviewTest {

    private val validPayload = ProfileUpdate.parse("""{"me":"新的我","stage_changed":false}""")
    private val invalidPayload = ProfileUpdate.parse("not-a-json")

    private fun suggestion(
        id: String = "s-1",
        payload: ProfileUpdate = validPayload,
        kbName: String = "kb1"
    ) = ProfileSuggestion(
        suggestionId = id, kbName = kbName, display = "建议摘要",
        rawJson = "{}", profileUpdate = payload
    )

    @Test
    fun freshReviewShowsNoCardAndAllowsNothing() {
        val r = ProfileReview()
        assertNull(r.suggestion)
        assertFalse(r.isConfirming)
        assertFalse("没有卡片就没有可确认", r.canConfirm)
        assertFalse(r.canAttemptConfirm)
    }

    @Test
    fun arrivedPutsTheSuggestionOnTheCard() {
        val r = ProfileReview().reduce(ProfileReview.Event.Arrived(suggestion(id = "s-7")))
        assertEquals("s-7", r.suggestion?.suggestionId)
        assertFalse("新建议到达不该顺手把卡片标成\"正在确认\"", r.isConfirming)
    }

    /** 按钮可点与否只看 payload，不看有没有卡片这一件事本身 */
    @Test
    fun canConfirmFollowsPayloadValidityOnly() {
        assertTrue(ProfileReview(suggestion = suggestion()).canConfirm)
        assertFalse(
            "payload 没过校验就不能让用户点下去再吃一次失败",
            ProfileReview(suggestion = suggestion(payload = invalidPayload)).canConfirm
        )
        assertFalse(
            "确认中不该再允许发起第二次",
            ProfileReview(suggestion = suggestion(), isConfirming = true).canAttemptConfirm
        )
    }

    @Test
    fun confirmStartSetsTheFlagAndKeepsTheSuggestion() {
        val before = ProfileReview(suggestion = suggestion())
        val after = before.reduce(ProfileReview.Event.ConfirmStarted)
        assertTrue(after.isConfirming)
        assertEquals(before.suggestion, after.suggestion)
    }

    /** 重复点确认（双击、或第二次请求在第一次没结束时到达）必须原样返回 */
    @Test
    fun doubleConfirmIsIgnoredNotQueued() {
        val confirming = ProfileReview(suggestion = suggestion(), isConfirming = true)
        assertEquals(confirming, confirming.reduce(ProfileReview.Event.ConfirmStarted))
        assertEquals(
            "没有建议时也不许把状态标成确认中",
            ProfileReview(), ProfileReview().reduce(ProfileReview.Event.ConfirmStarted)
        )
    }

    @Test
    fun finishingOnlyEndsTheBusyFlag() {
        val r = ProfileReview(suggestion = suggestion(), isConfirming = true)
            .reduce(ProfileReview.Event.ConfirmFinished)
        assertFalse(r.isConfirming)
        assertEquals("结束确认不等于卡片作废——失败时卡片要留着让人重试", "s-1", r.suggestion?.suggestionId)
    }

    @Test
    fun dismissingTheCardLeavesTheBusyFlagAlone() {
        val r = ProfileReview(suggestion = suggestion(), isConfirming = true)
            .reduce(ProfileReview.Event.Dismissed)
        assertNull(r.suggestion)
        assertTrue(
            "钉住这个中间态：卡片关了但协程还在跑，finally 之后 confirming 才归位。" +
                "它现在无害（没有卡片就没有按钮），写下来是为了下次改动时看得见",
            r.isConfirming
        )
    }

    /**
     * 确认期间来了新建议：清卡只能清"我确认的那一份"。
     *
     * 这条是这次合并最实在的收益——原先这个判据手写在调用点（读一次 flow、比 id、再写），
     * 一旦有人图省事直接写 null 就会抹掉用户没看过的新卡片。
     */
    @Test
    fun clearingAfterSuccessOnlyClearsTheConfirmedOne() {
        val confirmed = suggestion(id = "old")
        val arrivedWhileConfirming = ProfileReview(
            suggestion = suggestion(id = "new"), isConfirming = true
        )
        val kept = arrivedWhileConfirming.reduce(ProfileReview.Event.ClearedIfCurrent(confirmed.suggestionId))
        assertEquals("新卡片不许被旧请求的收尾抹掉", "new", kept.suggestion?.suggestionId)

        val sameOne = arrivedWhileConfirming.copy(suggestion = confirmed)
            .reduce(ProfileReview.Event.ClearedIfCurrent(confirmed.suggestionId))
        assertNull("确认的正是当前这份时该清掉", sameOne.suggestion)
    }

    @Test
    fun aFreshSuggestionReplacesTheOldCardWithoutTouchingTheFlag() {
        val r = ProfileReview(suggestion = suggestion(id = "old"), isConfirming = true)
            .reduce(ProfileReview.Event.Arrived(suggestion(id = "new")))
        assertEquals("new", r.suggestion?.suggestionId)
        assertTrue("替换卡片不是结束确认的理由", r.isConfirming)
    }
}
