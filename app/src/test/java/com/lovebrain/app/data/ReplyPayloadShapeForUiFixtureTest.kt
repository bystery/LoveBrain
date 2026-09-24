package com.lovebrain.app.data

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CI 上 ResultAreaInteractionTest 三格红的根因，用生产解析器在本机量一遍。
 *
 * 那三格找的是「推荐回复内容」这个节点。夹具构造 payload 时调的是
 * MainChainHarness.parsedSuccess(raw) —— 而那个 helper 的入参是**一句回复正文**，
 * 它会替调用者拼一个只有 recommended 有内容的外壳。把整段 JSON 当正文传进去，
 * 解析结果就是：四格里只有一格有文本，且那格的内容是那一整段 JSON 字符串。
 * 于是"推荐回复内容"这个节点从来就不存在，断言找不着东西。
 *
 * 下面这份 fourStylePayload 与 app/src/androidTest/…/ResultAreaInteractionTest.kt 里
 * makeSuccessResult() 的文本是同一段：那边的三格断言靠这份文本成立，改一处要改两处。
 * （两个测试源集之间没有共享目录，这条欠账记在 §5.1 的 core/testing 那一格里。）
 */
class ReplyPayloadShapeForUiFixtureTest {

    private lateinit var repo: DeepSeekRepository

    /** 四风格齐全 + 方向列表——UI 夹具要的就是这份 */
    private val fourStylePayload =
        "{\"response\":{\"recommended\":\"推荐回复内容\",\"bad_boy\":\"清醒回复\"," +
            "\"playful\":\"俏皮回复\",\"warm\":\"温柔回复\"}," +
            "\"directions\":[\"F reply\",\"E reply\",\"X reply\",\"S reply\"]," +
            "\"analysis\":{\"topic_status\":\"same\",\"topic_label\":\"test\"}}"

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        repo = DeepSeekRepository(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /** 正确构造：整段文本交给生产解析器，四格文本各归各位 */
    @Test
    fun fourStylePayloadYieldsFourSeparateSchemeTexts() {
        val replies = repo.parseReplyResponse(fourStylePayload).schemes.map { it.reply }

        assertEquals(
            "四格方案的文本应当一一对上（UI 用例就是拿这些文本找节点的）：$replies",
            listOf("推荐回复内容", "清醒回复", "俏皮回复", "温柔回复"),
            replies
        )
    }

    /**
     * 反向（就是 CI 那三格的成因）：把整段 JSON 当"一句正文"塞进 recommended。
     * 只有一格非空，且那一格的文本不等于任何一条方案文案 → onNodeWithText 必然找不到。
     */
    @Test
    fun stuffingWholeJsonIntoTheSingleSlotBreaksEverySchemeLabel() {
        val escaped = fourStylePayload
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val singleSlotEnvelope =
            "{\"response\":{\"recommended\":\"$escaped\",\"bad_boy\":\"\"," +
                "\"playful\":\"\",\"warm\":\"\"}," +
                "\"analysis\":{\"topic_status\":\"same\",\"topic_label\":\"fake\"}}"

        val replies = repo.parseReplyResponse(singleSlotEnvelope).schemes.map { it.reply }

        assertEquals(
            "这种构造只会有 recommended 那一格有内容，其余三格是空串——面板上根本凑不齐四张卡：" +
                replies.joinToString(" | "),
            listOf(fourStylePayload, "", "", ""),
            replies
        )
        assertTrue(
            "「推荐回复内容」在这份模型里不是任何一格的完整文本，" +
                "所以 onNodeWithText(\"推荐回复内容\") 在 CI 上必然报节点不存在",
            replies.none { it == "推荐回复内容" }
        )
    }
}
