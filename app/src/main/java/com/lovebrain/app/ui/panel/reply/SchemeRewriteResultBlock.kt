package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.lovebrain.app.core.designsystem.*

/**
 * 改写结果在卡片上只剩**一句简短反馈**这一件事。
 *
 * 原来这一档画的是两坨东西：成功后是"新正文 + 新旧对比 + 返回原版/用这版"那条工具条，
 * 失败后是一整块居中的错误盒（里面还有一颗自己的"重试"）。现在两条都不画——
 * 成功就是那张默认卡换了正文，失败/停止就是默认卡恢复旧正文、下面多这一行字。
 * 历史与恢复用的数据仍在数据层（`RewriteStore` 那一族），这里少画一栏不等于删掉那份能力。
 */
@Composable
internal fun SchemeRewriteNotice(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        style = AppTypography.labelSmall,
        color = Error,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth()
    )
}
