package com.lovebrain.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 凭据摘要算法（指导书 §11.1 的连接身份用它记住"哪一把 Key、哪一个地址"）。
 *
 * 期望值全部**手写**（下面那五个十六进制串是 SHA-256 的前 12 位，由权威实现离线算出后入码），
 * 不是让判据自己算自己——否则有人把算法换成 MD5、把长度改成 8 位，这一族会跟着一起改绿。
 *
 * 反例（缺陷态会怎么红）：
 * - 加了随机盐或时间因子 → `the same credential fingerprints…` 红，而生产里每次读盘都算"换了 Key"，
 *   绿灯永远留不住（§11.1 要修的反面）；
 * - 明文 Key 混进摘要串（比如图省事直接返回原串）→ `no fragment leaks…` 红，那是安全红线；
 * - 空值降级成某个固定串（如 "-"）→ 第二句红，且"没配 Key"与"Key 恰好哈希成那个值"撞车；
 * - 丢掉显式 UTF-8（改成 `toByteArray()` 跟平台默认编码走）→ `non-ascii…` 在中文 Windows 上红。
 */
class CredentialFingerprintTest {

    /** 摘要片段的形状：永远是 12 个小写十六进制字符 */
    private val fragmentShape = Regex("^[0-9a-f]{12}$")

    @Test
    fun `the same credential always fingerprints to the same fragment`() {
        assertEquals("SHA-256(\"abc\") 前 12 位（手写常量）", "ba7816bf8f01", CredentialFingerprint.of("abc"))
        assertEquals("同一个 Key 读两次必须同值（无盐、无时间因子）",
            CredentialFingerprint.of("sk-old-key"), CredentialFingerprint.of("sk-old-key"))
        assertEquals("地址同理：同一串 baseUrl 永远同一个片段",
            "37ac3c481f21", CredentialFingerprint.of("https://api.deep.example"))
    }

    @Test
    fun `a rotated key or url moves the fragment`() {
        // 这一格是"尺真的会动"的证人：身份式里带摘要的意义全押在它身上
        val oldKey = CredentialFingerprint.of("sk-old-key")
        val newKey = CredentialFingerprint.of("sk-brand-new-key")
        assertTrue("换 Key 必须算出不同片段：old=$oldKey new=$newKey", oldKey != newKey)
        assertTrue("只差最后一个字符也要认出来是另一把 Key",
            CredentialFingerprint.of("sk-old-key") != CredentialFingerprint.of("sk-old-kex"))
        assertTrue("换地址（同 Key）同理",
            CredentialFingerprint.of("https://api.deep.example") !=
                CredentialFingerprint.of("https://api.deep.example/v1"))
        listOf(oldKey, newKey).forEach {
            assertTrue("片段必须是 12 位小写十六进制：$it", it.matches(fragmentShape))
        }
    }

    @Test
    fun `no credential material appears in the fragment`() {
        val key = "sk-abcdefghij0123456789"
        val fragment = CredentialFingerprint.of(key)
        assertFalse("摘要是不可逆片段，明文的一个字符都不许在里面", fragment.contains("sk-"))
        assertFalse("也不许是原文的截断（那等于把 Key 前缀写进盘）", key.startsWith(fragment))
        assertTrue("输出只剩十六进制：$fragment", fragment.matches(fragmentShape))
    }

    @Test
    fun `absent credentials fingerprint to an empty segment`() {
        // 空串 = 这一格根本没有凭据（NO_PROVIDER / 没 Key 那一档），与"有值"天然可区分
        assertEquals("null（没写过 Key）", "", CredentialFingerprint.of(null))
        assertEquals("空串", "", CredentialFingerprint.of(""))
        assertEquals("全空白按没配处理（与 hasKey 的判据同口径）", "", CredentialFingerprint.of("   "))
        assertFalse("空段不许被洗成一个固定摘要（那会与其它'没凭据'的记录撞车）",
            CredentialFingerprint.of("").matches(fragmentShape))
    }

    @Test
    fun `non-ascii material is hashed as utf-8 not as the platform default`() {
        // 手写常量：SHA-256("她".toByteArray(UTF_8)) 前 12 位
        assertEquals("显式 UTF-8（跟平台默认编码走时这一句在中文 Windows 上红）",
            "2ab425e0c6b9", CredentialFingerprint.of("她"))
    }
}
