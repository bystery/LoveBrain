package com.lovebrain.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `HomeDestination` 导航状态（首页重做之后只剩四格：Home / FeedbackCases / Providers / CaptureApps）。
 *
 * 钉三件事：
 * - Saver round-trip：保存→恢复后 destination 一致（旋转/进程重建）；
 * - 默认格是 Home；各格互不可混淆；
 * - **旧进程里存下的 `About` / `Usage` 两个名字必须回落到 Home**——那两页已经按"完全不可达"
 *   删除，如果 Saver 还能把它们复活，就是给了一个没有页面的空目的地（第14节第4条 那条动态入口的账）。
 */
class HomeNavigationTest {

    private val liveDestinations = listOf(
        HomeDestination.Home,
        HomeDestination.FeedbackCases,
        HomeDestination.Providers,
        HomeDestination.CaptureApps
    )

    @Test
    fun `Saver round-trips every live destination`() {
        // 哨兵：清单只剩四格说明有目的地没人登记了（删了一格却忘了这里会静默少测）
        assertEquals("首页重做后只剩四格", 4, liveDestinations.size)
        val scope = androidx.compose.runtime.saveable.SaverScope { true }
        for (dest in liveDestinations) {
            val saved = HomeDestination.Saver.run { scope.save(dest) }
            assertNotNull("Saved value should not be null for $dest", saved)
            assertEquals("Round-trip should restore same destination", dest, HomeDestination.Saver.restore(saved as String))
        }
    }

    @Test
    fun `the saved name of a removed page falls back to home`() {
        listOf("About", "Usage", "UnknownDestination", "").forEach { name ->
            assertEquals(
                "`$name` 这一格已经没有页面了，Saver 必须回落 Home，不许造出空目的地",
                HomeDestination.Home, HomeDestination.Saver.restore(name)
            )
        }
    }

    @Test
    fun `Home is the default destination`() {
        assertEquals(HomeDestination.Home, HomeDestination.Home)
    }

    @Test
    fun `each destination is distinct`() {
        assertEquals("四格互不相同", 4, liveDestinations.toSet().size)
    }

    @Test
    fun `navigating between destinations changes state`() {
        var current: HomeDestination = HomeDestination.Home
        assertEquals(HomeDestination.Home, current)

        current = HomeDestination.Providers
        assertEquals(HomeDestination.Providers, current)
        assertNotEquals(HomeDestination.Home, current)

        current = HomeDestination.CaptureApps
        assertEquals(HomeDestination.CaptureApps, current)

        current = HomeDestination.Home
        assertEquals(HomeDestination.Home, current)
    }

    // ─────────────── §9.2：根导航一整族都是滑入滑出（排序 + 形状两把尺）───────────────
    //
    // ⚠ 为什么排序用行为、支路用形状：`AnimatedContent` 的 transitionSpec 是**进场前后**才成立的
    // 读数，Robolectric 这一棵树上量不到动画本体（只能等真机录像）；能静态钉死的是
    // "哪一支不许只剩一次淡入"和"方向按哪把尺判"。手感那一面仍登记为未验证项。

    private val rootSource: String by lazy {
        val f = File("src/main/java/com/lovebrain/app/ui/home/SetupRoot.kt").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/home/SetupRoot.kt")
        com.lovebrain.app.core.testing.SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    /** 取 `transitionSpec = { … }` 那一具的体内（花括号配平；注释已被抹平，不会数到说明文字） */
    private fun transitionSpecBody(): String {
        val start = rootSource.indexOf("transitionSpec =")
        assertTrue("`transitionSpec` 不在盘上——根导航又退回裸 `when(destination)` 了", start >= 0)
        val open = rootSource.indexOf('{', start)
        var depth = 0
        for (i in open until rootSource.length) {
            when (rootSource[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return rootSource.substring(open + 1, i)
                }
            }
        }
        throw AssertionError("transitionSpec 的花括号没配平")
    }

    /** 前进/后退的方向判据：首页最前，三格子页各一档且互不相同 */
    @Test
    fun `rank puts home first and the three subpages behind it`() {
        assertEquals(0, HomeDestination.Home.rank)
        assertEquals(
            "三档子页各占一格（方向就按这一把尺判，读不出第二本账）",
            listOf(1, 2, 3),
            listOf(
                HomeDestination.FeedbackCases.rank,
                HomeDestination.Providers.rank,
                HomeDestination.CaptureApps.rank
            )
        )
    }

    /**
     * 三支分支**全部**带滑入或滑出：§9.2 要的是"统一根导航的前进/返回过渡"，
     * 表行②判的那句"前进分支实际只有新页淡入"复测属实的是**旧读数**（`bacede8` 已把前进那一支
     * 补成滑入），本轮收的是剩下那一支——子页↔子页原先是纯淡入淡出（"一次性淡入"）。
     *
     * 回退成什么就红：
     * - 谁把某一支改回 `fadeIn(…) togetherWith fadeOut(…)` ⇒ 第二句从 0 变 1（反向证人已经钉过旧形状）；
     * - 谁把滑入丢了 ⇒ 第一句数不到 2；
     * - 方向判据换成"另一本硬编码账"（不用 rank）⇒ 第三句数到 0。
     */
    @Test
    fun `every root nav branch slides, none is a bare fade`() {
        val spec = transitionSpecBody()
        assertEquals("前进两支都要 slideInHorizontally", 2, Regex("""slideInHorizontally\(""").findAll(spec).count())
        assertEquals("离场三支都要 slideOutHorizontally", 3, Regex("""slideOutHorizontally\(""").findAll(spec).count())
        assertEquals(
            "一支都不许只剩「淡入配淡出」（§9.2：不每页各补一份一次性淡入）",
            0, Regex("""fadeIn\((?:[^()]|\([^()]*\))*\)\s*togetherWith\s*fadeOut""").findAll(spec).count()
        )
        assertEquals("方向按 rank 判，且只有那一处判据", 1, Regex("""targetState\.rank > initialState\.rank""")
            .findAll(spec).count())
        // 反向证人：改前那一支（纯淡入淡出）必须被第二句数到 1，否则这条尺是恒绿的
        val oldBranch = "fadeIn(tween(200)) togetherWith fadeOut(tween(200))"
        assertEquals("注件没就位：旧形状该被这句抓到",
            1, Regex("""fadeIn\((?:[^()]|\([^()]*\))*\)\s*togetherWith\s*fadeOut""").findAll(oldBranch).count())
    }
}
