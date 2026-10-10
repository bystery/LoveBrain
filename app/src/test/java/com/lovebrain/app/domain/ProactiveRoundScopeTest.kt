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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 第10节第4条 / ：**「仅看本轮」这颗开关主动发也真认**——判据是**截到手的请求正文**，
 * 不是"源码里出现了分支"。
 *
 * 用户原话问的是"仅看本轮是真的仅看本轮吗"。 第1节 的  点名的缺口是：
 * 回复分支读这颗布尔（`GenerationEngine.replyStream` 走
 * `buildReplyUserPromptOnlyThisRound`），而 `generateProactive` / `proactiveStream`
 * 一个都不读——于是总开关画在公共输入区，主动发却悄悄继续吃 KB 画像与近期历史。
 * 只把开关画成 checked 不算完成，所以这一族的证据全部来自 [CapturingGateway]
 * 记下的**真正交给 provider 的那串 user 正文**。
 *
 * 判法是**两组独特标记 + 双向夹逼**（缺任何一侧都不算测到）：
 * - 旧记忆那一组 `KBME- / KBHER- / KBWARMTH- / KBSTYLE- / KBLESSON- / KBSCENE- /
 *   KBRECENT- / KBPLAN- / KBTOPIC-` 只可能从知识库来，`KBINTENT-` 只可能从冻结的持续意图来。
 * - 本轮那一组 `ROUNDHER- / ROUNDME- / ROUNDNOTE- / ROUNDDRAFT-` 只可能来自
 *   当前对话列表与本轮草稿/军师备注。
 * - **开关开着**：两条链路（主动发 [GenerationEngine.proactiveStream] 与回复
 *   [GenerationEngine.replyStream]）截到的正文里，本轮组一条都不许少（备注与草稿必须逐字在场），
 *   旧记忆组与持续意图一条都不许多。再补一把硬尺——[CountingKnowledgePort] 数到的
 *   **知识库读取次数必须是 0**："排除"不是读了再藏起来，而是整条读取都没发生。
 *   回复那一轮交回的 `ReplyMemoryRefs` 必须是空清单。
 * - **开关关掉**：旧记忆组必须**真的进得去**（否则上面那串"不含"可能只是假库是空的造成的假绿），
 *   引用清单必须非空。这一侧同时钉住"关闭时沿用原有上下文"那条合同：
 *   关闭分支的字节没为这一档改道（另见 `PromptByteFreezeBaselineTest` 里 proactive 组那三行）。
 *
 * 夹具用**真 [PromptBuilder] + 真 [GenerationEngine]**，只换掉三处边界：
 * 资产读取（classpath 上同一份 markdown，不复制不内联）、知识库（带计数的内存库）、
 * 网络出口（假 provider，只登记收到的正文）。
 */
class ProactiveRoundScopeTest {

    // ═══════════════════════ 两组独特标记 ═══════════════════════

    /** 负标记：只可能来自知识库 / 持续意图，开启后一条都不许出现在请求正文里 */
    private val oldMemoryMarkers = listOf(
        "KBME-画像我", "KBHER-画像她", "KBWARMTH-我们温度", "KBSTYLE-表达偏好",
        "KBLESSON-旧经验", "KBSCENE-旧场景链", "KBRECENT-近期历史", "KBPLAN-旧事项",
        "KBTOPIC-旧话题", "KBINTENT-持续意图"
    )

    /**
     * 正标记：只可能来自本轮。
     *
     * ⚠ 两档/两链要求的不是同一份清单，读数实测过（见 `report` 打的行）：
     * - 回复链路本来就没有"草稿"这个输入（它拿的是本轮备注），所以 ROUNDDRAFT 只归主动发；
     * - 主动发**关闭**那一档沿用原有上下文（草稿 + 画像 + 近期历史），本轮对话与备注本来就不在
     *   旧行为里——第10节第4条 只要求"开启时传当前对话＋当前主动草稿/备注"，关闭侧要的是
     *   "沿用正常上下文"，所以这里不拿开启侧的清单去要求关闭侧。
     */
    private val dialogueMarkers = listOf("ROUNDHER-她说她加班到十点", "ROUNDME-我想回这句")
    private val noteMarker = "ROUNDNOTE-本轮备注别再问忙不忙"
    private val draftMarker = "ROUNDDRAFT-本轮草稿想约她看展"

    /** 开启那一档：两条链路都必须逐字在场的东西（本轮真实对话 + 本轮备注） */
    private val requiredWhenOn = dialogueMarkers + listOf(noteMarker)

    /** 开启那一档写进请求头部的边界声明——同时证明走的是本轮专用装配 */
    private val roundScopeHeader = "（本轮仅看模式"

    /** 旧记忆段落标题：段名比标记更容易被改写，两把尺一起量 */
    private val oldMemoryHeaders = listOf(
        "## 对方画像", "## 近期对话", "# 【懂得】关系画像", "# 【记忆】经验教训",
        "# 【此刻】场景上下文", "# 【进行中事项】", "【持续意图】"
    )

    private val kb = KnowledgeBase(
        name = "kb", displayName = "核查库", stage = "暧昧期", turnCount = 3, active = true
    )

    private val roundDialogue = listOf(
        ChatMessage(id = "h1", role = ChatMessage.Role.HER, content = "ROUNDHER-她说她加班到十点"),
        ChatMessage(id = "m1", role = ChatMessage.Role.ME, content = "ROUNDME-我想回这句")
    )

    private val note = "ROUNDNOTE-本轮备注别再问忙不忙"
    private val draft = "ROUNDDRAFT-本轮草稿想约她看展"

    /** 启用中的持续意图：它属于要排除的那一半，开启后必须整块消失 */
    private val liveIntent = IntentConfig(text = "KBINTENT-持续意图：先把见面约下来", enabled = true)

    // ═══════════════════════ 假边界 ═══════════════════════

    /**
     * 带接触计数的知识库端口。
     *
     * 计数这把尺是给"排除"定性用的——开启时读取次数不是 0，就只能说明"读了又没写进正文"，
     * 那不是本轮语义（画像、阶段、历史、经验、旧事项、表达偏好一个都不该被读）。
     */
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
        override suspend fun getTopicAgeHours(kbName: String): Int {
            see("getTopicAgeHours"); return inner.getTopicAgeHours(kbName)
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

    /**
     * 假 provider：**把真正交上来的 system/user 两串正文原样存下来**。
     * 这一族读的就是这几个字符串，不读任何实现自己算出来的中间值。
     */
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

    /** 资产真源：test classpath 上那**同一份** markdown，禁止副本/内联（沿用冻结表手法） */
    private fun loadAsset(path: String): String {
        val res = ClassLoader.getSystemClassLoader().getResourceAsStream(path)
            ?: error("资产 $path 不在 test classpath 上（build.gradle.kts 把 src/main/assets 挂进来了）")
        return res.bufferedReader().use { it.readText() }
    }

    private val ctx: PromptSourcePort by lazy {
        // 端口化后同一形状：生产实现 AssetPromptSource + classpath 那份真资产（读到的字节与
        // 端口化前 mockk 的 Context.assets 完全一致）
        AssetPromptSource { path -> loadAsset(path).byteInputStream() }
    }

    /** 一次运行：新夹具、新库、新 provider——两档之间不共享任何状态 */
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
        val builder = PromptBuilder(ctx, port, OngoingContextSelector(port, clock), clock)
        val engine = GenerationEngine(gateway, builder)
        val events = runBlocking {
            engine.proactiveStream(
                requestId = "req-proactive",
                draft = draft,
                knowledgeBase = kb,
                messages = roundDialogue,
                onlyThisRound = onlyThisRound,
                advisorNote = note
            ).toList()
        }
        assertEquals("一次主动发只交给 provider 一条请求（ 不做两次调用）", 1, gateway.users.size)
        assertTrue("主动发必须真的出 options：$events", events.any { it is com.lovebrain.app.model.ProactiveOptions })
        return Run(gateway.users.last(), gateway.systems.last(), port.touches, emptyList(), port.readPaths)
    }

    private fun runReply(onlyThisRound: Boolean): Run {
        val (port, gateway) = newFixture()
        val clock = FixedClock()
        val builder = PromptBuilder(ctx, port, OngoingContextSelector(port, clock), clock)
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
            aggressive = false,
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

    /** 把截到的正文打成读数行——绿了不打数等于没测（同一口径见冻结表那族） */
    private fun report(chain: String, switchOn: Boolean, run: Run) {
        val leaked = oldMemoryMarkers.filter { run.user.contains(it) }
        val missing = requiredWhenOn.filterNot { run.user.contains(it) }
        println(
            "ROUNDSCOPE|$chain|switch=${if (switchOn) "ON" else "OFF"}|" +
                "kbTouches=${run.touches}|userBytes=${run.user.toByteArray(Charsets.UTF_8).size}|" +
                "本轮边界声明=${run.user.contains(roundScopeHeader)}|refs=${run.refs.size}|" +
                "旧标记漏进=${if (leaked.isEmpty()) "无" else leaked.joinToString(",")}|" +
                "本轮必带的缺=${if (missing.isEmpty()) "无" else missing.joinToString(",")}|" +
                "读过的库=${run.readPaths.joinToString(",")}"
        )
    }

    // ═══════════════════════ 主动发： 那格 ═══════════════════════

    /**
     * 开关**开着**：主动发截到的正文只有本轮那几件事（草稿、备注、真实对话、本轮推断的场景、
     * 当前时间）——旧记忆标记一条都不许多，而且**一次知识库读取都没发生**。
     */
    @Test
    fun proactive_with_switch_on_hands_only_this_round_to_the_provider() {
        val run = runProactive(onlyThisRound = true)
        report("proactive", true, run)

        (requiredWhenOn + draftMarker).forEach { marker ->
            assertTrue("开启时本轮标记「$marker」必须在主动发请求正文里（草稿/备注/真实对话一个都不许掉）:\n${run.user}", run.user.contains(marker))
        }
        assertTrue("开启时要写明这一轮的范围边界:\n${run.user}", run.user.contains(roundScopeHeader))
        assertTrue(
            "开启时备注区块必须用那一颗真源标题",
            run.user.contains(com.lovebrain.app.domain.prompt.IntentIdeaBlock.NOTE_BLOCK_HEADER)
        )
        oldMemoryMarkers.forEach { marker ->
            assertTrue("开启时旧记忆标记「$marker」混进了主动发请求正文:\n${run.user}", !run.user.contains(marker))
        }
        oldMemoryHeaders.forEach { header ->
            assertTrue("开启时旧记忆段落「$header」整块都不该在主动发请求里:\n${run.user}", !run.user.contains(header))
        }
        assertEquals("开启时主动发对知识库的读取次数必须是 0，实到 ${run.touches} 次：${run.readPaths}", 0, run.touches)
    }

    /**
     * 开关**关掉**（普通分支）：原有相关记忆照旧进请求，并且原始第 18 条补齐后
     * **本轮真实输入**（备注 / 本轮对话）与**表达偏好**也真的进得去——以前这两个都不在旧行为里。
     * 这一格是上面那格的反向证人：没有它，"不含 KB 标记"可能只是因为假库本来就空。
     */
    @Test
    fun proactive_with_switch_off_still_hands_the_old_context() {
        val run = runProactive(onlyThisRound = false)
        report("proactive", false, run)

        assertTrue("关闭时对方画像标记必须进得去", run.user.contains("KBHER-画像她"))
        assertTrue("关闭时近期历史标记必须进得去", run.user.contains("KBRECENT-近期历史"))
        // 原始第 18 条：表达偏好此前只在资产里声明、从不接入；现在从 understand/style.md 真读真拼
        assertTrue("关闭时表达偏好标记必须进得去（不再是假声明）", run.user.contains("KBSTYLE-表达偏好"))
        // 原始第 18 条：普通分支以前完全不引用 messages / advisorNote，现在本轮输入必须到位
        assertTrue("关闭时用户草稿仍是本轮输入", run.user.contains(draftMarker))
        assertTrue("关闭时本轮军师备注必须进得去", run.user.contains(noteMarker))
        dialogueMarkers.forEach { marker ->
            assertTrue("关闭时本轮真实对话「$marker」必须进得去:\n${run.user}", run.user.contains(marker))
        }
        assertTrue("关闭时不该出现本轮专用边界声明", !run.user.contains(roundScopeHeader))
        // 白名单随新读取策略显式改写：普通分支多出表达偏好这一处 KB 读（本轮输入不走读口，不在此列）
        assertEquals(
            "关闭时读的就是画像 + 近期历史 + 表达偏好这三处库文件",
            listOf("understand/her.md", "moment/recent.md", "understand/style.md"),
            run.readPaths
        )
    }

    // ═══════════════════════ 回复：同一条判据覆盖另一条链路 ═══════════════════════

    /** 开关开着：回复链路的正文同样不许有旧记忆，持续意图整块消失，引用清单为空。 */
    @Test
    fun reply_with_switch_on_hands_no_old_memory_and_no_refs() {
        val run = runReply(onlyThisRound = true)
        report("reply", true, run)

        requiredWhenOn.forEach { marker ->
            assertTrue("开启时本轮标记「$marker」必须在回复请求正文里", run.user.contains(marker))
        }
        oldMemoryMarkers.forEach { marker ->
            assertTrue("开启时旧记忆标记「$marker」混进了回复请求正文:\n${run.user}", !run.user.contains(marker))
        }
        oldMemoryHeaders.forEach { header ->
            assertTrue("开启时旧记忆段落「$header」整块都不该在回复请求里", !run.user.contains(header))
        }
        assertEquals("开启时回复链路对知识库的读取次数必须是 0，实到 ${run.touches} 次", 0, run.touches)
        assertTrue("开启时交回的记忆引用必须是空清单（非空即假引用），实到 ${run.refs}", run.refs.isEmpty())
    }

    /** 开关关掉：画像/经验/偏好/旧话题/持续意图按原业务进入，且真交回引用清单。 */
    @Test
    fun reply_with_switch_off_keeps_old_context_and_real_refs() {
        val run = runReply(onlyThisRound = false)
        report("reply", false, run)

        listOf(
            "KBHER-画像她", "KBME-画像我", "KBWARMTH-我们温度", "KBSTYLE-表达偏好",
            "KBLESSON-旧经验", "KBRECENT-近期历史", "KBTOPIC-旧话题", "KBINTENT-持续意图"
        ).forEach { marker ->
            assertTrue("关闭时「$marker」应按原业务进请求:\n${run.user}", run.user.contains(marker))
        }
        requiredWhenOn.forEach { marker ->
            assertTrue("关闭时本轮那几件照样在场（开关切的是旧上下文那一半）:\n$marker", run.user.contains(marker))
        }
        assertTrue("关闭时不该出现本轮专用边界声明", !run.user.contains(roundScopeHeader))
        assertTrue("关闭时必须真交回引用清单，否则上面那些「不含」都是假绿", run.refs.isNotEmpty())
    }

    /**
     * 开着开关时，**两条链路的请求正文都只带着本轮的备注与真实对话出去**——
     * 主动发那一格以前一个都不在（），现在与回复逐字同判。
     */
    @Test
    fun the_note_and_this_round_dialogue_reach_both_chains_when_the_switch_is_on() {
        val proactive = runProactive(onlyThisRound = true)
        val reply = runReply(onlyThisRound = true)
        listOf("proactive" to proactive, "reply" to reply).forEach { (chain, run) ->
            requiredWhenOn.forEach { marker ->
                assertTrue("$chain 开着开关时「$marker」必须逐字在场:\n${run.user}", run.user.contains(marker))
            }
            assertEquals("$chain 开着开关时不该碰知识库一次", 0, run.touches)
        }
    }
}
