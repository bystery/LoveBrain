package com.lovebrain.app.feature.counseling

import com.lovebrain.app.model.CounselingChunk
import com.lovebrain.app.model.CounselingEnded
import com.lovebrain.app.model.CounselingFailed
import com.lovebrain.app.model.CounselingFirstToken
import com.lovebrain.app.model.CounselingResult
import com.lovebrain.app.model.CounselingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CounselingStore 自己的合同（复核 §5.2 第 4 步搬出来的那三件事）。
 *
 * 与 ReplyStoreTest 同构：只测"状态持有者"本身，不开 ViewModel、不需要 Koin、
 * 不需要 Android。正因为如此，`while (true)` 那处常驻协程缺陷在搬家当天就当场
 * 暴露（不写这一格，它在 VM 里永远躲在 viewModelScope 的死亡后面）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CounselingStoreTest {

    private class Recorder {
        val effects = mutableListOf<CounselingStore.Effect>()
        operator fun invoke(e: CounselingStore.Effect) { effects.add(e) }
    }

    private fun result(
        requestId: String = "r1",
        kbName: String? = "恋爱记录",
        userMessage: String = "我今天忍住了没发消息",
        replyText: String = "忍得住也是一种推进",
        analysisText: String = "## 观察\n你在回避上有了新策略"
    ) = CounselingResult(requestId, replyText, analysisText, kbName, userMessage)

    /** 相邻增量合并成一次发布，且发布前不进界面 */
    @Test
    fun `adjacent chunks are coalesced into one published update`() = runTest {
        val store = CounselingStore(scope = this, flushIntervalMs = 50L)
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r1")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "你")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "做得")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "很好")))

        assertEquals("没到节拍前不得发布", "", store.uiState.value.streaming)

        advanceTimeBy(60L)
        runCurrent()
        assertEquals("你做得很好", store.uiState.value.streaming)
    }

    /**
     * 发完就该没有常驻协程——这是给 `while (true)` 修复上的第二把锁。
     *
     * 同一处缺陷在回复与谈心两条链上各写了一遍（DRY 的反面不是"重复两次就自动修好"），
     * 所以两格都要有：只测 ReplyStore 的话，谈心的循环改回去没人会发现。
     */
    @Test
    fun `the flush loop ends on its own once the buffer is drained`() = runTest {
        val host = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val store = CounselingStore(scope = host, flushIntervalMs = 50L)
            store.accept(CounselingStore.Intent.Apply(CounselingStarted("r1")))
            store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "发得出去的话")))

            advanceTimeBy(60L)
            runCurrent()
            assertEquals("发得出去的话", store.uiState.value.streaming)

            val left = host.coroutineContext[kotlinx.coroutines.Job]!!.children.toList()
            assertTrue("缓冲已空，却还有活着的子协程：$left", left.isEmpty())
        } finally {
            host.cancel()
        }
    }

    /** 被取代请求的事件整条丢弃：既不能写界面，也不能触发落盘 */
    @Test
    fun `events from a superseded request change neither state nor effects`() = runTest {
        val recorder = Recorder()
        val store = CounselingStore(
            scope = this,
            isCurrentRequest = { it == "new" },
            flushIntervalMs = 50L,
            onEffect = { recorder(it) }
        )
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("new")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("old", "上一轮的残留")))
        advanceTimeBy(60L)
        runCurrent()
        store.accept(CounselingStore.Intent.Apply(result("old")))

        assertEquals("", store.uiState.value.streaming)
        assertNull(store.uiState.value.result)
        assertTrue("旧请求的结果不得触发日志/持久化", recorder.effects.isEmpty())
    }

    /**
     * 已经压在缓冲区里的残句，易主之后也不许再发布。
     *
     * 入口那道闸只拦得住"新到的事件"，拦不住"上一轮到一半、这轮已经易主"的存货；
     * 发布前必须再核一次主人（ReplyStore 同一位置本来就有这道核对，搬家时补齐）。
     * 反例：删掉 flushPending 里的 isCurrentRequest 核对 → 残句写进新轮界面，本条当场红。
     */
    @Test
    fun `a buffered half sentence from a request that just lost the slot is never published`() = runTest {
        var owner = "r1"
        val store = CounselingStore(
            scope = this,
            isCurrentRequest = { it == owner },
            flushIntervalMs = 50L
        )
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r1")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "上一轮说到一半")))
        // 还没到节拍，前台已经换给了新一轮
        owner = "r2"
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r2", "这一轮的话")))

        advanceTimeBy(60L)
        runCurrent()
        assertEquals("旧轮残句不许出现在新轮面板上", "这一轮的话", store.uiState.value.streaming)
    }

    /**
     * 落盘用的四件事全部来自**事件本身**。
     *
     * 这条以前只是 reducer 上的一句注释（"不回读实时状态"）。现在它是合同：
     * 生成期间切了知识库、改了倾诉草稿，日志仍记在这轮真正归属的那个库上。
     */
    @Test
    fun `persist effect carries the identity frozen in the event`() = runTest {
        val recorder = Recorder()
        val store = CounselingStore(scope = this, onEffect = { recorder(it) })
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r7")))
        store.accept(
            CounselingStore.Intent.Apply(
                CounselingResult(
                    requestId = "r7",
                    replyText = "回复正文",
                    analysisText = "分析正文",
                    kbName = "发起时的那个库",
                    userMessage = "发起时说的那句话"
                )
            )
        )

        val persist = recorder.effects.filterIsInstance<CounselingStore.Effect.PersistResult>()
        assertEquals("一次结果只应发一条 PersistResult", 1, persist.size)
        val e = persist.single()
        assertEquals("发起时的那个库", e.kbName)
        assertEquals("发起时说的那句话", e.userMessage)
        assertEquals("回复正文", e.replyText)
        assertEquals("分析正文", e.analysisText)
    }

    /** 没有可用知识库时 kbName 为 null：仍然落 prefs，只是不写日志（由 VM 判空） */
    @Test
    fun `a result with no knowledge base still emits the persist effect with null kb`() = runTest {
        val recorder = Recorder()
        val store = CounselingStore(scope = this, onEffect = { recorder(it) })
        store.accept(CounselingStore.Intent.Apply(result("r1", kbName = null)))

        val e = recorder.effects.filterIsInstance<CounselingStore.Effect.PersistResult>().single()
        assertNull("库为空要原样交出去，让写日志那一步自己决定跳过", e.kbName)
        assertEquals("我今天忍住了没发消息", e.userMessage)
    }

    /**
     * 结果落定时不清流式正文。
     *
     * CounselingResult 比 CounselingEnded 早半拍，而面板在 isCounseling 期间显示的就是
     * streaming。提前清空会让最后一段正文闪成"还在等待"占位——一次肉眼可见的回退。
     */
    @Test
    fun `a settled result leaves the streamed text standing until the round ends`() = runTest {
        val store = CounselingStore(scope = this, flushIntervalMs = 50L)
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r1")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "已经说出口的一半")))
        advanceTimeBy(60L)
        runCurrent()
        store.accept(CounselingStore.Intent.Apply(result("r1", replyText = "已经说出口的一半")))

        assertEquals("已经说出口的一半", store.uiState.value.streaming)
        assertEquals("已经说出口的一半", store.currentResult)

        store.accept(CounselingStore.Intent.Apply(CounselingEnded("r1")))
        assertEquals("收尾事件才负责收走流式位", "", store.uiState.value.streaming)
    }

    /**
     * 收尾时缓冲里剩的半截要一次处理干净：既不能丢在缓冲区里"迟到复活"，
     * 也不能让合并循环在轮次关闭后还往界面上写字。
     *
     * 反例（这条真正抓得住的写法）：把 `flushPending()` 从 CounselingEnded 分支删掉，
     * 于是文本在下一个节拍被写进已经关闭的轮次 → 断言当场红。
     */
    @Test
    fun `no text appears after the round has ended`() = runTest {
        val store = CounselingStore(scope = this, flushIntervalMs = 50L)
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r1")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "最后一句")))
        // 故意不推进虚拟时间：这句还压在缓冲里
        store.accept(CounselingStore.Intent.Apply(CounselingEnded("r1")))
        assertEquals("", store.uiState.value.streaming)

        advanceTimeBy(200L)
        runCurrent()
        assertEquals("轮次关闭后不得再长出流式正文", "", store.uiState.value.streaming)
    }

    /** 失败也先冲缓冲：半截正文要留在界面上，用户才知道模型说到哪儿断了 */
    @Test
    fun `failure keeps the partially streamed text and records the reason`() = runTest {
        val store = CounselingStore(scope = this, flushIntervalMs = 50L)
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r1")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "断在这里")))
        store.accept(CounselingStore.Intent.Apply(CounselingFailed("r1", "网络中断")))

        assertEquals("断在这里", store.uiState.value.streaming)
        assertEquals("网络中断", store.uiState.value.error)
    }

    /** 首字耗时原样转出去（跨 feature 的统计口径，不在 store 里换算） */
    @Test
    fun `first token timing is forwarded unchanged`() = runTest {
        val recorder = Recorder()
        val store = CounselingStore(scope = this, onEffect = { recorder(it) })
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r1")))
        store.accept(CounselingStore.Intent.Apply(CounselingFirstToken("r1", 1234L)))

        val observed = recorder.effects.filterIsInstance<CounselingStore.Effect.FirstTokenObserved>()
        assertEquals(1, observed.size)
        assertEquals(1234L, observed.single().elapsedMs)
    }

    /** 冷启动恢复：只填 result，不再落一次盘 */
    @Test
    fun `restore fills the result without re-persisting`() = runTest {
        val recorder = Recorder()
        val store = CounselingStore(scope = this, onEffect = { recorder(it) })
        store.accept(CounselingStore.Intent.Restore("上次那轮的回答"))

        assertEquals("上次那轮的回答", store.uiState.value.result)
        assertEquals("", store.uiState.value.streaming)
        assertTrue("从盘上读回来的东西不该再写回盘", recorder.effects.isEmpty())
    }

    /**
     * 清空：界面三样一起走，缓冲里没发布的半截也一起丢。
     *
     * 关键在于"清空之后还压着一句没发布的"——只把已发布的内容抹掉是测不出
     * 漏掉 `discard()` 的写法的：那个合并循环会在下一个节拍把存货重新写进
     * 刚清空的界面（本条一开始就是这么一条恒真断言，补上这句才真的红得起来）。
     */
    @Test
    fun `clear drops state and unpublished chunks together`() = runTest {
        val store = CounselingStore(scope = this, flushIntervalMs = 50L)
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r1")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "已经发出去的一半")))
        advanceTimeBy(60L)
        runCurrent()
        store.accept(CounselingStore.Intent.Apply(result("r1")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "还压在缓冲里的一半")))
        store.accept(CounselingStore.Intent.Clear)

        advanceTimeBy(200L)
        runCurrent()
        assertEquals(CounselingStore.UiState(), store.uiState.value)
        assertNull(store.currentResult)
    }

    /**
     * 新一轮开始必须从零开始：上一轮的回答与错误都不能留在界面上。
     *
     * `Intent.Fail` 也在这里一并测——它服务的是"没走到模型就失败"（例如 stop 时
     * 本轮还没有结果），由 ViewModel 判断要不要报"已手动停止"。
     */
    @Test
    fun `a new round starts blank and can be failed without a model call`() = runTest {
        val store = CounselingStore(scope = this)
        store.accept(CounselingStore.Intent.Apply(result("r1")))
        store.accept(CounselingStore.Intent.Fail("已手动停止"))
        assertEquals("已手动停止", store.uiState.value.error)

        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r2")))
        assertNull("新一轮还挂着上一轮的回答", store.uiState.value.result)
        assertNull(store.uiState.value.error)
        assertEquals("", store.uiState.value.streaming)
    }

    /**
     * 手动停止只丢"还没发出去的半句"，不动已经落定的结果。
     *
     * 老代码里 stop 之后还要按 `result == null` 决定要不要报"已手动停止"，
     * 所以结果必须活过这次丢弃——否则用户停止前那一轮已经拿到的回复会被抹掉。
     */
    @Test
    fun `discarding pending chunks drops unpublished text but keeps the settled result`() = runTest {
        val store = CounselingStore(scope = this, flushIntervalMs = 50L)
        store.accept(CounselingStore.Intent.Apply(CounselingStarted("r1")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "已经发出去的一半")))
        advanceTimeBy(60L)
        runCurrent()
        store.accept(CounselingStore.Intent.Apply(result("r1", replyText = "已经发出去的一半")))
        store.accept(CounselingStore.Intent.Apply(CounselingChunk("r1", "迟到半句")))

        store.accept(CounselingStore.Intent.DiscardPendingChunks)
        advanceTimeBy(200L)
        runCurrent()

        assertEquals("已经发出去的一半", store.uiState.value.streaming)
        assertEquals("已经发出去的一半", store.currentResult)
    }
}
