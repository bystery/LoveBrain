package com.lovebrain.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.TextSecondary

/**
 * 行内次级操作：供应商行、知识库卡片那两族共用的一颗小动作。
 *
 * **§6.1 归并之后它不再自己画任何东西**，而是转进设计系统那一颗文字动作 [LbTextAction]。
 * 归属为什么是"文字动作"而不是"行"（`LbSettingRow`）：
 * - 它自己就是"行的尾部那一颗"，没有标题、没有说明、没有状态可归——
 *   `LbSettingRow` 的尾部槽（`trailingText` / `onTrailingClick`）只在"整行"存在时才有意义，
 *   而这两个调用点一个在自画的 `KbCard` 里、一个在 `ProviderSection` 的编辑态行里，
 *   把它们连行一起搬进行组件是另一格的事（`KbCard` / `MiniSwitchRow` 还挂在异形账上）；
 * - 表里"文字动作"这个词有主人之后（§6.1 `LbEmptyState` 那行："可选文字动作；动作热区 ≥48dp"），
 *   页面再自己画一颗 `Box + Text + clickable` 就是同一件事的第二份实现。
 *
 * 本机实量的旧形状（`RowActionSemanticsTest` 在归并之前那轮读到的，记在这儿免得被当成推测）：
 * 标签一个字符时 **32x48dp**、`role=无`——只垫了高度，而且读屏念不出"按钮"。
 * 这两样由 `LbTextAction` 统一补齐（见方 + `Role.Button` + 全站那一处按压缩放）。
 * 一起消失的还有那层浅灰胶囊底：本组件的契约是"只有文字、没有底色"，
 * 要为它开一个底色旋钮，等于同意下一颗再长一种底色（§6.1 :490 末句）。
 *
 * ⚠ `tint` 这一旋钮是**留给两个旧调用点的兼容口子**，不是"颜色还能自己交"：
 * `ProviderSection.kt` 与 `KnowledgeBaseActivity.kt` 此刻在别的窗口手里，改不到它们的调用点，
 * 所以旧的 `tint = Error` 必须还有人接。它现在只用来**选语气档**（见 [rowActionTone]），
 * 不再直接当颜色用。新写的行内动作请直接 call [LbTextAction] 并交 `tone`。
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
        tone = rowActionTone(tint)
    )
}

/**
 * 旧 `tint` → 语气档。全仓两个调用点只有两支：默认（`TextSecondary`）与 `tint = Error`。
 *
 * 单列成一颗纯函数是为了能被直接判：语义树里没有颜色与字号这两栏，
 * "删除那颗走错误色"这个说法在树上量不到，只能这样留下证人（同一件事写在
 * `LbTextActionTest` 第二格的注释里）。
 */
internal fun rowActionTone(tint: Color): LbTextActionTone =
    if (tint == Error) LbTextActionTone.Destructive else LbTextActionTone.RowSecondary
