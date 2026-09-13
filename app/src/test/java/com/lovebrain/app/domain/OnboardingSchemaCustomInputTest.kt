package com.lovebrain.app.domain

import androidx.compose.runtime.mutableStateMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v4.2 多选问卷架构契约单测：
 *
 * 覆盖：
 * - Q1 只能存在一个 selectedIndex（SINGLE）
 * - MULTIPLE 可以存在多个 selectedIndex
 * - 达到 maxSelections 后不能继续增加
 * - 再次点击已选项可以取消
 * - Q1 改变后 Q2-Q5 清空
 * - 多选所有 tag 都进入 schema
 * - profile 正确用"；"拼接多个答案
 * - customText 可以和 fixed option 共存
 * - customText 不产生伪 tag
 * - 任意 selected redline option 会触发 redline
 * - 红线激活后 Q5 隐藏选项被清理
 * - 取消红线后 Q5 可再次选择普通项
 * - total_answered 是答题数而非选项数
 */
class OnboardingSchemaCustomInputTest {

    // ═══ 辅助 ═══

    /** 分支 B（Q1 选 B）、Q2-Q5 各选一个的基础答案 */
    private fun fixedAnswersB(): Map<Int, OnboardingAnswer> = mapOf(
        1 to OnboardingAnswer(selectedIndices = setOf(1)),
        2 to OnboardingAnswer(selectedIndices = setOf(0)),
        3 to OnboardingAnswer(selectedIndices = setOf(1)),
        4 to OnboardingAnswer(selectedIndices = setOf(2)),
        5 to OnboardingAnswer(selectedIndices = setOf(0))
    )

    /** 分支 E（Q1 选 E）基础答案 */
    private fun fixedAnswersE(): Map<Int, OnboardingAnswer> = mapOf(
        1 to OnboardingAnswer(selectedIndices = setOf(4)),
        2 to OnboardingAnswer(selectedIndices = setOf(0)),
        3 to OnboardingAnswer(selectedIndices = setOf(0)),
        4 to OnboardingAnswer(selectedIndices = setOf(1)),
        5 to OnboardingAnswer(selectedIndices = setOf(2))
    )

    // ═══ Q1 SINGLE：只能有一个 selectedIndex ═══

    @Test
    fun `Q1 single selection only allows one index`() {
        val q1 = OnboardingBank.q1
        assertEquals(SelectionMode.SINGLE, q1.selectionMode)

        // 选 index 2
        val a1 = OnboardingStateMachine.toggleOption(q1, OnboardingAnswer(), 2)
        assertEquals(setOf(2), a1.selectedIndices)

        // 改选 index 0 → 替换，不是添加
        val a2 = OnboardingStateMachine.toggleOption(q1, a1, 0)
        assertEquals(setOf(0), a2.selectedIndices)
    }

    // ═══ MULTIPLE：可以存在多个 selectedIndex ═══

    @Test
    fun `multiple selection allows two indices`() {
        val q4b = OnboardingBank.question(4, "B")
        assertEquals(SelectionMode.MULTIPLE, q4b.selectionMode)

        var answer = OnboardingAnswer()
        answer = OnboardingStateMachine.toggleOption(q4b, answer, 0)
        assertEquals(setOf(0), answer.selectedIndices)

        answer = OnboardingStateMachine.toggleOption(q4b, answer, 1)
        assertEquals(setOf(0, 1), answer.selectedIndices)
    }

    // ═══ 达到 maxSelections 后不能继续增加 ═══

    @Test
    fun `max selections prevents adding beyond limit`() {
        val q4b = OnboardingBank.question(4, "B")
        // maxSelections = 2
        assertEquals(2, q4b.maxSelections)

        var answer = OnboardingAnswer()
        answer = OnboardingStateMachine.toggleOption(q4b, answer, 0)
        answer = OnboardingStateMachine.toggleOption(q4b, answer, 1)
        // 已达 2 项，尝试加第三项
        val before = answer.selectedIndices
        answer = OnboardingStateMachine.toggleOption(q4b, answer, 2)
        assertEquals(before, answer.selectedIndices)
    }

    // ═══ 再次点击已选项可以取消 ═══

    @Test
    fun `toggling selected item deselects it`() {
        val q4b = OnboardingBank.question(4, "B")

        var answer = OnboardingAnswer()
        answer = OnboardingStateMachine.toggleOption(q4b, answer, 0)
        answer = OnboardingStateMachine.toggleOption(q4b, answer, 1)
        assertTrue(answer.selectedIndices.contains(0))
        assertTrue(answer.selectedIndices.contains(1))

        // 取消 0
        answer = OnboardingStateMachine.toggleOption(q4b, answer, 0)
        assertFalse(answer.selectedIndices.contains(0))
        assertTrue(answer.selectedIndices.contains(1))
    }

    // ═══ Q1 改变后 Q2-Q5 清空 ═══

    @Test
    fun `Q1 change clears downstream answers`() {
        val answers = mutableStateMapOf<Int, OnboardingAnswer>()
        // 先填充分支 B 的完整答案
        answers[1] = OnboardingAnswer(selectedIndices = setOf(1)) // B
        answers[2] = OnboardingAnswer(selectedIndices = setOf(0))
        answers[3] = OnboardingAnswer(selectedIndices = setOf(1))
        answers[4] = OnboardingAnswer(selectedIndices = setOf(2))
        answers[5] = OnboardingAnswer(selectedIndices = setOf(0))

        // 改 Q1 → E
        OnboardingStateMachine.clearDownstreamAnswers(answers)

        // Q1 保留，Q2-Q5 全清
        assertTrue(answers.containsKey(1))
        assertFalse(answers.containsKey(2))
        assertFalse(answers.containsKey(3))
        assertFalse(answers.containsKey(4))
        assertFalse(answers.containsKey(5))
    }

    // ═══ 多选所有 tag 都进入 schema ═══

    @Test
    fun `multiple selections all contribute tags`() {
        // 分支 B，Q4 选 0+1（两个 tag）
        val answers = fixedAnswersB() + (
            4 to OnboardingAnswer(selectedIndices = setOf(0, 1))
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")

        // Q4 的两个 tag 都应该在
        val q4b = OnboardingBank.question(4, "B")
        assertTrue(schema.tags.contains(q4b.options[0].tag))
        assertTrue(schema.tags.contains(q4b.options[1].tag))
    }

    // ═══ profile 正确用"；"拼接多个答案 ═══

    @Test
    fun `profile joins multiple selections with semicolon`() {
        val answers = fixedAnswersB() + (
            4 to OnboardingAnswer(selectedIndices = setOf(0, 1))
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")

        val q4b = OnboardingBank.question(4, "B")
        val expected = listOf(q4b.options[0].text, q4b.options[1].text).joinToString("；")
        assertEquals(expected, schema.profile.core_dilemma)
    }

    // ═══ customText 可以和 fixed option 共存 ═══

    @Test
    fun `customText coexists with fixed selections in profile`() {
        val answers = fixedAnswersB() + (
            4 to OnboardingAnswer(
                selectedIndices = setOf(0, 2),
                customText = "但见面时她又挺主动"
            )
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")

        val q4b = OnboardingBank.question(4, "B")
        val expected = listOf(
            q4b.options[0].text,
            q4b.options[2].text,
            "补充：但见面时她又挺主动"
        ).joinToString("；")
        assertEquals(expected, schema.profile.core_dilemma)
    }

    // ═══ customText 不产生伪 tag ═══

    @Test
    fun `customText does not generate fake tags`() {
        val answers = fixedAnswersB() + (
            4 to OnboardingAnswer(
                selectedIndices = setOf(0),
                customText = "我还特别怕自己情绪上头"
            )
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")

        // Q4 只选了 1 个固定选项 → 1 个 tag
        val q4b = OnboardingBank.question(4, "B")
        assertEquals(4, schema.tags.size) // Q2-Q5 各 1 个 tag
        assertTrue(schema.tags.contains(q4b.options[0].tag))
        // customText 不应出现在 tags 中
        assertFalse(schema.tags.any { it.contains("情绪") || it.contains("上头") })
    }

    // ═══ 任意 selected redline option 会触发 redline ═══

    @Test
    fun `any selected redline option triggers redline`() {
        // 分支 E，Q3 选 0（普通）+ 3（拉黑，isRedline=true）
        val answers = fixedAnswersE() + (
            3 to OnboardingAnswer(selectedIndices = setOf(0, 3))
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")
        assertTrue(schema.redline_triggered)
        assertEquals("SELF_REBUILD_ONLY", schema.system_directive)
    }

    @Test
    fun `redline triggers with Q4 redline option selected`() {
        // 分支 E，Q4 选 0（明确拒绝，isRedline=true）
        val answers = fixedAnswersE() + (
            4 to OnboardingAnswer(selectedIndices = setOf(0))
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")
        assertTrue(schema.redline_triggered)
    }

    @Test
    fun `no redline when no redline option selected in branch E`() {
        // 分支 E，Q3 全选普通项，Q4 全选普通项
        val answers = fixedAnswersE() + mapOf(
            3 to OnboardingAnswer(selectedIndices = setOf(0, 1)),
            4 to OnboardingAnswer(selectedIndices = setOf(1, 2))
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")
        assertFalse(schema.redline_triggered)
        assertEquals("NORMAL_ASSIST", schema.system_directive)
    }

    @Test
    fun `non-E branch never triggers redline`() {
        val answers = fixedAnswersB()
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")
        assertFalse(schema.redline_triggered)
    }

    // ═══ 红线激活后 Q5 隐藏选项被清理 ═══

    @Test
    fun `redline activation cleans hidden Q5 options`() {
        val answers = mutableStateMapOf<Int, OnboardingAnswer>()
        // 分支 E，Q3 选拉黑（index 3, redline）
        answers[1] = OnboardingAnswer(selectedIndices = setOf(4)) // E
        answers[3] = OnboardingAnswer(selectedIndices = setOf(3)) // 拉黑 → redline
        // Q5 先选了 0 和 2
        answers[5] = OnboardingAnswer(selectedIndices = setOf(0, 2))

        // 触发红线 → 隐藏 0 和 1
        val hidden = OnboardingStateMachine.hiddenOptionIndices(true)
        OnboardingStateMachine.cleanHiddenFromQ5(answers, hidden)

        // Q5 的 0 被清理，2 保留
        assertEquals(setOf(2), answers[5]?.selectedIndices)
    }

    // ═══ 取消红线后 Q5 可再次选择普通项 ═══

    @Test
    fun `after redline cancelled Q5 can select normal options again`() {
        val answers = mutableStateMapOf<Int, OnboardingAnswer>()
        answers[1] = OnboardingAnswer(selectedIndices = setOf(4)) // E

        // 先触发红线（Q3 选拉黑）
        answers[3] = OnboardingAnswer(selectedIndices = setOf(3))
        assertTrue(OnboardingStateMachine.isRedlineTriggered(answers, "E"))

        // 取消红线（Q3 改为选普通项 0）
        answers[3] = OnboardingAnswer(selectedIndices = setOf(0))
        assertFalse(OnboardingStateMachine.isRedlineTriggered(answers, "E"))

        // Q5 现在可以正常选 0（之前被隐藏的项）
        val q5e = OnboardingBank.question(5, "E")
        var q5Answer = OnboardingAnswer()
        q5Answer = OnboardingStateMachine.toggleOption(q5e, q5Answer, 0)
        assertEquals(setOf(0), q5Answer.selectedIndices)
    }

    // ═══ total_answered 是答题数而非选项数 ═══

    @Test
    fun `total_answered counts questions not selections`() {
        // Q1 选1个，Q2 选2个，Q3 选2个，Q4 选2个，Q5 选2个
        val answers = mapOf(
            1 to OnboardingAnswer(selectedIndices = setOf(1)), // B
            2 to OnboardingAnswer(selectedIndices = setOf(0, 1)),
            3 to OnboardingAnswer(selectedIndices = setOf(0, 1)),
            4 to OnboardingAnswer(selectedIndices = setOf(0, 1)),
            5 to OnboardingAnswer(selectedIndices = setOf(0, 1))
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")

        // 5 道题全答 → total_answered = 5（不是 9）
        assertEquals(5, schema.meta.total_answered)
    }

    // ═══ customText 单独可满足 MULTIPLE 答题条件 ═══

    @Test
    fun `customText alone satisfies multiple question answered`() {
        val q4b = OnboardingBank.question(4, "B")
        val answer = OnboardingAnswer(customText = "我自己说的情况")
        assertTrue(answer.isAnswered(q4b))
    }

    @Test
    fun `empty answer does not satisfy answered`() {
        val q4b = OnboardingBank.question(4, "B")
        val empty = OnboardingAnswer()
        assertFalse(empty.isAnswered(q4b))
    }

    @Test
    fun `single selection answer satisfies single question`() {
        val q1 = OnboardingBank.q1
        val answer = OnboardingAnswer(selectedIndices = setOf(0))
        assertTrue(answer.isAnswered(q1))
    }

    @Test
    fun `single selection with two indices does not satisfy single question`() {
        val q1 = OnboardingBank.q1
        // SINGLE 模式不应该有两个选择，但测试防御性
        val answer = OnboardingAnswer(selectedIndices = setOf(0, 1))
        assertFalse(answer.isAnswered(q1))
    }

    // ═══ profile 拼接不使用 toString() 集合样式 ═══

    @Test
    fun `profile does not use kotlin collection toString`() {
        val answers = fixedAnswersB() + (
            4 to OnboardingAnswer(selectedIndices = setOf(0, 1))
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")

        // 不应包含 [ 或 ] 或 Kotlin Set toString 样式
        assertFalse(schema.profile.core_dilemma.contains("["))
        assertFalse(schema.profile.core_dilemma.contains("]"))
    }

    // ═══ tags 去重 ═══

    @Test
    fun `tags are distinct`() {
        val answers = fixedAnswersB()
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")
        assertEquals(schema.tags.size, schema.tags.distinct().size)
    }

    // ═══ 缺失答案不崩 ═══

    @Test
    fun `missing answers yield empty profile fields`() {
        val answers = mapOf(
            1 to OnboardingAnswer(selectedIndices = setOf(1)) // 只答了 Q1
        )
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")
        assertEquals("", schema.profile.interpersonal_context)
        assertEquals("", schema.profile.counterpart_feedback)
        assertEquals("", schema.profile.core_dilemma)
        assertEquals("", schema.profile.user_intent)
    }

    // ═══ ONB-02：双方称呼进入 schema ═══

    @Test
    fun `schema carries user supplied names`() {
        val schema = OnboardingSchemaBuilder.build(
            answers = fixedAnswersB(),
            myName = "小明",
            herName = "小雨"
        )

        assertEquals("小明", schema.names.self)
        assertEquals("小雨", schema.names.counterpart)
    }

    @Test
    fun `blank names remain blank`() {
        val schema = OnboardingSchemaBuilder.build(
            answers = fixedAnswersB(),
            myName = "   ",
            herName = ""
        )

        assertEquals("", schema.names.self)
        assertEquals("", schema.names.counterpart)
    }
}
