package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import java.io.File

/**
 * 枚举知识库目录需要的最小存储视图（§5.3 catalog 那一格的第一刀）。
 *
 * 刻意不是 [KbStorageAccess]，也不是 `BackupStorage`：这一格只**读目录、读元数据**，
 * 一个字都不写。给它一道写门就等于让"列一下有哪些库"这种操作拿到改库的权限（ISP）。
 *
 * 解析也不在这里做：仓库把它自己那份 `Json { ignoreUnknownKeys… }` 经 [decodeMeta] 注入进来。
 * 两处各 new 一份解析配置的后果是"同一份 kb.json 在两条路径上解出不同结果"，
 * 那种不一致没有任何测试能发现。
 */
internal interface CatalogStorage {
    /** knowledge/ 根目录 */
    val catalogRoot: File

    /** 用仓库那把尺解 kb.json；解不出来返回 null */
    fun decodeMeta(text: String): KnowledgeBase?

    /** 某个目录被挡下的原因。观测由仓库写，本类不直连日志也不直连 Android */
    fun onMetaRejected(dirName: String, reason: String)
}

/**
 * "这个根目录下现在有哪些知识库"——唯一的回答者。
 *
 * 拆它出来的直接理由不是"文件太大"，而是这件事此前在同一个类里**写了两遍**：
 * 公开的 `listAll()` 与无锁的 `listAllUnlocked()` 各有一份"扫目录 → 读 kb.json → 校验
 * name 与目录名等值 → 按 updatedAt 倒序"，两遍唯一的差别是公开那份被挡时会记一条日志。
 * 于是走哪条路径决定了两件事：读到的库顺序一样吗？出问题的库会被说出来吗？
 * ——第二个问题的答案此前是"不一定"。现在两条路径共用这一个实现，日志也只有一份判据。
 *
 * 三条语义必须原样保住（都有用例钉着）：
 * 1. 以 `.` 开头的目录不是库（`.backup`、`.kb_initialized` 那类根级标记不参与枚举）；
 * 2. **`kb.json` 里的 `name` 必须与目录名等值**，否则整条丢弃——这是防"元数据字段做路径遍历"
 *    的单一扼制点，所有消费端都从这里的返回值取名字；
 * 3. 缺 `kb.json`、内容坏掉，都是"丢弃这一条"而不是"抛出去"——一个坏库不该让列表页整页打不开。
 *
 * 不持锁、不写盘、不接 CoroutineScope：调用方负责它自己的互斥（见 `KnowledgeRepository.fileMutex`）。
 */
internal class KnowledgeCatalogStore(private val storage: CatalogStorage) {

    /** 无锁枚举。调用方（仓库）在需要时自己持有 fileMutex */
    fun list(): List<KnowledgeBase> =
        storage.catalogRoot.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { dir -> readOne(dir) }
            ?.sortedByDescending { it.updatedAt }
            ?.toList()
            ?: emptyList()

    private fun readOne(dir: File): KnowledgeBase? {
        val meta = File(dir, META_FILE)
        if (!meta.isFile) return null
        val text = runCatching { meta.readText(Charsets.UTF_8) }.getOrNull()
            ?: run {
                storage.onMetaRejected(dir.name, REASON_UNREADABLE)
                return null
            }
        val kb = storage.decodeMeta(text)
            ?: run {
                storage.onMetaRejected(dir.name, REASON_BAD_JSON)
                return null
            }
        // 单一扼制点：目录名才是身份，元数据里的 name 只是声明。不相等就当这条不存在。
        if (kb.name != dir.name) {
            storage.onMetaRejected(dir.name, REASON_NAME_MISMATCH)
            return null
        }
        return kb
    }

    companion object {
        const val META_FILE = "kb.json"
        const val REASON_NAME_MISMATCH = "name-mismatch"
        const val REASON_BAD_JSON = "undecodable"
        const val REASON_UNREADABLE = "unreadable"
    }
}
