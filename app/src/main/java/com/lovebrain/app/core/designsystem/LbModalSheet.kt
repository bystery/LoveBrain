package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * §6.1 表里 `LbModalSheet/Dialog` 的 **Sheet 半边**：悬浮窗世界里那套"需要用户决策的浮层"。
 *
 * 为什么它和 [LbDialog] 是两个形状而不是一个：面板与气泡跑在
 * `TYPE_APPLICATION_OVERLAY` 窗口里（`FloatingService.kt` 建那两个窗口各一处，
 * 类型名以那里为准，别照这里的注释信——这一句原先写的是
 * `TYPE_ACCESSIBILITY_OVERLAY`，是错的，账本 §36 记了怎么发现的），
 * 那里没有合适的 activity token，Material 的 `AlertDialog`（内部起一棵 Dialog 窗口）
 * 会直接抛 `WindowManager.BadTokenException`。所以这一套是**在同一棵 ComposeView 里自画**的
 * 遮罩 + 居中卡片——它是被平台约束逼出来的第二种形状，不是"有人又想自造一套"。
 * 两种形状共用同一份动作词表（[LbDialogAction] / [LbDialogActionTone]），
 * 所以"同一颗取消"在对话框和浮层里至少是同一个说法、同一种着色。
 *
 * 前身是 `ui/panel/PanelModalHost` + `PanelModalTitle` + `PanelModalActions`。
 * 搬进来时修掉三条**在这台仪器上量出来的**缺陷（旧形状的实测值见账本 §29.2）：
 *
 * ① 动作按钮只有 **26dp / 22dp 高**，低于 §6.5 :531 的 48dp 下限；
 * ② `confirmLabel = ""` 会画出一颗 **24x22dp、没有任何可读名字的**可点节点
 *    （读屏念不出、手指也点不准）——现在标签为空的动**根本不入树**；
 * ③ 遮罩与"拦截点击"的卡片本身都挂过 `clickable`，于是各成一个**无名 clickable 节点**
 *    （360x1000 那颗遮罩、和把标题当成按钮念出来的那颗卡片）。现在两处改用
 *    `pointerInput` + `detectTapGestures`：手势照拦，但不再对外声明"我是一颗可点的按钮"。
 *    用户要的出口是那颗有名字的"取消"，不是一整块屏幕。
 */

/** 浮层卡片的最大高度——超了就滚，不把面板顶出屏 */
private const val SHEET_MAX_HEIGHT_DP = 560

/**
 * 动作热区下限：**不自己抄一个数**，指回 [AppDimens.TOUCH_TARGET_MIN_DP]。
 * 保留这个名字是因为读调用方时要看得出"这是浮层动作的下限"，
 * 但 :596 那条验收线（"无小于 48dp 热区"）是全站口径——数写两遍就等于没有下限。
 */
const val LB_SHEET_ACTION_MIN_DP = AppDimens.TOUCH_TARGET_MIN_DP

/**
 * 一薄层浮层宿主：遮罩 + 居中卡片。
 *
 * [dismissable] 关掉时遮罩不再吞点击（用于"必须先做一个选择"那种流程）。
 */
@Composable
fun LbModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissable: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            // 不用 clickable：那会给一整块遮罩加上"可点击节点"语义，读屏就得多念一口
            // 没有名字的按钮（旧形状实测 360x1000dp）。手势留在 pointerInput 里。
            .pointerInput(dismissable) {
                if (dismissable) detectTapGestures(onTap = { onDismissRequest() })
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .heightIn(max = SHEET_MAX_HEIGHT_DP.dp)
                .verticalScroll(rememberScrollState())
                .clip(LoveBrainShape.lg)
                .background(SurfaceCard)
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
                // 卡片内部吞掉点击（点内容不该关浮层）——同理不声明成按钮
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(Spacing.lg)
        ) {
            content()
        }
    }
}

/** 浮层标题：与 [LbDialog] 的标题同一档字重，但浮层里更紧凑 */
@Composable
fun LbModalSheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = AppTypography.titleMedium,
        color = TextPrimary,
        fontWeight = FontWeight.Bold
    )
}

/**
 * 浮层的动作行。
 *
 * 传进来的 [LbDialogAction] 里**标签为空的会被丢掉**——这不是省事，是修缺陷 ②：
 * 旧形状会把 `confirmLabel = ""` 画成一颗 24x22dp、没有任何可读名字的按钮。
 * "这一档没有主动作"应当是**没有那颗节点**，而不是一颗点得到却说不出是什么的东西。
 */
@Composable
fun LbModalSheetActions(
    actions: List<LbDialogAction>,
    modifier: Modifier = Modifier
) {
    val shown = actions.filter { it.label.isNotBlank() }
    if (shown.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shown.forEach { action -> LbModalSheetActionCell(action) }
    }
}

@Composable
private fun LbModalSheetActionCell(action: LbDialogAction) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val (interaction, scale) = rememberPressScale(0.96f, "lbSheetAction")
    Text(
        text = action.label,
        style = AppTypography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = when {
            !action.enabled -> TextHint
            action.tone == LbDialogActionTone.Muted -> TextSecondary
            action.tone == LbDialogActionTone.Destructive -> Error
            else -> Color.White
        },
        modifier = Modifier
            .heightIn(min = LB_SHEET_ACTION_MIN_DP.dp)
            .clip(LoveBrainShape.sm)
            .background(
                when {
                    !action.enabled -> SurfaceInset
                    action.tone == LbDialogActionTone.Accent -> Primary
                    else -> Color.Transparent
                },
                LoveBrainShape.sm
            )
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,   // §6.5 :532；见 `DesignSystemRolesTest`
                enabled = action.enabled
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                action.onClick()
            }
            // 内边距排在 clickable 之后，热区才是完整的 48dp（§7.1 那条顺序红线）
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    )
}
