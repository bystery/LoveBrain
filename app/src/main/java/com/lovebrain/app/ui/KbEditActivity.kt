package com.lovebrain.app.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.ui.common.ScreenHeader
import com.lovebrain.app.ui.panel.MarkdownText
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.util.L
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private data class KbFile(val label: String, val path: String, val layer: String)

/** 知识库编辑页内部尺寸常量 */
private object KbEditDimens {
    const val DIRTY_DOT_SIZE_DP = 6         // 脏标记小圆点尺寸
    const val ACTION_BUTTON_HEIGHT_DP = 44  // 保存按钮高度
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
    KbFile("最近两句", "moment/recent.md", layer = "当下"),
    KbFile("在聊什么", "moment/topic.md", layer = "当下"),
    KbFile("此刻状态", "moment/scene.md", layer = "当下"),
    KbFile("进行中事项", "moment/plan.md", layer = "当下"),
    KbFile("话题档案", "memory/raw_topic.md", layer = "积累"),
    KbFile("经验", "memory/lessons.md", layer = "积累"),
    KbFile("谈心记录", "memory/counseling_log.md", layer = "积累"),
    KbFile("军师日志", "memory/reflect_history.md", layer = "积累")
)

class KbEditActivity : ComponentActivity() {

    private val repo: KnowledgeRepository by inject()
    private val securePrefs: SecurePrefs by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kbName = intent.getStringExtra("kb_name") ?: run { finish(); return }

        setContent {
            LoveBrainTheme {
                var loaded by remember { mutableStateOf(false) }

                LaunchedEffect(kbName) {
                    repo.migrateIfNeeded(kbName)
                    loaded = true
                }

                if (!loaded) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(SurfaceBase),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                } else {
                    KbEditScreen(
                        files = KB_FILES,
                        lastFile = securePrefs.lastKbEditFile,
                        onLastFileChange = { securePrefs.lastKbEditFile = it },
                        readFile = { path ->
                            val content = repo.readFile(kbName, path)
                            if (content.isNotBlank()) content
                            else {
                                val oldPath = KnowledgeRepository.OLD_PATH_MAP[path]
                                // 新路径为空时，才尝试回退旧路径；且仅当旧路径非空时才读取
                                val fallbackContent = oldPath?.let { repo.readFile(kbName, it) }.orEmpty()
                                fallbackContent
                            }
                        },
                        saveFile = { path, content -> repo.writeFile(kbName, path, content) },
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun KbEditScreen(
    files: List<KbFile>,
    lastFile: String?,
    onLastFileChange: (String) -> Unit,
    readFile: suspend (String) -> String,
    saveFile: suspend (String, String) -> Unit,
    onBack: () -> Unit
) {
    // ── 初始文件：持久化记忆 > 默认「最近两句」 ──
    val initialFile = remember(files, lastFile) {
        files.firstOrNull { it.path == lastFile } ?: files.first { it.path == "moment/recent.md" }
    }

    var selectedPath by remember { mutableStateOf(initialFile.path) }
    val selected = files.first { it.path == selectedPath }

    var drafts by remember { mutableStateOf(emptyMap<String, String>()) }
    var saved by remember { mutableStateOf(emptyMap<String, String>()) }
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
        drafts = initial
        saved = initial
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
        return runCatching { saveFile(path, d) }
            .onSuccess { saved = saved + (path to d) }
            .onFailure { L.w("KbEdit autosave failed: $path") }
            .isSuccess
    }

    // ── 切换统一入口：自动保存上一个 → 切文件 → 记忆 → 回预览态 ──
    fun switchToFile(target: KbFile) {
        if (target.path == selectedPath) return
        scope.launch {
            val ok = autosave(selectedPath)
            if (!ok) {
                hint = "保存失败，请重试" to true
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
            if (allOk) onBack() else hint = "保存失败，请重试" to true
        }
    }

    BackHandler(enabled = anyDirty) { saveAllAndExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
            .padding(Spacing.xxxl)
    ) {
        // 页头：最新式（返回 + 标题 + 清空，48dp 行 + 分割线）
        ScreenHeader(
            title = "知识库编辑",
            onBack = { if (anyDirty) saveAllAndExit() else onBack() }
        ) {
            if ((drafts[selectedPath] ?: "").isNotBlank()) {
                TextButton(onClick = { pendingClear = true }) {
                    Text("清空", color = Error, style = AppTypography.labelLarge)
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.lg))

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
                        // 之前按钮样式（792b040）：TextButton 浅蓝底 + 深蓝字（主人复点）
                        TextButton(
                            onClick = { switchToFile(f) },
                            shape = LoveBrainShape.md,
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isSel) PrimaryLight else SurfaceCard
                            )
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
            // ：阴影统一收进 2/4 令牌（6→4 为任务单批准的唯一超限修正）
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
                    TextButton(onClick = { isPreview = !isPreview }) {
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
                        modifier = Modifier.fillMaxWidth().weight(1f),
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
                        TextButton(onClick = {
                            val baseline = editBaseline
                            drafts = drafts + (selectedPath to baseline)
                            editorStates.remove(selectedPath)
                            scope.launch {
                                runCatching { saveFile(selectedPath, baseline) }
                                    .onSuccess { saved = saved + (selectedPath to baseline) }
                                isPreview = true
                            }
                        }) {
                            Text("放弃修改", style = AppTypography.labelMedium, color = TextHint)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                val text = editorValue.text
                                scope.launch {
                                    runCatching { saveFile(selectedPath, text) }
                                        .onSuccess {
                                            saved = saved + (selectedPath to text)
                                            editorStates.remove(selectedPath)
                                            hint = "已保存" to false
                                            isPreview = true
                                        }
                                        .onFailure {
                                            L.w("KbEdit manual save failed: ${selected.path}")
                                            hint = "保存失败，请重试" to true
                                        }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = LoveBrainShape.md,
                            modifier = Modifier.height(KbEditDimens.ACTION_BUTTON_HEIGHT_DP.dp)
                        ) {
                            Text("保存", style = AppTypography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text("清空「${selected.label}」？", style = AppTypography.titleLarge) },
            text = {
                Text(
                    "将清空《${selected.label}》全部内容，不可恢复。确定？",
                    style = AppTypography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingClear = false
                    val path = selectedPath
                    scope.launch {
                        runCatching { saveFile(path, "") }
                            .onSuccess {
                                drafts = drafts + (path to "")
                                saved = saved + (path to "")
                            }
                            .onFailure {
                                L.w("KbEdit clear failed: $path")
                                hint = "清空失败，请重试" to true
                            }
                    }
                }) { Text("清空", color = Error, style = AppTypography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) {
                    Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                }
            }
        )
    }

}

private fun prettyForPreview(path: String, content: String): String {
    if (path != "moment/plan.md") return content
    val sb = StringBuilder()
    var inComment = false
    content.lines().forEach { line ->
        val t = line.trim()
        when {
            inComment -> {
                if (t.contains("-->")) inComment = false
            }
            t.startsWith("<!--") -> if (!t.contains("-->")) inComment = true
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
