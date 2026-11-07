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
        val ctx = Ctx()
        // 换行统一 + 剪贴板不可见污染清洗（BOM/零宽字符）：它们不是正文，留着会破坏
        // 发言人行/时间行的锚定（trim 不吃 U+FEFF）。
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n')
            .replace('\uFEFF', ' ').replace('\u200B', ' ')
            .split('\n')
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

    /** 单次 [parse] 的全部可变状态——进出都是局部量，object 本身无状态 */
    private class Ctx {
        val entries = mutableListOf<Entry>()
        val warnings = mutableListOf<String>()

        /** 见过的发言人昵称：开条目时登记；"已知昵称的冒号行必是新条"的判据 */
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
                            if (colon.second.isNotBlank()) openLines.add(colon.second)
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

            // ── `昵称 12:00`（昵称与时间同一行，仅在没有进行中记录/待定行时认）──
            if (!openExists && pendingText == null && NAME_THEN_TIME.matches(line)) {
                val m = NAME_THEN_TIME.find(line)!!
                startOpen(m.groupValues[1].trim(), m.groupValues[2].trim(), lineNo, Origin.BLOCK)
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
                flushPending()
                startOpen(name, null, lineNo, Origin.COLON)
                if (content.isNotBlank()) openLines.add(content)
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
            openLines.add(line)
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
            if (speaker != null) seenSpeakers.add(speaker)
            openExists = true
            openSpeaker = speaker
            openTime = time
            openLineNo = lineNo
            openOrigin = origin
        }

        /**
         * 冒号行拆分：返回 (发言人, 内容)，不是冒号行返回 null。
         *
         * [knownSpeakerOnly] 为真（已开条目）时只有**再出现的已知昵称**才认作新记录——
         * 正文里的冒号句（"我想说：晚安"）不是已知发言人，归并进当前条继续累积。
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

    /** 行尾是时间戳、行首是昵称：`昵称 2024-01-01 12:00` */
    private val NAME_THEN_TIME = Regex(
        "^(.{1,30}?)$WS+" +
            "((?:(?:\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?)|(?:\\d{1,2}[-/月]\\d{1,2}日?))?$WS*" +
            "(?:昨天|前天|今天|星期[一二三四五六日天]|周[一二三四五六日天])?$WS*" +
            "(?:上午|下午|晚上|凌晨|中午)?$WS*" +
            "\\d{1,2}:\\d{2}(?::\\d{2})?)$"
    )

    /** 编号转发前缀：`1.` `12、` `3)` `4．` */
    private val NUMBERED = Regex("^(\\d{1,3})[.、)．]\\s*(.*)$")
}
