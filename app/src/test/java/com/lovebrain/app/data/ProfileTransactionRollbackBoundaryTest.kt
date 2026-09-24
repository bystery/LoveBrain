package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.ProfileTransactionResult
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 回滚不许有第二条写链（复核报告 P0"Repository 内禁止出现第二条 `atomicWriteText` 公共链"）。
 *
 * `applyProfileUpdateAtomically` 的写前快照、回滚落盘、回滚后校验三段都是
 * 自己 `File(File(knowledgeRoot, kbName), path)` + `atomicWriteText` / `file.delete()`，
 * 绕开了 canonical 守门、只读 schema 拒绝与备份节流。其中"存在性"用**裸** `file.exists()`，
 * "内容"用**守门**的 `readFileUnlockedFast`（那条上一轮已收紧）——两把尺一混，
 * 库名带 `..` 时结论是"外面那个文件存在、但旧内容是空串"，于是回滚**把库外那个文件清空**：
 * 不是"多写了一个字节"，是"删掉了不属于本库的内容"。
 *
 * 每格都配一个库内正对照：同一套机制在真库里必须真的会改文件，
 * 否则"库外没被动过"会因为"什么都没发生"而假绿。
 */
class ProfileTransactionRollbackBoundaryTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private lateinit var root: File
    private lateinit var appScope: CoroutineScope

    private fun newRepo(): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    private fun writeKbJson(dir: File, name: String) {
        File(dir, "kb.json").writeText(
            json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = name, displayName = name, active = true)
            ),
            Charsets.UTF_8
        )
    }

    private fun seedLibrary(dir: File, me: String) {
        dir.mkdirs()
        File(dir, "understand").mkdirs()
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()
        writeKbJson(dir, dir.name)
        File(dir, "understand/me.md").writeText(me, Charsets.UTF_8)
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("profile_tx_boundary").toFile()
    }

    @After
    fun tearDown() {
        if (::appScope.isInitialized) appScope.cancel()
        root.deleteRecursively()
    }

    /** 注入失败：stageChanged=true + 非白名单阶段 → strict 版抛 IOException → 走回滚 */
    private fun runFailingTransaction(repo: KnowledgeRepository, kbName: String, me: String) =
        runBlocking {
            withContext(Dispatchers.IO) {
                repo.applyProfileUpdateAtomically(
                    kbName = kbName,
                    me = me,
                    her = null,
                    warmth = null,
                    stageChanged = true,
                    newStage = "非白名单的假阶段",
                    expectedRevision = 0
                )
            }
        }

    @Test
    fun rollbackDoesNotRewriteAFileOutsideTheKnowledgeRoot() = runBlocking {
        val outside = File(root.parentFile, "tx_outside_" + System.nanoTime())
        seedLibrary(outside, "外面那份画像不该被动")
        val outsideMe = File(outside, "understand/me.md")
        val repo = newRepo()

        // 前置：这个库名确实"看得见却摸不着"——kbExistsUnlocked 用的是裸路径
        assertTrue("前置：库外那份文件要在", outsideMe.exists())

        try {
            runFailingTransaction(repo, "../${outside.name}", "新内容")

            val after = outsideMe.readText(Charsets.UTF_8)
            assertEquals(
                "回滚改写了知识库根外面的文件（这不是本库的内容）。" +
                    "裸 file.exists() 说它在、守门读说内容是空串，两者一混就把外面那份清空了",
                "外面那份画像不该被动", after
            )
        } finally {
            outside.deleteRecursively()
        }
    }

    /** 库内正对照：同一套机制在真库里必须真的回滚——不然上面那格是"本来就没写"的假绿 */
    @Test
    fun anInsideLibraryStillRollsBackToItsPreviousContent() = runBlocking {
        val dir = File(root, "inside_kb")
        seedLibrary(dir, "原来的画像")
        val repo = newRepo()

        val result = runFailingTransaction(repo, "inside_kb", "新内容")

        assertTrue(
            "注入的失败必须真的走到回滚那一段，实到 $result",
            result is ProfileTransactionResult.RolledBack ||
                result is ProfileTransactionResult.RollbackFailed
        )
        assertEquals("原来的画像", File(dir, "understand/me.md").readText(Charsets.UTF_8))
    }

    /**
     * 越界那次调用不许报"写成功了"。
     *
     * ⚠ 这一格在修**之前**就是绿的（正向写本来就被守门拒掉），留着是防回归，
     * 不当本轮证据；本轮的红色证据是上面那格。
     */
    @Test
    fun anEscapedTransactionNeverReportsSuccess() = runBlocking {
        val outside = File(root.parentFile, "tx_target_" + System.nanoTime())
        seedLibrary(outside, "外面那份")
        val repo = newRepo()
        try {
            val result = runFailingTransaction(repo, "../${outside.name}", "新内容")
            assertTrue(
                "库不在根内，任何返回值都不许是 Success（实到 $result）",
                result !is ProfileTransactionResult.Success
            )
        } finally {
            outside.deleteRecursively()
        }
    }
}
