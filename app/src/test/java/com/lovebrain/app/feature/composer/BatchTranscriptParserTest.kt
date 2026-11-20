package com.lovebrain.app.feature.composer

import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BatchTranscriptParser] 的 JVM 单测——解析器是纯 Kotlin，无 Android 依赖，
 * 这些格判据不需要真机：真机验收只负责"真实微信复制样本"的格式覆盖（见 T2 交付文档）。
 */
class BatchTranscriptParserTest {

    /** 块格式：昵称 / 时间 / 正文（可多行），正文连续普通行都归并进当前条 */
    @Test
    fun blockFormatSplitsBySpeakerAndTimeLines() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            2024-01-01 12:00
            今天好累啊
            不想动
            我
            昨天 14:30
            那早点休息
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("今天好累啊\n不想动", result.entries[0].content)
        assertEquals("2024-01-01 12:00", result.entries[0].timestampText)
        assertEquals("我", result.entries[1].speakerName)
        assertEquals("那早点休息", result.entries[1].content)
        assertTrue(result.warnings.isEmpty())
    }

    /** 冒号行格式：每行一条；正文里的冒号句（未知"名字"）不拆条 */
    @Test
    fun colonLinesAreRecordsButUnknownColonsInContentAreNot() {
        val result = BatchTranscriptParser.parse(
            """
            小美：在吗
            小美：我想说：周末去看电影吧
            我：好啊
            """.trimIndent()
        )
        assertEquals(3, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("在吗", result.entries[0].content)
        assertEquals("我想说：周末去看电影吧", result.entries[1].content)
        assertEquals("我", result.entries[2].speakerName)
    }

    /** 半角冒号只有后跟空白才拆：URL、比例这类正文不被当发言人 */
    @Test
    fun halfWidthColonNeedsWhitespaceSoUrlsSurvive() {
        val result = BatchTranscriptParser.parse(
            """
            小美：发你个链接 https://example.com/a:b 看看
            小美：比例3:2的图
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("发你个链接 https://example.com/a:b 看看", result.entries[0].content)
        assertEquals("比例3:2的图", result.entries[1].content)
    }

    /** 编号转发格式：名字+时间可能同行或分行 */
    @Test
    fun numberedForwardFormatIsRecognized() {
        val result = BatchTranscriptParser.parse(
            """
            1. 小美
            2024-01-01 12:00:23
            周末有空吗
            2. 我 2024-01-01 12:00:30
            有啊怎么了
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("周末有空吗", result.entries[0].content)
        assertEquals("2024-01-01 12:00:23", result.entries[0].timestampText)
        assertEquals("我", result.entries[1].speakerName)
        assertEquals("有啊怎么了", result.entries[1].content)
    }

    /** 未知格式行不静默丢弃：并成 speakerName=null 的条目，等用户显式指定 */
    @Test
    fun unrecognizedLinesAreKeptAsUnresolvedEntriesNotDropped() {
        val result = BatchTranscriptParser.parse(
            """
            这是一段没有格式的文字
            第二行也是
            """.trimIndent()
        )
        assertEquals(1, result.entries.size)
        assertNull(result.entries[0].speakerName)
        assertEquals("这是一段没有格式的文字\n第二行也是", result.entries[0].content)
    }

    /** 待定行（可能是发言人）没被时间行确认 → 不猜成发言人，按未识别条目交预览 */
    @Test
    fun speakerLineWithoutTimeConfirmationIsNotGuessed() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            今天好累啊
            """.trimIndent()
        )
        // 没有"小美是发言人"的时间锚：整块是未识别条目，绝不猜小美是发言人
        assertEquals(1, result.entries.size)
        assertNull(result.entries[0].speakerName)
        assertEquals("小美\n今天好累啊", result.entries[0].content)
    }

    /** 块格式正文里冒号开头的行（未知昵称）不拆条——只有已知发言人才借冒号换条 */
    @Test
    fun blockContentColonLinesStayMerged() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            2024-01-01 12:00
            地址：北京市朝阳区
            备注：带伞
            """.trimIndent()
        )
        assertEquals(1, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("地址：北京市朝阳区\n备注：带伞", result.entries[0].content)
    }

    /** 无处归属的时间行：跳过但留警告（不是无痕消失） */
    @Test
    fun orphanTimeLineProducesWarningNotSilentDrop() {
        val result = BatchTranscriptParser.parse("14:30")
        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("14:30"))
    }

    /** 识别到发言人但没有正文：跳过 + 警告 */
    @Test
    fun speakerWithoutContentIsSkippedWithWarning() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            2024-01-01 12:00
            """.trimIndent()
        )
        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.warnings.size)
    }

    /** 条目顺序 = 文本顺序（导入时上下文依赖它） */
    @Test
    fun entryOrderFollowsTextOrder() {
        val result = BatchTranscriptParser.parse(
            """
            A：第一条
            B：第二条
            A：第三条
            """.trimIndent()
        )
        assertEquals(listOf("第一条", "第二条", "第三条"), result.entries.map { it.content })
        assertEquals(listOf("A", "B", "A"), result.entries.map { it.speakerName })
    }

    /** 空输入：空结果，不抛 */
    @Test
    fun blankInputYieldsEmptyResult() {
        val result = BatchTranscriptParser.parse("  \n \r\n ")
        assertTrue(result.entries.isEmpty())
        assertTrue(result.warnings.isEmpty())
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

    // ───────────────────────── W8 加固夹具矩阵 ─────────────────────────
    // 形态 × ≥2 变体；每夹具断言条数/发言人/顺序/正文保真。
    // 多行正文保真 = 空行也不丢；占位行按 T2 原则"不静默丢弃，预览标出"。

    /** 块格式 · 多行正文含内部空行：空行是正文的一部分，不丢行 */
    @Test
    fun blockFormatPreservesInteriorBlankLine() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            2024-01-01 12:00
            第一段

            第二段
            我
            昨天 14:30
            回复
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("第一段\n\n第二段", result.entries[0].content)
        assertEquals("回复", result.entries[1].content)
    }

    /** 块格式 · 空行密集：条目之间大量空行不产生幽灵条目、不产生警告 */
    @Test
    fun blockFormatSurvivesDenseBlankLines() {
        val result = BatchTranscriptParser.parse(
            "\n\n小美\n\n2024-01-01 12:00\n\n第一条\n\n\n我\n\n昨天 14:30\n\n第二条\n\n"
        )
        assertEquals(2, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("第一条", result.entries[0].content)
        assertEquals("我", result.entries[1].speakerName)
        assertEquals("第二条", result.entries[1].content)
        assertTrue(result.warnings.isEmpty())
    }

    /** 块格式 · 表情描述 [捂脸]：原样保留在正文里 */
    @Test
    fun blockFormatKeepsEmojiDescriptions() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            2024-01-01 12:00
            [捂脸]
            我
            2024-01-01 12:01
            哈哈哈[捂脸][哭笑]笑死我了
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("[捂脸]", result.entries[0].content)
        assertEquals("哈哈哈[捂脸][哭笑]笑死我了", result.entries[1].content)
    }

    /** 块格式 · 正文含 URL（带冒号端口/参数）不被拆条 */
    @Test
    fun blockFormatKeepsUrlInContent() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            昨天 14:30
            看看这个 https://example.com:8080/a/b?x=1:y
            我
            昨天 14:31
            打不开
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("看看这个 https://example.com:8080/a/b?x=1:y", result.entries[0].content)
    }

    /** 占位行（非文本消息）按 T2 规则处理：作为正文原样保留，绝不静默丢弃 */
    @Test
    fun placeholderLinesStayAsContentInOrder() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            2024-01-01 12:00
            [图片]
            我
            2024-01-01 12:01
            [语音]
            小美
            2024-01-01 12:02
            [视频]
            """.trimIndent()
        )
        assertEquals(3, result.entries.size)
        assertEquals(listOf("[图片]", "[语音]", "[视频]"), result.entries.map { it.content })
        assertEquals(listOf("小美", "我", "小美"), result.entries.map { it.speakerName })
    }

    /** 占位行 · 无主的 "[图片]" 行：未识别条目进预览，不丢、不猜发言人 */
    @Test
    fun standalonePlaceholderBecomesUnresolvedEntry() {
        val result = BatchTranscriptParser.parse("[图片]")
        assertEquals(1, result.entries.size)
        assertNull(result.entries[0].speakerName)
        assertEquals("[图片]", result.entries[0].content)
    }

    /** 块格式 · 时间行多变体：带/不带年份、带/不带秒、相对日、纯钟点 */
    @Test
    fun blockFormatTimeLineVariants() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            2024年1月1日 14:30
            a
            我
            01-15 09:05
            b
            小美
            昨天 23:59
            c
            我
            12:00
            d
            """.trimIndent()
        )
        assertEquals(4, result.entries.size)
        assertEquals(
            listOf("2024年1月1日 14:30", "01-15 09:05", "昨天 23:59", "12:00"),
            result.entries.map { it.timestampText }
        )
        assertEquals(listOf("a", "b", "c", "d"), result.entries.map { it.content })
        assertTrue(result.warnings.isEmpty())
    }

    /** 冒号行 · 半角冒号带空白也是记录分隔符（微信合并转发复制形态） */
    @Test
    fun colonFormatHalfWidthColonWithSpace() {
        val result = BatchTranscriptParser.parse(
            """
            小美: 在吗
            我: 在
            小美: 那晚上见
            """.trimIndent()
        )
        assertEquals(3, result.entries.size)
        assertEquals(listOf("小美", "我", "小美"), result.entries.map { it.speakerName })
        assertEquals("那晚上见", result.entries[2].content)
    }

    /** 冒号行 · 多行正文含内部空行：空行保真，不误拆 */
    @Test
    fun colonFormatPreservesInteriorBlankLine() {
        val result = BatchTranscriptParser.parse(
            """
            小美：第一行

            第三行
            我：好
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("第一行\n\n第三行", result.entries[0].content)
        assertEquals("我", result.entries[1].speakerName)
    }

    /** 冒号行 · 全角/半角混排 + 正文里的 URL 与比例 */
    @Test
    fun colonFormatMixedColonWidthsAndUrls() {
        val result = BatchTranscriptParser.parse(
            """
            小美：发你 https://a.b/c:d
            我: 比例3:2的图吗
            小美：对
            """.trimIndent()
        )
        assertEquals(3, result.entries.size)
        assertEquals("发你 https://a.b/c:d", result.entries[0].content)
        assertEquals("比例3:2的图吗", result.entries[1].content)
        assertEquals("对", result.entries[2].content)
    }

    /** 编号转发 · 编号符号多变体：`1、` `2)` `3．` 与 `名字 时间` 同行 */
    @Test
    fun numberedForwardMarkerVariants() {
        val result = BatchTranscriptParser.parse(
            """
            1、小美
            2024-01-01 12:00:23
            第一条
            2) 我 2024-01-01 12:00:30
            第二条
            3．小美：第三条
            """.trimIndent()
        )
        assertEquals(3, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("2024-01-01 12:00:23", result.entries[0].timestampText)
        assertEquals("第一条", result.entries[0].content)
        assertEquals("我", result.entries[1].speakerName)
        assertEquals("第二条", result.entries[1].content)
        assertEquals("小美", result.entries[2].speakerName)
        assertEquals("第三条", result.entries[2].content)
    }

    /** 合并转发 · "xx的聊天记录" 标题行：跳过 + 警告，不吞掉后面的记录 */
    @Test
    fun forwardedTitleLineIsSkippedWithWarning() {
        val result = BatchTranscriptParser.parse(
            """
            小美和我的聊天记录
            1. 小美 2024-01-01 12:00:23
            早上好
            2. 我 2024-01-01 12:00:30
            早呀
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("早上好", result.entries[0].content)
        assertEquals("早呀", result.entries[1].content)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("聊天记录"))
    }

    /** 导出说明 · "以下为聊天记录" 在冒号记录前：不再把整段吞进未识别条目（W8 修复） */
    @Test
    fun exportMarkerBeforeColonRecordsDoesNotPoisonParse() {
        val result = BatchTranscriptParser.parse(
            """
            以下为聊天记录
            小美：在吗
            我：在
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("在吗", result.entries[0].content)
        assertEquals("在", result.entries[1].content)
        assertEquals(1, result.warnings.size)
    }

    /** 导出说明 · 结尾的 "以上为聊天记录"：跳过 + 警告，已有条目不受影响 */
    @Test
    fun exportMarkerAtEndIsSkippedWithWarning() {
        val result = BatchTranscriptParser.parse(
            """
            小美：你好
            我：早
            以上为聊天记录
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("早", result.entries[1].content)
        assertEquals(1, result.warnings.size)
    }

    /** `昵称 时间` 同行 · 空白变体：半角空格 / 全角空格 U+3000 / 不换行空格 U+00A0 */
    @Test
    fun nameThenTimeToleratesIdeographicAndNbspSpaces() {
        for (sep in listOf(" ", "　", "\u00A0")) {
            val result = BatchTranscriptParser.parse("小美${sep}14:30\n你好")
            assertEquals("separator=" + sep.map { "U+" + it.code.toString(16) }, 1, result.entries.size)
            assertEquals("小美", result.entries[0].speakerName)
            assertEquals("14:30", result.entries[0].timestampText)
            assertEquals("你好", result.entries[0].content)
        }
    }

    /** 剪贴板 BOM：文件头 U+FEFF 不破坏首个发言人行的识别 */
    @Test
    fun bomPrefixIsIgnored() {
        val result = BatchTranscriptParser.parse("\uFEFF小美\n2024-01-01 12:00\n你好")
        assertEquals(1, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("你好", result.entries[0].content)
    }

    /** 超长单条（>4000 字）：完整保真，不截断、不拆条 */
    @Test
    fun ultraLongSingleLineIsPreserved() {
        val long = "这条消息非常长".repeat(600) // 4200 字
        val result = BatchTranscriptParser.parse(
            "小美\n2024-01-01 12:00\n$long"
        )
        assertEquals(1, result.entries.size)
        assertEquals(long, result.entries[0].content)
        assertEquals(4200, result.entries[0].content.length)
    }

    /** 超长多行（100 行）：行数与顺序保真 */
    @Test
    fun longMultiLineMessagePreservesEveryLine() {
        val body = (1..100).joinToString("\n") { "第${it}行" }
        val result = BatchTranscriptParser.parse(
            "我\n昨天 14:30\n$body"
        )
        assertEquals(1, result.entries.size)
        assertEquals(body, result.entries[0].content)
    }
    // ───────────────────────── R6 审计回归（冒号行误拆） ─────────────────────────
    // 冒号行格式里"地址：西湖边"这类字段行是上一条正文的续行，不是发言人叫"地址"的新条。
    // 修法：已开的冒号/编号条目里，常见字段词 + 该名字从未作为发言人出现 + 冒号后有内容
    // → 并入上一条并写 warning 标注（T2 原则：不误拆、交预览裁决）；真实短昵称不受影响。

    /** 审计案例：冒号格式里"地址：西湖边"是上一条的续行，不是新发言人 */
    @Test
    fun auditCaseColonFormatAddressLineIsContinuationNotNewSpeaker() {
        val result = BatchTranscriptParser.parse(
            """
            小美：周末见面
            地址：西湖边
            我：好
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("周末见面\n地址：西湖边", result.entries[0].content)
        assertEquals("我", result.entries[1].speakerName)
        assertEquals("好", result.entries[1].content)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("地址"))
    }

    /** 变体 · 正文连续两行字段词（电话/备注）：全部并入上一条 */
    @Test
    fun colonFormatConsecutiveLabelLinesStayMerged() {
        val result = BatchTranscriptParser.parse(
            """
            小美：给你我的信息
            电话：13800000000
            备注：到楼下打电话
            我：收到
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("给你我的信息\n电话：13800000000\n备注：到楼下打电话", result.entries[0].content)
        assertEquals("我", result.entries[1].speakerName)
        assertEquals("收到", result.entries[1].content)
    }

    /** 变体 · 字段词后接 URL：链接行并入正文，URL 原样保留 */
    @Test
    fun colonFormatLabelLineWithUrlStaysMerged() {
        val result = BatchTranscriptParser.parse(
            """
            小美：看这个
            链接：https://example.com/page?id=1
            我：打不开
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("看这个\n链接：https://example.com/page?id=1", result.entries[0].content)
        assertEquals("打不开", result.entries[1].content)
    }

    /** 编号转发 · 冒号同行开头 + 字段词续行：同样不误拆 */
    @Test
    fun numberedFormatLabelLineStaysMerged() {
        val result = BatchTranscriptParser.parse(
            """
            1. 小美：周末见面
            地址：西湖边
            2. 我：好
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("周末见面\n地址：西湖边", result.entries[0].content)
        assertEquals("我", result.entries[1].speakerName)
    }

    /** 反例 · 真实短昵称发言人（她/他）不吸收进上一条 */
    @Test
    fun genuineShortNicknameSpeakersStillSplit() {
        val result = BatchTranscriptParser.parse(
            """
            小美：好
            她：晚上见
            他：我也来
            """.trimIndent()
        )
        assertEquals(3, result.entries.size)
        assertEquals(listOf("小美", "她", "他"), result.entries.map { it.speakerName })
    }

    /** 反例 · 名字撞字段词但已登记为发言人（如昵称就叫"时间"）：以已出现为准，照常换条 */
    @Test
    fun labelNameRegisteredAsSpeakerStillSplits() {
        val result = BatchTranscriptParser.parse(
            """
            时间：周六行吗
            我：好
            时间：那周日
            """.trimIndent()
        )
        assertEquals(3, result.entries.size)
        assertEquals(listOf("时间", "我", "时间"), result.entries.map { it.speakerName })
    }

    /** 边界 · 首行就是字段词（无可并入的上一条）：维持原行为当新记录，交预览裁决 */
    @Test
    fun labelLineAsFirstLineStillBecomesRecord() {
        val result = BatchTranscriptParser.parse("地址：西湖边")
        assertEquals(1, result.entries.size)
        assertEquals("地址", result.entries[0].speakerName)
    }

    /** 边界 · 块格式正文里字段词行（既有保护）继续归并，不受影响 */
    @Test
    fun blockFormatLabelLinesStillMerged() {
        val result = BatchTranscriptParser.parse(
            """
            小美
            2024-01-01 12:00
            地址：北京市朝阳区
            我
            2024-01-01 12:01
            好
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("地址：北京市朝阳区", result.entries[0].content)
    }

    // ───────────────── 续行判定 / 编号小数 / 空壳登记 / 自动解析契约 ─────────────────

    /** 用户 2026-11-20 提供的真实块样本（逐字，条目间两空行，末条「行啵」） */
    private val userBlockSample =
        "W-2f\n2026年11月20日 15:14\n那我要晚一点粗发嘛\n\n\n" +
            "W-2f\n2026年11月20日 15:14\n四点出发？\n\n\n" +
            "陆晓\n2026年11月20日 15:15\n哈哈哈不急\n\n\n" +
            "陆晓\n2026年11月20日 15:15\n等我出发的时候和你是哦\n\n\n" +
            "陆晓\n2026年11月20日 15:15\n咱俩应该差不多同时到\n\n\n" +
            "W-2f\n2026年11月20日 15:16\n行啵"

    /** 冒号续行反例：「我想说：其实我不饿」是正文冒号句，并入上一条 + 1 警告，不猜新发言人 */
    @Test
    fun colonPronounPrefixContinuationMergesWithWarning() {
        val result = BatchTranscriptParser.parse("小美：今天去吃饭\n我想说：其实我不饿")
        assertEquals(1, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("今天去吃饭\n我想说：其实我不饿", result.entries[0].content)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("我想说"))
    }

    /** 多字代词前缀（我觉得/他说）并入；单字代词（我）仍是正常发言人，照常换条 */
    @Test
    fun colonMultiCharPronounPrefixesMergeButSinglePronounSplits() {
        val result = BatchTranscriptParser.parse(
            """
            小美：周末安排
            我觉得：先看天气
            他说：随便
            我：好
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("周末安排\n我觉得：先看天气\n他说：随便", result.entries[0].content)
        assertEquals("我", result.entries[1].speakerName)
        assertEquals("好", result.entries[1].content)
        assertEquals(2, result.warnings.size)
    }

    /** 未知短昵称不受续行规则影响：A：/B：/A：仍拆 3 条、零警告（与 entryOrderFollowsTextOrder 同夹具） */
    @Test
    fun shortUnknownNicknamesStillSplitWithoutWarnings() {
        val result = BatchTranscriptParser.parse("A：第一条\nB：第二条\nA：第三条")
        assertEquals(3, result.entries.size)
        assertEquals(listOf("A", "B", "A"), result.entries.map { it.speakerName })
        assertEquals(listOf("第一条", "第二条", "第三条"), result.entries.map { it.content })
        assertTrue(result.warnings.isEmpty())
    }

    /** 已确认发言人例外：昵称真叫「我想说」且已出现过 → 照常换条，不当续行 */
    @Test
    fun confirmedPronounPrefixSpeakerStillSplits() {
        val result = BatchTranscriptParser.parse("我想说：第一\n我：好\n我想说：第二")
        assertEquals(3, result.entries.size)
        assertEquals(listOf("我想说", "我", "我想说"), result.entries.map { it.speakerName })
        assertTrue(result.warnings.isEmpty())
    }

    /** 续行合并时正文里的 URL / 比例原样保留，不被半角冒号拆条 */
    @Test
    fun continuationMergeKeepsUrlsAndRatiosIntact() {
        val result = BatchTranscriptParser.parse(
            """
            小美：发你个图
            我想说：比例3:2的图
            链接：https://example.com/a:b
            我：收到
            """.trimIndent()
        )
        assertEquals(2, result.entries.size)
        assertEquals("发你个图\n我想说：比例3:2的图\n链接：https://example.com/a:b", result.entries[0].content)
        assertEquals("收到", result.entries[1].content)
        assertEquals(2, result.warnings.size)
    }

    /** 用户真实块样本（逐字）：6 条有序、时间串原样、零警告 */
    @Test
    fun userBlockSampleSixMessagesParsesVerbatim() {
        val result = BatchTranscriptParser.parse(userBlockSample)
        assertEquals(6, result.entries.size)
        assertEquals(
            listOf("W-2f", "W-2f", "陆晓", "陆晓", "陆晓", "W-2f"),
            result.entries.map { it.speakerName }
        )
        assertEquals(
            listOf("那我要晚一点粗发嘛", "四点出发？", "哈哈哈不急", "等我出发的时候和你是哦", "咱俩应该差不多同时到", "行啵"),
            result.entries.map { it.content }
        )
        assertEquals(
            listOf(
                "2026年11月20日 15:14", "2026年11月20日 15:14", "2026年11月20日 15:15",
                "2026年11月20日 15:15", "2026年11月20日 15:15", "2026年11月20日 15:16"
            ),
            result.entries.map { it.timestampText }
        )
        assertTrue(result.warnings.isEmpty())
    }

    /** 块样本单换行变体（条目间无空行）：仍靠时间锚拆条 */
    @Test
    fun userBlockSampleSingleNewlineVariantSplits() {
        val raw = "W-2f\n2026年11月20日 15:14\n那我要晚一点粗发嘛\n" +
            "陆晓\n2026年11月20日 15:15\n哈哈哈不急"
        val result = BatchTranscriptParser.parse(raw)
        assertEquals(2, result.entries.size)
        assertEquals(listOf("W-2f", "陆晓"), result.entries.map { it.speakerName })
        assertEquals(listOf("那我要晚一点粗发嘛", "哈哈哈不急"), result.entries.map { it.content })
        assertEquals(
            listOf("2026年11月20日 15:14", "2026年11月20日 15:15"),
            result.entries.map { it.timestampText }
        )
        assertTrue(result.warnings.isEmpty())
    }

    /** 编号标记后紧跟数字是小数正文，不被当编号拆碎 */
    @Test
    fun decimalLineIsNotNumberedMarker() {
        val result = BatchTranscriptParser.parse("小美：拍照参数\n1.5倍焦距\n我：好")
        assertEquals(2, result.entries.size)
        assertEquals("拍照参数\n1.5倍焦距", result.entries[0].content)
        assertEquals("好", result.entries[1].content)
        assertTrue(result.warnings.isEmpty())
    }

    /** 小数独立成行：整行原样进未识别条目，不碎成「5倍焦距」 */
    @Test
    fun standaloneDecimalLineStaysIntact() {
        val result = BatchTranscriptParser.parse("1.5倍焦距")
        assertEquals(1, result.entries.size)
        assertNull(result.entries[0].speakerName)
        assertEquals("1.5倍焦距", result.entries[0].content)
    }

    /** 空壳条不登记发言人：后续同名续行仍受保护，不被空壳污染成「已确认发言人」而硬拆 */
    @Test
    fun emptyContentRecordDoesNotPoisonContinuationProtection() {
        val result = BatchTranscriptParser.parse("地址：\n小美：周末见面\n地址：西湖边")
        assertEquals(1, result.entries.size)
        assertEquals("小美", result.entries[0].speakerName)
        assertEquals("周末见面\n地址：西湖边", result.entries[0].content)
        // 空壳「地址：」仍按既有行为留警告；续行合并再留一条
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings.any { it.contains("没有正文") })
    }

    // ── autoSplitEntries：轻点自动解析共享契约（确信才交出，否则 null）──

    /** 确信多条：用户块样本 → 6 条原样交出 */
    @Test
    fun autoSplitReturnsEntriesForConfidentBlockSample() {
        val entries = BatchTranscriptParser.autoSplitEntries(userBlockSample)
        assertNotNull(entries)
        assertEquals(6, entries!!.size)
        assertEquals(listOf("W-2f", "W-2f", "陆晓", "陆晓", "陆晓", "W-2f"), entries!!.map { it.speakerName })
    }

    /** 确信多条：普通冒号对话（含单字代词发言人「我」）→ 交出 */
    @Test
    fun autoSplitReturnsEntriesForPlainColonConversation() {
        val entries = BatchTranscriptParser.autoSplitEntries("小美：在吗\n我：在")
        assertNotNull(entries)
        assertEquals(2, entries!!.size)
        assertEquals(listOf("小美", "我"), entries!!.map { it.speakerName })
    }

    /** 不确信：单行 / 多行纯文本（无发言人）→ null */
    @Test
    fun autoSplitReturnsNullForPlainText() {
        assertNull(BatchTranscriptParser.autoSplitEntries("你好"))
        assertNull(BatchTranscriptParser.autoSplitEntries("这是一段没有格式的文字\n第二行也是"))
    }

    /** 不确信：孤儿时间行 / 空壳条 / 续行合并等带警告 → null */
    @Test
    fun autoSplitReturnsNullWhenWarningsPresent() {
        assertNull(BatchTranscriptParser.autoSplitEntries("14:30"))
        assertNull(BatchTranscriptParser.autoSplitEntries("小美\n2024-01-01 12:00"))
        assertNull(BatchTranscriptParser.autoSplitEntries("小美：在吗\n地址：西湖边\n我：好"))
    }

    /** 不确信：发言人全是字段词 / 代词续行形态（即便拆出 ≥2 条、零警告）→ null */
    @Test
    fun autoSplitReturnsNullWhenAllSpeakersAreContinuationForms() {
        assertNull(BatchTranscriptParser.autoSplitEntries("地址：西湖边\n地址：东湖边"))
        assertNull(BatchTranscriptParser.autoSplitEntries("我想说：第一\n我想说：第二"))
    }
}
