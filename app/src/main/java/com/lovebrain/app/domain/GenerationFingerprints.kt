package com.lovebrain.app.domain

import com.lovebrain.app.model.ChatMessage
import java.security.MessageDigest

/**
 * 本轮生成输入的指纹算法（从 `LoveBrainViewModel` 拆出）。
 *
 * 指纹原先只能靠"跑一次生成再看有没有命中缓存"间接观察，而且拼串规则写在
 * ViewModel 里没法单独测。它决定的是**"这一轮的输入算不算变了"**：
 * 算错方向就会该重算的不重算（拿旧结果冒充新上下文），
 * 或者不该重算的次次重算（白花钱）。
 *
 * 关键设计是 length-prefix 编码：`key(len):value;`。
 * 不这么做的话 `"a"+"bc"` 与 `"ab"+"c"` 拼出来一模一样，
 * 两条完全不同的输入会撞成同一个指纹——这不是理论风险，是无前缀拼接的默认结果。
 */
object GenerationFingerprints {

    /** 单段子内容的摘要长度；空内容固定成 "-"，让"没内容"与"内容哈希恰好以 0 开头"可区分 */
    const val SECTION_PREFIX_LEN = 12

    /**
     * 军师备注在拼串里唯一的段名。
     *
     * 以前这一段叫 `idea`——那是《想法》时代的名字，留着它会让下一个读代码的人以为
     * "备注还可以退化成一种消息角色"。名字改掉只影响同一进程内的比较：
     * 指纹不落盘（它躺在 `ReplyGenerationContext` 里，杀进程即清），所以没有跨版本命中问题。
     */
    const val NOTE_SECTION_KEY = "note"

    fun appendLengthPrefixed(sb: StringBuilder, key: String, value: String) {
        sb.append(key).append("(").append(value.length).append("):").append(value).append(";")
    }

    /** SHA-256 前若干位十六进制；显式 UTF-8，不跟平台默认编码走 */
    fun hashPrefix(text: String, len: Int = SECTION_PREFIX_LEN): String {
        if (text.isEmpty()) return "-"
        return sha256Hex(text).take(len)
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * 本轮生成输入的指纹。
     *
     * 覆盖：KB 身份、仅本轮开关、意图 revision、**军师备注原文**（trim 后）、每条消息的
     * **身份 + 角色 + 正文 + 顺序**。不覆盖温度等生成参数——不把它说成"完整输入"。
     *
     * 备注这一维不是"顺手带上的字符串"：第6节第2条 要的正是"备注一改，旧结果就得判过时"。
     * 所以它有一个带名字的类型入口（[RoundInput]），实参也必须叫 `advisorNote`——
     * 位置参数被某个调用点写成 `""` 编译器不会拦，名字与类型至少让这一维写不丢。
     * 生产里两个调用面（发起生成时冻结的那一份、事后判 stale 时现读的那一份）
     * 都必须从同一个 `ComposerStore` 出口取备注，否则两边算出的数永远对不上：
     * 那不是"保守地标过时"，那是拿旧回复冒充新上下文。
     */
    fun inputOf(
        messages: List<ChatMessage>,
        advisorNote: String,
        kbName: String?,
        onlyThisRound: Boolean,
        intentRevision: Int
    ): String {
        val sb = StringBuilder()
        appendLengthPrefixed(sb, "kb", kbName ?: "")
        appendLengthPrefixed(sb, "otr", onlyThisRound.toString())
        appendLengthPrefixed(sb, "irev", intentRevision.toString())
        appendLengthPrefixed(sb, NOTE_SECTION_KEY, advisorNote.trim())
        sb.append("msgs=")
        for (msg in messages) {
            appendLengthPrefixed(sb, "m", msg.id)
            sb.append(msg.role.name).append(",")
            appendLengthPrefixed(sb, "c", msg.content)
            sb.append(";")
        }
        return sha256Hex(sb.toString()).take(16)
    }

    /**
     * 算一次本轮输入指纹所需的那一组冻结值。
     *
     * 存在的理由只有一个：**"备注"这一维不许是某个调用点上可省略的尾巴参数**。
     * 备注原文与消息/KB/开关/意图版本同级，缺了它"改过备注"就仍是"输入没变"，
     * 于是旧回复继续被命中——那是 第6节第2条 点名要禁的结果。
     *
     * 它不是第二份指纹：算法只有 [inputOf] 这一处，这里唯一做的事是把五个维度先绑成一组。
     */
    data class RoundInput(
        /** 本轮真实对话（只含 HER/ME；备注不在这里，见 [advisorNote]） */
        val messages: List<ChatMessage>,
        /** 军师备注原文——必填维度，不是可选参数 */
        val advisorNote: String,
        val kbName: String?,
        val onlyThisRound: Boolean,
        val intentRevision: Int
    )

    /** [RoundInput] 形状的同一条算法——逐字委派给上面那颗，不另起一份拼串规则 */
    fun inputOf(input: RoundInput): String = inputOf(
        messages = input.messages,
        advisorNote = input.advisorNote,
        kbName = input.kbName,
        onlyThisRound = input.onlyThisRound,
        intentRevision = input.intentRevision
    )
}
