package com.lovebrain.app.domain

/**
 * 锦囊缓存的命中判据（从 `LoveBrainViewModel` 拆出）。
 *
 * 缓存命中的意义是"同一天、同一个库、同一份上下文、同一版 prompt 才不重复花钱"。
 * 四条判据原先写在 `showTodaySuggestion` 的协程里，测一条就要连模拟器、偏好与
 * Provider 快照一起搭；搬出来后可以直接问"这一份缓存能不能直接用"。
 *
 * 四条里最容易被写坏的是 promptVersion：拿 App 版本名当它，改了 prompt 不发版就
 * 永远命中旧缓存，用户看到的还是上一版建议。所以现在用资产内容 hash；
 * 老缓存（没有 promptVersion 时落到占位值）因此必然不命中，这是故意的。
 */
object SuggestCachePolicy {

    /** 一次请求冻结下来的可比身份 */
    data class Request(
        val kbName: String,
        val date: String,
        val contextFingerprint: String,
        val promptVersion: String
    )

    /** 缓存记录里可比的那四项 */
    data class Cached(
        val kbId: String,
        val date: String,
        val contextFingerprint: String,
        val promptVersion: String
    )

    /** 四项全等才算命中；任何一项不同都得重新请求 */
    fun isHit(cached: Cached?, request: Request): Boolean {
        if (cached == null) return false
        return cached.date == request.date &&
            cached.kbId == request.kbName &&
            cached.contextFingerprint == request.contextFingerprint &&
            cached.promptVersion == request.promptVersion
    }
}
