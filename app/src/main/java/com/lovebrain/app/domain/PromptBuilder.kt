package com.lovebrain.app.domain

import com.lovebrain.app.domain.port.KnowledgeReadPort
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.domain.port.SystemClock
import com.lovebrain.app.domain.prompt.ChatTranscriptBlock
import com.lovebrain.app.domain.prompt.CorrectionExtractionFilter
import com.lovebrain.app.domain.prompt.CurrentSceneInjection
import com.lovebrain.app.domain.prompt.IntentIdeaBlock
import com.lovebrain.app.domain.prompt.MemoryRefPolicy
import com.lovebrain.app.domain.prompt.PromptCoreKnowledgeSection
import com.lovebrain.app.domain.prompt.PromptKnowledgeSection
import com.lovebrain.app.domain.prompt.PromptProactiveSection
import com.lovebrain.app.domain.prompt.PromptReflectSection
import com.lovebrain.app.domain.prompt.PromptVectorSection
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.ProfileUpdateSchema

/**
 * Prompt 组装器 v4（ 缓存锚点前置重排）。
 *
 * 新结构（三流各自专用 system；动态内容一律 user 侧，保证 system 前缀稳定命中上下文缓存）：
 *   回复  = System（全静态） + User（知识段[含历史块] + 当前场景 + 持续意图 + 对话记录
 *                          + 用户备注 + 时间戳垫底）
 *   谈心  = System（counseling.md 全文） + User（核心知识子集 + 倾诉与任务 + 时间戳垫底）
 *   润色  = System（polish.md 全文） + User（仅草稿；无时间戳、无知识、无场景）
 *
 * System（回复） = core + naturalness_check + redline + format
 * （进攻模式已删除：aggressive.md 不再读取、不再进入 user 知识段）
 *
 * 本类是**薄编排器**：知识段 / 主动开场段 / 反思维段 / 向量段 / 核心子集段
 * 的纯装配逻辑已分别搬进 `domain.prompt` 下的同名 object（`PromptKnowledgeSection` 等），
 * 它们只接收已读出的文本与纠正表，返回 String。本类只负责读资产 / 读库 /
 * 拼装最终 prompt，不再内联大段格式化逻辑。
 */
class PromptBuilder(
    /**
     * 引擎提示词资产的读取口（assets 实现在 data/AssetPromptSource，接线见 di/AppModule.kt）。
     * 以前这一格是 `android.content.Context`——domain 由此摸到 Android，是登记过的架构债；
     * 现在按 SettingsStorePort/KbArchivePort 同一张处方换成端口，缺资产的 fail-open 语义在实现侧不变。
     */
    private val promptSource: PromptSourcePort,
    private val knowledgeRepo: KnowledgeReadPort,
    /**
     * prompt 里的「当前时间」段和 `buildTimestampPrompt()` 都从这个端口取。
     *
 * 之前它们直接读系统时间，后果有两个：一是同一份冻结输入在 09:59 与 10:01 会
 * 拼出不同 prompt，于是"资产指纹描述的就是这棵树"永远多一个未知量；
     * 二是任何测试都没法断言"模型被告知的时间是什么"——只能跑一次看它像不像今天。
     */
    private val clock: Clock = SystemClock
) {

    // ═══════════ 配置校验 → `domain/PromptConfigValidator` ═══════════

    /** 校验 thinkingMode 范围，无效值回退默认并给出警告。
     *  纯函数逻辑已搬进 [PromptConfigValidator]——这里只留薄包装，保持旧调用点签名不变。 */
    fun validateConfig(thinkingMode: Int): ConfigValidationResult =
        PromptConfigValidator.validate(thinkingMode).let {
            ConfigValidationResult(it.thinkingMode, it.warnings)
        }

    data class ConfigValidationResult(
        val thinkingMode: Int,
        val warnings: List<String>
    ) { val isValid: Boolean get() = warnings.isEmpty() }

    // ═══════════ System Prompt（三流各自专用） ═══════════

    /**
     * 回复系 system prompt（缓存锚点，顺序严格固定）。
     * core + naturalness_check + redline + format
     * 进攻模式已删除（aggressive.md 不再读取，也不再进入 user 知识段）。
     */
    fun buildSystemPrompt(): String = buildString {
        append(readAsset(AssetRegistry.CORE))
        append("\n\n---\n\n")
        append(readAsset(AssetRegistry.NATURALNESS))
        append("\n\n---\n\n")
        append(readAsset(AssetRegistry.REDLINE))
        append("\n\n---\n\n")
        append(readAsset(AssetRegistry.FORMAT))
    }

    /**
     * 回复链路 prompt 资产的内容指纹。
     *
     * 缓存的 `promptVersion` 以前拿 App 版本名顶过——版本没发就永远算不出
     * "prompt 其实被改过"，改过 prompt 也照样命中旧缓存。
     * 这里对真正进入 system 的四份资产按拼装顺序取组合 SHA-256。
     */
    fun replyPromptAssetHash(): String = AssetFingerprints.hashOf(
        listOf(AssetRegistry.CORE, AssetRegistry.NATURALNESS, AssetRegistry.REDLINE, AssetRegistry.FORMAT)
            .map { it to readAsset(it) }
    )

    /**
     * 对任意一组资产按给定顺序取内容指纹。
     *
     * 算法在 `AssetFingerprints`（纯函数，可离线单测）；这里只负责把资产读出来。
     */
    fun assetHashOf(vararg paths: String): String =
        AssetFingerprints.hashOf(paths.map { it to readAsset(it) })

    /** 谈心专用 system：counseling.md 全文（无安全声明） */
    fun buildCounselingSystemPrompt(): String = readAsset(AssetRegistry.COUNSELING)

    /** 润色专用 system：polish.md 全文 */
    fun buildPolishSystemPrompt(): String = readAsset(AssetRegistry.POLISH)

    /** 主动开场专用 system：proactive.md 全文 */
    fun buildProactiveSystemPrompt(): String = readAsset(AssetRegistry.PROACTIVE)

    /**
     * 从 stage 类 markdown 中提取「当前阶段」小节（## 阶段名 到下一个 ## 之间）。
     * 只使用传入的 KB stage，不再回读活跃库——阶段为空就按未知处理。
     * 生成链路只使用传入快照，防生成期间切库导致阶段来自不同对象。
     */
    suspend fun extractStageSection(assetPath: String, kb: KnowledgeBase? = null): String {
        // 阶段为空时按未知处理，不回读 knowledgeRepo.getActive()
        val stage = kb?.stage?.trim().orEmpty()
        if (stage.isBlank() || stage == "待确定" || stage == "阶段未确定") return ""
        val content = readAsset(assetPath)
        if (content.isBlank()) return ""
        val re = Regex("(^|\\n)##\\s*${Regex.escape(stage)}\\s*\\n(.*?)(?=\\n##\\s|\\z)", RegexOption.DOT_MATCHES_ALL)
        val m = re.find(content) ?: return ""
        val body = m.groupValues[2].trim()
        return if (body.isBlank()) "" else "## $stage\n$body"
    }

    // ═══════════ 知识库注入（回复 user 知识段） ═══════════

    /**
     * 回复系 knowledge 段（user 侧，无历史块、无引用清单）：懂得 + 阶段节选 + 此刻。
     * 委托给 [PromptKnowledgeSection]，本类只负责把文本读出来。
     *
     * 2026-11-21 结构收敛后「我们 / 我的表达偏好 / 经验教训 / 进攻段 / 进行中事项 /
     * 最近对话 / 距上次对话」都已删除。
     */
    suspend fun buildKnowledgeInsertion(kb: KnowledgeBase?, messages: List<ChatMessage> = emptyList()): String {
        if (kb == null) return "（暂无知识库，按通用策略处理）\n\n"
        return buildKnowledgeSection(kb, emptyMap(), "").text
    }

    // ═══════════ 当前场景推断 → `domain/prompt/CurrentSceneInjection` ═══════════

    // 「本轮属于哪一种场景」与「场景段怎么写进 prompt」是纯函数行为块（不读库、不读资产、不读时钟），
    // 已整块搬进 `com.lovebrain.app.domain.prompt.CurrentSceneInjection`——这里只留调用。

    // ═══════════ 回复 User Prompt ═══════════

    /**
     * 回复 user prompt 的最终顺序（2026-11-21 目标结构）：
     * ① 知识段（画像我/她 + 阶段策略 + 此刻 + 历史块）
     * ② 当前场景 ③ 持续意图 ④ `<chat>` ⑤ 用户备注 ⑥ 当前时间
     * format.md 已移入 system（不再附在 user 尾部）。
     *
     * R-DRY: 旧入口现为薄包装，委托 buildReplyUserPromptWithRefs，不再维护两份实现。
     */
    suspend fun buildReplyUserPrompt(
        kb: KnowledgeBase?,
        messages: List<ChatMessage>,
        userHint: String = "",
        intentConfig: com.lovebrain.app.model.IntentConfig = com.lovebrain.app.model.IntentConfig()
    ): String {
        return buildReplyUserPromptWithRefs(kb, messages, userHint, intentConfig).prompt
    }

    // ═══════════ 记忆引用与纠正过滤 ═══════════

    /** Prompt 构建结果 — 包含最终 prompt 文本和实际注入的 MemoryRef 清单
     * B项修复：sourceAliasMap 提供模型来源ID别名→实际消息ID的映射，
     * 供 TopicRecorder 做来源校验时转换。 */
    data class PromptBuildResult(
        val prompt: String,
        val memoryRefs: List<MemoryRef>,
        val sourceAliasMap: Map<String, String> = emptyMap()
    )

    /**
     * 构建回复 user prompt + MemoryRef 清单。
     *
     * 知识段的纯装配逻辑在 [PromptKnowledgeSection]——本类读出文本后把
     * [PromptKnowledgeSection.Input] 交给它，拿回段文本与注入的 MemoryRef。纠正记录在注入前
     * 过滤：WRONG 跳过，MUTED 标记不主动提，WRONG_PERSON 跳过并隔离。
     * 纠正后的 MemoryRef 不出现在清单中。
     *
     * 2026-11-21 结构收敛：
     * - [historicalBlock]：冠军真实历史消息块（3600 字逐字），渲染进知识段「此刻」之后的
     *   原 recent 槽；预算层整块保留/整块移除，不做内部截断。
     * - 未定事项 `uncertainBlock` 已整块删除，本签名不再有它（契约守卫改造由另一批对接）。
     * - [currentTurn] 仅为旧调用点兼容保留（进行中事项检索已删除，本类不再消费）。
     * - 用户备注（来源 `replyDirective.text`）标题为「用户备注」，排在 `<chat>` 之后、
     *   当前时间之前；kb == null 与有库两分支一致。
     */
    suspend fun buildReplyUserPromptWithRefs(
        kb: KnowledgeBase?,
        messages: List<ChatMessage>,
        userHint: String = "",
        intentConfig: com.lovebrain.app.model.IntentConfig = com.lovebrain.app.model.IntentConfig(),
        corrections: Map<String, MemoryCorrection> = emptyMap(),
        historicalBlock: String = "",
        @Suppress("UNUSED_PARAMETER") currentTurn: Int? = null
    ): PromptBuildResult {
        val noteBlock = IntentIdeaBlock.buildAdvisorNoteBlock(userHint, IntentIdeaBlock.USER_NOTE_HEADER)
        if (kb == null) {
            val knowledgeBlock = "（暂无知识库，按通用策略处理）\n\n"
            // 无库时也注入当前场景
            val sceneBlock = CurrentSceneInjection.block(CurrentSceneInjection.infer(messages))
            val chatBlock = ChatTranscriptBlock.render(messages)
            val timestampBlock = buildTimestampPrompt()
            val intentBlock = IntentIdeaBlock.buildIntentBlock(intentConfig)
            // 无库分支也过预算，不再绕过；历史块由预算层接在知识段之后
            val prompt = PromptBudget.byBlocks(
                knowledgeBlock = knowledgeBlock,
                sceneBlock = sceneBlock,
                intentBlock = intentBlock,
                userNoteBlock = noteBlock,
                chatHeader = chatBlock.header,
                chatBody = chatBlock.body,
                timestampBlock = timestampBlock,
                historicalBlock = historicalBlock
            )
            return PromptBuildResult(prompt, emptyList(), chatBlock.sourceAliasMap)
        }

        // 知识段：历史块在段内渲染（原 recent 槽），预算层按 Output.historicalBlock 保护
        val knowledgeOutput = buildKnowledgeSection(kb, corrections, historicalBlock)
        // 推断当前场景并注入——场景是本轮属性，不永久改档案
        val sceneBlock = CurrentSceneInjection.block(CurrentSceneInjection.infer(messages))
        val intentBlock = IntentIdeaBlock.buildIntentBlock(intentConfig)
        val chatBlock = ChatTranscriptBlock.render(messages)
        val timestampBlock = buildTimestampPrompt()

        val prompt = PromptBudget.byBlocks(
            knowledgeBlock = knowledgeOutput.text,
            sceneBlock = sceneBlock,
            intentBlock = intentBlock,
            userNoteBlock = noteBlock,
            chatHeader = chatBlock.header,
            chatBody = chatBlock.body,
            timestampBlock = timestampBlock,
            historicalBlock = knowledgeOutput.historicalBlock,
            historyInKnowledge = knowledgeOutput.historicalBlock.isNotEmpty()
        )
        // refs 裁剪后再生 — 只保留实际在最终 prompt 中出现的引用
        val finalRefs = MemoryRefPolicy.filterRefsByPrompt(knowledgeOutput.refs, prompt)
        return PromptBuildResult(prompt, finalRefs, chatBlock.sourceAliasMap)
    }

    /**
     * 仅看本轮——排除画像、阶段、记忆、意图和偏好。
     * 只携带本轮真实对话记录 + 用户备注 + 时间戳（备注仍排在 `<chat>` 之后）。
     *
     * 与正常回复共用同一个预算入口（[PromptBudget.byBlocks]）：这一支原先自己拼字符串，
     * 60 条 × 任意长度的对话没有任何 9000 上限；复用同一入口后同样受硬帽约束。
     */
    suspend fun buildReplyUserPromptOnlyThisRound(
        messages: List<ChatMessage>,
        userHint: String
    ): PromptBuildResult {
        val chatBlock = ChatTranscriptBlock.render(messages)
        val noteBlock = IntentIdeaBlock.buildAdvisorNoteBlock(userHint, IntentIdeaBlock.USER_NOTE_HEADER)
        val timestampBlock = buildTimestampPrompt()
        // 仅看本轮也注入当前场景——场景是本轮属性，不属于旧记忆
        val sceneBlock = CurrentSceneInjection.block(CurrentSceneInjection.infer(messages))
        // 模式说明充当知识段（"不携带画像、记忆、意图和偏好"是这一档的契约）
        val prompt = PromptBudget.byBlocks(
            knowledgeBlock = "（本轮仅看模式：不携带画像、记忆、意图和偏好）",
            sceneBlock = sceneBlock,
            intentBlock = "",
            userNoteBlock = noteBlock,
            chatHeader = chatBlock.header,
            chatBody = chatBlock.body,
            timestampBlock = timestampBlock
        )
        return PromptBuildResult(prompt, emptyList(), chatBlock.sourceAliasMap)
    }

    // 「对话记录围栏」→ `domain.prompt.ChatTranscriptBlock`；「持续意图/用户备注」→ `IntentIdeaBlock`；
    // 「记忆引用与纠正过滤」→ `MemoryRefPolicy`；回复知识段/主动开场段/反思维段/向量段/核心子集段
    // → `PromptKnowledgeSection` 等同名 object。均为纯函数（只接收已读出文本与纠正表，返回 String），
    // 本类只负责读资产/读库/拼装最终 prompt。
    /**
     * 读取回复系知识段所需的全部输入并委托 [PromptKnowledgeSection] 装配。
     * 纠正记录在注入前过滤。
     *
     * 2026-11-21 起只读：我 / 她 / 阶段 / 当前话题 / 场景状态链，外加调用方传入的历史块。
     * warmth、style、经验、事项、recent、距上次对话一律不再读取。
     */
    private suspend fun buildKnowledgeSection(
        kb: KnowledgeBase,
        corrections: Map<String, MemoryCorrection>,
        historicalBlock: String = ""
    ): PromptKnowledgeSection.Output {
        val me = readFileCompat(kb.name, "understand/me.md")
        val her = readFileCompat(kb.name, "understand/her.md")
        val stageSection = extractStageSection(AssetRegistry.STAGE, kb)
        val topic = knowledgeRepo.getCurrentTopic(kb.name)
        val sceneChain = knowledgeRepo.readFile(kb.name, "moment/scene.md")

        return PromptKnowledgeSection.build(
            PromptKnowledgeSection.Input(
                kbName = kb.name,
                me = me,
                her = her,
                stageSection = stageSection,
                topic = topic,
                sceneChain = sceneChain,
                corrections = corrections,
                // 龄标注/过期判断用注入的时钟：同一次装配里"现在"只有一个来源
                nowMs = clock.epochMs(),
                today = clock.today(),
                historicalBlock = historicalBlock
            )
        )
    }

    // ═══════════ 时间注入 ═══════════

    /** Timestamp Injection prompt */
    fun buildTimestampPrompt(): String {
        val time = clock.wallClock()
        return "【当前时间】$time\n所有回复必须基于上述当前时间进行时段判断，禁止臆测。回复需自然贴合当前时段。时间只认系统给定的当前时间，不凭对话内容或主观感觉推测。"
    }

    // ═══════════ 谈心 / 润色 User Prompt ═══════════

    /**
     * 谈心 user prompt：核心知识子集（固定块，不吃主回复 9000 帽、不做字符级裁剪）
     * + 倾诉与任务段 + 时间戳垫底。
     * confessionTaskBlock = GenerationEngine 谈心调用点的逐字 suffix
     * （倾诉与任务句自基线 GE suffix 逐字搬移，===分析=== 契约，禁区）；
     * 段前空白由块自带，此处不再追加分隔，拼合结果逐字等于  规格。
     */
    suspend fun buildCounselingUserPrompt(kb: KnowledgeBase?, confessionTaskBlock: String): String = buildString {
        append(buildCoreKnowledgeSubset(kb))
        append(confessionTaskBlock)
        append("\n\n")
        append(buildTimestampPrompt())
    }

    /**
     * 润色 user prompt：仅草稿（无时间戳、无知识库、无场景）。
     * 空草稿兜底：固定文案（无草稿，请主动给出开场）。
     */
    fun buildPolishUserPrompt(draft: String): String =
        draft.trim().ifBlank { "（无草稿，请主动给出开场）" }

    /**
     * 主动开场 user prompt——替代旧润色 prompt。
     *
     * 2026-11-21 结构收敛：普通与「仅看本轮」两档**都只保留用户草稿 + 当前时间**。
     * 军师备注 / 当前场景 / 本轮真实对话 / 持续意图 / 对方画像 / 近期对话 / 表达偏好
     * 全部删除，**一次知识库读取都不做**。
     */
    suspend fun buildProactiveUserPrompt(draft: String): String {
        // 时间戳垫底；两档共用同一颗尾巴
        val timestampTail = "## 当前时间\n" + clock.wallClock() + "\n"
        return PromptProactiveSection.build(draft) + timestampTail
    }

    /**
     * 核心知识子集（谈心 user 侧，DRY）：只保留画像「我 / 她 / 我们」。
     * 表达偏好、最近 3 块经验、进行中事项已按用户拍板删除。
     * 无阶段节选、无此刻、无最近对话。装配逻辑在 [PromptCoreKnowledgeSection]。
     */
    private suspend fun buildCoreKnowledgeSubset(kb: KnowledgeBase?): String {
        if (kb == null) return "（暂无知识库，按通用策略处理）\n\n"
        val me = readFileCompat(kb.name, "understand/me.md")
        val her = readFileCompat(kb.name, "understand/her.md")
        val warmth = readFileCompat(kb.name, "understand/warmth.md")

        return PromptCoreKnowledgeSection.build(
            PromptCoreKnowledgeSection.Input(me = me, her = her, warmth = warmth)
        )
    }

    // ═══════════ 辅助引擎（经验/画像/向量） ═══════════

    fun buildLessonsSystemPrompt(): String = readAsset(AssetRegistry.LESSONS)

    fun buildLessonsUserPrompt(topicContext: String): String = buildString {
        append("以下是一段已结束的对话话题的完整记录。请按照经验提取引擎的格式，提取经验。\n\n")
        append(topicContext)
    }

    /**
     * reflect system prompt 动态注入 ProfileUpdateSchema——
     * reflect.md 只保留语义规则/证据门控/更新原则，不再硬编码 JSON 字段 schema。
     * schema 描述由 ProfileUpdateSchema.schemaDescriptionForPrompt() 单一真源提供。
     */
    fun buildReflectSystemPrompt(): String = buildString {
        append(readAsset(AssetRegistry.REFLECT))
        append("\n\n---\n\n")
        append(ProfileUpdateSchema.schemaDescriptionForPrompt())
    }

    suspend fun buildReflectUserPrompt(kbName: String): String {
        //  F3.3（提取链消费 corrections，最小改动点）：画像 reflect 读源材料时与注入侧
        // 同一颗纠正过滤（CorrectionExtractionFilter → MemoryRefPolicy 语义）——
        // 被纠正的画像/经验内容不再按原文回流进画像重写。此处只改"提取 prompt"这一支，
        // 回复装配主函数未动。谈心分析无 MemoryRef 纠正身份，原样读取。
        val corrections = knowledgeRepo.readCorrections(kbName)
        val me = correctionFiltered(kbName, MemoryKind.PROFILE, "understand/me.md",
            readFileCompat(kbName, "understand/me.md"), corrections)
        val her = correctionFiltered(kbName, MemoryKind.PROFILE, "understand/her.md",
            readFileCompat(kbName, "understand/her.md"), corrections)
        val warmth = correctionFiltered(kbName, MemoryKind.PROFILE, "understand/warmth.md",
            readFileCompat(kbName, "understand/warmth.md"), corrections)
        //  读侧净化：同 buildKnowledgeSection，走 `LessonDoc.purify` 那一颗共用口
        val lessons = correctionFiltered(
            kbName, MemoryKind.LESSON, LessonDoc.LESSONS_PATH,
            LessonDoc.purify(knowledgeRepo.readFile(kbName, LessonDoc.LESSONS_PATH)), corrections
        )
        //  S3 遗漏 A：raw_topic 之前原样进画像更新 prompt（画像/经验已过滤、归档没过滤）。
        // 现在与 KnowledgeContextReader 的 R4 读取侧走**同一颗**：scene 生效纠正 →
        // "已被纠正的条目原文"对撞基准（ S3 遗漏 B 的持久依据优先，scene.md 现算兜底）→
        // raw_topic 按条目级/行级精确剔除。未纠正时逐字放行，与 R4 行为一致。
        val sceneRaw = readFileCompat(kbName, CorrectionExtractionFilter.SCENE_SOURCE_PATH)
        val correctedFacts = CorrectionExtractionFilter.correctedSceneFactTexts(
            kbName, sceneRaw, corrections,
            CorrectionExtractionFilter.decodePersistedFacts(
                readFileCompat(kbName, CorrectionExtractionFilter.PERSISTED_FACTS_PATH)
            )
        )
        val rawTopic = CorrectionExtractionFilter
            .filterTopicArchive(knowledgeRepo.readFile(kbName, "memory/raw_topic.md"), correctedFacts)
            .text
        val counselingAnalysis = knowledgeRepo.readCounselingAnalysisBlocks(kbName, 2)
        return PromptReflectSection.build(
            PromptReflectSection.Input(
                me = me, her = her, warmth = warmth,
                lessons = lessons, rawTopic = rawTopic,
                counselingAnalysis = counselingAnalysis
            )
        )
    }

    fun buildVectorSystemPrompt(): String = readAsset(AssetRegistry.VECTOR)

    fun buildVectorUserPrompt(currentVector: Map<String, Int>, currentStage: String, context: String): String =
        PromptVectorSection.build(currentVector, currentStage, context)

    // ═══════════ 工具方法 ═══════════

    private suspend fun readFileCompat(kbName: String, newPath: String): String =
        knowledgeRepo.readFile(kbName, newPath)

    /**
     * 提取链源材料统一纠正过滤（ F3.3）——[com.lovebrain.app.domain.prompt.CorrectionExtractionFilter]
     * 的薄转发：null（整段排除）折成空串，其余逐字透传。语义单一真源在那颗 object
     * （复用 [MemoryRefPolicy] 的 WRONG/MUTED/FINISHED/WRONG_PERSON 判据），这里不另立规则。
     */
    private fun correctionFiltered(
        kbName: String,
        kind: MemoryKind,
        sourcePath: String,
        content: String,
        corrections: Map<String, MemoryCorrection>
    ): String =
        CorrectionExtractionFilter.filterForExtraction(kbName, kind, sourcePath, content, corrections) ?: ""

    // E4：asset 缺失不再静默吞掉，记日志便于定位 prompt 段丢失——日志随 fail-open 兜底一起
    // 挪进了实现侧（data/AssetPromptSource），这里只问端口拿全文。
    private fun readAsset(path: String): String = promptSource.read(path)
}
