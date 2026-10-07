package com.lovebrain.app.util

import java.security.MessageDigest

/**
 * 凭据摘要：把"用的是哪一把 Key / 哪一个地址"压成**不可逆**的短片段，只让摘要出门。
 *
 * 存在的理由只有一条：连接身份必须记住地址与 Key（指导书 §11.1「地址/Key/模型真正变化才使
 * 旧成功无效」），而身份串要落 `SharedPreferences`、要进内存账本、可能随 `toString` 进日志或报告。
 * 明文 Key 一旦进那三处里的任何一处就再也收不回来，所以这一格里**只有摘要**会被交出去：
 * SHA-256 前 12 位十六进制（48 bit）——判"换没换"够用，反推原文不够用。
 *
 * 三条口径写死在这里，别让调用方各写一套：
 * - **空值 → 空串**：`null` 或全空白（= 没配凭据）那一段是空串，与"有凭据"天然可区分；
 *   注意这与"有值但恰好哈希不出前缀"不会撞车，因为真有值时那一段永远是 12 个十六进制字符；
 * - **同输入必同输出**：没有随机盐、没有时间因子——否则每次读盘都是一次"身份变了"，
 *   绿灯永远留不住，那正是 §11.1 要修的反面；
 * - **读不出就抛，不静默降级成空串**：SHA-256 是 Java/Android 平台保证存在的算法，
 *   `getInstance` 真失败说明环境坏了；把它吞成空串会让"换 Key"永远判不出变化，
 *   那是把安全缺陷洗成假绿灯。
 */
internal object CredentialFingerprint {

    /** 摘要片段长度（十六进制字符数）：够区分同一工单下的两把 Key，又不足以反推原文 */
    private const val HEX_CHARS = 12

    /**
     * 一段凭据/地址 → 12 位十六进制摘要；空值或全空白 → 空串。
     *
     * 显式按 UTF-8 取字节（与 [com.lovebrain.app.domain.GenerationFingerprints] 同一口径），
     * 不跟平台默认编码走：默认编码一变，同一个 Key 就会算出两个摘要，绿等会无故作废。
     */
    fun of(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(HEX_CHARS)
    }
}
