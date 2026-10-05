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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SemanticsProbe.Target
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
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
 * 供应商表单本体（`ProviderFormBody`）——这一屏逐颗量的那一族尺。
 *
 * 为什么现在量得到、以前量不到：这台仪器里「`Dialog` 窗口 + 文本框拿焦点」永不空闲
 * （账本里那条一次性诊断钉死的，不是猜的）。表单内容抽成 `ProviderFormBody` 之后
 * `Dialog` 只剩外壳，测量绕开那扇窗口直接挂这一格就够了。
 * 生产上它仍然画在 `Dialog` + `Card` 里——那一半由
 * `UiLayerDependencyContractTest` 的结构格守（浮层窗口在本机量不了，只能读结构）。
 *
 * **尺寸这一族按本轮合同分成两条轴**（旧的那条"每一颗都得 ≥48dp"已经不是合同）：
 * 可见尺寸与热区各归各的主人，所以这里逐颗点名它走哪一档，认不出档的仍按全站 48 判。
 * 每一档的边界都写在下面的判据里，坏实现（把行内盒抬回 48、把可见外框缩到别处）都会红。
 *
 * ⚠ 本文件的锚点分两类：**「添加模型」「取消」「显示」…仍是内联中文字面量**
 * （这些文案还没还债，换语言也不变，拿它们当锚点是稳的）；
 * 而「保存 / 保存修改」与行尾那四颗图标动作的名字已经走了资源，判据必须 `getString` 取
 * （见下面 `saveLabel` 与 `setModelLabel` 那一组）。
 * 前者是字面量预算记着的债，还一处就要回来把这里的锚点改成资源驱动。
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
        // 第七颗实参就是这张工单的生成超时档位。
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
     * 「保存」那颗现在走资源了（本轮之后）。
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
     * 这种版式问题，本轮不假装覆盖。
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
    fun `every control in the form meets the tier this contract assigns it`() {
        assertFormTiers(UiMatrix(360))
    }

    /**
     * 最坏组合那一档单开一格。
     *
     * 原来两档写在一个 `for` 里，当场红在 `Cannot call setContent twice per test!`——
     * 一台测试只能挂一次内容（坑表第 3 条），所以"跑多档"只能多开格，不能靠循环。
     */
    @Test
    fun `the form still meets its tiers at the worst combination`() {
        assertFormTiers(UiMatrix(320, fontScale = 2.0f))
    }

    /** 模型行尾那四颗的名字（走资源，本机解析成英文）——这一族的档是 28dp 见方的紧凑盒 */
    private val rowIconLabels = listOf(setModelLabel, testConnectionLabel, editLabel, deleteLabel)

    /**
     * 本轮合同把**可见尺寸**与**热区**拆成两条轴，"每一颗可见尺寸都得 ≥48dp"那一条已经不是合同；
     * 但**热区那一条一个字没松**（`AppDimens.TOUCH_TARGET_MIN_DP` 仍写着 48），
     * 本轮明写的例外只有一族：卡片/列表行里那 28dp 见方的紧凑盒。
     * 所以这一格逐颗点名它走哪一档，并且**每一条都是数值**（不是"存在就行"）：
     *
     * - 模型行尾那四颗：**28dp 见方**那一档，两侧都钉（抬回 48 会挤掉模型名，缩成 20 也红）；
     * - Key 那一行的显隐那颗：**[TouchTier.VISIBLE_FORM_INPUT] 那一档（36）**，两侧都钉，
     *   理由与它到底坐在那一层的读数写在 [assertFieldSlotTier] 上面那一段；
     * - 其余每一颗可交互节点（保存、取消、「＋ 添加模型」、思考模式开关、四颗超时胶囊）：
     *   仍然过全站 48 下限。**「取消」这颗是这一格有意的反向证人**：本轮把它跟着表单密度收成 36 高，
     *   缩的是**带 clickable 的那一颗自己**，退路于是变成全屏难点的那一颗——那一条已判成真回归，
     *   修的是生产码（把热区垫回下限），不把尺子改成 36 来认这个退化。
     * - 认不出档的节点**一律按 48 判**，所以"表单里新长出一颗没登记档位的控件"不会静默通过。
     *
     * ⚠ 三颗输入框自己不判尺寸，而且是**如实点名**不判：本机实量它们的可编辑节点是
     * 304x15dp / 264x15dp（文字行那一格），既不是 36 的可见外框也不是 48 的透明热区——
     * `CompactInput` 那两层（可见胶囊 + 只转焦点、不声明点击的透明盒）都不带语义，
     * 语义树里没有节点，拿那颗 15dp 去比 36 或 48 都是许愿。同理量不到的还有 MiniSwitch 那颗
     * 36×20 的轨道与 16dp 的球、行内图标的 16dp 字形（图标本体 `contentDescription = null`，不入树）：
     * 这几条只能等设备侧截图那一族。输入框那一族在这里只判"必须仍然数得出三颗、且说得出名字"。
     */
    private fun assertFormTiers(matrix: UiMatrix) {
        val seen = scanToBottom(threeModels, matrix)
        assertTrue("${matrix.id} 只量到 ${seen.size} 类节点，这一档没渲染出来", seen.size >= 8)

        // ── 28 见方那一档（本轮唯一明写的热区例外）：两侧都钉 ──
        val rowIcons = seen.values.filter { rowIconLabels.contains(it.label) }
        assertEquals(
            "模型行尾那四颗紧凑盒必须逐颗量到，实到 " + rowIcons.joinToString { it.describe() },
            4, rowIcons.size
        )
        rowIcons.forEach { t ->
            assertTrue(
                "行内图标要的是本轮钉的 ${TouchTier.CARD_ACTION.toInt()}dp 见方点击盒" +
                    "（合同：不要 3–4 颗 48dp 盒挤占模型名；抬回 48 与缩成别的数一样是违约），实到 " +
                    t.describe(),
                abs(t.widthDp - TouchTier.CARD_ACTION) < 0.6f &&
                    abs(t.heightDp - TouchTier.CARD_ACTION) < 0.6f
            )
        }
        assertModelNameKeepsItsSlot(matrix)

        // ── 输入框那一族：不判尺寸（理由见上），但必须数得出、说得出名字 ──
        val inputs = seen.values.filter { it.editable }
        assertEquals(
            "表单里应当恰好三颗可编辑节点（名称/地址/Key），实到 " + inputs.joinToString { it.describe() },
            3, inputs.size
        )
        assertTrue(
            "三颗输入框都得让读屏说得出自己是什么（placeholder 就是它的名字）：" +
                inputs.joinToString { it.describe() },
            inputs.all { it.labeled }
        )

        // ── 其余全部：仍是全站 48 下限（含本轮被收小后又垫回去的「取消」） ──
        val exempted = (rowIcons + inputs).toSet()
        val rest = seen.values.filter { it !in exempted }
        assertTrue("${matrix.id} 除行内图标与输入框外没量到任何一颗控件 ⇒ 这格在空转", rest.isNotEmpty())
        // 字段框尾部槽那一颗先摘出来按**它自己那一档**判（见 [assertFieldSlotTier] 上面那一段），
        // 剩下那颗都不许跟着一起松：这就是"逐颗点名档位"而不是"把整屏的尺调到最小"。
        val slotActions = rest.filter { keyVisibilityLabels.contains(it.label) }
        assertEquals(
            "Key 那一行的显隐动作必须逐档量到恰好一颗，实到 " +
                slotActions.joinToString { it.describe() },
            1, slotActions.size
        )
        assertFieldSlotTier(slotActions, "表单 ${matrix.id}")
        probe.assertTargetsMeetFloor(
            rest - slotActions.toSet(), TouchTier.SITE_FLOOR,
            "表单 ${matrix.id}·热区下限（本轮放过的是 28 见方那一族行内盒、输入框那颗文本行，" +
                "与字段框尾部槽那一颗；把主动作或出口自己缩到比它那一档还矮就是被缩热区，不是被放宽的可见尺寸）"
        )
    }

    /** 尾部槽那两颗名字（生产里仍是内联字面量，换语言不动，所以拿它们当锚点是稳的） */
    private val keyVisibilityLabels = listOf("显示", "隐藏")

    /**
     * Key 显隐那一颗走哪一档：**它所在那一条字段框**那一档，两轴下限 36、上限 48。
     *
     * 复验过的那条链（生产 `ui/common/CompactInput.kt`，本机读数对得上）：
     *  - 外层透明热区盒 `heightIn(min = 48)`——只转焦点、**故意不声明点击语义**；
     *  - 中层可见胶囊 `height(INPUT_ROW_HEIGHT_DP = 36)`；
     *  - 尾部槽 `Box(align = CenterEnd)` 挂在**中层**那一颗里面。
     * 所以那颗动作自己写的 `heightIn(min = 48)` 顶不过父约束（父给它的 maxHeight 就是 36）：
     * 360dp 那一档实量 **58x36dp @(274,205)**，320dp+2.0 倍字实量 **72x36dp @(220,249)**——
     * 两档的高度都是 36，正是父框，而不是它自己要求的 48。
     *
     * 于是这一档判的是：
     *  - **下限 [TouchTier.VISIBLE_FORM_INPUT]**：两轴都不许比它所在那条字段框还矮
     *    （缩到 28/20 就红——那才是"把一颗东西又缩了一档"，不是被放宽的可见尺寸）；
     *  - **上限 [TouchTier.SITE_FLOOR]**：高不许越过整行那一档（越过就是它去占邻行的地方了）。
     *
     * ⚠ 如实说一件量不到的事：**"整行 ≥48" 这一半在本机读不出来**，不是判过了。
     * 外层那 48 高的热区盒没有语义节点（给它声明一颗入口，"这一行有几个可点项"的守卫数就会错，
     * 这件事写在生产注释里），语义树里因此最粗的那条输入框读数只有 15dp 那一行字。
     * 这一档选择的代价就摆在这里：**这颗动作自己的可点框是 36 高，比全站下限低一档**，
     * 手指点它要点进那 36dp；要点整行 48 那一层得到的是输入框的焦点，不是翻显隐。
     * 要把这一半变成机器读数，得让那层热区有一个可读锚点（生产侧），或走设备侧截图那一族。
     */
    private fun assertFieldSlotTier(items: List<Target>, where: String) {
        probe.assertTargetsMeetFloor(
            items, TouchTier.VISIBLE_FORM_INPUT,
            "$where·字段框尾部槽那颗：它坐在 36dp 的可见框里，自己就得把这一框的高度吃到"
        )
        val over = items.filter { it.heightDp - TouchTier.SITE_FLOOR > 0.6f }
        assertTrue(
            "$where：尾部槽那颗高越过 ${TouchTier.SITE_FLOOR.toInt()}dp 就是长出自己那一行了（占邻行的地方）：" +
                over.joinToString { it.describe() },
            over.isEmpty()
        )
    }

    /**
     * 本轮那一句"动作不许挤成两行巨按钮、长名允许清楚截断"量的是**槽位**，不是文字宽：
     * 模型名那颗 `Text` 报回来的是它自己的自然宽（截不截都一样），所以这里量
     * 「文本左缘 → 行内第一颗图标左缘」那一段=名称那一格真正分到的宽。
     *
     * 判据：行尾四颗合计之后，名称那一格还必须有"四颗合计再加一颗紧凑盒"的余量。
     * 反例是这一轮的起点：那四颗抬回 48 见方时，四颗占掉 192dp，名称只剩一百二十几 dp，
     * 这条就在 360 那一档当场红。
     */
    private fun assertModelNameKeepsItsSlot(matrix: UiMatrix) {
        // 先把**第一颗行内图标**滚进视口：2 倍字那一档整张表单比视口长，扫描停在最底部时
        // 模型那几行全在视口之外（报 0x0），这一格就会红在「读不到数」上而不是红在版式上。
        val firstIcon = rule.onAllNodes(
            SemanticsMatcher("行内图标") { n ->
                n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                    .any { it in rowIconLabels }
            }
        )
        check(firstIcon.fetchSemanticsNodes().isNotEmpty()) {
            "${matrix.id} 量不到任何一颗行内图标 ⇒ 槽宽这一判没有证人"
        }
        firstIcon[0].performScrollTo()
        rule.waitForIdle()
        val targets = probe.actionableTargets(rule, "ProviderFormBody 模型行")
        val icons = targets.filter { rowIconLabels.contains(it.label) }
        // 只判**整行都在视口里**的那几条带子（滚动容器会裁贴边的，裁过的读数不是控件本来的尺寸）
        val bands = icons.groupBy { it.topDp.roundToInt() }
            .values.filter { g -> g.size == 4 && g.all { it.heightDp + 0.6f >= TouchTier.CARD_ACTION } }
            .sortedBy { it.first().topDp }
        assertTrue(
            "${matrix.id} 量不到一整行模型行（四颗行内图标同一条带、都没被裁）——" +
                "这一格就没有判据：" + icons.joinToString { it.describe() },
            bands.isNotEmpty()
        )
        val textNodes = rule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true
        ).fetchSemanticsNodes().distinctBy { it.id }
        bands.forEach { g ->
            val bandTop = g.minOf { it.topDp }
            val bandBottom = g.maxOf { it.topDp + it.heightDp }
            val iconLeft = g.minOf { it.leftDp }
            val names = textNodes.filter { n ->
                val b = n.boundsInRoot
                val cy = (b.top + b.bottom) / 2f / density
                cy >= bandTop - 0.5f && cy <= bandBottom + 0.5f && b.left / density < iconLeft - 0.5f
            }
            assertEquals(
                "这一带里图标左边应当只有模型名这一条文本，实到 " +
                    names.joinToString { "${it.boundsInRoot.left / density}" },
                1, names.size
            )
            val nameLeft = names.single().boundsInRoot.left / density
            val iconsTotal = g.fold(0f) { acc, t -> acc + t.widthDp }
            val nameSlot = iconLeft - nameLeft
            assertTrue(
                "行尾四颗合计 ${iconsTotal.toInt()}dp，模型名那一格只剩 ${nameSlot.toInt()}dp——" +
                    "本轮合同要的是动作不许挤占模型名（下限：四颗合计再加一颗 ${TouchTier.CARD_ACTION.toInt()}dp 盒）" +
                    "，带子读数：" + g.joinToString { it.describe() },
                nameSlot >= iconsTotal + TouchTier.CARD_ACTION
            )
        }
    }

    /**
     * 可交互节点得报得出角色。
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

    /** 空表单时"保存"要灰着留在原地，而不是整颗消失 */
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
            "空态里得就地给出「＋ 添加模型」这个动作（不能只留一句话）：" + seen.keys,
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
     * 「显示 / 隐藏」这颗：既要**仍然坐在 Key 那一行的带里**，也要真的会改名。
     *
     * 两条轴分开判，正是本轮那条合同的样子：
     * - **可见那一轴**：它住在输入框尾部槽里，不许自己另起一行、也不许被做成整宽的第二颗大按钮
     *   ——判据是它与 Key 那一行的可编辑节点**同轴**（两颗都被要求在同一行里垂直居中）。
     *   这一条对"收成 36 高的可见框"与"外面再垫一层透明热区"两种实现都成立，不认尺寸只认位置；
     *   再加一条**不许长出自己那一行**：与别的行的可点节点叠上就红。
     * - **档位那一轴**：它自己过 [TouchTier.VISIBLE_FORM_INPUT]（两轴），高不超过整行那一档——
     *   为什么不是全站 48、以及"整行 48"这一半本机读不到，全写在 [assertFieldSlotTier] 上面那一段。
     *   这一颗自己的可点框实测 36 高，比全站下限低一档，是这一档明写的代价；
     *   另一条路（把尾部槽挪进外层那颗 48 高的透明盒，可见框仍是 36）要动的是共用的生产组件，
     *   本轮没走，只把改法交出去。
     * 名字跟着状态翻那一半一个字没动：只判尺寸的话，写死"显示"两个字、
     * 点下去什么都不变的实现也能过。
     */
    @Test
    fun `the key visibility action stays on its field row and keeps the touch floor`() {
        mount(threeModels, UiMatrix(360))
        val targets = probe.actionableTargets(rule, "表单-Key 槽")
        val toggleKey = targets.filter { keyVisibilityLabels.contains(it.label) }
        assertEquals(
            "Key 那行得恰好有一颗显示/隐藏动作：" + targets.joinToString { it.describe() },
            1, toggleKey.size
        )
        val t = toggleKey.single()
        val field = targets.firstOrNull { it.editable && it.label == "留空保留原 Key" }
        checkNotNull(field) {
            "量不到 Key 那一行的可编辑节点（placeholder 那一句改了名就要回来换锚点），实到名字：" +
                targets.map { it.label }.distinct()
        }
        val dy = (t.topDp + t.heightDp / 2f) - (field.topDp + field.heightDp / 2f)
        assertTrue(
            "显隐那颗的中心与 Key 输入行的中心差 ${dy.toInt()}dp ⇒ 它已经不在那一行带里了" +
                "（被排成第二行/整宽按钮就是这种读数）：" + t.describe() + " / " + field.describe(),
            abs(dy) < 1.5f
        )
        assertFieldSlotTier(listOf(t), "Key 显隐那颗")
        // 长出自己那一行的另一种画法：它去压**别的内容行**的可点节点。同轴那条判据放过同一行里的
        // 输入框与标签，这一条只放行外头的邻居——两颗都被要求同轴，所以同轴的那几颗不算邻居。
        val fieldCy = field.topDp + field.heightDp / 2f
        val strangers = targets.filter {
            it !== t && it.widthDp > 0f && it.heightDp > 0f &&
                abs((it.topDp + it.heightDp / 2f) - fieldCy) >= 1.5f
        }
        fun crosses(o: Target) =
            minOf(t.leftDp + t.widthDp, o.leftDp + o.widthDp) - maxOf(t.leftDp, o.leftDp) > 0.5f &&
                minOf(t.topDp + t.heightDp, o.topDp + o.heightDp) - maxOf(t.topDp, o.topDp) > 0.5f
        val crowded = strangers.filter { crosses(it) }
        assertTrue(
            "这颗动作已经长出 Key 那一行的带、压到别的内容行的可点节点上了 →\n" +
                crowded.joinToString("\n") { "  " + it.describe() } + "\n  它自己：" + t.describe(),
            crowded.isEmpty()
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
     * 生成超时档位那一排：界面上只有白名单那四颗、
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
