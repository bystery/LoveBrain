package com.lovebrain.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.theme.AppTypography
import com.lovebrain.app.ui.theme.Border
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.SurfaceBase
import com.lovebrain.app.ui.theme.TextPrimary
import com.lovebrain.app.ui.theme.TextSecondary

/**
 * 全 App 唯一页头规格（主人 2026-08-31 定稿）：
 *   顶部留白 24dp → 页头行（返回钮 + 18sp 标题）高 48dp → 1dp 浅分割线 → 内容距分割线 16dp。
 *   所有二级页（知识库管理/编辑知识库/设置页/新建知识库问卷）走 [ScreenPage]，不再各写各的。
 */
private object ScreenHeaderDimens {
    const val HEADER_ROW_HEIGHT_DP = 48   // 页头行固定高
    const val BACK_HOTZONE_DP = 32        // 返回箭头热区
    const val BACK_ICON_DP = 22           // 返回箭头字形
    const val TITLE_GAP_DP = 2            // 返回箭头与标题间距
    const val DIVIDER_HEIGHT_DP = 1       // 标题下浅分割线
}

/**
 * 页头行：返回箭头 + 标题（18sp，左对齐）+ 可选尾部操作；下接 1dp 浅分割线。
 * 不直接使用——由 [ScreenPage] 统一承载，保证全站规格一致。
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(ScreenHeaderDimens.HEADER_ROW_HEIGHT_DP.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(
                        width = ScreenHeaderDimens.BACK_HOTZONE_DP.dp,
                        height = ScreenHeaderDimens.BACK_HOTZONE_DP.dp
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = TextSecondary,
                    modifier = Modifier.size(ScreenHeaderDimens.BACK_ICON_DP.dp)
                )
            }
            Spacer(modifier = Modifier.width(ScreenHeaderDimens.TITLE_GAP_DP.dp))
            Text(
                title,
                style = AppTypography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            trailing?.let { it() }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScreenHeaderDimens.DIVIDER_HEIGHT_DP.dp)
                .background(Border.copy(alpha = 0.6f))
        )
    }
}

/**
 * 全 App 唯一页面骨架：背景 + 顶部 24dp 留白 + [ScreenHeader]（48dp 行 + 分割线）+ 16dp 内容距。
 * 调用方在 [content] 内自行滚动与布局（[ColumnScope] 便于用 weight 占满剩余空间）。
 */
@Composable
fun ScreenPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceBase)
            .padding(horizontal = Spacing.xxxl, vertical = Spacing.xxxl)
    ) {
        ScreenHeader(title = title, onBack = onBack, trailing = trailing)
        Spacer(Modifier.height(Spacing.xl))
        content()
    }
}
