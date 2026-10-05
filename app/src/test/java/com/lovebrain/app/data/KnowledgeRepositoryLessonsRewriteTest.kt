package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.domain.LessonDoc
import com.lovebrain.app.domain.port.LessonsRewriteResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.KnowledgeSchemaVersion
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 「读—整理—条件替换」那一次事务的**落盘侧**证据（ 第8节第2条 可重入整理、第8节第3条 计数与发号在写事务里）。
 *
 * 三条安全边界各自有证人，而且**每一条都能被对应的坏实现打破**：
 *
 * ① 计数、发号、revision 检查、替换必须在同一把 `fileMutex` 锁区内
 *    - [theNumberIsAllocatedWhileTheRepositoryLockIsHeld]：compose 里当场读 `fileMutex.isLocked`。
 *      坏实现（`readFile` 在锁外、只有写进锁）在这一刻拿到的是 false ⇒ 当场红；
 *    - [concurrentTidyAndAppendDoNotBurnTheSameNumberTwice]：两批并发进来，两条都在、编号不撞。
 *      坏实现会让两批都数到同一个号、后写的把先写的整篇盖掉 ⇒ 少一批，红。
 *
 * ② 写前先有快照，写失败不得丢正文
 *    - [theRealTidyLandsWithAByteIdenticalSnapshotOfTheOriginal]：快照里就是整理前那一篇逐字；
 *    - [aSnapshotThatDoesNotLandStopsTheReplacementEntirely]：快照没落成 ⇒ 目标一个字节都不写（SnapshotFailed）；
 *    - [aRefusedReplacementHandsTheComposedTextBackInsteadOfReportingSuccess]：替换没落成 ⇒ 交回全文、不报成功；
 *    - [aWriterThatClaimsSuccessButStoresNothingIsCaughtByTheReadBack]：写链撒谎（返回 true 但字节没落）
 *      被落盘后的回读抓到 ⇒ 仍是 WriteFailed。
 *
 * ③ 只在 `changed == true` 时才写盘（可重入、不重复写）
 *    - [identicalCompositionWritesNotEvenASnapshot]：逐字相同 ⇒ 一次 write 都不发生（连快照都不落）；
 *    - [tidyingAnAlreadyCleanFileIsANoOpOnTheSecondRun]：真文件系统上跑两次，第二次 NothingToWrite，
 *      且快照仍是**最早那份原件**——只要实现肯多写一次，那份快照就会被"已经整理过的内容"盖掉。
 *
 * 另有两格盯边界外的两条纪律：取消原样重抛（[cancellationWhileTheLockIsHeldPropagates]）、
 * 只读库与过期 revision 整段挡在写之前（[aStaleRevisionRefusesBeforeTheBodyIsEvenRead]）。
 *
 * 夹具用真实判据 [LessonDoc.tidy] 与用户实测到的那份 `1、1、3、1、5、1` 形状；不读任何真实用户数据。
 */
class KnowledgeRepositoryLessonsRewriteTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kb_lessons_rewrite").toFile()
    }

    @After
    fun tearDown() {
        if (::appScope.isInitialized) appScope.cancel()
        root.deleteRecursively()
    }

    private fun newRepo(): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    /** 建一个"库目录 + kb.json + memory/lessons.md"的最小现场（不过迁移器，避开 assets 那一路） */
    private fun library(repo: KnowledgeRepository, lessons: String): File {
        val dir = File(root, KB).apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(KnowledgeBase.serializer(), KnowledgeBase(name = KB, displayName = KB)),
            Charsets.UTF_8
        )
        File(dir, "memory").mkdirs()
        File(dir, "memory/lessons.md").writeText(lessons, Charsets.UTF_8)
        return dir
    }

    private fun file(dir: File, relativePath: String): File = File(dir, relativePath)

    // ═══════════ 用户实测到的那一份脏档（编号 1、1、3、1、5、1）═══════════

    /** 两对成对节头 + 一对只有标题没正文 + 编号已经跳到 5：与归档里那两份的形状同构 */
    private val messyUserFile = """
        # 经验库

        军师自动追加提取节，节头格式：# [yyyy-MM-dd HH:mm] 第N次提取（模板内不放示例节）

        # [2026-09-17 10:00] 第1次提取

        # [2026-09-17 10:00] 第1次提取

        ## 踩过的坑
        - 踩坑：她把「格式」两字拿来开玩笑时我认真解释了；教训：跟着松

        # [2026-09-17 11:20] 第3次提取

        # [2026-09-17 ]第3次提取

        ## 测试接法
        - 测试：连续抛"会不会找我"式试探；接法：高框架弹回

        # [2026-09-18 09:05] 第5次提取
    """.trimIndent()

    /** 生产侧那颗 compose：整理 + 按整理后的批数发号 + 追加这一批（与 extractLessons 同一形状） */
    private fun productionCompose(newBody: String): (String) -> String? = { existing ->
        val tidied = LessonDoc.tidy(existing)
        val header = LessonDoc.sectionHeader("2026-10-04 15:00", LessonDoc.nextExtractionNumber(tidied.text))
        if (tidied.text.isEmpty()) "$header\n\n$newBody" else "${tidied.text}\n\n$header\n\n$newBody"
    }

    // ═══════════ ① 计数 / 发号 / 检查 / 替换全在同一把锁里 ═══════════

    /**
     * compose 拿到的那一刻，仓库那把 `fileMutex` **必须正被持有**。
     *
     * 坏实现长什么样：`val existing = readFile(...)`（自己加一次锁、放掉）→ `compose(existing)` →
     * 再进锁写。那种写法下这一行读到的是 `isLocked == false`，这一格当场红——
     * 而这正是  之前 `extractLessons` 的真实形状（  P2 第 1 条点名的那个窗口）。
     */
    @Test
    fun theNumberIsAllocatedWhileTheRepositoryLockIsHeld() = runBlocking {
        val repo = newRepo()
        library(repo, messyUserFile)

        var lockedWhenComposed: Boolean? = null
        val result = repo.readTidyAndReplaceWithRevisionCheck(KB, LessonDoc.LESSONS_PATH, 0) { existing ->
            lockedWhenComposed = repo.fileMutex.isLocked
            productionCompose("## 做对的\n- 做对：先接情绪")(existing)
        }

        assertTrue("事务必须落盘，实际：$result", result is LessonsRewriteResult.Rewritten)
        assertEquals("compose 必须在锁内被调用（读正文与发号不能掉到锁外）", true, lockedWhenComposed)
    }

    /**
     * 两批并发进来：**两条都要在，编号不能撞**。
     *
     * 这一格是①的行为证人（`isLocked` 那格钉的是形状，这格钉的是结果）：
     * 坏实现把"读"放到锁外，两个协程都从同一篇正文数出同一个 next，
     * 于是第二次替换把第一次那一整篇盖掉 ⇒ 文件里只剩一批、或者两条节头同号。
     */
    @Test
    fun concurrentTidyAndAppendDoNotBurnTheSameNumberTwice() = runBlocking {
        val repo = newRepo()
        val dir = library(repo, messyUserFile)

        val jobs = listOf(
            async(Dispatchers.IO) {
                repo.readTidyAndReplaceWithRevisionCheck(KB, LessonDoc.LESSONS_PATH, 0, productionCompose("## 加分项\n- 触发：甲"))
            },
            async(Dispatchers.IO) {
                repo.readTidyAndReplaceWithRevisionCheck(KB, LessonDoc.LESSONS_PATH, 0, productionCompose("## 加分项\n- 触发：乙"))
            }
        )
        val results = jobs.map { it.await() }

        results.forEach { r ->
            assertTrue("两批都必须落盘，实际：$r", r is LessonsRewriteResult.Rewritten)
        }
        val final = file(dir, LessonDoc.LESSONS_PATH).readText(Charsets.UTF_8)
        val numbers = Regex("""第(\d+)次提取""").findAll(final).map { it.groupValues[1].toInt() }.toList()
        // 账：脏档 3 个有效批 → 第一趟整理成 1..3 再加第 4 次；第二趟进来读到的已是整理过的那一篇，发第 5 次。
        // 两趟谁先谁后不影响这个总数，但**任何一趟把读放到锁外**都会让两边都数到 4。
        assertEquals("整理后的 3 批 + 并发进来的 2 批 = 5 条节头", 5, numbers.size)
        assertEquals("编号必须是连续的 1..5（撞号或漏号都在这里红）", (1..5).toList(), numbers)
        assertTrue("甲那一批的正文不许被乙盖掉", final.contains("- 触发：甲"))
        assertTrue("乙那一批的正文也不许丢", final.contains("- 触发：乙"))
        assertTrue("真实历史正文一句都不许少", final.contains("- 测试：连续抛\"会不会找我\"式试探；接法：高框架弹回"))
    }

    // ═══════════ ② 写前先有快照；写失败不得丢正文 ═══════════

    /**
     * 真文件系统上的整理：目标换成整理后的那一篇，**快照里是整理前的原件逐字**。
     *
     * 坏实现（直接 `writeFile(整理后的)`、没有快照）在 `snapshotPath` 那句就红，
     * 或者读回来的是"整理后的"而不是原件。
     */
    @Test
    fun theRealTidyLandsWithAByteIdenticalSnapshotOfTheOriginal() = runBlocking {
        val repo = newRepo()
        val dir = library(repo, messyUserFile)

        val result = repo.readTidyAndReplaceWithRevisionCheck(
            KB, LessonDoc.LESSONS_PATH, 0, productionCompose("## 做对的\n- 做对：先接情绪；有效：她愿意继续说")
        )
        assertTrue("必须落盘，实际：$result", result is LessonsRewriteResult.Rewritten)
        val snapshotPath = (result as LessonsRewriteResult.Rewritten).snapshotPath
        assertNotNull("非空原件必须有快照，实际：$result", snapshotPath)

        assertEquals(
            "快照里必须是**整理前**的原件，逐字一字不差",
            messyUserFile, file(dir, snapshotPath!!).readText(Charsets.UTF_8)
        )
        val tidied = file(dir, LessonDoc.LESSONS_PATH).readText(Charsets.UTF_8)
        val numbers = Regex("""第(\d+)次提取""").findAll(tidied).map { it.groupValues[1].toInt() }.toList()
        // 脏档 1、1、3、1、5、1 → 三个有效批（成对节头算一批、只有标题的那条也算一批）+ 新写的第 4 次
        assertEquals("用户那份 1、1、3、1、5、1 必须被收敛成连续的 1..4", (1..4).toList(), numbers)
        assertFalse("模板序言不许再留在用户文件里", tidied.contains("军师自动追加提取节"))
        assertTrue("真实正文必须原样活着", tidied.contains("- 踩坑：她把「格式」两字拿来开玩笑时我认真解释了；教训：跟着松"))
        assertEquals(
            "快照命名规则只有一个主人（data 那一格），路径就是它算出来的那颗",
            "memory/.lessons.pre-tidy.md", snapshotPath
        )
    }

    /**
     * 第二趟同样输入必须 **一个字节都不写**（③的可重入证人，同时反证"没变也重写一次"）。
     *
     * 证人为什么选快照内容而不是 lastModified：只要实现肯再写一次，它就会拿"当前内容"当快照盖上去，
     * 于是那句"快照仍是第一趟之前的原件"立刻红；不肯写的话这里连 write 都不会发生。
     */
    @Test
    fun tidyingAnAlreadyCleanFileIsANoOpOnTheSecondRun() = runBlocking {
        val repo = newRepo()
        val dir = library(repo, messyUserFile)

        val first = repo.readTidyAndReplaceWithRevisionCheck(
            KB, LessonDoc.LESSONS_PATH, 0, productionCompose("## 做对的\n- 做对：先接情绪")
        )
        assertTrue("第一趟必须落盘，实际：$first", first is LessonsRewriteResult.Rewritten)
        val afterFirst = file(dir, LessonDoc.LESSONS_PATH).readText(Charsets.UTF_8)
        val snapshotAfterFirst = file(dir, "memory/.lessons.pre-tidy.md").readText(Charsets.UTF_8)

        // 第二趟：把当前文件交给**同一个整理**——tidy 之后没有新批要加（compose 交回逐字相同的一篇）
        val second = repo.readTidyAndReplaceWithRevisionCheck(KB, LessonDoc.LESSONS_PATH, 0) { existing ->
            LessonDoc.tidy(existing).text
        }
        assertTrue(
            "已经整理过的文件再进来一次必须报「什么都不写」，实际：$second",
            second == LessonsRewriteResult.NothingToWrite
        )
        assertEquals("正文不许被那趟 no-op 改动", afterFirst, file(dir, LessonDoc.LESSONS_PATH).readText(Charsets.UTF_8))
        assertEquals(
            "快照必须还是最早那份原件（被盖掉就说明它写过盘）",
            snapshotAfterFirst, file(dir, "memory/.lessons.pre-tidy.md").readText(Charsets.UTF_8)
        )
    }

    // ═══════════ ② / ③ 的坏实现对照：拿 fake 存储注入那三条都不许过 ═══════════

    /**
     * 快照没落成 ⇒ **不许动原件**。坏实现（先替换、后快照，或压根不判快照返回值）在这一格红：
     * 它会往目标写一次，而这里 `writes` 只许有一次、且是快照那一次。
     */
    @Test
    fun aSnapshotThatDoesNotLandStopsTheReplacementEntirely() {
        val storage = FakeLessonsStorage(content = "原件内容\n")
        storage.snapshotLands = false
        val service = KnowledgeLessonsRewriteService(storage)

        val result = service.rewrite(KB, LessonDoc.LESSONS_PATH, 0) { existing -> "$existing\n新批" }

        assertTrue("必须报快照失败，实际：$result", result == LessonsRewriteResult.SnapshotFailed)
        assertEquals("只许尝试过一次落盘，而且必须是快照那一次", listOf(SNAPSHOT), storage.writes.map { it.first })
        assertEquals("原件一个字节都不许动", "原件内容\n", storage.content)
        assertTrue(
            "挡下必须留痕（一条不出声的 false 与静默跳过分不开）：${storage.notes}",
            storage.notes.any { it.contains("snapshot") }
        )
    }

    /**
     * 替换没落成 ⇒ 交回整理后的全文 + **不报成功**（第8节第3条「写入失败不报告成功」）。
     * 坏实现返回 true / Rewritten 就在这里红。
     */
    @Test
    fun aRefusedReplacementHandsTheComposedTextBackInsteadOfReportingSuccess() {
        val storage = FakeLessonsStorage(content = "原件内容\n")
        storage.targetLands = false
        val service = KnowledgeLessonsRewriteService(storage)

        val result = service.rewrite(KB, LessonDoc.LESSONS_PATH, 0) { existing -> "$existing\n新批" }

        assertTrue("必须报替换失败并把全文交回，实际：$result", result is LessonsRewriteResult.WriteFailed)
        assertEquals("交回的必须正是那一篇（正文不许被咽掉）", "原件内容\n\n新批", (result as LessonsRewriteResult.WriteFailed).composed)
        assertEquals("原件仍在", "原件内容\n", storage.content)
        assertEquals("快照已经落了，所以原件的备份也在", 2, storage.writes.size)
    }

    /**
     * 写链撒谎（返回 true 但字节没落）也**不许**被报成成功——这一格钉的是落盘后的回读核对。
     * 把 `rewrite` 里那次回读删掉，这一格立刻红。
     */
    @Test
    fun aWriterThatClaimsSuccessButStoresNothingIsCaughtByTheReadBack() {
        val storage = FakeLessonsStorage(content = "原件内容\n")
        storage.liesAboutWrites = true // 每次都返回 true，但什么都不存
        val service = KnowledgeLessonsRewriteService(storage)

        val result = service.rewrite(KB, LessonDoc.LESSONS_PATH, 0) { existing -> "$existing\n新批" }

        assertTrue("回读对不上就是没写成，不许报成功，实际：$result", result is LessonsRewriteResult.WriteFailed)
        assertEquals("原文一字未动", "原件内容\n", storage.content)
    }

    /** 逐字相同 / compose 交回 null：连快照都不许落（③最严的那一档——一次 write 都不许多） */
    @Test
    fun identicalCompositionWritesNotEvenASnapshot() {
        val unchanged = FakeLessonsStorage(content = "已经是干净的一篇\n")
        KnowledgeLessonsRewriteService(unchanged).rewrite(KB, LessonDoc.LESSONS_PATH, 0) { it }
        assertTrue("内容没变就不许有任何一次落盘：${unchanged.writes}", unchanged.writes.isEmpty())

        val declined = FakeLessonsStorage(content = "有东西\n")
        val result = KnowledgeLessonsRewriteService(declined).rewrite(KB, LessonDoc.LESSONS_PATH, 0) { null }
        assertTrue("compose=null 必须报「什么都不写」，实际：$result", result == LessonsRewriteResult.NothingToWrite)
        assertTrue("compose=null 也不许落任何一次盘：${declined.writes}", declined.writes.isEmpty())
    }

    /**
     * 过期调用：revision 检查必须**在读正文之前**——compose 一次都不许被调用。
     * 坏实现（先读先发号、最后才比 revision）会在这里红：分配了编号却没落盘，正是 第8节第3条 禁的那件事。
     */
    @Test
    fun aStaleRevisionRefusesBeforeTheBodyIsEvenRead() {
        val storage = FakeLessonsStorage(content = "原件内容\n", revision = 7)
        val result = KnowledgeLessonsRewriteService(storage).rewrite(KB, LessonDoc.LESSONS_PATH, 3) { existing ->
            storage.composeCalls++
            "$existing\n不该写进来的批"
        }
        assertTrue("必须报 revision 已变，实际：$result", result == LessonsRewriteResult.RevisionChanged)
        assertEquals("过期调用不许读正文、不许发号", 0, storage.composeCalls)
        assertEquals("revision 检查必须在读正文**之前**（读了正文就是已经进过锁里的读了）", 0, storage.readCalls)
        assertEquals("一个字节都不许写", 0, storage.writes.size)
    }

    /** 库没了 / 只读库都从仓库那一侧挡下（只读那一支由 transaction 入口判，见 ReadOnlySchemaWriteGateTest 同一口径） */
    @Test
    fun aMissingLibraryOrANewerSchemaRefusesTheWholeTransaction() = runBlocking {
        val repo = newRepo()
        val gone = repo.readTidyAndReplaceWithRevisionCheck("没有这本库", LessonDoc.LESSONS_PATH, 0) { it + "\n批" }
        assertTrue("库不在必须报写不了，实际：$gone", gone is LessonsRewriteResult.MissingLibrary)

        val dir = library(repo, messyUserFile)
        file(dir, ".schema_version").writeText((KnowledgeSchemaVersion.CURRENT + 1).toString(), Charsets.UTF_8)
        repo.migrateIfNeeded(KB)
        assertTrue("先让迁移器把这库判成只读", repo.isSchemaReadOnly(KB))
        val before = file(dir, LessonDoc.LESSONS_PATH).readText(Charsets.UTF_8)

        val refused = repo.readTidyAndReplaceWithRevisionCheck(KB, LessonDoc.LESSONS_PATH, 0) { it + "\n只读库不该收到" }
        assertTrue("只读库必须挡在事务入口，实际：$refused", refused == LessonsRewriteResult.RefusedNewerSchema)
        assertEquals("正文一字未动", before, file(dir, LessonDoc.LESSONS_PATH).readText(Charsets.UTF_8))
        assertFalse("也不许留下快照", file(dir, SNAPSHOT).exists())
    }

    /** 越界路径必须被同一道 canonical 守门挡下，不许在库目录外落任何字节 */
    @Test
    fun pathsThatTryToLeaveTheLibraryAreRefusedBeforeAnythingIsWritten() = runBlocking {
        val repo = newRepo()
        val dir = library(repo, messyUserFile)

        val escaping = repo.readTidyAndReplaceWithRevisionCheck(KB, "../outside-lessons.md", 0) { "越界写入" }
        assertTrue("越界路径必须报写不了，实际：$escaping", escaping is LessonsRewriteResult.MissingLibrary)
        assertFalse("knowledge/ 根外不许出现那份文件", File(root, "outside-lessons.md").exists())
        assertFalse("库里也不许出现快照", file(dir, SNAPSHOT).exists())
    }

    // ═══════════ 取消：原样重抛，不许被当成"写失败"咽掉 ═══════════

    /**
     * 锁被占住时发起这次事务、随后取消调用方：出来的必须是 **CancellationException**，
     * 而不是一个"失败结果"，也不是无声无息。
     *
     * 坏实现（`runCatching` 或 `catch (e: Exception)` 把取消咽掉、或把它折算成 MissingLibrary）
     * 在这里红：`observed` 会拿到 null 或一个非 CE，"被取消"与"整理失败"就分不开了。
     */
    @Test
    fun cancellationWhileTheLockIsHeldPropagates() = runBlocking {
        val repo = newRepo()
        library(repo, messyUserFile)
        var composeRan = false
        val lockIsHeld = java.util.concurrent.CountDownLatch(1)
        val observed = CompletableDeferred<Throwable?>()

        val blocker = launch(Dispatchers.IO) {
            repo.fileMutex.withLock {
                lockIsHeld.countDown()
                delay(5_000) // 一直占着锁，直到下面那个调用方被取消
            }
        }
        lockIsHeld.await()

        val job = launch(Dispatchers.IO) {
            try {
                repo.readTidyAndReplaceWithRevisionCheck(KB, LessonDoc.LESSONS_PATH, 0) { existing ->
                    composeRan = true
                    "$existing\n不该出现"
                }
                observed.complete(null)
            } catch (e: Throwable) {
                observed.complete(e)
                throw e // 测试侧也要原样重抛：这里就是在量这一条
            }
        }
        job.cancel()

        val cause = withTimeout(5_000) { observed.await() }
        assertTrue(
            "取消必须原样传出 CancellationException，实际拿到：${cause?.let { it::class.simpleName } ?: "null（=被咽掉了）"}",
            cause is CancellationException
        )
        assertFalse("还在等锁的时候根本不该读到正文", composeRan)
        blocker.cancel()
    }

    // ═══════════ 注入用的那份假存储 ═══════════

    /**
     * 只给 [KnowledgeLessonsRewriteService] 用的假存储：能如实回答"落没落盘"，
     * 也能源源不断撒谎（[liesAboutWrites]）来验那道回读。
     *
     * 它同时是那三条坏实现的靶子：快照不落成、替换不成、写链报假成功——
     * 生产码里这三条都没有别的格子覆盖（物理失败要靠真断磁盘）。
     */
    private class FakeLessonsStorage(
        var content: String,
        var revision: Int = 0,
        var libraryExists: Boolean = true,
        var snapshotLands: Boolean = true,
        var targetLands: Boolean = true,
        var liesAboutWrites: Boolean = false
    ) : LessonsRewriteStorage {
        val writes = mutableListOf<Pair<String, String>>()
        val notes = mutableListOf<String>()
        var readCalls = 0
        var composeCalls = 0

        override fun note(message: String) { notes += message }
        override fun kbExists(kbName: String): Boolean = libraryExists
        override fun revisionOf(kbName: String): Int = revision
        override fun readGuarded(kbName: String, relativePath: String): String {
            readCalls++
            return when (relativePath) {
                LessonDoc.LESSONS_PATH -> content
                else -> "" // 快照不参与回读，读它就是空（回读只认目标那一条）
            }
        }

        override fun writeChecked(kbName: String, relativePath: String, content: String): Boolean {
            writes += relativePath to content
            if (liesAboutWrites) return true
            val lands = if (relativePath == LessonDoc.LESSONS_PATH) targetLands else snapshotLands
            if (lands && relativePath == LessonDoc.LESSONS_PATH) this.content = content
            return lands
        }
    }

    private companion object {
        const val KB = "kb"
        const val SNAPSHOT = "memory/.lessons.pre-tidy.md"
    }
}
