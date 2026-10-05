package com.lovebrain.app.domain

import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.SchemeIdentity
import com.lovebrain.app.model.SchemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 单条方案正文的读与改（`ReplyPatch`）。
 *
 * 风格的四条卡在存储层是 `ReplySchemes` 的四个字段（A/B/C/D 按**位置**对应），
 * 方向的四条卡在 `directions` 列表里按 index 对应、null 表示"本轮不适合"。
 * 这两套位置映射原先在 ViewModel 里抄了两遍，改一遍漏一遍就是
 * "撤回归挡 B，屏上变的是 C"。这里把映射钉死。
 */
class ReplyPatchTest {

    private val style = SchemeSource.STYLE
    private val dir = SchemeSource.DIRECTION

    private fun response(
        schemes: ReplySchemes = ReplySchemes(recommended = "A", badBoy = "B", playful = "C", warm = "D"),
        directions: List<String?> = listOf("F", "E", "X", "S")
    ) = LoveBrainResponse(
        response = schemes,
        directions = directions,
        analysis = ReplyAnalysis(topic_status = "same", topic_label = "原分析")
    )

    // ─── 读 ──────────────────────────────────────────────────────

    @Test
    fun `style tags map to the four fields by position`() {
        val r = response()
        assertEquals("A", ReplyPatch.textOf(r, SchemeIdentity(style, "A")))
        assertEquals("B", ReplyPatch.textOf(r, SchemeIdentity(style, "B")))
        assertEquals("C", ReplyPatch.textOf(r, SchemeIdentity(style, "C")))
        assertEquals("D", ReplyPatch.textOf(r, SchemeIdentity(style, "D")))
    }

    @Test
    fun `direction tags map by index and an unknown tag reads nothing`() {
        val r = response()
        assertEquals("F", ReplyPatch.textOf(r, SchemeIdentity(dir, "F")))
        assertEquals("S", ReplyPatch.textOf(r, SchemeIdentity(dir, "S")))
        assertNull(ReplyPatch.textOf(r, SchemeIdentity(dir, "Q")))
    }

    // ─── 改 ──────────────────────────────────────────────────────

    @Test
    fun `patching one style card leaves the other three and every direction intact`() {
        val r = response()
        val patched = ReplyPatch.withText(r, SchemeIdentity(style, "C"), newReply = "新俏皮")

        val s = patched.response
        assertEquals("A", s.recommended)
        assertEquals("B", s.badBoy)
        assertEquals("新俏皮", s.playful)
        assertEquals("D", s.warm)
        assertEquals(r.directions, patched.directions)
        assertEquals("分析段不该被改写顺手动过", r.analysis, patched.analysis)
    }

    @Test
    fun `the four tags cannot be shifted relative to their fields`() {
        // 谁把位置映射改错（比如 B 写成 playful），这条就会红
        val r = response()
        val byTag = listOf("A", "B", "C", "D").associateWith { tag ->
            ReplyPatch.withText(r, SchemeIdentity(style, tag), "X").response
        }
        assertEquals("X", byTag["A"]?.recommended)
        assertEquals("X", byTag["B"]?.badBoy)
        assertEquals("X", byTag["C"]?.playful)
        assertEquals("X", byTag["D"]?.warm)
        for ((tag, schemes) in byTag) {
            assertEquals("$tag 改到了别的字段", 1, listOf(
                schemes.recommended, schemes.badBoy, schemes.playful, schemes.warm
            ).count { it == "X" })
        }
    }

    @Test
    fun `patching a direction pads only up to the target slot`() {
        val r = response(directions = listOf("F"))
        val patched = ReplyPatch.withText(r, SchemeIdentity(dir, "X"), newReply = "表达")

        // 只补到目标位：后面的空位不提前造出来（读的时候按 index 取，缺位等于"本轮不适合"）
        assertEquals(listOf("F", null, "表达"), patched.directions)
        assertEquals("A", patched.response.recommended)
        // 缺位读出来是空正文，不是崩溃也不是"有一条空卡"
        assertEquals("", ReplyPatch.textOf(patched, SchemeIdentity(dir, "S")))
    }

    @Test
    fun `an unknown direction tag returns the same instance instead of throwing`() {
        val r = response()
        val same = ReplyPatch.withText(r, SchemeIdentity(dir, "Q"), newReply = "不该被写进去")
        assertSame(r, same)
        assertEquals(r, same)
    }

    @Test
    fun `style and direction cards with the same position never touch each other`() {
        val r = response()
        val stylePatched = ReplyPatch.withText(r, SchemeIdentity(style, "A"), newReply = "风格改过")
        val dirPatched = ReplyPatch.withText(r, SchemeIdentity(dir, "F"), newReply = "方向改过")

        assertEquals("风格改过", stylePatched.response.recommended)
        assertEquals("F", stylePatched.directions.first())
        assertEquals("A", dirPatched.response.recommended)
        assertEquals("方向改过", dirPatched.directions.first())
    }

    @Test
    fun `patch then read is a round trip`() {
        val r = response()
        val identity = SchemeIdentity(dir, "E")
        assertEquals(
            "新的",
            ReplyPatch.textOf(ReplyPatch.withText(r, identity, "新的"), identity)
        )
    }

    @Test
    fun `writing a blank reply reads back blank, not missing`() {
        val patched = ReplyPatch.withText(response(), SchemeIdentity(style, "B"), newReply = "")
        assertEquals("", ReplyPatch.textOf(patched, SchemeIdentity(style, "B")))
    }
}
