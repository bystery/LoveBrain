package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * P1-RC: Repository actual-sent upsert test using real temp directory.
 *
 * Verifies:
 * 1. First actual sent write succeeds
 * 2. Same generation version update: old sent entry removed, new entry exactly one copy
 * 3. Other content in recent.md is preserved
 * 4. replaceActualSentRecord returns false when oldEntry doesn't exist
 */
class ActualSentUpsertTest {

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

    private fun setupKb(kbName: String = "testkb") {
        val dir = File(root, kbName).apply { mkdirs() }
        File(dir, "understand").mkdirs()
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()
        val kb = KnowledgeBase(name = kbName, displayName = "Test", updatedAt = "2026-09-20T09:00:00+08:00", active = true)
        File(dir, "kb.json").writeText(Json.encodeToString(KnowledgeBase.serializer(), kb), Charsets.UTF_8)
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("actual_sent_upsert").toFile()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun `append_then_replace_preserves_other_content`() = runBlocking {
        setupKb("testkb")
        val repo = newRepo()

        // Pre-populate recent.md with existing content
        val recentFile = File(File(root, "testkb"), "moment/recent.md")
        val prefix = "普通内容行1\n普通内容行2\n"
        val suffix = "\n其他内容行1\n其他内容行2\n"
        val sentV1 = "<!-- sent:2026-09-20T10:00:00 linked:null version:v1 candidate:null -->\n我（确认已发送）：v1 message\n"
        recentFile.writeText(prefix + sentV1 + suffix)

        // Replace v1 with v2
        val sentV2 = "<!-- sent:2026-09-20T11:00:00 linked:null version:v1 candidate:null -->\n我（确认已发送）：v2 message\n"
        val result = repo.replaceActualSentRecord("testkb", sentV1, sentV2)

        assertTrue("replace should return true when oldEntry exists", result)

        val finalContent = recentFile.readText()

        // Other content must be preserved
        assertTrue("prefix content should still exist", finalContent.contains("普通内容行1"))
        assertTrue("prefix content should still exist", finalContent.contains("普通内容行2"))
        assertTrue("suffix content should still exist", finalContent.contains("其他内容行1"))
        assertTrue("suffix content should still exist", finalContent.contains("其他内容行2"))

        // Old entry must be gone
        assertFalse("sent v1 should not exist", finalContent.contains("v1 message"))

        // New entry must exist exactly once
        val v2Count = finalContent.split("v2 message").size - 1
        assertEquals("sent v2 should appear exactly once", 1, v2Count)
    }

    @Test
    fun `first_append_succeeds`() = runBlocking {
        setupKb("testkb")
        val repo = newRepo()

        val recentFile = File(File(root, "testkb"), "moment/recent.md")
        recentFile.writeText("initial content\n")

        val sentEntry = "<!-- sent:2026-09-20T10:00:00 linked:null version:v1 candidate:null -->\n我（确认已发送）：hello\n"
        val result = repo.appendActualSentRecord("testkb", sentEntry)

        assertTrue("first append should succeed", result)

        val content = recentFile.readText()
        assertTrue("initial content preserved", content.contains("initial content"))
        assertTrue("sent entry exists", content.contains("hello"))
    }

    @Test
    fun `replace_returns_false_when_old_entry_not_found`() = runBlocking {
        setupKb("testkb")
        val repo = newRepo()

        val recentFile = File(File(root, "testkb"), "moment/recent.md")
        recentFile.writeText("some content without sent entry\n")

        val oldEntry = "<!-- sent:old -->\nold message\n"
        val newEntry = "<!-- sent:new -->\nnew message\n"
        val result = repo.replaceActualSentRecord("testkb", oldEntry, newEntry)

        assertFalse("replace should return false when oldEntry not found", result)

        // Content should be unchanged
        val content = recentFile.readText()
        assertEquals("content unchanged", "some content without sent entry\n", content)
    }

    @Test
    fun `replace_returns_false_when_kb_does_not_exist`() = runBlocking {
        val repo = newRepo()

        val result = repo.replaceActualSentRecord("nonexistent", "old", "new")
        assertFalse("should return false for nonexistent KB", result)
    }

    @Test
    fun `append_returns_false_when_kb_does_not_exist`() = runBlocking {
        val repo = newRepo()

        val result = repo.appendActualSentRecord("nonexistent", "entry")
        assertFalse("should return false for nonexistent KB", result)
    }
}
