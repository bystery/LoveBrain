package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.panel.INTENT_MAX_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * 设置页那一格**持续意图**的行级合同（脸按原型 `.intent-edit` 那一格钉，语义按两态合同钉）。
 *
 * 这一族要钉住的八件事，每件都对应一种"看不出来、坏了也没人喊"的坏法：
 * 1. **关 = 输入与保存整段不在树上**，不是"画出来但禁用"；关着也不许把旧文本写空、更不许注入。
 *    同时钉住"屏上不出现期限与终态那一族读数"（旧六档的形状）。
 * 2. **开 = 开关一行 + 紧跟其下的输入一行**，两行各自取族里的行版式（`settingsEntryRow()`）。
 *    开关那一行是**行根**，锚点 `settings-row-intent` 挂在它自己身上（锚点合同：量具按这一颗找行）；
 *    输入那一行与它之间**不画分隔线**（原型 `<div id="intent-edit">` 就是 `.item` 之后的兄弟段），
 *    两行仍都在这一组的内容框之内。
 * 3. **输入与保存在同一行**：共用一条中轴、垂直居中；那颗按钮贴这一组内容的右缘（外面没再套空盒），
 *   它与输入框之间只有**一份** `Spacing.sm`（外加输入框自己的内衬，不含第二层空盒）。
 * 4. **正文再长也不许把保存挤走**：260 / 300 / 350dp 三档各一格，每格都另挂一份**同一棵行**的自然宽
 *    参照（`requiredWidth(2400dp)`），比的是"那颗芯片排开'保存'两个字到底要多宽"——被测那一档量出来
 *    比参照窄就是被挤了；同时被测那一档的输入框必须**比参照那一档窄**（吃掉长度的是输入框，
 *    不是按钮）。⚠ 这一族的宽度判据只能这样来：语义树在被裁掉的那一截上仍报完整字符串，
 *    所以"念得出'保存'"证明不了"看得见'保存'"（同一坑与同一解法的前例：
 *    `ui/home/LongProviderNameSemanticsTest`）。宽度这类判据靠**给被测子树固定宽度约束**
 *    （[RenderIn] 那一颗盒子），量的是"这棵子树被要求在 260dp 内摆好"，不是"这台设备是 260dp"
 *    （[UiMatrix] 文件头写着这条边界）。
 * 5. **只有两处提交**：拨开关、按保存。打字与失焦都不写盘（旧实现那一路 `.onFocusChanged`
 *    会在失焦时再交一次，与那颗保存按钮并存就是同一稿写两遍）。
 * 6. **一次写盘只交一笔**：那一笔还在飞的时候保存按钮按不动（仍在树上、仍念得出"保存"），
 *    宿主那份读数跟上就恢复；写失败（读数永远跟不上）到最长那一窗也恢复，
 *    于是"请重试"那一句永远配得上一个真按得动的按钮。草稿在失败那一路原样留着。
 *    另一颗门是**切库保护**：这一稿的主人与屏幕上的库不是同一个时不交出去（不写给错的人），
 *    也不留"看着是 A、写进 B"的中间态。
 * 7. **长度那一把尺与浮层共用同一颗数**（F05A，2026-10-15 书 §6）：正好在上限那一稿正常交出去、
 *    一个字都不少；过界那一稿**看得见原因**（多挂一行极短的话）、**灰着还在**（不消失）、
 *    **一个字都不交**（保存与开关同一条闸，两处提交同口径）。这一格也不许再抄第二份字面数。
 * 8. **「已保存」那一档属于这一次点击**（F05B）：成功票根只认**相对本入口上屏基线的增量**；
 *    别处（浮层那一扇）保存过的历史值再进这一页，首帧不许白亮 1.3 秒。
 *
 * 屏上**不许有**输入下方那一行灰字说明（`intent_row_hint` 那一句）：原型 `.intent-edit` 里只有输入与
 * 保存两颗，那句话已经由输入框的 placeholder 承担。这条用两处顶：文案集合钉成"此刻该有谁"（多一行
 * 字就红），加源码尺点名 `intent_row_hint` 不许再出现（资源键本身归这一族的主人清，测试不押 `R.string.*`，
 * 否则哪天孤儿清理删掉那颗键会让本文件在谁都没改界面的一天编译不过）。
 *
 * 时钟口径：第 6 件与第 8 件那几格**先**关掉 `mainClock.autoAdvance`，飞行中／刚亮起的读数才抓得住
 * （同一口径的先例：
 * `ui/panel/PanelErrorStatesSemanticsTest` 与 `ui/panel/stats/UsageStatBarSemanticsTest` 都在挂载之前
 * 把时钟冻住，因为默认档有机会在 `waitForIdle` 里把那一窗的虚拟时间一路喂完）。
 * ⚠ 反过来，"到点自动放行"那一段**不能**靠"把自动推进打开再 `waitForIdle`"收尾：本机实测那样回来
 * 那颗按钮仍报 disabled（自动推进要等有东西等帧才走表，而这里等的是一颗纯 `delay`，它不等帧）。
 * 这两句合起来的口径是：这一族不猜自动推进什么时候走表，需要它别走就冻住，需要它走就自己推。
 * 手动推的姿势同两条先例：`ui/KbEditScreenStatesTest` 喂成功提示那两秒、
 * `ui/panel/stats/UsageStatBarSemanticsTest` 喂轮播那一整段（推一段、再 `waitForIdle`）。
 * 中段逐帧推、落定段交给 waitForIdle 的那一族先例见 `core/designsystem/LbPrimaryButtonStateTest`、
 * `ui/panel/ModalSheetExitAnimationTest`。
 *
 * 一个用例只 `setContent` 一次（框架会当场拒掉第二次，那一段断言从不执行），所以三档宽度
 * 是三格，不是一格里的循环。文案锚点一律现取资源（这台机器的环境可能解析成英文）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsIntentRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private val rowLabel: String get() = ctx.getString(R.string.intent_label)
    private val placeholder: String get() = ctx.getString(R.string.intent_row_placeholder)
    private val saveLabel: String get() = ctx.getString(R.string.intent_save)

    /** 成功那一档的文案：与宿主那句回执共用同一条资源（这一格不写第二份话） */
    private val savedNotice: String get() = ctx.getString(R.string.intent_saved_notice)

    /**
     * 超限那一句：与浮层共用同一条资源、同一个数（[INTENT_MAX_LENGTH]）。
     * 这里的格式化参数**取那颗尺本身**而不是写死 200——写死就把"只有一把尺"这条判据
     * 自己先违了（哪天尺动了，这一格会红成"屏上念不到那句"，而那其实是量具抄了第二份数）。
     */
    private val tooLongMsg: String get() = ctx.getString(R.string.intent_too_long_error, INTENT_MAX_LENGTH)

    /**
     * 宿主那几份读数：开关 + 正文 + **写侧那两个真读数**（还在飞吗、成功票根跳了几号）。
     * 这一格不持有配置，测试替宿主拿着它们；后两颗模拟的是写侧那一颗控制器自己的翻动
     * （`IntentController.save` 起协程前把 `isSaving` 置真、`finally` 里置假；
     * 只有正文真的落盘成功那一次才把票根 +1）。
     */
    private class Host(
        enabledAtStart: Boolean = false,
        textAtStart: String = ""
    ) {
        val enabled: MutableState<Boolean> = mutableStateOf(enabledAtStart)
        val text: MutableState<String> = mutableStateOf(textAtStart)
        val saving: MutableState<Boolean> = mutableStateOf(false)
        val receipts: MutableState<Int> = mutableStateOf(0)
    }

    /**
     * 写口收到的每一笔：只有正文与启用两件。
     *
     * 旧形状这里是四件（正文／启用／有效期／是否点名重算期限）。期限与状态那一族读数随两态
     * 合同退役、写口收成两件之后，"传进去的期限对不对""有没有点名重算"这两问已经不存在，
     * 由"只交一笔、正文原样、enabled 就是拨的那一下"这一句等价顶住：件数少了，但每一笔仍然
     * 逐字比对（下面各格用的是 `assertEquals(那一句, 期望列表, 实到列表)`，不是"存在即可"）。
     */
    private data class Write(
        val text: String,
        val enabled: Boolean
    )

    /**
     * 挂载：被测那一桶按组外框的横向内缩（[Spacing.lg]）摆进矩阵宽度里，
     * [withControl] 那一遍再挂一份**同一棵行**、套进 `requiredWidth(2400dp)` 当自然宽参照。
     * 两遍在同一个 [RenderIn] 里 ⇒ 同一把字号，否则量的不是同一种字。
     */
    private fun mountRow(
        host: Host,
        writes: MutableList<Write>,
        widthDp: Int = PROBE_WIDTH_DP,
        ownerKb: String? = "kbA",
        onInputIntent: (() -> Unit)? = null,
        withControl: Boolean = false
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(widthDp, heightDp = 600).RenderIn(deviceDensity) {
                Column {
                    Box(modifier = Modifier.padding(horizontal = Spacing.lg).testTag(PROBED_TAG)) {
                        SettingsIntentEntry(
                            intentEnabled = host.enabled.value,
                            intentText = host.text.value,
                            saving = host.saving.value,
                            saveReceipts = host.receipts.value,
                            onIntentChange = { text, enabled ->
                                writes += Write(text, enabled)
                            },
                            ownerKbName = ownerKb,
                            onInputIntent = onInputIntent
                        )
                    }
                    if (withControl) {
                        Box(
                            modifier = Modifier.testTag(CONTROL_TAG).requiredWidth(CONTROL_WIDTH)
                        ) {
                            SettingsIntentEntry(
                                intentEnabled = host.enabled.value,
                                intentText = host.text.value,
                                // 参照那一遍只借它的几何，不接任何写口：真有回调就会让
                                // "按一次只交一笔"那一句数到两笔，那是夹具造的账，不是产品的账。
                                onIntentChange = { _, _ -> },
                                ownerKbName = ownerKb
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    // ═══════════ 读数工具：全部限定在被测那一桶里 ═══════════

    /** 桶的边框（只有 testTag 的节点不进合并树 ⇒ 读未合并那一侧，本仓既有先例） */
    private fun bucket(tag: String): SemanticsProbe.Target {
        val nodes = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("$tag 这个桶必须恰有一颗，实到 ${nodes.size}", 1, nodes.size)
        return probe.of(nodes.single())
    }

    /** 被测那一桶里的可交互节点（参照那一桶靠 y 轴分开，不混进来） */
    private fun probedActionables(host: Host): List<SemanticsProbe.Target> {
        val frame = bucket(PROBED_TAG)
        val bottom = frame.topDp + frame.heightDp
        val all = probe.laid(probe.actionableTargets(rule, "持续意图那一格"))
        val inside = all.filter { it.centerY in frame.topDp..bottom }
        if (inside.isEmpty()) {
            error("${host.enabled.value} 那一态下被测桶里一个可交互节点都没量到：${all.joinToString { it.describe() }}")
        }
        return inside
    }

    private fun saveChip(host: Host): SemanticsProbe.Target =
        probedActionables(host).singleOrNull { it.label == saveLabel }
            ?: error("「$saveLabel」那颗按钮在该态下应当恰有一颗：" + probedActionables(host).joinToString { it.describe() })

    /** 可编辑那颗节点（`LbFieldInput` 里的文本节点）；这一格不判角色，只判它在不在、写着什么 */
    private fun editableNode(): SemanticsProbe.Target {
        val nodes = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals("这一格没挂参照，输入节点该恰一颗，实到 ${nodes.size}", 1, nodes.size)
        return probe.of(nodes.single())
    }

    private fun editableCount(): Int = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size

    private fun editableText(): String =
        rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().single()
            .config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

    private fun nodeCount(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /** 「保存」那颗的锚点读数（锚点合同：量具按这一颗找按钮，不靠颜色也不只靠文案） */
    private fun saveTagCount(): Int =
        rule.onAllNodes(hasTestTag(SETTINGS_INTENT_SAVE_TAG)).fetchSemanticsNodes().size

    /**
     * 树上所有**带文案**的节点（合并树，逐颗读出那串字）。
     *
     * 这一格"没有多出一行说明／旧读数"这件事就靠这一份读数判：不点名去比旧资源键（那些键
     * 随语义退役、随时可能被孤儿资源清理删掉，押着它们的 `R.string.*` 会让本文件在谁都没
     * 改界面的一天编译不过），而是把整颗行的文案集合钉死成**此刻该有谁**。
     * 多一行期限、多一颗「已完成」、多一句介绍文案、多回那一行灰字说明，都会在这里让集合变大当场红。
     *
     * ⚠ 可编辑那颗要滤掉：文本框在语义树里报的是 `EditableText`，正文那件事由 [editableText]
     *   那一句判；把它算进来等于让"草稿写了什么"混进"这一格画了几行字"这两件不同的事。
     */
    private fun textNodes(): List<SemanticsProbe.Target> =
        probe.laid(
            rule.onAllNodes(SemanticsMatcher("has Text") { it.config.contains(SemanticsProperties.Text) })
                .fetchSemanticsNodes().map { probe.of(it) }
        ).filterNot { it.editable }

    private val SemanticsProbe.Target.centerY: Float get() = topDp + heightDp / 2f
    private val SemanticsProbe.Target.rightEdge: Float get() = leftDp + widthDp
    private val SemanticsProbe.Target.bottomEdge: Float get() = topDp + heightDp

    /** 生产那棵件的源码（剥注释后）：源码尺那一族的对象 */
    private fun entrySource(): String {
        val rel = "ui/panel/settings/SettingsIntentEntry.kt"
        val file = File("src/main/java/com/lovebrain/app/$rel").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/$rel")
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        // 反向证人：这把尺看得见这个文件本身（空串=扫了个寂寞，整格会恒绿）
        assertTrue("$rel 读出来是空的，这把尺已经看不见东西", code.isNotBlank())
        return code
    }

    // ═══════════ 1. 关 = 输入与保存整段不在树上 ═══════════

    /**
     * 关掉的那一态：只剩「持续意图 + 开关」那一行，输入与保存**整段不渲染**，
     * 盘上那条文本原样留着（这一格一个字都不写），旧六档的读数一颗都不许在屏上。
     *
     * 替代关系：旧格 `an expired intent keeps its body instead of collapsing to an empty row`
     * 判的是"已到期那一格仍要有正文区与期限读数"——新合同里期限与终态整族退役，
     * 于是"开着的正文区在场"变成"关着的时候整段不在场"，而那颗"已到期"读数改为**必须读不出**。
     */
    @Test
    fun `off leaves only the switch row and writes nothing`() {
        val host = Host(enabledAtStart = false, textAtStart = "先约她看电影")
        val writes = mutableListOf<Write>()
        mountRow(host = host, writes = writes)

        assertEquals("关着的时候输入节点一颗都不许在树上", 0, editableCount())
        assertEquals("关着的时候「$saveLabel」整段不渲染", 0, nodeCount(saveLabel))
        assertEquals("关着的时候那颗按钮的锚点也不在树上", 0, saveTagCount())
        assertTrue("开关那一颗自己仍在", rule.onAllNodes(hasContentDescription(rowLabel)).fetchSemanticsNodes().size == 1)
        // 这一格的文案集合钉成"此刻该有谁"：只有行标签那三个字。
        // 回退成旧形状（多一行期限读数、多一颗「已完成」、多一句介绍文案、多一行灰字说明）
        // 在这里让集合变大当场红。
        assertEquals(
            "关着的时候整格只有「$rowLabel」这一串文案，实到：" + textNodes().joinToString { it.describe() },
            listOf(rowLabel), textNodes().map { it.label }
        )
        assertTrue("关着不写盘（盘上那条文本原样留着）：实到 $writes", writes.isEmpty())
    }

    // ═══════════ 2. 开 = 开关一行 + 紧跟其下的输入一行；正文回填 ═══════════

    /**
     * 拨开关只交**一笔**，交的是"屏上那条正文原样 + enabled=true"，两件之外不多任何一件。
     * 宿主把开关落定之后，输入那一行才在场，里面回填的就是盘上那条已保存正文。
     *
     * 替代关系：这一格旧形状里故意把有效期挂成 `ONE_HOUR`、并在写口收第四参"是否点名重算期限"，
     * 判的是"这一格不替用户选档、也不续期"。期限那一族随两态合同整颗退役（写口只剩正文与启用），
     * 那一问已经没有对象可问 ⇒ 由这里等价顶住：**件数**恰好一笔、**内容**逐字比对
     * （`assertEquals(期望列表, 实到列表)`）。坏实现的两条路都仍然红——多交一笔（例如失焦又交一次）
     * 让列表长度变 2；拨开关时改正文或漏正文让列表内容对不上。
     */
    @Test
    fun `switching on hands over one write and the row comes back with the saved text`() {
        val host = Host(enabledAtStart = false, textAtStart = "先约她看电影")
        val writes = mutableListOf<Write>()
        mountRow(host = host, writes = writes)

        rule.onAllNodes(hasContentDescription(rowLabel))[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "拨开只交一笔，交的是屏上那条正文原样 + enabled=true，不多一件也不少一件",
            listOf(Write("先约她看电影", true)), writes.toList()
        )

        // 落盘回来的那一拍由宿主给（这一格不预涂）：开关跟上之后输入行才在场
        rule.runOnIdle { host.enabled.value = true }
        rule.waitForIdle()

        assertEquals("开着的时候输入节点恰一颗", 1, editableCount())
        assertEquals("已保存的正文回填进输入行", "先约她看电影", editableText())
        assertEquals("「$saveLabel」那一颗在行里", 1, nodeCount(saveLabel))
        assertEquals("那颗按钮挂着合同里那枚锚点", 1, saveTagCount())
        assertEquals("回填不等于又写一笔", 1, writes.size)
    }

    /**
     * 拨关：整段当场不在树上（宿主那份读数跟上之后），文本没被这一格清空、也没被顺手写新值。
     * 重开之后草稿仍是那条已保存正文。
     */
    @Test
    fun `switching off hides the row and reopening still offers the saved text`() {
        val host = Host(enabledAtStart = true, textAtStart = "先约她看电影")
        val writes = mutableListOf<Write>()
        mountRow(host = host, writes = writes)
        assertEquals("开着的时候输入行在场", 1, editableCount())

        rule.onAllNodes(hasContentDescription(rowLabel))[0].performClick()
        rule.waitForIdle()
        assertEquals("拨关只交一笔（正文带着、enabled=false）", listOf(Write("先约她看电影", false)), writes.toList())
        assertEquals("宿主还没跟上之前那一行仍在（不预涂成关）", 1, editableCount())

        rule.runOnIdle { host.enabled.value = false }
        rule.waitForIdle()
        assertEquals("落定之后输入与保存整段都不在树上", 0, editableCount())
        assertEquals("落定那一步不等于又写一笔", 1, writes.size)

        rule.runOnIdle { host.enabled.value = true }
        rule.waitForIdle()
        assertEquals("重开后仍可改：回填的是盘上那条", "先约她看电影", editableText())
    }

    // ═══════════ 3. 两行的形状：行根锚点 + 同一行输入与保存 ═══════════

    /**
     * 开着的版式合同，一次钉齐原型那一格的全部形状：
     * · **行根**：`settings-row-intent` 恰一颗，挂在开关那一行自己身上，那一行到族里的版式下限 48dp；
     * · **紧跟**：输入那一行接在开关行的下沿，两段之间没有第三条带子（分隔线／空行）插进来，
     *   两段都还在这组内容框之内；
     * · **同一行**：那颗输入框与那颗「保存」垂直中心一致（旧形状是输入一行、按钮另起一行）；
     * · **下限**：按钮与开关各到全站下限 48（实心档的可见高 36 由芯片那圈分层外盒垫到 48）；
     * · **贴右缘、不套空盒**：按钮右缘 == 这组内容右缘；
     * · **只留一份 Spacing.sm**：输入框可编辑节点右缘到按钮左缘 = 输入框自己的内衬 [Spacing.lg]
     *   + 那一份 [Spacing.sm]，多一层空盒就在这里分开（旧形状那颗按钮外面套过一圈盒子）；
     * · **文案集合**只有行标签与「保存」两串——输入下方那一行灰字说明已经退役，
     *   placeholder 承担那句话（旧格那句 `那一行极短说明恰一句` 反过来钉成"不许有第三串"）。
     */
    @Test
    fun `the switch row is the anchored row root and the edit row sits right under it on one line`() {
        val host = Host(enabledAtStart = true, textAtStart = "先约她")
        val writes = mutableListOf<Write>()
        mountRow(host = host, writes = writes)

        val frame = bucket(PROBED_TAG)
        val rowRoot = bucket(SETTINGS_INTENT_ROW_TAG)
        val save = saveChip(host)
        val field = editableNode()
        val switch = probedActionables(host).single { it.contentDescriptions.contains(rowLabel) }

        // 行根自己到版式下限（48dp）：这一行不是被挤出来的一条小字
        assertTrue(
            "行根「$SETTINGS_INTENT_ROW_TAG」不到 ${TouchTier.SITE_FLOOR.toInt()}dp 的版式下限：" + rowRoot.describe(),
            !rowRoot.tooSmall(TouchTier.SITE_FLOOR)
        )
        // 行根就是开关那一行：标题与开关都落在它的 y 区间里（旧形状把组名与开关拆开摆就会分开）
        val label = probe.of(rule.onAllNodes(hasText(rowLabel)).fetchSemanticsNodes().single())
        assertTrue("行标签不在行根那一行里：" + label.describe() + " / " + rowRoot.describe(),
            label.centerY in rowRoot.topDp..rowRoot.bottomEdge)
        assertTrue("开关不在行根那一行里：" + switch.describe() + " / " + rowRoot.describe(),
            switch.centerY in rowRoot.topDp..rowRoot.bottomEdge)
        assertEquals("标题与开关不在同一行（中心差 ${abs(label.centerY - switch.centerY)}dp）",
            label.centerY, switch.centerY, TOLERANCE_DP)

        // 紧跟：输入那一行接在开关行下沿，中间不许多一条分隔线或一行空带
        assertTrue(
            "输入那一行不在开关行之下：" + field.describe() + " / 行根下沿 ${rowRoot.bottomEdge}dp",
            field.centerY > rowRoot.bottomEdge
        )
        // 行根下沿到按钮上沿之间只够装亚像素（分隔线／空行插进来会把这一段顶开；
        // 这一档量具挂在 density=1 的矩阵上 ⇒ dp 与 px 同数，1dp 的那一条线装不进来）
        val gapBetweenRows = save.topDp - rowRoot.bottomEdge
        assertTrue(
            "开关行与输入行之间多了 ${gapBetweenRows}dp 的那一条（原型那两段是兄弟段：不画分隔线、不留空行）：" +
                "行根 ${rowRoot.describe()} / 按钮 ${save.describe()}",
            gapBetweenRows in -TOLERANCE_DP..ROW_SEAM_DP
        )
        // 整段仍在这张组外框之内（分组归页面，这里只保证自己没长出框外）
        assertTrue("输入那一行跑到这组内容框之外：" + save.describe(), save.rightEdge <= frame.rightEdge + TOLERANCE_DP)
        assertTrue("输入那一行跑到这组内容框之外：" + field.describe(), field.bottomEdge <= frame.bottomEdge + TOLERANCE_DP)

        // 同一行：输入那颗的垂直中心与保存那颗一致
        assertEquals(
            "输入与保存不在同一行（中心差了 ${save.centerY - field.centerY}dp）：" +
                field.describe() + " / " + save.describe(),
            save.centerY, field.centerY, TOLERANCE_DP
        )
        // 两颗都到全站下限：共享一条 48dp 热区轴，不是把按钮缩成一条小字。
        // ⚠ 那颗按钮走的是实心档（`LbChipStyles.actionSolid`：分层外盒垫 48 **高**、宽度轴不垫），
        //   所以它的**宽度**下限不是盒子给的，是"两个字 + 左右各 Spacing.lg"排出来的：
        //   这一句与这一族的主人那侧 `SettingsPageSemanticsTest` 用的是同一把尺（两轴都量 48），
        //   哪天这颗红在宽度轴上，说的是那两字的排宽掉到了 48 以下，不是这一格换了尺。
        listOf(save, switch).forEach {
            assertTrue(
                "页级控件不到 ${TouchTier.SITE_FLOOR.toInt()}dp：" + it.describe(),
                !it.tooSmall(TouchTier.SITE_FLOOR)
            )
        }
        // 按钮贴着组内右缘：外面再套一层空盒 / 再叠一层内缩就会在这里分开
        assertEquals(
            "「$saveLabel」没贴到这一组的右缘（差 ${frame.rightEdge - save.rightEdge}dp）：" + save.describe(),
            frame.rightEdge, save.rightEdge, TOLERANCE_DP
        )
        // 输入与保存之间：一份 Spacing.sm，外加输入框自己的横向内衬 Spacing.lg；没有第三层
        val gapToChip = save.leftDp - field.rightEdge
        val expectedGap = Spacing.lg.value + Spacing.sm.value
        assertTrue(
            "输入与保存之间的留白不是那一份 ${Spacing.sm.value}dp（实到 $gapToChip，" +
                "里面含输入框自己的横向内衬，只许多不许少）：" + field.describe() + " / " + save.describe(),
            gapToChip + TOLERANCE_DP >= Spacing.sm.value
        )
        assertTrue(
            "输入与保存之间多出第二层留白了（实到 ${gapToChip}dp，上限是内衬 ${Spacing.lg.value}" +
                "+ 那一份间距 ${Spacing.sm.value} = ${expectedGap}dp）：按钮外面不许再套空盒、" +
                "行里也不许再叠一圈 padding —— " + field.describe() + " / " + save.describe(),
            gapToChip - TOLERANCE_DP <= expectedGap
        )
        // 开着的一格"该有谁"钉死：只有行标签与「保存」这两串。
        // 多一行「意图内容」标签、多一句期限读数、多一颗「已完成」、多回输入下方那行灰字说明，
        // 都在这里让集合变大当场红。
        assertEquals(
            "开着的时候整格的文案集合就是这两串，实到：" + textNodes().joinToString { it.describe() },
            setOf(rowLabel, saveLabel), textNodes().map { it.label }.toSet()
        )
        assertTrue("这一格不写盘：实到 $writes", writes.isEmpty())
    }

    /**
     * 输入下方那一行灰字说明（原型没有的那一行）已经从这一格退役。
     *
     * 语义树那一半由上面那一句"文案集合恰有两串"顶住；这一格补源码那一半：点名那颗资源键
     * 不许再被这一格引用。**为什么不押 `R.string.intent_row_hint`**：那句话归这一族的主人做孤儿清理，
     * 键哪天被删，引用它的 `R.string.*` 会让本文件在谁都没改界面的一天编译不过——
     * 所以这里比的是符号名（源码尺同一族先例见 `ui/UiLayerDependencyContractTest`）。
     */
    @Test
    fun `the row carries no hint line under the field anymore`() {
        val code = entrySource()
        assertFalse(
            "这一格又引用了「intent_row_hint」——原型 `.intent-edit` 里没有这一行，那句话归 placeholder 说",
            code.contains("intent_row_hint")
        )
        // 反向证人：这把尺确实在读这一格（不然是空转）
        assertTrue("这一格应当还在读那两串真文案，尺子瞎了就没法判上面那一句",
            code.contains("R.string.intent_label") && code.contains("R.string.intent_row_placeholder"))
    }

    /**
     * 行版式与共享件那一半的源码合同：**两行各自**取族里那颗 `settingsEntryRow()`，
     * 行标签走整页唯一那一颗 [LbRowLabel]，那颗按钮走这一版新落的实心档
     * [com.lovebrain.app.core.designsystem.LbChipStyles.actionSolid]。
     *
     * 为什么这三件事用源码尺而不是语义树：
     * · 行那两档（`heightIn(min = 48)`）挂在一颗**没有语义**的 `Row` 上——输入那一行自己不带 tag
     *   （锚点合同只让行根那一颗挂），语义树里读不到它，只能按符号数它出现几次；
     *   少一行、或把两行合并成一行，这一句都会红（下面那格版式合同量得到的是几何，量不出"谁给的"）。
     * · `LbRowLabel` 与 `labelMedium` 灰字在语义树里**逐像素读不出一档字阶的出处**，
     *   而这一页的判据是"整页唯一一种行标签"，那是引用关系，不是尺寸。
     * · 实心／描边是颜色，语义树根本不报颜色（像素那一面归席 F 的 `SettingsPageVisualBaselineTest`）。
     */
    @Test
    fun `both rows take the family row floor and the save uses the solid tier`() {
        val code = entrySource()
        val rowFloorCalls = occurrences(code, "settingsEntryRow()")
        assertEquals(
            "两行（开关行与输入行）各自取一次 Modifier.settingsEntryRow()，实到 $rowFloorCalls 次：" +
                "少一次就是那一行退回自己抄的 fillMaxWidth，多一次说明这一格又长出了第三段",
            2, rowFloorCalls
        )
        assertTrue("行标签没走整页唯一那颗 LbRowLabel（一页三种字阶就是10-11 那一版的脸不对）",
            occurrences(code, "LbRowLabel(") == 1)
        // 静息那一档必须是实心（白底描边会读成第二个输入框）；成功那一拍换状态色那一档，
        // 两档都住在设计系统里，由 `justSaved` 这一颗二选一——页面里不许再画第三条换色分支。
        assertTrue("「保存」没走实心档 LbChipStyles.actionSolid（白底描边会读成第二个输入框）",
            occurrences(code, "LbChipStyles.actionSolid") == 1)
        assertTrue("成功那一拍没接状态色那一档 LbChipStyles.actionSuccess（按钮不会自己亮一下）",
            occurrences(code, "LbChipStyles.actionSuccess") == 1)
        assertTrue("两档的切换不是由那一张成功票根说的（页面自己造了第二本「成功没成功」的账）",
            occurrences(code, "if (justSaved) LbChipStyles.actionSuccess else LbChipStyles.actionSolid") == 1)
        assertTrue("这颗按钮的锚点没挂或挂了两颗（锚点合同：量具按这一颗找按钮）",
            occurrences(code, "Modifier.testTag(SETTINGS_INTENT_SAVE_TAG)") == 1)
        assertTrue("开关还是现成的那颗 MiniSwitch（原型 35×21，仓库这颗 36×20 + 48 热区，不动）",
            code.contains("MiniSwitch("))
        // 反向证人：这一格没有自画分隔线（分组与组内分隔线归页面持有）
        assertFalse("这一格里出现了自画分隔线（两段之间不画线，见原型 DOM）",
            code.contains("HorizontalDivider") || code.contains("Divider("))
    }

    /** 数一个符号片段在剥过注释的源码里出现几次（"出现即可"顶不住"少一行/多一行"那一族坏法） */
    private fun occurrences(haystack: String, needle: String): Int =
        Regex(Regex.escape(needle)).findAll(haystack).count()

    // ═══════════ 4. 三档宽度：保存完整可见 ═══════════

    /**
     * 长正文在三档悬浮窗宽度下都不许把「保存」挤走。
     *
     * 比的是什么数：同一棵行另挂在 `requiredWidth(2400dp)` 里当**自然宽参照**，
     * 参照那一颗芯片量到的就是"排开'保存'两个字要多宽"（同一把字号、同一颗组件、同一段文案、
     * 同一档实心形状）；被测那一档量到的芯片宽度必须与它相同（容差之内），而且右缘仍在这组内容
     * 右缘之内。芯片被挤窄 / 文案换行（高度跟着长）/ 整颗被挤出窗口，三种坏法都会在这里红。
     * 同时被测那一档的**输入框**必须比参照那一档窄——吃掉长正文的是输入框（光标水平移动），
     * 不是把按钮推走；反过来说"按钮没被挤但整行溢出窗口"也会在这里红。
     *
     * ⚠ 一格里只 `setContent` 一次 ⇒ 260 / 300 / 350 是三格，不是一格里的循环。
     */
    @Test
    fun `a long draft keeps the whole save button visible at two sixty`() {
        assertSaveKeepsItsNaturalWidthAt(260)
    }

    @Test
    fun `a long draft keeps the whole save button visible at three hundred`() {
        assertSaveKeepsItsNaturalWidthAt(300)
    }

    @Test
    fun `a long draft keeps the whole save button visible at three fifty`() {
        assertSaveKeepsItsNaturalWidthAt(350)
    }

    private fun assertSaveKeepsItsNaturalWidthAt(widthDp: Int) {
        val longDraft = "先约她周末看电影，然后把话题从工作里带出来，别一直聊加班和同事那些事，语气要轻松一点"
        val host = Host(enabledAtStart = true, textAtStart = longDraft)
        val writes = mutableListOf<Write>()
        mountRow(host = host, writes = writes, widthDp = widthDp, withControl = true)

        val frame = bucket(PROBED_TAG)
        val controlFrame = bucket(CONTROL_TAG)
        val chips = probe.laid(
            rule.onAllNodes(hasTestTag(SETTINGS_INTENT_SAVE_TAG)).fetchSemanticsNodes().map { probe.of(it) }
        )
        val save = chips.singleOrNull { it.centerY < controlFrame.topDp }
            ?: error("${widthDp}dp 这一档被测桶里应当恰有一颗「$saveLabel」：${chips.joinToString { it.describe() }}")
        val natural = chips.singleOrNull { it.centerY >= controlFrame.topDp }
            ?: error("自然宽参照那颗没挂出来（参照挂了这格就没有可比的那个数）：${chips.joinToString { it.describe() }}")
        val fields = probe.laid(
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { probe.of(it) }
        )
        val probedField = fields.singleOrNull { it.centerY < controlFrame.topDp }
            ?: error("${widthDp}dp 这一档被测桶里应当恰有一颗输入框：${fields.joinToString { it.describe() }}")
        val naturalField = fields.singleOrNull { it.centerY >= controlFrame.topDp }
            ?: error("参照那一档的输入框没挂出来：${fields.joinToString { it.describe() }}")

        // 仪器证人两条：参照必须真摆得出宽度（0x0 会让两边都比 0、整格恒绿），
        // 而且被测与参照是**两颗**节点、不是同一颗数了两遍。
        assertTrue(
            "${widthDp}dp 这一档的自然宽参照没摆出来（实到 ${natural.widthDp}dp）：" + natural.describe(),
            natural.widthDp > 0f
        )
        assertTrue(
            "参照那颗没落在被测内容右缘之外，两桶的读数分不开：" +
                "参照 ${natural.describe()} / 被测右缘 ${frame.rightEdge}dp",
            natural.leftDp > frame.rightEdge
        )

        // ① 宽度没被挤：被测那一颗 = 自然宽那一颗
        assertTrue(
            "${widthDp}dp 这一档把「$saveLabel」挤窄了：被测 ${save.widthDp}dp、自然宽 ${natural.widthDp}dp" +
                "（正文长到 ${longDraft.length} 字也一样不许挤按钮）",
            save.widthDp + TOLERANCE_DP >= natural.widthDp
        )
        // ② 没被撑成两行：文案换行会让被测那一颗比参照高
        assertTrue(
            "${widthDp}dp 这一档的「$saveLabel」比自然宽那一颗高（文案换了行）：" +
                save.describe() + " vs " + natural.describe(),
            save.heightDp <= natural.heightDp + TOLERANCE_DP
        )
        // ③ 整颗还在窗口里：右缘不越过这一组内容的右缘
        assertTrue(
            "${widthDp}dp 这一档「$saveLabel」被挤出内容右缘：${save.describe()} / 桶 ${frame.rightEdge}dp",
            save.rightEdge <= frame.rightEdge + TOLERANCE_DP
        )
        // ④ 那颗按钮自己仍是页级下限的热区（"看得见"不等于"点得中"）
        assertTrue(
            "「$saveLabel」的点击区不到 ${TouchTier.SITE_FLOOR.toInt()}dp：" + save.describe(),
            !save.tooSmall(TouchTier.SITE_FLOOR)
        )
        // ⑤ 挤的是输入框不是按钮：被测那一档的输入框比自然宽那一档窄，且仍看得见（宽度 > 0）
        assertTrue(
            "${widthDp}dp 这一档没让输入框吃掉长正文（被测 ${probedField.widthDp}dp、" +
                "自然宽 ${naturalField.widthDp}dp）：光标水平移动编辑的那一侧必须自己缩",
            probedField.widthDp > 0f && probedField.widthDp < naturalField.widthDp - TOLERANCE_DP
        )
        // ⑥ 按钮在输入框右侧、没被推到行尾之外（同一行、贴右缘）
        assertTrue(
            "${widthDp}dp 这一档「$saveLabel」不在输入框右侧：${probedField.describe()} / ${save.describe()}",
            save.leftDp >= probedField.rightEdge
        )
    }

    // ═══════════ 5. 只有两处提交 ═══════════

    /**
     * 打字只改草稿：逐键不落盘、不续期（旧实现里"每按一键就失焦落盘一次"会把保存按钮变成摆设）。
     */
    @Test
    fun `typing changes only the draft and never writes`() {
        val host = Host(enabledAtStart = true, textAtStart = "")
        val writes = mutableListOf<Write>()
        mountRow(host = host, writes = writes)

        // 空的输入行也得让读屏念得出它是干什么的：placeholder 由那颗输入框自己转成可编辑节点的
        // contentDescription（`LbFieldInput` 里那一笔）。文本框不报角色，所以这一族判的是名字，
        // 不是"可交互就得有 Button/role"——那种写法在这台机器上永远修不红。
        // 输入下方那一行灰字说明退役之后，这句话**只有** placeholder 这一处载体，所以这一句
        // 同时是"placeholder 承担说明"那条判据的证人。
        val emptyField = editableNode()
        assertTrue(
            "空草稿下那颗输入框念不出自己是什么（说明行退役后 placeholder 是唯一的载体）：" +
                emptyField.describe(),
            emptyField.contentDescriptions.contains(placeholder)
        )

        rule.onNode(hasSetTextAction()).performTextInput("先约她")
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("看电影")
        rule.waitForIdle()

        assertEquals("打字期间一笔都不交：实到 $writes", 0, writes.size)
        assertEquals("草稿确实留在屏上", "先约她看电影", editableText())
        assertEquals("草稿不落盘也不许多长出第二颗输入节点", 1, editableCount())
    }

    /**
     * **失焦不再提交**这件事的源码那一半：这一格里不许再有 `onFocusChanged` 那一路，
     * 也不许再有"焦点状态"这类局部量。
     *
     * 为什么用源码尺而不是"点一下别处再看写没写"：语义树里的焦点在 JVM 上要靠真的第二个
     * 可聚焦件才转得动，那种夹具造出来的"失焦"和悬浮窗里那条键盘通路不是同一件事；
     * 而这一条判据要防的正是"有人把那一路接线改回来"，按符号名判更直接（同一族源码尺的
     * 先例见 `ui/UiLayerDependencyContractTest`）。注释里的名字不算一处，所以先剥注释。
     */
    @Test
    fun `the row carries no focus-loss commit path anymore`() {
        val code = entrySource()
        listOf("onFocusChanged", "FocusRequester", "fieldHadFocus", "hasFocus").forEach { needle ->
            assertFalse("这一格里又出现了「$needle」——失焦自动保存那一路不许回来", code.contains(needle))
        }
        assertTrue("这一格还得是那两处提交之一（写口没了这格就没意义）", code.contains("onIntentChange"))
    }

    /**
     * 按「保存」交出去的就是屏上那一稿正文（首尾空白去掉、正文原样），而且只交**一笔两件**。
     *
     * 替代关系：旧那一格把有效期故意挂成 `ONE_WEEK`，用它证明"按保存不许顺手改档、也不许点名
     * 重算期限"。期限那一族已经不在写口上 ⇒ 同一句牙改在"件数恰好一笔 + 正文逐字（含 trim）+
     * enabled=true"上：多写一笔、把没 trim 的草稿交出去、或不带 enabled 都当场红。
     */
    @Test
    fun `pressing save hands over the draft exactly once`() {
        val host = Host(enabledAtStart = true, textAtStart = "")
        val writes = mutableListOf<Write>()
        mountRow(host = host, writes = writes)

        rule.onNode(hasSetTextAction()).performTextInput("先约她看电影  ")
        rule.waitForIdle()
        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        rule.waitForIdle()

        assertEquals(
            "按一次保存只交一笔，交的是这一稿正文（首尾空白去掉、正文原样），除了启用状态不多一件",
            listOf(Write("先约她看电影", true)), writes.toList()
        )
        assertTrue("按下去不等于把屏上涂成成功（宿主那份没动）", host.text.value.isEmpty())
    }

    // ═══════════ 6. 正在写的那一笔，与切库那颗门 ═══════════

    /**
     * 那一笔还在飞的时候，「保存」是**按不动**，不是消失：节点仍在原位、仍念得出"保存"，
     * 只多报一个 Disabled；期间不可能再交第二笔（宿主每收一笔起一条写盘协程）。
     * 宿主那份读数跟上（写盘成功的可见凭据）之后按钮恢复，再按就是第二笔。
     *
     * ⚠ 这一格关掉自动推进时钟：默认档会在 `waitForIdle` 里把最长那一窗的虚拟时间跑完，
     * 飞行中的读数就抓不到（推进姿势见文件头）。
     */
    /**
     * 那一笔还在飞的时候，「保存」是**按不动**，不是消失：节点仍在原位、仍念得出"保存"，
     * 只多报一个 Disabled；期间不可能再交第二笔。飞行中屏上**不许**先说成功（票根没跳就不亮绿档）。
     *
     * ⚠ 这一格读的是**写侧那两个真读数**（`saving` / `saveReceipts`），不是这一格自己推演的
     * "我按了一下所以应该在飞"：上一版这里是一笔自留账 + 一颗 1.5 秒时间窗，那种账要有人负责翻回来。
     * 夹具里那句 `host.saving.value = true` 模拟的正是控制器起协程前那一步同步置真。
     */
    @Test
    fun `save is disabled while that write is in flight and comes back when the host lands it`() {
        rule.mainClock.autoAdvance = false
        val host = Host(enabledAtStart = true, textAtStart = "")
        val writes = mutableListOf<Write>()
        rule.setContent {
            UiMatrix(PROBE_WIDTH_DP, heightDp = 600).RenderIn(LocalDensity.current.density) {
                SettingsIntentEntry(
                    intentEnabled = host.enabled.value,
                    intentText = host.text.value,
                    saving = host.saving.value,
                    saveReceipts = host.receipts.value,
                    onIntentChange = { text, enabled ->
                        writes += Write(text, enabled)
                        host.saving.value = true
                    },
                    ownerKbName = "kbA"
                )
            }
        }
        pump(SETTLE_MS)

        rule.onNode(hasSetTextAction()).performTextInput("先约她")
        pump(SETTLE_MS)
        assertTrue("刚打字完、还没按保存：按钮不该是按不动的", !saveTarget().disabled)

        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        pump(SETTLE_MS)
        assertEquals("按一次只交一笔", 1, writes.size)
        val flying = saveTarget()
        assertTrue("那一笔还在飞的时候按钮必须自己说按不动：" + flying.describe(), flying.disabled)
        assertEquals("按不动不等于消失：那颗仍念得出自己是「$saveLabel」", saveLabel, flying.label)
        assertEquals("按不动的那一拍那颗按钮仍在原位、仍挂在同一枚锚点上", 1, saveTagCount())
        assertTrue("飞行中不许先说成功（票根没跳就不该亮那一档）", savedTargets().isEmpty())

        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        pump(SETTLE_MS)
        assertEquals("飞行中再按不交第二笔", 1, writes.size)

        // 落盘成功那一拍：写侧把 `isSaving` 翻回假、票根跳一号 ⇒ 按钮亮成功那一档
        rule.runOnIdle {
            host.saving.value = false
            host.text.value = "先约她"
            host.receipts.value += 1
        }
        pump(SETTLE_MS)
        val flashed = savedTargets()
        assertEquals("成功那一拍那颗按钮该换成「$savedNotice」那一档，恰一颗", 1, flashed.size)
        // `Action` 那一族交互不写 `Selected` 语义（那是互斥单选那一族才有的槽，见 `LbChip` 的
        // 动作分支），所以"亮没亮那一档"读的是**文案**：资源里那句是「$savedNotice」，
        // 屏上多出来的那枚对勾只能由 `actionSuccess` 那一档自己加（`markSelectedWithCheck`）——
        // 页面里写不出它，写了就等于把档形的话抄进页面。
        assertEquals(
            "成功那一档屏上念的不是「档位加了对勾的那一句」（对勾只能来自 actionSuccess 那一档）",
            "✓ $savedNotice",
            flashed.single().label
        )
        assertFalse("亮成功那一档不等于按不动", flashed.single().disabled)

        // 那一档到点自己收回实心档。⚠ 不抄生产那个毫秒数：一段一段推，推到文案回「保存」为止；
        // 到上限还没回就把已经推了多久一起报出来（区分"仪器没把表喂动"与"实现真不收回"）。
        var pushedMs = 0L
        while (saveNodeCount() == 0 && pushedMs < RELEASE_PUMP_CEILING_MS) {
            rule.mainClock.advanceTimeBy(RELEASE_PUMP_STEP_MS)
            rule.waitForIdle()
            pushedMs += RELEASE_PUMP_STEP_MS
        }
        assertEquals(
            "亮过之后要回实心档：「$saveLabel」那颗得重新出现（虚拟时间已推过 ${pushedMs}ms）",
            1, saveNodeCount()
        )
        assertTrue("回实心档之后不再是那一档的选中态（实到 ${saveTarget().selected}）", saveTarget().selected != true)
        assertTrue("收回之后成功那一档不再留在屏上", savedTargets().isEmpty())
    }

    /** 那几格共用的时钟姿势：逐帧推进（整段跳时间只落一记时刻、不产帧，组合跟不上会读成"没画"） */
    private fun pump(ms: Long) {
        repeat(((ms + 15L) / 16L).toInt()) { rule.mainClock.advanceTimeByFrame() }
    }

    /**
     * 被测那颗「保存」的读数（这几格没有参照桶，全树就那一颗）。
     *
     * ⚠ 读的是**芯片自己**那一颗，不是锚点合同那枚 `settings-save-button`：`enabled = false`
     * 写出来的 `Disabled` 与合并进节点的文案都长在芯片身上，而那枚锚点挂在紧贴它的外盒上
     * （两颗 `testTag` 落在同一颗节点只能活一颗，被盖掉的正是 `LbChip` 内部那颗全族计数把手
     * `LbTags.CHIP`——所以锚点另由 [saveTagCount] 盯，见 `SettingsIntentEntry` 里那句注释）。
     */
    private fun saveTarget(): SemanticsProbe.Target =
        probe.of(
            rule.onAllNodes(hasTestTag(LbTags.CHIP) and hasText(saveLabel)).fetchSemanticsNodes().single()
        )

    /** 「保存」那颗此刻在不在树上、几颗（亮成功那一档时文案换掉了，这一颗会数到 0） */
    private fun saveNodeCount(): Int =
        rule.onAllNodes(hasTestTag(LbTags.CHIP) and hasText(saveLabel)).fetchSemanticsNodes().size

    /**
     * 「$savedNotice」那一档的读数。⚠ 按**子串**找：那一档的文案是档位自己加了对勾前缀的
     * （「✓ $savedNotice」），按整串找就等于把那个前缀抄进判据——哪天对勾换了地方，
     * 判据会红成"成功档没亮"，而那其实是量具瞎。
     */
    private fun savedTargets(): List<SemanticsProbe.Target> =
        rule.onAllNodes(hasTestTag(LbTags.CHIP) and hasText(savedNotice, substring = true))
            .fetchSemanticsNodes().map { probe.of(it) }
            // ⚠ 这里**不能**走 `probe.laid`：那一颗在空集上是抛的（它的用途是"我要量的那批
            // 必须量得到"），而"成功那一档没亮"恰恰是这一族要放行的一种正常读数（失败那一路、
            // 以及飞行中都必须不亮）。空集就交回空集，让调用方按空/非空判；
            // 真被压成 0x0 的那一颗由下面的尺寸断言兜住。
            .filter { it.widthDp > 0f && it.heightDp > 0f }

    /**
     * 写失败那一路：屏上不能留一颗永远按不动的「保存」，也**绝不能**亮成功那一档。
     *
     * 新形状下这一路不需要"到点自动放行"那种时间窗：写侧那颗 `isSaving` 在 `finally` 里
     * 无论成功失败都翻回假（那颗由 `IntentSaveReceiptTest` 在控制器上钉），
     * 而票根只在真正落盘成功那一次才跳——所以失败这一路屏上看到的必然是：
     * 按钮重新按得动、文案仍是「$saveLabel」、草稿原样留着、再按就是一笔新的重试。
     *
     * 反例形状：① 失败也去跳票根 ⇒ 下面那句「不许亮成功档」当场红；
     * ② 失败不翻 `isSaving` ⇒ 循环到上限、最后一句红。
     */
    @Test
    fun `a write that never lands releases the save button and keeps the draft`() {
        rule.mainClock.autoAdvance = false
        val host = Host(enabledAtStart = true, textAtStart = "")
        val writes = mutableListOf<Write>()
        rule.setContent {
            UiMatrix(PROBE_WIDTH_DP, heightDp = 600).RenderIn(LocalDensity.current.density) {
                SettingsIntentEntry(
                    intentEnabled = host.enabled.value,
                    intentText = host.text.value,
                    saving = host.saving.value,
                    saveReceipts = host.receipts.value,
                    onIntentChange = { text, enabled ->
                        writes += Write(text, enabled)
                        host.saving.value = true
                    },
                    ownerKbName = "kbA"
                )
            }
        }
        pump(SETTLE_MS)
        rule.onNode(hasSetTextAction()).performTextInput("先约她")
        pump(SETTLE_MS)
        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        pump(SETTLE_MS)
        assertEquals(1, writes.size)
        assertTrue("刚交出去那一笔还在飞的时候，那颗按钮该是按不动的：" + saveTarget().describe(),
            saveTarget().disabled)

        // 失败落定：写侧把那颗翻回假，票根**不跳**（宿主那份正文也没被换掉）
        rule.runOnIdle { host.saving.value = false }
        pump(SETTLE_MS)
        assertTrue("失败之后按钮必须重新按得动，不然用户没法重试：" + saveTarget().describe(),
            !saveTarget().disabled)
        assertTrue("失败那一路不许亮成功那一档", savedTargets().isEmpty())
        assertEquals("草稿在失败那一路原样留着", "先约她", editableText())

        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        pump(SETTLE_MS)
        assertEquals("重试那一按就是一笔新的", 2, writes.size)
    }

    /**
     * **切库保护**那颗门还在：这一稿正文的主人是 A 库，而屏幕已经换成 B 库时那一按**不交出去**
     * （不把 A 的正文写给 B），也不静默失灵——它把草稿交回屏幕上那一份，于是下一次按写的就是
     * 现在这块库那份数据。
     *
     * 这一格为什么要在这颗件自己的测试里再钉一遍：`intentWriteOwnedBy` 那颗纯函数由
     * `SettingsPageStructureTest` 钉真值表，但**接线**在这一格里（谁在写之前调它、拦下之后屏幕
     * 上留什么）。把纯函数保留、把调用删掉，真值表那一格照样全绿。
     * 反例形状：删掉 `commit` 里那一句 `if (!intentWriteOwnedBy(...))` ⇒ 下面第一个
     * `assertEquals(0, writes.size)` 当场红（A 的稿被写给 B）。
     */
    @Test
    fun `a draft owned by another library is not handed over after the screen switched`() {
        val host = Host(enabledAtStart = true, textAtStart = "")
        val ownerKb = mutableStateOf("kbA")
        val writes = mutableListOf<Write>()
        rule.setContent {
            UiMatrix(PROBE_WIDTH_DP, heightDp = 600).RenderIn(LocalDensity.current.density) {
                SettingsIntentEntry(
                    intentEnabled = host.enabled.value,
                    intentText = host.text.value,
                    onIntentChange = { text, enabled -> writes += Write(text, enabled) },
                    ownerKbName = ownerKb.value
                )
            }
        }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction()).performTextInput("A 库那一稿")
        rule.waitForIdle()
        assertEquals("打字本身不落盘", 0, writes.size)

        // 切库：屏幕上这块库与它那份正文一起换成 B（宿主那两份读数同源）
        rule.runOnIdle {
            host.text.value = "B 库那条已保存"
            ownerKb.value = "kbB"
        }
        rule.waitForIdle()
        assertEquals("切库后回填的是屏幕上这一块库那份", "B 库那条已保存", editableText())

        // 第一按：这一稿的主人还是 A ⇒ 一个字都不写给 B
        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        rule.waitForIdle()
        assertEquals("切库之后那一按不交出去（不写给错的人）：实到 $writes", 0, writes.size)
        assertEquals(
            "拦下之后不留「看着是 A、写进 B」的中间态：草稿已经交回屏幕上那一份",
            "B 库那条已保存", editableText()
        )

        // 第二按：主人已经归零 ⇒ 正常交给现在这块库
        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        rule.waitForIdle()
        assertEquals("再按就是写给现在这块库的那一笔", listOf(Write("B 库那条已保存", true)), writes.toList())
    }

    // ═══════════ 7. 悬浮窗那条键盘通路 ═══════════

    /**
     * 触碰输入区要通知宿主进入编辑态（悬浮窗那条 `FLAG_NOT_FOCUSABLE` 通路），
     * 而这件事本身不写盘。
     */
    @Test
    fun `touching the field tells the host it is editing without writing`() {
        var edits = 0
        val host = Host(enabledAtStart = true, textAtStart = "先约她")
        val writes = mutableListOf<Write>()
        mountRow(host = host, writes = writes, onInputIntent = { edits++ })

        rule.onNode(hasSetTextAction()).performClick()
        rule.waitForIdle()
        assertTrue("碰到输入区没把编辑意图投给宿主＝输入法弹不出来（实到 $edits 次）", edits >= 1)
        assertTrue("编辑意图不等于写盘：实到 $writes", writes.isEmpty())
    }

    // ═══════════ 8. 长度那一把尺（F05A）与回执的归属（F05B） ═══════════

    /**
     * **正好在上限**那一稿与**过界一个字**那一稿的两种形状，一次钉齐（书 §6：提示要可见、温和、
     * 不会丢文字；保存超限要置灰，不要静默 take(200)）。
     *
     * 为什么两半写在同一格里：先按正好 200 字那一稿交成一笔，再把正文推过界，才能证明"交不成"
     * 这件事是**长度**造成的，而不是"这颗按钮本来就点不动""这一格压根没接写口"。
     * 反例形状四条：
     * · 门写成 `>=`（或把尺抄成第二份数）⇒ 前面那句"正好在上限正常交一笔"当场红；
     * · 静默 `take(200)` ⇒ "上限那一稿交的是整份正文"那条 `assertEquals(期望列表, 实到列表)` 红
     *   （列表比的是**全文**，不是长度）；
     * · 超限只把按钮**藏掉** ⇒ `saveTagCount()==1` 与"仍念得出保存"那两句红；
     * · 只闸保存、不闸开关（旧形状里拨开关仍然把超长那一稿写出去）⇒ 最后那句 `writes.size` 从 1
     *   变 2 当场红。
     */
    @Test
    fun `a draft at the limit goes whole while one over it says why and writes nothing`() {
        val host = Host(enabledAtStart = true, textAtStart = "")
        val writes = mutableListOf<Write>()
        mountRow(host = host, writes = writes)

        val atLimit = "意".repeat(INTENT_MAX_LENGTH)
        rule.onNode(hasSetTextAction()).performTextInput(atLimit)
        rule.waitForIdle()
        assertEquals("正好在上限那一稿屏上一个字都没被吃掉", INTENT_MAX_LENGTH, editableText().length)
        assertEquals("正好在上限那一稿不该挂超限那句", 0, nodeCount(tooLongMsg))
        assertFalse("正好在上限那一稿保存点得动（门是 > 而不是 >=）：" + saveTarget().describe(),
            saveTarget().disabled)
        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "上限那一稿正常交一笔，交的是整份正文（这里不静默截断，超限那一侧另有门）",
            listOf(Write(atLimit, true)), writes.toList()
        )

        // 过界一个字：形状换成"看得见原因、灰着还在、一个字都不交"
        rule.onNode(hasSetTextAction()).performTextInput("图")
        rule.waitForIdle()
        assertEquals("超限那一稿正文仍然原样留在屏上（不许静默截断，用户得知道自己写了多少）",
            INTENT_MAX_LENGTH + 1, editableText().length)
        assertEquals("超限那一句要读得到，且只有一份", 1, nodeCount(tooLongMsg))
        val gated = saveTarget()
        assertTrue("超限时那颗「$saveLabel」是按不动的：" + gated.describe(), gated.disabled)
        assertEquals("按不动不等于消失：那颗仍念得出自己是谁", saveLabel, gated.label)
        assertEquals("按不动的那一拍那颗仍挂在同一枚锚点上", 1, saveTagCount())
        assertEquals(
            "超限的一格文案集合只有这三串（多一行说明卡／旧读数就是又长了一张脸），实到：" +
                textNodes().joinToString { it.describe() },
            setOf(rowLabel, saveLabel, tooLongMsg), textNodes().map { it.label }.toSet()
        )

        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        rule.waitForIdle()
        assertEquals("超限那一按不交盘（只多一次看得见的原因，不多一笔脏数据）：实到 $writes", 1, writes.size)

        rule.onAllNodes(hasContentDescription(rowLabel))[0].performClick()
        rule.waitForIdle()
        assertEquals("超限那一稿拨开关也不交出去（两处提交同一条闸）：实到 $writes", 1, writes.size)
    }

    /**
     * 长度那一把尺**只有一条**（源码那一半）：这一格不许抄第二份数，也不许自己另写一句话。
     *
     * 为什么语义树那一半顶不住这一句：把尺抄成 `> 200` 之后，屏上的一切与现在**一模一样**
     * （提示照样挂、按钮照样灰），只有"哪天浮层那颗尺动了、两处入口又各走各的"这一种坏法看得出来——
     * 那正是书 §6 抱怨的"两处入口规则不一致"的来路。按符号名判（同一族源码尺先例见
     * `ui/UiLayerDependencyContractTest`、上面那颗 `intent_row_hint`）。
     */
    @Test
    fun `the length gate reads the one shared ruler and no second copy of the number`() {
        val code = entrySource()
        assertTrue("这一格的超限门没引用那颗公共的尺 INTENT_MAX_LENGTH（两处入口各自一份数就是这么回来的）",
            code.contains("INTENT_MAX_LENGTH"))
        assertTrue("这一格的超限提示没走浮层那一条资源（两处念的是两句话）",
            code.contains("R.string.intent_too_long_error"))
        assertFalse("这一格里出现了字面的长度数——那是第二把尺", code.contains("200"))
        // 反向证人：这把尺确实数得出"第二把尺"那种写法（不然这三句是恒绿的空转）
        assertTrue("证人：抄一份字面数进来时这把尺要数得到",
            "val overLimit = draft.length > 200".contains("200"))
    }

    /**
     * 「已保存」那一档属于**这一次点击**（F05B）：成功票根只认相对本入口上屏基线的增量。
     *
     * 旧形状 `LaunchedEffect(saveReceipts) { if (saveReceipts == 0) return … }` 读的是"这台机器
     * 历史上成功保存过没有"：在浮层那一扇保存过一次再进设置页，首帧就白亮 1.3 秒的绿档——
     * 屏上说了一件这一刻没发生过的事。反例形状两条：
     * · 判据回成 `!= 0` ⇒ 下面第一句"首帧不亮"当场红（带着历史值 5 进页）；
     * · 增量那条门写成相等就返回、跳号也不推进基线 ⇒ 第二句"这一笔成功要亮"红。
     *
     * ⚠ 时钟同第 6 件那几格的口径：先冻住自动推进，否则 `waitForIdle` 会把那一窗虚拟时间喂完，
     * "刚亮起"与"已收回"两个读数合成一个。
     */
    @Test
    fun `a receipt left by another entrance does not light the save but this one does`() {
        rule.mainClock.autoAdvance = false
        val host = Host(enabledAtStart = true, textAtStart = "先约她")
        val writes = mutableListOf<Write>()
        // 别处已经成功保存过 5 次：票根带着**历史值**交下来（这正是"进页首帧白亮"那一条坏法的现场）
        host.receipts.value = 5
        rule.setContent {
            UiMatrix(PROBE_WIDTH_DP, heightDp = 600).RenderIn(LocalDensity.current.density) {
                SettingsIntentEntry(
                    intentEnabled = host.enabled.value,
                    intentText = host.text.value,
                    saving = host.saving.value,
                    saveReceipts = host.receipts.value,
                    onIntentChange = { text, enabled -> writes += Write(text, enabled) },
                    ownerKbName = "kbA"
                )
            }
        }
        pump(SETTLE_MS)
        assertTrue("带着历史票根进页，首帧不许白亮成功那一档（那是别处那一笔的回执）",
            savedTargets().isEmpty())
        assertEquals("首帧那颗仍念得出「$saveLabel」", 1, saveNodeCount())

        // 这一页自己按一次，写侧把票根从 5 跳到 6 ⇒ 那一档亮的是**这一笔**
        rule.onAllNodes(hasText(saveLabel))[0].performClick()
        pump(SETTLE_MS)
        assertEquals("按一次交一笔", listOf(Write("先约她", true)), writes.toList())
        rule.runOnIdle { host.receipts.value = 6 }
        pump(SETTLE_MS)
        assertEquals("这一笔落盘成功之后该亮，恰一颗", 1, savedTargets().size)

        // 到点自己收回实心档（推法同第 6 件那一格：不抄生产那个毫秒数）
        var pushedMs = 0L
        while (saveNodeCount() == 0 && pushedMs < RELEASE_PUMP_CEILING_MS) {
            rule.mainClock.advanceTimeBy(RELEASE_PUMP_STEP_MS)
            rule.waitForIdle()
            pushedMs += RELEASE_PUMP_STEP_MS
        }
        assertEquals("亮过之后要回实心档（虚拟时间已推过 ${pushedMs}ms）", 1, saveNodeCount())
        assertTrue("收回之后成功那一档不再留在屏上", savedTargets().isEmpty())
    }

    companion object {
        /**
         * 被测那一遍 / 自然宽参照那一遍的挂载桶把手（只在本文件里用；两遍同字号才量得是同一种字）。
         *
         * 把手长在 [mountRow] 那两只测试自己的盒子上，而不是生产那棵件里：宽度那三格把**同一棵件**
         * 挂两遍（被测一档 + 参照一档），tag 若长在本组件的节点上就让两只桶数到同一颗，
         * "这一桶恰有一颗"那一句当场没有牙。本仓同一族写法与命名先例：
         * `ui/home/LongProviderNameSemanticsTest`、`core/designsystem/LbListCardTest`、
         * `ui/panel/stats/UsageStatBarSemanticsTest` 的 companion。生产那侧零消费。
         *
         * ⚠ 行根与那颗按钮的两枚锚点**不是**这里的把手：它们是锚点合同的跨席合同，
         *   长在 `SettingsIntentEntry.kt` 自己身上（`SETTINGS_INTENT_ROW_TAG` / `SETTINGS_INTENT_SAVE_TAG`）。
         */
        private const val PROBED_TAG = "settings_intent_row_probed"
        private const val CONTROL_TAG = "settings_intent_row_control"

        /** 除三档宽度那三格之外，其余各格挂载用的默认宽度（中间那一档） */
        private const val PROBE_WIDTH_DP = 300

        /** 自然宽参照：宽到任何一行都排得开，量出来的就是"排开要多宽" */
        private val CONTROL_WIDTH = 2400.dp

        /** 一次推进的最短落定段 */
        private const val SETTLE_MS = 48L

        /**
         * 手动喂那一窗的步长与耐心上限（毫秒）。
         *
         * 这两个数只是仪器肯走多远虚拟时间，**不是**那一窗的长度（长度是生产那颗私有常量，
         * 这一格读不到也不该读）：判据是"一笔永不落定的写最终会放行"，
         * 到上限还按不动就把已经推了多少毫秒一起报出来。
         */
        private const val RELEASE_PUMP_STEP_MS = 200L
        private const val RELEASE_PUMP_CEILING_MS = 15_000L

        /** 语义树边界的读数容差（dp）：描边那 1dp 与亚像素舍入都落在这一档里 */
        private const val TOLERANCE_DP = 1.5f

        /**
         * 开关行与输入行之间允许的那一段接缝（dp）。
         *
         * 只够装亚像素的读数误差：这一族量具跑在 `mdpi`（density = 1，dp 与 px 同数）的矩阵上，
         * 所以"两条行直接相接"量出来就是 0。插一条分隔线（1dp）、一行说明或多一圈 padding
         * 都会把这一段顶到这里面装不下的地方。
         */
        private const val ROW_SEAM_DP = 0.6f
    }
}
