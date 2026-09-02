package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识引擎契约单测（ · ， ）——阈值/块数/文案/邀约兜底入测试。
 *
 * 资产来源：app/src/main/assets（由 build.gradle.kts sourceSets.test.resources.srcDir
 * 挂入 test classpath，加载模式照抄 PromptBuilderStageContractTest.loadAsset，禁止副本/内联）。
 */
class KnowledgeEngineContractTest {

    private fun loadAsset(path: String): String {
        val cls = ClassLoader.getSystemClassLoader()
        val res = cls.getResourceAsStream(path)
            ?: error("资产 $path 未在 test classpath 上——检查 build.gradle.kts sourceSets.test.resources.srcDir")
        return res.bufferedReader().use { it.readText() }
    }

    /**
     * 资产禁词表——与   同源（固定子串，逐条基线双向校准过）。
     * 合法表述"最近25个话题"（经验上下文窗口）不含任一禁词子串，不误伤。
     */
    private val forbiddenPhrases = listOf(
        "50 个话题",
        "50个话题",
        "每 50 话题",
        "每50话题",
        "每25个话题",
        "per 50 topics",
        "2-4句剖析",
        "2-4 句剖析",
        "本批25个话题"
    )

    // ═══ ~T4：AppConfig 阈值常量 ═══

    @Test
    fun `vector reestimate interval is 3`() {
        assertEquals("VECTOR_REESTIMATE_INTERVAL 应为 3", 3, AppConfig.VECTOR_REESTIMATE_INTERVAL)
    }

    @Test
    fun `lesson trigger interval is 5`() {
        assertEquals("LESSON_TRIGGER_INTERVAL 应为 5", 5, AppConfig.LESSON_TRIGGER_INTERVAL)
    }

    @Test
    fun `reflect trigger interval is 5`() {
        assertEquals("REFLECT_TRIGGER_INTERVAL 应为 5", 5, AppConfig.REFLECT_TRIGGER_INTERVAL)
    }

    @Test
    fun `reflect context topics is 5`() {
        assertEquals("REFLECT_CONTEXT_TOPICS 应为 5", 5, AppConfig.REFLECT_CONTEXT_TOPICS)
    }

    // ═══ T5：资产禁词零残留（门禁  的编译期化）═══

    @Test
    fun `all registered assets contain no forbidden threshold phrases`() {
        val hits = mutableListOf<String>()
        AssetRegistry.ALL.forEach { path ->
            val content = loadAsset(path)
            forbiddenPhrases.forEach { phrase ->
                if (content.contains(phrase)) hits.add("$path 含禁词「$phrase」")
            }
        }
        assertTrue("资产禁词残留（与   同表）：$hits", hits.isEmpty())
    }

    // ═══ T6：reflect.md 输入行与 user prompt 实际输入一致 ═══

    @Test
    fun `reflect asset input line matches new reflect inputs`() {
        val reflect = loadAsset("engine/knowledge_prompt/reflect.md")
        assertTrue("reflect.md 应含「最近5个话题档案」", reflect.contains("最近5个话题档案"))
        assertTrue("reflect.md 应含「谈心分析」（输入项补齐）", reflect.contains("谈心分析"))
        assertTrue("reflect.md 应含「每积累 5 个话题」（触发时机新阈值）", reflect.contains("每积累 5 个话题"))
    }

    // ═══ T7：谈心分析块标尺压实 ═══

    @Test
    fun `counseling asset analysis block ruler is compact`() {
        val counseling = loadAsset("engine/counseling.md")
        assertTrue("counseling.md 应含「核心判断」（新标尺）", counseling.contains("核心判断"))
        assertFalse("counseling.md 不应含旧标尺「2-4句剖析」", counseling.contains("2-4句剖析"))
        assertFalse("counseling.md 不应含旧标尺「2-4 句剖析」（带空格双形）", counseling.contains("2-4 句剖析"))
    }

    // ═══ T8：邀约旧值兜底（删映射后读侧经 normalizeOrUnknown 落"待确定"，不报错）═══

    @Test
    fun `deprecated invite stage values are rejected and fall back to unknown`() {
        assertNull("normalize(\"邀约\") 应为 null（不在八阶段白名单）", StageCatalog.normalize("邀约"))
        assertEquals("normalizeOrUnknown(\"邀约\") 应落「待确定」", "待确定", StageCatalog.normalizeOrUnknown("邀约"))
        assertNull("normalize(\"邀约期\") 应为 null（已废除阶段）", StageCatalog.normalize("邀约期"))
    }

    // ═══ T9：A9 经验提取计数正则只认真实提取节（与写入格式逐字同构）═══

    @Test
    fun `lesson extraction count only matches real dated sections`() {
        // 0 个真实节（模板骨架，A9 已删示例节）→ 首次提取 = 第 1 次
        val skeleton = "# 经验库\n\n军师自动追加提取节，节头格式见写入侧。\n"
        assertEquals("0 个真实节 → 第 1 次提取", 1, countLessonSections(skeleton) + 1)

        // 2 个真实节：节头与 extractLessonsAsync 的 entry 格式逐字同构（`\n\n# [time] 第N次提取\n\n`）
        val twoSections = "# 经验库\n" +
            "\n\n# [2026-08-30 21:00] 第1次提取\n\n- 测试：行为；接法：有效回复\n" +
            "\n\n# [2026-08-30 22:05] 第2次提取\n\n- 测试：行为；接法：有效回复\n"
        assertEquals("2 个真实节 → 第 3 次提取", 3, countLessonSections(twoSections) + 1)
    }

    // ═══ T10：A9 schema/lessons.md 模板无示例提取节（恰 1 个一级标题）═══

    @Test
    fun `lessons schema template contains exactly one top level heading`() {
        val schema = loadAsset("schema/lessons.md")
        val topHeadings = schema.lines().filter { it.startsWith("# ") }
        assertEquals("模板应恰含 1 个一级标题（示例提取节已删）", 1, topHeadings.size)
        assertEquals("唯一一级标题应为 # 经验库", "# 经验库", topHeadings.first())
    }

    // ═══ T11：A12 三引擎串行触发（向量重估 → 经验提取 → 画像 reflect）═══

    @Test(timeout = 30_000)
    fun `checkTriggers runs three engines serially vector then lessons then reflect`() {
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val callIndex = java.util.concurrent.atomic.AtomicInteger(0)

        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getLessonCount("kb") } returns 15 // 15 % 3 == 0 且 % 5 == 0 → 三引擎齐触发
        val topicRecorder = mockk<TopicRecorder>(relaxed = true)
        coEvery { topicRecorder.getTopicFullContext(any(), any()) } returns "话题上下文" // 经验引擎要求非空上下文
        coEvery { topicRecorder.getVectorContext(any()) } returns "向量上下文"
        val deepSeekRepo = mockk<DeepSeekRepository>()
        coEvery { deepSeekRepo.generateRaw(any(), any()) } coAnswers {
            val idx = callIndex.getAndIncrement()
            events.add("start-$idx")
            delay(80) // 串行 = 前一引擎 end 先于下一引擎 start；并发必然乱序
            events.add("end-$idx")
            when (idx) {
                0 -> "===REASON===\n向量依据\n===STAGE===\n" // 向量重估 raw（无维度数字 → 维值不变，不走阶段分支）
                1 -> "记录一条新经验" // 经验提取（非空且非"无新经验"）
                else -> "{\"message_to_user\":\"ok\",\"observations\":[],\"stage_changed\":false,\"new_stage\":\"\"}" // 画像 reflect JSON
            }
        }
        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        val callbacks = mockk<KnowledgeTriggerCoordinator.Callbacks>(relaxUnitFun = true)

        val coordinator = KnowledgeTriggerCoordinator(knowledgeRepo, deepSeekRepo, promptBuilder, topicRecorder)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        coordinator.checkTriggers("kb", scope, callbacks)
        runBlocking { scope.coroutineContext[Job]!!.children.forEach { it.join() } }
        scope.cancel()

        assertEquals("三引擎应各触发一次", 3, callIndex.get())
        assertEquals(
            "触发顺序应串行：向量(0) → 经验(1) → 画像(2)，且前引擎完成先于后引擎开始",
            listOf("start-0", "end-0", "start-1", "end-1", "start-2", "end-2"),
            events.toList()
        )
    }

    // ═══ T12：A6 onboarding 阶段枚举收口八阶段，与 StageCatalog.ALL 逐项同序一致 ═══

    @Test
    fun `onboarding stage enumeration line matches StageCatalog exactly`() {
        val onboarding = loadAsset("engine/knowledge_prompt/onboarding.md")
        val enumLine = onboarding.lines().single { it.contains("只写阶段名") }
        assertTrue("onboarding 阶段枚举行应为八选一", enumLine.contains("八选一"))
        val stages = enumLine.substringAfter("只写阶段名：")
            .removeSuffix("）")
            .split(" / ")
            .map { it.trim() }
        assertEquals("onboarding 阶段枚举应与 StageCatalog.ALL 逐项同序一致", StageCatalog.ALL, stages)
        assertFalse("onboarding 枚举行不应含已废除阶段「邀约期」", enumLine.contains("邀约期"))
    }
}
