package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * 文字动作的热区下限——**不另抄一个数**，就是全局那颗 [AppDimens.TOUCH_TARGET_MIN_DP]。
 *
 * 这颗数在本仓库只许写一次，理由见 Dimens.kt 上那段注释。
 */
const val LB_TEXT_ACTION_MIN_DP = AppDimens.TOUCH_TARGET_MIN_DP

/**
 * 语气。和 [LbButtonTone] 同理：**颜色来自词表，不是来自参数**（§6.1 :490 末句
 * "不许只在一个页面看起来不一样"——如果把 `color: Color` 开成参数，下一颗就会长成第三种颜色）。
 */
enum class LbTextActionTone {
    /** 这页/这块的引导动作：空态"去添加"、错误态"重试" */
    Accent,

    /** 次要到可以忽略：「跳过」「以后再说」 */
    Muted,

    /**
     * 行/卡里那一颗次级动作：「编辑」「导出」。
     *
     * 这一档是为 §6.1 归并 `RowActionButton` 加的，理由要写清：那颗小按钮自己画
     * `Box + Text + clickable`，热区只垫了高度（短标签本机实量 **32x48dp**），
     * 而且 `clickable` 没声明角色 —— 读屏只念那两个字，不念"按钮"。
     * 这两样正是本组件已经替全站修过一遍的。加一档**语气**而不是给它开一个
     * `color:` 旋钮，是因为旋钮一开就会长出第三种颜色（:490 末句）。
     */
    RowSecondary,

    /** 会把东西删掉/停用的那一行内动作：「删除」——字用错误色，热区与角色与上一档**完全一样** */
    Destructive
}

// 底色与字号写成扩展而不是枚举构造参数：构造参数里写 `Accent(Primary)` 时那个 `Primary`
// 会被解析成枚举项自己，不是同名的顶层颜色 val（`LbButtonTone` 上已经踩过一次）。
internal val LbTextActionTone.ink: Color
    get() = when (this) {
        LbTextActionTone.Accent -> Primary
        LbTextActionTone.Muted -> TextHint
        LbTextActionTone.RowSecondary -> TextSecondary
        LbTextActionTone.Destructive -> Error
    }

/**
 * 语气带着字号一起走。
 *
 * 不这么做的话，「跳过」为了复用这颗组件就得从 `labelMedium` 长成 `labelLarge`——
 * 那就是"复用"顺手改了一次外观。字号与颜色同属一种语气，就该同进同退。
 *
 * `RowSecondary` / `Destructive` 两档沿用被合并那颗原来的 `bodyMedium`（13sp）：
 * 归并换的是**所有者**，不是把行内动作顺手改成引导动作的字号。
 */
internal val LbTextActionTone.style: TextStyle
    get() = when (this) {
        LbTextActionTone.Accent -> AppTypography.labelLarge
        LbTextActionTone.Muted -> AppTypography.labelMedium
        LbTextActionTone.RowSecondary -> AppTypography.bodyMedium
        LbTextActionTone.Destructive -> AppTypography.bodyMedium
    }

/**
 * 一颗**只有文字、没有底色**的动作。
 *
 * §6.1 那张表里"文字动作"这个词是设计系统自己承认的（`LbEmptyState` 那行：
 * "图标、主说明、可选文字动作；动作热区 ≥48dp"），但在这一格之前它**只在
 * `LbEmptyState` 内部存在过**——页面想要一颗别的文字动作就没有地方放，于是
 * 每页自己画一个 `Text(...).clickable {}`。本格起因就是首次引导那颗「跳过」：
 * 语义树实量 **38x25dp**，而它是这屏唯一能让人退出流程的出口。
 *
 * 三件事由组件保证，调用方拿不到旋钮（拿到了就一定会有第二版长得不一样）：
 * 1. 热区 ≥ [LB_TEXT_ACTION_MIN_DP] **见方**——只垫高度不够，短标签会量出 40x48dp
 *    （`LbEmptyState` 的第一版就是这么被自家测试测红的，教训写在它旁边）；
 * 2. 声明 [Role.Button]——裸 `Text + clickable` 读屏不会念成按钮，只念那两个字；
 * 3. 按压缩放走全站那一处 [rememberPressScale]。
 *
 * `clickable` 必须排在任何 `padding` **之前**：排在后面等于自己把热区又削掉一圈。
 *
 * **现在这一颗是"文字动作"唯一的主人**（§6.1 归并）：`ui/common/RowAction.kt` 那颗
 * 行内小按钮（`RowActionButton`）已经改成转进这里，于是它上面那三条一起补齐。
 * 代价如实记在这儿：那一颗原来有一层浅灰胶囊底（`TextSecondary` 8% 不透明），
 * 本组件的契约是"只有文字、没有底色"——要把底色带进来就得开一个 `containerColor` 旋钮，
 * 而那一开就等于同意"下一颗可以再长一种底色"。所以底色随归并一起消失，
 * 字号与颜色则留在语气词表里（[LbTextActionTone.RowSecondary] / [LbTextActionTone.Destructive]），
 * 同进同退那条规矩一个字没破。
 */
@Composable
fun LbTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LbTextActionTone = LbTextActionTone.Accent
) {
    val (interaction, scale) = rememberPressScale(0.96f, "lbTextActionScale")
    Box(
        modifier = modifier
            .heightIn(min = LB_TEXT_ACTION_MIN_DP.dp)
            .widthIn(min = LB_TEXT_ACTION_MIN_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = tone.style,
            color = tone.ink,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
    }
}
