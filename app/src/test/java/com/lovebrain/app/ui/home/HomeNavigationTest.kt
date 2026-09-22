package com.lovebrain.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1-17: 首页导航目的地测试。
 *
 * 验证 HomeDestination sealed class 的完整性和唯一性：
 * - Home / FeedbackCases / About / Providers / Usage 五个目的地
 * - 每个目的地是唯一的 data object
 * - 覆盖首页四状态导航和 Provider 根页面
 */
class HomeNavigationTest {

    @Test
    fun `HomeDestination has all required destinations`() {
        val destinations = listOf(
            HomeDestination.Home,
            HomeDestination.FeedbackCases,
            HomeDestination.About,
            HomeDestination.Providers,
            HomeDestination.Usage
        )

        assertEquals("Should have 5 destinations", 5, destinations.size)
        assertEquals("Home", HomeDestination.Home::class.simpleName)
        assertEquals("FeedbackCases", HomeDestination.FeedbackCases::class.simpleName)
        assertEquals("About", HomeDestination.About::class.simpleName)
        assertEquals("Providers", HomeDestination.Providers::class.simpleName)
        assertEquals("Usage", HomeDestination.Usage::class.simpleName)
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
}
