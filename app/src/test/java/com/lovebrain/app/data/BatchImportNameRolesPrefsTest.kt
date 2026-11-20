package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.domain.port.FakeSharedPreferences
import com.lovebrain.app.model.ChatMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批量导入昵称 → 她/我 归属表（`batch_import_name_roles`）的落盘合同。
 *
 * 打法沿用 `ProductionSettingsStoreContractTest` 那一族：真 [SecurePrefs] + JVM 假盘
 * （Keystore 不可用走降级分支），判据只有两条：
 * · **往返与跨重启**——写进去的归属表原样读回，换一颗新 SecurePrefs（同一份盘）还在；
 * · **脏值整体 fail-closed 成空表**——非 JSON、角色不是 HER/ME（含 IDEA）、值不是字符串、
 *   空昵称，任何一种都不许猜着读：宁可让用户回浮层重新显式指定，绝不把对方的话标成我。
 */
class BatchImportNameRolesPrefsTest {

    private companion object {
        const val KEY = "batch_import_name_roles"
    }

    private fun build(fake: FakeSharedPreferences): SecurePrefs {
        val ctx = mockk<Context>()
        every { ctx.getSharedPreferences(any(), any()) } returns fake
        return SecurePrefs(ctx)
    }

    @Test
    fun `an empty store reports no known roles`() {
        val prefs = build(FakeSharedPreferences())
        assertEquals("没写过归属表 = 空表（自动拆分一律回浮层显式指定）", emptyMap<String, ChatMessage.Role>(), prefs.batchImportNameRoles)
    }

    @Test
    fun `roles round-trip and survive a restart on the same disk`() {
        val fake = FakeSharedPreferences()
        val prefs = build(fake)
        val roles = mapOf("小美" to ChatMessage.Role.HER, "我" to ChatMessage.Role.ME)

        prefs.batchImportNameRoles = roles

        assertEquals("写进去的归属表原样读回", roles, prefs.batchImportNameRoles)
        assertTrue("归属表要真写进盘，不是内存假持久", fake.contains(KEY))
        assertEquals("换一颗新 SecurePrefs（重启）读同一份盘还在", roles, build(fake).batchImportNameRoles)
    }

    @Test
    fun `a later write replaces the earlier map`() {
        val prefs = build(FakeSharedPreferences())
        prefs.batchImportNameRoles = mapOf("小美" to ChatMessage.Role.HER)
        prefs.batchImportNameRoles = mapOf("我" to ChatMessage.Role.ME)
        assertEquals(mapOf("我" to ChatMessage.Role.ME), prefs.batchImportNameRoles)
    }

    @Test
    fun `an empty map round-trips as empty`() {
        val fake = FakeSharedPreferences()
        val prefs = build(fake)
        prefs.batchImportNameRoles = emptyMap()
        assertEquals(emptyMap<String, ChatMessage.Role>(), prefs.batchImportNameRoles)
        assertEquals(emptyMap<String, ChatMessage.Role>(), build(fake).batchImportNameRoles)
    }

    @Test
    fun `dirty values fail closed to the empty map as a whole`() {
        val dirty = listOf(
            "not json at all",
            "{\"小美\":\"HER\",\"我\":\"ALIEN\"}",   // 未知角色：不许猜，也不许只读半张表
            "{\"小美\":\"IDEA\"}",                  // 备注不是消息角色
            "{\"小美\":123}",                       // 值不是字符串
            "{\"小美\":{\"role\":\"HER\"}}",        // 值不是原语
            "[\"小美\",\"HER\"]",                   // 不是对象
            "{\"\":\"HER\"}"                        // 空昵称
        )
        for (raw in dirty) {
            val fake = FakeSharedPreferences().apply { edit().putString(KEY, raw).apply() }
            assertEquals(
                "脏值「$raw」必须整体读成空表——读半张或猜一个角色都会静默错标",
                emptyMap<String, ChatMessage.Role>(),
                build(fake).batchImportNameRoles
            )
        }
    }

    @Test
    fun `a dirty map can be healed by an explicit write`() {
        val fake = FakeSharedPreferences().apply { edit().putString(KEY, "garbage").apply() }
        val prefs = build(fake)
        assertEquals(emptyMap<String, ChatMessage.Role>(), prefs.batchImportNameRoles)

        prefs.batchImportNameRoles = mapOf("小美" to ChatMessage.Role.HER)

        assertEquals("fail-closed 不是永久锁死：用户重新显式指定后照常落盘读回",
            mapOf("小美" to ChatMessage.Role.HER), prefs.batchImportNameRoles)
    }
}