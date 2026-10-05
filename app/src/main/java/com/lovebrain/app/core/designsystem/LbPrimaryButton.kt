package com.lovebrain.app.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * `LbPrimaryButton`——页面唯一主动作，**四态一旋钮**。
 *
 * 状态只用 `LbButtonState` **一个参数**表达，不用 `mode` + `enabled` 两个平行旋钮：
 * 两张表管一件事就必有说不清的那一格（`STOP` + `enabled = false` 这类组合在类型上合法、
 * 在生产里没人约定过含义，而"禁用"其实是 NORMAL 的一个分支）。收成一颗后非法组合不可表达。
 *
 * 还有一件刻意**没**放进来的事：Loading 那串"分析对话 · 7s 点击停止"的**计时与阶段词**。
 * 设计系统不该知道"生成"这件事（把某一屏的文案烤进 core 就是要拦的写法）。
 * 所以这里只画四态共有的那层壳——那一档的可见高度、形状、着色、按压反馈、脉冲与进度环、
 * 停止锚点——**说什么由调用方决定**（`label` 传进来）。
 *
 * 触摸区与版式高度是**两条轴**，这里各写各的：
 * · **热区那条**：默认档 [LB_PRIMARY_MIN_HEIGHT_DP] 仍指回全站那一颗 [AppDimens.TOUCH_TARGET_MIN_DP]，
 *   除面板工具条之外的每一颗按钮都走它，一寸没动；
 * · **版式那条**：[LbButtonHeightTier] 给出三个**具名档**。调用方拿到的是档位，不是一个自由的
 *   `Dp`——`heightDp` 那种参数只能把按钮改高、改不矮，等于不存在的自由度，
 *   所以它不回来；要矮就得说出"矮在哪一档、为什么这一档可以矮"。
 */

/** 默认档的可点击盒子下限，也就是全站那一颗触摸下限；数指回全局那颗，不另抄一遍 */
const val LB_PRIMARY_MIN_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP

/**
 * 主动作的**可见高度档位**（版式那条轴）。
 *
 * 三档里只有 [Standard] 等于全局热区下限；另两档是**明写的例外**，各自低于
 * [AppDimens.TOUCH_TARGET_MIN_DP]，都只服务于面板那条紧凑工具条：
 * 上面 30dp 的模式栏、下面 28dp 的角色 chip 与调整胶囊，主动作单独撑到 48 就会把那排挤出可视区。
 * 例外只降低**这一档**的高度，[AppDimens.TOUCH_TARGET_MIN_DP] 本身没被改，
 * 其它场景（页面主体、列表行、弹窗按钮、首页 Hero、向导）继续走 [Standard]。
 *
 * 宽度这一轴**不跟着降**：见方热区那条教训（短标签的「保存」量出 33x48dp）说的是两条边都得垫，
 * 紧凑合同也只说了高度，没说可以把一颗按钮收成一条窄条。
 *
 * 哪天确实要让"点得到的面积"大于"看得见的框"，走 `CompactInput` / `PanelHeader` 那套
 * 已经立过的两层写法（外层透明热区 + 内层可见形状，语义挂在带 clickable 的那一层自己身上），
 * 不在这里另发明第三套。
 */
enum class LbButtonHeightTier(val minHeightDp: Int) {

    /** 默认档 = 全站触摸下限：除了面板那一排紧凑工具条，所有调用方走这档 */
    Standard(LB_PRIMARY_MIN_HEIGHT_DP),

    /** 面板主动作档：生成 / 生成中 / 停止共用那一颗槽位，整排对齐 [AppDimens.PANEL_PRIMARY_ACTION_HEIGHT_DP] */
    PanelPrimaryAction(AppDimens.PANEL_PRIMARY_ACTION_HEIGHT_DP),

    /** 面板"有结果"那一排的并列双出口档：重试 / 记入知识库，各占一半宽 */
    PanelResultActionPair(AppDimens.PANEL_RESULT_ACTION_HEIGHT_DP)
}

/** 四态：一旋钮，替代旧的 `ButtonMode` + `enabled` 两个平行旋钮 */
enum class LbButtonState { Idle, Loading, Disabled, Stop }

/**
 * 着色调性。颜色来自这张两档小表，不是来自 `containerColor` 参数——
 * 一开参数就许了"只在一个页面看起来不一样"。
 *
 * 底色写在下面那个扩展里而不是枚举构造参数里：构造参数里 `Primary(Primary)`
 * 的第二个 `Primary` 会被解析成**枚举项自己**而不是同名的顶层颜色 val（编译期就报类型不符）。
 */
enum class LbButtonTone { Primary, Deep }

internal val LbButtonTone.container: Color
    get() = when (this) {
        LbButtonTone.Primary -> Primary
        LbButtonTone.Deep -> PrimaryDark
    }

@Composable
fun LbPrimaryButton(
    state: LbButtonState,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LbButtonTone = LbButtonTone.Primary,
    heightTier: LbButtonHeightTier = LbButtonHeightTier.Standard
) {
    // 可点击盒子必须是整档高度：内边距放在 clickable **之后**，
    // 放在之前就等于自己把热区削掉一圈（旧组件修过一次，别再改回去）。
    val haptics = LocalHapticFeedback.current
    val (interaction, scale) = rememberPressScale(0.96f, "lbPrimaryScale")
    // 热区两**条边**都得垫：只写 `.height(48)` 时短标签的主动作会在宽度轴不达标
    // ——验收线要的是"无小于 48dp 的热区"，不是"无矮于 48dp"。`LbTextAction` 上是同一课。
    // 整宽的那些（`fillMaxWidth` / `weight(1f)`）不受影响：min 只抬高不裁宽。
    // ⚠ 只有**高度**跟着档位走，宽度那一轴始终用 [LB_PRIMARY_MIN_HEIGHT_DP]：
    // 紧凑合同说的是"这一排 40dp/36dp 高"，没说可以把一颗按钮收成窄条，见方的那条教训不跟着降档。
    val base = modifier
        .heightIn(min = heightTier.minHeightDp.dp)
        .widthIn(min = LB_PRIMARY_MIN_HEIGHT_DP.dp)
    val container = tone.container

    when (state) {
        LbButtonState.Loading -> {
            val transition = rememberInfiniteTransition(label = "pulse")
            val overlayAlpha by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0.22f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "overlayAlpha"
            )
            Box(
                modifier = base
                    .clip(LoveBrainShape.md)
                    .background(container, LoveBrainShape.md)
                    .clickable(role = Role.Button, onClick = onClick)
                    // 文字会变，tag 不会：自动化找的是这颗停止条，不是那句中文。
                    // ⚠ tag 必须挂在这颗**带 clickable 的盒子上**，不能挂在里面那行文字上：
                    // clickable 会把后代的语义合并进自己，子节点在**合并后的语义树**里根本不存在，
                    // 而 `onNodeWithTag` 默认查的就是合并树——设备侧三格红
                    // （OverlayGenerateSmokeTest > successStream_… / stopDuringGeneration_…、
                    // ReplyPrimaryActionsTest > generating_showsProductionLoadingStopAffordance…）
                    // 全都停在「节点不存在」。读屏同理：停止这条动作得自己带名字。
                    .testTag(LbTags.PRIMARY_STOP)
                    .paddingInside(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .background(PrimaryDark, LoveBrainShape.md)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(Spacing.xl),
                        strokeWidth = Spacing.xs
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = label,
                        color = Color.White,
                        style = AppTypography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LbButtonState.Stop -> Box(
            modifier = base
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(LoveBrainShape.md)
                .background(Neutral200, LoveBrainShape.md)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .paddingInside(),
            contentAlignment = Alignment.Center
        ) {
            LbPrimaryLabel(label, Color.White)
        }

        LbButtonState.Disabled -> Box(
            modifier = base
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(LoveBrainShape.md)
                .background(SurfaceInset, LoveBrainShape.md)
                .clickable(
                    enabled = false,
                    interactionSource = interaction,
                    indication = null,
                    // 禁用态也要报角色：读屏用户得知道"这里是一颗按钮，只是现在不能按"，
                    // 而不是听到一段没有名字的文字。
                    role = Role.Button,
                    onClick = onClick
                )
                .paddingInside(),
            contentAlignment = Alignment.Center
        ) {
            LbPrimaryLabel(label, TextSecondary)
        }

        LbButtonState.Idle -> Box(
            modifier = base
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md)
                .clip(LoveBrainShape.md)
                .background(container, LoveBrainShape.md)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .paddingInside(),
            contentAlignment = Alignment.Center
        ) {
            LbPrimaryLabel(label, Color.White)
        }
    }
}

/** 四态共用的那颗标签样式：一处改，四态一起改 */
@Composable
private fun LbPrimaryLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = AppTypography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * 内容层的内边距——**必须排在 `clickable` 之后**：
 * 排在之前就只有 `48 - 2*xs` 能点到，等于自己把热区削掉一圈。
 * 抽成一个函数是为了让"四态都用同一条顺序"这件事写在名字里，而不是散在四段代码里。
 *
 * **横向这一条别再删回去。**没有横向内边距时，"按内容排"的调用点上盒宽 == 字宽，
 * 标签直接涂在品牌色底色的边上。它只在**不给宽度约束**的调用点上显形——
 * 整宽的那些（`fillMaxWidth()` / `weight(1f)`）本来就有富余。
 *
 * 取 `Spacing.xl`（16dp）而不是页面边距那一档 `Spacing.xxxl`（24dp）：前者是本系统里
 * 卡片/行的内边距档（`LbActionCard`、`LbMetricGrid`），后者是整页的水平留白，
 * 一颗按钮不该比页面留白还宽。
 */
private fun Modifier.paddingInside(): Modifier =
    this.padding(horizontal = Spacing.xl, vertical = Spacing.xs)
