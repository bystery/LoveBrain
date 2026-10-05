package com.lovebrain.app.viewmodel

import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.data.ConnectionTestResult
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.ProviderConfigResolver
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.model.ProviderTicket
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 齿轮设置页与供应商表单那两处的**保存**判据（这两处都不在悬浮窗里画像素，只看落盘）。
 *
 * 页面接线分两家，这一族因此也照着这两家写：
 *  - 四档超时与透明度由 [LoveBrainViewModel] 写（`setActiveTicketGenerationTimeout` /
 *    `setPanelBackdropOpacityPercent`）——它们刻意不走 `saveTicketWithProbe`，
 *    拨一颗档位不该先跑一遍真实连接探测；
 *  - 供应商表单由 [SetupViewModel] 写。
 *
 * 每一格钉的坏实现各不相同：
 *  1. 拨档位只写**当前工单**那一个字段：交出去的清单里别的工单必须逐字是原来那个对象
 *     （`ProviderTicket` 是 data class，整表比较就把"顺手重建了一张工单"也一并抓住），
 *     而且这一趟不许碰任何一条 Key（不写、不删），不许改激活项。
 *  2. 四档逐颗走一遍：写进去的秒数必须就是这张工单下一次请求的等待预算
 *     （过 `ProviderConfigResolver` 读回来，存与用不分家），另一张工单全程不动。
 *  3. 没有活动工单时**什么都不写**：不许凭空造一张、也不许把档位摊到所有工单上。
 *  4. 表单里 Key 留空 = 保留原 Key：落到盘上、落到探测调用上的都得是原来那把；
 *     同时这一张已设的超时档位与思考开关不许被这次保存洗掉。
 *  5. 透明度读回一致，且交给落盘口的那颗数本身就是刻度里的一颗——刻度只认
 *     [PanelBackdropOpacity] 这一处主人，这一族里没有任何一处另抄区间。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelSettingsSaveTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    /** 模拟盘上那张工单表：读到的是它、写进去的也是它，"改了谁"因此看得见 */
    private lateinit var stored: MutableList<ProviderTicket>
    private lateinit var prefs: SecurePrefs

    private val ticketA = ProviderTicket(
        id = "A",
        name = "慢速兼容服务",
        baseUrl = "https://provider-a.example.com/v1/chat",
        model = "m1",
        models = listOf("m1", "m2"),
        thinkingMode = 1,
        generateTimeoutSec = 300
    )
    private val ticketB = ProviderTicket(
        id = "B",
        name = "官方 Key",
        baseUrl = "https://api.deepseek.com/v1/chat",
        model = "deepseek-chat",
        models = listOf("deepseek-chat"),
        thinkingMode = 0,
        generateTimeoutSec = 60
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        stored = mutableListOf(ticketA, ticketB)
        prefs = mockk(relaxed = true)
        every { prefs.getWorkerTickets() } answers { stored.toList() }
        every { prefs.setWorkerTickets(any()) } answers { stored = firstArg<List<ProviderTicket>>().toMutableList() }
        every { prefs.activeTicketId } returns "A"
        every { prefs.getWorkerApiKey("A") } returns "sk-key-A"
        every { prefs.getWorkerApiKey("B") } returns "sk-key-B"
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadTodayCost() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newPanelViewModel(
        readOpacity: () -> Int = { PanelBackdropOpacity.DEFAULT_PERCENT },
        writeOpacity: (Int) -> Unit = {}
    ): LoveBrainViewModel {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns null
        coEvery { knowledgeRepo.listAll() } returns emptyList()
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.readIntent(any()) } returns com.lovebrain.app.model.IntentConfig()
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns ConfigValidationResult(0, 0, emptyList())
        every { promptBuilder.assetHashOf(*anyVararg()) } returns "asset-hash"
        return LoveBrainViewModel(
            deepSeekRepo = mockk<DeepSeekRepository>(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk<GenerationEngine>(relaxed = true),
            operationCoordinator = ForegroundOperationCoordinator(
                CoroutineScope(testDispatcher + SupervisorJob())
            ),
            readBackdropOpacityPercent = readOpacity,
            writeBackdropOpacityPercent = writeOpacity
        )
    }

    private fun TestScope.settle() = advanceUntilIdle()

    // ═══════════════════ 1 + 2 + 3：四档只写当前工单 ═══════════════════

    @Test
    fun `dialing the timeout rewrites the current ticket only and leaves the other one byte for byte`() =
        runTest(testDispatcher) {
            val vm = newPanelViewModel()
            settle()

            vm.setActiveTicketGenerationTimeout(GenerationTimeoutTier.SEC_180)
            settle()

            assertEquals(
                "整张表除了当前工单那一格，别的一个字段都不许变（尤其不许顺手重建别的工单）",
                listOf(ticketA.copy(generateTimeoutSec = 180), ticketB),
                stored
            )
            verify(exactly = 0) { prefs.saveWorkerApiKey(any(), any()) }
            verify(exactly = 0) { prefs.deleteWorkerApiKey(any()) }
            verify(exactly = 0) { prefs.activeTicketId = any() }
            assertEquals(
                "设置页的选中态读的就是这一颗：写完要能立刻读回 180",
                180,
                vm.activeTicket.value?.generateTimeoutSec
            )
        }

    @Test
    fun `each of the four tiers becomes exactly that ticket's wait budget on the next request`() =
        runTest(testDispatcher) {
            val vm = newPanelViewModel()
            settle()

            for (tier in GenerationTimeoutTier.options) {
                vm.setActiveTicketGenerationTimeout(tier)
                settle()
                assertEquals("档位写盘写的是这一档本身", tier.seconds, stored.single { it.id == "A" }.generateTimeoutSec)
                assertEquals(
                    "存进去的这一档必须就是这张工单下一次请求的等待预算，不能只活在盘上",
                    tier.millis,
                    ProviderConfigResolver(prefs).resolveFor("A")!!.generateTimeoutMs
                )
                assertEquals("另一张工单这一格全程没被碰过", 60, stored.single { it.id == "B" }.generateTimeoutSec)
            }
        }

    @Test
    fun `dialing the timeout with no active ticket writes nothing instead of inventing one`() =
        runTest(testDispatcher) {
            every { prefs.activeTicketId } returns null
            val before = stored.toList()
            val vm = newPanelViewModel()
            settle()

            vm.setActiveTicketGenerationTimeout(GenerationTimeoutTier.SEC_60)
            settle()

            verify(exactly = 0) { prefs.setWorkerTickets(any()) }
            assertEquals("没有活动工单时不该有任何一张被摊上档位", before, stored)
            assertEquals(60, stored.single { it.id == "B" }.generateTimeoutSec)
        }

    // ═══════════════════ 4：表单保存不误清 ═══════════════════

    @Test
    fun `saving the form with a blank key keeps the stored key and probes with it`() = runTest(testDispatcher) {
        val probedUrl = "https://provider-a.example.com/chat/completions"
        val repo = mockk<DeepSeekRepository>(relaxed = true)
        coEvery { repo.testConnectionWithProbe(any(), any(), any()) } returns
            ConnectionTestResult(success = true, resolvedUrl = probedUrl)
        val vm = SetupViewModel(prefs, repo)

        val saved = vm.saveTicketWithProbe(
            ticketId = "A",
            name = "慢速兼容服务改名",
            baseUrl = "https://provider-a.example.com",
            models = listOf("m1", "m2"),
            apiKey = "   ",
            thinkingMode = 0,
            // 这一族的最后一个形参是档位（默认 null ⇒ 走 `fromSecondsOrDefault` 落回默认档 120，
            // 那一条由 ProviderGenerateTimeoutTierTest 钉着）。真页面的写法是"档位跟着这张工单现在
            // 的值进表单、再原样交回去"：ProviderSection 的表单态由
            // GenerationTimeoutTier.fromSecondsOrDefault(ticket?.generateTimeoutSec) 起，保存时把
            // timeoutTier.seconds 传进同一个调用（ProviderSection.kt 保存那颗 onClick）。这里照同一条形状传。
            // 下面那句整表比较因此仍然在判"保存不许把已设档位洗掉"：传 300 而盘上不是 300 就红。
            generateTimeoutSec = ticketA.generateTimeoutSec
        )

        assertTrue("表单四项齐备（Key 走保留那条）时保存必须成功", saved)
        verify { prefs.saveWorkerApiKey("A", "sk-key-A") }
        verify(exactly = 0) { prefs.saveWorkerApiKey(any(), "") }
        verify(exactly = 0) { prefs.deleteWorkerApiKey(any()) }
        coVerify { repo.testConnectionWithProbe("sk-key-A", "m1", "https://provider-a.example.com") }
        assertEquals(
            "编辑保存只能改表单里那几项：已设的超时档位与思考开关都得原地留着",
            ticketA.copy(name = "慢速兼容服务改名", baseUrl = probedUrl),
            stored.single { it.id == "A" }
        )
        assertEquals("另一家的配置一个字都不动，Key 也在原处", ticketB, stored.single { it.id == "B" })
        verify(exactly = 0) { prefs.saveWorkerApiKey("B", any()) }
    }

    // ═══════════════════ 5：透明度读回一致 ═══════════════════

    @Test
    fun `the opacity slider hands the store a value on its own scale and reads it back unchanged`() =
        runTest(testDispatcher) {
            var onDisk = PanelBackdropOpacity.DEFAULT_PERCENT
            val handedToStore = mutableListOf<Int>()
            val vm = newPanelViewModel(
                readOpacity = { onDisk },
                writeOpacity = { handedToStore.add(it); onDisk = it }
            )
            settle()

            assertEquals(
                "这台机器从没滑过 ⇒ 读回来是刻度主人那个默认值",
                PanelBackdropOpacity.DEFAULT_PERCENT,
                vm.panelBackdropOpacityPercent
            )
            assertTrue("光是读一屏不许顺手落一次盘（那等于把没动过的设置当成被改过一次）", handedToStore.isEmpty())

            for (step in PanelBackdropOpacity.steps) {
                vm.setPanelBackdropOpacityPercent(step)
                assertEquals("写进去的第 $step 档要原样读回来", step, vm.panelBackdropOpacityPercent)
            }
            assertEquals(
                "一次滑动只落一次盘（落盘口子被写两遍就是两份状态在打架）",
                PanelBackdropOpacity.steps.size,
                handedToStore.size
            )

            // 刻度外（滑杆停在两档中间）：落盘口收到的必须已经是被对齐过的那一颗
            val offTick = PanelBackdropOpacity.steps.first() + PanelBackdropOpacity.STEP_PERCENT - 1
            assertTrue("这个 fixture 得真的落在两档中间，否则下面那句判不出东西",
                PanelBackdropOpacity.steps.none { it == offTick })
            vm.setPanelBackdropOpacityPercent(offTick)
            val landed = handedToStore.last()
            assertTrue(
                "刻度外被原样写了下去 ⇒ 区间与刻度在落盘这一侧又被抄了一遍：" + handedToStore,
                PanelBackdropOpacity.steps.contains(landed)
            )
            assertTrue("对齐必须真的动过这个数", offTick != landed)
            assertEquals("写下去的那颗就是下一次读回来的那颗", landed, vm.panelBackdropOpacityPercent)
        }
}
