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
    Muted
}

// 底色与字号写成扩展而不是枚举构造参数：构造参数里写 `Accent(Primary)` 时那个 `Primary`
// 会被解析成枚举项自己，不是同名的顶层颜色 val（`LbButtonTone` 上已经踩过一次）。
internal val LbTextActionTone.ink: Color
    get() = when (this) {
        LbTextActionTone.Accent -> Primary
        LbTextActionTone.Muted -> TextHint
    }

/**
 * 语气带着字号一起走。
 *
 * 不这么做的话，「跳过」为了复用这颗组件就得从 `labelMedium` 长成 `labelLarge`——
 * 那就是"复用"顺手改了一次外观。字号与颜色同属一种语气，就该同进同退。
 */
internal val LbTextActionTone.style: TextStyle
    get() = when (this) {
        LbTextActionTone.Accent -> AppTypography.labelLarge
        LbTextActionTone.Muted -> AppTypography.labelMedium
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
