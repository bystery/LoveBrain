package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import kotlinx.serialization.Serializable

/**
 * 归档操作的累积状态——`rotateTopic` 的四步各完成一步就在 [completedSteps] 里追加一项，
 * 而不是每次从初始集合重算。异常中断后重启据此跳过已完成步骤，只做剩下的部分。
 *
 * 字段名是要落盘的（`moment/.archive_op.json`），改任何一个都等于换格式：
 * `ArchiveOperationStateTest` 里有用字面 JSON 造的旧状态。
 */
@Serializable
internal data class ArchiveOperationState(
    val operationId: String,       // 唯一操作 ID（基于内容哈希）
    val kbName: String,            // 目标知识库
    val timestamp: String,         // 操作时间戳
    val oldTopic: String,          // 旧话题名
    val contentHash: String,       // 输入内容哈希（防重复归档不同内容）
    val completedSteps: List<String> = emptyList()  // 已完成步骤（累积追加）
)

/**
 * 仓库交给归档格用的能力，七样。
 *
 * 为什么这一格与它的接口是 `internal`，而文档格/记忆格的接口是 public：
 * `ArchiveOperationState` 只是这一格的状态文件格式，出了归档格没有第二处读它
 * （`MemoryCorrection` 不一样，生成侧与 UI 都要读，所以那个住在 `model/` 里、是 public）。
 */
internal interface ArchiveStorage {
    fun note(message: String)

    /** 归档条目标题与 operationId 共用的那一刻（`TimeFmt.now()`，形如 `2026-09-25 10:00`） */
    fun stamp(): String

    /**
     * kb.json 的 `updatedAt` 用另一把尺（ISO-8601 带时区，`isoNow()`）。
     *
     * 两把尺不是我这轮加的选择，是原来就有的事实：条目行里的时间与 meta 里的时间格式不同，
     * 混成一个会把 kb.json 的 updatedAt 换格式。这里显式分开命名，至少不再靠"背下来"。
     */
    fun metaTimestamp(): String

    /** 过 canonical 守门的读（与公开读同源，旧布局有回退） */
    fun read(kbName: String, relativePath: String): String

    /** 一次写事务：状态、追加、清空源文件、计数都在这一个事务对象里 */
    fun runTransaction(kbName: String, block: ArchiveTx.() -> Unit)

    /** 状态的编解码仍用仓库那一份 Json 配置——给第二个类另配一把尺就等于换了判据 */
    fun encodeState(state: ArchiveOperationState): String

    fun decodeState(text: String): ArchiveOperationState?
}

/** 归档格能做的四类落盘动作。每个都是"这一次事务里"的动作，仓库在外层决定锁与只读判定。 */
internal interface ArchiveTx {
    /** 覆盖写。false = 被只读保护或路径非法挡下，一个字节都没落 */
    fun write(relativePath: String, content: String): Boolean

    /** 追加写（归档条目），语义同 [write] */
    fun append(relativePath: String, content: String): Boolean

    /** 删除状态文件。false = 没删（被挡、越界或本来就不存在） */
    fun delete(relativePath: String): Boolean

    /** 读改写 kb.json；库缺失 / 只读 / JSON 坏掉返回 false 且不写 */
    fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean
}

/**
 * 第5节第3条 最后一格：**话题归档**（`rotateTopic` 的四步状态机 + 归档条目格式 + 归档计数）。
 *
 * 那行写的是"topic rotate/import/export/backup"。import/export 早在 `KbArchiveTransfer`、
 * backup 早在 `KnowledgeBackupService`，这格要收的其实是剩下那件：四步状态机原先长在
 * 2200→1800 行的仓库里，"哪些步骤算完成""归档条目长什么样""计数从哪回填"三件事没有名字。
 *
 * 四条规则现在各有一个所有者：
 *  ① 步骤集合与恢复判定（[ArchiveStep] 与 [rotate] 里的三段 `if`）；
 *  ② 归档条目的样子（`# [时间] 旧话题` + 状态变化段 + 对话记录段，合并策略仍在
 *     [KbTextOps.mergeSceneEntries]，那一份没动）；
 *  ③ "旧话题名"的读法：[KbTextOps.topicLabel]，与四处写侧同一个所有者（上一笔收的）；
 *  ④ 归档计数的回填口径：[countArchiveEntries]（`^## \[` 数条目），
 *     与 `topicCount + 1` 走的是同一个 kb.json 字段。
 *
 * 本类不自持锁、不拼路径、不落盘、不配 JSON：这四件事分别由仓库的 `fileMutex`、
 * [KnowledgeDocumentStore.resolve]、那唯一的写链、以及仓库那一份 Json 配置负责。
 */
internal class KnowledgeArchiveService(private val storage: ArchiveStorage) {

    /**
     * 归档一步：把此刻的内容沉进 `memory/raw_topic.md`，计数 +1，清空四个源文件。
     *
     * 调用方必须已持有仓库的文件互斥锁（本类不抢锁）。
     *
     * 恢复语义保留原样：只要状态文件在，就**信任里面记录的已完成步骤**跳过它们；
     * 因为 `CLEAR_SOURCES` 会改源文件，恢复时不能拿内容哈希去验（那会把已归档的内容判成"新内容"再归一次）。
     * 哈希只参与 `operationId` 的唯一性。
     *
     * 返回 false = 中途某一步**没有落成**（被只读保护挡下、路径非法、或磁盘写失败）。
     * 此时：已完成步骤仍按原样记在状态文件里（可重试且不重复），未完成的那一步不记账，
     * 绝不会出现"报了推进但字节没落"的半成品状态机。旧实现在每一段事务里丢掉写入返回值，
     * 于是只读库上照样把四步全记成完成。
     */
    fun rotate(kbName: String): Boolean {
        val existingOp = readState(kbName)

        val rawChat = storage.read(kbName, "memory/raw_chat.md")
        val recent = storage.read(kbName, "moment/recent.md")
        val rawScene = storage.read(kbName, "memory/raw_scene.md")
        val scene = storage.read(kbName, "moment/scene.md")

        val opState = existingOp ?: createState(kbName, rawChat, recent, rawScene, scene) ?: return false

        val completed = opState.completedSteps.toMutableList()
        val hasContent =
            rawChat.isNotBlank() || recent.isNotBlank() || rawScene.isNotBlank() || scene.isNotBlank()

        // 步骤 2 — 追加归档条目（幂等：做过就跳）
        if (hasContent && ArchiveStep.APPEND_ARCHIVE !in completed) {
            val entry = archiveEntry(opState, rawChat, recent, rawScene, scene)
            if (!writeTransactionChecked(kbName, "append $ARCHIVE_FILE") { append(ARCHIVE_FILE, entry) }) {
                return false
            }
            completed.add(ArchiveStep.APPEND_ARCHIVE)
            if (!writeState(kbName, opState.copy(completedSteps = completed.toList()))) return false
        }

        // 步骤 3 — 计数 +1（幂等）
        if (hasContent && ArchiveStep.INCREMENT_COUNT !in completed) {
            if (!writeTransactionChecked(kbName, "increment kb.json topicCount") {
                    updateMeta { kb -> kb.copy(topicCount = kb.topicCount + 1, updatedAt = storage.metaTimestamp()) }
                }
            ) {
                return false
            }
            completed.add(ArchiveStep.INCREMENT_COUNT)
            if (!writeState(kbName, opState.copy(completedSteps = completed.toList()))) return false
        }

        // 步骤 4 — 清空四个源文件（幂等；无内容时也要清，这是原来的行为）
        if (ArchiveStep.CLEAR_SOURCES !in completed) {
            var cleared = false
            if (!writeTransactionChecked(kbName, "clear source files") {
                    cleared = SOURCE_FILES.all { write(it, "") }
                    cleared
                }
            ) {
                return false
            }
            if (!cleared) {
                storage.note("话题归档：清空源文件未全部落盘，状态保持可重试")
                return false
            }
            completed.add(ArchiveStep.CLEAR_SOURCES)
            if (!writeState(kbName, opState.copy(completedSteps = completed.toList()))) return false
        }

        // 四步都做完 —— 状态文件不该留着。删不掉（被拒/IO 失败）要留痕：
        // 留着它不会丢数据（重跑只会命中幂等分支），但会让人以为"上次没完成"。
        var deleteAttempted = false
        var deleted = false
        storage.runTransaction(kbName) {
            deleteAttempted = true
            deleted = delete(STATE_FILE)
        }
        if (deleteAttempted && !deleted && storage.read(kbName, STATE_FILE).isNotBlank()) {
            storage.note("归档操作状态没落盘：$STATE_FILE 删除被拒或失败，下次将按已完成步骤跳过")
        }
        return true
    }

    /**
     * 一次归档写事务的成败判定：**事务根本没开**（只读库被外层跳过）与
     * **块里的写返回 false**（路径非法/底层拒绝）都算失败。
     */
    private fun writeTransactionChecked(kbName: String, what: String, block: ArchiveTx.() -> Boolean): Boolean {
        var attempted = false
        var written = false
        storage.runTransaction(kbName) {
            attempted = true
            written = block()
        }
        if (!attempted || !written) {
            storage.note("话题归档失败（$what）：只读（schema 过新）、路径非法或落盘失败，该步骤未记账")
            return false
        }
        return true
    }

    /**
     * 归档条目长什么样。抽成函数是为了让"格式"这件事有一个能单独测的名字——
     * 它原来是 `rotate` 里的一段 `buildString`，要验格式只能连临时目录与状态机一起跑。
     */
    fun archiveEntry(
        state: ArchiveOperationState,
        rawChat: String,
        recent: String,
        rawScene: String,
        scene: String
    ): String = buildString {
        append("\n# [${state.timestamp}] ${state.oldTopic}\n\n")
        append("## [${state.timestamp}] 状态变化\n")
        // 合并策略见 KbTextOps.mergeSceneEntries：无合法时间戳的行不丢弃，标为 legacy 留在尾部
        append(KbTextOps.mergeSceneEntries(rawScene, scene))
        append("\n")
        append("### [${state.timestamp}] 对话记录\n")
        if (rawChat.isNotBlank()) append(rawChat.trim()).append("\n")
        if (recent.isNotBlank()) append(recent.trim()).append("\n")
    }

    /** 归档计数口径：数 `## [` 开头的条目行（旧库 kb.json 还没记数时用它回填） */
    fun countArchiveEntries(content: String): Int =
        Regex("^## \\[", RegexOption.MULTILINE).findAll(content).count()

    /**
     * 建立本次操作的状态。返回 null = 状态文件没落成：此时**一步都不许做**——
     * 没有恢复记录就清源/追加，崩溃后重跑要么重复归档、要么把内容吞掉。
     */
    private fun createState(
        kbName: String,
        rawChat: String,
        recent: String,
        rawScene: String,
        scene: String
    ): ArchiveOperationState? {
        val timestamp = storage.stamp()
        val oldTopic = KbTextOps.topicLabel(storage.read(kbName, "moment/topic.md"))
        val inputHash = KbTextOps.contentHash(rawChat, recent, rawScene, scene, oldTopic)
        val newState = ArchiveOperationState(
            operationId = "$timestamp-$inputHash",
            kbName = kbName,
            timestamp = timestamp,
            oldTopic = oldTopic,
            contentHash = inputHash
        )
        if (!writeState(kbName, newState)) return null
        return newState
    }

    private fun readState(kbName: String): ArchiveOperationState? {
        val raw = storage.read(kbName, STATE_FILE)
        if (raw.isBlank()) return null
        return storage.decodeState(raw)
    }

    /**
     * 状态落盘。**两种"没写成"都要留痕**：事务里的写被挡下（返回 false），
     * 以及整个事务被外层跳过（block 一次都没跑）。静默跳过就等于"报了推进却没写"，
     * 下次崩溃恢复会重做已经完成的步骤。
     *
     * @return true = 状态真的落盘了；false = 一个字节都没写（调用方必须停在这一步）
     */
    private fun writeState(kbName: String, state: ArchiveOperationState): Boolean {
        var attempted = false
        var written = false
        storage.runTransaction(kbName) {
            attempted = true
            written = write(STATE_FILE, storage.encodeState(state))
        }
        if (!attempted || !written) {
            storage.note("归档操作状态没落盘：$STATE_FILE 只读（schema 过新）或路径非法")
            return false
        }
        return true
    }

    companion object {
        /** 归档文件与四个源文件：清哪四个、往哪儿追加，只有这一份清单 */
        const val ARCHIVE_FILE = "memory/raw_topic.md"
        val SOURCE_FILES = listOf(
            "memory/raw_chat.md", "memory/raw_scene.md", "moment/scene.md", "moment/recent.md"
        )
        const val STATE_FILE = "moment/.archive_op.json"
    }
}

/** rotateTopic 的四步。步骤名要落盘（状态文件里存的就是这些串），改字等于换格式。 */
internal object ArchiveStep {
    const val READ_INPUT = "read_input"           // 读取输入文件
    const val APPEND_ARCHIVE = "append_archive"     // 追加到 raw_topic.md
    const val INCREMENT_COUNT = "increment_count"   // topicCount + 1
    const val CLEAR_SOURCES = "clear_sources"       // 清空四个源文件
}
