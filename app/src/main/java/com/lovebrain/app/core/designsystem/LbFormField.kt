package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * 所在字段带不带错误——由 [LbFormField] 提供给字段体内的控件。
 *
 * 为什么走 CompositionLocal 而不是给 [LbFieldInput] 开一颗 `error` 参数：
 * 错误的**归属**在字段（基线 v1 §3.7 / D1 §② C7——"错误行不钉在地址下面"），
 * 描边转 `Error` 只是字段状态在框上的投影。开成参数等于让页面自己决定"哪颗框该红"，
 * 而 C7 的病根恰恰就是页面各自钉锚点。字段说一次，框跟着变，中间没有第二个旋钮。
 */
val LocalLbFieldHasError = compositionLocalOf { false }

/**
 * 表单字段三段节奏的唯一主人（基线 v1 §3.1/§3.7，采纳依据 D1 参考池 #6：NN/g 邻近律）。
 *
 * 内部版式只有一份：
 * 标签（`labelMedium` 11sp、`TextSecondary`）→ [Spacing.sm] 4dp → 控件 →
 * [Spacing.sm] 4dp → 错误行（`labelMedium` 11sp、`Error`，仅在有错误时在场）。
 * 邻近律的判据是**顺序**：标签到字段（4）必须比字段到字段（8，归 [LbFormGroup] 管）更近，
 * 标签才读得出"我在说这一颗"。把 4 抬成与字段间距同档，就退回 D1 §② C1 的病
 * （标签与字段同距 = 视觉上不存在分组）——这一族的守卫格钉的正是这两颗数不许相等。
 *
 * 间距只用 [Spacing.sm]/[Spacing.md]/[Spacing.xl]（4/8/16）：不新增标尺值。
 *
 * @param label 字段标签，必填，由调用方交（设计系统不持词表）。
 * @param error 错误文案；null/空白 = 无错误，错误行整行不进版式（不是画一颗透明的占位）。
 *   文案归属在这里，不在任何框体里——读它永远比读描边准。
 * @param trailingSlot 标签行尾部的轻量件（如"选填"或就地动作）；不传就不占行。
 *   控件**自身**的尾部槽（Key 显隐那种 40dp 槽）归 [LbFieldInput] 的 trailingAction，
 *   两颗不是同一件事：那颗在框里，这颗在标签行上。
 * @param content 控件本体。[LbFieldInput] 放这里时会自动接上错误描边位（经 [LocalLbFieldHasError]）。
 */
@Composable
fun LbFormField(
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    trailingSlot: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val errorText = error?.takeIf { it.isNotBlank() }
    val showError = errorText != null
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = AppTypography.labelMedium,
                color = TextSecondary
            )
            if (trailingSlot != null) {
                Box(
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    trailingSlot()
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        CompositionLocalProvider(LocalLbFieldHasError provides showError) {
            content()
        }
        if (showError) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = errorText!!,
                style = AppTypography.labelMedium,
                color = Error,
                // 不裁行：裁掉半句等于丢话（与 LbEmptyState 通知条同一条纪律，指导书 §7.5）；
                // 上限 2 行是本族行档，超长进 2 行后 ellipsis 收尾但不吞首句。
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
