package com.lovebrain.app.domain

import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.domain.port.LessonsRewriteResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.RawGenerationResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **整理接进生产写盘链**的证据（ 第8节第2条「对已有文件做一次可重入的小范围整理」、
 * 第8节第3条「计数、分配下一个编号、检查 revision 和追加在同一个写事务里完成」）。
 *
 * 这一族盯的是**接线**，不是判据——判据那 18 格在 [LessonDocTidyTest]，落盘三条边界在
 * [com.lovebrain.app.data.KnowledgeRepositoryLessonsRewriteTest]。它回答的是一个具体的用户问题：
 * 他那份编号长成 `1、1、3、1、5、1` 的经验文件，**下一次经验提取之后到底会不会被整理**。
 *   之前答案是"不会"（`LessonDoc.tidy` 只有测试在调，写链走的是锁外 readFile + 盲追加）。
 *
 * 这一族用的是 [InMemoryKnowledgePort]——它必须与生产适配器遵守同一份
 * [com.lovebrain.app.domain.port.KnowledgePortContract]，所以这里量到的是**真事务语义**
 * （revision 检查、写前快照、逐字相同就不写），不是一个只会点头的 mock。
 *
 * 每格都写了"什么坏实现会让它红"。
 */
class LessonDocTidyProductionTest {

    /** 用户实测到的那一份：两对成对节头、一条只有标题的空批、编号已经跳到 5 */
    private val messyUserFile = """
        # 经验库

        军师自动追加提取节，节头格式：# [yyyy-MM-dd HH:mm] 第N次提取（模板内不放示例节）

        # [2026-09-17 10:00] 第1次提取

        # [2026-09-17 10:00] 第1次提取

        ## 踩过的坑
        - 踩坑：她把「格式」两字拿来开玩笑时我认真解释了；教训：跟着松；补救：补一句"你那是逗我"

        # [2026-09-17 11:20] 第3次提取

        # [2026-09-17 ]第3次提取

        ## 测试接法
        - 测试：连续抛"会不会找我"式试探；接法：高框架弹回；内核：确认你不会离开

        # [2026-09-18 09:05] 第5次提取
    """.trimIndent()

    /** 模型这一批的正常输出：两条它自己仿的一级标题 + 一条真实经验（第8节第3条 要剥掉的就是前者） */
    private val modelBatch = """
        # [2026-09-19 12:00] 第1次提取

        ## 加分项
        - 触发：她主动报备行程；放大：接住并夸具体那步；复用：她报备任何事时

        # [2026-09-19 12:00] 第1次提取
    """.trimIndent()

    /** 五个话题归档条目 → `getLessonCount` = 5：命中经验那一档（5 % 3 != 0 所以向量那路不跑） */
    private val fiveTopics = (1..5).joinToString("\n") { "## [2026-09-1$it] 话题$it" }

    private class Running(
        val port: InMemoryKnowledgePort,
        val emitted: List<KnowledgeTriggerEvent>
    ) {
        val lessons: String get() = port.dump()["kb/memory/lessons.md"].orEmpty()
        val snapshot: String? get() = port.dump()["kb/memory/.lessons.pre-tidy.md"]
        fun saidSuccess() = emitted.any { it is KnowledgeTriggerEvent.Notice && it.message == "已记入经验" }
    }

    /**
     * 跑一轮完整的后台触发（话题数 5 → 只跑经验与画像）。
     * 画像那一路被钉成空返回，免得它的失败事件混进经验这一族的读数里。
     */
    private fun runOnce(port: InMemoryKnowledgePort, modelRaw: String?): Running {
        val gateway = mockk<AiGateway>(relaxed = true)
        coEvery { gateway.generateRaw(any(), any()) } returns modelRaw.orEmpty()
        coEvery {
            gateway.generateRawWithMetadata(any(), any())
        } returns RawGenerationResult(content = "", finishReason = "stop", error = null)
        val topicRecorder = mockk<TopicRecorder>(relaxed = true)
        coEvery { topicRecorder.getTopicFullContext(any(), any()) } returns "话题上下文"

        val coordinator = KnowledgeTriggerCoordinator(
            port, gateway, mockk<PromptBuilder>(relaxed = true), topicRecorder
        )
        val emitted = mutableListOf<KnowledgeTriggerEvent>()
        runBlocking { coordinator.triggerEvents("kb").collect { emitted += it } }
        return Running(port, emitted)
    }

    private fun portWith(lessons: String): InMemoryKnowledgePort = InMemoryKnowledgePort().apply {
        seedLibrary(KnowledgeBase(name = "kb", displayName = "kb"))
        seed("kb", "memory/raw_topic.md", fiveTopics)
        seed("kb", LessonDoc.LESSONS_PATH, lessons)
    }

    // ═══════════ 1. 用户那份脏档：下一次提取真的会被整理 ═══════════

    /**
     * 提取一次之后：编号收敛成连续的 1、2、3、4，模板序言消失，真实正文一句不少，
     * 而**整理前的原件逐字躺在快照里**，并且发了「已记入经验」。
     *
     * 坏实现与反例：
     * - 写链还是"锁外 readFile + 盲追加"（tidy 没接进来）→ 文件里依旧 1、1、3、1、5、1，
     *   第一句 `assertEquals(listOf(1,2,3,4), numbers)` 当场红；
     * - 接了 tidy 但没快照 → `snapshot` 为 null，第二句红；
     * - 整理顺手删了"只有标题的那批" → 编号只剩 1、2、3、4 里的三个 + 快照那句对不上。
     */
    @Test
    fun theUsersMessyLessonFileIsTidiedByTheProductionWriteChain() {
        val port = portWith(messyUserFile)

        val run = runOnce(port, modelBatch)

        val numbers = Regex("""第(\d+)次提取""").findAll(run.lessons).map { it.groupValues[1].toInt() }.toList()
        assertEquals(
            "三个有效批（成对节头算一批、只有标题的那条也算一批）+ 这一批 = 连续的 1..4",
            listOf(1, 2, 3, 4), numbers
        )
        assertEquals("整理过就只剩每个批一条节头", 4, LessonDoc.batchCount(run.lessons))
        assertFalse("模板序言不许再留在用户文件里", run.lessons.contains("军师自动追加提取节"))
        assertFalse("文档标题壳也一样", run.lessons.contains("# 经验库"))
        listOf(
            "- 踩坑：她把「格式」两字拿来开玩笑时我认真解释了；教训：跟着松；补救：补一句\"你那是逗我\"",
            "- 测试：连续抛\"会不会找我\"式试探；接法：高框架弹回；内核：确认你不会离开",
            "- 触发：她主动报备行程；放大：接住并夸具体那步；复用：她报备任何事时"
        ).forEach { assertTrue("真实正文一句都不许少：$it", run.lessons.contains(it)) }
        assertFalse("模型抄的那两条假节头不许进文件", run.lessons.contains("12:00"))

        assertEquals(
            "写前必须有一份**整理前的原件**，逐字一字不差（「备份原文件」）",
            messyUserFile, run.snapshot
        )
        assertTrue("落盘成功才发提示", run.saidSuccess())
    }

    // ═══════════ 2. 可重入：第二次提取不许把第一次的整理结果当"原件"盖掉 ═══════════

    /**
     * 连着提取两次：第二次的快照必须是**第一次落盘的那一篇**（已整理过的），
     * 编号继续 1..5 而不是从头再来或撞号。
     *
     * 坏实现：
     * - 每次提取都重新按"一级标题数量"发号 → 第二次会写出两条同号；
     * - 第二趟不快照、或拿最早那份脏档当快照 → `snapshot` 不等于 firstLessons，红；
     * - 整理没被接进来 → 编号里还带着 5、3 那种历史跳号。
     */
    @Test
    fun aSecondExtractionContinuesTheNumberingAndSnapshotsTheAlreadyTidiedFile() {
        val port = portWith(messyUserFile)

        val first = runOnce(port, modelBatch)
        val firstLessons = first.lessons

        val second = runOnce(port, modelBatch)

        val numbers = Regex("""第(\d+)次提取""").findAll(second.lessons).map { it.groupValues[1].toInt() }.toList()
        assertEquals("第二次进来只能多一条、且编号连续到 5", listOf(1, 2, 3, 4, 5), numbers)
        assertEquals(
            "第二趟的快照必须是**第一趟落盘的那一篇**（说明第一趟的整理结果被当原件保住了）",
            firstLessons, second.snapshot
        )
        assertTrue("两趟都算成功", first.saidSuccess() && second.saidSuccess())
    }

    // ═══════════ 3. 「无新经验」不新增批、不增次数、不动快照 ═══════════

    /**
     * 模型交白卷：文件必须**一个字节都没动**——连整理都不许顺带做一次。
     *
     * 反例：把守卫从 `raw.isBlank() || isNoNewLessons(raw)` 拆掉，或在没东西可记时仍然跑一次事务
     * → 编号会多出第 4 次（甚至涨到 4 条节头）、快照会被写出来，两句都红。
     */
    @Test
    fun nothingNewAddsNoBatchNoNumberAndNoSnapshot() {
        val port = portWith(messyUserFile)

        val run = runOnce(port, "无新经验")

        assertEquals("没新东西就一个字都不该动", messyUserFile, run.lessons)
        assertFalse("没写盘就不该有快照", run.snapshot != null)
        assertFalse("更不能报成功", run.saidSuccess())
    }

    // ═══════════ 4. revision 在提取期间变了：整段事务收手，不烧号 ═══════════

    /**
     * 冻结的是 0，模型返回之后有人递增了纠正号 ⇒ 这一次事务必须整段收手：
     * 文件逐字不变、没有快照、不发成功提示。
     *
     * 坏实现（先读正文先发号、最后才比 revision，或比不过也照样写）会在这里红：
     * 号被分配了却没落盘，正是 第8节第3条 明令禁止的"分配编号后丢内容"。
     */
    @Test
    fun aCorrectionsRevisionChangeDuringExtractionRefusesTheWholeTransaction() {
        val port = portWith(messyUserFile)
        val gateway = mockk<AiGateway>(relaxed = true)
        coEvery { gateway.generateRaw(any(), any()) } coAnswers {
            // 提取期间用户点了一次踩（纠正号 0 → 9）：这就是"先读次数、另处写入"要挡的那种交错
            port.seedRevision("kb", 9)
            modelBatch
        }
        coEvery { gateway.generateRawWithMetadata(any(), any()) } returns
            RawGenerationResult(content = "", finishReason = "stop", error = null)
        val topicRecorder = mockk<TopicRecorder>(relaxed = true)
        coEvery { topicRecorder.getTopicFullContext(any(), any()) } returns "话题上下文"

        val emitted = mutableListOf<KnowledgeTriggerEvent>()
        runBlocking {
            KnowledgeTriggerCoordinator(port, gateway, mockk<PromptBuilder>(relaxed = true), topicRecorder)
                .triggerEvents("kb").collect { emitted += it }
        }

        val lessons = port.dump()["kb/memory/lessons.md"].orEmpty()
        assertEquals("正文一个字都不许动", messyUserFile, lessons)
        assertNull("没写盘就不该留快照", port.dump()["kb/memory/.lessons.pre-tidy.md"])
        assertFalse(
            "revision 变了就不许报成功",
            emitted.any { it is KnowledgeTriggerEvent.Notice && it.message == "已记入经验" }
        )
    }

    // ═══════════ 5. 写失败绝不报成功，而且正文交回调用侧 ═══════════

    /**
     * 端口如实回 [LessonsRewriteResult.WriteFailed]（替换没落成）：协调器**不许**发「已记入经验」，
     * 也不许把那一篇咽掉——交回的全文就是"分配了编号的那一批"，正文不能凭空消失。
     *
     * 这一格就是"写失败仍报成功"那个坏实现的反例：把 `when (outcome)` 里
     * `is LessonsRewriteResult.Rewritten ->` 那一条放宽成"任何结果都发提示"，立刻红。
     */
    @Test
    fun aFailedReplacementIsNotReportedAsSuccessAndTheBodyIsHandedBack() {
        val port = mockk<KnowledgePort>(relaxed = true)
        var composedSeenByCaller: String? = null
        coEvery { port.getLessonCount("kb") } returns 5
        coEvery { port.getCorrectionsRevision("kb") } returns 0
        coEvery {
            port.readTidyAndReplaceWithRevisionCheck(any(), any(), any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val compose = invocation.args[3] as (String) -> String?
            val out = compose.invoke(messyUserFile)
            composedSeenByCaller = out
            LessonsRewriteResult.WriteFailed(out ?: "")
        }
        val gateway = mockk<AiGateway>(relaxed = true)
        coEvery { gateway.generateRaw(any(), any()) } returns modelBatch
        coEvery { gateway.generateRawWithMetadata(any(), any()) } returns
            RawGenerationResult(content = "", finishReason = "stop", error = null)
        val topicRecorder = mockk<TopicRecorder>(relaxed = true)
        coEvery { topicRecorder.getTopicFullContext(any(), any()) } returns "话题上下文"

        val emitted = mutableListOf<KnowledgeTriggerEvent>()
        runBlocking {
            KnowledgeTriggerCoordinator(port, gateway, mockk<PromptBuilder>(relaxed = true), topicRecorder)
                .triggerEvents("kb").collect { emitted += it }
        }

        assertFalse("替换没落成不许报成功", emitted.any { it is KnowledgeTriggerEvent.Notice && it.message == "已记入经验" })
        val handed = checkNotNull(composedSeenByCaller) {
            "那一篇必须交回调用侧（不许分配完编号就把正文咽掉）"
        }
        assertTrue("交回的必须正是这一批", handed.contains("- 触发：她主动报备行程"))
        assertEquals(
            "编号也确实是发过的第 4 次",
            listOf(1, 2, 3, 4),
            Regex("""第(\d+)次提取""").findAll(handed).map { it.groupValues[1].toInt() }.toList()
        )
    }

    // ═══════════ 6. 取消：原样重抛，不许折算成一次"整理失败" ═══════════

    /**
     * 写事务里抛出 CancellationException（收集方被取消就是这条形状）：
     * 冷流必须把它原样传出去，并且**不许**发「已记入经验」。
     *
     * 坏实现（`extractLessons` 外面那圈 `catch (e: Exception)` 忘了先重抛 CE，
     * 或用 runCatching 把取消咽掉）会在这里红：`collect` 正常返回了，
     * 收集方就以为这一轮真的跑完了。
     */
    @Test
    fun cancellationInsideTheLessonTransactionIsRethrownNotSwallowed() {
        val port = mockk<KnowledgePort>(relaxed = true)
        coEvery { port.getLessonCount("kb") } returns 5
        coEvery { port.getCorrectionsRevision("kb") } returns 0
        coEvery {
            port.readTidyAndReplaceWithRevisionCheck(any(), any(), any(), any())
        } throws CancellationException("用户切走了")
        val gateway = mockk<AiGateway>(relaxed = true)
        coEvery { gateway.generateRaw(any(), any()) } returns modelBatch
        val topicRecorder = mockk<TopicRecorder>(relaxed = true)
        coEvery { topicRecorder.getTopicFullContext(any(), any()) } returns "话题上下文"

        val emitted = mutableListOf<KnowledgeTriggerEvent>()
        val thrown = runCatching {
            runBlocking {
                KnowledgeTriggerCoordinator(port, gateway, mockk<PromptBuilder>(relaxed = true), topicRecorder)
                    .triggerEvents("kb").collect { emitted += it }
            }
        }.exceptionOrNull()

        assertTrue("取消必须原样穿出冷流，实际：$thrown", thrown is CancellationException)
        assertFalse("被取消的一轮不许报成功", emitted.any { it is KnowledgeTriggerEvent.Notice && it.message == "已记入经验" })
    }
}
