package com.lovebrain.app.viewmodel

import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.ui.home.AdvisorControl
import com.lovebrain.app.ui.home.AdvisorLamp
import com.lovebrain.app.ui.home.AdvisorMissing
import com.lovebrain.app.ui.home.AdvisorState
import com.lovebrain.app.ui.home.FakeHomeKnowledge
import com.lovebrain.app.ui.home.FakeHomeProbe
import com.lovebrain.app.ui.home.FakeHomeProvider
import com.lovebrain.app.ui.home.FakeHomeService
import com.lovebrain.app.ui.home.render
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 首页那盏灯的派生表与状态机（）。
 *
 * 三条纪律各自有格钉着，每格都写明"什么反例会让它红"：
 * 1. 绿只认真凭据：连接成功 + 服务在跑 + 当前对象有知识库 + 权限在（本地有 Key 只算配置存在）；
 * 2. 关掉之后回来的响应不许把灯再点绿（token + `userStarted` 两道）；
 * 3. **探针只由按 ▶ 发起**：冷启动、重组、脉冲、进页面、切供应商/切当前对象都只重读与作废，
 *    一次都不许多发（`probe.calls` 就是这条的读数）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeStatusViewModelTest {

    // ═══════════ 夹具（四颗哑端口，共用件在 ui/home/HomeStatusFakes.kt）═══════════

    private val service = FakeHomeService()
    private val provider = FakeHomeProvider(ref = null)
    private val knowledge = FakeHomeKnowledge()
    private val probe = FakeHomeProbe()

    private fun newVm(): HomeStatusViewModel = HomeStatusViewModel(service, provider, knowledge, probe)

    private fun ref(id: String = "t1", model: String = "deepseek-chat", hasKey: Boolean = true) =
        HomeProviderRef(id = id, name = "DeepSeek", model = model, hasKey = hasKey)

    /** 全部齐的那一格：权限在、配置在、库在、服务在跑、探针成功 */
    private fun allGood() {
        provider.ref = ref()
        knowledge.presence = HomeKnowledgePresence.Present
        knowledge.kbName = "她"
        service.running = true
        probe.outcome = HomeProbeOutcome.Connected
    }

    @Before
    fun setUp() {
        // viewModelScope 走 Dispatchers.Main.immediate：把 Main 换成不排队的那一档，
        // 于是"点开始 → 探针挂起 → gate 完成 → 状态落定"全在调用点同步走完，
        // 用例控的是 gate 什么时候响，而不是"睡一会儿再看"（后者是时序赌博）。
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ═══════════ 1. 齐 + 检查成功 → 绿（这份夹具里压根没有"捕获辅助权限"这个输入）═══════════

    /**
     * 反例（会让这一格红的坏实现）：
     * - 只看"本地有 Key"就点绿 → 这一格绿不了，因为探针得先被真的调一次；
     * - 探针成功但服务没起来就点绿 → `service.running = true` 那一条拦着；
     * - 把可选的捕获辅助权限也列进缺项 → 这里没有任何捕获相关的输入，缺项清单却不是空。
     */
    @Test
    fun `green needs a real successful probe plus running service plus current object kb`() = runTest {
        allGood()
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()

        val status = vm.status.value
        assertEquals(AdvisorState.RunningReady, status.state)
        assertEquals(AdvisorLamp.Green, status.render().lamp)
        assertEquals(AdvisorControl.Stop, status.render().control)
        assertNull("绿灯那一档不许多余解释", status.render().hint)
        assertEquals("一次开始只发一个微请求", 1, probe.calls)
        assertTrue("齐的时候不该念出任何缺项", status.missing.isEmpty())
    }

    /** 本地有 Key（配置存在）但连接失败：仍然不绿，且黄字念的是那一条 */
    @Test
    fun `having a key is not the same as a working connection`() = runTest {
        allGood()
        probe.outcome = HomeProbeOutcome.Unreachable
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()

        val status = vm.status.value
        assertNotEquals("有 Key 就点绿 = 把'配置存在'当成'连接成功'", AdvisorState.RunningReady, status.state)
        assertEquals(AdvisorState.RunningNeedsSetup, status.state)
        assertEquals(listOf(AdvisorMissing.ConnectionFailed), status.missing)
        assertEquals("连接失败，请检查Key或地址", status.render().hint)
        assertEquals(AdvisorLamp.Yellow, status.render().lamp)
    }

    // ═══════════ 2. checking 期间关闭：晚到的响应不能把灯再点绿 ═══════════

    /**
     * 反例：
     * - 响应回来无条件写状态（没有 token 比对）→ 关掉之后那一次成功会把灯点成绿/黄；
     * - 只比对 token 不比对 `userStarted` → 关掉再点开始之间的那一次旧响应还能改灯；
     * - `stopClicked` 不推 token → 旧 job 的续体照样通过检查。
     */
    @Test
    fun `a response that lands after stop cannot light the lamp again`() = runTest {
        allGood()
        probe.gate = CompletableDeferred()          // 让探针停在半路
        val vm = newVm()

        vm.playClicked(overlayGranted = true)
        assertEquals("正在检查就是黄 + ■", AdvisorState.Checking, vm.status.value.state)
        assertEquals(AdvisorLamp.Yellow, vm.status.value.render().lamp)
        assertEquals(AdvisorControl.Stop, vm.status.value.render().control)

        vm.stopClicked()
        assertEquals(AdvisorState.Stopped, vm.status.value.state)
        assertEquals(AdvisorLamp.Red, vm.status.value.render().lamp)
        assertEquals(AdvisorControl.Play, vm.status.value.render().control)
        assertNull("红档没有常驻解释", vm.status.value.render().hint)
        assertEquals("点方块要真的把服务停掉", 1, service.stopCount)

        probe.gate!!.complete(Unit)                 // 迟到的成功响应此刻才回来
        advanceUntilIdle()

        val late = vm.status.value
        assertEquals("晚到的响应把已经关掉的灯又点亮了", AdvisorState.Stopped, late.state)
        assertEquals(AdvisorLamp.Red, late.render().lamp)
        assertEquals(AdvisorControl.Play, late.render().control)
        assertEquals("关掉了不许偷偷补一次检查", 1, probe.calls)
    }

    /** 检查还没回来就换了供应商：那一条响应属于旧身份，同样不能算数 */
    @Test
    fun `a response for a stale provider identity is dropped`() = runTest {
        allGood()
        probe.gate = CompletableDeferred()
        val vm = newVm()
        vm.playClicked(overlayGranted = true)

        provider.ref = ref(id = "t2", model = "other-model")   // 在路上的那一次属于 t1
        probe.gate!!.complete(Unit)
        advanceUntilIdle()

        assertNotEquals(
            "旧身份的探针结论记在新身份头上 = 没检查过却说检查过",
            AdvisorState.RunningReady, vm.status.value.state
        )
        assertTrue(
            "当前身份没有真凭据时必须念出'连接还没检查过'这一条，实到缺项：" + vm.status.value.missing,
            vm.status.value.missing.contains(AdvisorMissing.ConnectionUnchecked)
        )
    }

    // ═══════════ 3. 切供应商 / 切当前知识库使旧检查失效 ═══════════

    /**
     * 反例：`publish()` 不看身份、只记"上次成功过" → 换供应商之后这一格照样报绿，
     * 而那台新供应商根本没被验过（用户看到的绿是假的）。
     */
    @Test
    fun `changing provider invalidates the previous check`() = runTest {
        allGood()
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(AdvisorState.RunningReady, vm.status.value.state)

        // 只推一次"服务状态脉冲"（不发请求、不点重新检查），旧结论必须当场作废
        provider.ref = ref(model = "another-model")
        service.pulseNow()
        advanceUntilIdle()

        assertNotEquals("换模型后旧检查还撑着绿灯", AdvisorState.RunningReady, vm.status.value.state)
        assertEquals(AdvisorLamp.Yellow, vm.status.value.render().lamp)
        assertEquals("身份变了不该另发一次请求，只作废就行", 1, probe.calls)
    }

    /**
     * 从子页回来 / 进页面：**一个探针都不发**，身份变了也只是作废旧结论。
     *
     * 这一格钉的是本轮改掉的那条行为：上一版 `returnedFromSubpage` 在"当前身份与上次结论
     * 不是同一组"时会**自己补一次检查**，于是从供应商页切完回来、或从知识库页切完当前对象回来，
     * 在用户没有按 ▶ 的情况下真发了一次付费请求。合同那句"不每次重组发微请求 + 下次开始再检查"
     * 判的就是这个。
     *
     * 反例（坏实现怎么把这格弄红）：
     * - 把 `runCheck()` 留在 `returnedFromSubpage` 里 → 换对象那一步 `probe.calls` 变 2；
     * - 在 `init` 里补一次检查 → 第一段那句 `assertEquals(0, probe.calls)` 当场红；
     * - 作废只做在 `runCheck` 里、`publish` 不比身份 → 换完当前对象这一格还会报绿。
     */
    @Test
    fun `entering the page never sends a probe and only voids a stale conclusion`() = runTest {
        allGood()
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(AdvisorState.RunningReady, vm.status.value.state)
        assertEquals(1, probe.calls)

        repeat(3) { vm.returnedFromSubpage(overlayGranted = true) }
        advanceUntilIdle()
        assertEquals("身份没变的刷新不许再发微请求", 1, probe.calls)
        assertEquals(AdvisorState.RunningReady, vm.status.value.state)

        // 换当前对象 = 新身份：旧的那点成功不再替它说话，但也**不补发**
        knowledge.kbName = "另一个人"
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()
        assertEquals("切当前对象不许自动发探针（下一次开始才检查）", 1, probe.calls)
        assertNotEquals(AdvisorState.RunningReady, vm.status.value.state)
        assertEquals(listOf(AdvisorMissing.ConnectionUnchecked), vm.status.value.missing)
    }

    /** 切供应商同一件事：当场作废，重新检查要等用户真的再按一次 ▶ */
    @Test
    fun `switching provider invalidates now and rechecks only on the next start`() = runTest {
        allGood()
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(AdvisorState.RunningReady, vm.status.value.state)

        provider.ref = ref(id = "t9", model = "brand-new")
        probe.outcome = HomeProbeOutcome.Unreachable
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()

        assertEquals("进页面不该发请求", 1, probe.calls)
        assertEquals(listOf(AdvisorMissing.ConnectionUnchecked), vm.status.value.missing)

        // 用户自己重新走一遍开始：这一次真的去检查，检查失败就念失败
        vm.stopClicked()
        vm.playClicked(overlayGranted = true)
        // ▶ 那一档在合同里是"先检查悬浮权限…、**启动必要服务**"，而启动服务不归这颗 VM：
        // `playClicked` 的注释与 `HomeScreen.onPlay` 都把它交给平台那条 `onStartService`
        // （`SetupActivity` 才认得授权页回来要续跑）。■ 已经把服务停了，所以这一格要把
        // "平台把服务又起回来了"这颗事实交回夹具，否则测的是生产到不了的那一态。
        // ⚠ 这不是把判据放松：服务真没起来时 `ServiceNotRunning` 必须自己念出来，
        //    那一条由 `service that never came up is its own missing step` 单独钉着。
        service.markRunning()
        advanceUntilIdle()
        assertEquals(2, probe.calls)
        assertEquals(AdvisorState.RunningNeedsSetup, vm.status.value.state)
        assertEquals(listOf(AdvisorMissing.ConnectionFailed), vm.status.value.missing)
    }

    /**
     * 冷启动：服务在跑（上一趟遗留 / 面板那条路起的）、配置看着齐、库也在——也**不许**自己发探针。
     *
     * 反例：`init` 里 "顺手" 检查一次 ⇒ 每次进设置页都烧一次调用，`probe.calls` 从 0 变 1；
     * 反例：冷启动直接给绿 ⇒ 这一格读不到 `ConnectionUnchecked` 也读得到"绿"，两条都拦着。
     *
     * ⚠ "进页面"在这颗 VM 上是有主人的一个动作，不是"构造完就算"：`HomeScreen` 在
     * `LaunchedEffect(Unit)` 与 ON_RESUME 各调一次 [HomeStatusViewModel.returnedFromSubpage]
     * （见 `ui/home/HomeScreen.kt` 那两处），悬浮权限的真实读数也只有这一条路进得来
     * （VM 自己没有权限端口，`playClicked` 是另一条）。上一版这一格只 `newVm()` 就要求
     * 缺项恰好等于 `[连接还没检查成功]`，那是把"事实还没读"当成"事实读坏了"来判：
     * 实到的是 `[权限, 还没读到知识库, 连接还没检查成功]`——三句都是**当时的真读数**
     * （没人告诉过它权限、也没人叫它读过库），红的是判据走错了入口，不是派生表。
     * 现在这一格把那条入口动作真的走一遍，因此它同时还在判"读事实的那一步不发请求"。
     */
    @Test
    fun `cold start with a running service reads facts but sends no probe`() = runTest {
        allGood()                      // 供应商 + 库 + 服务在跑 + 探针会成功
        val vm = newVm()
        advanceUntilIdle()

        assertEquals("装配本身不该发请求", 0, probe.calls)
        // 只造好 VM、还没进过页面：这一刻 VM 手里只有"服务在跑"这一颗事实，
        // 权限与知识库两颗都还没读（`reads == 0`），所以缺项是三条真读数而不是错误。
        // 这几句就是上一版那条判据为什么会红的全部原因——它在这里要求缺项恰好等于一条。
        assertEquals("没进页面就没有读过库", 0, knowledge.reads)
        assertNotEquals("光装配不许点绿", AdvisorState.RunningReady, vm.status.value.state)
        assertEquals(
            listOf(AdvisorMissing.OverlayPermission, AdvisorMissing.KnowledgeUnread, AdvisorMissing.ConnectionUnchecked),
            vm.status.value.missing
        )

        // 这一句就是"进页面"：HomeScreen 冷启动/ON_RESUME 走的同一条
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()

        assertEquals("进页面读事实也不该发请求", 0, probe.calls)
        assertTrue("这一条没真的读过知识库，那'reads facts'是空的", knowledge.reads > 0)
        assertNotEquals("没检查过就点绿 = 假绿", AdvisorState.RunningReady, vm.status.value.state)
        assertEquals(listOf(AdvisorMissing.ConnectionUnchecked), vm.status.value.missing)
        assertEquals(AdvisorControl.Stop, vm.status.value.render().control)
        assertEquals(AdvisorLamp.Yellow, vm.status.value.render().lamp)
        assertEquals("没按开始也不许把服务停掉", 0, service.stopCount)
    }

    // ═══════════ 3'. 检查一次、结论按身份持续有效（用户原话 2026-10-05「我们只检查一次可以吗」）═══════════

    /**
     * 修"重进屏幕灯落回黄"：Activity 销毁 → VM 重建 → ledger 整本空 → 落盘副本
     * （`store.connectionVerified`）兜底交回 [HomeConnectionVerdict.Verified]，
     * 不把已经绿过的灯落回 NotChecked（黄）。
     *
     * 反例（修前形状）：ledger 纯内存、随 VM 生灭 → 新 VM 的 `connectionFor` 交回 NotChecked → 黄。
     * 反例（兜底太宽）：身份变化也查盘 → 换供应商后旧结论替没验过的新身份说话
     *   （`a genuinely new identity is unchecked…` 那一格红）。
     * 反例（落盘没写）：`runCheck` 不写盘 → store.connectionVerified 恒 false → 这一格红。
     *
     * 模拟"重进屏幕"：全新 VM + `returnedFromSubpage`，不按 ▶、不发探针，灯仍然绿。
     */
    @Test
    fun `a recreated VM reads the persisted verdict instead of resetting to not checked`() = runTest {
        allGood()
        val store = InMemorySettingsStore().apply { connectionVerified = true }
        val vm = HomeStatusViewModel(service, provider, knowledge, probe, store)

        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()

        assertEquals(
            "落盘副本在 ledger 空时兜底，重进屏幕不落回 NotChecked",
            AdvisorState.RunningReady, vm.status.value.state
        )
        assertTrue(
            "不该念连接还没检查过，实到缺项 ${vm.status.value.missing}",
            !vm.status.value.missing.contains(AdvisorMissing.ConnectionUnchecked)
        )
        assertEquals("重进屏幕不该发探针", 0, probe.calls)
    }

    /**
     * ② 同身份下**已验证结论不许变黄**：切出去再切回来时知识库那一格"读不动"，
     * 那不是用户换了对象，所以连接结论必须原样留着；黄字只念"还没读到知识库"这一句真话。
     * 读回来之后当场是绿，全程 `probe.calls` 还是 1。
     *
     * 反例（把改动回退成什么样它会红）：
     * - 回退成"身份那一位直接吃 `snapshot.name`"（上一版）：Unknown 那一次库名是 null ⇒
     *   身份漂成 `t1|deepseek-chat|-`，第一段"缺项恰好等于 KnowledgeUnread"当场红
     *   （多出一条 ConnectionUnchecked），而且第三段永远回不到绿——旧凭据被那一格唯一的槽挤掉了，
     *   两处一起红，而这正是用户看到的"绿 → 切回来 → 黄"；
     * - 回退成"返回路径自己补一次检查"（更早那一版）：`assertEquals(1, probe.calls)` 变 2 红。
     */
    @Test
    fun `an unreadable kb read does not void a verified connection`() = runTest {
        allGood()
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals("起点是绿", AdvisorState.RunningReady, vm.status.value.state)

        knowledge.presence = HomeKnowledgePresence.Unknown
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()

        assertEquals("读不动不是换对象，一次钱都不许多花", 1, probe.calls)
        assertEquals(
            "这一格唯一的真缺项是「知识库读不到」，不许再捎上连接那一句，实到 ${vm.status.value.missing}",
            listOf(AdvisorMissing.KnowledgeUnread), vm.status.value.missing
        )

        knowledge.presence = HomeKnowledgePresence.Present
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()
        assertEquals("读回同一组身份，旧凭据当场取回", AdvisorState.RunningReady, vm.status.value.state)
        assertEquals(1, probe.calls)
    }

    /**
     * ①+④ 右侧是 ■ 的那整段时间（灯绿着、服务在跑）：回前台、返回子页、窗口态脉冲
     * **一次都不许多发探针**，也不许把已验证结论弄下来。
     * 反例：把检查写回 `returnedFromSubpage` 或 `init` ⇒ `probe.calls` 从 1 变 2 红；
     * 反例：结论不绑身份 ⇒ 这一格不红，但下面那格（换新身份必须作废）与上面那格会红。
     */
    @Test
    fun `running under the stop square never sends another probe`() = runTest {
        allGood()
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(AdvisorControl.Stop, vm.status.value.render().control)

        repeat(4) {
            vm.returnedFromSubpage(overlayGranted = true)   // 切出去再切回来 / 从子页返回
            service.pulseNow()                              // 回前台那类"窗口态漂了一下"的脉冲
            advanceUntilIdle()
        }
        assertEquals("■ 期间一次都不许多发", 1, probe.calls)
        assertEquals(AdvisorState.RunningReady, vm.status.value.state)
        assertNull("绿档不许多余解释", vm.status.value.render().hint)
    }

    /**
     * ③ 身份**真的**变了必须作废重检（这一条不许放松成"永远绿"）：换供应商算出一组新身份，
     * 账上没有它，必须如实念"连接还没检查过"、不许绿；只有切回原来那家才取回旧凭据。
     * 反例：结论不绑身份（只记"上次成功过"）⇒ 换过去那一段 `assertNotEquals` 红，
     *   而那家新供应商根本没被验过，用户看到的绿是假的；
     * 反例：把"作废"写成"抹掉旧账"（旧身份的记录一起清）⇒ 切回来那一段红，
     *   而且还要再烧一次钱，`assertEquals(1, probe.calls)` 同样拦着。
     */
    @Test
    fun `a genuinely new identity is unchecked while the old one stays restorable`() = runTest {
        allGood()
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(AdvisorState.RunningReady, vm.status.value.state)

        provider.ref = ref(id = "t2", model = "kimi-k2")     // 换供应商：身份两位一起变
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()
        assertNotEquals("没验过的那家不许还挂着绿", AdvisorState.RunningReady, vm.status.value.state)
        assertEquals(listOf(AdvisorMissing.ConnectionUnchecked), vm.status.value.missing)

        provider.ref = ref()                                 // 切回原来那家：同一组身份
        vm.returnedFromSubpage(overlayGranted = true)
        advanceUntilIdle()
        assertEquals("旧凭据按身份取回", AdvisorState.RunningReady, vm.status.value.state)
        assertEquals("取回结论不该再花一次钱", 1, probe.calls)
    }

    /**
     * **只有按 ■ 才清账**（那是用户主动说"这次不算了"），清完下一次按 ▶ 真的重检一次。
     * 反例：`stopClicked` 不清账 ⇒ 这里 `probe.calls` 停在 1 红，用户明确重开却看不到新的检查
     *   （合同"下次开始再检查"）；
     * 反例：把清账也写进 `returnedFromSubpage` ⇒ 上面两格（不作废 / 取回）当场红。
     */
    @Test
    fun `only stopping clears the ledger so the next start really checks`() = runTest {
        allGood()
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals(1, probe.calls)

        vm.stopClicked()
        // ■ 之后重开：平台那条链会把服务起回来（与 `switching provider invalidates now…` 同一手法，
        // 不是把判据放松——服务真没起来时 ServiceNotRunning 必须自己念出来）
        service.markRunning()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()
        assertEquals("关掉再开 = 用户要重检，这一次必须真的发", 2, probe.calls)
        assertEquals(AdvisorState.RunningReady, vm.status.value.state)
    }

    /**
     * 账本本体：按身份记、按身份取、只有 `clear` 抹掉全部。
     * 反例：把 `remember` 退化成"只留最后一次"（上一版那一格可空槽的形状）⇒
     *   第二段 `recall("t1|m|她")` 取回 null 红，而"切回来变黄"就从这个红里长出来。
     */
    @Test
    fun `the ledger remembers one verdict per identity`() {
        val ledger = HomeConnectionLedger()
        ledger.remember("t1|m|她", HomeConnectionVerdict.Verified)
        ledger.remember("t2|m|她", HomeConnectionVerdict.Failed)
        assertEquals(HomeConnectionVerdict.Verified, ledger.recall("t1|m|她"))
        assertEquals(HomeConnectionVerdict.Failed, ledger.recall("t2|m|她"))
        assertNull("没记过的身份交回 null，调用方才能老实说没检查过", ledger.recall("t3|m|她"))
        assertEquals(2, ledger.remembered)
        ledger.clear()
        assertEquals(0, ledger.remembered)
        assertNull(ledger.recall("t1|m|她"))
    }

    /**
     * 「谎报」那一格单独结清：这一档的实话是"从没检查过"，不是"检查失败"。
     * 反例：把话改回「连接还没检查成功」——它落在"…成功"的否定式里，用户读成"检查失败了"
     *   （本轮投诉的原话），第一句逐字红；反例：改成带"失败"的任何话 ⇒ 第二句红。
     * ⚠ 这条改的是**同一条字面量的文本**：enum 构造参不在 `UiStringLiteralBudgetTest`
     *   四把尺的锚点射程里（EX-C2 那族 Context-free 结构性例外的登记原文），
     *   所以既没有新增中文字面量、也不是换桶，预算读数一动不动。
     */
    @Test
    fun `the unchecked verdict says a neutral fact and never a failure`() {
        val label = AdvisorMissing.ConnectionUnchecked.label
        assertEquals("连接还没检查过", label)
        assertTrue("没检查过不许念成失败，实到「$label」", !label.contains("失败"))
        assertTrue("也不许用「…成功」的否定式，实到「$label」", !label.contains("成功"))
    }

    // ═══════════ 4. 缺项与状态一一对应（配置不齐时连请求都不发）═══════════

    /** 没配供应商：不烧钱发请求，黄字念"未配置模型供应商"，而且是黄不是红 */
    @Test
    fun `missing provider shows its own line and sends no request`() = runTest {
        allGood()
        provider.ref = null
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()

        assertEquals("配置不齐就发请求 = 白烧一次调用", 0, probe.calls)
        assertEquals(listOf(AdvisorMissing.NoProvider), vm.status.value.missing)
        assertEquals("未配置模型供应商", vm.status.value.render().hint)
    }

    /** 当前对象没有知识库：探针成功也不绿，念的是建库那一句 */
    @Test
    fun `probe success without a kb for the current object is not green`() = runTest {
        allGood()
        knowledge.presence = HomeKnowledgePresence.Missing
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()

        assertEquals(AdvisorState.RunningNeedsSetup, vm.status.value.state)
        assertEquals(listOf(AdvisorMissing.NoKnowledgeBase), vm.status.value.missing)
        assertEquals("请为当前对象建立知识库", vm.status.value.render().hint)
    }

    /** 没给悬浮窗权限：只念根因那一条，不把"服务没起来"再抄一遍 */
    @Test
    fun `missing overlay permission states the cause once`() = runTest {
        allGood()
        service.running = false
        val vm = newVm()
        vm.playClicked(overlayGranted = false)
        advanceUntilIdle()

        val missing = vm.status.value.missing
        assertTrue("缺项里要看见权限这一条，实到 $missing", missing.contains(AdvisorMissing.OverlayPermission))
        assertTrue("根因说过就不该再念重复的", !missing.contains(AdvisorMissing.ServiceNotRunning))
        assertEquals(AdvisorState.RunningNeedsSetup, vm.status.value.state)
    }

    /** 权限给了但服务没起来：这一条要说出来，因为用户看到的确实是"灯黄着、点不动" */
    @Test
    fun `service that never came up is its own missing step`() = runTest {
        allGood()
        service.running = false
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()

        assertTrue(
            "缺项里要看见服务这一条，实到 " + vm.status.value.missing,
            vm.status.value.missing.contains(AdvisorMissing.ServiceNotRunning)
        )
        assertEquals(AdvisorLamp.Yellow, vm.status.value.render().lamp)
    }

    /** 知识库读不到 ≠ 没有：念"还没读到"，不骗用户去重建 */
    @Test
    fun `unreadable kb is not reported as missing kb`() = runTest {
        allGood()
        knowledge.presence = HomeKnowledgePresence.Unknown
        val vm = newVm()
        vm.playClicked(overlayGranted = true)
        advanceUntilIdle()

        assertEquals(listOf(AdvisorMissing.KnowledgeUnread), vm.status.value.missing)
        assertEquals("还没读到当前对象的知识库", vm.status.value.render().hint)
        assertNotEquals(AdvisorState.RunningReady, vm.status.value.state)
    }

    // ═══════════ 5. 派生矩阵：状态 × 渲染产物（带哨兵）═══════════

    /** 一行 = 一组事实 + 它该派生出的四件产物。期望值手写，不是让判据自己算自己 */
    private data class Row(
        val name: String,
        val facts: HomeFacts,
        val state: AdvisorState,
        val lamp: AdvisorLamp,
        val control: AdvisorControl,
        val hint: String?
    )

    private val readyFacts = HomeFacts(
        userStarted = true, checking = false, overlayGranted = true, serviceRunning = true,
        provider = ref(), knowledge = HomeKnowledgePresence.Present, connection = HomeConnectionVerdict.Verified
    )

    private fun factsOf(
        started: Boolean = true,
        checking: Boolean = false,
        overlay: Boolean = true,
        running: Boolean = true,
        providerRef: HomeProviderRef? = ref(),
        kb: HomeKnowledgePresence = HomeKnowledgePresence.Present,
        connection: HomeConnectionVerdict = HomeConnectionVerdict.Verified
    ) = HomeFacts(started, checking, overlay, running, providerRef, kb, connection)

    private val rows: List<Row> = listOf(
        Row("从没点过开始、服务也没在跑", factsOf(started = false, running = false),
            AdvisorState.Stopped, AdvisorLamp.Red, AdvisorControl.Play, null),
        Row("检查中", factsOf(checking = true),
            AdvisorState.Checking, AdvisorLamp.Yellow, AdvisorControl.Stop, "正在检查…"),
        Row("全都齐", readyFacts,
            AdvisorState.RunningReady, AdvisorLamp.Green, AdvisorControl.Stop, null),
        Row("缺权限", factsOf(overlay = false, running = false),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop, "需要悬浮窗权限"),
        Row("缺供应商（不发请求）", factsOf(providerRef = null),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop, "未配置模型供应商"),
        Row("有供应商没 Key", factsOf(providerRef = ref(hasKey = false)),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop, "未配置模型供应商"),
        Row("没建库", factsOf(kb = HomeKnowledgePresence.Missing),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop, "请为当前对象建立知识库"),
        Row("库读不到", factsOf(kb = HomeKnowledgePresence.Unknown),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop, "还没读到当前对象的知识库"),
        Row("连不上", factsOf(connection = HomeConnectionVerdict.Failed),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop, "连接失败，请检查Key或地址"),
        Row("这一档还没检查过", factsOf(connection = HomeConnectionVerdict.NotChecked),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop, "连接还没检查过"),
        Row("服务没起来", factsOf(running = false, connection = HomeConnectionVerdict.NotApplicable, providerRef = null),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop,
            "未配置模型供应商 · 军师服务没起来"),
        Row("三条一起缺", factsOf(overlay = false, kb = HomeKnowledgePresence.Missing, connection = HomeConnectionVerdict.Failed),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop,
            "需要悬浮窗权限 · 请为当前对象建立知识库 · 连接失败，请检查Key或地址"),
        // 服务在别人手里起来了（面板那条路），首页没点过开始：不能算红，也不能算绿
        Row("没点过但服务在跑", factsOf(started = false, running = true, connection = HomeConnectionVerdict.NotChecked),
            AdvisorState.RunningNeedsSetup, AdvisorLamp.Yellow, AdvisorControl.Stop, "连接还没检查过")
    )

    /**
     * 矩阵本体：逐行比四件产物。
     * 反例：把某一档的灯色接错、或黄字与缺项清单脱钩（灯黄着却念上一档的话）——都会当场红。
     */
    @Test
    fun `each fact row derives exactly one render`() {
        assertTrue("矩阵只有 ${rows.size} 行，行数掉下来说明有分支没人摆", rows.size >= 12)
        var seenStates = setOf<AdvisorState>()
        var seenLamps = setOf<AdvisorLamp>()
        var seenControls = setOf<AdvisorControl>()
        rows.forEach { row ->
            val status = advisorStatusOf(row.facts)
            val render = status.render()
            assertEquals("${row.name}：状态", row.state, status.state)
            assertEquals("${row.name}：灯", row.lamp, render.lamp)
            assertEquals("${row.name}：控件", row.control, render.control)
            assertEquals("${row.name}：那一行黄字", row.hint, render.hint)
            seenStates += status.state; seenLamps += render.lamp; seenControls += render.control
        }
        // 哨兵：三件产物每一档都被矩阵走过一次，否则"逐行比对"可以是空的还报绿
        assertEquals("四档状态都要被走到", AdvisorState.values().toSet(), seenStates)
        assertEquals("三档灯都要被走到", AdvisorLamp.values().toSet(), seenLamps)
        assertEquals("两种控件都要被走到", AdvisorControl.values().toSet(), seenControls)
    }

    /**
     * 缺项 → 文案一一对应：每一条缺项都有自己的话，两条一起缺时用一行短分隔。
     * 反例：`describe` 只念第一条（后面几条被吞）、或者两条拼出同一句话。
     */
    @Test
    fun `every missing step has its own line and they never collide`() {
        val labels = AdvisorMissing.values().map { AdvisorMissing.describe(listOf(it)) }
        assertEquals("每条缺项都要有自己的话", AdvisorMissing.values().size, labels.size)
        assertEquals("两句不许撞车", labels.size, labels.distinct().size)
        labels.forEach { assertTrue("文案不许是空的：$labels", it.isNotBlank()) }
        val two = AdvisorMissing.describe(listOf(AdvisorMissing.NoProvider, AdvisorMissing.ConnectionFailed))
        assertEquals("未配置模型供应商 · 连接失败，请检查Key或地址", two)
        assertEquals("空清单就不该有那一行", "", AdvisorMissing.describe(emptyList()))
    }

    /**
     * 派生表覆盖检查：每一个 `AdvisorMissing` 都能被某组事实真的产出——
     * 反例：enum 里加一条却没有事实能走到它（黄字念不到，用户看不到那一步），这一格当场红。
     */
    @Test
    fun `every declared missing step is actually producible by some fact row`() {
        val producible = rows.flatMap { advisorMissingSteps(it.facts) }.toSet()
        val undelivered = AdvisorMissing.values().toSet() - producible
        assertTrue("这些缺项没有任何事实能产出（enum 与派生表脱钩）：$undelivered", undelivered.isEmpty())
    }

    /**
     * 绿档的唯一入口：缺项为空 ⇔ RunningReady，且任何"没真检查过"的组合都不为空。
     * 反例：`advisorStatusOf` 里给绿加一条 `else` 兜底 → 没验过的组合也会掉进绿。
     *
     * ⚠ `checking` 是盖在缺项表**之上**的一档，不是缺项表的一行：第7节第2条 那一表写的是
     * "已点击开始、正在检查或有缺项 → 黄 + ■"，检查中那一档灯黄、控件 ■，而那一刻
     * `advisorMissingSteps` 完全可以已经空了（权限、配置、库、连接四项都在路上齐着）。
     * 所以"缺项为空"是绿的**必要**条件而不是充分条件，判据要连着 `checking` 一起收：
     * 上一版把这格写成只看 `missing.isEmpty() && userStarted`，于是矩阵里"检查中"那一行
     * （其余事实全齐）被要求成绿——红的是这把尺自己，派生表和 第7节第2条 都是对的
     * （同一行的 `Checking` 由 `each fact row derives exactly one render` 逐行钉着，那一格是绿的）。
     *
     * 两条反向证人让"把 checking 排除掉"不是一张空白放行：
     * ① 矩阵里必须真的存在"缺项为空但正在检查"那一行，且它派生出的是 `Checking` 不是绿
     *    ——谁把 `f.checking ->` 那条挪到缺项判断之后，这一句当场红；
     * ② 必须真的存在"缺项为空、不在检查中"的那一行并派生出 `RunningReady`。
     */
    @Test
    fun `green appears only when nothing is missing`() {
        assertTrue("矩阵行数掉了", rows.isNotEmpty())
        rows.forEach { row ->
            val missing = advisorMissingSteps(row.facts)
            val status = advisorStatusOf(row.facts)
            val greenCandidate = missing.isEmpty() && row.facts.userStarted && !row.facts.checking
            if (greenCandidate) {
                assertEquals("${row.name}：缺项为空且不在检查中才许绿", AdvisorState.RunningReady, status.state)
            } else {
                assertNotEquals("${row.name}：绿得不该这么容易($missing, checking=${row.facts.checking})", AdvisorState.RunningReady, status.state)
            }
        }
        // 反向证人①：检查中 + 缺项为空这一格在矩阵里真的存在，并且它不是绿
        val checkingWithNothingMissing = rows.filter {
            it.facts.checking && advisorMissingSteps(it.facts).isEmpty()
        }
        assertTrue(
            "矩阵里没有'正在检查却已无缺项'那一行，上面那句排除就是空转",
            checkingWithNothingMissing.isNotEmpty()
        )
        checkingWithNothingMissing.forEach {
            assertEquals("${it.name}：检查中的那一格不许掉进绿", AdvisorState.Checking, advisorStatusOf(it.facts).state)
        }
        // 反向证人②：这一格里必须真的存在"缺项为空"那一行，否则上面那条循环可以恒真
        assertTrue(
            "矩阵里没有'全都齐'那一行，上面那条断言就是空的",
            rows.any { advisorMissingSteps(it.facts).isEmpty() && !it.facts.checking }
        )
    }
}
