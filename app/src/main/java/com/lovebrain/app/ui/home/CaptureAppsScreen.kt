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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.lovebrain.app.viewmodel.FloatingServiceHomePort
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 这一页勾选行的私有尺寸档：行首那一格与行组件的图标档同宽 */
private object CaptureRowDimens {
    const val LEADING_BOX_DP = 36   // 勾选那一格的视觉位（整行才是点击位）
}

/**
 * 进这一页时要一次读齐的**四颗**事实（都在 IO 上取，见 [CaptureAppsScreen] 的那一批）。
 *
 * 收成一颗带类型的读数而不是三个散着的 `withContext`，是为了让"这一批落完了"成为一件
 * 可以在屏幕上观测到的事：那颗总开关的闸门同时吃 `granted` 与 `disclosure`，
 * 两颗分别挂状态的话，用例点开关时读到哪一颗就成了运气。
 *
 * CAP1 加了第四颗 `floatingRunning`：候选③（悬浮窗不在 ⇒ 抓到的内容当场不记）要进
 * [captureTruthOf] 的判据，而且**必须与前三颗同一时刻**——分批读会把"悬浮窗刚被划掉"
 * 那一瞬错报成运行中。
 */
private class CaptureFacts(
    val targets: List<SetupViewModel.CaptureApp>?,
    val granted: Boolean,
    val disclosure: Boolean,
    val floatingRunning: Boolean
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
 * - 按"同意并继续" → **先**写同意记录（`confirmAccessibilityDisclosure`），**然后**把开这一路
 *   续跑：权限已给就当场生效，没给才跳系统设置；
 * - 这一版已经同意过但仍未授予 → 直送系统设置，同一篇长文不端第二遍；
 * - 已授予且已同意过 → 开关当场生效，不弹任何窗、不跳任何设置（"已授予不骚扰"）；
 * - 状态卡那一行（四件只差记录）走出的同意 → **只补记录**，不拨开关、不跳设置。
 *
 * 关掉捕获永远不设门槛：退出采集不需要前置告知。
 *
 * ## CAP1：那一行状态从此只说真话（2026-10-06）
 *
 * 用户报"显示已开启，但抓不到聊天内容"（A1 取证 source-10 候选①③）：旧状态行只看授权与
 * allowlist 计数，allowlist 空着时服务侧 `CapturePolicy` 把每条事件 fail-closed 全拒，
 * 悬浮窗没起时长按确认到的内容又当场不记——界面却一路绿灯。
 * 现在那一行念的是 [captureTruthOf] 的六格之一：**权限 ∧ 开关 ∧ 披露 ∧ 范围非空（再加投递的
 * 悬浮窗）全部为真，才允许念"已开启 · 长按消息自动捕获"**；缺哪件念哪件，
 * 披露缺的那格行本身还能点（补开披露弹窗），范围缺的那格入口就是下方直接可见的勾选列表。
 * 服务侧的 fail-closed 一个字没改——改的是"静默"，不是"默认拒绝"。
 *
 * ## CAP3：开关那一格画的是"到底成没成"，不是"偏好位是几"（2026-10-06）
 *
 * 用户读数：「第一次进入消息捕获页面，开关是打开的……我关闭了重新拨开才触发了长文；
 * 按长文把无障碍打开之后，开关还是没开，再拨又跳去设置，卡好几次，退出重进才发现好了」。
 * 两颗病灶，各归一处，都在这屏：
 * - **D1 首屏谎报**：那颗胶囊以前直读 `captureEnabled`，而那颗偏好位当时**默认就是 true**
 *   （默认值本体已由 CAP4 翻关，另一位的账）。现在显示走 [captureSwitchShowsOn]，
 *   吃的就是这一批 IO 读数里那五颗——没同意 / 没授权 / 没选范围，开关**就画成关**，
 *   用户第一下拨的就是真"拨开"，长文当场到；不新增第二颗旗标。
 * - **D2 同意后没接上意图**：那扇弹窗的两条出口以前拿 `accessibilityGranted` 分流，
 *   把"开关那一路 + 权限已给"错分成"只补记录"。现在分流认的是 [disclosureEntry]
 *   （**这一记意图**，弹窗开着才存在，不落盘）：开关那一路同意后走
 *   [disclosureAgreedGateStep] 把"要开"续跑，状态卡那一路仍然只补记录、一条语义都没丢。
 *
 * 两根轴各钉各的：显示 = 有效态（[captureSwitchShowsOn]），落盘 = 用户那一记意图
 * （[applyCaptureIntent] + [captureIntentNeedsWrite]）。合一根就会长出
 * "用户拨一下、那一档自己弹回去"的新毛病——判据矩阵在 `CaptureTruthStateTest`，
 * 屏幕读数在 `CaptureAppsScreenTruthTest`，法律链在 `HomeStatusCaptureDisclosureTest`。
 */
@Composable
fun CaptureAppsScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit,
    /**
     * 悬浮窗在不在的读数源。默认接容器侧唯一的那颗服务端口（不 new、不启动、不拉起）；
     * 用例注入假读数——[com.lovebrain.app.service.FloatingService.instance] 的写口是
     * private，真机上它就住在那颗端口里，这一格不开第二条通道。
     */
    floatingRunning: () -> Boolean = { FloatingServiceHomePort.isRunning() }
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
    // 悬浮窗在不在（候选③的投递闸）：与上面三颗同一批 IO 读数落位
    var floatingUp by remember { mutableStateOf(false) }
    /**
     * 那扇披露弹窗**是谁**开出来的（[CaptureDisclosureEntry]）：`null` = 弹窗不在。
     *
     * 这颗就是"用户那一记意图"，不是第二本账——它只在弹窗开着的那一会儿存在，随弹窗一起没了，
     * 不落盘、不加偏好键。分流必须认它，**不许**拿 `accessibilityGranted` 当分流键：
     * 那颗旗标分不开"开关那一路（同意后要把开续跑）"与"状态卡那一路（同意后只补记录）"，
     * 而这两件事在"权限已经给了"那一格里判起来完全相反。
     */
    var disclosureEntry by remember { mutableStateOf<CaptureDisclosureEntry?>(null) }
    var query by remember { mutableStateOf("") }

    // **从系统授权页回来必须自己重读一遍**（用户 2026-10-06 真机读数，指导书 第3条 的验收句
    // "未授权 → 点击去设置 → **返回刷新**"到今天才真的成立）：
    // 这一页的四颗事实（扫包 / 无障碍授权 / 这一版同意记录 / 悬浮窗在不在）只在
    // `LaunchedEffect(rescanTick)` 里读一次；跳去系统设置给完权限再回来，Activity 只是 resume，
    // 这一屏**没有重新装配**，于是 `accessibilityGranted` 还留在出发前的 false——
    // 用户看到的正是他描述的那一串：「我已经开启了啊，拨开关却又跳去设置，卡好几次，
    // 退出页面重进才发现好了」（重进 = 重新组合 = 才会重读）。
    // 挂在 resume 而不是挂 `LaunchedEffect(Unit)`：后者只在进入这一格时跑一次，跳回来不会重来。
    // 体例照 `HomeScreen.kt:110-117` 那一颗（同一族问题、同一个观察者写法），不复述它的判据。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) rescanTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(rescanTick) {
        scanning = true
        // 组合期不碰磁盘/IPC：这四件都是读系统状态/偏好，放到 IO 上去。
        // **一次 IO 批读完，再一次性落位**——不是读一颗挂一次：
        // 1) 四颗读数描述的是同一时刻的同一台机器（扫包 + 无障碍授权 + 这一版同意记录 + 悬浮窗在不在），
        //    分四次挂会让中间那两格短暂读到"上一批"的值；
        // 2) 闸门 `captureSwitchGateStep` 同时用第二、三颗当输入，[captureTruthOf] 四颗全吃，
        //    把它们分次写进合成状态，用例就可能在一批还没落完时点开关（那是拿竞态当判据）；
        // 3) `scanning = false` 因此**必须排在最后一颗**：列表画得出来 = 这一批四颗都已落位，
        //    用例可以用"那一行在不在"当作这一批完成的可观测定点。
        val batch = withContext(Dispatchers.IO) {
            CaptureFacts(
                targets = viewModel.selectableCaptureTargets(context),
                granted = viewModel.isCaptureServiceEnabled(context),
                disclosure = viewModel.isAccessibilityDisclosureConfirmed(),
                floatingRunning = floatingRunning()
            )
        }
        candidates = batch.targets
        accessibilityGranted = batch.granted
        disclosureConfirmed = batch.disclosure
        floatingUp = batch.floatingRunning
        scanning = false
    }

    /**
     * 写"用户要把捕获开到哪一档"这一记意图。**写口只有 [SetupViewModel.toggleCapture] 一颗**
     * （它是翻、不是设），所以只有目标档与当前档不一致时才翻：
     * 显示与意图分成两根轴之后，"拨一下却没写"是正确行为，"拨一下反而把意图关掉"才是 bug。
     */
    fun applyCaptureIntent(target: Boolean) {
        if (captureIntentNeedsWrite(captureEnabled, target)) viewModel.toggleCapture()
    }

    /**
     * 那颗总开关按下去的唯一路径——判据全在 [captureSwitchGateStep] 那一处，这里只接三条出口。
     *
     * 顺序钉死：`ApplySwitch` 才写意图；`ShowDisclosure` 只弹窗（不写同意、不跳设置、不改开关），
     * 并且**记下是开关这一路开的**；`OpenAccessibilitySettings` 只在"这一版已经明确同意过"时才走。
     *
     * 交回来的 `target` 是**用户想去的那一档**（那颗胶囊画的是有效态，见 [captureSwitchShowsOn]），
     * 所以它只能当意图用，落盘那一步走 [applyCaptureIntent]，不直接翻旗标。
     */
    fun onCaptureSwitch(target: Boolean) {
        when (captureSwitchGateStep(target, accessibilityGranted, disclosureConfirmed)) {
            CaptureGateStep.ApplySwitch -> applyCaptureIntent(target)
            CaptureGateStep.ShowDisclosure -> disclosureEntry = CaptureDisclosureEntry.FromSwitch
            CaptureGateStep.OpenAccessibilitySettings -> openAccessibilitySettings(context)
        }
    }

    /**
     * **开关那一路**的"同意并继续"：先写同意记录，再把"要开"这一格续跑。
     *
     * 续跑判据是 [disclosureAgreedGateStep]：权限已给 ⇒ 当场生效（意图落成真开）；
     * 权限没给 ⇒ 去系统设置，回来由 resume 那一拍（上面那颗 `LifecycleEventObserver`）接上。
     * 顺序是承重的：记录必须先落地，才允许跳设置——否则给完权限服务仍把正文挡在门外。
     */
    fun onDisclosureAgreedFromSwitch() {
        disclosureEntry = null
        viewModel.confirmAccessibilityDisclosure()
        disclosureConfirmed = true
        when (disclosureAgreedGateStep(accessibilityGranted)) {
            CaptureGateStep.ApplySwitch -> applyCaptureIntent(true)
            CaptureGateStep.OpenAccessibilitySettings -> openAccessibilitySettings(context)
            CaptureGateStep.ShowDisclosure -> Unit   // 这一步不会被判出来：见那两个 when 分支
        }
    }

    /**
     * 状态卡那一行（[CaptureTruthState.DisclosurePending] 的"能点的入口"）走出的同意：
     * 这一路进来时权限、开关、范围**都已经是真的**，缺的只有披露记录这一颗——
     * 所以只补写记录，**不许**再拨开关（拨了就当场把用户已经开着的捕获关掉了），
     * 也**不许**跳系统设置（权限已经给了，那一跳是骚扰）。
     */
    fun onDisclosureAgreedInPlace() {
        disclosureEntry = null
        viewModel.confirmAccessibilityDisclosure()
        disclosureConfirmed = true
    }

    /** 四件判据 + 投递闸，同一批落位的读数算一次；只有 Running 允许念"已开启" */
    val truthFacts = CaptureTruthFacts(
        accessibilityGranted = accessibilityGranted,
        switchOn = captureEnabled,
        disclosureConfirmed = disclosureConfirmed,
        scopeNonEmpty = allowed.isNotEmpty(),
        floatingRunning = floatingUp
    )
    val truthState = captureTruthOf(truthFacts)
    /** 开关画的那一档：四件真才画开。**意图旗标（`captureEnabled`）不参与显示，只有它参与判定** */
    val switchShowsOn = captureSwitchShowsOn(truthFacts)

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
        // 那一行念的是 captureTruthOf 的六格之一：范围没选就说没选，绝不借"已开启"的句子。
        // 画的是 captureSwitchShowsOn（有效态），不是偏好位：默认开那颗旗标不许让用户首屏看到"开"。
        CaptureStatusCard(
            checked = switchShowsOn,
            state = truthState,
            onToggle = { target -> onCaptureSwitch(target) },
            onNeedDisclosure = { disclosureEntry = CaptureDisclosureEntry.FromStatusRow }
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
    // 两条入口同一扇窗，出去那一步由**是谁开的**（[disclosureEntry]）分流：
    // 开关那一路同意后把"开"续跑；状态卡那一路（四件本来就在）同意只补记录。
    disclosureEntry?.let { entry ->
        AccessibilityDisclosureDialog(
            onAgree = {
                when (entry) {
                    CaptureDisclosureEntry.FromSwitch -> onDisclosureAgreedFromSwitch()
                    CaptureDisclosureEntry.FromStatusRow -> onDisclosureAgreedInPlace()
                }
            },
            onDismiss = { disclosureEntry = null }
        )
    }
}

/**
 * 「捕获开关 + 授权状态」那一行：行 = 设计系统那颗 [LbSettingRow]，卡底 = 白卡（与知识库同一副）。
 *
 * 三样信息各自只有一个来源：
 * - 开关：**画的**是 [captureSwitchShowsOn] 那一档（四件事实的全称读数），**交回的**是
 *   用户想去的那一档（`onCheckedChange` 的新值）——两件事分两根轴，谁也不许替谁顶缺：
 *   拿偏好位当显示就是"第一次进来开关已经拨开"（CAP3 的 D1），拿显示当意图回写就是
 *   "拨一下自己弹回去"。那一格要不要先弹披露由 [captureSwitchGateStep] 判，
 *   这颗组件自己不做任何授权判断，视觉是 36×20 那颗胶囊，
 *   **直接用供应商表单那一颗 [MiniSwitch]**，不在这一页再造一份平行件
 *   （这一档提进 `core/designsystem`，两处一起换掉）；
 * - 状态行：只念 [captureStatusSentence] 从 [CaptureTruthState] 派生的那一句——
 *   权限、开关、披露、范围、悬浮窗**五读一屏各说各的**，四件（+投递）都真才轮到"已开启 · 长按消息自动捕获"。
 *   CAP1 修的就是这一格旧账：旧写法只看授权与计数，allowlist 空着也照样"已授权 0 个"糊过去，
 *   而服务侧当场把所有事件全拒；
 * - 状态点：颜色走 [LbRowState]，只有 [CaptureTruthState.Running] 配 [LbRowState.Ready]，
 *   不在页面里再写一遍 `if (ready) Primary else Neutral300`。
 *
 * [CaptureTruthState.DisclosurePending] 那一格的整行是"能点的入口"：点下去只开
 * [AccessibilityDisclosureDialog]（补看那一篇长文），不拨开关、不跳设置——其余格的行空白
 * 仍然没有整行点击位（点行空白不该把捕获关掉）。
 */
@Composable
private fun CaptureStatusCard(
    checked: Boolean,
    state: CaptureTruthState,
    onToggle: (Boolean) -> Unit,
    onNeedDisclosure: () -> Unit
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
            subtitle = stringResource(captureStatusSentence(state)),
            dot = if (state == CaptureTruthState.Running) {
                LbRowState.Ready
            } else {
                LbRowState.NotReady
            },
            onClick = if (state == CaptureTruthState.DisclosurePending) onNeedDisclosure else null,
            trailing = {
                // 开关自己带 Role.Switch、48 见方热区与 contentDescription（见 MiniSwitch）；
                // 画的是 [captureSwitchShowsOn] 那一档，交回的是**用户想去的那一档**，不是"已经翻了"：
                // 那一格要不要先弹披露、这一笔要不要落盘，全归闸门与 [captureIntentNeedsWrite] 判。
                MiniSwitch(checked = checked, label = switchName, onCheckedChange = { onToggle(it) })
            }
        )
    }
}

/**
 * 状态 → 那一句话。**只复用已有资源，一条新文案都不加**（strings.xml 不在本席独占区，
 * 预算只降不升；专用句已登 `handoffs/2026-10-06-CAP1-文案申请.md`）。
 * 六格六句互不相同——两格共用一句就是"缺的那件事少说一件"，`captureTruthOf` 的
 * 判据矩阵用例把这条分不开钉死了。
 */
internal fun captureStatusSentence(state: CaptureTruthState): Int = when (state) {
    // 已开启那句自带"长按"两个字：候选②的"只有长按才会抓"从此在运行态里说得见
    CaptureTruthState.Running -> R.string.home_capture_status_on
    CaptureTruthState.NoPermission -> R.string.home_capture_no_permission
    CaptureTruthState.CaptureSwitchOff -> R.string.home_capture_status_off
    CaptureTruthState.DisclosurePending -> R.string.capture_disclosure_title
    CaptureTruthState.ScopeNotSelected -> R.string.capture_apps_row_subtitle_none
    CaptureTruthState.FloatingNotStarted -> R.string.home_desc_not_started
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
