package com.lovebrain.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锦囊缓存的命中判据（`SuggestCachePolicy`）。
 *
 * 这条规则决定"要不要再花一次请求"：判太松，用户改了上下文还看到昨天的建议；
 * 判太严，同一天同条件反复扣钱。四条判据里最要紧的是 promptVersion——
 * 它必须能察觉"prompt 被改过但 App 没发版"。
 */
class SuggestCachePolicyTest {

    private val request = SuggestCachePolicy.Request(
        kbName = "default",
        date = "2026-09-24",
        contextFingerprint = "fp-abc",
        promptVersion = "hash-1"
    )

    private fun cached(
        kbId: String = "default",
        date: String = "2026-09-24",
        contextFingerprint: String = "fp-abc",
        promptVersion: String = "hash-1"
    ) = SuggestCachePolicy.Cached(kbId, date, contextFingerprint, promptVersion)

    @Test
    fun `all four parts matching is the only way to hit`() {
        assertTrue(SuggestCachePolicy.isHit(cached(), request))
    }

    @Test
    fun `no cache means no hit`() {
        assertFalse(SuggestCachePolicy.isHit(null, request))
    }

    @Test
    fun `each part alone can invalidate the cache`() {
        assertFalse("换库不能复用别人的建议", SuggestCachePolicy.isHit(cached(kbId = "other"), request))
        assertFalse("跨到第二天必须重新生成", SuggestCachePolicy.isHit(cached(date = "2026-09-25"), request))
        assertFalse("上下文变了要重算", SuggestCachePolicy.isHit(cached(contextFingerprint = "fp-other"), request))
        assertFalse("prompt 改了要重算", SuggestCachePolicy.isHit(cached(promptVersion = "hash-2"), request))
    }

    @Test
    fun `a legacy cache without a prompt version never masquerades as current`() {
        // 老数据落的是占位值；与资产 hash 不可能相等，因此必然 miss 并重新生成
        assertFalse(SuggestCachePolicy.isHit(cached(promptVersion = "v1"), request))
    }

    @Test
    fun `matching is exact rather than prefix or case-insensitive`() {
        assertFalse(SuggestCachePolicy.isHit(cached(contextFingerprint = "fp-abc-extended"), request))
        assertFalse(SuggestCachePolicy.isHit(cached(promptVersion = "HASH-1"), request))
    }
}
