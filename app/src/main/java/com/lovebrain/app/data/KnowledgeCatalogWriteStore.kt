package com.lovebrain.app.data

import com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort
import com.lovebrain.app.model.KB_NAME_MAX_LENGTH
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.StageCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

/**
 * 仓库交给目录写侧那一格的能力清单。
 *
 * ## 为什么这一格比 第5节第3条 别家宽，而宽了仍然没有第二个所有者
 * 别家（文档、记忆、画像、归档）六格各拿六到八样，因为它们只读只写**文件**；
 * 「库的存在性」这件事天生跨四族：目录生命周期（建/删/列）、一次写事务、
 * 三把尺（kb.json 编码 / schema 模板 / 时间戳）、以及"写完该不该备份"的节流。
 * 把它们压成一样都会让本类自己长出缺的那件——那才是说的第二个所有者。
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
 * 本轮（§4 M01/M02）这份清单多了一员**读**（[CatalogWriteStorage.readCatalogSlot]），
 * 上面四条一条都没被它松动：它交不出 `File`、也落不下字节，只是把"这座库到底有没有真实内容"
 * 这件事交给文档格那**唯一**一道守门来回答——判据只许有一处，读法的"一处"算在内。
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

    /**
     * 守门读某一格的**正文**（缺文件、越界、非法路径都给空串，不抛）。
     *
     * 为什么目录写侧需要一把读尺：§4 M01 那句「判断可以复用必须基于真实元数据与内容状态」
     * 问的就是盘上此刻有什么，而 kb.json 那四格回答不了正文——
     * `HomeStatusViewModel` 今天明写 turnCount/topicCount/stage/正文都不参与存在性，
     * 全仓唯一那颗存在性读数（`FileKnowledgePresence`）只数根目录条目、连 kb.json 都不验。
     * 所以「这座库有没有真实用户内容」这颗判据只能自己去看真内容，
     * 而看内容只许借文档格那**唯一**一道守门（[KnowledgeDocumentStore.read]），
     * 不在本类复制第二份 `File(dir, path)` —— 与 [CatalogTx.exists] 同一个理由。
     */
    fun readCatalogSlot(kbName: String, relativePath: String): String

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
 * 第5节第3条 catalog 那一格的后半：**「库的存在性」的写侧**。
 *
 * 那条完成定义写的是"KnowledgeRepository 不再是所有知识能力的唯一入口"。
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
     * 新建一个知识库：名字归一化 → 三道拒绝 → **先问能不能复用那顶还没用过的初始库** →
     * 一次事务 seed 13 格 → 记激活库 → 排备份。
     *
     * 三道拒绝（空名、过长、已占位）都必须发生在建目录**之前**：
     * 长度那条与 [KB_NAME_MAX_LENGTH] 同源，就是因为以前 sanitizer 只过滤字符集不限长度，
     * 于是 101+ 字符的库"建得出来、每一读都被路径守门判非法"，盘上还留下半套 seed 文件。
     *
     * 同一条理由管到 seed 写本身：seed 半途失败时这次刚造的目录一并收回（见 [createWithin]），
     * 「报失败」与「列表里多出一座能用的空库」不许同时发生——§12.3 那句
     * 「创建完成才出现在可用列表，失败显示失败」要的就是这一条。
     *
     * ## 为什么复用判据也在这颗口里，而不是另开一条"首次建库"流程
     * §4（活台账 M01/M02）把「首次初始化」与「首次正式建库」定为**同一个用户流程的两步**，
     * 而"第一步留下的那顶空壳还没被人用过"这件事只有在持锁的一刻才判得准。
     * 判据见 [reusableInitialKb]：它只读真实元数据与真实正文，**目录名不参与**。
     * 复用时内部身份（目录名 = kb.json 的 name）一个字都不动，只按流程结果改显示名，
     * 画像三段与阶段仍由调用方走现有 [writeFile] / [updateStage] 两员——
     * 全程仍是这一把锁、这一条写链，没有第二条文件系统流程。
     * 不可复用（有用户内容、已被明确使用、或盘上不止一座库）时保持今天的行为：照常加库，
     * **绝不因为"修复重复"去删任何既有库**。
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

        // 锁内判定与改写之间没有挂起点，也不会有别人插进来：这一步与下一步同属一次持锁。
        reusableInitialKb()?.let { initial ->
            // 返回 null = 那一格没改成（只读库 / kb.json 坏掉），落回正常新建，不谎报也不删任何东西
            adoptInitialKb(initial, displayName)?.let { return it }
        }

        storage.makeCatalogSkeleton(safeName)

        val kb = KnowledgeBase(
            name = safeName,
            displayName = displayName.ifBlank { safeName },
            updatedAt = storage.timestamp(),
            stage = INITIAL_STAGE,
            turnCount = 0,
            active = storage.entries().isEmpty()
        )
        // 建库的 13 格 seed 全走唯一写链：CatalogTx.write → 仓库的 KnowledgeTx.write →
        // writeFileCheckedUnlocked（入口侧只读判定 + safeKbFile 路径守门 + 唯一原子写）。
        // 每格落盘什么字节由 `KnowledgeSeedWriteBytesBaselineTest` 逐格钉住（含 schema 正文与 assets 原字节比）。
        try {
            storage.writeCatalogTransaction(safeName) {
                // 写入失败传播：write 返回 false = 被只读保护或路径非法挡下。
                // 必须抛异常让上层 ViewModel 捕获报失败，不得假成功（指导书§6）。
                val metaOk = write(KnowledgeCatalogStore.META_FILE, storage.encodeMeta(kb))

                // 全部文件从 assets/schema/ 加载（schema 是知识库结构的唯一来源）
                // 懂得层（慢变量画像）
                val seedOk = metaOk
                    && write("understand/me.md", storage.template("me"))
                    && write("understand/her.md", storage.template("her"))
                    && write("understand/warmth.md", storage.template("warmth"))
                    // 此刻层（快变量上下文）
                    && write("moment/topic.md", storage.template("topic"))
                    && write("moment/recent.md", storage.template("recent"))
                    && write("moment/scene.md", storage.template("scene"))
                    && write("moment/plan.md", storage.template("plan"))
                    // 记忆层（长期归档）
                    && write("memory/lessons.md", storage.template("lessons"))
                    && write("memory/raw_chat.md", storage.template("raw_chat"))
                    && write("memory/raw_topic.md", storage.template("raw_topic"))
                    && write("memory/raw_scene.md", storage.template("raw_scene"))
                    && write("memory/counseling_log.md", storage.template("counseling_log"))

                if (!seedOk) throw IOException("knowledge base seed write failed for $safeName")
            }
        } catch (e: Exception) {
            // seed 半途失败：这一次调用进来时那格名字还没占位（上面第三道 require 判的就是它），
            // 所以这一颗目录是自己刚造的，收回去不销毁任何旧证据。
            // 留着它的后果是 §12.3 那两句同时被破：报了失败，可用列表里却多出一座空库；
            // 而它若是盘上第一座库，偏好里的当前库是空的，getActive 的兜底会把这座半成品当成
            // 在用库——面板之后的每一格都往它里面写。
            // 回收仍走仓库那道 canonical 守卫，本类不自己判目录在不在树内；
            // 备份与当前库都在成功之后才动，失败这一趟一样都不碰。
            storage.removeCatalogDir(safeName)
            throw e
        }

        if (kb.active) storage.activeKbName = safeName
        storage.scheduleBackup()
        return kb
    }

    /**
     * 首次流程可以当目标库的那座初始库；**没有**（含盘上不止一座库）时给 null。
     *
     * 「不止一座就新建」是护栏，不是保守：M01 说的复用只发生在「首次初始化 → 首次正式建库」
     * 这一条用户流程里，而流程能走到第二座库时盘上必然已经有一座带内容的了
     * （§4 场景④「已有一个真实库再主动新建 ⇒ 正常变 2 个」——那条必须照样变 2 个）。
     */
    private fun reusableInitialKb(): KnowledgeBase? {
        val all = storage.entries()
        if (all.size != 1) return null
        return all.first().takeIf { isPristineInitialKb(it) }
    }

    /**
     * 「这座库还是不是首次初始化那一步留下的、一个字都没用过的空壳」——全仓唯一回答者。
     *
     * 判据的输入**全是盘上的真实读数**（§4「判断可以复用必须基于真实元数据与内容状态」），
     * 一条都不许是推断：
     * 1. `kb.json` 的四格：[KnowledgeBase.stage] 仍是初始那一个、
     *    [KnowledgeBase.turnCount] 与 [KnowledgeBase.topicCount] 仍是 0（对话与归档各只在那里自增）、
     *    [KnowledgeBase.displayName] 仍是首次 seed 那一个——用户改过名字就是明确使用过；
     * 2. 画像三段与此刻/记忆各格的**正文**（经 [CatalogWriteStorage.readCatalogSlot] 读真字节）：
     *    首次 seed 写的是真空内容，任何非空都只能来自用户或模型；
     * 3. 话题行仍是"等待第一次对话"那一句（[KbTextOps.topicLabel] 是话题行的唯一读法）；
     * 4. 两份用户台账文件（持续意图、记忆纠正）仍是空的。
     *
     * ⚠ **目录名一个字都不参与判断**——既不是充分条件也不是必要条件。
     *   它只承担"复用后要保留的稳定内部身份"这一件事（[KnowledgeCatalogStore] 强制
     *   `kb.name == 目录名`，改目录名等于凭空换一座库）。旧版本把同一份空壳落在别的目录名下
     *   照样算可复用；用户给一座真有内容的库起名 `default` 照样不可复用。
     *
     * ⚠ `updatedAt` 与 `active` 都不参与：kb.json 没有 createdAt，一个时间戳既证不了"刚 seed"
     *   也证不了"被用过"；而 `active` 首次 seed 就写 true，它回答的是"当前在用哪个"，不是"有没有被用过"。
     */
    private fun isPristineInitialKb(kb: KnowledgeBase): Boolean {
        if (kb.stage != INITIAL_STAGE) return false
        if (kb.turnCount != 0 || kb.topicCount != 0) return false
        if (kb.displayName != DEFAULT_DISPLAY_NAME) return false
        // 正文：seed 时是真空内容的每一格，非空即"有真实用户内容"
        for (path in PRISTINE_BLANK_SLOTS) {
            if (storage.readCatalogSlot(kb.name, path).isNotBlank()) return false
        }
        val topic = storage.readCatalogSlot(kb.name, TOPIC_FILE)
        if (topic.isNotBlank() && KbTextOps.topicLabel(topic) != KbTextOps.TOPIC_INITIAL_LABEL) return false
        return true
    }

    /**
     * 复用那顶初始库：一次持锁里的**一次事务**，只改 `displayName`（与 `updatedAt`）那一格。
     *
     * 内部身份不动（见 [isPristineInitialKb] 那条 ⚠）；画像三段与阶段仍由调用方经现有
     * [writeFile] / [updateStage] 落进去——§4「复用时继续使用现有 KnowledgeCatalogWriteStore、
     * 知识库存储事务与互斥机制，不要另写一条文件系统建库流程」要的就是这个形状。
     *
     * 显示名为空白时不覆盖（沿用 [renameWithin] 那条 no-op 规矩）；复用后的库不再是空壳，
     * 下一次新建因此一律走正常加库——复用最多发生一次，不会把第二座也吃掉。
     *
     * 返回 null = 那一格没改成（只读库、kb.json 坏掉：`updateMeta` 静默 false 的那两支）。
     * 调用方据此回到正常新建那条路：「没改成」与「已经改好了」从此分得开。
     * 备份不在这儿排：这一次事务落的每一个字节都在唯一写链上，节流备份在那条链的下游自己排。
     */
    private fun adoptInitialKb(initial: KnowledgeBase, displayName: String): KnowledgeBase? {
        val newDisplay = displayName.trim().ifBlank { initial.displayName }
        val stamp = storage.timestamp()
        var landed = false
        storage.writeCatalogTransaction(initial.name) {
            landed = updateMeta { kb -> kb.copy(displayName = newDisplay, updatedAt = stamp) }
        }
        return if (landed) initial.copy(displayName = newDisplay, updatedAt = stamp) else null
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
            val next = nextActiveAfter(name)
            if (next != null) activateWithin(next) else storage.activeKbName = ""
        }
        return ok
    }

    /**
     * 删掉的正是当前库时，下一个当前库**只能从枚举认下来的库里选**。
     *
     * 「根下 mtime 最新的那个目录名」那一旧判据已删（接口成员与实现一并移除，全仓无调用方）。
     * 它问的是目录，而目录不是库：一次中断的建库、一份坏掉的 kb.json、一个 name
     * 与目录名不相等的元数据，都会留下一颗进不了清单的目录（判据见
     * [KnowledgeCatalogStore]，它才是"这个根下有哪几座库"的唯一回答者）。把当前库交给那一颗，
     * 得到的就是 §12.3 明令不许出现的「活动引用指向一个不存在的库」——而且 [activateWithin]
     * 会顺手把清单里每一座库的 kb.json 都改成 active=false，三级回退的第一判据
     * （同名且 active）从此再也落不下去，界面只能靠兜底读数猜在用哪一个。
     *
     * [storage.entries] 已经按 updatedAt 倒序，所以这里选的仍是"最近那一个"，
     * 只是换成了**真存在的库**里的最近；一个有效库都不剩时清除选择（`""`），不新造一个。
     */
    private fun nextActiveAfter(deleted: String): String? =
        storage.entries().firstOrNull { it.name != deleted }?.name

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

    /** [updateDisplayName] 的锁内核心：空白显示名 no-op，其余只改这一格字段。
     *  updateMeta 返回 false = 库不存在 / 只读 / JSON 坏掉——对只读库静默降级（不抛、不写），
     *  对正常库的写失败也不抛（上层用返回值/磁盘 diff 判结果，不靠异常）。 */
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
     * 这一格只负责**第一步**（把一座可用的空壳摆上盘）。第二步（首次正式建库）由
     * [create] 承接，它先用 [reusableInitialKb] 问一句"那顶空壳还没人用过吗"，
     * 有就复用、没有才加库——两条路径因此在同一个用户流程里只留下一座有效库（§4 M01/M02）。
     * 这里 seed 的形状（画像真·空、话题初始标签、显示名 [DEFAULT_DISPLAY_NAME]）
     * 就是那颗判据读的三样事实，改任何一样都要回去看 [isPristineInitialKb]。
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
                stage = INITIAL_STAGE,
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

        /**
         * 首次 seed 写进 `kb.json` 的那个阶段。
         *
         * 三条 seed 路径（[createWithin]、[ensureInitialKnowledgeBase]、[isPristineInitialKb]）
         * 读同一个常数，不再各抄一份字面量——判据说"阶段还是不是初始那一个"，
         * 而写入侧偷偷改字的话，判据会在每一座新库上恒假（复用悄悄失效）。
         *
         * 字面量的主人是 `model/StageCatalog.UNKNOWN`（阶段词表唯一那一处），这里只是给它起一个
         * 读本文件时更好懂的名字；`viewmodel/KnowledgeBaseViewModel` 的问卷回落也读同一颗，
         * 两侧不再各写一遍"待确定"。
         */
        internal const val INITIAL_STAGE = StageCatalog.UNKNOWN

        /** 话题行那一格（判"有没有真的聊过"要读它，也是 seed 里唯一带时钟的正文格） */
        private const val TOPIC_FILE = "moment/topic.md"

        /**
         * 首次 seed 写的是**真空内容**的那些格：任何非空都只能来自用户或模型，因此都是"用过"的证据。
         *
         * 刻意不列 `moment/plan.md`（seed 就是 schema 模板正文，非空）与 [TOPIC_FILE]（单独判标签）。
         * `understand/style.md` 两条 seed 路径都不写，用户写了就是内容。
         * `memory/corrections.json`（纠正台账）与 `moment/intent.json`（持续意图）也在这一列里：
         * 它们一旦存在就是用户动过台账，跟正文同一个读法、同一道守门。
         */
        private val PRISTINE_BLANK_SLOTS = listOf(
            "understand/me.md", "understand/her.md", "understand/warmth.md", "understand/style.md",
            "moment/recent.md", "moment/scene.md", "moment/intent.json",
            "memory/lessons.md", "memory/raw_chat.md", "memory/raw_topic.md",
            "memory/raw_scene.md", "memory/counseling_log.md", "memory/corrections.json"
        )
    }
}
