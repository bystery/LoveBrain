package com.lovebrain.app.viewmodel

import android.content.SharedPreferences
import com.lovebrain.app.LoveBrainApp
import com.lovebrain.app.data.ConnectionTestResult
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.di.appModule
import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.domain.port.KnowledgeReadPort
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.ui.home.AdvisorControl
import com.lovebrain.app.ui.home.AdvisorLamp
import com.lovebrain.app.ui.home.AdvisorMissing
import com.lovebrain.app.ui.home.AdvisorState
import com.lovebrain.app.ui.home.AdvisorStatus
import com.lovebrain.app.ui.home.render
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * 首页那盏灯的**装配后行为**（ 那两条纪律，判在真端口与真容器上）。
 *
 * 与 `HomeStatusViewModelTest` 的分工要说清：那一族拿四颗**哑**端口判状态机本身，
 * 上一版的两条病恰好都在它的射程之外——
 * ① 装配：`HomeStatusViewModel` 没并进 `di/AppModule.kt`，知识库那一条靠
 *    `KoinPlatform.getKoin()` 在 VM 文件里自己摸读端口（第二套依赖机制）；
 * ② 探针触发条件：`returnedFromSubpage` 在"身份变了"时**自己补一次检查**，
 *    而哑探针让这一条看起来无害（它烧的是假请求，不是用户的钱）。
 * 这一族因此判"接进容器之后的这条链"：三颗端口实现（`SettingsStoreHomeProviderPort` /
 * `GatewayHomeProbePort` / `ReadPortHomeKnowledgePort`）、那颗 `viewModel {}` 注册、
 * 以及"只有按 ▶ 才发"的实测计数（`coVerify` 在 `DeepSeekRepository.testConnectionWithProbe` 那一颗）。
 *
 * ⚠ JVM 上 `FloatingService.instance` 永远是 null，所以**绿档在这一族里不可达**
 * （缺项里总会有"军师服务没起来"那一条）。这一族因此不判"绿"，只判三件事：
 * 请求发了几次、旧结论还在不在、缺项念的是哪一句。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeStatusDiProbeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 真 CoroutineScope：KnowledgeRepository 的 init 会往里 launch，空跑即可 */
    private val appScope = CoroutineScope(SupervisorJob())

    private val store = InMemorySettingsStore()
    private val read = mockk<KnowledgeReadPort>()
    private val gateway = mockk<DeepSeekRepository>()

    private val ticket = ProviderTicket(
        id = "t-deep", name = "DeepSeek", baseUrl = "https://api.example.com", model = "deepseek-chat"
    )

    @Before
    fun setUp() {
        stopKoinIfRunning()
    }

    @After
    fun tearDown() {
        stopKoinIfRunning()
    }

    private fun stopKoinIfRunning() = runCatching { stopKoin() }

    private companion object {
        /** 假 Key：只在内存里走一趟，绝不进日志也绝不发给任何真地址 */
        const val KEY_SENTINEL = "sk-sentinel-not-a-real-key"

        /**
         * 那颗 Main 委托**整类装一次**，用不排队的 `Dispatchers.Unconfined`，而不是每格
         * `setMain(UnconfinedTestDispatcher()) / resetMain()`。这不是口味差异，是修
         * `the probe uses the stored key…` 那格的 `UncaughtExceptionsBeforeTest`（已删）：
         * 污染源就是本文件前一格 `the container builds…`——它把 VM 接进**真**容器，
         * `returnedFromSubpage` 的那次本地重读是 `viewModelScope.launch`，挂在仓库的
         * `withContext(Dispatchers.IO)` 上；runTest 的调度器等不到真 IO 线程上那一跳回交，
         * 而 1.8.1 的 `runTest`/`TestScope.enter` 自己**不**接管 Main——于是 resetMain 一旦落在
         * 回交之前，委托退回这个纯 JVM classloader 里永远加载失败的 MissingMain
         * （红话 Caused-by 的根："The main looper is not available"），
         * `isDispatchNeeded` 当场炸成 `CoroutinesInternalError`，被 kotlinx-coroutines-test
         * 的全局 ExceptionCollector 存进"测试开始前"的队列，毒死**下一格**的 `runTest`。
         * 用类级的 Unconfined：回交永不排队，就在交出结果的那根 IO 线程上同步走完，
         * 类跑完才 resetMain——在飞的回交早已落地，没有窗口。
         * 同步语义与原来一致：viewModelScope 走"不排队那一档"，"按 ▶ → 探针 → 落灯"
         * 仍在调用点同步走完，计数不是时序赌博（各格里的 `advanceUntilIdle()` 管的是
         * runTest 自己的调度器，不受影响）。
         */
        @JvmStatic
        @BeforeClass
        fun installMainForClass() {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        @JvmStatic
        @AfterClass
        fun releaseMainForClass() {
            Dispatchers.resetMain()
        }
    }

    /** 与 `AppModuleGraphTest` 同一副假 App：filesDir 指临时目录，SharedPreferences 给宽松桩 */
    private fun fakeApp(): LoveBrainApp {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.commit() } returns true
        every { prefs.getString(any(), any()) } returns null
        every { prefs.getStringSet(any(), any()) } returns null
        every { prefs.getBoolean(any(), any()) } returns false
        every { prefs.getInt(any(), any()) } returns 0
        every { prefs.getLong(any(), any()) } returns 0L
        every { prefs.contains(any()) } returns false

        val app = mockk<LoveBrainApp>(relaxed = true)
        every { app.filesDir } returns tmp.root
        every { app.cacheDir } returns tmp.root
        every { app.packageName } returns "com.lovebrain.app"
        every { app.applicationContext } returns app
        every { app.getSharedPreferences(any(), any()) } returns prefs
        every { app.applicationScope } returns appScope
        return app
    }

    private fun startGraph(): Koin = startKoin {
        androidContext(fakeApp())
        modules(appModule)
    }.koin

    /** 把"盘上那一份"配成齐的：工单 + 生效 + Key（Key 是假的那一条，不拿去连任何真地址） */
    private fun configuredStore(): SettingsStorePort = store.apply {
        setWorkerTickets(listOf(ticket))
        activeTicketId = ticket.id
        saveWorkerApiKey(ticket.id, KEY_SENTINEL)
    }

    private fun kbHasContent() = KnowledgeBase(name = "她", turnCount = 3, topicCount = 2)

    private fun probeSucceeds() {
        coEvery { read.getActive() } returns kbHasContent()
        coEvery { gateway.testConnectionWithProbe(any(), any(), any()) } returns
            ConnectionTestResult(success = true)
    }

    // ═══════════ 1. 容器这一半：那颗 viewModel {} 真解析得动，装配不碰网络 ═══════════

    /**
     * 反例（这一格会怎么红）：
     * - `viewModel { newHomeStatusViewModel(get(), get(), get()) }` 是**位置参数**——
     *   装配函数调参数顺序/加一颗端口，编译期不报错，首次 get() 才炸（`AppModuleGraphTest` 的存在理由）；
     * - 有人把 `viewModel {}` 改成 `single {}` → 最后那句 `assertNotSame` 红：
     *   两棵 ViewModelStore 会共用同一颗可变状态机，切对象的旧结论会串到下一趟；
     * - 有人给灯另开一颗 `SecurePrefs(...)` → 那句"端口与页面解析到同一颗"当场红。
     */
    @Test
    fun `the container builds the lamp view model without probing`() = runTest {
        val koin = startGraph()
        val vm = koin.get<HomeStatusViewModel>()
        advanceUntilIdle()

        assertEquals("冷启动那一格是红 + ▶", AdvisorState.Stopped, vm.status.value.state)
        assertEquals(AdvisorLamp.Red, vm.status.value.render().lamp)
        assertNull("红档不写常驻解释", vm.status.value.render().hint)

        // 这台假机器上一条配置都没有：进页面也不许把灯推上去（更不许发请求）
        vm.returnedFromSubpage(overlayGranted = false)
        advanceUntilIdle()
        assertEquals("进页面不许自己点灯", AdvisorState.Stopped, vm.status.value.state)

        assertNotSame(
            "viewModel {} 是工厂语义，改成 single {} 就让两棵 store 共用一颗状态机",
            vm, koin.get<HomeStatusViewModel>()
        )
        assertTrue(
            "灯读的那颗偏好存储就是容器里唯一那颗（== SecurePrefs 本体），不是第二份加密判据持有者",
            koin.get<SettingsStorePort>() === koin.get<SecurePrefs>()
        )
    }

    // ═══════════ 2. 探针触发条件：只有按 ▶ 才发 ═══════════

    @Test
    fun `assembly and page entry send no probe while the play button sends exactly one`() = runTest {
        val model = configuredStore()
        probeSucceeds()

        val vm = newHomeStatusViewModel(model, read, gateway)
        advanceUntilIdle()
        coVerify(exactly = 0) { gateway.testConnectionWithProbe(any(), any(), any()) }

        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()
        coVerify(exactly = 0) { gateway.testConnectionWithProbe(any(), any(), any()) }
        // 进页面这一档在 第7节第2条 那张表上就是第一行："未启动/已关闭 → 红 + ▶ + 无常驻解释"。
        // 上一版这里要求缺项里出现"连接还没检查成功"，那一句话**不属于红档**：
        // 红档按合同不念任何缺项（`AdvisorStatus(Stopped)` 的 missing 本来就是空表），
        // 而 `userStarted == false` 时派生表走的正是那一支。实到 `缺项 []` 是对的，
        // 红的是尺读错了档，不是派生表。
        // 这一格因此把红档那四件产物逐件钉住——它比原来那句更凶：
        // 装配好的这台机器上盘是齐的、探针也真的会成功，谁要是把"进页面自己补一次检查"
        // 写回来（上一版的真事故），`Stopped` 与那句 `coVerify(exactly = 0)` 一起红。
        assertEquals("没按 ▶ 就是红档，不是黄也不是绿", AdvisorState.Stopped, vm.status.value.state)
        val entered = vm.status.value.render()
        assertEquals("红档的灯", AdvisorLamp.Red, entered.lamp)
        assertEquals("红档的控件只有 ▶", AdvisorControl.Play, entered.control)
        assertNull("红档不写常驻解释", entered.hint)

        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }
        // 按过 ▶ 之后"还没检查过"那一句必须收掉：这一句是"探针的结果真的落进了状态机"的证人。
        // 反例：检查结果只写了探针端口、没写 evidence → 灯黄着却还在念上一档的话。
        assertTrue(
            "检查已经成功过，不许还念'连接还没检查成功'，实到缺项 ${vm.status.value.missing}",
            !vm.status.value.missing.contains(AdvisorMissing.ConnectionUnchecked)
        )
        // JVM 上 FloatingService.instance 恒 null ⇒ 这一族不可达绿档（文件头那条），
        // 这里把"为什么不是绿"也钉成一句实话，而不是留成一句注释。
        assertTrue(
            "服务在这一族里起不来，缺项必须自己说出这一条，实到 ${vm.status.value.missing}",
            vm.status.value.missing.contains(AdvisorMissing.ServiceNotRunning)
        )

        // 反复进页面不再补发：上一版那一条"身份变了就自己查一次"就是在这里变成第二次的
        repeat(3) { vm.returnedFromSubpage(overlayGranted = true) }
        advanceUntilIdle()
        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }
    }

    /**
     * 切供应商 = 旧检查当场作废，**但不补发**。
     *
     * 为什么这一格非要判在 `SettingsStoreHomeProviderPort` 上而不是哑端口上：
     * 上一版的身份来源是 `SetupViewModel.activeTicket.value`，而 Koin 的 `viewModel {}` 是工厂语义——
     * 真接进容器就会拿到**第二颗** SetupViewModel，它那份 `_tickets` 是它自己构造时读的缓存。
     * 于是供应商页写的盘、灯读的那颗 VM 谁都不认谁，"切了供应商"对灯永远没发生。
     * 这一格用另一颗存储写入者（`model.setWorkerTickets` + 换 `activeTicketId`）摆出那个形状。
     */
    @Test
    fun `switching provider voids the stale conclusion without sending a second probe`() = runTest {
        val model = configuredStore()
        probeSucceeds()

        val vm = newHomeStatusViewModel(model, read, gateway)
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }

        val other = ProviderTicket(
            "t-other", "Kimi", "https://api.other.example", model = "kimi-k2"
        )
        model.setWorkerTickets(listOf(ticket, other))
        model.activeTicketId = other.id
        model.saveWorkerApiKey(other.id, "sk-other-key")

        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }
        assertTrue(
            "换供应商后旧结论还撑着 = 对没验过的那家说检查成功，实到缺项 ${vm.status.value.missing}",
            vm.status.value.missing.contains(AdvisorMissing.ConnectionUnchecked)
        )
        assertTrue(
            "这一格不许是绿的", vm.status.value.state != AdvisorState.RunningReady
        )
    }

    /**
     * 探针带出去的是**这一组身份**那三样：Key 现取、模型与地址来自当前那张工单。
     *
     * 上一版把 Key 交空串、由 `SetupViewModel.testConnection` 内部兜底去取；并进容器之后
     * 没有那颗 VM 替它兜底了，所以"Key 到底从哪来"必须在这里钉住。
     * 反例：Key 交空串 → `testConnectionWithProbe` 收到 ""，这一格红；
     * 反例：拿第一张工单而不是生效那张 → 模型/地址那两位红。
     */
    @Test
    fun `the probe uses the stored key and the active ticket's own model and url`() = runTest {
        val model = configuredStore()
        probeSucceeds()

        val vm = newHomeStatusViewModel(model, read, gateway)
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            gateway.testConnectionWithProbe(KEY_SENTINEL, ticket.model, ticket.baseUrl)
        }
    }

    // ═══════════ 3. 当前对象知识库的存在性判据（指导书 §7.4：有效已选即已建立）═══════════

    /**
     * 只筛知识库两格（悬浮权限/供应商/服务/连接与这一族判点无关）。
     * 反例：知识库判据从缺项表里整个掉线（比如异常被吞成 Present、Missing 不再产出）——
     * 各格对它摆的**相等**断言当场红，不是一句"存在即可"。
     */
    private fun kbSays(status: AdvisorStatus): List<AdvisorMissing> = status.missing.filter {
        it == AdvisorMissing.NoKnowledgeBase || it == AdvisorMissing.KnowledgeUnread
    }

    /**
     * 这一格旧版钉的是"空库 = Missing（主线程 2026-10-03 拍）"，那条行为已被用户原话
     * 第 13 条否定，2026-10-05 依指导书 §7.4 按新判据改写（台账
     * `evidence/2026-10-05-feedback/impl-G2-empty-kb.md` 有改写前后对照）。四格：
     * ① 新建/默认空库（两计数全 0）→ 已建立，既不念"请建立"也不念"还没读到"；
     * ①' 对话一轮后再读（只有 turnCount 动、库名没动）→ 判定不许出现错误翻转，
     *     同一身份的已验证结论也不许作废；
     * ② 只有归档批次数 → 同样不念建库（手写画像那格 turnCount 恒 0，形状与 ① 同一读数）；
     * ③ 读端口抛异常 → 念"还没读到"，**不许**把"读不动"报成"没有"，更不许绿。
     *
     * 反例：把 `turnCount > 0 || topicCount > 0` 的旧判据写回来 → ① 的相等断言当场红；
     * 反例：把轮数算进身份位 → ①' 会冒出"连接还没检查成功"，红；
     * 反例：异常吞成 Missing/Present → ③ 的两句各红一条。
     */
    @Test
    fun `a selected empty library counts as built while one round later nothing flips`() = runTest {
        val model = configuredStore()
        coEvery { gateway.testConnectionWithProbe(any(), any(), any()) } returns
            ConnectionTestResult(success = true)

        // ① 新建库的 kb.json 就是这个形状：目录建好、一格正文都没积累过
        coEvery { read.getActive() } returns KnowledgeBase(name = "她", turnCount = 0, topicCount = 0)
        val empty = newHomeStatusViewModel(model, read, gateway)
        empty.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(
            "选了空库既不许念建库也不许念读不到",
            emptyList<AdvisorMissing>(), kbSays(empty.status.value)
        )
        val hint = empty.status.value.render().hint.orEmpty()
        assertTrue(
            "空库的黄字里不许出现建库那一句，实到「$hint」",
            !hint.contains("请为当前对象建立知识库")
        )

        // ①' 一轮对话之后：turnCount 动了、库名没动——前后都是 Present，旧结论不作废、不补发
        coEvery { read.getActive() } returns KnowledgeBase(name = "她", turnCount = 1, topicCount = 0)
        empty.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()
        assertEquals("一轮前后判定不许翻转", emptyList<AdvisorMissing>(), kbSays(empty.status.value))
        assertTrue(
            "同身份的已验证结论不许因为计数变化就作废，实到缺项 ${empty.status.value.missing}",
            !empty.status.value.missing.contains(AdvisorMissing.ConnectionUnchecked)
        )
        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }

        // ② 只有归档批次数（轮数与归档都不参与存在性，这一格是第二个读数点）
        coEvery { read.getActive() } returns KnowledgeBase(name = "她", turnCount = 0, topicCount = 1)
        val lessonsOnly = newHomeStatusViewModel(model, read, gateway)
        lessonsOnly.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(listOf<AdvisorMissing>(), kbSays(lessonsOnly.status.value))

        // ③ 读不动 ≠ 没有
        coEvery { read.getActive() } throws IllegalStateException("disk gone")
        val unreadable = newHomeStatusViewModel(model, read, gateway)
        unreadable.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(listOf(AdvisorMissing.KnowledgeUnread), kbSays(unreadable.status.value))
        assertTrue(
            "读不动这一格永远不许绿", unreadable.status.value.state != AdvisorState.RunningReady
        )
    }

    /**
     * §7.4 的另一半：真缺失照旧要念。没有活动库、库名空白（无效引用）都还是 `Missing`，
     * 缺项仍是"请为当前对象建立知识库"。
     * 反例：修空库误报时把"没有库"也吞成 Present → 两句 `kbSays` 相等各红；
     * 反例：吞成 Unknown → 筛出来的是 `KnowledgeUnread`，同样红。
     */
    @Test
    fun `no active library and blank library name both still ask to build`() = runTest {
        val model = configuredStore()
        coEvery { gateway.testConnectionWithProbe(any(), any(), any()) } returns
            ConnectionTestResult(success = true)

        coEvery { read.getActive() } returns null
        val none = newHomeStatusViewModel(model, read, gateway)
        none.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(listOf(AdvisorMissing.NoKnowledgeBase), kbSays(none.status.value))
        assertTrue(
            "真没选库的黄字要念得出建库那一句，实到「${none.status.value.render().hint.orEmpty()}」",
            none.status.value.render().hint.orEmpty().contains("请为当前对象建立知识库")
        )

        coEvery { read.getActive() } returns KnowledgeBase(name = "   ", turnCount = 5, topicCount = 5)
        val blank = newHomeStatusViewModel(model, read, gateway)
        blank.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(
            "库名空白 = 无效引用，有计数也不算有效库",
            listOf(AdvisorMissing.NoKnowledgeBase), kbSays(blank.status.value)
        )
    }

    /**
     * 切到另一张**空**库：新库照样算已建立（§7.4 同一条），但身份里库名换了 →
     * 旧连接结论作废，且**不补发**探针。
     * 反例：空库判据还在 → 第一句 `kbSays` 红；反例：库名不进身份位 →
     * `ConnectionUnchecked` 不出现，第二句红；反例：返回路径自己补检查 → `coVerify` 红。
     */
    @Test
    fun `switching to another empty library keeps it built but voids the old verdict`() = runTest {
        val model = configuredStore()
        coEvery { gateway.testConnectionWithProbe(any(), any(), any()) } returns
            ConnectionTestResult(success = true)
        coEvery { read.getActive() } returns KnowledgeBase(name = "她", turnCount = 0, topicCount = 0)
        val vm = newHomeStatusViewModel(model, read, gateway)
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(emptyList<AdvisorMissing>(), kbSays(vm.status.value))
        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }

        coEvery { read.getActive() } returns KnowledgeBase(name = "另一个人", turnCount = 0, topicCount = 0)
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()
        assertEquals("新的空库同样算已建立，不许念建库", emptyList<AdvisorMissing>(), kbSays(vm.status.value))
        assertTrue(
            "切库后旧结论必须作废，实到缺项 ${vm.status.value.missing}",
            vm.status.value.missing.contains(AdvisorMissing.ConnectionUnchecked)
        )
        assertNotEquals(AdvisorState.RunningReady, vm.status.value.state)
        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }
    }

    /**
     * 活动库被删：重读回 `Missing`，身份位没了 → 旧结论作废，永远不许留在绿档。
     * 反例：删除后 publish 仍吃旧快照（不重读）→ 第一句红；
     * 反例：作废只写在 runCheck → 第二句红；反例：绿不认身份 → 第三句红。
     */
    @Test
    fun `deleting the active library voids the verdict and asks to build again`() = runTest {
        val model = configuredStore()
        coEvery { gateway.testConnectionWithProbe(any(), any(), any()) } returns
            ConnectionTestResult(success = true)
        coEvery { read.getActive() } returns KnowledgeBase(name = "她", turnCount = 0, topicCount = 0)
        val vm = newHomeStatusViewModel(model, read, gateway)
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(emptyList<AdvisorMissing>(), kbSays(vm.status.value))

        coEvery { read.getActive() } returns null
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(
            "库删了要重新念建库那一句", listOf(AdvisorMissing.NoKnowledgeBase), kbSays(vm.status.value)
        )
        assertTrue(
            "库删了旧结论必须作废，实到缺项 ${vm.status.value.missing}",
            vm.status.value.missing.contains(AdvisorMissing.ConnectionUnchecked)
        )
        assertNotEquals("库删了永远不许是绿的", AdvisorState.RunningReady, vm.status.value.state)
        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }
    }

    /**
     * 只有空库的机器上切供应商：作废与补发纪律和原有那格一致——
     * **修空库误报不许让任何一格亮绿灯**（§7.4 末段与 F4 的守卫）。
     * 反例：presence 变 Present 被当成点绿条件 → `ConnectionUnchecked`/非绿两句一起红；
     * 反例：切供应商不作废 → 第一句红；反例：返回补发付费请求 → `coVerify` 红。
     */
    @Test
    fun `switching provider over an empty library voids the old verdict without turning green`() = runTest {
        val model = configuredStore()
        coEvery { gateway.testConnectionWithProbe(any(), any(), any()) } returns
            ConnectionTestResult(success = true)
        coEvery { read.getActive() } returns KnowledgeBase(name = "她", turnCount = 0, topicCount = 0)
        val vm = newHomeStatusViewModel(model, read, gateway)
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(emptyList<AdvisorMissing>(), kbSays(vm.status.value))

        val other = ProviderTicket("t-other", "Kimi", "https://api.other.example", model = "kimi-k2")
        model.setWorkerTickets(listOf(ticket, other))
        model.activeTicketId = other.id
        model.saveWorkerApiKey(other.id, "sk-other-key")
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()
        assertTrue(
            "换供应商后旧结论必须作废，实到缺项 ${vm.status.value.missing}",
            vm.status.value.missing.contains(AdvisorMissing.ConnectionUnchecked)
        )
        assertEquals("空库这一格不许顺手红成建库", emptyList<AdvisorMissing>(), kbSays(vm.status.value))
        assertTrue(
            "不许因为修空库错误就点绿", vm.status.value.state != AdvisorState.RunningReady
        )
        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }
    }

    /**
     * 库还没读到（冷启动加载顺序里目录读取在路上）：这一刻只有"正在检查…"，
     * 不许抢跑念出"建库"或"读不到"；读回来后空库按 Present 落地，全程只发过一次探针。
     * 反例：把在飞读取当失败（走 Unknown 抢答"还没读到"以外的话/把建库念出来）→ hint 相等句红；
     * 反例：读取期间就把探针发出去 → `coVerify(exactly = 0)` 红；
     * 反例：读回来后空库又被判 Missing → 最后一句红。
     */
    @Test
    fun `a kb read still in flight says checking and settles as built without extra probes`() = runTest {
        val model = configuredStore()
        coEvery { gateway.testConnectionWithProbe(any(), any(), any()) } returns
            ConnectionTestResult(success = true)
        val gate = CompletableDeferred<KnowledgeBase>()
        coEvery { read.getActive() } coAnswers { gate.await() }

        val vm = newHomeStatusViewModel(model, read, gateway)
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals("库没读到之前不许出建库那一句，只许停在检查中", AdvisorState.Checking, vm.status.value.state)
        assertEquals(
            "在飞阶段的黄字只有一句'正在检查…'", "正在检查…", vm.status.value.render().hint
        )
        coVerify(exactly = 0) { gateway.testConnectionWithProbe(any(), any(), any()) }

        gate.complete(KnowledgeBase(name = "她", turnCount = 0, topicCount = 0))
        advanceUntilIdle()
        assertEquals("读回来的空库算已建立", emptyList<AdvisorMissing>(), kbSays(vm.status.value))
        coVerify(exactly = 1) { gateway.testConnectionWithProbe(any(), any(), any()) }
        assertTrue(
            "JVM 上服务起不来，这一格永远不许绿", vm.status.value.state != AdvisorState.RunningReady
        )
    }
}
