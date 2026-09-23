package com.lovebrain.app.viewmodel

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.domain.OnboardingSchema
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.ProviderTicket
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S2-05 职责拆分的行为合同。
 *
 * 建库事务原先写在 KnowledgeBaseActivity 里，靠 `kbCreationInProgress` boolean 与 `onboardingJob`
 * 两套台账共同维护，且只能在真机上验证。迁到 [KnowledgeBaseViewModel] 后本组用例钉住四件事：
 * 1. 事务只有一个 owner——生成中重复点击不会建出第二个库；
 * 2. 画像完整 / 降级 / 建库失败 / 取消四种结果各发各的事件，UI 只按事件选文案；
 * 3. 未配置供应商时不碰 Repository；
 * 4. 导入导出归 ViewModel，导入后强制修正 active，坏包不落库。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeBaseViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 同一个调度器充当 Main、IO 与 runTest 的调度器：全程虚拟时间，无线程竞态 */
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeContext(assetText: String = "ONBOARDING-SYSTEM"): Context {
        val assets = mockk<AssetManager>()
        every { assets.open(any()) } answers { assetText.byteInputStream() }
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.assets } returns assets
        every { ctx.filesDir } returns filesRoot
        every { ctx.cacheDir } returns cacheRoot
        every { ctx.contentResolver } returns mockk(relaxed = true)
        return ctx
    }

    private fun vm(
        ctx: Context,
        repo: KnowledgeRepository,
        deepSeek: DeepSeekRepository
    ) = KnowledgeBaseViewModel(ctx, repo, deepSeek, dispatcher)

    private fun readyProvider(deepSeek: DeepSeekRepository) {
        every { deepSeek.getActiveTicket() } returns ProviderTicket(
            name = "工单", baseUrl = "https://example.test/v1", model = "deepseek-chat"
        )
        every { deepSeek.getActiveApiKey() } returns "sk-test"
    }

    private fun newRepo(): KnowledgeRepository {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { repo.create(any(), any()) } returns mockk<KnowledgeBase>(relaxed = true)
        coEvery { repo.listAll() } returns emptyList()
        coEvery { repo.getActive() } returns null
        return repo
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

    /** 收集一次性事件：SharedFlow 无 replay，必须先把订阅者挂上 */
    private fun TestScope.subscribe(vm: KnowledgeBaseViewModel): MutableList<KbEvent> {
        val events = mutableListOf<KbEvent>()
        backgroundScope.launch(dispatcher) { vm.events.collect { events += it } }
        runCurrent()
        return events
    }

    @Test
    fun `complete profile writes all three sections and reports ProfileCreated`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } returns raw("我是我", "她是她", "温度")

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.Creation(KbCreationOutcome.ProfileCreated)), events)
        coVerify(exactly = 1) { repo.writeFile(any(), "understand/me.md", "我是我") }
        coVerify(exactly = 1) { repo.writeFile(any(), "understand/her.md", "她是她") }
        coVerify(exactly = 1) { repo.writeFile(any(), "understand/warmth.md", "温度") }
        coVerify(exactly = 1) { repo.updateStage(any(), "conflict") }
    }

    @Test
    fun `partial profile downgrades and skips empty sections`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } returns raw("只有我", "", "")

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.Creation(KbCreationOutcome.TemplateOnlyCreated)), events)
        coVerify(exactly = 1) { repo.writeFile(any(), "understand/me.md", "只有我") }
        coVerify(exactly = 0) { repo.writeFile(any(), "understand/her.md", any()) }
        coVerify(exactly = 0) { repo.writeFile(any(), "understand/warmth.md", any()) }
    }

    @Test
    fun `second click while generating cannot start a second creation`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        val gate = CompletableDeferred<Unit>()
        coEvery { deepSeek.generateRaw(any(), any()) } coAnswers {
            gate.await()
            raw("a", "b", "c")
        }
        var creates = 0
        coEvery { repo.create(any(), any()) } answers {
            creates++
            mockk<KnowledgeBase>(relaxed = true)
        }

        val model = vm(fakeContext(), repo, deepSeek)
        subscribe(model)

        model.createKbWithOnboarding(schema())
        runCurrent()
        assertTrue("生成中事务必须处于占用态", model.isCreating)
        model.createKbWithOnboarding(schema())
        model.createEmptyKb()
        runCurrent()

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("重复点击只能建一个库", 1, creates)
        assertFalse("事务结束后 owner 必须释放", model.isCreating)
    }

    @Test
    fun `cancelling generation emits Cancelled and writes nothing`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } coAnswers {
            // 永不完成：协程只能被 cancel 叫醒，用来验证「取消不落盘」
            CompletableDeferred<Unit>().await()
            "never returned"
        }

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)

        model.createKbWithOnboarding(schema())
        runCurrent()
        model.cancelOnboarding()
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.Creation(KbCreationOutcome.Cancelled)), events)
        coVerify(exactly = 0) { repo.create(any(), any()) }
        coVerify(exactly = 0) { repo.writeFile(any(), any(), any()) }
    }

    @Test
    fun `missing provider emits ProviderNotConfigured without touching the repository`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        every { deepSeek.getActiveTicket() } returns null
        every { deepSeek.getActiveApiKey() } returns null

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        advanceUntilIdle()

        assertEquals(
            listOf(KbEvent.Creation(KbCreationOutcome.ProviderNotConfigured)),
            events
        )
        coVerify(exactly = 0) { repo.create(any(), any()) }
    }

    @Test
    fun `provider model without key is not treated as ready`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        every { deepSeek.getActiveTicket() } returns ProviderTicket(
            name = "工单", baseUrl = "https://example.test/v1", model = "deepseek-chat"
        )
        every { deepSeek.getActiveApiKey() } returns null

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        advanceUntilIdle()

        assertEquals(
            listOf(KbEvent.Creation(KbCreationOutcome.ProviderNotConfigured)),
            events
        )
    }

    @Test
    fun `engine failure degrades to a template library, never a fake success`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } throws IllegalStateException("401")

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        advanceUntilIdle()

        // 引擎报错 → 空输出 → 一段都解析不出来 → 只能报「降级建库」，不能报画像成功
        assertEquals(
            listOf(KbEvent.Creation(KbCreationOutcome.TemplateOnlyCreated)),
            events
        )
        coVerify(exactly = 0) { repo.writeFile(any(), any(), any()) }
    }

    @Test
    fun `create failure distinguishes the empty and onboarding paths`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        coEvery { repo.create(any(), any()) } throws IllegalStateException("disk full")

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)

        model.createEmptyKb()
        advanceUntilIdle()
        assertEquals(listOf(KbEvent.Creation(KbCreationOutcome.EmptyCreateFailed)), events)

        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } returns raw("a", "b", "c")
        model.createKbWithOnboarding(schema())
        advanceUntilIdle()

        assertEquals(
            listOf(
                KbEvent.Creation(KbCreationOutcome.EmptyCreateFailed),
                KbEvent.Creation(KbCreationOutcome.OnboardingCreateFailed)
            ),
            events
        )
    }

    @Test
    fun `generated kb names stay unique across batches`() = runTest(dispatcher) {
        val repo = newRepo()
        val names = mutableListOf<String>()
        coEvery { repo.create(capture(names), any()) } returns mockk(relaxed = true)

        val model = vm(fakeContext(), repo, mockk(relaxed = true))
        subscribe(model)
        repeat(5) {
            model.createEmptyKb()
            advanceUntilIdle()
        }

        assertEquals(5, names.size)
        assertTrue(names.all { it.startsWith("kb_") })
        assertEquals("建库名不得重复", 5, names.distinct().size)
    }

    @Test
    fun `delete failure surfaces an event instead of an empty branch`() = runTest(dispatcher) {
        val repo = newRepo()
        coEvery { repo.delete("kb_a") } returns false

        val model = vm(fakeContext(), repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.delete("kb_a")
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.DeleteFailed), events)
    }

    @Test
    fun `delete success refreshes the list without an event`() = runTest(dispatcher) {
        val repo = newRepo()
        val kept = KnowledgeBase(name = "kb_b", displayName = "留下的库")
        coEvery { repo.delete("kb_a") } returns true
        coEvery { repo.listAll() } returns listOf(kept)

        val model = vm(fakeContext(), repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.delete("kb_a")
        advanceUntilIdle()

        assertEquals(emptyList<KbEvent>(), events)
        assertEquals(listOf(kept), model.state.value.knowledgeBases)
    }

    @Test
    fun `import moves the archive in and repairs the active kb`() = runTest(dispatcher) {
        val repo = newRepo()
        val ctx = fakeContext()
        val source = mockk<Uri>()
        every { ctx.contentResolver.openInputStream(source) } returns
            ByteArrayInputStream(zipWithMeta("kb_demo"))
        val imported = KnowledgeBase(name = "kb_demo", displayName = "小雅的库")
        coEvery { repo.listAll() } returns listOf(imported)
        coEvery { repo.getActive() } returns imported

        val model = vm(ctx, repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.import(source)
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.Imported), events)
        assertTrue(File(kbRoot, "kb_demo/kb.json").isFile)
        coVerify(exactly = 1) { repo.setActive("kb_demo") }
        assertEquals("kb_demo", model.state.value.activeName)
        assertEquals(1, model.state.value.knowledgeBases.size)
    }

    @Test
    fun `import of a broken archive reports failure and writes nothing`() = runTest(dispatcher) {
        val repo = newRepo()
        val ctx = fakeContext()
        val source = mockk<Uri>()
        val bogus = zipOf("kb_demo/" to null, "kb_demo/notes.md" to "没有 kb.json")
        every { ctx.contentResolver.openInputStream(source) } returns ByteArrayInputStream(bogus)

        val model = vm(ctx, repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.import(source)
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.ImportFailed), events)
        assertFalse(File(kbRoot, "kb_demo").exists())
        coVerify(exactly = 0) { repo.setActive(any()) }
    }

    @Test
    fun `export writes the kb folder as a zip`() = runTest(dispatcher) {
        val repo = newRepo()
        val ctx = fakeContext()
        val target = mockk<Uri>()
        val out = ByteArrayOutputStream()
        every { ctx.contentResolver.openOutputStream(target) } returns out
        val folder = File(ctx.filesDir, "knowledge/kb_demo").apply { mkdirs() }
        File(folder, "kb.json").writeText("""{"name":"kb_demo"}""")
        File(folder, "me.md").writeText("我")

        val model = vm(ctx, repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.export("kb_demo", target)
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.Exported), events)
        assertEquals(
            listOf("kb_demo/kb.json", "kb_demo/me.md"),
            entriesOf(out.toByteArray()).sorted()
        )
    }

    @Test
    fun `export of a missing kb folder reports failure`() = runTest(dispatcher) {
        val repo = newRepo()
        val ctx = fakeContext()
        val target = mockk<Uri>()
        every { ctx.contentResolver.openOutputStream(target) } returns ByteArrayOutputStream()

        val model = vm(ctx, repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.export("kb_missing", target)
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.ExportFailed), events)
    }

    // ═══════════ zip 夹具 ═══════════

    private fun zipOf(vararg entries: Pair<String, String?>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                if (content != null) zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipWithMeta(name: String): ByteArray = zipOf(
        "$name/" to null,
        "$name/kb.json" to """{"name":"$name","displayName":"小雅的库"}""",
        "$name/understand/me.md" to "我习惯先讲道理"
    )

    private fun entriesOf(bytes: ByteArray): List<String> {
        val zis = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes))
        val acc = mutableListOf<String>()
        var entry = zis.nextEntry
        while (entry != null) {
            acc += entry.name
            entry = zis.nextEntry
        }
        return acc
    }
}
