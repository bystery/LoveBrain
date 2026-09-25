package com.lovebrain.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.ui.common.CompactInput
import com.lovebrain.app.ui.theme.AppDimens
import com.lovebrain.app.ui.theme.AppTypography
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.TextHint
import com.lovebrain.app.ui.theme.TextPrimary
import com.lovebrain.app.ui.theme.TextSecondary
import com.lovebrain.app.viewmodel.SetupViewModel

/**
 * 消息捕获 allowlist 选择页。
 *
 *事实是：代码里只有关键词 blocklist，没命中的任意 App 都能进入捕获逻辑。
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
    // 包列表只在进入本页时枚举一次；queryIntentActivities 是同步 IPC，放 remember 里避免每帧重算。
    // rescanTick 是错误态那颗重试**唯一**的出口——没有它，"读不出来"那一格就是死路。
    // 用 mutableIntStateOf：mutableStateOf(0) 会走装箱（lint 的 AutoboxingStateCreation 当场报，
    // 这一档预算原本是 6 条，被我这行顶到 7 过——修的是自己，不是抬预算）。
    var rescanTick by remember { mutableIntStateOf(0) }
    val candidates = remember(rescanTick) { viewModel.selectableCaptureTargets(context) }
    var query by remember { mutableStateOf("") }

    val screenState = captureScreenState(
        candidates = candidates,
        query = query,
        nothingToAuthorize = stringResource(R.string.capture_apps_none_to_authorize),
        noResults = stringResource(R.string.capture_apps_no_results),
        scanFailed = stringResource(R.string.capture_apps_scan_failed),
        retry = ScreenAction(stringResource(R.string.action_retry)) { rescanTick++ }
    )

    ScreenPage(title = stringResource(R.string.capture_apps_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.capture_apps_intro),
            style = AppTypography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.lg))

        // 原来是这里一张自造的 inset 卡片（"尚未选择任何 App，消息捕获实际处于关闭状态"），
        // 而它下面还有一行"已选 0 个"——同一个事实说两遍，其中一遍正是 §6.3 点名的形状。
        // 现在只留一行，并且用**首页那两行同款文案**（capture_apps_row_subtitle / _none）：
        // 0 个的时候说"未选择 App · 不会捕获任何内容"，比"已选 0 个"多说了后果。
        Text(
            text = if (allowed.isEmpty()) stringResource(R.string.capture_apps_row_subtitle_none)
            else stringResource(R.string.capture_apps_row_subtitle, allowed.size),
            style = AppTypography.labelMedium,
            color = if (allowed.isEmpty()) TextHint else TextSecondary
        )
        Spacer(Modifier.height(Spacing.lg))

        CompactInput(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.capture_apps_search),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.md))

        CaptureAppListRegion(
            state = screenState,
            allowed = allowed,
            onToggle = { pkg, allow -> viewModel.setCaptureAllowed(pkg, allow) }
        )
    }
}

/**
 * §6.3：这一页"现在是哪一格"只判一次，版式交给 [com.lovebrain.app.core.designsystem.LbAsyncState]。
 *
 * 先说清一处与知识库页不同的地方：**这页没有 Loading 格**。两个数据源都是同步的——
 * `captureAllowedPackages` 在 ViewModel 构造时就把 prefs 里那份读出来了，
 * `selectableCaptureTargets` 是组合期一次同步枚举，首帧就有答案。画一个永远不出现的转圈格
 * 等于装饰分支，宁可少一格（真要那一格有意义，得先把那次同步 IPC 挪到 IO 上——
 * 那笔属于"主线程"账，不属于这一格）。
 *
 * 剩下三格的顺序：读不出来 > 整机没有可授权的 App > 搜索没匹配 > 有结果。
 * 前两个**必须是两格**，这正是 `selectableCaptureTargets` 改成可空的原因：旧写法
 * `runCatching{…}.getOrDefault(emptyList())` 把两件事合成一件，页面于是会对用户说
 * "这台机器上没有可授权的 App"，而真相可能是那次同步 IPC 失败了。
 *
 * 过滤跟着判据一起搬进来（原来在 `remember(candidates, query){…}` 里另算一遍）：
 * 判据少一处，就少一份"两处会长歪"的余地；n 是已装 App 数，逐帧滤得起。
 */
internal fun captureScreenState(
    candidates: List<SetupViewModel.CaptureApp>?,
    query: String,
    nothingToAuthorize: String,
    noResults: String,
    scanFailed: String,
    retry: ScreenAction
): ScreenState<List<SetupViewModel.CaptureApp>> = when {
    candidates == null -> ScreenState.Error(scanFailed, retry)
    candidates.isEmpty() -> ScreenState.Empty(nothingToAuthorize)
    else -> {
        val q = query.trim()
        val visible = if (q.isEmpty()) candidates else candidates.filter {
            it.displayName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
        if (visible.isEmpty()) ScreenState.Empty(noResults) else ScreenState.Content(visible)
    }
}

/**
 * 四态出口 + 勾选列表。拆成一颗是为了让"判到哪一格"与"那一格怎么画"各自只有一处，
 * 页面本身只负责拼状态源；语义树用例走的是整页挂载（造一份 SetupViewModel 就行）。
 */
@Composable
internal fun CaptureAppListRegion(
    state: ScreenState<List<SetupViewModel.CaptureApp>>,
    allowed: Set<String>,
    onToggle: (String, Boolean) -> Unit
) {
    LbAsyncState(state, modifier = Modifier.fillMaxSize()) { shown ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.BORDER_WIDTH_DP.dp)
        ) {
            items(shown, key = { it.packageName }) { app ->
                CaptureAppRow(
                    displayName = app.displayName,
                    packageName = app.packageName,
                    checked = app.packageName in allowed,
                    blocked = app.secondRejected,
                    onToggle = { onToggle(app.packageName, app.packageName !in allowed) }
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

/**
 * 一行 = 一个可授权 App。
 *
 * 整行可点，高度 ≥48dp；被二次拒绝的类别不可勾选，但仍然显示出来，
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
