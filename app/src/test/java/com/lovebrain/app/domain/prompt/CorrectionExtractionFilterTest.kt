package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MuteDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

/**
 *  F3.3 提取链统一纠正过滤的判据格：[CorrectionExtractionFilter] 必须**复用**
 * [MemoryRefPolicy] 的注入侧语义（WRONG/MUTED/FINISHED/WRONG_PERSON），不发明新状态。
 *
 * 每一格的对照物是注入侧行为：
 * - WRONG + 补正 → 原文不出现，补正值以 `（已纠正：…）` 传下去（同注入侧文案）；
 * - WRONG 无补正 / FINISHED / WRONG_PERSON / MUTED 未过期 → 内容整段不进提取材料；
 * - MUTED 已过期（TODAY 跨天）→ 与注入侧同判恢复，原文放行；
 * - 未纠正 → 原文逐字放行（一个字节都不动）。
 */
class CorrectionExtractionFilterTest {

    private val kb = "kb"

    private fun correction(
        id: String,
        action: CorrectionAction,
        replacement: String = "",
        muteDuration: MuteDuration = MuteDuration.UNTIL_RESTORE,
        muteTimestamp: String = ""
    ) = MemoryCorrection(
        memoryId = id, action = action, replacementText = replacement,
        muteDuration = muteDuration, muteTimestamp = muteTimestamp
    )

    private fun filter(
        content: String,
        corrections: Map<String, MemoryCorrection>,
        kind: MemoryKind = MemoryKind.PROFILE,
        sourcePath: String = "understand/her.md",
        entryId: String = ""
    ): String? = CorrectionExtractionFilter.filterForExtraction(kb, kind, sourcePath, content, corrections, entryId)

    @Test
    fun `uncorrected content passes through verbatim`() {
        val content = "她喜欢猫，养了一只叫麻薯的猫"
        assertEquals(content, filter(content, emptyMap()))
    }

    @Test
    fun `blank content stays blank regardless of corrections`() {
        assertEquals("", filter("", emptyMap()))
        assertEquals("   ", filter("   ", emptyMap()))
    }

    @Test
    fun `wrong with replacement drops original and annotates corrected value`() {
        val content = "她在北京工作"
        val out = filter(content, mapOf("PROFILE:understand/her.md" to correction(
            "PROFILE:understand/her.md", CorrectionAction.WRONG, replacement = "她在上海工作")))
        assertNull("被 WRONG 纠正的原文不得进提取材料", out?.contains(content)?.let { if (it) "leaked" else null })
        assertEquals("（已纠正：她在上海工作）\n", out)
    }

    @Test
    fun `wrong without replacement excludes content entirely`() {
        val out = filter("她在北京工作", mapOf("PROFILE:understand/her.md" to correction(
            "PROFILE:understand/her.md", CorrectionAction.WRONG)))
        assertNull(out)
    }

    @Test
    fun `muted unexpired content is excluded from extraction material`() {
        val out = filter("她养了一只猫", mapOf("PROFILE:understand/her.md" to correction(
            "PROFILE:understand/her.md", CorrectionAction.MUTED,
            muteDuration = MuteDuration.UNTIL_RESTORE, muteTimestamp = OffsetDateTime.now().toString())))
        assertNull("MUTED 未过期的内容不得进提取材料", out)
    }

    @Test
    fun `muted expired today-duration recovers like injection side`() {
        val yesterday = OffsetDateTime.now().minusDays(1).toString()
        val content = "她养了一只猫"
        val out = filter(content, mapOf("PROFILE:understand/her.md" to correction(
            "PROFILE:understand/her.md", CorrectionAction.MUTED,
            muteDuration = MuteDuration.TODAY, muteTimestamp = yesterday)))
        assertEquals("TODAY 静音跨天后与注入侧同判恢复", content, out)
    }

    @Test
    fun `finished and wrong person exclude content`() {
        val finished = filter("周末去了西湖", mapOf("SCENE:moment/scene.md" to correction(
            "SCENE:moment/scene.md", CorrectionAction.FINISHED)), kind = MemoryKind.SCENE,
            sourcePath = "moment/scene.md")
        assertNull(finished)
        val wrongPerson = filter("她在北京工作", mapOf("PROFILE:understand/her.md" to correction(
            "PROFILE:understand/her.md", CorrectionAction.WRONG_PERSON)))
        assertNull(wrongPerson)
    }

    @Test
    fun `entry level ongoing correction only hits that entry id`() {
        val content = "周末约她看电影"
        val hit = filter(content, mapOf("ONGOING:moment/plan.md:看电影" to correction(
            "ONGOING:moment/plan.md:看电影", CorrectionAction.FINISHED)),
            kind = MemoryKind.ONGOING, sourcePath = "moment/plan.md", entryId = "看电影")
        assertNull("被 FINISHED 的那条事项不进提取材料", hit)

        val miss = filter(content, mapOf("ONGOING:moment/plan.md:另一件事" to correction(
            "ONGOING:moment/plan.md:另一件事", CorrectionAction.FINISHED)),
            kind = MemoryKind.ONGOING, sourcePath = "moment/plan.md", entryId = "看电影")
        assertEquals("别条事项的纠正不得误伤本条", content, miss)
    }

    @Test
    fun `lesson file correction uses lesson kind and path`() {
        val content = "# 批1\n- 她提到不喜欢被打断"
        val out = filter(content, mapOf("LESSON:memory/lessons.md" to correction(
            "LESSON:memory/lessons.md", CorrectionAction.WRONG, replacement = "她喜欢被打断")),
            kind = MemoryKind.LESSON, sourcePath = "memory/lessons.md")
        assertEquals("（已纠正：她喜欢被打断）\n", out)
        assertNull("原文不得残留", out?.takeIf { it.contains("不喜欢被打断") })
    }
}
