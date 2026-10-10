package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import java.io.File
import kotlinx.coroutines.sync.withLock

/**
 * 交给各格子用的受限视图：只暴露无锁原语，公开 API 仍然只在 [KnowledgeRepository] 上。
 *
 * 从 [KnowledgeRepository] 拆出（原 private inner class `RepoStorage`）。
 * 所有文件动作仍经本类回到 [KnowledgeRepository] 那一把锁、那一条写链、那一位路径守门，
 * 所以「同一时刻只有一个写者」这条不变量不会因为拆类而散成两把锁——
 * 本类不持锁、不拼路径、不另配一把 Json 尺，全部回仓库那一侧。
 *
 * 本类是无状态的：只持一个仓库引用，多个格子各拿一个实例也不影响不变量。
 */
internal class KnowledgeRepoStorage(private val repo: KnowledgeRepository) : KbStorageAccess, BackupStorage, CatalogStorage, DocumentStorage,
    MemoryStorage, ProfileStorage, ArchiveStorage, CatalogWriteStorage, ProfileTxStorage,
    CounselingStorage, IntentStorage, TopicTextStorage, RevisionCheckStorage, LessonsRewriteStorage {
    override val root: File get() = repo.knowledgeRoot
    override val catalogRoot: File get() = repo.knowledgeRoot

    /** 枚举用的解析走仓库那一份 Json 配置——不给第二个类另配一把尺 */
    override fun decodeMeta(text: String): KnowledgeBase? =
        runCatching { repo.json.decodeFromString<KnowledgeBase>(text) }.getOrNull()

    /** 目录被挡下的原因只有仓库知道该不该说、怎么说；观测留在这里，不在策略类里 */
    override fun onMetaRejected(dirName: String, reason: String) {
        com.lovebrain.app.util.L.w("知识库元数据异常已忽略：dir=$dirName reason=$reason")
    }

    /** 文档格的观测出口：同一个日志器，不给第二个类开一条自己的日志通道 */
    override fun note(message: String) {
        com.lovebrain.app.util.L.w(message)
    }

    /** 文档格判断"库还在不在"用无锁那一份——锁由调用方在外面套 */
    override fun kbExists(kbName: String): Boolean = repo.kbExistsUnlocked(kbName)

    /** 记忆格的读也走文档格那道守门：两个格子共用同一个路径边界，不各写一套 */
    override fun read(kbName: String, relativePath: String): String =
        repo.documents.read(kbName, relativePath)

    /** 记忆格只能经这一次写事务落盘：只读判定与"一批要么都写要么都不写"都在这 */
    override fun writeTransaction(kbName: String, block: MemoryTx.() -> Unit) {
        repo.transactionUnlocked(kbName) {
            MemoryTx { relativePath, content -> write(relativePath, content) }.block()
        }
    }

    /**
     * 意图格的一次写事务——与 [writeTransaction] 同形，名字刻意不同：
     * `MemoryTx.() -> Unit` 与 `IntentTx.() -> Unit` 都擦除成 `Function1`，
     * 同名就是 platform declaration clash。
     */
    override fun runWrite(kbName: String, block: IntentTx.() -> Unit) {
        repo.transactionUnlocked(kbName) {
            IntentTx { relativePath, content -> write(relativePath, content) }.block()
        }
    }

    /**
     * 画像格只能经这一次**元数据**写事务改 kb.json。
     *
     * 与上面那条同一段事务语义（同一次 schema 判定），只是交出去的能力不同：
     * 画像格拿到的是"读改写 kb.json"，不是"想写哪个文件就写哪个文件"。
     * 名字与上面那条不同不是 stylistic：`MemoryTx.() -> Unit` 与 `ProfileTx.() -> Unit`
     * 都擦除成 `Function1`，同名 overload 在 JVM 上是 platform declaration clash——
     * 编译器报这一条要等编译，所以先把名字分开写清，别留给下一个人踩。
     */
    override fun writeMetaTransaction(kbName: String, block: ProfileTx.() -> Unit) {
        repo.transactionUnlocked(kbName) {
            block(ProfileTx { transform -> updateMeta(transform) })
        }
    }

    /** 画像格读 kb.json 用的就是仓库那一份解码口径（坏 JSON 当没有），不开第二把尺 */
    override fun metaOf(kbName: String): KnowledgeBase? = repo.readMetaUnlocked(kbName)

    /**
     * 归档格的落盘：四类动作都从同一个事务对象取。
     * 只读判定、锁、路径守门仍在仓库那一侧（`transactionUnlocked` + [KnowledgeRepository.KnowledgeTx]）。
     *
     * 名字刻意不叫 `writeTransaction`：`MemoryTx.() -> Unit` 与 `ArchiveTx.() -> Unit`
     * 都擦除成 `Function1`，同名就是 platform declaration clash（同一条坑这轮踩到第二次）。
     */
    override fun runTransaction(kbName: String, block: ArchiveTx.() -> Unit) {
        repo.transactionUnlocked(kbName) { ArchiveTxView(this).block() }
    }

    /**
     * 归档格的两把时钟。
     *
     * 分成两个名字不是设计癖好，是既存事实：归档条目标题与 operationId 用 `TimeFmt.now()`
     * （`2026-09-25 10:00`），而 kb.json 的 `updatedAt` 一直是 ISO-8601 带时区。
     * 合成一把就会悄悄换掉某一份落盘格式。
     */
    override fun stamp(): String = com.lovebrain.app.util.TimeFmt.now()
    override fun metaTimestamp(): String = repo.isoNow()

    /** 归档状态的编解码仍用仓库那一份 Json 配置 */
    override fun encodeState(state: ArchiveOperationState): String =
        repo.json.encodeToString(ArchiveOperationState.serializer(), state)

    override fun decodeState(text: String): ArchiveOperationState? = runCatching {
        repo.json.decodeFromString<ArchiveOperationState>(text)
    }.getOrNull()

    /** 编解码共用仓库那一份 Json 配置：给第二个类另配一把尺就等于换了把判据 */
    override fun decodeCorrections(
        text: String
    ): List<com.lovebrain.app.model.MemoryCorrection>? = runCatching {
        repo.json.decodeFromString<List<com.lovebrain.app.model.MemoryCorrection>>(text)
    }.getOrNull()

    override fun encodeCorrections(
        corrections: List<com.lovebrain.app.model.MemoryCorrection>
    ): String = repo.json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(
            com.lovebrain.app.model.MemoryCorrection.serializer()
        ),
        corrections
    )

    /**
     * 文档格的唯一写出口：回到仓库的 [KnowledgeRepository.writeFileUnlocked]。
     * 那里带着只读 schema 拒绝与备份节流，所以新版本写也不可能绕开它们。
     */
    override fun writeUnlocked(kbName: String, relativePath: String, content: String) =
        repo.writeFileUnlocked(kbName, relativePath, content)

    /** revision-check 格的追加写：回到仓库的 [KnowledgeRepository.appendFileUnlocked]（唯一写链） */
    override fun appendUnlocked(kbName: String, relativePath: String, content: String) =
        repo.appendFileUnlocked(kbName, relativePath, content)

    /** revision-check 格的向量写：回到画像格那一份 [KnowledgeProfileStore.setVector] */
    override fun writeVectorUnlocked(kbName: String, values: Map<String, Int>) {
        repo.profile.setVector(kbName, values)
    }

    /** revision-check 格读 revision：回到记忆格那一份 [KnowledgeMemoryStore.revisionOf] */
    override fun revisionOf(kbName: String): Int = repo.memory.revisionOf(kbName)

    // ═══ 「读—整理—条件替换」那一格（KnowledgeLessonsRewriteService）拿到的能力 ═══
    // 五样里没有一把锁、没有一个 File、没有一条落盘实现：读回文档格那道门，写回仓库那个登记在册的写核。

    /** 锁内守门读——与公开 `readFile` 同一道 canonical 判定，不开第二条读路径 */
    override fun readGuarded(kbName: String, relativePath: String): String = repo.documents.read(kbName, relativePath)

    /** 锁内写：`writeFileCheckedUnlocked`（唯一写链上"有没有真落盘"那一支），只读判定与备份节流都在下游 */
    override fun writeChecked(kbName: String, relativePath: String, content: String): Boolean =
        repo.writeFileCheckedUnlocked(kbName, relativePath, content)

    override fun atomicWriteAt(kbName: String, relativePath: String, content: String): Boolean {
        // 路径与落盘都交给仓库已有的那两样：safeKbFile（与公开读写同一个判定）+
        // writeFileCheckedUnlocked（唯一写链上登记的写核）。这里刻意不再新写一条
        // atomicWriteText、也不再自己拼 File——判定只有一处，宽严不可能分叉。
        //
        // 返回 false 从「异常状况」变成了「可以发生的拒绝」（守门认不下的库名/路径、
        // 只读库），但一条不留痕的拒绝等于静默跳过，所以照旧记一条错误级日志再报出去。
        if (repo.writeFileCheckedUnlocked(kbName, relativePath, content)) return true
        com.lovebrain.app.util.L.e(
            "migration write refused by the guard: $kbName/$relativePath", null
        )
        return false
    }
    /**
     * 备份写根级 marker：端口这一侧只交得出**文件名**，路径由仓库这侧的根级守门给。
     *
     * 原来那道 `guardedWrite(target: File, …)` 收的是调用方拼好的 File——与批次四关掉的
     * 迁移器那扇门同形，也是这笔欠账一直挂着的真正原因（不是因为它写 root 级文件，是因为它的**入参**是路径）。
     */
    override fun writeRootMarker(fileName: String, content: String): Boolean =
        repo.writeRootFileGuarded(fileName, content)
    override fun schema(name: String): String = repo.loadSchema(name)
    override suspend fun currentStage(kbName: String): String = repo.getCurrentStage(kbName)
    override suspend fun setStage(kbName: String, stage: String): Unit =
        repo.updateStageUnlocked(kbName, stage)
    override suspend fun setWarmthStageLabel(kbName: String, stage: String): Unit =
        repo.updateWarmthStageLabelUnlocked(kbName, stage)
    override fun timestamp(): String = repo.isoNow()

    // ═══ 目录写侧那一格（KnowledgeCatalogWriteStore）拿到的能力 ═══
    // 三件承重的事一件都没交出去：锁还是这一把（inWriteLock 只是把调用方请进来）、
    // 路径还是 safeKbFile 那一道（事务块里交出去的是 CatalogTx 而不是 File）、
    // 落盘还是唯一那条写链（transactionUnlocked → KnowledgeTx → writeFileCheckedUnlocked）。

    override suspend fun <T> inWriteLock(block: suspend () -> T): T = repo.fileMutex.withLock { block() }

    override fun writeCatalogTransaction(kbName: String, block: CatalogTx.() -> Unit) {
        repo.transactionUnlocked(kbName) { CatalogTxView(this).block() }
    }

    /** 根下所有非隐藏目录：切库要逐库改 kb.json，含还没元数据的那类目录，所以不经枚举格 */
    override fun catalogDirNames(): List<String> = repo.visibleKbDirs().map { it.name }

    override fun catalogDirPresent(kbName: String): Boolean = File(repo.knowledgeRoot, kbName).exists()

    /** 库目录 + 三层子目录；幂等，"新建"与"补齐"共用这一个形状定义 */
    override fun makeCatalogSkeleton(kbName: String) {
        val dir = File(repo.knowledgeRoot, kbName)
        dir.mkdirs()
        KnowledgeRepository.CATALOG_LAYERS.forEach { File(dir, it).mkdirs() }
    }

    /**
     * 删除整库目录。canonical 守卫刻意留在这里：判"这个目录在不在 knowledge/ 树内"
     * 全仓只许有一处口径，目录写侧想删就得问这一处，不许自己抄一份。
     */
    override fun removeCatalogDir(kbName: String): Boolean {
        val knowledgeRoot = repo.knowledgeRoot
        val dir = File(knowledgeRoot, kbName)
        val canonicalDirPath = dir.canonicalPath
        val canonicalRootPath = knowledgeRoot.canonicalPath
        if (!canonicalDirPath.startsWith(canonicalRootPath + File.separator)) return false
        if (!dir.exists()) return false
        val ok = dir.deleteRecursively()
        // 清理旧版本遗留的 .trash（若存在），一次性腾空
        File(knowledgeRoot, KnowledgeRepository.LEGACY_TRASH_DIR).takeIf { it.exists() }?.deleteRecursively()
        return ok
    }

    override fun deleteCatalogBackups(kbName: String) = repo.backup.deleteBackupsFor(kbName)

    override fun initMarkerPresent(): Boolean = File(repo.knowledgeRoot, KnowledgeRepository.INIT_MARKER_FILE).exists()

    /**
     * 全部 seed 落成了才由写侧调用；半套文件 + 标记 = 下次启动不再补。
     *
     * 路径与落盘都从根级守门取（[KnowledgeRepository.writeRootFileGuarded] → safeRootFile → 登记在册的写核），
     * 这里不再自己拼 `File(knowledgeRoot, …)` 再裸 `writeText`：那颗标记写的是 knowledge/ 根下，
     * 而根级那道门本来就在账上跑着（`.last_backup` 那一族用的就是它）。
     *
     * 返回 false = 名字被守门挡下、一个字节都没落。「没落」与「已经初始化过了」从此看不出差别，
     * 那正是一次不留痕的跳过，所以判掉它：留一条错误级日志，再把失败交给调用方。
     * 抛 [IllegalStateException] 而不是新增异常类型：本包已经是这个形状（见 DeepSeekRepository），
     * 而搬之前 `File.writeText` 失败就直接抛，「调用方会失败」这条语义得原样留着——
     * 调用方是 KnowledgeCatalogWriteStore 里那两处事务内调用，异常会穿过 inWriteLock 上抛。
     */
    override fun markInitialized() {
        if (!repo.writeRootFileGuarded(KnowledgeRepository.INIT_MARKER_FILE, "done")) {
            com.lovebrain.app.util.L.e("init marker did not land: ${KnowledgeRepository.INIT_MARKER_FILE}", null)
            throw IllegalStateException("init marker did not land: ${KnowledgeRepository.INIT_MARKER_FILE}")
        }
    }

    /** 枚举判据只有目录读侧那一份 */
    override fun entries(): List<KnowledgeBase> = repo.catalog.list()

    /**
     * 目录写侧判「这座库有没有真实用户内容」时读正文用的那一把尺。
     *
     * 与 [kbExists] / [read] 同一个来源（文档格那唯一一道守门），不为写侧另开一条读路径：
     * 越界与缺失都给空串，所以"读不到"永远不会被判成"有内容"，也就永远不会把一座真库吃掉。
     * 无锁——调用方（目录写侧）已经在那把锁里。
     */
    override fun readCatalogSlot(kbName: String, relativePath: String): String =
        repo.documents.read(kbName, relativePath)

    /** 编解码共用仓库那一份 Json 配置：给第二个类另配一把尺就等于换了判据 */
    override fun encodeMeta(kb: KnowledgeBase): String =
        repo.json.encodeToString(KnowledgeBase.serializer(), kb)

    override fun template(name: String): String = repo.loadSchema(name)

    override var activeKbName: String
        get() = repo.securePrefs.activeKbName
        set(value) { repo.securePrefs.activeKbName = value }

    override suspend fun migrateCatalogEntry(kbName: String) = repo.migrator.migrateUnlocked(kbName)

    /** 节流备份的启动者仍是仓库（外部 CoroutineScope 只能由协调器持有） */
    override fun scheduleBackup() = repo.scheduleDebouncedBackup()

    /** 当前库那三级回退的判据住在 [KnowledgeRepository.getActive]，目录写侧不抄第二份 */
    override suspend fun activeEntry(): KnowledgeBase? = repo.getActive()

    /** 阶段标签的主人画像格，白名单归一化与拒绝措辞都在那里 */
    override suspend fun writeStage(kbName: String, stage: String) = repo.updateStage(kbName, stage)

    /** 正文写回到 [KnowledgeRepository.writeFile]：只读判定、"库没了别复活"那两条都还在原处 */
    override suspend fun writeDocument(kbName: String, relativePath: String, content: String) =
        repo.writeFile(kbName, relativePath, content)

    // ═══ 画像事务格（KnowledgeProfileTransactionService）拿到的能力 ═══
    // 与归档格同形：锁、路径、只读判定、落盘四件仍在仓库。
    // read / writeUnlocked / kbExists / timestamp 与文档格/画像格同名同签，由上面那些 override 一处满足。

    override fun isReadOnly(kbName: String): Boolean = repo.migrator.isReadOnly(kbName)
    override fun memoryRevisionOf(kbName: String): Int = repo.memory.revisionOf(kbName)
    override fun readVector(kbName: String): Map<String, Int> = repo.profile.vectorOf(kbName)
    override fun writeVector(kbName: String, values: Map<String, Int>) {
        repo.profile.setVector(kbName, values)
    }
    override fun normalizeStage(raw: String, opLabel: String): String? =
        repo.profile.normalizeStage(raw, opLabel)
    override fun rewriteStageLine(warmth: String, stage: String): String =
        repo.profile.rewriteStageLine(warmth, stage)
    override fun resolvePath(kbName: String, relativePath: String): File? =
        repo.safeKbFile(kbName, relativePath)
    override fun <T> runTx(kbName: String, block: ProfileTxOps.() -> T): T =
        repo.transactionUnlocked(kbName) { ProfileTxOpsView(this).block() }
    // scheduleBackup() / timestamp() / read() / writeUnlocked() / kbExists() 与
    // CatalogStorage/DocumentStorage/ProfileStorage 同名同签，上面那些 override 一处满足。
}

/**
 * 把 [KnowledgeTx] 收窄成归档格能看见的四件事。
 *
 * 直接把 `KnowledgeTx` 交出去就等于把 `pathOf`（能拿 File）也交出去了——那正是
 * 说的"第二个所有者"的起点。适配层薄，但它让"格子里没有 File"这件事能被静态检查。
 */
internal class ArchiveTxView(private val tx: KnowledgeTx) : ArchiveTx {
    override fun write(relativePath: String, content: String): Boolean = tx.write(relativePath, content)
    override fun append(relativePath: String, content: String): Boolean = tx.append(relativePath, content)
    override fun delete(relativePath: String): Boolean = tx.deleteAt(relativePath)
    override fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean =
        tx.updateMeta(transform)
}

/**
 * 把 [KnowledgeTx] 收窄成目录写侧能看见的三件事（seed 写、改 meta、判某一格在不在）。
 *
 * 与 [ArchiveTxView] 同一用意，而且这里更要紧：建库与补齐原本都习惯先 `File(dir, path)`
 * 再问一句 `exists()`——那一步就是"第二个路径所有者"。现在判存在性也只能问
 * [KnowledgeTx.pathOf] 那一道守门，越界路径既写不进、也判不出存在。
 */
internal class CatalogTxView(private val tx: KnowledgeTx) : CatalogTx {
    override fun write(relativePath: String, content: String): Boolean = tx.write(relativePath, content)
    override fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean =
        tx.updateMeta(transform)
    override fun exists(relativePath: String): Boolean = tx.pathOf(relativePath)?.exists() == true
}

/** 把 [KnowledgeTx] 收窄成画像事务格能看见的四件事（与 [ArchiveTxView] 同一用意）。 */
internal class ProfileTxOpsView(private val tx: KnowledgeTx) : ProfileTxOps {
    override fun write(relativePath: String, content: String): Boolean = tx.write(relativePath, content)
    override fun readTextAt(relativePath: String): String = tx.readTextAt(relativePath)
    override fun deleteAt(relativePath: String): Boolean = tx.deleteAt(relativePath)
    override fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean =
        tx.updateMeta(transform)
}
