package com.lovebrain.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.ui.common.CompactInput
import com.lovebrain.app.ui.theme.AppDimens
import com.lovebrain.app.ui.theme.AppTypography
import com.lovebrain.app.ui.theme.Border
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.SurfaceInset
import com.lovebrain.app.ui.theme.TextHint
import com.lovebrain.app.ui.theme.TextPrimary
import com.lovebrain.app.ui.theme.TextSecondary
import com.lovebrain.app.viewmodel.SetupViewModel

/**
 * P3-05: 消息捕获 allowlist 选择页。
 *
 * 复核报告 §7.2 的事实是：代码里只有关键词 blocklist，没命中的任意 App 都能进入捕获逻辑。
 * 现在捕获默认 fail-closed，所以必须有一个页面让用户真正点名授权哪些 App——
 * 否则"最小化采集"仍然只是文档上的说法。
 */
@Composable
fun CaptureAppsScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit
) {
    val allowed by viewModel.captureAllowedPackages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 包列表只在进入本页时枚举一次；queryIntentActivities 是同步 IPC，放 remember 里避免每帧重算
    val candidates = remember { viewModel.selectableCaptureTargets(context) }
    var query by remember { mutableStateOf("") }

    ScreenPage(title = stringResource(R.string.capture_apps_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.capture_apps_intro),
            style = AppTypography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.lg))

        if (allowed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LoveBrainShape.md)
                    .background(SurfaceInset, LoveBrainShape.md)
                    .padding(Spacing.lg)
            ) {
                Text(
                    text = stringResource(R.string.capture_apps_empty_hint),
                    style = AppTypography.bodyMedium,
                    color = TextHint
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }

        CompactInput(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.capture_apps_search),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.md))

        Text(
            text = stringResource(R.string.capture_apps_selected_count, allowed.size),
            style = AppTypography.labelMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.sm))

        val visible = remember(candidates, query) {
            val q = query.trim()
            if (q.isEmpty()) candidates
            else candidates.filter {
                it.displayName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }

        if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.capture_apps_no_results),
                style = AppTypography.bodyMedium,
                color = TextHint,
                modifier = Modifier.padding(vertical = Spacing.xl)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AppDimens.BORDER_WIDTH_DP.dp)
            ) {
                items(visible, key = { it.packageName }) { app ->
                    CaptureAppRow(
                        displayName = app.displayName,
                        packageName = app.packageName,
                        checked = app.packageName in allowed,
                        blocked = app.secondRejected,
                        onToggle = { viewModel.setCaptureAllowed(app.packageName, app.packageName !in allowed) }
                    )
                }
                item {
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        text = stringResource(R.string.capture_apps_blocked),
                        style = AppTypography.labelMedium,
                        color = TextHint
                    )
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

/**
 * 一行 = 一个可授权 App。
 *
 * P3-03: 整行可点，高度 ≥48dp；被二次拒绝的类别不可勾选，但仍然显示出来，
 * 让用户看得见"为什么它不能选"，而不是假装列表里只有这些 App。
 */
@Composable
private fun CaptureAppRow(
    displayName: String,
    packageName: String,
    checked: Boolean,
    blocked: Boolean,
    onToggle: () -> Unit
) {
    val rowLabel = stringResource(R.string.capture_apps_allow)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .clip(LoveBrainShape.sm)
            .clickable(
                enabled = !blocked,
                role = Role.Checkbox,
                onClick = onToggle
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = !blocked,
            modifier = Modifier.size(AppDimens.TOUCH_TARGET_MIN_DP.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = displayName,
                style = AppTypography.bodyLarge,
                color = if (blocked) TextHint else TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (blocked) rowLabel + " — " + packageName else packageName,
                style = AppTypography.labelMedium,
                color = TextHint
            )
        }
    }
}
