package com.lovebrain.app.domain

import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * 两处指纹的算法合同（`GenerationFingerprints`）。
 *
 * 它们决定"这一轮输入算不算变了"和"锦囊缓存能不能复用"，
 * 所以必须钉住三件事：**同样的输入必得同样的值**（否则白花钱重算）、
 * **任何一个真正影响结果的输入变了值就得变**（否则拿旧锦囊冒充新上下文）、
 * **拼接不能自己造出碰撞**（不加长度前缀时 `"a"+"bc"` 与 `"ab"+"c"` 会同串，
 * 那是真会发生的两类不同输入撞车）。
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

    // ─── 锦囊上下文指纹 ──────────────────────────────────────────

    private fun ctx() = GenerationFingerprints.SuggestContext(
        kbId = "default",
        today = "2026-09-24",
        stage = "暧昧期",
        outputMode = 0,
        thinkingMode = 1,
        onlyThisRound = false,
        assetHash = "aaaaaaaaaaaaaaaa",
        providerHost = "https://api.deepseek.com",
        providerModel = "deepseek-chat",
        ongoingPlan = "周末旅行 | 待定 | 已订机票",
        styleContent = "不用感叹号"
    )

    @Test
    fun `the suggest fingerprint is stable and sensitive to each part`() {
        val base = GenerationFingerprints.suggestContextOf(ctx())
        assertEquals(GenerationFingerprints.SUGGEST_PREFIX_LEN, base.length)
        assertEquals(base, GenerationFingerprints.suggestContextOf(ctx()))

        val mutations = mapOf(
            "库" to ctx().copy(kbId = "other"),
            "日期（冻结的发起日）" to ctx().copy(today = "2026-09-25"),
            "阶段" to ctx().copy(stage = "热恋期"),
            "输出模式" to ctx().copy(outputMode = 1),
            "思考模式" to ctx().copy(thinkingMode = 0),
            "仅本轮" to ctx().copy(onlyThisRound = true),
            "prompt 资产 hash" to ctx().copy(assetHash = "bbbbbbbbbbbbbbbb"),
            "供应商地址" to ctx().copy(providerHost = "https://evil.example"),
            "模型" to ctx().copy(providerModel = "deepseek-reasoner"),
            "事项内容" to ctx().copy(ongoingPlan = "周末旅行 | 已完成"),
            "表达偏好内容" to ctx().copy(styleContent = "可以带表情")
        )
        for ((label, mutated) in mutations) {
            assertNotEquals("$label 变了却还算同一次上下文", base, GenerationFingerprints.suggestContextOf(mutated))
        }
    }

    @Test
    fun `a plan that is empty differs from one that is only whitespace`() {
        val empty = GenerationFingerprints.suggestContextOf(ctx().copy(ongoingPlan = ""))
        val blank = GenerationFingerprints.suggestContextOf(ctx().copy(ongoingPlan = " "))
        assertNotEquals("没有事项与有一条空事项是两回事", empty, blank)
    }
}
