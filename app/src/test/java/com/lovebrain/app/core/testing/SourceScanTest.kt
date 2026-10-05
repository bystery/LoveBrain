package com.lovebrain.app.core.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **尺自己**的测试（不是生产代码的守卫）。
 *
 * 这一族尺在本仓库翻过三次车，每次都是"闸绿着而尺是瞎的"：
 * ①`[^)]*` 碰到 `if (x) A else B` 在右括号处断掉（坑表 95，表面色 25 其实是 45）；
 * ②不剥注释时 KDoc 里引用的旧形状会自己抵消一次真还债（坑表 88）；
 * ③只看 `clickable` 自己那条链 ⇒ "热区与视觉分两层"的形状整档看不见
 *   （`d0b6358` 之后 python 那把从 17 掉到 15，一处债都没还）。
 * ⇒ 判"某形状存在不存在"的尺，**每条判据都要有正向对照**（真该认出来的必须认出来），
 *   光有反向对照（不许把中性底认成品牌底）是不够的。
 */
class SourceScanTest {

    @Test
    fun `masking keeps length and line numbers`() {
        val src = """
            val a = 1 // Primary 在注释里不算
            /* Primary 也不算
               Box(modifier = Modifier.background(Primary)) */
            val url = "https://example.com/Primary"
            val b = Modifier.background(Primary)
        """.trimIndent()
        val masked = SourceScan.maskComments(src)
        assertEquals("掩码必须逐字符等长，否则行号与偏移全错", src.length, masked.length)
        assertEquals("换行数不能动", src.count { it == '\n' }, masked.count { it == '\n' })
        // 注释里的三处 `Primary` 都被掩掉了；字符串里那一处（URL）**必须还在**——
        // 把 `"https://…"` 里的 `//` 当行注释，就会从这一行开始把真代码掩掉，尺会漏认
        assertEquals(
            "掩码之后还剩两处 Primary（URL 里那处 + 真代码那处）：" + masked,
            2, Regex("Primary").findAll(masked).count()
        )
        assertEquals(
            "只有真正的代码里那一处 `.background(Primary)` 该被认出来",
            1, SourceScan.brandedBackgroundOffsets(masked).size
        )
        assertEquals(5, SourceScan.lineOf(masked, masked.indexOf("val b")))
    }

    @Test
    fun `nested block comments are masked to the right depth`() {
        val src = "val x = 1\n/* 外层 /* 内层 Primary */ 还是外层 */\nval y = Modifier.background(Primary)\n"
        val masked = SourceScan.maskComments(src)
        assertEquals(1, Regex("Primary").findAll(masked).count())
        assertEquals(1, SourceScan.brandedBackgroundOffsets(masked).size)
    }

    /** 正向对照①：条件涂色（旧 `[^)]*` 那把尺在这一档整档失明） */
    @Test
    fun `conditional brand background is counted`() {
        val code = "Box(modifier = Modifier.background(if (isSelected) Primary else SurfaceInset, shape))"
        assertEquals(1, SourceScan.brandedBackgroundOffsets(SourceScan.maskComments(code)).size)
    }

    /** 反向对照：中性底不许算成品牌底 */
    @Test
    fun `neutral background is not counted`() {
        val code = "Box(modifier = Modifier.background(SurfaceInset, shape))"
        assertEquals(0, SourceScan.brandedBackgroundOffsets(SourceScan.maskComments(code)).size)
    }

    /** 正向对照②：单层形状——可点与涂底在同一条链上 */
    @Test
    fun `single layer hand-drawn button is counted`() {
        val code = """
            Box(
                modifier = Modifier
                    .background(Primary, LoveBrainShape.md)
                    .clickable(role = Role.Button) { onAdd() }
            ) { Text("x") }
        """.trimIndent()
        assertEquals(1, SourceScan.actionableBranded(SourceScan.maskComments(code)).size)
    }

    /**
     * 正向对照③：**热区与视觉分两层**——可点在外层盒子，品牌底涂在里面的盒子上。
     *
     * 这一档就是 python 那把尺漏掉的形状（`ReplyInput` 的三颗 chip 与「添加」改成这样之后，
     * 它从 17 掉到 15）。这一格存在的意义就是说"不许再漏"。
     */
    @Test
    fun `two layer hand-drawn button is counted too`() {
        val code = """
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Tab, onClick = onClick)
                    .semantics { selected = true }
            ) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .background(if (selected) Primary else SurfaceInset, LoveBrainShape.md)
                ) { Text("她") }
            }
        """.trimIndent()
        val masked = SourceScan.maskComments(code)
        // 逐段断，别只断最后那个数——尺瞎的时候要先知道瞎在哪一段
        assertEquals("这一段里应当恰好一个 clickable：" + masked, 1, SourceScan.clickableOffsets(masked).size)
        assertEquals("里层那个 Box 要涂得出品牌底", 1, SourceScan.brandedBackgroundOffsets(masked).size)
        val click = SourceScan.clickableOffsets(masked).single()
        val call = checkNotNull(SourceScan.enclosingCall(masked, click)) {
            "clickable 下标 $click 没找到包住它的那次调用"
        }
        assertTrue(
            "包它的应当是外层那个 Box（起点 ${call.first}、终点 ${call.second}，clickable 在 $click）",
            call.first < click && call.second > click
        )
        assertEquals(
            "click=$click bg=${SourceScan.brandedBackgroundOffsets(masked)} " +
                "call=$call sites=${SourceScan.actionableBranded(masked)}\n掩码之后：$masked",
            1, SourceScan.actionableBranded(masked).size
        )
    }

    /** 反向对照：可点但整棵子树没有品牌底 ⇒ 不是"手绘的品牌底按钮" */
    @Test
    fun `clickable without brand tone is not counted`() {
        val code = """
            Box(modifier = Modifier.clickable(onClick = f)) {
                Box(modifier = Modifier.background(SurfaceCard)) { Text("x") }
            }
        """.trimIndent()
        assertEquals(0, SourceScan.actionableBranded(SourceScan.maskComments(code)).size)
    }

    /** 反向对照：涂了品牌底但**没人能按**（静态卡片）——那属于"表面色"那一把尺 */
    @Test
    fun `brand tone without clickable is not counted here`() {
        val code = """
            Box(modifier = Modifier.fillMaxWidth().background(PrimaryLight)) {
                Text("状态卡浅底")
            }
        """.trimIndent()
        assertEquals(0, SourceScan.actionableBranded(SourceScan.maskComments(code)).size)
        assertEquals(1, SourceScan.brandedBackgroundOffsets(SourceScan.maskComments(code)).size)
    }

    /** 一次调用里两颗 clickable ⇒ 两个站点（不合并：它们本来就是两颗控件） */
    @Test
    fun `two clickables inside one call are two sites`() {
        val code = """
            Row {
                Box(Modifier.background(Primary).clickable { a() })
                Box(Modifier.background(Primary).clickable { b() })
            }
        """.trimIndent()
        assertEquals(2, SourceScan.actionableBranded(SourceScan.maskComments(code)).size)
    }

    /** `enclosingCall` 找的是**最内层**那一个，不是第一个匹配到的 */
    @Test
    fun `enclosing call picks the innermost span`() {
        val code = "Column { Box(modifier = M.clickable { }) }"
        val masked = SourceScan.maskComments(code)
        val click = masked.indexOf(".clickable")
        val call = SourceScan.enclosingCall(masked, click)
        assertTrue("最内层应当是那个 Box( 而不是外层 Column(", call != null)
        val (start, end) = call!!
        // 约定：`enclosingCall` 给的起点就是那个左括号本身（名字在它前面）
        assertEquals("起点应是左括号", '(', code[start])
        assertTrue("左括号前面应是 Box", code.substring(0, start).trimEnd().endsWith("Box"))
        assertTrue("Box 的闭括号必须包住 clickable", end > click)
        assertNull(SourceScan.enclosingCall(masked, 0))
    }
}
