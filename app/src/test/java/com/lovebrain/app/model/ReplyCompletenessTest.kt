package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 八项回复完整性检查（§11.2）——四风格 + 四方向 = 八项。
 *
 * 指导书要求：
 * - 正常场景应完整生成四风格和四方向
 * - 四风格缺失/空白、方向字段缺失都属于生成不完整，不能冒充"不适合"
 * - 不要把一条正文复制成多个标签凑数
 * - 不能静默隐藏，不能画空卡后报"八条完整回复"
 *
 * 这里钉的是 [LoveBrainResponse.replyCompleteness] 那颗纯函数属性：
 * 它区分 Complete / Partial / Duplicated / Empty 四态，
 * 解析层用它记录、UI 层的空卡仍显示"本轮不适合"——两件事各管各的。
 */
class ReplyCompletenessTest {

    private fun response(
        recommended: String = "推荐",
        badBoy: String = "清醒",
        playful: String = "俏皮",
        warm: String = "温柔",
        directions: List<String?> = listOf("跟进", "展开", "表达", "转向")
    ) = LoveBrainResponse(
        response = ReplySchemes(
            recommended = recommended,
            badBoy = badBoy,
            playful = playful,
            warm = warm
        ),
        directions = directions
    )

    /** 八项全有非空内容 → Complete */
    @Test
    fun `eight non-empty items yield Complete`() {
        val r = response()
        assertEquals(ReplyCompleteness.Complete, r.replyCompleteness)
    }

    /** 缺一个方向 → Partial，缺失项标题列出 */
    @Test
    fun `a missing direction yields Partial with the missing label`() {
        val r = response(directions = listOf("跟进", null, "表达", "转向"))
        val partial = r.replyCompleteness
        assertTrue("缺一个方向应是 Partial，实到 $partial", partial is ReplyCompleteness.Partial)
        assertEquals(listOf("展开"), (partial as ReplyCompleteness.Partial).missingLabels)
    }

    /** 缺一个风格 + 缺一个方向 → Partial，两个缺失项都列出 */
    @Test
    fun `a missing style and a missing direction both appear in missing labels`() {
        val r = response(
            badBoy = "",
            directions = listOf("跟进", "展开", "   ", "转向")
        )
        val partial = r.replyCompleteness
        assertTrue(partial is ReplyCompleteness.Partial)
        val missing = (partial as ReplyCompleteness.Partial).missingLabels
        assertTrue("缺失项应包含'清醒'", missing.contains("清醒"))
        assertTrue("缺失项应包含'表达'", missing.contains("表达"))
    }

    /** 两条正文相同 → Duplicated，即使八项都有内容 */
    @Test
    fun `duplicated texts across labels yield Duplicated even when all eight are non-empty`() {
        val r = response(
            recommended = "同样的话",
            badBoy = "同样的话",
            playful = "俏皮",
            warm = "温柔",
            directions = listOf("跟进", "展开", "表达", "转向")
        )
        val dup = r.replyCompleteness
        assertTrue("两条相同正文应是 Duplicated，实到 $dup", dup is ReplyCompleteness.Duplicated)
        val duplicated = (dup as ReplyCompleteness.Duplicated).duplicatedTexts
        assertTrue("重复正文应包含'同样的话'", duplicated.contains("同样的话"))
    }

    /** 全空 → Empty */
    @Test
    fun `all empty yields Empty`() {
        val r = response(
            recommended = "", badBoy = "", playful = "", warm = "",
            directions = listOf(null, null, null, null)
        )
        assertEquals(ReplyCompleteness.Empty, r.replyCompleteness)
    }

    /** 只有空白串也算空 */
    @Test
    fun `blank-only strings count as empty`() {
        val r = response(
            recommended = "  ", badBoy = "", playful = "  ", warm = "",
            directions = listOf("  ", null, "", "  ")
        )
        assertEquals(ReplyCompleteness.Empty, r.replyCompleteness)
    }

    /** directions 列表比四短 → 缺失的位置算 Partial */
    @Test
    fun `a short directions list yields Partial for the missing positions`() {
        val r = response(directions = listOf("跟进", "展开"))
        val partial = r.replyCompleteness
        assertTrue(partial is ReplyCompleteness.Partial)
        val missing = (partial as ReplyCompleteness.Partial).missingLabels
        assertTrue("缺失项应包含'表达'", missing.contains("表达"))
        assertTrue("缺失项应包含'转向'", missing.contains("转向"))
    }
}
