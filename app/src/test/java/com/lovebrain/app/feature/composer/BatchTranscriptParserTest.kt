package com.lovebrain.app.feature.composer

import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [BatchTranscriptParser] 的 JVM 单测：解析器是纯 Kotlin、零 Android 依赖，
 * 这里钉的是**结构规则**本身——空行分块、块头/时间行/正文三段、全块合格才拆、否则整段一条。
 *
 * 角色不在这一层判（调用方统一用输入框角色），所以本文件没有一条"昵称→角色"的断言。
 */
class BatchTranscriptParserTest {

    /** 两块标准结构：只有第 3 行起的正文成为条目，块头与时间行被结构吃掉 */
    @Test
    fun blankLinesSplitBlocksAndOnlyBodyBecomesEntry() {
        val entries = BatchTranscriptParser.autoSplitEntries(
            """
            小美
            2024-01-01 12:00
            今天好累

            我
            昨天 14:30
            那早点休息
            """.trimIndent()
        )
        assertEquals(listOf("今天好累", "那早点休息"), entries?.map { it.content })
    }

    /** 多行正文用换行连接：块内不会因为行多而被再切 */
    @Test
    fun multiLineBodyIsJoinedWithNewlines() {
        val entries = BatchTranscriptParser.autoSplitEntries(
            """
            小美
            2024-01-01 12:00
            第一行
            第二行
            第三行

            我
            2024-01-01 12:01
            收到
            """.trimIndent()
        )
        assertEquals(listOf("第一行\n第二行\n第三行", "收到"), entries?.map { it.content })
    }

    /** CRLF 与孤立 CR 都先归一成 LF，块结构不受换行形态影响 */
    @Test
    fun crlfAndLoneCrAreNormalized() {
        val crlf = "小美\r\n2024-01-01 12:00\r\n你好\r\n\r\n我\r\n2024-01-01 12:01\r\n在"
        assertEquals(listOf("你好", "在"), BatchTranscriptParser.autoSplitEntries(crlf)?.map { it.content })

        val loneCr = "小美\r2024-01-01 12:00\r你好\r\r我\r2024-01-01 12:01\r在"
        assertEquals(listOf("你好", "在"), BatchTranscriptParser.autoSplitEntries(loneCr)?.map { it.content })
    }

    /** 时间行只按结构认：纯日期、带年月日、纯钟点、日期+钟点、昨天+钟点都算 */
    @Test
    fun timestampLineAcceptsDateAndClockStructures() {
        val variants = listOf(
            "2024-01-01",
            "2024/1/1",
            "2026年11月20日",
            "11月20日",
            "21:30",
            "21:30:00",
            "2026-11-21 21:30",
            "2026年11月20日 21:30",
            "昨天 21:30"
        )
        for (line in variants) {
            val raw = "小美\n$line\n第一\n\n我\n$line\n第二"
            assertEquals(
                "时间行「$line」该被结构认出来",
                listOf("第一", "第二"),
                BatchTranscriptParser.autoSplitEntries(raw)?.map { it.content }
            )
        }
    }

    /**
     * 第 2 行必须**整行**是时间行：钟点/日期后面还跟着半句话 ⇒ 不是时间行 ⇒ 整段退 B。
     *
     * 反例（核验给的那段真文本）：旧写法在 "21:30 见" 上匹配到钟点就认了块结构，
     * 于是这段被切成 2 条，还把 "见" 与 "带伞" 一起丢掉。
     */
    @Test
    fun timestampLineMustBeTheWholeLine() {
        assertNull(
            BatchTranscriptParser.autoSplitEntries(
                "甲\n2024-01-01\n加班\n\n乙\n21:30 见\n带伞"
            )
        )
        val dirty = listOf("2024-01-01 加班", "21:30 见", "昨天 21:30 带伞", "2024/1/1（周二）")
        for (line in dirty) {
            assertNull(
                "「$line」不是纯时间行，第 2 行不该成立",
                BatchTranscriptParser.autoSplitEntries("小美\n$line\n第一\n\n我\n2024-01-01 12:00\n第二")
            )
        }
    }

    /** 年月日时分位数严格：多一位、少一位、分隔符混用都不是时间行 */
    @Test
    fun timestampLineRejectsLooseDigitsAndMixedSeparators() {
        val loose = listOf(
            "21:3",          // 分钟只有 1 位
            "21:300",        // 分钟多一位
            "2024-01-011",   // 日多一位
            "24-1-1",        // 年不足 4 位
            "2024/01-01",    // 分隔符混用
            "2026年11月20",   // 缺"日"
            "11月20"         // 缺"日"
        )
        for (line in loose) {
            assertNull(
                "「$line」位数/分隔符不严格，不该当时间行",
                BatchTranscriptParser.autoSplitEntries("小美\n$line\n第一\n\n我\n2024-01-01 12:00\n第二")
            )
        }
    }

    /** 从第 3 行起不再判时间：正文里长得像日期/时间的行原样保留 */
    @Test
    fun bodyLinesLookingLikeTimestampsStayVerbatim() {
        val entries = BatchTranscriptParser.autoSplitEntries(
            "小美\n2024-01-01 12:00\n2024-01-01\n21:30\n昨天 21:30\n\n" +
                "我\n2024-01-01 12:01\n明天 12:00 见"
        )
        assertEquals(
            listOf("2024-01-01\n21:30\n昨天 21:30", "明天 12:00 见"),
            entries?.map { it.content }
        )
    }

    /** 块内出现空行 = 又一个块：后续那几行没有块头/时间行 ⇒ 整段不合格（不猜着拼回上一条） */
    @Test
    fun aBlankLineInsideTheBodyStartsANewBlockAndFailsTheWholeText() {
        assertNull(
            BatchTranscriptParser.autoSplitEntries(
                "小美\n2024-01-01 12:00\n第一段\n\n第二段接着写\n\n我\n2024-01-01 12:01\n收到"
            )
        )
    }

    /** 正文行原样保留：行首缩进是粘贴文本的一部分，只按空行裁块，不逐行 trim */
    @Test
    fun bodyKeepsItsIndentationAndTrailingSpaces() {
        val entries = BatchTranscriptParser.autoSplitEntries(
            "小美\n2024-01-01 12:00\n  第一行\n    第二行\n\n我\n2024-01-01 12:01\n收到"
        )
        assertEquals(
            listOf("  第一行\n    第二行", "收到"),
            entries?.map { it.content }
        )
    }

    /** 正文首行长得像日期也只是正文：第 3 行起不再判时间，一个字都不许被结构吃掉 */
    @Test
    fun aDateLookingFirstBodyLineStaysInTheBody() {
        val entries = BatchTranscriptParser.autoSplitEntries(
            "小美\n2024-01-01 12:00\n2026-11-21\n那天说的\n\n我\n2024-01-01 12:01\n收到"
        )
        assertEquals(
            listOf("2026-11-21\n那天说的", "收到"),
            entries?.map { it.content }
        )
    }

    /** 块头只是结构位：内容不限（含冒号/编号/标题字样都不拆、不参与角色判断） */
    @Test
    fun headerLineIsNotParsedForMeaning() {
        val entries = BatchTranscriptParser.autoSplitEntries(
            "小美：在吗\n2024-01-01 12:00\n今天好累\n\n" +
                "1. 我 2024-01-01 12:01\n2024-01-01 12:01\n早点休息"
        )
        assertEquals(listOf("今天好累", "早点休息"), entries?.map { it.content })
    }

    /** 正文行里的冒号句不被拆条（第 3 行起不解析） */
    @Test
    fun colonLinesInsideBodyStayInTheSameEntry() {
        val entries = BatchTranscriptParser.autoSplitEntries(
            "小美\n2024-01-01 12:00\n地址：北京市朝阳区\n备注：带伞\n\n" +
                "我\n2024-01-01 12:01\n收到"
        )
        assertEquals(
            listOf("地址：北京市朝阳区\n备注：带伞", "收到"),
            entries?.map { it.content }
        )
    }

    /** 冒号两行文本是单块且只有两行（缺时间行与正文）⇒ 结构不成立 ⇒ 整段不合格（调用方整段一条） */
    @Test
    fun colonTwoLineTextHasNoBlocksAndFallsBack() {
        assertNull(BatchTranscriptParser.autoSplitEntries("小美：在吗\n我：在的"))
        // 即使每行都"像"一条冒号记录，没有空行就还是一块
        assertNull(
            BatchTranscriptParser.autoSplitEntries(
                "小美：在吗\n我：在的\n小美：晚安"
            )
        )
    }

    /** 单块结构成立也拆：昵称行+日期行+正文 ≥1 行即交出正文（0 块或坏块才整段一条） */
    @Test
    fun singleValidBlockYieldsItsBody() {
        assertEquals(
            listOf("今天好累"),
            BatchTranscriptParser.autoSplitEntries("小美\n2024-01-01 12:00\n今天好累")?.map { it.content }
        )
    }

    /** 任一块不合格 ⇒ 整段不合格：缺正文、缺时间行都返回 null，不跳过坏块 */
    @Test
    fun anyBadBlockFailsTheWholeText() {
        // 第二块只有块头 + 时间行，没有正文
        assertNull(
            BatchTranscriptParser.autoSplitEntries(
                "小美\n2024-01-01 12:00\n第一\n\n我\n2024-01-01 12:01"
            )
        )
        // 第二块第 2 行不是日期/时间结构
        assertNull(
            BatchTranscriptParser.autoSplitEntries(
                "小美\n2024-01-01 12:00\n第一\n\n我\n随便一句\n第二"
            )
        )
        // 第二块只有一行块头
        assertNull(
            BatchTranscriptParser.autoSplitEntries(
                "小美\n2024-01-01 12:00\n第一\n\n我"
            )
        )
    }

    /** 首尾与连续空白行（含仅空白字符的行）只是块边界：不产生空块、不改变条目 */
    @Test
    fun leadingTrailingAndRepeatedBlankLinesAreJustBoundaries() {
        val entries = BatchTranscriptParser.autoSplitEntries(
            "\n \t \n小美\n2024-01-01 12:00\n第一\n  \n\n \n我\n2024-01-01 12:01\n第二\n\n  \n"
        )
        assertEquals(listOf("第一", "第二"), entries?.map { it.content })
    }

    /** 三块顺序即原文顺序，块数 ≥2 全合格就全交出 */
    @Test
    fun threeValidBlocksKeepOriginalOrder() {
        val entries = BatchTranscriptParser.autoSplitEntries(
            "A\n2024-01-01 12:00\n第一条\n\n" +
                "B\n2024-01-01 12:01\n第二条\n\n" +
                "A\n2024-01-01 12:02\n第三条"
        )
        assertEquals(listOf("第一条", "第二条", "第三条"), entries?.map { it.content })
    }

    /** 空文本 / 纯空白：null（调用方 no-op，不落空消息） */
    @Test
    fun blankTextFallsBack() {
        assertNull(BatchTranscriptParser.autoSplitEntries(""))
        assertNull(BatchTranscriptParser.autoSplitEntries("  \n \r\n \t"))
    }

    /** [BatchImportDraft] 的构造期硬挡：IDEA（军师备注通道）进不来 */
    @Test(expected = IllegalArgumentException::class)
    fun batchImportDraftRejectsIdeaRole() {
        BatchImportDraft(ChatMessage.Role.IDEA, "内容")
    }

    @Test(expected = IllegalArgumentException::class)
    fun batchImportDraftRejectsBlankContent() {
        BatchImportDraft(ChatMessage.Role.HER, "   ")
    }
}
