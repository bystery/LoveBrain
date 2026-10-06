package com.lovebrain.app.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lovebrain.app.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.heightIn
import com.lovebrain.app.domain.LessonDoc
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.ui.panel.MarkdownText
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.util.L
import com.lovebrain.app.viewmodel.KbEditViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

internal data class KbFile(val label: String, val path: String, val layer: String)

/**
 * 知识库编辑页内部尺寸常量
 *
 * 两颗新登记的都写着"这一档是**版式**，不是热区下限"：这一页把 M3 那颗
 * `minimumInteractiveContainer` 的 48dp 当成了可见高度合同，于是分区按钮、卡内那一排的
 * 「编辑/预览」与「放弃修改」一起被抬高（用户点名的"按钮变得非常大"就是这一条）。
 * 下限本身一处没改（`AppDimens.TOUCH_TARGET_MIN_DP` 仍写着 48，页面主体与两颗主按钮照旧），
 * 这一页只是不再借它当自己那几颗胶囊的高度。
 */
private object KbEditDimens {
    const val DIRTY_DOT_SIZE_DP = 6         // 脏标记小圆点尺寸

    /**
     * 正文编辑器那一棵的**地板**高度（dp），不是它的高度。
     *
     * 它真正的尺寸由外层那一档 `weight(1f)` 给：键盘把外框 `imePadding()` 撑高多少，
     * 加权的那一格就矮多少，这一棵跟着矮（本页键盘责任已单一化，见 `onCreate`）。
     * 这一颗只挡"被压成一粒"那种情况，所以取小而不要取大——取大了会在小屏 + 键盘弹起时
     * 把「保存 / 放弃修改」那一排挤出可视区，那是用另一个缺陷换一个缺陷。
     *
     * 这一档不是热区下限，也不是 core 的矮档：core 那四档（36/28/40/36）各有各的用途，
     * 借哪一颗都是把两件事并成一件（`ui/UiLayerDependencyContractTest.kt` 那条"每颗数要解得出主人"
     * 只扫挂在可点链上的数，编辑器这一棵不在射程里）。
     */
    const val EDITOR_MIN_HEIGHT_DP = 96
}

/** 脏标记小圆点 */
@Composable
private fun DirtyDot(sizeDp: Int = KbEditDimens.DIRTY_DOT_SIZE_DP) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Warning)
    )
}

private val KB_FILES = listOf(
    KbFile("我是谁", "understand/me.md", layer = "画像"),
    KbFile("她是谁", "understand/her.md", layer = "画像"),
    KbFile("我们走到哪了", "understand/warmth.md", layer = "画像"),
    // 个人表达偏好——结构化、易编辑，不要求用户写提示词
    KbFile("我的表达偏好", "understand/style.md", layer = "画像"),
    KbFile("最近两句", "moment/recent.md", layer = "当下"),
    KbFile("在聊什么", "moment/topic.md", layer = "当下"),
    KbFile("此刻状态", "moment/scene.md", layer = "当下"),
    KbFile("进行中事项", "moment/plan.md", layer = "当下"),
    KbFile("话题档案", "memory/raw_topic.md", layer = "积累"),
    KbFile("经验", "memory/lessons.md", layer = "积累"),
    KbFile("谈心记录", "memory/counseling_log.md", layer = "积累"),
    KbFile("军师日志", "memory/reflect_history.md", layer = "积累")
    // 「旧版归档」这一格已经从名单里删掉（用户点名的两条之一）。
    // `memory/archive.md` **文件本体、兼容迁移与导出内容一份都没动**——
    // 删的是这个页面上的入口，不是用户的历史。
)

/** 默认打开的那一格（[resolveInitialKbFile] 的兜底目标） */
private const val DEFAULT_KB_FILE_PATH = "moment/recent.md"

/**
 * 这一次进这一页该开哪一格：持久化的 lastFile > 「最近两句」> 名单第一格。
 *
 * 为什么单独抽出来判：旧写法是 `files.first { it.path == "moment/recent.md" }`——
 * 那句在名单里没有「最近两句」时**直接抛**（`NoSuchElementException`），而名单是页面常量，
 * 少一格是随时可能发生的事（这一轮就真的少了一格）。抽成纯函数之后这一格可以用
 * [KbFileInitialSelectionTest]（已删）穷举，不必把 Activity 拖进来。
 *
 * [DEFAULT_KB_FILE_PATH] 之外的历史值也一律安全回落：上一版本把 `memory/archive.md`
 * 记进过 lastFile（那一段时间名单里确实有「旧版归档」这一格），现在它不在名单里了——
 * 落到「最近两句」，不崩、也不给用户看一份没登记的档案。
 *
 * 返回 null 只有一种情况：名单是空的（生产上不会发生，测试夹具里会出现）。
 */
internal fun resolveInitialKbFile(files: List<KbFile>, lastFile: String?): KbFile? =
    files.firstOrNull { it.path == lastFile }
        ?: files.firstOrNull { it.path == DEFAULT_KB_FILE_PATH }
        ?: files.firstOrNull()

class KbEditActivity : ComponentActivity() {

    // 数据访问一律经 ViewModel（Activity 不 inject Repository/SecurePrefs）
    private val viewModel: KbEditViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE——知识库编辑页含关系数据，防止最近任务截图泄露
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        //  键盘遮挡这一条只有一个责任人：外框 `ScreenPage` 已经吃了 `imePadding()`
        // （`ui/common/ScreenHeader.kt:63`，那一层是禁区、也不许在这一页再垫第二遍），
        // 所以这一扇窗口自己**不再让位**——用 `SOFT_INPUT_ADJUST_NOTHING` 明确关掉
        // 系统对窗口的"改尺寸/平移"猜测，让键盘高度**只由 Compose 的 `imePadding()` 吃一次**。
        //
        // 为什么是 ADJUST_NOTHING 而不是 ADJUST_RESIZE：`ADJUST_RESIZE` 会让系统先把窗口压矮，
        // 外框的 `imePadding()` 又按 `WindowInsets.ime` 再减一次 ⇒ 同一段键盘空白**扣了两遍**
        // （这正是原始反馈"点进去正文塌成空白、摸黑打字"的结构性成因：`H_page` 被吃了两个键盘高，
        // 一减到固定占位以下，正文编辑器那一档就归零）。分责口径照 `ui/home/ProviderSection.kt:518-525`
        // 那句"这里再让一次就是把同一段空白扣两遍"。
        //
        // 为什么也不退回 `adjustUnspecified`/`adjustPan`：`adjustPan` 是整扇窗口往上平移，
        // Compose 量到的尺寸一个字没变，`weight(1f)` 那套版式压根不会缩；`adjustUnspecified`
        // 让系统自己猜，可能猜成上面任一种。`ADJUST_NOTHING` 把"谁吃键盘"这一件事交回
        // Compose 一侧唯一的那层 `imePadding()`，窗口尺寸不再被系统改动，
        // `WindowInsets.ime` 也才会真的把键盘高度报进来（下面编辑器的 `bringIntoView` 依赖它）。
        //
        // ⚠ 这半条（非 edge-to-edge 窗口里 `imePadding()` 究竟收到几个像素的 ime inset）
        // 本机看不出来：Robolectric 给的 insets 恒为 0，`adb devices` 也空。只能真机 + Layout Inspector 验。
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        // KbName value object 验证——UI 不直接传递任意路径
        val rawName = intent.getStringExtra("kb_name") ?: run { finish(); return }
        val kbName = try {
            com.lovebrain.app.model.KbName(rawName).value
        } catch (e: IllegalArgumentException) {
            finish(); return
        }

        setContent {
            LoveBrainTheme {
                var loaded by remember { mutableStateOf(false) }

                LaunchedEffect(kbName) {
                    viewModel.ensureMigrated(kbName)
                    loaded = true
                }

                if (!loaded) {
                    // 加载态也是一整屏，所以它的外框归同一个所有者 [ScreenPage]（与知识库列表页、
                    // 反馈案例页同一副版式），不再自己拼第二个 `LbScreenScaffold + Box(background)`。
                    // 转圈那一棵照 [LbAsyncState] Loading 档的形状画一次：Primary 色、`Spacing.xl` 见方、
                    // 带 `LbAsyncTags.LOADING` 锚点（与其它页那一格同一 tag，自动化找得到）。
                    ScreenPage(title = "知识库编辑", onBack = { finish() }) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Primary,
                                modifier = Modifier
                                    .size(Spacing.xl)
                                    .testTag(LbAsyncTags.LOADING)
                            )
                        }
                    }
                } else {
                    KbEditScreen(
                        files = KB_FILES,
                        lastFile = viewModel.lastFile,
                        onLastFileChange = { viewModel.lastFile = it },
                        readFile = { path -> viewModel.read(kbName, path) },
                        saveFile = { path, content, version ->
                            viewModel.save(kbName, path, content, version)
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

//  那一棵"把当前行带回视野"用的是 foundation 的 relocation 那一族；本项目锁的
// foundation 1.6.8（BOM 2024.06.00）里 `BringIntoViewRequester` / `Modifier.bringIntoViewRequester`
// 仍挂着 @ExperimentalFoundationApi（class 文件上是 RuntimeInvisibleAnnotations，实测自
// ~/.gradle 缓存那颗 aar），所以这一页要显式 opt-in，与 MessageList / ReplyPrimaryActions 同一写法。
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KbEditScreen(
    files: List<KbFile>,
    lastFile: String?,
    onLastFileChange: (String) -> Unit,
    readFile: suspend (String) -> Pair<String, String>,
    saveFile: suspend (String, String, String?) -> String?,
    onBack: () -> Unit
) {
    // ── 初始文件：持久化记忆 > 默认「最近两句」> 名单第一格（见 [resolveInitialKbFile]）──
    val initialFile = remember(files, lastFile) { resolveInitialKbFile(files, lastFile) }
    // 名单是空的（只有测试夹具会走到这一档）：画一页没有分区的空外框，不抛
    if (initialFile == null) {
        ScreenPage(title = "知识库编辑", onBack = onBack) {
            Text(
                "这个知识库没有可读的分区",
                style = AppTypography.bodyMedium,
                color = TextHint,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)
            )
        }
        return
    }

    var selectedPath by remember { mutableStateOf(initialFile.path) }
    val selected = files.firstOrNull { it.path == selectedPath } ?: initialFile
    // 读屏名字：semantics 的 lambda 不是 @Composable，所以这句在外面取好再闭包进去
    val editorName = stringResource(R.string.kb_edit_editor_hint, selected.label)
    // 同理：下面那几处 `hint = … to true/false` 全在 `scope.launch { }` 里面，
    // 不是 @Composable 位置，拿不到 `stringResource` ⇒ 在这取好、闭包进去。
    // ⚠ 这四条原来是**内联中文**：`LbPrimaryButton` 那把锚点按括号配对取实参时，
    //   整段 `onClick = { … }` 都落进射程里，于是"组件实参里的可见文案"当场从 78 涨到 81
    //   ——**尺变严了，不是债涨了**，但正确的反应是把它们搬进资源，而不是把表填到 81。
    val savedHint = stringResource(R.string.hint_saved)
    val conflictKeptDraftHint = stringResource(R.string.hint_conflict_kept_draft)
    val saveFailedHint = stringResource(R.string.hint_save_failed)
    // 下面三句给状态件那一格用（`ScreenState` 收已解析的 String，不收资源 id）
    val emptyDocHint = stringResource(R.string.kb_edit_empty_hint)
    val readFailedHint = stringResource(R.string.kb_edit_read_failed)
    val retryLabel = stringResource(R.string.action_retry)

    var drafts by remember { mutableStateOf(emptyMap<String, String>()) }
    var saved by remember { mutableStateOf(emptyMap<String, String>()) }
    // 版本快照——每个文件读取时的 SHA-256，保存时做冲突检测
    var versions by remember { mutableStateOf(emptyMap<String, String>()) }
    var loaded by remember { mutableStateOf(false) }
    // 这一次读**有没有读出来**。分开两颗是有原因的：合成一颗（旧写法只有 `loaded`）时，
    // 读失败就退化成"永远在转圈"——用户分不清"还在读"与"读不出来"，
    // 而知识库列表页修的正是同一副形状（见 `kbScreenState` 那份 KDoc）。
    var readFailed by remember { mutableStateOf(false) }
    // 错误态那颗重试唯一真的出口。用 `mutableIntStateOf`：`mutableStateOf(0)` 走装箱，
    // lint 的 AutoboxingStateCreation 当场报，而 lint 预算那把闸不许新增债。
    var reloadTick by remember { mutableIntStateOf(0) }
    var isPreview by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // ── 编辑态状态：内容快照（放弃修改基线）+ 光标记忆 + 页内提示 ──
    val editorStates = remember { mutableStateMapOf<String, TextFieldValue>() }
    /**
     * 「放弃修改」回哪去：按**分区**各存一份进入编辑态那一刻的正文。
     *
     * ⚠ 修掉的一处真缺陷：改之前这一格是 `var editBaseline by remember { mutableStateOf("") }`，
     * 而全文件**没有任何一处给它赋过值**——于是那颗「放弃修改」把 `""` 当基线写盘，
     * 用户点一下"放弃"，那一篇正文就被清空了（还要再点一次才会发现没了）。
     * 现在进入编辑态那一刻按路径存档，取不到存档时回落"这一篇最后一次读回来的正文"，
     * 任何一条路径都不再写空串。
     */
    val editBaselines = remember { mutableStateMapOf<String, String>() }
    var hint by remember { mutableStateOf<Pair<String, Boolean>?>(null) }  // msg to isError；错误持续，成功 2s 消失

    // 异步加载所有文件内容。这一格是整屏唯一真异步的流：`readFile` 转给
    // `KbEditViewModel.read` → `KnowledgeRepository.readFileWithVersion`（Dispatchers.IO 上真读盘）。
    // 读不出来必须落成 `readFailed`，不能让 `loaded` 永远为假——那等于把失败画成"还在读"。
    // `CancellationException` 原样抛（与本页其余三处 catch 同一写法）：吞掉它 = 退出/切库静默半程。
    LaunchedEffect(files, reloadTick) {
        val initial = try {
            files.associate { it.path to readFile(it.path) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            L.w("KbEdit read failed: $selectedPath")
            null
        }
        if (initial != null) {
            drafts = initial.mapValues { it.value.first }
            saved = initial.mapValues { it.value.first }
            versions = initial.mapValues { it.value.second }
        }
        readFailed = initial == null
        loaded = true
    }

    // 成功提示 2 秒自动消失；失败提示常驻直到下次成功
    LaunchedEffect(hint) {
        val h = hint
        if (h != null && !h.second) {
            delay(2000)
            if (hint == h) hint = null
        }
    }

    val isDirty: (String) -> Boolean = { path -> (drafts[path] ?: "") != (saved[path] ?: "") }
    val anyDirty = files.any { isDirty(it.path) }

    // ── 静默自动保存（切文件/退出）；失败返回 false，调用方决定提示 ──
    suspend fun autosave(path: String): Boolean {
        val d = drafts[path] ?: ""
        if (d == (saved[path] ?: "")) return true
        val ver = versions[path]
        return try {
            val newVersion = saveFile(path, d, ver)
            if (newVersion != null) {
                saved = saved + (path to d)
                versions = versions + (path to newVersion)
            } else {
                L.w("KbEdit version conflict: $path, keeping draft")
            }
            newVersion != null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            L.w("KbEdit autosave failed: $path")
            false
        }
    }

    // ── 切换统一入口：自动保存上一个 → 切文件 → 记忆 → 回预览态 ──
    fun switchToFile(target: KbFile) {
        if (target.path == selectedPath) return
        scope.launch {
            val ok = autosave(selectedPath)
            if (!ok) {
                hint = saveFailedHint to true
                return@launch
            }
            selectedPath = target.path
            onLastFileChange(target.path)
            isPreview = true
        }
    }

    // ── 退出：自动保存全部脏文件；失败则留下并红字提示（不静默丢改动）──
    fun saveAllAndExit() {
        scope.launch {
            var allOk = true
            files.forEach { f ->
                if (!autosave(f.path)) allOk = false
            }
            if (allOk) onBack() else hint = saveFailedHint to true
        }
    }

    BackHandler(enabled = anyDirty) { saveAllAndExit() }

    // 这一篇"现在是哪一格"只判一次，交 [kbEditFileScreenState]；下面只负责画那一格。
    val bodyState = kbEditFileScreenState(
        loaded = loaded,
        readFailed = readFailed,
        text = drafts[selectedPath] ?: "",
        isPreview = isPreview,
        emptyMessage = emptyDocHint,
        errorMessage = readFailedHint,
        retry = ScreenAction(retryLabel) {
            loaded = false
            readFailed = false
            reloadTick++
        }
    )

    // 外框原本是自己拼的 `Column.fillMaxSize.background(SurfaceBase).padding(xxxl)`
    // ——和 `ScreenPage` 同一套东西的第二个副本。走 `ScreenPage`（它已 delegate 给
    // `LbScreenScaffold`）之后这一页不再有第二套外框，键盘内边距也只由那一层吃。
    // 唯一有意的视觉差别：页头到内容的间距从旧版这一页的 12dp 变成其它页统一的 16dp。
    // 页头不再有尾部动作：右上那颗「清空」按用户点名整条删除（连同 pendingClear、
    // 确认弹窗与只服务它的那一处冲突提示）。这一页的破坏性动作只剩"改完不保存就退出"，
    // 而那条走的是下面 saveAllAndExit 的既有链路，一秒钟都不会把某一篇正文抹平。
    ScreenPage(
        title = "知识库编辑",
        onBack = { if (anyDirty) saveAllAndExit() else onBack() }
    ) {
        // 编辑态收起分区三排：键盘弹出时它们仍占固定 ~120dp，会挤压正文编辑器。
        // 指导书§7.1："在编辑态精简/收起上方固定区域，保证正文视口与保存动作可达"。
        // 预览态展开三排让用户切文件；编辑态只需要当前分区名 + 编辑器 + 保存。
        if (isPreview) {
        // 三档分区名留在页面上（它们就是这一页的目录），后面那串括号里的是**内部分层说明**
        // ——"画像/当下/积累"这套词是给我们读代码的人用的，用户看见的只需要名字本身。
        val layers = listOf("画像", "当下", "积累")
        layers.forEach { layerName ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)
            ) {
                Text(
                    layerName,
                    style = AppTypography.labelMedium,
                    color = TextSecondary
                )
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    files.filter { it.layer == layerName }.forEach { f ->
                        val isSel = f.path == selectedPath
                        val dirty = isDirty(f.path)
                        TextButton(
                            onClick = { switchToFile(f) },
                            shape = LoveBrainShape.md,
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isSel) PrimaryLight else SurfaceCard
                            ),
                            // 这一排回到 v1.3.1 那一档：可见胶囊本来就只有 ~40dp 高，
                            // 给它垫了 `heightIn(min = 48)`，于是三颗分区按钮一起变大块、
                            // 横排被挤成换行——用户点名的正是这一条。
                            //
                            // 两件事是分开的：**可见高度**归版式（这一屏走 M3 TextButton 自己那一档），
                            // **点得到**归热区。热区这一档买不到 48 是明写的取舍（第3节第1条：
                            // 旧版密度与"每一颗都 48"不可兼得时，保留用户要求的紧凑），
                            // 替代出口是同一排里每颗都带名字、且整排横向可滚，不存在只有 15dp 字能点的死角。
                            //
                            // `Role.Tab` + `selected` 留着，那是"哪一份正开着"唯一的读屏出口：
                            // 选中态本来就只涂在底色上。
                            modifier = Modifier
                                .semantics {
                                    this[SemanticsProperties.Role] = Role.Tab
                                    this.selected = isSel
                                }
                        ) {
                            Text(
                                f.label,
                                style = AppTypography.labelLarge,
                                color = if (isSel) PrimaryDark else TextSecondary,
                                fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal
                            )
                            if (dirty) {
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                DirtyDot()
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(Spacing.xxxl)
                        .height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, SurfaceBase)
                            )
                        )
                )
            }
        }
        //  卡前留白从 12dp（Spacing.lg）压到 4dp（Spacing.sm）：这一格是"分区三排"到"正文卡"之间
        //  唯一一处**纯占位**（不承载信息、不承载热区）。矮视口 + 键盘弹起时，页级固定那 322dp 里
        //  能安全省下的就是它——省下的每一 dp 都直接回到正文编辑器的可用高度上（H_editor = H_page − 固定项）。
        //  页头段(112)、分区三排(198)按紧凑档不许涨、卡内固定(132)是标题行/动作排/内边距，都不在这一格射程里。
        Spacer(modifier = Modifier.height(Spacing.sm))
        } // end if (isPreview)

        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            // 阴影统一收进 2/4 令牌（6→4 为唯一超限修正）
            modifier = Modifier.fillMaxWidth().weight(1f).shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)
        ) {
            // 指导书§7.1：编辑态精简卡内固定区域，让正文编辑器拿到更多可用高度。
            // 预览态保持 xl(24dp)；编辑态用 md(16dp)，上下各省 8dp。
            Column(modifier = Modifier.padding(if (isPreview) Spacing.xl else Spacing.md)) {
                // 页头那一行（标题 + 「编辑 / 预览」那颗）画在状态件**外面**，四格都在。
                // 这不是顺手：空态那句"点右上「编辑」添加"指的正是这一行里那颗 toggle，
                // 它要是跟着 Content 一起消失，就等于把用户指向一个屏幕上没有的按钮。
                // 与改之前唯一的视觉差别：那一次读还没回来时这一行也画出来了（原先整张卡只剩一个转圈）。
                val savedText = drafts[selectedPath] ?: ""
                val editorValue = editorStates[selectedPath] ?: TextFieldValue(savedText)
                val liveLen = if (isPreview) savedText.length else editorValue.text.length

                // 编辑态不画 toggle（底部已有「放弃修改」可以回预览），
                // 标题行只保留分区名+字数标签，由 TextButton 降到纯 Text，省 ~20dp 行高。
                // bottom padding 编辑态用 xs(8dp)，预览态保持 md(16dp)。
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = if (isPreview) Spacing.md else Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selected.label} ｜ $liveLen 字",

                        style = AppTypography.labelMedium,
                        color = TextHint,
                        maxLines = 1
                    )
                    if (isPreview) {
                        TextButton(
                            onClick = {
                                // 每次**从预览进编辑**都把基线对齐"这一篇当前已落盘的正文"（`saved`），
                                // 而不是 `drafts`。改之前这里存的是 `drafts`：预览→编辑→预览（不保存）
                                // →再编辑这一串之后，`drafts` 已经带着上一轮没保存的改动，于是基线被刷成
                                // **脏草稿**，那颗「放弃修改」回的是脏草稿而不是原文，与"放弃恢复原文"不符。
                                // `saved` 才是"最后一次成功落盘/读回的内容"，切分区/自动保存也会同步它，
                                // 所以拿 `saved` 当基线让"放弃"回到真正的原文。编辑→预览这一趟不重写基线，
                                // 纯切渲染，编辑器里的字仍归 editorStates/drafts，一个字都不清。
                                if (isPreview) {
                                    editBaselines[selectedPath] = saved[selectedPath] ?: ""
                                }
                                isPreview = !isPreview
                            }
                            // 这颗与上面那一排分区按钮同一档：v1.3.1 它没有 `heightIn(min = 48)`，
                            // 可见高度由 M3 TextButton 自己那一档给（量到 40dp）。
                            // 这里**不写数**：写了就是给 `UiLayerDependencyContractTest` 那张
                            // "可点链上的数要解得出主人"的表新添一颗没人认领的矮档。
                        ) {
                            Text("编辑", style = AppTypography.labelLarge, color = Primary)
                        }
                    }
                }

                // 四格只在这一处出口画：Loading / Error / Empty 交共用状态件，Content 才进下面两分支。
                LbAsyncState(state = bodyState, modifier = Modifier.fillMaxWidth().weight(1f)) { shown ->
                    if (isPreview) {
                        // 大文件分块懒渲染（点开不卡的根因修复：只组合可见块）
                        val previewText = remember(selectedPath, shown) {
                            prettyForPreview(selected.path, shown)
                        }
                        val previewChunks = remember(previewText) {
                            previewText.split(Regex("\n\\s*\n"))
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                        }
                        LazyColumn(
                            // 编辑/预览两支的**根**都改成 `fillMaxSize()`：`:536` 传下去的
                            // `Modifier.fillMaxWidth().weight(1f)` 由（本批并行的）`LbAsyncState`
                            // Content 支接到那一格的容器上，容器拿到 `weight` 之后，这一支把容器填满即可。
                            // 旧写法在这里自己再写一遍 `weight(1f)` 是"Content 支吞掉传入 modifier"逼出来的
                            // 兜底；一旦状态件把 modifier 接上，这棵就不该再抢一份权重（否则 `weight`
                            // 落进 Box 的 BoxScope 直接失效）。见 handoff《2026-10-05-I3b-LbAsyncState接线单》。
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            items(previewChunks.size) { i ->
                                MarkdownText(
                                    text = previewChunks[i],
                                    color = TextPrimary,
                                    fontSize = MarkdownBodyFontSize,
                                    lineHeight = MarkdownBodyLineHeight,
                                    listSingleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // 编辑态：TextFieldValue（光标/选区记忆）；输入实时写 drafts + 字数联动
                        //
                        //  键盘遮挡这一条里，这一棵编辑器管的是"有限高度 + 自己会滚"：
                        // - 这一支的**根**是一棵 `Column(Modifier.fillMaxSize())`：`:536` 交给 `LbAsyncState`
                        //   的 `weight(1f)` 由（本批并行的）Content 支接到那一格容器上，容器定高后这一棵
                        //   把容器填满，其中 `OutlinedTextField` 用 `weight(1f)` 吃掉"除固定排以外"的高度；
                        //   [KbEditDimens.EDITOR_MIN_HEIGHT_DP] 只是地板，免得它被压成一粒
                        //   （⚠ 地板不是修复：父层真给 0 时它只会溢出，见 §7.1 那句告诫）；
                        // - 文本超出这一棵自己的视口时，`OutlinedTextField` 内部滚动，
                        //   并由 [bringIntoViewRequester] 在键盘弹起/光标移动时把当前行要回来。
                        //
                        // ⚠ 键盘责任**只有一层**（本页已单一化，见 onCreate 里 `SOFT_INPUT_ADJUST_NOTHING`）：
                        // 外框 `ScreenPage` 已经吃了 `imePadding()`（`ui/common/ScreenHeader.kt:63`，禁区），
                        // 这一页不再自己垫第二遍，也不靠给编辑器加高度去"补" —— 那样只会把同一段键盘空白
                        // 扣两遍或把保存挤出屏。窗口尺寸不由系统改，`WindowInsets.ime` 才报得进键盘高度，
                        // 下面那棵编辑器的 `bringIntoView` 才有触发条件。两半都要真机验（本机 insets 恒 0）。
                        val bringIntoViewRequester = remember { BringIntoViewRequester() }
                        val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
                        LaunchedEffect(imeBottomPx, editorValue.selection, selectedPath) {
                            if (imeBottomPx > 0) bringIntoViewRequester.bringIntoView()
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = editorValue,
                            onValueChange = { v ->
                                editorStates[selectedPath] = v
                                drafts = drafts + (selectedPath to v.text)
                            },
                            // 这颗是全仓最大的一棵输入框（整篇正文编辑器），
                            // 但它以前**既没有文案也没有 contentDescription**——读屏只念"编辑框"，
                            // 用户听不出自己在编辑哪一格。名字取"编辑《当前分区》正文"，
                            // 分区名就是屏幕上那行标题，不另造一套说法。
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = KbEditDimens.EDITOR_MIN_HEIGHT_DP.dp)
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .semantics { contentDescription = editorName },
                            textStyle = AppTypography.bodyLarge.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                // 「摸黑打字」根因之一：colors 没钉文字色与容器底，M3 默认的 onSurface/transparent
                                // 在这套底色下落成不可读。这里钉成与预览那一支同一对（TextPrimary 字 / SurfaceCard 底）。
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard,
                                focusedBorderColor = PrimarySubtle,
                                unfocusedBorderColor = Border,
                                cursorColor = Primary
                            )
                        )
                        // 指导书§7.1：编辑态用更紧凑的间距（xs=4dp），预览态保持 sm(8dp)。
                        Spacer(modifier = Modifier.height(if (isPreview) Spacing.sm else Spacing.xs))
                        // 页内提示行（禁 Toast 铁律：成功小字 2s 消失，失败红字常驻）
                        hint?.let { (msg, isError) ->
                            Text(
                                msg,
                                style = AppTypography.labelSmall,
                                color = if (isError) Error else TextHint,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 放弃修改：恢复进入编辑态前内容，落盘对齐后回预览
                            TextButton(
                                onClick = {
                                // 取不到基线时回落"这一篇最后一次读回来的正文"，
                                // 绝不回落空串——旧写法正是拿空串当基线，点一下就抹掉一篇。
                                val baseline = editBaselines[selectedPath]
                                    ?: saved[selectedPath]
                                    ?: ""
                                drafts = drafts + (selectedPath to baseline)
                                editorStates.remove(selectedPath)
                                scope.launch {
                                    try {
                                        val newVer = saveFile(selectedPath, baseline, versions[selectedPath])
                                        if (newVer != null) {
                                            saved = saved + (selectedPath to baseline)
                                            versions = versions + (selectedPath to newVer)
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        L.w("KbEdit discard save failed: $selectedPath")
                                    }
                                    isPreview = true
                                }
                            }) {
                                Text("放弃修改", style = AppTypography.labelMedium, color = TextHint)
                            }
                            Spacer(Modifier.weight(1f))
                            // 编辑态的唯一主动作归设计系统那一颗。先量：旧版这颗是
                            // **78x44dp**——44 由页面自己的常量钉死，热区到不了下限，
                            // 是这一屏唯一没过的一档。归并之后可见高度与热区同走 48：
                            // 想要旧版那 44dp 的观感，得在设计系统那颗上开一档具名矮档、
                            // 并把热区留在外层透明盒里（`CompactInput` 的两层写法），
                            // 页面这一侧不该再自己抄一个 44 出来。
                            LbPrimaryButton(
                                state = LbButtonState.Idle,
                                label = stringResource(R.string.kb_save),
                                onClick = {
                                    val text = editorValue.text
                                    scope.launch {
                                        val ver = versions[selectedPath]
                                        try {
                                            val newVer = saveFile(selectedPath, text, ver)
                                            if (newVer != null) {
                                                saved = saved + (selectedPath to text)
                                                versions = versions + (selectedPath to newVer)
                                                editorStates.remove(selectedPath)
                                                hint = savedHint to false
                                                isPreview = true
                                            } else {
                                                L.w("KbEdit save conflict: ${selected.path}")
                                                hint = conflictKeptDraftHint to true
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            L.w("KbEdit manual save failed: ${selected.path}")
                                            hint = saveFailedHint to true
                                        }
                                    }
                                }
                            )
                        }
                        } // ← Column(fillMaxSize) 这一支的根闭合
                    }
                }
            }
        }
    }

}

/**
 * 编辑页"这一篇现在是哪一格"只判这一处，版式交 [LbAsyncState]。
 *
 * 先按代码取证：这一屏**只有一条真流**。左边那一排文件标签不是流——[KB_FILES] 是编译期常量，
 * 12 条（本轮删掉「旧版归档」那一条 UI 入口；`memory/archive.md` 本体、迁移与导出都还在），
 * 不读盘、不会失败；唯一真异步的是 `LaunchedEffect` 里那一次 `readFile`，
 * 它转给 `KbEditViewModel.read` → `KnowledgeRepository.readFileWithVersion`（IO 线程上真读盘）。
 * 四档因此各有各的真输入：
 * - Loading = 那一次读还没回来；
 * - Error = 那次读抛了。`KnowledgeDocumentStore.read` 对**不存在**的文件给空串、不抛，
 *   但对**存在却读不动**的文件走 `File.readText()`，IOException 照抛；改之前这条路上没人接，
 *   异常从 `LaunchedEffect` 穿到组合作用域，而 `loaded` 永远是假 ⇒ 失败被画成"还在读"；
 * - Empty = 读成功而这一篇是空的（新建库刚 seed 出来的 md 就是空的，产品天天走这一格）；
 * - Content = 有正文。
 *
 * 两处与同族两页（[kbScreenState]、[captureScreenState]）不同，都记的是现场行为不是新档位：
 * 1. [isPreview] 进判据。编辑态下"正文为空"是用户正往空框里敲字，画一张空态图会把输入框换掉，
 *    那才是真的把人关死；所以 Empty 只在预览那一档成立。
 * 2. **失败只在预览那一档报，编辑态不落 Error**。旧写法 `readFailed -> Error` 判在 [isPreview] 之前，
 *    于是读失败时连「编辑」都点不开——那颗 toggle 画在状态件**外面**（点得到），但一落到编辑态还是被
 *    `readFailed` 抢先画成 Error，屏幕上根本没有输入框，用户点半天"打不出字"，与塌陷叠加时更像"页面坏了"。
 *    现在编辑态（`!isPreview`）永远交回 Content：`drafts` 里有残留就保住那已有文字继续编辑，读失败也不清空、
 *    不挡输入。失败优先这条**仍然保留在预览那一档**（`readFailed && isPreview -> Error`）：
 *    预览时用残留值去判"这篇是空的"等于对用户撒谎（与 [kbScreenState] 那条"带残留列表也不许画 Content"同一笔），
 *    所以预览落 Error 带重试；用户想自救就点那颗「编辑」进编辑态直接改、改完保存。
 */
internal fun kbEditFileScreenState(
    loaded: Boolean,
    readFailed: Boolean,
    text: String,
    isPreview: Boolean,
    emptyMessage: String,
    errorMessage: String,
    retry: ScreenAction
): ScreenState<String> = when {
    !loaded -> ScreenState.Loading
    // 失败只在预览那一格报；编辑态（!isPreview）绝不落 Error，见上面第 2 条。
    readFailed && isPreview -> ScreenState.Error(errorMessage, retry)
    isPreview && text.isBlank() -> ScreenState.Empty(emptyMessage)
    else -> ScreenState.Content(text)
}

/**
 * 预览那一格的净化口。
 *
 * `internal` 而不是 `private`：单测 `KbEditLessonsPreviewTest` 要拿它跟 `LessonDoc.purify` 比**同一份输出**
 * ——"两边共用同一净化函数"这件事只有被直接比过才算钉住（藏在文件里就只是一句注释）。
 */
internal fun prettyForPreview(path: String, content: String): String {
    // 所有文件都先去 HTML 注释，不只是 plan.md
    val noComments = if (path == LessonDoc.LESSONS_PATH) {
        // 经验那一格走 [LessonDoc.purify]——生成 prompt 那一路用的是**同一颗**函数。
        // 各洗各的就会长成用户这轮撞上的样子：界面把模板藏好了，AI 还在照着模板读。
        LessonDoc.purify(content)
    } else {
        stripHtmlComments(content)
    }
    if (path != "moment/plan.md") return noComments
    // plan.md 额外格式化事项行
    val sb = StringBuilder()
    noComments.lines().forEach { line ->
        val t = line.trim()
        when {
            t.isEmpty() -> sb.append("\n")
            t.startsWith("#") -> sb.append(line).append("\n")
            t.contains("|") -> {
                val parts = t.split("|").map { it.trim() }
                if (parts.size >= 3 && parts[0].isNotBlank()) {
                    val chain = parts.drop(2).joinToString("|")
                    val latest = chain.substringAfterLast("→", chain).trim().substringAfter("]")
                    sb.append("- ").append(parts[0]).append(" · ").append(parts[1])
                    if (latest.isNotBlank()) sb.append(" · 最新：").append(latest)
                    sb.append("\n")
                } else {
                    sb.append(line).append("\n")
                }
            }
            else -> sb.append(line).append("\n")
        }
    }
    return sb.toString()
}

/** 剥离 HTML 注释（<!-- ... -->，跨行也处理） */
private fun stripHtmlComments(text: String): String {
    val regex = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    return regex.replace(text, "").trim()
}
