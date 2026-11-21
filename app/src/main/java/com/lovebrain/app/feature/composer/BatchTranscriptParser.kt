package com.lovebrain.app.feature.composer

/**
 * 微信批量复制文本的解析器（Issue #7「复制粘贴支持多条对话记录」）。
 *
 * 纯 Kotlin、零 Android 依赖、无共享可变状态：不给它接 Context/Compose，
 * 它就永远可以在 JVM 上单测（`BatchTranscriptParserTest`）。
 * UI 与 ViewModel 只消费 [parse] 的产物，不复制第二份规则。
 *
 * ## 识别的三种微信复制格式
 *
 * 1. **块格式**（聊天里多选 → 复制，逐条一块）：
 *    ```
 *    昵称
 *    2024-01-01 12:00        （或 昨天 14:30 / 下午3:24 等时间行）
 *    消息内容（可多行，连续普通行都算这一条）
 *    ```
 *    发言人行用**一行前瞻**确认：只有下一非空行是时间行，它才算发言人——
 *    绝不因为"孤零零一行"就猜一个发言人出来。
 * 2. **冒号行格式**（合并转发/部分版本复制）：
 *    ```
 *    昵称：内容
 *    昵称：内容（可多行）
 *    ```
 *    全角冒号一律算新记录；半角冒号只有后面跟空白才算（防止把 `比例3:2`、`https://…`
 *    这类正文当发言人拆掉）。冒号行是**冒号/编号格式**的记录分隔符；块格式正文里的
 *    "地址：北京市"这类行（昵称不是已知发言人）照旧归并进当前条。
 *    冒号/编号格式里的**续行形态前缀**不硬拆为新发言人：常见字段词（"地址：…""备注：…"）
 *    与代词+从句动词开头的多字前缀（"我想说：其实我不饿"是正文里的冒号句，不是发言人叫
 *    "我想说"），并入上一条 + warning 标注，由预览界面裁决。该名字已作为发言人确认过、
 *    或作为代词开头前缀在全文出现 ≥2 次（再出现的真昵称，如"我的宝贝"）时照常换条。
 *    单字代词（我/她/他）、代词开头但非动词承接的真实昵称（我的宝贝）与未知短昵称（B）
 *    仍是正常发言人。
 * 3. **编号转发格式**（多选 → 合并转发 → 复制）：
 *    ```
 *    1. 昵称
 *    2024-01-01 12:00:23
 *    内容
 *    2. 昵称 2024-01-01 12:00:30
 *    内容
 *    ```
 *
 * ## 两条不许破的边界
 *
 * - **未知行不静默丢弃**：归不到任何记录的行会并进 [Entry.speakerName] 为 null 的条目，
 *   进预览标出来，由用户显式处理（指定发言人或移除）；跳过的行（识别到发言人但无正文、
 *   无处归属的时间行）逐条写进 [Result.warnings]，绝不无痕消失。
 * - **不猜发言人**：解析器只把文本里**写着的**昵称交出去；昵称 → 她/我 的归属由用户在
 *   预览里显式指定，这里没有任何"默认算 HER"的路径。
 *
 * 多行正文用 `\n` 原样保留；时间串只做展示（[Entry.timestampText]），不参与入库——
 * 入库走现有 `ChatMessage`（提交时刻），对话顺序由预览条目顺序保证。
 */
object BatchTranscriptParser {

    /**
     * 解析出的一条候选消息。
     *
     * @param speakerName  文本里写着的发言人昵称；null = 未能识别发言人（预览必须标出，
     *                     且必须由用户显式指定她/我后才允许提交）
     * @param content      消息正文（多行以 `\n` 保留，已 trim）
     * @param timestampText 识别到的时间串（原样保留，仅预览展示）
     * @param sourceLineNo 该条在原始文本中的起始行号（1 起），预览与警告用它定位
     */
    data class Entry(
        val speakerName: String?,
        val content: String,
        val timestampText: String?,
        val sourceLineNo: Int
    )

    data class Result(
        val entries: List<Entry>,
        val warnings: List<String>
    )

    fun parse(raw: String): Result {
        if (raw.isBlank()) return Result(emptyList(), emptyList())
        // 换行统一 + 剪贴板不可见污染清洗（BOM/零宽字符）：它们不是正文，留着会破坏
        // 发言人行/时间行的锚定（trim 不吃 U+FEFF）。
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n')
            .replace('\uFEFF', ' ').replace('\u200B', ' ')
            .split('\n')
        // 再出现发言人例外要先看全文：同一冒号前缀出现 ≥2 次就是真昵称（哪怕代词开头），
        // 不能等逐行解析时「第一次见过」才知道——第一次出现时证据就已经在全文里了。
        val prefixCounts = HashMap<String, Int>()
        for (rawLine in lines) {
            val t = rawLine.trim()
            if (t.isEmpty()) continue
            val candidate = NUMBERED.find(t)?.groupValues?.get(2)?.trim() ?: t
            val m = COLON_LINE.find(candidate) ?: continue
            val name = m.groupValues[1].trim()
            if (name.isEmpty() || name.length > 30 || name.all { it.isDigit() }) continue
            prefixCounts[name] = (prefixCounts[name] ?: 0) + 1
        }
        // 例外只豁免代词开头的前缀：字段词（地址/备注）的续行保护仍只认 seenSpeakers，
        // 否则正文里重复出现的字段行会被误拆成发言人。
        val recurringSpeakers = prefixCounts.entries
            .filter { (name, count) -> count >= 2 && name.first() in PRONOUN_PREFIXES }
            .map { it.key }
            .toSet()
        val ctx = Ctx(recurringSpeakers)
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                // 空行在条目内部是正文的一部分（多行消息里的空行），先记账，
                // 等下一条普通行归并进当前条时再按原样补回——空行密集的粘贴不丢行。
                ctx.onBlankLine()
                i++
                continue
            }
            val lineNo = i + 1
            // 一行前瞻：下一非空行。块格式的发言人行只靠它确认（"昵称"下面必须是时间行）。
            var j = i + 1
            while (j < lines.size && lines[j].trim().isEmpty()) j++
            val nextLineNo = j + 1
            val nextLine = lines.getOrNull(j)?.trim().orEmpty()
            i = ctx.step(line, lineNo, nextLine, nextLineNo, i)
        }
        ctx.flushPending()
        ctx.closeOpen()
        return Result(ctx.entries, ctx.warnings)
    }

    /**
     * 轻点自动解析的三态结果——调用方据此决定「单条 / 浮层预览 / 整批」：
     *
     * - [NotTranscript]：不像多人转录 → 原文整段照旧走单条消息（普通文本永不被拆）；
     * - [NeedsReview]：已拆出 ≥2 条完备记录，但夹着需要人裁决的情况（转发标题被跳过、
     *   续行被并回正文、空壳条被略过……）→ 既不静默丢弃也不直吞成一条，交**既有**
     *   浮层预览预填原文与 [NeedsReview.warnings]，用户看过标记后再显式提交；
     * - [Ready]：≥2 条完备记录且零 warning → 可按归属表整批入库。
     */
    sealed interface AutoSplit {
        data class Ready(val entries: List<Entry>) : AutoSplit
        data class NeedsReview(val entries: List<Entry>, val warnings: List<String>) : AutoSplit
        data object NotTranscript : AutoSplit
    }

    /**
     * 轻点自动解析的共享契约（复用 [parse]，不另立第二份规则）。判据按序：
     *
     * 1. 少于 2 条 → [AutoSplit.NotTranscript]（单行/普通多行文本不拆）；
     * 2. 任一条没有文本里写着的发言人（speakerName == null）→
     *    [AutoSplit.NotTranscript]——普通多行文本不长这样，保住"普通文本仍是一条消息"；
     * 3. 发言人**全是**续行形态前缀（字段词 / 代词+从句动词前缀）→
     *    [AutoSplit.NotTranscript]：整段都是"地址：…""我想说：…"，大概率是一条正文里的
     *    冒号句而非真多人对话；
     * 4. 有 warning（跳过行 / 续行合并 / 空壳条等需要人裁决）→ [AutoSplit.NeedsReview]；
     * 5. 否则 → [AutoSplit.Ready]。
     *
     * 纯函数、零 Android 依赖，JVM 可单测。
     */
    fun autoSplit(raw: String): AutoSplit {
        val result = parse(raw)
        val entries = result.entries
        if (entries.size < 2) return AutoSplit.NotTranscript
        if (entries.any { it.speakerName == null }) return AutoSplit.NotTranscript
        if (entries.all { isContinuationPrefix(it.speakerName!!, emptySet()) }) {
            return AutoSplit.NotTranscript
        }
        if (result.warnings.isNotEmpty()) return AutoSplit.NeedsReview(entries, result.warnings)
        return AutoSplit.Ready(entries)
    }

    /** 单次 [parse] 的全部可变状态——进出都是局部量，object 本身无状态 */
    private class Ctx(private val recurringSpeakers: Set<String>) {
        val entries = mutableListOf<Entry>()
        val warnings = mutableListOf<String>()

        /** 已确认的发言人昵称：条目真正带上正文时才登记（空壳条不登记，免得污染续行判定）；
         *  块格式借冒号换条、以及续行形态「已确认发言人」例外的判据 */
        private val seenSpeakers = mutableSetOf<String>()

        /** 当前条是按哪种格式开的：冒号/编号格式里冒号行就是记录分隔符，块格式里不是 */
        private var openOrigin = Origin.UNKNOWN

        private enum class Origin { BLOCK, COLON, NUMBERED, UNKNOWN }

        /** 待定行：编号转发里"名字在编号行、时间没跟在同行"那一种，等下一行确认 */
        private var pendingText: String? = null
        private var pendingLineNo = 0

        /** 连续空行记账：只在有进行中条目/待定行时记，归并下一条普通行时补回 */
        private var pendingBlanks = 0

        fun onBlankLine() {
            if (openExists || pendingText != null) pendingBlanks++
        }

        // 正在累积的一条：speaker 为 null = 未识别发言人
        private var openExists = false
        private var openSpeaker: String? = null
        private var openTime: String? = null
        private var openLineNo = 0
        private var openLines = mutableListOf<String>()

        /** 处理一行，返回新的游标 i（普通行 +1；借前瞻消费时间行的块头 +2） */
        fun step(line: String, lineNo: Int, nextLine: String, nextLineNo: Int, i: Int): Int {
            val blanks = pendingBlanks
            pendingBlanks = 0

            // ── 转发标题/导出说明行："小美和小红的聊天记录"、"以下为聊天记录" ──
            // 合并转发复制的首行。若不当记录处理，会把后面整段冒号记录吞进一个
            // 未识别条目（未开条目时冒号行不拆条）。跳过 + 警告，不静默丢弃。
            if (FORWARDED_TITLE_LINE.matches(line) || EXPORT_MARKER_LINE.matches(line)) {
                flushPending()
                warnings.add("第 $lineNo 行：转发标题/导出说明「$line」，已跳过")
                return i + 1
            }

            // ── 时间行：块格式的发言人确认锚 ─────────────────────────────
            if (TIME_LINE.matches(line)) {
                val pending = pendingText
                when {
                    pending != null -> {
                        startOpen(pending, line, pendingLineNo, Origin.BLOCK)
                        pendingText = null
                    }
                    // 已开的条目还没有任何正文（编号头/发言人行的延续）：时间是它的元数据
                    openExists && openLines.isEmpty() -> openTime = line
                    openExists ->
                        warnings.add("第 $lineNo 行：时间行「$line」无处归属（不是紧跟发言人），已跳过")
                    else ->
                        warnings.add("第 $lineNo 行：时间行「$line」前面没有发言人，已跳过")
                }
                return i + 1
            }

            // ── 编号转发：`1. 昵称` / `1. 昵称 12:00` / `1. 昵称：内容` ─────
            if (NUMBERED.matches(line)) {
                flushPending()
                val rest = NUMBERED.find(line)!!.groupValues[2].trim()
                when {
                    rest.isEmpty() ->
                        // 只有编号、名字在下一行：下一行是时间就当块头，否则开未知发言人的空记录
                        if (TIME_LINE.matches(nextLine)) {
                            startOpen(null, nextLine, lineNo, Origin.NUMBERED)
                            return i + 2
                        } else {
                            startOpen(null, null, lineNo, Origin.NUMBERED)
                            return i + 1
                        }
                    NAME_THEN_TIME.matches(rest) -> {
                        val m = NAME_THEN_TIME.find(rest)!!
                        startOpen(m.groupValues[1].trim(), m.groupValues[2].trim(), lineNo, Origin.NUMBERED)
                    }
                    TIME_LINE.matches(rest) -> startOpen(null, rest, lineNo, Origin.NUMBERED)
                    else -> {
                        val colon = colonSplit(rest, allowNewSpeaker = true)
                        if (colon != null) {
                            startOpen(colon.first, null, lineNo, Origin.COLON)
                            if (colon.second.isNotBlank()) addContent(colon.second)
                        } else if (TIME_LINE.matches(nextLine)) {
                            // `1. 昵称` 换行 `时间`：名字被下一行的时间确认
                            startOpen(rest, nextLine, lineNo, Origin.NUMBERED)
                            return i + 2
                        } else {
                            // 名字没被时间确认：留待定，下一行普通行来时按未识别条目交预览
                            closeOpen()
                            pendingText = rest
                            pendingLineNo = lineNo
                        }
                    }
                }
                return i + 1
            }

            // ── `昵称 12:00`（昵称与时间同一行）：微信转录常见形态，进行中条目里也认 ──
            // 连续多条同格式时，第二条起也必须换条（旧条件要求"没有进行中记录"，
            // 第二个昵称+时间被并进上一条正文 → 只剩 1 条 → 整段退化成一大坨）。
            if (pendingText == null && NAME_THEN_TIME.matches(line)) {
                val m = NAME_THEN_TIME.find(line)!!
                val name = m.groupValues[1].trim()
                val time = m.groupValues[2].trim()
                // 续行护栏：进行中条目里"我想说 12:00"这类字段词/代词+从句动词前缀是
                // 上一条正文，不猜成新发言人——并入正文 + warning 交预览裁决（与冒号续行同口径）。
                if (openExists && isContinuationPrefix(name, seenSpeakers, recurringSpeakers)) {
                    if (openLines.isNotEmpty() && blanks > 0) {
                        repeat(blanks) { openLines.add("") }
                    }
                    addContent(line)
                    warnings.add(
                        "第 $lineNo 行：「$line」疑似上一条正文的续行（「$name」是字段词/代词+动词前缀，" +
                            "未拆为新发言人）；如确是新发言人，请在预览中手动处理"
                    )
                    return i + 1
                }
                startOpen(name, time, lineNo, Origin.BLOCK)
                return i + 1
            }

            // ── 冒号行：`昵称：内容`（每行一条，正文多行时后续普通行归并进来）──
            // 什么时候认：还没有进行中的条、昵称是再出现的已知发言人、或当前条本身就是
            // 冒号/编号格式开的（这两种格式里冒号行就是记录分隔符）。
            // 块格式正文里"地址：北京市"这种行不是已知发言人，照旧归并进当前条。
            colonSplit(
                line,
                allowNewSpeaker = !openExists ||
                    openOrigin == Origin.COLON || openOrigin == Origin.NUMBERED
            )?.let { (name, content) ->
                // 续行不硬拆为发言人：已开的冒号/编号条目里，前缀是续行形态（字段词，或
                // 代词+从句动词的多字前缀）且未确认过是发言人、也不是全文再出现的昵称时，
                // 这行是上一条正文的冒号句（"我想说：其实我不饿"），并入上一条 + warning
                // 交预览裁决，而非猜一个新昵称。
                if (openExists &&
                    (openOrigin == Origin.COLON || openOrigin == Origin.NUMBERED) &&
                    content.isNotBlank() &&
                    isContinuationPrefix(name, seenSpeakers, recurringSpeakers)
                ) {
                    if (openLines.isNotEmpty() && blanks > 0) {
                        repeat(blanks) { openLines.add("") }
                    }
                    addContent(line)
                    warnings.add(
                        "第 $lineNo 行：「$line」疑似上一条正文的续行（「$name」是字段词/代词+动词前缀，" +
                            "未拆为新发言人）；如确是新发言人，请在预览中手动处理"
                    )
                    return i + 1
                }
                flushPending()
                startOpen(name, null, lineNo, Origin.COLON)
                if (content.isNotBlank()) addContent(content)
                return i + 1
            }

            // ── 普通行：块头（下一行是时间）、正文延续或未识别内容 ──────────
            if (pendingText != null) {
                // 待定行没有被时间行确认：它不是发言人，按未识别条目交预览
                flushPending()
            }
            if (TIME_LINE.matches(nextLine)) {
                // 这一行下面紧跟时间行 ⇒ 它是块格式的发言人行（被时间确认，不是猜的）
                startOpen(line, nextLine, lineNo, Origin.BLOCK)
                return i + 2
            }
            if (!openExists) {
                startOpen(null, null, lineNo, Origin.UNKNOWN)
            }
            if (openLines.isNotEmpty() && blanks > 0) {
                // 条目内部的空行是正文：原样补回（开条目后的前导空行由 trim 吃掉）
                repeat(blanks) { openLines.add("") }
            }
            addContent(line)
            return i + 1
        }

        fun closeOpen() {
            if (!openExists) return
            val content = openLines.joinToString("\n").trim()
            if (content.isEmpty()) {
                warnings.add("第 $openLineNo 行起：识别到发言人「${openSpeaker ?: "？"}」但没有正文，该条已跳过")
            } else {
                entries.add(Entry(openSpeaker, content, openTime, openLineNo))
            }
            openExists = false
            openSpeaker = null
            openTime = null
            openLines = mutableListOf()
        }

        /** 待定行没有被时间行确认：按未识别条目交给预览（不静默丢弃，也不猜成发言人） */
        fun flushPending() {
            val text = pendingText ?: return
            entries.add(Entry(null, text, null, pendingLineNo))
            pendingText = null
        }

        private fun startOpen(speaker: String?, time: String?, lineNo: Int, origin: Origin) {
            closeOpen()
            openExists = true
            openSpeaker = speaker
            openTime = time
            openLineNo = lineNo
            openOrigin = origin
        }

        /**
         * 正文行入账，并在此刻才确认发言人：只有真正带正文的条目才登记 seenSpeakers。
         * 空壳条（识别到昵称却没正文、最终跳过）若提前登记，会让后续同名续行被误判成
         * 「已确认发言人」而硬拆——登记推迟到正文落地即可避免这种污染。
         */
        private fun addContent(line: String) {
            openLines.add(line)
            val speaker = openSpeaker
            if (speaker != null) seenSpeakers.add(speaker)
        }

        /**
         * 冒号行拆分：返回 (发言人, 内容)，不是冒号行返回 null。
         *
         * [allowNewSpeaker]=false（块格式正文）时只有已确认发言人（seenSpeakers）才认作
         * 新记录，未知昵称的冒号句留给正文归并；=true（未开条目，或当前条是冒号/编号格式）
         * 时任何合法昵称都拆。昵称是否属于「续行形态」由 [isContinuationPrefix] 另行判定，
         * 不在这里——这里只管「是不是冒号行、昵称合不合法」。
         */
        private fun colonSplit(line: String, allowNewSpeaker: Boolean): Pair<String, String>? {
            val m = COLON_LINE.find(line) ?: return null
            val name = m.groupValues[1].trim()
            val content = m.groupValues[2].trim()
            if (name.isEmpty() || name.length > 30) return null
            if (name.all { it.isDigit() }) return null
            if (!allowNewSpeaker && name !in seenSpeakers) return null
            return name to content
        }
    }

    /** 转发记录标题行（合并转发复制首行）："小美和小红的聊天记录" */
    private val FORWARDED_TITLE_LINE = Regex("^.{1,20}和.{1,20}的聊天记录$")

    /** 导出说明行："以下为聊天记录" / "以上是和小红的聊天记录" 等 */
    private val EXPORT_MARKER_LINE = Regex("^(?:以下|以上)(?:为|是).{0,20}聊天记录$")

    /**
     * 冒号行：全角 `：` 一律分隔；半角 `:` 必须后跟空白（防 URL / `比例3:2`）。
     * 发言人段 1..30 字、不含任何冒号。
     */
    private val COLON_LINE = Regex("^([^：:]{1,30})(?:：|:\\s)(.*)$")

    /**
     * 常见字段词：冒号/编号格式正文里"地址：…""备注：…"这类续行的冒号前缀。
     * 语义上是消息元数据而非人称，真实联系人用它当昵称极少见；真撞上时只要该名字
     * 已作为发言人确认过（seenSpeakers），仍按发言人换条。
     */
    private val CONTENT_LABELS = setOf(
        "地址", "地点", "时间", "日期", "电话", "手机", "座机", "备注", "附", "附言",
        "邮箱", "邮编", "网址", "链接", "姓名", "性别", "年龄", "坐标", "QQ", "微信"
    )

    /** 人称代词：多字冒号前缀以这些字开头（我想说/他觉得/她说）才可能是正文冒号句 */
    private const val PRONOUN_PREFIXES = "我你她他它"

    /**
     * 代词后的从句动词：「我想说/她担心/他打算」是正文里的冒号句；而「我的宝贝」这类
     * 代词开头的真实昵称后面不是动词，不算续行——只凭代词开头会误吞真发言人。
     */
    private val CLAUSE_VERBS = listOf(
        "想", "觉得", "说", "问", "看", "听", "猜", "担心", "怕", "希望",
        "要是", "如果", "打算", "感觉", "以为", "认为", "记得", "知道", "准备"
    )

    /**
     * 续行判定（纯函数，无状态）：已开的冒号/编号条目里，冒号行前缀 [name] 是不是
     * 上一条正文的续行，而非一个新发言人。
     *
     * 续行形态两种（命中其一即是）：
     * 1. 常见字段词（[CONTENT_LABELS]，如「地址：西湖边」）；
     * 2. 人称代词 我/你/她/他/它 开头、后接从句动词（[CLAUSE_VERBS]）的**多字**前缀
     *    （「我想说：其实我不饿」是正文里的冒号句，不是发言人叫「我想说」）。
     *    单字代词（我/她/他）是正常短昵称；代词开头但后接非动词（我的宝贝）是真实昵称，
     *    都不算续行；未知短昵称（B）也照常拆条。
     *
     * 例外（命中其一即返回 false，照常换条）：
     * - 该名字已作为发言人被确认过（[seenSpeakers]）；
     * - 该名字是全文再出现的代词开头前缀（[recurringSpeakers]，出现 ≥2 次）——真昵称
     *   哪怕撞了续行形态，也以全文证据为准。字段词不受此例外影响（正文里重复的字段行
     *   仍是续行）。[autoSplit] 以空集合复用本函数判断「发言人是否全是续行形态」，
     *   不另立第二份规则。
     */
    private fun isContinuationPrefix(
        name: String,
        seenSpeakers: Set<String>,
        recurringSpeakers: Set<String> = emptySet()
    ): Boolean {
        if (name in seenSpeakers || name in recurringSpeakers) return false
        if (name in CONTENT_LABELS) return true
        return name.length >= 2 && name.first() in PRONOUN_PREFIXES &&
            CLAUSE_VERBS.any { name.startsWith(it, startIndex = 1) }
    }

    /** 空白类：含全角空格 U+3000 与不换行空格 U+00A0（微信粘贴里出现，`\s` 不认） */
    private val WS = "[\\s\\u3000\\u00A0]"

    /** 整行只是一个时间戳（块格式第 2 行）：必须有钟点，可带日期/相对日/上下午前缀 */
    private val TIME_LINE = Regex(
        "^(?:(?:\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?)|(?:\\d{1,2}[-/月]\\d{1,2}日?))?$WS*" +
            "(?:昨天|前天|今天|星期[一二三四五六日天]|周[一二三四五六日天])?$WS*" +
            "(?:上午|下午|晚上|凌晨|中午)?$WS*" +
            "\\d{1,2}:\\d{2}(?::\\d{2})?$WS*" +
            "(?:上午|下午|晚上|凌晨|中午)?$"
    )

    /**
     * 行尾是时间戳、行首是昵称：`昵称 2024-01-01 12:00`。
     * 昵称组排除冒号（半角/全角都不进昵称）：`小美：明天 12:00` 是冒号行、正文含时间，
     * 必须落到 colonSplit，不能被误认成"昵称+同行时间"的块头。
     */
    private val NAME_THEN_TIME = Regex(
        "^([^：:]{1,30}?)$WS+" +
            "((?:(?:\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?)|(?:\\d{1,2}[-/月]\\d{1,2}日?))?$WS*" +
            "(?:昨天|前天|今天|星期[一二三四五六日天]|周[一二三四五六日天])?$WS*" +
            "(?:上午|下午|晚上|凌晨|中午)?$WS*" +
            "\\d{1,2}:\\d{2}(?::\\d{2})?)$"
    )

    /** 编号转发前缀：`1.` `12、` `3)` `4．`；标记后紧跟数字的是小数（1.5倍焦距），不是编号 */
    private val NUMBERED = Regex("^(\\d{1,3})[.、)．](?!\\d)\\s*(.*)$")
}
