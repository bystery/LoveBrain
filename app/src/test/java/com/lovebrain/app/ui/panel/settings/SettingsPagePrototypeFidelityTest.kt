package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.R
import com.lovebrain.app.ReplyCardLayout
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceBase
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.ui.theme.LoveBrainTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 悬浮窗设置页 × 设计原型（那份 HTML 示意稿）的**整页几何尺**。
 *
 * 这一格存在的理由写在 2026-10-12 那份原型换算表第一段：10-11 那一版把**结构**改对了（三组、行、分段、
 * 同行输入与保存），但**没照原型画控件**，用户回归测试后判"根本不一样"。结构判据读不出脸，
 * 因为脸是几何：一页几种字阶、轨道包不包得住格子、滑杆吃不吃满整行、保存有没有被挤走、行与行
 * 之间还剩几 dp。所以这里只写**量出来的**判据——每一条都从语义树取 `boundsInRoot` 换成 dp，
 * 数值来源在注释里指回原型那一族 CSS；"看起来对"一句都不写。
 *
 * ## 每一条判据都有牙（这一版的坑就出在这）
 *
 * 一把只会往松里调的尺永远绿。这里的做法是**每条判据配一颗常驻的反例**，而不是"手动改坏夹具看一眼"
 * （那种红不留档，第二天没人记得它红过）：判据本体写成 `xxxViolations(量到的那几颗 Target) → List<String>`
 * 这一族的纯函数，产品那一侧用它判"违例为空"，反例那一侧用**同一颗函数**判"违例非空"。
 * 反例挂的是真的错形状（组名塞进卡里、`labelMedium` 与 `labelLarge` 并排、三颗各自带描边的盒子中间
 * 空一大段、没有热区层的 28dp 胶囊、逐项一张卡），不是手填的假数字——于是"量具瞎"与"产品画错"
 * 会红在不同的那句上，两种都能被这一族用例抓到。
 *
 * ## 与别的格的分工（不重复挂尸体）
 *
 * · `SettingsPageStructureTest`（席 A）判**源码那一侧**的结构（三张框、分隔线色、组名那一族的脸）；
 * · `SettingsPageSemanticsTest`（这一族的主人）判**语义那一侧**（页头档位、意图两态、读数只有一份）；
 * · 各 Entry 自己那一格判**单颗件**的形状（滑杆 125dp 上限、三档宽度下的保存自然宽）；
 * · 本格判**接成整页之后**才成立的事：整页是否只有一种行标签字阶、尾部选择器是否共一条右缘、
 *   分段轨道在整行的宽度约束里包不包得住格子、整页密度、新形状上热区有没有退档。
 *   整页这一侧隔着组外框与滚动柱量不出单颗件那几个数；反过来单颗件那一侧也看不见"一页几种字阶"。
 *
 * ## 夹具边界（如实写明，别把它读成"真机"）
 *
 * 宿主那一层按 `SettingsPageVisualBaselineTest` 同一份复现：`LoveBrainTheme` 长在悬浮窗
 * `ComposeView` 的根上，面板根部给 `padding(start/end = Spacing.xl, bottom = Spacing.lg)` 与表面底色。
 * 这两颗不补时本机实量到的是"组名贴窗口左缘被切半字 + 滑杆回到 Material 紫"——那是量具瞎。
 * `@Config(qualifiers)` 钉死 `zh-rCN`（locale 不写等于不固定，换台机器换语言就换读数）与
 * `any-xhdpi`（density 2.0：11sp 与 12sp 的宽度差在 1.0 那一档只差 1px，量不稳）。
 * 窗口取原型那一档 300dp（`.device{width:300px}`），另按 `.resizer` 那三颗补 260 / 350 两档，
 * 只在"保存看得见"那一条上换档。`UiMatrix` 换的是**被测子树的布局约束**，不是这台设备的屏幕
 * （那条边界写在 [UiMatrix] 文件头）。
 *
 * ⚠ **跨席合同**：本格按锚点合同那张 `testTag` 表找节点。表里席 C / 席 E 那几颗这一版还没落
 * （`settings-row-size` / `settings-row-reply` / `settings-row-kb` / `settings-row-assistant` /
 * `settings-pill-size` / `settings-pill-reply`），所以依赖它们的判据现在红在"锚点实到 0 颗"那一句上——
 * 那是**本来的形状**，不是量具坏了；每颗锚点该由哪一席挂，写在那句失败信息里。
 * 这一颗文件一个字不碰生产源码树（`app/src/main` 底下那一层）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPagePrototypeFidelityTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    // ═══════════ 文案锚点：一律现读资源（这台机器可能解析成英文，写死中文会假红） ═══════════

    private val opacityLabel: String get() = ctx.getString(R.string.settings_opacity_label)
    private val sizeLabel: String get() = ctx.getString(R.string.settings_bubble_size_row_label)
    private val replyLabel: String get() = ctx.getString(R.string.settings_reply_card_row_label)
    private val kbLabel: String get() = ctx.getString(R.string.settings_kb_current_label)
    private val intentLabel: String get() = ctx.getString(R.string.intent_label)
    private val assistantLabel: String get() = ctx.getString(R.string.settings_assistant_label)
    private val saveLabel: String get() = ctx.getString(R.string.intent_save)
    private val backName: String get() = ctx.getString(R.string.common_back)
    private val collapseName: String get() = ctx.getString(R.string.panel_collapse)

    private val groupTitles: List<String>
        get() = listOf(
            ctx.getString(R.string.settings_group_display),
            ctx.getString(R.string.settings_group_content),
            ctx.getString(R.string.settings_group_assistant)
        )

    /**
     * 屏上那一句百分比：落盘轴（不透明度 80）经 [PanelBackdropOpacity.transparencyOf] 镜像成显示轴。
     * 判据与生产读同一颗换算口 ⇒ 换算被改回去（直接把不透明度印在"透明度"上）时这一句当场红。
     */
    private fun percentReadout(storedOpacityPercent: Int): String =
        ctx.getString(R.string.settings_opacity_percent, PanelBackdropOpacity.transparencyOf(storedOpacityPercent))

    // ═══════════ 行级锚点表（锚点合同） ═══════════

    /**
     * 六行的清单：[RowSpec.tag] 是锚点合同那一颗行根锚点，[RowSpec.owner] 是**该由哪一席挂上它**，
     * [RowSpec.refTag] 只给夹具自己用（同文案 `labelLarge` 参照那颗，理由见 [sameFaceViolations]）。
     */
    private data class RowSpec(
        val key: String,
        val tag: String,
        val owner: String,
        val label: String,
        val group: Int,
        val refTag: String
    )

    private fun rowSpecs(): List<RowSpec> = listOf(
        RowSpec("透明度", ROW_OPACITY_TAG, OWNER_B, opacityLabel, GROUP_DISPLAY, REFERENCE_LABEL_PREFIX + "0"),
        RowSpec("图标大小", ROW_SIZE_TAG, OWNER_C, sizeLabel, GROUP_DISPLAY, REFERENCE_LABEL_PREFIX + "1"),
        RowSpec("回复卡片", ROW_REPLY_TAG, OWNER_C, replyLabel, GROUP_DISPLAY, REFERENCE_LABEL_PREFIX + "2"),
        RowSpec("当前知识库", ROW_KB_TAG, OWNER_E, kbLabel, GROUP_CONTENT, REFERENCE_LABEL_PREFIX + "3"),
        RowSpec("持续意图", ROW_INTENT_TAG, OWNER_D, intentLabel, GROUP_CONTENT, REFERENCE_LABEL_PREFIX + "4"),
        RowSpec("悬浮助手", ROW_ASSISTANT_TAG, OWNER_E, assistantLabel, GROUP_ASSISTANT, REFERENCE_LABEL_PREFIX + "5")
    )

    private fun specByKey(key: String): RowSpec = rowSpecs().first { it.key == key }

    /** 三组各领哪几行（顺序 = 原型 `.content` 里的读序：显示 → 内容 → 悬浮助手） */
    private fun groupOf(group: Int): List<RowSpec> = rowSpecs().filter { it.group == group }

    // ═══════════ 夹具 ═══════════

    /** 宿主那两份意图读数（这一页是无状态的：测试替宿主拿着，才能在同一次挂载里翻那一颗状态） */
    private class PageHost(
        intentEnabledAtStart: Boolean = false,
        intentTextAtStart: String = LONG_DRAFT
    ) {
        val intentEnabled: MutableState<Boolean> = mutableStateOf(intentEnabledAtStart)
        val intentText: MutableState<String> = mutableStateOf(intentTextAtStart)
    }

    /**
     * 整页挂载：宿主那一层复现生产（主题 + 左右 `Spacing.xl` 内缩 + 表面底色），
     * [extras]（参照与反例）挂在**页桶之外**、并用 `offset(y = 窗口高)` 挪到页下面。
     *
     * 为什么不是 `Column { 页 ; 参照 }`：`Column` 会把剩下的纵向约束传给后面的子节点——页桶已经
     * 吃掉整窗高，参照就会被压成 `0x0`，那是"量具自己看不见参照"（`0x0` 会让所有宽度比对变成
     * 0 对 0，整格恒绿）。`Box` 把同一份约束分别交给每个子节点，参照拿到满窗，量得到数。
     *
     * 挂完先判"整页画进这一窗"再让用例量东西：`verticalScroll` 会把滚出去的那半截照样组合进树、
     * 照样报完整文案，拿它的几何做判据 = 拿一张看不见的图做判据（同一件事写在
     * `SettingsPageVisualBaselineTest#assertWholePageIsDrawnBeforeCapture`）。
     */
    private fun mountPage(
        widthDp: Int = PROTOTYPE_WIDTH_DP,
        host: PageHost = PageHost(),
        extras: @Composable () -> Unit = {}
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(widthDp, heightDp = WINDOW_HEIGHT_DP).RenderIn(deviceDensity) {
                LoveBrainTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WINDOW_HEIGHT_DP.dp)
                            .background(SurfaceBase)
                            .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg)
                            .testTag(PAGE_BUCKET_TAG)
                    ) {
                        LoveBrainSettingsContent(
                            onBack = {},
                            onCollapse = {},
                            assistantOn = true,
                            onAssistantEnable = {},
                            onAssistantClose = {},
                            opacityPercent = OPACITY_STORED_PERCENT,
                            onOpacityPreview = {},
                            onOpacityCommit = {},
                            bubbleSizeDp = BubbleSizeTier.STANDARD_DP,
                            onBubbleSizeChange = {},
                            replyCardVertical = true,
                            onReplyCardVerticalChange = {},
                            intentEnabled = host.intentEnabled.value,
                            intentText = host.intentText.value,
                            // 测试替宿主把这一笔落回读数（真落盘在宿主那一侧，本格不判那条链）
                            onIntentChange = { text, enabled ->
                                host.intentText.value = text
                                host.intentEnabled.value = enabled
                            },
                            knowledgeBases = listOf(kb(ACTIVE_KB_NAME), kb(SECOND_KB_NAME)),
                            activeKbName = ACTIVE_KB_NAME
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset(y = WINDOW_HEIGHT_DP.dp)
                            .testTag(EXTRAS_BUCKET_TAG)
                    ) { extras() }
                }
            }
        }
        rule.waitForIdle()
        assertWholePageIsInsideTheWindow()
    }

    /** 反例夹具：只挂替身、不挂产品（这一族用例判的是**尺有没有牙**，与产品画成什么样无关） */
    private fun mountDecoys(content: @Composable () -> Unit) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(PROTOTYPE_WIDTH_DP, heightDp = WINDOW_HEIGHT_DP).RenderIn(deviceDensity) {
                LoveBrainTheme {
                    Box(modifier = Modifier.testTag(EXTRAS_BUCKET_TAG)) { content() }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun kb(name: String) = KnowledgeBase(
        name = name,
        displayName = name,
        updatedAt = "2026-10-12 00:00",
        stage = "",
        turnCount = 0,
        topicCount = 0,
        active = name == ACTIVE_KB_NAME
    )

    // ═══════════ 读数工具（全部限定在页桶子树或自己的 tag 上，替身混不进来） ═══════════

    private val SemanticsProbe.Target.rightEdge: Float get() = leftDp + widthDp
    private val SemanticsProbe.Target.bottomEdge: Float get() = topDp + heightDp
    private val SemanticsProbe.Target.centerY: Float get() = topDp + heightDp / 2f

    private fun targetsOf(nodes: List<SemanticsNode>): List<SemanticsProbe.Target> = nodes.map { probe.of(it) }

    /**
     * 一次读数：摆得出来的那几颗进判据，被压成 `0x0` 的那几颗**留在失败信息里**。
     *
     * 为什么要这一颗而不是直接用 [SemanticsProbe.laid]：`laid` 在"一颗都没摆出来"时抛的是一句
     * "全被压成 0x0"，而这一族的判据里有两种不同的红（那一颗压根没画 vs 画了但被容器压掉），
     * 必须分得开；同时"筛掉的那一半"不许悄悄过去（拿过滤藏读数就是另一种假绿，踩过的坑 同一条）。
     */
    private class Reading(val laid: List<SemanticsProbe.Target>, val all: List<SemanticsProbe.Target>) {
        val droppedNote: String
            get() {
                val dropped = all.filter { it.widthDp <= 0f || it.heightDp <= 0f }
                return if (dropped.isEmpty()) {
                    ""
                } else {
                    "（另有 ${dropped.size} 颗被压成 0x0：" + dropped.joinToString("; ") { it.describe() } + "）"
                }
            }
    }

    private fun read(nodes: List<SemanticsNode>): Reading {
        val all = targetsOf(nodes)
        return Reading(all.filter { it.widthDp > 0f && it.heightDp > 0f }, all)
    }

    private fun nodesWithTag(tag: String, want: Int, owner: String): List<SemanticsNode> {
        // 只有 testTag 的节点不进合并语义树 ⇒ 读未合并那一侧（本仓既有先例）
        val nodes = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(
            "锚点 `$tag` 应当恰有 $want 颗（锚点合同那张行级锚点表，这一颗由 $owner 挂在行根/容器那一颗身上）。" +
                "实到 ${nodes.size} 颗：0 颗 = 那一席还没落，>1 颗 = 同一个 tag 挂了两处，两种都不许用别的读数顶",
            want,
            nodes.size
        )
        return nodes
    }

    private fun anchorTarget(tag: String, owner: String): SemanticsProbe.Target =
        probe.of(nodesWithTag(tag, 1, owner).single())

    private fun pageBucket(): SemanticsNode = nodesWithTag(PAGE_BUCKET_TAG, 1, "本格夹具").single()

    private fun subtreeOf(node: SemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap { subtreeOf(it) }

    private fun SemanticsNode.textValue(): String? =
        config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

    /** 页桶里所有**带文案**的节点（未合并那一侧 ⇒ 一颗 `Text` 就是它自己那块版面） */
    private fun pageTextNodes(): List<SemanticsNode> =
        subtreeOf(pageBucket()).filter { it.textValue() != null }

    private fun textNodesUnder(row: SemanticsNode): List<SemanticsNode> =
        subtreeOf(row).filter { it.textValue() != null }

    private fun chipsUnder(node: SemanticsNode): List<SemanticsNode> =
        subtreeOf(node).filter { it.config.getOrNull(SemanticsProperties.TestTag) == LbTags.CHIP }

    /**
     * 行标签那颗：**在行根子树里**按整串文案认（不整页搜）。
     *
     * 为什么必须限定到行里：`settings_assistant_label` 与组名那颗资源同字（原型换算表 明写的「悬浮助手」），
     * 整页搜会得到两颗；参照桶里那颗同文案参照也在同一棵树上。限定之后"恰一颗"才真的在说
     * 这一行只画了一份标签。
     */
    private fun labelTargetOf(spec: RowSpec): SemanticsProbe.Target {
        val reading = read(textNodesUnder(rowNode(spec)).filter { it.textValue() == spec.label })
        assertEquals(
            "「${spec.key}」那一行（锚点 `${spec.tag}`，${spec.owner}）里行标签必须恰量到一颗，实到 ${reading.laid.size}" +
                reading.droppedNote + "：少一颗 = 那一行没用族里那颗 `LbRowLabel` 画；多一颗 = 同一件事屏上说了两遍。" +
                reading.all.joinToString("; ") { it.describe() },
            1,
            reading.laid.size
        )
        return reading.laid.single()
    }

    private fun rowNode(spec: RowSpec): SemanticsNode = nodesWithTag(spec.tag, 1, spec.owner).single()
    private fun rowTarget(spec: RowSpec): SemanticsProbe.Target = probe.of(rowNode(spec))

    /**
     * 尾部那颗：行子树里（行根自己不算——它是满宽的）**右缘最远**的摆得出来的节点。
     *
     * 为什么不逐行点名某颗 tag：原型 `.action-area{justify-content:flex-end;flex:1}` 说的是
     * "这一行最右那一样东西贴右缘"，那一样东西可能是 `%` 读数、分段轨道、箭头、开关或那颗「保存」。
     * 判据要的本来就是"最右那一颗"；逐颗点名反而让"尾部少画一颗"读成绿。
     */
    private fun trailingTarget(spec: RowSpec): SemanticsProbe.Target {
        val rowId = rowNode(spec).id
        val reading = read(subtreeOf(rowNode(spec)).filter { it.id != rowId })
        assertTrue("「${spec.key}」那一行子里量不出任何一颗摆得出来的节点" + reading.droppedNote, reading.laid.isNotEmpty())
        return reading.laid.maxByOrNull { it.rightEdge }!!
    }

    /** 组名那颗：整页里与该组名同字、且**底边在它那张框第一行上沿之上**的那一颗（恰一颗） */
    private fun groupTitleTarget(group: Int): SemanticsProbe.Target {
        val title = groupTitles[group]
        val firstRow = rowTarget(groupOf(group).first())
        val reading = read(
            pageTextNodes().filter {
                it.textValue() == title && (it.boundsInRoot.bottom / density) <= firstRow.topDp + EDGE_TOLERANCE_DP
            }
        )
        assertEquals(
            "组名「$title」在它那组第一行（`${groupOf(group).first().tag}`）之上必须恰量到一颗，实到 ${reading.laid.size}" +
                reading.droppedNote + "（0 颗 = 组名没了或掉进框里；2 颗 = 屏上有第二份同字）：" +
                reading.all.joinToString("; ") { it.describe() },
            1,
            reading.laid.size
        )
        return reading.laid.single()
    }

    /** 页桶里"看得见的内容"（带文案 / 带名字 / 可编辑 / 可点 / 可切换），用来判整页画进没画进这一窗 */
    private val contentish: SemanticsMatcher = SemanticsMatcher("has Text/Desc/action/toggle") { node ->
        node.config.contains(SemanticsProperties.Text) ||
            node.config.contains(SemanticsProperties.ContentDescription) ||
            node.config.contains(SemanticsProperties.EditableText) ||
            node.config.contains(SemanticsProperties.ToggleableState) ||
            hasClickAction().matches(node)
    }

    private fun assertWholePageIsInsideTheWindow() {
        val content = read(subtreeOf(pageBucket()).filter { contentish.matches(it) })
        assertTrue("这一窗里一个内容节点都没量到 ⇒ 量具瞎，本文件所有判据都会跟着变成假绿", content.laid.isNotEmpty())
        val lowest = content.laid.maxByOrNull { it.bottomEdge }!!
        assertTrue(
            "整页没画进 ${WINDOW_HEIGHT_DP}dp 这一窗：最低那颗 ${lowest.describe()} 的底边 ${lowest.bottomEdge}dp 已经在折线以下",
            lowest.bottomEdge <= WINDOW_HEIGHT_DP + EDGE_TOLERANCE_DP
        )
    }

    /** 页桶里的可交互节点（`SemanticsProbe` 那份"可交互"定义的整页版；参照与替身不在页桶里，混不进来） */
    private fun pageActionables(): List<SemanticsProbe.Target> {
        val actionable = hasClickAction() or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)
        return read(subtreeOf(pageBucket()).filter { actionable.matches(it) }).laid
    }

    // ═══════════ 判据本体（纯函数：产品那侧判"违例为空"，反例那侧判"违例非空"） ═══════════

    /**
     * 判据一：组名在它那张框**之外**。两条各挡一种坏法：
     * · 组名的底边要在它那一组第一行上沿之上（原型 `.section-title` 排在 `.group` 之前）；
     * · 组名的左缘要在"框内那一圈横向内缩"**之外**——组外框是横向内缩的唯一主人（原型 `.lbl` 那一档），
     *   于是名字贴框的左缘、框内内容比名字再往右一档。把组名塞进框里画，它就和行标签同一条左缘，
     *   第二句当场红（10-11 那一版那张脸的一半就是"结构对了、组名还在框内"）。
     *
     * ⚠ 这里**不抄** `Spacing.lg` 那 12dp：判据只认"名字的左缘在行内容左缘之左"这一件事实。
     * ⚠ 也**量不到框本身**：`settingsEntryGroupCard()` 只画 clip/background/border/padding，
     *   一颗语义都不交出 ⇒ "组名上沿 vs 框上沿"这一句现在只能由第一行的上沿顶。要钉成框那一颗，
     *   需要席 A 在框上补一颗 tag（已写进交付报告）。
     */
    private fun outsideFrameViolations(
        title: SemanticsProbe.Target,
        firstRow: SemanticsProbe.Target,
        firstLabel: SemanticsProbe.Target
    ): List<String> = buildList {
        if (title.bottomEdge > firstRow.topDp + EDGE_TOLERANCE_DP) {
            add("组名「${title.label}」的底边 ${title.bottomEdge}dp 不在第一行上沿 ${firstRow.topDp}dp 之上")
        }
        val inset = firstLabel.leftDp - title.leftDp
        if (inset < NAME_OUTSIDE_INSET_MIN_DP) {
            add("组名「${title.label}」左缘 ${title.leftDp}dp 与行标签左缘 ${firstLabel.leftDp}dp 只差 ${inset}dp：" +
                "两者在同一条线上 ⇒ 组名被画进了框里（原型 `.section-title` 长在 `.group` 之外）")
        }
    }

    /**
     * 判据二：整页行标签**同一档字**。三条：
     * · 六颗量到的文本节点高度彼此相等（±[TEXT_HEIGHT_TOLERANCE_DP]）；
     * · 每一颗的**宽度**等于"同一串字用 `labelLarge` 排出来的自然宽"（±[TEXT_HEIGHT_TOLERANCE_DP]）；
     * · 都不许大于组名高度的两倍（原型 `.section-title` 10.5px / `.lbl` 12px 那层主次）。
     *
     * ⚠ 第二条不是多余，是这一条**唯一的牙**：本仓库 `AppTypography.labelLarge`(12sp) 与
     * `labelMedium`(11sp) 的 `lineHeight` **同为 16sp**，只看文本节点高度量不出这两档的分别
     * （`instrument separates the two twelve-ish label tiers by width only` 那一格把这件事当场钉死：
     * 两颗替身同一串字，高度相等、宽度分得开）。10-11 那一版"一页三种字阶"里那两种
     * （`labelMedium` 灰字与 `LbSettingRow` 的 `titleMedium` 粗体）只判高度只能抓到后者。
     * 宽度这一句同时抓另一种坏法：标签被 `weight(1f)` 拉满剩余槽——原型 `.lbl{white-space:nowrap}`
     * 不占 `flex`，吃掉宽度的是 `.action-area`；被拉宽的节点宽度大于自然宽，红。
     * ⚠ 给后来人留这一句：`weight(1f)`（缺省 `fill = true`）把节点撑满剩余槽 ⇒ 红；
     * 整页六行因此统一走"标签自然宽 + 一颗 `Spacer(Modifier.weight(1f))` 吃剩下的槽"，
     * 尾部控件照样贴右缘。别为了省事把判据二放宽——那一放宽，"一页三种字阶"就再也量不出来了。
     *
     * ⚠ 第三条（不许超过组名高度的两倍）是这一族里**最松**的一句，别把它当主证：
     * `padding` 与文案由同一颗 LayoutNode 承载 ⇒ 组名那颗的 `boundsInRoot` 里含它自己那 8+8 上下内缩
     * （量到的是 ≈30dp 而不是 14dp 字高），所以这一句只挡"行标签涨到标题那一族字"的极端坏法。
     * 真正的牙是前两条（高度彼此相等 + 宽度对同文案参照）。
     */
    private fun sameFaceViolations(
        pairs: List<Pair<String, Pair<SemanticsProbe.Target, SemanticsProbe.Target>>>,
        groupTitleHeight: Float
    ): List<String> = buildList {
        if (pairs.size < rowSpecs().size) {
            add("只量到 ${pairs.size} 颗行标签，这一族该有 ${rowSpecs().size} 颗（锚点合同那六行）")
        }
        val heights = pairs.map { it.second.first.heightDp }
        if (heights.isEmpty()) return@buildList
        val spread = heights.maxOrNull()!! - heights.minOrNull()!!
        if (spread > TEXT_HEIGHT_TOLERANCE_DP) {
            add("一页好几种行标签高度（最大最小差 ${spread}dp）：" +
                pairs.joinToString("; ") { "${it.first}=${it.second.first.heightDp}dp" })
        }
        heights.forEach { h ->
            if (h > groupTitleHeight * GROUP_NAME_HEIGHT_RATIO_MAX + TEXT_HEIGHT_TOLERANCE_DP) {
                add("行标签高度 ${h}dp 超过组名高度 ${groupTitleHeight}dp 的 ${GROUP_NAME_HEIGHT_RATIO_MAX} 倍：" +
                    "原型里行的层次由组名与分隔线表达，不由行标题的字阶表达")
            }
        }
        pairs.forEach { (key, pair) ->
            val label = pair.first
            val reference = pair.second
            if (abs(label.widthDp - reference.widthDp) > TEXT_HEIGHT_TOLERANCE_DP) {
                add("「$key」那颗行标签宽 ${label.widthDp}dp，同文案 `labelLarge` 参照宽 ${reference.widthDp}dp：" +
                    "差的这一截要么是字阶，要么那颗标签吃了剩余槽（该走自然宽 + Spacer 吃权重）")
            }
        }
    }

    /**
     * 判据三：同一组里标签左缘共一条线、尾部选择器右缘共一条线（max−min ≤ [EDGE_TOLERANCE_DP]）。
     * 原型 `.item{justify-content:space-between}` + 横向内缩只有一个主人，两条线就是这么来的；
     * 谁再给自己垫一层横向 padding（需求书点名的"双重内缩"），左缘或右缘当场漂。
     */
    private fun lineViolations(
        group: String,
        labels: List<Pair<String, SemanticsProbe.Target>>,
        trailings: List<Pair<String, SemanticsProbe.Target>>
    ): List<String> = buildList {
        fun spreadOf(name: String, items: List<Pair<String, SemanticsProbe.Target>>, edge: (SemanticsProbe.Target) -> Float) {
            if (items.size < 2) return
            val values = items.map { edge(it.second) }
            val spread = values.maxOrNull()!! - values.minOrNull()!!
            if (spread > EDGE_TOLERANCE_DP) {
                add("$group 的 $name 不共一条线（最大最小差 ${spread}dp）：" +
                    items.joinToString("; ") { "${it.first}=${edge(it.second)}dp" })
            }
        }
        spreadOf("行标签左缘", labels) { it.leftDp }
        spreadOf("尾部选择器右缘", trailings) { it.rightEdge }
    }

    /**
     * 判据四：分段**轨道**把格子里三层、格子彼此相邻。
     * · 包住：轨道上下左右都要比格子那一圈再多出一截（原型 `.segments{padding:3px;background:#eef0f5}`
     *   那一圈浅底就是"这几格属于同一件事"的证据），格子不许越出轨道；
     * · 相邻：同一上沿、横排、彼此间隙 ≤ [CELL_GAP_MAX_DP]（原型 `gap:2px`）；
     * · 颗数：与档位尺一致（大小取 [BubbleSizeTier.ALLOWED_DP]、回复取 [ReplyCardLayout]）。
     *
     * 这一条判的就是10-11 那一版那张脸：三颗各自带描边的盒子、中间空一大段。
     * ⚠ 描边本身是**画**出来的，语义树里没有这一槽，所以这里用的是"轨道包住 + 格子相邻"这一族
     *   只有量才能看见的等价证据；描边那一半由整页截图基线（`SettingsPageVisualBaselineTest`）看着。
     */
    private fun trackViolations(
        trackName: String,
        track: SemanticsProbe.Target,
        cells: List<SemanticsProbe.Target>,
        wantCells: Int
    ): List<String> = buildList {
        if (cells.size != wantCells) {
            add("$trackName 里数到 ${cells.size} 颗格子，档位尺说该有 $wantCells 颗")
        }
        if (cells.isEmpty()) return@buildList
        cells.forEach { cell ->
            if (cell.leftDp < track.leftDp - EDGE_TOLERANCE_DP || cell.rightEdge > track.rightEdge + EDGE_TOLERANCE_DP) {
                add("$trackName 的轨道没在横向上包住格子 ${cell.describe()}（轨道 ${track.leftDp}…${track.rightEdge}dp）")
            }
            if (cell.topDp < track.topDp - EDGE_TOLERANCE_DP || cell.bottomEdge > track.bottomEdge + EDGE_TOLERANCE_DP) {
                add("$trackName 的轨道没在纵向上包住格子 ${cell.describe()}（轨道 ${track.topDp}…${track.bottomEdge}dp）")
            }
            // 内缩这一条只量**横向**：轨道那圈浅底在纵向上是"画"出来的（`drawBehind` 里按
            // `PILL_TRACK_VISIBLE_DP` 贴住格子那一档厚度），不是"排"出来的——格子那圈 48dp
            // 透明热区与轨道节点在纵向上同高，四边取小的话恒读到 0，那是量具看不见，不是没画。
            // 纵向那一份的牙挪到用例里那句源码尺上（轨道必须自己画那条底，不许让热区撑它）。
            val inset = minOf(
                cell.leftDp - track.leftDp,
                track.rightEdge - cell.rightEdge
            )
            if (inset < TRACK_MIN_INSET_DP) {
                add("$trackName 的轨道比格子只多 ${inset}dp：横向上没有那一圈浅底内缩（原型 `.segments{padding:3px}`），" +
                    "轨道等于没画，格子又读回几颗独立按钮")
            }
            // 纵向反过来钉：轨道节点与格子那圈热区**同高**才对——那条浅底是画出来的，不是排出来的。
            // 谁把内缩又排回纵向（`padding(vertical = 3)` 那一版），轨道就比格子高 6dp，
            // 那正是真机回归里"右边的按钮太大了"量到的形状；这一句就是它的反例证人。
            val verticalSlop = abs(track.heightDp - cell.heightDp)
            if (verticalSlop > EDGE_TOLERANCE_DP) {
                add("$trackName 的轨道在纵向上比格子高 ${verticalSlop}dp：那条浅底该贴住格子画出来，" +
                    "不该由纵向内缩把轨道撑厚（撑厚就是用户报的那一格）")
            }
        }
        val tops = cells.map { it.topDp }
        val topSpread = tops.maxOrNull()!! - tops.minOrNull()!!
        if (topSpread > EDGE_TOLERANCE_DP) {
            add("$trackName 的格子不在同一条横排上（上沿差 ${topSpread}dp）：" + cells.joinToString("; ") { it.describe() })
        }
        val byLeft = cells.sortedBy { it.leftDp }
        for (i in 1 until byLeft.size) {
            val gap = byLeft[i].leftDp - byLeft[i - 1].rightEdge
            if (gap > CELL_GAP_MAX_DP) {
                add("$trackName 里第 $i 颗格子与前一颗之间空了 ${gap}dp（原型 `gap:2px`；空一大段就是几颗独立按钮）")
            }
        }
    }

    /**
     * 判据五：滑杆是原型那一根（`input[type=range]{max-width:125px}`），`%` 读数在它**右边**
     * （`.value{min-width:27px;text-align:right}` 排在滑杆之后）。
     * 上限那句挡的就是10-11 那一版那半张脸：滑杆吃满 `weight` 分到的整条（300dp 这一档约 180dp）。
     * 余量 [SLIDER_TOUCH_ALLOWANCE_DP] 只用来不吃亚像素；触达外盒垫的是**高度**轴，不放宽宽度。
     */
    private fun sliderViolations(slider: SemanticsProbe.Target, readout: SemanticsProbe.Target): List<String> =
        buildList {
            if (slider.widthDp > SLIDER_MAX_WIDTH_DP + SLIDER_TOUCH_ALLOWANCE_DP) {
                add("透明度滑杆宽 ${slider.widthDp}dp > 原型 `max-width` 的 ${SLIDER_MAX_WIDTH_DP}dp：" +
                    "吃满整行就是10-11 那一版那根 Material 粗轨的位置")
            }
            if (readout.leftDp < slider.rightEdge - EDGE_TOLERANCE_DP) {
                add("`%` 读数 ${readout.describe()} 不在滑杆（${slider.leftDp}…${slider.rightEdge}dp）右边")
            }
        }

    /**
     * 判据六：那颗「保存」与输入框**同一行**（垂直中心差 ≤ [SAVE_CENTER_TOLERANCE_DP]）、
     * 宽度不低于"同一颗件同文案的自然宽参照"、没被撑高（换行）、右缘不出内容框、且在输入框右侧。
     *
     * ⚠ "三档都看得见完整两个字"只能这么量：语义树在被裁掉的那一截上仍报完整字符串
     * （同一条坑写在 `LongProviderNameSemanticsTest`），所以"读得出『保存』两个字"证明不了"看得见"；
     * 也不许抄一个宽度字面量当判据（字号或词表哪天一改就假红），比的是**另一颗同文案参照**。
     */
    private fun saveRowViolations(
        save: SemanticsProbe.Target,
        field: SemanticsProbe.Target,
        natural: SemanticsProbe.Target,
        contentRight: Float
    ): List<String> = buildList {
        if (abs(save.centerY - field.centerY) > SAVE_CENTER_TOLERANCE_DP) {
            add("「$saveLabel」与输入框不在同一行：中心 ${save.centerY}dp vs ${field.centerY}dp（书 需求书那一节：不许另起一行）")
        }
        if (save.leftDp < field.rightEdge - EDGE_TOLERANCE_DP) {
            add("「$saveLabel」不在输入框右侧：${save.describe()} / ${field.describe()}")
        }
        if (save.widthDp + TEXT_HEIGHT_TOLERANCE_DP < natural.widthDp) {
            add("「$saveLabel」被长正文挤窄了：被测 ${save.widthDp}dp、自然宽参照 ${natural.widthDp}dp" +
                "（该吃掉长度的是输入框，它靠光标水平移动编辑）")
        }
        if (save.heightDp > natural.heightDp + TEXT_HEIGHT_TOLERANCE_DP) {
            add("「$saveLabel」比自然宽那一颗高（${save.heightDp}dp vs ${natural.heightDp}dp）= 文案换了行")
        }
        if (save.rightEdge > contentRight + EDGE_TOLERANCE_DP) {
            add("「$saveLabel」右缘 ${save.rightEdge}dp 越过内容右缘 ${contentRight}dp = 整颗被挤出这一档窗口")
        }
    }

    /**
     * 判据八：密度。三条：
     * · 六行行高之和 ≤ [ROW_MAX_HEIGHT_DP] × 6（绝对档）；
     * · 同一组相邻两行之间只剩一条分隔线那么厚（≤ [INTER_ROW_GAP_MAX_DP]）；
     * · 整页**容器开销** ≤ 10-11 那一版"每项一张卡 + 逐项 `Spacer`"对照夹具的 [DENSITY_ALLOWED_RATIO]
     *   （那一句在 `six rows stay inside...` 用例里比，理由紧跟在 [densityViolations] 下面）。
     *
     * 为什么行高用绝对档而不是"≤ 10-11 那一版的 90%"：对照夹具只能复现**容器**，复现不了旧行里已经退役的
     * 那些读数（有效期三档、预览条、六态意图）。先按实数试算过一遍：只复现容器的对照 ≈ 436dp，
     * 而改对之后的整页 ≈ 425dp ⇒ 比值 97%，一条永远修不绿的假闸（红在谁都没改坏的那一天）。
     * 把两边各自减掉行高之和、只比"这几行外面套了几层容器"，这一比才成立 ⇒ 于是绝对档管行高、
     * 相对档管容器开销，两条各钉一头。
     *
     * 56 那一档怎么来的：原型 `.item{min-height:49px}` 是**下限**不是上限，而这一族还要吃全站那颗
     * 48dp 热区下限与分段轨道那圈内缩（原型换算表 明写"分层外盒垫 48 高"、原型 `.lbl` 那一档 轨道内缩 3dp）
     * ⇒ 轨道那一档的行是 48 + 2×3 = 54，56 = 54 再加亚像素余量。超过它就一定是多出来一层壳
     * （副标题、期限读数、说明行、卡套卡）把行撑高了。
     */
    private fun densityViolations(
        rows: List<Pair<String, SemanticsProbe.Target>>,
        gaps: List<Pair<String, Float>>
    ): List<String> = buildList {
        rows.forEach { (key, row) ->
            if (row.heightDp > ROW_MAX_HEIGHT_DP + EDGE_TOLERANCE_DP) {
                add("「$key」那一行高 ${row.heightDp}dp > 这一族的上限 ${ROW_MAX_HEIGHT_DP}dp：" +
                    "行里多半又长回一层壳（副标题／期限读数／说明行／卡套卡）")
            }
        }
        val sum = rows.fold(0f) { acc, pair -> acc + pair.second.heightDp }
        if (sum > ROW_MAX_HEIGHT_DP * rowSpecs().size + EDGE_TOLERANCE_DP) {
            add("六行行高之和 ${sum}dp > ${ROW_MAX_HEIGHT_DP}dp × ${rowSpecs().size}")
        }
        gaps.forEach { (where, gap) ->
            if (gap > INTER_ROW_GAP_MAX_DP) {
                add("同组相邻两行（$where）之间空了 ${gap}dp：那一格只该有一条 1dp 分隔线" +
                    "（原型 `.item + .item{border-top:1px}`；逐项一张卡 + 逐项 Spacer 就是这一句红）")
            }
        }
    }

    /** 容器开销 = 内容总高 − 行高之和（两边各算一次再比 [DENSITY_ALLOWED_RATIO]） */
    private fun containerOverhead(totalHeight: Float, rowHeights: List<Float>): Float =
        totalHeight - rowHeights.fold(0f) { acc, h -> acc + h }

    /**
     * 判据九：热区不退。整页除了页头那一族（F3 装饰带那一档，判据在 `SettingsPageSemanticsTest`，
     * 本格不重复挂），每一颗可交互节点的**高度轴**仍到全站下限；开关那类两轴都要到；
     * 分段轨道里的格子宽度轴走原型那一档（`.segments button{min-width:37px}`，
     * 原型换算表 明写这一族"宽度轴不垫"）。
     */
    private fun hotZoneViolations(
        actionables: List<SemanticsProbe.Target>,
        pillCells: List<SemanticsProbe.Target>
    ): List<String> = buildList {
        val headerNames = setOf(backName, collapseName)
        actionables.filter { node -> node.contentDescriptions.none { it in headerNames } }.forEach { node ->
            if (node.heightDp + TOUCH_TOLERANCE_DP < TouchTier.SITE_FLOOR) {
                add("可交互节点高度轴不到全站 ${TouchTier.SITE_FLOOR.toInt()}dp（可见尺寸可以缩，热区不许跟着缩）：" +
                    node.describe())
            }
            if (node.isToggle && node.widthDp + TOUCH_TOLERANCE_DP < TouchTier.SITE_FLOOR) {
                add("开关那颗的宽度轴不到 ${TouchTier.SITE_FLOOR.toInt()}dp：" + node.describe())
            }
        }
        pillCells.forEach { cell ->
            if (cell.widthDp + TOUCH_TOLERANCE_DP < PILL_CELL_MIN_WIDTH_DP) {
                add("分段格子宽 ${cell.widthDp}dp < 原型那 ${PILL_CELL_MIN_WIDTH_DP}dp（单字档会缩成一枚图标）：" +
                    cell.describe())
            }
        }
    }

    // ═══════════ 1. 组名在框外 ═══════════

    /**
     * 反例：把组名塞进它自己那张框里（原型没有的那张脸）。
     * 替身用的是**真的**组外框 helper，所以这一格红在"左缘只差 0dp"那一句上，不是红在手填的数上。
     */
    @Test
    fun `instrument flags a group name painted inside its own card`() {
        mountDecoys {
            Column(modifier = Modifier.settingsEntryGroupCard()) {
                Text(
                    text = DECOY_GROUP_NAME,
                    style = AppTypography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier.testTag(DECOY_IN_FRAME_NAME)
                )
                Row(modifier = Modifier.testTag(DECOY_IN_FRAME_ROW).settingsEntryRow()) {
                    Text(
                        text = DECOY_ROW_LABEL,
                        style = AppTypography.labelLarge,
                        color = TextPrimary,
                        modifier = Modifier.testTag(DECOY_IN_FRAME_LABEL)
                    )
                }
            }
        }
        val violations = outsideFrameViolations(
            title = anchorTarget(DECOY_IN_FRAME_NAME, "本格替身"),
            firstRow = anchorTarget(DECOY_IN_FRAME_ROW, "本格替身"),
            firstLabel = anchorTarget(DECOY_IN_FRAME_LABEL, "本格替身")
        )
        assertTrue(
            "量具读不出『组名被画进框里』这一种坏形状（违例清单为空 ⇒ 判据一是装饰）：$violations",
            violations.isNotEmpty()
        )
    }

    @Test
    fun `each group name sits outside the frame it heads`() {
        mountPage()
        val violations = buildList {
            groupTitles.indices.forEach { group ->
                val rows = groupOf(group)
                addAll(
                    outsideFrameViolations(
                        title = groupTitleTarget(group),
                        firstRow = rowTarget(rows.first()),
                        firstLabel = labelTargetOf(rows.first())
                    )
                )
            }
            // 读序也要对：三颗组名从上到下依次是 显示 → 内容 → 悬浮助手（原型 `.content` 那一族顺序）
            val tops = groupTitles.indices.map { groupTitleTarget(it).topDp }
            if (tops != tops.sorted()) {
                add("三颗组名的上下顺序不是 显示 → 内容 → 悬浮助手：$tops")
            }
        }
        assertTrue(
            "组名与框的关系不合原型（原型 `.lbl` 那一档：每组一张框、组名写在框外）：\n$violations",
            violations.isEmpty()
        )
    }

    // ═══════════ 2. 整页行标签同一档字 ═══════════

    /**
     * 仪器自检（它决定判据二能用什么量法）：同一串字，`labelLarge` 与 `labelMedium` 两颗替身
     * · 高度**分得开**、且大的是 `labelLarge`（实到 14.0dp 对 12.5dp：这一族两档的 lineHeight
     *   名义同数，但 Robolectric 量到的是字体实际行高，字号差一档高度就差一档）；
     * · 宽度也**分得开** ⇒ 两条轴都有牙，判据二同时用它们；
     * · 同一种字却被 `width` 拉宽的那一颗，宽度也比自然宽大 ⇒ "标签不许吃满剩余槽"这一半同样有凭据
     *   （所以六行的标签都走自然宽 + 一颗吃权重的 Spacer，见各 Entry 那句注释）。
     *
     * ⚠ 字重这一条几何量不出来（Medium 与 Normal 的差在笔画里，不在外框里）：这一条的补法是
     *   **结构判据**——整页只许有一个行标签的主人 `core/designsystem/LbRowLabel.kt`，
     *   谁想换一档字重就得改那颗公共件，改公共件会惊动首页与捕获页那两族行。
     */
    @Test
    fun `instrument separates the two twelve-ish label tiers by width only`() {
        mountDecoys {
            Column {
                Text(
                    text = DECOY_TIER_SAMPLE,
                    style = AppTypography.labelLarge,
                    color = TextPrimary,
                    modifier = Modifier.testTag(DECOY_TIER_LARGE)
                )
                Text(
                    text = DECOY_TIER_SAMPLE,
                    style = AppTypography.labelMedium,
                    color = TextPrimary,
                    modifier = Modifier.testTag(DECOY_TIER_MEDIUM)
                )
                Text(
                    text = DECOY_TIER_SAMPLE,
                    style = AppTypography.labelLarge,
                    color = TextPrimary,
                    modifier = Modifier
                        .testTag(DECOY_TIER_STRETCHED)
                        .width(DECOY_STRETCHED_WIDTH_DP.dp)
                )
            }
        }
        val large = anchorTarget(DECOY_TIER_LARGE, "本格替身")
        val medium = anchorTarget(DECOY_TIER_MEDIUM, "本格替身")
        val stretched = anchorTarget(DECOY_TIER_STRETCHED, "本格替身")

        assertTrue(
            "高度分不开 12sp 与 11sp（large=${large.heightDp}dp / medium=${medium.heightDp}dp）" +
                "⇒ 判据二的高度那一句没牙。红在这一句说明全站字阶账被改了，" +
                "要换量法就换判据二，别把这一句放松",
            large.heightDp > medium.heightDp + TEXT_HEIGHT_TOLERANCE_DP
        )
        assertTrue(
            "宽度分不开 12sp 与 11sp（large=${large.widthDp}dp / medium=${medium.widthDp}dp）⇒ 判据二没牙",
            large.widthDp > medium.widthDp + TEXT_HEIGHT_TOLERANCE_DP
        )
        assertTrue(
            "被拉宽的那颗没让宽度判据红（large=${large.widthDp}dp / stretched=${stretched.widthDp}dp）⇒ 判据二没牙",
            stretched.widthDp > large.widthDp + TEXT_HEIGHT_TOLERANCE_DP
        )
    }

    @Test
    fun `all six row labels are the same face`() {
        mountPage(extras = { labelReferences() })
        val pairs = rowSpecs().map { spec ->
            spec.key to (labelTargetOf(spec) to anchorTarget(spec.refTag, "本格参照"))
        }
        val violations = sameFaceViolations(pairs, groupTitleTarget(GROUP_DISPLAY).heightDp)
        assertTrue(
            "整页行标签不是同一档字（原型 `.lbl` 那一档：`LbRowLabel` = `labelLarge` + `TextPrimary`，" +
                "原型 `.lbl{font-size:12px;font-weight:540;color:var(--text)}` 是整页唯一一种行标签；" +
                "10-11 那一版那一页三种字阶就是这一条红）：\n$violations",
            violations.isEmpty()
        )
    }

    /** 六行标签各自的同文案 `labelLarge` 参照（判据二那句宽度比的就是它，不抄字面量宽） */
    @Composable
    private fun labelReferences() {
        Column(modifier = Modifier.requiredWidth(REFERENCE_WIDTH_DP.dp)) {
            rowSpecs().forEach { spec ->
                Text(
                    text = spec.label,
                    style = AppTypography.labelLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.testTag(spec.refTag)
                )
            }
        }
    }

    // ═══════════ 3. 两条线 ═══════════

    /**
     * 反例：组内两行，第二行**自己**又垫一层横向内缩（需求书点名的"双重内缩"那一族坏法）。
     *
     * ⚠ 这一颗替身必须把 padding 挂在**行根**上，挂在尾部那颗自己的 padding 是量不出来的：
     * 一个 composable 的整条 Modifier 链由**同一颗 LayoutNode** 承载，`padding` 只挪子里面的东西、
     * 不改那颗节点自己的外框 ⇒ 语义树上的 `boundsInRoot` 仍是整条外框，右缘看着一模一样。
     * 真会漂的是"行自己再缩一层"，那一层缩的是**所有后代**的位置，这条尺才读得到。
     */
    @Test
    fun `instrument flags a row that indents itself again`() {
        mountDecoys {
            Column(modifier = Modifier.settingsEntryGroupCard()) {
                Row(modifier = Modifier.testTag(DECOY_LINE_ROW_ONE).settingsEntryRow()) {
                    Text(DECOY_ROW_LABEL, style = AppTypography.labelLarge, modifier = Modifier.testTag(DECOY_LINE_LABEL_ONE))
                    Box(
                        modifier = Modifier
                            .size(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                            .testTag(DECOY_LINE_TRAILING_ONE)
                    )
                }
                Row(
                    modifier = Modifier
                        .testTag(DECOY_LINE_ROW_TWO)
                        .padding(start = Spacing.md, end = Spacing.lg)
                        .settingsEntryRow()
                ) {
                    Text(DECOY_ROW_LABEL, style = AppTypography.labelLarge, modifier = Modifier.testTag(DECOY_LINE_LABEL_TWO))
                    Box(
                        modifier = Modifier
                            .size(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                            .testTag(DECOY_LINE_TRAILING_TWO)
                    )
                }
            }
        }
        val violations = lineViolations(
            group = "替身那一组",
            labels = listOf(
                "第一行" to anchorTarget(DECOY_LINE_LABEL_ONE, "本格替身"),
                "第二行" to anchorTarget(DECOY_LINE_LABEL_TWO, "本格替身")
            ),
            trailings = listOf(
                "第一行" to anchorTarget(DECOY_LINE_TRAILING_ONE, "本格替身"),
                "第二行" to anchorTarget(DECOY_LINE_TRAILING_TWO, "本格替身")
            )
        )
        assertTrue(
            "量具读不出『行自己又垫了一层横向内缩』（违例清单为空 ⇒ 判据三没牙）：$violations",
            violations.isNotEmpty()
        )
    }

    @Test
    fun `labels and trailing selectors each sit on one line per group`() {
        mountPage()
        val violations = buildList {
            groupTitles.indices.forEach { group ->
                val rows = groupOf(group)
                addAll(
                    lineViolations(
                        group = "第 ${group + 1} 组（${groupTitles[group]}）",
                        labels = rows.map { it.key to labelTargetOf(it) },
                        trailings = rows.map { it.key to trailingTarget(it) }
                    )
                )
            }
        }
        assertTrue(
            "同组行没共那两条竖线（原型 `.item{justify-content:space-between}` + 横向内缩只有一个主人）：\n$violations",
            violations.isEmpty()
        )
    }

    // ═══════════ 4. 分段轨道 ═══════════

    /** 反例：10-11 那一版那张脸本体——几颗各自带描边的盒子、中间空一大段、外面没有轨道 */
    @Test
    fun `instrument flags three bordered boxes in a row instead of one track`() {
        mountDecoys {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                modifier = Modifier.testTag(DECOY_OLD_TRACK)
            ) {
                listOf("小", "标准", "大").forEach { word ->
                    LbChip(
                        label = word,
                        selected = false,
                        onClick = {},
                        interaction = LbChipInteraction.Single,
                        style = LbChipStyles.segmented
                    )
                }
            }
        }
        val trackNode = nodesWithTag(DECOY_OLD_TRACK, 1, "本格替身").single()
        val violations = trackViolations(
            trackName = "替身那一排",
            track = probe.of(trackNode),
            cells = read(chipsUnder(trackNode)).laid,
            wantCells = 3
        )
        assertTrue(
            "量具读不出『几颗各自带描边、中间空一大段』这张10-11 那一版的脸（违例清单为空 ⇒ 判据四没牙）：\n$violations",
            violations.isNotEmpty()
        )
    }

    @Test
    fun `the segmented track wraps its cells and keeps them adjacent`() {
        mountPage()
        val sizeTrackNode = nodesWithTag(PILL_SIZE_TAG, 1, OWNER_C).single()
        val replyTrackNode = nodesWithTag(PILL_REPLY_TAG, 1, OWNER_C).single()
        val violations =
            trackViolations(
                trackName = "大小轨道",
                track = probe.of(sizeTrackNode),
                cells = read(chipsUnder(sizeTrackNode)).laid,
                wantCells = BubbleSizeTier.ALLOWED_DP.size
            ) +
                trackViolations(
                    trackName = "回复轨道",
                    track = probe.of(replyTrackNode),
                    cells = read(chipsUnder(replyTrackNode)).laid,
                    wantCells = ReplyCardLayout.entries.size
                )
        assertTrue(
            "分段不是『一条浅底轨道里的几格』（原型 `.segments{padding:3px;gap:2px;background:#eef0f5}` + " +
                "`.segments button{border:none}`；原型 `.lbl` 那一档：那颗 `SettingsSegmentedPill` + `segmentedPill` 档）：\n" +
                "$violations",
            violations.isEmpty()
        )
    }

    // ═══════════ 5. 滑杆 ═══════════

    /** 反例：吃满整行的那一根 + 读数跑到左边（原型没有的两件形状） */
    @Test
    fun `instrument flags a slider that eats the whole row`() {
        mountDecoys {
            Row(modifier = Modifier.testTag(DECOY_WIDE_ROW)) {
                Text(
                    text = percentReadout(OPACITY_STORED_PERCENT),
                    style = AppTypography.labelMedium,
                    modifier = Modifier.testTag(DECOY_WIDE_READOUT)
                )
                Box(
                    modifier = Modifier
                        .width(DECOY_WIDE_SLIDER_WIDTH_DP.dp)
                        .height(AppDimens.TOUCH_TARGET_MIN_DP.dp)
                        .testTag(DECOY_WIDE_SLIDER)
                )
            }
        }
        val violations = sliderViolations(
            slider = anchorTarget(DECOY_WIDE_SLIDER, "本格替身"),
            readout = anchorTarget(DECOY_WIDE_READOUT, "本格替身")
        )
        assertTrue(
            "量具读不出『滑杆吃满整行 + 读数跑到左边』（违例清单为空 ⇒ 判据五没牙）：\n$violations",
            violations.isNotEmpty()
        )
    }

    @Test
    fun `the slider keeps the prototype length and the percent readout sits at its right`() {
        mountPage()
        val spec = specByKey("透明度")
        val slider = anchorTarget(SLIDER_TAG, OWNER_B)
        val row = rowTarget(spec)
        // 读数 = 那一行里除行标签之外的那颗文本节点（恰一颗：屏上不许有第二份百分比）
        val readouts = read(textNodesUnder(rowNode(spec)).filter { it.textValue() != opacityLabel })
        assertEquals("透明度那一行的 `%` 读数应当恰一颗" + readouts.droppedNote, 1, readouts.laid.size)
        val readout = readouts.laid.single()
        assertEquals(
            "读数写的是本次那份透明度（换算口现算；抄常量或留旧值当场红）",
            percentReadout(OPACITY_STORED_PERCENT),
            readout.label
        )
        val rowFloorViolations = if (row.heightDp + TOUCH_TOLERANCE_DP < SettingsRowDimens.ROW_MIN_HEIGHT_DP) {
            listOf("透明度那一行短于族里的行版式下限 ${SettingsRowDimens.ROW_MIN_HEIGHT_DP}dp：" + row.describe())
        } else {
            emptyList()
        }
        val violations = sliderViolations(slider, readout) + rowFloorViolations
        assertTrue(
            "透明度那一格不合原型 `input[type=range]{max-width:125px}` + `.value{min-width:27px;text-align:right}`：\n" +
                "$violations",
            violations.isEmpty()
        )
    }

    // ═══════════ 6. 保存与输入同一行、三档都看得见 ═══════════

    /**
     * 三档窗口宽度（原型 `.resizer` 那三颗：260 / 300 / 350）各一格——
     * 一格里只能 `setContent` 一次（第二次挂载会被框架当场拒掉，那一段断言从不执行），所以是三格
     * （同一条纪律写在 `SettingsIntentRowTest` 与本文件 `the edit row...` 那一格）。
     */
    @Test
    fun `the save shares its row with the field and keeps both characters at two sixty`() {
        assertSaveVisibleAt(260)
    }

    @Test
    fun `the save shares its row with the field and keeps both characters at three hundred`() {
        assertSaveVisibleAt(PROTOTYPE_WIDTH_DP)
    }

    @Test
    fun `the save shares its row with the field and keeps both characters at three fifty`() {
        assertSaveVisibleAt(350)
    }

    private fun assertSaveVisibleAt(widthDp: Int) {
        mountPage(widthDp = widthDp, host = PageHost(intentEnabledAtStart = true), extras = { saveReference() })
        val save = anchorTarget(SAVE_TAG, OWNER_D)
        val fieldNodes = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals("开着意图的那一档整页应当恰有一颗输入节点，实到 ${fieldNodes.size}", 1, fieldNodes.size)
        val field = probe.of(fieldNodes.single())
        val natural = anchorTarget(REFERENCE_SAVE_TAG, "本格参照")
        val contentRight = rowTarget(specByKey("持续意图")).rightEdge

        // 仪器证人两条（同 `SettingsIntentRowTest` 的口径）：参照必须真摆得出宽度，而且与被测**分处两桶**——
        // 否则三条比较全变成 0 对 0 / 自己对自己，整格恒绿。
        assertTrue("自然宽参照那颗没摆出来（${natural.widthDp}dp）⇒ 这一格没有可比的那个数", natural.widthDp > 0f)
        assertTrue(
            "参照那颗没挂在页桶之外（参照上沿 ${natural.topDp}dp / 页桶下沿 ${probe.of(pageBucket()).bottomEdge}dp）" +
                "⇒ 两桶读数分不开，比的会是同一颗",
            natural.topDp >= probe.of(pageBucket()).bottomEdge - EDGE_TOLERANCE_DP
        )
        val violations = saveRowViolations(save, field, natural, contentRight)
        assertTrue(
            "「$saveLabel」在 ${widthDp}dp 这一档不合原型 `.intent-edit{display:flex;align-items:center;gap:5px}` + " +
                "`.save{height:37px}`（原型换算表 `actionSolid`；需求书『同一行、不另起一行，260/300/350 都要看得见』）：\n" +
                "$violations",
            violations.isEmpty()
        )
    }

    /** 同文案、同档形的那颗自然宽参照：判据六不许抄字面量宽度 */
    @Composable
    private fun saveReference() {
        Box(modifier = Modifier.requiredWidth(REFERENCE_WIDTH_DP.dp)) {
            LbChip(
                label = saveLabel,
                selected = false,
                onClick = {},
                interaction = LbChipInteraction.Action,
                style = LbChipStyles.actionSolid,
                modifier = Modifier.testTag(REFERENCE_SAVE_TAG)
            )
        }
    }

    // ═══════════ 7. 意图两态 ═══════════

    /**
     * 关着：输入行**整段不在树上**；开着：在。两头都在**同一次挂载**里翻那一颗状态。
     *
     * 为什么必须两头：只判"关着不许在"的话，"这一栏根本没接线"也是绿的；只判"开着要在"的话，
     * "关着但留一片禁用的表单"也是绿的（书 需求书那一节明令禁止的那一种）。
     */
    @Test
    fun `the edit row is on the tree only while the intent is on`() {
        val host = PageHost(intentEnabledAtStart = false)
        mountPage(host = host)

        assertEquals("关着的时候屏上不许有输入节点", 0, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals(
            "关着的时候 `$SAVE_TAG` 那颗不渲染（隐藏 = 不组合，不是画一颗禁用的）",
            0,
            rule.onAllNodes(hasTestTag(SAVE_TAG)).fetchSemanticsNodes().size
        )

        rule.runOnIdle { host.intentEnabled.value = true }
        rule.waitForIdle()

        assertEquals("开着的时候输入节点恰一颗", 1, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals("开着的时候 `$SAVE_TAG` 恰一颗", 1, rule.onAllNodes(hasTestTag(SAVE_TAG)).fetchSemanticsNodes().size)
        assertEquals(
            "开着的时候回填的是盘上那条正文（EditableText 是 AnnotatedString，要 .text）",
            LONG_DRAFT,
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().single()
                .config.getOrNull(SemanticsProperties.EditableText)?.text
        )
    }

    // ═══════════ 8. 密度 ═══════════

    /** 反例：回到"每项一张卡 + 逐项 Spacer"（需求书点名要拆的那一版容器） */
    @Test
    fun `instrument flags per-item cards stacked again`() {
        mountDecoys {
            Column(modifier = Modifier.testTag(DECOY_STACK_COLUMN)) {
                repeat(2) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsEntryCard()
                            .testTag(DECOY_STACK_CARD_PREFIX + index)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                                .testTag(DECOY_STACK_ROW_PREFIX + index)
                        )
                    }
                    if (index != 1) Spacer(modifier = Modifier.height(Spacing.md))
                }
            }
        }
        val first = anchorTarget(DECOY_STACK_ROW_PREFIX + "0", "本格替身")
        val second = anchorTarget(DECOY_STACK_ROW_PREFIX + "1", "本格替身")
        val violations = densityViolations(
            rows = listOf("替身一" to first, "替身二" to second),
            gaps = listOf("替身一→替身二" to (second.topDp - first.bottomEdge))
        )
        assertTrue(
            "量具读不出『每项一张卡 + 逐项 Spacer』那种堆叠（违例清单为空 ⇒ 判据八没牙）：\n$violations",
            violations.isNotEmpty()
        )
    }

    @Test
    fun `six rows stay inside the prototype density and beat the old container overhead`() {
        mountPage(extras = { oldContainerControl() })
        val rows = rowSpecs().map { it.key to rowTarget(it) }
        val gaps = buildList {
            groupTitles.indices.forEach { group ->
                val inGroup = groupOf(group)
                for (i in 1 until inGroup.size) {
                    val previous = rowTarget(inGroup[i - 1])
                    val next = rowTarget(inGroup[i])
                    add("${inGroup[i - 1].key}→${inGroup[i].key}" to (next.topDp - previous.bottomEdge))
                }
            }
        }
        val violations = densityViolations(rows, gaps)
        assertTrue(
            "整页密度退回10-11 那一版（原型 `.item{min-height:49px}` + `.item + .item{border-top:1px}`：一行只排行，" +
                "组内只剩一条分隔线）：\n$violations",
            violations.isEmpty()
        )

        // 容器开销那一比（为什么只比开销：见 [densityViolations] 上面那段）
        val pageBottom = probe.of(pageBucket()).bottomEdge
        val contentTop = groupTitleTarget(GROUP_DISPLAY).topDp
        val contentBottom = rowTarget(groupOf(GROUP_ASSISTANT).last()).bottomEdge
        val overhead = containerOverhead(contentBottom - contentTop, rows.map { it.second.heightDp })
        val controlRows = (0 until OLD_CONTROL_ITEMS).map { anchorTarget(DECOY_OLD_ROW_PREFIX + it, "本格夹具") }
        val controlTotal = anchorTarget(DECOY_OLD_COLUMN, "本格夹具")
        val controlOverhead = containerOverhead(controlTotal.heightDp, controlRows.map { it.heightDp })

        assertTrue(
            "替身那一列没挂在页桶之外（${controlTotal.topDp}dp / 页桶下沿 ${pageBottom}dp）⇒ 两桶读数分不开",
            controlTotal.topDp >= pageBottom - EDGE_TOLERANCE_DP
        )
        assertTrue(
            "对照夹具自己都没量出容器开销（${controlOverhead}dp，替身 ${controlTotal.heightDp}dp / 行 ${controlRows.size} 颗）" +
                "⇒ 这一比是空的，不许读成绿",
            controlOverhead > 0f
        )
        assertTrue(
            "整页容器开销 ${overhead}dp 超过10-11 那一版那张『每项一张卡 + 逐项 Spacer』对照（${controlOverhead}dp）的 " +
                "${(DENSITY_ALLOWED_RATIO * 100).toInt()}%：结构对了、密度没赢回来，就是需求书交付定义那句『不算完成』",
            overhead <= controlOverhead * DENSITY_ALLOWED_RATIO + EDGE_TOLERANCE_DP
        )
    }

    /**
     * "10-11 那一版"的容器对照夹具：HEAD 那一版 [LoveBrainSettingsContent] 的滚动柱就是六个 Entry 各调一次
     * `settingsEntryCard()`（那一颗当时是横 12 竖 8）＋ 每两项之间 `Spacer(Spacing.md)`，
     * 而且**没有组名行**（`git show HEAD` 实读，2026-10-12）。
     * 这里复现的是**容器**、不是旧行的内容——所以判据八只比减掉行高之后那一截。
     */
    @Composable
    private fun oldContainerControl() {
        Column(modifier = Modifier.width(PROTOTYPE_WIDTH_DP.dp).testTag(DECOY_OLD_COLUMN)) {
            repeat(OLD_CONTROL_ITEMS) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsEntryCard()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                            .testTag(DECOY_OLD_ROW_PREFIX + index)
                    )
                }
                if (index != OLD_CONTROL_ITEMS - 1) Spacer(modifier = Modifier.height(Spacing.md))
            }
        }
    }

    // ═══════════ 9. 热区不退 ═══════════

    /**
     * 反例：只有可见 28dp、没有热区层的那一格。原型换算表 明写 `segmentedPill` / `actionSolid`
     * "分层外盒垫 48 高"——撤掉那一层，眼睛看不出，手指看得出，这一句当场红。
     */
    @Test
    fun `instrument flags a control that dropped its touch layer`() {
        mountDecoys {
            Column {
                Box(
                    modifier = Modifier
                        .size(AppDimens.CHIP_PANEL_HEIGHT_DP.dp)
                        .clickable(onClick = {})
                        .testTag(DECOY_NO_TOUCH)
                )
            }
        }
        val violations = hotZoneViolations(listOf(anchorTarget(DECOY_NO_TOUCH, "本格替身")), emptyList())
        assertTrue(
            "量具读不出『可点那颗自己没垫到下限』（违例清单为空 ⇒ 判据九没牙）。" +
                "只放大外层容器而点击仍挂在子节点上等于没改：$violations",
            violations.isNotEmpty()
        )
    }

    @Test
    fun `no actionable node on the page gives up its hot zone`() {
        mountPage()
        val pillCells = read(
            nodesWithTag(PILL_SIZE_TAG, 1, OWNER_C).flatMap { chipsUnder(it) } +
                nodesWithTag(PILL_REPLY_TAG, 1, OWNER_C).flatMap { chipsUnder(it) }
        ).laid
        val actionableViolations = hotZoneViolations(pageActionables(), emptyList())
        val cellWidthViolations = hotZoneViolations(emptyList(), pillCells)
        assertTrue(
            "整页热区退档（全站那颗 48 下限本身一个字没动，退的是新形状上的分层外盒）：\n" +
                (actionableViolations + cellWidthViolations),
            (actionableViolations + cellWidthViolations).isEmpty()
        )
    }

    private companion object {
        // ── 行级锚点（锚点合同那张表；值按合同写死，不跟别席的常量名走，别席改名会红在这一句而不是编不过）──
        const val ROW_OPACITY_TAG = "settings-row-opacity"
        const val ROW_SIZE_TAG = "settings-row-size"
        const val ROW_REPLY_TAG = "settings-row-reply"
        const val ROW_KB_TAG = "settings-row-kb"
        const val ROW_INTENT_TAG = "settings-row-intent"
        const val ROW_ASSISTANT_TAG = "settings-row-assistant"
        const val PILL_SIZE_TAG = "settings-pill-size"
        const val PILL_REPLY_TAG = "settings-pill-reply"
        const val SAVE_TAG = "settings-save-button"
        const val SLIDER_TAG = "settings-opacity-slider"

        const val OWNER_B = "席 B（滑杆）"
        const val OWNER_C = "席 C（分段两行，这一版待落）"
        const val OWNER_D = "席 D（意图那一格）"
        const val OWNER_E = "席 E（知识库＋助手，这一版待落）"

        // ── 夹具自己的桶、参照与替身 ──
        const val PAGE_BUCKET_TAG = "fidelity-page"
        const val EXTRAS_BUCKET_TAG = "fidelity-extras"
        const val REFERENCE_LABEL_PREFIX = "fidelity-ref-label-"
        const val REFERENCE_SAVE_TAG = "fidelity-ref-save"
        const val REFERENCE_WIDTH_DP = 2400

        const val DECOY_IN_FRAME_NAME = "fidelity-decoy-in-frame-name"
        const val DECOY_IN_FRAME_ROW = "fidelity-decoy-in-frame-row"
        const val DECOY_IN_FRAME_LABEL = "fidelity-decoy-in-frame-label"
        const val DECOY_TIER_LARGE = "fidelity-decoy-tier-large"
        const val DECOY_TIER_MEDIUM = "fidelity-decoy-tier-medium"
        const val DECOY_TIER_STRETCHED = "fidelity-decoy-tier-stretched"
        const val DECOY_TIER_SAMPLE = "FIDELITY-TIER-SAMPLE"
        const val DECOY_STRETCHED_WIDTH_DP = 220
        const val DECOY_LINE_ROW_ONE = "fidelity-decoy-line-row-one"
        const val DECOY_LINE_ROW_TWO = "fidelity-decoy-line-row-two"
        const val DECOY_LINE_LABEL_ONE = "fidelity-decoy-line-label-one"
        const val DECOY_LINE_LABEL_TWO = "fidelity-decoy-line-label-two"
        const val DECOY_LINE_TRAILING_ONE = "fidelity-decoy-line-trailing-one"
        const val DECOY_LINE_TRAILING_TWO = "fidelity-decoy-line-trailing-two"
        const val DECOY_OLD_TRACK = "fidelity-decoy-old-track"
        const val DECOY_WIDE_ROW = "fidelity-decoy-wide-row"
        const val DECOY_WIDE_SLIDER = "fidelity-decoy-wide-slider"
        const val DECOY_WIDE_SLIDER_WIDTH_DP = 180
        const val DECOY_WIDE_READOUT = "fidelity-decoy-wide-readout"
        const val DECOY_STACK_COLUMN = "fidelity-decoy-stack-column"
        const val DECOY_STACK_CARD_PREFIX = "fidelity-decoy-stack-card-"
        const val DECOY_STACK_ROW_PREFIX = "fidelity-decoy-stack-row-"
        const val DECOY_OLD_COLUMN = "fidelity-decoy-old-container-column"
        const val DECOY_OLD_ROW_PREFIX = "fidelity-decoy-old-container-row-"
        const val DECOY_NO_TOUCH = "fidelity-decoy-no-touch-layer"

        /** 替身一律用 ASCII 词：语义树按整串文字认节点，中文串会被 locale/断词干扰（本仓既有教训） */
        const val DECOY_GROUP_NAME = "FIDELITY-GROUP"
        const val DECOY_ROW_LABEL = "FIDELITY-ROW-LABEL"

        // ── 窗口与夹具读数 ──
        const val WINDOW_HEIGHT_DP = 1000
        /** 原型 `.device{width:300px}` 那一档（`.resizer` 另两档只用在判据六那三格） */
        const val PROTOTYPE_WIDTH_DP = 300
        const val OPACITY_STORED_PERCENT = 80
        const val ACTIVE_KB_NAME = "小明"
        const val SECOND_KB_NAME = "工作记录"
        const val LONG_DRAFT = "先约她周末看电影，然后把话题从工作里带出来，别一直聊加班和同事那些事，语气要轻松一点"
        const val OLD_CONTROL_ITEMS = 6

        // ── 数值一律指回原型那一族 CSS（不读被测那一侧的生产常量：那等于让产品自己出题）──
        /** 边缘比对的亚像素余量（与 `SettingsPageStructureTest` 的 `EDGE_TOLERANCE_DP` 同档） */
        const val EDGE_TOLERANCE_DP = 0.6f

        /** 文本节点高度/宽度比对余量（锚点合同那把 ±0.5dp） */
        const val TEXT_HEIGHT_TOLERANCE_DP = 0.5f

        /** 热区比对余量 */
        const val TOUCH_TOLERANCE_DP = 0.6f

        /** 组名左缘要比框内内容左缘更靠左至少这一档（只认"在内缩之外"，不抄 12dp 那个数） */
        const val NAME_OUTSIDE_INSET_MIN_DP = 1f

        /** 行标签高度不许超过组名高度的这一倍（原型 10.5px vs 12px 那层主次） */
        const val GROUP_NAME_HEIGHT_RATIO_MAX = 2f

        /** `input[type=range]{max-width:125px}` */
        const val SLIDER_MAX_WIDTH_DP = 125f

        /** 触达外盒那一档的余量：tag 挂在带语义的那颗身上，宽度不许借这一档放宽 */
        const val SLIDER_TOUCH_ALLOWANCE_DP = 2f

        /** `.segments{gap:2px}` 加一档余量 */
        const val CELL_GAP_MAX_DP = 3f

        /** `.segments{padding:3px}`：轨道至少要 visibly 比格子多出一截才算"包住" */
        const val TRACK_MIN_INSET_DP = 1f

        /** `.segments button{min-width:37px}`（紧凑档 49 是同一颗件的另一档，不放宽这里） */
        const val PILL_CELL_MIN_WIDTH_DP = 37f

        /** `.intent-edit{align-items:center}`：同一行的垂直中心差上限（锚点合同写的 ≤1dp） */
        const val SAVE_CENTER_TOLERANCE_DP = 1f

        /** 行高上限：`.item{min-height:49}` + 热区/轨道那一档（为什么用绝对档见 [densityViolations]） */
        const val ROW_MAX_HEIGHT_DP = 56f

        /** `.item + .item{border-top:1px}`：同组相邻两行之间只该剩这一条线的厚度 */
        const val INTER_ROW_GAP_MAX_DP = 2f

        /** 整页容器开销不许超过"每项一张卡"对照的这一比例 */
        const val DENSITY_ALLOWED_RATIO = 0.9f

        const val GROUP_DISPLAY = 0
        const val GROUP_CONTENT = 1
        const val GROUP_ASSISTANT = 2
    }
}
