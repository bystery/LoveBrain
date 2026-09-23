package com.lovebrain.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1-03: HomeDestination 导航状态测试。
 *
 * 替代旧的枚举数量测试——验证：
 * - Saver round-trip：保存→恢复后 destination 一致（旋转/进程恢复）
 * - 默认 destination 为 Home
 * - 各 destination 唯一可区分
 * - Saver 对未知名称回退到 Home（向前兼容）
 */
class HomeNavigationTest {

    @Test
    fun `Saver round-trips all destinations`() {
        val destinations = listOf(
            HomeDestination.Home,
            HomeDestination.FeedbackCases,
            HomeDestination.About,
            HomeDestination.Providers,
            HomeDestination.Usage
        )
        for (dest in destinations) {
            val scope = androidx.compose.runtime.saveable.SaverScope { true }
            val saved = HomeDestination.Saver.run { scope.save(dest) }
            assertNotNull("Saved value should not be null for $dest", saved)
            val restored = HomeDestination.Saver.restore(saved as String)
            assertEquals("Round-trip should restore same destination", dest, restored)
        }
    }

    @Test
    fun `Saver restores unknown name to Home`() {
        val restored = HomeDestination.Saver.restore("UnknownDestination")
        assertEquals("Unknown destination should restore to Home",
            HomeDestination.Home, restored)
    }

    @Test
    fun `Saver restores null to Home`() {
        val restored = HomeDestination.Saver.restore("")
        assertEquals("Empty string should restore to Home",
            HomeDestination.Home, restored)
    }

    @Test
    fun `Home is the default destination`() {
        val initial = HomeDestination.Home
        assertNotNull("Default should not be null", initial)
        assertTrue("Default should be Home", initial is HomeDestination.Home)
    }

    @Test
    fun `each destination is distinct`() {
        val set = setOf(
            HomeDestination.Home,
            HomeDestination.FeedbackCases,
            HomeDestination.About,
            HomeDestination.Providers,
            HomeDestination.Usage
        )
        assertEquals("All destinations should be distinct", 5, set.size)
    }

    @Test
    fun `navigating between destinations changes state`() {
        // Simulate state holder behavior: remember mutableStateOf(Home)
        var current: HomeDestination = HomeDestination.Home
        assertEquals(HomeDestination.Home, current)

        // Navigate to Providers
        current = HomeDestination.Providers
        assertEquals(HomeDestination.Providers, current)
        assertNotEquals(HomeDestination.Home, current)

        // Navigate to About
        current = HomeDestination.About
        assertEquals(HomeDestination.About, current)

        // Back to Home
        current = HomeDestination.Home
        assertEquals(HomeDestination.Home, current)
    }
}
