package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.KnowledgeSchemaVersion
import com.lovebrain.app.domain.port.KnowledgeDocumentPort
import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.domain.port.KnowledgeRuntimePort
import com.lovebrain.app.model.ProfileTransactionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 知识库仓储 v3：基于「懂得/此刻/记忆」三层架构。
 *
 * 所有公开方法 suspend + withContext(Dispatchers.IO) 确保主线程无磁盘 IO；
 * read-modify-write 路径经 fileMutex.withLock 保护，防止并发读写冲突。
 *
 * 本类是**落盘边界**的唯一持有者：唯一的原子写、唯一的 canonical 守门、唯一的写者锁都留在这里，
 * 各格子（迁移、备份、枚举、文档、记忆、画像、归档、目录写侧）一律经自己那一份
 * 窄能力接口回到这三件，没有第二份实现。
 *
 * 「库的存在性」的写侧（建/删/切/改名）不在本类，归 [KnowledgeCatalogWriteStore]：
 * 页面要管库只需要那颗 [com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort]，
 * 不需要抱着整个仓库。首次启动那格也搬过去了，这里只留
 * [ensureInitialKnowledgeBase] 一行转手——它挂在 `KnowledgeRuntimePort` 上，
 * 调用方是首页启动流程，端口删不掉它。
 */
class KnowledgeRepository(
    internal val knowledgeRoot: File,
    internal val securePrefs: SecurePrefs,
    private val context: Context,
    private val appScope: CoroutineScope
) : KnowledgePort, KnowledgeDocumentPort, KnowledgeRuntimePort {
    internal val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    /** 文件操作互斥锁，防止多协程并发读写同一文件——全仓库唯一的一把 */
    internal val fileMutex = Mutex()

    /**
     * 迁移器那份受限视图的**实例**：只暴露 [KbStorageAccess] 上这几样能力。
     *
     * 类型写成端口而不是 `RepoStorage`，所以交出去的东西不超过迁移器本来就有的那些
     * （`RepoStorage` 自己是 private，模块内也认不出这个类型，转不成别的存储接口）。
     *
     * internal 只为测试能直接开「新入口」这一刀——越界相对路径经 [KbStorageAccess.atomicWriteAt]
     * 到底写不写得出去、返回值报不报得准，端口自己没别的调用方，产品码一行都不必碰它。
     */
    internal val migratorStorage: KnowledgeRepoStorage = KnowledgeRepoStorage(this)

    /**
     * schema 探测与旧库迁移。
     *
     * 它不持锁也不碰路径拼接，所有文件动作都经 [RepoStorage] 回到本类，
     * 所以"同一时刻只有一个写者"这条不变量不会因为拆类而散成两把锁。
     */
    internal val migrator = KnowledgeMigrator(migratorStorage)


    /**
     * 自动备份的策略（§5.3 拆出的第一格）：该复制什么、留几份、删哪些全在它里，
     * 写盘仍经 [RepoStorage] 回到本类唯一的 [atomicWriteText]。
     *
     * "什么时候要备份"的节流调度**故意留在本类**：外部 CoroutineScope 只能由协调器持有
     * （SingleOwnerContractTest 那条闸），仓库是这里唯一的启动者，把 launch 一起搬出去就违规。
     */
    internal val backup = KnowledgeBackupService(KnowledgeRepoStorage(this))

    /**
     * 目录枚举（§5.3 catalog 第一刀）：读哪些目录、什么算一个库、按什么排，全在它里。
     *
     * 这格拆出来不是因为仓库大，而是因为这件事**以前在本类里写了两遍**——
     * 公开的 `listAll()` 与无锁的枚举各一份，且只有一份会说话（记日志）。
     * 现在两个入口（本类的 [listAll] 与目录写侧经 [CatalogWriteStorage.entries] 的那一次）
     * 共用这一个实现。
     */
    internal val catalog = KnowledgeCatalogStore(KnowledgeRepoStorage(this))

    /**
     * 文档的安全路径 + 版本化读写（§5.3 第四格）。
     *
     * 搬出来的理由是一条真实的宽严不一：公开读路径过 canonical 守门，
     * 而无锁快速读 `readFileUnlockedFast` 直接 `File(File(root, kb), path)`——
     * 同一份内容，走哪个入口决定"边界"存不存在（独立复核报告里 P0 那条读路径的末段点名的就是它）。
     * 现在两个入口共用 [KnowledgeDocumentStore.resolve]，守门只有一处。
     *
     * 写仍然只有本类那一条链：文档格想落盘必须经 [RepoStorage.writeUnlocked] 回来，
     * 于是只读 schema 拒绝与备份节流都不会被新版本写绕过。
     */
    internal val documents = KnowledgeDocumentStore(KnowledgeRepoStorage(this))

    /**
     * 记忆纠正与库级 revision（§5.3 第五格）。
     *
     * 拆它的理由是一条**单调性**承诺以前散在两处：saveCorrection 与 undoCorrection
     * 各写一遍"递增并把 revision 落盘"，两处都得自己记得"纠正文件写成了才写 revision"。
     * 现在这条顺序只有一个所有者（KnowledgeMemoryStore.persist）。
     * 锁、路径守门、落盘仍然在本类：记忆格只拿到 read / writeTransaction / 编解码 /
     * kbExists / timestamp 五样能力。
     */
    internal val memory = KnowledgeMemoryStore(KnowledgeRepoStorage(this))

    /**
     * 画像正文、内容修订、阶段、状态向量与温度文件里的阶段标签（§5.3 第六格）。
     *
     * 拆它的理由有两条，都不是"仓库太大"：
     *  ① 同一份 warmth 内容以前有三种宽严——`readVector` 看得见旧布局回退、
     *     `writeVectorUnlocked` 看不见于是静默不写、阶段标签那条又走公开读；
     *  ② 九阶段白名单"拒绝时说不说、怎么说"在四处各写一遍，标签行改写两处各抄一份。
     * 现在向量维度表、阶段行正则、修订号输入清单都只有一个所有者，
     * `applyProfileUpdateAtomically` 的 strict 版也回来共用同一份改写规则。
     * 锁、路径守门、落盘、kb.json 的解码仍然在本类。
     */
    internal val profile = KnowledgeProfileStore(KnowledgeRepoStorage(this))

    /**
     * 画像事务性写入（§5.3 第八格）：`applyProfileUpdateAtomically` 的跨文件事务 + 备份 + 回滚 + 校验。
     * 画像格 [KnowledgeProfileStore] 注释里那行"它本体仍在仓库里等 archive 那一格"指的就是这一块。
     * 锁、路径守门、只读判定、落盘与备份节流仍在本类（见 [ProfileTxStorage] 那份实现）。
     */
    private val profileTx = KnowledgeProfileTransactionService(KnowledgeRepoStorage(this))

    /**
     * 话题归档的四步状态机（§5.3 第七格）。
     *
     * `rotateTopic` 原先是仓库里一段 80 行的方法体：步骤判定、归档条目格式、状态文件读写删、
     * kb.json 计数全混在一起，三件事没有名字。现在这四条规则在 [KnowledgeArchiveService]，
     * 锁与事务仍在本类——归档格拿到的是七样能力（note / 两把时钟 / 守门读 / 一次写事务 /
     * 状态编解码），没有 Mutex、没有 File、没有第二条落盘链。
     * 它比画像格宽一样是因为它确实管四步与一个状态文件；再宽就要开始拆"初始化"了。
     */
    private val archive = KnowledgeArchiveService(KnowledgeRepoStorage(this))

    /**
     * 「库的存在性」的写侧（§5.3 catalog 那一格的后半）：建、删、切当前库、改显示名、首次启动 seed。
     *
     * 交出去的是这个**对象**而不是仓库上的一层转手方法——页面注入
     * [com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort] 时拿到的就是它，
     * 于是"管库"这件事不再要求调用方抱着整个仓库（"KnowledgeRepository 不再是所有知识能力的唯一入口"）。
     * 它拿到的能力清单见 [CatalogWriteStorage]：锁、路径、落盘三件都仍在本类。
     *
     * internal 只为测试与 di/AppModule.kt 能直接拿到那位主人——绑定端口时指回这里，
     * 而不是再造一个包装（两个持有文件系统的对象就等于没有唯一入口）。
     */
    internal val catalogWrites = KnowledgeCatalogWriteStore(KnowledgeRepoStorage(this))

    /**
     * 谈心日志格（§5.3 后续）：两段式追加 + 实际发送记录 upsert + 分析节读取。
     * 锁、路径守门、落盘仍在本类（见 [CounselingStorage] 那份实现）。
     */
    private val counseling = KnowledgeCounselingService(KnowledgeRepoStorage(this))

    /**
     * 持续意图格（§5.3 后续）：`moment/intent.json` 的读/写 + revision 递增。
     * 锁、路径守门、落盘仍在本类（见 [IntentStorage] 那份实现）。
     */
    private val intent = KnowledgeIntentService(KnowledgeRepoStorage(this))

    /**
     * 话题文本格（§5.3 后续）：话题标签读写 + 计划事项解析 + 话题年龄。
     * 锁、路径守门、落盘仍在本类（见 [TopicTextStorage] 那份实现）。
     */
    private val topicText = KnowledgeTopicTextService(KnowledgeRepoStorage(this))

    /**
     * revision-check 写入格（§5.3 后续）：带修订版本条件校验的原子写入。
     * 锁、路径守门、落盘仍在本类（见 [RevisionCheckStorage] 那份实现）。
     */
    private val revisionCheck = KnowledgeRevisionCheckedWriteService(KnowledgeRepoStorage(this))

    /** 备份节流：记录最后一次写入时间，debounce 5s 后触发增量备份 */
    private val backupDebounceMs = 5_000L
    private val lastWriteTimestamp = AtomicLong(0L)
    private var backupDebounceJob: Job? = null

    init {
        knowledgeRoot.mkdirs()
        // 启动时自动备份（使用 applicationScope 替代 GlobalScope，生命周期可管理）
        //  所有 launch 必须包 SupervisorJob + ExceptionHandler
        // 备份经 fileMutex 序列化，与 delete 互斥防竞态
        appScope.launch(Dispatchers.IO + SupervisorJob()) {
            try {
                backupIfNeeded()
            } catch (e: CancellationException) {
                // 作用域被取消不是"备份失败"，记成失败会丢真相
                throw e
            } catch (e: Exception) {
                com.lovebrain.app.util.L.e("backup init failed", e)
            }
        }
    }

    /**
     * 写入后触发节流备份：每次写入操作调用此方法，5s 内无新写入则触发一次增量备份。
     * 调研依据：kotlinx.coroutines debounce 模式 + Android 文件 I/O 最佳实践。
     */
    /**
     * 写入后触发节流备份：5s 内没有新写入才真的备份一次。
     *
     * 任务体里再核一次时间戳——`delay` 期间可能又来了新写入，那一班就该让给下一班。
     */
    internal fun scheduleDebouncedBackup() {
        lastWriteTimestamp.set(System.currentTimeMillis())
        backupDebounceJob?.cancel()
        backupDebounceJob = appScope.launch(Dispatchers.IO + SupervisorJob()) {
            delay(backupDebounceMs)
            // 再次确认：delay 期间没有新的写入（时间戳没变）
            if (System.currentTimeMillis() - lastWriteTimestamp.get() >= backupDebounceMs - 100L) {
                try {
                    backupIfNeeded()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    com.lovebrain.app.util.L.e("debounce backup failed", e)
                }
            }
        }
    }

    // ═══════════ 自动备份：调度在本类，策略在 KnowledgeBackupService ═══════════

    /**
     * 序列化备份过程——与 delete 共用 fileMutex，防止并发竞态。
     * 备份最多 12 小时一次、知识库主要是文本，短暂锁住文件更新的成本可接受。
     */
    private suspend fun backupIfNeeded() {
        fileMutex.withLock {
            backup.backupIfNeededUnlocked()
        }
    }


    /** 该库当前是否可写（schema 过新时为 false） */
    fun isWritable(kbName: String): Boolean = !migrator.isReadOnly(kbName)

    // ═══════════ 唯一写边界（独立复核 2026-09-24）═══════════

    /** 一次 [transaction] 的结果。三个分支各自对应"有没有写过字节"。 */
    sealed interface WriteResult<out T> {
        /** 事务里的写都真的落盘了 */
        data class Written<out T>(val value: T) : WriteResult<T>

        /** 库的 schema 比本 App 还新：整段事务一个字节都没写 */
        data object RefusedNewerSchema : WriteResult<Nothing>

        /** 库不存在或已被删除：同样什么都没写 */
        data object MissingLibrary : WriteResult<Nothing>
    }


    /**
     * 公开写入口：一次事务、一个库、一把锁。
     *
     * 只读判定在**入口**做一次，所以 block 里不需要再写任何 schema if；
     * block 返回即事务结束。失败原因用类型给出，不靠调用方猜 null。
     */
    internal suspend fun <T> transaction(
        kb: com.lovebrain.app.model.KbName,
        block: KnowledgeTx.() -> T
    ): WriteResult<T> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kb.value)) return@withLock WriteResult.MissingLibrary
            if (migrator.isReadOnly(kb.value)) return@withLock WriteResult.RefusedNewerSchema
            WriteResult.Written(KnowledgeTx(this@KnowledgeRepository, kb.value).block())
        }
    }


    // ═══════════ schema 版本与迁移（实现见 KnowledgeMigrator）═══════════

    /** 该库是否因 schema 过新而进入只读保护 */
    fun isSchemaReadOnly(kbName: String): Boolean = migrator.isReadOnly(kbName)

    /**
     * 库当前的 schema 版本（对外只读，供升级断言与诊断使用）。
     * 读不到 .schema_version 时按 legacy marker 推断，与迁移判定同一把尺。
     */
    suspend fun schemaVersion(kbName: String): Int = withContext(Dispatchers.IO) {
        migrator.detectVersion(kbName)
    }

    /** 进入知识库前补齐/迁移结构；整段在文件互斥锁内执行 */
    override suspend fun migrateIfNeeded(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock { migrator.migrateUnlocked(kbName) }
    }

    // ═══════════ 默认知识库初始化（判断与 seed 归 KnowledgeCatalogWriteStore）═══════════

    /**
     * 确保应用至少有一个合法知识库。应用初始化唯一入口。
     *
     * 七条规则与那 13 格 seed 的判定现在都在 [catalogWrites] 里，这里只剩一次转手——
     * 留这一行不是舍不得删，是这颗口挂在 `KnowledgeRuntimePort` 上：调用它的是首页启动流程
     * （`LoveBrainViewModel`），不是管理页那颗目录端口。端口不动它，本类就不能把它撤了。
     * 只读判定、锁、路径守门、落盘四件仍然一样在本类那一侧（见 [CatalogWriteStorage]）。
     */
    override suspend fun ensureInitialKnowledgeBase() = catalogWrites.ensureInitialKnowledgeBase()

    // ═══════════ 公开 API ═══════════

    /**
     * 列出所有知识库。判定（隐藏目录、name 与目录名等值、坏元数据丢弃、按 updatedAt 倒序）
     * 在 [KnowledgeCatalogStore]，与无锁路径同一条尺。
     */
    override suspend fun listAll(): List<KnowledgeBase> = withContext(Dispatchers.IO) {
        catalog.list()
    }

    override suspend fun getActive(): KnowledgeBase? = withContext(Dispatchers.IO) {
        val all = listAll()
        val activeName = securePrefs.activeKbName
        all.firstOrNull { it.name == activeName && it.active }
            ?: all.firstOrNull { it.name == activeName }
            ?: all.firstOrNull()
    }

    // ═══════════ 库的存在性（建 / 删 / 切当前 / 改名）归 KnowledgeCatalogWriteStore ═══════════
    //
    // 这四个不再在本类上露面：`create` / `delete` / `setActive` / `updateDisplayName` 连同
    // "切库要逐库改 kb.json""删成功才删备份""名字过长先拒"三套判断一起搬进 [catalogWrites]。
    // 页面与测试都从 `KnowledgeBaseCatalogPort` 拿它们，那颗端口现在由 [catalogWrites] 实现。
    // 本类留下的是它们要问的四件：唯一那把锁、唯一那条写链、唯一那道路径守门、唯一的编解码尺
    // （见 [CatalogWriteStorage] 那份实现）。

    // ═══════════ 持续意图（每 KB 一份，moment/intent.json） ═══════════

    /**
     * 读取持续意图配置。
     *
     * §5.3 画像格量读路径时发现的第三处裸路径：以前这里 `File(File(knowledgeRoot, kbName),
     * "moment/intent.json")` 自己拼，库名带 `..` 就能把库外那份意图读回调用方手里。
     * 现在与公开读共用 [KnowledgeDocumentStore.read] 那一道门。
     */
    override suspend fun readIntent(kbName: String): IntentConfig = withContext(Dispatchers.IO) {
        intent.read(kbName)
    }

    /** 保存持续意图配置。每次保存 revision+1，用于生成时冻结快照识别旧请求。
     *  支持有效期和完成状态。
     *  ⚠ 形参的默认值住在端口那一侧（`KnowledgeRuntimePort.saveIntent`）：
     *  Kotlin 不许覆写方再写一遍默认值，省略参数的调用点仍会拿到同一组默认。 */
    override suspend fun saveIntent(
        kbName: String,
        text: String,
        enabled: Boolean,
        expiry: com.lovebrain.app.model.IntentExpiry,
        expiryDate: String,
        status: com.lovebrain.app.model.IntentStatus
    ): IntentConfig = withContext(Dispatchers.IO) {
        fileMutex.withLock { intent.save(kbName, text, enabled, expiry, expiryDate, status) }
    }

    // ═══════════ 记忆纠正（每 KB 一份，memory/corrections.json） ═══════════

    /** 读取纠正记录列表。返回 memoryId → correction 映射。 */
    override suspend fun readCorrections(kbName: String): Map<String, com.lovebrain.app.model.MemoryCorrection> =
        withContext(Dispatchers.IO) { memory.corrections(kbName) }

    /** 保存一条纠正记录。revision 单调递增（库级），不会因撤销倒退。
     * R07: 不再使用剩余记录的 max 推算 revision（撤销删除后可能倒退）。
     * 改为读取库级持久化 revision 标记，每次纠正/撤销均递增。 */
    /**
     * 保存一条纠正（§5.3 第五格之后只剩锁与转发）。
     *
     * revision 单调、"纠正文件写成了才允许写 revision"这条顺序都在 [KnowledgeMemoryStore.save]；
     * 被只读保护挡下时如实返回 false，不报"成功却没落盘"。
     *
     * ⚠ 形参的默认值住在端口那一侧（`KnowledgeRuntimePort.saveCorrection`），理由同 saveIntent。
     */
    override suspend fun saveCorrection(
        kbName: String,
        memoryId: String,
        action: com.lovebrain.app.model.CorrectionAction,
        replacementText: String,
        targetKbId: String,
        muteDuration: com.lovebrain.app.model.MuteDuration
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            memory.save(kbName, memoryId, action, replacementText, targetKbId, muteDuration)
        }
    }

    /** 撤销纠正 — 删除指定 memoryId 的纠正记录。
     * R07: 撤销也递增库级 revision，保证单调性。 */
    /**
     * 撤销一条纠正。撤销同样递增 revision（0→1→0 是最容易被破的单调性），
     * 规则住在 [KnowledgeMemoryStore.undo] 里，这里只保留锁与转发。
     */
    override suspend fun undoCorrection(kbName: String, memoryId: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock { memory.undo(kbName, memoryId) }
    }

    /** 获取纠正记录的全局 revision（用于后台防护）。
     * R07: 读取库级持久化 revision（单调递增），不依赖剩余记录 max。
     * b3-8: 加锁读取，保证一致性（原先无锁读可能读到半写状态） */
    override suspend fun getCorrectionsRevision(kbName: String): Int = withContext(Dispatchers.IO) {
        fileMutex.withLock { memory.revisionOf(kbName) }
    }

    /** R07: 原子读取纠正记录和 revision——用于生成准备阶段一次性快照。
     * 消除读取纠正和读取 revision 之间的竞态窗口。 */
    override suspend fun readCorrectionsAndRevision(kbName: String): Pair<Map<String, com.lovebrain.app.model.MemoryCorrection>, Int> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            memory.correctionSnapshot(kbName)
        }
    }

    /** b3-8: 带修订版本条件校验的原子追加——在锁内一次性完成 revision 检查和文件写入，
     * 消除 KnowledgeTriggerCoordinator 中先检查后写入的竞态窗口。
     * @return true = 写入成功，false = revision 已变或 KB 不存在 */
    override suspend fun appendFileWithRevisionCheck(
        kbName: String,
        relativePath: String,
        content: String,
        expectedRevision: Int
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock { revisionCheck.appendFileWithRevisionCheck(kbName, relativePath, content, expectedRevision) }
    }

    /** b3-8: 带修订版本条件校验的原子写入——在锁内一次性完成 revision 检查和文件写入。
     * @return true = 写入成功，false = revision 已变或 KB 不存在 */
    suspend fun writeFileWithRevisionCheck(
        kbName: String,
        relativePath: String,
        content: String,
        expectedRevision: Int
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock { revisionCheck.writeFileWithRevisionCheck(kbName, relativePath, content, expectedRevision) }
    }

    /** b3-8: 带修订版本条件校验的向量写入——在锁内一次性完成 revision 检查和向量写入。
     * @return true = 写入成功，false = revision 已变或 KB 不存在 */
    override suspend fun writeVectorWithRevisionCheck(
        kbName: String,
        values: Map<String, Int>,
        expectedRevision: Int
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock { revisionCheck.writeVectorWithRevisionCheck(kbName, values, expectedRevision) }
    }

    /**
     * 读取文件（自动兼容新旧路径）。
     *
     * 实现已归 [KnowledgeDocumentStore]；这里只剩转发，公开 API 不变。
     */
    override suspend fun readFile(kbName: String, relativePath: String): String =
        withContext(Dispatchers.IO) { documents.read(kbName, relativePath) }

    // ═══════════ canonical 路径边界 ═══════════

    /** 把裸字符串库名校验成 [KbName]（判据见 [KnowledgeDocumentStore.toKbName]） */
    fun toKbName(raw: String): com.lovebrain.app.model.KbName? = documents.toKbName(raw)

    /** 把裸字符串相对路径校验成 [KbRelativePath] */
    fun toKbPath(raw: String): com.lovebrain.app.model.KbRelativePath? = documents.toKbPath(raw)

    /** 线程安全的文件追加（fileMutex 锁 + I/O 线程；A2-5 合并原 appendFileSafe）
     *  目标 KB 已删除时 no-op，不自动 mkdirs 复活 */
    override suspend fun appendFile(kbName: String, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("appendFile skipped: kb no longer exists")
                return@withLock
            }
            appendFileUnlocked(kbName, relativePath, content)
        }
    }

    /** 线程安全的文件删除（fileMutex 锁 + I/O 线程） */
    override suspend fun deleteFile(kbName: String, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) return@withLock false
            if (refusedByReadOnlySchema(kbName, "deleteFile", relativePath)) return@withLock false
            val file = safeKbFile(kbName, relativePath) ?: return@withLock false
            if (file.exists()) file.delete() else false
        }
    }

    /** 线程安全的文件写入（fileMutex 锁 + I/O 线程；A2-5 合并原 writeFileSafe）
     *  目标 KB 已删除时 no-op，不自动 mkdirs 复活 */
    override suspend fun writeFile(kbName: String, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("writeFile skipped: kb no longer exists")
                return@withLock
            }
            writeFileUnlocked(kbName, relativePath, content)
        }
    }

    /**
     * 带版本校验的文件写入——防止编辑覆盖后台新增。
     * 调用方在读取文件时获得 [expectedVersion]（文件内容的 SHA-256），
     * 写入时校验磁盘上的文件是否仍为该版本。
     * 如果文件已被修改（后台追加等），拒绝写入并返回 null，调用方保留草稿。
     * 成功写入后返回新内容的 SHA-256 作为新版本号，调用方应更新本地版本快照。
     *
     * @return 新版本号（SHA-256）=写入成功，null=版本冲突或 KB 不存在
     */
    override suspend fun writeFileWithVersion(
        kbName: String, relativePath: String, content: String, expectedVersion: String
    ): String? = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            documents.writeWithVersion(kbName, relativePath, content, expectedVersion)
        }
    }

    /**
     * 读取文件并返回内容 + 版本号（SHA-256）。
     * 调用方持有版本号，写入时传给 [writeFileWithVersion] 做冲突检测。
     */
    override suspend fun readFileWithVersion(kbName: String, relativePath: String): Pair<String, String> =
        withContext(Dispatchers.IO) { documents.readWithVersion(kbName, relativePath) }

    /** 对外暴露的内容哈希——供 KbEdit 无版本校验路径生成新版本号 */
    override fun hashContent(text: String): String = documents.hashContent(text)

    /**  目标 KB 已删除时 no-op */
    suspend fun incrementTurnCount(kbName: String) = incrementTurnCountBy(kbName, 1)

    /**
     * 按 WAL 事件中记录的增量推进轮次计数。
     *
     * 恢复路径必须读事件里的 `turnCountIncrement` 值，
     * 而不是硬编码 +1——否则一次记录 2 轮的事务恢复后只补 1。
     */
    override suspend fun incrementTurnCountBy(kbName: String, delta: Int): Unit = withContext(Dispatchers.IO) {
        if (delta <= 0) return@withContext
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("incrementTurnCount skipped: kb no longer exists")
                return@withLock
            }
            transactionUnlocked(kbName) {
                updateMeta { kb -> kb.copy(turnCount = kb.turnCount + delta, updatedAt = isoNow()) }
            }
        }
    }

    /** 读取当前轮次数（kb.json 的 turnCount 字段），供 OngoingContextSelector 冷却逻辑使用 */
    override suspend fun getTurnCount(kbName: String): Int = withContext(Dispatchers.IO) {
        readMetaUnlocked(kbName)?.turnCount ?: 0
    }

    /**
     * 关系画像正文。
     *
     * GenerationInput 的 `kbContext.profile` 历史上被写死成空串，
     * 于是"冻结输入"里根本没有画像，画像变化也就无从参与身份比对。
     * 拼哪四份、按什么顺序，归 [KnowledgeProfileStore]。
     */
    override suspend fun readProfile(kbName: String): String = withContext(Dispatchers.IO) {
        profile.profileText(kbName)
    }

    /** 知识内容修订号（判据与输入文件清单见 [KnowledgeProfileStore.contentRevision]） */
    override suspend fun contentRevision(kbName: String): String = withContext(Dispatchers.IO) {
        profile.contentRevision(kbName)
    }

    /** 读取当前阶段（kb.json）；读不到或库在根外时给空串 */
    override suspend fun getCurrentStage(kbName: String): String = withContext(Dispatchers.IO) {
        profile.stageOf(kbName)
    }

    /** 设置知识库阶段标签（onboarding 推断 / 向量重估触发阶段变化时用）。写入前经 StageCatalog 归一化
     *  目标 KB 已删除时 no-op */
    override suspend fun updateStage(kbName: String, stage: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("updateStage skipped: kb no longer exists")
                return@withLock
            }
            updateStageUnlocked(kbName, stage)
        }
    }

    /** 读取 warmth.md 的五维状态向量（解析不到默认 50） */
    override suspend fun readVector(kbName: String): Map<String, Int> = withContext(Dispatchers.IO) {
        profile.vectorOf(kbName)
    }

    /** 就地更新 warmth.md 的五维状态向量数值
     *  目标 KB 已删除时 no-op */
    suspend fun writeVector(kbName: String, values: Map<String, Int>) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("writeVector skipped: kb no longer exists")
                return@withLock
            }
            writeVectorUnlocked(kbName, values)
        }
    }

    /** 就地更新 warmth.md 的阶段标签行（阶段变化时用），保留旧值作为历史注释。写入前经 StageCatalog 归一化
     *  目标 KB 已删除时 no-op */
    override suspend fun updateWarmthStageLabel(kbName: String, newStage: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("updateWarmthStageLabel skipped: kb no longer exists")
                return@withLock
            }
            updateWarmthStageLabelUnlocked(kbName, newStage)
        }
    }

    /**
     * 画像更新事务性写入——只剩一次转手：锁 + IO 线程 + 转给画像事务格。跨文件事务、备份、回滚、
     * 回滚后校验都在 [KnowledgeProfileTransactionService.apply]。**写链未变**：落盘仍经
     * `writeFileUnlocked` → `atomicWriteText`，路径仍经 `safeKbFile`，只读判定仍在入口。
     */
    override suspend fun applyProfileUpdateAtomically(
        kbName: String,
        me: String?,
        her: String?,
        warmth: String?,
        stageChanged: Boolean,
        newStage: String?,
        expectedRevision: Int
    ): ProfileTransactionResult = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            profileTx.apply(kbName, me, her, warmth, stageChanged, newStage, expectedRevision)
        }
    }


    // ═══════════ 谈心日志（两段式）— 逻辑见 KnowledgeCounselingService ═══════════

    /**
     * 谈心日志两段式追加：recordEntry 写入「# 谈心记录」节，analysisEntry 写入「# 军师分析」节。
     * 固定代码写入、全量不截断。旧格式文件（没有两个 # 大标题）自动迁移：旧内容并入第一节。
     *  目标 KB 已删除时 no-op
     */
    override suspend fun appendCounselingEntries(kbName: String, recordEntry: String, analysisEntry: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock { counseling.appendCounselingEntries(kbName, recordEntry, analysisEntry) }
    }

    /**
     * 原子追加"实际发送"记录——在单次 fileMutex.withLock 中完成：
     * 1. 校验 KB 仍存在
     * 2. 读取 recent.md
     * 3. 追加发送记录
     * 4. 原子写入
     * 5. 返回 true（成功）或 false（KB 不存在）
     *
     * 消除 ViewModel 中 listAll → readFile → writeFile 的 TOCTOU 竞态。
     */
    override suspend fun appendActualSentRecord(kbName: String, entry: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock { counseling.appendActualSentRecord(kbName, entry) }
    }

    /** 替换同一 generationVersionId 的旧 actual sent 记录（upsert）。
     * 在单次 fileMutex.withLock 中完成：检查 KB → 读取 → 替换 → 写入 → 返回 Boolean。
     * 如果 oldEntry 在 recent.md 中不存在，返回 false（不执行无效写入）。 */
    override suspend fun replaceActualSentRecord(kbName: String, oldEntry: String, newEntry: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock { counseling.replaceActualSentRecord(kbName, oldEntry, newEntry) }
    }

    /** 读取谈心日志「# 军师分析」节的最近 count 个 ## 块（供画像更新引擎） */
    override suspend fun readCounselingAnalysisBlocks(kbName: String, count: Int): String = withContext(Dispatchers.IO) {
        counseling.readAnalysisBlocks(kbName, count)
    }

    // ═══════════ 话题管理 API — 逻辑见 KnowledgeTopicTextService ═══════════

    /** 话题行的读法在 [KbTextOps.topicLabel]，与四处写侧同一个所有者 */
    override suspend fun getCurrentTopic(kbName: String): String = withContext(Dispatchers.IO) {
        topicText.currentTopic(kbName)
    }

    override suspend fun setCurrentTopic(kbName: String, topicLabel: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock { topicText.setCurrentTopic(kbName, topicLabel) }
    }

    /** 读取 plan.md「## 进行中」分区的事项行（注入 prompt；已结束不注入） */
    override suspend fun readPlanActive(kbName: String): String = withContext(Dispatchers.IO) {
        topicText.readPlanActive(kbName)
    }

    /** 获取当前话题的年龄（小时），用于时间衰减判断 */
    override suspend fun getTopicAgeHours(kbName: String): Int = withContext(Dispatchers.IO) {
        topicText.topicAgeHours(kbName)
    }

    // ═══════════ 话题归档（§5.3 第七格：实现见 KnowledgeArchiveService）═══════════

    /**
     * rotateTopic — 使用累积操作状态实现幂等和中断恢复。
     *
     * 四步（读输入 → 追加归档条目 → 计数 +1 → 清空四个源文件）与"信任状态文件里记录的
     * 已完成步骤"这套恢复判定，都在 [KnowledgeArchiveService.rotate]；这里只剩"一次锁 + 一次 IO 线程"。
     *
     * 搬走的原因是三件事原先没有名字：哪些步骤算完成、归档条目长什么样、计数从哪回填——
     * 它们与 kb.json 的读写挤在同一段 80 行的方法体里，只能连着临时目录和互斥锁间接测。
     */
    override suspend fun rotateTopic(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock { archive.rotate(kbName) }
    }

    /**
     * 归档计数：kb.json 的 `topicCount`；旧库还没记数时从 `memory/raw_topic.md` 回填一次并持久化。
     *
     * 两处改动是本轮显式决定的：
     *  ① 原先"读 kb.json"与"回填"各自加一次锁，两次之间别人可以写完一份，回填就会拿旧口径
     *     覆盖一次计数——现在整段在一次锁内完成（收紧，不是搬家）；
     *  ② "怎么数一条归档"这条格式规则搬去 [KnowledgeArchiveService.countArchiveEntries]，
     *     与 rotate 那一步的 `topicCount + 1` 同出一门。**事务编排仍在这里**：
     *     归档格的能力接口已经七样，再为一次回填加"读 meta"就是第八样，那是拿拆类名义放宽端口。
     */
    override suspend fun getLessonCount(kbName: String): Int = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val counted = readMetaUnlocked(kbName)?.topicCount ?: 0
            if (counted > 0) return@withLock counted
            val content = documents.read(kbName, KnowledgeArchiveService.ARCHIVE_FILE)
            val regexCount = archive.countArchiveEntries(content)
            if (regexCount > 0) {
                transactionUnlocked(kbName) { updateMeta { kb -> kb.copy(topicCount = regexCount) } }
            }
            regexCount
        }
    }


    private fun readAsset(path: String): String {
        return runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    companion object {
        // 内容修订号的输入清单已随画像正文归 KnowledgeProfileStore，这里不再有第二份

        // 旧→新路径映射已随读路径一起归 KnowledgeDocumentStore，这里不再有第二份
        /**
         * 记忆格的两个文件相对路径。
         *
         * 只登记**名字**，不登记怎么拼：拼路径是文档格的守门职责（[KnowledgeDocumentStore.resolve]），
         * 读写两侧都用这两个常量，免得又出现"读不过门、写走门"那种宽严不一。
         */
        const val MEMORY_REVISION_FILE = "memory/.revision"
        const val CORRECTIONS_FILE = "memory/corrections.json"
        // rotateTopic 的状态文件名跟着归档格走（KnowledgeArchiveService.STATE_FILE），这里不留第二份

        /**
         * 根级标记与库目录的三层形状。
         *
         * 这三个名字只有本类认得到：`.kb_initialized` 与 `.trash` 不属于任何库
         * （`kbOwning` 对点开头的根级条目返回 null，所以它们不受只读保护），
         * 三层目录是"一个库长什么样"的唯一口径——建库与补齐都问 [makeCatalogSkeleton]，
         * 别处再抄一份目录名列表就等于第二个所有者。
         */
        const val INIT_MARKER_FILE = ".kb_initialized"
        internal const val LEGACY_TRASH_DIR = ".trash"
        internal val CATALOG_LAYERS = listOf("understand", "moment", "memory")
    }

    /** 从 assets/schema/ 加载知识库初始化模板（标题骨架 = 单一数据源） */
    internal fun loadSchema(name: String): String {
        return readAsset(com.lovebrain.app.domain.AssetRegistry.schema(name))
    }
}
