package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.model.RewriteCommand

/**
 * 方案卡调整态展示块——从 SchemeCard 抽离的纯展示子组件。
 *
 * 调整态：标签 + 改写选项（预设 chips）+ 自定义输入 + 取消。
 * - 不显示方向 chips（方向属于 Result-level）
 * - 替换内容而非追加
 *
 * 视觉与原 SchemeCard 内联实现完全一致；modifier（含 weight）由调用方传入。
 */
@Composable
internal fun SchemeAdjustingBlock(
    onRewrite: (RewriteCommand) -> Unit,
    onCustomRewrite: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customText by remember { mutableStateOf("") }
    // semantics 的 lambda 不是 @Composable：读屏名字在外面取好再闭包进去
    val customHint = stringResource(R.string.scheme_custom_hint)
    var showCustomInput by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // 预设选项
        val presetCommands = RewriteCommand.entries.filter { it != RewriteCommand.CUSTOM }
        val chunkedRows = presetCommands.chunked(2)
        chunkedRows.forEachIndexed { rowIndex, rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                rowOptions.forEach { command ->
                    val (interaction, optScale) = rememberPressScale(0.94f, "optScale${command.label}")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { scaleX = optScale; scaleY = optScale }
                            .clip(LoveBrainShape.sm)
                            .background(PrimaryLight, LoveBrainShape.sm)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onRewrite(command) }
                            )
                            .padding(vertical = Spacing.xs, horizontal = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            command.label,
                            style = AppTypography.labelSmall,
                            color = PrimaryDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (rowIndex < chunkedRows.lastIndex) {
                Spacer(Modifier.height(Spacing.xs))
            }
        }
        // 自定义改写入口
        if (!showCustomInput) {
            val (customInteraction, customScale) = rememberPressScale(0.94f, "customBtnScale")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = customScale; scaleY = customScale }
                    .clip(LoveBrainShape.sm)
                    .background(SurfaceInset, LoveBrainShape.sm)
                    .border(1.dp, Border, LoveBrainShape.sm)
                    .clickable(
                        interactionSource = customInteraction,
                        indication = null,
                        onClick = { showCustomInput = true }
                    )
                    .padding(vertical = Spacing.xs, horizontal = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "自定义要求…",
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    maxLines = 1
                )
            }
        } else {
            // 自定义输入框
            // §6.5 第②栏：placeholder 是"举个例子"，不是这格**是什么**；
            // 而且用户敲进第一个字之后连举例那行都不在树上了。
            // 所以名字用"自定义改写要求"这句（只给读屏用，屏幕上不新增字）
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = customHint },
                placeholder = {
                    Text(
                        "如：保留第一句，第二句不要",
                        style = AppTypography.labelSmall,
                        color = TextHint
                    )
                },
                textStyle = AppTypography.labelSmall.copy(color = TextPrimary),
                singleLine = false,
                maxLines = 3,
                shape = LoveBrainShape.sm
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "确认改写",
                    style = AppTypography.labelSmall,
                    color = if (customText.isNotBlank()) PrimaryDark else TextHint,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (customText.isNotBlank()) {
                                onCustomRewrite(customText.trim())
                            }
                        }
                    ).padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // 取消/返回
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "取消",
                style = AppTypography.labelSmall,
                color = TextHint,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel
                ).padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            )
        }
    }
}
