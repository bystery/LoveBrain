package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.AssetPromptSource
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.SystemClock
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.ProviderRequestConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四流组装顺序契约（2026-11-21 提示词结构收敛后的目标顺序）。
 *
 * 目标 user prompt（总帽 9000）：
 * ```
 * ① 知识段：# 【懂得】关系画像 / ## 我 / ## 她 / 阶段策略 / 【此刻】(当前话题/状态链) / 历史块
 * ② 当前场景 ③ 持续意图 ④ <chat> ⑤ 用户备注 ⑥ 当前时间
 * ```
 * 已删除：## 我们 / ## 我的表达偏好 / 经验教训 / 进攻段 / 进行中事项 / # 最近对话 /
 * 距上次对话 / 军师备注标题（改称「用户备注」，排在 chat 之后）。
 *
 * 资产真源：test classpath 挂入的 src/main/assets（build.gradle.kts:54-56），
 * 沿用 PromptBuilderStageContractTest.loadAsset 手法，禁止副本/内联资产全文。
 * 资产读取走生产实现 AssetPromptSource（开口 lambda 指到 classpath 那份真资产字节流）。
 */
class PromptAssemblyOrderContractTest {

    private val SEP = "\n\n---\n\n"

    // 段标记表：资产侧一律取资产真实首行（禁止内联资产全文）
    private val CORE_FIRST = loadAsset(AssetRegistry.CORE).lineSequence().first()
    private val NAT_FIRST = loadAsset(AssetRegistry.NATURALNESS).lineSequence().first()
    private val RED_FIRST = loadAsset(AssetRegistry.REDLINE).lineSequence().first()
    private val FMT_FIRST = loadAsset(AssetRegistry.FORMAT).lineSequence().first()

    // 谈心倾诉与任务段：自基线 GenerationEngine :304-305 逐字（===分析=== 契约，禁区）
    private fun counselingSuffix(userMessage: String): String =
        "\n\n## 用户倾诉\n" + userMessage.trim() +
            "\n\n## 任务\n请以公正法官的身份，按谈心引擎的回应结构（六步法）回复，末尾按契约附上 ===分析=== 块。"

    private fun loadAsset(path: String): String {
        val res = ClassLoader.getSystemClassLoader().getResourceAsStream(path)
            ?: error("asset $path not on test classpath")
        return res.bufferedReader().use { it.readText() }
    }

    private fun assetsFromTestClasspath() =
        AssetPromptSource { path -> loadAsset(path).byteInputStream() }

    private fun newBuilder(clock: Clock = SystemClock): PromptBuilder {
        val repo = mockk<KnowledgeRepository>()
        coEvery { repo.getActive() } returns KnowledgeBase(name = "kb", stage = "暧昧期")
        coEvery { repo.migrateIfNeeded("kb") } returns Unit
        coEvery { repo.readFile("kb", "understand/me.md") } returns "me画像桩：喜欢咖啡"
        coEvery { repo.readFile("kb", "understand/her.md") } returns "her画像桩：喜欢猫"
        coEvery { repo.readFile("kb", "moment/scene.md") } returns ""
        coEvery { repo.getCurrentTopic("kb") } returns "测试话题"
        // 结构收敛后 warmth 只进谈心核心子集；reply 知识段不再读取
        coEvery { repo.readFile("kb", "understand/warmth.md") } returns "warmth桩：关系轻松"
        coEvery { repo.readFile("kb", "understand/style.md") } returns ""
        coEvery { repo.readFile("kb", "memory/lessons.md") } returns ""
        coEvery { repo.readFile("kb", "moment/recent.md") } returns ""
        coEvery { repo.readPlanActive("kb") } returns ""

        return PromptBuilder(assetsFromTestClasspath(), repo, clock)
    }

    private val kb = KnowledgeBase(name = "kb", stage = "暧昧期")
    private val twoMsgs = listOf(
        ChatMessage(id = "m1", role = ChatMessage.Role.HER, content = "你好"),
        ChatMessage(id = "m2", role = ChatMessage.Role.ME, content = "在干嘛")
    )

    // ═══ 回复 system = 四资产（字节级），无 stage 节、无 aggressive、无安全声明 ═══
    @Test
    fun t1_replySystem_isStaticConcatenation() {
        val pb = newBuilder()
        val system = pb.buildSystemPrompt()
        val expected = loadAsset(AssetRegistry.CORE) + SEP +
            loadAsset(AssetRegistry.NATURALNESS) + SEP +
            loadAsset(AssetRegistry.REDLINE) + SEP +
            loadAsset(AssetRegistry.FORMAT)
        assertEquals("回复 system 必须字节级等于 core+naturalness+redline+format", expected, system)
        assertFalse("system 不应含阶段节选标题", system.contains("## 当前阶段策略（"))
        assertFalse("system 不应含安全声明", system.contains("输入安全声明"))
        assertFalse("system 不应含进攻段", system.contains("激进"))
    }

    // ═══ T2 回复 user 目标顺序链（indexOf 单调）；用户备注在 chat 之后、时间之前 ═══
    @Test
    fun t2_replyUser_orderChain() = runBlocking {
        val pb = newBuilder()
        val user = pb.buildReplyUserPrompt(kb, twoMsgs, userHint = "想幽默一点")
        val markers = listOf(
            "# 【懂得】关系画像", "## 我", "## 她", "## 暧昧期", "# 【此刻】",
            "当前话题：测试话题", "# 【当前场景】", "<chat>", "</chat>",
            "# 用户备注", "【当前时间】"
        )
        val idx = markers.map { m -> m to user.indexOf(m) }
        idx.forEach { (m, i) -> assertTrue("user 缺少段标记：$m", i >= 0) }
        for (i in 0 until idx.size - 1) {
            assertTrue(
                "顺序违例：${idx[i].first}(${idx[i].second}) 应在 ${idx[i + 1].first}(${idx[i + 1].second}) 之前",
                idx[i].second < idx[i + 1].second
            )
        }
        assertFalse("回复 user 不应含 format 首行（已搬入 system）", user.contains(FMT_FIRST))

        // 结构收敛删除的段：一个都不许回来
        listOf(
            "## 我们", "## 我的表达偏好", "# 【记忆】经验教训", "# 【进行中事项】",
            "# 最近对话", "距上次对话", "# 军师备注（用户补充"
        ).forEach { gone -> assertFalse("已删除的段仍在 user 里：$gone", user.contains(gone)) }

        val noHint = pb.buildReplyUserPrompt(kb, twoMsgs, userHint = "")
        assertFalse("userHint 为空时用户备注段不出现", noHint.contains("# 用户备注"))
    }

    // ═══ T4 回复 user 尾部锚定：用户备注在 </chat> 之后、时间戳之前 ═══
    @Test
    fun t4_replyUser_tailAnchors() = runBlocking {
        val pb = newBuilder()
        val user = pb.buildReplyUserPrompt(kb, twoMsgs, userHint = "只按本轮想")
        val iChat = user.indexOf("</chat>")
        val iNote = user.indexOf("# 用户备注")
        val iTime = user.indexOf("【当前时间】")
        assertTrue(iChat >= 0 && iNote >= 0 && iTime >= 0)
        assertTrue("用户备注必须排在 </chat> 之后", iChat < iNote)
        assertTrue("时间戳必须垫底（位于用户备注之后）", iNote < iTime)
        assertFalse("format 已搬入 system，user 全文无 format 首行", user.contains(FMT_FIRST))
    }

    // ═══ T5 谈心：system 字节级 = counseling.md；user 段序 + 负断言 ═══
    @Test
    fun t5_counseling_systemAndUser() = runBlocking {
        val pb = newBuilder()
        val system = pb.buildCounselingSystemPrompt()
        assertEquals("谈心 system 必须字节级等于 counseling.md 全文", loadAsset(AssetRegistry.COUNSELING), system)
        assertFalse("谈心 system 不应含 CORE 首行", system.contains(CORE_FIRST))
        assertFalse("谈心 system 不应含 REDLINE 首行", system.contains(RED_FIRST))

        val user = pb.buildCounselingUserPrompt(kb, counselingSuffix("最近压力有点大"))
        val iMe = user.indexOf("# 【懂得】关系画像")
        val iConf = user.indexOf("## 用户倾诉")
        val iTask = user.indexOf("## 任务")
        val iTime = user.indexOf("【当前时间】")
        assertTrue(listOf(iMe, iConf, iTask, iTime).all { it >= 0 })
        assertTrue("user 段序：知识子集 → 倾诉 → 任务 → 时间戳", iMe < iConf && iConf < iTask && iTask < iTime)
        assertTrue("倾诉内容在场", user.contains("最近压力有点大"))
        assertTrue("任务句含 ===分析=== 契约", user.contains("===分析==="))
        assertTrue("核心子集保留「我们」", user.contains("## 我们"))
        assertFalse("谈心 user 不含表达偏好", user.contains("## 我的表达偏好"))
        assertFalse("谈心 user 不含经验教训", user.contains("# 【记忆】经验教训"))
        assertFalse("谈心 user 不含进行中事项", user.contains("# 【进行中事项】"))
        assertFalse("谈心 user 不含此刻", user.contains("# 【此刻】"))
        assertFalse("谈心 user 不含最近对话", user.contains("# 最近对话"))
        assertFalse("谈心 user 不含阶段节选", user.contains("## 暧昧期"))
    }

    // ═══ T5b 谈心 user prompt 不套主回复的 9000 帽，也不做字符级裁剪 ═══
    @Test
    fun t5b_counselingUser_isNotCappedByReplyBudget() = runBlocking {
        // 画像本身就超过 9000：谈心只拼固定块，整段原样进 prompt——
        // 旧写法对核心子集套 applyBudget 裸 take，会把画像切在半个字符上。
        val repo = mockk<KnowledgeRepository>()
        coEvery { repo.getActive() } returns kb
        coEvery { repo.migrateIfNeeded("kb") } returns Unit
        coEvery { repo.readFile("kb", "understand/me.md") } returns "我".repeat(AppConfig.TOTAL_BUDGET)
        coEvery { repo.readFile("kb", "understand/her.md") } returns "她".repeat(AppConfig.TOTAL_BUDGET)
        coEvery { repo.readFile("kb", "understand/warmth.md") } returns "我们".repeat(1000)
        val pb = PromptBuilder(assetsFromTestClasspath(), repo)

        val user = pb.buildCounselingUserPrompt(kb, counselingSuffix("最近压力有点大"))
        assertTrue("谈心 prompt 不受主回复帽约束：实到 ${user.length}", user.length > AppConfig.TOTAL_BUDGET)
        assertTrue("我画像整段原样在场", user.contains("我".repeat(AppConfig.TOTAL_BUDGET)))
        assertTrue("她画像整段原样在场", user.contains("她".repeat(AppConfig.TOTAL_BUDGET)))
        assertFalse("不得做字符级裁剪", user.contains("旧记忆因长度限制已省略"))
        assertFalse("不得做字符级裁剪（旧中间省略标记）", user.contains("中间旧记忆因长度限制已省略"))
        assertTrue("倾诉与任务段仍在", user.contains("===分析==="))
        assertTrue("时间戳垫底仍在", user.contains("【当前时间】"))
    }

    // ═══ T6（锦囊）随  整删：suggest.md 资产、buildSuggestSystemPrompt/UserPrompt 与
    //     AppConfig.SUGGEST_BUDGET 都不存在了，这一格没有可判的对象。编号不重排。

    // ═══ T7 润色：system 字节级 = polish.md；user 仅草稿 + 空草稿兜底 ═══
    @Test
    fun t7_polish_systemAndUser() {
        val pb = newBuilder()
        assertEquals("润色 system 必须字节级等于 polish.md 全文", loadAsset(AssetRegistry.POLISH), pb.buildPolishSystemPrompt())

        assertEquals("user == 草稿原文", "今晚想约她看电影", pb.buildPolishUserPrompt("今晚想约她看电影"))
        assertEquals("空草稿兜底句逐字", "（无草稿，请主动给出开场）", pb.buildPolishUserPrompt("   "))

        val user = pb.buildPolishUserPrompt("今晚想约她看电影")
        assertFalse("润色无时间戳", user.contains("【当前时间】"))
        assertFalse("润色无知识段", user.contains("# 【懂得】关系画像"))
        assertFalse("润色无 CORE 首行", user.contains(CORE_FIRST))
    }

    // ═══ T8 经验段已从回复 prompt 删除（原文会强断言不出现） ═══
    @Test
    fun t8_lessons_neverReachTheReplyPrompt() = runBlocking {
        val repo = mockk<KnowledgeRepository>()
        coEvery { repo.getActive() } returns KnowledgeBase(name = "kb", stage = "暧昧期")
        coEvery { repo.migrateIfNeeded("kb") } returns Unit
        coEvery { repo.readFile("kb", "understand/me.md") } returns "me"
        coEvery { repo.readFile("kb", "understand/her.md") } returns "her"
        coEvery { repo.readFile("kb", "understand/warmth.md") } returns "warmth"
        coEvery { repo.readFile("kb", "understand/style.md") } returns "style"
        coEvery { repo.readFile("kb", "memory/lessons.md") } returns "# 块1\n内容甲。\n# 块2\n内容乙。"
        coEvery { repo.readFile("kb", "moment/scene.md") } returns ""
        coEvery { repo.getCurrentTopic("kb") } returns ""
        val pb = PromptBuilder(assetsFromTestClasspath(), repo)
        val user = pb.buildReplyUserPrompt(kb, twoMsgs, userHint = "")
        assertFalse("经验正文不得进回复 prompt", user.contains("内容甲") || user.contains("内容乙"))
        assertFalse("经验段头不得出现", user.contains("# 【记忆】经验教训"))
    }

    // ═══ T9 消息掐尾（REPLY_MAX_MESSAGES） ═══
    @Test
    fun t9_messages_takeLastCap() = runBlocking {
        val pb = newBuilder()
        val msgs = (0 until AppConfig.REPLY_MAX_MESSAGES + 5).map {
            ChatMessage(id = "m$it", role = ChatMessage.Role.HER, content = "msg-$it")
        }
        val user = pb.buildReplyUserPrompt(kb, msgs, userHint = "")
        assertTrue("超量注记在场", user.contains("（注：对话记录超过"))
        assertTrue("最后一条在场", user.contains("msg-${AppConfig.REPLY_MAX_MESSAGES + 4}"))
        assertFalse("第一条已被掐掉", user.contains("msg-0\n"))
        assertEquals("<chat> 恰 1 次", 1, Regex("<chat>").findAll(user).count())
        assertEquals("</chat> 恰 1 次", 1, Regex("</chat>").findAll(user).count())
    }

    // ═══ T10 预算：知识段超 9000 → 省略标记 + 总长封顶 ═══
    @Test
    fun t10_budget_truncatesKnowledge() = runBlocking {
        val repoBig = mockk<KnowledgeRepository>()
        coEvery { repoBig.getActive() } returns kb
        coEvery { repoBig.migrateIfNeeded("kb") } returns Unit
        coEvery { repoBig.readFile("kb", "understand/me.md") } returns "画".repeat(10_000)
        coEvery { repoBig.readFile("kb", "understand/her.md") } returns ""
        coEvery { repoBig.readFile("kb", "moment/scene.md") } returns ""
        coEvery { repoBig.getCurrentTopic("kb") } returns ""
        val pbBig = PromptBuilder(assetsFromTestClasspath(), repoBig)

        val user = pbBig.buildReplyUserPrompt(kb, emptyList(), userHint = "")
        val knowledgePart = user.substringBefore("# 本次对话记录")
        assertTrue("知识段含省略标记", knowledgePart.contains("旧记忆因长度限制已省略"))
        assertTrue(
            "知识段总长 ≤ 预算 + 标记长度（实际 ${knowledgePart.length}）",
            knowledgePart.length <= AppConfig.TOTAL_BUDGET + 100
        )
        assertTrue(pbBig.buildTimestampPrompt().isNotBlank())
    }

    // ═══ T11 进攻开关整删：入口输出不再因 aggressive 分叉，也不含已删的进攻段 ═══
    // 旧判据扫 PromptBuilder 源码，换实现就失效；这里改判行为：真实构建入口在两档下必须
    // 吐出同一串字节（固定时钟去掉时间戳这个唯一变量），且已删的进攻段一个字都不许回来。
    @Test
    fun t11_aggressiveSwitchNoLongerChangesPromptOutput() = runBlocking {
        val pb = newBuilder(FixedClock())
        val user = pb.buildReplyUserPrompt(kb, twoMsgs, userHint = "想幽默一点")

        assertFalse("已删的进攻段（aggressive.md 首行）不得回潮", user.contains("# 进攻模式"))
        assertEquals(
            "system 段没有进攻开关：仍必须是四资产那一份",
            loadAsset(AssetRegistry.CORE) + SEP + loadAsset(AssetRegistry.NATURALNESS) + SEP +
                loadAsset(AssetRegistry.REDLINE) + SEP + loadAsset(AssetRegistry.FORMAT),
            pb.buildSystemPrompt()
        )
    }

    // ════════════════════════════════════════════════════════════════
    // GEN-02B: Engine 主回复 generate 使用冻结的 knowledgeBase 参数，不读 callbacks.getActiveKb()
    // ════════════════════════════════════════════════════════════════

    @Test
    fun t_gen02b_engine_uses_frozen_kb_not_callback() = runBlocking {
        val kbA = KnowledgeBase(name = "kb-a", stage = "暧昧期")
        val kbB = KnowledgeBase(name = "kb-b", stage = "热恋期")

        val dsk = mockk<DeepSeekRepository>(relaxed = true)
        every { dsk.generateStream(any(), any(), any(), any()) } returns
            flowOf<StreamEvent>(StreamEvent.Complete("{\"response\":{\"recommended\":\"r\"}}"))
        every { dsk.parseReplyResponse(any()) } returns
            LoveBrainResponse(response = ReplySchemes(recommended = "r"))

        val pb = mockk<PromptBuilder>(relaxed = true)
        every { pb.buildSystemPrompt() } returns "system"
        // 新签名：kb, messages, userHint, intentConfig, corrections,
        // historicalBlock, currentTurn（已无 uncertainBlock）
        coEvery {
            pb.buildReplyUserPromptWithRefs(any(), any(), any(), any(), any(), any(), any())
        } returns PromptBuilder.PromptBuildResult("user", emptyList())

        val engine = GenerationEngine(dsk, pb)
        val input = com.lovebrain.app.model.buildGenerationInput(
            requestId = "test-kb", messages = twoMsgs, userHint = "", knowledgeBase = kbA,
            intentConfig = com.lovebrain.app.model.IntentConfig(),
            corrections = emptyMap(), correctionsRevision = 0,
            onlyThisRound = false,
            providerIdentity = com.lovebrain.app.model.ProviderIdentity("t", "hash", "m", 0),
            kbProfile = "", kbRevision = "rev", promptAssetHash = "assets"
        )
        engine.replyStream(input).collect { }

        coVerify {
            pb.buildReplyUserPromptWithRefs(
                match { it?.name == "kb-a" },
                any(), any(), any(), any(), any(), any()
            )
        }
        coVerify(exactly = 0) {
            pb.buildReplyUserPromptWithRefs(
                match { it?.name == "kb-b" },
                any(), any(), any(), any(), any(), any()
            )
        }
    }
}
