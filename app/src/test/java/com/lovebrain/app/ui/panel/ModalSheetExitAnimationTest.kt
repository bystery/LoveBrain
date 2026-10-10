package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.MuteDuration
import com.lovebrain.app.ui.panel.reply.CorrectionCenterHolder
import com.lovebrain.app.ui.panel.reply.CorrectionCenterHost
import com.lovebrain.app.ui.panel.reply.MemoryCorrectionFlow
import com.lovebrain.app.ui.panel.reply.MemoryCorrectionFlowHost
import com.lovebrain.app.ui.panel.reply.durationLabel
import com.lovebrain.app.ui.panel.settings.LoveBrainSettingsContent
import com.lovebrain.app.ui.panel.settings.SettingsIntentEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 浮层的**退场**到底播没播——三个调用点各钉一到两格，外加一把源码尺。
 *
 * 缺陷形状（同一句话记在 `LbModalSheet` 的 `visible` 参数 KDoc 上）：公共壳里那棵
 * `AnimatedVisibility` 原来写死 `visible = true`，调用方用 `if (show) { LbModalSheet(...) }`
 * 挂载／卸载整棵树。入场动画能跑（挂上那一帧 visible 从 false 变 true），
 * **退场永远跑不到**：关的那一帧整棵树被摘掉，exit 还没开始播就跟着树一起没了。
 * 现在三处都改成 `visible` 驱动、树常驻：
 * `ui/panel/settings/SettingsIntentEntry.kt`（意图介绍浮层）、
 * `ui/panel/reply/MemoryCorrectionFlow.kt`（暂停时长／标记为错误两扇）、
 * `ui/panel/reply/CorrectionCenter.kt`（纠正中心）。
 *
 * ── 这台仪器量得到什么、量不到什么（诚实边界） ──
 * JVM 语义树**看不见像素与 alpha**，所以这里不声称量到了那 200ms 的淡出曲线本身。
 * 量到的是「退场能播」的必要条件，而且每一句都能反证旧写法：
 * · `visible` 翻回 false 之后、退场时长之内，卡片内容**必须仍在组合中**（读得到语义节点）。
 *   旧写法在这一格读到 0——树都被摘了，动画自然无处可播；
 * · 退场时长之后**必须回 0**（钉的是常驻不等于画而不见：摘除仍然发生，只是晚一拍）。
 *   反例：忘了摘、或者在外面又裹一层条件却把判据写坏 ⇒ 这一句红；
 * · 淡出途中那一行**仍交得对最后请求的那条记忆 id**（holder 里 `lastMuteTargetId` 那一份余温）。
 *   反例：内容继续读 `muteTargetId ?: ""` ⇒ 交回空串，这一句红；
 * · 淡出途中**草稿框里那句话还读得到**（holder 里 `lastWrongDraft` 那一份余温）。
 *   这一条抓的是同族的另一半缺陷：树留住了、内容却被瞬空——`dismiss()` 把 `wrongDraft`
 *   清成空串，而卡片还在淡出，用户看到的就是"自己打的话在一扇正在关的浮层里当场蒸发"。
 *   反例①：内容直接读 `flow.wrongDraft` ⇒ 淡出途中读出空串，这一句红；
 *   反例②：仍按旧 `if` 挂载 ⇒ 淡出途中根本没有输入框节点，同样红；
 * · 源码尺：每一处 `LbModalSheet(` 的实参里都得有 `visible =`，而且调用点必须待在
 *   **函数体的顶层**——被 `if (show) {` 或 `target?.let {` 包住（相对深度多一格）就是那个旧形状。
 *
 * ⚠ 时钟口径：这些格子**必须**关掉 `mainClock.autoAdvance`。本仓库默认它是开的
 * （`PanelPageSwipePagerTest:718` 就记着「否则 waitForIdle 会把那 200ms 一路推完」），
 * 而取一次语义树内部就会调 `waitForIdle`——不关的话退场在读取那一刻已经播完，
 * 「淡出途中仍在树上」这一句永远读到 0，这一格就退化成恒红。关掉之后唯一的推进办法是手动推帧。
 *
 * 挂载槽按 `CorrectionCenterTest` / `MemoryCorrectionFlowTest` 那一族：360x900dp。
 * 这三处生产代码都在同一棵 ComposeView 里自画遮罩（不是另一扇窗口），整扇浮层在本机挂得上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModalSheetExitAnimationTest {

    private companion object {
        /** 退场进行中那一格：200ms（SHEET_ANIM_MS）的差不多四分之一，动画还在半路 */
        const val MID_EXIT_MS = 48L

        /** 比退场时长更久：动画一定已经落地 */
        const val SETTLED_MS = 320L

        /** SettingsIntentEntry 记「首次已看过介绍」的那份 prefs：测前先清，否则不弹 */
        const val SETTINGS_PREFS = "lovebrain_settings"
    }

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun nodesWith(text: String): Int =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /**
     * `autoAdvance=false` 下的推进姿势（同一形见 `LbPrimaryButtonStateTest:254`、
     * `PanelPageSwipePagerTest:226`）：状态变更要在**逐帧**里走重组，整段跳时间只落一记
     * 时刻、不产帧，组合没跟上就会读成"浮层没起"。帧距 16ms：3 帧=48ms 仍在 200ms 退场
     * 半路，20 帧=320ms 保证动画落地摘树。
     */
    private fun pump(ms: Long) {
        repeat(((ms + 15L) / 16L).toInt()) { rule.mainClock.advanceTimeByFrame() }
    }

    /**
     * `autoAdvance=false` 下的推进姿势（同一形见 `LbPrimaryButtonStateTest:254`、
     * `PanelPageSwipePagerTest:226`）：状态变更要在**逐帧**里走重组，整段跳时间不产帧——
     * 跳 320ms 只落一记时刻，组合没跟上，读到的就是"浮层没起"。
     * 帧距 16ms：3 帧=48ms 在 200ms 退场中段，20 帧=320ms 必过界落定。
     */
    private fun pumpFrames(ms: Long) {
        repeat(((ms + 15L) / 16L).toInt()) { rule.mainClock.advanceTimeByFrame() }
    }

    // ═══════════ ① 纠正中心 ═══════════

    /**
     * 纠正中心：close 之后卡片先留在树里（退场播得完），播完才没了（不是画而不见）。
     * 旧写法 `if (!holder.isOpen) return` 红在中间那一句：close 的那一帧就读到 0。
     */
    @Test
    fun `the correction center is dropped only after its exit has played`() {
        val holder = CorrectionCenterHolder()
        rule.setContent {
            UiMatrix(360, heightDp = 900).RenderIn(LocalDensity.current.density) {
                CorrectionCenterHost(holder = holder, corrections = emptyMap(), onUndoCorrection = {})
            }
        }
        val title = "记忆纠正中心"
        // 落定段用默认 autoAdvance + waitForIdle（同 `PanelPageSwipePagerTest:188` 的 mountPager）：
        // 从头 `autoAdvance=false` 时，测试线程直接调的 `holder.open()` 走不进重组——
        // 只有 performClick 自带 idle 等待，这就是四格全红在"起浮层"那一读的成因。
        rule.waitForIdle()
        assertEquals("没 open 之前不该画任何东西", 0, nodesWith(title))

        holder.open()
        rule.waitForIdle()
        assertEquals("open 之后恰好一颗", 1, nodesWith(title))

        rule.mainClock.autoAdvance = false
        holder.close()
        pump(MID_EXIT_MS)
        assertEquals(
            "close 之后、退场播完之前，卡片必须还在组合里（动画就播在这段）；" +
                "读到 0 就是又回到了「整棵树跟着没」那个形状",
            1, nodesWith(title)
        )

        pump(SETTLED_MS)
        assertEquals("退场播完才许摘树，一直留着就是画而不见", 0, nodesWith(title))
    }

    // ═══════════ ② 暂停时长 ／ 标记为错误 ═══════════

    /**
     * 暂停时长那一扇：淡出途中整张卡片仍然画得完整，而且**仍然交得对那一条记忆**。
     *
     * 淡出途中那一记点的是 `durationLabel(THIS_ROUND)` 那一行：旧写法里这一刻树上什么都没有，
     * 红在「找不到节点」；内容若继续读 `muteTargetId ?: ""`（不取 holder 那份余温），
     * 红在「交回的 id 是空串」。两条各抓一种坏实现。
     */
    @Test
    fun `the mute sheet fades out with its last request still drawn`() {
        val flow = MemoryCorrectionFlow()
        val sent = mutableListOf<Pair<String, MuteDuration>>()
        rule.setContent {
            UiMatrix(360, heightDp = 900).RenderIn(LocalDensity.current.density) {
                MemoryCorrectionFlowHost(
                    flow = flow,
                    onMute = { id, duration -> sent += id to duration },
                    onWrong = { _, _ -> }
                )
            }
        }
        val muteTitle = ctx.getString(R.string.memory_mute_sheet_title)
        val thisRound = durationLabel(MuteDuration.THIS_ROUND)
        rule.waitForIdle()
        assertEquals("没有请求时不该有遮罩", 0, nodesWith(muteTitle))
        assertEquals("没有请求时那一行时长也不该在", 0, nodesWith(thisRound))

        flow.requestMute("mem-A")
        rule.waitForIdle()
        assertEquals("请求进来该起浮层", 1, nodesWith(muteTitle))

        rule.mainClock.autoAdvance = false
        flow.dismiss()
        pump(MID_EXIT_MS)
        assertEquals("淡出途中：标题仍该在树上", 1, nodesWith(muteTitle))
        MuteDuration.entries.forEach { duration ->
            assertEquals(
                "淡出途中「${durationLabel(duration)}」那一档仍该画着（退场的是整张卡片，不是一层空遮罩）",
                1, nodesWith(durationLabel(duration))
            )
        }

        val rows = rule.onAllNodes(hasText(thisRound) and hasClickAction()).fetchSemanticsNodes().size
        assertEquals("淡出途中那一行仍该点得动，实到 $rows", 1, rows)
        rule.onAllNodes(hasText(thisRound) and hasClickAction())[0].performClick()
        // 关掉 autoAdvance 之后，注入的那一记要手动推帧才落进组合（同一写法见 LbPrimaryButtonStateTest）
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(
            "淡出途中点「$thisRound」交的必须还是那一条记忆（实到：$sent）",
            listOf("mem-A" to MuteDuration.THIS_ROUND), sent.toList()
        )

        pump(SETTLED_MS)
        assertEquals("退场播完才摘树", 0, nodesWith(muteTitle))
    }

    /** 另一扇同理：标错那一张也走 visible，不再被 `wrongTargetId?.let` 摘在退场之前 */
    @Test
    fun `the mark wrong sheet fades out too instead of vanishing with the tree`() {
        val flow = MemoryCorrectionFlow()
        rule.setContent {
            UiMatrix(360, heightDp = 900).RenderIn(LocalDensity.current.density) {
                MemoryCorrectionFlowHost(flow = flow, onMute = { _, _ -> }, onWrong = { _, _ -> })
            }
        }
        val wrongTitle = ctx.getString(R.string.memory_mark_wrong_sheet_title)
        rule.waitForIdle()
        assertEquals("没有请求时不该有遮罩", 0, nodesWith(wrongTitle))

        flow.requestWrong("mem-B")
        rule.waitForIdle()
        assertEquals("请求进来该起浮层", 1, nodesWith(wrongTitle))

        rule.mainClock.autoAdvance = false
        flow.dismiss()
        pump(MID_EXIT_MS)
        assertEquals("淡出途中仍该在树上（旧写法这里读到 0）", 1, nodesWith(wrongTitle))
        pump(SETTLED_MS)
        assertEquals("退场播完才摘树", 0, nodesWith(wrongTitle))
    }

    /**
     * 淡出途中**那句话**还在不在——这一格抓的是同族里另一半缺陷：
     * 挂载形态改对了、内容却在这一段被瞬空。
     *
     * `dismiss()` 清 `wrongDraft` 是业务合同（`MemoryCorrectionFlowTest` 逐字钉着"dismiss 也要
     * 清掉草稿"），可卡片还要淡出 200ms；卡片里那只框若直接读业务那一份，
     * 淡出的画面上就是用户刚打的那句话当场蒸发。生产取的是 holder 的 `lastWrongDraft` 余温。
     *
     * 三种坏实现各红一处（旧 `if` 挂载形态同样红，因为那时树上根本没有这只框）：
     * ① `value = flow.wrongDraft` ⇒ 淡出途中读出空串；
     * ② 把 `dismiss()` 改成不清草稿 ⇒ 末尾那两句（业务语义）红；
     * ③ 余温不随下一次 `requestWrong` 归零 ⇒ 上一轮的话预进下一轮那张卡。
     */
    @Test
    fun `the mark wrong sheet does not empty its draft box while it fades`() {
        val flow = MemoryCorrectionFlow()
        val handed = mutableListOf<Pair<String, String>>()
        rule.setContent {
            UiMatrix(360, heightDp = 900).RenderIn(LocalDensity.current.density) {
                MemoryCorrectionFlowHost(
                    flow = flow,
                    onMute = { _, _ -> },
                    onWrong = { id, text -> handed += id to text }
                )
            }
        }
        rule.waitForIdle()
        assertEquals("没有请求时不该有输入框", 0, editableNodes().size)

        flow.requestWrong("mem-B")
        rule.waitForIdle()
        assertEquals("请求进来该起浮层，且只有一只输入框", 1, editableNodes().size)
        val draft = "她说明天再答，不是答应约会"
        // 草稿走 holder 那一句写（`editWrongDraft`），不走 IME：这一格判的是"渲染读的是哪一份"，
        // 打字那一早由 `MemoryCorrectionFlowTest`「confirming hands over the id and the draft」钉着。
        flow.editWrongDraft(draft)
        rule.waitForIdle()
        assertEquals("挂载证人：开着的时候那句话读得到（读不到就是量具坏了，整格会假绿）", draft, draftInTree())

        rule.mainClock.autoAdvance = false
        flow.dismiss()
        pump(MID_EXIT_MS)
        assertEquals("淡出途中这只框仍该在树上（旧 if 挂载在这里读到 0）", 1, editableNodes().size)
        assertEquals(
            "淡出途中框里那句话不许当场蒸发——树留住了却画一层空白，等于退场没播完",
            draft, draftInTree()
        )
        // 余温只是画面余温：业务那一份该照旧被 dismiss 清掉（这条合同不许被这格带坏）
        assertEquals("dismiss 仍要清掉业务那份草稿（MemoryCorrectionFlowTest 钉着同一句）", "", flow.wrongDraft)

        rule.onAllNodes(hasText(ctx.getString(R.string.a11y_action_confirm)))[0].performClick()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(
            "淡出途中点「确认」交的必须还是那一条 + 那一句话（实到：$handed）",
            listOf("mem-B" to draft), handed.toList()
        )

        pump(SETTLED_MS)
        assertEquals("退场播完才摘树", 0, editableNodes().size)

        // 下一轮请求进来：余温必须归零，否则上一轮的话会预填进这一轮那张卡
        // （开着那一格读的是业务那一份，量不出这一条 ⇒ 只对 holder 说一句，它是渲染余温不是业务状态）
        rule.mainClock.autoAdvance = true
        flow.requestWrong("mem-C")
        rule.waitForIdle()
        assertEquals("新一轮的草稿框该是空的", "", draftInTree())
        assertEquals("新一轮要把上一轮的余温一起归零", "", flow.lastWrongDraft)
    }

    /** 浮层里那只有 `SetText` 的节点（这一棵子树里只该有草稿框那一只） */
    private fun editableNodes() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()

    /**
     * 那只框里**现在画着的那句话**：取语义树的 `EditableText` 那一栏——
     * 它才是真输入值（同一口径见 `InputFieldLabelsTest:62`、`KbEditScreenStatesTest:325`）。
     */
    private fun draftInTree(): String? =
        editableNodes().firstOrNull()?.config
            ?.getOrNull(SemanticsProperties.EditableText)?.text

    // ═══════════ ③ 设置页那扇意图介绍浮层 ═══════════

    /** 「确认后才落盘」那一条业务语义的形状（本轮改的是挂载形态，这一句一个字没动） */
    private data class Saved(
        val text: String,
        val enabled: Boolean,
        val expiry: IntentExpiry,
        val recomputeExpiry: Boolean
    )

    /**
     * 介绍浮层：确认那一记之后先淡出（树仍在），淡完才摘。
     *
     * 顺带钉住两条我没让我动的语义：
     * · 首次拨开关**只弹浮层**，确认前一颗盘都不落（§10.2）；
     * · 「知道了」落的仍是 `onIntentChange(当前正文, enabled = true, 当前档, 不换档)` 那一句。
     */
    @Test
    fun `the intent intro is dropped only after its exit has played`() {
        rule.mainClock.autoAdvance = false
        ctx.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val saved = mutableListOf<Saved>()
        rule.setContent {
            UiMatrix(360, heightDp = 900).RenderIn(LocalDensity.current.density) {
                LoveBrainSettingsContent(
                    onBack = {},
                    onCollapse = {},
                    opacityPercent = 100,
                    onOpacityPreview = {},
                    onOpacityCommit = {},
                    assistantOn = true,
                    onAssistantEnable = {},
                    onAssistantClose = {},
                    bubbleSizeDp = BubbleSizeTier.STANDARD_DP,
                    onBubbleSizeChange = {},
                    replyCardVertical = true,
                    onReplyCardVerticalChange = {},
                    intentEnabled = false,
                    intentText = "",
                    intentExpiry = IntentExpiry.ONE_DAY,
                    onIntentChange = { text, enabled, expiry, recompute ->
                        saved += Saved(text, enabled, expiry, recompute)
                    }
                )
            }
        }
        val body = ctx.getString(R.string.intent_intro_body)
        val acknowledge = ctx.getString(R.string.intent_intro_acknowledge)
        val switchName = ctx.getString(R.string.intent_label)
        pump(SETTLED_MS)

        assertEquals("没拨开关之前不该有介绍浮层", 0, nodesWith(body))
        val switches = rule.onAllNodes(hasContentDescription(switchName)).fetchSemanticsNodes().size
        assertEquals("那一格该恰好有一颗开关，实到 $switches", 1, switches)

        // 首次拨开：只弹浮层，确认前不启用、不落盘
        rule.onAllNodes(hasContentDescription(switchName))[0].performClick()
        pump(SETTLED_MS)
        assertEquals("首次拨开该起介绍浮层", 1, nodesWith(body))
        assertTrue("确认前不许落盘（§10.2）：实到 $saved", saved.isEmpty())

        rule.onAllNodes(hasText(acknowledge))[0].performClick()
        pump(MID_EXIT_MS)
        assertEquals(
            "淡出途中那句话仍该在树上（旧写法 if (showIntro) 在这里读到 0，退场动画播不到）",
            1, nodesWith(body)
        )
        assertEquals(
            "「知道了」落的那一句一个字没动：当前正文、enabled=true、没换档",
            listOf(Saved("", true, IntentExpiry.ONE_DAY, false)), saved.toList()
        )

        pump(SETTLED_MS)
        assertEquals("退场播完才摘树", 0, nodesWith(body))
    }

    // ═══════════ ④ 源码尺：三处都不许再把树摘在 exit 之前 ═══════════

    /**
     * 语义树判的是「今天真的在淡出」，这一格判的是「别再改回去」：
     * ① 每一处 `LbModalSheet(` 的实参里都得有 `visible =`（少了它壳里就还是写死 true）；
     * ② 调用点待在**函数体顶层**（相对深度 1）。被 `if (show) {` 或 `target?.let {` 包住
     *    就变成深度 2——那正是「关的那一帧整棵树被摘掉」的那个形状。
     */
    @Test
    fun `every call site drives the sheet through visible at the top level`() {
        val sites = listOf(
            Site("panel/settings/SettingsIntentEntry.kt", "internal fun SettingsIntentIntroSheet(", 1),
            Site("panel/reply/MemoryCorrectionFlow.kt", "fun MemoryCorrectionFlowHost(", 2),
            Site("panel/reply/CorrectionCenter.kt", "fun CorrectionCenterHost(", 1)
        )
        sites.forEach { site ->
            val slices = sheetArgSlices(maskedSource(site.rel))
            assertEquals("${site.rel} 里浮层的颗数：$slices", site.count, slices.size)
            val unwired = slices.filter { "visible =" !in it }
            assertTrue(
                "${site.rel} 里有浮层没接 visible（壳里就还是写死 true，退场动画没机会播）：" + unwired,
                unwired.isEmpty()
            )
            val depths = sheetCallDepths(site.rel, site.header)
            assertEquals("${site.rel} 的调用点颗数与实参颗数对不上", site.count, depths.size)
            val nested = depths.filter { it != 1 }
            assertTrue(
                "${site.rel} 里浮层又被条件包进块里挂载了（深度 $depths，顶层应为 1）：" +
                    "退场动画播不到",
                nested.isEmpty()
            )
        }

        // 纠正中心旧写法是体首一句早退——那种形状下调用点深度仍是 1，上面那把尺看不见它，所以单点钉
        val center = bodyOf("panel/reply/CorrectionCenter.kt", "fun CorrectionCenterHost(")
        assertTrue("纠正中心又写回了体首早退（close 那一帧整棵树被摘掉）：" + center, "return" !in center)
    }

    /** 一个调用点：生产文件（相对 app/src/main/java/com/lovebrain/app/ui/）、函数头锚点、应有的浮层颗数 */
    private data class Site(val rel: String, val header: String, val count: Int)

    // ═══════════ 读源码的那几颗小工具 ═══════════

    /** 生产文件按 `app/` 相对路径取（与 `IntentEditorSheetMergeTest` 同一口径：两种工作目录都试） */
    private fun sourceFile(rel: String): File {
        val file = File("src/main/java/com/lovebrain/app/ui/$rel").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/$rel")
        assertTrue("$file 不在了——这一格会恒绿", file.isFile)
        return file
    }

    private fun maskedSource(rel: String): String =
        SourceScan.maskComments(sourceFile(rel).readText(Charsets.UTF_8))

    /** 每一处 `LbModalSheet(` 圆括号里的实参（按配对取；注释已被掩掉，所以数不到 KDoc 里那句举例） */
    private fun sheetArgSlices(masked: String): List<String> {
        val needle = "LbModalSheet("
        val out = mutableListOf<String>()
        var i = 0
        while (true) {
            val at = masked.indexOf(needle, i)
            if (at < 0) return out
            val open = at + needle.length - 1
            val close = SourceScan.closeIndexOf(masked, open)
            out += masked.substring(open + 1, close - 1)
            i = close
        }
    }

    /** 函数头之后到下一个 `@Composable` 之前——用来判「这一棵函数的体里长什么样」 */
    private fun bodyOf(rel: String, header: String): String {
        val code = maskedSource(rel)
        assertTrue("$rel 里找不到 $header——锚点漂了，这一格会恒绿", header in code)
        return code.substringAfter(header).substringBefore("\n@Composable")
    }

    /**
     * 该函数体里每一处 `LbModalSheet(` 当时的**括号深度**：函数体自己那一层记 1，
     * 再被 `if (…) {` 或 `?.let {` 包一层就是 2 ⇒ 那一处又是条件挂载。
     */
    private fun sheetCallDepths(rel: String, header: String): List<Int> {
        val body = bodyOf(rel, header)
        val open = body.indexOf('{')
        assertTrue("$rel 的 $header 找不到函数体左括号", open >= 0)
        val needle = "LbModalSheet("
        val depths = mutableListOf<Int>()
        var depth = 0
        var i = open
        while (i < body.length) {
            when {
                body[i] == '{' -> depth++
                body[i] == '}' -> depth--
                body.startsWith(needle, i) -> {
                    depths += depth
                    i += needle.length - 1
                }
            }
            i++
        }
        return depths
    }
}
