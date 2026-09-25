package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * §6.1 表里的 `LbTopBar`：标题、副标题、**单一**尾部动作。
 *
 * 搬过来第一件事是把它"焊死的东西"拆开：从 `ui/home` 搬来的第一版里写着
 * "LoveBrain"、"帮你更自然地表达"和那颗 About 图标——**设计系统认识了一个具体页面**，
 * 而那正是 §6.1 末句要拦的方向。现在标题/副标题是参数，尾部动作是槽位：
 * 首页往里放 About 入口（锚点 `LbHomeTags.ABOUT` 留在页面那边），
 * 二级页将来放返回——两张页面共用一颗组件，而不是共用一份文案。
 */
@Composable
fun LbTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                title,
                style = AppTypography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = AppTypography.bodySmall,
                    color = TextHint
                )
            }
        }
        trailing?.invoke()
    }
}
