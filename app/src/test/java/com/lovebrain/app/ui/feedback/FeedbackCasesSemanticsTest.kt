package com.lovebrain.app.ui.feedback

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 已踩案例页（第9节第2条 那一版：分类 chips 与导出格式切换都删了，卡片只剩正文 + 时间）。
 *
 * ## 这一版盯的是三件事
 *
 * 1. **默认卡片不再出现被删掉的诊断字段**——判据不写"界面不该有『待分析』"这种中文字面量
 *    （这台 JVM 解析成英文，中文判据会红在不相干的理由上），而是拿**数据里的哨兵值**判：
 *    夹具里那一份 modelId / appVersion / buildType / contextMode / tokens 的值
 *    只要有一个上屏就是红。正证（同一份夹具的正文必须看得见）钉在每一格前面，
 *    防止"夹具根本没进分支"这种恒绿形状。
 * 2. **旧 JSON 仍然读得起来**——夹具不是手搓的 `FeedbackCase(...)`，而是一段
 *    1.40 那一代写盘的 JSON 文本现解出来的（字段全在，含 `dialogueSnapshot`）。
 *    解不出来这一格直接红，不会悄悄退化成空列表。
 * 3. **导出只剩 JSON 一档，并且没有预览这一格**——点页头那颗导出，
 *    交给 ViewModel 的格式实参必须是 `json`；`exportStepOf` 把 `Success` 映射成
 *    "直接写系统保存流程"，映射一旦改回"先弹一张预览"就红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackCasesSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /**
     * 旧数据的一份真形：1.40 那一代把诊断字段全写进 `cases.json`，
     * 这一版只是**不许把它们摆在屏上**，字段与读盘路径一个字都没动。
     * 每个要消失的值都是哨兵，不是自然语言，英文环境也一样判。
     */
    private val legacyJson = """
        [
          {
            "caseId": "legacy-1",
            "schemeIdentityKey": "STYLE:B",
            "candidateReply": "REPLY_SENTINEL 那一句候选回复",
            "categories": ["UNDERSTANDING_ERROR"],
            "reasons": ["REASON_SENTINEL"],
            "userNote": "NOTE_SENTINEL",
            "betterVersion": "BETTER_SENTINEL",
            "kbName": "default",
            "ideaHint": "IDEA_SENTINEL",
            "intentText": "INTENT_SENTINEL",
            "contextMode": "CTXMODE_SENTINEL",
            "modelId": "MODEL_SENTINEL",
            "promptVersion": "PROMPTVER_SENTINEL",
            "timestamp": "TIME_SENTINEL",
            "dialogueSnapshot": [{ "speaker": "PARTNER", "text": "DIALOG_SENTINEL" }],
            "memoryRefs": ["MEMORYREF_SENTINEL"],
            "appVersion": "APPVER_SENTINEL",
            "buildType": "BUILD_SENTINEL",
            "promptTokens": 1111,
            "completionTokens": 2222,
            "costYuan": 9.87,
            "status": "PENDING"
          }
        ]
    """.trimIndent()

    private val legacyCases: List<FeedbackCase> = Json.decodeFromString(legacyJson)

    private fun mount(
        cases: List<FeedbackCase>,
        exportState: SetupViewModel.ExportState = SetupViewModel.ExportState.Idle
    ): SetupViewModel {
        val vm = mockk<SetupViewModel>(relaxed = true).also {
            every { it.feedbackCases } returns MutableStateFlow(cases)
            every { it.feedbackLoading } returns MutableStateFlow(false)
            every { it.feedbackError } returns MutableStateFlow<String?>(null)
            every { it.exportState } returns MutableStateFlow(exportState)
        }
        rule.setContent {
            UiMatrix(600).RenderIn(LocalDensity.current.density) {
                FeedbackCasesScreen(viewModel = vm, onBack = {})
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        return vm
    }

    // ── 夹具自己先进场（下面每一格都靠这条排除"分支没走到"的恒绿）────────────────

    @Test
    fun `the legacy fixture really decodes and really reaches the screen`() {
        assertEquals("旧 JSON 解不出那一条就等于下面每一格都在空转", 1, legacyCases.size)
        val c = legacyCases.single()
        assertEquals("MODEL_SENTINEL", c.modelId)
        assertEquals(listOf(FeedbackCategory.UNDERSTANDING_ERROR), c.categories)
        mount(legacyCases)
        rule.onNodeWithText("REPLY_SENTINEL 那一句候选回复", substring = true).assertExists()
    }

    // ── ：默认卡片只剩正文 + 时间 ──────────────────────────────────────────

    @Test
    fun `the collapsed card shows the reply and the time and nothing else`() {
        mount(legacyCases)
        rule.onNodeWithText("REPLY_SENTINEL", substring = true).assertExists()
        rule.onNodeWithText("TIME_SENTINEL", substring = true).assertExists()
        HIDDEN_WHEN_COLLAPSED.forEach { sentinel ->
            assertEquals(
                "被删掉的展示位又露出来了（哨兵：$sentinel）",
                0, rule.onAllNodesWithText(sentinel, substring = true).fetchSemanticsNodes().size
            )
        }
    }

    @Test
    fun `expanding shows the real context and still hides the diagnostic fields`() {
        mount(legacyCases)
        // 整卡可点 = 展开（这一颗在语义树里是这一屏最宽的可点节点，认形状不认标签）
        val card = probe.actionableTargets(rule, "已踩案例页·展开前").maxByOrNull { it.widthDp }
            ?: error("一个可点节点都没量到——这页没挂上")
        assertEquals("整卡可点=展开，语义要报成 Button：" + card.describe(), "Button", card.role)
        rule.onAllNodes(hasText("REPLY_SENTINEL", substring = true) and hasClickAction())[0]
            .performClick()
        rule.mainClock.advanceTimeBy(16L)

        // 展开那一层：用户理解所需的真实上下文 + 旧数据里确实填过的理由/补充
        rule.onNodeWithText("DIALOG_SENTINEL", substring = true).assertExists()
        rule.onNodeWithText("NOTE_SENTINEL", substring = true).assertExists()
        rule.onNodeWithText("BETTER_SENTINEL", substring = true).assertExists()
        rule.onNodeWithText("REASON_SENTINEL", substring = true).assertExists()
        rule.onNodeWithText("IDEA_SENTINEL", substring = true).assertExists()
        rule.onNodeWithText("INTENT_SENTINEL", substring = true).assertExists()

        // 被删掉的展示位在展开之后仍然不许出现（反例：把"清理展示"做成"清理到展开里"）
        HIDDEN_ALWAYS.forEach { sentinel ->
            assertEquals(
                "被删掉的展示位又露出来了（哨兵：$sentinel）",
                0, rule.onAllNodesWithText(sentinel, substring = true).fetchSemanticsNodes().size
            )
        }
    }

    /**
     * 没有的内容不许长成"暂无XX"那一排空字段。
     * 夹具是**空**的那一份：理由、补充、对话、意图、时间全没有（这就是现在点踩落盘的真实形状）。
     *
     * 判据是**展开前后的文本节点数不变**：展开那一层如果给空字段排了行（哪怕写着"暂无"），
     * 节点数就会涨——这比"数某一句中文"抗改版式，也比"源码里有没有那句"真。
     */
    @Test
    fun `a case with no extra data renders no empty field rows`() {
        val bare = FeedbackCase(
            caseId = "bare-1",
            schemeIdentityKey = "STYLE:B",
            candidateReply = "BARE_REPLY_SENTINEL",
            categories = emptyList(),
            reasons = emptyList(),
            timestamp = ""
        )
        mount(listOf(bare))
        val anyText = androidx.compose.ui.test.SemanticsMatcher("带文字的节点") {
            it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)
        }
        val before = rule.onAllNodes(anyText).fetchSemanticsNodes().size
        assertTrue("夹具那颗正文都没上屏，这格无从判起（实到 $before 行）", before >= 1)
        rule.onAllNodes(hasText("BARE_REPLY_SENTINEL", substring = true) and hasClickAction())[0]
            .performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            "空白一条案例展开后不该长出任何空字段行",
            before, rule.onAllNodes(anyText).fetchSemanticsNodes().size
        )
        assertEquals(
            "空的「【】」标题排又长回来了",
            0, rule.onAllNodesWithText("【").fetchSemanticsNodes().size
        )
    }

    // ── ：分类 chips 那一族整排退场（行为与屏上文字两头判）───────────────────

    /**
     * 列表就是**全部**案例，不再按分类筛：两条案例一条带 `UNDERSTANDING_ERROR`、
     * 一条 `categories = []`（今天点踩落盘的真实形状），两条都必须在这一棵树上。
     *
     * 反例（这一格要挡的坏法）：有人把默认筛选做成"只看某一类"或"只看待分析"——
     * 那一条就从不在这棵树里，这一格红。两颗 `caseId` 必须不同：列表按 `caseId` 取 key，
     * 同 id 会让 LazyColumn 当场抛，那种红的是夹具不是判据。
     */
    @Test
    fun `every case is listed whatever its category`() {
        val two = listOf(
            legacyCases.single(),
            FeedbackCase(
                caseId = "no-category-1",
                schemeIdentityKey = "DIRECTION:F",
                candidateReply = "NOCATEGORY_SENTINEL",
                categories = emptyList(),
                reasons = emptyList()
            )
        )
        mount(two)
        rule.onNodeWithText("REPLY_SENTINEL", substring = true).assertExists()
        rule.onNodeWithText("NOCATEGORY_SENTINEL", substring = true).assertExists()
    }

    /**
     * 那一排 chips 与那颗导出格式切换**不能还在屏上**。
     *
     * 判据不读"源码里还有没有 `FilterChip` 这个符号"（改了名没改行为它是瞎的），而是拿
     * 资源里那四句现成的话去语义树里点名：`feedback_filter_all`（「全部」）、
     * `feedback_format_markdown`（「Markdown」）、`feedback_format_json`（「JSON」）、
     * `feedback_export_markdown`（「导出 MD」）。这四条资源**本轮没删**（`res/` 那一整棵归主线程），
     * 所以它们照样解析得出来——谁把那一排 chip 或 Markdown/JSON 切换接回来，节点当场数得到，
     * 这一格就红。
     *
     * 正向对照钉在同一棵树里：页头那颗 `feedback_export_json`（「导出」）必须数得到；
     * 没有这一句，上面那四个 0 就可能只是"探针压根没进场"那种恒绿。
     * 逐条 exact 匹配（`substring` 默认关）：「导出」不等于「JSON」/「导出 MD」，不会被负例误伤。
     */
    @Test
    fun `no filter chips and no export format switch survived`() {
        mount(legacyCases)
        assertEquals(
            "正向对照失效：页头那颗导出都不在树上，下面那几条 0 不算证人",
            1, rule.onAllNodesWithText(context.getString(R.string.feedback_export_json))
                .fetchSemanticsNodes().size
        )
        // R18：导出只有 JSON 一档，旧格式切换已整组删除——资源也一并清理了。
        // 这里改用内联字面量做负例探针，不依赖已删的资源 key。
        listOf("Markdown", "JSON", "导出 MD").forEach { label ->
            assertTrue("负例探针文案为空", label.isNotBlank())
            assertEquals(
                "删掉的 chip / 格式切换又露出来了（$label）",
                0, rule.onAllNodesWithText(label).fetchSemanticsNodes().size
            )
        }
    }

    // ── ：导出只有 JSON 一档，而且不弹预览 ─────────────────────────────────

    @Test
    fun `tapping export hands the whole list over as json`() {
        val vm = mount(legacyCases)
        val format = slot<String>()
        val sent = slot<List<FeedbackCase>>()

        rule.onNodeWithText(context.getString(R.string.feedback_export_json)).performClick()
        rule.mainClock.advanceTimeBy(16L)

        verify(exactly = 1) { vm.exportFeedback(capture(sent), capture(format)) }
        assertEquals("格式只剩 JSON 这一档", "json", format.captured)
        assertEquals("导的是这一页的全部案例，没有筛选那一层了", 1, sent.captured.size)
        assertEquals("legacy-1", sent.captured.single().caseId)
    }

    /**
     * 导出状态 → 动作的映射：`Success` 那一份正文**直接**交给系统保存流程。
     *
     * 反例（这次要防的就是它）：把这一格改回"先开一张预览弹窗、用户再点一次保存"——
     * 映射会少掉 `WriteToSystem` 这一支或者丢掉正文，这格当场红；
     * 把 `Error` 映射成弹窗同样红，因为这一版错误只许是一行就地短提示。
     */
    @Test
    fun `the export state maps to a direct save and an inline error, never a preview`() {
        val text = "[{\"caseId\":\"x\"}]"
        assertEquals(
            ExportStep.WriteToSystem(text),
            exportStepOf(SetupViewModel.ExportState.Success(text))
        )
        assertEquals(
            ExportStep.ShowInlineError("导出失败，请重试"),
            exportStepOf(SetupViewModel.ExportState.Error("导出失败，请重试"))
        )
        assertEquals(ExportStep.Wait, exportStepOf(SetupViewModel.ExportState.Loading))
        assertEquals(ExportStep.Wait, exportStepOf(SetupViewModel.ExportState.Idle))
    }

    /**
     * 导出那一档 MIME 与文件名必须是一对：这一格只防一类事故——
     * 有人把 MIME 换成 markdown 而文件名还留着 `.json`（或反过来）。
     * 它**不**证明链路真的用了这两个常量，那一条由上面那格 `verify` 钉。
     */
    @Test
    fun `the export mime and the file name agree`() {
        assertTrue(FEEDBACK_EXPORT_MIME, FEEDBACK_EXPORT_MIME.endsWith("/json"))
        assertTrue(FEEDBACK_EXPORT_FILE_NAME, FEEDBACK_EXPORT_FILE_NAME.endsWith(".json"))
    }

    /** 导出失败那一句要**在这一屏读得到**（旧写法在另一扇窗口里，页面这一棵树看不见它） */
    @Test
    fun `an export failure is stated on the page itself`() {
        val message = "EXPORT_FAILED_SENTINEL"
        mount(legacyCases, SetupViewModel.ExportState.Error(message))
        rule.onNodeWithText(message, substring = true).assertExists()
    }

    // ── ：页名走资源，与首页入口同一句 ─────────────────────────────────────

    @Test
    fun `the header says what the resource says`() {
        mount(legacyCases)
        val title = context.getString(R.string.feedback_cases_title)
        assertTrue("资源里那句不能是空的", title.isNotBlank())
        rule.onNodeWithText(title).assertExists()
    }

    /**
     * 中文那一份页名就叫"已踩案例"。
     * 反例：只翻英文、或首页入口与页头各抄一份（那两份迟早漂）——这一格读的是
     * `values/strings.xml` 那一族在 `zh` 配置下的读数，两边不同步就红。
     */
    @Test
    fun `the chinese page name is the one the user asked for`() {
        val zh = android.content.res.Configuration().apply {
            setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
        }
        val localized = context.createConfigurationContext(zh)
        assertEquals(
            "页名必须是「已踩案例」（入口与页头同一资源），实到：" +
                localized.getString(R.string.feedback_cases_title),
            "已踩案例",
            localized.getString(R.string.feedback_cases_title)
        )
    }

    // ── 既有合同：页头两颗的角色、禁用态 ─────────────────────────────────────

    @Test
    fun `the header actions declare a role`() {
        mount(legacyCases)
        val header = probe.laid(probe.actionableTargets(rule, "已踩案例页·页头"))
            .filter { it.topDp < com.lovebrain.app.core.designsystem.AppDimens.TOUCH_TARGET_MIN_DP }
        assertEquals(
            "页头这一带应当量到两颗（返回 + 导出），实到：" + header.joinToString { it.describe() },
            2, header.size
        )
        header.forEach {
            assertEquals("页头这两颗都是按钮：" + it.describe(), "Button", it.role)
        }
    }

    @Test
    fun `the export action stays visible but reports itself disabled when the list is empty`() {
        mount(emptyList())
        assertEquals(
            "零结果时导出那颗要还在树上（灰着也算说得出话）",
            1,
            rule.onAllNodes(hasText(context.getString(R.string.feedback_export_json)))
                .fetchSemanticsNodes().size
        )
        val disabled = probe.laid(probe.actionableTargets(rule, "已踩案例页·空结果"))
            .filter { it.disabled }
        assertTrue(
            "导出那颗在零结果时必须报 disabled：" + disabled.joinToString { it.describe() },
            disabled.isNotEmpty()
        )
    }

    private companion object {
        /** 折叠与展开都不许出现的诊断字段值（哨兵 = 夹具里那份数据本体） */
        val HIDDEN_ALWAYS = listOf(
            "MODEL_SENTINEL",          // 模型名
            "CTXMODE_SENTINEL",        // 上下文模式：full
            "APPVER_SENTINEL",         // 版本：1.40-re1
            "BUILD_SENTINEL",          // （release）
            "PROMPTVER_SENTINEL",      // 提示词版本
            "MEMORYREF_SENTINEL",      // 内部记忆引用 id
            "1111", "2222",            // tokens
            "9.87"                     // 费用
        )

        /** 折叠那一格额外不许出现的（展开后才算"用户理解所需的真实上下文"） */
        val HIDDEN_WHEN_COLLAPSED = HIDDEN_ALWAYS + listOf(
            "REASON_SENTINEL", "NOTE_SENTINEL", "BETTER_SENTINEL",
            "IDEA_SENTINEL", "INTENT_SENTINEL", "DIALOG_SENTINEL"
        )
    }
}
