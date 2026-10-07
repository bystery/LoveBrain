package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 八项回复完整性检查（指导书 §11.2）——四风格 + 四方向 = 八项，**三个成因分家**。
 *
 * 钉住的是 [LoveBrainResponse.replyCompleteness] 与 [LoveBrainResponse.directionSchemes]
 * 这两颗纯函数的**读数**，不是屏幕字符串：
 *
 * | 成因 | 从哪来 | 读数落在哪 | 卡片念什么 |
 * |---|---|---|---|
 * | 真正没生成 | 四风格空白、方向缺位、方向空白串 | `Incomplete.missingLabels` | 「未生成」 |
 * | 合法 null | 方向这一位输出了 null（format.md 明写允许） | `Incomplete.notSuitableLabels` | 「本轮不适合」 |
 * | 重复凑数 | 同一条正文出现在多于一颗标签上 | `Incomplete.duplicatedLabels` | 卡不空，只在提示里点名 |
 *
 * ⚠ 这一族以前的混判是：`directions[i] == null` 与「这一位根本没出现」读成同一档，
 * 于是合法 null 被记进缺项、空白串被记成「不适合」。下面几格喂的是**同一轮里一颗合法 null
 * 加一颗真空缺**，两份读数必须分开——旧混判态下它们全红。
 *
 * 计数一律带宇宙：八项全池（[LoveBrainResponse.mergedEightItems]），不是露出来的那几张卡。
 */
class ReplyCompletenessTest {

    private fun response(
        recommended: String = "推荐的话",
        badBoy: String = "清醒的话",
        playful: String = "俏皮的话",
        warm: String = "温柔的话",
        directions: List<String?> = listOf("跟进的话", "展开的话", "表达的话", "转向的话")
    ) = LoveBrainResponse(
        response = ReplySchemes(
            recommended = recommended,
            badBoy = badBoy,
            playful = playful,
            warm = warm
        ),
        directions = directions
    )

    /** 八项宇宙的组成与顺序：四风格在前、四方向在后，一项都不能少 */
    @Test
    fun `the pool is four styles followed by four directions`() {
        val pool = response().mergedEightItems
        assertEquals("八项宇宙必须是 8 项，不是露出来的那几张", 8, pool.size)
        assertEquals(
            listOf(
                "STYLE:A", "STYLE:B", "STYLE:C", "STYLE:D",
                "DIRECTION:F", "DIRECTION:E", "DIRECTION:X", "DIRECTION:S"
            ),
            pool.map { it.identity.key }
        )
    }

    /** 八项都真出了正文 → Complete */
    @Test
    fun `all eight generated yields Complete`() {
        assertEquals(ReplyCompleteness.Complete, response().replyCompleteness)
    }

    /**
     * 合法 null 的方向**不计入缺项**（§11.2：只有协议明确允许的这一档才是「不适合」）。
     *
     * 旧混判态：`Partial(missingLabels = [展开])` ⇒ 这一格红。
     */
    @Test
    fun `a legal null direction is not counted as a missing item`() {
        val r = response(directions = listOf("跟进的话", null, "表达的话", "转向的话"))
        assertEquals(
            "合法 null 的方向不是缺项，八项里没有没生成的",
            ReplyCompleteness.Complete,
            r.replyCompleteness
        )
        // 同一判据在卡片那一侧：这一位 notSuitable=true、正文空
        val dir = r.directionSchemes[1]
        assertEquals("展开", dir.title)
        assertTrue("合法 null 的方向要带 notSuitable", dir.notSuitable)
        assertEquals("", dir.reply)
    }

    /**
     * 核心格：同一轮里**一颗合法 null + 一颗真空缺**，两份读数必须分开。
     *
     * 旧混判态：缺项那份会把「展开」也列进去（missing=[清醒, 展开]），
     * 而 notSuitableLabels 这一档当时根本不存在 ⇒ 三行断言全红。
     */
    @Test
    fun `a legal null and a true miss read apart in the same round`() {
        val r = response(
            badBoy = "   ",
            directions = listOf("跟进的话", null, "表达的话", "转向的话")
        )
        val c = r.replyCompleteness
        assertTrue("有真空缺就该是不完整，实到 $c", c is ReplyCompleteness.Incomplete)
        c as ReplyCompleteness.Incomplete

        assertEquals("缺项只点名真正没生成的那一颗", listOf("清醒"), c.missingLabels)
        assertEquals("合法 null 走自己那份读数，不借缺项的位子", listOf("展开"), c.notSuitableLabels)
        assertEquals("这一轮没有重复凑数", emptyList<String>(), c.duplicatedLabels)
        assertEquals("数量按整池八项说", 8, c.totalItems)

        // 宇宙守恒：有正文的 = 八项 - 缺项 - 不适合
        val withBody = r.mergedEightItems.count { it.reply.isNotBlank() }
        assertEquals(6, withBody)
        assertEquals(8, withBody + c.missingLabels.size + c.notSuitableLabels.size)
    }

    /**
     * 方向数组比四短（字段缺位）= 没生成，**不是**合法 null。
     *
     * 旧混判态：`getOrNull` 拿到的 null 与缺位在 notSuitable 上读成同一档 ⇒
     * 这里对四方向的 notSuitable 断言会拿到 [false, false, true, true] 之外的形状。
     */
    @Test
    fun `a short directions list means missing slots, not legal nulls`() {
        val r = response(directions = listOf("跟进的话", "展开的话"))
        val c = r.replyCompleteness
        assertTrue(c is ReplyCompleteness.Incomplete)
        c as ReplyCompleteness.Incomplete

        assertEquals(listOf("表达", "转向"), c.missingLabels)
        assertEquals("缺位不是合法 null，一份不适合都不许报", emptyList<String>(), c.notSuitableLabels)

        assertEquals(
            "缺位那两位的卡片必须念「未生成」（notSuitable=false）",
            listOf(false, false, false, false),
            r.directionSchemes.map { it.notSuitable }
        )
        assertEquals(
            listOf("跟进的话", "展开的话", "", ""),
            r.directionSchemes.map { it.reply }
        )
    }

    /**
     * 方向位的**空白串**也是没生成；只有 null 才是「本轮不适合」。
     *
     * 旧混判态：空白串被记成 notSuitable=true、null 被记成 false——两份读数正好相反 ⇒ 红。
     */
    @Test
    fun `a blank direction string is a miss while a null is not suitable`() {
        val r = response(directions = listOf(null, "   ", "表达的话", "转向的话"))
        assertEquals(
            "只有输出 null 的那一位才算不适合",
            listOf(true, false, false, false),
            r.directionSchemes.map { it.notSuitable }
        )
        val c = r.replyCompleteness
        assertTrue(c is ReplyCompleteness.Incomplete)
        c as ReplyCompleteness.Incomplete
        assertEquals(listOf("展开"), c.missingLabels)
        assertEquals(listOf("跟进"), c.notSuitableLabels)
    }

    /** 四风格没有「合法 null」这一档：空就是没生成，卡片不许念「不适合」 */
    @Test
    fun `a blank style is never a legal not-suitable`() {
        val r = response(recommended = "", warm = "  ")
        assertTrue(
            "四风格的空位一律是「没生成」，不许冒不适合",
            r.schemes.none { it.notSuitable }
        )
        val c = r.replyCompleteness
        assertTrue(c is ReplyCompleteness.Incomplete)
        c as ReplyCompleteness.Incomplete
        assertEquals(listOf("推荐", "温柔"), c.missingLabels)
        assertEquals(emptyList<String>(), c.notSuitableLabels)
    }

    /** 重复凑数按整池抓，跨风格与方向也算；三份读数各归各 */
    @Test
    fun `one body reused across labels is reported as duplicated across the whole pool`() {
        val shared = "同一句话"
        val r = response(
            recommended = shared,
            directions = listOf(shared, "展开的话", "表达的话", "转向的话")
        )
        val c = r.replyCompleteness
        assertTrue("一条正文贴两颗标签就是凑数，实到 $c", c is ReplyCompleteness.Incomplete)
        c as ReplyCompleteness.Incomplete
        assertEquals("按整池顺序点名", listOf("推荐", "跟进"), c.duplicatedLabels)
        assertEquals(emptyList<String>(), c.missingLabels)
        assertEquals(emptyList<String>(), c.notSuitableLabels)
        assertEquals(8, c.totalItems)
    }

    /** 缺项与重复可以同时非空——两因不互相吞掉 */
    @Test
    fun `a missing item does not hide a duplicate and vice versa`() {
        val shared = "同一句话"
        val r = response(
            recommended = shared,
            badBoy = shared,
            playful = "",
            directions = listOf("跟进的话", "展开的话", "表达的话", "转向的话")
        )
        val c = r.replyCompleteness
        c as ReplyCompleteness.Incomplete
        assertEquals(listOf("俏皮"), c.missingLabels)
        assertEquals(listOf("推荐", "清醒"), c.duplicatedLabels)
    }

    /** 全空 → Empty（解析层会拒绝，这一档只保证完备） */
    @Test
    fun `all eight without any body is Empty`() {
        val r = response(
            recommended = "", badBoy = "", playful = "", warm = "",
            directions = listOf(null, null, null, null)
        )
        assertEquals(ReplyCompleteness.Empty, r.replyCompleteness)
    }

    /**
     * 全池守恒（带宇宙的数量判据）：任何一种形状下，八项里的每一个空位
     * 必须**恰好**落进「缺项」或「本轮不适合」之一，不多算也不漏算。
     */
    @Test
    fun `every empty slot in the pool lands in exactly one of the two readings`() {
        val shapes = listOf(
            listOf<String?>("跟进的话", null, "表达的话", "转向的话"),
            listOf<String?>("跟进的话", "   ", "表达的话", "转向的话"),
            listOf<String?>("跟进的话", "展开的话"),
            listOf<String?>(null, null, "表达的话", "转向的话"),
            listOf<String?>("跟进的话", "展开的话", "表达的话", "转向的话"),
            emptyList()
        )
        shapes.forEach { dirs ->
            val r = response(directions = dirs)
            val pool = r.mergedEightItems
            val c = r.replyCompleteness

            if (c is ReplyCompleteness.Incomplete) {
                val trueMisses = pool.count { it.reply.isBlank() && !it.notSuitable }
                val legalNulls = pool.count { it.reply.isBlank() && it.notSuitable }
                assertEquals("形状 $dirs 的缺项读数不等于真正没生成的位数", trueMisses, c.missingLabels.size)
                assertEquals(
                    "形状 $dirs 的不适合读数不等于合法 null 的位数",
                    legalNulls,
                    c.notSuitableLabels.size
                )
                assertEquals(
                    "每一个空位都得有个名字，且只能有一个",
                    pool.count { it.reply.isBlank() },
                    c.missingLabels.size + c.notSuitableLabels.size
                )
                assertEquals("数量按整池八项说", 8, c.totalItems)
                assertEquals(
                    "八项 = 有正文的 + 缺项 + 不适合",
                    8,
                    pool.count { it.reply.isNotBlank() } + c.missingLabels.size + c.notSuitableLabels.size
                )
            } else {
                // 判 Complete 时池子仍可能有空位——但那些**必须**全是合法 null（本轮不适合）。
                // 拿"一个空位都没有"当 Complete 的前提，正是这张表行骂过的混为一谈本身。
                assertEquals(ReplyCompleteness.Complete, c)
                assertEquals(
                    "形状 $dirs 里不许有没名分的真空位（合法 null 各归不适合档，不进这条判据）",
                    0, pool.count { it.reply.isBlank() && !it.notSuitable }
                )
            }
        }
    }
}
