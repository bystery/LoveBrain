package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeSchemaVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 知识库文件访问的**最小内部接口**。
 *
 * 抽出来的类（迁移、备份等）不再各自拿一把锁、各自拼路径，
 * 而是共用仓库这一份实现：单写者互斥仍然只有 `KnowledgeRepository.fileMutex` 一把，
 * 抽类不会把"同一时刻只有一个写者"这个不变量拆散。
 */
internal interface KbStorageAccess {
    /** knowledge/ 根目录 */
    val root: File

    /** 原子写入（临时文件 → rename），失败不清空原文件 */
    fun atomicWrite(target: File, content: String)

    /** 读取 schema 模板 */
    fun schema(name: String): String

    /** kb.json 里当前的阶段标签 */
    suspend fun currentStage(kbName: String): String

    /** 写 kb.json 的阶段标签（无锁核心） */
    suspend fun setStage(kbName: String, stage: String)

    /** 就地更新 warmth.md 的阶段标签行（无锁核心） */
    suspend fun setWarmthStageLabel(kbName: String, stage: String)

    /** 统一时间戳格式 */
    fun timestamp(): String
}

/**
 * schema 版本探测 + 旧库结构迁移（从 `KnowledgeRepository` 拆出）。
 *
 * 职责边界：**只**管"这个库现在是 v 几、要不要动、动完写几"，
 * 文件读写与互斥一律经 [KbStorageAccess] 回仓库做。
 *
 * 三类判定必须保持原语义：
 * - 库版本高于本 App 支持范围 → 记为只读，不是"无需迁移"；
 * - 版本够新但没有 `.schema_version` → 归一化写一次并清 legacy marker，否则每次启动都在猜；
 * - 全新库（无 global 目录） → 也要把 CURRENT 落下去，旧实现在这里直接 return，
 *   于是新库永远没有版本文件。
 */
internal class KnowledgeMigrator(private val io: KbStorageAccess) {

    /** schema 高于本 App 支持范围的库——只读，拒绝写入 */
    private val schemaTooNewKbs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** 该库是否因 schema 过新而进入只读保护 */
    fun isReadOnly(kbName: String): Boolean = schemaTooNewKbs.contains(kbName)

    /**
     * 使用单一 .schema_version 文件检测当前 schema 版本。
     * 如果 .schema_version 存在，直接读取其版本号。
     * 如果不存在，回退到 legacy marker 文件检测（向后兼容）。
     *
     * 库当前的 schema 版本（对外只读，供升级断言与诊断使用）。
     * 读不到 .schema_version 时按 legacy marker 推断，与迁移判定同一把尺。
     */
    suspend fun detectVersion(kbName: String): Int = withContext(Dispatchers.IO) {
        detectSchemaVersion(kbName)
    }

    private fun detectSchemaVersion(kbName: String): Int {
        val dir = File(io.root, kbName)
        // 优先从统一 .schema_version 文件读取
        val versionFile = File(dir, ".schema_version")
        if (versionFile.exists()) {
            val content = runCatching { versionFile.readText().trim() }.getOrDefault("")
            val version = content.toIntOrNull()
            if (version != null && version > 0) return version
        }
        // 回退：检查 legacy marker 文件
        var maxVersion = 1
        for ((markerName, version) in KnowledgeSchemaVersion.legacyMarkers) {
            if (File(dir, markerName).exists()) {
                if (version > maxVersion) maxVersion = version
            }
        }
        return maxVersion
    }

    /**
     * 写入统一 schema 版本文件，同时清理 legacy marker。
     */
    private fun writeSchemaVersion(kbName: String, version: Int) {
        val dir = File(io.root, kbName)
        io.atomicWrite(File(dir, ".schema_version"), version.toString())
        // 清理 legacy marker 文件
        for ((markerName, _) in KnowledgeSchemaVersion.legacyMarkers) {
            val marker = File(dir, markerName)
            if (marker.exists()) marker.delete()
        }
    }

    /** 无锁迁移核心——调用方（仓库）必须已持有文件互斥锁 */
    suspend fun migrateUnlocked(kbName: String) = migrateIfNeededUnlocked(kbName)

    /** R11: 迁移的无锁核心——调用方必须已持有文件互斥锁
     *
     * A项修复：旧库重复迁移覆盖新内容。
     * 旧实现只判断 global 目录是否存在，每次迁移都用 overwrite=true 覆盖新路径，
     * 导致用户修改画像/积累新聊天后再次生成时旧数据覆盖新内容。
     *
     * 修复：持久迁移完成标记 (.migrated_v2) 必须参与判断。
     * - 标记存在 → 迁移已完成，只做 v3 文件补齐和阶段迁移，不覆盖任何目标文件
     * - 标记不存在 + global 存在 → 执行迁移，完成后写标记
     * - 标记不存在 + global 不存在 → 全新库，只做 v3 文件补齐
     * - 中断恢复：区分已迁移目标、空占位目标和用户新编辑目标
     *   - 目标已有非空内容 → 跳过（用户已编辑）
     *   - 目标为空或不存在 → 从旧源复制
     * - topic.md 和 topic_log.md 只在迁移完成时初始化，不重复清空
     */
    private suspend fun migrateIfNeededUnlocked(kbName: String) {
        val dir = File(io.root, kbName)
        val oldGlobal = File(dir, "global")
        // 使用 KnowledgeSchemaVersion 确定当前版本
        val currentVersion = detectSchemaVersion(kbName)

        // 库比本 App 还新（降级安装 / 新版设备带回的数据）——
        // 记为"只读"，让上层拒绝写入，而不是当成"不需要迁移"继续用旧代码读写未来结构。
        if (KnowledgeSchemaVersion.isBeyondSupported(currentVersion)) {
            schemaTooNewKbs.add(kbName)
            com.lovebrain.app.util.L.w(
                "schema v$currentVersion is newer than supported v${KnowledgeSchemaVersion.CURRENT}; $kbName is read-only"
            )
            return
        }
        // 版本已经够新，但如果 .schema_version 还没落盘（只有 legacy marker），
        // 就必须归一化一次：否则每次启动都要重新靠 marker 猜版本，marker 也永远清不掉。
        if (!KnowledgeSchemaVersion.needsMigration(currentVersion)) {
            val versionFile = File(dir, ".schema_version")
            if (!versionFile.exists() || runCatching { versionFile.readText().trim().toIntOrNull() }.getOrNull() == null) {
                writeSchemaVersion(kbName, currentVersion)
            }
            return
        }

        // 确保 v3 文件存在（v2→v3 过渡）——只补缺失，不覆盖已有
        if (dir.exists()) {
            File(dir, "moment").mkdirs()
            File(dir, "memory").mkdirs()
            val sceneFile = File(dir, "moment/scene.md")
            if (!sceneFile.exists()) io.atomicWrite(sceneFile, "")
            val rawChat = File(dir, "memory/raw_chat.md")
            if (!rawChat.exists()) io.atomicWrite(rawChat, "")
            val rawTopic = File(dir, "memory/raw_topic.md")
            if (!rawTopic.exists()) io.atomicWrite(rawTopic, "")
            val rawScene = File(dir, "memory/raw_scene.md")
            if (!rawScene.exists()) io.atomicWrite(rawScene, "")
            val planFile = File(dir, "moment/plan.md")
            if (!planFile.exists()) io.atomicWrite(planFile, io.schema("plan"))
            // 旧版 plan.md 的裸"格式/示例"说明行包进注释（编辑可见、预览隐藏、不进 prompt）
            if (planFile.exists()) {
                val planText = runCatching { planFile.readText() }.getOrDefault("")
                val fixed = KbTextOps.wrapPlanMetaLines(planText)
                if (fixed != planText) io.atomicWrite(planFile, fixed)
            }
            val counselingLog = File(dir, "memory/counseling_log.md")
            if (!counselingLog.exists()) io.atomicWrite(counselingLog, "")
            val reflectHistory = File(dir, "memory/reflect_history.md")
            if (!reflectHistory.exists()) io.atomicWrite(reflectHistory, "")
            // 兼容：旧知识库把"她"的画像存为 understand/you.md，统一改名为 her.md
            val oldYou = File(dir, "understand/you.md")
            val newHer = File(dir, "understand/her.md")
            // A项修复：you.md 存在但 her.md 已有非空内容时不覆盖
            if (oldYou.exists() && (!newHer.exists() || newHer.readText().isBlank())) {
                oldYou.renameTo(newHer)
            }

            // ═══  修复：旧阶段枚举（无"期"六选一）→ 新八阶段（带"期"）迁移 ═══
            val oldStage = io.currentStage(kbName)
            val mapped = STAGE_MIGRATION[oldStage]
            if (mapped != null) {
                io.setStage(kbName, mapped)
                io.setWarmthStageLabel(kbName, mapped)
            }
        }

        // 版本 >= 2 表示 v1→v2 迁移已完成
        // 但仍需检查是否需要 v2→v3 plan 数据迁移
        if (currentVersion >= 2) {
            migratePlanDataIfNeededUnlocked(kbName)
            // 确保 schema_version 文件存在并清理 legacy marker
            writeSchemaVersion(kbName, KnowledgeSchemaVersion.CURRENT)
            return
        }

        // 全新库（无 global 目录、无任何迁移标记）——上面的 ensureKbFilesComplete
        // 已经把 v3 文件补齐了，这里必须把 CURRENT 版本号落下去。
        // 旧实现在这里直接 return，于是新库永远没有 .schema_version，
        // detectSchemaVersion 每次都回落到 1，每次启动都重跑一遍"迁移"。
        if (!oldGlobal.exists()) {
            writeSchemaVersion(kbName, KnowledgeSchemaVersion.CURRENT)
            return
        }

        // 旧库迁移：首次执行（global 存在但标记不存在）
        File(dir, "understand").mkdirs()
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()

        // A项修复：只在目标为空或不存在时复制旧数据，不覆盖用户已有编辑
        val me = File(dir, "global/me.md")
        val meTarget = File(dir, "understand/me.md")
        if (me.exists() && (!meTarget.exists() || meTarget.readText().isBlank())) {
            me.copyTo(meTarget, overwrite = true)
        }
        val her = File(dir, "global/her.md")
        val herTarget = File(dir, "understand/her.md")
        // her.md 特殊：you.md 可能已改名为 her.md，检查内容是否非空
        if (her.exists() && (!herTarget.exists() || herTarget.readText().isBlank())) {
            her.copyTo(herTarget, overwrite = true)
        }
        val status = File(dir, "global/status.md")
        val warmthTarget = File(dir, "understand/warmth.md")
        if (status.exists() && (!warmthTarget.exists() || warmthTarget.readText().isBlank())) {
            status.copyTo(warmthTarget, overwrite = true)
        }

        val chatlog = File(dir, "recent/chatlog.md")
        val recentTarget = File(dir, "moment/recent.md")
        if (chatlog.exists() && (!recentTarget.exists() || recentTarget.readText().isBlank())) {
            chatlog.copyTo(recentTarget, overwrite = true)
        }

        val lessons = File(dir, "general/lessons.md")
        val lessonsTarget = File(dir, "memory/lessons.md")
        if (lessons.exists() && (!lessonsTarget.exists() || lessonsTarget.readText().isBlank())) {
            lessons.copyTo(lessonsTarget, overwrite = true)
        }
        val moments = File(dir, "general/moments.md")
        val details = File(dir, "general/details.md")
        val archiveFile = File(dir, "memory/archive.md")
        val archiveContent = buildString {
            if (moments.exists()) append(moments.readText()).append("\n\n")
            if (details.exists()) append(details.readText())
        }
        if (archiveContent.isNotBlank() && (!archiveFile.exists() || archiveFile.readText().isBlank())) {
            io.atomicWrite(archiveFile, archiveContent)
        }

        // A项修复：topic.md 和 topic_log.md 只在迁移完成时初始化一次
        val topicFile = File(dir, "moment/topic.md")
        if (!topicFile.exists() || topicFile.readText().isBlank()) {
            val initTime = com.lovebrain.app.util.TimeFmt.now()
            io.atomicWrite(topicFile, KbTextOps.topicLine(initTime, KbTextOps.TOPIC_INITIAL_LABEL))
        }
        val topicLogFile = File(dir, "memory/topic_log.md")
        if (!topicLogFile.exists()) {
            io.atomicWrite(topicLogFile, "")
        }

        // v1→v2 迁移完成后，执行 v2→v3 plan 数据迁移
        migratePlanDataIfNeededUnlocked(kbName)

        // 写入统一 schema 版本文件，清理所有 legacy marker
        writeSchemaVersion(kbName, KnowledgeSchemaVersion.CURRENT)
    }

    /**
     * plan.md 数据迁移——处理旧版本累积的万字状态链。
     *
     * 旧版本 mergeOngoing 无条件追加状态链，相同文本重复返回也追加，
     * 导致 plan.md 可膨胀到成千上万字。新写入已修（幂等更新），
     * 但已累积的旧数据不会自行消失。
     *
     * 迁移流程（可恢复、幂等）：
     * 迁移标记现在通过 .schema_version >= 3 判断（不再使用 .migrated_plan_v3）。
     *
     * 中断恢复：版本未写入 .schema_version 前重跑无害（备份追加、归档保留）；
     * 版本写入后跳过，保证幂等。未知格式原样保留。
     */
    private fun migratePlanDataIfNeededUnlocked(kbName: String) {
        val dir = File(io.root, kbName)
        // 通过 schema version 判断是否已迁移
        val currentVersion = detectSchemaVersion(kbName)
        if (currentVersion >= 3) return  // plan v3 迁移已完成

        val planFile = File(dir, "moment/plan.md")
        val planContent = if (planFile.exists()) planFile.readText() else ""
        if (planContent.isBlank()) {
            // 空文件——无需迁移，直接标记为已迁移
            writeSchemaVersion(kbName, 3)
            return
        }

        // 1. 备份旧 plan.md 到归档
        val archiveFile = File(dir, "memory/plan_archive_v2.md")
        val backupContent = buildString {
            append("<!-- plan 结构迁移 v2 备份: ${io.timestamp()} -->\n")
            append("<!-- 原始 plan.md 内容（迁移前快照） -->\n")
            append(planContent)
            if (!planContent.endsWith("\n")) append("\n")
            if (archiveFile.exists()) {
                append("\n<!-- 以下为更早的归档 -->\n")
                append(archiveFile.readText())
            }
        }
        io.atomicWrite(archiveFile, backupContent)

        // 2. 解析旧格式事项
        val items = mutableListOf<MigratablePlanItem>()
        var section = "active"
        for (line in planContent.lines()) {
            val t = line.trim()
            when {
                t.startsWith("## 进行中") -> section = "active"
                t.startsWith("## 已结束") -> section = "ended"
                t.startsWith("#") || t.startsWith("<!--") -> { /* 跳过 */ }
                t.contains("|") -> {
                    val parts = t.split("|").map { it.trim() }
                    if (parts.size >= 3 && parts[0].isNotBlank()) {
                        val firstPart = parts[0]
                        val tildeIdx = firstPart.indexOf('~')
                        val (itemId, name) = if (tildeIdx > 0) {
                            firstPart.substring(0, tildeIdx) to firstPart.substring(tildeIdx + 1)
                        } else {
                            "" to firstPart
                        }
                        items.add(MigratablePlanItem(
                            itemId = itemId,
                            name = name,
                            status = parts[1],
                            chain = parts.drop(2).joinToString("|").trim(),
                            section = section
                        ))
                    }
                }
            }
        }

        if (items.isEmpty()) {
            // 无法解析——原样保留，标记为已迁移
            com.lovebrain.app.util.L.w("no parseable items in plan.md for '$kbName', keeping original")
            writeSchemaVersion(kbName, 3)
            return
        }

        // 3. 清理重复状态——合并连续相同，截断到最大长度
        var totalReduction = 0
        val cleanedItems = items.map { item ->
            val originalLen = item.chain.length
            val cleanedChain = KbTextOps.cleanStateChain(item.chain)
            totalReduction += originalLen - cleanedChain.length
            item.copy(chain = cleanedChain)
        }
        com.lovebrain.app.util.L.w("migrated plan for '$kbName', ${items.size} items, reduced $totalReduction chars")

        // 4. 渲染清理后的 plan.md
        val active = cleanedItems.filter { it.section == "active" }
        val ended = cleanedItems.filter { it.section == "ended" }
        val hadActiveHeader = planContent.contains("## 进行中")
        val hadEndedHeader = planContent.contains("## 已结束")

        val newPlan = buildString {
            append("# 事项计划\n\n")
            if (hadActiveHeader || active.isNotEmpty()) {
                append("## 进行中\n")
                active.forEach { item ->
                    val id = if (item.itemId.isNotBlank()) "${item.itemId}~" else ""
                    append(id).append(item.name)
                        .append(" | ").append(item.status)
                        .append(" | ").append(item.chain)
                        .append("\n")
                }
                append("\n")
            }
            if (hadEndedHeader || ended.isNotEmpty()) {
                append("## 已结束\n")
                ended.forEach { item ->
                    val id = if (item.itemId.isNotBlank()) "${item.itemId}~" else ""
                    append(id).append(item.name)
                        .append(" | ").append(item.status)
                        .append(" | ").append(item.chain)
                        .append("\n")
                }
            }
        }
        io.atomicWrite(planFile, newPlan)

        // 写入 schema version 3，标记 plan 迁移完成
        writeSchemaVersion(kbName, 3)
    }

    /** 可迁移的事项数据 */
    private data class MigratablePlanItem(
        val itemId: String,
        val name: String,
        val status: String,
        val chain: String,
        val section: String
    )

    /** 旧阶段枚举 → 新八阶段迁移映射（迁移专用，故与迁移器放一起） */
    private val STAGE_MIGRATION = mapOf(
        "初识" to "初识期",
        "破冰" to "破冰期",
        "暧昧" to "暧昧期",
        "热恋" to "热恋期",
        "磨合" to "磨合期",
        "稳定" to "稳定期",
        "危机" to "危机期",
        "修复" to "修复期"
    )

}
