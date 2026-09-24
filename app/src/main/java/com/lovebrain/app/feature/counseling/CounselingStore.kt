package com.lovebrain.app.feature.counseling

import com.lovebrain.app.model.CounselingChunk
import com.lovebrain.app.model.CounselingEnded
import com.lovebrain.app.model.CounselingEvent
import com.lovebrain.app.model.CounselingFailed
import com.lovebrain.app.model.CounselingFirstToken
import com.lovebrain.app.model.CounselingResult
import com.lovebrain.app.model.CounselingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 谈心（长文倾诉回复）这条链的状态持有者（§5.2 第 4 步：接管流式正文与日志命令）。
 *
 * 流式正文的合并缓冲与 [com.lovebrain.app.feature.reply.ReplyStore] 是同一类代码，
 * 也带着**同一个缺陷**：原来这里是 `while (true) { delay(50); flush() }` —— 一旦有
 * chunk 进来就永远每 50ms 醒一次，只有外部记得调"取消"才停。搬进来时一并改成
 * "有货要发才等下一班"，并留一格测试锁住（ReplyStore 那把是同样的修法）。
 *
 * "日志命令"指 `CounselingResult` 到达时要做的那两件事（写 counseling_log.md、
 * 把结果存进 prefs）。它们不在 store 里做：store 只把**事件里冻结的** kbName /
 * 倾诉文本 / 回复 / 分析文字随 Effect 交出去，落盘仍由 ViewModel 完成。
 * 不回读实时状态这一点是原来就有的正确行为（跨库/改草稿时不串写），
 * 现在它变成一条可以断言的合同而不是注释里的一句话。
 */
class CounselingStore(
    private val scope: CoroutineScope,
    private val isCurrentRequest: (String) -> Boolean = { true },
    private val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
    private val onEffect: (Effect) -> Unit = {}
) {

    data class UiState(
        val streaming: String = "",
        val result: String? = null,
        val error: String? = null
    )

    sealed interface Intent {
        data class Apply(val event: CounselingEvent) : Intent
        /** 用户手动停止时丢掉未发布的半截 */
        data object DiscardPendingChunks : Intent
        /**
         * 把这一整轮抹掉（流式正文、结果、错误一起）。
         *
         * 对应面板上的"清空"（`clearCounselingAll`）。SecurePrefs 那一份不在这里管——
         * store 不碰盘，由 ViewModel 在同一个动作里清。
         */
        data object Clear : Intent
        /** 没走到模型就失败（没有知识库等） */
        data class Fail(val message: String) : Intent

        /**
         * 冷启动把上一轮的回复正文放回界面（SecurePrefs 里存的那份）。
         *
         * 只填 result：不发 [Effect.PersistResult]（它本来就是从盘上读出来的，
         * 再落一次盘等于自己抄自己），也不碰流式缓冲与错误位。
         */
        data class Restore(val result: String) : Intent
    }

    sealed interface Effect {
        /**
         * 一次完整回复落定，需要写日志与持久化。
         *
         * 四个字段全部来自**事件本身**（发起时冻结），不是回读当前状态：
         * 生成期间切了知识库或改了草稿，日志仍应记在这轮真正归属的那个库上。
         */
        data class PersistResult(
            /** 这轮归属的知识库；发起时没有可用库则为 null（此时不写日志，但仍落 prefs） */
            val kbName: String?,
            val userMessage: String,
            val replyText: String,
            val analysisText: String
        ) : Effect

        data class FirstTokenObserved(val elapsedMs: Long) : Effect
    }

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    val currentResult: String? get() = _ui.value.result

    private val pendingChunk = StringBuilder()
    private var pendingChunkRequestId: String? = null
    private var flushJob: Job? = null

    fun accept(intent: Intent) {
        when (intent) {
            is Intent.Apply -> onEvent(intent.event)
            Intent.DiscardPendingChunks -> discard()
            Intent.Clear -> { discard(); _ui.value = UiState() }
            is Intent.Fail -> _ui.value = _ui.value.copy(error = intent.message)
            is Intent.Restore -> _ui.value = _ui.value.copy(result = intent.result)
        }
    }

    private fun onEvent(event: CounselingEvent) {
        if (!isCurrentRequest(event.requestId)) return
        when (event) {
            is CounselingStarted -> {
                discard()
                _ui.value = UiState()
            }

            is CounselingChunk -> {
                // 换人了：先把上一家的存货结掉（它会带着自己的 requestId 被作废或发布），
                // 再收下这一家的增量——两条链的存货不许混进同一次发布。
                if (pendingChunkRequestId != null && pendingChunkRequestId != event.requestId) {
                    flushPending()
                }
                pendingChunkRequestId = event.requestId
                pendingChunk.append(event.text)
                scheduleFlush()
            }

            is CounselingFirstToken -> onEffect(Effect.FirstTokenObserved(event.elapsedMs))

            is CounselingResult -> {
                flushPending()
                // 这里**不**清 streaming：CounselingResult 到得比 CounselingEnded 早半拍，
                // 而面板在 isCounseling 期间显示的就是 streaming（result 要等租约释放后才接管）。
                // 提前清空会让最后一段正文闪成"还在等待"占位——一次肉眼可见的回退。
                _ui.value = _ui.value.copy(result = event.replyText, error = null)
                onEffect(
                    Effect.PersistResult(
                        kbName = event.kbName,
                        userMessage = event.userMessage,
                        replyText = event.replyText,
                        analysisText = event.analysisText
                    )
                )
            }

            is CounselingFailed -> {
                flushPending()
                _ui.value = _ui.value.copy(error = event.message)
            }

            is CounselingEnded -> {
                flushPending()
                _ui.value = _ui.value.copy(streaming = "")
            }
        }
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            // 循环存续条件就是它该做的事还有剩（见文件头：这里原来是 while (true)）。
            while (pendingChunk.isNotEmpty() && pendingChunkRequestId != null) {
                delay(flushIntervalMs)
                flushPending()
            }
        }
    }

    /**
     * 发布缓冲区里的增量。
     *
     * 发布前再核对一次主人：`onEvent` 入口那道闸只拦得住"新到的事件"，拦不住
     * "上一轮到一半、这轮已经易主"的存货。不核对就会把旧轮残句写进新轮的面板——
     * [com.lovebrain.app.feature.reply.ReplyStore] 里同一个位置是有这道核对的，
     * 搬家时把这条补齐，两条链用同一套语法。
     */
    private fun flushPending() {
        val requestId = pendingChunkRequestId ?: return
        if (pendingChunk.isEmpty()) return
        val text = pendingChunk.toString()
        pendingChunk.setLength(0)
        if (!isCurrentRequest(requestId)) return
        _ui.value = _ui.value.copy(streaming = _ui.value.streaming + text)
    }

    private fun discard() {
        flushJob?.cancel()
        flushJob = null
        pendingChunkRequestId = null
        pendingChunk.setLength(0)
    }

    companion object {
        const val DEFAULT_FLUSH_INTERVAL_MS = 50L
    }
}
