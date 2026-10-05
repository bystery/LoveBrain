package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.CaseStatus
import com.lovebrain.app.model.DialogueSnapshotEntry
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 反馈案例仓库——本地持久化于 feedback/cases.json。
 *
 * 点踩立即落本地反馈；不因点踩就自动调用 AI。
 * 导出 Markdown + JSON，不记录凭证。
 * 幂等：同 caseId 不重复入库。
 *
 *
 * - mutex 互斥保护（旧版 mutableList 无互斥）
 * - 原子落盘（旧版 file.writeText 直接覆写）
 * - 统一导出口径（UI 不再直接读盘）
 * - 完整诊断快照（含真实消息、版本信息）
 */
class FeedbackCaseRepository(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val dir = File(context.filesDir, "feedback").also { if (!it.exists()) it.mkdirs() }
    private val file = File(dir, "cases.json").also { if (!it.exists()) it.createNewFile() }

    private val mutex = Mutex()
    private val _cases = mutableListOf<FeedbackCase>()

    private fun loadCasesSync() {
        runCatching {
            val text = file.readText()
            if (text.isNotBlank()) {
                _cases.clear()
                _cases.addAll(json.decodeFromString<List<FeedbackCase>>(text))
            }
        }.onFailure { L.w("FeedbackCaseRepository load failed: ${it::class.simpleName}") }
    }

    private fun saveCasesSync() {
        // 原子写入——先写 tmp 再 rename，避免写入中断导致数据损坏
        val tmpFile = File(dir, "cases.json.tmp")
        runCatching {
            tmpFile.writeText(json.encodeToString(_cases.toList()))
            if (!tmpFile.renameTo(file)) {
                // rename 失败回退到直接写入
                file.writeText(json.encodeToString(_cases.toList()))
            }
            tmpFile.delete()
        }.onFailure { err ->
            L.e("FeedbackCaseRepository save failed: ${err::class.simpleName}", err)
            tmpFile.delete()
        }
    }

    /** 保存一条反馈案例（幂等：同 caseId 更新而非重复入库） */
    suspend fun save(case: FeedbackCase) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_cases.isEmpty() && file.length() > 0) loadCasesSync()
            val existing = _cases.indexOfFirst { it.caseId == case.caseId }
            if (existing >= 0) {
                _cases[existing] = case
            } else {
                _cases.add(case)
            }
            saveCasesSync()
        }
    }

    /** 获取全部案例 */
    suspend fun getAll(): List<FeedbackCase> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_cases.isEmpty() && file.length() > 0) loadCasesSync()
            _cases.toList()
        }
    }

    /** 按筛选条件获取 */
    suspend fun filter(
        category: FeedbackCategory? = null,
        kbName: String? = null,
        modelId: String? = null,
        status: CaseStatus? = null
    ): List<FeedbackCase> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_cases.isEmpty() && file.length() > 0) loadCasesSync()
            _cases.filter { c ->
                (category == null || category in c.categories) &&
                (kbName == null || c.kbName == kbName) &&
                (modelId == null || c.modelId == modelId) &&
                (status == null || c.status == status)
            }
        }
    }

    /** 更新案例备注 */
    suspend fun updateNote(caseId: String, note: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val idx = _cases.indexOfFirst { it.caseId == caseId }
            if (idx >= 0) {
                _cases[idx] = _cases[idx].copy(userNote = note)
                saveCasesSync()
            }
        }
    }

    /** 更新案例状态 */
    suspend fun updateStatus(caseId: String, status: CaseStatus) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val idx = _cases.indexOfFirst { it.caseId == caseId }
            if (idx >= 0) {
                _cases[idx] = _cases[idx].copy(status = status)
                saveCasesSync()
            }
        }
    }

    /** 删除案例 */
    suspend fun delete(caseId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _cases.removeAll { it.caseId == caseId }
            saveCasesSync()
        }
    }

    /**
     * 统一导出为 Markdown 报告。
     * 包含完整真实消息快照、版本信息、记忆引用。
     * UI 不再自己实现 buildMarkdownReport。
     */
    suspend fun exportMarkdown(filteredCases: List<FeedbackCase>): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("# LoveBrain 反馈案例报告")
            appendLine()
            appendLine("生成时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine()

            appendLine("## 统计")
            appendLine("- 总案例数：${filteredCases.size}")
            val understandingCount = filteredCases.count { FeedbackCategory.UNDERSTANDING_ERROR in it.categories }
            val expressionCount = filteredCases.count { FeedbackCategory.EXPRESSION_DISLIKE in it.categories }
            val otherCount = filteredCases.count { FeedbackCategory.OTHER in it.categories }
            appendLine("- 理解错误：$understandingCount")
            appendLine("- 表达不喜欢：$expressionCount")
            appendLine("- 其他：$otherCount")
            appendLine()

            appendLine("## 案例列表")
            appendLine()
            filteredCases.forEachIndexed { idx, c ->
                appendLine("### 案例 ${idx + 1}")
                appendLine("- **时间**：${c.timestamp}")
                appendLine("- **分类**：${c.categories.joinToString(", ") { categoryName(it) }}")
                appendLine("- **原因**：${c.reasons.joinToString(", ")}")
                if (c.kbName.isNotBlank()) appendLine("- **档案**：${c.kbName}")
                if (c.modelId.isNotBlank()) appendLine("- **模型**：${c.modelId}")
                if (c.contextMode.isNotBlank()) appendLine("- **上下文模式**：${c.contextMode}")
                if (c.promptVersion.isNotBlank()) appendLine("- **提示词版本**：${c.promptVersion}")
                if (c.appVersion.isNotBlank()) appendLine("- **应用版本**：${c.appVersion}")
                if (c.buildType.isNotBlank()) appendLine("- **构建类型**：${c.buildType}")
                appendLine("- **候选原文**：${c.candidateReply}")
                if (c.userNote.isNotBlank()) appendLine("- **用户补充**：${c.userNote}")
                if (c.betterVersion.isNotBlank()) appendLine("- **期望版本**：${c.betterVersion}")
                if (c.ideaHint.isNotBlank()) appendLine("- **本轮想法**：${c.ideaHint}")
                if (c.intentText.isNotBlank()) appendLine("- **意图**：${c.intentText}")

                // 完整真实消息快照
                if (c.dialogueSnapshot.isNotEmpty()) {
                    appendLine("- **真实对话**：")
                    c.dialogueSnapshot.forEach { msg ->
                        appendLine("  - [${msg.speaker}] ${msg.text}")
                    }
                }

                // 记忆引用
                if (c.memoryRefs.isNotEmpty()) {
                    appendLine("- **记忆引用**：${c.memoryRefs.joinToString(", ")}")
                }

                // 用量
                if (c.promptTokens > 0 || c.completionTokens > 0) {
                    appendLine("- **Token 用量**：prompt=${c.promptTokens}, completion=${c.completionTokens}")
                }
                if (c.costYuan > 0) {
                    appendLine("- **费用（元）**：${c.costYuan}")
                }

                appendLine("- **状态**：${statusName(c.status)}")
                appendLine()
            }
        }
    }

    /** 导出为结构化 JSON（便于 AI 分析） */
    suspend fun exportJson(filteredCases: List<FeedbackCase>): String = withContext(Dispatchers.IO) {
        json.encodeToString(filteredCases)
    }

    companion object {
        /** 分类中文名 */
        fun categoryName(cat: FeedbackCategory): String = when (cat) {
            FeedbackCategory.UNDERSTANDING_ERROR -> "理解错误"
            FeedbackCategory.EXPRESSION_DISLIKE -> "表达不喜欢"
            FeedbackCategory.OTHER -> "其他"
        }

        /** 状态中文名 */
        fun statusName(status: CaseStatus): String = when (status) {
            CaseStatus.PENDING -> "待分析"
            CaseStatus.CONCLUDED -> "已有结论"
            CaseStatus.TO_VERIFY -> "待验证"
            CaseStatus.VERIFIED -> "已验证"
        }
    }
}
