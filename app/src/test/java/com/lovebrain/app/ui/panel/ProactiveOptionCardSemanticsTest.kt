package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.model.ProactiveOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 主动发候选卡的展示结构账（书 §14.1「展示结构」在语义树上的那一半）。
 *
 * 链路侧（字段解析、留场、迟到事件丢弃）由 `ProactiveStoreTest` / `ProactiveOptionFieldsTest` /
 * `ProactiveResultRetentionTest` / `ProactiveFailureKeepsPartialBodyTest` 各自钉着；
 * 这里挂的是生产那一颗 `ProactiveResultArea`，判**画出来长什么样**：
 *
 * 1. **可复制正文在前、短角度说明为辅**：正文节点在角度节点上方；没写角度的候选
 *    不画一个空标签行。坏实现把角度提到正文上面、或对空角度照画"角度："，当场红。
 * 2. **时机/先别发/准备材料默认收起、按需展开，且展开后带类别标签**：
 *    收起时三类正文一行都不在（默认收起是 §14.1 的硬话），但入口讲得出里面是哪几类
 *    （不是一颗裸箭头）；展开后每行以"时机：/先别发：/需要准备："开头——三类句子长得像，
 *    没有标签就分不清哪句是等待依据（§14.2 末行拦的混淆）。默认展开、展开无标签，都红。
 * 3. **复制只带走可发送正文**：整卡点击交出去的字符串逐字等于 `text`，角度/时机/先别发/
 *    准备四个字段的记号一个都不许混进剪贴板——把策略行拼进复制载荷的实现当场红。
 *    展开之后再点，复制内容不变。
 * 4. **断流后部分候选与失败短提示同屏**（展示侧半边；store 那半边在
 *    `ProactiveStoreTest`）：error 非空且 options 非空时走的是"候选 + 底部一句错因"，
 *    不是把候选整批换掉只画错误条。坏实现一收到 error 就清场，红。
 * 5. **再生成的新一批默认收起**：上一批翻开的展开态不许按 index 原样带给新一批
 *    （真实链路再生成先清空 options 再流出新一批，这里照用户路径走）。
 *    展开账本跨批不销账的实现，红。
 *
 * ⚠ 标签一律 `getString` 从资源取，不写死中文（`NoticeStripSemanticsTest` 头部记过的教训）。
 * ⚠ 数量判据**只有"渲染几条画几条"**：书 §14.2 九类方向是评审差异用的，本文件与生产
 *    两侧都不写"必须有 N 张"；夹具只用一两条候选，任何"九张"断言都不该在这棵测试树上出生。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProactiveOptionCardSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    // 标签与行格式都从资源来：改文案不用改测试，但"每行带标签"这个结构跑不掉
    private val angleLabel get() = ctx.getString(R.string.proactive_label_angle)
    private val timingLabel get() = ctx.getString(R.string.proactive_label_timing)
    private val holdBackLabel get() = ctx.getString(R.string.proactive_label_hold_back)
    private val prepareLabel get() = ctx.getString(R.string.proactive_label_prepare)
    private fun line(label: String, body: String) =
        ctx.getString(R.string.proactive_strategy_line, label, body)

    private val optionsState = mutableStateOf<List<ProactiveOption>>(emptyList())
    private val copies = mutableListOf<String>()

    /** 挂生产那一颗；`onCopy` 捕获进 [copies]，按点击顺序排列 */
    private fun mount(initial: List<ProactiveOption>, error: String? = null) {
        optionsState.value = initial
        copies.clear()
        rule.setContent {
            UiMatrix(360, 900).RenderIn(LocalDensity.current.density) {
                ProactiveResultArea(
                    isProactive = false,
                    options = optionsState.value,
                    error = error,
                    onCopy = { copies.add(it) }
                )
            }
        }
        rule.waitForIdle()
    }

    private fun option(
        text: String,
        angle: String = "",
        timing: String = "",
        holdBack: String = "",
        prepare: String = ""
    ) = ProactiveOption(
        text = text,
        angle = angle,
        timing = timing,
        holdBack = holdBack,
        prepare = prepare
    )

    // ═══════════ ① 正文在前，角度为辅 ═══════════

    @Test
    fun `the sendable text leads and the angle note follows it`() {
        mount(listOf(option("正文一", angle = "角度说明A")))
        val body = rule.onNodeWithText("正文一", useUnmergedTree = true).fetchSemanticsNode()
        val angleLine = rule.onNodeWithText(line(angleLabel, "角度说明A"), useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(
            "角度说明(${angleLine.boundsInRoot.top})该排在正文(${body.boundsInRoot.top})下面——" +
                "§14.1 的次序是可复制正文在前、短角度说明为辅",
            angleLine.boundsInRoot.top > body.boundsInRoot.top
        )
    }

    /** 没写角度的候选：不画一个只带"角度："的空标签行（一台夹具一次 setContent，所以单独成格） */
    @Test
    fun `an angle-less candidate paints no bare angle row`() {
        mount(listOf(option("正文二")))
        rule.onNodeWithText(angleLabel, substring = true).assertDoesNotExist()
    }

    // ═══════════ ② 默认收起、按需展开、展开带类别标签 ═══════════

    @Test
    fun `strategy lines stay collapsed until asked and name their kind when open`() {
        mount(
            listOf(
                option(
                    "正文一",
                    timing = "时机说明T",
                    holdBack = "先别发说明H",
                    prepare = "准备说明P"
                )
            )
        )
        // 默认收起：三类正文一行都不在树上
        rule.onNodeWithText(line(timingLabel, "时机说明T")).assertDoesNotExist()
        rule.onNodeWithText(line(holdBackLabel, "先别发说明H")).assertDoesNotExist()
        rule.onNodeWithText(line(prepareLabel, "准备说明P")).assertDoesNotExist()
        // 收起时入口也讲得出里面是哪几类（不是一颗裸箭头）
        val hint = listOf(timingLabel, holdBackLabel, prepareLabel).joinToString(" · ")
        rule.onNodeWithText(hint).assertExists()

        rule.onNodeWithText(hint).performClick()
        rule.waitForIdle()
        // 展开后每行带自己的类别标签——三类没有标签就分不清哪句是等待依据
        rule.onNodeWithText(line(timingLabel, "时机说明T")).assertExists()
        rule.onNodeWithText(line(holdBackLabel, "先别发说明H")).assertExists()
        rule.onNodeWithText(line(prepareLabel, "准备说明P")).assertExists()

        // 再点收回：按需展开是双向的
        rule.onNodeWithText(hint).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(line(timingLabel, "时机说明T")).assertDoesNotExist()
    }

    /** 三段全空的候选（旧输出）：不画展开行，也没有箭头入口 */
    @Test
    fun `a candidate with no strategy payload grows no expand row`() {
        mount(listOf(option("正文三", angle = "角度说明A")))
        rule.onNodeWithText(line(angleLabel, "角度说明A")).assertExists()
        rule.onNodeWithText(timingLabel, substring = true).assertDoesNotExist()
        val expand = ctx.getString(R.string.action_expand)
        rule.onNodeWithText(expand, substring = true).assertDoesNotExist()
    }

    // ═══════════ ③ 复制只带走可发送正文 ═══════════

    @Test
    fun `copying a candidate copies only its sendable text`() {
        mount(
            listOf(
                option(
                    "正文一",
                    angle = "角度说明A",
                    timing = "时机说明T",
                    holdBack = "先别发说明H",
                    prepare = "准备说明P"
                )
            )
        )
        // ⚠ 必须用**未合并树**点正文自己：合并树上 onNodeWithText 命中的是整卡可点击节点，
        // performClick 点的是整卡几何中心——那里住着策略行（内层可点击），点下去是"展开"
        // 不是"复制"（首跑就是这么拿到空 copies 的）。
        rule.onNodeWithText("正文一", useUnmergedTree = true).performClick()
        assertEquals("点卡复制的是可发送正文本身", listOf("正文一"), copies)
        val payload = copies.single()
        listOf("角度说明A", "时机说明T", "先别发说明H", "准备说明P").forEach { marker ->
            assertTrue("策略行「$marker」不许混进复制内容，剪贴板里只有：$payload", marker !in payload)
        }

        // 展开之后再点：复制内容仍然只有正文
        val hint = listOf(timingLabel, holdBackLabel, prepareLabel).joinToString(" · ")
        rule.onNodeWithText(hint).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("正文一", useUnmergedTree = true).performClick()
        assertEquals("展开前后复制内容不变", listOf("正文一", "正文一"), copies)
    }

    // ═══════════ ④ 断流后部分候选与失败短提示同屏 ═══════════

    @Test
    fun `partial openers stay next to the failure notice`() {
        val timeoutNotice = "生成超时，已保留部分内容"
        mount(
            listOf(option("正文一", timing = "时机说明T"), option("正文二")),
            error = timeoutNotice
        )
        rule.onNodeWithText("正文一").assertExists()
        rule.onNodeWithText("正文二").assertExists()
        rule.onNodeWithText(timeoutNotice).assertExists()
        // 已流出候选的策略入口仍在：等待依据不该因为断流一起消失
        rule.onNodeWithText(timingLabel).assertExists()
    }

    // ═══════════ ⑤ 再生成的新一批默认收起 ═══════════

    @Test
    fun `a regenerated batch does not inherit the previous batch's expansion`() {
        mount(listOf(option("正文旧", timing = "旧时机说明")))
        rule.onNodeWithText(timingLabel).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(line(timingLabel, "旧时机说明")).assertExists()

        // 真实链路的再生成：ProactiveStarted 先清空、再流出新一批——照用户路径走
        rule.runOnIdle { optionsState.value = emptyList() }
        rule.waitForIdle()
        rule.runOnIdle { optionsState.value = listOf(option("正文新", timing = "新时机说明")) }
        rule.waitForIdle()

        rule.onNodeWithText("正文新").assertExists()
        // 上一批的展开态不许按 index 带给新的一批：默认收起以批为单位
        rule.onNodeWithText(line(timingLabel, "新时机说明")).assertDoesNotExist()
        // 入口还在，用户按需展开的路径没被收走
        rule.onNodeWithText(timingLabel).assertExists()
    }
}
