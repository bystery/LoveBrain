package com.lovebrain.app.domain

import com.lovebrain.app.data.AssetPromptSource
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplyMemoryRefs
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.buildGenerationInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「仅看本轮」与上下文范围的请求正文合同（2026-11-21 结构收敛后）。
 *
 * 判据全部来自 [CapturingGateway] 记下的**真正交给 provider 的那串 user 正文**：
 * - 主动开场：普通与「仅看本轮」两档都只留「用户草稿 + 当前时间」，**零知识库读取**。
 * - 回复「仅看本轮」：模式说明（充当知识段）+ 本轮真实对话 + 用户备注 + 当前场景 + 时间，零 KB 读取、零引用。
 * - 回复普通：知识段只含画像我/她 + 阶段 + 此刻（当前话题/场景状态链）+ 意图，再拼
 *   场景/chat/用户备注/时间；「我们 / 表达偏好 / 经验 / 近期 / 进行中事项 / 最近对话」
 *   一行都不许回来。
 */
class ProactiveRoundScopeTest {

    // ═══════════════════════ 两组独特标记 ═══════════════════════

    /** 负标记：只可能来自知识库 / 持续意图，开启后一条都不许出现在请求正文里 */
    private val oldMemoryMarkers = listOf(
        "KBME-画像我", "KBHER-画像她", "KBWARMTH-我们温度", "KBSTYLE-表达偏好",
        "KBLESSON-旧经验", "KBSCENE-旧场景链", "KBRECENT-近期历史", "KBPLAN-旧事项",
        "KBTOPIC-旧话题", "KBINTENT-持续意图"
    )

    /** 结构收敛后已删除的段标题：两档/两链路都不许出现 */
    private val deletedHeaders = listOf(
        "## 对方画像", "## 近期对话", "## 表达偏好", "## 我的表达偏好",
        "# 【记忆】经验教训", "# 【进行中事项】", "# 最近对话", "距上次对话"
    )

    /**
     * 「（本轮仅看模式：…）」声明在新结构里是回复仅看本轮档的**知识段占位**（生产保留，
     * 见 PromptBuilder.buildReplyUserPromptOnlyThisRound 与 PromptBuilderOnlyThisRoundBudgetTest
     * 的「模式说明仍在」）：回复开启档必须逐字在场；主动开场已无档位概念，不许出现。
     */
    private val roundScopeHeader = "（本轮仅看模式"

    private val dialogueMarkers = listOf("ROUNDHER-她说她加班到十点", "ROUNDME-我想回这句")
    private val noteMarker = "ROUNDNOTE-本轮备注别再问忙不忙"
    private val draftMarker = "ROUNDDRAFT-本轮草稿想约她看展"

    private val kb = KnowledgeBase(
        name = "kb", displayName = "核查库", stage = "暧昧期", turnCount = 3, active = true
    )

    private val roundDialogue = listOf(
        ChatMessage(id = "h1", role = ChatMessage.Role.HER, content = "ROUNDHER-她说她加班到十点"),
        ChatMessage(id = "m1", role = ChatMessage.Role.ME, content = "ROUNDME-我想回这句")
    )

    private val note = "ROUNDNOTE-本轮备注别再问忙不忙"
    private val draft = "ROUNDDRAFT-本轮草稿想约她看展"

    /** 启用中的持续意图：回复普通分支应注入，开启时整块消失 */
    private val liveIntent = IntentConfig(text = "KBINTENT-持续意图：先把见面约下来", enabled = true)

    // ═══════════════════════ 假边界 ═══════════════════════

    private class CountingKnowledgePort(val inner: InMemoryKnowledgePort) : KnowledgePort by inner {
        var touches = 0
        val readPaths = mutableListOf<String>()

        private fun see(path: String) { touches++; readPaths += path }

        override suspend fun readFile(kbName: String, relativePath: String): String {
            see(relativePath); return inner.readFile(kbName, relativePath)
        }
        override suspend fun getActive(): KnowledgeBase? { see("getActive"); return inner.getActive() }
        override suspend fun getCurrentStage(kbName: String): String {
            see("getCurrentStage"); return inner.getCurrentStage(kbName)
        }
        override suspend fun getCurrentTopic(kbName: String): String {
            see("getCurrentTopic"); return inner.getCurrentTopic(kbName)
        }
        override suspend fun getTurnCount(kbName: String): Int {
            see("getTurnCount"); return inner.getTurnCount(kbName)
        }
        override suspend fun readPlanActive(kbName: String): String {
            see("readPlanActive"); return inner.readPlanActive(kbName)
        }
        override suspend fun readVector(kbName: String): Map<String, Int> {
            see("readVector"); return inner.readVector(kbName)
        }
        override suspend fun readCounselingAnalysisBlocks(kbName: String, count: Int): String {
            see("readCounselingAnalysisBlocks"); return inner.readCounselingAnalysisBlocks(kbName, count)
        }
        override suspend fun getCorrectionsRevision(kbName: String): Int {
            see("getCorrectionsRevision"); return inner.getCorrectionsRevision(kbName)
        }
        override suspend fun getLessonCount(kbName: String): Int {
            see("getLessonCount"); return inner.getLessonCount(kbName)
        }
    }

    private class CapturingGateway(private val config: ProviderRequestConfig) : AiGateway {
        val systems = mutableListOf<String>()
        val users = mutableListOf<String>()

        override fun snapshotProviderConfig(): ProviderRequestConfig? = config
        override fun configForTicket(ticketId: String): ProviderRequestConfig? =
            config.takeIf { it.ticketId == ticketId }

        override fun generateStream(
            systemPrompt: String,
            userPrompt: String,
            thinkingOverride: Int?,
            thinkingShapeIndex: Int,
            config: ProviderRequestConfig?
        ): Flow<StreamEvent> {
            systems += systemPrompt
            users += userPrompt
            return flowOf(
                StreamEvent.Chunk("""{"options":[{"text":"开场一","angle":"日常"}]}"""),
                StreamEvent.Complete("""{"options":[{"text":"开场一","angle":"日常"}]}""")
            )
        }

        override suspend fun generateRaw(systemPrompt: String, userPrompt: String): String =
            error("这一族不走非流式")

        override suspend fun generateRawWithMetadata(
            systemPrompt: String,
            userPrompt: String
        ): RawGenerationResult = error("这一族不走非流式")

        override fun parseReplyResponse(content: String): LoveBrainResponse =
            LoveBrainResponse(response = ReplySchemes(recommended = "r"))
    }

    private val providerConfig = ProviderRequestConfig(
        ticketId = "T-roundscope", apiKey = "k", baseUrl = "https://x.example/v1",
        model = "m", thinkingMode = 0
    )

    /** 资产真源：test classpath 上那**同一份** markdown，禁止副本/内联 */
    private fun loadAsset(path: String): String {
        val res = ClassLoader.getSystemClassLoader().getResourceAsStream(path)
            ?: error("资产 $path 不在 test classpath 上（build.gradle.kts 把 src/main/assets 挂进来了）")
        return res.bufferedReader().use { it.readText() }
    }

    private val ctx: PromptSourcePort by lazy {
        AssetPromptSource { path -> loadAsset(path).byteInputStream() }
    }

    private class Run(
        val user: String,
        val system: String,
        val touches: Int,
        val refs: List<MemoryRef>,
        val readPaths: List<String>
    )

    private fun newFixture(): Pair<CountingKnowledgePort, CapturingGateway> {
        val inner = InMemoryKnowledgePort()
        inner.seedLibrary(kb)
        inner.seed("kb", "understand/me.md", "KBME-画像我：喜欢手冲咖啡")
        inner.seed("kb", "understand/her.md", "KBHER-画像她：猫奴，最近在准备雅思")
        inner.seed("kb", "understand/warmth.md", "KBWARMTH-我们温度：轻松，会主动找我聊")
        inner.seed("kb", "understand/style.md", "KBSTYLE-表达偏好：短句、少表情")
        inner.seed("kb", "memory/lessons.md", "# KBLESSON-旧经验\n别再追问她累不累\n")
        inner.seed("kb", "moment/scene.md", "- [2020-01-01 09:00] KBSCENE-旧场景链：聊过展览")
        inner.seed("kb", "moment/recent.md", "KBRECENT-近期历史：上周为回消息慢吵过一次")
        inner.seed("kb", "moment/plan.md", "KBPLAN-旧事项 ~展览门票 | 进行中 | KBPLAN-旧事项链")
        inner.seed("kb", "moment/topic.md", "KBTOPIC-旧话题：手冲咖啡")
        return CountingKnowledgePort(inner) to CapturingGateway(providerConfig)
    }

    private fun runProactive(onlyThisRound: Boolean): Run {
        val (port, gateway) = newFixture()
        val clock = FixedClock()
        val builder = PromptBuilder(ctx, port, clock)
        val engine = GenerationEngine(gateway, builder)
        val events = runBlocking {
            engine.proactiveStream(
                requestId = "req-proactive",
                draft = draft
            ).toList()
        }
        assertEquals("一次主动发只交给 provider 一条请求", 1, gateway.users.size)
        assertTrue("主动发必须真的出 options：$events", events.any { it is com.lovebrain.app.model.ProactiveOptions })
        return Run(gateway.users.last(), gateway.systems.last(), port.touches, emptyList(), port.readPaths)
    }

    private fun runReply(onlyThisRound: Boolean): Run {
        val (port, gateway) = newFixture()
        val clock = FixedClock()
        val builder = PromptBuilder(ctx, port, clock)
        val engine = GenerationEngine(gateway, builder)
        val input = buildGenerationInput(
            requestId = "req-reply",
            messages = roundDialogue,
            userHint = note,
            knowledgeBase = kb,
            intentConfig = liveIntent,
            corrections = emptyMap(),
            correctionsRevision = 0,
            onlyThisRound = onlyThisRound,
            providerIdentity = providerConfig.toIdentity(),
            kbProfile = "KBME-画像我",
            kbRevision = "rev-1",
            promptAssetHash = ""
        )
        val events = runBlocking { engine.replyStream(input).toList() }
        assertEquals("一次回复只交给 provider 一条请求", 1, gateway.users.size)
        val refs = events.filterIsInstance<ReplyMemoryRefs>().flatMap { it.refs }
        return Run(gateway.users.last(), gateway.systems.last(), port.touches, refs, port.readPaths)
    }

    /** 把截到的正文打成读数行——绿了不打数等于没测 */
    private fun report(chain: String, switchOn: Boolean, run: Run) {
        val leaked = oldMemoryMarkers.filter { run.user.contains(it) }
        println(
            "ROUNDSCOPE|$chain|switch=${if (switchOn) "ON" else "OFF"}|" +
                "kbTouches=${run.touches}|userBytes=${run.user.toByteArray(Charsets.UTF_8).size}|" +
                "roundHeader=${run.user.contains(roundScopeHeader)}|refs=${run.refs.size}|" +
                "旧标记漏进=${if (leaked.isEmpty()) "无" else leaked.joinToString(",")}|" +
                "读过的库=${run.readPaths.joinToString(",")}"
        )
    }

    private fun assertNoRetiredSections(user: String) {
        deletedHeaders.forEach { header ->
            assertFalse("已删除的段「$header」仍出现在请求正文里:\n$user", user.contains(header))
        }
    }

    // ═══════════════════════ 主动发：只留草稿 + 时间 ═══════════════════════

    @Test
    fun proactive_carries_only_the_draft_in_both_switch_positions() {
        listOf(true, false).forEach { only ->
            val run = runProactive(onlyThisRound = only)
            report("proactive", only, run)
            assertTrue("草稿必须逐字在场:\n${run.user}", run.user.contains(draftMarker))
            assertTrue("时间戳垫底:\n${run.user}", run.user.contains("## 当前时间"))
            assertFalse("主动开场没有档位声明（本轮档位只属于回复链路）:\n${run.user}", run.user.contains(roundScopeHeader))
            assertFalse("备注不再进主动开场:\n${run.user}", run.user.contains(noteMarker))
            dialogueMarkers.forEach { marker ->
                assertFalse("本轮真实对话不再进主动开场「$marker」:\n${run.user}", run.user.contains(marker))
            }
            assertNoRetiredSections(run.user)
            oldMemoryMarkers.forEach { marker ->
                assertFalse("旧记忆标记「$marker」混进了主动发请求正文:\n${run.user}", run.user.contains(marker))
            }
            assertEquals(
                "主动开场对知识库的读取次数必须是 0，实到 ${run.touches} 次：${run.readPaths}",
                0, run.touches
            )
        }
    }

    // ═══════════════════════ 回复：仅看本轮 ═══════════════════════

    @Test
    fun reply_with_switch_on_hands_no_old_memory_and_no_refs() {
        val run = runReply(onlyThisRound = true)
        report("reply", true, run)

        (dialogueMarkers + noteMarker).forEach { marker ->
            assertTrue("开启时本轮标记「$marker」必须在回复请求正文里:\n${run.user}", run.user.contains(marker))
        }
        assertTrue("本轮档位声明作为知识段逐字在场:\n${run.user}", run.user.contains(roundScopeHeader))
        assertTrue("用户备注段用「用户备注」标题", run.user.contains(com.lovebrain.app.domain.prompt.IntentIdeaBlock.USER_NOTE_HEADER))
        oldMemoryMarkers.forEach { marker ->
            assertFalse("开启时旧记忆标记「$marker」混进了回复请求正文:\n${run.user}", run.user.contains(marker))
        }
        assertNoRetiredSections(run.user)
        assertEquals("开启时回复链路对知识库的读取次数必须是 0，实到 ${run.touches} 次", 0, run.touches)
        assertTrue("开启时交回的记忆引用必须是空清单（非空即假引用），实到 ${run.refs}", run.refs.isEmpty())
    }

    // ═══════════════════════ 回复：普通分支 ═══════════════════════

    @Test
    fun reply_with_switch_off_keeps_only_the_retained_knowledge() {
        val run = runReply(onlyThisRound = false)
        report("reply", false, run)

        // 保留下来的知识：画像我/她、当前话题、持续意图、本轮对话与用户备注
        listOf("KBME-画像我", "KBHER-画像她", "KBTOPIC-旧话题", "KBINTENT-持续意图").forEach { marker ->
            assertTrue("关闭时「$marker」应按新结构进请求:\n${run.user}", run.user.contains(marker))
        }
        (dialogueMarkers + noteMarker).forEach { marker ->
            assertTrue("关闭时本轮那几件照样在场:\n$marker", run.user.contains(marker))
        }
        // 结构收敛后已删除的内容：经验 / 表达偏好 / 近期对话 / 旧事项 / 我们 / 场景链
        listOf(
            "KBLESSON-旧经验", "KBSTYLE-表达偏好", "KBRECENT-近期历史",
            "KBPLAN-旧事项", "KBWARMTH-我们温度", "KBSCENE-旧场景链"
        ).forEach { gone ->
            assertFalse("已删除的内容「$gone」仍在请求正文里:\n${run.user}", run.user.contains(gone))
        }
        assertNoRetiredSections(run.user)
        assertTrue("关闭时必须真交回引用清单，否则上面那些「不含」都是假绿", run.refs.isNotEmpty())
        assertEquals(
            "普通回复只读画像我/她 + 当前话题 + 场景链",
            listOf("understand/me.md", "understand/her.md", "getCurrentTopic", "moment/scene.md"),
            run.readPaths
        )
    }

    /**
     * 开着开关时两条链路的请求正文：回复带本轮对话 + 用户备注，主动发只带草稿；
     * 两条链路都**一次知识库都不读**。
     */
    @Test
    fun the_two_chains_share_the_round_scope_but_proactive_keeps_only_the_draft() {
        val proactive = runProactive(onlyThisRound = true)
        val reply = runReply(onlyThisRound = true)
        assertEquals("主动发开着开关时不该碰知识库一次", 0, proactive.touches)
        assertEquals("回复开着开关时不该碰知识库一次", 0, reply.touches)
        assertTrue(proactive.user.contains(draftMarker))
        assertFalse(proactive.user.contains(noteMarker))
        (dialogueMarkers + noteMarker).forEach { marker ->
            assertTrue("回复开着开关时「$marker」必须逐字在场:\n${reply.user}", reply.user.contains(marker))
        }
    }
}
