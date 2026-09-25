package com.lovebrain.app.ui

import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.viewmodel.KbListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.3 知识库页"现在是哪一格"的判据，穷举三个输入维度（8 格全跑）。
 *
 * 为什么单独测这个纯函数而不只测版式：替换前这一页根本没有 Error 格，
 * 读取抛异常时 `loaded` 永远为假 → 页面一直转圈。四格对不对，只有把
 * loaded × loadFailed × 列表长度 三个维度摊开才看得住；只测组件会漏掉判据本身。
 *
 * 判据与反馈案例页、供应商区同一副（Loading > Error > Empty > Content）。
 */
class KbScreenStateMappingTest {

    private val newKb = ScreenAction("NEW_KB_SENTINEL") {}
    private val retry = ScreenAction("RETRY_SENTINEL") {}

    private fun kb(name: String = "kb_a") = KnowledgeBase(name = name, displayName = "她")

    private fun mapping(
        loaded: Boolean,
        loadFailed: Boolean,
        count: Int
    ): ScreenState<KbListState> = kbScreenState(
        state = KbListState(
            knowledgeBases = List(count) { kb("kb_$it") },
            activeName = if (count > 0) "kb_0" else null,
            loaded = loaded,
            loadFailed = loadFailed
        ),
        emptyMessage = "EMPTY_MSG",
        errorMessage = "ERROR_MSG",
        newKb = newKb,
        retry = retry
    )

    // ═══════════ 第一维：还没读过 ═══════════

    @Test
    fun `not yet loaded is Loading whatever else says`() {
        // 列表里已经有东西、又标了失败，也不能盖过"第一次数据还没到"
        assertSame(
            ScreenState.Loading, mapping(loaded = false, loadFailed = false, count = 0)
        )
        assertSame(ScreenState.Loading, mapping(loaded = false, loadFailed = false, count = 2))
        assertSame(ScreenState.Loading, mapping(loaded = false, loadFailed = true, count = 0))
        assertSame(ScreenState.Loading, mapping(loaded = false, loadFailed = true, count = 2))
    }

    // ═══════════ 第二维：读过，但读失败了 ═══════════

    @Test
    fun `a failed read is Error even when a stale list is still in hand`() {
        val empty = mapping(loaded = true, loadFailed = true, count = 0)
        assertTrue("读失败、列表为空时必须落在 Error：$empty", empty is ScreenState.Error)
        assertEquals("ERROR_MSG", (empty as ScreenState.Error).message)
        assertSame(retry, empty.retry)

        // 关键那一格：失败时 knowledgeBases 是上一次的残留值，不许被当成"刚读到的内容"画出来
        val stale = mapping(loaded = true, loadFailed = true, count = 3)
        assertTrue("带着 3 个残留库就更不能画 Content：$stale", stale is ScreenState.Error)
    }

    // ═══════════ 第三维：读成功了，空 / 不空 ═══════════

    @Test
    fun `loaded with no libraries is Empty and carries the real action`() {
        val state = mapping(loaded = true, loadFailed = false, count = 0)
        assertTrue("读完为空要落 Empty：$state", state is ScreenState.Empty)
        assertEquals("EMPTY_MSG", (state as ScreenState.Empty).message)
        // §6.1：动作是一处操作，不是一行文字。空态少了这颗按钮就等于把用户关死在这页。
        assertNotNull("Empty 必须带一个动作", state.action)
        assertEquals("NEW_KB_SENTINEL", state.action?.label)
    }

    @Test
    fun `loaded with libraries is Content handing back the whole snapshot`() {
        val state = mapping(loaded = true, loadFailed = false, count = 2)
        assertTrue("有库要落 Content：$state", state is ScreenState.Content)
        val value = (state as ScreenState.Content).value
        // 交回整份快照而不是只有列表：卡片要不要显示"当前激活"取决于 activeName，
        // 只交 List 的话那一格就得在 UI 里另拿一份状态，判据立刻变成两处。
        assertEquals(2, value.knowledgeBases.size)
        assertEquals("kb_0", value.activeName)
    }

    // ═══════════ 反向：这把尺不是恒真的 ═══════════

    @Test
    fun `the four slots are distinguishable so the ruler is not vacuous`() {
        val slots = listOf(
            mapping(loaded = false, loadFailed = false, count = 0),
            mapping(loaded = true, loadFailed = true, count = 0),
            mapping(loaded = true, loadFailed = false, count = 0),
            mapping(loaded = true, loadFailed = false, count = 1)
        )
        // 上面四条若都在比同一个类型，全绿也说明不了什么。这里把"四格各是哪种、按什么顺序"
        // 钉成一张表：调换判据优先顺序会立刻红在这一格。
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
