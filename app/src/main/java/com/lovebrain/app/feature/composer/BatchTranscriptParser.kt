package com.lovebrain.app.feature.composer

/**
 * 微信批量复制文本的**结构**解析器（Issue #7「复制粘贴支持多条对话记录」）。
 *
 * 纯 Kotlin、零 Android 依赖、无共享可变状态：不给它接 Context/Compose，
 * 它就永远可以在 JVM 上单测（`BatchTranscriptParserTest`）。
 *
 * ## 只认一种结构：空行分块
 *
 * ```
 * 昵称（任意非空文字，内容不限）
 * 日期/时间行（只看结构，见下）
 * 正文（可多行，从第 3 行起原样保留，不再解析）
 *
 * 昵称
 * 日期/时间行
 * 正文
 * ```
 *
 * 先统一换行、去掉首尾空白行，再按"一个或多个空行（仅空白也算）"切块。
 * **所有块都必须满足上述结构且至少 1 块** 才交出条目；任何一块不满足就整段不合格
 * （返回 null）——不允许跳过坏块、不允许部分导入。为什么这么严：半段结构说明这段文本
 * 本来就不是同一份转录，猜着拆会把普通正文拆成几条假消息，比不拆更难收拾。
 *
 * 日期/时间行判据**只有结构正则**（日期形态或钟点形态），不看昵称、说话人、冒号、
 * 编号、标题，也不据首行推断角色——首行只是"块头"，叫什么都行。第 3 行起即使长得
 * 像时间/日期也原样留在正文里（从第 3 行起不再判时间）。
 *
 * 第 2 行必须**整行**就是时间行（首尾空白不计）：`21:30 见` 这种"钟点 + 半句话"
 * 不是时间行，否则结构会在半句话上成立、把后半句连同正文一起切走。
 *
 * 冒号两行文本（`小美：在吗\n我：在的`）只有 1 块且只有两行（缺时间行与正文）⇒
 * 结构不成立 ⇒ 返回 null，调用方整段落成一条，绝不按冒号拆条。
 *
 * ## 角色不在这里判
 *
 * 条目只有正文：角色由调用方统一用输入框当前角色（composeRole）。
 * 昵称/标题因此不进入任何角色推断。
 */
object BatchTranscriptParser {

    /** 解析出的一条候选消息。 */
    data class Entry(
        /** 该块第 3 行起的正文（多行以 `\n` 连接，只去掉块首尾空行，行内缩进原样保留） */
        val content: String
    )

    /**
     * 按空行分块拆条；不合格（没有块、任一块结构不成立或整段为空）返回 null，
     * 调用方把整段原文当一条消息入库。
     *
     * 纯函数、零 Android 依赖，JVM 可单测。
     */
    fun autoSplitEntries(raw: String): List<Entry>? {
        val blocks = blocksOf(raw)
        if (blocks.isEmpty()) return null
        val entries = ArrayList<Entry>(blocks.size)
        for (block in blocks) {
            // 块头 + 时间行 + 至少 1 行正文；缺任何一段都不是这份转录的结构。
            if (block.size < 3) return null
            if (!isTimestampLine(block[1])) return null
            // 只按空行裁块（[blocksOf] 已切好，块内没有空行）；**不 trim**：行首缩进是
            // 粘贴文本的一部分，把它削掉等于改了用户要发出去的字。
            val body = block.subList(2, block.size).joinToString("\n")
            if (body.isBlank()) return null
            entries += Entry(body)
        }
        return entries
    }

    /** 统一换行、按空白行切块：连续多个空白行只切一次，首尾空白行不产生空块。 */
    private fun blocksOf(raw: String): List<List<String>> {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    blocks += current
                    current = mutableListOf()
                }
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) blocks += current
        return blocks
    }

    /**
     * 日期/时间行：第 2 行去掉首尾空白后，**整行**必须是日期形态或钟点形态之一
     * （2024-01-01、2024/1/1、2026年11月20日、11月20日、21:30、21:30:00、
     * 2026-11-21 21:30、可带"昨天"前缀），行内不许再有任何其他文字。
     * 不做语义判断：这一行是块结构里的固定位置，长什么样由粘贴的客户端决定。
     */
    private fun isTimestampLine(line: String): Boolean = TIMESTAMP.matches(line.trim())

    /**
     * 整行时间行。三段结构缺一不可：可选"昨天"前缀、日期或钟点、行尾锚定。
     *
     * - 年固定 4 位、月/日/时 1–2 位、分/秒固定 2 位——位数放宽会让 `21:300`、
     *   `2024-01-011` 这类正文混进结构位；
     * - 日期分隔符必须一致（`\1`），`2024/01-01` 是正文不是日期；
     * - 末尾 `$` 与 `matches` 的双保险：`21:30 见` 尾部还有字 ⇒ 不是时间行，
     *   整段退回 B 路，不会切开半句话。
     */
    private val TIMESTAMP = Regex(
        "^(?:昨天\\s*)?" +
            "(?:(?:\\d{4}([-/.])\\d{1,2}\\1\\d{1,2}" +
            "|\\d{4}年\\d{1,2}月\\d{1,2}日" +
            "|\\d{1,2}月\\d{1,2}日)" +
            "(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?" +
            "|\\d{1,2}:\\d{2}(?::\\d{2})?)" +
            "\$"
    )
}
