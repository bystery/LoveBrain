package com.lovebrain.app.core.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * P1-02 要求的那把尺：**读语义树**，不读源码。
 *
 * 复核原话是"UI 合同必须改成语义树边界断言：读取每个可点击节点的 boundsInRoot，
 * 宽/高都不小于 48dp；再加 TalkBack role/selected/stateDescription"。
 * 之前的 `ProductionUiContractTest` 在源码里搜 `MIN_TOUCH_TARGET_DP` 字样，
 * 于是"外层套了 48dp 的 Box、点击仍挂在 20dp 的子节点上"这种局面会判绿——
 * 而用户的手指信的是后者。
 *
 * 这里刻意不做任何字符串匹配：只看组合并测量之后，带点击/切换语义的那个节点自己的边界。
 */
class SemanticsProbe(private val density: Float, private val minTouchDp: Float = 48f) {

    /** 一个可交互节点的完整可读快照（断言失败时要能凭这一行说出"哪儿、多大、念什么"） */
    data class Target(
        val label: String,
        val contentDescriptions: List<String>,
        val role: String,
        val selected: Boolean?,
        val stateDescription: String?,
        val isToggle: Boolean,
        val toggleState: String?,
        val disabled: Boolean,
        val widthDp: Float,
        val heightDp: Float,
        val leftDp: Float,
        val topDp: Float
    ) {
        /** 读屏能念出点什么：文案或 contentDescription 至少有一个 */
        val labeled: Boolean get() = label.isNotBlank() || contentDescriptions.isNotEmpty()

        /**
         * 合并语义后的标签可能同时有文字与 contentDescription。
         * `label` 优先取文字（那才是眼睛看到的），所以"图标自己声明了什么"
         * 必须单独看 [contentDescriptions]——否则像"折叠箭头上写了什么"这种断言
         * 永远读不到，还会假红。
         */
        val announced: String get() = (listOf(label) + contentDescriptions).joinToString(" / ")

        /** 合并语义后同一个名字出现两次以上 = 念两遍 */
        val isDuplicatedAnnouncement: Boolean
            get() {
                val parts = label.split('+').map { it.trim() }.filter { it.isNotEmpty() }
                return parts.size > 1 && parts.distinct().size < parts.size
            }

        fun tooSmall(minDp: Float): Boolean =
            widthDp + 0.5f < minDp || heightDp + 0.5f < minDp

        fun describe(): String =
            "「$label」 role=$role selected=$selected state=$stateDescription " +
                (if (isToggle) "toggle=$toggleState " else "") +
                (if (disabled) "disabled " else "") +
                "尺寸 ${widthDp.toInt()}x${heightDp.toInt()}dp @(${leftDp.toInt()},${topDp.toInt()})"
    }

    /** 触摸区下限（dp），失败信息里要说清用的是哪一把尺 */
    val floorDp: Float get() = minTouchDp

    /**
     * "可交互"的判据：带点击动作、带切换状态，或者**被禁用**的点击控件。
     *
     * 把 Disabled 也算进来不是为了多抓几个节点：`clickable(enabled = false)` 的按钮
     * 仍然是一个操作入口，只是当前不许按。跳过它就会让"N=0 时那个按钮根本没画出来"
     * 和"N=0 时它画出来了但是灰的"两种实现都判绿——而合同要的是后者。
     */
    private val actionable: SemanticsMatcher =
        hasClickAction() or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

    /**
     * 取出所有带点击/切换语义的节点。
     *
     * 空集合在这里不是"没有可交互控件"，而是"这格断言成了空过"——按 §9 的规矩当成事故。
     */
    fun actionableTargets(rule: ComposeTestRule, screen: String): List<Target> {
        val nodes = rule.onAllNodes(actionable).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) {
            "$screen 里一个可点击/可切换节点都没测到。要么入口被删了，要么点击语义没挂上——" +
                "两种都是事故，不能让这条测试因为'没东西可查'而绿过去。"
        }
        return nodes.map { of(it) }
    }

    fun of(node: SemanticsNode): Target {
        val bounds = node.boundsInRoot
        val text = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        val descs = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        return Target(
            label = (text ?: descs.joinToString("+")).trim(),
            contentDescriptions = descs,
            role = node.config.getOrNull(SemanticsProperties.Role)?.toString() ?: "无",
            selected = node.config.getOrNull(SemanticsProperties.Selected),
            stateDescription = node.config.getOrNull(SemanticsProperties.StateDescription),
            isToggle = node.config.contains(SemanticsProperties.ToggleableState),
            toggleState = node.config.getOrNull(SemanticsProperties.ToggleableState)?.toString(),
            disabled = node.config.contains(SemanticsProperties.Disabled),
            widthDp = bounds.width / density,
            heightDp = bounds.height / density,
            leftDp = bounds.left / density,
            topDp = bounds.top / density
        )
    }

    /** §6.5：所有 clickable/toggleable 的边界都要 ≥48×48dp */
    fun assertAllActionableMeetTouchFloor(
        rule: ComposeTestRule,
        screen: String,
        context: String = ""
    ): List<Target> {
        val targets = actionableTargets(rule, screen)
        val offenders = targets.filter { it.tooSmall(minTouchDp) }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "$screen$context 有 ${offenders.size}/${targets.size} 个可交互节点小于 ${minTouchDp.toInt()}dp" +
                    "（density=$density）：\n" +
                    offenders.joinToString("\n") { "  " + it.describe() } +
                    "\n  修法：把 clickable/toggleable 挂到那个足够大的盒子自己身上；" +
                    "只放大外层容器而点击仍挂在子节点上，等于没改。"
            )
        }
        return targets
    }

    /** §6.5：读屏必须能说出每个可交互节点是什么 */
    fun assertAllActionableLabeled(
        rule: ComposeTestRule,
        screen: String,
        context: String = ""
    ): List<Target> {
        val targets = actionableTargets(rule, screen)
        val unlabeled = targets.filter { !it.labeled }
        if (unlabeled.isNotEmpty()) {
            throw AssertionError(
                "$screen$context 有 ${unlabeled.size}/${targets.size} 个可交互节点既没有文案也没有 " +
                    "contentDescription，读屏只会念「按钮」：\n" +
                    unlabeled.joinToString("\n") { "  " + it.describe() }
            )
        }
        return targets
    }

    /**
     * §6.5：同一个名字不许念两遍。
     *
     * 语义树合并时，父节点自己声明的 contentDescription 会和子图标的拼成
     * `"X+X"` 这种串——读屏就把一个按钮念成两遍。修法是把子图标显式设为装饰
     * （`contentDescription = null`），标签只在可点击那一处声明。
     */
    fun assertNoDuplicatedAnnouncement(
        rule: ComposeTestRule,
        screen: String,
        context: String = ""
    ): List<Target> {
        val targets = actionableTargets(rule, screen)
        val doubled = targets.filter { it.isDuplicatedAnnouncement }
        if (doubled.isNotEmpty()) {
            throw AssertionError(
                "$screen$context 有 ${doubled.size}/${targets.size} 个可交互节点把标签念了两遍" +
                    "（父节点与子图标各声明一次，合并后成了「X+X」）：\n" +
                    doubled.joinToString("\n") { "  " + it.describe() } +
                    "\n  修法：标签只在可点击的那个节点上声明一次，里面的图标写 contentDescription = null。"
            )
        }
        return targets
    }

    /**
     * §6.5：一组互斥选项（模式切换这类）必须让读屏听得出"现在在哪一格"。
     * 每个可选项要么带 selected，要么带 stateDescription / toggle 状态。
     */
    fun assertSelectableAnnounceState(targets: List<Target>, group: String, context: String = "") {
        val missing = targets.filter {
            it.selected == null && it.stateDescription.isNullOrBlank() && !it.isToggle
        }
        if (missing.isNotEmpty()) {
            throw AssertionError(
                "$group$context 有 ${missing.size}/${targets.size} 个可选项没 announce 自己的状态" +
                    "（selected / stateDescription / toggleable 三者全无）：\n" +
                    missing.joinToString("\n") { "  " + it.describe() }
            )
        }
    }
}
