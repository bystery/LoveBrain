package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.domain.port.SystemClock
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.OngoingItem
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SceneFact
import com.lovebrain.app.util.L

/**
 * 话题生命周期管理器——**一轮提交的事务协调者**。
 *
 * 核心逻辑：
 * - 每轮对话记录到 moment/recent.md（最近 2 轮，溢出→对话暂存）
 * - 场景事实写入 moment/scene.md（带时间戳的状态链）
 * - 超过 TTL/最大条数 的状态条目移入 memory/raw_scene.md
 * - 话题切换判定：仅凭 topic_status=new
 * - 话题切换时归档到 memory/raw_topic.md（状态倒序），重置此刻层
 * - ongoing 进行中事项合并写入 moment/plan.md（跨话题生存）
 * - 轮次/状态条目解析使用真实时间戳校验，防 schema 模板示例行混入
 *
 * 设计要点：
 * - 废弃中文关键词列表匹配
 * - 每条 scene fact 携带 sourceIds，引用本轮 HER/ME 消息 ID
 * - 客户端逐条校验 sourceId 必须属于冻结快照中的 HER/ME；非法来源只拒绝该事实
 * - 模型重述旧事实不更新时间；只有新来源的真实证据才更新
 * - "她"和"我"不合并：通过来源消息的 role 区分
 * - 相同来源重复提交幂等
 * - 无法确定同一事项时保守不覆盖
 *
 * ## 这里只剩一个变化理由
 * "一轮提交要跨过六个写入边界、崩在任意一步都要能收敛"——即 [record] 的事件构建、
 * [applyRound] 的投影顺序与水位、[recoverIfNeeded] 的崩溃恢复。
 * 各份文件的**正文格式与合并判据**已经各自的 owner 拿走：
 *
 * | 协作者 | 管的这份东西 | 它的变化理由 |
 * | --- | --- | --- |
 * | [RecentRoundStore] | moment/recent.md | 轮次记录正文长什么样、留几轮、溢出到哪 |
 * | [SceneChainStore] | moment/scene.md + raw_scene.md | 状态链行的格式、来源校验、幂等与过期归档 |
 * | [OngoingPlanStore] | moment/plan.md | 事项身份、状态链合并、防旧任务复活 |
 * | [KnowledgeContextReader] | 读侧上下文装配 | 喂给经验提取/向量重估的那两段文本怎么拼 |
 *
 * 拆动公共 API 的地方一个也没有：类名、构造参数表、被外部调用的方法签名都原样留着，
 * 上面这四个协作者由本类自建（构造参数不许变，DI 与调用点都在别的文件里），
 * 它们只依赖知识库的读/写端口与 [Clock] 端口（同一本仓库、同一组实例），不碰 data/android 的具体实现。
 */
class TopicRecorder(
    private val knowledgeRepo: KnowledgePort,
    roundCommitJournal: RoundCommitJournal? = null,
    /**
     * 时间从端口来，不在这里 `TimeFmt.now()`：轮次块头 `- [yyyy-MM-dd HH:mm]`
     * 和场景时间戳是**写进知识库正文的内容**，不是日志。有了可注入的时钟，
     * "这一轮落进 recent.md 的块头到底是什么"才能被钉住断言（见 TopicRecorderClockTest）。
     */
    private val clock: Clock = SystemClock
) {

    /**
     * WAL 始终启用。未注入时用同一实现自建，避免出现
     * "有 journal 走事务 / 无 journal 裸写" 两套写入路径。
     *
     * internal 只为让 Koin 图测试能断言"注入的就是容器里那一个"——
     * 生产代码里没有任何地方第二个 new 它（AppModule 用 get() 复用同一实例）。
     */
    internal val journal: RoundCommitJournal = roundCommitJournal ?: RoundCommitJournal(knowledgeRepo)

    /**
     * 各投影的 owner。都用本类持有的同一份端口与同一个时钟构造，
     * 所以"注入的时钟是不是摆设"这件事不会因为搬家而失效（见 ClockWiringTest）。
     */
    private val recentStore = RecentRoundStore(knowledgeRepo)
    private val sceneStore = SceneChainStore(knowledgeRepo, clock)
    // 计划格只要"读旧 plan + 写新 plan"，所以把它要的读、写两条门分别递给它：
    // knowledgeRepo 这本仓库同时 implements 读口与写口，同一实例、同一条写链，只是视角收窄。
    private val planStore = OngoingPlanStore(knowledgeRepo, knowledgeRepo)
    private val contextReader = KnowledgeContextReader(knowledgeRepo)

    /**
     * 记录一轮对话 + 处理话题状态 + 更新场景链 + 合并进行中事项。
     *
     * sceneFacts 参数为带来源 ID 的 [SceneFact]，
     * 同时传入冻结的 messages 快照用于来源校验。
     *
     * 六个写入边界（话题归档、话题标签、recent、scene、plan、轮次计数）
     * 全部包在一把 journal 事务锁里，完整事件先落 WAL 再动 target。
     * 幂等判据是 [RoundCommitJournal.isRoundCommitted] + 每投影水位，
     * 不再拿 recent.md 的 HTML marker 当跨文件提交标记。
     *
     * @param topicStatus 从 AI 回复 JSON 中提取的话题状态（same/drift/new）
     * @param topicLabel 从 AI 回复 JSON 中提取的话题标签
     * @param sceneFacts 从 AI 回复 JSON 中提取的场景关键事实（带来源 ID）
     * @param ongoing 从 AI 回复 JSON 中提取的进行中事项变化
     * @param sourceAliasMap her-0/me-1 之类别名→真实消息 ID；在进 WAL 之前解析，
     *                       使首提与崩溃恢复使用同一份 payload
     * @return true 如果话题发生了切换（用于触发知识库更新）
     */
    suspend fun record(
        kb: KnowledgeBase,
        messages: List<ChatMessage>,
        scheme: Scheme?,
        topicStatus: String,
        topicLabel: String,
        sceneFacts: List<SceneFact> = emptyList(),
        userHint: String = "",
        ongoing: List<OngoingItem> = emptyList(),
        likedSchemes: List<Scheme> = emptyList(),
        sourceAliasMap: Map<String, String> = emptyMap()
    ): Boolean {
        val time = clock.wallClock()

        val conversationalMessages = messages.filter {
            it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME
        }
        val roundMsgIds = conversationalMessages.map { it.id }.sorted().joinToString(",")

        // 1. 确定本轮变更意图（不写任何 target）——必须在 WAL PREPARED 之前完成
        val curTopic = knowledgeRepo.getCurrentTopic(kb.name)
        val hasTopic = curTopic.isNotBlank() && curTopic != "（等待第一次对话）"
        val shouldRotate = topicLabel.isNotBlank() && topicStatus == "new"

        // 2. 构建本轮记录（纯函数，不写入）
        val entry = recentStore.buildRoundEntry(
            time = time,
            roundMsgIds = roundMsgIds,
            conversationalMessages = conversationalMessages,
            userHint = userHint,
            scheme = scheme,
            likedSchemes = likedSchemes
        )

        // 3. 别名→真实消息 ID 在 WAL 之前解析；恢复时不再依赖调用方上下文
        val resolvedSceneFacts = sceneFacts.map { sf ->
            sf.copy(sourceIds = sf.sourceIds.map { raw -> sourceAliasMap[raw] ?: raw })
        }

        // 4. 稳定 roundId——同一轮重试得到同一身份
        val roundId = RoundCommitJournal.stableRoundId(kb.name, roundMsgIds, entry)

        // 5. 本轮是否已完整提交（跨文件幂等的唯一判据）。
        //    这是锁外快路径；并发同轮的第二个协程由 commit() 内的锁内重读拦下，
        //    两条路径都归到这里，调用方看到的返回值语义一致。
        if (journal.isRoundCommitted(kb.name, roundId)) {
            L.w("round $roundId already committed, skipping all writes (roundMsgIds=$roundMsgIds)")
            return false
        }

        val event = RoundCommitJournal.RoundCommitEvent(
            roundId = roundId,
            kbName = kb.name,
            inputRevision = knowledgeRepo.getTurnCount(kb.name),
            timestamp = time,
            topicStatus = topicStatus,
            topicLabel = topicLabel,
            shouldRotate = shouldRotate,
            hadTopic = hasTopic,
            roundMsgIds = roundMsgIds,
            frozenMessages = conversationalMessages.map { RoundCommitJournal.JournalMessage.of(it) },
            recentEntry = entry,
            sceneFacts = resolvedSceneFacts.map { RoundCommitJournal.JournalSceneFact.of(it) },
            ongoing = ongoing.map { RoundCommitJournal.JournalOngoingItem.of(it) },
            turnCountIncrement = 1
        )

        return when (val outcome = journal.commit(kb.name, event) { e -> applyRound(e) }) {
            is RoundCommitJournal.CommitOutcome.Committed -> outcome.value
            // 另一个协程在本协程排队等锁时把这一轮提交了：本轮没有新话题切换
            RoundCommitJournal.CommitOutcome.AlreadyCommitted -> false
        }
    }

    /**
     * 一轮提交的六个投影——首次提交与崩溃恢复走的是同一段代码。
     *
     * 顺序固定（话题归档会清空 recent/scene，必须排在它们之前），
     * 每个投影完成后由 journal 推进水位；进程中途被杀后，
     * [recoverIfNeeded] 只补做水位尚未覆盖的投影。
     *
     * 每个投影现在只负责"要不要跑 + 把事件里的那份数据交给 owner"，
     * 具体怎么写盘归各自的 Store——两边判据都没有改，只是不再住在同一个类里。
     *
     * 返回 true 表示本轮确实执行了话题归档。恢复路径上若归档早已生效，
     * 返回 false——调用方据此不再重复触发知识库更新。
     */
    private suspend fun RoundCommitJournal.Tx.applyRound(
        event: RoundCommitJournal.RoundCommitEvent
    ): Boolean {
        val kbName = event.kbName
        var topicRotated = false

        // 投影 1：话题归档（仅 status=new 且有有效旧话题）
        apply(RoundCommitJournal.PROJ_TOPIC_ROTATE) {
            if (event.shouldRotate && event.hadTopic) {
                knowledgeRepo.rotateTopic(kbName)
                topicRotated = true
            }
        }

        // 投影 2：话题标签
        apply(RoundCommitJournal.PROJ_TOPIC_SET) {
            val newLabel = when {
                event.shouldRotate -> event.topicLabel
                event.topicStatus == "drift" && event.topicLabel.isNotBlank() -> event.topicLabel
                !event.hadTopic -> event.topicLabel.ifBlank { "日常对话" }
                else -> null
            }
            if (newLabel != null) knowledgeRepo.setCurrentTopic(kbName, newLabel)
        }

        // 投影 3：moment/recent.md
        apply(RoundCommitJournal.PROJ_RECENT) {
            if (event.recentEntry.isNotBlank()) {
                recentStore.writeRecent(kbName, event.recentEntry, event.roundMsgIds)
            }
        }

        // 投影 4：moment/scene.md（带完整来源与归属）
        apply(RoundCommitJournal.PROJ_SCENE) {
            if (event.sceneFacts.isNotEmpty()) {
                sceneStore.updateSceneChain(
                    kbName = kbName,
                    topicLabel = event.topicLabel,
                    sceneFacts = event.sceneFacts.map { it.toSceneFact() },
                    frozenMessages = event.frozenMessages.map { it.toChatMessage() }
                )
            }
        }

        // 投影 5：moment/plan.md（完整 OngoingItem，不再只留 name）
        apply(RoundCommitJournal.PROJ_PLAN) {
            if (event.ongoing.isNotEmpty()) {
                planStore.mergeOngoing(kbName, event.ongoing.map { it.toOngoingItem() }, event.timestamp)
            }
        }

        // 投影 6：轮次计数——按事件中的增量值使用，恢复路径同样读该字段
        apply(RoundCommitJournal.PROJ_COUNT) {
            if (event.turnCountIncrement > 0) {
                knowledgeRepo.incrementTurnCountBy(kbName, event.turnCountIncrement)
            }
        }

        return topicRotated
    }

    /**
     * 崩溃恢复——若 journal 中有 PREPARED/WRITING 的在途轮次，
     * 用与首提完全相同的 [applyRound] 补齐尚未覆盖的投影。
     *
     * 在 KB 被打开/激活时调用。恢复不再自己拼一套"简化版"重放：
     * 旧实现把 scene 的来源清空、把 ongoing 降级成只有 name 的伪事项，
     * 还会因"recent 刚由恢复写入、count 从未写入"而错误跳过计数。
     */
    suspend fun recoverIfNeeded(kbName: String) {
        journal.recover(kbName) { event -> applyRound(event) }
    }

    /** 获取经验提取的完整上下文：当前话题 + 场景链 + 最近对话 + 暂存 + 话题档案（最近N个） */
    suspend fun getTopicFullContext(kbName: String, topicCount: Int = AppConfig.VECTOR_CONTEXT_TOPICS): String =
        contextReader.getTopicFullContext(kbName, topicCount)

    /** 向量重估专用上下文：当前话题 + 场景 + 最近对话 + 最近 N 个话题档案 */
    suspend fun getVectorContext(kbName: String): String = contextReader.getVectorContext(kbName)
}
