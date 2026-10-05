package com.lovebrain.app.domain.port

import android.content.Context
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.KnowledgeSchemaVersion
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import java.io.File
import java.nio.file.Files

/**
 * 合同测试之**生产侧**：真 KnowledgeRepository + 临时目录。
 * 这一格绿，说明端口背后的落盘语义真的成立。
 */
class FileBackedKnowledgePortContractTest : KnowledgePortContract() {

    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kb_port_contract").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    override fun newPort(): KnowledgePort {
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = scope
        )
        val dir = File(root, kb).apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(KnowledgeBase.serializer(), KnowledgeBase(name = kb, displayName = kb)),
            Charsets.UTF_8
        )
        runBlocking { repo.migrateIfNeeded(kb) }
        return repo
    }

    override fun markFutureSchema(port: KnowledgePort) {
        File(root, "$kb/.schema_version").writeText(
            (KnowledgeSchemaVersion.CURRENT + 1).toString(), Charsets.UTF_8
        )
        runBlocking { repo.migrateIfNeeded(kb) }
        assertTrue("生产侧必须先被迁移器判成只读", repo.isSchemaReadOnly(kb))
    }

    override fun outsideProbeFile(): File = File(root, "outside-secret.md")

    private fun assertTrue(msg: String, cond: Boolean) = org.junit.Assert.assertTrue(msg, cond)
}

/**
 * 合同测试之 **fake 侧**：同一个抽象类，一个字节都不改。
 * fake 想蒙混过关只有一条路——把语义做对。
 */
class InMemoryKnowledgePortContractTest : KnowledgePortContract() {

    private lateinit var fake: InMemoryKnowledgePort

    override fun newPort(): KnowledgePort {
        fake = InMemoryKnowledgePort()
        fake.seedLibrary(KnowledgeBase(name = kb, displayName = kb, stage = "待确定", turnCount = 0))
        return fake
    }

    override fun markFutureSchema(port: KnowledgePort) {
        fake.readOnlyLibraries.add(kb)
    }
}
