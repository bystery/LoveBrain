package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SemanticsProbe.Target
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 供应商表单本体（`ProviderFormBody`）——§6.5 :531/:532 在这一屏的第一次逐颗量。
 *
 * 为什么现在量得到、以前量不到：这台仪器里「`Dialog` 窗口 + 文本框拿焦点」永不空闲
 * （账本 §45.1 用一次性诊断钉死的，不是猜的）。表单内容抽成 `ProviderFormBody` 之后
 * `Dialog` 只剩外壳，测量绕开那扇窗口直接挂这一格就够了。
 * 生产上它仍然画在 `Dialog` + `Card` 里——那一半由
 * `UiLayerDependencyContractTest` 的结构格守（浮层窗口在本机量不了，只能读结构）。
 *
 * ⚠ 本文件的锚点分两类：**「添加模型」「取消」「显示」…仍是内联中文字面量**
 * （这些文案还没还债，换语言也不变，拿它们当锚点是稳的）；
 * 而「保存 / 保存修改」与行尾那四颗图标动作的名字已经走了资源，判据必须 `getString` 取
 * （见下面 `saveLabel` 与 `setModelLabel` 那一组）。
 * 前者是 §6.1 字面量预算记着的债，还一处就要回来把这里的锚点改成资源驱动。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProviderFormSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun fakeVm(save: ((Int?) -> Unit)? = null): SetupViewModel {
        val vm = mockk<SetupViewModel>(relaxed = true)
        // ⚠ 四条显式桩一条都不能省：relaxed 对 `StateFlow<String?>` 交回泛型 mock，
        //   `.value` 一取就 ClassCastException，而栈顶会指向一个不存在的行号（坑表 75/84）。
        every { vm.formError } returns MutableStateFlow<String?>(null)
        every { vm.saving } returns MutableStateFlow(false)
        every { vm.getKeyMask(any()) } returns "sk-…3f9a"
        every { vm.globalThinking } returns 0
        // 只有要看"表单把什么交给了 VM"的格子才挂这颗桩；其余格子仍走 relaxed 的 false。
        // 第七颗实参就是这张工单的生成超时档位（Issue #5）。
        // ⚠ 这里**不能**用 `lastArg()`：`saveTicketWithProbe` 是 suspend，mockk 收到的运行期
        //   参数尾巴上还挂着一枚 Continuation，`lastArg()` 交回的是那个 lambda，
        //   一取就 ClassCastException（本机第一次跑就是这么红的）。按槽捕获才对准声明位。
        if (save != null) {
            val tier = slot<Int>()
            coEvery {
                vm.saveTicketWithProbe(any(), any(), any(), any(), any(), any(), capture(tier))
            } answers { save(tier.captured); true }
        }
        return vm
    }

    /**
     * 「保存」那颗现在走资源了（本格之后）。
     *
     * ⚠ 这台机器的环境解析出来是**英文**，判据不能写死"保存修改"四个字——
     * 上一格「添加供应商」就栽在这里：matcher 直接 0 命中。
     * 名字一律 `getString` 取；中文那份留在 `values/`，由资源覆盖保证两边一起改。
     */
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val saveLabel: String get() = ctx.getString(R.string.provider_save)
    private val saveChangesLabel: String get() = ctx.getString(R.string.provider_save_changes)

    /**
     * 表单行尾那四颗图标动作的读屏名字也走资源了（图标档接管那一格）——于是这里的锚点
     * 同样得 `getString` 取，判据一个字没动：还是"滚到底必须量到这四颗、两轴都过下限"。
     */
    private val setModelLabel: String get() = ctx.getString(R.string.a11y_set_current_model)
    private val testConnectionLabel: String get() = ctx.getString(R.string.a11y_test_connection)
    private val editLabel: String get() = ctx.getString(R.string.a11y_action_edit)
    private val deleteLabel: String get() = ctx.getString(R.string.a11y_action_delete)

    private val threeModels = ProviderTicket(
        id = "t1",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.example",
        model = "deepseek-chat",
        models = listOf("deepseek-chat", "deepseek-reasoner", "qwen-max"),
        thinkingMode = 1
    )

    private fun mount(ticket: ProviderTicket?, matrix: UiMatrix, vm: SetupViewModel = fakeVm()) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                ProviderFormBody(viewModel = vm, ticket = ticket, onDismiss = {})
            }
        }
        rule.waitForIdle()
    }


    /**
     * 沿纵向**滚到底**，每档重扫整棵树，返回"每颗控件自己的尺寸"。
     *
     * 为什么取**最大面积**而不是"某一档量到的那个尺寸"：`verticalScroll` 的容器会把
     * 露出视口的那部分**裁掉之后再报进语义树**（坑表 85：本机实量到 48x43dp 与 0x0dp，
     * 那是滚动容器在裁，不是热区做小了；上一格在知识库卡片上就是这么差点去修一个没坏的东西）。
     * 裁切只会让读数变小，所以滚过一遍取最大才是这颗控件本来的尺寸——
     * 顺带也就不用把视口高度写进判据（写了换个档位就失效，见坑表 64）。
     *
     * 两条哨兵，缺一条这格就退化成"在首屏转圈却自称扫过全表单"：
     * ① 位置指纹连续两档相同才算到底；② 到步数上限还没相同 ⇒ 直接判失败。
     *
     * ⚠ 同名多颗（三行 × 四颗图标）在这把尺里合成一条。少判的是"某一行被别的行挤小"
     * 这种版式问题，本格不假装覆盖。
     */
    /**
     * 逐档滚到底那把尺**不再写在这里**——它收进了 `core/testing/ScrollScan`（同一个口径
     * 只许有一处：新的第三屏 `OnboardingScreenSemanticsTest` 要测的是同一件事，
     * 复制一份就会有两把尺各自漂移）。这里只留一层转发。
     */
    private val scan by lazy { ScrollScan(rule, probe) }

    private fun scanToBottom(ticket: ProviderTicket?, matrix: UiMatrix): Map<String, SemanticsProbe.Target> {
        mount(ticket, matrix)
        return scan.toBottom("ProviderFormBody")
    }

    /** 这一屏该说得出名字的东西，全部来自 `ProviderFormBody`：内联的那几处 + 已经走资源的那几处 */
    private val expectedControls = listOf(
        "名称", "https://api.example.com", "留空保留原 Key", "显示", "Thinking mode",
        setModelLabel, testConnectionLabel, editLabel, deleteLabel, "＋ 添加模型", "取消", saveChangesLabel
    )

    @Test
    fun `the form can be measured without the Dialog window and scrolling reaches its bottom`() {
        val seen = scanToBottom(threeModels, UiMatrix(360))
        assertTrue(
            "扫完只量到 ${seen.size} 类节点，样本这么小说明挂载或滚动没生效：" +
                seen.values.joinToString { it.describe() },
            seen.size >= 8
        )
        // 「保存修改」在最底下：量到它就等于证明滚动真把表单体翻出来了，不是只在首屏转了一圈
        assertTrue(
            "滚到底也没量到表单的动作区，只量到：" + seen.keys,
            seen.containsKey("取消") && seen.containsKey(saveChangesLabel)
        )
    }

    @Test
    fun `every control in the form meets the 48dp floor`() {
        assertFloor(UiMatrix(360))
    }

    /**
     * 最坏组合那一档单开一格。
     *
     * 原来两档写在一个 `for` 里，当场红在 `Cannot call setContent twice per test!`——
     * 一台测试只能挂一次内容（坑表第 3 条），所以"跑多档"只能多开格，不能靠循环。
     */
    @Test
    fun `the form still meets the floor at the worst combination`() {
        assertFloor(UiMatrix(320, fontScale = 2.0f))
    }

    private fun assertFloor(matrix: UiMatrix) {
        val seen = scanToBottom(threeModels, matrix)
        assertTrue("${matrix.id} 只量到 ${seen.size} 类节点，这一档没渲染出来", seen.size >= 8)
        val offenders = seen.values.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "表单里有 ${offenders.size} 类可交互节点小于 ${probe.floorDp.toInt()}dp（${matrix.id}）：\n" +
                offenders.joinToString("\n") { "  " + it.describe() } +
                "\n  （尺寸取各滚动档里**最大**的那一次，报的都是没被容器裁掉的真实尺寸）",
            offenders.isEmpty()
        )
    }

    /**
     * §6.5 :532——可交互节点得报得出角色。
     *
     * 输入框**故意不判**：1.6.8 的 `BasicTextField` 根本不报 role
     * （见 [SemanticsProbe.Target.editable] 里那条实量），把它们算进来会得到一条
     * 永远修不红的假闸（判据换了对象就不再成立，坑表 66 那一族）。
     */
    @Test
    fun `every non-editable control in the form announces a role`() {
        val seen = scanToBottom(threeModels, UiMatrix(360))
        val missing = seen.values.filter { !it.editable && it.role == "无" }
        assertTrue(
            "表单里有 ${missing.size} 类可交互节点没声明角色（读屏念得出名字，说不出它是按钮）：\n" +
                missing.joinToString("\n") { "  " + it.describe() },
            missing.isEmpty()
        )
    }

    @Test
    fun `every control in the form can be named`() {
        val seen = scanToBottom(threeModels, UiMatrix(360))
        val unlabeled = seen.values.filter { !it.labeled }
        assertTrue(
            "有 ${unlabeled.size} 类节点既没有文案也没有 contentDescription：\n" +
                unlabeled.joinToString("\n") { "  " + it.describe() },
            unlabeled.isEmpty()
        )
    }

    /**
     * 该说得出名字的东西一个都不能少——这条是**覆盖**判据，不是尺寸判据。
     *
     * 「每颗都达标」在样本只剩三颗时也是绿的，所以反过来要求：滚完之后累计必须见到
     * 清单里每一类。缺哪一类，就是那一类没被渲染出来或被筛掉了。
     */
    @Test
    fun `the scan actually covers the whole form`() {
        val seen = scanToBottom(threeModels, UiMatrix(360))
        val absent = expectedControls.filter { !seen.containsKey(it) }
        assertTrue(
            "扫完全表单仍然没量到这些控件：" + absent + "\n实际量到：" + seen.keys.sorted(),
            absent.isEmpty()
        )
    }

    /** §6.3/§6.5：空表单时"保存"要灰着留在原地，而不是整颗消失 */
    @Test
    fun `the empty form keeps the save action and marks it disabled`() {
        val seen = scanToBottom(null, UiMatrix(360))
        val saves = seen.values.filter { it.label == saveLabel }
        assertEquals(
            "空表单里「保存」（本机解析成" + saveLabel + "）应当恰好一颗（多出来就是两个入口各写各的）：" +
                seen.values.joinToString { it.describe() },
            1, saves.size
        )
        assertTrue(
            "名字没填时「保存」必须报 Disabled——整颗消失与灰着不能都判绿：" + saves.single().describe(),
            saves.single().disabled
        )
        assertTrue(
            "空态里得就地给出「＋ 添加模型」这个动作（§6.3：不能只留一句话）：" + seen.keys,
            seen.containsKey("＋ 添加模型")
        )
    }

    /**
     * 反过来那一半：填齐了「保存」必须**能按**。
     *
     * 上一格只判了"空表单里它灰着且还在"，于是把 `when` 写错成"永远 Disabled"
     * 也能全绿（ Disabled 那格照样过、尺寸与角色那格照样过）——
     * 禁用态与可用态必须**各有一格**，不然这条链只测了一半（坑表：局部收紧要回扫同一资源的其它属性）。
     */
    @Test
    fun `a filled form leaves the save action enabled`() {
        val seen = scanToBottom(threeModels, UiMatrix(360))
        val saves = seen.values.filter { it.label == saveChangesLabel }
        assertEquals(
            "填好的表单里「保存修改」（本机解析成 $saveChangesLabel）应当恰好一颗：" +
                seen.values.joinToString { it.describe() },
            1, saves.size
        )
        val t = saves.single()
        assertTrue(
            "字段都齐了还报 Disabled，等于把这条链关死了：" + t.describe(),
            !t.disabled
        )
        assertEquals(
            "主动作得报成按钮，不能因为换了实现就丢掉角色：" + t.describe(),
            "Button", t.role
        )
    }

    /**
     * 「显示 / 隐藏」这颗：既要够大，也要真的会改名。
     *
     * 只判尺寸的话，写死"显示"两个字、点下去什么都不变的实现也能过——
     * 名字跟着状态翻才是 :532 那一栏的意思。
     */
    @Test
    fun `the key visibility action is big enough and renames itself`() {
        mount(threeModels, UiMatrix(360))
        val toggleKey = probe.actionableTargets(rule, "表单-Key 槽").filter { it.label == "显示" || it.label == "隐藏" }
        assertEquals(
            "Key 那行得恰好有一颗显示/隐藏动作：" +
                probe.actionableTargets(rule, "表单-Key 槽").joinToString { it.describe() },
            1, toggleKey.size
        )
        val t = toggleKey.single()
        assertTrue(
            "这颗动作的热区不到 ${probe.floorDp.toInt()}dp。它挂在输入框尾部槽里，" +
                "外面那层 Box 有 48dp 不算——手指信的是这一颗自己：" + t.describe(),
            !t.tooSmall(probe.floorDp)
        )
        val before = t.label
        rule.onAllNodesWithText(before)[0].performClick()
        rule.waitForIdle()
        val after = rule.onAllNodesWithText(if (before == "显示") "隐藏" else "显示")
            .fetchSemanticsNodes()
        assertTrue(
            "点过一次之后屏幕上得出现另一个名字（原来是「$before」），" +
                "否则读屏永远念的是旧状态：点完之后量到 " +
                probe.actionableTargets(rule, "表单-点过一次").joinToString { it.describe() },
            after.isNotEmpty()
        )
    }

    /**
     * 生成超时档位那一排（Issue #5 / 外部复核 §2 P1-1）：界面上只有白名单那四颗、
     * 互斥单选、**手指点哪一颗就把哪一颗交给 VM**。
     *
     * 为什么必须有这一格（本机实测出来的缺口，不是猜的）：
     * `ProviderGenerateTimeoutTierTest` 那 14 格量的是白名单 → 请求配置 → OkHttp 读超时
     * → `withTimeout` 这条链，它从表单**下面**经过。把保存那一行的实参写死成 `120`
     * （用户点了 300、盘上仍是 120）时，那 14 格与本文件其余各格（尺寸/角色/命名/覆盖）**全部照绿**
     * ——本机注入 `120` 后 `ProviderFormSemanticsTest` 退出码 0。
     * 这一格补的就是"手指到 VM"那一段，写法照 `the key visibility action ...` 那一格：
     * 先判存在与选中位，再点一次看状态真的移动，最后把交出去的账钉死。
     */
    @Test
    fun `the tier row offers exactly four bounded options and saving hands the tapped one over`() {
        val sent = mutableListOf<Int?>()
        val vm = fakeVm(save = { sent.add(it) })
        mount(threeModels, UiMatrix(360), vm)

        val labels = GenerationTimeoutTier.options.map {
            ctx.getString(R.string.provider_timeout_tier_label, it.seconds)
        }
        assertEquals("档位标签按白名单顺序取，四颗一颗不多一颗不少", 4, labels.size)
        // 子串匹配：选中那一颗的名字带对勾前缀（`LbChipStyle.markSelectedWithCheck`），
        // 判据要认的是"这一档在不在"，不是对勾画没画——对勾由 LbChip 自己的组件格钉。
        labels.forEach { l ->
            rule.onAllNodesWithText(l, substring = true).assertCountEquals(1)
        }
        // 这张工单从没配过档位 ⇒ 选中的必须是默认档那一个（120 秒）
        val defaultIdx = GenerationTimeoutTier.options.indexOf(GenerationTimeoutTier.DEFAULT)
        rule.onAllNodesWithText(labels[defaultIdx], substring = true)[0]
            .performScrollTo().assertIsSelected()
        labels.forEachIndexed { i, l ->
            if (i != defaultIdx) {
                rule.onAllNodesWithText(l, substring = true)[0].performScrollTo().assertIsNotSelected()
            }
        }

        // 点最大那一档：选中位必须**移动**，且同一组里只许剩一颗选中
        val topIdx = GenerationTimeoutTier.options.lastIndex
        rule.onAllNodesWithText(labels[topIdx], substring = true)[0].performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(labels[topIdx], substring = true)[0].assertIsSelected()
        rule.onAllNodesWithText(labels[defaultIdx], substring = true)[0].assertIsNotSelected()

        // 保存：VM 收到的那第七颗实参必须就是刚点的那一档
        rule.onAllNodesWithText(saveChangesLabel)[0].performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("点过一次保存就该恰好交一次账，实到：$sent", 1, sent.size)
        assertEquals(
            "手指点在 " + GenerationTimeoutTier.options[topIdx].seconds + " 秒那一颗，" +
                "VM 收到的必须是它——写死成默认档的实现在这一格红",
            GenerationTimeoutTier.options[topIdx].seconds,
            sent.single()
        )
    }
}
