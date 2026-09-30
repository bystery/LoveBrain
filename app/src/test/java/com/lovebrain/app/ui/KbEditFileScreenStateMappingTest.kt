package com.lovebrain.app.ui

import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.3：编辑页"这一篇现在是哪一格"的判据，四个维度全摊开穷举
 * （`loaded` × `readFailed` × 正文空不空 × 预览/编辑 ⇒ 16 行一行不落）。
 *
 * 为什么单独测这处纯函数、不只测版式：替换前这一屏**没有 Error 格**——那一次读抛异常时
 * `loaded` 永远是假，页面一路转圈（异常本身还从 `LaunchedEffect` 穿到组合作用域）。
 * 四格对不对，只有把四个维度摊开才看得住，与 `KbScreenStateMappingTest` 同一副理由。
 *
 * 优先顺序与同族两页一致（Loading > Error > Empty > Content）；唯一多出来的条件是
 * Empty 只在预览那一档成立，最后专门有一格钉它——那种"少一格"的地方最容易被顺手抹平。
 */
class KbEditFileScreenStateMappingTest {

    private val retry = ScreenAction(RETRY_SENTINEL) {}

    private fun mapping(
        loaded: Boolean,
        readFailed: Boolean,
        blank: Boolean,
        isPreview: Boolean
    ): ScreenState<String> = kbEditFileScreenState(
        loaded = loaded,
        readFailed = readFailed,
        text = if (blank) BLANK_TEXT else REAL_TEXT,
        isPreview = isPreview,
        emptyMessage = EMPTY_MSG,
        errorMessage = ERROR_MSG,
        retry = retry
    )

    /** 行的枚举序：loaded 在外、isPreview 在内，与下面那张期望表同序 */
    private fun allSixteen(): List<ScreenState<String>> {
        val rows = ArrayList<ScreenState<String>>()
        for (loaded in listOf(false, true)) {
            for (readFailed in listOf(false, true)) {
                for (blank in listOf(true, false)) {
                    for (isPreview in listOf(true, false)) {
                        rows += mapping(loaded, readFailed, blank, isPreview)
                    }
                }
            }
        }
        return rows
    }

    // ═══════════ 全表：16 行各自落在哪一格 ═══════════

    @Test
    fun `all sixteen combinations land in the slot the priority order dictates`() {
        val expected = listOf(
            // loaded=false（前 8 行）：读还没回来，其余三维说什么都不算
            "Loading", "Loading", "Loading", "Loading",
            "Loading", "Loading", "Loading", "Loading",
            // loaded=true, readFailed=false（接着 4 行）：blank×isPreview
            "Empty",     // 空正文 + 预览 ⇒ 这屏唯一的 Empty
            "Content",   // 空正文 + 编辑 ⇒ 输入框要继续画，不许被空态图换掉
            "Content",   // 有正文 + 预览
            "Content",   // 有正文 + 编辑
            // loaded=true, readFailed=true（最后 4 行）：Error 吃掉空与内容两档
            "Error", "Error", "Error", "Error"
        )
        assertEquals(expected, allSixteen().map { it::class.simpleName })
    }

    // ═══════════ 失败 ≠ 空 ≠ 还在读 ═══════════

    @Test
    fun `a failed read is Error even when a stale draft is still in hand`() {
        val failed = mapping(loaded = true, readFailed = true, blank = false, isPreview = true)
        assertTrue("带着上一轮的残留正文就更不许画 Content：$failed", failed is ScreenState.Error)
        assertEquals(ERROR_MSG, (failed as ScreenState.Error).message)
        // §6.3：错误态必须带重试入口，没有出口的错误态等于把用户关死在这一屏
        assertSame("Error 必须原样交出调用方给的那颗重试", retry, failed.retry)

        val blankFailed = mapping(loaded = true, readFailed = true, blank = true, isPreview = true)
        assertTrue(
            "读失败时 drafts 是空的，判据若按'空不空'先判就会把失败报成'这篇没有内容'：$blankFailed",
            blankFailed is ScreenState.Error
        )
    }

    @Test
    fun `the read that has not come back outranks a failure flag`() {
        // 重试正在进行时两件事同时为真（上一轮失败过、这一轮还没回来）：
        // 这时候画"读不出来"是在用户已经点过重试之后再说一遍坏消息，该画的是转圈。
        val duringRetry = mapping(loaded = false, readFailed = true, blank = false, isPreview = true)
        assertSame(ScreenState.Loading, duringRetry)
    }

    // ═══════════ 这一格是这屏与同族两页唯一不同的地方 ═══════════

    @Test
    fun `typing into an empty document is not answered with an empty-state picture`() {
        val editing = mapping(loaded = true, readFailed = false, blank = true, isPreview = false)
        assertTrue(
            "编辑态下空正文要当 Content 交回，好让那颗输入框继续画：$editing",
            editing is ScreenState.Content
        )
        assertEquals(BLANK_TEXT, (editing as ScreenState.Content).value)

        val previewing = mapping(loaded = true, readFailed = false, blank = true, isPreview = true)
        assertTrue("同一份空正文，预览那一档才是 Empty：$previewing", previewing is ScreenState.Empty)
    }

    @Test
    fun `an empty preview carries the sentence and no action`() {
        val state = mapping(loaded = true, readFailed = false, blank = true, isPreview = true)
        assertTrue("$state 应为 Empty", state is ScreenState.Empty)
        assertEquals(EMPTY_MSG, (state as ScreenState.Empty).message)
        // 这一格**故意**不带动作，与知识库页那颗「新建知识库」不同：
        // 「编辑」那颗 toggle 画在状态件外面、四格都在，空态那句指的就是它。
        // 再补一颗同义按钮等于给同一件事两个入口。钉住这条，是为了让下一个
        // 想在这里加动作的人先读到理由，而不是顺手把两句拼在一起。
        assertNull("这一格的动作入口在卡上排着，不在状态里再画一颗", state.action)
    }

    // ═══════════ Content 交回的是判过的那一份，不是第二份状态 ═══════════

    @Test
    fun `content hands back exactly the text that was read`() {
        val state = mapping(loaded = true, readFailed = false, blank = false, isPreview = true)
        assertTrue("$state 应为 Content", state is ScreenState.Content)
        assertEquals(REAL_TEXT, (state as ScreenState.Content).value)
    }

    // ═══════════ 反向：这把尺不是恒真的 ═══════════

    @Test
    fun `the four slots are distinguishable so the ruler is not vacuous`() {
        val slots = listOf(
            mapping(loaded = false, readFailed = false, blank = false, isPreview = true),
            mapping(loaded = true, readFailed = true, blank = false, isPreview = true),
            mapping(loaded = true, readFailed = false, blank = true, isPreview = true),
            mapping(loaded = true, readFailed = false, blank = false, isPreview = true)
        )
        assertEquals(
            listOf(
                ScreenState.Loading::class,
                ScreenState.Error::class,
                ScreenState.Empty::class,
                ScreenState.Content::class
            ),
            slots.map { it::class }
        )
    }
}

private const val RETRY_SENTINEL = "KB_EDIT_RETRY_SENTINEL"
private const val EMPTY_MSG = "KB_EDIT_EMPTY_MSG"
private const val ERROR_MSG = "KB_EDIT_ERROR_MSG"
private const val BLANK_TEXT = "   "
private const val REAL_TEXT = "她在加班"
