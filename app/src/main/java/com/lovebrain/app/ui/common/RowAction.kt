package com.lovebrain.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionSize
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.TextSecondary

/**
 * 行内次级操作：供应商行、知识库卡片那两族共用的一颗小动作。
 *
 * 自己不画任何东西，转进设计系统的文字动作 [LbTextAction]，并交 v1.3.1 那一档形状
 * [LbTextActionSize.RowCapsule]（32dp 最小高 + 浅灰胶囊 + full 圆角 + 水平 12/垂直 4dp 留白）。
 * 新写的行内动作请直接 call [LbTextAction] 并交 `tone` 与 `size`。
 *
 * ⚠ `tint` 是留给两个旧调用点的兼容口子，只用来**选语气档**（见 [rowActionTone]），不再直接当颜色用。
 */
@Composable
fun RowActionButton(
    text: String,
    tint: Color = TextSecondary,
    onClick: () -> Unit
) {
    LbTextAction(
        label = text,
        onClick = onClick,
        tone = rowActionTone(tint),
        size = LbTextActionSize.RowCapsule
    )
}

/**
 * 旧 `tint` → 语气档。全仓两个调用点只有两支：默认（`TextSecondary`）与 `tint = Error`。
 *
 * 单列成一颗纯函数是为了能被直接判：语义树里没有颜色与字号这两栏，
 * "删除那颗走错误色"这个说法在树上量不到，只能这样留下证人。
 */
internal fun rowActionTone(tint: Color): LbTextActionTone =
    if (tint == Error) LbTextActionTone.Destructive else LbTextActionTone.RowSecondary
