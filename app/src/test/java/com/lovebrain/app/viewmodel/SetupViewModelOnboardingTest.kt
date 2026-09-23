package com.lovebrain.app.viewmodel

import android.content.Context
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ProviderTicket
import io.mockk.every
import io.mockk.mockk
import io.mockk.justRun
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 引导可见性判定（S2-05：从 SetupActivity 收进来的那段数据逻辑）。
 *
 * 原先 SetupActivity 自己读 filesDir、自己拼四个条件、还直接写
 * securePrefs.hasCompletedOnboarding —— 判定与写入都散在 UI 生命周期里，
 * 在 JVM 上根本调不到。现在 SetupViewModel 是唯一持有者，本组用例钉住五件事：
 * 1. 已完成就不再判、也不再写；
 * 2. 新人（什么都没配）必须看到引导；
 * 3. 老用户要**顺手补上完成标记**，且只补这一次判定；
 * 4. "本机有没有知识库"这个文件系统条件真的参与判定；
 * 5. 没给 Context 时按"没有知识库"处理，而不是崩。
 */
class SetupViewModelOnboardingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun prefs(
        completed: Boolean = false,
        tickets: List<ProviderTicket> = emptyList(),
        activeTicketId: String? = null,
        totalGenerateCount: Int = 0
    ): SecurePrefs {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.hasCompletedOnboarding } returns completed
        every { prefs.getWorkerTickets() } returns tickets
        every { prefs.activeTicketId } returns activeTicketId
        every { prefs.totalGenerateCount } returns totalGenerateCount
        justRun { prefs.hasCompletedOnboarding = any() }
        return prefs
    }

    private fun vm(
        prefs: SecurePrefs,
        context: Context? = null
    ) = SetupViewModel(prefs, mockk<DeepSeekRepository>(relaxed = true), null, context)

    private fun contextWithKnowledge(hasKb: Boolean): Context {
        val root = File(tmp.root, "files").apply { mkdirs() }
        val knowledge = File(root, "knowledge").apply { mkdirs() }
        if (hasKb) File(knowledge, "kb_demo").apply { mkdirs() } else knowledge.delete()
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns root
        return ctx
    }

    private fun ticket() = ProviderTicket(
        name = "工单", baseUrl = "https://example.test/v1", model = "deepseek-chat"
    )

    @Test
    fun `already completed onboarding shows nothing and writes nothing`() {
        val prefs = prefs(completed = true, tickets = listOf(ticket()))
        assertFalse(vm(prefs).shouldShowOnboarding())
        verify(exactly = 0) { prefs.hasCompletedOnboarding = true }
    }

    @Test
    fun `brand new user sees onboarding and the flag stays untouched`() {
        val prefs = prefs(completed = false)
        assertTrue(vm(prefs).shouldShowOnboarding())
        verify(exactly = 0) { prefs.hasCompletedOnboarding = any() }
    }

    @Test
    fun `existing user with tickets skips onboarding and the flag gets backfilled`() {
        val prefs = prefs(completed = false, tickets = listOf(ticket()))
        assertFalse(vm(prefs).shouldShowOnboarding())
        verify(exactly = 1) { prefs.hasCompletedOnboarding = true }
    }

    @Test
    fun `generate count alone marks the user as existing`() {
        val prefs = prefs(completed = false, totalGenerateCount = 3)
        assertFalse(vm(prefs).shouldShowOnboarding())
        verify(exactly = 1) { prefs.hasCompletedOnboarding = true }
    }

    @Test
    fun `a knowledge base on disk counts even with no provider configured`() {
        val prefs = prefs(completed = false)
        assertFalse(vm(prefs, contextWithKnowledge(hasKb = true)).shouldShowOnboarding())
        verify(exactly = 1) { prefs.hasCompletedOnboarding = true }
    }

    @Test
    fun `empty knowledge dir does not fake an existing user`() {
        val prefs = prefs(completed = false)
        assertTrue(vm(prefs, contextWithKnowledge(hasKb = false)).shouldShowOnboarding())
    }

    @Test
    fun `missing app context degrades to no-knowledge-base instead of crashing`() {
        val prefs = prefs(completed = false)
        assertTrue(vm(prefs, context = null).shouldShowOnboarding())
    }

    @Test
    fun `missing app context does not erase the other three conditions`() {
        // 这条是给"缺 Context 就 return false"那个写法准备的：
        // 查不了磁盘只该让「有没有知识库」这一条按无处理，
        // 已配工单/已有激活供应商/生成过这三条照样能把老用户认出来。
        assertTrue(
            !vm(prefs(tickets = listOf(ticket())), context = null).shouldShowOnboarding()
        )
        assertTrue(
            !vm(prefs(activeTicketId = "t-1"), context = null).shouldShowOnboarding()
        )
        assertTrue(
            !vm(prefs(totalGenerateCount = 1), context = null).shouldShowOnboarding()
        )
    }

    @Test
    fun `completeOnboarding writes the flag exactly once`() {
        val prefs = prefs(completed = false)
        vm(prefs).completeOnboarding()
        verify(exactly = 1) { prefs.hasCompletedOnboarding = true }
    }
}
