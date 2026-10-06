package com.lovebrain.app.feature.roundcommit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 实际发送记录状态——异步写盘的真实 typed result（原来钉在 ViewModel 里，主人是这个持有者） */
enum class ActualSentState { IDLE, RECORDED, KB_NOT_FOUND, NO_KB, IO_ERROR }

/**
 * "我确认这条真的发出去了"这一族的持有者（复核 第5节第2条 第 6 步：搬**行为块**而不是搬字段）。
 *
 * 它管三件事：**upsert 身份**（同一 generation version 再次确认要替换旧记录，不是 append 第二条）、
 * **状态机**（五种结果各自的可见效果）、**adopt 计数只在第一次确认时加**。
 * 这三件事原来散在 ViewModel 里，其中两条曾经造成过真实的死路：
 *
 * 1. 枚举有五种结果，浮层那段接线只处理三种——`NO_KB` **是真会发生的**（未激活知识库时就走这支）；
 * 2. `StateFlow` 对同一个值**不再发射**，于是"第一次失败后浮层停在保存中，改两个字再确认、又失败"
 *    这一次没有任何跳变可观察，而取消与遮罩都是 `enabled = !saving` ⇒ 用户既关不掉也退不出。
 *
 * 修法因此不是一句"记得重置"，而是两条**结构约束**：
 * · 每次 [record] 一进来就先回 `IDLE`，让"这一尝试"必然是一次可观察的跳变——这条由本类负责；
 * · 五种结果必须被**穷尽**处理——那条判据住在面板那段接线的 `when`（编译器保证覆盖），
 *   而"有人写 `else -> {}` 满足编译器却吞掉一种结果"这一支由
 *   `ui/panel/RecordSentFailurePathTest`（已删）盯着：它**现算** `ActualSentState.entries`，
 *   再逐个去生产源码里找 `ActualSentState.<名字>`，加了新状态而忘了接线就当场红。
 *
 * 为什么读数靠注入的 lambda：`feature` 不许 import `data`/`viewmodel`（第5节第1条 包边界）。
 * "本轮上下文是什么"由 ViewModel 决定，这里只问三个值与两个写动作。
 */
class ActualSentRecorder(
    private val scope: CoroutineScope,
    /**
     * 本轮上下文；**null = 这一轮没有上下文**（与"有上下文但没激活库"是两种结果，
     * 前者 `IO_ERROR`、后者 `NO_KB` + 一条面板警告）。搬之前就是这样分岔的，保持不动。
     */
    private val readContext: () -> AttemptContext?,
    /** 本轮生成版本 ID；null = 这一轮还没有可绑定的版本 */
    private val readVersionKey: () -> String?,
    /** 关联候选的正文（按 identity key 从本轮结果里取；取不到就不写 candidate 字段） */
    private val readCandidateText: (identityKey: String?) -> String?,
    /** 落盘：`replaces == null` 追加，否则把旧那条替换掉。返回 false = 库不存在 */
    private val writeRecord: suspend (kbName: String, entry: String, replaces: String?) -> Boolean,
    private val onWarning: (String) -> Unit = {},
    private val onNotice: (String) -> Unit = {},
    private val onAdopted: () -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
    private val clock: () -> String = { com.lovebrain.app.util.TimeFmt.now() }
) {

    /** 一次确认所需的本轮信息 */
    data class AttemptContext(val kbName: String?)

    private val _state = MutableStateFlow(ActualSentState.IDLE)

    /** 面板订阅的就是这一条；除本类之外没有写入口 */
    val state: StateFlow<ActualSentState> = _state.asStateFlow()

    /** session 内已记录的 entry，按 versionKey upsert（不跨重启，与搬之前一致） */
    private val entries = HashMap<String?, String>()

    /** 用户关掉回执：只回 IDLE，不动 upsert 账（否则第二次确认会 append 出第二条） */
    fun dismiss() {
        _state.value = ActualSentState.IDLE
    }

    /**
     * 记一条"我（确认已发送）"。
     *
     * @param sentText 用户实际发出去的文本——空白按 IO 失败处理（没有内容可记）
     * @param identityKey 关联到哪张候选卡；不自动推断，必须由调用方明确传入
     * @return 这次尝试的任务句柄；被前置条件挡住时是一个**已完成**的 Job。
     *   交出来不是形式主义：取消这条性质只有从这里才看得见——把 `throw e` 改成空 catch
     *   之后，状态、警告、日志三个可见面全都"干净"，谁都看不出取消被吞了
     *   （`ActualSentRecorderTest` 的取消那一格钉的就是 `isCancelled`）。
     */
    fun record(sentText: String, identityKey: String? = null): Job {
        // 每一次尝试都先回 IDLE——见类 KDoc 第 2 条：StateFlow 同值不再发射就是那条死路
        _state.value = ActualSentState.IDLE

        if (sentText.isBlank()) {
            _state.value = ActualSentState.IO_ERROR
            return completedJob()
        }
        val ctx = readContext() ?: run {
            _state.value = ActualSentState.IO_ERROR
            return completedJob()
        }
        val kbName = ctx.kbName ?: run {
            onWarning("未激活知识库，无法记录已发送消息")
            _state.value = ActualSentState.NO_KB
            return completedJob()
        }

        val versionKey = readVersionKey()
        val entry = buildEntry(clock(), identityKey, versionKey, readCandidateText(identityKey), sentText)
        val existing = entries[versionKey]
        val isUpdate = existing != null

        return scope.launch {
            try {
                val success = writeRecord(kbName, entry, existing)
                if (success) {
                    // 只有第一次确认才计 adopt；同一 version 更新不重复 +1
                    if (!isUpdate) onAdopted()
                    entries[versionKey] = entry
                    _state.value = ActualSentState.RECORDED
                    onNotice(if (isUpdate) "已更新实际发送记录" else "已记录实际发送的消息")
                } else {
                    _state.value = ActualSentState.KB_NOT_FOUND
                    onWarning("本轮保存失败：知识库已被删除")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 取消不是失败：原样上抛，让调用方的取消语义成立（全仓取消审计盯的就是这一支）
                throw e
            } catch (e: Exception) {
                onError(e)
                _state.value = ActualSentState.IO_ERROR
                onWarning("记录发送失败，内容已保留，请重试")
            }
        }
    }

    /** 一个"这一尝试没启动任何异步工作"的句柄——用于前置条件就返回的三条路径 */
    private fun completedJob(): Job = Job().apply { complete() }

    /**
     * entry 的格式：一行机器读的注释头（时间 / 关联候选 / 版本 / 候选正文）+ 一行用户可见文本。
     *
     * 正文做 URL 编码并**截到 200 字符**——这一条是有意的：头注释不能塞进任意长正文，
     * 它只用于"当初是哪张候选"的线索，完整内容在消息行里。截断与编码都保持搬之前的样子。
     */
    private fun buildEntry(
        time: String,
        identityKey: String?,
        versionKey: String?,
        candidateText: String?,
        sentText: String
    ): String = buildString {
        append("<!-- sent:").append(time)
            .append(" linked:").append(identityKey ?: "null")
            .append(" version:").append(versionKey ?: "null")
            .append(" candidate:").append(
                candidateText?.let {
                    java.net.URLEncoder.encode(it, "UTF-8").take(200)
                } ?: "null"
            )
            .append(" -->\n")
        append("我（确认已发送）：").append(sentText.trim()).append("\n")
    }
}
