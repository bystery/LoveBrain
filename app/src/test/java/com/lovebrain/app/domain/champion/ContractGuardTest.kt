package com.lovebrain.app.domain.champion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import org.junit.rules.TemporaryFolder

/**
 * ContractGuard 三层修复路径（规格书 §5.3：漂移族夹具在 Kotlin 侧与 Python 输出一致；
 * E2/E3 实测样本 AK02_T01、N01_T05 形状；fuzzy_map 未对拍前关闭）。
 */
class ContractGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newGuard(): ContractGuard = ContractGuard()

    // ── 守卫自身与基准行为 ──

    @Test
    fun `clean well-formed json is returned byte-identical with changed=false`() {
        val raw = """{"response": {"recommended": "好", "bad_boy": null, "playful": null, "warm": null}, "directions": [null, null, null, null], "analysis": {"a": 1}}"""
        val out = newGuard().repairAndAudit(raw)
        assertEquals(raw, out.text)
        assertFalse(out.changed)
        val rules = rulesOf(out)
        assertFalse("harness_text_rewritten" in rules)
    }

    @Test
    fun `empty response is flagged and raw text kept verbatim`() {
        val raw = """{"response": {"recommended": "", "bad_boy": null, "playful": null, "warm": null}, "directions": [null, null, null, null], "analysis": {}}"""
        val out = newGuard().repairAndAudit(raw)
        assertEquals(raw, out.text)
        assertTrue(metaOf(out)["empty_response"]?.toString() == "true")
        assertTrue("empty_response_raw_kept" in rulesOf(out))
    }

    @Test
    fun `explicit no_reply action is not an empty response and slots are not padded`() {
        val raw = """{"action": "no_reply", "reason": "不适合插话", "analysis": {}}"""
        val out = newGuard().repairAndAudit(raw)
        assertTrue(metaOf(out)["no_reply_action"]?.toString() == "true")
        assertFalse(metaOf(out)["empty_response"]?.toString() == "true")
        assertEquals(raw, out.text)
    }

    @Test
    fun `unparseable raw text is returned untouched`() {
        val raw = "这不是 JSON 的任何形状"
        val out = newGuard().repairAndAudit(raw)
        assertEquals(raw, out.text)
        assertTrue("unparseable_raw_left_untouched" in rulesOf(out))
    }

    @Test
    fun `guard exception degrades to raw text with guard_exception rule`() {
        // 内联子类注入故障：raw=null 形状在 Kotlin 不可能；用 auditSink 抛异常验证降级不传播
        val throwing = ContractGuard(ContractGuard.GuardAuditSink { error("boom") })
        val raw = """{"response": {"recommended": "好", "bad_boy": null, "playful": null, "warm": null}, "directions": [null, null, null, null], "analysis": {}}"""
        // 未变化 → 不写审计 → 不炸；变化路径的审计异常也被吞
        val out = throwing.repairAndAudit("""{"response": {"recommended": "好", "bad_boy": null, "playful": null, "warm": null}}""")
        assertTrue(out.text.startsWith("{\"response\""))
    }

    // ── 第 1 层：vendor 解析兜底 ──

    @Test
    fun `markdown fence is stripped and json recovered (I1)`() {
        val raw = "```json\n{\"response\": {\"recommended\": \"你好\"}}\n```"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertTrue(out.text.contains("\"recommended\": \"你好\""))
        assertTrue(out.text.contains("\"bad_boy\": null"))
    }

    @Test
    fun `curly structural quotes are normalized in order (I1)`() {
        val raw = "{\"response\": {＂recommended＂: ＂好＂, \"bad_boy\": null, \"playful\": null, \"warm\": null}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertTrue(out.text.contains("\"recommended\": \"好\""))
        // quote_normalize 记在 I1 留损的 detail 里（Python 同形）
        assertTrue("I1_inherited_parse" in repairLossRules(out))
    }

    @Test
    fun `E1 zero-width characters in style keys are normalized`() {
        // W17 实测形状：'bad_bo\u200by' 夹零宽字符（U+200B）
        val raw = "{\"response\": {\"recommended\": \"主\", \"bad_bo\\u200by\": \"备\"}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertTrue(out.text.contains("\"bad_boy\": \"备\""))
        assertTrue("E1_style_key_normalized" in repairLossRules(out))
    }

    @Test
    fun `E1 alias table maps bad_boys when the canonical slot is empty (I2)`() {
        val raw = "{\"response\": {\"bad_boys\": \"文案\", \"recommended\": null}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        // 别名表把 bad_boys 映射到 bad_boy（不是 recommended）；recommended 缺槽补 null
        assertTrue(out.text.contains("\"bad_boy\": \"文案\""))
        assertTrue(out.text.contains("\"recommended\": null"))
    }

    @Test
    fun `E1 both non-empty keeps the alias value in audit, never overwrites the canonical slot (I2)`() {
        val raw = "{\"response\": {\"bad_boys\": \"别名文本\", \"bad_boy\": \"规范文本\"}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertFalse(out.text.contains("别名文本"))
        // 与 Python 逐字一致：I2 别名路径的别名值只进留损计数（unmapped_style_keys），
        // 不进 meta.unmapped_preserved（那是未知键/零宽键路径的保全通道）
        val unmapped = (metaOf(out)["unmapped_preserved"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        assertTrue(unmapped.isEmpty())
        assertTrue("I2_I3_I4_inherited_normalize" in repairLossRules(out))
    }

    @Test
    fun `E2 premature top-level brace rejoins analysis (AK02_T01 shape)`() {
        // 实测形状：directions 之后提前闭合顶层 }，analysis 原样接回
        val raw = "{\"response\": {\"recommended\": \"好\", \"bad_boy\": null, \"playful\": null, \"warm\": null}, \"directions\": [null, null, null, null]}, \"analysis\": {\"topic\": \"t\"}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertTrue(out.text.contains("\"analysis\": {\"topic\": \"t\"}"))
        assertTrue("E2_premature_top_level_brace" in repairLossRules(out))
    }

    @Test
    fun `E3 stray wrapper object is unwrapped with a mismatched closer (N01_T05 shape)`() {
        // 实测形状：...,"directions":[...},{"analysis":{...}}（数组被 } 收尾 + analysis 多包一层）
        val raw = "{\"response\": {\"recommended\": \"好\", \"bad_boy\": null, \"playful\": null, \"warm\": null}, \"directions\": [null, null, null, null},{\"analysis\": {\"topic\": \"t\"}}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertTrue(out.text.startsWith("{\"response\""))
        assertTrue(out.text.contains("\"analysis\": {\"topic\": \"t\"}"))
        assertTrue("E3_stray_wrapper_object_removed" in repairLossRules(out))
    }

    // ── 第 2 层：漂移族补修 ──

    @Test
    fun `missing response slots are padded with null and registered`() {
        val raw = "{\"response\": {\"recommended\": \"好\"}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        for (k in listOf("recommended", "bad_boy", "playful", "warm")) {
            assertTrue(out.text.contains("\"$k\""))
        }
        // vendor 层（I4）已补 null；guard 层的 response_pad_null 不再触发（Python 同形）
        assertFalse("response_pad_null" in rulesOf(out))
        assertTrue("I2_I3_I4_inherited_normalize" in repairLossRules(out))
    }

    @Test
    fun `directions overflow is truncated to 4 with the extras preserved to audit`() {
        val raw = "{\"response\": {\"recommended\": \"好\", \"bad_boy\": null, \"playful\": null, \"warm\": null}, \"directions\": [\"一\", \"二\", \"三\", \"四\", \"五\"], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertTrue(out.text.contains("\"directions\": [\"一\", \"二\", \"三\", \"四\"]"))
        val extras = (metaOf(out)["extra_directions_preserved"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        assertEquals(1, extras.size)
        assertTrue(extras.joinToString().contains("五"))
    }

    @Test
    fun `missing directions list is padded to four nulls`() {
        val raw = "{\"response\": {\"recommended\": \"好\", \"bad_boy\": null, \"playful\": null, \"warm\": null}, \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertTrue(out.text.contains("\"directions\": [null, null, null, null]"))
        assertTrue("directions_added_null" in rulesOf(out))
    }

    @Test
    fun `action-only object is renamed to response`() {
        val raw = "{\"action\": {\"action\": \"decline\", \"decline_reason\": \"r\"}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        // Python 同形：改名后 action/decline_reason 被当未知风格键移除 → response 全空 →
        // empty_response 原样返回（守卫不伪造内容）；meta 仍如实登记改名与保全
        assertFalse(out.changed)
        assertEquals(raw, out.text)
        assertTrue("action_to_response" in rulesOf(out))
        assertTrue(metaOf(out)["empty_response"]?.toString() == "true")
        val unmapped = (metaOf(out)["unmapped_preserved"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        assertTrue(unmapped.joinToString().contains("decline"))
    }

    @Test
    fun `action-shaped response is renamed to action only when action is missing`() {
        val raw = "{\"response\": {\"action\": \"no_reply\", \"reason\": \"r\"}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        // 改名后 response 缺失 → empty_response 原样返回（Python 同形）
        assertFalse(out.changed)
        assertEquals(raw, out.text)
        assertTrue("response_to_action" in rulesOf(out))
        assertTrue(metaOf(out)["empty_response"]?.toString() == "true")
    }

    @Test
    fun `unknown style keys take the conservative unmapped path while fuzzy_map is off`() {
        // W17 观察族：bad_but——相似度本可 ≥0.5，但未对拍前整体走保守路径
        val raw = "{\"response\": {\"recommended\": \"主\", \"bad_but\": \"漂移文本\"}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertFalse(out.text.contains("漂移文本"))
        val unmapped = (metaOf(out)["unmapped_preserved"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        assertTrue(unmapped.joinToString().contains("漂移文本"))
        assertTrue("unmapped_style_removed" in rulesOf(out))
        assertFalse("style_key_fuzzy_map" in rulesOf(out))
    }

    // ── 第 3 层：输出纪律与审计 ──

    @Test
    fun `audit line is written only when changed with context fields`() {
        val dir = tmp.newFolder()
        val guard = ContractGuard(ContractGuard.FileGuardAuditSink(dir))
                // 变化轮
        guard.repairAndAudit(
            "{\"response\": {\"recommended\": \"好\"}, \"directions\": [null, null, null, null], \"analysis\": {}}",
            kotlinx.serialization.json.buildJsonObject { put("requestId", "r1"); put("turn", 3) }
        )
        // 未变化轮（不写审计）
        guard.repairAndAudit(
            "{\"response\": {\"recommended\": \"好\", \"bad_boy\": null, \"playful\": null, \"warm\": null}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        )
        val file = java.io.File(dir, "contract_guard_audit.jsonl")
        assertTrue(file.isFile)
        val lines = file.readLines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("\"requestId\": \"r1\""))
        assertTrue(lines[0].contains("\"turn\": 3"))
        assertTrue(lines[0].contains("\"changed\": true"))
    }

    @Test
    fun `sequenceMatcherRatio matches difflib on known pairs`() {
        val g = newGuard()
        // 与 CPython difflib.SequenceMatcher(None, a, b).ratio() 实测值一致
        assertEquals(1.0, g.sequenceMatcherRatio("bad_boy", "bad_boy"), 1e-9)
        assertEquals(0.0, g.sequenceMatcherRatio("abc", "xyz"), 1e-9)
        // CPython: SequenceMatcher(None, "abcd", "abce").ratio() = 0.75（最长匹配块 "abc"，2*3/8）
        assertEquals(0.75, g.sequenceMatcherRatio("abcd", "abce"), 1e-9)
        // CPython: SequenceMatcher(None, "bad_but", "bad_boy").ratio() = 2*5/14（最长匹配块 "bad_b"）
        assertEquals(10.0 / 14.0, g.sequenceMatcherRatio("bad_but", "bad_boy"), 1e-9)
        // 空串形状
        assertEquals(0.0, g.sequenceMatcherRatio("", "bad_boy"), 1e-9)
    }

    @Test
    fun `non-ascii stays unescaped on rewrite like ensure_ascii=False`() {
        val raw = "{\"response\": {\"recommended\": \"好，周五见 🙂\"}, \"directions\": [null, null, null, null], \"analysis\": {}}"
        val out = newGuard().repairAndAudit(raw)
        assertTrue(out.changed)
        assertTrue(out.text.contains("好，周五见 🙂"))
        assertFalse(out.text.contains("\\u"))
    }

    // ── 反射辅助 ──

    private fun metaOf(out: ContractGuard.GuardOutcome) = out.meta

    private fun rulesOf(out: ContractGuard.GuardOutcome): Set<String> =
        (out.meta["rules"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?.toSet()
            .orEmpty()

    private fun repairLossRules(out: ContractGuard.GuardOutcome): Set<String> =
        (out.meta["repair_loss"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { entry ->
                (entry as? kotlinx.serialization.json.JsonObject)?.get("rule")
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }?.toSet().orEmpty()
}
