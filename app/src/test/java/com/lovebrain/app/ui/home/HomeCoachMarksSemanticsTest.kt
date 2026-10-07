package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.viewmodel.GuideCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页遮罩引导的几何与出口（用户原话第 14 条；规格按设计基线 v1.1 §6.7）。
 *
 * ⚠ 本仓库三条仪器事实决定了这里怎么判：
 * 1. `maxLines + TextOverflow.Ellipsis` 时语义树仍报**完整原文** ⇒ 文本断言抓不到"字被裁/板太宽"，
 *    所以这一组只判**几何**（`boundsInRoot`）与**存在性**；文案只留"名字来自资源"那一格
 *    （它比的是 `app.getString` 现读出来的串，硬编码中文在 en 环境必红）。
 * 2. `stringResource` 不许在 `semantics {}` 里调 ⇒ 罩子那一层的字全由宿主取好再交进来，
 *    本文件因此用 `app.getString(...)` 造 [CoachStepCopy]，与生产同一把尺。
 * 3. **一格用例一次 `setContent`**（`createComposeRule` 第二次调直接抛
 *    `Cannot call setContent twice per test!`）⇒ 档位矩阵要跨两档读的那几格一律对半拆成独立
 *    `@Test`、共用一个私有断言函数，**不砍档、不松断言**。
 *
 * 锚点用首页那两颗**替身**摆：tag 直接取 [LbHomeTags] 那一族（与 `HomeScreen` 同名同序），
 * 替身各自挂 [coachAnchor] 把位置写进罩子读的那本账（`HomeScreen` 四入口那四行真锚点已由主线程
 * 接上，本组仍用替身把档位摆可控）。这一组证明的是"账本通了之后指得对、没通时引导也不消失"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeCoachMarksSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /** 每格一本新账（JUnit 每格一个新实例）：账不从上一格漏过来 */
    private val registry = CoachAnchorRegistry()

    /**
     * 宿主那颗**页面闸**的替身（§9.1）。
     *
     * 生产里它由 `SetupActivity` 从 `SetupRoot` 的只读出口 `onDestinationChanged` 喂进来；
     * 测试没有真导航，就让它直接改这一棵树的形状：**闸一关，首页那一整棵（含四入口替身）一起离场**
     * ——这正是 `AnimatedContent` 换 destination 时的形状，锚点因此走 `isAttached=false` 那一条销账路径。
     */
    private val onHome = androidx.compose.runtime.mutableStateOf(true)

    private val plateName: String get() = app.getString(R.string.onboarding_title)

    private fun stepCopy(): CoachStepCopy = CoachStepCopy(
        plateDescription = plateName,
        title = app.getString(R.string.provider_page_title),
        body = app.getString(R.string.error_provider_missing),
        actionLabel = app.getString(R.string.provider_open_settings),
        deferLabel = app.getString(R.string.onboarding_skip),
        stopLabel = app.getString(R.string.a11y_close_onboarding)
    )

    private var opened = 0
    private var deferred = 0
    private var stopped = 0

    /**
     * 覆盖层与首页替身同层摆，结构照生产：`Box { 首页; HomeCoachMarks(...) }`。
     *
     * @param anchored 关掉替身的 [coachAnchor]，模拟 `HomeScreen` 那一行还没接上时罩子的退路
     * @param anchorRowBottom 把那一排入口沉到屏幕下沿——用来验"下方放不下就整摞翻到上方"那一支
     * @param homeInitially 开局首页在不在场（§9.1 的页面闸）；测试中途可用 [onHome] 翻转
     */
    private fun mount(
        cursor: GuideCursor,
        copy: CoachStepCopy? = stepCopy(),
        anchored: Boolean = true,
        anchorRowBottom: Boolean = false,
        homeInitially: Boolean = true
    ) {
        opened = 0; deferred = 0; stopped = 0
        onHome.value = homeInitially
        rule.setContent {
            val homeNow = onHome.value
            UiMatrix(360, 640).RenderIn(LocalDensity.current.density) {
                CompositionLocalProvider(LocalCoachAnchorRegistry provides registry) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 首页那一整棵（含四入口替身）随页面闸进出——生产里这是 `SetupRoot`
                        // 换 destination 的那一次装配，替身留在树上就等于骗过了销账那条路
                        if (homeNow) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (anchorRowBottom) Spacer(Modifier.weight(1f))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    AnchorEntry(LbHomeTags.ENTRY_CAPTURE, anchored)
                                    AnchorEntry(LbHomeTags.ENTRY_PROVIDER, anchored)
                                }
                                if (!anchorRowBottom) Spacer(Modifier.weight(1f))
                            }
                        } else {
                            // 子页在场：另一屏的内容（没有首页那四颗锚点）
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxWidth().height(80.dp))
                            }
                        }
                        HomeCoachMarks(
                            cursor = cursor,
                            copy = copy,
                            onHome = homeNow,
                            onOpenTarget = { opened++ },
                            onDefer = { deferred++ },
                            onStopGuiding = { stopped++ },
                            registry = registry
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 一颗入口替身：与 HomeScreen 同一族 tag，顺带把位置写进罩子那本账 */
    @Composable
    private fun RowScope.AnchorEntry(key: String, anchored: Boolean) {
        val base = Modifier.weight(1f).height(80.dp)
        Box(
            modifier = if (anchored) {
                base.testTag(key).coachAnchor(key)
            } else {
                base.testTag(key)
            }
        )
    }

    // ─────────────────────────── 读数仪器 ───────────────────────────

    private fun nodes(tag: String): SemanticsNodeInteractionCollection =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true)

    private fun count(tag: String): Int = nodes(tag).fetchSemanticsNodes().size

    private fun rectOf(tag: String): Rect = nodes(tag).fetchSemanticsNodes().first().boundsInRoot

    /** px → dp：断言里说的数一律是 dp（这一格 density=1，换算仍照写） */
    private fun Float.dpNumber(): Float = this / density

    // ─────────────────────────── 锚点与几何 ───────────────────────────

    /**
     * PROVIDER 那一格的箭头指在供应商入口那颗上，提示板在目标外侧、宽 ≤260dp。
     *
     * 回退成什么就红：
     * ① 有人在 UI 里另写一套"哪步没做"的判断 → 游标与锚点对不上，中心 X 当场红；
     * ② 有人把箭头写死在屏幕中间、或塞进提示板里 → 与目标中心对不上；
     * ③ 三档映射抄反（PROVIDER↔CAPTURE）→ 抓到的是捕获那颗的中心；
     * ④ 提示板盖住目标、或宽过 260dp → 后两条断言红。
     */
    @Test
    fun `the provider step points at the provider entry`() {
        mount(GuideCursor.PROVIDER)
        val target = rectOf(LbHomeTags.ENTRY_PROVIDER)
        val pointer = rectOf(LbCoachTags.POINTER)
        val plate = rectOf(LbCoachTags.PLATE)

        assertEquals(
            "箭头该正对被指那颗的水平中心（容差 6dp），实到 pointer=" +
                pointer.center.x.dpNumber() + "dp target=" + target.center.x.dpNumber() + "dp",
            target.center.x,
            pointer.center.x,
            6f * density
        )
        assertTrue(
            "箭头必须落在目标外侧（不许压在目标上）：pointer=$pointer target=$target",
            pointer.top >= target.bottom - 1f || pointer.bottom <= target.top + 1f
        )
        assertTrue(
            "提示板不许压住被指的那一格（用户得看得见自己点哪）：plate=$plate target=$target",
            plate.top >= target.bottom - 1f || plate.bottom <= target.top + 1f
        )
        assertTrue(
            "提示板可见宽要 ≤260dp（§6.7 定版），实到 " + plate.width.dpNumber() + "dp",
            plate.width.dpNumber() <= 260f + 0.5f
        )
        // 反空跑：遮罩也得在场，否则"指着"只是画了张图
        assertEquals("有锚点时该铺遮罩", 1, count(LbCoachTags.SCRIM))
    }

    /**
     * ACCESSIBILITY 那一格指「消息捕获」那颗，而且**不许指成供应商**。
     * （与下一格同判据、拆开成两颗用例——见 [assertPointsAtCaptureEntry] 顶上那条仪器事实。）
     *
     * 两格同锚是产品事实：无障碍授权与捕获开关同住 `CaptureAppsScreen`，首页没有第二颗无障碍入口。
     * 回退成什么就红：有人把 ACCESSIBILITY 映射到 ENTRY_PROVIDER（那颗早就点亮了，等于骗用户
     * 再点一遍）——第二条断言按"中心差一整列宽"抓它，容差给到 40dp 仍不误红。
     */
    @Test
    fun `the accessibility step points at the capture entry, not the provider one`() {
        assertPointsAtCaptureEntry(GuideCursor.ACCESSIBILITY)
    }

    /** CAPTURE 那一格：与上一格同一把尺、同一颗锚点键，只是游标换成捕获 */
    @Test
    fun `the capture step points at the capture entry, not the provider one`() {
        assertPointsAtCaptureEntry(GuideCursor.CAPTURE)
    }

    /**
     * 一格游标一次挂载的两条读数：箭头中心 == 捕获入口中心（±6dp）、离供应商中心 >40dp。
     *
     * ⚠ 为什么它是"两颗用例"而不是一格里的 `forEach`：`rule.setContent` 每格用例只许调一次
     * （这族罩子测原本就是把 ACCESSIBILITY/CAPTURE 两档并在同一格里面重挂，报的就是
     * `Cannot call setContent twice per test!`）。档位矩阵一档都不许砍，所以这里对半拆：
     * 判据逐字保留在两格里，一条不多一条不少。
     */
    private fun assertPointsAtCaptureEntry(cursor: GuideCursor) {
        mount(cursor)
        val capture = rectOf(LbHomeTags.ENTRY_CAPTURE)
        val provider = rectOf(LbHomeTags.ENTRY_PROVIDER)
        val pointer = rectOf(LbCoachTags.POINTER)
        assertEquals(
            "$cursor 该指消息捕获那一颗（容差 6dp）",
            capture.center.x,
            pointer.center.x,
            6f * density
        )
        assertTrue(
            "$cursor 的箭头离供应商那颗太近（" + pointer.center.x.dpNumber() + "dp vs " +
                provider.center.x.dpNumber() + "dp）——锚点映射抄反了",
            kotlin.math.abs(pointer.center.x - provider.center.x) > 40f * density
        )
    }

    /**
     * 目标贴近屏幕下沿时那一摞整块翻到目标**上方**，箭头随之朝下、板子仍在屏内。
     *
     * 回退成什么就红：只写"永远往下放"——提示板掉出屏幕，引导看不见（等于又"没了"）。
     */
    @Test
    fun `a target near the bottom edge gets the plate above it instead of off screen`() {
        mount(GuideCursor.PROVIDER, anchorRowBottom = true)
        val target = rectOf(LbHomeTags.ENTRY_PROVIDER)
        val plate = rectOf(LbCoachTags.PLATE)
        val pointer = rectOf(LbCoachTags.POINTER)
        assertTrue(
            "放不下时提示板该在目标上方：plate.bottom=" + plate.bottom.dpNumber() +
                "dp target.top=" + target.top.dpNumber() + "dp",
            plate.bottom <= target.top + 1f
        )
        assertTrue("箭头也该翻到上方：pointer=$pointer target=$target", pointer.bottom <= target.top + 1f)
        assertTrue(
            "翻上去之后板子仍要在屏内，实到 plate.top=" + plate.top.dpNumber() + "dp",
            plate.top.dpNumber() >= 0f
        )
    }

    // ─────────────────────────── 画不画 ───────────────────────────

    /**
     * `DEFERRED_TO_HINT` 一格都不画：缺项交回首页那一行黄字（那一行不归本席，这里只保证不叠二层）。
     *
     * 回退成什么就红：有人把"稍后"当"完成"实现（直接钉 DONE）→ 用户再也找不回引导；
     * 反过来把罩子常驻 → "挡路"回来。三颗 tag 各数一次，两头都看得见。
     */
    @Test
    fun `the deferred cursor paints nothing`() {
        mount(GuideCursor.DEFERRED_TO_HINT)
        assertEquals("DEFERRED 不该有遮罩", 0, count(LbCoachTags.SCRIM))
        assertEquals("DEFERRED 不该有箭头", 0, count(LbCoachTags.POINTER))
        assertEquals("DEFERRED 不该有提示板", 0, count(LbCoachTags.PLATE))
    }

    /**
     * `NONE`（介绍层还没看）不画：介绍页在场时首页本来也不在场。
     * `DONE` 那一格是同一条判据的另一半，拆成独立用例——`setContent` 每格用例只许调一次。
     */
    @Test
    fun `none paints nothing`() {
        assertPaintsNothing(GuideCursor.NONE)
    }

    /** `DONE`（事实齐了）同样一格都不画：引导不许在配齐之后还常驻 */
    @Test
    fun `done paints nothing`() {
        assertPaintsNothing(GuideCursor.DONE)
    }

    /** 两档共用的读数：遮罩 0 颗、提示板 0 颗（与 DEFERRED 那一格同口径，判据不松） */
    private fun assertPaintsNothing(cursor: GuideCursor) {
        mount(cursor)
        assertEquals("$cursor 不该画遮罩", 0, count(LbCoachTags.SCRIM))
        assertEquals("$cursor 不该画提示板", 0, count(LbCoachTags.PLATE))
    }

    /**
     * 锚点还没接上（`HomeScreen` 那行 `.coachAnchor(...)` 未落）时的退路：
     * 提示板**仍在场**，但不遮、不挡、不画箭头。
     *
     * 回退成什么就红：①量不到锚点就整块 return——又回到"引导没了"；
     * ②没锚点也铺一张吃点击的遮罩——用户点什么都没反应，比原来更糟。
     */
    @Test
    fun `a missing anchor keeps the guidance on screen but stops covering the page`() {
        mount(GuideCursor.PROVIDER, anchored = false)
        assertEquals("没锚点不该铺遮罩", 0, count(LbCoachTags.SCRIM))
        assertEquals("没锚点不该画箭头", 0, count(LbCoachTags.POINTER))
        assertEquals("字与出口都还得在场", 1, count(LbCoachTags.PLATE))
        assertEquals("主动作仍在", 1, count(LbCoachTags.OPEN_TARGET))
    }

    /** 宿主算不出字（copy=null）时也不许留半张罩子 */
    @Test
    fun `no copy means no half-drawn overlay`() {
        mount(GuideCursor.CAPTURE, copy = null)
        assertEquals(0, count(LbCoachTags.SCRIM))
        assertEquals(0, count(LbCoachTags.PLATE))
        assertEquals(0, count(LbCoachTags.POINTER))
    }

    /**
     * §9.1 的**页面闸**：根导航换到子页那一刻，罩子整块离场；回首页整块回来。
     *
     * 表行判的就是这一条：「遮罩没有限定只在首页显示」——改前这一层只有"锚点/文案"两道早退，
     * 子页上当然没有首页那四颗锚点，于是罩子退化成"无锚点退路"那张提示板，**浮在子页上**。
     *
     * 回退成什么就红：
     * - 闸被摘掉（`onHome` 不参与早退）⇒ 中间那三条各数到 1（旧板留在子页，正是表行的读数）；
     * - 闸做过了头、连首页也不画 ⇒ 第一条数到 0；
     * - 回首页时锚点没随重新入场登记回来 ⇒ 最后一条数到 0，罩子只剩一张贴底的板。
     */
    @Test
    fun `the overlay leaves entirely when a subpage takes the screen`() {
        mount(GuideCursor.PROVIDER)
        assertEquals("首页在场：提示板该在", 1, count(LbCoachTags.PLATE))
        assertEquals("首页在场：遮罩该在", 1, count(LbCoachTags.SCRIM))

        rule.runOnIdle { onHome.value = false }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(50L)
        assertEquals("子页不许留遮罩", 0, count(LbCoachTags.SCRIM))
        assertEquals("子页不许留箭头", 0, count(LbCoachTags.POINTER))
        assertEquals(
            "子页最不许留的是那张提示板（§9.1「点击洞和提示按钮都不能把旧板留到子页」）",
            0, count(LbCoachTags.PLATE)
        )

        rule.runOnIdle { onHome.value = true }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(50L)
        assertEquals("回首页：遮罩整块回来", 1, count(LbCoachTags.SCRIM))
        assertEquals("回首页：箭头回来并对得上重登记的锚点", 1, count(LbCoachTags.POINTER))
        assertEquals(
            "回首页：箭头该正对供应商那颗（不是贴在屏幕底板的退路档）",
            rectOf(LbHomeTags.ENTRY_PROVIDER).center.x,
            rectOf(LbCoachTags.POINTER).center.x,
            6f * density
        )
    }

    // ─────────────────────────── 出口与热区 ───────────────────────────

    /**
     * 每格都带"稍后"与"不再提示"两条出口，点了真的落到宿主那两颗回调（各一次，不多不少）。
     *
     * 回退成什么就红：出口只画不响（接 `{}`）；或有人把"稍后"接成 `stopBeingGuided()`
     * ——那就是"点一下永久消失"，用户原话第 14 条当场复发。
     */
    @Test
    fun `each step carries a defer exit and a never-again exit that actually fire`() {
        mount(GuideCursor.CAPTURE)
        assertEquals("「稍后」该只有一颗", 1, count(LbCoachTags.DEFER))
        assertEquals("「不再提示」该只有一颗", 1, count(LbCoachTags.STOP))
        nodes(LbCoachTags.DEFER)[0].performClick()
        nodes(LbCoachTags.STOP)[0].performClick()
        nodes(LbCoachTags.OPEN_TARGET)[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("「稍后」点一次该走宿主一次", 1, deferred)
        assertEquals("「不再提示」点一次该走宿主一次", 1, stopped)
        assertEquals("主动作点一次该走宿主一次", 1, opened)
    }

    /**
     * 罩子里每一颗可交互节点都够全站 48dp 下限（§3.1/§3.5：三颗动作全借设计系统的件，
     * 页面不给自己垫热区盒）。回退成什么就红：出口被画成一格 24dp 的小字。
     */
    @Test
    fun `every actionable node on the overlay meets the site touch floor`() {
        mount(GuideCursor.PROVIDER)
        val targets = probe.actionableTargets(rule, "首页遮罩引导")
        probe.at(TouchTier.SITE_FLOOR)
            .assertTargetsMeetFloor(targets, TouchTier.SITE_FLOOR, "遮罩引导")
        assertTrue(
            "反空跑：至少三颗（主动作 + 两条出口），实到 ${targets.size}：" +
                targets.joinToString { it.describe() },
            targets.size >= 3
        )
    }

    /**
     * 提示板对读屏报的名字来自 `R.string`（`contentDescription` 不许硬编码中文）。
     *
     * 这是文案那一面唯一有牙的判据：比的是资源现读出来的串——硬编码中文在 en 环境照旧念中文，当场红。
     * 屏上那几句字不作判据（见类顶仪器事实第 1 条）。
     */
    @Test
    fun `the plate announces itself by a name that comes from resources`() {
        mount(GuideCursor.PROVIDER)
        val plate = probe.of(nodes(LbCoachTags.PLATE).fetchSemanticsNodes().first())
        assertEquals(
            "提示板只该有一条 contentDescription，且等于 R.string.onboarding_title" +
                "（当前 locale 下 = \"$plateName\"），实到 " + plate.contentDescriptions,
            listOf(plateName),
            plate.contentDescriptions
        )
    }

    /**
     * 遮罩铺满整层（洞在那张 Path 里，语义树看不见它，所以这里只钉"罩子确实盖住了屏"）。
     * 回退成什么就红：遮罩只盖一半、或提示板被压成 0 尺寸（那格根本没摆出来）。
     */
    @Test
    fun `the scrim covers the whole overlay and the plate has a size`() {
        mount(GuideCursor.PROVIDER)
        val scrim = rectOf(LbCoachTags.SCRIM)
        val plate = rectOf(LbCoachTags.PLATE)
        assertTrue(
            "遮罩该铺满 360x640 这一格，实到 " + scrim.width.dpNumber() + "x" +
                scrim.height.dpNumber() + "dp",
            scrim.width.dpNumber() >= 359f && scrim.height.dpNumber() >= 639f
        )
        assertFalse("提示板不该是 0 尺寸（说明这格根本没摆出来）", plate.width.dpNumber() <= 0f)
    }
}
