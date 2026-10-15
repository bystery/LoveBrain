package com.lovebrain.app.ui.visual

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceBase
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.KnowledgeBase
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import com.lovebrain.app.ui.panel.settings.SETTINGS_KB_OPTION_TAG
import com.lovebrain.app.ui.panel.settings.SETTINGS_ROW_KB_TAG
import com.lovebrain.app.ui.panel.settings.LoveBrainSettingsContent
import com.lovebrain.app.ui.theme.LoveBrainTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 悬浮窗设置页的**屏幕级**截图基线。
 *
 * 组件级基线（`LbSettingRow` / `LbChip` / `LbFieldInput`…）各钉自己那一颗，读不出"三组摆进同一屏"
 * 之后的样子：组名在框外还是框内、行是否共一条左缘、整页要不要滚才看得见最后一组。
 * 这些只有像素能支持，所以这一格存在。
 *
 * 摆的是**意图开着**那一档（输入行与那颗「保存」都在屏上）：这一档同时覆盖两组容器、两排分段选择器、
 * 滑杆、输入行与开关，是整页最满的一屏。关掉那一档由语义树那族钉——少一行不值得多要一张图。
 *
 * ⚠ 落盘前必须先断状态再拍：先 `setContent` → `waitForIdle()` → 判"三组都在、整页画进窗口" →
 * 才 `onRoot().captureRoboImage()`，让断言与像素出自同一棵已落定的树（这条纪律是两张录错状态的
 * 基线换来的教训）。**基线本身不由这一格替换**：重录走 `scripts/record_visual_baseline.sh`
 * （只写 `build/`），人看过再单独提交。
 *
 * ⚠ 这一格**复现宿主那一层的外框**：生产里这一页由面板根部盖一层，`LoveBrainPanelScreen` 里
 * `if (surface.settingsOpen)` 那一段给了 `padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg)`
 * 与表面底色，而 `LoveBrainTheme` 长在悬浮窗 `ComposeView` 的根上（`OverlayPanelWindow` 那一处）。
 * 这两颗都不补时，本机第一次实量到的是"组名贴到窗口左缘被切掉半字 + 滑杆回到 Material 默认的紫色"——
 * 那是**量具瞎**，不是产品画错（生产两处都在）。宿主要 Service 与真实窗口状态，本机挂不起来，
 * 所以这里量的是"这棵子树在 24dp 内缩与主题色之下摆成什么样"，边界写在这一段。
 *
 * ## 2026-10-12 10-12 那一版加的夹具参数（三档窗口宽度）
 *
 * 原型 `.resizer` 那三颗按钮给的是 **260 / 300 / 350** 三档悬浮窗宽度，锚点合同那一族也点名要
 * "三种悬浮窗宽度"验收；10-11 那一版只录 360 那一档，读不出"窄档下分段轨道被挤成竖排 / 那颗「保存」被挤出
 * 右缘"这一族坏法。于是下面按档各加一格，每格都**先判状态再落图**（同一份纪律）。
 *
 * ⚠ **这一笔不替换任何在册基线**：本类在 `app/src/test/roborazzi/` 里**一张 PNG 都没有**
 * （`ls` 实扫：45 张在册件里没有 `SettingsPageVisualBaselineTest.*`），也就是说过审的那张整页图
 * 从来还没提交过；这一版形状又改了（三张组框、同一档行标签、轨道分段、细轨滑杆），
 * 所以四张图都**等人过眼后**走 `bash scripts/record_visual_baseline.sh --tests '*SettingsPageVisualBaselineTest*'`
 * 录进 `build/`，人看过、单独一笔提交才算换基线。换基线是人工动作，归这一族的主人与用户，不在这一格。
 *
 * ⚠ 档位换的是**被测子树的布局约束**（`UiMatrix` 那颗盒子），Robolectric 的窗口固定在
 * `@Config(qualifiers)` 那一格（`w360dp-h1000dp`）——同一条边界写在 [UiMatrix] 文件头：
 * "260dp 这一格"量的是"这棵子树被要求在 260dp 内摆好"，不是"这台设备是 260dp"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPageVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /**
     * 在册那一格：360dp 档（**方法名不许改**——roborazzi 的落盘名就是 `<类>.<方法>.png`，
     * 改名等于把那张待过审的基线位置凭空挪走）。
     */
    @Test
    fun intentOnPageFitsTheFrameAndHasACommittedVisualBaseline() {
        mountWholePage(WIDTH_REGISTERED_DP)
        assertWholePageIsDrawnBeforeCapture(WIDTH_REGISTERED_DP)
        rule.onRoot().captureRoboImage()
    }

    // ═══════════ 原型 `.resizer` 那三档（这一版新形状的重录对象） ═══════════

    @Test
    fun intentOnPageAtTwoSixtyHasACommittedVisualBaseline() {
        mountWholePage(WIDTH_NARROW_DP)
        assertWholePageIsDrawnBeforeCapture(WIDTH_NARROW_DP)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun intentOnPageAtThreeHundredHasACommittedVisualBaseline() {
        mountWholePage(WIDTH_PROTOTYPE_DP)
        assertWholePageIsDrawnBeforeCapture(WIDTH_PROTOTYPE_DP)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun intentOnPageAtThreeFiftyHasACommittedVisualBaseline() {
        mountWholePage(WIDE_DP)
        assertWholePageIsDrawnBeforeCapture(WIDE_DP)
        rule.onRoot().captureRoboImage()
    }

    // ═══════════ 原型可交互的那几态：默认那一屏读不出来的部分 ═══════════
    //
    // 原型不是一张静态图：它的脚本会 `hidden` 掉输入行、展开知识库那一列、把「保存」涂绿、
    // 换尺寸档、把滑杆推到两端。只对默认那一屏，等于只对了一半——下面每一格都是
    // **先判状态、再落图**（与上面四档同一条纪律），并且都在原型那一档宽度 300dp 上拍。

    /** 原型 `intent.onclick`：关掉那一档，输入行与那颗按钮**整段不在屏上**（不是灰着） */
    @Test
    fun intentOffHidesTheEditRowEntirely() {
        mountWholePage(WIDTH_PROTOTYPE_DP, intentEnabled = false)
        assertEquals(
            "关着那一档不该有那颗「保存」",
            0,
            rule.onAllNodesWithText(app.getString(R.string.intent_save)).fetchSemanticsNodes().size
        )
        assertEquals(
            "关着那一档整页只剩一颗可编辑节点都不许有",
            0,
            rule.onAllNodesWithText(app.getString(R.string.intent_row_placeholder))
                .fetchSemanticsNodes().size
        )
        rule.onRoot().captureRoboImage()
    }

    /** 原型 `kb.onclick` 展开 `.kb-options`：当前那本涂浅蓝底，箭头从 `›` 换成 `⌄` */
    @Test
    fun knowledgeBaseListExpandedHighlightsTheOneInUse() {
        mountWholePage(WIDTH_PROTOTYPE_DP)
        rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG))[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "展开后两本库都要在屏上",
            2,
            rule.onAllNodes(hasTestTag(SETTINGS_KB_OPTION_TAG)).fetchSemanticsNodes().size
        )
        rule.onRoot().captureRoboImage()
    }

    /** 原型滑杆推到最左：`min=0` = 完全不透明 */
    @Test
    fun opacityAtTheOpaqueEndReadsZeroPercent() {
        mountWholePage(WIDTH_PROTOTYPE_DP, opacityPercent = 100)
        assertEquals(
            "最不透明那一头屏上该念 0%",
            1,
            rule.onAllNodesWithText(app.getString(R.string.settings_opacity_percent, 0))
                .fetchSemanticsNodes().size
        )
        rule.onRoot().captureRoboImage()
    }

    /** 原型滑杆推到最右：这一族放开之后是 80% 透明（= 不透明度 20） */
    @Test
    fun opacityAtTheTransparentEndReadsEightyPercent() {
        mountWholePage(WIDTH_PROTOTYPE_DP, opacityPercent = 20)
        assertEquals(
            "最透那一头屏上该念 80%",
            1,
            rule.onAllNodesWithText(app.getString(R.string.settings_opacity_percent, 80))
                .fetchSemanticsNodes().size
        )
        rule.onRoot().captureRoboImage()
    }

    /** 原型 `.save.success`：正文真落盘那一拍，那颗按钮自己换成浅绿底 + 「✓ 已保存」 */
    @Test
    fun saveShowsTheSuccessTierRightAfterTheReceiptTicks() {
        // 自动推进会把那一拍 1.3 秒的虚拟时间直接跑完，图就落在收回之后——关掉它，只推几帧
        rule.mainClock.autoAdvance = false
        mountWholePage(WIDTH_PROTOTYPE_DP, saveReceipts = 1)
        repeat(8) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(
            "成功那一拍屏上那颗按钮该念「已保存」（对勾由档位加）",
            1,
            rule.onAllNodesWithText(app.getString(R.string.intent_saved_notice), substring = true)
                .fetchSemanticsNodes().size
        )
        rule.onRoot().captureRoboImage()
        rule.mainClock.autoAdvance = true
    }

    /** 原型 `.segments button.selected`：换档时那块实心跟着走，轨道本身不动 */
    @Test
    fun largestBubbleTierMovesTheSelectedCell() {
        mountWholePage(WIDTH_PROTOTYPE_DP, bubbleSizeDp = 64, replyCardVertical = false)
        assertEquals(
            "最大那一档该是「大」被选中",
            1,
            rule.onAllNodesWithText(app.getString(R.string.settings_bubble_size_large))
                .fetchSemanticsNodes().size
        )
        rule.onRoot().captureRoboImage()
    }

    /**
     * 整页挂载：宿主那一层（主题 + `Spacing.xl` 内缩 + 表面底色）照生产复现，
     * 摆的是**意图开着**那一档（输入行与那颗「保存」都在屏上，整页最满的一屏）。
     */
    private fun mountWholePage(
        widthDp: Int,
        intentEnabled: Boolean = true,
        opacityPercent: Int = 80,
        bubbleSizeDp: Int = 56,
        replyCardVertical: Boolean = true,
        saveReceipts: Int = 0
    ) {
        rule.setContent {
            UiMatrix(widthDp, heightDp = FRAME_HEIGHT_DP).RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceBase)
                            .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg)
                    ) {
                        LoveBrainSettingsContent(
                            onBack = {},
                            onCollapse = {},
                            assistantOn = true,
                            onAssistantEnable = {},
                            onAssistantClose = {},
                            opacityPercent = opacityPercent,
                            onOpacityPreview = {},
                            onOpacityCommit = {},
                            bubbleSizeDp = bubbleSizeDp,
                            onBubbleSizeChange = {},
                            replyCardVertical = replyCardVertical,
                            onReplyCardVerticalChange = {},
                            intentSaveReceipts = saveReceipts,
                            intentEnabled = intentEnabled,
                            intentText = "这周把见面的事定下来",
                            onIntentChange = { _, _ -> },
                            knowledgeBases = listOf(kb("小明"), kb("工作记录")),
                            activeKbName = "小明"
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /**
     * 录制前的状态断言，三件事：
     * 1. **locale 钉死 zh-CN**——`@Config(qualifiers)` 里不写 locale 段就等于不固定，
     *    换台机器换语言就换图（屏上会变成"资源英文 + 内联中文"混排）。
     * 2. **该在的都在**：两处在框外的组名与那颗「保存」，各恰一颗。
     *    文案锚点一律现读资源（英文 JVM 环境里写中文字面量会假红）。
     * 3. **整页画进窗口**：最低那颗可交互节点的底边不许掉到折线以下。
     *    `verticalScroll` 会把滚不到的部分照样组合进树，所以"在树上"不等于"看得见"，必须量几何。
     *    曾经那张"第四段只剩标题"的基线就是这么混进过落盘件，这条当场会红。
     *    窗口宽度是参数：窄档挤不挤得下同一屏，正是这一句在三档上各判一次的理由。
     */
    private fun assertWholePageIsDrawnBeforeCapture(widthDp: Int) {
        assertEquals(
            "截图基线要求 zh-CN",
            "zh-CN",
            app.resources.configuration.locales.toLanguageTags()
        )

        for (key in listOf(R.string.settings_group_display, R.string.settings_group_content)) {
            assertEquals(
                "组名「${app.getString(key)}」必须恰有一颗在树上",
                1,
                rule.onAllNodesWithText(app.getString(key)).fetchSemanticsNodes().size
            )
        }
        assertEquals(
            "开着意图那一档，那颗「保存」必须在树上",
            1,
            rule.onAllNodesWithText(app.getString(R.string.intent_save)).fetchSemanticsNodes().size
        )

        val targets = probe.actionableTargets(rule, "${widthDp}dp 档整窗设置页")
        val laid = probe.laid(targets)
        val dropped = probe.unlaid(targets)
        assertTrue("这一屏至少要认得到可交互节点，实到 ${laid.size}", laid.isNotEmpty())
        val lowest = laid.maxByOrNull { it.topDp + it.heightDp }!!
        assertTrue(
            "整页没画进窗口：最低那颗 ${lowest.describe()} 底边 ${lowest.topDp + lowest.heightDp}dp > 窗口 ${FRAME_HEIGHT_DP}dp" +
                "（被排除的没摆出来的节点 ${dropped.size} 颗：" +
                dropped.joinToString("; ") { it.describe() } +
                "）——这一格的名字承诺的是「整屏」，画到一半不许落盘",
            lowest.topDp + lowest.heightDp <= FRAME_HEIGHT_DP.toFloat()
        )
    }

    private fun kb(name: String) = KnowledgeBase(
        name = name,
        displayName = name,
        updatedAt = "2026-10-11 00:00",
        stage = "",
        turnCount = 0,
        topicCount = 0,
        active = name == "小明"
    )

    private companion object {
        /** 窗口那一档高度（`@Config` 里也是这一格；`verticalScroll` 之下看得见与否按它判） */
        const val FRAME_HEIGHT_DP = 1000

        /** 在册那一档：10-11 那一版定下的 360dp（方法名与档一起原地留着，动它就是凭空挪基线位置） */
        const val WIDTH_REGISTERED_DP = 360

        /** 原型 `.resizer` 那三档：260 / 300 / 350 */
        const val WIDTH_NARROW_DP = 260
        const val WIDTH_PROTOTYPE_DP = 300
        const val WIDE_DP = 350
    }
}
