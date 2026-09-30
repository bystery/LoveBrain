package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import java.io.File

/**
 * 一个知识库的写事务句柄——Repository 里唯一被允许"拿到路径 + 写文件"的入口。
 *
 * 从 [KnowledgeRepository] 拆出（原 `inner class KnowledgeTx`）。它持一个仓库引用，
 * 所有路径与落盘仍回仓库那一侧：`safeKbFile` / `writeFileCheckedUnlocked` /
 * `appendFileCheckedUnlocked` / `safeRootFile` / `writeRootFileGuarded` / `migrator` / `json`，
 * 所以"同一时刻只有一个写者"这条不变量不会因为拆类而散成两把锁。
 *
 * 为什么要有它（2026-09-24 独立复核）：以前每个新方法都可能顺手
 * `atomicWriteText(File(dir, "kb.json"), …)`，于是"schema 过新即只读"要靠
 * 十几处 if 各自记得写。现在 mutation 只能从这个对象取得安全路径与原子写能力，
 * 判定集中在 [KnowledgeRepository.transaction] 入口 + [atomicWriteText] 出口两处。
 *
 * 构造点是 internal：外部拿不到一个"没经过只读判定"的 Tx。
 */
internal class KnowledgeTx internal constructor(
    private val repo: KnowledgeRepository,
    val kbName: String
) {

    // 方法名刻意带 At 后缀：取消审计按**名字**判断块体里有没有挂起调用，
    // 而仓库里已经有 `suspend fun read` / 大量 delete 语义，叫裸名会被虚报成
    // "catch 吞掉挂起调用"。名字撞车会让那条门禁变成噪声，噪声一旦被接受就等于没有门禁。

    /** 相对路径 → 安全绝对路径；越界或非法输入返回 null（绝不落到库目录之外） */
    fun pathOf(relativePath: String): File? = repo.safeKbFile(kbName, relativePath)

    /** 读；越界、非法或不存在都得到空串 */
    fun readTextAt(relativePath: String): String {
        val f = pathOf(relativePath) ?: return ""
        return if (f.exists()) runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("") else ""
    }

    /** 原子写。false = 被只读保护或路径非法挡下，一个字节都没落。 */
    fun write(relativePath: String, content: String): Boolean =
        repo.writeFileCheckedUnlocked(kbName, relativePath, content)

    /** 原子追加，语义同 [write] */
    fun append(relativePath: String, content: String): Boolean =
        repo.appendFileCheckedUnlocked(kbName, relativePath, content)

    /**
     * 根级 marker（`.last_backup` 那一族）的安全路径：与 [pathOf] 同一条纪律、同一位所有者，
     * 只是解析基准换成 knowledge/ 根。那个文件按设计不属于任何库，`safeKbFile` 表达不了它，
     * 而"表达不了"从今天起不再等于"可以不守门"：只收裸文件名，越界一律 null。
     */
    fun rootFile(fileName: String): File? = repo.safeRootFile(fileName)

    /**
     * 写一个根级 marker。false = 名字被守门挡下，一个字节都没落（并且留一条错误级日志）。
     *
     * 落盘仍在唯一写链上（[KnowledgeRepository.writeFileCheckedUnlocked] 的「路径已解析」那一支），只读判定在它下游的
     * [atomicWriteText] 里按归属判——根级文件不属于任何库，那道判定与搬之前一样是放行。
     */
    fun writeRootMarker(fileName: String, content: String): Boolean =
        repo.writeRootFileGuarded(fileName, content)

    /** 删除。false = 没删（被挡、越界或本来就不存在）。 */
    fun deleteAt(relativePath: String): Boolean {
        if (repo.migrator.isReadOnly(kbName)) return false
        val f = pathOf(relativePath) ?: return false
        return f.exists() && f.delete()
    }

    /** 读改写 kb.json；库不存在 / 只读 / JSON 坏掉都返回 false 且不写 */
    fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean {
        if (repo.migrator.isReadOnly(kbName)) return false
        val raw = readTextAt("kb.json")
        if (raw.isBlank()) return false
        val kb = runCatching { repo.json.decodeFromString<KnowledgeBase>(raw) }.getOrNull() ?: return false
        return write("kb.json", repo.json.encodeToString(KnowledgeBase.serializer(), transform(kb)))
    }
}
