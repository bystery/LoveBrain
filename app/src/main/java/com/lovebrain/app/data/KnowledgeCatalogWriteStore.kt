package com.lovebrain.app.data

import com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort
import com.lovebrain.app.model.KB_NAME_MAX_LENGTH
import com.lovebrain.app.model.KnowledgeBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 仓库交给目录写侧那一格的能力清单。
 *
 * ## 为什么这一格比 §5.3 别家宽，而宽了仍然没有第二个所有者
 * 别家（文档、记忆、画像、归档）六格各拿六到八样，因为它们只读只写**文件**；
 * 「库的存在性」这件事天生跨四族：目录生命周期（建/删/列）、一次写事务、
 * 三把尺（kb.json 编码 / schema 模板 / 时间戳）、以及"写完该不该备份"的节流。
 * 把它们压成一样都会让本类自己长出缺的那件——那才是复核报告说的第二个所有者。
 *
 * 所以这份清单的牙不在条数，在**能力种类**：
 * 1. 没有一员给出 `File`，也没有一员接受调用方拼好的路径——路径仍只有 `KnowledgeTx.pathOf`
 *    （即 `KnowledgeDocumentStore.resolve`）那一道 canonical 守门给；
 * 2. 没有一员给出 `Mutex`：[CatalogWriteStorage.inWriteLock] 是把调用方请进仓库那一把锁，
 *    本类不自持锁（`StorageBoundaryOwnershipTest` 盯着"各自 new Mutex"这条）；
 * 3. 没有一员直接落字节：[CatalogTx.write] / [CatalogTx.updateMeta] 背后是仓库的
 *    `transactionUnlocked` → `KnowledgeTx` → `writeFileCheckedUnlocked` → `atomicWriteText`
 *    那**唯一**一条写链，只读 schema 拒绝与备份节流都在那条链上，本类绕不过去；
 * 4. 删库那道 canonical 守卫留在仓库（[CatalogWriteStorage.removeCatalogDir]），
 *    本类连"这个目录在不在树内"都不自己判——判据只许有一处。
 *
 * ## 为什么端口上还有三员是转手
 * [KnowledgeBaseCatalogPort] 是页面唯一可见的那颗口，它另外三员（清单、当前库、阶段、正文写）
 * 的主人各在别处：枚举在目录读侧、阶段在画像格、正文在文档格。这里转一次手，
 * 换的是"页面拿一颗端口就能管库"，而不是"多一颗谁都能调的口子"；
 * 本类没有为它们写第二份判据，`StorageBoundaryOwnershipTest` 与
 * `KnowledgeTxMutationEntryTest` 数的都还是仓库里那一处。
 */
internal interface CatalogWriteStorage {

    // ── 锁与事务：唯一那把锁、唯一那条写链 ──

    /** 仓库的 `fileMutex`：整段目录写操作在它里面跑完（Mutex 非重入，块内不许再拿锁） */
    suspend fun <T> inWriteLock(block: suspend () -> T): T

    /**
     * 一次库内写事务（无锁版，调用方必须已持有 [inWriteLock]）。
     * 只读判定、路径守门、原子落盘都在实现那一侧；块体返回即事务结束。
     */
    fun writeCatalogTransaction(kbName: String, block: CatalogTx.() -> Unit)

    // ── 库目录的生命周期：目录形状与"删干净"都由仓库判 ──

    /** knowledge/ 根下所有非隐藏目录名（切库要逐库改 kb.json，含还没元数据的那类目录） */
    fun catalogDirNames(): List<String>

    /** 最近被改过的那个库目录名；一个都没有时 null（删掉当前库之后选下一个） */
    fun newestCatalogDirName(): String?

    /** 这个名字在根下占位了吗。文件也算占位——建库要拒得比"是不是目录"更严 */
    fun catalogDirPresent(kbName: String): Boolean

    /** 库目录 + 三层子目录，幂等；本类不摸 File，目录形状是仓库的事 */
    fun makeCatalogSkeleton(kbName: String)

    /**
     * canonical 守卫 + 物理删除 + 旧 `.trash` 一次性腾空。
     * false = 目标不在 knowledge/ 树内、或压根不存在，一个目录都没删。
     */
    fun removeCatalogDir(kbName: String): Boolean

    /** 该库的全部备份：库删了备份还在等于隐私副本残留 */
    fun deleteCatalogBackups(kbName: String)

    // ── 首次启动那个根级标记 ──

    /** `.kb_initialized` 在不在：区分"首次启动"与"用户删掉了最后一个库" */
    fun initMarkerPresent(): Boolean

    /** 全部 seed 都落成了才许写这个标记（半套文件 + 标记 = 下次启动不修） */
    fun markInitialized()

    // ── 三把尺：编码、模板、时间，全用仓库那一份 ──

    /** 库的清单：判据（隐藏目录、name 与目录名等值、坏元数据丢弃、倒序）在目录读侧那一份 */
    fun entries(): List<KnowledgeBase>

    /** kb.json 的编码：与枚举侧同一把 Json 尺，不开第二份配置 */
    fun encodeMeta(kb: KnowledgeBase): String

    /** `assets/schema/<name>` 那份模板正文（知识库结构的唯一来源） */
    fun template(name: String): String

    /** kb.json 的 `updatedAt` 那一把尺（ISO-8601 带时区） */
    fun timestamp(): String

    /** 当前激活库名。加密落盘归 `SecurePrefs`，这一格只转一次手 */
    var activeKbName: String

    /** 进入已有库前补齐旧格式结构（迁移那一格的能力，锁由本类在外面套着） */
    suspend fun migrateCatalogEntry(kbName: String)

    /** 写完之后那班 5 秒节流备份；启动者仍是仓库，本类不接 CoroutineScope */
    fun scheduleBackup()

    // ── 端口上其余成员的主人：清单、当前库、阶段、正文写 ──

    /** 当前在用的库（三级回退判据住在仓库，见 `KnowledgeRepository.getActive`） */
    suspend fun activeEntry(): KnowledgeBase?

    /** 阶段标签（白名单归一化在画像格，见 `KnowledgeProfileStore.setStage`） */
    suspend fun writeStage(kbName: String, stage: String)

    /** 正文写（建库之后补画像三段用；路径守门与只读判定在文档格与写链那一侧） */
    suspend fun writeDocument(kbName: String, relativePath: String, content: String)
}

/**
 * 仓库交给目录写侧的**一次事务**里能做的三件事。
 *
 * 与 `ArchiveTx` / `ProfileTx` 同一用意：把 `KnowledgeTx` 收窄，
 * 尤其是不把 `pathOf`（能拿出一个 `File`）交出去——那正是"第二个路径所有者"的起点。
 */
internal interface CatalogTx {

    /** 覆盖写一格。false = 被只读保护或路径非法挡下，一个字节都没落 */
    fun write(relativePath: String, content: String): Boolean

    /** 读改写 kb.json；库缺失 / 只读 / JSON 坏掉返回 false 且不写 */
    fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean

    /**
     * 这一格在盘上吗。
     *
     * 判"缺不缺"必须问存在性而不是"读出来是不是空串"——后者会把用户故意留空的
     * `moment/plan.md` 当成缺失，盖回 schema 模板。路径非法时给 false（写也一样写不进）。
     */
    fun exists(relativePath: String): Boolean
}

/**
 * §5.3 catalog 那一格的后半：**「库的存在性」的写侧**。
 *
 * 指导书那条完成定义写的是"KnowledgeRepository 不再是所有知识能力的唯一入口"。
 * 枚举侧早就归 [KnowledgeCatalogStore] 了，写侧一直留着的理由写在交接账里：
 * 建库要向仓库要编码、模板写、列目录、事务改 meta、删备份、标记、节流备份……
 * 诚实接口会比别家宽一截。这一轮付掉这笔代价，换来的是**页面从此抱着这一格就能管库，
 * 不必再抱着整个仓库**——[KnowledgeBaseCatalogPort] 的实现者由仓库换成这里，
 * 而落盘、路径、锁三件仍然一样都不多（见 [CatalogWriteStorage] 那份 KDoc 的第 1–4 条）。
 *
 * ## 搬进来之前的四条规矩，搬进来之后一条没松
 * 1. **只读库一个字节都不写**：schema 比本 App 还新的库，写侧的每一格都被写链拒掉
 *    （`ReadOnlySchemaWriteGateTest` 把整棵目录树比对钉着这一条）；
 * 2. **拒绝时不许先销毁旧证据**：本类不删 marker、不动备份，除非仓库那条守门说写成了；
 * 3. **正式目录删成功才动备份**：反过来就是"备份没了、库还在"；
 * 4. **seed 的字节不许漂**：13 格的路径与内容逐格钉在 `KnowledgeSeedWriteBytesBaselineTest`。
 *
 * 本类不持锁、不摸 `File`、不判 canonical、不落盘、不配 Json、不接 CoroutineScope。
 */
internal class KnowledgeCatalogWriteStore(private val storage: CatalogWriteStorage) :
    KnowledgeBaseCatalogPort {

    /** 列出所有库：判定在目录读侧那一份，与仓库内部路径同一条尺 */
    override suspend fun listAll(): List<KnowledgeBase> = withContext(Dispatchers.IO) { storage.entries() }

    /** 当前在用的库：三级回退（同名且 active → 同名 → 第一个）判据仍只有一处 */
    override suspend fun getActive(): KnowledgeBase? = withContext(Dispatchers.IO) { storage.activeEntry() }

    /**
     * 切换当前库。
     *
     * 写边界：切库要重写**每个**库的 kb.json，所以这里一次拿锁、逐库一次事务。
     * schema 过新的那个库会被 `KnowledgeTx.updateMeta` 拒掉——判定住在写链那一侧，
     * 本类不写"这个库能不能改"这种第二份规则。
     */
    override suspend fun setActive(name: String): Unit = withContext(Dispatchers.IO) {
        storage.inWriteLock { activateWithin(name) }
    }

    /** [setActive] 的锁内核心；删掉当前库之后选下一个时也走这里，两边同一份写法 */
    private fun activateWithin(name: String) {
        storage.activeKbName = name
        storage.catalogDirNames().forEach { dirName ->
            storage.writeCatalogTransaction(dirName) {
                updateMeta { kb -> kb.copy(active = kb.name == name) }
            }
        }
    }

    /**
     * 新建一个知识库：名字归一化 → 三道拒绝 → 一次事务 seed 13 格 → 记激活库 → 排备份。
     *
     * 三道拒绝（空名、过长、已占位）都必须发生在建目录**之前**：
     * 长度那条与 [KB_NAME_MAX_LENGTH] 同源，就是因为以前 sanitizer 只过滤字符集不限长度，
     * 于是 101+ 字符的库"建得出来、每一读都被路径守门判非法"，盘上还留下半套 seed 文件。
     */
    override suspend fun create(name: String, displayName: String): KnowledgeBase =
        withContext(Dispatchers.IO) {
            storage.inWriteLock { createWithin(name, displayName) }
        }

    private fun createWithin(name: String, displayName: String): KnowledgeBase {
        val safeName = sanitizeName(name)
        require(safeName.isNotEmpty()) { "知识库名不能为空" }
        require(safeName.length <= KB_NAME_MAX_LENGTH) {
            "知识库名过长（最多 $KB_NAME_MAX_LENGTH 个字符）"
        }
        require(!storage.catalogDirPresent(safeName)) { "知识库 '$safeName' 已存在" }

        storage.makeCatalogSkeleton(safeName)

        val kb = KnowledgeBase(
            name = safeName,
            displayName = displayName.ifBlank { safeName },
            updatedAt = storage.timestamp(),
            stage = "待确定",
            turnCount = 0,
            active = storage.entries().isEmpty()
        )
        // 建库的 13 格 seed 全走唯一写链：CatalogTx.write → 仓库的 KnowledgeTx.write →
        // writeFileCheckedUnlocked（入口侧只读判定 + safeKbFile 路径守门 + 唯一原子写）。
        // 每格落盘什么字节由 `KnowledgeSeedWriteBytesBaselineTest` 逐格钉住（含 schema 正文与 assets 原字节比）。
        storage.writeCatalogTransaction(safeName) {
            write(KnowledgeCatalogStore.META_FILE, storage.encodeMeta(kb))

            // 全部文件从 assets/schema/ 加载（schema 是知识库结构的唯一来源）
            // 懂得层（慢变量画像）
            write("understand/me.md", storage.template("me"))
            write("understand/her.md", storage.template("her"))
            write("understand/warmth.md", storage.template("warmth"))
            // 此刻层（快变量上下文）
            write("moment/topic.md", storage.template("topic"))
            write("moment/recent.md", storage.template("recent"))
            write("moment/scene.md", storage.template("scene"))
            write("moment/plan.md", storage.template("plan"))
            // 记忆层（长期归档）
            write("memory/lessons.md", storage.template("lessons"))
            write("memory/raw_chat.md", storage.template("raw_chat"))
            write("memory/raw_topic.md", storage.template("raw_topic"))
            write("memory/raw_scene.md", storage.template("raw_scene"))
            write("memory/counseling_log.md", storage.template("counseling_log"))
        }

        if (kb.active) storage.activeKbName = safeName
        storage.scheduleBackup()
        return kb
    }

    /**
     * 物理删除一个知识库。
     *
     * 三条顺序是承重的，都有用例钉着：
     * ① 越界名先被仓库的 canonical 守卫拒掉（`KnowledgeRepositorySecurityTest`）；
     * ② 正式目录删**成功**才删备份——反过来就是"备份没了、库还在"
     *    （`KnowledgeRepositoryDeleteBackupTest`）；
     * ③ 删的是当前库时才改激活库，且改法与 [setActive] 同一份写法。
     */
    override suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        storage.inWriteLock { removeWithin(name) }
    }

    /** [delete] 的锁内核心：三条顺序全在这里，一次拿锁跑完 */
    private fun removeWithin(name: String): Boolean {
        val ok = storage.removeCatalogDir(name)
        if (ok) storage.deleteCatalogBackups(name)
        if (ok && storage.activeKbName == name) {
            val next = storage.newestCatalogDirName()
            if (next != null) activateWithin(next) else storage.activeKbName = ""
        }
        return ok
    }

    /**
     * 修改知识库显示名。
     *
     * 返回类型显式写 Unit 与端口对齐（同 `incrementTurnCountBy` 那一格的先例）：
     * 不写的话它会跟着事务块里最后一个表达式推断成 Boolean，端口那头就对不上。
     */
    override suspend fun updateDisplayName(kbName: String, newDisplay: String): Unit =
        withContext(Dispatchers.IO) {
            storage.inWriteLock { renameWithin(kbName, newDisplay) }
        }

    /** [updateDisplayName] 的锁内核心：空白显示名 no-op，其余只改这一格字段 */
    private fun renameWithin(kbName: String, newDisplay: String) {
        if (newDisplay.isBlank()) return
        storage.writeCatalogTransaction(kbName) {
            updateMeta { kb ->
                kb.copy(displayName = newDisplay.trim(), updatedAt = storage.timestamp())
            }
        }
    }

    /** 阶段标签：白名单归一化与"拒绝时说不说"都在画像格，这里只转一次手 */
    override suspend fun updateStage(kbName: String, stage: String) = storage.writeStage(kbName, stage)

    /**
     * 建库之后写画像三段：内容级写，但由"管理库"这件事带出来。
     * 锁与只读判定在仓库那一侧（`KnowledgeRepository.writeFile`），本类不再套一层。
     */
    override suspend fun writeFile(kbName: String, relativePath: String, content: String) =
        storage.writeDocument(kbName, relativePath, content)

    /**
     * 确保应用至少有一个合法知识库。应用初始化唯一入口。
     *
     * 规则：
     * 1. 有库沿用原激活项；不创建新库
     * 2. 首次无库创建恰好一个"默认知识库"，阶段"待确定"，画像空
     * 3. 用户主动删除最后一个库后不重复创建（通过 `.kb_initialized` 标记区分）
     * 4. 导入优先：有导入的库存在时不创建默认库
     * 5. 中断恢复优先补齐缺失文件，不 deleteRecursively 后重建
     * 6. 全部写入成功才标完成
     * 7. 无网络、无模型配置也成功
     *
     * 这颗口不在 [KnowledgeBaseCatalogPort] 上，而在 `KnowledgeRuntimePort` 上——
     * 调用它是首页启动流程，不是管理页；仓库那侧只留一行转手，seed 与补齐的判断都在这里。
     */
    suspend fun ensureInitialKnowledgeBase(): Unit = withContext(Dispatchers.IO) {
        storage.inWriteLock {
            val existingKbs = storage.entries()
            if (existingKbs.isNotEmpty()) {
                // 已有库——先完成旧格式迁移，再补缺失文件。
                // 旧顺序：先补齐创建空文件，再 migrateIfNeeded——
                // 空文件遮住迁移（迁移以 understand 已存在为提前返回条件）。
                existingKbs.forEach { kb ->
                    storage.migrateCatalogEntry(kb.name)
                    completeMissingFiles(kb.name)
                }
                storage.markInitialized()
                return@inWriteLock
            }

            // 无库——区分"首次启动"和"用户删除最后一个库后"
            if (storage.initMarkerPresent()) {
                // 用户已初始化过，之后删除了所有库——不重复创建
                return@inWriteLock
            }

            // 首次启动——创建默认知识库
            storage.makeCatalogSkeleton(DEFAULT_KB_NAME)
            val kb = KnowledgeBase(
                name = DEFAULT_KB_NAME,
                displayName = DEFAULT_DISPLAY_NAME,
                updatedAt = storage.timestamp(),
                stage = "待确定",
                turnCount = 0,
                topicCount = 0,
                active = true
            )
            // seed 的 13 格全走唯一写链（与 [createWithin] 同一形状）；落盘字节由
            // `KnowledgeSeedWriteBytesBaselineTest` 逐格钉住（路径 + 内容 + 文件集合）。
            // 锁已由外层 inWriteLock 持有；那次事务不要求"库已存在"，对刚建出来的目录照样可用。
            storage.writeCatalogTransaction(DEFAULT_KB_NAME) {
                write(KnowledgeCatalogStore.META_FILE, storage.encodeMeta(kb))

                // 画像默认真实空内容（非 schema 模板占位文字）
                write("understand/me.md", "")
                write("understand/her.md", "")
                write("understand/warmth.md", "")
                // 此刻层：topic 有初始行，其余空
                write(
                    "moment/topic.md",
                    KbTextOps.topicLine(com.lovebrain.app.util.TimeFmt.now(), KbTextOps.TOPIC_INITIAL_LABEL)
                )
                write("moment/recent.md", "")
                write("moment/scene.md", "")
                write("moment/plan.md", storage.template("plan"))
                // 记忆层：全部空
                write("memory/lessons.md", "")
                write("memory/raw_chat.md", "")
                write("memory/raw_topic.md", "")
                write("memory/raw_scene.md", "")
                write("memory/counseling_log.md", "")
            }

            // 全部写入成功才标完成
            storage.activeKbName = DEFAULT_KB_NAME
            storage.markInitialized()
            storage.scheduleBackup()
        }
    }

    /**
     * 检查知识库文件是否完整，补齐缺失文件（中断恢复）。
     * 不 deleteRecursively，只补缺失；调用方（本类）已持有仓库那把锁。
     *
     * 判"存不存在"必须用 [CatalogTx.exists] 而不是"读出来是空串"——后者会把
     * "文件在、内容为空"误判成缺失，于是给一个用户故意留空的 plan.md 盖回 schema 模板。
     * 路径仍由仓库那一侧的守门给，写仍是同一条链上的一次事务。
     */
    private fun completeMissingFiles(kbName: String) {
        if (!storage.catalogDirPresent(kbName)) return
        storage.makeCatalogSkeleton(kbName)

        // 补齐缺失的必需文件（不覆盖已有内容）
        val requiredFiles = listOf(
            "understand/me.md" to "",
            "understand/her.md" to "",
            "understand/warmth.md" to "",
            "moment/recent.md" to "",
            "moment/scene.md" to "",
            "moment/plan.md" to storage.template("plan"),
            "memory/lessons.md" to "",
            "memory/raw_chat.md" to "",
            "memory/raw_topic.md" to "",
            "memory/raw_scene.md" to "",
            "memory/counseling_log.md" to ""
        )

        storage.writeCatalogTransaction(kbName) {
            requiredFiles.forEach { (path, defaultContent) ->
                if (!exists(path)) write(path, defaultContent)
            }

            // topic.md 特殊处理：不存在时写入初始行
            if (!exists("moment/topic.md")) {
                write(
                    "moment/topic.md",
                    KbTextOps.topicLine(com.lovebrain.app.util.TimeFmt.now(), KbTextOps.TOPIC_INITIAL_LABEL)
                )
            }
        }
    }

    /**
     * 库名归一化：小写、去空白、只留 a–z / 0–9 / 汉字 / 下划线 / 连字符。
     *
     * 长度上限**不在这里**判，见 [createWithin] 那一格——两处必须读同一个
     * [KB_NAME_MAX_LENGTH]，否则"建得出来、读不回来"那种库又会重新出现。
     */
    private fun sanitizeName(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9\\u4e00-\\u9fa5_-]"), "")

    companion object {
        private const val DEFAULT_KB_NAME = "default"
        private const val DEFAULT_DISPLAY_NAME = "默认知识库"
    }
}
