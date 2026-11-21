package com.lovebrain.app.ui.common

import androidx.compose.runtime.Composable
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionSize
import com.lovebrain.app.core.designsystem.LbTextActionTone

/**
 * 行内次级操作：供应商行、知识库卡片那两族共用的一颗小动作。
 *
 * 自己不画任何东西，转进设计系统的文字动作 [LbTextAction]，并交 v1.3.1 那一档形状
 * [LbTextActionSize.RowCapsule]（32dp 最小高 + 浅灰胶囊 + full 圆角 + 水平 12/垂直 4dp 留白）。
 * 新写的行内动作请直接 call [LbTextAction] 并交 `tone` 与 `size`。
 */
@Composable
fun RowActionButton(
    text: String,
    onClick: () -> Unit
) {
    LbTextAction(
        label = text,
        onClick = onClick,
        tone = LbTextActionTone.RowSecondary,
        size = LbTextActionSize.RowCapsule
    )
}
