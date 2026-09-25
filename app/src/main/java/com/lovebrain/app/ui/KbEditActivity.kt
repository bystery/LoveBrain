package com.lovebrain.app.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import com.lovebrain.app.core.designsystem.LbDialog
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lovebrain.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.heightIn
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

/** 知识库编辑页内部尺寸常量 */
private object KbEditDimens {
    const val DIRTY_DOT_SIZE_DP = 6         // 脏标记小圆点尺寸
    // ⚠ 本格之后**没人再引用它**（只留这一行记下"44 这一档是从哪来的"，别把它接回任何按钮）：
    //   它原来把编辑态那颗「保存」钉在 44dp 高——语义树实量 **78x44dp**，
    //   是 :596"无小于 48dp 的热区"在这一屏唯一没过的一处。主动作的高度现在归
    //   `LB_PRIMARY_MIN_HEIGHT_DP`（全站那一颗），页面常量不许再自己开一档。
    const val ACTION_BUTTON_HEIGHT_DP = 44
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
    KbFile("军师日志", "memory/reflect_history.md", layer = "积累"),
    // 旧迁移写入 archive.md，界面原先看不到——加入列表使历史记录可见
    KbFile("旧版归档", "memory/archive.md", layer = "积累")
)

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
                    // 加载态也是一整屏，所以它的外框也归同一个所有者——
                    // 之前这里第二个 `Box(fillMaxSize).background(SurfaceBase)` 是同一套东西的副本
                    LbScreenScaffold {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Primary)
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

@Composable
internal fun KbEditScreen(
    files: List<KbFile>,
    lastFile: String?,
    onLastFileChange: (String) -> Unit,
    readFile: suspend (String) -> Pair<String, String>,
    saveFile: suspend (String, String, String?) -> String?,
    onBack: () -> Unit
) {
    // ── 初始文件：持久化记忆 > 默认「最近两句」 ──
    val initialFile = remember(files, lastFile) {
        files.firstOrNull { it.path == lastFile } ?: files.first { it.path == "moment/recent.md" }
    }

    var selectedPath by remember { mutableStateOf(initialFile.path) }
    val selected = files.first { it.path == selectedPath }
    // 读屏名字：semantics 的 lambda 不是 @Composable，所以这句在外面取好再闭包进去
    val editorName = stringResource(R.string.kb_edit_editor_hint, selected.label)
    // 同理：下面那几处 `hint = … to true/false` 全在 `scope.launch { }` 里面，
    // 不是 @Composable 位置，拿不到 `stringResource` ⇒ 在这取好、闭包进去。
    // ⚠ 这四条原来是**内联中文**：`LbPrimaryButton` 那把锚点按括号配对取实参时，
    //   整段 `onClick = { … }` 都落进射程里，于是"组件实参里的可见文案"当场从 78 涨到 81
    //   ——**尺变严了，不是债涨了**，但正确的反应是把它们搬进资源，而不是把表填到 81。
    val savedHint = stringResource(R.string.hint_saved)
    val conflictKeptDraftHint = stringResource(R.string.hint_conflict_kept_draft)
    val conflictReopenHint = stringResource(R.string.hint_conflict_reopen)
    val saveFailedHint = stringResource(R.string.hint_save_failed)

    var drafts by remember { mutableStateOf(emptyMap<String, String>()) }
    var saved by remember { mutableStateOf(emptyMap<String, String>()) }
    // 版本快照——每个文件读取时的 SHA-256，保存时做冲突检测
    var versions by remember { mutableStateOf(emptyMap<String, String>()) }
    var loaded by remember { mutableStateOf(false) }
    var isPreview by remember { mutableStateOf(true) }
    var pendingClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ── 编辑态状态：内容快照（放弃修改基线）+ 光标记忆 + 页内提示 ──
    val editorStates = remember { mutableStateMapOf<String, TextFieldValue>() }
    var editBaseline by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<Pair<String, Boolean>?>(null) }  // msg to isError；错误持续，成功 2s 消失

    // 异步加载所有文件内容
    LaunchedEffect(files) {
        val initial = files.associate { it.path to readFile(it.path) }
        drafts = initial.mapValues { it.value.first }
        saved = initial.mapValues { it.value.first }
        versions = initial.mapValues { it.value.second }
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

    // §6.1：外框原本是自己拼的 `Column.fillMaxSize.background(SurfaceBase).padding(xxxl)`
    // ——和 `ScreenPage` 同一套东西的第二个副本。走 `ScreenPage`（它已 delegate 给
    // `LbScreenScaffold`）之后这一页不再有第二套外框。
    // 唯一有意的视觉差别：页头到内容的间距从 12dp 变成其它页统一的 16dp。
    ScreenPage(
        title = "知识库编辑",
        onBack = { if (anyDirty) saveAllAndExit() else onBack() },
        trailing = {
            if ((drafts[selectedPath] ?: "").isNotBlank()) {
                TextButton(
                    onClick = { pendingClear = true },
                    // 实量 **58x40dp**：M3 的"至少 48dp"是 `minimumInteractiveContainer` 装饰，
                    // 带 `role=Button` 的这一颗自己只有 40 高（坑表 92，同一族第四次撞）。
                    modifier = Modifier.heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                ) {
                    Text("清空", color = Error, style = AppTypography.labelLarge)
                }
            }
        }
    ) {
        val layers = listOf("画像" to "你们是谁，走到哪了", "当下" to "当前话题和状态", "积累" to "经验和原始记录")
        layers.forEach { (layerName, layerDesc) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)
            ) {
                Text(
                    layerName,
                    style = AppTypography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    "（$layerDesc）",
                    style = AppTypography.labelSmall,
                    color = TextHint
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
                        // 次级按钮样式
                        TextButton(
                            onClick = { switchToFile(f) },
                            shape = LoveBrainShape.md,
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isSel) PrimaryLight else SurfaceCard
                            ),
                            // 两条一起补（本机实量这三颗是 `role=Button selected=null 60x40dp`）：
                            // ① 热区垫到 48——和上面那颗同一个"框架的保证是装饰"的形状；
                            // ② **哪一份正开着**原来只涂在底色上，读屏听不出来（:532 那一栏）。
                            //    `role = Role.Tab` 是"这一排互斥、当前只有一个"的意思；
                            //    `selected` 交布尔值，不能只交 true（那样谁都亮）。
                            modifier = Modifier
                                .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
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
        Spacer(modifier = Modifier.height(Spacing.lg))

        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            // 阴影统一收进 2/4 令牌（6→4 为唯一超限修正）
            modifier = Modifier.fillMaxWidth().weight(1f).shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)
        ) {
            Column(modifier = Modifier.padding(Spacing.xl)) cardContent@{
                if (!loaded) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                    return@cardContent
                }
                val savedText = drafts[selectedPath] ?: ""
                val editorValue = editorStates[selectedPath] ?: TextFieldValue(savedText)
                val liveLen = if (isPreview) savedText.length else editorValue.text.length

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selected.label} ｜ $liveLen 字",

                        style = AppTypography.labelMedium,
                        color = TextHint,
                        maxLines = 1
                    )
                    TextButton(
                        onClick = { isPreview = !isPreview },
                        modifier = Modifier.heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    ) {
                        Text(if (isPreview) "编辑" else "预览", style = AppTypography.labelLarge, color = Primary)
                    }
                }

                if (isPreview) {
                    if (savedText.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("还没有内容，点右上「编辑」添加", style = AppTypography.bodySmall, color = TextHint)
                        }
                    } else {
                        // 大文件分块懒渲染（点开不卡的根因修复：只组合可见块）
                        val previewText = remember(selectedPath, savedText) {
                            prettyForPreview(selected.path, savedText)
                        }
                        val previewChunks = remember(previewText) {
                            previewText.split(Regex("\n\\s*\n"))
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
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
                    }
                } else {
                    // 编辑态：TextFieldValue（光标/选区记忆）；输入实时写 drafts + 字数联动
                    OutlinedTextField(
                        value = editorValue,
                        onValueChange = { v ->
                            editorStates[selectedPath] = v
                            drafts = drafts + (selectedPath to v.text)
                        },
                        // §6.5 第②栏：这颗是全仓最大的一棵输入框（整篇正文编辑器），
                        // 但它以前**既没有文案也没有 contentDescription**——读屏只念"编辑框"，
                        // 用户听不出自己在编辑哪一格。名字取"编辑《当前分区》正文"，
                        // 分区名就是屏幕上那行标题，不另造一套说法。
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .semantics { contentDescription = editorName },
                        textStyle = AppTypography.bodyLarge.copy(color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimarySubtle,
                            unfocusedBorderColor = Border,
                            cursorColor = Primary
                        )
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
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
                            modifier = Modifier.heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp),
                            onClick = {
                            val baseline = editBaseline
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
                        // §6.1 :479——编辑态的唯一主动作归 `LbPrimaryButton`。
                        // 先量：实量 **78x44dp** ⇒ 这一颗是**真缺陷**（:596"无小于 48dp 的热区"没过），
                        // 与前面三处不同：它的高度由页面自己的常量 `ACTION_BUTTON_HEIGHT_DP` 钉死在 44，
                        // 全站只有这一颗主动作是这样（所以第三把尺上它既算"换门涂色"又算热区缺陷）。
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
                }
            }
        }
    }

    if (pendingClear) {
        LbDialog(
            title = "清空「${selected.label}」？",
            onDismissRequest = { pendingClear = false },
            message = "将清空《${selected.label}》全部内容，不可恢复。确定？",
            confirm = LbDialogAction(
                label = "清空",
                tone = LbDialogActionTone.Destructive,
                onClick = {
                    pendingClear = false
                    val path = selectedPath
                    scope.launch {
                        try {
                            val newVer = saveFile(path, "", versions[path])
                            if (newVer != null) {
                                drafts = drafts + (path to "")
                                saved = saved + (path to "")
                                versions = versions + (path to newVer)
                            } else {
                                L.w("KbEdit clear conflict: $path")
                                hint = conflictReopenHint to true
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            L.w("KbEdit clear failed: $path")
                            hint = "清空失败，请重试" to true
                        }
                    }
                }
            ),
            dismiss = LbDialogAction("取消", { pendingClear = false },
                tone = LbDialogActionTone.Muted)
        )
    }

}

private fun prettyForPreview(path: String, content: String): String {
    // 所有文件都先去 HTML 注释，不只是 plan.md
    val noComments = stripHtmlComments(content)
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
