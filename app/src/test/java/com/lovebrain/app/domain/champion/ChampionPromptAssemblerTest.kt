package com.lovebrain.app.domain.champion

import com.lovebrain.app.model.IntentConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 冠军 prompt 装配（段序固定、空段不出现；JSON 行转义与注入防护同口径）。
 * 未定/待确认事项段已整段删除：装配任何轮次都不再产生该段与它的计量。
 */
class ChampionPromptAssemblerTest {

    private val assembler = ChampionPromptAssembler(
        timestampBlock = { "【当前时间】2026-11-06 10:00\n（产品时间块文案——由 PromptBuilder.buildTimestampPrompt 注入）" },
        promptSource = { "" }
    )

    private fun ev(uid: Long, role: String, text: String, ts: String, turn: Int = 1) =
        ChampionEvent(uid, role, text, ts, turn)

    @Test
    fun `section order is fixed and empty sections are omitted`() {
        val cur = listOf(ev(0, "HER", "你好", "2026-11-06 10:00"), ev(1, "ME", "早", "2026-11-06 10:01", 1))
        val p = assembler.assemble(
            events = cur, curWindow = cur, turn = 1,
            intent = IntentConfig(), advisorNote = "", wallClock = "2026-11-06 10:00"
        )
        // 只有：历史段 + 本轮段 + 时间段（种子/intent/note 全空）；段序固定
        val iH = p.prompt.indexOf("# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】")
        val iC = p.prompt.indexOf("# 本轮输入(最高优先，整条不被裁；id=来源ID，PARTNER=对方/USER=本人)")
        val iT = p.prompt.indexOf("【当前时间】")
        assertTrue(iH >= 0 && iC > iH && iT > iC)
        assertFalse(p.prompt.contains("【画像/初始事实"))
        assertFalse(p.prompt.contains("【持续意图】"))
        // 未定块已整段删除：装配任何轮次都不再出现该段
        assertFalse(p.prompt.contains("# 【未定/待确认事项"))
    }

    @Test
    fun `current window lines are json rows with alias and stable source ids`() {
        val cur = listOf(ev(5, "HER", "带\"引号\"与\n换行", "2026-11-06 10:00"))
        val p = assembler.assemble(cur, cur, 1, IntentConfig(), "", "2026-11-06 10:00")
        assertTrue(p.prompt.contains("<chat>"))
        assertTrue(p.prompt.contains("{\"id\": \"m0\", \"source_id\": 5, \"speaker\": \"PARTNER\", \"ts\": \"2026-11-06 10:00\", \"text\": \"带\\\"引号\\\"与\\n换行\"}"))
        assertTrue(p.prompt.contains("</chat>"))
        assertEquals(mapOf("m0" to 5L), p.sourceAliasMap)
    }

    @Test
    fun `history excludes current window and uses the 3600 whole-line budget`() {
        val hist = listOf(
            ev(0, "HER", "旧消息一", "2026-11-05 09:00", 1),
            ev(1, "ME", "旧消息二", "2026-11-05 09:01", 1)
        )
        val cur = listOf(ev(2, "HER", "新消息", "2026-11-06 10:00", 2))
        val p = assembler.assemble(hist + cur, cur, 2, IntentConfig(), "", "2026-11-06 10:00")
        assertTrue(p.prompt.contains("m_src[0] | 2026-11-05 09:00 | PARTNER | 旧消息一"))
        assertTrue(p.prompt.contains("m_src[1] | 2026-11-05 09:01 | USER | 旧消息二"))
        // 当前消息不在历史里重复
        assertFalse(p.prompt.contains("m_src[2]"))
        assertTrue(p.historyCu <= EventHistoryAssembler.MAX_EVENT_HISTORY_CU)
    }

    @Test
    fun `intent appears only when effective and never leaves stale text`() {
        val cur = listOf(ev(0, "HER", "hi", "2026-11-06 10:00"))
        val off = IntentConfig(text = "推进周五见面", enabled = false)
        val p1 = assembler.assemble(cur, cur, 1, off, "", "2026-11-06 10:00")
        assertFalse(p1.prompt.contains("【持续意图】"))
        val on = IntentConfig(text = "推进周五见面", enabled = true)
        val p2 = assembler.assemble(cur, cur, 1, on, "", "2026-11-06 10:00")
        assertTrue(p2.prompt.contains("【持续意图】\n推进周五见面"))
    }

    @Test
    fun `advisor note is injected verbatim when non-empty`() {
        val cur = listOf(ev(0, "HER", "hi", "2026-11-06 10:00"))
        val p = assembler.assemble(cur, cur, 1, IntentConfig(), "别提钱", "2026-11-06 10:00")
        assertTrue(p.prompt.contains(com.lovebrain.app.domain.prompt.IntentIdeaBlock.NOTE_BLOCK_HEADER))
        assertTrue(p.prompt.contains("「别提」".replace("别提", "别提钱")))
    }

    @Test
    fun `no uncertain section is ever emitted`() {
        // 未定块完整删除：即便历史/本轮里全是"未定"话术，装配也只出历史段与本轮段
        val cur = listOf(ev(0, "HER", "如果明天到货就给你寄", "2026-11-06 10:00"))
        val p = assembler.assemble(cur, cur, 1, IntentConfig(), "", "2026-11-06 10:00")
        val iHist = p.prompt.indexOf("# 【真实历史消息")
        val iCur = p.prompt.indexOf("# 本轮输入")
        assertFalse("未定段整段不出现", p.prompt.contains("# 【未定/待确认事项"))
        assertTrue("历史段后直接是本轮段", iHist >= 0 && iCur > iHist)
    }

    @Test
    fun `seed section renders persona and initial_kb in python dumps shape`() {
        val cur = listOf(ev(0, "HER", "hi", "2026-11-06 10:00"))
        val seed = ChampionPromptAssembler.ChampionSeed(
            persona = Json.parseToJsonElement("{\"name\": \"小林\", \"likes\": \"爬山\"}") as kotlinx.serialization.json.JsonObject
        )
        val p = assembler.assemble(cur, cur, 1, IntentConfig(), "", "2026-11-06 10:00", seed)
        assertTrue(p.prompt.startsWith("【画像/初始事实(种子，可公开使用)】\n{\"persona\": {\"name\": \"小林\", \"likes\": \"爬山\"}}\n\n# 【真实历史消息"))
    }

    @Test
    fun `empty wallclock omits the timestamp block`() {
        val cur = listOf(ev(0, "HER", "hi", "2026-11-06 10:00"))
        val p = assembler.assemble(cur, cur, 1, IntentConfig(), "", "  ")
        assertFalse(p.prompt.contains("【当前时间】"))
    }

    @Test
    fun `system prompt is exactly the four slim assets without declaration`() {
        val a = ChampionPromptAssembler(
            timestampBlock = { "" },
            promptSource = { path -> "[$path]" }
        )
        val sys = a.buildSystemPrompt()
        assertEquals(
            "[engine/champion/core_slim.md]\n\n---\n\n" +
                "[engine/champion/naturalness_check.md]\n\n---\n\n" +
                "[engine/champion/redline.md]\n\n---\n\n" +
                "[engine/champion/format.md]",
            sys
        )
        assertFalse(sys.contains("输入安全声明"))
    }
}
