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
import com.lovebrain.app.ui.home.render
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
         * `the probe uses the stored key…` 那格的 `UncaughtExceptionsBeforeTest`：
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

    // ═══════════ 3. 空库不算已建知识库（主线程 2026-10-03 拍）═══════════

    /**
     * 三格一起摆：有内容 → 不念建库那一句；两个计数都是 0 → 念"请为当前对象建立知识库"；
     * 读端口抛异常 → 念"还没读到"，**不许**把"读不动"报成"没有"骗用户重建。
     *
     * 反例：`getActive() != null` 就算 Present（上一版就是这么写的）→ 第二格红；
     * 反例：异常吞成 Missing → 第三格念错话；
     * 反例：只认 turnCount → 有归档批次、但一轮都没提交的那一格会被当成空库（第一格②拦着）。
     */
    @Test
    fun `empty library is not a built knowledge base while unreadable is not missing`() = runTest {
        val model = configuredStore()
        coEvery { gateway.testConnectionWithProbe(any(), any(), any()) } returns
            ConnectionTestResult(success = true)

        // ① 空库：目录建好了、一格正文都没积累过（新建库的 kb.json 就是这个形状）
        coEvery { read.getActive() } returns KnowledgeBase(name = "她", turnCount = 0, topicCount = 0)
        val empty = newHomeStatusViewModel(model, read, gateway)
        empty.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(
            "空库要说建库那一句，而不是'读不到'",
            listOf(AdvisorMissing.NoKnowledgeBase),
            empty.status.value.missing.filter {
                it == AdvisorMissing.NoKnowledgeBase || it == AdvisorMissing.KnowledgeUnread
            }
        )
        val hint = empty.status.value.render().hint.orEmpty()
        assertTrue("黄字要念得出这一句，实到「$hint」", hint.contains("请为当前对象建立知识库"))

        // ② 只有归档批次数（turnCount 会随提交轮数走，不能拿它当唯一读数）
        coEvery { read.getActive() } returns KnowledgeBase(name = "她", turnCount = 0, topicCount = 1)
        val lessonsOnly = newHomeStatusViewModel(model, read, gateway)
        lessonsOnly.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertTrue(
            "积累过就不要念建库那一句，实到 ${lessonsOnly.status.value.missing}",
            !lessonsOnly.status.value.missing.contains(AdvisorMissing.NoKnowledgeBase)
        )

        // ③ 读不动 ≠ 没有
        coEvery { read.getActive() } throws IllegalStateException("disk gone")
        val unreadable = newHomeStatusViewModel(model, read, gateway)
        unreadable.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(
            listOf(AdvisorMissing.KnowledgeUnread),
            unreadable.status.value.missing.filter {
                it == AdvisorMissing.NoKnowledgeBase || it == AdvisorMissing.KnowledgeUnread
            }
        )
        assertTrue(
            "读不动这一格永远不许绿", unreadable.status.value.state != AdvisorState.RunningReady
        )
    }
}
