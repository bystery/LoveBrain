package com.lovebrain.app.data

import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import kotlinx.serialization.json.Json

/**
 * 一次写事务里的落盘动作。整批共享同一次 schema 只读判定。
 *
 * 与 [MemoryTx] 同形，但名字刻意分开：`MemoryTx.() -> Unit` 与 `IntentTx.() -> Unit`
 * 都擦除成 `Function1`，同名 overload 在 JVM 上是 platform declaration clash。
 */
fun interface IntentTx {
    /** false = 被只读保护或路径非法挡下，一个字节都没落 */
    fun write(relativePath: String, content: String): Boolean
}

/**
 * 仓库交给意图格用的能力，三样。
 *
 * 与记忆格同一纪律：不给 Mutex、不给 File、不给落盘实现。
 * `read` 过 canonical 守门，`runWrite` 经仓库唯一写事务落盘。
 *
 * 方法名刻意叫 `runWrite` 而不是 `writeTransaction`：`MemoryTx.() -> Unit`
 * 与 `IntentTx.() -> Unit` 都擦除成 `Function1`，同名就是 platform declaration clash
 * （与画像格的 `writeMetaTransaction` / 归档格的 `runTransaction` 同一条坑）。
 */
internal interface IntentStorage {
    fun note(message: String)

    /** 过 canonical 守门的读：越界、非法、不存在都得到空串 */
    fun read(kbName: String, relativePath: String): String

    /** 同一个库的一次写事务；schema 过新时块内所有写一起被拒 */
    fun runWrite(kbName: String, block: IntentTx.() -> Unit)
}

/**
 * §5.3 后续拆出的持续意图格：每 KB 一份 `moment/intent.json`。
 *
 * 搬出来的理由是 revision 递增 + 编解码 + "未落盘要留痕"这三件事以前混在仓库
 * `saveIntent` 那一节里（读、改、写事务、日志四件事没有名字）。现在意图文件的
 * 编解码口径（坏 JSON 当空配置）与 revision+1 的递增语义各有一个所有者；
 * 锁、路径守门、落盘仍在本类外（仓库的 fileMutex + `atomicWriteText`）。
 *
 * 本类不自持锁、不拼路径、不落盘：这三件事分别由仓库的 fileMutex、
 * [KnowledgeDocumentStore.resolve]、以及 `atomicWriteText` 那唯一的写链负责。
 */
internal class KnowledgeIntentService(private val storage: IntentStorage) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    /** 读取持续意图配置。缺失、空、JSON 坏掉都回到"当前没有意图"。 */
    fun read(kbName: String): IntentConfig = decode(storage.read(kbName, INTENT_FILE))

    /**
     * 保存持续意图配置。每次保存 revision+1，用于生成时冻结快照识别旧请求。
     * 调用方必须已持有 fileMutex。
     */
    fun save(
        kbName: String,
        text: String,
        enabled: Boolean,
        expiry: IntentExpiry,
        expiryDate: String,
        status: IntentStatus
    ): IntentConfig {
        val current = read(kbName)
        val updated = IntentConfig(
            text = text,
            enabled = enabled,
            revision = current.revision + 1,
            expiry = expiry,
            expiryDate = expiryDate,
            status = status
        )
        var persisted = false
        storage.runWrite(kbName) {
            persisted = write(INTENT_FILE, json.encodeToString(IntentConfig.serializer(), updated))
        }
        if (!persisted) {
            storage.note("saveIntent 未落盘：$kbName 只读（schema 过新）或目录不可用")
        }
        return updated
    }

    /** 意图文件的解码口径：缺失、空、JSON 坏掉都回到"当前没有意图" */
    private fun decode(text: String): IntentConfig {
        if (text.isBlank()) return IntentConfig()
        return runCatching { json.decodeFromString<IntentConfig>(text) }.getOrDefault(IntentConfig())
    }

    companion object {
        private const val INTENT_FILE = "moment/intent.json"
    }
}
