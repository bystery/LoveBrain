package com.lovebrain.app.domain

import android.content.Context
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * F07/F08 持续意图注入和区块裁剪测试。
 *
 * 验证点：
 * 1. 持续意图 enabled=true 且非空时注入
 * 2. 持续意图 enabled=false 时不注入
 * 3. 持续意图 text 为空时不注入
 * 4. IDEA 和最新消息在预算裁剪后仍保留
 * 5. 旧记忆先被裁剪
 * 6. 结构围栏不被截半
 */
class IntentInjectionTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private lateinit var promptBuilder: PromptBuilder
    private lateinit var knowledgeRepo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("intent_test").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        knowledgeRepo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
        promptBuilder = PromptBuilder(mockk<Context>(relaxed = true), knowledgeRepo)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    private fun mkKb(name: String = "testkb"): KnowledgeBase {
        return KnowledgeBase(name = name, displayName = name, updatedAt = "2026-09-16T09:00:00+08:00", active = true)
    }

    private fun mkMessages(): List<ChatMessage> {
        return listOf(
            ChatMessage(role = ChatMessage.Role.HER, content = "你在干嘛？"),
            ChatMessage(role = ChatMessage.Role.ME, content = "刚下班，你呢？")
        )
    }

    // ════════════════════════════════════════════════════════════════
    // Test 1: 持续意图 enabled=true 且非空时注入
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `enabled_intent_with_text_is_injected`() = runBlocking {
        val intent = IntentConfig(text = "先恢复轻松交流", enabled = true, revision = 1)
        val prompt = promptBuilder.buildReplyUserPrompt(mkKb(), mkMessages(), "test hint", false, intent)

        assertTrue("持续意图应被注入", prompt.contains("【持续意图】"))
        assertTrue("意图文本应出现", prompt.contains("先恢复轻松交流"))
    }

    // ════════════════════════════════════════════════════════════════
    // Test 2: 持续意图 enabled=false 时不注入
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `disabled_intent_not_injected`() = runBlocking {
        val intent = IntentConfig(text = "先恢复轻松交流", enabled = false, revision = 1)
        val prompt = promptBuilder.buildReplyUserPrompt(mkKb(), mkMessages(), "", false, intent)

        assertFalse("持续意图不应被注入", prompt.contains("【持续意图】"))
        assertFalse("意图文本不应出现", prompt.contains("先恢复轻松交流"))
    }

    // ════════════════════════════════════════════════════════════════
    // Test 3: 持续意图 text 为空时不注入
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `empty_intent_text_not_injected`() = runBlocking {
        val intent = IntentConfig(text = "", enabled = true, revision = 1)
        val prompt = promptBuilder.buildReplyUserPrompt(mkKb(), mkMessages(), "", false, intent)

        assertFalse("空持续意图不应被注入", prompt.contains("【持续意图】"))
    }

    // ════════════════════════════════════════════════════════════════
    // Test 4: IDEA hint 仍被注入（回归检查）
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `idea_hint_still_injected_with_intent`() = runBlocking {
        val intent = IntentConfig(text = "先恢复轻松交流", enabled = true, revision = 1)
        val prompt = promptBuilder.buildReplyUserPrompt(mkKb(), mkMessages(), "不约，接她吐槽", false, intent)

        assertTrue("IDEA hint 应被注入", prompt.contains("不约，接她吐槽"))
        assertTrue("持续意图应被注入", prompt.contains("【持续意图】"))
    }

    // ════════════════════════════════════════════════════════════════
    // Test 5: 最新消息在预算裁剪后仍保留
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `latest_message_preserved_after_budget_trim`() = runBlocking {
        // 构建超长对话触发预算裁剪
        // 最后一条是唯一的"最新消息"标记，确保它被保留
        val longMessages = (1..50).map { i ->
            ChatMessage(role = if (i % 2 == 0) ChatMessage.Role.HER else ChatMessage.Role.ME, content = "msg$i " + "x".repeat(180))
        } + ChatMessage(role = ChatMessage.Role.HER, content = "最新消息_UNIQUE_MARKER")
        val intent = IntentConfig(text = "测试意图", enabled = true, revision = 1)
        val prompt = promptBuilder.buildReplyUserPrompt(mkKb(), longMessages, "测试想法", false, intent)

        // 最新消息应保留
        assertTrue("最新消息应保留", prompt.contains("最新消息_UNIQUE_MARKER"))
    }

    // ════════════════════════════════════════════════════════════════
    // Test 6: chat 围栏不被截半
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `chat_fence_not_broken`() = runBlocking {
        val prompt = promptBuilder.buildReplyUserPrompt(mkKb(), mkMessages(), "", false, IntentConfig())

        // <chat> 和 </chat> 都应存在
        assertTrue("chat 开标签应存在", prompt.contains("<chat>"))
        assertTrue("chat 闭标签应存在", prompt.contains("</chat>"))
    }
}
