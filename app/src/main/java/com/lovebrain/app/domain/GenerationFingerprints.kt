package com.lovebrain.app.domain

import com.lovebrain.app.model.ChatMessage
import java.security.MessageDigest

/**
 * 生成输入与锦囊缓存的指纹算法（从 `LoveBrainViewModel` 拆出）。
 *
 * 两处指纹原先只能靠"跑一次生成再看有没有命中缓存"间接观察，而且拼串规则写在
 * ViewModel 里没法单独测。它们决定的是**"这一轮的输入算不算变了"**：
 * 算错方向就会该重算的不重算（拿旧锦囊冒充新上下文），
 * 或者不该重算的天天重算（白花钱）。
 *
 * 关键设计是 length-prefix 编码：`key(len):value;`。
 * 不这么做的话 `"a"+"bc"` 与 `"ab"+"c"` 拼出来一模一样，
 * 两条完全不同的输入会撞成同一个指纹——这不是理论风险，是无前缀拼接的默认结果。
 */
object GenerationFingerprints {

    /** 锦囊缓存指纹的输出长度 */
    const val SUGGEST_PREFIX_LEN = 16

    /** 单段子内容的摘要长度；空内容固定成 "-"，让"没内容"与"内容哈希恰好以 0 开头"可区分 */
    const val SECTION_PREFIX_LEN = 12

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
     * 覆盖：KB 身份、仅本轮开关、意图 revision、想法文本（trim 后）、每条消息的
     * **身份 + 角色 + 正文 + 顺序**。不覆盖温度等生成参数——不把它说成"完整输入"。
     */
    fun inputOf(
        messages: List<ChatMessage>,
        ideaHint: String,
        kbName: String?,
        onlyThisRound: Boolean,
        intentRevision: Int
    ): String {
        val sb = StringBuilder()
        appendLengthPrefixed(sb, "kb", kbName ?: "")
        appendLengthPrefixed(sb, "otr", onlyThisRound.toString())
        appendLengthPrefixed(sb, "irev", intentRevision.toString())
        appendLengthPrefixed(sb, "idea", ideaHint.trim())
        sb.append("msgs=")
        for (msg in messages) {
            appendLengthPrefixed(sb, "m", msg.id)
            sb.append(msg.role.name).append(",")
            appendLengthPrefixed(sb, "c", msg.content)
            sb.append(";")
        }
        return sha256Hex(sb.toString()).take(16)
    }

    /** 锦囊上下文指纹的各段输入——全部由调用方读好后传进来，这里不碰仓库与偏好 */
    data class SuggestContext(
        val kbId: String,
        val today: String,
        val stage: String,
        val outputMode: Int,
        val thinkingMode: Int,
        val onlyThisRound: Boolean,
        val assetHash: String,
        val providerHost: String,
        val providerModel: String,
        val ongoingPlan: String,
        val styleContent: String
    )

    /**
     * 锦囊缓存的命中判据。
     *
     * 日期是**发起时冻结**那天，跨午夜完成仍算发起当天；
     * 事项与表达偏好只取内容摘要，不把整篇文本拼进指纹（长度不可控）。
     */
    fun suggestContextOf(ctx: SuggestContext): String = buildString {
        append(ctx.kbId).append('|').append(ctx.today).append('|').append(ctx.stage).append('|')
        append(ctx.outputMode).append('|').append(ctx.thinkingMode).append('|').append(ctx.onlyThisRound).append('|')
        append(ctx.assetHash).append('|').append(ctx.providerHost).append('|').append(ctx.providerModel).append('|')
        append(hashPrefix(ctx.ongoingPlan)).append('|').append(hashPrefix(ctx.styleContent))
    }.let { sha256Hex(it).take(SUGGEST_PREFIX_LEN) }
}
