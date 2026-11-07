package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MuteDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ════════ F3.3 扩展（R4 覆盖面）：recent / raw_chat / raw_scene / raw_topic ════════

    private val sceneChain = "- [2026-11-05 20:00] 出差：她在杭州出差|src=m1|spk=HER|subj=HER；住滨江酒店|src=m2"

    @Test
    fun `scene entry fact texts strip decorations in both formats`() {
        val texts = CorrectionExtractionFilter.sceneEntryFactTexts(
            "- [2026-11-05 20:00] 出差：她在杭州出差|src=m1|spk=HER；住滨江酒店⟨m2,m3⟩\n" +
            "- [2026-11-05 21:00] 日常：她养了一只猫\n" +
            "她：这不是条目行\n"
        )
        assertEquals(setOf("她在杭州出差", "住滨江酒店", "她养了一只猫"), texts)
    }

    @Test
    fun `corrected fact texts empty without active correction`() {
        assertTrue("未纠正 → 空集", CorrectionExtractionFilter
            .correctedSceneFactTexts(kb, sceneChain, emptyMap()).isEmpty())
        val expired = correction("SCENE:moment/scene.md", CorrectionAction.MUTED,
            muteDuration = MuteDuration.TODAY, muteTimestamp = OffsetDateTime.now().minusDays(1).toString())
        assertTrue("MUTED 过期恢复 → 空集", CorrectionExtractionFilter
            .correctedSceneFactTexts(kb, sceneChain, mapOf("SCENE:moment/scene.md" to expired)).isEmpty())
    }

    @Test
    fun `corrected fact texts include all scene facts while any scene correction is active`() {
        val active = mapOf("SCENE:moment/scene.md" to correction(
            "SCENE:moment/scene.md", CorrectionAction.WRONG, replacement = "她在苏州出差"))
        assertEquals(setOf("她在杭州出差", "住滨江酒店"),
            CorrectionExtractionFilter.correctedSceneFactTexts(kb, sceneChain, active))
        val muted = mapOf("SCENE:moment/scene.md" to correction(
            "SCENE:moment/scene.md", CorrectionAction.MUTED,
            muteTimestamp = OffsetDateTime.now().toString()))
        assertEquals("MUTED 未过期同样生效", setOf("她在杭州出差", "住滨江酒店"),
            CorrectionExtractionFilter.correctedSceneFactTexts(kb, sceneChain, muted))
    }

    @Test
    fun `scene archive drops only the exact corrected fact and keeps adjacent facts verbatim`() {
        val corrected = setOf("她在杭州出差")
        val content = "- [2026-11-04 09:00] 旧况：她在杭州出差|src=a1|spk=HER；住西湖酒店|src=a2|spk=HER\n" +
            "- [2026-11-03 08:00] 日常：她养了一只猫\n"
        val out = CorrectionExtractionFilter.filterSceneArchive(content, corrected)
        assertEquals("只剔除精确命中的那一条事实，剔除量=1", 1, out.removed)
        assertFalse("旧值不得残留", out.text.contains("她在杭州出差"))
        assertTrue("相邻事实逐字保留（含 src/spk 装饰）", out.text.contains("住西湖酒店|src=a2|spk=HER"))
        assertTrue("未被波及的条目行逐字保留", out.text.contains("- [2026-11-03 08:00] 日常：她养了一只猫"))
        assertTrue("幸存事实所在的条目行头逐字保留", out.text.contains("- [2026-11-04 09:00] 旧况："))
    }

    @Test
    fun `scene archive drops the whole entry line only when no fact survives`() {
        val corrected = setOf("她在杭州出差", "住西湖酒店")
        val content = "- [2026-11-04 09:00] 旧况：她在杭州出差；住西湖酒店\n" +
            "- [2026-11-03 08:00] 日常：她养了一只猫\n"
        val out = CorrectionExtractionFilter.filterSceneArchive(content, corrected)
        assertEquals(2, out.removed)
        assertFalse(out.text.contains("- [2026-11-04 09:00]"))
        assertTrue("别的条目行不受影响", out.text.contains("- [2026-11-03 08:00] 日常：她养了一只猫"))
    }

    @Test
    fun `scene archive passes non entry lines and untouched content byte for byte`() {
        val content = "# [2026-11-01] 旧话题\n\n## [2026-11-01] 状态变化\n- [2026-11-01 08:00] 日常：她养了一只猫\n\n"
        val out = CorrectionExtractionFilter.filterSceneArchive(content, setOf("她在杭州出差"))
        assertEquals("没有任何命中的归档逐字节不变", content, out.text)
        assertEquals(0, out.removed)
        val outEmpty = CorrectionExtractionFilter.filterSceneArchive(content, emptySet())
        assertEquals("空基准直通", content, outEmpty.text)
    }

    @Test
    fun `chat archive drops only the whole line exactly matching a corrected fact`() {
        val corrected = setOf("她在杭州出差")
        val content = "- [2026-11-05 20:00]\n" +
            "<!-- LB:ROUND:c-0 -->\n" +
            "她：她在杭州出差\n" +
            "她：她在杭州出差啦\n" +
            "我：你在杭州哪个区\n" +
            "我的想法：问问她出差累不累\n" +
            "我（最终回复：方案A-关心）：出差辛苦吗？\n"
        val out = CorrectionExtractionFilter.filterChatArchive(content, corrected)
        assertEquals("只剔除精确命中的对话行", 1, out.removed)
        assertFalse("旧值整行不进提取材料", out.text.contains("她：她在杭州出差\n"))
        assertTrue("近似的行不得误剔（宁漏勿误）", out.text.contains("她：她在杭州出差啦"))
        assertTrue("块头与 marker 保留", out.text.contains("- [2026-11-05 20:00]\n"))
        assertTrue("想法行保留", out.text.contains("我的想法：问问她出差累不累"))
        assertTrue("最终回复行保留", out.text.contains("我（最终回复：方案A-关心）：出差辛苦吗？"))
        assertTrue("普通对话行保留", out.text.contains("我：你在杭州哪个区"))
    }

    @Test
    fun `chat archive passes content byte for byte with no hits`() {
        val content = "她：我到酒店了\n我：注意休息\n"
        val out = CorrectionExtractionFilter.filterChatArchive(content, setOf("她在杭州出差"))
        assertEquals(content, out.text)
        val outEmpty = CorrectionExtractionFilter.filterChatArchive(content, emptySet())
        assertEquals(content, outEmpty.text)
    }

    @Test
    fun `scene then chat filters compose for raw topic mixed archive`() {
        val corrected = setOf("她在杭州出差")
        val rawTopic = "# [2026-11-06 10:00] 出差安排\n\n" +
            "## [2026-11-06 10:00] 状态变化\n" +
            "- [2026-11-05 09:00] 出差：她在杭州出差；住西湖酒店\n\n" +
            "### [2026-11-06 10:00] 对话记录\n" +
            "她：她在杭州出差\n" +
            "她：今天见客户\n"
        val scenes = CorrectionExtractionFilter.filterSceneArchive(rawTopic, corrected)
        val out = CorrectionExtractionFilter.filterChatArchive(scenes.text, corrected)
        assertEquals("条目事实 + 对话行共剔除 2", 2, scenes.removed + out.removed)
        assertFalse(out.text.contains("她在杭州出差"))
        assertTrue("相邻事实与相邻对话行保留", out.text.contains("住西湖酒店"))
        assertTrue(out.text.contains("她：今天见客户"))
        assertTrue("归档头保留", out.text.contains("# [2026-11-06 10:00] 出差安排"))
        assertTrue("分节标题保留", out.text.contains("## [2026-11-06 10:00] 状态变化") &&
            out.text.contains("### [2026-11-06 10:00] 对话记录"))
    }
}
