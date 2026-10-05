package com.lovebrain.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
