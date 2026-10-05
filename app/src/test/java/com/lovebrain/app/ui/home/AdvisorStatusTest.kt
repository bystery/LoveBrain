package com.lovebrain.app.ui.home

import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.Success
import com.lovebrain.app.core.designsystem.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页那一格的**渲染派生表**（ 的"统一派生渲染"那一半）。
 *
 * 判的是：一份 [AdvisorStatus] 进来，灯、控件、两个读屏名字、那一行黄字一次定完，
 * 而且同一份状态永远派生出同一套产物——搬家之前这里是五个平行 `when`
 * （statusText / statusColor / description / buttonText / buttonAction），
 * 五份判据各说各话，加一档状态时最容易忘的恰恰是"颜色那一份"。
 *
 * 事实 → 状态那一半在 `HomeStatusViewModelTest` 的矩阵里钉；这里只钉状态 → 产物。
 */
class AdvisorStatusTest {

    private val allStates = AdvisorState.values().toList()

    /** 一格 = 一份状态 + 它该派生出的五件产物（期望值手写，不让判据自己算自己） */
    private data class Row(
        val status: AdvisorStatus,
        val lamp: AdvisorLamp,
        val control: AdvisorControl,
        val hint: String?,
        val controlDescription: String
    )

    private val rows: List<Row> = listOf(
        Row(AdvisorStatus(AdvisorState.Stopped), AdvisorLamp.Red, AdvisorControl.Play, null, HOME_A11Y_PLAY),
        Row(AdvisorStatus(AdvisorState.Checking), AdvisorLamp.Yellow, AdvisorControl.Stop, HOME_CHECKING_HINT, HOME_A11Y_STOP),
        Row(AdvisorStatus(AdvisorState.RunningReady), AdvisorLamp.Green, AdvisorControl.Stop, null, HOME_A11Y_STOP),
        Row(
            AdvisorStatus(AdvisorState.RunningNeedsSetup, listOf(AdvisorMissing.NoProvider)),
            AdvisorLamp.Yellow, AdvisorControl.Stop, "未配置模型供应商", HOME_A11Y_STOP
        ),
        Row(
            AdvisorStatus(
                AdvisorState.RunningNeedsSetup,
                listOf(AdvisorMissing.OverlayPermission, AdvisorMissing.NoKnowledgeBase)
            ),
            AdvisorLamp.Yellow, AdvisorControl.Stop, "需要悬浮窗权限 · 请为当前对象建立知识库", HOME_A11Y_STOP
        )
    )

    @Test
    fun `the matrix walks every state and every lamp twice`() {
        assertTrue("矩阵只剩 ${rows.size} 行：有分支没人摆了", rows.size >= 5)
        // 哨兵①：四档状态都被走到，否则"逐行比对"可以是空的还报绿
        assertEquals("四档都要出现", allStates.toSet(), rows.map { it.status.state }.toSet())
        // 哨兵②：黄档必须被**两组以上**输入走过（检查中 / 一种缺项 / 多种缺项）——
        // 只有一组输入走到的档，拦不住"黄灯配了绿字"这类脱钩
        assertEquals(
            "黄灯要有多组输入来对照", 3, rows.count { it.lamp == AdvisorLamp.Yellow }
        )
        assertEquals("三档灯都出现过", AdvisorLamp.values().toSet(), rows.map { it.lamp }.toSet())
    }

    /**
     * 逐行比对五件产物。
     * 反例：把黄档的控件写成 ▶（灯与按钮各判各的）、或 RunningNeedsSetup 忘了带黄字（灯黄着却不说话）。
     */
    @Test
    fun `each state renders exactly the registered products`() {
        rows.forEach { row ->
            val r = row.status.render()
            assertEquals("${row.status.state}：灯", row.lamp, r.lamp)
            assertEquals("${row.status.state}：控件", row.control, r.control)
            assertEquals("${row.status.state}：那一行", row.hint, r.hint)
            assertEquals("${row.status.state}：读屏说的动作", row.controlDescription, r.controlDescription)
            assertTrue(
                "${row.status.state}：读屏名字不许是空的（这一格不能靠字形让 TalkBack 猜）",
                r.controlDescription.isNotBlank() && r.lampDescription.isNotBlank()
            )
        }
    }

    /**
     * 一份状态只有一套产物：同一格摆两次、或同一档带不同缺项清单，
     * 灯与控件都不许跟着抖（缺项只改那一行字）。
     * 反例：`when` 里让黄字非空时把灯换成另一种颜色——那正是五个 when 会漏的事。
     */
    @Test
    fun `missing items only change the hint line, never the lamp or the control`() {
        val one = AdvisorStatus(AdvisorState.RunningNeedsSetup, listOf(AdvisorMissing.NoProvider)).render()
        val two = AdvisorStatus(
            AdvisorState.RunningNeedsSetup,
            listOf(AdvisorMissing.NoProvider, AdvisorMissing.ConnectionFailed)
        ).render()
        assertEquals(one.lamp, two.lamp)
        assertEquals(one.control, two.control)
        assertEquals(one.controlDescription, two.controlDescription)
        assertNotEquals("两条缺项必须都比一条缺项说得多，不是只念第一条", one.hint, two.hint)
        assertTrue("多条用短分隔，一行说完：" + two.hint, two.hint!!.contains(" · "))
    }

    /** 只有黄灯有话可说：红档与绿档都不许带常驻解释（合同"无常驻解释/无多余解释"） */
    @Test
    fun `red and green states carry no explanation line`() {
        assertNull(AdvisorStatus(AdvisorState.Stopped).render().hint)
        assertNull(AdvisorStatus(AdvisorState.RunningReady).render().hint)
        assertEquals(HOME_CHECKING_HINT, AdvisorStatus(AdvisorState.Checking).render().hint)
    }

    /**
     * 灯色仍然只有那三颗语义色，且与灯档一一对应。
     * 反例：`AdvisorLamp.color` 里绿档漏了、掉回默认色（用户看到"该绿的时候还是黄"）。
     */
    @Test
    fun `the three lamps are the three semantic colors`() {
        assertEquals(Error, AdvisorLamp.Red.color)
        assertEquals(Warning, AdvisorLamp.Yellow.color)
        assertEquals(Success, AdvisorLamp.Green.color)
        assertEquals(
            "三档不能撞成两个色", 3,
            listOf(AdvisorLamp.Red.color, AdvisorLamp.Yellow.color, AdvisorLamp.Green.color).distinct().size
        )
    }

    /**
     * 反向证人：`RunningNeedsSetup` 却给了空缺项时不崩、也不凭空念上一档的话。
     * 这一格存在的理由是那条组合期不许抛的纪律（判据在 viewmodel 那一侧保证非空）。
     */
    @Test
    fun `an empty missing list renders no line instead of throwing`() {
        val r = AdvisorStatus(AdvisorState.RunningNeedsSetup, emptyList()).render()
        assertEquals(AdvisorLamp.Yellow, r.lamp)
        assertNull(r.hint)
    }
}
