package com.lovebrain.app.core.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * 本轮界面合同（实施书 第3节第2条 那张目标值表）里**测试侧的档位入口**。
 *
 * 为什么要有这一颗，而不是把 [SemanticsProbe] 的默认值改成 28/24：
 * 默认值一改，全站每一格一起松掉——那是 第16节第2条 明令禁止的"全局放宽断言"。
 * 合同拆掉的只是"把所有小件的**可见尺寸**钉成 48"这一条过度约束，
 * 全站那颗下限本身一个字没动（`AppDimens.TOUCH_TARGET_MIN_DP` 仍写着 48）。
 *
 * 所以规矩是：**按屏/按族显式传自己那一档**，紧凑场景不传就当它在走全站下限。
 * 每一颗都注明它管的是合同里的哪一行，别拿 A 档去量 B 屏。
 */
object TouchTier {
    /** 全站下限（默认档）：页面主体、列表行、弹窗/浮层动作、设计系统主动作 `Standard` */
    const val SITE_FLOOR = 48f

    /** 面板头部**整行**（合同「模式栏外层 30dp」）：三段切换器各占 1/3 宽 × 整行高 */
    const val PANEL_HEADER_ROW = 30f

    /** 面板头部那颗齿轮/收起的**外包盒**（合同「收起外包盒 24dp」，字形分别 16dp / 20dp） */
    const val PANEL_HEADER_HOTZONE = 24f

    /** 卡内那一排小操作（复制/赞/踩）与调整区胶囊：合同 28dp 见方，三颗合计 84 ≤ 142 内容宽 */
    const val CARD_ACTION = 28f

    /** 紧凑胶囊族（角色标签、卡内调整胶囊）：合同「最低 28dp 高」，与 [CARD_ACTION] 同数不同名分 */
    const val COMPACT_CHIP = 28f

    /** 通用**表单输入**的可见外框（合同「通用表单输入为 36dp」；回复那一条更窄，28dp 由调用方传） */
    const val VISIBLE_FORM_INPUT = 36f

    /** 面板主动作（生成 / 生成中 / 停止）的可见高度：合同 40dp */
    const val PANEL_PRIMARY_ACTION = 40f

    /** 已有结果那一排的并列双出口（重试 / 记入知识库）的可见高度：合同 36dp */
    const val PANEL_RESULT_ACTION = 36f
}

/**
 * 要求的那把尺：**读语义树**，不读源码。
 *
 * 复核原话是"UI 合同必须改成语义树边界断言：读取每个可点击节点的 boundsInRoot，
 * 宽/高都不小于这一档的下限；再加 TalkBack role/selected/stateDescription"。
 * 之前的 `ProductionUiContractTest` 在源码里搜 `MIN_TOUCH_TARGET_DP` 字样，
 * 于是"外层套了 48dp 的 Box、点击仍挂在 20dp 的子节点上"这种局面会判绿——
 * 而用户的手指信的是后者。
 *
 * 这里刻意不做任何字符串匹配：只看组合并测量之后，带点击/切换语义的那个节点自己的边界。
 *
 * ⚠ [minTouchDp] 的默认值是 [TouchTier.SITE_FLOOR]，含义是**全站下限**，不是"没传就算了"。
 *   紧凑场景（面板头部 30/24、卡内 28、可见表单 36、面板主动作 40/36）**必须**在调用点
 *   显式传 [TouchTier] 里对应的那一档——或者用 [withFloor] / [assertTargetsMeetFloor] /
 *   [assertActionablesByTier] 把档位写在判据旁边。把默认值整体调松就是 第16节第2条 禁的那条。
 */
class SemanticsProbe(private val density: Float, private val minTouchDp: Float = TouchTier.SITE_FLOOR) {

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
        /**
         * 这一颗是**可编辑文本**吗（带 `SetText` 动作）？
         *
         * 加这一栏是因为有一条判据不能套在输入框上：本机实量供应商表单的三颗输入框，
         * 尺寸与名字都对，`role` 却是 `无`——1.6.8 的 `BasicTextField` 根本不报角色。
         * 拿"可交互就得有角色"去判输入框，得到的是一条**永远修不红**的假闸
         * （与坑表 66 那一族同形：判据换了对象就不再成立）。
         * 输入框那一面该判的是 第6节第5条 第②栏的"读屏说得出它是什么"。
         */
        val editable: Boolean,
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
     * 换一把尺，但不换这份快照的所有读数：**调用点显式声明自己走哪一档**的入口。
     *
     * 用法是 `SemanticsProbe(density).at(TouchTier.CARD_ACTION)`——档位写在调用点上，
     * 一眼能看出这一格量的是紧凑档还是全站下限；默认值那颗 [SITE_FLOOR] 因此不需要、
     * 也不许被改松（改了就是把全站每一格一起放宽）。
     */
    fun at(minTouchDp: Float): SemanticsProbe = SemanticsProbe(density, minTouchDp)

    /**
     * 语义树里**根本没摆出来**的节点：`0x0dp @(0,0)`。
     *
     * 本机实量的来源：反馈案例页那族横排芯片（`horizontalScroll`）滚出视口的最后一颗
     * 「JSON」——账本 第58节。滚动容器会把它压成 0x0（贴在边上的那颗则被压成"看得见的那半截"，
     * 例如 15x48），两种都不是控件自己的尺寸。
     * 留着这种读数，三格会同时红在没发生过的话上（"页头有三颗"、"水平边距 0dp"、
     * "热区 0dp"），而且 @(0,0) 会顶掉任何"取最靠上/最靠左"的锚点。
     *
     * ⚠ 排除必须**看得见**：调用方要把 [unlaid] 那一份连尺寸一起打进失败信息或读数里。
     *   默默筛掉就是拿过滤藏读数（坑表 91：读不出数不等于没数）。
     */
    fun unlaid(targets: List<Target>): List<Target> =
        targets.filter { it.widthDp <= 0f || it.heightDp <= 0f }

    /** 摆得出来的那些（判尺寸、判角色、认锚点都用这一份） */
    fun laid(targets: List<Target>): List<Target> {
        val dropped = unlaid(targets)
        check(targets.size - dropped.size > 0) {
            "一颗粒都没摆出来（全被压成 0x0）：${targets.joinToString { it.describe() }}"
        }
        return targets - dropped.toSet()
    }

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
     * 空集合在这里不是"没有可交互控件"，而是"这格断言成了空过"——按 第9节 的规矩当成事故。
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
            editable = node.config.contains(SemanticsProperties.EditableText),
            widthDp = bounds.width / density,
            heightDp = bounds.height / density,
            leftDp = bounds.left / density,
            topDp = bounds.top / density
        )
    }

    /** 第6节第5条：所有 clickable/toggleable 的边界都要 ≥ **这一把尺**（默认 = 全站下限 [TouchTier.SITE_FLOOR]） */
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

    /**
     * 拿**指定的一档**量已经取到的读数：判数值、判两轴，不退化成"存在就行"。
     *
     * 给"同一屏里住着好几种档"的那几格用（结果区里卡内 28 与页级 48 并排），
     * 调用点必须自己把 [minDp] 说清，并且失败信息里要说是哪一档。
     */
    fun assertTargetsMeetFloor(
        targets: List<Target>,
        minDp: Float,
        screen: String,
        context: String = ""
    ): List<Target> {
        val offenders = targets.filter { it.tooSmall(minDp) }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "$screen$context 有 ${offenders.size}/${targets.size} 个节点小于这一档的 " +
                    "${minDp.toInt()}dp：\n" + offenders.joinToString("\n") { "  " + it.describe() }
            )
        }
        return targets
    }

    /**
     * 一整屏混档时的判据：**逐颗**按 [tierOf] 给出的那一档量，而不是整屏一把尺。
     *
     * 为什么不是"把整屏的尺调松到最小的那颗"：那等于让页面主体、弹窗动作这些
     * 仍该是 48 的格子跟着卡内那颗一起松掉（第16节第2条 禁的"全局放宽断言"）。
     * [tierOf] 拿不到档位的节点一律按 [minTouchDp]（这一格自己传进来的那一把）兜，
     * 不许返回 0 蒙过去。
     */
    fun assertActionablesByTier(
        rule: ComposeTestRule,
        screen: String,
        context: String = "",
        tierOf: (Target) -> Float
    ): List<Target> {
        val targets = actionableTargets(rule, screen)
        val offenders = targets.filter { it.tooSmall(tierOf(it)) }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "$screen$context 有 ${offenders.size}/${targets.size} 个可交互节点不到**自己那一档**的下限" +
                    "（默认 ${minTouchDp.toInt()}dp，density=$density）：\n" +
                    offenders.joinToString("\n") {
                        "  ${"%.0f".format(tierOf(it))}dp 档 → " + it.describe()
                    } +
                    "\n  修法：热区与视觉分两层——可点那一层自己垫到自己那一档，" +
                    "胶囊/图标在里面居中；只放大外层容器而点击仍挂在子节点上等于没改。"
            )
        }
        return targets
    }

    /** 第6节第5条：读屏必须能说出每个可交互节点是什么 */
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
     * 第6节第5条：同一个名字不许念两遍。
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
     * 第6节第5条：一组互斥选项（模式切换这类）必须让读屏听得出"现在在哪一格"。
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
