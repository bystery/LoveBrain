package com.lovebrain.app.domain.champion

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 冠军引擎的事件存储（T4 Kotlin 移植 §1.1）——移植蓝本
 * `candidates/window_expand_b_norecap_uc_guard/mechanism/mechanism.py` 的
 * `events` / `uid_by_key` / `uid_seq` 三件状态。
 *
 * 移植不变量（逐条对照 Python，任何一条破了都不是冠军机制）：
 * - **回复永不入库**：模型草稿、候选、点赞偏好永不 append；只有真实消息
 *   （HER=对方 / ME=用户实际发送或捕获）才进 [append]。非 HER/ME 的角色在
 *   [append] 处直接被拒——"回复草稿入库请求被拒"是验收对拍项（规格书 §5.4）。
 * - `uid_by_key` 去重语义：key = `"$role|$text|$ts"`（照 `_key_of` 逐字），
 *   同键复用既有 uid，不重复入库——同一条消息跨轮稳定同 id，
 *   未定段 seq 与历史行 `m_src[uid]` 因此可对齐。
 * - [events] 按 uid 升序返回 = 时间序（Python 列表 append 序 == uid 递增序）。
 * - 每条事件额外带 `turn`（W16 增量）：仅供未定段「（T<turn>）」标注，不进历史行文本。
 *
 * 存储（[ChampionEventStorage]）按 `contactId__story` 一个键一份 JSON Lines，
 * 目录键带 engine 维度（规格书 §2.2：与 kb 投影物理分离的第 7 个写入面）。
 * 回退（flag→OFF）时文件保留不清；重开时按 `role|text|ts` 续用，不回填、不迁移。
 */
@Serializable
data class ChampionEvent(
    val uid: Long,
    val role: String,
    val text: String,
    val ts: String,
    val turn: Int
)

/** 持久化状态：有序事件 + 去重索引 + uid 计数器（对应 Python `snapshot`/`restore` 的载荷）。 */
@Serializable
data class ChampionEventState(
    val events: List<ChampionEvent> = emptyList(),
    val uidSeq: Long = 0
)

/**
 * 事件存储端口。实现方负责按 [key]（已由调用方拼好 `contactId__story`）读写；
 * 读不到（新对话）交回 [ChampionEventState] 默认空态。
 */
interface ChampionEventStorage {
    fun load(key: String): ChampionEventState
    fun save(key: String, state: ChampionEventState)
}

/**
 * 文件实现：每键一个 JSON 文件（`champion_events/<key>.json`，内容为
 * [ChampionEventState] 的 JSON）。域名允许 java.io.File（PackageDependencyTest
 * 的 domain 禁单不含它）；写入是整文件原子替换（tmp → rename），与产品
 * 「只有一个事务 owner」的口径一致——本存储是新增的第 7 个写入面，
 * 不触碰 TopicRecorder 的 WAL/投影链。
 */
class FileChampionEventStorage(private val rootDir: File) : ChampionEventStorage {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun fileFor(key: String): File {
        val safe = key.map { ch -> if (ch.isLetterOrDigit() || ch in "._-") ch else '_' }
            .joinToString("")
        return File(rootDir, "$safe.json")
    }

    override fun load(key: String): ChampionEventState {
        val f = fileFor(key)
        if (!f.isFile) return ChampionEventState()
        return runCatching {
            json.decodeFromString(ChampionEventState.serializer(), f.readText(Charsets.UTF_8))
        }.getOrDefault(ChampionEventState())
    }

    override fun save(key: String, state: ChampionEventState) {
        rootDir.mkdirs()
        val f = fileFor(key)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(ChampionEventState.serializer(), state), Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            // Windows 上目标存在时 rename 可能失败：删旧再试一次（幂等，最后败则抛）
            f.delete()
            check(tmp.renameTo(f)) { "champion events save failed for $key" }
        }
    }
}

/**
 * 有序真实消息存储（对应 `mechanism.py::_uid_for` + `h["events"]`）。
 *
 * [append] 逐条走 `role|text|ts` 去重：新键分配 `uidSeq` 递增的新 uid 并入库，
 * 旧键原样返回既有事件（不入库第二次）。[turn] 只在**新**事件落库时记录。
 */
class ChampionEventStore(private val storage: ChampionEventStorage) {

    private val mutex = Mutex()

    /** 合法角色白名单：回复草稿（REPLY/DRAFT/其他任何值）在入口就被拒。 */
    private fun requireRealRole(role: String) {
        check(role == "HER" || role == "ME") {
            "champion events 只收真实消息（HER/ME），拒绝入库 role=$role（回复是草稿，不是已发生的事）"
        }
    }

    /** 去重键——照 `_key_of` 逐字：`"$role|$text|$ts"`。 */
    internal fun keyOf(role: String, text: String, ts: String): String = "$role|$text|$ts"

    /**
     * 追加一批本轮真实消息，返回**去重后**的事件（顺序与入参一致）。
     * 同键消息复用既有 uid；同一批内重复键同样只入一次。
     */
    suspend fun append(
        key: String,
        messages: List<Triple<String, String, String>>,
        turn: Int
    ): List<ChampionEvent> = mutex.withLock {
        if (messages.isEmpty()) return@withLock emptyList()
        messages.forEach { requireRealRole(it.first) }
        val state = storage.load(key)
        val byKey = HashMap<String, ChampionEvent>()
        state.events.forEach { byKey[keyOf(it.role, it.text, it.ts)] = it }
        var uidSeq = state.uidSeq
        val out = mutableListOf<ChampionEvent>()
        var dirty = false
        for ((role, text, ts) in messages) {
            val k = keyOf(role, text, ts)
            val existing = byKey[k]
            if (existing != null) {
                out += existing
                continue
            }
            val ev = ChampionEvent(uid = uidSeq, role = role, text = text, ts = ts, turn = turn)
            uidSeq++
            byKey[k] = ev
            out += ev
            dirty = true
        }
        if (dirty) {
            val merged = (state.events + out.filter { it.uid >= state.uidSeq })
                .distinctBy { keyOf(it.role, it.text, it.ts) }
            storage.save(key, ChampionEventState(events = merged, uidSeq = uidSeq))
        }
        out
    }

    /** 全量事件，按 uid 升序 = 时间序。 */
    suspend fun events(key: String): List<ChampionEvent> = mutex.withLock {
        storage.load(key).events.sortedBy { it.uid }
    }

    /** 下一个轮次号（Python `h["turn_count"] + 1` 的等价：取已入库最大 turn + 1，空库 = 1）。 */
    suspend fun nextTurn(key: String): Int = mutex.withLock {
        (storage.load(key).events.maxOfOrNull { it.turn } ?: 0) + 1
    }
}
