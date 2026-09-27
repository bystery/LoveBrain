package com.lovebrain.app.domain.port

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import com.lovebrain.app.model.ProfileTransactionResult

/**
 * 页面侧（viewmodel）需要的三族知识库能力。
 *
 * **为什么是三族而不是一个"仓库的接口版"**：这三族的边界不是照着具体仓库
 * （`KnowledgeRepository`）的公开方法抄出来的，而是按三个调用方**各自真正调用过的方法**
 * 开出来的（逐个 ViewModel 实扫调用点，不是凭印象）：
 * - 编辑页只要"把一篇文档读出来、带版本写回去"，它拿到 [KnowledgeDocumentPort] 之后
 *   就**删不掉库**、也**切不了当前库**——这正是 [KnowledgePort] 那份 KDoc 立的规矩在页面侧的延续。
 * - 管理页要的是"库的清单与元信息"，建库事务顺带要把画像三段落盘，所以它那一族里
 *   只有配对的 [KnowledgeBaseCatalogPort.writeFile]，没有版本化写、也没有正文级的事务口。
 * - 对话运行时读的东西最多（意图、纠正、画像、咨询日志、实际发送记录），
 *   但它**一件都写不到"库的存在性"上**：[KnowledgeRuntimePort] 里没有 delete、没有 setActive、
 *   也没有绕过版本校验的 writeFile。于是"首页删不掉一本库"第一次变成可静态检查的事实，而不是靠自觉。
 *
 * **与 [KnowledgeReadPort] / [KnowledgeWritePort] 的分工**：那一对是给 domain 用的，
 * domain 只碰正文、WAL 与主题轮转；这里的三颗是给页面用的，页面还要管库的生命周期与内容台账。
 * 三颗端口与那一对都由同一个具体仓库实现，
 * 容器里每个端口视图都解析到**同一个仓库实例**（见 di/AppModule.kt 那段警告）——
 * 所以这里没有第二条写链，落盘边界仍然只有仓库里那一处。
 *
 * 成员重叠（readFile / writeFile / listAll / getActive / updateStage / migrateIfNeeded 出现在两颗端口里）
 * 是有意的：接口的形状跟着**调用方**走，两个调用方各自需要时才各自声明一次，
 * 而不是为了去重把两边并成一颗谁都能调谁的口子。实现方（仓库）一处实现覆盖两边。
 */

/**
 * 编辑一篇文档所需的那一族：先保证库结构齐全，再带版本地读、带版本地写。
 *
 * [migrateIfNeeded] 在这里是"打开一篇文档的前置条件"（进入编辑页时补齐缺失结构，幂等），
 * 它本质上是库级生命周期操作——留在这里是因为调用方今天真的在调它，端口不替调用方撒谎。
 *
 * [hashContent] 不是 suspend：它只是算 SHA-256，不碰磁盘，仓库那边也是这么实现的。
 */
interface KnowledgeDocumentPort {
    suspend fun migrateIfNeeded(kbName: String)
    suspend fun readFile(kbName: String, relativePath: String): String
    suspend fun writeFile(kbName: String, relativePath: String, content: String)
    suspend fun readFileWithVersion(kbName: String, relativePath: String): Pair<String, String>
    suspend fun writeFileWithVersion(
        kbName: String, relativePath: String, content: String, expectedVersion: String
    ): String?

    fun hashContent(text: String): String
}

/**
 * 库的清单与元信息：有哪些库、当前在用哪个、建一个、删一个、改名、改阶段。
 *
 * [writeFile] 属于这颗端口而不是只属于 [KnowledgeDocumentPort]，理由是 AI 建库那一段事务
 * 要在建库之后立刻把 understand 下那三份 md 落进去——那是"建库"的一部分，不是"编辑一篇文档"。
 */
interface KnowledgeBaseCatalogPort {
    suspend fun listAll(): List<KnowledgeBase>
    suspend fun getActive(): KnowledgeBase?
    suspend fun setActive(name: String)
    suspend fun create(name: String, displayName: String): KnowledgeBase
    suspend fun delete(name: String): Boolean
    suspend fun updateDisplayName(kbName: String, newDisplay: String)
    suspend fun updateStage(kbName: String, stage: String)

    /** 建库之后写画像三段：内容级写，但由"管理库"这件事带出来 */
    suspend fun writeFile(kbName: String, relativePath: String, content: String)
}

/**
 * 对话运行时对当前库需要的那一族：读上下文、写内容台账。
 *
 * 这一颗刻意**不包含** [KnowledgeBaseCatalogPort.setActive] / [KnowledgeBaseCatalogPort.create] /
 * [KnowledgeBaseCatalogPort.delete]，也不包含 [KnowledgeDocumentPort.writeFile] 那种
 * "不带版本校验直接盖一个文件"的写——首页的 ViewModel 因此既删不掉一本库，
 * 也盖不掉用户正在编辑的文件。
 * [updateStage] 留在这里是因为确认画像时它确实是运行时行为（阶段变了要立刻落盘），
 * 而不是"管理库清单"。
 *
 * [saveIntent] 与 [saveCorrection] 的形参默认值写在这颗端口上：Kotlin 不许覆写方再写一遍默认值，
 * 所以仓库那两处只剩形参。省略参数的调用点（含既有测试）拿到的默认与改动前逐字相同。
 */
interface KnowledgeRuntimePort {
    // ── 在用库与其结构 ──
    suspend fun listAll(): List<KnowledgeBase>
    suspend fun getActive(): KnowledgeBase?
    suspend fun migrateIfNeeded(kbName: String)
    suspend fun ensureInitialKnowledgeBase()
    suspend fun updateStage(kbName: String, stage: String)

    // ── 文档级读取（此刻计划、风格文件都走这一条）──
    suspend fun readFile(kbName: String, relativePath: String): String

    // ── 画像与向量 ──
    suspend fun readVector(kbName: String): Map<String, Int>
    suspend fun readProfile(kbName: String): String
    suspend fun contentRevision(kbName: String): String
    suspend fun updateWarmthStageLabel(kbName: String, newStage: String)

    /**
     * 画像三段 + 阶段 + 向量的一次事务写，带 revision 前置条件。
     *
     * 返回值是 typed result 而不是 Boolean：回滚失败与前置条件不满足要给 UI 两种不同反馈，
     * 收成 Boolean 就会"磁盘没变、嘴里说成功"。
     */
    suspend fun applyProfileUpdateAtomically(
        kbName: String,
        me: String?,
        her: String?,
        warmth: String?,
        stageChanged: Boolean,
        newStage: String?,
        expectedRevision: Int
    ): ProfileTransactionResult

    // ── 持续意图 ──
    suspend fun readIntent(kbName: String): IntentConfig
    suspend fun saveIntent(
        kbName: String,
        text: String,
        enabled: Boolean,
        expiry: IntentExpiry = IntentExpiry.UNTIL_DONE,
        expiryDate: String = "",
        status: IntentStatus = IntentStatus.ACTIVE
    ): IntentConfig

    // ── 纠正台账（revision 单调，撤销也递增）──
    suspend fun readCorrections(kbName: String): Map<String, MemoryCorrection>
    suspend fun readCorrectionsAndRevision(kbName: String): Pair<Map<String, MemoryCorrection>, Int>
    suspend fun getCorrectionsRevision(kbName: String): Int
    suspend fun saveCorrection(
        kbName: String,
        memoryId: String,
        action: CorrectionAction,
        replacementText: String = "",
        targetKbId: String = "",
        muteDuration: MuteDuration = MuteDuration.UNTIL_RESTORE
    ): Boolean

    suspend fun undoCorrection(kbName: String, memoryId: String): Boolean

    // ── 咨询日志与实际发送记录 ──
    suspend fun appendCounselingEntries(kbName: String, recordEntry: String, analysisEntry: String)
    suspend fun appendActualSentRecord(kbName: String, entry: String): Boolean
    suspend fun replaceActualSentRecord(kbName: String, oldEntry: String, newEntry: String): Boolean
}
