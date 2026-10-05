package com.lovebrain.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LbDialog
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextSecondary

/**
 * 无障碍隐私披露 Dialog——用户开启消息捕获**之前**展示的那一屏法律性长文。
 *
 * 这一屏是**法律性的长文**，不是"一句说明"，所以走 [LbDialog] 的 `body` 槽而不是 `message`；
 * 两颗出口的着色由 `LbDialogActionTone` 决定（原来这里自己写了 `color = Primary` 与字重）。
 *
 * ## 这条链现在住在哪儿（别再把它删成孤儿）
 *
 * 唯一的调用点是 `CaptureAppsScreen`（"消息捕获"子页）：首页那段"服务设置"随首页重做删掉时，
 * 这一颗整块失去过调用者——那不等于这段告知可以没有，只等于它断链了。
 * 判据（合同 第7节第1条 末段"不得静默开权限"）与那三步顺序写在 `CaptureConsentGate.kt`，
 * 用例钉在 `HomeStatusCaptureDisclosureTest`。
 *
 * @param onAgree **明确同意**这一版披露：调用方先写同意记录，才允许进系统设置那一步
 * @param onDismiss 取消 / 返回键 / 点遮罩：调用方什么都不许做（不写记录、不跳设置、不改开关）
 */
@Composable
fun AccessibilityDisclosureDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    LbDialog(
        title = stringResource(R.string.capture_disclosure_title),
        modifier = Modifier.testTag(LbCaptureTags.DISCLOSURE),
        onDismissRequest = onDismiss,
        confirm = LbDialogAction(stringResource(R.string.capture_disclosure_agree), onAgree),
        dismiss = LbDialogAction(stringResource(R.string.a11y_action_cancel), onDismiss, tone = LbDialogActionTone.Muted),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.capture_disclosure_reads_events), style = AppTypography.bodyMedium, color = TextSecondary)
                Text(stringResource(R.string.capture_disclosure_purpose), style = AppTypography.bodyMedium, color = TextSecondary)
                Text(stringResource(R.string.capture_disclosure_sends_provider), style = AppTypography.bodyMedium, color = TextSecondary)
                Text(stringResource(R.string.capture_disclosure_writes_kb), style = AppTypography.bodyMedium, color = TextSecondary)
                HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border.copy(alpha = 0.5f))
                Text(stringResource(R.string.capture_disclosure_no_autoclick), style = AppTypography.labelMedium, color = TextHint)
            }
        }
    )
}
