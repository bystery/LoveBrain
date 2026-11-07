package com.lovebrain.app.domain

import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.prompt.CorrectionExtractionFilter
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 *  F3.3 知识读取层判据格：经验提取（[KnowledgeContextReader.getTopicFullContext]）
 * 与向量重估（[KnowledgeContextReader.getVectorContext]）读源材料时必须消费纠正表——
 * 被纠正的场景链内容不再按原文回流进提取材料；MUTED 未过期同样不出现在提取输入里。
 */
class KnowledgeContextReaderCorrectionTest {

    private val kb = "kb"

    private val sceneContent = "## 场景状态链\n- [2026-11-05 20:00] 她在杭州出差（2小时前）\n"

    private fun newReader(
        corrections: Map<String, MemoryCorrection> = emptyMap()
    ): Pair<InMemoryKnowledgePort, KnowledgeContextReader> {
        val port = InMemoryKnowledgePort()
        port.seed(kb, "moment/scene.md", sceneContent)
        port.seed(kb, "moment/recent.md", "她：我到酒店了\n")
        port.seed(kb, "moment/topic.md", "- [2026-11-05 20:00] 正在聊：出差安排")
        port.seed(kb, "memory/raw_topic.md", "# [2026-11-05]\n话题档案内容\n")
        if (corrections.isNotEmpty()) port.seedCorrections(kb, corrections)
        return port to KnowledgeContextReader(port)
    }

    @Test
    fun `uncorrected scene still flows into extraction context`() = runBlocking {
        val (_, reader) = newReader()
        val context = reader.getTopicFullContext(kb)
        assertTrue("未被纠正的场景链应原样进提取材料", context.contains("她在杭州出差"))
        val vector = reader.getVectorContext(kb)
        assertTrue(vector.contains("她在杭州出差"))
    }

    @Test
    fun `corrected scene does not leak into lessons extraction material`() = runBlocking {
        val (port, reader) = newReader(mapOf(
            "SCENE:moment/scene.md" to MemoryCorrection(
                memoryId = "SCENE:moment/scene.md",
                action = CorrectionAction.WRONG,
                replacementText = "她在苏州出差"
            )
        ))
        val context = reader.getTopicFullContext(kb)
        assertFalse("被 WRONG 纠正的旧场景值不得回流进经验提取材料", context.contains("她在杭州出差"))
        assertTrue("补正值应传导进提取材料", context.contains("（已纠正：她在苏州出差）"))
        // 其余源材料不受影响
        assertTrue(context.contains("我到酒店了"))
        assertTrue(port.dump().getValue("$kb/moment/scene.md").contains("她在杭州出差"))
    }

    @Test
    fun `muted scene absent from vector reestimate input`() = runBlocking {
        val (_, reader) = newReader(mapOf(
            "SCENE:moment/scene.md" to MemoryCorrection(
                memoryId = "SCENE:moment/scene.md",
                action = CorrectionAction.MUTED,
                muteDuration = MuteDuration.UNTIL_RESTORE,
                muteTimestamp = OffsetDateTime.now().toString()
            )
        ))
        val vector = reader.getVectorContext(kb)
        assertFalse("MUTED 未过期的内容不得出现在向量重估输入", vector.contains("她在杭州出差"))
        assertFalse(vector.contains("（已纠正"))
        assertFalse("经验提取上下文同样不见该条", reader.getTopicFullContext(kb).contains("她在杭州出差"))
    }

    @Test
    fun `muted expired today-duration recovers in extraction context`() = runBlocking {
        val (_, reader) = newReader(mapOf(
            "SCENE:moment/scene.md" to MemoryCorrection(
                memoryId = "SCENE:moment/scene.md",
                action = CorrectionAction.MUTED,
                muteDuration = MuteDuration.TODAY,
                muteTimestamp = OffsetDateTime.now().minusDays(1).toString()
            )
        ))
        assertTrue("TODAY 静音跨天后恢复", reader.getVectorContext(kb).contains("她在杭州出差"))
    }

    @Test
    fun `wrong without replacement excludes scene from both contexts`() = runBlocking {
        val (_, reader) = newReader(mapOf(
            "SCENE:moment/scene.md" to MemoryCorrection(
                memoryId = "SCENE:moment/scene.md",
                action = CorrectionAction.WRONG
            )
        ))
        assertFalse(reader.getTopicFullContext(kb).contains("她在杭州出差"))
        assertFalse(reader.getVectorContext(kb).contains("她在杭州出差"))
        // 场景段整段省略：连"【此刻状态】"节头都不出现
        assertFalse(reader.getVectorContext(kb).contains("【此刻状态】"))
    }

    // ═══ F3.3 扩展（R4 覆盖面）：recent / raw_chat / raw_scene / raw_topic 不再原样放行 ═══

    private val sceneRaw = "- [2026-11-05 20:00] 出差：她在杭州出差|src=m1|spk=HER|subj=HER\n"

    private val rawSceneContent =
        "- [2026-11-04 09:00] 旧况：她在杭州出差；她感冒了\n" +
        "- [2026-11-03 08:00] 日常：她养了一只猫\n"

    private val recentContent =
        "- [2026-11-05 20:00]\n" +
        "<!-- LB:ROUND:c-3 -->\n" +
        "她：她在杭州出差\n" +
        "我：见客户顺利吗\n"

    private val rawChatContent =
        "- [2026-11-04 18:00]\n" +
        "她：她在杭州出差\n" +
        "我：注意保暖\n"

    private val rawTopicContent =
        "# [2026-11-04 10:00] 出差安排\n\n" +
        "## [2026-11-04 10:00] 状态变化\n" +
        "- [2026-11-04 09:00] 旧况：她在杭州出差；她感冒了\n\n" +
        "### [2026-11-04 10:00] 对话记录\n" +
        "她：她在杭州出差\n" +
        "我：路上小心\n"

    private fun newFullReader(
        corrections: Map<String, MemoryCorrection>
    ): Pair<InMemoryKnowledgePort, KnowledgeContextReader> {
        val port = InMemoryKnowledgePort()
        port.seed(kb, "moment/scene.md", sceneRaw)
        port.seed(kb, "moment/recent.md", recentContent)
        port.seed(kb, "memory/raw_chat.md", rawChatContent)
        port.seed(kb, "memory/raw_scene.md", rawSceneContent)
        port.seed(kb, "memory/raw_topic.md", rawTopicContent)
        port.seed(kb, "moment/topic.md", "- [2026-11-05 20:00] 正在聊：出差安排")
        if (corrections.isNotEmpty()) port.seedCorrections(kb, corrections)
        return port to KnowledgeContextReader(port)
    }

    private val wrongCorrection = mapOf(
        "SCENE:moment/scene.md" to MemoryCorrection(
            memoryId = "SCENE:moment/scene.md",
            action = CorrectionAction.WRONG
        )
    )

    @Test
    fun `no correction leaves all raw paths byte for byte`() = runBlocking {
        val (port, reader) = newFullReader(emptyMap())
        val context = reader.getTopicFullContext(kb)
        assertTrue(context.contains("她：她在杭州出差"))
        assertTrue(context.contains("她在杭州出差；她感冒了"))
        assertTrue(context.contains("她养了一只猫"))
        assertEquals("未纠正时 raw_scene 逐字放行", rawSceneContent, port.dump().getValue("$kb/memory/raw_scene.md"))
        assertEquals("未纠正时 raw_topic 逐字放行", rawTopicContent, port.dump().getValue("$kb/memory/raw_topic.md"))
    }

    @Test
    fun `corrected fact is stripped from raw_scene entries and adjacent facts survive`() = runBlocking {
        val (_, reader) = newFullReader(wrongCorrection)
        val context = reader.getTopicFullContext(kb)
        assertFalse("被纠正的事实不得经状态暂存回流", context.contains("她在杭州出差"))
        assertTrue("同一条目行的相邻事实必须保留", context.contains("她感冒了"))
        assertTrue("别的条目行不受影响", context.contains("她养了一只猫"))
        assertTrue("幸存条目行的时间戳与标签保留", context.contains("- [2026-11-03 08:00] 日常：她养了一只猫"))
    }

    @Test
    fun `corrected fact is stripped from recent and raw_chat chat lines`() = runBlocking {
        val (_, reader) = newFullReader(wrongCorrection)
        val context = reader.getTopicFullContext(kb)
        assertFalse("被纠正的事实不得经最近对话回流", context.contains("她：她在杭州出差\n"))
        assertFalse("对话暂存里的副本同样剔除", context.contains("她：她在杭州出差\n我：注意保暖"))
        assertTrue("相邻对话行保留", context.contains("我：见客户顺利吗") && context.contains("我：注意保暖"))
        assertTrue("轮次块头与 marker 保留", context.contains("- [2026-11-05 20:00]\n"))
    }

    @Test
    fun `raw_topic archive is filtered on both status lines and chat lines`() = runBlocking {
        val (_, reader) = newFullReader(wrongCorrection)
        val context = reader.getTopicFullContext(kb)
        assertFalse("话题档案里的旧值不得回流", context.contains("她在杭州出差"))
        assertTrue("状态段相邻事实保留", context.contains("她感冒了"))
        assertTrue("对话段相邻行保留", context.contains("我：路上小心"))
        assertTrue("归档标题与分节标题保留", context.contains("# [2026-11-04 10:00] 出差安排") &&
            context.contains("## [2026-11-04 10:00] 状态变化") &&
            context.contains("### [2026-11-04 10:00] 对话记录"))
    }

    @Test
    fun `vector context applies the same archive filtering`() = runBlocking {
        val (_, reader) = newFullReader(wrongCorrection)
        val vector = reader.getVectorContext(kb)
        assertFalse("向量重估输入不得含被纠正的旧值", vector.contains("她在杭州出差"))
        assertTrue("相邻内容保留", vector.contains("她感冒了"))
    }

    @Test
    fun `wrong with replacement annotates scene and still strips archive copies`() = runBlocking {
        val (_, reader) = newFullReader(mapOf(
            "SCENE:moment/scene.md" to MemoryCorrection(
                memoryId = "SCENE:moment/scene.md",
                action = CorrectionAction.WRONG,
                replacementText = "她在苏州出差"
            )
        ))
        val context = reader.getTopicFullContext(kb)
        assertTrue("补正值经场景段传导", context.contains("（已纠正：她在苏州出差）"))
        assertFalse("归档里的旧值副本一并剔除", context.contains("她在杭州出差"))
        assertTrue("相邻事实保留", context.contains("她感冒了"))
    }

    // ═══ S3 遗漏 B：归档（scene.md 清空）后过滤依据不再消失 ═══

    @Test
    fun `persisted basis keeps archive filtering after scene md is archived away`() = runBlocking {
        val (port, reader) = newFullReader(wrongCorrection)
        // 模拟话题切换归档完成：scene.md 已清空，被纠正原文由记忆格持久在依据文件
        port.seed(kb, "moment/scene.md", "")
        port.seed(kb, CorrectionExtractionFilter.PERSISTED_FACTS_PATH, "她在杭州出差\n")
        val context = reader.getTopicFullContext(kb)
        assertFalse("归档后旧值不得漏回经验提取材料（S3 遗漏 B）", context.contains("她在杭州出差"))
        assertTrue("相邻内容保留", context.contains("她感冒了"))
        val vector = reader.getVectorContext(kb)
        assertFalse("向量重估同判", vector.contains("她在杭州出差"))
    }

    @Test
    fun `no persisted basis and empty scene leaves archives untouched`() = runBlocking {
        // 旧数据兜底：本功能上线前保存的纠正没有持久依据，scene.md 已清空时宁漏勿误
        val (port, reader) = newFullReader(wrongCorrection)
        port.seed(kb, "moment/scene.md", "")
        val context = reader.getTopicFullContext(kb)
        assertTrue("没有依据就没有剔除，一字不动", context.contains("她在杭州出差"))
    }
}
