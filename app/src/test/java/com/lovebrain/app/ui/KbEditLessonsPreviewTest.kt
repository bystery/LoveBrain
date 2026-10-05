package com.lovebrain.app.ui

import com.lovebrain.app.domain.LessonDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「模板既不进界面，也不进 prompt」在**界面那一头**的钉子：编辑页预览的经验格必须走
 * [LessonDoc.purify] 这颗**共用的**净化口，而不是在 UI 里再洗一遍。
 *
 * 为什么值得单独一格：`KbEditActivity` 里对别的文件用的是 `stripHtmlComments`（去注释），
 * 只有经验格换成 purify。这句"两边共用同一颗函数"如果只是注释里的一句话，
 * 下一轮有人把它改回去（各洗各的）就不会有任何东西变红——那正是这轮用户撞上的形状：
 * 界面把模板藏好了，AI 还在照着模板读。
 */
class KbEditLessonsPreviewTest {

    /** 界面预览与 prompt 都会读到的那份脏档：seed 模板 + 两批真实经验 + 模型抄的字段说明 */
    private val laden = """
        <!-- 经验（军师自动提取，每5个话题转换触发一次）。
        -->

        # 经验库

        军师自动追加提取节，节头格式：# [yyyy-MM-dd HH:mm] 第N次提取（模板内不放示例节）

        ## 测试接法
        - 测试：行为；接法：有效回复；禁用：无效回复；内核：她真正需求

        # [2026-09-17 10:00] 第1次提取

        # [2026-09-17 10:00] 第1次提取

        ## 做对的
        - 做对：她抱怨工作时只接情绪；有效：她情绪明显好转；复用：她抱怨任何事时

        ### 字段说明
        **字段说明**：

        # [2026-09-17 11:20] 第2次提取

        ## 踩过的坑
        - 踩坑：急着给建议；教训：先听完；补救：补一句「我再说」
        """.trimIndent()

    /**
     * 经验格的预览 == `LessonDoc.purify`，逐字相等。
     *
     * 反例（这一句就是为了它写的）：把 `prettyForPreview` 里 `path == LESSONS_PATH` 那个分支删掉、
     * 让经验格也走 `stripHtmlComments` —— 两边输出立刻不等，这一格红；
     * 或者在 UI 里另写一颗净化（哪怕结果暂时一样，只要不是同一颗函数的返回）也红。
     */
    @Test
    fun lessonsPreviewIsTheVeryOutputOfTheSharedSanitizer() {
        assertEquals(
            "预览必须原样交出共用净化口那颗函数的输出",
            LessonDoc.purify(laden),
            prettyForPreview(LessonDoc.LESSONS_PATH, laden)
        )
    }

    /**
     * 去注释这条路**洗不掉**模板——正向对照，证明上一格绿不是因为"随便哪条路都能洗干净"。
     *
     * 反例：若有人把 purify 换成"只去 HTML 注释"，这句会跟着一起红（因为经验格与非经验格会输出同一份
     * 含模板的文本，`assertNotEquals` 那一行先炸）。
     */
    @Test
    fun commentStrippingAloneDoesNotHideTheTemplate() {
        val viaOtherPath = prettyForPreview("moment/recent.md", laden)
        val viaLessonsPath = prettyForPreview(LessonDoc.LESSONS_PATH, laden)

        assertNotEquals(
            "经验格与非经验格本该走不同的净化口，输出一样了就是共用口丢了",
            viaOtherPath, viaLessonsPath
        )
        assertTrue("别的格子仍按原样去注释即可（这一格不许顺手改语义）", !viaOtherPath.contains("<!--"))
        assertTrue("走非经验那条路时模板行仍然在场（去注释办不到这件事）", viaOtherPath.contains("# 经验库"))
    }

    /**
     * 界面拿到的那份文本里没有任何模板行，但真实经验一句不少。
     *
     * 反例：`TEMPLATE_LINES` 少登记一行、或 `prettyForPreview` 传错 path（比如把 path 判据写成
     * 内容里含「经验」二字），下面这两组断言各管一头：少剥→第一组红；错剥正文→第二组红。
     */
    @Test
    fun previewShowsRealLinesAndNoTemplateLines() {
        val shown = prettyForPreview(LessonDoc.LESSONS_PATH, laden)

        (LessonDoc.PROMPT_COPIED_TEMPLATE_LINES + LessonDoc.LEGACY_TEMPLATE_LINES).forEach {
            assertFalse("预览里还留着模板行：$it", shown.contains(it))
        }
        assertFalse("预览里不该有 HTML 注释", shown.contains("<!--"))
        assertTrue(shown.contains("- 做对：她抱怨工作时只接情绪；有效：她情绪明显好转；复用：她抱怨任何事时"))
        assertTrue(shown.contains("- 踩坑：急着给建议；教训：先听完；补救：补一句「我再说」"))
        assertEquals("屏幕上就该看见收敛后的两条节头（一、二），不是四对",
            2, Regex("""(?m)^# \[""" ).findAll(shown).count())
    }

    /**
     * 非经验格的行为一字不变（这一轮只该动经验那一格）。
     *
     * 反例：把 purify 误接到所有 path 上 → plan.md 那张 `|` 表被当正文吞进批里，这一句红。
     */
    @Test
    fun otherFilesKeepTheirOwnPreviewShape() {
        val plan = "# 进行中\n- 看房 | 进行中 | [09-15] 看房→[09-20] 比价"
        assertEquals("非 plan 的非经验格只做去注释", plan, prettyForPreview("moment/topic.md", plan))
        val prettified = prettyForPreview("moment/plan.md", plan)
        assertTrue("plan 那格仍走自己的表格格式化", prettified.contains("- 看房 · 进行中"))
    }
}
