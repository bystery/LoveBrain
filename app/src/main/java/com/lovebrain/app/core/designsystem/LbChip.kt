package com.lovebrain.app.core.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 芯片的**交互归属**——决定它是什么角色、"选中"这件事怎么进语义树。
 *
 * 为什么这是一颗参数而不是三种组件：本仓库那七处异形芯片的**形状**本来就不一样，
 * 但它们要回答的问题只有这三种，而且这三种的答案在语义树上必须各自唯一：
 *
 * - [Action]：点下去有结果（填模板、开编辑器）。`Role.Button`，**不播报选中**——
 *   它没有"在哪一格"这件事，写 `selected` 反而是给读屏加噪声。
 * - [Single]：一组里互斥的单选。`Role.Tab` + `SemanticsProperties.Selected`，
 *   与页头那三档模式、回复输入行的「她/我/想法」同一写法（同一棵树上得听得出
 *   "这是个选项、现在在哪一格"）。
 * - [Multi]：可叠加的多选。`Role.Checkbox` + `toggleable` ⇒ 语义树里是
 *   `ToggleableState`（On/Off），点踩面板那族守卫判的就是这个，不是 `Selected`。
 *
 * ⚠ [Multi] 与 [Single] 在语义树里是两种不同的槽，**不能混**：把多选写成 `Selected`
 * 会让读屏把"还能再选几个"说成"现在在哪一格"。
 */
enum class LbChipInteraction { Action, Single, Multi }

/**
 * 标签在胶囊自己那个盒子里**贴哪儿**的那一档。
 *
 * 为什么这也是一颗参数而不是组件里写死的一句：归并那六处芯片时，模板那颗原来用的是
 * `Box` 的默认对齐——标签贴在 48dp 见方药丸的**左上沿**；共用组件一律居中，于是
 * "形状收进公共件"顺手把那一带的标签往下挪了约十 dp（一处真实的外观变化）。
 * 这一档买的就是**形状归并能不改变外观**：收得掉链，收不掉脸。
 *
 * ⚠ 只有这两档，而且**不是一张自由的 `Alignment`**：给调用方一个 `Alignment` 参数，
 * 等于允许下一颗芯片在页面里自己再摆一次标签。
 * 摆放只在 [labelPlacement] 这一处映射，`LbChip` 里没有第二条分支。
 */
enum class LbChipLabelAlignment {
    /** 标签落在胶囊的中轴上——这是这一族**今天**的形状，也是 [LbChipStyle] 的默认档 */
    Center,

    /** 标签贴胶囊左上角——归并前模板那颗就是这个形状，[LbChipStyles.neutral] 选的正是这一档 */
    TopStart
}

/**
 * 档位 → 摆放。写法照 `LbTextActionGlyph.glyphSize`：枚举项只带名字，形状写在扩展里，
 * 于是"换一档"在调用点上是一个词，不是一条链。
 */
internal val LbChipLabelAlignment.labelPlacement: Alignment
    get() = when (this) {
        LbChipLabelAlignment.Center -> Alignment.Center
        LbChipLabelAlignment.TopStart -> Alignment.TopStart
    }

/**
 * 按下去到回弹这一段时间里，缩放**沿哪条曲线**走的那一档。
 *
 * 它管的不是"缩到多少"（那是 [LbChipStyle.pressedScale]），也不是"点下去算什么角色"
 * （那是 [LbChipInteraction]）。归并那六处时，回复输入行那三颗角色芯片走的是
 * `animateFloatAsState` 不指定 animationSpec 时那条**默认弹簧**，而共用组件统一走
 * 全站那条 120ms 的 tween（又一处外观变化）——没有这一档，归并就得换脸。
 *
 * ⚠ 同样不许开成自由的 `FiniteAnimationSpec` 参数：曲线只能从名字里选。
 * 页面要是能自己交一条曲线，"按压反馈"就又有 N 份了，而这颗组件存在的理由就是它只有一份。
 */
enum class LbChipPressFeedback {
    /** 全站那条曲线（120ms + FastOutSlowIn）——[LbChipStyle] 的默认档，也是这一族今天的曲线 */
    Standard,

    /** 默认弹簧：`animateFloatAsState` 不写 animationSpec 时走的那一条 */
    Spring
}

/**
 * 档位 → 曲线。`Standard` 交出去的是 [lbPressCurve] 那**一颗对象**，不是照抄它的两个数：
 * 这样"全站曲线被改了"与"芯片默认档跟没跟上"永远不可能分家。
 */
internal val LbChipPressFeedback.pressCurve: FiniteAnimationSpec<Float>
    get() = when (this) {
        LbChipPressFeedback.Standard -> lbPressCurve
        LbChipPressFeedback.Spring -> spring()
    }

/**
 * 一颗芯片的全部**形状参数**（颜色、圆角、内边距、字号、热区结构）。
 *
 * 为什么要把这些写成参数而不是写死一档：这七处芯片的圆角从 6dp 到 999dp、
 * 字号有 `labelSmall` 与 `labelMedium` 两档、内边距有四档、
 * 选中底有实心与浅底两式——**归并它们的前提是别把七屏的脸换掉**，
 * 所以变的是"哪一档"，不变的是"形状只在这里画一次"。
 * 默认值就是最常用的那一档（实心选中 + 小字 + 48dp 见方），
 * 页面上要用别的档位从这里 `copy`，**不许在页面里再画一条 Modifier 链**。
 *
 * 里面没有任何词表：文案一律由调用方交进来（见 [LbChip.label]）。
 *
 * @param radius 圆角档位
 * @param textStyle 字号档（`labelSmall` / `labelMedium` 两档在本仓库各有主）
 * @param labelMaxLines 文案行数上限；默认不限，紧凑胶囊那颗要钉成一行
 * @param labelAlignment 标签在胶囊里贴哪儿（[LbChipLabelAlignment]）；默认居中 =
 *        这一族今天的形状，只有 [LbChipStyles.neutral] 换档（它归并前那一条链就是贴左上的）
 * @param textColorSelected 选中时的字色
 * @param textColor 未选中时的字色
 * @param fontWeightSelected 选中时的字重（`Neutral` 一档里前后同值 = 不靠字重表达状态）
 * @param fontWeight 未选中时的字重
 * @param backgroundSelected 选中时的底色（实心档 = Primary，浅底档 = PrimaryLight）
 * @param background 未选中时的底色
 * @param borderSelected 选中时的描边色——它的存在让"选中不描边"那种写法能收成
 *        同一条链：描边色与底色同色时像素不变，于是组件里少一个条件分支
 * @param border 未选中时的描边色
 * @param paddingHorizontal 文案左右内边距
 * @param paddingVertical 文案上下内边距
 * @param pressedScale 按压时的缩放（标准件那三档 0.92/0.94/0.96）
 * @param pressFeedback 这个缩放**沿哪条曲线**走到（[LbChipPressFeedback]）；默认
 *        [LbChipPressFeedback.Standard] = 全站那条 120ms，也就是这一族今天的曲线
 * @param markSelectedWithCheck 选中时在文案前加一个对勾。这个勾**不是**"选中的第二种
 *        表达"：规范位是语义树里的 `Selected`/`ToggleableState`，勾是画给眼睛的
 * @param touchFloor 要不要把这颗自己的可点节点垫到全局那颗下限
 *        （[AppDimens.TOUCH_TARGET_MIN_DP]）。关掉它只用于**本来就低于下限的既有形状**，
 *        新开的一律别关
 * @param layeredTouch 热区与视觉分两层：外层那颗透明盒子负责下限与语义，
 *        里面的胶囊按自己的尺寸画。有些胶囊一旦铺满 48dp 就会变粗，这一档买的是"视觉不动"
 * @param pillHeight 胶囊自己的高度；`null` = 由内容决定
 */
data class LbChipStyle(
    val radius: Shape = LoveBrainShape.sm,
    val textStyle: TextStyle = AppTypography.labelSmall,
    val labelMaxLines: Int = Int.MAX_VALUE,
    val labelAlignment: LbChipLabelAlignment = LbChipLabelAlignment.Center,
    val textColorSelected: Color = Color.White,
    val textColor: Color = TextSecondary,
    val fontWeightSelected: FontWeight = FontWeight.SemiBold,
    val fontWeight: FontWeight = FontWeight.Normal,
    val backgroundSelected: Color = Primary,
    val background: Color = SurfaceCard,
    val borderSelected: Color = Primary,
    val border: Color = Border,
    val paddingHorizontal: Dp = Spacing.lg,
    val paddingVertical: Dp = Spacing.sm,
    val pressedScale: Float = 0.94f,
    val pressFeedback: LbChipPressFeedback = LbChipPressFeedback.Standard,
    val markSelectedWithCheck: Boolean = true,
    val touchFloor: Boolean = true,
    val layeredTouch: Boolean = false,
    val pillHeight: Dp? = null
)

/**
 * 芯片的常用形状档位。名字跟着**语义**走，不跟着页面走——
 * 新增一档之前先问"它表达的是不是另一件事"，只是颜色不同的话应该改调用方的档位选择。
 * 页面要微调某一档里某一个数（比如某颗胶囊自己的高度），走 `copy`，**不要新长一档**，
 * 更不要回到页面里自己画那条链。
 */
object LbChipStyles {

    /** 一级选项：选中就实心。点踩面板的一级类别、反馈页的筛选芯片、有效期那三档 */
    val filled = LbChipStyle()

    /** 二级选项：选中只上浅底 + 深色字——它比一级轻，但仍然是"选中" */
    val soft = LbChipStyle(
        backgroundSelected = PrimaryLight,
        textColorSelected = PrimaryDark,
        fontWeightSelected = FontWeight.Medium,
        fontWeight = FontWeight.Medium
    )

    /**
     * 无选中态的动作芯片：点下去有结果，底色与描边不随状态变。
     *
     * 这一档是把归并前那一条链**逐项**抄过来的，包括标签的摆放：那颗的盒子写着
     * `heightIn(min = 48)` 而 `Box` 没写 `contentAlignment`，于是标签贴在药丸的左上沿。
     * 归并那一批没有"标签对齐"这一档，标签被组件的居中挪走了约十 dp，
     * [LbChipLabelAlignment.TopStart] 补上这一档之后
     * 这里把它接回原位——**这一档的名字管的是"没有选中态"，标签贴左上属于它的形状**。
     * 要一颗居中的动作芯片请新长一档，不要在这里改数。
     */
    val neutral = LbChipStyle(
        backgroundSelected = SurfaceInset,
        textColorSelected = TextSecondary,
        fontWeightSelected = FontWeight.Medium,
        fontWeight = FontWeight.Medium,
        background = SurfaceInset,
        borderSelected = Border,
        paddingHorizontal = Spacing.md,
        paddingVertical = Spacing.sm,
        pressedScale = 0.92f,
        labelAlignment = LbChipLabelAlignment.TopStart,
        markSelectedWithCheck = false
    )

    /** 标题行里那颗紧凑胶囊：圆到极致、由内容决定高度、带"开/关"两色 */
    val pill = LbChipStyle(
        radius = LoveBrainShape.full,
        textColorSelected = PrimaryDark,
        textColor = TextHint,
        fontWeightSelected = FontWeight.Medium,
        fontWeight = FontWeight.Medium,
        backgroundSelected = PrimaryLight,
        background = SurfaceInset,
        borderSelected = PrimarySubtle,
        // 竖直内边距一档都不给：这颗的盒子由自己的高度定死，文案在里面居中，
        // 多给两档不会让它变高，只会把可点节点里的空白算进文案区
        paddingHorizontal = Spacing.sm,
        paddingVertical = 0.dp,
        pressedScale = 0.92f,
        markSelectedWithCheck = false,
        touchFloor = false,
        labelMaxLines = 1
    )
}

/** 选中时对勾与文案之间那一个空格——形状的一部分，不是文案 */
private const val LB_CHIP_CHECK = "✓ "

/**
 * 全站唯一的一颗芯片（统一组件清单里缺的那一行）。
 *
 * 它买掉的是一件事：**"选中"长什么样、点得中多大、读屏念什么**只在这里有一份。
 * 归并之前这七处各画一条 Modifier 链，于是同一件事在同一屏里有四种写法——
 * 上一格量到的 `✓ ` 前缀有带的不带的、下限有垫的没垫的、`Role` 有 Tab 有 Checkbox
 * 有干脆没声明的。文案一律由调用方交（设计系统不持有某一个页面的词表），
 * 形状与语义一律由这一处定。
 *
 * 三条不能退回的判据，都只有语义树说了算：
 * 1. [LbChipInteraction.Single] 必发 `Selected`、[LbChipInteraction.Multi] 必发
 *    `ToggleableState`——**颜色与对勾都不是选中的规范位**；
 * 2. 下限垫在**可点那颗自己身上**（[LbChipStyle.touchFloor] 排在动作之前），
 *    只放大外层容器而动作仍挂在子里面等于没改；
 * 3. [enabled] 为假时这颗**还在原位**、仍然报得出名字，只是多报一个 Disabled
 *    ——不是从树上消失（消失掉的禁用态读屏永远不知道曾经有个入口）。
 *
 * ⚠ [LbChipStyle.layeredTouch] 与 [LbChipStyle.pillHeight] 是两条既有的"热区分层"形状，
 * 不是偷懒：那两处原来就是外层触摸盒 + 内层胶囊，改成单层会把胶囊撑到 48dp 见方，
 * 那是换脸不是归并。
 *
 * ⚠ [LbChipStyle.labelAlignment] 与 [LbChipStyle.pressFeedback] 这两档是为同一件事补的：
 * 归并那六处时缺了这两个旋钮，于是"收链"顺手挪走了标签（约 10dp）、换掉了按压曲线，
 * 两处都只能"登记未修"。判据不变——**归并的产物必须与归并前逐像素相同**，
 * 需要另一种脸就得先有另一种档，不许让调用方自己画。
 */
@Composable
fun LbChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    interaction: LbChipInteraction = LbChipInteraction.Action,
    style: LbChipStyle = LbChipStyles.filled
) {
    val (pressSource, scale) = rememberPressScale(
        style.pressedScale,
        "lbChip_$label",
        curve = style.pressFeedback.pressCurve
    )
    val shownLabel =
        if (selected && style.markSelectedWithCheck) LB_CHIP_CHECK + label else label
    val backgroundNow =
        if (selected) style.backgroundSelected else style.background
    val borderNow = if (selected) style.borderSelected else style.border
    val textColorNow = if (selected) style.textColorSelected else style.textColor
    val textWeightNow = if (selected) style.fontWeightSelected else style.fontWeight
    // 先接到局部：胶囊高度这一档要么"由内容定"（null）要么钉死，两处分支都要用同一个判据
    val fixedPillHeight = style.pillHeight

    // 语义与热区这一层：动作挂在它身上，合并语义把里面的文案收进同一颗节点
    val floorModifier = if (style.touchFloor) {
        Modifier
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
    } else {
        Modifier
    }
    val actionModifier = when (interaction) {
        LbChipInteraction.Multi -> Modifier.toggleable(
            value = selected,
            role = Role.Checkbox,
            enabled = enabled,
            interactionSource = pressSource,
            indication = null,
            onValueChange = { onClick() }
        )
        LbChipInteraction.Single -> Modifier
            .clickable(
                interactionSource = pressSource,
                indication = null,
                role = Role.Tab,
                enabled = enabled,
                onClick = onClick
            )
            // `selected` 必须在动作**之后**排在同一条链上：换顺序不会改语义，
            // 但会让读屏在动作缺失的实现里静默丢掉这一槽（与页头模式段同一写法）
            .semantics { this.selected = selected }
        LbChipInteraction.Action -> Modifier.clickable(
            interactionSource = pressSource,
            indication = null,
            role = Role.Button,
            enabled = enabled,
            onClick = onClick
        )
    }

    if (style.layeredTouch) {
        // 两层：外面那颗只负责下限与语义，胶囊在里面按原来的尺寸画——视觉一字不改
        Box(
            modifier = modifier
                .then(floorModifier)
                .then(actionModifier)
                .testTag(LbTags.CHIP),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .then(if (fixedPillHeight != null) Modifier.height(fixedPillHeight) else Modifier)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(style.radius)
                    .background(backgroundNow, style.radius)
                    .border(AppDimens.BORDER_WIDTH_DP.dp, borderNow, style.radius)
                    .padding(
                        horizontal = style.paddingHorizontal,
                        vertical = style.paddingVertical
                    ),
                // 标签贴哪儿由档位说，不由这一颗盒子自己想
                contentAlignment = style.labelAlignment.labelPlacement
            ) {
                ChipLabel(shownLabel, style, textColorNow, textWeightNow)
            }
        }
    } else {
        // 单层：胶囊自己就是那颗可点节点，下限与底色落在同一颗身上
        Box(
            modifier = modifier
                .then(if (fixedPillHeight != null) Modifier.height(fixedPillHeight) else Modifier)
                .then(floorModifier)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(style.radius)
                .background(backgroundNow, style.radius)
                .border(AppDimens.BORDER_WIDTH_DP.dp, borderNow, style.radius)
                .then(actionModifier)
                .padding(
                    horizontal = style.paddingHorizontal,
                    vertical = style.paddingVertical
                )
                .testTag(LbTags.CHIP),
            contentAlignment = style.labelAlignment.labelPlacement
        ) {
            ChipLabel(shownLabel, style, textColorNow, textWeightNow)
        }
    }
}

@Composable
private fun ChipLabel(
    text: String,
    style: LbChipStyle,
    color: Color,
    weight: FontWeight
) {
    Text(
        text = text,
        style = style.textStyle,
        color = color,
        fontWeight = weight,
        maxLines = style.labelMaxLines
    )
}

/**
 * 一组芯片的换行容器（与 [LbChip] 同一位所有者）。
 *
 * 为什么它也在设计系统里：二级原因那一族原来自己开着一个 `FlowRow`——
 * "芯片排不下就换行、行距等于列距"这件事属于芯片族，不属于那一个面板。
 * 留在页面上就会长成第八颗异形（按形状认的那把尺连 `FlowRow(` 都算自画容器）。
 *
 * 这一颗只服务多选（[LbChipInteraction.Multi]）：单选的互斥关系要调用方自己决定
 * 哪一档算选中，横排还是换行由页面自己的行容器管，这里不替它决定。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LbChipGroup(
    labels: List<String>,
    isSelected: (String) -> Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = Spacing.xs,
    enabled: Boolean = true,
    style: LbChipStyle = LbChipStyles.filled
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        labels.forEach { label ->
            LbChip(
                label = label,
                selected = isSelected(label),
                enabled = enabled,
                onClick = { onToggle(label) },
                interaction = LbChipInteraction.Multi,
                style = style
            )
        }
    }
}
