package com.lovebrain.app.domain

import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.OngoingItem
import com.lovebrain.app.model.SceneFact
import com.lovebrain.app.util.L
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Round Commit Journal——可恢复的轮次提交事务。
 *
 * ## 为什么存在
 * 一轮对话要写 6 个互不原子的事物（话题归档、话题标签、recent.md、scene.md、plan.md、轮次计数）。
 * 进程在任意两步之间被杀，重启后必须既不丢写入、也不重复写入。
 *
 * ## 事务模型（WAL + 每投影水位）
 * 1. **PREPARED**：完整事件（含全部投影所需的类型化数据）先落 journal，再动任何 target。
 * 2. **WRITING**：真正开始写 target 前显式改写阶段，使"崩溃在 PREPARED 与第一个 target 之间"
 *    与"崩溃在 target 中途"可区分。
 * 3. 每个投影写完 → 立即把 `roundId` 记入 [CommitState.projections]（水位）。
 * 4. **COMMITTED**：全部投影完成后写 COMMITTED、把 roundId 追加进 committedRounds、删 journal。
 *
 * 恢复 = 用同一段 body 再跑一遍；水位决定哪些投影跳过。因此：
 * - **至少一次 + 幂等收敛（at-least-once + idempotent convergence）**：effect 先落盘、水位后更新，
 *   所以进程死在两者之间时，那个投影会被重跑一次。最终状态是收敛的，
 *   但 effect 的执行次数不是恰好一次——本项目没有跨文件事务，拿不到那个保证。
 *   钉住这条口径的两格用例：
 *   RoundCommitJournalTest 的 `an effect that landed but lost its watermark runs again on recovery`
 *   与 `two coroutines committing the same round write it once`。
 * - 不依赖 recent.md 里的 HTML marker 作为跨文件提交标记
 *   （marker 仅作为 recent 投影自身的文件内去重，见 [WRITER_MARKER_PREFIX]）
 *
 * ## roundId 稳定性
 * [stableRoundId] 由 kb 名 + 本轮消息 ID 集合派生，同一轮重试得到同一 ID；
 * 不再用随机 UUID（随机 ID 让"部分写入后重试"变成新事务，等于没有幂等）。
 *
 * ## 序列化
 * 全部走 kotlinx.serialization。历史上这里是手写 JSON parser，
 * 它在遇到 `\` 转义时先丢弃反斜杠再二次 unescape，导致 recentEntry 丢失换行与 marker 结构。
 */
class RoundCommitJournal(
    private val knowledgeRepo: KnowledgeRepository
) {

    companion object {
        /** in-flight 事务的 WAL 文件 */
        const val JOURNAL_PATH = "moment/.round_commit_journal.json"

        /** 已提交轮次 + 每投影水位 */
        const val STATE_PATH = "moment/.round_commit_state.json"

        /** recent.md 内的轮次标记前缀——只用于该文件自身去重，不作跨文件提交标记 */
        const val WRITER_MARKER_PREFIX = "<!-- round:msgIds:"

        // 六个写入边界，每个都有独立水位
        const val PROJ_TOPIC_ROTATE = "topicRotate"
        const val PROJ_TOPIC_SET = "topicSet"
        const val PROJ_RECENT = "recent"
        const val PROJ_SCENE = "scene"
        const val PROJ_PLAN = "plan"
        const val PROJ_COUNT = "count"

        /** 固定顺序：rotate 会清空 recent/scene，必须在它们之前 */
        val ORDERED_PROJECTIONS = listOf(
            PROJ_TOPIC_ROTATE, PROJ_TOPIC_SET, PROJ_RECENT, PROJ_SCENE, PROJ_PLAN, PROJ_COUNT
        )

        /** committedRounds 上界，防状态文件无限膨胀 */
        private const val MAX_COMMITTED_HISTORY = 256

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

        /**
         * 稳定 roundId：同一轮重试必然得到同一 ID。
         *
         * @param roundMsgIds 本轮 HER/ME 消息 ID（已排序、逗号连接）
         * @param fallbackContent 无消息可标识时（纯话题切换轮）用事件内容兜底
         */
        fun stableRoundId(kbName: String, roundMsgIds: String, fallbackContent: String = ""): String {
            val seed = if (roundMsgIds.isNotBlank()) roundMsgIds else "content:" + sha256(fallbackContent)
            return sha256("$kbName\n$seed").substring(0, 32)
        }

        private fun sha256(s: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    /** 事务阶段 */
    enum class CommitStage { PREPARED, WRITING, COMMITTED }

    // ════════════════════════════════════════════════════════════════
    // 类型化 payload——恢复时语义与首次执行完全一致
    // ════════════════════════════════════════════════════════════════

    /** scene 投影的完整事实：文本 + 真实来源 + 归属。恢复时不降级为空来源。 */
    @Serializable
    data class JournalSceneFact(
        val text: String = "",
        @SerialName("source_ids") val sourceIds: List<String> = emptyList(),
        val speaker: String = "",
        val subject: String = ""
    ) {
        fun toSceneFact(): SceneFact = SceneFact(
            text = text,
            sourceIds = sourceIds,
            speaker = entityRefOf(speaker),
            subject = entityRefOf(subject)
        )

        companion object {
            fun of(fact: SceneFact): JournalSceneFact = JournalSceneFact(
                text = fact.text,
                sourceIds = fact.sourceIds,
                speaker = fact.speaker.name,
                subject = fact.subject.name
            )
        }
    }

    /** plan 投影的完整事项：名称 + 稳定 itemId + 状态 + 本轮状态节点 + 来源 */
    @Serializable
    data class JournalOngoingItem(
        val name: String = "",
        val status: String = "",
        val state: String = "",
        @SerialName("item_id") val itemId: String = "",
        @SerialName("source_ids") val sourceIds: List<String> = emptyList()
    ) {
        fun toOngoingItem(): OngoingItem = OngoingItem(
            name = name,
            status = status,
            state = state,
            itemId = itemId,
            sourceIds = sourceIds
        )

        companion object {
            fun of(item: OngoingItem): JournalOngoingItem = JournalOngoingItem(
                name = item.name,
                status = item.status,
                state = item.state,
                itemId = item.itemId,
                sourceIds = item.sourceIds
            )
        }
    }

    /** 一轮提交的完整事件——包含所有投影重放所需的每一个字段 */
    @Serializable
    data class RoundCommitEvent(
        val roundId: String,
        val kbName: String,
        val inputRevision: Int,
        val timestamp: String,
        val topicStatus: String,
        val topicLabel: String,
        /** 本轮是否触发话题归档（由 record() 在 PREPARED 之前判定，恢复时不再重算） */
        val shouldRotate: Boolean = false,
        /** rotate 之前是否存在有效话题 */
        val hadTopic: Boolean = false,
        /** 本轮 HER/ME 消息 ID 集合，recent.md 文件内去重与稳定 roundId 派生用 */
        val roundMsgIds: String = "",
        /**
         * 冻结的 HER/ME 快照。
         * scene 投影需要它做来源校验、speaker 推导和 subject 解析；
         * 不落盘的话恢复时只能传空列表，语义就变了（旧实现正是如此）。
         */
        val frozenMessages: List<JournalMessage> = emptyList(),
        val recentEntry: String = "",
        val sceneFacts: List<JournalSceneFact> = emptyList(),
        val ongoing: List<JournalOngoingItem> = emptyList(),
        val turnCountIncrement: Int = 1,
        val stage: CommitStage = CommitStage.PREPARED
    )

    /** 冻结快照中的一条消息（role 存枚举名） */
    @Serializable
    data class JournalMessage(
        val id: String = "",
        val role: String = "",
        val content: String = ""
    ) {
        fun toChatMessage(): com.lovebrain.app.model.ChatMessage =
            com.lovebrain.app.model.ChatMessage(
                id = id,
                role = runCatching { com.lovebrain.app.model.ChatMessage.Role.valueOf(role) }
                    .getOrDefault(com.lovebrain.app.model.ChatMessage.Role.HER),
                content = content
            )

        companion object {
            fun of(msg: com.lovebrain.app.model.ChatMessage): JournalMessage =
                JournalMessage(id = msg.id, role = msg.role.name, content = msg.content)
        }
    }

    /** 提交状态：已提交轮次清单 + 每投影最后应用的 roundId */
    @Serializable
    data class CommitState(
        val committedRounds: List<String> = emptyList(),
        /** projection -> lastAppliedRoundId */
        val projections: Map<String, String> = emptyMap()
    )

    /**
     * 一次 [commit] 的结果。
     *
     * 之所以要有这个类型而不让调用方自己判断"返回值和第一次一样吗"：
     * `TopicRecorder.record()` 在事务锁**外面**先查过一次 [isRoundCommitted]，
     * 两个并发协程提交同一 round 时都会读到 false，第二个必须在**拿到锁之后**再查一次，
     * 并且要让调用方看得见"这一次什么都没写"。
     */
    sealed interface CommitOutcome<out R> {
        /** body 真的执行了（首次提交或恢复），[value] 是 body 的返回值 */
        data class Committed<out R>(val value: R) : CommitOutcome<R>

        /** 进入事务锁后发现该轮已在 committedRounds 里：一个字节都没有写 */
        object AlreadyCommitted : CommitOutcome<Nothing>
    }

    /** 事务执行句柄——body 通过它声明"写这个投影"，水位由 journal 统一维护 */
    inner class Tx internal constructor(
        val roundId: String,
        private val stateRef: suspend (CommitState) -> Unit,
        private var state: CommitState
    ) {
        /** 该投影是否已对本轮生效 */
        suspend fun isApplied(projection: String): Boolean =
            state.projections[projection] == roundId

        /**
         * 执行一个投影写入，成功后推进水位。
         *
         * 顺序固定为 **effect → watermark**：进程在 effect 中途被杀、
         * 或在 effect 成功后、水位落盘前被杀时，该投影都会被重跑一次，
         * 所以每个 block 内部必须自己幂等
         * （rotateTopic 用 ArchiveOperationState，recent 用文件内 marker，scene/plan 用来源与 itemId）。
         * 这就是本事务是 at-least-once 而不是 exactly-once 的确切位置。
         *
         * @return block 的返回值；已生效则返回 [skipped]
         */
        suspend fun <R> apply(
            projection: String,
            skipped: R,
            block: suspend () -> R
        ): R {
            if (state.projections[projection] == roundId) {
                L.w("projection $projection already applied for round $roundId, skipping")
                return skipped
            }
            val result = block()
            state = state.copy(projections = state.projections + (projection to roundId))
            stateRef(state)
            return result
        }

        suspend fun apply(projection: String, block: suspend () -> Unit) {
            apply(projection, Unit) { block() }
        }
    }

    /** journal 事务互斥锁——beginCommit、全部 target 写、markCommitted 在同一把锁内 */
    private val txMutex = Mutex()

    // ════════════════════════════════════════════════════════════════
    // 对外 API
    // ════════════════════════════════════════════════════════════════

    /**
     * 本轮是否已完整提交——跨文件幂等的唯一判据，不看 recent marker。
     *
     * 这是**不加事务锁**的快路径，只用来省掉一次完整事件的构建；
     * 它不能作为正确性依据，真正拦并发的是 [commit] 里的锁内重读。
     */
    suspend fun isRoundCommitted(kbName: String, roundId: String): Boolean =
        readState(kbName).committedRounds.contains(roundId)

    /** 读取当前状态（只读，不加事务锁） */
    suspend fun readState(kbName: String): CommitState {
        val raw = knowledgeRepo.readFile(kbName, STATE_PATH)
        if (raw.isBlank()) return CommitState()
        return runCatching { json.decodeFromString<CommitState>(raw) }
            .onFailure { L.w("commit state unreadable, starting empty: ${it.message}") }
            .getOrDefault(CommitState())
    }

    private suspend fun writeState(kbName: String, state: CommitState) {
        knowledgeRepo.writeFile(kbName, STATE_PATH, json.encodeToString(CommitState.serializer(), state))
    }

    /**
     * 执行一轮提交。
     *
     * body 必须按 [ORDERED_PROJECTIONS] 顺序声明投影，并且只使用 [event] 中的数据
     * （不得回读调用方的可变状态）——恢复时走的正是同一个 body + 同一个 event。
     *
     * 崩溃在任意点之后，[recoverPending] 会用同一 body 完成剩余投影。
     *
     * **锁内二次检查**：[isRoundCommitted] 是给调用方的锁外快路径，它和这里不是重复代码——
     * 两个协程可以在锁外同时读到"未提交"，只有拿到 [txMutex] 之后重读磁盘状态才算数。
     */
    suspend fun <R> commit(
        kbName: String,
        event: RoundCommitEvent,
        body: suspend Tx.(RoundCommitEvent) -> R
    ): CommitOutcome<R> = txMutex.withLock {
        // 锁内二次检查：进入事务锁后重读提交清单。锁外那一次判断只省开销，不作正确性依据。
        val stateOnDisk = readState(kbName)
        if (stateOnDisk.committedRounds.contains(event.roundId)) {
            L.w("WAL already-committed roundId=${event.roundId} (checked inside tx lock); nothing written")
            return@withLock CommitOutcome.AlreadyCommitted
        }

        val existing = readEvent(kbName)
        if (existing != null && existing.roundId != event.roundId) {
            // 上一轮尚未收敛——先让调用方恢复，避免 journal 被覆盖丢账
            throw IllegalStateException(
                "round ${existing.roundId} (stage ${existing.stage}) is still in flight for $kbName; " +
                    "call recoverPending() before starting a new commit"
            )
        }

        // 1. PREPARED——完整事件先落盘
        val prepared = event.copy(stage = CommitStage.PREPARED)
        knowledgeRepo.writeFile(kbName, JOURNAL_PATH, json.encodeToString(RoundCommitEvent.serializer(), prepared))
        L.w("WAL PREPARED roundId=${event.roundId}")

        // 2. WRITING——显式标记已开始写 target
        knowledgeRepo.writeFile(
            kbName, JOURNAL_PATH,
            json.encodeToString(RoundCommitEvent.serializer(), prepared.copy(stage = CommitStage.WRITING))
        )

        var persisted = stateOnDisk
        val tx = Tx(event.roundId, { newState ->
            persisted = newState
            writeState(kbName, newState)
        }, persisted)

        val result = try {
            body(tx, event)
        } catch (t: Throwable) {
            // 保留 journal，交由下次恢复 roll-forward；不回滚 target（target 各自幂等）
            L.w("round ${event.roundId} aborted (${t::class.simpleName}); journal kept for roll-forward")
            throw t
        }

        // 3. COMMITTED——标记完成、登记轮次、清理 journal
        val committed = persisted.copy(
            committedRounds = (persisted.committedRounds + event.roundId).takeLast(MAX_COMMITTED_HISTORY)
        )
        writeState(kbName, committed)
        knowledgeRepo.writeFile(
            kbName, JOURNAL_PATH,
            json.encodeToString(RoundCommitEvent.serializer(), event.copy(stage = CommitStage.COMMITTED))
        )
        knowledgeRepo.deleteFile(kbName, JOURNAL_PATH)
        L.w("WAL COMMITTED roundId=${event.roundId}")
        CommitOutcome.Committed(result)
    }

    /** 有在途事务时返回它（PREPARED/WRITING），否则 null */
    suspend fun recoverPending(kbName: String): RoundCommitEvent? {
        val event = readEvent(kbName) ?: return null
        return when (event.stage) {
            CommitStage.COMMITTED -> {
                knowledgeRepo.deleteFile(kbName, JOURNAL_PATH)
                L.w("WAL recovery - committed but uncleared, cleaned roundId=${event.roundId}")
                null
            }
            CommitStage.PREPARED, CommitStage.WRITING -> {
                L.w("WAL recovery - ${event.stage} roundId=${event.roundId}, rolling forward")
                event
            }
        }
    }

    /**
     * 崩溃恢复：用与首提相同的 body 重放未完成投影。
     *
     * 返回 true 表示确实重放了一轮。
     *
     * [CommitOutcome.AlreadyCommitted] 同源的锁内二次检查在这里也有：
     * 提交清单已经认得这一轮时，只清 journal，绝不重跑投影。
     */
    suspend fun recover(
        kbName: String,
        body: suspend Tx.(RoundCommitEvent) -> Unit
    ): Boolean {
        val pending = recoverPending(kbName) ?: return false
        return txMutex.withLock {
            if (readState(kbName).committedRounds.contains(pending.roundId)) {
                knowledgeRepo.deleteFile(kbName, JOURNAL_PATH)
                L.w("WAL recovery skipped round ${pending.roundId}: commit log already holds it, journal cleaned")
                return@withLock false
            }
            knowledgeRepo.writeFile(
                kbName, JOURNAL_PATH,
                json.encodeToString(RoundCommitEvent.serializer(), pending.copy(stage = CommitStage.WRITING))
            )
            var persisted = readState(kbName)
            val tx = Tx(pending.roundId, { newState ->
                persisted = newState
                writeState(kbName, newState)
            }, persisted)
            body(tx, pending)
            val committed = persisted.copy(
                committedRounds = (persisted.committedRounds + pending.roundId).takeLast(MAX_COMMITTED_HISTORY)
            )
            writeState(kbName, committed)
            knowledgeRepo.deleteFile(kbName, JOURNAL_PATH)
            L.w("recovery complete for round ${pending.roundId}")
            true
        }
    }

    /** 丢弃 journal（不撤销 target——本项目没有反向写入能力，回滚一律表现为 roll-forward） */
    suspend fun abandon(kbName: String, roundId: String) = txMutex.withLock {
        val event = readEvent(kbName) ?: return@withLock
        if (event.roundId == roundId) {
            knowledgeRepo.deleteFile(kbName, JOURNAL_PATH)
            L.w("WAL abandoned roundId=$roundId")
        }
    }

    private suspend fun readEvent(kbName: String): RoundCommitEvent? {
        val raw = knowledgeRepo.readFile(kbName, JOURNAL_PATH)
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<RoundCommitEvent>(raw) }
            .onFailure {
                L.w("journal corrupt, discarding: ${it.message}")
                // 损坏的 journal 必须留下痕迹再删除，否则丢账无人知情
                knowledgeRepo.appendFile(
                    kbName, "moment/.round_commit_corrupt.log",
                    raw.trim() + "\n"
                )
                knowledgeRepo.deleteFile(kbName, JOURNAL_PATH)
            }
            .getOrNull()
    }
}

/** EntityRef 名称还原；无法识别时保守回落到 UNKNOWN，绝不猜测归属 */
private fun entityRefOf(name: String): com.lovebrain.app.model.EntityRef =
    runCatching { com.lovebrain.app.model.EntityRef.valueOf(name) }
        .getOrDefault(com.lovebrain.app.model.EntityRef.UNKNOWN)
