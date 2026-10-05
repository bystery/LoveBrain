package com.lovebrain.app.domain

import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * 本轮输入指纹的算法合同（`GenerationFingerprints.inputOf`）。
 *
 * 它决定的是**"这一轮的输入算不算变了"**，所以必须钉住三件事：**同样的输入必得同样的值**
 * （否则白花钱重算）、**任何一个真正影响结果的输入变了值就得变**（否则拿旧结果冒充新上下文）、
 * **拼接不能自己造出碰撞**（不加长度前缀时 `"a"+"bc"` 与 `"ab"+"c"` 会同串，
 * 那是真会发生的两类不同输入撞车）。
 *
 * 2026-10-03 锦囊整族退场，同一颗 object 上那条"锦囊上下文指纹"（`SuggestContext` 与它的
 * `suggestContextOf`）随功能一起删。输入这一族的判据一个字没减：备注（ideaHint）、仅看本轮
 * （onlyThisRound）、消息顺序都仍在下面那格里逐字段点名。
 */
class GenerationFingerprintsTest {

    private fun msg(id: String, role: ChatMessage.Role, content: String) =
        ChatMessage(id = id, role = role, content = content)

    private val baseMessages = listOf(
        msg("m1", ChatMessage.Role.HER, "在忙"),
        msg("m2", ChatMessage.Role.ME, "好")
    )

    private fun input(
        messages: List<ChatMessage> = baseMessages,
        ideaHint: String = "想约周末",
        kbName: String? = "default",
        onlyThisRound: Boolean = false,
        intentRevision: Int = 0
    ) = GenerationFingerprints.inputOf(messages, ideaHint, kbName, onlyThisRound, intentRevision)

    // ─── 本轮输入指纹 ────────────────────────────────────────────

    @Test
    fun `the input fingerprint is stable and 16 hex chars`() {
        val first = input()
        assertEquals(16, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{16}")))
        assertEquals(first, input())
    }

    @Test
    fun `every field that changes the turn changes the fingerprint`() {
        val base = input()
        assertNotEquals("换 KB 必须算变", base, input(kbName = "other"))
        assertNotEquals("仅本轮开关必须算变", base, input(onlyThisRound = true))
        assertNotEquals("意图 revision 必须算变", base, input(intentRevision = 1))
        assertNotEquals("想法文本必须算变", base, input(ideaHint = "换个说法"))
        assertNotEquals("消息正文必须算变", base,
            input(messages = listOf(msg("m1", ChatMessage.Role.HER, "在忙呀"), msg("m2", ChatMessage.Role.ME, "好"))))
        assertNotEquals("消息身份必须算变", base,
            input(messages = listOf(msg("mX", ChatMessage.Role.HER, "在忙"), msg("m2", ChatMessage.Role.ME, "好"))))
        assertNotEquals("消息角色必须算变", base,
            input(messages = listOf(msg("m1", ChatMessage.Role.ME, "在忙"), msg("m2", ChatMessage.Role.ME, "好"))))
        assertNotEquals("消息顺序必须算变", base,
            input(messages = listOf(baseMessages[1], baseMessages[0])))
        assertNotEquals("多一条消息必须算变", base, input(messages = baseMessages + msg("m3", ChatMessage.Role.HER, "嗯")))
    }

    @Test
    fun `idea text is trimmed and a missing kb name reads as empty`() {
        assertEquals(input(ideaHint = "想约周末"), input(ideaHint = "  想约周末\n"))
        assertEquals(input(kbName = null), input(kbName = ""))
    }

    @Test
    fun `length prefixes keep two different splits of the same characters apart`() {
        fun canon(pairs: List<Pair<String, String>>): String {
            val sb = StringBuilder()
            pairs.forEach { (k, v) -> GenerationFingerprints.appendLengthPrefixed(sb, k, v) }
            return sb.toString()
        }
        fun naive(pairs: List<Pair<String, String>>): String = pairs.joinToString("") { it.second }

        val left = listOf("c" to "ab", "c" to "c")
        val right = listOf("c" to "a", "c" to "bc")

        // 裸拼接：两种切法同串——这就是不加长度前缀会撞的那种车
        assertEquals("abc", naive(left))
        assertEquals(naive(left), naive(right))
        // 加了前缀：必须分开
        assertNotEquals(canon(left), canon(right))
    }

    @Test
    fun `message content boundaries are part of the input fingerprint`() {
        // 这条测的是"边界也算输入"（真正防裸拼接撞车的是上面 canon 那条）：
        // 两条消息正文怎么切分，都必须得到不同指纹
        val oneWay = input(messages = listOf(
            msg("m1", ChatMessage.Role.HER, "ab"), msg("m2", ChatMessage.Role.ME, "c")
        ))
        val otherWay = input(messages = listOf(
            msg("m1", ChatMessage.Role.HER, "a"), msg("m2", ChatMessage.Role.ME, "bc")
        ))
        assertEquals("ab" + "c", "a" + "bc")
        assertNotEquals("正文切分不同就是两轮不同的输入", oneWay, otherWay)
    }

    @Test
    fun `hashing is pinned to utf-8 not to the platform default charset`() {
        val text = "她说明天见"
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(GenerationFingerprints.SECTION_PREFIX_LEN)
        assertEquals(expected, GenerationFingerprints.hashPrefix(text))
    }

    @Test
    fun `an empty section is a dash, so no content never looks like a real digest`() {
        assertEquals("-", GenerationFingerprints.hashPrefix(""))
        assertNotEquals("-", GenerationFingerprints.hashPrefix(" "))
        assertTrue(GenerationFingerprints.hashPrefix("a").startsWith(
            GenerationFingerprints.hashPrefix("a", 4)
        ))
    }
}
