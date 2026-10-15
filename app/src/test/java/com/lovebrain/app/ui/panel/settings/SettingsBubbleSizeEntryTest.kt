package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页「图标大小」那一行的读数合同。这一族钉五件互相独立的事：
 *
 * 1. **点完立刻有选中态**（这一族存在的全部理由）。宿主交下来的 `sizeDp` 读的是盘上那一格，
 *    而那条读数是**普通取值**、没有可观察通道：真实面板里点完芯片之后那个参数一个字都不会变，
 *    于是"窗口真的改了、屏上选中态没改"。下面第一格就把宿主钉成那个形状（参数恒定不变），
 *    只有这一行自己会重绘时才会绿。旧形状（选中态只跟参数比）红在
 *    "「大」的选中态读数应当是 true"那一句上，而不是红在尺寸或文案上。
 *    ⚠ 这一颗是10-11 那一版修好的**真实缺陷**，这一版换的是画法、不是这条链——它原样保留、原样红得回来。
 * 2. **高亮那一档 = 写进去那一档**。写口那条链先过 [BubbleSizeTier.snapDp] 再落盘
 *    （`viewmodel/LoveBrainViewModel.kt` 的 `setBubbleSizeDp` → `data/SecurePrefs.kt`），
 *    所以这一格按同一把尺画的档，就是盘上会给出的那个读数。
 * 3. **局部那颗不是第二份真值**：宿主把盘上的读数交下来时（参数变化），亮的那一档必须让位给它。
 * 4. **一条浅底轨道、几格透明文字、只有选中那格涂蓝**（这一版的靶心）。原型 `.segments` 是
 *    "轨道画一条底、格子不画底"，10-11 那一版错画成"三颗各自带描边的独立盒子"（判"根本不一样"就红在这）。
 *    语义树量不到底色，所以判**结构**：整行只许有一颗轨道容器（`settings-pill-size`），
 *    三格都落在这颗轨道之内——三颗独立盒子那种画法根本没有那颗共享轨道，节点数 0 ⇒ 当场红。
 * 5. **同一行、右对齐、竖向命中不缩、显示不铺满、三档悬浮窗宽下不溢出**。
 *    格子高度轴仍到全站下限（48，**缩显示不缩命中**）；宽度轴走紧凑档（每格 ≥ [SettingsRowDimens.PILL_CELL_MIN_WIDTH_DP]、
 *    不铺满整行、标签留得出位置），轨道贴右缘、在 260 / 300 / 350 三档都摆得下、不压住标签。
 *
 * 文案锚点一律 `getString` 现取（这台机器的环境解析出来是英文，写死中文会假红）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsBubbleSizeEntryTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private val rowLabel: String get() = ctx.getString(R.string.settings_bubble_size_row_label)

    /** 三档标签按 [BubbleSizeTier.ALLOWED_DP] 的顺序现取：小 / 标准 / 大 */
    private val tierLabels: List<String>
        get() = listOf(
            R.string.settings_bubble_size_small,
            R.string.settings_bubble_size_standard,
            R.string.settings_bubble_size_large
        ).map { ctx.getString(it) }

    /** 盘上那一格的替身：写口先过同一把尺再落，读回来就是它 */
    private var disk = BubbleSizeTier.DEFAULT_DP

    private fun chip(label: String): SemanticsMatcher = hasTestTag(LbTags.CHIP) and hasText(label)

    private fun tabs(): List<SemanticsProbe.Target> =
        probe.laid(probe.actionableTargets(rule, "图标大小那一行")).filter { it.role == "Tab" }

    /** 此刻报"选中"的档数——互斥单选只许剩一颗 */
    private fun selectedCount(): Int = tabs().count { it.selected == true }

    /**
     * 轨道/行根那两颗**只有 testTag、没有点击**的容器：按未合并树读（本仓既有先例——纯 tag 节点
     * 不进合并语义树）。量具要找的正是这两颗，各恰一颗。
     */
    private fun tagTarget(tag: String): SemanticsProbe.Target {
        val nodes = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("tag=$tag 的节点必须恰有一颗，实到 ${nodes.size}", 1, nodes.size)
        return probe.of(nodes.single())
    }

    /**
     * 某一档的选中态。规范位是语义树里的 `Selected`（颜色不是）；
     * 读不到那一槽也算红——去掉互斥单选那一档就会退化成"没人报状态"。
     */
    private fun assertTier(label: String, wantSelected: Boolean) {
        val nodes = rule.onAllNodes(chip(label)).fetchSemanticsNodes()
        assertEquals("「$label」那一档该恰有一颗芯片，实到 ${nodes.size}", 1, nodes.size)
        assertEquals(
            "「$label」的选中态（同一时刻只许一颗亮）",
            wantSelected,
            nodes.single().config.getOrNull(SemanticsProperties.Selected)
        )
    }

    private fun mount(sizeDp: Int = disk, onSizeChange: (Int) -> Unit, widthDp: Int = ROW_WIDTH_DP) {
        rule.setContent {
            UiMatrix(widthDp).RenderIn(LocalDensity.current.density) {
                SettingsBubbleSizeEntry(sizeDp = sizeDp, onSizeChange = onSizeChange)
            }
        }
        rule.waitForIdle()
    }

    /**
     * 第 1 + 2 件：**宿主那份读数全程不换**（真实面板就是这个形状），点一档必须立刻亮这一档，
     * 而且亮的那一档就是写进去那一档。10-11 那一版"点了没反馈"那颗真实缺陷就钉在这一格。
     */
    @Test
    fun `tapping a tier highlights it at once even when the host never re-supplies the value`() {
        disk = BubbleSizeTier.STANDARD_DP
        val writes = mutableListOf<Int>()
        mount(sizeDp = disk, onSizeChange = { dp ->
            writes += dp
            disk = BubbleSizeTier.snapDp(dp)
        })

        // 起点：标准档亮，其余两颗灭
        assertTier(tierLabels[1], true)
        assertTier(tierLabels[2], false)

        // 点「大」：窗口那一侧由宿主与服务负责，这一格只管把选中态当场移过来
        rule.onAllNodes(chip(tierLabels[2]))[0].performClick()
        rule.waitForIdle()
        assertTier(tierLabels[2], true)
        assertTier(tierLabels[1], false)
        assertEquals("点一次只交一次账", listOf(BubbleSizeTier.LARGE_DP), writes.toList())
        assertEquals("高亮那一档必须就是写进去那一档", BubbleSizeTier.LARGE_DP, disk)
        assertEquals("同一时刻只许一颗亮", 1, selectedCount())

        // 再点「小」：上一档必须灭掉，账再交一次
        rule.onAllNodes(chip(tierLabels[0]))[0].performClick()
        rule.waitForIdle()
        assertTier(tierLabels[0], true)
        assertTier(tierLabels[2], false)
        assertEquals("两档之间来回点，落盘读数跟着走", BubbleSizeTier.SMALL_DP, disk)
        assertEquals(listOf(BubbleSizeTier.LARGE_DP, BubbleSizeTier.SMALL_DP), writes.toList())
        assertEquals("换了档也仍然只许一颗亮", 1, selectedCount())
    }

    /**
     * 第 3 件：**局部那颗只买"及时重绘"，盘上的读数一到就接管**。
     * 先点一次（写盘 + 读回交回参数），再在没有点击的情况下换一个读回值——亮的那一档必须跟过去。
     */
    @Test
    fun `the value the host reads back from disk takes over the local highlight`() {
        val param: MutableState<Int> = mutableStateOf(BubbleSizeTier.STANDARD_DP)
        val writes = mutableListOf<Int>()
        rule.setContent {
            UiMatrix(ROW_WIDTH_DP).RenderIn(LocalDensity.current.density) {
                SettingsBubbleSizeEntry(
                    sizeDp = param.value,
                    onSizeChange = { dp ->
                        writes += dp
                        param.value = BubbleSizeTier.snapDp(dp)  // 写盘之后读回，再交回参数
                    }
                )
            }
        }
        rule.waitForIdle()

        rule.onAllNodes(chip(tierLabels[0]))[0].performClick()
        rule.waitForIdle()
        assertEquals("读回的落盘值就是刚点那一档", BubbleSizeTier.SMALL_DP, param.value)
        assertTier(tierLabels[0], true)

        // 没有点击、只有宿主换了读数（等价于收起再展开设置页时从盘上重取一次）
        rule.runOnIdle { param.value = BubbleSizeTier.LARGE_DP }
        rule.waitForIdle()
        assertTier(tierLabels[2], true)
        assertTier(tierLabels[0], false)
        assertEquals("读数换了不等于用户又点了一次", listOf(BubbleSizeTier.SMALL_DP), writes.toList())
    }

    /**
     * 第 4 件：**一条浅底轨道、三格透明、只一颗轨道容器**（不是三颗各自带描边的盒子）。
     *
     * 语义树读不出底色，于是判结构：整行只许有一颗 `settings-pill-size` 轨道，三格都落在它之内。
     * 反例（10-11 那一版那颗真实坏形状）：三颗各画各的独立描边盒 = 没有那颗共享轨道 ⇒
     * `tagTarget(SETTINGS_BUBBLE_SIZE_PILL_TAG)` 第一句 `assertEquals(1, nodes.size)` 就在 0 上红。
     */
    @Test
    fun `the three tiers sit inside one shared track instead of three separate boxes`() {
        disk = BubbleSizeTier.STANDARD_DP
        mount(sizeDp = disk, onSizeChange = {})

        // 行根与轨道各恰一颗（量具靠这两颗找行、找轨道）
        tagTarget(SETTINGS_BUBBLE_SIZE_ROW_TAG)
        val track = tagTarget(SETTINGS_BUBBLE_SIZE_PILL_TAG)

        val chips = tabs()
        assertEquals("三档恰好三颗互斥单选：" + chips.joinToString { it.describe() }, 3, chips.size)
        // 每一格都在这一条轨道之内——共享一条底、不是各自一盒。
        chips.forEach {
            assertTrue(
                "这一格掉到了轨道之外（各自一盒的坏形状）：" + it.describe() + " / 轨道 " + track.describe(),
                it.leftDp >= track.leftDp - HIT_TOLERANCE_DP && it.rightEdge <= track.rightEdge + HIT_TOLERANCE_DP
            )
            assertTrue(
                "这一格的高度中心不在轨道里（没和轨道同排）：" + it.describe(),
                kotlin.math.abs(it.centerY - track.centerY) <= HIT_TOLERANCE_DP
            )
        }
        // 选中档 = 盘上读数：此刻 disk = 标准，亮的那一颗就应当是「标准」这一档。
        assertTier(tierLabels[1], true)
        assertEquals("同一时刻只许一颗亮", 1, selectedCount())
        val selectedLabel = tabs().first { it.selected == true }.label
        val diskTierIndex = BubbleSizeTier.ALLOWED_DP.indexOf(BubbleSizeTier.snapDp(disk))
        assertEquals(
            "亮的那一档必须就是盘上读回来的那一档",
            tierLabels[diskTierIndex], selectedLabel
        )
    }

    /**
     * 第 5 件：**同一行、竖向命中不缩、显示不铺满、轨道贴右缘、三档悬浮窗宽都不溢出**。
     *
     * 旧形状（三颗各 `weight(1f)` 占满整行）与紧凑坏形状（缩显示顺手缩了命中）都在这里红：
     * · 每格高度轴仍到全站下限 48（**缩的是显示，不是命中**）；
     * · 每格宽度轴走紧凑档：≥ [SettingsRowDimens.PILL_CELL_MIN_WIDTH_DP]、不铺满整行（轨道留出标签的位置）；
     * · 轨道右缘就是行右缘（这一页要的"标签在左、轨道右对齐"）；
     * · 标签与三格垂直中心一致（同一行，不是上下两行）；
     * · 在 260 / 300 / 350 三档都不溢出、不换行、不压住标签。
     */
    @Test
    fun `the track hugs the right edge and fits every overlay width without crushing the label`() {
        val width = mutableIntStateOf(ROW_WIDTH_DP)
        val param: MutableState<Int> = mutableStateOf(BubbleSizeTier.STANDARD_DP)
        rule.setContent {
            UiMatrix(width.intValue).RenderIn(LocalDensity.current.density) {
                SettingsBubbleSizeEntry(sizeDp = param.value, onSizeChange = {})
            }
        }
        rule.waitForIdle()

        OVERLAY_WIDTHS_DP.forEach { w ->
            rule.runOnIdle { width.intValue = w }
            rule.waitForIdle()

            val chips = tabs()
            assertEquals("宽 $w 下三档仍恰三颗", 3, chips.size)
            val track = tagTarget(SETTINGS_BUBBLE_SIZE_PILL_TAG)

            chips.forEach {
                assertTrue(
                    "宽 $w：高度轴命中不到 ${TouchTier.SITE_FLOOR.toInt()}dp（只缩显示不许缩命中）：" + it.describe(),
                    it.heightDp >= TouchTier.SITE_FLOOR - HIT_TOLERANCE_DP
                )
                assertTrue(
                    "宽 $w：单字档缩成了一枚图标（不到最小格宽）：" + it.describe(),
                    it.widthDp >= SettingsRowDimens.PILL_CELL_MIN_WIDTH_DP - HIT_TOLERANCE_DP
                )
            }

            // 轨道贴右缘、绝不溢出这一档宽度；而且给标签留得出位置（没把整行铺满）。
            assertEquals("宽 $w：轨道没贴到行右缘", w.toFloat(), track.rightEdge, HIT_TOLERANCE_DP)
            assertTrue("宽 $w：轨道溢出/铺满了整行，挤不掉标签：" + track.describe(),
                track.rightEdge <= w + HIT_TOLERANCE_DP)
            assertTrue("宽 $w：轨道铺满整行、没给标签留位置：" + track.describe(),
                track.widthDp <= w - MIN_LABEL_ROOM_DP)

            // 同一行：标签与轨道垂直中心一致（回退成"标题在上、选择器在下"的两行会红在这里）。
            val labels = rule.onAllNodes(hasText(rowLabel)).fetchSemanticsNodes()
            assertEquals("宽 $w：行标签「$rowLabel」必须恰有一颗，实到 ${labels.size}", 1, labels.size)
            val label = probe.of(labels.single())
            assertTrue(
                "宽 $w：标签与轨道不在同一行（上下两行那种旧形状）：" + label.describe() + " / " + track.describe(),
                kotlin.math.abs(label.centerY - track.centerY) <= HIT_TOLERANCE_DP
            )
        }

        // 旧的整行标题（"悬浮图标大小"）已被这一行的短标签替代：屏上不许再出现那一串。
        rule.onAllNodes(hasText(ctx.getString(R.string.settings_bubble_size_label))).assertCountEquals(0)
    }

    private val SemanticsProbe.Target.centerY: Float get() = topDp + heightDp / 2f
    private val SemanticsProbe.Target.rightEdge: Float get() = leftDp + widthDp

    companion object {
        /** 悬浮窗设置页那一族最窄的一档：行必须在这一档还摆得下三颗 */
        private const val ROW_WIDTH_DP = 260

        /** 三档悬浮窗宽度：轨道在每一档都不许溢出、不许压住标签 */
        private val OVERLAY_WIDTHS_DP = listOf(260, 300, 350)

        /** 轨道右侧之外必须留给标签的最小宽度（dp）：轨道若铺满整行，这一档就被挤成 0 */
        private const val MIN_LABEL_ROOM_DP = 40f

        /** 语义树边界的读数容差（dp）：描边那 1dp 与四舍五入都落在这一档里 */
        private const val HIT_TOLERANCE_DP = 1.5f
    }
}
