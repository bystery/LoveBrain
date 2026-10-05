package com.lovebrain.app.domain

import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.domain.port.LessonsRewriteResult
import com.lovebrain.app.domain.prompt.PromptKnowledgeSection
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `memory/lessons.md` 的解析 / 净化 / 一次性整理契约（ 第8节第2条、第8节第3条）。
 *
 * 钉的四件事，逐条对应这轮用户实测到的读数：
 * 1. **模型双标题**：程序写一条节头、模型又吐一条 → 中间没有正文就是同一批，只留一次
 *    （`1、1、3、1、5、1` 就靠这一条收敛）；
 * 2. **一批一个编号**：编号来自「有效提取批」，不是一级标题数量；
 * 3. **空输出不增次数**：空串 / `无新经验` / 只有标题没正文 → 不新增批、不动计数、不发成功提示；
 * 4. **整理不丢正文且可重入**：连跑两次，第二次不许改任何东西（批数、字节都不动）。
 *
 * 夹具全部是「仓库模板 + 三批仿真经验」，形状取自
 * （成对节头那一份归档）。
 * **不读真实用户数据、不接任何供应商**——引擎那两格用 mockk 把网关挡在门外，
 * 模型返回什么由测试写死。
 *
 * 每格都写了"什么反例会让它红"。判据一律不靠"含'测试'/'格式'就删"那种关键词网
 * （真实经验也用这些词，见 [keywordLookalikeRealLessonSurvives]）。
 */
class LessonDocTidyTest {

    // ═══════════ 夹具 ═══════════

    /**
     * 2026-10-03 之前 `assets/schema/lessons.md` 的全文，逐字（那份资产现在只剩一行标题）。
     * 历史模板全文**只住在这里**：它是 [LessonDoc.tidy] 的判据来源之一，不是要再 seed 进用户文件的东西。
     *
     * 夹具前提本身就是断言：这里每一行非空文本都必须被 `LessonDoc` 的模板表逐字登记，
     * 否则历史用户文件就有一行洗不掉——由 [oldSeededTemplateWashesToNothingAtAll] 从反方向钉住。
     */
    private val oldSeedTemplate: String = """
        <!-- 经验（军师自动提取，每5个话题转换触发一次）。
        每次提取以一级标题分隔。
        -->

        # 经验库

        军师自动追加提取节，节头格式：# [yyyy-MM-dd HH:mm] 第N次提取（模板内不放示例节）

        ## 测试接法
        - 测试：行为；接法：有效回复；禁用：无效回复；内核：她真正需求

        ## 做对的
        - 做对：行为；有效：为什么；复用：什么场景用

        ## 加分项
        - 触发：她什么状态；放大：做什么；复用：什么场景用

        ## 踩过的坑
        - 踩坑：做错什么；教训：下次怎么做；补救：已踩了怎么修

        ## 阶段转换
        - 从__到__：信号描述
        """.trimIndent()

    /**
     * 三批仿真长档（用户"几百轮"之后那个样子的浓缩版）：
     * - 批 1：程序节头 + 模型节头**同日期同编号**，正文里夹着整段 `### 字段说明` 加一行字段解释；
     * - 批 2：程序节头 `第3次提取` + 模型节头，后者是用户实测到的畸形写法 `# [2026-09-17 ]第3次提取`
     *   （方括号里没钟点、`提取` 前没空格）；
     * - 批 3：**只有节头、下面一个字都没有**（旧版"模型说无新经验、程序照样写标题"留下的空批）。
     *
     * ⚠ 这份夹具是 `oldSeedTemplate + 字面块` **拼**出来的，不是把 `$oldSeedTemplate` 插进 raw string 里：
     * `trimIndent()` 作用在**运行时的整篇字符串**上，插进来的那份自己已经没有缩进，
     * 公共最小缩进于是算成 0，字面块那 8 个空格就原样留在每一行行首——实测后果是节头行变成
     * `        # [2026-09-17 10:00] 第1次提取`，`LessonDoc` 的节头形状（行首必须是 `#`）认不出它，
     * `batchCount` 直接读到 0（2026-10-04 15:27 那 9 条红里 5 条的成因就是这里）。
     * 真实用户文件里节头永远在行首（`sectionHeader` 写出去的那一颗就是），所以这里拼，不放宽判据。
     */
    private val historicalFile: String = oldSeedTemplate + "\n\n" + """
        # [2026-09-17 10:00] 第1次提取

        # [2026-09-17 10:00] 第1次提取

        ## 踩过的坑
        - 踩坑：她把「格式」两字拿来开玩笑时我认真解释了；教训：跟着松；补救：补一句"你那是逗我"

        ### 字段说明
        **字段说明**：
        - 测试接法：行为（她做了什么探索性行为）；接法（你应该怎么回，可写原话或模式）；禁用（千万别怎么回）；内核（她真正想了解什么）

        # [2026-09-17 11:20] 第3次提取

        # [2026-09-17 ]第3次提取

        ## 测试接法
        - 测试：连续抛"会不会找我"式试探；接法：高框架弹回；内核：确认你不会离开

        ## 做对的
        - 做对：她抱怨工作时只接情绪；有效：她情绪明显好转；复用：她抱怨任何事时

        # [2026-09-18 09:05] 第5次提取
        """.trimIndent()

    /** 历史档里那六句**真实正文**——整理后必须一句不少地在场 */
    private val realBodyLines = listOf(
        "## 踩过的坑",
        "- 踩坑：她把「格式」两字拿来开玩笑时我认真解释了；教训：跟着松；补救：补一句\"你那是逗我\"",
        "## 测试接法",
        "- 测试：连续抛\"会不会找我\"式试探；接法：高框架弹回；内核：确认你不会离开",
        "## 做对的",
        "- 做对：她抱怨工作时只接情绪；有效：她情绪明显好转；复用：她抱怨任何事时"
    )

    /** 两批干净历史（编号 1、2 连续）：追加下一批时该发第 3 次 */
    private val twoCleanBatches = """
        # [2026-09-17 10:00] 第1次提取

        ## 做对的
        - 做对：先接情绪；有效：她愿意继续说；复用：她抱怨时

        # [2026-09-17 11:00] 第2次提取

        ## 踩过的坑
        - 踩坑：急着给建议；教训：先听完；补救：补一句"我再说"
        """.trimIndent()

    private fun loadAsset(path: String): String {
        val res = ClassLoader.getSystemClassLoader().getResourceAsStream(path)
            ?: error("资产 $path 不在 test classpath 上（build.gradle.kts 把 src/main/assets 挂进来了）")
        return res.bufferedReader().use { it.readText() }
    }

    /**
     * 一级提取节头的**测试侧**尺：故意与 `LessonDoc` 里那颗正则分开写，
     * 免得"生产码的判据漂了 + 测试跟着漂"两头一起瞎。
     */
    private fun extractionHeadings(text: String): List<String> =
        text.lines().filter { Regex("""^#\s*\[\s*\d{4}-\d{2}-\d{2}.*提取""").matches(it.trimEnd()) }

    /**
     * 只跑经验这一档的协调器夹具（话题数 5：命中经验与画像，不命中向量；画像那路被 mock 挡空）。
     *
     *   之后，写入这一档走的是**一次**「锁内读 → 整理 → 发号 → 条件替换」事务
     * （`readTidyAndReplaceWithRevisionCheck`），不再是"锁外 readFile + 锁内 appendFileWithRevisionCheck"。
     * 所以这颗夹具自己当那把锁：把 seeded 的 `existing` 交给 compose，拿回来的就是**整篇**新文件
     * （历史 + 这一批），并把它记进 [composed]——断言的对象由此从"追加的那一条"变成"落盘的那一篇"。
     */
    private class Harness(existing: String, modelRaw: String?) {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)

        /** 这一轮真的落盘的那一篇全文（compose 交回的东西）；null = 一次都没写 */
        var composed: String? = null
            private set

        /** 事务被调用了几次（"不许写盘"那一族判的就是这个，比 mock 的 verify 更直白） */
        var rewriteCalls: Int = 0
            private set

        private val coordinator: KnowledgeTriggerCoordinator

        init {
            coEvery { knowledgeRepo.getLessonCount("kb") } returns 5
            coEvery {
                knowledgeRepo.readTidyAndReplaceWithRevisionCheck(any(), any(), any(), any())
            } coAnswers {
                rewriteCalls++
                @Suppress("UNCHECKED_CAST")
                val compose = invocation.args[3] as (String) -> String?
                val out = compose.invoke(existing)
                when {
                    out == null || out == existing -> LessonsRewriteResult.NothingToWrite
                    else -> {
                        composed = out
                        LessonsRewriteResult.Rewritten("memory/.lessons.pre-tidy.md")
                    }
                }
            }
            val topicRecorder = mockk<TopicRecorder>(relaxed = true)
            coEvery { topicRecorder.getTopicFullContext(any(), any()) } returns "话题上下文"
            val gateway = mockk<DeepSeekRepository>(relaxed = true)
            if (modelRaw != null) coEvery { gateway.generateRaw(any(), any()) } returns modelRaw
            coordinator = KnowledgeTriggerCoordinator(
                knowledgeRepo, gateway, mockk<PromptBuilder>(relaxed = true), topicRecorder
            )
        }

        fun events(): List<KnowledgeTriggerEvent> {
            val emitted = mutableListOf<KnowledgeTriggerEvent>()
            runBlocking { coordinator.triggerEvents("kb").collect { emitted += it } }
            return emitted
        }
    }

    // ═══════════ 1. 模型双标题 = 一批 ═══════════

    /**
     * 成对节头收敛成一批，正文一个字不动。
     *
     * 反例：把 `parse` 的合并判据从"上一批还没有正文"换成"只认逐字相同的相邻标题"——
     * 夹具里批 2 那对是 `第3次提取` / `]第3次提取`（字面不同），那样就漏收成两批；
     * 换成"凡两条节头都算两批"更直接红：节头条数从 3 变 5。
     */
    @Test
    fun modelDoubleHeadingIsOneBatchAndKeepsEveryBodyLine() {
        val purified = LessonDoc.purify(historicalFile)

        assertEquals("purify 不重编，但也不许留成对标题", 3, extractionHeadings(purified).size)
        assertEquals("有效提取批应为 3", 3, LessonDoc.batchCount(historicalFile))
        assertEquals(
            "同一批里多出来的包裹节头条数应为 2（批 1 与批 2 各一条）",
            2, LessonDoc.wrappedDuplicateCount(historicalFile)
        )
        realBodyLines.forEach { assertTrue("净化把真实正文弄丢了：$it", purified.contains(it)) }
        assertFalse("旧 seed 的文档标题还在", purified.contains("# 经验库"))
        assertFalse("旧 seed 的序言还在", purified.contains("军师自动追加提取节"))
        assertFalse("字段说明那段还在", purified.contains("字段说明"))
    }

    /**
     * 一批一个编号：下一号取「有效批数」与「已声明最大编号」里大的那个 +1。
     *
     * 反例：编号若按一级标题数量发（旧写法），`twoCleanBatches` 也还是 3、但 [historicalFile] 会算成 6→7
     * 或 4（取决于按标题数还是按行数），跟这里钉的 6 与 4 都对不上；
     * 若去掉"取已声明最大值"那一半，这份没整理过的文件会发第 4 号——而历史里第 5 次已经在场，撞号。
     */
    @Test
    fun oneBatchOneNumberAcrossTheDoubleHeadingFile() {
        assertEquals("两批干净历史 → 下一批第 3 次", 3, LessonDoc.nextExtractionNumber(twoCleanBatches))
        assertEquals("只有一行标题的新库 → 第 1 次", 1, LessonDoc.nextExtractionNumber(oldSeedTemplate))
        assertEquals(
            "没整理过的历史文件里编号已跳到 5，按已声明最大值发号才不撞号",
            6, LessonDoc.nextExtractionNumber(historicalFile)
        )
        assertEquals(
            "整理过之后两边相等：3 批 → 下一批第 4 次",
            4, LessonDoc.nextExtractionNumber(LessonDoc.tidy(historicalFile).text)
        )
    }

    // ═══════════ 2. 空输出不增次数 ═══════════

    /**
     * 空串 / `无新经验` / 只有节头没正文 / 只有模板行：一律不写盘、不占编号、不发"已记入经验"。
     *
     * 反例：把守卫从 `raw.isBlank() || isNoNewLessons(raw)` 放宽成只看 `raw.isBlank()` →
     * `无新经验` 两档会被当正文追加、编号照涨；只拆掉 `modelBody(raw).isBlank()` 那道门 →
     * "只有节头""只有模板行"两档会各追加一条空批。四档各挨一刀，两道门都独立有对照。
     */
    @Test
    fun emptyModelOutputAddsNeitherBatchNorCount() {
        listOf(
            "空串" to "",
            "无新经验" to "无新经验",
            "无新经验带句号" to "无新经验。",
            "只有节头" to "# [2026-09-19 08:00] 第3次提取\n\n   \n",
            "只有模板行" to "### 字段说明\n- 测试：行为；接法：有效回复；禁用：无效回复；内核：她真正需求"
        ).forEach { (label, modelRaw) ->
            val harness = Harness(twoCleanBatches, modelRaw)
            val emitted = harness.events()

            assertEquals("$label：整段写事务一次都不许被调用", 0, harness.rewriteCalls)
            assertNull("$label：既然没进事务，就没有任何一篇要落盘", harness.composed)
            assertFalse(
                "$label：不该发「已记入经验」",
                emitted.any { it is KnowledgeTriggerEvent.Notice && it.message == "已记入经验" }
            )
            // 正向对照：守卫挡住的这批文本洗完确实没正文——两边判据说的是同一件事，不是各自瞎猜
            assertTrue("$label：洗完必须真是空的", LessonDoc.modelBody(modelRaw).isBlank())
        }
    }

    /**
     * 模型带了双标题 + 真实正文：整篇文件里只**多出一条**节头，编号接在已有两批之后（第 3 次），
     * 模型那两条假节头不许留在落盘的那一篇里（认它抄示例抄来的那个 `12:00` 日期——
     * 历史里本来就有一条合法的「第1次」，所以证人只能是日期而不是编号）。
     *
     *   之后断言的对象是"**这一轮真的落盘的那一篇全文**"（历史两批 + 新增一批），
     * 而不是旧写法里那段独立的追加 blob——这正好把 第8节第3条 那句"一批一个编号"钉得更严：
     * 反例①`entry` 拼的是模型原话 `raw` 而不是洗过的 `body` → 节头数从 3 变 5、`12:00` 那个假日期进来；
     * 反例②编号按一级标题数量发 → 写成第 5 次；反例③整理与追加拆成两次写 → 事务被调两次（rewriteCalls=2）。
     */
    @Test
    fun headingsFromModelStillAppendExactlyOneNumberedBatch() {
        val modelRaw = """
            # [2026-09-17 12:00] 第1次提取

            ## 加分项
            - 触发：她主动报备行程；放大：接住并夸具体那步；复用：她报备任何事时

            # [2026-09-17 12:00] 第1次提取
            """.trimIndent()
        val harness = Harness(twoCleanBatches, modelRaw)
        val emitted = harness.events()

        val written = harness.composed
        assertNotNull("这一批有正文，必须写盘", written)
        assertEquals("整理 + 追加是同一次事务，不许写两遍", 1, harness.rewriteCalls)
        val heads = extractionHeadings(written!!)
        assertEquals("历史两批 + 新增一批 = 整篇三条节头（模型那两条假标题被剥掉了）", 3, heads.size)
        assertEquals(
            "历史那两条节头一个字都不许被重新编过（这份文件本来就干净）",
            listOf(
                LessonDoc.sectionHeader("2026-09-17 10:00", 1),
                LessonDoc.sectionHeader("2026-09-17 11:00", 2)
            ),
            heads.take(2)
        )
        val heading = heads.last()
        val shape = Regex("""^# \[([^]\n]*)] 第(\d+)次提取$""").find(heading)
            ?: error("写出去的节头不是 sectionHeader 那颗形状：$heading")
        assertEquals(
            "节头必须由唯一的节头写法 sectionHeader 生出（第二处字面量一冒出来就红）",
            LessonDoc.sectionHeader(shape.groupValues[1], shape.groupValues[2].toInt()), heading
        )
        assertEquals("编号要接在有效批之后（第 3 次）", 3, shape.groupValues[2].toInt())
        assertTrue(
            "日期必须是程序钟给的四位 ISO 带钟点形状，不是模型抄来的那种：${shape.groupValues[1]}",
            Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$""").matches(shape.groupValues[1])
        )
        assertTrue(
            "模型那两条假节头（12:00 那个日期是它抄示例抄来的）整篇都不许在场",
            heads.none { it.contains("12:00") }
        )
        assertTrue(
            "真实正文一个字不许被剥标题时一起带走",
            written.contains("- 触发：她主动报备行程")
        )
        assertTrue(
            "两批历史正文也不许被这一次整篇替换弄丢",
            written.contains("- 做对：先接情绪") && written.contains("- 踩坑：急着给建议")
        )
        assertTrue(
            "写盘成功才发提示",
            emitted.any { it is KnowledgeTriggerEvent.Notice && it.message == "已记入经验" }
        )
    }

    // ═══════════ 3. 一次性整理：不丢正文 + 可重入 ═══════════

    /**
     * 整理后的硬账：**真实正文一句不少**、编号按文件顺序 1、2、3、原始日期原样保留，
     * 并且**连跑第二次什么都不改**（可重入）。
     *
     * 可重入这条的三种反例，每一种都会让它红：
     * ① 整理把"只有节头没有正文"的空批删掉 → 第一次就少一批（另一格
     *    [tidyRecordsAnomaliesInsteadOfDeletingUncertainBlocks] 再单独盯"不许偷偷删"）；
     * ② 整理写出的节头形状与 `parse` 认的形状不一致（比如 `render` 自己拼字面量、`sectionHeader` 改了格式）
     *    → 第二次整篇被当正文，批数从 3 掉到 0、字节全漂；
     * ③ 编号按"已声明最大值"而不是文件顺序发 → 第二次跑拿到 5、6、7，字节漂。
     */
    @Test
    fun tidyKeepsEveryRealLineAndIsReentrant() {
        val first = LessonDoc.tidy(historicalFile)

        assertEquals("有效提取批应为 3", 3, first.batchCount)
        realBodyLines.forEach { assertTrue("整理丢了真实正文：$it", first.text.contains(it)) }
        listOf("2026-09-17 10:00", "2026-09-17 11:20", "2026-09-18 09:05").forEach { date ->
            assertTrue("原始日期必须原样保留（不替谁补一个）：$date", first.text.contains("[$date]"))
        }
        assertEquals(
            "编号按文件顺序重编 1、2、3",
            listOf(1, 2, 3),
            Regex("""第(\d+)次提取""").findAll(first.text).map { it.groupValues[1].toInt() }.toList()
        )
        assertTrue("第一次跑确实动了手", first.changed)

        val second = LessonDoc.tidy(first.text)
        assertEquals("第二次跑的字节必须与第一次一字不差", first.text, second.text)
        assertEquals("第二次跑的批数不许动", first.batchCount, second.batchCount)
        assertFalse(
            "第二次跑 changed 必须为 false——不然「整理过一次」就变成每次开页都改写用户文件",
            second.changed
        )
        assertEquals(
            "第二次仍只剩那一条空批异常（包裹与编号异常已被第一次消掉）",
            listOf("节头「${LessonDoc.sectionHeader("2026-09-18 09:05", 3)}」下面没有正文，原样保留"),
            second.anomalies
        )
    }

    /**
     * 整理重编走的是**唯一那颗节头写法**：与 [LessonDoc.sectionHeader] 逐字同构。
     *
     * 反例：`render` 里再写一遍节头字面量（井号 + 方括号里那个日期 + 「第 N 次提取」那三段），
     * 某天 `sectionHeader` 改文案
     * （比如把"次"改成"批"）→ 整理过的文件与刚追加的批长成两种形状，这一格当场红。
     */
    @Test
    fun tidyRenumbersThroughTheSingleHeadingWriter() {
        val tidied = LessonDoc.tidy(historicalFile).text
        val expected = listOf(
            LessonDoc.sectionHeader("2026-09-17 10:00", 1),
            LessonDoc.sectionHeader("2026-09-17 11:20", 2),
            LessonDoc.sectionHeader("2026-09-18 09:05", 3)
        )
        assertEquals("三条节头都必须是 sectionHeader 的产物", expected, extractionHeadings(tidied))
    }

    /**
     * 说不清的历史块：内容留着 + 记异常，**不偷偷删**。
     *
     * 反例：把"只有节头没正文"那批当垃圾删 → 批数 3 变 2、异常少一条、下面那句"节头还在"也红；
     * 把"声明第 5 次、按顺序应为第 3 次"静默改写不记账 → mismatch 异常从 2 掉到 0。
     */
    @Test
    fun tidyRecordsAnomaliesInsteadOfDeletingUncertainBlocks() {
        val result = LessonDoc.tidy(historicalFile)

        assertEquals("两条包裹节头异常", 2, result.anomalies.count { it.contains("包裹节头") })
        assertEquals(
            "两条编号与顺序不符的异常（声明 3→应 2、声明 5→应 3）",
            2, result.anomalies.count { it.contains("按文件顺序应为") }
        )
        assertEquals(
            "一条只有节头没正文的异常（原样保留，不删）",
            1, result.anomalies.count { it.contains("下面没有正文") }
        )
        assertEquals(
            "0 条日期读不出的异常（三条节头都带方括号日期）",
            0, result.anomalies.count { it.contains("读不出日期") }
        )
        assertTrue(
            "空批的节头必须还在文件里（不许拿「简洁」冒充整理过）",
            result.text.contains(LessonDoc.sectionHeader("2026-09-18 09:05", 3))
        )
    }

    /**
     * 「只有标题的空批」与紧随其后的**真批次**是两批，不是一条批次的两次标题。
     *
     * 这一格钉的是   留下的那颗判据缺陷：合并判据只看"上一批没正文"，于是历史里那条
     * 空批会把**刚发号的那一批**当包裹吞掉。实测后果有两条，每一条都打在 第8节第3条 的红线上：
     * - 一批一个编号：4 批被数成 3 批，下一批发的号与已被吞掉的那条同号（`1、1、3、1、5、1` 换了个形式回来）；
     * - 可重入：`render` 给空批吐两个空行、调用方拼新批只给一个 ⇒ 每次整理都差一个空行，`changed` 永远 true。
     *
     * 反例：把 `isWrappedHeading` 那半步（同号 / 同 ISO 日期才认亲）删掉 ⇒ 第一条断言红；
     * 把 `render` 里"有正文才补那道空行"改回无条件 `append("\n")` ⇒ 第二条断言红。
     */
    @Test
    fun emptyLegacyBatchDoesNotSwallowTheBatchThatFollowsIt() {
        val withEmptyBatch = """
            # [2026-09-17 10:00] 第1次提取

            ## 做对的
            - 做对：先接情绪；有效：她愿意继续说；复用：她抱怨时

            # [2026-09-18 09:05] 第2次提取

            # [2026-09-19 15:00] 第3次提取

            ## 加分项
            - 触发：她主动报备行程；放大：接住并夸具体那步；复用：她报备任何事时
            """.trimIndent()

        val tidied = LessonDoc.tidy(withEmptyBatch)
        assertEquals("三批就是三批：空批不许把后面那批吞成一条", 3, tidied.batchCount)
        assertEquals("编号连续 1、2、3", listOf(1, 2, 3), numbersOf(tidied.text))
        assertTrue("空批的节头原样留着（只记异常，不删数据）",
            tidied.text.contains(LessonDoc.sectionHeader("2026-09-18 09:05", 2)))
        assertEquals("只有节头没正文那条记 1 条异常", 1, tidied.anomalies.count { it.contains("下面没有正文") })

        assertEquals("整理过一次就是终态：再跑一次逐字不动", false, LessonDoc.tidy(tidied.text).changed)
        assertEquals("第二次跑的字节与第一次一字不差", tidied.text, LessonDoc.tidy(tidied.text).text)
    }

    /** 文本里按出现顺序读出所有「第N次提取」的 N */
    private fun numbersOf(text: String): List<Int> =
        Regex("""第(\d+)次提取""").findAll(text).map { it.groupValues[1].toInt() }.toList()

    // ═══════════ 4. 模板的去处 ═══════════

    /**
     * 旧 seeding 模板（已从资产删走那份）整份洗完必须**一个字都不剩**——
     * 这句同时是 `tidy` 的合法性证明：历史文件里那些行全在册，一条都没漏登记。
     *
     * 反例：从 `LessonDoc` 的模板表里删掉任意一行（例如那句 seed 序言、或某个占位条目）
     * → 洗完还剩一行 → 红。
     */
    @Test
    fun oldSeededTemplateWashesToNothingAtAll() {
        assertEquals("旧模板 purify 后应为空串", "", LessonDoc.purify(oldSeedTemplate))
        assertEquals("旧模板 tidy 后应为空串", "", LessonDoc.tidy(oldSeedTemplate).text)
        assertEquals("旧模板里没有任何有效提取批", 0, LessonDoc.batchCount(oldSeedTemplate))
        assertEquals("旧模板当模型输出来洗也该是空", "", LessonDoc.modelBody(oldSeedTemplate))
    }

    /**
     * 现役 `assets/schema/lessons.md`（被清成 13 字节那份）——建库 seed 写进用户文件的正是它：
     * 洗完必须是空，即"模板既不进界面也不进 prompt"在**新建库那一头**也成立。
     *
     * 反例：往那颗资产里放回序言/占位条目而没同时登记进模板表 → 洗完还剩东西；
     * 资产被清空成 0 字节 → 第一句"应只剩一行标题"红（点名这颗资产没被顺手清过头）。
     */
    @Test
    fun currentSeedAssetWashesToNothingAtAll() {
        val seed = loadAsset("schema/lessons.md")
        assertEquals(
            "这份 seed 资产应只剩一行标题（不是空的，也不是模板全文）",
            listOf("# 经验库"),
            seed.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        )
        assertEquals("新建库的 seed 洗完应为空串", "", LessonDoc.purify(seed))
        assertEquals("新建库的 seed 不应有批", 0, LessonDoc.batchCount(seed))
        assertEquals("新建库的 seed 整理后应为空串", "", LessonDoc.tidy(seed).text)
    }

    /**
     * 结构在不在生成该在的那一层：模型抄得到的每一行模板都**必须仍逐字挂在引擎资产里并被登记**。
     * 这一格是"改了资产没人补登记"那起漂移的哨兵。
     *
     * 反例：把 `engine/knowledge_prompt/lessons.md` 里那五行字段解释改一个字、或把
     * `### 字段含义…` 再改一次名而不动登记表 → 那一行当场红；
     * 反过来在登记表里凭空加一行资产里没有的，也红。
     */
    @Test
    fun assetRulerStillSeesEveryPromptCopiedTemplateLine() {
        val engineLines = loadAsset("engine/knowledge_prompt/lessons.md")
            .lines().map { it.trimEnd() }.toSet()
        // `.toSet()` 不是装饰：`Set.filter` 交回的是 **List**，而 `assertEquals(emptySet(), listOf())`
        // 就算两边都空也红（EmptySet 与 ArrayList 永远不相等）——2026-10-04 15:27 那 9 条里有 1 条就是这么来的。
        val unregistered = LessonDoc.PROMPT_COPIED_TEMPLATE_LINES.filter { it !in engineLines }.toSet()
        assertEquals(
            "这些登记行已不在引擎资产里逐字在场（资产改了字/改了标题名，登记没跟上）：$unregistered",
            emptySet<String>(), unregistered
        )
        // 正向对照：资产真带着这些行（万一资产被清成空，上一条会"空集比空集"绿，所以要这一句）
        assertEquals(
            "登记表与资产同在场该行全部命中",
            LessonDoc.PROMPT_COPIED_TEMPLATE_LINES.size,
            LessonDoc.PROMPT_COPIED_TEMPLATE_LINES.count { it in engineLines }
        )
    }

    /**
     * 提取用的 prompt 结构来自 `engine/knowledge_prompt/lessons.md`，**不是** schema 那份：
     * 引擎资产带着 `## 输出格式` 那一族的五格类别，schema 那份只剩标题（它只当 seed 用）。
     *
     * 反例： 之后若有人把结构搬回 `schema/lessons.md`（那正是"模板又变回用户内容"的老路），
     * 引擎资产这边就少一格 → 红；同理 `## 示例` 里若还挂着 `# [日期] 第N次提取` 那种一级标题，
     * 就等于一边禁止一边示范，也红——这一句钉的正是本轮实测到的双标题成因。
     */
    @Test
    fun structureLivesInTheEngineAssetNotTheSeededOne() {
        val engine = loadAsset("engine/knowledge_prompt/lessons.md")
        val seed = loadAsset("schema/lessons.md")

        listOf("## 输出格式", "## 测试接法", "## 做对的", "## 加分项", "## 踩过的坑", "## 阶段转换").forEach {
            assertTrue("引擎资产少了结构那一格：$it", engine.contains(it))
            assertFalse("schema 那份 seed 里不该再有结构（它是会被写进用户文件的）：$it", seed.contains(it))
        }
        assertTrue(
            "引擎资产应写明日期与编号由程序分配",
            engine.contains("日期与编号由程序在写入时分配") || engine.contains("日期与编号由程序写入时分配")
        )
        assertEquals(
            "示例里不许出现一级提取节头（那是在示范被禁止的形状，实测双标题就是这么来的）",
            emptyList<String>(),
            Regex("""(?m)^#[ \t]*\[[ \t]*\d{4}-\d{2}-\d{2}[^\n]*提取""").findAll(engine).map { it.value }.toList()
        )
    }

    /**
     * 净化过的那一头进 prompt 时不带模板行。
     *
     * ⚠ 下面 `rawSeed` 那两句是**故意登记的缺口读数**，不是"这样才对"：生产链路 `PromptBuilder`
     * 此刻把 `readFile("memory/lessons.md")` 的**原始字节**直接喂给段（`PromptKnowledgeSection:77` →
     * `PromptBudget.lastH1Blocks`），那颗共用净化口现在只接到界面预览与写入侧。
     * 于是：同一份 seed 模板，purify 过就一个字都不注入，没 purify 就整段照进 prompt。
     * 等 prompt 侧接上同一颗函数，`rawSeed` 那句会反过来变红，提醒把这条登记撤掉。
     *
     * 反例（让主断言红）：`purify` 少剥一类模板行，或段里把原始文件与净化结果用反了。
     * 用**只装 seed 的那份**做缺口对照不是随手挑的：长档里节头有 6 个，`lastH1Blocks(_, 3)`
     * 只会取最后 3 块，模板那几块本来就被截掉了——拿它当"缺口存在"的证据是假读数。
     */
    @Test
    fun purifiedLessonsReachThePromptWithoutTemplateLines() {
        val purified = LessonDoc.purify(historicalFile)
        val section = knowledgeSectionOf(purified)

        (LessonDoc.PROMPT_COPIED_TEMPLATE_LINES + LessonDoc.LEGACY_TEMPLATE_LINES).forEach {
            assertFalse("净化过的经验段还带着模板行：$it", section.text.contains(it))
        }
        assertTrue("真实正文要能进 prompt", section.text.contains("- 做对：她抱怨工作时只接情绪"))

        // 缺口读数（同一颗输入、两条路）：seed 模板没经 purify 时确实照进 prompt
        val rawSeed = knowledgeSectionOf(oldSeedTemplate)
        val washedSeed = knowledgeSectionOf(LessonDoc.purify(oldSeedTemplate))
        assertTrue(
            "缺口读数：生产链路此刻没接 purify，seed 模板的序言照进 prompt",
            rawSeed.text.contains("军师自动追加提取节")
        )
        assertFalse(
            "同一份洗完就不该有任何东西被注入",
            washedSeed.text.contains("军师自动追加提取节") || washedSeed.text.contains("# 经验库")
        )
    }

    private fun knowledgeSectionOf(lessons: String): PromptKnowledgeSection.Output =
        PromptKnowledgeSection.build(
            PromptKnowledgeSection.Input(
                kbName = "kb", me = "我：短句", her = "她：猫奴", warmth = "温度：轻松", style = "偏好：短句",
                stageSection = "", lessons = lessons, aggressiveText = "",
                topicAge = 1, topic = "手冲", sceneChain = "", recent = "",
                ongoingPlan = "", corrections = emptyMap()
            )
        )

    // ═══════════ 5. 判据不许长成关键词网 ═══════════

    /**
     * 真实经验里出现"测试""格式""提取"这些字，一个字都不许被吃掉。
     *
     * 反例：把剥除判据换成 `line.contains("测试") || line.contains("格式") → 删`——
     * 本夹具就是那把刀的靶子，两行真实经验当场消失，这一格红；
     * `## 测试接法` 这种**有正文**的类别标题同理（空类别才会被去掉，见上一族用例）。
     */
    @Test
    fun keywordLookalikeRealLessonSurvives() {
        val real = """
            # [2026-09-20 09:00] 第1次提取

            ## 测试接法
            - 测试：她用"你这格式"打趣我的回复；接法：跟着玩，别解释；内核：她想确认你能松下来
            - 提取：她主动提"上次那个话题"；接法：接住并推进；复用：她回头提旧话题时
            """.trimIndent()
        val purified = LessonDoc.purify(real)

        assertTrue(
            "含「格式」二字的真实经验被误伤了",
            purified.contains("- 测试：她用\"你这格式\"打趣我的回复；接法：跟着玩，别解释；内核：她想确认你能松下来")
        )
        assertTrue(
            "以「提取：」开头的真实条目被误伤了",
            purified.contains("- 提取：她主动提\"上次那个话题\"；接法：接住并推进；复用：她回头提旧话题时")
        )
        assertTrue("有正文的二级类别标题不许消失", purified.contains("## 测试接法"))
        assertEquals("整理同样不许动它", real, LessonDoc.tidy(real).text)
    }

    /**
     * HTML 注释只整段剥一次：两段注释**之间**的正文不许被吞。
     *
     * 反例：把 `stripComments` 换成分步剥（先按行找 `<!--` 再找 `-->`）→ 夹在两段注释之间的那条
     * 真实经验整片消失，这一格红（本仓库踩过的那颗坑）。
     */
    @Test
    fun commentsAreStrippedInOnePassSoTextBetweenThemSurvives() {
        val withComments = """
            # [2026-09-21 09:00] 第1次提取
            <!-- 旧版说明第一段
            -->
            - 测试：她问"在吗"；接法：立刻回；内核：她要确认你在
            <!-- 旧版说明第二段
            -->
            """.trimIndent()
        val purified = LessonDoc.purify(withComments)

        assertTrue(
            "两段注释之间的正文被吞了",
            purified.contains("- 测试：她问\"在吗\"；接法：立刻回；内核：她要确认你在")
        )
        assertFalse("注释本身不许留在净化结果里", purified.contains("旧版说明"))
    }
}
