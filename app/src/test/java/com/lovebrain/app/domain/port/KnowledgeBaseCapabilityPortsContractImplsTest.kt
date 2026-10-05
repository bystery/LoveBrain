package com.lovebrain.app.domain.port

import android.content.Context
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.every
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
 * 三族能力端口（[KnowledgeDocumentPort] / [KnowledgeBaseCatalogPort] / [KnowledgeRuntimePort]）
 * 的**生产侧**合同测试：真 [KnowledgeRepository] + 临时目录。
 *
 * 与 [KnowledgePortContractImplsTest] 同形：fake 侧在 [KnowledgeBaseCapabilityPortsContractTest]
 * 里，生产侧在这里。两侧跑同一个抽象合同——fake 想蒙混过关只有一条路：把语义做对。
 */
// ═══════════ KnowledgeDocumentPort ═══════════

class FileBackedKnowledgeDocumentPortContractTest : KnowledgeDocumentPortContract() {

    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kb_doc_port_contract").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    override fun newPort(): KnowledgeDocumentPort {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = mockk<Context>(relaxed = true),
            appScope = scope
        )
        seedMinimalKb(root, "kb")
        runBlocking { repo.migrateIfNeeded("kb") }
        return repo
    }
}

// ═══════════ KnowledgeBaseCatalogPort ═══════════

class FileBackedKnowledgeBaseCatalogPortContractTest : KnowledgeBaseCatalogPortContract() {

    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kb_catalog_port_contract").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    override fun newPort(): KnowledgeBaseCatalogPort {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        var activeKb = ""
        every { prefs.activeKbName } answers { activeKb }
        every { prefs.activeKbName = any() } answers { activeKb = firstArg() }
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = mockk<Context>(relaxed = true),
            appScope = scope
        )
        return repo.catalogWrites
    }
}

// ═══════════ KnowledgeRuntimePort ═══════════

class FileBackedKnowledgeRuntimePortContractTest : KnowledgeRuntimePortContract() {

    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kb_runtime_port_contract").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    override fun newPort(): KnowledgeRuntimePort {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = mockk<Context>(relaxed = true),
            appScope = scope
        )
        seedMinimalKb(root, "kb")
        runBlocking { repo.migrateIfNeeded("kb") }
        return repo
    }
}

// ═══════════ 共用工具 ═══════════

/**
 * 在临时根目录下建一个最小 KB（只有 kb.json + 三个空子目录）。
 * 写入侧的 parentFile.mkdirs() 会按需建子目录，所以这里只保证 kb.json 存在。
 */
private fun seedMinimalKb(root: File, kbName: String) {
    val dir = File(root, kbName).apply { mkdirs() }
    File(dir, "kb.json").writeText(
        Json.encodeToString(KnowledgeBase.serializer(), KnowledgeBase(name = kbName, displayName = kbName)),
        Charsets.UTF_8
    )
}
