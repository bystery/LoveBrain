package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * §6.1 表里的 `LbModalSheet/Dialog` 的 **Dialog 那一半**：需要用户决策的浮层。
 * （Sheet 那一半仍然没做——面板里那些"把展开内容直接插在原页面下方"的浮层归 §6.4，
 * 见交接单 §4；别把这格报成整行完成。）
 *
 * 为什么要收：这 11 处对话框原先各写各的——标题有的 `titleLarge`、有的 `titleMedium` 还额外加
 * `FontWeight.SemiBold`；正文有的 `bodyMedium`、有的 `labelSmall`、失败那两句是 `Error` 色；
 * 按钮有的带 `titleMedium`、有的裸 `TextButton`。这就是指导书说的"一功能一种格式"。
 *
 * **收的时候顺手修掉一条真的缺陷**：`androidx.compose.material3.AlertDialog` 里那颗
 * `TextButton` 的可点击盒子，本机语义树实量 **188x40dp**（`DialogProbeTest` 量出来的，
 * 见账本 §28.2），而 §6.5 :531 的下限是 48dp。也就是说仓库里 11 个浮层的"确定/取消"
 * 全部低于下限，而之前那把 48dp 的尺**从没往对话框里看过一眼**——
 * 它扫的是页面里的可交互节点，对话框是另一扇窗。
 * 所以这里给动作按钮垫了 `heightIn(min = 48dp)`，并由 `LbDialogTest` 逐颗读 `boundsInRoot` 钉住。
 *
 * 只用 Material 的 `AlertDialog` 做窗口骨架（遮罩、返回键、焦点、进出场），
 * 语义与样式全部由这一处决定；生产里除本文件之外不许再直接 call `AlertDialog`
 * （`UiLayerDependencyContractTest` 那条"只有一个所有者"的闸盯着这件事）。
 */

/** 对话框里那颗动作按钮的可点击盒子下限——实测 Material 自己只给到 40dp */
const val LB_DIALOG_ACTION_MIN_DP = AppDimens.TOUCH_TARGET_MIN_DP

/**
 * 正文语气：失败那两类要念得比说明更重。
 *
 * 颜色写在下面那个扩展里而不是枚举构造参数里——`Error(Error)` 这种写法，
 * 括号里那个 `Error` 会被解析成**枚举项自己**而不是同名的顶层颜色 val
 * （上一格 `Primary(Primary)` 编译不过，同一个坑；小表两个以上成员撞名时别再踩）。
 */
enum class LbDialogMessageTone { Plain, Error }

internal val LbDialogMessageTone.color: Color
    get() = when (this) {
        LbDialogMessageTone.Plain -> TextSecondary
        LbDialogMessageTone.Error -> com.lovebrain.app.core.designsystem.Error
    }

/**
 * 一个决策出口（确定 / 取消 / 删除 / 仍要导出…）。
 *
 * 着色写在 [LbDialogActionTone] 这张小表里，而不是让调用方直接交 `color`——
 * 与 `LbSettingRow`、`LbPrimaryButton` 同一口径：**颜色来自词表，不来自参数**。
 *
 * [enabled] 与上一格删掉的 `mode` + `enabled` 不是同一件事：那颗主动作的 `enabled` 与
 * `mode` 编码的是**同一根轴**（我处在哪个状态），所以并成一颗旋钮；这里的 `enabled` 是
 * **另一根轴**（表单此刻填没填满，比如"显示名不许为空"），跟"这是哪一颗按钮"无关。
 */
data class LbDialogAction(
    val label: String,
    val onClick: () -> Unit,
    val tone: LbDialogActionTone = LbDialogActionTone.Accent,
    val enabled: Boolean = true
)

enum class LbDialogActionTone(internal val color: Color) {
    /** 默认 affirmative：Primary */
    Accent(Primary),

    /** 不可恢复的那一颗：Error（删除、清空） */
    Destructive(Error),

    /** 取消 / 关闭：TextSecondary，视觉上退一步 */
    Muted(TextSecondary)
}

@Composable
fun LbDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    messageTone: LbDialogMessageTone = LbDialogMessageTone.Plain,
    confirm: LbDialogAction? = null,
    dismiss: LbDialogAction? = null,
    /**
     * 挂在 `dismiss` 同一行的次级出口（导出预览那颗"分享/复制"就是这个形状）。
     *
     * 为什么不干脆让调用方自己往 `dismissButton` 里塞一个 Row：那等于把"浮层里有几排按钮、
     * 每排多大"又交回页面自己决定，正是 §6.1 要收的东西。
     * 上限写死在这里：**一个主动作 + 最多三个次级**，再多就说明这不该是个对话框（该走 Sheet）。
     */
    secondary: List<LbDialogAction> = emptyList(),
    body: (@Composable () -> Unit)? = null
) {
    require(secondary.size <= 3) {
        "LbDialog 的次级出口最多 3 个（实到 ${secondary.size}）；再多请改用浮层 Sheet 或独立 screen"
    }
    // 先把正文收成**一个**带类型的局部：直接在 `text = when { … }` 里返回嵌套 lambda，
    // @Composable 注解会在 when 的公共父类型上丢掉，编译器就报「() -> Unit? 但需要 @Composable () -> Unit」
    val content: (@Composable () -> Unit)? = when {
        body != null -> {
            body
        }
        message != null -> {
            @Composable {
                Text(
                    text = message,
                    style = AppTypography.bodyMedium,
                    color = messageTone.color
                )
            }
        }
        else -> null
    }
    val trailing = secondary + listOfNotNull(dismiss)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = { Text(text = title, style = AppTypography.titleLarge, color = TextPrimary) },
        text = content,
        // 槽位**永远交一个 composable lambda**，里面判空——写成 `confirm?.let { { … } }` 的话
        // let 推出来的是普通 `() -> Unit`，@Composable 注解在中间那一层丢了，编译器不认
        confirmButton = { confirm?.let { LbDialogActionCell(it) } },
        dismissButton = if (trailing.isEmpty()) null else {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    trailing.forEach { LbDialogActionCell(it) }
                }
            }
        }
    )
}

@Composable
private fun LbDialogActionCell(action: LbDialogAction) {
    TextButton(
        onClick = action.onClick,
        enabled = action.enabled,
        // 热区垫到 §6.5 的下限：Material 自己给的是 40dp，而这是用户要点到的那一颗
        modifier = Modifier.heightIn(min = LB_DIALOG_ACTION_MIN_DP.dp)
    ) {
        Text(
            text = action.label,
            color = if (action.enabled) action.tone.color else TextHint,
            style = AppTypography.titleMedium,
            // 表里其它对话框的标题/按钮带过 SemiBold；按钮这一层统一成同一个字重，
            // 免得"同一颗确定"在两个页面长得不一样（§6.1 末句那条禁令）
            fontWeight = FontWeight.SemiBold
        )
    }
}
