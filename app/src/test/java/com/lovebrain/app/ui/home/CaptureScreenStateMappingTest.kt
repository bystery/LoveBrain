package com.lovebrain.app.ui.home

import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.viewmodel.SetupViewModel.CaptureApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.3 最后一格：捕获范围页"现在是哪一格"的判据，穷举（可空 × 查询词 × 匹配与否）。
 *
 * 这组用例的核心不是"有几格"，是**两件事不许合成一格**：
 * "读不出来"与"这台机器没有可授权的 App"以前是同一个 `emptyList()`，页面因此撒谎。
 * 知识库页那一格（账本 §20）有 Loading，这里刻意**没有**——两个数据源都是同步的，
 * 画一个永远不出现的转圈格就是装饰分支，所以最后一格不是四格齐全，是三格 + 一条写明为什么少。
 */
class CaptureScreenStateMappingTest {

    private val retry = ScreenAction("RETRY_SENTINEL") {}

    private fun app(pkg: String, label: String, blocked: Boolean = false) =
        CaptureApp(packageName = pkg, displayName = label, secondRejected = blocked)

    private fun mapping(
        candidates: List<CaptureApp>?,
        query: String = ""
    ) = captureScreenState(
        candidates = candidates,
        query = query,
        nothingToAuthorize = NOTHING_MSG,
        noResults = NO_RESULTS_MSG,
        scanFailed = FAILED_MSG,
        retry = retry
    )

    // ═══════════ 读不出来 ≠ 真的没有（这一格整笔改动就是为了这一条）═══════════

    @Test
    fun `a failed enumeration is Error while an empty list is Empty`() {
        val failed = mapping(null)
        assertTrue("null（读不出来）必须落 Error：$failed", failed is ScreenState.Error)
        assertEquals(FAILED_MSG, (failed as ScreenState.Error).message)
        assertSame(retry, failed.retry)

        val none = mapping(emptyList())
        assertTrue("空表（真的没有）必须落 Empty，不许借用错误语气：$none", none is ScreenState.Empty)
        assertEquals(NOTHING_MSG, (none as ScreenState.Empty).message)
    }

    @Test
    fun `a read failure outranks whatever the search box says`() {
        val state = mapping(null, query = "WECHAT")
        assertTrue(
            "读不出来时不该因为查询词非空就退化成「没搜索到」：$state",
            state is ScreenState.Error
        )
    }

    // ═══════════ 两个 Empty 各自说各自的话 ═══════════

    @Test
    fun `nothing-to-authorize and no-search-results are different sentences`() {
        val nothing = mapping(emptyList(), query = "whatever") as ScreenState.Empty
        val noResults = mapping(listOf(app("com.a", "A")), query = "zzz") as ScreenState.Empty
        assertEquals(NOTHING_MSG, nothing.message)
        assertEquals(NO_RESULTS_MSG, noResults.message)
        assertTrue(
            "两格共用一句话就等于告诉用户\"没搜索结果\"，而他其实整机没装可授权的 App",
            nothing.message != noResults.message
        )
    }

    // ═══════════ Content：过滤判据搬进来了，就得被钉住 ═══════════

    @Test
    fun `an empty query shows every candidate including the blocked ones`() {
        val all = listOf(app("com.a", "Alfred"), app("com.b", "Bear", blocked = true))
        val state = mapping(all) as ScreenState.Content
        assertEquals("二次拒绝的那行要继续显示（让用户看见为什么不能选，而不是假装列表里没它）",
            2, state.value.size)
    }

    @Test
    fun `the query matches display name and package name case-insensitively`() {
        val candidates = listOf(app("com.tencent.mm", "微信"), app("com.a", "Alfred"))
        assertEquals(
            "标签匹配要忽略大小写", listOf("com.a"),
            (mapping(candidates, "ALF") as ScreenState.Content).value.map { it.packageName }
        )
        assertEquals(
            "包名也要能搜到", listOf("com.tencent.mm"),
            (mapping(candidates, "tencent") as ScreenState.Content).value.map { it.packageName }
        )
    }

    @Test
    fun `a whitespace-only query is not a search`() {
        val candidates = listOf(app("com.a", "Alfred"), app("com.b", "Bear"))
        val state = mapping(candidates, "   ") as ScreenState.Content
        assertEquals("只有空格的查询词等于没搜，滤空整个列表是错的", 2, state.value.size)
    }

    // ═══════════ 反向：这把尺看得见差别 ═══════════

    @Test
    fun `the slots stay distinguishable`() {
        val slots = listOf(
            mapping(null),
            mapping(emptyList()),
            mapping(listOf(app("com.a", "A")), "zzz"),
            mapping(listOf(app("com.a", "A")))
        )
        assertEquals(
            listOf(
                ScreenState.Error::class,
                ScreenState.Empty::class,
                ScreenState.Empty::class,
                ScreenState.Content::class
            ),
            slots.map { it::class }
        )
        // 同是 Empty 的两格也要分得开（只看类型会漏掉它们共用文案这种退化）
        assertEquals(
            listOf(FAILED_MSG, NOTHING_MSG, NO_RESULTS_MSG),
            listOf(
                (slots[0] as ScreenState.Error).message,
                (slots[1] as ScreenState.Empty).message,
                (slots[2] as ScreenState.Empty).message
            )
        )
    }
}

private const val NOTHING_MSG = "CAPTURE_NOTHING_TO_AUTHORIZE"
private const val NO_RESULTS_MSG = "CAPTURE_NO_RESULTS"
private const val FAILED_MSG = "CAPTURE_SCAN_FAILED"
