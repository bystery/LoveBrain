package com.lovebrain.app.ui

import android.content.Context
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.viewmodel.GuideCursor
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 引导闸门与三条出口的落盘合同（用户原话第 14 条「引导一翻页就没了」；需求映射第 14 行；
 * 设计基线 v1.1 §6.8「完成状态一律派生自真实状态，废除提前 complete 与 `recreate()` 通道」）。
 *
 * 两把尺各管一件事：
 * - **源码那一把**钉住"通道断了"：旧写法（一次性 local val + 三条出口都 `completeOnboarding()+recreate()`）
 *   一回归就红，而它判不了外观；
 * - **行为那一把**拿真 `SetupViewModel` + `InMemorySettingsStore` 跑，判"盘上到底落成什么"，
 *   所以它钉的是状态而不是按钮。
 *
 * ⚠ 边界（照 TEAM_RULES §5 如实标）：这一组里 `accessibilityGranted` **永远读不到真值**——
 * 纯 JVM 下 `Settings.Secure.getString` 走 `isReturnDefaultValues=true` 交回 null，
 * 于是派生游标最远只能到 `ACCESSIBILITY`。"三步齐 → DONE 并把旧完成旗标补真"那一格
 * 由主线程的 `GuideCursorResolveTest`（纯函数）与本席的 `stopBeingGuided` 那两格合起来覆盖，
 * 真机上的完整走一遍仍是未验证项（见台账 impl-G1b）。
 */
class SetupActivityGuideTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val activitySource: String by lazy {
        SourceScan.maskComments(mainSourceOf("ui/SetupActivity.kt").readText())
    }

    private fun mainSourceOf(relative: String): File =
        File("src/main/java/com/lovebrain/app", relative).takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app", relative)

    // ───────────────────────── 仪器：把 VM 摆到指定事实 ─────────────────────────

    /** 一个"查得到但确实没有知识库"的 Context：isExistingUser 的第四条按无处理 */
    private fun contextWithoutKnowledge(): Context {
        val root = File(tmp.root, "files").apply { mkdirs() }
        File(root, "knowledge").apply { mkdirs() } // 空目录 = 没有知识库
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns root
        return ctx
    }

    private fun providerTicket(): ProviderTicket =
        ProviderTicket(name = "工单", baseUrl = "https://example.test/v1", model = "deepseek-chat")

    /**
     * 事实开关：供应商 / 披露同意。无障碍那一颗 JVM 里拿不到真值（见类顶那段边界说明）。
     */
    private fun store(
        providerReady: Boolean = false,
        disclosureConfirmed: Boolean = false,
        introSeen: Boolean = false,
        cursor: String = "",
        legacyCompleted: Boolean = false,
        generateCount: Int = 0
    ): InMemorySettingsStore {
        val store = InMemorySettingsStore()
        store.introSeen = introSeen
        store.guideCursor = cursor
        store.hasCompletedOnboarding = legacyCompleted
        store.totalGenerateCount = generateCount
        store.captureEnabled = true
        store.accessibilityDisclosureVersion = if (disclosureConfirmed) 999 else 0
        if (providerReady) {
            val ticket = providerTicket()
            store.setWorkerTickets(listOf(ticket))
            store.activeTicketId = ticket.id
            store.saveWorkerApiKey(ticket.id, "sk-not-empty")
        }
        return store
    }

    private fun vm(store: SettingsStorePort): SetupViewModel = SetupViewModel(
        store,
        mockk<DeepSeekRepository>(relaxed = true),
        null,
        // 第四格是 KnowledgePresencePort（"本机有没有知识库"）：null = 按"没有"处理，
        // 与端口化前 contextWithoutKnowledge()（空目录）的读数等价。
        null
    )

    // ─────────────────────── A 组：闸门与通道都断了（源码尺）───────────────────────

    /**
     * 旧写法的两根管子必须一根不剩。
     *
     * 回退成什么就红：有人把任一出口又接回 `completeOnboarding()` 或 `recreate()`——
     * 那就是"看过一遍向导 = 引导永久消失"，用户原话第 14 条当场复发。
     */
    @Test
    fun `the activity keeps no premature-complete or recreate channel`() {
        listOf("completeOnboarding(", "recreate(").forEach { banned ->
            assertFalse(
                "SetupActivity 里不该再有 `$banned`——三条出口各走 SetupViewModel 的语义 API，" +
                    "完成与否由 currentGuideCursor 派生（基线 §6.8）",
                activitySource.contains(banned)
            )
        }
    }

    /**
     * `setContent` 只许调一次（本仓库记过坑：同一颗 Activity 上调第二次会崩）。
     * 闸门改成状态驱动之后，这一条尤其要盯着：别有人为了"重新判一次引导"再调一遍。
     */
    @Test
    fun `setContent is still called exactly once`() {
        val calls = Regex("""\bsetContent\s*\{""").findAll(activitySource).count()
        assertEquals("SetupActivity 里 setContent 应恰好 1 处（闸门靠状态翻，不靠重装）", 1, calls)
    }

    /**
     * 闸门读的是新的两颗（`shouldShowIntro` + `currentGuideCursor`），旧的那颗一次性布尔不许回来。
     *
     * 回退成什么就红：有人把 `shouldShowOnboarding()` 捡回去——它一命中就把旧完成旗标补真，
     * 于是"点去配置"又变成"引导算完成"。
     */
    @Test
    fun `the gate reads introSeen and the cursor, not the legacy one-shot flag`() {
        assertTrue(
            "闸门应当经 shouldShowIntro() 决定介绍层在不在场",
            activitySource.contains("shouldShowIntro(")
        )
        assertTrue(
            "罩子的游标应当来自 currentGuideCursor(context)（派生自真状态）",
            activitySource.contains("currentGuideCursor(")
        )
        assertFalse(
            "shouldShowOnboarding() 是旧合同（只作 VM 侧兼容留在树上），Activity 不该再读它",
            activitySource.contains("shouldShowOnboarding(")
        )
    }

    /**
     * 罩子是首页之上的**覆盖层**，不是首页的替身：`HomeCoachMarks` 必须与 `SetupRoot`
     * 同在一棵 Box 里、且排在它后面（后画的才在上面）。
     *
     * 回退成什么就红：有人写成 `if (cursor != DONE) HomeCoachMarks(...) else SetupRoot(...)`
     * ——那正是"引导出现 = 主页没了"，与用户要的"主页面上覆盖几个箭头"相反。
     */
    @Test
    fun `the overlay is a sibling painted above SetupRoot instead of replacing it`() {
        val root = activitySource.indexOf("SetupRoot(")
        val coach = activitySource.indexOf("HomeCoachMarks(")
        assertTrue("两处都得在盘上：SetupRoot=$root HomeCoachMarks=$coach", root >= 0 && coach >= 0)
        assertTrue("罩子必须画在首页之后（同层兄弟，后者在上）", coach > root)
        assertFalse(
            "不该出现 else 分支把首页换掉：这一格里 SetupRoot 与 HomeCoachMarks 之间不许有 `else {`",
            activitySource.substring(root, coach).contains("else {")
        )
    }

    // ─────────────────────── B 组：三条出口各落成什么（行为尺）───────────────────────

    /**
     * 「跳过」= 不想现在弄：只豁免介绍层，并把游标落到"稍后"那一档——
     * 罩子收，缺项交回首页黄字行，**引导没有永久消失**（`resumeGuide` 还能回来）。
     */
    @Test
    fun `skip exempts the intro and defers the guide without completing it`() {
        val store = store()
        val cursor = GuideExitPaths.run(GuideExit.Skip, vm(store), contextWithoutKnowledge())
        assertTrue("跳过只该写 introSeen", store.introSeen)
        assertFalse("跳过绝不写引导完成", store.hasCompletedOnboarding)
        assertEquals(GuideCursor.DEFERRED_TO_HINT, cursor)
        assertEquals(GuideCursor.DEFERRED_TO_HINT.name, store.guideCursor)
    }

    /**
     * 「完成」= 介绍层走完了：游标停在**最早未满足那一格**（这里是没有供应商 → PROVIDER），
     * 而不是 DONE。基线 §6.8 与需求映射第 14 行同一条："配置不算引导完成"反过来也成立——
     * 没配齐就不能算完成。
     */
    @Test
    fun `complete leaves the guide at the earliest unmet step, not DONE`() {
        val store = store()
        val cursor = GuideExitPaths.run(GuideExit.Complete, vm(store), contextWithoutKnowledge())
        assertTrue(store.introSeen)
        assertFalse(store.hasCompletedOnboarding)
        assertEquals(GuideCursor.PROVIDER, cursor)
        assertEquals(GuideCursor.PROVIDER.name, store.guideCursor)
    }

    /**
     * 「去设置」= 现在就去配：清掉先前那一下"稍后"，游标回到最早未满足那一格。
     *
     * 这一格同时把 跳过 与 去设置 分开：同一份"按过稍后"的盘，跳过留在 DEFERRED_TO_HINT，
     * 去设置 落到 PROVIDER。回退成什么就红：有人把两条出口并成一条（"跳过=去设置"），
     * 用户点了"去设置"却仍看不到罩子。
     */
    @Test
    fun `open settings clears a previous defer while skip respects it`() {
        val deferredBefore = store(cursor = GuideCursor.DEFERRED_TO_HINT.name)
        val viaSettings = GuideExitPaths.run(
            GuideExit.OpenSettings, vm(deferredBefore), contextWithoutKnowledge()
        )
        assertEquals(
            "点「去设置」就是明确表态现在要配：先前那一下稍后要清掉，罩子指回最早未满足那一格",
            GuideCursor.PROVIDER,
            viaSettings
        )

        val skipAgain = store(cursor = GuideCursor.DEFERRED_TO_HINT.name)
        val viaSkip = GuideExitPaths.run(GuideExit.Skip, vm(skipAgain), contextWithoutKnowledge())
        assertEquals(
            "再点一次跳过不该把已经要稍后的人拽回来",
            GuideCursor.DEFERRED_TO_HINT,
            viaSkip
        )
    }

    /**
     * 「完成」与「去设置」的分工：走完介绍**不**推翻用户先前按过的稍后（尊重一次选择），
     * 而"我现在就去配"推翻它。回退成什么就红：两条出口写成同一条。
     */
    @Test
    fun `complete respects an earlier defer while open settings overrides it`() {
        val store = store(cursor = GuideCursor.DEFERRED_TO_HINT.name)
        assertEquals(
            GuideCursor.DEFERRED_TO_HINT,
            GuideExitPaths.run(GuideExit.Complete, vm(store), contextWithoutKnowledge())
        )
    }

    /** 供应商已就绪、无障碍还没给：派生游标落 ACCESSIBILITY（罩子该指消息捕获那一格） */
    @Test
    fun `a ready provider with no accessibility lands the cursor on the capture step`() {
        val store = store(providerReady = true)
        val cursor = GuideExitPaths.run(GuideExit.Complete, vm(store), contextWithoutKnowledge())
        assertEquals(GuideCursor.ACCESSIBILITY, cursor)
        assertEquals(GuideCursor.ACCESSIBILITY.name, store.guideCursor)
    }

    /**
     * 「不再提示」是唯一一条人工把游标钉死 DONE 的通道，而且不清任何数据。
     *
     * 回退成什么就红：有人拿 `completeOnboarding()` 当"不再提示"的实现——
     * 那会顺手把旧完成旗标写脏，升级用户与派生账再次分家。
     */
    @Test
    fun `stop being guided pins the cursor without touching user data`() {
        val store = store(providerReady = true)
        val model = vm(store)
        model.stopBeingGuided()
        assertTrue(store.introSeen)
        assertEquals(GuideCursor.DONE.name, store.guideCursor)
        assertTrue("人工收引导也顺手把旧旗标补真（两面旗标只剩一本账）", store.hasCompletedOnboarding)
        assertEquals("供应商那本数据一个字没动", 1, store.getWorkerTickets().size)
    }

    /** 「稍后」不钉 DONE：配齐之后派生自己走 DONE，提示行随之消失 */
    @Test
    fun `defer does not pin DONE`() {
        val store = store(providerReady = true)
        val model = vm(store)
        model.deferGuide()
        assertEquals(GuideCursor.DEFERRED_TO_HINT.name, store.guideCursor)
    }

    // ─────────────────────────── C 组：闸门真值表 ───────────────────────────

    /**
     * `introSeen=true` 之后向导不再出现，但游标可以仍是 PROVIDER——
     * 这两件事分家正是这一轮的目的（介绍层 vs 指引位置各一本账）。
     */
    @Test
    fun `seen intro hides the wizard while the guide cursor can still be PROVIDER`() {
        val store = store(introSeen = true)
        val model = vm(store)
        assertFalse("看过介绍就不该再被拽回向导", model.shouldShowIntro())
        assertEquals(
            "向导不出现不等于引导结束：还没有供应商，游标该停在 PROVIDER",
            GuideCursor.PROVIDER,
            model.currentGuideCursor(contextWithoutKnowledge())
        )
        assertFalse("事实没配齐就不许写完成", store.hasCompletedOnboarding)
    }

    /** 新人第一次进：介绍层在场，且不动任何旗标 */
    @Test
    fun `a brand new visitor sees the intro and nothing gets written`() {
        val store = store()
        assertTrue(vm(store).shouldShowIntro())
        assertFalse(store.introSeen)
        assertEquals("", store.guideCursor)
    }

    /**
     * 老用户迁移：旧完成旗标为真 = 介绍与指引都收工。
     * 回退成什么就红：只补 introSeen 不补游标——升级用户会被罩子追着重配一遍（基线 §6.8）。
     */
    @Test
    fun `an upgraded existing user gets both new ledgers backfilled`() {
        val store = store(legacyCompleted = true)
        assertFalse(vm(store).shouldShowIntro())
        assertTrue(store.introSeen)
        assertEquals(GuideCursor.DONE.name, store.guideCursor)
    }

    /** 生成过几次的老用户同样算"看过"，并且不会看到罩子 */
    @Test
    fun `a user with history is not dragged back into the guide`() {
        val store = store(generateCount = 4)
        assertFalse(vm(store).shouldShowIntro())
        assertTrue(store.introSeen)
        assertEquals(GuideCursor.DONE.name, store.guideCursor)
    }

    /** 脏游标不炸：盘上被人写过没见过的名字时，读回来是 NONE，重算后落回派生值。
     * 回退成什么就红：有人把脏值当 DONE 处理（那就是"引导悄悄没了"）。
     */
    @Test
    fun `a dirty cursor value falls back to the derived step instead of DONE`() {
        val store = store(introSeen = true, cursor = "STEP_7")
        val cursor = vm(store).currentGuideCursor(contextWithoutKnowledge())
        assertEquals(GuideCursor.PROVIDER, cursor)
        assertEquals(GuideCursor.PROVIDER.name, store.guideCursor)
    }

    // ─────────────── D 组：§9.1 页面闸与"子页返回"的游标刷新（形状尺）───────────────
    //
    // ⚠ 为什么这三格用源码形状、不挂组件：页面闸的**另一头**是 `SetupRoot` 的导航账（`rememberSaveable`
    // 的 destination）与 Activity 的 onResume，两者在 `createComposeRule()` 摆出来的那棵树上都没有
    // 真宿主可推（本仓库在 `CapturePageResumeRefreshTest` 顶上记过同一条仪器事实）。
    // 闸**本身**的行为（关了整块不画、开了整块回来）由 `HomeCoachMarksSemanticsTest`
    // 的 `the overlay leaves entirely when a subpage takes the screen` 量语义树，两把尺各管一头。

    private val rootSource: String by lazy {
        SourceScan.maskComments(mainSourceOf("ui/home/SetupRoot.kt").readText())
    }

    /** 取 `fun 名字(…) { … }` 那一具的花括号体内（配平，不 `[^}]*`：具里还有别的括号） */
    private fun bodyOf(code: String, signature: String): String {
        val start = code.indexOf(signature)
        assertTrue("`$signature` 不在盘上——这一格会扫了个空集恒绿", start >= 0)
        val open = code.indexOf('{', start)
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return code.substring(open + 1, i)
                }
            }
        }
        throw AssertionError("`$signature` 的花括号没配平")
    }

    /**
     * 罩子必须**只站在首页那一格**（表行：「遮罩没有限定只在首页显示」）。
     *
     * 回退成什么就红：
     * - 宿主不再把页面闸传给罩子 ⇒ 第一句数到 0（罩子退回"只按锚点早退"，子页上浮一张提示板）；
     * - `SetupRoot` 那颗出口被摘掉或改成"宿主自己判导航" ⇒ 第二/三句数到 0；
     * - 只读出口被写成第二本导航账（宿主也能改 destination）⇒ `destinationState.value =` 数到 2。
     */
    @Test
    fun `the coach overlay is gated by the page the root nav is standing on`() {
        assertEquals(
            "HomeCoachMarks 必须收到页面闸（onHome = 现在是不是 Home 那一格），且只这一处",
            1, Regex("""onHome\s*=\s*homeDestination == HomeDestination\.Home""").findAll(activitySource).count()
        )
        assertEquals(
            "`SetupRoot` 必须开一颗只读出口给宿主",
            1, Regex("""onDestinationChanged\s*:""").findAll(rootSource).count()
        )
        assertTrue("宿主必须接住那颗出口", activitySource.contains("onDestinationChanged ="))
        // 只读：这一层的导航账只有一个写点（goTo），宿主一侧一个字都写不进来
        assertEquals(
            "destination 只有一个写点：绕开 goTo 直接写 = 只读出口漏一次通知 = 旧板留在子页",
            1, Regex("""destinationState\.value\s*=""").findAll(rootSource).count()
        )
        assertEquals(
            "九处页内导航（3 前进 + 3 BackHandler + 3 onBack）全都要走那颗会通知出口的唯一写点",
            9, Regex("""goTo\(HomeDestination\.""").findAll(rootSource).count()
        )
        // 反向证人：改前那一版宿主（没有 onHome 这一参）必须被第一句数到 0
        val oldShape = "HomeCoachMarks(\n cursor = guideCursor,\n copy = coachCopyFor(guideCursor),\n)"
        assertEquals("注件没就位：改前的形状里该数不到页面闸", 0,
            Regex("""onHome\s*=\s*homeDestination == HomeDestination\.Home""").findAll(oldShape).count())
    }

    /**
     * 「子页返回也未完整接上游标刷新」那一格：回到 Home 必须有刷新点，且**只在回 Home 时**刷。
     *
     * 同一棵 Activity 树里换 destination 不走 `onResume`，少了这一步，回首页的罩子还停在出发前那一格，
     * 而子页里刚配好的供应商/刚开的捕获开关已经在盘上——引导会追着用户指已经做完的那一步。
     *
     * 回退成什么就红：刷新点没接 ⇒ 第二句数到 0；刷新写成了无条件（进子页也刷）⇒ 第一句红。
     */
    @Test
    fun `returning home re-derives the cursor without waiting for onResume`() {
        val handler = bodyOf(activitySource, "fun onHomeDestinationChanged")
        assertTrue("出口先把读数存进宿主那一颗（罩子的 onHome 读的就是它）",
            handler.contains("homeDestination = destination"))
        assertEquals(
            "回到 Home 那一格必须重算一次游标，且只经 publishGuideCursor 这一颗口（不开第二本账）",
            1, Regex("""publishGuideCursor\(\)""").findAll(handler).count()
        )
        assertTrue(
            "刷新要挂在「回到 Home」那个判据上：进子页重算等于把离场那一趟再写一遍盘",
            Regex("""==\s*HomeDestination\.Home\)""").containsMatchIn(handler)
        )
        // onResume 那颗刷新点不能被这一条顶掉（从系统设置那一趟走的还是它）
        assertEquals(
            "onResume 仍要刷一次（系统授权页那一趟不经过 destination）",
            1, Regex("""if \(!introOnScreen\) publishGuideCursor\(\)""")
                .findAll(bodyOf(activitySource, "override fun onResume")).count()
        )
    }
}
