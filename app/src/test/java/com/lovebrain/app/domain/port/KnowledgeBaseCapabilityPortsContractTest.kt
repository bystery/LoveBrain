package com.lovebrain.app.domain.port

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import com.lovebrain.app.model.PreconditionReason
import com.lovebrain.app.model.ProfileTransactionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * [KnowledgeBaseCapabilityPorts] 三族端口（[KnowledgeDocumentPort] /
 * [KnowledgeBaseCatalogPort] / [KnowledgeRuntimePort]）的合同。
 *
 * 沿用本仓库既有合同打法（[AiGatewayContract] / [KbArchivePortContract] / [KnowledgePortContract]）：
 * 每族给出一个内存 fake，再给一份不依赖 Android/网络的格子钉住"谁实现谁就必须给出
 * 同样的可观察行为"。这三族是给页面用的，但语义判据（只读保护、版本化写、revision 单调、
 * 事务前置条件）必须由 fake 同样遵守——否则 UI 测试全绿、设备上不是同一台机器。
 *
 * 生产侧（[com.lovebrain.app.data.KnowledgeRepository]）的核心读/写语义已由 [KnowledgePortContract]
 * 覆盖；这里只补三族各自专属的成员（版本化写、库的生命周期、运行时事务）。
 */

// ═══════════ KnowledgeDocumentPort ═══════════

abstract class KnowledgeDocumentPortContract {
    protected abstract fun newPort(): KnowledgeDocumentPort

    @Test
    fun `reading a file that does not exist yields empty instead of throwing`() {
        val p = newPort()
        assertEquals("", runBlocking { p.readFile("kb", "moment/none.md") })
    }

    @Test
    fun `write then read round-trips exactly`() {
        val p = newPort()
        runBlocking { p.writeFile("kb", "understand/me.md", "她喜欢早睡") }
        assertEquals("她喜欢早睡", runBlocking { p.readFile("kb", "understand/me.md") })
    }

    @Test
    fun `version-checked write rejects a stale caller and accepts the current one`() {
        val p = newPort()
        // 首写：absent 文件的当前版本是空串
        val first = runBlocking { p.writeFileWithVersion("kb", "moment/scene.md", "v1", "") }
        assertNotNull("expectedVersion 与空版本匹配时首写必须成功", first)
        // 过期调用：传错版本必须返回 null 且不改动内容
        assertNull(runBlocking { p.writeFileWithVersion("kb", "moment/scene.md", "stale", "wrong-version") })
        assertEquals("v1", runBlocking { p.readFile("kb", "moment/scene.md") })
        // 当前版本写入：传首写返回的版本必须成功，并交回新版本
        val second = runBlocking { p.writeFileWithVersion("kb", "moment/scene.md", "v2", first!!) }
        assertNotNull(second)
        assertEquals("v2", runBlocking { p.readFile("kb", "moment/scene.md") })
        assertNotEquals("新版本必须与旧版本不同", first, second)
    }

    @Test
    fun `hashContent is stable and discriminating`() {
        val p = newPort()
        val a = p.hashContent("同一段文本")
        assertEquals(a, p.hashContent("同一段文本"))
        assertNotEquals(a, p.hashContent("另一段文本"))
    }
}

class InMemoryKnowledgeDocumentPortContractTest : KnowledgeDocumentPortContract() {
    override fun newPort(): KnowledgeDocumentPort = InMemoryKnowledgeDocumentPort()
}

class InMemoryKnowledgeDocumentPort : KnowledgeDocumentPort {
    private val files = LinkedHashMap<String, String>()
    private val versions = LinkedHashMap<String, String>()
    private fun key(kb: String, path: String) = "$kb/$path"

    override suspend fun migrateIfNeeded(kbName: String) { /* schema always current in fake */ }
    override suspend fun readFile(kbName: String, relativePath: String): String =
        files[key(kbName, relativePath)] ?: ""
    override suspend fun writeFile(kbName: String, relativePath: String, content: String) {
        val k = key(kbName, relativePath)
        files[k] = content
        versions[k] = content.hashCode().toString()
    }
    override suspend fun readFileWithVersion(kbName: String, relativePath: String): Pair<String, String> {
        val k = key(kbName, relativePath)
        return (files[k] ?: "") to (versions[k] ?: "")
    }
    override suspend fun writeFileWithVersion(
        kbName: String, relativePath: String, content: String, expectedVersion: String
    ): String? {
        val k = key(kbName, relativePath)
        if ((versions[k] ?: "") != expectedVersion) return null
        files[k] = content
        val newVersion = content.hashCode().toString()
        versions[k] = newVersion
        return newVersion
    }
    override fun hashContent(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

// ═══════════ KnowledgeBaseCatalogPort ═══════════

abstract class KnowledgeBaseCatalogPortContract {
    protected abstract fun newPort(): KnowledgeBaseCatalogPort

    @Test
    fun `an empty catalog reports no bases and no active`() {
        val p = newPort()
        assertEquals(emptyList<KnowledgeBase>(), runBlocking { p.listAll() })
        assertNull(runBlocking { p.getActive() })
    }

    @Test
    fun `create returns the base and lists it`() {
        val p = newPort()
        val kb = runBlocking { p.create("kb1", "小雅的库") }
        assertEquals("kb1", kb.name)
        assertEquals("小雅的库", kb.displayName)
        assertEquals(listOf(kb), runBlocking { p.listAll() })
    }

    @Test
    fun `setActive toggles which base getActive returns`() {
        val p = newPort()
        runBlocking { p.create("a", "A"); p.create("b", "B") }
        runBlocking { p.setActive("b") }
        assertEquals("b", runBlocking { p.getActive() }?.name)
        runBlocking { p.setActive("a") }
        assertEquals("a", runBlocking { p.getActive() }?.name)
    }

    @Test
    fun `delete reports what actually happened`() {
        val p = newPort()
        runBlocking { p.create("a", "A") }
        assertTrue(runBlocking { p.delete("a") })
        assertFalse("删不存在的库要返回 false", runBlocking { p.delete("a") })
        assertEquals(emptyList<KnowledgeBase>(), runBlocking { p.listAll() })
    }

    @Test
    fun `display name and stage round-trip`() {
        val p = newPort()
        runBlocking { p.create("a", "A") }
        runBlocking { p.updateDisplayName("a", "新名字") }
        runBlocking { p.updateStage("a", "升温") }
        val kb = runBlocking { p.listAll() }.single()
        assertEquals("新名字", kb.displayName)
        assertEquals("升温", kb.stage)
    }
}

class InMemoryKnowledgeBaseCatalogPortContractTest : KnowledgeBaseCatalogPortContract() {
    override fun newPort(): KnowledgeBaseCatalogPort = InMemoryKnowledgeBaseCatalogPort()
}

class InMemoryKnowledgeBaseCatalogPort : KnowledgeBaseCatalogPort {
    private val bases = LinkedHashMap<String, KnowledgeBase>()

    override suspend fun listAll(): List<KnowledgeBase> = bases.values.toList()
    override suspend fun getActive(): KnowledgeBase? = bases.values.firstOrNull { it.active }
    override suspend fun setActive(name: String) {
        if (!bases.containsKey(name)) throw IllegalArgumentException("KB not found: $name")
        bases.replaceAll { k, v -> v.copy(active = (k == name)) }
    }
    override suspend fun create(name: String, displayName: String): KnowledgeBase {
        if (bases.containsKey(name)) throw IllegalArgumentException("KB already exists: $name")
        val kb = KnowledgeBase(name = name, displayName = displayName, active = bases.isEmpty())
        bases[name] = kb
        return kb
    }
    override suspend fun delete(name: String): Boolean = bases.remove(name) != null
    override suspend fun updateDisplayName(kbName: String, newDisplay: String) {
        bases[kbName]?.let { bases[kbName] = it.copy(displayName = newDisplay) }
    }
    override suspend fun updateStage(kbName: String, stage: String) {
        bases[kbName]?.let { bases[kbName] = it.copy(stage = stage) }
    }
    override suspend fun writeFile(kbName: String, relativePath: String, content: String) {
        /* 建库事务写画像三段——目录写侧只转手，内容级写归仓库；fake 仅记账不落盘 */
    }
}

// ═══════════ KnowledgeRuntimePort ═══════════

abstract class KnowledgeRuntimePortContract {
    protected abstract fun newPort(): KnowledgeRuntimePort

    @Test
    fun `readIntent defaults to a disabled empty intent`() {
        val p = newPort()
        val intent = runBlocking { p.readIntent("kb") }
        assertFalse("默认意图必须关闭", intent.enabled)
        assertEquals("", intent.text)
    }

    @Test
    fun `saveIntent round-trips and bumps revision each save`() {
        val p = newPort()
        val first = runBlocking {
            p.saveIntent("kb", "推进周末见面", enabled = true, status = IntentStatus.ACTIVE)
        }
        assertEquals("推进周末见面", first.text)
        assertTrue(first.enabled)
        val second = runBlocking {
            p.saveIntent("kb", "推进周末见面", enabled = true, status = IntentStatus.PAUSED)
        }
        assertTrue("每次保存 revision 必须单调递增", second.revision > first.revision)
        assertEquals("保存后回读必须等于保存值", second, runBlocking { p.readIntent("kb") })
    }

    @Test
    fun `corrections start empty and revision is zero`() {
        val p = newPort()
        assertEquals(emptyMap<String, MemoryCorrection>(), runBlocking { p.readCorrections("kb") })
        assertEquals(0, runBlocking { p.getCorrectionsRevision("kb") })
    }

    @Test
    fun `saveCorrection adds and bumps revision undo removes and bumps again`() {
        val p = newPort()
        val saved = runBlocking {
            p.saveCorrection("kb", "scene:moment/scene.md", CorrectionAction.WRONG)
        }
        assertTrue(saved)
        val one = runBlocking { p.readCorrections("kb") }
        assertEquals(1, one.size)
        val rev1 = runBlocking { p.getCorrectionsRevision("kb") }
        assertTrue("保存纠正后 revision 必须 > 0", rev1 > 0)
        val undone = runBlocking { p.undoCorrection("kb", "scene:moment/scene.md") }
        assertTrue(undone)
        assertEquals(emptyMap<String, MemoryCorrection>(), runBlocking { p.readCorrections("kb") })
        assertTrue("撤销也递增 revision", runBlocking { p.getCorrectionsRevision("kb") } > rev1)
        assertFalse("撤销不存在的纠正返回 false", runBlocking { p.undoCorrection("kb", "ghost") })
    }

    @Test
    fun `applyProfileUpdateAtomically rejects a stale revision and accepts the current one`() {
        val p = newPort()
        // 当前 revision 是 0
        val stale = runBlocking {
            p.applyProfileUpdateAtomically("kb", me = "x", her = null, warmth = null,
                stageChanged = false, newStage = null, expectedRevision = 99)
        }
        assertTrue("过期 revision 必须返回 PreconditionFailed", stale is ProfileTransactionResult.PreconditionFailed)
        assertEquals(
            PreconditionReason.REVISION_CONFLICT,
            (stale as ProfileTransactionResult.PreconditionFailed).reason
        )
        assertEquals("前置失败不得改动画像", "", runBlocking { p.readProfile("kb") })
        val ok = runBlocking {
            p.applyProfileUpdateAtomically("kb", me = "我喜欢直球", her = null, warmth = null,
                stageChanged = false, newStage = null, expectedRevision = 0)
        }
        assertEquals(ProfileTransactionResult.Success, ok)
        assertEquals("我喜欢直球", runBlocking { p.readProfile("kb") })
    }

    @Test
    fun `actual sent record appends then replaces`() {
        val p = newPort()
        runBlocking { p.appendActualSentRecord("kb", "回复A") }
        runBlocking { p.appendActualSentRecord("kb", "回复B") }
        assertTrue(runBlocking { p.replaceActualSentRecord("kb", "回复A", "回复A改") })
        assertFalse("替换不存在的条目返回 false", runBlocking { p.replaceActualSentRecord("kb", "不存在", "x") })
    }
}

class InMemoryKnowledgeRuntimePortContractTest : KnowledgeRuntimePortContract() {
    override fun newPort(): KnowledgeRuntimePort = InMemoryKnowledgeRuntimePort()
}

class InMemoryKnowledgeRuntimePort : KnowledgeRuntimePort {
    private val bases = LinkedHashMap<String, KnowledgeBase>()
    private val files = LinkedHashMap<String, String>()
    private val stages = mutableMapOf<String, String>()
    private val profiles = mutableMapOf<String, String>()
    private val contentRevisions = mutableMapOf<String, Int>()
    private val intents = mutableMapOf<String, IntentConfig>()
    private val corrections = mutableMapOf<String, MutableMap<String, MemoryCorrection>>()
    private val correctionRevisions = mutableMapOf<String, Int>()
    private val counselingLog = mutableMapOf<String, StringBuilder>()
    private val actualSent = mutableMapOf<String, StringBuilder>()

    fun seed(kb: KnowledgeBase) { bases[kb.name] = kb }

    override suspend fun listAll(): List<KnowledgeBase> = bases.values.toList()
    override suspend fun getActive(): KnowledgeBase? = bases.values.firstOrNull { it.active }
    override suspend fun migrateIfNeeded(kbName: String) {}
    override suspend fun ensureInitialKnowledgeBase() {}
    override suspend fun updateStage(kbName: String, stage: String) { stages[kbName] = stage }
    override suspend fun readFile(kbName: String, relativePath: String): String = files["$kbName/$relativePath"] ?: ""
    override suspend fun readVector(kbName: String): Map<String, Int> = emptyMap()
    override suspend fun readProfile(kbName: String): String = profiles[kbName] ?: ""
    override suspend fun contentRevision(kbName: String): String = (contentRevisions[kbName] ?: 0).toString()
    override suspend fun updateWarmthStageLabel(kbName: String, newStage: String) { stages[kbName] = newStage }

    override suspend fun applyProfileUpdateAtomically(
        kbName: String, me: String?, her: String?, warmth: String?,
        stageChanged: Boolean, newStage: String?, expectedRevision: Int
    ): ProfileTransactionResult {
        val current = contentRevisions[kbName] ?: 0
        if (current != expectedRevision)
            return ProfileTransactionResult.PreconditionFailed(PreconditionReason.REVISION_CONFLICT)
        if (me != null) profiles[kbName] = me
        if (stageChanged && newStage != null) stages[kbName] = newStage
        contentRevisions[kbName] = current + 1
        return ProfileTransactionResult.Success
    }

    override suspend fun readIntent(kbName: String): IntentConfig = intents[kbName] ?: IntentConfig()
    override suspend fun saveIntent(
        kbName: String, text: String, enabled: Boolean, expiry: IntentExpiry, expiryDate: String, status: IntentStatus
    ): IntentConfig {
        val next = IntentConfig(
            text = text, enabled = enabled, revision = (intents[kbName]?.revision ?: 0) + 1,
            expiry = expiry, expiryDate = expiryDate, status = status
        )
        intents[kbName] = next
        return next
    }

    override suspend fun readCorrections(kbName: String): Map<String, MemoryCorrection> =
        corrections[kbName]?.toMap() ?: emptyMap()
    override suspend fun readCorrectionsAndRevision(kbName: String): Pair<Map<String, MemoryCorrection>, Int> =
        readCorrections(kbName) to (correctionRevisions[kbName] ?: 0)
    override suspend fun getCorrectionsRevision(kbName: String): Int = correctionRevisions[kbName] ?: 0
    override suspend fun saveCorrection(
        kbName: String, memoryId: String, action: CorrectionAction,
        replacementText: String, targetKbId: String, muteDuration: MuteDuration
    ): Boolean {
        val map = corrections.getOrPut(kbName) { mutableMapOf() }
        val rev = (correctionRevisions[kbName] ?: 0) + 1
        map[memoryId] = MemoryCorrection(
            memoryId = memoryId, action = action, replacementText = replacementText,
            targetKbId = targetKbId, muteDuration = muteDuration, revision = rev
        )
        correctionRevisions[kbName] = rev
        return true
    }
    override suspend fun undoCorrection(kbName: String, memoryId: String): Boolean {
        val map = corrections[kbName] ?: return false
        val removed = map.remove(memoryId) != null
        if (removed) correctionRevisions[kbName] = (correctionRevisions[kbName] ?: 0) + 1
        return removed
    }

    override suspend fun appendCounselingEntries(kbName: String, recordEntry: String, analysisEntry: String) {
        counselingLog.getOrPut(kbName) { StringBuilder() }
            .append(recordEntry).append('\n').append(analysisEntry).append('\n')
    }
    override suspend fun appendActualSentRecord(kbName: String, entry: String): Boolean {
        actualSent.getOrPut(kbName) { StringBuilder() }.append(entry).append('\n')
        return true
    }
    override suspend fun replaceActualSentRecord(kbName: String, oldEntry: String, newEntry: String): Boolean {
        val sb = actualSent[kbName] ?: return false
        if (!sb.contains(oldEntry)) return false
        actualSent[kbName] = StringBuilder(sb.toString().replace(oldEntry, newEntry))
        return true
    }
}
