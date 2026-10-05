package com.lovebrain.app.domain

/**
 * 经验文件（`memory/lessons.md`）的解析、净化与编号——**日期与编号的唯一所有者是程序**。
 *
 * 三件事住在一处，只有一个出口：
 * - [modelBody]：把模型这一批的输出洗成「只有非空类别 + 真实正文」，剥掉的只有匹配提取节头的
 *   包裹标题，二级分类一个字都不动；
 * - [purify]：读侧净化——编辑页预览（`KbEditActivity.prettyForPreview`，`ui/KbEditActivity.kt:737`）
 *   与生成 prompt 那一路**共用这一颗**：2026-10-05  把 `domain/PromptBuilder.kt` 原先三处
 *   把 `knowledgeRepo.readFile(..., "memory/lessons.md")` 原始字节直接交给段的读侧
 *   （`buildKnowledgeSection` / `buildCoreKnowledgeSubset` / `buildReflectUserPrompt`，
 *   原 `:303`/`:424`/`:459` → `domain/prompt/PromptKnowledgeSection.kt:77` 的 `PromptBudget.lastH1Blocks`）
 *   全部接成 `LessonDoc.purify(readFile(...))`——模板行从此既进不了界面也进不了 prompt。
 *   字节证据随重录钉在 `PromptByteFreezeBaselineTest`（夹具那份干净 LESSONS 洗完逐字恒等，
 *   接线本身零字节贡献）；`LessonDocTidyTest.purifiedLessonsReachThePromptWithoutTemplateLines`
 *   的 rawSeed 缺口读数直接喂段、不经 `PromptBuilder`，接线后仍绿——它的撤登记与否归那颗格子自判；
 * - [tidy] / [nextExtractionNumber]：旧数据按「有效提取批」整理与编号。
 *
 * ⚠ 判据一律**不靠关键词**："测试""格式"这类词真实经验也会用。这里只认两样东西——
 * ① 提取节头的形状（一级标题 + 方括号里四位 ISO 日期开头 + 「提取」）；
 * ② 旧模板里那几行的**逐字文本**（[TEMPLATE_LINES]）。对不上这两样的行，原样保留。
 *
 * ⚠ "一批"的认亲只有 [isWrappedHeading] 一处口径（同号或同 ISO 日期才算同一批的包裹标题）：
 * 只看"上一批没正文"会把**紧随空批之后的真批次**当包裹吞掉——编号被吞、`changed` 永远为 true，
 * 第8节第3条 的"一批一个编号"与 第8节第2条 的"可重入"同时破。认不出的一律判两批（多留一条异常读数，不并批）。
 *
 * ⚠ 节头与正文之间的那道空行只在**有正文时**才补（见 [render]）：空批也照抄两个换行就会吐出
 * 两个空行，而写链拼新批只给一个，两边逐字比不过 ⇒ 每次整理都算"改过了"。
 *
 * ⚠ 剥 HTML 注释只有 [stripComments] 这一处、且是**整段一次剥完**（DOT_MATCHES_ALL）。
 * 分步剥（先按行找 `<!--` 再找 `-->`）会吞掉夹在两段注释之间的一切，那是本仓库踩过的坑。
 */
internal object LessonDoc {

    /** 用户经验文件在库内的相对路径 */
    const val LESSONS_PATH = "memory/lessons.md"

    /** 模板留下的那一条文档标题——读侧当模板壳处理 */
    private const val DOC_TITLE = "# 经验库"

    /**
     * 提取节头：一级标题 + 方括号内以四位 ISO 日期开头 + 同一行里出现「提取」。
     *
     * 与 [sectionHeader] 写出去的那一行逐字同构，同时容忍历史里两种畸形写法
     * （`# [2026-09-17 10:00] 第1次提取` 与 `# [2026-09-17 ]第1次提取`）。
     * `# 经验库` 这种文档标题、`## 测试接法` 这种二级分类都不算。
     */
    private val EXTRACTION_HEADING =
        Regex("""^#[ \t]*\[[ \t]*\d{4}-\d{2}-\d{2}[^\]\n]*][^\n]*提取""")

    /**
     * 一级标题行（`#` + 空白 + 非 `#` 开头的内容）。只用于 [modelBody] 那一侧的包裹剥除，
     * 判据是**形状**而不是关键词——正文里出现"测试""格式""提取"都不受影响，
     * `##` 二级分类与更深层级一律不算。
     */
    private val LEVEL_ONE_HEADING = Regex("""^#[ \t]+[^#\s].*$""")

    /**
     * 二级分类标题（整理时只判它的块里还有没有正文）。
     *
     * ⚠ 这颗是**整行判定**（`Regex.matches` 要求吞掉整个输入），所以末尾必须带 `.*`：
     * 写成 `^##[ \t]+\S` 时 `matches("## 测试接法")` 实测是 **false**——空类别就永远剥不掉，
     * 旧 seed 模板洗完还剩五颗光标题（`LessonDocTidyTest.oldSeededTemplateWashesToNothingAtAll`
     * 2026-10-04 15:27 实测到的正是这一条）。`###` 及更深层级依旧不算（第三个字符不是空白）。
     */
    private val CATEGORY_HEADING = Regex("""^##[ \t]+\S.*""")

    /** 节头里声明的编号（第N次）——读得到才算数，读不到就当「没有声明」 */
    private val DECLARED_NUMBER = Regex("""第[ \t]*(\d+)[ \t]*次""")

    /** 节头里方括号那段日期文本：整理时原样保留，不替谁补一个日期 */
    private val BRACKET_DATE = Regex("""\[[ \t]*([^\]\n]*)]""")

    /** 方括号日期前 10 位的形状（`yyyy-MM-dd`）——相邻节头"认亲"退一步时才用它 */
    private val ISO_DATE_PREFIX = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * 还挂在引擎资产里、模型会整行抄回来的模板行（[com.lovebrain.app.domain.AssetRegistry.LESSONS]）。
     *
     * 逐字登记的模板行**分两档**，两档来源不同、被谁钉住也不同。混成一档就会出
     * "改了资产没人补登记"那种静默漂移（2026-10-03 真的漂过一次：引擎资产里
     * `### 字段说明` 被改名成 `### 字段含义（…）`，登记表当时没跟上，新抄出去的标题就剥不掉了）。
     *
     * 这一档由单测 `LessonDocTidyTest.assetRulerStillSeesEveryPromptCopiedTemplateLine` 拿
     * `assets/engine/knowledge_prompt/lessons.md` 的全文逐行比对——资产里改了字而这里没跟上，当场红。
     */
    internal val PROMPT_COPIED_TEMPLATE_LINES: Set<String> = setOf(
        // —— `## 输出格式` 那五格占位条目：横线上面是字段名，模型连横线下面一起抄 ——
        "- 测试：行为；接法：有效回复；禁用：无效回复；内核：她真正需求",
        "- 做对：行为；有效：为什么；复用：什么场景用",
        "- 触发：她什么状态；放大：做什么；复用：什么场景用",
        "- 踩坑：做错什么；教训：下次怎么做；补救：已踩了怎么修",
        "- 从__到__：信号描述",
        // —— `### 字段含义…` 那一段：标题加五行解释，历史上被整段抄进过用户库 ——
        "### 字段含义（这一段只给你读，抄进输出就是污染用户文件）",
        "- 测试接法：行为（她做了什么探索性行为）；接法（你应该怎么回，可写原话或模式）；禁用（千万别怎么回）；内核（她真正想了解什么）",
        "- 做对的：做对（你做了什么）；有效（为什么有效，写机制）；复用（什么情况下可以再用）",
        "- 加分项：触发（她表现出的积极信号）；放大（你做什么能让她更开心）；复用（什么场景适用）",
        "- 踩过的坑：踩坑（你做错了什么）；教训（下次该怎么做）；补救（已经踩了可以怎么挽回）",
        "- 阶段转换：从__到__：信号（写具体可观察的推进或回退信号，≤15字）"
    )

    /**
     * 不在生成链路上、只可能躺在用户历史文件里的模板行（一次性整理用）。
     *
     * 这一档由单测 `LessonDocTidyTest.oldSeededTemplateWashesToNothingAtAll` 从**反方向**钉：
     * 拿"2026-10-03 之前那份 seed 模板全文"当夹具洗完必须是空串——少登记一行，
     * 那份历史模板就有一行洗不掉。（`**字段说明**` 两种加粗写法是模型自己的变体，
     * 两份资产里都没有它们，所以它们不进上一档那颗正向尺，只靠这条反向夹具兜着"多剥"的风险。）
     */
    internal val LEGACY_TEMPLATE_LINES: Set<String> = setOf(
        // 现仍在 `assets/schema/lessons.md`（建库 seed 的唯一来源）里的那颗文档标题，以及 2026-10-03
        // 之前那份 seed 模板的序言行——两句都不是模型抄来的，是**程序当年写进用户文件**的。
        DOC_TITLE,
        "军师自动追加提取节，节头格式：# [yyyy-MM-dd HH:mm] 第N次提取（模板内不放示例节）",
        // 旧引擎资产的字段说明标题，与模型抄它时的两种加粗写法
        "### 字段说明",
        "**字段说明**：",
        "**字段说明**"
    )

    /**
     * 读侧与整理侧共用的那一张模板表（= 上面两档之和；声明在最后是因为 object 的初始化按书写顺序走，
     * 早于两档声明就只会拿到 null）。
     *
     * ⚠ 两档都只认**整行逐字相同**。模型改写过的同义行算不出来，就原样留着——那种行只能靠
     * 提示词那一侧不再喂给它抄。判据一律**不靠关键词**："测试""格式"这类词真实经验也会用，
     * 关键词网会吃掉正文。
     */
    private val TEMPLATE_LINES: Set<String> = PROMPT_COPIED_TEMPLATE_LINES + LEGACY_TEMPLATE_LINES

    /** 模型"这批没有东西"的那一句（整行只有它）——不新增批、不增次数 */
    fun isNoNewLessons(raw: String): Boolean = raw.trim().removeSuffix("。") == "无新经验"

    /** 程序写出去的那一行节头：日期与编号只从这里出 */
    fun sectionHeader(time: String, number: Int): String = "# [$time] 第${number}次提取"

    /**
     * 一批「有效提取批」：连着出现的包裹节头（中间没有正文）算同一批，
     * 所以 [headings] 可以有多条，历史里的 `1、1、3、1、5、1` 就靠这一条收敛。
     */
    internal data class Batch(
        val headings: List<String>,
        val body: List<String>,
        val isFrontMatter: Boolean
    ) {
        /** 这批有没有真实正文（只有空行不算） */
        val hasContent: Boolean get() = body.any { it.isNotBlank() }

        /** 同一批里多出来的包裹节头条数（>0 = 模型又自己编了一次标题） */
        val wrappedDuplicates: Int get() = headings.size - 1

        /** 节头里声明过的编号；读不到 = null，不是 0（"没有该节"与"有小节读不出"分两档） */
        val declaredNumber: Int?
            get() = headings.firstOrNull()
                ?.let { DECLARED_NUMBER.find(it)?.groupValues?.get(1)?.toIntOrNull() }

        /** 方括号里的原始日期文本；没有方括号 = null，整理时不替它编 */
        val dateText: String?
            get() = headings.firstOrNull()
                ?.let { BRACKET_DATE.find(it)?.groupValues?.get(1)?.trim() }
                ?.takeIf { it.isNotEmpty() }
    }

    /** 整文件一次洗完的产物：[batches] 含文档序言那一格（[Batch.isFrontMatter]） */
    private fun parse(text: String): List<Batch> {
        val lines = stripComments(text.replace("\r\n", "\n")).split("\n")
        val out = mutableListOf<MutableList<String>>() // 每格的节头（可以是 0、1 或多条）
        val bodies = mutableListOf<MutableList<String>>()

        fun openBatch(heading: String?) {
            out.add(mutableListOf<String>().apply { heading?.let { add(it) } })
            bodies.add(mutableListOf())
        }

        for (raw in lines) {
            val line = raw.trimEnd()
            if (EXTRACTION_HEADING.matches(line)) {
                val last = out.lastOrNull()
                // 上一批还没有正文 **且这两颗标题说的是同一次提取** ⇒ 同一批的包裹标题，不另起一批。
                // 后半个条件不能省：省掉了，"只有标题的空批"会把紧随其后的**真批次**当包裹吞掉
                // （实测：整理后的 4 批被数成 3 批、第二次整理又改写全文 ⇒ 可重入破功、编号从 5 退回 4，
                //  见 LessonDocTidyProductionTest 与 KnowledgeRepositoryLessonsRewriteTest 2026-10-04 15:27 的红）。
                val swallowed = last != null &&
                    bodies.last().none { it.isNotBlank() } &&
                    isWrappedHeading(last.lastOrNull(), line)
                if (last == null || !swallowed) openBatch(line) else last.add(line)
                continue
            }
            if (out.isEmpty()) openBatch(null)
            bodies.last().add(raw)
        }
        return out.mapIndexed { i, headings ->
            Batch(
                headings = headings.toList(),
                body = sanitizeBody(bodies[i]),
                isFrontMatter = headings.isEmpty()
            )
        }
    }

    /**
     * 相邻两颗节头是不是**同一次提取**的两条标题（程序写一条 + 模型照着抄一条）。
     *
     * 认亲只看形状，不看关键词：
     * - 两边都声明了「第N次」⇒ 编号相同才算一批（归档实测：`第1次提取`/`第1次提取`、
     *   `第3次提取`/`]第3次提取` 都是同号）；
     * - 至少一边没声明编号 ⇒ 退一步比方括号里那半截 ISO 日期（`[yyyy-MM-dd` 前 10 位）；
     * - 两边都认不出 ⇒ **判成两批**。这一侧偏保守：多留一颗标题只是多一条异常读数，
     *   误并一批却会把真实批次的编号吞掉，第8节第3条 明令不做后者。
     */
    private fun isWrappedHeading(prev: String?, current: String): Boolean {
        if (prev == null) return false
        val a = DECLARED_NUMBER.find(prev)?.groupValues?.get(1)
        val b = DECLARED_NUMBER.find(current)?.groupValues?.get(1)
        if (a != null && b != null) return a == b
        val da = isoDateOf(prev)
        return da != null && da == isoDateOf(current)
    }

    /** 节头方括号里那半截日期的 ISO 部分（`yyyy-MM-dd`）；凑不满 10 位就不算日期 */
    private fun isoDateOf(heading: String): String? =
        BRACKET_DATE.find(heading)?.groupValues?.get(1)?.trim()?.take(10)
            ?.takeIf { ISO_DATE_PREFIX.matches(it) }

    /**
     * 批内正文的清洗：逐字模板行整行去掉；去掉之后**没有正文的二级分类标题**一起去掉
     * （空类别既不进界面也不进 prompt），其余行一个字不动。
     */
    private fun sanitizeBody(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var pendingCategory: String? = null
        for (raw in lines) {
            val trimmed = raw.trim()
            if (trimmed in TEMPLATE_LINES) continue
            if (CATEGORY_HEADING.matches(raw.trimEnd())) {
                // 上一颗类别标题如果一直没等到正文，就在这里被丢掉
                pendingCategory = raw.trimEnd()
                continue
            }
            if (trimmed.isNotEmpty()) {
                pendingCategory?.let { out.add(it) }
                pendingCategory = null
            }
            out.add(raw)
        }
        return out
    }

    private fun stripComments(text: String): String =
        Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).replace(text, "")

    /** 把批表拼回文本；[renumber] = true 时按文件顺序重编 1、2、3…，日期沿用原始那一段 */
    private fun render(batches: List<Batch>, renumber: Boolean): String {
        val sb = StringBuilder()
        var ordinal = 0
        for (b in batches) {
            val body = b.body.joinToString("\n").trim()
            if (b.isFrontMatter) {
                if (body.isNotEmpty()) sb.append(body).append("\n\n")
                continue
            }
            ordinal++
            val heading = if (!renumber) {
                b.headings.first()
            } else {
                // 节头的写法只有 [sectionHeader] 一处：整理重编与追加新批必须吐出同一形状，
                // 否则"整理过一次"的文件下一批就会被写出另一种节头（那里是第二处字面量，早就漂了）
                val date = b.dateText
                if (date != null) sectionHeader(date, ordinal) else b.headings.first()
            }
            sb.append(heading)
            // 只有正文时才补那一道空行：空批也照抄 "标题\n + \n\n" 就会吐出**两个**空行，
            // 而调用方拼新批时只给一个（`tidied.text + "\n\n" + header`）——差的那一个空行让
            // `changed` 永远为 true，"整理过一次"就变成每次开页都改写用户文件（第8节第2条 可重入）。
            if (body.isNotEmpty()) sb.append("\n\n").append(body)
            sb.append("\n\n")
        }
        return sb.toString().trim()
    }

    /**
     * 读侧净化（编辑页预览 + 生成 prompt 共用）：去注释、去模板壳、折叠同一批的包裹节头、
     * 去空类别。**不重编编号**——屏幕上要看见的是文件真实的编号，不是第二个真相。
     */
    fun purify(content: String): String = render(parse(content), renumber = false)

    /** 一次整理的产物：正文一个字不少，编号按文件顺序收敛成 1、2、3… */
    internal data class TidyResult(
        val text: String,
        val batchCount: Int,
        /** 这批历史里说不清的部分（包裹重复 / 声明编号与顺序不符 / 只有标题没有正文），只记账不删数据 */
        val anomalies: List<String>,
        /** 与原文件逐字符不同（false = 这份文件已经很干净，可重入调用不会二次改写） */
        val changed: Boolean
    )

    /**
     * 旧数据整理：**可重入**（`tidy(tidy(x)).text == tidy(x).text`，批数与异常读数都不许再动，
     * 由 `LessonDocTidyTest.tidyKeepsEveryRealLineAndIsReentrant` 连跑两次钉住）、
     * 只按 [TEMPLATE_LINES] 与节头形状动手、真实原始日期保留、编号按文件顺序重发（走 [sectionHeader]，
     * 与追加新批同一颗写法）。**说不清的块留着不删**，只往 [TidyResult.anomalies] 记一行。
     *
     * ⚠ 生产写盘链已经接上它（ ）：调用点是
     * `KnowledgeTriggerCoordinator.extractLessons`，走 `KnowledgeWritePort.readTidyAndReplaceWithRevisionCheck`
     * 那**一次**事务——读正文、整理、发号、revision 检查、快照、整篇替换全在 data/ 那一颗 `fileMutex` 里跑完，
     * 判据本身仍然只住在这里（data/ 不认得 [tidy]，它拿到的是调用方交回的那一篇）。
     * 三条边界各自的证据：`KnowledgeRepositoryLessonsRewriteTest`（快照/条件替换/锁内发号）、
     * `LessonDocTidyProductionTest`（用户那份 `1、1、3、1、5、1` 真的被整理、写失败不报成功）。
     *
     * ⚠ 仍然欠一格才算 第8节第2条 做完：**开页整理**。`KbEditActivity` 现在只在预览上跑 [purify]，
     * 用户不触发提取就看不见整理结果；接法是把上面那颗口拿来用一次
     * `compose = { LessonDoc.tidy(it).text.takeIf { t -> t != it } }`（`changed == false` 交回 null ⇒ 一个字节都不写）。
     * 那颗文件不在（/主线程），缺口与接线点写在  的
     *  lessons 那份 （当前在  下）
     * （同一颗文件里 prompt 那一路没接 [purify] 的三处读数**已于 2026-10-05  接上**，见上方 [purify] 条目）。
     * 2026-10-04  这一轮把 15:27 那 9 条红逐条分诊过了：整理逻辑自身错 3 条
     * （空类别剥不掉 / 空批吞真批 / 空批多空行），夹具与断言写错 6 条，逐条读数在
     * 同一份  lessons 归档目录的 probe 子目录里（`after-fix-readings.txt`）。
     */
    fun tidy(content: String): TidyResult {
        val normalized = content.replace("\r\n", "\n")
        val batches = parse(normalized).filterNot { it.isFrontMatter && it.body.all { l -> l.isBlank() } }
        val text = render(batches, renumber = true)
        val real = batches.filterNot { it.isFrontMatter }
        val anomalies = mutableListOf<String>()
        real.forEachIndexed { i, b ->
            if (b.wrappedDuplicates > 0) {
                anomalies += "第${i + 1}批里有 ${b.wrappedDuplicates} 条包裹节头（模型重复输出了标题）"
            }
            if (!b.hasContent) {
                anomalies += "节头「${b.headings.first()}」下面没有正文，原样保留"
            }
            val declared = b.declaredNumber
            if (declared != null && declared != i + 1) {
                anomalies += "节头「${b.headings.first()}」声明第${declared}次，按文件顺序应为第${i + 1}次"
            }
            if (b.dateText == null) {
                anomalies += "节头「${b.headings.first()}」读不出日期，整理时不替它编"
            }
        }
        return TidyResult(
            text = text,
            batchCount = real.size,
            anomalies = anomalies,
            changed = text != normalized.trim()
        )
    }

    /**
     * 已经攒了几批真实提取（不含文档序言）。
     *
     * ⚠ 这里数的是**有效提取批**，不是一级标题数量——旧数据里同一批常有两条节头
     * （程序一条 + 模型一条），按标题数就会把 1、3、5 一路翻倍。
     */
    fun batchCount(content: String): Int =
        parse(content.replace("\r\n", "\n")).count { !it.isFrontMatter }

    /**
     * 下一批该写第几次：**批数**与**文件里已声明的最大编号**取大的那个再 +1。
     *
     * 取 max 是为了不撞号：没整理过的旧文件里编号可能已经跳到 5（历史遗留），
     * 只按批数发号就会写出第二个 4。整理过之后两边相等，往后就是连续的一、二、三。
     */
    fun nextExtractionNumber(content: String): Int {
        val batches = parse(content.replace("\r\n", "\n")).filterNot { it.isFrontMatter }
        val declaredMax = batches.mapNotNull { it.declaredNumber }.maxOrNull() ?: 0
        return maxOf(batches.size, declaredMax) + 1
    }

    /** 这一批文本里所有"包裹重复"的条数（写日志用，不参与编号） */
    fun wrappedDuplicateCount(content: String): Int =
        parse(content.replace("\r\n", "\n")).sumOf { kotlin.math.max(0, it.headings.size - 1) }

    /**
     * 模型这一批的输出 → 只留类别与正文。
     *
     * **这一颗才是"一批一个编号"的承重墙**，提示词那一半只是减压力。实测到的形状是
     *  那种成对节头：
     * 程序写一条 `# [2026-09-03 18:54] 第1次提取`，模型紧跟着又吐一条
     * `# [2026-09-03 18:54] 第1次提取`——它照着自己读到的归档格式仿（`KnowledgeArchiveService
     * .archiveEntry` 写出去的话题条目本来就是 `# [时间戳] 话题名`），而 `buildLessonsUserPrompt`
     * 既不喂钟、也不喂已有经验，它算不出日期与编号，只能仿一个。
     *
     * 所以这里**无条件剥掉模型输出里的每一条一级标题**，不管它长哪个形状：
     * 契约里"经验正文"只有二级分类，一级标题一律是包裹物。二级分类一个字不动，
     * 正文里出现"测试""格式""提取"这些词也不看（判据是形状，不是关键词）。
     * 洗完什么都没有了就交回空串——调用方据此**不新增批、不增次数**。
     */
    fun modelBody(raw: String): String {
        val kept = stripComments(raw.replace("\r\n", "\n")).split("\n")
            // 一级标题是包裹物；「无新经验」那一整行是"这批没东西"的哨兵，两者都不是经验正文。
            // 哨兵在这里也要剥，是因为调用方那道守卫（isNoNewLessons）判的是**整份输出**，
            // 而这里判的是**逐行**——两边口径不一致时，"模型在标题下面写一句无新经验"就会被当正文追加，
            // 于是那一格的正向对照（洗完必须是空的）说的就不是同一件事了。
            .filterNot { isLevelOneHeading(it) || isNoNewLessons(it) }
        return purify(kept.joinToString("\n")).trim()
    }

    /** 模型这批输出里吐了几条一级标题（写日志用；>0 就说明提示词那一半没管住它） */
    fun modelHeadingCount(raw: String): Int =
        stripComments(raw.replace("\r\n", "\n")).split("\n").count { isLevelOneHeading(it) }

    /**
     * 一级标题行：`#` + 空白 + 不是 `#` 的内容。
     *
     * `##` 与更深的层级一律不算——二级分类是经验正文的结构，一个字都不许动。
     */
    private fun isLevelOneHeading(line: String): Boolean =
        LEVEL_ONE_HEADING.matches(line.trimEnd())
}

/**
 * 统计 lessons.md 中真实提取批数（ 计数口径）。
 *
 * ⚠ 这颗现在是 [LessonDoc.batchCount] 的别名，不再是"一级标题数量"：`# [四位年-` 那种
 * 裸正则会把模型重复输出的包裹标题也数成一批，编号就一路 1、3、5 地翻。
 * 保留这个名字是给旧调用与旧用例的，编号一律走 [LessonDoc.nextExtractionNumber]。
 */
internal fun countLessonSections(existing: String): Int = LessonDoc.batchCount(existing)
