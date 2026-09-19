package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.CaseStatus
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * F02: 反馈案例仓库——本地持久化于 feedback/cases.json。
 *
 * 点踩立即落本地反馈；不因点踩就自动调用 AI。
 * 导出 Markdown + JSON，不记录敏感凭证。
 * 幂等：同 caseId 不重复入库。
 */
class FeedbackCaseRepository(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val dir = File(context.filesDir, "feedback").also { if (!it.exists()) it.mkdirs() }
    private val file = File(dir, "cases.json").also { if (!it.exists()) it.createNewFile() }

    private val _cases = mutableListOf<FeedbackCase>()
    private val cases: MutableList<FeedbackCase>
        get() {
            if (_cases.isEmpty() && file.length() > 0) loadCasesSync()
            return _cases
        }

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
        runCatching {
            file.writeText(json.encodeToString(_cases.toList()))
        }.onFailure { L.w("FeedbackCaseRepository save failed: ${it::class.simpleName}") }
    }

    /** 保存一条反馈案例（幂等：同 caseId 更新而非重复入库） */
    suspend fun save(case: FeedbackCase) = withContext(Dispatchers.IO) {
        val existing = cases.indexOfFirst { it.caseId == case.caseId }
        if (existing >= 0) {
            cases[existing] = case
        } else {
            cases.add(case)
        }
        saveCasesSync()
    }

    /** 获取全部案例 */
    suspend fun getAll(): List<FeedbackCase> = withContext(Dispatchers.IO) {
        cases.toList()
    }

    /** 按筛选条件获取 */
    suspend fun filter(
        category: FeedbackCategory? = null,
        kbName: String? = null,
        modelId: String? = null,
        status: CaseStatus? = null
    ): List<FeedbackCase> = withContext(Dispatchers.IO) {
        cases.filter { c ->
            (category == null || category in c.categories) &&
            (kbName == null || c.kbName == kbName) &&
            (modelId == null || c.modelId == modelId) &&
            (status == null || c.status == status)
        }
    }

    /** 更新案例备注 */
    suspend fun updateNote(caseId: String, note: String) = withContext(Dispatchers.IO) {
        val idx = cases.indexOfFirst { it.caseId == caseId }
        if (idx >= 0) {
            cases[idx] = cases[idx].copy(userNote = note)
            saveCasesSync()
        }
    }

    /** 更新案例状态 */
    suspend fun updateStatus(caseId: String, status: CaseStatus) = withContext(Dispatchers.IO) {
        val idx = cases.indexOfFirst { it.caseId == caseId }
        if (idx >= 0) {
            cases[idx] = cases[idx].copy(status = status)
            saveCasesSync()
        }
    }

    /** 删除案例 */
    suspend fun delete(caseId: String) = withContext(Dispatchers.IO) {
        cases.removeAll { it.caseId == caseId }
        saveCasesSync()
    }

    /** 导出为 Markdown 报告（可供人阅读） */
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
                appendLine("- **分类**：${c.categories.joinToString(", ")}")
                appendLine("- **原因**：${c.reasons.joinToString(", ")}")
                if (c.kbName.isNotBlank()) appendLine("- **档案**：${c.kbName}")
                if (c.modelId.isNotBlank()) appendLine("- **模型**：${c.modelId}")
                appendLine("- **候选原文**：${c.candidateReply}")
                if (c.userNote.isNotBlank()) appendLine("- **用户补充**：${c.userNote}")
                if (c.betterVersion.isNotBlank()) appendLine("- **期望版本**：${c.betterVersion}")
                if (c.ideaHint.isNotBlank()) appendLine("- **本轮想法**：${c.ideaHint}")
                if (c.intentText.isNotBlank()) appendLine("- **意图**：${c.intentText}")
                appendLine("- **状态**：${c.status}")
                appendLine()
            }
        }
    }

    /** 导出为结构化 JSON（便于 AI 分析） */
    suspend fun exportJson(filteredCases: List<FeedbackCase>): String = withContext(Dispatchers.IO) {
        json.encodeToString(filteredCases)
    }
}
