package com.lovebrain.app.viewmodel

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.FileKbArchiveTransfer
import com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort
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
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 职责拆分的行为合同。
 *
 * 建库事务原先写在 KnowledgeBaseActivity 里，靠 `kbCreationInProgress` boolean 与 `onboardingJob`
 * 两套台账共同维护，且只能在真机上验证。迁到 [KnowledgeBaseViewModel] 后本组用例钉住四件事：
 * 1. 事务只有一个 owner——生成中重复点击不会建出第二个库；
 * 2. 画像完整 / 降级 / 建库失败 / 取消四种结果各发各的事件，UI 只按事件选文案；
 * 3. 未配置供应商时不碰 Repository；
 * 4. 导入导出归 ViewModel，导入后强制修正 active，坏包不落库。
 *
 * 2026-10-08 这一族又加了两件事（§12.2 / §12.3 / §2.2 第 4 条）：
 * 5. 切库、改名、删除的**结果**只认"重读回来的真实数据流"——端口上 setActive 与
 *    updateDisplayName 都是 `Unit`，写侧被只读保护或目录已不在时是静默降级的，
 *    只信"调用有没有抛"就会把没落地的改动说成成功；
 * 6. 建库那趟事务写入的目标库 = 发起时就绑定的那一座，不是落笔时才去问的当前库。
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
        repo: KnowledgeBaseCatalogPort,
        deepSeek: DeepSeekRepository
    ) = KnowledgeBaseViewModel(
        ctx, repo, deepSeek,
        FileKbArchiveTransfer(knowledgeRoot = kbRoot, stagingBase = cacheRoot),
        dispatcher
    )

    private fun readyProvider(deepSeek: DeepSeekRepository) {
        every { deepSeek.getActiveTicket() } returns ProviderTicket(
            name = "工单", baseUrl = "https://example.test/v1", model = "deepseek-chat"
        )
        every { deepSeek.getActiveApiKey() } returns "sk-test"
    }

    private fun newRepo(): KnowledgeBaseCatalogPort {
        val repo = mockk<KnowledgeBaseCatalogPort>(relaxed = true)
        // create 回来的是**真库名**（生产侧的写侧契约：返回的那一座就是落盘的那一座）。
        // 用 relaxed mock 交一个 name="" 的假对象，"目标库绑在哪一座"这条判据就什么都测不出来——
        // 2026-10-10 §4 M01 复用上线后，写侧可能把库里已有的那顶初始空壳当目标库回来，
        // 所以这一格必须让回来的名字与提出的一致（复用那一支的反面证据在
        // `a reused initial library is never reclaimed on cancellation` 与真磁盘那一族）。
        coEvery { repo.create(any(), any()) } answers {
            KnowledgeBase(name = firstArg(), displayName = secondArg())
        }
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

    /**
     * 引擎报错 ⇒ **一个目录都不许多造**（§4 场景⑤；2026-10-10 由「降级建库」改成本行为）。
     *
     * 这一格改的是既存判据，不是放松：旧断言（raw="" 照样 create，回报 TemplateOnlyCreated）
     * 正是「首次建库出现两个库」里最难解释的那一支——它在 default 之外又落了一座 13 格全模板的
     * 空库，而用户看到的是"创建成功"。需求原话要求"还没拿到可写内容就别建目录"，
     * 于是这里钉的是：报失败、一次 create 都不调、一格画像都不写、一座库都不删。
     * 跨两条路径的真磁盘形状（旧库字节完全没漂）由 `FirstRunKnowledgeBaseFlowTest` 那族钉。
     */
    @Test
    fun `engine failure creates nothing instead of a half library`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } throws IllegalStateException("401")

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        advanceUntilIdle()

        // 「失败显示失败」（§12.3），而且盘上什么都没发生
        assertEquals(listOf(KbEvent.Creation(KbCreationOutcome.OnboardingCreateFailed)), events)
        coVerify(exactly = 0) { repo.create(any(), any()) }
        coVerify(exactly = 0) { repo.writeFile(any(), any(), any()) }
        coVerify(exactly = 0) { repo.delete(any()) }
    }

    /** 引擎答了话但答的是空：同一条「没有可写内容就不建目录」，这一支不走异常 */
    @Test
    fun `a blank answer creates no library`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } returns "   "

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.Creation(KbCreationOutcome.OnboardingCreateFailed)), events)
        coVerify(exactly = 0) { repo.create(any(), any()) }
    }

    /**
     * 取消落在"目录已落盘、画像还在挂起"这一支：只回收**这一次自造**的那座。
     *
     * 判据是 `created.name == 这一次提出的库名`。写侧复用了盘上那座初始库时回来的名字不是它，
     * 于是 delete 一次都不许调——那正是"以修复重复为理由自动删除现有库"的禁区。
     * 真磁盘那一头的同一支（含"复用时库还在、画像还在"）在 `FirstRunKnowledgeBaseFlowTest`。
     */
    @Test
    fun `a cancellation after the create reclaims exactly the library this run made`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } returns raw("我是我", "她是她", "温度")
        val made = mutableListOf<String>()
        coEvery { repo.create(any(), any()) } answers {
            val requested = firstArg<String>()
            made += requested
            KnowledgeBase(name = requested, displayName = "AI 取的展示名")
        }
        val gate = CompletableDeferred<Unit>()
        // 挂起点放在第二段画像上：第一段真的写完（这才叫"取消落在建库之后"），
        // 而且 mockk 对它已经有一笔记得的调用。
        // 实参位置按端口签名 `writeFile(kbName, relativePath, content)`：路径是**第二个**实参，
        // 第三个是正文。抄错一位（曾写 thirdArg）时这个条件恒假——门不会关，流程一路跑到
        // ProfileCreated，两格红得毫无线索；所以这里另把"门真的关上了"记成可读的实参轨迹，
        // 取消点没钉住时由那条前提当场报出实到路径，而不是让事件断言替仪器背锅。
        val seenPaths = mutableListOf<String>()
        coEvery { repo.writeFile(any(), any(), any()) } coAnswers {
            val relativePath = secondArg<String>()
            seenPaths += relativePath
            if (relativePath == "understand/her.md") gate.await()
        }

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        runCurrent()
        model.cancelOnboarding()
        advanceUntilIdle()

        assertEquals(
            "前提：取消要真的钉在第二段画像上（me.md 已落盘、her.md 挂住、warmth.md 没开始），实到 $seenPaths",
            listOf("understand/me.md", "understand/her.md"), seenPaths
        )
        assertEquals(listOf(KbEvent.Creation(KbCreationOutcome.Cancelled)), events)
        val selfCreated = made.single()
        coVerify(exactly = 1) { repo.delete(selfCreated) }
        coVerify(exactly = 1) { repo.writeFile(selfCreated, "understand/me.md", "我是我") }
        assertTrue("回收的必须是这一次自造的那个库名", selfCreated.startsWith("kb_"))
    }

    /** 反向证人：写侧把目标库换成盘上那座初始库时（回来的名字不是这次提出的），一个字节都不许回收 */
    @Test
    fun `a reused initial library is never reclaimed on cancellation`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        coEvery { deepSeek.generateRaw(any(), any()) } returns raw("我是我", "她是她", "温度")
        // 写侧复用：回来的库名是 default，不是这一次提出的 kb_*
        coEvery { repo.create(any(), any()) } returns KnowledgeBase(name = "default", displayName = "AI 取的展示名")
        val gate = CompletableDeferred<Unit>()
        val seenPaths = mutableListOf<String>()
        coEvery { repo.writeFile(any(), any(), any()) } coAnswers {
            val relativePath = secondArg<String>()
            seenPaths += relativePath
            if (relativePath == "understand/her.md") gate.await()
        }

        val model = vm(fakeContext(), repo, deepSeek)
        val events = subscribe(model)
        model.createKbWithOnboarding(schema())
        runCurrent()
        model.cancelOnboarding()
        advanceUntilIdle()

        assertEquals(
            "前提：取消要真的钉在第二段画像上（me.md 已落盘、her.md 挂住、warmth.md 没开始），实到 $seenPaths",
            listOf("understand/me.md", "understand/her.md"), seenPaths
        )
        assertEquals(listOf(KbEvent.Creation(KbCreationOutcome.Cancelled)), events)
        coVerify(exactly = 0) { repo.delete(any()) }
        coVerify(exactly = 1) { repo.writeFile("default", "understand/me.md", "我是我") }
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

    // ═══════════ 切库 / 改名 / 删除的验收：判据是重读回来的真实数据流 ═══════════

    /**
     * §12.2「点击非活动卡用于切换当前知识库时，应明确反馈『已切换到……』」。
     *
     * 这一格读的是事件里那颗**显示名**：写侧落的是内部库名（目录身份），
     * 而用户要知道的是刚切到了"哪一座"，两者不是同一个东西——事件要是把内部名端出来，
     * 卡片那一行念的会是 kb_b 这种目录名（§12.2「不展示内部文件名」同一判据）。
     */
    @Test
    fun `switching the active library names the library it switched to`() = runTest(dispatcher) {
        val repo = newRepo()
        val mine = KnowledgeBase(name = "kb_a", displayName = "我的她")
        val hers = KnowledgeBase(name = "kb_b", displayName = "小雅的库")
        var activeName = "kb_a"
        coEvery { repo.listAll() } returns listOf(hers, mine)
        coEvery { repo.getActive() } answers { if (activeName == "kb_b") hers else mine }
        coEvery { repo.setActive(any()) } answers { activeName = firstArg() }

        val model = vm(fakeContext(), repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.setActive("kb_b")
        advanceUntilIdle()

        assertEquals(
            "切库成功要当场说清切到了哪一座，实到 $events",
            listOf(KbEvent.ActiveSwitched("小雅的库")), events
        )
        assertEquals("状态里的活动引用换的是那一座的内部名", "kb_b", model.state.value.activeName)
    }

    /**
     * 只读库那一族的真实形状：`setActive` 不抛、也什么都没改（端口上这一员是 Unit，没有失败通道）。
     * 信"没抛 = 成功"就会把一次没落地的切换报成成功，界面下一帧还是旧的那一座。
     */
    @Test
    fun `a switch the store did not land is reported as a failure`() = runTest(dispatcher) {
        val repo = newRepo()
        val mine = KnowledgeBase(name = "kb_a", displayName = "我的她")
        coEvery { repo.listAll() } returns listOf(mine)
        coEvery { repo.getActive() } returns mine
        coEvery { repo.setActive(any()) } returns Unit

        val model = vm(fakeContext(), repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.setActive("kb_b")
        advanceUntilIdle()

        assertEquals("切到了一座没落地的库要说失败，不许沉默", listOf(KbEvent.SwitchFailed), events)
        assertEquals("kb_a", model.state.value.activeName)
    }

    /** 重读本身失败时切换不说话：那一格由页面顶上去的 Error 说，这里再判一次就是替没读到的数据编结论 */
    @Test
    fun `a switch whose re-read failed leaves the verdict to the error cell`() = runTest(dispatcher) {
        val repo = newRepo()
        coEvery { repo.setActive(any()) } returns Unit
        coEvery { repo.listAll() } throws IOException("keystore key cannot decrypt")

        val model = vm(fakeContext(), repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.setActive("kb_b")
        advanceUntilIdle()

        assertTrue("前提：这一趟要真的落在读失败那一格", model.state.value.loadFailed)
        assertEquals(emptyList<KbEvent>(), events)
    }

    @Test
    fun `a rename the store silently refused is reported as a failure`() = runTest(dispatcher) {
        val repo = newRepo()
        val stale = KnowledgeBase(name = "kb_a", displayName = "旧名字")
        coEvery { repo.listAll() } returns listOf(stale)
        coEvery { repo.getActive() } returns stale
        coEvery { repo.updateDisplayName(any(), any()) } returns Unit

        val model = vm(fakeContext(), repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.rename("kb_a", "新名字")
        advanceUntilIdle()

        assertEquals(
            "盘上还是旧名字 = 这一次改名没落进真实数据流，必须报失败，实到 $events",
            listOf(KbEvent.RenameFailed), events
        )
        assertEquals("旧名字", model.state.value.knowledgeBases.single().displayName)
    }

    /** 反向证人：改成了就不许报失败——上面那一格单独存在时，"永远报失败"的坏实现也能全绿 */
    @Test
    fun `a rename that lands is not reported as a failure`() = runTest(dispatcher) {
        val repo = newRepo()
        var display = "旧名字"
        coEvery { repo.updateDisplayName(any(), any()) } answers { display = secondArg() }
        coEvery { repo.listAll() } answers { listOf(KnowledgeBase(name = "kb_a", displayName = display)) }
        coEvery { repo.getActive() } returns null

        val model = vm(fakeContext(), repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.rename("kb_a", "  带空白的名字  ")
        advanceUntilIdle()

        assertEquals("送出去与验收读的是同一份已归一化的名字，实到 $events", emptyList<KbEvent>(), events)
        assertEquals("带空白的名字", model.state.value.knowledgeBases.single().displayName)
    }

    /** 写侧说删成了、清单里却还认得这座库 = 这一删没落到真实数据流上（§12.1「列表没刷新」那一支） */
    @Test
    fun `a delete that left the library in the list is reported as a failure`() = runTest(dispatcher) {
        val repo = newRepo()
        val ghost = KnowledgeBase(name = "kb_a", displayName = "删不掉的库")
        coEvery { repo.delete("kb_a") } returns true
        coEvery { repo.listAll() } returns listOf(ghost)
        coEvery { repo.getActive() } returns ghost

        val model = vm(fakeContext(), repo, mockk(relaxed = true))
        val events = subscribe(model)
        model.delete("kb_a")
        advanceUntilIdle()

        assertEquals(listOf(KbEvent.DeleteFailed), events)
    }

    /**
     * §12.3「生成或写入在开始时绑定目标库，切库后迟到结果不能写入新对象」。
     *
     * 这里量的是建库那趟事务：库名在发起生成**之前**就定了，画像三段与阶段写的全是它。
     * 判据是"每一个写入目标 = 这一次 create 拿到名字"，而盘上另有一座一直在用的库当对照——
     * 把写入改成"落笔时才去问当前库"的坏实现，写出去的就是那座在用的旧库，这一格当场红。
     */
    @Test
    fun `a late generation result writes into the library it bound at the start`() = runTest(dispatcher) {
        val repo = newRepo()
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        readyProvider(deepSeek)
        val gate = CompletableDeferred<Unit>()
        coEvery { deepSeek.generateRaw(any(), any()) } coAnswers {
            gate.await()
            raw("我是我", "她是她", "温度")
        }
        val inUse = KnowledgeBase(name = "kb_in_use", displayName = "在用的旧库")
        var created: String? = null
        coEvery { repo.create(any(), any()) } answers {
            created = firstArg<String>()
            KnowledgeBase(name = firstArg(), displayName = "AI 取的展示名")
        }
        coEvery { repo.listAll() } returns listOf(inUse)
        coEvery { repo.getActive() } returns inUse
        val writeTargets = mutableListOf<String>()
        val stageTargets = mutableListOf<String>()
        coEvery { repo.writeFile(any(), any(), any()) } coAnswers {
            writeTargets += firstArg<String>()
            Unit
        }
        coEvery { repo.updateStage(any(), any()) } coAnswers {
            stageTargets += firstArg<String>()
            Unit
        }

        val model = vm(fakeContext(), repo, deepSeek)
        subscribe(model)
        model.createKbWithOnboarding(schema())
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        val bound = created
        assertTrue(
            "前提：这一趟建出的库不是盘上那座在用的（否则这条判据分辨不了写给了谁），实到 $bound",
            bound != null && bound != "kb_in_use"
        )
        assertTrue(
            "迟到的画像每一段都必须写回开始时绑定的那一座（$bound），实到 $writeTargets",
            writeTargets.isNotEmpty() && writeTargets.all { it == bound }
        )
        assertEquals("阶段写的也是同一座库", listOf(bound), stageTargets)
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
