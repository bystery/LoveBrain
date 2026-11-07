package com.lovebrain.app.domain

import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import kotlinx.coroutines.runBlocking
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
}
