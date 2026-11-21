package com.lovebrain.app.domain.champion

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 冠军引擎的事件存储——移植自研究原型的 `events` / `uid_by_key` / `uid_seq`
 * 三件状态（本文件是 Kotlin 侧唯一实现）。
 *
 * 不变量（任何一条破了都不是冠军机制）：
 * - **回复永不入库**：模型草稿、候选、点赞偏好永不 append；只有真实消息
 *   （HER=对方 / ME=用户实际发送或捕获）才进 [append]。非 HER/ME 的角色在
 *   [append] 处直接被拒——"回复草稿入库请求被拒"是验收对拍项。
 * - 去重语义：去重身份 = **每条消息的稳定 uid**
 *   （[ChampionEvent.msgUid]，提交侧 ChatMessage.id——同一 pending 轮重试携带同一
 *   msgUid，复用既有事件；不同消息 msgUid 不同，永不误合）。旧 `"$role|$text|$ts"`
 *   键已废弃：提交侧时间只格式化到分钟，对方同一分钟连发两次"好"是两条真实消息，
 *   旧键会把第二条合进第一条——违反"真实对话顺序不能丢、不能合并真实事实"。
 * - [events] 按 uid 升序返回 = 时间序（append 序 == uid 递增序）。
 * - 每条事件额外带 `turn`（提交轮次）：供历史行「（T<turn>）」标注，不进历史正文。
 *
 * 存储（[ChampionEventStorage]）按 `contactId__story` 一个键一份 JSON Lines。
 * 分区键身份：`contactId` = [com.lovebrain.app.model.KbContext.name] =
 * KB 目录名（kb.json 的 `name`，建库时 `kb_<时间戳36>_<随机>` 生成，产品全局稳定身份）；
 * **显示名不参与存储键**（只用于日志展示）。删除 KB 重建后新库拿到新的目录名，
 * 不会复用旧库的事件历史——旧库目录删除时其事件文件随键不可达（见 fileFor 摘要命名）。
 * 目录键带 engine 维度（与 kb 投影物理分离的第 7 个写入面）。
 * 存量兼容：当前模式默认 OFF、shadow 未上线，**无存量用户数据**——
 * 去重身份换键（role|text|ts → msgUid）不留迁移代码；旧键下落过库的内部验证数据
 * （msgUid 缺省为空串）按"永不命中去重"对待，重提交即新事件，不回填、不迁移。
 */
@Serializable
data class ChampionEvent(
    val uid: Long,
    val role: String,
    val text: String,
    val ts: String,
    val turn: Int,
    /**
     * 稳定消息身份（去重键）：提交侧（LoveBrainViewModel.nextRound）传
     * ChatMessage.id——用户删掉重来/保存失败重试的同一 pending 轮携带同一 msgUid
     * （uid 复用、不重复入库）；同一分钟同内容的两条不同消息 msgUid 不同（两条事件）。
     * 必须非空（[ChampionMessage.msgUid]）。
     */
    val msgUid: String = ""
)

/** 持久化状态：有序事件 + 去重索引 + uid 计数器（对应 Python `snapshot`/`restore` 的载荷）。 */
@Serializable
data class ChampionEventState(
    val events: List<ChampionEvent> = emptyList(),
    val uidSeq: Long = 0
)

/**
 * 事件文件存在但读不出来（坏 JSON / IO 失败）时抛这个。
 *
 * 为什么不再像旧实现那样 `getOrDefault(emptyState)`：空态会被 append 当成
 * "这本对话还没有历史"，于是下一次保存把原文件整个覆盖——历史事件与 `uidSeq`
 * 一起归零，去重身份也全灭。坏数据必须打断写入，把原文件留在原地等人处理。
 */
class ChampionEventCorruptException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 事件存储端口。实现方负责按 [key]（已由调用方拼好 `contactId__story`）读写；
 * 文件不存在（新对话）交回 [ChampionEventState] 默认空态。
 *
 * ⚠ 文件存在但内容坏掉时**不许**当空态返回：那会让下一次 save 静默覆盖全部历史。
 * 文件实现抛 [ChampionEventCorruptException]，由调用方决定提示/保留原文件。
 */
interface ChampionEventStorage {
    fun load(key: String): ChampionEventState
    fun save(key: String, state: ChampionEventState)
}

/**
 * 文件实现：每键一个 JSON 文件（`champion_events/<safe>__<摘要前缀>.json`，内容为
 * [ChampionEventState] 的 JSON）。
 *
 * 文件名身份：**不允许**用"特殊字符替换成下划线"把 key 映射成文件名——
 * 那是把多对一映射：不同 key（含异库同名显示名、含路径分隔/空白等脏字符的目录名）
 * 可以洗成同一个文件名互相覆盖，删库重建同名还会读到别人的旧历史。改为
 * `清洗前缀(≤48 字符，人读) + "__" + SHA-256(key) 前 24 hex（稳定身份）`：
 * 强摘要抗碰撞，键与文件一一对应；键换名即换文件，旧文件自然失联不复活。
 *
 * 存量兼容：当前模式默认 OFF、shadow 未上线，**无存量用户数据**——
 * 直接换键，不留迁移代码；旧命名文件若存在（内部验证机）按不可达废文件对待，不读不删。
 *
 * 域名允许 java.io.File（PackageDependencyTest 的 domain 禁单不含它）；写入是整文件
 * 原子替换（tmp 写好并 fsync → NIO move 覆盖），与产品「只有一个事务 owner」的口径一致——
 * 本存储是新增的第 7 个写入面，不触碰 TopicRecorder 的 WAL/投影链。
 */
class FileChampionEventStorage(private val rootDir: File) : ChampionEventStorage {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun fileFor(key: String): File {
        // 人读前缀：照旧清洗（仅用于肉眼对账，不承担身份职责），截到 48 字符防文件名超长
        val safe = key.map { ch -> if (ch.isLetterOrDigit() || ch in "._-") ch else '_' }
            .joinToString("").take(48)
        // 稳定身份：SHA-256(key) 前 24 hex——碰撞面只剩摘要碰撞（工程上视为不存在）
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(24)
        return File(rootDir, "${safe}__${hex}.json")
    }

    override fun load(key: String): ChampionEventState {
        val f = fileFor(key)
        if (!f.isFile) return ChampionEventState()
        val text = runCatching { f.readText(Charsets.UTF_8) }.getOrElse { e ->
            throw ChampionEventCorruptException("champion events unreadable: ${f.name}", e)
        }
        return runCatching {
            json.decodeFromString(ChampionEventState.serializer(), text)
        }.getOrElse { e ->
            // 不许把坏文件当空态（那会在下一次 save 时静默覆盖掉全部历史）
            throw ChampionEventCorruptException("champion events corrupted: ${f.name}", e)
        }
    }

    override fun save(key: String, state: ChampionEventState) {
        rootDir.mkdirs()
        val f = fileFor(key)
        // 目标已存在但解不开：先把它挪成旁证文件再写新内容，绝不原地覆盖。
        // 正常情况下 append 会先在 load 处抛，这里兜的是直接 save 的调用方。
        if (f.isFile) {
            val readable = runCatching {
                json.decodeFromString(ChampionEventState.serializer(), f.readText(Charsets.UTF_8))
            }.isSuccess
            if (!readable) {
                val preserved = File(f.parentFile, f.name + ".corrupt-" + System.currentTimeMillis())
                if (!f.renameTo(preserved)) {
                    throw IllegalStateException("champion events save aborted: unreadable target kept at ${f.name}")
                }
                com.lovebrain.app.util.L.e(
                    "champion events file was unreadable and preserved at ${preserved.name}", null
                )
            }
        }
        val tmp = File(f.parentFile, f.name + ".tmp")
        try {
            // 先 fsync 再 rename：旧实现 writeText 之后直接 rename，掉电可能留下
            // "文件在、内容是空/半截"的事件库（load 现在会把它判成坏数据，等于全灭）。
            FileOutputStream(tmp).use { fos ->
                fos.write(json.encodeToString(ChampionEventState.serializer(), state).toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            // 目标替换只走 NIO：失败就抛，不再"删旧再 rename"——
            // 那条路第二次 rename 失败时新旧两份都没了。
            try {
                Files.move(
                    tmp.toPath(), f.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            throw IllegalStateException("champion events save failed for $key", e)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}

/**
 * 一条待入库的真实消息：[role]/[text]/[ts] 之外携带 [msgUid]——
 * 提交侧每条消息的**稳定身份**（ChatMessage.id），是入库去重的唯一键。
 * msgUid 为空直接被拒：空身份会把多条消息合并成一条，正是丢消息的那颗。
 */
data class ChampionMessage(
    val role: String,
    val text: String,
    val ts: String,
    val msgUid: String
)

/**
 * 有序真实消息存储（对应原型的 uid 分配 + 事件列表）。
 *
 * [append] 逐条按 [ChampionMessage.msgUid] 去重：新身份分配 `uidSeq`
 * 递增的新 uid 并入库，旧身份原样返回既有事件（不入库第二次）——同一 pending 轮
 * 重试（同 msgUid）去重命中，同分钟同内容的不同消息（不同 msgUid）各成一条事件。
 * [turn] 只在**新**事件落库时记录。
 */
class ChampionEventStore(private val storage: ChampionEventStorage) {

    private val mutex = Mutex()

    /** 合法角色白名单：回复草稿（REPLY/DRAFT/其他任何值）在入口就被拒。 */
    private fun requireRealRole(role: String) {
        check(role == "HER" || role == "ME") {
            "champion events 只收真实消息（HER/ME），拒绝入库 role=$role（回复是草稿，不是已发生的事）"
        }
    }

    /** 去重身份必须非空：空 msgUid 会把多条消息误合成一条。 */
    private fun requireMsgUid(msgUid: String) {
        check(msgUid.isNotBlank()) {
            "champion events 入库请求缺稳定消息身份（msgUid 为空）——去重键不许为空"
        }
    }

    /**
     * 追加一批本轮真实消息，返回**去重后**的事件（顺序与入参一致；同 msgUid 的
     * 入参条目映射到同一个既有事件对象）。新身份入库，旧身份复用既有 uid。
     */
    suspend fun append(
        key: String,
        messages: List<ChampionMessage>,
        turn: Int
    ): List<ChampionEvent> = mutex.withLock {
        if (messages.isEmpty()) return@withLock emptyList()
        messages.forEach { requireRealRole(it.role); requireMsgUid(it.msgUid) }
        val state = storage.load(key)
        val byMsgUid = HashMap<String, ChampionEvent>()
        state.events.forEach { if (it.msgUid.isNotBlank()) byMsgUid[it.msgUid] = it }
        var uidSeq = state.uidSeq
        val out = mutableListOf<ChampionEvent>()
        for (msg in messages) {
            val existing = byMsgUid[msg.msgUid]
            if (existing != null) {
                out += existing
                continue
            }
            val ev = ChampionEvent(
                uid = uidSeq, role = msg.role, text = msg.text, ts = msg.ts,
                turn = turn, msgUid = msg.msgUid
            )
            uidSeq++
            byMsgUid[msg.msgUid] = ev
            out += ev
        }
        // 新事件（uid >= 旧 uidSeq）才需要落盘；复用事件本就在 state.events 里。
        // uid 由 uidSeq 单调分配、批内同 msgUid 只分配一次，按 uid 去重即足够
        // （旧的 role|text|ts distinctBy 已废弃——它正是合并同分钟重复消息的那颗）。
        val newEvents = out.filter { it.uid >= state.uidSeq }.distinctBy { it.uid }
        if (newEvents.isNotEmpty()) {
            storage.save(key, ChampionEventState(events = state.events + newEvents, uidSeq = uidSeq))
        }
        out
    }

    /**
     * 只读路径的兜底：文件坏了就交回空态并记错误，**绝不回写**。
     *
     * 与 [FileChampionEventStorage.load] 的抛出一分为二：写侧（[append]）继续在 load 处抛，
     * 于是空态永远到不了 save、历史不会被覆盖；读侧（[events]/[nextTurn]）降级成"这次没有历史"，
     * 不让一次坏文件把正在进行的这一轮崩掉。两种处理都不撒谎。
     */
    private fun loadForRead(key: String): ChampionEventState =
        try {
            storage.load(key)
        } catch (e: ChampionEventCorruptException) {
            com.lovebrain.app.util.L.e(
                "champion events unreadable; history omitted, file preserved untouched: $key", e
            )
            ChampionEventState()
        }

    /** 全量事件，按 uid 升序 = 时间序。 */
    suspend fun events(key: String): List<ChampionEvent> = mutex.withLock {
        loadForRead(key).events.sortedBy { it.uid }
    }

    /** 下一个轮次号（Python `h["turn_count"] + 1` 的等价：取已入库最大 turn + 1，空库 = 1）。 */
    suspend fun nextTurn(key: String): Int = mutex.withLock {
        (loadForRead(key).events.maxOfOrNull { it.turn } ?: 0) + 1
    }
}
