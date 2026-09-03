package com.lovebrain.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lovebrain.app.R
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.ui.common.CompactInput
import com.lovebrain.app.ui.common.RowActionButton
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private data class ObQuestion(val title: String, val options: List<String>)

/** 知识库管理页内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object KbDimens {
    const val EMPTY_ICON_CONTAINER_DP = 72    // 空态图标容器（语义例外：大于通用 48）
    const val EMPTY_ICON_SIZE_DP = 36         // 空态图标本体（语义例外）
    const val PRIMARY_ACTION_HEIGHT_DP = 48   // 新建/导入/完成大按钮高度（主人 2026-08-31 定稿 48dp）
    const val EDIT_ICON_SIZE_DP = 14          // 重命名小铅笔图标
    const val ONBOARDING_SPINNER_SIZE_DP = 18 // 问卷生成中按钮内转圈尺寸
    const val PROGRESS_BAR_HEIGHT_DP = 4     // 问卷答题进度条高度
}

private val ONBOARDING_QUESTIONS = listOf(
    ObQuestion("你们现在是什么关系？", listOf("刚认识/朋友", "暧昧中", "已在一起", "已婚", "说不清")),
    ObQuestion("在一起多久了？", listOf("还没在一起", "3个月内", "3个月到1年", "1年以上")),
    ObQuestion("你们主要怎么相处？", listOf("异地", "同城常见面", "同城但少见", "基本线上")),
    ObQuestion("她大概什么性格？", listOf("活泼外向", "安静内敛", "时冷时热", "理性独立")),
    ObQuestion("你在感情里最容易犯的毛病？", listOf("太焦虑总想确认", "太冷淡不善表达", "太讨好没底线", "太自我忽略她")),
    ObQuestion("你们现在最大的状况？", listOf("刚认识在试探", "热恋很甜", "有点矛盾在磨合", "平稳但有点淡", "正在闹矛盾"))
)

class KnowledgeBaseActivity : ComponentActivity() {

    private val repo: KnowledgeRepository by inject()
    private val deepSeek: DeepSeekRepository by inject()
    private var pendingExportKb: String? = null

    //  ：未就绪二选一弹窗态（守卫拦截时的待定 onDone 载荷）
    private val noProviderDialogVisible = mutableStateOf(false)
    private var pendingNoProviderDone: (() -> Unit)? = null

    // C1 修复：建库/AI 结果反馈通道（空分支原样补告知）
    private val kbFeedback = mutableStateOf<String?>(null)

    // P0-① 修复：持有 onboarding 生成协程 Job，onDismiss 时显式 cancel
    private var onboardingJob: kotlinx.coroutines.Job? = null

    private fun createEmptyKb(onDone: () -> Unit) {
        lifecycleScope.launch {
            val name = autoKbName()
            val ok = runCatching { repo.create(name, "新知识库") }.isSuccess
            if (!ok) {
                kbFeedback.value = "创建失败：可能名称重复，请重试"
            }
            onDone()
        }
    }

    private fun createKbWithOnboarding(
        answers: Map<Int, String>,
        myName: String,
        herName: String,
        onDone: () -> Unit
    ) {
        //  ：建库守卫（存量豁免内处置：复用本文件已注入 deepSeek 的读通道，
        // 不搬分层，决策留档见 记账）：未就绪 → 二选一弹窗（继续=空模板库）
        val ready = deepSeek.getActiveTicket()?.model?.isNotBlank() == true &&
            !deepSeek.getActiveApiKey().isNullOrBlank()
        if (!ready) {
            pendingNoProviderDone = onDone
            noProviderDialogVisible.value = true
            return
        }
        // P0-① 修复：存 Job 引用，onDismiss 时 cancel → generateRaw 抛 CancellationException 跳出
        onboardingJob = lifecycleScope.launch {
            val name = autoKbName()
            val system = readEngineAsset(com.lovebrain.app.domain.AssetRegistry.ONBOARDING)
            val user = buildString {
                append("## 用户的选择题答案\n")
                ONBOARDING_QUESTIONS.forEachIndexed { i, q ->
                    append("- ").append(q.title).append("：").append(answers[i] ?: "未回答").append("\n")
                }
                append("- 你的称呼：").append(myName.ifBlank { "（未提供）" }).append("\n")
                append("- 她的称呼：").append(herName.ifBlank { "（未提供）" }).append("\n")
            }
            val raw = runCatching {
                withContext(Dispatchers.IO) { deepSeek.generateRaw(system, user) }
            }.getOrDefault("")
            // P0-① 修复：协程被 cancel 后 withContext 恢复会抛 CancellationException，
            // runCatching 吞掉后 raw="" 且 isActive=false——这里拦住不建库
            if (!isActive) {
                onDone()
                return@launch
            }

            val display = parseSection(raw, "===DISPLAY===", "===STAGE===")
                .ifBlank { herName.ifBlank { "我的她" } }
            val stage = parseSection(raw, "===STAGE===", "===ME===").ifBlank { "待确定" }
            val me = parseSection(raw, "===ME===", "===HER===")
            val her = parseSection(raw, "===HER===", "===WARMTH===")
            val warmth = raw.substringAfter("===WARMTH===", "").trim()

            val ok = runCatching { repo.create(name, display) }.isSuccess
            if (ok) {
                if (me.isNotBlank()) repo.writeFile(name, "understand/me.md", me)
                if (her.isNotBlank()) repo.writeFile(name, "understand/her.md", her)
                if (warmth.isNotBlank()) repo.writeFile(name, "understand/warmth.md", warmth)
                repo.updateStage(name, stage)
                // C1 修复：成功分支补充反馈——AI 分析返回空不再假装成功（降级链如实告知）
                if (raw.isBlank()) {
                    kbFeedback.value = "AI 分析失败，已创建空模板库，可稍后在编辑页补充画像"
                } else {
                    kbFeedback.value = "知识库已创建，画像已生成"
                }
            } else {
                kbFeedback.value = "创建失败：可能名称重复，请重试"
            }
            onDone()
        }
    }

    // C2 修复：去掉 % 1000000（每 16.7 分钟循环碰撞），用全时间戳 + 随机后缀
    private fun autoKbName(): String =
        "kb_" + System.currentTimeMillis().toString(36) + "_" + (0..9999).random().toString(36)

    private fun readEngineAsset(path: String): String = runCatching {
        assets.open(path).bufferedReader().use { it.readText() }
    }.onFailure { com.lovebrain.app.util.L.e("readEngineAsset missing/failed: $path", it) }
        .getOrDefault("")

    private fun parseSection(text: String, startMarker: String, endMarker: String): String {
        val start = text.indexOf(startMarker)
        if (start < 0) return ""
        val contentStart = start + startMarker.length
        val end = text.indexOf(endMarker, contentStart)
        return (if (end > contentStart) text.substring(contentStart, end) else "").trim()
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val kbName = pendingExportKb
        pendingExportKb = null
        if (uri == null || kbName == null) return@registerForActivityResult
        // （ 清偿）：zip 下沉 IO 线程，防大库阻塞主线程（ANR 隐患）；函数体零改动
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { os ->
                    zipKbFolder(File(filesDir, "knowledge/$kbName"), kbName, ZipOutputStream(os))
                }
                withContext(Dispatchers.Main) {
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                }
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        // （ 清偿）：解压下沉 IO 线程，防大库阻塞主线程（ANR 隐患）；函数体零改动
        // 激活修正+recreate 移入同一协程、置于解压成功之后，时序与基线一致
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    unzipToKnowledge(ZipInputStream(input), File(filesDir, "knowledge"))
                }
                // 导入后强制修正 active 状态，防止导入的 KB 带 active=true 导致双激活
                val currentActive = repo.getActive()?.name ?: repo.listAll().firstOrNull()?.name
                if (currentActive != null) repo.setActive(currentActive)
                withContext(Dispatchers.Main) {
                    recreate()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LoveBrainTheme {
                var version by remember { mutableStateOf(0) }
                val reload = { version++ }
                var kbs by remember(version) { mutableStateOf(emptyList<KnowledgeBase>()) }
                var active by remember(version) { mutableStateOf<KnowledgeBase?>(null) }
                var showOnboarding by remember { mutableStateOf(false) }

                LaunchedEffect(version) {
                    kbs = repo.listAll()
                    active = repo.getActive()
                }

                KbListScreen(
                    kbs = kbs,
                    activeName = active?.name,
                    onActivate = { name ->
                        lifecycleScope.launch {
                            repo.setActive(name)
                            val displayName = repo.getActive()?.displayName ?: name
                            reload()
                        }
                    },
                    onNewKb = { showOnboarding = true },
                    onRename = { name, newDisplay ->
                        lifecycleScope.launch {
                            repo.updateDisplayName(name, newDisplay)
                            reload()
                        }
                    },
                    onDelete = { name ->
                        lifecycleScope.launch {
                            val ok = repo.delete(name)
                            reload()
                            if (ok) {
                            } else {
                            }
                        }
                    },
                    onEdit = { name ->
                        startActivity(Intent(this, KbEditActivity::class.java).putExtra("kb_name", name))
                    },
                    // ：导出警示 Compose 化——确认链路上提至此，弹窗由 KbListScreen pendingExport 承载
                    onConfirmExport = { name ->
                        pendingExportKb = name
                        exportLauncher.launch("kb_${name}.zip")
                    },
                    onImport = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    onBack = { finish() }
                )

                if (showOnboarding) {
                    OnboardingScreen(
                        onDismiss = { showOnboarding = false },
                        onSkip = {
                            createEmptyKb {
                                showOnboarding = false
                                reload()
                            }
                        },
                        onComplete = { answers, myName, herName ->
                            createKbWithOnboarding(answers, myName, herName) {
                                showOnboarding = false
                                reload()
                            }
                        },
                        // P0-① 修复：生成中取消——cancel 协程后关闭页面
                        onCancelGenerating = {
                            onboardingJob?.cancel()
                            onboardingJob = null
                            showOnboarding = false
                        }
                    )
                }

                //  ：未配置供应商二选一弹窗（继续 = 空模板库，取消 = 返回）
                if (noProviderDialogVisible.value) {
                    AlertDialog(
                        onDismissRequest = {
                            noProviderDialogVisible.value = false
                            pendingNoProviderDone = null
                        },
                        title = { Text("未配置模型供应商", style = AppTypography.titleLarge) },
                        text = {
                            Text(
                                "未配置模型供应商，只能创建空模板库",
                                style = AppTypography.bodyMedium,
                                color = TextSecondary
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val done = pendingNoProviderDone
                                noProviderDialogVisible.value = false
                                pendingNoProviderDone = null
                                if (done != null) createEmptyKb(done)
                            }) { Text("继续", color = Primary, style = AppTypography.titleMedium) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                noProviderDialogVisible.value = false
                                pendingNoProviderDone = null
                            }) {
                                Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                            }
                        }
                    )
                }

                // C1 修复：建库/生成结果反馈弹窗
                kbFeedback.value?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { kbFeedback.value = null },
                        title = { Text("提示", style = AppTypography.titleLarge) },
                        text = { Text(msg, style = AppTypography.bodyMedium, color = TextSecondary) },
                        confirmButton = {
                            TextButton(onClick = { kbFeedback.value = null }) {
                                Text("知道了", color = Primary, style = AppTypography.titleMedium)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun zipKbFolder(folder: File, prefix: String, zos: ZipOutputStream) {
        if (!folder.exists()) return
        folder.walkTopDown().filter { it.isFile }.forEach { file ->
            val entryName = "$prefix/${file.relativeTo(folder).path.replace('\\', '/')}"
            zos.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        zos.finish()
    }

    /**
     * : 导入知识库 = 解压到暂存区 → 校验 → 原子搬入正式位置。
     * - 不直接写 knowledge/ 树：解压中途磁盘满/进程被杀不会残留半截坏库
     * - 校验三关：顶层恰一个目录 / kb.json 可解码且 name==顶层目录名（与  同判据）/ 无同名碰撞
     * - 任一失败 = 整体中止并清理暂存目录，异常上抛复用现有「导入失败：」提示
     */
    private fun unzipToKnowledge(zis: ZipInputStream, knowledgeRoot: File) {
        val stagingRoot = File(cacheDir, "kb_import_${System.currentTimeMillis()}")
        stagingRoot.mkdirs()
        try {
            // 1. 解压（entry 级 canonical 防护原样保留，根改为暂存根）
            val canonicalStaging = stagingRoot.canonicalPath
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(stagingRoot, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalStaging)) {
                    entry = zis.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                entry = zis.nextEntry
            }

            // 2a. 校验：顶层恰一个目录
            val topDirs = stagingRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
            if (topDirs.size != 1) {
                throw IllegalStateException("知识库包结构无效：应恰有一个顶层目录")
            }
            val topDir = topDirs.first()

            // 2b. 校验：kb.json 可解码且 name 字段 == 顶层目录名（ 同判据，导入时前置拦截）
            val kb = runCatching {
                Json.decodeFromString<KnowledgeBase>(File(topDir, "kb.json").readText())
            }.getOrNull() ?: throw IllegalStateException("知识库元数据缺失或损坏")
            if (kb.name != topDir.name) {
                throw IllegalStateException("知识库元数据校验失败：name 与目录名不一致")
            }

            // 2c. 校验：已有同名知识库 → 碰撞即整体中止（不静默覆盖）
            val target = File(knowledgeRoot, topDir.name)
            if (target.exists()) {
                throw IllegalStateException("已存在同名知识库，导入中止")
            }

            // 3. 原子搬入正式位置（minSdk=26：java.nio.file 可用）
            Files.move(topDir.toPath(), target.toPath())
            // 成功后清理暂存空壳（防御性）
            runCatching { stagingRoot.deleteRecursively() }
        } catch (e: Exception) {
            // 任何失败路径均清理暂存目录后上抛
            runCatching { stagingRoot.deleteRecursively() }
            throw e
        }
    }
}

@Composable
private fun KbListScreen(
    kbs: List<KnowledgeBase>,
    activeName: String?,
    onActivate: (String) -> Unit,
    onNewKb: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
    onConfirmExport: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<KnowledgeBase?>(null) }
    var pendingExport by remember { mutableStateOf<KnowledgeBase?>(null) }

    ScreenPage(title = "知识库管理", onBack = onBack) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
        if (kbs.isEmpty()) {
            // 空状态三要素（第2轮调研：图标 + 友好文案 + 明确 CTA）
            Card(
                shape = LoveBrainShape.lg,
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                // ：阴影统一收进 2/4 令牌（6→4 为任务单批准的唯一超限修正）
                modifier = Modifier.fillMaxWidth().shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(KbDimens.EMPTY_ICON_CONTAINER_DP.dp)
                            .clip(LoveBrainShape.full)
                            .background(PrimaryLight)
                            .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.full),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bubble),
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(KbDimens.EMPTY_ICON_SIZE_DP.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    Text("还没有知识库", style = AppTypography.titleLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        "点下方「新建知识库」，给她建一份专属档案，军师回复会更懂她。",
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            kbs.forEach { kb ->
                KbCard(
                    kb = kb,
                    isActive = kb.name == activeName,
                    onActivate = { onActivate(kb.name) },
                    onRename = { newName -> onRename(kb.name, newName) },
                    onEdit = { onEdit(kb.name) },
                    onExport = { pendingExport = kb },
                    onDelete = { pendingDelete = kb }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Button(
                onClick = { onNewKb() },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = LoveBrainShape.md,
                modifier = Modifier.weight(1f).height(KbDimens.PRIMARY_ACTION_HEIGHT_DP.dp)
            ) {
                Text("新建知识库", style = AppTypography.titleMedium)
            }
            OutlinedButton(
                onClick = onImport,
                shape = LoveBrainShape.md,
                modifier = Modifier.weight(1f).height(KbDimens.PRIMARY_ACTION_HEIGHT_DP.dp)
            ) {
                Text("导入知识库", style = AppTypography.titleMedium, color = TextPrimary)
            }
        }
        }
    }

    pendingDelete?.let { kb ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除知识库「${kb.displayName}」？", style = AppTypography.titleLarge) },
            text = {
                Text("将物理删除该知识库的全部内容，不可恢复。确定删除？", style = AppTypography.bodyMedium, color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = kb.name
                    pendingDelete = null
                    onDelete(n)
                }) { Text("删除", color = Error, style = AppTypography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                }
            }
        )
    }

    // ：导出警示 Compose 化（/：明文 zip 含全部画像/归档，先警示再启动；文案红线逐字不动）
    pendingExport?.let { kb ->
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = { Text("导出提醒", style = AppTypography.titleLarge) },
            text = {
                Text("导出文件是明文，包含她的全部画像、聊天归档与谈心记录。请妥善保管，不要分享给他人。", style = AppTypography.bodyMedium, color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = kb.name
                    pendingExport = null
                    onConfirmExport(n)
                }) { Text("仍要导出", color = Primary, style = AppTypography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { pendingExport = null }) {
                    Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                }
            }
        )
    }
}

@Composable
private fun KbCard(
    kb: KnowledgeBase,
    isActive: Boolean,
    onActivate: () -> Unit,
    onRename: (String) -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(kb.displayName) }

    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        // ：阴影统一收进 2/4 令牌（6→4 为任务单批准的唯一超限修正）
        // ：点卡片主体 = 激活（非当前库时），与供应商行交互一致
        modifier = Modifier
            .fillMaxWidth()
            .shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)
            .clickable(enabled = !isActive, onClick = onActivate)
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            // 第一行：名称 + 重命名笔 + 当前使用徽章 + 删除（F2 合行）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        renameText = kb.displayName
                        showRename = true
                    }
                ) {
                    Text(
                        kb.displayName,
                        style = AppTypography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "重命名",
                        tint = TextHint,
                        modifier = Modifier.size(KbDimens.EDIT_ICON_SIZE_DP.dp)
                    )
                }
                if (isActive) {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Box(
                        modifier = Modifier
                            .background(PrimaryLight, LoveBrainShape.sm)
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Text("当前使用", style = AppTypography.labelSmall, color = PrimaryDark, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除知识库",
                    tint = TextHint,
                    modifier = Modifier
                        .size(AppDimens.ACTION_ICON_SIZE_DP.dp)
                        .clip(LoveBrainShape.sm)
                        .clickable(onClick = onDelete)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            // 第二行：阶段/对话信息 + 编辑/导出（F2 合行；按钮样式复用供应商行同款 RowActionButton）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "阶段：${kb.stage} ｜ 已对话 ${kb.turnCount} 轮",
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                RowActionButton("编辑") { onEdit() }
                Spacer(modifier = Modifier.width(Spacing.sm))
                RowActionButton("导出") { onExport() }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("修改显示名", style = AppTypography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("显示名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRename = false
                        onRename(renameText.trim())
                    },
                    // 空名禁止保存，避免卡片标题变空白
                    enabled = renameText.isNotBlank()
                ) { Text("保存", color = Primary, style = AppTypography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                }
            }
        )
    }
}

@Composable
private fun OnboardingScreen(
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onComplete: (Map<Int, String>, String, String) -> Unit,
    // P0-① 修复：生成中取消时调用（cancel 协程 + 关闭页面）
    onCancelGenerating: () -> Unit
) {
    val answers = remember { mutableStateMapOf<Int, String>() }
    var myName by remember { mutableStateOf("") }
    var herName by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }

    ScreenPage(
        title = "新建知识库",
        onBack = onDismiss,
        trailing = {
            TextButton(onClick = onSkip) {
                Text("建空档案", color = TextSecondary, style = AppTypography.labelLarge)
            }
        }
    ) {
        Text(
            "回答几道简单选择题，军师帮你建好初始画像。依恋类型不用你答，AI 会推断。也可点右上「建空档案」。",
            style = AppTypography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        // 答题进度反馈：n/6 计数 + 细进度条（填几张、还剩几张一眼可见）
        Text(
            "已答 ${answers.size}/${ONBOARDING_QUESTIONS.size} 题",
            style = AppTypography.labelSmall,
            color = TextHint
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(KbDimens.PROGRESS_BAR_HEIGHT_DP.dp)
                .clip(LoveBrainShape.full)
                .background(SurfaceInset)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(
                        if (ONBOARDING_QUESTIONS.isEmpty()) 0f
                        else answers.size.toFloat() / ONBOARDING_QUESTIONS.size
                    )
                    .height(KbDimens.PROGRESS_BAR_HEIGHT_DP.dp)
                    .clip(LoveBrainShape.full)
                    .background(Primary)
            )
        }
        Spacer(modifier = Modifier.height(Spacing.lg))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
        ) {
            ONBOARDING_QUESTIONS.forEachIndexed { i, q ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(q.title, style = AppTypography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    q.options.chunked(2).forEach { pair ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            pair.forEach { opt ->
                                // 补按压缩放反馈；生成中整体禁点（防答案与已提交请求不一致）
                                OnboardingOption(
                                    text = opt,
                                    selected = answers[i] == opt,
                                    enabled = !generating,
                                    onClick = { answers[i] = opt },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))
            Text("选填（不填也能建，之后能改）", style = AppTypography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(Spacing.xs))
            CompactInput(
                value = myName,
                onValueChange = { myName = it },
                placeholder = "你的称呼"
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            CompactInput(
                value = herName,
                onValueChange = { herName = it },
                placeholder = "她的称呼"
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        Button(
            onClick = {
                if (generating) {
                    // P0-① 修复：生成中取消——cancel 协程（generateRaw 抛 CancellationException 跳出）
                    onCancelGenerating()
                } else {
                    generating = true
                    onComplete(answers.toMap(), myName.trim(), herName.trim())
                }
            },
            enabled = true,
            colors = ButtonDefaults.buttonColors(containerColor = if (generating) TextHint else Primary),
            shape = LoveBrainShape.md,
            modifier = Modifier.fillMaxWidth().height(KbDimens.PRIMARY_ACTION_HEIGHT_DP.dp)
        ) {
            if (generating) {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .height(KbDimens.ONBOARDING_SPINNER_SIZE_DP.dp)
                        .width(KbDimens.ONBOARDING_SPINNER_SIZE_DP.dp),
                    strokeWidth = Spacing.xs
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Text("点击取消（军师还在生成画像…）", style = AppTypography.titleMedium)
            } else {
                Text("完成，AI 生成画像", style = AppTypography.titleMedium)
            }
        }
    }
}

/** 问卷选项：选中态 PrimaryLight+描边，按压缩放 0.97，可禁用 */
@Composable
private fun OnboardingOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.97f else 1f,
        label = "obOptionScale"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            // 统一 chip 语言（主人选型）：选中 = Primary 蓝底白字；未选 = SurfaceInset 灰底 + 细描边——
            // 与全 App 的分段器/文件 chip 同族，废除旧的"浅底+描边"双写样式
            .background(
                if (selected) Primary else SurfaceInset,
                LoveBrainShape.md
            )
            .border(
                if (selected) 0.dp else AppDimens.BORDER_WIDTH_DP.dp,
                if (selected) Color.Transparent else Border,
                LoveBrainShape.md
            )
            .semantics { this.selected = selected }
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.md)
    ) {
        Text(
            text,
            style = AppTypography.bodyMedium,
            color = when {
                !enabled -> TextHint
                selected -> Color.White
                else -> TextPrimary
            },
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}
