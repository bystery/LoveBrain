package com.lovebrain.app.viewmodel

import android.content.Context
import android.content.res.AssetManager
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.FileKbArchiveTransfer
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.OnboardingSchema
import com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.ProviderTicket
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * **跨两条调用路径的首次建库回归**（§4 活台账 M01/M02，本轮唯一 P0）。
 *
 * 被测的不是某个函数，而是那一条用户流程：
 * `LoveBrainViewModel.init` 里那一句 `knowledgeRepo.ensureInitialKnowledgeBase()`（路径一，
 * 本类用同一个仓库方法原样跑，不去动那个 VM——它在别人的辖区里，那一格由主线程处置）→
 * 管理页的首次问卷完成 / 跳过 / 取消 / 失败（路径二，由真 [KnowledgeBaseViewModel] 驱动，
 * 落库走真 [KnowledgeRepository] 到真磁盘）→ 重启 App（换一个新的仓库实例再跑一次路径一）。
 *
 * 为什么必须有这一族，而 `EnsureInitialKnowledgeBaseTest` 那七格再加几个幂等格不够：
 * 那两个现场（首次问卷出两座库、跳过问卷也出两座库）**只有跨过两条路径的边界才看得见**——
 * 路径一单看永远只造一座，路径二单看也永远只造一座，红只发生在"路径二把路径一留下的那顶
 * 空壳当目标库"那一步上。用户原话：「增加一组真正跨两条调用路径的回归测试，比继续为
 * `ensureInitialKnowledgeBase()` 单独增加几个幂等测试更有价值」。
 *
 * 形状照 `EnsureInitialKnowledgeBaseTest` 那一族：临时目录 + 真仓库 + mockk 的 SecurePrefs
 * （`activeKbName` 用一对外科读写口子，于是"改活动库"的真实通道也被钉住）。
 * 与那一族只有一处差别：`ctx.assets` 接的是 test classpath 上**同一份** schema 资产
 * （照 `KnowledgeSeedWriteBytesBaselineTest` 的接线），因此"新建"seed 出的是真模板而不是空串——
 * 「画像三段非空 = 有真实用户内容」这条判据在本类里是有牙的。
 *
 * ⚠ 时间一律真实：本类不用虚拟时间调度器当同步点（真磁盘那几跳在别的线程上，虚拟时间会抢在它们
 * 前面收场，把没跑完的流程读成跑完了）。Main 用 `UnconfinedTestDispatcher`（就地执行，不需要泵），
 * 同步点是"先挂上事件订阅者、再用真实超时等这一次流程交回结果"。等不到就是红——
 * 「没结果」与「结果为成功」在 §12.3 那两句里是两格，不许混。
 *
 * 每格头注写的是**喂成什么坏样子这一格会红**（静态变异自证；本轮未跑构建，待主线程编译）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunKnowledgeBaseFlowTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** 就地执行的 Main：本族不等虚拟时间，同步点全是真磁盘读数与真事件 */
    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var root: File
    private lateinit var cacheRoot: File
    private lateinit var ctx: Context
    private lateinit var appScope: CoroutineScope
    private lateinit var collectScope: CoroutineScope
    private var activeKbName: String = ""

    @Before
    fun setUp() {
        // viewModelScope 要 Main。用 Unconfined 那一族 TestDispatcher：任务就地执行、不需要谁去泵
        // 虚拟时间——本族的同步点全在"真磁盘 + 真事件"上（见类头那条 ⚠）。
        Dispatchers.setMain(mainDispatcher)
        root = Files.createTempDirectory("first_run_flow").toFile()
        cacheRoot = Files.createTempDirectory("first_run_cache").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // 收集器走 Unconfined：`vm.events` 是 replay=0 的 SharedFlow，订阅者没挂上就触发建库，
        // 事件会掉进无人接收的缓冲区，"等不到结果"就会被读成"流程没跑完"（假绿）。Unconfined 让
        // `collect` 在 `launch` 返回**之前**就把订阅注册好，因此不需要等 `subscriptionCount`——
        // 那颗只长在 MutableSharedFlow 上，端口面交出来的是只读 SharedFlow，没有这一员。
        collectScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        activeKbName = ""
        ctx = fakeContext()
    }

    @After
    fun tearDown() {
        collectScope.cancel()
        appScope.cancel()
        root.deleteRecursively()
        cacheRoot.deleteRecursively()
        Dispatchers.resetMain()
    }

    // ═══════════ 夹具 ═══════════

    /**
     * 路径一：与 `LoveBrainViewModel` 构造里那一格调的是同一个入口（同一个仓库、同一把锁、
     * 同一条写链）。本类不复制那次初始化的判断，只是把它的产物摆在盘上让路径二去接。
     */
    private suspend fun KnowledgeRepository.runAppInitStep() = ensureInitialKnowledgeBase()

    private fun fakeContext(): Context {
        val assets = mockk<AssetManager>()
        every { assets.open(any<String>()) } answers { assetBytes(firstArg<String>()).inputStream() }
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.assets } returns assets
        return ctx
    }

    /** schema 取真资产（seed 字节与生产一致）；引擎提示词那一格只需要一份非空文本 */
    private fun assetBytes(path: String): ByteArray =
        if (path.startsWith("schema/")) {
            ClassLoader.getSystemClassLoader().getResourceAsStream(path)
                ?.use { it.readBytes() }
                ?: error("schema 资产不在 test classpath 上：$path")
        } else {
            "ONBOARDING-SYSTEM".toByteArray(Charsets.UTF_8)
        }

    /** 每"重启一次"就新建一个仓库实例：与真机重启一样，只有盘上那些字节是共享的 */
    private fun newRepo(): KnowledgeRepository {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.activeKbName } answers { activeKbName }
        every { prefs.activeKbName = any<String>() } answers { activeKbName = arg(0) }
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = ctx,
            appScope = appScope
        )
    }

    /** [answer] 就是引擎那一跳的返回值；需要它抛时让它抛（[createEmptyKb] 那一支永远不会被调到） */
    private fun newVm(port: KnowledgeBaseCatalogPort, answer: suspend () -> String): KnowledgeBaseViewModel {
        val deepSeek = mockk<DeepSeekRepository>(relaxed = true)
        every { deepSeek.getActiveTicket() } returns ProviderTicket(
            name = "工单", baseUrl = "https://example.test/v1", model = "deepseek-chat"
        )
        every { deepSeek.getActiveApiKey() } returns "sk-test"
        coEvery { deepSeek.generateRaw(any(), any()) } coAnswers { answer() }
        return KnowledgeBaseViewModel(
            ctx, port, deepSeek,
            FileKbArchiveTransfer(knowledgeRoot = root, stagingBase = cacheRoot),
            Dispatchers.IO
        )
    }

    /**
     * 一次性事件的真实订阅者。
     *
     * `KbEvent` 那颗 SharedFlow 是 replay=0：订阅者没挂上就触发建库，事件会掉进无人接收的缓冲区，
     * "等不到结果"就会被读成"流程没跑完"——那是假绿。所以收集器用 Unconfined 起（见 setUp 那段：
     * `launch` 返回前订阅就注册好了），本方法只负责挂上，不再等一颗端口面没有的读数。
     */
    private inner class Events {
        private val outcome = CompletableDeferred<KbCreationOutcome>()

        suspend fun attach(vm: KnowledgeBaseViewModel) {
            collectScope.launch {
                vm.events.collect { e -> if (e is KbEvent.Creation) outcome.complete(e.outcome) }
            }
        }

        suspend fun await(what: String): KbCreationOutcome = try {
            withTimeout(10_000) { outcome.await() }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("$what：这一次流程没有交回任何结果（真实超时 10s）")
        }
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

    private fun onboardingRaw(
        me: String, her: String, warmth: String,
        display: String = "AI 取的展示名", stage: String = "暧昧期"
    ) = "===DISPLAY===\n$display\n===STAGE===\n$stage\n===ME===\n$me\n===HER===\n$her\n===WARMTH===\n$warmth"

    /** knowledge/ 根下"算一座库"的那些目录（点开头的都是仓库自己的账：.backup/.last_backup/.kb_initialized） */
    private fun kbDirs(): List<String> =
        root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.map { it.name }?.sorted()
            ?: emptyList()

    /** 一座库目录的字节读数（"没被改动"这一判据用它，而不是"内容读起来差不多"） */
    private fun dirFingerprint(dir: File): Map<String, String> =
        dir.walkTopDown().filter { it.isFile }.associate { f ->
            f.relativeTo(dir).path.replace('\\', '/') to
                java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes())
                    .joinToString("") { "%02x".format(it) }
        }

    private fun metaOf(name: String): KnowledgeBase =
        json.decodeFromString(KnowledgeBase.serializer(), File(root, "$name/kb.json").readText(Charsets.UTF_8))

    private fun writeMeta(name: String, kb: KnowledgeBase) =
        File(root, "$name/kb.json").writeText(json.encodeToString(KnowledgeBase.serializer(), kb), Charsets.UTF_8)

    // ═══════════ 场景① 全新安装 → 完成首次问卷 ⇒ 1 个有效库 ═══════════

    /**
     * 需求表第①条，也是最直接的那一支现场：路径一 seed 出 `default`，用户随后进管理页答完问卷，
     * 路径二必须把那顶空壳**当目标库**，而不是在它旁边再造一座。
     *
     * 坏样子（每一样都对应一条断言）：
     * - 复用没接上（今天的行为）→ `size == 1` 与 `kbDirs()` 两条当场红（根下两座）；
     * - 复用时顺手把目录名也改了 → "稳定内部身份"那条红（`KnowledgeCatalogStore` 要求
     *   `kb.name == 目录名`，改了等于凭空换一座库，清单里那条再也读不回来）；
     * - 只往目录里写画像、没改 kb.json 的显示名 → "显示名按问卷结果改"红；
     * - 画像三段写进了别的库（目标库没绑住）→ 三条 `readFile` 全读到空串，红。
     */
    @Test
    fun `scene1 fresh install plus completed onboarding leaves exactly one effective library`() = runBlocking {
        val repo = newRepo()
        repo.runAppInitStep()
        assertEquals("前提：首次初始化那一步之后盘上恰好一座库", 1, repo.listAll().size)

        val vm = newVm(repo.catalogWrites) { onboardingRaw("我是我", "她是她", "她的温度") }
        val events = Events()
        events.attach(vm)
        vm.createKbWithOnboarding(schema())
        assertEquals(KbCreationOutcome.ProfileCreated, events.await("①首次问卷完成"))

        val all = repo.listAll()
        assertEquals("①全新安装直接完成首次问卷 ⇒ 1 个有效库（多出来的那一座就是本轮缺陷本身）", 1, all.size)
        val kb = all.single()
        assertEquals("复用保留的稳定内部身份 = 首次 seed 的目录名", "default", kb.name)
        assertEquals("显示名按问卷结果改", "AI 取的展示名", kb.displayName)
        assertEquals("阶段落的也是问卷那一段（白名单归一化在画像格）", "暧昧期", kb.stage)
        assertEquals("画像三段落进的就是这一座", "我是我", repo.readFile(kb.name, "understand/me.md"))
        assertEquals("画像三段落进的就是这一座", "她是她", repo.readFile(kb.name, "understand/her.md"))
        assertEquals("画像三段落进的就是这一座", "她的温度", repo.readFile(kb.name, "understand/warmth.md"))
        assertEquals("当前库仍是那一座，没有被一次新建甩走", "default", activeKbName)
        assertEquals("根下不许出现第二座库目录", listOf("default"), kbDirs())
        assertTrue("首次初始化那一步的完成标记仍在", File(root, ".kb_initialized").isFile)
    }

    // ═══════════ 场景② 全新安装 → 跳过问卷 ⇒ 1 个可使用的空库 ═══════════

    /**
     * 需求表第②条（今天这一支同样出两个库：`createEmptyKb` 在 default 之外又建"新知识库"）。
     *
     * 坏样子：
     * - 跳过那一支没接复用（今天的行为）→ `size == 1` 红、根下两座；
     * - 把"跳过"实现成"直接报错"→ 事件不是 EmptyCreated 红（跳过是用户的一次确认，不是失败）；
     * - 复用时顺手盖了画像或阶段 → "正文仍是空的""阶段仍是待确定"两条红
     *   （跳过没有问卷结果：没有可写内容就不该往库里写字节）。
     */
    @Test
    fun `scene2 fresh install skipping the questionnaire leaves one usable empty library`() = runBlocking {
        val repo = newRepo()
        repo.runAppInitStep()

        val vm = newVm(repo.catalogWrites) { error("跳过问卷这一支不该碰引擎") }
        val events = Events()
        events.attach(vm)
        vm.createEmptyKb()
        assertEquals(KbCreationOutcome.EmptyCreated, events.await("②跳过问卷"))

        val all = repo.listAll()
        assertEquals("②全新安装跳过问卷 ⇒ 1 个可使用的空库", 1, all.size)
        val kb = all.single()
        assertEquals("内部身份仍是首次 seed 那一座", "default", kb.name)
        assertEquals("跳过这一支没有问卷画像，正文一格都不许多写", "", repo.readFile(kb.name, "understand/me.md"))
        assertEquals("", repo.readFile(kb.name, "understand/her.md"))
        assertEquals("跳过不改阶段", "待确定", kb.stage)
        assertEquals("这座空库是当前在用的（可直接使用）", "default", activeKbName)
        assertEquals(listOf("default"), kbDirs())
    }

    /**
     * 同一场景的另一半顺序：**先**建库、**后**才跑首次初始化（面板 VM 还没构造就被拉进管理页）。
     *
     * 这一支钉的是"两个入口谁先到都只留一座库"：复用判据写在路径二的 create 里，
     * 而路径一的幂等判据（有库就不 seed）照旧成立。
     * 坏样子：把复用判据写成"只认目录名 default"→ 这里先建出来的 `kb_*` 会被路径一当成"没有库"，
     * 于是 default 被额外 seed 出来 → 最后一行红。
     */
    @Test
    fun `scene2b creating before the app-init step still converges on one library`() = runBlocking {
        val repo = newRepo()
        val vm = newVm(repo.catalogWrites) { error("跳过问卷这一支不该碰引擎") }
        val events = Events()
        events.attach(vm)
        vm.createEmptyKb()
        assertEquals(KbCreationOutcome.EmptyCreated, events.await("②跳过（路径一还没跑）"))
        val created = repo.listAll().single()
        assertTrue("没有可复用的空壳时才真的新建：库名是这一次自造的", created.name.startsWith("kb_"))

        repo.runAppInitStep()

        assertEquals("先建库再跑首次初始化 ⇒ 仍然只有 1 座库", 1, repo.listAll().size)
        assertEquals(listOf(created.name), kbDirs())
    }

    // ═══════════ 场景③ 建库成功后重启 App ⇒ 不增加库 ═══════════

    /**
     * 需求表第③条：换一个新仓库实例（= 真机重启）再跑一次路径一。
     *
     * 坏样子：
     * - 复用后这座库还被判成空壳（例如判据只读正文不读显示名，而画像恰好写成了空段）→
     *   这一趟之后它会被再吃一次，`displayName` / `stage` 两条红；
     * - 路径一被改成"没有 default 目录就再 seed 一座"→ `size == 1` 与 `kbDirs()` 两条红；
     * - 重启时补齐逻辑覆盖了正文 → 最后那条 `readFile` 红。
     *
     * ⚠ 这里刻意不判"整目录字节一个都没漂"：重启这一趟本来会跑旧格式归一化
     *   （`.schema_version` 与 `memory/reflect_history.md` 是迁移器登记的补齐格），
     *   那是既存行为，与本轮要修的"两座库"无关。这一格判的是**库的数量与这一次的成果**。
     */
    @Test
    fun `scene3 restarting the app after a successful creation adds no library`() = runBlocking {
        val repo = newRepo()
        repo.runAppInitStep()
        val vm = newVm(repo.catalogWrites) { onboardingRaw("我是我", "她是她", "她的温度") }
        val events = Events()
        events.attach(vm)
        vm.createKbWithOnboarding(schema())
        events.await("③建库那一趟")

        val restarted = newRepo()                                  // 真机重启的形状：新实例 + 同一个盘
        restarted.runAppInitStep()

        val all = restarted.listAll()
        assertEquals("③建库成功后重启 App ⇒ 不增加库", 1, all.size)
        assertEquals("根下只有那一座", listOf("default"), kbDirs())
        val kb = all.single()
        assertEquals("default", kb.name)
        assertEquals("重启没把这次的成果当成空壳再吃一遍：显示名仍是问卷那一个", "AI 取的展示名", kb.displayName)
        assertEquals("阶段仍是这次落的那一个", "暧昧期", kb.stage)
        assertEquals("画像正文还是那一份（补齐只补缺失，不覆盖）", "我是我", restarted.readFile("default", "understand/me.md"))
        assertEquals("当前库引用没被重启改写", "default", activeKbName)
    }

    // ═══════════ 场景④ 已有一个真实库 → 主动新建 ⇒ 正常变 2 个 ═══════════

    /**
     * 需求表第④条，也是"复用不能过头"的正面证人：第二座库必须照样加得出来。
     *
     * 这一格故意让"真实库"由**本流程自己**建出来（①跑完之后那座带画像与阶段的 default）：
     * 复用因此最多只发生一次——第一次把显示名改掉之后，那座库就再也不像空壳了。
     * 坏样子：
     * - `reusableInitialKb()` 去掉"清单里只有一座"那条护栏 → 第二次请求又被吃掉，`size == 2` 红；
     * - 判据退化（例如"只要显示名是默认知识库就复用"）→ 用户那座真库被改名，
     *   `displayName` 与 `me.md` 两条红。
     */
    @Test
    fun `scene4 an existing real library plus a new creation gives two libraries`() = runBlocking {
        val repo = newRepo()
        repo.runAppInitStep()
        val first = newVm(repo.catalogWrites) { onboardingRaw("我是我", "她是她", "她的温度") }
        val e1 = Events()
        e1.attach(first)
        first.createKbWithOnboarding(schema())
        e1.await("④第一座（带真实内容的那座）")

        val second = newVm(repo.catalogWrites) { error("主动新建这一支不该碰引擎") }
        val e2 = Events()
        e2.attach(second)
        second.createEmptyKb()
        e2.await("④主动新建")

        val all = repo.listAll()
        assertEquals("④已有一个真实库再主动新建 ⇒ 正常变 2 个", 2, all.size)
        val real = all.first { it.name == "default" }
        assertEquals("那座真实库的显示名不许被第二次建库改掉", "AI 取的展示名", real.displayName)
        assertEquals("那座真实库的画像一个字都不许动", "我是我", repo.readFile("default", "understand/me.md"))
        val added = all.first { it.name != "default" }
        assertTrue("新加的那座是这次自造的目录", added.name.startsWith("kb_"))
        assertEquals("新知识库", added.displayName)
        assertEquals(listOf("default", added.name), kbDirs())
    }

    // ═══════════ 场景⑤ 取消或失败 ⇒ 不留下额外半成品库 ═══════════

    /**
     * 需求表第⑤条的"引擎失败"那一支（今天的行为：`generateRaw` 抛 → raw="" → 照样 create，
     * 于是在 default 之外多出一座 13 格全模板的空库）。
     *
     * 判据是**字节级**的：那顶还没用过的空壳整目录指纹必须与跑之前完全相等
     * （连 kb.json 的 updatedAt 都不许漂），并且根下不许多出任何目录。
     * 坏样子：
     * - 顺序没改（先建目录再发现没内容可写）→ `kbDirs()` 多一座、事件也不是失败收场；
     * - 失败被咽成"降级建库成功"→ 事件那条红（§12.3「失败显示失败」）；
     * - 反过来为了"清理重复"去删那座旧库 → 指纹与"还在"两条红。
     */
    @Test
    fun `scene5a engine failure leaves no half library and touches nothing`() = runBlocking {
        val repo = newRepo()
        repo.runAppInitStep()
        val before = dirFingerprint(File(root, "default"))

        val vm = newVm(repo.catalogWrites) { throw IllegalStateException("401") }
        val events = Events()
        events.attach(vm)
        vm.createKbWithOnboarding(schema())
        assertEquals(KbCreationOutcome.OnboardingCreateFailed, events.await("⑤引擎报错"))

        assertEquals("⑤首次问卷失败 ⇒ 不留下额外半成品库", listOf("default"), kbDirs())
        assertEquals("一座都没多，也没删或改名：旧库字节完全没漂", before, dirFingerprint(File(root, "default")))
        assertEquals(1, repo.listAll().size)
        assertEquals("默认知识库", metaOf("default").displayName)
    }

    /**
     * 第⑤条的另一支：引擎**答了话但答的是空**（不抛异常）。
     *
     * 单独钉这一支的理由：旧代码里它是"走得通的一条路"——raw="" → 解析四段全空 →
     * 显示名回落到"我的她" → 照样建出一座库。"还没拿到可写内容就别建目录"这句判据读的是**内容**
     * 而不是异常，所以异常/空响应两路都要有格子。
     * 坏样子：只在 catch 分支里 return（漏掉空响应那一支）→ 这三行全红。
     */
    @Test
    fun `scene5b blank answer without an exception also creates nothing`() = runBlocking {
        val repo = newRepo()
        repo.runAppInitStep()
        val before = dirFingerprint(File(root, "default"))

        val vm = newVm(repo.catalogWrites) { "   " }
        val events = Events()
        events.attach(vm)
        vm.createKbWithOnboarding(schema())
        assertEquals(KbCreationOutcome.OnboardingCreateFailed, events.await("⑤空响应"))

        assertEquals(listOf("default"), kbDirs())
        assertEquals(before, dirFingerprint(File(root, "default")))
    }

    /**
     * 第⑤条最刁的一支：**取消落在 create 之后、画像写的挂起期间**——当前没有任何用例覆盖这一支。
     *
     * 端口是"真写侧 + 第一次画像写之后挂住"的薄封装，所以目录是真的落盘了（不是假想场景）。
     * 期望：只回收**这一次自造**的那一座（沿用写侧 seed 半途失败时回收自己刚造的目录那条先例），
     * 盘上回到这一次流程开始前的"零座库"，然后重启时路径一正常 seed 出唯一那座 default。
     * 坏样子：
     * - 不回收（今天的行为）→ `kbDirs()` 里留着那座半成品；
     * - 把回收包在已取消的协程里（不套 NonCancellable）→ 删不动，`kbDirs()` 红；
     * - 回收判据写成"删掉清单里最新/第一座" → 这一格看不出恶果，
     *   所以必须有下面那一格（有可复用空壳时）配套，两格互为反向证人。
     */
    @Test
    fun `scene5c cancel right after the create reclaims only what this run made`() = runBlocking {
        val repo = newRepo()                       // 没跑路径一：没有可复用的空壳，这一次只能自造一座
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val vm = newVm(HangingAfterFirstWrite(repo.catalogWrites, started, gate)) {
            onboardingRaw("我是我", "她是她", "她的温度")
        }
        val events = Events()
        events.attach(vm)
        vm.createKbWithOnboarding(schema())

        withTimeout(5_000) { started.await() }      // 目录已落盘，画像卡在写之后
        vm.cancelOnboarding()
        assertEquals(KbCreationOutcome.Cancelled, events.await("⑤取消在建库之后"))

        assertEquals("⑤取消后不许留下半成品库（本次自造的那座要收回）", emptyList<String>(), kbDirs())
        assertEquals("当前库引用也被清掉了，没指向一座不存在的库", "", activeKbName)

        repo.runAppInitStep()
        assertEquals("重启后由路径一给出唯一那座可用库", listOf("default"), kbDirs())
    }

    /**
     * 同一支的反向证人，也是"绝不删用户那座库"的那颗牙。
     *
     * 这一次盘上有路径一留下的空壳，写侧把它当目标库（回来的库名**不是**这次提出的 `kb_*`），
     * 于是取消时一个字节都不许回收：那座库还在、目录名还在、已经写进去的画像还在。
     * 坏样子：
     * - 回收判据丢掉 `selfCreated`（例如改成"取消就删掉 created.name"）→ 这里会把用户那座
     *   刚被复用的库一起删掉，`kbDirs()` 与"画像还在"两条当场红——
     *   那正是需求里明令禁止的"以修复重复为理由自动删除现有库"；
     * - 反过来"取消时什么都不回收"→ 上面 scene5c 红。
     */
    @Test
    fun `scene5d cancel after adopting never touches the reused library`() = runBlocking {
        val repo = newRepo()
        repo.runAppInitStep()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val vm = newVm(HangingAfterFirstWrite(repo.catalogWrites, started, gate)) {
            onboardingRaw("我是我", "她是她", "她的温度")
        }
        val events = Events()
        events.attach(vm)
        vm.createKbWithOnboarding(schema())

        withTimeout(5_000) { started.await() }
        vm.cancelOnboarding()
        assertEquals(KbCreationOutcome.Cancelled, events.await("⑤取消（复用之后）"))

        assertEquals("复用的那座不是本次自造的：一个字节都不许回收", listOf("default"), kbDirs())
        assertEquals("我是我", repo.readFile("default", "understand/me.md"))
        assertEquals("AI 取的展示名", metaOf("default").displayName)
        assertEquals("当前库引用仍指着它", "default", activeKbName)
    }

    // ═══════════ 场景⑥ 已有旧数据升级 ⇒ 不删除、不覆盖、不错误改名 ═══════════

    /**
     * 需求表第⑥条：旧数据（同一座库里已有真实画像与聊天计数）升级后不删除、不覆盖、不错误改名；
     * 用户随后建库时**照常加一座**。
     *
     * 形状照"旧版本落盘的那座库"：目录名与显示名都还是 default / 默认知识库（旧版 seed 的形状），
     * 但 `turnCount`/`topicCount` 已涨、`understand/me.md` 有真实画像——
     * 于是"目录名与显示名都像空壳"而"真实读数不像"。这一格量的正是判据读的是哪一边。
     * 坏样子：
     * - 判据只认目录名 default → 这座旧库被改名（`displayName` 那条红）、画像被覆盖
     *   （`me.md` 那条红），而且新库根本没建出来（`size == 2` 红）；
     * - 判据漏读 `turnCount`、`topicCount` 或某一条正文 → 同上，任一条红；
     * - 任何"顺手删掉重复库"的实现 → 旧库目录与指纹那两条红。
     */
    @Test
    fun `scene6 old data with real content is kept not renamed not overwritten`() = runBlocking {
        val repo = newRepo()
        repo.runAppInitStep()                                   // 旧版本形状：default + 默认知识库
        File(root, "default/understand/me.md").writeText("旧版本攒下的真实画像", Charsets.UTF_8)
        File(root, "default/memory/raw_chat.md").writeText("旧聊天记录", Charsets.UTF_8)
        writeMeta("default", metaOf("default").copy(turnCount = 12, topicCount = 3))

        val upgraded = newRepo()                                // 升级后的新实例，第一次启动
        upgraded.runAppInitStep()
        assertEquals("⑥升级不新建：仍然只有那座旧库", 1, upgraded.listAll().size)
        val before = dirFingerprint(File(root, "default"))      // 归一化跑完之后的基线

        val vm = newVm(upgraded.catalogWrites) { onboardingRaw("新画像的我", "新画像的她", "新画像的温度") }
        val events = Events()
        events.attach(vm)
        vm.createKbWithOnboarding(schema())
        assertEquals(KbCreationOutcome.ProfileCreated, events.await("⑥升级后建库"))

        val all = upgraded.listAll()
        assertEquals("旧库有真实内容 ⇒ 后续新建照常加库（2 座）", 2, all.size)
        assertTrue("旧库没被删", File(root, "default").isDirectory)
        assertEquals("不错误改名：显示名仍是旧库那一个", "默认知识库", metaOf("default").displayName)
        assertEquals("旧版本攒下的真实画像", upgraded.readFile("default", "understand/me.md"))
        assertEquals("旧库的字节整趟都没漂", before, dirFingerprint(File(root, "default")))
        val fresh = all.first { it.name != "default" }
        assertTrue("新库是这一次自造的", fresh.name.startsWith("kb_"))
        assertEquals("新画像的我", upgraded.readFile(fresh.name, "understand/me.md"))
    }

    /**
     * 与⑥同一条需求里的另一判据：**「可以复用」不看目录名**（§4 明写"不以目录叫 default 作为唯一依据"）。
     *
     * 这里把首次 seed 那份**真空壳**落在一个叫 `legacy_shell` 的目录名下（旧版本或导入留下的形状：
     * 用真 seed 再改目录名与 kb.json 的 name，保证形状与生产一致）。
     * 期望：它同样算可复用 → 整个流程只有一座库，内部身份 `legacy_shell` 保持不变。
     * 坏样子：判据写成 `kb.name == "default"` → 这里会另建一座 `kb_*`，
     * `size == 1` 与"内部身份仍是 legacy_shell"两条红；反过来把目录名当**唯一**依据（不看内容）时，
     * 上面 scene6 那格会红——两格合起来才把"目录名不参与判断"钉死。
     */
    @Test
    fun `reuse key reads real facts instead of the directory name`() = runBlocking {
        val repo = newRepo()
        repo.runAppInitStep()
        val shell = File(root, "legacy_shell")
        assertTrue("把首次 seed 那座空壳换个目录名（模拟旧版本/导入留下的形状）", File(root, "default").renameTo(shell))
        writeMeta("legacy_shell", metaOf("legacy_shell").copy(name = "legacy_shell"))

        val vm = newVm(repo.catalogWrites) { onboardingRaw("我是我", "她是她", "她的温度") }
        val events = Events()
        events.attach(vm)
        vm.createKbWithOnboarding(schema())
        assertEquals(KbCreationOutcome.ProfileCreated, events.await("目录名不是依据"))

        val all = repo.listAll()
        assertEquals("空壳换个目录名一样算可复用 ⇒ 仍然只有 1 座", 1, all.size)
        assertEquals("内部身份保持不动：目录名仍是 legacy_shell", "legacy_shell", all.single().name)
        assertEquals("AI 取的展示名", all.single().displayName)
        assertEquals(listOf("legacy_shell"), kbDirs())
    }

    /**
     * 一次薄封装：真写侧照常落盘，只在**第一次**画像写之后挂住。
     *
     * 为什么要它：这一族用真磁盘（否则跨两条路径的那个边界根本看不见），
     * 而"取消落在 create 之后"这一支需要一个真实的挂起点才能被稳定复现——
     * 引擎那一跳早就跑完了，流程里剩下的唯一挂起窗口就是画像写。
     */
    private class HangingAfterFirstWrite(
        private val delegate: KnowledgeBaseCatalogPort,
        private val started: CompletableDeferred<Unit>,
        private val gate: CompletableDeferred<Unit>
    ) : KnowledgeBaseCatalogPort by delegate {
        private var hung = false

        override suspend fun writeFile(kbName: String, relativePath: String, content: String) {
            delegate.writeFile(kbName, relativePath, content)
            if (hung) return
            hung = true
            started.complete(Unit)
            gate.await()
        }
    }
}
