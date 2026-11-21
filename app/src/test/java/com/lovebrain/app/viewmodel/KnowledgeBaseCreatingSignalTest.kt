package com.lovebrain.app.viewmodel

import android.content.Context
import android.content.res.AssetManager
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.FileKbArchiveTransfer
import com.lovebrain.app.domain.OnboardingSchema
import com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.ProviderTicket
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 向导读的那一格 `creating`：它必须**开了会落、关了会回**。
 *
 * 这一格的三个读数正好对应旧 bug 的三条漏网路径：未配模型（根没开建）、
 * 建库在飞（开了没落）、建库失败（落了没回）。任何一条漏了，向导就会永远停在"生成中"，
 * 用户刚答的五步问卷再也交不出去。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeBaseCreatingSignalTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var filesRoot: File
    private lateinit var kbRoot: File
    private lateinit var cacheRoot: File

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        filesRoot = File(tmp.root, "files").apply { mkdirs() }
        kbRoot = File(filesRoot, "knowledge").apply { mkdirs() }
        cacheRoot = File(tmp.root, "cache").apply { mkdirs() }
    }

    private fun fakeContext(): Context {
        val assets = mockk<AssetManager>()
        every { assets.open(any()) } answers { "ONBOARDING-SYSTEM".byteInputStream() }
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.assets } returns assets
        every { ctx.filesDir } returns filesRoot
        every { ctx.cacheDir } returns cacheRoot
        every { ctx.contentResolver } returns mockk(relaxed = true)
        return ctx
    }

    private fun vm(ctx: Context, repo: KnowledgeBaseCatalogPort, deepSeek: DeepSeekRepository) =
        KnowledgeBaseViewModel(
            ctx, repo, deepSeek,
            FileKbArchiveTransfer(knowledgeRoot = kbRoot, stagingBase = cacheRoot),
            dispatcher
        )

    private fun newRepo(): KnowledgeBaseCatalogPort {
        val repo = mockk<KnowledgeBaseCatalogPort>(relaxed = true)
        coEvery { repo.create(any(), any()) } answers {
            KnowledgeBase(name = firstArg(), displayName = secondArg())
        }
        coEvery { repo.listAll() } returns emptyList()
        coEvery { repo.getActive() } returns null
        return repo
    }

    private fun readyProvider(deepSeek: DeepSeekRepository) {
        every { deepSeek.getActiveTicket() } returns ProviderTicket(
            name = "工单", baseUrl = "https://example.test/v1", model = "deepseek-chat"
        )
        every { deepSeek.getActiveApiKey() } returns "sk-test"
    }

    private fun schema() = OnboardingSchema(
        stage = "dating",
        meta = OnboardingSchema.Meta(total_answered = 5, path = listOf("A")),
        names = OnboardingSchema.Names(self = "我", counterpart = "小雅"),
        tags = emptyList(),
        profile = OnboardingSchema.Profile("", "", "", ""),
        redline_triggered = false,
        system_directive = ""
    )

    private fun raw(me: String, her: String, warmth: String) =
        "===DISPLAY===\nAI 取的展示名\n===STAGE===\nconflict\n===ME===\n$me\n===HER===\n$her\n===WARMTH===\n$warmth"

    private fun TestScope.subscribe(vm: KnowledgeBaseViewModel): MutableList<KbEvent> {
        val events = mutableListOf<KbEvent>()
        backgroundScope.launch(dispatcher) { vm.events.collect { events += it } }
        runCurrent()
        return events
    }

    @Test
    fun `the signal is raised while a creation is in flight and lowered when it settles`() =
        runTest(dispatcher) {
            val repo = newRepo()
            val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
            readyProvider(deepSeek)
            val gate = CompletableDeferred<Unit>()
            coEvery { deepSeek.generateRaw(any(), any()) } coAnswers {
                gate.await()
                raw("我是我", "她是她", "温度")
            }

            val model = vm(fakeContext(), repo, deepSeek)
            subscribe(model)
            assertFalse("没开建时向导不该是生成中", model.creating.value)

            model.createKbWithOnboarding(schema())
            runCurrent()
            assertTrue("开建之后向导那一格必须为真（否则按钮还点得动）", model.creating.value)

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse("建完之后必须回落（否则向导永远生成中）", model.creating.value)
        }

    @Test
    fun `an unconfigured provider never raises the creation signal`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        every { deepSeek.getActiveTicket() } returns null
        every { deepSeek.getActiveApiKey() } returns null

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        advanceUntilIdle()

        assertFalse(
            "未配模型时根没开建：向导必须留在可答状态（旧实现本地置真后永远回不来）",
            model.creating.value
        )
        assertEquals(
            listOf(KbEvent.Creation(KbCreationOutcome.ProviderNotConfigured)), events
        )
    }

    @Test
    fun `cancelling a creation lowers the signal on the spot`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } coAnswers {
            CompletableDeferred<Unit>().await()
            "never returns"
        }

        val model = vm(fakeContext(), repo, deepSeek)
        subscribe(model)
        model.createKbWithOnboarding(schema())
        runCurrent()
        assertTrue("取消之前向导必须处于生成中", model.creating.value)

        model.cancelOnboarding()

        // 取消是同步落下的一格：向导要当场解锁，不必等协程把取消异常走完
        assertFalse("取消之后向导必须立刻回落", model.creating.value)
        advanceUntilIdle()
        assertFalse("取消路径走完之后仍保持回落，不许被收尾再抬起来", model.creating.value)
    }

    @Test
    fun `a failed empty creation lowers the signal and reports the failure`() = runTest(dispatcher) {
        val repo = newRepo()
        coEvery { repo.create(any(), any()) } throws IllegalStateException("disk full")

        val model = vm(fakeContext(), repo, mockk<DeepSeekRepository>(relaxed = true))
        val events = subscribe(model)
        model.createEmptyKb()
        advanceUntilIdle()

        assertFalse("失败之后必须回落", model.creating.value)
        assertEquals(listOf(KbEvent.Creation(KbCreationOutcome.EmptyCreateFailed)), events)
    }
}
