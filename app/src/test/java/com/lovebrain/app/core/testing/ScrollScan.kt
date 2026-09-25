package com.lovebrain.app.core.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp

/**
 * 沿纵向**滚到底、每档重扫整棵树**，返回"每颗控件自己的尺寸"。
 *
 * 为什么需要它（这台仪器的两条天花板合在一起）：
 * 1. **滚动容器的裁切会进语义树**（坑表 90，横排那一次记在 85）：`verticalScroll` 里
 *    贴着视口底的那颗报 `48x43dp`，完全滚出去的报 `0x0dp @(0,0)`。
 *    照"筛掉被裁的"老办法就得把**视口高度写进判据**——换一档字号或换一档宽度就失效（坑表 64）。
 *    ⇒ 裁切只会让读数**变小**，所以滚过一遍取**最大面积**就是这颗控件本来的尺寸，一个常数都不写。
 * 2. 一屏之下的东西不滚就永远看不见，而"只扫首屏却自称扫过全表单"是最难发现的假绿。
 *
 * 两条哨兵（缺一条这格就退化成在首屏转圈）：
 * ① 位置指纹**连续两档相同**才算到底并返回；
 * ② 到 [maxSteps] 上限仍没相同 ⇒ 抛错，不许拿半截扫描当整屏的证据。
 *
 * ⚠ 同名多颗（例如三行 × 四颗行内图标）在这里会**合成一条**（取最大面积）。
 * 少判的是"某一行被别的行挤小了"这种版式问题——那一类要用另一把尺判（行内逐颗比宽度），
 * 别以为这一格覆盖了。
 */
class ScrollScan(
    private val rule: ComposeTestRule,
    private val probe: SemanticsProbe,
    private val maxSteps: Int = 12
) {

    private val scrollByMatcher =
        SemanticsMatcher("hasScrollBy") { it.config.contains(SemanticsActions.ScrollBy) }

    /**
     * 这把尺里"同一颗控件"的身份键。
     *
     * ⚠ 不能直接用 [SemanticsProbe.Target.announced]：输入框那颗的文字与
     * `contentDescription` 常是同一条 placeholder，`announced` 会拼成「名称 / 名称」，
     * 覆盖清单就永远对不上——报出来是"没量到"，而它明明在屏上（坑表 91：
     * **读不出数不等于没数**）。
     */
    fun keyOf(t: SemanticsProbe.Target): String =
        (listOf(t.label) + t.contentDescriptions).filter { it.isNotBlank() }.distinct().joinToString(" + ")

    fun toBottom(screen: String): Map<String, SemanticsProbe.Target> {
        val best = LinkedHashMap<String, SemanticsProbe.Target>()
        var previous: String? = null
        for (step in 0 until maxSteps) {
            val targets = probe.actionableTargets(rule, screen)
            targets.forEach { t ->
                val cur = best[keyOf(t)]
                if (cur == null || cur.widthDp * cur.heightDp < t.widthDp * t.heightDp) best[keyOf(t)] = t
            }
            val fingerprint =
                targets.joinToString(";") { "${keyOf(it)}@${it.leftDp.toInt()},${it.topDp.toInt()}" }
            if (fingerprint == previous) return best
            previous = fingerprint
            val nodes = rule.onAllNodes(scrollByMatcher).fetchSemanticsNodes()
            check(nodes.isNotEmpty()) {
                "$screen 里找不到滚动容器：这一格没法逐档扫，先去查 verticalScroll 还在不在"
            }
            rule.onAllNodes(scrollByMatcher)[0].performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        throw AssertionError(
            "$screen 滚了 $maxSteps 档还没到底（累计只量到 ${best.size} 类节点）——" +
                "要么滚动没生效，要么这屏长得没法扫，不能拿半截结果当整屏的证据"
        )
    }
}
