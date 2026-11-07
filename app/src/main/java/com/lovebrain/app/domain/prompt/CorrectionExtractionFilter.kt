package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MemoryKind

/**
 * 提取链统一纠正过滤（ F3.3：纠错后旧值在摘要中回流）。
 *
 * 背景：纠正（corrections 表）此前只在**注入侧**生效（[MemoryRefPolicy] +
 * `PromptKnowledgeSection` 注入前过滤）；后台提取链——经验提取 / 画像 reflect /
 * 向量重估——读源材料时完全不消费纠正表，被用户纠正过的旧内容仍按原文回流进
 * 经验与画像，静默覆盖用户纠正。
 *
 * 这一格的职责只有一件事：提取链在读取源材料时，按 [MemoryRefPolicy] **既有的**
 * 纠正语义（WRONG / MUTED / FINISHED / WRONG_PERSON，含 MUTED 过期恢复与旧 ID 回退
 * 匹配）决定该段内容是原样放行、标注纠正值、还是整段排除。不发明新状态、不复述
 * 判据——跳不跳过这一颗判定只问 [MemoryRefPolicy.isCorrected]。
 *
 * 语义约束：
 * - 未被纠正 → 原文逐字放行（空串也原样返回）。
 * - WRONG 且带 replacementText → 返回 `（已纠正：…）` 标注，让纠正值传导进提取结果
 *   （与注入侧同款文案）；原始内容不再出现。
 * - WRONG 无 replacement / FINISHED / WRONG_PERSON / MUTED 未过期 → 返回 null，
 *   该内容不进提取材料（提取侧没有"可被动回应"的语义，MUTED 的提示文案在这里是噪声，
 *   故整段排除而不落标注）。
 * - MUTED 已过期 → 与注入侧同判（[MemoryRefPolicy.isMuteExpired]），原文恢复放行。
 *
 * 纯函数：不读库、不读时钟（时钟在 [MemoryRefPolicy.isMuteExpired] 内取系统时间，
 * 与注入侧同一来源）、不持有状态。
 */
object CorrectionExtractionFilter {

    /**
     * 提取链读源材料时的统一过滤口。
     *
     * @param kind 记忆类型（与注入侧 MemoryRef 同源：PROFILE / SCENE / LESSON / ONGOING）
     * @param sourcePath 源文件相对路径（与注入侧 makeRef 的 sourcePath 逐字同源）
     * @param content 已读出的源材料文本
     * @param corrections 纠正表（`memory/corrections.json` 读出的键值）
     * @param entryId 条目级 ID（仅 ONGOING 事项用，与注入侧 makeOngoingEntryRef 同源）
     * @return null = 该内容不进提取材料；非空 = 原文（未被纠正）或纠正标注（WRONG 带补正）
     */
    fun filterForExtraction(
        kbName: String,
        kind: MemoryKind,
        sourcePath: String,
        content: String,
        corrections: Map<String, MemoryCorrection>,
        entryId: String = ""
    ): String? {
        if (content.isBlank()) return content
        val ref = MemoryRefPolicy.makeRef(kbName, kind, sourcePath, content, entryId)
        val sb = StringBuilder()
        if (!MemoryRefPolicy.isCorrected(ref.id, corrections, sb)) return content
        // 命中纠正：只有「WRONG 且带补正」把纠正值传下去，其余整段排除
        val correction = findCorrection(ref.id, corrections)
        val replacement = correction?.replacementText?.trim().orEmpty()
        return if (correction?.action == CorrectionAction.WRONG && replacement.isNotEmpty()) {
            "（已纠正：$replacement）\n"
        } else {
            null
        }
    }

    /**
     * 与 [MemoryRefPolicy.isCorrected] 内部同款的 ID 回退查找（精确 → 去掉 `:hash` 后缀的
     * 旧格式文件级 ID）。isCorrected 负责判"是否跳过"，这里只负责取补正文案；两处使用
     * 同一条回退规则，避免"判定命中了、补正却取不到"的分叉。
     */
    private fun findCorrection(
        memoryId: String,
        corrections: Map<String, MemoryCorrection>
    ): MemoryCorrection? {
        corrections[memoryId]?.let { return it }
        val lastColon = memoryId.lastIndexOf(':')
        if (lastColon > 0) return corrections[memoryId.substring(0, lastColon)]
        return null
    }

    // ════════════════════════════════════════════════════════════════
    //  F3.3 扩展（R4 覆盖面）：recent / raw_chat / raw_scene / raw_topic 的回流剔除
    // ════════════════════════════════════════════════════════════════

    /**
     * 归档 / 轮次材料的过滤结果：净化后的文本 + 被剔除的条数。
     * 计数只进日志（路径 + 条数），被剔除的正文按隐私红线不入日志。
     */
    data class FilteredArchive(val text: String, val removed: Int)

    /**
     * 「已被纠正的条目原文」对撞基准（ F3.3 扩展）。
     *
     * scene.md 的文件级纠正（`SCENE:moment/scene.md`，[MemoryRefPolicy.isCorrected] 同判，
     * 含 MUTED 过期恢复）**当前生效**时，scene.md 此刻条目化的各条事实原文就是
     * "已被纠正的条目原文"——它们经归档（raw_scene / raw_topic 状态段）与对话行
     * （recent / raw_chat / raw_topic 对话段）原样回流进提取材料。返回这批原文的
     * 精确集合，供 [filterSceneArchive] / [filterChatArchive] 做条目级 / 行级剔除。
     *
     * 纠正未生效（未纠正 / MUTED 已过期）→ 空集，下游一字不动。
     * 归档条目自身还没有纠正身份（纠正表不持 recent / raw_* 的 MemoryRef），
     * 纠正时已过期归档、scene.md 里已不存在的旧事实暂不在此列——条目级化之前宁可漏剔。
     */
    fun correctedSceneFactTexts(
        kbName: String,
        sceneContent: String,
        corrections: Map<String, MemoryCorrection>
    ): Set<String> {
        if (sceneContent.isBlank()) return emptySet()
        val ref = MemoryRefPolicy.makeRef(kbName, MemoryKind.SCENE, SCENE_SOURCE_PATH, sceneContent)
        if (!MemoryRefPolicy.isCorrected(ref.id, corrections, StringBuilder())) return emptySet()
        return sceneEntryFactTexts(sceneContent)
    }

    /**
     * 场景条目行 → 事实原文集合。与 SceneChainStore 的落盘格式同源：
     * 条目行 `- [ts] 标签：事实|src=…|spk=…|subj=…`（新格式）、`事实⟨ids⟩`（旧格式）、纯文本。
     * 只认 `- [yyyy-MM-dd HH:mm]` 开头的条目行；轮次块头、对话行、空行不产生事实。
     */
    fun sceneEntryFactTexts(content: String): Set<String> {
        if (content.isBlank()) return emptySet()
        val out = mutableSetOf<String>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            val match = entryLineRegex.find(trimmed) ?: continue
            val rest = match.groupValues[2]
            val colonIdx = rest.indexOf('：')
            val factsRaw = if (colonIdx >= 0) rest.substring(colonIdx + 1) else rest
            for (seg in factsRaw.split('；', ';')) {
                val segT = seg.trim()
                if (segT.isBlank()) continue
                factTextOf(segT).takeIf { it.isNotBlank() }?.let(out::add)
            }
        }
        return out
    }

    /**
     * 场景条目归档（`memory/raw_scene.md` 与 raw_topic.md 的「状态变化」段）条目级过滤。
     *
     * 只剔除**事实原文与已纠正事实精确相同**的那一条事实（剥掉 src/spk/subj、⟨ids⟩
     * 装饰后全文相等）；同一行的相邻事实、时间戳、标签与幸存事实的装饰逐字保留；
     * 一条事实都不剩的条目行整行省略；没有任何事实被剔除的行（含轮次块头、对话行、空行）
     * 逐字节不变。不做子串 / 模糊替换——相邻正确内容不可能被波及。
     */
    fun filterSceneArchive(content: String, correctedFactTexts: Set<String>): FilteredArchive {
        if (content.isBlank() || correctedFactTexts.isEmpty()) return FilteredArchive(content, 0)
        var removed = 0
        val text = mapLines(content) { line ->
            val trimmed = line.trim()
            val match = entryLineRegex.find(trimmed) ?: return@mapLines line
            val ts = match.groupValues[1]
            val rest = match.groupValues[2]
            val colonIdx = rest.indexOf('：')
            val factsRaw = if (colonIdx >= 0) rest.substring(colonIdx + 1) else rest
            val kept = mutableListOf<String>()
            var dropped = 0
            for (seg in factsRaw.split('；', ';')) {
                val segT = seg.trim()
                if (segT.isBlank()) continue
                if (factTextOf(segT) in correctedFactTexts) {
                    dropped++
                    continue
                }
                kept.add(segT)
            }
            if (dropped == 0) return@mapLines line   // 无剔除：逐字放行（含原始空格与装饰）
            removed += dropped
            when {
                kept.isEmpty() -> null               // 一条事实都不剩 → 整行省略
                colonIdx >= 0 -> "- [$ts] ${rest.substring(0, colonIdx)}：${kept.joinToString("；")}"
                else -> "- [$ts] ${kept.joinToString("；")}"
            }
        }
        return FilteredArchive(text, removed)
    }

    /**
     * 对话归档（`moment/recent.md` 轮次块、`memory/raw_chat.md`、raw_topic.md 对话记录段）
     * 行级过滤。
     *
     * 只剔除**整行**为「她：/我： + 已纠正事实原文」的对话行（trim 后全文相等，精确匹配）；
     * 「我的想法：」「我（最终回复：…）：」与非完全匹配的行逐字节保留。对话行通常只是
     * 事实的转述而非原文——匹配不上就漏剔，宁漏勿误。
     */
    fun filterChatArchive(content: String, correctedFactTexts: Set<String>): FilteredArchive {
        if (content.isBlank() || correctedFactTexts.isEmpty()) return FilteredArchive(content, 0)
        var removed = 0
        val text = mapLines(content) { line ->
            val match = chatLineRegex.find(line.trim())
            if (match != null && match.groupValues[2].trim() in correctedFactTexts) {
                removed++
                null
            } else {
                line
            }
        }
        return FilteredArchive(text, removed)
    }

    // ── 内部：格式解析（纯函数，不读库、不落盘、不持状态） ──

    private const val SCENE_SOURCE_PATH = "moment/scene.md"

    /** 场景条目行：`- [yyyy-MM-dd HH:mm] 其余部分`（与 SceneChainStore / RecentRoundStore 同一格式族） */
    private val entryLineRegex = Regex("^- \\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]\\s*(.*)$")

    /** 对话行：`她：…` / `我：…`（ChatMessage.Role 的 label 前缀；最终回复 / 想法行不是这个形状） */
    private val chatLineRegex = Regex("^(她|我)：(.*)$")

    /** 单条事实 → 原文：剥掉 `|src=…|spk=…|subj=…` 装饰（新格式）与 `⟨ids⟩` 后缀（旧格式） */
    private fun factTextOf(factText: String): String = when {
        factText.contains("|src=") || factText.contains("|spk=") || factText.contains("|subj=") ->
            factText.substringBefore('|').trim()
        factText.endsWith("⟩") ->
            Regex("(.*)⟨(.+)⟩$").find(factText)?.groupValues?.get(1)?.trim() ?: factText
        else -> factText
    }

    /** 逐行映射：transform 返回 null = 该行剔除；未剔除的行按返回值落位，行尾换行原样保留 */
    private inline fun mapLines(content: String, transform: (String) -> String?): String {
        val endsWithNewline = content.endsWith("\n")
        val body = if (endsWithNewline) content.removeSuffix("\n") else content
        val out = StringBuilder()
        for (line in body.split('\n')) {
            val mapped = transform(line) ?: continue
            if (out.isNotEmpty()) out.append('\n')
            out.append(mapped)
        }
        if (endsWithNewline && out.isNotEmpty()) out.append('\n')
        return out.toString()
    }
}
