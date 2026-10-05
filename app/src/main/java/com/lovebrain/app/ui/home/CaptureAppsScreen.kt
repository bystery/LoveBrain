package com.lovebrain.app.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.LbRowState
import com.lovebrain.app.core.designsystem.LbSettingRow
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.ui.common.CompactInput
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 这一页勾选行的私有尺寸档：行首那一格与行组件的图标档同宽 */
private object CaptureRowDimens {
    const val LEADING_BOX_DP = 36   // 勾选那一格的视觉位（整行才是点击位）
}

/**
 * 进这一页时要一次读齐的三颗事实（都在 IO 上取，见 [CaptureAppsScreen] 的那一批）。
 *
 * 收成一颗带类型的读数而不是三个散着的 `withContext`，是为了让"这一批落完了"成为一件
 * 可以在屏幕上观测到的事：那颗总开关的闸门同时吃 `granted` 与 `disclosure`，
 * 两颗分别挂状态的话，用例点开关时读到哪一颗就成了运气。
 */
private class CaptureFacts(
    val targets: List<SetupViewModel.CaptureApp>?,
    val granted: Boolean,
    val disclosure: Boolean
)

/**
 * 消息捕获 allowlist 选择页。
 *
 * 事实是：捕获默认 fail-closed，所以必须有一页让用户真正点名授权哪些 App。
 *
 * ## 这一页的外观从哪儿来
 *
 * 与已踩案例页、知识库页同一副壳：[ScreenPage] 的页头与内容边距 + 白卡（`Card(lg, SurfaceCard)`）
 * + [LbSettingRow] 那一族行。这一层只填捕获这件事的内容：开关、授权状态、可授权 App 名单、
 * 必要时搜索。授权判据与允许名单都归 ViewModel/仓库，这一页不 new Repository、
 * 也不复制别的 Activity 的读写逻辑。
 *
 * ## 这次删掉的废话
 *
 * 重复教程、大段工作原理、内部包名、被拒类别的解释段都不再出现在这一屏；
 * 说明最多一行（`capture_apps_intro`）。数据源与保存出口一个没动：
 * `captureAllowedPackages` 读、`setCaptureAllowed` 写、`CapturePolicy` 的二次拒绝仍在
 * ViewModel 那一侧兜住（界面回退不会把捕获串义带回来，未授权的 App 进不了采集链）。
 *
 * ## 那次同步 IPC 挪走了
 *
 * 枚举安装包（`queryIntentActivities`）以前挂在 `remember { … }` 上，也就是**组合期同步扫描**：
 * 装机量大时它直接在 UI 线程上要等，错误态重试也一样。现在它只在
 * [LaunchedEffect] 里、`Dispatchers.IO` 上跑，页面因此有了真正的 Loading 那一格
 * （以前"没有 Loading"的唯一理由就是它是同步的）。授权状态（无障碍是否已开启）同一格一起刷：
 * 从系统设置回来重新进这一页，会重新读一次，不会停在旧读数上。
 *
 * ## 授权披露接回来了（这一页是唯一的那一步）
 *
 * 首页重做把"服务设置段"整段删掉时，`AccessibilityDisclosureDialog` 的**调用点**也一起没了——
 * 那一颗弹窗变成全仓没人 call 的孤儿，而"未授予时先弹披露、明确同意才进系统设置"这条链就此断掉。
 * 现在它接回这一页，三步都在 [captureSwitchGateStep] / [disclosureAgreedGateStep] 那一处判：
 * - 无障碍**没授予**、这一版披露也没明确同意过 → 只弹披露；按"取消"什么都不发生；
 * - 按"同意并继续" → 先写同意记录（`confirmAccessibilityDisclosure`），**然后**才跳系统设置；
 * - 这一版已经同意过但仍未授予 → 直送系统设置，同一篇长文不端第二遍；
 * - 已授予 → 开关当场生效，不弹任何窗、不跳任何设置（"已授予不骚扰"）。
 *
 * 关掉捕获永远不设门槛：退出采集不需要前置告知。
 */
@Composable
fun CaptureAppsScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit
) {
    val allowed by viewModel.captureAllowedPackages.collectAsStateWithLifecycle()
    val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var rescanTick by remember { mutableIntStateOf(0) }
    // null = "读不出来"（那次 IPC 失败了），空表 = "整机没有可授权的 App"——两件事不能合成一格
    var candidates by remember { mutableStateOf<List<SetupViewModel.CaptureApp>?>(null) }
    var scanning by remember { mutableStateOf(true) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    // 这一版隐私披露用户有没有**明确同意过**（记的是版本号，不是"看见过"）
    var disclosureConfirmed by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(rescanTick) {
        scanning = true
        // 组合期不碰磁盘/IPC：这三件都是读系统状态/偏好，放到 IO 上去。
        // **一次 IO 批读完，再一次性落位**——不是读一颗挂一次：
        // 1) 三颗读数描述的是同一时刻的同一台机器（扫包 + 无障碍授权 + 这一版同意记录），
        //    分三次挂会让中间那两格短暂读到"上一批"的值；
        // 2) 闸门 `captureSwitchGateStep` 同时用后两颗当输入，把它们分次写进合成状态，
        //    用例就可能在一批还没落完时点开关（那是拿竞态当判据）；
        // 3) `scanning = false` 因此**必须排在最后一颗**：列表画得出来 = 这一批三颗都已落位，
        //    用例可以用"那一行在不在"当作这一批完成的可观测定点。
        val batch = withContext(Dispatchers.IO) {
            CaptureFacts(
                targets = viewModel.selectableCaptureTargets(context),
                granted = viewModel.isCaptureServiceEnabled(context),
                disclosure = viewModel.isAccessibilityDisclosureConfirmed()
            )
        }
        candidates = batch.targets
        accessibilityGranted = batch.granted
        disclosureConfirmed = batch.disclosure
        scanning = false
    }

    /**
     * 那颗总开关按下去的唯一路径——判据全在 [captureSwitchGateStep] 那一处，这里只接三条出口。
     *
     * 顺序钉死：`ApplySwitch` 才写开关；`ShowDisclosure` 只弹窗（不写同意、不跳设置、不改开关）；
     * `OpenAccessibilitySettings` 只在"这一版已经明确同意过"时才走。
     */
    fun onCaptureSwitch(target: Boolean) {
        when (captureSwitchGateStep(target, accessibilityGranted, disclosureConfirmed)) {
            CaptureGateStep.ApplySwitch -> viewModel.toggleCapture()
            CaptureGateStep.ShowDisclosure -> showDisclosure = true
            CaptureGateStep.OpenAccessibilitySettings -> openAccessibilitySettings(context)
        }
    }

    /** 披露弹窗上"同意并继续"：先写同意记录，再看这一格该开捕获还是该去系统设置 */
    fun onDisclosureAgreed() {
        showDisclosure = false
        viewModel.confirmAccessibilityDisclosure()
        disclosureConfirmed = true
        when (disclosureAgreedGateStep(accessibilityGranted)) {
            CaptureGateStep.ApplySwitch -> viewModel.toggleCapture()
            CaptureGateStep.OpenAccessibilitySettings -> openAccessibilitySettings(context)
            CaptureGateStep.ShowDisclosure -> Unit   // 这一步不会被判出来：见那两个 when 分支
        }
    }

    ScreenPage(title = stringResource(R.string.capture_apps_title), onBack = onBack) {
        // 说明只有这一行
        Text(
            text = stringResource(R.string.capture_apps_intro),
            style = AppTypography.labelMedium,
            color = TextHint
        )
        Spacer(Modifier.height(Spacing.lg))

        // 捕获开关 + 授权状态：一张白卡里的一行，行尾那颗小开关是这一页唯一的总开关。
        // 开关按下去不等于立刻生效——那一步归 captureSwitchGateStep 判（未授予先弹披露）。
        CaptureStatusCard(
            enabled = captureEnabled,
            accessibilityGranted = accessibilityGranted,
            allowedCount = allowed.size,
            onToggle = { target -> onCaptureSwitch(target) }
        )
        Spacer(Modifier.height(Spacing.lg))

        CaptureScopePicker(
            candidates = if (scanning) null else candidates,
            loading = scanning,
            query = query,
            onQueryChange = { query = it },
            allowed = allowed,
            onToggle = { pkg, allow -> viewModel.setCaptureAllowed(pkg, allow) },
            onRescan = { rescanTick++ },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }

    // 授权披露：这一屏唯一的"进系统设置"入口，而且是**看完长文之后**才有的那一步。
    // 放在 ScreenPage 外面是因为它是另一扇窗（AlertDialog），不该占这一列的版式位。
    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = { onDisclosureAgreed() },
            onDismiss = { showDisclosure = false }
        )
    }
}

/**
 * 「捕获开关 + 授权状态」那一行：行 = 设计系统那颗 [LbSettingRow]，卡底 = 白卡（与知识库同一副）。
 *
 * 三样信息各自只有一个来源：
 * - 开关：把**用户想去的那一档**（`onCheckedChange` 的新值）交回调用方，由
 *   [captureSwitchGateStep] 判这一格是"当场生效"还是"先看披露"——这颗组件自己不做任何授权判断，
 *   视觉是 36×20 那颗胶囊，**直接用供应商表单那一颗 [MiniSwitch]**，不在这一页再造一份平行件
 *   （这一档提进 `core/designsystem`，两处一起换掉）；
 * - 授权状态：无障碍没开就说没开（`home_capture_no_permission`），开了才报"已授权 N 个 App"
 *   ——这两句与首页那一行读**同一条资源**，不在这里抄第二份；
 * - 状态点：颜色走 [LbRowState]，不在页面里再写一遍 `if (ready) Primary else Neutral300`。
 */
@Composable
private fun CaptureStatusCard(
    enabled: Boolean,
    accessibilityGranted: Boolean,
    allowedCount: Int,
    onToggle: (Boolean) -> Unit
) {
    val switchName = stringResource(R.string.capture_apps_allow)
    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
            .testTag(LbCaptureTags.STATUS_CARD)
    ) {
        LbSettingRow(
            title = stringResource(R.string.capture_apps_title),
            subtitle = when {
                !accessibilityGranted -> stringResource(R.string.home_capture_no_permission)
                allowedCount == 0 -> stringResource(R.string.capture_apps_row_subtitle_none)
                else -> stringResource(R.string.capture_apps_row_subtitle, allowedCount)
            },
            dot = if (enabled && accessibilityGranted && allowedCount > 0) {
                LbRowState.Ready
            } else {
                LbRowState.NotReady
            },
            trailing = {
                // 开关自己带 Role.Switch、48 见方热区与 contentDescription（见 MiniSwitch）；
                // 这一行没有整行点击位——点行空白不该把捕获关掉。
                // 交回的是**用户想去的那一档**，不是"已经翻了"：那一格要不要先弹披露由闸门判。
                MiniSwitch(checked = enabled, label = switchName, onCheckedChange = { onToggle(it) })
            }
        )
    }
}

/**
 * 「范围选择内容」这一段的**唯一主人**：搜索框 → 四态勾选列表。
 *
 * 抽出来的唯一动机是复用：悬浮窗齿轮那扇整窗设置页（`ui/panel/settings/SettingsCaptureEntry`）
 * 复用的是同一颗、同一个 `captureAllowedPackages` 状态源，不再有第二份范围数据。
 *
 * 与替换前相比少了两句解释（"范围说明 + 已授权数"）——那两行现在归上面那张状态卡，
 * 同一件事在一屏里说两遍就是这次的删除对象；判据、状态格、行组件与保存出口都没换主人。
 *
 * @param candidates 枚举结果；`null` 是那一次同步 IPC 失败了（与"整机没有可授权的 App"是两格）
 * @param loading 第一次枚举还在路上——这一格现在是真的会出现了，因为枚举挪到了 IO 上
 * @param onRescan 错误态那颗重试的出口，调用方用它换一次重新枚举
 * @param modifier 外层给的尺寸约束；嵌进设置页时在这里给一个**有界高度**
 */
@Composable
internal fun CaptureScopePicker(
    candidates: List<SetupViewModel.CaptureApp>?,
    query: String,
    onQueryChange: (String) -> Unit,
    allowed: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    val screenState = captureScreenState(
        candidates = candidates,
        query = query,
        loading = loading,
        nothingToAuthorize = stringResource(R.string.capture_apps_none_to_authorize),
        noResults = stringResource(R.string.capture_apps_no_results),
        scanFailed = stringResource(R.string.capture_apps_scan_failed),
        retry = ScreenAction(stringResource(R.string.action_retry)) { onRescan() }
    )

    Column(modifier) {
        CompactInput(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.capture_apps_search),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.md))

        // 列表那一格拿剩下的空间：外层是固定高度时它就有界，能嵌进会滚的设置页
        Box(modifier = Modifier.weight(1f)) {
            CaptureAppListRegion(state = screenState, allowed = allowed, onToggle = onToggle)
        }
    }
}

/**
 * 这一页"现在是哪一格"只判一次，版式交给 [com.lovebrain.app.core.designsystem.LbAsyncState]。
 *
 * 与替换前多出来的一格是 Loading：**枚举安装包挪到 IO 上之后，首帧就没有答案了**，
 * 那一格从此真的会出现。剩下三格的顺序不变：读不出来 > 整机没有可授权的 App > 搜索没匹配 > 有结果。
 * 前两个**必须是两格**——旧写法 `runCatching{…}.getOrDefault(emptyList())` 把两件事合成一件，
 * 页面于是会对用户说"这台机器上没有可授权的 App"，而真相可能是那次同步 IPC 失败了。
 *
 * 过滤跟着判据一起留在这里（判据少一处，就少一份"两处会长歪"的余地）。
 */
internal fun captureScreenState(
    candidates: List<SetupViewModel.CaptureApp>?,
    query: String,
    nothingToAuthorize: String,
    noResults: String,
    scanFailed: String,
    retry: ScreenAction,
    loading: Boolean = false
): ScreenState<List<SetupViewModel.CaptureApp>> = when {
    loading -> ScreenState.Loading
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
 * 四态出口 + 勾选列表：一张白卡装着这一族行（与知识库、供应商那一族同一个卡底）。
 *
 * 拆成一颗是为了让"判到哪一格"与"那一格怎么画"各自只有一处，页面本身只负责拼状态源。
 * 卡底只在这里画一次；Loading / Empty / Error 三格仍走共用组件，不给它们另配卡底
 * （那一族的空态长什么样已经由 `LbAsyncState` 定了，这一页不另开第二种答案）。
 */
@Composable
internal fun CaptureAppListRegion(
    state: ScreenState<List<SetupViewModel.CaptureApp>>,
    allowed: Set<String>,
    onToggle: (String, Boolean) -> Unit
) {
    if (state is ScreenState.Content) {
        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AppDimens.BORDER_WIDTH_DP.dp)
            ) {
                items(state.value, key = { it.packageName }) { app ->
                    CaptureAppRow(
                        displayName = app.displayName,
                        checked = app.packageName in allowed,
                        blocked = app.secondRejected,
                        onToggle = { onToggle(app.packageName, app.packageName !in allowed) }
                    )
                }
            }
        }
        return
    }
    LbAsyncState(state, modifier = Modifier.fillMaxSize()) { }
}

/**
 * 一行 = 一个可授权 App ⇒ 归 第6节第1条 表里"行"那一族的主人 [LbSettingRow]。
 *
 * 行内不再写包名：包名是内部标识，用户要靠它做不了任何决定，而这一屏的行距就那么多。
 * 被二次拒绝的类别仍然显示、仍然按不动，并且仍然**读得出**按不动（`rowEnabled = false`
 * 会把 `Disabled` 挂上去——旧写法靠"不给 onClick"表达同一件事，那样整行会退化成一颗哑行）。
 *
 * 勾选那一格留在调用方（走 `leading` 槽），不是偷懒：它是"这一行授权了没有"唯一的视觉来源，
 * 而设计系统不该认识"勾选框/对勾"这个具体控件。画法与这一族页面其它"当前是哪一项"同一档：
 * **勾上了就画一颗 18dp 的对勾（`AppDimens.ACTION_ICON_SIZE_DP`），没勾就留空**——
 * 不再画一颗 Material 大 Checkbox（那颗自带 48dp 轨道，正是这一屏被点名撑大的形状之一）。
 */
@Composable
private fun CaptureAppRow(
    displayName: String,
    checked: Boolean,
    blocked: Boolean,
    onToggle: () -> Unit
) {
    LbSettingRow(
        leading = {
            Box(
                modifier = Modifier.size(CaptureRowDimens.LEADING_BOX_DP.dp),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(AppDimens.ACTION_ICON_SIZE_DP.dp)
                    )
                }
            }
        },
        title = displayName,
        subtitle = "",
        onClick = onToggle,
        rowRole = Role.Checkbox,
        rowEnabled = !blocked
    )
}
