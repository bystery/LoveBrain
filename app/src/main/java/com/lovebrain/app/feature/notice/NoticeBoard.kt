package com.lovebrain.app.feature.notice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗通知位的唯一持有者：VM 只投递，面板只读那一条正在显示的。
 *
 * 三条通道的**内容来源与语义不变**——知识库后台操作的回执（[Channel.Knowledge]，如"经验提取完成"）、
 * 面板级临时警告（[Channel.Warning]，如"未配置模型"、"这轮没记入知识库"，以及输入变化那一类黄色提示）、
 * 五维向量重估摘要（[Channel.Vector]）。加上两张要用户点头的建议卡（[SuggestionKind]）。
 * 变的是展示方式：
 * 以前三条通道各自存一条、各自在面板上起表，于是一屏能挤两条、后到的还会盖掉正在读的那条；
 * 现在通知位**一次只输出一条**（[current]），其余按到达顺序排队，
 * 当前这条**真正结束**（时限到点 / 用户点原有的关闭 / 需确认项被确认或忽略）后下一条立即顶上。
 *
 * 排队只做在这一格，不抽通用通知框架：每一格（一条通道、或一张卡）至多占一格
 * （再写一次是刷新那一格，不是新增一条），队列长度因此有上限，
 * 不需要优先级表、数量徽标、"以后不再提示"这一类新东西。
 *
 * 计时怎么分：时限的**判据**在这一格（每条带着自己的 [Notice.autoDismissMillis] 与
 * [Notice.shownAtMillis]），**表**由调用方起——面板 `delay(remainingMillis(now))` 到点后回报 [dismiss]。
 * 关键是排队中的条目身上没有任何时间戳，只有被显示的那一刻才盖上，
 * 所以"在后台数着数着就过期了"这个形状在这里写不出来。
 *
 * 建议卡为什么只留一个身份：画像与阶段的**正文和确认业务**各自住在
 * `feature/profile/ProfileUpdateController` 与 `feature/stage/StageSuggestionStore`，
 * 这一格只记"现在轮到哪一张卡"（[SuggestionRef]），既不复制正文也不接确认代码——
 * 业务往队列里搬会把"这张卡改了什么"这件事变成第二个所有者。
 * 对应 Store 的状态变 null（确认成功 / 忽略 / 作废 / 切库）之后由调用方回报 [dismissSuggestion]。
 *
 * 为什么值得单独一格：VM 里有十处直接写这三条流的点，而"切库要把哪一族清空"只是其中一处顺带动作。
 * 漏掉的那几类不报错，只会让的提示赖在下一块屏幕上。现在"整族一起清"是
 * [dismissVolatileNotices] 一个动作，调用点只说意图。
 */
class NoticeBoard(
    /** 显示时刻的读数来源；生产用真实时间，测试注入假表。 */
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** 三条文字通道。用枚举而不是三个字段，是为了让"整族清"这件事有穷尽的定义。 */
    enum class Channel { Knowledge, Warning, Vector }

    /**
     * 两张需要用户点头的建议卡。它们不是文字通道：
     * [Profile] 的正文与确认归画像那一格，[Stage] 的归阶段那一格，
     * 这一格只把它们当作**排在同一通知位上的条目**——所以画像卡和一条回执不会同时出现，
     * 也不会出现"卡和回执各按各的表倒数"。
     */
    enum class SuggestionKind { Profile, Stage }

    /**
     * 队列引用的稳定建议身份。
     *
     * · [key] 是 Store 里那一份建议的身份（画像用 `suggestionId`，阶段用"库名+新阶段"这种稳定组合）。
     *   同一个 key 重复投递**不换号、不排第二份**，所以宿主每次重组都照常调 [showSuggestion] 是安全的；
     *   换了 key 才是新的一条。
     * · 这一格也保证"晚到的回调关不掉新来的卡"：[dismissSuggestion] 带着 key 时，
     *   身份不符就什么都不做。
     */
    data class SuggestionRef(val kind: SuggestionKind, val key: String)

    /**
     * 通知位上正在显示的那一条。
     *
     * · [id] 单调递增，面板把它当计时效果的 key，也是结束它的凭据：换下一条、或同一条被刷新都会换号，
     *   表因此重起；内容与时限都没变的重复投递不换号（那是"同一条还在读"，不是"新一条来了"）。
     * · [channel] 与 [suggestion] 二者恰有一个非空：前者是文字条，后者是建议卡。
     *   建议卡的 [message] 是**空的**——正文由宿主从对应 Store 取，这里不持有文案，
     *   免得同一条建议有两个版本；宿主看到 [isSuggestion] 为真时画卡片、不画那条一行文字。
     * · [autoDismissMillis] 为 null 表示这条需要用户确认、**不新增自动过期**，面板不该为它起表。
     * · [shownAtMillis] 是**显示那一刻**的读数，不是到达时刻。
     */
    data class Notice(
        val id: Long,
        val channel: Channel?,
        val message: String,
        val autoDismissMillis: Long?,
        val shownAtMillis: Long,
        val suggestion: SuggestionRef? = null
    ) {

        /** 这一格现在是建议卡（要画卡片那种带确认按钮的样子），不是一行文字。 */
        val isSuggestion: Boolean get() = suggestion != null

        /**
         * 此刻还剩多少毫秒该结束；null = 这条不自动过期。
         * 已经过点的报 0，不报负数——调用方拿它直接 `delay`，不该因为取到负数而多跑一圈。
         */
        fun remainingMillis(nowMillis: Long): Long? = autoDismissMillis?.let { total ->
            (total - (nowMillis - shownAtMillis)).coerceAtLeast(0L)
        }
    }

    /**
     * 排队中的一条：带着它该显示的内容与停留时长，**没有任何时间戳**。
     * 时间戳在顶到通知位那一刻才盖，所以排队期间没有任何东西可以倒计时。
     */
    private class Queued(
        val line: NoticeLine,
        val message: String,
        val autoDismissMillis: Long?,
        val suggestion: SuggestionRef?
    )

    private val _kbNotice = MutableStateFlow<String?>(null)
    private val _panelWarning = MutableStateFlow<String?>(null)
    private val _vectorUpdate = MutableStateFlow<String?>(null)
    private val _current = MutableStateFlow<Notice?>(null)

    /**
     * 知识库后台操作回执最近一次的文案（正在显示或正在排队；null = 这条通道没有内容）。
     * 只读出口，写只由这一格做；显示与否**不看这一颗**，看 [current]。
     */
    val knowledge: StateFlow<String?> = _kbNotice.asStateFlow()

    /** 面板级临时警告最近一次的文案（未配置引导 / 未记入提示 / 输入变化那类黄色提示） */
    val warning: StateFlow<String?> = _panelWarning.asStateFlow()

    /** 五维向量最近一次重估的变化摘要文案 */
    val vector: StateFlow<String?> = _vectorUpdate.asStateFlow()

    /** 通知位上唯一的那一条，null = 通知位空着。面板只读这一颗，其余两条流不再直接决定画面。 */
    val current: StateFlow<Notice?> = _current.asStateFlow()

    private val lock = Any()
    private val pending = mutableListOf<Queued>()
    private var nextId = 0L

    fun show(channel: Channel, message: String) {
        show(channel, message, defaultVisibleMillis(channel))
    }

    /**
     * 投递一条文字通知。
     *
     * · 通知位空着 → 立即显示（不空转、不等下一帧）。
     * · 别的格正在显示 → 排到队尾，按到达顺序，**不抢占正在读的那条**。
     * · **同一通道**再写 → 刷新那一条的文案与时限并重起它的表（保留原来"重写即覆盖"的语义），
     *   既不另起一条也不排队；正在排队的同理只刷新文案与时限、不改它已经排到的位置。
     *
     * [autoDismissMillis] 传 null 表示这条要用户确认、不自动过期，
     * 由调用方在确认或忽略之后回报 [dismiss]，通知位在此之前不会被后面到的顶掉。
     */
    fun show(channel: Channel, message: String, autoDismissMillis: Long?) {
        synchronized(lock) {
            deliverLocked(NoticeLine.Text(channel), message, autoDismissMillis, suggestion = null)
        }
    }

    /**
     * 让一张建议卡排进通知位；正文与确认都不走这里（见 [SuggestionRef]）。
     *
     * 建议**一律不自动过期**：它等的是用户点头或忽略，不是时钟。
     * 通知位空着就直接上卡，正被别条占着就按到达顺序排队，排队期间不起表也不倒计时。
     * 同一 [key] 重复调用（重组每一次都调）只算"这张卡还在"，不会插出第二份、也不会换号。
     */
    fun showSuggestion(kind: SuggestionKind, key: String) {
        synchronized(lock) {
            deliverLocked(NoticeLine.Card(kind), "", null, SuggestionRef(kind, key))
        }
    }

    /**
     * 建议对应的那一格结束——宿主在 Store 的状态变成 null 之后回报这一句
     * （确认成功、忽略、建议作废都算；**确认失败不算**，那份建议还在，这一格也就该继续占着通知位）。
     *
     * [key] 传了就只在"队列里那一格仍是这一份建议"时才摘掉：
     * 上一份建议的晚到回调不该把刚到的那张卡撤走，也撤不掉已经顶上来的一条。
     */
    fun dismissSuggestion(kind: SuggestionKind, key: String? = null) {
        val line = NoticeLine.Card(kind)
        synchronized(lock) {
            val active = _current.value
            if (active != null && lineOf(active) == line) {
                if (key != null && active.suggestion?.key != key) return
                finishLocked(line)
            } else {
                val index = pending.indexOfFirst { it.line == line }
                if (index < 0) return
                if (key != null && pending[index].suggestion?.key != key) return
                pending.removeAt(index)
            }
            promoteIfVacantLocked()
        }
    }

    /**
     * 抹掉某条通道的内容：正在显示的那条就此结束（下一条立即顶上），
     * 还在排队的那条在显示之前就被撤掉，因此它从没有过计时。
     */
    fun dismiss(channel: Channel) {
        synchronized(lock) {
            finishLocked(NoticeLine.Text(channel))
            promoteIfVacantLocked()
        }
    }

    /**
     * 只摘掉"就是这一句话"的那一项：投它的那个条件回正了（例如输入又与结果一致了），
     * 就把这一项从活动位或队列里撤掉，而**不动同一通道里别人刚投的别的警告**——
     * [dismiss] 是整条通道清，用它撤一条输入变化提示会顺手抹掉"未配置模型"那一类。
     * 文案不是现在这一项就什么都不做，于是晚到的那一次"条件不成立了"也关不掉别人的条目。
     */
    fun dismissMessage(channel: Channel, message: String) {
        val line = NoticeLine.Text(channel)
        synchronized(lock) {
            val active = _current.value
            if (active != null && lineOf(active) == line) {
                if (active.message != message) return
                finishLocked(line)
            } else {
                val index = pending.indexOfFirst { it.line == line && it.message == message }
                if (index < 0) return
                pending.removeAt(index)
            }
            promoteIfVacantLocked()
        }
    }

    /**
     * 结束**指定的那一条**——自动过期到点、用户点原有那个关闭、需确认项被确认或忽略都走这一句。
     *
     * 只认 [id]：id 不是此刻通知位上那一条就什么都不做。
     * 于是"上一条的表晚到了半秒"不可能把正在读的新一条关掉，
     * 面板那侧也无需担心自己的 delay 与用户的手慢撞上同一格。
     */
    fun dismiss(id: Long) {
        synchronized(lock) {
            val active = _current.value ?: return
            if (active.id != id) return
            finishLocked(lineOf(active) ?: return)
            promoteIfVacantLocked()
        }
    }

    /**
     * 无 id 的结束（原有的那一个出口）：结束此刻正在显示的那一条。
     * 带着 id 的调用方（到点的表、卡片回调）应该用 [dismiss]——那条才挡得住晚到的回调。
     */
    fun endCurrent() {
        synchronized(lock) {
            val active = _current.value ?: return
            finishLocked(lineOf(active) ?: return)
            promoteIfVacantLocked()
        }
    }

    /**
     * 换知识库时该清的这几格，连同它们在队列里的位置一起清。
     *
     * 含两张建议卡：画像建议与阶段建议说的都是**这一块库**，
     * 清掉它们是把"旧库那份不该出现在新库上"这条判据收在同一个动作里
     * （面板另有一层按库名的过滤，那一层管的是卡片本身，队列这一层管的是别让它排到新库的通知位上）。
     *
     * 不含 [Channel.Warning]：面板级警告说的是**这台设备**的配置状态，
     * 与切到哪块知识库无关，切库把它抹掉反而会让人以为配置好了。
     * 这条区分是搬进这一格之前 `refreshKnowledgeBases` 的实际行为，写下来是为了不让下一个人"顺手全清"。
     * 被清掉的正好是显示中的那一条时，排在其后的（包括留下来的警告）立即顶上。
     */
    fun dismissVolatileNotices() {
        synchronized(lock) {
            finishLocked(NoticeLine.Text(Channel.Knowledge))
            finishLocked(NoticeLine.Text(Channel.Vector))
            finishLocked(NoticeLine.Card(SuggestionKind.Profile))
            finishLocked(NoticeLine.Card(SuggestionKind.Stage))
            promoteIfVacantLocked()
        }
    }

    /** 投递的统一入口：刷新 / 排队 / 立即顶上，三条分支对文字通道与建议卡是同一条链。 */
    private fun deliverLocked(
        line: NoticeLine,
        message: String,
        autoDismissMillis: Long?,
        suggestion: SuggestionRef?
    ) {
        // 文字通道留一份"最近文案"只读出口（各通道原来那三条流）；建议卡的正文不归这一格。
        if (line is NoticeLine.Text) writeSlot(line.channel, message)
        val active = _current.value
        if (active != null && lineOf(active) == line) {
            // 同一格再写 = 刷新那一条并重起它的表（保留原来"重写即覆盖"的语义），不另起一条、不排队；
            // 内容与时限都没变的重复投递不算"新一条"——正在读的那条不该因此续命。
            if (active.message != message || active.autoDismissMillis != autoDismissMillis ||
                active.suggestion != suggestion
            ) {
                _current.value = Notice(
                    id = nextId++,
                    channel = (line as? NoticeLine.Text)?.channel,
                    message = message,
                    autoDismissMillis = autoDismissMillis,
                    shownAtMillis = clock(),
                    suggestion = suggestion
                )
            }
            return
        }
        // 一格在队列里至多占一位：位置按首次到达，内容取最新
        val fresh = Queued(line, message, autoDismissMillis, suggestion)
        val index = pending.indexOfFirst { it.line == line }
        if (index >= 0) {
            pending[index] = fresh
        } else {
            pending.add(fresh)
        }
        // 通知位空着（首条投递、或上一条结束时队列确实空着）就立即顶上，不空转到下一次投递
        promoteIfVacantLocked()
    }

    /** 抹掉一格的持有：显示中的清空，排队中的摘掉；补位由调用方在同一段锁里统一做一次。 */
    private fun finishLocked(line: NoticeLine) {
        if (line is NoticeLine.Text) writeSlot(line.channel, null)
        pending.removeAll { it.line == line }
        if (_current.value?.let { lineOf(it) } == line) _current.value = null
    }

    /** 队首顶上一条；已经有货在显示中就什么都不做（这条不变式让"一屏一条"只剩一处判据）。 */
    private fun promoteIfVacantLocked() {
        if (_current.value != null || pending.isEmpty()) return
        val head = pending.removeAt(0)
        _current.value = Notice(
            id = nextId++,
            channel = (head.line as? NoticeLine.Text)?.channel,
            message = head.message,
            autoDismissMillis = head.autoDismissMillis,
            shownAtMillis = clock(),
            suggestion = head.suggestion
        )
    }

    /** 一条通知归哪一格：建议卡认 kind，文字条认 channel。 */
    private fun lineOf(notice: Notice): NoticeLine? =
        notice.suggestion?.let { NoticeLine.Card(it.kind) } ?: notice.channel?.let { NoticeLine.Text(it) }

    private fun slotFlow(channel: Channel): MutableStateFlow<String?> = when (channel) {
        Channel.Knowledge -> _kbNotice
        Channel.Warning -> _panelWarning
        Channel.Vector -> _vectorUpdate
    }

    private fun writeSlot(channel: Channel, message: String?) {
        slotFlow(channel).value = message
    }

    /** 各通道在通知位上的原本停留时长（原先是面板里的三颗私有常量，搬到这里由队列自己带着）。 */
    companion object {
        const val KNOWLEDGE_AUTO_DISMISS_MS = 3_000L
        const val WARNING_AUTO_DISMISS_MS = 3_000L
        const val VECTOR_AUTO_DISMISS_MS = 5_000L

        private fun defaultVisibleMillis(channel: Channel): Long? = when (channel) {
            Channel.Knowledge -> KNOWLEDGE_AUTO_DISMISS_MS
            Channel.Warning -> WARNING_AUTO_DISMISS_MS
            Channel.Vector -> VECTOR_AUTO_DISMISS_MS
        }
    }
}

/**
 * 一格的归属：一条文字通道，或一张建议卡。
 * 用它而不是"每样一个字段"来表达"谁和谁算同一条"，于是"每格至多一条"这件事只有一处定义。
 * 文件私有——它是这一格的排队细节，不是给宿主看的接口（宿主只认 [NoticeBoard.Notice] 那几员）。
 */
private sealed interface NoticeLine {
    data class Text(val channel: NoticeBoard.Channel) : NoticeLine
    data class Card(val kind: NoticeBoard.SuggestionKind) : NoticeLine
}
